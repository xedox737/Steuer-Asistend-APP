package com.example.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Final Phase-1 scoring additions. They only produce deterministic suggestions. */
data class PaymentMatchAdjustment(val points: Int, val reasons: List<String>)

object BankPaymentMatchScore {
    fun adjustment(transaction: BankTransaction, receipt: Receipt): PaymentMatchAdjustment {
        val method = normalize(receipt.zahlungsart)
        val bankText = normalize("${transaction.purpose} ${transaction.counterparty} ${transaction.bankReference}")
        return when {
            method == "bar" -> PaymentMatchAdjustment(-35, listOf("Zahlungsart Bar spricht gegen Bankmatch"))
            method == "ueberweisung" -> {
                if ("lastschrift" in bankText) PaymentMatchAdjustment(-8, listOf("Banktext spricht eher für Lastschrift"))
                else PaymentMatchAdjustment(8, listOf("Zahlungsart Überweisung passt zum Bankmatch"))
            }
            method == "lastschrift" && ("lastschrift" in bankText || "sepa" in bankText) ->
                PaymentMatchAdjustment(10, listOf("Zahlungsart Lastschrift passt zum Banktext"))
            (method == "karte" || method.contains("girocard") || method.contains("kreditkarte")) &&
                listOf("karte", "card", "girocard", "visa", "mastercard").any { it in bankText } ->
                PaymentMatchAdjustment(6, listOf("Kartenzahlung passt zum Banktext"))
            else -> PaymentMatchAdjustment(0, emptyList())
        }
    }

    private fun normalize(value: String): String = value.lowercase(java.util.Locale.GERMANY)
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace(Regex("[^a-z0-9]+"), " ").trim()
}

object BankLoanPaymentType {
    const val REGULAERE_RATE = "REGULAERE_RATE"
    const val SONDERTILGUNG = "SONDERTILGUNG"
    const val GEBUEHR = "GEBUEHR"
    const val KORREKTUR_ERSTATTUNG = "KORREKTUR_ERSTATTUNG"
    const val SONSTIGE_DARLEHENSZAHLUNG = "SONSTIGE_DARLEHENSZAHLUNG"
    const val UNKLAR = "UNKLAR"
}

object BankLoanConflictState {
    const val NONE = "NONE"
    const val MULTIPLE_LOANS = "MULTIPLE_LOANS"
    const val RATE_CHANGED = "RATE_CHANGED"
    const val LARGE_AMOUNT_DIFFERENCE = "LARGE_AMOUNT_DIFFERENCE"
    const val POSSIBLE_SPECIAL_REPAYMENT = "POSSIBLE_SPECIAL_REPAYMENT"
    const val POSITIVE_LOAN_MOVEMENT = "POSITIVE_LOAN_MOVEMENT"
    const val UNCLASSIFIED = "UNCLASSIFIED"
}

object BankLoanThresholds {
    const val HIGH_SCORE = 85
    const val MEDIUM_SCORE = 65
    const val MIN_SUGGESTION_SCORE = 55
    const val MAX_RULE_BONUS = 10
    const val SMALL_AMOUNT_DIFFERENCE_EUR = 5.0
    const val SMALL_AMOUNT_DIFFERENCE_RATIO = 0.05
    const val LARGE_AMOUNT_DIFFERENCE_RATIO = 0.25
    const val TYPICAL_DAY_TOLERANCE = 4L
    const val LARGE_DAY_DEVIATION = 10L
    const val RATE_CHANGE_MIN_CONFIRMATIONS = 3
}

data class BankLoanSuggestion(
    val transactionId: String = "",
    val loanId: Int,
    val loanName: String = "",
    val bankName: String = "",
    val propertyId: String = "",
    val expectedAmount: Double = 0.0,
    val actualAmount: Double = 0.0,
    val difference: Double = 0.0,
    val score: Int,
    val confidence: String,
    val reasons: List<String>,
    val paymentType: String = BankLoanPaymentType.UNKLAR,
    val period: String = "",
    val conflictState: String = BankLoanConflictState.NONE,
    val recurrenceEvidence: Int = 0,
    val ruleBonus: Int = 0
)

object BankLoanMatcher {
    fun suggestions(
        transaction: BankTransaction,
        loans: List<Loan>,
        account: BankAccount? = null,
        history: List<BankTransaction> = emptyList(),
        rules: List<BankLearningRule> = emptyList()
    ): List<BankLoanSuggestion> {
        val ranked = loans.asSequence()
            .filter { it.aktiv && it.monatlicheRate > 0.0 }
            .map { score(transaction, it, account, history, rules) }
            .filter { it.score >= BankLoanThresholds.MIN_SUGGESTION_SCORE }
            .sortedWith(compareByDescending<BankLoanSuggestion> { it.score }.thenBy { it.loanId })
            .toList()
        if (ranked.size >= 2 && ranked[0].score == ranked[1].score) {
            return ranked.mapIndexed { index, item ->
                if (index <= 1) item.copy(
                    confidence = "NIEDRIG",
                    conflictState = BankLoanConflictState.MULTIPLE_LOANS,
                    reasons = (item.reasons + "Mehrere Darlehen sind gleich plausibel").distinct()
                ) else item
            }
        }
        return ranked
    }

