package com.example.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    @Test fun persistedIsoDatesAppearGermanInAfaTenancyAndTasks() {
        val property = seedProperty("date-display", "Datumsobjekt", rented = true)
        val model = vm
        runBlocking(Dispatchers.IO) {
            db.propertyDao().insertPropertyMetadata(property.copy(
                notariellesKaufdatum = "2025-10-01", uebergangNutzenLasten = "2026-01-01", gebaeudewert = 320_000.0))
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
        ui.onNodeWithTag("property_units_overview").performScrollToNode(hasText("Testwohnung"))
        ui.onNodeWithText("Testwohnung").assertIsDisplayed().performClick()
        ui.onNodeWithText("01.04.2021").performScrollTo().assertIsDisplayed()
        noVisibleIsoDate()
        capture("date-tenancy-display")

        openProperty(property.propertyId)
        ui.onNode(hasText("Notizen & Aufgaben") and hasClickAction()).performScrollTo().performClick()
        ui.onNodeWithText("Sonstiges · fällig 11.10.2026").performScrollTo().assertIsDisplayed()
        noVisibleIsoDate()
        capture("date-task-display")

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
