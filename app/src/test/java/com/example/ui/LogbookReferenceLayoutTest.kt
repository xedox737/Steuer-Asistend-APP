package com.example.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.PropertyMetadata
import com.example.data.Receipt
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class LogbookReferenceLayoutTest {
    @get:Rule val ui = createComposeRule()

    @Test fun routeAndTripDetailsFitAboveTheExistingNavigation() {
        val viewModel = ReceiptViewModel(ApplicationProvider.getApplicationContext<Application>())
        val receipt = Receipt(
            id = 51, aussteller = "Bauhaus", datum = "2025-12-05", uhrzeit = "",
            bruttobetrag = 0.0, hauptkategorie = "Sanierung", unterkategorie = "",
            kontoNr = "", beschreibung = "Duschkabine, Thermostatbatterie, Silikon, Fliesenkleber"
        )
        ui.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(64.dp)) { Text("ImmoPilot") }
                    Box(Modifier.weight(1f)) {
                        LogbookEntryScreen(receipt, PropertyMetadata(
                            wohnort = "Hauptstraße 1, 12345 Wohnstadt",
                            adresse = "Musterstraße 42, 12345 Musterstadt"
                        ), viewModel, onBack = {}, onSaved = {})
                    }
                    Box(Modifier.fillMaxWidth().height(80.dp)) { Text("Scannen") }
                }
            }
        }
        ui.onNodeWithText("Fahrtdetails").assertIsDisplayed()
        ui.onNodeWithText("Fahrt prüfen").assertIsDisplayed()
        ui.onRoot().captureRoboImage("build/reports/logbook-reference/route.png")
    }
}
