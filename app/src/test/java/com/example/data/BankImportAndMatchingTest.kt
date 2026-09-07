package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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


    @Test
    fun malformedCsvRowIsSkippedAndReported() {
        val csv = """
            Buchungstag;Auftraggeber/Empfänger;Verwendungszweck;Betrag;Währung
            04.09.2026;Hornbach;Material;-84,50;EUR
            ungueltig;Kaputt;Zeile;abc;EUR
            05.09.2026;Versicherung;Beitrag;-120,00;EUR
        """.trimIndent()

        val batch = BankImportParser.parseCsv(csv, importFileName = "konto.csv", importedAt = "now")

        assertEquals(2, batch.transactions.size)
        assertEquals(1, batch.errorRows)
        assertEquals("konto.csv", batch.transactions.first().importFileName)
        assertTrue(batch.transactions.first().importRunId.startsWith("import-"))
    }

    @Test
    fun csvSupportsTabAndCommaDelimiters() {
        val tab = "Buchungstag\tAuftraggeber/Empfänger\tBetrag\n04.09.2026\tHornbach\t-84,50"
        val comma = "Buchungstag,Auftraggeber/Empfänger,Betrag\n04.09.2026,Hornbach,\"-84,50\""
        assertEquals(-84.50, BankImportParser.parseCsv(tab).transactions.single().amount, 0.001)
        assertEquals(-84.50, BankImportParser.parseCsv(comma).transactions.single().amount, 0.001)
    }

    @Test
    fun sameAmountAndDateWithDifferentReferenceRemainDistinct() {
        val csv = """
            Buchungstag;Auftraggeber/Empfänger;Verwendungszweck;Betrag;Referenz
            04.09.2026;Hornbach;Material;-84,50;REF-A
            04.09.2026;Hornbach;Material;-84,50;REF-B
        """.trimIndent()
        val tx = BankImportParser.parseCsv(csv).transactions
        assertEquals(2, tx.size)
        assertNotEquals(tx[0].transactionId, tx[1].transactionId)
    }

    @Test
    fun exactAmountAndDateDoNotOverrideClearlyWrongVendor() {
        val tx = BankTransaction("tx", "a", "2026-09-04", amount = -247.38, counterparty = "Hornbach", purpose = "Baumaterial")
        val wrong = receipt(1, "Allianz Versicherung", "2026-09-04", 247.38, "Versicherungsbeitrag")
        assertNull(BankReceiptMatcher.bestForTransaction(tx, listOf(wrong)))
    }

    @Test
    fun dateWithinFiveDaysStillProducesPlausibleMatch() {
        val tx = BankTransaction("tx", "a", "2026-09-09", amount = -84.50, counterparty = "Hornbach")
        val candidate = receipt(1, "Hornbach", "2026-09-04", 84.50, "Material")
        val suggestion = BankReceiptMatcher.bestForTransaction(tx, listOf(candidate))
        assertEquals(1, suggestion?.receiptId)
        assertTrue((suggestion?.score ?: 0) >= 65)
    }

    @Test
    fun matcherChoosesBestOfSeveralCandidates() {
        val tx = BankTransaction("tx", "a", "2026-09-04", amount = -84.50, counterparty = "Hornbach", purpose = "Farbe Renovierung")
        val weaker = receipt(1, "Hornbach", "2026-09-01", 84.50, "Werkzeug")
        val stronger = receipt(2, "Hornbach", "2026-09-04", 84.50, "Farbe Renovierung")
        assertEquals(2, BankReceiptMatcher.bestForTransaction(tx, listOf(weaker, stronger))?.receiptId)
    }

    @Test
    fun incomeDoesNotMatchExpenseReceipt() {
        val tx = BankTransaction("tx", "a", "2026-09-04", amount = 84.50, counterparty = "Hornbach")
        assertNull(BankReceiptMatcher.bestForTransaction(tx, listOf(receipt(1, "Hornbach", "2026-09-04", 84.50, "Material"))))
    }

    @Test
    fun rentMatchUsesTenantUnitPropertyAndMonth() {
        val tx = BankTransaction(
            "rent", "a", "2026-09-03", amount = 950.0, counterparty = "Max Mustermann",
            purpose = "Miete September OG links", propertyId = "property-1"
        )
        val rent = Receipt(
            id = 9, aussteller = "Miete Max", datum = "2026-09-01", uhrzeit = "", bruttobetrag = 950.0,
            hauptkategorie = "Miete, Nebenkosten & Kaution", unterkategorie = "Warmmiete", kontoNr = "",
            beschreibung = "Miete September", wohneinheit = "OG links", mieter = "Max Mustermann",
            internalId = "rent-9", propertyId = "property-1"
        )
        val suggestion = BankReceiptMatcher.bestForTransaction(tx, listOf(rent))
        assertEquals(9, suggestion?.receiptId)
        assertTrue(suggestion?.reasons?.contains("Mietername passt") == true)
        assertTrue(suggestion?.reasons?.contains("Wohneinheit im Verwendungszweck") == true)
    }

    @Test
    fun confirmedBankMatchSetsBankPaymentButProtectsUserConfirmedMethod() {
        val tx = BankTransaction("tx", "a", "2026-09-04", amount = -84.50, purpose = "SEPA Lastschrift")
        val unknown = receipt(1, "A", "2026-09-04", 84.50, "").copy(
            zahlungsart = "Unbekannt", zahlungsartQuelle = "UNBEKANNT"
        )
        val manual = unknown.copy(zahlungsart = "Bar", zahlungsartQuelle = "NUTZER_BESTAETIGT")
        val decision = BankPaymentMethodPolicy.fromConfirmedBankMatch(tx, unknown)
        assertEquals("Lastschrift", decision?.method)
        assertEquals("BANKABGLEICH", decision?.source)
        assertNull(BankPaymentMethodPolicy.fromConfirmedBankMatch(tx, manual))
    }

    @Test
    fun unlinkingOneAllocationRecomputesMatchedPartialAndOpen() {
        val tx = BankTransaction("tx", "a", "2026-09-04", amount = -150.0)
        val a = BankReceiptLink("a", "tx", 1, "", 100.0)
        val b = BankReceiptLink("b", "tx", 2, "", 50.0)
        assertEquals(BankReconciliationStatus.MATCHED, BankLinkPolicy.statusFor(tx, listOf(a, b)))
        assertEquals(BankReconciliationStatus.PARTIAL, BankLinkPolicy.statusFor(tx, listOf(a)))
        assertEquals(BankReconciliationStatus.OPEN, BankLinkPolicy.statusFor(tx, emptyList()))
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
