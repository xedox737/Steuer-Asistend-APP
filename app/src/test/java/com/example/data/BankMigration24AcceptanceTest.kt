package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankMigration24AcceptanceTest {
    @Test fun migration21To25PreservesCoreTablesAndCreatesBankAndRuleSchema() = withDb(21) { db ->
        createCoreSentinels(db)
        MIGRATION_21_22.migrate(db)
        MIGRATION_22_23.migrate(db)
        MIGRATION_23_24.migrate(db)
        MIGRATION_24_25.migrate(db)
        assertCoreSentinels(db)
        assertTrue(columns(db, "bank_transactions").containsAll(setOf("propertyId", "unitId", "importFileName", "importRunId", "updatedAt")))
        assertTrue(columns(db, "bank_learning_rules").containsAll(setOf("ruleId", "enabled", "state", "confidence", "accountId", "propertyId", "unitId")))
        assertTrue(columns(db, "bank_rule_evidence").containsAll(setOf("evidenceId", "candidateKey", "transactionId", "receiptId")))
    }

    @Test fun migration22To25PreservesCoreAndBankRows() = withDb(22) { db ->
        createCoreSentinels(db)
        createBank22(db)
        db.execSQL("INSERT INTO bank_accounts VALUES ('a','Haus','Sparkasse','DE1','EUR','CSV',1,'c','u')")
        db.execSQL("INSERT INTO bank_transactions VALUES ('t','a','2026-09-04','',-84.5,'EUR','Hornbach','','Material','R','CSV','OPEN','','import')")
        db.execSQL("INSERT INTO bank_receipt_links VALUES ('l','t',1,'r-1',84.5,'CONFIRMED','created')")
        MIGRATION_22_23.migrate(db)
        MIGRATION_23_24.migrate(db)
        MIGRATION_24_25.migrate(db)
        assertCoreSentinels(db)
        db.query("SELECT bankName FROM bank_accounts WHERE accountId='a'").use {
            assertTrue(it.moveToFirst()); assertEquals("Sparkasse", it.getString(0))
        }
        db.query("SELECT counterparty, updatedAt FROM bank_transactions WHERE transactionId='t'").use {
            assertTrue(it.moveToFirst()); assertEquals("Hornbach", it.getString(0)); assertEquals("", it.getString(1))
        }
        db.query("SELECT source FROM bank_receipt_links WHERE linkId='l'").use {
            assertTrue(it.moveToFirst()); assertEquals(BankLinkSource.NUTZER_BESTAETIGT, it.getString(0))
        }
    }

    @Test fun migration23To25IsAdditiveAndPreservesBankRows() = withDb(23) { db ->
        createCoreSentinels(db)
        createBank23(db)
        db.execSQL("INSERT INTO bank_accounts VALUES ('a','Haus','Sparkasse','DE1','EUR','CSV',1,'c','u','Sergej')")
        db.execSQL("INSERT INTO bank_transactions VALUES ('t','a','2026-09-04','',-84.5,'EUR','Hornbach','','Material','R','CSV','OPEN','','import','p','u','konto.csv','run')")
        db.execSQL("INSERT INTO bank_receipt_links VALUES ('l','t',1,'r-1',84.5,'CONFIRMED','created','MANUELL')")
        MIGRATION_23_24.migrate(db)
        MIGRATION_24_25.migrate(db)
        assertCoreSentinels(db)
        db.query("SELECT propertyId, importFileName, updatedAt FROM bank_transactions WHERE transactionId='t'").use {
            assertTrue(it.moveToFirst()); assertEquals("p", it.getString(0)); assertEquals("konto.csv", it.getString(1)); assertEquals("", it.getString(2))
        }
        db.query("SELECT source FROM bank_receipt_links WHERE linkId='l'").use {
            assertTrue(it.moveToFirst()); assertEquals("MANUELL", it.getString(0))
        }
    }

    @Test fun migration24To25PreservesCoreAndBankRowsAndCreatesLearningTables() = withDb(24) { db ->
        createCoreSentinels(db)
        createBank24(db)
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

    private fun createCoreSentinels(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE receipts (id INTEGER NOT NULL PRIMARY KEY, marker TEXT NOT NULL)")
        db.execSQL("CREATE TABLE property_metadata (id INTEGER NOT NULL PRIMARY KEY, marker TEXT NOT NULL)")
        db.execSQL("CREATE TABLE loans (id INTEGER NOT NULL PRIMARY KEY, marker TEXT NOT NULL)")
        db.execSQL("CREATE TABLE managed_documents (documentId TEXT NOT NULL PRIMARY KEY, marker TEXT NOT NULL)")
        db.execSQL("INSERT INTO receipts VALUES (1,'receipt')")
        db.execSQL("INSERT INTO property_metadata VALUES (1,'property')")
        db.execSQL("INSERT INTO loans VALUES (1,'loan')")
        db.execSQL("INSERT INTO managed_documents VALUES ('d','document')")
    }

    private fun assertCoreSentinels(db: SupportSQLiteDatabase) {
        listOf("receipts" to "receipt", "property_metadata" to "property", "loans" to "loan", "managed_documents" to "document").forEach { (table, expected) ->
            db.query("SELECT marker FROM $table LIMIT 1").use { assertTrue(it.moveToFirst()); assertEquals(expected, it.getString(0)) }
        }
    }

    private fun createBank22(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE bank_accounts (accountId TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, bankName TEXT NOT NULL, iban TEXT NOT NULL, currency TEXT NOT NULL, source TEXT NOT NULL, active INTEGER NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL)")
        db.execSQL("CREATE TABLE bank_transactions (transactionId TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, bookingDate TEXT NOT NULL, valueDate TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, counterparty TEXT NOT NULL, counterpartyIban TEXT NOT NULL, purpose TEXT NOT NULL, bankReference TEXT NOT NULL, source TEXT NOT NULL, reconciliationStatus TEXT NOT NULL, noReceiptReason TEXT NOT NULL, importedAt TEXT NOT NULL)")
        db.execSQL("CREATE TABLE bank_receipt_links (linkId TEXT NOT NULL PRIMARY KEY, transactionId TEXT NOT NULL, receiptId INTEGER NOT NULL, receiptInternalId TEXT NOT NULL, allocatedAmount REAL NOT NULL, status TEXT NOT NULL, createdAt TEXT NOT NULL)")
    }

    private fun createBank23(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE bank_accounts (accountId TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, bankName TEXT NOT NULL, iban TEXT NOT NULL, currency TEXT NOT NULL, source TEXT NOT NULL, active INTEGER NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, accountHolder TEXT NOT NULL)")
        db.execSQL("CREATE TABLE bank_transactions (transactionId TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, bookingDate TEXT NOT NULL, valueDate TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, counterparty TEXT NOT NULL, counterpartyIban TEXT NOT NULL, purpose TEXT NOT NULL, bankReference TEXT NOT NULL, source TEXT NOT NULL, reconciliationStatus TEXT NOT NULL, noReceiptReason TEXT NOT NULL, importedAt TEXT NOT NULL, propertyId TEXT NOT NULL, unitId TEXT NOT NULL, importFileName TEXT NOT NULL, importRunId TEXT NOT NULL)")
        db.execSQL("CREATE TABLE bank_receipt_links (linkId TEXT NOT NULL PRIMARY KEY, transactionId TEXT NOT NULL, receiptId INTEGER NOT NULL, receiptInternalId TEXT NOT NULL, allocatedAmount REAL NOT NULL, status TEXT NOT NULL, createdAt TEXT NOT NULL, source TEXT NOT NULL)")
    }

    private fun createBank24(db: SupportSQLiteDatabase) {
        createBank23(db)
        db.execSQL("ALTER TABLE bank_transactions ADD COLUMN updatedAt TEXT NOT NULL DEFAULT ''")
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> {
        val result = mutableSetOf<String>()
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) result += cursor.getString(nameIndex)
        }
        return result
    }
}
