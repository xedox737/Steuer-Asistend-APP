package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.IsolatedAppDatabaseRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

/** Real core writer/reader + supplemental payload; only the Drive transport is fake. */
abstract class Phase10CRestoreFixture {
    @get:Rule val isolation = IsolatedAppDatabaseRule()
    protected val context get() = ApplicationProvider.getApplicationContext<Context>()
    protected val db get() = AppDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
    protected lateinit var repository: ReceiptRepository
    protected lateinit var drive: DrivePersistenceRepository
    private lateinit var fake: ManagedDocumentFakeDrive
    protected lateinit var payload: JSONObject
    protected val now = "2026-10-17T10:00:00Z"
    protected val config = DriveAppConfig(rootFolderId = "root", systemFolderId = "system", receiptsFolderId = "receipts",
        paymentsFolderId = "payments", exportsFolderId = "exports", transactionsFolderId = "transactions",
        backupsFolderId = "backups", createdAt = "2026-01-01", updatedAt = "2026-01-01")

    @Before fun setupRestore() {
        clearPreferences()
        repository = ReceiptRepository(db.receiptDao(), db.propertyDao(), db.receiptEntityDao(), db.belegDao(),
            db.exportAuditDao(), db.receiptDocumentDao(), db.managedDocumentDao())
        fake = ManagedDocumentFakeDrive()
        drive = DrivePersistenceRepository(context, repository, fake)
    }
    @After fun clearPreferences() {
        PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
    }
    protected fun receipt(id: Int = 0) = Receipt(id = id, internalId = "receipt-$id", aussteller = "Test",
        datum = "2026-10-17", uhrzeit = "10:00", bruttobetrag = 3.0, hauptkategorie = "Sonstige Ausgaben",
        unterkategorie = "Material", kontoNr = "4800", beschreibung = "Test", propertyId = "property-a", unitId = "unit-a")

    protected suspend fun backup() {
        val property = repository.getAllProperties().first()
        fake.putJson("property", "system", "propertyMetadata", with(drive) { property.toJson() })
        val entries = JSONArray()
        repository.getAllReceiptsList().forEach { r ->
            assertNotNull(drive.upsertReceiptMetadata("test-token", config, r))
            entries.put(JSONObject().put("internalId", r.internalId).put("displayId", r.displayId)
                .put("metadataFileId", "meta-${r.internalId}").put("mainDriveFileId", r.driveFileId.orEmpty())
                .put("bruttobetragCent", ReceiptMoney.toCent(r.bruttobetrag)).put("syncStatus", "SYNCED"))
        }
        fake.putJson("index", "system", "receiptIndex", JSONObject().put("entries", entries).toString())
        payload = SupplementalDriveBackup.createPayload(context, db)
        val snapshot = drive.buildRestoreSnapshot("test-token", config)
        assertTrue(snapshot.errors.toString(), snapshot.errors.none { it.isBlocking })
    }
    protected suspend fun restore(mode: RestoreMode, legacyDocumentFlag: Boolean = false) {
        val outcome = restoreOutcome(mode, legacyDocumentFlag)
        assertTrue(outcome.message, outcome.isSuccess)
    }
    protected suspend fun restoreOutcome(
        mode: RestoreMode,
        legacyDocumentFlag: Boolean = false,
        supplemental: JSONObject? = payload
    ): FullRestoreOutcome = FullRestoreCoordinator.execute(
            preflight = {
                supplemental?.let(SupplementalDriveBackup::validatePayload)
                if (mode == RestoreMode.REPLACE_FULL) SupplementalDriveBackup.validateFullRestoreCoverage(context, db, supplemental)
            },
            coreRestore = { drive.executeFullDriveRestore("test-token", config, mode) },
            supplementalRestore = {
                // Explicit mode alone must enforce replace, even without the legacy document flag.
                supplemental?.let {
                    SupplementalDriveBackup.restorePayload(context, db, it, replaceManagedDocuments = legacyDocumentFlag, restoreMode = mode)
                }
                SupplementalDriveBackup.Result(true, "ok")
            })
    protected suspend fun seedProperty() {
        repository.updatePropertyMetadata(PropertyMetadata(id = 1, propertyId = "property-a", name = "Objekt",
            wohneinheiten = UnitIdentity.encode(listOf(UnitIdentity.Entry("unit-a", "WE 01, EG links")))))
    }
    protected suspend fun assertTripIntegrity() {
        val ids = repository.getAllReceiptsList().map { it.id }.toSet()
        val trips = db.logbookDao().getAllTrips()
        assertTrue("orphan expenseReceiptId", trips.none { it.expenseReceiptId != null && it.expenseReceiptId !in ids })
        assertTrue("orphan sourceReceiptId", trips.none { it.sourceReceiptId != null && it.sourceReceiptId !in ids })
        val routes = db.logbookDao().getAllStandardRoutes().map { it.id }.toSet()
        assertTrue("orphan standardRouteId", trips.none { it.standardRouteId != null && it.standardRouteId !in routes })
        val properties = repository.getAllProperties().map { it.propertyId }.toSet()
        assertTrue("orphan propertyId", trips.all { it.propertyId in properties })
    }
}
