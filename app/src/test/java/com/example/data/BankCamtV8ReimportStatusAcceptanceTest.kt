package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BankCamtV8ReimportStatusAcceptanceTest {
    private lateinit var database: AppDatabase

    @Before fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() = database.close()

    @Test fun directAndZipReimportPreserveMatchedLinkAndSingleTransaction() = runTest {
        val xml = synthetic052()
        val first = BankImportParser.parseCamtV8(xml, "inside", "first", "inside.xml")
        val tx = first.transactions.single()
        val dao = database.bankDao()
        dao.upsertAccount(first.account)
        dao.insertTransactions(first.transactions)

        database.receiptDao().insertAll(listOf(Receipt(
            id = 1, aussteller = "SYNTHETIC PAYEE", datum = tx.bookingDate, uhrzeit = "",
            bruttobetrag = tx.absoluteAmount, hauptkategorie = "Kosten", unterkategorie = "Test",
            kontoNr = "", beschreibung = "Synthetic invoice", internalId = "synthetic-r1"
        )))
        dao.upsertLink(BankReceiptLink(
            linkId = "synthetic-link", transactionId = tx.transactionId, receiptId = 1,
            receiptInternalId = "synthetic-r1", allocatedAmount = tx.absoluteAmount,
            status = BankLinkStatus.CONFIRMED, source = BankLinkSource.NUTZER_BESTAETIGT,
            createdAt = "first"
        ))
        dao.updateTransactionStatus(tx.transactionId, BankReconciliationStatus.MATCHED, "", "matched")

        val directAgain = BankImportParser.parseCamtV8(xml, "inside", "second", "inside.xml")
        dao.upsertAccount(directAgain.account)
        dao.insertTransactions(directAgain.transactions)

        val zipBatch = BankZipImportParser.parse(zipOf("inside.xml" to xml.toByteArray()), "bundle.zip", "third")
            .batches.single()
        dao.upsertAccount(zipBatch.account)
        dao.insertTransactions(zipBatch.transactions)

        assertEquals(tx.transactionId, directAgain.transactions.single().transactionId)
        assertEquals(tx.transactionId, zipBatch.transactions.single().transactionId)
        assertEquals(1, dao.getAllTransactions().size)
        assertEquals(BankReconciliationStatus.MATCHED, dao.getTransaction(tx.transactionId)?.reconciliationStatus)
        assertEquals(1, dao.getAllLinks().size)
        assertEquals("synthetic-link", dao.getAllLinks().single().linkId)
    }

    @Test fun reimportPreservesPartialReviewAndNoReceiptRequiredStatuses() = runTest {
        val dao = database.bankDao()
        val batch = BankImportParser.parseCamtV8(synthetic052(), "SYNTHETIC", "first")
        dao.upsertAccount(batch.account)
        val base = batch.transactions.single()
        val statuses = listOf(
            BankReconciliationStatus.PARTIAL,
            BankReconciliationStatus.REVIEW,
            BankReconciliationStatus.NO_RECEIPT_REQUIRED
        )
        statuses.forEachIndexed { index, status ->
            val tx = base.copy(transactionId = "synthetic-status-$index")
            dao.insertTransactions(listOf(tx))
            dao.updateTransactionStatus(tx.transactionId, status, "synthetic reason", "changed")
            dao.insertTransactions(listOf(tx.copy(importedAt = "later", updatedAt = "later")))
            assertEquals(status, dao.getTransaction(tx.transactionId)?.reconciliationStatus)
        }
    }

    @Test fun bothFormatsPreserveEveryStatusAndLinkThroughRealDirectAndZipReimport() = runTest {
        val dao = database.bankDao()
        val statuses = listOf(
            BankReconciliationStatus.MATCHED, BankReconciliationStatus.PARTIAL,
            BankReconciliationStatus.REVIEW, BankReconciliationStatus.NO_RECEIPT_REQUIRED
        )
        val formats = listOf(synthetic052(), synthetic052()
            .replace("camt.052.001.08", "camt.053.001.08")
            .replace("BkToCstmrAcctRpt", "BkToCstmrStmt").replace("Rpt>", "Stmt>"))
        var count = 0
        formats.forEachIndexed { formatIndex, template ->
            statuses.forEachIndexed { statusIndex, status ->
                val key = "synthetic-$formatIndex-$statusIndex"
                val xml = template.replace("SYNTH-REF", key)
                val batch = BankImportParser.parseCamtV8(xml, "inside", "first")
                val tx = batch.transactions.single()
                dao.upsertAccount(batch.account)
                dao.insertTransactions(batch.transactions)
                count++
                database.receiptDao().insertAll(listOf(Receipt(
                    id = count, aussteller = "SYNTHETIC", datum = tx.bookingDate, uhrzeit = "",
                    bruttobetrag = tx.absoluteAmount, hauptkategorie = "Kosten", unterkategorie = "Test",
                    kontoNr = "", beschreibung = "Synthetic", internalId = key
                )))
                val link = BankReceiptLink(
                    linkId = key, transactionId = tx.transactionId, receiptId = count,
                    receiptInternalId = key, allocatedAmount = 10.0,
                    status = BankLinkStatus.CONFIRMED, source = BankLinkSource.NUTZER_BESTAETIGT,
                    createdAt = "first"
                )
                dao.upsertLink(link)
                dao.updateTransactionStatus(tx.transactionId, status, "synthetic", "changed")
                dao.insertTransactions(BankImportParser.parseCamtV8(xml, "inside", "second").transactions)
                val zipped = BankZipImportParser.parse(
                    zipOf("inside.xml" to xml.toByteArray(), "duplicate.xml" to xml.toByteArray()),
                    "synthetic.zip", "third"
                )
                zipped.batches.forEach { dao.insertTransactions(it.transactions) }
                assertEquals(count, dao.getAllTransactions().size)
                assertEquals(status, dao.getTransaction(tx.transactionId)?.reconciliationStatus)
                assertEquals(link, dao.getAllLinks().single { it.linkId == key })
            }
        }
        assertEquals(8, dao.getAllTransactions().size)
        assertEquals(8, dao.getAllLinks().size)
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun synthetic052(): String = """
        <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.052.001.08">
          <BkToCstmrAcctRpt><Rpt><Acct><Id><IBAN>DE00SYNTHETIC0001</IBAN></Id><Ccy>EUR</Ccy></Acct>
            <Ntry><Amt Ccy="EUR">25.00</Amt><CdtDbtInd>DBIT</CdtDbtInd><Sts><Cd>BOOK</Cd></Sts>
              <BookgDt><Dt>2026-09-09</Dt></BookgDt><ValDt><Dt>2026-09-09</Dt></ValDt><AcctSvcrRef>SYNTH-REF</AcctSvcrRef>
              <NtryDtls><TxDtls><RltdPties><Cdtr><Nm>SYNTHETIC PAYEE</Nm></Cdtr></RltdPties><RmtInf><Ustrd>Synthetic invoice</Ustrd></RmtInf></TxDtls></NtryDtls>
            </Ntry>
          </Rpt></BkToCstmrAcctRpt>
        </Document>
    """.trimIndent()
}

