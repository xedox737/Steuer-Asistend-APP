package com.example.ui

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.core.app.ApplicationProvider
import com.example.data.DistanceEvidence
import com.example.data.PropertyMetadata
import com.example.data.Receipt
import com.example.data.TripRouteMode
import com.example.data.TripStop
import com.example.data.TripStopJson
import com.example.data.TripRouteNormalizer
import com.example.data.StandardRoute
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
class LogbookReferenceLayoutTest {
    @get:Rule val ui = createComposeRule()

    private fun capture(name: String) {
        ui.mainClock.advanceTimeBy(300)
        ui.waitForIdle()
        val path = "build/reports/logbook-reference/$name.png"
        onView(isRoot()).captureRoboImage(path)
        assertTrue("Gerendertes Bild fehlt: $name", File(path).isFile)
    }

    @Test fun referenceScreensRenderInsideTheActualAppFrame() {
        val viewModel = ReceiptViewModel(ApplicationProvider.getApplicationContext<Application>())
        viewModel.setScreen(AppScreen.LOGBOOK)
        viewModel.updatePropertyMetadata(PropertyMetadata(
            wohnort = "Dornstetten", adresse = "Sulzerstraße 32, Dornstetten"
        ))
        viewModel.insertReceiptQuietly(Receipt(id = 1001, aussteller = "Bauhaus", datum = LocalDate.now().toString(),
            uhrzeit = "", bruttobetrag = 0.0, hauptkategorie = "Sanierung", unterkategorie = "", kontoNr = "",
            beschreibung = "Besichtigung / Instandhaltung"))
        runBlocking {
            val route = TripRouteNormalizer.normalize("Dornstetten", listOf(TripStop("Bauhaus", "Zwischenstopp 1", 0)),
                "Sulzerstraße 32, Dornstetten", TripRouteMode.HIN_UND_RUECKFAHRT, true)
            viewModel.saveStandardRoute(StandardRoute(name = "Teststrecke", startAddress = "Dornstetten",
                destinationAddress = "Sulzerstraße 32, Dornstetten", stopsJson = TripStopJson.encode(route.stops),
                routeMode = TripRouteMode.HIN_UND_RUECKFAHRT.name, sameReturnRoute = true, distanceKm = 28.4,
                routeSignature = route.signature, sourceProvider = "MANUELL", createdAt = "2026-09-30", updatedAt = "2026-09-30"))
            listOf("Bauhaus Freudenstadt", "Hornbach Freudenstadt", "Sulzerstraße 32").forEachIndexed { index, destination ->
                viewModel.saveLogbookTrip(
                    originalReceipt = Receipt(aussteller = "Manuelle Fahrt", datum = LocalDate.now().minusDays(index.toLong()).toString(),
                        uhrzeit = "", bruttobetrag = 0.0, hauptkategorie = "", unterkategorie = "", kontoNr = "", beschreibung = ""),
                    purpose = if (index == 2) "Besichtigung / Instandhaltung" else "Einkauf",
                    startAddress = "Dornstetten", destinationAddress = destination,
                    stops = listOf(TripStop("Dornstetten", "Start", 0), TripStop(destination, "Ziel", 1)),
                    routeMode = TripRouteMode.HIN_UND_RUECKFAHRT, sameReturnRoute = true,
                    evidence = DistanceEvidence(manualKm = 28.4, manuallyConfirmed = true)
                ).getOrThrow()
            }
        }
        lateinit var backDispatcher: OnBackPressedDispatcher
        ui.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            MyApplicationTheme(darkTheme = false) {
                // The activity's outer handler must only run after leaving the nested views.
                BackHandler { viewModel.setScreen(AppScreen.DASHBOARD) }
                ReceiptAppUi(viewModel)
            }
        }
        fun systemBack() {
            ui.runOnUiThread { backDispatcher.onBackPressed() }
            ui.waitForIdle()
            assertEquals(AppScreen.LOGBOOK, viewModel.currentScreen.value)
        }
        ui.waitUntil(10000) { viewModel.logbookTrips.value.size >= 3 && viewModel.propertyMetadata.value?.wohnort == "Dornstetten" && viewModel.receipts.value.any { it.id == 1001 } }
        ui.onNodeWithTag("bottom_navigation").assertIsDisplayed()
        ui.onNodeWithText("ImmoPilot").assertIsDisplayed()
        ui.onNodeWithText("Letzte Fahrten").assertIsDisplayed()
        capture("overview")
        ui.onNodeWithTag("logbook_suggestion_1001").performScrollTo().performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("28,4 km").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("Fahrtdetails").assertIsDisplayed()
        ui.onNodeWithText("Entfernung").assertIsDisplayed()
        ui.onNodeWithText("Fahrt prüfen").assertIsEnabled()
        ui.onNodeWithTag("bottom_navigation").assertIsDisplayed()
        capture("route")
        ui.onNodeWithText("Fahrt prüfen").performClick()
        ui.onNodeWithText("Neue Fahrt").assertDoesNotExist()
        ui.onNodeWithText("Fahrtdaten").assertIsDisplayed()
        ui.onNodeWithText("Strecke noch nicht bestätigt").assertIsDisplayed()
        ui.onNodeWithText("Als Werbungskosten erfassen").assertIsDisplayed()
        ui.onNodeWithText("Fahrt speichern").assertIsDisplayed()
        ui.onNodeWithTag("logbook_confirmation").assertIsDisplayed()
        capture("review-unconfirmed")
        ui.onNodeWithTag("logbook_confirmation").performClick()
        ui.onNodeWithText("Strecke bestätigt").assertIsDisplayed()
        ui.onNodeWithText("Fahrt speichern").assertIsEnabled()
        ui.onNodeWithText("Fahrt speichern").assertIsDisplayed()
        ui.onNodeWithTag("bottom_navigation").assertIsDisplayed()
        capture("review")