    fun score(
        transaction: BankTransaction,
        loan: Loan,
        account: BankAccount? = null,
        history: List<BankTransaction> = emptyList(),
        rules: List<BankLearningRule> = emptyList()
    ): BankLoanSuggestion {
        var score = 0
        val reasons = mutableListOf<String>()
        val actual = transaction.absoluteAmount
        val expected = abs(loan.monatlicheRate)
        val diff = abs(actual - expected)
        var conflict = BankLoanConflictState.NONE
        var paymentType = classifyPaymentType(transaction, expected)

        if (transaction.amount < 0.0) {
            score += 15
            reasons += "Belastung ist für Darlehensrate plausibel"
        } else {
            score -= 20
            conflict = BankLoanConflictState.POSITIVE_LOAN_MOVEMENT
            paymentType = BankLoanPaymentType.KORREKTUR_ERSTATTUNG
            reasons += "Positive Bewegung ist keine normale Darlehensrate; als Korrektur/Erstattung prüfen"
        }

        when {
            diff <= 0.01 -> { score += 45; reasons += "Monatsrate stimmt exakt" }
            diff <= BankLoanThresholds.SMALL_AMOUNT_DIFFERENCE_EUR -> { score += 32; reasons += "Monatsrate nahezu gleich" }
            expected > 0.0 && diff / expected <= BankLoanThresholds.SMALL_AMOUNT_DIFFERENCE_RATIO -> { score += 20; reasons += "Monatsrate ähnlich" }
            expected > 0.0 && diff / expected >= BankLoanThresholds.LARGE_AMOUNT_DIFFERENCE_RATIO -> {
                score -= 15
                if (paymentType == BankLoanPaymentType.SONDERTILGUNG) {
                    conflict = BankLoanConflictState.POSSIBLE_SPECIAL_REPAYMENT
                    reasons += "Betrag weicht stark ab; mögliche Sondertilgung"
                } else {
                    conflict = BankLoanConflictState.LARGE_AMOUNT_DIFFERENCE
                    reasons += "Betrag weicht stark von der Sollrate ab"
                }
            }
        }

        val loanBank = normalize(loan.bank)
        val accountBank = normalize(account?.bankName.orEmpty())
        val bankText = normalize("${transaction.counterparty} ${transaction.purpose} ${transaction.bankReference}")
        if (loanBank.isNotBlank() && accountBank.isNotBlank()) {
            if (tokenSimilarity(loanBank, accountBank) >= 0.5) {
                score += 15; reasons += "Bankname passt"
            } else {
                score -= 25; reasons += "Bankname weicht ab"
            }
        } else if (loanBank.isNotBlank() && loanBank.split(' ').filter { it.length >= 4 }.any { it in bankText }) {
            score += 15; reasons += "Kreditgeber im Zahlungstext"
        }

        val loanText = normalize("${loan.bezeichnung} ${loan.bank} ${loan.notiz}")
        val loanTokens = loanText.split(' ').filter { it.length >= 4 }.toSet()
        val txTokens = bankText.split(' ').filter { it.length >= 4 }.toSet()
        val overlap = loanTokens.intersect(txTokens).size
        if (overlap >= 2) { score += 18; reasons += "Darlehensreferenz im Zahlungstext" }
        else if (overlap == 1) { score += 8; reasons += "Zahlungstext passt zum Darlehen" }

        if (transaction.propertyId.isNotBlank() && loan.propertyId.isNotBlank()) {
            if (transaction.propertyId == loan.propertyId) {
                score += 12; reasons += "Immobilie passt"
            } else {
                score -= 30; reasons += "Immobilie weicht ab"
            }
        }

        val relevantHistory = history.filter {
            it.transactionId != transaction.transactionId &&
                it.accountId == transaction.accountId && it.amount < 0.0 &&
                sameCounterparty(it, transaction)
        }
        val recurrenceEvidence = relevantHistory.count {
            abs(it.absoluteAmount - expected) <= maxOf(1.0, expected * 0.03)
        }
        if (recurrenceEvidence >= 2) {
            score += 10
            reasons += "Bestätigbare wiederkehrende Rate (${recurrenceEvidence + 1} Vorkommen)"
        }

        typicalBookingDay(relevantHistory)?.let { typicalDay ->
            bookingDay(transaction)?.let { currentDay ->
                val delta = circularDayDistance(currentDay, typicalDay)
                when {
                    delta <= BankLoanThresholds.TYPICAL_DAY_TOLERANCE -> {
                        score += 7; reasons += "Typischer Abbuchungstag passt"
                    }
                    delta >= BankLoanThresholds.LARGE_DAY_DEVIATION -> {
                        score -= 5; reasons += "Abbuchungstag weicht deutlich ab"
                    }
                }
            }
        }

        if (detectPossibleRateChange(transaction, loan, relevantHistory)) {
            conflict = BankLoanConflictState.RATE_CHANGED
            reasons += "Mögliche Ratenänderung: mehrere konsistente neue Beträge"
        }

        var ruleBonus = 0
        if (score >= 45) {
            val evaluation = BankRuleEngine.evaluate(transaction, rules)
            if (!evaluation.hasConflict) {
                val best = evaluation.suggestions.firstOrNull()
                if (best != null) {
                    ruleBonus = (best.score / 8).coerceIn(1, BankLoanThresholds.MAX_RULE_BONUS)
                    score += ruleBonus
                    reasons += "Phase-2A-Regel als Zusatzhilfe +$ruleBonus"
                }
            } else {
                reasons += "Phase-2A-Regelkonflikt wurde nicht als Bonus gewertet"
            }
        }

        val bounded = score.coerceIn(0, 100)
        val confidence = when {
            conflict != BankLoanConflictState.NONE -> "NIEDRIG"
            bounded >= BankLoanThresholds.HIGH_SCORE -> "HOCH"
            bounded >= BankLoanThresholds.MEDIUM_SCORE -> "MITTEL"
            else -> "NIEDRIG"
        }
        val period = runCatching { YearMonth.from(LocalDate.parse(transaction.bookingDate)).toString() }.getOrDefault("")
        return BankLoanSuggestion(
            transactionId = transaction.transactionId,
            loanId = loan.id,
            loanName = loan.bezeichnung,
            bankName = loan.bank,
            propertyId = loan.propertyId,
            expectedAmount = expected,
            actualAmount = actual,
            difference = actual - expected,
            score = bounded,
            confidence = confidence,
            reasons = reasons.distinct(),
            paymentType = paymentType,
            period = period,
            conflictState = conflict,
            recurrenceEvidence = recurrenceEvidence,
            ruleBonus = ruleBonus
        )
    }

