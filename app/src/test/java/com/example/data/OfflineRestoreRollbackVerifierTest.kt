package com.example.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineRestoreRollbackVerifierTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var database: AppDatabase
    private val namespace = "rollback-verify-${UUID.randomUUID()}"
    private fun id() = UUID.randomUUID().toString()
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
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .openHelperFactory(OfflineBackupCoordinator.shared.sqliteFactory()).build()
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 3, propertyId = "house", name = "Haus"))
        val original = File(context.filesDir, "managed_documents/original.pdf").apply { parentFile!!.mkdirs(); writeText("original") }
        database.receiptDao().insertReceipt(Receipt(id = 41, internalId = "stable", propertyId = "house", aussteller = "Beleg",
            datum = "2026-11-11", uhrzeit = "", bruttobetrag = 12.0, hauptkategorie = "Kosten", unterkategorie = "Reparatur",
            kontoNr = "4800", beschreibung = "Original", imageUrl = original.absolutePath))
    }
    @After fun cleanup() {
        database.close(); PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        root.deleteRecursively()
    }
    private suspend fun prepare(): Pair<OfflineRestoreRollbackBundle.Prepared, OfflineRestoreJournal.Entry> =
        OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
            val bundle = OfflineRestoreRollbackBundle.prepare(context, database, token)
            bundle to OfflineRestoreJournal(context).begin(id(), id(), bundle.id, bundle.manifestBytes, bundle.manifestSha256)
        }
    @Test fun verifiesJournalBoundIndexAndDirectArtifactsWithoutTouchingLiveData() = runBlocking<Unit> {
        val (bundle, entry) = prepare()
        val index = OfflineRestoreRollbackVerifier.verifyIndex(context, entry)
        assertEquals(bundle.id, index.rollbackId); assertEquals(1, index.fileCount)
        assertEquals(1, index.referenceCount); assertEquals(0, index.unavailableCount)
        assertEquals(bundle.database.file, index.database); assertEquals(bundle.preferences.file, index.preferences)
        assertEquals(41, database.receiptDao().getAllReceiptsIncludingDeletedList().single().id)
        assertEquals(entry, OfflineRestoreJournal(context).load())
    }
    @Test fun rejectsAnyChangedDirectArtifactAndKeepsJournalAndOtherFiles() = runBlocking<Unit> {
        val (bundle, entry) = prepare()
        val targets = listOf(bundle.manifest, bundle.database.file, bundle.preferences.file,
            File(bundle.ownedFiles.directory, "manifest.json"), File(bundle.references.directory, "manifest.json"))
        targets.forEach { target ->
            val original = target.readBytes(); target.writeBytes(original.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() })
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestoreRollbackVerifier.verifyIndex(context, entry) } }
            assertEquals(entry, OfflineRestoreJournal(context).load()); target.writeBytes(original)
        }
        OfflineRestoreRollbackVerifier.verifyIndex(context, entry)
        assertEquals(41, database.receiptDao().getAllReceiptsIncludingDeletedList().single().id)
    }
    @Test fun malformedBoundIndexAndStaleJournalNeverPassVerification() = runBlocking<Unit> {
        val journal = OfflineRestoreJournal(context); val rollbackId = id()
        val file = File(context.noBackupFilesDir, "offline-backup-state/rollback-$rollbackId.bundle.json")
        file.parentFile!!.mkdirs(); file.writeText("{\"version\":1,\"version\":1}")
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
        val entry = journal.begin(id(), id(), rollbackId, file.length(), digest)
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestoreRollbackVerifier.verifyIndex(context, entry) } }
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestoreRollbackVerifier.verifyIndex(context, entry.copy(operationId = id())) } }
        assertEquals(entry, journal.load()); assertEquals("{\"version\":1,\"version\":1}", file.readText())
    }
}
