package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankPhase2CLoanMatcherTest {
    private val loan = Loan(
        id = 7,
        bezeichnung = "Zwischenkredit OG",
        bank = "Musterbank",
        monatlicheRate = 445.0,
        propertyId = "property-1",
        notiz = "Vertrag ABC123",
        aktiv = true
    )

    private fun tx(
        id: String,
        date: String,
        amount: Double,
        purpose: String = "Darlehen ABC123",
        propertyId: String = "property-1",
        counterparty: String = "Musterbank"
    ) = BankTransaction(
        transactionId = id,
        accountId = "acc-1",
        bookingDate = date,
        amount = amount,
        counterparty = counterparty,
        purpose = purpose,
        propertyId = propertyId
    )

    @Test
    fun exactNegativeRateProducesHighTransparentSuggestion() {
        val suggestion = BankLoanMatcher.score(tx("t1", "2026-09-02", -445.0), loan)
        assertTrue(suggestion.score >= 85)
        assertEquals(BankLoanPaymentType.REGULAERE_RATE, suggestion.paymentType)
        assertEquals(445.0, suggestion.expectedAmount, 0.001)
        assertEquals(445.0, suggestion.actualAmount, 0.001)
        assertEquals(0.0, suggestion.difference, 0.001)
        assertEquals("2026-09", suggestion.period)
        assertTrue(suggestion.reasons.any { it.contains("Monatsrate stimmt exakt") })
    }

    @Test
    fun positiveMovementIsNeverNormalRate() {
        val suggestion = BankLoanMatcher.score(tx("t2", "2026-09-02", 445.0), loan)
        assertEquals(BankLoanPaymentType.KORREKTUR_ERSTATTUNG, suggestion.paymentType)
        assertEquals(BankLoanConflictState.POSITIVE_LOAN_MOVEMENT, suggestion.conflictState)
        assertEquals("NIEDRIG", suggestion.confidence)
    }

    @Test
    fun propertyMismatchLowersScore() {
        val right = BankLoanMatcher.score(tx("r", "2026-09-02", -445.0), loan)
        val wrong = BankLoanMatcher.score(tx("w", "2026-09-02", -445.0, propertyId = "property-2"), loan)
        assertTrue(right.score > wrong.score)
        assertTrue(wrong.reasons.any { it.contains("Immobilie weicht ab") })
    }

    @Test
    fun specialRepaymentTextIsNotRegularRate() {
        val suggestion = BankLoanMatcher.score(
            tx("s", "2026-09-02", -5_000.0, purpose = "Sondertilgung Darlehen ABC123"),
            loan
        )
        assertEquals(BankLoanPaymentType.SONDERTILGUNG, suggestion.paymentType)
        assertEquals(BankLoanConflictState.POSSIBLE_SPECIAL_REPAYMENT, suggestion.conflictState)
        assertEquals("NIEDRIG", suggestion.confidence)
    }

    @Test
    fun repeatedNewRateCreatesRateChangeHintButDoesNotChangeLoanMaster() {
        val history = listOf(
            tx("a", "2026-06-03", -472.0),
            tx("b", "2026-07-02", -472.0)
        )
        val suggestion = BankLoanMatcher.score(tx("c", "2026-08-04", -472.0), loan, history = history)
        assertEquals(BankLoanConflictState.RATE_CHANGED, suggestion.conflictState)
        assertTrue(suggestion.reasons.any { it.contains("Mögliche Ratenänderung") })
        assertEquals(445.0, loan.monatlicheRate, 0.001)
    }

    @Test
    fun equalCandidatesProduceMultipleLoanConflict() {
        val second = loan.copy(id = 8, bezeichnung = loan.bezeichnung)
        val ranked = BankLoanMatcher.suggestions(tx("x", "2026-09-02", -445.0), listOf(loan, second))
        assertTrue(ranked.size >= 2)
        assertEquals(BankLoanConflictState.MULTIPLE_LOANS, ranked[0].conflictState)
        assertEquals(BankLoanConflictState.MULTIPLE_LOANS, ranked[1].conflictState)
    }

    @Test
    fun inactiveLoanIsNotSuggested() {
        val inactive = loan.copy(aktiv = false)
        val ranked = BankLoanMatcher.suggestions(tx("i", "2026-09-02", -445.0), listOf(inactive))
        assertTrue(ranked.isEmpty())
    }
}
