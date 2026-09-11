package com.example.data

data class BankDatevPrecheckReason(
    val transactionId: String,
    val code: String,
    val message: String
)

data class BankDatevPrecheckSummary(
    val checked: Int,
    val potentiallyExportable: Int,
    val privateIgnored: Int,
    val transfers: Int,
    val noReceiptRequired: Int,
    val unresolved: Int,
    val done: Int,
    val reasons: List<BankDatevPrecheckReason>
)

object BankDatevPrecheckPolicy {
    fun summarize(transactions: List<BankTransaction>): BankDatevPrecheckSummary {
        val reasons = transactions.mapNotNull(::reason)
        val privateIgnored = transactions.count {
            BankTransactionClassification.normalize(it.classification) == BankTransactionClassification.PRIVATE_IGNORED
        }
        val transfers = transactions.count {
            BankTransactionClassification.normalize(it.classification) == BankTransactionClassification.TRANSFER
        }
        val noReceiptRequired = transactions.count {
            BankTransactionClassification.normalize(it.classification) == BankTransactionClassification.NORMAL &&
                it.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED
        }
        val potentiallyExportable = transactions.count { transaction ->
            BankTransactionClassification.normalize(transaction.classification) == BankTransactionClassification.NORMAL &&
                transaction.reconciliationStatus in setOf(
                    BankReconciliationStatus.MATCHED,
                    BankReconciliationStatus.NO_RECEIPT_REQUIRED
                )
        }
        val unresolved = transactions.count { transaction ->
            BankTransactionClassification.normalize(transaction.classification) == BankTransactionClassification.NORMAL &&
                transaction.reconciliationStatus in setOf(
                    BankReconciliationStatus.OPEN,
                    BankReconciliationStatus.PARTIAL,
                    BankReconciliationStatus.REVIEW
                )
        }
        return BankDatevPrecheckSummary(
            checked = transactions.size,
            potentiallyExportable = potentiallyExportable,
            privateIgnored = privateIgnored,
            transfers = transfers,
            noReceiptRequired = noReceiptRequired,
            unresolved = unresolved,
            done = transactions.count { it.reviewState == BankReviewState.DONE },
            reasons = reasons
        )
    }

    fun reason(transaction: BankTransaction): BankDatevPrecheckReason? {
        return when (BankTransactionClassification.normalize(transaction.classification)) {
            BankTransactionClassification.PRIVATE_IGNORED -> BankDatevPrecheckReason(
                transaction.transactionId,
                "BANK_PRIVATE_IGNORED",
                "Privat/ignoriert – wird niemals als betriebliche DATEV-Buchung exportiert."
            )
            BankTransactionClassification.TRANSFER -> BankDatevPrecheckReason(
                transaction.transactionId,
                "BANK_TRANSFER",
                "Umbuchung – wird nicht als normale Einnahme oder Ausgabe exportiert."
            )
            else -> when (transaction.reconciliationStatus) {
                BankReconciliationStatus.OPEN -> BankDatevPrecheckReason(
                    transaction.transactionId,
                    "BANK_UNRESOLVED",
                    "Noch offen – vor DATEV fachlich zuordnen oder ausdrücklich als ohne Beleg erforderlich behandeln."
                )
                BankReconciliationStatus.PARTIAL -> BankDatevPrecheckReason(
                    transaction.transactionId,
                    "BANK_PARTIAL",
                    "Nur teilweise zugeordnet – DATEV-Vorprüfung noch nicht abgeschlossen."
                )
                BankReconciliationStatus.REVIEW -> BankDatevPrecheckReason(
                    transaction.transactionId,
                    "BANK_REVIEW",
                    "Zur Prüfung markiert – vor DATEV klären."
                )
                else -> null
            }
        }
    }

    fun needsReviewInbox(transaction: BankTransaction): Boolean =
        BankTransactionClassification.normalize(transaction.classification) == BankTransactionClassification.NORMAL &&
            transaction.reviewState == BankReviewState.OPEN &&
            transaction.reconciliationStatus in setOf(
                BankReconciliationStatus.OPEN,
                BankReconciliationStatus.PARTIAL,
                BankReconciliationStatus.REVIEW
            )
}
