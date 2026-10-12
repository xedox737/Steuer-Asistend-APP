package com.example.e2e

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.MainActivity
import com.example.data.Loan
import com.example.data.SupplementalDriveBackup
import com.example.ui.AppScreen
import com.example.ui.LoanEditDialog
import com.example.ui.LoanInputValidation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Android Compose, real object navigation, Room and a fresh VM; inherited device is ~393 dp. */
abstract class Phase8ALoanValidationMatrix : EmulatorTestSupport() {
    @Test fun invalidDraftCannotPersistThenRepairReopenRecreateAndNewActivityKeepValues() {
        val a = seedProperty("p8-a", "MFH Validierung A")
        val b = seedProperty("p8-b", "MFH Validierung B")
        val original = Loan(id = 801, propertyId = a.propertyId, bezeichnung = "Darlehen A",
            darlehensbetrag = 100000.25, restschuld = 90000.0, sollzinsProzent = 3.125,
            tilgungProzent = 1.5, monatlicheRate = 600.25, vermietungsanteilProzent = 87.12345,
            startDatum = "2026-01-01")
        runBlocking(Dispatchers.IO) {
            db.loanDao().upsertLoan(original)
            db.loanDao().upsertLoan(original.copy(id = 802, propertyId = b.propertyId, bezeichnung = "Darlehen B"))
        }
        open(a.propertyId, "Darlehen A")
        screenshot("valid")
        save()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Darlehen bearbeiten").fetchSemanticsNodes().isEmpty() }
        assertEquals(original, stored())
        edit()
        loanInput("Restschuld €*", "9".repeat(310))
        save(); error("BALANCE", "Der Wert ist zu groß oder ungültig.")
        assertEquals(original, stored()); screenshot("extreme-balance")
        ui.onNodeWithText("Darlehen bearbeiten").assertExists()
        loanInput("Restschuld €*", "89000.25")
        loanInput("Tilgung %", "abc"); loanInput("Monatliche Rate €", "-500")
        save(); error("REPAYMENT", "Bitte eine gültige Zahl eingeben.")
        screenshot("invalid-repayment")
        error("MONTHLY", "Der Wert darf nicht negativ sein."); screenshot("invalid-monthly")
        assertEquals(original, stored())
        loanInput("Tilgung %", "-2"); loanInput("Monatliche Rate €", "1,2,3")
        save(); error("REPAYMENT", "Der Wert darf nicht negativ sein.")
        error("MONTHLY", "Bitte eine gültige Zahl eingeben.")
        loanInput("Tilgung %", "2,125"); loanInput("Monatliche Rate €", "700,25")
        loanInput("Vermietungsanteil %", "abc"); save(); error("RENTED", "Bitte eine gültige Zahl eingeben.")
        loanInput("Vermietungsanteil %", "120"); save()
        error("RENTED", "Bitte einen Wert zwischen 0 und 100 eingeben."); screenshot("invalid-share")
        assertEquals(original, stored())
        ui.onNodeWithTag("save_loan_button").assertIsDisplayed()
        ui.onNode(hasText("Abbrechen") and hasAnyAncestor(isDialog())).assertIsDisplayed().performClick()
        assertEquals(original, stored())
        edit()
        loanInput("Restschuld €*", "89000.25")
        loanInput("Tilgung %", "2,125"); loanInput("Monatliche Rate €", "700,25")
        loanInput("Vermietungsanteil %", "0")
        save()
        val repaired = original.copy(restschuld = 89000.25, tilgungProzent = 2.125, monatlicheRate = 700.25, vermietungsanteilProzent = 0.0)
        ui.waitUntil(10_000) { stored() == repaired }
        edit(); loanInput("Vermietungsanteil %", "100"); save()
        val final = repaired.copy(vermietungsanteilProzent = 100.0)
        ui.waitUntil(10_000) { stored() == final }
        edit(); loanInput("Restschuld €*", "89000,25"); screenshot("repaired")
        ui.onNodeWithTag("save_loan_button").performClick()
        assertEquals(final, stored())
        ui.onNodeWithText("Schließen").performClick()
        scenario.recreate()
        ui.onNodeWithTag("loan_management_card").performClick(); edit()
        inputField("Restschuld €*").assertTextContains("89000,25")
        assertEquals(final, stored())
        scenario.close(); scenario = ActivityScenario.launch(MainActivity::class.java)
        open(b.propertyId, "Darlehen B")
        inputField("Restschuld €*").assertTextContains("90000,00")
        ui.onAllNodesWithTag("loan_error_BALANCE").assertCountEquals(0)
        ui.onNode(hasText("Abbrechen") and hasAnyAncestor(isDialog())).performClick()
        ui.onNodeWithText("Schließen").performClick()
        open(a.propertyId, "Darlehen A")
        inputField("Restschuld €*").assertTextContains("89000,25")
        assertEquals(final, stored())
    }

