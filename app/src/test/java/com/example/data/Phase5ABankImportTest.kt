package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase5ABankImportTest {
    private val iban = "DE02120300000000202051"
    private fun csv(month: String = "10", count: Int = 60, identifier: String = iban) =
        "Buchungstag;Wertstellung;Betrag;Währung;Auftraggeber;Verwendungszweck;Referenz;Konto IBAN\n" +
            (1..count).joinToString("\n") { "01.$month.2026;02.$month.2026;${it},00;EUR;Partner $it;Miete;REF-$month-$it;$identifier" }

    @Test fun allSupportedDatesUseStrictCalendarResolution() {
        mapOf("31.01.2026" to "2026-01-31", "29.02.2024" to "2024-02-29", "1.2.2026" to "2026-02-01",
            "31/01/2026" to "2026-01-31", "1/2/2026" to "2026-02-01", "2024-02-29" to "2024-02-29").forEach { (input, expected) ->
            assertEquals(input, expected, BankImportParser.normalizeDate(input))
        }
        listOf("29.02.2025", "30.02.2026", "31.04.2026", "30/02/2026", "2026-02-30", "2026-04-31", "0000-01-01").forEach {
            assertEquals(it, "", BankImportParser.normalizeDate(it))
        }
    }

    @Test fun finiteAmountsAndGermanFormatsAreRequired() {
        mapOf("1.234,56" to 1234.56, "1 234,56 €" to 1234.56, "-84,50" to -84.5,
            "(84,50)" to -84.5, "950.00" to 950.0, "1,234.56" to 1234.56).forEach { (input, expected) ->
            assertEquals(input, expected, BankImportParser.parseAmount(input)!!, 0.00001)
        }
        listOf("NaN", "Infinity", "-Infinity", "", "abc", "1,2,3", "1.23,45", "1€2", "1 2", "--12", "9".repeat(400)).forEach {
            assertNull(it, BankImportParser.parseAmount(it))
        }
    }

    @Test fun bothLegacyAndV8CamtRejectInvalidBookingValueDatesAndNonFiniteAmounts() {
        fun xml(date: String = "2026-01-31", valueDate: String = "2026-01-31", amount: String = "100.00") =
            """<Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.053.001.08"><BkToCstmrStmt><Stmt>
                <Acct><Id><IBAN>$iban</IBAN></Id><Ccy>EUR</Ccy></Acct><Ntry><Sts><Cd>BOOK</Cd></Sts>
                <Amt Ccy="EUR">$amount</Amt><CdtDbtInd>CRDT</CdtDbtInd><BookgDt><Dt>$date</Dt></BookgDt>
                <ValDt><Dt>$valueDate</Dt></ValDt><AcctSvcrRef>REF-1</AcctSvcrRef></Ntry>
                </Stmt></BkToCstmrStmt></Document>"""
        val parsers: List<(String) -> BankImportBatch> = listOf({ BankImportParser.parseCamt053(it) }, { BankImportParser.parseCamtV8(it) })
        parsers.forEach { parse ->
            assertEquals("2026-01-31", parse(xml()).transactions.single().bookingDate)
            assertEquals("2024-02-29", parse(xml("2024-02-29", "2024-02-29")).transactions.single().bookingDate)
            listOf("2026-02-30", "2025-02-29", "2026-04-31").forEach { date ->
                assertThrows(IllegalArgumentException::class.java) { parse(xml(date = date)) }
                assertThrows(IllegalArgumentException::class.java) { parse(xml(valueDate = date)) }
            }
            listOf("NaN", "Infinity", "-Infinity", "").forEach { amount ->
                assertThrows(IllegalArgumentException::class.java) { parse(xml(amount = amount)) }
            }
        }
    }

    @Test fun csvErrorsContainPhysicalLineFieldAndOriginalValueWithoutNormalizing() {
        val input = "Buchungstag;Wertstellung;Betrag\n31.01.2026;31.01.2026;1,00\n\n30.02.2026;;NaN\n31.01.2026;31.04.2026;2,00\n31.01.2026;;\n"
        val batch = BankImportParser.parseCsv(input)
        assertEquals(1, batch.transactions.size)
        assertEquals(3, batch.errorRows)
        assertTrue(batch.errors.any { it.rowNumber == 4 && it.field == "Buchungsdatum" && it.originalValue == "30.02.2026" })
        assertTrue(batch.errors.any { it.rowNumber == 4 && it.field == "Betrag" && it.originalValue == "NaN" })
        assertTrue(batch.errors.any { it.rowNumber == 5 && it.field == "Wertstellungsdatum" && it.originalValue == "31.04.2026" })
        assertTrue(batch.errors.any { it.rowNumber == 6 && it.field == "Betrag" && it.originalValue.isEmpty() })
        assertTrue(batch.errors.first { it.field == "Buchungsdatum" }.message.contains("Zeile 4: Ungültiges Buchungsdatum ‚30.02.2026‘."))
        val error = assertThrows(IllegalArgumentException::class.java) { BankImportParser.parseCsv("Buchungstag;Betrag\n30.02.2026;Infinity") }
        assertTrue(error.message!!.contains("Zeile 2"))
        assertTrue(error.message!!.contains("Infinity"))
    }

    @Test fun reimportAndRenamed60RowFileUseSameAccountAndTransactionsAndOnlyNextMonthAddsRows() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            suspend fun importRows(name: String, text: String): Pair<Int, Int> {
                val parsed = BankImportParser.parseCsv(text, name, "2026-10-08T00:00:00Z", name)
                val resolved = BankImportAccountResolver.resolve(parsed, db.bankDao().getAllAccounts())
                db.bankDao().upsertAccount(resolved.account)
                val added = db.bankDao().insertTransactions(resolved.transactions).count { it != -1L }
                return added to (resolved.transactions.size - added)
            }
            assertEquals(60 to 0, importRows("TEST-KONTO-A.csv", csv()))
            val accountId = db.bankDao().getAllAccounts().single().accountId
            val originalIds = db.bankDao().getAllTransactions().map { it.transactionId }.toSet()
            assertEquals(0 to 60, importRows("TEST-KONTO-A.csv", csv()))
            assertEquals(0 to 60, importRows("Kontoauszug-Oktober-neu.csv", csv()))
            assertEquals(originalIds, db.bankDao().getAllTransactions().map { it.transactionId }.toSet())
            assertEquals(accountId, db.bankDao().getAllAccounts().single().accountId)
            assertEquals(60 to 0, importRows("November.csv", csv("11", identifier = "de02 1203 0000 0000 2020 51")))
            assertEquals(120, db.bankDao().getAllTransactions().size)
            assertEquals(accountId, db.bankDao().getAllAccounts().single().accountId)
        } finally { db.close() }
    }

    @Test fun legacyAccountIdIsRetainedIncludingTransactionIdentityAndNoIbanRequiresSelection() {
        val parsed = BankImportParser.parseCsv(csv(count = 2), "Renamed.csv")
        val legacy = BankAccount("old-stable-account", "Hauskonto", iban = iban)
        val resolved = BankImportAccountResolver.resolve(parsed, listOf(legacy))
        assertEquals(legacy.accountId, resolved.account.accountId)
        resolved.transactions.forEach { tx ->
            assertEquals(BankTransactionIdentity.transactionId(legacy.accountId, tx.bookingDate, tx.valueDate, tx.amount,
                tx.currency, tx.counterparty, tx.counterpartyIban, tx.purpose, tx.bankReference), tx.transactionId)
        }
        val noIban = BankImportParser.parseCsv(csv(count = 2, identifier = ""), "Not-an-account.csv")
        assertTrue(noIban.account.accountId.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { BankImportAccountResolver.resolve(noIban, listOf(legacy)) }
        val selected = BankImportAccountResolver.resolve(noIban, listOf(legacy), legacy)
        assertEquals(legacy.accountId, selected.account.accountId)
        val renamed = BankImportAccountResolver.resolve(BankImportParser.parseCsv(csv(count = 2, identifier = ""), "other.csv"), listOf(legacy), legacy)
        assertEquals(selected.transactions.map { it.transactionId }, renamed.transactions.map { it.transactionId })
        assertEquals(BankTransactionIdentity.accountId("CSV", iban, "A"), BankTransactionIdentity.accountId("CAMT053", iban, "B"))
    }
}
