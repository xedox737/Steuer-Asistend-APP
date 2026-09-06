package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.api.DriveFileResult
import com.example.api.DriveManagedFile
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrivePersistenceManagedDocumentE2eTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var localRepository: ReceiptRepository
    private lateinit var fakeDrive: ManagedDocumentFakeDrive
    private lateinit var driveRepository: DrivePersistenceRepository
    private lateinit var documentService: ManagedDocumentService

    private val config = DriveAppConfig(
        rootFolderId = "root",
        systemFolderId = "system",
        receiptsFolderId = "receipts",
        paymentsFolderId = "payments",
        exportsFolderId = "exports",
        transactionsFolderId = "transactions",
        backupsFolderId = "backups",
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = "2026-01-01T00:00:00Z"
    )

    @Before fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        localRepository = ReceiptRepository(
            database.receiptDao(), database.propertyDao(), database.receiptEntityDao(), database.belegDao(),
            database.exportAuditDao(), database.receiptDocumentDao(), database.managedDocumentDao()
        )
        localRepository.updatePropertyMetadata(
            PropertyMetadata(propertyId = "property-test", name = "Testobjekt", adresse = "Musterstrasse 12")
        )
        context.getSharedPreferences("wohneinheiten_prefs", Context.MODE_PRIVATE).edit()
            .putString("unit_id_WE_01", "unit-test-01").commit()
        fakeDrive = ManagedDocumentFakeDrive()
        driveRepository = DrivePersistenceRepository(context, localRepository, fakeDrive)
        documentService = ManagedDocumentService(context, localRepository)
    }

    @After fun tearDown() {
        context.getSharedPreferences("wohneinheiten_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        database.close()
    }

    @Test fun `fixture 9 purchase contract uses production repository sync and updates real index json`() = runTest {
        assertSuccessfulReclassification(9, ManagedDocumentType.KAUFVERTRAG, null, "00_Stammdaten/01_Kauf_Eigentum")
    }

    @Test fun `fixture 10 loan contract uses production repository sync`() = runTest {
        assertSuccessfulReclassification(10, ManagedDocumentType.DARLEHENSVERTRAG, null, "03_Finanzierung_AfA/Darlehen")
    }

    @Test fun `fixture 13 rental contract keeps confirmed unit and production target`() = runTest {
        val updated = assertSuccessfulReclassification(
            13, ManagedDocumentType.MIETVERTRAG, "unit-test-01", "01_Einheiten/WE_01/Mietvertrag"
        )
        assertEquals("unit-test-01", updated.unitId)
        assertTrue(updated.documentCategory.contains("WE_01"))
    }

    @Test fun `offline confirmed reclassification retries same original without replacement upload`() = runTest {
        val fixture = SyntheticDocumentFixtureFactory.create().single { it.number == 9 }
        val original = insertOtherDocument(fixture)
        val confirmed = documentService.confirmReview(original, ManagedDocumentType.KAUFVERTRAG, "2025-10-01", null, "{}")
        assertEquals("DRIVE_REORGANIZATION_PENDING", confirmed.migrationStatus)
        val fileId = requireNotNull(original.driveFileId)
        val before = fakeDrive.snapshot(fileId)

        fakeDrive.failure = FakeDriveFailure.HTTP_503
        assertFalse(driveRepository.syncManagedDocumentToDrive("test-token", config, original.documentId))
        val pending = localRepository.getManagedDocument(original.documentId)!!
        assertEquals(ManagedDocumentType.KAUFVERTRAG.name, pending.documentType)
        assertEquals("DRIVE_DATEI_NICHT_ERREICHBAR", pending.migrationStatus)
        assertEquals(before, fakeDrive.snapshot(fileId))
        assertEquals(0, fakeDrive.uploadCalls)

        fakeDrive.failure = FakeDriveFailure.NONE
        assertTrue(driveRepository.syncManagedDocumentToDrive("test-token", config, original.documentId))
        val synced = localRepository.getManagedDocument(original.documentId)!!
        assertEquals(original.driveFileId, synced.driveFileId)
        assertEquals(original.sha256, synced.sha256)
        assertEquals("SYNCED", synced.migrationStatus)
        assertEquals(0, fakeDrive.uploadCalls)
    }

    @Test fun `hash mismatch blocks move rename replacement upload and index success`() = runTest {
        val fixture = SyntheticDocumentFixtureFactory.create().single { it.number == 29 }
        val original = insertOtherDocument(fixture, expectedHash = "a".repeat(64))
        val fileId = requireNotNull(original.driveFileId)
        val before = fakeDrive.snapshot(fileId)
        documentService.confirmReview(original, ManagedDocumentType.KAUFVERTRAG, "2025-10-01", null, "{}")

        assertFalse(driveRepository.syncManagedDocumentToDrive("test-token", config, original.documentId))

        assertEquals(before, fakeDrive.snapshot(fileId))
        assertEquals(0, fakeDrive.moveCalls)
        assertEquals(0, fakeDrive.uploadCalls)
        assertEquals(0, fakeDrive.indexWrites)
        assertEquals(DocumentReviewStatus.MIGRATION_PRUEFEN.name, localRepository.getManagedDocument(original.documentId)!!.migrationStatus)
    }

    @Test fun `404 for existing Drive id never creates a replacement original`() = runTest {
        val fixture = SyntheticDocumentFixtureFactory.create().single { it.number == 10 }
        val original = insertOtherDocument(fixture)
        documentService.confirmReview(original, ManagedDocumentType.DARLEHENSVERTRAG, "2026-01-01", null, "{}")
        fakeDrive.failure = FakeDriveFailure.HTTP_404

        assertFalse(driveRepository.syncManagedDocumentToDrive("test-token", config, original.documentId))

        assertEquals(0, fakeDrive.moveCalls)
        assertEquals(0, fakeDrive.uploadCalls)
        assertEquals(0, fakeDrive.indexWrites)
        val failed = localRepository.getManagedDocument(original.documentId)!!
        assertEquals(original.driveFileId, failed.driveFileId)
        assertEquals("DRIVE_DATEI_NICHT_ERREICHBAR", failed.migrationStatus)
    }

    @Test fun `429 keeps confirmed classification pending without mutating Drive`() = runTest {
        assertTransientFailureLeavesOriginalUntouched(FakeDriveFailure.HTTP_429)
    }

    @Test fun `timeout keeps confirmed classification pending without mutating Drive`() = runTest {
        assertTransientFailureLeavesOriginalUntouched(FakeDriveFailure.TIMEOUT)
    }

    private suspend fun assertTransientFailureLeavesOriginalUntouched(failure: FakeDriveFailure) {
        val fixture = SyntheticDocumentFixtureFactory.create().single { it.number == 9 }
        val original = insertOtherDocument(fixture)
        documentService.confirmReview(original, ManagedDocumentType.KAUFVERTRAG, "2025-10-01", null, "{}")
        val fileId = requireNotNull(original.driveFileId)
        val before = fakeDrive.snapshot(fileId)
        fakeDrive.failure = failure

        assertFalse(driveRepository.syncManagedDocumentToDrive("test-token", config, original.documentId))

        assertEquals(before, fakeDrive.snapshot(fileId))
        assertEquals(0, fakeDrive.moveCalls)
        assertEquals(0, fakeDrive.uploadCalls)
        val pending = localRepository.getManagedDocument(original.documentId)!!
        assertEquals(ManagedDocumentType.KAUFVERTRAG.name, pending.documentType)
        assertEquals("DRIVE_DATEI_NICHT_ERREICHBAR", pending.migrationStatus)
    }

    private suspend fun assertSuccessfulReclassification(
        fixtureNumber: Int,
        type: ManagedDocumentType,
        unitId: String?,
        expectedCategory: String
    ): ManagedDocument {
        val fixture = SyntheticDocumentFixtureFactory.create().single { it.number == fixtureNumber }
        val original = insertOtherDocument(fixture)
        val fileId = requireNotNull(original.driveFileId)
        val before = fakeDrive.snapshot(fileId)

        assertEquals(ManagedDocumentType.SONSTIGES.name, original.documentType)
        assertEquals(before, fakeDrive.snapshot(fileId))
        val confirmed = documentService.confirmReview(original, type, if (fixtureNumber == 9) "2025-10-01" else "2026-01-01", unitId, "{\"accepted\":true}")
        assertEquals("DRIVE_REORGANIZATION_PENDING", confirmed.migrationStatus)
        assertEquals(before, fakeDrive.snapshot(fileId))

        assertTrue(driveRepository.syncManagedDocumentToDrive("test-token", config, original.documentId))

        val updated = localRepository.getManagedDocument(original.documentId)!!
        val driveFile = fakeDrive.snapshot(fileId)
        assertEquals(original.driveFileId, updated.driveFileId)
        assertEquals(original.driveFileId, driveFile.id)
        assertArrayEquals(before.bytes, driveFile.bytes)
        assertEquals(before.sha256, driveFile.sha256)
        assertEquals(expectedCategory, updated.documentCategory)
        assertEquals(updated.driveFolderId, driveFile.parentId)
        assertEquals(updated.storedFilename, driveFile.name)
        assertNotEquals(original.driveFolderId, updated.driveFolderId)
        assertNotEquals(original.storedFilename, updated.storedFilename)
        assertEquals("SYNCED", updated.migrationStatus)
        assertEquals(1, fakeDrive.moveCalls)
        assertEquals(0, fakeDrive.uploadCalls)

        val root = JSONObject(requireNotNull(fakeDrive.indexJson))
        val indexEntry = (0 until root.getJSONArray("documents").length())
            .map { root.getJSONArray("documents").getJSONObject(it) }
            .single { it.getString("documentId") == original.documentId }
        assertEquals("property-test", indexEntry.getString("propertyId"))
        if (unitId == null) assertTrue(indexEntry.isNull("unitId")) else assertEquals(unitId, indexEntry.getString("unitId"))
        assertTrue(indexEntry.isNull("receiptInternalId"))
        assertEquals(type.name, indexEntry.getString("documentType"))
        assertEquals(updated.documentDate, indexEntry.getString("documentDate"))
        assertEquals(updated.storedFilename, indexEntry.getString("storedFilename"))
        assertEquals(updated.driveFileId, indexEntry.getString("driveFileId"))
        assertEquals(updated.driveFolderId, indexEntry.getString("driveFolderId"))
        assertEquals(updated.sha256, indexEntry.getString("sha256"))
        assertEquals(updated.fileSizeBytes, indexEntry.getLong("fileSizeBytes"))
        assertEquals(DocumentReviewStatus.GEPRUEFT.name, indexEntry.getString("reviewStatus"))
        assertEquals(updated.updatedAt, indexEntry.getString("updatedAt"))
        assertFalse(fakeDrive.indexJson!!.contains(original.driveFolderId.orEmpty()))
        assertFalse(fakeDrive.indexJson!!.contains(original.storedFilename))
        return updated
    }

    private suspend fun insertOtherDocument(fixture: SyntheticDocumentFixture, expectedHash: String = fixture.sha256): ManagedDocument {
        val document = ManagedDocument(
            documentId = "repository-e2e-${fixture.number}",
            propertyId = "property-test",
            documentType = ManagedDocumentType.SONSTIGES.name,
            documentCategory = "00_Stammdaten/06_Sonstige_Objektunterlagen",
            documentDate = if (fixture.number == 9) "2025-10-01" else "2026-01-01",
            title = fixture.filename.substringBeforeLast('.'),
            originalFilename = fixture.filename,
            storedFilename = fixture.filename,
            mimeType = "application/pdf",
            driveFileId = fixture.driveFileId,
            driveFolderId = "folder-other",
            sha256 = expectedHash,
            fileSizeBytes = fixture.bytes.size.toLong(),
            reviewStatus = DocumentReviewStatus.PRUEFEN.name,
            migrationStatus = "SYNCED",
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z"
        )
        localRepository.upsertManagedDocument(document)
        fakeDrive.put(document.driveFileId!!, "folder-other", document.storedFilename, fixture.bytes)
        return document
    }
}

