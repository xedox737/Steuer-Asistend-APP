package com.example.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.ManagedDocument
import com.example.data.RestoreMode
import com.example.data.StableDocumentIdentity
import com.example.data.SupplementalDriveBackup
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RestoreMergeDataIntegrityTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPrefs()
        File(context.filesDir, "managed_documents").deleteRecursively()
        database = newDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        clearPrefs()
        File(context.filesDir, "managed_documents").deleteRecursively()
    }

    private fun newDatabase() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private fun clearPrefs() {
        listOf("tenant_history_prefs", "property_tasks_prefs").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test
    fun mergeRestore_preservesNewerTenantPeriodAndTask_andIsIdempotent() = runTest {
        val propertyId = "property-A"
        val unitId = "unit-A"
        val unitName = "WE 1"
        val periodA = TenantPeriod(
            id = 101L, unitName = unitName, tenantName = "Mieter A",
            startDate = "2026-01-01", endDate = "",
            kaltmiete = 760.0, nebenkosten = 220.0, sonstige = 0.0
        )
        TenantHistoryStore.save(context, propertyId, unitId, unitName, listOf(periodA))
        PropertyTaskStore.upsert(
            context,
            PropertyTask(
                id = "task-A", propertyId = propertyId, unitId = unitId,
                title = "Aufgabe A", createdAt = "2026-01-01T00:00:00Z",
                updatedAt = "2026-01-01T00:00:00Z"
            )
        )
        val payload = SupplementalDriveBackup.createPayload(context, database)

        val periodB = TenantPeriod(
            id = 202L, unitName = unitName, tenantName = "Mieter B",
            startDate = "2026-10-01", endDate = "",
            kaltmiete = 850.0, nebenkosten = 220.0, sonstige = 0.0
        )
        TenantHistoryStore.save(
            context, propertyId, unitId, unitName,
            listOf(periodA.copy(endDate = "2026-09-30"), periodB)
        )
        PropertyTaskStore.upsert(
            context,
            PropertyTask(
                id = "task-B", propertyId = propertyId, unitId = unitId,
                title = "Aufgabe B", createdAt = "2026-10-01T00:00:00Z",
                updatedAt = "2026-10-01T00:00:00Z"
            )
        )

        repeat(2) {
            SupplementalDriveBackup.restorePayload(
                context, database, payload, restoreMode = RestoreMode.MERGE
            )
        }

        val periods = TenantHistoryStore.load(context, propertyId, unitId, unitName)
        assertEquals(setOf(101L, 202L), periods.map { it.id }.toSet())
        assertEquals("2026-09-30", periods.single { it.id == 101L }.endDate)
        assertEquals(setOf("task-A", "task-B"), PropertyTaskStore.load(context, propertyId).map { it.id }.toSet())
        assertEquals(2, periods.size)
        assertEquals(2, PropertyTaskStore.load(context, propertyId).size)
    }

    @Test
    fun mergeRestore_preservesPhase1BRentChangesFromBackupAndLocal() = runTest {
        val propertyId = "property-rent"
        val unitId = "unit-rent"
        val unitName = "WE 1"
        val backupPeriod = TenantPeriod(
            id = 11L, unitName = unitName, tenantName = "Mieter",
            startDate = "2026-01-01", endDate = "",
            kaltmiete = 760.0, nebenkosten = 220.0, sonstige = 0.0,
            rentChanges = listOf(RentAmountChange("2026-07-01", 800.0, 220.0, 0.0))
        )
        TenantHistoryStore.save(context, propertyId, unitId, unitName, listOf(backupPeriod))
        val payload = SupplementalDriveBackup.createPayload(context, database)

        TenantHistoryStore.save(
            context, propertyId, unitId, unitName,
            listOf(
                backupPeriod.copy(
                    rentChanges = backupPeriod.rentChanges +
                        RentAmountChange("2026-10-01", 850.0, 220.0, 0.0)
                )
            )
        )

        repeat(3) {
            SupplementalDriveBackup.restorePayload(
                context, database, payload, restoreMode = RestoreMode.MERGE
            )
        }

        val restored = TenantHistoryStore.load(context, propertyId, unitId, unitName).single()
        assertEquals(listOf("2026-07-01", "2026-10-01"), restored.rentChanges.map { it.effectiveDate })
        assertEquals(760.0, restored.amountsAt(java.time.LocalDate.of(2026, 1, 1)).kaltmiete, 0.0)
        assertEquals(800.0, restored.amountsAt(java.time.LocalDate.of(2026, 7, 1)).kaltmiete, 0.0)
        assertEquals(850.0, restored.amountsAt(java.time.LocalDate.of(2026, 10, 1)).kaltmiete, 0.0)
    }

    @Test
    fun mergeRestore_usesTaskUpdatedAtAndOtherwiseKeepsLocalConflict() = runTest {
        val propertyId = "property-task"
        val old = PropertyTask(
            id = "task-A", propertyId = propertyId, title = "Backup alt",
            createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z"
        )
        PropertyTaskStore.upsert(context, old)
        val oldPayload = SupplementalDriveBackup.createPayload(context, database)

        PropertyTaskStore.upsert(
            context, old.copy(title = "Lokal neuer", updatedAt = "2026-10-01T00:00:00Z")
        )
        SupplementalDriveBackup.restorePayload(
            context, database, oldPayload, restoreMode = RestoreMode.MERGE
        )
        assertEquals("Lokal neuer", PropertyTaskStore.load(context, propertyId).single().title)

        PropertyTaskStore.upsert(
            context, old.copy(title = "Backup neuer", updatedAt = "2026-12-01T00:00:00Z")
        )
        val newerPayload = SupplementalDriveBackup.createPayload(context, database)
        PropertyTaskStore.upsert(
            context, old.copy(title = "Lokal älter", updatedAt = "2026-11-01T00:00:00Z")
        )
        SupplementalDriveBackup.restorePayload(
            context, database, newerPayload, restoreMode = RestoreMode.MERGE
        )
        assertEquals("Backup neuer", PropertyTaskStore.load(context, propertyId).single().title)
    }

    @Test
    fun mergeRestore_keepsUsableLocalDocumentPathAndNewerLocalMetadata() = runTest {
        val dir = File(context.filesDir, "managed_documents").apply { mkdirs() }
        val file = File(dir, "doc-A.pdf").apply { writeBytes("%PDF-test".toByteArray()) }
        val sha = StableDocumentIdentity.sha256(file.readBytes())
        val backupDocument = ManagedDocument(
            documentId = "doc-A", propertyId = "property-A", unitId = "unit-A",
            title = "Backup-Titel", storedFilename = "doc-A.pdf", mimeType = "application/pdf",
            localUri = file.absolutePath, driveFileId = "drive-A", sha256 = sha,
            fileSizeBytes = file.length(), createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z"
        )
        database.managedDocumentDao().upsert(backupDocument)
        val payload = SupplementalDriveBackup.createPayload(context, database)

        database.managedDocumentDao().upsert(
            backupDocument.copy(title = "Lokal neuer", updatedAt = "2026-10-01T00:00:00Z")
        )

        repeat(2) {
            SupplementalDriveBackup.restorePayload(
                context, database, payload, restoreMode = RestoreMode.MERGE
            )
        }

        val restored = database.managedDocumentDao().getById("doc-A")!!
        assertEquals("Lokal neuer", restored.title)
        assertEquals(file.absolutePath, restored.localUri)
        assertEquals("drive-A", restored.driveFileId)
        assertEquals(sha, restored.sha256)
        assertTrue(File(restored.localUri).isFile)
        assertEquals(1, database.managedDocumentDao().getAll().size)
    }

    @Test
    fun mergeRestore_relinksManagedFileByHashWithoutCreatingCopy() = runTest {
        val dir = File(context.filesDir, "managed_documents").apply { mkdirs() }
        val file = File(dir, "hash-source.pdf").apply { writeBytes("%PDF-hash".toByteArray()) }
        val sha = StableDocumentIdentity.sha256(file.readBytes())
        val document = ManagedDocument(
            documentId = "doc-hash", propertyId = "property-A",
            storedFilename = "hash-source.pdf", mimeType = "application/pdf",
            localUri = file.absolutePath, driveFileId = "drive-hash", sha256 = sha,
            fileSizeBytes = file.length(), updatedAt = "2026-01-01T00:00:00Z"
        )
        database.managedDocumentDao().upsert(document)
        val payload = SupplementalDriveBackup.createPayload(context, database)
        database.managedDocumentDao().deleteById(document.documentId)

        SupplementalDriveBackup.restorePayload(
            context, database, payload, restoreMode = RestoreMode.MERGE
        )
        val restored = database.managedDocumentDao().getById(document.documentId)!!
        assertEquals(file.absolutePath, restored.localUri)
        assertEquals(1, dir.listFiles()!!.size)
    }

    @Test
    fun freshDeviceRestore_neverReusesDeadDevicePathAndKeepsRemoteReference() = runTest {
        val dir = File(context.filesDir, "managed_documents").apply { mkdirs() }
        val file = File(dir, "old-device.pdf").apply { writeBytes("%PDF-old".toByteArray()) }
        val document = ManagedDocument(
            documentId = "doc-remote", propertyId = "property-A",
            storedFilename = "remote.pdf", mimeType = "application/pdf",
            localUri = file.absolutePath, driveFileId = "drive-remote",
            sha256 = StableDocumentIdentity.sha256(file.readBytes()),
            fileSizeBytes = file.length(), updatedAt = "2026-01-01T00:00:00Z"
        )
        database.managedDocumentDao().upsert(document)
        val payload = SupplementalDriveBackup.createPayload(context, database)

        file.delete()
        database.close()
        database = newDatabase()

        SupplementalDriveBackup.restorePayload(
            context, database, payload, restoreMode = RestoreMode.MERGE
        )
        val restored = database.managedDocumentDao().getById(document.documentId)!!
        assertTrue(restored.localUri.isBlank())
        assertEquals("drive-remote", restored.driveFileId)
        assertFalse(restored.localUri == file.absolutePath)
    }

    @Test
    fun replaceFullRemainsExplicitSnapshotForTasksWhileMergeDoesNot() = runTest {
        val propertyId = "property-replace"
        val taskA = PropertyTask(
            id = "task-A", propertyId = propertyId, title = "A",
            createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z"
        )
        PropertyTaskStore.upsert(context, taskA)
        val payload = SupplementalDriveBackup.createPayload(context, database)
        PropertyTaskStore.upsert(
            context,
            taskA.copy(id = "task-B", title = "B", updatedAt = "2026-10-01T00:00:00Z")
        )

        SupplementalDriveBackup.restorePayload(
            context, database, payload, restoreMode = RestoreMode.REPLACE_FULL
        )
        assertEquals(listOf("task-A"), PropertyTaskStore.load(context, propertyId).map { it.id })
    }
}
