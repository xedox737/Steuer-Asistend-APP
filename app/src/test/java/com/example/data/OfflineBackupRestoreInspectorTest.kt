package com.example.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.api.AiProviderSettings
import com.example.api.TestAndroidKeyStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
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
class OfflineBackupRestoreInspectorTest {
    private lateinit var root: File
    private lateinit var source: Context
    private lateinit var target: Context
    private lateinit var sourceDb: AppDatabase
    private lateinit var targetDb: AppDatabase
    private lateinit var original: File
    private val namespace = "inspect-${UUID.randomUUID()}"
    private val secret = "test-only-synthetic-gemini-key-12345"
    private fun password() = "synthetic-recovery-password".toCharArray()
    private fun context(base: Context, name: String): Context = object : ContextWrapper(base) {
        private fun directory(child: String) = File(root, "$name/$child").apply { check(isDirectory || mkdirs()) }
        override fun getFilesDir() = directory("files")
        override fun getCacheDir() = directory("cache")
        override fun getNoBackupFilesDir() = directory("no-backup")
        override fun getSharedPreferences(store: String, mode: Int): SharedPreferences =
            base.getSharedPreferences("${namespace}_${name}_$store", mode)
    }
    @Before fun setup() = runBlocking<Unit> {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, namespace).apply { check(mkdirs()) }
        source = context(base, "source"); target = context(base, "target")
        sourceDb = Room.inMemoryDatabaseBuilder(source, AppDatabase::class.java)
            .openHelperFactory(OfflineBackupCoordinator.shared.sqliteFactory()).build()
        targetDb = Room.inMemoryDatabaseBuilder(target, AppDatabase::class.java)
            .openHelperFactory(OfflineBackupCoordinator.shared.sqliteFactory()).build()
        sourceDb.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 3, propertyId = "archive-house", name = "Archived house"))
        original = File(source.filesDir, "managed_documents/original.pdf").apply { parentFile!!.mkdirs(); writeText("%PDF-1.4 synthetic original") }
        sourceDb.receiptDao().insertReceipt(Receipt(id = 41, internalId = "archive-receipt", propertyId = "archive-house",
            aussteller = "Archived", datum = "2026-11-11", uhrzeit = "", bruttobetrag = 125.73,
            hauptkategorie = "Kosten", unterkategorie = "Reparatur", kontoNr = "4800", beschreibung = "Archived", imageUrl = original.absolutePath))
        targetDb.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 77, propertyId = "live-house", name = "Keep live"))
        target.getSharedPreferences("bank_transaction_notes", 0).edit().putString("note", "keep live note").commit()
    }
    @After fun cleanup() {
        sourceDb.close(); targetDb.close(); TestAndroidKeyStore.remove()
        listOf(source, target).forEach { context -> PersistentPreferenceInventory.stores.forEach {
            context.getSharedPreferences(it.name, 0).edit().clear().commit()
        }; context.getSharedPreferences("ai_provider_settings", 0).edit().clear().commit() }
        root.deleteRecursively()
    }
    private suspend fun archive(credentials: Boolean = false): ByteArray {
        if (credentials) {
            TestAndroidKeyStore.register()
            AiProviderSettings.storeGeminiKey(source, secret.toCharArray())
        }
        return OfflineBackupKeyEnvelope.create(password()).use { generation ->
            OfflineBackupSnapshot.capture(source, sourceDb, credentials = if (credentials)
                OfflineBackupSnapshot.CredentialCapture(generation, OfflineBackupCredentialSection.Consent(manual = true),
                    OfflineBackupCredentialSection.Trigger.MANUAL) else null).use { snapshot ->
                val bytes = ByteArrayOutputStream()
                OfflineBackupArchiveCrypto.encrypt(generation, bytes) { snapshot.writeArchive(it) }
                bytes.toByteArray()
            }
        }
    }
    private fun noStagingLeft() {
        val staging = File(target.filesDir, "offline-backup-staging")
        assertTrue(staging.listFiles().orEmpty().isEmpty())
    }
    private suspend fun liveUnchanged() {
        assertEquals("Keep live", targetDb.propertyDao().getAllProperties().single().name)
        assertTrue(targetDb.receiptDao().getAllReceiptsIncludingDeletedList().isEmpty())
        assertEquals(mapOf("note" to "keep live note"), target.getSharedPreferences("bank_transaction_notes", 0).all)
        assertFalse(File(target.filesDir, "managed_documents").exists())
        assertNull(OfflineBackupGenerationStore(target).loadCurrent())
    }

    @Test fun portablePreviewUsesArchivePasswordWithoutDeviceKeysAndNeverAppliesLiveData() = runBlocking<Unit> {
        val encrypted = archive(credentials = true)
        TestAndroidKeyStore.remove() // Simulate another installation with no source Keystore.
        val inputPassword = password()
        var closes = 0
        val token = OfflineBackupCoordinator.shared.beginCapture()
        val prepared = OfflineBackupRestoreInspector.inspect(target, inputPassword) {
            object : ByteArrayInputStream(encrypted) { override fun close() { closes++; super.close() } }
        }
        try {
            assertTrue(inputPassword.all { it == '\u0000' })
            assertEquals(1, closes)
            UUID.fromString(prepared.preview.backupId)
            assertEquals(18, prepared.preview.rowCounts.size)
            assertEquals(1L, prepared.preview.rowCounts["receipts"])
            assertEquals(1, prepared.preview.fileVersions)
            assertEquals(original.length(), prepared.preview.originalBytes)
            assertEquals(setOf(OfflineBackupCredentialSection.Provider.GEMINI), prepared.preview.credentialProviders)
            val receipt = prepared.probe.database.receiptDao().getAllReceiptsIncludingDeletedList().single()
            assertEquals(41, receipt.id); assertEquals("archive-receipt", receipt.internalId)
            assertArrayEquals(original.readBytes(), File(receipt.imageUrl!!).readBytes())
            val sealed = prepared.contents.parts.getValue("credentials.bin")
            assertNull(sealed.file)
            assertFalse(File(target.filesDir, "offline-backup-staging").walkTopDown().filter { it.isFile }.any {
                it.readBytes().toString(Charsets.UTF_8).contains(secret)
            })
            liveUnchanged()
            OfflineBackupCoordinator.shared.completeCapture(token)
        } finally { prepared.close(); prepared.close() }
        noStagingLeft()
    }

    @Test fun wrongPasswordTruncationAndAuthenticatedNonZipRejectAndCleanOnlyPrivateWork() = runBlocking<Unit> {
        val encrypted = archive()
        val nonZip = ByteArrayOutputStream().also { bytes -> OfflineBackupKeyEnvelope.create(password()).use { generation ->
            OfflineBackupArchiveCrypto.encrypt(generation, bytes) { it.write("not a ZIP".toByteArray()) }
        } }.toByteArray()
        for ((bytes, supplied) in listOf(encrypted to "wrong-recovery-password".toCharArray(),
            encrypted.copyOf(encrypted.size - 1) to password(), nonZip to password())) {
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineBackupRestoreInspector.inspect(target, supplied) { bytes.inputStream() }
            } }
            assertTrue(supplied.all { it == '\u0000' }); noStagingLeft(); liveUnchanged()
        }
    }

    @Test fun providerFailureAndCancellationConsumePasswordWithoutOpeningOrLeavingState() = runBlocking<Unit> {
        val supplied = password()
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
            OfflineBackupRestoreInspector.inspect(target, supplied) { throw SecurityException("private provider detail") }
        } }
        assertTrue(supplied.all { it == '\u0000' }); noStagingLeft()
        val cancelled = password()
        var opened = false
        assertThrows(kotlinx.coroutines.CancellationException::class.java) { runBlocking {
            coroutineContext[kotlinx.coroutines.Job]!!.cancel()
            OfflineBackupRestoreInspector.inspect(target, cancelled) { opened = true; byteArrayOf().inputStream() }
        } }
        assertFalse(opened); assertTrue(cancelled.all { it == '\u0000' }); noStagingLeft(); liveUnchanged()
    }
}
