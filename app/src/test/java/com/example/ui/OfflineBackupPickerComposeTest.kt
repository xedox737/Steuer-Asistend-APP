package com.example.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.PersistentPreferenceInventory
import com.example.data.PropertyMetadata
import com.example.data.Receipt
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Exercise the real picker callbacks, content streams and backup service together. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-420dpi")
class OfflineBackupPickerComposeTest {
    @get:Rule(order = 0) val applicationIsolation = IsolatedAndroidApplicationRule()
    @get:Rule(order = 1) val databaseIsolation = IsolatedAppDatabaseRule()
    @get:Rule(order = 2) val ui = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: AppDatabase
    private lateinit var model: ReceiptViewModel
    private lateinit var selectedUri: Uri
    private var pickerLaunches = 0
    private var pickerContract: String? = null
    private var cancelPicker = false
    private val property = PropertyMetadata(id = 7, propertyId = "picker-backup-house", name = "Lokales Haus")
    private val receipt = Receipt(id = 41, internalId = "picker-backup-receipt", propertyId = property.propertyId,
        aussteller = "Lokaler Beleg", datum = "2026-11-08", uhrzeit = "", bruttobetrag = 20.0,
        hauptkategorie = "Kosten", unterkategorie = "Reparatur", kontoNr = "4800", beschreibung = "Behalten")
    private val picker = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            pickerLaunches++
            pickerContract = contract.javaClass.name
            dispatchResult(requestCode, if (cancelPicker) Activity.RESULT_CANCELED else Activity.RESULT_OK,
                Intent().apply { if (!cancelPicker) data = selectedUri })
        }
    }

    @Before fun setUp() {
        val app = ui.activity.application
        PersistentPreferenceInventory.stores.forEach { app.getSharedPreferences(it.name, 0).edit().clear().commit() }
        app.getSharedPreferences("google_drive_prefs", 0).edit().putBoolean("user_disconnected", true).commit()
        @Suppress("UNCHECKED_CAST")
        val providerCache = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.get(null) as MutableMap<Any?, Any?>
        providerCache.clear()
        model = ui.runOnIdle { ViewModelProvider(ui.activity)[ReceiptViewModel::class.java] }
        database = AppDatabase.getDatabase(app, kotlinx.coroutines.CoroutineScope(Dispatchers.IO))
        runBlocking(Dispatchers.IO) {
            database.propertyDao().insertPropertyMetadata(property)
            database.receiptDao().insertReceipt(receipt)
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = picker }
        ui.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { OfflineBackupCard(model) } }
            }
        }
        ui.waitUntil(10_000) { model.offlineBackupControllerForUi().state.value.loaded }
        ui.onNodeWithTag("offline_backup_legacy_options").performScrollTo().performClick()
    }

    private fun select(file: File) {
        selectedUri = FileProvider.getUriForFile(ui.activity, "${ui.activity.packageName}.provider", file)
    }

    private fun waitForStatus(text: String) {
        ui.waitUntil(10_000) {
            ui.onAllNodes(hasTestTag("offline_backup_status") and hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertLocalDataUnchanged() = runBlocking(Dispatchers.IO) {
        assertEquals(listOf(property), database.propertyDao().getAllProperties())
        assertEquals(listOf(receipt), database.receiptDao().getAllReceiptsList())
    }

    @Test fun encryptedDefaultUsesFolderPickerAndCancellationKeepsConfiguration() {
        ui.onNodeWithTag("offline_backup_legacy_options").performScrollTo().performClick()
        ui.onNodeWithTag("offline_backup_legacy_export").assertDoesNotExist()
        ui.onNodeWithTag("offline_backup_export").assertIsNotEnabled()
        ui.onNodeWithTag("offline_backup_include_credentials").assertIsOff()
        cancelPicker = true
        ui.onNodeWithTag("offline_backup_folder").performScrollTo().performClick()
        ui.runOnIdle {
            assertEquals(1, pickerLaunches)
            assertEquals(ActivityResultContracts.OpenDocumentTree::class.java.name, pickerContract)
            assertNull(com.example.data.OfflineBackupSettings(ui.activity).load().targetUri)
            assertFalse(model.offlineBackupControllerForUi().state.value.busy)
        }
    }

    @Test fun emptyFileReturnedByDocumentPickerShowsReadableErrorAndCannotStartRestore() {
        select(File(ui.activity.cacheDir, "empty-selected-backup.zip").apply { writeBytes(byteArrayOf()) })
        ui.onNodeWithTag("offline_backup_import").performScrollTo().performClick()
        waitForStatus("vollständige ImmoPilot-Vollsicherung")
        ui.onNodeWithTag("offline_backup_status").assertTextEquals("Die Datei enthält keine vollständige ImmoPilot-Vollsicherung. Bitte wähle die ZIP-Datei aus „Vollsicherung speichern“.")
        ui.onAllNodes(hasText("offline-import-", substring = true)).assertCountEquals(0)
        ui.onAllNodes(hasText("snapshot.json", substring = true)).assertCountEquals(0)
        ui.onNodeWithText("Wiederherstellen").assertDoesNotExist()
        assertLocalDataUnchanged()
    }

    @Test fun inaccessibleDocumentPickerUriDoesNotExposeRawProviderOrFileSystemErrors() {
        selectedUri = Uri.parse("content://unavailable-backup-provider/private/snapshot.json")
        ui.onNodeWithTag("offline_backup_import").performScrollTo().performClick()
        waitForStatus("Die Sicherung konnte nicht gelesen werden")
        ui.onAllNodes(hasText("content://", substring = true)).assertCountEquals(0)
        ui.onAllNodes(hasText("snapshot.json", substring = true)).assertCountEquals(0)
        ui.onNodeWithText("Wiederherstellen").assertDoesNotExist()
        assertLocalDataUnchanged()
    }

    @Test fun backupSavedThroughContentUriCanBeCheckedAndRestoredWithoutDuplicates() {
        val file = File(ui.activity.cacheDir, "selected-backup.zip").apply { writeBytes(byteArrayOf()) }
        select(file)
        ui.onNodeWithTag("offline_backup_legacy_export").performScrollTo().performClick()
        waitForStatus("Vollständige lokale Sicherung gespeichert")
        ZipFile(file).use { zip ->
            assertNotNull(zip.getEntry("snapshot.json"))
            assertNotNull(zip.getEntry("snapshot.sha256"))
        }
        ui.onNodeWithTag("offline_backup_import").performScrollTo().performClick()
        ui.waitUntil(10_000) {
            ui.onAllNodesWithText("Lokale Sicherung wiederherstellen?").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithText("1 Immobilien · 1 Belege · 0 Originaldateien", substring = true).assertExists()
        ui.onNodeWithText("Wiederherstellen").performClick()
        waitForStatus("Lokale Sicherung wiederhergestellt")
        assertLocalDataUnchanged()
    }

    @Test fun failedPreparationLeavesThePickerAndExistingBackupUntouchedAndErrorSurvivesImport() {
        val missing = File(ui.activity.filesDir, "managed_documents/missing-backup-original.pdf")
        missing.delete()
        val withMissingOriginal = receipt.copy(imageUrl = missing.absolutePath)
        runBlocking(Dispatchers.IO) { database.receiptDao().insertReceipt(withMissingOriginal) }
        val previous = File(ui.activity.cacheDir, "keep-existing-backup.zip").apply { writeText("previous backup") }
        select(previous)
        ui.onNodeWithTag("offline_backup_legacy_export").performScrollTo().performClick()
        waitForStatus("fehlt")
        assertEquals("A failed preparation must not create a target document", 0, pickerLaunches)
        assertEquals("previous backup", previous.readText())
        ui.onNodeWithText("picker-backup-receipt", substring = true).assertExists()

        select(File(ui.activity.cacheDir, "empty-import-after-export-error.zip").apply { writeBytes(byteArrayOf()) })
        ui.onNodeWithTag("offline_backup_import").performScrollTo().performClick()
        waitForStatus("vollständige ImmoPilot-Vollsicherung")
        ui.onNodeWithText("picker-backup-receipt", substring = true).assertExists()
        ui.onNodeWithText("Wiederherstellen").assertDoesNotExist()
        runBlocking(Dispatchers.IO) {
            assertEquals(withMissingOriginal, database.receiptDao().getReceiptById(receipt.id))
        }
    }

    @Test fun cancellingExportPickerCleansThePrivateArchiveAndKeepsExistingFile() {
        val previous = File(ui.activity.cacheDir, "keep-on-picker-cancel.zip").apply { writeText("previous backup") }
        select(previous)
        cancelPicker = true
        val staging = File(ui.activity.filesDir, "offline-backup-staging")
        ui.onNodeWithTag("offline_backup_legacy_export").performScrollTo().performClick()
        ui.waitUntil(10_000) {
            pickerLaunches == 1 && staging.isDirectory && staging.listFiles()?.isEmpty() == true
        }
        ui.onNodeWithTag("offline_backup_legacy_export").assertIsEnabled()
        ui.onAllNodes(hasText("Vollständige lokale Sicherung gespeichert", substring = true)).assertCountEquals(0)
        assertEquals("previous backup", previous.readText())
        assertLocalDataUnchanged()
    }
}
