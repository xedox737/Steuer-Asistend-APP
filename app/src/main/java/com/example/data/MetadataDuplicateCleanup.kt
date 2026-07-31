package com.example.data

/**
 * Explicit plan for removing legacy receipt metadata duplicates.
 *
 * The file referenced by receipt-index.json is always retained. Creating a plan is read-only;
 * execution additionally requires an explicit confirmation.
 */
data class MetadataDuplicateCleanupPlan(
    val internalId: String,
    val activeMetadataFileId: String,
    val orphanMetadataFileIds: List<String>
)

data class MetadataDuplicateCleanupResult(
    val internalId: String,
    val activeMetadataFileId: String,
    val deletedMetadataFileIds: List<String>,
    val failures: List<String>
) {
    val completed: Boolean get() = failures.isEmpty()
}

fun interface MetadataDuplicateFileDeleter {
    suspend fun delete(fileId: String): Boolean
}

object MetadataDuplicateCleanupPlanner {
    fun plan(group: MetadataDuplicateGroup): MetadataDuplicateCleanupPlan {
        require(group.referencedMetadataFileIds.size == 1) {
            "Mehrdeutige Referenzen in receipt-index.json müssen zuerst manuell geprüft werden."
        }
        val activeId = group.referencedFileIdInIndex?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException(
                "Ohne eindeutige Referenz aus receipt-index.json darf keine Metadatendatei gelöscht werden."
            )
        require(group.files.any { it.driveId == activeId && it.isReferencedInIndex }) {
            "Die aktive Metadatendatei aus receipt-index.json ist in der Drive-Liste nicht eindeutig vorhanden."
        }

        val orphanIds = group.files
            .map { it.driveId }
            .filter(String::isNotBlank)
            .filterNot { it == activeId }
            .distinct()
            .sorted()

        require(orphanIds.isNotEmpty()) { "Keine verwaisten Metadaten-Dubletten vorhanden." }
        return MetadataDuplicateCleanupPlan(
            internalId = group.internalId,
            activeMetadataFileId = activeId,
            orphanMetadataFileIds = orphanIds
        )
    }
}

class MetadataDuplicateCleanupExecutor(
    private val deleter: MetadataDuplicateFileDeleter,
    private val audit: suspend (MetadataDuplicateCleanupResult) -> Unit = {}
) {
    suspend fun execute(
        plan: MetadataDuplicateCleanupPlan,
        explicitlyConfirmed: Boolean
    ): MetadataDuplicateCleanupResult {
        require(explicitlyConfirmed) {
            "Die Bereinigung benötigt eine ausdrückliche Nutzerbestätigung."
        }
        require(plan.activeMetadataFileId.isNotBlank())
        require(plan.activeMetadataFileId !in plan.orphanMetadataFileIds) {
            "Die aktive metadataFileId darf niemals gelöscht werden."
        }

        val deleted = mutableListOf<String>()
        val failures = mutableListOf<String>()
        for (fileId in plan.orphanMetadataFileIds.distinct()) {
            if (fileId.isBlank() || fileId == plan.activeMetadataFileId) {
                failures += "Ungültiges Löschziel: $fileId"
                continue
            }
            try {
                if (deleter.delete(fileId)) {
                    deleted += fileId
                } else {
                    failures += "Metadatendatei konnte nicht eindeutig gelöscht werden: $fileId"
                }
            } catch (e: Exception) {
                failures += "Metadatendatei $fileId: ${e.message ?: e::class.java.simpleName}"
            }
        }

        val result = MetadataDuplicateCleanupResult(
            internalId = plan.internalId,
            activeMetadataFileId = plan.activeMetadataFileId,
            deletedMetadataFileIds = deleted,
            failures = failures
        )
        audit(result)
        return result
    }
}
