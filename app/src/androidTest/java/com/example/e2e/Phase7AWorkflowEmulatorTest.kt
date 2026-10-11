package com.example.e2e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MainActivity
import com.example.data.*
import com.example.ui.AppScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Production object menu, loan component, Room, bank confirmation and ledger. No alternate NavHost. */
abstract class Phase7ALoanMatrix : EmulatorTestSupport() {
    @Test fun activeObjectFinancingPreservesEditBackPersistenceAndObjectIsolation() {
        val a = seedProperty("p7-a", "MFH Lindenstraße")
        val b = seedProperty("p7-b", "MFH Bergstraße")
        val original = Loan(id = 701, propertyId = a.propertyId, bezeichnung = "Linden-Darlehen",
            darlehensbetrag = 100_000.0, restschuld = 90_000.0, sollzinsProzent = 3.125,
            monatlicheRate = 600.0, startDatum = "2026-01-01")
        runBlocking(Dispatchers.IO) {
            db.loanDao().upsertLoan(original)
            db.loanDao().upsertLoan(original.copy(id = 702, propertyId = b.propertyId, bezeichnung = "Berg-Darlehen", restschuld = 60_000.0))
            db.bankDao().upsertAccount(BankAccount("p7-account", "Synthetisches Testkonto"))
            db.bankDao().upsertTransaction(refund("p7-refund-a", a.propertyId, 200.0))
            db.bankDao().upsertTransaction(refund("p7-refund-b", b.propertyId, 300.0))
        }
        openProperty(a.propertyId)
        val action = ui.onNode(hasText("Finanzierung") and hasClickAction()).performScrollTo().assertIsDisplayed()
        completeFinancingText()
        capture("p7-object-entry-font-$fontScale")
        action.performClick()
        ui.onNodeWithTag("loan_management_card").assertIsDisplayed()
        capture("p7-financing-font-$fontScale")
        // A divergent global selection must never change the financing of the opened object.
        val model = vm
        ui.runOnIdle { model.selectProperty(b.propertyId) }
        ui.waitUntil(10_000) { model.propertyMetadata.value?.propertyId == b.propertyId }
        openLoan("Linden-Darlehen")
        ui.onNodeWithText("Berg-Darlehen").assertDoesNotExist()
        editLoan()
        ui.onNode(hasText("Restschuld €*") and hasSetTextAction()).performScrollTo().assertTextContains("90000,00")
        capture("p7-loan-editor-font-$fontScale", ui.onAllNodes(isDialog()).onLast())
        input("Restschuld €*", "89.000,50")
        ui.onNodeWithTag("save_loan_button").performClick()
        ui.waitUntil(10_000) { savedLoan().restschuld == 89_000.50 }
        assertEquals(original.copy(restschuld = 89_000.50), savedLoan()) // untouched numbers/dates/identity retained
        capture("p7-after-save-font-$fontScale")
        editLoan(); input("Restschuld €*", "1,00")
        ui.onNode(hasText("Abbrechen") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(89_000.50, savedLoan().restschuld, .001)
        editLoan(); input("Restschuld €*", "2,00"); systemBack()
        ui.onNodeWithText("Darlehen bearbeiten").assertDoesNotExist()
        assertEquals(89_000.50, savedLoan().restschuld, .001)
        editLoan(); input("Restschuld €*", "3,00")
        ui.onNode(hasContentDescription("Zurück") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(89_000.50, savedLoan().restschuld, .001)
        ui.onNodeWithText("Schließen").performClick() // existing management dialog -> financing
        ui.onNodeWithContentDescription("Zurück").performClick() // financing -> object
        ui.onNode(hasText("Finanzierung") and hasClickAction()).performScrollTo().performClick()
        openLoan("Linden-Darlehen"); editLoan()
        ui.onNode(hasText("Restschuld €*") and hasSetTextAction()).performScrollTo().assertTextContains("89000,50")
        systemBack(); systemBack(); systemBack() // editor -> manager -> financing -> object
        ui.onNode(hasText("Finanzierung") and hasClickAction()).performScrollTo().assertIsDisplayed()
        scenario.recreate()
        ui.onNode(hasText("Finanzierung") and hasClickAction()).performScrollTo().performClick()
        openLoan("Linden-Darlehen"); editLoan()
        ui.onNode(hasText("Restschuld €*") and hasSetTextAction()).performScrollTo().assertTextContains("89000,50")
        assertEquals(original.copy(restschuld = 89_000.50), savedLoan())
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java) // new VM, persisted Room/preferences
        openProperty(b.propertyId)
        ui.onNode(hasText("Finanzierung") and hasClickAction()).performScrollTo().performClick()
        openLoan("Berg-Darlehen")
        ui.onNodeWithText("Linden-Darlehen").assertDoesNotExist()
        capture("p7-object-b-isolation-font-$fontScale")
        systemBack(); systemBack(); systemBack() // manager -> financing -> object -> portfolio
        ui.onNodeWithTag("properties_overview").assertIsDisplayed()
        openProperty(a.propertyId)
        ui.onNode(hasText("Finanzierung") and hasClickAction()).performScrollTo().performClick()
        openLoan("Linden-Darlehen")
        ui.onNodeWithText("Berg-Darlehen").assertDoesNotExist()
        systemBack(); systemBack()
        clickMore("Einnahmen & Ausgaben")
        ledgerProperty("MFH Lindenstraße"); ledgerMetric("Einnahmen", "200,00 €")
        ledgerProperty("MFH Bergstraße"); ledgerMetric("Einnahmen", "300,00 €")
    }
    private fun savedLoan() = runBlocking(Dispatchers.IO) { db.loanDao().getAllLoans().single { it.id == 701 } }
    private fun openProperty(id: String) {
        clickTab(AppScreen.PROPERTIES)
        ui.onNodeWithTag("property_card_$id").performScrollTo().performClick()
    }
    private fun openLoan(name: String) {
        ui.onNodeWithTag("loan_management_card").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText(name).performScrollTo().assertIsDisplayed()
    }
    private fun editLoan() = ui.onNodeWithContentDescription("Bearbeiten").performScrollTo().performClick()
    private fun completeFinancingText() {
        val results = mutableListOf<TextLayoutResult>()
        ui.onNodeWithText("Finanzierung", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
        assertTrue(results.isNotEmpty())
        results.forEach {
            assertFalse(it.didOverflowWidth); assertFalse(it.didOverflowHeight)
            assertFalse(it.multiParagraph.didExceedMaxLines)
            assertEquals("Finanzierung".length, it.getLineEnd(it.lineCount - 1, visibleEnd = true))
        }
    }
    private fun ledgerProperty(name: String) {
        ui.onNodeWithTag("ledger_overview").performScrollToNode(hasTestTag("ledger_property"))
        ui.onNodeWithTag("ledger_property").performClick()
        ui.onNode(hasText(name) and hasAnyAncestor(isPopup())).performClick()
    }
    private fun ledgerMetric(title: String, value: String) {
        ui.onNodeWithTag("ledger_overview").performScrollToNode(hasTestTag("ledger_metric_$title"))
        ui.onNode(hasText(value) and hasAnyAncestor(hasTestTag("ledger_metric_$title"))).assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class Phase7ALoan10EmulatorTest : Phase7ALoanMatrix() { override val fontScale = 1.0f }
@RunWith(AndroidJUnit4::class)
class Phase7ALoan13EmulatorTest : Phase7ALoanMatrix() { override val fontScale = 1.3f }
@RunWith(AndroidJUnit4::class)
class Phase7ALoan15EmulatorTest : Phase7ALoanMatrix() { override val fontScale = 1.5f }

private fun refund(id: String, property: String, amount: Double, date: String = "2026-10-11") = BankTransaction(
    id, "p7-account", date, amount = amount, counterparty = "Erstattung $id", purpose = "Sonstige Erstattung",
    propertyId = property, category = "Sonstige Einnahmen", classification = BankTransactionClassification.NORMAL,
    reconciliationStatus = BankReconciliationStatus.NO_RECEIPT_REQUIRED, noReceiptReason = "Sonstiges")

@RunWith(AndroidJUnit4::class)
class Phase7AIncomeEmulatorTest : EmulatorTestSupport() {
    @Test fun bankConfirmationWithoutReceiptOrSplitFlowsIntoSameFilteredListAndMetrics() {
        val a = seedProperty("p7-a", "MFH Lindenstraße")
        val b = seedProperty("p7-b", "MFH Bergstraße")
        runBlocking(Dispatchers.IO) {
            db.bankDao().upsertAccount(BankAccount("p7-account", "Synthetisches Testkonto"))
            // Object/category are fixture metadata: current bank detail has no unsplit object selector.
            db.bankDao().upsertTransaction(refund("refund-a", a.propertyId, 200.0).copy(
                reconciliationStatus = BankReconciliationStatus.OPEN, noReceiptReason = ""))
            db.bankDao().upsertTransaction(refund("refund-b", b.propertyId, 300.0))
            db.bankDao().upsertTransaction(refund("refund-prior", a.propertyId, 150.0, "2025-10-11"))
        }
        val model = vm
        ui.waitUntil(10_000) { model.bankTransactions.value.size == 3 }
        clickMore("Bank & Kontoauszüge")
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Erstattung refund-a"))
        ui.onNodeWithText("Erstattung refund-a").performClick()
        ui.onNodeWithText("Buchungsdetails").assertIsDisplayed()
        ui.onNode(hasText("Kein Beleg erforderlich") and hasClickAction()).performScrollTo().performClick()
        ui.onNode(hasText("Sonstiges") and hasAnyAncestor(isDialog())).performClick()
        ui.onNodeWithText("Bestätigen").performClick()
        ui.waitUntil(10_000) { model.bankTransactions.value.single { it.transactionId == "refund-a" }.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED }
        assertTrue(model.bankReceiptLinks.value.isEmpty()); assertTrue(model.bankRentAssignments.value.isEmpty())
        assertTrue(model.receipts.value.isEmpty())
        systemBack(); clickMore("Einnahmen & Ausgaben")
        year("2026"); metric("Einnahmen", "500,00 €"); metric("Ergebnis", "500,00 €"); metric("Buchungen", "2")
        property("MFH Lindenstraße")
        metric("Einnahmen", "200,00 €"); metric("Ergebnis", "200,00 €"); metric("Buchungen", "1")
        capture("p7-200-refund-metrics")
        scroll("ledger_end")
        ui.onNodeWithTag("ledger_bank_bank-refund-a").assertIsDisplayed()
        ui.onNodeWithTag("ledger_bank_bank-refund-b").assertDoesNotExist()
        ui.onNodeWithTag("ledger_bank_bank-refund-prior").assertDoesNotExist()
        capture("p7-200-refund-row")
        year("2025"); metric("Einnahmen", "150,00 €"); metric("Buchungen", "1")
        scroll("ledger_end"); ui.onNodeWithTag("ledger_bank_bank-refund-prior").assertIsDisplayed()
        ui.onNodeWithTag("ledger_bank_bank-refund-a").assertDoesNotExist()
        year("2026"); property("MFH Bergstraße"); metric("Einnahmen", "300,00 €"); metric("Buchungen", "1")
        scroll("ledger_end"); ui.onNodeWithTag("ledger_bank_bank-refund-b").assertIsDisplayed()
        ui.onNodeWithTag("ledger_bank_bank-refund-a").assertDoesNotExist()
    }
    private fun scroll(tag: String) = ui.onNodeWithTag("ledger_overview").performScrollToNode(hasTestTag(tag))
    private fun metric(title: String, value: String) {
        scroll("ledger_metric_$title")
        ui.onNode(hasText(value) and hasAnyAncestor(hasTestTag("ledger_metric_$title"))).assertIsDisplayed()
    }
    private fun property(name: String) {
        scroll("ledger_property"); ui.onNodeWithTag("ledger_property").performClick()
        ui.onNode(hasText(name) and hasAnyAncestor(isPopup())).performClick()
    }
    private fun year(value: String) {
        scroll("ledger_year"); ui.onNodeWithTag("ledger_year").performClick()
        ui.onNode(hasText(value) and hasAnyAncestor(isPopup())).performClick()
    }
}
