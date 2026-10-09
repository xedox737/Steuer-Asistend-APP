package com.example.ui

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class Phase5BContractInputTest {
    private fun period(id: Long, start: String, end: String = "") = TenantPeriod(id, "WE 01", "Mieter", start, end, 760.0, 220.0, 0.0)

    @Test fun oldEndBeforeStartIsBlockedAtFieldAndStoreEntry() {
        val old = period(1, "2021-04-01")
        val error = TenantChronology.changeError(listOf(old), old.id, "2020-03-31", "2020-04-01")!!
        assertEquals(TenantChronology.OLD_END, error.field)
        assertEquals("Das Vertragsende darf nicht vor dem Mietbeginn liegen.", error.message)
        assertThrows(IllegalArgumentException::class.java) { TenantHistoryStore.changeTenant(listOf(old), "2020-03-31", period(2, "2020-04-01")) }
        assertEquals("", old.endDate)
    }

    @Test fun overlapsAndInvalidCalendarDatesAreBlocked() {
        val old = period(1, "2021-04-01")
        assertNotNull(TenantChronology.changeError(listOf(old), 1, "2026-10-31", "2026-10-31"))
        assertNotNull(TenantChronology.changeError(listOf(old), 1, "2026-02-30", "2026-03-01"))
        val future = period(3, "2027-04-01")
        assertNotNull(TenantChronology.changeError(listOf(old.copy(endDate = "2026-10-31"), future), null, "", "2026-11-01"))
    }

    @Test fun validFutureChangePreservesIdsAmountsAndCurrentTenant() {
        val old = period(1, "2021-04-01").copy(rentChanges = listOf(RentAmountChange("2026-07-01", 800.0, 220.0, 0.0)))
        val next = period(2, "2027-01-01")
        val changed = TenantHistoryStore.changeTenant(listOf(old), "2026-12-31", next, old.id)
        assertEquals(old.rentChanges, changed.first().rentChanges)
        assertEquals(1L, TenantHistoryStore.currentAt(changed, LocalDate.of(2026, 10, 9))!!.id)
        assertEquals("GEPLANT", TenantChronology.status(next, LocalDate.of(2026, 10, 9)))
        assertEquals("BEENDET", TenantChronology.status(changed.first(), LocalDate.of(2027, 1, 2)))
        assertEquals(2L, TenantHistoryStore.currentAt(changed, LocalDate.of(2027, 1, 2))!!.id)
    }

    @Test fun statusEndingUsesContractBoundaries() {
        val old = period(1, "2021-04-01")
        assertEquals("Das Vertragsende darf nicht vor dem Mietbeginn liegen.", TenantChronology.endError(listOf(old), 1, "2020-03-31"))
        assertNull(TenantChronology.endError(listOf(old), 1, "2021-04-01"))
    }

    @Test fun objectRawInputNeverSilentlyChangesSignOrDecimal() {
        listOf("-1", "2,5", "2.5", "1e3", "0", "999999999999").forEach { assertNull(PropertyFormInput.unitCount(it)) }
        assertEquals(25, PropertyFormInput.unitCount("25"))
        assertEquals(9999, PropertyFormInput.unitCount("9999"))
        assertNull(PropertyFormInput.year("9999", 2026))
        assertNull(PropertyFormInput.year("-1968", 2026))
        assertEquals(0, PropertyFormInput.year("", 2026))
        assertEquals(1968, PropertyFormInput.year("1968", 2026))
        assertEquals(setOf("name", "address", "type"), PropertyFormInput.requiredErrors(" ", "", "").keys)
    }

    @Test fun bulkRowsValidateGermanAmountsDatesAndVacancy() {
        val good = InitialRentRow("unit", "Müller", "2024-01-01", "760,50", "220.25", "0", "Vermietet", true)
        assertNull(InitialRentInput.error(good))
        assertNotNull(InitialRentInput.error(good.copy(start = "2024-02-30")))
        assertNotNull(InitialRentInput.error(good.copy(cold = "-1")))
        assertNotNull(InitialRentInput.error(good.copy(tenant = "")))
        assertNotNull(InitialRentInput.error(good.copy(status = "Leerstand")))
        assertNull(InitialRentInput.error(InitialRentRow("vacant", included = true)))
        assertFalse(InitialRentInput.eligible(WohneinheitStatus("WE", "WE", "Vermietet", "Mieter", 700.0, 50.0), emptyList()))
    }
}
