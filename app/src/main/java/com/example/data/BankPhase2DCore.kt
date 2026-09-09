package com.example.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.round

object BankAllocationPolicy {
    const val MONEY_TOLERANCE = 0.01

    data class Remaining(
        val originalAmount: Double,
        val allocatedAmount: Double,
        val remainingAmount: Double
    )

    data class GuardResult(val allowed: Boolean, val normalizedAmount: Double, val reason: String = "")

    fun roundMoney(value: Double): Double = round(value * 100.0) / 100.0

    fun transactionRemaining(transaction: BankTransaction, links: List<BankReceiptLink>): Remaining {
        val allocated = links.asSequence()
            .filter { it.transactionId == transaction.transactionId && it.status == BankLinkStatus.CONFIRMED }
            .sumOf { it.allocatedAmount }
            .let(::roundMoney)
        val original = roundMoney(transaction.absoluteAmount)
        return Remaining(original, allocated, roundMoney((original - allocated).coerceAtLeast(0.0)))
    }

    fun receiptRemaining(receipt: Receipt, links: List<BankReceiptLink>): Remaining {
        val allocated = links.asSequence()
            .filter {
                it.status == BankLinkStatus.CONFIRMED &&
                    (it.receiptId == receipt.id || (receipt.internalId.isNotBlank() && it.receiptInternalId == receipt.internalId))
            }
            .sumOf { it.allocatedAmount }
            .let(::roundMoney)
        val original = roundMoney(receipt.bruttobetrag)
        return Remaining(original, allocated, roundMoney((original - allocated).coerceAtLeast(0.0)))
    }

    fun guardAllocation(
        transaction: BankTransaction,
        receipt: Receipt,
        amount: Double,
        links: List<BankReceiptLink>
    ): GuardResult {
        if (!amount.isFinite() || amount <= 0.0) return GuardResult(false, 0.0, "Allocation muss positiv und endlich sein.")
        val normalized = roundMoney(amount)
        val tx = transactionRemaining(transaction, links)
        val receiptRemaining = receiptRemaining(receipt, links)
        if (normalized > tx.remainingAmount + MONEY_TOLERANCE) {
            return GuardResult(false, normalized, "Allocation überschreitet den Restbetrag der Bankbuchung.")
        }
        if (normalized > receiptRemaining.remainingAmount + MONEY_TOLERANCE) {
            return GuardResult(false, normalized, "Allocation überschreitet den Restbetrag des Belegs.")
        }
        return GuardResult(true, normalized)
    }
}

object BankCombinationSuggestionType {
    const val ONE_TO_MANY = "ONE_TO_MANY"
    const val MANY_TO_ONE = "MANY_TO_ONE"
    const val PARTIAL = "PARTIAL"
    const val EXACT_COMBINATION = "EXACT_COMBINATION"
    const val NEAR_COMBINATION = "NEAR_COMBINATION"
    const val CONFLICT = "CONFLICT"
}

object BankCombinationConflict {
    const val MULTIPLE_COMBINATIONS = "MULTIPLE_COMBINATIONS"
    const val PROPERTY_CONFLICT = "PROPERTY_CONFLICT"
    const val DIRECTION_CONFLICT = "DIRECTION_CONFLICT"
    const val OVER_ALLOCATION = "OVER_ALLOCATION"
    const val DUPLICATE_RECEIPT = "DUPLICATE_RECEIPT"
    const val SPECIAL_CLASSIFICATION = "SPECIAL_CLASSIFICATION"
}

data class BankProposedAllocation(
    val transactionId: String,
    val receiptId: Int,
    val receiptInternalId: String = "",
    val amount: Double
)

data class BankCombinationSuggestion(
    val suggestionId: String,
    val transactionIds: List<String>,
    val receiptIds: List<Int>,
    val allocations: List<BankProposedAllocation>,
    val targetAmount: Double,
    val matchedAmount: Double,
    val difference: Double,
    val score: Int,
    val confidence: String,
    val reasons: List<String>,
    val conflicts: List<String>,
    val suggestionType: String
)

object BankCombinationThresholds {
    const val MAX_COMBINATION_SIZE = 4
    const val MAX_CANDIDATES_PER_SIDE = 12
    const val TOP_RESULTS = 8
    const val NEAR_ABSOLUTE_EUR = 1.0
    const val NEAR_RATIO = 0.02
    const val DATE_WINDOW_DAYS = 45L
    const val OLD_DATE_DAYS = 90L
    const val HIGH_SCORE = 85
    const val MEDIUM_SCORE = 65
}

