from pathlib import Path

bank = Path('app/src/main/java/com/example/data/BankImportV8.kt')
text = bank.read_text(encoding='utf-8')
old = '''        val factory = DocumentBuilderFactory.newInstance()\n        factory.isNamespaceAware = true\n        factory.isXIncludeAware = false\n        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)\n'''
new = '''        val factory = DocumentBuilderFactory.newInstance()\n        factory.isNamespaceAware = true\n        // Android's DocumentBuilderFactory may reject XInclude configuration with\n        // "This parser does not support specification Unknown version 0.0".\n        // XInclude is not enabled by default; defensively request disabled only when supported.\n        runCatching { factory.isXIncludeAware = false }\n        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)\n'''
if old not in text:
    raise SystemExit('BankImportV8 target block not found')
bank.write_text(text.replace(old, new, 1), encoding='utf-8')

test = Path('app/src/test/java/com/example/data/BankCamtV8AndroidCompatibilityTest.kt')
test.write_text(r'''package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BankCamtV8AndroidCompatibilityTest {

    @Test
    fun parseSecureCamt052WorksOnAndroidCompatibleRuntimeWithoutUnknownVersionError() {
        val result = runCatching { BankCamtV8Xml.parseSecure(camt052()) }
        assertTrue(result.exceptionOrNull()?.message.orEmpty(), result.isSuccess)
        val document = result.getOrThrow()
        assertEquals(BankCamtFormat.CAMT_052_001_08.namespace, document.documentElement.namespaceURI)
        assertEquals("Document", document.documentElement.localName)
    }

    @Test
    fun productionZipPathImportsCamt052WithoutUnknownVersionError() {
        val parsed = BankZipImportParser.parse(
            zipOf("statement.xml" to camt052().toByteArray()),
            "synthetic-android.zip",
            "2026-09-10T03:00:00Z"
        )
        assertEquals(1, parsed.supportedCamtFiles)
        assertEquals(0, parsed.faultyFiles)
        assertEquals(1, parsed.batches.size)
        assertTrue(parsed.batches.single().transactions.isNotEmpty())
        assertFalse(parsed.entryReports.any { it.message.contains("Unknown version 0.0", ignoreCase = true) })
    }

    @Test
    fun camt053DirectAndZipRemainStableOnAndroidCompatibleRuntime() {
        val direct = BankImportParser.parseCamtV8(camt053(), "SYNTHETIC", "2026-09-10T03:00:00Z", "statement.xml")
        val zipped = BankZipImportParser.parse(
            zipOf("statement.xml" to camt053().toByteArray()),
            "synthetic-053.zip",
            "2026-09-10T04:00:00Z"
        ).batches.single()
        assertEquals("CAMT.053 V8", direct.format)
        assertEquals("CAMT.053 V8", zipped.format)
        assertEquals(direct.account.accountId, zipped.account.accountId)
        assertEquals(direct.transactions.map { it.transactionId }, zipped.transactions.map { it.transactionId })
        assertEquals(listOf(-42.25, 99.10), direct.transactions.map { it.amount })
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
            <Acct><Id><IBAN>DE00SYNTHETIC0001</IBAN></Id><Ccy>EUR</Ccy></Acct>
            <Ntry><Amt Ccy="EUR">15.25</Amt><CdtDbtInd>CRDT</CdtDbtInd><Sts><Cd>BOOK</Cd></Sts><BookgDt><Dt>2026-09-09</Dt></BookgDt><ValDt><Dt>2026-09-10</Dt></ValDt><AcctSvcrRef>SYNTH-REF-052</AcctSvcrRef><NtryDtls><TxDtls><Refs><EndToEndId>SYNTH-E2E-052</EndToEndId></Refs><RltdPties><Dbtr><Nm>SYNTHETIC DEBTOR</Nm></Dbtr><DbtrAcct><Id><IBAN>DE00SYNTHETIC9001</IBAN></Id></DbtrAcct></RltdPties><RmtInf><Ustrd>Synthetic CAMT 052</Ustrd></RmtInf></TxDtls></NtryDtls></Ntry>
          </Rpt></BkToCstmrAcctRpt>
        </Document>
    """.trimIndent()

    private fun camt053(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.08"><BkToCstmrStmt><Stmt>
          <Acct><Id><IBAN>DE00SYNTHETIC0053</IBAN></Id><Ccy>EUR</Ccy></Acct>
          <Ntry><Amt Ccy="EUR">42.25</Amt><CdtDbtInd>DBIT</CdtDbtInd><BookgDt><Dt>2026-09-08</Dt></BookgDt><ValDt><Dt>2026-09-09</Dt></ValDt><AcctSvcrRef>SYNTH-053-A</AcctSvcrRef><NtryDtls><TxDtls><RltdPties><Cdtr><Nm>SYNTHETIC PAYEE</Nm></Cdtr><CdtrAcct><Id><IBAN>DE00SYNTHETIC9053</IBAN></Id></CdtrAcct></RltdPties><RmtInf><Ustrd>Synthetic debit</Ustrd></RmtInf></TxDtls></NtryDtls></Ntry>
          <Ntry><Amt Ccy="EUR">99.10</Amt><CdtDbtInd>CRDT</CdtDbtInd><BookgDt><Dt>2026-09-09</Dt></BookgDt><AcctSvcrRef>SYNTH-053-B</AcctSvcrRef><NtryDtls><TxDtls><RltdPties><Dbtr><Nm>SYNTHETIC PAYER</Nm></Dbtr><DbtrAcct><Id><IBAN>DE00SYNTHETIC9054</IBAN></Id></DbtrAcct></RltdPties></TxDtls></NtryDtls></Ntry>
        </Stmt></BkToCstmrStmt></Document>
    """.trimIndent()
}
''', encoding='utf-8')
