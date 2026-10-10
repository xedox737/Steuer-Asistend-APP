package com.example.data

import com.example.util.DatevMappingService
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max

data class RenovationReviewLine(
    val relation: RenovationReceiptRelation,
    val receipt: Receipt?,
    val measure: RenovationMeasure?,
    val grossCost: Double,
    val netCost: Double?,
    val included: Boolean,
    val periodLabel: String,
    val issue: String? = null
)

enum class RenovationWarning { NEUTRAL, HINWEIS, WARNUNG, GRENZE_ERREICHT }

data class RenovationReviewSummary(
    val property: PropertyMetadata,
    val basis: TaxPhase1Summary,
    val startSource: String,
    val measures: List<RenovationMeasure>,
    val lines: List<RenovationReviewLine>,
    val errors: List<String>
) {
    val consideredNet: Double = lines.filter { it.included }.sumOf { it.netCost ?: 0.0 }
    val remaining: Double = max(0.0, basis.limit15Percent - consideredNet)
    val usagePercent: Double? = if (basis.limit15Percent > 0.0) consideredNet / basis.limit15Percent * 100 else null
    val warning: RenovationWarning = when {
        usagePercent == null || usagePercent!! < 70 -> RenovationWarning.NEUTRAL
        usagePercent!! < 90 -> RenovationWarning.HINWEIS
        usagePercent!! < 100 -> RenovationWarning.WARNUNG
        else -> RenovationWarning.GRENZE_ERREICHT
    }
    val openCases: List<RenovationReviewLine> = lines.filter {
        it.issue != null || it.relation.advisorMarked || it.relation.taxStatus in setOf(
            RenovationTaxStatus.NICHT_GEPRUEFT, RenovationTaxStatus.STEUERBERATER_PRUEFEN)
    }
}

/** Preparation only: the calculator never edits a receipt, an AfA record or DATEV approval. */
object RenovationReviewCalculator {
    const val NO_TAX_DECISION = "Keine automatische Steuerentscheidung."
    const val RETROSPECTIVE_WARNING = "Die 15%-Grenze wurde erreicht oder überschritten. Bitte auch bereits zugeordnete Belege des Prüfzeitraums steuerlich erneut prüfen."
    const val PERIOD_NOTE = "Belegdatum als vorläufiger Zeitbezug. Leistungszeitraum und zeitanteilige Kosten bitte mit dem Steuerberater prüfen."