    private fun classifyPaymentType(transaction: BankTransaction, expected: Double): String {
        val text = normalize("${transaction.purpose} ${transaction.bankReference}")
        return when {
            transaction.amount > 0.0 -> BankLoanPaymentType.KORREKTUR_ERSTATTUNG
            listOf("sondertilgung", "sonderzahlung", "extra tilgung", "zusatztilgung").any { it in text } -> BankLoanPaymentType.SONDERTILGUNG
            listOf("gebuehr", "bearbeitungsentgelt", "entgelt").any { it in text } -> BankLoanPaymentType.GEBUEHR
            expected > 0.0 && abs(transaction.absoluteAmount - expected) <= maxOf(5.0, expected * 0.05) -> BankLoanPaymentType.REGULAERE_RATE
            text.contains("darlehen") || text.contains("kredit") -> BankLoanPaymentType.SONSTIGE_DARLEHENSZAHLUNG
            else -> BankLoanPaymentType.UNKLAR
        }
    }

    private fun detectPossibleRateChange(transaction: BankTransaction, loan: Loan, history: List<BankTransaction>): Boolean {
        val expected = abs(loan.monatlicheRate)
        val actual = transaction.absoluteAmount
        if (expected <= 0.0 || abs(actual - expected) <= maxOf(5.0, expected * 0.05)) return false
        val similarNew = history.count { abs(it.absoluteAmount - actual) <= maxOf(1.0, actual * 0.02) }
        return similarNew + 1 >= BankLoanThresholds.RATE_CHANGE_MIN_CONFIRMATIONS
    }

    private fun sameCounterparty(a: BankTransaction, b: BankTransaction): Boolean {
        val ai = normalize(a.counterpartyIban)
        val bi = normalize(b.counterpartyIban)
        if (ai.isNotBlank() && bi.isNotBlank()) return ai == bi
        return tokenSimilarity(a.counterparty, b.counterparty) >= 0.5
    }

    private fun typicalBookingDay(history: List<BankTransaction>): Int? {
        val days = history.mapNotNull(::bookingDay)
        if (days.size < 2) return null
        return days.sorted()[days.size / 2]
    }

    private fun bookingDay(tx: BankTransaction): Int? = runCatching { LocalDate.parse(tx.bookingDate).dayOfMonth }.getOrNull()

    private fun circularDayDistance(a: Int, b: Int): Long = minOf(abs(a - b), 31 - abs(a - b)).toLong()

    private fun tokenSimilarity(a: String, b: String): Double {
        val left = normalize(a).split(' ').filter { it.length >= 3 }.toSet()
        val right = normalize(b).split(' ').filter { it.length >= 3 }.toSet()
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return left.intersect(right).size.toDouble() / minOf(left.size, right.size).toDouble()
    }

    private fun normalize(value: String): String = value.lowercase(java.util.Locale.GERMANY)
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace(Regex("[^a-z0-9]+"), " ").trim()
}
