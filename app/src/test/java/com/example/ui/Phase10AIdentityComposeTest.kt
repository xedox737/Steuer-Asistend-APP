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
class Phase10AIdentityComposeTest {
    private val property = PropertyMetadata(id = 102, propertyId = "phase10a-compose", name = "Kommahaus",
        wohneinheiten = UnitIdentity.encode(listOf(UnitIdentity.Entry("U1", "WE 01, EG links"), UnitIdentity.Entry("U2", "WE 02"))))
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
            check(context.getSharedPreferences("wohneinheiten_prefs", 0).edit()
                .putFloat("property_${property.propertyId}_unitid_U1_area", 41.25f)
                .putFloat("property_${property.propertyId}_unitid_U2_area", 55.5f).commit())
            context.getSharedPreferences("google_drive_prefs", 0).edit().putString("selected_property_id", property.propertyId).commit()
            runBlocking(Dispatchers.IO) { db.propertyDao().insertPropertyMetadata(property) }

        }
    }
    @get:Rule(order = 3) val ui = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(ui.activity)[ReceiptViewModel::class.java]
    @Test fun productionUnitDetailShowsCommaNameAndFractionAfterReopen() {
        val model = ui.runOnIdle { vm }
        ui.onNodeWithTag("nav_item_properties").performClick()
        ui.waitUntilAndroidState(10_000) { model.properties.value.any { it.propertyId == property.propertyId } }
        ui.onNodeWithTag("property_card_${property.propertyId}").performScrollTo().performClick()
        ui.waitUntilAndroidState(10_000) { model.propertyMetadata.value?.propertyId == property.propertyId && model.wohneinheitenStatus.value.size == 2 }
        ui.onNode(hasText("Einheiten") and hasClickAction()).performScrollTo().performClick()
        repeat(2) {
            ui.onNodeWithTag("property_units_overview").performScrollToNode(hasTestTag("unit_card_U1"))
            ui.onNodeWithText("WE 01, EG links").assertIsDisplayed()
            ui.onNodeWithTag("unit_card_U1").performClick()
            ui.onNodeWithTag("unit_detail_list").performScrollToNode(hasText("41,25 m²"))
            ui.onNodeWithText("41,25 m²").assertIsDisplayed()
            assertEquals(listOf("U1", "U2"), model.getWohneinheitenForProperty(property).map { unit -> unit.unitId })
            ui.onNodeWithContentDescription("Zurück").performClick()
        }
    }
}
