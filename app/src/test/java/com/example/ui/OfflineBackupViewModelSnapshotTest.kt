package com.example.ui

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.OfflineBackupCoordinator
import com.example.data.OfflineBackupValidationException
import com.example.data.PersistentPreferenceInventory
import com.example.data.PropertyMetadata
import com.example.data.UnitIdentity
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executor
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = OfflineBackupViewModelSnapshotTest.ProbeApplication::class)
class OfflineBackupViewModelSnapshotTest {
    class ProbeApplication : Application() {
        var onStringRead: ((String?) -> Unit)? = null
        private val opened = mutableMapOf<String, SharedPreferences>()
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = opened.getOrPut(name) {
            val raw = super.getSharedPreferences(name, mode)
            object : SharedPreferences by raw {
                override fun getString(key: String?, defValue: String?): String? {
                    onStringRead?.invoke(key)
                    return raw.getString(key, defValue)
                }
            }
        }
    }

    private lateinit var app: ProbeApplication
    private lateinit var database: AppDatabase
    private lateinit var viewModel: ReceiptViewModel
    private val store = ViewModelStore()
    private val instance = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    private var previous: AppDatabase? = null
    private val executor = PausableExecutor()

    private class PausableExecutor : Executor {
        private var paused = false
        private val pending = mutableListOf<Runnable>()
        override fun execute(command: Runnable) {
            val queued = synchronized(this) {
                if (paused) { pending.add(command); true } else false
            }
            if (!queued) command.run()
        }
        fun pause() = synchronized(this) { paused = true }
        fun resume() {
            val queued = synchronized(this) {
                paused = false
                pending.toList().also { pending.clear() }
            }
            queued.forEach { it.run() }
        }
    }

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        PersistentPreferenceInventory.stores.forEach { app.getSharedPreferences(it.name, 0).edit().clear().commit() }
        app.getSharedPreferences("google_drive_prefs", 0).edit().putBoolean("user_disconnected", true).commit()
        previous = instance.get(null) as AppDatabase?
        database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .setQueryExecutor(executor).setTransactionExecutor(executor).allowMainThreadQueries().build()
        instance.set(null, database)
        viewModel = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[ReceiptViewModel::class.java]
    }

    @After fun tearDown() {
        app.onStringRead = null
        executor.resume()
        store.clear()
        instance.set(null, previous)
        database.close()
    }

    @Test fun propertyCreationIsRegisteredBeforeDatabaseEntryAndPublishesAllStoresBeforeRelease() {
        val coordinator = OfflineBackupCoordinator.shared
        val before = coordinator.beginCapture()
        val parent = requireNotNull(viewModel.viewModelScope.coroutineContext[Job])
        val existing = parent.children.toSet()
        val unit = WohneinheitStatus("WE", "Wohnung", "Leerstand", "", 123.5, 45.0, unitId = "U")
        executor.pause()
        viewModel.createProperty(PropertyMetadata(propertyId = "P", name = "Haus"), listOf(unit))
        val created = parent.children.filter { it !in existing }.toList()
        try {
            assertEquals(1, created.size)
            assertThrows(OfflineBackupValidationException::class.java) { coordinator.beginCapture() }
        } finally {
            executor.resume()
            runBlocking { created.forEach { it.join() } }
        }
        val saved = runBlocking { requireNotNull(database.propertyDao().getPropertyByPropertyId("P")) }
        assertEquals("Haus", saved.name)
        assertEquals(listOf(UnitIdentity.Entry("U", "WE")),
            UnitIdentity.read(saved.wohneinheiten, "P", app.getSharedPreferences("wohneinheiten_prefs", 0)).entries)
        val prefs = app.getSharedPreferences("wohneinheiten_prefs", 0)
        assertEquals("Wohnung", prefs.getString("property_P_unitid_U_label", null))
        assertEquals(45f, prefs.getFloat("property_P_unitid_U_area", 0f), 0f)
        assertEquals(123.5f, prefs.getFloat("property_P_unitid_U_rent", 0f), 0f)
        assertEquals("P", app.getSharedPreferences("google_drive_prefs", 0).getString("selected_property_id", null))
        assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(before) }
        coordinator.completeCapture(coordinator.beginCapture())
    }

    @Test fun legacyUnitReadKeepsRegistrationBetweenFieldCopiesAndCanonicalReadIsQuiet() {
        val prefs = app.getSharedPreferences("wohneinheiten_prefs", 0)
        val old = "property_P_unit_Altname_"
        val current = "property_P_unitid_U_"
        prefs.edit().putString(old + "id", "U").putString(old + "label", "Wohnung")
            .putString(old + "status", "Renovierung").putString(old + "mieter", "Mieter")
            .putFloat(old + "rent", 123.5f).putFloat(old + "area", 45f)
            .putString(old + "start", "2026-01-01").commit()
        val property = PropertyMetadata(propertyId = "P", name = "Haus",
            wohneinheiten = UnitIdentity.encode(listOf(UnitIdentity.Entry("U", "Altname"))))
        val coordinator = OfflineBackupCoordinator.shared
        val before = coordinator.beginCapture()
        val blockedBetweenEditors = mutableListOf<Boolean>()
        app.onStringRead = { key ->
            if (key == old + "status" && prefs.contains(current + "label")) {
                blockedBetweenEditors += runCatching { coordinator.beginCapture() }
                    .exceptionOrNull() is OfflineBackupValidationException
            }
        }
        val unit = viewModel.getWohneinheitenForProperty(property).single()
        app.onStringRead = null
        assertEquals(listOf(true), blockedBetweenEditors)
        assertEquals("U", unit.unitId)
        assertEquals("Wohnung", unit.label)
        assertEquals("Renovierung", unit.status)
        assertEquals("Mieter", unit.mieter)
        assertEquals(123.5, unit.kaltmiete, 0.0)
        assertEquals(45.0, unit.wohnflaeche, 0.0)
        assertEquals("2026-01-01", unit.mietvertragsstart)
        assertEquals("Wohnung", prefs.getString(old + "label", null))
        assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(before) }
        val canonical = coordinator.beginCapture()
        assertEquals(unit, viewModel.getWohneinheitenForProperty(property).single())
        coordinator.completeCapture(canonical)
    }
}
