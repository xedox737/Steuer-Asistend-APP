package com.example.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
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
class OfflineRestoreDatabaseSnapshotTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var source: AppDatabase
    private val coordinator get() = OfflineBackupCoordinator.shared
    private fun id() = UUID.randomUUID().toString()
    @Before fun setup() = runBlocking<Unit> {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, "rollback-db-${id()}").apply { check(mkdirs()) }
        context = object : ContextWrapper(base) { override fun getNoBackupFilesDir() = root }
        source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).openHelperFactory(coordinator.sqliteFactory()).build()
        source.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 3, propertyId = "house", name = "Original"))
        source.receiptDao().insertReceipt(Receipt(id = 41, internalId = "stable", propertyId = "house", aussteller = "Beleg",
            datum = "2026-11-11", uhrzeit = "", bruttobetrag = 12.0, hauptkategorie = "Kosten", unterkategorie = "Reparatur",
            kontoNr = "4800", beschreibung = "Original", imageUrl = File(root, "already-missing.pdf").absolutePath))
        source.openHelper.writableDatabase.execSQL("UPDATE sqlite_sequence SET seq=1000 WHERE name='receipts'")
    }
    @After fun cleanup() { source.close(); root.deleteRecursively() }
    @Test fun rollbackCopyPreservesRowsIdsMissingPathsAndAutoIncrementSequence() = runBlocking<Unit> {
        coordinator.beginMaintenance().use { token ->
            val snapshot = OfflineRestoreDatabaseSnapshot.capture(context, source, token, id())
            assertEquals(19, snapshot.rowCounts.size); assertEquals(1L, snapshot.rowCounts["receipts"])
            assertEquals(64, snapshot.sha256.length); assertEquals(snapshot.file.length(), snapshot.size)
            val copy = Room.databaseBuilder(context, AppDatabase::class.java, snapshot.file.absolutePath).build()
            try {
                val receipt = copy.receiptDao().getAllReceiptsIncludingDeletedList().single()
                assertEquals(41, receipt.id); assertEquals("stable", receipt.internalId)
                assertEquals(File(root, "already-missing.pdf").absolutePath, receipt.imageUrl)
                val sequence = copy.openHelper.readableDatabase.query("SELECT seq FROM sqlite_sequence WHERE name='receipts'").use { it.moveToFirst(); it.getLong(0) }
                assertEquals(1000, sequence)
            } finally { copy.close() }
            assertEquals("Original", source.propertyDao().getAllProperties().single().name)
        }
    }
    @Test fun refusesOccupiedDestinationAndLeavesExistingBytesUntouched() = runBlocking<Unit> {
        val rollbackId = id(); val directory = File(root, "offline-backup-state").apply { mkdirs() }
        val existing = File(directory, "rollback-$rollbackId.db").apply { writeText("preserve") }
        coordinator.beginMaintenance().use { token ->
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineRestoreDatabaseSnapshot.capture(context, source, token, rollbackId)
            } }
        }
        assertEquals("preserve", existing.readText())
        assertEquals("Original", source.propertyDao().getAllProperties().single().name)
    }
    @Test fun refusesExpiredOrForeignMaintenanceTokenWithoutCreatingRollbackFiles() = runBlocking<Unit> {
        val foreign = OfflineBackupCoordinator().beginMaintenance()
        try { assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
            OfflineRestoreDatabaseSnapshot.capture(context, source, foreign, id())
        } } } finally { foreign.close() }
        val expired = coordinator.beginMaintenance(); expired.close()
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
            OfflineRestoreDatabaseSnapshot.capture(context, source, expired, id())
        } }
        assertFalse(File(root, "offline-backup-state").exists())
    }
}
