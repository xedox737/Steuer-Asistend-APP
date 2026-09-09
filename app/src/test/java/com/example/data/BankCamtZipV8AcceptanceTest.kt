package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BankCamtZipV8AcceptanceTest {
    @Test fun detectsCamt052And053FromNamespaceNotExtension() {
        assertEquals(BankCamtFormat.CAMT_052_001_08, BankCamtV8FormatDetector.detect(camt052()))
        assertEquals(BankCamtFormat.CAMT_053_001_08, BankCamtV8FormatDetector.detect(camt053()))
        val unknown = camt052().replace("camt.052.001.08", "camt.052.001.07")
        assertEquals(BankCamtFormat.UNSUPPORTED, BankCamtV8FormatDetector.detect(unknown))
    }

    @Test fun camt052V8ImportsOnlyBookAndMapsRequiredFields() {
        val batch = BankImportParser.parseCamtV8(camt052(), "SYNTHETIC", "2026-09-09T20:00:00Z", "report.xml")
        assertEquals("CAMT.052 V8", batch.format)
        assertEquals("CAMT052", batch.account.source)
        assertEquals("DE00TESTOWN0001", batch.account.iban)
        assertEquals("EUR", batch.account.currency)
        assertEquals("SYNTHETIC BANK", batch.account.bankName)
        assertEquals(2, batch.transactions.size)
        assertEquals(1, batch.skippedRows)
        val credit = batch.transactions.first { it.amount > 0 }
        val debit = batch.transactions.first { it.amount < 0 }
        assertEquals(125.50, credit.amount, 0.001)
        assertEquals(-86.40, debit.amount, 0.001)
        assertEquals("2026-09-08", debit.bookingDate)
        assertEquals("2026-09-09", debit.valueDate)
        assertEquals("SYNTHETIC CREDITOR", debit.counterparty)
        assertEquals("DE00TESTOTHER0002", debit.counterpartyIban)
        assertEquals("Synthetic invoice 4711", debit.purpose)
        assertEquals("SERVICE-REF-1", debit.bankReference)
        assertEquals("CAMT052", debit.source)
        assertEquals("report.xml", debit.importFileName)
    }

    @Test fun camt052UsesEndToEndAndAdditionalInfoFallbacks() {
        val tx = BankImportParser.parseCamtV8(camt052(), "SYNTHETIC").transactions.first { it.amount > 0 }
        assertEquals("E2E-SYNTH-2", tx.bankReference)
        assertEquals("Synthetic fallback purpose", tx.purpose)
        assertEquals("SYNTHETIC DEBTOR", tx.counterparty)
        assertEquals("DE00TESTOTHER0003", tx.counterpartyIban)
    }

    @Test fun camt053RegressionKeepsLegacyStableTransactionIds() {
        val xml = camt053()
        val legacy = BankImportParser.parseCamt053(xml, "SYNTHETIC", "first")
        val v8 = BankImportParser.parseCamtV8(xml, "SYNTHETIC", "later")
        assertEquals(legacy.account.accountId, v8.account.accountId)
        assertEquals(legacy.transactions.map { it.transactionId }, v8.transactions.map { it.transactionId })
        assertEquals(-42.25, v8.transactions[0].amount, 0.001)
        assertEquals(99.10, v8.transactions[1].amount, 0.001)
    }

    @Test fun xxeAndDoctypeAreRejected() {
        val malicious = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY xxe SYSTEM="file:///etc/passwd">]><Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.052.001.08"><BkToCstmrAcctRpt><Rpt><Acct><Id><IBAN>&xxe;</IBAN></Id></Acct></Rpt></BkToCstmrAcctRpt></Document>"""
        assertTrue(runCatching { BankCamtV8FormatDetector.detect(malicious) }.isFailure)
        assertTrue(runCatching { BankImportParser.parseCamtV8(malicious) }.isFailure)
    }

    @Test fun multiTxDetailsSplitOnlyWhenDetailSumMatchesEntry() {
        val exact = BankImportParser.parseCamtV8(camt052Multi(detailsTotalMatches = true), "SYNTHETIC")
        assertEquals(2, exact.transactions.size)
        assertEquals(listOf(-40.0, -60.0), exact.transactions.map { it.amount })
        val ambiguous = BankImportParser.parseCamtV8(camt052Multi(detailsTotalMatches = false), "SYNTHETIC")
        assertEquals(1, ambiguous.transactions.size)
        assertEquals(-100.0, ambiguous.transactions.single().amount, 0.001)
    }

    @Test fun zipImportsCamt052And053AndReportsUnsupportedEntries() {
        val zip = zipOf(
            "first.xml" to camt052().toByteArray(),
            "second.xml" to camt053().toByteArray(),
            "notes.txt" to "synthetic note".toByteArray(),
            "unknown.xml" to camt052().replace("camt.052.001.08", "camt.052.001.07").toByteArray()
        )
        val parsed = BankZipImportParser.parse(zip, "synthetic.zip", "2026-09-09T20:00:00Z")
        assertEquals(4, parsed.totalEntries)
        assertEquals(3, parsed.xmlEntries)
        assertEquals(2, parsed.supportedCamtFiles)
        assertEquals(2, parsed.unsupportedFiles)
        assertEquals(0, parsed.faultyFiles)
        assertEquals(setOf("CAMT.052 V8", "CAMT.053 V8"), parsed.batches.map { it.format }.toSet())
        assertTrue(parsed.entryReports.any { it.entryName == "notes.txt" && it.status == "SKIPPED" })
        assertTrue(parsed.entryReports.any { it.entryName == "unknown.xml" && it.status == "UNSUPPORTED" })
    }

    @Test fun zipWithSingle052AndSingle053WorksIndividually() {
        assertEquals(1, BankZipImportParser.parse(zipOf("only.xml" to camt052().toByteArray()), "one.zip").supportedCamtFiles)
        assertEquals(1, BankZipImportParser.parse(zipOf("only.xml" to camt053().toByteArray()), "two.zip").supportedCamtFiles)
    }

    @Test fun zipSafetyLimitsBlockLargeXmlTotalSizeAndImplausibleCompression() {
        val xml = camt052().toByteArray()
        val one = zipOf("large.xml" to xml)
        assertTrue(runCatching {
            BankZipImportParser.parse(
                one, "large.zip",
                limits = BankZipSafetyLimits(maxXmlBytes = (xml.size - 1).toLong())
            )
        }.isFailure)

        val two = zipOf("a.xml" to xml, "b.xml" to xml)
        assertTrue(runCatching {
            BankZipImportParser.parse(
                two, "total.zip",
                limits = BankZipSafetyLimits(maxTotalUncompressedBytes = (xml.size + 10).toLong())
            )
        }.isFailure)

        assertTrue(runCatching {
            BankZipImportParser.parse(
                one, "ratio.zip",
                limits = BankZipSafetyLimits(maxCompressionRatio = 1.01)
            )
        }.isFailure)
    }

    @Test fun camt052MalformedRowsAreReportedWithoutInventingTransactions() {
        val malformed = camt052().replace(
            "<Ntry><Amt Ccy=\"EUR\">50.00</Amt><CdtDbtInd>CRDT</CdtDbtInd><Sts><Cd>PDNG</Cd></Sts><BookgDt><Dt>2026-09-09</Dt></BookgDt></Ntry>",
            "<Ntry><Amt Ccy=\"EUR\">not-a-number</Amt><CdtDbtInd>DBIT</CdtDbtInd><Sts><Cd>BOOK</Cd></Sts><BookgDt><Dt>2026-09-09</Dt></BookgDt></Ntry>" +
            "<Ntry><Amt Ccy=\"EUR\">10.00</Amt><CdtDbtInd>DBIT</CdtDbtInd><Sts><Cd>BOOK</Cd></Sts></Ntry>"
        )
        val batch = BankImportParser.parseCamtV8(malformed, "SYNTHETIC")
        assertEquals(2, batch.transactions.size)
        assertEquals(2, batch.errorRows)
    }

    @Test fun duplicateSupportedXmlInsideOneZipProducesStableDuplicateIdentities() {
        val parsed = BankZipImportParser.parse(
            zipOf("a.xml" to camt052().toByteArray(), "b.xml" to camt052().toByteArray()),
            "duplicates.zip"
        )
        assertEquals(2, parsed.batches.size)
        assertEquals(parsed.batches[0].transactions.map { it.transactionId }, parsed.batches[1].transactions.map { it.transactionId })
    }

    @Test fun zipPathTraversalIsBlockedAndNestedZipIsNeverImported() {
        val traversal = zipOf("../evil.xml" to camt052().toByteArray())
        assertTrue(runCatching { BankZipImportParser.parse(traversal, "bad.zip") }.isFailure)
        val nested = zipOf("inside.zip" to zipOf("x.xml" to camt052().toByteArray()), "safe.xml" to camt052().toByteArray())
        val parsed = BankZipImportParser.parse(nested, "nested.zip")
        assertEquals(1, parsed.supportedCamtFiles)
        assertTrue(parsed.entryReports.any { it.entryName == "inside.zip" && it.status == "BLOCKED" })
    }

    @Test fun emptyCorruptAndTooManyEntryZipFailCleanly() {
        val empty = zipOf()
        assertTrue(runCatching { BankZipImportParser.parse(empty, "empty.zip") }.isFailure)
        assertTrue(runCatching { BankZipImportParser.parse(byteArrayOf(0x50, 0x4b, 0x03, 0x04, 1, 2, 3), "broken.zip") }.isFailure)
        val many = (1..51).associate { "f$it.txt" to byteArrayOf(1) }
        assertTrue(runCatching { BankZipImportParser.parse(zipOf(*many.toList().toTypedArray()), "many.zip") }.isFailure)
    }

    @Test fun sameTransactionsFromDirectAndZipKeepSameIds() {
        val direct = BankImportParser.parseCamtV8(camt052(), "SYNTHETIC", "first", "direct.xml")
        val zipped = BankZipImportParser.parse(zipOf("inside.xml" to camt052().toByteArray()), "bundle.zip", "later").batches.single()
        assertEquals(direct.account.accountId, zipped.account.accountId)
        assertEquals(direct.transactions.map { it.transactionId }, zipped.transactions.map { it.transactionId })
    }

    @Test fun sameOwnIbanAcross052And053UsesOneDeterministicAccountIdentity() {
        val a = BankImportParser.parseCamtV8(camt052(), "SYNTHETIC").account.accountId
        val b = BankImportParser.parseCamtV8(camt053().replace("DE00TESTOWN0053", "DE00TESTOWN0001"), "SYNTHETIC").account.accountId
        assertEquals(a, b)
    }

    @Test fun zipMagicDetectionDoesNotTrustFilename() {
        val bytes = zipOf("x.xml" to camt052().toByteArray())
        assertTrue(BankZipImportParser.looksLikeZip(bytes))
        assertFalse(BankZipImportParser.looksLikeZip(camt052().toByteArray()))
        assertNotEquals(BankCamtFormat.UNSUPPORTED, BankCamtV8FormatDetector.detect(camt052()))
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun camt052(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.052.001.08">
          <BkToCstmrAcctRpt><Rpt>
            <Acct><Id><IBAN>DE00TESTOWN0001</IBAN></Id><Ccy>EUR</Ccy><Ownr><Nm>SYNTHETIC OWNER</Nm></Ownr><Svcr><FinInstnId><Nm>SYNTHETIC BANK</Nm></FinInstnId></Svcr></Acct>
            <Ntry><Amt Ccy="EUR">86.40</Amt><CdtDbtInd>DBIT</CdtDbtInd><Sts><Cd>BOOK</Cd></Sts><BookgDt><Dt>2026-09-08</Dt></BookgDt><ValDt><Dt>2026-09-09</Dt></ValDt><AcctSvcrRef>SERVICE-REF-1</AcctSvcrRef><NtryDtls><TxDtls><RltdPties><Cdtr><Nm>SYNTHETIC CREDITOR</Nm></Cdtr><CdtrAcct><Id><IBAN>DE00TESTOTHER0002</IBAN></Id></CdtrAcct></RltdPties><RmtInf><Ustrd>Synthetic invoice 4711</Ustrd></RmtInf></TxDtls></NtryDtls></Ntry>
            <Ntry><Amt Ccy="EUR">125.50</Amt><CdtDbtInd>CRDT</CdtDbtInd><Sts><Cd>BOOK</Cd></Sts><BookgDt><Dt>2026-09-09</Dt></BookgDt><ValDt><Dt>2026-09-09</Dt></ValDt><NtryDtls><TxDtls><Refs><EndToEndId>E2E-SYNTH-2</EndToEndId></Refs><RltdPties><Dbtr><Nm>SYNTHETIC DEBTOR</Nm></Dbtr><DbtrAcct><Id><IBAN>DE00TESTOTHER0003</IBAN></Id></DbtrAcct></RltdPties></TxDtls></NtryDtls><AddtlNtryInf>Synthetic fallback purpose</AddtlNtryInf></Ntry>
            <Ntry><Amt Ccy="EUR">50.00</Amt><CdtDbtInd>CRDT</CdtDbtInd><Sts><Cd>PDNG</Cd></Sts><BookgDt><Dt>2026-09-09</Dt></BookgDt></Ntry>
          </Rpt></BkToCstmrAcctRpt>
        </Document>
    """.trimIndent()

    private fun camt053(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.08"><BkToCstmrStmt><Stmt>
          <Acct><Id><IBAN>DE00TESTOWN0053</IBAN></Id><Ccy>EUR</Ccy></Acct>
          <Ntry><Amt Ccy="EUR">42.25</Amt><CdtDbtInd>DBIT</CdtDbtInd><BookgDt><Dt>2026-09-07</Dt></BookgDt><ValDt><Dt>2026-09-08</Dt></ValDt><AcctSvcrRef>REF-053-A</AcctSvcrRef><NtryDtls><TxDtls><RltdPties><Cdtr><Nm>SYNTHETIC PAYEE</Nm></Cdtr><CdtrAcct><Id><IBAN>DE00TESTOTHER053A</IBAN></Id></CdtrAcct></RltdPties><RmtInf><Ustrd>Synthetic debit</Ustrd></RmtInf></TxDtls></NtryDtls></Ntry>
          <Ntry><Amt Ccy="EUR">99.10</Amt><CdtDbtInd>CRDT</CdtDbtInd><BookgDt><Dt>2026-09-08</Dt></BookgDt><AcctSvcrRef>REF-053-B</AcctSvcrRef><NtryDtls><TxDtls><RltdPties><Dbtr><Nm>SYNTHETIC PAYER</Nm></Dbtr><DbtrAcct><Id><IBAN>DE00TESTOTHER053B</IBAN></Id></DbtrAcct></RltdPties><RmtInf><Ustrd>Synthetic credit</Ustrd></RmtInf></TxDtls></NtryDtls></Ntry>
        </Stmt></BkToCstmrStmt></Document>
    """.trimIndent()

    private fun camt052Multi(detailsTotalMatches: Boolean): String {
        val second = if (detailsTotalMatches) "60.00" else "50.00"
        return """
          <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.052.001.08"><BkToCstmrAcctRpt><Rpt><Acct><Id><IBAN>DE00TESTOWNMULTI</IBAN></Id><Ccy>EUR</Ccy></Acct>
          <Ntry><Amt Ccy="EUR">100.00</Amt><CdtDbtInd>DBIT</CdtDbtInd><Sts><Cd>BOOK</Cd></Sts><BookgDt><Dt>2026-09-09</Dt></BookgDt><NtryDtls>
          <TxDtls><Amt Ccy="EUR">40.00</Amt><CdtDbtInd>DBIT</CdtDbtInd><Refs><EndToEndId>MULTI-A</EndToEndId></Refs><RmtInf><Ustrd>Part A</Ustrd></RmtInf></TxDtls>
          <TxDtls><Amt Ccy="EUR">$second</Amt><CdtDbtInd>DBIT</CdtDbtInd><Refs><EndToEndId>MULTI-B</EndToEndId></Refs><RmtInf><Ustrd>Part B</Ustrd></RmtInf></TxDtls>
          </NtryDtls></Ntry></Rpt></BkToCstmrAcctRpt></Document>
        """.trimIndent()
    }
}
