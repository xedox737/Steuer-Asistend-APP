package com.example.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TenantHistoryReliabilityTest {
    @Test
    fun prefilledTechnicalRentValuesDoNotMultiplyOnSave() {
        assertEquals(760.0, parseTenantNumber("760.0")!!, 0.0)
        assertEquals(220.0, parseTenantNumber("220.0")!!, 0.0)
        assertEquals(760.0, parseTenantNumber("760,00")!!, 0.0)
        assertEquals(1249.90, parseTenantNumber("1.249,90")!!, 0.001)
    }

    @Test
    fun tenantChangeEndsOldPeriodAndKeepsItsHistoricalValues() {
        val old = TenantPeriod(
            id = 10L,
            unitName = "OG",
            tenantName = "Mieter A",
            startDate = "2026-01-01",
            endDate = "",
            kaltmiete = 760.0,
            nebenkosten = 220.0,
            sonstige = 0.0,
            rentChanges = listOf(RentAmountChange("2026-07-01", 800.0, 220.0, 0.0))
        )
        val next = TenantPeriod(
            id = 11L,
            unitName = "OG",
            tenantName = "Mieter B",
            startDate = "2026-10-01",
            endDate = "",
            kaltmiete = 850.0,
            nebenkosten = 220.0,
            sonstige = 0.0
        )

        val result = TenantHistoryStore.changeTenant(listOf(old), "2026-09-30", next)

        assertEquals(2, result.size)
        val preserved = result.first()
        assertEquals(10L, preserved.id)
        assertEquals("Mieter A", preserved.tenantName)
        assertEquals("2026-09-30", preserved.endDate)
        assertEquals(760.0, preserved.kaltmiete, 0.0)
        assertEquals(220.0, preserved.nebenkosten, 0.0)
        assertEquals(1, preserved.rentChanges.size)
        assertFalse(preserved.active)

        val current = result.last()
        assertEquals(11L, current.id)
        assertEquals("Mieter B", current.tenantName)
        assertEquals(850.0, current.kaltmiete, 0.0)
        assertEquals(220.0, current.nebenkosten, 0.0)
        assertTrue(current.active)
    }

    @Test
    fun rentChangeValidationRequiresRealFirstOfMonthDate() {
        assertEquals(
            null,
            RentPlanInput.error(
                "850,00", "220,00", "0,00", "2026-10-01", mode = RentPlanEditMode.CHANGE_FROM_DATE
            )
        )
        assertEquals(
            "Mietänderungen sind derzeit nur zum Monatsersten möglich.",
            RentPlanInput.error(
                "850,00", "220,00", "0,00", "2026-10-15", mode = RentPlanEditMode.CHANGE_FROM_DATE
            )
        )
        assertEquals(
            "Bitte ein gültiges Datum für die Mietänderung im Format JJJJ-MM-TT eingeben.",
            RentPlanInput.error(
                "850,00", "220,00", "0,00", "2026-02-30", mode = RentPlanEditMode.CHANGE_FROM_DATE
            )
        )
    }
}
