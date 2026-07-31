package com.example.data

import com.example.api.GoogleDriveClient

/**
 * Production reference mutator. It removes only references captured in the confirmed journal.
 * Missing tombstones are treated as already removed; ambiguous Drive failures abort the workflow.
 */
class DrivePersistenceDuplicateReferenceMutator(
    private val repository: DrivePersistenceRepository,
    private val accessTokenProvider: suspend () -> String,
    private val configProvider: suspend () -> DriveAppConfig
) : DuplicateReferenceMutator {
    override suspend fun removeIndexReferences(
        internalIds: Set<String>,
        mainDriveFileId: String
    ) {
        require(internalIds.none(String::isBlank)) { "Leere internalId im Bereinigungsauftrag." }
        require(mainDriveFileId.isNotBlank()) { "Hauptdatei-ID fehlt." }
        val token = accessTokenProvider().trim()
        require(token.isNotEmpty()) { "Google-Drive-Zugriffstoken fehlt." }
        val config = configProvider()
        val current = repository.getReceiptIndexFromDrive(token, config)
        val retained = current.filterNot { entry ->
            entry.internalId in internalIds && entry.mainDriveFileId == mainDriveFileId
        }
        if (retained.size == current.size) return
        check(repository.replaceReceiptIndexEntries(token, config, retained)) {
            "receipt-index.json konnte nicht eindeutig aktualisiert werden."
        }
    }

    override suspend fun removeTombstones(internalIds: Set<String>) {
        if (internalIds.isEmpty()) return
        require(internalIds.none(String::isBlank)) { "Leere Tombstone-internalId im Bereinigungsauftrag." }
        val token = accessTokenProvider().trim()
        require(token.isNotEmpty()) { "Google-Drive-Zugriffstoken fehlt." }
        val config = configProvider()
        val folderId = repository.getOrCreateDeletionsFolder(token, config)
            ?: error("Drive-Papierkorb-Ordner konnte nicht bestimmt werden.")
        internalIds.sorted().forEach { internalId ->
            val fileId = GoogleDriveClient.findFileByName(token, folderId, "$internalId.json")
                ?: return@forEach
            check(GoogleDriveClient.deleteFile(token, fileId)) {
                "Tombstone konnte nicht eindeutig gelöscht werden: $fileId"
            }
        }
    }
}
