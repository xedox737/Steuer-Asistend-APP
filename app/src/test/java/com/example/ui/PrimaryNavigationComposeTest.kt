package com.example.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.example.MainActivity
import com.example.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class PrimaryNavigationComposeTest {
    @get:Rule(order = 0) val applicationIsolation = IsolatedAndroidApplicationRule()
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(ui.activity)[ReceiptViewModel::class.java]
    private val fixtureContext get() = vm.getApplication<Application>()
    private lateinit var db: AppDatabase
    private val propertyId = "primary-property"
    private lateinit var storedUnits: List<WohneinheitStatus>
    private lateinit var storedReceipt: Receipt
    private lateinit var storedDocument: ManagedDocument
    private lateinit var storedTransaction: BankTransaction
    private lateinit var preferenceSnapshot: Map<String, Map<String, *>>
    private val prefs = listOf("wohneinheiten_prefs", "rent_plan_prefs", "tenant_history_prefs", "google_drive_prefs")

    private fun clearFixture() {
        ui.runOnIdle { vm.navigateToPrimaryDestination(AppScreen.DASHBOARD) }
        runBlocking(Dispatchers.IO) { db.clearAllTables() }
        ui.waitUntil(10000) { vm.properties.value.isEmpty() && vm.receipts.value.isEmpty() }
        ui.waitForIdle()
        prefs.forEach { fixtureContext.getSharedPreferences(it, 0).edit().clear().commit() }
    }
    @After fun cleanUpFixture() = clearFixture()

    @Before fun seed() {
        val application = fixtureContext
        db = AppDatabase.getDatabase(application, CoroutineScope(Dispatchers.IO))
        clearFixture()
        runBlocking(Dispatchers.IO) {
            db.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 84, propertyId = propertyId,
                name = "Primärnavigation Haus", wohneinheiten = "WE 1"))
            storedReceipt = Receipt(id = 980, aussteller = "Primär-Beleg", datum = "2026-10-01", uhrzeit = "",
                bruttobetrag = 750.0, hauptkategorie = "Miete, Nebenkosten & Kaution", unterkategorie = "Kaltmiete",
                kontoNr = "8100", beschreibung = "Navigation", internalId = "primary-receipt", propertyId = propertyId)
            db.receiptDao().insertReceipt(storedReceipt)
            db.bankDao().upsertAccount(BankAccount("primary-account", "Testkonto"))
            storedTransaction = BankTransaction("primary-transaction", "primary-account", "2026-10-01",
                amount = 750.0, counterparty = "Primär-Buchung", purpose = "Miete")
            db.bankDao().upsertTransaction(storedTransaction)
            val file = File(application.filesDir, "primary-document.txt").apply { writeText("Erhaltene Unterlage") }
            storedDocument = ManagedDocument(documentId = "primary-document", propertyId = propertyId,
                title = "Primär-Dokument", originalFilename = file.name, storedFilename = file.name,
                mimeType = "text/plain", localUri = file.absolutePath, ocrText = "Erhaltene Unterlage")
            db.managedDocumentDao().upsert(storedDocument)
        }
        ui.waitUntil(10000) { vm.properties.value.any { it.propertyId == propertyId } && vm.receipts.value.any { it.id == 980 } }
        ui.runOnIdle { vm.selectProperty(propertyId) }
        ui.waitUntil(10000) { vm.propertyMetadata.value?.propertyId == propertyId && vm.wohneinheitenStatus.value.isNotEmpty() }
        ui.runOnIdle {
            val unit = vm.wohneinheitenStatus.value.single().copy(status = "Vermietet", mieter = "Primär-Mieter",
                kaltmiete = 600.0, mietvertragsstart = "2026-01-01")
            vm.updateWohneinheit(unit)
            PropertyUnitScopedData.setRentValues(fixtureContext, propertyId, unit, 150.0, 0.0)
            TenantHistoryStore.ensureCurrentPeriod(fixtureContext, unit, 150.0, 0.0, propertyId)
        }
        ui.waitForIdle()
        storedUnits = vm.wohneinheitenStatus.value.toList()
        preferenceSnapshot = prefs.associateWith { fixtureContext.getSharedPreferences(it, 0).all.toMap() }
    }

    private fun clickTab(destination: AppScreen) {
        ui.onNodeWithTag("nav_item_${destination.name.lowercase()}").performClick()
        ui.waitForIdle()
    }
    private fun back() { ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }; ui.waitForIdle() }
    private fun assertRoot(destination: AppScreen) {
        assertEquals(destination, vm.currentScreen.value)
        when (destination) {
            AppScreen.DASHBOARD -> ui.onNodeWithText("Hallo Sergej!").assertIsDisplayed()
            AppScreen.RECEIPTS_LIST -> {
                ui.onNode(hasTestTag("receipts_list") or hasTestTag("receipts_grid")).assertIsDisplayed()
                ui.onNodeWithTag("receipt_item_980").assertTextContains("Primär-Beleg")
            }
            AppScreen.ADD_RECEIPT -> ui.onNodeWithText("Beleg erfassen (KI & Manuell)").assertExists()
            AppScreen.PROPERTIES -> ui.onNodeWithTag("properties_overview").assertIsDisplayed()
            AppScreen.MORE -> ui.onNodeWithTag("more_group_Finanzen").assertIsDisplayed()
            else -> error("Kein Hauptziel")
        }
        ui.onNodeWithText("ImmoPilot").assertIsDisplayed()
        ui.onNodeWithTag("bottom_navigation").assertIsDisplayed()
        PRIMARY_NAVIGATION_SCREENS.forEach {
            val tab = ui.onNodeWithTag("nav_item_${it.name.lowercase()}")
            if (it == destination) tab.assertIsSelected() else tab.assertIsNotSelected()
        }
        ui.onNodeWithText("Buchungsdetails").assertDoesNotExist()
        ui.onNodeWithText("Dokumentendetail").assertDoesNotExist()
        assertNull(vm.selectedReceiptDetailId.value)
    }
    private fun assertDataPreserved() {
        assertEquals(propertyId, vm.propertyMetadata.value?.propertyId)
        assertEquals(storedUnits, vm.wohneinheitenStatus.value)
        assertEquals(storedReceipt, vm.receipts.value.single { it.id == storedReceipt.id })
        assertEquals(storedTransaction, vm.bankTransactions.value.single { it.transactionId == storedTransaction.transactionId })
        runBlocking { assertEquals(storedDocument, db.managedDocumentDao().getById(storedDocument.documentId)) }
        prefs.forEach { assertEquals(it, preferenceSnapshot[it], fixtureContext.getSharedPreferences(it, 0).all) }
    }
    private fun matrix(openSource: () -> Unit) {
        PRIMARY_NAVIGATION_SCREENS.forEach { destination ->
            openSource()
            clickTab(destination)
            assertRoot(destination)
            // The primary switch must never return into the departed child.
            if (destination != AppScreen.DASHBOARD) { back(); assertRoot(AppScreen.DASHBOARD) }
        }
        assertDataPreserved()
    }
    private fun openPropertyUnit() {
        clickTab(AppScreen.PROPERTIES)
        ui.onNodeWithTag("property_card_$propertyId").performScrollTo().performClick()
        ui.onNode(hasText("Einheiten") and hasClickAction()).performScrollTo().performClick()
        ui.onNodeWithText("WE 1", substring = false).performScrollTo().performClick()
        ui.onNodeWithText("Wohneinheit im Überblick").assertIsDisplayed()
    }
    private fun clickMore(label: String) {
        ui.onNodeWithTag("more_menu").performScrollToNode(hasTestTag("more_item_$label"))
        ui.onNodeWithTag("more_item_$label").performClick()
    }
    private fun openMorePage(label: String, content: String) {
        clickTab(AppScreen.MORE)
        clickMore(label)
        ui.onNodeWithText(content).assertExists()
    }
    @Test fun propertyUnitToEveryPrimaryRootIncludingActiveProperties() = matrix { openPropertyUnit() }
    @Test fun afaToEveryPrimaryRootIncludingActiveMore() = matrix { openMorePage("AfA Gebäude", "Gebäude abschreiben") }
    @Test fun monitorToEveryPrimaryRootIncludingActiveMore() = matrix { openMorePage("Sanierungs-Monitor", "Sanierungs-Monitor") }
    @Test fun rulesToEveryPrimaryRootIncludingActiveMore() = matrix { openMorePage("Gelernte Regeln", "Gelerntes KI-Wissen") }
    @Test fun backupToEveryPrimaryRootIncludingActiveMore() = matrix { openMorePage("Backup & Cloud", "Google Drive Backup") }
    @Test fun receiptDetailToEveryPrimaryRootIncludingActiveReceipts() = matrix {
        clickTab(AppScreen.RECEIPTS_LIST)
        ui.runOnIdle { vm.openReceiptDetail(980) }
        ui.onNodeWithText("Bearbeiten").assertExists()
    }
    @Test fun receiptInlineEditorToEveryPrimaryRoot() = matrix {
        clickTab(AppScreen.RECEIPTS_LIST)
        ui.runOnIdle { vm.openReceiptDetail(980) }
        ui.onNodeWithText("Bearbeiten").performScrollTo().performClick()
        ui.onNodeWithTag("receipt_inline_editor").assertExists()
    }
    @Test fun bankDetailToEveryPrimaryRoot() = matrix {
        clickTab(AppScreen.MORE)
        clickMore("Bank & Kontoauszüge")
        ui.waitUntil(10000) { vm.bankTransactions.value.any { it.transactionId == "primary-transaction" } }
        ui.onNodeWithText("Primär-Buchung").performScrollTo().performClick()
        ui.onNodeWithText("Buchungsdetails").assertExists()
    }
    @Test fun documentDetailToEveryPrimaryRoot() = matrix {
        clickTab(AppScreen.MORE)
        clickMore("Dokumentenakte")
        ui.waitUntil(10000) { vm.managedDocuments.value.any { it.documentId == "primary-document" } }
        ui.onNodeWithText("Primär-Dokument").performScrollTo().performClick()
        ui.onNodeWithText("Dokumentendetail").assertExists()
    }
    @Test fun rentOverviewToEveryPrimaryRoot() = matrix {
        clickTab(AppScreen.MORE)
        clickMore("Mieteingänge")
        ui.onNodeWithTag("rent_overview").assertIsDisplayed()
        ui.onNodeWithTag("nav_item_more").assertIsSelected()
    }
    @Test fun ledgerToEveryPrimaryRoot() = matrix {
        clickTab(AppScreen.MORE)
        clickMore("Einnahmen & Ausgaben")
        ui.onNodeWithTag("ledger_overview").assertIsDisplayed()
        ui.onNodeWithTag("nav_item_more").assertIsSelected()
    }
    @Test fun datevToEveryPrimaryRoot() = matrix {
        openMorePage("DATEV Export", "DATEV Export")
    }
    @Test fun logbookToEveryPrimaryRoot() = matrix {
        openMorePage("Fahrtenbuch", "Fahrtenbuch")
    }

    @Test fun scanReselectLeavesBankPrefillAndReturnsToFreshScanRoot() {
        ui.runOnIdle { vm.startReceiptFromBankTransaction(storedTransaction) }
        ui.onNode(hasSetTextAction() and hasText("Primär-Buchung")).assertExists()
        clickTab(AppScreen.ADD_RECEIPT)
        assertRoot(AppScreen.ADD_RECEIPT)
        ui.onNode(hasSetTextAction() and hasText("Primär-Buchung")).assertDoesNotExist()
        assertNull(vm.pendingBankTransactionId.value)
        assertEquals(ScanUiState.Idle, vm.scanState.value)
        assertDataPreserved()
    }
    @Test fun repeatedPrimarySwitchesDoNotAccumulateHistory() {
        repeat(3) {
            openPropertyUnit()
            clickTab(AppScreen.PROPERTIES); assertRoot(AppScreen.PROPERTIES)
            clickTab(AppScreen.MORE); assertRoot(AppScreen.MORE)
            clickTab(AppScreen.MORE); assertRoot(AppScreen.MORE)
            clickTab(AppScreen.DASHBOARD); assertRoot(AppScreen.DASHBOARD)
        }
        clickTab(AppScreen.MORE)
        back(); assertRoot(AppScreen.DASHBOARD)
        assertDataPreserved()
    }
    @Test fun primaryResetLeavesSettingsPageAndDoesNotReopenIt() {
        clickTab(AppScreen.MORE)
        clickMore("App-Einstellungen")
        ui.onNodeWithTag("settings_overview").assertIsDisplayed()
        clickTab(AppScreen.MORE); assertRoot(AppScreen.MORE)
        ui.onNodeWithTag("settings_overview").assertDoesNotExist()
        clickTab(AppScreen.PROPERTIES); clickTab(AppScreen.MORE); assertRoot(AppScreen.MORE)
        assertDataPreserved()
    }
    @Test fun localUnitBackStillReturnsToUnitsThenProperty() {
        openPropertyUnit()
        back()
        ui.onNodeWithText("Mietverhältnisse, Zahlungen und Nebenkosten im Überblick").assertExists()
        ui.onNodeWithContentDescription("Zurück").performClick()
        ui.onNodeWithText("Notizen & Aufgaben").assertExists()
        back(); assertRoot(AppScreen.PROPERTIES)
    }
    @Test fun committedSaveFinishesWithoutOverridingANewerPrimaryDestination() {
        ui.runOnUiThread {
            vm.startReceiptFromBankTransaction(storedTransaction)
            vm.saveReceipt("Speichern läuft", "2026-10-01", "", 750.0,
                "Miete, Nebenkosten & Kaution", "Kaltmiete", "8100", "Speichertest", false)
            vm.navigateToPrimaryDestination(AppScreen.PROPERTIES)
        }
        ui.waitUntil(10000) { vm.receipts.value.any { it.aussteller == "Speichern läuft" } }
        ui.waitUntil(10000) { vm.bankReceiptLinks.value.any { it.transactionId == storedTransaction.transactionId } }
        assertRoot(AppScreen.PROPERTIES)
        assertEquals(propertyId, vm.propertyMetadata.value?.propertyId)
        assertEquals(storedUnits, vm.wohneinheitenStatus.value)
    }

}
