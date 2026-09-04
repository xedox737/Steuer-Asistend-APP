package com.example.data

import java.time.Instant

enum class DocumentMigrationAction { UNVERAENDERT, VERSCHIEBEN, UMBENENNEN, VERSCHIEBEN_UND_UMBENENNEN, PRUEFEN }

data class DocumentMigrationCandidate(
    val receiptInternalId: String,
    val driveFileId: String,
    val currentFolderId: String,
    val currentFilename: String,
    val targetFolderId: String,
    val targetFilename: String,
    val beforeSha256: String = "",
    val ambiguous: Boolean = false
)

data class DocumentMigrationPlanItem(
    val candidate: DocumentMigrationCandidate,
    val action: DocumentMigrationAction
)

data class DocumentMigrationPreview(
    val items: List<DocumentMigrationPlanItem>
) {
    val found get() = items.size
    val alreadyNew get() = items.count { it.action == DocumentMigrationAction.UNVERAENDERT }
    val unchanged get() = items.count { it.action == DocumentMigrationAction.UNVERAENDERT }
    val moveOnly get() = items.count { it.action == DocumentMigrationAction.VERSCHIEBEN }
    val renameOnly get() = items.count { it.action == DocumentMigrationAction.UMBENENNEN }
    val moveAndRename get() = items.count { it.action == DocumentMigrationAction.VERSCHIEBEN_UND_UMBENENNEN }
    val willMove get() = moveOnly + moveAndRename
    val willRename get() = renameOnly + moveAndRename
    val conflicts get() = items.groupBy { it.candidate.targetFolderId to it.candidate.targetFilename.lowercase() }
        .values.sumOf { (it.size - 1).coerceAtLeast(0) }
    val possibleDuplicates get() = items.filter { it.candidate.beforeSha256.isNotBlank() }
        .groupBy { it.candidate.beforeSha256.lowercase() }.values.sumOf { (it.size - 1).coerceAtLeast(0) }
    val review get() = items.count { it.action == DocumentMigrationAction.PRUEFEN }
}

object DocumentStorageMigrationPlanner {
    fun preview(candidates: List<DocumentMigrationCandidate>): DocumentMigrationPreview {
        val unique = candidates.distinctBy { it.driveFileId }
        val duplicateHashes = unique.filter { it.beforeSha256.isNotBlank() }.groupBy { it.beforeSha256.lowercase() }
            .filterValues { it.size > 1 }.keys
        val conflictingTargets = unique.groupBy { it.targetFolderId to it.targetFilename.lowercase() }
            .filterValues { it.map { item -> item.driveFileId }.distinct().size > 1 }.keys
        return DocumentMigrationPreview(unique.map { candidate ->
            val action = when {
                candidate.ambiguous || candidate.driveFileId.isBlank() || candidate.targetFolderId.isBlank() ||
                    candidate.beforeSha256.lowercase() in duplicateHashes ||
                    (candidate.targetFolderId to candidate.targetFilename.lowercase()) in conflictingTargets -> DocumentMigrationAction.PRUEFEN
                candidate.currentFolderId == candidate.targetFolderId && candidate.currentFilename == candidate.targetFilename -> DocumentMigrationAction.UNVERAENDERT
                candidate.currentFolderId != candidate.targetFolderId && candidate.currentFilename != candidate.targetFilename -> DocumentMigrationAction.VERSCHIEBEN_UND_UMBENENNEN
                candidate.currentFolderId != candidate.targetFolderId -> DocumentMigrationAction.VERSCHIEBEN
                else -> DocumentMigrationAction.UMBENENNEN
            }
            DocumentMigrationPlanItem(candidate, action)
        })
    }

    fun journal(item: DocumentMigrationPlanItem): DocumentMigrationJournal = DocumentMigrationJournal(
        documentId = StableDocumentIdentity.receiptDocumentId(item.candidate.receiptInternalId),
        receiptInternalId = item.candidate.receiptInternalId,
        driveFileId = item.candidate.driveFileId,
        originalFolderId = item.candidate.currentFolderId,
        originalFilename = item.candidate.currentFilename,
        targetFolderId = item.candidate.targetFolderId,
        targetFilename = item.candidate.targetFilename,
        beforeSha256 = item.candidate.beforeSha256,
        state = if (item.action == DocumentMigrationAction.UNVERAENDERT) "COMPLETED" else "PLANNED",
        updatedAt = Instant.now().toString()
    )

    fun resume(journal: List<DocumentMigrationJournal>): DocumentMigrationPreview = preview(
        journal.filter { it.state != "COMPLETED" }.map {
            DocumentMigrationCandidate(
                receiptInternalId = it.receiptInternalId.orEmpty(), driveFileId = it.driveFileId,
                currentFolderId = it.originalFolderId, currentFilename = it.originalFilename,
                targetFolderId = it.targetFolderId, targetFilename = it.targetFilename,
                beforeSha256 = it.beforeSha256,
                ambiguous = it.receiptInternalId.isNullOrBlank() || it.driveFileId.isBlank()
            )
        }
    )
}

object DocumentMigrationVerifier {
    fun isContentPreserved(beforeSha256: String, afterSha256: String): Boolean =
        beforeSha256.isNotBlank() && beforeSha256.equals(afterSha256, ignoreCase = true)
}
