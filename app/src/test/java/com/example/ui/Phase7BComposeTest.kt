package com.example.ui

import android.app.Application
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import com.example.MainActivity
import com.example.data.*
import com.example.ui.theme.MyApplicationTheme
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
class Phase7BComposeTest {
    @get:Rule(order = 0) val isolation = IsolatedAndroidApplicationRule()
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(ui.activity)[ReceiptViewModel::class.java]
    private lateinit var db: AppDatabase
    @Before fun clear() {
        db = AppDatabase.getDatabase(ui.activity.application as Application, CoroutineScope(Dispatchers.IO))
        runBlocking(Dispatchers.IO) { db.clearAllTables() }
        PersistentPreferenceInventory.stores.forEach { ui.activity.getSharedPreferences(it.name, 0).edit().clear().commit() }
        ui.waitUntil(10_000) { vm.properties.value.isEmpty() && vm.receipts.value.isEmpty() }
    }
    private fun batch(count: Int, scale: Float, tablet: Boolean = false) {
        val p = PropertyMetadata(id = 91, propertyId = "batch", name = "Haus",
            wohneinheiten = (1..count).joinToString(", ") { "WE $it" })
        runBlocking(Dispatchers.IO) { db.propertyDao().insertPropertyMetadata(p) }
        ui.waitUntil(10_000) { vm.properties.value.size == 1 }
        val model = vm
        val units = model.getWohneinheitenForProperty(p)
        val first = units.first().unitId; val last = units.last().unitId
        var dismissed = false
        ui.runOnUiThread { ui.activity.setContent { MyApplicationTheme {
            CompositionLocalProvider(LocalDensity provides Density(ui.activity.resources.displayMetrics.density, scale)) {
                InitialRentBatchDialog(p, units, model, {}, { dismissed = true })
            }
        } } }
        settleWindow(if (tablet) 880 else 393)
        ui.onNodeWithTag(if (tablet) "rent_batch_table_$first" else "rent_batch_vertical_$first").assertExists()
        if (!tablet) {
            val frame = ui.onNodeWithTag("rent_batch_rows").fetchSemanticsNode().boundsInRoot
            val bounds = mutableListOf<androidx.compose.ui.geometry.Rect>()
            listOf("tenant", "start", "cold", "utilities", "other").forEach { name ->
                val field = ui.onNodeWithTag("rent_batch_${name}_$first").performScrollTo().assertIsDisplayed()
                val rect = field.fetchSemanticsNode().boundsInRoot
                assertTrue("$name must fit within dialog width at $scale", rect.left >= frame.left && rect.right <= frame.right)
                bounds += rect
            }
            assertTrue("Phone fields share the same full-width column", bounds.all { kotlin.math.abs(it.width - bounds.first().width) < 1f })
        }
        ui.onNodeWithTag("rent_batch_tenant_$first").performScrollTo().performTextReplacement("Mieter A")
        ui.onNodeWithTag("rent_batch_cold_$first").performScrollTo().performTextReplacement("760,50")
        ui.onNodeWithTag("rent_batch_start_$first").performScrollTo().performTextReplacement("01.01.2026")
        ui.onNodeWithTag("rent_batch_status_$first").performScrollTo().performClick()
        ui.onNodeWithText("Vermietet").performClick()
        if (count > 1) {
            ui.onNodeWithTag("rent_batch_rows").performScrollToNode(hasTestTag("rent_batch_select_$last"))
            ui.onNodeWithTag("rent_batch_select_$last").performClick()
            ui.onNodeWithTag("rent_batch_rows").performScrollToNode(hasTestTag("rent_batch_select_$first"))
        }
        ui.onNodeWithTag("rent_batch_tenant_$first").performScrollTo().assertTextContains("Mieter A")
        ui.onNodeWithTag("rent_batch_cold_$first").performScrollTo().assertTextContains("760,50")
        ui.onNodeWithTag("rent_batch_confirm").assertIsDisplayed().performClick()
        ui.onNodeWithText("${if (count == 1) 1 else 2} Einheiten speichern?").assertIsDisplayed()
        assertTrue(model.getWohneinheitenForProperty(p).all { it.mieter.isBlank() })
        ui.onNodeWithTag("rent_batch_rows").captureRoboImage("build/reports/phase7b/batch-$count-scale-$scale-tablet-$tablet.png")
        ui.onNodeWithTag("rent_batch_confirm").performClick()
        ui.waitUntil(10_000) { dismissed }
        val saved = model.getWohneinheitenForProperty(p)
        assertEquals(units.map { it.unitId }, saved.map { it.unitId })
        assertEquals("Mieter A", saved.first().mieter)
        assertEquals(760.5, saved.first().kaltmiete, .001)
        assertTrue(saved.drop(1).all { it.mieter.isBlank() })
    }
    private fun settleWindow(width: Int = 393) {
        ui.waitForIdle()
        ui.onAllNodes(isDialog()).onFirst().assertExists()
        ui.mainClock.advanceTimeByFrame()
        ui.runOnUiThread {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            val density = ui.activity.resources.displayMetrics.density
            dialog.window!!.setLayout((width * density).toInt(), (800 * density).toInt())
        }
        ui.waitForIdle()
    }
    @Test fun oneUnit393Scale10() = batch(1, 1f)
    @Test fun eightUnits393Scale13() = batch(8, 1.3f)
    @Test fun twentyUnits393Scale15() = batch(20, 1.5f)
    @Test @Config(qualifiers = "w1000dp-h1000dp-160dpi") fun tabletRetainsSingleRow() = batch(8, 1f, true)

