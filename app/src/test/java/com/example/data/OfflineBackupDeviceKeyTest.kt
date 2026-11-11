package com.example.data

import com.example.api.TestAndroidKeyStore
import java.security.KeyStore
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Simulated Keystore persistence, real AES-GCM; physical device assurance is separate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class OfflineBackupDeviceKeyTest {
    @Before fun setup() { TestAndroidKeyStore.register() }
    @After fun cleanup() { TestAndroidKeyStore.remove() }
    private fun generation() = OfflineBackupKeyEnvelope.create("synthetic-recovery-password".toCharArray())
    @Test fun protectedGenerationReopensWithSameMasterAndFreshIv() {
        OfflineBackupDeviceKey.initialize()
        generation().use { original ->
            val first = OfflineBackupDeviceKey.protect(original)
            val second = OfflineBackupDeviceKey.protect(original)
            assertNotEquals(first.iv.toList(), second.iv.toList())
            assertEquals(48, first.ciphertext.size)
            OfflineBackupDeviceKey.open(first).use { opened ->
                val expected = original.masterCopy(); val actual = opened.masterCopy()
                try { assertArrayEquals(expected, actual) } finally { expected.fill(0); actual.fill(0) }
            }
        }
    }
    @Test fun initializationDoesNotReplaceExistingDeviceKey() {
        OfflineBackupDeviceKey.initialize()
        generation().use { original ->
            val record = OfflineBackupDeviceKey.protect(original)
            OfflineBackupDeviceKey.initialize()
            OfflineBackupDeviceKey.open(record).use { opened -> assertEquals(original.recovery.generationId, opened.recovery.generationId) }
        }
    }
    @Test fun missingDeviceKeyCannotSilentlyRegenerateWhileOpeningSavedRecord() {
        OfflineBackupDeviceKey.initialize()
        generation().use { original ->
            val record = OfflineBackupDeviceKey.protect(original)
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            store.deleteEntry(OfflineBackupDeviceKey.ALIAS)
            assertThrows(OfflineBackupValidationException::class.java) { OfflineBackupDeviceKey.open(record) }
            assertFalse(store.containsAlias(OfflineBackupDeviceKey.ALIAS))
        }
    }
    @Test fun alteredRecoveryContextOrCiphertextCannotOpenMaster() {
        OfflineBackupDeviceKey.initialize()
        generation().use { original ->
            val record = OfflineBackupDeviceKey.protect(original)
            val changedContext = record.copy(recovery = record.recovery.copy(generationId = UUID.randomUUID().toString()))
            assertThrows(OfflineBackupValidationException::class.java) { OfflineBackupDeviceKey.open(changedContext) }
            val changedCipher = record.copy(ciphertext = record.ciphertext.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() })
            assertThrows(OfflineBackupValidationException::class.java) { OfflineBackupDeviceKey.open(changedCipher) }
            OfflineBackupDeviceKey.open(record).close()
        }
    }
    @Test fun protectingWithoutExplicitSetupCannotCreateDeviceKey() {
        generation().use { original ->
            assertThrows(OfflineBackupValidationException::class.java) { OfflineBackupDeviceKey.protect(original) }
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            assertFalse(store.containsAlias(OfflineBackupDeviceKey.ALIAS))
        }
    }
}