private enum class FakeDriveFailure { NONE, HTTP_404, HTTP_429, HTTP_503, TIMEOUT }

private data class FakeManagedDriveFile(
    val id: String,
    val parentId: String,
    val name: String,
    val bytes: ByteArray,
    val sha256: String = StableDocumentIdentity.sha256(bytes)
) {
    override fun equals(other: Any?): Boolean = other is FakeManagedDriveFile &&
        id == other.id && parentId == other.parentId && name == other.name &&
        bytes.contentEquals(other.bytes) && sha256 == other.sha256
    override fun hashCode(): Int = 31 * id.hashCode() + sha256.hashCode()
}

private class ManagedDocumentFakeDrive : ManagedDocumentDriveGateway {
    var failure: FakeDriveFailure = FakeDriveFailure.NONE
    var moveCalls = 0
    var uploadCalls = 0
    var indexWrites = 0
    var indexJson: String? = null
    private var nextId = 1
    private val files = linkedMapOf<String, FakeManagedDriveFile>()

    fun put(id: String, parentId: String, name: String, bytes: ByteArray) {
        files[id] = FakeManagedDriveFile(id, parentId, name, bytes.copyOf())
    }

    fun snapshot(id: String): FakeManagedDriveFile = requireNotNull(files[id]).copy(bytes = requireNotNull(files[id]).bytes.copyOf())