    @Test fun restoredInfinityOpensWithoutCrashAndCanBeCancelledOrRepaired() {
        val a = seedProperty("p8-legacy", "Altbestand")
        // JSON cannot contain a numeric Infinity literal; historical strings are read by optDouble.
        val payload = JSONObject().put("schemaVersion", 15).put("loans", JSONArray().put(JSONObject()
            .put("id", 801).put("propertyId", a.propertyId).put("bezeichnung", "Altdarlehen")
            .put("darlehensbetrag", 100000).put("restschuld", "Infinity").put("sollzinsProzent", 3)))
        runBlocking(Dispatchers.IO) { SupplementalDriveBackup.restorePayload(context, db, payload) }
        assertEquals(Double.POSITIVE_INFINITY, stored().restschuld, 0.0)
        open(a.propertyId, "Altdarlehen")
        error("BALANCE", LoanInputValidation.STORED_ERROR); screenshot("legacy-infinity")
        save(); assertEquals(Double.POSITIVE_INFINITY, stored().restschuld, 0.0)
        ui.onNode(hasText("Abbrechen") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(Double.POSITIVE_INFINITY, stored().restschuld, 0.0)
        edit(); loanInput("Restschuld €*", "85000,50"); save()
        ui.waitUntil(10_000) { stored().restschuld == 85000.5 }
        edit(); inputField("Restschuld €*").assertTextContains("85000,50")
        ui.onAllNodesWithTag("loan_error_BALANCE").assertCountEquals(0)
        screenshot("legacy-repaired")
    }

    @Test fun nanModelFixtureRendersRepairableWithoutSqliteConvertingNanToNull() {
        // SQLite maps bound NaN to NULL. Exercise the actual editor directly to retain NaN.
        var saved: Loan? = null
        scenario.onActivity { activity -> activity.setContent {
            MaterialTheme { LoanEditDialog(Loan(id = 899, bezeichnung = "NaN-Altbestand",
                darlehensbetrag = 100000.0, restschuld = Double.NaN, sollzinsProzent = 3.0),
                "Darlehen bearbeiten", {}, { saved = it }) }
        } }
        error("BALANCE", LoanInputValidation.STORED_ERROR); screenshot("legacy-nan")
        save(); ui.runOnIdle { assertNull(saved) }
        loanInput("Restschuld €*", "85000,50"); save()
        ui.runOnIdle { assertEquals(85000.5, saved!!.restschuld, 0.0) }
    }

    private fun stored() = runBlocking(Dispatchers.IO) { db.loanDao().getAllLoans().single { it.id == 801 } }
    private fun open(id: String, name: String) {
        clickTab(AppScreen.PROPERTIES)
        ui.onNodeWithTag("property_card_$id").performScrollTo().performClick()
        ui.onNode(hasText("Finanzierung") and hasClickAction()).performScrollTo().performClick()
        ui.onNodeWithTag("loan_management_card").performClick()
        ui.onNodeWithText(name).performScrollTo().assertIsDisplayed()
        edit()
    }
    private fun edit() = ui.onNodeWithContentDescription("Bearbeiten").performScrollTo().performClick()
    private fun inputField(label: String): SemanticsNodeInteraction {
        ui.onNodeWithTag("loan_editor_fields").performScrollToNode(hasText(label) and hasSetTextAction())
        return ui.onNode(hasText(label) and hasSetTextAction()).performScrollTo()
    }
    private fun loanInput(label: String, value: String) {
        inputField(label).performTextReplacement(value)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
    }
    private fun save() = ui.onNodeWithTag("save_loan_button").assertIsDisplayed().performClick()
    private fun error(field: String, expected: String) {
        inputField(LoanInputValidation.Field.valueOf(field).label)
        val node = ui.onNodeWithTag("loan_error_$field", useUnmergedTree = true).performScrollTo().assertIsDisplayed().assertTextEquals(expected)
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
        assertTrue(results.isNotEmpty())
        results.forEach {
            assertFalse(it.didOverflowWidth); assertFalse(it.didOverflowHeight)
            assertFalse(it.multiParagraph.didExceedMaxLines)
            assertEquals(expected.length, it.getLineEnd(it.lineCount - 1, visibleEnd = true))
        }
        ui.onNodeWithTag("save_loan_button").assertIsDisplayed()
        ui.onNode(hasText("Abbrechen") and hasAnyAncestor(isDialog())).assertIsDisplayed()
    }
    private fun screenshot(state: String) = capture("p8-$state-font-$fontScale", ui.onAllNodes(isDialog()).onLast())
}
@RunWith(AndroidJUnit4::class)
class Phase8ALoan10EmulatorTest : Phase8ALoanValidationMatrix() { override val fontScale = 1.0f }
@RunWith(AndroidJUnit4::class)
class Phase8ALoan13EmulatorTest : Phase8ALoanValidationMatrix() { override val fontScale = 1.3f }
@RunWith(AndroidJUnit4::class)
class Phase8ALoan15EmulatorTest : Phase8ALoanValidationMatrix() { override val fontScale = 1.5f }
