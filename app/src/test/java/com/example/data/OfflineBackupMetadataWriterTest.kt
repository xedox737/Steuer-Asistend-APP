package com.example.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
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
class OfflineBackupMetadataWriterTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var database: AppDatabase
    private lateinit var original: File
    private val namespace = "metadata-${UUID.randomUUID()}"
    private val originalBytes = "%PDF-1.4\nSynthetisches Original\n%%EOF".toByteArray()
    private val property = PropertyMetadata(id = 3, propertyId = "house", name = "Haus Äöü 🏠", gesamtKaufpreis = 125_000.75)
    private val receipt = Receipt(id = 41, internalId = "stable-receipt", propertyId = "house",
        aussteller = "Synthetischer Handwerker", datum = "2026-11-10", uhrzeit = "10:30", bruttobetrag = 125.73,
        hauptkategorie = "Kosten", unterkategorie = "Reparatur", kontoNr = "4800", beschreibung = "Unverändert; \"Zitat\"\nzweite Zeile")
    private fun document() = ManagedDocument(documentId = "stable-document", propertyId = "house",
        receiptInternalId = receipt.internalId, title = "Original", originalFilename = "original.pdf", storedFilename = "original.pdf",
        mimeType = "application/pdf", localUri = original.absolutePath, sha256 = StableDocumentIdentity.sha256(originalBytes),
        fileSizeBytes = originalBytes.size.toLong(), ocrText = "OCR bleibt äöü 🏠", extractedFieldsJson = "{\"manuell\":true}")

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
        original = File(context.filesDir, "managed_documents/original.pdf").apply { parentFile!!.mkdirs(); writeBytes(originalBytes) }
        database.propertyDao().insertPropertyMetadata(property)
        database.receiptDao().insertReceipt(receipt)
        database.receiptDao().insertReceipt(receipt.copy(id = 100, internalId = "deleted-high-id"))
        database.openHelper.writableDatabase.execSQL("DELETE FROM receipts WHERE id=100")
        database.managedDocumentDao().upsert(document())
        database.managedDocumentDao().upsertMigrationJournal(DocumentMigrationJournal("stable-document", driveFileId = "remote",
            targetFolderId = "technical-target", targetFilename = "must-not-replay.pdf"))
        database.exportAuditDao().insertRun(ExportAuditRun("audit", timestamp = 9_007_199_254_740_993L,
            exportierteReceiptIdsJson = "[41]", zipFileName = "retained.zip", totalAmount = 125.73))
        context.getSharedPreferences("rent_plan_prefs", 0).edit().putFloat("nk_token", 80.25f).commit()
        context.getSharedPreferences("ai_provider_settings", 0).edit().putString("openai_model", "synthetic-model")
            .putString("api_key", "SYNTHETIC_API_SECRET_MUST_NOT_EXPORT").commit()
        context.getSharedPreferences("google_drive_prefs", 0).edit().putString("selected_property_id", "house")
            .putString("oauth_token", "SYNTHETIC_OAUTH_SECRET_MUST_NOT_EXPORT").commit()
    }

    @After fun tearDown() {
        database.close()
        PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        root.deleteRecursively()
    }
    private fun output(name: String = "metadata.json") = File(root, name)
    private fun rows(root: JSONObject, name: String): List<JSONObject> {
        val table = root.getJSONObject("tables").getJSONObject(name)
        val columns = table.getJSONArray("columns")
        return (0 until table.getJSONArray("rows").length()).map { index ->
            val row = table.getJSONArray("rows").getJSONArray(index)
            JSONObject().also { result -> (0 until columns.length()).forEach { c -> result.put(columns.getJSONObject(c).getString("name"), row.get(c)) } }
        }
    }

    @Test fun preservesBusinessValuesPreferencesAndHighwaterInOneReadonlyDraft() = runBlocking {
        val before = OfflineBackupCoordinator.shared.beginCapture()
        val draft = OfflineBackupMetadataWriter(context, database).write(output())
        assertEquals(1L, draft.rowCounts["receipts"])
        assertEquals(18, draft.rowCounts.size)
        val json = JSONObject(draft.file.readText())
        assertEquals(2, json.getInt("version"))
        assertEquals(34, json.getInt("databaseVersion"))
        assertEquals(receipt.beschreibung, rows(json, "receipts").single().getString("beschreibung"))
        assertEquals(receipt.bruttobetrag, rows(json, "receipts").single().getDouble("bruttobetrag"), 0.0)
        assertEquals(property.name, rows(json, "property_metadata").single().getString("name"))
        assertEquals(document().ocrText, rows(json, "managed_documents").single().getString("ocrText"))
        assertEquals(document().extractedFieldsJson, rows(json, "managed_documents").single().getString("extractedFieldsJson"))
        assertEquals(9_007_199_254_740_993L, rows(json, "export_audit_runs").single().getLong("timestamp"))
        assertFalse(json.getJSONObject("tables").has("document_migration_journal"))
        assertFalse(json.getJSONObject("tables").has("document_search_fts"))
        assertEquals(100L, json.getJSONObject("sequences").getLong("receipts"))
        val prefs = json.getJSONObject("preferences")
        assertEquals("synthetic-model", prefs.getJSONObject("aiSelectionPrefs").getJSONObject("openai_model").getString("value"))
        assertEquals(80.25, prefs.getJSONObject("rentPlanPrefs").getJSONObject("nk_token").getDouble("value"), 0.0)
        assertFalse(draft.file.readText().contains("SYNTHETIC_API_SECRET"))
        assertFalse(draft.file.readText().contains("SYNTHETIC_OAUTH_SECRET"))
        assertEquals(draft.file.length(), draft.bytes)
        assertEquals(com.example.util.ReceiptManifestService.calculateSha256(draft.file), draft.sha256)
        OfflineBackupCoordinator.shared.completeCapture(before)
        assertEquals(listOf(receipt), database.receiptDao().getAllReceiptsIncludingDeletedList())
        assertEquals(document(), database.managedDocumentDao().getById("stable-document"))
        assertEquals("PLANNED", database.managedDocumentDao().getMigrationJournal().single().state)
        assertArrayEquals(originalBytes, original.readBytes())
    }

    @Test fun portableAliasesKeepMultipageOrderHashesAndPrivateUriCandidatesOutOfEmittedMetadata() = runBlocking {
        val content = "content://synthetic/original.pdf?token=PRIVATE_QUERY#PRIVATE_FRAGMENT"
        database.receiptDao().insertReceipt(receipt.copy(imageUrl = JSONArray(listOf(original.absolutePath, content)).toString()))
        database.managedDocumentDao().upsert(document().copy(localUri = content))
        val draft = OfflineBackupMetadataWriter(context, database).write(output())
        assertEquals(2, draft.references.size)
        val refs = draft.references.associateBy { it.path }
        val json = JSONObject(draft.file.readText())
        val order = JSONArray(rows(json, "receipts").single().getString("imageUrl"))
        assertEquals(refs.getValue(original.absolutePath).key, order.getString(0))
        assertEquals(refs.getValue(content).key, order.getString(1))
        assertEquals(refs.getValue(content).key, rows(json, "managed_documents").single().getString("localUri"))
        assertEquals(document().sha256, refs.getValue(content).expectedSha256)
        assertEquals(document().fileSizeBytes, refs.getValue(content).expectedSize)
        assertTrue(original.absolutePath in refs.getValue(content).verifiedCandidates)
        val emitted = draft.file.readText()
        assertFalse(emitted.contains(root.absolutePath))
        assertFalse(emitted.contains("PRIVATE_QUERY"))
        assertFalse(emitted.contains("PRIVATE_FRAGMENT"))
        assertEquals(content, database.managedDocumentDao().getById("stable-document")!!.localUri)
    }

    @Test fun legacyAttachmentRequiresMatchingDocumentAndReceiptIdentity() = runBlocking {
        val attachment = ReceiptDocumentReference("stable-document", "other-receipt", "private-drive-id",
            null, "annex.pdf", "application/pdf", originalBytes.size.toLong(), "OTHER", "")
        database.receiptDocumentDao().insertDocument(attachment)
        val error = assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
            OfflineBackupMetadataWriter(context, database).write(output())
        } }
        assertTrue(error.message.orEmpty().contains("annex.pdf"))
        assertTrue(error.message.orEmpty().contains("other-receipt"))
        assertFalse(error.message.orEmpty().contains("private-drive-id"))
        database.receiptDocumentDao().insertDocument(attachment.copy(receiptInternalId = receipt.internalId))
        val draft = OfflineBackupMetadataWriter(context, database).write(output())
        assertEquals(document().sha256, draft.references.single().expectedSha256)
    }

    @Test fun metadataOnlyDocumentIsPreservedButClaimedMissingOriginalAborts() = runBlocking {
        database.managedDocumentDao().upsert(document().copy(localUri = "", sha256 = "", fileSizeBytes = 0))
        val draft = OfflineBackupMetadataWriter(context, database).write(output())
        assertEquals(1L, draft.rowCounts["managed_documents"])
        assertTrue(draft.references.isEmpty())
        assertEquals("", rows(JSONObject(draft.file.readText()), "managed_documents").single().getString("localUri"))
        database.receiptDao().insertReceipt(receipt.copy(driveFileId = "cloud-only", originalMimeType = "application/pdf"))
        val failed = output("missing.json")
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineBackupMetadataWriter(context, database).write(failed) } }
        assertFalse(failed.exists())
        assertTrue(original.isFile)
    }

    @Test fun unknownTablesAndMetadataOverflowFailWithoutChangingSourceOrKeepingPartialOutput() = runBlocking {
        database.openHelper.writableDatabase.execSQL("CREATE TABLE unclassified_source(id INTEGER PRIMARY KEY, value TEXT)")
        val unknown = output("unknown.json")
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineBackupMetadataWriter(context, database).write(unknown) } }
        assertFalse(unknown.exists())
        database.openHelper.writableDatabase.execSQL("DROP TABLE unclassified_source")
        val overflow = output("overflow.json")
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineBackupMetadataWriter(context, database, maximumBytes = 128).write(overflow) } }
        assertFalse(overflow.exists())
        assertThrows(IllegalArgumentException::class.java) { OfflineBackupMetadataWriter(context, database, maximumBytes = 64L * 1024 * 1024 + 1) }
        assertEquals(receipt, database.receiptDao().getReceiptById(41))
        assertArrayEquals(originalBytes, original.readBytes())
    }

    @Test fun existingOutputIsNeverOverwrittenAndCancelledStartCreatesNoDraft() {
        val existing = output().apply { writeText("keep existing bytes") }
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineBackupMetadataWriter(context, database).write(existing) } }
        assertEquals("keep existing bytes", existing.readText())
        val cancelled = output("cancelled.json")
        val job = Job().apply { cancel() }
        assertThrows(kotlinx.coroutines.CancellationException::class.java) { runBlocking(job) { OfflineBackupMetadataWriter(context, database).write(cancelled) } }
        assertFalse(cancelled.exists())
        assertArrayEquals(originalBytes, original.readBytes())
    }
}
