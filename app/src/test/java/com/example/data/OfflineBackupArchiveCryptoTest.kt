package com.example.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OfflineBackupArchiveCryptoTest {
    private fun password() = "synthetic-recovery-password".toCharArray()
    private val plaintext = ByteArray(1024 * 1024 + 123) { (it * 17).toByte() }
    private suspend fun archive(): ByteArray = ByteArrayOutputStream().also { output ->
        OfflineBackupKeyEnvelope.create(password()).use { generation ->
            OfflineBackupArchiveCrypto.encrypt(generation, output) { it.write(plaintext) }
        }
    }.toByteArray()
    private fun directory() = Files.createTempDirectory("immopilot-crypto-test-").toFile()

    @Test fun fullEnvelopeAndStreamingRoundTripCompletesBeforeReturningPlaintextFile() = runBlocking {
        val directory = directory()
        try {
            val output = File(directory, "private.zip")
            val input = password()
            OfflineBackupArchiveCrypto.decryptToPrivateStaging(ByteArrayInputStream(archive()), input, output)
            assertTrue(input.all { it == '\u0000' })
            assertArrayEquals(plaintext, output.readBytes())
        } finally { directory.deleteRecursively() }
    }
    @Test fun wrongPasswordAndTruncatedFinalSegmentNeverLeaveAcceptedStaging() = runBlocking {
        val directory = directory()
        try {
            val encrypted = archive()
            val wrong = "wrong-synthetic-password".toCharArray()
            val wrongOutput = File(directory, "wrong.zip")
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineBackupArchiveCrypto.decryptToPrivateStaging(ByteArrayInputStream(encrypted), wrong, wrongOutput)
            } }
            assertTrue(wrong.all { it == '\u0000' })
            assertFalse(wrongOutput.exists())
            val truncated = File(directory, "truncated.zip")
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineBackupArchiveCrypto.decryptToPrivateStaging(ByteArrayInputStream(encrypted.copyOf(encrypted.size - 1)), password(), truncated)
            } }
            assertFalse(truncated.exists())
        } finally { directory.deleteRecursively() }
    }
    @Test fun changedHeaderAndSizeOverflowRejectAndCleanOnlyNewOutput() = runBlocking {
        val directory = directory()
        try {
            val encrypted = archive()
            val changed = encrypted.copyOf().apply { this[20] = (this[20].toInt() xor 1).toByte() }
            val tampered = File(directory, "tampered.zip")
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineBackupArchiveCrypto.decryptToPrivateStaging(ByteArrayInputStream(changed), password(), tampered)
            } }
            assertFalse(tampered.exists())
            val overflow = File(directory, "overflow.zip")
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineBackupArchiveCrypto.decryptToPrivateStaging(ByteArrayInputStream(encrypted), password(), overflow, maximumPlaintextBytes = 16)
            } }
            assertFalse(overflow.exists())
        } finally { directory.deleteRecursively() }
    }
    @Test fun zipWriterCanCloseCipherStreamAndStillProduceAnAuthenticatedCompleteArchive() = runBlocking {
        val directory = directory()
        try {
            val encrypted = ByteArrayOutputStream()
            OfflineBackupKeyEnvelope.create(password()).use { generation ->
                OfflineBackupArchiveCrypto.encrypt(generation, encrypted) { output ->
                    java.util.zip.ZipOutputStream(output).use { zip ->
                        zip.putNextEntry(java.util.zip.ZipEntry("payload.txt"))
                        zip.write("synthetic ZIP payload".toByteArray())
                        zip.closeEntry()
                    }
                }
            }
            val output = File(directory, "private.zip")
            OfflineBackupArchiveCrypto.decryptToPrivateStaging(ByteArrayInputStream(encrypted.toByteArray()), password(), output)
            java.util.zip.ZipInputStream(output.inputStream()).use { zip ->
                assertEquals("payload.txt", zip.nextEntry.name)
                assertEquals("synthetic ZIP payload", zip.readBytes().toString(Charsets.UTF_8))
                assertNull(zip.nextEntry)
            }
        } finally { directory.deleteRecursively() }
    }
    @Test fun cancellationBeforeIoDispatcherEntryStillConsumesPassword() {
        val directory = directory()
        val input = password()
        val output = File(directory, "cancelled.zip")
        try {
            assertThrows(kotlinx.coroutines.CancellationException::class.java) { runBlocking {
                coroutineContext[kotlinx.coroutines.Job]!!.cancel()
                OfflineBackupArchiveCrypto.decryptToPrivateStaging(ByteArrayInputStream(byteArrayOf()), input, output)
            } }
            assertTrue(input.all { it == '\u0000' })
            assertFalse(output.exists())
        } finally { directory.deleteRecursively() }
    }
    @Test fun existingOutputAndMalformedHeaderPreserveExistingDataAndConsumePassword() = runBlocking {
        val directory = directory()
        try {
            val existing = File(directory, "keep.zip").apply { writeText("keep prior data") }
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineBackupArchiveCrypto.decryptToPrivateStaging(ByteArrayInputStream(archive()), password(), existing)
            } }
            assertEquals("keep prior data", existing.readText())
            val input = password()
            val missing = File(directory, "invalid.zip")
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineBackupArchiveCrypto.decryptToPrivateStaging(ByteArrayInputStream(byteArrayOf(1, 2)), input, missing)
            } }
            assertTrue(input.all { it == '\u0000' })
            assertFalse(missing.exists())
        } finally { directory.deleteRecursively() }
    }
}
