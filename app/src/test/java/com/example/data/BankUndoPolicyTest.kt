package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BankUndoPolicyTest {
    @Test
    fun restoresOnlyTransactionsStillAtActionTimestamp() {
        val state = BankUndoState(
            label = "Privat markieren",
            entries = listOf(
                BankUndoEntry("t1", BankUndoFieldSet.CLASSIFICATION, expectedUpdatedAt = "after-1"),
                BankUndoEntry("t2", BankUndoFieldSet.CLASSIFICATION, expectedUpdatedAt = "after-2")
            )
        )
        val current = listOf(
            transaction("t1", updatedAt = "after-1"),
            transaction("t2", updatedAt = "newer-change")
        )

        val decision = BankUndoPolicy.decide(current, state)

        assertEquals(listOf("t1"), decision.restorable.map { it.transactionId })
        assertEquals(listOf("t2"), decision.skipped.map { it.transactionId })
    }

    @Test
    fun missingTransactionIsSkipped() {
        val state = BankUndoState(
            label = "Kein Beleg",
            entries = listOf(BankUndoEntry("missing", BankUndoFieldSet.RECONCILIATION, expectedUpdatedAt = "after"))
        )

        val decision = BankUndoPolicy.decide(emptyList(), state)

        assertEquals(0, decision.restorable.size)
        assertEquals(1, decision.skipped.size)
    }

    private fun transaction(id: String, updatedAt: String) = BankTransaction(
        transactionId = id,
        accountId = "a1",
        bookingDate = "2026-09-01",
        amount = -10.0,
        updatedAt = updatedAt
    )
}