    override suspend fun getOrCreateFolder(
        accessToken: String,
        folderName: String,
        parentId: String?,
        canonicalPathKey: String?,
        systemFolderId: String?
    ): String? = if (failure == FakeDriveFailure.NONE) "folder:${canonicalPathKey ?: "$parentId/$folderName"}" else null

    override suspend fun getFileMetadata(accessToken: String, fileId: String): DriveManagedFile? {
        if (failure != FakeDriveFailure.NONE) return null
        val file = files[fileId] ?: return null
        return DriveManagedFile(file.id, file.name, "application/pdf", file.bytes.size.toLong(), listOf(file.parentId))
    }

    override suspend fun downloadFileBytes(accessToken: String, fileId: String): ByteArray? =
        if (failure == FakeDriveFailure.NONE) files[fileId]?.bytes?.copyOf() else null

    override suspend fun moveAndRenameFile(
        accessToken: String,
        fileId: String,
        targetParentId: String,
        oldParentIds: List<String>,
        targetFilename: String
    ): Boolean {
        if (failure != FakeDriveFailure.NONE) return false
        val current = files[fileId] ?: return false
        moveCalls++
        files[fileId] = current.copy(parentId = targetParentId, name = targetFilename)
        return true
    }

    override suspend fun uploadFile(
        accessToken: String,
        folderId: String,
        filename: String,
        mimeType: String,
        bytes: ByteArray,
        appProperties: Map<String, String>
    ): String? {
        if (failure != FakeDriveFailure.NONE) return null
        uploadCalls++
        return "uploaded-${nextId++}".also { put(it, folderId, filename, bytes) }
    }

    override suspend fun upsertJson(
        accessToken: String,
        folderId: String,
        entityType: String,
        filename: String,
        json: String
    ): DriveFileResult {
        if (failure != FakeDriveFailure.NONE) return DriveFileResult(false, null, failure.name)
        indexWrites++
        indexJson = json
        return DriveFileResult(true, "document-index", null)
    }
}
