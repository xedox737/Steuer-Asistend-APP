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

/** MW-01: an allocation is only complete when its actual property/unit target exists. */
@RunWith(AndroidJUnit4::class)
class MartinWeberSplitReferenceRegressionTest {
    private lateinit var database: AppDatabase
    private lateinit var service: BankPhase2DService

    @Before fun setUp() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        service = BankPhase2DService(database) { "2026-11-06T10:00:00Z" }
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(
            id = 1, propertyId = "p1", name = "MFH Lindenstraße",
            wohneinheiten = UnitIdentity.encode(listOf(UnitIdentity.Entry("u1", "EG links")))
        ))
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(
            id = 2, propertyId = "p2", name = "MFH Bergstraße",
            wohneinheiten = UnitIdentity.encode(listOf(UnitIdentity.Entry("u2", "EG links")))
        ))
        // No preselected property: this was the path on which the bad reference was accepted.
        database.bankDao().insertTransactions(listOf(BankTransaction(
            transactionId = "payment", accountId = "account", bookingDate = "2026-11-06",
            amount = 980.0, counterparty = "Synthetic tenant", purpose = "Rent November"
        )))
    }

    @After fun tearDown() = database.close()

    private fun position(
        propertyId: String = "p1",
        unitId: String = "u1",
        amount: Double = 980.0,
        type: String = BankSplitPaymentType.RENT
    ) = BankManualSplitPosition(
        transactionId = "payment", amount = amount, paymentType = type,
        propertyId = propertyId, unitId = unitId, tenantReference = "Synthetic tenant",
        rentMonth = "2026-11"
    )

    private suspend fun assertRejectedUnchanged(positions: List<BankManualSplitPosition>) {
        val transactionBefore = database.bankDao().getTransaction("payment")
        val assignmentsBefore = database.bankRentAssignmentDao().getForTransaction("payment")
        val linksBefore = database.bankDao().getLinksForTransaction("payment")
        val result = service.confirmManualSplit(positions, explicitlyConfirmed = true)
        assertFalse("Invalid property/unit reference must not complete the payment", result.success)
        assertTrue("Rejection must explain the problem", result.message.isNotBlank())
        assertEquals(transactionBefore, database.bankDao().getTransaction("payment"))
        assertEquals(assignmentsBefore, database.bankRentAssignmentDao().getForTransaction("payment"))
        assertEquals(linksBefore, database.bankDao().getLinksForTransaction("payment"))
    }

    @Test fun typedPropertyNameCannotBecomeAnInternalPropertyId() = runTest {
        assertRejectedUnchanged(listOf(position(propertyId = "MFH Lindenstraße")))
    }

    @Test fun unknownUnitCannotMarkThePaymentMatched() = runTest {
        assertRejectedUnchanged(listOf(position(unitId = "missing-unit")))
    }

    @Test fun sameUnitNameInAnotherPropertyDoesNotPermitCrossPropertyAllocation() = runTest {
        assertRejectedUnchanged(listOf(position(propertyId = "p1", unitId = "u2")))
    }

    @Test fun invalidLaterPositionDoesNotPersistEarlierValidPosition() = runTest {
        assertRejectedUnchanged(listOf(
            position(amount = 700.0),
            position(unitId = "missing-unit", amount = 280.0, type = BankSplitPaymentType.DEPOSIT)
        ))
    }

    @Test fun invalidNewPositionPreservesExistingAllocationAndPartialStatus() = runTest {
        assertTrue(service.confirmManualSplit(listOf(position(amount = 700.0)), true).success)
        assertEquals(BankReconciliationStatus.PARTIAL,
            database.bankDao().getTransaction("payment")!!.reconciliationStatus)
        assertRejectedUnchanged(listOf(position(
            unitId = "missing-unit", amount = 280.0, type = BankSplitPaymentType.DEPOSIT
        )))
    }

    @Test fun realPropertyAndUnitCompleteThePaymentWithoutDuplicates() = runTest {
        val positions = listOf(position())
        assertTrue(service.confirmManualSplit(positions, true).success)
        assertTrue(service.confirmManualSplit(positions, true).success)
        val saved = database.bankRentAssignmentDao().getForTransaction("payment")
        assertEquals(1, saved.size)
        assertEquals("p1", saved.single().propertyId)
        assertEquals("u1", saved.single().unitId)
        assertEquals(980.0, saved.single().allocatedAmount, 0.0)
        assertEquals(BankReconciliationStatus.MATCHED,
            database.bankDao().getTransaction("payment")!!.reconciliationStatus)
        assertEquals(980.0, database.bankDao().getTransaction("payment")!!.amount, 0.0)
    }
}
