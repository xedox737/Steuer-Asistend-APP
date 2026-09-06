package com.example.data

import android.content.Context
import com.example.api.DriveFileResult
import com.example.api.DriveManagedFile
import com.example.api.GoogleDriveClient

/** Narrow seam for exercising the production ManagedDocument sync without a real Drive account. */
interface ManagedDocumentDriveGateway {
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
