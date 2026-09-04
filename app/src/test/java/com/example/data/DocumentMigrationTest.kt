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
class DocumentMigrationTest {
    @Test fun migrationAddsStablePropertyIdentityAndPreservesExistingRow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE property_metadata (id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
                        db.execSQL("INSERT INTO property_metadata(id, name) VALUES (1, 'Bestandsobjekt')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        helper.use {
            val db = it.writableDatabase
            MIGRATION_19_20.migrate(db)
            db.query("SELECT name, propertyId FROM property_metadata WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Bestandsobjekt", cursor.getString(0))
                assertEquals(StableDocumentIdentity.LEGACY_PROPERTY_ID, cursor.getString(1))
            }
            db.query("SELECT COUNT(*) FROM managed_documents").use { cursor ->
                assertTrue(cursor.moveToFirst()); assertEquals(0, cursor.getInt(0))
            }
        }
    }
}
