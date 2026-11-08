package com.example.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.data.PropertyMetadata
import com.example.data.Receipt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class LedgerFilterAccessComposeTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    @Test fun propertyCategoryPeriodAndSearchAreReachableBeforeMetricsWithoutInitialScrolling() {
        val properties = listOf(PropertyMetadata(propertyId = "filter-a", name = "Haus A"),
            PropertyMetadata(id = 2, propertyId = "filter-b", name = "Haus B"))
        val receipts = listOf(
            Receipt(id = 1, aussteller = "Handwerker A", datum = "2026-11-01", uhrzeit = "", bruttobetrag = 100.0,
                hauptkategorie = "Sanierung", unterkategorie = "Reparatur", kontoNr = "4800", beschreibung = "Haus A", propertyId = "filter-a"),
            Receipt(id = 2, aussteller = "Handwerker B", datum = "2026-11-01", uhrzeit = "", bruttobetrag = 200.0,
                hauptkategorie = "Sanierung", unterkategorie = "Reparatur", kontoNr = "4800", beschreibung = "Haus B", propertyId = "filter-b")
        )
        ui.setContent { MaterialTheme { LedgerOverview(receipts = receipts, properties = properties, onReceipt = {}) } }
        listOf("ledger_year", "ledger_property", "ledger_category", "ledger_period", "ledger_search").forEach { tag ->
            ui.onNodeWithTag(tag).assertIsDisplayed()
        }
        val search = ui.onNodeWithTag("ledger_search").fetchSemanticsNode().boundsInRoot
        val metric = ui.onNodeWithTag("ledger_metric_Einnahmen").fetchSemanticsNode().boundsInRoot
        assertTrue("Filters and search must precede the summary cards", search.bottom <= metric.top)
        ui.onNodeWithTag("ledger_property").performClick()
        ui.onNode(hasText("Haus B") and hasAnyAncestor(isPopup())).performClick()
        ui.onNodeWithTag("ledger_property").assertTextContains("Haus B", substring = true)
        ui.onNodeWithTag("ledger_overview").performScrollToNode(hasTestTag("ledger_receipt_2"))
        ui.onNodeWithTag("ledger_receipt_2").assertIsDisplayed()
        ui.onNodeWithTag("ledger_receipt_1").assertDoesNotExist()
    }
}
