package com.example.e2e

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import com.example.ui.AppScreen
import com.example.util.DatevMappingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Real editor, classification actions, Room and financial screen on the CI emulator. */
@RunWith(AndroidJUnit4::class)
class Phase6AConsistencyEmulatorTest : EmulatorTestSupport() {
    private val propertyA = "mw-a"
    private val propertyB = "mw-b"
    private val receiptId = 961

    private fun seedTwoUnits() {
        val property = seedProperty(propertyA, "Lindenstraße")
        runBlocking(Dispatchers.IO) {
            check(context.getSharedPreferences("wohneinheiten_prefs", 0).edit()
                .putString("property_${propertyA}_unit_WE 02_id", "$propertyA-unit-02")
                .putString("property_${propertyA}_unit_id_index_1", "$propertyA-unit-02").commit())
            db.propertyDao().insertPropertyMetadata(property.copy(wohneinheiten = "WE 01, WE 02"))
        }
        val model = vm
        ui.waitUntil(10_000) { model.properties.value.any { it.propertyId == propertyA && it.wohneinheiten == "WE 01, WE 02" } }
    }

    private fun seedReceipt(approved: Boolean = false, general: Boolean = false): Receipt {
        val receipt = expense(receiptId, 119.0, propertyA, LocalDate.now().toString())
            .copy(unitId = if (general) "" else "$propertyA-unit", wohneinheit = if (general) "" else "WE 01")
        val saved = if (approved) DatevMappingService.confirmDatevPreview(receipt,
            DatevMappingService.buildDatevBookingRows(receipt, DatevProfile()))!! else receipt
        runBlocking(Dispatchers.IO) { db.receiptDao().insertReceipt(saved) }
        val model = vm
        ui.waitUntil(10_000) { model.receipts.value.any { it.id == receiptId } }
        return saved
    }

    private fun openReceipt() {
        clickTab(AppScreen.RECEIPTS_LIST)
        ui.onNodeWithTag("receipts_list").performScrollToNode(hasTestTag("receipt_item_$receiptId"))
        ui.onNodeWithTag("receipt_item_$receiptId").performClick()
        ui.onNodeWithTag("receipt_detail_screen").assertIsDisplayed()
    }
    private fun edit() = ui.onNodeWithTag("edit_receipt_button").performScrollTo().performClick()
    private fun selectUnit(name: String) {
        ui.onNodeWithTag("edit_receipt_wohneinheit_dropdown").performScrollTo().performClick()
        ui.onNode(hasText(name) and hasAnyAncestor(isPopup())).performClick()
        ui.onNodeWithTag("edit_receipt_wohneinheit_dropdown").assertTextContains(name)
    }
    private fun save() = ui.onNodeWithTag("save_edited_receipt_button").performScrollTo().assertIsEnabled().performClick()
    private fun savedReceipt() = runBlocking(Dispatchers.IO) { db.receiptDao().getReceiptById(receiptId)!! }
    private fun metric(title: String, value: String) {
        ui.onNodeWithTag("ledger_overview").performScrollToNode(hasTestTag("ledger_metric_$title"))
        ui.onNode(hasText(value) and hasAnyAncestor(hasTestTag("ledger_metric_$title"))).assertIsDisplayed()
    }

    @Test fun mw01UnitChangeSurvivesSaveReopenRecreationAndCancelBackPaths() {
        seedTwoUnits()
        seedReceipt()
        openReceipt(); edit(); selectUnit("WE 02")
        capture("mw01-editor-after-unit-change")
        save()
        val model = vm
        ui.waitUntil(10_000) { model.receipts.value.single().unitId == "$propertyA-unit-02" }
        assertEquals("WE 02", savedReceipt().wohneinheit)
        systemBack()
        assertEquals(AppScreen.RECEIPTS_LIST, vm.currentScreen.value)
        openReceipt(); edit()
        ui.onNodeWithTag("edit_receipt_wohneinheit_dropdown").performScrollTo().assertTextContains("WE 02")
        systemBack() // Editing -> details, without saving.
        scenario.recreate()
        ui.onNodeWithTag("receipt_detail_screen").assertIsDisplayed()
        edit()
        ui.onNodeWithTag("edit_receipt_wohneinheit_dropdown").performScrollTo().assertTextContains("WE 02")
        capture("mw01-editor-after-activity-reload")
        selectUnit("WE 01")
        ui.onNode(hasText("Abbrechen") and hasClickAction()).performScrollTo().performClick()
        assertEquals("$propertyA-unit-02", savedReceipt().unitId)
        edit(); selectUnit("WE 01")
        ui.onNodeWithContentDescription("Zurück").performClick() // App-back also cancels the editor.
        assertEquals("$propertyA-unit-02", savedReceipt().unitId)
        ui.onNodeWithContentDescription("Zurück").performClick()
        assertEquals(AppScreen.RECEIPTS_LIST, vm.currentScreen.value)
        ui.onNodeWithTag("receipts_list").assertIsDisplayed()
    }

