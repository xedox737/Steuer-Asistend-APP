package com.example.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

/** Frozen a4fd298 behavior: prepared matching must preserve every reason and tie break. */
class BankReceiptPreparedMatchingTest {
    @Test fun preparedScoresAndPaymentReasonsEqualMainAcrossTextDatesAmountsDirectionsAndLocales() {
        val oldLocale = Locale.getDefault()
        try {
            listOf(Locale.GERMANY, Locale.US).forEach { locale ->
                Locale.setDefault(locale)
                transactions().forEach { tx -> receipts().forEach { receipt ->
                    val label = "locale=$locale tx=${tx.transactionId} receipt=${receipt.id}"
                    assertEquals(label, LegacyPreparedMatchOracle.score(tx, receipt), BankReceiptMatcher.score(tx, receipt))
                    assertEquals(label, LegacyPreparedPaymentOracle.adjustment(tx, receipt), BankPaymentMatchScore.adjustment(tx, receipt))
                } }
            }
        } finally { Locale.setDefault(oldLocale) }
    }

    @Test fun batchedSuggestionsEqualMainWithMixedDirectionsClassificationsStatusesAndLinks() {
        val rows = transactions()
        val receipts = receipts()
        val links = listOf(
            BankReceiptLink("confirmed-matched", rows[0].transactionId, receipts[0].id, allocatedAmount = 50.0),
            BankReceiptLink("unconfirmed-excludes-receipt", rows[1].transactionId, receipts[1].id,
                allocatedAmount = 25.0, status = "REVIEW"),
            BankReceiptLink("partial-link", rows[2].transactionId, receipts[2].id, allocatedAmount = 10.0),
            BankReceiptLink("internal-id-link", "unrelated-transaction", -1,
                receiptInternalId = receipts[3].internalId, allocatedAmount = 10.0)
        )
        val snapshots = Triple(rows.toList(), receipts.toList(), links.toList())
        listOf(receipts, receipts.reversed()).forEach { candidates ->
            assertEquals(LegacyPreparedMatchOracle.bestSuggestions(rows, candidates, links),
                BankReceiptMatcher.bestSuggestions(rows, candidates, links))
            rows.forEach { row ->
                assertEquals(LegacyPreparedMatchOracle.bestForTransaction(row, candidates, links),
                    BankReceiptMatcher.bestForTransaction(row, candidates, links))
            }
            candidates.forEach { receipt ->
                assertEquals(LegacyPreparedMatchOracle.bestForReceipt(receipt, rows, links),
                    BankReceiptMatcher.bestForReceipt(receipt, rows, links))
            }
        }
        assertEquals(snapshots, Triple(rows, receipts, links))
    }

    @Test fun lowerReceiptIdWinsTiesAndHigherTransactionIdWinsReverseTies() {
        val tx = transactions()[2].copy(classification = "NORMAL", reconciliationStatus = "OPEN",
            amount = -100.0, counterparty = "Müller Handel", purpose = "Rechnung", bookingDate = "2026-09-03")
        val receipt = receipts()[1].copy(id = 9, hauptkategorie = "Instandhaltung", bruttobetrag = 100.0,
            aussteller = tx.counterparty, beschreibung = tx.purpose, datum = tx.bookingDate, displayId = null,
            zahlungsart = "Unbekannt")
        val ties = listOf(receipt, receipt.copy(id = 3))
        assertEquals(3, BankReceiptMatcher.bestForTransaction(tx, ties)?.receiptId)
        assertEquals(3, BankReceiptMatcher.bestSuggestions(listOf(tx), ties, emptyList())[tx.transactionId]?.receiptId)
        assertEquals("tie-z", BankReceiptMatcher.bestForReceipt(receipt,
            listOf(tx.copy(transactionId = "tie-a"), tx.copy(transactionId = "tie-z")))?.transactionId)
        assertEquals(LegacyPreparedMatchOracle.bestForTransaction(tx, ties), BankReceiptMatcher.bestForTransaction(tx, ties))
    }

    @Test fun punctuationOnlyDisplayIdRetainsLegacyEmptyNormalizedSubstringBonus() {
        val tx = transactions()[2].copy(amount = -100.0, counterparty = "Müller Handel", purpose = "Eine Rechnung")
        val receipt = receipts()[1].copy(bruttobetrag = 100.0, aussteller = tx.counterparty, displayId = ". / #")
        val expected = LegacyPreparedMatchOracle.score(tx, receipt)
        assertTrue(expected.reasons.contains("Belegnummer im Verwendungszweck"))
        assertEquals(expected, BankReceiptMatcher.score(tx, receipt))
        assertEquals(LegacyPreparedMatchOracle.score(tx, receipt.copy(displayId = "  ")),
            BankReceiptMatcher.score(tx, receipt.copy(displayId = "  ")))
    }

