package com.example.data

import com.example.api.GoogleDriveClient

interface DuplicateReferenceDriveStore {
    suspend fun loadIndex(token: String, config: DriveAppConfig): List<ReceiptIndexEntry>
    suspend fun replaceIndex(
        token: String,
        config: DriveAppConfig,
        entries: List<ReceiptIndexEntry>
    ): Boolean
    suspend fun findTombstoneFile(
        token: String,
        config: DriveAppConfig,
        internalId: String
    ): String?
    suspend fun deleteFile(token: String, fileId: String): Boolean
}

class RepositoryDuplicateReferenceDriveStore(
    private val repository: DrivePersistenceRepository
) : DuplicateReferenceDriveStore {
    override suspend fun loadIndex(
        token: String,
        config: DriveAppConfig
    ): List<ReceiptIndexEntry> = repository.getReceiptIndexFromDrive(token, config)

    override suspend fun replaceIndex(
        token: String,
        config: DriveAppConfig,
        entries: List<ReceiptIndexEntry>
    ): Boolean = repository.replaceReceiptIndexEntries(token, config, entries)

    override suspend fun findTombstoneFile(
        token: String,
        config: DriveAppConfig,
        internalId: String
    ): String? {
        val folderId = repository.getOrCreateDeletionsFolder(token, config) ?: return null
        return GoogleDriveClient.findFileByName(token, folderId, "$internalId.json")
    }

    override suspend fun deleteFile(token: String, fileId: String): Boolean =
        GoogleDriveClient.deleteFile(token, fileId)
}

/**
 * Production reference mutator. It removes only references captured in the confirmed journal.
 * Missing tombstones are treated as already removed; ambiguous Drive failures abort the workflow.
 */
class DrivePersistenceDuplicateReferenceMutator(
    private val store: DuplicateReferenceDriveStore,
    private val accessTokenProvider: suspend () -> String,
    private val configProvider: suspend () -> DriveAppConfig
) : DuplicateReferenceMutator {
    constructor(
        repository: DrivePersistenceRepository,
        accessTokenProvider: suspend () -> String,
        configProvider: suspend () -> DriveAppConfig
    ) : this(
        RepositoryDuplicateReferenceDriveStore(repository),
        accessTokenProvider,
        configProvider
    )

    override suspend fun removeIndexReferences(
        internalIds: Set<String>,
        mainDriveFileId: String
    ) {
        require(internalIds.none(String::isBlank)) { "Leere internalId im Bereinigungsauftrag." }
        require(mainDriveFileId.isNotBlank()) { "Hauptdatei-ID fehlt." }
        val token = accessTokenProvider().trim()
        require(token.isNotEmpty()) { "Google-Drive-Zugriffstoken fehlt." }
        val config = configProvider()
        val current = store.loadIndex(token, config)
        val retained = current.filterNot { entry ->
            entry.internalId in internalIds && entry.mainDriveFileId == mainDriveFileId
        }
        if (retained.size == current.size) return
        check(store.replaceIndex(token, config, retained)) {
            "receipt-index.json konnte nicht eindeutig aktualisiert werden."
        }
    }

    override suspend fun removeTombstones(internalIds: Set<String>) {
        if (internalIds.isEmpty()) return
        require(internalIds.none(String::isBlank)) { "Leere Tombstone-internalId im Bereinigungsauftrag." }
        val token = accessTokenProvider().trim()
        require(token.isNotEmpty()) { "Google-Drive-Zugriffstoken fehlt." }
        val config = configProvider()
        internalIds.sorted().forEach { internalId ->
            val fileId = store.findTombstoneFile(token, config, internalId)
                ?: return@forEach
            check(store.deleteFile(token, fileId)) {
                "Tombstone konnte nicht eindeutig gelöscht werden: $fileId"
            }
        }
    }
}