        // An edit opened from review returns to review, including edits of the first step.
        ui.onNodeWithText("Strecke").performClick()
        ui.onNodeWithText("Strecke ermitteln und bestätigen").assertIsDisplayed()
        ui.onNodeWithContentDescription("Zurück").performClick()
        ui.onNodeWithText("Fahrtdaten").assertIsDisplayed()
        ui.onNodeWithText("Zweck der Fahrt").performClick()
        ui.onNodeWithText("Fahrtdetails").assertIsDisplayed()
        systemBack()
        ui.onNodeWithText("Fahrtdaten").assertIsDisplayed()
        systemBack()
        ui.onNodeWithText("Fahrtdetails").assertIsDisplayed()
        ui.onNodeWithText("28,4 km").assertIsDisplayed()

        // Review reached from distance must return to distance before route and overview.
        ui.onNodeWithText("Entfernung").performClick()
        ui.onNodeWithText("Weiter zur Prüfung").performClick()
        ui.onNodeWithText("Fahrtdaten").assertIsDisplayed()
        systemBack()
        ui.onNodeWithText("Strecke ermitteln und bestätigen").assertIsDisplayed()
        ui.onNodeWithContentDescription("Zurück").performClick()
        ui.onNodeWithText("Fahrtdetails").assertIsDisplayed()
        systemBack()
        ui.onNodeWithText("Letzte Fahrten").assertIsDisplayed()

        ui.onNodeWithText("Dornstetten → Bauhaus Freudenstadt").performScrollTo().performClick()
        ui.onNodeWithText("Fahrtdaten").assertIsDisplayed()
        systemBack()
        ui.onNodeWithText("Letzte Fahrten").assertIsDisplayed()
        ui.runOnUiThread { backDispatcher.onBackPressed() }
        ui.waitForIdle()
        assertEquals(AppScreen.DASHBOARD, viewModel.currentScreen.value)
    }
}
