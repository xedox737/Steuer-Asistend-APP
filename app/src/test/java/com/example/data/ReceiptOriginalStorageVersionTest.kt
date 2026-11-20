package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReceiptOriginalStorageVersionTest {
    private lateinit var context: Context
    private lateinit var directory: File
    private lateinit var original: File
    private lateinit var storage: ReceiptOriginalStorage
    private val bytes = "unchanged original text".toByteArray()

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        directory = File(context.filesDir, "managed_documents").apply { mkdirs() }
        original = File(directory, "document.txt").apply { writeBytes(bytes) }
        storage = ReceiptOriginalStorage(context)
    }

    @After fun tearDown() { directory.deleteRecursively() }

    private fun document() = ManagedDocument(
        documentId = "document", propertyId = "property", unitId = "unit",
        originalFilename = "Original.txt", storedFilename = "Beleg.txt", mimeType = "text/plain",
        localUri = original.absolutePath, sha256 = StableDocumentIdentity.sha256(bytes),
        fileSizeBytes = bytes.size.toLong(), driveFileId = "drive", receiptInternalId = "receipt"
    )

    @Test fun downloadPublishesSeparateCompleteVersionAndPreservesIdentityAndPreviousBytes() {
        val before = document()
        val downloaded = storage.storeDownloadedOriginal(before, bytes)
        assertNotEquals(before.localUri, downloaded.localUri)
        assertEquals(before.copy(localUri = downloaded.localUri, updatedAt = downloaded.updatedAt), downloaded)
        assertArrayEquals(bytes, original.readBytes())
        assertArrayEquals(bytes, File(downloaded.localUri).readBytes())
        assertEquals(2, directory.listFiles()!!.size)
    }

    @Test fun repeatedDownloadKeepsEveryPublishedVersionReadable() {
        val first = storage.storeDownloadedOriginal(document(), bytes)
        val second = storage.storeDownloadedOriginal(first, bytes)
        assertNotEquals(first.localUri, second.localUri)
        listOf(original, File(first.localUri), File(second.localUri)).forEach {
            assertArrayEquals(bytes, it.readBytes())
        }
        assertEquals(3, directory.listFiles()!!.size)
    }

    @Test fun corruptOrIncompleteDownloadsDoNotPublishOrTouchPreviousVersion() {
        val before = document()
        listOf(
            before to "wrong content".toByteArray(),
            before.copy(fileSizeBytes = bytes.size + 1L) to bytes,
            before.copy(sha256 = "", fileSizeBytes = 0) to byteArrayOf()
        ).forEach { (metadata, content) ->
            assertTrue(runCatching { storage.storeDownloadedOriginal(metadata, content) }.isFailure)
            assertArrayEquals(bytes, original.readBytes())
            assertEquals(listOf(original.name), directory.listFiles()!!.map { it.name })
        }
    }

    @Test fun legacyDownloadRecordsContentProofAndCannotEscapeStorageThroughFilename() {
        val downloaded = storage.storeDownloadedOriginal(document().copy(
            storedFilename = "original.../../../outside", sha256 = "", fileSizeBytes = 0
        ), bytes)
        val file = File(downloaded.localUri)
        assertEquals(directory.canonicalFile, file.canonicalFile.parentFile)
        assertEquals("bin", file.extension)
        assertEquals(StableDocumentIdentity.sha256(bytes), downloaded.sha256)
        assertEquals(bytes.size.toLong(), downloaded.fileSizeBytes)
        assertArrayEquals(bytes, file.readBytes())
        assertArrayEquals(bytes, original.readBytes())
    }

    @Test fun unavailableStorageLeavesExistingBytesUntouched() {
        val blocked = File(context.filesDir, "managed_documents")
        original.delete()
        check(blocked.delete())
        blocked.writeText("unavailable storage")
        try {
            assertTrue(runCatching { storage.storeDownloadedOriginal(document(), bytes) }.isFailure)
            assertEquals("unavailable storage", blocked.readText())
        } finally { blocked.delete() }
    }
}
