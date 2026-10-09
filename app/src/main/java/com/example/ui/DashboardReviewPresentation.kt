package com.example.ui

import com.example.data.*

internal data class DashboardReviewCounts(val receiptsToReview: Int, val missingBankReceipts: Int, val uncheckedBankEntries: Int)

internal object DashboardReviewPresentation {
    fun counts(receipts: List<Receipt>, transactions: List<BankTransaction>, links: List<BankReceiptLink>,
        assignments: List<BankRentAssignment>): DashboardReviewCounts {
        val active = receipts.filter { it.deletionStatus in setOf("", "ACTIVE") }
        val pending = active.count {
            it.pruefstatus !in setOf("GEPRUEFT", "KORRIGIERT") ||
                it.exportStatus in setOf("ENTWURF", "KI_VORSCHLAG", "ZU_PRUEFEN")
        }
        val validLinks = links.filter { link -> active.any { receipt ->
            if (link.receiptInternalId.isNotBlank()) link.receiptInternalId == receipt.internalId else link.receiptId == receipt.id
        } }
        val normal = transactions.filter { it.classification == BankTransactionClassification.NORMAL &&
            it.reconciliationStatus != BankReconciliationStatus.NO_RECEIPT_REQUIRED }
        val missing = normal.count { tx -> tx.category.isNotBlank() &&
            RentPaymentProjection.remainingAmount(tx, active, validLinks, assignments) > .01 }
        val unchecked = normal.count { tx -> tx.category.isBlank() &&
            tx.reconciliationStatus in setOf(BankReconciliationStatus.OPEN, BankReconciliationStatus.REVIEW, BankReconciliationStatus.PARTIAL) }
        return DashboardReviewCounts(pending, missing, unchecked)
    }
}
