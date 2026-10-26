package com.example.data

import org.json.JSONObject
import org.json.JSONArray
import java.io.File

internal object ReceiptOriginalChain {
    /** Keep the legacy representation for separator-free paths, escape complete paths otherwise. */
    fun encodePaths(paths: List<String>): String = if (paths.any { ',' in it || it.trimStart().startsWith("[") })
        JSONArray(paths).toString() else paths.joinToString(",")

    fun paths(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        if (raw.trimStart().startsWith("[")) {
            val array = JSONArray(raw)
            return (0 until array.length()).map { array.getString(it) }
        }
        // An existing original with a comma in its filename is one original, never two pages.
        if (File(raw).isFile) return listOf(raw)
        val solutions = mutableListOf<List<String>>()
        fun visit(offset: Int, found: List<String>) {
            if (solutions.size > 1) return
            if (offset == raw.length) { solutions.add(found); return }
            var end = raw.indexOf(',', offset)
            while (end >= 0) {
                val path = raw.substring(offset, end).trim()
                if (File(path).isFile) visit(end + 1, found + path)
                end = raw.indexOf(',', end + 1)
            }
            val path = raw.substring(offset).trim()
            if (File(path).isFile) visit(raw.length, found + path)
        }
        visit(0, emptyList())
        // No original is renamed/deleted and no guessed fragment becomes a new attachment.
        return solutions.singleOrNull() ?: listOf(raw)
    }

    fun metadataJson(documents: List<ManagedDocument>): JSONArray = JSONArray().apply {
        ordered(documents).forEachIndexed { order, document -> put(JSONObject().apply {
            put("documentId", document.documentId); put("originalFilename", document.originalFilename)
            put("unitId", document.unitId ?: JSONObject.NULL)
            put("storedFilename", document.storedFilename); put("mimeType", document.mimeType)
            put("sha256", document.sha256); put("fileSizeBytes", document.fileSizeBytes)
            put("driveFileId", document.driveFileId ?: JSONObject.NULL)
            put("driveFolderId", document.driveFolderId ?: JSONObject.NULL)
            put("createdAt", document.createdAt); put("updatedAt", document.updatedAt); put("order", order)
            // Preserve the authored JSON verbatim. Reconstructing a rank-only blob changes
            // reviewed fields during the later supplemental merge, even for a single page.
            put("extractedFieldsJson", document.extractedFieldsJson)
        }) }
    }

    fun parseMetadata(array: JSONArray, receiptId: String, propertyId: String): List<ManagedDocument> =
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val id = item.getString("documentId")
            require(id.isNotBlank()) { "Originalanhang ohne stabile Dokument-ID." }
            ManagedDocument(documentId = id, receiptInternalId = receiptId, propertyId = propertyId,
                unitId = if (item.isNull("unitId")) null else item.optString("unitId").takeIf(String::isNotBlank),
                originalFilename = item.optString("originalFilename"), storedFilename = item.optString("storedFilename"),
                mimeType = item.optString("mimeType", "application/octet-stream"), sha256 = item.optString("sha256"),
                fileSizeBytes = item.optLong("fileSizeBytes"),
                driveFileId = if (item.isNull("driveFileId")) null else item.optString("driveFileId").takeIf(String::isNotBlank),
                driveFolderId = if (item.isNull("driveFolderId")) null else item.optString("driveFolderId").takeIf(String::isNotBlank),
                createdAt = item.optString("createdAt"), updatedAt = item.optString("updatedAt"),
                source = DocumentSource.RECEIPT.name, documentType = ManagedDocumentType.RECHNUNG.name,
                extractedFieldsJson = if (item.has("extractedFieldsJson") && !item.isNull("extractedFieldsJson"))
                    item.getString("extractedFieldsJson")
                else JSONObject().put("_receiptOriginalOrder", item.optInt("order", index)).toString())
        }.also { documents ->
            require(documents.map { it.documentId }.distinct().size == documents.size) { "Doppelte Originalanhang-ID in der Sicherung." }
        }

    fun ordered(documents: List<ManagedDocument>): List<ManagedDocument> = documents.sortedWith(
        compareBy<ManagedDocument> {
            runCatching { JSONObject(it.extractedFieldsJson).optInt("_receiptOriginalOrder", Int.MAX_VALUE) }
                .getOrDefault(Int.MAX_VALUE)
        }.thenBy { if (it.documentId == StableDocumentIdentity.receiptDocumentId(it.receiptInternalId.orEmpty())) 0 else 1 }
            .thenBy { it.createdAt }.thenBy { it.documentId }
    )

    fun localPaths(receipt: Receipt, documents: List<ManagedDocument>): String {
        val originals = ordered(documents.filter { it.receiptInternalId == receipt.internalId })
        if (originals.isEmpty() || originals.any { !File(it.localUri).isFile }) return receipt.imageUrl
        val existing = paths(receipt.imageUrl).filter { File(it).isFile }
        return encodePaths((existing + originals.map { it.localUri }).distinct())
    }
}
