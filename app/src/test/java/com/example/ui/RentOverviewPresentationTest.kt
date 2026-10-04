package com.example.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PropertyMetadata
import com.example.data.Receipt
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
class RentOverviewPresentationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val unit = WohneinheitStatus("OG links", "OG links", "Vermietet", "Mieter A", 600.0, 55.0, "2026-01-01", "u-a")
    private val property = PropertyMetadata(id = 81, propertyId = "p-a", name = "Haus A")
    private fun group(u: WohneinheitStatus = unit) = RentPropertyUnits(property, listOf(u))
    private fun receipt(id: Int = 1, amount: Double = 600.0, sub: String = "Kaltmiete", date: String = "2026-01-03", propertyId: String = "p-a", unitName: String = "OG links") =
        Receipt(id = id, aussteller = "Mieter", datum = date, uhrzeit = "", bruttobetrag = amount, hauptkategorie = "Miete, Nebenkosten & Kaution",
            unterkategorie = sub, kontoNr = "8100", beschreibung = "Miete", wohneinheit = unitName, propertyId = propertyId)
    private fun overview(receipts: List<Receipt>, groups: List<RentPropertyUnits> = listOf(group()), year: Int = 2026) =
        RentOverviewPresentation.year(context, groups, receipts, year)
    @Before fun clearPrefs() {
        listOf("rent_plan_prefs", "tenant_history_prefs", "google_drive_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    @Test fun actualIncludesOnlyAssignedRentalReceiptsAndNeverDepositsOrExpenses() {
        val deposits = listOf("Kaution", "Einzahlung Kaution", "Rückzahlung Kaution").mapIndexed { i, sub -> receipt(i + 10, 500.0, sub) }
        val expense = receipt(20, 900.0, "Wasser").copy(hauptkategorie = "Betriebs- / Nebenkosten")
        val rows = listOf(receipt(), receipt(2, 700.0, unitName = "")) + deposits + expense
        assertEquals(600.0, overview(rows).actual, .001)
        deposits.forEach { assertFalse(isRentalIncomeReceipt(it)) }
        assertEquals(1, overview(rows).unassigned.size)
    }
    @Test fun expectedUsesExistingProratedMonthlyLogicForLaterContractStart() {
        val midMonth = unit.copy(mietvertragsstart = "2026-03-16")
        val year = overview(emptyList(), listOf(group(midMonth)))
        assertEquals(0.0, year.months[0].first, .001)
        assertEquals(0.0, year.months[1].first, .001)
        assertEquals(600.0 * 16.0 / 31.0, year.months[2].first, .001)
        assertEquals(600.0 * (9.0 + 16.0 / 31.0), year.expected, .001)
    }
    @Test fun vacancyWithoutTenancyIsNeutralAndHasNoExpectedRent() {
        val row = overview(emptyList(), listOf(group(unit.copy(status = "Leerstand")))).rows.single()
        assertEquals(0.0, row.expected, .001); assertEquals(0.0, row.monthly, .001); assertEquals(0.0, row.missing, .001)
    }
    @Test fun explicitUtilitiesIncludeAdvancesAndBackPaymentsButNeverCostsOrEstimatedWarmRentSplit() {
        val values = listOf(receipt(1, 150.0, "Nebenkostenvorauszahlung"), receipt(2, 250.0, "Betriebskostennachzahlung"),
            receipt(3, 900.0, "Warmmiete"), receipt(4, 300.0, "Betriebskosten").copy(hauptkategorie = "Betriebs- / Nebenkosten"),
            receipt(5, 100.0, "Nebenkostenvorauszahlung", unitName = ""))
        assertEquals(400.0, overview(values).utilities, .001)
        assertEquals(1300.0, overview(values).actual, .001)
    }
    @Test fun annualArrearsNeverBecomeNegativeAndSurplusDoesNotHideAnotherUnitsDebt() {
        val b = unit.copy(name = "DG", label = "DG", unitId = "u-b")
        val year = overview(listOf(receipt(amount = 8000.0), receipt(2, 7000.0, unitName = "DG")), listOf(RentPropertyUnits(property, listOf(unit, b))))
        assertEquals(0.0, year.rows[0].missing, .001)
        assertEquals(200.0, year.rows[1].missing, .001)
        assertEquals(200.0, year.missing, .001)
    }
    @Test fun annualTotalsAndChartHaveTheSameExpectedAndActualSource() {
        PropertyUnitScopedData.setRentValues(context, property.propertyId, unit, 150.0, 50.0)
        val year = overview(listOf(receipt(1, 800.0), receipt(2, 400.0, date = "2026-02-03")))
        assertEquals(9600.0, year.expected, .001); assertEquals(1200.0, year.actual, .001)
        assertEquals(year.expected, year.months.sumOf { it.first }, .001)
        assertEquals(year.actual, year.months.sumOf { it.second }, .001)
        assertEquals(8400.0, year.missing, .001)
    }
    @Test fun sameUnitNameInTwoPropertiesNeverMixesPaymentsOrPlans() {
        val second = property.copy(id = 82, propertyId = "p-b", name = "Haus B")
        val b = unit.copy(kaltmiete = 900.0, unitId = "u-b")
        val payments = listOf(receipt(amount = 600.0), receipt(2, 900.0, propertyId = "p-b"))
        val year = overview(payments, listOf(group(), RentPropertyUnits(second, listOf(b))))
        assertEquals(600.0, year.rows[0].actual, .001); assertEquals(900.0, year.rows[1].actual, .001)
        assertEquals(7200.0, year.rows[0].expected, .001); assertEquals(10800.0, year.rows[1].expected, .001)
        assertNotEquals(year.rows[0].key, year.rows[1].key)
        assertEquals(600.0, RentTrackingLogic.month(context, "p-a", unit, payments, YearMonth.of(2026, 1)).actual, .001)
    }
    @Test fun scopedOverviewContainsOnlySelectedPropertyAndItsUnassignedPayments() {
        val payments = listOf(receipt(), receipt(2, 900.0, propertyId = "p-b"), receipt(3, 100.0, unitName = ""))
        val scoped = RentOverviewPresentation.scopedReceipts(listOf(group()), payments, true)
        assertEquals(listOf(1, 3), scoped.map { it.id })
        assertEquals(600.0, overview(scoped).actual, .001)
        assertEquals(100.0, overview(scoped).unassigned.sumOf { it.bruttobetrag }, .001)
        assertEquals(payments, RentOverviewPresentation.scopedReceipts(listOf(group()), payments, false))
    }
    @Test fun unassignedIncludesUnknownUnitAndUnknownPropertyEvenIfUnitNameExistsElsewhere() {
        val payments = listOf(receipt(1, 100.0, unitName = ""), receipt(2, 200.0, unitName = "Unbekannt"), receipt(3, 300.0, propertyId = "p-missing"))
        assertEquals(3, overview(payments).unassigned.size); assertEquals(0.0, overview(payments).actual, .001)
    }
    @Test fun availableYearsUseReceiptsContractsAndHistoryWithCurrentYearFallback() {
        val today = LocalDate.of(2026, 10, 4)
        assertEquals(listOf(2026), RentOverviewPresentation.years(context, emptyList(), emptyList(), today))
        TenantHistoryStore.save(context, "p-a", "u-a", unit.name, listOf(TenantPeriod(1, unit.name, "Alt", "2023-01-01", "2024-12-31", 500.0, 100.0, 0.0)))
        assertEquals(listOf(2026, 2025, 2024, 2023), RentOverviewPresentation.years(context, listOf(group()), listOf(receipt(date = "2025-12-01")), today))
        assertEquals(0.0, overview(listOf(receipt(date = "2025-12-01"))).actual, .001)
        assertEquals(600.0, overview(listOf(receipt(date = "2025-12-01")), year = 2025).actual, .001)
    }
    @Test fun tenantHistoryKeepsEndedPeriodsAndProratesChangesWithoutParallelCalculation() {
        TenantHistoryStore.save(context, "p-a", "u-a", unit.name, listOf(
            TenantPeriod(1, unit.name, "Alt", "2026-01-01", "2026-06-30", 500.0, 100.0, 0.0),
            TenantPeriod(2, unit.name, "Neu", "2026-07-01", "", 600.0, 150.0, 0.0)))
        val year = overview(emptyList())
        assertEquals(8100.0, year.expected, .001)
        assertEquals("Alt → Neu", year.rows.single().tenants)
        assertEquals(600.0, year.months[5].first, .001); assertEquals(750.0, year.months[6].first, .001)
    }
    @Test fun amountValidationAcceptsCommaPointAndBlankWithoutMultiplyingPointDecimals() {
        assertEquals(750.5, RentPlanInput.amount("750,50")!!, .001)
        assertEquals(750.5, RentPlanInput.amount("750.50")!!, .001)
        assertEquals(750.0, RentPlanInput.amount("750.0")!!, .001)
        assertEquals(1234.56, RentPlanInput.amount("1.234,56")!!, .001)
        assertEquals(0.0, RentPlanInput.amount("")!!, .001)
    }
    @Test fun negativeNonFiniteAndMalformedRentInputsAreRejectedWithGermanErrors() {
        listOf("-1", "NaN", "Infinity", "1e400", "1e39", "abc").forEach { value ->
            assertNull(value, RentPlanInput.amount(value)); assertNotNull(RentPlanInput.error(value, "0", "0", ""))
        }
        assertNotNull(RentPlanInput.error("1", "0", "0", "2026-02-30"))
        assertNotNull(RentPlanInput.error("1", "0", "0", "01.01.2026"))
        assertNull(RentPlanInput.error("1", "0", "0", "2026-02-28"))
        assertNull(RentPlanInput.error("1", "0", "0", ""))
        assertNotNull(RentPlanInput.error("1", "0", "0", "2026-06-30", LocalDate.of(2026, 6, 30)))
        assertNull(RentPlanInput.error("1", "0", "0", "2026-07-01", LocalDate.of(2026, 6, 30)))
    }
}
