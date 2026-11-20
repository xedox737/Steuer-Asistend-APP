package com.example.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class OfflineBackupStreamCipherTest {
    private val key = ByteArray(32) { it.toByte() }
    private val header = "synthetic authenticated archive header".toByteArray()
    private val plain = ByteArray(2 * 1024 * 1024 + 19) { (it * 31).toByte() }
    private fun encrypted(): ByteArray = ByteArrayOutputStream().also { output ->
        OfflineBackupStreamCipher.encrypt(key, header, output).use { it.write(plain) }
    }.toByteArray()
    private fun read(bytes: ByteArray, secret: ByteArray = key, aad: ByteArray = header): ByteArray =
        OfflineBackupStreamCipher.decrypt(secret, aad, ByteArrayInputStream(bytes)).use { it.readBytes() }

    @Test fun multipleSegmentsRoundTripAndIndependentArchivesUseFreshRandomness() {
        val first = encrypted()
        val second = encrypted()
        assertFalse(first.contentEquals(second))
        assertArrayEquals(plain, read(first))
        assertArrayEquals(plain, read(second))
    }
    @Test fun wrongKeyAndChangedAuthenticatedHeaderCannotAuthenticate() {
        val bytes = encrypted()
        assertThrows(IOException::class.java) { read(bytes, key.copyOf().apply { this[0] = 99 }) }
        assertThrows(IOException::class.java) { read(bytes, aad = header + 1.toByte()) }
    }
    @Test fun corruptionAndMissingFinalSegmentCannotBeAcceptedAtEof() {
        val bytes = encrypted()
        assertThrows(IOException::class.java) { read(bytes.copyOf().apply { this[size / 2] = (this[size / 2].toInt() xor 1).toByte() }) }
        assertThrows(IOException::class.java) { read(bytes.copyOf(bytes.size - 1)) }
        assertThrows(IOException::class.java) { read(bytes.copyOf(1024 * 1024)) }
    }
    @Test fun invalidKeySizesAreRejectedBeforeCreatingCiphertext() {
        listOf(0, 16, 31, 33).forEach { size ->
            val output = ByteArrayOutputStream()
            assertThrows(IllegalArgumentException::class.java) { OfflineBackupStreamCipher.encrypt(ByteArray(size), header, output) }
            assertEquals(0, output.size())
        }
    }
}
