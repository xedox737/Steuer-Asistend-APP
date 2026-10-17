package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.IsolatedAppDatabaseRule
import com.example.ui.TenantPeriod
import com.example.ui.TenantHistoryStore
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
class Phase10ADriveRoundTripTest {
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
        val property = PropertyMetadata(id = 1, propertyId = "property-a", name = "Testobjekt", adresse = "Musterstraße 1", wohneinheiten = UnitIdentity.encode(listOf(UnitIdentity.Entry("unit-a", "WE 01, EG links"), UnitIdentity.Entry("unit-b", "WE 02"))))
        repository.updatePropertyMetadata(property)
        val prefix = "property_property-a_unit_WE 01, EG links_"
        context.getSharedPreferences("wohneinheiten_prefs", 0).edit().putString(prefix + "id", "unit-a")
            .putString(prefix + "status", "Leerstand").putString(prefix + "label", "WE 01")
            .putFloat(prefix + "area", 41.25f).putString("property_property-a_unit_id_index_0", "unit-a").commit()
        fake.putJson("property", "system", "propertyMetadata", with(drive) { property.toJson() })
        val amounts = listOf(.29, 1.13, 10.01, 1000.99, -119.0)
        val bytes = SyntheticDocumentFixtureFactory.create().first { it.number == 1 }.bytes
        val originals = mutableListOf<ManagedDocument>()
        val sources = amounts.mapIndexed { index, amount ->
            val file = java.io.File(context.filesDir, "phase9a-$index.pdf").apply { writeBytes(bytes) }
            val receipt = Receipt(id = 49 + index, internalId = "phase9a-$index", displayId = "BLG-9A-$index",
                aussteller = "Quelle $index", datum = "2026-10-13", uhrzeit = "12:34", bruttobetrag = amount,
                hauptkategorie = "Sonstige Ausgaben", unterkategorie = "Material", kontoNr = "4971",
                beschreibung = if (amount < 0) "Gutschrift" else "Beschreibung", isEigenleistungSanierung = true,
                propertyId = "property-a", unitId = "unit-a", wohneinheit = "WE 01, EG links", mieter = "Mieter",
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
        db.exportAuditDao().insertRun(ExportAuditRun("export-9a", timestamp = 1791889307000, status = "SUCCESS"))
        db.bankDao().upsertAccount(BankAccount("account-9a", "Testkonto"))
        db.bankDao().upsertTransaction(BankTransaction("tx-9a", "account-9a", "2026-10-13", amount = .29, propertyId = "property-a"))
        db.bankDao().upsertLink(BankReceiptLink("link-9a", "tx-9a", sources.first().id, sources.first().internalId, .29))
        context.getSharedPreferences("wohneinheiten_prefs", 0).edit()
            .putString("property_property-a_unit_WE 02_id", "unit-b")
            .putFloat("property_property-a_unitid_unit-b_area", 55.5f).commit()
        val units = drive.getWohneinheitenFromPrefs(property)
        assertEquals(2, units.size)
        fake.putJson("units", "system", "units", with(drive) { units.toJson() })
        val period = TenantPeriod(10101, "WE 01, EG links", "Martin Weber", "2020-01-01", "", 600.0, 100.0, 0.0)
        TenantHistoryStore.save(context, "property-a", "unit-a", "WE 01, EG links", listOf(period))
        val renovation = RenovationReviewStore(context)
        renovation.saveMeasure(RenovationMeasure("measure-10a", "property-a", "unit-a", "Bad"))
        renovation.saveRelation(RenovationReceiptRelation(sources.first().internalId, "property-a", "measure-10a"))
        val reviewBefore = renovation.snapshot.value
        val supplemental = SupplementalDriveBackup.createPayload(context, db)
        fake.putJson("supplemental", "system", "supplementalBackup", supplemental.toString())
        suspend fun restore(mode: RestoreMode): Boolean = FullRestoreCoordinator.execute(
            coreRestore = { drive.executeFullDriveRestore("test-token", config, mode) },
            preflight = { SupplementalDriveBackup.validatePayload(JSONObject(fake.json("supplemental"))) },
            supplementalRestore = {
                SupplementalDriveBackup.restorePayload(context, db, JSONObject(fake.json("supplemental")),
                    replaceManagedDocuments = mode == RestoreMode.REPLACE_FULL, restoreMode = mode)
                SupplementalDriveBackup.Result(true, "ok")
            }).isSuccess
        val snapshot = drive.buildRestoreSnapshot("test-token", config)
        assertTrue(snapshot.errors.toString(), snapshot.errors.none { it.isBlocking })
        assertEquals(5, snapshot.receipts.size)
        assertTrue(snapshot.receipts.any { it.bruttobetragCent == -11900L })
        kotlinx.coroutines.withContext(Dispatchers.IO) { db.clearAllTables() } // Logical fresh target, retaining the fake remote snapshot.
        PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        repeat(2) {
            assertTrue(restore(RestoreMode.MERGE))
            val restoredProperty = repository.getPropertyMetadata()!!
            assertEquals(units, drive.getWohneinheitenFromPrefs(restoredProperty))
            assertEquals(listOf(period), TenantHistoryStore.load(context, "property-a", "unit-a", "WE 01, EG links"))
            renovation.refresh()
            assertEquals(reviewBefore, renovation.snapshot.value)
        }
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
        assertEquals(sources.first().id, db.bankDao().getAllLinks().single().receiptId)
        assertEquals(sources.first().internalId, db.bankDao().getAllLinks().single().receiptInternalId)
        assertNotNull(db.exportAuditDao().getRunById("export-9a"))
        assertEquals("unit-a", context.getSharedPreferences("wohneinheiten_prefs", 0).getString(prefix + "id", null))
        val renamed = property.copy(wohneinheiten = UnitIdentity.encode(listOf(UnitIdentity.Entry("unit-a", "Lokal, geändert"), UnitIdentity.Entry("unit-b", "WE 02"))))
        repository.updatePropertyMetadata(renamed)
        repeat(2) { assertTrue(restore(RestoreMode.MERGE)) }
        assertEquals(renamed.wohneinheiten, repository.getPropertyMetadata()!!.wohneinheiten)
        assertTrue(restore(RestoreMode.REPLACE_FULL))
        assertEquals(property.wohneinheiten, repository.getPropertyMetadata()!!.wohneinheiten)
        assertEquals(units, drive.getWohneinheitenFromPrefs(repository.getPropertyMetadata()))
        assertEquals(sources.map { it.id }, repository.getAllReceiptsList().map { it.id }.sorted())
        assertEquals("export-9a", repository.getReceiptByInternalId("phase9a-0")!!.exportlaufId)
        originals.forEach { java.io.File(it.localUri).delete() }
    }

}
