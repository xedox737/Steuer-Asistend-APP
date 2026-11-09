package com.example.data

import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
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
import org.json.JSONObject
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

    @Test fun preferenceRestoreKeepsOneRegistrationAcrossStores() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shared = OfflineBackupCoordinator.shared
        val rent = context.getSharedPreferences("rent_plan_prefs", 0)
        val notes = context.getSharedPreferences("bank_transaction_notes", 0)
        rent.edit().clear().commit(); notes.edit().clear().commit()
        val payload = JSONObject()
            .put("rentPlanPrefs", JSONObject().put("v2_P_U_nk", JSONObject().put("type", "float").put("value", 80.0)))
            .put("bankTransactionNotes", JSONObject().put("T", JSONObject().put("type", "string").put("value", "restored")))
        val observed = mutableListOf<Boolean>()
        val probe = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val result = super.getSharedPreferences(name, mode)
                if (name == "bank_transaction_notes" && rent.getFloat("v2_P_U_nk", 0f) == 80f) {
                    observed += runCatching { shared.beginCapture() }.exceptionOrNull() is OfflineBackupValidationException
                }
                return result
            }
        }
        val before = shared.beginCapture()
        PersistentPreferenceInventory.restorePayload(probe, payload, RestoreMode.REPLACE_FULL)
        assertEquals(listOf(true), observed)
        assertEquals(80f, rent.getFloat("v2_P_U_nk", 0f), 0f)
        assertEquals("restored", notes.getString("T", null))
        assertThrows(OfflineBackupValidationException::class.java) { shared.completeCapture(before) }
        shared.completeCapture(shared.beginCapture())
    }

    @Test fun supplementalRestoreIsRegisteredBeforePropertyImagePublication() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(propertyId = StableDocumentIdentity.LEGACY_PROPERTY_ID, name = "Legacy"))
        val bytes = "synthetic property image".toByteArray()
        val photo = File.createTempFile("coordinator-property-", ".jpg", context.cacheDir).apply { writeBytes(bytes) }
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 101, propertyId = "photo-owner", name = "Photo", bildPfad = photo.absolutePath))
        val payload = SupplementalDriveBackup.createPayload(context, database)
        val shared = OfflineBackupCoordinator.shared
        var observing = false
        val observed = mutableListOf<Boolean>()
        val probe = object : ContextWrapper(context) {
            override fun getFilesDir(): File {
                if (observing) observed += runCatching { shared.beginCapture() }.exceptionOrNull() is OfflineBackupValidationException
                return context.filesDir
            }
        }
        try {
            observing = true
            try { SupplementalDriveBackup.restorePayload(probe, database, payload, replaceManagedDocuments = true) }
            finally { observing = false }
            assertEquals(listOf(true), observed)
            val restored = database.propertyDao().getPropertyByPropertyId("photo-owner")!!
            assertEquals(101, restored.id)
            assertNotEquals(photo.absolutePath, restored.bildPfad)
            assertArrayEquals(bytes, File(restored.bildPfad).readBytes())
            shared.completeCapture(shared.beginCapture())
        } finally { photo.delete() }
    }

    @Test fun localRestoreIsRegisteredBeforeOriginalStoragePublication() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(propertyId = StableDocumentIdentity.LEGACY_PROPERTY_ID, name = "Legacy"))
        val service = OfflineBackupService(context, database)
        val bytes = java.io.ByteArrayOutputStream().also { service.create(it) }.toByteArray()
        val shared = OfflineBackupCoordinator.shared
        var observing = false
        val observed = mutableListOf<Boolean>()
        val probe = object : ContextWrapper(context) {
            override fun getFilesDir(): File {
                if (observing) observed += runCatching { shared.beginCapture() }.exceptionOrNull() is OfflineBackupValidationException
                return context.filesDir
            }
        }
        val target = OfflineBackupService(probe, database)
        service.inspect(bytes.inputStream()).use { prepared ->
            observing = true
            try { target.restore(prepared) } finally { observing = false }
        }
        assertEquals(listOf(true), observed)
        assertEquals(receipt, database.receiptDao().getReceiptById(7))
        shared.completeCapture(shared.beginCapture())
    }

    @Test fun repositoryInsertKeepsRegistrationBetweenCommittedReceiptAndItsProjections() = runBlocking {
        val shared = OfflineBackupCoordinator.shared
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val delegate = database.receiptEntityDao()
        val projection = object : ReceiptEntityDao by delegate {
            override suspend fun insert(receipt: ReceiptEntity): Long {
                entered.complete(Unit)
                release.await()
                return delegate.insert(receipt)
            }
        }
        val repository = ReceiptRepository(database.receiptDao(), database.propertyDao(), projection,
            database.belegDao(), database.exportAuditDao(), database.receiptDocumentDao(), database.managedDocumentDao())
        val capture = shared.beginCapture()
        val updated = receipt.copy(aussteller = "Coordinated")
        val job = launch(Dispatchers.IO) { repository.insert(updated) }
        try {
            entered.await()
            assertEquals(updated, database.receiptDao().getReceiptById(7))
            assertNull(delegate.getEntityById(7))
            assertThrows(OfflineBackupValidationException::class.java) { shared.beginCapture() }
        } finally { release.complete(Unit); job.join() }
        assertEquals("Coordinated", delegate.getEntityById(7)!!.kreditor)
        sql.query("SELECT anbieter FROM belege WHERE id = 7").use {
            assertTrue(it.moveToFirst()); assertEquals("Coordinated", it.getString(0))
        }
        assertEquals("KEEP", repository.getReceiptById(7)!!.displayId)
        assertThrows(OfflineBackupValidationException::class.java) { shared.completeCapture(capture) }
        shared.completeCapture(shared.beginCapture())
    }

    @Test fun originalFilePublicationsInvalidateCaptureAndReleaseRegistrationAfterCompletion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shared = OfflineBackupCoordinator.shared
        val storage = ReceiptOriginalStorage(context)
        val bytes = "%PDF-1.4\nsynthetic original\n%%EOF".toByteArray()
        val source = File.createTempFile("coordinator-original-", ".pdf", context.cacheDir).apply { writeBytes(bytes) }
        val published = mutableListOf<File>()
        try {
            val downloadCapture = shared.beginCapture()
            val downloaded = storage.storeDownloadedVersion(bytes, "receipt.pdf").also { published += it }
            assertArrayEquals(bytes, downloaded.readBytes())
            assertThrows(OfflineBackupValidationException::class.java) { shared.completeCapture(downloadCapture) }
            shared.completeCapture(shared.beginCapture())
            val importCapture = shared.beginCapture()
            val imported = storage.importOriginal(android.net.Uri.fromFile(source), "Original.pdf")
            val original = File(imported.localUri).also { published += it }
            assertArrayEquals(bytes, original.readBytes())
            assertEquals(StableDocumentIdentity.sha256(bytes), imported.sha256)
            assertEquals(bytes.size.toLong(), imported.fileSizeBytes)
            assertThrows(OfflineBackupValidationException::class.java) { shared.completeCapture(importCapture) }
            shared.completeCapture(shared.beginCapture())
        } finally { source.delete(); published.forEach { it.delete() } }
    }

    @Test fun queuedMutationIsRegisteredBeforeEntryAndAcrossSuspension() = kotlinx.coroutines.test.runTest {
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val release = CompletableDeferred<Unit>()
        var entered = false
        val capture = coordinator.beginCapture()
        val job = coordinator.launch(this, dispatcher) {
            entered = true
            sql.execSQL("UPDATE receipts SET aussteller = 'first' WHERE id = 7")
            release.await()
            sql.execSQL("UPDATE receipts SET aussteller = 'complete' WHERE id = 7")
        }
        try {
            assertFalse(entered)
            assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(capture) }
            assertThrows(OfflineBackupValidationException::class.java) { coordinator.beginCapture() }
            testScheduler.runCurrent()
            assertTrue(entered)
            assertEquals("first", database.receiptDao().getReceiptById(7)!!.aussteller)
            assertThrows(OfflineBackupValidationException::class.java) { coordinator.beginCapture() }
            release.complete(Unit)
            testScheduler.runCurrent()
            job.join()
            assertEquals("complete", database.receiptDao().getReceiptById(7)!!.aussteller)
            quietCapture()
        } finally { job.cancel(); testScheduler.runCurrent() }
    }

    @Test fun cancellationBeforeEntryReleasesQueuedAndLazyMutations() = kotlinx.coroutines.test.runTest {
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        listOf(kotlinx.coroutines.CoroutineStart.DEFAULT, kotlinx.coroutines.CoroutineStart.LAZY).forEach { start ->
            var entered = false
            val capture = coordinator.beginCapture()
            val job = coordinator.launch(this, dispatcher, start) { entered = true }
            try {
                assertThrows(OfflineBackupValidationException::class.java) { coordinator.beginCapture() }
            } finally { job.cancel(); testScheduler.runCurrent(); job.join() }
            assertFalse(entered)
            assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(capture) }
            quietCapture()
        }
    }

    @Test fun mutationRegistrationSurvivesParentBodyUntilChildrenFinishAndReleasesOnCancellation() = kotlinx.coroutines.test.runTest {
        val dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val job = coordinator.launch(this, dispatcher) {
            launch { entered.complete(Unit); release.await() }
        }
        try {
            testScheduler.runCurrent()
            assertTrue(entered.isCompleted)
            assertFalse(job.isCompleted)
            assertThrows(OfflineBackupValidationException::class.java) { coordinator.beginCapture() }
        } finally { job.cancel(); testScheduler.runCurrent(); job.join() }
        quietCapture()
    }

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

    private fun rawPreferences(): SharedPreferences = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("coordinator-editor-${UUID.randomUUID()}", Context.MODE_PRIVATE)

    @Test fun preferenceEditorChainsPublishEveryTypeOnlyAtApplyOrCommit() {
        val raw = rawPreferences()
        raw.edit().putString("remove", "old").commit()
        val tracked = coordinator.trackPreferences(raw)
        val editor = tracked.edit()
        val capture = coordinator.beginCapture()
        assertSame(editor, editor.remove("remove").putString("string", "value").putInt("int", 7)
            .putLong("long", 16777217L).putFloat("float", 1.5f).putBoolean("boolean", true)
            .putStringSet("set", mutableSetOf("a", "b")))
        assertEquals(setOf("remove"), raw.all.keys)
        coordinator.completeCapture(capture)
        editor.apply()
        assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(capture) }
        assertEquals("value", raw.getString("string", null))
        assertEquals(7, raw.getInt("int", 0))
        assertEquals(16777217L, raw.getLong("long", 0))
        assertEquals(1.5f, raw.getFloat("float", 0f), 0f)
        assertTrue(raw.getBoolean("boolean", false))
        assertEquals(setOf("a", "b"), raw.getStringSet("set", null))
        assertFalse(raw.contains("remove"))
        val next = coordinator.beginCapture()
        val clearing = tracked.edit()
        assertSame(clearing, clearing.clear())
        assertTrue(clearing.commit())
        assertTrue(raw.all.isEmpty())
        assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(next) }
        quietCapture()
    }

    @Test fun preferenceReadsAndUnpublishedEditorsLeaveGenerationUnchanged() {
        val raw = rawPreferences()
        raw.edit().putString("existing", "value").commit()
        val capture = coordinator.beginCapture()
        val tracked = coordinator.trackPreferences(raw)
        assertSame(tracked, coordinator.trackPreferences(raw))
        assertEquals("value", tracked.getString("existing", null))
        assertTrue(tracked.contains("existing"))
        assertEquals(setOf("existing"), tracked.all.keys)
        tracked.edit().putString("unpublished", "pending")
        assertFalse(raw.contains("unpublished"))
        coordinator.completeCapture(capture)
    }

    @Test fun preferenceListenersReceiveTrackedIdentityAndCanBeUnregistered() {
        val raw = rawPreferences()
        val tracked = coordinator.trackPreferences(raw)
        var received: SharedPreferences? = null
        var calls = 0
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ -> received = prefs; calls++ }
        tracked.registerOnSharedPreferenceChangeListener(listener)
        coordinator.trackPreferences(raw).registerOnSharedPreferenceChangeListener(listener)
        tracked.edit().putString("first", "value").commit()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertSame(tracked, received)
        assertEquals(1, calls)
        val capture = coordinator.beginCapture()
        received!!.edit().putInt("throughListener", 1).apply()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, raw.getInt("throughListener", 0))
        assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(capture) }
        coordinator.trackPreferences(raw).unregisterOnSharedPreferenceChangeListener(listener)
        val before = calls
        tracked.edit().putInt("afterRemoval", 2).commit()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(before, calls)
    }

    @Test fun failedPreferenceCommitAndApplyPreserveResultsAndReleaseRegistration() {
        val raw = rawPreferences()
        val failing = object : SharedPreferences by raw {
            override fun edit(): SharedPreferences.Editor {
                val actual = raw.edit()
                return object : SharedPreferences.Editor by actual {
                    override fun commit(): Boolean { actual.apply(); return false }
                    override fun apply() { throw IllegalStateException("synthetic apply failure") }
                }
            }
        }
        val tracked = coordinator.trackPreferences(failing)
        val capture = coordinator.beginCapture()
        assertFalse(tracked.edit().putString("publishedInMemory", "value").commit())
        assertEquals("value", raw.getString("publishedInMemory", null))
        assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(capture) }
        quietCapture()
        val next = coordinator.beginCapture()
        assertThrows(IllegalStateException::class.java) { tracked.edit().putInt("failed", 1).apply() }
        assertFalse(raw.contains("failed"))
        assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(next) }
        quietCapture()
    }

    @Test fun inventoryRestoreRegistersBusinessWritesAndRejectsUnknownStoreNames() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val key = "history_synthetic-${UUID.randomUUID()}"
        val shared = OfflineBackupCoordinator.shared
        val capture = shared.beginCapture()
        val root = JSONObject().put("tenantHistoryPrefs", JSONObject().put(key,
            JSONObject().put("type", "string").put("value", "[]")))
        try {
            PersistentPreferenceInventory.restorePayload(context, root)
            assertEquals("[]", PersistentPreferenceInventory.open(context, "tenant_history_prefs").getString(key, null))
            assertThrows(OfflineBackupValidationException::class.java) { shared.completeCapture(capture) }
            assertThrows(IllegalArgumentException::class.java) { PersistentPreferenceInventory.open(context, "unclassified-store") }
            shared.completeCapture(shared.beginCapture())
        } finally { PersistentPreferenceInventory.open(context, "tenant_history_prefs").edit().remove(key).commit() }
    }

    @Test fun technicalPreferenceWritesRemainOutsideBusinessCapture() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shared = OfflineBackupCoordinator.shared
        val capture = shared.beginCapture()
        val technical = PersistentPreferenceInventory.open(context, "app_installation_prefs")
        try {
            assertTrue(technical.edit().putString("synthetic_marker", "technical").commit())
            shared.completeCapture(capture)
        } finally { technical.edit().remove("synthetic_marker").commit() }
    }

    @Test fun domainReadMigrationsRegisterWritesAndCanonicalReadsRemainQuiet() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shared = OfflineBackupCoordinator.shared
        val pid = StableDocumentIdentity.LEGACY_PROPERTY_ID
        val name = "migration-${UUID.randomUUID()}"
        val uid = "stable-$name"
        val units = context.getSharedPreferences("wohneinheiten_prefs", 0)
        units.edit().putString("unit_id_$name", uid).putString("unit_id_$name-rent", "$uid-rent").commit()
        val histories = context.getSharedPreferences("tenant_history_prefs", 0)
        val raw = org.json.JSONArray().put(JSONObject().put("id", 17).put("tenantName", "Alt")
            .put("startDate", "2020-01-01").put("endDate", "").put("kaltmiete", 600)
            .put("nebenkosten", 100).put("sonstige", 5)).toString()
        histories.edit().putString("history_$name", raw).commit()
        val capture = shared.beginCapture()
        val read = com.example.ui.TenantHistoryStore.read(context, pid, uid, name)
        assertFalse(read.damaged)
        assertEquals(17L, read.entries.single().id)
        assertEquals(raw, histories.getString("history_v2_${pid}_$uid", null))
        assertEquals(raw, histories.getString("history_$name", null))
        assertThrows(OfflineBackupValidationException::class.java) { shared.completeCapture(capture) }
        val quiet = shared.beginCapture()
        com.example.ui.TenantHistoryStore.read(context, pid, uid, name)
        shared.completeCapture(quiet)
        val rents = context.getSharedPreferences("rent_plan_prefs", 0)
        rents.edit().putFloat("nk_$name-rent", 125.25f).commit()
        val unit = com.example.ui.WohneinheitStatus("$name-rent", "", "Vermietet", "", 600.0, 50.0, unitId = "$uid-rent")
        val rentCapture = shared.beginCapture()
        assertEquals(125.25, com.example.ui.PropertyUnitScopedData.rentValue(context, pid, unit, "nk"), 0.0)
        assertEquals(125.25f, rents.getFloat("v2_${pid}_${uid}-rent_nk", 0f), 0f)
        assertThrows(OfflineBackupValidationException::class.java) { shared.completeCapture(rentCapture) }
        val rentQuiet = shared.beginCapture()
        com.example.ui.PropertyUnitScopedData.rentValue(context, pid, unit, "nk")
        shared.completeCapture(rentQuiet)
    }

    @Test fun domainMultiEditorUpdatesDoNotExposeIdleGap() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shared = OfflineBackupCoordinator.shared
        val pid = StableDocumentIdentity.LEGACY_PROPERTY_ID
        val name = "gap-${UUID.randomUUID()}"
        val uid = "stable-$name"
        val units = context.getSharedPreferences("wohneinheiten_prefs", 0)
        units.edit().putString("unit_id_$name", uid).commit()
        val histories = context.getSharedPreferences("tenant_history_prefs", 0)
        histories.edit().putString("history_v2_${pid}_$uid", "[]").commit()
        val blocked = mutableListOf<Boolean>()
        val observedUnits = object : SharedPreferences by units {
            override fun getString(key: String?, defValue: String?): String? {
                blocked += runCatching { shared.beginCapture() }.exceptionOrNull() is OfflineBackupValidationException
                return units.getString(key, defValue)
            }
        }
        val observedContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                if (name == "wohneinheiten_prefs") observedUnits else context.getSharedPreferences(name, mode)
        }
        shared.completeCapture(shared.beginCapture())
        val period = com.example.ui.TenantPeriod(23, name, "Neu", "2020-01-01", "", 600.0, 100.0, 5.0)
        assertTrue(com.example.ui.TenantHistoryStore.save(observedContext, pid, uid, name, listOf(period)))
        assertTrue(blocked.isNotEmpty() && blocked.all { it })
        assertEquals(histories.getString("history_v2_${pid}_$uid", null), histories.getString("history_$name", null))
        blocked.clear()
        val unit = com.example.ui.WohneinheitStatus(name, "", "Vermietet", "", 600.0, 50.0, unitId = uid)
        com.example.ui.PropertyUnitScopedData.setRentValues(observedContext, pid, unit, 150.0, 7.0)
        assertTrue(blocked.isNotEmpty() && blocked.all { it })
        val rents = context.getSharedPreferences("rent_plan_prefs", 0)
        assertEquals(150f, rents.getFloat("v2_${pid}_${uid}_nk", 0f), 0f)
        assertEquals(150f, rents.getFloat("nk_$name", 0f), 0f)
        shared.completeCapture(shared.beginCapture())
    }
}
