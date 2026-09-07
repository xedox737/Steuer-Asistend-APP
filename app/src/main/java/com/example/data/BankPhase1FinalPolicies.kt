package com.example.data

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

data class BankLoanSuggestion(
    val loanId: Int,
    val score: Int,
    val confidence: String,
    val reasons: List<String>
)

object BankLoanMatcher {
    fun suggestions(
        transaction: BankTransaction,
        loans: List<Loan>,
        account: BankAccount? = null,
        history: List<BankTransaction> = emptyList()
    ): List<BankLoanSuggestion> = loans.asSequence()
        .filter { it.aktiv && it.monatlicheRate > 0.0 }
        .map { score(transaction, it, account, history) }
        .filter { it.score >= 55 }
        .sortedWith(compareByDescending<BankLoanSuggestion> { it.score }.thenBy { it.loanId })
        .toList()

    fun score(
        transaction: BankTransaction,
        loan: Loan,
        account: BankAccount? = null,
        history: List<BankTransaction> = emptyList()
    ): BankLoanSuggestion {
        var score = 0
        val reasons = mutableListOf<String>()
        val actual = transaction.absoluteAmount
        val expected = abs(loan.monatlicheRate)
        val diff = abs(actual - expected)
        when {
            diff <= 0.01 -> { score += 50; reasons += "Monatsrate stimmt exakt" }
            diff <= 5.0 -> { score += 35; reasons += "Monatsrate nahezu gleich" }
            expected > 0.0 && diff / expected <= 0.05 -> { score += 20; reasons += "Monatsrate ähnlich" }
        }

        val loanBank = normalize(loan.bank)
        val accountBank = normalize(account?.bankName.orEmpty())
        val bankText = normalize("${transaction.counterparty} ${transaction.purpose} ${transaction.bankReference}")
        if (loanBank.isNotBlank() && accountBank.isNotBlank()) {
            if (tokenSimilarity(loanBank, accountBank) >= 0.5) {
                score += 20; reasons += "Bankname passt"
            } else {
                score -= 35; reasons += "Bankname weicht ab"
            }
        } else if (loanBank.isNotBlank() && loanBank.split(' ').filter { it.length >= 4 }.any { it in bankText }) {
            score += 15; reasons += "Bank im Zahlungstext"
        }

        val loanText = normalize("${loan.bezeichnung} ${loan.bank} ${loan.notiz}")
        val loanTokens = loanText.split(' ').filter { it.length >= 4 }.toSet()
        val txTokens = bankText.split(' ').filter { it.length >= 4 }.toSet()
        val overlap = loanTokens.intersect(txTokens).size
        if (overlap >= 2) { score += 15; reasons += "Darlehensreferenz im Zahlungstext" }
        else if (overlap == 1) { score += 8; reasons += "Zahlungstext passt zum Darlehen" }

        if (transaction.propertyId.isNotBlank() && loan.propertyId.isNotBlank()) {
            if (transaction.propertyId == loan.propertyId) {
                score += 10; reasons += "Immobilie passt"
            } else {
                score -= 20; reasons += "Immobilie weicht ab"
            }
        }

        val recurring = history.count {
            it.accountId == transaction.accountId && abs(it.absoluteAmount - actual) <= 0.01
        }
        if (recurring >= 2) { score += 10; reasons += "Wiederkehrender Betrag" }

        val bounded = score.coerceIn(0, 100)
        val confidence = when {
            bounded >= 85 -> "HOCH"
            bounded >= 65 -> "MITTEL"
            else -> "NIEDRIG"
        }
        return BankLoanSuggestion(loan.id, bounded, confidence, reasons)
    }

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
