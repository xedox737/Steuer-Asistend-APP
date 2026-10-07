package com.example.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.api.ManagedDocumentAiField
import com.example.api.ManagedDocumentAiResult
import com.example.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class DocumentReviewWorkflowComposeTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private lateinit var vm: ReceiptViewModel
    private lateinit var database: AppDatabase
    private var parentBack = 0
    private val documentA = ManagedDocument("review-a", "review-property-b", title = "Dokument A", documentDate = "2026-10-01", ocrStatus = "ERFOLGREICH", ocrText = "Testvertrag")
    private val documentB = ManagedDocument("review-b", "review-property-a", title = "Dokument B", documentDate = "2026-10-01", ocrStatus = "ERFOLGREICH", ocrText = "Testvertrag")
    private val resultA = ManagedDocumentAiResult(documentDate = "2026-10-01", fields = listOf(ManagedDocumentAiField("objektadresse", "Objektadresse", "Neue Adresse B", .9)))

    @Before fun setUp() {
        val application = ui.activity.application as Application
        listOf("google_drive_prefs", "wohneinheiten_prefs", "tenant_history_prefs", "rent_plan_prefs").forEach { application.getSharedPreferences(it, 0).edit().clear().commit() }
        vm = ReceiptViewModel(application)
        database = AppDatabase.getDatabase(application, CoroutineScope(Dispatchers.IO))
        runBlocking(Dispatchers.IO) {
            database.clearAllTables()
            database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 1, propertyId = "review-property-a", name = "Objekt A", adresse = "Adresse A"))
            database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 2, propertyId = "review-property-b", name = "Objekt B", adresse = "Adresse B"))
            database.managedDocumentDao().upsert(documentA)
            database.managedDocumentDao().upsert(documentB)
        }
        ui.setContent { MaterialTheme { BackHandler { parentBack++ }; DocumentManagementScreen(vm) } }
        ui.waitUntil(10000) { vm.managedDocuments.value.size == 2 && vm.properties.value.size == 2 }
        ui.runOnIdle { vm.selectProperty("review-property-a") }
        ui.waitUntil(10000) { vm.propertyMetadata.value?.propertyId == "review-property-a" }
        ui.runOnIdle {
            vm.documentAiReviewStore.offer(documentA, resultA)
            vm.documentAiReviewStore.dismiss(documentA.documentId)
            vm.documentAiReviewStore.offer(documentB, ManagedDocumentAiResult())
            vm.documentAiReviewStore.dismiss(documentB.documentId)
        }
    }

    private fun openReviewA() {
        ui.onNodeWithText("Dokument A").performScrollTo().performClick()
        ui.onNodeWithTag("review_managed_document_ai").performScrollTo().assertIsDisplayed().performClick()
        // Bound the native Robolectric text-input dialog, just as existing rent editor tests do.
        ui.mainClock.advanceTimeByFrame()
        ui.runOnUiThread {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            val density = ui.activity.resources.displayMetrics.density
            dialog.window!!.setLayout((360 * density).toInt(), (700 * density).toInt())
        }
        ui.waitForIdle()
        ui.onNodeWithText("Erkannte Daten prüfen").assertIsDisplayed()
    }

    @Test fun confirmingInDetailUpdatesDocumentPropertyAndKeepsOtherPendingReviewAndBothLocalBackPaths() {
        openReviewA()
        ui.onNode(isToggleable()).performScrollTo().performClick()
        ui.onNodeWithTag("confirm_document_ai_review").performClick()
        ui.waitUntil(10000) { documentA.documentId !in vm.documentAiReviewState.value.pending && documentA.documentId !in vm.documentAiReviewState.value.confirming }
        ui.waitUntil(10000) { vm.managedDocuments.value.any { it.documentId == documentA.documentId && it.reviewStatus == "GEPRUEFT" } }
        runBlocking(Dispatchers.IO) {
            assertEquals("Neue Adresse B", database.propertyDao().getPropertyByPropertyId("review-property-b")!!.adresse)
            assertEquals("Adresse A", database.propertyDao().getPropertyByPropertyId("review-property-a")!!.adresse)
        }
        assertTrue(documentB.documentId in vm.documentAiReviewState.value.pending)
        ui.onNodeWithText("Dokumentendetail").assertIsDisplayed()
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithText("Dokumentenakte").assertIsDisplayed()
        ui.onNodeWithText("Dokument B").performScrollTo().performClick()
        ui.onNodeWithTag("review_managed_document_ai").performScrollTo().assertIsDisplayed()
        ui.onNodeWithContentDescription("Zurück").performClick()
        ui.onNodeWithText("Dokumentenakte").assertIsDisplayed()
        assertEquals(0, parentBack)
    }

    @Test fun invalidPropertyShowsGermanErrorInDialogAndDetailAndRetainsAnalysisForLater() {
        openReviewA()
        runBlocking(Dispatchers.IO) { database.propertyDao().deletePropertyByPropertyId("review-property-b") }
        ui.onNodeWithTag("confirm_document_ai_review").performClick()
        ui.waitUntil(10000) { vm.documentAiReviewState.value.messages[documentA.documentId]?.contains("Immobilie ist nicht mehr verfügbar") == true }
        ui.onNodeWithTag("document_review_message").assertTextContains("Immobilie ist nicht mehr verfügbar", substring = true)
        ui.onNodeWithText("Abbrechen").performClick()
        ui.onNodeWithText("Dokumentendetail").assertIsDisplayed()
        ui.onNodeWithTag("document_detail_operation_status").performScrollTo().assertTextContains("Immobilie ist nicht mehr verfügbar", substring = true)
        assertTrue(documentA.documentId in vm.documentAiReviewState.value.pending)
        ui.onNodeWithTag("review_managed_document_ai").performScrollTo().assertIsDisplayed()
        assertEquals(0, parentBack)
    }
}
