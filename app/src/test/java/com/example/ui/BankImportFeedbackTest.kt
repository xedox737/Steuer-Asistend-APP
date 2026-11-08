package com.example.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BankImportFeedbackTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun selectedStatementShowsImportProgress() {
        composeRule.setContent {
            MaterialTheme {
                BankImportFeedback("Kontoauszug wird eingelesen …", onDetails = {})
            }
        }
        composeRule.onNodeWithText("Kontoauszug wird eingelesen …").fetchSemanticsNode()
    }

    @Test fun failedStatementShowsErrorAndOpensDetails() {
        var detailsOpened = 0
        composeRule.setContent {
            MaterialTheme {
                BankImportFeedback("Import fehlgeschlagen: Datei konnte nicht gelesen werden.", onDetails = { detailsOpened++ })
            }
        }
        composeRule.onNodeWithText("Import fehlgeschlagen: Datei konnte nicht gelesen werden.").fetchSemanticsNode()
        composeRule.onNodeWithText("Details").performClick()
        composeRule.runOnIdle { assertEquals(1, detailsOpened) }
    }

    @Test fun completedStatementShowsCounts() {
        composeRule.setContent {
            MaterialTheme {
                BankImportFeedback("CSV: 22 gültige Buchungen erkannt • 22 neu • 0 Dubletten.", onDetails = {})
            }
        }
        composeRule.onNodeWithText("CSV: 22 gültige Buchungen erkannt • 22 neu • 0 Dubletten.").fetchSemanticsNode()
    }
}
