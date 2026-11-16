package com.example.ui

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.data.OfflineRestoreJournal
import java.io.File
import java.util.UUID
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class OfflineRestoreStartupComposeTest {
    @get:Rule(order = 0) val isolation = IsolatedAndroidApplicationRule()
    @get:Rule(order = 1) val databaseIsolation = IsolatedAppDatabaseRule()
    @get:Rule(order = 2) val ui = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val journalFile get() = File(context.noBackupFilesDir, "offline-backup-state/restore-journal.json")
    @After fun cleanup() {
        listOf(journalFile, File(journalFile.path + ".bak"), File(journalFile.path + ".new")).forEach { it.delete() }
    }
    @Test fun noJournalStartsNormalBusinessUi() {
        ActivityScenario.launch(MainActivity::class.java).use {
            ui.waitUntil(10_000) { ui.onAllNodesWithTag("bottom_navigation").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("offline_restore_startup_blocked").assertDoesNotExist()
            ui.onNodeWithTag("bottom_navigation").assertExists()
        }
    }
    @Test fun pendingRestoreBlocksBusinessUiBeforeViewModelCreation() {
        val id = { UUID.randomUUID().toString() }
        val journal = OfflineRestoreJournal(context)
        val rollbackId = id()
        val bundle = File(context.noBackupFilesDir, "offline-backup-state/rollback-$rollbackId.bundle.json")
        bundle.parentFile!!.mkdirs(); bundle.writeText("synthetic rollback manifest")
        val hash = MessageDigest.getInstance("SHA-256").digest(bundle.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
        val entry = journal.begin(id(), id(), rollbackId, bundle.length(), hash)
        ActivityScenario.launch(MainActivity::class.java).use {
            ui.onNodeWithTag("offline_restore_startup_blocked").assertExists()
            ui.onNodeWithTag("bottom_navigation").assertDoesNotExist()
            assertEquals(entry, journal.load())
        }
        bundle.delete()
    }
    @Test fun corruptJournalAlsoBlocksAndIsNotDiscarded() {
        journalFile.parentFile!!.mkdirs(); journalFile.writeText("{broken")
        ActivityScenario.launch(MainActivity::class.java).use {
            ui.onNodeWithTag("offline_restore_startup_blocked").assertExists()
            ui.onNodeWithTag("bottom_navigation").assertDoesNotExist()
            assertEquals("{broken", journalFile.readText())
        }
    }
}
