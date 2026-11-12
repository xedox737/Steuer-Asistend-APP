package com.example.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineBackupArchiveValidatorTest {
    private lateinit var root: File
    private lateinit var context: Context
    private lateinit var prior: File
    private val payload = "synthetic payload CRC marker".toByteArray()
    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, "validator-${UUID.randomUUID()}").apply { check(mkdirs()) }
        context = object : ContextWrapper(base) { override fun getFilesDir() = File(root, "files").apply { check(isDirectory || mkdirs()) } }
        prior = File(context.filesDir, "offline-backup-staging/prior.immopilot").apply { parentFile!!.mkdirs(); writeText("prior valid archive") }
    }
    @After fun cleanup() { root.deleteRecursively() }
    private fun regular() = linkedMapOf("manifest.json" to "{}".toByteArray(), "metadata.json" to "{}".toByteArray(), "files/0" to payload)
    private fun zip(entries: Map<String, ByteArray>, stored: Boolean = false): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip -> entries.forEach { (name, bytes) ->
            val entry = ZipEntry(name)
            if (stored) {
                entry.method = ZipEntry.STORED; entry.size = bytes.size.toLong(); entry.compressedSize = bytes.size.toLong()
                entry.crc = CRC32().apply { update(bytes) }.value
            }
            zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry()
        } }
    }.toByteArray()
    private fun source(bytes: ByteArray) = File(root, "input.zip").apply { writeBytes(bytes) }
    private fun noOwnedDirectoryLeft() {
        assertEquals("prior valid archive", prior.readText())
        assertTrue(prior.parentFile!!.listFiles()!!.none { it.isDirectory && it.name.startsWith("verify-") })
    }
    private fun replaceName(bytes: ByteArray, from: String, to: String, centralOnly: Boolean = false): ByteArray {
        require(from.length == to.length)
        val needle = from.toByteArray(); val positions = bytes.indices.filter { index ->
            index + needle.size <= bytes.size && bytes.copyOfRange(index, index + needle.size).contentEquals(needle) }
        assertEquals(2, positions.size)
        return bytes.copyOf().apply { (if (centralOnly) positions.takeLast(1) else positions).forEach { to.toByteArray().copyInto(this, it) } }
    }

    @Test fun stagesOnlyKnownRegularEntriesAndComputesActualSizeAndHash() = runBlocking<Unit> {
        val input = source(zip(regular()))
        OfflineBackupArchiveValidator.stageZip(context, input).use { staged ->
            assertEquals(regular().keys, staged.parts.keys)
            val part = staged.parts.getValue("files/0")
            assertEquals(payload.size.toLong(), part.bytes); assertArrayEquals(payload, part.file!!.readBytes())
            val hash = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            assertEquals(hash, part.sha256)
        }
        assertTrue(input.exists()); noOwnedDirectoryLeft()
    }

    @Test fun untrustedCredentialEntryNeverTouchesDiskAndIsWipedOnClose() = runBlocking<Unit> {
        val secret = "synthetic credential must stay in memory".toByteArray()
        val entries = regular().apply { put("credentials.bin", secret) }
        val staged = OfflineBackupArchiveValidator.stageZip(context, source(zip(entries)))
        val credential = staged.parts.getValue("credentials.bin")
        assertNull(credential.file); assertArrayEquals(secret, credential.inMemory)
        staged.parts.values.mapNotNull { it.file }.forEach { file -> assertFalse(file.readBytes().contentEquals(secret)) }
        staged.close(); assertTrue(credential.inMemory!!.all { it == 0.toByte() }); noOwnedDirectoryLeft()
    }

    @Test fun traversalDirectoriesUnknownDuplicateAndMismatchedCentralEntriesAreRejected() = runBlocking<Unit> {
        val variants = listOf(
            zip(regular().apply { put("../escape", payload) }), zip(regular().apply { put("files/", byteArrayOf()) }),
            zip(regular().apply { put("files/01", payload) }), zip(regular().apply { put("files/10000", payload) }),
            zip(regular().apply { remove("metadata.json") }),
            replaceName(zip(regular().apply { put("files/1", payload) }), "files/1", "files/0"),
            replaceName(zip(regular().apply { put("files/1", payload) }), "files/1", "files/2", centralOnly = true)
        )
        variants.forEach { bytes ->
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineBackupArchiveValidator.stageZip(context, source(bytes))
            } }
            noOwnedDirectoryLeft()
        }
        assertFalse(File(root, "escape").exists())
    }

    @Test fun crcFailureAndTruncatedZipLeaveNoStagingAndPreserveOldArchive() = runBlocking<Unit> {
        val original = zip(regular(), stored = true)
        val index = original.indices.first { i -> i + payload.size <= original.size && original.copyOfRange(i, i + payload.size).contentEquals(payload) }
        val damaged = original.copyOf().apply { this[index] = (this[index].toInt() xor 1).toByte() }
        listOf(damaged, original.copyOf(original.size - 12)).forEach { bytes ->
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineBackupArchiveValidator.stageZip(context, source(bytes))
            } }
            noOwnedDirectoryLeft()
        }
    }

    @Test fun totalMetadataPayloadAndEntryLimitsRejectWithoutLeavingOwnOutput() = runBlocking<Unit> {
        val limits = listOf(OfflineBackupArchiveValidator.Limits(bytes = 3), OfflineBackupArchiveValidator.Limits(metadataBytes = 3),
            OfflineBackupArchiveValidator.Limits(payloadBytes = 1), OfflineBackupArchiveValidator.Limits(entries = 2))
        limits.forEach { limit ->
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineBackupArchiveValidator.stageZip(context, source(zip(regular())), limit)
            } }
            noOwnedDirectoryLeft()
        }
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
            OfflineBackupArchiveValidator.stageZip(context, source(zip(regular().apply { put("files/0", ByteArray(128 * 1024)) })),
                OfflineBackupArchiveValidator.Limits(payloadBytes = 1024))
        } }
        noOwnedDirectoryLeft()
    }

    @Test fun cancellationRemainsCancellationAndNeverLeavesOwnedStaging() {
        val input = source(zip(regular()))
        assertThrows(kotlinx.coroutines.CancellationException::class.java) { runBlocking {
            coroutineContext[kotlinx.coroutines.Job]!!.cancel()
            OfflineBackupArchiveValidator.stageZip(context, input)
        } }
        noOwnedDirectoryLeft()
    }
}
