package com.example.data

/**
 * Pure, read-only replacement for destructive index sanitation.
 * It reports duplicate and stale references but never mutates Drive, Room, tombstones or files.
 */
data class ReceiptIndexDiagnostic(
    val originalEntries: List<ReceiptIndexEntry>,
    val canonicalEntries: List<ReceiptIndexEntry>,
    val duplicateEntries: List<ReceiptIndexEntry>,
    val missingLocalInternalIds: Set<String>,
    val sharedMainFileIds: Map<String, List<String>>,
    val sharedMetadataFileIds: Map<String, List<String>>
) {
    val hasIssues: Boolean
        get() = duplicateEntries.isNotEmpty() ||
            missingLocalInternalIds.isNotEmpty() ||
            sharedMainFileIds.isNotEmpty() ||
            sharedMetadataFileIds.isNotEmpty()
}

object ReadOnlyReceiptIndexSanitizer {
    fun diagnose(
        entries: List<ReceiptIndexEntry>,
        localReceipts: List<Receipt>
    ): ReceiptIndexDiagnostic {
        val localIds = localReceipts.map { it.internalId }.filter { it.isNotBlank() }.toSet()
        val canonical = mutableListOf<ReceiptIndexEntry>()
        val duplicates = mutableListOf<ReceiptIndexEntry>()

        entries.groupBy { it.internalId }
            .toSortedMap()
            .forEach { (_, group) ->
                val ordered = group.sortedWith(
                    compareByDescending<ReceiptIndexEntry> { entry ->
                        localReceipts.any {
                            it.internalId == entry.internalId &&
                                it.driveFileId == entry.mainDriveFileId &&
                                it.driveMetadataFileId == entry.metadataFileId
                        }
                    }
                        .thenByDescending { it.updatedAt }
                        .thenBy { it.mainDriveFileId }
                        .thenBy { it.metadataFileId }
                )
                canonical += ordered.first()
                duplicates += ordered.drop(1)
            }

        val sharedMain = entries
            .filter { it.mainDriveFileId.isNotBlank() }
            .groupBy { it.mainDriveFileId }
            .mapValues { (_, group) -> group.map { it.internalId }.distinct().sorted() }
            .filterValues { it.size > 1 }

        val sharedMetadata = entries
            .filter { it.metadataFileId.isNotBlank() }
            .groupBy { it.metadataFileId }
            .mapValues { (_, group) -> group.map { it.internalId }.distinct().sorted() }
            .filterValues { it.size > 1 }

        return ReceiptIndexDiagnostic(
            originalEntries = entries.toList(),
            canonicalEntries = canonical,
            duplicateEntries = duplicates,
            missingLocalInternalIds = entries.map { it.internalId }
                .filter { it.isNotBlank() && it !in localIds }
                .toSet(),
            sharedMainFileIds = sharedMain,
            sharedMetadataFileIds = sharedMetadata
        )
    }
}