    fun calculate(property: PropertyMetadata, receipts: List<Receipt>, snapshot: RenovationReviewSnapshot,
        documents: List<ManagedDocument>? = null): RenovationReviewSummary {
        val propertyReceipts = receipts.filter { it.propertyId == property.propertyId }
        // Same acquisition-cost source as AfA. No second allocation formula.
        val basis = TaxPropertyCalculator.calculate(property, propertyReceipts)
        val start = date(basis.monitorStartDate)
        val end = date(basis.monitorEndDate)
        val measures = snapshot.measures.filter { it.propertyId == property.propertyId }
        val measureMap = measures.associateBy { it.id }
        val receiptMap = receipts.filter { it.internalId.isNotBlank() && it.deletionStatus in setOf("", "ACTIVE", "RESTORED") }
            .groupBy { it.internalId }
        val lines = snapshot.relations.filter { it.propertyId == property.propertyId }
            .distinctBy { it.receiptInternalId }.filter { it.renovationMeasureId.isNotBlank() || it.advisorMarked || it.taxStatus != RenovationTaxStatus.NICHT_GEPRUEFT }
            .map { relation ->
                val matches = receiptMap[relation.receiptInternalId].orEmpty()
                val receipt = matches.maxByOrNull { it.id }
                val measure = measureMap[relation.renovationMeasureId]
                val receiptDate = receipt?.let { date(it.datum) }
                val inPeriod = start != null && end != null && receiptDate != null && receiptDate >= start && receiptDate <= end
                val credit = receipt?.let(DatevMappingService::isCreditNote) == true
                val gross = receipt?.bruttobetrag?.takeIf { it.isFinite() }?.let { abs(it) * if (credit) -1 else 1 } ?: 0.0
                val net = receipt?.let { relation.confirmedNetAmount ?: reliableNetAmount(it) }
                    ?.let { it * if (credit) -1 else 1 }
                val issue = when {
                    receipt == null -> "Der zugeordnete Beleg ist nicht verfügbar."
                    matches.any { it.propertyId != property.propertyId } -> "Beleg und Maßnahme gehören zu unterschiedlichen Immobilien. Bitte neu zuordnen."
                    relation.renovationMeasureId.isNotBlank() && measure == null -> "Die zugeordnete Maßnahme ist nicht verfügbar."
                    measure?.unitId?.isNotBlank() == true && measure.unitId != receipt.unitId -> "Beleg und Maßnahme gehören zu unterschiedlichen Einheiten."
                    !receipt.bruttobetrag.isFinite() -> "Der Bruttobetrag ist ungültig."
                    net != null && abs(net) > abs(gross) + 0.01 -> "Der Nettobetrag darf den Bruttobetrag nicht übersteigen."
                    start == null || end == null -> "Prüfzeitraum fehlt. Bitte Besitz/Nutzen/Lasten im Objekt ergänzen."
                    receiptDate == null -> "Das Belegdatum ist ungültig."
                    net == null -> "Belastbarer Nettobetrag fehlt. Bitte Positionen oder einen bestätigten Nettobetrag ergänzen."
                    else -> null
                }
                val included = issue == null && inPeriod && relation.taxStatus.countsForReview &&
                    measure?.taxStatus != RenovationTaxStatus.NICHT_BERUECKSICHTIGEN
                RenovationReviewLine(relation, receipt, measure, gross, net, included,
                    if (start == null || end == null || receiptDate == null) "Prüfzeitraum offen"
                    else if (inPeriod) "Innerhalb Prüfzeitraum" else "Außerhalb Prüfzeitraum", issue)
            }
        val evidenceErrors = if (documents == null) emptyList() else measures.flatMap { measure ->
            measure.evidence.filter { evidence -> documents.none { it.documentId == evidence.documentId && it.propertyId == property.propertyId } }
                .map { "Nachweis für ${measure.name} nicht verfügbar. Bitte die Dokumentreferenz prüfen." }
        }
        return RenovationReviewSummary(property, basis,
            if (property.uebergangNutzenLasten.isNotBlank()) "Besitz / Nutzen / Lasten"
            else if (property.notariellesKaufdatum.isNotBlank()) "Notarielles Kaufdatum (Ersatz; wirtschaftlichen Übergang prüfen)"
            else "Anschaffungsdatum fehlt", measures, lines, snapshot.errors + evidenceErrors)
    }

    fun periodLabel(receipt: Receipt, basis: TaxPhase1Summary): String {
        val start = date(basis.monitorStartDate)
        val end = date(basis.monitorEndDate)
        val receiptDate = date(receipt.datum)
        return if (start == null || end == null || receiptDate == null) "Prüfzeitraum offen"
            else if (receiptDate >= start && receiptDate <= end) "Innerhalb Prüfzeitraum" else "Außerhalb Prüfzeitraum"
    }

    /** Existing receipt positions / VAT rates, with no invented 19% estimate. */
    fun reliableNetAmount(receipt: Receipt): Double? {
        val gross = abs(receipt.bruttobetrag)
        if (!gross.isFinite()) return null
        val positions = receipt.getPositionenList()
        if (positions.isEmpty() || positions.any { !it.gesamtpreis.isFinite() || !it.steuersatz.isFinite() || it.steuersatz !in 0.0..100.0 }) return null
        // Negative credit-note positions follow the same absolute-value convention as DATEV.
        val sum = positions.sumOf { abs(it.gesamtpreis) }
        if (abs(sum - gross) > max(0.01, gross * 0.001)) return null
        return positions.sumOf { abs(it.gesamtpreis) / (1 + it.steuersatz / 100) }
    }

    private fun date(text: String): LocalDate? = runCatching { LocalDate.parse(text) }.getOrNull()
}
