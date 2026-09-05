package com.example.data

import java.time.Instant

enum class DocumentMigrationAction { UNVERAENDERT, VERSCHIEBEN, UMBENENNEN, VERSCHIEBEN_UND_UMBENENNEN, PRUEFEN }

enum class DriveDocumentInventoryStatus {
    OK, LEGACY_LAYOUT, ORPHAN, MISSING_LOCAL_REFERENCE, MISSING_INDEX_REFERENCE,
    MULTIPLE_REFERENCES, POSSIBLE_DUPLICATE, UNKNOWN_APP_FILE, MIGRATION_PRUEFEN
}

enum class DriveDocumentInventorySource {
    LOCAL_RECEIPT, RECEIPT_INDEX, MANAGED_DOCUMENT, DOCUMENT_INDEX, DRIVE_APP_PROPERTIES, LEGACY_APP_FOLDER
}

data class DriveInventoryReference(
    val driveFileId: String,
    val receiptInternalId: String = "",
    val documentId: String = "",
    val source: DriveDocumentInventorySource,
    val targetPath: String = "",
    val targetFilename: String = "",
    val sha256: String = ""
)

data class DriveInventoryFile(
    val driveFileId: String,
    val name: String,
    val mimeType: String = "application/octet-stream",
    val parentIds: List<String> = emptyList(),
    val sizeBytes: Long = 0L,
    val sha256: String = "",
    val receiptInternalId: String = "",
    val documentId: String = "",
    val documentRole: String = "",
    val entityType: String = "",
    val appRelevant: Boolean = false,
    val legacyFolder: Boolean = false,
    val reachable: Boolean = true
)

data class DriveDocumentInventoryItem(
    val driveFileId: String,
    val name: String,
    val mimeType: String,
    val parentIds: List<String>,
    val sizeBytes: Long,
    val sha256: String,
    val receiptInternalId: String,
    val documentId: String,
    val documentRole: String,
    val entityType: String,
    val detectedSources: Set<DriveDocumentInventorySource>,
    val referencedByLocalReceipt: Boolean,
    val referencedByManagedDocument: Boolean,
    val referencedByReceiptIndex: Boolean,
    val referencedByDocumentIndex: Boolean,
    val targetPath: String,
    val targetFilename: String,
    val status: DriveDocumentInventoryStatus
)

