package com.example.ui

import com.example.data.Receipt
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LedgerPresentationTest {
    private fun receipt(id: Int, income: Boolean = false, date: String = "2026-05-03", amount: Double = 120.0,
        property: String = "a", category: String = "Instandhaltung") = Receipt(id = id,
        aussteller = if (income) "Mieter Müller" else "Hornbach", datum = date, uhrzeit = "", bruttobetrag = amount,
        hauptkategorie = if (income) "Miete, Nebenkosten & Kaution" else "Sanierung", unterkategorie = category,
        kontoNr = "4830", beschreibung = "Fenster reparieren", displayId = "R-2026-$id", propertyId = property)

    @Test fun totalsPreserveActualAmountsAndExistingIncomeClassification() {
        val totals = LedgerPresentation.totals(listOf(receipt(1, true, amount = 1200.0),
            receipt(2, amount = 256.4), receipt(3, true, amount = 100.0).copy(hauptkategorie = "Sonstige Einnahmen"),
            receipt(4, amount = -20.0)))
        assertEquals(1300.0, totals.income, 0.001)
        assertEquals(236.4, totals.expense, 0.001)
        assertEquals(1063.6, totals.result, 0.001)
        assertEquals(4, totals.count)
    }
    @Test fun yearsUseValidReceiptDatesAndFallbackToCurrentYear() {
        assertEquals(listOf(2026, 2025), LedgerPresentation.years(listOf(receipt(1), receipt(2, date = "2025-12-31"),
            receipt(3, date = "x"), receipt(4, date = "2027-99-99"))))
        assertEquals(listOf(2030), LedgerPresentation.years(emptyList(), LocalDate.of(2030, 1, 1)))
    }
    @Test fun yearFilterDoesNotMixYearsOrCrashOnIncompleteDates() {
        assertEquals(listOf(1), LedgerPresentation.filter(listOf(receipt(1), receipt(2, date = "2025-05-03"),
            receipt(3, date = "2026")), LedgerFilters(2026)).map { it.id })
    }
    @Test fun threeKindsUseExistingClassification() {
        val receipts = listOf(receipt(1, true), receipt(2))
        assertEquals(2, LedgerPresentation.filter(receipts, LedgerFilters(2026)).size)
        assertEquals(listOf(1), LedgerPresentation.filter(receipts, LedgerFilters(2026, kind = LedgerKind.INCOME)).map { it.id })
        assertEquals(listOf(2), LedgerPresentation.filter(receipts, LedgerFilters(2026, kind = LedgerKind.EXPENSE)).map { it.id })
    }
    @Test fun propertyFilterUsesStableIdentityIncludingUnassigned() {
        val receipts = listOf(receipt(1, property = "a"), receipt(2, property = "b"), receipt(3, property = ""))
        assertEquals(listOf(2), LedgerPresentation.filter(receipts, LedgerFilters(2026, propertyId = "b")).map { it.id })
        assertEquals(listOf(3), LedgerPresentation.filter(receipts, LedgerFilters(2026, propertyId = "")).map { it.id })
    }
    @Test fun unresolvedLegacyAndMissingPropertyIdsAreVisibleAsUnassignedWithoutChangingIds() {
        val receipts = listOf(receipt(1, property = "a"), receipt(2, property = "missing"),
            receipt(3, property = com.example.data.StableDocumentIdentity.LEGACY_PROPERTY_ID), receipt(4, property = ""))
        assertEquals(listOf(4, 3, 2), LedgerPresentation.filter(receipts, LedgerFilters(2026, propertyId = ""),
            knownPropertyIds = setOf("a")).map { it.id })
        assertEquals("missing", receipts.first { it.id == 2 }.propertyId)
        assertEquals(listOf(3), LedgerPresentation.filter(receipts,
            LedgerFilters(2026, propertyId = com.example.data.StableDocumentIdentity.LEGACY_PROPERTY_ID),
            knownPropertyIds = setOf("a", com.example.data.StableDocumentIdentity.LEGACY_PROPERTY_ID)).map { it.id })
    }
    @Test fun searchCoversIssuerDescriptionCategoryAndDisplayNumberLocally() {
        val receipts = listOf(receipt(7))
        listOf("hornBACH", "Fenster", "Instandhaltung", "R-2026-7").forEach { query ->
            assertEquals(query, 1, LedgerPresentation.filter(receipts, LedgerFilters(2026, query = query)).size)
        }
        assertTrue(LedgerPresentation.filter(receipts, LedgerFilters(2026, query = "unbekannt")).isEmpty())
    }
    @Test fun categoryAndPeriodComposeWithOtherFiltersIncludingJanuaryBoundary() {
        val receipts = listOf(receipt(1, date = "2025-12-31"), receipt(2, date = "2026-01-03"),
            receipt(3, date = "2026-01-03", category = "Versicherungen"))
        val today = LocalDate.of(2026, 1, 10)
        assertEquals(listOf(1), LedgerPresentation.filter(receipts,
            LedgerFilters(2025, period = LedgerPeriod.LAST_MONTH), today).map { it.id })
        assertTrue(LedgerPresentation.filter(receipts, LedgerFilters(2026, period = LedgerPeriod.LAST_MONTH), today).isEmpty())
        assertEquals(listOf(2), LedgerPresentation.filter(receipts,
            LedgerFilters(2026, category = "Instandhaltung", period = LedgerPeriod.CURRENT_MONTH), today).map { it.id })
    }
    @Test fun signedFormattingPreservesCreditsAndGermanCurrency() {
        assertTrue(LedgerPresentation.signedMoney(receipt(1, true)).startsWith("+120,00"))
        assertTrue(LedgerPresentation.signedMoney(receipt(2)).startsWith("−120,00"))
        assertTrue(LedgerPresentation.signedMoney(receipt(3, amount = -10.0)).startsWith("+10,00"))
        assertTrue(LedgerPresentation.money(1234.56).contains("1.234,56"))
    }
    @Test fun priorYearComparisonNeverInventsPercentages() {
        assertNull(LedgerPresentation.comparison(100.0, 0.0, 2025))
        assertEquals("+12 % zu 2025", LedgerPresentation.comparison(112.0, 100.0, 2025))
    }
}
