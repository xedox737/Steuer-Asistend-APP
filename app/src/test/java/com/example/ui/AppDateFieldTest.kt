package com.example.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.util.DateFormatUtils
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class AppDateFieldTest {
    @get:Rule val ui = createComposeRule()
    @Test fun isoDisplayManualEntryInvalidDraftAndOptionalClear() {
        var saved = "2026-10-11"
        var valid = true
        ui.setContent { var value by remember { mutableStateOf(saved) }; MaterialTheme {
            AppDateField(value, { saved = it; value = it }, "Datum", testTag = "date", onValidityChange = { valid = it })
        } }
        ui.onNodeWithTag("date").assertTextContains("11.10.2026")
        ui.onNodeWithTag("date").performTextReplacement("12.12.2026")
        ui.runOnIdle { assertEquals("2026-12-12", saved); assertTrue(valid) }
        ui.onNodeWithTag("date").performTextReplacement("31.02.2026")
        ui.onNodeWithText(DateFormatUtils.INPUT_ERROR).assertExists()
        ui.onNodeWithTag("date").assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, DateFormatUtils.INPUT_ERROR))
        ui.runOnIdle { assertEquals("2026-12-12", saved); assertFalse(valid) }
        ui.onNodeWithTag("date").performTextReplacement("2026-10-11")
        ui.runOnIdle { assertEquals("2026-12-12", saved); assertFalse(valid) }
        ui.onNodeWithTag("date").performTextClearance()
        ui.runOnIdle { assertEquals("", saved); assertTrue(valid) }
    }
    @Test fun fieldAndIconOpenPickerCancelDoesNotEmitAndApplyStoresIso() {
        var saved = "2026-10-11"
        var emissions = 0
        ui.setContent { var value by remember { mutableStateOf(saved) }; MaterialTheme {
            AppDateField(value, { saved = it; value = it; emissions++ }, "Datum", testTag = "date")
        } }
        ui.onNodeWithTag("date").performClick()
        ui.onNodeWithTag("app_date_picker").assertExists()
        ui.onNodeWithTag("app_date_cancel").performClick()
        ui.runOnIdle { assertEquals(0, emissions) }
        ui.onNodeWithTag("date_calendar").performClick()
        ui.onNodeWithTag("app_date_picker").assertExists()
        ui.onAllNodesWithText("Oktober 2026", substring = true, useUnmergedTree = true).onFirst().assertExists()
        ui.onNode(calendarDay("12"), useUnmergedTree = true).performClick()
        ui.onNodeWithTag("app_date_apply").performClick()
        ui.runOnIdle { assertEquals("2026-10-12", saved); assertEquals(1, emissions) }
        ui.onNodeWithTag("date").assertTextContains("12.10.2026")
    }
    @Test fun requiredAndBoundsRejectInvalidValues() {
        var valid = true
        var emissions = 0
        ui.setContent { MaterialTheme {
            AppDateField("", { emissions++ }, "Datum", required = true, testTag = "date",
                minDate = LocalDate.of(2026, 1, 1), maxDate = LocalDate.of(2026, 12, 31), onValidityChange = { valid = it })
        } }
        ui.runOnIdle { assertFalse(valid) }
        ui.onNodeWithTag("date").performTextReplacement("31.12.2025")
        ui.runOnIdle { assertFalse(valid); assertEquals(0, emissions) }
        ui.onNodeWithTag("date").performTextReplacement("01.01.2026")
        ui.runOnIdle { assertTrue(valid); assertEquals(1, emissions) }
    }
    @Test fun disabledAndReadOnlyFieldsKeepTheirCanonicalValues() {
        ui.setContent { MaterialTheme {
            androidx.compose.foundation.layout.Column {
                AppDateField("2026-10-11", { error("Disabled field emitted") }, "Deaktiviert", enabled = false, testTag = "disabled")
                AppDateField("2026-10-11", { error("Read-only field emitted") }, "Nur lesen", readOnly = true, testTag = "readonly")
            }
        } }
        ui.onNodeWithTag("disabled").assertIsNotEnabled().assertTextContains("11.10.2026")
        ui.onNodeWithTag("disabled_calendar").assertIsNotEnabled()
        ui.onNodeWithTag("readonly_calendar").assertIsNotEnabled()
        ui.onNodeWithTag("readonly").performClick()
        ui.onNodeWithTag("app_date_picker").assertDoesNotExist()
    }

}

private fun calendarDay(day: String) = SemanticsMatcher("Kalendertag $day") { node ->
    (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).any {
        Regex("(^|\\D)$day(\\D|$)").containsMatchIn(it)
    } && node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick) != null
}
