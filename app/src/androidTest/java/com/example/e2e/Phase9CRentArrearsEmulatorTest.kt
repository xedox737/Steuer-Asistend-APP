package com.example.e2e

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import com.example.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

/** Both production entry points and their detail link; Room/preferences survive Activity recreation. */
@RunWith(AndroidJUnit4::class)
class Phase9CRentArrearsEmulatorTest : EmulatorTestSupport() {
    @Test fun overpaidUnitCannotHideUnpaidUnitAfterRecreate() {
        val month = YearMonth.now()
        val propertyId = "p9c-arrears"
        val property = PropertyMetadata(id = 91, propertyId = propertyId, name = "Rückstandshaus", wohneinheiten = "WE A,WE B")
        val units = listOf("A", "B").map { name -> WohneinheitStatus("WE $name", "Einheit $name", "Vermietet",
            "Mieter $name", 980.0, 70.0, month.atDay(1).toString(), "p9c-$name") }
        // One closed rental month makes month and year scopes intentionally equal.
        val periods = units.mapIndexed { i, unit -> TenantPeriod(900 + i.toLong(), unit.name, unit.mieter,
            month.atDay(1).toString(), month.atEndOfMonth().toString(), 980.0, 0.0, 0.0) }
        val receipt = Receipt(991, "Mieter A", month.atDay(3).toString(), "", 1960.0,
            "Miete, Nebenkosten & Kaution", "Kaltmiete", "8100", "Überzahlung A", propertyId = propertyId,
            unitId = units[0].unitId, wohneinheit = units[0].name, internalId = "p9c-payment", pruefstatus = "GEPRUEFT", exportStatus = "BEREIT")
        val model = vm
        runBlocking(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("wohneinheiten_prefs", 0).edit()
            units.forEachIndexed { i, unit ->
                val prefix = "property_${propertyId}_unit_${unit.name}_"
                prefs.putString(prefix + "id", unit.unitId).putString(prefix + "label", unit.label)
                    .putString(prefix + "status", unit.status).putString(prefix + "mieter", unit.mieter)
                    .putFloat(prefix + "rent", 980f).putFloat(prefix + "area", 70f)
                    .putString(prefix + "start", unit.mietvertragsstart)
                    .putString("property_${propertyId}_unit_id_index_$i", unit.unitId)
                TenantHistoryStore.save(context, propertyId, unit.unitId, unit.name, listOf(periods[i]))
            }
            check(prefs.commit())
            db.propertyDao().insertPropertyMetadata(property)
            db.receiptDao().insertReceipt(receipt)
        }
        ui.waitUntil(10_000) { model.properties.value.any { it.propertyId == propertyId } && model.receipts.value.size == 1 }
        clickTab(AppScreen.PROPERTIES)
        fun propertyAmount() {
            ui.onNodeWithTag("property_card_$propertyId").performScrollTo().assertIsDisplayed()
            ui.onNode(hasText("980,00 €") and hasAnyAncestor(hasTestTag("property_outstanding")), useUnmergedTree = true).assertIsDisplayed()
        }
        fun annualAmount() {
            ui.onNodeWithTag("property_units_overview").performScrollToNode(hasTestTag("unit_annual_outstanding"))
            ui.onNode(hasText("980,00 €") and hasAnyAncestor(hasTestTag("unit_annual_outstanding")), useUnmergedTree = true).assertIsDisplayed()
            ui.onNode(hasText("0,00 €") and hasAnyAncestor(hasTestTag("unit_annual_outstanding")), useUnmergedTree = true).assertDoesNotExist()
        }
        propertyAmount(); capture("phase9c-property-open-980")
        scenario.recreate(); propertyAmount()
        openUnits(propertyId); annualAmount(); capture("phase9c-unit-year-open-980")
        scenario.recreate(); annualAmount()
        ui.onNodeWithTag("property_units_overview").performScrollToNode(hasTestTag("unit_annual_details"))
        ui.onNodeWithTag("unit_annual_details").performClick()
        ui.onNodeWithTag("rent_overview").performScrollToNode(hasTestTag("rent_metric_missing"))
        ui.onNode(hasText("980,00 €") and hasAnyAncestor(hasTestTag("rent_metric_missing")), useUnmergedTree = true).assertIsDisplayed()
        systemBack(); annualAmount()
        assertEquals(receipt, runBlocking(Dispatchers.IO) { db.receiptDao().getReceiptById(receipt.id) })
        units.forEachIndexed { i, unit -> assertEquals(listOf(periods[i]), TenantHistoryStore.load(context, propertyId, unit.unitId, unit.name)) }
    }
}
