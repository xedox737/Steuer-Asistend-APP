package com.example.e2e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.AppScreen
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FontScaleEmulatorTest : EmulatorTestSupport() {
    override val fontScale = 1.5f

    @Test fun realLargeFontKeepsDashboardDatevEntryAndPrimaryNavigationUsable() {
        scenario.onActivity { assertEquals(1.5f, it.resources.configuration.fontScale, .01f) }
        visibleTextWithoutOverflow("Willkommen bei ImmoPilot")
        visibleTextWithoutOverflow("Aktueller Stand")
        capture("dashboard-font-1.5")
        clickMore("DATEV Export")
        visibleTextWithoutOverflow("DATEV Export")
        visibleTextWithoutOverflow("Export prüfen")
        visibleTextWithoutOverflow("Noch keine Buchungen vorhanden")
        ui.onNodeWithText("Export vorbereiten").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        ui.onNodeWithContentDescription("Zurück").assertIsDisplayed().performClick()
        ui.onNodeWithText("Export prüfen").assertIsDisplayed()
        capture("datev-font-1.5")
        listOf(AppScreen.RECEIPTS_LIST, AppScreen.ADD_RECEIPT, AppScreen.PROPERTIES,
            AppScreen.MORE, AppScreen.DASHBOARD).forEach { clickTab(it) }
        ui.onNodeWithText("Willkommen bei ImmoPilot").assertIsDisplayed()
    }

    private fun visibleTextWithoutOverflow(text: String) {
        val node = ui.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        assertTrue("Kein Textlayout für $text", layouts.isNotEmpty())
        assertFalse("Zentraler Text bei fontScale=1.5 abgeschnitten: $text", layouts.any { it.hasVisualOverflow })
    }
}