/** Pure inventory reconciliation. It only reports; it never mutates local or Drive data. */
object DriveDocumentInventoryPlanner {
    fun build(
        files: List<DriveInventoryFile>,
        references: List<DriveInventoryReference>
    ): List<DriveDocumentInventoryItem> {
        val relevantFiles = files.filter { it.appRelevant || it.legacyFolder || references.any { ref -> ref.driveFileId == it.driveFileId } }
            .toMutableList()
        val foundIds = relevantFiles.map { it.driveFileId }.toSet()
        references.filter { it.driveFileId.isNotBlank() && it.driveFileId !in foundIds }.distinctBy { it.driveFileId }.forEach { ref ->
            relevantFiles += DriveInventoryFile(
                driveFileId = ref.driveFileId, name = ref.targetFilename, sha256 = ref.sha256,
                receiptInternalId = ref.receiptInternalId, documentId = ref.documentId,
                appRelevant = true, reachable = false
            )
        }
        val refsByFile = references.filter { it.driveFileId.isNotBlank() }.groupBy { it.driveFileId }
        val receiptFiles = (references.mapNotNull { ref -> ref.receiptInternalId.takeIf(String::isNotBlank)?.let { it to ref.driveFileId } } +
            relevantFiles.mapNotNull { file -> file.receiptInternalId.takeIf(String::isNotBlank)?.let { it to file.driveFileId } })
            .groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
        val documentFiles = (references.mapNotNull { ref -> ref.documentId.takeIf(String::isNotBlank)?.let { it to ref.driveFileId } } +
            relevantFiles.mapNotNull { file -> file.documentId.takeIf(String::isNotBlank)?.let { it to file.driveFileId } })
            .groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
        val duplicateHashes = relevantFiles.filter { it.sha256.isNotBlank() }
            .groupBy { it.sha256.lowercase() }.filterValues { values -> values.map { it.driveFileId }.distinct().size > 1 }.keys

        return relevantFiles.distinctBy { it.driveFileId }.map { file ->
            val refs = refsByFile[file.driveFileId].orEmpty()
            val sources = refs.map { it.source }.toMutableSet().apply {
                if (file.appRelevant) add(DriveDocumentInventorySource.DRIVE_APP_PROPERTIES)
                if (file.legacyFolder) add(DriveDocumentInventorySource.LEGACY_APP_FOLDER)
            }
            val receiptId = file.receiptInternalId.ifBlank { refs.map { it.receiptInternalId }.firstOrNull(String::isNotBlank).orEmpty() }
            val documentId = file.documentId.ifBlank { refs.map { it.documentId }.firstOrNull(String::isNotBlank).orEmpty() }
            val targetPath = refs.map { it.targetPath }.firstOrNull(String::isNotBlank).orEmpty()
            val targetFilename = refs.map { it.targetFilename }.firstOrNull(String::isNotBlank).orEmpty()
            val identities = (refs.mapNotNull {
                when {
                    it.receiptInternalId.isNotBlank() -> "receipt:${it.receiptInternalId}"
                    it.documentId.isNotBlank() -> "document:${it.documentId}"
                    else -> null
                }
            } + listOfNotNull(
                file.receiptInternalId.takeIf { it.isNotBlank() }?.let { "receipt:$it" },
                file.documentId.takeIf { it.isNotBlank() }?.let { "document:$it" }
            )).toSet()
            val localReceipt = refs.any { it.source == DriveDocumentInventorySource.LOCAL_RECEIPT }
            val localDocument = refs.any { it.source == DriveDocumentInventorySource.MANAGED_DOCUMENT }
            val receiptIndex = refs.any { it.source == DriveDocumentInventorySource.RECEIPT_INDEX }
            val documentIndex = refs.any { it.source == DriveDocumentInventorySource.DOCUMENT_INDEX }
            val multiple = identities.size > 1 ||
                (receiptId.isNotBlank() && receiptFiles[receiptId].orEmpty().size > 1) ||
                (documentId.isNotBlank() && documentFiles[documentId].orEmpty().size > 1)
            val status = when {
                !file.reachable -> DriveDocumentInventoryStatus.MIGRATION_PRUEFEN
                multiple -> DriveDocumentInventoryStatus.MULTIPLE_REFERENCES
                file.sha256.isNotBlank() && file.sha256.lowercase() in duplicateHashes -> DriveDocumentInventoryStatus.POSSIBLE_DUPLICATE
                (receiptId.isNotBlank() || documentId.isNotBlank()) && !localReceipt && !localDocument -> DriveDocumentInventoryStatus.MISSING_LOCAL_REFERENCE
                refs.isEmpty() && (file.appRelevant || file.legacyFolder) -> DriveDocumentInventoryStatus.ORPHAN
                (localReceipt && !receiptIndex) || (localDocument && !documentIndex) -> DriveDocumentInventoryStatus.MISSING_INDEX_REFERENCE
                file.legacyFolder || (targetFilename.isNotBlank() && file.name != targetFilename) -> DriveDocumentInventoryStatus.LEGACY_LAYOUT
                refs.isEmpty() -> DriveDocumentInventoryStatus.UNKNOWN_APP_FILE
                else -> DriveDocumentInventoryStatus.OK
            }
            DriveDocumentInventoryItem(
                driveFileId = file.driveFileId, name = file.name, mimeType = file.mimeType,
                parentIds = file.parentIds, sizeBytes = file.sizeBytes, sha256 = file.sha256,
                receiptInternalId = receiptId, documentId = documentId, documentRole = file.documentRole,
                entityType = file.entityType, detectedSources = sources,
                referencedByLocalReceipt = localReceipt, referencedByManagedDocument = localDocument,
                referencedByReceiptIndex = receiptIndex, referencedByDocumentIndex = documentIndex,
                targetPath = targetPath, targetFilename = targetFilename, status = status
            )
        }
    }
}

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
    val items: List<DocumentMigrationPlanItem>,
    val inventoryItems: List<DriveDocumentInventoryItem> = emptyList()
) {
    val found get() = if (inventoryItems.isEmpty()) items.size else inventoryItems.size
    val alreadyNew get() = if (inventoryItems.isEmpty()) items.count { it.action == DocumentMigrationAction.UNVERAENDERT }
        else inventoryItems.count { it.status == DriveDocumentInventoryStatus.OK }
    val unchanged get() = items.count { it.action == DocumentMigrationAction.UNVERAENDERT }
    val moveOnly get() = items.count { it.action == DocumentMigrationAction.VERSCHIEBEN }
    val renameOnly get() = items.count { it.action == DocumentMigrationAction.UMBENENNEN }
    val moveAndRename get() = items.count { it.action == DocumentMigrationAction.VERSCHIEBEN_UND_UMBENENNEN }
    val willMove get() = moveOnly + moveAndRename
    val willRename get() = renameOnly + moveAndRename
    val conflicts get() = if (inventoryItems.isEmpty()) items.groupBy { it.candidate.targetFolderId to it.candidate.targetFilename.lowercase() }
        .values.sumOf { (it.size - 1).coerceAtLeast(0) }
        else inventoryItems.count { it.status == DriveDocumentInventoryStatus.MULTIPLE_REFERENCES }
    val possibleDuplicates get() = if (inventoryItems.isEmpty()) items.filter { it.candidate.beforeSha256.isNotBlank() }
        .groupBy { it.candidate.beforeSha256.lowercase() }.values.sumOf { (it.size - 1).coerceAtLeast(0) }
        else inventoryItems.count { it.status == DriveDocumentInventoryStatus.POSSIBLE_DUPLICATE }
    val review get() = items.count { it.action == DocumentMigrationAction.PRUEFEN }
    val orphanedAppFiles get() = inventoryItems.count { it.status == DriveDocumentInventoryStatus.ORPHAN }
    val missingLocalReferences get() = inventoryItems.count { it.status == DriveDocumentInventoryStatus.MISSING_LOCAL_REFERENCE }
    val indexConflicts get() = inventoryItems.count { it.status in setOf(DriveDocumentInventoryStatus.MISSING_INDEX_REFERENCE, DriveDocumentInventoryStatus.MULTIPLE_REFERENCES) }
    val inventoryReview get() = inventoryItems.count { it.status !in setOf(DriveDocumentInventoryStatus.OK, DriveDocumentInventoryStatus.LEGACY_LAYOUT) }
    val manualReview get() = (
        items.filter { it.action == DocumentMigrationAction.PRUEFEN }.map { it.candidate.driveFileId } +
            inventoryItems.filter { it.status !in setOf(DriveDocumentInventoryStatus.OK, DriveDocumentInventoryStatus.LEGACY_LAYOUT) }
                .map { it.driveFileId }
        ).toSet().size
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

data class ManagedDocumentDriveReorganizationPlan(
    val action: DocumentMigrationAction,
    val canApply: Boolean,
    val reason: String = ""
)

object ManagedDocumentDriveReorganization {
    fun statusAfterConfirmedChange(hasDriveFile: Boolean, targetRelevantFieldsChanged: Boolean, currentStatus: String): String =
        if (hasDriveFile && targetRelevantFieldsChanged) "DRIVE_REORGANIZATION_PENDING" else currentStatus

    fun isSameVerifiedOriginal(
        expectedDriveFileId: String,
        actualDriveFileId: String?,
        beforeSha256: String,
        afterSha256: String
    ): Boolean = actualDriveFileId == expectedDriveFileId &&
        DocumentMigrationVerifier.isContentPreserved(beforeSha256, afterSha256)

    fun plan(
        reachable: Boolean,
        currentParentIds: List<String>,
        currentFilename: String,
        targetFolderId: String,
        targetFilename: String,
        expectedSha256: String,
        actualSha256: String,
        referenceCount: Int = 1
    ): ManagedDocumentDriveReorganizationPlan {
        if (!reachable) return ManagedDocumentDriveReorganizationPlan(DocumentMigrationAction.PRUEFEN, false, "DRIVE_FILE_UNREACHABLE")
        if (currentParentIds.size != 1) return ManagedDocumentDriveReorganizationPlan(DocumentMigrationAction.PRUEFEN, false, "AMBIGUOUS_PARENT")
        if (targetFolderId.isBlank() || targetFilename.isBlank()) return ManagedDocumentDriveReorganizationPlan(DocumentMigrationAction.PRUEFEN, false, "TARGET_INCOMPLETE")
        if (expectedSha256.isNotBlank() && !expectedSha256.equals(actualSha256, ignoreCase = true)) {
            return ManagedDocumentDriveReorganizationPlan(DocumentMigrationAction.PRUEFEN, false, "HASH_MISMATCH")
        }
        val folderDiffers = currentParentIds.single() != targetFolderId
        val nameDiffers = currentFilename != targetFilename
        if (referenceCount > 1 && (folderDiffers || nameDiffers)) {
            return ManagedDocumentDriveReorganizationPlan(DocumentMigrationAction.PRUEFEN, false, "MULTIPLE_LOCAL_REFERENCES")
        }
        val action = when {
            folderDiffers && nameDiffers -> DocumentMigrationAction.VERSCHIEBEN_UND_UMBENENNEN
            folderDiffers -> DocumentMigrationAction.VERSCHIEBEN
            nameDiffers -> DocumentMigrationAction.UMBENENNEN
            else -> DocumentMigrationAction.UNVERAENDERT
        }
        return ManagedDocumentDriveReorganizationPlan(action, true)
    }
}
