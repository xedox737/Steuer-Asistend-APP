package com.example.ui

import com.example.data.BankLearningRule
import com.example.data.BankRuleEngine
import com.example.data.BankTransaction
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.abs

object RentPaymentType {
    const val MIETE = "MIETE"
    const val NEBENKOSTEN = "NEBENKOSTEN"
    const val KAUTION = "KAUTION"
    const val SONSTIGE_MIETZAHLUNG = "SONSTIGE_MIETZAHLUNG"
    const val UNKLAR = "UNKLAR"
}

object RentMatchConfidence {
    const val HOCH = "HOCH"
    const val MITTEL = "MITTEL"
    const val NIEDRIG = "NIEDRIG"
}

object RentConflictState {
    const val NONE = "NONE"
    const val MONTH_CONFLICT = "MONTH_CONFLICT"
    const val MULTIPLE_CANDIDATES = "MULTIPLE_CANDIDATES"
    const val OVERPAYMENT = "OVERPAYMENT"
    const val ALREADY_PAID = "ALREADY_PAID"
    const val SPECIAL_PAYMENT = "SPECIAL_PAYMENT"
    const val LARGE_AMOUNT_DIFFERENCE = "LARGE_AMOUNT_DIFFERENCE"
}

object BankRentThresholds {
    const val HIGH = 80
    const val MEDIUM = 55
    const val MIN_PLAUSIBLE = 35
    const val MAX_RULE_BONUS = 10
    const val AMOUNT_EXACT_TOLERANCE = 0.01
    const val AMOUNT_NEAR_RATIO = 0.10
}

data class RentCandidateContext(
    val propertyId: String,
    val propertyLabel: String,
    val unitId: String,
    val unitName: String,
    val tenantReference: String,
    val tenantName: String,
    val tenantStart: String,
    val tenantEnd: String,
    val rentMonth: YearMonth,
    val expectedAmount: Double,
    val alreadyConfirmedAmount: Double,
    val accountId: String = ""
) {
    val remainingAmount: Double get() = (expectedAmount - alreadyConfirmedAmount).coerceAtLeast(0.0)
    val fullyPaid: Boolean get() = expectedAmount > 0.01 && remainingAmount <= 0.01
}

data class BankRentSuggestion(
    val transactionId: String,
    val propertyId: String,
    val unitId: String,
    val tenantReference: String,
    val tenantName: String,
    val rentMonth: String,
    val expectedAmount: Double,
    val actualAmount: Double,
    val difference: Double,
    val score: Int,
    val confidence: String,
    val reasons: List<String>,
    val paymentType: String,
    val conflictState: String,
    val remainingAmount: Double,
    val propertyLabel: String = "",
    val unitName: String = ""
)

data class RentMonthDetection(
    val month: YearMonth?,
    val explicit: Boolean,
    val conflict: Boolean,
    val reasons: List<String>
)

object RentMonthParser {
    private val germanMonths = linkedMapOf(
        "januar" to 1, "jan" to 1,
        "februar" to 2, "feb" to 2,
        "maerz" to 3, "märz" to 3, "mrz" to 3,
        "april" to 4, "apr" to 4,
        "mai" to 5,
        "juni" to 6, "jun" to 6,
        "juli" to 7, "jul" to 7,
        "august" to 8, "aug" to 8,
        "september" to 9, "sep" to 9, "sept" to 9,
        "oktober" to 10, "okt" to 10,
        "november" to 11, "nov" to 11,
        "dezember" to 12, "dez" to 12
    )

