package com.example.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankRecurringPaymentDetectorTest {
    private fun tx(id: String, date: String, amount: Double = -49.90, purpose: String = "Versicherung Vertrag 123456 Monat 09 2026") =
        BankTransaction(
            transactionId = id,
            accountId = "acc-1",
            bookingDate = date,
            amount = amount,
            counterparty = "Versicherung AG",
            counterpartyIban = "DE00123456789012345678",
            purpose = purpose,
            propertyId = "property-1"
        )

    @Test
    fun oneOccurrenceDoesNotCreatePattern() {
        assertTrue(BankRecurringPaymentDetector.detect(listOf(tx("1", "2026-01-05"))).patterns.isEmpty())
    }

    @Test
    fun twoOccurrencesAreWeakOnly() {
        val pattern = BankRecurringPaymentDetector.detect(listOf(tx("1", "2026-01-05"), tx("2", "2026-02-05"))).patterns.single()
        assertTrue(pattern.confidence < 50)
        assertTrue(pattern.reasons.any { it.contains("schwache Evidenz", ignoreCase = true) })
    }

    @Test
    fun monthlyPatternNeedsMinimumEvidenceForStrongSuggestion() {
        val analysis = BankRecurringPaymentDetector.detect(
            listOf(tx("1", "2026-01-05"), tx("2", "2026-02-04"), tx("3", "2026-03-06"))
        )
        val pattern = analysis.patterns.single()
        assertEquals(RecurringCadence.MONTHLY, pattern.cadence)
        assertTrue(pattern.occurrenceCount >= BankRecurringThresholds.MIN_OCCURRENCES_FOR_PATTERN)
        assertTrue(pattern.confidence >= 50)
        assertEquals("2026-04", pattern.nextExpectedWindow!!.expectedDate.take(7))
    }

    @Test
    fun quarterlyAndYearlyAreRecognized() {
        val quarterly = BankRecurringPaymentDetector.detect(
            listOf(tx("q1", "2025-01-10"), tx("q2", "2025-04-10"), tx("q3", "2025-07-11"))
        ).patterns.single()
        assertEquals(RecurringCadence.QUARTERLY, quarterly.cadence)

        val yearly = BankRecurringPaymentDetector.detect(
            listOf(tx("y1", "2023-06-15"), tx("y2", "2024-06-14"), tx("y3", "2025-06-16"))
        ).patterns.single()
        assertEquals(RecurringCadence.YEARLY, yearly.cadence)
    }

    @Test
    fun irregularSeriesIsNotClassifiedAsSafeCadence() {
        val pattern = BankRecurringPaymentDetector.detect(
            listOf(tx("1", "2026-01-05"), tx("2", "2026-02-05"), tx("3", "2026-06-05"), tx("4", "2026-07-05"))
        ).patterns.single()
        assertEquals(RecurringCadence.IRREGULAR, pattern.cadence)
        assertTrue(pattern.confidence < 80)
    }

    @Test
    fun variableInvoiceNumbersAreIgnoredInPurposeFingerprint() {
        val a = BankRecurringPaymentDetector.purposeFingerprint("Versicherung Rechnung 123456 September 2026")
        val b = BankRecurringPaymentDetector.purposeFingerprint("Versicherung Rechnung 987654 Oktober 2026")
        assertEquals(a, b)
        assertTrue(a.contains("versicherung"))
    }

    @Test
    fun oneOutlierDoesNotDestroyMonthlyPattern() {
        val pattern = BankRecurringPaymentDetector.detect(
            listOf(
                tx("1", "2026-01-05", -49.90),
                tx("2", "2026-02-05", -49.90),
                tx("3", "2026-03-05", -79.90),
                tx("4", "2026-04-05", -49.90)
            )
        ).patterns.single()
        assertEquals(RecurringCadence.MONTHLY, pattern.cadence)
        assertTrue(pattern.outlierCount >= 1)
        assertEquals(49.90, pattern.typicalAmount, 0.01)
    }

    @Test
    fun possibleDuplicateRecurringPaymentIsFlaggedWithoutDeletingAnything() {
        val items = listOf(
            tx("1", "2026-01-05"),
            tx("2", "2026-02-05"),
            tx("3", "2026-03-05"),
            tx("4", "2026-03-07")
        )
        val analysis = BankRecurringPaymentDetector.detect(items)
        assertTrue("3" in analysis.duplicateTransactionIds)
        assertTrue("4" in analysis.duplicateTransactionIds)
        assertEquals(4, items.size)
    }

    @Test
    fun overdueExpectedWindowIsMarkedMissingOnlyAsExpectation() {
        val analysis = BankRecurringPaymentDetector.detect(
            listOf(tx("1", "2026-01-05"), tx("2", "2026-02-05"), tx("3", "2026-03-05")),
            today = LocalDate.parse("2026-04-20")
        )
        val pattern = analysis.patterns.single()
        assertTrue(pattern.patternId in analysis.missingExpectedPatternIds)
        assertFalse(pattern.nextExpectedWindow == null)
    }
}
