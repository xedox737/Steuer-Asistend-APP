package com.example.e2e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import com.example.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real MainActivity, Room and confirmed-value preferences; no alternate navigation host. */
abstract class Phase9DAfaMatrix : EmulatorTestSupport() {
    @Test fun signedBasisAndRemainingCutoffYearSurviveRecreateAndBothBackActions() {
        ui.waitUntil(10_000) {
            var actual = 0f
            scenario.onActivity { actual = it.resources.configuration.fontScale }
            kotlin.math.abs(actual - fontScale) < .01f
        }
        assertEquals(393, context.resources.configuration.screenWidthDp)
        val initial = seedProperty("9d-native", "AfA-Korrekturhaus")
        val property = initial.copy(baujahr = 1954, gesamtKaufpreis = 200_000.0, gebaeudewert = 200_000.0,
            grundUndBodenWert = 0.0, uebergangNutzenLasten = "2020-01-01", notariellesKaufdatum = "2020-01-01")
        val confirmed = ConfirmedAfaValues("2026-06-30", 27_170.0, 181_830.0, "Steuerberater")
        fun receipt(id: Int, amount: Double) = Receipt(id, "Testnotar", "2020-01-15", "", amount,
            "Anschaffungskosten", "Notar", "", if (amount < 0) "Gutschrift" else "Notarkosten",
            propertyId = property.propertyId, internalId = "9d-native-$id")
        val invoice = receipt(996, 10_000.0)
        val credit = receipt(997, -1_000.0)
        val model = vm
        runBlocking(Dispatchers.IO) {
            assertTrue(ConfirmedAfaValuesStore.write(context, property.propertyId, confirmed))
            db.propertyDao().insertPropertyMetadata(property)
            db.receiptDao().insertReceipt(invoice)
            db.receiptDao().insertReceipt(credit)
        }
        val originalRaw = context.getSharedPreferences("afa_confirmed_values_prefs", 0).getString(property.propertyId, null)
        clickMore("AfA Gebäude")
        // Let the real destination start the WhileSubscribed receipt collection.
        ui.waitUntil(10_000) { model.properties.value.any { it.propertyId == property.propertyId && it.gebaeudewert == 200_000.0 } && model.receipts.value.size == 2 }
        ui.onNodeWithTag("afa_property_${property.propertyId}").performScrollTo().performClick()
        ui.onNodeWithText("AfA Berechnung").performScrollTo().performClick()
        ui.onNodeWithText("209.000,00 €").performScrollTo().assertIsDisplayed()
        ui.onAllNodesWithText("4.180,00 €").onFirst().performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Verlauf").performScrollTo().performClick()
        fun checkRestYear() {
            ui.waitUntil(10_000) { ui.onAllNodesWithTag("afa_forecast_2026").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("afa_forecast_2026").performScrollTo().assertIsDisplayed()
            completeText("2026 (6 Monate)")
            completeText("2.090,00 €", hasAnyAncestor(hasTestTag("afa_forecast_2026")))
        }
        checkRestYear()
        capture("9d-afa-restjahr-font-$fontScale")
        scenario.recreate()
        checkRestYear()
        assertEquals(originalRaw, context.getSharedPreferences("afa_confirmed_values_prefs", 0).getString(property.propertyId, null))
        assertEquals(confirmed, ConfirmedAfaValuesStore.read(context, property.propertyId))
        ui.onNodeWithText("Bestätigte Vorjahreswerte bearbeiten").performScrollTo().performClick()
        ui.onNodeWithText(AFA_CUTOFF_MONTH_NOTE, useUnmergedTree = true).performScrollTo()
        completeText(AFA_CUTOFF_MONTH_NOTE)
        capture("9d-afa-stichtag-hinweis-font-$fontScale", ui.onAllNodes(isDialog()).onLast())
        ui.onNodeWithText("Abbrechen").performClick()
        // The LazyColumn header was recycled while scrolling to the forecast.
        // Bring its item back into composition before finding the real app arrow.
        ui.onNodeWithTag("afa_portfolio").performScrollToNode(hasContentDescription("Zurück"))
        ui.onNodeWithContentDescription("Zurück").assertIsDisplayed().performClick()
        ui.onNodeWithTag("afa_property_${property.propertyId}").performScrollTo().assertIsDisplayed()
        systemBack()
        assertEquals(AppScreen.MORE, vm.currentScreen.value)
        val tax = TaxPropertyCalculator.calculate(property, listOf(invoice, credit))
        val review = RenovationReviewCalculator.calculate(property, listOf(invoice, credit), RenovationReviewSnapshot(emptyList(), emptyList()))
        assertEquals(31_350.0, review.basis.limit15Percent, .001)
        assertEquals(tax, review.basis)
        assertEquals(credit, runBlocking(Dispatchers.IO) { db.receiptDao().getReceiptById(credit.id) })
        assertEquals(property, runBlocking(Dispatchers.IO) { db.propertyDao().getPropertyByPropertyId(property.propertyId) })

        // A damaged legacy basis stays stored and visible as a repairable review case.
        val damaged = credit.copy(bruttobetrag = -300_000.0)
        runBlocking(Dispatchers.IO) { db.receiptDao().insertReceipt(damaged) }
        ui.waitUntil(10_000) { vm.receipts.value.any { it.id == damaged.id && it.bruttobetrag == -300_000.0 } }
        clickMore("AfA Gebäude")
        val error = TaxPropertyCalculator.calculate(property, listOf(invoice, damaged)).acquisitionBasisError!!
        ui.onNodeWithText(error, useUnmergedTree = true).performScrollTo()
        completeText(error)
        capture("9d-afa-basis-prueffall-font-$fontScale")
        assertEquals(-300_000.0, runBlocking(Dispatchers.IO) { db.receiptDao().getReceiptById(credit.id)!!.bruttobetrag }, 0.0)
        assertEquals(originalRaw, context.getSharedPreferences("afa_confirmed_values_prefs", 0).getString(property.propertyId, null))
    }

    private fun completeText(text: String, extra: SemanticsMatcher = SemanticsMatcher("beliebiger Text") { true }) {
        val node = ui.onNode(hasText(text) and extra, useUnmergedTree = true)
        node.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach {
            assertFalse(text, it.didOverflowWidth)
            assertFalse(text, it.didOverflowHeight)
            assertFalse(text, it.multiParagraph.didExceedMaxLines)
            assertEquals(text.length, it.getLineEnd(it.lineCount - 1, visibleEnd = true))
            (0 until it.lineCount).forEach { line -> assertFalse(text, it.isLineEllipsized(line)) }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class Phase9DAfa10EmulatorTest : Phase9DAfaMatrix() { override val fontScale = 1.0f }
@RunWith(AndroidJUnit4::class)
class Phase9DAfa13EmulatorTest : Phase9DAfaMatrix() { override val fontScale = 1.3f }
@RunWith(AndroidJUnit4::class)
class Phase9DAfa15EmulatorTest : Phase9DAfaMatrix() { override val fontScale = 1.5f }
