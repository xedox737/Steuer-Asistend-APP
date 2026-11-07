package com.example.ui

import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.data.PropertyMetadata
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class PropertyWizardRegressionComposeTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private var dismissals = 0
    private var savedProperty: PropertyMetadata? = null
    private var savedUnits: List<WohneinheitStatus>? = null

    private fun openWizard() {
        val visible = mutableStateOf(true)
        ui.setContent {
            MaterialTheme {
                if (visible.value) PropertyCreationWizard(
                    onDismiss = { dismissals++; visible.value = false },
                    onSave = { property, units, _ ->
                        savedProperty = property
                        savedUnits = units
                        visible.value = false
                    }
                )
            }
        }
        settleWindow()
    }

    private fun settleWindow() {
        ui.mainClock.advanceTimeByFrame()
        ui.runOnUiThread {
            val dialog = ShadowDialog.getLatestDialog()
            assertTrue(dialog.isShowing)
            val density = ui.activity.resources.displayMetrics.density
            dialog.window!!.setLayout((360 * density).toInt(), (700 * density).toInt())
        }
        ui.waitForIdle()
    }

    private fun field(label: String) = ui.onNode(hasText(label) and hasSetTextAction())
    private fun next() = ui.onNodeWithTag("property_wizard_next").performClick()
    private fun systemBack() {
        ui.runOnIdle {
            val dialog = ShadowDialog.getShownDialogs().filterIsInstance<ComponentDialog>().lastOrNull { it.isShowing }
            assertNotNull("Expected the active wizard dialog", dialog)
            dialog!!.onBackPressedDispatcher.onBackPressed()
        }
        ui.waitForIdle()
    }

    private fun enterFirstStep() {
        field("Objektname").performTextReplacement("MFH Lindenstraße")
        field("Straße und Hausnummer").performScrollTo().performTextReplacement("Lindenstraße 12")
        next()
        ui.onNodeWithText("Immobilie anlegen · 2/5").assertIsDisplayed()
    }

    @Test fun systemBackGoesOneStepBackAndPreservesObjectAndEightUnitDraft() {
        openWizard()
        enterFirstStep()
        field("Wohnfläche m²").performTextReplacement("620")
        field("Anzahl Einheiten").performTextReplacement("8")
        next()
        ui.waitUntil(10000) {
            ui.onAllNodesWithText("Immobilie anlegen · 3/5").fetchSemanticsNodes().isNotEmpty()
        }
        systemBack()
        ui.onNodeWithText("Immobilie anlegen · 2/5").assertIsDisplayed()
        field("Wohnfläche m²").assertTextContains("620")
        field("Anzahl Einheiten").assertTextContains("8")
        systemBack()
        ui.onNodeWithText("Immobilie anlegen · 1/5").assertIsDisplayed()
        field("Objektname").assertTextContains("MFH Lindenstraße")
        field("Straße und Hausnummer").performScrollTo().assertTextContains("Lindenstraße 12")
        assertEquals(0, dismissals)
        assertNull(savedProperty)
    }

    @Test fun leavingChangedFirstStepRequiresExplicitDiscardAndContinueKeepsInput() {
        openWizard()
        field("Objektname").performTextReplacement("Unfertiges Haus")
        systemBack()
        settleWindow()
        ui.onNodeWithText("Entwurf verwerfen?").assertIsDisplayed()
        assertEquals(0, dismissals)
        ui.onNodeWithText("Weiter bearbeiten").performClick()
        field("Objektname").assertTextContains("Unfertiges Haus")
        assertEquals(0, dismissals)
        systemBack()
        ui.onNodeWithText("Verwerfen").performClick()
        assertEquals(1, dismissals)
        assertNull(savedProperty)
        ui.onNodeWithText("Immobilie anlegen · 1/5").assertDoesNotExist()
    }

    @Test fun eightMissingUnitAreasAreFlaggedBeforeSaveWithoutBlockingIntentionalIncompleteDraft() {
        openWizard()
        enterFirstStep()
        field("Wohnfläche m²").performTextReplacement("620")
        field("Anzahl Einheiten").performTextReplacement("8")
        next()
        ui.waitUntil(10000) {
            ui.onAllNodesWithText("Immobilie anlegen · 3/5").fetchSemanticsNodes().isNotEmpty()
        }
        next()
        ui.onNodeWithText("Immobilie anlegen · 4/5").assertIsDisplayed()
        next()
        ui.onNodeWithText("Immobilie anlegen · 5/5").assertIsDisplayed()
        ui.onNode(hasText("Flächen prüfen:", substring = true)).performScrollTo()
            .assertTextContains("620", substring = true)
            .assertTextContains("Einheiten 0", substring = true)
        ui.onNode(hasText("8 Einheiten ohne Wohnfläche.", substring = true)).performScrollTo().assertIsDisplayed()
        assertNull(savedProperty)
        next()
        assertEquals(620.0, savedProperty!!.wohnflaeche, .001)
        assertEquals(8, savedUnits!!.size)
        assertTrue(savedUnits!!.all { it.wohnflaeche == 0.0 })
        assertEquals(8, savedUnits!!.map { it.unitId }.distinct().size)
        assertEquals(0, dismissals)
    }
}
