package com.example.e2e

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.AppScreen
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationBackstackEmulatorTest : EmulatorTestSupport() {
    @Test fun realSystemBackAndAppArrowReturnFromUnitDetailToUnitsBeforeProperty() {
        val property = seedProperty()
        openUnits(property.propertyId)
        openUnitDetail()
        ui.onNodeWithText("Wohneinheit im Überblick").assertIsDisplayed()
        capture("unit-detail")

        systemBack()
        assertUnitsInsteadOfDashboard()
        openUnitDetail()
        ui.onNodeWithText("Wohneinheit im Überblick").assertIsDisplayed()
        ui.onNodeWithContentDescription("Zurück").performClick()
        assertUnitsInsteadOfDashboard()
        capture("unit-list-after-back")

        // A second genuine BACK must close the list, not reopen a departed detail.
        systemBack()
        ui.onNodeWithText("Notizen & Aufgaben").assertExists()
        assertEquals(AppScreen.PROPERTIES, vm.currentScreen.value)
        ui.onNodeWithText("Wohneinheit im Überblick").assertDoesNotExist()
    }

    private fun openUnitDetail() {
        ui.onNodeWithTag("property_units_overview").performScrollToNode(hasText("Testwohnung"))
        ui.onNodeWithText("Testwohnung").assertIsDisplayed().performClick()
    }

    private fun assertUnitsInsteadOfDashboard() {
        ui.onNodeWithTag("property_units_overview").assertIsDisplayed()
        ui.onNodeWithText("Wohneinheit im Überblick").assertDoesNotExist()
        ui.onNodeWithText("Willkommen bei ImmoPilot").assertDoesNotExist()
        ui.onNodeWithTag("nav_item_properties").assertIsSelected()
        assertEquals(AppScreen.PROPERTIES, vm.currentScreen.value)
    }
}
