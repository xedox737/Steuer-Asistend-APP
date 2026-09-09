package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankPhase2DFinalPatchTest {
    private fun tx(
        id: String,
        amount: Double = -100.0,
        propertyId: String = "p1",
        status: String = BankReconciliationStatus.OPEN,
        date: String = "2026-09-09"
    ) = BankTransaction(
        transactionId = id,
        accountId = "a1",
        bookingDate = date,
        amount = amount,
        counterparty = "Vendor $id",
        purpose = "Invoice $id",
        propertyId = propertyId,
        reconciliationStatus = status
    )

    private fun receipt(id: Int, amount: Double = 100.0, propertyId: String = "p1") = Receipt(
        id = id,
        aussteller = "Vendor $id",
        datum = "2026-09-08",
        uhrzeit = "",
        bruttobetrag = amount,
        hauptkategorie = "Kosten",
        unterkategorie = "Material",
        kontoNr = "",
        beschreibung = "Invoice $id",
        internalId = "r$id",
        propertyId = propertyId
    )

    private fun safe(key: String, txId: String, receiptId: Int, amount: Double = 100.0) = BankReviewItem(
        stableKey = key,
        type = BankReviewType.SAFE_SUGGESTION,
        priority = 30,
        transactionIds = listOf(txId),
        receiptIds = listOf(receiptId),
        propertyId = "p1",
        amount = amount,
        remainingAmount = amount,
        score = 95,
        confidence = "HOCH",
        title = "Sicher",
        explanation = ""
    )

    @Test fun detailedBatchPreviewContainsConcreteCaseDataAndAllocations() {
        val t1 = tx("t1", -100.0)
        val t2 = tx("t2", -80.0)
        val r1 = receipt(1, 100.0)
        val r2 = receipt(2, 80.0)
        val preview = BankBatchPreviewBuilder.build(
            listOf(safe("s1", "t1", 1, 100.0), safe("s2", "t2", 2, 80.0)),
            listOf(t1, t2), listOf(r1, r2), emptyList()
        )
        assertEquals(listOf("s1", "s2"), preview.eligibleKeys)
        assertEquals(2, preview.caseCount)
        assertEquals(2, preview.transactionCount)
        assertEquals(2, preview.receiptCount)
        assertEquals(180.0, preview.totalAmount, 0.001)
        val first = preview.cases.first { it.stableKey == "s1" }
        assertEquals("t1", first.transactionId)
        assertEquals("2026-09-09", first.bookingDate)
        assertEquals("Vendor t1", first.counterpartyOrPurpose)
        assertEquals(-100.0, first.transactionAmount, 0.001)
        assertEquals(100.0, first.transactionRemaining, 0.001)
        assertEquals(1, first.receiptId)
        assertEquals("Vendor 1", first.receiptIssuer)
        assertEquals(100.0, first.receiptAmount, 0.001)
        assertEquals(100.0, first.receiptRemaining, 0.001)
        assertEquals(100.0, first.allocationAmount, 0.001)
        assertEquals(95, first.score)
        assertEquals("HOCH", first.confidence)
    }

    @Test fun previewUsesRemainingAmountsAfterExistingAllocation() {
        val t = tx("t", -100.0)
        val r = receipt(1, 100.0)
        val link = BankReceiptLink("old", "t", 2, "r2", 40.0)
        val preview = BankBatchPreviewBuilder.build(listOf(safe("s", "t", 1, 60.0)), listOf(t), listOf(r), listOf(link))
        assertEquals(60.0, preview.cases.single().transactionRemaining, 0.001)
        assertEquals(60.0, preview.cases.single().allocationAmount, 0.001)
    }

    @Test fun excludedCasesAreConcreteAndCarryReasons() {
        val t = tx("t")
        val r = receipt(1)
        val low = safe("low", "t", 1).copy(confidence = "MITTEL")
        val preview = BankBatchPreviewBuilder.build(listOf(low), listOf(t), listOf(r), emptyList())
        assertTrue(preview.cases.isEmpty())
        assertEquals("low", preview.excludedCases.single().stableKey)
        assertEquals(listOf("t"), preview.excludedCases.single().transactionIds)
        assertEquals(listOf(1), preview.excludedCases.single().receiptIds)
        assertTrue(preview.excludedCases.single().reason.contains("Confidence"))
    }

    @Test fun allUnsafeReviewFamiliesAreExcludedFromBatch() {
        val t = tx("t")
        val r = receipt(1)
        val unsafeTypes = listOf(
            BankReviewType.MISSING_RECEIPT,
            BankReviewType.MULTIPLE_CANDIDATES,
            BankReviewType.COMBINATION_SUGGESTION,
            BankReviewType.PARTIAL_PAYMENT,
            BankReviewType.RENT_REVIEW,
            BankReviewType.LOAN_REVIEW,
            BankReviewType.RECURRING_REVIEW,
            BankReviewType.POSSIBLE_DUPLICATE,
            BankReviewType.AMOUNT_CONFLICT,
            BankReviewType.PROPERTY_CONFLICT,
            BankReviewType.MANUAL_REVIEW
        )
        val items = unsafeTypes.mapIndexed { index, type -> safe("u$index", "t$index", 100 + index).copy(type = type) }
        val txs = unsafeTypes.indices.map { index -> tx("t$index") }
        val receipts = unsafeTypes.indices.map { index -> receipt(100 + index) }
        val preview = BankBatchPreviewBuilder.build(items, txs, receipts, emptyList())
        assertEquals(0, preview.caseCount)
        assertEquals(unsafeTypes.size, preview.excludedCases.size)
    }

    @Test fun lowScoreAndZeroRestAreExcluded() {
        val t1 = tx("t1")
        val t2 = tx("t2")
        val r1 = receipt(1)
        val r2 = receipt(2)
        val lowScore = safe("score", "t1", 1).copy(score = BankCombinationThresholds.HIGH_SCORE - 1)
        val noRest = safe("rest", "t2", 2).copy(remainingAmount = 0.0)
        val preview = BankBatchPreviewBuilder.build(listOf(lowScore, noRest), listOf(t1, t2), listOf(r1, r2), emptyList())
        assertEquals(setOf("score", "rest"), preview.excludedCases.map { it.stableKey }.toSet())
    }

    @Test fun propertyConflictIsExcludedEvenIfItemLooksSafe() {
        val preview = BankBatchPreviewBuilder.build(
            listOf(safe("property", "t", 1)),
            listOf(tx("t", propertyId = "p1")),
            listOf(receipt(1, propertyId = "p2")),
            emptyList()
        )
        assertEquals(0, preview.caseCount)
        assertTrue(preview.excludedCases.single().reason.contains("Property-Konflikt"))
    }

    @Test fun duplicateStableKeysAreNotProcessedTwice() {
        val item = safe("same", "t", 1)
        val preview = BankBatchPreviewBuilder.build(listOf(item, item), listOf(tx("t")), listOf(receipt(1)), emptyList())
        assertEquals(1, preview.caseCount)
        assertEquals(listOf("same"), preview.eligibleKeys)
    }

    @Test fun conflictingReuseOfSameTransactionIsExcluded() {
        val items = listOf(safe("a", "t", 1), safe("b", "t", 2))
        val preview = BankBatchPreviewBuilder.build(items, listOf(tx("t")), listOf(receipt(1), receipt(2)), emptyList())
        assertEquals(1, preview.caseCount)
        assertEquals(1, preview.excludedCases.size)
        assertTrue(preview.excludedCases.single().reason.contains("bereits verwendet"))
    }

    @Test fun noReceiptRequiredIsNeverEligible() {
        val preview = BankBatchPreviewBuilder.build(
            listOf(safe("n", "t", 1)),
            listOf(tx("t", status = BankReconciliationStatus.NO_RECEIPT_REQUIRED)),
            listOf(receipt(1)), emptyList()
        )
        assertEquals(0, preview.caseCount)
        assertTrue(preview.excludedCases.single().reason.contains("NO_RECEIPT_REQUIRED"))
    }

    @Test fun reviewRoutingHasFunctionalPathForEveryRequiredType() {
        val expected = mapOf(
            BankReviewType.SAFE_SUGGESTION to BankPhase2DReviewAction.CONFIRM_SAFE,
            BankReviewType.MISSING_RECEIPT to BankPhase2DReviewAction.CREATE_RECEIPT,
            BankReviewType.MULTIPLE_CANDIDATES to BankPhase2DReviewAction.OPEN_RECEIPT_PICKER,
            BankReviewType.COMBINATION_SUGGESTION to BankPhase2DReviewAction.CONFIRM_COMBINATION,
            BankReviewType.PARTIAL_PAYMENT to BankPhase2DReviewAction.OPEN_RECEIPT_PICKER,
            BankReviewType.RENT_REVIEW to BankPhase2DReviewAction.OPEN_RENT_WORKFLOW,
            BankReviewType.LOAN_REVIEW to BankPhase2DReviewAction.OPEN_LOAN_WORKFLOW,
            BankReviewType.RECURRING_REVIEW to BankPhase2DReviewAction.OPEN_RECURRING_WORKFLOW,
            BankReviewType.POSSIBLE_DUPLICATE to BankPhase2DReviewAction.MARK_MANUAL_REVIEW,
            BankReviewType.AMOUNT_CONFLICT to BankPhase2DReviewAction.OPEN_RECEIPT_PICKER,
            BankReviewType.PROPERTY_CONFLICT to BankPhase2DReviewAction.OPEN_RECEIPT_PICKER,
            BankReviewType.MANUAL_REVIEW to BankPhase2DReviewAction.OPEN_RECEIPT_PICKER
        )
        expected.forEach { (type, action) ->
            assertTrue("$type must route to $action", action in BankPhase2DReviewActionPolicy.actionsFor(type, hasExistingLinks = true))
        }
    }

    @Test fun partialRoutingIncludesAddEditAndUnlinkWithoutAutoConfirmation() {
        val actions = BankPhase2DReviewActionPolicy.actionsFor(BankReviewType.PARTIAL_PAYMENT, hasExistingLinks = true)
        assertTrue(BankPhase2DReviewAction.OPEN_RECEIPT_PICKER in actions)
        assertTrue(BankPhase2DReviewAction.EDIT_ALLOCATION in actions)
        assertTrue(BankPhase2DReviewAction.UNLINK in actions)
        assertFalse(BankPhase2DReviewAction.CONFIRM_SAFE in actions)
    }

    @Test fun propertyConflictAndDuplicateHaveNoGenericConfirmAction() {
        val property = BankPhase2DReviewActionPolicy.actionsFor(BankReviewType.PROPERTY_CONFLICT, false)
        val duplicate = BankPhase2DReviewActionPolicy.actionsFor(BankReviewType.POSSIBLE_DUPLICATE, false)
        assertFalse(BankPhase2DReviewAction.CONFIRM_SAFE in property)
        assertFalse(BankPhase2DReviewAction.CONFIRM_COMBINATION in property)
        assertFalse(BankPhase2DReviewAction.CONFIRM_SAFE in duplicate)
        assertFalse(BankPhase2DReviewAction.CONFIRM_COMBINATION in duplicate)
    }
}
