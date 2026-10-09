package com.example.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.example.MainActivity
import com.example.data.*
import com.github.takahirom.roborazzi.captureRoboImage
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
class Phase5BComposeWorkflowTest {
    @get:Rule(order = 0) val applicationIsolation = IsolatedAndroidApplicationRule()
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(ui.activity)[ReceiptViewModel::class.java]
    private lateinit var db: AppDatabase
    private val property = PropertyMetadata(id = 74, propertyId = "phase5b-a", name = "Phase5B Haus A",
        adresse = "Teststraße 1", baujahr = 1968, wohneinheiten = "WE 01, WE 02")

    @Before fun clearData() {
        assertSame(ui.activity.application, vm.getApplication<Application>())
        // FileProvider's process cache otherwise retains another Robolectric test's cache root.
        val providerCache = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            .apply { isAccessible = true }.get(null) as MutableMap<*, *>
        providerCache.clear()
        db = AppDatabase.getDatabase(ui.activity.application as Application, CoroutineScope(Dispatchers.IO))
        runBlocking(Dispatchers.IO) { db.clearAllTables() }
        PersistentPreferenceInventory.stores.forEach { ui.activity.getSharedPreferences(it.name, 0).edit().clear().commit() }
        ui.waitUntil(10000) { vm.receipts.value.isEmpty() && vm.properties.value.isEmpty() }
        ui.waitForIdle()
    }

    private fun seed(rented: Boolean = false) {
        val prefs = ui.activity.getSharedPreferences("wohneinheiten_prefs", 0)
        (1..2).forEach { n ->
            val unit = "u-$n"; val name = "WE ${n.toString().padStart(2, '0')}"
            prefs.edit().putString("property_phase5b-a_unit_${name}_id", unit)
                .putString("property_phase5b-a_unit_id_index_${n - 1}", unit)
                .putString("property_phase5b-a_unitid_${unit}_status", if (rented) "Vermietet" else "Leerstand")
                .putString("property_phase5b-a_unitid_${unit}_mieter", if (rented) "Mieter $n" else "")
                .putFloat("property_phase5b-a_unitid_${unit}_rent", if (rented) 760f else 0f)
                .putString("property_phase5b-a_unitid_${unit}_start", if (rented) "2021-04-01" else "").commit()
        }
        runBlocking(Dispatchers.IO) { db.propertyDao().insertPropertyMetadata(property) }
        ui.waitUntil(10000) { vm.properties.value.any { it.propertyId == property.propertyId } }
        ui.runOnIdle { vm.selectProperty(property.propertyId); vm.setScreen(AppScreen.PROPERTIES) }
        ui.waitUntil(10000) { vm.propertyMetadata.value?.propertyId == property.propertyId &&
            vm.wohneinheitenStatus.value.map { it.unitId } == listOf("u-1", "u-2") }
        ui.onNodeWithText(property.name).performClick()
    }

