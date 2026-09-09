package com.example.data

import kotlin.math.abs

object BankPhase2DRulePolicy {
    const val MAX_RULE_BONUS = 8

    fun bonus(transactionIds: List<String>, transactions: List<BankTransaction>, rules: List<BankLearningRule>): Pair<Int, List<String>> {
        if (rules.isEmpty()) return 0 to emptyList()
        val byId = transactions.associateBy { it.transactionId }
        var best = 0
        val reasons = mutableListOf<String>()
        transactionIds.forEach { id ->
            val tx = byId[id] ?: return@forEach
            val evaluation = BankRuleEngine.evaluate(tx, rules)
            if (evaluation.hasConflict) return@forEach
            val points = evaluation.suggestions.firstOrNull()?.score?.div(12)?.coerceIn(1, MAX_RULE_BONUS) ?: 0
            if (points > 0) {
                best = maxOf(best, points)
                reasons += "Aktive Phase-2A-Regel liefert begrenzte Zusatz-Evidenz."
            }
        }
        return best.coerceAtMost(MAX_RULE_BONUS) to reasons.distinct()
    }
}

data class BankPhase2DAnalysis(
    val combinations: List<BankCombinationSuggestion>,
    val queue: List<BankReviewItem>
)

object BankPhase2DEngine {
    fun analyze(
        transactions: List<BankTransaction>,
        receipts: List<Receipt>,
        links: List<BankReceiptLink>,
        oneToOne: Map<String, BankMatchSuggestion>,
        rules: List<BankLearningRule> = emptyList(),
        rentAssignments: List<BankRentAssignment> = emptyList(),
        loanAssignments: List<BankLoanAssignment> = emptyList(),
        recurringPatterns: List<BankRecurringPattern> = emptyList()
    ): BankPhase2DAnalysis {
        val rentTx = rentAssignments.filter { it.status == BankRentAssignmentStatus.CONFIRMED }.map { it.transactionId }.toSet()
        val loanTx = loanAssignments.map { it.transactionId }.toSet()
        val excludedClassified = rentTx + loanTx
        val combinationTransactions = transactions.filterNot { it.transactionId in excludedClassified }

        val raw = buildList {
            combinationTransactions.forEach { tx ->
                addAll(BankCombinationMatcher.oneTransactionToManyReceipts(tx, receipts, links))
                BankReceiptMatcher.rankReceipts(tx, receipts, links).take(4).forEach { candidate ->
                    val receipt = receipts.firstOrNull { it.id == candidate.receiptId } ?: return@forEach
                    BankCombinationMatcher.partialSuggestion(tx, receipt, links)?.let(::add)
                }
            }
            receipts.forEach { receipt ->
                addAll(BankCombinationMatcher.manyTransactionsToOneReceipt(receipt, combinationTransactions, links))
            }
        }
        val combinations = raw.distinctBy { it.suggestionId }.map { suggestion ->
            if (suggestion.conflicts.isNotEmpty()) return@map suggestion
            val (bonus, ruleReasons) = BankPhase2DRulePolicy.bonus(suggestion.transactionIds, transactions, rules)
            if (bonus <= 0) suggestion else suggestion.copy(
                score = (suggestion.score + bonus).coerceAtMost(100),
                reasons = (suggestion.reasons + ruleReasons).distinct()
            )
        }.sortedWith(compareByDescending<BankCombinationSuggestion> { it.score }.thenBy { it.difference }.thenBy { it.suggestionId })

        val queue = BankPhase2DReviewQueue.build(
            transactions, receipts, links, oneToOne, combinations,
            rentAssignments, loanAssignments, recurringPatterns
        )
        return BankPhase2DAnalysis(combinations, queue)
    }
}

object BankPhase2DReviewQueue {
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
        val base = BankReviewQueueBuilder.build(
            transactions, receipts, links, oneToOne, combinations,
            rentAssignments, loanAssignments, recurringPatterns
        ).associateBy { it.stableKey }.toMutableMap()

        val receiptById = receipts.associateBy { it.id }
        val transactionsById = transactions.associateBy { it.transactionId }
        val rentTx = rentAssignments.filter { it.status == BankRentAssignmentStatus.CONFIRMED }.map { it.transactionId }.toSet()
        val loanTx = loanAssignments.map { it.transactionId }.toSet()

