package com.example.data

import android.content.Context
import androidx.room.withTransaction
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Reuses a verified original, while keeping accounting review explicitly separate. */
internal class ManagedDocumentBookingPreparation(
    private val context: Context,
    private val database: AppDatabase,
    private val repository: ReceiptRepository
) {
    suspend fun prepare(documentId: String): Receipt = database.withTransaction {
        val document = requireNotNull(database.managedDocumentDao().getById(documentId)) { "Dokument nicht gefunden." }
        val property = requireNotNull(database.propertyDao().getPropertyByPropertyId(document.propertyId)) { "Immobilie nicht gefunden." }
        val unitId = document.unitId.orEmpty()
        val units = UnitIdentity.read(property.wohneinheiten, property.propertyId,
            context.getSharedPreferences("wohneinheiten_prefs", Context.MODE_PRIVATE)).entries
        require(unitId.isBlank() || units.any { it.unitId == unitId }) { "Die Wohneinheit gehört nicht zur Immobilie." }
        val original = File(document.localUri.removePrefix("file://"))
        require(original.isFile && original.length() > 0 && document.sha256.isNotBlank() &&
            com.example.util.ReceiptManifestService.calculateSha256(original) == document.sha256) {
            "Das Original fehlt oder wurde verändert. Bitte zuerst das Original wiederherstellen."
        }
        val existing = database.managedDocumentDao().getAll()
            .filter { it.sha256 == document.sha256 && !it.receiptInternalId.isNullOrBlank() }
            .mapNotNull { database.receiptDao().getReceiptByInternalId(it.receiptInternalId!!) }
            .firstOrNull { it.deletionStatus == "ACTIVE" }
        if (existing != null) {
            require(existing.propertyId == document.propertyId && existing.unitId == unitId) {
                "Dieses Original ist bereits einem Buchungsbeleg mit anderer Zuordnung zugeordnet. Bitte den vorhandenen Beleg prüfen."
            }
            return@withTransaction existing
        }
        LocalDate.parse(document.documentDate)
        val prepared = ReceiptOriginalStorage(context).importOriginal(android.net.Uri.fromFile(original))
        val draft = Receipt(
            internalId = UUID.randomUUID().toString(), aussteller = document.title,
            datum = document.documentDate, uhrzeit = "", bruttobetrag = 0.0,
            hauptkategorie = "", unterkategorie = "", kontoNr = "",
            beschreibung = "Aus der Dokumentenakte vorbereitet. Betrag und Kontierung bitte prüfen.",
            imageUrl = prepared.localUri, originalMimeType = document.mimeType,
            storedFilename = document.storedFilename, fileSizeBytes = original.length(),
            propertyId = property.propertyId, unitId = unitId,
            wohneinheit = units.firstOrNull { it.unitId == unitId }?.name.orEmpty(),
            freigabestatus = "OFFEN", pruefstatus = "ZU_PRUEFEN", exportStatus = "ZU_PRUEFEN"
        )
        repository.upsertManagedDocument(prepared.copy(
            documentId = StableDocumentIdentity.receiptDocumentId(draft.internalId),
            propertyId = draft.propertyId, unitId = unitId.takeIf(String::isNotBlank), receiptInternalId = draft.internalId,
            documentDate = draft.datum, title = draft.aussteller, originalFilename = document.originalFilename,
            reviewStatus = DocumentReviewStatus.PRUEFEN.name
        ), draft)
        val id = repository.insert(draft).toInt()
        requireNotNull(repository.getReceiptById(id))
    }
}
