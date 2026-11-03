package com.example.data

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.api.DriveSyncCheck
import com.example.api.DriveSyncOutcome
import com.example.api.DriveSyncPhase
import com.example.api.GoogleDriveClient
import com.example.comparison.ComparisonDriveHttpFixture
import com.example.ui.DriveConnectionRegressionTest
import com.example.ui.IsolatedAppDatabaseRule
import com.example.ui.ReceiptViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Existing Drive stock, including two original attachments. All data and HTTP are synthetic. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [DriveConnectionRegressionTest.AuthShadow::class])
class DriveDocumentIndexRegressionTest {
    @get:Rule val isolation = IsolatedAppDatabaseRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val db get() = AppDatabase.getDatabase(app, CoroutineScope(Dispatchers.IO))
    private val repo get() = ReceiptRepository(db.receiptDao(), db.propertyDao(), db.receiptEntityDao(),
        db.belegDao(), db.exportAuditDao(), db.receiptDocumentDao(), db.managedDocumentDao())
    private val drive get() = DrivePersistenceRepository(app, repo)
    private val prefs get() = app.getSharedPreferences("google_drive_prefs", 0)
    private val transport = GoogleDriveClient::class.java.getDeclaredField("client").apply { isAccessible = true }
    private val token = "synthetic-native"
    private lateinit var previous: OkHttpClient
    private lateinit var remote: ComparisonDriveHttpFixture
    private lateinit var config: DriveAppConfig
    private val models = mutableListOf<ReceiptViewModel>()
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun setup() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        prefs.edit().clear().putBoolean("user_disconnected", true).putBoolean("auto_backup", false).commit()
        DriveConnectionRegressionTest.AuthShadow.failure = null
        DriveConnectionRegressionTest.AuthShadow.calls.set(0)
        DriveConnectionRegressionTest.AuthShadow.clears.set(0)
        DriveConnectionRegressionTest.AuthShadow.expireFirst = false
        File(app.noBackupFilesDir, "drive-pending-writes").deleteRecursively()
        previous = transport.get(null) as OkHttpClient
        remote = ComparisonDriveHttpFixture()
        transport.set(null, OkHttpClient.Builder().addInterceptor(remote).build())
        GoogleDriveClient.clearFolderCache()
        config = when (val result = drive.initializeDriveStorage(token)) {
            is DriveInitializationResult.SuccessCreatedNew -> result.config
            is DriveInitializationResult.SuccessLoadedExisting -> result.config
            is DriveInitializationResult.Failure -> error(result.error)
        }
        repo.restorePropertyMetadata(PropertyMetadata(name = "Synthetic document index"))
    }

    @After fun cleanup() = runBlocking {
        stores.forEach { it.clear() }
        models.forEach { it.viewModelScope.coroutineContext[Job]?.join() }
        transport.set(null, previous)
        GoogleDriveClient.clearFolderCache()
        Dispatchers.resetMain()
    }

    private suspend fun connectedModel(): ReceiptViewModel {
        val store = ViewModelStore().also { stores += it }
        val vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[ReceiptViewModel::class.java]
            .also { models += it }
        vm.connectDrive("document-index-synthetic@example.invalid")
        awaitJob(vm, "driveInitializationJob")
        assertTrue(vm.isDriveConnected.value)
        return vm
    }

    private suspend fun awaitJob(vm: ReceiptViewModel, name: String) {
        val field = ReceiptViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }
        withTimeout(60_000) { (field.get(vm) as? Job)?.join() }
    }

    private suspend fun sync(vm: ReceiptViewModel) {
        remote.requests.clear()
        vm.syncAllToDrive()
        awaitJob(vm, "driveSyncJob")
    }

    private fun mutations() = remote.requests.filter { it.method != "GET" }
    private fun indexFile() = remote.entries.values.single { it.properties["entityType"] == "documentIndex" }

    private suspend fun seedTwentyTwoReceiptsWithTwentyFourOriginals() {
        val index = JSONArray()
        repeat(22) { zero ->
            val i = zero + 1
            val receiptId = "document-index-receipt-$i"
            val primaryId = "document-index-original-$i"
            val primaryBytes = originalBytes(i)
            val row = Receipt(id = i, internalId = receiptId, displayId = "BLG-2026-${i.toString().padStart(6, '0')}",
                aussteller = "Synthetic vendor $i", datum = "2026-01-01", uhrzeit = "12:00", bruttobetrag = 119.0,
                hauptkategorie = "Instandhaltung", unterkategorie = "Material", kontoNr = "4800",
                beschreibung = "Synthetic document-index stock", imageUrl = "", driveFileId = primaryId,
                driveFolderId = config.receiptsFolderId, storedFilename = "synthetic-$i.png",
                originalMimeType = "image/png", fileSizeBytes = primaryBytes.size.toLong(), syncStatus = "SYNCED",
                driveRevision = 0, isArchivedToDrive = true)
            repo.insert(row)
            addOriginal(row, "receipt:$receiptId", primaryId, "synthetic-$i.png", primaryBytes, 0)
            if (i <= 2) {
                addOriginal(row, "attachment:$receiptId:1", "document-index-attachment-$i",
                    "synthetic-$i-attachment.png", originalBytes(i + 22), 1)
            }
            val metadataId = requireNotNull(drive.upsertReceiptMetadata(token, config, row))
            repo.insert(row.copy(driveMetadataFileId = metadataId, driveRevision = 1))
            index.put(JSONObject().put("internalId", receiptId).put("displayId", row.displayId)
                .put("mainDriveFileId", primaryId).put("metadataFileId", metadataId).put("syncStatus", "SYNCED")
                .put("updatedAt", "2026-01-01T00:00:00Z"))
        }
        remote.put("document-index-receipts", "receipt-index.json", config.systemFolderId,
            JSONObject().put("version", 1).put("entries", index).toString(), "receiptIndex")
        repo.allReceipts.first().mapNotNull { it.driveMetadataFileId }.forEach { DrivePendingWrites(app).completedReceiptIndex(it) }
        assertEquals(24, repo.getAllManagedDocuments().size)
        assertTrue(repo.getAllManagedDocuments().all { !it.receiptInternalId.isNullOrBlank() })
    }

    private fun originalBytes(value: Int) =
        byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) + ByteArray(32) { value.toByte() }

    private suspend fun addOriginal(receipt: Receipt, documentId: String, fileId: String,
                                    filename: String, bytes: ByteArray, order: Int) {
        remote.put(fileId, filename, config.receiptsFolderId, mime = "image/png").bytes = bytes
        repo.insertDocument(ReceiptDocumentReference(id = documentId, receiptInternalId = receipt.internalId,
            driveFileId = fileId, driveFolderId = config.receiptsFolderId, filename = filename, mimeType = "image/png",
            sizeBytes = bytes.size.toLong(), role = if (order == 0) "MAIN_RECEIPT" else "ATTACHMENT",
            createdAt = "2026-01-01T00:00:00Z"))
        repo.upsertManagedDocument(ManagedDocument(documentId = documentId, propertyId = receipt.propertyId,
            receiptInternalId = receipt.internalId, documentType = ManagedDocumentType.RECHNUNG.name,
            documentDate = receipt.datum, title = "Synthetic reviewed document", originalFilename = filename,
            storedFilename = filename, mimeType = "image/png", driveFileId = fileId,
            driveFolderId = config.receiptsFolderId, sha256 = StableDocumentIdentity.sha256(bytes),
            fileSizeBytes = bytes.size.toLong(), createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z",
            reviewStatus = DocumentReviewStatus.GEPRUEFT.name, source = DocumentSource.RECEIPT.name,
            extractedFieldsJson = JSONObject().put("_receiptOriginalOrder", order).toString()))
    }

    private suspend fun establishedCloudStock(): ReceiptViewModel {
        seedTwentyTwoReceiptsWithTwentyFourOriginals()
        val vm = connectedModel()
        sync(vm)
        assertNull(vm.driveSyncReport.value?.toJson(), vm.driveSyncError.value)
        assertEquals(24, JSONObject(indexFile().bytes.toString(Charsets.UTF_8)).getJSONArray("documents").length())
        return vm
    }

    @Test fun completeFreshRestoreWithColdJournalAndMetadataEditCompletesAllRemainingPhases() = runBlocking {
        val vm = establishedCloudStock()
        val expectedDocuments = repo.getAllManagedDocuments().associateBy { it.documentId }
        val originalBytes = remote.entries.values.filter { it.mime == "image/png" }.associate { it.id to it.bytes.copyOf() }
        val supplemental = remote.entries.values.single { it.properties["entityType"] == "supplementalBackup" }.bytes.copyOf()
        // A new installation: the public full-restore coordinator snapshots local rows before core.
        withContext(Dispatchers.IO) { db.clearAllTables() }
        val documentsBeforeCore = repo.getAllManagedDocuments().associateBy { it.documentId }
        assertTrue(documentsBeforeCore.isEmpty())
        val core = drive.executeFullDriveRestore(token, config, RestoreMode.MERGE)
        assertTrue(core.toString(), core.isSuccess)
        SupplementalDriveBackup.restorePayload(app, db, JSONObject(supplemental.toString(Charsets.UTF_8)),
            restoreMode = RestoreMode.MERGE, localDocumentsBeforeCore = documentsBeforeCore)
        assertEquals(expectedDocuments.keys, repo.getAllManagedDocuments().map { it.documentId }.toSet())
        repo.getAllManagedDocuments().forEach { document ->
            val expected = expectedDocuments.getValue(document.documentId)
            assertEquals(expected.documentDate, document.documentDate)
            assertEquals(expected.reviewStatus, document.reviewStatus)
            assertEquals(expected.sha256, document.sha256)
        }
        File(app.noBackupFilesDir, "drive-pending-writes").deleteRecursively()
        remote.omitV3Etags = true
        val edited = repo.getReceiptById(3)!!.copy(aussteller = "Synthetic local metadata edit")
        repo.insert(edited)
        sync(vm)
        assertNull(vm.driveSyncReport.value?.toJson(), vm.driveSyncError.value)
        val report = requireNotNull(vm.driveSyncReport.value)
        for (phase in listOf(DriveSyncPhase.DOCUMENT_INDEX, DriveSyncPhase.SUPPLEMENTAL, DriveSyncPhase.COMPLETION)) {
            assertTrue("Required phase missing: $phase", report.steps.any {
                it.phase == phase && it.outcome in setOf(DriveSyncOutcome.SUCCESS, DriveSyncOutcome.UNCHANGED)
            })
        }
        assertEquals(22, repo.allReceipts.first().size)
        assertEquals(24, repo.getAllManagedDocuments().size)
        originalBytes.forEach { (id, bytes) -> assertArrayEquals(id, bytes, remote.entries.getValue(id).bytes) }
        val beforeNoOp = remote.entries.mapValues { it.value.bytes.copyOf() }
        val rowsBeforeNoOp = repo.allReceipts.first()
        sync(vm)
        assertNull(vm.driveSyncReport.value?.toJson(), vm.driveSyncError.value)
        assertTrue(mutations().isEmpty())
        assertEquals(beforeNoOp.keys, remote.entries.keys)
        beforeNoOp.forEach { (id, bytes) -> assertArrayEquals(id, bytes, remote.entries.getValue(id).bytes) }
        assertEquals(rowsBeforeNoOp, repo.allReceipts.first())
    }

    @Test fun newerUnknownRemoteDocumentIndexCannotBeOverwrittenByLocalReceiptSynchronization() = runBlocking {
        val vm = establishedCloudStock()
        val remoteIndex = indexFile()
        val json = JSONObject(remoteIndex.bytes.toString(Charsets.UTF_8))
        json.getJSONArray("documents").getJSONObject(0)
            .put("documentDate", "2026-02-01").put("updatedAt", "2026-02-01T00:00:00Z")
        remoteIndex.bytes = json.toString().toByteArray()
        remoteIndex.revision++
        File(app.noBackupFilesDir, "drive-pending-writes").deleteRecursively()
        val expectedBytes = remoteIndex.bytes.copyOf()
        val edited = repo.getReceiptById(3)!!.copy(aussteller = "Synthetic independent local edit")
        repo.insert(edited)
        val expectedRows = repo.getAllManagedDocuments()
        sync(vm)
        assertNotNull(vm.driveSyncError.value)
        assertArrayEquals(expectedBytes, remoteIndex.bytes)
        assertEquals(expectedRows, repo.getAllManagedDocuments())
        val failure = requireNotNull(vm.driveSyncReport.value).steps.firstNotNullOfOrNull { step ->
            step.failure?.takeIf { it.check == DriveSyncCheck.BACKUP_PREIMAGE_CONFLICT }
        }
        assertNotNull(failure)
        assertEquals(edited.aussteller, repo.getReceiptById(3)!!.aussteller)
        assertEquals(24, repo.getAllManagedDocuments().size)
    }

    @Test fun verifiedMissingHashEnrichmentCompletesExistingIndexAndSupplementalWithoutChangingOriginals() = runBlocking {
        val vm = establishedCloudStock()
        val receipt = repo.getReceiptById(22)!!
        val original = repo.getManagedDocument("receipt:${receipt.internalId}")!!
        val expectedHash = original.sha256
        val originalFiles = remote.entries.values.filter { it.mime == "image/png" }
            .associate { it.id to it.bytes.copyOf() }
        // Legacy index and supplemental copy predate an independently saved original-chain hash.
        // Neither an absent installation journal nor the safe enrichment is an authored edit.
        db.managedDocumentDao().upsert(original.copy(sha256 = ""))
        for (entity in listOf("documentIndex", "supplementalBackup")) {
            val file = remote.entries.values.single { it.properties["entityType"] == entity }
            val json = JSONObject(file.bytes.toString(Charsets.UTF_8))
            val documents = json.getJSONArray(if (entity == "documentIndex") "documents" else "managedDocuments")
            val entry = (0 until documents.length()).map { documents.getJSONObject(it) }
                .single { it.getString("documentId") == original.documentId }
            entry.put("sha256", "")
            file.bytes = json.toString().toByteArray()
            file.revision++
        }
        val saved = JSONObject(remote.entries.getValue(receipt.driveMetadataFileId!!).bytes.toString(Charsets.UTF_8))
        assertEquals(expectedHash, saved.getJSONArray("originalDocuments").getJSONObject(0).getString("sha256"))
        File(app.noBackupFilesDir, "drive-pending-writes").deleteRecursively()
        remote.omitV3Etags = true
        repo.insert(repo.getReceiptById(3)!!.copy(aussteller = "Synthetic independent metadata edit"))
        sync(vm)
        val enriched = repo.getManagedDocument(original.documentId)!!
        assertEquals("The existing original was independently verified", expectedHash, enriched.sha256)
        assertEquals("Hash evidence is not a document authoring timestamp", original.updatedAt, enriched.updatedAt)
        assertNull(vm.driveSyncReport.value?.toJson(), vm.driveSyncError.value)
        val report = requireNotNull(vm.driveSyncReport.value)
        for (phase in listOf(DriveSyncPhase.DOCUMENT_INDEX, DriveSyncPhase.SUPPLEMENTAL, DriveSyncPhase.COMPLETION)) {
            assertTrue("Required phase missing: $phase", report.steps.any {
                it.phase == phase && it.outcome in setOf(DriveSyncOutcome.SUCCESS, DriveSyncOutcome.UNCHANGED)
            })
        }
        assertEquals(22, repo.allReceipts.first().size)
        assertEquals(24, repo.getAllManagedDocuments().size)
        for (entity in listOf("documentIndex", "supplementalBackup")) {
            val file = remote.entries.values.single { it.properties["entityType"] == entity }
            val json = JSONObject(file.bytes.toString(Charsets.UTF_8))
            val documents = json.getJSONArray(if (entity == "documentIndex") "documents" else "managedDocuments")
            assertEquals(24, documents.length())
            val entry = (0 until documents.length()).map { documents.getJSONObject(it) }
                .single { it.getString("documentId") == original.documentId }
            assertEquals(expectedHash, entry.getString("sha256"))
            assertEquals(original.updatedAt, entry.getString("updatedAt"))
        }
        originalFiles.forEach { (id, bytes) -> assertArrayEquals(id, bytes, remote.entries.getValue(id).bytes) }
        assertFalse(remote.requests.any { it.method == "POST" ||
            (it.method in setOf("PUT", "PATCH") && it.url.encodedPath.substringAfterLast('/') in originalFiles) })
        val beforeSecond = remote.entries.mapValues { it.value.bytes.copyOf() }
        val documentRows = repo.getAllManagedDocuments()
        sync(vm)
        assertNull(vm.driveSyncReport.value?.toJson(), vm.driveSyncError.value)
        assertTrue(mutations().isEmpty())
        assertEquals(documentRows, repo.getAllManagedDocuments())
        assertEquals(beforeSecond.keys, remote.entries.keys)
        beforeSecond.forEach { (id, bytes) -> assertArrayEquals(id, bytes, remote.entries.getValue(id).bytes) }
    }

    @Test fun blankRemoteHashCannotBeFilledFromServerReachabilityWithoutIndependentRecoveryProof() = runBlocking {
        establishedCloudStock()
        val original = repo.getManagedDocument("receipt:document-index-receipt-22")!!
        val file = indexFile()
        val before = JSONObject(file.bytes.toString(Charsets.UTF_8))
        val entry = (0 until before.getJSONArray("documents").length()).map { before.getJSONArray("documents").getJSONObject(it) }
            .single { it.getString("documentId") == original.documentId }
        entry.put("sha256", "")
        file.bytes = before.toString().toByteArray(); file.revision++
        val bytes = file.bytes.copyOf()
        val candidate = JSONObject(before.toString())
        (0 until candidate.getJSONArray("documents").length()).map { candidate.getJSONArray("documents").getJSONObject(it) }
            .single { it.getString("documentId") == original.documentId }.put("sha256", original.sha256)
        File(app.noBackupFilesDir, "drive-pending-writes").deleteRecursively()
        remote.requests.clear()
        try {
            GoogleDriveClient.withSyncReadProof(token) {
                // This populates the ordinary run fingerprint cache, but supplies no independent
                // original history and must not populate the backup-enrichment attestation.
                assertEquals(original.sha256,
                    GoogleDriveClient.getOriginalFingerprintChecked(token, original.driveFileId!!).sha256)
                GoogleDriveClient.updateSyncJson(token, file.id, candidate.toString(), conflictContext = app)
            }
            fail("Reachable current bytes alone cannot certify a legacy original")
        } catch (expected: com.example.api.DriveSyncConflict) {
            assertEquals(DriveSyncCheck.BACKUP_PREIMAGE_CONFLICT, expected.check)
        }
        assertArrayEquals(bytes, file.bytes)
        assertTrue(mutations().isEmpty())
    }

    @Test fun recoveredOriginalDoesNotAuthorizeReplacingAnExistingRemoteIndexHash() = runBlocking {
        val vm = establishedCloudStock()
        val original = repo.getManagedDocument("receipt:document-index-receipt-22")!!
        db.managedDocumentDao().upsert(original.copy(sha256 = ""))
        val file = indexFile()
        val before = JSONObject(file.bytes.toString(Charsets.UTF_8))
        (0 until before.getJSONArray("documents").length()).map { before.getJSONArray("documents").getJSONObject(it) }
            .single { it.getString("documentId") == original.documentId }.put("sha256", "f".repeat(64))
        file.bytes = before.toString().toByteArray(); file.revision++
        val bytes = file.bytes.copyOf()
        File(app.noBackupFilesDir, "drive-pending-writes").deleteRecursively()
        sync(vm)
        assertEquals(original.sha256, repo.getManagedDocument(original.documentId)!!.sha256)
        assertNotNull(vm.driveSyncError.value)
        assertArrayEquals(bytes, file.bytes)
        assertTrue(mutations().isEmpty())
    }

    @Test fun recoveredHashProofCannotSurviveAConcurrentOriginalVersionChange() = runBlocking {
        rejectChangedOriginalAfterRecovery(move = false)
    }

    @Test fun recoveredOriginalHashCannotAuthorizeAnUnknownReviewChange() = runBlocking {
        val vm = establishedCloudStock()
        val original = repo.getManagedDocument("receipt:document-index-receipt-22")!!
        db.managedDocumentDao().upsert(original.copy(sha256 = ""))
        val file = indexFile()
        val json = JSONObject(file.bytes.toString(Charsets.UTF_8))
        (0 until json.getJSONArray("documents").length()).map { json.getJSONArray("documents").getJSONObject(it) }
            .single { it.getString("documentId") == original.documentId }
            .put("sha256", "").put("reviewStatus", DocumentReviewStatus.PRUEFEN.name)
        file.bytes = json.toString().toByteArray(); file.revision++
        val bytes = file.bytes.copyOf()
        File(app.noBackupFilesDir, "drive-pending-writes").deleteRecursively()
        sync(vm)
        assertEquals(original.sha256, repo.getManagedDocument(original.documentId)!!.sha256)
        assertEquals(DocumentReviewStatus.GEPRUEFT.name, repo.getManagedDocument(original.documentId)!!.reviewStatus)
        assertNotNull(vm.driveSyncError.value)
        assertArrayEquals(bytes, file.bytes)
        assertTrue(mutations().isEmpty())
    }

    @Test fun recoveredHashProofCannotSurviveAChangedOriginalBinding() = runBlocking {
        rejectChangedOriginalAfterRecovery(move = true)
    }

    @Test fun interruptedIndexUpdateReestablishesEnrichmentProofAfterRepositoryAndScopeRestart() = runBlocking {
        val vm = establishedCloudStock()
        val original = repo.getManagedDocument("receipt:document-index-receipt-22")!!
        val originals = remote.entries.values.filter { it.mime == "image/png" }.associate { it.id to it.bytes.copyOf() }
        db.managedDocumentDao().upsert(original.copy(sha256 = ""))
        for (entity in listOf("documentIndex", "supplementalBackup")) {
            val file = remote.entries.values.single { it.properties["entityType"] == entity }
            val json = JSONObject(file.bytes.toString(Charsets.UTF_8))
            val documents = json.getJSONArray(if (entity == "documentIndex") "documents" else "managedDocuments")
            (0 until documents.length()).map { documents.getJSONObject(it) }
                .single { it.getString("documentId") == original.documentId }.put("sha256", "")
            file.bytes = json.toString().toByteArray(); file.revision++
        }
        File(app.noBackupFilesDir, "drive-pending-writes").deleteRecursively()
        remote.omitV3Etags = true
        val indexId = indexFile().id
        val interrupted = java.util.concurrent.atomic.AtomicBoolean()
        transport.set(null, OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val request = chain.request()
            if (request.method in setOf("PATCH", "PUT") && request.url.encodedPath.endsWith("/$indexId") &&
                request.url.queryParameter("uploadType") == "media" && !interrupted.getAndSet(true)) {
                okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(503)
                    .message("Synthetic interrupted index transfer").body(okhttp3.ResponseBody.create(null, "{}")).build()
            } else chain.proceed(request)
        }.addInterceptor(remote).build())
        val stamp = prefs.getString("last_stammdaten_backup_time", null)
        sync(vm)
        assertTrue("The first run must reach the conditional index update", interrupted.get())
        assertNotNull(vm.driveSyncError.value)
        assertEquals(stamp, prefs.getString("last_stammdaten_backup_time", null))
        assertEquals(original.sha256, repo.getManagedDocument(original.documentId)!!.sha256)
        assertEquals(original.updatedAt, repo.getManagedDocument(original.documentId)!!.updatedAt)
        val originalIds = remote.entries.keys.toSet()
        transport.set(null, OkHttpClient.Builder().addInterceptor(remote).build())
        GoogleDriveClient.clearFolderCache()
        val restarted = connectedModel()
        sync(restarted)
        assertNull(restarted.driveSyncReport.value?.toJson(), restarted.driveSyncError.value)
        assertEquals(originalIds, remote.entries.keys)
        for (entity in listOf("documentIndex", "supplementalBackup")) {
            val file = remote.entries.values.single { it.properties["entityType"] == entity }
            val json = JSONObject(file.bytes.toString(Charsets.UTF_8))
            val documents = json.getJSONArray(if (entity == "documentIndex") "documents" else "managedDocuments")
            val entry = (0 until documents.length()).map { documents.getJSONObject(it) }
                .single { it.getString("documentId") == original.documentId }
            assertEquals(original.sha256, entry.getString("sha256"))
            assertEquals(original.updatedAt, entry.getString("updatedAt"))
        }
        originals.forEach { (id, bytes) -> assertArrayEquals(id, bytes, remote.entries.getValue(id).bytes) }
        sync(restarted)
        assertNull(restarted.driveSyncReport.value?.toJson(), restarted.driveSyncError.value)
        assertTrue(mutations().isEmpty())
    }

    private suspend fun rejectChangedOriginalAfterRecovery(move: Boolean) {
        establishedCloudStock()
        val original = repo.getManagedDocument("receipt:document-index-receipt-22")!!
        db.managedDocumentDao().upsert(original.copy(sha256 = ""))
        val file = indexFile()
        val before = JSONObject(file.bytes.toString(Charsets.UTF_8))
        (0 until before.getJSONArray("documents").length()).map { before.getJSONArray("documents").getJSONObject(it) }
            .single { it.getString("documentId") == original.documentId }.put("sha256", "")
        file.bytes = before.toString().toByteArray(); file.revision++
        val bytes = file.bytes.copyOf()
        File(app.noBackupFilesDir, "drive-pending-writes").deleteRecursively()
        remote.requests.clear()
        try {
            GoogleDriveClient.withSyncReadProof(token) {
                drive.validateSynchronizationInventory(token, config, repo.allReceipts.first())
                assertEquals(original.sha256, repo.getManagedDocument(original.documentId)!!.sha256)
                val source = remote.entries.getValue(original.driveFileId!!)
                if (move) source.parent = config.exportsFolderId
                else source.bytes = source.bytes.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
                source.revision++
                assertFalse("A changed source cannot authorize an index mutation", drive.updateManagedDocumentIndex(token, config))
            }
        } catch (expected: com.example.api.DriveAccessFailure) {
            // The exact namespace/content conflict remains controlled, never an unconditional write.
        }
        assertArrayEquals(bytes, file.bytes)
        assertTrue(mutations().isEmpty())
    }

    @Test fun knownOriginalHashWithoutRecoveryFlagFillsMissingIndexHashAndSize() = runBlocking {
        val vm = establishedCloudStock()
        val original = repo.getManagedDocument("receipt:document-index-receipt-22")!!
        for (entity in listOf("documentIndex", "supplementalBackup")) {
            val file = remote.entries.values.single { it.properties["entityType"] == entity }
            val root = JSONObject(file.bytes.toString(Charsets.UTF_8))
            val documents = root.getJSONArray(if (entity == "documentIndex") "documents" else "managedDocuments")
            (0 until documents.length()).map { documents.getJSONObject(it) }
                .single { it.getString("documentId") == original.documentId }
                .put("sha256", "").put("fileSizeBytes", 0)
            file.bytes = root.toString().toByteArray(); file.revision++
        }
        File(app.noBackupFilesDir, "drive-pending-writes").deleteRecursively()
        assertEquals(original.sha256, repo.getManagedDocument(original.documentId)!!.sha256)
        sync(vm)
        assertNull(vm.driveSyncReport.value?.toJson(), vm.driveSyncError.value)
        assertEquals(original, repo.getManagedDocument(original.documentId))
        for (entity in listOf("documentIndex", "supplementalBackup")) {
            val file = remote.entries.values.single { it.properties["entityType"] == entity }
            val root = JSONObject(file.bytes.toString(Charsets.UTF_8))
            val documents = root.getJSONArray(if (entity == "documentIndex") "documents" else "managedDocuments")
            val entry = (0 until documents.length()).map { documents.getJSONObject(it) }
                .single { it.getString("documentId") == original.documentId }
            assertEquals(original.sha256, entry.getString("sha256"))
            assertEquals(original.fileSizeBytes, entry.getLong("fileSizeBytes"))
            assertEquals(original.updatedAt, entry.getString("updatedAt"))
        }
        sync(vm)
        assertNull(vm.driveSyncReport.value?.toJson(), vm.driveSyncError.value)
        assertTrue(mutations().isEmpty())
    }

    @Test fun independentHashProofCannotDiscardUnknownRemoteDocumentFields() = runBlocking {
        val vm = establishedCloudStock()
        val original = repo.getManagedDocument("receipt:document-index-receipt-22")!!
        db.managedDocumentDao().upsert(original.copy(sha256 = ""))
        val file = indexFile()
        val root = JSONObject(file.bytes.toString(Charsets.UTF_8))
        (0 until root.getJSONArray("documents").length()).map { root.getJSONArray("documents").getJSONObject(it) }
            .single { it.getString("documentId") == original.documentId }
            .put("sha256", "").put("PrivateSyntheticOwner_314159", "must survive unchanged")
        file.bytes = root.toString().toByteArray(); file.revision++
        val before = file.bytes.copyOf()
        File(app.noBackupFilesDir, "drive-pending-writes").deleteRecursively()
        sync(vm)
        assertEquals(original.sha256, repo.getManagedDocument(original.documentId)!!.sha256)
        assertNotNull(vm.driveSyncError.value)
        assertArrayEquals(before, file.bytes)
        assertTrue(mutations().isEmpty())
        val report = requireNotNull(vm.driveSyncReport.value).toJson()
        assertFalse(report.contains("PrivateSyntheticOwner_314159"))
        assertFalse(report.contains("must survive unchanged"))
    }

    @Test fun independentHashProofIsNotReusedAfterTokenOrAccountChange() = runBlocking {
        establishedCloudStock()
        val original = repo.getManagedDocument("receipt:document-index-receipt-22")!!
        db.managedDocumentDao().upsert(original.copy(sha256 = ""))
        val file = indexFile()
        val root = JSONObject(file.bytes.toString(Charsets.UTF_8))
        (0 until root.getJSONArray("documents").length()).map { root.getJSONArray("documents").getJSONObject(it) }
            .single { it.getString("documentId") == original.documentId }.put("sha256", "")
        file.bytes = root.toString().toByteArray(); file.revision++
        val before = file.bytes.copyOf()
        File(app.noBackupFilesDir, "drive-pending-writes").deleteRecursively()
        remote.requests.clear()
        try {
            GoogleDriveClient.withSyncReadProof(token) {
                drive.validateSynchronizationInventory(token, config, repo.allReceipts.first())
                assertEquals(original.sha256, repo.getManagedDocument(original.documentId)!!.sha256)
                assertFalse("Another account must establish its own independent proof",
                    drive.updateManagedDocumentIndex("synthetic-different-account", config))
            }
        } catch (expected: com.example.api.DriveAccessFailure) { /* controlled refusal */ }
        assertArrayEquals(before, file.bytes)
        assertTrue(mutations().isEmpty())
    }

    @Test fun mixedLegacyStockKeepsEighteenUnprovedHashesBlankAndCompletesTheVerifiedEnrichment() = runBlocking {
        val vm = establishedCloudStock()
        val rows = repo.allReceipts.first()
        val legacyReceiptIds = rows.filter { it.id <= 16 }.map { it.internalId }.toSet()
        val recoveredReceipt = repo.getReceiptById(22)!!
        val documents = repo.getAllManagedDocuments()
        val blankDocuments = documents.filter {
            it.receiptInternalId in legacyReceiptIds || it.receiptInternalId == recoveredReceipt.internalId
        }.associateBy { it.documentId }
        assertEquals(19, blankDocuments.size)
        val neverCertify = blankDocuments.keys - "receipt:${recoveredReceipt.internalId}"
        assertEquals(18, neverCertify.size)
        val originalBytes = remote.entries.values.filter { it.mime == "image/png" }.associate { it.id to it.bytes.copyOf() }
        blankDocuments.values.forEach { db.managedDocumentDao().upsert(it.copy(sha256 = "")) }
        rows.filter { it.internalId in legacyReceiptIds }.forEach { receipt ->
            val file = remote.entries.getValue(receipt.driveMetadataFileId!!)
            val legacy = JSONObject(file.bytes.toString(Charsets.UTF_8)).put("schemaVersion", 1)
            legacy.remove("originalDocuments")
            file.bytes = legacy.toString().toByteArray(); file.revision++
        }
        for (entity in listOf("documentIndex", "supplementalBackup")) {
            val file = remote.entries.values.single { it.properties["entityType"] == entity }
            val root = JSONObject(file.bytes.toString(Charsets.UTF_8))
            val array = root.getJSONArray(if (entity == "documentIndex") "documents" else "managedDocuments")
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                if (item.getString("documentId") in blankDocuments) item.put("sha256", "")
            }
            file.bytes = root.toString().toByteArray(); file.revision++
        }
        File(app.noBackupFilesDir, "drive-pending-writes").deleteRecursively()
        remote.omitV3Etags = true
        repo.insert(repo.getReceiptById(17)!!.copy(aussteller = "Synthetic edited known-hash receipt"))
        sync(vm)
        assertNull(vm.driveSyncReport.value?.toJson(), vm.driveSyncError.value)
        assertEquals(22, repo.allReceipts.first().size)
        assertEquals(24, repo.getAllManagedDocuments().size)
        assertEquals(18, repo.getAllManagedDocuments().count { it.sha256.isBlank() })
        for (entity in listOf("documentIndex", "supplementalBackup")) {
            val file = remote.entries.values.single { it.properties["entityType"] == entity }
            val root = JSONObject(file.bytes.toString(Charsets.UTF_8))
            val array = root.getJSONArray(if (entity == "documentIndex") "documents" else "managedDocuments")
            val byId = (0 until array.length()).map { array.getJSONObject(it) }.associateBy { it.getString("documentId") }
            assertEquals(24, byId.size)
            neverCertify.forEach { assertEquals("", byId.getValue(it).getString("sha256")) }
            assertEquals(blankDocuments.getValue("receipt:${recoveredReceipt.internalId}").sha256,
                byId.getValue("receipt:${recoveredReceipt.internalId}").getString("sha256"))
        }
        assertFalse(remote.requests.any { it.method == "POST" ||
            (it.method in setOf("PUT", "PATCH") && it.url.encodedPath.substringAfterLast('/') in originalBytes) })
        originalBytes.forEach { (id, bytes) -> assertArrayEquals(id, bytes, remote.entries.getValue(id).bytes) }
        val finalBytes = remote.entries.mapValues { it.value.bytes.copyOf() }
        sync(vm)
        assertNull(vm.driveSyncReport.value?.toJson(), vm.driveSyncError.value)
        assertTrue(mutations().isEmpty())
        assertEquals(finalBytes.keys, remote.entries.keys)
        finalBytes.forEach { (id, bytes) -> assertArrayEquals(id, bytes, remote.entries.getValue(id).bytes) }
        assertEquals(18, repo.getAllManagedDocuments().count { it.sha256.isBlank() })
    }

    private suspend fun prepareColdOriginalEdit(): Triple<ReceiptViewModel, ManagedDocument, ByteArray> {
        val vm = establishedCloudStock()
        val receipt = repo.getReceiptById(22)!!
        val original = repo.getManagedDocument("receipt:${receipt.internalId}")!!
        val replacement = remote.entries.getValue(original.driveFileId!!).bytes + byteArrayOf(73)
        val local = File(app.filesDir, "synthetic-cold-original-replacement.png").apply { writeBytes(replacement) }
        File(app.noBackupFilesDir, "drive-pending-writes").deleteRecursively()
        remote.omitV3Etags = true
        repo.insert(receipt.copy(imageUrl = local.absolutePath))
        assertEquals(original.sha256, repo.getManagedDocument(original.documentId)!!.sha256)
        assertEquals(original.updatedAt, repo.getManagedDocument(original.documentId)!!.updatedAt)
        return Triple(vm, original, replacement)
    }

    @Test fun intentionalOriginalEditWithColdJournalCompletesBothDependentManifests() = runBlocking {
        val (vm, original, replacement) = prepareColdOriginalEdit()
        val allIds = remote.entries.keys.toSet()
        val otherOriginals = remote.entries.values.filter { it.mime == "image/png" && it.id != original.driveFileId }
            .associate { it.id to it.bytes.copyOf() }
        sync(vm)
        assertArrayEquals("The independently authorized original update already committed",
            replacement, remote.entries.getValue(original.driveFileId!!).bytes)
        assertEquals(StableDocumentIdentity.sha256(replacement), repo.getManagedDocument(original.documentId)!!.sha256)
        assertNull(vm.driveSyncReport.value?.toJson(), vm.driveSyncError.value)
        assertEquals(allIds, remote.entries.keys)
        assertEquals(24, repo.getAllManagedDocuments().size)
        for (entity in listOf("documentIndex", "supplementalBackup")) {
            val file = remote.entries.values.single { it.properties["entityType"] == entity }
            val root = JSONObject(file.bytes.toString(Charsets.UTF_8))
            val array = root.getJSONArray(if (entity == "documentIndex") "documents" else "managedDocuments")
            val row = (0 until array.length()).map { array.getJSONObject(it) }
                .single { it.getString("documentId") == original.documentId }
            assertEquals(StableDocumentIdentity.sha256(replacement), row.getString("sha256"))
            assertEquals(replacement.size.toLong(), row.getLong("fileSizeBytes"))
            assertEquals(original.updatedAt, row.getString("updatedAt"))
        }
        otherOriginals.forEach { (id, bytes) -> assertArrayEquals(id, bytes, remote.entries.getValue(id).bytes) }
        sync(vm)
        assertNull(vm.driveSyncReport.value?.toJson(), vm.driveSyncError.value)
        assertTrue(mutations().isEmpty())
        assertEquals(allIds, remote.entries.keys)
    }

    @Test fun lostIndexAcknowledgementAfterOriginalEditResumesWithoutReuploadOrLosingItsPriorHashProof() = runBlocking {
        val (vm, original, replacement) = prepareColdOriginalEdit()
        val indexId = indexFile().id
        val lost = java.util.concurrent.atomic.AtomicBoolean()
        transport.set(null, OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (request.method in setOf("PATCH", "PUT") && request.url.encodedPath.endsWith("/$indexId") &&
                request.url.queryParameter("uploadType") == "media" && !lost.getAndSet(true)) {
                response.close()
                throw java.net.SocketTimeoutException("Synthetic lost document-index acknowledgement")
            }
            response
        }.addInterceptor(remote).build())
        val stamp = prefs.getString("last_stammdaten_backup_time", null)
        sync(vm)
        assertArrayEquals(replacement, remote.entries.getValue(original.driveFileId!!).bytes)
        assertTrue("The first run must reach the conditional document-index update", lost.get())
        assertNotNull(vm.driveSyncError.value)
        assertEquals(stamp, prefs.getString("last_stammdaten_backup_time", null))
        val allIds = remote.entries.keys.toSet()
        transport.set(null, OkHttpClient.Builder().addInterceptor(remote).build())
        GoogleDriveClient.clearFolderCache()
        val restarted = connectedModel()
        sync(restarted)
        assertNull(restarted.driveSyncReport.value?.toJson(), restarted.driveSyncError.value)
        assertEquals(allIds, remote.entries.keys)
        assertArrayEquals(replacement, remote.entries.getValue(original.driveFileId!!).bytes)
        assertFalse(remote.requests.any { it.method == "POST" ||
            (it.method in setOf("PATCH", "PUT") && it.url.encodedPath.endsWith("/${original.driveFileId}")) })
        val supplemental = remote.entries.values.single { it.properties["entityType"] == "supplementalBackup" }
        val rows = JSONObject(supplemental.bytes.toString(Charsets.UTF_8)).getJSONArray("managedDocuments")
        val row = (0 until rows.length()).map { rows.getJSONObject(it) }
            .single { it.getString("documentId") == original.documentId }
        assertEquals(StableDocumentIdentity.sha256(replacement), row.getString("sha256"))
        assertEquals(replacement.size.toLong(), row.getLong("fileSizeBytes"))
        sync(restarted)
        assertNull(restarted.driveSyncReport.value?.toJson(), restarted.driveSyncError.value)
        assertTrue(mutations().isEmpty())
    }

    private fun assertOriginalInBothManifests(original: ManagedDocument, bytes: ByteArray) {
        for (entity in listOf("documentIndex", "supplementalBackup")) {
            val file = remote.entries.values.single { it.properties["entityType"] == entity }
            val root = JSONObject(file.bytes.toString(Charsets.UTF_8))
            val array = root.getJSONArray(if (entity == "documentIndex") "documents" else "managedDocuments")
            val row = (0 until array.length()).map { array.getJSONObject(it) }
                .single { it.getString("documentId") == original.documentId }
            assertEquals(entity, StableDocumentIdentity.sha256(bytes), row.getString("sha256"))
            assertEquals(entity, bytes.size.toLong(), row.getLong("fileSizeBytes"))
            assertEquals(entity, original.updatedAt, row.getString("updatedAt"))
        }
    }

    private fun forgetJsonBaselinesOnly() {
        File(app.noBackupFilesDir, "drive-pending-writes").listFiles().orEmpty().filter { file ->
            runCatching { JSONObject(file.readText()).has("sha256") }.getOrDefault(false)
        }.forEach { it.delete() }
    }

    @Test fun secondIntentionalOriginalEditWithColdJsonBaselinesCompletesWithoutStalePriorAuthorization() = runBlocking {
        val (vm, original, replacement) = prepareColdOriginalEdit()
        sync(vm)
        assertNull(vm.driveSyncReport.value?.toJson(), vm.driveSyncError.value)
        assertOriginalInBothManifests(original, replacement)
        val ids = remote.entries.keys.toSet()
        val second = replacement + byteArrayOf(74)
        File(app.filesDir, "synthetic-cold-original-replacement.png").writeBytes(second)
        forgetJsonBaselinesOnly()
        GoogleDriveClient.clearFolderCache()
        val restarted = connectedModel()
        sync(restarted)
        assertNull(restarted.driveSyncReport.value?.toJson(), restarted.driveSyncError.value)
        assertArrayEquals(second, remote.entries.getValue(original.driveFileId!!).bytes)
        assertOriginalInBothManifests(original, second)
        assertEquals(ids, remote.entries.keys)
        assertEquals(1, remote.requests.count { it.method in setOf("PATCH", "PUT") &&
            it.url.encodedPath.endsWith("/${original.driveFileId}") })
        sync(restarted)
        assertNull(restarted.driveSyncReport.value?.toJson(), restarted.driveSyncError.value)
        assertTrue(mutations().isEmpty())
    }

    @Test fun interruptedOriginalEditThenAnotherEditComposesBothManifestPreimagesWithoutDuplicatingFiles() = runBlocking {
        val (vm, original, replacement) = prepareColdOriginalEdit()
        val supplemental = remote.entries.values.single { it.properties["entityType"] == "supplementalBackup" }
        val oldSupplemental = supplemental.bytes.copyOf()
        val interrupted = java.util.concurrent.atomic.AtomicBoolean()
        transport.set(null, OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val request = chain.request()
            if (request.method in setOf("PATCH", "PUT") &&
                request.url.encodedPath.endsWith("/${supplemental.id}") &&
                request.url.queryParameter("uploadType") == "media" && !interrupted.getAndSet(true))
                throw java.net.SocketTimeoutException("Synthetic interruption before supplemental manifest write")
            chain.proceed(request)
        }.addInterceptor(remote).build())
        sync(vm)
        assertTrue("The first run must finish the document index before the interruption", interrupted.get())
        assertNotNull(vm.driveSyncError.value)
        assertArrayEquals(replacement, remote.entries.getValue(original.driveFileId!!).bytes)
        assertArrayEquals(oldSupplemental, supplemental.bytes)
        val ids = remote.entries.keys.toSet()
        val second = replacement + byteArrayOf(75)
        File(app.filesDir, "synthetic-cold-original-replacement.png").writeBytes(second)
        // Keep the interrupted own-write facts, while requiring each old manifest to be proved.
        forgetJsonBaselinesOnly()
        transport.set(null, OkHttpClient.Builder().addInterceptor(remote).build())
        GoogleDriveClient.clearFolderCache()
        val restarted = connectedModel()
        sync(restarted)
        assertNull(restarted.driveSyncReport.value?.toJson(), restarted.driveSyncError.value)
        assertArrayEquals(second, remote.entries.getValue(original.driveFileId!!).bytes)
        assertOriginalInBothManifests(original, second)
        assertEquals(ids, remote.entries.keys)
        assertEquals(1, remote.requests.count { it.method in setOf("PATCH", "PUT") &&
            it.url.encodedPath.endsWith("/${original.driveFileId}") })
        sync(restarted)
        assertNull(restarted.driveSyncReport.value?.toJson(), restarted.driveSyncError.value)
        assertTrue(mutations().isEmpty())
    }

    @Test fun lateExternalOriginalChangeCannotUseTheEarlierOwnWriteProofToPublishStaleManifests() = runBlocking {
        val (vm, original, replacement) = prepareColdOriginalEdit()
        val receiptIndex = remote.entries.values.single { it.properties["entityType"] == "receiptIndex" }
        val documentIndex = indexFile()
        val oldIndex = documentIndex.bytes.copyOf()
        val supplemental = remote.entries.values.single { it.properties["entityType"] == "supplementalBackup" }
        val oldSupplemental = supplemental.bytes.copyOf()
        val external = replacement + byteArrayOf(76)
        val changed = java.util.concurrent.atomic.AtomicBoolean()
        transport.set(null, OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (request.method in setOf("PATCH", "PUT") &&
                request.url.encodedPath.endsWith("/${receiptIndex.id}") &&
                request.url.queryParameter("uploadType") == "media" && !changed.getAndSet(true)) {
                remote.entries.getValue(original.driveFileId!!).apply { bytes = external; revision++ }
            }
            response
        }.addInterceptor(remote).build())
        sync(vm)
        assertTrue("The original changed only after our own media and receipt acknowledgements", changed.get())
        assertNotNull(vm.driveSyncError.value)
        assertArrayEquals(external, remote.entries.getValue(original.driveFileId!!).bytes)
        assertEquals(StableDocumentIdentity.sha256(replacement), repo.getManagedDocument(original.documentId)!!.sha256)
        assertArrayEquals(oldIndex, documentIndex.bytes)
        assertArrayEquals(oldSupplemental, supplemental.bytes)
        assertFalse(remote.requests.any { it.method in setOf("PATCH", "PUT") &&
            it.url.encodedPath.endsWith("/${documentIndex.id}") })
        assertTrue(vm.driveSyncReport.value!!.steps.any { it.phase == DriveSyncPhase.DOCUMENT_INDEX &&
            it.failure?.check == DriveSyncCheck.ORIGINAL_BASELINE_CHANGED_DURING_READ })
    }

    private fun setManifestOriginalSize(documentId: String, size: Long) {
        for (entity in listOf("documentIndex", "supplementalBackup")) {
            val file = remote.entries.values.single { it.properties["entityType"] == entity }
            val root = JSONObject(file.bytes.toString(Charsets.UTF_8))
            val array = root.getJSONArray(if (entity == "documentIndex") "documents" else "managedDocuments")
            (0 until array.length()).map { array.getJSONObject(it) }
                .single { it.getString("documentId") == documentId }.put("fileSizeBytes", size)
            file.bytes = root.toString().toByteArray()
            file.revision++
        }
    }

    @Test fun independentlyProvenOwnOriginalEditCanEnrichLegacyUnknownManifestSize() = runBlocking {
        val (vm, original, replacement) = prepareColdOriginalEdit()
        setManifestOriginalSize(original.documentId, 0)
        val ids = remote.entries.keys.toSet()
        sync(vm)
        assertArrayEquals(replacement, remote.entries.getValue(original.driveFileId!!).bytes)
        assertNull(vm.driveSyncReport.value?.toJson(), vm.driveSyncError.value)
        assertOriginalInBothManifests(original, replacement)
        assertEquals(ids, remote.entries.keys)
        sync(vm)
        assertNull(vm.driveSyncReport.value?.toJson(), vm.driveSyncError.value)
        assertTrue(mutations().isEmpty())
    }

    @Test fun independentlyProvenOwnOriginalEditCannotOverwriteAnUnknownPositiveManifestSize() = runBlocking {
        val (vm, original, replacement) = prepareColdOriginalEdit()
        setManifestOriginalSize(original.documentId, original.fileSizeBytes + 99)
        val oldIndex = indexFile().bytes.copyOf()
        val supplemental = remote.entries.values.single { it.properties["entityType"] == "supplementalBackup" }
        val oldSupplemental = supplemental.bytes.copyOf()
        val ids = remote.entries.keys.toSet()
        sync(vm)
        assertArrayEquals(replacement, remote.entries.getValue(original.driveFileId!!).bytes)
        assertNotNull(vm.driveSyncError.value)
        assertArrayEquals(oldIndex, indexFile().bytes)
        assertArrayEquals(oldSupplemental, supplemental.bytes)
        assertEquals(ids, remote.entries.keys)
        assertTrue(vm.driveSyncReport.value!!.steps.any { it.phase == DriveSyncPhase.DOCUMENT_INDEX &&
            it.failure?.check == DriveSyncCheck.BACKUP_PREIMAGE_CONFLICT })
    }

    private suspend fun establishedStandaloneWithMissingManifestHash(): Pair<ReceiptViewModel, ManagedDocument> {
        val vm = establishedCloudStock()
        val property = repo.getPropertyMetadata()!!
        val id = "synthetic-standalone-cold-index"
        val type = ManagedDocumentType.SONSTIGES
        val file = File(app.filesDir, "synthetic-standalone-cold-index.png").apply { writeBytes(originalBytes(25)) }
        val document = ManagedDocument(documentId = id, propertyId = property.propertyId, documentType = type.name,
            documentDate = "2026-01-01", title = "Synthetic standalone document", originalFilename = file.name,
            storedFilename = DocumentFilenameGenerator.document(type, "2026-01-01", "Synthetic standalone document", "png", id),
            mimeType = "image/png", localUri = file.absolutePath, sha256 = StableDocumentIdentity.sha256(file.readBytes()),
            fileSizeBytes = file.length(), createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z")
        repo.upsertManagedDocument(document)
        assertTrue(GoogleDriveClient.withCreateRecovery(app) { drive.syncManagedDocumentToDrive(token, config, id) })
        sync(vm)
        assertNull(vm.driveSyncReport.value?.toJson(), vm.driveSyncError.value)
        val uploaded = repo.getManagedDocument(id)!!
        for (entity in listOf("documentIndex", "supplementalBackup")) {
            val manifest = remote.entries.values.single { it.properties["entityType"] == entity }
            val root = JSONObject(manifest.bytes.toString(Charsets.UTF_8))
            val rows = root.getJSONArray(if (entity == "documentIndex") "documents" else "managedDocuments")
            (0 until rows.length()).map { rows.getJSONObject(it) }
                .single { it.getString("documentId") == id }.put("sha256", "")
            manifest.bytes = root.toString().toByteArray()
            manifest.revision++
        }
        File(app.noBackupFilesDir, "drive-pending-writes").deleteRecursively()
        remote.omitV3Etags = true
        GoogleDriveClient.clearFolderCache()
        return vm to uploaded
    }

    @Test fun independentlyVerifiedStandaloneNoOpEnrichesBothExistingManifestHashesWithColdJournal() = runBlocking {
        val (vm, original) = establishedStandaloneWithMissingManifestHash()
        val allIds = remote.entries.keys.toSet()
        val sourceBytes = remote.entries.getValue(original.driveFileId!!).bytes.copyOf()
        sync(vm)
        assertNull(vm.driveSyncReport.value?.toJson(), vm.driveSyncError.value)
        assertEquals(original, repo.getManagedDocument(original.documentId))
        assertEquals(25, repo.getAllManagedDocuments().size)
        assertEquals(allIds, remote.entries.keys)
        assertArrayEquals(sourceBytes, remote.entries.getValue(original.driveFileId!!).bytes)
        assertOriginalInBothManifests(original, sourceBytes)
        assertFalse(remote.requests.any { it.method in setOf("PATCH", "PUT", "POST") &&
            it.url.encodedPath.endsWith("/${original.driveFileId}") })
        assertFalse(remote.requests.any { it.url.queryParameter("alt") == "media" &&
            it.url.encodedPath.endsWith("/${original.driveFileId}") })
        sync(vm)
        assertNull(vm.driveSyncReport.value?.toJson(), vm.driveSyncError.value)
        assertTrue(mutations().isEmpty())
    }

    @Test fun standaloneHashMismatchCannotEnrichOrOverwriteEitherExistingManifest() = runBlocking {
        val (vm, original) = establishedStandaloneWithMissingManifestHash()
        val cloud = remote.entries.getValue(original.driveFileId!!)
        val external = originalBytes(26) + byteArrayOf(77)
        cloud.bytes = external
        cloud.revision++
        val oldIndex = indexFile().bytes.copyOf()
        val supplemental = remote.entries.values.single { it.properties["entityType"] == "supplementalBackup" }
        val oldSupplemental = supplemental.bytes.copyOf()
        val ids = remote.entries.keys.toSet()
        sync(vm)
        assertNotNull(vm.driveSyncError.value)
        assertArrayEquals(external, cloud.bytes)
        assertEquals(original.sha256, repo.getManagedDocument(original.documentId)!!.sha256)
        assertEquals(original.updatedAt, repo.getManagedDocument(original.documentId)!!.updatedAt)
        assertArrayEquals(oldIndex, indexFile().bytes)
        assertArrayEquals(oldSupplemental, supplemental.bytes)
        assertEquals(ids, remote.entries.keys)
        assertFalse(remote.requests.any { it.method in setOf("PATCH", "PUT", "POST") &&
            it.url.encodedPath.endsWith("/${original.driveFileId}") })
    }

    @Test fun missingOrInvalidOriginalVersionsCannotAuthorizeBaselineAdoptionOrManifestEnrichment() = runBlocking {
        val vm = establishedCloudStock()
        val original = repo.getManagedDocument("receipt:document-index-receipt-22")!!
        db.managedDocumentDao().upsert(original.copy(sha256 = ""))
        for (entity in listOf("documentIndex", "supplementalBackup")) {
            val manifest = remote.entries.values.single { it.properties["entityType"] == entity }
            val root = JSONObject(manifest.bytes.toString(Charsets.UTF_8))
            val rows = root.getJSONArray(if (entity == "documentIndex") "documents" else "managedDocuments")
            (0 until rows.length()).map { rows.getJSONObject(it) }
                .single { it.getString("documentId") == original.documentId }.put("sha256", "")
            manifest.bytes = root.toString().toByteArray()
            manifest.revision++
        }
        File(app.noBackupFilesDir, "drive-pending-writes").deleteRecursively()
        val documentsBefore = repo.getAllManagedDocuments()
        val remoteBefore = remote.entries.mapValues { it.value.bytes.copyOf() }
        for ((label, version) in listOf("absent" to null, "empty" to "", "invalid" to "invalid-version")) {
            transport.set(null, OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                if (request.method != "GET" || request.url.queryParameter("alt") == "media") response else {
                    val root = JSONObject(response.body!!.string())
                    fun removeVersion(item: JSONObject) {
                        if (item.optString("id") == original.driveFileId) {
                            if (version == null) item.remove("version") else item.put("version", version)
                        }
                    }
                    removeVersion(root)
                    root.optJSONArray("files")?.let { rows ->
                        for (index in 0 until rows.length()) removeVersion(rows.getJSONObject(index))
                    }
                    response.newBuilder().body(okhttp3.ResponseBody.create(null, root.toString())).build()
                }
            }.addInterceptor(remote).build())
            GoogleDriveClient.clearFolderCache()
            sync(vm)
            assertNotNull("$label original version must not certify a baseline: ${vm.driveSyncReport.value?.toJson()}",
                vm.driveSyncError.value)
            assertEquals("$label original version must leave local documents intact", documentsBefore, repo.getAllManagedDocuments())
            assertTrue("$label original version must not permit a write", mutations().isEmpty())
            assertEquals(remoteBefore.keys, remote.entries.keys)
            remoteBefore.forEach { (id, bytes) -> assertArrayEquals("$label/$id", bytes, remote.entries.getValue(id).bytes) }
        }
    }

    @Test fun inconsistentLocalOriginalSizeCannotBeReplacedByACloudHashOrWrittenToEitherManifest() = runBlocking {
        val vm = establishedCloudStock()
        val original = repo.getManagedDocument("receipt:document-index-receipt-22")!!
        val source = remote.entries.getValue(original.driveFileId!!)
        val sourceBytes = source.bytes.copyOf()
        val receipt = repo.getReceiptById(22)!!
        val saved = JSONObject(remote.entries.getValue(receipt.driveMetadataFileId!!).bytes.toString(Charsets.UTF_8))
        assertEquals(original.sha256, saved.getJSONArray("originalDocuments").getJSONObject(0).getString("sha256"))
        assertEquals(sourceBytes.size.toLong(), saved.getJSONArray("originalDocuments").getJSONObject(0).getLong("fileSizeBytes"))
        db.managedDocumentDao().upsert(original.copy(sha256 = "", fileSizeBytes = 0))
        for (entity in listOf("documentIndex", "supplementalBackup")) {
            val manifest = remote.entries.values.single { it.properties["entityType"] == entity }
            val root = JSONObject(manifest.bytes.toString(Charsets.UTF_8))
            val rows = root.getJSONArray(if (entity == "documentIndex") "documents" else "managedDocuments")
            (0 until rows.length()).map { rows.getJSONObject(it) }
                .single { it.getString("documentId") == original.documentId }
                .put("sha256", "").put("fileSizeBytes", 0)
            manifest.bytes = root.toString().toByteArray()
            manifest.revision++
        }
        File(app.noBackupFilesDir, "drive-pending-writes").deleteRecursively()
        remote.omitV3Etags = true
        val ids = remote.entries.keys.toSet()
        val before = remote.entries.mapValues { it.value.bytes.copyOf() }
        sync(vm)
        assertNotNull(vm.driveSyncError.value)
        assertEquals(original.copy(sha256 = "", fileSizeBytes = 0), repo.getManagedDocument(original.documentId))
        assertArrayEquals(sourceBytes, source.bytes)
        assertEquals(ids, remote.entries.keys)
        assertTrue(mutations().isEmpty())
        before.forEach { (id, bytes) -> assertArrayEquals(id, bytes, remote.entries.getValue(id).bytes) }
        assertTrue(vm.driveSyncReport.value!!.steps.any {
            it.failure?.check == DriveSyncCheck.ORIGINAL_BASELINE_REFERENCE_MISMATCH })
    }
}