    @Test fun newInputsArePreparedFreshAndSingleScoringStillAllowsCrossDirectionPairs() {
        val tx = transactions()[2].copy(amount = 890.0, counterparty = "Jörg Müller", purpose = "Miete März WE 1",
            propertyId = "property-1", bookingDate = "2026-03-03")
        val income = receipts()[0].copy(bruttobetrag = 890.0, aussteller = "Jörg Müller", mieter = "Jörg Müller",
            wohneinheit = "WE 1", propertyId = "property-1", beschreibung = "Miete März", datum = "2026-03-03",
            hauptkategorie = "Miete, Nebenkosten & Kaution")
        val expense = income.copy(hauptkategorie = "Instandhaltung")
        listOf(income, expense, income.copy(mieter = "Anderer Mieter", datum = "invalid", zahlungsart = "Bar")).forEach { changed ->
            assertEquals(LegacyPreparedMatchOracle.score(tx, changed), BankReceiptMatcher.score(tx, changed))
            assertEquals(LegacyPreparedMatchOracle.bestSuggestions(listOf(tx), listOf(changed), emptyList()),
                BankReceiptMatcher.bestSuggestions(listOf(tx), listOf(changed), emptyList()))
        }
    }

    @Test fun numericAndDateThresholdsPreserveLegacyScoreAndReasonOrdering() {
        val tx = transactions()[2].copy(amount = -100.0, counterparty = "Müller Handel", bookingDate = "2026-09-03")
        val base = receipts()[1].copy(bruttobetrag = 100.0, aussteller = tx.counterparty,
            hauptkategorie = "Instandhaltung", zahlungsart = "Unbekannt")
        listOf(100.0, 100.009999, 100.01, 101.0, 101.000001, 105.0, 105.000001,
            0.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { amount ->
            listOf(0L, 1L, 2L, 3L, 5L, 6L, 14L, 15L).forEach { days ->
                val receipt = base.copy(bruttobetrag = amount, datum = LocalDate.parse(tx.bookingDate).plusDays(days).toString())
                assertEquals("amount=$amount days=$days", LegacyPreparedMatchOracle.score(tx, receipt),
                    BankReceiptMatcher.score(tx, receipt))
            }
        }
    }

    private fun transactions(): List<BankTransaction> = (0 until 24).map { index ->
        BankTransaction(transactionId = "prepared-tx-${index.toString().padStart(2, '0')}", accountId = "account-${index % 2}",
            bookingDate = listOf("2026-09-03", "2025-09-03", "2026-03-01", "2026-09-05", "", "not-a-date", "2026-02-30", "2026-09-20")[index % 8],
            amount = listOf(100.0, -100.0, -100.50, 0.0, 890.0, -104.0, -1.0, -0.0)[index % 8],
            counterparty = listOf("Jörg Müller GmbH", "Mueller Handel", "", "  ", "ÖL-AG", "Firma & Söhne", "X Y", "Unbekannt")[index % 8],
            purpose = listOf("Miete März WE 1 BLG-007", "SEPA Lastschrift Reparatur", "Visa girocard Karte", "Miete September 2025",
                "Überweisung Rechnung INV/2026-42", "maintainer Straße 7", "", "ÄÖÜß: BLG-007; SEPA!")[index % 8],
            bankReference = listOf("REF-ÄÖÜ", "SEPA", "card", "", "REF/2026-42", "ÜBERWEISUNG")[index % 6],
            propertyId = listOf("property-1", "property-2", "")[index % 3], unitId = "unit-${index % 2}",
            classification = listOf("NORMAL", "NORMAL", "NORMAL", "PRIVATE_IGNORED", "TRANSFER", "unknown-legacy")[index % 6],
            reconciliationStatus = listOf("MATCHED", "PARTIAL", "OPEN", "REVIEW", "NO_RECEIPT_REQUIRED", "UNKNOWN")[index % 6])
    }

    private fun receipts(): List<Receipt> = (0 until 24).map { index ->
        Receipt(id = index + 1,
            aussteller = listOf("Jörg Müller GmbH", "Mueller Handel", "", "  ", "ÖL AG", "Firma Söhne", "X Y", "Andere Firma")[index % 8],
            datum = listOf("2026-09-03", "2025-09-03", "2026-03-01", "2026-09-05", "", "invalid", "2026-02-30", "2026-09-20")[index % 8],
            uhrzeit = "", bruttobetrag = listOf(100.0, 100.50, 104.0, 890.0, 0.0, 101.0, 1000.0, 1.0)[index % 8],
            hauptkategorie = listOf("Miete, Nebenkosten & Kaution", "Instandhaltung", "Sonstige Einnahmen", "miete, nebenkosten & kaution")[index % 4],
            unterkategorie = "synthetic", kontoNr = "4800",
            beschreibung = listOf("Miete März WE 1", "Reparatur SEPA Rechnung", "Visa Karte", "Miete September 2026",
                "INV/2026-42", "main Straße 7", "", "ÄÖÜß: BLG-007")[index % 8],
            displayId = listOf(null, "", " ", ". / #", "BLG-007", "INV/2026-42")[index % 6],
            internalId = listOf("receipt-stable-id", "", "INTERNAL/ÄÖÜ-1")[index % 3],
            mieter = listOf("Jörg Müller", "", "Andere Person", "Mueller Handel")[index % 4],
            wohneinheit = listOf("WE 1", "", "WE-2", "  ")[index % 4],
            propertyId = listOf("property-1", "property-2", "")[index % 3],
            zahlungsart = listOf("Bar", "Überweisung", "Lastschrift", "Karte", "girocard", "Kreditkarte Visa", "Unbekannt", "")[index % 8])
    }
}

// Deliberately frozen from main a4fd298. Do not replace calls with production helpers.
private object LegacyPreparedMatchOracle {
    fun bestSuggestions(
        transactions: List<BankTransaction>,
        receipts: List<Receipt>,
        links: List<BankReceiptLink>
    ): Map<String, BankMatchSuggestion> {
        val linkedTransactionIds = links.filter { it.status == BankLinkStatus.CONFIRMED }.map { it.transactionId }.toSet()
        return transactions.asSequence()
            .filter { eligible(it) }
            .filterNot { it.transactionId in linkedTransactionIds && it.reconciliationStatus == BankReconciliationStatus.MATCHED }
            .mapNotNull { transaction -> bestForTransaction(transaction, receipts, links)?.let { transaction.transactionId to it } }
            .toMap()
    }

