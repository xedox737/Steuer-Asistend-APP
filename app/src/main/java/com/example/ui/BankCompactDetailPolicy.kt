package com.example.ui

import com.example.data.BankMatchSuggestion
import com.example.data.BankReceiptLink
import com.example.data.BankRentAssignment
import com.example.data.BankSplitPaymentType
import com.example.data.BankTransaction
import com.example.data.BankTransactionSplitPolicy
import com.example.data.BankTransactionClassification
import com.example.data.BankReviewState

internal enum class BankQuickActionType(val label: String) {
    SEARCH_RECEIPT("Beleg suchen"),
    CREATE_RECEIPT("Beleg hochladen / anlegen"),
    SPLIT_TRANSACTION("Buchung aufteilen"),
    NO_RECEIPT_REQUIRED("Kein Beleg erforderlich")
}

internal data class BankDetailAllocationSummary(
    val allocated: Double,
    val remaining: Double,
    val splitLabels: List<String>
)

internal data class BankDetailActionLabels(
    val privateAction: String,
    val transferAction: String,
    val reviewAction: String,
    val reviewSubtitle: String
)

internal object BankDetailActionPolicy {
    fun labels(transaction: BankTransaction) = BankDetailActionLabels(
        privateAction = if (transaction.classification == BankTransactionClassification.PRIVATE_IGNORED) "Privat aufheben" else "Privat / ignorieren",
        transferAction = if (transaction.classification == BankTransactionClassification.TRANSFER) "Umbuchung aufheben" else "Als Umbuchung markieren",
        reviewAction = if (transaction.reviewState == BankReviewState.DONE) "Wieder öffnen" else "Als erledigt markieren",
        reviewSubtitle = if (transaction.reviewState == BankReviewState.DONE) "Erneut bearbeiten" else "Buchung ist geprüft"
    )
}

internal object BankCompactDetailPolicy {
    val quickActions: List<BankQuickActionType> = BankQuickActionType.entries

    fun visibleMatchScore(suggestion: BankMatchSuggestion?): Int? = suggestion?.score

    fun navigationOpen(transactionId: String): String = transactionId

    fun navigationBack(): String? = null

    fun allocationSummary(
        transaction: BankTransaction,
        links: List<BankReceiptLink>,
        assignments: List<BankRentAssignment>
    ): BankDetailAllocationSummary {
        val allocated = BankTransactionSplitPolicy.allocatedAmount(transaction.transactionId, links, assignments)
        return BankDetailAllocationSummary(
            allocated = allocated,
            remaining = (transaction.absoluteAmount - allocated).coerceAtLeast(0.0),
            splitLabels = assignments.map { "${it.allocatedAmount} → ${BankSplitPaymentType.label(it.paymentType)}" }
        )
    }
}
