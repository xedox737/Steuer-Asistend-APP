package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankAssignmentFavoritesPolicyTest {
    @Test
    fun manualFavoriteRanksBeforeMoreFrequentAssignment() {
        val r1 = receipt(1, "Strom", "Energie")
        val r2 = receipt(2, "Versicherung", "Gebäude")
        val t1 = transaction("t1", "2026-08-01")
        val t2 = transaction("t2", "2026-08-02")
        val t3 = transaction("t3", "2026-08-03")
        val links = listOf(
            link("l1", "t1", 1),
            link("l2", "t2", 1),
            link("l3", "t3", 2)
        )
        val manualKey = BankAssignmentFavoritesPolicy.key(r2)

        val result = BankAssignmentFavoritesPolicy.derive(
            transactions = listOf(t1, t2, t3),
            receipts = listOf(r1, r2),
            links = links,
            manualFavoriteKeys = setOf(manualKey)
        )

        assertEquals(manualKey, result.first().key)
        assertTrue(result.first().manualFavorite)
        assertEquals(2, result.first { it.key == BankAssignmentFavoritesPolicy.key(r1) }.useCount)
    }

    @Test
    fun withoutManualFavoriteFrequencyThenRecencyDecide() {
        val r1 = receipt(1, "Strom", "Energie")
        val r2 = receipt(2, "Strom", "Wasser")
        val t1 = transaction("t1", "2026-08-01")
        val t2 = transaction("t2", "2026-08-05")
        val links = listOf(link("l1", "t1", 1), link("l2", "t2", 2))

        val result = BankAssignmentFavoritesPolicy.derive(
            transactions = listOf(t1, t2), receipts = listOf(r1, r2), links = links, manualFavoriteKeys = emptySet()
        )

        assertEquals(BankAssignmentFavoritesPolicy.key(r2), result.first().key)
    }

    private fun transaction(id: String, date: String) = BankTransaction(
        transactionId = id,
        accountId = "a1",
        bookingDate = date,
        amount = -50.0
    )

    private fun receipt(id: Int, category: String, subcategory: String) = Receipt(
        id = id,
        aussteller = "Anbieter $id",
        datum = "2026-08-01",
        uhrzeit = "",
        bruttobetrag = 50.0,
        hauptkategorie = category,
        unterkategorie = subcategory,
        kontoNr = "",
        beschreibung = "",
        wohneinheit = "OG links",
        mieter = "",
        zahlungsart = "Überweisung",
        propertyId = "p1"
    )

    private fun link(id: String, txId: String, receiptId: Int) = BankReceiptLink(
        linkId = id,
        transactionId = txId,
        receiptId = receiptId,
        allocatedAmount = 50.0
    )
}
