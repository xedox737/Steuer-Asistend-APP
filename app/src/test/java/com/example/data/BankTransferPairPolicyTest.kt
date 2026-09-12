package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankTransferPairPolicyTest {
    private fun tx(
        id: String,
        account: String,
        date: String,
        amount: Double,
        classification: String = BankTransactionClassification.NORMAL,
        linked: String = ""
    ) = BankTransaction(
        transactionId = id,
        accountId = account,
        bookingDate = date,
        amount = amount,
        classification = classification,
        linkedTransferTransactionId = linked
    )

    @Test
    fun confirmedPairGetsReciprocalLinksAndCounterAccounts() {
        val outgoing = tx("out", "a", "2026-09-10", -500.0)
        val incoming = tx("in", "b", "2026-09-10", 500.0)

        val update = BankTransferPairPolicy.confirm(outgoing, incoming, "now")

        assertEquals(BankTransactionClassification.TRANSFER, update.first.classification)
        assertEquals("b", update.first.transferCounterAccountId)
        assertEquals("in", update.first.linkedTransferTransactionId)
        assertEquals(BankReviewState.DONE, update.first.reviewState)
        assertEquals(BankTransactionClassification.TRANSFER, update.second.classification)
        assertEquals("a", update.second.transferCounterAccountId)
        assertEquals("out", update.second.linkedTransferTransactionId)
        assertEquals(BankReviewState.DONE, update.second.reviewState)
    }

    @Test
    fun sameAccountOrSameSignCannotBeConfirmed() {
        val outgoing = tx("out", "a", "2026-09-10", -500.0)
        assertFalse(BankTransferPairPolicy.canConfirm(outgoing, tx("same-account", "a", "2026-09-10", 500.0)))
        assertFalse(BankTransferPairPolicy.canConfirm(outgoing, tx("same-sign", "b", "2026-09-10", -500.0)))
    }

    @Test
    fun privateMovementCannotBeSilentlyConvertedToTransferPair() {
        val privateTx = tx("private", "a", "2026-09-10", -500.0, BankTransactionClassification.PRIVATE_IGNORED)
        val incoming = tx("in", "b", "2026-09-10", 500.0)
        assertFalse(BankTransferPairPolicy.canConfirm(privateTx, incoming))
    }

    @Test
    fun unlinkClearsBothReciprocalLinksButKeepsTransferClassification() {
        val first = tx("a-tx", "a", "2026-09-10", -500.0, BankTransactionClassification.TRANSFER, linked = "b-tx")
            .copy(transferCounterAccountId = "b")
        val second = tx("b-tx", "b", "2026-09-10", 500.0, BankTransactionClassification.TRANSFER, linked = "a-tx")
            .copy(transferCounterAccountId = "a")

        val update = BankTransferPairPolicy.unlink(first, second, "now")

        assertEquals(BankTransactionClassification.TRANSFER, update.selected.classification)
        assertEquals("", update.selected.linkedTransferTransactionId)
        assertEquals("", update.selected.transferCounterAccountId)
        assertEquals(BankTransactionClassification.TRANSFER, update.counterpart!!.classification)
        assertEquals("", update.counterpart!!.linkedTransferTransactionId)
    }

    @Test
    fun unlinkDoesNotModifyUnrelatedCounterpart() {
        val first = tx("a-tx", "a", "2026-09-10", -500.0, BankTransactionClassification.TRANSFER, linked = "b-tx")
        val unrelated = tx("b-tx", "b", "2026-09-10", 500.0, BankTransactionClassification.TRANSFER, linked = "other")
        val update = BankTransferPairPolicy.unlink(first, unrelated, "now")
        assertNull(update.counterpart)
    }
}
