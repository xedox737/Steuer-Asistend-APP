package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankPhase2DReimportAcceptanceTest {
    private lateinit var database: AppDatabase

    @Before fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() = database.close()

    @Test fun reimportKeepsTransactionIdentityAndCannotCreateDuplicateAllocation() = runTest {
        val csv = """
            Buchungstag;Wertstellung;Auftraggeber/Empfänger;Verwendungszweck;Betrag;Währung;IBAN
            05.09.2026;05.09.2026;Vendor;Rechnung 1;-100,00;EUR;DE111
        """.trimIndent()

        val first = BankImportParser.parseCsv(csv, "konto.csv", "first")
        val second = BankImportParser.parseCsv(csv, "konto.csv", "second")
        val firstTx = first.transactions.single()
        val secondTx = second.transactions.single()

        assertEquals(firstTx.transactionId, secondTx.transactionId)

        database.bankDao().insertTransactions(first.transactions)
        database.bankDao().insertTransactions(second.transactions)

        val receipt = Receipt(
            id = 1,
            aussteller = "Vendor",
            datum = "2026-09-04",
            uhrzeit = "",
            bruttobetrag = 100.0,
            hauptkategorie = "Kosten",
            unterkategorie = "Material",
            kontoNr = "",
            beschreibung = "Rechnung 1",
            internalId = "r1"
        )
        database.receiptDao().insertAll(listOf(receipt))

        val service = BankPhase2DService(database) { "now" }
        val allocation = BankProposedAllocation(firstTx.transactionId, 1, "r1", 100.0)

        assertTrue(service.confirmManualAllocations(listOf(allocation), true).success)
        assertTrue(service.confirmManualAllocations(listOf(allocation), true).success)

        val links = database.bankDao().getAllLinks()
        assertEquals(1, links.size)
        assertEquals(firstTx.transactionId, links.single().transactionId)
        assertEquals(100.0, links.single().allocatedAmount, 0.001)
    }
}