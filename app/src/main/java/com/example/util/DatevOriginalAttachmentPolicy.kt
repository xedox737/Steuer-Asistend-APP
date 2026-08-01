package com.example.util

import com.example.data.Receipt
import java.io.File

data class DatevOriginalAttachment(
    val receiptInternalId: String,
    val file: File,
    val mimeType: String,
    val extension: String
)

object DatevOriginalAttachmentPolicy {
    fun resolve(receipt: Receipt): DatevOriginalAttachment? {
        if (receipt.internalId.isBlank()) return null
        val file = receipt.imageUrl
            .split(",")
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .map(::File)
            .firstOrNull { it.isFile && it.canRead() && it.length() > 0L }
            ?: return null

        val detected = detect(file, receipt.originalMimeType) ?: return null
        return DatevOriginalAttachment(
            receiptInternalId = receipt.internalId,
            file = file,
            mimeType = detected.first,
            extension = detected.second
        )
    }

    fun unique(receipts: List<Receipt>): List<DatevOriginalAttachment> =
        receipts.asSequence()
            .distinctBy { it.internalId }
            .mapNotNull(::resolve)
            .toList()

    private fun detect(file: File, declaredMimeType: String?): Pair<String, String>? {
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
            declaredMimeType == "application/pdf" && file.extension.equals("pdf", true) ->
                "application/pdf" to "pdf"
            else -> null
        }
    }
}
