package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankPhase2DServiceTest {
    private lateinit var database: AppDatabase

    @Before fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    private fun tx(id: String, amount: Double = -300.0, property: String = "p1", status: String = BankReconciliationStatus.OPEN) = BankTransaction(
        transactionId=id, accountId="a", bookingDate="2026-09-05", amount=amount, counterparty="Vendor", purpose="Sammelzahlung", propertyId=property, reconciliationStatus=status
    )
    private fun receipt(id: Int, amount: Double, property: String = "p1") = Receipt(
        id=id, aussteller="Vendor", datum="2026-09-04", uhrzeit="", bruttobetrag=amount,
        hauptkategorie="Kosten", unterkategorie="Material", kontoNr="", beschreibung="Invoice", internalId="r$id", propertyId=property
    )
    private fun safe(key: String, txId: String, receiptId: Int, amount: Double = 100.0) = BankReviewItem(
        key, BankReviewType.SAFE_SUGGESTION, 30, listOf(txId), listOf(receiptId), amount=amount, remainingAmount=amount,
        score=95, confidence="HOCH", title="Safe", explanation=""
    )

    @Test fun combinationRequiresExplicitUserConfirmation() = runTest {
        val transaction = tx("t")
        database.bankDao().insertTransactions(listOf(transaction))
        database.receiptDao().insertAll(listOf(receipt(1,100.0), receipt(2,80.0), receipt(3,120.0)))
        val suggestion = BankCombinationMatcher.oneTransactionToManyReceipts(transaction, database.receiptDao().getAllReceiptsList(), emptyList()).first { it.receiptIds.size == 3 }
        val result = BankPhase2DService(database).confirmCombination(suggestion, explicitlyConfirmed=false)
        assertFalse(result.success)
        assertTrue(database.bankDao().getAllLinks().isEmpty())
    }

    @Test fun exactOneToManyCreatesIndividualLinksAndMatchedStatus() = runTest {
        val transaction = tx("t")
        database.bankDao().insertTransactions(listOf(transaction))
        database.receiptDao().insertAll(listOf(receipt(1,100.0), receipt(2,80.0), receipt(3,120.0)))
        val suggestion = BankCombinationMatcher.oneTransactionToManyReceipts(transaction, database.receiptDao().getAllReceiptsList(), emptyList()).first { it.receiptIds.size == 3 }
        val result = BankPhase2DService(database) { "now" }.confirmCombination(suggestion, true)
        assertTrue(result.success)
        assertEquals(3, database.bankDao().getAllLinks().size)
        assertEquals(300.0, database.bankDao().getAllLinks().sumOf { it.allocatedAmount }, 0.001)
        assertEquals(BankReconciliationStatus.MATCHED, database.bankDao().getTransaction("t")!!.reconciliationStatus)
    }

    @Test fun repeatingSameCombinationIsIdempotent() = runTest {
        val transaction = tx("t")
        database.bankDao().insertTransactions(listOf(transaction))
        database.receiptDao().insertAll(listOf(receipt(1,100.0), receipt(2,80.0), receipt(3,120.0)))
        val suggestion = BankCombinationMatcher.oneTransactionToManyReceipts(transaction, database.receiptDao().getAllReceiptsList(), emptyList()).first { it.receiptIds.size == 3 }
        val service = BankPhase2DService(database) { "now" }
        assertTrue(service.confirmCombination(suggestion, true).success)
        assertTrue(service.confirmCombination(suggestion, true).success)
        assertEquals(3, database.bankDao().getAllLinks().size)
    }

    @Test fun conflictingExistingUserAllocationIsNeverOverwritten() = runTest {
        val transaction = tx("t", -100.0)
        val r = receipt(1,100.0)
        database.bankDao().insertTransactions(listOf(transaction))
        database.receiptDao().insertAll(listOf(r))
        database.bankDao().upsertLink(BankReceiptLink(BankLinkPolicy.linkId("t",1,"r1"),"t",1,"r1",40.0,createdAt="old"))
        val result = BankPhase2DService(database).confirmManualAllocations(listOf(BankProposedAllocation("t",1,"r1",60.0)), true)
        assertFalse(result.success)
        assertEquals(40.0, database.bankDao().getAllLinks().single().allocatedAmount, 0.001)
    }

    @Test fun noReceiptRequiredIsNeverAutomaticallyOverwritten() = runTest {
        val transaction = tx("t", -100.0, status=BankReconciliationStatus.NO_RECEIPT_REQUIRED)
        database.bankDao().insertTransactions(listOf(transaction))
        database.receiptDao().insertAll(listOf(receipt(1,100.0)))
        val result = BankPhase2DService(database).confirmManualAllocations(listOf(BankProposedAllocation("t",1,"r1",100.0)), true)
        assertFalse(result.success)
        assertEquals(BankReconciliationStatus.NO_RECEIPT_REQUIRED, database.bankDao().getTransaction("t")!!.reconciliationStatus)
    }

    @Test fun propertyConflictBlocksConfirmation() = runTest {
        database.bankDao().insertTransactions(listOf(tx("t", -100.0, property="p1")))
        database.receiptDao().insertAll(listOf(receipt(1,100.0,property="p2")))
        val result = BankPhase2DService(database).confirmManualAllocations(listOf(BankProposedAllocation("t",1,"r1",100.0)), true)
        assertFalse(result.success)
        assertTrue(database.bankDao().getAllLinks().isEmpty())
    }

    @Test fun allocationChangeRecalculatesStatusAndGuardsOverAllocation() = runTest {
        val transaction = tx("t", -300.0)
        database.bankDao().insertTransactions(listOf(transaction))
        database.receiptDao().insertAll(listOf(receipt(1,300.0)))
        val service = BankPhase2DService(database) { "now" }
        assertTrue(service.confirmManualAllocations(listOf(BankProposedAllocation("t",1,"r1",200.0)), true).success)
        val link = database.bankDao().getAllLinks().single()
        assertEquals(BankReconciliationStatus.PARTIAL, database.bankDao().getTransaction("t")!!.reconciliationStatus)
        assertTrue(service.changeAllocation(link.linkId, 300.0, true).success)
        assertEquals(BankReconciliationStatus.MATCHED, database.bankDao().getTransaction("t")!!.reconciliationStatus)
        assertFalse(service.changeAllocation(link.linkId, 301.0, true).success)
    }

    @Test fun unlinkReopensTransactionWithoutDeletingReceiptOrTransaction() = runTest {
        val transaction = tx("t", -100.0)
        database.bankDao().insertTransactions(listOf(transaction))
        database.receiptDao().insertAll(listOf(receipt(1,100.0)))
        val service = BankPhase2DService(database) { "now" }
        assertTrue(service.confirmManualAllocations(listOf(BankProposedAllocation("t",1,"r1",100.0)), true).success)
        val linkId = database.bankDao().getAllLinks().single().linkId
        assertTrue(service.unlink(linkId).success)
        assertTrue(database.bankDao().getAllLinks().isEmpty())
        assertEquals(BankReconciliationStatus.OPEN, database.bankDao().getTransaction("t")!!.reconciliationStatus)
        assertTrue(database.receiptDao().getReceiptById(1) != null)
    }

    @Test fun safeBatchRequiresFinalConfirmationAndIsIdempotent() = runTest {
        database.bankDao().insertTransactions(listOf(tx("t",-100.0)))
        database.receiptDao().insertAll(listOf(receipt(1,100.0)))
        val item = safe("safe", "t", 1)
        val service = BankPhase2DService(database) { "now" }
        assertFalse(service.executeSafeBatch(listOf(item), false).success)
        assertTrue(service.executeSafeBatch(listOf(item), true).success)
        assertTrue(service.executeSafeBatch(listOf(item), true).success)
        assertEquals(1, database.bankDao().getAllLinks().size)
    }

    @Test fun emptyBatchChangesNothing() = runTest {
        val result = BankPhase2DService(database).executeSafeBatch(emptyList(), true)
        assertFalse(result.success)
        assertTrue(database.bankDao().getAllLinks().isEmpty())
    }

    @Test fun severalSafeCasesAreAppliedAtomically() = runTest {
        database.bankDao().insertTransactions(listOf(tx("t1",-100.0), tx("t2",-80.0)))
        database.receiptDao().insertAll(listOf(receipt(1,100.0), receipt(2,80.0)))
        val result = BankPhase2DService(database) { "now" }.executeSafeBatch(
            listOf(safe("s1","t1",1,100.0), safe("s2","t2",2,80.0)), true
        )
        assertTrue(result.success)
        assertEquals(2, database.bankDao().getAllLinks().size)
        assertEquals(180.0, database.bankDao().getAllLinks().sumOf { it.allocatedAmount }, 0.001)
    }

    @Test fun duplicateStableKeyDoesNotCreateDuplicateLink() = runTest {
        database.bankDao().insertTransactions(listOf(tx("t",-100.0)))
        database.receiptDao().insertAll(listOf(receipt(1,100.0)))
        val item = safe("same","t",1)
        val result = BankPhase2DService(database) { "now" }.executeSafeBatch(listOf(item,item), true)
        assertTrue(result.success)
        assertEquals(1, database.bankDao().getAllLinks().size)
    }

    @Test fun sameTransactionTwiceInBatchIsRejectedBeforeAnyWrite() = runTest {
        database.bankDao().insertTransactions(listOf(tx("t",-100.0)))
        database.receiptDao().insertAll(listOf(receipt(1,100.0), receipt(2,100.0)))
        val result = BankPhase2DService(database).executeSafeBatch(
            listOf(safe("a","t",1), safe("b","t",2)), true
        )
        assertFalse(result.success)
        assertTrue(database.bankDao().getAllLinks().isEmpty())
    }

    @Test fun sameReceiptTwiceInBatchIsRejectedBeforeAnyWrite() = runTest {
        database.bankDao().insertTransactions(listOf(tx("t1",-100.0), tx("t2",-100.0)))
        database.receiptDao().insertAll(listOf(receipt(1,100.0)))
        val result = BankPhase2DService(database).executeSafeBatch(
            listOf(safe("a","t1",1), safe("b","t2",1)), true
        )
        assertFalse(result.success)
        assertTrue(database.bankDao().getAllLinks().isEmpty())
    }

    @Test fun unsafeCaseRejectsWholeBatchWithoutPartialWrite() = runTest {
        database.bankDao().insertTransactions(listOf(tx("t1",-100.0), tx("t2",-100.0)))
        database.receiptDao().insertAll(listOf(receipt(1,100.0), receipt(2,100.0)))
        val safe = safe("s","t1",1)
        val unsafe = safe("u","t2",2).copy(type=BankReviewType.PROPERTY_CONFLICT, conflicts=listOf(BankCombinationConflict.PROPERTY_CONFLICT))
        val result = BankPhase2DService(database).executeSafeBatch(listOf(safe,unsafe), true)
        assertFalse(result.success)
        assertTrue(database.bankDao().getAllLinks().isEmpty())
    }

    @Test fun currentDatabasePropertyGuardRollsBackWholeBatch() = runTest {
        database.bankDao().insertTransactions(listOf(tx("t1",-100.0), tx("t2",-100.0, property="p1")))
        database.receiptDao().insertAll(listOf(receipt(1,100.0), receipt(2,100.0, property="p2")))
        val result = BankPhase2DService(database).executeSafeBatch(
            listOf(safe("s1","t1",1), safe("s2","t2",2)), true
        )
        assertFalse(result.success)
        assertTrue(database.bankDao().getAllLinks().isEmpty())
    }

    @Test fun batchRejectsMissingReceiptAndLoanSpecialCasesByType() = runTest {
        val base = safe("x","t",1)
        assertFalse(BankBatchEligibility.evaluate(base.copy(type=BankReviewType.MISSING_RECEIPT)).eligible)
        assertFalse(BankBatchEligibility.evaluate(base.copy(type=BankReviewType.LOAN_REVIEW)).eligible)
        assertFalse(BankBatchEligibility.evaluate(base.copy(type=BankReviewType.RENT_REVIEW)).eligible)
        assertFalse(BankBatchEligibility.evaluate(base.copy(type=BankReviewType.PARTIAL_PAYMENT)).eligible)
        assertFalse(BankBatchEligibility.evaluate(base.copy(type=BankReviewType.RECURRING_REVIEW)).eligible)
        assertFalse(BankBatchEligibility.evaluate(base.copy(type=BankReviewType.POSSIBLE_DUPLICATE)).eligible)
    }
}
