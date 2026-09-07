package com.example.data

import android.content.Context
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
class BankMigrationTest {
    @Test
    fun migration22To23KeepsBankRowsAndAddsCorrectionFields() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(22) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        helper.use {
            val db = it.writableDatabase
            createVersion22BankTables(db)
            db.execSQL("INSERT INTO bank_accounts VALUES ('a','Hauskonto','Sparkasse','DE1','EUR','CSV',1,'c','u')")
            db.execSQL("INSERT INTO bank_transactions VALUES ('t','a','2026-09-04','',-84.5,'EUR','Hornbach','','Material','R1','CSV','OPEN','','now')")
            db.execSQL("INSERT INTO bank_receipt_links VALUES ('l','t',7,'receipt-7',84.5,'CONFIRMED','now')")

            MIGRATION_22_23.migrate(db)

            db.query("SELECT displayName, accountHolder FROM bank_accounts WHERE accountId='a'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Hauskonto", cursor.getString(0))
                assertEquals("", cursor.getString(1))
            }
            db.query("SELECT counterparty, propertyId, unitId, importFileName, importRunId FROM bank_transactions WHERE transactionId='t'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Hornbach", cursor.getString(0))
                assertEquals("", cursor.getString(1))
                assertEquals("", cursor.getString(2))
                assertEquals("", cursor.getString(3))
                assertEquals("", cursor.getString(4))
            }
            db.query("SELECT receiptId, source FROM bank_receipt_links WHERE linkId='l'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(7, cursor.getInt(0))
                assertEquals(BankLinkSource.NUTZER_BESTAETIGT, cursor.getString(1))
            }
        }
    }

    @Test
    fun migration21To22To23CreatesCompleteBankSchemaWithoutTouchingReceipt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(21) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        helper.use {
            val db = it.writableDatabase
            db.execSQL("CREATE TABLE receipts (id INTEGER NOT NULL PRIMARY KEY, aussteller TEXT NOT NULL)")
            db.execSQL("INSERT INTO receipts VALUES (42, 'Bestandsbeleg')")

            MIGRATION_21_22.migrate(db)
            MIGRATION_22_23.migrate(db)

            db.query("SELECT aussteller FROM receipts WHERE id=42").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Bestandsbeleg", cursor.getString(0))
            }
            db.query("PRAGMA table_info(bank_transactions)").use { cursor ->
                val columns = mutableSetOf<String>()
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) columns += cursor.getString(nameIndex)
                assertTrue(columns.containsAll(setOf("propertyId", "unitId", "importFileName", "importRunId")))
            }
        }
    }

    private fun createVersion22BankTables(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE bank_accounts (
                accountId TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, bankName TEXT NOT NULL,
                iban TEXT NOT NULL, currency TEXT NOT NULL, source TEXT NOT NULL, active INTEGER NOT NULL,
                createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE bank_transactions (
                transactionId TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, bookingDate TEXT NOT NULL,
                valueDate TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, counterparty TEXT NOT NULL,
                counterpartyIban TEXT NOT NULL, purpose TEXT NOT NULL, bankReference TEXT NOT NULL, source TEXT NOT NULL,
                reconciliationStatus TEXT NOT NULL, noReceiptReason TEXT NOT NULL, importedAt TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE bank_receipt_links (
                linkId TEXT NOT NULL PRIMARY KEY, transactionId TEXT NOT NULL, receiptId INTEGER NOT NULL,
                receiptInternalId TEXT NOT NULL, allocatedAmount REAL NOT NULL, status TEXT NOT NULL, createdAt TEXT NOT NULL
            )
        """.trimIndent())
    }
}
