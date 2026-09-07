package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankImportAndMatchingTest {
    @Test
    fun germanCsvParsesSignedAmountsAndIsIdempotentlyIdentified() {
        val csv = """
            Buchungstag;Wertstellung;Auftraggeber/Empfänger;Verwendungszweck;Betrag;Währung;IBAN
            04.09.2026;04.09.2026;HORNBACH BAUMARKT AG;Material OG links;-247,38;EUR;DE111
            05.09.2026;05.09.2026;Max Mustermann;Miete September;950,00;EUR;DE222
        """.trimIndent()

        val first = BankImportParser.parseCsv(csv, "Hauskonto", "now")
        val second = BankImportParser.parseCsv(csv, "Hauskonto", "later")

        assertEquals(2, first.transactions.size)
        assertEquals(-247.38, first.transactions[0].amount, 0.001)
        assertEquals(950.0, first.transactions[1].amount, 0.001)
        assertEquals(first.transactions.map { it.transactionId }, second.transactions.map { it.transactionId })
    }

    @Test
    fun camt053ParsesDebitAndCredit() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.08">
              <BkToCstmrStmt><Stmt>
                <Acct><Id><IBAN>DE001234567890</IBAN></Id><Ccy>EUR</Ccy></Acct>
                <Ntry>
                  <Amt Ccy="EUR">86.40</Amt><CdtDbtInd>DBIT</CdtDbtInd>
                  <BookgDt><Dt>2026-09-04</Dt></BookgDt><ValDt><Dt>2026-09-04</Dt></ValDt>
                  <AcctSvcrRef>REF-1</AcctSvcrRef>
                  <NtryDtls><TxDtls><RltdPties><Cdtr><Nm>Schornsteinfeger Müller</Nm></Cdtr><CdtrAcct><Id><IBAN>DE999</IBAN></Id></CdtrAcct></RltdPties>
                  <RmtInf><Ustrd>Rechnung 4711</Ustrd></RmtInf></TxDtls></NtryDtls>
                </Ntry>
                <Ntry>
                  <Amt Ccy="EUR">950.00</Amt><CdtDbtInd>CRDT</CdtDbtInd>
                  <BookgDt><Dt>2026-09-05</Dt></BookgDt>
                  <AcctSvcrRef>REF-2</AcctSvcrRef>
                  <NtryDtls><TxDtls><RltdPties><Dbtr><Nm>Max Mustermann</Nm></Dbtr></RltdPties>
                  <RmtInf><Ustrd>Miete September</Ustrd></RmtInf></TxDtls></NtryDtls>
                </Ntry>
              </Stmt></BkToCstmrStmt>
            </Document>
        """.trimIndent()

        val batch = BankImportParser.parseCamt053(xml, "Hauskonto", "now")

        assertEquals("DE001234567890", batch.account.iban)
        assertEquals(2, batch.transactions.size)
        assertEquals(-86.40, batch.transactions[0].amount, 0.001)
        assertEquals(950.0, batch.transactions[1].amount, 0.001)
    }

    @Test
    fun deterministicMatcherPrefersExactHornbachReceipt() {
        val tx = BankTransaction(
            transactionId = "tx-1", accountId = "a", bookingDate = "2026-09-04",
            amount = -247.38, counterparty = "HORNBACH BAUMARKT AG", purpose = "Material OG links"
        )
        val correct = receipt(1, "Hornbach", "2026-09-03", 247.38, "Material OG links")
        val wrong = receipt(2, "Versicherung", "2026-09-04", 247.38, "Jahresbeitrag")

        val suggestion = BankReceiptMatcher.bestForTransaction(tx, listOf(wrong, correct))

        assertEquals(1, suggestion?.receiptId)
        assertTrue((suggestion?.score ?: 0) >= 85)
        assertEquals("HOCH", suggestion?.confidence)
    }

    @Test
    fun directionPreventsExpenseFromMatchingRentReceipt() {
        val tx = BankTransaction("tx", "a", "2026-09-04", amount = -950.0, counterparty = "Max Mustermann")
        val rent = receipt(1, "Max Mustermann", "2026-09-04", 950.0, "Miete", "Miete, Nebenkosten & Kaution")

        assertEquals(null, BankReceiptMatcher.bestForTransaction(tx, listOf(rent)))
    }

    @Test
    fun allocationSupportsSplitsWithoutOverAllocation() {
        val tx = BankTransaction("tx", "a", "2026-09-04", amount = -150.0)
        val firstReceipt = receipt(1, "A", "2026-09-04", 100.0, "")
        val secondReceipt = receipt(2, "B", "2026-09-04", 80.0, "")

        val first = BankLinkPolicy.propose(tx, firstReceipt, emptyList())
        assertTrue(first.allowed)
        assertEquals(100.0, first.amount, 0.001)

        val firstLink = BankReceiptLink("l1", "tx", 1, "", 100.0)
        val second = BankLinkPolicy.propose(tx, secondReceipt, listOf(firstLink))
        assertTrue(second.allowed)
        assertEquals(50.0, second.amount, 0.001)

        val secondLink = BankReceiptLink("l2", "tx", 2, "", 50.0)
        val exhausted = BankLinkPolicy.propose(tx, secondReceipt, listOf(firstLink, secondLink))
        assertFalse(exhausted.allowed)
    }

    @Test
    fun oneReceiptCanReceiveMultiplePartialPayments() {
        val receipt = receipt(7, "Handwerker", "2026-09-01", 200.0, "")
        val tx1 = BankTransaction("t1", "a", "2026-09-02", amount = -100.0)
        val tx2 = BankTransaction("t2", "a", "2026-09-03", amount = -100.0)
        val first = BankLinkPolicy.propose(tx1, receipt, emptyList())
        val link = BankReceiptLink("l1", "t1", receipt.id, receipt.internalId, first.amount)
        val second = BankLinkPolicy.propose(tx2, receipt, listOf(link))
        assertTrue(second.allowed)
        assertEquals(100.0, second.amount, 0.001)
    }

    private fun receipt(
        id: Int,
        vendor: String,
        date: String,
        amount: Double,
        description: String,
        category: String = "Renovierung"
    ) = Receipt(
        id = id,
        aussteller = vendor,
        datum = date,
        uhrzeit = "",
        bruttobetrag = amount,
        hauptkategorie = category,
        unterkategorie = "",
        kontoNr = "",
        beschreibung = description,
        internalId = "receipt-$id"
    )
}
