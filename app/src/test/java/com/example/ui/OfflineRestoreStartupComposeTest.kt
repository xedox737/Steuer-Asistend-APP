package com.example.ui

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.OfflineBackupArchiveInfo
import com.example.data.OfflineBackupCoordinator
import com.example.data.OfflineBackupManifest
import com.example.data.OfflineRestoreFilePlan
import com.example.data.OfflineRestoreFilePlanStore
import com.example.data.OfflineRestoreJournal
import com.example.data.OfflineRestoreRollbackBundle
import com.example.data.PropertyMetadata
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        context.deleteDatabase("receipt_database")
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
            ui.waitUntil(10_000) { ui.onAllNodesWithTag("offline_restore_retry").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("offline_restore_startup_blocked").assertExists()
            ui.onNodeWithTag("bottom_navigation").assertDoesNotExist()
            ui.onNodeWithTag("offline_restore_retry").assertExists()
            assertEquals("{broken", journalFile.readText())
        }
    }
    @Test fun validPendingRestoreRecoversBeforeBusinessUiStarts() = runBlocking<Unit> {
        context.deleteDatabase("receipt_database")
        context.getSharedPreferences("google_drive_prefs", 0).edit().putBoolean("user_disconnected", true).putBoolean("auto_backup", false).commit()
        val live = Room.databaseBuilder(context, AppDatabase::class.java, "receipt_database")
            .openHelperFactory(OfflineBackupCoordinator.shared.sqliteFactory()).build()
        val journal = OfflineRestoreJournal(context)
        try {
            live.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 9, propertyId = "prior", name = "Before"))
            OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
                val manifest = OfflineBackupManifest.Checked(emptyMap(), emptyMap(), emptyList(), emptyList(), null, emptySet(),
                    OfflineBackupArchiveInfo("2026-11-17T00:00:00Z", 1, "test"))
                val plan = OfflineRestoreFilePlan.prepare(context, UUID.randomUUID().toString(), manifest)
                val snapshot = OfflineRestoreFilePlanStore.capture(context, token, UUID.randomUUID().toString(), plan)
                val bundle = OfflineRestoreRollbackBundle.prepare(context, live, token)
                assertTrue(bundle.ownedFiles.versions.isEmpty())
                val initial = journal.beginWithFilePlan(token, bundle.id, bundle.manifestBytes, bundle.manifestSha256, snapshot)
                val files = journal.advance(initial, OfflineRestoreJournal.Phase.FILES)
                journal.advance(files, OfflineRestoreJournal.Phase.DATABASE)
                token.writing { live.openHelper.writableDatabase.execSQL("UPDATE property_metadata SET name = 'After' WHERE id = 9") }
            }
        } finally { live.close() }
        ActivityScenario.launch(MainActivity::class.java).use {
            ui.waitUntil(30_000) { journal.load() == null && ui.onAllNodesWithTag("bottom_navigation").fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithTag("offline_restore_startup_blocked").assertDoesNotExist()
            ui.onNodeWithTag("bottom_navigation").assertExists(); assertNull(journal.load())
        }
        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, "receipt_database").build()
        try { assertEquals("Before", reopened.propertyDao().getAllProperties().single().name) } finally { reopened.close() }
    }

}
