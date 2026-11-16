package com.example.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineRestoreReferenceSnapshotTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var database: AppDatabase
    private lateinit var original: File
    private lateinit var external: File
    private lateinit var missing: File
    private lateinit var content: File
    private var mutateOnSecondOpen = false
    private var providerOpens = 0
    private val namespace = "rollback-refs-${UUID.randomUUID()}"
    private val contentUri = Uri.parse("content://$namespace/original")
    private val coordinator get() = OfflineBackupCoordinator.shared
    private fun id() = UUID.randomUUID().toString()
    @Before fun setup() = runBlocking<Unit> {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, namespace).apply { check(mkdirs()) }
        context = object : ContextWrapper(base) {
            private fun dir(name: String) = File(root, name).apply { check(isDirectory || mkdirs()) }
            override fun getFilesDir() = dir("files")
            override fun getCacheDir() = dir("cache")
            override fun getNoBackupFilesDir() = dir("no-backup")
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = base.getSharedPreferences("${namespace}_$name", mode)
        }
        original = File(context.filesDir, "managed_documents/own.pdf").apply { parentFile!!.mkdirs(); writeText("owned original") }
        external = File(root, "external.pdf").apply { writeText("external original") }
        missing = File(root, "already-missing.pdf")
        content = File(root, "provider.pdf").apply { writeText("provider original") }
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "application/octet-stream"
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = error("read only")
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = error("read only")
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = error("read only")
            override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
                if (uri.lastPathSegment == "missing") throw FileNotFoundException()
                providerOpens++
                if (mutateOnSecondOpen && providerOpens == 2) content.writeText("changed provider bytes")
                return ParcelFileDescriptor.open(content, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        }
        provider.attachInfo(base, ProviderInfo().apply { authority = namespace })
        ShadowContentResolver.registerProviderInternal(namespace, provider)
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).openHelperFactory(coordinator.sqliteFactory()).build()
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 3, propertyId = "house", name = "Haus"))
        val paths = JSONArray(listOf(original.absolutePath, missing.absolutePath, external.absolutePath,
            contentUri.toString(), "content://$namespace/missing")).toString()
        database.receiptDao().insertReceipt(Receipt(id = 41, internalId = "stable", propertyId = "house", aussteller = "Beleg",
            datum = "2026-11-11", uhrzeit = "", bruttobetrag = 12.0, hauptkategorie = "Kosten", unterkategorie = "Reparatur",
            kontoNr = "4800", beschreibung = "Original", imageUrl = paths))
    }
    @After fun cleanup() {
        database.close()
        PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        root.deleteRecursively()
    }
    @Test fun capturesExternalFilesAndMarksPreviouslyUnavailableReferencesWithoutChangingLiveData() = runBlocking<Unit> {
        coordinator.beginMaintenance().use { token ->
            val snapshotId = id()
            val owned = OfflineRestoreOwnedFilesSnapshot.capture(context, token, snapshotId)
            val snapshot = OfflineRestoreReferenceSnapshot.capture(context, database, token, snapshotId, owned)
            val byPath = snapshot.references.associateBy { it.path }
            assertEquals(5, byPath.size)
            assertEquals(OfflineRestoreReferenceSnapshot.State.OWNED, byPath.getValue(original.absolutePath).state)
            assertEquals(OfflineRestoreReferenceSnapshot.State.UNAVAILABLE, byPath.getValue(missing.absolutePath).state)
            assertEquals(OfflineRestoreReferenceSnapshot.State.UNAVAILABLE, byPath.getValue("content://$namespace/missing").state)
            for ((path, expected) in listOf(external.absolutePath to "external original", contentUri.toString() to "provider original")) {
                val reference = byPath.getValue(path)
                assertEquals(OfflineRestoreReferenceSnapshot.State.EXTERNAL, reference.state)
                assertEquals(expected, File(snapshot.directory, reference.stagedName!!).readText())
                assertEquals(64, reference.sha256!!.length)
            }
            val manifest = File(snapshot.directory, "manifest.json").readText()
            assertFalse(manifest.contains(external.absolutePath)); assertFalse(manifest.contains(contentUri.toString()))
            assertEquals(5, JSONObject(manifest).getJSONArray("references").length())
            assertFalse(File(snapshot.directory, "draft.json").exists())
            assertEquals(41, database.receiptDao().getAllReceiptsIncludingDeletedList().single().id)
            assertEquals("owned original", original.readText())
        }
    }
    @Test fun externalProviderChangeDuringRereadAbortsAndCleansOnlyNewReferenceDirectory() = runBlocking<Unit> {
        coordinator.beginMaintenance().use { token ->
            val snapshotId = id(); val owned = OfflineRestoreOwnedFilesSnapshot.capture(context, token, snapshotId)
            mutateOnSecondOpen = true
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineRestoreReferenceSnapshot.capture(context, database, token, snapshotId, owned)
            } }
            assertTrue(owned.directory.isDirectory)
            assertEquals("owned original", original.readText())
            assertFalse(File(context.noBackupFilesDir, "offline-backup-state/rollback-$snapshotId.references").exists())
        }
    }

    @Test fun changedOwnedSnapshotIsRejectedWithoutDeletingOldOrUnrelatedFiles() = runBlocking<Unit> {
        coordinator.beginMaintenance().use { token ->
            val snapshotId = id(); val owned = OfflineRestoreOwnedFilesSnapshot.capture(context, token, snapshotId)
            val ownFile = File(owned.directory, owned.versions.single().stagedName)
            ownFile.writeText("tampered")
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineRestoreReferenceSnapshot.capture(context, database, token, snapshotId, owned)
            } }
            assertEquals("tampered", ownFile.readText()); assertEquals("owned original", original.readText())
            assertFalse(File(context.noBackupFilesDir, "offline-backup-state/rollback-$snapshotId.references").exists())
        }
    }
}
