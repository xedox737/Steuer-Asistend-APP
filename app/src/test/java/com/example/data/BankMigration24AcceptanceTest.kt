package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BankMigration24AcceptanceTest {
    @Test fun migration24To25PreservesBankRowsAndAddsLearningColumns() = withDb(24) { db ->
        db.execSQL("CREATE TABLE bank_accounts (accountId TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, bankName TEXT NOT NULL, iban TEXT NOT NULL, currency TEXT NOT NULL, importFormat TEXT NOT NULL, active INTEGER NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, ownerName TEXT NOT NULL)")
        db.execSQL("CREATE TABLE bank_transactions (transactionId TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, bookingDate TEXT NOT NULL, valueDate TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, counterparty TEXT NOT NULL, counterpartyIban TEXT NOT NULL, purpose TEXT NOT NULL, bankReference TEXT NOT NULL, source TEXT NOT NULL, reconciliationStatus TEXT NOT NULL, noReceiptReason TEXT NOT NULL, importedAt TEXT NOT NULL, propertyId TEXT NOT NULL, unitId TEXT NOT NULL, importFileName TEXT NOT NULL, importRunId TEXT NOT NULL, updatedAt TEXT NOT NULL)")
        db.execSQL("CREATE TABLE bank_receipt_links (linkId TEXT NOT NULL PRIMARY KEY, transactionId TEXT NOT NULL, receiptId INTEGER NOT NULL, receiptInternalId TEXT NOT NULL, allocatedAmount REAL NOT NULL, status TEXT NOT NULL, createdAt TEXT NOT NULL, source TEXT NOT NULL)")
        db.execSQL("CREATE TABLE bank_learning_rules (ruleId TEXT NOT NULL PRIMARY KEY, matchCounterparty TEXT NOT NULL, matchIban TEXT NOT NULL, matchPurposeContains TEXT NOT NULL, matchAmountMin REAL, matchAmountMax REAL, propertyId TEXT NOT NULL, unitId TEXT NOT NULL, receiptCategory TEXT NOT NULL, account TEXT NOT NULL, counterAccount TEXT NOT NULL, bookingText TEXT NOT NULL, enabled INTEGER NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL)")
        db.execSQL("CREATE TABLE bank_rule_evidence (evidenceId TEXT NOT NULL PRIMARY KEY, ruleId TEXT NOT NULL, transactionId TEXT NOT NULL, receiptId INTEGER, receiptInternalId TEXT NOT NULL, decision TEXT NOT NULL, createdAt TEXT NOT NULL)")
        db.execSQL("INSERT INTO bank_accounts VALUES ('a','Haus','Sparkasse','DE1','EUR','CSV',1,'c','u','Sergej')")
        db.execSQL("INSERT INTO bank_transactions VALUES ('t','a','2026-09-04','',-84.5,'EUR','Hornbach','','Material','R','CSV','OPEN','','import','p','u','konto.csv','run','changed')")
        db.execSQL("INSERT INTO bank_receipt_links VALUES ('l','t',1,'r-1',84.5,'CONFIRMED','created','MANUELL')")
        MIGRATION_24_25.migrate(db)
        assertCoreSentinels(db)
        db.query("SELECT counterparty, updatedAt FROM bank_transactions WHERE transactionId='t'").use {
            assertTrue(it.moveToFirst()); assertEquals("Hornbach", it.getString(0)); assertEquals("changed", it.getString(1))
        }
        assertTrue(columns(db, "bank_learning_rules").contains("receiptCategoryTarget"))
        assertTrue(columns(db, "bank_rule_evidence").contains("paymentMethodTarget"))
    }

    @Test fun migration26To27CreatesLoanAndRecurringTables() = withDb(26) { db ->
        MIGRATION_26_27.migrate(db)
        assertTrue(columns(db, "bank_loan_assignments").containsAll(setOf("assignmentId", "transactionId", "loanId", "paymentType", "period", "splitStatus")))
        assertTrue(columns(db, "bank_recurring_patterns").containsAll(setOf("patternId", "enabled", "cadence", "typicalAmount", "confidence", "nextExpectedStart", "nextExpectedEnd")))
    }

    @Test fun freshDatabaseIsVersion29() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            assertEquals(29, database.openHelper.writableDatabase.version)
        } finally {
            database.close()
        }
    }

    private fun withDb(version: Int, block: (SupportSQLiteDatabase) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        try {
            block(helper.writableDatabase)
        } finally {
            helper.close()
        }
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> {
        val result = mutableSetOf<String>()
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val index = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) result += cursor.getString(index)
        }
        return result
    }

    private fun assertCoreSentinels(db: SupportSQLiteDatabase) {
        assertTrue(columns(db, "bank_accounts").contains("accountId"))
        assertTrue(columns(db, "bank_transactions").contains("transactionId"))
        assertTrue(columns(db, "bank_receipt_links").contains("linkId"))
    }
}
