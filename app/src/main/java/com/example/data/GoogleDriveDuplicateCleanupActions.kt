package com.example.data

import com.example.api.GoogleDriveClient

/**
 * Captured reference mutation boundary. Implementations must remove only the explicitly captured
 * non-canonical index and tombstone references from a confirmed journal.
 */
interface DuplicateReferenceMutator {
    suspend fun removeIndexReferences(internalIds: Set<String>, mainDriveFileId: String)
    suspend fun removeTombstones(internalIds: Set<String>)
}

/**
 * Production Drive actions used by the duplicate cleanup state machine.
 * A failed Drive delete is surfaced as an error so local rows are never removed after an
 * ambiguous remote result.
 */
class GoogleDriveDuplicateCleanupActions(
    private val accessTokenProvider: suspend () -> String,
    private val referenceMutator: DuplicateReferenceMutator,
    private val deleteFile: suspend (String, String) -> Boolean = { token, fileId ->
        GoogleDriveClient.deleteFile(token, fileId)
    }
) : DuplicateRemoteCleanupActions {

    override suspend fun removeCapturedReferences(journal: DuplicateCleanupJournal) {
        val targets = journal.targetInternalIds.filter(String::isNotBlank).toSet()
        require(targets.isNotEmpty()) { "Keine bestätigten Zielreferenzen vorhanden." }

        if (!journal.removeWholeGroup) {
            require(journal.canonicalInternalId !in targets) {
                "Die kanonische Referenz darf nicht entfernt werden."
            }
        }

        referenceMutator.removeIndexReferences(targets, journal.mainDriveFileId)
        referenceMutator.removeTombstones(journal.tombstoneInternalIds.toSet())
    }

    override suspend fun deleteMetadataFile(fileId: String) {
        deleteConfirmedFile(fileId, "Metadatendatei")
    }

    override suspend fun deleteMainFile(fileId: String) {
        deleteConfirmedFile(fileId, "Hauptdatei")
    }

    private suspend fun deleteConfirmedFile(fileId: String, kind: String) {
        require(fileId.isNotBlank()) { "$kind ohne Drive-Datei-ID." }
        val token = accessTokenProvider().trim()
        require(token.isNotEmpty()) { "Google-Drive-Zugriffstoken fehlt." }
        check(deleteFile(token, fileId)) {
            "$kind konnte in Google Drive nicht eindeutig gelöscht werden: $fileId"
        }
    }
}
