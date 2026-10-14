package com.example.data

import android.content.Context
import com.example.api.DriveFileResult
import com.example.api.DriveManagedFile
import com.example.api.GoogleDriveClient

/** Narrow seam for exercising the production ManagedDocument sync without a real Drive account. */
interface ManagedDocumentDriveGateway {
    // The existing Drive seam also covers receipt JSON contracts and restore reads.
    suspend fun findJsonFile(accessToken: String, folderId: String, entityType: String): com.example.api.DriveFile? =
        GoogleDriveClient.findFileByAppProperty(accessToken, folderId, entityType)

    suspend fun downloadJson(accessToken: String, fileId: String): String =
        GoogleDriveClient.downloadJson(accessToken, fileId)

    suspend fun findReceiptJsonFile(accessToken: String, folderId: String, internalId: String): String? =
        GoogleDriveClient.findMetadataFileByProperties(accessToken, folderId, internalId)?.id
            ?: GoogleDriveClient.findFileByName(accessToken, folderId, "$internalId.json")

    suspend fun writeReceiptJson(accessToken: String, fileId: String?, folderId: String,
        filename: String, json: String, appProperties: Map<String, String>): DriveFileResult =
        if (fileId != null) GoogleDriveClient.updateJson(accessToken, fileId, json)
        else GoogleDriveClient.uploadJson(accessToken, folderId, filename, json, appProperties)

    suspend fun tombstoneJsonFiles(accessToken: String, systemFolderId: String): List<String> {
        val folder = GoogleDriveClient.getOrCreateFolder(accessToken, "deletions", systemFolderId) ?: return emptyList()
        return GoogleDriveClient.searchFiles(accessToken, "'$folder' in parents and mimeType = 'application/json' and trashed = false")
            .mapNotNull { runCatching { GoogleDriveClient.downloadJson(accessToken, it.id) }.getOrNull() }
    }

    suspend fun getOrCreateFolder(
        accessToken: String,
        folderName: String,
        parentId: String?,
        canonicalPathKey: String?,
        systemFolderId: String?
    ): String?

    suspend fun getFileMetadata(accessToken: String, fileId: String): DriveManagedFile?
    suspend fun downloadFileBytes(accessToken: String, fileId: String): ByteArray?
    suspend fun moveAndRenameFile(
        accessToken: String,
        fileId: String,
        targetParentId: String,
        oldParentIds: List<String>,
        targetFilename: String
    ): Boolean

    suspend fun uploadFile(
        accessToken: String,
        folderId: String,
        filename: String,
        mimeType: String,
        bytes: ByteArray,
        appProperties: Map<String, String>
    ): String?

    suspend fun upsertJson(
        accessToken: String,
        folderId: String,
        entityType: String,
        filename: String,
        json: String
    ): DriveFileResult
}

class GoogleManagedDocumentDriveGateway(private val context: Context) : ManagedDocumentDriveGateway {
    override suspend fun getOrCreateFolder(
        accessToken: String,
        folderName: String,
        parentId: String?,
        canonicalPathKey: String?,
        systemFolderId: String?
    ): String? = GoogleDriveClient.getOrCreateFolder(
        accessToken,
        folderName,
        parentId,
        canonicalPathKey,
        systemFolderId,
        context
    )

    override suspend fun getFileMetadata(accessToken: String, fileId: String): DriveManagedFile? =
        GoogleDriveClient.getFileMetadata(accessToken, fileId)

    override suspend fun downloadFileBytes(accessToken: String, fileId: String): ByteArray? =
        GoogleDriveClient.downloadFileBytes(accessToken, fileId)

    override suspend fun moveAndRenameFile(
        accessToken: String,
        fileId: String,
        targetParentId: String,
        oldParentIds: List<String>,
        targetFilename: String
    ): Boolean = GoogleDriveClient.moveAndRenameFile(
        accessToken,
        fileId,
        targetParentId,
        oldParentIds,
        targetFilename
    )

    override suspend fun uploadFile(
        accessToken: String,
        folderId: String,
        filename: String,
        mimeType: String,
        bytes: ByteArray,
        appProperties: Map<String, String>
    ): String? = GoogleDriveClient.uploadFile(
        accessToken,
        folderId,
        filename,
        mimeType,
        bytes,
        appProperties = appProperties
    )

    override suspend fun upsertJson(
        accessToken: String,
        folderId: String,
        entityType: String,
        filename: String,
        json: String
    ): DriveFileResult {
        val existing = GoogleDriveClient.findFileByAppProperty(accessToken, folderId, entityType)
        return if (existing != null) {
            GoogleDriveClient.updateJson(accessToken, existing.id, json)
        } else {
            GoogleDriveClient.uploadJson(
                accessToken,
                folderId,
                filename,
                json,
                mapOf(
                    "appName" to "ImmobilienBelegApp",
                    "entityType" to entityType,
                    "schemaVersion" to "1"
                )
            )
        }
    }
}
