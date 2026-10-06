package com.example.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class Phase1BInputAndRentHistoryTest {

    @Test
    fun germanNumberParser_acceptsGermanAndTechnicalFormats() {
        assertEquals(1234.0, GermanNumberInput.parse("1234")!!, 0.001)
        assertEquals(1234.5, GermanNumberInput.parse("1234,5")!!, 0.001)
        assertEquals(1234.50, GermanNumberInput.parse("1.234,50")!!, 0.001)
        assertEquals(0.75, GermanNumberInput.parse("0,75")!!, 0.001)
        assertEquals(72.5, GermanNumberInput.parse("72,5")!!, 0.001)
        assertEquals(760.0, GermanNumberInput.parse("760.0")!!, 0.001)
        assertEquals(1050000.0, GermanNumberInput.parse("1.050.000,00")!!, 0.001)
    }

    @Test
    fun germanNumberParser_rejectsMalformedAndNonFiniteValues() {
        listOf("abc", "12,34,56", "1e309", "NaN", "Infinity", "-Infinity").forEach {
            assertNull("Expected invalid: $it", GermanNumberInput.parse(it))
        }
        assertNull(GermanNumberInput.parseNonNegative("-1,00"))
    }

    @Test
    fun calendarValidation_usesRealCalendarDates() {
        assertNotNull(CalendarInput.parseIsoDate("2024-02-29"))
        assertNotNull(CalendarInput.parseIsoDate("2026-02-28"))
        assertNull(CalendarInput.parseIsoDate("2026-02-29"))
        assertNull(CalendarInput.parseIsoDate("2026-02-30"))
        assertNull(CalendarInput.parseIsoDate("2026-13-01"))
        assertNull(CalendarInput.parseIsoDate("2026-00-01"))
    }

    @Test
    fun receiptValidation_keepsGermanAmountAndRejectsInvalidDate() {
        assertEquals(1249.90, ReceiptInputValidation.amount("1.249,90")!!, 0.001)
        assertEquals(1249.90, ReceiptInputValidation.amount("1249,90")!!, 0.001)
        assertNull(ReceiptInputValidation.date("2026-02-30"))
        assertEquals(LocalDate.of(2026, 2, 28), ReceiptInputValidation.date("2026-02-28"))
    }

    @Test
    fun propertyWizard_parsesGermanValuesAndRejectsInvalidInputs() {
        val ok = PropertyWizardInput.validate(
            purchasePrice = "1.050.000,00",
            livingArea = "620,5",
            landArea = "950",
            purchaseDate = "2026-10-01",
            yearBuilt = "1954",
            buildingValue = "",
            landValue = "",
            loanAmount = ""
        )
        assertTrue(ok.errors.isEmpty())
        assertEquals(1050000.0, ok.values!!.purchasePrice, 0.001)
        assertEquals(620.5, ok.values!!.livingArea, 0.001)

        val badArea = PropertyWizardInput.validate(
            purchasePrice = "100000",
            livingArea = "-1",
            landArea = "950",
            purchaseDate = "2026-10-01",
            yearBuilt = "",
            buildingValue = "",
            landValue = "",
            loanAmount = ""
        )
        assertTrue(badArea.errors.containsKey(PropertyWizardInput.LIVING_AREA))

        val badDate = PropertyWizardInput.validate(
            purchasePrice = "100000",
            livingArea = "100",
            landArea = "950",
            purchaseDate = "2026-02-30",
            yearBuilt = "",
            buildingValue = "",
            landValue = "",
            loanAmount = ""
        )
        assertTrue(badDate.errors.containsKey(PropertyWizardInput.PURCHASE_DATE))

        val overflow = PropertyWizardInput.validate(
            purchasePrice = "1e309",
            livingArea = "100",
            landArea = "950",
            purchaseDate = "2026-10-01",
            yearBuilt = "",
            buildingValue = "",
            landValue = "",
            loanAmount = ""
        )
        assertTrue(overflow.errors.containsKey(PropertyWizardInput.PURCHASE_PRICE))
    }

    @Test
    fun tenantNumberRoundtrip_doesNotMultiplyTechnicalDecimal() {
        assertEquals(760.0, parseTenantNumber("760.0")!!, 0.001)
        assertEquals(220.0, parseTenantNumber("220,00")!!, 0.001)
    }

    @Test
    fun rentChange_appliesOnlyFromEffectiveMonthAndPreservesHistory() {
        val period = TenantPeriod(
            id = 1,
            unitName = "WE 1",
            tenantName = "Alt",
            startDate = "2026-01-01",
            endDate = "",
            kaltmiete = 760.0,
            nebenkosten = 220.0,
            sonstige = 0.0,
            rentChanges = listOf(
                RentAmountChange("2026-10-01", 850.0, 220.0, 0.0)
            )
        )
        assertEquals(980.0, TenantHistoryStore.expectedInMonth(period, YearMonth.of(2026, 9)), 0.001)
        assertEquals(1070.0, TenantHistoryStore.expectedInMonth(period, YearMonth.of(2026, 10)), 0.001)
        assertEquals(760.0, period.amountsAt(LocalDate.of(2026, 9, 30)).kaltmiete, 0.001)
        assertEquals(850.0, period.amountsAt(LocalDate.of(2026, 10, 1)).kaltmiete, 0.001)
    }

    @Test
    fun multipleRentChanges_useLatestEffectiveAmount() {
        val period = TenantPeriod(
            id = 1,
            unitName = "WE 1",
            tenantName = "Mieter",
            startDate = "2026-01-01",
            endDate = "",
            kaltmiete = 760.0,
            nebenkosten = 220.0,
            sonstige = 0.0,
            rentChanges = listOf(
                RentAmountChange("2026-07-01", 800.0, 220.0, 0.0),
                RentAmountChange("2026-10-01", 850.0, 230.0, 0.0)
            )
        )
        assertEquals(760.0, period.amountsAt(LocalDate.of(2026, 6, 30)).kaltmiete, 0.001)
        assertEquals(800.0, period.amountsAt(LocalDate.of(2026, 7, 1)).kaltmiete, 0.001)
        assertEquals(1080.0, TenantHistoryStore.expectedInMonth(period, YearMonth.of(2026, 10)), 0.001)
    }

    @Test
    fun tenantChange_closesOldPeriodAndKeepsOldRentHistory() {
        val old = TenantPeriod(
            id = 1,
            unitName = "WE 1",
            tenantName = "Alt",
            startDate = "2026-01-01",
            endDate = "",
            kaltmiete = 760.0,
            nebenkosten = 220.0,
            sonstige = 0.0,
            rentChanges = listOf(RentAmountChange("2026-07-01", 800.0, 220.0, 0.0))
        )
        val next = TenantPeriod(
            id = 2,
            unitName = "WE 1",
            tenantName = "Neu",
            startDate = "2026-10-01",
            endDate = "",
            kaltmiete = 850.0,
            nebenkosten = 220.0,
            sonstige = 0.0
        )
        val changed = TenantHistoryStore.changeTenant(listOf(old), "2026-09-30", next)
        assertEquals(2, changed.size)
        assertEquals("2026-09-30", changed.first().endDate)
        assertEquals(1, changed.first().rentChanges.size)
        assertEquals("Neu", changed.last().tenantName)
        assertTrue(changed.last().active)
    }

    @Test
    fun rentPlan_changeModeRequiresRealMonthStartDate() {
        assertNull(RentPlanInput.error("850,00", "220,00", "", "2026-10-01", mode = RentPlanEditMode.CHANGE_FROM_DATE))
        assertNotNull(RentPlanInput.error("850,00", "220,00", "", "2026-10-15", mode = RentPlanEditMode.CHANGE_FROM_DATE))
        assertNotNull(RentPlanInput.error("850,00", "220,00", "", "2026-02-30", mode = RentPlanEditMode.CHANGE_FROM_DATE))
        assertFalse(RentPlanInput.needsLargeChangeConfirmation(760.0, 850.0))
        assertTrue(RentPlanInput.needsLargeChangeConfirmation(760.0, 2500.0))
    }
}
