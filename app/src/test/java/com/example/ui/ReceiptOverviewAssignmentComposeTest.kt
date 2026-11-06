package com.example.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.BankReceiptLink
import com.example.data.Receipt
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
class ReceiptOverviewAssignmentComposeTest {
    @get:Rule val ui = createComposeRule()
    private val receipt = Receipt(id = 15, internalId = "stable", aussteller = "Testhandwerk",
        datum = "2026-01-04", uhrzeit = "", bruttobetrag = 650.0,
        hauptkategorie = "Anschaffungskosten", unterkategorie = "Notarkosten", kontoNr = "",
        beschreibung = "Testbeleg")
    private val link = BankReceiptLink(linkId = "link", transactionId = "tx", receiptId = 15,
        receiptInternalId = "stable", allocatedAmount = 650.0)

    @Test fun listUpdatesWhenAssignedAndUnlinkedAndKeepsActions() {
        val links = mutableStateOf(emptyList<BankReceiptLink>())
        var opened = 0
        var deleted = 0
        ui.setContent {
            val currentLinks by links
            val assignments = remember(currentLinks) { ReceiptBankAssignmentIndex(currentLinks) }
            MaterialTheme {
                ReceiptRowItem(receipt, { opened++ }, { deleted++ },
                    isBankAssigned = assignments.isAssigned(receipt))
            }
        }
        ui.onNodeWithText("Zugeordnet").assertDoesNotExist()
        ui.runOnIdle { links.value = listOf(link) }
        ui.onNodeWithText("Zugeordnet", useUnmergedTree = true).assertIsDisplayed()
        ui.onNodeWithTag("receipt_item_15").performClick()
        ui.onNodeWithTag("delete_receipt_15").performClick()
        ui.runOnIdle { assertEquals(1, opened); assertEquals(1, deleted) }
        ui.runOnIdle { links.value = emptyList() }
        ui.onNodeWithText("Zugeordnet").assertDoesNotExist()
        ui.onNodeWithText("Testbeleg", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun gridShowsAssignmentWithoutHidingExistingContent() {
        val assigned = mutableStateOf(true)
        ui.setContent {
            MaterialTheme {
                ReceiptGridCard(receipt, {}, {}, {}, isBankAssigned = assigned.value)
            }
        }
        ui.onNodeWithText("Zugeordnet", useUnmergedTree = true).assertIsDisplayed()
        // Both the synthetic thumbnail and the card metadata show the issuer.
        val issuers = ui.onAllNodesWithText("Testhandwerk", useUnmergedTree = true).assertCountEquals(2)
        issuers[0].assertIsDisplayed()
        issuers[1].assertIsDisplayed()
        ui.onNodeWithTag("preview_receipt_15").assertExists()
        ui.onNodeWithTag("delete_receipt_15").assertExists()
        ui.runOnIdle { assigned.value = false }
        ui.onNodeWithText("Zugeordnet").assertDoesNotExist()
    }
}
