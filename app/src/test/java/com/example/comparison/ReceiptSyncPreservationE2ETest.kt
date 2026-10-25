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
class ReceiptSyncPreservationE2ETest {
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
    private val output get() = File("build/reports/e11a03-repair")
    private val originalPaths = linkedMapOf<String, File>()

    @Before fun setup() {
        // This class cannot be invoked as an installed-app seed. It runs solely in Robolectric.
        check(android.os.Build.FINGERPRINT == "robolectric")
        check(context.filesDir.canonicalPath.startsWith(File(System.getProperty("java.io.tmpdir")).canonicalPath))
        check(context.packageName == BuildConfig.APPLICATION_ID)
        assertEquals(if (isTest) "test" else "main", System.getProperty("comparison.variant", if (isTest) "test" else "main"))
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
        .put("source", "UNIT_TEST").put("commit", System.getenv("GITHUB_SHA") ?: "WORKTREE_UNCOMMITTED")
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
        val fixture = File(requireNotNull(javaClass.classLoader.getResource("phase11a/qa-transport-snapshot.zip")).toURI())
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

    private fun authored(document: ManagedDocument): String {
        val moshi = com.squareup.moshi.Moshi.Builder().addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
        val json = JSONObject(moshi.adapter(ManagedDocument::class.java).toJson(document))
        listOf("storedFilename", "mimeType", "localUri", "driveFileId", "driveFolderId", "sha256", "fileSizeBytes", "legacyDriveFolderId").forEach(json::remove)
        // Original-chain rank is technical metadata added by the existing attachment sync.
        // Its value is asserted separately; all authored extracted fields remain compared.
        val fields = JSONObject(document.extractedFieldsJson.ifBlank { "{}" })
        fields.remove("_receiptOriginalOrder")
        json.put("extractedFieldsJson", canonicalJson(fields))
        return canonicalJson(json)
    }

    @Test fun realOriginalUploadTwiceAndTwoMergesPreserveEveryAuthoredDocumentField() = runBlocking {
        loadOriginalPortfolio()
        val before = repo.getAllManagedDocuments().associateBy { it.documentId }
        val config = initialize()
        repeat(2) {
            for (r in repo.getAllReceiptsList()) {
                val success = drive.syncReceiptToDrive(token, config, r)
                assertEquals(r.internalId, r.imageUrl.isNotBlank(), success)
            }
            for (d in repo.getAllManagedDocuments()) assertEquals(d.documentId, authored(before.getValue(d.documentId)), authored(d))
            assertTrue(drive.saveStammdatenToDrive(token, config))
            assertTrue(SupplementalDriveBackup.backup(context, db, token, config.systemFolderId).success)
        }
        repeat(2) {
            assertTrue(drive.executeFullDriveRestore(token, config, RestoreMode.MERGE).isSuccess)
            assertTrue(SupplementalDriveBackup.restore(context, db, token, config.systemFolderId, restoreMode=RestoreMode.MERGE).success)
            for (d in repo.getAllManagedDocuments()) assertEquals(d.documentId, authored(before.getValue(d.documentId)), authored(d))
        }
        originalPaths.forEach { (id,f) ->
            assertEquals(before.getValue(id).sha256, StableDocumentIdentity.sha256(f.readBytes()))
            assertEquals(0, JSONObject(repo.getManagedDocument(id)!!.extractedFieldsJson).getInt("_receiptOriginalOrder"))
        }
        snapshot("preserved-after-two-merges")
        Unit
    }

    @Test fun actualReceiptUploadPreservesConfirmedReviewAndReferences() = runBlocking {
        loadOriginalPortfolio(); val config = initialize()
        val r = repo.getReceiptByInternalId("e10b-receipt-1")!!
        val d = repo.getManagedDocument("receipt:${r.internalId}")!!.copy(tenantReference="tenant",loanId=71,
            renovationReference="renovation",ocrText="reviewed text",aiConfidence=0.91)
        repo.upsertManagedDocument(d)
        val reviewed = ManagedDocumentService(context,repo).confirmReview(d,ManagedDocumentType.MIETVERTRAG,"2026-09-30",d.unitId,"{\"confirmed\":true}")
        repeat(2) {
            assertTrue(drive.syncReceiptToDrive(token,config,repo.getReceiptByInternalId(r.internalId)!!))
            assertEquals(authored(reviewed),authored(repo.getManagedDocument(d.documentId)!!))
        }
    }
    companion object {
        const val PRODUCT_ROOT = "Steuerassistent Belege"
        const val TEST_ROOT = "ImmoPilot Phase11A Testdaten"
    }
}
