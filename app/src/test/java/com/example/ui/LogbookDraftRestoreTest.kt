package com.example.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.PropertyMetadata
import com.example.data.Receipt
import com.example.ui.theme.MyApplicationTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class LogbookDraftRestoreTest {
    @get:Rule val ui = createComposeRule()
    @Test fun draftSurvivesCompositionRemovalAndSavedStateRestoration() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val vm = ReceiptViewModel(application)
        vm.saveLogbookDraft("0", JSONObject().put("bookingKey", "stable-request").put("start", "Dornstetten")
            .put("destination", "Mietobjekt").put("date", "2026-09-12").put("purpose", "Besichtigung")
            .put("manualKmText", "12,5").put("propertyId", "property-1").toString())
        val show = mutableStateOf(true)
        val tester = StateRestorationTester(ui)
        tester.setContent {
            if (show.value) MyApplicationTheme(darkTheme = false) {
                LogbookEntryScreen(Receipt(aussteller = "Manuelle Fahrt", datum = "2026-10-01", uhrzeit = "",
                    bruttobetrag = 0.0, hauptkategorie = "", unterkategorie = "", kontoNr = "", beschreibung = ""),
                    PropertyMetadata(wohnort = "Anderer Start", adresse = "Anderes Ziel"), vm, {}, {})
            }
        }
        ui.onNodeWithText("Besichtigung").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("12,5 km").performScrollTo().assertIsDisplayed()
        tester.emulateSavedInstanceStateRestore()
        ui.onNodeWithText("Besichtigung").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("12,5 km").performScrollTo().assertIsDisplayed()
        ui.runOnIdle { show.value = false }
        ui.runOnIdle { show.value = true }
        ui.onNodeWithText("Besichtigung").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("12,5 km").performScrollTo().assertIsDisplayed()
        val reloaded = ReceiptViewModel(application)
        assertEquals("stable-request", JSONObject(reloaded.logbookDrafts.value.getValue("0")).getString("bookingKey"))
        vm.clearLogbookDraft("0")
        assertFalse(vm.logbookDrafts.value.containsKey("0"))
    }
}
