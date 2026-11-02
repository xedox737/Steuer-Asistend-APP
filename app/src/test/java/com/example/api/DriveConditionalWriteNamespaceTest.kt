package com.example.api

import com.example.comparison.ComparisonDriveHttpFixture
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Synthetic account only. The v2 compatibility path must retain the v3 ownership fence. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DriveConditionalWriteNamespaceTest {
    private val policy = DriveNamespacePolicy("com.aistudio.steuerassistent.vnhqkp")
    private lateinit var remote: ComparisonDriveHttpFixture
    private lateinit var raw: OkHttpClient
    private val authorization = "Bearer synthetic-v2-namespace"
    private val projection = "fields=id,etag,version,fileSize"

    @Before fun setup() {
        remote = ComparisonDriveHttpFixture()
        raw = OkHttpClient.Builder().addInterceptor(remote).build()
        remote.put("own-root", DriveEnvironment.PRODUCTION_ROOT, remote.myDrive, mime = ComparisonDriveHttpFixture.FOLDER)
        remote.put("own-system", "_BelegApp-Daten", "own-root", mime = ComparisonDriveHttpFixture.FOLDER)
        remote.put("own-receipts", "receipts", "own-system", mime = ComparisonDriveHttpFixture.FOLDER)
        remote.put("own-file", "original.pdf", "own-receipts", "original bytes", mime = "application/pdf")
        remote.put("foreign-root", DriveNamespacePolicy.TEST_ROOT, remote.myDrive, mime = ComparisonDriveHttpFixture.FOLDER)
        remote.put("foreign-folder", "receipts", "foreign-root", mime = ComparisonDriveHttpFixture.FOLDER)
        remote.put("foreign-file", "foreign.pdf", "foreign-folder", "foreign bytes", mime = "application/pdf")
    }

    private fun get(id: String) = Request.Builder()
        .url("https://www.googleapis.com/drive/v2/files/$id?$projection")
        .header("Authorization", authorization).build()

    private fun put(id: String, etag: String? = "\"revision-1\"") = Request.Builder()
        .url("https://www.googleapis.com/upload/drive/v2/files/$id?uploadType=media")
        .header("Authorization", authorization)
        .apply { etag?.let { header("If-Match", it) } }
        .put("changed bytes".toRequestBody("application/pdf".toMediaType())).build()

    private fun execute(request: Request) = policy.execute(request) { raw.newCall(it).execute() }

    private fun assertDeniedWithoutV2Access(request: Request) {
        val before = remote.entries.mapValues { (_, entry) -> entry.bytes.copyOf() }
        remote.requests.clear()
        assertThrows(DriveAccessFailure::class.java) { execute(request).close() }
        assertFalse(remote.requests.any { it.url.encodedPath.contains("/drive/v2/") })
        assertTrue(remote.requests.all { it.method == "GET" })
        assertEquals(before.keys, remote.entries.keys)
        before.forEach { (id, bytes) -> assertArrayEquals(bytes, remote.entries.getValue(id).bytes) }
    }

    @Test fun ownedV2ProjectionAndConditionalMediaPutUseFreshV3OwnershipProofs() {
        execute(get("own-file")).use {
            assertEquals(200, it.code)
            assertEquals("\"revision-1\"", JSONObject(it.body!!.string()).getString("etag"))
        }
        remote.requests.clear()
        execute(put("own-file")).use { assertEquals(200, it.code) }
        assertEquals("changed bytes", remote.entries.getValue("own-file").bytes.toString(Charsets.UTF_8))
        assertTrue(remote.requests.any { it.method == "GET" && it.url.encodedPath == "/drive/v3/files/own-file" })
        assertTrue(remote.requests.any { it.method == "GET" && it.url.encodedPath == "/drive/v3/files/own-root" })
        assertEquals(1, remote.requests.count { it.method == "PUT" })
        assertEquals("foreign bytes", remote.entries.getValue("foreign-file").bytes.toString(Charsets.UTF_8))
    }

    @Test fun foreignV2ProjectionAndMediaPutAreDeniedBeforeCompatibilityEndpoint() {
        assertDeniedWithoutV2Access(get("foreign-file"))
        assertDeniedWithoutV2Access(put("foreign-file"))
    }

    @Test fun rootSystemAndReceiptContainersCannotBeOverwrittenThroughV2() {
        for (id in listOf("own-root", "own-system", "own-receipts"))
            assertDeniedWithoutV2Access(put(id))
    }

    @Test fun v2MediaPutRequiresOneStrongNonWildcardIfMatch() {
        for (etag in listOf(null, "", "*", "W/\"revision-1\"", "revision-1", "\"revision-1\", \"revision-2\""))
            assertDeniedWithoutV2Access(put("own-file", etag))
    }

    @Test fun compatibilityPathDoesNotEnableV2ListsCreatesDeletesMediaReadsOrMetadataWrites() {
        val invalid = listOf(
            get("own-file").newBuilder().url("https://www.googleapis.com/drive/v2/files?q=anything").build(),
            get("own-file").newBuilder().url("https://www.googleapis.com/drive/v2/files/own-file?alt=media").build(),
            put("own-file").newBuilder().url("https://www.googleapis.com/drive/v2/files/own-file").build(),
            put("own-file").newBuilder().url("https://www.googleapis.com/upload/drive/v2/files?uploadType=media").post("{}".toRequestBody()).build(),
            get("own-file").newBuilder().delete().build(),
            put("own-file").newBuilder().patch("{}".toRequestBody()).build(),
            put("own-file").newBuilder().url("https://www.googleapis.com/upload/drive/v2/files/own-file?uploadType=media&addParents=own-root").build()
        )
        invalid.forEach(::assertDeniedWithoutV2Access)
    }

    @Test fun previouslyObservedFileMovedToForeignTreeCannotUseV2Write() {
        val proof = DriveReadProof(authorization)
        policy.execute(get("own-file"), { raw.newCall(it).execute() }, proof).close()
        remote.entries.getValue("own-file").parent = "foreign-folder"
        remote.requests.clear()
        assertThrows(DriveAccessFailure::class.java) {
            policy.execute(put("own-file"), { raw.newCall(it).execute() }, proof).close()
        }
        assertFalse(remote.requests.any { it.url.encodedPath.contains("/drive/v2/") })
        assertEquals("original bytes", remote.entries.getValue("own-file").bytes.toString(Charsets.UTF_8))
    }

    @Test fun missingAncestorAndWrongHostCannotReachV2CompatibilityEndpoint() {
        remote.entries.remove("own-receipts")
        assertDeniedWithoutV2Access(get("own-file"))
        assertDeniedWithoutV2Access(put("own-file"))
        assertDeniedWithoutV2Access(get("own-file").newBuilder().url("https://unrelated.invalid/drive/v2/files/own-file?$projection").build())
    }
}
