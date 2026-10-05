package com.example.api

import android.content.Context
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
}
