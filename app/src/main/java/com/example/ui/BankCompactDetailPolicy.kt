package com.example.ui

import com.example.data.BankMatchSuggestion
import com.example.data.BankReceiptLink
import com.example.data.BankRentAssignment
import com.example.data.BankSplitPaymentType
import com.example.data.BankTransaction
import com.example.data.BankTransactionSplitPolicy

internal enum class BankQuickActionType(val label: String) {
    SEARCH_RECEIPT("Beleg suchen"),
    CREATE_RECEIPT("Beleg anlegen"),
    SPLIT_TRANSACTION("Buchung aufteilen"),
    NO_RECEIPT_REQUIRED("Kein Beleg erforderlich")
}

internal data class BankDetailAllocationSummary(
    val allocated: Double,
    val remaining: Double,
    val splitLabels: List<String>
)

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
