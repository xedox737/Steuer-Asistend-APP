package com.example.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineRestoreFileManifestVerifierTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var database: AppDatabase
    private val namespace = "rollback-file-verify-${UUID.randomUUID()}"
    private fun id() = UUID.randomUUID().toString()
    private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
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
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).openHelperFactory(OfflineBackupCoordinator.shared.sqliteFactory()).build()
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 3, propertyId = "house", name = "Haus"))
        val original = File(context.filesDir, "managed_documents/original.pdf").apply { parentFile!!.mkdirs(); writeText("original") }
        File(original.parentFile, "retained.pdf").writeText("retained")
        val external = File(root, "external.pdf").apply { writeText("external") }
        val missing = File(root, "missing.pdf")
        val paths = JSONArray(listOf(original.absolutePath, external.absolutePath, missing.absolutePath)).toString()
        database.receiptDao().insertReceipt(Receipt(id = 41, internalId = "stable", propertyId = "house", aussteller = "Beleg",
            datum = "2026-11-11", uhrzeit = "", bruttobetrag = 12.0, hauptkategorie = "Kosten", unterkategorie = "Reparatur",
            kontoNr = "4800", beschreibung = "Original", imageUrl = paths))
    }
    @After fun cleanup() {
        database.close(); PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        root.deleteRecursively()
    }
    private suspend fun bundle() = OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
        OfflineRestoreRollbackBundle.prepare(context, database, token)
    }
    private fun bind(bundle: OfflineRestoreRollbackBundle.Prepared): OfflineRestoreJournal.Entry =
        OfflineRestoreJournal(context).begin(id(), id(), bundle.id, bundle.manifest.length(), hash(bundle.manifest))
    private fun rebindManifest(bundle: OfflineRestoreRollbackBundle.Prepared, file: File, bytesKey: String, hashKey: String) {
        val master = JSONObject(bundle.manifest.readText()).put(bytesKey, file.length()).put(hashKey, hash(file))
        bundle.manifest.writeText(master.toString())
    }
    @Test fun validatesAllOwnedExternalAndUnavailableReferences() = runBlocking<Unit> {
        val bundle = bundle(); val journal = bind(bundle)
        val result = OfflineRestoreFileManifestVerifier.verify(context, journal)
        assertEquals(2, result.files.size); assertEquals(3, result.references.size)
        assertEquals(1, result.references.count { it.state == OfflineRestoreReferenceSnapshot.State.OWNED })
        assertEquals(1, result.references.count { it.state == OfflineRestoreReferenceSnapshot.State.EXTERNAL })
        assertEquals(1, result.references.count { it.state == OfflineRestoreReferenceSnapshot.State.UNAVAILABLE })
        assertEquals(journal, OfflineRestoreJournal(context).load())
        assertEquals(41, database.receiptDao().getAllReceiptsIncludingDeletedList().single().id)
    }
    @Test fun alteredOrUnexpectedPayloadsFailWithoutTouchingJournalOrLiveData() = runBlocking<Unit> {
        val bundle = bundle(); val journal = bind(bundle)
        val targets = listOf(File(bundle.ownedFiles.directory, bundle.ownedFiles.versions.first().stagedName),
            File(bundle.references.directory, bundle.references.references.first { it.state == OfflineRestoreReferenceSnapshot.State.EXTERNAL }.stagedName!!))
        targets.forEach { file ->
            val bytes = file.readBytes(); file.writeBytes(bytes.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() })
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestoreFileManifestVerifier.verify(context, journal) } }
            file.writeBytes(bytes)
        }
        val extra = File(bundle.ownedFiles.directory, "unexpected").apply { writeText("other") }
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestoreFileManifestVerifier.verify(context, journal) } }
        assertEquals("other", extra.readText()); extra.delete()
        OfflineRestoreFileManifestVerifier.verify(context, journal)
        assertEquals(journal, OfflineRestoreJournal(context).load())
        assertEquals(41, database.receiptDao().getAllReceiptsIncludingDeletedList().single().id)
    }
    @Test fun traversalInReboundOwnedManifestIsRejectedEvenWhenHashesMatch() = runBlocking<Unit> {
        val bundle = bundle(); val file = File(bundle.ownedFiles.directory, "manifest.json")
        val altered = JSONObject(file.readText())
        altered.getJSONArray("files").getJSONObject(0).put("path", "files/../escape")
        file.writeText(altered.toString()); rebindManifest(bundle, file, "filesManifestBytes", "filesManifestSha256")
        val journal = bind(bundle)
        OfflineRestoreRollbackVerifier.verifyIndex(context, journal)
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestoreFileManifestVerifier.verify(context, journal) } }
        assertEquals(journal, OfflineRestoreJournal(context).load())
    }
    @Test fun unknownOwnedReferenceIsRejectedEvenWhenManifestHashesMatch() = runBlocking<Unit> {
        val bundle = bundle(); val file = File(bundle.references.directory, "manifest.json")
        val altered = JSONObject(file.readText()); val entries = altered.getJSONArray("references")
        val owned = (0 until entries.length()).map { entries.getJSONObject(it) }.single { it.getString("state") == "OWNED" }
        owned.put("file", "file-999"); file.writeText(altered.toString())
        rebindManifest(bundle, file, "referencesManifestBytes", "referencesManifestSha256")
        val journal = bind(bundle)
        OfflineRestoreRollbackVerifier.verifyIndex(context, journal)
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestoreFileManifestVerifier.verify(context, journal) } }
        assertEquals(journal, OfflineRestoreJournal(context).load())
    }
}
