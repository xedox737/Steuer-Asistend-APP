package com.example.e2e

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.AppScreen
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImmoPilotEmulatorSmokeTest : EmulatorTestSupport() {
    @Test fun appStartsWithDashboardAndAllFivePrimaryDestinationsAreUsable() {
        ui.onNodeWithText("Willkommen bei ImmoPilot").assertIsDisplayed()
        ui.onNodeWithText("Aktueller Stand").assertIsDisplayed()
        capture("dashboard")

        clickTab(AppScreen.RECEIPTS_LIST)
        ui.onNodeWithText("Belege mit KI durchsuchen").assertExists()
        clickTab(AppScreen.ADD_RECEIPT)
        ui.onNodeWithText("Beleg erfassen (KI & Manuell)").assertExists()
        ui.onNodeWithTag("scan_camera_button").assertExists()
        clickTab(AppScreen.PROPERTIES)
        ui.onNodeWithTag("properties_overview").assertIsDisplayed()
        ui.onNodeWithTag("add_property_button").assertIsDisplayed()
        clickTab(AppScreen.MORE)
        ui.onNodeWithTag("more_menu").assertIsDisplayed()
        clickTab(AppScreen.DASHBOARD)
        ui.onNodeWithText("Willkommen bei ImmoPilot").assertIsDisplayed()
        ui.onNodeWithTag("bottom_navigation").assertIsDisplayed()
    }
}
