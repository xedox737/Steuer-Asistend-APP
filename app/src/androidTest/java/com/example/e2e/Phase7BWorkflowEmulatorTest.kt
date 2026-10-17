package com.example.e2e

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import com.example.ui.AppScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase7BObjectLedgerEmulatorTest : EmulatorTestSupport() {
    @Test fun objectPaymentFlowUsesSharedEntriesAndReturnsFromReceiptAndBankToSameObject() {
        val a = seedProperty("p7b-a", "Gleiches Haus")
        val b = seedProperty("p7b-b", "Gleiches Haus")
        runBlocking(Dispatchers.IO) {
            db.receiptDao().insertReceipt(expense(981, 500.0, a.propertyId, "2026-10-01").copy(
                aussteller = "Handwerker A", hauptkategorie = "Reparaturen", unterkategorie = "Heizung"))
            db.receiptDao().insertReceipt(expense(982, 700.0, b.propertyId, "2026-10-01"))
            db.receiptDao().insertReceipt(expense(983, 50.0, a.propertyId, "2025-10-01"))
            db.bankDao().upsertAccount(BankAccount("p7b-bank", "Synthetisches Konto"))
            listOf(payment("direct-a", a.propertyId, -1200.0), payment("income-a", a.propertyId, 200.0),
                payment("linked-a", a.propertyId, -500.0).copy(reconciliationStatus = BankReconciliationStatus.MATCHED),
                payment("income-b", b.propertyId, 300.0)).forEach { db.bankDao().upsertTransaction(it) }
            db.bankDao().upsertLink(BankReceiptLink("p7b-link", "linked-a", 981, "", 500.0))
        }
        val model = vm
        ui.waitUntil(10_000) { model.receipts.value.size == 3 && model.bankTransactions.value.size == 4 && model.bankReceiptLinks.value.size == 1 }
        open(a.propertyId)
        capture("p7b-object-a")
        incomeExpense()
        metric("Einnahmen", "200,00 €"); metric("Ausgaben", "1.700,00 €"); metric("Buchungen", "3")
        capture("p7b-object-a-ledger")
        // A changed global selection must never alter the scope of the open object.
        ui.runOnIdle { model.selectProperty(b.propertyId) }
        ui.waitUntil(10_000) { model.propertyMetadata.value?.propertyId == b.propertyId }
        scroll("ledger_property"); ui.onNodeWithTag("ledger_property").performClick()
        ui.onNodeWithText("Alle Immobilien").assertDoesNotExist()
        systemBack() // only the property popup
        scroll("ledger_end")
        ui.onNodeWithTag("ledger_bank_bank-income-a").assertIsDisplayed()
        ui.onNodeWithTag("ledger_bank_bank-direct-a").assertIsDisplayed()
        ui.onNodeWithTag("ledger_bank_bank-income-b").assertDoesNotExist()
        ui.onNodeWithTag("ledger_receipt_982").assertDoesNotExist()
        ui.onNodeWithTag("ledger_receipt_981").performClick()
        assertEquals(AppScreen.RECEIPT_DETAIL, model.currentScreen.value)
        capture("p7b-receipt-detail")
        systemBack()
        assertEquals(AppScreen.PROPERTIES, model.currentScreen.value)
        scroll("ledger_receipt_981"); capture("p7b-return-object-a-ledger")
        ui.onNodeWithTag("ledger_receipt_981").performClick()
        ui.onNodeWithContentDescription("Zurück").performClick()
        scroll("ledger_property"); ui.onNodeWithTag("ledger_property").assertTextContains("Gleiches Haus")
        scroll("ledger_category"); ui.onNodeWithTag("ledger_category").performClick()
        ui.onNode(hasText("Heizung") and hasAnyAncestor(isPopup())).performClick()
        metric("Ausgaben", "500,00 €"); metric("Buchungen", "1")
        scroll("ledger_search"); ui.onNodeWithTag("ledger_search").performTextReplacement("Reparaturen")
        // Finish the search input before testing screen back; otherwise Android Back first dismisses the IME.
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        ui.waitForIdle()
        scroll("ledger_receipt_981"); ui.onNodeWithTag("ledger_receipt_981").performClick()
        assertEquals(AppScreen.RECEIPT_DETAIL, model.currentScreen.value)
        systemBack()
        assertEquals(AppScreen.PROPERTIES, model.currentScreen.value)
        scroll("ledger_search"); ui.onNodeWithTag("ledger_search").assertTextContains("Reparaturen")
        ui.onNodeWithTag("ledger_search").performTextClearance()
        scroll("ledger_category"); ui.onNodeWithTag("ledger_category").performClick()
        ui.onNodeWithText("Alle Kategorien").performClick()
        scroll("ledger_year"); ui.onNodeWithTag("ledger_year").performClick()
        ui.onNode(hasText("2025") and hasAnyAncestor(isPopup())).performClick()
        metric("Ausgaben", "50,00 €"); metric("Buchungen", "1")
        scenario.recreate()
        metric("Ausgaben", "50,00 €") // year and object remain saved
        systemBack(); ui.onNode(hasText("Einnahmen / Ausgaben") and hasClickAction()).assertExists()
        systemBack(); ui.onNodeWithTag("properties_overview").assertIsDisplayed()
        ui.onNodeWithTag("property_card_${b.propertyId}").performScrollTo().performClick(); incomeExpense()
        metric("Einnahmen", "300,00 €"); metric("Ausgaben", "700,00 €"); metric("Buchungen", "2")
        scroll("ledger_end"); ui.onNodeWithTag("ledger_receipt_981").assertDoesNotExist()
        ui.onNodeWithTag("ledger_bank_bank-income-b").performClick()
        ui.onNodeWithText("Buchungsdetails").assertIsDisplayed()
        systemBack(); scroll("ledger_property"); ui.onNodeWithTag("ledger_property").assertTextContains("Gleiches Haus")
        ui.onNodeWithContentDescription("Zurück").performClick()
        ui.onNode(hasText("Einnahmen / Ausgaben") and hasClickAction()).assertExists()
    }
    private fun payment(id: String, property: String, amount: Double) = BankTransaction(id, "p7b-bank", "2026-10-02",
        amount = amount, propertyId = property, counterparty = id, category = if (amount > 0) "Sonstige Einnahmen" else "Versicherung",
        reconciliationStatus = BankReconciliationStatus.NO_RECEIPT_REQUIRED, noReceiptReason = "Sonstiges")
    private fun open(id: String) { clickTab(AppScreen.PROPERTIES); ui.onNodeWithTag("property_card_$id").performScrollTo().performClick() }
    private fun incomeExpense() = ui.onNode(hasText("Einnahmen / Ausgaben") and hasClickAction()).performScrollTo().performClick()
    private fun scroll(tag: String) = ui.onNodeWithTag("ledger_overview").performScrollToNode(hasTestTag(tag))
    private fun metric(title: String, value: String) { scroll("ledger_metric_$title"); ui.onNode(hasText(value) and hasAnyAncestor(hasTestTag("ledger_metric_$title"))).assertIsDisplayed() }
}

