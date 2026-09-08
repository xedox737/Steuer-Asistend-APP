package com.example.ui

import com.example.data.BankLearningRule
import com.example.data.BankRuleDirection
import com.example.data.BankRuleSource
import com.example.data.BankRuleState
import com.example.data.BankRuleType
import com.example.data.BankTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class BankRentMatchingAcceptanceTest {
    private fun tx(
        id: String = "tx1",
        amount: Double = 950.0,
        date: String = "2026-09-02",
        payer: String = "Max Mustermann",
        purpose: String = "Miete September OG links",
        propertyId: String = "property-1",
        unitId: String = "unit-og-links",
        accountId: String = "acc-rent"
    ) = BankTransaction(
        transactionId = id,
        accountId = accountId,
        bookingDate = date,
        amount = amount,
        counterparty = payer,
        purpose = purpose,
        propertyId = propertyId,
        unitId = unitId
    )

    private fun candidate(
        month: YearMonth = YearMonth.of(2026, 9),
        expected: Double = 950.0,
        confirmed: Double = 0.0,
        propertyId: String = "property-1",
        unitId: String = "unit-og-links",
        tenant: String = "Max Mustermann",
        accountId: String = "acc-rent"
    ) = RentCandidateContext(
        propertyId = propertyId,
        propertyLabel = "Sulzerstraße 32",
        unitId = unitId,
        unitName = "OG links",
        tenantReference = "tenant-max",
        tenantName = tenant,
        tenantStart = "2026-01-01",
        tenantEnd = "",
        rentMonth = month,
        expectedAmount = expected,
        alreadyConfirmedAmount = confirmed,
        accountId = accountId
    )

    @Test fun exactRentProducesHighTransparentSuggestion() {
        val result = BankRentMatcher.match(tx(), listOf(candidate())).single()
        assertEquals(RentMatchConfidence.HOCH, result.confidence)
        assertEquals(RentPaymentType.MIETE, result.paymentType)
        assertEquals(0.0, result.remainingAmount, 0.01)
        assertTrue(result.reasons.any { "Betrag" in it })
        assertTrue(result.reasons.any { "Mietmonat" in it })
        assertTrue(result.reasons.any { "Mieter" in it })
    }

    @Test fun monthFormatsAreDetected() {
        val cases = listOf(
            "Miete September" to YearMonth.of(2026, 9),
            "Septembermiete" to YearMonth.of(2026, 9),
            "Miete Sep 2026" to YearMonth.of(2026, 9),
            "Miete 09/2026" to YearMonth.of(2026, 9),
            "Miete 09.2026" to YearMonth.of(2026, 9),
            "Miete 2026-09" to YearMonth.of(2026, 9),
            "Miete lfd. Monat" to YearMonth.of(2026, 9)
        )
        cases.forEach { (purpose, expected) ->
            val detected = RentMonthParser.detect(purpose, "2026-09-02")
            assertEquals("$purpose failed", expected, detected.month)
            assertFalse(detected.conflict)
        }
    }

    @Test fun explicitAdvanceAndLateMonthBeatBookingMonth() {
        assertEquals(YearMonth.of(2026, 9), RentMonthParser.detect("Miete September", "2026-08-28").month)
        assertEquals(YearMonth.of(2026, 9), RentMonthParser.detect("Miete September", "2026-10-03").month)
    }

    @Test fun noMonthUsesBookingMonthWeaklyAndConflictIsVisible() {
        val weak = RentMonthParser.detect("Mietzahlung", "2026-09-02")
        assertEquals(YearMonth.of(2026, 9), weak.month)
        assertFalse(weak.explicit)
        val conflict = RentMonthParser.detect("Miete September 09/2026 Oktober 2026", "2026-09-02")
        assertTrue(conflict.conflict)
    }

    @Test fun firstPartialUsesFullRentAndSecondTargetsRemaining() {
        val first = BankRentMatcher.match(tx(amount = 500.0), listOf(candidate())).single()
        assertEquals(450.0, first.remainingAmount, 0.01)
        val second = BankRentMatcher.match(tx(id = "tx2", amount = 450.0), listOf(candidate(confirmed = 500.0))).single()
        assertEquals(0.0, second.remainingAmount, 0.01)
        assertTrue(second.reasons.any { "Restbetrag" in it })
    }

    @Test fun underpaymentIsNotCompleteAndOverpaymentConflicts() {
        val under = BankRentMatcher.match(tx(amount = 900.0), listOf(candidate())).single()
        assertEquals(50.0, under.remainingAmount, 0.01)
        val over = BankRentMatcher.match(tx(amount = 1000.0), listOf(candidate())).single()
        assertEquals(RentConflictState.OVERPAYMENT, over.conflictState)
        assertEquals(50.0, over.difference, 0.01)
    }

    @Test fun alreadyPaidMonthIsConflictAndCannotDoubleComplete() {
        val result = BankRentMatcher.match(tx(id = "tx2"), listOf(candidate(confirmed = 950.0))).single()
        assertEquals(RentConflictState.ALREADY_PAID, result.conflictState)
        assertEquals(RentMatchConfidence.NIEDRIG, result.confidence)
    }

    @Test fun depositAndUtilitiesAreNotNormalRent() {
        val deposit = BankRentMatcher.match(tx(purpose = "Mietkaution OG links"), listOf(candidate())).single()
        assertEquals(RentPaymentType.KAUTION, deposit.paymentType)
        assertEquals(RentConflictState.SPECIAL_PAYMENT, deposit.conflictState)
        val utilities = BankRentMatcher.match(tx(purpose = "Nebenkosten Nachzahlung September OG links"), listOf(candidate())).single()
        assertEquals(RentPaymentType.NEBENKOSTEN, utilities.paymentType)
        assertEquals(RentConflictState.SPECIAL_PAYMENT, utilities.conflictState)
    }

    @Test fun differentPayerCanRemainPlausibleButNameAloneDoesNotDecide() {
        val result = BankRentMatcher.match(tx(payer = "Anna Mustermann"), listOf(candidate())).single()
        assertTrue(result.score >= BankRentThresholds.MIN_PLAUSIBLE)
        assertTrue(result.reasons.any { "Mieter/Zahler" in it || "Abweichender Zahler" in it })
    }

    @Test fun propertyAndUnitIsolationRejectWrongScope() {
        val candidates = listOf(
            candidate(propertyId = "property-1", unitId = "unit-og-links"),
            candidate(propertyId = "property-2", unitId = "unit-og-links"),
            candidate(propertyId = "property-1", unitId = "unit-eg-links")
        )
        val result = BankRentMatcher.match(tx(), candidates)
        assertEquals(1, result.size)
        assertEquals("property-1", result.single().propertyId)
        assertEquals("unit-og-links", result.single().unitId)
    }

    @Test fun equalPlausibleCandidatesBecomeConflictNotRandomDecision() {
        val noScope = tx(propertyId = "", unitId = "", payer = "", purpose = "Miete September", accountId = "")
        val candidates = listOf(
            candidate(propertyId = "p1", unitId = "u1", tenant = "A", accountId = ""),
            candidate(propertyId = "p2", unitId = "u2", tenant = "B", accountId = "")
        )
        val result = BankRentMatcher.match(noScope, candidates)
        assertTrue(result.size >= 2)
        assertEquals(RentConflictState.MULTIPLE_CANDIDATES, result[0].conflictState)
        assertEquals(RentConflictState.MULTIPLE_CANDIDATES, result[1].conflictState)
    }

    @Test fun largeAmountErrorLowersScore() {
        val exact = BankRentMatcher.match(tx(), listOf(candidate())).single()
        val wrong = BankRentMatcher.match(tx(amount = 2500.0), listOf(candidate())).single()
        assertTrue(wrong.score < exact.score)
        assertEquals(RentConflictState.LARGE_AMOUNT_DIFFERENCE, wrong.conflictState)
    }

    @Test fun expenseNeverCreatesRentSuggestion() {
        assertTrue(BankRentMatcher.match(tx(amount = -950.0), listOf(candidate())).isEmpty())
    }

    @Test fun activeScopedPhase2aRuleCanOnlyAddBoundedBonus() {
        val base = BankRentMatcher.match(tx(propertyId = "", unitId = ""), listOf(candidate()), emptyList()).single()
        val rule = BankLearningRule(
            ruleId = "rent-rule",
            displayName = "Miete OG links",
            enabled = true,
            state = BankRuleState.ACTIVE,
            ruleType = BankRuleType.COMBINED,
            transactionDirection = BankRuleDirection.INCOME,
            counterpartyPattern = "Max Mustermann",
            purposeTerms = "miete|september",
            accountId = "acc-rent",
            propertyId = "property-1",
            unitId = "unit-og-links",
            confidence = 90,
            source = BankRuleSource.USER_CREATED
        )
        val enhanced = BankRentMatcher.match(tx(propertyId = "", unitId = ""), listOf(candidate()), listOf(rule)).single()
        assertTrue(enhanced.score >= base.score)
        assertTrue(enhanced.score - base.score <= BankRentThresholds.MAX_RULE_BONUS)
        assertTrue(enhanced.reasons.any { "Regel" in it })
    }

    @Test fun disabledOrWrongScopedRuleDoesNotHelp() {
        val base = BankRentMatcher.match(tx(propertyId = "", unitId = ""), listOf(candidate()), emptyList()).single()
        val wrong = BankLearningRule(
            ruleId = "wrong",
            displayName = "Wrong",
            enabled = true,
            state = BankRuleState.ACTIVE,
            transactionDirection = BankRuleDirection.INCOME,
            counterpartyPattern = "Max Mustermann",
            purposeTerms = "miete",
            propertyId = "other-property",
            unitId = "other-unit",
            confidence = 90
        )
        val result = BankRentMatcher.match(tx(propertyId = "", unitId = ""), listOf(candidate()), listOf(wrong)).single()
        assertEquals(base.score, result.score)
        assertFalse(result.reasons.any { "Wrong" in it })
    }
}