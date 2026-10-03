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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class LedgerComposeTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(ui.activity)[ReceiptViewModel::class.java]
    private fun seed(openLedger: Boolean = true) {
        val database = AppDatabase.getDatabase(ui.activity.application as Application, CoroutineScope(Dispatchers.IO))
        runBlocking {
            // Deliberately identical display names ensure filtering really uses stable IDs.
            database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 81, propertyId = "ledger-a", name = "Testhaus"))
            database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 82, propertyId = "ledger-b", name = "Testhaus"))
            listOf(
                Receipt(id = 901, aussteller = "Mieter Müller", datum = "2026-05-03", uhrzeit = "", bruttobetrag = 1200.0,
                    hauptkategorie = "Miete, Nebenkosten & Kaution", unterkategorie = "Mieteinnahmen", kontoNr = "8100",
                    beschreibung = "Mai-Miete", propertyId = "ledger-a", displayId = "R-901"),
                Receipt(id = 902, aussteller = "Hornbach", datum = "2026-04-28", uhrzeit = "", bruttobetrag = 256.4,
                    hauptkategorie = "Sanierung", unterkategorie = "Instandhaltung", kontoNr = "4830",
                    beschreibung = "Fenster reparieren", propertyId = "ledger-b", displayId = "R-902"),
                Receipt(id = 903, aussteller = "Stadtwerke", datum = "2026-04-20", uhrzeit = "", bruttobetrag = 412.0,
                    hauptkategorie = "Betriebskosten", unterkategorie = "Betriebskosten", kontoNr = "4670",
                    beschreibung = "Wasser", propertyId = "ledger-a"),
                Receipt(id = 904, aussteller = "Vorjahr", datum = "2025-12-31", uhrzeit = "", bruttobetrag = 1000.0,
                    hauptkategorie = "Miete, Nebenkosten & Kaution", unterkategorie = "Mieteinnahmen", kontoNr = "8100",
                    beschreibung = "Dezember", propertyId = "ledger-a")
            ).forEach { database.receiptDao().insertReceipt(it) }
        }
        ui.waitUntil(10000) { vm.receipts.value.count { it.id in 901..904 } == 4 && vm.properties.value.size >= 2 }
        if (openLedger) ui.runOnIdle { vm.setScreen(AppScreen.LEDGER) }
        ui.waitForIdle()
    }
    private fun shell() {
        ui.onNodeWithText("ImmoPilot").assertIsDisplayed()
        ui.onNodeWithTag("bottom_navigation").assertIsDisplayed()
    }
    private fun scroll(tag: String) = ui.onNodeWithTag("ledger_overview").performScrollToNode(hasTestTag(tag))
    private fun capture(name: String) {
        ui.runOnIdle {
            fun redraw(view: android.view.View) {
                // Compose render layers are not Android child Views. Invalidate them as well
                // so native Robolectric captures retain correct text positions after lazy scrolling.
                if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") {
                    view.javaClass.getMethod("invalidateDescendants").invoke(view)
                }
                view.requestLayout()
                view.invalidate()
                if (view is android.view.ViewGroup) {
                    for (index in 0 until view.childCount) redraw(view.getChildAt(index))
                }
            }
            redraw(ui.activity.window.decorView)
        }
        ui.mainClock.advanceTimeBy(300)
        ui.waitForIdle()
        val path = "build/reports/ledger-financial-overview/$name.png"
        onView(isRoot()).captureRoboImage(path)
        // Verify the recorded pixels too: semantics alone cannot catch incomplete native redraws.
        val bitmap = android.graphics.BitmapFactory.decodeFile(path)
        val density = ui.activity.resources.displayMetrics.density
        fun hasBlue(top: Int, bottom: Int): Boolean {
            var count = 0
            for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(bitmap.height)) {
                for (x in 0 until bitmap.width) {
                    val pixel = bitmap.getPixel(x, y)
                    if (android.graphics.Color.blue(pixel) > 150 && android.graphics.Color.red(pixel) < 100 &&
                        android.graphics.Color.green(pixel) < 160) count++
                }
            }
            return count > 100
        }
        assertTrue("Screenshot must show the ImmoPilot header", hasBlue(0, (56 * density).toInt()))
        assertTrue("Screenshot must show the bottom navigation", hasBlue(bitmap.height - (88 * density).toInt(), bitmap.height))
        bitmap.recycle()
    }
    private fun systemBack() { ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }; ui.waitForIdle() }

    @Test
    fun financialOverviewEndsAfterBookingsAndKeepsTheShell() {
        seed()
        ui.onNode(hasText(LedgerPresentation.money(1200.0)) and hasAnyAncestor(hasTestTag("ledger_metric_Einnahmen"))).assertExists()
        ui.onNode(hasText(LedgerPresentation.money(668.4)) and hasAnyAncestor(hasTestTag("ledger_metric_Ausgaben"))).assertExists()
        ui.onNode(hasText(LedgerPresentation.money(531.6)) and hasAnyAncestor(hasTestTag("ledger_metric_Ergebnis"))).assertExists()
        ui.onNode(hasText("3") and hasAnyAncestor(hasTestTag("ledger_metric_Buchungen"))).assertExists()
        ui.onNodeWithText("Ausgaben nach Kategorie").assertExists()
        ui.onNodeWithTag("ledger_categories").assertExists()
        ui.onNode(hasText(LedgerPresentation.money(668.4)) and hasAnyAncestor(hasTestTag("ledger_categories"))).assertExists()
        ui.onNode(hasText(LedgerPresentation.money(256.4)) and hasAnyAncestor(hasTestTag("ledger_categories"))).assertExists()
        ui.onNode(hasText("38 %") and hasAnyAncestor(hasTestTag("ledger_categories"))).assertExists()
        val incomeBar = ui.onNodeWithTag("ledger_bar_INCOME_5").fetchSemanticsNode().boundsInRoot
        val expenseBar = ui.onNodeWithTag("ledger_bar_EXPENSE_4").fetchSemanticsNode().boundsInRoot
        assertTrue(incomeBar.height > expenseBar.height && expenseBar.height > 0)
        assertEquals("Monthly bars must represent actual April expenses and May income",
            (668.4 / 1200.0).toFloat(), expenseBar.height / incomeBar.height, .01f)
        assertEquals(0f, ui.onNodeWithTag("ledger_bar_INCOME_1").fetchSemanticsNode().boundsInRoot.height, 0.01f)
        val chartOrder = ui.onNodeWithTag("ledger_chart").fetchSemanticsNode().boundsInRoot
        val categoriesOrder = ui.onNodeWithTag("ledger_categories").fetchSemanticsNode().boundsInRoot
        assertTrue("Year chart must precede categories", chartOrder.bottom <= categoriesOrder.top)
        // Density regression: metrics stay under 104 dp, chart under 120 dp and bookings under 80 dp.
        val density = ui.activity.resources.displayMetrics.density
        val metric = ui.onNodeWithTag("ledger_metric_Einnahmen").fetchSemanticsNode().boundsInRoot
        assertTrue("Metrics must remain compact", metric.height / density <= 104f)
        val chart = ui.onNodeWithTag("ledger_chart").fetchSemanticsNode().boundsInRoot
        assertTrue("Chart must remain compact", chart.height / density <= 120f)
        assertNoAccountingActions()
        shell(); capture("393-overview")
        scroll("ledger_receipt_901")
        ui.onNodeWithText(LedgerPresentation.signedMoney(vm.receipts.value.first { it.id == 901 })).assertExists()
        ui.onNodeWithText(LedgerPresentation.signedMoney(vm.receipts.value.first { it.id == 902 })).assertExists()
        scroll("ledger_kind_ALL")
        val segment = ui.onNodeWithTag("ledger_kind_ALL").fetchSemanticsNode().boundsInRoot
        assertTrue("Filter segments must remain compact", segment.height / density <= 40f)
        scroll("ledger_receipt_901")
        val booking = ui.onNodeWithTag("ledger_receipt_901").fetchSemanticsNode().boundsInRoot
        assertTrue("Booking rows must remain compact", booking.height / density <= 80f)
        shell(); capture("393-bookings")
        scroll("ledger_end")
        shell(); capture("393-end")
        assertNoAccountingActions()

    }


    private fun assertNoAccountingActions() {
        listOf("ledger_exports", "ledger_tools", "accounting_pdf", "accounting_tools",
            "legacy_description_backfill_button", "existing_payment_backfill_button").forEach {
            ui.onNodeWithTag(it).assertDoesNotExist()
        }
        listOf("DATEV", "PDF-Bericht", "SKR03", "Saldenaufstellung", "Datenpflege", "Weitere Werkzeuge", "Export & Auswertung").forEach {
            ui.onAllNodes(hasText(it, substring = true)).assertCountEquals(0)
        }
    }

    @Test @Config(shadows = [LedgerPdfDocumentShadow::class])
    fun accountingActionsRemainReachableFromExistingDatevEntryInMore() {
        seed(openLedger = false)
        ui.onNodeWithTag("nav_item_more").performClick()
        ui.onNodeWithText("DATEV Export").performScrollTo().performClick()
        assertEquals(AppScreen.DATEV_EXPORT, vm.currentScreen.value)
        ui.onNodeWithTag("accounting_tools").performScrollTo().performClick()
        ui.onNodeWithTag("legacy_description_backfill_button").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("existing_payment_backfill_button").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Saldenaufstellung").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("DATEV-Kontenrahmen SKR 03").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("accounting_pdf").performScrollTo().performClick()
        ui.onNodeWithText("Finanzamt PDF Export").assertIsDisplayed()
        ui.onNodeWithText("Exportieren & Teilen").assertIsEnabled()
        ui.onNodeWithText("Exportieren & Teilen").performClick()
        assertTrue(java.io.File(ui.activity.cacheDir, "Steuerbericht_Finanzamt_2026.pdf").isFile)
        assertTrue(LedgerPdfDocumentShadow.startedPages > 0)
        assertEquals(LedgerPdfDocumentShadow.startedPages, LedgerPdfDocumentShadow.finishedPages)
        assertEquals(1, LedgerPdfDocumentShadow.writes)
        val chooser = org.robolectric.Shadows.shadowOf(ui.activity).nextStartedActivity
        assertEquals(android.content.Intent.ACTION_CHOOSER, chooser.action)
        val send = chooser.getParcelableExtra(android.content.Intent.EXTRA_INTENT, android.content.Intent::class.java)!!
        assertEquals(android.content.Intent.ACTION_SEND, send.action)
        assertEquals("application/pdf", send.type)
        systemBack(); assertEquals(AppScreen.MORE, vm.currentScreen.value); shell()
    }

    @Test fun filtersSearchAndReceiptDetailsWorkWithDuplicatePropertyNames() {
        seed()
        scroll("ledger_kind_INCOME"); ui.onNodeWithTag("ledger_kind_INCOME").performClick()
        scroll("ledger_receipt_901"); ui.onNodeWithTag("ledger_receipt_902").assertDoesNotExist()
        scroll("ledger_kind_EXPENSE"); ui.onNodeWithTag("ledger_kind_EXPENSE").performClick()
        scroll("ledger_receipt_902"); ui.onNodeWithTag("ledger_receipt_901").assertDoesNotExist()
        scroll("ledger_kind_ALL"); ui.onNodeWithTag("ledger_kind_ALL").performClick()
        scroll("ledger_property"); ui.onNodeWithTag("ledger_property").performClick()
        ui.onAllNodesWithText("Testhaus").onLast().performClick()
        scroll("ledger_receipt_902"); ui.onNodeWithTag("ledger_receipt_901").assertDoesNotExist()
        scroll("ledger_property"); ui.onNodeWithTag("ledger_property").performClick()
        ui.onNodeWithText("Alle Immobilien").performClick()
        scroll("ledger_category"); ui.onNodeWithTag("ledger_category").performClick()
        ui.onAllNodesWithText("Instandhaltung").onLast().performClick()
        scroll("ledger_receipt_902"); ui.onNodeWithTag("ledger_receipt_901").assertDoesNotExist()
        scroll("ledger_category"); ui.onNodeWithTag("ledger_category").performClick()
        ui.onNodeWithText("Alle Kategorien").performClick()
        scroll("ledger_search"); ui.onNodeWithTag("ledger_search").performTextInput("R-902")
        scroll("ledger_receipt_902"); ui.onNodeWithTag("ledger_receipt_901").assertDoesNotExist()
        ui.onNodeWithTag("ledger_receipt_902").performClick()
        assertEquals(AppScreen.RECEIPT_DETAIL, vm.currentScreen.value)
        systemBack(); assertEquals(AppScreen.LEDGER, vm.currentScreen.value); shell()
        scroll("ledger_year"); ui.onNodeWithTag("ledger_year").performClick()
        ui.onNodeWithText("2025").performClick()
        // Search resets only on explicit user action; clear it to see the previous year.
        scroll("ledger_search"); ui.onNodeWithTag("ledger_search").performTextClearance()
        scroll("ledger_receipt_904"); ui.onNodeWithTag("ledger_receipt_901").assertDoesNotExist()
    }

    @Test fun systemBackReturnsToMoreAndTheGlobalNavigationRemainsVisible() {
        seed(openLedger = false)
        ui.onNodeWithTag("nav_item_more").performClick()
        ui.onNodeWithText("Einnahmen & Ausgaben").performScrollTo().performClick()
        assertEquals(AppScreen.LEDGER, vm.currentScreen.value)
        systemBack(); assertEquals(AppScreen.MORE, vm.currentScreen.value); shell()
    }

    private fun responsive(name: String) {
        seed(); shell()
        val title = ui.onNodeWithTag("ledger_title").fetchSemanticsNode().boundsInRoot
        assertTrue("Title and year must share a compact row", title.height / ui.activity.resources.displayMetrics.density <= 30f)
        capture("$name-overview")
        scroll("ledger_receipt_901"); shell(); capture("$name-bookings")
        scroll("ledger_end"); shell()
        val incomeLabel = ui.onNode(hasText("Einnahmen") and hasAnyAncestor(hasTestTag("ledger_kind_INCOME")),
            useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val expenseLabel = ui.onNode(hasText("Ausgaben") and hasAnyAncestor(hasTestTag("ledger_kind_EXPENSE")),
            useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Inactive filter labels must stay separate after scrolling", incomeLabel.right < expenseLabel.left)
        capture("$name-end")
        assertNoAccountingActions()
        val frame = ui.onNodeWithTag("ledger_overview").fetchSemanticsNode().boundsInRoot
        listOf("ledger_chart", "ledger_categories", "ledger_bookings").forEach { tag ->
            scroll(tag)
            val bounds = ui.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag exceeds content width", bounds.left >= frame.left && bounds.right <= frame.right)
        }
    }
    @Test @Config(qualifiers = "w360dp-h800dp-420dpi") fun compactPhone() = responsive("360")
    @Test @Config(qualifiers = "w480dp-h960dp-420dpi") fun largePhone() = responsive("480")

    @Test fun emptyStateAndLargeAmountsStayReadableOnCompactPhones() {
        ui.runOnIdle { vm.setScreen(AppScreen.LEDGER) }
        ui.onNodeWithTag("ledger_overview").performScrollToNode(hasText("Noch keine Einnahmen oder Ausgaben vorhanden"))
        ui.onNodeWithText("Noch keine Einnahmen oder Ausgaben vorhanden").assertIsDisplayed()
        shell()
        seed()
        val database = AppDatabase.getDatabase(ui.activity.application as Application, CoroutineScope(Dispatchers.IO))
        runBlocking {
            val original = vm.receipts.value.first { it.id == 901 }
            database.receiptDao().insertReceipt(original.copy(id = 901,
                aussteller = "Sehr langer Ausstellername für die Prüfung der Buchungskarten auf kleinen Geräten",
                bruttobetrag = 9999999.99))
        }
        ui.waitUntil(10000) { vm.receipts.value.any { it.id == 901 && it.bruttobetrag == 9999999.99 } }
        scroll("ledger_metric_Einnahmen")
        ui.onNodeWithText(LedgerPresentation.money(9999999.99)).assertExists()
        scroll("ledger_chart")
        ui.onNodeWithText("10 Mio. €").assertExists()
        scroll("ledger_metric_Einnahmen")
        capture("393-large-amount-metrics")
        scroll("ledger_receipt_901"); shell(); capture("393-large-amount-booking")
        runBlocking {
            val rows = vm.receipts.value
            database.receiptDao().insertReceipt(rows.first { it.id == 901 }.copy(bruttobetrag = 1200.0))
            database.receiptDao().insertReceipt(rows.first { it.id == 902 }.copy(bruttobetrag = -256.4))
            database.receiptDao().insertReceipt(rows.first { it.id == 903 }.copy(datum = "2026-03-20"))
        }
        ui.waitUntil(10000) { vm.receipts.value.any { it.id == 902 && it.bruttobetrag < 0 } &&
            vm.receipts.value.any { it.id == 901 && it.bruttobetrag == 1200.0 } &&
            vm.receipts.value.any { it.id == 903 && it.datum == "2026-03-20" } }
        scroll("ledger_chart")
        val refund = ui.onNodeWithTag("ledger_bar_EXPENSE_4").fetchSemanticsNode().boundsInRoot
        val zero = ui.onNodeWithTag("ledger_chart_zero").fetchSemanticsNode().boundsInRoot
        assertTrue("A refund must be drawn below zero", refund.top >= zero.top - 1 && refund.bottom > zero.bottom)
        shell(); capture("393-refund-chart")
    }
}
