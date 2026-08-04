package com.example.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountingApprovalMigrationTest {
    @Test
    fun migration13To14PreservesRowsAndStartsEveryLegacyReceiptUnapproved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(13) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE receipts (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "internalId TEXT NOT NULL)"
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )

        helper.use {
            val db = it.writableDatabase
            db.execSQL("INSERT INTO receipts(internalId) VALUES ('duplicate')")
            db.execSQL("INSERT INTO receipts(internalId) VALUES ('duplicate')")

            MIGRATION_13_14.migrate(db)

            db.query(
                "SELECT internalId, allocationsJson, bookingProposalsJson, freigabestatus " +
                    "FROM receipts ORDER BY id"
            ).use { cursor ->
                assertEquals(2, cursor.count)
                while (cursor.moveToNext()) {
                    assertEquals("duplicate", cursor.getString(0))
                    assertEquals("", cursor.getString(1))
                    assertEquals("", cursor.getString(2))
                    assertEquals("OFFEN", cursor.getString(3))
                }
            }

            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info('receipts')").use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) columns += cursor.getString(nameColumn)
            }
            assertTrue(columns.containsAll(
                listOf("allocationsJson", "bookingProposalsJson", "freigabestatus")
            ))
        }
    }
}