    @Test fun mw02ApprovedGeneralReceiptPropertyChangeVisiblyRequiresNewApproval() {
        seedProperty(propertyA, "Lindenstraße")
        seedProperty(propertyB, "Parkstraße")
        seedReceipt(approved = true, general = true)
        openReceipt()
        ui.onNodeWithTag("receipt_more_data").performScrollTo().performClick()
        ui.onNodeWithTag("receipt_approval_status").performScrollTo().assertTextEquals("DATEV-Freigabe: Freigegeben")
        edit()
        ui.onNodeWithTag("edit_receipt_property").performScrollTo().performClick()
        ui.onNode(hasText("Parkstraße") and hasAnyAncestor(isPopup())).performClick()
        save()
        val model = vm
        ui.waitUntil(10_000) { model.receipts.value.single().propertyId == propertyB && model.receipts.value.single().freigabestatus == "OFFEN" }
        val saved = savedReceipt()
        assertEquals("", saved.unitId)
        assertEquals("ZU_PRUEFEN", saved.pruefstatus)
        assertTrue(DatevMappingService.buildConfirmedDatevBookingRows(saved, DatevProfile()).isEmpty())
        ui.onNodeWithTag("receipt_approval_status").performScrollTo().assertTextEquals("DATEV-Freigabe: Erneut zu prüfen")
        capture("mw02-approval-reset-after-property-change")
    }

    @Test fun mw04PrivateLinkedBankPaymentUpdatesFinancialViewAndResetCountsOnce() {
        seedProperty(propertyA, "Lindenstraße")
        val receipt = seedReceipt(approved = true)
        val today = LocalDate.now().toString()
        runBlocking(Dispatchers.IO) {
            db.bankDao().upsertAccount(BankAccount("mw-account", "Synthetisches Testkonto"))
            db.bankDao().upsertTransaction(BankTransaction("mw-payment", "mw-account", today, amount = -119.0,
                counterparty = "MW Testhandwerk", purpose = "Reparatur", propertyId = propertyA,
                unitId = "$propertyA-unit", category = "Reparatur", reconciliationStatus = BankReconciliationStatus.MATCHED))
            db.bankDao().upsertLink(BankReceiptLink("mw-link", "mw-payment", receiptId, receipt.internalId, 119.0))
        }
        val model = vm
        ui.waitUntil(10_000) { model.bankReceiptLinks.value.size == 1 && model.bankTransactions.value.size == 1 }
        clickMore("Einnahmen & Ausgaben")
        metric("Ausgaben", "119,00 €"); metric("Buchungen", "1")
        clickMore("Bank & Kontoauszüge")
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("MW Testhandwerk"))
        ui.onNodeWithText("MW Testhandwerk").performClick()
        ui.onNodeWithText("Buchungsdetails").assertIsDisplayed()
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Buchung ignorieren"))
        ui.onNodeWithText("Buchung ignorieren").performClick()
        ui.waitUntil(10_000) { model.bankTransactions.value.single().classification == BankTransactionClassification.PRIVATE_IGNORED }
        systemBack()
        clickMore("Einnahmen & Ausgaben")
        metric("Ausgaben", "0,00 €"); metric("Buchungen", "0")
        capture("mw04-private-payment-financial-view")
        openReceipt()
        ui.onNodeWithTag("receipt_more_data").performScrollTo().performClick()
        ui.onNodeWithTag("receipt_bank_classification_conflict").performScrollTo()
            .assertTextEquals("Bankzuordnung prüfen: Mit privater/ignorierter Bankbuchung verknüpft – kein DATEV-Export.")
        capture("mw04-receipt-bank-classification-conflict")
        clickMore("Bank & Kontoauszüge")
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("MW Testhandwerk"))
        ui.onNodeWithText("MW Testhandwerk").performClick()
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Privat aufheben"))
        ui.onNodeWithText("Privat aufheben").performClick()
        ui.waitUntil(10_000) { model.bankTransactions.value.single().classification == BankTransactionClassification.NORMAL }
        systemBack(); clickMore("Einnahmen & Ausgaben")
        metric("Ausgaben", "119,00 €"); metric("Buchungen", "1")
    }
}
