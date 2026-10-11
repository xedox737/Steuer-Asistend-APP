package com.example.e2e

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.AppScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Calendar input through the production receipt form, persisted Room data and a fresh Activity. */
abstract class AppDateCalendarMatrix : EmulatorTestSupport() {
    @Test fun receiptCalendarManualValidationCancelBackAndPersistence() {
        seedProperty("date-object", "Datumsprüfung")
        val model = vm
        clickTab(AppScreen.ADD_RECEIPT)
        ui.onNodeWithTag("edit_aussteller").performScrollTo().performTextReplacement("Kalender Handwerker")
        ui.onNodeWithTag("edit_datum").performScrollTo().performTextReplacement("10.10.2026")
        Espresso.closeSoftKeyboard()
        ui.onNodeWithTag("edit_datum").performClick()
        ui.onNodeWithTag("app_date_picker").assertIsDisplayed()
        ui.onNode(calendarDay("11"), useUnmergedTree = true).performClick()
        capture("date-calendar-font-$fontScale")
        ui.onNodeWithTag("app_date_apply").performClick()
        ui.onNodeWithTag("edit_datum").assertTextContains("11.10.2026")
        ui.onNodeWithTag("edit_aussteller").assertTextContains("Kalender Handwerker")
        ui.onNodeWithTag("edit_datum").performClick()
        ui.onNode(calendarDay("12"), useUnmergedTree = true).performClick()
        ui.onNodeWithTag("app_date_cancel").performClick()
        ui.onNodeWithTag("edit_datum").assertTextContains("11.10.2026")
        ui.onNodeWithTag("edit_datum").performClick()
        systemBack()
        ui.onNodeWithTag("app_date_picker").assertDoesNotExist()
        ui.onNodeWithTag("edit_datum").assertTextContains("11.10.2026")
        ui.onNodeWithTag("edit_datum").performTextReplacement("31.02.2026")
        Espresso.closeSoftKeyboard()
        ui.onNodeWithText("Bitte ein gültiges Datum im Format TT.MM.JJJJ eingeben.").assertExists()
        ui.onNodeWithTag("edit_betrag").performScrollTo().performTextReplacement("119,00")
        Espresso.closeSoftKeyboard()
        ui.onNodeWithTag("save_extracted_receipt_button").performScrollTo().performClick()
        assertTrue(model.receipts.value.isEmpty())
        ui.onNodeWithTag("edit_datum").performScrollTo().performTextReplacement("11.10.2026")
        Espresso.closeSoftKeyboard()
        capture("date-receipt-form-font-$fontScale")
        ui.onNodeWithTag("save_extracted_receipt_button").performScrollTo().performClick()
        ui.waitUntil(10_000) { model.receipts.value.any { it.aussteller == "Kalender Handwerker" } }
        val receipt = model.receipts.value.single { it.aussteller == "Kalender Handwerker" }
        assertEquals("2026-10-11", receipt.datum)
        assertEquals("2026-10-11", runBlocking(Dispatchers.IO) { db.receiptDao().getReceiptById(receipt.id)!!.datum })
        scenario.recreate()
        clickTab(AppScreen.RECEIPTS_LIST)
        ui.onNodeWithTag("receipts_list").performScrollToNode(hasTestTag("receipt_item_${receipt.id}"))
        ui.onNodeWithTag("receipt_item_${receipt.id}").performClick()
        ui.onNodeWithText("11.10.2026").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("2026-10-11").assertDoesNotExist()
        capture("date-receipt-reopened-font-$fontScale")
    }
}
@RunWith(AndroidJUnit4::class) class AppDateCalendarFont100Test : AppDateCalendarMatrix()
@RunWith(AndroidJUnit4::class) class AppDateCalendarFont130Test : AppDateCalendarMatrix() { override val fontScale = 1.3f }
@RunWith(AndroidJUnit4::class) class AppDateCalendarFont150Test : AppDateCalendarMatrix() { override val fontScale = 1.5f }

private fun calendarDay(day: String) = SemanticsMatcher("Kalendertag $day") { node ->
    (node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() +
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }).any {
        Regex("(^|\\D)$day(\\D|$)").containsMatchIn(it)
    } && node.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.OnClick) != null
}
