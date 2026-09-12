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
class BankBatchActionsTest {
    private lateinit var database: AppDatabase

    @Before fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    private fun tx(id: String, property: String = "") = BankTransaction(
        transactionId = id, accountId = "a", bookingDate = "2026-09-12", amount = -50.0,
        counterparty = "Test", propertyId = property
    )

    @Test fun previewReportsExistingManualDataAndDefaultApplySkipsIt() = runTest {
        database.bankDao().insertTransactions(listOf(tx("clean"), tx("assigned", "p1")))
        val selected = database.bankDao().getAllTransactions()
        val preview = BankBatchActionPolicy.preview(selected, emptyList(), BankBatchAction.PRIVATE)
        assertEquals(1, preview.protected)

        val result = BankBatchActionService(database).apply(selected.map { it.transactionId }.toSet(), BankBatchAction.PRIVATE)
        assertEquals(1, result.changed)
        assertEquals(BankTransactionClassification.PRIVATE_IGNORED, database.bankDao().getTransaction("clean")!!.classification)
        assertEquals(BankTransactionClassification.NORMAL, database.bankDao().getTransaction("assigned")!!.classification)
    }

    @Test fun batchReviewAndUndoArePersistentAndLossless() = runTest {
        database.bankDao().insertTransactions(listOf(tx("a"), tx("b")))
        val service = BankBatchActionService(database)
        val result = service.apply(setOf("a", "b"), BankBatchAction.REVIEW_DONE)
        assertTrue(database.bankDao().getAllTransactions().all { it.reviewState == BankReviewState.DONE })
        service.restore(result.before)
        assertTrue(database.bankDao().getAllTransactions().all { it.reviewState == BankReviewState.OPEN })
    }

    @Test fun noReceiptBatchNeverSilentlyOverwritesConfirmedLink() = runTest {
        database.bankDao().insertTransactions(listOf(tx("linked")))
        database.bankDao().upsertLink(BankReceiptLink("link", "linked", 1, "r1", 50.0))
        val result = BankBatchActionService(database).apply(setOf("linked"), BankBatchAction.NO_RECEIPT_REQUIRED)
        assertEquals(0, result.changed)
        assertEquals(BankReconciliationStatus.OPEN, database.bankDao().getTransaction("linked")!!.reconciliationStatus)
    }
}
