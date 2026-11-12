package com.example.data

import android.content.Context
import com.example.api.AiProviderSettings
import com.example.api.TestAndroidKeyStore
import java.security.MessageDigest
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
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
class OfflineBackupSnapshotTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var database: AppDatabase
    private lateinit var original: File
    private lateinit var retained: File
    private lateinit var export: File
    private lateinit var priorArchive: File
    private val bytes = "%PDF-1.4\nOriginal snapshot bytes\n%%EOF".toByteArray()
    private val namespace = "snapshot-${UUID.randomUUID()}"
    private val receipt = Receipt(id = 41, internalId = "snapshot-receipt", propertyId = "house", aussteller = "Original",
        datum = "2026-11-10", uhrzeit = "", bruttobetrag = 125.73, hauptkategorie = "Kosten", unterkategorie = "Reparatur",
        kontoNr = "4800", beschreibung = "old metadata")

    @Before fun setUp(): Unit = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, namespace).apply { check(mkdirs()) }
        context = object : ContextWrapper(base) {
            override fun getFilesDir() = File(root, "files").apply { check(isDirectory || mkdirs()) }
            override fun getCacheDir() = File(root, "cache").apply { check(isDirectory || mkdirs()) }
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = base.getSharedPreferences("${namespace}_$name", mode)
        }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .openHelperFactory(OfflineBackupCoordinator.shared.sqliteFactory()).build()
        original = File(context.filesDir, "managed_documents/original.pdf").apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        retained = File(context.filesDir, "managed_documents/old-version.pdf").apply { writeBytes(bytes) }
        export = File(context.cacheDir, "exports/Kontoauszug_Bankabgleich.csv").apply { parentFile!!.mkdirs(); writeText("original export") }
        priorArchive = File(context.filesDir, "offline-backup-staging/keep-valid.immopilot").apply { parentFile!!.mkdirs(); writeText("keep prior archive") }
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 3, propertyId = "house", name = "Haus"))
        database.receiptDao().insertReceipt(receipt.copy(imageUrl = original.absolutePath))
        // Deliberately legacy: no preexisting hash/size. Only the global generation can bind its owned bytes.
        database.managedDocumentDao().upsert(ManagedDocument("document", "house", receiptInternalId = receipt.internalId,
            title = "Original", localUri = original.absolutePath, originalFilename = "original.pdf", storedFilename = "original.pdf"))
        context.getSharedPreferences("bank_transaction_notes", 0).edit().putString("transaction", "old note").commit()
    }
    @After fun tearDown() {
        database.close()
        PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        root.deleteRecursively()
    }
    private fun noOwnedRunLeft() {
        assertTrue(File(context.filesDir, "offline-backup-staging").listFiles()!!.none { it.isDirectory && it.name.startsWith("snapshot-") })
        assertEquals("keep prior archive", priorArchive.readText())
    }
    private suspend fun entries(snapshot: OfflineBackupSnapshot): Map<String, ByteArray> {
        val output = ByteArrayOutputStream()
        snapshot.writeArchive(output)
        return ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip -> buildMap {
            while (true) { val entry = zip.nextEntry ?: break; put(entry.name, zip.readBytes()) }
        } }
    }
    private fun field(metadata: JSONObject, table: String, name: String): Any {
        val value = metadata.getJSONObject("tables").getJSONObject(table)
        val columns = value.getJSONArray("columns")
        val index = (0 until columns.length()).single { columns.getJSONObject(it).getString("name") == name }
        return value.getJSONArray("rows").getJSONArray(0).get(index)
    }

    @Test fun capturesAllRetainedVersionsAndExportBytesWhileDeduplicatingOnlyPayloads() = runBlocking {
        OfflineBackupSnapshot.capture(context, database).use { snapshot ->
            assertEquals(3, snapshot.summary.files)
            assertEquals(2, snapshot.summary.payloads)
            assertEquals(1L, snapshot.summary.rowCounts["receipts"])
            val entries = entries(snapshot)
            val manifest = JSONObject(entries.getValue("manifest.json").toString(Charsets.UTF_8))
            val metadata = JSONObject(entries.getValue("metadata.json").toString(Charsets.UTF_8))
            assertEquals(3, manifest.getJSONArray("files").length())
            assertEquals(2, manifest.getJSONArray("payloads").length())
            assertEquals(setOf("files/managed_documents/original.pdf", "files/managed_documents/old-version.pdf",
                "cache/exports/Kontoauszug_Bankabgleich.csv"), (0 until 3).map {
                manifest.getJSONArray("files").getJSONObject(it).getString("portablePath") }.toSet())
            val alias = field(metadata, "managed_documents", "localUri") as String
            val entry = manifest.getJSONObject("aliases").getString(alias)
            assertArrayEquals(bytes, entries.getValue(entry))
            assertEquals("old metadata", field(metadata, "receipts", "beschreibung"))
            assertFalse(entries.getValue("metadata.json").toString(Charsets.UTF_8).contains(root.absolutePath))
            assertFalse(entries.getValue("manifest.json").toString(Charsets.UTF_8).contains(root.absolutePath))
        }
        noOwnedRunLeft()
        assertArrayEquals(bytes, original.readBytes())
        assertArrayEquals(bytes, retained.readBytes())
    }

    @Test fun activeBusinessWriterPreventsSnapshotBeforeItCanBeFreedForArchiving() {
        var unexpected: OfflineBackupSnapshot? = null
        try {
            OfflineBackupCoordinator.shared.beginWrite().use {
                val error = assertThrows(OfflineBackupValidationException::class.java) {
                    runBlocking { unexpected = OfflineBackupSnapshot.capture(context, database) }
                }
                assertTrue(error.message.orEmpty().contains("gerade geändert"))
            }
        } finally { unexpected?.close() }
        noOwnedRunLeft()
    }

    @Test fun databaseAndOwnedOriginalMutationBetweenMetadataAndCopyAbortInsteadOfMixingVersions() {
        var unexpected: OfflineBackupSnapshot? = null
        var changed = false
        try {
            val error = assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                unexpected = OfflineBackupSnapshot.capture(context, database) { progress ->
                    if (progress.stage == OfflineBackupSnapshot.Stage.METADATA_CAPTURED) {
                        OfflineBackupCoordinator.shared.writing {
                            original.writeText("new original version")
                            database.openHelper.writableDatabase.execSQL("UPDATE receipts SET beschreibung='new metadata' WHERE id=41")
                        }
                        changed = true
                    }
                }
            } }
            assertTrue(changed)
            assertTrue(error.message.orEmpty().contains("während der Erfassung"))
        } finally { unexpected?.close() }
        noOwnedRunLeft()
        assertEquals("new metadata", runBlocking { database.receiptDao().getReceiptById(41)!!.beschreibung })
        assertEquals("new original version", original.readText())
    }

    @Test fun trackedPreferencePublicationBetweenMetadataAndCopyInvalidatesWholeCapture() {
        var unexpected: OfflineBackupSnapshot? = null
        try {
            val error = assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                unexpected = OfflineBackupSnapshot.capture(context, database) { progress ->
                    if (progress.stage == OfflineBackupSnapshot.Stage.METADATA_CAPTURED) {
                        PersistentPreferenceInventory.open(context, "bank_transaction_notes").edit().putString("transaction", "new note").commit()
                    }
                }
            } }
            assertTrue(error.message.orEmpty().contains("während der Erfassung"))
        } finally { unexpected?.close() }
        noOwnedRunLeft()
        assertEquals("new note", context.getSharedPreferences("bank_transaction_notes", 0).getString("transaction", ""))
    }

    @Test fun osCacheUnlinkAfterCopiesIsDetectedEvenWithoutAnyRegisteredBusinessMutation() {
        val before = OfflineBackupCoordinator.shared.beginCapture()
        var deleted = false
        val error = assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
            OfflineBackupSnapshot.capture(context, database) { progress ->
                if (progress.stage == OfflineBackupSnapshot.Stage.ORIGINALS_FIXED) { check(export.delete()); deleted = true }
            }
        } }
        assertTrue(deleted)
        assertTrue(error.message.orEmpty().contains("entfernt oder verändert"))
        OfflineBackupCoordinator.shared.completeCapture(before)
        noOwnedRunLeft()
    }

    @Test fun laterLiveChangesCannotAlterAnyFixedArchiveInput() = runBlocking {
        OfflineBackupSnapshot.capture(context, database).use { snapshot ->
            OfflineBackupCoordinator.shared.writing {
                original.writeText("later original")
                export.writeText("later export")
                database.openHelper.writableDatabase.execSQL("UPDATE receipts SET beschreibung='later metadata' WHERE id=41")
                PersistentPreferenceInventory.open(context, "bank_transaction_notes").edit().putString("transaction", "later note").commit()
            }
            val entries = entries(snapshot)
            val metadata = JSONObject(entries.getValue("metadata.json").toString(Charsets.UTF_8))
            val manifest = JSONObject(entries.getValue("manifest.json").toString(Charsets.UTF_8))
            assertEquals("old metadata", field(metadata, "receipts", "beschreibung"))
            assertEquals("old note", metadata.getJSONObject("preferences").getJSONObject("bankTransactionNotes").getJSONObject("transaction").getString("value"))
            val originalEntry = manifest.getJSONObject("aliases").getString(field(metadata, "managed_documents", "localUri") as String)
            assertArrayEquals(bytes, entries.getValue(originalEntry))
            val files = manifest.getJSONArray("files")
            val exportRecord = (0 until files.length()).map { files.getJSONObject(it) }.single { it.getString("kind") == "SAVED_EXPORT" }
            assertEquals("original export", entries.getValue(exportRecord.getString("entry")).toString(Charsets.UTF_8))
        }
        noOwnedRunLeft()
    }

    @Test fun externalOriginalWithoutPriorContentProofCannotBecomeACompleteSnapshot() = runBlocking {
        val uri = "content://synthetic/original.pdf?token=PRIVATE_SECRET"
        database.managedDocumentDao().upsert(ManagedDocument("document", "house", receiptInternalId = receipt.internalId,
            title = "Original", localUri = uri, originalFilename = "original.pdf"))
        database.receiptDao().insertReceipt(receipt.copy(imageUrl = uri))
        val error = assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineBackupSnapshot.capture(context, database) } }
        assertTrue(error.message.orEmpty().contains("Inhaltsnachweis"))
        assertFalse(error.message.orEmpty().contains("PRIVATE_SECRET"))
        assertFalse(error.message.orEmpty().contains(root.absolutePath))
        noOwnedRunLeft()
        assertArrayEquals(bytes, original.readBytes())
    }

    @Test fun limitsAndCancellationRemoveOnlyOwnNewStagingAndKeepPreviousArchive() {
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineBackupSnapshot.capture(context, database, maximumPayloadBytes = 1) } }
        noOwnedRunLeft()
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineBackupSnapshot.capture(context, database, maximumFiles = 1) } }
        noOwnedRunLeft()
        assertThrows(kotlinx.coroutines.CancellationException::class.java) { runBlocking {
            OfflineBackupSnapshot.capture(context, database) { progress ->
                if (progress.stage == OfflineBackupSnapshot.Stage.METADATA_CAPTURED) throw kotlinx.coroutines.CancellationException("synthetic cancellation")
            }
        } }
        noOwnedRunLeft()
        assertArrayEquals(bytes, original.readBytes())
    }
    private suspend fun withEngineKeys(block: suspend (Context) -> Unit) {
        TestAndroidKeyStore.register()
        val keyContext = object : ContextWrapper(context) {
            override fun getNoBackupFilesDir() = File(root, "key-records").apply { check(isDirectory || mkdirs()) }
        }
        try {
            OfflineBackupKeyManager(keyContext).initialize("synthetic-recovery-password".toCharArray(),
                "synthetic-recovery-password".toCharArray())
            block(keyContext)
        } finally { TestAndroidKeyStore.remove() }
    }
    private suspend fun decryptedEntries(prepared: OfflineBackupArchiveEngine.Prepared): Map<String, ByteArray> {
        val plain = File(root, "plain-${UUID.randomUUID()}.zip")
        try {
            prepared.file.inputStream().use { OfflineBackupArchiveCrypto.decryptToPrivateStaging(it,
                "synthetic-recovery-password".toCharArray(), plain) }
            return ZipInputStream(plain.inputStream()).use { zip -> buildMap {
                while (true) { val entry = zip.nextEntry ?: break; put(entry.name, zip.readBytes()) }
            } }
        } finally { plain.delete() }
    }
    private fun noPreparedArchiveLeft() {
        noOwnedRunLeft()
        assertTrue(File(context.filesDir, "offline-backup-staging").listFiles()!!.none { it.name.startsWith("encrypted-") })
    }

    @Test fun enginePreparesCompleteEncryptedSnapshotWithCredentialsExcludedByDefault() = runBlocking<Unit> {
        withEngineKeys { keyContext ->
            OfflineBackupArchiveEngine(keyContext, database).prepare().use { prepared ->
                assertTrue(prepared.file.isFile)
                assertEquals(1L, prepared.summary.rowCounts["receipts"])
                original.writeText("later live content")
                val fixed = decryptedEntries(prepared)
                val manifest = JSONObject(fixed.getValue("manifest.json").toString(Charsets.UTF_8))
                assertTrue(manifest.isNull("credentials")); assertFalse("credentials.bin" in fixed)
                val metadata = JSONObject(fixed.getValue("metadata.json").toString(Charsets.UTF_8))
                assertEquals("old metadata", field(metadata, "receipts", "beschreibung"))
                val alias = field(metadata, "managed_documents", "localUri") as String
                assertArrayEquals(bytes, fixed.getValue(manifest.getJSONObject("aliases").getString(alias)))
            }
        }
        noPreparedArchiveLeft()
    }

    @Test fun enginePinsOnlyEncryptedCredentialsAndBindsTheirSizeAndHashInManifest() = runBlocking<Unit> {
        withEngineKeys { keyContext ->
            val synthetic = "test-only-synthetic-gemini-key-12345"
            AiProviderSettings.storeGeminiKey(keyContext, synthetic.toCharArray())
            val prefs = context.getSharedPreferences("ai_provider_settings", 0)
            val before = prefs.all.toMap()
            OfflineBackupArchiveEngine(keyContext, database).prepare(OfflineBackupCredentialSection.Consent(manual = true)).use { prepared ->
                val fixed = decryptedEntries(prepared)
                val manifest = JSONObject(fixed.getValue("manifest.json").toString(Charsets.UTF_8))
                val credential = manifest.getJSONObject("credentials")
                val sealed = fixed.getValue(credential.getString("entry"))
                assertEquals(sealed.size.toLong(), credential.getLong("size"))
                val sha = MessageDigest.getInstance("SHA-256").digest(sealed).joinToString("") {
                    (it.toInt() and 255).toString(16).padStart(2, '0') }
                assertEquals(sha, credential.getString("sha256"))
                val needles = listOf(synthetic.toByteArray().toList(), synthetic.toByteArray(Charsets.UTF_16BE).toList())
                fixed.values.forEach { content -> needles.forEach { needle ->
                    assertFalse(content.toList().windowed(needle.size).any { it == needle })
                } }
                val header = prepared.file.inputStream().use { OfflineBackupArchiveHeader.read(it) }
                OfflineBackupKeyEnvelope.recover(header.recovery, "synthetic-recovery-password".toCharArray()).use { recovered ->
                    OfflineBackupCredentialSection.open(recovered, sealed).use { opened ->
                        assertArrayEquals(synthetic.toCharArray(), opened.values[OfflineBackupCredentialSection.Provider.GEMINI])
                    }
                }
            }
            assertEquals(before, prefs.all)
        }
        noPreparedArchiveLeft()
    }

    @Test fun engineRejectsCredentialMutationDuringCaptureAndKeepsPriorArchive() = runBlocking<Unit> {
        withEngineKeys { keyContext ->
            AiProviderSettings.storeGeminiKey(keyContext, "test-only-synthetic-gemini-key-before".toCharArray())
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineBackupArchiveEngine(keyContext, database).prepare(OfflineBackupCredentialSection.Consent(manual = true)) { progress ->
                    if (progress.stage == OfflineBackupSnapshot.Stage.METADATA_CAPTURED)
                        AiProviderSettings.storeGeminiKey(keyContext, "test-only-synthetic-gemini-key-after".toCharArray())
                }
            } }
        }
        noPreparedArchiveLeft()
    }

    @Test fun engineCredentialFailureLeavesSettingsAndPriorArchiveUntouched() = runBlocking<Unit> {
        withEngineKeys { keyContext ->
            val prefs = context.getSharedPreferences("ai_provider_settings", 0)
            prefs.edit().putString("gemini_key_ciphertext", "damaged").commit()
            val before = prefs.all.toMap()
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineBackupArchiveEngine(keyContext, database).prepare(OfflineBackupCredentialSection.Consent(manual = true))
            } }
            assertEquals(before, prefs.all)
        }
        noPreparedArchiveLeft()
    }

}
