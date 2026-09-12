package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankDatevPrecheckPolicyTest {
    @Test
    fun separatesPrivateTransferUnresolvedAndPotentiallyExportable() {
        val normalMatched = transaction("matched", BankReconciliationStatus.MATCHED)
        val noReceipt = transaction("no-receipt", BankReconciliationStatus.NO_RECEIPT_REQUIRED)
        val openDone = transaction("open-done", BankReconciliationStatus.OPEN, reviewState = BankReviewState.DONE)
        val private = transaction("private", BankReconciliationStatus.OPEN, classification = BankTransactionClassification.PRIVATE_IGNORED)
        val transfer = transaction("transfer", BankReconciliationStatus.MATCHED, classification = BankTransactionClassification.TRANSFER)

        val summary = BankDatevPrecheckPolicy.summarize(listOf(normalMatched, noReceipt, openDone, private, transfer))

        assertEquals(5, summary.checked)
        assertEquals(2, summary.potentiallyExportable)
        assertEquals(1, summary.privateIgnored)
        assertEquals(1, summary.transfers)
        assertEquals(1, summary.noReceiptRequired)
        assertEquals(1, summary.unresolved)
        assertEquals(1, summary.done)
        assertTrue(summary.reasons.any { it.transactionId == "private" && it.code == "BANK_PRIVATE_IGNORED" })
        assertTrue(summary.reasons.any { it.transactionId == "transfer" && it.code == "BANK_TRANSFER" })
        assertTrue(summary.reasons.any { it.transactionId == "open-done" && it.code == "BANK_UNRESOLVED" })
    }

    @Test
    fun doneDoesNotMakeAnOpenTransactionDatevReady() {
        val doneOpen = transaction("done-open", BankReconciliationStatus.OPEN, reviewState = BankReviewState.DONE)

        val summary = BankDatevPrecheckPolicy.summarize(listOf(doneOpen))

        assertEquals(0, summary.potentiallyExportable)
        assertEquals(1, summary.unresolved)
        assertFalse(BankDatevPrecheckPolicy.needsReviewInbox(doneOpen))
        assertEquals("BANK_UNRESOLVED", BankDatevPrecheckPolicy.reason(doneOpen)?.code)
    }

    @Test
    fun onlyOpenNormalTransactionsEnterReviewInbox() {
        val open = transaction("open", BankReconciliationStatus.OPEN)
        val private = transaction("private", BankReconciliationStatus.OPEN, classification = BankTransactionClassification.PRIVATE_IGNORED)
        val matched = transaction("matched", BankReconciliationStatus.MATCHED)

        assertTrue(BankDatevPrecheckPolicy.needsReviewInbox(open))
        assertFalse(BankDatevPrecheckPolicy.needsReviewInbox(private))
        assertFalse(BankDatevPrecheckPolicy.needsReviewInbox(matched))
    }

    private fun transaction(
        id: String,
        status: String,
        classification: String = BankTransactionClassification.NORMAL,
        reviewState: String = BankReviewState.OPEN
    ) = BankTransaction(
        transactionId = id,
        accountId = "account",
        bookingDate = "2026-09-11",
        amount = -42.0,
        reconciliationStatus = status,
        classification = classification,
        reviewState = reviewState
    )
}
