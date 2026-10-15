package com.example.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class Phase9DAfaComposeTest {
    private val property = PropertyMetadata(id = 95, propertyId = "9d-compose", name = "AfA-Korrekturhaus",
        baujahr = 1954, gesamtKaufpreis = 200_000.0, gebaeudewert = 200_000.0,
        uebergangNutzenLasten = "2020-01-01", notariellesKaufdatum = "2020-01-01")
    private val confirmed = ConfirmedAfaValues("2026-06-30", 27_170.0, 181_830.0, "Steuerberater")
    private var originalRaw: String? = null
    @get:Rule(order = 0) val applicationIsolation = IsolatedAndroidApplicationRule()
    @get:Rule(order = 1) val databaseIsolation = IsolatedAppDatabaseRule()
    @get:Rule(order = 2) val fixture = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val db = AppDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
            context.getSharedPreferences("google_drive_prefs", 0).edit().clear().commit()
            assertTrue(ConfirmedAfaValuesStore.write(context, property.propertyId, confirmed))
            originalRaw = context.getSharedPreferences("afa_confirmed_values_prefs", 0).getString(property.propertyId, null)
            runBlocking(Dispatchers.IO) {
                db.propertyDao().insertPropertyMetadata(property)
                listOf(96 to 10_000.0, 97 to -1_000.0).forEach { (id, amount) ->
                    db.receiptDao().insertReceipt(Receipt(id, "Testnotar", "2020-01-15", "", amount,
                        "Anschaffungskosten", "Notar", "", if (amount < 0) "Gutschrift" else "Notarkosten",
                        propertyId = property.propertyId, internalId = "9d-compose-$id"))
                }
                assertEquals(property, db.propertyDao().getAllProperties().single())
            }
        }
    }
    @get:Rule(order = 3) val ui = createAndroidComposeRule<MainActivity>()

    @Test fun productionAfaShowsCorrectedBasisAndTheCutoffYearInsteadOf2027Only() {
        val model = ViewModelProvider(ui.activity)[ReceiptViewModel::class.java]
        ui.waitUntil(10_000) { model.properties.value.any { it.propertyId == property.propertyId } && model.receipts.value.size == 2 }
        ui.onNodeWithTag("nav_item_more").performClick()
        ui.onNode(hasText("AfA Gebäude") and hasClickAction()).performScrollTo().performClick()
        ui.onNodeWithTag("afa_property_${property.propertyId}").performScrollTo().performClick()
        ui.onNodeWithText("AfA Berechnung").performScrollTo().performClick()
        ui.onNodeWithText("209.000,00 €").performScrollTo().assertIsDisplayed()
        ui.onAllNodesWithText("4.180,00 €").onFirst().performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Verlauf").performScrollTo().performClick()
        ui.onNodeWithTag("afa_forecast_2026").performScrollTo().assertIsDisplayed()
        ui.onNode(hasText("2026 (6 Monate)") and hasAnyAncestor(hasTestTag("afa_forecast_2026")), useUnmergedTree = true).assertIsDisplayed()
        ui.onNode(hasText("2.090,00 €") and hasAnyAncestor(hasTestTag("afa_forecast_2026")), useUnmergedTree = true).assertIsDisplayed()
        val context = ui.activity
        assertEquals(originalRaw, context.getSharedPreferences("afa_confirmed_values_prefs", 0).getString(property.propertyId, null))
        assertEquals(confirmed, ConfirmedAfaValuesStore.read(context, property.propertyId))
    }
}
