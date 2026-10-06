package com.example.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RentHistoryReliabilityTest {
    @Test fun tenantChangeKeepsOldPeriodAndUnchangedPrefilledAmounts() {
        val old = TenantPeriod(1, "WE 1", "Mieter A", "2026-01-01", "", 760.0, 220.0, 0.0)
        val newCold = parseTenantNumber(GermanNumberInput.formatForInput(old.kaltmiete))!!
        val newNk = parseTenantNumber(GermanNumberInput.formatForInput(old.nebenkosten))!!
        val next = TenantPeriod(2, "WE 1", "Mieter B", "2026-10-01", "", newCold, newNk, 0.0)

        val changed = TenantHistoryStore.changeTenant(listOf(old), "2026-09-30", next)

        assertEquals(2, changed.size)
        assertEquals("Mieter A", changed[0].tenantName)
        assertEquals("2026-09-30", changed[0].endDate)
        assertEquals(760.0, changed[0].kaltmiete, 0.001)
        assertEquals(220.0, changed[0].nebenkosten, 0.001)
        assertEquals("Mieter B", changed[1].tenantName)
        assertEquals(760.0, changed[1].kaltmiete, 0.001)
        assertEquals(220.0, changed[1].nebenkosten, 0.001)
    }

    @Test fun datedRentChangeDoesNotRewriteEarlierMonths() {
        val period = TenantPeriod(
            id = 1, unitName = "WE 1", tenantName = "Mieter A",
            startDate = "2026-01-01", endDate = "",
            kaltmiete = 760.0, nebenkosten = 220.0, sonstige = 0.0,
            rentChanges = listOf(RentAmountChange("2026-10-01", 850.0, 220.0, 0.0))
        )

        assertEquals(980.0, TenantHistoryStore.expectedInMonth(period, YearMonth.of(2026, 1)), 0.001)
        assertEquals(980.0, TenantHistoryStore.expectedInMonth(period, YearMonth.of(2026, 9)), 0.001)
        assertEquals(1070.0, TenantHistoryStore.expectedInMonth(period, YearMonth.of(2026, 10)), 0.001)
        assertEquals(1070.0, TenantHistoryStore.expectedInMonth(period, YearMonth.of(2026, 11)), 0.001)
    }

    @Test fun multipleRentChangesUseLatestEffectiveValue() {
        val period = TenantPeriod(
            1, "WE 1", "Mieter A", "2026-01-01", "",
            760.0, 220.0, 0.0,
            listOf(
                RentAmountChange("2026-07-01", 800.0, 220.0, 0.0),
                RentAmountChange("2026-10-01", 850.0, 220.0, 0.0)
            )
        )
        assertEquals(760.0, period.amountsAt(LocalDate.of(2026, 1, 1)).kaltmiete, 0.001)
        assertEquals(800.0, period.amountsAt(LocalDate.of(2026, 7, 1)).kaltmiete, 0.001)
        assertEquals(850.0, period.amountsAt(LocalDate.of(2026, 12, 1)).kaltmiete, 0.001)
    }

    @Test fun rentChangeRequiresValidMonthStart() {
        val middle = RentPlanInput.error(
            "850,00", "220,00", "0,00", "2026-10-15",
            RentPlanEditMode.CHANGE_FROM_DATE
        )
        assertNotNull(middle)
        assertTrue(middle!!.contains("Monatsersten"))
        assertEquals(
            null,
            RentPlanInput.error(
                "850,00", "220,00", "0,00", "2026-10-01",
                RentPlanEditMode.CHANGE_FROM_DATE
            )
        )
    }
}
