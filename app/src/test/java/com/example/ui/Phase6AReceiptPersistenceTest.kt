package com.example.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.util.DatevMappingService
import com.example.util.DatevReceiptEligibility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Phase6AReceiptPersistenceTest {
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var vm: ReceiptViewModel
    private val store = ViewModelStore()
    private val dbName = "phase6a-${UUID.randomUUID()}"
    private val instance = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    private var previous: AppDatabase? = null
    private val profile = DatevProfile()
    private fun database() = Room.databaseBuilder(app, AppDatabase::class.java, dbName).allowMainThreadQueries().build()
    private fun receipt() = Receipt(61, "Testhandwerk", "2026-10-03", "", 119.0, "Werbungskosten", "Reparatur", "4801", "Reparatur",
        propertyId = "a", unitId = "a-1", wohneinheit = "WE 01", internalId = "phase6a-stable")

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        PersistentPreferenceInventory.stores.forEach { app.getSharedPreferences(it.name, 0).edit().clear().commit() }
        app.getSharedPreferences("google_drive_prefs", 0).edit().putBoolean("user_disconnected", true).commit()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        previous = instance.get(null) as AppDatabase?
        db = database()
        instance.set(null, db)
        runBlocking {
            listOf("a", "b").forEachIndexed { index, property ->
                val editor = app.getSharedPreferences("wohneinheiten_prefs", 0).edit()
                (1..2).forEach { n ->
                    val name = "WE ${n.toString().padStart(2, '0')}"
                    editor.putString("property_${property}_unit_${name}_id", "$property-$n")
                        .putString("property_${property}_unit_id_index_${n - 1}", "$property-$n")
                }
                check(editor.commit())
                db.propertyDao().insertPropertyMetadata(PropertyMetadata(id = index + 1, propertyId = property, name = "Haus $property", wohneinheiten = "WE 01, WE 02"))
            }
            db.receiptDao().insertReceipt(receipt())
            db.managedDocumentDao().upsert(ManagedDocument(documentId = StableDocumentIdentity.receiptDocumentId(receipt().internalId),
                propertyId = "a", unitId = "a-1", receiptInternalId = receipt().internalId, originalFilename = "Original.pdf", sha256 = "preserved-hash"))
        }
        vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[ReceiptViewModel::class.java]
    }
    @After fun tearDown() {
        store.clear(); instance.set(null, previous); db.close(); app.deleteDatabase(dbName)
        PersistentPreferenceInventory.stores.forEach { app.getSharedPreferences(it.name, 0).edit().clear().commit() }
        Dispatchers.resetMain()
    }
    private suspend fun changed(candidate: Receipt, selection: String? = null, expectedUnit: String): Receipt {
        vm.updateReceipt(candidate, selection)
        val saved = db.receiptDao().getAllReceipts().first { list -> list.any { it.id == 61 && it.unitId == expectedUnit && it.propertyId == candidate.propertyId } }.single()
        db.managedDocumentDao().observeAll().first { it.any { doc -> doc.receiptInternalId == saved.internalId && doc.unitId.orEmpty() == expectedUnit && doc.propertyId == candidate.propertyId } }
        return saved
    }
    @Test fun explicitUnitChangePersistsAcrossDatabaseAndViewModelReload() = runTest {
        val saved = changed(receipt().copy(wohneinheit = "WE 02"), "a-2", "a-2")
        assertEquals("WE 02", saved.wohneinheit)
        store.clear(); db.close(); db = database(); instance.set(null, db)
        vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[ReceiptViewModel::class.java]
        val reloaded = db.receiptDao().getReceiptById(61)!!
        assertEquals("a-2", reloaded.unitId); assertEquals(saved.internalId, reloaded.internalId)
        val doc = db.managedDocumentDao().getAllByReceiptId(saved.internalId).single()
        assertEquals("a-2", doc.unitId); assertEquals("preserved-hash", doc.sha256)
        val approved = DatevMappingService.confirmDatevPreview(reloaded, DatevMappingService.buildDatevBookingRows(reloaded, profile))!!
        val mapped = DatevMappingService.buildConfirmedDatevBookingRows(approved, profile).single()
        assertEquals("a-2", mapped.wohneinheitId); assertTrue(mapped.kost2.contains("WE02"))
    }
    @Test fun changedLegacyNameCannotRetainValidOldId() = runTest {
        assertEquals("a-2", changed(receipt().copy(wohneinheit = "WE 02"), expectedUnit = "a-2").unitId)
    }
    @Test fun generalReceiptClearsDocumentUnitAndBackupKeepsTheRemoval() = runTest {
        val saved = changed(receipt().copy(wohneinheit = ""), "", "")
        assertEquals("", saved.unitId); assertEquals("", saved.wohneinheit)
        val payload = SupplementalDriveBackup.createPayload(app, db)
        assertTrue(payload.toString().contains("phase6a-stable"))
        SupplementalDriveBackup.restorePayload(app, db, payload)
        assertNull(db.managedDocumentDao().getAllByReceiptId(saved.internalId).single().unitId)
    }
    @Test fun propertySwitchUsesOnlyNewPropertyUnit() = runTest {
        val saved = changed(receipt().copy(propertyId = "b"), "b-1", "b-1")
        assertEquals("b", saved.propertyId); assertEquals("WE 01", saved.wohneinheit)
    }
    @Test fun propertySwitchWithoutNewSelectionCannotCarryOldUnit() = runTest {
        val saved = changed(receipt().copy(propertyId = "b"), expectedUnit = "")
        assertEquals("", saved.wohneinheit)
    }
    @Test fun exportedPropertyEditRequiresApprovalAndPreservesAuditHistory() = runTest {
        val approved = DatevMappingService.confirmDatevPreview(receipt(), DatevMappingService.buildDatevBookingRows(receipt(), profile))!!
            .copy(exportStatus = "EXPORTIERT", exportlaufId = "historic-export")
        db.receiptDao().insertReceipt(approved)
        db.exportAuditDao().insertRun(ExportAuditRun("historic-export", exportierteReceiptIdsJson = "[61]", bookingCount = 1))
        val saved = changed(approved.copy(propertyId = "b"), "b-1", "b-1")
        assertEquals("OFFEN", saved.freigabestatus); assertEquals("ZU_PRUEFEN", saved.pruefstatus)
        assertFalse(DatevReceiptEligibility.isAccountingApproved(saved))
        assertTrue(DatevMappingService.buildConfirmedDatevBookingRows(saved, profile).isEmpty())
        assertEquals("historic-export", db.exportAuditDao().getRunById("historic-export")!!.exportlaufId)
    }
}
