package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankBatchActionPolicyTest {
    private fun tx(
        id: String,
        classification: String = BankTransactionClassification.NORMAL,
        reconciliationStatus: String = BankReconciliationStatus.OPEN,
        linkedTransferTransactionId: String = ""
    ) = BankTransaction(
        transactionId = id,
        accountId = "acc",
        bookingDate = "2026-09-01",
        valueDate = "2026-09-01",
        amount = -50.0,
        currency = "EUR",
        classification = classification,
        reconciliationStatus = reconciliationStatus,
        linkedTransferTransactionId = linkedTransferTransactionId
    )

    @Test
    fun privateBatchSkipsReceiptLinksAndOtherSpecialClassification() {
        val selected = listOf(
            tx("eligible"),
            tx("linked"),
            tx("transfer", classification = BankTransactionClassification.TRANSFER),
            tx("already", classification = BankTransactionClassification.PRIVATE_IGNORED)
        )
        val links = listOf(BankReceiptLink(linkId = "l", transactionId = "linked", receiptId = 7))

        val preview = BankBatchActionPolicy.preview(selected, links, BankBatchAction.PRIVATE_IGNORED)

        assertEquals(listOf("eligible"), preview.eligibleTransactionIds)
        assertEquals(listOf("already"), preview.unchangedTransactionIds)
        assertEquals(2, preview.conflictCount)
    }

    @Test
    fun transferBatchNeverOverwritesExistingPairOrReceiptLink() {
        val selected = listOf(tx("eligible"), tx("paired", linkedTransferTransactionId = "other"), tx("linked"))
        val links = listOf(BankReceiptLink(linkId = "l", transactionId = "linked", receiptId = 7))

        val preview = BankBatchActionPolicy.preview(selected, links, BankBatchAction.TRANSFER)

        assertEquals(listOf("eligible"), preview.eligibleTransactionIds)
        assertEquals(2, preview.conflictCount)
    }

    @Test
    fun noReceiptBatchProtectsMatchedAndPartialTransactions() {
        val selected = listOf(
            tx("open"),
            tx("review", reconciliationStatus = BankReconciliationStatus.REVIEW),
            tx("matched", reconciliationStatus = BankReconciliationStatus.MATCHED),
            tx("partial", reconciliationStatus = BankReconciliationStatus.PARTIAL),
            tx("already", reconciliationStatus = BankReconciliationStatus.NO_RECEIPT_REQUIRED)
        )

        val preview = BankBatchActionPolicy.preview(selected, emptyList(), BankBatchAction.NO_RECEIPT_REQUIRED)

        assertEquals(listOf("open", "review"), preview.eligibleTransactionIds)
        assertEquals(listOf("already"), preview.unchangedTransactionIds)
        assertEquals(2, preview.conflictCount)
        assertTrue(preview.conflicts.all { it.reason.isNotBlank() })
    }
}