    fun bestForTransaction(
        transaction: BankTransaction,
        receipts: List<Receipt>,
        links: List<BankReceiptLink> = emptyList()
    ): BankMatchSuggestion? {
        if (!eligible(transaction)) return null
        val linkedReceiptIds = links.filter { it.transactionId == transaction.transactionId }.map { it.receiptId }.toSet()
        return receipts.asSequence()
            .filterNot { it.id in linkedReceiptIds }
            .filter { receiptDirectionMatches(transaction, it) }
            .map { score(transaction, it) }
            .filter { it.score >= 45 }
            .maxWithOrNull(compareBy<BankMatchSuggestion> { it.score }.thenByDescending { it.receiptId })
    }

    fun bestForReceipt(
        receipt: Receipt,
        transactions: List<BankTransaction>,
        links: List<BankReceiptLink> = emptyList()
    ): BankMatchSuggestion? {
        val linkedTxIds = links.filter { it.receiptId == receipt.id || (receipt.internalId.isNotBlank() && it.receiptInternalId == receipt.internalId) }
            .map { it.transactionId }.toSet()
        return transactions.asSequence()
            .filterNot { it.transactionId in linkedTxIds }
            .filter { eligible(it) }
            .filter { receiptDirectionMatches(it, receipt) }
            .map { score(it, receipt) }
            .filter { it.score >= 45 }
            .maxWithOrNull(compareBy<BankMatchSuggestion> { it.score }.thenBy { it.transactionId })
    }

