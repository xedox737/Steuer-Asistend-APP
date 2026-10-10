package com.example.e2e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import com.example.ui.AppScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Full requested screen matrix, real system font scale and production navigation. */
abstract class Phase6BFontScaleMatrix : EmulatorTestSupport() {
    @Test fun fullScreenMatrixPreservesWordsButtonsAndBackNavigation() {
        assertTrue("CI-Gerätebreite nahe 393 dp", context.resources.configuration.screenWidthDp in 385..405)
        scenario.onActivity { assertEquals(fontScale, it.resources.configuration.fontScale, .01f) }
        completeText("Immobilien. Finanzen. Steuern.")
        completeText("Schön, dass du da bist!")
        completeText("Immobilien")
        completeText("Fehlende Bankbelege", scroll = true)
        capture("phase6b-dashboard-bankbelege-font-$fontScale")
        completeText("Kontoauszüge importieren", scroll = true)
        capture("phase6b-dashboard-font-$fontScale")
        val property = seedProperty("font-review", "MFH Lindenstraße")
        val model = vm
        val receipt = expense(682, 119.0, property.propertyId, "2026-10-10").copy(
            aussteller = "Handwerkerrechnung", positionenJson = ReceiptItemConverter.toJson(listOf(ReceiptItem(gesamtpreis = 119.0, steuersatz = 19.0))))
        runBlocking(Dispatchers.IO) {
            db.propertyDao().insertPropertyMetadata(property.copy(uebergangNutzenLasten = "2026-01-01", gebaeudewert = 320_000.0))
            db.receiptDao().insertReceipt(receipt)
        }
        ui.runOnIdle { model.saveRenovationMeasure(RenovationMeasure(id = "font-bath", propertyId = property.propertyId, name = "Bad OG links")) }
        ui.waitUntil(10_000) { model.renovationReview.value.measures.any { it.id == "font-bath" } }
        clickTab(AppScreen.PROPERTIES)
        ui.onNodeWithTag("property_card_${property.propertyId}").performScrollTo().performClick()
        completeText("Sanierung & 15%-Prüfung", scroll = true)
        capture("phase6b-property-font-$fontScale")
        ui.onNode(hasText("Sanierung & 15%-Prüfung") and hasClickAction()).performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("renovation_overview").fetchSemanticsNodes().isNotEmpty() }
        completeText("Sanierung & 15%-Prüfung")
        completeText("Sanierungs- & 15%-Prüfung", scroll = true)
        completeText("Keine automatische Steuerentscheidung.", scroll = true)
        ui.onNodeWithTag("renovation_overview").performScrollToNode(hasTestTag("renovation_add_measure"))
        ui.onNodeWithTag("renovation_add_measure").assertIsDisplayed().assertIsEnabled()
        capture("phase6b-renovation-font-$fontScale")
        ui.onNodeWithContentDescription("Zurück").assertIsDisplayed().performClick()
        clickTab(AppScreen.RECEIPTS_LIST)
        ui.onNodeWithTag("receipts_list").performScrollToNode(hasTestTag("receipt_item_682"))
        ui.onNodeWithTag("receipt_item_682").performClick()
        completeText("Handwerkerrechnung", scroll = true)
        ui.waitUntil(10_000) { ui.onAllNodesWithTag("receipt_renovation_card").fetchSemanticsNodes().isNotEmpty() }
        completeText("Sanierungs- & 15%-Prüfung", scroll = true)
        ui.onNodeWithTag("renovation_assign").performScrollTo().assertIsDisplayed().assertIsEnabled()
        capture("phase6b-receipt-font-$fontScale")
        systemBack()
        assertEquals(AppScreen.RECEIPTS_LIST, model.currentScreen.value)
        clickMore("DATEV Export")
        completeText("DATEV Export")
        completeText("Export prüfen", scroll = true)
        ui.onNodeWithText("Export vorbereiten").performScrollTo().assertIsDisplayed().assertIsEnabled()
        capture("phase6b-datev-font-$fontScale")
        listOf(AppScreen.ADD_RECEIPT, AppScreen.PROPERTIES, AppScreen.MORE, AppScreen.DASHBOARD).forEach { clickTab(it) }
        completeText("Immobilien")
    }

    private fun completeText(text: String, scroll: Boolean = false) {
        val node = ui.onAllNodesWithText(text, useUnmergedTree = true).onFirst()
        if (scroll) node.performScrollTo()
        node.assertIsDisplayed()
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
        assertTrue(results.isNotEmpty())
        results.forEach { layout ->
            val last = layout.lineCount - 1
            assertFalse("$text: abgeschnitten", layout.multiParagraph.didExceedMaxLines)
            assertEquals("$text: Textende fehlt", text.length, layout.getLineEnd(last, visibleEnd = true))
            // Android trims line-height padding; lineBottom can include the trimmed descent.
            // TextLayoutResult's visual overflow flag compares the actual paragraph and box.
            assertFalse("$text: Höhe (Text ${layout.multiParagraph.height}, Box ${layout.size.height})", layout.didOverflowHeight)
            (0..last).forEach { line ->
                assertFalse("$text: Ellipse", layout.isLineEllipsized(line))
                assertTrue("$text: Breite", layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= layout.size.width + 1f)
                if (line < last) {
                    val end = layout.getLineEnd(line, visibleEnd = true)
                    val next = layout.getLineStart(line + 1)
                    assertFalse("$text: Wort mitten im Wort getrennt", end == next && end in 1 until text.length && text[end - 1].isLetter() && text[end].isLetter())
                }
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class Phase6BFontScale10EmulatorTest : Phase6BFontScaleMatrix() { override val fontScale = 1.0f }
@RunWith(AndroidJUnit4::class)
class Phase6BFontScale13EmulatorTest : Phase6BFontScaleMatrix() { override val fontScale = 1.3f }
@RunWith(AndroidJUnit4::class)
class Phase6BFontScale15EmulatorTest : Phase6BFontScaleMatrix() { override val fontScale = 1.5f }
