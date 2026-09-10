package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankTransactionSplitMigrationBackupTest {
    @Test fun migration27To28AddsNoteWithSafeDefault() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(27) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE bank_rent_assignments (assignmentId TEXT NOT NULL PRIMARY KEY, transactionId TEXT NOT NULL, propertyId TEXT NOT NULL, unitId TEXT NOT NULL, rentMonth TEXT NOT NULL, tenantReference TEXT NOT NULL, allocatedAmount REAL NOT NULL, paymentType TEXT NOT NULL, status TEXT NOT NULL, source TEXT NOT NULL, receiptId INTEGER, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        MIGRATION_27_28.migrate(db)
        db.execSQL("INSERT INTO bank_rent_assignments VALUES ('a','t','p','u','2026-09','tenant',10.0,'MIETE','CONFIRMED','MANUAL',NULL,'c','u','')")
        db.query("SELECT note FROM bank_rent_assignments WHERE assignmentId='a'").use { cursor ->
            assertTrue(cursor.moveToFirst()); assertEquals("", cursor.getString(0))
        }
        helper.close()
    }

    @Test fun supplementalBackupRoundTripsManualNote() = runTest {
        assertEquals(11, SupplementalDriveBackup.SCHEMA_VERSION)
        val context: Context = ApplicationProvider.getApplicationContext()
        val source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        source.bankRentAssignmentDao().upsert(BankRentAssignment("a","t","p","u","2026-09","tenant",10.0,"KAUTION",createdAt="c",updatedAt="u",note="Synthetic note"))
        val payload: JSONObject = SupplementalDriveBackup.createPayload(context, source)
        val target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        SupplementalDriveBackup.restorePayload(context, target, payload)
        assertEquals("Synthetic note", target.bankRentAssignmentDao().getById("a")!!.note)
        source.close(); target.close()
    }
}
