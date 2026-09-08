package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankPhase2CLoanExtendedAcceptanceTest {
    private fun loan(
        id: Int = 1,
        bank: String = "Hausbank Alpha",
        rate: Double = 445.0,
        property: String = "property-a",
        note: String = "Vertrag REF-A",
        active: Boolean = true
    ) = Loan(
        id = id, bezeichnung = "Darlehen $id", bank = bank, monatlicheRate = rate,
        propertyId = property, notiz = note, aktiv = active
    )

    private fun tx(
        id: String = "tx",
        amount: Double = -445.0,
        date: String = "2026-09-05",
        counterparty: String = "Hausbank Alpha",
        purpose: String = "Darlehen REF-A",
        property: String = "property-a",
        account: String = "acc-a"
    ) = BankTransaction(
        transactionId=id, accountId=account, bookingDate=date, amount=amount,
        counterparty=counterparty, purpose=purpose, propertyId=property
    )

    @Test fun smallDeviationRemainsPlausibleAndVisible() {
        val s = BankLoanMatcher.score(tx(amount=-447.0), loan())
        assertTrue(s.score >= BankLoanThresholds.MIN_SUGGESTION_SCORE)
        assertEquals(2.0, s.difference, 0.001)
        assertTrue(s.reasons.any { it.contains("nahezu gleich") })
    }

    @Test fun largeDeviationCreatesConflictAndNeverChangesMasterRate() {
        val l = loan()
        val s = BankLoanMatcher.score(tx(amount=-900.0), l)
        assertTrue(s.conflictState == BankLoanConflictState.LARGE_AMOUNT_DIFFERENCE || s.conflictState == BankLoanConflictState.POSSIBLE_SPECIAL_REPAYMENT)
        assertEquals(445.0, l.monatlicheRate, 0.001)
    }

    @Test fun lenderContextSeparatesRightAndWrongBank() {
        val right = BankLoanMatcher.score(tx(), loan(bank="Hausbank Alpha"), BankAccount("acc-a","Hauskonto",bankName="Hausbank Alpha"))
        val wrong = BankLoanMatcher.score(tx(), loan(bank="Andere Bank"), BankAccount("acc-a","Hauskonto",bankName="Hausbank Alpha"))
        assertTrue(right.score > wrong.score)
        assertTrue(wrong.reasons.any { it.contains("Bankname weicht ab") })
    }

    @Test fun contractReferenceSeparatesTwoLoansAtSameBank() {
        val a = loan(id=1, note="Vertrag REF-A")
        val b = loan(id=2, note="Vertrag REF-B")
        val ranked = BankLoanMatcher.suggestions(tx(purpose="Rate REF-A Hausbank Alpha"), listOf(a,b))
        assertTrue(ranked.isNotEmpty())
        assertEquals(1, ranked.first().loanId)
    }

    @Test fun smallBookingDayShiftIsEvidenceButLargeShiftReducesScore() {
        val history = listOf(
            tx("h1", date="2026-06-05"), tx("h2", date="2026-07-05"), tx("h3", date="2026-08-05")
        )
        val close = BankLoanMatcher.score(tx("c", date="2026-09-08"), loan(), history=history)
        val far = BankLoanMatcher.score(tx("f", date="2026-09-20"), loan(), history=history)
        assertTrue(close.reasons.any { it.contains("Abbuchungstag passt") })
        assertTrue(far.reasons.any { it.contains("weicht deutlich ab") })
        assertTrue(close.score >= far.score)
    }

    @Test fun oneNewRateOutlierDoesNotClaimRateChange() {
        val history = listOf(tx("old1", date="2026-07-05"), tx("new1", date="2026-08-05", amount=-472.0))
        val s = BankLoanMatcher.score(tx("current", date="2026-09-05", amount=-472.0), loan(), history=history)
        assertTrue(s.conflictState != BankLoanConflictState.RATE_CHANGED)
    }

    @Test fun threeConsistentNewRatesCreateRateChangeHint() {
        val history = listOf(tx("n1", date="2026-07-05", amount=-472.0), tx("n2", date="2026-08-05", amount=-472.0))
        val s = BankLoanMatcher.score(tx("n3", date="2026-09-05", amount=-472.0), loan(), history=history)
        assertEquals(BankLoanConflictState.RATE_CHANGED, s.conflictState)
    }

    @Test fun activePhase2ARuleIsOnlyBoundedBonusAndDisabledRuleAddsNothing() {
        val transaction = tx()
        val active = BankLearningRule(
            ruleId="rule-1", displayName="Hausbank", enabled=true, state=BankRuleState.ACTIVE,
            transactionDirection=BankRuleDirection.EXPENSE, counterpartyPattern="Hausbank Alpha",
            purposeTerms="darlehen", amountMin=440.0, amountMax=450.0,
            propertyId="property-a", confidence=90
        )
        val disabled = active.copy(ruleId="rule-2", enabled=false)
        val base = BankLoanMatcher.score(transaction, loan())
        val boosted = BankLoanMatcher.score(transaction, loan(), rules=listOf(active))
        val ignored = BankLoanMatcher.score(transaction, loan(), rules=listOf(disabled))
        assertTrue(boosted.ruleBonus in 1..BankLoanThresholds.MAX_RULE_BONUS)
        assertTrue(boosted.score >= base.score)
        assertEquals(0, ignored.ruleBonus)
        assertEquals(base.score, ignored.score)
    }

    @Test fun confirmedHistoryAddsOnlyTransparentBoundedEvidence() {
        val history = listOf(tx("confirmed", date="2026-08-05"), tx("raw", date="2026-07-05"))
        val base = BankLoanMatcher.score(tx("current"), loan(), history=history)
        val confirmed = BankLoanMatcher.score(tx("current"), loan(), history=history, confirmedLoanTransactionIds=setOf("confirmed"))
        assertTrue(confirmed.score >= base.score)
        assertTrue(confirmed.reasons.any { it.contains("Bestätigte Darlehenshistorie") })
    }
}
