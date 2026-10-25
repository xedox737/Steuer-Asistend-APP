package com.example.comparison

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.BuildConfig
import com.example.api.GoogleDriveClient
import com.example.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipFile

/** Characterization only: expected defects stay explicit in JSON; a green method is not a bug fix. */
@RunWith(RobolectricTestRunner::class)
class BranchComparisonTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var remote: ComparisonDriveHttpFixture
    private lateinit var client: OkHttpClient
    private lateinit var oldInterceptors: Any
    private lateinit var databaseName: String
    private var oldDatabase: Any? = null
    private val singleton = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    private val interceptors = OkHttpClient::class.java.getDeclaredField("interceptors").apply { isAccessible = true }
    private val repo get() = ReceiptRepository(db.receiptDao(), db.propertyDao(), db.receiptEntityDao(), db.belegDao(),
        db.exportAuditDao(), db.receiptDocumentDao(), db.managedDocumentDao())
    private val drive get() = DrivePersistenceRepository(context, repo)
    private val isTest get() = BuildConfig.APPLICATION_ID.endsWith(".phase11a")
    private val ownRoot get() = if (isTest) TEST_ROOT else PRODUCT_ROOT
    private val foreignRoot get() = if (isTest) PRODUCT_ROOT else TEST_ROOT
    private val token = "synthetic-comparison-account"
    private val runId = "P11A-2026-10-25-COMPARE-${java.util.UUID.randomUUID()}"
    private val output get() = File(System.getProperty("comparison.output"))
    private val originalPaths = linkedMapOf<String, File>()

    @Before fun setup() {
        // This class cannot be invoked as an installed-app seed. It runs solely in Robolectric.
        check(android.os.Build.FINGERPRINT == "robolectric")
        check(context.filesDir.canonicalPath.startsWith(File(System.getProperty("java.io.tmpdir")).canonicalPath))
        check(context.packageName == BuildConfig.APPLICATION_ID)
        assertEquals(if (isTest) "test" else "main", System.getProperty("comparison.variant"))
        databaseName = "comparison-${java.util.UUID.randomUUID()}.db"
        oldDatabase = singleton.get(null)
        db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        singleton.set(null, db)
        remote = ComparisonDriveHttpFixture()
        // The product's private static final client is NOT changed. Intercept only this JVM's HTTP.
        client = GoogleDriveClient::class.java.getDeclaredField("client").apply { isAccessible = true }.get(null) as OkHttpClient
        oldInterceptors = interceptors.get(client)
        interceptors.set(client, listOf(remote))
        GoogleDriveClient.clearFolderCache()
        output.mkdirs()
    }

    @After fun cleanup() {
        if (::client.isInitialized) interceptors.set(client, oldInterceptors)
        GoogleDriveClient.clearFolderCache()
        singleton.set(null, oldDatabase)
        if (::db.isInitialized) db.close()
        // No installed package, production path, preferences or existing files are deleted.
    }

    private fun meta(label: String) = JSONObject().put("runId", runId).put("label", label)
        .put("source", "UNIT_TEST").put("commit", System.getProperty("comparison.commit"))
        .put("capturedAt", java.time.Instant.now().toString()).put("device", "Robolectric JVM, isolated synthetic Room")
        .put("android", android.os.Build.VERSION.RELEASE).put("appVersion", BuildConfig.VERSION_NAME).put("buildType", BuildConfig.BUILD_TYPE)

    private fun write(name: String, json: JSONObject) = File(output, "$name.json").writeText(json.toString(2))
    private suspend fun snapshot(label: String): JSONObject = ComparisonSnapshot.capture(context, db, meta(label)).also { write(label, it) }
    private fun report(label: String) = meta(label).put("applicationId", BuildConfig.APPLICATION_ID)
        .put("transport", "IN_MEMORY_HTTP_ONLY").put("practicalStatus", "NOCH OFFEN")

    private suspend fun initialize(): DriveAppConfig = when (val r = drive.initializeDriveStorage(token)) {
        is DriveInitializationResult.SuccessCreatedNew -> r.config
        is DriveInitializationResult.SuccessLoadedExisting -> r.config
        is DriveInitializationResult.Failure -> error(r.error)
    }

    /** Replay the pinned, complete original Phase10B ZIP into a fresh JVM-owned Room database.
     * Uses existing receipt parser and SupplementalDriveBackup.restorePayload, never the guarded UI seeder.
     */
    private suspend fun loadOriginalPortfolio() {
        val fixture = File(System.getProperty("comparison.fixture"))
        assertEquals("b052a1ae4569196abb791375aa1fa01fe6a636a1d0082820acb576fa497ea638", StableDocumentIdentity.sha256(fixture.readBytes()))
        assertTrue(requireNotNull(db.openHelper.writableDatabase.path).endsWith(databaseName))
        assertTrue(db.propertyDao().getAllProperties().isEmpty())
        ZipFile(fixture).use { zip ->
            fun text(name: String) = zip.getInputStream(requireNotNull(zip.getEntry(name))).bufferedReader().use { it.readText() }
            val payload = JSONObject(text("supplemental.json"))
            SupplementalDriveBackup.validatePayload(payload)
            val docs = payload.getJSONArray("managedDocuments")
            for (i in 0 until docs.length()) {
                val doc = docs.getJSONObject(i)
                if (doc.getLong("fileSizeBytes") > 0) {
                    val oldId = doc.getString("driveFileId")
                    val entry = zip.entries().asSequence().single { it.name.startsWith("originals/$oldId.") }
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    assertEquals(doc.getString("sha256"), StableDocumentIdentity.sha256(bytes))
                    assertEquals(doc.getLong("fileSizeBytes"), bytes.size.toLong())
                    val f = File(context.filesDir, "managed_documents/comparison-${doc.getString("sha256")}.${entry.name.substringAfterLast('.')}")
                    f.parentFile!!.mkdirs(); f.writeBytes(bytes)
                    originalPaths[doc.getString("documentId")] = f
                }
                doc.put("driveFileId", JSONObject.NULL).put("driveFolderId", JSONObject.NULL).put("legacyDriveFolderId", JSONObject.NULL)
            }
            zip.entries().asSequence().filter { it.name.startsWith("metadata/") && it.name.endsWith(".json") }.forEach { e ->
                val row = with(drive) { parsePersistedReceipt(text(e.name)).toLocalReceipt(null) }
                repo.insert(row.copy(imageUrl = originalPaths["receipt:${row.internalId}"]?.absolutePath.orEmpty(),
                    driveFileId = null, driveFolderId = null, driveMetadataFileId = null, driveRevision = null,
                    syncStatus = "PENDING", lastSyncedAt = null, isArchivedToDrive = false))
            }
            SupplementalDriveBackup.restorePayload(context, db, payload, replaceManagedDocuments = true,
                restoreMode = RestoreMode.REPLACE_FULL, localDocumentsBeforeCore = emptyMap())
            repo.getAllManagedDocuments().forEach { d -> originalPaths[d.documentId]?.let { repo.upsertManagedDocument(d.copy(localUri = it.absolutePath)) } }
        }
        assertEquals(60, originalPaths.size)
        assertEquals(70, repo.getAllReceiptsList().size)
        assertEquals(70, repo.getAllManagedDocuments().size)
        assertEquals(5, db.propertyDao().getAllProperties().size)
        assertEquals(120, db.bankDao().getAllTransactions().size)
        assertEquals(10, db.logbookDao().getAllTrips().size)
    }

    @Test fun completePortfolioPinpointsUploadLossBeforeDownloadMergeAndPersistsAfterDatabaseReopen() = runBlocking {
        loadOriginalPortfolio()
        val before = snapshot("portfolio-before")
        assertEquals(17, before.getJSONObject("counts").getInt("units"))
        assertEquals(17, before.getJSONObject("counts").getInt("tenancies"))
        val expectedUnits = repo.getAllManagedDocuments().associate { it.documentId to it.unitId }
        val expectedReceipts = repo.getAllReceiptsList().associate { it.internalId to it.unitId }
        val config = initialize()
        val first = repo.getReceiptByInternalId("e10b-receipt-1")!!
        assertNotNull(expectedUnits["receipt:${first.internalId}"])
        assertTrue(drive.syncReceiptToDrive(token, config, first))
        val immediately = repo.getManagedDocument("receipt:${first.internalId}")!!
        assertNull(immediately.unitId) // Actual defect reproduction, before any restore.
        assertEquals(first.unitId, repo.getReceiptByInternalId(first.internalId)!!.unitId)
        snapshot("portfolio-after-first-upload")
        val failures = linkedSetOf<String>()
        suspend fun syncAll() {
            for (r in repo.getAllReceiptsList()) {
                if (!drive.syncReceiptToDrive(token, config, r)) {
                    failures += r.internalId
                    assertTrue(r.imageUrl.isBlank())
                    assertEquals("Keine lokale Quelldatei gefunden oder Datei ist leer.", repo.getReceiptByInternalId(r.internalId)!!.syncError)
                }
            }
        }
        syncAll(); val afterUpload = snapshot("portfolio-after-upload-1")
        assertEquals(10, failures.size)
        val lost = repo.getAllManagedDocuments().filter { expectedUnits[it.documentId] != null && it.unitId == null }.map { it.documentId }
        assertEquals(60, lost.size)
        assertEquals(expectedReceipts, repo.getAllReceiptsList().associate { it.internalId to it.unitId })
        val fileIds = remote.entries.keys.toSet()
        syncAll(); assertEquals(fileIds, remote.entries.keys.toSet()); snapshot("portfolio-after-upload-2")
        repeat(2) {
            assertTrue(drive.saveStammdatenToDrive(token, config))
            assertTrue(SupplementalDriveBackup.backup(context, db, token, config.systemFolderId).success)
        }
        val beforeRead = SupplementalDriveBackup.createPayload(context, db)
        drive.buildRestoreSnapshot(token, config)
        assertEquals(canonicalJson(beforeRead), canonicalJson(SupplementalDriveBackup.createPayload(context, db)))
        snapshot("portfolio-after-read-only-download")
        repeat(2) { n ->
            val r = drive.executeFullDriveRestore(token, config, RestoreMode.MERGE)
            assertTrue(r.errors.toString(), r.isSuccess)
            assertTrue(SupplementalDriveBackup.restore(context, db, token, config.systemFolderId, restoreMode = RestoreMode.MERGE).success)
            snapshot("portfolio-after-merge-${n+1}")
            assertEquals(60, repo.getAllManagedDocuments().count { expectedUnits[it.documentId] != null && it.unitId == null })
        }
        val stableDomains = listOf("properties", "loans", "bankTransactions", "bankAccounts", "bankRentAssignments", "logbookTrips",
            "tenantHistoryPrefs", "propertyUnitPrefs", "confirmedAfaValuesPrefs", "renovationReviewPrefs")
        val current = SupplementalDriveBackup.createPayload(context, db)
        val domainChecks = stableDomains.associateWith { key -> canonicalJson(before.getJSONObject("raw").getJSONObject("supplemental").get(key)) == canonicalJson(current.get(key)) }
        db.close(); db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build(); singleton.set(null, db)
        snapshot("portfolio-after-database-reopen")
        assertEquals(60, repo.getAllManagedDocuments().count { expectedUnits[it.documentId] != null && it.unitId == null })
        originalPaths.forEach { (id, f) -> assertEquals(repo.getManagedDocument(id)!!.sha256, StableDocumentIdentity.sha256(f.readBytes())) }
        assertTrue(remote.orphanIds().isEmpty())
        write("portfolio-result", report("portfolio").put("firstLossStage", "FIRST_RECEIPT_UPLOAD_BEFORE_ANY_RESTORE")
            .put("affectedDocumentIds", JSONArray(lost.sorted())).put("missingSourceReceiptIds", JSONArray(failures.sorted()))
            .put("originalHashesMatched", 60).put("unchangedDomains", JSONArray(domainChecks.filterValues { it }.keys))
            .put("rawDomainChecks", JSONObject(domainChecks)).put("receiptUnitsUnchanged", true)
            .put("backupPasses", 2).put("mergePasses", 2).put("cloudOrphans", 0).put("databaseReopenPreservesDefect", true)
            .put("beforeCounts", before.getJSONObject("counts")).put("afterUploadCounts", afterUpload.getJSONObject("counts")))
        // Preserve the original strict raw assertions, including a red result for added aliases.
        // Evidence and database-reopen checks run first so this diagnostic cannot hide their results.
        for (key in stableDomains) assertEquals(key, canonicalJson(before.getJSONObject("raw").getJSONObject("supplemental").get(key)), canonicalJson(current.get(key)))
    }

    @Test fun normalDiscoveryUsesOwnRootAndTagsWhileLegacyMixedProductFilesRemainProductData() = runBlocking {
        remote.put("foreign-root", foreignRoot, remote.myDrive, mime = ComparisonDriveHttpFixture.FOLDER)
        remote.put("foreign-system", "_BelegApp-Daten", "foreign-root", mime = ComparisonDriveHttpFixture.FOLDER)
        remote.put("foreign-config", "app-config.json", "foreign-system", "{\"sentinel\":\"foreign\"}", "appConfig")
            .properties = mapOf("appName" to if (isTest) PRODUCT_TAG else TEST_TAG, "entityType" to "appConfig")
        remote.put("foreign-original", "Original.pdf", "foreign-root", "%PDF-synthetic", mime = "application/pdf")
            .properties = mapOf("appName" to if (isTest) PRODUCT_TAG else TEST_TAG, "receiptInternalId" to "same-id", "documentRole" to "ORIGINAL")
        val before = remote.inventory("foreign-root")
        val config = initialize()
        assertEquals(ownRoot, remote.entries.getValue(config.rootFolderId).name)
        assertNull(GoogleDriveClient.findFileByReceiptPropertiesGlobally(token, "same-id", "ORIGINAL"))
        assertEquals(before, remote.inventory("foreign-root"))
        assertFalse(remote.requests.any { it.url.encodedPath.endsWith("/foreign-config") && it.url.queryParameter("alt") == "media" })
        val prodRoot = if (isTest) "foreign-root" else config.rootFolderId
        remote.put("legacy-mixed", "Old-test-file.pdf", prodRoot, "%PDF-old-test", mime = "application/pdf")
            .properties = mapOf("appName" to PRODUCT_TAG, "receiptInternalId" to "legacy-same-id", "documentRole" to "ORIGINAL")
        val legacy = GoogleDriveClient.findFileByReceiptPropertiesGlobally(token, "legacy-same-id", "ORIGINAL")
        assertEquals(!isTest, legacy != null)
        write("discovery-result", report("discovery").put("ownRoot", ownRoot).put("foreignInventoryUnchanged", true)
            .put("foreignConfigContentRead", false).put("newForeignTagFound", false).put("legacyMixedProductFileFound", legacy != null))
    }

    @Test fun explicitForeignIdsAndPoisonedMappingsAreBlockedOnlyByTestFence() = runBlocking {
        val config = initialize()
        remote.put("foreign-root", foreignRoot, remote.myDrive, mime = ComparisonDriveHttpFixture.FOLDER)
        remote.put("foreign-category", "Foreign category", "foreign-root", mime = ComparisonDriveHttpFixture.FOLDER)
        for (id in listOf("read", "write", "delete", "move")) remote.put("foreign-$id", "$id.json", "foreign-root", "{\"sentinel\":true}")
        val before = remote.inventory("foreign-root")
        val read = GoogleDriveClient.downloadFileBytes(token, "foreign-read") != null
        val write = GoogleDriveClient.updateJson(token, "foreign-write", "{\"syntheticChanged\":true}").success
        val delete = GoogleDriveClient.deleteFile(token, "foreign-delete")
        val move = GoogleDriveClient.moveAndRenameFile(token, "foreign-move", config.rootFolderId, listOf("foreign-root"), "Moved.json")
        context.getSharedPreferences("category_folder_mappings_prefs", 0).edit().putString("2026/Poisoned", "foreign-category").commit()
        val mapping = runCatching { GoogleDriveClient.getOrCreateFolder(token, "Mapped", config.rootFolderId, "2026/Poisoned", config.systemFolderId, context) }.getOrNull()
        assertEquals(!isTest, read); assertEquals(!isTest, write); assertEquals(!isTest, delete); assertEquals(!isTest, move)
        assertEquals(if (isTest) null else "foreign-category", mapping)
        if (isTest) assertEquals(before, remote.inventory("foreign-root"))
        write("foreign-ids-result", report("foreign-ids").put("precondition", "explicit foreign IDs / poisoned local category mapping")
            .put("foreignReadAllowed", read).put("foreignWriteAllowed", write).put("foreignDeleteAllowed", delete).put("foreignMoveAllowed", move)
            .put("foreignMappingAccepted", mapping != null).put("foreignInventoryUnchanged", before == remote.inventory("foreign-root")))
    }

    @Test fun missingAndEmptySourcesAlsoRejectOrdinaryReceiptsAndPreserveRows() = runBlocking {
        loadOriginalPortfolio(); val config = initialize()
        val source = repo.getReceiptByInternalId("e10b-receipt-1")!!
        val empty = File(context.cacheDir, "comparison-empty.pdf").apply { writeBytes(byteArrayOf()) }
        val scenarios = listOf("blank" to "", "missing" to File(context.cacheDir, "does-not-exist.pdf").absolutePath, "empty" to empty.absolutePath)
        val rows = JSONArray()
        for ((name, path) in scenarios) {
            val row = source.copy(internalId = "comparison-source-$name", id = 8000 + rows.length(), imageUrl = path,
                driveFileId = null, driveFolderId = null, driveMetadataFileId = null)
            repo.insert(row)
            assertFalse(drive.syncReceiptToDrive(token, config, row))
            val actual = repo.getReceiptByInternalId(row.internalId)!!
            assertEquals(source.unitId, actual.unitId); assertEquals(source.bruttobetrag, actual.bruttobetrag, 0.0)
            assertEquals("Keine lokale Quelldatei gefunden oder Datei ist leer.", actual.syncError)
            rows.put(JSONObject().put("scenario", name).put("receiptId", row.internalId).put("sync", false).put("localRowPreserved", true).put("error", actual.syncError))
        }
        write("source-variants-result", report("source-variants").put("scenarios", rows).put("localRowsDeleted", 0))
    }

    @Test fun standaloneRentalDocumentKeepsUnitAndTenantThroughOwnSyncAndSupplementalMerge() = runBlocking {
        loadOriginalPortfolio(); val config = initialize()
        val original = repo.getManagedDocument("receipt:e10b-receipt-1")!!
        val standalone = original.copy(documentId = "comparison-rental-document", receiptInternalId = null,
            documentType = ManagedDocumentType.MIETVERTRAG.name, tenantReference = "synthetic-tenant-reference", source = DocumentSource.DATEIIMPORT.name)
        repo.upsertManagedDocument(standalone)
        repeat(2) { assertTrue(drive.syncManagedDocumentToDrive(token, config, standalone.documentId)) }
        val synced = repo.getManagedDocument(standalone.documentId)!!
        assertEquals(standalone.unitId, synced.unitId); assertEquals(standalone.tenantReference, synced.tenantReference)
        assertTrue(SupplementalDriveBackup.backup(context, db, token, config.systemFolderId).success)
        repeat(2) { assertTrue(SupplementalDriveBackup.restore(context, db, token, config.systemFolderId, restoreMode = RestoreMode.MERGE).success) }
        val restored = repo.getManagedDocument(standalone.documentId)!!
        assertEquals(standalone.unitId, restored.unitId); assertEquals(standalone.tenantReference, restored.tenantReference)
        write("standalone-result", report("standalone").put("documentId", standalone.documentId).put("unitId", restored.unitId)
            .put("tenantReference", restored.tenantReference).put("unitAndTenantPreserved", true).put("syncPasses", 2).put("mergePasses", 2))
    }

    @Test fun authoredReceiptLinkedReviewAndOptionalReferencesAreOverwrittenByReceiptUpload() = runBlocking {
        loadOriginalPortfolio(); val config = initialize()
        val receipt = repo.getReceiptByInternalId("e10b-receipt-1")!!
        val initial = repo.getManagedDocument("receipt:${receipt.internalId}")!!
        val referenced = initial.copy(tenantReference = "synthetic-tenant-reference", loanId = db.loanDao().getAllLoans().single().id,
            renovationReference = "synthetic-renovation-reference", ocrText = "synthetic-reviewed-ocr", aiConfidence = 0.91)
        repo.upsertManagedDocument(referenced)
        val reviewed = ManagedDocumentService(context, repo).confirmReview(referenced, ManagedDocumentType.MIETVERTRAG,
            "2026-09-30", initial.unitId, "{\"_displayDescription\":\"Synthetic confirmed review\"}")
        assertTrue(drive.syncReceiptToDrive(token, config, receipt))
        val actual = repo.getManagedDocument(initial.documentId)!!
        assertNull(actual.unitId); assertNull(actual.tenantReference); assertNull(actual.loanId); assertNull(actual.renovationReference)
        assertEquals(ManagedDocumentType.RECHNUNG.name, actual.documentType)
        assertEquals(receipt.datum, actual.documentDate)
        assertEquals("", actual.ocrText); assertEquals(0.0, actual.aiConfidence, 0.0)
        assertEquals(reviewed.extractedFieldsJson, actual.extractedFieldsJson)
        assertEquals(receipt.unitId, repo.getReceiptByInternalId(receipt.internalId)!!.unitId)
        val moshi = com.squareup.moshi.Moshi.Builder().addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(ManagedDocument::class.java)
        write("review-loss-result", report("review-loss").put("before", JSONObject(adapter.toJson(reviewed)))
            .put("after", JSONObject(adapter.toJson(actual))).put("referencesAreExplicitSyntheticPrecondition", true))
    }

    companion object {
        const val PRODUCT_ROOT = "Steuerassistent Belege"
        const val TEST_ROOT = "ImmoPilot Phase11A Testdaten"
        const val PRODUCT_TAG = "ImmobilienBelegApp"
        const val TEST_TAG = "ImmoPilotPhase11ATest"
    }
}
