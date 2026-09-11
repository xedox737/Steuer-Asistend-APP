package com.example.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Fachliche Klassifikation getrennt vom bestehenden Belegabgleich. */
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

data class BankTransactionClassificationRecord(
    val transactionId: String,
    val classification: String = BankTransactionClassification.NORMAL,
    val transferCounterAccountId: String = "",
    val linkedTransferTransactionId: String = "",
    val reviewState: String = BankReviewState.OPEN,
    val updatedAt: String = ""
)

data class BankClassificationDecision(
    val classification: String,
    val requiresReceiptReview: Boolean,
    val eligibleForReceiptMatching: Boolean,
    val eligibleForNormalDatevExport: Boolean,
    val reviewState: String
)

object BankClassificationPolicy {
    fun decision(
        transaction: BankTransaction,
        record: BankTransactionClassificationRecord? = null
    ): BankClassificationDecision {
        val classification = BankTransactionClassification.normalize(record?.classification.orEmpty())
        val special = classification != BankTransactionClassification.NORMAL
        return BankClassificationDecision(
            classification = classification,
            requiresReceiptReview = !special && transaction.reconciliationStatus !in setOf(
                BankReconciliationStatus.MATCHED,
                BankReconciliationStatus.NO_RECEIPT_REQUIRED
            ),
            eligibleForReceiptMatching = !special && transaction.reconciliationStatus != BankReconciliationStatus.NO_RECEIPT_REQUIRED,
            eligibleForNormalDatevExport = !special,
            reviewState = if (special) BankReviewState.DONE else record?.reviewState ?: BankReviewState.OPEN
        )
    }

    fun classify(
        transactionId: String,
        classification: String,
        transferCounterAccountId: String = "",
        linkedTransferTransactionId: String = "",
        now: String
    ): BankTransactionClassificationRecord {
        val normalized = BankTransactionClassification.normalize(classification)
        val transfer = normalized == BankTransactionClassification.TRANSFER
        return BankTransactionClassificationRecord(
            transactionId = transactionId,
            classification = normalized,
            transferCounterAccountId = if (transfer) transferCounterAccountId else "",
            linkedTransferTransactionId = if (transfer) linkedTransferTransactionId else "",
            reviewState = if (normalized == BankTransactionClassification.NORMAL) BankReviewState.OPEN else BankReviewState.DONE,
            updatedAt = now
        )
    }
}

object BankDatevClassificationGate {
    fun exclusionReason(record: BankTransactionClassificationRecord?): String? = when (
        BankTransactionClassification.normalize(record?.classification.orEmpty())
    ) {
        BankTransactionClassification.PRIVATE_IGNORED -> "Privat/ignoriert – nicht als betriebliche DATEV-Buchung exportieren."
        BankTransactionClassification.TRANSFER -> "Umbuchung – nicht als normale Einnahme/Ausgabe exportieren."
        else -> null
    }
}

data class BankTransferSuggestion(
    val transactionId: String,
    val counterTransactionId: String,
    val score: Int,
    val reasons: List<String>
)

/** Deterministische Phase-B-Vorbereitung: nur Vorschläge, niemals automatische Umbuchung. */
object BankTransferMatcher {
    fun suggestions(transaction: BankTransaction, candidates: List<BankTransaction>): List<BankTransferSuggestion> =
        candidates.asSequence()
            .filter { it.transactionId != transaction.transactionId }
            .filter { it.accountId != transaction.accountId }
            .filter { transaction.amount * it.amount < 0.0 }
            .mapNotNull { candidate -> score(transaction, candidate) }
            .sortedWith(compareByDescending<BankTransferSuggestion> { it.score }.thenBy { it.counterTransactionId })
            .take(8)
            .toList()

    private fun score(a: BankTransaction, b: BankTransaction): BankTransferSuggestion? {
        var score = 0
        val reasons = mutableListOf<String>()
        val amountDiff = abs(a.absoluteAmount - b.absoluteAmount)
        when {
            amountDiff <= 0.01 -> { score += 70; reasons += "Gegenbetrag stimmt exakt" }
            amountDiff <= 1.0 -> { score += 45; reasons += "Gegenbetrag nahezu gleich" }
            else -> return null
        }
        val days = runCatching { abs(ChronoUnit.DAYS.between(LocalDate.parse(a.bookingDate), LocalDate.parse(b.bookingDate))) }.getOrNull()
            ?: return null
        when {
            days == 0L -> { score += 25; reasons += "Gleiches Buchungsdatum" }
            days <= 2L -> { score += 18; reasons += "Buchungsdatum ±2 Tage" }
            days <= 5L -> { score += 8; reasons += "Buchungsdatum ±5 Tage" }
            else -> return null
        }
        if (a.currency.equals(b.currency, true)) score += 5 else return null
        return BankTransferSuggestion(a.transactionId, b.transactionId, score.coerceAtMost(100), reasons)
    }
}
