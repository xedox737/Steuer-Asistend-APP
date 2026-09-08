package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankPhase2CWorkflowAcceptanceTest {
    private lateinit var database: AppDatabase

    @Before fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() = database.close()

    private fun tx(id: String = "tx-loan", amount: Double = -445.0) = BankTransaction(
        transactionId = id,
        accountId = "acc-1",
        bookingDate = "2026-09-05",
        amount = amount,
        counterparty = "Hausbank",
        purpose = "Darlehen REF-A",
        propertyId = "property-a"
    )

    private fun loan(id: Int = 1) = Loan(
        id = id,
        bezeichnung = "Darlehen $id",
        bank = "Hausbank",
        monatlicheRate = 445.0,
        restschuld = 100_000.0,
        sollzinsProzent = 3.6,
        propertyId = "property-a"
    )

    @Test fun confirmingTwiceIsIdempotentAndManualLoanChangeReplacesAssignment() = runTest {
        val transaction = tx()
        database.bankDao().insertTransactions(listOf(transaction))
        val service = BankLoanAssignmentService(database.bankLoanAssignmentDao(), database.bankDao()) { "2026-09-08T17:00:00Z" }
        val firstLoan = loan(1)
        val firstSuggestion = BankLoanMatcher.score(transaction, firstLoan)
        service.confirm(transaction, firstLoan, firstSuggestion)
        service.confirm(transaction, firstLoan, firstSuggestion)
        assertEquals(1, database.bankLoanAssignmentDao().getAll().size)

        val otherLoan = loan(2)
        val otherSuggestion = BankLoanMatcher.score(transaction, otherLoan)
        service.confirm(transaction, otherLoan, otherSuggestion, source = BankLoanAssignmentSource.MANUAL)
        val assignments = database.bankLoanAssignmentDao().getAll()
        assertEquals(1, assignments.size)
        assertEquals(2, assignments.single().loanId)
        assertEquals(BankLoanAssignmentSource.MANUAL, assignments.single().source)
    }

    @Test fun positiveLoanMovementConfirmationUsesSharedReviewStatus() = runTest {
        val transaction = tx(id = "tx-positive", amount = 445.0)
        database.bankDao().insertTransactions(listOf(transaction))
        val currentLoan = loan()
        val suggestion = BankLoanMatcher.score(transaction, currentLoan)
        assertEquals(BankLoanConflictState.POSITIVE_LOAN_MOVEMENT, suggestion.conflictState)
        BankLoanAssignmentService(database.bankLoanAssignmentDao(), database.bankDao()) { "2026-09-08T17:00:00Z" }
            .confirm(transaction, currentLoan, suggestion)

        val assignment = database.bankLoanAssignmentDao().getForTransaction(transaction.transactionId)
        val storedTx = database.bankDao().getTransaction(transaction.transactionId)
        assertNotNull(assignment)
        assertEquals(BankLoanAssignmentStatus.REVIEW, assignment!!.status)
        assertEquals(BankReconciliationStatus.REVIEW, storedTx!!.reconciliationStatus)
    }

    @Test fun unlinkRemovesOnlyLoanAssignmentAndPreservesReceiptLink() = runTest {
        val transaction = tx(id = "tx-linked")
        database.bankDao().insertTransactions(listOf(transaction))
        val receiptLink = BankReceiptLink(
            linkId = "receipt-link-1",
            transactionId = transaction.transactionId,
            receiptId = 123,
            receiptInternalId = "receipt-123",
            allocatedAmount = 100.0,
            status = BankLinkStatus.CONFIRMED,
            source = BankLinkSource.NUTZER_BESTAETIGT,
            createdAt = "2026-09-08T16:00:00Z"
        )
        database.bankDao().upsertLink(receiptLink)
        val service = BankLoanAssignmentService(database.bankLoanAssignmentDao(), database.bankDao()) { "2026-09-08T17:00:00Z" }
        val currentLoan = loan()
        service.confirm(transaction, currentLoan, BankLoanMatcher.score(transaction, currentLoan))
        service.unlink(transaction.transactionId)

        assertTrue(database.bankLoanAssignmentDao().getAll().isEmpty())
        assertNotNull(database.bankDao().getLink(receiptLink.linkId))
        assertEquals(BankReconciliationStatus.OPEN, database.bankDao().getTransaction(transaction.transactionId)!!.reconciliationStatus)
    }

    @Test fun splitEditMustStillSumToAllocatedAmount() = runTest {
        val transaction = tx(id = "tx-split")
        database.bankDao().insertTransactions(listOf(transaction))
        val currentLoan = loan()
        val suggestion = BankLoanMatcher.score(transaction, currentLoan)
        val split = BankLoanSplitProposer.propose(currentLoan, transaction.absoluteAmount, suggestion.period)
        val service = BankLoanAssignmentService(database.bankLoanAssignmentDao(), database.bankDao()) { "2026-09-08T17:00:00Z" }
        service.confirm(transaction, currentLoan, suggestion, split = split)

        service.updateSplit(transaction.transactionId, interest = 200.0, principal = 200.0, accept = true, edited = true)
        var stored = database.bankLoanAssignmentDao().getForTransaction(transaction.transactionId)!!
        assertEquals(BankLoanSplitStatus.PROPOSED, stored.splitStatus)

        service.updateSplit(transaction.transactionId, interest = 200.0, principal = 245.0, accept = true, edited = true)
        stored = database.bankLoanAssignmentDao().getForTransaction(transaction.transactionId)!!
        assertEquals(BankLoanSplitStatus.EDITED, stored.splitStatus)
        assertEquals(445.0, stored.proposedInterest!! + stored.proposedPrincipal!!, 0.01)
    }
}
