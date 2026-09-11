package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankTransactionClassificationTest {
    private fun tx(id: String = "tx-1", account: String = "a", date: String = "2026-09-01", amount: Double = -100.0) =
        BankTransaction(transactionId = id, accountId = account, bookingDate = date, amount = amount)

    @Test
    fun privateIgnored_isDone_andExcludedFromMatchingAndDatev() {
        val transaction = tx()
        val record = BankClassificationPolicy.classify(
            transactionId = transaction.transactionId,
            classification = BankTransactionClassification.PRIVATE_IGNORED,
            now = "2026-09-11T12:00:00Z"
        )
        val decision = BankClassificationPolicy.decision(transaction, record)

        assertEquals(BankReviewState.DONE, decision.reviewState)
        assertFalse(decision.requiresReceiptReview)
        assertFalse(decision.eligibleForReceiptMatching)
        assertFalse(decision.eligibleForNormalDatevExport)
        assertNotNull(BankDatevClassificationGate.exclusionReason(record))
    }

    @Test
    fun persistedPrivateIgnored_isUsedWithoutSeparateRecord() {
        val transaction = tx().copy(
            classification = BankTransactionClassification.PRIVATE_IGNORED,
            reviewState = BankReviewState.OPEN
        )

        val decision = BankClassificationPolicy.decision(transaction)

        assertEquals(BankTransactionClassification.PRIVATE_IGNORED, decision.classification)
        assertEquals(BankReviewState.DONE, decision.reviewState)
        assertFalse(decision.requiresReceiptReview)
        assertFalse(decision.eligibleForReceiptMatching)
        assertFalse(decision.eligibleForNormalDatevExport)
        assertNotNull(BankDatevClassificationGate.exclusionReason(transaction))
    }

    @Test
    fun persistedNormalDoneReviewState_isPreservedWithoutSeparateRecord() {
        val transaction = tx().copy(
            classification = BankTransactionClassification.NORMAL,
            reviewState = BankReviewState.DONE
        )

        val decision = BankClassificationPolicy.decision(transaction)

        assertEquals(BankTransactionClassification.NORMAL, decision.classification)
        assertEquals(BankReviewState.DONE, decision.reviewState)
        assertTrue(decision.requiresReceiptReview)
        assertTrue(decision.eligibleForReceiptMatching)
        assertTrue(decision.eligibleForNormalDatevExport)
    }

    @Test
    fun transfer_keepsCounterAccount_andIsNotNormalIncomeExpense() {
        val transaction = tx()
        val record = BankClassificationPolicy.classify(
            transactionId = transaction.transactionId,
            classification = BankTransactionClassification.TRANSFER,
            transferCounterAccountId = "account-b",
            linkedTransferTransactionId = "tx-2",
            now = "2026-09-11T12:00:00Z"
        )

        assertEquals("account-b", record.transferCounterAccountId)
        assertEquals("tx-2", record.linkedTransferTransactionId)
        assertFalse(BankClassificationPolicy.decision(transaction, record).eligibleForNormalDatevExport)
    }

    @Test
    fun normal_openTransaction_staysInReview() {
        val transaction = tx()
        val record = BankClassificationPolicy.classify(
            transactionId = transaction.transactionId,
            classification = BankTransactionClassification.NORMAL,
            now = "2026-09-11T12:00:00Z"
        )
        val decision = BankClassificationPolicy.decision(transaction, record)

        assertEquals(BankReviewState.OPEN, decision.reviewState)
        assertTrue(decision.requiresReceiptReview)
        assertTrue(decision.eligibleForReceiptMatching)
        assertTrue(decision.eligibleForNormalDatevExport)
        assertNull(BankDatevClassificationGate.exclusionReason(record))
    }

    @Test
    fun transferMatcher_onlySuggestsOppositeEqualMovementOnOtherAccount() {
        val source = tx(id = "out", account = "checking", date = "2026-09-10", amount = -1000.0)
        val counterpart = tx(id = "in", account = "savings", date = "2026-09-11", amount = 1000.0)
        val wrongSign = tx(id = "wrong", account = "savings", date = "2026-09-11", amount = -1000.0)

        val result = BankTransferMatcher.suggestions(source, listOf(counterpart, wrongSign))

        assertEquals(1, result.size)
        assertEquals("in", result.single().counterTransactionId)
        assertTrue(result.single().score >= 85)
    }
}
