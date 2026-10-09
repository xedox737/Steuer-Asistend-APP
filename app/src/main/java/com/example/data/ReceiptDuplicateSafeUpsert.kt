package com.example.data

/**
 * Result of resolving a restored receipt against the local Room inventory.
 */
data class ReceiptUpsertResolution(
    val receipt: Receipt,
    val matchedBy: Match
) {
    enum class Match { INTERNAL_ID, MAIN_DRIVE_FILE_ID, NEW }
}

/**
 * Pure resolver used by restore/import paths. Identity priority is deliberately strict:
 * internalId first, then the shared Drive main file id, otherwise a new local row.
 */
object ReceiptRestoreUpsertResolver {
    fun resolve(incoming: Receipt, existing: List<Receipt>, mode: RestoreMode = RestoreMode.REPLACE_FULL): ReceiptUpsertResolution {
        val byInternalId = incoming.internalId
            .takeIf { it.isNotBlank() }
            ?.let { id -> existing.firstOrNull { it.internalId == id } }
        if (byInternalId != null) {
            return ReceiptUpsertResolution(
                receipt = preferredReceipt(incoming, byInternalId, mode).copy(
                    id = byInternalId.id,
                    internalId = byInternalId.internalId,
                    displayId = if (mode == RestoreMode.MERGE) byInternalId.displayId ?: incoming.displayId else incoming.displayId ?: byInternalId.displayId
                ),
                matchedBy = ReceiptUpsertResolution.Match.INTERNAL_ID
            )
        }

        val byMainFile = incoming.driveFileId
            ?.takeIf { it.isNotBlank() }
            ?.let { mainId -> existing.firstOrNull { it.driveFileId == mainId } }
        if (byMainFile != null) {
            return ReceiptUpsertResolution(
                receipt = preferredReceipt(incoming, byMainFile, mode).copy(
                    id = byMainFile.id,
                    // Never manufacture a second identity for the same Drive main file.
                    internalId = byMainFile.internalId.ifBlank { incoming.internalId },
                    displayId = if (mode == RestoreMode.MERGE) byMainFile.displayId ?: incoming.displayId else incoming.displayId ?: byMainFile.displayId
                ),
                matchedBy = ReceiptUpsertResolution.Match.MAIN_DRIVE_FILE_ID
            )
        }

        return ReceiptUpsertResolution(incoming.copy(id = 0), ReceiptUpsertResolution.Match.NEW)
    }

    private fun preferredReceipt(incoming: Receipt, local: Receipt, mode: RestoreMode): Receipt =
        if (mode != RestoreMode.MERGE) incoming else local.copy(
            // Receipt has no local modification version. Sync revisions cannot prove that
            // remotely backed-up accounting fields supersede a subsequent local correction.
            driveFileId = local.driveFileId ?: incoming.driveFileId,
            driveFolderId = local.driveFolderId ?: incoming.driveFolderId,
            driveMetadataFileId = local.driveMetadataFileId ?: incoming.driveMetadataFileId,
            storedFilename = local.storedFilename ?: incoming.storedFilename,
            originalMimeType = local.originalMimeType ?: incoming.originalMimeType,
            fileSizeBytes = local.fileSizeBytes ?: incoming.fileSizeBytes,
            imageUrl = local.imageUrl.ifBlank { incoming.imageUrl }
        )
}

/**
 * Safe entry point for restore code. Repeating the same restore updates the existing Room row
 * instead of inserting another record. Existing production insert behaviour remains untouched.
 */
suspend fun ReceiptRepository.upsertRestoredReceipt(receipt: Receipt, mode: RestoreMode = RestoreMode.MERGE): ReceiptUpsertResolution {
    val byInternalId = receipt.internalId
        .takeIf(String::isNotBlank)
        ?.let { getReceiptByInternalId(it) }
    val byMainDriveFileId = if (byInternalId == null) {
        getReceiptByMainDriveFileId(receipt.driveFileId)
    } else {
        null
    }
    val resolution = ReceiptRestoreUpsertResolver.resolve(
        incoming = receipt,
        existing = listOfNotNull(byInternalId, byMainDriveFileId),
        mode = mode
    )
    if (mode != RestoreMode.MERGE || resolution.receipt != (byInternalId ?: byMainDriveFileId)) {
        insert(resolution.receipt)
    }
    return resolution
}
