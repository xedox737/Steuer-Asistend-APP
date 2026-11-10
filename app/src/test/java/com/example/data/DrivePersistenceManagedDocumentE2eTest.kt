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

    @Test fun drivePreferenceMutationIsRegisteredBeforeOpeningItsStore() {
        val coordinator = OfflineBackupCoordinator.shared
        val observed = mutableListOf<Boolean>()
        var observing = false
        val probe = object : android.content.ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                if (observing && name == "wohneinheiten_prefs") {
                    observed += runCatching { coordinator.beginCapture() }.exceptionOrNull() is OfflineBackupValidationException
                }
                return super.getSharedPreferences(name, mode)
            }
        }
        val repository = DrivePersistenceRepository(probe, localRepository, fakeDrive)
        val unit = com.example.ui.WohneinheitStatus("WE_01", "Wohnung", "Vermietet", "Tenant", 80.0, 45.0,
            mietvertragsstart = "2026-01-01", unitId = "unit-test-01")
        val capture = coordinator.beginCapture()
        observing = true
        try { repository.saveWohneinheitenToPrefs(listOf(unit), RestoreMode.REPLACE_FULL, "property-test") }
        finally { observing = false }
        assertEquals(listOf(true), observed)
        val prefs = context.getSharedPreferences("wohneinheiten_prefs", 0)
        assertEquals("Tenant", prefs.getString("property_property-test_unitid_unit-test-01_mieter", null))
        assertEquals(80f, prefs.getFloat("property_property-test_unitid_unit-test-01_rent", 0f), 0f)
        assertEquals(45f, prefs.getFloat("property_property-test_unitid_unit-test-01_area", 0f), 0f)
        org.junit.Assert.assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(capture) }
        coordinator.completeCapture(coordinator.beginCapture())
    }

    private suspend fun legacyReceiptWithOriginal(): Pair<Receipt, java.io.File> {
        val file = java.io.File(context.filesDir, "restored_receipts/legacy-version/Original.pdf")
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        file.writeText("previous original bytes")
        val receipt = Receipt(id = 700, internalId = "legacy-version", aussteller = "Legacy",
            datum = "2026-10-08", uhrzeit = "", bruttobetrag = 100.0,
            hauptkategorie = "Renovierung", unterkategorie = "Material", kontoNr = "4800",
            beschreibung = "Original", propertyId = "property-test", unitId = "unit-test-01",
            imageUrl = file.absolutePath, driveFileId = "legacy-remote", storedFilename = file.name,
            originalMimeType = "application/pdf")
        // A real legacy row has not yet been projected into managed_documents.
        database.receiptDao().insertReceipt(receipt)
        assertTrue(localRepository.getAllManagedDocuments().isEmpty())
        return receipt to file
    }

    @Test fun legacyReceiptDownloadPublishesNewVersionWithoutOverwritingPreviousOriginal() = runTest {
        val (receipt, previous) = legacyReceiptWithOriginal()
        val bytes = SyntheticDocumentFixtureFactory.create().first().bytes
        fakeDrive.put("legacy-remote", "receipts", "Original.pdf", bytes)
        var published: java.io.File? = null
        try {
            val path = driveRepository.downloadDocumentOnDemand("test-token", receipt)
            published = java.io.File(path)
            assertNotEquals(previous.absolutePath, path)
            assertEquals("previous original bytes", previous.readText())
            assertArrayEquals(bytes, published.readBytes())
            val saved = localRepository.getReceiptByInternalId(receipt.internalId)!!
            assertEquals(path, saved.imageUrl)
            assertEquals(receipt.id, saved.id)
            assertEquals(receipt.propertyId, saved.propertyId)
            assertEquals(receipt.unitId, saved.unitId)
            assertEquals(receipt.driveFileId, saved.driveFileId)
            assertEquals(receipt.storedFilename, saved.storedFilename)
            assertEquals(path, localRepository.getAllManagedDocuments().single().localUri)
            assertEquals(path, driveRepository.downloadDocumentOnDemand("test-token", saved))
            assertEquals(1, localRepository.getAllReceiptsList().size)
            assertEquals(1, localRepository.getAllManagedDocuments().size)
            assertEquals(0, fakeDrive.uploadCalls)
            assertEquals(0, fakeDrive.indexWrites)
        } finally {
            published?.delete()
            previous.delete()
        }
    }

    @Test fun missingOrEmptyLegacyDownloadPreservesExistingBytesAndReferences() = runTest {
        val (receipt, previous) = legacyReceiptWithOriginal()
        val directory = java.io.File(context.filesDir, "managed_documents")
        val filesBefore = directory.listFiles()?.map { it.name }?.sorted().orEmpty()
        try {
            // Missing remote content, then an empty response: neither may publish a version.
            repeat(2) { attempt ->
                if (attempt == 1) fakeDrive.put("legacy-remote", "receipts", "Original.pdf", byteArrayOf())
                assertTrue(runCatching {
                    driveRepository.downloadDocumentOnDemand("test-token", receipt)
                }.isFailure)
                assertEquals(receipt, localRepository.getReceiptByInternalId(receipt.internalId))
                assertEquals("previous original bytes", previous.readText())
                assertTrue(localRepository.getAllManagedDocuments().isEmpty())
                assertEquals(filesBefore, directory.listFiles()?.map { it.name }?.sorted().orEmpty())
            }
            assertEquals(0, fakeDrive.uploadCalls)
            assertEquals(0, fakeDrive.indexWrites)
        } finally { previous.delete() }
    }

    private fun forcedOriginal(receipt: Receipt, file: java.io.File, bytes: ByteArray, index: Int = 0) = ManagedDocument(
        documentId = if (index == 0) StableDocumentIdentity.receiptDocumentId(receipt.internalId) else "forced-page-$index",
        receiptInternalId = receipt.internalId, propertyId = receipt.propertyId, unitId = receipt.unitId,
        localUri = file.absolutePath,
        storedFilename = if (index == 0) receipt.storedFilename!! else "Original-$index.pdf",
        originalFilename = "Original-$index.pdf",
        mimeType = "application/pdf", sha256 = StableDocumentIdentity.sha256(bytes), fileSizeBytes = bytes.size.toLong(),
        driveFileId = if (index == 0) receipt.driveFileId else "forced-remote-$index",
        extractedFieldsJson = JSONObject().put("_receiptOriginalOrder", index).toString()
    )

    @Test fun forcedDownloadPublishesVerifiedReplacementWhileKeepingCorruptLocalVersion() = runTest {
        val (receipt, previous) = legacyReceiptWithOriginal()
        val bytes = SyntheticDocumentFixtureFactory.create().first().bytes
        val original = forcedOriginal(receipt, previous, bytes)
        localRepository.upsertManagedDocument(original)
        fakeDrive.put(original.driveFileId!!, "receipts", original.storedFilename, bytes)
        var published: java.io.File? = null
        try {
            val path = driveRepository.downloadDocumentOnDemand("test-token", receipt, forceRemote = true)
            published = java.io.File(path)
            assertNotEquals(previous.absolutePath, path)
            assertEquals("previous original bytes", previous.readText())
            assertArrayEquals(bytes, published.readBytes())
            assertEquals(original.copy(localUri = path), localRepository.getManagedDocument(original.documentId))
            assertEquals(path, localRepository.getReceiptByInternalId(receipt.internalId)!!.imageUrl)
            assertEquals(0, fakeDrive.uploadCalls)
        } finally {
            published?.delete()
            previous.delete()
        }
    }

    @Test fun missingForcedRemoteOriginalKeepsLocalBytesAndAllReferences() = runTest {
        val (receipt, previous) = legacyReceiptWithOriginal()
        val original = forcedOriginal(receipt, previous, previous.readBytes())
        localRepository.upsertManagedDocument(original)
        try {
            assertTrue(runCatching {
                driveRepository.downloadDocumentOnDemand("test-token", receipt, forceRemote = true)
            }.isFailure)
            assertEquals("previous original bytes", previous.readText())
            assertEquals(original, localRepository.getManagedDocument(original.documentId))
            assertEquals(receipt, localRepository.getReceiptByInternalId(receipt.internalId))
            assertEquals(previous.absolutePath, driveRepository.downloadDocumentOnDemand("test-token", receipt))
            assertEquals(0, fakeDrive.uploadCalls)
        } finally { previous.delete() }
    }

    @Test fun failedSecondForcedPageDoesNotPublishAnyNewReferenceOrDeletePreviousPages() = runTest {
        val (receipt, previous) = legacyReceiptWithOriginal()
        val second = java.io.File(previous.parentFile, "Second.pdf").apply { writeText("second previous bytes") }
        val current = receipt.copy(imageUrl = ReceiptOriginalChain.encodePaths(listOf(previous.absolutePath, second.absolutePath)))
        database.receiptDao().insertReceipt(current)
        val fixtures = SyntheticDocumentFixtureFactory.create().take(2)
        val originals = listOf(previous, second).mapIndexed { index, file ->
            forcedOriginal(current, file, fixtures[index].bytes, index)
        }
        originals.forEach { localRepository.upsertManagedDocument(it) }
        fakeDrive.put(originals.first().driveFileId!!, "receipts", "Original.pdf", fixtures.first().bytes)
        try {
            assertTrue(runCatching {
                driveRepository.downloadDocumentOnDemand("test-token", current, forceRemote = true)
            }.isFailure)
            assertEquals("previous original bytes", previous.readText())
            assertEquals("second previous bytes", second.readText())
            assertEquals(current, localRepository.getReceiptByInternalId(current.internalId))
            assertEquals(originals.associateBy { it.documentId }, localRepository.getAllManagedDocuments().associateBy { it.documentId })
            assertEquals(0, fakeDrive.uploadCalls)
        } finally {
            java.io.File(context.filesDir, "managed_documents").deleteRecursively()
            previous.delete()
            second.delete()
        }
    }

    @Test fun multiPageDownloadKeepsRegistrationBetweenFilePublicationAndReferenceUpdates() = runTest {
        val (receipt, previous) = legacyReceiptWithOriginal()
        val second = java.io.File(previous.parentFile, "Coordinated-second.pdf").apply { writeText("second previous bytes") }
        val current = receipt.copy(imageUrl = ReceiptOriginalChain.encodePaths(listOf(previous.absolutePath, second.absolutePath)))
        database.receiptDao().insertReceipt(current)
        val fixtures = SyntheticDocumentFixtureFactory.create().take(2)
        val originals = listOf(previous, second).mapIndexed { index, file ->
            forcedOriginal(current, file, fixtures[index].bytes, index)
        }
        originals.forEachIndexed { index, document ->
            localRepository.upsertManagedDocument(document)
            fakeDrive.put(document.driveFileId!!, "receipts", document.storedFilename, fixtures[index].bytes)
        }
        val coordinator = OfflineBackupCoordinator.shared
        var blockedAtSecondPage: Boolean? = null
        var firstFilePublished = false
        var referencesStillOld = false
        val gateway = object : ManagedDocumentDriveGateway by fakeDrive {
            override suspend fun downloadFileBytes(accessToken: String, fileId: String): ByteArray? {
                if (fileId == originals[1].driveFileId) {
                    firstFilePublished = java.io.File(context.filesDir, "managed_documents").listFiles().orEmpty().any { it.isFile }
                    referencesStillOld = localRepository.getManagedDocument(originals[0].documentId) == originals[0]
                    blockedAtSecondPage = runCatching { coordinator.beginCapture() }.exceptionOrNull() is OfflineBackupValidationException
                }
                return fakeDrive.downloadFileBytes(accessToken, fileId)
            }
        }
        val repository = DrivePersistenceRepository(context, localRepository, gateway)
        val capture = coordinator.beginCapture()
        try {
            repository.downloadReceiptOriginals("test-token", current, originals, emptyList(), forceRemote = true)
            assertTrue(firstFilePublished)
            assertTrue(referencesStillOld)
            assertEquals(true, blockedAtSecondPage)
            val saved = originals.map { localRepository.getManagedDocument(it.documentId)!! }
            saved.forEachIndexed { index, document ->
                assertNotEquals(originals[index].localUri, document.localUri)
                assertArrayEquals(fixtures[index].bytes, java.io.File(document.localUri).readBytes())
            }
            assertEquals(saved.map { it.localUri }, ReceiptOriginalChain.paths(localRepository.getReceiptByInternalId(current.internalId)!!.imageUrl))
            assertEquals("previous original bytes", previous.readText())
            assertEquals("second previous bytes", second.readText())
            org.junit.Assert.assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(capture) }
            coordinator.completeCapture(coordinator.beginCapture())
        } finally {
            java.io.File(context.filesDir, "managed_documents").deleteRecursively()
            previous.delete(); second.delete()
        }
    }

    @Test fun receiptAttachmentSyncBackupAndDownloadRetainAllOriginalsAndStableMetadata() = runTest {
        val fixtures = SyntheticDocumentFixtureFactory.create().filter { it.number in setOf(1, 5) }
        val originals = fixtures.mapIndexed { index, fixture ->
            val source = java.io.File(context.cacheDir, fixture.filename).apply { writeBytes(fixture.bytes) }
            val original = ReceiptOriginalStorage(context).importOriginal(android.net.Uri.fromFile(source), fixture.filename)
            source.delete()
            original.copy(documentId = if (index == 0) "receipt:multi" else "multi-page-2", receiptInternalId = "multi",
                propertyId = "property-test", documentDate = "2026-10-08",
                extractedFieldsJson = JSONObject().put("_receiptOriginalOrder", index).toString())
        }
        val receipt = Receipt(internalId = "multi", aussteller = "Manuell", datum = "2026-10-08", uhrzeit = "", bruttobetrag = 100.0,
            hauptkategorie = "Renovierung", unterkategorie = "Material", kontoNr = "4800", beschreibung = "",
            propertyId = "property-test", imageUrl = originals.joinToString(",") { it.localUri }, driveFileId = "main-original")
        originals.forEach { localRepository.upsertManagedDocument(it, receipt) }
        val id = localRepository.insert(receipt).toInt()
        val saved = localRepository.getReceiptById(id)!!
        fakeDrive.put("main-original", "receipts", fixtures.first().filename, fixtures.first().bytes)
        assertTrue(driveRepository.syncReceiptOriginalAttachments("test-token", config, saved))
        assertEquals(1, fakeDrive.uploadCalls)
        assertTrue(driveRepository.syncReceiptOriginalAttachments("test-token", config, saved))
        assertEquals(1, fakeDrive.uploadCalls)
        assertEquals(1, localRepository.getDocumentsForReceipt("multi").size)
        val backup = SupplementalDriveBackup.createPayload(context, database)
        originals.forEach { java.io.File(it.localUri).delete() }
        localRepository.clearAllData()
        localRepository.insert(saved.copy(id = 0, imageUrl = ""))
        repeat(2) { SupplementalDriveBackup.restorePayload(context, database, backup) }
        val restored = localRepository.getReceiptByInternalId("multi")!!
        driveRepository.downloadReceiptOriginals("test-token", restored)
        val downloaded = ReceiptOriginalChain.ordered(localRepository.getAllManagedDocuments().filter { it.receiptInternalId == "multi" })
        assertEquals(2, downloaded.size)
        downloaded.forEachIndexed { index, document ->
            assertEquals(originals[index].documentId, document.documentId)
            assertEquals(fixtures[index].filename, document.originalFilename)
            assertEquals(fixtures[index].sha256, document.sha256)
            assertEquals(originals[index].mimeType, document.mimeType)
            assertArrayEquals(fixtures[index].bytes, java.io.File(document.localUri).readBytes())
        }
        val updatedReceipt = localRepository.getReceiptByInternalId("multi")!!
        assertEquals(downloaded.map { it.localUri }, updatedReceipt.imageUrl.split(','))
        driveRepository.downloadReceiptOriginals("test-token", updatedReceipt)
        assertEquals(downloaded, ReceiptOriginalChain.ordered(localRepository.getAllManagedDocuments().filter { it.receiptInternalId == "multi" }))
        downloaded.forEach { java.io.File(it.localUri).delete() }
    }

    @Test fun automaticReceiptMetadataRoundTripCarriesEveryOriginalWithoutLocalPathsOrOcrAndReadsOldBackups() {
        val originals = (0..1).map { order -> ManagedDocument(
            documentId = if (order == 0) "receipt:metadata" else "metadata-page-2", receiptInternalId = "metadata",
            propertyId = "property-test", originalFilename = "Seite ${order + 1}.${if (order == 0) "pdf" else "png"}",
            storedFilename = "managed-$order.${if (order == 0) "pdf" else "png"}",
            mimeType = if (order == 0) "application/pdf" else "image/png", sha256 = "$order".repeat(64), unitId = "stable-unit-id",
            fileSizeBytes = 100L + order, driveFileId = "remote-$order", driveFolderId = "folder",
            localUri = "/private/local/original-$order", ocrText = "LOCAL_OCR_SENTINEL",
            createdAt = "2026-10-08T00:00:00Z", updatedAt = "2026-10-08T00:00:00Z",
            extractedFieldsJson = JSONObject().put("_receiptOriginalOrder", order).toString()) }
        val persisted = PersistedReceipt(internalId = "metadata", displayId = "BELEG-1", originalDocuments = originals,
            aussteller = "Manuell", rechnungsnummer = "", datum = "2026-10-08", nettobetragCent = 10000,
            steuerbetragCent = 0, bruttobetragCent = 10000, hauptkategorie = "Renovierung", unterkategorie = "Material",
            wohneinheit = "", propertyId = "property-test", massnahme = "", positionen = emptyList(),
            zahlungsstatus = "BEZAHLT", zahlungsdatum = "2026-10-08", pruefstatus = "GEPRUEFT", exportstatus = "EXPORTBEREIT",
            createdAt = "2026-10-08T00:00:00Z", updatedAt = "2026-10-08T00:00:00Z", lastSyncedAt = null)
        val json = with(driveRepository) { persisted.toJson() }
        assertFalse(json.contains("/private/local"))
        assertFalse(json.contains("LOCAL_OCR_SENTINEL"))
        val parsed = driveRepository.parsePersistedReceipt(json)
        assertEquals(2, parsed.originalDocuments.size)
        parsed.originalDocuments.forEachIndexed { order, document ->
            assertEquals(originals[order].documentId, document.documentId)
            assertEquals(originals[order].sha256, document.sha256)
            assertEquals("stable-unit-id", document.unitId)
            assertEquals(originals[order].originalFilename, document.originalFilename)
            assertEquals(originals[order].mimeType, document.mimeType)
            assertEquals(originals[order].fileSizeBytes, document.fileSizeBytes)
            assertEquals(order, JSONObject(document.extractedFieldsJson).getInt("_receiptOriginalOrder"))
            assertTrue(document.localUri.isEmpty())
        }
        assertEquals(json, with(driveRepository) { parsed.toJson() })
        val legacy = JSONObject(json).apply { remove("originalDocuments") }
        assertTrue(driveRepository.parsePersistedReceipt(legacy.toString()).originalDocuments.isEmpty())
    }

    @Test fun newReceiptIndexCannotOverrideBackupOriginalMetadataAndExistingUnitCorrectionStillWins() = runTest {
        val receipt = Receipt(internalId = "core-original", aussteller = "Manuell", datum = "2026-10-08", uhrzeit = "",
            bruttobetrag = 100.0, hauptkategorie = "Renovierung", unterkategorie = "Material", kontoNr = "4800",
            beschreibung = "", propertyId = "property-test")
        val original = ManagedDocument(documentId = "receipt:core-original", propertyId = receipt.propertyId,
            receiptInternalId = receipt.internalId, unitId = "stable-unit-id", originalFilename = "Original.pdf",
            storedFilename = "Original.pdf", mimeType = "application/pdf", sha256 = "1".repeat(64),
            driveFileId = "remote-original", createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z",
            extractedFieldsJson = "{\"_receiptOriginalOrder\":0}")
        val before = localRepository.getAllManagedDocuments().associateBy { it.documentId }
        val resolution = localRepository.upsertRestoredReceipt(receipt)
        assertNotEquals(original.updatedAt, localRepository.getManagedDocument(original.documentId)!!.updatedAt)
        driveRepository.restoreReceiptOriginalMetadata(receipt.internalId, resolution.receipt, listOf(original), RestoreMode.MERGE, before)
        assertEquals(original, localRepository.getManagedDocument(original.documentId))
        repeat(2) {
            val existing = localRepository.getAllManagedDocuments().associateBy { it.documentId }
            val repeated = localRepository.upsertRestoredReceipt(receipt)
            driveRepository.restoreReceiptOriginalMetadata(receipt.internalId, repeated.receipt, listOf(original), RestoreMode.MERGE, existing)
            assertEquals(original, localRepository.getManagedDocument(original.documentId))
        }
        val corrected = original.copy(unitId = null, updatedAt = "2026-10-08T00:00:00Z")
        localRepository.upsertManagedDocument(corrected)
        driveRepository.restoreReceiptOriginalMetadata(receipt.internalId, resolution.receipt, listOf(original), RestoreMode.MERGE,
            localRepository.getAllManagedDocuments().associateBy { it.documentId })
        assertEquals(corrected, localRepository.getManagedDocument(original.documentId))
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

internal enum class FakeDriveFailure { NONE, HTTP_404, HTTP_429, HTTP_503, TIMEOUT }

internal data class FakeManagedDriveFile(
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

internal class ManagedDocumentFakeDrive : ManagedDocumentDriveGateway {
    private val jsonFiles = linkedMapOf<String, String>()
    private val jsonEntities = linkedMapOf<Pair<String, String>, String>()
    fun putJson(id: String, folderId: String, entity: String, json: String) {
        jsonFiles[id] = json; jsonEntities[folderId to entity] = id
    }
    fun json(id: String): String = requireNotNull(jsonFiles[id])
    override suspend fun findJsonFile(accessToken: String, folderId: String, entityType: String): com.example.api.DriveFile? =
        jsonEntities[folderId to entityType]?.let { com.example.api.DriveFile(it, "$it.json") }
    override suspend fun downloadJson(accessToken: String, fileId: String): String = json(fileId)
    override suspend fun findReceiptJsonFile(accessToken: String, folderId: String, internalId: String): String? =
        jsonFiles.keys.firstOrNull { it == "meta-$internalId" }
    override suspend fun writeReceiptJson(accessToken: String, fileId: String?, folderId: String, filename: String,
        json: String, appProperties: Map<String, String>): DriveFileResult {
        val id = fileId ?: "meta-${appProperties.getValue("receiptInternalId")}"
        jsonFiles[id] = json
        return DriveFileResult(true, id)
    }
    override suspend fun tombstoneJsonFiles(accessToken: String, systemFolderId: String): List<String> =
        jsonEntities[systemFolderId to "tombstone"]?.let { listOf(json(it)) }.orEmpty()

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
