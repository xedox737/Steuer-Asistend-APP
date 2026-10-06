package com.example.ui

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shared validation for human-entered German numbers and calendar dates.
 * Accepts German grouping/decimal notation as well as the app's technical decimal-dot roundtrip.
 */
internal object GermanNumberInput {
    private val germanNumber = Regex("""[+-]?(?:\d+|\d{1,3}(?:\.\d{3})+)(?:,\d+)?""")
    private val technicalDecimal = Regex("""[+-]?\d+\.\d+""")
    private val integerNumber = Regex("""[+-]?\d+""")

    fun parse(value: String): Double? {
        val compact = value.trim()
            .replace("\u00a0", "")
            .replace(" ", "")
            .replace("€", "")
        if (compact.isBlank()) return null

        val normalized = when {
            germanNumber.matches(compact) && (compact.contains(',') || compact.matches(Regex("""[+-]?\d{1,3}(?:\.\d{3})+"""))) ->
                compact.replace(".", "").replace(',', '.')
            technicalDecimal.matches(compact) || integerNumber.matches(compact) -> compact
            germanNumber.matches(compact) -> compact.replace(',', '.')
            else -> return null
        }
        return normalized.toDoubleOrNull()?.takeIf(Double::isFinite)
    }

    fun parseNonNegative(value: String): Double? =
        parse(value)?.takeIf { it >= 0.0 }

    fun formatForInput(value: Double, fractionDigits: Int = 2): String {
        require(value.isFinite()) { "Nicht-endliche Zahlen dürfen nicht formatiert werden." }
        return NumberFormat.getNumberInstance(Locale.GERMANY).apply {
            isGroupingUsed = false
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
        }.format(value)
    }
}

internal object CalendarInput {
    private val isoPattern = Regex("""\d{4}-\d{2}-\d{2}""")

    fun parseIsoDate(value: String): LocalDate? {
        val text = value.trim()
        if (!isoPattern.matches(text)) return null
        return runCatching { LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
    }

    fun isValidIsoDate(value: String): Boolean = parseIsoDate(value) != null
}

internal object ReceiptInputValidation {
    fun amount(value: String): Double? = GermanNumberInput.parse(value)

    fun date(value: String): LocalDate? = CalendarInput.parseIsoDate(value)
}
