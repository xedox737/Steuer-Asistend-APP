package com.example.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.Loan
import com.example.ui.AppScreen
import com.example.ui.PropertyTask
import com.example.ui.PropertyTaskStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Complements the receipt/property/renovation/DATEV font-scale screenshot matrix. */
@RunWith(AndroidJUnit4::class)
class AppDateDisplayEmulatorTest : EmulatorTestSupport() {
    @Test fun persistedIsoDatesAppearGermanInAfaTenancyTasksAndLoanReview() {
        val property = seedProperty("date-display", "Datumsobjekt", rented = true)
        val model = vm
        runBlocking(Dispatchers.IO) {
            db.propertyDao().insertPropertyMetadata(property.copy(
                notariellesKaufdatum = "2025-10-01", uebergangNutzenLasten = "2026-01-01", gebaeudewert = 320_000.0))
            db.loanDao().upsertLoan(Loan(bezeichnung = "Testdarlehen", propertyId = property.propertyId,
                startDatum = "2026-01-01", zinsbindungBis = "2036-12-31", laufzeitBis = "2046-12-31"))
            db.receiptDao().insertReceipt(expense(911, 120.0, property.propertyId, "2026-10-11").copy(
                hauptkategorie = "Finanzierung, Kredite & Versicherungen", unterkategorie = "Kreditzinsen"))
            PropertyTaskStore.upsert(context, PropertyTask(id = "date-task", propertyId = property.propertyId,
                title = "Datumsfrist", dueDate = "2026-10-11", createdAt = "2026-10-01T12:00:00Z", updatedAt = "2026-10-01T12:00:00Z"))
        }
        ui.waitUntil(10_000) { model.properties.value.single().uebergangNutzenLasten == "2026-01-01" && model.receipts.value.any { it.id == 911 } }

        clickMore("AfA Gebäude")
        ui.onNodeWithText("Datumsobjekt").performScrollTo().performClick()
        ui.onNodeWithText("01.10.2025").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("01.01.2026").performScrollTo().assertIsDisplayed()
        noVisibleIsoDate()
        capture("date-afa-display")

        openUnits(property.propertyId)
        ui.onNodeWithText("Testwohnung").performScrollTo().performClick()
        ui.onNodeWithText("01.04.2021").performScrollTo().assertIsDisplayed()
        noVisibleIsoDate()
        capture("date-tenancy-display")

        openProperty(property.propertyId)
        ui.onNode(hasText("Aufgaben & Fristen") and hasClickAction()).performScrollTo().performClick()
        ui.onNodeWithText("Sonstiges · fällig 11.10.2026").performScrollTo().assertIsDisplayed()
        noVisibleIsoDate()
        capture("date-task-display")

        openProperty(property.propertyId)
        ui.onNode(hasText("Finanzierung") and hasClickAction()).performScrollTo().performClick()
        ui.onNodeWithTag("loan_management_card").performClick()
        ui.onNodeWithText("11.10.2026").performScrollTo().assertIsDisplayed()
        noVisibleIsoDate()
        capture("date-loan-review-display")
        ui.onNodeWithContentDescription("Bearbeiten").performScrollTo().performClick()
        ui.onNode(hasText("Start") and hasSetTextAction()).performScrollTo().assertTextContains("01.01.2026")
        ui.onNode(hasText("Zinsbindung bis") and hasSetTextAction()).performScrollTo().assertTextContains("31.12.2036")
        ui.onNode(hasText("Geplantes Laufzeitende") and hasSetTextAction()).performScrollTo().assertTextContains("31.12.2046")
        noVisibleIsoDate()
    }

    private fun openProperty(id: String) {
        clickTab(AppScreen.DASHBOARD)
        clickTab(AppScreen.PROPERTIES)
        ui.onNodeWithTag("property_card_$id").performScrollTo().performClick()
    }

    private fun noVisibleIsoDate() {
        val iso = Regex("\\b\\d{4}-\\d{2}-\\d{2}\\b")
        ui.onAllNodes(SemanticsMatcher("sichtbares ISO-Datum") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.any { iso.containsMatchIn(it.text) } == true ||
                node.config.getOrNull(SemanticsProperties.EditableText)?.text?.let(iso::containsMatchIn) == true
        }, useUnmergedTree = true).assertCountEquals(0)
    }
}
