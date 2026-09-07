package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankPhase1LastPatchAcceptanceTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() = database.close()

    @Test fun manualReviewOpenToReviewAndBackToOpenUpdatesAuditAndUiClassification() = runTest {
        val dao = database.bankDao()
        dao.upsertTransaction(BankTransaction("review", "a", "2026-09-07", amount = -10.0, importedAt = "imported"))
        var now = "2026-09-07T21:30:00Z"
        val service = BankReviewStatusService(dao) { now }

        assertEquals(BankReconciliationStatus.REVIEW, service.markForReview("review"))
        val reviewed = dao.getTransaction("review")!!
        assertEquals(BankReconciliationStatus.REVIEW, reviewed.reconciliationStatus)
        assertEquals("", reviewed.noReceiptReason)
        assertEquals("2026-09-07T21:30:00Z", reviewed.updatedAt)
        assertTrue(BankReviewUiPolicy.isReviewQueue(reviewed.reconciliationStatus))
        assertFalse(BankReviewUiPolicy.isMatched(reviewed.reconciliationStatus))
        assertFalse(BankReviewUiPolicy.isNoReceiptRequired(reviewed.reconciliationStatus))

        now = "2026-09-07T21:31:00Z"
        assertEquals(BankReconciliationStatus.OPEN, service.reopen("review"))
        val reopened = dao.getTransaction("review")!!
        assertEquals(BankReconciliationStatus.OPEN, reopened.reconciliationStatus)
        assertEquals("2026-09-07T21:31:00Z", reopened.updatedAt)
    }

    @Test fun manualReviewDoesNotDeleteLinksOrOverridePartialAndMatched() = runTest {
        val dao = database.bankDao()
        val partial = BankTransaction("partial", "a", "2026-09-07", amount = -150.0, reconciliationStatus = BankReconciliationStatus.PARTIAL)
        val matched = BankTransaction("matched", "a", "2026-09-07", amount = -150.0, reconciliationStatus = BankReconciliationStatus.MATCHED)
        dao.upsertTransaction(partial)
        dao.upsertTransaction(matched)
        dao.upsertLink(BankReceiptLink("lp", "partial", 1, "r1", 100.0))
        dao.upsertLink(BankReceiptLink("lm", "matched", 2, "r2", 150.0))
        val service = BankReviewStatusService(dao) { "changed" }

        assertEquals(BankReconciliationStatus.PARTIAL, service.markForReview("partial"))
        assertEquals(BankReconciliationStatus.MATCHED, service.markForReview("matched"))
        assertEquals(BankReconciliationStatus.PARTIAL, dao.getTransaction("partial")!!.reconciliationStatus)
        assertEquals(BankReconciliationStatus.MATCHED, dao.getTransaction("matched")!!.reconciliationStatus)
        assertEquals(1, dao.getLinksForTransaction("partial").size)
        assertEquals(1, dao.getLinksForTransaction("matched").size)
    }

    @Test fun noReceiptRequiredMustBeReopenedBeforeManualReview() = runTest {
        val dao = database.bankDao()
        dao.upsertTransaction(
            BankTransaction(
                "no-receipt", "a", "2026-09-07", amount = -7.5,
                reconciliationStatus = BankReconciliationStatus.NO_RECEIPT_REQUIRED,
                noReceiptReason = "Bankgebühr",
                updatedAt = "original"
            )
        )
        val service = BankReviewStatusService(dao) { "changed" }
        assertEquals(BankReconciliationStatus.NO_RECEIPT_REQUIRED, service.markForReview("no-receipt"))
        val unchanged = dao.getTransaction("no-receipt")!!
        assertEquals("Bankgebühr", unchanged.noReceiptReason)
        assertEquals("original", unchanged.updatedAt)

        assertEquals(BankReconciliationStatus.OPEN, service.reopen("no-receipt"))
        assertEquals(BankReconciliationStatus.REVIEW, service.markForReview("no-receipt"))
    }

    @Test fun identicalCsvReimportKeepsExactlyThreeRowsAndPreservesExistingData() = runTest {
        val csv = """
            Buchungstag;Auftraggeber/Empfänger;Verwendungszweck;Betrag;Referenz
            01.09.2026;Hornbach;Material;-84,50;REF-1
            02.09.2026;Versicherung;Beitrag;-120,00;REF-2
            03.09.2026;Max Mustermann;Miete September;950,00;REF-3
        """.trimIndent()
        val first = BankImportParser.parseCsv(csv, "Hauskonto", "first", "konto.csv")
        database.bankDao().upsertAccount(first.account)
        database.bankDao().insertTransactions(first.transactions)
        assertEquals(3, database.bankDao().getAllTransactions().size)
        val firstIds = first.transactions.map { it.transactionId }

        database.bankDao().updateTransactionStatus(firstIds.first(), BankReconciliationStatus.REVIEW, "", "reviewed")
        val second = BankImportParser.parseCsv(csv, "Hauskonto", "second", "konto.csv")
        database.bankDao().upsertAccount(second.account)
        database.bankDao().insertTransactions(second.transactions)

        val stored = database.bankDao().getAllTransactions()
        assertEquals(3, stored.size)
        assertEquals(firstIds.toSet(), second.transactions.map { it.transactionId }.toSet())
        assertEquals(firstIds.toSet(), stored.map { it.transactionId }.toSet())
        assertEquals(BankReconciliationStatus.REVIEW, database.bankDao().getTransaction(firstIds.first())!!.reconciliationStatus)
        assertEquals("reviewed", database.bankDao().getTransaction(firstIds.first())!!.updatedAt)
    }

    @Test fun identicalDuplicateRowsStayTwoRealStableRowsAcrossRoomReimport() = runTest {
        val csv = """
            Buchungstag;Auftraggeber/Empfänger;Verwendungszweck;Betrag;Referenz
            04.09.2026;A;X;-10,00;R
            04.09.2026;A;X;-10,00;R
        """.trimIndent()
        val first = BankImportParser.parseCsv(csv, "Hauskonto", "first", "dup.csv")
        assertEquals(2, first.transactions.size)
        assertNotEquals(first.transactions[0].transactionId, first.transactions[1].transactionId)
        database.bankDao().insertTransactions(first.transactions)
        assertEquals(2, database.bankDao().getAllTransactions().size)

        val second = BankImportParser.parseCsv(csv, "Hauskonto", "second", "dup.csv")
        assertEquals(first.transactions.map { it.transactionId }, second.transactions.map { it.transactionId })
        database.bankDao().insertTransactions(second.transactions)
        assertEquals(2, database.bankDao().getAllTransactions().size)
    }

    @Test fun differentReferencesRemainDistinctAcrossRoomReimport() = runTest {
        val csv = """
            Buchungstag;Auftraggeber/Empfänger;Verwendungszweck;Betrag;Referenz
            05.09.2026;A;X;-10,00;REF-A
            05.09.2026;A;X;-10,00;REF-B
        """.trimIndent()
        val first = BankImportParser.parseCsv(csv, "Hauskonto", "first", "refs.csv")
        assertEquals(2, first.transactions.size)
        assertNotEquals(first.transactions[0].transactionId, first.transactions[1].transactionId)
        database.bankDao().insertTransactions(first.transactions)

        val second = BankImportParser.parseCsv(csv, "Hauskonto", "second", "refs.csv")
        database.bankDao().insertTransactions(second.transactions)
        assertEquals(first.transactions.map { it.transactionId }, second.transactions.map { it.transactionId })
        assertEquals(2, database.bankDao().getAllTransactions().size)
    }
}
