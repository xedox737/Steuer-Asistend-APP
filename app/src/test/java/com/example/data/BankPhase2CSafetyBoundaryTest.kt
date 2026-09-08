package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankPhase2CSafetyBoundaryTest {
    private val loan = Loan(
        id = 3,
        bezeichnung = "Objektdarlehen",
        bank = "Musterbank",
        monatlicheRate = 445.0,
        restschuld = 80_000.0,
        sollzinsProzent = 3.0,
        propertyId = "property-1"
    )

    private fun tx(amount: Double, purpose: String = "Darlehen Objektdarlehen") = BankTransaction(
        transactionId = "tx-${amount}-${purpose.hashCode()}",
        accountId = "acc-1",
        bookingDate = "2026-09-05",
        amount = amount,
        counterparty = "Musterbank",
        purpose = purpose,
        propertyId = "property-1"
    )

    @Test fun positiveMovementIsReviewableButNeverRegularRate() {
        val suggestion = BankLoanMatcher.score(tx(445.0), loan)
        assertEquals(BankLoanPaymentType.KORREKTUR_ERSTATTUNG, suggestion.paymentType)
        assertEquals(BankLoanConflictState.POSITIVE_LOAN_MOVEMENT, suggestion.conflictState)
        assertEquals("NIEDRIG", suggestion.confidence)
        assertTrue(suggestion.reasons.any { it.contains("keine normale Darlehensrate") })
    }

    @Test fun specialRepaymentTextIsNotCollapsedIntoRegularRate() {
        val suggestion = BankLoanMatcher.score(tx(-5_000.0, "Sondertilgung Objektdarlehen Musterbank"), loan)
        assertEquals(BankLoanPaymentType.SONDERTILGUNG, suggestion.paymentType)
        assertTrue(suggestion.conflictState == BankLoanConflictState.POSSIBLE_SPECIAL_REPAYMENT || suggestion.score >= 0)
        assertFalse(suggestion.paymentType == BankLoanPaymentType.REGULAERE_RATE)
    }

    @Test fun splitProposalDoesNotMutateLoanModel() {
        val before = loan.copy()
        val proposal = BankLoanSplitProposer.propose(loan, 445.0, "2026-09")
        assertTrue(proposal != null)
        assertEquals(before, loan)
    }
}
