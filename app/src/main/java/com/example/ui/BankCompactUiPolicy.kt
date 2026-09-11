package com.example.ui

import com.example.data.BankDatevPrecheckPolicy
import com.example.data.BankReconciliationStatus
import com.example.data.BankReviewState
import com.example.data.BankTransaction
import com.example.data.BankTransactionClassification
import com.example.data.BankClassificationPolicy
import java.time.LocalDate
import java.util.Locale

internal enum class BankCompactFilter {
    ALL,
    TO_REVIEW,
    OPEN,
    MATCHED,
    PARTIAL,
    REVIEW,
    PRIVATE_IGNORED,
    TRANSFER,
    NO_RECEIPT_REQUIRED,
    DONE
}

internal data class BankCompactCounts(
    val all: Int,
    val toReview: Int,
    val open: Int,
    val matched: Int,
    val partial: Int,
    val review: Int,
    val privateIgnored: Int,
    val transfers: Int,
    val noReceiptRequired: Int,
    val done: Int
)

internal data class BankCompactSummary(
    val incoming: Double,
    val outgoing: Double,
    val balance: Double
)

internal data class BankDateGroup(
    val key: String,
    val label: String,
    val transactions: List<BankTransaction>
)

internal object BankCompactUiPolicy {
    fun counts(transactions: List<BankTransaction>): BankCompactCounts = BankCompactCounts(
        all = transactions.size,
        toReview = transactions.count(BankDatevPrecheckPolicy::needsReviewInbox),
        open = transactions.count { BankClassificationPolicy.decision(it).requiresReceiptReview && it.reconciliationStatus == BankReconciliationStatus.OPEN },
        matched = transactions.count { it.reconciliationStatus == BankReconciliationStatus.MATCHED },
        partial = transactions.count { it.reconciliationStatus == BankReconciliationStatus.PARTIAL },
        review = transactions.count { it.reconciliationStatus == BankReconciliationStatus.REVIEW },
        privateIgnored = transactions.count { BankTransactionClassification.normalize(it.classification) == BankTransactionClassification.PRIVATE_IGNORED },
        transfers = transactions.count { BankTransactionClassification.normalize(it.classification) == BankTransactionClassification.TRANSFER },
        noReceiptRequired = transactions.count { it.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED },
        done = transactions.count { it.reviewState == BankReviewState.DONE }
    )

    fun filter(transactions: List<BankTransaction>, filter: BankCompactFilter): List<BankTransaction> = when (filter) {
        BankCompactFilter.ALL -> transactions
        BankCompactFilter.TO_REVIEW -> transactions.filter(BankDatevPrecheckPolicy::needsReviewInbox)
        BankCompactFilter.OPEN -> transactions.filter { BankClassificationPolicy.decision(it).requiresReceiptReview && it.reconciliationStatus == BankReconciliationStatus.OPEN }
        BankCompactFilter.MATCHED -> transactions.filter { it.reconciliationStatus == BankReconciliationStatus.MATCHED }
        BankCompactFilter.PARTIAL -> transactions.filter { it.reconciliationStatus == BankReconciliationStatus.PARTIAL }
        BankCompactFilter.REVIEW -> transactions.filter { it.reconciliationStatus == BankReconciliationStatus.REVIEW }
        BankCompactFilter.PRIVATE_IGNORED -> transactions.filter { BankTransactionClassification.normalize(it.classification) == BankTransactionClassification.PRIVATE_IGNORED }
        BankCompactFilter.TRANSFER -> transactions.filter { BankTransactionClassification.normalize(it.classification) == BankTransactionClassification.TRANSFER }
        BankCompactFilter.NO_RECEIPT_REQUIRED -> transactions.filter { it.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED }
        BankCompactFilter.DONE -> transactions.filter { it.reviewState == BankReviewState.DONE }
    }

    fun search(transactions: List<BankTransaction>, query: String): List<BankTransaction> {
        val needle = normalize(query)
        if (needle.isBlank()) return transactions
        return transactions.filter { transaction ->
            val haystack = normalize(
                listOf(
                    transaction.counterparty,
                    transaction.purpose,
                    transaction.bankReference,
                    transaction.amount.toString(),
                    moneySearchText(transaction.amount)
                ).joinToString(" ")
            )
            needle in haystack
        }
    }

