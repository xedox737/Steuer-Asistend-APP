package com.example.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.api.AiProviderSettings
import com.example.api.TestAndroidKeyStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineBackupRestoreInspectorTest {
    private lateinit var root: File
    private lateinit var source: Context
    private lateinit var target: Context
    private lateinit var sourceDb: AppDatabase
    private lateinit var targetDb: AppDatabase
    private lateinit var original: File
    private val namespace = "inspect-${UUID.randomUUID()}"
    private val secret = "test-only-synthetic-gemini-key-12345"
    private fun password() = "synthetic-recovery-password".toCharArray()
    private fun context(base: Context, name: String): Context = object : ContextWrapper(base) {
        private fun directory(child: String) = File(root, "$name/$child").apply { check(isDirectory || mkdirs()) }
        override fun getFilesDir() = directory("files")
        override fun getCacheDir() = directory("cache")
        override fun getNoBackupFilesDir() = directory("no-backup")
        override fun getSharedPreferences(store: String, mode: Int): SharedPreferences =
            base.getSharedPreferences("${namespace}_${name}_$store", mode)
    }
    @Before fun setup() = runBlocking<Unit> {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, namespace).apply { check(mkdirs()) }
        source = context(base, "source"); target = context(base, "target")
        sourceDb = Room.inMemoryDatabaseBuilder(source, AppDatabase::class.java)
            .openHelperFactory(OfflineBackupCoordinator.shared.sqliteFactory()).build()
        targetDb = Room.inMemoryDatabaseBuilder(target, AppDatabase::class.java)
            .openHelperFactory(OfflineBackupCoordinator.shared.sqliteFactory()).build()
        sourceDb.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 3, propertyId = "archive-house", name = "Archived house"))
        original = File(source.filesDir, "managed_documents/original.pdf").apply { parentFile!!.mkdirs(); writeText("%PDF-1.4 synthetic original") }
        sourceDb.receiptDao().insertReceipt(Receipt(id = 41, internalId = "archive-receipt", propertyId = "archive-house",
            aussteller = "Archived", datum = "2026-11-11", uhrzeit = "", bruttobetrag = 125.73,
            hauptkategorie = "Kosten", unterkategorie = "Reparatur", kontoNr = "4800", beschreibung = "Archived", imageUrl = original.absolutePath))
        targetDb.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 77, propertyId = "live-house", name = "Keep live"))
        target.getSharedPreferences("bank_transaction_notes", 0).edit().putString("note", "keep live note").commit()
    }
    @After fun cleanup() {
        sourceDb.close(); targetDb.close(); TestAndroidKeyStore.remove()
        listOf(source, target).forEach { context -> PersistentPreferenceInventory.stores.forEach {
            context.getSharedPreferences(it.name, 0).edit().clear().commit()
        }; context.getSharedPreferences("ai_provider_settings", 0).edit().clear().commit() }
        root.deleteRecursively()
    }
    private suspend fun archive(credentials: Boolean = false): ByteArray {
        if (credentials) {
            TestAndroidKeyStore.register()
            AiProviderSettings.storeGeminiKey(source, secret.toCharArray())
        }
        return OfflineBackupKeyEnvelope.create(password()).use { generation ->
            OfflineBackupSnapshot.capture(source, sourceDb, credentials = if (credentials)
                OfflineBackupSnapshot.CredentialCapture(generation, OfflineBackupCredentialSection.Consent(manual = true),
                    OfflineBackupCredentialSection.Trigger.MANUAL) else null).use { snapshot ->
                val bytes = ByteArrayOutputStream()
                OfflineBackupArchiveCrypto.encrypt(generation, bytes) { snapshot.writeArchive(it) }
                bytes.toByteArray()
            }
        }
    }
    private fun noStagingLeft() {
        val staging = File(target.filesDir, "offline-backup-staging")
        assertTrue(staging.listFiles().orEmpty().isEmpty())
    }
    private suspend fun liveUnchanged() {
        assertEquals("Keep live", targetDb.propertyDao().getAllProperties().single().name)
        assertTrue(targetDb.receiptDao().getAllReceiptsIncludingDeletedList().isEmpty())
        assertEquals(mapOf("note" to "keep live note"), target.getSharedPreferences("bank_transaction_notes", 0).all)
        assertFalse(File(target.filesDir, "managed_documents").exists())
        assertNull(OfflineBackupGenerationStore(target).loadCurrent())
    }

    @Test fun portablePreviewUsesArchivePasswordWithoutDeviceKeysAndNeverAppliesLiveData() = runBlocking<Unit> {
        val encrypted = archive(credentials = true)
        TestAndroidKeyStore.remove() // Simulate another installation with no source Keystore.
        val inputPassword = password()
        var closes = 0
        val token = OfflineBackupCoordinator.shared.beginCapture()
        val prepared = OfflineBackupRestoreInspector.inspect(target, inputPassword) {
            object : ByteArrayInputStream(encrypted) { override fun close() { closes++; super.close() } }
        }
        try {
            assertTrue(inputPassword.all { it == '\u0000' })
            assertEquals(1, closes)
            UUID.fromString(prepared.preview.backupId)
            assertEquals(18, prepared.preview.rowCounts.size)
            assertEquals(1L, prepared.preview.rowCounts["receipts"])
            assertEquals(1, prepared.preview.fileVersions)
            assertEquals(original.length(), prepared.preview.originalBytes)
            assertEquals(setOf(OfflineBackupCredentialSection.Provider.GEMINI), prepared.preview.credentialProviders)
            val receipt = prepared.probe.database.receiptDao().getAllReceiptsIncludingDeletedList().single()
            assertEquals(41, receipt.id); assertEquals("archive-receipt", receipt.internalId)
            assertArrayEquals(original.readBytes(), File(receipt.imageUrl!!).readBytes())
            val sealed = prepared.contents.parts.getValue("credentials.bin")
            assertNull(sealed.file)
            assertFalse(File(target.filesDir, "offline-backup-staging").walkTopDown().filter { it.isFile }.any {
                it.readBytes().toString(Charsets.UTF_8).contains(secret)
            })
            liveUnchanged()
            OfflineBackupCoordinator.shared.completeCapture(token)
        } finally { prepared.close(); prepared.close() }
        noStagingLeft()
    }

    @Test fun wrongPasswordTruncationAndAuthenticatedNonZipRejectAndCleanOnlyPrivateWork() = runBlocking<Unit> {
        val encrypted = archive()
        val nonZip = ByteArrayOutputStream().also { bytes -> OfflineBackupKeyEnvelope.create(password()).use { generation ->
            OfflineBackupArchiveCrypto.encrypt(generation, bytes) { it.write("not a ZIP".toByteArray()) }
        } }.toByteArray()
        for ((bytes, supplied) in listOf(encrypted to "wrong-recovery-password".toCharArray(),
            encrypted.copyOf(encrypted.size - 1) to password(), nonZip to password())) {
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineBackupRestoreInspector.inspect(target, supplied) { bytes.inputStream() }
            } }
            assertTrue(supplied.all { it == '\u0000' }); noStagingLeft(); liveUnchanged()
        }
    }

    @Test fun providerFailureAndCancellationConsumePasswordWithoutOpeningOrLeavingState() = runBlocking<Unit> {
        val supplied = password()
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
            OfflineBackupRestoreInspector.inspect(target, supplied) { throw SecurityException("private provider detail") }
        } }
        assertTrue(supplied.all { it == '\u0000' }); noStagingLeft()
        val cancelled = password()
        var opened = false
        assertThrows(kotlinx.coroutines.CancellationException::class.java) { runBlocking {
            coroutineContext[kotlinx.coroutines.Job]!!.cancel()
            OfflineBackupRestoreInspector.inspect(target, cancelled) { opened = true; byteArrayOf().inputStream() }
        } }
        assertFalse(opened); assertTrue(cancelled.all { it == '\u0000' }); noStagingLeft(); liveUnchanged()
    }
    private fun syncTestDirectory(directory: File) {
        java.nio.channels.FileChannel.open(directory.toPath(), java.nio.file.StandardOpenOption.READ).use { it.force(true) }
    }
    private fun atomicTestRename(from: File, to: File) {
        java.nio.file.Files.move(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    }
    private fun atomicTestLink(from: File, to: File) { java.nio.file.Files.createLink(to.toPath(), from.toPath()) }

    @Test fun verifiedArchiveFilesInstallWithoutOverwriteAndRollbackCleanly() = runBlocking<Unit> {
        val encrypted = archive()
        val prepared = OfflineBackupRestoreInspector.inspect(target, password()) { encrypted.inputStream() }
        try {
            val operation = UUID.randomUUID().toString(); val staging = UUID.randomUUID().toString()
            val plan = OfflineRestoreFilePlan.prepare(target, operation, prepared.probe.manifest)
            val journal = OfflineRestoreJournal(target)
            OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
                val snapshot = OfflineRestoreFilePlanStore.capture(target, token, staging, plan)
                val bundle = OfflineRestoreRollbackBundle.prepare(target, targetDb, token)
                val initial = journal.beginWithFilePlan(token, bundle.id, bundle.manifestBytes, bundle.manifestSha256, snapshot)
                val files = journal.advance(initial, OfflineRestoreJournal.Phase.FILES)
                assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                    OfflineRestoreStagedDatabase.prepare(target, token, files, prepared)
                } }
                repeat(2) { OfflineRestoreImportedFiles.install(target, token, files, prepared, ::syncTestDirectory, ::atomicTestRename, ::atomicTestLink) }
                assertEquals(1, plan.items.size)
                assertArrayEquals(original.readBytes(), plan.items.single().destination.readBytes())
                OfflineRestoreStagedDatabase.prepare(target, token, files, prepared).use { staged ->
                    val receipt = staged.database.receiptDao().getAllReceiptsIncludingDeletedList().single()
                    assertEquals(41, receipt.id); assertEquals("archive-receipt", receipt.internalId)
                    assertEquals(plan.aliases.getValue("original-0").absolutePath, receipt.imageUrl)
                    assertArrayEquals(original.readBytes(), File(receipt.imageUrl!!).readBytes())
                }
                assertEquals("Keep live", targetDb.propertyDao().getAllProperties().single().name)
                assertEquals("keep live note", target.getSharedPreferences("bank_transaction_notes", 0).getString("note", null))
                plan.items.single().destination.writeText("do not overwrite")
                assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                    OfflineRestoreImportedFiles.install(target, token, files, prepared, ::syncTestDirectory, ::atomicTestRename, ::atomicTestLink)
                } }
                assertEquals("do not overwrite", plan.items.single().destination.readText())
                plan.items.single().destination.writeBytes(original.readBytes())
                val rollback = journal.advance(files, OfflineRestoreJournal.Phase.ROLLBACK)
                OfflineRestoreImportedFiles.rollback(target, token, rollback, ::syncTestDirectory)
                assertTrue(plan.ownedDirectories.none { it.exists() }); assertEquals(rollback, journal.load())
            }
        } finally { prepared.close() }
        noStagingLeft(); liveUnchanged()
    }
    @Test fun changedStagedArchiveIsRejectedBeforeAnyDestinationIsCreated() = runBlocking<Unit> {
        val encrypted = archive()
        val prepared = OfflineBackupRestoreInspector.inspect(target, password()) { encrypted.inputStream() }
        try {
            val operation = UUID.randomUUID().toString(); val staging = UUID.randomUUID().toString()
            val plan = OfflineRestoreFilePlan.prepare(target, operation, prepared.probe.manifest)
            val journal = OfflineRestoreJournal(target)
            OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
                val snapshot = OfflineRestoreFilePlanStore.capture(target, token, staging, plan)
                val bundle = OfflineRestoreRollbackBundle.prepare(target, targetDb, token)
                val initial = journal.beginWithFilePlan(token, bundle.id, bundle.manifestBytes, bundle.manifestSha256, snapshot)
                val files = journal.advance(initial, OfflineRestoreJournal.Phase.FILES)
                val part = requireNotNull(prepared.contents.parts.getValue(plan.items.single().version.entry).file)
                part.appendText("tampered")
                assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                    OfflineRestoreImportedFiles.install(target, token, files, prepared, ::syncTestDirectory, ::atomicTestRename, ::atomicTestLink)
                } }
                assertTrue(plan.ownedDirectories.none { it.exists() }); assertEquals(files, journal.load())
                assertEquals("Keep live", targetDb.propertyDao().getAllProperties().single().name)
            }
        } finally { prepared.close() }
        noStagingLeft(); liveUnchanged()
    }

    @Test fun stagedDatabaseRewritesMultipleOriginalReferencesToInstalledFiles() = runBlocking<Unit> {
        val second = File(original.parentFile, "second.pdf").apply { writeText("second original") }
        val pages = org.json.JSONArray(listOf(original.absolutePath, second.absolutePath)).toString()
        sourceDb.openHelper.writableDatabase.execSQL("UPDATE receipts SET imageUrl = ? WHERE id = ?", arrayOf(pages, 41))
        val encrypted = archive()
        val prepared = OfflineBackupRestoreInspector.inspect(target, password()) { encrypted.inputStream() }
        try {
            val operation = UUID.randomUUID().toString(); val staging = UUID.randomUUID().toString()
            val plan = OfflineRestoreFilePlan.prepare(target, operation, prepared.probe.manifest)
            val journal = OfflineRestoreJournal(target)
            OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
                val snapshot = OfflineRestoreFilePlanStore.capture(target, token, staging, plan)
                val bundle = OfflineRestoreRollbackBundle.prepare(target, targetDb, token)
                val initial = journal.beginWithFilePlan(token, bundle.id, bundle.manifestBytes, bundle.manifestSha256, snapshot)
                val files = journal.advance(initial, OfflineRestoreJournal.Phase.FILES)
                OfflineRestoreImportedFiles.install(target, token, files, prepared, ::syncTestDirectory, ::atomicTestRename, ::atomicTestLink)
                OfflineRestoreStagedDatabase.prepare(target, token, files, prepared).use { staged ->
                    val receipt = staged.database.receiptDao().getAllReceiptsIncludingDeletedList().single()
                    val restored = org.json.JSONArray(receipt.imageUrl)
                    assertEquals(41, receipt.id); assertEquals(2, restored.length())
                    assertArrayEquals(original.readBytes(), File(restored.getString(0)).readBytes())
                    assertArrayEquals(second.readBytes(), File(restored.getString(1)).readBytes())
                    assertEquals(plan.aliases.values.map { it.canonicalPath }.toSet(),
                        (0 until restored.length()).map { File(restored.getString(it)).canonicalPath }.toSet())
                }
                assertEquals("Keep live", targetDb.propertyDao().getAllProperties().single().name)
                val rollback = journal.advance(files, OfflineRestoreJournal.Phase.ROLLBACK)
                OfflineRestoreImportedFiles.rollback(target, token, rollback, ::syncTestDirectory)
            }
        } finally { prepared.close() }
        noStagingLeft(); liveUnchanged()
    }

    @Test fun databaseImportPreservesIdsSequencesAndCanRollbackThePriorInventory() = runBlocking<Unit> {
        targetDb.managedDocumentDao().upsertMigrationJournal(DocumentMigrationJournal(
            documentId = "old-task", driveFileId = "synthetic-drive", targetFolderId = "old-folder", targetFilename = "old.pdf"))
        val encrypted = archive()
        val prepared = OfflineBackupRestoreInspector.inspect(target, password()) { encrypted.inputStream() }
        try {
            val operation = UUID.randomUUID().toString(); val staging = UUID.randomUUID().toString()
            val plan = OfflineRestoreFilePlan.prepare(target, operation, prepared.probe.manifest)
            val journal = OfflineRestoreJournal(target)
            OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
                val snapshot = OfflineRestoreFilePlanStore.capture(target, token, staging, plan)
                val bundle = OfflineRestoreRollbackBundle.prepare(target, targetDb, token)
                val initial = journal.beginWithFilePlan(token, bundle.id, bundle.manifestBytes, bundle.manifestSha256, snapshot)
                val files = journal.advance(initial, OfflineRestoreJournal.Phase.FILES)
                OfflineRestoreImportedFiles.install(target, token, files, prepared, ::syncTestDirectory, ::atomicTestRename, ::atomicTestLink)
                val database = journal.advance(files, OfflineRestoreJournal.Phase.DATABASE)
                repeat(2) { OfflineRestoreLiveDatabase.apply(target, targetDb, token, database, prepared) }
                assertEquals("archive-house", targetDb.propertyDao().getAllProperties().single().propertyId)
                val receipt = targetDb.receiptDao().getAllReceiptsIncludingDeletedList().single()
                assertEquals(41, receipt.id); assertEquals("archive-receipt", receipt.internalId)
                assertArrayEquals(original.readBytes(), File(receipt.imageUrl!!).readBytes())
                assertTrue(targetDb.managedDocumentDao().getMigrationJournal().isEmpty())
                val originalSequence = sourceDb.openHelper.readableDatabase.query("SELECT seq FROM sqlite_sequence WHERE name='receipts'").use { assertTrue(it.moveToFirst()); it.getLong(0) }
                val restoredSequence = targetDb.openHelper.readableDatabase.query("SELECT seq FROM sqlite_sequence WHERE name='receipts'").use { assertTrue(it.moveToFirst()); it.getLong(0) }
                assertEquals(originalSequence, restoredSequence)
                assertEquals("keep live note", target.getSharedPreferences("bank_transaction_notes", 0).getString("note", null))
                val rollback = journal.advance(database, OfflineRestoreJournal.Phase.ROLLBACK)
                OfflineRestoreDatabaseRollback.apply(target, targetDb, token, rollback)
                OfflineRestoreImportedFiles.rollback(target, token, rollback, ::syncTestDirectory)
                assertEquals("old-task", targetDb.managedDocumentDao().getMigrationJournal().single().documentId)
                assertEquals(rollback, journal.load())
            }
        } finally { prepared.close() }
        noStagingLeft(); liveUnchanged()
    }
    @Test fun databaseImportFailureRollsBackTheSqlTransactionWithoutLosingLiveData() = runBlocking<Unit> {
        val encrypted = archive()
        val prepared = OfflineBackupRestoreInspector.inspect(target, password()) { encrypted.inputStream() }
        try {
            val operation = UUID.randomUUID().toString(); val staging = UUID.randomUUID().toString()
            val plan = OfflineRestoreFilePlan.prepare(target, operation, prepared.probe.manifest)
            val journal = OfflineRestoreJournal(target)
            OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
                val snapshot = OfflineRestoreFilePlanStore.capture(target, token, staging, plan)
                val bundle = OfflineRestoreRollbackBundle.prepare(target, targetDb, token)
                val initial = journal.beginWithFilePlan(token, bundle.id, bundle.manifestBytes, bundle.manifestSha256, snapshot)
                val files = journal.advance(initial, OfflineRestoreJournal.Phase.FILES)
                OfflineRestoreImportedFiles.install(target, token, files, prepared, ::syncTestDirectory, ::atomicTestRename, ::atomicTestLink)
                val database = journal.advance(files, OfflineRestoreJournal.Phase.DATABASE)
                token.writing { targetDb.openHelper.writableDatabase.execSQL("CREATE TRIGGER test_reject_insert BEFORE INSERT ON receipts BEGIN SELECT RAISE(ABORT, 'synthetic'); END") }
                assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                    OfflineRestoreLiveDatabase.apply(target, targetDb, token, database, prepared)
                } }
                assertEquals("Keep live", targetDb.propertyDao().getAllProperties().single().name)
                assertTrue(targetDb.receiptDao().getAllReceiptsIncludingDeletedList().isEmpty())
                assertEquals(database, journal.load())
                token.writing { targetDb.openHelper.writableDatabase.execSQL("DROP TRIGGER test_reject_insert") }
                val rollback = journal.advance(database, OfflineRestoreJournal.Phase.ROLLBACK)
                OfflineRestoreDatabaseRollback.apply(target, targetDb, token, rollback)
                OfflineRestoreImportedFiles.rollback(target, token, rollback, ::syncTestDirectory)
            }
        } finally { prepared.close() }
        noStagingLeft(); liveUnchanged()
    }

    @Test fun preferenceImportKeepsSecretsDisablesAutomaticDriveAndRollsBack() = runBlocking<Unit> {
        source.getSharedPreferences("bank_transaction_notes", 0).edit().putString("note", "archive note").commit()
        source.getSharedPreferences("google_drive_prefs", 0).edit().putBoolean("auto_backup", true).commit()
        target.getSharedPreferences("google_drive_prefs", 0).edit().putBoolean("auto_backup", true).commit()
        val keys = target.getSharedPreferences("ai_provider_settings", 0)
        keys.edit().putString("openai_key_ciphertext", "synthetic-preserved").commit()
        val local = target.getSharedPreferences("app_installation_prefs", 0)
        local.edit().putString("device", "keep").commit()
        val encrypted = archive()
        val prepared = OfflineBackupRestoreInspector.inspect(target, password()) { encrypted.inputStream() }
        try {
            val operation = UUID.randomUUID().toString(); val staging = UUID.randomUUID().toString()
            val plan = OfflineRestoreFilePlan.prepare(target, operation, prepared.probe.manifest)
            val journal = OfflineRestoreJournal(target)
            OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
                val snapshot = OfflineRestoreFilePlanStore.capture(target, token, staging, plan)
                val bundle = OfflineRestoreRollbackBundle.prepare(target, targetDb, token)
                val initial = journal.beginWithFilePlan(token, bundle.id, bundle.manifestBytes, bundle.manifestSha256, snapshot)
                val files = journal.advance(initial, OfflineRestoreJournal.Phase.FILES)
                assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                    OfflineRestoreLivePreferences.apply(target, token, files, prepared)
                } }
                OfflineRestoreImportedFiles.install(target, token, files, prepared, ::syncTestDirectory, ::atomicTestRename, ::atomicTestLink)
                val database = journal.advance(files, OfflineRestoreJournal.Phase.DATABASE)
                OfflineRestoreLiveDatabase.apply(target, targetDb, token, database, prepared)
                val preferences = journal.advance(database, OfflineRestoreJournal.Phase.PREFERENCES)
                repeat(2) { OfflineRestoreLivePreferences.apply(target, token, preferences, prepared) }
                assertEquals("archive note", target.getSharedPreferences("bank_transaction_notes", 0).getString("note", null))
                assertFalse(target.getSharedPreferences("google_drive_prefs", 0).getBoolean("auto_backup", true))
                assertEquals("synthetic-preserved", keys.getString("openai_key_ciphertext", null))
                assertEquals("keep", local.getString("device", null))
                val rollback = journal.advance(preferences, OfflineRestoreJournal.Phase.ROLLBACK)
                OfflineRestorePreferenceRollback.apply(target, token, rollback)
                OfflineRestoreDatabaseRollback.apply(target, targetDb, token, rollback)
                OfflineRestoreImportedFiles.rollback(target, token, rollback, ::syncTestDirectory)
                assertTrue(target.getSharedPreferences("google_drive_prefs", 0).getBoolean("auto_backup", false))
                assertEquals("synthetic-preserved", keys.getString("openai_key_ciphertext", null))
                assertEquals("keep", local.getString("device", null)); assertEquals(rollback, journal.load())
            }
        } finally { prepared.close(); local.edit().clear().commit() }
        noStagingLeft(); liveUnchanged()
    }
    @Test fun preferenceImportRejectsChangedArchiveBeforeWritingPreferences() = runBlocking<Unit> {
        source.getSharedPreferences("bank_transaction_notes", 0).edit().putString("note", "archive note").commit()
        target.getSharedPreferences("google_drive_prefs", 0).edit().putBoolean("auto_backup", true).commit()
        val encrypted = archive()
        val prepared = OfflineBackupRestoreInspector.inspect(target, password()) { encrypted.inputStream() }
        try {
            val operation = UUID.randomUUID().toString(); val staging = UUID.randomUUID().toString()
            val plan = OfflineRestoreFilePlan.prepare(target, operation, prepared.probe.manifest)
            val journal = OfflineRestoreJournal(target)
            OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
                val snapshot = OfflineRestoreFilePlanStore.capture(target, token, staging, plan)
                val bundle = OfflineRestoreRollbackBundle.prepare(target, targetDb, token)
                val initial = journal.beginWithFilePlan(token, bundle.id, bundle.manifestBytes, bundle.manifestSha256, snapshot)
                val files = journal.advance(initial, OfflineRestoreJournal.Phase.FILES)
                OfflineRestoreImportedFiles.install(target, token, files, prepared, ::syncTestDirectory, ::atomicTestRename, ::atomicTestLink)
                val database = journal.advance(files, OfflineRestoreJournal.Phase.DATABASE)
                OfflineRestoreLiveDatabase.apply(target, targetDb, token, database, prepared)
                val preferences = journal.advance(database, OfflineRestoreJournal.Phase.PREFERENCES)
                requireNotNull(prepared.contents.parts.getValue("metadata.json").file).appendText("tampered")
                assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                    OfflineRestoreLivePreferences.apply(target, token, preferences, prepared)
                } }
                assertEquals("keep live note", target.getSharedPreferences("bank_transaction_notes", 0).getString("note", null))
                assertTrue(target.getSharedPreferences("google_drive_prefs", 0).getBoolean("auto_backup", false))
                val rollback = journal.advance(preferences, OfflineRestoreJournal.Phase.ROLLBACK)
                OfflineRestoreDatabaseRollback.apply(target, targetDb, token, rollback)
                OfflineRestoreImportedFiles.rollback(target, token, rollback, ::syncTestDirectory)
            }
        } finally { prepared.close() }
        noStagingLeft(); liveUnchanged()
    }

    @Test fun selectedCredentialsRestoreOnAnotherKeystoreAndRollbackWithoutReplacingExistingAliases() = runBlocking<Unit> {
        val encrypted = archive(credentials = true)
        TestAndroidKeyStore.remove(); TestAndroidKeyStore.register()
        val oldGemini = "synthetic-target-gemini-key-67890"
        val oldOpenAi = "sk-" + "x".repeat(30)
        AiProviderSettings.storeGeminiKey(target, oldGemini.toCharArray())
        AiProviderSettings.storeOpenAiKey(target, oldOpenAi.toCharArray())
        val before = AiProviderSettings.captureRollbackCiphertexts(target)
        val keystore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val aliases = keystore.aliases().toList().toSet()
        val prepared = OfflineBackupRestoreInspector.inspect(target, password()) { encrypted.inputStream() }
        try {
            val operation = UUID.randomUUID().toString(); val staging = UUID.randomUUID().toString()
            val plan = OfflineRestoreFilePlan.prepare(target, operation, prepared.probe.manifest)
            val journal = OfflineRestoreJournal(target)
            OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
                val snapshot = OfflineRestoreFilePlanStore.capture(target, token, staging, plan)
                val bundle = OfflineRestoreRollbackBundle.prepare(target, targetDb, token, includeCredentials = true)
                val initial = journal.beginWithFilePlan(token, bundle.id, bundle.manifestBytes, bundle.manifestSha256, snapshot)
                val files = journal.advance(initial, OfflineRestoreJournal.Phase.FILES)
                OfflineRestoreImportedFiles.install(target, token, files, prepared, ::syncTestDirectory, ::atomicTestRename, ::atomicTestLink)
                val database = journal.advance(files, OfflineRestoreJournal.Phase.DATABASE)
                OfflineRestoreLiveDatabase.apply(target, targetDb, token, database, prepared)
                val preferences = journal.advance(database, OfflineRestoreJournal.Phase.PREFERENCES)
                OfflineRestoreLivePreferences.apply(target, token, preferences, prepared)
                val alias = "steuer_assistent_gemini_key_v1"
                val oldKey = requireNotNull(keystore.getKey(alias, null))
                keystore.deleteEntry(alias)
                assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                    AiProviderSettings.importBackupCredentials(target, token, preferences, prepared)
                } }
                assertFalse(keystore.containsAlias(alias))
                keystore.setKeyEntry(alias, oldKey, null, null)
                assertEquals(before, AiProviderSettings.captureRollbackCiphertexts(target))
                AiProviderSettings.importBackupCredentials(target, token, preferences, prepared)
                val restored = requireNotNull(AiProviderSettings.getGeminiKey(target))
                try { assertTrue(restored.contentEquals(secret.toCharArray())) } finally { restored.fill('\u0000') }
                assertEquals(before.getValue(OfflineBackupCredentialSection.Provider.OPENAI),
                    AiProviderSettings.captureRollbackCiphertexts(target).getValue(OfflineBackupCredentialSection.Provider.OPENAI))
                assertEquals(aliases, keystore.aliases().toList().toSet())
                val rollback = journal.advance(preferences, OfflineRestoreJournal.Phase.ROLLBACK)
                AiProviderSettings.restoreRollbackCiphertexts(target, token, rollback)
                OfflineRestorePreferenceRollback.apply(target, token, rollback)
                OfflineRestoreDatabaseRollback.apply(target, targetDb, token, rollback)
                OfflineRestoreImportedFiles.rollback(target, token, rollback, ::syncTestDirectory)
                assertEquals(before, AiProviderSettings.captureRollbackCiphertexts(target))
                assertEquals(aliases, keystore.aliases().toList().toSet()); assertEquals(rollback, journal.load())
            }
        } finally { prepared.close() }
        noStagingLeft(); liveUnchanged()
    }
    @Test fun credentialsRemainUnchangedWhenImportWasNotSelected() = runBlocking<Unit> {
        val encrypted = archive(credentials = true)
        TestAndroidKeyStore.remove(); TestAndroidKeyStore.register()
        AiProviderSettings.storeGeminiKey(target, "synthetic-target-gemini-key-67890".toCharArray())
        val before = AiProviderSettings.captureRollbackCiphertexts(target)
        val prepared = OfflineBackupRestoreInspector.inspect(target, password()) { encrypted.inputStream() }
        try {
            val operation = UUID.randomUUID().toString(); val staging = UUID.randomUUID().toString()
            val plan = OfflineRestoreFilePlan.prepare(target, operation, prepared.probe.manifest)
            val journal = OfflineRestoreJournal(target)
            OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
                val snapshot = OfflineRestoreFilePlanStore.capture(target, token, staging, plan)
                val bundle = OfflineRestoreRollbackBundle.prepare(target, targetDb, token, includeCredentials = false)
                val initial = journal.beginWithFilePlan(token, bundle.id, bundle.manifestBytes, bundle.manifestSha256, snapshot)
                val files = journal.advance(initial, OfflineRestoreJournal.Phase.FILES)
                OfflineRestoreImportedFiles.install(target, token, files, prepared, ::syncTestDirectory, ::atomicTestRename, ::atomicTestLink)
                val database = journal.advance(files, OfflineRestoreJournal.Phase.DATABASE)
                OfflineRestoreLiveDatabase.apply(target, targetDb, token, database, prepared)
                val preferences = journal.advance(database, OfflineRestoreJournal.Phase.PREFERENCES)
                OfflineRestoreLivePreferences.apply(target, token, preferences, prepared)
                AiProviderSettings.importBackupCredentials(target, token, preferences, prepared)
                assertEquals(before, AiProviderSettings.captureRollbackCiphertexts(target))
                val rollback = journal.advance(preferences, OfflineRestoreJournal.Phase.ROLLBACK)
                OfflineRestorePreferenceRollback.apply(target, token, rollback)
                OfflineRestoreDatabaseRollback.apply(target, targetDb, token, rollback)
                OfflineRestoreImportedFiles.rollback(target, token, rollback, ::syncTestDirectory)
            }
        } finally { prepared.close() }
        noStagingLeft(); liveUnchanged()
    }

}
