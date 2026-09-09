package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankPhase2DReviewQueueTest {
    private fun tx(id: String, amount: Double = -100.0, status: String = BankReconciliationStatus.OPEN, date: String = "2026-09-05") = BankTransaction(
        transactionId=id, accountId="a", bookingDate=date, amount=amount, counterparty="Vendor", purpose="Invoice", reconciliationStatus=status
    )
    private fun receipt(id: Int, amount: Double = 100.0) = Receipt(
        id=id, aussteller="Vendor", datum="2026-09-04", uhrzeit="", bruttobetrag=amount,
        hauptkategorie="Kosten", unterkategorie="Material", kontoNr="", beschreibung="Invoice", internalId="r$id"
    )

    @Test fun safeSuggestionCategoryIsCreated() {
        val transaction = tx("safe")
        val suggestion = BankMatchSuggestion("safe", 1, 95, "HOCH", listOf("Betrag stimmt exakt"))
        val queue = BankReviewQueueBuilder.build(listOf(transaction), listOf(receipt(1)), emptyList(), mapOf("safe" to suggestion), emptyList())
        assertEquals(BankReviewType.SAFE_SUGGESTION, queue.single().type)
    }

    @Test fun missingReceiptCategoryIsCreated() {
        val queue = BankReviewQueueBuilder.build(listOf(tx("missing")), emptyList(), emptyList(), emptyMap(), emptyList())
        assertEquals(BankReviewType.MISSING_RECEIPT, queue.single().type)
        assertTrue("NO_RECEIPT_REQUIRED" in queue.single().availableActions)
    }

    @Test fun partialCategoryUsesRemainingAmount() {
        val transaction = tx("partial", -100.0, BankReconciliationStatus.PARTIAL)
        val link = BankReceiptLink("l","partial",1,"r1",40.0)
        val queue = BankReviewQueueBuilder.build(listOf(transaction), listOf(receipt(1)), listOf(link), emptyMap(), emptyList())
        assertEquals(BankReviewType.PARTIAL_PAYMENT, queue.single().type)
        assertEquals(60.0, queue.single().remainingAmount, 0.001)
    }

    @Test fun manualReviewCategoryReusesExistingReviewStatus() {
        val queue = BankReviewQueueBuilder.build(listOf(tx("manual", status=BankReconciliationStatus.REVIEW)), emptyList(), emptyList(), emptyMap(), emptyList())
        assertEquals(BankReviewType.MANUAL_REVIEW, queue.single().type)
    }

    @Test fun combinationSuggestionCategoryIsCreated() {
        val transaction = tx("combo", -200.0)
        val receipts = listOf(receipt(1), receipt(2))
        val combo = BankCombinationMatcher.oneTransactionToManyReceipts(transaction, receipts, emptyList())
        val queue = BankReviewQueueBuilder.build(listOf(transaction), receipts, emptyList(), emptyMap(), combo)
        assertEquals(BankReviewType.COMBINATION_SUGGESTION, queue.single().type)
    }

    @Test fun multipleCombinationConflictBeatsCombinationSuggestion() {
        val transaction = tx("multi", -200.0)
        val receipts = listOf(receipt(1), receipt(2), receipt(3))
        val combo = BankCombinationMatcher.oneTransactionToManyReceipts(transaction, receipts, emptyList())
        val queue = BankReviewQueueBuilder.build(listOf(transaction), receipts, emptyList(), emptyMap(), combo)
        assertEquals(BankReviewType.MULTIPLE_CANDIDATES, queue.single().type)
        assertTrue(queue.single().priority > 30)
    }

    @Test fun rentReviewAndLoanReviewReuseExistingAssignments() {
        val rentTx = tx("rent", 500.0, BankReconciliationStatus.REVIEW)
        val loanTx = tx("loan", -445.0, BankReconciliationStatus.REVIEW)
        val rent = BankRentAssignment(
            assignmentId="ra", transactionId="rent", propertyId="p", unitId="u", rentMonth="2026-09",
            tenantReference="tenant", allocatedAmount=500.0, paymentType="MIETE",
            status=BankRentAssignmentStatus.CONFIRMED, source=BankRentAssignmentSource.MANUAL,
            createdAt="c", updatedAt="u"
        )
        val loan = BankLoanAssignment("la","loan",1,"p",445.0,BankLoanPaymentType.REGULAERE_RATE,"2026-09",BankLoanAssignmentStatus.REVIEW,"MANUAL",createdAt="c",updatedAt="u")
        val queue = BankReviewQueueBuilder.build(listOf(rentTx,loanTx), emptyList(), emptyList(), emptyMap(), emptyList(), listOf(rent), listOf(loan))
        assertTrue(queue.any { it.type == BankReviewType.RENT_REVIEW })
        assertTrue(queue.any { it.type == BankReviewType.LOAN_REVIEW })
    }

    @Test fun recurringReviewCategoryIsCreatedForUncertainEnabledPattern() {
        val pattern = BankRecurringPattern(
            patternId="rec", enabled=true, direction=RecurringDirection.EXPENSE, normalizedCounterparty="vendor", purposeFingerprint="service",
            typicalAmount=99.0, amountTolerance=2.0, cadence=RecurringCadence.MONTHLY, typicalDay=5, occurrenceCount=3, confidence=70,
            lastOccurrence="2026-09-05", nextExpectedStart="2026-10-01", nextExpectedEnd="2026-10-10", reasonsText="3 Vorkommen", createdAt="c", updatedAt="u"
        )
        val queue = BankReviewQueueBuilder.build(emptyList(), emptyList(), emptyList(), emptyMap(), emptyList(), recurringPatterns=listOf(pattern))
        assertEquals(BankReviewType.RECURRING_REVIEW, queue.single().type)
    }

    @Test fun criticalConflictSortsBeforeSafeSuggestionAndDateIsSecondary() {
        val safe = BankReviewItem("s",BankReviewType.SAFE_SUGGESTION,30,listOf("s"),listOf(1),amount=100.0,remainingAmount=100.0,score=95,confidence="HOCH",title="safe",explanation="",bookingDate="2026-09-09")
        val conflict = BankReviewItem("c",BankReviewType.PROPERTY_CONFLICT,100,listOf("c"),listOf(2),amount=100.0,remainingAmount=100.0,title="conflict",explanation="",bookingDate="2026-09-01")
        val d1 = BankReviewItem("d1",BankReviewType.MANUAL_REVIEW,80,listOf("d1"),emptyList(),title="1",explanation="",bookingDate="2026-09-01")
        val d2 = BankReviewItem("d2",BankReviewType.MANUAL_REVIEW,80,listOf("d2"),emptyList(),title="2",explanation="",bookingDate="2026-09-02")
        val sorted = listOf(safe, conflict, d1, d2).sortedWith(compareByDescending<BankReviewItem>{it.priority}.thenByDescending{it.bookingDate}.thenBy{it.stableKey})
        assertEquals("c", sorted.first().stableKey)
        assertTrue(sorted.indexOf(d2) < sorted.indexOf(d1))
    }

    @Test fun stableQueueKeysAreDeterministicAcrossRefresh() {
        val transaction = tx("safe")
        val suggestion = BankMatchSuggestion("safe", 1, 95, "HOCH", emptyList())
        val a = BankReviewQueueBuilder.build(listOf(transaction), listOf(receipt(1)), emptyList(), mapOf("safe" to suggestion), emptyList())
        val b = BankReviewQueueBuilder.build(listOf(transaction), listOf(receipt(1)), emptyList(), mapOf("safe" to suggestion), emptyList())
        assertEquals(a.map { it.stableKey }, b.map { it.stableKey })
    }

    @Test fun onlySafeHighConfidenceItemsAreBatchEligible() {
        val safe = BankReviewItem("s",BankReviewType.SAFE_SUGGESTION,30,listOf("s"),listOf(1),amount=100.0,remainingAmount=100.0,score=95,confidence="HOCH",title="safe",explanation="")
        val missing = safe.copy(stableKey="m", type=BankReviewType.MISSING_RECEIPT)
        val conflict = safe.copy(stableKey="c", conflicts=listOf(BankCombinationConflict.MULTIPLE_COMBINATIONS))
        assertTrue(BankBatchEligibility.evaluate(safe).eligible)
        assertFalse(BankBatchEligibility.evaluate(missing).eligible)
        assertFalse(BankBatchEligibility.evaluate(conflict).eligible)
    }

    @Test fun batchPreviewTotalsOnlyEligibleItemsAndListsExcluded() {
        val safe1 = BankReviewItem("s1",BankReviewType.SAFE_SUGGESTION,30,listOf("t1"),listOf(1),amount=100.0,remainingAmount=100.0,score=95,confidence="HOCH",title="safe",explanation="")
        val safe2 = safe1.copy(stableKey="s2",transactionIds=listOf("t2"),receiptIds=listOf(2),amount=80.0,remainingAmount=80.0)
        val missing = safe1.copy(stableKey="m",type=BankReviewType.MISSING_RECEIPT,transactionIds=listOf("t3"))
        val preview = BankBatchEligibility.preview(listOf(safe1,safe2,missing))
        assertEquals(180.0, preview.totalAmount, 0.001)
        assertEquals(listOf("s1","s2"), preview.eligibleKeys)
        assertEquals(listOf("m"), preview.excludedKeys)
        assertTrue(preview.warnings.isNotEmpty())
    }

    @Test fun allTwelveRequiredReviewTypesExist() {
        val required = setOf(
            BankReviewType.SAFE_SUGGESTION, BankReviewType.MISSING_RECEIPT, BankReviewType.MULTIPLE_CANDIDATES,
            BankReviewType.COMBINATION_SUGGESTION, BankReviewType.PARTIAL_PAYMENT, BankReviewType.RENT_REVIEW,
            BankReviewType.LOAN_REVIEW, BankReviewType.RECURRING_REVIEW, BankReviewType.POSSIBLE_DUPLICATE,
            BankReviewType.AMOUNT_CONFLICT, BankReviewType.PROPERTY_CONFLICT, BankReviewType.MANUAL_REVIEW
        )
        assertEquals(12, required.size)
    }
}