    fun score(transaction: BankTransaction, receipt: Receipt): BankMatchSuggestion {
        var score = 0
        val reasons = mutableListOf<String>()
        val amountDiff = kotlin.math.abs(transaction.absoluteAmount - receipt.bruttobetrag)
        when {
            amountDiff <= 0.01 -> { score += 55; reasons += "Betrag stimmt exakt" }
            amountDiff <= 1.0 -> { score += 35; reasons += "Betrag nahezu gleich" }
            transaction.absoluteAmount > 0 && amountDiff / transaction.absoluteAmount <= 0.05 -> { score += 15; reasons += "Betrag ähnlich" }
        }
        val dayDiff = dateDistance(transaction.bookingDate, receipt.datum)
        when {
            dayDiff == 0L -> { score += 20; reasons += "gleiches Datum" }
            dayDiff != null && dayDiff <= 2 -> { score += 16; reasons += "Datum ±2 Tage" }
            dayDiff != null && dayDiff <= 5 -> { score += 9; reasons += "Datum ±5 Tage" }
            dayDiff != null && dayDiff <= 14 -> score += 3
        }
        val partyScore = tokenSimilarity(transaction.counterparty, receipt.aussteller)
        if (partyScore >= 0.75) { score += 18; reasons += "Zahlungspartner passt" }
        else if (partyScore >= 0.45) { score += 10; reasons += "Zahlungspartner ähnlich" }
        val purposeNorm = normalize(transaction.purpose)
        val receiptTokens = normalize(listOf(receipt.beschreibung, receipt.aussteller, receipt.displayId.orEmpty(), receipt.internalId).joinToString(" "))
            .split(" ").filter { it.length >= 4 }.toSet()
        val purposeTokens = purposeNorm.split(" ").filter { it.length >= 4 }.toSet()
        val purposeOverlap = purposeTokens.intersect(receiptTokens).size
        if (purposeOverlap >= 2) { score += 7; reasons += "Verwendungszweck passt" }
        if (transaction.counterparty.isNotBlank() && receipt.aussteller.isNotBlank() && partyScore < 0.25 && purposeOverlap == 0) {
            score -= 35
            reasons += "Zahlungspartner weicht ab"
        }
        if (receipt.displayId?.takeIf { it.isNotBlank() }?.let { normalize(transaction.purpose).contains(normalize(it)) } == true) {
            score += 10; reasons += "Belegnummer im Verwendungszweck"
        }
        if (transaction.isIncome && isIncomeReceipt(receipt)) {
            val tenant = receipt.mieter.ifBlank { receipt.aussteller }
            val tenantSimilarity = tokenSimilarity(transaction.counterparty, tenant)
            if (tenantSimilarity >= 0.75) {
                score += 12
                reasons += "Mietername passt"
            } else if (tenantSimilarity >= 0.45) {
                score += 6
                reasons += "Mietername ähnlich"
            }
            val normalizedPurpose = normalize(transaction.purpose)
            val normalizedUnit = normalize(receipt.wohneinheit)
            if (normalizedUnit.isNotBlank() && normalizedPurpose.contains(normalizedUnit)) {
                score += 8
                reasons += "Wohneinheit im Verwendungszweck"
            }
            if (transaction.propertyId.isNotBlank() && transaction.propertyId == receipt.propertyId) {
                score += 5
                reasons += "Immobilie passt"
            }
            if (rentPeriodMatches(transaction.purpose, receipt.beschreibung, transaction.bookingDate, receipt.datum)) {
                score += 5
                reasons += "Mietmonat passt"
            }
        }
        val paymentAdjustment = LegacyPreparedPaymentOracle.adjustment(transaction, receipt)
        score += paymentAdjustment.points
        reasons += paymentAdjustment.reasons
        val confidence = when {
            score >= 85 -> "HOCH"
            score >= 65 -> "MITTEL"
            else -> "NIEDRIG"
        }
        return BankMatchSuggestion(transaction.transactionId, receipt.id, score.coerceIn(0, 100), confidence, reasons)
    }

    private fun receiptDirectionMatches(transaction: BankTransaction, receipt: Receipt): Boolean {
        val incomeReceipt = isIncomeReceipt(receipt)
        return if (transaction.isIncome) incomeReceipt else !incomeReceipt
    }

    private fun isIncomeReceipt(receipt: Receipt): Boolean =
        receipt.hauptkategorie.equals("Miete, Nebenkosten & Kaution", true) ||
            receipt.hauptkategorie.equals("Sonstige Einnahmen", true)

    private fun rentPeriodMatches(purpose: String, description: String, bookingDate: String, receiptDate: String): Boolean {
        val monthTokens = listOf(
            "januar", "februar", "maerz", "märz", "april", "mai", "juni",
            "juli", "august", "september", "oktober", "november", "dezember"
        )
        val left = normalize(purpose)
        val right = normalize(description)
        if (monthTokens.any { token -> normalize(token).let { it in left && it in right } }) return true
        val bookingMonth = runCatching { LocalDate.parse(bookingDate).monthValue }.getOrNull()
        val receiptMonth = runCatching { LocalDate.parse(receiptDate).monthValue }.getOrNull()
        return bookingMonth != null && bookingMonth == receiptMonth
    }

    private fun dateDistance(a: String, b: String): Long? = runCatching {
        kotlin.math.abs(ChronoUnit.DAYS.between(LocalDate.parse(a), LocalDate.parse(b)))
    }.getOrNull()

    private fun tokenSimilarity(a: String, b: String): Double {
        val aa = normalize(a).split(" ").filter { it.length >= 3 }.toSet()
        val bb = normalize(b).split(" ").filter { it.length >= 3 }.toSet()
        if (aa.isEmpty() || bb.isEmpty()) return 0.0
        return aa.intersect(bb).size.toDouble() / minOf(aa.size, bb.size).toDouble()
    }

    private fun normalize(value: String): String = value.lowercase(java.util.Locale.GERMANY)
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun eligible(transaction: BankTransaction): Boolean =
        transaction.classification !in setOf("PRIVATE_IGNORED", "TRANSFER") &&
            transaction.reconciliationStatus != "NO_RECEIPT_REQUIRED"
}

private object LegacyPreparedPaymentOracle {
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
