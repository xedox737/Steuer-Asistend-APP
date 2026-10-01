package com.example.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.example.MainActivity
import com.example.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class MainNavigationRegressionTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(ui.activity)[ReceiptViewModel::class.java]
    private fun database() = AppDatabase.getDatabase(ui.activity.application as Application, CoroutineScope(Dispatchers.IO))
    private fun systemBack() { ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }; ui.waitForIdle() }
    private fun assertShell() { ui.onNodeWithText("ImmoPilot").assertExists(); ui.onNodeWithTag("bottom_navigation").assertIsDisplayed() }
    private fun seedProperty() {
        runBlocking { database().propertyDao().insertPropertyMetadata(PropertyMetadata(id = 81,
            propertyId = "nav-property", name = "Navigationstest-Objekt", wohneinheiten = "WE 1")) }
        ui.waitUntil(10000) { vm.properties.value.any { it.propertyId == "nav-property" } }
    }

    @Test fun dashboardPropertySubsectionReturnsToPropertyThenOverviewWithBothBackActions() {
        seedProperty()
        ui.onNodeWithTag("nav_item_properties").performClick()
        ui.onNodeWithTag("properties_overview").assertIsDisplayed()
        ui.onNodeWithTag("property_card_nav-property").performScrollTo().performClick()
        ui.onNodeWithText("Notizen & Aufgaben").performScrollTo().performClick()
        ui.onNodeWithText("Aufgaben & Fristen").assertExists()
        systemBack()
        ui.onNodeWithText("Notizen & Aufgaben").assertExists()
        assertEquals(AppScreen.PROPERTIES, vm.currentScreen.value)
        ui.onNodeWithContentDescription("Zurück").performClick()
        ui.onNodeWithTag("properties_overview").assertIsDisplayed()
        ui.onNodeWithTag("property_card_nav-property").performScrollTo().performClick()
        ui.onNodeWithText("Notizen & Aufgaben").performScrollTo().performClick()
        ui.onNodeWithContentDescription("Zurück").performClick()
        ui.onNodeWithText("Notizen & Aufgaben").assertExists()
        systemBack()
        ui.onNodeWithTag("properties_overview").assertIsDisplayed()
        assertShell()
    }

    @Test fun bankDetailsReturnToBankListBySystemBackAndArrow() {
        runBlocking {
            database().bankDao().upsertAccount(BankAccount("nav-account", "Testkonto"))
            database().bankDao().upsertTransaction(BankTransaction("nav-transaction", "nav-account", "2026-10-01",
                amount = -12.0, counterparty = "Navigationstest-Buchung", purpose = "Test"))
        }
        ui.runOnIdle { vm.setScreen(AppScreen.BANK) }
        ui.waitUntil(10000) { vm.bankTransactions.value.any { it.transactionId == "nav-transaction" } }
        ui.onNodeWithText("Navigationstest-Buchung").performScrollTo().performClick()
        ui.onNodeWithText("Buchungsdetails").assertExists()
        systemBack()
        ui.onNodeWithText("Buchungsdetails").assertDoesNotExist()
        assertEquals(AppScreen.BANK, vm.currentScreen.value)
        ui.onNodeWithText("Navigationstest-Buchung").performScrollTo().performClick()
        ui.onNodeWithContentDescription("Zurück").performClick()
        ui.onNodeWithText("Buchungsdetails").assertDoesNotExist()
        assertEquals(AppScreen.BANK, vm.currentScreen.value)
        assertShell()
    }

    @Test fun receiptEditorAndDetailBackMustNotReinsertClosedDetailsIntoActivityHistory() {
        runBlocking { database().receiptDao().insertReceipt(Receipt(id = 900, aussteller = "Navigationstest-Beleg",
            datum = "2026-10-01", uhrzeit = "", bruttobetrag = 10.0, hauptkategorie = "Sonstiges",
            unterkategorie = "", kontoNr = "", beschreibung = "Test", internalId = "nav-receipt")) }
        ui.waitUntil(10000) { vm.receipts.value.any { it.id == 900 } }
        ui.onNodeWithTag("nav_item_receipts_list").performClick()
        ui.runOnIdle { vm.openReceiptDetail(900) }
        ui.onNodeWithText("Bearbeiten").performScrollTo().performClick()
        ui.onNodeWithTag("receipt_inline_editor").assertExists()
        systemBack()
        ui.onNodeWithTag("receipt_inline_editor").assertDoesNotExist()
        assertEquals(AppScreen.RECEIPT_DETAIL, vm.currentScreen.value)
        ui.onNodeWithContentDescription("Zurück").performClick()
        assertEquals(AppScreen.RECEIPTS_LIST, vm.currentScreen.value)
        systemBack()
        assertEquals("Closed details must not reappear in main history", AppScreen.DASHBOARD, vm.currentScreen.value)
        assertShell()
    }

    @Test fun afaPropertyBackReturnsToAfaOverviewThenMore() {
        seedProperty()
        ui.onNodeWithTag("nav_item_more").performClick()
        ui.onNodeWithText("AfA Gebäude").performScrollTo().performClick()
        ui.onNodeWithText("Navigationstest-Objekt").performScrollTo().performClick()
        systemBack()
        ui.onNodeWithText("Gebäude abschreiben").assertExists()
        ui.onNodeWithText("Navigationstest-Objekt").performScrollTo().performClick()
        ui.onNodeWithContentDescription("Zurück").performScrollTo().performClick()
        ui.onNodeWithText("Gebäude abschreiben").assertExists()
        systemBack()
        assertEquals(AppScreen.MORE, vm.currentScreen.value)
        ui.onNodeWithText("AfA Gebäude").assertExists()
        assertShell()
    }
}
