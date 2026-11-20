package com.example.data

import org.junit.Assert.*
import org.junit.Test

class OfflineBackupPasswordKdfTest {
    @Test fun sha256DerivationMatchesIndependent600000IterationVectorAndConsumesPassword() {
        val password = "synthetic-recovery-password".toCharArray()
        val result = OfflineBackupPasswordKdf.derive(password, ByteArray(16) { it.toByte() })
        assertEquals("9f2a25f5ef85e7f068b9eed0554271d12e04fd54d45e02961d55c2e8ca2c8519", result.joinToString("") { "%02x".format(it) })
        assertTrue(password.all { it == '\u0000' })
        result.fill(0)
    }
    @Test fun invalidWorkFactorsAndSaltLengthsAreRejectedAndPasswordIsWiped() {
        listOf(0, 599_999, 2_000_001, Int.MAX_VALUE).forEach { count ->
            val password = "synthetic-recovery-password".toCharArray()
            assertThrows(IllegalArgumentException::class.java) { OfflineBackupPasswordKdf.derive(password, ByteArray(16), count) }
            assertTrue(password.all { it == '\u0000' })
        }
        listOf(0, 15, 33).forEach { length ->
            val password = "synthetic-recovery-password".toCharArray()
            assertThrows(IllegalArgumentException::class.java) { OfflineBackupPasswordKdf.derive(password, ByteArray(length)) }
            assertTrue(password.all { it == '\u0000' })
        }
    }
    @Test fun invalidPasswordLengthsAreRejectedAndWiped() {
        listOf(0, 15, 1025).forEach { length ->
            val password = CharArray(length) { 'x' }
            assertThrows(IllegalArgumentException::class.java) { OfflineBackupPasswordKdf.derive(password, ByteArray(16)) }
            assertTrue(password.all { it == '\u0000' })
        }
    }
}
