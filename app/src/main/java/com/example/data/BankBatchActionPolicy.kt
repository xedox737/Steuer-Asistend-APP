package com.example.data

enum class BankBatchAction {
    PRIVATE_IGNORED,
    TRANSFER,
    NO_RECEIPT_REQUIRED
}

data class BankBatchConflict(
    val transactionId: String,
    val reason: String
)

data class BankBatchActionPreview(
    val action: BankBatchAction,
    val selectedCount: Int,
    val eligibleTransactionIds: List<String>,
    val unchangedTransactionIds: List<String>,
    val conflicts: List<BankBatchConflict>
) {
    val eligibleCount: Int get() = eligibleTransactionIds.size
    val unchangedCount: Int get() = unchangedTransactionIds.size
    val conflictCount: Int get() = conflicts.size
}

/**
 * Pure preview policy for Phase-B multi-select actions.
 * It never silently replaces confirmed receipt links, transfer pairs or another special classification.
 */
object BankBatchActionPolicy {
    fun preview(
        selected: List<BankTransaction>,
        links: List<BankReceiptLink>,
        action: BankBatchAction
    ): BankBatchActionPreview {
        val linkedIds = links.mapTo(mutableSetOf()) { it.transactionId }
        val eligible = mutableListOf<String>()
        val unchanged = mutableListOf<String>()
        val conflicts = mutableListOf<BankBatchConflict>()

        selected.distinctBy { it.transactionId }.forEach { transaction ->
            val classification = BankTransactionClassification.normalize(transaction.classification)
            when (action) {
                BankBatchAction.PRIVATE_IGNORED -> when {
                    classification == BankTransactionClassification.PRIVATE_IGNORED -> unchanged += transaction.transactionId
                    classification != BankTransactionClassification.NORMAL -> conflicts += BankBatchConflict(transaction.transactionId, "Bereits als Umbuchung klassifiziert")
                    transaction.linkedTransferTransactionId.isNotBlank() -> conflicts += BankBatchConflict(transaction.transactionId, "Bestehendes Umbuchungspaar zuerst lösen")
                    transaction.transactionId in linkedIds -> conflicts += BankBatchConflict(transaction.transactionId, "Bestätigte Belegverknüpfung zuerst lösen")
                    else -> eligible += transaction.transactionId
                }

                BankBatchAction.TRANSFER -> when {
                    classification == BankTransactionClassification.TRANSFER -> unchanged += transaction.transactionId
                    classification != BankTransactionClassification.NORMAL -> conflicts += BankBatchConflict(transaction.transactionId, "Bereits als Privat/ignoriert klassifiziert")
                    transaction.linkedTransferTransactionId.isNotBlank() -> conflicts += BankBatchConflict(transaction.transactionId, "Bestehendes Umbuchungspaar wird nicht überschrieben")
                    transaction.transactionId in linkedIds -> conflicts += BankBatchConflict(transaction.transactionId, "Bestätigte Belegverknüpfung zuerst lösen")
                    else -> eligible += transaction.transactionId
                }

                BankBatchAction.NO_RECEIPT_REQUIRED -> when {
                    classification != BankTransactionClassification.NORMAL -> conflicts += BankBatchConflict(transaction.transactionId, "Sonderklassifikation Privat/Umbuchung bleibt unverändert")
                    transaction.transactionId in linkedIds -> conflicts += BankBatchConflict(transaction.transactionId, "Bestätigte Belegverknüpfung zuerst lösen")
                    transaction.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED -> unchanged += transaction.transactionId
                    transaction.reconciliationStatus == BankReconciliationStatus.MATCHED ||
                        transaction.reconciliationStatus == BankReconciliationStatus.PARTIAL -> conflicts += BankBatchConflict(transaction.transactionId, "Bestehende Beleg-/Teilzuordnung bleibt unverändert")
                    else -> eligible += transaction.transactionId
                }
            }
        }

        return BankBatchActionPreview(
            action = action,
            selectedCount = selected.distinctBy { it.transactionId }.size,
            eligibleTransactionIds = eligible,
            unchangedTransactionIds = unchanged,
            conflicts = conflicts
        )
    }
}
