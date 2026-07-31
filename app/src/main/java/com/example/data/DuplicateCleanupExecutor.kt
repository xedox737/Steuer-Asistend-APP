package com.example.data

/**
 * Side-effect boundaries for duplicate cleanup. Production adapters must persist journals and
 * perform Drive/index/tombstone/local mutations. The executor itself is deterministic and
 * idempotent: completed phases are never repeated.
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

        suspend fun persist(next: DuplicateCleanupPhase) {
            journal = journal.copy(phase = next, updatedAt = now(), errors = failures.map { it.message })
            journalStore.save(journal)
        }

        if (journal.phase < DuplicateCleanupPhase.CONFIRMED) {
            return DuplicateCleanupReport(operationId, journal.phase, completed = false)
        }

        if (journal.phase < DuplicateCleanupPhase.REFERENCES_REMOVED) {
            try {
                gateway.removeNonCanonicalReferences(journal)
                persist(DuplicateCleanupPhase.REFERENCES_REMOVED)
            } catch (t: Throwable) {
                failures += DuplicateCleanupFailure(
                    DuplicateCleanupPhase.REFERENCES_REMOVED,
                    journal.mainDriveFileId,
                    t.message ?: t::class.java.simpleName
                )
                persist(journal.phase)
                return DuplicateCleanupReport(operationId, journal.phase, false, failures)
            }
        }

        if (journal.phase < DuplicateCleanupPhase.DRIVE_METADATA_REMOVED) {
            for (metadataId in journal.metadataFileIds.distinct()) {
                try {
                    gateway.deleteMetadataFile(metadataId)
                } catch (missing: DriveResourceAlreadyMissingException) {
                    // Idempotent resume: already absent is equivalent to successfully removed.
                } catch (t: Throwable) {
                    failures += DuplicateCleanupFailure(
                        DuplicateCleanupPhase.DRIVE_METADATA_REMOVED,
                        metadataId,
                        t.message ?: t::class.java.simpleName
                    )
                    persist(journal.phase)
                    return DuplicateCleanupReport(operationId, journal.phase, false, failures)
                }
            }
            persist(DuplicateCleanupPhase.DRIVE_METADATA_REMOVED)
        }

        if (journal.removeWholeGroup && journal.phase < DuplicateCleanupPhase.MAIN_FILE_REMOVED) {
            try {
                gateway.deleteMainFile(journal.mainDriveFileId)
            } catch (missing: DriveResourceAlreadyMissingException) {
                // Idempotent resume.
            } catch (t: Throwable) {
                failures += DuplicateCleanupFailure(
                    DuplicateCleanupPhase.MAIN_FILE_REMOVED,
                    journal.mainDriveFileId,
                    t.message ?: t::class.java.simpleName
                )
                persist(journal.phase)
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
            } catch (t: Throwable) {
                failures += DuplicateCleanupFailure(
                    DuplicateCleanupPhase.LOCAL_PURGED,
                    journal.targetInternalIds.joinToString(),
                    t.message ?: t::class.java.simpleName
                )
                persist(journal.phase)
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
