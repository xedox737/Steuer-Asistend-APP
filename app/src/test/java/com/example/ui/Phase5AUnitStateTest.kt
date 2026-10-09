package com.example.ui

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Phase5AUnitStateTest {
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var vm: ReceiptViewModel
    private val store = ViewModelStore()
    private val instance = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    private var previous: AppDatabase? = null
    private val property = "phase5a-property"
    private val unit = "phase5a-unit"
    private val prefix = "property_${property}_unit_id_${unit}_"
    private fun prefs(name: String) = app.getSharedPreferences(name, 0)

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        PersistentPreferenceInventory.stores.forEach { prefs(it.name).edit().clear().commit() }
        prefs("google_drive_prefs").edit().putBoolean("user_disconnected", true).commit()
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        previous = instance.get(null) as AppDatabase?
        instance.set(null, db)
        vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[ReceiptViewModel::class.java]
        prefs("wohneinheiten_prefs").edit().putString("property_${property}_unit_WE 1_id", unit).commit()
    }
    @After fun tearDown() {
        store.clear()
        instance.set(null, previous)
        db.close()
        PersistentPreferenceInventory.stores.forEach { prefs(it.name).edit().clear().commit() }
    }
    private fun readUnit(): WohneinheitStatus {
        val method = ReceiptViewModel::class.java.getDeclaredMethod("loadUnit", SharedPreferences::class.java,
            String::class.java, Int::class.javaPrimitiveType, String::class.java).apply { isAccessible = true }
        return method.invoke(vm, prefs("wohneinheiten_prefs"), property, 0, "WE 1") as WohneinheitStatus
    }
    private fun writeUnit(tenant: String, rent: Float) {
        prefs("wohneinheiten_prefs").edit().putString(prefix + "mieter", tenant).putFloat(prefix + "rent", rent)
            .putString(prefix + "status", "Vermietet").putString(prefix + "start", "2000-01-01").commit()
    }

    @Test fun martinAlt760LocalNeu850SurvivesMergeAndFullRestoreRemainsSnapshot() = runTest {
        writeUnit("Testmieter Alt", 760f)
        prefs("rent_plan_prefs").edit().putFloat("v2_${property}_${unit}_nk", 160f).commit()
        val backup = SupplementalDriveBackup.createPayload(app, db)
        writeUnit("Testmieter Neu", 850f)
        prefs("rent_plan_prefs").edit().putFloat("v2_${property}_${unit}_nk", 220f).commit()
        repeat(2) {
            SupplementalDriveBackup.restorePayload(app, db, backup)
            val state = readUnit()
            assertEquals(unit, state.unitId)
            assertEquals("Testmieter Neu", state.mieter)
            assertEquals(850.0, state.kaltmiete, 0.0)
            assertEquals(220.0, PropertyUnitScopedData.rentValue(app, property, state, "nk"), 0.0)
        }
        SupplementalDriveBackup.restorePayload(app, db, backup, restoreMode = RestoreMode.REPLACE_FULL)
        assertEquals("Testmieter Alt", readUnit().mieter)
        assertEquals(760.0, readUnit().kaltmiete, 0.0)
    }

    @Test fun currentStateAndNkComeFromRetainedValidPeriodEvenWithOlderRawPreferenceFields() = runTest {
        val old = TenantPeriod(1, "WE 1", "Testmieter Alt", "2000-01-01", "", 760.0, 160.0, 0.0)
        TenantHistoryStore.save(app, property, unit, "WE 1", listOf(old))
        writeUnit("Testmieter Alt", 760f)
        val backup = SupplementalDriveBackup.createPayload(app, db)
        val local = old.copy(id = 2, tenantName = "Testmieter Neu", startDate = "2001-01-01", kaltmiete = 850.0,
            nebenkosten = 220.0, sonstige = 30.0)
        TenantHistoryStore.save(app, property, unit, "WE 1", listOf(old.copy(endDate = "2000-12-31"), local))
        SupplementalDriveBackup.restorePayload(app, db, backup)
        val first = readUnit()
        val snapshot = PersistentPreferenceInventory.stores.associate { it.name to prefs(it.name).all.toMap() }
        SupplementalDriveBackup.restorePayload(app, db, backup)
        assertEquals(first, readUnit())
        assertEquals(snapshot, PersistentPreferenceInventory.stores.associate { it.name to prefs(it.name).all.toMap() })
        assertEquals("Testmieter Neu", first.mieter)
        assertEquals("Vermietet", first.status)
        assertEquals("2001-01-01", first.mietvertragsstart)
        assertEquals(850.0, first.kaltmiete, 0.0)
        assertEquals(220.0, PropertyUnitScopedData.rentValue(app, property, first, "nk"), 0.0)
        assertEquals(30.0, PropertyUnitScopedData.rentValue(app, property, first, "other"), 0.0)
        assertEquals(2, TenantHistoryStore.load(app, property, unit, "WE 1").size)
    }

    @Test fun historyProjectionDoesNotActivateFutureContractOrRetainEndedTenant() {
        val ended = TenantPeriod(1, "WE 1", "Alt", "2020-01-01", "2020-12-31", 760.0, 160.0, 0.0)
        val future = ended.copy(id = 2, startDate = "2030-01-01", endDate = "", tenantName = "Neu")
        assertNull(TenantHistoryStore.currentAt(listOf(ended, future), java.time.LocalDate.of(2026, 10, 8)))
        assertEquals(future, TenantHistoryStore.currentAt(listOf(ended, future), java.time.LocalDate.of(2030, 1, 1)))
    }
}