object BankCombinationMatcher {
    fun oneTransactionToManyReceipts(
        transaction: BankTransaction,
        receipts: List<Receipt>,
        links: List<BankReceiptLink>
    ): List<BankCombinationSuggestion> {
        if (transaction.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED) return emptyList()
        val txRemaining = BankAllocationPolicy.transactionRemaining(transaction, links).remainingAmount
        if (txRemaining <= BankAllocationPolicy.MONEY_TOLERANCE) return emptyList()
        val candidates = receipts.asSequence()
            .filter { BankAllocationPolicy.receiptRemaining(it, links).remainingAmount > BankAllocationPolicy.MONEY_TOLERANCE }
            .filter { directionMatches(transaction, it) }
            .filter { propertyCompatible(transaction.propertyId, it.propertyId) }
            .filter { dateDistance(transaction.bookingDate, it.datum) <= BankCombinationThresholds.OLD_DATE_DAYS }
            .sortedWith(compareBy<Receipt> { dateDistance(transaction.bookingDate, it.datum) }.thenBy { it.id })
            .take(BankCombinationThresholds.MAX_CANDIDATES_PER_SIDE)
            .toList()

        val raw = mutableListOf<BankCombinationSuggestion>()
        for (size in 2..minOf(BankCombinationThresholds.MAX_COMBINATION_SIZE, candidates.size)) {
            combinations(candidates, size).forEach { group ->
                val remaining = group.map { it to BankAllocationPolicy.receiptRemaining(it, links).remainingAmount }
                val sum = BankAllocationPolicy.roundMoney(remaining.sumOf { it.second })
                val diff = BankAllocationPolicy.roundMoney(abs(txRemaining - sum))
                if (!withinNearTolerance(txRemaining, diff)) return@forEach
                val allocations = allocateOneToMany(transaction, remaining, txRemaining)
                raw += buildSuggestion(
                    transactionIds = listOf(transaction.transactionId),
                    receiptIds = group.map { it.id },
                    allocations = allocations,
                    targetAmount = txRemaining,
                    matchedAmount = allocations.sumOf { it.amount },
                    difference = diff,
                    baseType = BankCombinationSuggestionType.ONE_TO_MANY,
                    dates = group.map { it.datum } + transaction.bookingDate,
                    propertyConflict = group.any { !propertyCompatibleStrict(transaction.propertyId, it.propertyId) }
                )
            }
        }
        return markAmbiguityAndRank(raw)
    }

    fun manyTransactionsToOneReceipt(
        receipt: Receipt,
        transactions: List<BankTransaction>,
        links: List<BankReceiptLink>
    ): List<BankCombinationSuggestion> {
        val receiptRemaining = BankAllocationPolicy.receiptRemaining(receipt, links).remainingAmount
        if (receiptRemaining <= BankAllocationPolicy.MONEY_TOLERANCE) return emptyList()
        val candidates = transactions.asSequence()
            .filter { it.reconciliationStatus != BankReconciliationStatus.NO_RECEIPT_REQUIRED }
            .filter { BankAllocationPolicy.transactionRemaining(it, links).remainingAmount > BankAllocationPolicy.MONEY_TOLERANCE }
            .filter { directionMatches(it, receipt) }
            .filter { propertyCompatible(it.propertyId, receipt.propertyId) }
            .filter { dateDistance(it.bookingDate, receipt.datum) <= BankCombinationThresholds.OLD_DATE_DAYS }
            .sortedWith(compareBy<BankTransaction> { dateDistance(it.bookingDate, receipt.datum) }.thenBy { it.transactionId })
            .take(BankCombinationThresholds.MAX_CANDIDATES_PER_SIDE)
            .toList()

        val raw = mutableListOf<BankCombinationSuggestion>()
        for (size in 2..minOf(BankCombinationThresholds.MAX_COMBINATION_SIZE, candidates.size)) {
            combinations(candidates, size).forEach { group ->
                val amounts = group.map { it to BankAllocationPolicy.transactionRemaining(it, links).remainingAmount }
                val sum = BankAllocationPolicy.roundMoney(amounts.sumOf { it.second })
                val diff = BankAllocationPolicy.roundMoney(abs(receiptRemaining - sum))
                if (!withinNearTolerance(receiptRemaining, diff)) return@forEach
                val allocations = allocateManyToOne(receipt, amounts, receiptRemaining)
                raw += buildSuggestion(
                    transactionIds = group.map { it.transactionId },
                    receiptIds = listOf(receipt.id),
                    allocations = allocations,
                    targetAmount = receiptRemaining,
                    matchedAmount = allocations.sumOf { it.amount },
                    difference = diff,
                    baseType = BankCombinationSuggestionType.MANY_TO_ONE,
                    dates = group.map { it.bookingDate } + receipt.datum,
                    propertyConflict = group.any { !propertyCompatibleStrict(it.propertyId, receipt.propertyId) }
                )
            }
        }
        return markAmbiguityAndRank(raw)
    }

