package com.example.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.example.api.AiProviderSettings
import com.example.api.TestAndroidKeyStore
import java.io.File
import java.security.KeyStore
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
@Config(sdk = [24, 35])
class OfflineRestoreCredentialSnapshotTest {
    private lateinit var context: Context
    private lateinit var root: File
    private val namespace = "rollback-credentials-${UUID.randomUUID()}"
    private val prefs get() = context.getSharedPreferences("ai_provider_settings", 0)
    private fun id() = UUID.randomUUID().toString()
    private fun keys() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val openAi = "sk-" + "x".repeat(30)
    private val gemini = "synthetic-gemini-" + "y".repeat(30)
    private val routes = "synthetic-routes-" + "z".repeat(30)
    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, namespace).apply { check(mkdirs()) }
        context = object : ContextWrapper(base) {
            override fun getNoBackupFilesDir() = root
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = base.getSharedPreferences("${namespace}_$name", mode)
        }
        TestAndroidKeyStore.register()
    }
    @After fun cleanup() { prefs.edit().clear().commit(); TestAndroidKeyStore.remove(); root.deleteRecursively() }
    @Test fun capturesOnlyAuthenticatedCiphertextsAndNeverWritesRawKeys() = runBlocking<Unit> {
        AiProviderSettings.storeOpenAiKey(context, openAi.toCharArray())
        AiProviderSettings.storeGeminiKey(context, gemini.toCharArray())
        AiProviderSettings.storeGoogleRoutesKey(context, routes.toCharArray())
        val before = prefs.all.toMap(); val aliases = keys().aliases().toList().toSet()
        OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
            val snapshot = OfflineRestoreCredentialSnapshot.capture(context, token, id())
            val raw = snapshot.file.readText()
            listOf(openAi, gemini, routes).forEach { assertFalse(raw.contains(it)) }
            assertEquals(OfflineBackupCredentialSection.Provider.entries.toSet(), snapshot.providers)
            val checked = OfflineRestoreCredentialSnapshot.verify(context, snapshot.id, snapshot.size, snapshot.sha256)
            assertEquals(before["openai_key_ciphertext"], checked.state.getValue(OfflineBackupCredentialSection.Provider.OPENAI)!!.ciphertext)
            assertEquals(before["gemini_key_iv"], checked.state.getValue(OfflineBackupCredentialSection.Provider.GEMINI)!!.iv)
            assertEquals(before, prefs.all); assertEquals(aliases, keys().aliases().toList().toSet())
        }
    }
    @Test fun absentCredentialsRemainAbsentWithoutCreatingAnyKeystoreAlias() = runBlocking<Unit> {
        OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
            val snapshot = OfflineRestoreCredentialSnapshot.capture(context, token, id())
            val checked = OfflineRestoreCredentialSnapshot.verify(context, snapshot.id, snapshot.size, snapshot.sha256)
            assertTrue(snapshot.providers.isEmpty()); assertTrue(checked.state.values.all { it == null })
            assertTrue(keys().aliases().toList().isEmpty()); assertTrue(prefs.all.isEmpty())
        }
    }
    @Test fun damagedOrIncompleteCredentialsFailWithoutRepairOrKeyReplacement() = runBlocking<Unit> {
        AiProviderSettings.storeGeminiKey(context, gemini.toCharArray())
        prefs.edit().remove("gemini_key_iv").putString("openai_key_ciphertext", "SYNTHETIC_BAD_VALUE").commit()
        val before = prefs.all.toMap(); val aliases = keys().aliases().toList().toSet()
        OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestoreCredentialSnapshot.capture(context, token, id()) } }
        }
        assertEquals(before, prefs.all); assertEquals(aliases, keys().aliases().toList().toSet())
        assertFalse(File(root, "offline-backup-state").exists())
    }
    @Test fun tamperingAndMissingDeviceKeyAreRejectedWithoutRegeneration() = runBlocking<Unit> {
        AiProviderSettings.storeGeminiKey(context, gemini.toCharArray())
        val before = prefs.all.toMap()
        OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
            val snapshot = OfflineRestoreCredentialSnapshot.capture(context, token, id())
            val bytes = snapshot.file.readBytes(); snapshot.file.writeBytes(bytes.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() })
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestoreCredentialSnapshot.verify(context, snapshot.id, snapshot.size, snapshot.sha256) } }
            snapshot.file.writeBytes(bytes)
            keys().deleteEntry("steuer_assistent_gemini_key_v1")
            val aliases = keys().aliases().toList().toSet()
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestoreCredentialSnapshot.verify(context, snapshot.id, snapshot.size, snapshot.sha256) } }
            assertEquals(aliases, keys().aliases().toList().toSet()); assertEquals(before, prefs.all)
        }
    }
    @Test fun occupiedDestinationIsNeverOverwritten() = runBlocking<Unit> {
        val snapshotId = id(); val folder = File(root, "offline-backup-state").apply { mkdirs() }
        val prior = File(folder, "rollback-$snapshotId.credentials.json").apply { writeText("keep") }
        OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestoreCredentialSnapshot.capture(context, token, snapshotId) } }
        }
        assertEquals("keep", prior.readText()); assertTrue(prefs.all.isEmpty())
    }
}
