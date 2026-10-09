package com.example.e2e

import androidx.compose.ui.test.*
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.TenantHistoryStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TenantChronologyEmulatorTest : EmulatorTestSupport() {
    @Test fun impossibleContractEndIsBlockedAndValidTenantChangeIsPersisted() {
        val property = seedProperty(rented = true)
        val unitId = "${property.propertyId}-unit"
        openUnits(property.propertyId)
        ui.onNodeWithTag("property_units_overview").performScrollToNode(hasTestTag("unit_tenant_change_$unitId"))
        ui.onNodeWithTag("unit_tenant_change_$unitId").performClick()
        ui.onNodeWithText("Mieterwechsel erfassen").assertIsDisplayed()
        changeField("tenant_change_old_end", "2020-03-31")
        changeField("tenant_change_name", "Testmieter Neu")
        changeField("tenant_change_new_start", "2020-04-01")
        ui.onNodeWithText("Wechsel speichern").performClick()
        ui.onNodeWithText("Das Vertragsende darf nicht vor dem Mietbeginn liegen.")
            .performScrollTo().assertIsDisplayed()
        val unchanged = TenantHistoryStore.load(context, property.propertyId, unitId, "WE 01")
        assertEquals(1, unchanged.size)
        assertEquals("", unchanged.single().endDate)
        assertEquals("Testmieter Alt", vm.wohneinheitenStatus.value.single().mieter)
        capture("tenant-invalid-chronology")

        changeField("tenant_change_old_end", "2024-03-31")
        changeField("tenant_change_new_start", "2024-04-01")
        input("Neue Kaltmiete / Monat €*", "760,50")
        input("Neue NK-Vorauszahlung / Monat €", "220,25")
        ui.onNodeWithText("Wechsel speichern").performClick()
        val model = vm
        ui.waitUntil(10_000) { model.wohneinheitenStatus.value.single().mieter == "Testmieter Neu" }
        val saved = TenantHistoryStore.load(context, property.propertyId, unitId, "WE 01").sortedBy { it.startDate }
        assertEquals(2, saved.size)
        assertEquals("2024-03-31", saved.first().endDate)
        assertEquals("2024-04-01", saved.last().startDate)
        assertEquals(760.50, saved.last().kaltmiete, .001)
        assertEquals(220.25, saved.last().nebenkosten, .001)
        systemBack()
        openUnits(property.propertyId)
        ui.onNodeWithTag("property_units_overview").performScrollToNode(hasText("Testmieter Neu"))
        ui.onNodeWithText("Testmieter Neu").assertIsDisplayed()
        capture("tenant-valid-change")
    }

    private fun changeField(tag: String, value: String) {
        ui.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
        Espresso.closeSoftKeyboard()
    }
}
