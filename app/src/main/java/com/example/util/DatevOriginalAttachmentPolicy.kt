package com.example.util

import com.example.data.Receipt
import com.example.data.ManagedDocument
import java.io.File

data class DatevOriginalAttachment(
    val receiptInternalId: String,
    val file: File,
    val mimeType: String,
    val extension: String,
    val attachmentId: String = "",
    val originalFilename: String = "",
    val sha256: String = "",
    val order: Int = 0
)

object DatevOriginalAttachmentPolicy {
    fun resolve(receipt: Receipt): DatevOriginalAttachment? = resolveAll(receipt).firstOrNull()

    /** Fail closed if any selected page is missing; a readable first page is not a complete receipt. */
    fun resolveAll(receipt: Receipt, documents: List<ManagedDocument> = emptyList()): List<DatevOriginalAttachment> {
        if (receipt.internalId.isBlank()) return emptyList()
        val paths = receipt.imageUrl.split(",").map(String::trim).filter(String::isNotBlank).distinct()
        if (paths.isEmpty()) return emptyList()
        return try {
            paths.mapIndexed { index, path ->
                val file = File(path.removePrefix("file://"))
                if (!file.isFile || !file.canRead() || file.length() == 0L) return emptyList()
                val detected = detect(file) ?: return emptyList()
                val sha = ReceiptManifestService.calculateSha256(file)
                val document = documents.firstOrNull { it.receiptInternalId == receipt.internalId && it.localUri == path }
                if (document != null && (document.sha256.isNotBlank() && document.sha256 != sha ||
                    document.fileSizeBytes > 0 && document.fileSizeBytes != file.length())) return emptyList()
                DatevOriginalAttachment(receipt.internalId, file, detected.first, detected.second,
                    document?.documentId ?: "${receipt.internalId}:original:$sha",
                    document?.originalFilename?.takeIf(String::isNotBlank) ?: file.name, sha, index)
            }
        } catch (_: java.io.IOException) { emptyList() }
    }

    fun unique(receipts: List<Receipt>): List<DatevOriginalAttachment> =
        receipts.asSequence()
            .distinctBy { it.internalId }
            .flatMap { resolveAll(it).asSequence() }
            .toList()

    internal fun detect(file: File): Pair<String, String>? {
        val header = file.inputStream().use { input ->
            val bytes = ByteArray(12)
            val count = input.read(bytes)
            if (count <= 0) ByteArray(0) else bytes.copyOf(count)
        }
        return when {
            header.size >= 4 &&
                header.copyOfRange(0, 4).contentEquals(byteArrayOf(0x25, 0x50, 0x44, 0x46)) ->
                "application/pdf" to "pdf"
            header.size >= 3 &&
                header[0] == 0xFF.toByte() &&
                header[1] == 0xD8.toByte() &&
                header[2] == 0xFF.toByte() ->
                "image/jpeg" to "jpg"
            header.size >= 8 &&
                header.copyOfRange(0, 8).contentEquals(
                    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
                ) ->
                "image/png" to "png"
            header.size >= 12 &&
                header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
                header.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()) ->
                "image/webp" to "webp"
            else -> null
        }
    }
}
