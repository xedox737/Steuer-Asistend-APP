package com.example.e2e

import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MainActivity
import com.example.ui.AppScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatePersistenceEmulatorTest : EmulatorTestSupport() {
    @Test fun purchaseAndTransferCalendarsPersistWithoutChangingTaxBasis() {
        val property = seedProperty("date-tax", "Kalenderobjekt")
        val model = vm
        clickTab(AppScreen.PROPERTIES)
        ui.onNodeWithTag("property_card_${property.propertyId}").performClick()
        ui.onNodeWithText("Stammdaten").performClick()
        ui.onNodeWithTag("property_data").performScrollToIndex(2)
        ui.onNodeWithText("Stammdaten bearbeiten").performClick()
        selectCalendarDate("edit_property_notarielles_kaufdatum", "01.10.2025", "1")
        selectCalendarDate("edit_property_uebergang", "01.01.2026", "1")
        ui.onNodeWithTag("save_property_metadata_button").performClick()
        ui.waitUntil(10_000) { model.properties.value.single().uebergangNutzenLasten == "2026-01-01" }
        val saved = runBlocking(Dispatchers.IO) { db.propertyDao().getPropertyByPropertyId(property.propertyId)!! }
        assertEquals("2025-10-01", saved.notariellesKaufdatum)
        assertEquals("2026-01-01", saved.uebergangNutzenLasten)
        assertEquals(property.id, saved.id)
        assertEquals(property.gesamtKaufpreis, saved.gesamtKaufpreis, 0.0)
        ui.onNodeWithTag("property_data").performScrollToIndex(2)
        ui.onNodeWithText("Stammdaten bearbeiten").performClick()
        ui.onNodeWithTag("edit_property_notarielles_kaufdatum").performScrollTo().assertTextContains("01.10.2025")
        ui.onNodeWithTag("edit_property_uebergang").performScrollTo().assertTextContains("01.01.2026")
        capture("date-property-persisted")
    }
    @Test fun tripDateCalendarSurvivesFreshActivityWithoutLosingTripOrReceipt() {
        val property = seedProperty("date-trip", "Fahrtenobjekt")
        val model = vm
        runBlocking(Dispatchers.IO) { db.propertyDao().insertPropertyMetadata(property.copy(wohnort = "Teststart 1, Dornstetten")) }
        ui.waitUntil(10_000) { model.propertyMetadata.value?.wohnort == "Teststart 1, Dornstetten" }
        clickMore("Fahrtenbuch")
        ui.onNodeWithText("Neue Fahrt").performScrollTo().performClick()
        ui.onNode(hasText("Datum") and hasClickAction()).performScrollTo().performClick()
        selectCalendarDate("logbook_date", "11.10.2026", "11")
        ui.onNodeWithText("Übernehmen").performClick()
        ui.onNode(hasText("Zweck der Fahrt") and hasClickAction()).performScrollTo().performClick()
        ui.onNode(hasSetTextAction()).performTextReplacement("Besichtigung Kalenderobjekt")
        Espresso.closeSoftKeyboard()
        ui.onNodeWithText("Übernehmen").performClick()
        ui.onNode(hasText("Entfernung") and hasClickAction()).performScrollTo().performClick()
        input("Tacho Start", "1000")
        input("Tacho Ende", "1020")
        ui.onNodeWithTag("book_trip_button_0").performClick()
        ui.onNodeWithTag("logbook_confirmation").performScrollTo().performClick()
        ui.onNodeWithTag("book_trip_button_0").assertIsEnabled().performClick()
        ui.waitUntil(10_000) { model.logbookTrips.value.size == 1 }
        val trip = model.logbookTrips.value.single()
        assertEquals("2026-10-11", trip.date)
        ui.waitUntil(10_000) { model.receipts.value.any { it.id == trip.expenseReceiptId } }
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val reloaded = vm
        ui.waitUntil(10_000) { reloaded.logbookTrips.value.any { it.id == trip.id } }
        assertEquals("2026-10-11", reloaded.logbookTrips.value.single().date)
        ui.waitUntil(10_000) { reloaded.receipts.value.any { it.id == trip.expenseReceiptId } }
        clickMore("Fahrtenbuch")
        ui.onNodeWithText("11.10.2026").performScrollTo().assertIsDisplayed()
        capture("date-logbook-after-fresh-activity")
    }
}
