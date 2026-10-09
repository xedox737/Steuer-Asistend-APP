package com.example.data

import org.json.JSONObject
import java.io.File

internal object ReceiptOriginalChain {
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
