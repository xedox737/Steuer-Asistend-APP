package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.api.TestAndroidKeyStore
import java.io.File
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineBackupGenerationStoreTest {
    private lateinit var context: Context
    private lateinit var root: File
    private val record get() = File(root, "generation.bin")
    private fun store() = OfflineBackupGenerationStore(context)
    private fun generation() = OfflineBackupKeyEnvelope.create("synthetic-recovery-password".toCharArray())
    @Before fun setup() {
        TestAndroidKeyStore.register()
        context = ApplicationProvider.getApplicationContext()
        root = File(context.noBackupFilesDir, "offline-backup-keys")
        root.deleteRecursively()
    }
    @After fun cleanup() { root.deleteRecursively(); TestAndroidKeyStore.remove() }

    @Test fun explicitInstallationSurvivesNewStoreWithoutPersistingPlainMaster() {
        assertNull(store().loadCurrent())
        assertFalse(root.exists())
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertFalse(keys.containsAlias(OfflineBackupDeviceKey.ALIAS))
        generation().use { original ->
            store().installFirst(original)
            store().loadCurrent()!!.use { opened ->
                assertEquals(original.recovery.generationId, opened.recovery.generationId)
                val expected = original.masterCopy(); val actual = opened.masterCopy()
                try {
                    assertArrayEquals(expected, actual)
                    assertFalse(record.readBytes().toList().windowed(32).any { it == expected.toList() })
                } finally { expected.fill(0); actual.fill(0) }
            }
            val saved = record.readBytes()
            assertThrows(IllegalStateException::class.java) { store().installFirst(original) }
            assertArrayEquals(saved, record.readBytes())
            assertEquals(listOf("generation.bin"), root.list()!!.toList())
        }
    }

    @Test fun replacementRequiresCurrentGenerationAndPreservesRecordOnStaleRequest() {
        generation().use { first -> generation().use { second ->
            store().installFirst(first)
            store().replaceCurrent(first.recovery.generationId, second)
            store().loadCurrent()!!.use { assertEquals(second.recovery.generationId, it.recovery.generationId) }
            val saved = record.readBytes()
            assertThrows(IllegalStateException::class.java) { store().replaceCurrent(first.recovery.generationId, first) }
            assertArrayEquals(saved, record.readBytes())
        } }
    }

    @Test fun malformedRecordsNeverGetReplaced() {
        generation().use { first -> generation().use { replacement ->
            store().installFirst(first)
            val original = record.readBytes()
            val variants = listOf(original.copyOf(original.size - 1), original + byteArrayOf(1),
                original.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() },
                original.copyOf().apply { this[180] = (this[180].toInt() xor 1).toByte() })
            for (damaged in variants) {
                record.writeBytes(damaged)
                assertThrows(Exception::class.java) { store().loadCurrent() }
                assertThrows(Exception::class.java) { store().replaceCurrent(first.recovery.generationId, replacement) }
                assertArrayEquals(damaged, record.readBytes())
            }
        } }
    }

    @Test fun missingDeviceKeyCannotRegenerateOrOverwriteExistingRecord() {
        generation().use { first -> generation().use { replacement ->
            store().installFirst(first)
            val saved = record.readBytes()
            val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keys.deleteEntry(OfflineBackupDeviceKey.ALIAS)
            assertThrows(OfflineBackupValidationException::class.java) { store().loadCurrent() }
            assertThrows(OfflineBackupValidationException::class.java) { store().replaceCurrent(first.recovery.generationId, replacement) }
            assertFalse(keys.containsAlias(OfflineBackupDeviceKey.ALIAS))
            assertArrayEquals(saved, record.readBytes())
        } }
    }
}
