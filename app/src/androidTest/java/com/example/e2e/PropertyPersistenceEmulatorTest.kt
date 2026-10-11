package com.example.e2e

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.ui.AppScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PropertyPersistenceEmulatorTest : EmulatorTestSupport() {
    @Test fun propertyAndUnitCreatedWithGermanNumbersSurviveRecreationAndFreshActivity() {
        clickTab(AppScreen.PROPERTIES)
        ui.onNodeWithTag("add_property_button").performClick()
        input("Objektname", "Testobjekt Persistenz", scroll = false)
        input("Straße und Hausnummer", "Beispielstraße 1", scroll = false)
        input("Kaufdatum", "15.03.2021", scroll = false)
        ui.onNode(hasText("Kaufdatum") and hasSetTextAction()).performClick()
        ui.onNode(calendarDay("15"), useUnmergedTree = true).performClick()
        ui.onNodeWithTag("app_date_apply").performClick()
        input("Kaufpreis €", "1.050.000,25", scroll = false)
        nextWizardStep("2/5")
        input("Baujahr", "1968", scroll = false)
        input("Wohnfläche m²", "620,50", scroll = false)
        input("Grundstücksfläche m²", "950,25", scroll = false)
        nextWizardStep("3/5")
        nextWizardStep("4/5")
        input("Name", "WE 01")
        input("Lage / Bezeichnung", "Testwohnung Persistenz")
        input("Wohnfläche m²", "41,25")
        nextWizardStep("5/5")
        ui.onNodeWithTag("property_wizard_next").performClick()

        val originalModel = vm
        ui.waitUntil(10_000) { originalModel.properties.value.size == 1 &&
            originalModel.wohneinheitenStatus.value.singleOrNull()?.wohnflaeche == 41.25 }
        val saved = originalModel.properties.value.single()
        val unit = originalModel.wohneinheitenStatus.value.single()
        assertEquals("2021-03-15", saved.notariellesKaufdatum)
        assertEquals(1050000.25, saved.gesamtKaufpreis, .001)
        assertEquals(620.50, saved.wohnflaeche, .001)
        assertEquals(950.25, saved.grundstuecksgroesse, .001)
        assertTrue(saved.propertyId.isNotBlank())
        assertTrue(unit.unitId.isNotBlank())

        clickTab(AppScreen.DASHBOARD)
        openUnits(saved.propertyId)
        scrollToPersistedUnit()
        capture("property-persisted")
        scenario.recreate()
        ui.onNodeWithTag("property_units_overview").assertIsDisplayed()
        assertEquals(unit.unitId, vm.wohneinheitenStatus.value.single().unitId)

        // recreate() alone retains the ViewModel. Close and launch to prove disk reload.
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        ui.onNodeWithText("Willkommen bei ImmoPilot").assertIsDisplayed()
        val reloadedModel = vm
        assertNotSame(originalModel, reloadedModel)
        ui.waitUntil(10_000) { reloadedModel.properties.value.any { it.propertyId == saved.propertyId } &&
            reloadedModel.wohneinheitenStatus.value.any { it.unitId == unit.unitId } }
        assertEquals(saved, reloadedModel.properties.value.single())
        assertEquals(unit, reloadedModel.wohneinheitenStatus.value.single())

        // An independent Room connection verifies persisted SQLite data, not a VM cache.
        runBlocking(Dispatchers.IO) {
            val disk = Room.databaseBuilder(context, AppDatabase::class.java, "receipt_database").build()
            try { assertEquals(saved, disk.propertyDao().getPropertyByPropertyId(saved.propertyId)) }
            finally { disk.close() }
        }
        assertTrue(context.getSharedPreferences("wohneinheiten_prefs", 0).all.values.contains(unit.unitId))
        openUnits(saved.propertyId)
        scrollToPersistedUnit()
        ui.onNodeWithText("Testwohnung Persistenz").performClick()
        ui.onNodeWithText("Wohneinheit im Überblick").assertIsDisplayed()
        capture("property-after-fresh-activity")
    }

    private fun scrollToPersistedUnit() {
        ui.onNodeWithTag("property_units_overview").performScrollToNode(hasText("Testwohnung Persistenz"))
        ui.onNodeWithText("Testwohnung Persistenz").assertIsDisplayed()
    }

    private fun nextWizardStep(step: String) {
        ui.onNodeWithTag("property_wizard_next").assertIsEnabled().performClick()
        ui.waitUntil(10_000) {
            ui.onAllNodesWithText("Immobilie anlegen · $step").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithText("Immobilie anlegen · $step").assertIsDisplayed()
    }
}

private fun calendarDay(day: String) = SemanticsMatcher("Kalendertag $day") { node ->
    (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).any {
        Regex("(^|\\D)$day(\\D|$)").containsMatchIn(it)
    } && node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick) != null
}