    fun detect(purpose: String, bookingDate: String): RentMonthDetection {
        val booking = runCatching { LocalDate.parse(bookingDate) }.getOrNull()
        val bookingMonth = booking?.let(YearMonth::from)
        val text = normalize(purpose)
        val found = linkedSetOf<YearMonth>()
        val defaultYear = bookingMonth?.year ?: LocalDate.now().year

        Regex("\\b(20\\d{2})[-/.](0?[1-9]|1[0-2])\\b").findAll(text).forEach { m ->
            found += YearMonth.of(m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }
        Regex("\\b(0?[1-9]|1[0-2])[/.](20\\d{2})\\b").findAll(text).forEach { m ->
            found += YearMonth.of(m.groupValues[2].toInt(), m.groupValues[1].toInt())
        }
        germanMonths.forEach { (name, number) ->
            Regex("\\b${Regex.escape(name)}(?:miete)?\\b").find(text)?.let { match ->
                val after = text.substring(match.range.last + 1).trim()
                val year = Regex("^(?:\\s|-)*(20\\d{2})\\b").find(after)?.groupValues?.get(1)?.toIntOrNull() ?: defaultYear
                found += YearMonth.of(year, number)
            }
            Regex("\\b(?:miete\\s*)?${Regex.escape(name)}\\b").find(text)?.let { match ->
                val after = text.substring(match.range.last + 1).trim()
                val year = Regex("^(?:\\s|-)*(20\\d{2})\\b").find(after)?.groupValues?.get(1)?.toIntOrNull() ?: defaultYear
                found += YearMonth.of(year, number)
            }
        }
        if (Regex("\\b(lfd|laufender|laufende|laufenden)\\s*(monat)?\\b").containsMatchIn(text) && bookingMonth != null) {
            found += bookingMonth
        }

        return when {
            found.size > 1 -> RentMonthDetection(null, true, true, listOf("Widersprüchliche Mietmonate im Verwendungszweck"))
            found.size == 1 -> RentMonthDetection(found.first(), true, false, listOf("Mietmonat aus Verwendungszweck erkannt"))
            bookingMonth != null -> RentMonthDetection(bookingMonth, false, false, listOf("Buchungsmonat nur als schwacher Hinweis"))
            else -> RentMonthDetection(null, false, false, listOf("Kein Mietmonat erkennbar"))
        }
    }

    private fun normalize(value: String): String = value.lowercase(Locale.GERMANY)
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace(Regex("[^a-z0-9./-]+"), " ").trim()
}

object RentPaymentClassifier {
    fun classify(transaction: BankTransaction): String {
        val text = BankRuleEngine.norm("${transaction.counterparty} ${transaction.purpose}")
        return when {
            listOf("mietkaution", "kaution", "sicherheitsleistung", "deposit").any { it in text } -> RentPaymentType.KAUTION
            listOf("nebenkosten", "betriebskosten", "heizkosten", " nk ", "nachzahlung", "abrechnung").any { token ->
                if (token.trim() == "nk") Regex("(^| )nk( |$)").containsMatchIn(text) else token in text
            } -> RentPaymentType.NEBENKOSTEN
            listOf("miete", "monatsmiete", "warmmiete", "kaltmiete").any { it in text } -> RentPaymentType.MIETE
            else -> RentPaymentType.UNKLAR
        }
    }
}

object BankRentMatcher {
    fun match(
        transaction: BankTransaction,
        candidates: List<RentCandidateContext>,
        rules: List<BankLearningRule> = emptyList()
    ): List<BankRentSuggestion> {
        if (!transaction.isIncome) return emptyList()
        val monthDetection = RentMonthParser.detect(transaction.purpose, transaction.bookingDate)
        val paymentType = RentPaymentClassifier.classify(transaction)
        val scored = candidates.mapNotNull { candidate ->
            scoreCandidate(transaction, candidate, monthDetection, paymentType, rules)
        }.sortedWith(compareByDescending<BankRentSuggestion> { it.score }.thenBy { it.propertyId }.thenBy { it.unitId }.thenBy { it.rentMonth })

        if (scored.size < 2) return scored
        val top = scored.first()
        val second = scored[1]
        val ambiguous = top.score == second.score && (top.propertyId != second.propertyId || top.unitId != second.unitId || top.rentMonth != second.rentMonth)
        return if (!ambiguous) scored else scored.mapIndexed { index, item ->
            if (index < 2) item.copy(
                conflictState = RentConflictState.MULTIPLE_CANDIDATES,
                confidence = RentMatchConfidence.NIEDRIG,
                reasons = (item.reasons + "Mehrere gleich plausible Mietzuordnungen").distinct()
            ) else item
        }
    }