/** Real dialog, IME, shared calendar, unit stores and guarded persistence at the emulator's ~393 dp. */
abstract class Phase7BBatchMatrix : EmulatorTestSupport() {
    @Test fun twentyUnitsKeepValuesWhileScrollingAndProtectExistingContracts() {
        val property = seedProperty("p7b-rent", "Mietobjekt")
        val originalUnitId = vm.getWohneinheitenForProperty(property).single().unitId
        val expanded = property.copy(wohneinheiten = UnitIdentity.encode((1..20).map {
            val name = "WE ${it.toString().padStart(2, '0')}"
            UnitIdentity.Entry(if (it == 1) originalUnitId else StableDocumentIdentity.legacyUnitId(property.propertyId, name), name)
        }))
        runBlocking(Dispatchers.IO) { db.propertyDao().insertPropertyMetadata(expanded) }
        val model = vm
        ui.waitUntil(10_000) { model.properties.value.any { it.propertyId == property.propertyId && it.wohneinheiten == expanded.wohneinheiten } }
        openUnits(property.propertyId)
        ui.waitUntil(10_000) { model.wohneinheitenStatus.value.size == 20 }
        val units = model.wohneinheitenStatus.value
        val first = units.first().unitId; val last = units.last().unitId
        ui.onNodeWithTag("rent_batch_open").performClick()
        ui.onNodeWithTag("rent_batch_vertical_$first").assertExists()
        ui.onNodeWithTag("rent_batch_table_$first").assertDoesNotExist()
        capture("p7b-rent-multiple-font-$fontScale")
        field("tenant", first).performScrollTo().performTextReplacement("Max Mustermann")
        // Do not close the IME yet: another field must remain reachable while it is open.
        field("cold", first).performScrollTo().performTextReplacement("760,50")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        field("utilities", first).performScrollTo().performTextReplacement("220,25")
        field("other", first).performScrollTo().performTextReplacement("25,00")
        selectCalendarDate("rent_batch_start_$first", "01.01.2026", "1")
        ui.onNodeWithTag("rent_batch_status_$first").performScrollTo().performClick()
        ui.onNode(hasText("Vermietet") and hasAnyAncestor(isPopup())).performClick()
        ui.onNodeWithTag("rent_batch_rows").performScrollToNode(hasTestTag("rent_batch_select_$last"))
        ui.onNodeWithTag("rent_batch_select_$last").performClick()
        ui.onNodeWithTag("rent_batch_rows").performScrollToNode(hasTestTag("rent_batch_select_$first"))
        field("tenant", first).performScrollTo().assertTextContains("Max Mustermann")
        field("cold", first).performScrollTo().assertTextContains("760,50")
        assertEquals("", model.getWohneinheitenForProperty(expanded).first().mieter) // draft only
        ui.onNodeWithTag("rent_batch_confirm").assertIsDisplayed().performClick()
        ui.onNodeWithText("2 Einheiten speichern?").assertIsDisplayed()
        capture("p7b-rent-preview-font-$fontScale")
        ui.onNodeWithTag("rent_batch_confirm").performClick()
        ui.waitUntil(10_000) { model.wohneinheitenStatus.value.first().mieter == "Max Mustermann" }
        assertEquals(first, model.wohneinheitenStatus.value.first().unitId)
        assertEquals(760.5, model.wohneinheitenStatus.value.first().kaltmiete, .001)
        assertEquals("", model.wohneinheitenStatus.value.last().mieter)
        ui.onNodeWithTag("rent_batch_open").performClick()
        ui.onNodeWithTag("rent_batch_select_$first").assertIsNotEnabled()
        field("tenant", first).assertDoesNotExist()
        capture("p7b-rent-existing-protected-font-$fontScale")
        systemBack()
        scenario.recreate()
        assertEquals("Max Mustermann", vm.getWohneinheitenForProperty(expanded).first().mieter)
    }
    private fun field(name: String, id: String) = ui.onNodeWithTag("rent_batch_${name}_$id")
}
@RunWith(AndroidJUnit4::class) class Phase7BBatch10EmulatorTest : Phase7BBatchMatrix() { override val fontScale = 1f }
@RunWith(AndroidJUnit4::class) class Phase7BBatch13EmulatorTest : Phase7BBatchMatrix() { override val fontScale = 1.3f }
@RunWith(AndroidJUnit4::class) class Phase7BBatch15EmulatorTest : Phase7BBatchMatrix() { override val fontScale = 1.5f }

