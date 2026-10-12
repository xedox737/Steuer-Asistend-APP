package com.example.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase7ABackupProjectionTest {
    @Test fun unchangedSupplementalRestoreKeepsLoansAndDirectIncomeProjectionIdempotent() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val loans = listOf(Loan(id = 701, propertyId = "a", bezeichnung = "A", restschuld = 89000.50, sollzinsProzent = 3.125),
                Loan(id = 702, propertyId = "b", bezeichnung = "B", restschuld = 60000.0))
            loans.forEach { database.loanDao().upsertLoan(it) }
            database.bankDao().upsertAccount(BankAccount("account", "Synthetisch"))
            val transactions = listOf(BankTransaction("refund-a", "account", "2026-10-11", amount = 200.0,
                propertyId = "a", category = "Sonstige Einnahmen", classification = BankTransactionClassification.NORMAL,
                reconciliationStatus = BankReconciliationStatus.NO_RECEIPT_REQUIRED, noReceiptReason = "Sonstiges"),
                BankTransaction("refund-b", "account", "2026-10-11", amount = 300.0, propertyId = "b",
                    category = "Sonstige Einnahmen", reconciliationStatus = BankReconciliationStatus.NO_RECEIPT_REQUIRED, noReceiptReason = "Sonstiges"))
            transactions.forEach { database.bankDao().upsertTransaction(it) }
            val before = LedgerPaymentPresentation.entries(emptyList(), emptyList(), emptyList(), transactions)
            val payload = SupplementalDriveBackup.createPayload(context, database)
            assertEquals(15, payload.getInt("schemaVersion"))
            database.clearAllTables()
            repeat(2) { SupplementalDriveBackup.restorePayload(context, database, payload) }
            assertEquals(loans, database.loanDao().getAllLoans())
            val restored = database.bankDao().getAllTransactions()
            assertEquals(transactions.toSet(), restored.toSet())
            val after = LedgerPaymentPresentation.entries(emptyList(), database.bankRentAssignmentDao().getAll(), database.bankDao().getAllLinks(), restored)
            assertEquals(before.toSet(), after.toSet())
            assertEquals(500.0, LedgerPaymentPresentation.totals(after).income, .001)
            assertEquals(200.0, LedgerPaymentPresentation.totals(LedgerPaymentPresentation.filter(after, LedgerFilters(2026, propertyId = "a"))).income, .001)
        } finally { database.close() }
    }
}
