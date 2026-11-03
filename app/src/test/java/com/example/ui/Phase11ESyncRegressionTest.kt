package com.example.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.api.GoogleDriveClient
import com.example.comparison.ComparisonDriveHttpFixture
import com.example.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Synthetic Room and HTTP only. No production files, accounts or installed-phone data. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [DriveConnectionRegressionTest.AuthShadow::class])
class Phase11ESyncRegressionTest {
    @get:Rule val isolation = IsolatedAppDatabaseRule()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val db get() = AppDatabase.getDatabase(app, CoroutineScope(Dispatchers.IO))
    private val repo get() = ReceiptRepository(db.receiptDao(), db.propertyDao(), db.receiptEntityDao(), db.belegDao(),
        db.exportAuditDao(), db.receiptDocumentDao(), db.managedDocumentDao())
    private val drive get() = DrivePersistenceRepository(app, repo)
    private val prefs get() = app.getSharedPreferences("google_drive_prefs", 0)
    private val transport = GoogleDriveClient::class.java.getDeclaredField("client").apply { isAccessible = true }
    private lateinit var previous: OkHttpClient
    private lateinit var remote: ComparisonDriveHttpFixture
    private lateinit var config: DriveAppConfig
    private val stores = mutableListOf<ViewModelStore>()
    private val models = mutableListOf<ReceiptViewModel>()
    private val token = "synthetic-native"
    private var simulatedLatencyMs = 0L

