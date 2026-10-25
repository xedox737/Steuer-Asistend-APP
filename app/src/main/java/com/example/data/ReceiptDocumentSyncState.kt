package com.example.data

/** Transfer metadata is not an authored document edit. Never reconstruct reviewed state. */
internal object ReceiptDocumentSyncState {
    fun requireIdentity(existing: ManagedDocument?, documentId: String, propertyId: String, receiptId: String?) {
        require(existing == null || (existing.documentId == documentId && existing.propertyId == propertyId &&
            existing.receiptInternalId == receiptId)) {
            "Dokumentzuordnung widersprüchlich. Synchronisierung ohne Änderung abgebrochen."
        }
    }

    fun merge(existing: ManagedDocument?, transfer: ManagedDocument): ManagedDocument {
        if (existing == null) return transfer
        requireIdentity(existing, transfer.documentId, transfer.propertyId, transfer.receiptInternalId)
        return existing.copy(
            storedFilename = transfer.storedFilename,
            mimeType = transfer.mimeType,
            localUri = transfer.localUri,
            driveFileId = transfer.driveFileId,
            driveFolderId = transfer.driveFolderId,
            sha256 = transfer.sha256,
            fileSizeBytes = transfer.fileSizeBytes,
            legacyDriveFolderId = transfer.legacyDriveFolderId
        )
    }

    /** Diagnostics only: a missing association is not permission to infer or repair it. */
    fun associationProblem(document: ManagedDocument, receipt: Receipt): String? = when {
        document.propertyId != receipt.propertyId -> "PROPERTY_MISMATCH"
        document.unitId.isNullOrBlank() && receipt.unitId.isNotBlank() -> "MISSING_DOCUMENT_UNIT"
        !document.unitId.isNullOrBlank() && receipt.unitId.isNotBlank() && document.unitId != receipt.unitId -> "UNIT_MISMATCH"
        else -> null
    }

    data class Issue(val documentId: String, val receiptId: String, val reason: String)

    suspend fun scan(repository: ReceiptRepository): List<Issue> {
        val issues = mutableListOf<Issue>()
        for (document in repository.getAllManagedDocuments()) {
            val receiptId = document.receiptInternalId?.takeIf(String::isNotBlank) ?: continue
            val receipt = repository.getReceiptByInternalId(receiptId)
            val reason = if (receipt == null) "MISSING_RECEIPT" else associationProblem(document, receipt)
            if (reason != null) issues += Issue(document.documentId, receiptId, reason)
        }
        return issues
    }
}