    fun partialSuggestion(transaction: BankTransaction, receipt: Receipt, links: List<BankReceiptLink>): BankCombinationSuggestion? {
        if (!directionMatches(transaction, receipt)) return null
        if (!propertyCompatible(transaction.propertyId, receipt.propertyId)) return null
        val tx = BankAllocationPolicy.transactionRemaining(transaction, links).remainingAmount
        val receiptRemaining = BankAllocationPolicy.receiptRemaining(receipt, links).remainingAmount
        val amount = BankAllocationPolicy.roundMoney(minOf(tx, receiptRemaining))
        if (amount <= BankAllocationPolicy.MONEY_TOLERANCE) return null
        if (abs(tx - receiptRemaining) <= BankAllocationPolicy.MONEY_TOLERANCE) return null
        val allocation = BankProposedAllocation(transaction.transactionId, receipt.id, receipt.internalId, amount)
        return buildSuggestion(
            listOf(transaction.transactionId), listOf(receipt.id), listOf(allocation),
            targetAmount = maxOf(tx, receiptRemaining), matchedAmount = amount,
            difference = BankAllocationPolicy.roundMoney(abs(tx - receiptRemaining)),
            baseType = BankCombinationSuggestionType.PARTIAL,
            dates = listOf(transaction.bookingDate, receipt.datum),
            propertyConflict = !propertyCompatibleStrict(transaction.propertyId, receipt.propertyId)
        )
    }

    private fun allocateOneToMany(
        transaction: BankTransaction,
        receipts: List<Pair<Receipt, Double>>,
        target: Double
    ): List<BankProposedAllocation> {
        var remaining = target
        return receipts.mapNotNull { (receipt, available) ->
            val amount = BankAllocationPolicy.roundMoney(minOf(available, remaining))
            remaining = BankAllocationPolicy.roundMoney((remaining - amount).coerceAtLeast(0.0))
            amount.takeIf { it > BankAllocationPolicy.MONEY_TOLERANCE }?.let {
                BankProposedAllocation(transaction.transactionId, receipt.id, receipt.internalId, it)
            }
        }
    }

    private fun allocateManyToOne(
        receipt: Receipt,
        transactions: List<Pair<BankTransaction, Double>>,
        target: Double
    ): List<BankProposedAllocation> {
        var remaining = target
        return transactions.mapNotNull { (transaction, available) ->
            val amount = BankAllocationPolicy.roundMoney(minOf(available, remaining))
            remaining = BankAllocationPolicy.roundMoney((remaining - amount).coerceAtLeast(0.0))
            amount.takeIf { it > BankAllocationPolicy.MONEY_TOLERANCE }?.let {
                BankProposedAllocation(transaction.transactionId, receipt.id, receipt.internalId, it)
            }
        }
    }

