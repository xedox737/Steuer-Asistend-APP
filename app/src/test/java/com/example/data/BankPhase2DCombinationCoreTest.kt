package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankPhase2DCombinationCoreTest {
    private fun tx(id: String, amount: Double, date: String = "2026-09-05", property: String = "p1") = BankTransaction(
        transactionId = id, accountId = "a1", bookingDate = date, amount = amount,
        counterparty = "Vendor", purpose = "Sammelzahlung", propertyId = property
    )

    private fun receipt(id: Int, amount: Double, date: String = "2026-09-04", property: String = "p1") = Receipt(
        id = id, aussteller = "Vendor", datum = date, uhrzeit = "", bruttobetrag = amount,
        hauptkategorie = "Renovierung", unterkategorie = "Material", kontoNr = "",
        beschreibung = "Rechnung $id", propertyId = property, internalId = "r-$id"
    )

    @Test fun oneToManyExact300Equals100Plus80Plus120() {
        val transaction = tx("t300", -300.0)
        val receipts = listOf(receipt(1, 100.0), receipt(2, 80.0), receipt(3, 120.0))
        val suggestions = BankCombinationMatcher.oneTransactionToManyReceipts(transaction, receipts, emptyList())
        val exact = suggestions.first { it.receiptIds.toSet() == setOf(1,2,3) }
        assertEquals(300.0, exact.matchedAmount, 0.001)
        assertEquals(0.0, exact.difference, 0.001)
        assertEquals("HOCH", exact.confidence)
        assertEquals(3, exact.allocations.size)
    }

    @Test fun manyToOneExact1000Equals500Plus500() {
        val r = receipt(10, 1000.0)
        val suggestions = BankCombinationMatcher.manyTransactionsToOneReceipt(r, listOf(tx("a", -500.0), tx("b", -500.0)), emptyList())
        val exact = suggestions.single()
        assertEquals(1000.0, exact.matchedAmount, 0.001)
        assertEquals(0.0, exact.difference, 0.001)
        assertEquals("HOCH", exact.confidence)
    }

    @Test fun manyToOneExact1000Equals400Plus600() {
        val r = receipt(11, 1000.0)
        val suggestions = BankCombinationMatcher.manyTransactionsToOneReceipt(r, listOf(tx("a400", -400.0), tx("b600", -600.0)), emptyList())
        val exact = suggestions.single()
        assertEquals(setOf("a400", "b600"), exact.transactionIds.toSet())
        assertEquals(1000.0, exact.matchedAmount, 0.001)
        assertEquals(0.0, exact.difference, 0.001)
        assertEquals("HOCH", exact.confidence)
    }

    @Test fun receipt1000WithExisting400Leaves600AndNextPaymentUsesRest() {
        val r = receipt(10, 1000.0)
        val existing = BankReceiptLink("l1", "old", 10, "r-10", 400.0)
        val remaining = BankAllocationPolicy.receiptRemaining(r, listOf(existing))
        assertEquals(600.0, remaining.remainingAmount, 0.001)
        val allocation = BankAllocationPolicy.guardAllocation(tx("new", -600.0), r, 600.0, listOf(existing))
        assertTrue(allocation.allowed)
    }

    @Test fun transaction1000With600AllocatedLeaves400AndPartialStatus() {
        val transaction = tx("t", -1000.0)
        val existing = BankReceiptLink("l", "t", 1, "r-1", 600.0)
        assertEquals(400.0, BankAllocationPolicy.transactionRemaining(transaction, listOf(existing)).remainingAmount, 0.001)
        assertEquals(BankReconciliationStatus.PARTIAL, BankLinkPolicy.statusFor(transaction, listOf(existing)))
    }

    @Test fun overAllocationIsRejectedForTransactionAndReceipt() {
        val transaction = tx("t", -300.0)
        val r = receipt(1, 250.0)
        assertFalse(BankAllocationPolicy.guardAllocation(transaction, r, 251.0, emptyList()).allowed)
        val txLink = BankReceiptLink("x", "t", 2, "r-2", 200.0)
        assertFalse(BankAllocationPolicy.guardAllocation(transaction, r, 101.0, listOf(txLink)).allowed)
    }

    @Test fun invalidAllocationIsRejected() {
        val transaction = tx("t", -300.0)
        val r = receipt(1, 300.0)
        assertFalse(BankAllocationPolicy.guardAllocation(transaction, r, 0.0, emptyList()).allowed)
        assertFalse(BankAllocationPolicy.guardAllocation(transaction, r, Double.NaN, emptyList()).allowed)
    }

    @Test fun nearCombinationHasVisibleDifferenceAndLowerConfidenceThanExact() {
        val transaction = tx("t", -300.0)
        val near = BankCombinationMatcher.oneTransactionToManyReceipts(transaction, listOf(receipt(1, 100.0), receipt(2, 80.0), receipt(3, 119.5)), emptyList()).first()
        assertEquals(0.5, near.difference, 0.001)
        assertTrue(near.score < 92 + 6)
        assertTrue(near.suggestionType == BankCombinationSuggestionType.NEAR_COMBINATION || near.suggestionType == BankCombinationSuggestionType.CONFLICT)
    }

    @Test fun twoEqualCombinationsAreMarkedMultipleAndNeverSilentlySelected() {
        val transaction = tx("t", -200.0)
        val receipts = listOf(receipt(1, 100.0), receipt(2, 100.0), receipt(3, 100.0))
        val suggestions = BankCombinationMatcher.oneTransactionToManyReceipts(transaction, receipts, emptyList())
        assertTrue(suggestions.size >= 2)
        assertTrue(suggestions.take(2).all { BankCombinationConflict.MULTIPLE_COMBINATIONS in it.conflicts })
        assertTrue(suggestions.take(2).all { it.confidence == "NIEDRIG" })
    }

    @Test fun explicitWrongPropertyIsExcludedFromCombinationCandidates() {
        val suggestions = BankCombinationMatcher.oneTransactionToManyReceipts(
            tx("t", -200.0, property = "p1"),
            listOf(receipt(1, 100.0, property = "p2"), receipt(2, 100.0, property = "p2")),
            emptyList()
        )
        assertTrue(suggestions.isEmpty())
    }

    @Test fun oldDatesAreExcludedAndSlightDelayRemainsPlausible() {
        val transaction = tx("t", -200.0, date = "2026-09-05")
        val plausible = BankCombinationMatcher.oneTransactionToManyReceipts(
            transaction, listOf(receipt(1, 100.0, date="2026-08-30"), receipt(2, 100.0, date="2026-08-31")), emptyList()
        )
        val old = BankCombinationMatcher.oneTransactionToManyReceipts(
            transaction, listOf(receipt(3, 100.0, date="2026-01-01"), receipt(4, 100.0, date="2026-01-02")), emptyList()
        )
        assertTrue(plausible.isNotEmpty())
        assertTrue(old.isEmpty())
    }

    @Test fun positiveIncomeDoesNotCombineWithExpenseReceipts() {
        val suggestions = BankCombinationMatcher.oneTransactionToManyReceipts(tx("income", 200.0), listOf(receipt(1,100.0), receipt(2,100.0)), emptyList())
        assertTrue(suggestions.isEmpty())
    }

    @Test fun maxCombinationSizeIsBounded() {
        assertEquals(4, BankCombinationThresholds.MAX_COMBINATION_SIZE)
        val transaction = tx("t", -500.0)
        val receipts = (1..5).map { receipt(it, 100.0) }
        assertTrue(BankCombinationMatcher.oneTransactionToManyReceipts(transaction, receipts, emptyList()).none { it.receiptIds.size > 4 })
    }

    @Test(timeout = 5000) fun largerCandidateSetStaysBoundedAndCompletes() {
        val transaction = tx("large", -400.0)
        val receipts = (1..20).map { receipt(it, 100.0) }
        val suggestions = BankCombinationMatcher.oneTransactionToManyReceipts(transaction, receipts, emptyList())
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.none { it.receiptIds.size > BankCombinationThresholds.MAX_COMBINATION_SIZE })
        assertTrue(suggestions.any { it.receiptIds.size == 4 && it.difference <= 0.001 })
    }
}