package com.example.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.MainActivity
import com.example.data.ExportAuditRun
import com.example.ui.theme.MyApplicationTheme
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
class Phase8BAuditComposeTest {
    @get:Rule(order = 0) val isolation = IsolatedAndroidApplicationRule()
    @get:Rule(order = 1) val ui = createAndroidComposeRule<MainActivity>()
    private fun render(count: Int, scale: Float, full: Boolean = false) {
        val runs = (count downTo 1).map { ExportAuditRun("run-$it", timestamp = 1700000000000L + it * 1000L,
            zipFileName = "EXTF-$it.csv", kanzleiprofilNameVersion = "Testprofil v1", bookingCount = it) }
        ui.runOnUiThread { ui.activity.setContent { MyApplicationTheme {
            CompositionLocalProvider(LocalDensity provides Density(ui.activity.resources.displayMetrics.density, scale)) {
                var showAll by remember { mutableStateOf(full) }
                Box(Modifier.fillMaxSize().padding(12.dp)) {
                    if (showAll) DatevAuditHistory(runs)
                    else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { DatevAuditPreview(runs) { showAll = true } }
                }
            }
        } } }
    }
    @Test fun emptyOneAndFiveRunsPreserveCompactStateWithoutUnnecessaryAllButton() {
        listOf(0, 1, 5).forEach { count ->
            render(count, 1f)
            ui.onNodeWithTag("datev_audit_show_all").assertDoesNotExist()
            if (count == 0) ui.onNodeWithText("Bisher keine protokollierten Exportläufe vorhanden.").assertIsDisplayed()
            else { ui.onNodeWithTag("datev_audit_run-$count").assertIsDisplayed(); ui.onNodeWithTag("datev_audit_run-1").assertExists() }
        }
    }
    @Test fun sixRunsExposeOlderHistoryAtFontScale10() = history(6, 1f)
    @Test fun twentyRunsAreScrollableAtFontScale13() = history(20, 1.3f)
    @Test fun hundredRunsAreLazyAndScrollableAtFontScale15() = history(100, 1.5f)
    private fun history(count: Int, scale: Float) {
        render(count, scale)
        ui.onNodeWithTag("datev_audit_run-${count-5}").assertDoesNotExist()
        ui.onNodeWithTag("datev_audit_show_all").assertIsDisplayed().performClick()
        val list = ui.onNodeWithTag("datev_audit_history").assertIsDisplayed()
        list.performScrollToNode(hasTestTag("datev_audit_run-1"))
        ui.onNodeWithTag("datev_audit_run-1").assertIsDisplayed()
        ui.onNodeWithTag("datev_audit_status_run-1").assertTextEquals("Erfolgreich")
        list.performScrollToIndex(1)
        ui.onNodeWithTag("datev_audit_run-$count").assertIsDisplayed()
        ui.onNodeWithText("SUCCESS").assertDoesNotExist()
        assertTrue("LazyColumn must not compose all 100 entries", ui.onAllNodes(hasTestTag("datev_audit_run-1")).fetchSemanticsNodes().isEmpty())
    }
}