    private fun buildSuggestion(
        transactionIds: List<String>, receiptIds: List<Int>, allocations: List<BankProposedAllocation>,
        targetAmount: Double, matchedAmount: Double, difference: Double, baseType: String,
        dates: List<String>, propertyConflict: Boolean
    ): BankCombinationSuggestion {
        val exact = difference <= BankAllocationPolicy.MONEY_TOLERANCE
        var score = if (exact) 92 else 72
        val reasons = mutableListOf(if (exact) "Restbeträge ergeben exakt die Zielsumme." else "Restbeträge liegen innerhalb der Kombinationstoleranz.")
        val maxDateDistance = dates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.let { parsed ->
            if (parsed.size < 2) 0L else ChronoUnit.DAYS.between(parsed.minOrNull(), parsed.maxOrNull())
        }
        when {
            maxDateDistance <= 7 -> { score += 6; reasons += "Zeitlicher Zusammenhang ist stark." }
            maxDateDistance <= BankCombinationThresholds.DATE_WINDOW_DAYS -> { score += 2; reasons += "Zeitlicher Zusammenhang ist plausibel." }
            else -> { score -= 18; reasons += "Großer zeitlicher Abstand senkt die Plausibilität." }
        }
        val conflicts = mutableListOf<String>()
        if (propertyConflict) { conflicts += BankCombinationConflict.PROPERTY_CONFLICT; score -= 35 }
        val type = when {
            conflicts.isNotEmpty() -> BankCombinationSuggestionType.CONFLICT
            baseType == BankCombinationSuggestionType.PARTIAL -> BankCombinationSuggestionType.PARTIAL
            exact -> BankCombinationSuggestionType.EXACT_COMBINATION
            else -> BankCombinationSuggestionType.NEAR_COMBINATION
        }
        val normalizedScore = score.coerceIn(0, 100)
        val confidence = when {
            conflicts.isNotEmpty() -> "NIEDRIG"
            normalizedScore >= BankCombinationThresholds.HIGH_SCORE -> "HOCH"
            normalizedScore >= BankCombinationThresholds.MEDIUM_SCORE -> "MITTEL"
            else -> "NIEDRIG"
        }
        val idBasis = transactionIds.sorted().joinToString(",") + "|" + receiptIds.sorted().joinToString(",") + "|" + allocations.joinToString { "${it.transactionId}:${it.receiptId}:${it.amount}" }
        return BankCombinationSuggestion(
            suggestionId = "comb-" + BankTransactionIdentity.sha256(idBasis).take(28),
            transactionIds = transactionIds.sorted(), receiptIds = receiptIds.sorted(), allocations = allocations,
            targetAmount = BankAllocationPolicy.roundMoney(targetAmount), matchedAmount = BankAllocationPolicy.roundMoney(matchedAmount),
            difference = BankAllocationPolicy.roundMoney(difference), score = normalizedScore, confidence = confidence,
            reasons = reasons, conflicts = conflicts, suggestionType = type
        )
    }

    private fun markAmbiguityAndRank(input: List<BankCombinationSuggestion>): List<BankCombinationSuggestion> {
        val ranked = input.sortedWith(compareByDescending<BankCombinationSuggestion> { it.score }.thenBy { it.difference }.thenBy { it.suggestionId })
        if (ranked.size < 2) return ranked.take(BankCombinationThresholds.TOP_RESULTS)
        val top = ranked.first()
        val ambiguousIds = ranked.filter { abs(it.score - top.score) <= 2 && abs(it.difference - top.difference) <= BankAllocationPolicy.MONEY_TOLERANCE }
            .map { it.suggestionId }.toSet()
        return ranked.take(BankCombinationThresholds.TOP_RESULTS).map { suggestion ->
            if (suggestion.suggestionId !in ambiguousIds || ambiguousIds.size < 2) suggestion
            else suggestion.copy(
                confidence = "NIEDRIG",
                conflicts = (suggestion.conflicts + BankCombinationConflict.MULTIPLE_COMBINATIONS).distinct(),
                suggestionType = BankCombinationSuggestionType.CONFLICT,
                reasons = suggestion.reasons + "Mehrere gleich gute Kombinationen vorhanden; keine automatische Auswahl."
            )
        }
    }

    private fun withinNearTolerance(target: Double, difference: Double): Boolean =
        difference <= maxOf(BankCombinationThresholds.NEAR_ABSOLUTE_EUR, target * BankCombinationThresholds.NEAR_RATIO)

    private fun propertyCompatible(a: String, b: String): Boolean = a.isBlank() || b.isBlank() || a == b
    private fun propertyCompatibleStrict(a: String, b: String): Boolean = a.isBlank() || b.isBlank() || a == b

    private fun directionMatches(transaction: BankTransaction, receipt: Receipt): Boolean {
        val incomeReceipt = receipt.hauptkategorie.equals("Miete, Nebenkosten & Kaution", true) || receipt.hauptkategorie.equals("Sonstige Einnahmen", true)
        return if (transaction.isIncome) incomeReceipt else !incomeReceipt
    }

