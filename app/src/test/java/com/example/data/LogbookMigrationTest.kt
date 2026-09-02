package com.example.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogbookMigrationTest {
    @Test fun migration18To19KeepsTripAndAddsSafeDefaults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("logbook-migration")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name("logbook-migration")
                .callback(object : SupportSQLiteOpenHelper.Callback(18) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        val db = helper.writableDatabase
        db.execSQL("""
            CREATE TABLE logbook_trips (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, time TEXT NOT NULL,
                purpose TEXT NOT NULL, propertyReference TEXT NOT NULL, startAddress TEXT NOT NULL,
                destinationAddress TEXT NOT NULL, stopsJson TEXT NOT NULL, routeMode TEXT NOT NULL,
                sameReturnRoute INTEGER NOT NULL, taxDistanceKm REAL NOT NULL, kilometerSource TEXT NOT NULL,
                aiEstimatedKm REAL, routedKm REAL, gpsMeasuredKm REAL, odometerStartKm REAL,
                odometerEndKm REAL, standardRouteId INTEGER, plausibilityStatus TEXT NOT NULL,
                manuallyConfirmed INTEGER NOT NULL, sourceReceiptId INTEGER, expenseReceiptId INTEGER,
                note TEXT NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE standard_routes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, startAddress TEXT NOT NULL,
                destinationAddress TEXT NOT NULL, stopsJson TEXT NOT NULL, routeMode TEXT NOT NULL,
                sameReturnRoute INTEGER NOT NULL, distanceKm REAL NOT NULL, active INTEGER NOT NULL,
                createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO logbook_trips VALUES (
                5,'2026-01-01','','Alt','Objekt','A','B','','EINFACH',0,12.4,'MANUELL',
                NULL,NULL,NULL,NULL,NULL,NULL,'PRUEFEN',1,NULL,NULL,'alt','now','now'
            )
        """.trimIndent())

        MIGRATION_18_19.migrate(db)
        db.query("SELECT id, taxDistanceKm, routeProvider, correctionReason, routeSignature FROM logbook_trips WHERE id=5").use { cursor ->
            cursor.moveToFirst()
            assertEquals(5L, cursor.getLong(0))
            assertEquals(12.4, cursor.getDouble(1), 0.001)
            assertEquals("", cursor.getString(2))
            assertEquals("", cursor.getString(3))
            assertEquals("", cursor.getString(4))
        }
        helper.close()
        context.deleteDatabase("logbook-migration")
    }
}
