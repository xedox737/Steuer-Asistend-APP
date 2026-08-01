package com.example.util

import com.example.data.AccountingApprovalJson
import com.example.data.PersistedAllocation
import com.example.data.PersistedBookingProposal
import com.example.data.Receipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatevReceiptEligibilityTest {
    @Test
    fun openReceiptExplainsMissingApproval() {
        val issues = DatevReceiptEligibility.issues(receipt())

        assertEquals(listOf("NOT_APPROVED"), issues.map { it.code })
    }

    @Test
    fun approvedBalancedReceiptIsEligible() {
        val issues = DatevReceiptEligibility.issues(approvedReceipt())

        assertTrue(issues.isEmpty())
    }

    @Test
    fun changedAmountExplainsInvalidAllocation() {
        val issues = DatevReceiptEligibility.issues(
            approvedReceipt().copy(bruttobetrag = 120.0)
        )

        assertTrue(issues.any { it.code == "INVALID_ALLOCATION" })
        assertTrue(issues.any { it.message.contains("Belegbetrag") })
    }

    @Test
    fun missingAccountIsReported() {
        val receipt = approvedReceipt().copy(
            bookingProposalsJson = AccountingApprovalJson.encodeBookingProposals(
                listOf(PersistedBookingProposal("a", "", "70000", 10_000, ""))
            )
        )

        assertTrue(DatevReceiptEligibility.issues(receipt).any { it.code == "MISSING_ACCOUNT" })
    }

    private fun receipt() = Receipt(
        id = 3,
        aussteller = "Test GmbH",
        datum = "2026-08-01",
        uhrzeit = "",
        bruttobetrag = 100.0,
        hauptkategorie = "Werbungskosten",
        unterkategorie = "Reparatur",
        kontoNr = "4801",
        beschreibung = "Test",
        internalId = "receipt-3"
    )

    private fun approvedReceipt() = receipt().copy(
        allocationsJson = AccountingApprovalJson.encodeAllocations(
            listOf(PersistedAllocation("a", "Reparatur", 100.0, 10_000))
        ),
        bookingProposalsJson = AccountingApprovalJson.encodeBookingProposals(
            listOf(PersistedBookingProposal("a", "4801", "70000", 10_000, ""))
        ),
        freigabestatus = "FREIGEGEBEN"
    )
}
