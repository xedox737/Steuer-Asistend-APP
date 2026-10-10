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
class Phase6BWorkflowEmulatorTest : EmulatorTestSupport() {
    @Test fun globalReceiptSelectsObjectAndStableUnitThenSurvivesReopen() {
        seedProperty("6b-a", "MFH Lindenstraße")
        seedProperty("6b-b", "MFH Bergstraße")
        val model = vm
        clickTab(AppScreen.ADD_RECEIPT)
        ui.onNodeWithTag("capture_property").performScrollTo().assertTextContains("MFH Bergstraße").performClick()
        ui.onNode(hasText("MFH Lindenstraße") and hasAnyAncestor(isPopup())).performClick()
        ui.onNodeWithTag("capture_unit").assertTextContains("Gesamtobjekt / Allgemein").performClick()
        ui.onNode(hasText("WE 01") and hasAnyAncestor(isPopup())).performClick()
        capture("phase6b-global-object-a-unit-01")
        ui.onNodeWithTag("edit_aussteller").performScrollTo().performTextReplacement("MW Globalhandwerk")
        ui.onNodeWithTag("edit_datum").performScrollTo().performTextReplacement("2026-10-10")
        ui.onNodeWithTag("edit_betrag").performScrollTo().performTextReplacement("119,00")
        ui.onNodeWithTag("save_extracted_receipt_button").performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil(10_000) { model.currentScreen.value == AppScreen.RECEIPTS_LIST && model.receipts.value.any { it.aussteller == "MW Globalhandwerk" } }
        val saved = runBlocking(Dispatchers.IO) { db.receiptDao().getAllReceiptsList().single() }
        assertEquals("6b-a", saved.propertyId); assertEquals("6b-a-unit", saved.unitId)
        assertEquals("6b-b", model.selectedPropertyId.value)
        ui.onNodeWithTag("receipts_list").performScrollToNode(hasTestTag("receipt_item_${saved.id}"))
        ui.onNodeWithTag("receipt_item_${saved.id}").performClick()
        ui.onNodeWithTag("edit_receipt_button").performScrollTo().performClick()
        ui.onNodeWithTag("edit_receipt_property").performScrollTo().assertTextContains("MFH Lindenstraße")
        ui.onNodeWithTag("edit_receipt_wohneinheit_dropdown").performScrollTo().assertTextContains("WE 01")
        scenario.recreate()
        assertEquals("6b-a-unit", runBlocking(Dispatchers.IO) { db.receiptDao().getReceiptById(saved.id)!!.unitId })
        systemBack()
    }

    @Test fun realMeasureCreationReceiptAssignmentAdvisorMarkAndBackNavigation() {
        val property = seedProperty("6b-review", "MFH Prüfhof")
        val receipt = expense(681, 119.0, property.propertyId, "2026-10-10").copy(
            aussteller = "MW Badhandwerk", unitId = "6b-review-unit", wohneinheit = "WE 01",
            positionenJson = ReceiptItemConverter.toJson(listOf(ReceiptItem(gesamtpreis = 119.0, steuersatz = 19.0))))
        runBlocking(Dispatchers.IO) {
            db.propertyDao().insertPropertyMetadata(property.copy(uebergangNutzenLasten = "2026-01-01", gesamtKaufpreis = 400_000.0, gebaeudewert = 320_000.0, grundUndBodenWert = 80_000.0))
            db.receiptDao().insertReceipt(receipt)
        }
        val model = vm
        ui.waitUntil(10_000) { model.properties.value.any { it.propertyId == property.propertyId && it.gebaeudewert == 320_000.0 } }
        clickTab(AppScreen.PROPERTIES)
        ui.onNodeWithTag("property_card_${property.propertyId}").performScrollTo().performClick()
        ui.onNode(hasText("Sanierung & 15%-Prüfung") and hasClickAction()).performScrollTo().performClick()
        ui.onNodeWithTag("renovation_overview").assertIsDisplayed()
        ui.onNodeWithTag("renovation_add_measure").performScrollTo().performClick()
        ui.onNodeWithTag("renovation_measure_name").performScrollTo().performTextReplacement("Bad OG links")
        ui.onNodeWithTag("renovation_measure_note").performScrollTo().performTextReplacement("Mit Fenster DG prüfen")
        ui.onNodeWithTag("renovation_measure_save").performClick()
        ui.waitUntil(10_000) { model.renovationReview.value.measures.size == 1 }
        ui.onNode(hasText("Bad OG links") and hasClickAction()).performScrollTo().performClick()
        ui.onNodeWithTag("renovation_measure_detail").assertIsDisplayed()
        ui.onNode(hasText("Belege zuordnen") and hasClickAction()).performScrollTo().performClick()
        ui.onNodeWithTag("renovation_pick_receipt_681").performScrollTo().performClick()
        ui.onNodeWithTag("renovation_assignment_status").performScrollTo().performClick()
        ui.onNode(hasText("Für 15%-Prüfung vorgemerkt") and hasAnyAncestor(isPopup())).performClick()
        ui.onNodeWithTag("renovation_assignment_save").performClick()
        ui.waitUntil(10_000) { model.renovationReview.value.relations.size == 1 }
        ui.onNodeWithTag("renovation_measure_detail").performScrollToNode(hasText("MW Badhandwerk"))
        ui.onNode(hasText("MW Badhandwerk") and hasClickAction()).assertIsDisplayed().performClick()
        ui.onNodeWithTag("receipt_renovation_card").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("renovation_mark_advisor").performScrollTo().performClick()
        ui.waitUntil(10_000) { model.renovationReview.value.relations.single().advisorMarked }
        capture("phase6b-receipt-renovation-assignment")
        ui.onNodeWithTag("renovation_assign").performScrollTo().performClick()
        systemBack() // Cancel assignment, preserve current relation.
        assertEquals(1, model.renovationReview.value.relations.size)
        systemBack() // Receipt -> same measure, not dashboard.
        ui.onNodeWithTag("renovation_measure_detail").assertIsDisplayed()
        capture("phase6b-measure-with-receipt")
        ui.onNodeWithContentDescription("Zurück").performClick()
        ui.onNodeWithTag("renovation_overview").assertIsDisplayed()
        capture("phase6b-object-renovation-overview")
        ui.onNodeWithContentDescription("Zurück").performClick()
        ui.onNode(hasText("Notizen & Aufgaben") and hasClickAction()).assertExists()
        assertEquals(AppScreen.PROPERTIES, model.currentScreen.value)
        assertEquals("Mit Fenster DG prüfen", model.renovationReview.value.measures.single().advisorNote)
        assertEquals("OFFEN", runBlocking(Dispatchers.IO) { db.receiptDao().getReceiptById(681)!!.freigabestatus })
    }
}
