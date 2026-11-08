package com.example.e2e

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.ManagedDocument
import com.example.data.Receipt
import com.example.data.StableDocumentIdentity
import com.example.ui.OfflineBackupCard
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android content streams and picker callbacks; cleanup is guarded by EmulatorTestSupport. */
@RunWith(AndroidJUnit4::class)
class OfflineBackupPickerEmulatorTest : EmulatorTestSupport() {
    private fun showCard(uri: Uri) {
        val model = vm
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(uri))
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        scenario.onActivity { activity ->
            activity.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    MyApplicationTheme(darkTheme = false) { OfflineBackupCard(model) }
                }
            }
        }
        ui.waitForIdle()
    }

    private fun awaitStatus(text: String) {
        ui.waitUntil(10_000) {
            ui.onAllNodes(hasTestTag("offline_backup_status") and hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun emptySelectedFileShowsGermanValidationAndKeepsExistingProperty() {
        seedProperty("offline-empty-house", "Vorhandenes Haus")
        val before = runBlocking(Dispatchers.IO) { db.propertyDao().getAllProperties() }
        val file = File(context.cacheDir, "e2e-offline-empty.zip").apply { writeBytes(byteArrayOf()) }
        showCard(FileProvider.getUriForFile(context, "${context.packageName}.provider", file))
        ui.onNodeWithTag("offline_backup_import").performClick()
        awaitStatus("vollständige ImmoPilot-Vollsicherung")
        ui.onAllNodes(hasText("offline-import-", substring = true)).assertCountEquals(0)
        ui.onAllNodes(hasText("snapshot.json", substring = true)).assertCountEquals(0)
        ui.onNodeWithText("Wiederherstellen").assertDoesNotExist()
        assertEquals(before, runBlocking(Dispatchers.IO) { db.propertyDao().getAllProperties() })
    }

    @Test fun savedArchiveWithOriginalCanBeReadThroughContentUriAndPreviewCancelledWithoutChanges() {
        val property = seedProperty("offline-uri-house", "Lokales Haus")
        val originalBytes = "%PDF-1.4\nSynthetic offline picker original\n%%EOF".toByteArray()
        val original = File(context.filesDir, "managed_documents/e2e-offline-picker.pdf").apply {
            parentFile!!.mkdirs(); writeBytes(originalBytes)
        }
        val receipt = Receipt(id = 41, internalId = "e2e-offline-receipt", propertyId = property.propertyId,
            aussteller = "Lokaler Beleg", datum = "2026-11-08", uhrzeit = "", bruttobetrag = 20.0,
            hauptkategorie = "Kosten", unterkategorie = "Reparatur", kontoNr = "4800", beschreibung = "Behalten",
            imageUrl = original.absolutePath)
        val document = ManagedDocument(documentId = "e2e-offline-document", propertyId = property.propertyId,
            receiptInternalId = receipt.internalId, title = "Lokaler Beleg", documentDate = receipt.datum,
            localUri = original.absolutePath, sha256 = StableDocumentIdentity.sha256(originalBytes),
            originalFilename = original.name, storedFilename = original.name, mimeType = "application/pdf",
            fileSizeBytes = originalBytes.size.toLong())
        runBlocking(Dispatchers.IO) {
            db.receiptDao().insertReceipt(receipt)
            db.managedDocumentDao().upsert(document)
        }
        val beforeProperties = runBlocking(Dispatchers.IO) { db.propertyDao().getAllProperties() }
        val file = File(context.cacheDir, "e2e-offline-picker.zip").apply { writeBytes(byteArrayOf()) }
        showCard(FileProvider.getUriForFile(context, "${context.packageName}.provider", file))
        ui.onNodeWithTag("offline_backup_export").performClick()
        awaitStatus("Vollständige lokale Sicherung gespeichert")
        ZipFile(file).use { zip ->
            assertNotNull(zip.getEntry("snapshot.json"))
            assertNotNull(zip.getEntry("snapshot.sha256"))
            assertArrayEquals(originalBytes, zip.getInputStream(zip.getEntry("files/0")).use { it.readBytes() })
        }
        ui.onNodeWithTag("offline_backup_import").performClick()
        ui.waitUntil(10_000) { ui.onAllNodesWithText("Lokale Sicherung wiederherstellen?").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("${beforeProperties.size} Immobilien · 1 Belege · 1 Originaldateien", substring = true).assertExists()
        ui.onNodeWithText("Abbrechen").performClick()
        ui.onNodeWithText("Lokale Sicherung wiederherstellen?").assertDoesNotExist()
        runBlocking(Dispatchers.IO) {
            assertEquals(beforeProperties, db.propertyDao().getAllProperties())
            assertEquals(listOf(receipt), db.receiptDao().getAllReceiptsList())
            assertEquals(document, db.managedDocumentDao().getById(document.documentId))
        }
        assertArrayEquals(originalBytes, original.readBytes())
    }
}
