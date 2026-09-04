package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

enum class ManagedDocumentType {
    KAUFVERTRAG, NOTARUNTERLAGE, GRUNDBUCHAUSZUG, ENERGIEAUSWEIS,
    MIETVERTRAG, UEBERGABEPROTOKOLL, DARLEHENSVERTRAG, ZINSBESCHEINIGUNG,
    VERSICHERUNGSPOLICE, GRUNDSTEUERDOKUMENT, KAUFPREISAUFTEILUNG,
    RECHNUNG, KASSENBON, SANIERUNGSUNTERLAGE, BAUUNTERLAGE, GRUNDRISS,
    WOHNFLAECHENBERECHNUNG, PV_UNTERLAGE, SONSTIGES
}

enum class DocumentProcessingStatus { AUSSTEHEND, LAEUFT, ERFOLGREICH, FEHLGESCHLAGEN, NICHT_ERFORDERLICH }
enum class DocumentReviewStatus { PRUEFEN, GEPRUEFT, UEBERNOMMEN, IGNORIERT, MIGRATION_PRUEFEN }
enum class DocumentSource { RECEIPT, SCANNER, DATEIIMPORT, DRIVE_RESTORE, LEGACY_MIGRATION }
enum class DocumentFieldDecision { AUSSTEHEND, UEBERNEHMEN, IGNORIEREN }

@Entity(
    tableName = "managed_documents",
    indices = [
        Index("propertyId"), Index("unitId"), Index("receiptInternalId"),
        Index("driveFileId"), Index("sha256"), Index("documentDate"), Index("documentType")
    ]
)
data class ManagedDocument(
    @PrimaryKey val documentId: String,
    val propertyId: String,
    val unitId: String? = null,
    val receiptInternalId: String? = null,
    val documentType: String = ManagedDocumentType.SONSTIGES.name,
    val documentCategory: String = "06_Sonstige_Objektunterlagen",
    val documentDate: String = "",
    val title: String = "",
    val originalFilename: String = "",
    val storedFilename: String = "",
    val mimeType: String = "application/octet-stream",
    val localUri: String = "",
    val driveFileId: String? = null,
    val driveFolderId: String? = null,
    val sha256: String = "",
    val fileSizeBytes: Long = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    val ocrStatus: String = DocumentProcessingStatus.AUSSTEHEND.name,
    val ocrText: String = "",
    val aiAnalysisStatus: String = DocumentProcessingStatus.AUSSTEHEND.name,
    val aiConfidence: Double = 0.0,
    val reviewStatus: String = DocumentReviewStatus.PRUEFEN.name,
    val source: String = DocumentSource.DATEIIMPORT.name,
    val extractedFieldsJson: String = "",
    val loanId: Int? = null,
    val tenantReference: String? = null,
    val renovationReference: String? = null,
    val migrationStatus: String = "",
    val legacyDriveFolderId: String? = null
)

@Fts4
@Entity(tableName = "document_search_fts")
data class DocumentSearchFts(
    val documentId: String,
    val searchableText: String
)

@Entity(tableName = "document_migration_journal")
data class DocumentMigrationJournal(
    @PrimaryKey val documentId: String,
    val receiptInternalId: String? = null,
    val driveFileId: String,
    val originalFolderId: String = "",
    val originalFilename: String = "",
    val targetFolderId: String,
    val targetFilename: String,
    val beforeSha256: String = "",
    val state: String = "PLANNED",
    val lastError: String = "",
    val updatedAt: String = ""
)

