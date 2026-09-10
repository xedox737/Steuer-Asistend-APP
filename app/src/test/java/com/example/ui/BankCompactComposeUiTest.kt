package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.BankReconciliationStatus
import com.example.data.BankTransaction
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BankCompactComposeUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun transaction() = BankTransaction(
        transactionId = "tx-ui-synthetic",
        accountId = "account-ui-synthetic",
        bookingDate = "2026-09-10",
        amount = 1500.0,
        counterparty = "Synthetic Tenant",
        purpose = "Miete September",
        reconciliationStatus = BankReconciliationStatus.PARTIAL
    )

    @Test fun compactRowShowsNameAmountStatusDateAndOpensOnTap() {
        var taps = 0
        val tx = transaction()
        composeRule.setContent {
            MaterialTheme {
                BankCompactTransactionRow(transaction = tx, onClick = { taps++ })
            }
        }

        composeRule.onNodeWithText("Synthetic Tenant").fetchSemanticsNode()
        composeRule.onNodeWithText("Miete September").fetchSemanticsNode()
        composeRule.onNodeWithText(NumberFormatter.format(tx.amount)).fetchSemanticsNode()
        composeRule.onNodeWithText("Teilweise").fetchSemanticsNode()
        composeRule.onNodeWithText("10.09.2026").fetchSemanticsNode()
        composeRule.onNodeWithText("Synthetic Tenant").performClick()
        composeRule.runOnIdle { assertEquals(1, taps) }
    }

    @Test fun quickActionHasAccessibleLabelAndFiresOneActionForOneTap() {
        var actions = 0
        composeRule.setContent {
            MaterialTheme {
                BankQuickAction("Beleg\nsuchen", Icons.Default.Search, onClick = { actions++ })
            }
        }

        composeRule.onNodeWithText("Beleg\nsuchen").fetchSemanticsNode()
        composeRule.onNodeWithText("Beleg\nsuchen").performClick()
        composeRule.runOnIdle { assertEquals(1, actions) }
    }
}
