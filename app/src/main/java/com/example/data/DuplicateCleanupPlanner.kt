package com.example.data

import java.util.UUID

/**
 * Builds explicit, reviewable cleanup journals from a read-only duplicate preview.
 * This class performs no mutation and never schedules the shared main file for merge operations.
 */
object DuplicateCleanupPlanner {
    fun planMerge(
        preview: DuplicateGroupPreview,
        now: String,
        operationId: String = UUID.randomUUID().toString()
    ): DuplicateCleanupJournal {
        require(preview.duplicatesToRemove.isNotEmpty()) { "Keine Dubletten zum Zusammenführen vorhanden." }
        require(!preview.mainFileWillBeDeleted) { "Eine Zusammenführung darf die Hauptdatei nicht löschen." }

        return DuplicateCleanupJournal(
            operationId = operationId,
            mainDriveFileId = preview.mainDriveFileId,
            canonicalInternalId = preview.canonical.internalId,
            targetInternalIds = preview.duplicatesToRemove
                .map { it.internalId }
                .filterNot { it == preview.canonical.internalId }
                .distinct(),
            metadataFileIds = preview.metadataPlan.orphanMetadataFileIds.toList().sorted(),
            tombstoneInternalIds = preview.duplicatesToRemove
                .filter { it.tombstone != null }
                .map { it.internalId }
                .filterNot { it == preview.canonical.internalId }
                .distinct(),
            removeWholeGroup = false,
            phase = DuplicateCleanupPhase.PREVIEWED,
            createdAt = now,
            updatedAt = now,
            targetRoomIds = preview.duplicatesToRemove.map { it.roomId }.distinct()
        )
    }

    fun planWholeGroupDeletion(
        preview: DuplicateGroupPreview,
        now: String,
        operationId: String = UUID.randomUUID().toString()
    ): DuplicateCleanupJournal {
        val allRecords = listOf(preview.canonical) + preview.duplicatesToRemove
        val metadataIds = allRecords.mapNotNull { it.metadataFileId?.takeIf(String::isNotBlank) }

        return DuplicateCleanupJournal(
            operationId = operationId,
            mainDriveFileId = preview.mainDriveFileId,
            canonicalInternalId = null,
            targetInternalIds = allRecords.map { it.internalId }.distinct(),
            metadataFileIds = metadataIds.distinct().sorted(),
            tombstoneInternalIds = allRecords
                .filter { it.tombstone != null }
                .map { it.internalId }
                .distinct(),
            removeWholeGroup = true,
            phase = DuplicateCleanupPhase.PREVIEWED,
            createdAt = now,
            updatedAt = now,
            targetRoomIds = allRecords.map { it.roomId }.distinct()
        )
    }

    fun confirmMerge(journal: DuplicateCleanupJournal): DuplicateCleanupJournal {
        require(!journal.removeWholeGroup) { "Falscher Bestätigungsablauf für Gruppenlöschung." }
        require(journal.canonicalInternalId?.isNotBlank() == true) { "Kanonischer Datensatz fehlt." }
        return journal.copy(phase = DuplicateCleanupPhase.CONFIRMED)
    }

    fun confirmWholeGroupDeletion(
        journal: DuplicateCleanupJournal,
        firstConfirmation: Boolean,
        secondConfirmation: Boolean
    ): DuplicateCleanupJournal {
        require(journal.removeWholeGroup) { "Falscher Bestätigungsablauf für Zusammenführung." }
        require(firstConfirmation && secondConfirmation) {
            "Die vollständige Gruppenlöschung benötigt zwei ausdrückliche Bestätigungen."
        }
        return journal.copy(phase = DuplicateCleanupPhase.CONFIRMED)
    }
}