@Dao
interface ManagedDocumentDao {
    @Query("SELECT * FROM managed_documents ORDER BY documentDate DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ManagedDocument>>

    @Query("SELECT * FROM managed_documents ORDER BY documentDate DESC, updatedAt DESC")
    suspend fun getAll(): List<ManagedDocument>

    @Query("SELECT * FROM managed_documents WHERE documentId = :id LIMIT 1")
    suspend fun getById(id: String): ManagedDocument?

    @Query("SELECT * FROM managed_documents WHERE receiptInternalId = :receiptId LIMIT 1")
    suspend fun getByReceiptId(receiptId: String): ManagedDocument?

    @Query("SELECT * FROM managed_documents WHERE driveFileId = :driveFileId LIMIT 1")
    suspend fun getByDriveFileId(driveFileId: String): ManagedDocument?

    @Query("SELECT * FROM managed_documents WHERE sha256 = :sha256 AND sha256 != ''")
    suspend fun getByHash(sha256: String): List<ManagedDocument>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: ManagedDocument)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(documents: List<ManagedDocument>)

    @Query("DELETE FROM managed_documents WHERE documentId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM managed_documents")
    suspend fun deleteAllDocuments()

    @Query("DELETE FROM document_migration_journal")
    suspend fun clearMigrationJournal()

    @Query("DELETE FROM document_search_fts WHERE documentId = :documentId")
    suspend fun deleteSearchEntry(documentId: String)

    @Insert
    suspend fun insertSearchEntry(entry: DocumentSearchFts)

    @Query("DELETE FROM document_search_fts")
    suspend fun clearSearchIndex()

    @Query("SELECT d.* FROM managed_documents d JOIN document_search_fts f ON f.documentId = d.documentId WHERE document_search_fts MATCH :query AND (:propertyId = '' OR d.propertyId = :propertyId) AND (:unitId = '' OR d.unitId = :unitId) AND (:year = '' OR substr(d.documentDate, 1, 4) = :year) AND (:documentType = '' OR d.documentType = :documentType) AND (:category = '' OR d.documentCategory = :category) ORDER BY d.documentDate DESC")
    suspend fun search(query: String, propertyId: String = "", unitId: String = "", year: String = "", documentType: String = "", category: String = ""): List<ManagedDocument>

    @Query("SELECT * FROM document_migration_journal ORDER BY updatedAt, documentId")
    suspend fun getMigrationJournal(): List<DocumentMigrationJournal>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMigrationJournal(entry: DocumentMigrationJournal)

    @Query("DELETE FROM document_migration_journal WHERE documentId = :documentId")
    suspend fun deleteMigrationJournal(documentId: String)
}

data class DocumentRoute(
    val segments: List<String>,
    val canonicalKey: String
) {
    val displayPath: String get() = segments.joinToString("/")
}

object StableDocumentIdentity {
    const val LEGACY_PROPERTY_ID = "property-1"

    fun receiptDocumentId(internalId: String): String = "receipt:$internalId"

    fun legacyUnitId(propertyId: String, unitName: String): String {
        val canonical = "$propertyId|${unitName.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")}" 
        return "unit-" + sha256(canonical).take(20)
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun sha256(value: String): String = sha256(value.toByteArray(Charsets.UTF_8))
}

object DocumentDrivePathResolver {
    fun propertyFolderName(propertyName: String, address: String): String {
        val seed = address.ifBlank { propertyName }.ifBlank { "Immobilie" }
        return "SteuerAssistent_${safeSegment(seed, 70)}"
    }

