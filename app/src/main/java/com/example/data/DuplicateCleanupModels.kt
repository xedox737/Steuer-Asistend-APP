package com.example.data

/**
 * Pure domain model for read-only duplicate analysis and resumable cleanup planning.
 * No deletion is performed by this file.
 */
enum class DuplicateCleanupPhase {
    PREVIEWED,
    CONFIRMED,
    REFERENCES_REMOVED,
    DRIVE_METADATA_REMOVED,
    MAIN_FILE_REMOVED,
    LOCAL_PURGED,
    COMPLETED
}

data class DuplicateReceiptRecord(
    val roomId: Int,
    val internalId: String,
    val displayId: String,
    val mainDriveFileId: String,
    val metadataFileId: String?,
    val deletionStatus: String,
    val deletedAt: String?,
    val createdAt: String?,
    val indexReferenced: Boolean,
    val indexMetadataFileId: String?,
    val tombstone: ReceiptTombstone?,
    val completenessScore: Int
)

data class DuplicateMetadataPlan(
    val retainedMetadataFileIds: Set<String>,
    val orphanMetadataFileIds: Set<String>
)

data class DuplicateGroupPreview(
    val mainDriveFileId: String,
    val canonical: DuplicateReceiptRecord,
    val duplicatesToRemove: List<DuplicateReceiptRecord>,
    val metadataPlan: DuplicateMetadataPlan,
    val mainFileWillBeDeleted: Boolean = false,
    val warnings: List<String> = emptyList()
)

data class DuplicateCleanupJournal(
    val operationId: String,
    val mainDriveFileId: String,
    val canonicalInternalId: String?,
    val targetInternalIds: List<String>,
    val metadataFileIds: List<String>,
    val tombstoneInternalIds: List<String>,
    val removeWholeGroup: Boolean,
    val phase: DuplicateCleanupPhase,
    val createdAt: String,
    val updatedAt: String,
    val removedMetadataFileIds: Set<String> = emptySet(),
    val errors: List<String> = emptyList()
)

data class DuplicateCleanupFailure(
    val phase: DuplicateCleanupPhase,
    val target: String,
    val message: String
)

data class DuplicateCleanupReport(
    val operationId: String,
    val phase: DuplicateCleanupPhase,
    val completed: Boolean,
    val failures: List<DuplicateCleanupFailure> = emptyList()
)

object ReceiptDuplicateAnalyzer {
    fun analyze(
        receipts: List<Receipt>,
        indexEntries: List<ReceiptIndexEntry>,
        tombstones: Map<String, ReceiptTombstone>,
        createdAtByInternalId: Map<String, String?> = emptyMap()
    ): List<DuplicateGroupPreview> {
        val indexedByMain = indexEntries
            .filter { it.mainDriveFileId.isNotBlank() }
            .groupBy { it.mainDriveFileId }

        val eligibleStatuses = setOf("ACTIVE", "DELETED", "DELETE_PENDING")
        val eligibleReceipts = receipts.filter { it.deletionStatus in eligibleStatuses }
        val tombstoneMetadataRefs = tombstones.values
            .map { it.previousMetadataFileId }
            .filter(String::isNotBlank)
            .toSet()

        return eligibleReceipts
            .filter { !it.driveFileId.isNullOrBlank() }
            .groupBy { it.driveFileId!! }
            .filterValues { it.size > 1 }
            .map { (mainId, group) ->
                val indexForMain = indexedByMain[mainId].orEmpty()
                val records = group.map { receipt ->
                    val ownIndex = indexForMain.firstOrNull { it.internalId == receipt.internalId }
                    DuplicateReceiptRecord(
                        roomId = receipt.id,
                        internalId = receipt.internalId,
                        displayId = receipt.getEffectiveDisplayId(),
                        mainDriveFileId = mainId,
                        metadataFileId = receipt.driveMetadataFileId,
                        deletionStatus = receipt.deletionStatus,
                        deletedAt = receipt.deletedAt,
                        createdAt = createdAtByInternalId[receipt.internalId],
                        indexReferenced = ownIndex != null,
                        indexMetadataFileId = ownIndex?.metadataFileId,
                        tombstone = tombstones[receipt.internalId],
                        completenessScore = completenessScore(receipt)
                    )
                }
                val canonical = chooseCanonical(records, indexForMain)
                val duplicates = records.filterNot { it.roomId == canonical.roomId }
                val allMetadataRefs = records.mapNotNull { it.metadataFileId?.takeIf(String::isNotBlank) }
                val indexMetadataRefs = indexEntries.map { it.metadataFileId }.filter(String::isNotBlank).toSet()
                val retained = buildSet {
                    canonical.metadataFileId?.takeIf(String::isNotBlank)?.let(::add)
                    canonical.indexMetadataFileId?.takeIf(String::isNotBlank)?.let(::add)
                }
                val orphanCandidates = allMetadataRefs.toSet() - retained
                val orphaned = orphanCandidates.filterTo(mutableSetOf()) { candidate ->
                    val usedByOtherReceipt = receipts.any { other ->
                        other.id !in duplicates.map { it.roomId } && other.driveMetadataFileId == candidate
                    }
                    !usedByOtherReceipt &&
                        candidate !in indexMetadataRefs &&
                        candidate !in tombstoneMetadataRefs
                }
                DuplicateGroupPreview(
                    mainDriveFileId = mainId,
                    canonical = canonical,
                    duplicatesToRemove = duplicates,
                    metadataPlan = DuplicateMetadataPlan(
                        retainedMetadataFileIds = retained,
                        orphanMetadataFileIds = orphaned
                    ),
                    mainFileWillBeDeleted = false,
                    warnings = buildList {
                        if (indexForMain.size > 1) add("Mehrere Indexeinträge verweisen auf dieselbe Hauptdatei.")
                        if (records.mapNotNull { it.metadataFileId }.distinct().size > 1) add("Die Dubletten verwenden unterschiedliche Metadatendateien.")
                    }
                )
            }
            .sortedBy { it.canonical.displayId }
    }

    fun chooseCanonical(
        records: List<DuplicateReceiptRecord>,
        indexEntriesForMainFile: List<ReceiptIndexEntry>
    ): DuplicateReceiptRecord {
        require(records.isNotEmpty())

        val indexedInternalIds = indexEntriesForMainFile.map { it.internalId }.filter(String::isNotBlank).toSet()
        val indexedMetadataIds = indexEntriesForMainFile.map { it.metadataFileId }.filter(String::isNotBlank).toSet()

        return records.sortedWith(
            compareByDescending<DuplicateReceiptRecord> { it.internalId in indexedInternalIds }
                .thenByDescending { !it.metadataFileId.isNullOrBlank() && it.metadataFileId in indexedMetadataIds }
                .thenByDescending { it.completenessScore }
                .thenBy { it.createdAt ?: "9999-12-31T23:59:59" }
                .thenBy { it.roomId }
        ).first()
    }

    private fun completenessScore(receipt: Receipt): Int = listOf(
        receipt.internalId,
        receipt.displayId,
        receipt.driveFileId,
        receipt.driveMetadataFileId,
        receipt.storedFilename,
        receipt.originalMimeType,
        receipt.aussteller,
        receipt.datum,
        receipt.hauptkategorie,
        receipt.unterkategorie,
        receipt.wohneinheit,
        receipt.positionenJson
    ).count { !it.isNullOrBlank() } +
        if (receipt.bruttobetrag != 0.0) 1 else 0
}
