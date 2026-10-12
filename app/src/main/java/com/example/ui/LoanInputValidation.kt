package com.example.ui

import com.example.data.Loan
import kotlin.math.sqrt

/** Loan rules layered on the shared German parser; no second number grammar. */
internal object LoanInputValidation {
    enum class Field(val label: String, val required: Boolean = false, val emptyValue: Double = 0.0) {
        AMOUNT("Darlehenssumme €*", true), BALANCE("Restschuld €*", true),
        INTEREST("Sollzins %*", true), REPAYMENT("Tilgung %"), MONTHLY("Monatliche Rate €"),
        RENTED("Vermietungsanteil %", emptyValue = 100.0)
    }
    enum class Status { EMPTY, VALID, INVALID_FORMAT, NON_FINITE, OUT_OF_RANGE, INVALID_STORED }
    data class Result(val status: Status, val value: Double? = null, val error: String? = null)

    // Numerical safety, NOT a banking/tax maximum: two allowed values can be
    // multiplied with 10,000-fold headroom below Double.MAX_VALUE (annual interest).
    val MAX_SAFE_VALUE: Double = sqrt(Double.MAX_VALUE) / 100.0
    const val INVALID_STORED_TEXT = "Ungültiger gespeicherter Wert"
    const val STORED_ERROR = "Der gespeicherte Wert ist ungültig – bitte korrigieren."

    fun parse(raw: String, field: Field): Result {
        if (raw == INVALID_STORED_TEXT) return Result(Status.INVALID_STORED, error = STORED_ERROR)
        val parsed = GermanNumberInput.parseResult(raw)
        // Currency-only text is not an explicitly empty optional field.
        if (parsed.status == GermanNumberInput.Status.EMPTY && raw.isNotBlank()) {
            return Result(Status.INVALID_FORMAT, error = "Bitte eine gültige Zahl eingeben.")
        }
        return when (parsed.status) {
            GermanNumberInput.Status.EMPTY -> Result(Status.EMPTY,
                value = if (field.required) null else field.emptyValue,
                error = if (field.required) "Bitte einen Wert eingeben." else null)
            GermanNumberInput.Status.INVALID_FORMAT -> Result(Status.INVALID_FORMAT, error = "Bitte eine gültige Zahl eingeben.")
            GermanNumberInput.Status.NON_FINITE -> Result(Status.NON_FINITE, error = "Der Wert ist zu groß oder ungültig.")
            GermanNumberInput.Status.VALID -> {
                val value = requireNotNull(parsed.value)
                when {
                    field == Field.RENTED && value !in 0.0..100.0 -> Result(Status.OUT_OF_RANGE, error = "Bitte einen Wert zwischen 0 und 100 eingeben.")
                    value < 0.0 -> Result(Status.OUT_OF_RANGE, error = "Der Wert darf nicht negativ sein.")
                    value > MAX_SAFE_VALUE -> Result(Status.OUT_OF_RANGE, error = "Der Wert ist für die Berechnung zu groß.")
                    else -> Result(Status.VALID, value)
                }
            }
        }
    }

    fun prefill(value: Double, blankZero: Boolean = false): String = when {
        !value.isFinite() -> INVALID_STORED_TEXT
        blankZero && value.toBits() == 0.0.toBits() -> ""
        else -> GermanNumberInput.formatForInput(value, maxOf(2, value.toBigDecimal().scale()))
    }

    fun numbers(loan: Loan): Map<Field, Double> = mapOf(
        Field.AMOUNT to loan.darlehensbetrag, Field.BALANCE to loan.restschuld,
        Field.INTEREST to loan.sollzinsProzent, Field.REPAYMENT to loan.tilgungProzent,
        Field.MONTHLY to loan.monatlicheRate, Field.RENTED to loan.vermietungsanteilProzent
    )

    fun dateWarning(bindingEnd: String, termEnd: String): String? {
        val binding = CalendarInput.parseIsoDate(bindingEnd) ?: return null
        val term = CalendarInput.parseIsoDate(termEnd) ?: return null
        return if (binding.isAfter(term))
            "Die Zinsbindung endet nach dem geplanten Laufzeitende. Das kann bei vorzeitiger Tilgung passen – bitte Vertragsdaten prüfen."
        else null
    }

    fun dateErrors(start: String, bindingEnd: String, termEnd: String): Map<String, String> = buildMap {
        val dates = mapOf("start" to start, "binding" to bindingEnd, "end" to termEnd)
        dates.forEach { (key, raw) ->
            if (raw.isNotBlank() && CalendarInput.parseIsoDate(raw) == null) put(key, "Bitte ein gültiges Datum eingeben.")
        }
        val beginning = CalendarInput.parseIsoDate(start) ?: return@buildMap
        listOf("binding" to bindingEnd, "end" to termEnd).forEach { (key, raw) ->
            if (CalendarInput.parseIsoDate(raw)?.isBefore(beginning) == true) {
                put(key, "Das Enddatum darf nicht vor dem Beginn liegen.")
            }
        }
        // Binding after planned repayment can occur; no hard rule between them.
    }
}
