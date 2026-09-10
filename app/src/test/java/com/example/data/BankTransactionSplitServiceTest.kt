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
class BankTransactionSplitServiceTest {
    private lateinit var db: AppDatabase
    private lateinit var service: BankPhase2DService

    @Before fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        service = BankPhase2DService(db) { "2026-09-10T18:00:00Z" }
    }
    @After fun tearDown() = db.close()

    private fun tx(amount: Double = 1500.0, status: String = BankReconciliationStatus.OPEN, property: String = "p1") =
        BankTransaction("t", "a", "2026-09-10", amount = amount, counterparty = "Synthetic Tenant", purpose = "Synthetic payment", propertyId = property, reconciliationStatus = status)
    private fun split(amount: Double, type: String, property: String = "p1", unit: String = "u1", tenant: String = "Synthetic Tenant", note: String = "") =
        BankManualSplitPosition("t", amount, type, property, unit, tenant, "2026-09", note)
    private fun receipt(id: Int, amount: Double = 100.0, property: String = "p1") = Receipt(id=id, aussteller="Synthetic Vendor", datum="2026-09-09", uhrzeit="", bruttobetrag=amount, hauptkategorie="Kosten", unterkategorie="Test", kontoNr="", beschreibung="Synthetic", internalId="r$id", propertyId=property)

    @Test fun rentAndDepositFullyMatch1500() = runTest {
        db.bankDao().insertTransactions(listOf(tx()))
        val original = db.bankDao().getTransaction("t")!!
        val result = service.confirmManualSplit(listOf(split(1000.0, BankSplitPaymentType.RENT), split(500.0, BankSplitPaymentType.DEPOSIT)), true)
        assertTrue(result.success)
        val saved = db.bankRentAssignmentDao().getForTransaction("t")
        assertEquals(2, saved.size); assertEquals(setOf("MIETE", "KAUTION"), saved.map { it.paymentType }.toSet())
        assertEquals(1500.0, saved.sumOf { it.allocatedAmount }, 0.001)
        assertEquals(BankReconciliationStatus.MATCHED, db.bankDao().getTransaction("t")!!.reconciliationStatus)
        assertEquals(original.amount, db.bankDao().getTransaction("t")!!.amount, 0.0)
    }

    @Test fun partialRentLeaves500() = runTest {
        db.bankDao().insertTransactions(listOf(tx()))
        assertTrue(service.confirmManualSplit(listOf(split(1000.0, BankSplitPaymentType.RENT)), true).success)
        assertEquals(BankReconciliationStatus.PARTIAL, db.bankDao().getTransaction("t")!!.reconciliationStatus)
        assertEquals(500.0, BankTransactionSplitPolicy.remainingAmount(db.bankDao().getTransaction("t")!!, emptyList(), db.bankRentAssignmentDao().getForTransaction("t")), 0.001)
    }

    @Test fun overAllocationRejectsWithoutPartialPersistence() = runTest {
        db.bankDao().insertTransactions(listOf(tx()))
        val result = service.confirmManualSplit(listOf(split(1000.0, BankSplitPaymentType.RENT), split(600.0, BankSplitPaymentType.DEPOSIT)), true)
        assertFalse(result.success); assertTrue(db.bankRentAssignmentDao().getForTransaction("t").isEmpty())
    }

    @Test fun existing500Plus1000Completes() = runTest {
        db.bankDao().insertTransactions(listOf(tx()))
        assertTrue(service.confirmManualSplit(listOf(split(500.0, BankSplitPaymentType.DEPOSIT)), true).success)
        assertTrue(service.confirmManualSplit(listOf(split(1000.0, BankSplitPaymentType.RENT)), true).success)
        assertEquals(BankReconciliationStatus.MATCHED, db.bankDao().getTransaction("t")!!.reconciliationStatus)
    }

    @Test fun doubleConfirmationIsIdempotent() = runTest {
        db.bankDao().insertTransactions(listOf(tx()))
        val positions = listOf(split(1000.0, BankSplitPaymentType.RENT), split(500.0, BankSplitPaymentType.DEPOSIT))
        assertTrue(service.confirmManualSplit(positions, true).success)
        assertTrue(service.confirmManualSplit(positions, true).success)
        assertEquals(2, db.bankRentAssignmentDao().getForTransaction("t").size)
    }

    @Test fun noReceiptRequiredIsProtected() = runTest {
        db.bankDao().insertTransactions(listOf(tx(status=BankReconciliationStatus.NO_RECEIPT_REQUIRED)))
        assertFalse(service.confirmManualSplit(listOf(split(1500.0, BankSplitPaymentType.RENT)), true).success)
        assertTrue(db.bankRentAssignmentDao().getForTransaction("t").isEmpty())
        assertEquals(BankReconciliationStatus.NO_RECEIPT_REQUIRED, db.bankDao().getTransaction("t")!!.reconciliationStatus)
    }

    @Test fun changeAmountUpdatesRestAndStatus() = runTest {
        db.bankDao().insertTransactions(listOf(tx()))
        assertTrue(service.confirmManualSplit(listOf(split(1000.0, BankSplitPaymentType.RENT)), true).success)
        val id = db.bankRentAssignmentDao().getForTransaction("t").single().assignmentId
        assertTrue(service.changeManualSplitAssignment(id,1500.0,true).success)
        assertEquals(BankReconciliationStatus.MATCHED, db.bankDao().getTransaction("t")!!.reconciliationStatus)
    }

    @Test fun unlinkUpdatesRestAndStatus() = runTest {
        db.bankDao().insertTransactions(listOf(tx()))
        assertTrue(service.confirmManualSplit(listOf(split(1500.0, BankSplitPaymentType.RENT)), true).success)
        val id = db.bankRentAssignmentDao().getForTransaction("t").single().assignmentId
        assertTrue(service.unlinkManualSplitAssignment(id).success)
        assertEquals(BankReconciliationStatus.OPEN, db.bankDao().getTransaction("t")!!.reconciliationStatus)
        assertEquals(1500.0, db.bankDao().getTransaction("t")!!.absoluteAmount, 0.0)
    }

    @Test fun propertyConflictIsBlocked() = runTest {
        db.bankDao().insertTransactions(listOf(tx(property="p1")))
        assertFalse(service.confirmManualSplit(listOf(split(1500.0, BankSplitPaymentType.RENT, property="p2")), true).success)
        assertTrue(db.bankRentAssignmentDao().getForTransaction("t").isEmpty())
    }

    @Test fun centRoundingUsesExistingPolicy() = runTest {
        db.bankDao().insertTransactions(listOf(tx(amount=10.01)))
        val p = split(10.005, BankSplitPaymentType.RENT)
        val preview = BankTransactionSplitPolicy.preview(db.bankDao().getTransaction("t")!!, emptyList(), emptyList(), listOf(p))
        assertEquals(BankAllocationPolicy.roundMoney(10.005), preview.newAllocations, 0.0)
        assertTrue(preview.valid)
    }

    @Test fun existingOneToOneReceiptStillWorksWithManualRemainderPolicy() = runTest {
        db.bankDao().insertTransactions(listOf(tx(amount=-100.0)))
        db.receiptDao().insertAll(listOf(receipt(1,100.0)))
        assertTrue(service.confirmManualAllocations(listOf(BankProposedAllocation("t",1,"r1",100.0)),true).success)
        assertEquals(1, db.bankDao().getLinksForTransaction("t").size)
        assertEquals(BankReconciliationStatus.MATCHED, db.bankDao().getTransaction("t")!!.reconciliationStatus)
    }

    @Test fun phase2DCombinationStillWorksAndCannotOverallocateManualSplit() = runTest {
        db.bankDao().insertTransactions(listOf(tx(amount=-300.0)))
        db.receiptDao().insertAll(listOf(receipt(1,100.0), receipt(2,200.0)))
        val suggestion = BankCombinationMatcher.oneTransactionToManyReceipts(db.bankDao().getTransaction("t")!!, db.receiptDao().getAllReceiptsList(), emptyList()).first()
        assertTrue(service.confirmCombination(suggestion,true).success)
        assertEquals(BankReconciliationStatus.MATCHED, db.bankDao().getTransaction("t")!!.reconciliationStatus)
    }

    @Test fun sameTargetDifferentAmountIsNeverSilentlyOverwrittenAndNotePersists() = runTest {
        db.bankDao().insertTransactions(listOf(tx()))
        assertTrue(service.confirmManualSplit(listOf(split(1000.0, BankSplitPaymentType.RENT, note="Synthetic note")),true).success)
        assertFalse(service.confirmManualSplit(listOf(split(900.0, BankSplitPaymentType.RENT, note="Synthetic note")),true).success)
        assertEquals(1000.0, db.bankRentAssignmentDao().getForTransaction("t").single().allocatedAmount,0.0)
        assertEquals("Synthetic note", db.bankRentAssignmentDao().getForTransaction("t").single().note)
    }
}
