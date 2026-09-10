package com.example.ui

import com.example.data.BankMatchSuggestion
import com.example.data.BankReceiptLink
import com.example.data.BankReconciliationStatus
import com.example.data.BankRentAssignment
import com.example.data.BankSplitPaymentType
import com.example.data.BankTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankCompactDetailPolicyTest {
    private fun transaction(amount: Double = 1500.0, status: String = BankReconciliationStatus.OPEN) = BankTransaction(
        transactionId = "tx-synthetic",
        accountId = "account-synthetic",
        bookingDate = "2026-09-10",
        valueDate = "2026-09-10",
        amount = amount,
        counterparty = "Synthetic Partner",
        purpose = "Synthetic reference",
        reconciliationStatus = status
    )

    private fun assignment(id: String, amount: Double, type: String) = BankRentAssignment(
        assignmentId = id,
        transactionId = "tx-synthetic",
        propertyId = "property-synthetic",
        unitId = "unit-synthetic",
        rentMonth = "2026-09",
        tenantReference = "tenant-synthetic",
        allocatedAmount = amount,
        paymentType = type,
        createdAt = "2026-09-10T00:00:00Z",
        updatedAt = "2026-09-10T00:00:00Z"
    )

    @Test fun tapAndBackNavigationContractPreservesTransactionIdentity() {
        assertEquals("tx-synthetic", BankCompactDetailPolicy.navigationOpen("tx-synthetic"))
        assertNull(BankCompactDetailPolicy.navigationBack())
    }

    @Test fun detailHasExactlyFourRequiredQuickActions() {
        assertEquals(
            listOf("Beleg suchen", "Beleg anlegen", "Buchung aufteilen", "Kein Beleg erforderlich"),
            BankCompactDetailPolicy.quickActions.map { it.label }
        )
    }

    @Test fun matchScoreIsPassedThroughWithoutInventedPercentage() {
        val suggestion = BankMatchSuggestion("tx-synthetic", 1, 92, "HOCH", listOf("synthetic"))
        assertEquals(92, BankCompactDetailPolicy.visibleMatchScore(suggestion))
        assertNull(BankCompactDetailPolicy.visibleMatchScore(null))
    }

    @Test fun matchedReceiptAllocationProducesZeroRemaining() {
        val tx = transaction(amount = -64.57, status = BankReconciliationStatus.MATCHED)
        val link = BankReceiptLink(
            linkId = "link-synthetic",
            transactionId = tx.transactionId,
            receiptId = 1,
            allocatedAmount = 64.57,
            createdAt = "2026-09-10T00:00:00Z"
        )
        val summary = BankCompactDetailPolicy.allocationSummary(tx, listOf(link), emptyList())
        assertEquals(64.57, summary.allocated, 0.001)
        assertEquals(0.0, summary.remaining, 0.001)
    }

    @Test fun partialAllocationShowsCorrectRemainder() {
        val tx = transaction(amount = 1500.0, status = BankReconciliationStatus.PARTIAL)
        val summary = BankCompactDetailPolicy.allocationSummary(
            tx,
            emptyList(),
            listOf(assignment("rent", 1000.0, BankSplitPaymentType.RENT))
        )
        assertEquals(1000.0, summary.allocated, 0.001)
        assertEquals(500.0, summary.remaining, 0.001)
    }

    @Test fun rentAndDepositRemainSeparateAndVisible() {
        val summary = BankCompactDetailPolicy.allocationSummary(
            transaction(),
            emptyList(),
            listOf(
                assignment("rent", 1000.0, BankSplitPaymentType.RENT),
                assignment("deposit", 500.0, BankSplitPaymentType.DEPOSIT)
            )
        )
        assertEquals(1500.0, summary.allocated, 0.001)
        assertEquals(0.0, summary.remaining, 0.001)
        assertEquals(2, summary.splitLabels.size)
        assertTrue(summary.splitLabels.any { "Miete" in it })
        assertTrue(summary.splitLabels.any { "Kaution" in it })
    }
}
