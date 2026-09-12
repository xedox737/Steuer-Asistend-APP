package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankCollectiveReceiptLinkPolicyTest {
    @Test
    fun severalTransactionsCanShareOneReceiptWithoutDuplicatingIt() {
        val receipt = receipt(amount = 200.0)
        val selected = listOf(transaction("t1", -100.0), transaction("t2", -100.0))

        val preview = BankCollectiveReceiptLinkPolicy.preview(receipt, selected, emptyList())

        assertEquals(2, preview.candidateCount)
        assertEquals(0, preview.conflictCount)
        assertEquals(200.0, preview.allocatedTotal, 0.001)
    }

    @Test
    fun previewStopsFurtherAllocationWhenReceiptIsAlreadyCovered() {
        val receipt = receipt(amount = 100.0)
        val selected = listOf(transaction("t1", -100.0), transaction("t2", -100.0))

        val preview = BankCollectiveReceiptLinkPolicy.preview(receipt, selected, emptyList())

        assertEquals(1, preview.candidateCount)
        assertEquals(1, preview.conflictCount)
        assertTrue(preview.conflicts.first().transactionId == "t2")
    }

    private fun transaction(id: String, amount: Double) = BankTransaction(
        transactionId = id,
        accountId = "a1",
        bookingDate = "2026-09-01",
        amount = amount,
        counterparty = "Stadtwerke"
    )

    private fun receipt(amount: Double) = Receipt(
        id = 42,
        aussteller = "Stadtwerke",
        datum = "2026-09-01",
        uhrzeit = "",
        bruttobetrag = amount,
        hauptkategorie = "Betriebskosten",
        unterkategorie = "Strom",
        kontoNr = "",
        beschreibung = "Jahresabrechnung",
        wohneinheit = "",
        mieter = "",
        zahlungsart = "Überweisung"
    )
}
