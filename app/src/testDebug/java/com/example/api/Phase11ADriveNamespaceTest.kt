package com.example.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.qa.Phase11ADriveHttpFixture
import com.example.qa.Phase11APortfolioSeeder
import com.example.ui.IsolatedAppDatabaseRule
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class Phase11ADriveNamespaceTest {
    @get:Rule val isolation = IsolatedAppDatabaseRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val db get() = AppDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
    private val repository get() = ReceiptRepository(db.receiptDao(), db.propertyDao(), db.receiptEntityDao(), db.belegDao(),
        db.exportAuditDao(), db.receiptDocumentDao(), db.managedDocumentDao())
    private val drive get() = DrivePersistenceRepository(context, repository)
    private val token = "synthetic-same-account"
    private val runId = "P11A-2026-10-25-UNIT-DRIVE-${java.util.UUID.randomUUID()}"
    private lateinit var remote: Phase11ADriveHttpFixture
    private lateinit var previousClient: OkHttpClient
    private val transport get() = GoogleDriveClient::class.java.getDeclaredField("client").apply { isAccessible = true }
    private val folder = Phase11ADriveHttpFixture.FOLDER

    @Before fun setup() {
        remote = Phase11ADriveHttpFixture()
        remote.put("prod-root", DriveEnvironment.PRODUCTION_ROOT, remote.myDrive, mime = folder)
        remote.put("prod-system", "_BelegApp-Daten", "prod-root", mime = folder)
        remote.put("prod-config", "app-config.json", "prod-system", "{\"productionSecretSentinel\":\"never-load\"}", "appConfig")
        remote.put("prod-index", "receipt-index.json", "prod-system", "{\"entries\":[]}", "receiptIndex")
        remote.put("prod-original", "Original.pdf", "prod-root", "%PDF-synthetic-product-sentinel", mime = "application/pdf")
        remote.put("prod-category", "Kategorie", "prod-root", mime = folder).properties = mapOf(
            "appName" to "ImmobilienBelegApp", "folderRole" to "CATEGORY", "canonicalPathKey" to "2026/Material")
        previousClient = transport.get(null) as OkHttpClient
        transport.set(null, OkHttpClient.Builder().addInterceptor(remote).build())
        GoogleDriveClient.clearFolderCache()
    }
    @After fun cleanup() {
        transport.set(null, previousClient)
        GoogleDriveClient.clearFolderCache()
        context.getSharedPreferences("phase11a_seed_journal", 0).edit().clear().commit()
        context.getSharedPreferences("category_folder_mappings_prefs", 0).edit().clear().commit()
        PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        File(context.filesDir, "managed_documents").deleteRecursively()
    }
    private suspend fun initialize(): DriveAppConfig = when (val result = drive.initializeDriveStorage(token)) {
        is DriveInitializationResult.SuccessCreatedNew -> result.config
        is DriveInitializationResult.SuccessLoadedExisting -> result.config
        is DriveInitializationResult.Failure -> error(result.error)
    }
    private fun assertNoProductContentOrMutation() {
        assertFalse(remote.requests.any { r -> r.url.encodedPath.substringAfterLast('/') in setOf("prod-config", "prod-index", "prod-original") &&
            (r.method != "GET" || r.url.queryParameter("alt") == "media") })
    }

    @Test fun buildIdentitySelectsExactlyOneRootAndBothWrongRootDirectionsFail() {
        assertEquals(DriveEnvironment.PRODUCTION_ROOT, DriveNamespacePolicy("com.aistudio.steuerassistent.vnhqkp").rootFolderName)
        assertEquals(DriveNamespacePolicy.TEST_ROOT, DriveNamespacePolicy(DriveNamespacePolicy.TEST_PACKAGE).rootFolderName)
        assertEquals(DriveEnvironment.PRODUCTION_APP_IDENTIFIER, DriveNamespacePolicy("com.aistudio.steuerassistent.vnhqkp").appIdentifier)
        assertEquals(DriveNamespacePolicy.TEST_APP_IDENTIFIER, DriveEnvironment.appIdentifier)
        assertThrows(java.io.IOException::class.java) { DriveNamespacePolicy(DriveNamespacePolicy.TEST_PACKAGE, DriveEnvironment.PRODUCTION_ROOT) }
        assertThrows(java.io.IOException::class.java) { DriveNamespacePolicy("com.aistudio.steuerassistent.vnhqkp", DriveNamespacePolicy.TEST_ROOT) }
        assertThrows(java.io.IOException::class.java) { DriveNamespacePolicy("unknown.application") }
    }

    @Test fun productionPolicyDelegatesOriginalRequestWithoutPreflightOrRewrite() {
        val request = Request.Builder().url("https://www.googleapis.com/drive/v3/files/prod-config?alt=media").build()
        var sent = 0
        DriveNamespacePolicy("com.aistudio.steuerassistent.vnhqkp").execute(request) {
            assertSame(request, it); sent++
            previousClient.newBuilder().addInterceptor(remote).build().newCall(it).execute()
        }.close()
        assertEquals(1, sent)
    }

    @Test fun existingProductStructureIsNotLoadedAndRepeatedInitializationHasOwnSevenFolderIds() = runBlocking {
        val productBefore = remote.inventory("prod-root")
        val config = initialize()
        val loaded = initialize()
        // Existing parsing assigns a local installation ID; folder identity must not change.
        assertEquals(config.copy(installationId = loaded.installationId), loaded)
        assertNotEquals("prod-root", config.rootFolderId)
        assertNotEquals("prod-system", config.systemFolderId)
        assertEquals(DriveNamespacePolicy.TEST_ROOT, remote.entries.getValue(config.rootFolderId).name)
        GoogleDriveClient.validateStorageConfig(token, config)
        assertEquals(1, remote.entries.values.count { it.parent == remote.myDrive && it.name == DriveNamespacePolicy.TEST_ROOT })
        assertEquals(1, remote.entries.values.count { it.parent == config.rootFolderId && it.name == "_BelegApp-Daten" })
        assertEquals(productBefore, remote.inventory("prod-root"))
        assertTrue(remote.orphanIds().isEmpty())
        assertNoProductContentOrMutation()
        assertTrue(remote.requests.all { it.header("Authorization") == "Bearer $token" })
    }

    @Test fun directForeignReadsWritesDeletesAndCrossMovesAreDeniedWithoutProductChanges() = runBlocking {
        val config = initialize()
        val before = remote.inventory("prod-root")
        assertThrows(java.io.IOException::class.java) { runBlocking { GoogleDriveClient.downloadJson(token, "prod-config") } }
        assertNull(GoogleDriveClient.downloadFileBytes(token, "prod-original"))
        assertNull(GoogleDriveClient.getFileMetadata(token, "prod-original"))
        assertFalse(GoogleDriveClient.updateJson(token, "prod-config", "{}").success)
        assertFalse(GoogleDriveClient.uploadJson(token, "prod-system", "test.json", "{}").success)
        assertFalse(GoogleDriveClient.deleteFile(token, "prod-original"))
        assertNull(GoogleDriveClient.uploadFile(token, config.receiptsFolderId, "test.pdf", "application/pdf", byteArrayOf(1), "prod-original"))
        assertFalse(GoogleDriveClient.moveAndRenameFile(token, "prod-original", config.receiptsFolderId, listOf("prod-root"), "renamed.pdf"))
        val own = GoogleDriveClient.uploadJson(token, config.systemFolderId, "own.json", "{}").fileId!!
        assertFalse(GoogleDriveClient.moveAndRenameFile(token, own, "prod-root", listOf(config.systemFolderId), "cross.json"))
        assertEquals(before, remote.inventory("prod-root"))
        assertNoProductContentOrMutation()
    }

    @Test fun allSevenForeignConfigIdsAreRejectedAndForeignRestoreCannotClearLocalData() = runBlocking {
        val config = initialize()
        val variants = listOf(config.copy(rootFolderId = "prod-root"), config.copy(systemFolderId = "prod-system"),
            config.copy(receiptsFolderId = "prod-system"), config.copy(paymentsFolderId = "prod-system"),
            config.copy(exportsFolderId = "prod-system"), config.copy(transactionsFolderId = "prod-system"), config.copy(backupsFolderId = "prod-system"))
        repository.updatePropertyMetadata(PropertyMetadata(propertyId = "keep-local", name = "Lokaler Testbestand"))
        val localReceipt = Receipt(id = 123, internalId = "keep-local-receipt", propertyId = "keep-local", aussteller = "Synthetischer Test",
            datum = "2026-10-25", uhrzeit = "12:00", bruttobetrag = 12.34, hauptkategorie = "Sonstige Ausgaben",
            unterkategorie = "Material", kontoNr = "4971", beschreibung = "Datenverlustschutz-Test")
        repository.insert(localReceipt)
        val beforeReceipt = requireNotNull(repository.getReceiptByInternalId(localReceipt.internalId))
        for (bad in variants) {
            assertThrows(java.io.IOException::class.java) { GoogleDriveClient.validateStorageConfig(token, bad) }
            val result = drive.executeFullDriveRestore(token, bad, RestoreMode.REPLACE_FULL)
            assertFalse(result.isSuccess)
            assertTrue(result.errors.any { it.contains("DRIVE_NAMESPACE_MISMATCH") })
            assertEquals("keep-local", repository.getPropertyMetadata()!!.propertyId)
            assertEquals(beforeReceipt, repository.getReceiptByInternalId(localReceipt.internalId))
        }
        assertNoProductContentOrMutation()
    }

    @Test fun ownConfigAndIndexCannotSmuggleForeignIdsIntoRestore() = runBlocking {
        val config = initialize()
        val poisoned = with(drive) { config.copy(backupsFolderId = "prod-system").toJson() }
        val foreignIndex = "{\"entries\":[{\"metadataFileId\":\"prod-config\",\"mainDriveFileId\":\"prod-original\"}]}"
        assertFalse(GoogleDriveClient.uploadJson(token, config.systemFolderId, "poisoned-index.json", foreignIndex).success)
        // Also reject a foreign reference placed into our own tree by an external client.
        val index = remote.put("external-index", "poisoned-index.json", config.systemFolderId, foreignIndex).id
        assertThrows(java.io.IOException::class.java) { runBlocking { GoogleDriveClient.downloadJson(token, index) } }
        val configFile = GoogleDriveClient.findFileByAppProperty(token, config.systemFolderId, "appConfig")!!
        assertFalse(GoogleDriveClient.updateJson(token, configFile.id, poisoned).success)
        remote.entries.getValue(configFile.id).bytes = poisoned.toByteArray()
        assertTrue(drive.initializeDriveStorage(token) is DriveInitializationResult.Failure)
        assertNoProductContentOrMutation()
    }

    @Test fun legacyCategorySearchAndGlobalReceiptSearchRemainInsideTestRoot() = runBlocking {
        val config = initialize()
        val before = remote.inventory("prod-root")
        remote.entries.getValue("prod-original").properties = mapOf("appName" to "ImmobilienBelegApp", "receiptInternalId" to "same-receipt", "documentRole" to "ORIGINAL")
        val category = GoogleDriveClient.getOrCreateFolder(token, "Kategorie", config.rootFolderId, "2026/Material", config.systemFolderId, context)!!
        assertNotEquals("prod-category", category)
        assertEquals(config.rootFolderId, remote.entries.getValue(category).parent)
        assertNull(GoogleDriveClient.findFileByReceiptPropertiesGlobally(token, "same-receipt"))
        val own = GoogleDriveClient.uploadFile(token, category, "test.pdf", "application/pdf", "%PDF-test".toByteArray(),
            appProperties = mapOf("appName" to DriveEnvironment.appIdentifier, "receiptInternalId" to "same-receipt", "documentRole" to "ORIGINAL"))!!
        assertEquals(own, GoogleDriveClient.findFileByReceiptPropertiesGlobally(token, "same-receipt")!!.id)
        // Exclude the deliberately changed synthetic product tag from the baseline assertion.
        remote.entries.getValue("prod-original").properties = emptyMap()
        assertEquals(before, remote.inventory("prod-root"))
        assertNoProductContentOrMutation()
    }

    @Test fun staleLocalMappingAndMovedCachedFolderAreNotTrusted() = runBlocking {
        val config = initialize()
        val prefs = context.getSharedPreferences("category_folder_mappings_prefs", 0)
        prefs.edit().putString("poison", "prod-category").commit()
        assertThrows(java.io.IOException::class.java) { runBlocking {
            GoogleDriveClient.getOrCreateFolder(token, "Material", config.rootFolderId, "poison", config.systemFolderId, context)
        } }
        assertEquals("prod-category", prefs.getString("poison", null))
        val own = GoogleDriveClient.getOrCreateFolder(token, "Move-test", config.rootFolderId)!!
        remote.entries.getValue(own).parent = "prod-root" // An external move invalidates prior ownership.
        assertThrows(java.io.IOException::class.java) { runBlocking { GoogleDriveClient.getOrCreateFolder(token, "Move-test", config.rootFolderId) } }
        assertNoProductContentOrMutation()
    }

    @Test fun unchangedProductionGlobalQueriesCannotSelectTestCategoryOrOriginal() = runBlocking {
        val config = initialize()
        remote.entries.getValue("prod-original").properties = mapOf("appName" to DriveEnvironment.PRODUCTION_APP_IDENTIFIER,
            "receiptInternalId" to "same-receipt", "documentRole" to "ORIGINAL")
        val before = remote.inventory("prod-root")
        val category = GoogleDriveClient.getOrCreateFolder(token, "Kategorie", config.rootFolderId, "2026/Material", config.systemFolderId, context)!!
        val original = GoogleDriveClient.uploadFile(token, category, "own.pdf", "application/pdf", "%PDF-test".toByteArray(),
            appProperties = mapOf("appName" to DriveEnvironment.appIdentifier, "receiptInternalId" to "same-receipt", "documentRole" to "ORIGINAL"))!!
        val raw = OkHttpClient.Builder().addInterceptor(remote).build()
        fun productLookup(extra: String): Set<String> {
            val q = "appProperties has { key='appName' and value='${DriveEnvironment.PRODUCTION_APP_IDENTIFIER}' } and $extra and trashed = false"
            val request = Request.Builder().url("https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", q).addQueryParameter("fields", "files(id,name)").build())
                .header("Authorization", "Bearer $token").build()
            return DriveNamespacePolicy("com.aistudio.steuerassistent.vnhqkp").execute(request) { raw.newCall(it).execute() }.use {
                val files = JSONObject(it.body!!.string()).getJSONArray("files")
                (0 until files.length()).map { i -> files.getJSONObject(i).getString("id") }.toSet()
            }
        }
        assertEquals(setOf("prod-category"), productLookup("appProperties has { key='canonicalPathKey' and value='2026/Material' }"))
        assertEquals(setOf("prod-original"), productLookup("appProperties has { key='receiptInternalId' and value='same-receipt' }"))
        assertEquals(original, GoogleDriveClient.findFileByReceiptPropertiesGlobally(token, "same-receipt")!!.id)
        assertNull(GoogleDriveClient.getOrCreateFolder(token, DriveEnvironment.PRODUCTION_ROOT, config.rootFolderId))
        assertNull(GoogleDriveClient.uploadFile(token, category, "bad.pdf", "application/pdf", byteArrayOf(1),
            appProperties = mapOf("appName" to DriveEnvironment.PRODUCTION_APP_IDENTIFIER)))
        assertEquals(before, remote.inventory("prod-root"))
        assertNoProductContentOrMutation()
    }

    @Test fun shortcutsAndNestedImitationRootCannotEscapeTheNamespace() = runBlocking {
        val config = initialize()
        remote.put("shortcut", "Shortcut", config.rootFolderId, mime = "application/vnd.google-apps.shortcut")
        assertNull(GoogleDriveClient.downloadFileBytes(token, "shortcut"))
        remote.put("nested-root", DriveNamespacePolicy.TEST_ROOT, "prod-root", mime = folder)
        remote.put("nested-config", "app-config.json", "nested-root")
        assertThrows(java.io.IOException::class.java) { runBlocking { GoogleDriveClient.downloadJson(token, "nested-config") } }
        assertNoProductContentOrMutation()
    }

    @Test fun missingTestBackupNeverFallsBackToProductConfig() = runBlocking {
        val before = remote.inventory("prod-root")
        assertNull(drive.getExistingDriveAppConfigReadOnly(token))
        assertEquals(before, remote.inventory("prod-root"))
        assertNoProductContentOrMutation()
    }

    @Test fun diagnosticDriveTestUsesOwnRootAndReusesItsTestFile() {
        val before = remote.inventory("prod-root")
        val first = GoogleDriveClient.runDriveTest(token)
        assertTrue(first.toString(), first.uploadSuccess && first.downloadSuccess && first.contentIdentical)
        val second = GoogleDriveClient.runDriveTest(token)
        assertTrue(second.toString(), second.uploadSuccess && second.downloadSuccess && second.contentIdentical)
        assertEquals(first.mainFolderId, second.mainFolderId)
        assertEquals(first.systemFolderId, second.systemFolderId)
        assertEquals(first.testFileId, second.testFileId)
        assertEquals(DriveNamespacePolicy.TEST_ROOT, remote.entries.getValue(first.mainFolderId).name)
        assertEquals(before, remote.inventory("prod-root"))
        assertNoProductContentOrMutation()
    }

    @Test fun actualFullPortfolioTransportAndDoubleMergeStayIsolatedAndReportFilelessReceiptFailure() = runBlocking {
        Phase11APortfolioSeeder.seedBundled(context, db, runId)
        assertTrue(db.receiptDao().getAllReceiptsIncludingDeletedList().all {
            it.driveFileId.isNullOrBlank() && it.driveFolderId.isNullOrBlank() && it.driveMetadataFileId.isNullOrBlank()
        })
        assertTrue(db.managedDocumentDao().getAll().all {
            it.driveFileId.isNullOrBlank() && it.driveFolderId.isNullOrBlank() && it.legacyDriveFolderId.isNullOrBlank()
        })
        val productBefore = remote.inventory("prod-root")
        val config = initialize()
        val originals = db.managedDocumentDao().getAll().filter { it.fileSizeBytes > 0 }.associate { it.documentId to it.sha256 }
        val ids = db.receiptDao().getAllReceiptsIncludingDeletedList().map { it.internalId }.toSet()
        val fileless = db.receiptDao().getAllReceiptsIncludingDeletedList().filter { it.imageUrl.isBlank() }.map { it.internalId }.toSet()
        assertEquals(10, fileless.size)
        suspend fun backup() {
            for (receipt in db.receiptDao().getAllReceiptsIncludingDeletedList()) {
                val result = drive.syncReceiptToDrive(token, config, receipt)
                if (receipt.internalId in fileless) {
                    // E11A-02: reproduced existing product limitation, never report full UI-sync green.
                    assertFalse(receipt.internalId, result)
                    assertEquals("Keine lokale Quelldatei gefunden oder Datei ist leer.", repository.getReceiptByInternalId(receipt.internalId)!!.syncError)
                } else assertTrue(receipt.internalId, result)
            }
            assertTrue(drive.saveStammdatenToDrive(token, config))
            assertTrue(SupplementalDriveBackup.backup(context, db, token, config.systemFolderId).success)
        }
        backup()
        val fileIds = remote.descendants(config.rootFolderId).toSet()
        backup()
        assertEquals(fileIds, remote.descendants(config.rootFolderId).toSet())
        repeat(2) {
            val result = drive.executeFullDriveRestore(token, config, RestoreMode.MERGE)
            assertTrue(result.errors.toString(), result.isSuccess)
            assertTrue(SupplementalDriveBackup.restore(context, db, token, config.systemFolderId, restoreMode = RestoreMode.MERGE).success)
            assertEquals(ids, db.receiptDao().getAllReceiptsIncludingDeletedList().map { it.internalId }.toSet())
            assertEquals(5, db.propertyDao().getAllProperties().size)
            assertEquals(120, db.bankDao().getAllTransactions().size)
            assertEquals(10, db.logbookDao().getAllTrips().size)
            assertEquals(70, db.managedDocumentDao().getAll().size)
            assertEquals(originals, db.managedDocumentDao().getAll().filter { it.fileSizeBytes > 0 }.associate { it.documentId to it.sha256 })
            db.managedDocumentDao().getAll().filter { it.fileSizeBytes > 0 }.forEach { doc ->
                val bytes = GoogleDriveClient.downloadFileBytes(token, doc.driveFileId!!)!!
                assertEquals(doc.sha256, StableDocumentIdentity.sha256(bytes))
            }
        }
        assertEquals(60, originals.size)
        assertTrue(remote.orphanIds().isEmpty())
        assertEquals(productBefore, remote.inventory("prod-root"))
        assertNoProductContentOrMutation()
        val persisted = Phase11ASnapshot.capture(context, db, JSONObject().put("runId", runId)
            .put("label", "namespace-after-two-merges").put("source", "UNIT_TEST")
            .put("commit", System.getenv("GITHUB_SHA") ?: "68218f359db93388caf4ca995629f41f5655b57d")
            .put("capturedAt", java.time.Instant.now().toString()).put("device", "Robolectric")
            .put("android", android.os.Build.VERSION.RELEASE).put("appVersion", "1.0"))
        File("build/reports/phase11a-drive/after-merge-2.json").apply {
            requireNotNull(parentFile).mkdirs(); writeText(persisted.toString(2))
        }
        val report = JSONObject().put("status", "AUTOMATISIERT BESTANDEN").put("practicalStatus", "NOCH OFFEN")
            .put("runId", runId).put("transport", "IN_MEMORY_HTTP_SAME_ACCOUNT")
            .put("productInventoryUnchanged", true).put("backupTransportPasses", 2).put("mergeRestores", 2).put("originalHashesMatched", 60)
            .put("fullUiSyncSuccess", false).put("openFinding", "E11A-02").put("missingSourceReceiptIds", JSONArray(fileless.sorted()))
            .put("rootDuplicates", 0).put("orphans", JSONArray(remote.orphanIds())).put("receiptIds", JSONArray(ids.sorted()))
            .put("productBefore", JSONObject(productBefore)).put("productAfter", JSONObject(remote.inventory("prod-root")))
            .put("testRootId", config.rootFolderId).put("testSystemId", config.systemFolderId)
            .put("testFileIds", JSONArray(remote.descendants(config.rootFolderId)))
            .put("originalHashes", JSONObject(originals))
        File("build/reports/phase11a-drive/namespace.json").apply { requireNotNull(parentFile).mkdirs(); writeText(report.toString(2)) }
        Unit
    }
}