    private fun dateDistance(a: String, b: String): Long = runCatching {
        abs(ChronoUnit.DAYS.between(LocalDate.parse(a), LocalDate.parse(b)))
    }.getOrDefault(Long.MAX_VALUE)

    private fun <T> combinations(values: List<T>, size: Int): List<List<T>> {
        if (size <= 0 || size > values.size) return emptyList()
        val result = mutableListOf<List<T>>()
        fun walk(start: Int, current: MutableList<T>) {
            if (current.size == size) { result += current.toList(); return }
            for (i in start until values.size) {
                current += values[i]
                walk(i + 1, current)
                current.removeAt(current.lastIndex)
            }
        }
        walk(0, mutableListOf())
        return result
    }
}

object BankReviewType {
    const val SAFE_SUGGESTION = "SAFE_SUGGESTION"
    const val MISSING_RECEIPT = "MISSING_RECEIPT"
    const val MULTIPLE_CANDIDATES = "MULTIPLE_CANDIDATES"
    const val COMBINATION_SUGGESTION = "COMBINATION_SUGGESTION"
    const val PARTIAL_PAYMENT = "PARTIAL_PAYMENT"
    const val RENT_REVIEW = "RENT_REVIEW"
    const val LOAN_REVIEW = "LOAN_REVIEW"
    const val RECURRING_REVIEW = "RECURRING_REVIEW"
    const val POSSIBLE_DUPLICATE = "POSSIBLE_DUPLICATE"
    const val AMOUNT_CONFLICT = "AMOUNT_CONFLICT"
    const val PROPERTY_CONFLICT = "PROPERTY_CONFLICT"
    const val MANUAL_REVIEW = "MANUAL_REVIEW"
}

data class BankReviewItem(
    val stableKey: String,
    val type: String,
    val priority: Int,
    val transactionIds: List<String>,
    val receiptIds: List<Int>,
    val propertyId: String = "",
    val amount: Double = 0.0,
    val remainingAmount: Double = 0.0,
    val score: Int = 0,
    val confidence: String = "NIEDRIG",
    val title: String,
    val explanation: String,
    val reasons: List<String> = emptyList(),
    val conflicts: List<String> = emptyList(),
    val availableActions: List<String> = emptyList(),
    val bookingDate: String = ""
)

