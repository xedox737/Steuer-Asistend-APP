package com.example.ui

import com.example.data.BankReconciliationStatus
import com.example.data.BankTransaction
import java.time.LocalDate
import java.util.Locale

internal enum class BankCompactFilter {
    ALL,
    OPEN,
    MATCHED,
    PARTIAL,
    REVIEW,
    NO_RECEIPT_REQUIRED
}

internal data class BankCompactCounts(
    val all: Int,
    val open: Int,
    val matched: Int,
    val partial: Int,
    val review: Int,
    val noReceiptRequired: Int
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
        open = transactions.count { it.reconciliationStatus == BankReconciliationStatus.OPEN },
        matched = transactions.count { it.reconciliationStatus == BankReconciliationStatus.MATCHED },
        partial = transactions.count { it.reconciliationStatus == BankReconciliationStatus.PARTIAL },
        review = transactions.count { it.reconciliationStatus == BankReconciliationStatus.REVIEW },
        noReceiptRequired = transactions.count { it.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED }
    )

    fun filter(transactions: List<BankTransaction>, filter: BankCompactFilter): List<BankTransaction> = when (filter) {
        BankCompactFilter.ALL -> transactions
        BankCompactFilter.OPEN -> transactions.filter { it.reconciliationStatus == BankReconciliationStatus.OPEN }
        BankCompactFilter.MATCHED -> transactions.filter { it.reconciliationStatus == BankReconciliationStatus.MATCHED }
        BankCompactFilter.PARTIAL -> transactions.filter { it.reconciliationStatus == BankReconciliationStatus.PARTIAL }
        BankCompactFilter.REVIEW -> transactions.filter { it.reconciliationStatus == BankReconciliationStatus.REVIEW }
        BankCompactFilter.NO_RECEIPT_REQUIRED -> transactions.filter { it.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED }
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
        BankCompactFilter.OPEN -> "Offen"
        BankCompactFilter.MATCHED -> "Zugeordnet"
        BankCompactFilter.PARTIAL -> "Teilweise"
        BankCompactFilter.REVIEW -> "Prüfen"
        BankCompactFilter.NO_RECEIPT_REQUIRED -> "Kein Beleg nötig"
    }

    fun countFor(counts: BankCompactCounts, filter: BankCompactFilter): Int = when (filter) {
        BankCompactFilter.ALL -> counts.all
        BankCompactFilter.OPEN -> counts.open
        BankCompactFilter.MATCHED -> counts.matched
        BankCompactFilter.PARTIAL -> counts.partial
        BankCompactFilter.REVIEW -> counts.review
        BankCompactFilter.NO_RECEIPT_REQUIRED -> counts.noReceiptRequired
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
