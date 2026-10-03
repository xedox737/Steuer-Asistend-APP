package com.example.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.example.MainActivity
import com.github.takahirom.roborazzi.captureRoboImage
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
class MoreMenuComposeTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(ui.activity)[ReceiptViewModel::class.java]
    private val groups = linkedMapOf(
        "Finanzen" to listOf("Bank & Kontoauszüge", "Einnahmen & Ausgaben", "Mieteingänge"),
        "Steuern & Auswertung" to listOf("Steuerliche Übersicht", "AfA Gebäude", "Sanierungs-Monitor", "DATEV Export"),
        "Verwaltung" to listOf("Dokumentenakte", "Fahrtenbuch"),
        "KI & Automatisierung" to listOf("Gelernte Regeln"),
        "Daten & Sicherung" to listOf("Backup & Cloud"),
        "Einstellungen" to listOf("App-Einstellungen")
    )
    private val iconColors = mapOf(
        "Bank & Kontoauszüge" to AccentBlue, "Einnahmen & Ausgaben" to CrimsonRed,
        "Mieteingänge" to AccentBlue, "Steuerliche Übersicht" to WarmOrange, "AfA Gebäude" to AccentBlue,
        "Sanierungs-Monitor" to EmeraldGreen, "DATEV Export" to EmeraldGreen,
        "Dokumentenakte" to AccentBlue, "Fahrtenbuch" to AccentBlue,
        "Gelernte Regeln" to Color(0xFF7C3AED), "Backup & Cloud" to EmeraldGreen, "App-Einstellungen" to SlateGray
    )
    private fun openMore() { ui.onNodeWithTag("nav_item_more").performClick(); ui.waitForIdle() }
    private fun menu() = ui.onNodeWithTag("more_menu")
    private fun click(title: String) {
        menu().performScrollToNode(hasTestTag("more_item_$title"))
        ui.onNodeWithTag("more_item_$title").performClick()
    }
    private fun shell() {
        ui.onNodeWithText("ImmoPilot").assertIsDisplayed()
        ui.onNodeWithTag("bottom_navigation").assertIsDisplayed()
    }
    private fun systemBack() { ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }; ui.waitForIdle() }
    private fun assertMenu() { assertEquals(AppScreen.MORE, vm.currentScreen.value); menu().assertExists(); shell() }

    private fun capture(name: String): Set<String> {
        ui.runOnIdle {
            fun redraw(view: android.view.View) {
                if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") {
                    view.javaClass.getMethod("invalidateDescendants").invoke(view)
                }
                view.requestLayout(); view.invalidate()
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) redraw(view.getChildAt(i))
            }
            redraw(ui.activity.window.decorView)
        }
        ui.mainClock.advanceTimeBy(300); ui.waitForIdle()
        val path = "build/reports/more-menu/$name.png"
        onView(isRoot()).captureRoboImage(path)
        val bitmap = android.graphics.BitmapFactory.decodeFile(path)
        val navTop = ui.onNodeWithTag("bottom_navigation").fetchSemanticsNode().boundsInRoot.top
        val checked = mutableSetOf<String>()
        iconColors.forEach { (title, color) ->
            val node = ui.onAllNodesWithTag("more_icon_$title", useUnmergedTree = true).fetchSemanticsNodes().singleOrNull()
            if (node != null && ui.onNodeWithTag("more_item_$title").isDisplayed() && node.boundsInRoot.width > 0 && node.boundsInRoot.height > 0 &&
                node.boundsInRoot.top >= menu().fetchSemanticsNode().boundsInRoot.top && node.boundsInRoot.bottom <= navTop) {
                val bounds = node.boundsInRoot
                val expected = color.toArgb()
                var matching = 0
                for (y in bounds.top.toInt().coerceAtLeast(0) until bounds.bottom.toInt().coerceAtMost(bitmap.height)) {
                    for (x in bounds.left.toInt().coerceAtLeast(0) until bounds.right.toInt().coerceAtMost(bitmap.width)) {
                        val actual = bitmap.getPixel(x, y)
                        if (listOf(android.graphics.Color.red(actual) - android.graphics.Color.red(expected),
                                android.graphics.Color.green(actual) - android.graphics.Color.green(expected),
                                android.graphics.Color.blue(actual) - android.graphics.Color.blue(expected)).all { kotlin.math.abs(it) < 12 }) matching++
                    }
                }
                assertTrue("$title must retain its colored icon in the screenshot", matching > 3)
                checked += title
            }
        }
        bitmap.recycle()
        return checked
    }

    @Test fun taskGroupsOrderMembershipWidthsAndColoredIconsMatchTheRequestedMenu() {
        openMore()
        var commonLeft: Float? = null
        var commonRight: Float? = null
        groups.entries.forEachIndexed { index, (group, entries) ->
            menu().performScrollToIndex(index)
            val card = ui.onNodeWithTag("more_group_$group").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            if (commonLeft == null) { commonLeft = card.left; commonRight = card.right }
            assertEquals(commonLeft!!, card.left, .5f); assertEquals(commonRight!!, card.right, .5f)
            if (index < groups.size - 1) {
                val next = groups.keys.elementAt(index + 1)
                val nextCard = ui.onNodeWithTag("more_group_$next").fetchSemanticsNode().boundsInRoot
                assertTrue("$group must precede $next", card.bottom <= nextCard.top)
            }
            entries.forEach { title ->
                ui.onNode(hasTestTag("more_item_$title") and hasAnyAncestor(hasTestTag("more_group_$group"))).assertExists()
            }
        }
        ui.onNodeWithText("Immobilien verwalten").assertDoesNotExist()
        ui.onNodeWithText("Objekte & Steuern").assertDoesNotExist()
        menu().performScrollToIndex(0); shell()
        val colored = capture("393-overview").toMutableSet()
        menu().performScrollToNode(hasTestTag("more_group_Einstellungen")); shell()
        colored += capture("393-end")
        assertEquals("Both scroll screenshots must cover all twelve colored icons", iconColors.keys, colored)
    }

    @Test fun existingExternalDestinationsAndDatevEntryRemainUnchanged() {
        openMore()
        listOf("Bank & Kontoauszüge" to AppScreen.BANK, "Einnahmen & Ausgaben" to AppScreen.LEDGER,
            "Mieteingänge" to AppScreen.RENT_OVERVIEW, "Steuerliche Übersicht" to AppScreen.TAX_CALCULATOR,
            "DATEV Export" to AppScreen.DATEV_EXPORT, "Dokumentenakte" to AppScreen.DOCUMENTS,
            "Fahrtenbuch" to AppScreen.LOGBOOK).forEach { (title, target) ->
            click(title); ui.waitForIdle(); assertEquals(title, target, vm.currentScreen.value); shell()
            openMore(); assertMenu()
        }
    }

    private fun internalPages(back: Boolean) {
        openMore()
        listOf("AfA Gebäude" to "Gebäude abschreiben", "Sanierungs-Monitor" to "Sanierungs-Monitor",
            "Gelernte Regeln" to "Gelerntes KI-Wissen", "Backup & Cloud" to "Google Drive Backup").forEach { (title, content) ->
            click(title); ui.onNodeWithText(content).assertExists(); shell()
            assertEquals("Internal pages keep the existing More route", AppScreen.MORE, vm.currentScreen.value)
            if (back) systemBack()
            else if (title == "AfA Gebäude") ui.onNodeWithContentDescription("Zurück").performClick()
            else if (title == "Sanierungs-Monitor") ui.onNodeWithContentDescription("Zurück zu Mehr").performClick()
            else ui.onNodeWithText("Zurück zu Mehr").performClick()
            assertMenu()
        }
        systemBack()
        assertEquals("Internal pages must not add duplicate main backstack entries", AppScreen.DASHBOARD, vm.currentScreen.value)
    }
    @Test fun internalPagesReturnToMoreByAndroidBack() = internalPages(true)
    @Test fun internalPagesReturnToMoreByTheirVisibleBackAction() = internalPages(false)

    @Test fun existingSettingsDialogAndDirectPropertiesBottomNavigationRemainReachable() {
        openMore(); click("App-Einstellungen")
        ui.mainClock.advanceTimeByFrame()
        // Inspect the real Android window without forcing native Robolectric's
        // Material-dialog measurement loop. Exercise its actual Back dispatcher.
        ui.runOnUiThread {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            assertNotNull("The existing settings dialog must open", dialog)
            assertTrue(dialog.isShowing)
            @Suppress("DEPRECATION")
            dialog.onBackPressed()
        }
        assertMenu()
        ui.onNodeWithTag("nav_item_properties").performClick()
        assertEquals(AppScreen.PROPERTIES, vm.currentScreen.value)
        ui.onNodeWithTag("properties_overview").assertIsDisplayed(); shell()
    }
}
