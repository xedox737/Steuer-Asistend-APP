package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankReceiptDeletionPolicyTest {
    @Test
    fun allowsDeletionWithoutBankLinks() {
        val decision = BankReceiptDeletionPolicy.decide(receiptId = 42L, links = emptyList())

        assertTrue(decision.allowed)
        assertNull(decision.reason)
    }

    @Test
    fun blocksDeletionWhileBankLinksExist() {
        val links = listOf(
            BankReceiptLink(linkId = "l1", transactionId = "t1", receiptId = 42L),
            BankReceiptLink(linkId = "l2", transactionId = "t2", receiptId = 42L),
            BankReceiptLink(linkId = "other", transactionId = "t3", receiptId = 99L)
        )

        val decision = BankReceiptDeletionPolicy.decide(receiptId = 42L, links = links)

        assertFalse(decision.allowed)
        assertTrue(decision.linkedTransactionCount == 2)
        assertTrue(decision.reason?.contains("2 Bankbuchungen") == true)
    }
}
