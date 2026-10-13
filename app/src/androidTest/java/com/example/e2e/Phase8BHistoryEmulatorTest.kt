package com.example.e2e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import com.example.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** Production MainActivity, Room and preference fixtures; real navigation, no alternative screen host. */
abstract class Phase8BHistoryMatrix : EmulatorTestSupport() {
    @Test fun vacantUnitLastBankPaymentSurvivesRecreateAndCurrentTenantKeepsMonthlyDedup() {
        val vacant = seedProperty("p8b-vacant", "Leerstandshaus")
        val current = seedProperty("p8b-current", "Vermietetes Haus", rented = true)
        val old = TenantPeriod(801, "WE 01", "Vormieter", "2026-01-01", "2026-09-30", 760.0, 220.0, 0.0)
        val month = YearMonth.now()
        val newTenant = TenantPeriod(802, "WE 01", "Testmieter Alt", month.atDay(1).toString(), "", 600.0, 0.0, 0.0)
        val beforeChange = old.copy(endDate = month.minusMonths(1).atEndOfMonth().toString())
        TenantHistoryStore.save(context, vacant.propertyId, "${vacant.propertyId}-unit", "WE 01", listOf(old))
        TenantHistoryStore.save(context, current.propertyId, "${current.propertyId}-unit", "WE 01", listOf(beforeChange, newTenant))
        val receipt = Receipt(881, "Laufender Mieter", LocalDate.now().toString(), "", 600.0,
            "Miete, Nebenkosten & Kaution", "Miete", "", "Aktuelle Miete", propertyId = current.propertyId,
            unitId = "${current.propertyId}-unit", wohneinheit = "WE 01", internalId = "p8b-original", pruefstatus = "GEPRUEFT", exportStatus = "BEREIT")
        val vacantTx = bank("p8b-vacant-payment", vacant.propertyId, 980.0, "2026-10-05")
        val currentTx = bank("p8b-current-payment", current.propertyId, 600.0, LocalDate.now().toString())
        val oldTx = bank("p8b-old-payment", current.propertyId, 980.0, "2026-10-05")
        runBlocking(Dispatchers.IO) {
            db.bankDao().upsertAccount(BankAccount("p8b-account", "Synthetisches Testkonto"))
            listOf(vacantTx, currentTx, oldTx).forEach { db.bankDao().upsertTransaction(it) }
            db.bankRentAssignmentDao().upsert(assignment(vacantTx, "2026-09", "801"))
            db.bankRentAssignmentDao().upsert(assignment(currentTx, month.toString(), "802").copy(receiptId = receipt.id))
            db.bankRentAssignmentDao().upsert(assignment(oldTx, month.minusMonths(1).toString(), "801"))
            db.receiptDao().insertReceipt(receipt)
            db.bankDao().upsertLink(BankReceiptLink("p8b-link", currentTx.transactionId, receipt.id, receipt.internalId, 600.0))
        }
        val model = vm
        ui.waitUntil(10_000) { model.bankRentAssignments.value.size == 3 && model.bankTransactions.value.size == 3 && model.bankReceiptLinks.value.size == 1 && model.receipts.value.size == 1 }
        openUnits(vacant.propertyId); openUnit()
        paymentText("Letzte Mieterzahlung"); paymentText("980,00 €"); paymentText("am 05.10.2026")
        completePaymentText("Letzte Mieterzahlung"); completePaymentText("980,00 €"); completePaymentText("am 05.10.2026")
        capture("p8b-vacant-bank-font-$fontScale")
        scenario.recreate()
        paymentText("980,00 €"); paymentText("am 05.10.2026")
        systemBack(); ui.onNodeWithTag("property_units_overview").assertIsDisplayed()
        openUnit(); ui.onNodeWithContentDescription("Zurück").performClick()
        ui.onNodeWithTag("property_units_overview").assertIsDisplayed()
        openUnits(current.propertyId); openUnit()
        paymentText("Ist bestätigt"); paymentText("600,00 €") // bank+receipt once; prior tenancy's 980 is not current rent
        ui.onNode(hasText("980,00 €") and hasAnyAncestor(hasTestTag("unit_payment_status"))).assertDoesNotExist()
        capture("p8b-current-dedup-font-$fontScale")
        assertEquals("801", runBlocking(Dispatchers.IO) { db.bankRentAssignmentDao().getAll().single { it.transactionId == oldTx.transactionId }.tenantReference)
        assertEquals(receipt, runBlocking(Dispatchers.IO) { db.receiptDao().getReceiptById(receipt.id) })
    }

    @Test fun datevPreviewAndCompleteHistoryUseGermanStatusAndBothBackPathsKeepAudits() {
        val runs = (6 downTo 1).map { index -> ExportAuditRun("P8B-Export-$index",
            timestamp = Instant.parse("${2020+index}-06-15T12:00:00Z").toEpochMilli(),
            status = if (index == 1) "FAILED" else "SUCCESS", kanzleiprofilNameVersion = "Testkanzlei v1",
            zipFileName = "EXTF-$index.csv", bookingCount = index, totalAmount = index * 100.0, zipSha256 = "a".repeat(64)) }
        runBlocking(Dispatchers.IO) { runs.forEach { db.exportAuditDao().insertRun(it) } }
        val model = vm
        clickMore("DATEV Export")
        ui.waitUntil(10_000) { model.allAuditRuns.value.size == 6 }
        // Prepared result fixture uses the real wizard state; no export or historical audit is fabricated by UI.
        ui.runOnIdle { model.setWizardStep(6) }
        ui.onNodeWithTag("datev_audit_show_all").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("datev_audit_status_P8B-Export-6").performScrollTo().assertTextEquals("Erfolgreich")
        completeText("datev_audit_status_P8B-Export-6", "Erfolgreich")
        ui.onNodeWithTag("datev_audit_P8B-Export-1").assertDoesNotExist()
        capture("p8b-datev-preview-font-$fontScale")
        ui.onNodeWithTag("datev_audit_show_all").performScrollTo().performClick()
        historyOldest()
        capture("p8b-datev-full-history-font-$fontScale")
        scenario.recreate()
        ui.onNodeWithTag("datev_audit_history").assertIsDisplayed()
        historyOldest()
        systemBack()
        ui.onNodeWithText("Schritt 6 von 6 · Ergebnis & Exporthistorie").assertIsDisplayed()
        ui.onNodeWithTag("datev_audit_show_all").performScrollTo().performClick()
        historyOldest()
        ui.onNodeWithContentDescription("Zurück").performClick()
        ui.onNodeWithText("Schritt 6 von 6 · Ergebnis & Exporthistorie").assertIsDisplayed()
        systemBack(); ui.onNodeWithTag("more_menu").assertIsDisplayed()
        clickMore("DATEV Export")
        ui.onNodeWithTag("datev_audit_open").performScrollTo().performClick() // history reachable without re-exporting
        historyOldest(); systemBack()
        ui.onNodeWithText("DATEV Export").assertIsDisplayed()
        assertEquals(runs, runBlocking(Dispatchers.IO) { db.exportAuditDao().getAllRunsFlow().first() })
    }
    private fun historyOldest() {
        ui.onNodeWithTag("datev_audit_history").performScrollToNode(hasTestTag("datev_audit_P8B-Export-1"))
        ui.onNodeWithTag("datev_audit_P8B-Export-1").assertIsDisplayed()
        ui.onNodeWithTag("datev_audit_status_P8B-Export-1").assertTextEquals("Fehlgeschlagen")
        completeText("datev_audit_status_P8B-Export-1", "Fehlgeschlagen")
        ui.onNodeWithText("15.06.2021", substring = true).assertIsDisplayed()
        ui.onNodeWithText("SUCCESS").assertDoesNotExist()
    }
    private fun openUnit() {
        ui.onNodeWithTag("property_units_overview").performScrollToNode(hasText("Testwohnung"))
        ui.onNodeWithText("Testwohnung").assertIsDisplayed().performClick()
    }
    private fun paymentText(text: String) {
        ui.onNodeWithTag("unit_detail_list").performScrollToNode(hasTestTag("unit_payment_status"))
        ui.onNode(hasText(text) and hasAnyAncestor(hasTestTag("unit_payment_status"))).assertIsDisplayed()
    }
    private fun completePaymentText(text: String) {
        val node = ui.onNode(hasText(text) and hasAnyAncestor(hasTestTag("unit_payment_status")), useUnmergedTree = true)
        assertComplete(node, text)
    }
    private fun completeText(tag: String, text: String) = assertComplete(ui.onNodeWithTag(tag, useUnmergedTree = true), text)
    private fun assertComplete(node: SemanticsNodeInteraction, text: String) {
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
        assertTrue(results.isNotEmpty())
        results.forEach {
            assertFalse(it.didOverflowWidth); assertFalse(it.didOverflowHeight); assertFalse(it.multiParagraph.didExceedMaxLines)
            assertEquals(text.length, it.getLineEnd(it.lineCount - 1, visibleEnd = true))
        }
    }
    private fun bank(id: String, property: String, amount: Double, date: String) = BankTransaction(id, "p8b-account", date,
        amount = amount, propertyId = property, unitId = "$property-unit", counterparty = "Vormieter",
        reconciliationStatus = BankReconciliationStatus.MATCHED)
    private fun assignment(tx: BankTransaction, rentMonth: String, tenancy: String) = BankRentAssignment(
        "assignment-${tx.transactionId}", tx.transactionId, tx.propertyId, tx.unitId, rentMonth, tenancy, tx.amount,
        BankSplitPaymentType.RENT, createdAt = "", updatedAt = "")
}

@RunWith(AndroidJUnit4::class)
class Phase8BHistory10EmulatorTest : Phase8BHistoryMatrix() { override val fontScale = 1.0f }
@RunWith(AndroidJUnit4::class)
class Phase8BHistory13EmulatorTest : Phase8BHistoryMatrix() { override val fontScale = 1.3f }
@RunWith(AndroidJUnit4::class)
class Phase8BHistory15EmulatorTest : Phase8BHistoryMatrix() { override val fontScale = 1.5f }
