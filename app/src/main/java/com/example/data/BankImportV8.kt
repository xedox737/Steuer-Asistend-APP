package com.example.data

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs

/** Supported bank import formats are detected from XML content, never from the file extension alone. */
enum class BankCamtFormat(val namespace: String, val source: String, val label: String) {
    CAMT_052_001_08("urn:iso:std:iso:20022:tech:xsd:camt.052.001.08", "CAMT052", "CAMT.052 V8"),
    CAMT_053_001_08("urn:iso:std:iso:20022:tech:xsd:camt.053.001.08", "CAMT053", "CAMT.053 V8"),
    UNSUPPORTED("", "", "Nicht unterstützt")
}

object BankCamtV8FormatDetector {
    fun detect(xml: String): BankCamtFormat {
        val document = BankCamtV8Xml.parseSecure(xml)
        return when (document.documentElement?.namespaceURI.orEmpty()) {
            BankCamtFormat.CAMT_052_001_08.namespace -> BankCamtFormat.CAMT_052_001_08
            BankCamtFormat.CAMT_053_001_08.namespace -> BankCamtFormat.CAMT_053_001_08
            else -> BankCamtFormat.UNSUPPORTED
        }
    }
}

object BankCamtV8Xml {
    fun parseSecure(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isXIncludeAware = false
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        runCatching { factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
        runCatching { factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }
}

data class BankCamtV8Decision(
    val splitMultiTxDetails: Boolean,
    val reason: String
)

/**
 * CAMT.052/053 V8 parser that writes the existing BankTransaction/BankAccount SSOT.
 * Legacy compatibility: CAMT account ids deliberately keep the historical CAMT053 identity salt,
 * so a CAMT.053 reimport keeps exactly the same account/transaction ids and a CAMT.052 file for
 * the same own IBAN resolves to the same account instead of creating a parallel CAMT account.
 */
fun BankImportParser.parseCamtV8(
    xml: String,
    fallbackAccountName: String = "Importiertes Konto",
    importedAt: String = "",
    importFileName: String = "",
    importRunId: String = "",
    propertyId: String = "",
    unitId: String = ""
): BankImportBatch {
    val document = BankCamtV8Xml.parseSecure(xml)
    val format = when (document.documentElement?.namespaceURI.orEmpty()) {
        BankCamtFormat.CAMT_052_001_08.namespace -> BankCamtFormat.CAMT_052_001_08
        BankCamtFormat.CAMT_053_001_08.namespace -> BankCamtFormat.CAMT_053_001_08
        else -> throw IllegalArgumentException("Nicht unterstütztes CAMT-Format oder CAMT-Version.")
    }
    val root = document.documentElement
    val accountElement = firstElement(root, "Acct")
    val accountIban = accountElement?.let { firstText(firstElement(it, "Id") ?: it, "IBAN") }.orEmpty()
    val currency = accountElement?.let { firstText(it, "Ccy") }.orEmpty().ifBlank { "EUR" }
    val owner = accountElement?.let { firstElement(it, "Ownr") }?.let { firstText(it, "Nm") }.orEmpty()
    val bankName = accountElement?.let { firstElement(it, "Svcr") }?.let { firstText(it, "Nm") }.orEmpty()
    val accountId = BankTransactionIdentity.accountId("CAMT053", accountIban, fallbackAccountName)
    val effectiveRunId = importRunId.ifBlank {
        BankTransactionIdentity.importRunId(format.source, importFileName.ifBlank { fallbackAccountName }, importedAt)
    }
    val entries = root.getElementsByTagNameNS("*", "Ntry")
    val seenCanonical = mutableMapOf<String, Int>()
    var skippedRows = 0
    var errorRows = 0
    val transactions = buildList {
        for (entryIndex in 0 until entries.length) {
            val entry = entries.item(entryIndex) as? Element ?: run { errorRows++; continue }
            val status = entryStatus(entry)
            if (format == BankCamtFormat.CAMT_052_001_08 && status != "BOOK") {
                skippedRows++
                continue
            }
            if (format == BankCamtFormat.CAMT_053_001_08 && status.isNotBlank() && status != "BOOK") {
                skippedRows++
                continue
            }
            val entryAmountElement = directOrFirstElement(entry, "Amt")
            val entryRaw = entryAmountElement?.textContent?.trim()?.replace(',', '.')?.toDoubleOrNull()
            val bookingDate = firstDate(entry, "BookgDt")
            if (entryRaw == null || bookingDate.isBlank()) {
                errorRows++
                continue
            }
            val entryDirection = firstText(entry, "CdtDbtInd").uppercase(Locale.ROOT)
            if (entryDirection != "DBIT" && entryDirection != "CRDT") {
                errorRows++
                continue
            }
            val entrySigned = signed(entryRaw, entryDirection)
            val valueDate = firstDate(entry, "ValDt")
            val entryCurrency = entryAmountElement.getAttribute("Ccy").ifBlank { currency }
            val txDetails = directTxDetails(entry)
            val splitDecision = multiTxDetailsDecision(entrySigned, txDetails, entryDirection)
            if (splitDecision.splitMultiTxDetails) {
                txDetails.forEach { detail ->
                    val detailAmountElement = directOrFirstElement(detail, "Amt") ?: return@forEach
                    val detailRaw = detailAmountElement.textContent.trim().replace(',', '.').toDoubleOrNull() ?: return@forEach
                    val detailDirection = firstText(detail, "CdtDbtInd").uppercase(Locale.ROOT).ifBlank { entryDirection }
                    val detailSigned = signed(detailRaw, detailDirection)
                    addTransactionFromElement(
                        target = this,
                        data = detail,
                        accountId = accountId,
                        bookingDate = bookingDate,
                        valueDate = valueDate,
                        amount = detailSigned,
                        currency = detailAmountElement.getAttribute("Ccy").ifBlank { entryCurrency },
                        format = format,
                        propertyId = propertyId,
                        unitId = unitId,
                        importFileName = importFileName,
                        importRunId = effectiveRunId,
                        importedAt = importedAt,
                        seenCanonical = seenCanonical
                    )
                }
            } else {
                addTransactionFromElement(
                    target = this,
                    data = entry,
                    accountId = accountId,
                    bookingDate = bookingDate,
                    valueDate = valueDate,
                    amount = entrySigned,
                    currency = entryCurrency,
                    format = format,
                    propertyId = propertyId,
                    unitId = unitId,
                    importFileName = importFileName,
                    importRunId = effectiveRunId,
                    importedAt = importedAt,
                    seenCanonical = seenCanonical
                )
            }
        }
    }
    if (transactions.isEmpty() && errorRows == 0 && skippedRows > 0) {
        throw IllegalArgumentException("Keine gebuchten CAMT-Umsätze (BOOK) gefunden.")
    }
    require(transactions.isNotEmpty()) { "Keine gültigen ${format.label}-Buchungen erkannt." }
    return BankImportBatch(
        account = BankAccount(
            accountId = accountId,
            displayName = fallbackAccountName,
            bankName = bankName,
            accountHolder = owner,
            iban = accountIban,
            currency = currency,
            source = format.source,
            createdAt = importedAt,
            updatedAt = importedAt
        ),
        transactions = transactions,
        format = format.label,
        skippedRows = skippedRows,
        errorRows = errorRows
    )
}

internal fun multiTxDetailsDecision(entrySignedAmount: Double, details: List<Element>, fallbackDirection: String): BankCamtV8Decision {
    if (details.size <= 1) return BankCamtV8Decision(false, "Ntry enthält höchstens ein TxDtls; Ntry-Semantik bleibt erhalten.")
    val detailAmounts = details.map { detail ->
        val amount = directOrFirstElement(detail, "Amt")?.textContent?.trim()?.replace(',', '.')?.toDoubleOrNull()
            ?: return BankCamtV8Decision(false, "Mehrere TxDtls, aber nicht jeder Einzelbetrag ist eindeutig vorhanden.")
        val direction = firstText(detail, "CdtDbtInd").uppercase(Locale.ROOT).ifBlank { fallbackDirection }
        if (direction != "DBIT" && direction != "CRDT") return BankCamtV8Decision(false, "Mehrere TxDtls mit uneindeutiger Zahlungsrichtung.")
        signed(amount, direction)
    }
    return if (abs(detailAmounts.sum() - entrySignedAmount) <= 0.01) {
        BankCamtV8Decision(true, "TxDtls werden aufgeteilt, weil die signierten Einzelbeträge den Ntry-Gesamtbetrag exakt ergeben.")
    } else {
        BankCamtV8Decision(false, "TxDtls werden nicht aufgeteilt, weil die Einzelbeträge nicht eindeutig zum Ntry-Gesamtbetrag passen.")
    }
}

private fun addTransactionFromElement(
    target: MutableList<BankTransaction>,
    data: Element,
    accountId: String,
    bookingDate: String,
    valueDate: String,
    amount: Double,
    currency: String,
    format: BankCamtFormat,
    propertyId: String,
    unitId: String,
    importFileName: String,
    importRunId: String,
    importedAt: String,
    seenCanonical: MutableMap<String, Int>
) {
    val reference = firstNonBlank(
        firstText(data, "AcctSvcrRef"),
        firstText(data, "NtryRef"),
        firstText(data, "EndToEndId")
    )
    val purpose = firstNonBlank(allTexts(data, "Ustrd").joinToString(" ").trim(), firstText(data, "AddtlNtryInf"))
    val outgoing = amount < 0
    val counterparty = if (outgoing) {
        firstNonBlank(nameWithin(data, "Cdtr"), nameWithin(data, "UltmtCdtr"))
    } else {
        firstNonBlank(nameWithin(data, "Dbtr"), nameWithin(data, "UltmtDbtr"))
    }
    val counterpartyIban = counterpartyIban(data, outgoing)
    val canonical = listOf(bookingDate, valueDate, amount.toString(), counterparty, counterpartyIban, purpose, reference).joinToString("|")
    val occurrence = seenCanonical.getOrDefault(canonical, 0)
    seenCanonical[canonical] = occurrence + 1
    target += BankTransaction(
        transactionId = BankTransactionIdentity.transactionId(
            accountId, bookingDate, valueDate, amount, currency, counterparty, counterpartyIban, purpose, reference, occurrence
        ),
        accountId = accountId,
        bookingDate = bookingDate,
        valueDate = valueDate,
        amount = amount,
        currency = currency,
        counterparty = counterparty,
        counterpartyIban = counterpartyIban,
        purpose = purpose,
        bankReference = reference,
        source = format.source,
        propertyId = propertyId,
        unitId = unitId,
        importFileName = importFileName,
        importRunId = importRunId,
        importedAt = importedAt,
        updatedAt = importedAt
    )
}

private fun signed(amount: Double, direction: String): Double = if (direction == "DBIT") -abs(amount) else abs(amount)

private fun entryStatus(entry: Element): String {
    val statusElement = directOrFirstElement(entry, "Sts") ?: return ""
    return firstText(statusElement, "Cd").ifBlank { statusElement.textContent.orEmpty().trim() }.uppercase(Locale.ROOT)
}

private fun directTxDetails(entry: Element): List<Element> {
    val nodes = entry.getElementsByTagNameNS("*", "TxDtls")
    return buildList { for (i in 0 until nodes.length) (nodes.item(i) as? Element)?.let(::add) }
}

private fun firstElement(element: Element, localName: String): Element? {
    val nodes = element.getElementsByTagNameNS("*", localName)
    return if (nodes.length == 0) null else nodes.item(0) as? Element
}

private fun directOrFirstElement(element: Element, localName: String): Element? = firstElement(element, localName)

private fun firstText(element: Element, localName: String): String = firstElement(element, localName)?.textContent.orEmpty().trim()

private fun allTexts(element: Element, localName: String): List<String> {
    val nodes = element.getElementsByTagNameNS("*", localName)
    return buildList { for (i in 0 until nodes.length) nodes.item(i).textContent?.trim()?.takeIf(String::isNotBlank)?.let(::add) }
}

private fun nameWithin(element: Element, partyLocalName: String): String {
    val party = firstElement(element, partyLocalName) ?: return ""
    return firstText(party, "Nm")
}

private fun counterpartyIban(element: Element, outgoing: Boolean): String {
    val accountTag = if (outgoing) "CdtrAcct" else "DbtrAcct"
    val partyAccount = firstElement(element, accountTag) ?: return ""
    return firstText(partyAccount, "IBAN")
}

private fun firstDate(entry: Element, parentLocalName: String): String {
    val parent = firstElement(entry, parentLocalName) ?: return ""
    return firstNonBlank(firstText(parent, "Dt"), firstText(parent, "DtTm").take(10))
}

private fun firstNonBlank(vararg values: String): String = values.firstOrNull { it.isNotBlank() }.orEmpty()

data class BankZipEntryReport(
    val entryName: String,
    val status: String,
    val message: String,
    val format: String = "",
    val transactionsRead: Int = 0,
    val errorRows: Int = 0
)

data class BankZipParsedImport(
    val batches: List<BankImportBatch>,
    val totalEntries: Int,
    val xmlEntries: Int,
    val supportedCamtFiles: Int,
    val unsupportedFiles: Int,
    val faultyFiles: Int,
    val entryReports: List<BankZipEntryReport>
)

object BankZipImportLimits {
    const val MAX_ZIP_BYTES: Long = 20L * 1024 * 1024
    const val MAX_ENTRIES = 50
    const val MAX_TOTAL_UNCOMPRESSED_BYTES: Long = 50L * 1024 * 1024
    const val MAX_XML_BYTES: Long = 10L * 1024 * 1024
    const val MAX_COMPRESSION_RATIO = 200.0
}

object BankZipImportParser {
    fun looksLikeZip(bytes: ByteArray): Boolean = bytes.size >= 4 &&
        bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte() &&
        ((bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) ||
            (bytes[2] == 0x05.toByte() && bytes[3] == 0x06.toByte()) ||
            (bytes[2] == 0x07.toByte() && bytes[3] == 0x08.toByte()))

    fun parse(
        zipBytes: ByteArray,
        zipFileName: String,
        importedAt: String = Instant.now().toString(),
        propertyId: String = "",
        unitId: String = ""
    ): BankZipParsedImport {
        require(zipBytes.size.toLong() <= BankZipImportLimits.MAX_ZIP_BYTES) { "ZIP-Datei überschreitet die zulässige Maximalgröße." }
        require(looksLikeZip(zipBytes)) { "Datei ist kein gültiges ZIP-Archiv." }
        val reports = mutableListOf<BankZipEntryReport>()
        val batches = mutableListOf<BankImportBatch>()
        var totalEntries = 0
        var xmlEntries = 0
        var supported = 0
        var unsupported = 0
        var faulty = 0
        var totalUncompressed = 0L
        val runId = BankTransactionIdentity.importRunId("ZIP", zipFileName, importedAt)
        try {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    totalEntries++
                    require(totalEntries <= BankZipImportLimits.MAX_ENTRIES) { "ZIP enthält zu viele Einträge." }
                    val name = entry.name.orEmpty()
                    if (entry.isDirectory) {
                        reports += BankZipEntryReport(name, "IGNORED", "Verzeichniseintrag ignoriert.")
                        zip.closeEntry()
                        continue
                    }
                    if (!safeZipEntryName(name)) {
                        reports += BankZipEntryReport(name.take(120), "BLOCKED", "Unsicherer ZIP-Pfad wurde blockiert.")
                        throw IllegalArgumentException("ZIP enthält einen unsicheren Dateipfad.")
                    }
                    if (name.endsWith(".zip", true)) {
                        reports += BankZipEntryReport(name, "BLOCKED", "Verschachtelte ZIP-Dateien werden nicht importiert.")
                        unsupported++
                        zip.closeEntry()
                        continue
                    }
                    val isXmlByName = name.endsWith(".xml", true)
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var entryBytes = 0L
                    while (true) {
                        val read = zip.read(buffer)
                        if (read <= 0) break
                        entryBytes += read
                        totalUncompressed += read
                        require(totalUncompressed <= BankZipImportLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) { "ZIP überschreitet die zulässige entpackte Gesamtgröße." }
                        if (isXmlByName) require(entryBytes <= BankZipImportLimits.MAX_XML_BYTES) { "XML-Datei im ZIP ist zu groß." }
                        if (isXmlByName) output.write(buffer, 0, read)
                    }
                    val compressedSize = entry.compressedSize
                    if (isXmlByName && compressedSize > 0 && entryBytes / compressedSize.toDouble() > BankZipImportLimits.MAX_COMPRESSION_RATIO) {
                        throw IllegalArgumentException("ZIP-Eintrag weist ein unplausibles Kompressionsverhältnis auf.")
                    }
                    if (!isXmlByName) {
                        unsupported++
                        reports += BankZipEntryReport(name, "SKIPPED", "Nicht unterstützte Datei übersprungen.")
                        zip.closeEntry()
                        continue
                    }
                    xmlEntries++
                    val xml = output.toString(Charsets.UTF_8.name())
                    try {
                        val format = BankCamtV8FormatDetector.detect(xml)
                        if (format == BankCamtFormat.UNSUPPORTED) {
                            unsupported++
                            reports += BankZipEntryReport(name, "UNSUPPORTED", "XML ist kein unterstütztes CAMT.052/053 V8.")
                        } else {
                            val batch = BankImportParser.parseCamtV8(
                                xml = xml,
                                fallbackAccountName = zipFileName.substringBeforeLast('.'),
                                importedAt = importedAt,
                                importFileName = "$zipFileName!/$name",
                                importRunId = runId,
                                propertyId = propertyId,
                                unitId = unitId
                            )
                            batches += batch
                            supported++
                            reports += BankZipEntryReport(name, "PARSED", "${batch.transactions.size} Buchungen gelesen.", batch.format, batch.transactions.size, batch.errorRows)
                        }
                    } catch (e: Exception) {
                        faulty++
                        reports += BankZipEntryReport(name, "ERROR", e.message ?: "XML konnte nicht gelesen werden.")
                    }
                    zip.closeEntry()
                }
            }
        } catch (e: ZipException) {
            throw IllegalArgumentException("ZIP-Datei ist beschädigt oder ungültig.")
        }
        require(totalEntries > 0) { "ZIP-Datei ist leer." }
        return BankZipParsedImport(batches, totalEntries, xmlEntries, supported, unsupported, faulty, reports)
    }

    private fun safeZipEntryName(name: String): Boolean {
        if (name.isBlank() || name.startsWith("/") || name.startsWith("\\")) return false
        if (Regex("^[A-Za-z]:").containsMatchIn(name)) return false
        val normalized = name.replace('\\', '/')
        return normalized.split('/').none { it == ".." }
    }
}