    private fun back() {
        ui.runOnIdle {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            if (dialog is androidx.activity.ComponentDialog && dialog.isShowing) {
                dialog.onBackPressedDispatcher.onBackPressed()
            } else ui.activity.onBackPressedDispatcher.onBackPressed()
        }
        ui.waitForIdle()
    }
    private fun settleEditorWindow() {
        // Same native Robolectric window bound used by the existing rent-plan UI tests.
        ui.mainClock.advanceTimeByFrame()
        ui.runOnUiThread {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            assertTrue(dialog.isShowing)
            val density = ui.activity.resources.displayMetrics.density
            dialog.window!!.setLayout((360 * density).toInt(), (700 * density).toInt())
        }
        ui.waitForIdle()
    }
    private fun capture(name: String) {
        ui.runOnIdle {
            fun redraw(view: android.view.View) {
                if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") {
                    view.javaClass.getMethod("invalidateDescendants").invoke(view)
                }
                view.requestLayout(); view.invalidate()
                if (view is android.view.ViewGroup) for (index in 0 until view.childCount) redraw(view.getChildAt(index))
            }
            redraw(ui.activity.window.decorView)
        }
        ui.mainClock.advanceTimeBy(300); ui.waitForIdle()
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
            .captureRoboImage("build/reports/phase5b-workflows/$name.png")
    }
    private fun ledgerScroll(tag: String) = ui.onNodeWithTag("ledger_overview").performScrollToNode(hasTestTag(tag))
    private fun unitsScroll(tag: String) = ui.onNodeWithTag("property_units_overview").performScrollToNode(hasTestTag(tag))
    private fun expense(id: Int, amount: Double, propertyId: String) = Receipt(id, "Handwerk $id", "2026-10-03", "", amount,
        "Werbungskosten", "Reparatur", "4800", "Reparatur", propertyId = propertyId, internalId = "r-$id")

    @Test fun objectFilterUpdatesMetricsAndBankDetailReturnsToFilteredLedger() {
        seed()
        runBlocking(Dispatchers.IO) {
            db.propertyDao().insertPropertyMetadata(property.copy(id = 75, propertyId = "phase5b-b", name = "Phase5B Haus B"))
            db.receiptDao().insertReceipt(expense(901, 1000.0, "phase5b-a"))
            db.receiptDao().insertReceipt(expense(902, 2000.0, "phase5b-b"))
            db.bankDao().upsertTransaction(BankTransaction("tx", "account", "2026-10-03", amount = 980.0, counterparty = "Mieter 1",
                purpose = "Miete Oktober", reconciliationStatus = BankReconciliationStatus.MATCHED))
            db.bankRentAssignmentDao().upsert(BankRentAssignment("a", "tx", "phase5b-a", "u-1", "2026-10", "tenant", 980.0,
                BankSplitPaymentType.RENT, createdAt = "2026-10-03", updatedAt = "2026-10-03"))
        }
        ui.waitUntil(10000) { vm.receipts.value.size == 2 && vm.bankRentAssignments.value.size == 1 }
        ui.runOnIdle { vm.setScreen(AppScreen.LEDGER) }; ui.waitForIdle()
        ui.onNode(hasText(LedgerPresentation.money(3000.0)) and hasAnyAncestor(hasTestTag("ledger_metric_Ausgaben"))).assertExists()
        ledgerScroll("ledger_property"); ui.onNodeWithTag("ledger_property").performClick()
        ui.onNodeWithText("Phase5B Haus A").performClick()
        ledgerScroll("ledger_metric_Ausgaben")
        ui.onNode(hasText(LedgerPresentation.money(1000.0)) and hasAnyAncestor(hasTestTag("ledger_metric_Ausgaben"))).assertExists()
        capture("filtered-finances-393")
        ledgerScroll("ledger_bank_bank-a"); ui.onNodeWithTag("ledger_bank_bank-a").performClick()
        ui.waitUntil(10000) { vm.currentScreen.value == AppScreen.BANK }
        ui.onNodeWithText("Buchungsdetails").assertExists()
        back()
        assertEquals(AppScreen.LEDGER, vm.currentScreen.value)
        ledgerScroll("ledger_property"); ui.onNodeWithTag("ledger_property").assertTextContains("Phase5B Haus A")
        ui.onNodeWithTag("bottom_navigation").assertExists()
    }

    @Test fun unresolvedBankRentOpensSharedReviewAndReturnsFromBankWithAppBack() {
        seed()
        runBlocking(Dispatchers.IO) { db.bankDao().upsertTransaction(BankTransaction("open", "account", "2026-10-03",
            amount = 1020.0, purpose = "Miete Oktober", counterparty = "Unklarer Mieter")) }
        ui.waitUntil(10000) { vm.bankTransactions.value.size == 1 }
        ui.runOnIdle { vm.setScreen(AppScreen.RENT_OVERVIEW) }
        ui.onNodeWithTag("rent_overview").performScrollToNode(hasTestTag("rent_review_payments"))
        ui.onNodeWithTag("rent_review_payments").performClick()
        ui.onNodeWithTag("rent_review_bank-open").assertExists().performClick()
        ui.waitUntil(10000) { vm.currentScreen.value == AppScreen.BANK }
        ui.onNodeWithText("Buchungsdetails").assertExists()
        ui.onNodeWithContentDescription("Zurück").performClick()
        assertEquals(AppScreen.RENT_OVERVIEW, vm.currentScreen.value)
        assertTrue(vm.bankRentAssignments.value.isEmpty())
        ui.onNodeWithTag("rent_overview").performScrollToNode(hasTestTag("rent_monthly_check"))
        ui.onNodeWithTag("rent_monthly_check").performClick()
        ui.onNodeWithTag("rent_monthly_list").performScrollToNode(hasTestTag("monthly_rent_review_payments"))
        ui.onNodeWithTag("monthly_rent_review_payments").performClick()
        ui.onNodeWithTag("rent_review_bank-open").performClick()
        ui.waitUntil(10000) { vm.currentScreen.value == AppScreen.BANK }
        back()
        ui.onNodeWithText("Miet-Monatscheck").assertExists()
        back()
        ui.onNodeWithTag("rent_overview").assertExists()
    }

    @Test fun objectEditorContainsAndPersistsPurchaseTypeAndNotesWithSameIds() {
        seed()
        ui.onNodeWithText("Stammdaten").performClick()
        // The preceding lazy item is a single card taller than the viewport. Target its
        // existing footer directly instead of repeatedly scanning that oversized item.
        ui.onNodeWithTag("property_data").performScrollToIndex(2)
        ui.onNodeWithText("Stammdaten bearbeiten").performClick()
        settleEditorWindow()
        ui.onNodeWithTag("edit_property_notarielles_kaufdatum").performScrollTo().performTextReplacement("2021-03-15")
        ui.onNodeWithTag("edit_property_kaufpreis").performScrollTo().performTextReplacement("1.050.000,25")
        ui.onNodeWithTag("edit_property_type").performScrollTo().performTextReplacement("Mehrfamilienhaus")
        ui.onNodeWithTag("edit_property_notes").performScrollTo().performTextReplacement("Notiz am Objekt")
        ui.onNodeWithTag("save_property_metadata_button").performClick()
        ui.waitUntil(10000) { vm.properties.value.any { it.notizen == "Notiz am Objekt" } }
        val saved = vm.properties.value.single { it.propertyId == property.propertyId }
        assertEquals(property.id, saved.id)
        assertEquals(1050000.25, saved.gesamtKaufpreis, .001)
        assertEquals("2021-03-15", saved.notariellesKaufdatum)
        assertEquals(listOf("u-1", "u-2"), vm.getWohneinheitenForProperty(saved).map { it.unitId })
        back()
        assertEquals(AppScreen.PROPERTIES, vm.currentScreen.value)
    }

    @Test fun quickActionsOpenExactDialogsAndChronologyErrorStaysAtOldEnd() {
        seed(rented = true)
        ui.onNode(hasText("Einheiten") and hasClickAction()).performClick()
        unitsScroll("unit_monthly_u-1"); ui.onNodeWithTag("unit_monthly_u-1").performClick()
        ui.onNodeWithText("Monatscheck · WE 01").assertExists()
        back()
        unitsScroll("unit_tenant_change_u-1"); ui.onNodeWithTag("unit_tenant_change_u-1").performClick()
        settleEditorWindow()
        ui.onNodeWithText("Mieterwechsel erfassen").assertExists()
        ui.onNodeWithTag("tenant_change_old_end").performTextReplacement("2020-03-31")
        ui.onNodeWithTag("tenant_change_name").performTextReplacement("Neuer Mieter")
        ui.onNodeWithTag("tenant_change_new_start").performScrollTo().performTextReplacement("2020-04-01")
        ui.onNodeWithText("Wechsel speichern").performClick()
        ui.onNodeWithText("Das Vertragsende darf nicht vor dem Mietbeginn liegen.").assertExists()
        ui.onNodeWithTag("tenant_change_old_end").performScrollTo()
        capture("tenant-chronology-error-393")
        val history = TenantHistoryStore.load(ui.activity, property.propertyId, "u-1", "WE 01")
        assertEquals(1, history.size)
        assertEquals("", history.single().endDate)
        back()
        ui.onNodeWithTag("property_units_overview").assertExists()
        unitsScroll("unit_annual_details"); ui.onNodeWithTag("unit_annual_details").performClick()
        ui.onNodeWithTag("rent_overview").assertExists()
        back()
        assertEquals(AppScreen.PROPERTIES, vm.currentScreen.value)
        ui.onNodeWithTag("property_units_overview").assertExists()
    }

    @Test fun bulkPreviewNamesEveryChangedUnitBeforeSaving() {
        seed()
        ui.onNode(hasText("Einheiten") and hasClickAction()).performClick()
        ui.onNodeWithTag("rent_batch_open").performClick()
        settleEditorWindow()
        ui.onNodeWithTag("rent_batch_tenant_u-1").performScrollTo().performTextReplacement("Müller")
        ui.onNodeWithTag("rent_batch_start_u-1").performScrollTo().performTextReplacement("2024-01-01")
        ui.onNodeWithTag("rent_batch_cold_u-1").performScrollTo().performTextReplacement("760,50")
        ui.onNodeWithTag("rent_batch_utilities_u-1").performScrollTo().performTextReplacement("220,25")
        ui.onNodeWithTag("rent_batch_status_u-1").performScrollTo().performClick()
        ui.onNodeWithText("Vermietet").performClick()
        ui.onNodeWithTag("rent_batch_select_u-2").performClick()
        capture("initial-rent-editor-393")
        ui.onNodeWithTag("rent_batch_confirm").performClick()
        ui.onNodeWithText("2 Einheiten speichern?").assertExists()
        ui.onNode(hasText("WE 01") and hasAnyAncestor(hasTestTag("rent_batch_rows"))).assertExists()
        ui.onNode(hasText("WE 02") and hasAnyAncestor(hasTestTag("rent_batch_rows"))).assertExists()
        capture("initial-rent-preview-393")
        ui.onNodeWithTag("rent_batch_confirm").performClick()
        ui.waitUntil(10000) { vm.wohneinheitenStatus.value.any { it.mieter == "Müller" } }
        assertEquals("Leerstand", vm.wohneinheitenStatus.value.single { it.unitId == "u-2" }.status)
    }

    @Test fun receiptSearchUsesUserLanguageAndDashboardShowsLiveReviewCount() {
        runBlocking(Dispatchers.IO) { db.receiptDao().insertReceipt(expense(901, 100.0, "").copy(pruefstatus = "UNGEPRUEFT")) }
        ui.waitUntil(10000) { vm.receipts.value.size == 1 }
        ui.runOnIdle { vm.setScreen(AppScreen.DASHBOARD) }
        ui.onNodeWithText("Belege prüfen").assertExists()
        ui.onNodeWithText("Offene Belege").assertDoesNotExist()
        ui.runOnIdle { vm.setScreen(AppScreen.RECEIPTS_LIST) }
        ui.onNodeWithText("Belege mit KI durchsuchen").assertExists()
        ui.onAllNodes(hasText("Room", substring = true)).assertCountEquals(0)
        ui.onAllNodes(hasText("Belegsdatenbank", substring = true)).assertCountEquals(0)
        @Suppress("UNCHECKED_CAST")
        val state = ReceiptViewModel::class.java.getDeclaredField("_aiSearchState").apply { isAccessible = true }
            .get(vm) as kotlinx.coroutines.flow.MutableStateFlow<ReceiptViewModel.AiSearchUiState>
        ui.runOnIdle { state.value = ReceiptViewModel.AiSearchUiState(isLoading = true) }
        ui.onNodeWithText("Die KI durchsucht deine Belege …").assertExists()
        ui.onAllNodes(hasText("Room", substring = true)).assertCountEquals(0)
        ui.runOnIdle { state.value = ReceiptViewModel.AiSearchUiState(result = com.example.api.AiSearchResult(
            answer = "Ein passender Beleg", matchingReceiptIds = listOf(901L))) }
        ui.onNodeWithText("🎯 1 passende Belege werden unten angezeigt.").assertExists()
        ui.onAllNodes(hasText("Room", substring = true)).assertCountEquals(0)
        ui.runOnIdle { vm.clearAiSearch(); vm.deleteGeminiKey(); vm.performAiSearch("Reparatur") }
        ui.waitUntil(10000) { vm.aiSearchState.value.result?.matchingReceiptIds == listOf(901L) }
        ui.onNodeWithText("Auswertung für 'Reparatur': Insgesamt 100,00 € verteilt auf 1 Belege.").assertExists()
        ui.onAllNodes(hasText("Room", substring = true)).assertCountEquals(0)
        ui.runOnIdle { vm.performAiSearch("Unpassender Suchbegriff") }
        ui.waitUntil(10000) { vm.aiSearchState.value.query == "Unpassender Suchbegriff" && !vm.aiSearchState.value.isLoading }
        ui.onNodeWithText("Keine passenden Belege für 'Unpassender Suchbegriff' gefunden.").assertExists()
        runBlocking(Dispatchers.IO) { db.clearAllTables() }
        ui.waitUntil(10000) { vm.receipts.value.isEmpty() }
        ui.runOnIdle { vm.performAiSearch("Reparatur") }
        ui.waitUntil(10000) { vm.aiSearchState.value.error != null }
        ui.onNodeWithText("Noch keine Belege vorhanden.").assertExists()
        ui.onAllNodes(hasText("Room", substring = true)).assertCountEquals(0)
    }

    @Test fun unitCountKeepsInvalidRawTextAndYear9999CannotAdvanceWizard() {
        ui.runOnIdle { vm.setScreen(AppScreen.PROPERTIES) }
        ui.onNodeWithTag("add_property_button").performClick()
        settleEditorWindow()
        ui.onNode(hasText("Objektname") and hasSetTextAction()).performTextReplacement("Validiertes Haus")
        ui.onNode(hasText("Straße und Hausnummer") and hasSetTextAction()).performTextReplacement("Teststraße 1")
        ui.onNodeWithTag("property_wizard_next").performClick()
        listOf("-1", "2,5").forEach { input ->
            ui.onNode(hasText("Anzahl Einheiten") and hasSetTextAction()).performTextReplacement(input)
            ui.onNodeWithTag("property_wizard_next").performClick()
            ui.onNodeWithText("Immobilie anlegen · 2/5").assertExists()
            ui.onNode(hasText("Anzahl Einheiten") and hasSetTextAction()).assertTextContains(input)
            ui.onNodeWithText("Bitte eine positive Ganzzahl für die Einheitenzahl eingeben; Dezimalzahlen und Minuszeichen sind nicht erlaubt.").assertExists()
        }
        ui.onNode(hasText("Anzahl Einheiten") and hasSetTextAction()).performTextReplacement("2")
        ui.onNode(hasText("Baujahr") and hasSetTextAction()).performTextReplacement("9999")
        ui.onNodeWithTag("property_wizard_next").performClick()
        ui.onNodeWithText("Immobilie anlegen · 2/5").assertExists()
        ui.onNode(hasText("Baujahr") and hasSetTextAction()).assertTextContains("9999")
        ui.onNodeWithText("Bitte ein gültiges Baujahr eingeben.").assertExists()
        assertTrue(vm.properties.value.isEmpty())
        back(); ui.onNodeWithTag("properties_overview").assertExists()
    }

    @Test fun csvChoiceInWizardExportsAndSharesActualCsvAndAuditsBookingCount() {
        seed()
        runBlocking(Dispatchers.IO) {
            (901..902).forEach { id ->
                db.receiptDao().insertReceipt(expense(id, 100.0, property.propertyId).copy(
                    freigabestatus = "FREIGEGEBEN",
                    allocationsJson = AccountingApprovalJson.encodeAllocations(listOf(PersistedAllocation("row-$id", "Reparatur", 100.0, 10000))),
                    bookingProposalsJson = AccountingApprovalJson.encodeBookingProposals(listOf(PersistedBookingProposal("row-$id", "4800", "1200", 10000, "")))
                ))
            }
        }
        ui.waitUntil(10000) { vm.receipts.value.size == 2 }
        ui.runOnIdle {
            vm.updateActiveDatevProfile(DatevProfile.createDefaultSkr03().copy(beraterNummer = "1111111", mandantenNummer = "11111"))
            vm.setWizardFilters(year = "2026", targetFormat = "FULL_ZIP")
            vm.setWizardStep(5)
            vm.setScreen(AppScreen.DATEV_EXPORT)
        }
        ui.onNodeWithText("Reiner EXTF Buchungsstapel (.csv)").performScrollTo().performClick()
        ui.onNodeWithText("Buchungsstapel jetzt erzeugen").performScrollTo().assertIsEnabled().performClick()
        ui.waitUntil(10000) { vm.lastExportResult.value != null && vm.allAuditRuns.value.isNotEmpty() }
        val result = vm.lastExportResult.value!!
        assertEquals("csv", result.outputFile.extension)
        assertEquals("text/csv", result.mimeType)
        assertEquals(2, result.totalRecords)
        assertEquals(2, vm.allAuditRuns.value.first().bookingCount)
        assertTrue(vm.allAuditRuns.value.first().zipFileName.endsWith(".csv"))
        ui.onNodeWithText("Buchungsstapel erfolgreich erstellt!").assertExists()
        ui.onNodeWithTag("datev_share_output").performScrollTo().performClick()
        val chooser = org.robolectric.Shadows.shadowOf(ui.activity).nextStartedActivity
        val send = chooser.getParcelableExtra(android.content.Intent.EXTRA_INTENT, android.content.Intent::class.java)!!
        assertEquals(android.content.Intent.ACTION_SEND, send.action)
        assertEquals("text/csv", send.type)
        val uri = send.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)!!
        assertTrue(uri.lastPathSegment!!.endsWith(".csv"))
        val text = ui.activity.contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }.removePrefix("\uFEFF")
        assertTrue(com.example.util.DatevFormatValidator.validate(text).isValid)
        assertEquals(4, text.trimEnd().split("\r\n").size)
        assertEquals(2, vm.receipts.value.size)
        assertTrue(vm.receipts.value.all { it.freigabestatus == "FREIGEGEBEN" })
        back(); assertEquals(AppScreen.MORE, vm.currentScreen.value)
    }
}
