package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankPhase2CPersistenceTest {
    private fun loan(rate: Double = 445.0, rest: Double = 100_000.0, interest: Double = 3.6) = Loan(
        id = 7,
        bezeichnung = "Objektdarlehen",
        bank = "Testbank",
        monatlicheRate = rate,
        restschuld = rest,
        sollzinsProzent = interest,
        propertyId = "property-a"
    )

    private fun tx(amount: Double = -445.0) = BankTransaction(
        transactionId = "tx-1",
        accountId = "account-1",
        bookingDate = "2026-09-05",
        amount = amount,
        counterparty = "Testbank",
        purpose = "Darlehen Objekt A",
        propertyId = "property-a"
    )

    @Test fun stableAssignmentIdIsIdempotent() {
        assertEquals(BankLoanAssignmentIdentity.id("tx-1"), BankLoanAssignmentIdentity.id("tx-1"))
        assertTrue(BankLoanAssignmentIdentity.id("tx-1").startsWith("loan-assignment-"))
    }

    @Test fun splitProposalOnlyWhenBasisIsReliable() {
        assertNull(BankLoanSplitProposer.propose(loan(rest = 0.0), 445.0, "2026-09"))
        assertNull(BankLoanSplitProposer.propose(loan(interest = 0.0), 445.0, "2026-09"))
        assertNull(BankLoanSplitProposer.propose(loan(), 445.0, "September"))
        val proposal = BankLoanSplitProposer.propose(loan(), 445.0, "2026-09")!!
        assertEquals(300.0, proposal.interest, 0.01)
        assertEquals(145.0, proposal.principal, 0.01)
        assertEquals(445.0, proposal.interest + proposal.principal, 0.01)
        assertTrue(proposal.basis.contains("Sollzins"))
    }

    @Test fun priorityKeepsRentBeforeLoanAndLoanBeforeRecurring() {
        val transaction = tx()
        val suggestion = BankLoanMatcher.score(transaction, loan())
        val recurring = RecurringPaymentPattern(
            patternId="rec-1", direction=RecurringDirection.EXPENSE,
            normalizedCounterparty="testbank", purposeFingerprint="darlehen objekt",
            typicalAmount=445.0, amountTolerance=5.0, cadence=RecurringCadence.MONTHLY,
            typicalDay=5, accountId="account-1", propertyId="property-a", occurrenceCount=4,
            confidence=90, lastOccurrence="2026-09-05", nextExpectedWindow=null, reasons=emptyList()
        )
        assertEquals(BankPhase2CPriority.Classification.RENT,
            BankPhase2CPriority.classify(transaction.copy(amount=445.0), true, listOf(suggestion), listOf(recurring)))
        assertEquals(BankPhase2CPriority.Classification.LOAN,
            BankPhase2CPriority.classify(transaction, false, listOf(suggestion), listOf(recurring)))
        assertEquals(BankPhase2CPriority.Classification.RECURRING,
            BankPhase2CPriority.classify(transaction, false, emptyList(), listOf(recurring)))
    }

    @Test fun recurringEntityPreservesExpectedWindowAndScope() {
        val pattern = RecurringPaymentPattern(
            patternId="rec-1", direction=RecurringDirection.EXPENSE,
            normalizedCounterparty="iban:de123", purposeFingerprint="versicherung",
            typicalAmount=100.0, amountTolerance=3.0, cadence=RecurringCadence.MONTHLY,
            typicalDay=10, accountId="account-1", propertyId="property-a", occurrenceCount=5,
            confidence=88, lastOccurrence="2026-09-10",
            nextExpectedWindow=ExpectedPaymentWindow("2026-10-05","2026-10-15","2026-10-10"),
            reasons=listOf("5 ähnliche Vorkommen")
        )
        val entity = pattern.toEntity("2026-09-08T00:00:00Z")
        assertEquals("2026-10-05", entity.nextExpectedStart)
        assertEquals("2026-10-15", entity.nextExpectedEnd)
        assertEquals("account-1", entity.accountId)
        assertEquals("property-a", entity.propertyId)
    }
}
