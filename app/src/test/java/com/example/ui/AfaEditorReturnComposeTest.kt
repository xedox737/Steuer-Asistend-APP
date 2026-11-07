package com.example.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.PropertyMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class AfaEditorReturnComposeTest {
    @get:Rule(order = 0) val applicationIsolation = IsolatedAndroidApplicationRule()
    @get:Rule(order = 1) val databaseIsolation = IsolatedAppDatabaseRule()
    @get:Rule(order = 2) val ui = createAndroidComposeRule<ComponentActivity>()

    @Test fun openingTargetedPropertyEditorAndCancellingKeepsTheSameAfaPropertyAndTab() {
        val application = ui.activity.application as Application
        val database = AppDatabase.getDatabase(application, CoroutineScope(Dispatchers.IO))
        val other = PropertyMetadata(id = 1, propertyId = "afa-return-a", name = "Anderes Haus", adresse = "Andere Straße 1")
        val target = PropertyMetadata(id = 2, propertyId = "afa-return-b", name = "AfA Zielhaus", adresse = "Lindenstraße 12",
            baujahr = 1954, gesamtKaufpreis = 200_000.0, gebaeudewert = 160_000.0, grundUndBodenWert = 40_000.0,
            notariellesKaufdatum = "2020-01-01", uebergangNutzenLasten = "2020-01-01")
        application.getSharedPreferences("google_drive_prefs", 0).edit().putString("selected_property_id", other.propertyId).commit()
        runBlocking(Dispatchers.IO) {
            database.propertyDao().insertPropertyMetadata(other)
            database.propertyDao().insertPropertyMetadata(target)
        }
        val model = ui.runOnIdle { ViewModelProvider(ui.activity)[ReceiptViewModel::class.java] }
        val originalScreen = model.currentScreen.value
        var parentBack = 0
        ui.setContent { MaterialTheme { AfaPortfolioScreen(model) { parentBack++ } } }
        ui.waitUntilAndroidState(10_000) { model.properties.value.size == 2 }
        ui.onNodeWithTag("afa_property_${target.propertyId}").performScrollTo().performClick()
        ui.onNodeWithText("Immobilien-Stammdaten bearbeiten").performScrollTo().performClick()
        ui.mainClock.advanceTimeByFrame()
        ui.runOnUiThread {
            val dialog = ShadowDialog.getLatestDialog()
            assertTrue(dialog.isShowing)
            val density = ui.activity.resources.displayMetrics.density
            dialog.window!!.setLayout((360 * density).toInt(), (700 * density).toInt())
        }
        ui.onNodeWithText("Objekt-Stammdaten bearbeiten").assertIsDisplayed()
        ui.onNodeWithTag("edit_property_name").performScrollTo().assertTextContains(target.name)
        ui.onNodeWithTag("edit_property_adresse").performScrollTo().assertTextContains(target.adresse)
        ui.onNodeWithTag("edit_property_name").performScrollTo().performTextReplacement("Ungespeicherte Änderung")
        ui.onNodeWithText("Abbrechen").performClick()
        ui.onNodeWithText("Objekt-Stammdaten bearbeiten").assertDoesNotExist()
        ui.onNodeWithText("Immobilien-Stammdaten bearbeiten").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("afa_property_${target.propertyId}").assertDoesNotExist()
        ui.onNodeWithText(target.name).performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Immobilien-Stammdaten bearbeiten").performScrollTo().performClick()
        ui.runOnIdle {
            val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
            assertTrue(dialog.isShowing)
            dialog.onBackPressedDispatcher.onBackPressed()
        }
        ui.onNodeWithText("Objekt-Stammdaten bearbeiten").assertDoesNotExist()
        ui.onNodeWithText("Immobilien-Stammdaten bearbeiten").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("afa_property_${target.propertyId}").assertDoesNotExist()
        assertEquals(originalScreen, model.currentScreen.value)
        assertEquals(0, parentBack)
        assertEquals(other.propertyId, application.getSharedPreferences("google_drive_prefs", 0).getString("selected_property_id", null))
        runBlocking(Dispatchers.IO) {
            assertEquals(target, database.propertyDao().getPropertyByPropertyId(target.propertyId))
            assertEquals(other, database.propertyDao().getPropertyByPropertyId(other.propertyId))
        }
    }
}
