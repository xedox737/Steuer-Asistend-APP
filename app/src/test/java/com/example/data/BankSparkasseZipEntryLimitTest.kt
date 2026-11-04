package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Synthetic Sparkasse-style ZIP regression; no real banking data. */
class BankSparkasseZipEntryLimitTest {
    @Test fun zipWithMoreThanFiftyEntriesStillImportsCamtV8() {
        val parsed = BankZipImportParser.parse(archive(60), "synthetic-sparkasse.zip")
        assertEquals(60, parsed.totalEntries)
        assertEquals(1, parsed.supportedCamtFiles)
        assertEquals(1, parsed.batches.single().transactions.size)
    }

    @Test fun excessiveEntryCountIsStillRejected() {
        val rejected = runCatching { BankZipImportParser.parse(archive(501), "excessive.zip") }
        assertTrue(rejected.isFailure)
        assertTrue(rejected.exceptionOrNull()?.message.orEmpty().contains("zu viele Einträge"))
    }

    @Test fun injectedSmallerLimitStillRejectsExtraEntries() {
        val rejected = runCatching {
            BankZipImportParser.parse(archive(60), "limited.zip", limits = BankZipSafetyLimits(maxEntries = 50))
        }
        assertTrue(rejected.isFailure)
    }

    private fun archive(entries: Int): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            repeat(entries - 1) { index ->
                zip.putNextEntry(ZipEntry("metadata/note-${index.toString().padStart(3, '0')}.txt"))
                zip.write("synthetic".toByteArray())
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("statements/statement.xml"))
            zip.write(camt().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private fun camt() = """<?xml version="1.0" encoding="UTF-8"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.052.001.08">
  <BkToCstmrAcctRpt><GrpHdr><MsgId>SYNTHETIC</MsgId></GrpHdr>
  <Rpt><Id>SYNTHETIC</Id><Acct><Id><IBAN>DE00SYNTHETIC00001</IBAN></Id><Ccy>EUR</Ccy></Acct>
    <Ntry><Amt Ccy="EUR">42.50</Amt><CdtDbtInd>DBIT</CdtDbtInd>
      <Sts><Cd>BOOK</Cd></Sts><BookgDt><Dt>2026-10-30</Dt></BookgDt>
      <ValDt><Dt>2026-10-30</Dt></ValDt>
      <AcctSvcrRef>SYNTH-REF-01</AcctSvcrRef>
      <NtryDtls><TxDtls><RmtInf><Ustrd>Synthetic test expense</Ustrd></RmtInf></TxDtls></NtryDtls>
    </Ntry>
  </Rpt></BkToCstmrAcctRpt>
</Document>"""
}
