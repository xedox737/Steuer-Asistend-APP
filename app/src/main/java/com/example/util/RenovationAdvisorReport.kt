package com.example.util

import com.example.data.ManagedDocument
import com.example.data.RenovationReviewCalculator
import com.example.data.RenovationReviewLine
import com.example.data.RenovationReviewSummary
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Separate review report, never a booking batch or an accounting approval. */
object RenovationAdvisorReport {
    fun lines(summary: RenovationReviewSummary, unitNames: Map<String, String>, documents: List<ManagedDocument>): List<String> {
        val money = NumberFormat.getCurrencyInstance(Locale.GERMANY)
        fun amount(value: Double?) = value?.let(money::format) ?: "Offen"
        fun date(value: String) = runCatching { LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) }.getOrDefault("Offen")
        fun receiptLine(line: RenovationReviewLine) = buildString {
            append(line.receipt?.getEffectiveDisplayId() ?: line.relation.receiptInternalId)
            append(" | "); append(line.receipt?.aussteller ?: "Beleg fehlt")
            append(" | "); append(date(line.receipt?.datum.orEmpty()))
            append(" | Brutto "); append(amount(line.grossCost)); append(" | Netto "); append(amount(line.netCost))
            append(" | "); append(line.periodLabel); append(" | "); append(line.relation.taxStatus.label)
            append(if (line.included) " | Berücksichtigt" else " | Nicht in Prüfsumme")
            if (line.relation.advisorMarked) append(" | Steuerberater-Vormerkung")
            line.issue?.let { append(" | Prüffall: $it") }
        }
        return buildList {
            add("Objekt: ${summary.property.name} | ${summary.property.adresse}")
            add("Objekt-ID: ${summary.property.propertyId}")
            add(RenovationReviewCalculator.NO_TAX_DECISION)
            add("Prüfzeitraum: ${date(summary.basis.monitorStartDate)} - ${date(summary.basis.monitorEndDate)}")
            add("Quelle Zeitraum: ${summary.startSource}")
            add(RenovationReviewCalculator.PERIOD_NOTE)
            add("Gebäude-Anschaffungskosten (bestehende AfA-Basis): ${amount(summary.basis.buildingAcquisitionCosts)}")
            add("Kaufpreisaufteilung: ${summary.basis.allocationSource}; Gebäudeanteil + anteilige Erwerbsnebenkosten")
            if (summary.basis.allocationNeedsReview) add("Prüffall: Kaufpreisaufteilung ist nicht schlüssig.")
            add("15%-Grenze: ${amount(summary.basis.limit15Percent)}")
            add("Bisher bewusst berücksichtigt, netto: ${amount(summary.consideredNet)}")
            add("Rest bis Grenze: ${amount(summary.remaining)}")
            add("Auslastung: ${summary.usagePercent?.let { String.format(Locale.GERMANY, "%.1f %%", it) } ?: "Basis fehlt"}")
            if (summary.usagePercent != null && summary.usagePercent!! >= 100) add(RenovationReviewCalculator.RETROSPECTIVE_WARNING)
            add("")
            add("MASSNAHMEN")
            if (summary.measures.isEmpty()) add("Keine Maßnahmen zugeordnet.")
            summary.measures.forEach { measure ->
                val lines = summary.lines.filter { it.relation.renovationMeasureId == measure.id }
                add("${measure.name} | ID ${measure.id}")
                add("Einheit: ${if (measure.unitId.isBlank()) "Gesamtobjekt / Allgemein" else unitNames[measure.unitId] ?: "Einheit nicht verfügbar (${measure.unitId})"}")
                add("Zeitraum: ${date(measure.startDate)} - ${date(measure.endDate)} | ${measure.status.label} | ${measure.taxStatus.label}")
                if (measure.description.isNotBlank()) add("Beschreibung: ${measure.description}")
                add("Bruttokosten: ${amount(lines.filter { it.grossCost >= 0 }.sumOf { it.grossCost })}")
                add("Erstattungen / Gutschriften brutto: ${amount(-lines.filter { it.grossCost < 0 }.sumOf { it.grossCost })}")
                add("Berücksichtigte Nettokosten: ${amount(lines.filter { it.included }.sumOf { it.netCost ?: 0.0 })}")
                if (measure.advisorNote.isNotBlank()) add("Steuerberater-Notiz: ${measure.advisorNote}")
                val start = runCatching { LocalDate.parse(measure.startDate) }.getOrNull()
                val end = runCatching { LocalDate.parse(measure.endDate) }.getOrNull()
                val limit = runCatching { LocalDate.parse(summary.basis.monitorEndDate) }.getOrNull()
                if (start != null && end != null && limit != null && start <= limit && end > limit)
                    add("Prüffall: Maßnahme über das Ende des Prüfzeitraums; zeitanteilige Kosten gesondert prüfen.")
                lines.forEach { add(receiptLine(it)) }
                measure.evidence.forEach { evidence ->
                    val document = documents.firstOrNull { it.documentId == evidence.documentId && it.propertyId == summary.property.propertyId }
                    add("Nachweis ${evidence.role.label}: ${document?.originalFilename ?: "Dokument nicht verfügbar"} | ID ${evidence.documentId}")
                }
                add("")
            }
            add("OFFENE PRÜFFÄLLE")
            summary.errors.forEach(::add)
            if (summary.openCases.isEmpty() && summary.errors.isEmpty()) add("Keine offenen Belegprüffälle im aktuellen Zuordnungsstand.")
            summary.openCases.forEach { add(receiptLine(it)) }
            add("Maßnahmen und Vormerkungen verändern keine AfA-, DATEV- oder Jahresfreigabe.")
        }
    }
}
