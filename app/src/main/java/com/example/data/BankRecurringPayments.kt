package com.example.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max

object RecurringCadence {
    const val MONTHLY = "MONTHLY"
    const val QUARTERLY = "QUARTERLY"
    const val HALF_YEARLY = "HALF_YEARLY"
    const val YEARLY = "YEARLY"
    const val IRREGULAR = "IRREGULAR"
}

object RecurringDirection {
    const val INCOME = "INCOME"
    const val EXPENSE = "EXPENSE"
}

object BankRecurringThresholds {
    const val MIN_OCCURRENCES_FOR_PATTERN = 3
    const val WEAK_EVIDENCE_OCCURRENCES = 2
    const val DAY_TOLERANCE = 5L
    const val EXPECTED_WINDOW_RADIUS_DAYS = 5L
    const val DUPLICATE_WINDOW_DAYS = 5L
    const val MAX_AMOUNT_VARIATION_RATIO = 0.12
    const val HIGH_VARIATION_RATIO = 0.25
}

data class ExpectedPaymentWindow(
    val fromDate: String,
    val toDate: String,
    val expectedDate: String
)

data class RecurringPaymentPattern(
    val patternId: String,
    val direction: String,
    val normalizedCounterparty: String,
    val purposeFingerprint: String,
    val typicalAmount: Double,
    val amountTolerance: Double,
    val cadence: String,
    val typicalDay: Int,
    val accountId: String = "",
    val propertyId: String = "",
    val occurrenceCount: Int,
    val confidence: Int,
    val lastOccurrence: String,
    val nextExpectedWindow: ExpectedPaymentWindow?,
    val reasons: List<String>,
    val outlierCount: Int = 0
)

data class RecurringPaymentAnalysis(
    val patterns: List<RecurringPaymentPattern>,
    val duplicateTransactionIds: Set<String>,
    val missingExpectedPatternIds: Set<String>
)

object BankRecurringPaymentDetector {
    fun detect(transactions: List<BankTransaction>, today: LocalDate? = null): RecurringPaymentAnalysis {
        val usable = transactions.mapNotNull { tx ->
            val date = runCatching { LocalDate.parse(tx.bookingDate) }.getOrNull() ?: return@mapNotNull null
            Observation(tx, date, identity(tx))
        }
        val patterns = usable.groupBy { it.identity }
            .values
            .mapNotNull(::buildPattern)
            .sortedWith(compareByDescending<RecurringPaymentPattern> { it.confidence }.thenBy { it.patternId })
        val duplicates = detectDuplicates(usable)
        val missing = if (today == null) emptySet() else patterns.filter { pattern ->
            val window = pattern.nextExpectedWindow ?: return@filter false
            runCatching { LocalDate.parse(window.toDate).isBefore(today) }.getOrDefault(false)
        }.map { it.patternId }.toSet()
        return RecurringPaymentAnalysis(patterns, duplicates, missing)
    }

