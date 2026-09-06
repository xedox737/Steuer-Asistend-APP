package com.example.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.StableDocumentIdentity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PropertyScopedRentIsolationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("rent_plan_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("tenant_history_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("google_drive_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(PropertyTaskStore.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() = setUp()

    @Test
    fun `same unit name in two properties has isolated rent plan`() {
        val first = WohneinheitStatus("OG links", "OG links", "Vermietet", "A", 700.0, 60.0, unitId = "unit-a")
        val second = WohneinheitStatus("OG links", "OG links", "Vermietet", "B", 900.0, 70.0, unitId = "unit-b")

        PropertyUnitScopedData.setRentValues(context, "property-a", first, 180.0, 10.0)
        PropertyUnitScopedData.setRentValues(context, "property-b", second, 240.0, 25.0)

        assertEquals(180.0, PropertyUnitScopedData.rentValue(context, "property-a", first, "nk"), 0.001)
        assertEquals(240.0, PropertyUnitScopedData.rentValue(context, "property-b", second, "nk"), 0.001)
        assertEquals(10.0, PropertyUnitScopedData.rentValue(context, "property-a", first, "other"), 0.001)
        assertEquals(25.0, PropertyUnitScopedData.rentValue(context, "property-b", second, "other"), 0.001)
        assertFalse(context.getSharedPreferences("rent_plan_prefs", Context.MODE_PRIVATE).contains("nk_OG links"))
    }

    @Test
    fun `same unit name in two properties has isolated tenant history`() {
        val unitName = "OG links"
        val a = TenantPeriod(1, unitName, "Mieter A", "2026-01-01", "", 700.0, 180.0, 0.0)
        val b = TenantPeriod(2, unitName, "Mieter B", "2026-02-01", "", 900.0, 240.0, 0.0)

        TenantHistoryStore.save(context, "property-a", "unit-a", unitName, listOf(a))
        TenantHistoryStore.save(context, "property-b", "unit-b", unitName, listOf(b))

        assertEquals("Mieter A", TenantHistoryStore.load(context, "property-a", "unit-a", unitName).single().tenantName)
        assertEquals("Mieter B", TenantHistoryStore.load(context, "property-b", "unit-b", unitName).single().tenantName)
        assertFalse(context.getSharedPreferences("tenant_history_prefs", Context.MODE_PRIVATE).contains("history_$unitName"))
    }

    @Test
    fun `legacy rent plan is copied lazily but original remains`() {
        val prefs = context.getSharedPreferences("rent_plan_prefs", Context.MODE_PRIVATE)
        prefs.edit().putFloat("nk_WE 1", 155f).putFloat("other_WE 1", 12f).commit()
        val unit = WohneinheitStatus("WE 1", "WE 1", "Vermietet", "Alt", 500.0, 50.0, unitId = "legacy-unit")

        assertEquals(155.0, PropertyUnitScopedData.rentValue(context, StableDocumentIdentity.LEGACY_PROPERTY_ID, unit, "nk"), 0.001)
        assertTrue(prefs.contains("nk_WE 1"))
        assertTrue(prefs.contains("v2_${StableDocumentIdentity.LEGACY_PROPERTY_ID}_legacy-unit_nk"))
    }

    @Test
    fun `legacy tenant history is copied lazily but original remains`() {
        val arr = JSONArray().put(JSONObject().apply {
            put("id", 1L); put("tenantName", "Legacy")
            put("startDate", "2025-01-01"); put("endDate", "")
            put("kaltmiete", 500.0); put("nebenkosten", 150.0); put("sonstige", 0.0)
        })
        val prefs = context.getSharedPreferences("tenant_history_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("history_WE 1", arr.toString()).commit()

        val loaded = TenantHistoryStore.load(context, StableDocumentIdentity.LEGACY_PROPERTY_ID, "legacy-unit", "WE 1")

        assertEquals("Legacy", loaded.single().tenantName)
        assertTrue(prefs.contains("history_WE 1"))
        assertTrue(prefs.contains("history_v2_${StableDocumentIdentity.LEGACY_PROPERTY_ID}_legacy-unit"))
    }

    @Test
    fun `tasks are isolated by property`() {
        val now = "2026-09-06T20:00:00Z"
        PropertyTaskStore.upsert(context, PropertyTask("a", "property-a", title = "A", createdAt = now, updatedAt = now))
        PropertyTaskStore.upsert(context, PropertyTask("b", "property-b", title = "B", createdAt = now, updatedAt = now))

        assertEquals(listOf("A"), PropertyTaskStore.load(context, "property-a").map { it.title })
        assertEquals(listOf("B"), PropertyTaskStore.load(context, "property-b").map { it.title })
    }
}
