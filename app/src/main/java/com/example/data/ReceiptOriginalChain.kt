package com.example.data

import org.json.JSONObject
import org.json.JSONArray
import java.io.File

internal object ReceiptOriginalChain {
    fun metadataJson(documents: List<ManagedDocument>): JSONArray = JSONArray().apply {
        ordered(documents).forEachIndexed { order, document -> put(JSONObject().apply {
            put("documentId", document.documentId); put("originalFilename", document.originalFilename)
            put("unitId", document.unitId ?: JSONObject.NULL)
            put("storedFilename", document.storedFilename); put("mimeType", document.mimeType)
            put("sha256", document.sha256); put("fileSizeBytes", document.fileSizeBytes)
            put("driveFileId", document.driveFileId ?: JSONObject.NULL)
            put("driveFolderId", document.driveFolderId ?: JSONObject.NULL)
            put("createdAt", document.createdAt); put("updatedAt", document.updatedAt); put("order", order)
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
                extractedFieldsJson = JSONObject().put("_receiptOriginalOrder", item.optInt("order", index)).toString())
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
        val existing = receipt.imageUrl.split(',').map(String::trim).filter { File(it).isFile }
        return (existing + originals.map { it.localUri }).distinct().joinToString(",")
    }
}
