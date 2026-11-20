package com.example.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineRestoreDatabaseVerifierTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var database: AppDatabase
    private lateinit var original: File
    private val namespace = "rollback-database-verify-${UUID.randomUUID()}"
    private fun id() = UUID.randomUUID().toString()
    private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
    @Before fun setup() = runBlocking<Unit> {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, namespace).apply { check(mkdirs()) }
        context = object : ContextWrapper(base) {
            private fun dir(name: String) = File(root, name).apply { check(isDirectory || mkdirs()) }
            override fun getFilesDir() = dir("files")
            override fun getCacheDir() = dir("cache")
            override fun getNoBackupFilesDir() = dir("no-backup")
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = base.getSharedPreferences("${namespace}_$name", mode)
        }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).openHelperFactory(OfflineBackupCoordinator.shared.sqliteFactory()).build()
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 3, propertyId = "house", name = "Haus"))
        original = File(context.filesDir, "managed_documents/original.pdf").apply { parentFile!!.mkdirs(); writeText("original") }
        val external = File(root, "external.pdf").apply { writeText("external") }
        val missing = File(root, "missing.pdf")
        val paths = JSONArray(listOf(original.absolutePath, external.absolutePath, missing.absolutePath)).toString()
        database.receiptDao().insertReceipt(Receipt(id = 41, internalId = "stable", propertyId = "house", aussteller = "Beleg",
            datum = "2026-11-11", uhrzeit = "", bruttobetrag = 12.0, hauptkategorie = "Kosten", unterkategorie = "Reparatur",
            kontoNr = "4800", beschreibung = "Original", imageUrl = paths))
        database.receiptDao().insertReceipt(Receipt(id = 42, internalId = "cloud-only", propertyId = "house", aussteller = "Cloud",
            datum = "2026-11-11", uhrzeit = "", bruttobetrag = 1.0, hauptkategorie = "Kosten", unterkategorie = "Reparatur",
            kontoNr = "4800", beschreibung = "Nicht lokal", driveFileId = "synthetic-cloud", originalMimeType = "application/pdf"))
        database.receiptDocumentDao().insertDocument(ReceiptDocumentReference(id = "cloud-attachment", receiptInternalId = "cloud-only",
            driveFileId = "synthetic-attachment", driveFolderId = null, filename = "attachment.pdf", mimeType = "application/pdf",
            sizeBytes = 123, role = "OTHER", createdAt = "2026-11-11"))
        context.getSharedPreferences("bank_transaction_notes", 0).edit().putString("note", "keep").commit()
    }
    @After fun cleanup() {
        database.close(); PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        root.deleteRecursively()
    }
    private suspend fun bundle() = OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
        OfflineRestoreRollbackBundle.prepare(context, database, token)
    }
    private fun bind(bundle: OfflineRestoreRollbackBundle.Prepared): OfflineRestoreJournal.Entry =
        OfflineRestoreJournal(context).begin(id(), id(), bundle.id, bundle.manifest.length(), hash(bundle.manifest))
    private fun updateMaster(bundle: OfflineRestoreRollbackBundle.Prepared, file: File, bytesKey: String, hashKey: String) {
        bundle.manifest.writeText(JSONObject(bundle.manifest.readText()).put(bytesKey, file.length()).put(hashKey, hash(file)).toString())
    }
    private fun noWorkLeft() {
        val parent = File(context.noBackupFilesDir, "offline-backup-state")
        assertTrue(parent.listFiles().orEmpty().none { it.name.startsWith("rollback-check-") })
    }
    @Test fun verifiesIsolatedDatabaseSchemaAndAllReferencesIncludingPreviouslyMissingOriginals() = runBlocking<Unit> {
        val bundle = bundle(); val journal = bind(bundle)
        val result = OfflineRestoreDatabaseVerifier.verify(context, journal)
        assertEquals(2L, result.rowCounts["receipts"])
        assertEquals(5, result.references.size)
        assertEquals(3, result.files.references.count { it.state == OfflineRestoreReferenceSnapshot.State.UNAVAILABLE })
        assertEquals("keep", result.preferences.payload.getJSONObject("bankTransactionNotes").getJSONObject("note").getString("value"))
        assertEquals(2, database.receiptDao().getAllReceiptsIncludingDeletedList().size)
        assertEquals("original", original.readText()); assertEquals(journal, OfflineRestoreJournal(context).load())
        noWorkLeft()
    }
    @Test fun legacyMultiPageReferencesRemainVerifiableAfterOriginalLocationsDisappear() = runBlocking<Unit> {
        val external = File(root, "external.pdf")
        database.receiptDao().insertReceipt(Receipt(id = 43, internalId = "legacy-pages", propertyId = "house", aussteller = "Alt",
            datum = "2026-11-11", uhrzeit = "", bruttobetrag = 2.0, hauptkategorie = "Kosten", unterkategorie = "Reparatur",
            kontoNr = "4800", beschreibung = "Alte Seiten", imageUrl = "${original.absolutePath},${external.absolutePath}"))
        val bundle = bundle(); val journal = bind(bundle)
        assertTrue(original.delete()); assertTrue(external.delete())
        val result = OfflineRestoreDatabaseVerifier.verify(context, journal)
        assertEquals(3L, result.rowCounts["receipts"])
        assertEquals(journal, OfflineRestoreJournal(context).load()); noWorkLeft()
    }

    @Test fun changedDatabaseSchemaFailsEvenWhenAllBoundHashesMatch() = runBlocking<Unit> {
        val bundle = bundle()
        SQLiteDatabase.openDatabase(bundle.database.file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("ALTER TABLE property_metadata ADD COLUMN unexpected TEXT")
        }
        updateMaster(bundle, bundle.database.file, "databaseBytes", "databaseSha256")
        val journal = bind(bundle)
        OfflineRestoreRollbackVerifier.verifyIndex(context, journal)
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestoreDatabaseVerifier.verify(context, journal) } }
        assertEquals(journal, OfflineRestoreJournal(context).load()); noWorkLeft()
        assertEquals(2, database.receiptDao().getAllReceiptsIncludingDeletedList().size)
    }
    @Test fun missingDatabaseIndexFailsEvenWhenAllBoundHashesMatch() = runBlocking<Unit> {
        val bundle = bundle()
        SQLiteDatabase.openDatabase(bundle.database.file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("DROP INDEX index_receipts_propertyId")
        }
        updateMaster(bundle, bundle.database.file, "databaseBytes", "databaseSha256")
        val journal = bind(bundle)
        OfflineRestoreRollbackVerifier.verifyIndex(context, journal)
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestoreDatabaseVerifier.verify(context, journal) } }
        assertEquals(journal, OfflineRestoreJournal(context).load()); noWorkLeft()
    }

    @Test fun mismatchedDatabaseReferenceFailsEvenWhenAllBoundHashesMatch() = runBlocking<Unit> {
        val bundle = bundle(); val manifest = File(bundle.references.directory, "manifest.json")
        val json = JSONObject(manifest.readText())
        json.getJSONArray("references").getJSONObject(0).put("pathSha256", "0".repeat(64))
        manifest.writeText(json.toString()); updateMaster(bundle, manifest, "referencesManifestBytes", "referencesManifestSha256")
        val journal = bind(bundle)
        OfflineRestoreFileManifestVerifier.verify(context, journal)
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestoreDatabaseVerifier.verify(context, journal) } }
        assertEquals(journal, OfflineRestoreJournal(context).load()); noWorkLeft()
        assertEquals("original", original.readText())
    }
    @Test fun damagedDatabaseIsRejectedEvenWhenItsNewHashIsBound() = runBlocking<Unit> {
        val bundle = bundle(); val file = bundle.database.file
        val bytes = file.readBytes(); bytes[0] = 0; file.writeBytes(bytes)
        updateMaster(bundle, file, "databaseBytes", "databaseSha256")
        val journal = bind(bundle)
        OfflineRestoreRollbackVerifier.verifyIndex(context, journal)
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestoreDatabaseVerifier.verify(context, journal) } }
        assertEquals(journal, OfflineRestoreJournal(context).load()); noWorkLeft()
    }
    @Test fun rollbackRestoresDatabaseRowsAndIdsAndCanBeRepeated() = runBlocking<Unit> {
        val bundle = bundle(); val prepared = bind(bundle)
        val journal = OfflineRestoreJournal(context)
        val rollback = journal.advance(prepared, OfflineRestoreJournal.Phase.ROLLBACK)
        val expectedSequence = android.database.sqlite.SQLiteDatabase.openDatabase(bundle.database.file.path, null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use { snapshot ->
            snapshot.rawQuery("SELECT seq FROM sqlite_sequence WHERE name = 'receipts'", null).use {
                assertTrue(it.moveToFirst()); it.getLong(0)
            }
        }
        database.openHelper.writableDatabase.execSQL("DELETE FROM receipts")
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 5, propertyId = "new", name = "Neu"))
        OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
            repeat(2) { OfflineRestoreDatabaseRollback.apply(context, database, token, rollback) }
        }
        val receipts = database.receiptDao().getAllReceiptsIncludingDeletedList()
        assertEquals(setOf(41, 42), receipts.map { it.id }.toSet())
        assertEquals(setOf("stable", "cloud-only"), receipts.map { it.internalId }.toSet())
        val restoredSequence = database.openHelper.readableDatabase.query("SELECT seq FROM sqlite_sequence WHERE name = 'receipts'").use {
            assertTrue(it.moveToFirst()); it.getLong(0)
        }
        assertEquals(expectedSequence, restoredSequence)
        assertEquals(bundle.database.rowCounts["property_metadata"], 1L)
        assertEquals(1L, database.openHelper.readableDatabase.query("SELECT count(*) FROM property_metadata").use { it.moveToFirst(); it.getLong(0) })
        assertEquals(rollback, journal.load())
        noWorkLeft()
    }
    @Test fun rollbackRejectsTamperingAndWrongPhaseWithoutChangingLiveRows() = runBlocking<Unit> {
        val bundle = bundle(); val prepared = bind(bundle)
        OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineRestoreDatabaseRollback.apply(context, database, token, prepared)
            } }
        }
        val rollback = OfflineRestoreJournal(context).advance(prepared, OfflineRestoreJournal.Phase.ROLLBACK)
        bundle.database.file.appendText("tampered")
        OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineRestoreDatabaseRollback.apply(context, database, token, rollback)
            } }
        }
        assertEquals(setOf(41, 42), database.receiptDao().getAllReceiptsIncludingDeletedList().map { it.id }.toSet())
        assertEquals(rollback, OfflineRestoreJournal(context).load())
    }

}
