package com.example.data

/** Keep every authored field (including the selected local original), remove only Drive state.
 * New business fields remain part of equality automatically. No JSON/data mutation occurs. */
internal fun Receipt.driveAuthoredState(): Receipt = copy(
    isArchivedToDrive = false, driveFileId = null, driveFolderId = null, driveMetadataFileId = null,
    storedFilename = null, originalMimeType = null, fileSizeBytes = null, syncStatus = "",
    syncError = null, lastSyncedAt = null, driveRevision = null)
