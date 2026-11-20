package com.example.data

import java.security.GeneralSecurityException
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class OfflineBackupKeyEnvelopeTest {
    private fun password() = "synthetic-recovery-password".toCharArray()
    private val dataKey = ByteArray(32) { (it + 3).toByte() }
    @Test fun recoveryUnlocksTheSameGenerationAndDataKeyWithoutKeepingPassword() {
        val input = password()
        OfflineBackupKeyEnvelope.create(input).use { generation ->
            assertTrue(input.all { it == '\u0000' })
            val wrapped = OfflineBackupKeyEnvelope.wrapDataKey(generation, UUID.randomUUID().toString(), dataKey)
            val recoveryInput = password()
            OfflineBackupKeyEnvelope.recover(generation.recovery, recoveryInput).use { recovered ->
                assertTrue(recoveryInput.all { it == '\u0000' })
                val result = OfflineBackupKeyEnvelope.unwrapDataKey(recovered, wrapped)
                try { assertArrayEquals(dataKey, result) } finally { result.fill(0) }
            }
        }
    }
    @Test fun wrongPasswordAndChangedRecoveryContextCannotUnlockMaster() {
        OfflineBackupKeyEnvelope.create(password()).use { generation ->
            val wrong = "another-synthetic-password".toCharArray()
            assertThrows(GeneralSecurityException::class.java) { OfflineBackupKeyEnvelope.recover(generation.recovery, wrong) }
            assertTrue(wrong.all { it == '\u0000' })
            val changed = generation.recovery.copy(generationId = UUID.randomUUID().toString())
            assertThrows(GeneralSecurityException::class.java) { OfflineBackupKeyEnvelope.recover(changed, password()) }
        }
    }
    @Test fun changedArchiveContextOrCiphertextCannotUnlockDataKey() {
        OfflineBackupKeyEnvelope.create(password()).use { generation ->
            val wrapped = OfflineBackupKeyEnvelope.wrapDataKey(generation, UUID.randomUUID().toString(), dataKey)
            assertThrows(GeneralSecurityException::class.java) { OfflineBackupKeyEnvelope.unwrapDataKey(generation, wrapped.copy(archiveId = UUID.randomUUID().toString())) }
            val changed = wrapped.copy(ciphertext = wrapped.ciphertext.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() })
            assertThrows(GeneralSecurityException::class.java) { OfflineBackupKeyEnvelope.unwrapDataKey(generation, changed) }
        }
    }
    @Test fun replacementGenerationUsesIndependentMasterSoOldMasterCannotDecryptNewArchives() {
        OfflineBackupKeyEnvelope.create(password()).use { old ->
            OfflineBackupKeyEnvelope.create("new-synthetic-recovery-password".toCharArray()).use { fresh ->
                assertNotEquals(old.recovery.generationId, fresh.recovery.generationId)
                val oldKey = old.masterCopy()
                val newKey = fresh.masterCopy()
                try {
                    assertFalse(oldKey.contentEquals(newKey))
                    val wrapped = OfflineBackupKeyEnvelope.wrapDataKey(fresh, UUID.randomUUID().toString(), dataKey)
                    // Preserve the new context while deliberately injecting the old MK.
                    OfflineBackupKeyEnvelope.Generation(fresh.recovery, oldKey).use { wrongMaster ->
                        assertThrows(GeneralSecurityException::class.java) { OfflineBackupKeyEnvelope.unwrapDataKey(wrongMaster, wrapped) }
                    }
                    assertThrows(GeneralSecurityException::class.java) { OfflineBackupKeyEnvelope.recover(fresh.recovery, password()) }
                } finally { oldKey.fill(0); newKey.fill(0) }
            }
            OfflineBackupKeyEnvelope.recover(old.recovery, password()).close()
        }
    }
    @Test fun malformedRecoveryParametersAreRejectedAndClosedMasterCannotBeUsed() {
        OfflineBackupKeyEnvelope.create(password()).use { generation ->
            val input = password()
            assertThrows(IllegalArgumentException::class.java) { OfflineBackupKeyEnvelope.recover(generation.recovery.copy(iterations = Int.MAX_VALUE), input) }
            assertTrue(input.all { it == '\u0000' })
            generation.close()
            assertThrows(IllegalStateException::class.java) { OfflineBackupKeyEnvelope.wrapDataKey(generation, UUID.randomUUID().toString(), dataKey) }
        }
    }
}