    private fun scoreCandidate(
        tx: BankTransaction,
        c: RentCandidateContext,
        monthDetection: RentMonthDetection,
        paymentType: String,
        rules: List<BankLearningRule>
    ): BankRentSuggestion? {
        var score = 0
        val reasons = mutableListOf<String>()
        var conflict = RentConflictState.NONE

        if (tx.propertyId.isNotBlank()) {
            if (tx.propertyId != c.propertyId) return null
            score += 12; reasons += "Objekt passt"
        }
        if (tx.unitId.isNotBlank()) {
            if (tx.unitId != c.unitId) return null
            score += 12; reasons += "Einheit passt"
        }
        if (c.accountId.isNotBlank() && tx.accountId == c.accountId) {
            score += 5; reasons += "Konto passt"
        }

        val monthMatches = monthDetection.month == c.rentMonth
        if (monthDetection.conflict) {
            score -= 20; conflict = RentConflictState.MONTH_CONFLICT; reasons += monthDetection.reasons
        } else if (monthMatches) {
            score += if (monthDetection.explicit) 22 else 6
            reasons += if (monthDetection.explicit) "Mietmonat passt" else "Buchungsmonat passt als schwacher Hinweis"
        } else if (monthDetection.explicit) {
            score -= 28; reasons += "Expliziter Mietmonat passt nicht"
        }

        val tenantNorm = BankRuleEngine.norm(c.tenantName)
        val payerNorm = BankRuleEngine.norm(tx.counterparty)
        if (tenantNorm.isNotBlank() && payerNorm.isNotBlank()) {
            val tenantParts = tenantNorm.split(' ').filter { it.length >= 3 }.toSet()
            val payerParts = payerNorm.split(' ').filter { it.length >= 3 }.toSet()
            val nameMatch = tenantNorm in payerNorm || payerNorm in tenantNorm || tenantParts.intersect(payerParts).isNotEmpty()
            if (nameMatch) {
                score += 18; reasons += "Mieter/Zahler passt"
            } else {
                score -= 4; reasons += "Abweichender Zahler – Name allein ist kein Ausschluss"
            }
        }

        val purposeNorm = BankRuleEngine.norm(tx.purpose)
        val unitTerms = BankRuleEngine.norm(c.unitName).split(' ').filter { it.length >= 2 }
        if (unitTerms.isNotEmpty() && unitTerms.all { it in purposeNorm }) {
            score += 10; reasons += "Einheit im Verwendungszweck passt"
        }
        val propertyTerms = BankRuleEngine.norm(c.propertyLabel).split(' ').filter { it.length >= 4 }
        if (propertyTerms.isNotEmpty() && propertyTerms.any { it in purposeNorm }) {
            score += 6; reasons += "Objekt im Verwendungszweck passt"
        }
        if ("miete" in purposeNorm) {
            score += 8; reasons += "Verwendungszweck enthält Miete"
        }

        val actual = tx.absoluteAmount
        val expectedTarget = if (c.remainingAmount > 0.01) c.remainingAmount else c.expectedAmount
        val amountDiff = actual - expectedTarget
        val ratio = if (expectedTarget > 0.01) abs(amountDiff) / expectedTarget else 1.0
        when {
            abs(amountDiff) <= BankRentThresholds.AMOUNT_EXACT_TOLERANCE -> { score += 22; reasons += if (c.alreadyConfirmedAmount > 0.01) "Betrag passt exakt zum Restbetrag" else "Betrag passt zur Sollmiete" }
            actual < expectedTarget && ratio <= 0.55 -> { score += 10; reasons += "Betrag ist plausible Teil-/Unterzahlung" }
            actual > expectedTarget && ratio <= BankRentThresholds.AMOUNT_NEAR_RATIO -> { score += 5; reasons += "Betrag liegt knapp über dem offenen Soll"; conflict = RentConflictState.OVERPAYMENT }
            ratio > 0.50 -> { score -= 24; reasons += "Große Betragsabweichung"; conflict = RentConflictState.LARGE_AMOUNT_DIFFERENCE }
            else -> { score += 2; reasons += "Betrag weicht vom Soll ab" }
        }

        if (c.fullyPaid) {
            score -= 35
            conflict = RentConflictState.ALREADY_PAID
            reasons += "Mietmonat bereits vollständig bezahlt"
        }

        when (paymentType) {
            RentPaymentType.KAUTION -> { score -= 45; conflict = RentConflictState.SPECIAL_PAYMENT; reasons += "Kaution ist keine normale Monatsmiete" }
            RentPaymentType.NEBENKOSTEN -> { score -= 28; conflict = RentConflictState.SPECIAL_PAYMENT; reasons += "Nebenkosten/Nachzahlung nicht automatisch als Monatsmiete behandeln" }
            RentPaymentType.UNKLAR -> { score -= 4; reasons += "Zahlungstyp unklar" }
        }

        val ruleScopedTransaction = tx.copy(
            propertyId = tx.propertyId.ifBlank { c.propertyId },
            unitId = tx.unitId.ifBlank { c.unitId }
        )
        val ruleEval = BankRuleEngine.evaluate(ruleScopedTransaction, rules)
        if (!ruleEval.hasConflict) {
            val matchingRule = ruleEval.suggestions.firstOrNull { suggestion ->
                val rule = rules.firstOrNull { it.ruleId == suggestion.ruleId }
                rule != null && (rule.propertyId.isBlank() || rule.propertyId == c.propertyId) && (rule.unitId.isBlank() || rule.unitId == c.unitId)
            }
            if (matchingRule != null && score >= BankRentThresholds.MIN_PLAUSIBLE) {
                val bonus = (matchingRule.score / 10).coerceIn(1, BankRentThresholds.MAX_RULE_BONUS)
                score += bonus
                val name = rules.firstOrNull { it.ruleId == matchingRule.ruleId }?.displayName.orEmpty()
                reasons += "Regel „$name“ +$bonus"
            }
        } else {
            reasons += "Phase-2A-Regelkonflikt"
        }

        score = score.coerceIn(0, 100)
        if (score < BankRentThresholds.MIN_PLAUSIBLE && paymentType == RentPaymentType.UNKLAR) return null
        val confidence = when {
            conflict != RentConflictState.NONE -> RentMatchConfidence.NIEDRIG
            score >= BankRentThresholds.HIGH -> RentMatchConfidence.HOCH
            score >= BankRentThresholds.MEDIUM -> RentMatchConfidence.MITTEL
            else -> RentMatchConfidence.NIEDRIG
        }
        return BankRentSuggestion(
            transactionId = tx.transactionId,
            propertyId = c.propertyId,
            unitId = c.unitId,
            tenantReference = c.tenantReference,
            tenantName = c.tenantName,
            rentMonth = c.rentMonth.toString(),
            expectedAmount = c.expectedAmount,
            actualAmount = actual,
            difference = actual - c.remainingAmount,
            score = score,
            confidence = confidence,
            reasons = reasons.distinct(),
            paymentType = paymentType,
            conflictState = conflict,
            remainingAmount = (c.remainingAmount - actual).coerceAtLeast(0.0),
            propertyLabel = c.propertyLabel,
            unitName = c.unitName
        )
    }
}