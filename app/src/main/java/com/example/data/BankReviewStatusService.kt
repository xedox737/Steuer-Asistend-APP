package com.example.data

import java.time.Instant

/** Minimal final Phase-1 rules for the manual REVIEW state. */
class BankReviewStatusService(
    private val dao: BankDao,
    private val nowProvider: () -> String = { Instant.now().toString() }
) {
    suspend fun markForReview(transactionId: String): String? {
        val transaction = dao.getTransaction(transactionId) ?: return null
        val links = dao.getLinksForTransaction(transactionId)
        if (links.isNotEmpty()) return keepLinkedStatus(transaction, links)
        if (transaction.reconciliationStatus != BankReconciliationStatus.OPEN) {
            return transaction.reconciliationStatus
        }
        dao.updateTransactionStatus(
            transactionId,
            BankReconciliationStatus.REVIEW,
            "",
            nowProvider()
        )
        return BankReconciliationStatus.REVIEW
    }

    suspend fun reopen(transactionId: String): String? {
        val transaction = dao.getTransaction(transactionId) ?: return null
        val links = dao.getLinksForTransaction(transactionId)
        if (links.isNotEmpty()) return keepLinkedStatus(transaction, links)
        if (transaction.reconciliationStatus == BankReconciliationStatus.OPEN) {
            return BankReconciliationStatus.OPEN
        }
        dao.updateTransactionStatus(
            transactionId,
            BankReconciliationStatus.OPEN,
            "",
            nowProvider()
        )
        return BankReconciliationStatus.OPEN
    }

    private suspend fun keepLinkedStatus(
        transaction: BankTransaction,
        links: List<BankReceiptLink>
    ): String {
        val linkedStatus = BankLinkPolicy.statusFor(transaction, links)
        if (linkedStatus != transaction.reconciliationStatus) {
            dao.updateTransactionStatus(
                transaction.transactionId,
                linkedStatus,
                "",
                nowProvider()
            )
        }
        return linkedStatus
    }
}

object BankReviewUiPolicy {
    fun isReviewQueue(status: String): Boolean = status in setOf(
        BankReconciliationStatus.OPEN,
        BankReconciliationStatus.PARTIAL,
        BankReconciliationStatus.REVIEW
    )

    fun isMatched(status: String): Boolean = status == BankReconciliationStatus.MATCHED

    fun isNoReceiptRequired(status: String): Boolean =
        status == BankReconciliationStatus.NO_RECEIPT_REQUIRED

    fun isCompleted(status: String): Boolean = isMatched(status) || isNoReceiptRequired(status)
}
