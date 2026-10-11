package com.example.e2e

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.Loan
import com.example.ui.LoanManagementSection
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Component integration: FINANCE has no entry in main's current property menu.
 * Mounts the unchanged production component in MainActivity with real Room.
 * This is deliberately not reported as an end-to-end navigation test.
 */
@RunWith(AndroidJUnit4::class)
class LoanDateComponentEmulatorTest : EmulatorTestSupport() {
    @Test fun productionLoanEditorShowsGermanDatesAndCalendarPersistsIso() {
        val property = seedProperty("date-loan", "Darlehensobjekt")
        val model = vm
        runBlocking(Dispatchers.IO) {
            db.loanDao().upsertLoan(Loan(bezeichnung = "Testdarlehen", propertyId = property.propertyId,
                darlehensbetrag = 100_000.0, restschuld = 90_000.0, sollzinsProzent = 3.0,
                startDatum = "2026-01-01", zinsbindungBis = "2036-12-31", laufzeitBis = "2046-12-31"))
            db.receiptDao().insertReceipt(expense(912, 120.0, property.propertyId, "2026-10-11").copy(
                hauptkategorie = "Finanzierung, Kredite & Versicherungen", unterkategorie = "Kreditzinsen"))
        }
        scenario.onActivity { activity -> activity.setContent {
            MyApplicationTheme { LoanManagementSection(model, propertyScoped = true) }
        } }
        ui.onNodeWithTag("loan_management_card").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Testdarlehen").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("11.10.2026").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("2026-10-11").assertDoesNotExist()
        capture("date-loan-review-display")
        ui.onNodeWithContentDescription("Bearbeiten").performScrollTo().performClick()
        ui.onNode(hasText("Start") and hasSetTextAction()).performScrollTo().assertTextContains("01.01.2026")
        ui.onNode(hasText("Zinsbindung bis") and hasSetTextAction()).performScrollTo().assertTextContains("31.12.2036")
        ui.onNode(hasText("Geplantes Laufzeitende") and hasSetTextAction()).performScrollTo().assertTextContains("31.12.2046")
        capture("date-loan-editor-display", ui.onAllNodes(isDialog()).onLast())
        input("Start", "02.01.2026")
        ui.onNodeWithTag("Start_calendar").performClick()
        ui.onNode(SemanticsMatcher("Kalendertag 2") { node ->
            (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).any {
                Regex("(^|\\D)2(\\D|$)").containsMatchIn(it)
            } && node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick) != null
        }, useUnmergedTree = true).performClick()
        ui.onNodeWithTag("app_date_apply").performClick()
        ui.onNodeWithTag("save_loan_button").performClick()
        ui.waitUntil(10_000) { runBlocking(Dispatchers.IO) { db.loanDao().getAllLoans().single().startDatum == "2026-01-02" } }
        val saved = runBlocking(Dispatchers.IO) { db.loanDao().getAllLoans().single() }
        assertEquals("2036-12-31", saved.zinsbindungBis)
        assertEquals("2046-12-31", saved.laufzeitBis)
        ui.onNodeWithContentDescription("Bearbeiten").performScrollTo().performClick()
        ui.onNode(hasText("Start") and hasSetTextAction()).performScrollTo().assertTextContains("02.01.2026")
    }
}
