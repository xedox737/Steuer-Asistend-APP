package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.api.AiProviderSettings
import com.example.api.GeminiClient
import com.example.api.ManagedDocumentAiResult
import com.example.api.OpenAiClient
import com.example.api.ReceiptAnalysisProvider
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

sealed class ManagedDocumentImportResult {
    data class Imported(val document: ManagedDocument) : ManagedDocumentImportResult()
    data class ExactDuplicate(val existing: ManagedDocument) : ManagedDocumentImportResult()
    data class PossibleDuplicate(val candidate: ManagedDocument, val existing: ManagedDocument) : ManagedDocumentImportResult()
    data class Error(val message: String) : ManagedDocumentImportResult()
}

class ManagedDocumentService(
    private val context: Context,
    private val repository: ReceiptRepository,
    private val ocrService: DocumentOcrService = DocumentOcrService()
) {
    suspend fun prepareImport(uri: Uri, property: PropertyMetadata, unitId: String? = null): ManagedDocumentImportResult {
        return try {
        val resolver = context.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return ManagedDocumentImportResult.Error("Die ausgewählte Datei konnte nicht gelesen werden.")
        if (bytes.isEmpty()) return ManagedDocumentImportResult.Error("Die ausgewählte Datei ist leer.")
        val originalName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }.orEmpty().ifBlank { "Dokument" }
        val mime = resolver.getType(uri).orEmpty().ifBlank { mimeFromName(originalName) }
        if (!isSupported(mime, originalName)) return ManagedDocumentImportResult.Error("Dieses Dateiformat wird nicht unterstützt.")
        val now = Instant.now().toString()
        val id = UUID.randomUUID().toString()
        val extension = originalName.substringAfterLast('.', "bin").lowercase()
        val localDir = File(context.filesDir, "managed_documents").apply { mkdirs() }
        val candidate = ManagedDocument(
            documentId = id,
            propertyId = property.propertyId,
            unitId = unitId,
            documentDate = LocalDate.now().toString(),
            title = originalName.substringBeforeLast('.'),
            originalFilename = originalName,
            storedFilename = DocumentFilenameGenerator.document(ManagedDocumentType.SONSTIGES, LocalDate.now().toString(), originalName.substringBeforeLast('.'), extension, id),
            mimeType = mime,
            localUri = File(localDir, "$id.$extension").absolutePath,
            sha256 = StableDocumentIdentity.sha256(bytes),
            fileSizeBytes = bytes.size.toLong(),
            createdAt = now,
            updatedAt = now,
            source = DocumentSource.DATEIIMPORT.name
        )
        val duplicate = DocumentDuplicatePolicy.detect(candidate, repository.getAllManagedDocuments())
        when (duplicate.kind) {
            DocumentDuplicateKind.EXACT -> ManagedDocumentImportResult.ExactDuplicate(repository.getManagedDocument(requireNotNull(duplicate.existingDocumentId))!!)
            DocumentDuplicateKind.POSSIBLE -> ManagedDocumentImportResult.PossibleDuplicate(candidate, repository.getManagedDocument(requireNotNull(duplicate.existingDocumentId))!!)
            DocumentDuplicateKind.NONE -> persistPrepared(candidate, bytes)
        }
        } catch (e: Exception) {
            ManagedDocumentImportResult.Error(e.message ?: "Dokumentimport fehlgeschlagen.")
        }
    }

    suspend fun persistPossibleDuplicate(candidate: ManagedDocument, uri: Uri): ManagedDocumentImportResult {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return ManagedDocumentImportResult.Error("Die ausgewählte Datei konnte nicht erneut gelesen werden.")
            val id = UUID.randomUUID().toString()
            val extension = candidate.localUri.substringAfterLast('.', "bin")
            persistPrepared(candidate.copy(documentId = id, localUri = File(context.filesDir, "managed_documents/$id.$extension").absolutePath), bytes)
        } catch (e: Exception) {
            ManagedDocumentImportResult.Error(e.message ?: "Dokumentimport fehlgeschlagen.")
        }
    }

    suspend fun runOcr(documentId: String): ManagedDocument? {
        val document = repository.getManagedDocument(documentId) ?: return null
        repository.upsertManagedDocument(document.copy(ocrStatus = DocumentProcessingStatus.LAEUFT.name, updatedAt = Instant.now().toString()))
        val result = ocrService.extract(File(document.localUri), document.mimeType)
        val updated = document.copy(ocrStatus = result.status.name, ocrText = result.text, updatedAt = Instant.now().toString())
        repository.upsertManagedDocument(updated)
        return updated
    }

    suspend fun analyze(documentId: String, property: PropertyMetadata, units: List<com.example.ui.WohneinheitStatus>): ManagedDocumentAiResult? {
        val document = repository.getManagedDocument(documentId) ?: return null
        val withText = if (document.ocrStatus != DocumentProcessingStatus.ERFOLGREICH.name) runOcr(documentId) ?: document else document
        repository.upsertManagedDocument(withText.copy(aiAnalysisStatus = DocumentProcessingStatus.LAEUFT.name))
        val state = AiProviderSettings.loadState(context)
        val contextText = buildString {
            append("Property ${property.propertyId}: ${property.name}, ${property.adresse}\n")
            units.forEach { append("Unit ${it.unitId}: ${it.name}, ${it.label}\n") }
        }
        return try {
            val result = when (state.provider) {
                ReceiptAnalysisProvider.GEMINI -> {
                    val key = AiProviderSettings.getGeminiKey(context)
                    GeminiClient.analyzeManagedDocument(withText.ocrText, propertyContext = contextText, apiKeyOverride = key)
                }
                ReceiptAnalysisProvider.OPENAI -> {
                    val key = AiProviderSettings.getOpenAiKey(context) ?: return null
                    try { OpenAiClient.analyzeManagedDocument(key, state.openAiModel, withText.ocrText, propertyContext = contextText) }
                    finally { key.fill('\u0000') }
                }
            }
            val updated = withText.copy(
                aiAnalysisStatus = if (result == null) DocumentProcessingStatus.FEHLGESCHLAGEN.name else DocumentProcessingStatus.ERFOLGREICH.name,
                aiConfidence = result?.confidence ?: 0.0,
                extractedFieldsJson = result?.toPendingJson().orEmpty(),
                reviewStatus = DocumentReviewStatus.PRUEFEN.name,
                updatedAt = Instant.now().toString()
            )
            repository.upsertManagedDocument(updated)
            result
        } catch (e: Exception) {
            repository.upsertManagedDocument(withText.copy(aiAnalysisStatus = DocumentProcessingStatus.FEHLGESCHLAGEN.name, updatedAt = Instant.now().toString()))
            null
        }
    }

    suspend fun confirmReview(document: ManagedDocument, type: ManagedDocumentType, date: String, unitId: String?, fieldsJson: String): ManagedDocument {
        val extension = document.storedFilename.substringAfterLast('.', document.originalFilename.substringAfterLast('.', "bin"))
        val updated = document.copy(
            documentType = type.name,
            documentCategory = DocumentDrivePathResolver.route(document.propertyId, "", "", type, date, unitId).segments.drop(1).joinToString("/"),
            documentDate = date,
            unitId = unitId,
            storedFilename = DocumentFilenameGenerator.document(type, date, document.title, extension, document.documentId),
            extractedFieldsJson = fieldsJson,
            reviewStatus = DocumentReviewStatus.GEPRUEFT.name,
            updatedAt = Instant.now().toString()
        )
        repository.upsertManagedDocument(updated)
        return updated
    }

    private suspend fun persistPrepared(candidate: ManagedDocument, bytes: ByteArray): ManagedDocumentImportResult {
        val file = File(candidate.localUri)
        file.parentFile?.mkdirs()
        file.outputStream().use { it.write(bytes) }
        repository.upsertManagedDocument(candidate)
        return ManagedDocumentImportResult.Imported(candidate)
    }

    private fun isSupported(mime: String, name: String): Boolean = mime.startsWith("image/") || mime == "application/pdf" || mime.startsWith("text/") || name.substringAfterLast('.', "").lowercase() in setOf("pdf", "jpg", "jpeg", "png", "webp", "txt")
    private fun mimeFromName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"; "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; "txt" -> "text/plain"; else -> "application/octet-stream"
    }

    private fun ManagedDocumentAiResult.toPendingJson(): String = JSONObject().apply {
        put("documentType", documentType); put("confidence", confidence)
        put("suggestedPropertyId", suggestedPropertyId); put("suggestedUnitId", suggestedUnitId)
        put("documentDate", documentDate); put("targetArea", targetArea)
        put("fields", JSONArray().apply { fields.forEach { field -> put(JSONObject().apply {
            put("key", field.key); put("label", field.label); put("value", field.value)
            put("confidence", field.confidence); put("sourcePage", field.sourcePage)
            put("decision", DocumentFieldDecision.AUSSTEHEND.name)
        }) } })
    }.toString()
}
