package com.example.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Phase5BInitialRentPersistenceTest {
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var vm: ReceiptViewModel
    private val store = ViewModelStore()
    private val instance = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    private var previous: AppDatabase? = null
    private val property = PropertyMetadata(id = 71, propertyId = "phase5b-a", name = "Haus", adresse = "Teststraße 1",
        wohneinheiten = (1..25).joinToString(", ") { "WE ${it.toString().padStart(2, '0')}" })

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        PersistentPreferenceInventory.stores.forEach { app.getSharedPreferences(it.name, 0).edit().clear().commit() }
        app.getSharedPreferences("google_drive_prefs", 0).edit().putBoolean("user_disconnected", true).commit()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        previous = instance.get(null) as AppDatabase?
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        instance.set(null, db)
        vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[ReceiptViewModel::class.java]
    }

    @After fun tearDown() {
        store.clear(); instance.set(null, previous); db.close()
        PersistentPreferenceInventory.stores.forEach { app.getSharedPreferences(it.name, 0).edit().clear().commit() }
        Dispatchers.resetMain()
    }

    @Test fun twentyFiveRowsSaveTogetherKeepIdentityAndReloadFromExistingStores() = runTest {
        db.propertyDao().insertPropertyMetadata(property)
        val units = vm.getWohneinheitenForProperty(property)
        val rows = units.mapIndexed { index, unit ->
            if (index == 2) InitialRentRow(unit.unitId, included = true) else
                InitialRentRow(unit.unitId, "Mieter $index", "2024-01-01", "760,50", "220,25", "25,00", "Vermietet", true)
        }
        assertNull(vm.saveInitialRentBatch(property.propertyId, rows))
        val reloaded = vm.getWohneinheitenForProperty(property)
        assertEquals(units.map { it.unitId }, reloaded.map { it.unitId })
        assertEquals("Leerstand", reloaded[2].status)
        assertEquals("Mieter 0", reloaded.first().mieter)
        assertEquals(760.5, reloaded.first().kaltmiete, .001)
        assertEquals(220.25, PropertyUnitScopedData.rentValue(app, property.propertyId, reloaded.first(), "nk"), .001)
        assertEquals(25.0, PropertyUnitScopedData.rentValue(app, property.propertyId, reloaded.first(), "other"), .001)
        val history = reloaded.map { TenantHistoryStore.load(app, property.propertyId, it.unitId, it.name) }
        assertEquals(24, history.sumOf { it.size })
        assertNotNull(vm.saveInitialRentBatch(property.propertyId, rows))
        assertEquals(history, reloaded.map { TenantHistoryStore.load(app, property.propertyId, it.unitId, it.name) })
    }

    @Test fun invalidSecondRowWritesNothingAndExistingContractIsNeverReplaced() = runTest {
        db.propertyDao().insertPropertyMetadata(property)
        val units = vm.getWohneinheitenForProperty(property)
        val first = InitialRentRow(units[0].unitId, "Müller", "2024-01-01", "760", "220", "0", "Vermietet", true)
        val second = first.copy(unitId = units[1].unitId, start = "2024-02-30")
        assertNotNull(vm.saveInitialRentBatch(property.propertyId, listOf(first, second)))
        assertTrue(TenantHistoryStore.load(app, property.propertyId, units[0].unitId, units[0].name).isEmpty())
        val existing = TenantPeriod(501, units[1].name, "Altvertrag", "2020-01-01", "", 600.0, 200.0, 0.0)
        TenantHistoryStore.save(app, property.propertyId, units[1].unitId, units[1].name, listOf(existing))
        assertNotNull(vm.saveInitialRentBatch(property.propertyId, listOf(first, second.copy(start = "2024-01-01"))))
        assertTrue(TenantHistoryStore.load(app, property.propertyId, units[0].unitId, units[0].name).isEmpty())
        assertEquals(listOf(existing), TenantHistoryStore.load(app, property.propertyId, units[1].unitId, units[1].name))
    }

    @Test fun anotherPropertysStableUnitCannotBeWrittenThroughThisBatch() = runTest {
        db.propertyDao().insertPropertyMetadata(property)
        assertNotNull(vm.saveInitialRentBatch(property.propertyId, listOf(InitialRentRow("another-object-unit", included = true))))
        assertTrue(vm.getWohneinheitenForProperty(property).all { it.status == "Leerstand" })
    }
}
