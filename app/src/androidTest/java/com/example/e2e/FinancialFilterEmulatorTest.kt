package com.example.e2e

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class FinancialFilterEmulatorTest : EmulatorTestSupport() {
    @Test fun propertyFilterKeepsReceiptAndConfirmedBankRentRowsConsistentWithMetrics() {
        seedProperty("e2e-a", "Testobjekt A")
        seedProperty("e2e-b", "Testobjekt B")
        val today = LocalDate.now().toString()
        runBlocking(Dispatchers.IO) {
            db.receiptDao().insertReceipt(expense(901, 1000.0, "e2e-a", today))
            db.receiptDao().insertReceipt(expense(902, 2000.0, "e2e-b", today))
            db.bankDao().upsertAccount(BankAccount("e2e-account", "Synthetisches Testkonto"))
            db.bankDao().upsertTransaction(BankTransaction("e2e-transaction", "e2e-account", today,
                amount = 980.0, counterparty = "Testmieter", purpose = "Testmiete",
                reconciliationStatus = BankReconciliationStatus.MATCHED))
            db.bankRentAssignmentDao().upsert(BankRentAssignment("e2e-rent", "e2e-transaction", "e2e-a",
                "e2e-a-unit", YearMonth.now().toString(), "e2e-tenant", 980.0, BankSplitPaymentType.RENT,
                createdAt = today, updatedAt = today))
        }
        val model = vm
        clickMore("Einnahmen & Ausgaben")
        ui.waitUntil(10_000) { model.receipts.value.size == 2 && model.bankRentAssignments.value.size == 1 }
        metric("Ausgaben", "3.000,00 €")
        metric("Einnahmen", "980,00 €")
        metric("Buchungen", "3")
        scroll("ledger_property")
        ui.onNodeWithTag("ledger_property").performClick()
        ui.onNode(hasText("Testobjekt A") and hasAnyAncestor(isPopup())).performClick()
        metric("Ausgaben", "1.000,00 €")
        metric("Einnahmen", "980,00 €")
        metric("Ergebnis", "-20,00 €")
        metric("Buchungen", "2")
        capture("financial-filter-metrics")
        scroll("ledger_end")
        ui.onNodeWithTag("ledger_receipt_901").assertIsDisplayed()
        ui.onNodeWithTag("ledger_bank_bank-e2e-rent").assertIsDisplayed()
        ui.onNodeWithTag("ledger_receipt_902").assertDoesNotExist()
        capture("financial-filter-rows")
    }

    private fun scroll(tag: String) = ui.onNodeWithTag("ledger_overview").performScrollToNode(hasTestTag(tag))
    private fun metric(title: String, text: String) {
        scroll("ledger_metric_$title")
        ui.onNode(hasText(text) and hasAnyAncestor(hasTestTag("ledger_metric_$title"))).assertIsDisplayed()
    }
}

internal fun expense(id: Int, amount: Double, propertyId: String, date: String) = Receipt(
    id = id, aussteller = "Testhandwerk $id", datum = date, uhrzeit = "", bruttobetrag = amount,
    hauptkategorie = "Werbungskosten", unterkategorie = "Reparatur", kontoNr = "4800",
    beschreibung = "Synthetische Reparatur", propertyId = propertyId, internalId = "e2e-receipt-$id"
)
