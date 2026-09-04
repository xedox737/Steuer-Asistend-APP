package com.example.api

import com.example.data.DocumentFieldDecision
import com.example.data.DocumentFieldProposal
import com.example.data.ManagedDocumentType
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ManagedDocumentAiField(
    @Json(name = "key") val key: String = "",
    @Json(name = "label") val label: String = "",
    @Json(name = "value") val value: String = "",
    @Json(name = "confidence") val confidence: Double = 0.0,
    @Json(name = "sourcePage") val sourcePage: String = ""
)

@JsonClass(generateAdapter = true)
data class ManagedDocumentAiResult(
    @Json(name = "documentType") val documentType: String = ManagedDocumentType.SONSTIGES.name,
    @Json(name = "confidence") val confidence: Double = 0.0,
    @Json(name = "suggestedPropertyId") val suggestedPropertyId: String = "",
    @Json(name = "suggestedUnitId") val suggestedUnitId: String = "",
    @Json(name = "documentDate") val documentDate: String = "",
    @Json(name = "targetArea") val targetArea: String = "",
    @Json(name = "fields") val fields: List<ManagedDocumentAiField> = emptyList()
) {
    fun reviewFields(currentValues: Map<String, String>): List<DocumentFieldProposal> = fields.map {
        DocumentFieldProposal(
            key = it.key,
            label = it.label.ifBlank { it.key },
            detectedValue = it.value,
            currentValue = currentValues[it.key].orEmpty(),
            confidence = it.confidence.coerceIn(0.0, 1.0),
            sourcePage = it.sourcePage,
            decision = DocumentFieldDecision.AUSSTEHEND
        )
    }
}

object ManagedDocumentAiRules {
    val types = ManagedDocumentType.entries.map { it.name }.toSet()

    fun validate(result: ManagedDocumentAiResult): ManagedDocumentAiResult {
        val safeType = result.documentType.takeIf(types::contains) ?: ManagedDocumentType.SONSTIGES.name
        return result.copy(
            documentType = safeType,
            confidence = result.confidence.coerceIn(0.0, 1.0),
            fields = result.fields.filter { it.key.isNotBlank() && it.value.isNotBlank() }
                .map { it.copy(confidence = it.confidence.coerceIn(0.0, 1.0)) }
        )
    }
}
