package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReceiptOriginalPersistenceTest {
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var vm: ReceiptViewModel
    private val store = ViewModelStore()
    private val name = "phase5a-original-${UUID.randomUUID()}"
    private val instance = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    private var previous: AppDatabase? = null
    private val files = mutableListOf<File>()
    private fun newDatabase() = Room.databaseBuilder(app, AppDatabase::class.java, name).allowMainThreadQueries().build()

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        PersistentPreferenceInventory.stores.forEach { app.getSharedPreferences(it.name, 0).edit().clear().commit() }
        app.getSharedPreferences("google_drive_prefs", 0).edit().putBoolean("user_disconnected", true).commit()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        previous = instance.get(null) as AppDatabase?
        db = newDatabase()
        instance.set(null, db)
        vm = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[ReceiptViewModel::class.java]
    }
    @After fun tearDown() {
        store.clear()
        instance.set(null, previous)
        db.close()
        app.deleteDatabase(name)
        files.forEach(File::delete)
        PersistentPreferenceInventory.stores.forEach { app.getSharedPreferences(it.name, 0).edit().clear().commit() }
        Dispatchers.resetMain()
    }
    private fun bytes(extension: String): ByteArray = ByteArrayOutputStream().use { output ->
        if (extension == "pdf") {
            PdfDocument().use { pdf ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(120, 120, 1).create())
                page.canvas.drawColor(Color.WHITE)
                pdf.finishPage(page)
                pdf.writeTo(output)
            }
        } else {
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.RED)
            val format = when (extension) { "png" -> Bitmap.CompressFormat.PNG; "webp" -> Bitmap.CompressFormat.WEBP_LOSSLESS; else -> Bitmap.CompressFormat.JPEG }
            check(bitmap.compress(format, 100, output))
            bitmap.recycle()
        }
        output.toByteArray()
    }
    private fun importOriginal(extension: String): Pair<ManagedDocument, ByteArray> {
        val bytes = bytes(extension)
        val source = File(app.cacheDir, "Ausgewählt-${UUID.randomUUID()}.$extension").apply { writeBytes(bytes) }
        val original = ReceiptOriginalStorage(app).importOriginal(Uri.fromFile(source), source.name)
        files += File(original.localUri)
        source.delete()
        assertEquals(StableDocumentIdentity.sha256(bytes), original.sha256)
        assertArrayEquals(bytes, File(original.localUri).readBytes())
        return original to bytes
    }
    private suspend fun saveWithoutAi(originals: List<ManagedDocument>): Receipt {
        assertEquals(ScanUiState.Idle, vm.scanState.value)
        vm.saveReceipt("Manuell", "2026-10-08", "", 100.0, "Renovierung", "Material", "4800", "Ohne KI", false,
            originals = originals)
        return db.receiptDao().getAllReceipts().first { it.isNotEmpty() }.single()
    }
    private suspend fun assertOriginalSurvivesReload(extension: String, mime: String) {
        val (original, bytes) = importOriginal(extension)
        val receipt = saveWithoutAi(listOf(original))
        store.clear()
        db.close()
        db = newDatabase()
        instance.set(null, db)
        val reloaded = db.receiptDao().getReceiptById(receipt.id)!!
        val document = db.managedDocumentDao().getAllByReceiptId(receipt.internalId).single()
        assertEquals(original.localUri, reloaded.imageUrl)
        assertEquals(mime, document.mimeType)
        assertEquals(original.originalFilename, document.originalFilename)
        assertEquals(original.sha256, document.sha256)
        assertEquals(bytes.size.toLong(), document.fileSizeBytes)
        assertArrayEquals(bytes, File(reloaded.imageUrl).readBytes())
        if (extension == "pdf") {
            ParcelFileDescriptor.open(File(reloaded.imageUrl), ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer -> assertEquals(1, renderer.pageCount) }
            }
        } else {
            val bitmap = requireNotNull(BitmapFactory.decodeFile(reloaded.imageUrl))
            assertEquals(16, bitmap.width)
            bitmap.recycle()
        }
    }
    @Test fun manualPdfWithoutAiSurvivesDatabaseRestartAndOpens() = runTest { assertOriginalSurvivesReload("pdf", "application/pdf") }
    @Test fun manualPngWithoutAiRetainsOriginalBytes() = runTest { assertOriginalSurvivesReload("png", "image/png") }
    @Test fun manualWebpWithoutAiRetainsOriginalBytes() = runTest { assertOriginalSurvivesReload("webp", "image/webp") }
    @Test fun manualJpgWithoutAiRetainsOriginalBytes() = runTest { assertOriginalSurvivesReload("jpg", "image/jpeg") }

    @Test fun twoOriginalsPersistAsStableOrderedDocumentsAndSupplementalRestoreIsIdempotent() = runTest {
        val first = importOriginal("pdf").first
        val second = importOriginal("png").first
        val receipt = saveWithoutAi(listOf(first, second))
        val documents = db.managedDocumentDao().getAllByReceiptId(receipt.internalId)
        assertEquals(2, documents.size)
        assertEquals(listOf(first.localUri, second.localUri), receipt.imageUrl.split(','))
        documents.forEach { document ->
            val processed = db.managedDocumentDao().updateProcessingIfPresent(document, fieldsJson = "{\"datum\":\"2026-10-08\"}")!!
            val reviewed = db.managedDocumentDao().updateReviewIfPresent(processed,
                processed.copy(extractedFieldsJson = "{\"confirmed\":true}"))!!
            assertEquals(org.json.JSONObject(document.extractedFieldsJson).getInt("_receiptOriginalOrder"),
                org.json.JSONObject(reviewed.extractedFieldsJson).getInt("_receiptOriginalOrder"))
        }
        val backup = SupplementalDriveBackup.createPayload(app, db)
        assertFalse(backup.toString().contains(first.localUri))
        documents.forEach { db.managedDocumentDao().deleteById(it.documentId) }
        repeat(2) { SupplementalDriveBackup.restorePayload(app, db, backup) }
        val restored = ReceiptOriginalChain.ordered(db.managedDocumentDao().getAllByReceiptId(receipt.internalId))
        assertEquals(2, restored.size)
        assertEquals(documents.map { it.documentId }.toSet(), restored.map { it.documentId }.toSet())
        assertEquals(listOf(first.sha256, second.sha256), restored.map { it.sha256 })
        assertEquals(listOf(first.originalFilename, second.originalFilename), restored.map { it.originalFilename })
        assertEquals(listOf("application/pdf", "image/png"), restored.map { it.mimeType })
        assertEquals(receipt.imageUrl, db.receiptDao().getReceiptById(receipt.id)!!.imageUrl)
    }

    @Test fun failedCopyAndChangedOriginalCannotSilentlySaveFilelessReceipt() = runTest {
        assertThrows(Exception::class.java) { ReceiptOriginalStorage(app).importOriginal(Uri.fromFile(File(app.cacheDir, "missing.pdf"))) }
        val original = importOriginal("pdf").first
        File(original.localUri).writeText("corrupted")
        vm.saveReceipt("Manuell", "2026-10-08", "", 100.0, "Renovierung", "Material", "4800", "", false, originals = listOf(original))
        // Validation runs on IO before Room writes. Await the production error state.
        val state = vm.scanState.first { it is ScanUiState.Error } as ScanUiState.Error
        assertTrue(state.message.contains("Original"))
        assertTrue(db.receiptDao().getAllReceipts().first().isEmpty())
    }
}
