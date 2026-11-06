package com.example.ui

import com.example.data.BankReceiptLink
import com.example.data.Receipt
import org.junit.Assert.*
import org.junit.Test

class ReceiptBankAssignmentIndexTest {
    private val receipt = Receipt(id = 15, internalId = "stable-receipt", aussteller = "Test",
        datum = "2026-01-04", uhrzeit = "", bruttobetrag = 650.0,
        hauptkategorie = "Einnahmen", unterkategorie = "Miete", kontoNr = "", beschreibung = "Test")
    private val link = BankReceiptLink(linkId = "link", transactionId = "tx", receiptId = 15,
        receiptInternalId = "stable-receipt", allocatedAmount = 650.0)

    @Test fun confirmedLinkIsVisibleByLocalReceiptId() {
        assertTrue(ReceiptBankAssignmentIndex(listOf(link.copy(receiptInternalId = ""))).isAssigned(receipt))
    }

    @Test fun stableIdentitySurvivesChangedLocalId() {
        assertTrue(ReceiptBankAssignmentIndex(listOf(link.copy(receiptId = 99))).isAssigned(receipt))
    }

    @Test fun unrelatedReceiptsAndBlankStableIdsAreNotAssigned() {
        val index = ReceiptBankAssignmentIndex(listOf(link.copy(receiptInternalId = "")))
        assertFalse(index.isAssigned(receipt.copy(id = 99, internalId = "")))
        assertFalse(index.isAssigned(receipt.copy(id = 99, internalId = "unrelated")))
    }

    @Test fun suggestionsAreNotConfirmedAssignments() {
        assertFalse(ReceiptBankAssignmentIndex(listOf(link.copy(status = "SUGGESTED"))).isAssigned(receipt))
    }

    @Test fun partialAndMultiplePaymentsRemainAssignedUntilLastLinkIsRemoved() {
        val first = link.copy(allocatedAmount = 200.0)
        val second = link.copy(linkId = "second", transactionId = "second-tx", allocatedAmount = 450.0)
        assertTrue(ReceiptBankAssignmentIndex(listOf(first, second)).isAssigned(receipt))
        assertTrue(ReceiptBankAssignmentIndex(listOf(first)).isAssigned(receipt))
        assertFalse(ReceiptBankAssignmentIndex(emptyList()).isAssigned(receipt))
    }
}