    fun route(
        propertyId: String,
        propertyName: String,
        propertyAddress: String,
        type: ManagedDocumentType,
        documentDate: String,
        unitId: String? = null,
        unitLabel: String? = null
    ): DocumentRoute {
        val root = propertyFolderName(propertyName, propertyAddress)
        val tail = when (type) {
            ManagedDocumentType.KAUFVERTRAG, ManagedDocumentType.NOTARUNTERLAGE,
            ManagedDocumentType.GRUNDBUCHAUSZUG -> listOf("00_Stammdaten", "01_Kauf_Eigentum")
            ManagedDocumentType.BAUUNTERLAGE, ManagedDocumentType.GRUNDRISS,
            ManagedDocumentType.WOHNFLAECHENBERECHNUNG -> listOf("00_Stammdaten", "02_Grundstueck_Gebaeude")
            ManagedDocumentType.ENERGIEAUSWEIS, ManagedDocumentType.PV_UNTERLAGE -> listOf("00_Stammdaten", "03_Energie_Technik")
            ManagedDocumentType.VERSICHERUNGSPOLICE -> listOf("00_Stammdaten", "04_Versicherungen")
            ManagedDocumentType.GRUNDSTEUERDOKUMENT -> listOf("00_Stammdaten", "05_Steuer_Grundlagen")
            ManagedDocumentType.MIETVERTRAG -> listOf("01_Einheiten", unitFolder(unitId, unitLabel), "Mietvertrag")
            ManagedDocumentType.UEBERGABEPROTOKOLL -> listOf("01_Einheiten", unitFolder(unitId, unitLabel), "Uebergabe")
            ManagedDocumentType.DARLEHENSVERTRAG -> listOf("03_Finanzierung_AfA", "Darlehen")
            ManagedDocumentType.ZINSBESCHEINIGUNG -> listOf("03_Finanzierung_AfA", "Zinsunterlagen")
            ManagedDocumentType.KAUFPREISAUFTEILUNG -> listOf("03_Finanzierung_AfA", "Kaufpreisaufteilung")
            ManagedDocumentType.RECHNUNG, ManagedDocumentType.KASSENBON -> listOf("02_Belege", validYear(documentDate))
            ManagedDocumentType.SANIERUNGSUNTERLAGE -> listOf("04_Sanierungen")
            ManagedDocumentType.SONSTIGES -> listOf("00_Stammdaten", "06_Sonstige_Objektunterlagen")
        }
        val canonical = (listOf("property:$propertyId") + tail).joinToString("/") { canonical(it) }
        return DocumentRoute(listOf(root) + tail, canonical)
    }

    private fun unitFolder(unitId: String?, label: String?): String {
        val display = label?.takeIf(String::isNotBlank) ?: unitId?.takeIf(String::isNotBlank) ?: "Einheit_pruefen"
        return safeSegment(display.replace(' ', '_'), 55)
    }

    private fun validYear(date: String): String = runCatching { LocalDate.parse(date).year.toString() }.getOrDefault("Ungeklart")
    private fun canonical(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    fun safeSegment(value: String, maxLength: Int): String {
        val transliterated = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace("ß", "ss", ignoreCase = true)
        return transliterated.replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .replace(Regex("_+"), "_").trim('_', '-').ifBlank { "Unbekannt" }.take(maxLength).trimEnd('_', '-')
    }
}

object DocumentFilenameGenerator {
    fun receipt(receipt: Receipt, extension: String): String {
        val date = runCatching { LocalDate.parse(receipt.datum).toString() }.getOrDefault("0000-00-00")
        val vendor = DocumentDrivePathResolver.safeSegment(receipt.aussteller, 48)
        val amount = BigDecimal.valueOf(receipt.bruttobetrag).setScale(2, RoundingMode.HALF_UP)
            .toPlainString().replace('.', '-') + "EUR"
        val displayId = DocumentDrivePathResolver.safeSegment(receipt.getEffectiveDisplayId(), 30)
        val ext = extension.trim().trimStart('.').lowercase(Locale.ROOT).replace("jpeg", "jpg")
            .replace(Regex("[^a-z0-9]"), "").ifBlank { "bin" }
        val suffix = "_${amount}_${displayId}.$ext"
        val prefixBudget = (120 - date.length - 1 - suffix.length).coerceAtLeast(8)
        return "${date}_${vendor.take(prefixBudget).trimEnd('_', '-')}$suffix"
    }

