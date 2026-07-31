package com.example.data

/**
 * Minimal local data boundary used by the duplicate cleanup workflow.
 * Keeping this interface small makes the destructive path independently testable.
 */
interface DuplicateReceiptStore {
    suspend fun getAllIncludingDeleted(): List<Receipt>
    suspend fun deleteByRoomId(roomId: Int)
}

class ReceiptRepositoryDuplicateStore(
    private val repository: ReceiptRepository
) : DuplicateReceiptStore {
    override suspend fun getAllIncludingDeleted(): List<Receipt> =
        repository.getAllReceiptsIncludingDeletedList()

    override suspend fun deleteByRoomId(roomId: Int) {
        repository.deleteById(roomId)
    }
}

/**
 * Remote mutations are deliberately separated from local Room deletion.
 * Implementations must remove only the index/tombstone references captured by the confirmed
 * journal. They must never infer additional resources at execution time.
 */
interface DuplicateRemoteCleanupActions {
    suspend fun removeCapturedReferences(journal: DuplicateCleanupJournal)
    suspend fun deleteMetadataFile(fileId: String)
    suspend fun deleteMainFile(fileId: String)
}

/**
 * Production gateway for the state machine. Local rows are resolved again immediately before
 * deletion, so stale previews cannot cause unrelated rows to be purged.
 */
class RepositoryDuplicateCleanupGateway(
    private val receiptStore: DuplicateReceiptStore,
    private val remoteActions: DuplicateRemoteCleanupActions
) : DuplicateCleanupGateway {
    override suspend fun removeNonCanonicalReferences(journal: DuplicateCleanupJournal) {
        // Revalidate the confirmed preview immediately before the first remote mutation.
        validateCurrentSnapshot(journal, receiptStore.getAllIncludingDeleted())
        remoteActions.removeCapturedReferences(journal)
    }

    override suspend fun deleteMetadataFile(fileId: String) {
        remoteActions.deleteMetadataFile(fileId)
    }

    override suspend fun deleteMainFile(fileId: String) {
        remoteActions.deleteMainFile(fileId)
    }

    override suspend fun purgeLocalRecords(journal: DuplicateCleanupJournal) {
        val current = receiptStore.getAllIncludingDeleted()
        val targets = validateCurrentSnapshot(journal, current)
        targets.map { it.id }.distinct().forEach { receiptStore.deleteByRoomId(it) }
    }

    private fun validateCurrentSnapshot(
        journal: DuplicateCleanupJournal,
        current: List<Receipt>
    ): List<Receipt> {
        val usesRoomTargets = journal.targetRoomIds.isNotEmpty()
        val targets = if (usesRoomTargets) {
            current.filter { it.id in journal.targetRoomIds }
        } else {
            // Backward-compatible recovery for journals created before Room IDs were captured.
            current.filter { it.internalId in journal.targetInternalIds }
        }

        if (usesRoomTargets) {
            require(targets.map { it.id }.toSet() == journal.targetRoomIds.toSet()) {
                "Lokale Zielzeilen haben sich seit der Vorschau geändert; sichere Bereinigung abgebrochen."
            }
        } else {
            require(targets.map { it.internalId }.toSet() == journal.targetInternalIds.toSet()) {
                "Lokale Zielbelege haben sich seit der Vorschau geändert; sichere Bereinigung abgebrochen."
            }
        }
        require(targets.all { it.driveFileId == journal.mainDriveFileId }) {
            "Mindestens ein Zielbeleg verweist inzwischen auf eine andere Hauptdatei."
        }

        val targetRoomIds = targets.map { it.id }.toSet()
        val metadataStillReferenced = current.any { receipt ->
            receipt.id !in targetRoomIds &&
                receipt.driveMetadataFileId in journal.metadataFileIds
        }
        require(!metadataStillReferenced) {
            "Mindestens eine geplante Metadatendatei wird inzwischen von einem anderen Beleg verwendet."
        }

        if (!journal.removeWholeGroup) {
            val canonicalId = requireNotNull(journal.canonicalInternalId) {
                "Kanonischer Datensatz fehlt."
            }
            val canonical = current.singleOrNull {
                it.internalId == canonicalId && it.id !in targetRoomIds
            } ?: error("Kanonischer Datensatz wurde nicht eindeutig gefunden.")
            require(canonical.driveFileId == journal.mainDriveFileId) {
                "Der kanonische Datensatz verweist nicht mehr auf die gemeinsame Hauptdatei."
            }
        } else {
            val remainingSameMain = current.filter {
                it.driveFileId == journal.mainDriveFileId && it.id !in targetRoomIds
            }
            require(remainingSameMain.isEmpty()) {
                "Die Hauptdatei wird noch von nicht bestätigten lokalen Belegen verwendet."
            }
        }
        return targets
    }
}

/**
 * Coordinates read-only analysis, explicit confirmation and resumable execution.
 * No operation is executed before its confirmed journal has been durably saved.
 */
class DuplicateCleanupCoordinator(
    private val receiptStore: DuplicateReceiptStore,
    private val journalStore: DuplicateCleanupJournalStore,
    private val executor: DuplicateCleanupExecutor,
    private val now: () -> String
) {
    suspend fun analyze(
        indexEntries: List<ReceiptIndexEntry>,
        tombstones: Map<String, ReceiptTombstone>,
        createdAtByInternalId: Map<String, String?> = emptyMap()
    ): List<DuplicateGroupPreview> = ReceiptDuplicateAnalyzer.analyze(
        receipts = receiptStore.getAllIncludingDeleted(),
        indexEntries = indexEntries,
        tombstones = tombstones,
        createdAtByInternalId = createdAtByInternalId
    )

    suspend fun createMergePreview(preview: DuplicateGroupPreview): DuplicateCleanupJournal {
        val journal = DuplicateCleanupPlanner.planMerge(preview, now())
        journalStore.save(journal)
        return journal
    }

    suspend fun confirmAndExecuteMerge(operationId: String): DuplicateCleanupReport {
        val previewed = requireNotNull(journalStore.load(operationId)) {
            "Unbekannter Bereinigungsauftrag: $operationId"
        }
        require(previewed.phase == DuplicateCleanupPhase.PREVIEWED) {
            "Die Zusammenführung wurde bereits bestätigt oder gestartet."
        }
        val confirmed = DuplicateCleanupPlanner.confirmMerge(previewed).copy(updatedAt = now())
        journalStore.save(confirmed)
        return executor.execute(operationId)
    }

    suspend fun createWholeGroupPreview(preview: DuplicateGroupPreview): DuplicateCleanupJournal {
        val journal = DuplicateCleanupPlanner.planWholeGroupDeletion(preview, now())
        journalStore.save(journal)
        return journal
    }

    suspend fun confirmAndExecuteWholeGroup(
        operationId: String,
        firstConfirmation: Boolean,
        secondConfirmation: Boolean
    ): DuplicateCleanupReport {
        val previewed = requireNotNull(journalStore.load(operationId)) {
            "Unbekannter Bereinigungsauftrag: $operationId"
        }
        require(previewed.phase == DuplicateCleanupPhase.PREVIEWED) {
            "Die Gruppenlöschung wurde bereits bestätigt oder gestartet."
        }
        val confirmed = DuplicateCleanupPlanner.confirmWholeGroupDeletion(
            previewed,
            firstConfirmation,
            secondConfirmation
        ).copy(updatedAt = now())
        journalStore.save(confirmed)
        return executor.execute(operationId)
    }

    suspend fun resume(operationId: String): DuplicateCleanupReport = executor.execute(operationId)
}
