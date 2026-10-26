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
import org.robolectric.annotation.Config

/** API-contract simulation only; no real OAuth or Google account is used. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
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
    private var blockedId: String? = null
    private var metadataStatus = 200
    private var systemCreationStatus = 200
    private var corruptMetadata = false
    private var incompleteSearch = false
    private var configSearchStatus = 200
    private var networkDown = false
    private var omitEmptyCollections = false
    private var collectionBody: String? = null
    private val token = "synthetic-issue145-account"
    private val folder = ComparisonDriveHttpFixture.FOLDER

    @Before fun setup() {
        assertEquals("Issue 145 device reports Android 16", 36, android.os.Build.VERSION.SDK_INT)
        previous = transport.get(null) as OkHttpClient
        remote = ComparisonDriveHttpFixture()
        transport.set(null, OkHttpClient.Builder().addInterceptor { chain ->
            val r = chain.request()
            if (networkDown) throw java.io.IOException("private-token-or-address-must-not-escape")
            if (collectionBody != null && r.method == "GET" && r.url.encodedPath == "/drive/v3/files")
                return@addInterceptor Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(200).message("Synthetic malformed collection")
                    .body(collectionBody!!.toResponseBody("application/json".toMediaType())).build()
            val q = r.url.queryParameter("q").orEmpty()
            val systemCreation = r.method == "POST" && r.body?.let {
                val b = okio.Buffer(); it.writeTo(b); b.readUtf8().contains("_BelegApp-Daten")
            } == true
            val status = when {
                r.url.encodedPath == "/drive/v3/files/root" -> rootStatus
                r.url.encodedPath == "/drive/v3/files/$blockedId" -> metadataStatus
                systemCreation -> systemCreationStatus
                r.method == "GET" && r.url.encodedPath == "/drive/v3/files" && q.contains("appConfig") -> configSearchStatus
                r.method == "GET" && r.url.encodedPath == "/drive/v3/files" -> searchStatus
                else -> 200
            }
            if ((corruptMetadata && r.url.encodedPath == "/drive/v3/files/$blockedId") ||
                (incompleteSearch && r.method == "GET" && r.url.encodedPath == "/drive/v3/files")) {
                val body = if (corruptMetadata) "{\"id\":\"$blockedId\"}" else "{\"files\":[],\"incompleteSearch\":true}"
                return@addInterceptor Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(200).message("Synthetic")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            }
            if (status == 200) {
                val response = chain.proceed(r)
                if (omitEmptyCollections && r.method == "GET" && r.url.encodedPath == "/drive/v3/files") {
                    val json = org.json.JSONObject(response.body!!.string())
                    if (json.optJSONArray("files")?.length() == 0) json.remove("files")
                    response.newBuilder().body(json.toString().toResponseBody("application/json".toMediaType())).build()
                } else response
            } else Response.Builder().request(r).protocol(Protocol.HTTP_1_1)
                .code(status).message("Synthetic failure").body("{}".toResponseBody("application/json".toMediaType())).build()
        }.addInterceptor(remote).build())
        GoogleDriveClient.clearFolderCache()
    }
    @After fun cleanup() {
        transport.set(null, previous); GoogleDriveClient.clearFolderCache()
        context.getSharedPreferences("category_folder_mappings_prefs", 0).edit().clear().commit()
    }
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
    @Test fun cachedOwnFolderHttpFailuresRemainDistinctAndNeverCreateDuplicates() = runBlocking {
        val c = config(drive.initializeDriveStorage(token))
        val before = remote.inventory(c.rootFolderId)
        blockedId = c.rootFolderId
        for (status in listOf(401,403,404,429,500)) {
            metadataStatus = status
            val r = drive.initializeDriveStorage(token)
            assertTrue(r.toString(), r is DriveInitializationResult.Failure)
            val error = (r as DriveInitializationResult.Failure).error
            assertTrue(error, error.contains("HTTP_$status"))
            assertFalse(error.contains(token)); assertFalse(error.contains(c.rootFolderId))
            assertEquals(before, remote.inventory(c.rootFolderId))
            assertEquals(1, ownRoots().size)
        }
        metadataStatus = 200
        assertEquals(c.rootFolderId, config(drive.initializeDriveStorage(token)).rootFolderId)
    }

    @Test fun disconnectedNetworkIsNotAnInvalidFolderAndDoesNotMutate() = runBlocking {
        networkDown = true
        val r = drive.initializeDriveStorage(token) as DriveInitializationResult.Failure
        assertTrue(r.error, r.error.contains("NETWORK"))
        assertFalse(r.error.contains("private-token"))
        assertEquals(0, ownRoots().size)
        networkDown = false
        config(drive.initializeDriveStorage(token))
        Unit
    }

    @Test fun incompleteSearchCannotCreateAnAlternativeRoot() = runBlocking {
        incompleteSearch = true
        val r = drive.initializeDriveStorage(token) as DriveInitializationResult.Failure
        assertTrue(r.error, r.error.contains("INCOMPLETE_SEARCH"))
        assertEquals(0, ownRoots().size)
    }

    @Test fun partialSystemCreationFailureCanResumeWithoutChangingExistingRoot() = runBlocking {
        systemCreationStatus = 503
        val r = drive.initializeDriveStorage(token) as DriveInitializationResult.Failure
        assertTrue(r.error, r.error.contains("HTTP_503"))
        assertEquals(1, ownRoots().size)
        val id = ownRoots().single().id
        systemCreationStatus = 200
        assertEquals(id, config(drive.initializeDriveStorage(token)).rootFolderId)
        assertEquals(1, remote.entries.values.count { it.parent == id && it.name == "_BelegApp-Daten" })
    }

    @Test fun configSearchPermissionFailurePreservesExistingBackupBytesAndIds() = runBlocking {
        val c = config(drive.initializeDriveStorage(token))
        val before = remote.inventory(c.rootFolderId)
        configSearchStatus = 403
        val r = drive.initializeDriveStorage(token) as DriveInitializationResult.Failure
        assertTrue(r.error, r.error.contains("HTTP_403"))
        assertEquals(before, remote.inventory(c.rootFolderId))
        configSearchStatus = 200
        assertEquals(c.rootFolderId, config(drive.initializeDriveStorage(token)).rootFolderId)
    }

    @Test fun malformedSuccessfulMetadataIsNotMistakenForAWrongMimeOrDeletedMapping() = runBlocking {
        val c = config(drive.initializeDriveStorage(token))
        blockedId = c.rootFolderId; corruptMetadata = true
        val r = drive.initializeDriveStorage(token) as DriveInitializationResult.Failure
        assertTrue(r.error, r.error.contains("INVALID_METADATA"))
        assertEquals(1, ownRoots().size)
    }

    @Test fun confirmedWrongMimeIsDistinctAndOnlyInvalidTestPreferenceMayBeReplaced() = runBlocking {
        val c = config(drive.initializeDriveStorage(token))
        remote.put("own-file-not-folder", "Keep.json", c.rootFolderId, "{\"keep\":true}")
        val prefs = context.getSharedPreferences("category_folder_mappings_prefs", 0)
        prefs.edit().putString("2026/Test", "own-file-not-folder").commit()
        val before = remote.entries.getValue("own-file-not-folder").bytes.copyOf()
        if (DriveEnvironment.isIsolatedTest) {
            val id = GoogleDriveClient.getOrCreateFolder(token,"Test",c.rootFolderId,"2026/Test",c.systemFolderId,context)!!
            assertNotEquals("own-file-not-folder",id)
            assertEquals(folder,remote.entries.getValue(id).mime)
            assertEquals(id,prefs.getString("2026/Test",null))
        } else {
            val e = assertThrows(DriveAccessFailure::class.java) { runBlocking {
                GoogleDriveClient.getOrCreateFolder(token,"Test",c.rootFolderId,"2026/Test",c.systemFolderId,context)
            } }
            assertEquals(DriveAccessFailure.Reason.WRONG_MIME,e.reason)
            assertEquals("own-file-not-folder",prefs.getString("2026/Test",null))
        }
        assertArrayEquals(before,remote.entries.getValue("own-file-not-folder").bytes)
    }

    @Test fun invalidIdAndInaccessibleMappingAreNeverConfused() = runBlocking {
        val c = config(drive.initializeDriveStorage(token))
        val prefs = context.getSharedPreferences("category_folder_mappings_prefs",0)
        prefs.edit().putString("missing", "not-accessible").commit()
        val e = assertThrows(DriveAccessFailure::class.java) { runBlocking {
            GoogleDriveClient.getOrCreateFolder(token,"Missing",c.rootFolderId,"missing",c.systemFolderId,context)
        } }
        assertEquals(404,e.httpStatus)
        assertEquals("not-accessible",prefs.getString("missing",null))
        val syntax = assertThrows(DriveAccessFailure::class.java) { runBlocking { GoogleDriveClient.getFileMetadataChecked(token,"bad/id") } }
        assertEquals(DriveAccessFailure.Reason.INVALID_ID,syntax.reason)
    }

    @Test fun rotatedAccountTokenDoesNotReuseCachedIdsFromAnotherAccount() = runBlocking {
        val first = config(drive.initializeDriveStorage(token))
        remote = ComparisonDriveHttpFixture()
        // The interceptor reads the current remote through a delegating client, including this new account.
        val secondRemote = remote
        transport.set(null, OkHttpClient.Builder().addInterceptor(secondRemote).build())
        GoogleDriveClient.getOrCreateFolder("other-synthetic-account")
        assertEquals(1, ownRoots().size)
        assertEquals(first.rootFolderId,ownRoots().single().id) // same synthetic ID in another account is not a cache proof.
        assertTrue(secondRemote.requests.any { it.method=="GET" && it.url.encodedPath=="/drive/v3/files" })
        assertTrue(secondRemote.requests.any { it.method=="POST" })
    }

    @Test fun successfulEmptyFileListMayOmitEmptyFilesCollection() = runBlocking {
        omitEmptyCollections = true
        rootStatus = 404
        val first = config(drive.initializeDriveStorage(token))
        rootStatus = 403
        val second = config(drive.initializeDriveStorage(token))
        assertEquals(first.rootFolderId, second.rootFolderId)
        assertEquals(first.systemFolderId, second.systemFolderId)
        assertEquals(1, ownRoots().size)
        assertTrue(remote.orphanIds().isEmpty())
    }

    @Test fun malformedSuccessfulCollectionsNeverBecomeEmptySearchResults() = runBlocking {
        for (body in listOf("{\"files\":\"wrong-type\"}", "{\"error\":{\"message\":\"private-sentinel\"}}", "{\"kind\":\"drive#file\",\"id\":\"unexpected-metadata\"}")) {
            collectionBody = body
            val r = drive.initializeDriveStorage(token) as DriveInitializationResult.Failure
            assertTrue(r.error, r.error.contains("INVALID_METADATA"))
            assertFalse(r.error.contains("private-sentinel"))
            assertEquals(0,ownRoots().size)
            assertTrue(remote.requests.none { it.method != "GET" })
        }
    }

}
