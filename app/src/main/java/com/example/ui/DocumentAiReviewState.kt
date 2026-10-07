package com.example.ui

import com.example.api.ManagedDocumentAiResult
import com.example.api.ManagedDocumentAiField
import com.example.data.ManagedDocument
import com.example.data.DocumentReviewStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class PendingDocumentAiReview(
    val documentId: String,
    val propertyId: String,
    val unitId: String?,
    val revision: Long,
    val result: ManagedDocumentAiResult
) {
    fun matches(document: ManagedDocument): Boolean = documentId == document.documentId &&
        propertyId == document.propertyId && unitId == document.unitId
}

internal data class DocumentAiReviewState(
    val pending: Map<String, PendingDocumentAiReview> = emptyMap(),
    val dismissed: Set<String> = emptySet(),
    val analyzing: Set<String> = emptySet(),
    val confirming: Set<String> = emptySet(),
    val messages: Map<String, String> = emptyMap()
)

/** One immutable UI state; a second document never replaces another document's review. */
internal class DocumentAiReviewStore {
    private val mutableState = MutableStateFlow(DocumentAiReviewState())
    val state = mutableState.asStateFlow()
    private var revision = 0L

    @Synchronized fun beginAnalysis(documentId: String): Boolean {
        if (documentId in state.value.analyzing || documentId in state.value.confirming) return false
        mutableState.update { it.copy(analyzing = it.analyzing + documentId) }
        message(documentId, "KI-Dokumentanalyse läuft …")
        return true
    }

    @Synchronized fun offer(document: ManagedDocument, result: ManagedDocumentAiResult) {
        val review = PendingDocumentAiReview(document.documentId, document.propertyId, document.unitId, ++revision, result)
        mutableState.update { it.copy(pending = it.pending + (document.documentId to review), dismissed = it.dismissed - document.documentId) }
    }

    @Synchronized fun restore(document: ManagedDocument) {
        if (document.documentId in state.value.pending || document.documentId in state.value.analyzing) return
        pendingDocumentAnalysis(document)?.let { offer(document, it) }
    }

    @Synchronized fun beginConfirmation(review: PendingDocumentAiReview): Boolean {
        val current = state.value
        if (current.pending[review.documentId] != review || review.documentId in current.analyzing ||
            review.documentId in current.confirming) return false
        mutableState.update { it.copy(confirming = it.confirming + review.documentId) }
        return true
    }

    fun endAnalysis(documentId: String) { mutableState.update { it.copy(analyzing = it.analyzing - documentId) } }
    fun endConfirmation(documentId: String) { mutableState.update { it.copy(confirming = it.confirming - documentId) } }
    fun message(documentId: String, message: String) { mutableState.update { it.copy(messages = it.messages + (documentId to message)) } }
    fun dismiss(documentId: String) { mutableState.update { it.copy(dismissed = it.dismissed + documentId) } }
    fun reopen(documentId: String) { mutableState.update { it.copy(dismissed = it.dismissed - documentId) } }

    fun remove(documentId: String, expectedRevision: Long? = null) {
        mutableState.update {
            if (expectedRevision != null && it.pending[documentId]?.revision != expectedRevision) it
            else it.copy(pending = it.pending - documentId, dismissed = it.dismissed - documentId)
        }
    }
}

/** Decode only the already persisted pending-analysis format; confirmed values are not proposals. */
internal fun pendingDocumentAnalysis(document: ManagedDocument): ManagedDocumentAiResult? {
    if (document.reviewStatus != DocumentReviewStatus.PRUEFEN.name) return null
    return runCatching {
        val json = org.json.JSONObject(document.extractedFieldsJson)
        val fields = json.getJSONArray("fields")
        ManagedDocumentAiResult(
            documentType = json.getString("documentType"),
            confidence = json.getDouble("confidence"),
            suggestedPropertyId = json.optString("suggestedPropertyId"),
            suggestedUnitId = json.optString("suggestedUnitId"),
            documentDate = json.optString("documentDate"),
            targetArea = json.optString("targetArea"),
            fields = (0 until fields.length()).map { index ->
                val field = fields.getJSONObject(index)
                ManagedDocumentAiField(field.getString("key"), field.getString("label"), field.getString("value"), field.getDouble("confidence"), field.optString("sourcePage"))
            }
        )
    }.getOrNull()
}
