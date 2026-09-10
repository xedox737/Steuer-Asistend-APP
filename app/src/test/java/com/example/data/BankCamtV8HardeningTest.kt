package com.example.data

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.xml.sax.SAXException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BankCamtV8HardeningTest {
    // JAXP provider substitution exercises the actual detector/direct/ZIP call chain without
    // a mutable factory hook in production. Always restore the process property in finally.
    class RejectingFactory : DocumentBuilderFactory() {
        private val delegate = newDefaultInstance()
        override fun setFeature(name: String, value: Boolean) { throw ParserConfigurationException(name) }
        override fun getFeature(name: String): Boolean = throw ParserConfigurationException(name)
        override fun setAttribute(name: String, value: Any?) { throw IllegalArgumentException(name) }
        override fun getAttribute(name: String): Any = throw IllegalArgumentException(name)
        override fun setXIncludeAware(state: Boolean) { throw UnsupportedOperationException("Unknown version 0.0") }
        override fun setExpandEntityReferences(state: Boolean) { throw UnsupportedOperationException("unsupported") }
        override fun newDocumentBuilder(): DocumentBuilder {
            delegate.isNamespaceAware = isNamespaceAware
            delegate.isValidating = isValidating
            return delegate.newDocumentBuilder()
        }
    }

    class BrokenFactory : DocumentBuilderFactory() {
        override fun setFeature(name: String, value: Boolean) = Unit
        override fun getFeature(name: String) = false
        override fun setAttribute(name: String, value: Any?) = Unit
        override fun getAttribute(name: String): Any = ""
        override fun newDocumentBuilder(): DocumentBuilder =
            throw ParserConfigurationException("http://apache.org/xml/features/disallow-doctype-decl")
    }

    private fun withFactory(type: Class<out DocumentBuilderFactory>, block: () -> Unit) {
        val key = "javax.xml.parsers.DocumentBuilderFactory"
        val previous = System.getProperty(key)
        try {
            System.setProperty(key, type.name)
            assertEquals(type, DocumentBuilderFactory.newInstance().javaClass)
            block()
        } finally {
            if (previous == null) System.clearProperty(key) else System.setProperty(key, previous)
        }
    }

    @Test fun productionZipAndDirectImportWorkWhenEveryOptionalSettingThrows() =
        withFactory(RejectingFactory::class.java) {
            val direct = BankImportParser.parseCamtV8(camt052())
            val parsed = BankZipImportParser.parse(
                zipOf("a.xml" to camt052().toByteArray(), "b.xml" to camt052().replace("SYNTH-REF-052", "SYNTH-REF-OTHER").toByteArray()),
                "synthetic.zip"
            )
            assertEquals(2, parsed.xmlEntries)
            assertEquals(2, parsed.supportedCamtFiles)
            assertEquals(0, parsed.faultyFiles)
            assertEquals(2, parsed.batches.size)
            assertTrue(parsed.batches.all { it.transactions.isNotEmpty() })
            assertFalse(parsed.entryReports.any { it.message.contains("http") })
            val tx = direct.transactions.single()
            assertEquals(15.25, tx.amount, 0.001)
            assertEquals("DE00SYNTHETIC0001", direct.account.iban)
            assertEquals("SYNTHETIC DEBTOR", tx.counterparty)
            assertEquals("DE00SYNTHETIC9001", tx.counterpartyIban)
            assertEquals("Synthetic CAMT 052", tx.purpose)
            assertEquals("SYNTH-REF-052", tx.bankReference)
            assertEquals("2026-09-09", tx.bookingDate)
            assertEquals("2026-09-10", tx.valueDate)
            val withoutReference = camt052().replace("<AcctSvcrRef>SYNTH-REF-052</AcctSvcrRef>", "")
            assertEquals("SYNTH-E2E-052", BankImportParser.parseCamtV8(withoutReference).transactions.single().bankReference)
            val debit = camt052().replace("CRDT", "DBIT")
            assertEquals(-15.25, BankImportParser.parseCamtV8(debit).transactions.single().amount, 0.001)
        }

    @Test fun mixedZipAndCamt053RetainIdsWithUnsupportedCapabilities() =
        withFactory(RejectingFactory::class.java) {
            val parsed = BankZipImportParser.parse(zipOf(
                "a.xml" to camt052().toByteArray(), "b.xml" to camt053().toByteArray(),
                "unknown.txt" to "synthetic".toByteArray()
            ), "mixed.zip")
            assertEquals(2, parsed.supportedCamtFiles)
            assertEquals(1, parsed.unsupportedFiles)
            assertEquals(0, parsed.faultyFiles)
            val direct = BankImportParser.parseCamtV8(camt053(), "b")
            assertEquals(listOf(-42.25, 99.10), direct.transactions.map { it.amount })
            assertEquals(direct.transactions.map { it.transactionId }, parsed.batches.last().transactions.map { it.transactionId })
            assertEquals(direct.transactions.map { it.transactionId },
                BankImportParser.parseCamtV8(camt053(), "b", "later").transactions.map { it.transactionId })
            assertTrue(BankImportParser.parseCamt053(camt053()).transactions.isNotEmpty())
        }

    @Test fun doctypeAndExternalEntitiesAreRejectedBeforeFactoryCreation() =
        withFactory(BrokenFactory::class.java) {
            val declarations = listOf(
                "<!DOCTYPE Document>",
                "<!doctype Document>",
                "<!DOCTYPE Document [<!ENTITY x SYSTEM 'http://invalid.example/entity'>]>",
                "<!DOCTYPE Document [<!ENTITY x SYSTEM 'https://invalid.example/entity'>]>",
                "<!DOCTYPE Document [<!ENTITY x SYSTEM 'file:///synthetic-never-read'>]>",
                "<!DOCTYPE Document SYSTEM 'https://invalid.example/external.dtd'>",
                "<!DOCTYPE Document [<!ENTITY % x SYSTEM 'file:///synthetic.dtd'>%x;]>"
            )
            declarations.forEach { dtd ->
                val xml = camt052().replace("<Document xmlns", "$dtd\n<Document xmlns")
                val failure = runCatching { BankCamtV8Xml.parseSecure(xml) }.exceptionOrNull()
                assertEquals(BankCamtV8Xml.DOCTYPE_ERROR, failure?.message)
                val zip = BankZipImportParser.parse(zipOf("attack.xml" to xml.toByteArray()), "synthetic.zip")
                assertEquals(1, zip.faultyFiles)
                assertEquals(0, zip.supportedCamtFiles)
                assertTrue(zip.batches.isEmpty())
                assertEquals(BankCamtV8Xml.DOCTYPE_ERROR, zip.entryReports.single().message)
            }
        }

    @Test fun doctypeIsStillBlockedWhenOptionalSecuritySettingsAreUnavailable() =
        withFactory(RejectingFactory::class.java) {
            val xml = "<!DOCTYPE Document [<!ENTITY x SYSTEM 'file:///synthetic'>]>" +
                camt052().substringAfter("?>")
            assertEquals(BankCamtV8Xml.DOCTYPE_ERROR,
                runCatching { BankImportParser.parseCamtV8(xml) }.exceptionOrNull()?.message)
        }

    @Test fun resolverRejectsEveryExternalIdentifierWithoutOpeningSources() {
        val resolver = BankCamtV8Xml.blockingEntityResolver
        listOf("http://invalid.example/x", "https://invalid.example/x", "file:///synthetic", "relative.dtd").forEach { id ->
            assertTrue(runCatching { resolver.resolveEntity(null, id) }.exceptionOrNull() is SAXException)
            assertTrue(runCatching { resolver.resolveEntity("x", null, "file:///", id) }.exceptionOrNull() is SAXException)
        }
        assertTrue(runCatching { resolver.getExternalSubset("Document", null) }.exceptionOrNull() is SAXException)
    }

    @Test fun builderConfigurationAndMalformedXmlNeverExposeTechnicalDetails() {
        withFactory(BrokenFactory::class.java) {
            val parsed = BankZipImportParser.parse(zipOf("a.xml" to camt052().toByteArray()), "synthetic.zip")
            assertEquals(1, parsed.faultyFiles)
            assertEquals("Der XML-Parser konnte nicht sicher eingerichtet werden.", parsed.entryReports.single().message)
        }
        val failure = runCatching { BankCamtV8Xml.parseSecure("<Document>&SYNTHETIC_PRIVATE_TEXT;</Document>") }.exceptionOrNull()
        assertNotNull(failure)
        assertFalse(failure!!.message.orEmpty().contains("SYNTHETIC_PRIVATE_TEXT"))
        assertFalse(failure.message.orEmpty().contains("http"))
    }

    @Test fun characterInputHonorsGuardRegardlessOfEncodingDeclarationAndBom() {
        assertEquals("Document", BankCamtV8Xml.parseSecure("\uFEFF" + camt052()).documentElement.localName)
        assertEquals("Document", BankCamtV8Xml.parseSecure(camt052().replace("UTF-8", "UTF-16")).documentElement.localName)
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

