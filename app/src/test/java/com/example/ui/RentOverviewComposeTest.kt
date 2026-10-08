package com.example.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
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
class RentOverviewComposeTest {
    @get:Rule(order = 0) val applicationIsolation = IsolatedAndroidApplicationRule()
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(ui.activity)[ReceiptViewModel::class.java]
    private val fixtureContext get() = vm.getApplication<Application>()
    private val propertyId = "rent-a"
    private fun key(name: String) = "$propertyId:u-$name"
    private fun overview() = ui.onNodeWithTag("rent_overview")
    private fun scroll(tag: String) { overview().performScrollToNode(hasTestTag(tag)); ui.waitForIdle() }
    private fun shell() { ui.onNodeWithText("ImmoPilot").assertIsDisplayed(); ui.onNodeWithTag("bottom_navigation").assertIsDisplayed() }
    private fun back() { ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }; ui.waitForIdle() }
    @Before fun clearFixtureData() {
        val database = AppDatabase.getDatabase(fixtureContext, CoroutineScope(Dispatchers.IO))
        runBlocking(Dispatchers.IO) { database.clearAllTables() }
        listOf("rent_plan_prefs", "tenant_history_prefs", "wohneinheiten_prefs", "google_drive_prefs").forEach {
            fixtureContext.getSharedPreferences(it, 0).edit().clear().commit()
        }
        ui.waitUntil(10000) { vm.receipts.value.isEmpty() && vm.properties.value.isEmpty() }
    }
    private fun settleEditorWindow() {
        // Native Robolectric does not settle a wrap-content text-input dialog window.
        // Give the real Android window a device-sized bound; production remains unchanged.
        ui.mainClock.advanceTimeByFrame()
        ui.runOnUiThread {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            assertTrue(dialog.isShowing)
            val density = ui.activity.resources.displayMetrics.density
            dialog.window!!.setLayout((360 * density).toInt(), (700 * density).toInt())
        }
        ui.waitForIdle()
    }
    private fun seed(open: Boolean = true, extraProperty: Boolean = false) {
        val db = AppDatabase.getDatabase(fixtureContext, CoroutineScope(Dispatchers.IO))
        runBlocking {
            db.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 81, propertyId = propertyId, name = "Sulzerstraße 32", wohneinheiten = "OG links,OG rechts,DG rechts"))
            if (extraProperty) db.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 82, propertyId = "rent-b", name = "Zweites Haus", wohneinheiten = "OG links"))
        }
        ui.waitUntil(10000) { vm.properties.value.any { it.propertyId == propertyId } }
        ui.runOnIdle { vm.selectProperty(propertyId) }
        ui.waitUntil(10000) { vm.propertyMetadata.value?.propertyId == propertyId && vm.wohneinheitenStatus.value.size == 3 }
        ui.runOnIdle {
            vm.wohneinheitenStatus.value.forEachIndexed { i, unit ->
                val updated = unit.copy(status = "Vermietet", mieter = listOf("Max Mustermann", "Anna Beispiel", "Mieter mit einem sehr langen Namen")[i],
                    kaltmiete = 600.0, mietvertragsstart = "2026-01-01", unitId = "u-${unit.name}")
                vm.updateWohneinheit(updated)
                PropertyUnitScopedData.setRentValues(fixtureContext, propertyId, updated, 150.0, 0.0)
                TenantHistoryStore.ensureCurrentPeriod(fixtureContext, updated, 150.0, 0.0, propertyId)
            }
        }
        runBlocking {
            var id = 940
            listOf("OG links", "OG rechts", "DG rechts").forEachIndexed { index, unit ->
                (1..12).forEach { month ->
                    val date = "2026-${month.toString().padStart(2, '0')}-03"
                    db.receiptDao().insertReceipt(Receipt(id = id++, aussteller = "Mieter", datum = date, uhrzeit = "", bruttobetrag = if (index == 2 && month == 5) 500.0 else 600.0,
                        hauptkategorie = "Miete, Nebenkosten & Kaution", unterkategorie = "Kaltmiete", kontoNr = "8100", beschreibung = "Miete", wohneinheit = unit, propertyId = propertyId))
                    db.receiptDao().insertReceipt(Receipt(id = id++, aussteller = "Mieter", datum = date, uhrzeit = "", bruttobetrag = 150.0,
                        hauptkategorie = "Miete, Nebenkosten & Kaution", unterkategorie = "Nebenkostenvorauszahlung", kontoNr = "8100", beschreibung = "NK", wohneinheit = unit, propertyId = propertyId))
                }
            }
            db.receiptDao().insertReceipt(Receipt(id = 1030, aussteller = "Nicht zugeordnet", datum = "2026-05-01", uhrzeit = "", bruttobetrag = 1200.0,
                hauptkategorie = "Miete, Nebenkosten & Kaution", unterkategorie = "Warmmiete", kontoNr = "8100", beschreibung = "Prüfen", propertyId = propertyId))
            db.receiptDao().insertReceipt(Receipt(id = 1031, aussteller = "Vorjahr", datum = "2025-01-01", uhrzeit = "", bruttobetrag = 900.0,
                hauptkategorie = "Miete, Nebenkosten & Kaution", unterkategorie = "Kaltmiete", kontoNr = "8100", beschreibung = "Vorjahr", wohneinheit = "OG links", propertyId = propertyId))
            if (extraProperty) db.receiptDao().insertReceipt(Receipt(id = 1032, aussteller = "Anderes Haus", datum = "2026-01-01", uhrzeit = "", bruttobetrag = 5000.0,
                hauptkategorie = "Miete, Nebenkosten & Kaution", unterkategorie = "Kaltmiete", kontoNr = "8100", beschreibung = "Andere Immobilie", wohneinheit = "OG links", propertyId = "rent-b"))
        }
        ui.waitUntil(10000) { vm.receipts.value.any { it.id == 1031 } }
        if (open) { ui.onNodeWithTag("nav_item_more").performClick(); ui.onNodeWithText("Mieteingänge").performClick() }
        ui.waitForIdle()
    }
    private fun capture(name: String) {
        ui.runOnIdle {
            fun redraw(view: android.view.View) {
                if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") view.javaClass.getMethod("invalidateDescendants").invoke(view)
                view.requestLayout(); view.invalidate()
                if (view is android.view.ViewGroup) for (i in 0 until view.childCount) redraw(view.getChildAt(i))
            }
            redraw(ui.activity.window.decorView)
        }
        ui.mainClock.advanceTimeBy(300); ui.waitForIdle()
        val path = "build/reports/rent-control/$name.png"
        onView(isRoot()).captureRoboImage(path)
        val bitmap = android.graphics.BitmapFactory.decodeFile(path)
        val density = ui.activity.resources.displayMetrics.density
        fun blue(top: Int, bottom: Int): Int {
            var count = 0
            for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(bitmap.height)) for (x in 0 until bitmap.width) {
                val c = bitmap.getPixel(x, y)
                if (android.graphics.Color.blue(c) > 150 && android.graphics.Color.red(c) < 100 && android.graphics.Color.green(c) < 160) count++
            }
            return count
        }
        assertTrue("Native screenshot must include the existing blue header icon", blue(0, (64 * density).toInt()) > 100)
        assertTrue("Native screenshot must include the bottom navigation", blue(bitmap.height - (88 * density).toInt(), bitmap.height) > 100)
        bitmap.recycle()
    }
    @Test fun realDataOverviewHasFourMetricsMonthlyBarsWarningAndThreeClearStatuses() {
        seed(); shell()
        ui.onNodeWithText("Mieteinnahmen & Nebenkosten").assertDoesNotExist()
        ui.onNode(hasText(NumberFormatter.format(26900.0)) and hasAnyAncestor(hasTestTag("rent_metric_actual"))).assertExists()
        ui.onNode(hasText(NumberFormatter.format(27000.0)) and hasAnyAncestor(hasTestTag("rent_metric_expected"))).assertExists()
        ui.onNode(hasText(NumberFormatter.format(5400.0)) and hasAnyAncestor(hasTestTag("rent_metric_utilities"))).assertExists()
        ui.onNode(hasText(NumberFormatter.format(100.0)) and hasAnyAncestor(hasTestTag("rent_metric_missing"))).assertExists()
        ui.onNodeWithText("Mietverlauf 2026").assertIsDisplayed()
        val mayActual = ui.onNodeWithTag("rent_bar_actual_5", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val mayExpected = ui.onNodeWithTag("rent_bar_expected_5", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("May's real shortfall must be visible in the chart", mayActual.height < mayExpected.height && mayActual.top > mayExpected.top)
        ui.onNodeWithText("1 Mietzahlung nicht zugeordnet").assertExists()
        capture("393-overview")
        scroll("rent_unit_${key("DG rechts")}")
        listOf("OG links", "OG rechts").forEach { name -> ui.onNodeWithTag("rent_status_${key(name)}", useUnmergedTree = true).assertTextEquals("Bezahlt") }
        ui.onNodeWithTag("rent_status_${key("DG rechts")}", useUnmergedTree = true).assertTextEquals("${NumberFormatter.format(100.0)} offen")
        shell(); capture("393-units")
    }
    @Test fun yearSwitchUsesExistingDataAndAndroidBackReturnsToMoreWithoutDuplicateEntries() {
        seed()
        ui.onNodeWithTag("rent_year_previous").performClick()
        ui.onNodeWithText("Mietverlauf 2025").assertExists()
        ui.onNode(hasText(NumberFormatter.format(900.0)) and hasAnyAncestor(hasTestTag("rent_metric_actual"))).assertExists()
        ui.onNodeWithTag("rent_year_next").performClick()
        ui.onNodeWithText("Mietverlauf 2026").assertExists()
        back(); assertEquals(AppScreen.MORE, vm.currentScreen.value); shell()
        back(); assertEquals(AppScreen.DASHBOARD, vm.currentScreen.value)
    }
    @Test fun propertyEntryIsScopedAndBackReturnsToTheSameProperty() {
        seed(open = false, extraProperty = true)
        ui.onNodeWithTag("nav_item_properties").performClick()
        ui.onNodeWithTag("property_card_$propertyId").performClick()
        ui.onNodeWithText("Mieteingänge").performScrollTo().performClick()
        overview().assertExists()
        ui.onNode(hasText(NumberFormatter.format(26900.0)) and hasAnyAncestor(hasTestTag("rent_metric_actual"))).assertExists()
        scroll("rent_unit_${key("DG rechts")}")
        ui.onNodeWithText("Zweites Haus").assertDoesNotExist()
        back(); assertEquals(AppScreen.PROPERTIES, vm.currentScreen.value)
        ui.onNodeWithText("Mieteingänge").assertExists(); shell()
    }
    @Test fun existingRentPlanDialogSavesCommaValuesAndRejectsNegativeAndInvalidDates() {
        seed(); scroll("rent_unit_${key("OG links")}")
        ui.onNodeWithTag("rent_edit_${key("OG links")}", useUnmergedTree = true).performClick()
        settleEditorWindow()
        ui.onNodeWithTag("rent_plan_cold").performTextReplacement("-5")
        ui.onNodeWithTag("rent_plan_save").performClick()
        ui.onNodeWithText("Bitte gültige Beträge ab 0 € eingeben (Komma oder Punkt).").assertExists()
        ui.onNodeWithTag("rent_plan_cold").performTextReplacement("650,50")
        ui.onNodeWithTag("rent_plan_start").performTextReplacement("2026-02-30")
        ui.onNodeWithTag("rent_plan_save").performClick()
        ui.onNodeWithText("Bitte einen gültigen Mietbeginn im Format JJJJ-MM-TT eingeben.").assertExists()
        ui.onNodeWithTag("rent_plan_start").performTextReplacement("2026-01-01")
        ui.onNodeWithTag("rent_plan_utilities").performTextReplacement("175,25")
        ui.onNodeWithTag("rent_plan_save").performClick()
        ui.waitUntil(10000) { vm.wohneinheitenStatus.value.first { it.name == "OG links" }.kaltmiete == 650.5 }
        val unit = vm.wohneinheitenStatus.value.first { it.name == "OG links" }
        assertEquals(175.25, PropertyUnitScopedData.rentValue(fixtureContext, propertyId, unit, "nk"), .001)
        val history = TenantHistoryStore.load(ui.activity, propertyId, unit.unitId, unit.name)
        assertEquals(650.5, history.single().kaltmiete, .001)
        assertEquals(175.25, history.single().nebenkosten, .001)
        assertEquals(650.5 + 175.25, RentTrackingLogic.month(ui.activity, propertyId, unit, vm.receipts.value, java.time.YearMonth.of(2026, 1)).expected, .001)
    }
    @Test fun editingSameNamedUnitInAnotherPropertySavesOnlyItsScopedPlan() {
        seed(extraProperty = true)
        scroll("rent_unit_rent-b:" + com.example.data.StableDocumentIdentity.legacyUnitId("rent-b", "OG links"))
        ui.onNodeWithTag("rent_edit_rent-b:" + com.example.data.StableDocumentIdentity.legacyUnitId("rent-b", "OG links"), useUnmergedTree = true).performClick()
        settleEditorWindow()
        ui.onNodeWithTag("rent_plan_cold").performTextReplacement("620,50")
        ui.onNodeWithTag("rent_plan_utilities").performTextReplacement("99,25")
        ui.onNodeWithTag("rent_plan_save").performClick()
        ui.waitUntil(10000) { vm.selectedPropertyId.value == propertyId &&
            vm.getWohneinheitenForProperty(vm.properties.value.first { it.propertyId == "rent-b" }).single().kaltmiete == 620.5 }
        val b = vm.getWohneinheitenForProperty(vm.properties.value.first { it.propertyId == "rent-b" }).single()
        assertEquals(99.25, PropertyUnitScopedData.rentValue(fixtureContext, "rent-b", b, "nk"), .001)
        assertEquals(600.0, vm.wohneinheitenStatus.value.first { it.name == "OG links" }.kaltmiete, .001)
        assertEquals(150.0, PropertyUnitScopedData.rentValue(fixtureContext, propertyId, vm.wohneinheitenStatus.value.first { it.name == "OG links" }, "nk"), .001)
    }
    @Test fun legacyUnitIdentityDoesNotUseAnotherSelectedPropertysNamespace() {
        seed(open = false)
        val db = AppDatabase.getDatabase(fixtureContext, CoroutineScope(Dispatchers.IO))
        val legacy = PropertyMetadata(id = 1, propertyId = StableDocumentIdentity.LEGACY_PROPERTY_ID, name = "Altbestand", wohneinheiten = "WE Alt")
        runBlocking { db.propertyDao().insertPropertyMetadata(legacy) }
        ui.waitUntil(10000) { vm.properties.value.any { it.id == 1 && it.wohneinheiten == "WE Alt" } }
        ui.runOnIdle {
            val unit = vm.getWohneinheitenForProperty(legacy).single()
            assertEquals(StableDocumentIdentity.legacyUnitId(legacy.propertyId, "WE Alt"), unit.unitId)
            assertEquals(propertyId, vm.selectedPropertyId.value)
        }
    }
    @Test fun monthlyCheckTenantHistoryAndUnassignedReviewRemainReachable() {
        seed(); ui.onNodeWithTag("rent_monthly_check").performClick()
        ui.onNodeWithText("Miet-Monatscheck").assertExists()
        repeat(5) { ui.onNodeWithText("‹").performClick() }
        ui.onNodeWithText("Offen (1)").assertExists()
        ui.onNodeWithText("TEILZAHLUNG").assertExists()
        ui.onNodeWithText("BEZAHLT").assertDoesNotExist()
        ui.onNodeWithText("Alle (3)").performClick()
        ui.onNodeWithTag("rent_monthly_list").performScrollToNode(hasText("BEZAHLT"))
        ui.onAllNodesWithText("BEZAHLT").assertCountEquals(2)
        ui.onNodeWithText("Schließen").performClick()
        scroll("rent_unit_${key("OG links")}")
        ui.onNodeWithTag("rent_history_${key("OG links")}", useUnmergedTree = true).performClick()
        ui.onNodeWithText("Mieterwechsel · OG links").assertExists()
        ui.onNodeWithText("Mieterwechsel erfassen", substring = true).assertExists()
        ui.onNodeWithText("Schließen").performClick()
        scroll("rent_unassigned"); ui.onNodeWithTag("rent_review_payments").performClick()
        assertEquals(AppScreen.RECEIPTS_LIST, vm.currentScreen.value); shell()
        back(); assertEquals(AppScreen.RENT_OVERVIEW, vm.currentScreen.value)
    }
    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h800dp-420dpi")
    fun compactScreenKeepsLargeAmountsInsideEquallySizedMetricCards() {
        seed()
        val db = AppDatabase.getDatabase(fixtureContext, CoroutineScope(Dispatchers.IO))
        runBlocking { db.receiptDao().insertReceipt(Receipt(id = 1110, aussteller = "Großer Betrag", datum = "2026-01-01", uhrzeit = "", bruttobetrag = 12345678.90,
            hauptkategorie = "Miete, Nebenkosten & Kaution", unterkategorie = "Kaltmiete", kontoNr = "8100", beschreibung = "Layouttest", wohneinheit = "OG links", propertyId = propertyId)) }
        ui.waitUntil(10000) { vm.receipts.value.any { it.id == 1110 } }
        val card = ui.onNodeWithTag("rent_metric_actual").fetchSemanticsNode().boundsInRoot
        val paired = ui.onNodeWithTag("rent_metric_expected").fetchSemanticsNode().boundsInRoot
        val amount = ui.onNode(hasText(NumberFormatter.format(12345678.90 + 26900.0)) and hasAnyAncestor(hasTestTag("rent_metric_actual"))).fetchSemanticsNode().boundsInRoot
        assertTrue(amount.left >= card.left && amount.right <= card.right && amount.bottom <= card.bottom)
        assertEquals(card.height, paired.height, .5f)
        assertTrue(card.right <= paired.left)
        shell(); capture("360-large-values")
    }
    @Test fun noRentalDataHasAUsefulEmptyState() {
        ui.onNodeWithTag("nav_item_more").performClick(); ui.onNodeWithText("Mieteingänge").performClick()
        scroll("rent_empty")
        ui.onNodeWithText("Noch keine Mieteingänge vorhanden").assertIsDisplayed()
        ui.onNodeWithText("Hinterlege Mietdaten bei deinen Wohneinheiten und ordne eingehende Zahlungen zu.").assertIsDisplayed()
        ui.onNodeWithTag("rent_unassigned").assertDoesNotExist(); shell()
    }
}