        base.entries.removeAll { (_, item) ->
            val txId = item.transactionIds.singleOrNull()
            txId != null && (txId in rentTx || txId in loanTx) &&
                item.type !in setOf(BankReviewType.RENT_REVIEW, BankReviewType.LOAN_REVIEW)
        }

        // Phase 2C remains authoritative for classified loan transactions. A REVIEW assignment
        // must stay visible in the central Phase 2D queue even when the generic bank status is OPEN.
        loanAssignments.filter { it.status == BankLoanAssignmentStatus.REVIEW }.forEach { assignment ->
            val tx = transactionsById[assignment.transactionId] ?: return@forEach
            replaceTxItem(base, tx.transactionId, BankReviewItem(
                stableKey = stable(BankReviewType.LOAN_REVIEW, listOf(tx.transactionId), emptyList()),
                type = BankReviewType.LOAN_REVIEW,
                priority = 85,
                transactionIds = listOf(tx.transactionId),
                receiptIds = emptyList(),
                propertyId = assignment.propertyId.ifBlank { tx.propertyId },
                amount = tx.absoluteAmount,
                remainingAmount = BankAllocationPolicy.transactionRemaining(tx, links).remainingAmount,
                score = 0,
                confidence = "NIEDRIG",
                title = "Darlehenszahlung prüfen",
                explanation = "Die Phase-2C-Darlehensklassifizierung benötigt eine Nutzerprüfung und wird nicht als normale Belegkombination behandelt.",
                reasons = listOf("Bestehende Phase-2C-Darlehenszuordnung im Status REVIEW."),
                conflicts = listOf(BankCombinationConflict.SPECIAL_CLASSIFICATION),
                availableActions = listOf("OPEN_DETAILS", "MANUAL_REVIEW"),
                bookingDate = tx.bookingDate
            ))
        }

        transactions.forEach { tx ->
            if (tx.reconciliationStatus in setOf(BankReconciliationStatus.MATCHED, BankReconciliationStatus.NO_RECEIPT_REQUIRED)) return@forEach
            if (tx.transactionId in rentTx || tx.transactionId in loanTx) return@forEach
            val match = oneToOne[tx.transactionId]
            val receipt = match?.receiptId?.let(receiptById::get)
            if (receipt != null && tx.propertyId.isNotBlank() && receipt.propertyId.isNotBlank() && tx.propertyId != receipt.propertyId) {
                replaceTxItem(base, tx.transactionId, conflictItem(
                    tx, receipt, BankReviewType.PROPERTY_CONFLICT, "Immobilienkonflikt",
                    "Bankbuchung und Beleg gehören explizit zu unterschiedlichen Immobilien.",
                    listOf(BankCombinationConflict.PROPERTY_CONFLICT), match.score
                ))
                return@forEach
            }
            if (receipt != null) {
                val txRemaining = BankAllocationPolicy.transactionRemaining(tx, links).remainingAmount
                val receiptRemaining = BankAllocationPolicy.receiptRemaining(receipt, links).remainingAmount
                val diff = abs(txRemaining - receiptRemaining)
                val tolerance = maxOf(BankCombinationThresholds.NEAR_ABSOLUTE_EUR, maxOf(txRemaining, receiptRemaining) * BankCombinationThresholds.NEAR_RATIO)
                if (diff > tolerance && match.score >= 45) {
                    replaceTxItem(base, tx.transactionId, conflictItem(
                        tx, receipt, BankReviewType.AMOUNT_CONFLICT, "Betragskonflikt",
                        "Der Kandidat passt textlich/zeitlich, aber die offenen Beträge weichen deutlich voneinander ab.",
                        listOf("AMOUNT_DIFFERENCE"), match.score
                    ))
                }
            }
        }

