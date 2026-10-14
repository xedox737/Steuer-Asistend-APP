package com.example.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.example.MainActivity
import com.example.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class Phase9CRentArrearsComposeTest {
    @get:Rule(order = 0) val applicationIsolation = IsolatedAndroidApplicationRule()
    @get:Rule(order = 1) val databaseIsolation = IsolatedAppDatabaseRule()
    @get:Rule(order = 2) val ui = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(ui.activity)[ReceiptViewModel::class.java]
    private val fixtureContext get() = vm.getApplication<Application>()
    private fun shell() {
        ui.onNodeWithText("ImmoPilot").assertIsDisplayed()
        ui.onNodeWithTag("bottom_navigation").assertIsDisplayed()
    }
    @Test fun propertyAndUnitsShowUnpaidUnitDespiteAnotherUnitsSurplus() {
        val month = java.time.YearMonth.now()
        val property = PropertyMetadata(id = 89, propertyId = "p9c-compose", name = "Rückstandshaus", wohneinheiten = "WE A,WE B")
        val db = AppDatabase.getDatabase(fixtureContext, CoroutineScope(Dispatchers.IO))
        runBlocking(Dispatchers.IO) {
            db.propertyDao().insertPropertyMetadata(property)
            assertEquals(property, db.propertyDao().getAllProperties().single())
        }
        val model = vm
        ui.waitUntil(10_000) { model.properties.value.any { it.propertyId == property.propertyId } }
        ui.runOnIdle { model.selectProperty(property.propertyId) }
        ui.waitUntil(10_000) { model.propertyMetadata.value?.propertyId == property.propertyId && model.wohneinheitenStatus.value.size == 2 }
        val units = model.wohneinheitenStatus.value.mapIndexed { i, unit -> unit.copy(
            status = "Vermietet", mieter = "Mieter $i", kaltmiete = 980.0, mietvertragsstart = month.atDay(1).toString()) }
        ui.runOnIdle {
            units.forEachIndexed { i, unit ->
                model.updateWohneinheit(unit)
                TenantHistoryStore.save(fixtureContext, property.propertyId, unit.unitId, unit.name,
                    listOf(TenantPeriod(900 + i.toLong(), unit.name, unit.mieter, month.atDay(1).toString(), month.atEndOfMonth().toString(), 980.0, 0.0, 0.0)))
            }
        }
        runBlocking(Dispatchers.IO) { db.receiptDao().insertReceipt(Receipt(991, "Mieter A", month.atDay(3).toString(), "", 1960.0,
            "Miete, Nebenkosten & Kaution", "Kaltmiete", "8100", "Überzahlung", propertyId = property.propertyId,
            unitId = units[0].unitId, wohneinheit = units[0].name, pruefstatus = "GEPRUEFT", exportStatus = "BEREIT")) }
        ui.waitUntil(10_000) { model.receipts.value.any { it.id == 991 } }
        ui.onNodeWithTag("nav_item_properties").performClick()
        ui.onNodeWithTag("property_card_${property.propertyId}").performScrollTo()
        ui.onNode(hasText("980,00 €") and hasAnyAncestor(hasTestTag("property_outstanding")), useUnmergedTree = true).assertIsDisplayed()
        ui.onNodeWithTag("property_card_${property.propertyId}").performClick()
        ui.onNode(hasText("Einheiten") and hasClickAction()).performScrollTo().performClick()
        ui.onNodeWithTag("property_units_overview").performScrollToNode(hasTestTag("unit_annual_outstanding"))
        ui.onNode(hasText("980,00 €") and hasAnyAncestor(hasTestTag("unit_annual_outstanding")), useUnmergedTree = true).assertIsDisplayed()
        shell()
    }
}
