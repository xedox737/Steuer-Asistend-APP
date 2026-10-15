package com.example.ui

import androidx.compose.ui.test.junit4.ComposeTestRule

/** Room/ViewModel callbacks use Android's main looper, beyond Compose's frame clock. */
internal fun ComposeTestRule.waitUntilAndroidState(timeoutMillis: Long, condition: () -> Boolean) {
    waitUntil(timeoutMillis) { runOnIdle(condition) }
}
