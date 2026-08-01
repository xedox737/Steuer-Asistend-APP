package com.example.util

import com.example.data.AccountingApprovalJson
import com.example.data.ConfirmedAllocationPolicy
import com.example.data.Receipt

data class DatevReceiptEligibilityIssue(
    val code: String,
    val message: String
)

object DatevReceiptEligibility {
    fun issues(receipt: Receipt): List<DatevReceiptEligibilityIssue> {
        val issues = mutableListOf<DatevReceiptEligibilityIssue>()
        if (receipt.internalId.isBlank()) {
            issues += issue("MISSING_INTERNAL_ID", "Stabile Beleg-ID fehlt.")
        }
        if (receipt.freigabestatus != "FREIGEGEBEN") {
            issues += issue("NOT_APPROVED", "DATEV-Aufteilung wurde noch nicht ausdrücklich freigegeben.")
            return issues
        }

        val amountCent = Math.round(receipt.bruttobetrag * 100.0)
        if (amountCent == 0L) {
            issues += issue("ZERO_AMOUNT", "Belegbetrag ist null.")
        }

        val allocations = AccountingApprovalJson.decodeAllocations(receipt.allocationsJson)
        val allocationValidation = ConfirmedAllocationPolicy.validate(amountCent, allocations)
        allocationValidation.errors.forEach {
            issues += issue("INVALID_ALLOCATION", it)
        }

        val proposals = AccountingApprovalJson.decodeBookingProposals(receipt.bookingProposalsJson)
        if (proposals.isEmpty()) {
            issues += issue("MISSING_BOOKING_PROPOSAL", "Bestätigter Buchungsvorschlag fehlt.")
        }
        if (allocations.map { it.id }.toSet() != proposals.map { it.id }.toSet()) {
            issues += issue(
                "PROPOSAL_ALLOCATION_MISMATCH",
                "Aufteilungen und Buchungsvorschläge gehören nicht vollständig zusammen."
            )
        }
        if (proposals.any { it.konto.isBlank() || it.gegenkonto.isBlank() }) {
            issues += issue("MISSING_ACCOUNT", "Sachkonto oder Gegenkonto fehlt.")
        }
        if (proposals.any { it.betragCent <= 0L }) {
            issues += issue("INVALID_PROPOSAL_AMOUNT", "Buchungsvorschlag enthält keinen positiven Absolutbetrag.")
        }
        return issues.distinctBy { it.code to it.message }
    }

    fun key(receipt: Receipt): String =
        receipt.internalId.takeIf { it.isNotBlank() } ?: "room:" + receipt.id

    private fun issue(code: String, message: String) =
        DatevReceiptEligibilityIssue(code, message)
}
