package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankLinkedReceiptDatevPolicyTest {
    private fun receipt() = Receipt(
        id = 7,
        aussteller = "Versorger",
        datum = "2026-09-10",
        uhrzeit = "",
        bruttobetrag = 100.0,
        hauptkategorie = "Nebenkosten",
        unterkategorie = "Strom",
        kontoNr = "4800",
        beschreibung = "Strom",
        internalId = "receipt-7"
    )

    private fun tx(id: String, classification: String) = BankTransaction(
        transactionId = id,
        accountId = "bank-1",
        bookingDate = "2026-09-10",
        amount = -100.0,
        classification = classification
    )

    private fun link(txId: String) = BankReceiptLink(
        linkId = "link-$txId",
        transactionId = txId,
        receiptId = 7,
        receiptInternalId = "receipt-7",
        allocatedAmount = 100.0
    )

    @Test fun privateLinkedReceiptIsBlocked() {
        val exclusions = BankLinkedReceiptDatevPolicy.exclusions(
            receipt(), listOf(link("private")), listOf(tx("private", BankTransactionClassification.PRIVATE_IGNORED))
        )
        assertFalse(exclusions.isEmpty())
        assertEquals("BANK_PRIVATE_IGNORED", exclusions.single().code)
    }

    @Test fun transferLinkedReceiptIsBlocked() {
        val exclusions = BankLinkedReceiptDatevPolicy.exclusions(
            receipt(), listOf(link("transfer")), listOf(tx("transfer", BankTransactionClassification.TRANSFER))
        )
        assertEquals("BANK_TRANSFER", exclusions.single().code)
    }

    @Test fun normalLinkedReceiptRemainsEligibleForOtherDatevChecks() {
        assertTrue(BankLinkedReceiptDatevPolicy.isExportable(
            receipt(), listOf(link("normal")), listOf(tx("normal", BankTransactionClassification.NORMAL))
        ))
    }

    @Test fun unlinkedSpecialTransactionDoesNotBlockReceipt() {
        assertTrue(BankLinkedReceiptDatevPolicy.isExportable(
            receipt(), emptyList(), listOf(tx("private", BankTransactionClassification.PRIVATE_IGNORED))
        ))
    }
}
