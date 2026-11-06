package com.example.e2e

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.BankAccount
import com.example.data.BankReceiptLink
import com.example.data.BankTransaction
import com.example.ui.AppScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiptOverviewAssignmentEmulatorTest : EmulatorTestSupport() {
    @Test fun persistedAssignmentAppearsInEveryOverviewAndDisappearsAfterUnlink() {
        val property = seedProperty("receipt-status-property", "Testobjekt")
        val receipt = expense(1881, 119.0, property.propertyId, "2026-10-01")
        val link = BankReceiptLink("receipt-status-link", "receipt-status-tx", receipt.id, receipt.internalId, 119.0)
        runBlocking(Dispatchers.IO) {
            db.receiptDao().insertReceipt(receipt)
            db.bankDao().upsertAccount(BankAccount("receipt-status-account", "Synthetisches Konto"))
            db.bankDao().upsertTransaction(BankTransaction(link.transactionId, "receipt-status-account", "2026-10-02", amount = -119.0))
            db.bankDao().upsertLink(link)
        }
        val model = vm
        ui.waitUntil(10_000) { model.receipts.value.size == 1 && model.bankReceiptLinks.value.size == 1 }
        clickTab(AppScreen.RECEIPTS_LIST)
        showAssignment()
        ui.onNodeWithText("Raster").performScrollTo().performClick()
        showAssignment()
        ui.onNodeWithText("Tabelle").performScrollTo().performClick()
        showAssignment()
        ui.onNodeWithText("Liste").performScrollTo().performClick()
        scenario.recreate()
        showAssignment()
        capture("receipt-overview-assigned")
        ui.runOnIdle { vm.removeBankReceiptLink(link.linkId, link.transactionId) }
        ui.waitUntil(10_000) { vm.bankReceiptLinks.value.isEmpty() }
        assignedBadge().assertDoesNotExist()
        ui.onNodeWithTag("receipt_item_${receipt.id}").performScrollTo().assertIsDisplayed()
        runBlocking(Dispatchers.IO) {
            assertEquals(receipt, db.receiptDao().getReceiptById(receipt.id))
        }
    }

    private fun assignedBadge() = ui.onNodeWithText("Zugeordnet", useUnmergedTree = true)

    private fun showAssignment() {
        // The table also scrolls horizontally. Scroll the overview vertically first.
        ui.onNode(hasScrollAction() and hasAnyDescendant(hasText("Liste")))
            .performScrollToNode(hasText("Zugeordnet"))
        assignedBadge().assertIsDisplayed()
    }
}
