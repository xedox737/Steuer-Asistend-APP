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
    fun resolve(incoming: Receipt, existing: List<Receipt>): ReceiptUpsertResolution {
        val byInternalId = incoming.internalId
            .takeIf { it.isNotBlank() }
            ?.let { id -> existing.firstOrNull { it.internalId == id } }
        if (byInternalId != null) {
            return ReceiptUpsertResolution(
                receipt = incoming.copy(
                    id = byInternalId.id,
                    internalId = byInternalId.internalId,
                    displayId = incoming.displayId ?: byInternalId.displayId
                ),
                matchedBy = ReceiptUpsertResolution.Match.INTERNAL_ID
            )
        }

        val byMainFile = incoming.driveFileId
            ?.takeIf { it.isNotBlank() }
            ?.let { mainId -> existing.firstOrNull { it.driveFileId == mainId } }
        if (byMainFile != null) {
            return ReceiptUpsertResolution(
                receipt = incoming.copy(
                    id = byMainFile.id,
                    // Never manufacture a second identity for the same Drive main file.
                    internalId = byMainFile.internalId.ifBlank { incoming.internalId },
                    displayId = incoming.displayId ?: byMainFile.displayId
                ),
                matchedBy = ReceiptUpsertResolution.Match.MAIN_DRIVE_FILE_ID
            )
        }

        return ReceiptUpsertResolution(incoming.copy(id = 0), ReceiptUpsertResolution.Match.NEW)
    }
}

/**
 * Safe entry point for restore code. Repeating the same restore updates the existing Room row
 * instead of inserting another record. Existing production insert behaviour remains untouched.
 */
suspend fun ReceiptRepository.upsertRestoredReceipt(receipt: Receipt): ReceiptUpsertResolution {
    val resolution = ReceiptRestoreUpsertResolver.resolve(
        incoming = receipt,
        existing = getAllReceiptsIncludingDeletedList()
    )
    insert(resolution.receipt)
    return resolution
}
