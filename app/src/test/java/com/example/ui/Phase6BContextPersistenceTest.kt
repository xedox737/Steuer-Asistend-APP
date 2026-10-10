package com.example.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Phase6BContextPersistenceTest {
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var vm: ReceiptViewModel
    private val models = ViewModelStore()
    private val singleton = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    private var previous: AppDatabase? = null
    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        PersistentPreferenceInventory.stores.forEach { app.getSharedPreferences(it.name, 0).edit().clear().commit() }
        app.getSharedPreferences("google_drive_prefs", 0).edit().putBoolean("user_disconnected", true).commit()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        previous = singleton.get(null) as AppDatabase?
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build(); singleton.set(null, db)
        runBlocking {
            listOf("a", "b").forEachIndexed { index, id ->
                app.getSharedPreferences("wohneinheiten_prefs", 0).edit()
                    .putString("property_${id}_unit_WE 01_id", "$id-unit")
                    .putString("property_${id}_unit_id_index_0", "$id-unit").commit()
                db.propertyDao().insertPropertyMetadata(PropertyMetadata(id = index + 1, propertyId = id, name = "Haus $id", wohneinheiten = "WE 01"))
            }
        }
        vm = ViewModelProvider(models, ViewModelProvider.AndroidViewModelFactory(app))[ReceiptViewModel::class.java]
        runBlocking { vm.properties.first { it.size == 2 } }
    }
    @After fun cleanup() { models.clear(); singleton.set(null, previous); db.close(); Dispatchers.resetMain() }
    private fun save(property: String, unit: String = "", name: String = "Gesamtobjekt / Allgemein") =
        vm.saveReceipt("Global", "2026-10-10", "", 119.0, "Renovierungs- / Reparaturkosten & Investitionen", "Bad", "4800", "Manuell", false,
            propertyId = property, selectedUnitId = unit, wohneinheit = name)
    private fun saved() = runBlocking { db.receiptDao().getAllReceipts().first { it.isNotEmpty() }.single() }
    @Test fun globalSaveUsesExplicitAInsteadOfLastSelectedBAndReloadsSameIdentity() {
        vm.selectProperty("b"); save("a", "a-unit", "WE 01")
        val receipt = saved(); assertEquals("a", receipt.propertyId); assertEquals("a-unit", receipt.unitId)
        assertEquals("WE 01", receipt.wohneinheit); assertEquals("b", vm.selectedPropertyId.value)
        runBlocking { assertEquals(receipt.internalId, db.receiptDao().getReceiptById(receipt.id)!!.internalId) }
    }
    @Test fun sameUnitNameInDifferentHousesDoesNotReuseBIdentity() {
        save("a", "b-unit", "WE 01")
        assertTrue(vm.scanState.value is ScanUiState.Error)
        assertTrue(runBlocking { db.receiptDao().getAllReceiptsList() }.isEmpty())
    }
    @Test fun propertyChangeWithClearedUnitCreatesGeneralReceipt() {
        save("b", "", "WE 01")
        val receipt = saved(); assertEquals("b", receipt.propertyId); assertEquals("", receipt.unitId); assertEquals("", receipt.wohneinheit)
    }
    @Test fun missingExplicitObjectIsBlockedEvenWhenGlobalFallbackExists() {
        vm.selectProperty("b"); save("")
        assertTrue(vm.scanState.value is ScanUiState.Error); assertTrue(runBlocking { db.receiptDao().getAllReceiptsList() }.isEmpty())
    }
    @Test fun propertyFileEntryPrefillsExplicitProperty() {
        vm.setScreen(AppScreen.PROPERTIES); vm.startReceiptForProperty("a")
        assertEquals("a", vm.receiptCreationPropertyId.value); assertEquals(AppScreen.ADD_RECEIPT, vm.currentScreen.value)
    }
    @Test fun primaryGlobalEntryDoesNotCarryHiddenFileOrigin() {
        vm.startReceiptForProperty("a"); vm.navigateToPrimaryDestination(AppScreen.ADD_RECEIPT)
        assertNull(vm.receiptCreationPropertyId.value)
    }
    @Test fun responsiveGridKeepsReadableWidthAcrossRequestedFontScales() {
        assertEquals(2, ui2GridColumns(369f, 1.0f)); assertEquals(1, ui2GridColumns(369f, 1.3f)); assertEquals(1, ui2GridColumns(369f, 1.5f))
        assertEquals(2, ui2GridColumns(800f, 1.5f))
    }
}
