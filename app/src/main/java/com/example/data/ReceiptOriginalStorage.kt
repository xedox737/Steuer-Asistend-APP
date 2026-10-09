package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.util.DatevOriginalAttachmentPolicy
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** Copy before decoding/AI. A picker URI or a normalized analysis bitmap is never the archive. */
class ReceiptOriginalStorage(private val context: Context) {
    fun importOriginal(uri: Uri, filename: String? = null): ManagedDocument {
        val resolver = context.contentResolver
        val name = filename ?: runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "Beleg"
        if (uri.scheme == "content") {
            // Some providers do not grant persistable access; the managed copy remains sufficient.
            runCatching { resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        val directory = File(context.filesDir, "managed_documents")
        check(directory.isDirectory || directory.mkdirs()) { "Der lokale Belegspeicher konnte nicht angelegt werden." }
        val staging = File.createTempFile("receipt-import-", ".tmp", directory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(staging).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        output.write(buffer, 0, size)
                        digest.update(buffer, 0, size)
                    }
                    output.fd.sync()
                }
            } ?: error("Die ausgewählte Belegdatei konnte nicht gelesen werden.")
            check(staging.length() > 0) { "Die ausgewählte Belegdatei ist leer." }
            val format = DatevOriginalAttachmentPolicy.detect(staging)
                ?: error("Bitte einen Beleg als PDF, JPG, PNG oder WEBP auswählen.")
            val id = UUID.randomUUID().toString()
            val file = File(directory, "$id.${format.second}")
            check(staging.renameTo(file)) { "Das Original konnte nicht dauerhaft gespeichert werden. Bitte erneut auswählen." }
            val now = Instant.now().toString()
            return ManagedDocument(
                documentId = id, propertyId = StableDocumentIdentity.LEGACY_PROPERTY_ID,
                originalFilename = name, storedFilename = file.name,
                mimeType = format.first, localUri = file.absolutePath,
                sha256 = digest.digest().joinToString("") { "%02x".format(it) }, fileSizeBytes = file.length(),
                createdAt = now, updatedAt = now, source = DocumentSource.RECEIPT.name,
                documentType = ManagedDocumentType.RECHNUNG.name,
                aiAnalysisStatus = DocumentProcessingStatus.NICHT_ERFORDERLICH.name
            )
        } finally {
            staging.delete()
        }
    }

    companion object {
        fun validate(originals: List<ManagedDocument>) {
            originals.forEach { document ->
                val file = File(document.localUri)
                check(file.isFile && file.canRead() && file.length() == document.fileSizeBytes && file.length() > 0) {
                    "Das Original ‚${document.originalFilename}‘ fehlt oder ist nicht vollständig. Bitte erneut auswählen."
                }
                check(com.example.util.ReceiptManifestService.calculateSha256(file) == document.sha256) {
                    "Das Original ‚${document.originalFilename}‘ wurde verändert. Bitte erneut auswählen."
                }
            }
        }
    }
}