    fun document(type: ManagedDocumentType, date: String, distinguishingText: String, extension: String, stableId: String = ""): String {
        val prefix = type.name.lowercase(Locale.ROOT).split('_').joinToString("") { it.replaceFirstChar(Char::uppercase) }
        val detail = DocumentDrivePathResolver.safeSegment(distinguishingText, 54)
        val validDate = runCatching { LocalDate.parse(date).toString() }.getOrDefault("Datum_unbekannt")
        val ext = extension.trimStart('.').lowercase(Locale.ROOT).replace("jpeg", "jpg").replace(Regex("[^a-z0-9]"), "").ifBlank { "bin" }
        val identity = stableId.filter(Char::isLetterOrDigit).take(8).takeIf(String::isNotBlank)
        return (listOfNotNull(prefix, detail, validDate, identity).filter { it.isNotBlank() }.joinToString("_").take(115).trimEnd('_')) + ".$ext"
    }
}

data class DocumentFieldProposal(
    val key: String,
    val label: String,
    val detectedValue: String,
    val currentValue: String = "",
    val confidence: Double = 0.0,
    val sourcePage: String = "",
    val decision: DocumentFieldDecision = DocumentFieldDecision.AUSSTEHEND,
    val editedValue: String? = null
) {
    val effectiveValue: String get() = editedValue ?: detectedValue
}

object DocumentReviewPolicy {
    fun confirmedValues(proposals: List<DocumentFieldProposal>): Map<String, String> = proposals
        .filter { it.decision == DocumentFieldDecision.UEBERNEHMEN }
        .associate { it.key to it.effectiveValue }
}

enum class DocumentDuplicateKind { NONE, EXACT, POSSIBLE }
data class DocumentDuplicateMatch(val kind: DocumentDuplicateKind, val existingDocumentId: String? = null)

object DocumentDuplicatePolicy {
    fun detect(candidate: ManagedDocument, existing: List<ManagedDocument>): DocumentDuplicateMatch {
        existing.firstOrNull { it.documentId == candidate.documentId }?.let { return DocumentDuplicateMatch(DocumentDuplicateKind.EXACT, it.documentId) }
        candidate.driveFileId?.takeIf(String::isNotBlank)?.let { id ->
            existing.firstOrNull { it.driveFileId == id }?.let { return DocumentDuplicateMatch(DocumentDuplicateKind.EXACT, it.documentId) }
        }
        candidate.receiptInternalId?.takeIf(String::isNotBlank)?.let { id ->
            existing.firstOrNull { it.receiptInternalId == id }?.let { return DocumentDuplicateMatch(DocumentDuplicateKind.EXACT, it.documentId) }
        }
        if (candidate.sha256.isNotBlank()) {
            existing.firstOrNull { it.sha256 == candidate.sha256 && it.fileSizeBytes == candidate.fileSizeBytes }
                ?.let { return DocumentDuplicateMatch(DocumentDuplicateKind.EXACT, it.documentId) }
        }
        existing.firstOrNull {
            it.originalFilename.equals(candidate.originalFilename, ignoreCase = true) &&
                it.fileSizeBytes == candidate.fileSizeBytes && candidate.fileSizeBytes > 0
        }?.let { return DocumentDuplicateMatch(DocumentDuplicateKind.POSSIBLE, it.documentId) }
        return DocumentDuplicateMatch(DocumentDuplicateKind.NONE)
    }
}

object DocumentSearchTextBuilder {
    fun build(document: ManagedDocument, receipt: Receipt? = null): String = listOfNotNull(
        document.title, document.storedFilename, document.documentType, document.documentCategory,
        document.documentDate, document.ocrText, document.extractedFieldsJson,
        receipt?.aussteller, receipt?.beschreibung, receipt?.displayId, receipt?.internalId,
        receipt?.bruttobetrag?.let { BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_UP).toPlainString() },
        receipt?.hauptkategorie, receipt?.unterkategorie, receipt?.wohneinheit, receipt?.mieter
    ).filter { it.isNotBlank() }.joinToString(" ").lowercase(Locale.ROOT)

    fun ftsQuery(raw: String): String = raw.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        .joinToString(" AND ") { "\"${it.replace("\"", "\"\"")}\"*" }
}
