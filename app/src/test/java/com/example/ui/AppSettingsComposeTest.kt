package com.example.ui

import android.content.Context
import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.example.MainActivity
import com.example.api.ReceiptAnalysisProvider
import com.example.api.TestAndroidKeyStore
import com.example.api.AiProviderSettings
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class AppSettingsComposeTest {
    @get:Rule(order = 0) val applicationIsolation = IsolatedAndroidApplicationRule()
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(ui.activity)[ReceiptViewModel::class.java]
    private val keyContext get() = vm.getApplication<Application>()
    private val prefs get() = keyContext.getSharedPreferences("ai_provider_settings", Context.MODE_PRIVATE)
    @Before fun clear() { TestAndroidKeyStore.register(); prefs.edit().clear().commit(); refresh() }
    @After fun cleanup() { prefs.edit().clear().commit(); refresh(); TestAndroidKeyStore.remove() }
    private fun refresh() { ui.runOnIdle { runBlocking { vm.refreshPreferencesAfterRestore() } }; ui.mainClock.advanceTimeByFrame(); ui.waitForIdle() }
    // Synthetic, valid-format test keys stored through the unchanged production encryption path.
    private fun configured() {
        ui.runOnIdle {
            AiProviderSettings.storeOpenAiKey(keyContext, ("sk-" + "x".repeat(30)).toCharArray())
            AiProviderSettings.storeGeminiKey(keyContext, ("test-only-" + "z".repeat(30)).toCharArray())
            AiProviderSettings.storeGoogleRoutesKey(keyContext, ("test-routes-" + "y".repeat(30)).toCharArray())
            AiProviderSettings.saveSelection(keyContext, ReceiptAnalysisProvider.OPENAI, "gpt-5.6")
        }
        refresh()
    }
    private fun open() {
        ui.onNodeWithTag("nav_item_more").performClick()
        ui.onNodeWithTag("more_menu").performScrollToNode(hasTestTag("more_item_App-Einstellungen"))
        ui.onNodeWithTag("more_item_App-Einstellungen").performClick()
        ui.onNodeWithTag("settings_overview").assertIsDisplayed()
    }
    private fun row(tag: String, page: String = "overview") {
        ui.onNodeWithTag("settings_$page").performScrollToNode(hasTestTag("settings_row_$tag"))
        ui.onNodeWithTag("settings_row_$tag").performClick(); ui.waitForIdle()
    }
    private fun shell() {
        ui.onNode(hasText("ImmoPilot") and !hasAnyAncestor(hasTestTag("settings_info"))).assertIsDisplayed()
        ui.onNodeWithText("Immobilien. Finanzen. Steuern.").assertIsDisplayed()
        ui.onNodeWithTag("bottom_navigation").assertIsDisplayed()
        ui.onNodeWithTag("nav_item_more").assertIsSelected()
    }
    private fun back() { ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }; ui.waitForIdle() }
    private fun capture(name: String) {
        ui.runOnIdle {
            fun redraw(view: android.view.View) {
                if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") view.javaClass.getMethod("invalidateDescendants").invoke(view)
                view.requestLayout(); view.invalidate()
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) redraw(view.getChildAt(i))
            }
            redraw(ui.activity.window.decorView)
        }
        ui.mainClock.advanceTimeBy(300); ui.waitForIdle()
        val path = "build/reports/app-settings/$name.png"
        onView(isRoot()).captureRoboImage(path)
        val bitmap = android.graphics.BitmapFactory.decodeFile(path)
        val density = ui.activity.resources.displayMetrics.density
        fun bluePixels(top: Int, bottom: Int): Int {
            var count = 0
            for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(bitmap.height)) for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.blue(pixel) > 150 && android.graphics.Color.red(pixel) < 100 && android.graphics.Color.green(pixel) < 160) count++
            }
            return count
        }
        assertTrue("Recorded screenshot must include the ImmoPilot header", bluePixels(0, (56 * density).toInt()) > 100)
        assertTrue("Recorded screenshot must include bottom navigation", bluePixels(bitmap.height - (88 * density).toInt(), bitmap.height) > 100)
        bitmap.recycle()
    }
    @Test fun overviewUsesRealStatusesAndHasNoInventedSettingsOrDuplicateEntries() {
        open(); shell()
        listOf("OPENAI", "GEMINI", "ROUTES").forEach { provider ->
            ui.onNode(hasText("Nicht eingerichtet") and hasAnyAncestor(hasTestTag("settings_row_$provider")), useUnmergedTree = true).assertExists()
        }
        listOf("Backup & Cloud", "Gelernte Regeln", "Benachrichtigungen", "Standard-Objekt", "Darstellung", "Sprache").forEach { ui.onNodeWithText(it).assertDoesNotExist() }
        configured()
        listOf("OPENAI", "GEMINI", "ROUTES").forEach { provider ->
            ui.onNode(hasText("Eingerichtet") and hasAnyAncestor(hasTestTag("settings_row_$provider")), useUnmergedTree = true).assertExists()
        }
        // Capture a fresh root after also asserting live status updates above.
        open(); shell()
        capture("393-overview")
        ui.onNodeWithTag("settings_overview").performScrollToNode(hasText("Allgemein")); shell()
        ui.onNodeWithText("Sicherheit").assertExists(); ui.onNodeWithText("Allgemein").assertExists()
        capture("393-overview-end")
    }
    @Test fun providerPagesShowMaskModelAndRealEmptyStateAndReturnByBothBackActions() {
        configured(); open()
        SettingsProvider.entries.forEach { provider ->
            row(provider.name); shell()
            ui.onNodeWithTag("provider_key_mask").assertTextEquals("••••••••••••••••")
            ui.onNodeWithText("sk-" + "x".repeat(30)).assertDoesNotExist()
            if (provider == SettingsProvider.OPENAI) {
                ui.onNodeWithText("gpt-5.6").assertExists()
                capture("393-openai")
                ui.onNodeWithTag("settings_provider").performScrollToNode(hasText("Verwendung")); shell(); capture("393-openai-end")
            }
            back(); ui.onNodeWithTag("settings_overview").assertIsDisplayed()
            row(provider.name); ui.onNodeWithContentDescription("Zurück").performClick()
            ui.onNodeWithTag("settings_overview").assertIsDisplayed()
        }
        ui.runOnIdle { vm.deleteOpenAiKey() }
        row("OPENAI"); ui.onNodeWithTag("provider_key_mask").assertTextEquals("Nicht eingerichtet")
        ui.onNodeWithText("Jetzt einrichten").assertIsDisplayed()
        assertEquals("gpt-5.6", vm.aiProviderState.value.openAiModel)
    }
    @Test fun keyOverviewReturnsToOriginAndKeepsProviderSelectionAndModel() {
        configured(); open(); row("keys")
        row("GEMINI", "keys"); back(); ui.onNodeWithTag("settings_keys").assertIsDisplayed(); back()
        row("selection"); ui.onNodeWithTag("select_GEMINI").performClick()
        assertEquals(ReceiptAnalysisProvider.GEMINI, vm.aiProviderState.value.provider)
        assertEquals("gpt-5.6", vm.aiProviderState.value.openAiModel)
        back(); row("OPENAI"); row("model", "provider")
        ui.onNodeWithTag("openai_model_input").performTextReplacement("custom-model")
        ui.onNodeWithTag("save_openai_model").performClick()
        assertEquals("custom-model", vm.aiProviderState.value.openAiModel)
        assertTrue(vm.aiProviderState.value.hasOpenAiKey); assertTrue(vm.aiProviderState.value.hasGeminiKey)
    }
    @Test fun invalidKeysAreRejectedWithoutReplacingStoredKeysAndFormIsDisposed() {
        configured(); open()
        SettingsProvider.entries.forEach { provider ->
            row(provider.name); ui.onNodeWithTag("edit_provider_key").performClick()
            ui.onNodeWithTag("provider_api_key_input").performTextInput("invalid")
            ui.onNodeWithTag("save_provider_key").assertIsNotEnabled()
            ui.onNodeWithTag("private_device_confirmation").performClick()
            ui.onNodeWithTag("save_provider_key").performClick()
            ui.onNodeWithTag("settings_save_error").assertExists()
            assertTrue(vm.aiProviderState.value.hasOpenAiKey); assertTrue(vm.aiProviderState.value.hasGeminiKey); assertTrue(vm.aiProviderState.value.hasGoogleRoutesKey)
            back(); ui.onNodeWithTag("edit_provider_key").performClick()
            assertEquals("", ui.onNodeWithTag("provider_api_key_input").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            back(); back()
        }
    }
    @Test fun removalRequiresConfirmationAndRemovesOnlyCorrespondingSecret() {
        configured(); open()
        SettingsProvider.entries.forEach { provider ->
            row(provider.name)
            ui.onNodeWithTag("remove_provider_key").performClick()
            ui.onNodeWithText("${provider.title}-Schlüssel entfernen?").assertIsDisplayed()
            ui.onNodeWithText("Abbrechen").performClick()
            assertTrue(provider.configured(vm.aiProviderState.value))
            val before = prefs.all.toMap()
            ui.onNodeWithTag("remove_provider_key").performClick()
            ui.onNodeWithTag("confirm_remove_key").performClick()
            assertFalse(provider.configured(vm.aiProviderState.value))
            val prefix = when (provider) { SettingsProvider.OPENAI -> "openai_key"; SettingsProvider.GEMINI -> "gemini_key"; SettingsProvider.ROUTES -> "google_routes_key" }
            before.filterKeys { !it.startsWith(prefix) && it != "receipt_analysis_provider" }.forEach { (name, value) -> assertEquals(name, value, prefs.all[name]) }
            assertEquals("gpt-5.6", vm.aiProviderState.value.openAiModel)
            back()
        }
    }
    @Test fun keyChangesUseExistingEncryptedStorageAndPreserveOpenAiModel() {
        open()
        SettingsProvider.entries.forEach { provider ->
            row(provider.name); ui.onNodeWithTag("edit_provider_key").performClick()
            val synthetic = if (provider == SettingsProvider.OPENAI) "sk-" + "x".repeat(30) else "test-only-" + "z".repeat(30)
            ui.onNodeWithTag("provider_api_key_input").performTextInput(synthetic)
            ui.onNodeWithTag("private_device_confirmation").performClick()
            ui.onNodeWithTag("save_provider_key").performClick()
            ui.onNodeWithTag("settings_provider").assertIsDisplayed()
            ui.onNodeWithTag("provider_key_mask").assertTextEquals("••••••••••••••••")
            assertTrue(provider.configured(vm.aiProviderState.value))
            assertEquals(ReceiptAnalysisProvider.OPENAI, vm.aiProviderState.value.provider)
            assertFalse(prefs.all.values.any { it.toString().contains(synthetic) })
            assertFalse(org.robolectric.shadows.ShadowLog.getLogs().any { it.msg.contains(synthetic) })
            val recovered = when (provider) {
                SettingsProvider.OPENAI -> AiProviderSettings.getOpenAiKey(keyContext)
                SettingsProvider.GEMINI -> AiProviderSettings.getGeminiKey(keyContext)
                SettingsProvider.ROUTES -> AiProviderSettings.getGoogleRoutesKey(keyContext)
            }
            assertEquals(synthetic, recovered!!.concatToString()); recovered.fill('\u0000')
            assertEquals("gpt-5.6", vm.aiProviderState.value.openAiModel)
            back()
        }
    }
    @Test fun storageFailureIsShownWithoutLosingExistingConfiguration() {
        configured(); open(); row("OPENAI"); ui.onNodeWithTag("edit_provider_key").performClick()
        ui.onNodeWithTag("provider_api_key_input").performTextInput("sk-" + "x".repeat(30))
        ui.onNodeWithTag("private_device_confirmation").performClick()
        val originalCiphertext = prefs.getString("openai_key_ciphertext", null)
        TestAndroidKeyStore.remove()
        ui.onNodeWithTag("save_provider_key").performClick()
        ui.onNodeWithTag("settings_save_error").assertTextEquals("Der API-Schlüssel konnte auf diesem Gerät nicht sicher gespeichert werden.")
        assertEquals(originalCiphertext, prefs.getString("openai_key_ciphertext", null))
        assertTrue(vm.aiProviderState.value.hasOpenAiKey)
        TestAndroidKeyStore.register()
    }
    @Test fun generalInformationAndSecurityPagesReturnToSettingsByBothBackActions() {
        open()
        listOf("keys", "privacy", "addresses", "management", "selection").forEach { destination ->
            row(destination); back(); ui.onNodeWithTag("settings_overview").assertIsDisplayed()
            row(destination); ui.onNodeWithContentDescription("Zurück").performClick()
            ui.onNodeWithTag("settings_overview").assertIsDisplayed()
        }
        back(); ui.onNodeWithTag("more_menu").assertIsDisplayed()
    }
    @Test fun everySettingsPageAllowsAllFiveRealBottomNavigationClicksWithoutReturningToOldPage() {
        val sources = listOf("overview", "OPENAI", "GEMINI", "ROUTES", "keys", "privacy", "addresses", "management", "selection", "key", "model")
        val targets = listOf(AppScreen.DASHBOARD, AppScreen.RECEIPTS_LIST, AppScreen.ADD_RECEIPT, AppScreen.PROPERTIES, AppScreen.MORE)
        configured()
        sources.forEach { source -> targets.forEach { target ->
            open()
            when (source) {
                "overview" -> Unit
                "key", "model" -> { row("OPENAI"); if (source == "key") ui.onNodeWithTag("edit_provider_key").performClick() else row("model", "provider") }
                else -> row(source)
            }
            shell()
            ui.onNodeWithTag("nav_item_${target.name.lowercase()}").performClick(); ui.waitForIdle()
            assertEquals("$source → $target", target, vm.currentScreen.value)
            ui.onNodeWithTag("settings_overview").assertDoesNotExist(); ui.onNodeWithTag("settings_provider").assertDoesNotExist()
            if (target == AppScreen.MORE) ui.onNodeWithTag("more_menu").assertIsDisplayed()
            if (target != AppScreen.DASHBOARD) { back(); assertEquals(AppScreen.DASHBOARD, vm.currentScreen.value) }
        } }
    }
}
