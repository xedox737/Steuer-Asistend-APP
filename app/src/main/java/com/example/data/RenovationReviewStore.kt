package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class RenovationStatus(val label: String) {
    OFFEN("Offen"), IN_PRUEFUNG("In Prüfung"), BESTAETIGT("Bestätigt"), ABGESCHLOSSEN("Abgeschlossen")
}

enum class RenovationTaxStatus(val label: String) {
    NICHT_GEPRUEFT("Nicht geprüft"),
    FUER_15_PROZENT_PRUEFUNG("Für 15%-Prüfung vorgemerkt"),
    STEUERBERATER_PRUEFEN("Steuerlich zu prüfen"),
    STEUERBERATER_BESTAETIGT("Vom Steuerberater bestätigt"),
    NICHT_BERUECKSICHTIGEN("Nicht berücksichtigt");

    val countsForReview: Boolean get() = this == FUER_15_PROZENT_PRUEFUNG || this == STEUERBERATER_BESTAETIGT
}

enum class RenovationEvidenceRole(val label: String) {
    RECHNUNG("Rechnung"), ANGEBOT("Angebot"), LIEFERSCHEIN("Lieferschein"),
    VORHER_FOTO("Vorher-Foto"), NACHHER_FOTO("Nachher-Foto"), GUTSCHRIFT("Gutschrift"),
    ABNAHME("Abnahme"), SONSTIGES("Sonstiges Dokument")
}

data class RenovationEvidence(val documentId: String, val role: RenovationEvidenceRole)

data class RenovationMeasure(
    val id: String = UUID.randomUUID().toString(),
    val propertyId: String,
    val unitId: String = "",
    val name: String,
    val description: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val status: RenovationStatus = RenovationStatus.OFFEN,
    val taxStatus: RenovationTaxStatus = RenovationTaxStatus.NICHT_GEPRUEFT,
    val advisorNote: String = "",
    val evidence: List<RenovationEvidence> = emptyList(),
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = createdAt
)

/** One current relationship per stable internal receipt ID, including explicit removal. */
data class RenovationReceiptRelation(
    val receiptInternalId: String,
    val propertyId: String,
    val renovationMeasureId: String = "",
    val taxStatus: RenovationTaxStatus = RenovationTaxStatus.NICHT_GEPRUEFT,
    val advisorMarked: Boolean = false,
    // Absolute net amount; the receipt's existing credit-note semantics supply the sign.
    val confirmedNetAmount: Double? = null,
    val updatedAt: String = Instant.now().toString()
)

data class RenovationReviewSnapshot(
    val measures: List<RenovationMeasure> = emptyList(),
    val relations: List<RenovationReceiptRelation> = emptyList(),
    val errors: List<String> = emptyList()
)

