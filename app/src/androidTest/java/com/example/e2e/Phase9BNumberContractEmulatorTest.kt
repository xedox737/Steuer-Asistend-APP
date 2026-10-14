package com.example.e2e

import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

abstract class Phase9BNumberContractMatrix : EmulatorTestSupport() {
    private fun show(content: @Composable () -> Unit) {
        scenario.onActivity { it.setContent { MyApplicationTheme(darkTheme = false) { content() } } }
        ui.waitForIdle()
    }
    private fun visibleError(text: String, scroll: Boolean = false) {
        val node = ui.onNodeWithText(text)
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { assertFalse(it.didOverflowWidth); assertFalse(it.didOverflowHeight); assertFalse(it.multiParagraph.didExceedMaxLines) }
    }

    @Test fun bankAndAfaUseSharedGrammarWithoutClosingOnInvalidInputAndErrorsFit() {
        assertTrue(context.resources.configuration.screenWidthDp in 390..395)
        var amount: Double? = null
        val suggestion = BankRentSuggestion("9b-tx", "9b-property", "9b-unit", "9b-tenant", "Mieter", "2026-10",
            98.0, 980.0, 882.0, 90, "HOCH", emptyList(), "MIETE", "NONE", 98.0)
        show { PartialRentDialog(suggestion, {}, { amount = it }) }
        input("Zuzuordnender Betrag €", "1.2.3,45", scroll = false)
        ui.onNodeWithText("Bestätigen").performClick()
        assertNull(amount)
        visibleError("Bitte eine gültige Zahl eingeben.")
        capture("9b-bank-error-font-$fontScale", ui.onAllNodes(isDialog()).onLast())
        input("Zuzuordnender Betrag €", "98.0", scroll = false)
        ui.onNodeWithText("Bestätigen").performClick()
        assertEquals(98.0, amount!!, 0.0)

        var saved: ConfirmedAfaValues? = null
        show { ConfirmedAfaValuesDialog(ConfirmedAfaValues("2026-10-01", 20000.0, 80000.0, "Testbericht"), {}, { saved = it }) }
        input("Bisherige AfA kumuliert (€)", "1.2.3,45")
        ui.onNodeWithText("Bestätigte Werte speichern").performClick()
        assertNull(saved)
        visibleError("Bitte Stichtag, beide nicht negativen Beträge und Quelle prüfen.", scroll = true)
        capture("9b-afa-error-font-$fontScale", ui.onAllNodes(isDialog()).onLast())
        input("Bisherige AfA kumuliert (€)", "1.234,56")
        ui.onNodeWithText("Bestätigte Werte speichern").performClick()
        assertEquals(1234.56, saved!!.cumulativeAfa, 0.0)
    }

    @Test fun logbookRetainsInvalidMinusThenOdometerThousandsSaveAndReopenConsistently() {
        val property = seedProperty("9b-logbook", "Kilometerhaus")
        val model = vm
        val draft = JSONObject().put("bookingKey", "9b-trip").put("propertyId", property.propertyId)
            .put("start", "Dornstetten").put("destination", "Mietobjekt").put("purpose", "Besichtigung")
            .put("date", "2026-10-14").put("step", 1).put("manualKmText", "-12,5")
        model.saveLogbookDraft("0", draft.toString())
        val receipt = Receipt(aussteller = "Manuelle Fahrt", datum = "2026-10-14", uhrzeit = "", bruttobetrag = 0.0,
            hauptkategorie = "", unterkategorie = "", kontoNr = "", beschreibung = "", propertyId = property.propertyId)
        show { LogbookEntryScreen(receipt, property, model, {}, {}) }
        visibleError("Der Wert darf nicht negativ sein.", scroll = true)
        capture("9b-logbook-error-font-$fontScale")
        assertTrue(JSONObject(model.logbookDrafts.value.getValue("0")).getString("manualKmText") == "-12,5")
        input("Manuell bestätigte Strecke (km)", "", scroll = true)
        input("Tacho Start", "12.345", scroll = true)
        input("Tacho Ende", "12.365", scroll = true)
        val evidence = DistanceEvidence(odometerStartKm = GermanNumberInput.parse("12.345"),
            odometerEndKm = GermanNumberInput.parse("12.365"), manuallyConfirmed = true)
        assertEquals(20.0, LogbookDistancePolicy.decide(evidence).taxDistanceKm!!, 0.0)
        // Persist through the production booking boundary; reopen both Room records.
        runBlocking(Dispatchers.IO) {
            val result = model.saveLogbookTrip(receipt, "Besichtigung", "Dornstetten", "Mietobjekt",
                listOf(TripStop("Dornstetten", "Start", 0), TripStop("Mietobjekt", "Ziel", 1)), TripRouteMode.EINFACH,
                false, evidence, bookingKey = "9b-trip", tripDate = "2026-10-14", propertyId = property.propertyId)
            assertTrue(result.isSuccess)
            val trip = db.logbookDao().getAllTrips().single()
            assertEquals(20.0, trip.taxDistanceKm, 0.0)
            assertEquals(6.0, db.receiptDao().getReceiptById(trip.expenseReceiptId!!)!!.bruttobetrag, 0.0)
        }
        scenario.recreate()
        assertEquals(20.0, runBlocking(Dispatchers.IO) { db.logbookDao().getAllTrips().single().taxDistanceKm }, 0.0)
    }

