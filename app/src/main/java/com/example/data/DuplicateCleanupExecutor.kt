package com.example.data

import kotlinx.coroutines.CancellationException

/**
 * Side-effect boundaries for duplicate cleanup. Production adapters must persist journals and
 * perform Drive/index/tombstone/local mutations. The executor itself is deterministic and
 * idempotent: completed phases and files are never repeated.
 */
interface DuplicateCleanupJournalStore {
    suspend fun load(operationId: String): DuplicateCleanupJournal?
    suspend fun save(journal: DuplicateCleanupJournal)
}

interface DuplicateCleanupGateway {
    suspend fun removeNonCanonicalReferences(journal: DuplicateCleanupJournal)
    suspend fun deleteMetadataFile(fileId: String)
    suspend fun deleteMainFile(fileId: String)
    suspend fun purgeLocalRecords(journal: DuplicateCleanupJournal)
}

class DuplicateCleanupExecutor(
    private val journalStore: DuplicateCleanupJournalStore,
    private val gateway: DuplicateCleanupGateway,
    private val now: () -> String
) {
    suspend fun execute(operationId: String): DuplicateCleanupReport {
        var journal = requireNotNull(journalStore.load(operationId)) {
            "Unbekannter Bereinigungsauftrag: $operationId"
        }
        val failures = mutableListOf<DuplicateCleanupFailure>()

        suspend fun persist(
            next: DuplicateCleanupPhase = journal.phase,
            removedMetadataFileIds: Set<String> = journal.removedMetadataFileIds
        ) {
            journal = journal.copy(
                phase = next,
                updatedAt = now(),
                removedMetadataFileIds = removedMetadataFileIds,
                errors = failures.map { it.message }
            )
            journalStore.save(journal)
        }

        if (journal.phase < DuplicateCleanupPhase.CONFIRMED) {
            return DuplicateCleanupReport(operationId, journal.phase, completed = false)
        }

        if (journal.phase < DuplicateCleanupPhase.REFERENCES_REMOVED) {
            try {
                gateway.removeNonCanonicalReferences(journal)
                persist(DuplicateCleanupPhase.REFERENCES_REMOVED)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                failures += DuplicateCleanupFailure(
                    DuplicateCleanupPhase.REFERENCES_REMOVED,
                    journal.mainDriveFileId,
                    e.message ?: e::class.java.simpleName
                )
                persist()
                return DuplicateCleanupReport(operationId, journal.phase, false, failures)
            }
        }

        if (journal.phase < DuplicateCleanupPhase.DRIVE_METADATA_REMOVED) {
            val uniqueMetadataIds = journal.metadataFileIds.filter(String::isNotBlank).distinct()
            for (metadataId in uniqueMetadataIds) {
                if (metadataId in journal.removedMetadataFileIds) continue

                try {
                    gateway.deleteMetadataFile(metadataId)
                } catch (missing: DriveResourceAlreadyMissingException) {
                    // Already absent is equivalent to successfully removed.
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    failures += DuplicateCleanupFailure(
                        DuplicateCleanupPhase.DRIVE_METADATA_REMOVED,
                        metadataId,
                        e.message ?: e::class.java.simpleName
                    )
                    persist()
                    return DuplicateCleanupReport(operationId, journal.phase, false, failures)
                }

                // Persist after every individual success so a later retry cannot repeat it.
                persist(removedMetadataFileIds = journal.removedMetadataFileIds + metadataId)
            }
            persist(DuplicateCleanupPhase.DRIVE_METADATA_REMOVED)
        }

        if (journal.removeWholeGroup && journal.phase < DuplicateCleanupPhase.MAIN_FILE_REMOVED) {
            try {
                gateway.deleteMainFile(journal.mainDriveFileId)
            } catch (missing: DriveResourceAlreadyMissingException) {
                // Idempotent resume.
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                failures += DuplicateCleanupFailure(
                    DuplicateCleanupPhase.MAIN_FILE_REMOVED,
                    journal.mainDriveFileId,
                    e.message ?: e::class.java.simpleName
                )
                persist()
                return DuplicateCleanupReport(operationId, journal.phase, false, failures)
            }
            persist(DuplicateCleanupPhase.MAIN_FILE_REMOVED)
        }

        if (!journal.removeWholeGroup && journal.phase < DuplicateCleanupPhase.MAIN_FILE_REMOVED) {
            // Explicit invariant: merging duplicates must never delete the shared main file.
            persist(DuplicateCleanupPhase.MAIN_FILE_REMOVED)
        }

        if (journal.phase < DuplicateCleanupPhase.LOCAL_PURGED) {
            try {
                gateway.purgeLocalRecords(journal)
                persist(DuplicateCleanupPhase.LOCAL_PURGED)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                failures += DuplicateCleanupFailure(
                    DuplicateCleanupPhase.LOCAL_PURGED,
                    journal.targetInternalIds.joinToString(),
                    e.message ?: e::class.java.simpleName
                )
                persist()
                return DuplicateCleanupReport(operationId, journal.phase, false, failures)
            }
        }

        if (journal.phase < DuplicateCleanupPhase.COMPLETED) {
            persist(DuplicateCleanupPhase.COMPLETED)
        }
        return DuplicateCleanupReport(operationId, DuplicateCleanupPhase.COMPLETED, true)
    }
}

/** Marker used by Drive adapters for HTTP 404 / already deleted resources. */
class DriveResourceAlreadyMissingException(resourceId: String) :
    IllegalStateException("Drive-Ressource bereits entfernt: $resourceId")
