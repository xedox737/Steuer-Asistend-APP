package com.example.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase9AStoredDataPreservationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val unit = WohneinheitStatus("WE 01", "WE 01", "Vermietet", "Mieter", 760.0, 72.5, "2026-01-01", "unit-a")
    private val period = TenantPeriod(81, unit.name, "Mieter", "2026-01-01", "", 760.0, 220.0, 0.0)
    private val key = "history_v2_property-a_unit-a"
    private fun history() = context.getSharedPreferences("tenant_history_prefs", 0)
    private fun tasks() = context.getSharedPreferences(PropertyTaskStore.PREFS, 0)

    @Before fun clear() { history().edit().clear().commit(); tasks().edit().clear().commit() }
    @After fun cleanup() = clear()

    @Test fun damagedHistoryKeepsValidNeighbourAndExactRawDataAcrossReadEnsureAndSave() {
        assertTrue(TenantHistoryStore.save(context, "property-a", unit.unitId, unit.name, listOf(period)))
        val raw = JSONArray(history().getString(key, null)).put(17).toString()
        history().edit().putString(key, raw).commit()
        val read = TenantHistoryStore.read(context, "property-a", unit.unitId, unit.name)
        assertTrue(read.hasStoredData); assertTrue(read.damaged); assertNotNull(read.error)
        assertEquals(listOf(period), read.entries)
        assertEquals(listOf(period), TenantHistoryStore.ensureCurrentPeriod(context, unit, 220.0, 0.0, "property-a"))
        assertFalse(TenantHistoryStore.save(context, "property-a", unit.unitId, unit.name, listOf(period.copy(kaltmiete = 900.0))))
        assertFalse(TenantHistoryStore.correctCurrentRentalDetails(context, "property-a", unit.unitId, unit.copy(kaltmiete = 900.0)))
        assertEquals(raw, history().getString(key, null))
    }

    @Test fun damagedInnerRentChangeAndWrongPreferenceTypeAreNotEmptyStores() {
        assertTrue(TenantHistoryStore.save(context, "property-a", unit.unitId, unit.name, listOf(period)))
        val array = JSONArray(history().getString(key, null))
        array.getJSONObject(0).put("rentChanges", JSONArray().put(JSONObject().put("effectiveDate", "2026-07-01").put("kaltmiete", "kaputt")))
        val raw = array.toString(); history().edit().putString(key, raw).commit()
        assertTrue(TenantHistoryStore.read(context, "property-a", unit.unitId, unit.name).damaged)
        TenantHistoryStore.ensureCurrentPeriod(context, unit, 220.0, 0.0, "property-a")
        assertEquals(raw, history().getString(key, null))
        history().edit().putInt(key, 42).commit()
        assertTrue(TenantHistoryStore.read(context, "property-a", unit.unitId, unit.name).damaged)
        TenantHistoryStore.ensureCurrentPeriod(context, unit, 220.0, 0.0, "property-a")
        assertEquals(42, history().getInt(key, 0))
    }

    @Test fun absentAndExplicitlyEmptyHistoryRemainInitializableAndHealthyWritesWork() {
        assertFalse(TenantHistoryStore.read(context, "property-a", unit.unitId, unit.name).hasStoredData)
        history().edit().putString(key, "[]").commit()
        assertFalse(TenantHistoryStore.read(context, "property-a", unit.unitId, unit.name).damaged)
        assertEquals(1, TenantHistoryStore.ensureCurrentPeriod(context, unit, 220.0, 0.0, "property-a").size)
        assertTrue(TenantHistoryStore.save(context, "property-a", unit.unitId, unit.name, listOf(period)))
        assertEquals(listOf(period), TenantHistoryStore.load(context, "property-a", unit.unitId, unit.name))
    }

    @Test fun damagedTaskArrayBlocksAllWritersWithoutDiscardingHealthyNeighbour() {
        val task = PropertyTask(id = "task-a", propertyId = "property-a", title = "Frist", createdAt = "2026-01-01", updatedAt = "2026-01-01")
        assertTrue(PropertyTaskStore.upsert(context, task))
        val raw = JSONArray(tasks().getString("tasks_property-a", null)).put(17).toString()
        tasks().edit().putString("tasks_property-a", raw).commit()
        val read = PropertyTaskStore.read(context, "property-a")
        assertTrue(read.damaged); assertEquals(listOf(task), read.entries)
        assertFalse(PropertyTaskStore.upsert(context, task.copy(id = "task-b", title = "Neu")))
        assertFalse(PropertyTaskStore.save(context, "property-a", emptyList()))
        assertEquals(raw, tasks().getString("tasks_property-a", null))
    }

    @Test fun healthyTaskUpsertAndToggleKeepStableIdentity() {
        val task = PropertyTask(id = "task-a", propertyId = "property-a", title = "Frist", createdAt = "2026-01-01", updatedAt = "2026-01-01")
        assertTrue(PropertyTaskStore.upsert(context, task))
        assertTrue(PropertyTaskStore.upsert(context, task.copy(done = true)))
        assertEquals(listOf(task.copy(done = true)), PropertyTaskStore.load(context, "property-a"))
    }

    @Test fun missingOrFractionalHistoryIdsAndNonStringTaskIdsNeverGenerateReplacementIds() {
        assertTrue(TenantHistoryStore.save(context, "property-a", unit.unitId, unit.name, listOf(period)))
        val good = JSONArray(history().getString(key, null)).getJSONObject(0)
        for (bad in listOf(JSONObject(good.toString()).apply { remove("id") }, JSONObject(good.toString()).put("id", 1.5))) {
            val raw = JSONArray().put(good).put(bad).toString()
            history().edit().putString(key, raw).commit()
            val read = TenantHistoryStore.read(context, "property-a", unit.unitId, unit.name)
            assertTrue(read.damaged); assertEquals(listOf(period), read.entries)
            TenantHistoryStore.ensureCurrentPeriod(context, unit, 220.0, 0.0, "property-a")
            assertEquals(raw, history().getString(key, null))
        }
        val raw = JSONArray().put(JSONObject().put("id", 17).put("title", "Aufgabe")).toString()
        tasks().edit().putString("tasks_property-a", raw).commit()
        assertTrue(PropertyTaskStore.read(context, "property-a").damaged)
        assertFalse(PropertyTaskStore.upsert(context, PropertyTask("new", "property-a", title = "Neu", createdAt = "", updatedAt = "")))
        assertEquals(raw, tasks().getString("tasks_property-a", null))
    }
}
