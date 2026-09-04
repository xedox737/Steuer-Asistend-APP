package com.example.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.InflaterInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class DocumentTextResult(
    val text: String,
    val status: DocumentProcessingStatus,
    val source: String,
    val errorMessage: String = ""
)

/** Best-effort embedded text reader. It never rewrites the source PDF. */
object PdfEmbeddedTextExtractor {
    fun extract(bytes: ByteArray): String {
        if (bytes.size < 5 || !bytes.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray())) return ""
        val raw = bytes.toString(Charsets.ISO_8859_1)
        val chunks = mutableListOf<String>()
        Regex("stream\\r?\\n([\\s\\S]*?)\\r?\\nendstream").findAll(raw).forEach { match ->
            val headerStart = (match.range.first - 300).coerceAtLeast(0)
            val header = raw.substring(headerStart, match.range.first)
            val streamBytes = match.groupValues[1].toByteArray(Charsets.ISO_8859_1)
            val content = if (header.contains("/FlateDecode")) inflate(streamBytes) else match.groupValues[1]
            extractTextOperators(content, chunks)
        }
        if (chunks.isEmpty()) extractTextOperators(raw, chunks)
        return chunks.joinToString(" ").replace(Regex("\\s+"), " ").trim()
    }

    fun isUsable(text: String): Boolean = text.count(Char::isLetterOrDigit) >= 24 && text.split(Regex("\\s+")).size >= 4

    private fun inflate(bytes: ByteArray): String = runCatching {
        InflaterInputStream(ByteArrayInputStream(bytes)).use { input ->
            ByteArrayOutputStream().use { output -> input.copyTo(output); output.toByteArray().toString(Charsets.ISO_8859_1) }
        }
    }.getOrDefault("")

    private fun extractTextOperators(content: String, target: MutableList<String>) {
        Regex("""\(((?:\\.|[^\\()])*)\)\s*Tj""").findAll(content).forEach { target += decodeLiteral(it.groupValues[1]) }
        Regex("\\[(.*?)]\\s*TJ", RegexOption.DOT_MATCHES_ALL).findAll(content).forEach { array ->
            Regex("""\(((?:\\.|[^\\()])*)\)""").findAll(array.groupValues[1]).forEach { target += decodeLiteral(it.groupValues[1]) }
        }
    }

    private fun decodeLiteral(value: String): String = value
        .replace("\\\\(", "(").replace("\\\\)", ")").replace("\\\\n", " ")
        .replace("\\\\r", " ").replace("\\\\t", " ").replace("\\\\\\\\", "\\")
}

class DocumentOcrService {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun extract(file: File, mimeType: String): DocumentTextResult {
        return try {
        if (!file.exists() || !file.isFile || file.length() == 0L) {
            DocumentTextResult("", DocumentProcessingStatus.FEHLGESCHLAGEN, "NONE", "Datei fehlt oder ist leer.")
        } else if (mimeType.startsWith("text/")) {
            val text = file.readText()
            if (text.isBlank()) DocumentTextResult("", DocumentProcessingStatus.FEHLGESCHLAGEN, "TEXT", "Die Textdatei ist leer.")
            else DocumentTextResult(text, DocumentProcessingStatus.ERFOLGREICH, "TEXT")
        } else if (mimeType.contains("pdf", ignoreCase = true) || file.extension.equals("pdf", true)) {
            val embedded = PdfEmbeddedTextExtractor.extract(file.readBytes())
            if (PdfEmbeddedTextExtractor.isUsable(embedded)) {
                DocumentTextResult(embedded, DocumentProcessingStatus.ERFOLGREICH, "PDF_TEXT")
            } else {
                val text = recognizePdf(file)
                if (text.isBlank()) DocumentTextResult("", DocumentProcessingStatus.FEHLGESCHLAGEN, "OCR", "Kein lesbarer Text erkannt.")
                else DocumentTextResult(text, DocumentProcessingStatus.ERFOLGREICH, "OCR")
            }
        } else {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap == null) {
                DocumentTextResult("", DocumentProcessingStatus.FEHLGESCHLAGEN, "OCR", "Bild konnte nicht gelesen werden.")
            } else {
                val text = try { recognize(bitmap) } finally { bitmap.recycle() }
                if (text.isBlank()) DocumentTextResult("", DocumentProcessingStatus.FEHLGESCHLAGEN, "OCR", "Kein lesbarer Text erkannt.")
                else DocumentTextResult(text, DocumentProcessingStatus.ERFOLGREICH, "OCR")
            }
        }
    } catch (e: Exception) {
        DocumentTextResult("", DocumentProcessingStatus.FEHLGESCHLAGEN, "OCR", e.message ?: "Texterkennung fehlgeschlagen.")
        }
    }

    private suspend fun recognizePdf(file: File): String {
        val pages = mutableListOf<String>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                for (index in 0 until renderer.pageCount.coerceAtMost(50)) {
                    renderer.openPage(index).use { page ->
                        val scale = (1800f / page.width.coerceAtLeast(page.height)).coerceAtMost(2f)
                        val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(android.graphics.Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            recognize(bitmap).takeIf(String::isNotBlank)?.let { pages += "[Seite ${index + 1}] $it" }
                        } finally { bitmap.recycle() }
                    }
                }
            }
        }
        return pages.joinToString("\n")
    }

    private suspend fun recognize(bitmap: Bitmap): String = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text.trim()
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
