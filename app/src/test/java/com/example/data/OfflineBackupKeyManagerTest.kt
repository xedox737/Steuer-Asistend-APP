package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.api.TestAndroidKeyStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.GeneralSecurityException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
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
class OfflineBackupKeyManagerTest {
    private lateinit var context: Context
    private val root get() = File(context.noBackupFilesDir, "offline-backup-keys")
    private fun manager() = OfflineBackupKeyManager(context)
    private fun password() = "synthetic-recovery-password".toCharArray()
    @Before fun setup() {
        TestAndroidKeyStore.register()
        context = ApplicationProvider.getApplicationContext()
        root.deleteRecursively()
    }
    @After fun cleanup() { root.deleteRecursively(); TestAndroidKeyStore.remove() }
    private suspend fun archive(): ByteArray = ByteArrayOutputStream().also { output ->
        manager().encryptCurrent(output) { it.write("synthetic archive".toByteArray()) }
    }.toByteArray()
    private fun decrypt(bytes: ByteArray, password: CharArray): ByteArray = bytes.inputStream().use { input ->
        val header = OfflineBackupArchiveHeader.read(input)
        OfflineBackupKeyEnvelope.recover(header.recovery, password).use { generation ->
            val key = OfflineBackupKeyEnvelope.unwrapDataKey(generation, header.dataKey)
            try { OfflineBackupStreamCipher.decrypt(key, header.authenticatedBytes, input).use { it.readBytes() } }
            finally { key.fill(0) }
        }
    }

    @Test fun verifiedSetupAllowsPasswordlessEncryptionAndPortablePasswordRecovery() = runBlocking<Unit> {
        val password = password(); val confirmation = password()
        manager().initialize(password, confirmation)
        assertTrue(password.all { it == '\u0000' }); assertTrue(confirmation.all { it == '\u0000' })
        assertArrayEquals("synthetic archive".toByteArray(), decrypt(archive(), password()))
    }

    @Test fun confirmationFailureLeavesSetupAbsentAndConsumesBothBuffers() = runBlocking<Unit> {
        val password = password(); val confirmation = "synthetic-different-password".toCharArray()
        try { manager().initialize(password, confirmation); fail("Confirmation accepted") }
        catch (_: IllegalArgumentException) { }
        assertFalse(root.exists())
        assertTrue(password.all { it == '\u0000' }); assertTrue(confirmation.all { it == '\u0000' })
    }

    @Test fun rotationKeepsOldArchivesAndRejectsOldPasswordAndMasterForNewArchives() = runBlocking<Unit> {
        manager().initialize(password(), password())
        val oldArchive = archive()
        val oldHeader = OfflineBackupArchiveHeader.read(oldArchive.inputStream())
        OfflineBackupKeyEnvelope.recover(oldHeader.recovery, password()).use { oldGeneration ->
            manager().changePassword(oldGeneration.recovery.generationId,
                "synthetic-new-recovery-password".toCharArray(), "synthetic-new-recovery-password".toCharArray())
            val newArchive = archive()
            val newHeader = OfflineBackupArchiveHeader.read(newArchive.inputStream())
            assertNotEquals(oldHeader.recovery.generationId, newHeader.recovery.generationId)
            assertArrayEquals("synthetic archive".toByteArray(), decrypt(oldArchive, password()))
            assertArrayEquals("synthetic archive".toByteArray(), decrypt(newArchive, "synthetic-new-recovery-password".toCharArray()))
            assertThrows(GeneralSecurityException::class.java) { decrypt(newArchive, password()) }
            val oldMaster = oldGeneration.masterCopy()
            try {
                OfflineBackupKeyEnvelope.Generation(newHeader.recovery, oldMaster).use { forged ->
                    assertThrows(GeneralSecurityException::class.java) { OfflineBackupKeyEnvelope.unwrapDataKey(forged, newHeader.dataKey) }
                }
            } finally { oldMaster.fill(0) }
        }
    }

    @Test fun samePasswordCannotRotateOrReplaceCurrentRecord() = runBlocking<Unit> {
        manager().initialize(password(), password())
        val file = File(root, "generation.bin"); val saved = file.readBytes()
        val id = OfflineBackupGenerationStore(context).loadCurrent()!!.use { it.recovery.generationId }
        try { manager().changePassword(id, password(), password()); fail("Unchanged password accepted") }
        catch (_: IllegalArgumentException) { }
        assertArrayEquals(saved, file.readBytes())
    }

    @Test fun canceledRotationWaitsBehindArchiveAndConsumesPasswordsWithoutChangingRecord() = runBlocking<Unit> {
        manager().initialize(password(), password())
        val file = File(root, "generation.bin"); val saved = file.readBytes()
        val id = OfflineBackupGenerationStore(context).loadCurrent()!!.use { it.recovery.generationId }
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val encryption = async {
            manager().encryptCurrent(ByteArrayOutputStream()) { entered.complete(Unit); release.await(); it.write(1) }
        }
        entered.await()
        val password = "synthetic-new-recovery-password".toCharArray(); val confirmation = password.copyOf()
        val attempted = CompletableDeferred<Unit>()
        val rotation = async { attempted.complete(Unit); manager().changePassword(id, password, confirmation) }
        attempted.await()
        rotation.cancelAndJoin()
        assertTrue(password.all { it == '\u0000' }); assertTrue(confirmation.all { it == '\u0000' })
        assertArrayEquals(saved, file.readBytes())
        release.complete(Unit); encryption.await()
    }
    @Test fun restoreExclusivityBlocksArchiveAndPasswordSetupWithoutRequiringAnExistingGeneration() = runBlocking<Unit> {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val restore = async { manager().withRestoreExclusivity { entered.complete(Unit); release.await() } }
        entered.await()
        val password = password(); val confirmation = password()
        val attempted = CompletableDeferred<Unit>()
        val setup = async { attempted.complete(Unit); manager().initialize(password, confirmation) }
        attempted.await(); assertFalse(setup.isCompleted); assertFalse(root.exists())
        setup.cancelAndJoin()
        assertTrue(password.all { it == '\u0000' }); assertTrue(confirmation.all { it == '\u0000' })
        release.complete(Unit); restore.await()
        manager().initialize(password(), password())
        val nextEntered = CompletableDeferred<Unit>(); val nextRelease = CompletableDeferred<Unit>()
        val nextRestore = async { manager().withRestoreExclusivity { nextEntered.complete(Unit); nextRelease.await() } }
        nextEntered.await()
        val archiveStarted = CompletableDeferred<Unit>()
        val pendingArchive = async { archiveStarted.complete(Unit); archive() }
        archiveStarted.await(); assertFalse(pendingArchive.isCompleted)
        nextRelease.complete(Unit); nextRestore.await()
        assertArrayEquals("synthetic archive".toByteArray(), decrypt(pendingArchive.await(), password()))
    }

}