    @Test fun wizardAndDocumentRejectExtremeOrMalformedRawNumbersBeforeMutation() {
        var created = false
        show { PropertyCreationWizard({}, { _, _, _ -> created = true }) }
        input("Objektname", "9B Haus")
        input("Straße und Hausnummer", "Musterstraße 1")
        ui.onNodeWithTag("property_wizard_next").performClick()
        ui.onNodeWithTag("property_wizard_next").performClick()
        input("Darlehensbetrag €", "1" + "0".repeat(200))
        ui.onNodeWithTag("property_wizard_next").performClick()
        assertFalse(created)
        ui.onNodeWithText("Der Wert ist für die Berechnung zu groß.").performScrollTo().assertIsDisplayed()
        capture("9b-wizard-error-font-$fontScale", ui.onAllNodes(isDialog()).onLast())
        input("Darlehensbetrag €", "100000")
        ui.onNodeWithTag("property_wizard_next").performClick()
        input("Wohnfläche m²", "1" + "0".repeat(200))
        ui.onNodeWithTag("property_wizard_next").performClick()
        assertFalse(created)
        visibleError("Bitte für Einheit 1 eine gültige, nicht negative Wohnfläche eingeben.", scroll = true)
        ui.onNodeWithText("Immobilie anlegen · 4/5").assertIsDisplayed()
        capture("9b-wizard-float-error-font-$fontScale", ui.onAllNodes(isDialog()).onLast())

        val property = seedProperty("9b-document", "Dokumenthaus")
        val doc = ManagedDocument("9b-raw-doc", property.propertyId, title = "9B Dokument", documentDate = "2026-10-14",
            ocrStatus = "ERFOLGREICH", ocrText = "Testvertrag")
        runBlocking(Dispatchers.IO) { db.managedDocumentDao().upsert(doc) }
        val model = vm
        ui.waitUntil(10_000) { model.managedDocuments.value.any { it.documentId == doc.documentId } }
        ui.runOnIdle {
            model.selectProperty(property.propertyId)
            model.documentAiReviewStore.offer(doc, com.example.api.ManagedDocumentAiResult(documentDate = doc.documentDate,
                fields = listOf(com.example.api.ManagedDocumentAiField("kaufpreis", "Kaufpreis", "1e3", .9))))
            model.documentAiReviewStore.dismiss(doc.documentId)
        }
        show { DocumentManagementScreen(model) }
        ui.onNodeWithText("9B Dokument").performScrollTo().performClick()
        ui.onNodeWithTag("review_managed_document_ai").performScrollTo().performClick()
        ui.onNode(isToggleable()).performScrollTo().performClick()
        ui.onNodeWithTag("confirm_document_ai_review").performClick()
        ui.waitUntil(10_000) { model.documentAiReviewState.value.messages[doc.documentId]?.contains("Bitte eine gültige Zahl") == true }
        ui.onNodeWithTag("document_review_message").performScrollTo().assertIsDisplayed()
        capture("9b-document-error-font-$fontScale", ui.onAllNodes(isDialog()).onLast())
        assertEquals(doc, runBlocking(Dispatchers.IO) { db.managedDocumentDao().getById(doc.documentId) })
        assertTrue(doc.documentId in model.documentAiReviewState.value.pending)
        assertEquals(0.0, runBlocking(Dispatchers.IO) { db.propertyDao().getPropertyByPropertyId(property.propertyId)!!.gesamtKaufpreis }, 0.0)
    }
}

@RunWith(AndroidJUnit4::class) class Phase9BNumber10EmulatorTest : Phase9BNumberContractMatrix() { override val fontScale = 1.0f }
@RunWith(AndroidJUnit4::class) class Phase9BNumber13EmulatorTest : Phase9BNumberContractMatrix() { override val fontScale = 1.3f }
@RunWith(AndroidJUnit4::class) class Phase9BNumber15EmulatorTest : Phase9BNumberContractMatrix() { override val fontScale = 1.5f }
