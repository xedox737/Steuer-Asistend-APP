package com.example.data

import androidx.room.withTransaction
import java.time.Instant

object BankBatchAction {
    const val PRIVATE = "PRIVATE"
    const val TRANSFER = "TRANSFER"
    const val NO_RECEIPT_REQUIRED = "NO_RECEIPT_REQUIRED"
    const val REVIEW_DONE = "REVIEW_DONE"
    const val REVIEW_OPEN = "REVIEW_OPEN"
    const val PROPERTY = "PROPERTY"
}

data class BankBatchConflictPreview(
    val selected: Int,
    val protected: Int,
    val eligible: Int,
    val protectedTransactionIds: List<String>
)

data class BankBatchResult(
    val changed: Int,
    val skipped: Int,
    val before: List<BankTransaction>
)

/** Central safety policy for list quick actions and batch actions. */
object BankBatchActionPolicy {
    fun preview(
        transactions: List<BankTransaction>,
        links: List<BankReceiptLink>,
        action: String,
        propertyId: String = ""
    ): BankBatchConflictPreview {
        val linkedIds = links.map { it.transactionId }.toSet()
        val protected = transactions.filter { transaction ->
            when (action) {
                BankBatchAction.PRIVATE, BankBatchAction.TRANSFER ->
                    transaction.transactionId in linkedIds || transaction.propertyId.isNotBlank() || transaction.unitId.isNotBlank()
                BankBatchAction.NO_RECEIPT_REQUIRED ->
                    transaction.transactionId in linkedIds || transaction.reconciliationStatus in setOf(BankReconciliationStatus.MATCHED, BankReconciliationStatus.PARTIAL)
                BankBatchAction.PROPERTY -> transaction.propertyId.isNotBlank() && transaction.propertyId != propertyId
                else -> false
            }
        }
        return BankBatchConflictPreview(
            selected = transactions.size,
            protected = protected.size,
            eligible = transactions.size - protected.size,
            protectedTransactionIds = protected.map { it.transactionId }
        )
    }
}

class BankBatchActionService(private val database: AppDatabase) {
    suspend fun apply(
        transactionIds: Set<String>,
        action: String,
        propertyId: String = "",
        noReceiptReason: String = "Batch: kein Beleg erforderlich",
        overwriteProtected: Boolean = false
    ): BankBatchResult = database.withTransaction {
        val dao = database.bankDao()
        val selected = transactionIds.mapNotNull { dao.getTransaction(it) }
        val links = dao.getAllLinks().filter { it.transactionId in transactionIds }
        val preview = BankBatchActionPolicy.preview(selected, links, action, propertyId)
        val protected = preview.protectedTransactionIds.toSet()
        val targets = if (overwriteProtected) selected else selected.filterNot { it.transactionId in protected }
        val now = Instant.now().toString()
        targets.forEach { current ->
            val updated = when (action) {
                BankBatchAction.PRIVATE, BankBatchAction.TRANSFER -> {
                    val classification = if (action == BankBatchAction.PRIVATE)
                        BankTransactionClassification.PRIVATE_IGNORED else BankTransactionClassification.TRANSFER
                    val decision = BankClassificationPolicy.classify(current.transactionId, classification, now = now)
                    current.copy(
                        classification = decision.classification,
                        transferCounterAccountId = decision.transferCounterAccountId,
                        linkedTransferTransactionId = decision.linkedTransferTransactionId,
                        reviewState = decision.reviewState,
                        updatedAt = now
                    )
                }
                BankBatchAction.NO_RECEIPT_REQUIRED -> current.copy(
                    reconciliationStatus = BankReconciliationStatus.NO_RECEIPT_REQUIRED,
                    noReceiptReason = noReceiptReason.trim(), reviewState = BankReviewState.DONE, updatedAt = now
                )
                BankBatchAction.REVIEW_DONE -> current.copy(reviewState = BankReviewState.DONE, updatedAt = now)
                BankBatchAction.REVIEW_OPEN -> current.copy(reviewState = BankReviewState.OPEN, updatedAt = now)
                BankBatchAction.PROPERTY -> current.copy(propertyId = propertyId, updatedAt = now)
                else -> current
            }
            if (updated != current) dao.upsertTransaction(updated)
        }
        BankBatchResult(targets.size, selected.size - targets.size, targets)
    }

    suspend fun restore(before: List<BankTransaction>) = database.withTransaction {
        before.forEach { database.bankDao().upsertTransaction(it) }
    }
}