        duplicateGroups(transactions).forEach { group ->
            group.forEach duplicateTx@{ tx ->
                if (tx.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED) return@duplicateTx
                if (tx.transactionId in rentTx || tx.transactionId in loanTx) return@duplicateTx
                replaceTxItem(base, tx.transactionId, BankReviewItem(
                    stableKey = stable(BankReviewType.POSSIBLE_DUPLICATE, listOf(tx.transactionId), emptyList()),
                    type = BankReviewType.POSSIBLE_DUPLICATE,
                    priority = 92,
                    transactionIds = listOf(tx.transactionId),
                    receiptIds = emptyList(),
                    propertyId = tx.propertyId,
                    amount = tx.absoluteAmount,
                    remainingAmount = BankAllocationPolicy.transactionRemaining(tx, links).remainingAmount,
                    title = "Mögliche Bank-Dublette",
                    explanation = "Mehrere Buchungen besitzen dieselben wesentlichen Importmerkmale. Keine automatische Zuordnung.",
                    conflicts = listOf("POSSIBLE_BANK_DUPLICATE"),
                    availableActions = listOf("OPEN_DETAILS", "MANUAL_REVIEW"),
                    bookingDate = tx.bookingDate
                ))
            }
        }

        duplicateReceipts(receipts).forEach { duplicateIds ->
            base.values.toList().filter { item -> item.receiptIds.any { it in duplicateIds } }.forEach { item ->
                val changed = item.copy(
                    stableKey = stable(BankReviewType.POSSIBLE_DUPLICATE, item.transactionIds, item.receiptIds),
                    type = BankReviewType.POSSIBLE_DUPLICATE,
                    priority = maxOf(92, item.priority),
                    title = "Mögliche Beleg-Dublette",
                    explanation = "Ähnliche Belege dürfen nicht automatisch gemeinsam oder erneut verwendet werden.",
                    conflicts = (item.conflicts + BankCombinationConflict.DUPLICATE_RECEIPT).distinct(),
                    confidence = "NIEDRIG"
                )
                base.remove(item.stableKey)
                base[changed.stableKey] = changed
            }
        }

        return base.values.distinctBy { it.stableKey }
            .sortedWith(compareByDescending<BankReviewItem> { it.priority }.thenByDescending { it.bookingDate }.thenBy { it.stableKey })
    }

    private fun conflictItem(
        tx: BankTransaction,
        receipt: Receipt,
        type: String,
        title: String,
        explanation: String,
        conflicts: List<String>,
        score: Int
    ) = BankReviewItem(
        stableKey = stable(type, listOf(tx.transactionId), listOf(receipt.id)),
        type = type,
        priority = if (type == BankReviewType.PROPERTY_CONFLICT) 100 else 95,
        transactionIds = listOf(tx.transactionId),
        receiptIds = listOf(receipt.id),
        propertyId = tx.propertyId,
        amount = tx.absoluteAmount,
        remainingAmount = tx.absoluteAmount,
        score = score,
        confidence = "NIEDRIG",
        title = title,
        explanation = explanation,
        conflicts = conflicts,
        availableActions = listOf("OPEN_DETAILS", "CHOOSE_RECEIPT", "MANUAL_REVIEW"),
        bookingDate = tx.bookingDate
    )

    private fun replaceTxItem(base: MutableMap<String, BankReviewItem>, transactionId: String, replacement: BankReviewItem) {
        base.entries.removeAll { transactionId in it.value.transactionIds }
        base[replacement.stableKey] = replacement
    }

    private fun duplicateGroups(transactions: List<BankTransaction>): List<List<BankTransaction>> =
        transactions.groupBy {
            listOf(it.accountId, it.bookingDate, BankAllocationPolicy.roundMoney(it.amount).toString(), normalize(it.counterparty), normalize(it.purpose), normalize(it.bankReference)).joinToString("|")
        }.values.filter { it.size > 1 }

    private fun duplicateReceipts(receipts: List<Receipt>): List<Set<Int>> =
        receipts.groupBy { listOf(it.datum, BankAllocationPolicy.roundMoney(it.bruttobetrag).toString(), normalize(it.aussteller), normalize(it.beschreibung)).joinToString("|") }
            .values.filter { it.size > 1 }.map { group -> group.map { it.id }.toSet() }

    private fun stable(type: String, tx: List<String>, receipts: List<Int>): String =
        "review-" + BankTransactionIdentity.sha256("$type|${tx.sorted()}|${receipts.sorted()}").take(28)

    private fun normalize(value: String): String = value.lowercase(java.util.Locale.GERMANY)
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace(Regex("[^a-z0-9]+"), " ").trim()
}
