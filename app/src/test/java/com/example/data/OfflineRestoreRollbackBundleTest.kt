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

    @Test fun credentialRollbackRestoresEncryptedRecordsWithoutChangingAliasesOrSelection() = runBlocking<Unit> {
        TestAndroidKeyStore.register()
        val prefs = context.getSharedPreferences("ai_provider_settings", 0)
        val settings = com.example.api.AiProviderSettings
        try {
            settings.storeGeminiKey(context, "synthetic-original-key".toCharArray())
            val original = settings.captureRollbackCiphertexts(context)
            coordinator.beginMaintenance().use { token ->
                val bundle = OfflineRestoreRollbackBundle.prepare(context, database, token, includeCredentials = true)
                val journal = OfflineRestoreJournal(context)
                val prepared = journal.begin(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                    bundle.id, bundle.manifestBytes, bundle.manifestSha256)
                val rollback = journal.advance(prepared, OfflineRestoreJournal.Phase.ROLLBACK)
                token.writing {
                    settings.storeGeminiKey(context, "synthetic-replacement-key".toCharArray())
                    settings.storeOpenAiKey(context, ("sk-" + "x".repeat(30)).toCharArray())
                    prefs.edit().putString("openai_model", "preserve selection").commit()
                }
                val aliases = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.aliases().toList().toSet()
                repeat(2) { settings.restoreRollbackCiphertexts(context, token, rollback) }
                assertEquals(original, settings.captureRollbackCiphertexts(context))
                assertEquals("preserve selection", prefs.getString("openai_model", null))
                assertEquals(aliases, java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.aliases().toList().toSet())
                assertEquals(rollback, journal.load())
            }
        } finally { prefs.edit().clear().commit(); TestAndroidKeyStore.remove() }
    }

    @Test fun journalBindsFilePlanToOperationAndRejectsTamperingBeforeAndAfterBegin() = runBlocking<Unit> {
        val operationId = UUID.randomUUID().toString(); val stagingId = UUID.randomUUID().toString()
        val payload = OfflineBackupManifest.Reference("files/0", "a".repeat(64), 10)
        val manifest = OfflineBackupManifest.Checked(emptyMap(), mapOf("original-0" to payload.entry), listOf(
            OfflineBackupManifest.Version("files/managed_documents/source.pdf", "files/managed_documents/source.pdf",
                OfflineBackupFileInventory.Kind.ORIGINAL, payload.entry)), listOf(payload), null, emptySet(),
            OfflineBackupArchiveInfo("2026-11-17T00:00:00Z", 1, "test"))
        val plan = OfflineRestoreFilePlan.prepare(context, operationId, manifest)
        val journal = OfflineRestoreJournal(context)
        coordinator.beginMaintenance().use { token ->
            val snapshot = OfflineRestoreFilePlanStore.capture(context, token, stagingId, plan)
            val bundle = OfflineRestoreRollbackBundle.prepare(context, database, token)
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                journal.beginWithFilePlan(token, bundle.id, bundle.manifestBytes, bundle.manifestSha256,
                    snapshot.copy(operationId = UUID.randomUUID().toString()))
            } }
            assertNull(journal.load())
            val original = snapshot.file.readBytes(); snapshot.file.appendText(" ")
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                journal.beginWithFilePlan(token, bundle.id, bundle.manifestBytes, bundle.manifestSha256, snapshot)
            } }
            assertNull(journal.load()); snapshot.file.writeBytes(original)
            val occupied = plan.ownedDirectories.single().apply { check(mkdirs()) }
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                journal.beginWithFilePlan(token, bundle.id, bundle.manifestBytes, bundle.manifestSha256, snapshot)
            } }
            assertNull(journal.load()); assertTrue(occupied.delete())
            val prepared = journal.beginWithFilePlan(token, bundle.id, bundle.manifestBytes, bundle.manifestSha256, snapshot)
            assertEquals(snapshot.bytes, prepared.filePlanBytes); assertEquals(snapshot.sha256, prepared.filePlanSha256)
            assertEquals(plan, OfflineRestoreRollbackVerifier.verifyIndex(context, prepared).filePlan)
            val rollback = journal.advance(prepared, OfflineRestoreJournal.Phase.ROLLBACK)
            assertEquals(rollback, journal.load()); assertEquals(plan, OfflineRestoreRollbackVerifier.verifyIndex(context, rollback).filePlan)
            snapshot.file.appendText(" ")
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineRestoreRollbackVerifier.verifyIndex(context, rollback)
            } }
            assertEquals(rollback, journal.load())
            assertEquals(41, database.receiptDao().getAllReceiptsIncludingDeletedList().single().id)
        }
    }

    private fun syncTestDirectory(directory: File) {
        java.nio.channels.FileChannel.open(directory.toPath(), java.nio.file.StandardOpenOption.READ).use { it.force(true) }
    }
    private fun atomicTestRename(from: File, to: File) {
        java.nio.file.Files.move(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }
    private fun importManifest(): OfflineBackupManifest.Checked {
        val contents = listOf("original bytes", "export bytes")
        val payloads = contents.mapIndexed { index, data ->
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(data.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
            OfflineBackupManifest.Reference("files/$index", hash, data.toByteArray().size.toLong())
        }
        return OfflineBackupManifest.Checked(emptyMap(), mapOf("original-0" to "files/0"), listOf(
            OfflineBackupManifest.Version("files/managed_documents/import.pdf", "files/managed_documents/import.pdf", OfflineBackupFileInventory.Kind.ORIGINAL, "files/0"),
            OfflineBackupManifest.Version("cache/exports/export.pdf", "cache/exports/export.pdf", OfflineBackupFileInventory.Kind.SAVED_EXPORT, "files/1")
        ), payloads, null, emptySet(), OfflineBackupArchiveInfo("2026-11-17T00:00:00Z", 1, "test"))
    }
    @Test fun rollbackRemovesOnlyVerifiedFilesFromJournalOwnedDirectoriesAndCanRepeat() = runBlocking<Unit> {
        val operation = UUID.randomUUID().toString(); val staging = UUID.randomUUID().toString()
        val plan = OfflineRestoreFilePlan.prepare(context, operation, importManifest())
        val journal = OfflineRestoreJournal(context)
        coordinator.beginMaintenance().use { token ->
            val snapshot = OfflineRestoreFilePlanStore.capture(context, token, staging, plan)
            val bundle = OfflineRestoreRollbackBundle.prepare(context, database, token)
            val prepared = journal.beginWithFilePlan(token, bundle.id, bundle.manifestBytes, bundle.manifestSha256, snapshot)
            val files = journal.advance(prepared, OfflineRestoreJournal.Phase.FILES)
            OfflineRestoreImportedFiles.claim(context, token, files, ::syncTestDirectory, ::atomicTestRename)
            OfflineRestoreImportedFiles.claim(context, token, files, ::syncTestDirectory, ::atomicTestRename)
            plan.items.forEachIndexed { index, item ->
                check(item.destination.parentFile!!.mkdirs()); item.destination.writeText(if (index == 0) "original bytes" else "export bytes")
            }
            val rollback = journal.advance(files, OfflineRestoreJournal.Phase.ROLLBACK)
            repeat(2) { OfflineRestoreImportedFiles.rollback(context, token, rollback, ::syncTestDirectory) }
            assertTrue(plan.ownedDirectories.none { it.exists() })
            assertEquals("retained original", original.readText()); assertEquals(rollback, journal.load())
        }
    }
    @Test fun rollbackKeepsEveryFileWhenAnyOwnedDirectoryContainsUnknownOrChangedData() = runBlocking<Unit> {
        val operation = UUID.randomUUID().toString(); val staging = UUID.randomUUID().toString()
        val plan = OfflineRestoreFilePlan.prepare(context, operation, importManifest())
        val journal = OfflineRestoreJournal(context)
        coordinator.beginMaintenance().use { token ->
            val snapshot = OfflineRestoreFilePlanStore.capture(context, token, staging, plan)
            val bundle = OfflineRestoreRollbackBundle.prepare(context, database, token)
            val prepared = journal.beginWithFilePlan(token, bundle.id, bundle.manifestBytes, bundle.manifestSha256, snapshot)
            val files = journal.advance(prepared, OfflineRestoreJournal.Phase.FILES)
            OfflineRestoreImportedFiles.claim(context, token, files, ::syncTestDirectory, ::atomicTestRename)
            plan.items.forEachIndexed { index, item ->
                check(item.destination.parentFile!!.mkdirs()); item.destination.writeText(if (index == 0) "original bytes" else "changed export")
            }
            val rollback = journal.advance(files, OfflineRestoreJournal.Phase.ROLLBACK)
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineRestoreImportedFiles.rollback(context, token, rollback, ::syncTestDirectory)
            } }
            assertEquals("original bytes", plan.items[0].destination.readText())
            assertEquals("changed export", plan.items[1].destination.readText())
            plan.items[1].destination.writeText("export bytes")
            val unknown = File(plan.ownedDirectories.first(), "other-user-data").apply { writeText("keep") }
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineRestoreImportedFiles.rollback(context, token, rollback, ::syncTestDirectory)
            } }
            assertEquals("keep", unknown.readText()); assertEquals("original bytes", plan.items[0].destination.readText())
            assertEquals("retained original", original.readText()); assertEquals(rollback, journal.load())
        }
    }

    @Test fun committedCleanupRemovesOnlyMarkersAndCanResumeWithoutDeletingPayloads() = runBlocking<Unit> {
        val operation = UUID.randomUUID().toString(); val staging = UUID.randomUUID().toString()
        val plan = OfflineRestoreFilePlan.prepare(context, operation, importManifest())
        val journal = OfflineRestoreJournal(context)
        coordinator.beginMaintenance().use { token ->
            val snapshot = OfflineRestoreFilePlanStore.capture(context, token, staging, plan)
            val bundle = OfflineRestoreRollbackBundle.prepare(context, database, token)
            val prepared = journal.beginWithFilePlan(token, bundle.id, bundle.manifestBytes, bundle.manifestSha256, snapshot)
            val files = journal.advance(prepared, OfflineRestoreJournal.Phase.FILES)
            OfflineRestoreImportedFiles.claim(context, token, files, ::syncTestDirectory, ::atomicTestRename)
            plan.items.forEachIndexed { index, item ->
                check(item.destination.parentFile!!.mkdirs()); item.destination.writeText(if (index == 0) "original bytes" else "export bytes")
            }
            val database = journal.advance(files, OfflineRestoreJournal.Phase.DATABASE)
            val preferences = journal.advance(database, OfflineRestoreJournal.Phase.PREFERENCES)
            val committed = journal.advance(preferences, OfflineRestoreJournal.Phase.COMMITTED)
            val unexpected = File(plan.ownedDirectories.first(), "unknown").apply { writeText("keep") }
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineRestoreImportedFiles.finish(context, token, committed, ::syncTestDirectory)
            } }
            assertEquals("keep", unexpected.readText()); assertTrue(unexpected.delete())
            repeat(2) { OfflineRestoreImportedFiles.finish(context, token, committed, ::syncTestDirectory) }
            assertEquals("original bytes", plan.items[0].destination.readText())
            assertEquals("export bytes", plan.items[1].destination.readText())
            assertEquals("retained original", original.readText()); assertEquals(committed, journal.load())
            assertTrue(plan.ownedDirectories.all { File(it, ".immopilot-restore-owner").exists().not() })
            val ops = OfflineRestoreRecovery.FileOperations(::syncTestDirectory, ::atomicTestRename)
            assertEquals(committed, OfflineRestoreRecovery.recoverHeld(context, null, token, committed, ops))
            assertEquals(committed, journal.load())
        }
    }

    @Test fun credentialRollbackRejectsACommitThatDoesNotRestoreCiphertext() = runBlocking<Unit> {
        TestAndroidKeyStore.register()
        val prefs = context.getSharedPreferences("ai_provider_settings", 0)
        val settings = com.example.api.AiProviderSettings
        try {
            settings.storeGeminiKey(context, "synthetic-original-gemini-key".toCharArray())
            coordinator.beginMaintenance().use { token ->
                val bundle = OfflineRestoreRollbackBundle.prepare(context, database, token, includeCredentials = true)
                val journal = OfflineRestoreJournal(context)
                val prepared = journal.begin(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                    bundle.id, bundle.manifestBytes, bundle.manifestSha256)
                val rollback = journal.advance(prepared, OfflineRestoreJournal.Phase.ROLLBACK)
                token.writing { settings.storeGeminiKey(context, "synthetic-changed-gemini-key".toCharArray()) }
                val changed = settings.captureRollbackCiphertexts(context)
                val unreliable = object : ContextWrapper(context) {
                    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                        val actual = context.getSharedPreferences(name, mode)
                        if (name != "ai_provider_settings") return actual
                        return object : SharedPreferences by actual {
                            override fun edit(): SharedPreferences.Editor {
                                val editor = actual.edit()
                                return object : SharedPreferences.Editor by editor { override fun commit() = true }
                            }
                        }
                    }
                }
                assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                    settings.restoreRollbackCiphertexts(unreliable, token, rollback)
                } }
                assertEquals(changed, settings.captureRollbackCiphertexts(context)); assertEquals(rollback, journal.load())
            }
        } finally { prefs.edit().clear().commit(); TestAndroidKeyStore.remove() }
    }

}
