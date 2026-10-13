package com.example.data

import org.json.JSONObject
import java.math.BigDecimal

/** Only this deliberately written user message may cross the restore UI boundary. */
internal class BackupPreflightException(message: String, cause: Throwable) : IllegalArgumentException(message, cause)

/** Missing legacy fields may have defaults; damaged present values never do. */
internal fun JSONObject.backupDouble(key: String, default: Double = 0.0): Double {
    if (!has(key)) return default
    return runCatching { getDouble(key).also { require(it.isFinite()) } }
        .getOrElse { throw IllegalArgumentException("Gesicherter Zahlenwert '$key' ist ungültig. Bitte die Sicherung prüfen.", it) }
}

internal fun JSONObject.backupLong(key: String, default: Long = 0L): Long {
    if (!has(key)) return default
    return runCatching { BigDecimal(get(key).toString()).longValueExact() }
        .getOrElse { throw IllegalArgumentException("Gesicherter Ganzzahlwert '$key' ist ungültig. Bitte die Sicherung prüfen.", it) }
}

internal fun JSONObject.backupInt(key: String, default: Int = 0): Int =
    backupLong(key, default.toLong()).also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) {
        "Gesicherter Ganzzahlwert '$key' ist zu groß. Bitte die Sicherung prüfen."
    } }.toInt()

/** Existing receipt-position Math.round rule, now checked before narrowing to Long. */
internal object ReceiptMoney {
    fun toCent(euros: Double): Long {
        val scaled = euros * 100.0
        require(euros.isFinite() && scaled.isFinite() &&
            scaled >= Long.MIN_VALUE.toDouble() && scaled < Long.MAX_VALUE.toDouble()) {
            "Der gesicherte Belegbetrag ist zu groß oder ungültig. Bitte prüfen."
        }
        return Math.round(scaled)
    }
}

internal fun validateBackupProperty(property: PropertyMetadata) {
    val input = com.example.ui.PropertyWizardInput.validate(
        com.example.ui.GermanNumberInput.formatForInput(property.gesamtKaufpreis),
        com.example.ui.GermanNumberInput.formatForInput(property.wohnflaeche),
        com.example.ui.GermanNumberInput.formatForInput(property.grundstuecksgroesse),
        property.notariellesKaufdatum, property.baujahr.toString(),
        com.example.ui.GermanNumberInput.formatForInput(property.gebaeudewert),
        com.example.ui.GermanNumberInput.formatForInput(property.grundUndBodenWert), "")
    require(input.errors.isEmpty()) { "Gesicherte Immobilienwerte sind ungültig. Bitte prüfen." }
}
