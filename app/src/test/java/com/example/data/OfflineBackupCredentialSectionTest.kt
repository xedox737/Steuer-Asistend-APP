package com.example.data

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

class OfflineBackupCredentialSectionTest {
    private val consent = OfflineBackupCredentialSection.Consent(manual = true)
    private val provider = OfflineBackupCredentialSection.Provider.OPENAI
    private fun generation() = OfflineBackupKeyEnvelope.create("synthetic-recovery-password".toCharArray())
    private fun secret() = "synthetic-api-secret-for-tests".toCharArray()
    private fun inputs() = mapOf(provider to secret())
    private fun seal(g: OfflineBackupKeyEnvelope.Generation) =
        requireNotNull(OfflineBackupCredentialSection.seal(g, inputs(), consent))

    @Test fun independentlyEncryptedRoundTripIsPortableAndAllSecretBuffersCanBeWiped() {
        generation().use { original ->
            val values = OfflineBackupCredentialSection.Provider.entries.associateWith { secret() }
            val section = requireNotNull(OfflineBackupCredentialSection.seal(original, values, consent))
            assertTrue(values.values.all { value -> value.all { it == '\u0000' } })
            val ascii = "synthetic-api-secret-for-tests".toByteArray().toList()
            assertFalse(section.toList().windowed(ascii.size).any { it == ascii })
            val utf16 = "synthetic-api-secret-for-tests".toByteArray(Charsets.UTF_16BE).toList()
            assertFalse(section.toList().windowed(utf16.size).any { it == utf16 })
            val header = OfflineBackupArchiveHeader.read(section.copyOfRange(8, section.size).inputStream())
            OfflineBackupKeyEnvelope.recover(header.recovery, "synthetic-recovery-password".toCharArray()).use { recovered ->
                val opened = OfflineBackupCredentialSection.open(recovered, section)
                assertEquals(3, opened.values.size)
                opened.values.values.forEach { assertArrayEquals(secret(), it) }
                opened.close()
                assertTrue(opened.values.values.all { value -> value.all { it == '\u0000' } })
            }
            assertFalse(seal(original).contentEquals(seal(original)))
        }
    }

    @Test fun defaultsExcludeBothTriggersAndOptInsAreIndependent() {
        generation().use { g ->
            for (trigger in OfflineBackupCredentialSection.Trigger.entries) {
                val values = inputs()
                assertNull(OfflineBackupCredentialSection.seal(g, values, trigger = trigger))
                assertTrue(values.values.single().all { it == '\u0000' })
            }
            assertNull(OfflineBackupCredentialSection.seal(g, inputs(), consent,
                OfflineBackupCredentialSection.Trigger.SCHEDULED))
            val automatic = OfflineBackupCredentialSection.Consent(scheduled = true)
            assertNull(OfflineBackupCredentialSection.seal(g, inputs(), automatic))
            assertNotNull(OfflineBackupCredentialSection.seal(g, inputs(), automatic,
                OfflineBackupCredentialSection.Trigger.SCHEDULED))
        }
    }

    @Test fun tamperedTruncatedExtendedAndOversizedSectionsAreRejected() {
        generation().use { g ->
            val section = seal(g)
            val variants = listOf(section.copyOf(section.size - 1), section + byteArrayOf(1),
                section.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() },
                section.copyOf().apply { this[62] = (this[62].toInt() xor 1).toByte() },
                ByteArray(100_000))
            variants.forEach { damaged ->
                assertThrows(OfflineBackupValidationException::class.java) { OfflineBackupCredentialSection.open(g, damaged) }
            }
        }
    }

    @Test fun oldMasterCannotOpenNewSectionEvenWithForgedGenerationContext() {
        generation().use { old -> generation().use { fresh ->
            val section = seal(fresh)
            val master = old.masterCopy()
            try {
                OfflineBackupKeyEnvelope.Generation(fresh.recovery, master).use { forged ->
                    assertThrows(OfflineBackupValidationException::class.java) { OfflineBackupCredentialSection.open(forged, section) }
                }
            } finally { master.fill(0) }
        } }
    }

    @Test fun invalidInputSizesFailAndConsumeAllInputs() {
        generation().use { g ->
            for (size in listOf(0, 4097)) {
                val values = mapOf(provider to CharArray(size) { 'x' })
                assertThrows(IllegalArgumentException::class.java) { OfflineBackupCredentialSection.seal(g, values, consent) }
                assertTrue(values.values.single().all { it == '\u0000' })
            }
        }
    }

    @Test fun authenticatedButInvalidCredentialStructuresAreRejectedBeforeReturningSecrets() {
        generation().use { g ->
            fun raw(vararg bytes: Int) = bytes.map { it.toByte() }.toByteArray()
            // Unknown provider, zero/oversized length, truncated value, duplicate and trailing fields.
            val variants = listOf(raw(1, 99, 0, 1, 0, 120), raw(1, 1, 0, 0), raw(1, 1, 16, 1),
                raw(1, 1, 0, 1), raw(2, 1, 0, 1, 0, 120, 1, 0, 1, 0, 121), raw(0, 1))
            for (plain in variants) {
                val key = ByteArray(32) { (it + 1).toByte() }
                try {
                    val wrapped = OfflineBackupKeyEnvelope.wrapDataKey(g, UUID.randomUUID().toString(), key)
                    val header = OfflineBackupArchiveHeader.encode(g.recovery, wrapped)
                    val prefix = "IMMCRED2".toByteArray(Charsets.US_ASCII)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")); cipher.updateAAD(prefix + header)
                    val section = prefix + header + cipher.iv + cipher.doFinal(plain)
                    assertThrows(OfflineBackupValidationException::class.java) { OfflineBackupCredentialSection.open(g, section) }
                } finally { key.fill(0); plain.fill(0) }
            }
        }
    }
}
