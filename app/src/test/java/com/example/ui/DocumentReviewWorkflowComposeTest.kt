package com.example.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.api.ManagedDocumentAiField
import com.example.api.ManagedDocumentAiResult
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
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
    private lateinit var viewModelStore: ViewModelStore
    private var previousDatabase: AppDatabase? = null
    private val instanceField = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    private var parentBack = 0
    private val documentA = ManagedDocument("review-a", "review-property-b", "review-unit-b", title = "Dokument A", documentDate = "2026-10-01", ocrStatus = "ERFOLGREICH", ocrText = "Testvertrag")
    private val documentB = ManagedDocument("review-b", "review-property-a", title = "Dokument B", documentDate = "2026-10-01", ocrStatus = "ERFOLGREICH", ocrText = "Testvertrag")
    private val resultA = ManagedDocumentAiResult(documentDate = "2026-10-01", fields = listOf(ManagedDocumentAiField("objektadresse", "Objektadresse", "Neue Adresse B", .9)))

    @Before fun setUp() {
        val application = ui.activity.application as Application
        listOf("google_drive_prefs", "wohneinheiten_prefs", "tenant_history_prefs", "rent_plan_prefs").forEach { application.getSharedPreferences(it, 0).edit().clear().commit() }
        application.getSharedPreferences("wohneinheiten_prefs", 0).edit()
            .putString("property_review-property-a_unit_WE 01_id", "review-unit-a")
            .putString("property_review-property-b_unit_WE 01_id", "review-unit-b")
            .commit()
        // Each case owns its database and cancels the ViewModel's collectors on teardown.
        previousDatabase = instanceField.get(null) as AppDatabase?
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).build()
        instanceField.set(null, database)
        runBlocking(Dispatchers.IO) {
            database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 1, propertyId = "review-property-a", name = "Objekt A", adresse = "Adresse A", wohneinheiten = "WE 01"))
            database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 2, propertyId = "review-property-b", name = "Objekt B", adresse = "Adresse B", wohneinheiten = "WE 01"))
            database.managedDocumentDao().upsert(documentA)
            database.managedDocumentDao().upsert(documentB)
        }
        ui.runOnUiThread {
            viewModelStore = ViewModelStore()
            vm = ViewModelProvider(viewModelStore, ViewModelProvider.AndroidViewModelFactory(application))[ReceiptViewModel::class.java]
        }
        assertSame(application, vm.getApplication<Application>())
        val viewModelDatabase = ReceiptViewModel::class.java.getDeclaredField("database").apply { isAccessible = true }
        assertSame(database, viewModelDatabase.get(vm))
        ui.setContent { MaterialTheme { BackHandler { parentBack++ }; DocumentManagementScreen(vm) } }
        waitForModel { vm.managedDocuments.value.size == 2 && vm.properties.value.size == 2 }
        ui.runOnIdle { vm.selectProperty("review-property-a") }
        waitForModel { vm.propertyMetadata.value?.propertyId == "review-property-a" }
        ui.runOnIdle {
            vm.documentAiReviewStore.offer(documentA, resultA)
            vm.documentAiReviewStore.dismiss(documentA.documentId)
            vm.documentAiReviewStore.offer(documentB, ManagedDocumentAiResult())
            vm.documentAiReviewStore.dismiss(documentB.documentId)
        }
    }

    @After fun tearDown() {
        if (::viewModelStore.isInitialized) viewModelStore.clear()
        instanceField.set(null, previousDatabase)
        if (::database.isInitialized) database.close()
    }

    private fun waitForModel(condition: () -> Boolean) {
        // Room completes on IO, then StateFlow delivers on Robolectric's paused main Looper.
        // Advance Android work as well as Compose frames while awaiting the real model state.
        try {
            ui.waitUntil(10000) {
                ui.waitForIdle()
                condition()
            }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError("Documents=${vm.managedDocuments.value.map { it.documentId to it.reviewStatus }}, " +
                "properties=${vm.properties.value.map { it.propertyId }}, " +
                "lifecycle=${ui.activity.lifecycle.currentState}", error)
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
        waitForModel { documentA.documentId !in vm.documentAiReviewState.value.pending && documentA.documentId !in vm.documentAiReviewState.value.confirming }
        val saved = runBlocking(Dispatchers.IO) { database.managedDocumentDao().getById(documentA.documentId)!! }
        assertEquals("GEPRUEFT", saved.reviewStatus)
        waitForModel { vm.managedDocuments.value.any { it.documentId == documentA.documentId && it.reviewStatus == "GEPRUEFT" } }
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
        waitForModel { vm.documentAiReviewState.value.messages[documentA.documentId]?.contains("Immobilie ist nicht mehr verfügbar") == true }
        ui.onNodeWithTag("document_review_message").assertTextContains("Immobilie ist nicht mehr verfügbar", substring = true)
        ui.onNodeWithText("Abbrechen").performClick()
        ui.onNodeWithText("Dokumentendetail").assertIsDisplayed()
        ui.onNodeWithTag("document_detail_operation_status").performScrollTo().assertTextContains("Immobilie ist nicht mehr verfügbar", substring = true)
        assertTrue(documentA.documentId in vm.documentAiReviewState.value.pending)
        ui.onNodeWithTag("review_managed_document_ai").performScrollTo().assertIsDisplayed()
        assertEquals(0, parentBack)
    }

    @Test fun confirmingLeaseUpdatesOnlyDocumentUnitWithSameUnitNameInTwoProperties() {
        val result = ManagedDocumentAiResult(documentType = "MIETVERTRAG", suggestedUnitId = "review-unit-b",
            fields = listOf(ManagedDocumentAiField("mieter", "Mieter", "Martin Weber", .9),
                ManagedDocumentAiField("kaltmiete", "Kaltmiete", "690", .9)))
        ui.runOnIdle {
            vm.documentAiReviewStore.offer(documentA, result)
            vm.documentAiReviewStore.dismiss(documentA.documentId)
            val review = vm.documentAiReviewState.value.pending.getValue(documentA.documentId)
            vm.confirmManagedDocumentReview(documentA.documentId, ManagedDocumentType.MIETVERTRAG,
                documentA.documentDate, documentA.unitId,
                result.reviewFields(emptyMap()).map { it.copy(decision = DocumentFieldDecision.UEBERNEHMEN) }, review.revision)
        }
        waitForModel { documentA.documentId !in vm.documentAiReviewState.value.pending && documentA.documentId !in vm.documentAiReviewState.value.confirming }
        val properties = runBlocking(Dispatchers.IO) { listOf(
            database.propertyDao().getPropertyByPropertyId("review-property-a")!!,
            database.propertyDao().getPropertyByPropertyId("review-property-b")!!
        ) }
        val unitA = vm.getWohneinheitenForProperty(properties[0]).single()
        val unitB = vm.getWohneinheitenForProperty(properties[1]).single()
        assertEquals("review-unit-a", unitA.unitId)
        assertEquals("", unitA.mieter)
        assertEquals(0.0, unitA.kaltmiete, .001)
        assertEquals("review-unit-b", unitB.unitId)
        assertEquals("Martin Weber", unitB.mieter)
        assertEquals(690.0, unitB.kaltmiete, .001)
        assertEquals("review-property-a", vm.propertyMetadata.value?.propertyId)
    }
}
