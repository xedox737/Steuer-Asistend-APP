package com.example.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.comparison.ComparisonDriveHttpFixture
import com.example.data.*
import com.example.ui.IsolatedAppDatabaseRule
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** API-contract simulation only; no real OAuth or Google account is used. */
@RunWith(RobolectricTestRunner::class)
class DriveInitializationRegressionTest {
    @get:Rule val isolation = IsolatedAppDatabaseRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val db get() = AppDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
    private val repository get() = ReceiptRepository(db.receiptDao(), db.propertyDao(), db.receiptEntityDao(), db.belegDao(),
        db.exportAuditDao(), db.receiptDocumentDao(), db.managedDocumentDao())
    private val drive get() = DrivePersistenceRepository(context, repository)
    private val transport = GoogleDriveClient::class.java.getDeclaredField("client").apply { isAccessible = true }
    private lateinit var previous: OkHttpClient
    private lateinit var remote: ComparisonDriveHttpFixture
    private var rootStatus = 200
    private var searchStatus = 200
    private val token = "synthetic-issue145-account"
    private val folder = ComparisonDriveHttpFixture.FOLDER

    @Before fun setup() {
        previous = transport.get(null) as OkHttpClient
        remote = ComparisonDriveHttpFixture()
        transport.set(null, OkHttpClient.Builder().addInterceptor { chain ->
            val r = chain.request()
            val status = if (r.url.encodedPath == "/drive/v3/files/root") rootStatus
                else if (r.method == "GET" && r.url.encodedPath == "/drive/v3/files") searchStatus else 200
            if (status == 200) chain.proceed(r) else Response.Builder().request(r).protocol(Protocol.HTTP_1_1)
                .code(status).message("Synthetic failure").body("{}".toResponseBody("application/json".toMediaType())).build()
        }.addInterceptor(remote).build())
        GoogleDriveClient.clearFolderCache()
    }
    @After fun cleanup() { transport.set(null, previous); GoogleDriveClient.clearFolderCache() }
    private fun config(r: DriveInitializationResult): DriveAppConfig = when(r) {
        is DriveInitializationResult.SuccessCreatedNew -> r.config
        is DriveInitializationResult.SuccessLoadedExisting -> r.config
        is DriveInitializationResult.Failure -> error(r.error)
    }
    private fun ownRoots() = remote.entries.values.filter { it.parent == remote.myDrive && it.name == DriveEnvironment.rootFolderName }

    @Test fun freshSetupDoesNotRequireAccessToGeneralMyDriveMetadata() = runBlocking {
        rootStatus = 404 // drive.file may expose app-created items without granting general root metadata.
        val c = config(drive.initializeDriveStorage(token))
        assertEquals(1, ownRoots().size)
        assertEquals("_BelegApp-Daten", remote.entries.getValue(c.systemFolderId).name)
        assertEquals(c.rootFolderId, remote.entries.getValue(c.systemFolderId).parent)
        assertTrue(remote.orphanIds().isEmpty())
    }

    @Test fun existingEmptyOwnRootIsCompletedWithoutTouchingForeignBackup() = runBlocking {
        rootStatus = 404
        remote.put("own-empty", DriveEnvironment.rootFolderName, remote.myDrive, mime = folder)
        val foreignName = if (DriveEnvironment.isIsolatedTest) DriveEnvironment.PRODUCTION_ROOT else DriveNamespacePolicy.TEST_ROOT
        remote.put("foreign-root", foreignName, remote.myDrive, mime = folder)
        remote.put("foreign-backup", "Keep.json", "foreign-root", "{\"sentinel\":\"unchanged\"}")
        val before = remote.inventory("foreign-root")
        val first = config(drive.initializeDriveStorage(token))
        val second = config(drive.initializeDriveStorage(token))
        assertEquals("own-empty", first.rootFolderId)
        assertEquals(first.systemFolderId, second.systemFolderId)
        assertEquals(1, ownRoots().size)
        assertEquals(before, remote.inventory("foreign-root"))
        assertFalse(remote.requests.any { it.url.encodedPath.endsWith("/foreign-backup") })
    }

    @Test fun authorizationFailureDuringFolderSearchMustNotCreateReplacementFolders() = runBlocking {
        searchStatus = 403
        val result = drive.initializeDriveStorage(token)
        assertTrue(result.toString(), result is DriveInitializationResult.Failure)
        assertTrue((result as DriveInitializationResult.Failure).error, result.error.contains("HTTP_403"))
        assertEquals(0, ownRoots().size)
        assertTrue(remote.requests.none { it.method != "GET" })
    }

    @Test fun reachableExistingSetupAndCacheRemainIdempotent() = runBlocking {
        val first = config(drive.initializeDriveStorage(token))
        val second = config(drive.initializeDriveStorage(token))
        assertEquals(first.rootFolderId, second.rootFolderId)
        assertEquals(first.systemFolderId, second.systemFolderId)
        assertEquals(1, ownRoots().size)
        assertTrue(GoogleDriveClient.getFileMetadata(token, first.rootFolderId)?.mimeType == folder)
    }
}
