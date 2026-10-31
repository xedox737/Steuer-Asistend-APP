package com.example.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.DriveInventoryCheck
import com.example.data.DriveRestorePreviewResult
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DriveRestorePreviewComposeTest {
    @get:Rule val ui = createComposeRule()

    @Test fun unverifiedInventoryNeverEnablesRestoreEvenIfPropertyFlagClaimsRestorable() {
        var confirmed = false
        val preview = DriveRestorePreviewResult(success = false, inventory = DriveInventoryCheck(
            hasDriveFolder = true, propertyFound = true, propertyName = "Synthetic property", unitsCount = 7,
            canRestore = true, issues = listOf("receipt-index.json unlesbar: HTTP404")))
        ui.setContent { MaterialTheme { DriveRestoreConfirmationDialog(preview, false, { confirmed = true }, {}) } }
        ui.onNodeWithTag("confirm_restore_button").assertIsNotEnabled()
        ui.onNodeWithTag("confirm_restore_button").performTouchInput { click() }
        ui.runOnIdle { assertFalse(confirmed) }
        ui.onNodeWithText("0 Einträge").assertDoesNotExist()
        ui.onAllNodesWithText("Nicht prüfbar").assertCountEquals(3)
    }

    @Test fun absentPreviewCannotAuthorizeRestoreOrClaimEmptyReceiptInventory() {
        ui.setContent { MaterialTheme { DriveRestoreConfirmationDialog(null, false, {}, {}) } }
        ui.onNodeWithTag("confirm_restore_button").assertIsNotEnabled()
        ui.onNodeWithText("0 Einträge").assertDoesNotExist()
    }

    @Test fun verifiedEmptyReceiptIndexRemainsVisibleAndPropertyRestoreEnabled() {
        val preview = DriveRestorePreviewResult(success = true, inventory = DriveInventoryCheck(
            hasDriveFolder = true, propertyFound = true, canRestore = true))
        ui.setContent { MaterialTheme { DriveRestoreConfirmationDialog(preview, false, {}, {}) } }
        ui.onNodeWithTag("confirm_restore_button").assertIsEnabled()
        ui.onNodeWithText("0 Einträge").assertExists()
    }
}
