package com.example.data

object BankUndoFieldSet {
    const val CLASSIFICATION = "CLASSIFICATION"
    const val RECONCILIATION = "RECONCILIATION"
}

data class BankUndoEntry(
    val transactionId: String,
    val fieldSet: String,
    val expectedUpdatedAt: String,
    val classification: String = BankTransactionClassification.NORMAL,
    val transferCounterAccountId: String = "",
    val linkedTransferTransactionId: String = "",
    val reviewState: String = BankReviewState.OPEN,
    val reconciliationStatus: String = BankReconciliationStatus.OPEN,
    val noReceiptReason: String = ""
)

data class BankUndoState(
    val label: String,
    val entries: List<BankUndoEntry>
)

data class BankUndoDecision(
    val restorable: List<BankUndoEntry>,
    val skipped: List<BankUndoEntry>
)

/**
 * Undo is only allowed while the transaction still has the exact updatedAt value
 * written by the action being undone. Newer user work is never overwritten.
 */
object BankUndoPolicy {
    fun decide(current: List<BankTransaction>, state: BankUndoState): BankUndoDecision {
        val byId = current.associateBy { it.transactionId }
        val restorable = mutableListOf<BankUndoEntry>()
        val skipped = mutableListOf<BankUndoEntry>()
        state.entries.distinctBy { it.transactionId }.forEach { entry ->
            val transaction = byId[entry.transactionId]
            if (transaction != null && transaction.updatedAt == entry.expectedUpdatedAt) {
                restorable += entry
            } else {
                skipped += entry
            }
        }
        return BankUndoDecision(restorable, skipped)
    }
}
