package com.example.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.data.ManagedDocument
import com.example.data.PropertyMetadata
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
class UnitDocumentBackNavigationTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    @Test fun unitSystemBackAndArrowReturnToUnitsBeforeProperty() {
        val vm = ReceiptViewModel(ui.activity.application as Application)
        val property = PropertyMetadata(propertyId = "navigation-property", name = "Testobjekt")
        val unit = WohneinheitStatus(name = "WE 1", label = "Testwohnung", status = "Leerstand",
            mieter = "", kaltmiete = 0.0, wohnflaeche = 50.0, unitId = "unit-navigation")
        var parentBack = 0
        ui.setContent {
            MaterialTheme {
                BackHandler { parentBack++ }
                UnifiedPropertyUnitsScreen(vm, property, listOf(unit), emptyList(), emptyList(), { parentBack++ })
            }
        }
        ui.onNodeWithText("Testwohnung").performScrollTo().performClick()
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.runOnIdle { assertEquals("System back skipped the unit list", 0, parentBack) }
        // Opening again verifies that system back really left the detail screen.
        ui.onNodeWithText("Testwohnung").performScrollTo().performClick()
        ui.onNodeWithContentDescription("Zurück").performClick()
        ui.runOnIdle { assertEquals(0, parentBack) }
        ui.onNodeWithContentDescription("Zurück").performClick()
        ui.runOnIdle { assertEquals(1, parentBack) }
    }

    @Test fun documentSystemBackAndArrowUseTheSameLocalDestination() {
        var parentBack = 0
        var detailBack = 0
        var reopen: (() -> Unit)? = null
        ui.setContent {
            MaterialTheme {
                var opened by remember { mutableStateOf(true) }
                reopen = { opened = true }
                BackHandler { parentBack++ }
                if (opened) ManagedDocumentDetailScreen(
                    ManagedDocument(documentId = "navigation-document", propertyId = "navigation-property", title = "Testdokument"),
                    null, emptyList(), onBack = { detailBack++; opened = false },
                    onAnalyze = {}, onDownload = {}, onSync = {}, onUpdatePresentation = { _, _ -> }
                )
            }
        }
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.runOnIdle { assertEquals(0, parentBack); assertEquals(1, detailBack); reopen!!() }
        ui.onNodeWithContentDescription("Zurück").performClick()
        ui.runOnIdle { assertEquals(0, parentBack); assertEquals(2, detailBack) }
    }
}
