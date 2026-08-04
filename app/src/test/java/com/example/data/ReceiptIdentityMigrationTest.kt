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
class ReceiptIdentityMigrationTest {
    @Test
    fun migrationPreservesExistingDuplicatesAndAddsLookupIndexes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE receipts (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "internalId TEXT NOT NULL, " +
                                "driveFileId TEXT, " +
                                "driveMetadataFileId TEXT)"
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
            db.execSQL(
                "INSERT INTO receipts(internalId, driveFileId, driveMetadataFileId) " +
                    "VALUES ('duplicate', 'main', 'meta-1')"
            )
            db.execSQL(
                "INSERT INTO receipts(internalId, driveFileId, driveMetadataFileId) " +
                    "VALUES ('duplicate', 'main', 'meta-2')"
            )

            MIGRATION_12_13.migrate(db)

            db.query("SELECT COUNT(*) FROM receipts WHERE internalId = 'duplicate'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
            }

            val indexes = mutableMapOf<String, Int>()
            db.query("PRAGMA index_list('receipts')").use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                val uniqueColumn = cursor.getColumnIndexOrThrow("unique")
                while (cursor.moveToNext()) {
                    indexes[cursor.getString(nameColumn)] = cursor.getInt(uniqueColumn)
                }
            }
            assertEquals(0, indexes["index_receipts_internalId"])
            assertEquals(0, indexes["index_receipts_driveFileId"])
            assertEquals(0, indexes["index_receipts_driveMetadataFileId"])
        }
    }
}