    @Before fun setup() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        prefs.edit().clear().putBoolean("user_disconnected", true).putBoolean("auto_backup", false).commit()
        DriveConnectionRegressionTest.AuthShadow.failure = null
        DriveConnectionRegressionTest.AuthShadow.calls.set(0)
        DriveConnectionRegressionTest.AuthShadow.clears.set(0)
        DriveConnectionRegressionTest.AuthShadow.expireFirst = false
        previous = transport.get(null) as OkHttpClient
        remote = ComparisonDriveHttpFixture()
        File(app.noBackupFilesDir, "drive-pending-writes").deleteRecursively()
        transport.set(null, OkHttpClient.Builder().addInterceptor(remote).build())
        GoogleDriveClient.clearFolderCache()
        config = when (val result = drive.initializeDriveStorage(token)) {
            is DriveInitializationResult.SuccessCreatedNew -> result.config
            is DriveInitializationResult.SuccessLoadedExisting -> result.config
            is DriveInitializationResult.Failure -> error(result.error)
        }
        repo.restorePropertyMetadata(PropertyMetadata(name = "Synthetic Phase11E"))
    }

    @After fun cleanup() = runBlocking {
        stores.forEach { it.clear() }
        models.forEach { it.viewModelScope.coroutineContext[Job]?.join() }
        transport.set(null, previous)
        GoogleDriveClient.clearFolderCache()
        Dispatchers.resetMain()
    }

    private fun model(): ReceiptViewModel {
        val store = ViewModelStore().also { stores += it }
        return ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[ReceiptViewModel::class.java].also { models += it }
    }
    private suspend fun awaitJob(vm: ReceiptViewModel, name: String) {
        val field = ReceiptViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }
        withTimeout(60_000) { (field.get(vm) as? Job)?.join() }
    }
    private suspend fun connectedModel(): ReceiptViewModel = model().also {
        it.connectDrive("phase11e-synthetic@example.invalid")
        awaitJob(it, "driveInitializationJob")
        assertTrue(it.isDriveConnected.value)
    }

    private suspend fun seedReceipts(count: Int, localBytes: Boolean = false) {
        val entries = JSONArray()
        for (i in 1..count) {
            val internalId = "phase11e-synthetic-$i"
            val originalId = "phase11e-original-$i"
            val bytes = byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a) + ByteArray(32) { i.toByte() }
            remote.put(originalId, "synthetic-$i.png", config.receiptsFolderId, mime = "image/png").bytes = bytes
            val path = if (localBytes) File(app.filesDir,"phase11e-$i.png").apply { writeBytes(bytes) }.absolutePath else ""
            val row = Receipt(id = i, internalId = internalId, displayId = "BLG-2026-${i.toString().padStart(6,'0')}",
                aussteller = "Synthetic vendor $i", datum = "2026-01-01", uhrzeit = "12:00", bruttobetrag = 119.0,
                hauptkategorie = "Instandhaltung", unterkategorie = "Material", kontoNr = "4800", beschreibung = "Synthetic Phase11E receipt", imageUrl = path,
                driveFileId = originalId, driveFolderId = config.receiptsFolderId, storedFilename = "synthetic-$i.png",
                originalMimeType = "image/png", fileSizeBytes = bytes.size.toLong(), syncStatus = "SYNCED",
                driveRevision = 0, isArchivedToDrive = true)
            repo.insert(row)
            repo.insertDocument(ReceiptDocumentReference(id = "receipt:$internalId", receiptInternalId = internalId,
                driveFileId = originalId, driveFolderId = config.receiptsFolderId, filename = "synthetic-$i.png",
                mimeType = "image/png", sizeBytes = bytes.size.toLong(), role = "MAIN_RECEIPT", createdAt = "2026-01-01T00:00:00Z"))
            repo.upsertManagedDocument(ManagedDocument(documentId = "receipt:$internalId", propertyId = row.propertyId,
                receiptInternalId = internalId, originalFilename = "synthetic-$i.png", storedFilename = "synthetic-$i.png",
                mimeType = "image/png", localUri = path, driveFileId = originalId, driveFolderId = config.receiptsFolderId,
                sha256 = StableDocumentIdentity.sha256(bytes), fileSizeBytes = bytes.size.toLong(),
                createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z"))
            val metadataId = requireNotNull(drive.upsertReceiptMetadata(token,config,row))
            repo.insert(row.copy(driveMetadataFileId = metadataId, driveRevision = 1))
            entries.put(JSONObject().put("internalId",internalId).put("displayId",row.displayId)
                .put("mainDriveFileId",originalId).put("metadataFileId",metadataId).put("syncStatus","SYNCED")
                .put("updatedAt","2026-01-01T00:00:00Z"))
        }
        remote.put("phase11e-index","receipt-index.json",config.systemFolderId,
            JSONObject().put("version",1).put("entries",entries).toString(),"receiptIndex")
        // Fixture construction commits the index directly; it represents a fully restored stock,
        // not an application metadata upload still waiting for its index acknowledgement.
        repo.allReceipts.first().mapNotNull { it.driveMetadataFileId }.forEach {
            DrivePendingWrites(app).completedReceiptIndex(it)
        }
    }

    private suspend fun measure(vm: ReceiptViewModel, label: String): JSONObject {
        remote.requests.clear()
        val originalIdsBefore = remote.entries.values.filter { it.mime.startsWith("image/") || it.mime == "application/pdf" }.map { it.id }.toSet()
        val started = System.nanoTime()
        vm.syncAllToDrive()
        try { awaitJob(vm,"driveSyncJob") }
        catch (timeout: TimeoutCancellationException) {
            val output = File("build/reports/phase11e/$label-incomplete.json")
            output.parentFile!!.mkdirs()
            output.writeText(JSONObject().put("transport", "SIMULATED_HTTP")
                .put("simulatedLatencyPerRequestMs", simulatedLatencyMs).put("apiCalls", remote.requests.size)
                .put("report", vm.driveSyncReport.value?.let { JSONObject(it.toJson()) }).toString(2))
            throw timeout
        }
        val duration = (System.nanoTime()-started)/1_000_000
        val rows = repo.allReceipts.first()
        val result = JSONObject().put("transport","SIMULATED_HTTP").put("durationMs",duration)
            .put("simulatedLatencyPerRequestMs", simulatedLatencyMs)
            .put("receiptCount",rows.size).put("synced",rows.count { it.syncStatus=="SYNCED" })
            .put("errors",rows.count { it.syncStatus=="ERROR" }).put("apiCalls",vm.driveSyncReport.value?.apiCalls ?: 0)
            .put("requestsDeliveredToFake",remote.requests.size)
            .put("filesList",remote.requests.count { it.method=="GET" && it.url.encodedPath.endsWith("/files") })
            .put("filesGet",remote.requests.count { it.method=="GET" && !it.url.encodedPath.endsWith("/files") && !it.url.encodedPath.endsWith("/generateIds") })
            .put("filesGenerateIds",remote.requests.count { it.url.encodedPath.endsWith("/generateIds") })
            .put("filesCreate",remote.requests.count { it.method=="POST" }).put("filesUpdate",remote.requests.count { it.method=="PATCH" || it.method=="PUT" })
            .put("originalMediaGets",remote.requests.count { it.url.encodedPath.substringAfterLast('/') in originalIdsBefore && it.url.queryParameter("alt")=="media" })
            .put("missingLocalSourceErrors",rows.count { it.syncError.orEmpty().contains("Keine lokale Quelldatei") })
            .put("phases",vm.driveSyncReport.value?.let { JSONObject(it.toJson()).getJSONArray("steps") } ?: JSONArray())
            .put("hasSyncError",vm.driveSyncError.value!=null).put("connected",vm.isDriveConnected.value)
        val originals = remote.entries.values.filter { it.mime.startsWith("image/") || it.mime == "application/pdf" }.map { it.id }.toSet()
        result.put("originalCreates", (originals - originalIdsBefore).size)
            .put("originalUpdates", remote.requests.count { it.method == "PATCH" && it.url.encodedPath.substringAfterLast('/') in originals && it.url.queryParameter("uploadType") == "media" })
        val output=File("build/reports/phase11e/$label.json"); output.parentFile!!.mkdirs();output.writeText(result.toString(2))
        return result
    }

    @Test fun datevEquivalentPreimageCompletesEveryPhaseAndResumesAfterLostSupplementalAcknowledgement() = runBlocking {
        seedReceipts(22, localBytes = true)
        remote.omitV3Etags = true
        val vm = connectedModel()
        val initial = measure(vm, "datev-preimage-initial")
        assertFalse(initial.toString(), initial.getBoolean("hasSyncError"))
        val datev = remote.entries.values.single { it.properties["entityType"] == "datevProfiles" }
        val supplemental = remote.entries.values.single { it.properties["entityType"] == "supplementalBackup" }
        val profile = com.example.util.DatevProfileService.getActiveProfile(app)
        com.example.util.DatevProfileService.saveActiveProfile(app, profile.copy(lastModified = profile.lastModified + 1000))
        File(app.noBackupFilesDir, "drive-pending-writes").deleteRecursively()
        val remoteBytes = remote.entries.mapValues { it.value.bytes.copyOf() }
        val localBefore = repo.allReceipts.first()
        val repeated = measure(vm, "datev-preimage-repeat")
        assertFalse(repeated.toString(), repeated.getBoolean("hasSyncError"))
        assertEquals(0, repeated.getInt("filesCreate") + repeated.getInt("filesUpdate"))
        assertEquals(remoteBytes.keys, remote.entries.keys)
        remoteBytes.forEach { (id, bytes) -> assertArrayEquals(id, bytes, remote.entries.getValue(id).bytes) }
        assertEquals(localBefore, repo.allReceipts.first())
        val report = requireNotNull(vm.driveSyncReport.value)
        for (phase in listOf(com.example.api.DriveSyncPhase.DATEV_PROFILES, com.example.api.DriveSyncPhase.LEARNING_RULES,
            com.example.api.DriveSyncPhase.CATEGORIES, com.example.api.DriveSyncPhase.RECEIPTS,
            com.example.api.DriveSyncPhase.DOCUMENT_INDEX, com.example.api.DriveSyncPhase.SUPPLEMENTAL,
            com.example.api.DriveSyncPhase.COMPLETION)) {
            assertTrue("Phase $phase was not completed: ${report.toJson()}", report.steps.any {
                it.phase == phase && it.outcome in setOf(com.example.api.DriveSyncOutcome.SUCCESS, com.example.api.DriveSyncOutcome.UNCHANGED)
            })
        }
        val original = repo.getReceiptById(1)!!
        repo.insert(original.copy(aussteller = "Synthetic DATEV continuation edit"))
        val newFile = File(app.filesDir, "datev-continuation.png").apply { writeBytes(File(original.imageUrl).readBytes() + byteArrayOf(23)) }
        repo.insert(original.copy(id = 23, internalId = "datev-continuation-23", displayId = "BLG-2026-000023",
            imageUrl = newFile.absolutePath, driveFileId = null, driveMetadataFileId = null, driveFolderId = null,
            storedFilename = null, originalMimeType = null, fileSizeBytes = 0, driveRevision = null,
            syncStatus = "PENDING", lastSyncedAt = null, isArchivedToDrive = false))
        val lost = java.util.concurrent.atomic.AtomicBoolean()
        transport.set(null, OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (request.method in setOf("PATCH", "PUT") && request.url.encodedPath.endsWith("/" + supplemental.id) &&
                request.url.queryParameter("uploadType") == "media" && !lost.getAndSet(true)) {
                response.close()
                throw java.net.SocketTimeoutException("Synthetic lost supplemental acknowledgement")
            }
            response
        }.addInterceptor(remote).build())
        val stamp = prefs.getString("last_stammdaten_backup_time", null)
        val partial = measure(vm, "datev-preimage-partial")
        assertTrue(partial.toString(), partial.getBoolean("hasSyncError"))
        assertTrue(lost.get())
        assertEquals(stamp, prefs.getString("last_stammdaten_backup_time", null))
        assertEquals(23, repo.allReceipts.first().size)
        val afterPartial = repo.allReceipts.first()
        val idsAfterPartial = remote.entries.keys.toSet()
        transport.set(null, OkHttpClient.Builder().addInterceptor(remote).build())
        val resumed = measure(vm, "datev-preimage-resumed")
        assertFalse(resumed.toString(), resumed.getBoolean("hasSyncError"))
        assertEquals(idsAfterPartial, remote.entries.keys)
        assertEquals(afterPartial, repo.allReceipts.first())
        assertEquals(0, resumed.getInt("filesCreate") + resumed.getInt("filesUpdate"))
        assertArrayEquals(remoteBytes.getValue(datev.id), datev.bytes)
        val savedPreference = JSONObject(supplemental.bytes.toString(Charsets.UTF_8))
            .getJSONObject("datevProfilePrefs").getJSONObject("active_profile_json").getString("value")
        val oldPreference = JSONObject(remoteBytes.getValue(supplemental.id).toString(Charsets.UTF_8))
            .getJSONObject("datevProfilePrefs").getJSONObject("active_profile_json").getString("value")
        assertEquals(oldPreference, savedPreference)
        remoteBytes.filterKeys { id -> id.startsWith("phase11e-original-") }.forEach { (id, bytes) ->
            assertArrayEquals(id, bytes, remote.entries.getValue(id).bytes)
        }
        val beforeRestore = repo.allReceipts.first().associateBy { it.internalId }
        val bytesBeforeRestore = remote.entries.mapValues { it.value.bytes.copyOf() }
        var idsAfterRestore: Set<String>? = null
        repeat(2) {
            val restored = drive.executeFullDriveRestore(token, config, com.example.data.RestoreMode.MERGE)
            assertTrue(restored.toString(), restored.isSuccess)
            // Existing restore inventory initializes its missing deletion-history container.
            // It must be unique and empty, and must never change existing business files.
            val added = remote.entries.values.filter { it.id !in idsAfterPartial }
            assertEquals(1, added.size)
            assertEquals("deletions", added.single().name)
            assertEquals("application/vnd.google-apps.folder", added.single().mime)
            assertEquals(config.systemFolderId, added.single().parent)
            if (idsAfterRestore != null) assertEquals(idsAfterRestore, remote.entries.keys)
            idsAfterRestore = remote.entries.keys.toSet()
            bytesBeforeRestore.forEach { (id, bytes) -> assertArrayEquals(id, bytes, remote.entries.getValue(id).bytes) }
        }
        assertEquals(beforeRestore.keys, repo.allReceipts.first().map { it.internalId }.toSet())
        repo.allReceipts.first().forEach { row ->
            val before = beforeRestore.getValue(row.internalId)
            assertEquals(before.id, row.id)
            assertEquals(before.driveFileId, row.driveFileId)
            assertEquals(before.driveMetadataFileId, row.driveMetadataFileId)
            assertEquals(before.bruttobetrag, row.bruttobetrag, 0.0)
        }
        val afterRestore = measure(vm, "datev-preimage-after-restore")
        assertFalse(afterRestore.toString(), afterRestore.getBoolean("hasSyncError"))
        val final = measure(vm, "datev-preimage-final-repeat")
        assertFalse(final.toString(), final.getBoolean("hasSyncError"))
        assertEquals(0, final.getInt("filesCreate") + final.getInt("filesUpdate"))
        assertEquals(idsAfterRestore, remote.entries.keys)
    }

    @Test fun drive1403FullSyncWithNoV3EtagUpdatesLedgerAndMetadataThenRepeatsWithoutWrites() = runBlocking {
        seedReceipts(22, localBytes = true)
        remote.omitV3Etags = true
        val ledger = remote.put("ledger-1403", "Steuerassistent_Belege_Hauptbuch.csv", config.rootFolderId, "old ledger", mime = "text/csv")
        val before = repo.allReceipts.first()
        val originals = before.associate { it.driveFileId!! to remote.entries.getValue(it.driveFileId!!).bytes.copyOf() }
        val vm = connectedModel()
        val result = measure(vm, "drive1403-no-v3-etag-first")
        assertFalse(result.toString(), result.getBoolean("hasSyncError"))
        assertArrayEquals(GoogleDriveClient.ledgerCsv(before).toByteArray(), ledger.bytes)
        val changed = repo.allReceipts.first().first().copy(aussteller = "Synthetic local edit 1403")
        repo.insert(changed)
        val edited = measure(vm, "drive1403-no-v3-etag-edit")
        assertFalse(edited.toString(), edited.getBoolean("hasSyncError"))
        assertTrue(JSONObject(remote.entries.getValue(changed.driveMetadataFileId!!).bytes.toString(Charsets.UTF_8))
            .toString().contains(changed.aussteller))
        val newFile = File(app.filesDir, "synthetic-1403-new.png").apply {
            writeBytes(File(changed.imageUrl).readBytes() + byteArrayOf(23))
        }
        repo.insert(changed.copy(id = 23, internalId = "phase11e-new-1403", displayId = "BLG-2026-000023",
            imageUrl = newFile.absolutePath, driveFileId = null, driveMetadataFileId = null, driveFolderId = null,
            storedFilename = null, originalMimeType = null, fileSizeBytes = 0, driveRevision = null,
            syncStatus = "PENDING", lastSyncedAt = null, isArchivedToDrive = false))
        val created = measure(vm, "drive1403-no-v3-etag-create")
        assertFalse(created.toString(), created.getBoolean("hasSyncError"))
        assertEquals(1, created.getInt("originalCreates"))
        val newRow = repo.allReceipts.first().single { it.internalId == "phase11e-new-1403" }
        assertFalse(newRow.driveFileId.isNullOrBlank())
        assertFalse(newRow.driveMetadataFileId.isNullOrBlank())
        val repeated = measure(vm, "drive1403-no-v3-etag-repeat")
        assertFalse(repeated.toString(), repeated.getBoolean("hasSyncError"))
        assertEquals(0, repeated.getInt("filesCreate") + repeated.getInt("filesUpdate"))
        assertEquals(before.map { it.internalId to it.driveFileId }, repo.allReceipts.first().filter { it.id <= 22 }.map { it.internalId to it.driveFileId })
        assertEquals(23, repo.allReceipts.first().map { it.internalId }.toSet().size)
        val finalRows = repo.allReceipts.first()
        assertArrayEquals(GoogleDriveClient.ledgerCsv(finalRows).toByteArray(), ledger.bytes)
        val index = JSONObject(remote.entries.getValue("phase11e-index").bytes.toString(Charsets.UTF_8)).getJSONArray("entries")
        val newEntry = (0 until index.length()).map { index.getJSONObject(it) }.single { it.getString("internalId") == newRow.internalId }
        assertEquals(newRow.driveFileId, newEntry.getString("mainDriveFileId"))
        assertEquals(newRow.driveMetadataFileId, newEntry.getString("metadataFileId"))
        before.forEach { old ->
            val current = finalRows.single { it.id == old.id }
            assertEquals(old.propertyId, current.propertyId)
            assertEquals(old.displayId, current.displayId)
            assertEquals(old.bruttobetrag, current.bruttobetrag, 0.0)
            assertEquals(old.imageUrl, current.imageUrl)
        }
        originals.forEach { (id, bytes) -> assertArrayEquals(bytes, remote.entries.getValue(id).bytes) }
        assertTrue(vm.isDriveConnected.value)
    }

    @Test fun drive1403OriginalUsesSameIdAndConditionalV2UpdateWhenV3HeaderIsMissing() = runBlocking {
        remote.omitV3Etags = true
        val file = remote.put("original-1403", "original.png", config.receiptsFolderId, "old", mime = "image/png")
        val before = GoogleDriveClient.getOriginalFingerprintChecked(token, file.id)
        val desired = "new bytes".toByteArray()
        remote.requests.clear()
        GoogleDriveClient.updateOriginalChecked(token, before, desired, file.mime)
        assertArrayEquals(desired, remote.entries.getValue(file.id).bytes)
        assertTrue(remote.requests.any { it.method == "PUT" && it.header("If-Match") == "\"revision-1\"" })
        assertFalse(remote.requests.any { it.method == "POST" })
    }

    @Test fun drive1403MissingAllEtagsBlocksBeforeAnyMutationAndPreservesJson() = runBlocking {
        remote.omitV3Etags = true
        remote.omitV2Etags = true
        val file = remote.put("json-1403", "data.json", config.systemFolderId, "{}")
        remote.requests.clear()
        try {
            GoogleDriveClient.updateSyncJson(token, file.id, "{\"note\":\"local\"}")
            fail("Missing server write precondition must block")
        } catch (expected: com.example.api.DriveAccessFailure) { assertEquals(428, expected.httpStatus) }
        assertEquals("{}", file.bytes.toString(Charsets.UTF_8))
        assertTrue(remote.requests.all { it.method == "GET" })
    }

    @Test fun drive1403V2VersionMismatchDoesNotBindFreshEtagToStaleJson() = runBlocking {
        remote.omitV3Etags = true
        val file = remote.put("json-1403", "data.json", config.systemFolderId, "{}")
        val source = GoogleDriveClient.downloadSyncJsonSnapshot(token, file.id)
        file.bytes = "[]".toByteArray(); file.revision++ // Same size: only version can reject this binding.
        remote.requests.clear()
        try {
            GoogleDriveClient.updateSyncJson(token, file.id, "{\"note\":\"local\"}", source)
            fail("A newer ETag is not proof of the old version")
        } catch (expected: com.example.api.DriveSyncConflict) { /* exact preimage remains mandatory */ }
        assertTrue(remote.requests.all { it.method == "GET" })
        assertEquals("[]", file.bytes.toString(Charsets.UTF_8))
    }

    @Test fun drive1403ConcurrentChangeAfterV2ReadIsRejectedByServerIfMatch() = runBlocking {
        remote.omitV3Etags = true
        val file = remote.put("json-1403", "data.json", config.systemFolderId, "{}")
        transport.set(null, OkHttpClient.Builder().addInterceptor { chain ->
            if (chain.request().method == "PUT") {
                file.bytes = "{\"note\":\"external\"}".toByteArray(); file.revision++
            }
            chain.proceed(chain.request())
        }.addInterceptor(remote).build())
        try {
            GoogleDriveClient.updateSyncJson(token, file.id, "{\"note\":\"local\"}")
            fail("Server must reject a stale If-Match")
        } catch (expected: com.example.api.DriveAccessFailure) { assertEquals(412, expected.httpStatus) }
        assertEquals("{\"note\":\"external\"}", file.bytes.toString(Charsets.UTF_8))
        assertEquals(1, remote.requests.count { it.method == "PUT" })
    }

    @Test fun drive1403V2ReadPermissionAndOfflineFailuresNeverFallBackToUnconditionalWrites() = runBlocking {
        remote.omitV3Etags = true
        val file = remote.put("json-1403", "data.json", config.systemFolderId, "{}")
        for (offline in listOf(false, true)) {
            transport.set(null, OkHttpClient.Builder().addInterceptor { chain ->
                if (chain.request().url.encodedPath.contains("/drive/v2/")) {
                    if (offline) throw java.net.UnknownHostException("synthetic offline")
                    return@addInterceptor okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(403).message("Synthetic forbidden").body(okhttp3.ResponseBody.create(null, "{}")).build()
                }
                chain.proceed(chain.request())
            }.addInterceptor(remote).build())
            remote.requests.clear()
            try {
                GoogleDriveClient.updateSyncJson(token, file.id, "{\"note\":\"local\"}")
                fail("Failed precondition read must block")
            } catch (expected: java.io.IOException) {
                if (offline) {
                    assertEquals(com.example.api.DriveAccessFailure.Reason.NETWORK, (expected as com.example.api.DriveAccessFailure).reason)
                    assertTrue(expected.cause is java.net.UnknownHostException)
                    assertEquals("DNS", com.example.api.DriveSyncFailure.from(expected).kind)
                }
                else assertEquals(403, (expected as com.example.api.DriveAccessFailure).httpStatus)
            }
            assertEquals("{}", file.bytes.toString(Charsets.UTF_8))
            assertTrue(remote.requests.all { it.method == "GET" })
        }
    }

    @Test fun drive1403MalformedOrUnboundV2PreconditionsNeverAuthorizeWrites() = runBlocking {
        remote.omitV3Etags = true
        val file = remote.put("json-1403", "data.json", config.systemFolderId, "{}")
        for ((key, value) in listOf("id" to "another-file", "fileSize" to "999", "version" to "", "etag" to "*", "etag" to "W/\"revision-1\"")) {
            transport.set(null, OkHttpClient.Builder().addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (chain.request().url.encodedPath.contains("/drive/v2/")) {
                    val json = JSONObject(response.body!!.string()).put(key, value)
                    response.newBuilder().body(okhttp3.ResponseBody.create(null, json.toString())).build()
                } else response
            }.addInterceptor(remote).build())
            remote.requests.clear()
            try {
                GoogleDriveClient.updateSyncJson(token, file.id, "{\"note\":\"local\"}")
                fail("Invalid $key may not authorize a write")
            } catch (expected: com.example.api.DriveAccessFailure) { /* fail closed */ }
            assertEquals("{}", file.bytes.toString(Charsets.UTF_8))
            assertTrue(remote.requests.all { it.method == "GET" })
        }
    }

    @Test fun reproducibleTwentyMillisecondSyntheticLatencyBenchmarks() = runBlocking {
        seedReceipts(22)
        val vm = connectedModel()
        measure(vm, "benchmark-structure-warmup")
        simulatedLatencyMs = 20
        transport.set(null, OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            Thread.sleep(simulatedLatencyMs) // Deliberate synthetic HTTP latency, outside fixture's lock.
            chain.proceed(chain.request())
        }.addInterceptor(remote).build())
        val noOp = measure(vm, "benchmark-22-noop-20ms")
        assertFalse(noOp.toString(), noOp.getBoolean("hasSyncError"))
        assertEquals(0, noOp.getInt("filesCreate") + noOp.getInt("filesUpdate"))
        assertEquals(0, noOp.getInt("originalMediaGets"))
        suspend fun addReceipt(id: Int) {
            val template = repo.getReceiptById(1)!!
            val file = File(app.filesDir, "synthetic-new-$id.png")
            file.writeBytes(byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a) + ByteArray(256) { id.toByte() })
            repo.insert(template.copy(id = id, internalId = "phase11e-new-$id", displayId = "BLG-2026-${id.toString().padStart(6, '0')}",
                imageUrl = file.absolutePath, driveFileId = null, driveMetadataFileId = null, driveFolderId = null,
                storedFilename = null, originalMimeType = null, fileSizeBytes = 0, driveRevision = null,
                syncStatus = "PENDING", lastSyncedAt = null, isArchivedToDrive = false))
        }
        addReceipt(23)
        val one = measure(vm, "benchmark-one-new-20ms")
        assertFalse(one.toString(), one.getBoolean("hasSyncError"))
        assertEquals(1, one.getInt("originalCreates"))
        assertEquals(0, one.getInt("originalUpdates"))
        for (id in 24..28) addReceipt(id)
        val five = measure(vm, "benchmark-five-new-20ms")
        assertFalse(five.toString(), five.getBoolean("hasSyncError"))
        assertEquals(5, five.getInt("originalCreates"))
        assertEquals(0, five.getInt("originalUpdates"))
        val repeated = measure(vm, "benchmark-repeat-28-noop-20ms")
        assertFalse(repeated.toString(), repeated.getBoolean("hasSyncError"))
        assertEquals(0, repeated.getInt("filesCreate") + repeated.getInt("filesUpdate"))
        assertEquals(28, repo.allReceipts.first().map { it.internalId }.toSet().size)
    }

    @Test fun unreadableActiveMetadataBlocksBeforeAnyBackupWrite() = runBlocking {
        seedReceipts(22)
        val vm = connectedModel()
        val row = repo.allReceipts.first().last()
        remote.entries.remove(row.driveMetadataFileId)
        val result = measure(vm, "blocked-active-metadata")
        assertEquals(result.toString(), 0, result.getInt("filesCreate") + result.getInt("filesUpdate"))
        assertTrue(vm.isDriveConnected.value)
        assertEquals(22, vm.driveSyncReport.value!!.notAttempted)
        assertEquals(0, vm.driveSyncReport.value!!.failed)
        assertEquals(22, repo.allReceipts.first().size)
        assertNull(prefs.getString("last_stammdaten_backup_time", null))
    }

    @Test fun dnsDuringInventoryIsBoundedAndRetainsLoginAndAllReferences() = runBlocking {
        seedReceipts(22)
        val vm = connectedModel()
        val before = repo.allReceipts.first()
        val metadataId = before[7].driveMetadataFileId
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        transport.set(null, OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            if (chain.request().url.encodedPath.endsWith("/$metadataId") && chain.request().url.queryParameter("alt") == "media") {
                attempts.incrementAndGet()
                throw java.net.UnknownHostException("synthetic: private details must not appear")
            }
            chain.proceed(chain.request())
        }.addInterceptor(remote).build())
        measure(vm, "dns-during-inventory")
        assertEquals(2, attempts.get())
        assertEquals(before, repo.allReceipts.first())
        assertTrue(vm.isDriveConnected.value)
        val report = requireNotNull(vm.driveSyncReport.value)
        assertEquals(22, report.notAttempted)
        assertTrue(report.steps.any { it.failure?.kind == "DNS" })
        assertFalse(report.germanText().contains("private details"))
        assertTrue(remote.requests.all { it.method == "GET" })
    }

    @Test fun cloudActiveReceiptMissingLocallyCannotBeOverwrittenByPartialLocalBackup() = runBlocking {
        seedReceipts(2)
        val vm = connectedModel()
        val index = remote.entries.getValue("phase11e-index")
        val source = JSONObject(index.bytes.toString(Charsets.UTF_8))
        source.getJSONArray("entries").getJSONObject(1).put("internalId", "unknown-cloud-active")
        index.bytes = source.toString().toByteArray()
        val result = measure(vm, "unknown-cloud-active")
        assertEquals(0, result.getInt("filesCreate") + result.getInt("filesUpdate"))
        assertEquals(2, vm.driveSyncReport.value!!.notAttempted)
        val failure = vm.driveSyncReport.value!!.steps.first {
            it.failure?.check == com.example.api.DriveSyncCheck.INDEX_RECEIPT_NOT_LOCAL
        }.failure!!
        assertEquals("CONFLICT", failure.kind)
        assertNull(failure.httpStatus)
    }

    @Test fun failedReportRemainsAvailableAfterViewModelRestart() = runBlocking {
        seedReceipts(1)
        val vm = connectedModel()
        remote.entries.remove(repo.allReceipts.first().single().driveMetadataFileId)
        measure(vm, "report-before-restart")
        val previous = vm.driveSyncReport.value!!.germanText()
        stores.forEach { it.clear() }; stores.clear()
        val next = model()
        assertEquals(previous, next.driveSyncReport.value!!.germanText())
        assertFalse(prefs.getBoolean("auto_backup", true))
    }

    @Test fun changedOriginalUpdatesSameIdAndThenBecomesNoOp() = runBlocking {
        seedReceipts(1, localBytes = true)
        val before = repo.allReceipts.first().single()
        val updatedBytes = File(before.imageUrl).readBytes() + byteArrayOf(41, 42, 43)
        File(before.imageUrl).writeBytes(updatedBytes)
        remote.requests.clear()
        assertTrue(drive.syncReceiptToDrive(token, config, before))
        val after = repo.allReceipts.first().single()
        assertEquals(before.driveFileId, after.driveFileId)
        assertEquals(before.driveMetadataFileId, after.driveMetadataFileId)
        assertEquals(updatedBytes.size.toLong(), after.fileSizeBytes)
        assertArrayEquals(updatedBytes, remote.entries.getValue(requireNotNull(before.driveFileId)).bytes)
        assertEquals(1, remote.requests.count { it.method == "PATCH" && it.url.queryParameter("uploadType") == "media" && it.url.encodedPath.endsWith("/${before.driveFileId}") })
        assertFalse(remote.requests.any { it.method == "POST" })
        remote.requests.clear()
        assertTrue(drive.syncReceiptToDrive(token, config, after))
        assertTrue(remote.requests.all { it.method == "GET" })
    }

    @Test fun acceptedMediaUpdateWithLostResponseResumesAfterRepositoryRestartWithoutDuplicateOrReupload() = runBlocking {
        seedReceipts(1, localBytes = true)
        val before = repo.allReceipts.first().single()
        val id = requireNotNull(before.driveFileId)
        val bytes = File(before.imageUrl).readBytes() + byteArrayOf(61, 62)
        File(before.imageUrl).writeBytes(bytes)
        val transfers = java.util.concurrent.atomic.AtomicInteger()
        transport.set(null, OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (request.method == "PATCH" && request.url.encodedPath.endsWith("/$id") && request.url.queryParameter("uploadType") == "media" && transfers.incrementAndGet() == 1) {
                response.close()
                throw java.net.SocketTimeoutException("synthetic lost acknowledgement")
            }
            response
        }.addInterceptor(remote).build())
        assertFalse(drive.syncReceiptToDrive(token, config, before))
        assertArrayEquals(bytes, remote.entries.getValue(id).bytes)
        assertEquals(id, repo.allReceipts.first().single().driveFileId)
        val restarted = DrivePersistenceRepository(app, repo)
        assertTrue(restarted.syncReceiptToDrive(token, config, repo.allReceipts.first().single()))
        assertEquals(1, transfers.get())
        assertEquals(1, remote.entries.values.count { it.id == id })
        assertEquals(before.driveMetadataFileId, repo.allReceipts.first().single().driveMetadataFileId)
    }

    @Test fun acceptedNewOriginalWithLostPostResponseReusesReservedIdWithoutAnotherPost() = runBlocking {
        prefs.edit().putString("connected_email", "synthetic-reservation@example.invalid").commit()
        val bytes = byteArrayOf(0x25, 0x50, 0x44, 0x46) + ByteArray(64) { 3 }
        val posts = java.util.concurrent.atomic.AtomicInteger()
        transport.set(null, OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (request.method == "POST" && request.url.encodedPath.startsWith("/upload/") && posts.incrementAndGet() == 1) {
                response.close()
                throw java.net.SocketTimeoutException("synthetic accepted POST, lost response")
            }
            response
        }.addInterceptor(remote).build())
        val props = mapOf("appName" to "ImmobilienBelegApp", "receiptInternalId" to "new-stable-synthetic", "documentRole" to "ORIGINAL")
        val first = GoogleDriveClient.withCreateRecovery(app) {
            GoogleDriveClient.uploadFile(token, config.receiptsFolderId, "new.pdf", "application/pdf", bytes, appProperties = props)
        }
        assertNull(first)
        val accepted = remote.entries.values.single { it.properties["receiptInternalId"] == "new-stable-synthetic" }
        val second = GoogleDriveClient.withCreateRecovery(app) {
            GoogleDriveClient.uploadFile(token, config.receiptsFolderId, "new.pdf", "application/pdf", bytes, appProperties = props)
        }
        assertEquals(accepted.id, second)
        assertEquals(1, posts.get())
        assertArrayEquals(bytes, accepted.bytes)
        assertEquals(1, remote.entries.values.count { it.properties["receiptInternalId"] == "new-stable-synthetic" })
        assertEquals(1, remote.requests.count { it.url.encodedPath.endsWith("/generateIds") })
    }

    private suspend fun blockedHttp(code: Int, retryAfter: String? = null): Pair<ReceiptViewModel, Int> {
        seedReceipts(22)
        val vm = connectedModel()
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        transport.set(null, OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            attempts.incrementAndGet()
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(code).message("Synthetic controlled fault")
                .apply { retryAfter?.let { header("Retry-After", it) } }
                .body(okhttp3.ResponseBody.create(null, "{}")).build()
        }.build())
        measure(vm, "http-$code-${retryAfter ?: "none"}")
        assertTrue(vm.isDriveConnected.value)
        assertEquals(22, repo.allReceipts.first().size)
        assertEquals(22, vm.driveSyncReport.value!!.notAttempted)
        assertNull(prefs.getString("last_stammdaten_backup_time", null))
        return vm to attempts.get()
    }

    @Test fun forbiddenResponseIsNotRetriedAndNeverSignsOut() = runBlocking {
        val (vm, calls) = blockedHttp(403)
        assertEquals(1, calls)
        assertTrue(vm.driveSyncReport.value!!.steps.any { it.failure?.httpStatus == 403 })
    }

    @Test fun rateLimitUsesBoundedRetryAfterWithoutWriteReplay() = runBlocking {
        val (vm, calls) = blockedHttp(429, "0")
        assertEquals(2, calls)
        assertEquals(1, vm.driveSyncReport.value!!.steps.sumOf { it.retries })
    }

    @Test fun longRetryAfterStopsInsteadOfWaitingMinutesOrRetryingTooEarly() = runBlocking {
        val (_, calls) = blockedHttp(429, "30")
        assertEquals(1, calls)
    }

    @Test fun unavailableServiceHasExactlyOneBoundedReadRetry() = runBlocking {
        val (vm, calls) = blockedHttp(503)
        assertEquals(2, calls)
        assertEquals(1, vm.driveSyncReport.value!!.steps.sumOf { it.retries })
    }

    @Test fun dnsBeforeInitializationLeavesAllTwentyTwoReceiptsNotAttempted() = runBlocking {
        seedReceipts(22)
        val vm = connectedModel()
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        transport.set(null, OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor {
            attempts.incrementAndGet(); throw java.net.UnknownHostException("synthetic DNS failure")
        }.build())
        measure(vm, "dns-before-initialization")
        assertEquals(2, attempts.get())
        assertEquals(22, vm.driveSyncReport.value!!.notAttempted)
        assertTrue(vm.driveSyncReport.value!!.steps.any { it.failure?.kind == "DNS" })
        assertTrue(vm.isDriveConnected.value)
        assertFalse(prefs.getBoolean("auto_backup", true))
    }

    @Test fun cancellingIndependentParallelVerificationNeverStartsAnotherWrite() = runBlocking {
        seedReceipts(22)
        val vm = connectedModel()
        measure(vm, "before-cancel")
        val before = remote.inventory(config.rootFolderId)
        val concurrent = java.util.concurrent.atomic.AtomicInteger()
        val entered = CompletableDeferred<Unit>()
        val release = java.util.concurrent.CountDownLatch(1)
        transport.set(null, OkHttpClient.Builder().addInterceptor { chain ->
            if (vm.driveSyncReport.value?.currentPhase == com.example.api.DriveSyncPhase.COMPLETION) {
                if (concurrent.incrementAndGet() >= 2) entered.complete(Unit)
                try { check(release.await(5, java.util.concurrent.TimeUnit.SECONDS)) }
                finally { concurrent.decrementAndGet() }
            }
            chain.proceed(chain.request())
        }.addInterceptor(remote).build())
        remote.requests.clear()
        try {
            vm.syncAllToDrive()
            withTimeout(10_000) { entered.await() }
            val field = ReceiptViewModel::class.java.getDeclaredField("driveSyncJob").apply { isAccessible = true }
            val running = field.get(vm)
            repeat(3) { vm.syncAllToDrive() }
            assertSame(running, field.get(vm))
            vm.cancelDriveSynchronization()
            vm.syncAllToDrive() // A cancelling HTTP operation still owns the single-flight slot.
            assertSame(running, field.get(vm))
        } finally { release.countDown() }
        awaitJob(vm, "driveSyncJob")
        assertEquals(com.example.api.DriveSyncOutcome.CANCELLED, vm.driveSyncReport.value!!.outcome)
        assertTrue(remote.requests.all { it.method == "GET" })
        assertEquals(before, remote.inventory(config.rootFolderId))
        assertTrue(vm.isDriveConnected.value)
    }

    @Test fun queuedAutomaticBackupSharesMutationGateAndRemainsDisabled() = runBlocking {
        seedReceipts(1)
        val vm = connectedModel()
        val gate = ReceiptViewModel::class.java.getDeclaredField("driveMutationGate").apply { isAccessible = true }
            .get(vm) as kotlinx.coroutines.sync.Mutex
        val upload = ReceiptViewModel::class.java.getDeclaredMethod("uploadReceiptToDriveInternal", Receipt::class.java)
            .apply { isAccessible = true }
        val parent = vm.viewModelScope.coroutineContext[Job]!!
        val existing = parent.children.toSet()
        remote.requests.clear()
        gate.lock()
        val queued: Job
        try {
            upload.invoke(vm, repo.getReceiptById(1)!!)
            queued = withTimeout(5_000) {
                var child: Job? = null
                while (child == null) { child = parent.children.firstOrNull { it !in existing }; yield() }
                requireNotNull(child)
            }
            assertFalse(queued.isCompleted)
            assertTrue(remote.requests.isEmpty())
            assertFalse(prefs.getBoolean("auto_backup", true))
        } finally { gate.unlock() }
        withTimeout(5_000) { queued.join() }
        assertTrue(remote.requests.isEmpty())
        assertFalse(prefs.getBoolean("auto_backup", true))
        assertTrue(vm.isDriveConnected.value)
    }

    private suspend fun removeOriginalBaseline(id: Int, removeSavedHash: Boolean = true): ManagedDocument {
        val row = repo.getReceiptById(id)!!
        if (removeSavedHash) {
            val metadata = remote.entries.getValue(row.driveMetadataFileId!!)
            metadata.bytes = JSONObject(metadata.bytes.toString(Charsets.UTF_8)).apply {
                put("schemaVersion", 1); remove("originalDocuments")
            }.toString().toByteArray()
        }
        val document = repo.getAllManagedDocuments().single { it.receiptInternalId == row.internalId }
        val missing = document.copy(sha256 = "")
        db.managedDocumentDao().upsert(missing)
        return missing
    }

    @Test fun unchangedCloudOnlyLegacyWithoutHashDoesNotOverwriteOrInventBaseline() = runBlocking {
        seedReceipts(1)
        val missing = removeOriginalBaseline(1)
        val cloudBytes = remote.entries.getValue(missing.driveFileId!!).bytes.copyOf()
        val result = measure(connectedModel(), "cloud-only-legacy-unmodified")
        assertFalse(result.toString(), result.getBoolean("hasSyncError"))
        assertEquals(0, result.getInt("originalCreates") + result.getInt("originalUpdates"))
        assertArrayEquals(cloudBytes, remote.entries.getValue(missing.driveFileId).bytes)
        assertEquals("", repo.getManagedDocument(missing.documentId)!!.sha256)
    }

    @Test fun editedCloudOnlyLegacyWithoutHashMustStillBlockWrites() = runBlocking {
        seedReceipts(1)
        val missing = removeOriginalBaseline(1)
        val row = repo.getReceiptById(1)!!
        repo.insert(row.copy(aussteller = "LOCAL_CHANGED_AFTER_RESTORE"))
        val cloudBytes = remote.entries.getValue(missing.driveFileId!!).bytes.copyOf()
        val result = measure(connectedModel(), "cloud-only-legacy-edited")
        assertTrue(result.toString(), result.getBoolean("hasSyncError"))
        assertEquals(0, result.getInt("filesCreate") + result.getInt("filesUpdate"))
        assertArrayEquals(cloudBytes, remote.entries.getValue(missing.driveFileId).bytes)
        assertEquals("", repo.getManagedDocument(missing.documentId)!!.sha256)
    }

    @Test fun changedLocalCloudOnlyLegacyReferenceBlocksBeforeAnyCloudWrite() = runBlocking {
        seedReceipts(1)
        val missing = removeOriginalBaseline(1)
        val reference = repo.getDocumentsForReceipt(missing.receiptInternalId!!).single()
        repo.insertDocument(reference.copy(filename = "LOCAL_CHANGED_AFTER_RESTORE.png"))
        val before = remote.inventory(config.rootFolderId)
        val cloudBytes = remote.entries.getValue(missing.driveFileId!!).bytes.copyOf()
        val result = measure(connectedModel(), "cloud-only-legacy-reference-edited")
        assertTrue(result.toString(), result.getBoolean("hasSyncError"))
        assertEquals(0, result.getInt("filesCreate") + result.getInt("filesUpdate"))
        assertEquals(before, remote.inventory(config.rootFolderId))
        assertArrayEquals(cloudBytes, remote.entries.getValue(missing.driveFileId).bytes)
        assertEquals("", repo.getManagedDocument(missing.documentId)!!.sha256)
        assertEquals("LOCAL_CHANGED_AFTER_RESTORE.png", repo.getDocumentsForReceipt(missing.receiptInternalId).single().filename)
    }

    @Test fun legacyBaselineCanBeEstablishedFromMatchingIndependentLocalOriginal() = runBlocking {
        seedReceipts(3, localBytes = true)
        val missing = removeOriginalBaseline(3)
        val originalBytes = remote.entries.getValue(missing.driveFileId!!).bytes.copyOf()
        val vm = connectedModel()
        val result = measure(vm, "legacy-baseline-local-copy")
        assertFalse(result.toString(), result.getBoolean("hasSyncError"))
        assertEquals(StableDocumentIdentity.sha256(originalBytes), repo.getManagedDocument(missing.documentId)!!.sha256)
        assertEquals(0, result.getInt("originalCreates") + result.getInt("originalUpdates"))
        assertArrayEquals(originalBytes, remote.entries.getValue(missing.driveFileId).bytes)
        val next = measure(vm, "legacy-baseline-identical-repeat")
        assertFalse(next.toString(), next.getBoolean("hasSyncError"))
        assertEquals(0, next.getInt("filesCreate") + next.getInt("filesUpdate"))
        assertEquals(0, next.getInt("originalMediaGets"))
    }

    @Test fun savedOriginalHashRepairsOnlyMissingLocalHashWithCloudOnlyOriginal() = runBlocking {
        seedReceipts(1)
        val missing = removeOriginalBaseline(1, removeSavedHash = false)
        val vm = connectedModel()
        val result = measure(vm, "baseline-saved-hash")
        assertFalse(result.toString(), result.getBoolean("hasSyncError"))
        assertFalse(repo.getManagedDocument(missing.documentId)!!.sha256.isBlank())
        assertEquals(0, result.getInt("originalCreates") + result.getInt("originalUpdates"))
    }

    @Test fun changedLegacyOriginalCannotBeAdoptedBySizeOrReachability() = runBlocking {
        seedReceipts(1, localBytes = true)
        val missing = removeOriginalBaseline(1)
        val original = remote.entries.getValue(missing.driveFileId!!)
        original.bytes = original.bytes.copyOf().also { it[it.lastIndex] = 99 }
        original.revision++
        val result = measure(connectedModel(), "baseline-different-bytes-same-size")
        assertTrue(result.getBoolean("hasSyncError"))
        assertEquals(0, result.getInt("filesCreate") + result.getInt("filesUpdate"))
        assertEquals("", repo.getManagedDocument(missing.documentId)!!.sha256)
        assertTrue(result.toString().contains("ORIGINAL_BASELINE_CONTENT_MISMATCH"))
    }

    @Test fun changedCloudVersionDuringBaselineReadKeepsMissingHashAndOriginal() = runBlocking {
        seedReceipts(1, localBytes = true)
        val missing = removeOriginalBaseline(1)
        transport.set(null, OkHttpClient.Builder().addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (chain.request().url.encodedPath.endsWith("/" + missing.driveFileId) && chain.request().url.queryParameter("alt") == "media")
                remote.entries.getValue(missing.driveFileId!!).revision++
            response
        }.addInterceptor(remote).build())
        val result = measure(connectedModel(), "baseline-cloud-race")
        assertEquals(0, result.getInt("filesCreate") + result.getInt("filesUpdate"))
        assertTrue(result.toString().contains("ORIGINAL_BASELINE_CHANGED_DURING_READ"))
        assertEquals("", repo.getManagedDocument(missing.documentId)!!.sha256)
    }

    @Test fun concurrentLocalDocumentEditIsNotOverwrittenByBaselineAdoption() = runBlocking {
        seedReceipts(1, localBytes = true)
        val missing = removeOriginalBaseline(1)
        transport.set(null, OkHttpClient.Builder().addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (chain.request().url.encodedPath.endsWith("/" + missing.driveFileId) && chain.request().url.queryParameter("alt") == "media")
                runBlocking { db.managedDocumentDao().upsert(missing.copy(title = "Concurrent synthetic edit")) }
            response
        }.addInterceptor(remote).build())
        val result = measure(connectedModel(), "baseline-local-race")
        assertEquals(0, result.getInt("filesCreate") + result.getInt("filesUpdate"))
        assertEquals("", repo.getManagedDocument(missing.documentId)!!.sha256)
        assertEquals("Concurrent synthetic edit", repo.getManagedDocument(missing.documentId)!!.title)
        assertTrue(result.toString().contains("LOCAL_RECEIPT_CHANGED"))
    }

    @Test fun cancellationDuringBaselineReadDoesNotAdoptOrUpload() = runBlocking {
        seedReceipts(1, localBytes = true)
        val missing = removeOriginalBaseline(1)
        transport.set(null, OkHttpClient.Builder().addInterceptor { chain ->
            if (chain.request().url.encodedPath.endsWith("/" + missing.driveFileId) && chain.request().url.queryParameter("alt") == "media")
                throw CancellationException("Synthetic user cancellation")
            chain.proceed(chain.request())
        }.addInterceptor(remote).build())
        val vm = connectedModel()
        val result = measure(vm, "baseline-cancelled")
        assertEquals(com.example.api.DriveSyncOutcome.CANCELLED, vm.driveSyncReport.value!!.outcome)
        assertEquals(0, result.getInt("filesCreate") + result.getInt("filesUpdate"))
        assertEquals("", repo.getManagedDocument(missing.documentId)!!.sha256)
        assertTrue(vm.isDriveConnected.value)
    }

    @Test fun renamedOriginalCannotBootstrapFromAnUnrelatedReference() = runBlocking {
        seedReceipts(1, localBytes = true)
        val missing = removeOriginalBaseline(1)
        remote.entries.getValue(missing.driveFileId!!).name = "Different synthetic original.png"
        val result = measure(connectedModel(), "baseline-binding-conflict")
        assertEquals(0, result.getInt("filesCreate") + result.getInt("filesUpdate"))
        assertEquals("", repo.getManagedDocument(missing.documentId)!!.sha256)
        assertTrue(result.toString().contains("ORIGINAL_BASELINE_REFERENCE_MISMATCH"))
    }

    @Test fun primaryBaselineConflictAndCancelledSiblingChecksRemainDistinct() = runBlocking {
        val recorder = com.example.api.DriveSyncRecorder({})
        val started = java.util.concurrent.atomic.AtomicInteger()
        val ready = CompletableDeferred<Unit>()
        try {
            com.example.api.DriveSyncTrace.run(recorder) {
                coroutineScope {
                    repeat(2) { index -> launch {
                        com.example.api.DriveSyncTrace.step(com.example.api.DriveSyncPhase.METADATA, "synthetic-sibling-$index") {
                            if (started.incrementAndGet() == 2) ready.complete(Unit)
                            awaitCancellation()
                        }
                    } }
                    launch {
                        ready.await()
                        com.example.api.DriveSyncTrace.step(com.example.api.DriveSyncPhase.METADATA, "synthetic-primary") {
                            throw com.example.api.DriveSyncConflict(com.example.api.DriveSyncCheck.ORIGINAL_BASELINE_MISSING)
                        }
                    }
                }
            }
            fail("Primary conflict must propagate")
        } catch (_: com.example.api.DriveSyncConflict) { }
        recorder.finish(com.example.api.DriveSyncOutcome.BLOCKED)
        val report = recorder.snapshot()
        assertEquals(1, report.steps.count { it.outcome == com.example.api.DriveSyncOutcome.BLOCKED })
        assertEquals(2, report.steps.count { it.outcome == com.example.api.DriveSyncOutcome.CANCELLED })
        assertTrue(report.germanText().contains("Ursprünglicher Sicherheitskonflikt:"))
        assertTrue(report.germanText().contains("Dadurch abgebrochene parallele Prüfungen: 2"))
        assertEquals(report, com.example.api.DriveSyncReport.fromJson(report.toJson()))
    }

    @Test fun legacyStockPlusOneNewReceiptRemainsTwentyThreeWithNoDuplicateOriginals() = runBlocking {
        seedReceipts(22, localBytes = true)
        removeOriginalBaseline(3)
        val before = repo.allReceipts.first().associate { it.internalId to it.driveFileId }
        val template = repo.getReceiptById(1)!!
        val file = File(app.filesDir, "baseline-new-23.png").apply { writeBytes(File(template.imageUrl).readBytes()) }
        repo.insert(template.copy(id = 23, internalId = "synthetic-added-23", displayId = "BLG-2026-000023",
            imageUrl = file.absolutePath, driveFileId = null, driveMetadataFileId = null, driveFolderId = null,
            storedFilename = null, originalMimeType = null, fileSizeBytes = 0, driveRevision = null,
            syncStatus = "PENDING", lastSyncedAt = null, isArchivedToDrive = false))
        val vm = connectedModel()
        val result = measure(vm, "baseline-22-plus-one-new")
        assertFalse(result.toString(), result.getBoolean("hasSyncError"))
        assertEquals(1, result.getInt("originalCreates"))
        assertEquals(0, result.getInt("originalUpdates"))
        assertEquals(23, repo.allReceipts.first().size)
        before.forEach { (id, driveId) -> assertEquals(driveId, repo.getReceiptByInternalId(id)!!.driveFileId) }
        val noOp = measure(vm, "baseline-23-repeat-noop")
        assertFalse(noOp.toString(), noOp.getBoolean("hasSyncError"))
        assertEquals(0, noOp.getInt("filesCreate") + noOp.getInt("filesUpdate"))
        assertEquals(0, noOp.getInt("originalMediaGets"))
    }

    @Test fun verifiedBaselineSurvivesIndexFailureAndViewModelRestartWithoutDuplicateOriginals() = runBlocking {
        seedReceipts(1, localBytes = true)
        val missing = removeOriginalBaseline(1)
        transport.set(null, OkHttpClient.Builder().addInterceptor { chain ->
            if (chain.request().method == "PATCH" && chain.request().url.encodedPath.endsWith("/phase11e-index"))
                okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(503).message("Synthetic index failure").body(okhttp3.ResponseBody.create(null, "{}")).build()
            else chain.proceed(chain.request())
        }.addInterceptor(remote).build())
        val first = measure(connectedModel(), "baseline-index-failure")
        assertTrue(first.getBoolean("hasSyncError"))
        assertFalse(repo.getManagedDocument(missing.documentId)!!.sha256.isBlank())
        stores.forEach { it.clear() }; models.forEach { it.viewModelScope.coroutineContext[Job]?.join() }
        transport.set(null, OkHttpClient.Builder().addInterceptor(remote).build())
        val repeated = measure(connectedModel(), "baseline-resume-after-restart")
        assertFalse(repeated.toString(), repeated.getBoolean("hasSyncError"))
        assertEquals(0, repeated.getInt("originalCreates") + repeated.getInt("originalUpdates"))
        assertEquals(missing.driveFileId, repo.getManagedDocument(missing.documentId)!!.driveFileId)
        assertEquals(1, repo.allReceipts.first().size)
    }

    @Test fun editedLegacyOriginalWithoutHashIsNotReportedAsAnActualHttp412() = runBlocking {
        seedReceipts(3)
        val row = repo.getReceiptById(3)!!
        val metadata = remote.entries.getValue(row.driveMetadataFileId!!)
        val payload = JSONObject(metadata.bytes.toString(Charsets.UTF_8))
        payload.remove("originalDocuments")
        metadata.bytes = payload.toString().toByteArray(Charsets.UTF_8)
        val original = repo.getAllManagedDocuments().single { it.receiptInternalId == row.internalId }
        db.managedDocumentDao().upsert(original.copy(sha256 = "", localUri = ""))
        // Unchanged cloud-only originals are a read-only no-op since PR #157.
        // A local edit must still fail before writes, with a local check rather than HTTP 412.
        repo.insert(row.copy(aussteller = "LOCAL_CHANGED_AFTER_RESTORE"))
        val cloudBytes = remote.entries.getValue(original.driveFileId!!).bytes.copyOf()
        val vm = connectedModel()
        val result = measure(vm, "legacy-original-hash-missing")
        assertTrue(result.getBoolean("hasSyncError"))
        assertEquals(0, result.getInt("filesCreate") + result.getInt("filesUpdate"))
        val report = vm.driveSyncReport.value!!
        assertEquals(3, report.notAttempted)
        assertTrue(report.germanText(), report.germanText().contains("ORIGINAL_BASELINE_REFERENCE_MISMATCH"))
        assertTrue(report.steps.filter { it.failure?.check != null }.all { it.failure?.httpStatus == null })
        assertFalse(report.germanText().contains("HTTP=412"))
        assertArrayEquals(cloudBytes, remote.entries.getValue(original.driveFileId).bytes)
        assertEquals("", repo.getManagedDocument(original.documentId)!!.sha256)
        assertTrue(vm.isDriveConnected.value)
    }

    @Test fun unchangedBomEncodedJsonIsVerifiedAgainstItsActualFileBytes() = runBlocking {
        seedReceipts(1)
        val row = repo.getReceiptById(1)!!
        val metadata = remote.entries.getValue(row.driveMetadataFileId!!)
        metadata.bytes = byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + metadata.bytes
        val vm = connectedModel()
        val result = measure(vm, "utf8-bom-json")
        assertFalse(result.toString(), result.getBoolean("hasSyncError"))
        assertEquals(0, result.getInt("originalCreates") + result.getInt("originalUpdates"))
        val noOp = measure(vm, "utf8-bom-json-noop")
        assertFalse(noOp.toString(), noOp.getBoolean("hasSyncError"))
        assertEquals(0, noOp.getInt("filesCreate") + noOp.getInt("filesUpdate"))
    }

    @Test fun receiptConflictReportsOnlyFieldNamesAndRevisionEvidenceAfterRestart() = runBlocking {
        seedReceipts(1)
        val row = repo.getReceiptById(1)!!
        val metadata = remote.entries.getValue(row.driveMetadataFileId!!)
        val payload = JSONObject(metadata.bytes.toString(Charsets.UTF_8))
            .put("revision", 3).put("aussteller", "REMOTE_PRIVATE_CONTENT_MUST_NOT_BE_LOGGED")
        metadata.bytes = payload.toString().toByteArray(Charsets.UTF_8)
        repo.insert(row.copy(aussteller = "LOCAL_PRIVATE_CONTENT_MUST_NOT_BE_LOGGED"))
        val vm = connectedModel()
        val result = measure(vm, "detailed-revision-conflict")
        assertEquals(0, result.getInt("filesCreate") + result.getInt("filesUpdate"))
        val report = vm.driveSyncReport.value!!
        val failure = report.steps.first { it.failure?.check == com.example.api.DriveSyncCheck.RECEIPT_REVISION_MISMATCH }.failure!!
        assertNull(failure.httpStatus)
        assertEquals(1L, failure.localRevision)
        assertEquals(3L, failure.driveRevision)
        assertEquals(listOf("aussteller"), failure.differentFields)
        assertFalse(report.germanText().contains("PRIVATE_CONTENT"))
        assertFalse(report.toJson().contains("PRIVATE_CONTENT"))
        assertEquals(report, com.example.api.DriveSyncReport.fromJson(report.toJson()))
        stores.forEach { it.clear() }; models.forEach { it.viewModelScope.coroutineContext[Job]?.join() }
        val restarted = model().driveSyncReport.value!!
        assertEquals(failure, restarted.steps.first { it.failure?.check == failure.check }.failure)
        assertEquals(row.driveFileId, repo.getReceiptById(row.id)!!.driveFileId)
    }

    @Test fun actualServerHttp412RemainsDistinctFromLocalSafetyChecks() = runBlocking {
        seedReceipts(1)
        val row = repo.getReceiptById(1)!!
        val vm = connectedModel()
        transport.set(null, OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            if (chain.request().url.encodedPath.endsWith("/" + row.driveMetadataFileId) && chain.request().url.queryParameter("alt") == "media")
                okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(412)
                    .message("Synthetic server precondition").body(okhttp3.ResponseBody.create(null, "{}")).build()
            else chain.proceed(chain.request())
        }.addInterceptor(remote).build())
        measure(vm, "actual-server-http-412")
        val report = vm.driveSyncReport.value!!
        assertTrue(report.steps.any { it.failure?.httpStatus == 412 && it.failure?.check == null })
        assertTrue(report.germanText().contains("HTTP=412"))
        assertFalse(report.germanText().contains("Prüfcode="))
    }

    @Test fun utf16BomJsonKeepsRawByteProofWithoutRewritingTheFile() = runBlocking {
        for ((charset, bom) in listOf(Charsets.UTF_16LE to byteArrayOf(255.toByte(), 254.toByte()),
                Charsets.UTF_16BE to byteArrayOf(254.toByte(), 255.toByte()))) {
            val content = JSONObject().put("text", "Synthetische Prüfung 😀").toString()
            val id = "synthetic-encoding-" + charset.name().replace("-", "")
            remote.put(id, id + ".json", config.systemFolderId, content)
            val entry = remote.entries.getValue(id)
            entry.bytes = bom + content.toByteArray(charset)
            remote.requests.clear()
            GoogleDriveClient.withSyncReadProof(token) {
                assertEquals(content, GoogleDriveClient.downloadSyncJsonSnapshot(token, id).read.json)
            }
            assertTrue(remote.requests.all { it.method == "GET" })
            assertArrayEquals(bom + content.toByteArray(charset), entry.bytes)
        }
    }

    @Test fun malformedUtf8IsBlockedInsteadOfBeingSilentlyReplaced() = runBlocking {
        val id = "synthetic-malformed-encoding"
        remote.put(id, "malformed.json", config.systemFolderId, "{}")
        remote.entries.getValue(id).bytes = "{\"text\":\"".toByteArray() + byteArrayOf(128.toByte()) + "\"}".toByteArray()
        remote.requests.clear()
        try {
            GoogleDriveClient.withSyncReadProof(token) { GoogleDriveClient.downloadSyncJsonSnapshot(token, id) }
            fail("Invalid UTF-8 must not become replacement characters in a writable snapshot")
        } catch (error: com.example.api.DriveSyncConflict) {
            assertEquals(com.example.api.DriveSyncCheck.JSON_ENCODING_INVALID, error.check)
            assertNull(com.example.api.DriveSyncFailure.from(error).httpStatus)
        }
        assertTrue(remote.requests.all { it.method == "GET" })
    }

    @Test fun uppercaseOriginalDigestIsEquivalentAndLostMediaAckStillResumesSafely() = runBlocking {
        seedReceipts(1, localBytes = true)
        val row = repo.getReceiptById(1)!!
        val metadata = remote.entries.getValue(row.driveMetadataFileId!!)
        val json = JSONObject(metadata.bytes.toString(Charsets.UTF_8))
        val document = repo.getAllManagedDocuments().single { it.receiptInternalId == row.internalId }
        val uppercase = document.sha256.uppercase(java.util.Locale.ROOT)
        json.getJSONArray("originalDocuments").getJSONObject(0).put("sha256", uppercase)
        metadata.bytes = json.toString().toByteArray(Charsets.UTF_8)
        db.managedDocumentDao().upsert(document.copy(sha256 = uppercase))
        val vm = connectedModel()
        val noOp = measure(vm, "uppercase-original-hash")
        assertFalse(noOp.toString(), noOp.getBoolean("hasSyncError"))
        assertEquals(0, noOp.getInt("originalCreates") + noOp.getInt("originalUpdates"))
        val bytes = File(row.imageUrl).readBytes() + byteArrayOf(71, 72)
        File(row.imageUrl).writeBytes(bytes)
        val transfers = java.util.concurrent.atomic.AtomicInteger()
        transport.set(null, OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (request.method == "PATCH" && request.url.encodedPath.endsWith("/" + row.driveFileId) &&
                request.url.queryParameter("uploadType") == "media" && transfers.incrementAndGet() == 1) {
                response.close(); throw java.net.SocketTimeoutException("synthetic lost acknowledgement")
            }
            response
        }.addInterceptor(remote).build())
        assertFalse(drive.syncReceiptToDrive(token, config, repo.getReceiptById(row.id)!!))
        val restarted = DrivePersistenceRepository(app, repo)
        GoogleDriveClient.withSyncReadProof(token) {
            restarted.validateSynchronizationInventory(token, config, repo.allReceipts.first())
            assertTrue(restarted.syncReceiptToDrive(token, config, repo.getReceiptById(row.id)!!))
        }
        assertEquals(1, transfers.get())
        assertEquals(row.driveFileId, repo.getReceiptById(row.id)!!.driveFileId)
        assertArrayEquals(bytes, remote.entries.getValue(row.driveFileId!!).bytes)
    }

    @Test fun twentyTwoRestoredCloudOnlyReceiptsDoNotRequireLocalOriginalCache() = runBlocking {
        seedReceipts(22)
        val vm=connectedModel()
        val result=measure(vm,"restored-22")
        measure(vm,"restored-22-warm")
        assertTrue(result.toString(),vm.driveSyncError.value==null)
        assertEquals(22,repo.allReceipts.first().count { it.syncStatus=="SYNCED" })
        assertEquals(0,result.getInt("originalMediaGets"))
        assertFalse(prefs.getBoolean("auto_backup",true))
    }

    @Test fun deletedIndexHistoryDoesNotMakeEveryActiveReceiptFail() = runBlocking {
        seedReceipts(1,localBytes=true)
        val file=remote.entries.getValue("phase11e-index")
        val index=JSONObject(file.bytes.toString(Charsets.UTF_8))
        index.getJSONArray("entries").put(JSONObject().put("internalId","synthetic-deleted-history")
            .put("metadataFileId","absent-historical-metadata").put("mainDriveFileId","absent-historical-original")
            .put("syncStatus","DELETED"))
        file.bytes=index.toString().toByteArray()
        val vm=connectedModel()
        val result=measure(vm,"deleted-history")
        assertTrue(result.toString(),vm.driveSyncError.value==null)
        assertTrue(JSONObject(file.bytes.toString(Charsets.UTF_8)).getJSONArray("entries").toString().contains("synthetic-deleted-history"))
    }

    @Test fun unchangedSupplementalRetainsTwoUnavailableHistoricalOriginals() = runBlocking {
        repeat(2) { i -> repo.upsertManagedDocument(ManagedDocument(documentId="synthetic-history-$i",
            propertyId=StableDocumentIdentity.LEGACY_PROPERTY_ID,receiptInternalId="synthetic-deleted-$i",
            originalFilename="retained-$i.png",storedFilename="retained-$i.png",mimeType="image/png",
            driveFileId="absent-supplemental-$i",driveFolderId=config.receiptsFolderId,
            createdAt="2026-01-01T00:00:00Z",updatedAt="2026-01-01T00:00:00Z")) }
        val payload=SupplementalDriveBackup.createPayload(app,db).toString(4)
        remote.put("phase11e-supplemental","supplementalBackup.json",config.systemFolderId,payload,"supplementalBackup")
        remote.requests.clear()
        val result=SupplementalDriveBackup.backup(app,db,token,config.systemFolderId)
        File("build/reports/phase11e").mkdirs()
        File("build/reports/phase11e/supplemental-404.json").writeText(JSONObject().put("success",result.success)
            .put("message",result.message).put("apiCalls",remote.requests.size).toString(2))
        assertTrue(result.message,result.success)
        assertEquals(payload,remote.entries.getValue("phase11e-supplemental").bytes.toString(Charsets.UTF_8))
        assertTrue(remote.requests.all { it.method=="GET" })
    }

    @Test fun initializationDoesNotStampAnUnperformedCompleteBackup() = runBlocking {
        prefs.edit().putString("last_stammdaten_backup_time","known-complete-backup").commit()
        val vm=connectedModel()
        assertEquals("known-complete-backup",vm.lastStammdatenBackupTime.value)
        assertEquals("known-complete-backup",prefs.getString("last_stammdaten_backup_time",null))
    }
    @Test fun preserved404CannotBeReboundToAnotherEntityOrNewField() = runBlocking {
        val old=JSONObject().put("managedDocuments",JSONArray().put(JSONObject().put("documentId","history-a").put("driveFileId","missing-original")))
        remote.put("guard-source","guard.json",config.systemFolderId,old.toString())
        val source=GoogleDriveClient.downloadSyncJsonSnapshot(token,"guard-source")
        val before=remote.entries.getValue("guard-source").bytes.toList()
        for(candidate in listOf(
            JSONObject(old.toString()).apply { getJSONArray("managedDocuments").getJSONObject(0).put("documentId","history-b") },
            JSONObject(old.toString()).put("driveFileId","missing-original"))) {
            try { GoogleDriveClient.updateSyncJson(token,"guard-source",candidate.toString(),source);fail("A new reference must not borrow old 404 tolerance") }
            catch(expected: com.example.api.DriveAccessFailure) { assertEquals(404,expected.httpStatus) }
        }
        assertEquals(before,remote.entries.getValue("guard-source").bytes.toList())
    }

    @Test fun retainedDeleted404DoesNotAuthorizeReactivation() = runBlocking {
        val old=JSONObject().put("entries",JSONArray().put(JSONObject().put("internalId","history-a")
            .put("metadataFileId","missing-history-meta").put("mainDriveFileId","missing-history-original").put("syncStatus","DELETED")))
        remote.put("guard-source","guard.json",config.systemFolderId,old.toString())
        val source=GoogleDriveClient.downloadSyncJsonSnapshot(token,"guard-source")
        val candidate=JSONObject(old.toString());candidate.getJSONArray("entries").getJSONObject(0).put("syncStatus","SYNCED")
        try { GoogleDriveClient.updateSyncJson(token,"guard-source",candidate.toString(),source);fail("History may not be reactivated") }
        catch(expected: com.example.api.DriveAccessFailure) { assertEquals(404,expected.httpStatus) }
        assertEquals(old.toString(),remote.entries.getValue("guard-source").bytes.toString(Charsets.UTF_8))
    }

    @Test fun sameHistoricalReferenceCanBeConservedWithoutWeakeningOrdinaryWrites() = runBlocking {
        val old=JSONObject().put("managedDocuments",JSONArray().put(JSONObject().put("documentId","history-a").put("driveFileId","missing-original")))
        remote.put("guard-source","guard.json",config.systemFolderId,old.toString())
        val changed=JSONObject(old.toString()).put("note","synthetic changed metadata")
        assertTrue(GoogleDriveClient.updateSyncJson(token,"guard-source",changed.toString()).success)
        assertEquals("missing-original",JSONObject(remote.entries.getValue("guard-source").bytes.toString(Charsets.UTF_8))
            .getJSONArray("managedDocuments").getJSONObject(0).getString("driveFileId"))
        assertFalse(GoogleDriveClient.updateJson(token,"guard-source",changed.put("note","ordinary write must remain blocked").toString()).success)
    }

    @Test fun aConcurrentJsonChangeRejectsTheOldConditionalWrite() = runBlocking {
        remote.put("guard-source","guard.json",config.systemFolderId,"{\"note\":\"old\"}")
        val source=GoogleDriveClient.downloadSyncJsonSnapshot(token,"guard-source")
        val file=remote.entries.getValue("guard-source")
        file.bytes="{\"note\":\"newer remote change\"}".toByteArray();file.revision++
        try { GoogleDriveClient.updateSyncJson(token,"guard-source","{\"note\":\"pending local change\"}",source);fail("Stale preimage must not overwrite a newer version") }
        catch(expected: com.example.api.DriveAccessFailure) { assertEquals(412,expected.httpStatus) }
        assertTrue(file.bytes.toString(Charsets.UTF_8).contains("newer remote change"))
    }

    @Test fun missingAncestorStillBlocksAConservingJsonWrite() = runBlocking {
        remote.put("guard-source","guard.json",config.systemFolderId,"{}")
        remote.put("orphan-original","orphan.png","missing-ancestor",mime="image/png")
        try { GoogleDriveClient.updateSyncJson(token,"guard-source","{\"driveFileId\":\"orphan-original\"}");fail("Missing ancestry is not an optional original") }
        catch(expected: com.example.api.DriveAccessFailure) { assertEquals(404,expected.httpStatus) }
        assertEquals("{}",remote.entries.getValue("guard-source").bytes.toString(Charsets.UTF_8))
    }

    @Test fun batchedWriteFenceRejectsAnOriginalMovedAfterPreflightBeforeAnyPatch() = runBlocking {
        seedReceipts(1)
        remote.put("foreign-phase11e-root", "Unrelated files", remote.myDrive, mime = ComparisonDriveHttpFixture.FOLDER)
        remote.put("foreign-phase11e-folder", "Foreign", "foreign-phase11e-root", mime = ComparisonDriveHttpFixture.FOLDER)
        remote.requests.clear()
        try {
            GoogleDriveClient.withSyncReadProof(token) {
                val source = GoogleDriveClient.downloadSyncJsonSnapshot(token, "phase11e-index")
                val candidate = JSONObject(source.read.json)
                candidate.getJSONArray("entries").getJSONObject(0).put("aussteller", "Local author edit")
                remote.entries.getValue("phase11e-original-1").parent = "foreign-phase11e-folder"
                GoogleDriveClient.updateSyncJson(token, source.fileId, candidate.toString(), source)
            }
            fail("A cached reference must not grant write authority after a move")
        } catch (_: com.example.api.DriveAccessFailure) { }
        assertTrue(remote.requests.all { it.method == "GET" })
        assertFalse(remote.entries.getValue("phase11e-index").bytes.toString(Charsets.UTF_8).contains("Local author edit"))
    }

    @Test fun actualSupplementalBankBackupKeepsTransactionsSharingOneAssignment() = runBlocking {
        seedReceipts(1)
        db.bankDao().upsertAccount(BankAccount("synthetic-shared-account", "Synthetic account"))
        val first = BankTransaction(transactionId = "synthetic-bank-one", accountId = "synthetic-shared-account",
            bookingDate = "2026-01-01", amount = 100.0, propertyId = "synthetic-shared-property",
            unitId = "synthetic-shared-unit", purpose = "First", updatedAt = "2026-01-01T00:00:00Z")
        val second = first.copy(transactionId = "synthetic-bank-two", amount = 200.0, purpose = "Second")
        db.bankDao().upsertTransaction(first)
        db.bankDao().upsertTransaction(second)
        val before = SupplementalDriveBackup.createPayload(app, db)
        val file = remote.put("synthetic-bank-snapshot", "supplemental-backup.json", config.systemFolderId,
            before.toString(), "supplementalBackup")
        assertTrue(GoogleDriveClient.updateSyncJson(token, file.id, before.toString(), conflictContext = app).success)
        db.bankDao().upsertTransaction(first.copy(purpose = "Confirmed local edit", updatedAt = "2026-01-02T00:00:00Z"))
        db.bankDao().upsertTransaction(first.copy(transactionId = "synthetic-bank-three", amount = 300.0))
        val after = SupplementalDriveBackup.createPayload(app, db)
        assertTrue(GoogleDriveClient.updateSyncJson(token, file.id, after.toString(), conflictContext = app).success)
        val rows = JSONObject(file.bytes.toString(Charsets.UTF_8)).getJSONArray("bankTransactions")
        val byId = (0 until rows.length()).map { rows.getJSONObject(it) }.associateBy { it.getString("transactionId") }
        assertEquals(setOf(first.transactionId, second.transactionId, "synthetic-bank-three"), byId.keys)
        assertEquals("Confirmed local edit", byId.getValue(first.transactionId).getString("purpose"))
        assertEquals(200.0, byId.getValue(second.transactionId).getDouble("amount"), 0.0)
        assertTrue(byId.values.all { it.getString("unitId") == first.unitId && it.getString("propertyId") == first.propertyId })
    }

    private fun bankIdentityCases() = linkedMapOf("bankTransactions" to "transactionId", "bankAccounts" to "accountId",
        "bankReceiptLinks" to "linkId", "bankLearningRules" to "ruleId", "bankRuleEvidence" to "evidenceId",
        "bankRentAssignments" to "assignmentId", "bankLoanAssignments" to "assignmentId", "bankRecurringPatterns" to "patternId")

    private fun bankIdentityRow(table: String, key: String, id: String): JSONObject = JSONObject().apply {
        if (table != "bankAccounts") {
            put("propertyId", "shared-property"); put("unitId", "shared-unit"); put("transactionId", "shared-transaction")
            put("accountId", "shared-account"); put("ruleId", "shared-rule")
        }
        put(key, id); put("note", "Original")
    }

    @Test fun bankBackupGuardUsesEachEntityPrimaryKeyInsteadOfSharedReferences() {
        for ((table, key) in bankIdentityCases()) {
            val first = bankIdentityRow(table, key, "first")
            val second = bankIdentityRow(table, key, "second")
            val before = JSONObject().put(table, JSONArray().put(first).put(second))
            val candidate = JSONObject().put(table, JSONArray().put(second)
                .put(JSONObject(first.toString()).put("note", "Local edit")).put(bankIdentityRow(table, key, "third")))
            DriveBackupConflictGuard.validate(before, candidate, knownBaseline = true)
            assertEquals("Original", first.getString("note"))
            assertEquals(3, candidate.getJSONArray(table).length())
        }
    }

    @Test fun bankPrimaryKeysStillRejectDroppedDuplicateOrMissingEntitiesWithKnownBaseline() {
        for ((table, key) in bankIdentityCases()) {
            val first = bankIdentityRow(table, key, "first")
            val second = bankIdentityRow(table, key, "second")
            val before = JSONObject().put(table, JSONArray().put(first).put(second))
            val unsafe = listOf(JSONArray().put(first).put(bankIdentityRow(table, key, "replacement")),
                JSONArray().put(first).put(JSONObject(first.toString())),
                JSONArray().put(first).put(JSONObject(second.toString()).apply { remove(key) }))
            for (rows in unsafe) try {
                DriveBackupConflictGuard.validate(before, JSONObject().put(table, rows), knownBaseline = true)
                fail("$table must retain unique explicit $key values")
            } catch (failure: com.example.api.DriveSyncConflict) {
                assertEquals(com.example.api.DriveSyncCheck.BACKUP_PREIMAGE_CONFLICT, failure.check)
            }
        }
    }

    @Test fun unknownNewerRemoteSnapshotCannotBeOverwrittenByOlderLocalData() = runBlocking {
        val old = JSONObject().put("bankTransactions", JSONArray().put(JSONObject().put("transactionId", "bank-synthetic-1")
            .put("amountCent", 900).put("updatedAt", "2026-10-31T12:00:00Z")))
        remote.put("conflict-snapshot", "supplementalBackup.json", config.systemFolderId, old.toString(), "supplementalBackup")
        val stale = JSONObject(old.toString())
        stale.getJSONArray("bankTransactions").getJSONObject(0).put("amountCent", 100).put("updatedAt", "2026-10-30T12:00:00Z")
        remote.requests.clear()
        try {
            GoogleDriveClient.updateSyncJson(token, "conflict-snapshot", stale.toString(), conflictContext = app)
            fail("Older local data must not overwrite newer cloud data")
        } catch (error: com.example.api.DriveAccessFailure) { assertEquals(412, error.httpStatus) }
        assertTrue(remote.requests.all { it.method == "GET" })
        assertEquals(old.toString(), remote.entries.getValue("conflict-snapshot").bytes.toString(Charsets.UTF_8))
    }

    @Test fun knownBaselineDoesNotAuthorizeDroppingAnExistingBankRecord() = runBlocking {
        val before = JSONObject().put("bankTransactions", JSONArray().put(JSONObject().put("transactionId", "synthetic-bank-keep").put("amountCent", 300)))
        remote.put("conserved-snapshot", "supplementalBackup.json", config.systemFolderId, before.toString(), "supplementalBackup")
        assertTrue(GoogleDriveClient.updateSyncJson(token, "conserved-snapshot", before.toString(), conflictContext = app).success)
        remote.requests.clear()
        try {
            GoogleDriveClient.updateSyncJson(token, "conserved-snapshot", JSONObject().put("bankTransactions", JSONArray()).toString(), conflictContext = app)
            fail("An absent local record is not proof of deletion")
        } catch (error: com.example.api.DriveAccessFailure) { assertEquals(412, error.httpStatus) }
        assertTrue(remote.requests.all { it.method == "GET" })
    }

    @Test fun knownUnversionedBaselineAllowsLocalEditButBlocksAnUnknownCloudEdit() = runBlocking {
        val original = JSONObject().put("propertyId", "synthetic-property").put("name", "Before")
        val file = remote.put("unversioned-baseline", "property-metadata.json", config.systemFolderId, original.toString(), "propertyMetadata")
        assertTrue(GoogleDriveClient.updateSyncJson(token, file.id, original.toString(), conflictContext = app).success)
        val local = JSONObject(original.toString()).put("name", "Local edit from known common state")
        assertTrue(GoogleDriveClient.updateSyncJson(token, file.id, local.toString(), conflictContext = app).success)
        file.bytes = JSONObject(local.toString()).put("name", "Independent newer cloud edit").toString().toByteArray()
        file.revision++
        remote.requests.clear()
        try {
            GoogleDriveClient.updateSyncJson(token, file.id, local.toString(), conflictContext = app)
            fail("A changed common preimage must be resolved before overwriting")
        } catch (error: com.example.api.DriveAccessFailure) { assertEquals(412, error.httpStatus) }
        assertTrue(remote.requests.all { it.method == "GET" })
    }

    @Test fun metadataSuccessAndIndexFailureResumeWithoutReuploadOrRevisionConflict() = runBlocking {
        seedReceipts(1)
        val before = repo.allReceipts.first().single()
        val changed = before.copy(aussteller = "Synthetic changed vendor")
        repo.insert(changed)
        val failedOnce = java.util.concurrent.atomic.AtomicBoolean()
        transport.set(null, OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val request = chain.request()
            if (request.method == "PATCH" && request.url.encodedPath.endsWith("/phase11e-index") && !failedOnce.getAndSet(true))
                okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(503)
                    .message("Synthetic failed index commit").body(okhttp3.ResponseBody.create(null, "{}")).build()
            else chain.proceed(request)
        }.addInterceptor(remote).build())
        remote.requests.clear()
        assertFalse(drive.syncReceiptToDrive(token, config, changed))
        val partial = repo.allReceipts.first().single()
        assertEquals(2L, partial.driveRevision)
        assertEquals(before.driveFileId, partial.driveFileId)
        assertTrue(DrivePersistenceRepository(app, repo).syncReceiptToDrive(token, config, partial))
        assertEquals(1, remote.requests.count { it.method == "PATCH" && it.url.encodedPath.endsWith("/${before.driveMetadataFileId}") })
        assertFalse(remote.requests.any { it.method == "POST" || (it.method == "PATCH" && it.url.encodedPath.endsWith("/${before.driveFileId}")) })
        val finalEntry = JSONObject(remote.entries.getValue("phase11e-index").bytes.toString(Charsets.UTF_8)).getJSONArray("entries").getJSONObject(0)
        assertEquals(changed.aussteller, finalEntry.getString("aussteller"))
        assertEquals(before.driveMetadataFileId, finalEntry.getString("metadataFileId"))
    }

    @Test fun supplementalFailureKeepsLastCompleteTimestampAndReportsSuccessfulReceiptsHonestly() = runBlocking {
        seedReceipts(22)
        val vm = connectedModel()
        measure(vm, "before-supplemental-fault")
        assertNull(vm.driveSyncError.value)
        val timestamp = prefs.getString("last_stammdaten_backup_time", null)
        app.getSharedPreferences("bank_transaction_notes", 0).edit().putString("synthetic-note", "New local note").commit()
        val supplemental = remote.entries.values.single { it.properties["entityType"] == "supplementalBackup" }.id
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        transport.set(null, OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val request = chain.request()
            if (request.method == "PATCH" && request.url.encodedPath.endsWith("/$supplemental")) {
                attempts.incrementAndGet()
                okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(503)
                    .message("Synthetic supplemental failure").body(okhttp3.ResponseBody.create(null, "{}")).build()
            } else chain.proceed(request)
        }.addInterceptor(remote).build())
        measure(vm, "supplemental-fault")
        assertNotNull(vm.driveSyncError.value)
        assertEquals(1, attempts.get())
        assertEquals(timestamp, prefs.getString("last_stammdaten_backup_time", null))
        assertEquals(22, vm.driveSyncReport.value!!.unchanged)
        assertEquals(0, vm.driveSyncReport.value!!.failed)
        assertTrue(vm.driveSyncReport.value!!.steps.any { it.phase == com.example.api.DriveSyncPhase.SUPPLEMENTAL && it.failure?.httpStatus == 503 })
        assertTrue(vm.isDriveConnected.value)
    }

    @Test fun mediaAndMetadataEtagsMayDifferWithoutBreakingNoOpOrConditionalMetadataUpdate() = runBlocking {
        seedReceipts(1)
        transport.set(null, OkHttpClient.Builder().addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (chain.request().url.queryParameter("alt") == "media") response.newBuilder().header("ETag", "\"distinct-media-etag\"").build()
            else response
        }.addInterceptor(remote).build())
        val before = repo.allReceipts.first().single()
        assertTrue(GoogleDriveClient.withSyncReadProof(token) { drive.syncReceiptToDrive(token, config, before) })
        val changed = before.copy(aussteller = "Synthetic ETag edit")
        repo.insert(changed)
        assertTrue(GoogleDriveClient.withSyncReadProof(token) { drive.syncReceiptToDrive(token, config, changed) })
        assertEquals(before.driveFileId, repo.allReceipts.first().single().driveFileId)
    }

    @Test fun largeErrorReportSurvivesPersistenceWithoutSilentlyDroppingSteps() {
        val report = com.example.api.DriveSyncReport(outcome = com.example.api.DriveSyncOutcome.FAILED,
            steps = (0 until 900).map { index -> com.example.api.DriveSyncStep(com.example.api.DriveSyncPhase.RECEIPTS,
                internalReceiptId = "synthetic-$index", startMs = index.toLong(), endMs = index + 1L,
                outcome = if (index == 899) com.example.api.DriveSyncOutcome.FAILED else com.example.api.DriveSyncOutcome.UNCHANGED,
                failure = if (index == 899) com.example.api.DriveSyncFailure("DNS") else null) })
        val restored = requireNotNull(com.example.api.DriveSyncReport.fromJson(report.toJson()))
        assertEquals(report, restored)
        assertTrue(restored.germanText().contains("DNS-Auflösung fehlgeschlagen"))
    }

    @Test fun localAuthorEditDuringNewOriginalUploadCannotBeOverwrittenBySyncAcknowledgement() = runBlocking {
        seedReceipts(1, localBytes = true)
        val initial = repo.allReceipts.first().single().copy(id = 2, internalId = "synthetic-concurrent-edit",
            displayId = "BLG-2026-000002", driveFileId = null, driveMetadataFileId = null, driveFolderId = null,
            driveRevision = null, syncStatus = "PENDING", isArchivedToDrive = false)
        repo.insert(initial)
        val edited = initial.copy(aussteller = "Newer local user edit", bruttobetrag = 37.5, kontoNr = "4801",
            beschreibung = "Authored while transfer was running")
        val injected = java.util.concurrent.atomic.AtomicBoolean()
        transport.set(null, OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (request.method == "POST" && request.url.encodedPath.startsWith("/upload/") && !injected.getAndSet(true))
                runBlocking { repo.insert(edited) }
            response
        }.addInterceptor(remote).build())
        drive.syncReceiptToDrive(token, config, initial)
        assertTrue(injected.get())
        val after = requireNotNull(repo.getReceiptById(2))
        assertEquals(edited.aussteller, after.aussteller)
        assertEquals(edited.bruttobetrag, after.bruttobetrag, 0.0)
        assertEquals(edited.kontoNr, after.kontoNr)
        assertEquals(edited.beschreibung, after.beschreibung)
        assertEquals(initial.internalId, after.internalId)
    }

    private suspend fun assertMissingPrimaryNeverUsesAnotherPage(emptyPrimary: Boolean) {
        seedReceipts(1, localBytes = true)
        val row = repo.getReceiptById(1)!!
        val primary = repo.getAllManagedDocuments().single()
        val primaryBytes = remote.entries.getValue(row.driveFileId!!).bytes.copyOf()
        val otherBytes = primaryBytes.copyOf().also { it[it.lastIndex] = 92 }
        val otherFile = File(app.filesDir, "synthetic-page-two.png").apply { writeBytes(otherBytes) }
        val other = primary.copy(documentId = primary.documentId + ":page-two", localUri = otherFile.absolutePath,
            driveFileId = "synthetic-page-two", storedFilename = otherFile.name, originalFilename = otherFile.name,
            sha256 = StableDocumentIdentity.sha256(otherBytes), extractedFieldsJson = "{\"_receiptOriginalOrder\":1}")
        remote.put(other.driveFileId!!, other.storedFilename, config.receiptsFolderId, mime = other.mimeType).bytes = otherBytes
        repo.upsertManagedDocument(other)
        repo.insertDocument(ReceiptDocumentReference(id = other.documentId, receiptInternalId = row.internalId,
            driveFileId = other.driveFileId, driveFolderId = other.driveFolderId, filename = other.storedFilename,
            mimeType = other.mimeType, sizeBytes = other.fileSizeBytes, role = "ATTACHMENT", createdAt = other.createdAt))
        val pages = row.copy(imageUrl = JSONArray().put(primary.localUri).put(other.localUri).toString())
        db.receiptDao().insertReceipt(pages)
        drive.upsertReceiptMetadata(token, config, pages)
        val ready = repo.getReceiptById(1)!!.copy(driveRevision = 2)
        db.receiptDao().insertReceipt(ready)
        DrivePendingWrites(app).completedReceiptIndex(ready.driveMetadataFileId!!)
        if (emptyPrimary) File(primary.localUri).writeBytes(byteArrayOf()) else File(primary.localUri).delete()
        remote.requests.clear()
        assertTrue(drive.syncReceiptToDrive(token, config, ready))
        assertArrayEquals(primaryBytes, remote.entries.getValue(row.driveFileId).bytes)
        assertArrayEquals(otherBytes, remote.entries.getValue(other.driveFileId).bytes)
        assertEquals(primary.sha256, repo.getManagedDocument(primary.documentId)!!.sha256)
        assertTrue(remote.requests.all { it.method == "GET" })
    }

    @Test fun emptyPrimaryCacheNeverOverwritesItsCloudIdWithTheSecondPage() = runBlocking {
        assertMissingPrimaryNeverUsesAnotherPage(emptyPrimary = true)
    }

    @Test fun absentPrimaryCacheNeverOverwritesItsCloudIdWithTheSecondPage() = runBlocking {
        assertMissingPrimaryNeverUsesAnotherPage(emptyPrimary = false)
    }

    private suspend fun interruptedFirstUpload(unexpectedCloudReceipt: Boolean, beforeMetadata: Boolean = true, localEdit: Boolean = false) {
        val bytes = byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a) + ByteArray(32) { 73 }
        val file = File(app.filesDir, "synthetic-first-upload.png").apply { writeBytes(bytes) }
        repo.insert(Receipt(id = 1, internalId = "synthetic-first-upload", displayId = "BLG-2026-000001",
            aussteller = "Synthetic first upload", datum = "2026-01-01", uhrzeit = "12:00", bruttobetrag = 119.0,
            hauptkategorie = "Instandhaltung", unterkategorie = "Material", kontoNr = "4800", beschreibung = "Synthetic first upload",
            imageUrl = file.absolutePath,
            syncStatus = "PENDING"))
        transport.set(null, OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val request = chain.request()
            val body = okio.Buffer().also { request.body?.writeTo(it) }.readUtf8()
            if (request.method == "POST" && body.contains(if (beforeMetadata) "receiptMetadata" else "receiptIndex"))
                okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(503)
                    .message("Synthetic interruption after original").body(okhttp3.ResponseBody.create(null, "{}")).build()
            else chain.proceed(request)
        }.addInterceptor(remote).build())
        val first = measure(connectedModel(), "first-upload-interrupted-before-index")
        assertTrue(first.toString(), first.getBoolean("hasSyncError"))
        val saved = repo.getReceiptById(1)!!
        val originalId = requireNotNull(saved.driveFileId)
        if (beforeMetadata) assertNull(saved.driveMetadataFileId) else assertNotNull(saved.driveMetadataFileId)
        assertTrue(remote.entries.values.none { it.properties["entityType"] == "receiptIndex" })
        assertArrayEquals(bytes, remote.entries.getValue(originalId).bytes)
        if (unexpectedCloudReceipt) remote.put("unexplained-cloud-original", "old.png", config.receiptsFolderId,
            mime = "image/png").bytes = bytes
        if (localEdit) repo.insert(saved.copy(aussteller = "Newer local user edit after interrupted first upload"))
        val before = remote.inventory(config.rootFolderId)
        transport.set(null, OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(remote).build())
        GoogleDriveClient.clearFolderCache()
        val resumed = measure(connectedModel(), "first-upload-resumed-after-restart")
        val blocked = unexpectedCloudReceipt || localEdit
        assertEquals(resumed.toString(), blocked, resumed.getBoolean("hasSyncError"))
        assertEquals(originalId, repo.getReceiptById(1)!!.driveFileId)
        saved.driveMetadataFileId?.let { assertEquals(it, repo.getReceiptById(1)!!.driveMetadataFileId) }
        assertEquals(0, resumed.getInt("originalCreates") + resumed.getInt("originalUpdates"))
        assertArrayEquals(bytes, remote.entries.getValue(originalId).bytes)
        if (blocked) {
            assertEquals(0, resumed.getInt("filesCreate") + resumed.getInt("filesUpdate"))
            assertEquals(before, remote.inventory(config.rootFolderId))
        } else {
            assertNotNull(repo.getReceiptById(1)!!.driveMetadataFileId)
            val repeat = measure(connectedModel(), "first-upload-identical-repeat")
            assertFalse(repeat.toString(), repeat.getBoolean("hasSyncError"))
            assertEquals(0, repeat.getInt("filesCreate") + repeat.getInt("filesUpdate"))
        }
    }

    @Test fun interruptedFirstOriginalUploadResumesFullSyncWithoutReplacingItsId() = runBlocking {
        interruptedFirstUpload(unexpectedCloudReceipt = false)
    }

    @Test fun interruptedFirstMetadataUploadResumesFullSyncWithoutReplacingOriginalOrMetadata() = runBlocking {
        interruptedFirstUpload(unexpectedCloudReceipt = false, beforeMetadata = false)
    }

    @Test fun pendingFirstUploadCannotHideAnUnexplainedCloudOriginalWithoutIndex() = runBlocking {
        interruptedFirstUpload(unexpectedCloudReceipt = true)
    }

    @Test fun pendingFirstUploadCannotOverwriteCsvAfterAnUnprovenLocalEdit() = runBlocking {
        interruptedFirstUpload(unexpectedCloudReceipt = false, localEdit = true)
    }

    @Test fun missingIndexWithoutCreationProofStillBlocksBeforeWrites() = runBlocking {
        seedReceipts(1)
        remote.entries.remove("phase11e-index")
        val before = remote.inventory(config.rootFolderId)
        val result = measure(connectedModel(), "missing-index-no-creation-proof")
        assertTrue(result.toString(), result.getBoolean("hasSyncError"))
        assertEquals(0, result.getInt("filesCreate") + result.getInt("filesUpdate"))
        assertEquals(before, remote.inventory(config.rootFolderId))
    }

    @Test fun newReceiptMetadataPreservesConfirmedPaymentMethodSourceAndConfidence() = runBlocking {
        seedReceipts(1, localBytes = true)
        val initial = repo.allReceipts.first().single()
        remote.put("synthetic-payment-original", "payment.png", config.receiptsFolderId, mime = "image/png").bytes = File(initial.imageUrl).readBytes()
        val payment = initial.copy(id = 2, internalId = "synthetic-payment-receipt", displayId = "BLG-2026-000002",
            driveFileId = "synthetic-payment-original", driveMetadataFileId = null, driveRevision = null,
            zahlungsart = "Überweisung", zahlungsartQuelle = "MANUELL", zahlungsartConfidence = 1.0)
        repo.insert(payment)
        val id = requireNotNull(drive.upsertReceiptMetadata(token, config, payment))
        val metadata = JSONObject(remote.entries.getValue(id).bytes.toString(Charsets.UTF_8))
        assertEquals(payment.zahlungsart, metadata.getString("zahlungsart"))
        assertEquals(payment.zahlungsartQuelle, metadata.getString("zahlungsartQuelle"))
        assertEquals(payment.zahlungsartConfidence, metadata.getDouble("zahlungsartConfidence"), 0.0)
        assertEquals(payment.zahlungsart, repo.getReceiptById(2)!!.zahlungsart)
    }

    @Test fun acceptedMetadataCreationWithLostResponseResumesByStableIdentityWithoutDuplicate() = runBlocking {
        seedReceipts(1, localBytes = true)
        val template = repo.allReceipts.first().single()
        val originalId = "synthetic-metadata-restart-original"
        val original = remote.put(originalId, "metadata-restart.png", config.receiptsFolderId, mime = "image/png")
        original.bytes = File(template.imageUrl).readBytes()
        val row = template.copy(id = 2, internalId = "synthetic-metadata-restart", displayId = "BLG-2026-000002",
            driveFileId = originalId, storedFilename = original.name, driveMetadataFileId = null,
            driveRevision = null, syncStatus = "PENDING", isArchivedToDrive = false)
        repo.insert(row)
        prefs.edit().putString("connected_email", "synthetic-metadata@example.invalid").commit()
        val lost = java.util.concurrent.atomic.AtomicBoolean()
        val creations = java.util.concurrent.atomic.AtomicInteger()
        transport.set(null, OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (request.method == "POST" && request.url.encodedPath.startsWith("/upload/") && response.isSuccessful) {
                val created = JSONObject(response.peekBody(4096).string()).optString("id")
                val entry = remote.entries[created]
                if (entry?.properties?.get("entityType") == "receiptMetadata" && entry.properties["receiptInternalId"] == row.internalId) {
                    creations.incrementAndGet()
                    if (!lost.getAndSet(true)) { response.close(); throw java.net.SocketTimeoutException("synthetic lost metadata acknowledgement") }
                }
            }
            response
        }.addInterceptor(remote).build())
        assertFalse(GoogleDriveClient.withCreateRecovery(app) { drive.syncReceiptToDrive(token, config, row) })
        GoogleDriveClient.clearFolderCache()
        val restarted = DrivePersistenceRepository(app, repo)
        assertTrue(GoogleDriveClient.withCreateRecovery(app) { restarted.syncReceiptToDrive(token, config, repo.getReceiptById(2)!!) })
        assertEquals(1, creations.get())
        assertEquals(1, remote.entries.values.count { it.properties["entityType"] == "receiptMetadata" && it.properties["receiptInternalId"] == row.internalId })
        assertEquals(originalId, repo.getReceiptById(2)!!.driveFileId)
        assertNotNull(repo.getReceiptById(2)!!.driveMetadataFileId)
    }

    @Test fun partiallyCommittedSyncSurvivesClosingAndReopeningAnIsolatedPersistentRoomDatabase() = runBlocking {
        seedReceipts(1, localBytes = true)
        val filename = "phase11e-synthetic-restart.db"
        app.deleteDatabase(filename)
        fun open() = androidx.room.Room.databaseBuilder(app, AppDatabase::class.java, filename).allowMainThreadQueries().build()
        fun repository(database: AppDatabase) = ReceiptRepository(database.receiptDao(), database.propertyDao(), database.receiptEntityDao(),
            database.belegDao(), database.exportAuditDao(), database.receiptDocumentDao(), database.managedDocumentDao())
        var persistent = open()
        try {
            val first = repository(persistent)
            first.restorePropertyMetadata(requireNotNull(repo.getPropertyMetadata()))
            val original = repo.allReceipts.first().single()
            first.insert(original)
            repo.getAllManagedDocuments().forEach { first.upsertManagedDocument(it) }
            repo.getDocumentsForReceipt(original.internalId).forEach { first.insertDocument(it) }
            val edited = original.copy(aussteller = "Synthetic persisted local edit")
            first.insert(edited)
            val failed = java.util.concurrent.atomic.AtomicBoolean()
            transport.set(null, OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
                val request = chain.request()
                if (request.method == "PATCH" && request.url.encodedPath.endsWith("/phase11e-index") && !failed.getAndSet(true))
                    okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(503)
                        .message("Synthetic index interruption").body(okhttp3.ResponseBody.create(null, "{}")).build()
                else chain.proceed(request)
            }.addInterceptor(remote).build())
            assertFalse(DrivePersistenceRepository(app, first).syncReceiptToDrive(token, config, edited))
            assertEquals(2L, first.getReceiptById(original.id)!!.driveRevision)
            persistent.close()
            GoogleDriveClient.clearFolderCache()
            persistent = open()
            val restarted = repository(persistent)
            val saved = requireNotNull(restarted.getReceiptById(original.id))
            assertEquals(edited.aussteller, saved.aussteller)
            assertEquals(original.driveFileId, saved.driveFileId)
            assertEquals(original.driveMetadataFileId, saved.driveMetadataFileId)
            remote.requests.clear()
            assertTrue(DrivePersistenceRepository(app, restarted).syncReceiptToDrive(token, config, saved))
            assertEquals(1, restarted.allReceipts.first().size)
            assertEquals("SYNCED", restarted.getReceiptById(original.id)!!.syncStatus)
            assertFalse(remote.requests.any { it.method == "POST" || (it.method == "PATCH" && it.url.encodedPath.endsWith("/${original.driveMetadataFileId}")) })
        } finally { persistent.close(); app.deleteDatabase(filename) }
    }

    private suspend fun verifyFullSyncDocumentBindingChange(move: Boolean, changeUnrelated: Boolean) {
        seedReceipts(1)
        val property = requireNotNull(repo.getPropertyMetadata())
        val id = "synthetic-document-binding"
        val type = ManagedDocumentType.RECHNUNG
        val file = File(app.filesDir, "synthetic-binding.png").apply {
            writeBytes(byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a) + ByteArray(64) { 23 })
        }
        repo.upsertManagedDocument(ManagedDocument(documentId = id, propertyId = property.propertyId, documentType = type.name,
            documentDate = "2026-10-31", title = "Before local edit",
            storedFilename = DocumentFilenameGenerator.document(type, "2026-10-31", "Before local edit", "png", id),
            originalFilename = file.name, mimeType = "image/png", localUri = file.absolutePath,
            sha256 = StableDocumentIdentity.sha256(file.readBytes()), fileSizeBytes = file.length(),
            createdAt = "2026-10-30T00:00:00Z", updatedAt = "2026-10-31T00:00:00Z"))
        val vm = connectedModel()
        val baseline = measure(vm, "document-binding-before-$move-$changeUnrelated")
        assertFalse(baseline.toString(), baseline.getBoolean("hasSyncError"))
        val before = repo.getManagedDocument(id)!!
        val originalId = requireNotNull(before.driveFileId)
        val bytes = remote.entries.getValue(originalId).bytes.copyOf()
        val oldBackup = vm.lastStammdatenBackupTime.value
        repo.upsertManagedDocument(before.copy(title = "After local edit", documentDate = if (move) "2025-10-31" else before.documentDate,
            updatedAt = "2026-11-01T00:00:00Z"))
        val unrelatedId = repo.getReceiptById(1)!!.driveFileId!!
        val unrelatedBytes = remote.entries.getValue(unrelatedId).bytes.copyOf()
        val injected = java.util.concurrent.atomic.AtomicBoolean()
        if (changeUnrelated) transport.set(null, OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (request.method == "PATCH" && request.url.encodedPath == "/drive/v3/files/$originalId" && !injected.getAndSet(true))
                remote.entries.getValue(unrelatedId).name = "externally-renamed-unrelated.png"
            response
        }.addInterceptor(remote).build())
        val result = measure(vm, "document-binding-after-$move-$changeUnrelated")
        assertEquals(result.toString(), changeUnrelated, result.getBoolean("hasSyncError"))
        assertArrayEquals(bytes, remote.entries.getValue(originalId).bytes)
        assertArrayEquals(unrelatedBytes, remote.entries.getValue(unrelatedId).bytes)
        assertEquals(originalId, repo.getManagedDocument(id)!!.driveFileId)
        assertFalse(remote.requests.any { it.method == "PATCH" && it.url.queryParameter("uploadType") == "media" &&
            it.url.encodedPath.endsWith("/$originalId") })
        if (changeUnrelated) {
            assertTrue(injected.get())
            assertEquals("externally-renamed-unrelated.png", remote.entries.getValue(unrelatedId).name)
            assertEquals(oldBackup, vm.lastStammdatenBackupTime.value)
        } else {
            val after = repo.getManagedDocument(id)!!
            assertNotEquals(before.storedFilename, after.storedFilename)
            assertEquals(after.storedFilename, remote.entries.getValue(originalId).name)
            assertEquals(after.driveFolderId, remote.entries.getValue(originalId).parent)
            assertEquals(move, before.driveFolderId != after.driveFolderId)
            val repeated = measure(vm, "document-binding-repeat-$move")
            assertFalse(repeated.toString(), repeated.getBoolean("hasSyncError"))
            assertEquals(0, repeated.getInt("filesCreate") + repeated.getInt("filesUpdate"))
        }
    }

    @Test fun verifiedStandaloneRenameCompletesFullBackupAndRemainsNoOp() = runBlocking {
        verifyFullSyncDocumentBindingChange(move = false, changeUnrelated = false)
    }

    @Test fun verifiedStandaloneMoveCompletesFullBackupAndRemainsNoOp() = runBlocking {
        verifyFullSyncDocumentBindingChange(move = true, changeUnrelated = false)
    }

    @Test fun verifiedOwnMoveCannotHideAnUnrelatedExternalRename() = runBlocking {
        verifyFullSyncDocumentBindingChange(move = true, changeUnrelated = true)
    }

    @Test fun standaloneDocumentNoOpRetainsAuthorVersionAndAvoidsOriginalDownloadsAndWrites() = runBlocking {
        val property = requireNotNull(repo.getPropertyMetadata())
        val id = "synthetic-standalone-noop"
        val type = ManagedDocumentType.SONSTIGES
        val filename = DocumentFilenameGenerator.document(type, "2026-10-31", "Synthetic document", "png", id)
        val file = File(app.filesDir, "synthetic-standalone.png").apply {
            writeBytes(byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a) + ByteArray(64) { 7 })
        }
        val document = ManagedDocument(documentId = id, propertyId = property.propertyId, documentType = type.name,
            documentDate = "2026-10-31", title = "Synthetic document", storedFilename = filename,
            originalFilename = file.name, mimeType = "image/png", localUri = file.absolutePath,
            sha256 = StableDocumentIdentity.sha256(file.readBytes()), fileSizeBytes = file.length(),
            createdAt = "2026-10-30T00:00:00Z", updatedAt = "2026-10-31T00:00:00Z", ocrText = "Synthetic reviewed content")
        repo.upsertManagedDocument(document)
        prefs.edit().putString("connected_email", "synthetic-document@example.invalid").commit()
        assertTrue(GoogleDriveClient.withCreateRecovery(app) { drive.syncManagedDocumentToDrive(token, config, id) })
        val before = requireNotNull(repo.getManagedDocument(id))
        assertEquals(document.updatedAt, before.updatedAt)
        remote.requests.clear()
        assertTrue(GoogleDriveClient.withSyncReadProof(token) { drive.syncManagedDocumentToDrive(token, config, id) })
        assertEquals(before, repo.getManagedDocument(id))
        assertTrue(remote.requests.all { it.method == "GET" })
        assertFalse(remote.requests.any { it.url.queryParameter("alt") == "media" && it.url.encodedPath.endsWith("/${before.driveFileId}") })
    }

    @Test fun absentServerChecksumsUseVerifiedBytesAndStableVersionsWithoutLosingIds() = runBlocking {
        seedReceipts(1, localBytes = true)
        transport.set(null, OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (request.method == "GET" && request.url.queryParameter("alt") != "media") {
                val json = JSONObject(response.body!!.string())
                json.remove("sha256Checksum")
                json.optJSONArray("files")?.let { array -> for (i in 0 until array.length()) array.getJSONObject(i).remove("sha256Checksum") }
                response.newBuilder().body(okhttp3.ResponseBody.create(null, json.toString())).build()
            } else response
        }.addInterceptor(remote).build())
        val before = repo.allReceipts.first().single()
        assertTrue(GoogleDriveClient.withSyncReadProof(token) { drive.syncReceiptToDrive(token, config, before) })
        val bytes = File(before.imageUrl).readBytes() + byteArrayOf(31, 32)
        File(before.imageUrl).writeBytes(bytes)
        assertTrue(GoogleDriveClient.withSyncReadProof(token) { drive.syncReceiptToDrive(token, config, repo.allReceipts.first().single()) })
        val after = repo.allReceipts.first().single()
        assertEquals(before.driveFileId, after.driveFileId)
        assertEquals(before.driveMetadataFileId, after.driveMetadataFileId)
        assertEquals(StableDocumentIdentity.sha256(bytes), repo.getAllManagedDocuments().single { it.receiptInternalId == after.internalId }.sha256)
        remote.requests.clear()
        assertTrue(GoogleDriveClient.withSyncReadProof(token) { drive.syncReceiptToDrive(token, config, after) })
        assertTrue(remote.requests.all { it.method == "GET" })
    }

    @Test fun absentHttpEtagAllowsVerifiedNoOpButBlocksAChangedMetadataWrite() = runBlocking {
        seedReceipts(1)
        remote.omitV2Etags = true // Neither API supplies a usable conditional-write token.
        transport.set(null, OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request()).newBuilder().removeHeader("ETag").build()
        }.addInterceptor(remote).build())
        val before = repo.allReceipts.first().single()
        assertTrue(GoogleDriveClient.withSyncReadProof(token) { drive.syncReceiptToDrive(token, config, before) })
        repo.insert(before.copy(aussteller = "Synthetic changed without precondition"))
        remote.requests.clear()
        assertFalse(GoogleDriveClient.withSyncReadProof(token) { drive.syncReceiptToDrive(token, config, repo.allReceipts.first().single()) })
        assertTrue(remote.requests.all { it.method == "GET" })
        assertEquals(before.driveFileId, repo.allReceipts.first().single().driveFileId)
        assertEquals(before.driveMetadataFileId, repo.allReceipts.first().single().driveMetadataFileId)
    }

    @Test fun verifiedExistingDocumentReferenceIsLinkedWithoutCreatingAnotherOriginalAndRemainsNoOp() = runBlocking {
        val property = requireNotNull(repo.getPropertyMetadata())
        val bytes = byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a) + ByteArray(64) { 12 }
        val hash = StableDocumentIdentity.sha256(bytes)
        val original = remote.put("synthetic-shared-original", "shared-old.png", config.receiptsFolderId, mime = "image/png").apply { this.bytes = bytes }
        val old = ManagedDocument(documentId = "synthetic-shared-old", propertyId = property.propertyId,
            receiptInternalId = "synthetic-historical-receipt", driveFileId = original.id, driveFolderId = original.parent,
            storedFilename = original.name, mimeType = original.mime, sha256 = hash, fileSizeBytes = bytes.size.toLong())
        repo.upsertManagedDocument(old)
        val local = File(app.filesDir, "synthetic-shared-local.png").apply { writeBytes(bytes) }
        val incoming = ManagedDocument(documentId = "synthetic-shared-new", propertyId = property.propertyId,
            storedFilename = "new.png", mimeType = "image/png", sha256 = hash, fileSizeBytes = bytes.size.toLong(), localUri = local.absolutePath)
        repo.upsertManagedDocument(incoming)
        assertTrue(GoogleDriveClient.withSyncReadProof(token) { drive.syncManagedDocumentToDrive(token, config, incoming.documentId) })
        assertEquals(original.id, repo.getManagedDocument(incoming.documentId)!!.driveFileId)
        assertEquals(original.name, repo.getManagedDocument(incoming.documentId)!!.storedFilename)
        assertEquals(old, repo.getManagedDocument(old.documentId))
        assertEquals(1, remote.entries.values.count { it.mime == "image/png" })
        remote.requests.clear()
        assertTrue(GoogleDriveClient.withSyncReadProof(token) { drive.syncManagedDocumentToDrive(token, config, incoming.documentId) })
        assertTrue(remote.requests.all { it.method == "GET" })
    }

    @Test fun unavailableHistoricalHashReferenceIsNeverBoundToANewDocumentOrAutomaticallyReplaced() = runBlocking {
        val property = requireNotNull(repo.getPropertyMetadata())
        val bytes = byteArrayOf(0x89.toByte(),0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a) + ByteArray(64) { 13 }
        val hash = StableDocumentIdentity.sha256(bytes)
        val old = ManagedDocument(documentId = "synthetic-missing-history", propertyId = property.propertyId,
            receiptInternalId = "synthetic-deleted-receipt", driveFileId = "synthetic-missing-history-original",
            driveFolderId = config.receiptsFolderId, sha256 = hash, fileSizeBytes = bytes.size.toLong())
        repo.upsertManagedDocument(old)
        val local = File(app.filesDir, "synthetic-new-copy.png").apply { writeBytes(bytes) }
        val incoming = ManagedDocument(documentId = "synthetic-new-copy", propertyId = property.propertyId,
            storedFilename = "new-copy.png", mimeType = "image/png", sha256 = hash, fileSizeBytes = bytes.size.toLong(), localUri = local.absolutePath)
        repo.upsertManagedDocument(incoming)
        try {
            GoogleDriveClient.withSyncReadProof(token) { drive.syncManagedDocumentToDrive(token, config, incoming.documentId) }
            fail("An unreachable reference must not be rebound or guessed as a safe duplicate")
        } catch (failure: com.example.api.DriveAccessFailure) { assertEquals(404, failure.httpStatus) }
        assertNull(repo.getManagedDocument(incoming.documentId)!!.driveFileId)
        assertEquals(old, repo.getManagedDocument(old.documentId))
        assertFalse(remote.entries.values.any { it.mime == "image/png" })
        assertArrayEquals(bytes, local.readBytes())
    }

    @Test fun fullUnchangedSyncWithoutChecksumsOrEtagsDoesNotRewriteCsvOrOriginals() = runBlocking {
        seedReceipts(1)
        val vm = connectedModel()
        measure(vm, "before-optional-headers-unavailable")
        assertNull(vm.driveSyncError.value)
        transport.set(null, OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            val builder = response.newBuilder().removeHeader("ETag")
            if (request.method == "GET" && request.url.queryParameter("alt") != "media") {
                val json = JSONObject(response.body!!.string())
                json.remove("sha256Checksum")
                json.optJSONArray("files")?.let { a -> for (i in 0 until a.length()) a.getJSONObject(i).remove("sha256Checksum") }
                builder.body(okhttp3.ResponseBody.create(null, json.toString()))
            }
            builder.build()
        }.addInterceptor(remote).build())
        val result = measure(vm, "no-optional-checksums-or-etags")
        assertFalse(result.toString(), result.getBoolean("hasSyncError"))
        assertEquals(0, result.getInt("filesCreate") + result.getInt("filesUpdate"))
        assertTrue(vm.isDriveConnected.value)
        assertFalse(prefs.getBoolean("auto_backup", true))
    }

    @Test fun oneChangedReceiptUpdatesMetadataAndPreservesUnknownCloudFields() = runBlocking {
        seedReceipts(1,localBytes=true)
        val current=repo.allReceipts.first().single()
        val metadata=remote.entries.getValue(requireNotNull(current.driveMetadataFileId))
        val json=JSONObject(metadata.bytes.toString(Charsets.UTF_8)).put("futureSyntheticField","preserve")
        metadata.bytes=json.toString().toByteArray();metadata.revision++
        repo.insert(current.copy(aussteller="Synthetic changed vendor"))
        val vm=connectedModel()
        val result=measure(vm,"one-metadata-change")
        assertTrue(result.toString(),vm.driveSyncError.value==null)
        val after=JSONObject(metadata.bytes.toString(Charsets.UTF_8))
        assertEquals("Synthetic changed vendor",after.getString("aussteller"))
        assertEquals("preserve",after.getString("futureSyntheticField"))
        assertEquals(current.driveFileId,repo.allReceipts.first().single().driveFileId)
        assertEquals(0,remote.requests.count { it.url.encodedPath.contains("/upload/") && it.url.encodedPath.endsWith("/"+current.driveFileId) })
    }

}
