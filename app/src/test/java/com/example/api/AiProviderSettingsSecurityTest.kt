package com.example.api

import android.content.Context
import com.example.data.OfflineBackupCredentialSection
import com.example.data.OfflineBackupKeyEnvelope
import com.example.data.OfflineBackupValidationException
import java.security.KeyStore
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiProviderSettingsSecurityTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val prefs get() = context.getSharedPreferences("ai_provider_settings", Context.MODE_PRIVATE)
    @Before fun prepare() { prefs.edit().clear().commit(); TestAndroidKeyStore.register() }
    @After fun cleanup() { prefs.edit().clear().commit(); TestAndroidKeyStore.remove() }
    private fun dummyOpenAi() = "sk-" + "x".repeat(30)
    private fun dummyGoogle() = "test-only-" + "z".repeat(30)
    @Test fun maintenanceRejectsKeyCreationBeforeKeystoreMutationAndWipesAllInputs() {
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val before = prefs.all.toMap()
        val openAi = dummyOpenAi().toCharArray(); val gemini = dummyGoogle().toCharArray(); val routes = (dummyGoogle() + "routes").toCharArray()
        com.example.data.OfflineBackupCoordinator.shared.beginMaintenance().use {
            assertThrows(OfflineBackupValidationException::class.java) { AiProviderSettings.storeOpenAiKey(context, openAi) }
            assertThrows(OfflineBackupValidationException::class.java) { AiProviderSettings.storeGeminiKey(context, gemini) }
            assertThrows(OfflineBackupValidationException::class.java) { AiProviderSettings.storeGoogleRoutesKey(context, routes) }
            assertTrue(listOf(openAi, gemini, routes).all { chars -> chars.all { it == '\u0000' } })
            assertTrue(keys.aliases().toList().isEmpty())
            assertEquals(before, prefs.all)
        }
        AiProviderSettings.storeOpenAiKey(context, dummyOpenAi().toCharArray())
        assertNotNull(keys.getKey("steuer_assistent_openai_key_v1", null))
    }

    @Test fun allProviderKeysRoundTripThroughRealAesGcmAndInputArraysAreWiped() {
        val openAi = dummyOpenAi().toCharArray(); val gemini = dummyGoogle().toCharArray(); val routes = (dummyGoogle() + "routes").toCharArray()
        AiProviderSettings.storeOpenAiKey(context, openAi); AiProviderSettings.storeGeminiKey(context, gemini); AiProviderSettings.storeGoogleRoutesKey(context, routes)
        listOf(openAi, gemini, routes).forEach { assertTrue(it.all { char -> char == '\u0000' }) }
        assertEquals(dummyOpenAi(), AiProviderSettings.getOpenAiKey(context)!!.concatToString())
        assertEquals(dummyGoogle(), AiProviderSettings.getGeminiKey(context)!!.concatToString())
        assertEquals(dummyGoogle() + "routes", AiProviderSettings.getGoogleRoutesKey(context)!!.concatToString())
        prefs.all.values.forEach { assertFalse(it.toString().contains(dummyOpenAi())); assertFalse(it.toString().contains(dummyGoogle())) }
        val state = AiProviderSettings.loadState(context)
        assertTrue(state.hasOpenAiKey && state.hasGeminiKey && state.hasGoogleRoutesKey)
        assertFalse(state.toString().contains(dummyOpenAi()))
    }
    @Test fun replacingKeyUsesNewIvAndRemovingOneKeyPreservesOtherKeysAndModel() {
        AiProviderSettings.storeOpenAiKey(context, dummyOpenAi().toCharArray())
        AiProviderSettings.storeGeminiKey(context, dummyGoogle().toCharArray())
        AiProviderSettings.storeGoogleRoutesKey(context, (dummyGoogle() + "routes").toCharArray())
        AiProviderSettings.saveSelection(context, ReceiptAnalysisProvider.OPENAI, "retained-model")
        val oldIv = prefs.getString("openai_key_iv", null)
        AiProviderSettings.storeOpenAiKey(context, dummyOpenAi().toCharArray())
        assertNotEquals(oldIv, prefs.getString("openai_key_iv", null))
        val state = AiProviderSettings.clearOpenAiKey(context)
        assertFalse(state.hasOpenAiKey); assertTrue(state.hasGeminiKey); assertTrue(state.hasGoogleRoutesKey)
        assertEquals("retained-model", state.openAiModel)
        assertEquals(dummyGoogle(), AiProviderSettings.getGeminiKey(context)!!.concatToString())
        AiProviderSettings.clearGoogleRoutesKey(context)
        assertTrue(AiProviderSettings.loadState(context).hasGeminiKey)
    }
    @Test fun malformedKeysAreRejectedAndWipedWithoutOverwritingCiphertext() {
        AiProviderSettings.storeOpenAiKey(context, dummyOpenAi().toCharArray())
        val snapshot = prefs.all.toMap()
        val invalid = "invalid".toCharArray()
        assertThrows(IllegalArgumentException::class.java) { AiProviderSettings.storeOpenAiKey(context, invalid) }
        assertTrue(invalid.all { it == '\u0000' }); assertEquals(snapshot, prefs.all)
        assertFalse(AiProviderSettings.isPlausibleGeminiKey("short"))
        assertFalse(AiProviderSettings.isPlausibleGoogleRoutesKey("with whitespace value"))
        assertTrue(AiProviderSettings.isPlausibleGeminiKey(dummyGoogle()))
        assertTrue(AiProviderSettings.isPlausibleGoogleRoutesKey(dummyGoogle()))
    }
    private fun backupGeneration() = OfflineBackupKeyEnvelope.create("synthetic-recovery-password".toCharArray())

    @Test fun backupExportRoundTripsAllProvidersWithoutChangingSettings() {
        AiProviderSettings.storeOpenAiKey(context, dummyOpenAi().toCharArray())
        AiProviderSettings.storeGeminiKey(context, dummyGoogle().toCharArray())
        AiProviderSettings.storeGoogleRoutesKey(context, (dummyGoogle() + "routes").toCharArray())
        AiProviderSettings.saveSelection(context, ReceiptAnalysisProvider.OPENAI, "retained-model")
        val before = prefs.all.toMap()
        backupGeneration().use { generation ->
            val section = requireNotNull(AiProviderSettings.exportBackupCredentials(context, generation,
                OfflineBackupCredentialSection.Consent(manual = true)))
            OfflineBackupCredentialSection.open(generation, section).use { opened ->
                assertArrayEquals(dummyOpenAi().toCharArray(), opened.values[OfflineBackupCredentialSection.Provider.OPENAI])
                assertArrayEquals(dummyGoogle().toCharArray(), opened.values[OfflineBackupCredentialSection.Provider.GEMINI])
                assertArrayEquals((dummyGoogle() + "routes").toCharArray(), opened.values[OfflineBackupCredentialSection.Provider.GOOGLE_ROUTES])
            }
        }
        assertEquals(before, prefs.all)
    }

    @Test fun backupExportExcludedTriggersDoNotReadOrRepairDamagedCredentials() {
        prefs.edit().putInt("openai_key_ciphertext", 42).putString("openai_key_iv", "damaged").commit()
        val before = prefs.all.toMap()
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val aliases = keys.aliases().toList().toSet()
        backupGeneration().use { generation ->
            assertNull(AiProviderSettings.exportBackupCredentials(context, generation))
            assertNull(AiProviderSettings.exportBackupCredentials(context, generation,
                OfflineBackupCredentialSection.Consent(manual = true), OfflineBackupCredentialSection.Trigger.SCHEDULED))
            assertNull(AiProviderSettings.exportBackupCredentials(context, generation,
                OfflineBackupCredentialSection.Consent(scheduled = true)))
        }
        assertEquals(before, prefs.all)
        assertEquals(aliases, keys.aliases().toList().toSet())
    }

    @Test fun backupExportFailuresPreserveEveryEntryAndNeverReplaceMissingDeviceKeys() {
        AiProviderSettings.storeOpenAiKey(context, dummyOpenAi().toCharArray())
        AiProviderSettings.storeGeminiKey(context, dummyGoogle().toCharArray())
        val valid = prefs.all.toMap()
        backupGeneration().use { generation ->
            val consent = OfflineBackupCredentialSection.Consent(manual = true)
            val mutations: List<() -> Unit> = listOf(
                { prefs.edit().putString("gemini_key_ciphertext", "invalid").commit(); Unit },
                { prefs.edit().remove("gemini_key_iv").commit(); Unit },
                { prefs.edit().putInt("gemini_key_iv", 1).commit(); Unit },
                { prefs.edit().putString("gemini_key_ciphertext", "x".repeat(25 * 1024)).commit(); Unit }
            )
            mutations.forEach { mutate ->
                prefs.edit().clear().apply { valid.forEach { (name, value) -> putString(name, value as String) } }.commit()
                mutate()
                val before = prefs.all.toMap()
                assertThrows(OfflineBackupValidationException::class.java) {
                    AiProviderSettings.exportBackupCredentials(context, generation, consent)
                }
                assertEquals(before, prefs.all)
            }
            prefs.edit().clear().apply { valid.forEach { (name, value) -> putString(name, value as String) } }.commit()
            val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keys.deleteEntry("steuer_assistent_gemini_key_v1")
            val aliases = keys.aliases().toList().toSet()
            val before = prefs.all.toMap()
            assertThrows(OfflineBackupValidationException::class.java) {
                AiProviderSettings.exportBackupCredentials(context, generation, consent)
            }
            assertEquals(before, prefs.all)
            assertEquals(aliases, keys.aliases().toList().toSet())
        }
    }

}
