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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class Phase9CRentArrearsComposeTest {
    private val month = YearMonth.now()
    private val property = PropertyMetadata(id = 89, propertyId = "p9c-compose", name = "Rückstandshaus", wohneinheiten = "WE A,WE B")
    private val units = listOf("A", "B").map { WohneinheitStatus("WE $it", "Einheit $it", "Vermietet", "Mieter $it", 980.0, 70.0, month.atDay(1).toString(), "p9c-$it") }
    @get:Rule(order = 0) val applicationIsolation = IsolatedAndroidApplicationRule()
    @get:Rule(order = 1) val databaseIsolation = IsolatedAppDatabaseRule()
    // Seed before MainActivity/ViewModel/Room-flow observers start. This is a presentation fixture,
    // not an asynchronous import/update test; the Android E2E covers real activity persistence.
    @get:Rule(order = 2) val fixture = object : ExternalResource() {
        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val db = AppDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
            listOf("google_drive_prefs", "wohneinheiten_prefs", "rent_plan_prefs", "tenant_history_prefs").forEach {
                context.getSharedPreferences(it, 0).edit().clear().commit()
            }
            val prefs = context.getSharedPreferences("wohneinheiten_prefs", 0).edit()
            units.forEachIndexed { i, unit ->
                val prefix = "property_${property.propertyId}_unit_${unit.name}_"
                prefs.putString(prefix + "id", unit.unitId).putString(prefix + "label", unit.label)
                    .putString(prefix + "status", unit.status).putString(prefix + "mieter", unit.mieter)
                    .putFloat(prefix + "rent", 980f).putFloat(prefix + "area", 70f)
                    .putString(prefix + "start", unit.mietvertragsstart)
                    .putString("property_${property.propertyId}_unit_id_index_$i", unit.unitId)
                TenantHistoryStore.save(context, property.propertyId, unit.unitId, unit.name,
                    listOf(TenantPeriod(900 + i.toLong(), unit.name, unit.mieter, month.atDay(1).toString(), month.atEndOfMonth().toString(), 980.0, 0.0, 0.0)))
            }
            check(prefs.commit())
            context.getSharedPreferences("google_drive_prefs", 0).edit().putString("selected_property_id", property.propertyId).commit()
            runBlocking(Dispatchers.IO) {
                db.propertyDao().insertPropertyMetadata(property)
                db.receiptDao().insertReceipt(Receipt(991, "Mieter A", month.atDay(3).toString(), "", 1960.0,
                    "Miete, Nebenkosten & Kaution", "Kaltmiete", "8100", "Überzahlung", propertyId = property.propertyId,
                    unitId = units[0].unitId, wohneinheit = units[0].name, pruefstatus = "GEPRUEFT", exportStatus = "BEREIT"))
                assertEquals(property, db.propertyDao().getAllProperties().single())
            }
        }
    }
    @get:Rule(order = 3) val ui = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(ui.activity)[ReceiptViewModel::class.java]
    @Test fun propertyAndUnitsShowUnpaidUnitDespiteAnotherUnitsSurplus() {
        val model = ui.runOnIdle { vm }
        ui.onNodeWithTag("nav_item_properties").performClick()
        // Room resumes collectors on Android's main looper, not Compose's frame clock.
        // Drain that looper while awaiting the unchanged production-flow condition.
        ui.waitUntil(10_000) { ui.runOnIdle {
            model.properties.value.any { it.propertyId == property.propertyId } && model.receipts.value.any { it.id == 991 }
        } }
        ui.onNodeWithTag("property_card_${property.propertyId}").performScrollTo()
        ui.onNode(hasText("980,00 €") and hasAnyAncestor(hasTestTag("property_outstanding")), useUnmergedTree = true).assertIsDisplayed()
        ui.onNodeWithTag("property_card_${property.propertyId}").performClick()
        ui.waitUntil(10_000) { ui.runOnIdle {
            model.propertyMetadata.value?.propertyId == property.propertyId && model.wohneinheitenStatus.value.size == 2
        } }
        ui.onNode(hasText("Einheiten") and hasClickAction()).performScrollTo().performClick()
        ui.onNodeWithTag("property_units_overview").performScrollToNode(hasTestTag("unit_annual_outstanding"))
        ui.onNode(hasText("980,00 €") and hasAnyAncestor(hasTestTag("unit_annual_outstanding")), useUnmergedTree = true).assertIsDisplayed()
        ui.onNodeWithText("ImmoPilot").assertIsDisplayed()
        ui.onNodeWithTag("bottom_navigation").assertIsDisplayed()
    }
}
