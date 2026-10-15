package com.example.data

import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class Phase9DForecastTest {
    private val property = PropertyMetadata(baujahr = 1954, gebaeudewert = 888_000.0,
        gesamtKaufpreis = 888_000.0, uebergangNutzenLasten = "2020-01-01")
    private fun summary(metadata: PropertyMetadata = property) = TaxPropertyCalculator.calculate(metadata, emptyList())

    @Test fun firstUnconfirmedJulyIncludesTheRestOf2026AndThenFullYears() {
        val rows = TaxPropertyCalculator.forecast(summary(), YearMonth.of(2026, 7), 100_000.0)
        assertEquals(2026, rows.first().year)
        assertEquals(6, rows.first().months)
        assertEquals(8_880.0, rows.first().amount, .001)
        assertEquals(91_120.0, rows.first().remainingBookValue, .001)
        assertEquals(17_760.0, rows[1].amount, .001)
        assertEquals(100_000.0, rows.sumOf { it.amount }, .001)
        assertTrue(rows.all { it.remainingBookValue >= 0.0 })
    }

    @Test fun firstUnconfirmedJanuaryPreservesTheYearEndForecast() {
        val rows = TaxPropertyCalculator.forecast(summary(), YearMonth.of(2026, 1), 100_000.0)
        assertEquals(2026, rows.first().year)
        assertEquals(12, rows.first().months)
        assertEquals(17_760.0, rows.first().amount, .001)
    }

    @Test fun remainingBookValueLimitsTheRestYearAndCannotBecomeNegative() {
        val rows = TaxPropertyCalculator.forecast(summary(), YearMonth.of(2026, 7), 1_000.0)
        assertEquals(1_000.0, rows.first().amount, 0.0)
        assertEquals(0.0, rows.first().remainingBookValue, 0.0)
        assertTrue(rows.drop(1).all { it.amount == 0.0 && it.remainingBookValue == 0.0 })
    }

    @Test fun acquisitionMonthRemainsInclusiveAndCannotBeBypassedByAnEarlierCutoff() {
        val tax = summary(property.copy(uebergangNutzenLasten = "2026-04-15"))
        val rows = TaxPropertyCalculator.forecast(tax)
        assertEquals(9, rows.first().months)
        assertEquals(tax.firstYearAfa, rows.first().amount, .001)
        assertEquals(rows, TaxPropertyCalculator.forecast(tax, YearMonth.of(2025, 12)))
    }

    @Test fun confirmedShorterDurationRemainsTheSourceForPartialAndFullYears() {
        val tax = summary(property.copy(afaShorterYears = 25, afaShorterConfirmed = true,
            afaShorterStartDate = "2020-01-01", afaShorterReason = "Gutachten", afaShorterDocumentId = "evidence"))
        assertTrue(tax.shorterMethodActive)
        val rows = TaxPropertyCalculator.forecast(tax, YearMonth.of(2026, 7), 100_000.0)
        assertEquals(17_760.0, rows.first().amount, .001)
        assertEquals(35_520.0, rows[1].amount, .001)
    }

    @Test fun missingBeginningCannotInventAForecastEvenWithConfirmedBookValue() {
        val tax = summary(property.copy(uebergangNutzenLasten = "", notariellesKaufdatum = ""))
        assertTrue(TaxPropertyCalculator.forecast(tax, YearMonth.of(2026, 7), 100_000.0).isEmpty())
    }

    @Test fun invalidBasisAndInvalidBookValueAreBlockedWithoutRepair() {
        val invalid = summary(property.copy(gebaeudewert = -888_000.0))
        assertTrue(TaxPropertyCalculator.forecast(invalid).isEmpty())
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertTrue(TaxPropertyCalculator.forecast(summary(), YearMonth.of(2026, 7), it).isEmpty())
        }
    }
}
