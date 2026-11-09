package com.example.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.os.CancellationSignal
import android.os.OperationCanceledException
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SQL boundary tests, not a claim that preference/file/sync coverage is complete. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineBackupCoordinatorTest {
    private lateinit var coordinator: OfflineBackupCoordinator
    private lateinit var database: AppDatabase
    private lateinit var sql: SupportSQLiteDatabase
    private val receipt = Receipt(id = 7, internalId = "seed", displayId = "KEEP",
        aussteller = "Original", datum = "2026-10-08", uhrzeit = "", bruttobetrag = 100.0,
        hauptkategorie = "Renovierung", unterkategorie = "Material", kontoNr = "4800", beschreibung = "")

    @Before fun setUp() = runBlocking<Unit> {
        coordinator = OfflineBackupCoordinator()
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .openHelperFactory(coordinator.sqliteFactory()).allowMainThreadQueries().build()
        sql = database.openHelper.writableDatabase
        database.receiptDao().insertReceipt(receipt)
    }

    @After fun tearDown() { database.close() }

    private fun quietCapture() { coordinator.completeCapture(coordinator.beginCapture()) }

    @Test fun readonlyRoomTransactionDoesNotInvalidateCapture() = runBlocking {
        val capture = coordinator.beginCapture()
        database.withTransaction {
            assertEquals(receipt, database.receiptDao().getReceiptById(7))
            sql.query("PRAGMA table_info(receipts)").use { assertTrue(it.moveToFirst()) }
            sql.compileStatement("SELECT COUNT(*) FROM receipts").use { assertEquals(1L, it.simpleQueryForLong()) }
            listOf(null, CancellationSignal()).forEach { signal ->
                sql.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM receipts"), signal).use {
                    assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
                }
            }
            val cancelled = CancellationSignal().apply { cancel() }
            assertThrows(OperationCanceledException::class.java) {
                sql.query(SimpleSQLiteQuery("SELECT COUNT(*) FROM receipts"), cancelled).use { it.moveToFirst() }
            }
        }
        coordinator.completeCapture(capture)
    }

    @Test fun actualDaoDirectAndCompiledWritesInvalidateCaptureAndPreserveReturnValues() = runBlocking {
        suspend fun changed(action: suspend () -> Unit) {
            val capture = coordinator.beginCapture()
            action()
            assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(capture) }
            quietCapture()
        }
        changed { assertEquals(8L, database.receiptDao().insertReceipt(receipt.copy(id = 8, internalId = "second"))) }
        changed { database.propertyDao().insertPropertyMetadata(PropertyMetadata(propertyId = "property", name = "Objekt")) }
        changed { sql.execSQL("UPDATE receipts SET aussteller = ? WHERE id = ?", arrayOf("Direkt", 7)) }
        changed {
            sql.compileStatement("UPDATE receipts SET aussteller = ? WHERE id = ?").use {
                it.bindString(1, "Statement"); it.bindLong(2, 7); assertEquals(1, it.executeUpdateDelete())
            }
        }
        changed { assertEquals(1, sql.update("receipts", 0, ContentValues().apply { put("aussteller", "Update") }, "id = ?", arrayOf(7))) }
        changed { assertEquals(1, sql.delete("receipts", "id = ?", arrayOf(8))) }
        assertEquals("Update", database.receiptDao().getReceiptById(7)!!.aussteller)
        assertNull(database.receiptDao().getReceiptById(8))
    }

    @Test fun nestedTransactionKeepsWriterActiveUntilOuterRollback() {
        val capture = coordinator.beginCapture()
        sql.beginTransaction()
        try {
            sql.execSQL("UPDATE receipts SET aussteller = 'Outer' WHERE id = 7")
            sql.beginTransaction()
            try {
                sql.execSQL("UPDATE receipts SET aussteller = 'Inner' WHERE id = 7")
                sql.setTransactionSuccessful()
            } finally { sql.endTransaction() }
            assertThrows(OfflineBackupValidationException::class.java) { coordinator.beginCapture() }
            // The outer transaction intentionally rolls back.
        } finally { sql.endTransaction() }
        sql.query("SELECT aussteller FROM receipts WHERE id = 7").use {
            assertTrue(it.moveToFirst()); assertEquals("Original", it.getString(0))
        }
        quietCapture()
        assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(capture) }
    }

    @Test fun failedStatementReleasesOperationButKeepsOpenTransactionRegistered() {
        val capture = coordinator.beginCapture()
        sql.beginTransaction()
        try {
            assertThrows(SQLException::class.java) { sql.execSQL("UPDATE missing_table SET value = 1") }
            assertThrows(OfflineBackupValidationException::class.java) { coordinator.beginCapture() }
        } finally { sql.endTransaction() }
        quietCapture()
        assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(capture) }
    }

    @Test fun unclassifiedCursorKeepsRegistrationThroughConsumptionAndClose() {
        val capture = coordinator.beginCapture()
        sql.query("WITH values_to_read AS (SELECT id FROM receipts) SELECT id FROM values_to_read").use {
            assertThrows(OfflineBackupValidationException::class.java) { coordinator.beginCapture() }
            assertTrue(it.moveToFirst()); assertEquals(7, it.getInt(0))
        }
        quietCapture()
        assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(capture) }
    }

    @Test fun suspendedLogicalWriterAndDoubleCloseCannotExposeAnIdleGap() = runBlocking<Unit> {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writer = launch(Dispatchers.IO) {
            val lease = coordinator.beginWrite()
            try { started.complete(Unit); release.await() } finally { lease.close(); lease.close() }
        }
        try {
            started.await()
            val nested = coordinator.beginWrite()
            nested.close()
            assertThrows(OfflineBackupValidationException::class.java) { coordinator.beginCapture() }
        } finally { release.complete(Unit); writer.join() }
        quietCapture()
        val capture = coordinator.beginCapture()
        assertThrows(IllegalStateException::class.java) { coordinator.writing { error("synthetic failure") } }
        quietCapture()
        assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(capture) }
    }

    @Test fun startupRepairPreservesPrimaryAndExistingDisplayIdsAndReleasesRegistration() = runBlocking {
        sql.execSQL("UPDATE receipts SET internalId = '' WHERE id = 7")
        val parent = Job()
        val scope = CoroutineScope(parent + Dispatchers.IO)
        try {
            AppDatabase.Companion.AppDatabaseCallback(scope, coordinator).onOpen(sql)
            coordinator.awaitInitialization()
            val restored = database.receiptDao().getReceiptById(7)!!
            assertEquals(7, restored.id)
            assertEquals("KEEP", restored.displayId)
            assertTrue(restored.internalId.isNotBlank())
            quietCapture()
        } finally { scope.cancel() }
    }

    @Test fun cancelledStartupRefusesCaptureAfterItsJobHasFinished() = runBlocking {
        val parent = Job().apply { cancel() }
        val scope = CoroutineScope(parent + Dispatchers.IO)
        AppDatabase.Companion.AppDatabaseCallback(scope, coordinator).onOpen(sql)
        coordinator.awaitInitialization()
        val error = assertThrows(OfflineBackupValidationException::class.java) { coordinator.beginCapture() }
        assertTrue(error.message.orEmpty().contains("initialisierung"))
    }

    @Test fun failedStartupRefusesCaptureEvenWhenExistingCallbackCatchesTheSqlError() = runBlocking {
        val parent = Job()
        val scope = CoroutineScope(parent + Dispatchers.IO)
        val broken = object : SupportSQLiteDatabase by sql {
            override fun query(query: String): Cursor = throw SQLException("synthetic startup failure")
        }
        try {
            AppDatabase.Companion.AppDatabaseCallback(scope, coordinator).onOpen(broken)
            coordinator.awaitInitialization()
            val error = assertThrows(OfflineBackupValidationException::class.java) { coordinator.beginCapture() }
            assertTrue(error.message.orEmpty().contains("initialisierung"))
        } finally { scope.cancel() }
    }

    @Test fun captureFromAnotherCoordinatorIsRejected() {
        val first = OfflineBackupCoordinator()
        val second = OfflineBackupCoordinator()
        assertThrows(OfflineBackupValidationException::class.java) {
            second.completeCapture(first.beginCapture())
        }
    }

    @Test fun productionDatabaseBuilderTracksItsActualDaoMutations() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val instance = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
        val previous = instance.get(null)
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        var production: AppDatabase? = null
        try {
            // Robolectric's isolated application database, never a device/user database.
            instance.set(null, null)
            val opened = AppDatabase.getDatabase(context, scope)
            production = opened
            withContext(Dispatchers.IO) { opened.openHelper.writableDatabase }
            OfflineBackupCoordinator.shared.awaitInitialization()
            val capture = OfflineBackupCoordinator.shared.beginCapture()
            opened.receiptDao().insertReceipt(receipt)
            assertThrows(OfflineBackupValidationException::class.java) {
                OfflineBackupCoordinator.shared.completeCapture(capture)
            }
        } finally {
            production?.close()
            scope.cancel()
            instance.set(null, previous)
            context.deleteDatabase("receipt_database")
        }
    }

    @Test fun factoryPreservesStorageFlagsCallbacksMigrationAndReopening() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "snapshot-sql-probe-${UUID.randomUUID()}"
        val file = File(context.noBackupFilesDir, name)
        val events = mutableListOf<String>()
        fun helper(version: Int): SupportSQLiteOpenHelper = coordinator.sqliteFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .noBackupDirectory(true).allowDataLossOnRecovery(false)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onConfigure(db: SupportSQLiteDatabase) { events += "configure" }
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        events += "create"
                        db.execSQL("CREATE TABLE evidence(id INTEGER PRIMARY KEY, value TEXT NOT NULL)")
                        db.execSQL("INSERT INTO evidence VALUES(1, 'preserved')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        events += "upgrade:$oldVersion:$newVersion"
                        db.execSQL("ALTER TABLE evidence ADD COLUMN versioned INTEGER NOT NULL DEFAULT 2")
                    }
                    override fun onOpen(db: SupportSQLiteDatabase) { events += "open" }
                }).build()
        )
        val first = helper(1)
        var second: SupportSQLiteOpenHelper? = null
        try {
            assertEquals(file.canonicalFile, File(first.writableDatabase.path!!).canonicalFile)
            assertEquals(listOf("configure", "create", "open"), events)
            first.close()
            events.clear()
            val upgraded = helper(2)
            second = upgraded
            upgraded.writableDatabase.query("SELECT value, versioned FROM evidence WHERE id = 1").use {
                assertTrue(it.moveToFirst()); assertEquals("preserved", it.getString(0)); assertEquals(2, it.getInt(1))
            }
            // FrameworkSQLiteOpenHelper 2.5.0 reopens after migration to avoid stale schema.
            assertEquals(listOf("configure", "upgrade:1:2", "configure", "open"), events)
            quietCapture()
            upgraded.close()
            upgraded.writableDatabase.query("SELECT COUNT(*) FROM evidence").use {
                assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
            }
            quietCapture()
        } finally {
            first.close()
            second?.close()
            file.delete()
        }
    }

    @Test fun existingV1ExportCanReadTrackedRoomWithoutRegisteringBusinessWrites() = runBlocking {
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(
            propertyId = StableDocumentIdentity.LEGACY_PROPERTY_ID, name = "Legacy"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = OfflineBackupService(context, database)
        val output = java.io.ByteArrayOutputStream()
        val capture = coordinator.beginCapture()
        service.create(output)
        service.inspect(output.toByteArray().inputStream()).use {
            assertEquals(1, it.receiptCount)
            assertEquals(1, it.propertyCount)
            assertEquals(0, it.fileCount)
        }
        coordinator.completeCapture(capture)
        assertEquals(receipt, database.receiptDao().getReceiptById(7))
    }
}