object BankReviewQueueBuilder {
    fun build(
        transactions: List<BankTransaction>,
        receipts: List<Receipt>,
        links: List<BankReceiptLink>,
        oneToOne: Map<String, BankMatchSuggestion>,
        combinations: List<BankCombinationSuggestion>,
        rentAssignments: List<BankRentAssignment> = emptyList(),
        loanAssignments: List<BankLoanAssignment> = emptyList(),
        recurringPatterns: List<BankRecurringPattern> = emptyList()
    ): List<BankReviewItem> {
        val items = mutableListOf<BankReviewItem>()
        val rentTx = rentAssignments.map { it.transactionId }.toSet()
        val loanTx = loanAssignments.map { it.transactionId }.toSet()

        transactions.forEach { tx ->
            if (tx.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED || tx.reconciliationStatus == BankReconciliationStatus.MATCHED) return@forEach
            val remaining = BankAllocationPolicy.transactionRemaining(tx, links).remainingAmount
            val suggestion = oneToOne[tx.transactionId]
            val txCombinations = combinations.filter { tx.transactionId in it.transactionIds }
            val propertyConflict = txCombinations.any { BankCombinationConflict.PROPERTY_CONFLICT in it.conflicts }
            val multiple = txCombinations.any { BankCombinationConflict.MULTIPLE_COMBINATIONS in it.conflicts }
            val type = when {
                tx.transactionId in rentTx && tx.reconciliationStatus == BankReconciliationStatus.REVIEW -> BankReviewType.RENT_REVIEW
                tx.transactionId in loanTx && tx.reconciliationStatus == BankReconciliationStatus.REVIEW -> BankReviewType.LOAN_REVIEW
                propertyConflict -> BankReviewType.PROPERTY_CONFLICT
                multiple -> BankReviewType.MULTIPLE_CANDIDATES
                tx.reconciliationStatus == BankReconciliationStatus.PARTIAL -> BankReviewType.PARTIAL_PAYMENT
                tx.reconciliationStatus == BankReconciliationStatus.REVIEW -> BankReviewType.MANUAL_REVIEW
                txCombinations.isNotEmpty() -> BankReviewType.COMBINATION_SUGGESTION
                suggestion != null && suggestion.confidence == "HOCH" -> BankReviewType.SAFE_SUGGESTION
                suggestion != null -> BankReviewType.MULTIPLE_CANDIDATES
                tx.amount < 0 -> BankReviewType.MISSING_RECEIPT
                else -> BankReviewType.MANUAL_REVIEW
            }
            val bestCombination = txCombinations.maxWithOrNull(compareBy<BankCombinationSuggestion> { it.score }.thenByDescending { -it.difference })
            val score = bestCombination?.score ?: suggestion?.score ?: 0
            val conflicts = bestCombination?.conflicts.orEmpty()
            items += BankReviewItem(
                stableKey = stableKey(type, listOf(tx.transactionId), bestCombination?.receiptIds ?: suggestion?.let { listOf(it.receiptId) }.orEmpty()),
                type = type,
                priority = priority(type, remaining, conflicts),
                transactionIds = listOf(tx.transactionId),
                receiptIds = bestCombination?.receiptIds ?: suggestion?.let { listOf(it.receiptId) }.orEmpty(),
                propertyId = tx.propertyId,
                amount = tx.absoluteAmount,
                remainingAmount = remaining,
                score = score,
                confidence = bestCombination?.confidence ?: suggestion?.confidence ?: "NIEDRIG",
                title = title(type),
                explanation = explanation(type),
                reasons = bestCombination?.reasons ?: suggestion?.reasons.orEmpty(),
                conflicts = conflicts,
                availableActions = actions(type),
                bookingDate = tx.bookingDate
            )
        }

        recurringPatterns.filter { it.enabled && it.confidence < 85 }.forEach { pattern ->
            items += BankReviewItem(
                stableKey = "review-rec-${pattern.patternId}", type = BankReviewType.RECURRING_REVIEW,
                priority = 45, transactionIds = emptyList(), receiptIds = emptyList(), propertyId = pattern.propertyId,
                amount = pattern.typicalAmount, remainingAmount = pattern.typicalAmount, score = pattern.confidence,
                confidence = if (pattern.confidence >= 65) "MITTEL" else "NIEDRIG",
                title = "Wiederkehrende Zahlung prüfen", explanation = "Erkanntes Muster benötigt Nutzerprüfung.",
                reasons = pattern.reasonsText.split(" | ").filter { it.isNotBlank() },
                availableActions = listOf("PATTERN_REVIEW", "IGNORE")
            )
        }

        return items.distinctBy { it.stableKey }
            .sortedWith(compareByDescending<BankReviewItem> { it.priority }.thenByDescending { it.bookingDate }.thenBy { it.stableKey })
    }

    private fun priority(type: String, remaining: Double, conflicts: List<String>): Int {
        val base = when (type) {
            BankReviewType.PROPERTY_CONFLICT -> 100
            BankReviewType.AMOUNT_CONFLICT -> 95
            BankReviewType.POSSIBLE_DUPLICATE -> 92
            BankReviewType.MULTIPLE_CANDIDATES -> 90
            BankReviewType.LOAN_REVIEW, BankReviewType.RENT_REVIEW -> 85
            BankReviewType.MANUAL_REVIEW -> 80
            BankReviewType.MISSING_RECEIPT -> 70
            BankReviewType.PARTIAL_PAYMENT -> 65
            BankReviewType.COMBINATION_SUGGESTION -> 55
            BankReviewType.RECURRING_REVIEW -> 45
            BankReviewType.SAFE_SUGGESTION -> 30
            else -> 20
        }
        return (base + if (remaining >= 1000.0) 5 else 0 + if (conflicts.isNotEmpty()) 5 else 0).coerceAtMost(110)
    }

    private fun stableKey(type: String, tx: List<String>, receipts: List<Int>): String =
        "review-" + BankTransactionIdentity.sha256("$type|${tx.sorted()}|${receipts.sorted()}").take(28)

