package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.IsolatedAppDatabaseRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase9ADriveReceiptContractTest {
    @get:Rule val isolation = IsolatedAppDatabaseRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val db get() = AppDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
    private lateinit var repository: ReceiptRepository
    private lateinit var drive: DrivePersistenceRepository
    private lateinit var fake: ManagedDocumentFakeDrive
    private val config = DriveAppConfig(rootFolderId = "root", systemFolderId = "system", receiptsFolderId = "receipts",
        paymentsFolderId = "payments", exportsFolderId = "exports", transactionsFolderId = "transactions",
        backupsFolderId = "backups", createdAt = "2026-01-01", updatedAt = "2026-01-01")

    @Before fun setup() {
        PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        repository = ReceiptRepository(db.receiptDao(), db.propertyDao(), db.receiptEntityDao(), db.belegDao(),
            db.exportAuditDao(), db.receiptDocumentDao(), db.managedDocumentDao())
        fake = ManagedDocumentFakeDrive()
        drive = DrivePersistenceRepository(context, repository, fake)
    }
    @After fun cleanup() { PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() } }

    @Test fun actualMetadataWriterFakeDriveSnapshotAndFreshDoubleMergePreserveFieldsCentsAndOriginals() = runTest {
        val amounts = listOf(.29, 1.13, 10.01, 1000.99, -119.0)
        val bytes = SyntheticDocumentFixtureFactory.create().first { it.number == 1 }.bytes
        val originals = mutableListOf<ManagedDocument>()
        val sources = amounts.mapIndexed { index, amount ->
            val file = java.io.File(context.filesDir, "phase9a-$index.pdf").apply { writeBytes(bytes) }
            val receipt = Receipt(id = 49 + index, internalId = "phase9a-$index", displayId = "BLG-9A-$index",
                aussteller = "Quelle $index", datum = "2026-10-13", uhrzeit = "12:34", bruttobetrag = amount,
                hauptkategorie = "Sonstige Ausgaben", unterkategorie = "Material", kontoNr = "4971",
                beschreibung = if (amount < 0) "Gutschrift" else "Beschreibung", isEigenleistungSanierung = true,
                propertyId = "property-a", unitId = "unit-a", wohneinheit = "WE 01", mieter = "Mieter",
                exportlaufId = "export-9a", exportStatus = "EXPORTIERT", imageUrl = file.absolutePath,
                driveFileId = "main-$index", driveMetadataFileId = "meta-phase9a-$index")
            repository.insert(receipt)
            val document = ManagedDocument(documentId = "receipt:${receipt.internalId}", propertyId = receipt.propertyId,
                unitId = receipt.unitId, receiptInternalId = receipt.internalId, originalFilename = "Original-$index.pdf",
                storedFilename = "Original-$index.pdf", mimeType = "application/pdf", localUri = file.absolutePath,
                driveFileId = "main-$index", sha256 = StableDocumentIdentity.sha256(bytes), fileSizeBytes = bytes.size.toLong())
            repository.upsertManagedDocument(document)
            originals += document
            fake.put("main-$index", "receipts", document.storedFilename, bytes)
            assertNotNull(drive.upsertReceiptMetadata("test-token", config, receipt))
            val json = JSONObject(fake.json("meta-phase9a-$index"))
            assertEquals(2, json.getInt("schemaVersion"))
            assertEquals(ReceiptMoney.toCent(amount), json.getLong("bruttobetragCent"))
            receipt
        }
        val index = JSONObject().put("entries", JSONArray().apply { sources.forEachIndexed { i, receipt ->
            put(JSONObject().put("internalId", receipt.internalId).put("displayId", receipt.displayId)
                .put("metadataFileId", "meta-phase9a-$i").put("mainDriveFileId", "main-$i")
                .put("bruttobetragCent", ReceiptMoney.toCent(receipt.bruttobetrag)).put("syncStatus", "SYNCED"))
        } })
        fake.putJson("index", "system", "receiptIndex", index.toString())
        val snapshot = drive.buildRestoreSnapshot("test-token", config)
        assertTrue(snapshot.errors.toString(), snapshot.errors.none { it.isBlocking })
        assertEquals(5, snapshot.receipts.size)
        assertTrue(snapshot.receipts.any { it.bruttobetragCent == -11900L })
        kotlinx.coroutines.withContext(Dispatchers.IO) { db.clearAllTables() } // Logical fresh target, retaining the fake remote snapshot.
        repeat(2) { assertTrue(drive.executeFullDriveRestore("test-token", config, RestoreMode.MERGE).isSuccess) }
        assertEquals(5, repository.getAllReceiptsList().size)
        sources.forEach { source ->
            drive.downloadReceiptOriginals("test-token", repository.getReceiptByInternalId(source.internalId)!!)
            val restored = repository.getReceiptByInternalId(source.internalId)!!
            assertEquals(source.id, restored.id); assertEquals(source.propertyId, restored.propertyId)
            assertEquals(source.unitId, restored.unitId); assertEquals(source.exportlaufId, restored.exportlaufId)
            assertEquals(source.kontoNr, restored.kontoNr); assertTrue(restored.isEigenleistungSanierung)
            assertEquals(source.bruttobetrag, restored.bruttobetrag, 0.0)
            assertEquals(source.uhrzeit, restored.uhrzeit); assertEquals(source.mieter, restored.mieter)
            assertEquals(source.beschreibung, restored.beschreibung)
            val original = originals.single { it.receiptInternalId == source.internalId }
            val restoredDocument = repository.getAllManagedDocuments().single { it.documentId == original.documentId }
            assertEquals(original.unitId, restoredDocument.unitId); assertEquals(original.sha256, restoredDocument.sha256)
            assertEquals(original.driveFileId, restoredDocument.driveFileId); assertEquals(original.mimeType, restoredDocument.mimeType)
            assertArrayEquals(bytes, java.io.File(original.localUri).readBytes())
            assertArrayEquals(bytes, java.io.File(restoredDocument.localUri).readBytes())
        }
        assertTrue(drive.executeFullDriveRestore("test-token", config, RestoreMode.REPLACE_FULL).isSuccess)
        assertEquals(sources.map { it.id }, repository.getAllReceiptsList().map { it.id }.sorted())
        assertEquals("export-9a", repository.getReceiptByInternalId("phase9a-0")!!.exportlaufId)
        originals.forEach { java.io.File(it.localUri).delete() }
    }

    @Test fun checkedCentRoundingRejectsNonfiniteAndOverflowButAllowsExistingSignedRule() {
        listOf(.29 to 29L, 1.13 to 113L, 1.10 to 110L, 10.01 to 1001L, 1000.99 to 100099L, 0.0 to 0L, -1.19 to -119L)
            .forEach { (euro, cent) -> assertEquals(cent, ReceiptMoney.toCent(euro)) }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MAX_VALUE)
            .forEach { assertTrue(runCatching { ReceiptMoney.toCent(it) }.isFailure) }
    }

    @Test fun roomIdConflictNeverOverwritesAnUnrelatedReceipt() = runTest {
        val existing = Receipt(49, "Bestand", "2026-10-13", "", 10.0, "Sonstige Ausgaben", "", "4970", "", internalId = "other")
        repository.insert(existing)
        val before = repository.getReceiptById(49)!!
        assertTrue(runCatching { repository.upsertRestoredReceipt(before.copy(internalId = "incoming"), preserveIncomingId = true) }.isFailure)
        assertEquals(before, repository.getReceiptById(49))
    }
}
