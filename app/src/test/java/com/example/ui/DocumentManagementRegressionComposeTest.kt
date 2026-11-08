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
class DocumentManagementRegressionComposeTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private lateinit var vm: ReceiptViewModel
    private lateinit var database: AppDatabase
    private lateinit var viewModelStore: ViewModelStore
    private var previousDatabase: AppDatabase? = null
    private val instanceField = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    private var parentBack = 0
    private val documentA = ManagedDocument("review-a", "review-property-b", "review-unit-b", title = "Dokument A", documentDate = "2026-10-01", ocrStatus = "ERFOLGREICH", ocrText = "")
    private val documentB = ManagedDocument("review-b", "review-property-a", title = "Dokument B", documentDate = "2026-10-01", ocrStatus = "ERFOLGREICH", ocrText = "")
    private val resultA = ManagedDocumentAiResult(confidence = .99, documentDate = "2026-10-01", fields = listOf(ManagedDocumentAiField("objektadresse", "Objektadresse", "Neue Adresse B", .35)))

    @Before fun setUp() {
        val application = ui.activity.application as Application
        listOf("google_drive_prefs", "wohneinheiten_prefs", "tenant_history_prefs", "rent_plan_prefs").forEach { application.getSharedPreferences(it, 0).edit().clear().commit() }
        application.getSharedPreferences("wohneinheiten_prefs", 0).edit()
            .putString("property_review-property-a_unit_WE 01_id", "review-unit-a")
            .putString("property_review-property-b_unit_WE 01_id", "review-unit-b")
            .putString("property_review-property-a_unitid_review-unit-a_label", "Wohnung A")
            .putString("property_review-property-b_unitid_review-unit-b_label", "Wohnung B")
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

    @Test fun globalCardUsesItsOwnPropertyEvenWhenAnotherPropertyIsSelected() {
        ui.onNodeWithText("Dokument für Objekt B.").performScrollTo().assertIsDisplayed()
        // Checking the underlying row distinguishes a presentation fix from moving the document.
        val saved = runBlocking(Dispatchers.IO) { database.managedDocumentDao().getById(documentA.documentId)!! }
        assertEquals("review-property-b", saved.propertyId)
        assertEquals("review-unit-b", saved.unitId)
        assertEquals("review-property-a", vm.propertyMetadata.value?.propertyId)
    }

    @Test fun noSearchMatchesDoNotClaimTheStoredDocumentCollectionIsEmptyAndCanBeReset() {
        ui.onNodeWithTag("document_search_query").performScrollTo().performTextInput("no-such-document-9527")
        ui.onNodeWithText("Keine Dokumente für diese Suche oder Filter.").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Noch keine Dokumente vorhanden.").assertDoesNotExist()
        assertEquals(2, vm.managedDocuments.value.size)
        ui.onNodeWithText("Suche/Filter zurücksetzen").performScrollTo().performClick()
        ui.onNodeWithText("Dokument A").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Dokument B").performScrollTo().assertIsDisplayed()
    }

    @Test fun importDialogMakesPropertyExplicitAndUsesOnlyItsUnitsWithoutChangingGlobalSelection() {
        ui.onNodeWithTag("document_import_button").performScrollTo().performClick()
        ui.onNodeWithTag("document_import_property").assertIsDisplayed().performClick()
        ui.onNodeWithText("Objekt B").performClick()
        ui.onNodeWithTag("document_import_property").assertTextContains("Objekt B", substring = true)
        ui.onNode(hasText("Gesamtobjekt") and hasAnyAncestor(isDialog())).performClick()
        ui.onNode(hasText("Wohnung B") and hasAnyAncestor(isPopup())).assertIsDisplayed()
        ui.onNode(hasText("Wohnung A") and hasAnyAncestor(isPopup())).assertDoesNotExist()
        ui.onNode(hasText("Wohnung B") and hasAnyAncestor(isPopup())).performClick()
        ui.onNode(hasText("Wohnung B") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        assertEquals("review-property-a", vm.propertyMetadata.value?.propertyId)
        // Do not start a real system picker in Robolectric; the import boundary is tested separately.
        ui.onNodeWithText("Abbrechen").performClick()
        assertEquals(2, vm.managedDocuments.value.size)
    }

    @Test fun lowConfidenceFieldGetsClearWarningWithoutSelectingItOrApplyingItsValue() {
        openReviewA()
        ui.onNodeWithText("Bitte besonders prüfen: unsicherer Vorschlag").performScrollTo().assertIsDisplayed()
        ui.onNode(isToggleable()).performScrollTo().assertIsOff()
        val property = runBlocking(Dispatchers.IO) { database.propertyDao().getPropertyByPropertyId("review-property-b")!! }
        assertEquals("Adresse B", property.adresse)
        ui.onNodeWithText("Abbrechen").performClick()
        assertTrue(documentA.documentId in vm.documentAiReviewState.value.pending)
    }

    @Test fun highConfidenceFieldKeepsNormalPresentationAndStillRequiresManualSelection() {
        ui.runOnIdle {
            vm.documentAiReviewStore.offer(documentA, resultA.copy(fields = resultA.fields.map { it.copy(confidence = .95) }))
            vm.documentAiReviewStore.dismiss(documentA.documentId)
        }
        openReviewA()
        ui.onNode(isToggleable()).performScrollTo().assertIsOff()
        ui.onNodeWithText("Bitte besonders prüfen: unsicherer Vorschlag").assertDoesNotExist()
        val property = runBlocking(Dispatchers.IO) { database.propertyDao().getPropertyByPropertyId("review-property-b")!! }
        assertEquals("Adresse B", property.adresse)
    }

}
