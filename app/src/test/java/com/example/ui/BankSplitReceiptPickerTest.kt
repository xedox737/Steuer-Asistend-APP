package com.example.ui

import com.example.data.Receipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankSplitReceiptPickerTest {
    private fun receipt(id: Int) = Receipt(
        id = id,
        aussteller = "Lieferant $id",
        datum = "2026-09-10",
        uhrzeit = "",
        bruttobetrag = id.toDouble(),
        hauptkategorie = "Kosten",
        unterkategorie = "Test",
        kontoNr = "",
        beschreibung = "Synthetischer Beleg $id"
    )

    @Test fun searchFindsReceiptsBeyondFirstThirty() {
        val receipts = (1..55).map(::receipt)
        assertEquals(55, searchableSplitReceipts(receipts, "").size)
        assertEquals(listOf(41), searchableSplitReceipts(receipts, "Lieferant 41").map { it.id })
        assertEquals(listOf(55), searchableSplitReceipts(receipts, "55,0").map { it.id })
    }

    @Test fun missingReceiptShowsNoResultsWithoutModifyingInventory() {
        val receipts = (1..55).map(::receipt)
        assertTrue(searchableSplitReceipts(receipts, "unbekannter-beleg").isEmpty())
        assertEquals(55, receipts.size)
    }
}
