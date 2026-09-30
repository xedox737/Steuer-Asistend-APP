package com.example.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LogbookReliabilityMigrationTest {
    @Test fun migrationPreservesExistingTripsAndRecoversTheirProperty() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext<Context>()).name(null).callback(object : SupportSQLiteOpenHelper.Callback(32) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        helper.use {
            val db = helper.writableDatabase
            db.execSQL("CREATE TABLE receipts (id INTEGER PRIMARY KEY, propertyId TEXT NOT NULL)")
            db.execSQL("CREATE TABLE logbook_trips (id INTEGER PRIMARY KEY, sourceReceiptId INTEGER, expenseReceiptId INTEGER, taxDistanceKm REAL NOT NULL)")
            db.execSQL("INSERT INTO receipts VALUES(5,'other')")
            db.execSQL("INSERT INTO logbook_trips VALUES(7,5,NULL,12.4)")
            MIGRATION_32_33.migrate(db)
            db.query("SELECT taxDistanceKm,propertyId,bookingKey,cancelledAt,historyJson FROM logbook_trips").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(12.4, c.getDouble(0), .001); assertEquals("other", c.getString(1))
                assertEquals("", c.getString(2)); assertEquals("", c.getString(3)); assertEquals("[]", c.getString(4))
            }
        }
    }
}
