package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.security.MessageDigest
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

object BankReconciliationStatus {
    const val OPEN = "OPEN"
    const val PARTIAL = "PARTIAL"
    const val MATCHED = "MATCHED"
    const val NO_RECEIPT_REQUIRED = "NO_RECEIPT_REQUIRED"
    const val REVIEW = "REVIEW"
}

object BankLinkStatus {
    const val CONFIRMED = "CONFIRMED"
}

object BankLinkSource {
    const val AUTO_VORSCHLAG = "AUTO_VORSCHLAG"
    const val KI_VORSCHLAG = "KI_VORSCHLAG"
    const val NUTZER_BESTAETIGT = "NUTZER_BESTAETIGT"
    const val MANUELL = "MANUELL"
}

@Entity(tableName = "bank_accounts")
data class BankAccount(
    @PrimaryKey val accountId: String,
    val displayName: String,
    val bankName: String = "",
    val accountHolder: String = "",
    val iban: String = "",
    val currency: String = "EUR",
    val source: String = "CSV",
    val active: Boolean = true,
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Entity(
    tableName = "bank_transactions",
    indices = [
        Index(value = ["accountId"], name = "index_bank_transactions_accountId"),
        Index(value = ["bookingDate"], name = "index_bank_transactions_bookingDate"),
        Index(value = ["reconciliationStatus"], name = "index_bank_transactions_reconciliationStatus")
    ]
)
data class BankTransaction(
    @PrimaryKey val transactionId: String,
    val accountId: String,
    val bookingDate: String,
    val valueDate: String = "",
    val amount: Double,
    val currency: String = "EUR",
    val counterparty: String = "",
    val counterpartyIban: String = "",
    val purpose: String = "",
    val bankReference: String = "",
    val source: String = "CSV",
    val propertyId: String = "",
    val unitId: String = "",
    val importFileName: String = "",
    val importRunId: String = "",
    val reconciliationStatus: String = BankReconciliationStatus.OPEN,
    val noReceiptReason: String = "",
    val importedAt: String = ""
) {
    val isIncome: Boolean get() = amount > 0.0
    val absoluteAmount: Double get() = kotlin.math.abs(amount)
}

@Entity(
    tableName = "bank_receipt_links",
    indices = [
        Index(value = ["transactionId"], name = "index_bank_receipt_links_transactionId"),
        Index(value = ["receiptId"], name = "index_bank_receipt_links_receiptId"),
        Index(value = ["receiptInternalId"], name = "index_bank_receipt_links_receiptInternalId")
    ]
)
data class BankReceiptLink(
    @PrimaryKey val linkId: String,
    val transactionId: String,
    val receiptId: Int,
    val receiptInternalId: String = "",
    val allocatedAmount: Double,
    val status: String = BankLinkStatus.CONFIRMED,
    val source: String = BankLinkSource.NUTZER_BESTAETIGT,
    val createdAt: String = ""
)

@Dao
interface BankDao {
    @Query("SELECT * FROM bank_accounts ORDER BY active DESC, displayName COLLATE NOCASE")
    fun observeAccounts(): Flow<List<BankAccount>>

    @Query("SELECT * FROM bank_accounts ORDER BY active DESC, displayName COLLATE NOCASE")
    suspend fun getAllAccounts(): List<BankAccount>

    @Query("SELECT * FROM bank_accounts WHERE accountId = :accountId LIMIT 1")
    suspend fun getAccount(accountId: String): BankAccount?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: BankAccount)

    @Query("SELECT * FROM bank_transactions ORDER BY bookingDate DESC, transactionId DESC")
    fun observeTransactions(): Flow<List<BankTransaction>>

    @Query("SELECT * FROM bank_transactions ORDER BY bookingDate DESC, transactionId DESC")
    suspend fun getAllTransactions(): List<BankTransaction>

    @Query("SELECT * FROM bank_transactions WHERE transactionId = :transactionId LIMIT 1")
    suspend fun getTransaction(transactionId: String): BankTransaction?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactions(transactions: List<BankTransaction>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransaction(transaction: BankTransaction)

    @Query("UPDATE bank_transactions SET reconciliationStatus = :status, noReceiptReason = :reason WHERE transactionId = :transactionId")
    suspend fun updateTransactionStatus(transactionId: String, status: String, reason: String = "")

    @Query("SELECT * FROM bank_receipt_links ORDER BY createdAt DESC, linkId")
    fun observeLinks(): Flow<List<BankReceiptLink>>

    @Query("SELECT * FROM bank_receipt_links ORDER BY createdAt DESC, linkId")
    suspend fun getAllLinks(): List<BankReceiptLink>

    @Query("SELECT * FROM bank_receipt_links WHERE transactionId = :transactionId")
    suspend fun getLinksForTransaction(transactionId: String): List<BankReceiptLink>

    @Query("SELECT * FROM bank_receipt_links WHERE receiptId = :receiptId OR (:receiptInternalId != '' AND receiptInternalId = :receiptInternalId)")
    suspend fun getLinksForReceipt(receiptId: Int, receiptInternalId: String = ""): List<BankReceiptLink>

    @Query("SELECT * FROM bank_receipt_links WHERE linkId = :linkId LIMIT 1")
    suspend fun getLink(linkId: String): BankReceiptLink?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLink(link: BankReceiptLink)

    @Query("DELETE FROM bank_receipt_links WHERE linkId = :linkId")
    suspend fun deleteLink(linkId: String)

    @Query("DELETE FROM bank_receipt_links")
    suspend fun clearLinks()

    @Query("UPDATE bank_transactions SET reconciliationStatus = 'OPEN', noReceiptReason = '' WHERE reconciliationStatus IN ('MATCHED','PARTIAL')")
    suspend fun reopenLinkedTransactions()
}

data class BankImportBatch(
    val account: BankAccount,
    val transactions: List<BankTransaction>,
    val format: String,
    val skippedRows: Int = 0,
    val errorRows: Int = 0
)

data class BankMatchSuggestion(
    val transactionId: String,
    val receiptId: Int,
    val score: Int,
    val confidence: String,
    val reasons: List<String>
)

object BankTransactionIdentity {
    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun accountId(source: String, iban: String, fallbackName: String): String =
        "bank-" + sha256("${source.trim().uppercase()}|${normalize(iban)}|${normalize(fallbackName)}").take(24)

    fun importRunId(source: String, fileName: String, importedAt: String): String =
        "import-" + sha256("${source.trim().uppercase()}|${normalize(fileName)}|${importedAt.trim()}").take(24)

    fun transactionId(
        accountId: String,
        bookingDate: String,
        valueDate: String,
        amount: Double,
        currency: String,
        counterparty: String,
        counterpartyIban: String,
        purpose: String,
        bankReference: String,
        occurrence: Int = 0
    ): String {
        val canonical = listOf(
            accountId,
            bookingDate.trim(),
            valueDate.trim(),
            "%.2f".format(java.util.Locale.ROOT, amount),
            currency.trim().uppercase(),
            normalize(counterparty),
            normalize(counterpartyIban),
            normalize(purpose),
            normalize(bankReference),
            occurrence.toString()
        ).joinToString("|")
        return "tx-" + sha256(canonical).take(32)
    }

    private fun normalize(value: String): String =
        value.lowercase(java.util.Locale.GERMANY)
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}

object BankImportParser {
    private val bookingAliases = listOf("buchungstag", "buchungsdatum", "datum", "booking date")
    private val valueAliases = listOf("wertstellung", "valutadatum", "value date")
    private val counterpartyAliases = listOf(
        "auftraggeber empfaenger", "auftraggeber/empfänger", "auftraggeber/empfaenger",
        "zahlungspflichtiger zahlungsempfaenger", "zahlungspflichtiger/zahlungsempfänger",
        "empfaenger", "empfänger", "auftraggeber", "name", "counterparty"
    )
    private val purposeAliases = listOf("verwendungszweck", "buchungstext", "text", "purpose", "umsatztext")
    private val amountAliases = listOf("betrag", "umsatz", "amount")
    private val currencyAliases = listOf("waehrung", "währung", "currency")
    private val ibanAliases = listOf("iban", "gegenkonto iban", "empfaenger iban", "empfänger iban")
    private val referenceAliases = listOf("referenz", "kundenreferenz", "end-to-end-referenz", "endtoendid", "bankreferenz")
    private val ownIbanAliases = listOf("konto iban", "kontonummer/iban", "eigene iban")

    fun parseCsv(
        text: String,
        fallbackAccountName: String = "Importiertes Konto",
        importedAt: String = "",
        importFileName: String = "",
        importRunId: String = "",
        propertyId: String = "",
        unitId: String = ""
    ): BankImportBatch {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').lines().filter { it.isNotBlank() }
        require(lines.size >= 2) { "CSV enthält keine Buchungen." }
        val delimiter = detectDelimiter(lines.first())
        val rows = lines.map { splitCsvLine(it, delimiter) }
        val headers = rows.first().map(::normalizeHeader)
        fun indexOf(aliases: List<String>): Int = aliases.asSequence()
            .map(::normalizeHeader)
            .map(headers::indexOf)
            .firstOrNull { it >= 0 } ?: -1

        val bookingIdx = indexOf(bookingAliases)
        val valueIdx = indexOf(valueAliases)
        val counterpartyIdx = indexOf(counterpartyAliases)
        val purposeIdx = indexOf(purposeAliases)
        val amountIdx = indexOf(amountAliases)
        val currencyIdx = indexOf(currencyAliases)
        val ibanIdx = indexOf(ibanAliases)
        val refIdx = indexOf(referenceAliases)
        val ownIbanIdx = indexOf(ownIbanAliases)
        require(bookingIdx >= 0) { "CSV-Spalte für Buchungsdatum wurde nicht erkannt." }
        require(amountIdx >= 0) { "CSV-Spalte für Betrag wurde nicht erkannt." }

        val ownIban = rows.drop(1).firstNotNullOfOrNull { row ->
            row.getOrNull(ownIbanIdx).orEmpty().trim().takeIf { ownIbanIdx >= 0 && it.isNotBlank() }
        }.orEmpty()
        val accountId = BankTransactionIdentity.accountId("CSV", ownIban, fallbackAccountName)
        val seenCanonical = mutableMapOf<String, Int>()
        var errorRows = 0
        val effectiveRunId = importRunId.ifBlank {
            BankTransactionIdentity.importRunId("CSV", importFileName.ifBlank { fallbackAccountName }, importedAt)
        }
        val transactions = rows.drop(1).mapNotNull { row ->
            val rawAmount = row.getOrNull(amountIdx).orEmpty()
            val amount = parseAmount(rawAmount)
            val bookingDate = normalizeDate(row.getOrNull(bookingIdx).orEmpty())
            if (amount == null || bookingDate.isBlank()) {
                errorRows++
                return@mapNotNull null
            }
            val valueDate = normalizeDate(row.getOrNull(valueIdx).orEmpty())
            val counterparty = row.getOrNull(counterpartyIdx).orEmpty().trim()
            val purpose = row.getOrNull(purposeIdx).orEmpty().trim()
            val currency = row.getOrNull(currencyIdx).orEmpty().trim().ifBlank { "EUR" }
            val iban = row.getOrNull(ibanIdx).orEmpty().trim()
            val reference = row.getOrNull(refIdx).orEmpty().trim()
            val canonical = listOf(bookingDate, valueDate, amount.toString(), counterparty, iban, purpose, reference).joinToString("|")
            val occurrence = seenCanonical.getOrDefault(canonical, 0)
            seenCanonical[canonical] = occurrence + 1
            BankTransaction(
                transactionId = BankTransactionIdentity.transactionId(
                    accountId, bookingDate, valueDate, amount, currency, counterparty, iban, purpose, reference, occurrence
                ),
                accountId = accountId,
                bookingDate = bookingDate,
                valueDate = valueDate,
                amount = amount,
                currency = currency,
                counterparty = counterparty,
                counterpartyIban = iban,
                purpose = purpose,
                bankReference = reference,
                source = "CSV",
                propertyId = propertyId,
                unitId = unitId,
                importFileName = importFileName,
                importRunId = effectiveRunId,
                importedAt = importedAt
            )
        }
        require(transactions.isNotEmpty()) { "Keine gültigen CSV-Buchungen erkannt." }
        return BankImportBatch(
            account = BankAccount(
                accountId = accountId,
                displayName = fallbackAccountName,
                iban = ownIban,
                currency = transactions.firstOrNull()?.currency ?: "EUR",
                source = "CSV",
                createdAt = importedAt,
                updatedAt = importedAt
            ),
            transactions = transactions,
            format = "CSV",
            skippedRows = 0,
            errorRows = errorRows
        )
    }

    fun parseCamt053(
        xml: String,
        fallbackAccountName: String = "Importiertes Konto",
        importedAt: String = "",
        importFileName: String = "",
        importRunId: String = "",
        propertyId: String = "",
        unitId: String = ""
    ): BankImportBatch {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
        runCatching { factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val accountIban = firstText(doc.documentElement, "IBAN")
        val currency = firstText(doc.documentElement, "Ccy").ifBlank { "EUR" }
        val accountHolder = firstText(doc.documentElement, "Ownr").trim()
        val accountId = BankTransactionIdentity.accountId("CAMT053", accountIban, fallbackAccountName)
        val nodes = doc.getElementsByTagNameNS("*", "Ntry")
        val seenCanonical = mutableMapOf<String, Int>()
        var errorRows = 0
        val effectiveRunId = importRunId.ifBlank {
            BankTransactionIdentity.importRunId("CAMT053", importFileName.ifBlank { fallbackAccountName }, importedAt)
        }
        val transactions = buildList {
            for (index in 0 until nodes.length) {
                val entry = nodes.item(index) as? Element
                if (entry == null) {
                    errorRows++
                    continue
                }
                val amountElement = entry.getElementsByTagNameNS("*", "Amt").item(0) as? Element
                val rawAmount = amountElement?.textContent?.trim()?.replace(',', '.')?.toDoubleOrNull()
                if (amountElement == null || rawAmount == null) {
                    errorRows++
                    continue
                }
                val creditDebit = firstText(entry, "CdtDbtInd").uppercase()
                val signedAmount = if (creditDebit == "DBIT") -kotlin.math.abs(rawAmount) else kotlin.math.abs(rawAmount)
                val bookingDate = firstDate(entry, "BookgDt")
                if (bookingDate.isBlank()) {
                    errorRows++
                    continue
                }
                val valueDate = firstDate(entry, "ValDt")
                val reference = firstNonBlank(
                    firstText(entry, "AcctSvcrRef"),
                    firstText(entry, "NtryRef"),
                    firstText(entry, "EndToEndId")
                )
                val purpose = firstNonBlank(
                    allTexts(entry, "Ustrd").joinToString(" ").trim(),
                    firstText(entry, "AddtlNtryInf")
                )
                val parties = if (signedAmount < 0) listOf("Cdtr", "UltmtCdtr") else listOf("Dbtr", "UltmtDbtr")
                val counterparty = parties.asSequence().map { firstText(entry, it) }.firstOrNull { it.isNotBlank() }.orEmpty()
                val iban = findCounterpartyIban(entry, signedAmount < 0)
                val canonical = listOf(bookingDate, valueDate, signedAmount.toString(), counterparty, iban, purpose, reference).joinToString("|")
                val occurrence = seenCanonical.getOrDefault(canonical, 0)
                seenCanonical[canonical] = occurrence + 1
                add(
                    BankTransaction(
                        transactionId = BankTransactionIdentity.transactionId(
                            accountId, bookingDate, valueDate, signedAmount, currency, counterparty, iban, purpose, reference, occurrence
                        ),
                        accountId = accountId,
                        bookingDate = bookingDate,
                        valueDate = valueDate,
                        amount = signedAmount,
                        currency = amountElement.getAttribute("Ccy").ifBlank { currency },
                        counterparty = counterparty,
                        counterpartyIban = iban,
                        purpose = purpose,
                        bankReference = reference,
                        source = "CAMT053",
                        propertyId = propertyId,
                        unitId = unitId,
                        importFileName = importFileName,
                        importRunId = effectiveRunId,
                        importedAt = importedAt
                    )
                )
            }
        }
        require(transactions.isNotEmpty()) { "Keine CAMT.053-Buchungen erkannt." }
        return BankImportBatch(
            account = BankAccount(
                accountId = accountId,
                displayName = fallbackAccountName,
                accountHolder = accountHolder,
                iban = accountIban,
                currency = currency,
                source = "CAMT053",
                createdAt = importedAt,
                updatedAt = importedAt
            ),
            transactions = transactions,
            format = "CAMT.053",
            skippedRows = 0,
            errorRows = errorRows
        )
    }

    private fun detectDelimiter(line: String): Char =
        listOf(';', '\t', ',').maxByOrNull { candidate -> line.count { it == candidate } } ?: ';'

    internal fun splitCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> { current.append('"'); index++ }
                char == '"' -> quoted = !quoted
                char == delimiter && !quoted -> { result += current.toString().trim(); current.clear() }
                else -> current.append(char)
            }
            index++
        }
        result += current.toString().trim()
        return result
    }

    internal fun parseAmount(raw: String): Double? {
        var value = raw.trim().replace("\u00A0", "").replace(" ", "").replace("€", "")
        if (value.isBlank()) return null
        val negativeParentheses = value.startsWith("(") && value.endsWith(")")
        value = value.removePrefix("(").removeSuffix(")")
        val lastComma = value.lastIndexOf(',')
        val lastDot = value.lastIndexOf('.')
        value = when {
            lastComma >= 0 && lastDot >= 0 && lastComma > lastDot -> value.replace(".", "").replace(',', '.')
            lastComma >= 0 && lastDot < 0 -> value.replace(',', '.')
            lastDot >= 0 && lastComma < 0 && value.substringAfterLast('.').length == 3 -> value.replace(".", "")
            else -> value.replace(",", "")
        }
        return value.toDoubleOrNull()?.let { if (negativeParentheses) -kotlin.math.abs(it) else it }
    }

    internal fun normalizeDate(raw: String): String {
        val value = raw.trim()
        if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)) return value
        val formats = listOf("dd.MM.yyyy", "d.M.yyyy", "dd/MM/yyyy", "d/M/yyyy")
        return formats.asSequence().mapNotNull { pattern ->
            runCatching { java.time.format.DateTimeFormatter.ofPattern(pattern).let { LocalDate.parse(value, it).toString() } }.getOrNull()
        }.firstOrNull().orEmpty()
    }

    private fun normalizeHeader(raw: String): String =
        raw.trim().removePrefix("\uFEFF").lowercase(java.util.Locale.GERMANY)
            .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            .replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun firstText(element: Element, localName: String): String {
        val nodes = element.getElementsByTagNameNS("*", localName)
        return if (nodes.length == 0) "" else nodes.item(0).textContent.orEmpty().trim()
    }

    private fun allTexts(element: Element, localName: String): List<String> {
        val nodes = element.getElementsByTagNameNS("*", localName)
        return buildList {
            for (i in 0 until nodes.length) nodes.item(i).textContent?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    private fun firstDate(entry: Element, parentLocalName: String): String {
        val parents = entry.getElementsByTagNameNS("*", parentLocalName)
        if (parents.length == 0) return ""
        val parent = parents.item(0) as? Element ?: return ""
        return firstNonBlank(firstText(parent, "Dt"), firstText(parent, "DtTm").take(10))
    }

    private fun findCounterpartyIban(entry: Element, outgoing: Boolean): String {
        val tag = if (outgoing) "CdtrAcct" else "DbtrAcct"
        val parents = entry.getElementsByTagNameNS("*", tag)
        if (parents.length == 0) return ""
        return (parents.item(0) as? Element)?.let { firstText(it, "IBAN") }.orEmpty()
    }

    private fun firstNonBlank(vararg values: String): String = values.firstOrNull { it.isNotBlank() }.orEmpty()
}

object BankReceiptMatcher {
    fun bestSuggestions(
        transactions: List<BankTransaction>,
        receipts: List<Receipt>,
        links: List<BankReceiptLink>
    ): Map<String, BankMatchSuggestion> {
        val linkedTransactionIds = links.filter { it.status == BankLinkStatus.CONFIRMED }.map { it.transactionId }.toSet()
        return transactions.asSequence()
            .filter { it.reconciliationStatus != BankReconciliationStatus.NO_RECEIPT_REQUIRED }
            .filterNot { it.transactionId in linkedTransactionIds && it.reconciliationStatus == BankReconciliationStatus.MATCHED }
            .mapNotNull { transaction -> bestForTransaction(transaction, receipts, links)?.let { transaction.transactionId to it } }
            .toMap()
    }

    fun bestForTransaction(
        transaction: BankTransaction,
        receipts: List<Receipt>,
        links: List<BankReceiptLink> = emptyList()
    ): BankMatchSuggestion? {
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
            .filter { it.reconciliationStatus != BankReconciliationStatus.NO_RECEIPT_REQUIRED }
            .filter { receiptDirectionMatches(it, receipt) }
            .map { score(it, receipt) }
            .filter { it.score >= 45 }
            .maxWithOrNull(compareBy<BankMatchSuggestion> { it.score }.thenBy { it.transactionId })
    }

    fun rankReceipts(transaction: BankTransaction, receipts: List<Receipt>, links: List<BankReceiptLink>): List<BankMatchSuggestion> =
        receipts.asSequence()
            .filter { receiptDirectionMatches(transaction, it) }
            .map { score(transaction, it) }
            .sortedByDescending { it.score }
            .take(30)
            .toList()

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
        val confidence = when {
            score >= 85 -> "HOCH"
            score >= 65 -> "MITTEL"
            else -> "NIEDRIG"
        }
        return BankMatchSuggestion(transaction.transactionId, receipt.id, score.coerceAtMost(100), confidence, reasons)
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
}

object BankPaymentMethodPolicy {
    data class Decision(val method: String, val source: String = "BANKABGLEICH", val confidence: Double = 1.0)

    fun fromConfirmedBankMatch(transaction: BankTransaction, receipt: Receipt): Decision? {
        if (receipt.zahlungsartQuelle.equals("NUTZER_BESTAETIGT", true)) return null
        val text = "${transaction.purpose} ${transaction.counterparty}".lowercase(java.util.Locale.GERMANY)
        val method = if (text.contains("lastschrift")) "Lastschrift" else "Überweisung"
        return Decision(method)
    }
}

object BankLinkPolicy {
    data class Allocation(
        val allowed: Boolean,
        val amount: Double,
        val remainingTransaction: Double,
        val remainingReceipt: Double,
        val reason: String = ""
    )

    fun propose(transaction: BankTransaction, receipt: Receipt, existingLinks: List<BankReceiptLink>): Allocation {
        val txAllocated = existingLinks.filter { it.transactionId == transaction.transactionId && it.status == BankLinkStatus.CONFIRMED }
            .sumOf { it.allocatedAmount }
        val receiptAllocated = existingLinks.filter {
            (it.receiptId == receipt.id || (receipt.internalId.isNotBlank() && it.receiptInternalId == receipt.internalId)) &&
                it.status == BankLinkStatus.CONFIRMED
        }.sumOf { it.allocatedAmount }
        val txRemaining = (transaction.absoluteAmount - txAllocated).coerceAtLeast(0.0)
        val receiptRemaining = (receipt.bruttobetrag - receiptAllocated).coerceAtLeast(0.0)
        val amount = minOf(txRemaining, receiptRemaining)
        return if (amount > 0.009) Allocation(true, amount, txRemaining, receiptRemaining)
        else Allocation(false, 0.0, txRemaining, receiptRemaining, "Buchung oder Beleg ist bereits vollständig zugeordnet.")
    }

    fun statusFor(transaction: BankTransaction, existingLinks: List<BankReceiptLink>): String {
        val allocated = existingLinks
            .filter { it.transactionId == transaction.transactionId && it.status == BankLinkStatus.CONFIRMED }
            .sumOf { it.allocatedAmount }
        return when {
            allocated <= 0.009 -> BankReconciliationStatus.OPEN
            allocated + 0.01 >= transaction.absoluteAmount -> BankReconciliationStatus.MATCHED
            else -> BankReconciliationStatus.PARTIAL
        }
    }

    fun linkId(transactionId: String, receiptId: Int, receiptInternalId: String): String =
        "link-" + BankTransactionIdentity.sha256("$transactionId|$receiptId|$receiptInternalId").take(32)
}
