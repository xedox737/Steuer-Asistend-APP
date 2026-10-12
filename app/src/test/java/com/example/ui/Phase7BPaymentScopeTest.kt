package com.example.ui

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class Phase7BPaymentScopeTest {
    private val receipt = Receipt(981, "Handwerker", "2026-10-01", "", 500.0, "Reparaturen", "Heizung", "", "Kessel",
        propertyId = "a", internalId = "original-a")
    private fun tx(id: String, property: String, amount: Double, date: String = "2026-10-02") = BankTransaction(id, "bank", date,
        amount = amount, propertyId = property, category = if (amount > 0) "Sonstige Einnahmen" else "Versicherung",
        reconciliationStatus = BankReconciliationStatus.NO_RECEIPT_REQUIRED, noReceiptReason = "Sonstiges")
    private fun entries() = LedgerPaymentPresentation.entries(listOf(receipt, receipt.copy(id = 982, propertyId = "b", internalId = "original-b", bruttobetrag = 700.0),
        receipt.copy(id = 983, internalId = "original-prior", datum = "2025-10-01", bruttobetrag = 50.0)), emptyList(),
        listOf(BankReceiptLink("link", "linked", receipt.id, receipt.internalId, 500.0)),
        listOf(tx("linked", "a", -500.0).copy(counterparty = "Handwerker", purpose = "Kessel"), tx("direct", "a", -1200.0), tx("refund", "a", 200.0), tx("b-refund", "b", 300.0)))
    @Test fun twoObjectsUseTheSameEntriesForListAndTotalsWithReceiptBankDedup() {
        val entries = entries()
        val a = LedgerPaymentPresentation.filter(entries, LedgerFilters(2026, propertyId = "a"))
        assertEquals(setOf("bank-link-link", "bank-direct", "bank-refund"), a.map { it.key }.toSet())
        assertEquals(1, a.count { it.receipt?.id == receipt.id })
        assertEquals(200.0, LedgerPaymentPresentation.totals(a).income, 0.0)
        assertEquals(1700.0, LedgerPaymentPresentation.totals(a).expense, 0.0)
        assertEquals(-1500.0, LedgerPaymentPresentation.totals(a).result, 0.0)
        assertEquals(a.size, LedgerPaymentPresentation.totals(a).count)
        val b = LedgerPaymentPresentation.filter(entries, LedgerFilters(2026, propertyId = "b"))
        assertEquals(2, b.size); assertTrue(b.all { it.propertyId == "b" })
        assertEquals(300.0, LedgerPaymentPresentation.totals(b).income, 0.0)
        assertEquals(700.0, LedgerPaymentPresentation.totals(b).expense, 0.0)
    }
    @Test fun yearCategoryAndMainOrSubcategorySearchUseSharedFilter() {
        val entries = entries()
        assertEquals(50.0, LedgerPaymentPresentation.totals(LedgerPaymentPresentation.filter(entries, LedgerFilters(2025, propertyId = "a"))).expense, 0.0)
        listOf("Reparaturen", "Heizung", "Kessel", "Handwerker").forEach { query ->
            val filtered = LedgerPaymentPresentation.filter(entries, LedgerFilters(2026, propertyId = "a", query = query, category = "Heizung"))
            assertEquals(receipt, filtered.single().receipt)
            assertEquals(500.0, LedgerPaymentPresentation.totals(filtered).expense, 0.0)
        }
    }
    @Test fun splitAcrossObjectsDeduplicatesBeforeObjectFilter() {
        val split = BankRentAssignment("split-a", "split", "a", "unit-a", "2026-10", "tenant", 500.0,
            BankSplitPaymentType.OTHER_EXPENSE, createdAt = "", updatedAt = "", source = BankRentAssignmentSource.MANUAL,
            receiptId = receipt.id)
        val entries = LedgerPaymentPresentation.entries(listOf(receipt), listOf(split, split.copy(assignmentId = "split-b", propertyId = "b", receiptId = null, allocatedAmount = 300.0)),
            listOf(BankReceiptLink("linked-split", "split", receipt.id, receipt.internalId, 500.0)), listOf(tx("split", "a", -800.0)))
        assertEquals(500.0, LedgerPaymentPresentation.totals(LedgerPaymentPresentation.filter(entries, LedgerFilters(2026, propertyId = "a"))).expense, 0.0)
        assertEquals(300.0, LedgerPaymentPresentation.totals(LedgerPaymentPresentation.filter(entries, LedgerFilters(2026, propertyId = "b"))).expense, 0.0)
        assertEquals(2, entries.size)
    }
}