    @Test fun cancelAndSystemBackWriteNoDraftRentData() {
        val p = PropertyMetadata(id = 91, propertyId = "cancel", name = "Haus", wohneinheiten = "WE 1")
        runBlocking(Dispatchers.IO) { db.propertyDao().insertPropertyMetadata(p) }
        ui.waitUntil(10_000) { vm.properties.value.size == 1 }
        val model = vm; val units = model.getWohneinheitenForProperty(p)
        var dismissals = 0
        ui.runOnUiThread { ui.activity.setContent { MyApplicationTheme { InitialRentBatchDialog(p, units, model, {}, { dismissals++ }) } } }
        settleWindow()
        ui.onNodeWithTag("rent_batch_tenant_${units.first().unitId}").performTextReplacement("Entwurf")
        ui.onNodeWithText("Abbrechen").performClick()
        assertEquals(1, dismissals)
        ui.runOnIdle {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.activity.ComponentDialog
            dialog.onBackPressedDispatcher.onBackPressed()
        }
        assertEquals(2, dismissals)
        assertEquals("", model.getWohneinheitenForProperty(p).first().mieter)
        assertTrue(TenantHistoryStore.load(ui.activity, p.propertyId, units.first().unitId, units.first().name).isEmpty())
    }

    @Test fun existingLoanNumbersRoundTripWithoutChangingAnyValue() {
        val original = Loan(id = 901, propertyId = "loan", bezeichnung = "Bestand", darlehensbetrag = 100000.0,
            restschuld = 90000.0, sollzinsProzent = 3.0, tilgungProzent = 3.5, monatlicheRate = 100000.25,
            vermietungsanteilProzent = 87.12345, startDatum = "2026-01-01")
        var saved: Loan? = null
        ui.runOnUiThread { ui.activity.setContent { MyApplicationTheme { LoanEditDialog(original, "Darlehen bearbeiten", {}, { saved = it }) } } }
        settleWindow()
        mapOf("Darlehenssumme €*" to "100000,00", "Restschuld €*" to "90000,00", "Sollzins %*" to "3,00",
            "Tilgung %" to "3,50", "Monatliche Rate €" to "100000,25", "Vermietungsanteil %" to "87,12345").forEach { (label, value) ->
            ui.onNode(hasText(label) and hasSetTextAction()).performScrollTo().assertTextContains(value)
        }
        ui.onNodeWithTag("save_loan_button").performClick()
        assertEquals(original, saved)
        fun enter(label: String, value: String) = ui.onNode(hasText(label) and hasSetTextAction()).performScrollTo().performTextReplacement(value)
        listOf("100000", "100000,00").forEach { value ->
            enter("Darlehenssumme €*", value)
            ui.onNodeWithTag("save_loan_button").performClick()
            assertEquals(original, saved)
        }
        enter("Sollzins %*", "3")
        ui.onNodeWithTag("save_loan_button").performClick(); assertEquals(original, saved)
        enter("Sollzins %*", "3,5"); enter("Restschuld €*", "90000,25")
        ui.onNodeWithTag("save_loan_button").performClick()
        assertEquals(original.copy(sollzinsProzent = 3.5, restschuld = 90000.25), saved)
        saved = null
        enter("Darlehenssumme €*", "ungültig")
        ui.onNodeWithTag("save_loan_button").performClick()
        ui.onNodeWithText("Darlehenssumme ist ungültig.").assertExists()
        assertNull(saved)
    }
}
