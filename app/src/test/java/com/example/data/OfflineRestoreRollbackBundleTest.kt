package com.example.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.api.TestAndroidKeyStore
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.runBlocking
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
class OfflineRestoreRollbackBundleTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var database: AppDatabase
    private lateinit var original: File
    private lateinit var missing: File
    private val namespace = "rollback-bundle-${UUID.randomUUID()}"
    private val coordinator get() = OfflineBackupCoordinator.shared
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
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).openHelperFactory(coordinator.sqliteFactory()).build()
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 3, propertyId = "house", name = "Haus"))
        missing = File(root, "already-missing.pdf")
        original = File(context.filesDir, "managed_documents/retained.pdf").apply { parentFile!!.mkdirs(); writeText("retained original") }
        database.receiptDao().insertReceipt(Receipt(id = 41, internalId = "stable", propertyId = "house", aussteller = "Beleg",
            datum = "2026-11-11", uhrzeit = "", bruttobetrag = 12.0, hauptkategorie = "Kosten", unterkategorie = "Reparatur",
            kontoNr = "4800", beschreibung = "Original", imageUrl = missing.absolutePath))
        context.getSharedPreferences("bank_transaction_notes", 0).edit().putString("note", "keep note").commit()
    }
    @After fun cleanup() {
        database.close()
        PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        root.deleteRecursively()
    }
    @Test fun bundleBindsAllArtifactsAndRecordsMissingOriginalWithoutApplyingRestore() = runBlocking<Unit> {
        coordinator.beginMaintenance().use { token ->
            val bundle = OfflineRestoreRollbackBundle.prepare(context, database, token)
            assertEquals(bundle.id, bundle.database.id); assertEquals(bundle.id, bundle.preferences.id)
            assertEquals(bundle.id, bundle.ownedFiles.id); assertEquals(bundle.id, bundle.references.id)
            val record = JSONObject(bundle.manifest.readText())
            assertEquals(bundle.id, record.getString("id"))
            assertEquals(bundle.database.sha256, record.getString("databaseSha256"))
            assertEquals(bundle.preferences.sha256, record.getString("preferencesSha256"))
            assertEquals(bundle.ownedFiles.manifestSha256, record.getString("filesManifestSha256"))
            assertEquals(bundle.references.manifestSha256, record.getString("referencesManifestSha256"))
            assertEquals(1, record.getInt("unavailableCount"))
            assertEquals(OfflineRestoreReferenceSnapshot.State.UNAVAILABLE, bundle.references.references.single().state)
            assertEquals(41, database.receiptDao().getAllReceiptsIncludingDeletedList().single().id)
            assertEquals("keep note", context.getSharedPreferences("bank_transaction_notes", 0).getString("note", null))
            assertEquals("retained original", original.readText())
            assertEquals(64, bundle.manifestSha256.length); assertEquals(bundle.manifest.length(), bundle.manifestBytes)
        }
    }
    @Test fun laterFailureRemovesOnlyNewArtifactsAndKeepsExistingData() = runBlocking<Unit> {
        val directory = File(context.noBackupFilesDir, "offline-backup-state").apply { mkdirs() }
        val sentinel = File(directory, "keep.txt").apply { writeText("preserve") }
        val link = File(original.parentFile, "unsafe-link")
        Files.createSymbolicLink(link.toPath(), original.toPath())
        coordinator.beginMaintenance().use { token ->
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineRestoreRollbackBundle.prepare(context, database, token)
            } }
        }
        assertEquals("preserve", sentinel.readText())
        assertEquals(listOf("keep.txt"), directory.list()!!.toList())
        assertEquals("retained original", original.readText())
        assertEquals(41, database.receiptDao().getAllReceiptsIncludingDeletedList().single().id)
    }
    @Test fun foreignMaintenanceCannotCreateAnyBundle() = runBlocking<Unit> {
        val foreign = OfflineBackupCoordinator().beginMaintenance()
        try { assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
            OfflineRestoreRollbackBundle.prepare(context, database, foreign)
        } } } finally { foreign.close() }
        assertFalse(File(context.noBackupFilesDir, "offline-backup-state").exists())
    }
    @Test fun credentialSelectionIsExplicitAndDamagedKeysDoNotBlockUnchangedMode() = runBlocking<Unit> {
        TestAndroidKeyStore.register()
        val prefs = context.getSharedPreferences("ai_provider_settings", 0)
        try {
            prefs.edit().putString("openai_key_ciphertext", "damaged").commit()
            val before = prefs.all.toMap()
            coordinator.beginMaintenance().use { token ->
                val unchanged = OfflineRestoreRollbackBundle.prepare(context, database, token)
                assertNull(unchanged.credentials)
                assertEquals("UNCHANGED", JSONObject(unchanged.manifest.readText()).getString("credentialMode"))
                val entry = OfflineRestoreJournal(context).begin(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                    unchanged.id, unchanged.manifestBytes, unchanged.manifestSha256)
                assertNull(OfflineRestoreRollbackVerifier.verifyIndex(context, entry).credentials)
                assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                    OfflineRestoreRollbackBundle.prepare(context, database, token, includeCredentials = true)
                } }
            }
            assertEquals(before, prefs.all)
        } finally { prefs.edit().clear().commit(); TestAndroidKeyStore.remove() }
    }
    @Test fun selectedCredentialSnapshotIsBoundToJournalAndTamperingIsRejected() = runBlocking<Unit> {
        TestAndroidKeyStore.register()
        val prefs = context.getSharedPreferences("ai_provider_settings", 0)
        try {
            com.example.api.AiProviderSettings.storeGeminiKey(context, "synthetic-key-for-bundle".toCharArray())
            val before = prefs.all.toMap()
            coordinator.beginMaintenance().use { token ->
                val bundle = OfflineRestoreRollbackBundle.prepare(context, database, token, includeCredentials = true)
                val credentials = requireNotNull(bundle.credentials)
                val record = JSONObject(bundle.manifest.readText())
                assertEquals("RESTORE", record.getString("credentialMode"))
                assertEquals(credentials.sha256, record.getString("credentialSha256"))
                val entry = OfflineRestoreJournal(context).begin(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                    bundle.id, bundle.manifestBytes, bundle.manifestSha256)
                assertNotNull(OfflineRestoreRollbackVerifier.verifyIndex(context, entry).credentials)
                credentials.file.appendText(" ")
                assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                    OfflineRestoreRollbackVerifier.verifyIndex(context, entry)
                } }
                assertEquals(before, prefs.all)
            }
        } finally { prefs.edit().clear().commit(); TestAndroidKeyStore.remove() }
    }

}
