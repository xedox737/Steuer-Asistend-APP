package com.example.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** MW-04: transport is offline, original bytes and stable relationships survive a portable backup. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineBackupServiceTest {
    private lateinit var source: TestContext
    private lateinit var target: TestContext
    private lateinit var sourceDatabase: AppDatabase
    private lateinit var targetDatabase: AppDatabase
    private lateinit var sourceService: OfflineBackupService
    private lateinit var targetService: OfflineBackupService
    private val originalBytes = "%PDF-1.4\nSynthetic offline original\n%%EOF".toByteArray()

    @Before fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        source = TestContext(context, "offline-source-${UUID.randomUUID()}")
        target = TestContext(context, "offline-target-${UUID.randomUUID()}")
        sourceDatabase = Room.inMemoryDatabaseBuilder(source, AppDatabase::class.java).build()
        targetDatabase = Room.inMemoryDatabaseBuilder(target, AppDatabase::class.java).build()
        sourceService = OfflineBackupService(source, sourceDatabase)
        targetService = OfflineBackupService(target, targetDatabase)
    }

    @After fun tearDown() {
        sourceDatabase.close()
        targetDatabase.close()
        source.cleanup()
        target.cleanup()
    }

    private val property get() = PropertyMetadata(
        id = 3, propertyId = "offline-property", name = "MFH Lindenstraße",
        wohneinheiten = UnitIdentity.encode(listOf(UnitIdentity.Entry("offline-unit", "EG links")))
    )
    private val receipt get() = Receipt(
        id = 41, aussteller = "Synthetic vendor", datum = "2026-11-06", uhrzeit = "10:30",
        bruttobetrag = 125.0, hauptkategorie = "Kosten", unterkategorie = "Reparatur", kontoNr = "4800",
        beschreibung = "Offline invoice", internalId = "offline-receipt", propertyId = "offline-property",
        unitId = "offline-unit", wohneinheit = "EG links"
    )
    private val transaction get() = BankTransaction(
        transactionId = "offline-transaction", accountId = "offline-account", bookingDate = "2026-11-06",
        amount = -125.0, purpose = "Invoice 41", propertyId = "offline-property", unitId = "offline-unit",
        reconciliationStatus = BankReconciliationStatus.MATCHED
    )
    private val link get() = BankReceiptLink(
        linkId = "offline-link", transactionId = "offline-transaction", receiptId = 41,
        receiptInternalId = "offline-receipt", allocatedAmount = 125.0
    )

    private suspend fun seedPortfolio(missingOriginal: Boolean = false) {
        val original = File(source.filesDir, "managed_documents/offline-document.pdf")
        if (!missingOriginal) {
            original.parentFile!!.mkdirs()
            original.writeBytes(originalBytes)
        }
        sourceDatabase.propertyDao().insertPropertyMetadata(property)
        sourceDatabase.receiptDao().insertReceipt(receipt)
        sourceDatabase.bankDao().upsertAccount(BankAccount("offline-account", "Synthetic account"))
        sourceDatabase.bankDao().insertTransactions(listOf(transaction))
        sourceDatabase.bankDao().upsertLink(link)
        sourceDatabase.managedDocumentDao().upsert(ManagedDocument(
            documentId = "offline-document", propertyId = property.propertyId, unitId = "offline-unit",
            receiptInternalId = receipt.internalId, title = "Synthetic invoice", documentDate = "2026-11-06",
            originalFilename = "invoice.pdf", storedFilename = "invoice.pdf", mimeType = "application/pdf",
            localUri = original.absolutePath, sha256 = StableDocumentIdentity.sha256(originalBytes),
            fileSizeBytes = originalBytes.size.toLong()
        ))
        source.getSharedPreferences("rent_plan_prefs", 0).edit().putFloat("v2_offline-property_offline-unit_nk", 80f).commit()
        source.getSharedPreferences("bank_transaction_notes", 0).edit().putString("offline-transaction", "Checked locally").commit()
        source.getSharedPreferences("google_drive_prefs", 0).edit()
            .putString("selected_property_id", property.propertyId)
            .putString("oauth_token", "SECRET_GOOGLE_TOKEN_MUST_NOT_LEAVE_DEVICE").commit()
        source.getSharedPreferences("ai_provider_settings", 0).edit()
            .putString("openai_model", "synthetic-model")
            .putString("api_key", "SECRET_AI_KEY_MUST_NOT_LEAVE_DEVICE").commit()
    }

    private suspend fun backup(): ByteArray = ByteArrayOutputStream().also { sourceService.create(it) }.toByteArray()

    @Test fun portableBackupPreservesOriginalsIdsRelationshipsPreferencesAndIsIdempotent() = runBlocking {
        seedPortfolio()
        val bytes = backup()
        val snapshot = zipEntries(bytes).getValue("snapshot.json").toString(Charsets.UTF_8)
        assertFalse(snapshot.contains("SECRET_GOOGLE_TOKEN_MUST_NOT_LEAVE_DEVICE"))
        assertFalse(snapshot.contains("SECRET_AI_KEY_MUST_NOT_LEAVE_DEVICE"))
        targetService.inspect(ByteArrayInputStream(bytes)).use { prepared ->
            assertEquals(1, prepared.receiptCount)
            assertEquals(1, prepared.propertyCount)
            assertEquals(1, prepared.fileCount)
            targetService.restore(prepared)
        }
        val restored = targetDatabase.managedDocumentDao().getById("offline-document")!!
        assertEquals(property, targetDatabase.propertyDao().getPropertyByPropertyId(property.propertyId))
        assertEquals(receipt, targetDatabase.receiptDao().getReceiptById(receipt.id))
        assertEquals(transaction, targetDatabase.bankDao().getTransaction(transaction.transactionId))
        assertEquals(listOf(link), targetDatabase.bankDao().getLinksForTransaction(transaction.transactionId))
        assertEquals(receipt.internalId, restored.receiptInternalId)
        assertEquals("offline-unit", restored.unitId)
        assertTrue("Restored originals must live on the target device", File(restored.localUri).canonicalPath.startsWith(target.filesDir.canonicalPath + File.separator))
        assertArrayEquals(originalBytes, File(restored.localUri).readBytes())
        assertEquals(StableDocumentIdentity.sha256(originalBytes), restored.sha256)
        assertEquals(80.0, target.getSharedPreferences("rent_plan_prefs", 0).getFloat("v2_offline-property_offline-unit_nk", -1f).toDouble(), 0.0)
        assertEquals("Checked locally", target.getSharedPreferences("bank_transaction_notes", 0).getString("offline-transaction", null))
        assertEquals("synthetic-model", target.getSharedPreferences("ai_provider_settings", 0).getString("openai_model", null))
        assertFalse(target.getSharedPreferences("google_drive_prefs", 0).contains("oauth_token"))
        assertFalse(target.getSharedPreferences("ai_provider_settings", 0).contains("api_key"))
        targetService.inspect(ByteArrayInputStream(bytes)).use { targetService.restore(it) }
        assertEquals(1, targetDatabase.receiptDao().getAllReceiptsList().size)
        assertEquals(1, targetDatabase.managedDocumentDao().getAll().size)
        assertEquals(1, targetDatabase.bankDao().getAllTransactions().size)
        assertEquals(1, targetDatabase.bankDao().getAllLinks().size)
        assertEquals(restored, targetDatabase.managedDocumentDao().getById("offline-document"))
        assertArrayEquals(originalBytes, File(restored.localUri).readBytes())
    }

    @Test fun corruptedOriginalIsRejectedDuringInspectionWithoutWritingUserData() = runBlocking {
        seedPortfolio()
        val entries = zipEntries(backup()).toMutableMap()
        val originalEntry = entries.keys.single { it.startsWith("files/") }
        entries[originalEntry] = "Tampered original".toByteArray()
        val result = runCatching { targetService.inspect(ByteArrayInputStream(zip(entries))).close() }
        assertTrue("Original checksum mismatch must reject the package", result.isFailure)
        assertTrue(targetDatabase.receiptDao().getAllReceiptsList().isEmpty())
        assertTrue(targetDatabase.propertyDao().getAllProperties().isEmpty())
        assertTrue(targetDatabase.managedDocumentDao().getAll().isEmpty())
        assertFalse(target.getSharedPreferences("rent_plan_prefs", 0).contains("v2_offline-property_offline-unit_nk"))
    }

    @Test fun conflictingLocalRowRejectsTheWholeRestoreWithoutPartialInsertsOrPreferenceChanges() = runBlocking {
        seedPortfolio()
        val local = receipt.copy(beschreibung = "Newer local correction", bruttobetrag = 99.0)
        targetDatabase.receiptDao().insertReceipt(local)
        target.getSharedPreferences("bank_transaction_notes", 0).edit().putString("offline-transaction", "Keep my note").commit()
        targetService.inspect(ByteArrayInputStream(backup())).use { prepared ->
            assertTrue(runCatching { targetService.restore(prepared) }.isFailure)
        }
        assertEquals(listOf(local), targetDatabase.receiptDao().getAllReceiptsList())
        assertTrue(targetDatabase.propertyDao().getAllProperties().isEmpty())
        assertTrue(targetDatabase.bankDao().getAllTransactions().isEmpty())
        assertTrue(targetDatabase.bankDao().getAllLinks().isEmpty())
        assertTrue(targetDatabase.managedDocumentDao().getAll().isEmpty())
        assertEquals("Keep my note", target.getSharedPreferences("bank_transaction_notes", 0).getString("offline-transaction", null))
        assertFalse(target.getSharedPreferences("rent_plan_prefs", 0).contains("v2_offline-property_offline-unit_nk"))
    }

    @Test fun missingSourceOriginalCannotProduceASeeminglyCompleteBackup() = runBlocking {
        seedPortfolio(missingOriginal = true)
        assertTrue(runCatching { backup() }.isFailure)
        assertEquals(receipt, sourceDatabase.receiptDao().getReceiptById(receipt.id))
        assertEquals(listOf(link), sourceDatabase.bankDao().getAllLinks())
        assertEquals(1, sourceDatabase.managedDocumentDao().getAll().size)
    }

    @Test fun changedSourceOriginalMustMatchThePreviouslyRecordedDocumentChecksum() = runBlocking {
        seedPortfolio()
        val before = sourceDatabase.managedDocumentDao().getById("offline-document")!!
        val changed = "%PDF-1.4\nCorrupted local original\n%%EOF".toByteArray()
        File(before.localUri).writeBytes(changed)
        assertTrue("A backup must not certify corrupted original bytes against an older baseline", runCatching { backup() }.isFailure)
        assertArrayEquals(changed, File(before.localUri).readBytes())
        assertEquals(before, sourceDatabase.managedDocumentDao().getById("offline-document"))
    }

    @Test fun linkedDocumentWithoutAnyLocalOriginalCannotClaimACompleteBackup() = runBlocking {
        seedPortfolio()
        val document = sourceDatabase.managedDocumentDao().getById("offline-document")!!
        sourceDatabase.managedDocumentDao().upsert(document.copy(localUri = ""))
        // A stable receipt link is not proof of a local original: this receipt's imageUrl is blank.
        assertTrue(runCatching { backup() }.isFailure)
        assertEquals(receipt, sourceDatabase.receiptDao().getReceiptById(receipt.id))
        assertEquals("", sourceDatabase.managedDocumentDao().getById(document.documentId)!!.localUri)
    }

    @Test fun multipleOriginalsIncludingACommaFilenameRestoreAllReceiptAndAccountingMirrors() = runBlocking {
        val first = File(source.filesDir, "receipt-originals/page,one.pdf")
        val second = File(source.filesDir, "receipt-originals/page-two.pdf")
        first.parentFile!!.mkdirs()
        val firstBytes = "%PDF-1.4\nSynthetic first original\n%%EOF".toByteArray()
        val secondBytes = "%PDF-1.4\nSynthetic second original\n%%EOF".toByteArray()
        first.writeBytes(firstBytes)
        second.writeBytes(secondBytes)
        val withImage = property.copy(bildPfad = first.absolutePath)
        sourceDatabase.propertyDao().insertPropertyMetadata(withImage)
        val repository = ReceiptRepository(sourceDatabase.receiptDao(), sourceDatabase.propertyDao(),
            sourceDatabase.receiptEntityDao(), sourceDatabase.belegDao(), sourceDatabase.exportAuditDao(),
            sourceDatabase.receiptDocumentDao(), sourceDatabase.managedDocumentDao())
        repository.insert(receipt.copy(imageUrl = ReceiptOriginalChain.encodePaths(listOf(first.absolutePath, second.absolutePath)),
            originalMimeType = "application/pdf"))
        val beforeReceipt = sourceDatabase.receiptDao().getReceiptById(receipt.id)!!
        val beforeEntity = sourceDatabase.receiptEntityDao().getEntityById(receipt.id)!!
        val beforeBeleg = sourceDatabase.belegDao().getBelegById(receipt.id.toLong())!!
        val beforeDocuments = sourceDatabase.managedDocumentDao().getAll().associateBy { it.documentId }
        assertEquals(2, beforeDocuments.size)
        assertEquals(beforeReceipt.imageUrl, beforeEntity.pdfPath)
        assertEquals(beforeReceipt.imageUrl, beforeBeleg.bildPfad)
        targetService.inspect(ByteArrayInputStream(backup())).use { prepared ->
            assertEquals(2, prepared.fileCount)
            targetService.restore(prepared)
        }
        val restored = targetDatabase.receiptDao().getReceiptById(receipt.id)!!
        val restoredPaths = ReceiptOriginalChain.paths(restored.imageUrl)
        assertEquals(2, restoredPaths.size)
        assertArrayEquals(firstBytes, File(restoredPaths[0]).readBytes())
        assertArrayEquals(secondBytes, File(restoredPaths[1]).readBytes())
        restoredPaths.forEach { path -> assertTrue(File(path).canonicalPath.startsWith(target.filesDir.canonicalPath + File.separator)) }
        assertEquals(beforeReceipt, restored.copy(imageUrl = beforeReceipt.imageUrl))
        val entity = targetDatabase.receiptEntityDao().getEntityById(receipt.id)!!
        val beleg = targetDatabase.belegDao().getBelegById(receipt.id.toLong())!!
        assertEquals(restored.imageUrl, entity.pdfPath)
        assertEquals(restored.imageUrl, beleg.bildPfad)
        assertEquals(beforeEntity, entity.copy(pdfPath = beforeEntity.pdfPath))
        assertEquals(beforeBeleg, beleg.copy(bildPfad = beforeBeleg.bildPfad))
        assertEquals(restoredPaths[0], targetDatabase.propertyDao().getPropertyByPropertyId(property.propertyId)!!.bildPfad)
        val documents = targetDatabase.managedDocumentDao().getAll().associateBy { it.documentId }
        assertEquals(beforeDocuments.keys, documents.keys)
        beforeDocuments.forEach { (id, original) ->
            val document = documents.getValue(id)
            assertEquals(original, document.copy(localUri = original.localUri))
            assertTrue(document.localUri in restoredPaths)
            assertEquals(document.sha256, StableDocumentIdentity.sha256(File(document.localUri).readBytes()))
        }
    }

    @Test fun cloudOnlyReceiptCannotBeReportedAsAPortableCompleteBackup() = runBlocking {
        sourceDatabase.propertyDao().insertPropertyMetadata(property)
        val cloudOnly = receipt.copy(imageUrl = "", driveFileId = "cloud-only-original", originalMimeType = "application/pdf")
        sourceDatabase.receiptDao().insertReceipt(cloudOnly)
        assertTrue(runCatching { backup() }.isFailure)
        assertEquals(cloudOnly, sourceDatabase.receiptDao().getReceiptById(receipt.id))
        assertTrue(sourceDatabase.managedDocumentDao().getAll().isEmpty())
    }

    @Test fun anAdditionalCloudOnlyAttachmentCannotBeSilentlyOmittedFromTheBackup() = runBlocking {
        seedPortfolio()
        val attachment = ReceiptDocumentReference(
            id = "offline-extra-attachment", receiptInternalId = receipt.internalId,
            driveFileId = "cloud-only-additional-original", driveFolderId = "cloud-folder",
            filename = "additional-proof.pdf", mimeType = "application/pdf", sizeBytes = 123L,
            role = "PAYMENT_PROOF", createdAt = "2026-11-06T10:00:00Z"
        )
        sourceDatabase.receiptDocumentDao().insertDocument(attachment)
        assertTrue(runCatching { backup() }.isFailure)
        assertEquals(listOf(attachment), sourceDatabase.receiptDocumentDao().getDocumentsForReceipt(receipt.internalId))
        assertEquals(receipt, sourceDatabase.receiptDao().getReceiptById(receipt.id))
        assertArrayEquals(originalBytes, File(sourceDatabase.managedDocumentDao().getById("offline-document")!!.localUri).readBytes())
    }

    @Test fun changedSnapshotWithoutMatchingChecksumIsRejectedBeforeAnyRestore() = runBlocking {
        seedPortfolio()
        val entries = zipEntries(backup()).toMutableMap()
        entries["snapshot.json"] = JSONObject(entries.getValue("snapshot.json").toString(Charsets.UTF_8))
            .put("databaseVersion", -1).toString().toByteArray()
        assertInspectionRejectedWithoutUserWrites(zip(entries))
    }

    @Test fun zipTraversalEntryIsRejectedWithoutEscapingTheStagingDirectory() = runBlocking {
        seedPortfolio()
        val entries = zipEntries(backup()).toMutableMap()
        entries["../offline-escaped-original.pdf"] = originalBytes
        assertInspectionRejectedWithoutUserWrites(zip(entries))
        assertFalse(File(target.cacheDir, "offline-escaped-original.pdf").exists())
    }

    @Test fun duplicateZipEntryIsRejectedInsteadOfOverwritingTheFirstEntry() = runBlocking {
        seedPortfolio()
        val entries = zipEntries(backup()).toMutableMap()
        entries["files/1"] = originalBytes
        // ZipOutputStream refuses duplicate names; replace an equal-length header name in a
        // valid archive so the reader receives two files/0 records with otherwise valid CRCs.
        val duplicateArchive = replaceAscii(zip(entries), "files/1", "files/0")
        assertInspectionRejectedWithoutUserWrites(duplicateArchive)
    }

    @Test fun differentDatabaseSchemaVersionRequiresAnExplicitMigrationInsteadOfImport() = runBlocking {
        seedPortfolio()
        val changed = changeSnapshot(backup()) { root ->
            root.put("databaseVersion", root.getInt("databaseVersion") + 1)
        }
        assertInspectionRejectedWithoutUserWrites(changed)
    }

    @Test fun textInAnIntegerCellIsRejectedBeforeSqlCanCoerceIt() = runBlocking {
        seedPortfolio()
        val changed = changeSnapshot(backup()) { root ->
            val table = root.getJSONObject("tables").getJSONObject("receipts")
            val columns = table.getJSONArray("columns")
            val idColumn = (0 until columns.length()).single { columns.getString(it) == "id" }
            table.getJSONArray("rows").getJSONArray(0).put(idColumn, "forty-one")
        }
        assertInspectionRejectedWithoutUserWrites(changed)
    }

    @Test fun nullPrimaryKeyCannotSilentlyGenerateANewIdAndBreakReceiptLinks() = runBlocking {
        seedPortfolio()
        val changed = changeSnapshot(backup()) { root ->
            val table = root.getJSONObject("tables").getJSONObject("receipts")
            val columns = table.getJSONArray("columns")
            val idColumn = (0 until columns.length()).single { columns.getString(it) == "id" }
            table.getJSONArray("rows").getJSONArray(0).put(idColumn, JSONObject.NULL)
        }
        assertInspectionRejectedWithoutUserWrites(changed)
    }

    @Test fun restoreNeverOverwritesAnExistingDifferentOriginalFile() = runBlocking {
        seedPortfolio()
        val bytes = backup()
        targetService.inspect(ByteArrayInputStream(bytes)).use { targetService.restore(it) }
        val documentBefore = targetDatabase.managedDocumentDao().getById("offline-document")!!
        val localOriginal = File(documentBefore.localUri)
        val changedBytes = "%PDF-1.4\nNewer local original\n%%EOF".toByteArray()
        localOriginal.writeBytes(changedBytes)
        val receiptsBefore = targetDatabase.receiptDao().getAllReceiptsList()
        targetService.inspect(ByteArrayInputStream(bytes)).use { prepared ->
            assertTrue(runCatching { targetService.restore(prepared) }.isFailure)
        }
        assertArrayEquals(changedBytes, localOriginal.readBytes())
        assertEquals(documentBefore, targetDatabase.managedDocumentDao().getById("offline-document"))
        assertEquals(receiptsBefore, targetDatabase.receiptDao().getAllReceiptsList())
        assertEquals(listOf(link), targetDatabase.bankDao().getAllLinks())
    }

    private suspend fun assertInspectionRejectedWithoutUserWrites(bytes: ByteArray) {
        assertTrue(runCatching { targetService.inspect(ByteArrayInputStream(bytes)).close() }.isFailure)
        assertTrue(targetDatabase.receiptDao().getAllReceiptsList().isEmpty())
        assertTrue(targetDatabase.propertyDao().getAllProperties().isEmpty())
        assertTrue(targetDatabase.bankDao().getAllTransactions().isEmpty())
        assertTrue(targetDatabase.bankDao().getAllLinks().isEmpty())
        assertTrue(targetDatabase.managedDocumentDao().getAll().isEmpty())
        assertFalse(target.getSharedPreferences("rent_plan_prefs", 0).contains("v2_offline-property_offline-unit_nk"))
    }

    private fun changeSnapshot(bytes: ByteArray, change: (JSONObject) -> Unit): ByteArray {
        val entries = zipEntries(bytes).toMutableMap()
        val root = JSONObject(entries.getValue("snapshot.json").toString(Charsets.UTF_8))
        change(root)
        val snapshot = root.toString().toByteArray(Charsets.UTF_8)
        entries["snapshot.json"] = snapshot
        entries["snapshot.sha256"] = StableDocumentIdentity.sha256(snapshot).toByteArray()
        return zip(entries)
    }

    private fun replaceAscii(bytes: ByteArray, from: String, to: String): ByteArray {
        require(from.length == to.length)
        val source = from.toByteArray(Charsets.US_ASCII)
        val target = to.toByteArray(Charsets.US_ASCII)
        return bytes.copyOf().also { result ->
            for (offset in 0..(result.size - source.size)) {
                if (source.indices.all { result[offset + it] == source[it] }) {
                    target.indices.forEach { result[offset + it] = target[it] }
                }
            }
        }
    }

    private fun zipEntries(bytes: ByteArray): Map<String, ByteArray> = linkedMapOf<String, ByteArray>().apply {
        ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                if (!entry.isDirectory) put(entry.name, input.readBytes())
                input.closeEntry()
            }
        }
    }

    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private class TestContext(base: Context, private val namespace: String) : ContextWrapper(base) {
        private val root = File(base.cacheDir, namespace).apply { mkdirs() }
        override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences("${namespace}_$name", mode)
        fun cleanup() {
            PersistentPreferenceInventory.stores.forEach { getSharedPreferences(it.name, 0).edit().clear().commit() }
            root.deleteRecursively()
        }
    }
}
