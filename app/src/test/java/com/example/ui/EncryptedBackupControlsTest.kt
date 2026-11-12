package com.example.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
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
class EncryptedBackupControlsTest {
    @get:Rule(order = 0) val isolation = IsolatedAndroidApplicationRule()
    @get:Rule(order = 1) val ui = createAndroidComposeRule<ComponentActivity>()
    private var state by mutableStateOf(OfflineBackupController.State(loaded = true))
    private var configured: Triple<CharArray, CharArray, Boolean>? = null
    private var folderCalls = 0
    private var saveCalls = 0
    private var cancelCalls = 0

    private fun show() = ui.setContent {
        MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                EncryptedBackupControls(state, onFolder = { folderCalls++ },
                    onConfigure = { password, confirmation, change -> configured = Triple(password, confirmation, change) },
                    onManualCredentials = { state = state.copy(config = state.config.copy(manualCredentials = it)) },
                    onSave = { saveCalls++ }, onCancel = { cancelCalls++ }, onReload = {})
            }
        }
    }
    private fun node(tag: String) = ui.onNodeWithTag("offline_backup_$tag")

    private fun openPasswordDialog() {
        node("password_setup").performScrollTo().performClick()
        // Match existing native Robolectric text-dialog tests: bound the Android
        // window because wrap-content text-input windows do not settle here.
        ui.mainClock.advanceTimeByFrame()
        ui.runOnUiThread {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            assertTrue(dialog.isShowing)
            val density = ui.activity.resources.displayMetrics.density
            dialog.window!!.setLayout((360 * density).toInt(), (700 * density).toInt())
        }
    }

    @Test fun setupRequiresMatchingPasswordAndTransfersOnlyConfirmedInput() {
        show()
        node("export").assertIsNotEnabled()
        node("include_credentials").assertIsOff()
        openPasswordDialog()
        ui.onNodeWithText("Ohne dieses Passwort ist eine Wiederherstellung nicht möglich. Bitte außerhalb der App sicher aufbewahren.").assertExists()
        node("password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        node("password_confirmation").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        node("password").performTextInput("a long recovery password")
        node("password_confirmation").performTextInput("different password")
        node("password_confirm").assertIsNotEnabled()
        node("password_confirmation").performTextReplacement("a long recovery password")
        node("password_confirm").performClick()
        ui.runOnIdle {
            val result = requireNotNull(configured)
            assertArrayEquals("a long recovery password".toCharArray(), result.first)
            assertArrayEquals(result.first, result.second)
            assertFalse(result.third)
            result.first.fill('\u0000'); result.second.fill('\u0000')
        }
        node("password").assertDoesNotExist()
    }

    @Test fun configuredControlsRouteActionsAndCredentialOptIn() {
        state = state.copy(generationId = "generation", folderReady = true, folderLabel = "Sicherungen")
        show()
        node("folder").performClick()
        node("include_credentials").performClick().assertIsOn()
        node("export").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(1, folderCalls); assertEquals(1, saveCalls) }
        openPasswordDialog()
        ui.onNodeWithText("Ältere Archive benötigen weiterhin ihr bisheriges Passwort.").assertExists()
        node("password").performTextInput("new recovery password")
        node("password_confirmation").performTextInput("new recovery password")
        node("password_confirm").performClick()
        ui.runOnIdle {
            assertTrue(requireNotNull(configured).third)
            configured!!.first.fill('\u0000'); configured!!.second.fill('\u0000')
        }
    }

    @Test fun busyStateBlocksChangesAndOffersCancellation() {
        state = state.copy(generationId = "generation", folderReady = true,
            operation = OfflineBackupController.Operation.SAVING, status = "Zielsicherung wird geprüft")
        show()
        node("password_setup").assertIsNotEnabled()
        node("folder").assertIsNotEnabled()
        node("include_credentials").assertIsNotEnabled()
        node("export").assertIsNotEnabled()
        node("progress").assertExists()
        node("v2_status").assertTextEquals("Zielsicherung wird geprüft")
        node("cancel").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(1, cancelCalls); state = state.copy(operation = null, keyProblem = "Schlüssel nicht verfügbar") }
        node("password_setup").assertIsNotEnabled()
    }

    @Test fun dismissedPasswordIsAbsentOnReopening() {
        show()
        openPasswordDialog()
        node("password").performTextInput("discard this password")
        ui.onNodeWithText("Abbrechen").performClick()
        openPasswordDialog()
        node("password").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        node("password_confirm").assertIsNotEnabled()
        assertNull(configured)
    }
}
