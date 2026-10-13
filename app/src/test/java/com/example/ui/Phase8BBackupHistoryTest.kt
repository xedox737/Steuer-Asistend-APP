package com.example.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase8BBackupHistoryTest {
    @Test fun unchangedBackupRestoresVacantUnitBankHistoryAndAllRawAuditsIdempotently() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 81, propertyId = "p8b", name = "Haus", wohneinheiten = "WE 01"))
            val period = TenantPeriod(801, "WE 01", "Vormieter", "2026-01-01", "2026-09-30", 760.0, 220.0, 0.0)
            TenantHistoryStore.save(context, "p8b", "unit", "WE 01", listOf(period))
            db.bankDao().upsertAccount(BankAccount("account", "Synthetisch"))
            val tx = BankTransaction("bank-rent", "account", "2026-10-05", amount = 980.0,
                reconciliationStatus = BankReconciliationStatus.MATCHED, propertyId = "p8b", unitId = "unit")
            val assignment = BankRentAssignment("assignment", tx.transactionId, "p8b", "unit", "2026-09", "801", 980.0,
                BankSplitPaymentType.RENT, createdAt = "", updatedAt = "")
            db.bankDao().upsertTransaction(tx); db.bankRentAssignmentDao().upsert(assignment)
            val audits = (6 downTo 1).map { ExportAuditRun("run-$it", timestamp = it.toLong(), status = if (it == 1) "historical" else "SUCCESS") }
            audits.forEach { db.exportAuditDao().insertRun(it) }
            val before = LedgerPaymentPresentation.lastRentalPayment("p8b", "unit", emptyList(), listOf(assignment), emptyList(), listOf(tx))
            val payload = SupplementalDriveBackup.createPayload(context, db)
            assertEquals(15, payload.getInt("schemaVersion"))
            db.clearAllTables()
            context.getSharedPreferences("tenant_history_prefs", 0).edit().clear().commit()
            repeat(2) { SupplementalDriveBackup.restorePayload(context, db, payload) }
            assertEquals(listOf(period), TenantHistoryStore.load(context, "p8b", "unit", "WE 01"))
            assertEquals(audits, db.exportAuditDao().getAllRunsFlow().first())
            assertEquals(listOf(assignment), db.bankRentAssignmentDao().getAll())
            assertEquals(listOf(tx), db.bankDao().getAllTransactions())
            assertEquals(before, LedgerPaymentPresentation.lastRentalPayment("p8b", "unit", emptyList(),
                db.bankRentAssignmentDao().getAll(), db.bankDao().getAllLinks(), db.bankDao().getAllTransactions()))
        } finally { db.close() }
    }
}
