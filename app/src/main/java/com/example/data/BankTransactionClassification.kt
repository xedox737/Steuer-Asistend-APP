package com.example.data

/**
 * Fachliche Klassifikation einer Bankbuchung. Sie ist absichtlich getrennt vom
 * Belegabgleich (reconciliationStatus), damit Privatbuchungen und Umbuchungen
 * niemals als fehlender Beleg oder normale Einnahme/Ausgabe behandelt werden.
 */
object BankTransactionClassification {
    const val NORMAL = "NORMAL"
    const val PRIVATE_IGNORED = "PRIVATE_IGNORED"
    const val TRANSFER = "TRANSFER"

    val all = setOf(NORMAL, PRIVATE_IGNORED, TRANSFER)

    fun normalize(value: String): String = value.takeIf { it in all } ?: NORMAL
}

object BankReviewState {
    const val OPEN = "OPEN"
    const val DONE = "DONE"
}

data class BankClassificationDecision(
    val classification: String,
    val requiresReceiptReview: Boolean,
    val eligibleForReceiptMatching: Boolean,
    val eligibleForNormalDatevExport: Boolean,
    val reviewState: String
)

object BankClassificationPolicy {
    fun decision(transaction: BankTransaction): BankClassificationDecision {
        val classification = BankTransactionClassification.normalize(transaction.classification)
        val special = classification != BankTransactionClassification.NORMAL
        return BankClassificationDecision(
            classification = classification,
            requiresReceiptReview = !special && transaction.reconciliationStatus !in setOf(
                BankReconciliationStatus.MATCHED,
                BankReconciliationStatus.NO_RECEIPT_REQUIRED
            ),
            eligibleForReceiptMatching = !special && transaction.reconciliationStatus != BankReconciliationStatus.NO_RECEIPT_REQUIRED,
            eligibleForNormalDatevExport = !special,
            reviewState = if (special) BankReviewState.DONE else transaction.reviewState
        )
    }

    fun classify(
        transaction: BankTransaction,
        classification: String,
        transferCounterAccountId: String = "",
        linkedTransferTransactionId: String = "",
        now: String
    ): BankTransaction {
        val normalized = BankTransactionClassification.normalize(classification)
        val transfer = normalized == BankTransactionClassification.TRANSFER
        return transaction.copy(
            classification = normalized,
            transferCounterAccountId = if (transfer) transferCounterAccountId else "",
            linkedTransferTransactionId = if (transfer) linkedTransferTransactionId else "",
            reviewState = if (normalized == BankTransactionClassification.NORMAL) BankReviewState.OPEN else BankReviewState.DONE,
            updatedAt = now
        )
    }

    fun reset(transaction: BankTransaction, now: String): BankTransaction = classify(
        transaction = transaction,
        classification = BankTransactionClassification.NORMAL,
        now = now
    )
}

/** Hard gate used by export preparation: special bank movements are never normal DATEV income/expense. */
object BankDatevClassificationGate {
    fun exclusionReason(transaction: BankTransaction): String? = when (
        BankTransactionClassification.normalize(transaction.classification)
    ) {
        BankTransactionClassification.PRIVATE_IGNORED -> "Privat/ignoriert – nicht als betriebliche DATEV-Buchung exportieren."
        BankTransactionClassification.TRANSFER -> "Umbuchung – nicht als normale Einnahme/Ausgabe exportieren."
        else -> null
    }
}
