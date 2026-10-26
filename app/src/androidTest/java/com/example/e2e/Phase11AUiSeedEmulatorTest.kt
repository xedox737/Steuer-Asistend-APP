package com.example.e2e

import androidx.compose.ui.test.*
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import com.example.qa.Phase11APortfolioSeeder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.File

/** Existing environment rule enforces isolated package + disposable emulator before cleanup. */
@RunWith(AndroidJUnit4::class)
class Phase11AUiSeedEmulatorTest : EmulatorTestSupport() {
    @Before fun freshSyntheticSeedWorkspace() {
        // Existing E2Es intentionally reset their synthetic domain fixture. Never used on phones.
        context.getSharedPreferences("phase11a_seed_journal", 0).edit().clear().commit()
        listOf("managed_documents", "phase11a-originals", "property-images").forEach {
            File(context.filesDir, it).deleteRecursively()
        }
    }

    private fun openTools() {
        clickMore("App-Einstellungen")
        ui.onNodeWithTag("settings_overview").performScrollToNode(hasTestTag("phase11a_tools_entry"))
        ui.onNodeWithTag("phase11a_tools_entry").performClick()
        ui.onNodeWithTag("phase11a_tools").assertIsDisplayed()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Testinstallation wird geprüft …").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun realSettingsConfirmationLoadsFullPortfolioAndBlocksRepeat() {
        openTools()
        ui.onNodeWithTag("phase11a_load_17").assertIsEnabled().performClick()
        ui.onNodeWithText("Testportfolio laden?").assertIsDisplayed()
        ui.onNodeWithText("Abbrechen").performClick()
        assertTrue(runBlocking(Dispatchers.IO) { db.propertyDao().getAllProperties().isEmpty() })
        ui.onNodeWithTag("phase11a_load_17").performClick()
        ui.onNodeWithTag("phase11a_confirm_seed").performClick()
        ui.waitUntil(30_000) { ui.onAllNodesWithTag("phase11a_seed_success").fetchSemanticsNodes().isNotEmpty() }
        capture("phase11a-ui-seed-success")
        runBlocking(Dispatchers.IO) {
            assertEquals(5, db.propertyDao().getAllProperties().size)
            assertEquals(70, db.receiptDao().getAllReceiptsIncludingDeletedList().size)
            assertEquals(70, db.managedDocumentDao().getAll().size)
            assertEquals(120, db.bankDao().getAllTransactions().size)
            assertEquals(10, db.logbookDao().getAllTrips().size)
            val originals = db.managedDocumentDao().getAll().filter { it.fileSizeBytes > 0 }
            assertEquals(60, originals.size)
            originals.forEach { assertEquals(it.sha256, StableDocumentIdentity.sha256(File(it.localUri).readBytes())) }
        }
        ui.onNodeWithTag("phase11a_load_17").assertIsNotEnabled()
        ui.onNodeWithText("Zurück zu Einstellungen").performScrollTo().performClick()
        ui.onNodeWithTag("phase11a_tools_entry").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Testinstallation wird geprüft …").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithTag("phase11a_load_17").assertIsNotEnabled()
        ui.onNodeWithText(Phase11APortfolioSeeder.EMPTY_REQUIRED).assertIsDisplayed()
        val savedRunId = context.getSharedPreferences("phase11a_seed_journal", 0).getString("runId", null)
        ui.onNodeWithText("Run-ID: $savedRunId").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Zurück zu Einstellungen").performScrollTo().performClick()
        scenario.recreate()
        ui.onNodeWithTag("settings_overview").assertIsDisplayed()
        ui.waitUntil(10_000) { vm.properties.value.size == 5 }
        assertEquals(70, runBlocking(Dispatchers.IO) { db.receiptDao().getAllReceiptsIncludingDeletedList().size })
        // Native snapshots survive after ActivityScenario closes; the host checks a true process stop.
        val meta = org.json.JSONObject().put("runId", context.getSharedPreferences("phase11a_seed_journal", 0).getString("runId", null))
            .put("label", "ui-seed").put("source", "DEVICE_INSTRUMENTATION")
            .put("commit", requireNotNull(InstrumentationRegistry.getArguments().getString("phase11aCommit")))
            .put("capturedAt", java.time.Instant.now().toString()).put("device", android.os.Build.MODEL)
            .put("android", android.os.Build.VERSION.RELEASE).put("appVersion", context.packageManager.getPackageInfo(context.packageName, 0).versionName)
            .put("applicationId", context.packageName).put("uid", android.os.Process.myUid())
        val snapshot = runBlocking(Dispatchers.IO) { Phase11ASnapshot.capture(context, db, meta) }
        saveArtifact("phase11a-ui-seed-snapshot.json", snapshot.toString(2).toByteArray())
        scenario.moveToState(Lifecycle.State.CREATED)
    }

    @Test fun occupiedAppShowsGermanRefusalAndPreservesExistingIds() {
        val property = seedProperty()
        openTools()
        ui.onNodeWithTag("phase11a_load_17").assertIsNotEnabled()
        ui.onNodeWithText(Phase11APortfolioSeeder.EMPTY_REQUIRED).assertIsDisplayed()
        assertEquals(listOf(property.propertyId), runBlocking(Dispatchers.IO) { db.propertyDao().getAllProperties().map { it.propertyId } })
        assertTrue(runBlocking(Dispatchers.IO) { db.receiptDao().getAllReceiptsIncludingDeletedList().isEmpty() })
        systemBack()
        ui.onNodeWithTag("settings_overview").assertIsDisplayed()
    }
}