    private fun buildPattern(items: List<Observation>): RecurringPaymentPattern? {
        if (items.size < BankRecurringThresholds.WEAK_EVIDENCE_OCCURRENCES) return null
        val sorted = items.sortedBy { it.date }
        val amounts = sorted.map { it.tx.absoluteAmount }
        val typical = median(amounts)
        val deviations = amounts.map { abs(it - typical) }
        val tolerance = max(1.0, median(deviations) * 2.5)
        val outlierThreshold = max(tolerance, typical * BankRecurringThresholds.MAX_AMOUNT_VARIATION_RATIO)
        val outliers = deviations.count { it > outlierThreshold }
        val cadence = inferCadence(sorted.map { it.date })
        val typicalDay = medianInt(sorted.map { it.date.dayOfMonth })
        val reasons = mutableListOf<String>()
        reasons += "${items.size} ähnliche Vorkommen"
        if (cadence != RecurringCadence.IRREGULAR) reasons += "Rhythmus $cadence erkannt"
        if (outliers > 0) reasons += "$outliers Betragsausreißer sichtbar"
        if (items.size < BankRecurringThresholds.MIN_OCCURRENCES_FOR_PATTERN) reasons += "Nur schwache Evidenz"

        val amountSpread = if (typical <= 0.0) 0.0 else (amounts.maxOrNull()!! - amounts.minOrNull()!!) / typical
        var confidence = when {
            items.size >= 6 -> 75
            items.size >= BankRecurringThresholds.MIN_OCCURRENCES_FOR_PATTERN -> 60
            else -> 35
        }
        confidence += when (cadence) {
            RecurringCadence.MONTHLY, RecurringCadence.QUARTERLY, RecurringCadence.YEARLY -> 15
            RecurringCadence.HALF_YEARLY -> 10
            else -> -15
        }
        confidence += when {
            amountSpread <= 0.02 -> 10
            amountSpread <= BankRecurringThresholds.MAX_AMOUNT_VARIATION_RATIO -> 5
            amountSpread >= BankRecurringThresholds.HIGH_VARIATION_RATIO -> -20
            else -> -5
        }
        confidence -= outliers * 5
        if (items.size < BankRecurringThresholds.MIN_OCCURRENCES_FOR_PATTERN) confidence = minOf(confidence, 49)

        val last = sorted.last().date
        val nextWindow = nextExpectedWindow(last, cadence, typicalDay)
        val first = sorted.first().tx
        val normalizedCounterparty = normalizeCounterparty(first)
        val fingerprint = purposeFingerprint(first.purpose)
        val patternId = "rec-${BankTransactionIdentity.sha256(listOf(
            direction(first), normalizedCounterparty, fingerprint, first.accountId, first.propertyId
        ).joinToString("|")).take(28)}"

        return RecurringPaymentPattern(
            patternId = patternId,
            direction = direction(first),
            normalizedCounterparty = normalizedCounterparty,
            purposeFingerprint = fingerprint,
            typicalAmount = typical,
            amountTolerance = max(tolerance, typical * 0.03),
            cadence = cadence,
            typicalDay = typicalDay,
            accountId = first.accountId,
            propertyId = first.propertyId,
            occurrenceCount = items.size,
            confidence = confidence.coerceIn(0, 100),
            lastOccurrence = last.toString(),
            nextExpectedWindow = nextWindow,
            reasons = reasons.distinct(),
            outlierCount = outliers
        )
    }

    private fun inferCadence(dates: List<LocalDate>): String {
        if (dates.size < 2) return RecurringCadence.IRREGULAR
        val monthDiffs = dates.zipWithNext { a, b ->
            ChronoUnit.MONTHS.between(YearMonth.from(a), YearMonth.from(b)).toInt()
        }.filter { it > 0 }
        if (monthDiffs.isEmpty()) return RecurringCadence.IRREGULAR
        val candidate = medianInt(monthDiffs)
        val matching = monthDiffs.count { abs(it - candidate) <= 0 }
        if (matching.toDouble() / monthDiffs.size < 0.67) return RecurringCadence.IRREGULAR
        return when (candidate) {
            1 -> RecurringCadence.MONTHLY
            3 -> RecurringCadence.QUARTERLY
            6 -> RecurringCadence.HALF_YEARLY
            12 -> RecurringCadence.YEARLY
            else -> RecurringCadence.IRREGULAR
        }
    }

    private fun nextExpectedWindow(last: LocalDate, cadence: String, typicalDay: Int): ExpectedPaymentWindow? {
        val months = when (cadence) {
            RecurringCadence.MONTHLY -> 1L
            RecurringCadence.QUARTERLY -> 3L
            RecurringCadence.HALF_YEARLY -> 6L
            RecurringCadence.YEARLY -> 12L
            else -> return null
        }
        val targetMonth = YearMonth.from(last).plusMonths(months)
        val expected = targetMonth.atDay(typicalDay.coerceAtMost(targetMonth.lengthOfMonth()))
        return ExpectedPaymentWindow(
            fromDate = expected.minusDays(BankRecurringThresholds.EXPECTED_WINDOW_RADIUS_DAYS).toString(),
            toDate = expected.plusDays(BankRecurringThresholds.EXPECTED_WINDOW_RADIUS_DAYS).toString(),
            expectedDate = expected.toString()
        )
    }

    private fun detectDuplicates(items: List<Observation>): Set<String> {
        val duplicateIds = mutableSetOf<String>()
        items.groupBy { it.identity }.values.forEach { group ->
            val sorted = group.sortedBy { it.date }
            sorted.forEachIndexed { index, current ->
                for (other in sorted.drop(index + 1)) {
                    val days = abs(ChronoUnit.DAYS.between(current.date, other.date))
                    if (days > BankRecurringThresholds.DUPLICATE_WINDOW_DAYS) break
                    val amountTol = max(1.0, current.tx.absoluteAmount * 0.02)
                    if (abs(current.tx.absoluteAmount - other.tx.absoluteAmount) <= amountTol) {
                        duplicateIds += current.tx.transactionId
                        duplicateIds += other.tx.transactionId
                    }
                }
            }
        }
        return duplicateIds
    }

    private fun identity(tx: BankTransaction): String = listOf(
        direction(tx), normalizeCounterparty(tx), purposeFingerprint(tx.purpose), tx.accountId, tx.propertyId
    ).joinToString("|")

    private fun direction(tx: BankTransaction): String = if (tx.amount >= 0.0) RecurringDirection.INCOME else RecurringDirection.EXPENSE

    internal fun normalizeCounterparty(tx: BankTransaction): String {
        val iban = tx.counterpartyIban.lowercase().replace(Regex("[^a-z0-9]"), "")
        if (iban.length >= 10) return "iban:$iban"
        return BankRuleEngine.norm(tx.counterparty)
    }

    internal fun purposeFingerprint(value: String): String {
        return BankRuleEngine.norm(value)
            .split(' ')
            .filter { token ->
                token.length >= 3 &&
                    !token.matches(Regex("\\d{4,}")) &&
                    !token.matches(Regex("\\d{1,2}")) &&
                    !token.matches(Regex("(jan|feb|maerz|marz|apr|mai|jun|jul|aug|sep|okt|nov|dez|januar|februar|maerz|april|juni|juli|august|september|oktober|november|dezember)"))
            }
            .take(8)
            .joinToString(" ")
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }

    private fun medianInt(values: List<Int>): Int {
        if (values.isEmpty()) return 0
        return values.sorted()[values.size / 2]
    }

    private data class Observation(val tx: BankTransaction, val date: LocalDate, val identity: String)
}