/** Small local supplemental store. No receipt, original file or accounting row is copied. */
class RenovationReviewStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _snapshot = MutableStateFlow(RenovationReviewSnapshot())
    val snapshot = _snapshot.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) { mutex.withLock { _snapshot.value = load() } }

    suspend fun saveMeasure(measure: RenovationMeasure) = withContext(Dispatchers.IO) {
        mutex.withLock {
            validateMeasure(measure)
            val current = load().measures.firstOrNull { it.id == measure.id }
            require(current == null || current.propertyId == measure.propertyId) { "Das Objekt einer bestehenden Maßnahme darf nicht gewechselt werden." }
            val saved = measure.copy(name = measure.name.trim(),
                createdAt = current?.createdAt ?: measure.createdAt, updatedAt = Instant.now().toString())
            check(prefs.edit().putString("measure_${saved.id}", measureJson(saved).toString()).commit()) {
                "Die Maßnahme konnte nicht dauerhaft gespeichert werden."
            }
            _snapshot.value = load()
        }
    }

    suspend fun saveRelation(relation: RenovationReceiptRelation) = withContext(Dispatchers.IO) {
        mutex.withLock {
            validateRelation(relation)
            if (relation.renovationMeasureId.isNotBlank()) {
                val measure = load().measures.singleOrNull { it.id == relation.renovationMeasureId }
                require(measure?.propertyId == relation.propertyId) { "Die Maßnahme gehört nicht zur Immobilie dieses Belegs." }
            }
            check(prefs.edit().putString("receipt_${relation.receiptInternalId}",
                relationJson(relation.copy(updatedAt = Instant.now().toString())).toString()).commit()) {
                "Die Belegzuordnung konnte nicht dauerhaft gespeichert werden."
            }
            _snapshot.value = load()
        }
    }

    private fun load(): RenovationReviewSnapshot {
        val measures = mutableListOf<RenovationMeasure>()
        val relations = mutableListOf<RenovationReceiptRelation>()
        val errors = mutableListOf<String>()
        prefs.all.toSortedMap().forEach { (key, raw) ->
            runCatching {
                require(raw is String) { "Ungültiger Datentyp" }
                validateEntry(key, raw)
                if (key.startsWith("measure_")) measures += parseMeasure(JSONObject(raw))
                else relations += parseRelation(JSONObject(raw))
            }.onFailure { errors += "Sanierungsdaten beschädigt ($key). Bitte die Sicherung prüfen; die Originaldaten bleiben erhalten." }
        }
        return RenovationReviewSnapshot(measures.sortedBy { it.name }, relations, errors)
    }

    companion object {
        const val PREFS = "renovation_review_prefs"
        const val PAYLOAD_KEY = "renovationReviewPrefs"

        fun validateMeasure(measure: RenovationMeasure) {
            require(measure.id.isNotBlank() && measure.propertyId.isNotBlank()) { "Bitte eine Immobilie wählen." }
            require(measure.name.isNotBlank()) { "Bitte einen Namen für die Maßnahme eingeben." }
            val start = optionalDate(measure.startDate)
            val end = optionalDate(measure.endDate)
            require(end == null || start != null) { "Bitte zuerst ein Startdatum eingeben." }
            require(end == null || !end.isBefore(start)) { "Das Enddatum darf nicht vor dem Startdatum liegen." }
            require(measure.evidence.all { it.documentId.isNotBlank() }) { "Ein Nachweis hat keine gültige Dokument-ID." }
            require(measure.evidence.map { it.documentId }.distinct().size == measure.evidence.size) { "Ein Nachweis darf nur einmal zugeordnet werden." }
        }

        fun validateRelation(relation: RenovationReceiptRelation) {
            require(relation.receiptInternalId.isNotBlank() && relation.propertyId.isNotBlank()) { "Die stabile Beleg- oder Objekt-ID fehlt." }
            require(relation.confirmedNetAmount == null || relation.confirmedNetAmount.isFinite() && relation.confirmedNetAmount >= 0.0) {
                "Bitte einen gültigen positiven Nettobetrag eingeben. Gutschriften werden anhand des Belegs abgezogen."
            }
        }

        private fun optionalDate(raw: String): LocalDate? {
            if (raw.isBlank()) return null
            require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(raw)) { "Bitte ein gültiges Datum im Format JJJJ-MM-TT eingeben." }
            return runCatching { LocalDate.parse(raw) }.getOrElse { error("Bitte ein gültiges Kalenderdatum eingeben.") }
        }

        /** Invoked before restore mutates Room or preferences, including key/identity consistency. */
        fun validateEntry(key: String, raw: String) {
            val json = JSONObject(raw)
            when {
                key.startsWith("measure_") -> parseMeasure(json).also {
                    require(key == "measure_${it.id}") { "Maßnahmen-ID und Sicherungsschlüssel widersprechen sich." }; validateMeasure(it)
                }
                key.startsWith("receipt_") -> parseRelation(json).also {
                    require(key == "receipt_${it.receiptInternalId}") { "Beleg-ID und Sicherungsschlüssel widersprechen sich." }; validateRelation(it)
                }
                else -> error("Unbekannter Sanierungsdatensatz.")
            }
        }

        private fun measureJson(m: RenovationMeasure) = JSONObject().apply {
            put("id", m.id); put("propertyId", m.propertyId); put("unitId", m.unitId)
            put("name", m.name); put("description", m.description); put("startDate", m.startDate); put("endDate", m.endDate)
            put("status", m.status.name); put("taxStatus", m.taxStatus.name); put("advisorNote", m.advisorNote)
            put("createdAt", m.createdAt); put("updatedAt", m.updatedAt)
            put("evidence", JSONArray().apply { m.evidence.forEach { put(JSONObject().put("documentId", it.documentId).put("role", it.role.name)) } })
        }

        private fun relationJson(r: RenovationReceiptRelation) = JSONObject().apply {
            put("receiptInternalId", r.receiptInternalId); put("propertyId", r.propertyId); put("renovationMeasureId", r.renovationMeasureId)
            put("taxStatus", r.taxStatus.name); put("advisorMarked", r.advisorMarked); put("updatedAt", r.updatedAt)
            put("confirmedNetAmount", r.confirmedNetAmount ?: JSONObject.NULL)
        }

        private fun parseMeasure(o: JSONObject): RenovationMeasure {
            val evidence = o.getJSONArray("evidence")
            return RenovationMeasure(o.getString("id"), o.getString("propertyId"), o.getString("unitId"),
                o.getString("name"), o.getString("description"), o.getString("startDate"), o.getString("endDate"),
                RenovationStatus.valueOf(o.getString("status")), RenovationTaxStatus.valueOf(o.getString("taxStatus")),
                o.getString("advisorNote"), (0 until evidence.length()).map { index -> evidence.getJSONObject(index).let {
                    RenovationEvidence(it.getString("documentId"), RenovationEvidenceRole.valueOf(it.getString("role")))
                } }, o.getString("createdAt"), o.getString("updatedAt"))
        }

        private fun parseRelation(o: JSONObject) = RenovationReceiptRelation(o.getString("receiptInternalId"),
            o.getString("propertyId"), o.getString("renovationMeasureId"), RenovationTaxStatus.valueOf(o.getString("taxStatus")),
            o.getBoolean("advisorMarked"), if (o.isNull("confirmedNetAmount")) null else o.getDouble("confirmedNetAmount"), o.getString("updatedAt"))
    }
}
