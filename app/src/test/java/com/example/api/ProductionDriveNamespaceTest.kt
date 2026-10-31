package com.example.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.comparison.ComparisonDriveHttpFixture
import com.example.ui.IsolatedAppDatabaseRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ProductionDriveNamespaceTest {
    @get:Rule val isolation = IsolatedAppDatabaseRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val db get() = AppDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
    private val repository get() = ReceiptRepository(db.receiptDao(), db.propertyDao(), db.receiptEntityDao(), db.belegDao(),
        db.exportAuditDao(), db.receiptDocumentDao(), db.managedDocumentDao())
    private val drive get() = DrivePersistenceRepository(context, repository)
    private val token = "synthetic-same-account"
    private val runId = "P11A-2026-10-25-UNIT-DRIVE-${java.util.UUID.randomUUID()}"
    private lateinit var remote: ComparisonDriveHttpFixture
    private lateinit var previousClient: OkHttpClient
    private val transport get() = GoogleDriveClient::class.java.getDeclaredField("client").apply { isAccessible = true }
    private val folder = ComparisonDriveHttpFixture.FOLDER
    private val foreignRootName get() = if (DriveEnvironment.isIsolatedTest) DriveEnvironment.PRODUCTION_ROOT else DriveNamespacePolicy.TEST_ROOT
    private val foreignIdentifier get() = if (DriveEnvironment.isIsolatedTest) DriveEnvironment.PRODUCTION_APP_IDENTIFIER else DriveNamespacePolicy.TEST_APP_IDENTIFIER

    @Before fun setup() {
        remote = ComparisonDriveHttpFixture()
        remote.put("foreign-root", foreignRootName, remote.myDrive, mime = folder)
        remote.put("foreign-system", "_BelegApp-Daten", "foreign-root", mime = folder)
        remote.put("foreign-config", "app-config.json", "foreign-system", "{\"productionSecretSentinel\":\"never-load\"}", "appConfig")
        remote.put("foreign-index", "receipt-index.json", "foreign-system", "{\"entries\":[]}", "receiptIndex")
        remote.put("foreign-original", "Original.pdf", "foreign-root", "%PDF-synthetic-product-sentinel", mime = "application/pdf")
        remote.put("foreign-category", "Kategorie", "foreign-root", mime = folder).properties = mapOf(
            "appName" to foreignIdentifier, "folderRole" to "CATEGORY", "canonicalPathKey" to "2026/Material")
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
        assertFalse(remote.requests.any { r -> r.url.encodedPath.substringAfterLast('/') in setOf("foreign-config", "foreign-index", "foreign-original") &&
            (r.method != "GET" || r.url.queryParameter("alt") == "media") })
    }

    @Test fun buildIdentitySelectsExactlyOneRootAndBothWrongRootDirectionsFail() {
        assertEquals(DriveEnvironment.PRODUCTION_ROOT, DriveNamespacePolicy("com.aistudio.steuerassistent.vnhqkp").rootFolderName)
        assertEquals(DriveNamespacePolicy.TEST_ROOT, DriveNamespacePolicy(DriveNamespacePolicy.TEST_PACKAGE).rootFolderName)
        assertEquals(DriveEnvironment.PRODUCTION_APP_IDENTIFIER, DriveNamespacePolicy("com.aistudio.steuerassistent.vnhqkp").appIdentifier)
        assertEquals(DriveNamespacePolicy(com.example.BuildConfig.APPLICATION_ID).appIdentifier, DriveEnvironment.appIdentifier)
        assertThrows(java.io.IOException::class.java) { DriveNamespacePolicy(DriveNamespacePolicy.TEST_PACKAGE, DriveEnvironment.PRODUCTION_ROOT) }
        assertThrows(java.io.IOException::class.java) { DriveNamespacePolicy("com.aistudio.steuerassistent.vnhqkp", DriveNamespacePolicy.TEST_ROOT) }
        assertThrows(java.io.IOException::class.java) { DriveNamespacePolicy("unknown.application") }
    }

    @Test fun activeBuildAlsoRejectsForeignContentAndUnboundedSearches() {
        val raw = OkHttpClient.Builder().addInterceptor(remote).build()
        val policy = DriveNamespacePolicy(com.example.BuildConfig.APPLICATION_ID)
        for (url in listOf("https://www.googleapis.com/drive/v3/files/foreign-config?alt=media",
            "https://www.googleapis.com/drive/v3/files?q=name%20%3D%20%27app-config.json%27")) {
            val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            assertThrows(java.io.IOException::class.java) { policy.execute(request) { raw.newCall(it).execute() }.close() }
        }
        assertNoProductContentOrMutation()
    }

    @Test fun existingForeignStructureIsNotLoadedAndRepeatedInitializationHasOwnSevenFolderIds() = runBlocking {
        val productBefore = remote.inventory("foreign-root")
        val config = initialize()
        val loaded = initialize()
        // Existing parsing assigns a local installation ID; folder identity must not change.
        assertEquals(config.copy(installationId = loaded.installationId), loaded)
        assertNotEquals("foreign-root", config.rootFolderId)
        assertNotEquals("foreign-system", config.systemFolderId)
        assertEquals(DriveEnvironment.rootFolderName, remote.entries.getValue(config.rootFolderId).name)
        GoogleDriveClient.validateStorageConfig(token, config)
        assertEquals(1, remote.entries.values.count { it.parent == remote.myDrive && it.name == DriveEnvironment.rootFolderName })
        assertEquals(1, remote.entries.values.count { it.parent == config.rootFolderId && it.name == "_BelegApp-Daten" })
        assertEquals(productBefore, remote.inventory("foreign-root"))
        assertTrue(remote.orphanIds().isEmpty())
        assertNoProductContentOrMutation()
        assertTrue(remote.requests.all { it.header("Authorization") == "Bearer $token" })
    }

    @Test fun directForeignReadsWritesDeletesAndCrossMovesAreDeniedWithoutProductChanges() = runBlocking {
        val config = initialize()
        val before = remote.inventory("foreign-root")
        assertThrows(java.io.IOException::class.java) { runBlocking { GoogleDriveClient.downloadJson(token, "foreign-config") } }
        assertNull(GoogleDriveClient.downloadFileBytes(token, "foreign-original"))
        assertNull(GoogleDriveClient.getFileMetadata(token, "foreign-original"))
        assertFalse(GoogleDriveClient.updateJson(token, "foreign-config", "{}").success)
        assertFalse(GoogleDriveClient.uploadJson(token, "foreign-system", "test.json", "{}").success)
        assertFalse(GoogleDriveClient.deleteFile(token, "foreign-original"))
        assertNull(GoogleDriveClient.uploadFile(token, config.receiptsFolderId, "test.pdf", "application/pdf", byteArrayOf(1), "foreign-original"))
        assertFalse(GoogleDriveClient.moveAndRenameFile(token, "foreign-original", config.receiptsFolderId, listOf("foreign-root"), "renamed.pdf"))
        val own = GoogleDriveClient.uploadJson(token, config.systemFolderId, "own.json", "{}").fileId!!
        assertFalse(GoogleDriveClient.moveAndRenameFile(token, own, "foreign-root", listOf(config.systemFolderId), "cross.json"))
        assertEquals(before, remote.inventory("foreign-root"))
        assertNoProductContentOrMutation()
    }

    @Test fun allSevenForeignConfigIdsAreRejectedAndForeignRestoreCannotClearLocalData() = runBlocking {
        val config = initialize()
        val variants = listOf(config.copy(rootFolderId = "foreign-root"), config.copy(systemFolderId = "foreign-system"),
            config.copy(receiptsFolderId = "foreign-system"), config.copy(paymentsFolderId = "foreign-system"),
            config.copy(exportsFolderId = "foreign-system"), config.copy(transactionsFolderId = "foreign-system"), config.copy(backupsFolderId = "foreign-system"))
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
        val poisoned = with(drive) { config.copy(backupsFolderId = "foreign-system").toJson() }
        val foreignIndex = "{\"entries\":[{\"metadataFileId\":\"foreign-config\",\"mainDriveFileId\":\"foreign-original\"}]}"
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
        val before = remote.inventory("foreign-root")
        remote.entries.getValue("foreign-original").properties = mapOf("appName" to foreignIdentifier, "receiptInternalId" to "same-receipt", "documentRole" to "ORIGINAL")
        val category = GoogleDriveClient.getOrCreateFolder(token, "Kategorie", config.rootFolderId, "2026/Material", config.systemFolderId, context)!!
        assertNotEquals("foreign-category", category)
        assertEquals(config.rootFolderId, remote.entries.getValue(category).parent)
        assertNull(GoogleDriveClient.findFileByReceiptPropertiesGlobally(token, "same-receipt"))
        val own = GoogleDriveClient.uploadFile(token, category, "test.pdf", "application/pdf", "%PDF-test".toByteArray(),
            appProperties = mapOf("appName" to DriveEnvironment.appIdentifier, "receiptInternalId" to "same-receipt", "documentRole" to "ORIGINAL"))!!
        assertEquals(own, GoogleDriveClient.findFileByReceiptPropertiesGlobally(token, "same-receipt")!!.id)
        // Exclude the deliberately changed synthetic product tag from the baseline assertion.
        remote.entries.getValue("foreign-original").properties = emptyMap()
        assertEquals(before, remote.inventory("foreign-root"))
        assertNoProductContentOrMutation()
    }

    @Test fun staleLocalMappingAndMovedCachedFolderAreNotTrusted() = runBlocking {
        val config = initialize()
        val prefs = context.getSharedPreferences("category_folder_mappings_prefs", 0)
        prefs.edit().putString("poison", "foreign-category").commit()
        assertThrows(java.io.IOException::class.java) { runBlocking {
            GoogleDriveClient.getOrCreateFolder(token, "Material", config.rootFolderId, "poison", config.systemFolderId, context)
        } }
        assertEquals("foreign-category", prefs.getString("poison", null))
        val own = GoogleDriveClient.getOrCreateFolder(token, "Move-test", config.rootFolderId)!!
        remote.entries.getValue(own).parent = "foreign-root" // An external move invalidates prior ownership.
        assertThrows(java.io.IOException::class.java) { runBlocking { GoogleDriveClient.getOrCreateFolder(token, "Move-test", config.rootFolderId) } }
        assertNoProductContentOrMutation()
    }

    @Test fun shortcutsAndNestedImitationRootCannotEscapeTheNamespace() = runBlocking {
        val config = initialize()
        remote.put("shortcut", "Shortcut", config.rootFolderId, mime = "application/vnd.google-apps.shortcut")
        assertNull(GoogleDriveClient.downloadFileBytes(token, "shortcut"))
        remote.put("nested-root", DriveEnvironment.rootFolderName, "foreign-root", mime = folder)
        remote.put("nested-config", "app-config.json", "nested-root")
        assertThrows(java.io.IOException::class.java) { runBlocking { GoogleDriveClient.downloadJson(token, "nested-config") } }
        assertNoProductContentOrMutation()
    }

    @Test fun missingOwnBackupNeverFallsBackToForeignConfig() = runBlocking {
        val before = remote.inventory("foreign-root")
        assertNull(drive.getExistingDriveAppConfigReadOnly(token))
        assertEquals(before, remote.inventory("foreign-root"))
        assertNoProductContentOrMutation()
    }

    @Test fun diagnosticDriveTestUsesOwnRootAndReusesItsTestFile() {
        val before = remote.inventory("foreign-root")
        val first = GoogleDriveClient.runDriveTest(token)
        assertTrue(first.toString(), first.uploadSuccess && first.downloadSuccess && first.contentIdentical)
        val second = GoogleDriveClient.runDriveTest(token)
        assertTrue(second.toString(), second.uploadSuccess && second.downloadSuccess && second.contentIdentical)
        assertEquals(first.mainFolderId, second.mainFolderId)
        assertEquals(first.systemFolderId, second.systemFolderId)
        assertEquals(first.testFileId, second.testFileId)
        assertEquals(DriveEnvironment.rootFolderName, remote.entries.getValue(first.mainFolderId).name)
        assertEquals(before, remote.inventory("foreign-root"))
        assertNoProductContentOrMutation()
    }

    @Test fun protectedSystemFoldersAndNonemptyCategoryCannotBeDeleted() = runBlocking {
        val config = initialize()
        for (id in listOf(config.rootFolderId,config.systemFolderId,config.receiptsFolderId,config.backupsFolderId)) {
            assertFalse(GoogleDriveClient.deleteFile(token,id))
            assertFalse(GoogleDriveClient.updateJson(token,id,"{}").success)
        }
        val category = GoogleDriveClient.getOrCreateFolder(token,"Disposable category",config.rootFolderId)!!
        remote.put("child","Keep.json",category)
        assertFalse(GoogleDriveClient.deleteFile(token,category))
        assertTrue(remote.entries.containsKey("child"))
        remote.entries.remove("child") // synthetic fixture models an actually empty category
        assertTrue(GoogleDriveClient.deleteFile(token,category))
    }

    @Test fun forgedFileFolderRolesAndForeignDeletionCannotEraseLocalReceipt() = runBlocking {
        val config = initialize()
        assertFalse(GoogleDriveClient.uploadJson(token,config.systemFolderId,"bad.json",
            JSONObject().put("driveFileId",config.systemFolderId).toString()).success)
        val r = Receipt(id=987,internalId="keep-local",aussteller="Synthetic",datum="2026-10-25",uhrzeit="12:00",
            bruttobetrag=1.0,hauptkategorie="Test",unterkategorie="Test",kontoNr="4800",beschreibung="Test")
        repository.insert(r)
        val stored = repository.getReceiptByInternalId(r.internalId)!!
        val foreign = remote.inventory("foreign-root")
        for (badId in listOf("foreign-original",config.systemFolderId)) {
            assertTrue(drive.permanentlyDeleteReceipt(token,config,stored.copy(driveFileId=badId)) is PermanentDeleteResult.Error)
            assertEquals(stored,repository.getReceiptByInternalId(r.internalId))
        }
        assertEquals(foreign,remote.inventory("foreign-root"))
    }

    @Test fun missingHistoricalTombstoneReferenceIsReadableButCurrentOrForeignReferenceIsNot() = runBlocking {
        val config = initialize()
        assertTrue(GoogleDriveClient.uploadJson(token,config.systemFolderId,"history.json",
            JSONObject().put("previousMainDriveFileId","already-deleted").toString()).success)
        assertFalse(GoogleDriveClient.uploadJson(token,config.systemFolderId,"current.json",
            JSONObject().put("driveFileId","already-deleted").toString()).success)
        assertFalse(GoogleDriveClient.uploadJson(token,config.systemFolderId,"foreign-history.json",
            JSONObject().put("previousMainDriveFileId","foreign-original").toString()).success)
    }

    private suspend fun installDiagnosticConfig(config: DriveAppConfig, mainId: String, metadataId: String): Pair<String, String> {
        val file = GoogleDriveClient.findFileByAppProperty(token, config.systemFolderId, "appConfig")!!
        val json = with(drive) { config.copy(testReferences = DriveTestReferences(
            "synthetic-old-test", "TEST-BLG-2026-000001", mainId, metadataId)).toJson() }
        remote.entries.getValue(file.id).bytes = json.toByteArray()
        return file.id to json
    }

    @Test fun missingDiagnosticOriginalDoesNotBlockExistingConfigOrRewriteRemoteData() = runBlocking {
        val config = initialize()
        remote.put("old-test-meta", "test.json", config.receiptsFolderId)
        val (id, json) = installDiagnosticConfig(config, "deleted-test-original", "old-test-meta")
        val before = remote.inventory(config.rootFolderId)
        val result = drive.initializeDriveStorage(token)
        assertTrue(result.toString(), result is DriveInitializationResult.SuccessLoadedExisting)
        val loaded = (result as DriveInitializationResult.SuccessLoadedExisting).config
        assertEquals(config.rootFolderId, loaded.rootFolderId)
        assertEquals("deleted-test-original", loaded.testReferences!!.mainDriveFileId)
        assertEquals(json, remote.entries.getValue(id).bytes.toString(Charsets.UTF_8))
        assertEquals(before, remote.inventory(config.rootFolderId))
    }

    @Test fun bothMissingDiagnosticFilesAllowRepeatedInitializationWithoutReplacementFolders() = runBlocking {
        val config = initialize()
        installDiagnosticConfig(config, "deleted-test-original", "deleted-test-meta")
        val before = remote.inventory(config.rootFolderId)
        repeat(2) { assertTrue(drive.initializeDriveStorage(token) is DriveInitializationResult.SuccessLoadedExisting) }
        assertEquals(before, remote.inventory(config.rootFolderId))
    }

    @Test fun diagnosticMetadataMissingAloneDoesNotBlockConfig() = runBlocking {
        val config = initialize()
        remote.put("old-test-original", "test.pdf", config.receiptsFolderId, mime = "application/pdf")
        installDiagnosticConfig(config, "old-test-original", "deleted-test-meta")
        assertTrue(drive.initializeDriveStorage(token) is DriveInitializationResult.SuccessLoadedExisting)
    }

    @Test fun liveForeignDiagnosticReferenceStillBlocksWithoutReadingForeignContent() = runBlocking {
        val config = initialize()
        installDiagnosticConfig(config, "foreign-original", "deleted-test-meta")
        assertTrue(drive.initializeDriveStorage(token) is DriveInitializationResult.Failure)
        assertNoProductContentOrMutation()
    }

    @Test fun diagnosticReferenceWithMissingAncestorStillBlocks() = runBlocking {
        val config = initialize()
        remote.put("orphan-test", "test.pdf", "missing-parent", mime = "application/pdf")
        installDiagnosticConfig(config, "orphan-test", "deleted-test-meta")
        assertTrue(drive.initializeDriveStorage(token) is DriveInitializationResult.Failure)
    }

    @Test fun missingCurrentFolderStillBlocksEvenWithMissingDiagnosticFiles() = runBlocking {
        val config = initialize()
        installDiagnosticConfig(config, "deleted-test-original", "deleted-test-meta")
        remote.entries.remove(config.backupsFolderId)
        val before = remote.inventory(config.rootFolderId)
        assertTrue(drive.initializeDriveStorage(token) is DriveInitializationResult.Failure)
        assertEquals(before, remote.inventory(config.rootFolderId))
    }

    @Test fun diagnosticFieldNameInOrdinaryReceiptDoesNotAllowMissingCurrentReference() = runBlocking {
        val config = initialize()
        val json = JSONObject().put("testReferences", JSONObject().put("mainDriveFileId", "missing-current-original")).toString()
        assertFalse(GoogleDriveClient.uploadJson(token, config.systemFolderId, "receipt.json", json).success)
        val (_, configJson) = installDiagnosticConfig(config, "deleted-test-original", "deleted-test-meta")
        val poisoned = JSONObject(configJson).put("entries", JSONArray().put(JSONObject().put("mainDriveFileId", "missing-current-original")))
        assertFalse(GoogleDriveClient.uploadJson(token, config.systemFolderId, "poisoned.json", poisoned.toString()).success)
    }

    @Test fun optionalDiagnosticReferencesDoNotHideHttpFailuresOrNetworkCancellation() = runBlocking {
        val config = initialize()
        val (_, json) = installDiagnosticConfig(config, "diagnostic-unavailable", "deleted-test-meta")
        val policy = DriveNamespacePolicy(com.example.BuildConfig.APPLICATION_ID)
        for (code in listOf(401, 403, 503)) {
            val raw = OkHttpClient.Builder().addInterceptor { chain ->
                if (chain.request().url.encodedPath.endsWith("/diagnostic-unavailable"))
                    okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(code).message("Synthetic").body("{}".toResponseBody()).build()
                else chain.proceed(chain.request())
            }.addInterceptor(remote).build()
            val failure = assertThrows(DriveAccessFailure::class.java) {
                policy.validateJsonReferences(json, token) { raw.newCall(it).execute() }
            }
            assertEquals(code, failure.httpStatus)
        }
        val raw = OkHttpClient.Builder().addInterceptor { chain ->
            if (chain.request().url.encodedPath.endsWith("/diagnostic-unavailable")) throw java.net.SocketTimeoutException("synthetic")
            chain.proceed(chain.request())
        }.addInterceptor(remote).build()
        assertThrows(java.net.SocketTimeoutException::class.java) {
            policy.validateJsonReferences(json, token) { raw.newCall(it).execute() }
        }
        assertThrows(CancellationException::class.java) {
            policy.validateJsonReferences(json, token) { throw CancellationException("synthetic") }
        }
        Unit
    }

    @Test fun diagnosticReferenceCannotNameFolderOrMakeNestedMissingReferencesOptional() = runBlocking {
        val config = initialize()
        installDiagnosticConfig(config, config.receiptsFolderId, "deleted-test-meta")
        assertTrue(drive.initializeDriveStorage(token) is DriveInitializationResult.Failure)
        val (_, json) = installDiagnosticConfig(config, "deleted-test-original", "deleted-test-meta")
        val nested = JSONObject(json)
        nested.getJSONObject("testReferences").put("nested", JSONObject().put("metadataFileId", "missing-current-metadata"))
        assertFalse(GoogleDriveClient.uploadJson(token, config.systemFolderId, "nested.json", nested.toString()).success)
    }

    @Test fun unreadableCurrentIndexReferenceCannotAdvertiseZeroReceiptRestore() = runBlocking {
        val config = initialize()
        remote.put("damaged-index", "receipt-index.json", config.systemFolderId,
            """{"entries":[{"internalId":"keep-receipt","metadataFileId":"missing-current-metadata","mainDriveFileId":"missing-current-original"}]}""", "receiptIndex")
        val before = remote.inventory(config.rootFolderId)
        remote.requests.clear()
        val preview = drive.checkDriveInventoryForRestore(token, config)
        assertFalse("An unverified index is not a successful inventory", preview.success)
        assertFalse("Property metadata alone must not authorize incomplete restore", preview.inventory.canRestore)
        assertTrue(preview.inventory.issues.any { it.contains("receipt-index.json") })
        assertEquals(before, remote.inventory(config.rootFolderId))
        assertTrue(remote.requests.all { it.method == "GET" })
    }

    @Test fun malformedIndexArrayCannotBeDeclaredEmptyOrRestored() = runBlocking {
        val config = initialize()
        remote.put("malformed-index", "receipt-index.json", config.systemFolderId,
            """{"entries":"not-an-array"}""", "receiptIndex")
        val preview = drive.checkDriveInventoryForRestore(token, config)
        assertFalse(preview.success)
        assertFalse(preview.inventory.canRestore)
        val report = drive.executeFullDriveRestore(token, config, RestoreMode.REPLACE_FULL)
        assertFalse("Malformed backup must fail restore preflight", report.isSuccess)
    }

    @Test fun verifiedEmptyIndexAndPropertyBackupRemainRestorable() = runBlocking {
        val config = initialize()
        remote.put("empty-index", "receipt-index.json", config.systemFolderId, """{"entries":[]}""", "receiptIndex")
        val preview = drive.checkDriveInventoryForRestore(token, config)
        assertTrue(preview.toString(), preview.success)
        assertTrue(preview.inventory.propertyFound)
        assertEquals(0, preview.inventory.receiptCountInIndex)
        assertTrue(preview.inventory.canRestore)
    }

    @Test fun failedIndexSearchCannotBeTreatedAsAbsentEmptyBackup() = runBlocking {
        val config = initialize()
        transport.set(null, OkHttpClient.Builder().addInterceptor { chain ->
            if (chain.request().url.queryParameter("q").orEmpty().contains("receiptIndex"))
                okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(503).message("Synthetic unavailable").body("{}".toResponseBody()).build()
            else chain.proceed(chain.request())
        }.addInterceptor(remote).build())
        val preview = drive.checkDriveInventoryForRestore(token, config)
        assertFalse(preview.success)
        assertFalse(preview.inventory.canRestore)
        val before = repository.allReceipts.first()
        val report = drive.executeFullDriveRestore(token, config, RestoreMode.REPLACE_FULL)
        assertFalse("A failed search must block backend restore as well", report.isSuccess)
        assertEquals(before, repository.allReceipts.first())
    }

    @Test fun malformedIndexBlocksReplaceFullBeforeLocalDataChanges() = runBlocking {
        val config = initialize()
        val local = Receipt(aussteller = "Synthetic preserved original", datum = "2026-10-31", uhrzeit = "",
            bruttobetrag = 1.0, hauptkategorie = "Test", unterkategorie = "Test", kontoNr = "4800", beschreibung = "",
            internalId = "synthetic-preserve-local")
        repository.insert(local)
        val before = repository.allReceipts.first()
        remote.put("malformed-index", "receipt-index.json", config.systemFolderId,
            """{"entries":"not-an-array"}""", "receiptIndex")
        val report = drive.executeFullDriveRestore(token, config, RestoreMode.REPLACE_FULL)
        assertFalse(report.isSuccess)
        assertEquals(before, repository.allReceipts.first())
    }

    private fun installRecoveryReceipt(config: DriveAppConfig, original: String = "missing-original") {
        remote.put("recovery-meta", "recovery.json", config.systemFolderId,
            JSONObject().put("internalId", "recovery-receipt").put("displayId", "RECOVERY-1")
                .put("driveFileId", original).put("driveFolderId", config.receiptsFolderId)
                .put("bruttobetragCent", 12345).put("aussteller", "Synthetic retained vendor")
                .put("datum", "2026-01-01").put("hauptkategorie", "Instandhaltung")
                .put("createdAt", "2026-01-01T00:00:00").put("updatedAt", "2026-01-01T00:00:00")
                .put("originalDocuments", JSONArray().put(JSONObject()
                    .put("documentId", "receipt:recovery-receipt").put("originalFilename", "retained-original.jpg")
                    .put("storedFilename", "retained-original.jpg").put("mimeType", "image/jpeg")
                    .put("driveFileId", original).put("driveFolderId", config.receiptsFolderId)
                    .put("sha256", "a".repeat(64)).put("fileSizeBytes", 4096)
                    .put("createdAt", "2026-01-01T00:00:00").put("updatedAt", "2026-01-01T00:00:00")))
                .toString())
        remote.put("recovery-index", "receipt-index.json", config.systemFolderId,
            JSONObject().put("version", 1).put("entries", JSONArray()
                .put(JSONObject().put("internalId", "deleted-receipt").put("metadataFileId", "deleted-meta")
                    .put("mainDriveFileId", "deleted-original").put("syncStatus", "DELETED"))
                .put(JSONObject().put("internalId", "recovery-receipt").put("displayId", "RECOVERY-1")
                    .put("metadataFileId", "recovery-meta").put("mainDriveFileId", original)
                    .put("syncStatus", "SYNCED"))).toString(), "receiptIndex")
    }

    @Test fun restoreRetainsActiveReceiptWithMissingOriginalAndSkipsDeletedHistory() = runBlocking {
        val config = initialize(); installRecoveryReceipt(config)
        val before = remote.inventory(config.rootFolderId)
        remote.requests.clear()
        val preview = drive.checkDriveInventoryForRestore(token, config)
        assertTrue(preview.toString(), preview.success)
        assertTrue(preview.inventory.canRestore)
        assertEquals(1, preview.inventory.receiptCountInIndex)
        assertEquals(1, preview.inventory.metadataFileCount)
        assertEquals(0, preview.inventory.mainDocCount)
        assertEquals(1, preview.inventory.unavailableOriginalCount)
        assertTrue(preview.inventory.issues.any { it.contains("HTTP 404") })
        assertEquals(before, remote.inventory(config.rootFolderId))
        assertTrue(remote.requests.all { it.method == "GET" })
        repeat(2) {
            val report = drive.executeFullDriveRestore(token, config, RestoreMode.MERGE)
            assertTrue(report.errors.toString(), report.isSuccess)
            assertTrue(report.errors.any { it.contains("HTTP 404") })
            val local = repository.getReceiptByInternalId("recovery-receipt")!!
            assertEquals("missing-original", local.driveFileId)
            assertEquals(123.45, local.bruttobetrag, 0.001)
            assertEquals("recovery-meta", local.driveMetadataFileId)
            assertEquals(1, repository.allReceipts.first().size)
            val document = repository.getManagedDocument("receipt:recovery-receipt")!!
            assertEquals("missing-original", document.driveFileId)
            assertEquals("a".repeat(64), document.sha256)
            assertEquals(4096L, document.fileSizeBytes)
            assertNull(repository.getReceiptByInternalId("deleted-receipt"))
        }
        assertEquals(before, remote.inventory(config.rootFolderId).filterKeys { it in before })
        assertFalse(remote.requests.any { it.url.encodedPath.endsWith("/missing-original") && it.url.queryParameter("alt") == "media" })
        assertFalse(GoogleDriveClient.uploadJson(token, config.systemFolderId, "must-stay-strict.json",
            """{"driveFileId":"missing-original"}""").success)
    }

    @Test fun recoveryNeverTreatsForeignContentOrMissingAncestorAsUnavailableOriginal() = runBlocking {
        val config = initialize()
        for (original in listOf("foreign-original", "orphan-original")) {
            remote.put("orphan-original", "orphan.pdf", "missing-parent", mime = "application/pdf")
            installRecoveryReceipt(config, original)
            val before = repository.allReceipts.first()
            val preview = drive.checkDriveInventoryForRestore(token, config)
            assertFalse(preview.success)
            val report = drive.executeFullDriveRestore(token, config, RestoreMode.REPLACE_FULL)
            assertFalse(report.isSuccess)
            assertEquals(before, repository.allReceipts.first())
        }
        assertNoProductContentOrMutation()
    }

    @Test fun recoveryStillRequiresActiveMetadataAndReadableIndex() = runBlocking {
        val config = initialize(); installRecoveryReceipt(config)
        remote.entries.remove("recovery-meta")
        assertFalse(drive.checkDriveInventoryForRestore(token, config).success)
        val report = drive.executeFullDriveRestore(token, config, RestoreMode.REPLACE_FULL)
        assertFalse(report.isSuccess)
        assertTrue(repository.allReceipts.first().isEmpty())
    }

    @Test fun recoveryDoesNotDowngradeAuthorizationOrNetworkFailuresToMissingOriginalWarnings() = runBlocking {
        val config = initialize(); installRecoveryReceipt(config)
        for (code in listOf(401, 403, 503)) {
            transport.set(null, OkHttpClient.Builder().addInterceptor { chain ->
                if (chain.request().url.encodedPath.endsWith("/missing-original"))
                    okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(code).message("Synthetic").body("{}".toResponseBody()).build()
                else chain.proceed(chain.request())
            }.addInterceptor(remote).build())
            if (code == 401) {
                try { drive.checkDriveInventoryForRestore(token, config); fail("Authorization must propagate") }
                catch (expected: DriveAccessFailure) { assertEquals(401, expected.httpStatus) }
            } else assertFalse(drive.checkDriveInventoryForRestore(token, config).success)
        }
        transport.set(null, OkHttpClient.Builder().addInterceptor { chain ->
            if (chain.request().url.encodedPath.endsWith("/missing-original")) throw CancellationException("synthetic")
            chain.proceed(chain.request())
        }.addInterceptor(remote).build())
        try { drive.checkDriveInventoryForRestore(token, config); fail("Cancellation must propagate") }
        catch (_: CancellationException) { }
    }

    @Test fun supplementalRecoveryPreservesUnavailableOriginalAndReportsItBeforeImport() = runBlocking {
        val config = initialize()
        repository.upsertManagedDocument(ManagedDocument(documentId = "retained-standalone", propertyId = StableDocumentIdentity.LEGACY_PROPERTY_ID,
            originalFilename = "retained.jpg", storedFilename = "retained.jpg", mimeType = "image/jpeg",
            driveFileId = "missing-original", driveFolderId = config.receiptsFolderId,
            createdAt = "2026-01-01T00:00:00", updatedAt = "2026-01-01T00:00:00"))
        val payload = SupplementalDriveBackup.createPayload(context, db)
        remote.put("recovery-supplemental", "supplemental-backup.json", config.systemFolderId,
            payload.toString(), "supplementalBackup")
        val before = remote.inventory(config.rootFolderId)
        var unavailable = emptySet<String>()
        val read = SupplementalDriveBackup.loadValidatedPayload(token, config.systemFolderId) { unavailable = it }
        assertNotNull(read)
        assertEquals(setOf("missing-original"), unavailable)
        assertEquals("missing-original", read!!.getJSONArray("managedDocuments").getJSONObject(0).getString("driveFileId"))
        assertEquals(before, remote.inventory(config.rootFolderId))
        try { GoogleDriveClient.downloadJson(token, "recovery-supplemental"); fail("Ordinary JSON reads remain strict") }
        catch (expected: DriveAccessFailure) { assertEquals(404, expected.httpStatus) }
    }

}