    private fun title(type: String): String = when (type) {
        BankReviewType.SAFE_SUGGESTION -> "Sicherer Zuordnungsvorschlag"
        BankReviewType.MISSING_RECEIPT -> "Beleg fehlt"
        BankReviewType.MULTIPLE_CANDIDATES -> "Mehrere plausible Kandidaten"
        BankReviewType.COMBINATION_SUGGESTION -> "Sammelzahlungs-Vorschlag"
        BankReviewType.PARTIAL_PAYMENT -> "Teilzahlung / Restbetrag"
        BankReviewType.RENT_REVIEW -> "Mietzahlung prüfen"
        BankReviewType.LOAN_REVIEW -> "Darlehenszahlung prüfen"
        BankReviewType.RECURRING_REVIEW -> "Wiederkehrende Zahlung prüfen"
        BankReviewType.POSSIBLE_DUPLICATE -> "Mögliche Dublette"
        BankReviewType.AMOUNT_CONFLICT -> "Betragskonflikt"
        BankReviewType.PROPERTY_CONFLICT -> "Immobilienkonflikt"
        else -> "Manuell prüfen"
    }

    private fun explanation(type: String): String = when (type) {
        BankReviewType.MISSING_RECEIPT -> "Kein passender Beleg gefunden. Der Nutzer kann suchen, importieren oder manuell wählen."
        BankReviewType.COMBINATION_SUGGESTION -> "Mehrere Restbeträge bilden gemeinsam eine plausible Zuordnung."
        BankReviewType.PARTIAL_PAYMENT -> "Ein Restbetrag ist offen; nur der noch verfügbare Betrag darf erneut verwendet werden."
        BankReviewType.PROPERTY_CONFLICT -> "Bankbuchung und Beleg sind explizit unterschiedlichen Immobilien zugeordnet."
        BankReviewType.MULTIPLE_CANDIDATES -> "Mehrere ähnlich plausible Möglichkeiten vorhanden; keine automatische Auswahl."
        else -> "Transparenter Prüfpunkt aus dem bestehenden Bank-/Belegabgleich."
    }

    private fun actions(type: String): List<String> = when (type) {
        BankReviewType.MISSING_RECEIPT -> listOf("SEARCH_RECEIPT", "SCAN_IMPORT", "CHOOSE_RECEIPT", "NO_RECEIPT_REQUIRED", "MANUAL_REVIEW")
        BankReviewType.COMBINATION_SUGGESTION -> listOf("CONFIRM_COMBINATION", "EDIT_ALLOCATION", "CHOOSE_RECEIPT", "MANUAL_REVIEW")
        BankReviewType.PARTIAL_PAYMENT -> listOf("ADD_RECEIPT", "EDIT_ALLOCATION", "UNLINK", "MANUAL_REVIEW")
        BankReviewType.SAFE_SUGGESTION -> listOf("CONFIRM", "CHOOSE_RECEIPT", "MANUAL_REVIEW")
        else -> listOf("OPEN_DETAILS", "MANUAL_REVIEW", "UNLINK")
    }
}

object BankBatchEligibility {
    data class Decision(val eligible: Boolean, val reasons: List<String>)

    fun evaluate(item: BankReviewItem): Decision {
        val reasons = mutableListOf<String>()
        if (item.type != BankReviewType.SAFE_SUGGESTION) reasons += "Nur sichere Einzelvorschläge sind batchfähig."
        if (item.confidence != "HOCH" || item.score < BankCombinationThresholds.HIGH_SCORE) reasons += "Confidence ist nicht hoch genug."
        if (item.conflicts.isNotEmpty()) reasons += "Konflikt vorhanden."
        if (item.remainingAmount <= BankAllocationPolicy.MONEY_TOLERANCE) reasons += "Kein offener Betrag."
        return Decision(reasons.isEmpty(), reasons)
    }

    fun preview(items: List<BankReviewItem>): BankBatchPreview {
        val eligible = items.filter { evaluate(it).eligible }
        val excluded = items.filterNot { evaluate(it).eligible }
        return BankBatchPreview(
            eligibleKeys = eligible.map { it.stableKey },
            transactionIds = eligible.flatMap { it.transactionIds }.distinct().sorted(),
            receiptIds = eligible.flatMap { it.receiptIds }.distinct().sorted(),
            totalAmount = BankAllocationPolicy.roundMoney(eligible.sumOf { minOf(it.amount, it.remainingAmount) }),
            excludedKeys = excluded.map { it.stableKey },
            warnings = excluded.map { "${it.title}: ${evaluate(it).reasons.joinToString()}" }
        )
    }
}

data class BankBatchPreview(
    val eligibleKeys: List<String>,
    val transactionIds: List<String>,
    val receiptIds: List<Int>,
    val totalAmount: Double,
    val excludedKeys: List<String>,
    val warnings: List<String>
)
