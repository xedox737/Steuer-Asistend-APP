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
class PropertyAssignmentMigrationTest {
    @Test fun migrationKeepsLegacyReceiptAndLoanAndAddsStablePropertyDefaults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(20) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        helper.use {
            val db = it.writableDatabase
            db.execSQL("CREATE TABLE receipts (id INTEGER NOT NULL PRIMARY KEY, aussteller TEXT NOT NULL)")
            db.execSQL("CREATE TABLE loans (id INTEGER NOT NULL PRIMARY KEY, bezeichnung TEXT NOT NULL)")
            db.execSQL("INSERT INTO receipts VALUES (7, 'Altbeleg')")
            db.execSQL("INSERT INTO loans VALUES (9, 'Altdarlehen')")

            MIGRATION_20_21.migrate(db)

            db.query("SELECT aussteller, propertyId FROM receipts WHERE id=7").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Altbeleg", cursor.getString(0))
                assertEquals(StableDocumentIdentity.LEGACY_PROPERTY_ID, cursor.getString(1))
            }
            db.query("SELECT bezeichnung, propertyId FROM loans WHERE id=9").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Altdarlehen", cursor.getString(0))
                assertEquals(StableDocumentIdentity.LEGACY_PROPERTY_ID, cursor.getString(1))
            }
        }
    }
}