    fun account(transactions: List<BankTransaction>, accountId: String?): List<BankTransaction> =
        if (accountId == null) transactions else transactions.filter { it.accountId == accountId }

    fun summary(transactions: List<BankTransaction>): BankCompactSummary {
        val incoming = transactions.asSequence().map { it.amount }.filter { it > 0.0 }.sum()
        val outgoing = transactions.asSequence().map { it.amount }.filter { it < 0.0 }.sum()
        return BankCompactSummary(incoming = incoming, outgoing = outgoing, balance = incoming + outgoing)
    }

    fun group(transactions: List<BankTransaction>, today: LocalDate = LocalDate.now()): List<BankDateGroup> =
        transactions.sortedWith(
            compareByDescending<BankTransaction> { parseDate(it.bookingDate) ?: LocalDate.MIN }
                .thenByDescending { it.transactionId }
        ).groupBy { it.bookingDate }
            .map { (dateText, values) ->
                val date = parseDate(dateText)
                val label = when (date) {
                    today -> "Heute, ${formatGerman(date)}"
                    today.minusDays(1) -> "Gestern, ${formatGerman(date)}"
                    null -> dateText.ifBlank { "Datum unbekannt" }
                    else -> formatGerman(date)
                }
                BankDateGroup(dateText, label, values)
            }
            .sortedByDescending { parseDate(it.key) ?: LocalDate.MIN }

    fun compactSubtitle(transaction: BankTransaction): String =
        transaction.purpose.ifBlank { transaction.bankReference.ifBlank { "Keine Beschreibung" } }

    fun statusLabel(status: String): String = when (status) {
        BankReconciliationStatus.MATCHED -> "Zugeordnet"
        BankReconciliationStatus.PARTIAL -> "Teilweise"
        BankReconciliationStatus.REVIEW -> "Prüfen"
        BankReconciliationStatus.NO_RECEIPT_REQUIRED -> "Kein Beleg nötig"
        else -> "Offen"
    }

    fun filterLabel(filter: BankCompactFilter): String = when (filter) {
        BankCompactFilter.ALL -> "Alle"
        BankCompactFilter.TO_REVIEW -> "Nur noch zu prüfen"
        BankCompactFilter.OPEN -> "Offen"
        BankCompactFilter.MATCHED -> "Zugeordnet"
        BankCompactFilter.PARTIAL -> "Teilweise"
        BankCompactFilter.REVIEW -> "Prüfen"
        BankCompactFilter.PRIVATE_IGNORED -> "Privat"
        BankCompactFilter.TRANSFER -> "Umbuchung"
        BankCompactFilter.NO_RECEIPT_REQUIRED -> "Kein Beleg nötig"
        BankCompactFilter.DONE -> "Erledigt"
    }

    fun countFor(counts: BankCompactCounts, filter: BankCompactFilter): Int = when (filter) {
        BankCompactFilter.ALL -> counts.all
        BankCompactFilter.TO_REVIEW -> counts.toReview
        BankCompactFilter.OPEN -> counts.open
        BankCompactFilter.MATCHED -> counts.matched
        BankCompactFilter.PARTIAL -> counts.partial
        BankCompactFilter.REVIEW -> counts.review
        BankCompactFilter.PRIVATE_IGNORED -> counts.privateIgnored
        BankCompactFilter.TRANSFER -> counts.transfers
        BankCompactFilter.NO_RECEIPT_REQUIRED -> counts.noReceiptRequired
        BankCompactFilter.DONE -> counts.done
    }

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

    private fun formatGerman(date: LocalDate): String = "%02d.%02d.%04d".format(date.dayOfMonth, date.monthValue, date.year)

    private fun moneySearchText(amount: Double): String = java.text.DecimalFormat("0.00").format(amount)

    private fun normalize(value: String): String = value.lowercase(Locale.GERMANY)
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace("ß", "ss")
        .replace(',', '.')
        .replace(Regex("\\s+"), " ")
        .trim()
}