@RunWith(AndroidJUnit4::class)
class Phase7BLoanNumbersEmulatorTest : EmulatorTestSupport() {
    /** Same editor/save assertions as the JVM probe, now with real Android and persisted Room values. */
    @Test fun existingLoanNumbersRoundTripWithoutChangingAnyValue() {
        val property = seedProperty("p7b-loan", "Darlehensobjekt")
        val original = Loan(id = 901, propertyId = property.propertyId, bezeichnung = "Bestand", darlehensbetrag = 100000.0,
            restschuld = 90000.0, sollzinsProzent = 3.0, tilgungProzent = 3.5, monatlicheRate = 100000.25,
            vermietungsanteilProzent = 87.12345, startDatum = "2026-01-01")
        runBlocking(Dispatchers.IO) { db.loanDao().upsertLoan(original) }
        clickTab(AppScreen.PROPERTIES)
        ui.onNodeWithTag("property_card_${property.propertyId}").performScrollTo().performClick()
        ui.onNode(hasText("Finanzierung") and hasClickAction()).performScrollTo().performClick()
        ui.onNodeWithTag("loan_management_card").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Bestand").fetchSemanticsNodes().isNotEmpty() }
        edit()
        mapOf("Darlehenssumme €*" to "100000,00", "Restschuld €*" to "90000,00", "Sollzins %*" to "3,00",
            "Tilgung %" to "3,50", "Monatliche Rate €" to "100000,25", "Vermietungsanteil %" to "87,12345").forEach { (label, value) ->
            editorScroll(hasText(label) and hasSetTextAction())
            ui.onNode(hasText(label) and hasSetTextAction()).assertTextContains(value)
        }
        editorScroll(hasTestTag("loan_name"))
        capture("p7b-loan-german-existing-numbers", ui.onNode(isDialog() and hasAnyDescendant(hasText("Darlehen bearbeiten"))))
        save(original)
        listOf("100000", "100000,00").forEach { value ->
            edit(); enter("Darlehenssumme €*", value); save(original)
        }
        edit(); enter("Sollzins %*", "3"); save(original)
        edit(); enter("Sollzins %*", "3,5"); enter("Restschuld €*", "90000,25")
        val changed = original.copy(sollzinsProzent = 3.5, restschuld = 90000.25)
        save(changed)
        edit(); enter("Darlehenssumme €*", "ungültig")
        ui.onNodeWithTag("save_loan_button").performClick()
        editorScroll(hasText("Darlehenssumme €*") and hasSetTextAction())
        ui.onNodeWithTag("loan_error_AMOUNT", useUnmergedTree = true).performScrollTo()
            .assertIsDisplayed().assertTextEquals("Bitte eine gültige Zahl eingeben.")
        assertEquals(changed, stored())
        ui.onNode(hasText("Abbrechen") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(changed, stored())
    }
    private fun editorScroll(matcher: SemanticsMatcher) = ui.onNode(hasScrollToIndexAction() and
        hasAnyAncestor(isDialog() and hasAnyDescendant(hasText("Darlehen bearbeiten")))).performScrollToNode(matcher)
    private fun enter(label: String, value: String) {
        editorScroll(hasText(label) and hasSetTextAction())
        input(label, value, scroll = false)
    }
    private fun stored() = runBlocking(Dispatchers.IO) { db.loanDao().getAllLoans().single() }
    private fun edit() = ui.onNodeWithContentDescription("Bearbeiten").performScrollTo().performClick()
    private fun save(expected: Loan) {
        ui.onNodeWithTag("save_loan_button").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Darlehen bearbeiten").fetchSemanticsNodes().isEmpty() }
        ui.waitUntil(10_000) { stored() == expected }
        assertEquals(expected, stored())
    }
}
