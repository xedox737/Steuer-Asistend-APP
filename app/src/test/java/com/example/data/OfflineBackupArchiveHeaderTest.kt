package com.example.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class OfflineBackupArchiveHeaderTest {
    private val generation = UUID.randomUUID().toString()
    private val recovery = OfflineBackupKeyEnvelope.Recovery(generation, ByteArray(16) { it.toByte() }, 600_000, ByteArray(12), ByteArray(48))
    private val key = OfflineBackupKeyEnvelope.WrappedDataKey(generation, UUID.randomUUID().toString(), ByteArray(12), ByteArray(48))
    @Test fun roundTripPreservesExactAuthenticatedBytesAndLeavesCiphertextUnread() {
        val header = OfflineBackupArchiveHeader.encode(recovery, key)
        val source = ByteArrayInputStream(header + byteArrayOf(7, 8, 9))
        val result = OfflineBackupArchiveHeader.read(source)
        assertArrayEquals(header, result.authenticatedBytes)
        assertEquals(generation, result.recovery.generationId)
        assertEquals(key.archiveId, result.dataKey.archiveId)
        assertArrayEquals(recovery.salt, result.recovery.salt)
        assertArrayEquals(recovery.wrappedMaster, result.recovery.wrappedMaster)
        assertEquals(3, source.available())
        assertEquals(7, source.read())
    }
    @Test fun excessiveAndNegativeLengthsAreRejectedBeforeReadingAnyPayload() {
        listOf(-1, 0, 173, 65_537, Int.MAX_VALUE).forEach { length ->
            val prefix = ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use {
                it.write("IMMOFULL".toByteArray()); it.writeInt(2); it.writeInt(length)
            } }.toByteArray()
            assertThrows(OfflineBackupValidationException::class.java) { OfflineBackupArchiveHeader.read(ByteArrayInputStream(prefix)) }
        }
    }
    @Test fun unsupportedVersionSuiteAndKdfWorkAreRejectedWithoutDerivation() {
        val original = OfflineBackupArchiveHeader.encode(recovery, key)
        val newer = original.copyOf().also { ByteBuffer.wrap(it).putInt(8, 3) }
        assertTrue(assertThrows(OfflineBackupValidationException::class.java) { OfflineBackupArchiveHeader.read(ByteArrayInputStream(newer)) }.message!!.contains("aktualisieren"))
        val suite = original.copyOf().apply { this[16] = 99 }
        assertThrows(OfflineBackupValidationException::class.java) { OfflineBackupArchiveHeader.read(ByteArrayInputStream(suite)) }
        listOf(599_999, 2_000_001, Int.MAX_VALUE).forEach { iterations ->
            val changed = original.copyOf().also { ByteBuffer.wrap(it).putInt(49, iterations) }
            assertThrows(OfflineBackupValidationException::class.java) { OfflineBackupArchiveHeader.read(ByteArrayInputStream(changed)) }
        }
    }
    @Test fun truncationTrailingHeaderDataAndMismatchedGenerationCannotBeAccepted() {
        val original = OfflineBackupArchiveHeader.encode(recovery, key)
        assertThrows(EOFException::class.java) { OfflineBackupArchiveHeader.read(ByteArrayInputStream(original.copyOf(original.size - 1))) }
        val trailing = (original + 0.toByte()).also { ByteBuffer.wrap(it).putInt(12, original.size - 16 + 1) }
        assertThrows(OfflineBackupValidationException::class.java) { OfflineBackupArchiveHeader.read(ByteArrayInputStream(trailing)) }
        assertThrows(IllegalArgumentException::class.java) { OfflineBackupArchiveHeader.encode(recovery, key.copy(generationId = UUID.randomUUID().toString())) }
    }
}
