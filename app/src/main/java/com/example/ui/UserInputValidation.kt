package com.example.ui

import com.example.util.DateFormatUtils

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shared validation for human-entered German numbers and calendar dates.
 * Accepts German grouping/decimal notation as well as the app's technical decimal-dot roundtrip.
 */
internal object GermanNumberInput {
    enum class Status { EMPTY, VALID, INVALID_FORMAT, NON_FINITE }
    data class Result(val status: Status, val value: Double? = null)
    private val germanNumber = Regex("""[+-]?(?:\d+|\d{1,3}(?:\.\d{3})+)(?:,\d+)?""")
    private val technicalDecimal = Regex("""[+-]?\d+\.\d+""")
    private val integerNumber = Regex("""[+-]?\d+""")

    fun parse(value: String): Double? = parseResult(value).value

    fun parseResult(value: String): Result {
        val compact = value.trim()
            .replace("\u00a0", "")
            .replace(" ", "")
            .replace("€", "")
        if (compact.isBlank()) return Result(Status.EMPTY)
        if (compact in setOf("NaN", "Infinity", "+Infinity", "-Infinity")) return Result(Status.NON_FINITE)

        val normalized = when {
            germanNumber.matches(compact) && (compact.contains(',') || compact.matches(Regex("""[+-]?\d{1,3}(?:\.\d{3})+"""))) ->
                compact.replace(".", "").replace(',', '.')
            technicalDecimal.matches(compact) || integerNumber.matches(compact) -> compact
            germanNumber.matches(compact) -> compact.replace(',', '.')
            else -> return Result(Status.INVALID_FORMAT)
        }
        val number = normalized.toDoubleOrNull() ?: return Result(Status.INVALID_FORMAT)
        // A nonzero decimal outside Double's smallest magnitude must not silently become zero.
        if (number == 0.0 && normalized.any { it in '1'..'9' }) return Result(Status.INVALID_FORMAT)
        return if (number.isFinite()) Result(Status.VALID, number) else Result(Status.NON_FINITE)
    }

    /** Shared German presentation for non-currency decimal quantities, without integer truncation. */
    fun formatQuantity(value: Double, minimumDecimals: Int = 0, grouping: Boolean = true): String {
        if (!value.isFinite() || value < 0.0) return "Gespeicherten Wert bitte korrigieren"
        // Preferences store Float; do not expose widening noise (41.99f -> 41.990001678...).
        val decimal = if (value.toFloat().isFinite() && value.toFloat().toDouble() == value)
            value.toFloat().toString().toBigDecimal() else value.toBigDecimal()
        return NumberFormat.getNumberInstance(Locale.GERMANY).apply {
            isGroupingUsed = grouping
            minimumFractionDigits = minimumDecimals
            maximumFractionDigits = decimal.scale().coerceAtLeast(minimumDecimals)
        }.format(decimal)
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

/** Small field adapter on the shared grammar. Optional means genuinely blank, not invalid. */
internal object NumberFieldInput {
    data class Result(val value: Double? = null, val error: String? = null)
    fun nonNegative(raw: String, optional: Boolean = false, positive: Boolean = false, floatTarget: Boolean = false): Result {
        if (raw.isBlank()) return if (optional) Result() else Result(error = "Bitte einen Wert eingeben.")
        val parsed = GermanNumberInput.parseResult(raw)
        val value = parsed.value ?: return Result(error = if (parsed.status == GermanNumberInput.Status.NON_FINITE)
            "Der Wert ist zu groß oder ungültig." else "Bitte eine gültige Zahl eingeben.")
        if (value < 0.0) return Result(error = "Der Wert darf nicht negativ sein.")
        if (positive && value <= 0.0) return Result(error = "Der Wert muss größer als null sein.")
        if (floatTarget) runCatching { com.example.data.NumericWriteBoundary.float(value) }.exceptionOrNull()?.let {
            return Result(error = it.message ?: "Bitte den Wert prüfen.")
        }
        return Result(value)
    }
    fun prefill(value: Double): String = LoanInputValidation.prefill(value)
}

internal object CalendarInput {
    fun parseIsoDate(value: String): LocalDate? = com.example.util.DateFormatUtils.parseIso(value)

    fun isValidIsoDate(value: String): Boolean = parseIsoDate(value) != null
}

internal object ReceiptInputValidation {
    fun amount(value: String): Double? = GermanNumberInput.parse(value)

    fun date(value: String): LocalDate? = CalendarInput.parseIsoDate(value)
}


internal data class PropertyWizardValues(
    val purchasePrice: Double,
    val livingArea: Double,
    val landArea: Double,
    val yearBuilt: Int,
    val buildingValue: Double,
    val landValue: Double,
    val loanAmount: Double?
)

internal data class PropertyWizardValidation(
    val values: PropertyWizardValues?,
    val errors: Map<String, String>
)

internal object PropertyWizardInput {
    const val PURCHASE_PRICE = "Kaufpreis €"
    const val LIVING_AREA = "Wohnfläche m²"
    const val LAND_AREA = "Grundstücksfläche m²"
    const val PURCHASE_DATE = "Kaufdatum"
    const val YEAR_BUILT = "Baujahr"
    const val BUILDING_VALUE = "Gebäudeanteil € (optional)"
    const val LAND_VALUE = "Grund und Boden € (optional)"
    const val LOAN_AMOUNT = "Darlehensbetrag €"

    fun validate(
        purchasePrice: String,
        livingArea: String,
        landArea: String,
        purchaseDate: String,
        yearBuilt: String,
        buildingValue: String,
        landValue: String,
        loanAmount: String
    ): PropertyWizardValidation {
        val errors = linkedMapOf<String, String>()

        fun optionalNonNegative(raw: String, field: String, area: Boolean = false): Double {
            if (raw.isBlank()) return 0.0
            val parsed = GermanNumberInput.parse(raw)
            when {
                parsed == null -> errors[field] =
                    if (raw.contains("e", true) || raw.contains("Infinity", true) || raw.contains("NaN", true))
                        "Der eingegebene Wert ist zu groß oder ungültig."
                    else if (area) "Bitte eine gültige Fläche eingeben." else "Bitte einen gültigen Betrag eingeben."
                parsed < 0.0 -> errors[field] =
                    if (area) "Die Fläche darf nicht negativ sein." else "Der Betrag darf nicht negativ sein."
            }
            return parsed?.takeIf { it >= 0.0 } ?: 0.0
        }

        val purchase = optionalNonNegative(purchasePrice, PURCHASE_PRICE)
        val living = optionalNonNegative(livingArea, LIVING_AREA, area = true)
        val land = optionalNonNegative(landArea, LAND_AREA, area = true)
        val building = optionalNonNegative(buildingValue, BUILDING_VALUE)
        val landPart = optionalNonNegative(landValue, LAND_VALUE)
        val loanResult = if (loanAmount.isBlank()) null else LoanInputValidation.parse(loanAmount, LoanInputValidation.Field.AMOUNT)
        loanResult?.error?.let { errors[LOAN_AMOUNT] = it }
        val loan = loanResult?.value

        if (purchaseDate.isNotBlank() && !CalendarInput.isValidIsoDate(purchaseDate)) {
            errors[PURCHASE_DATE] = "Bitte ein gültiges Datum eingeben."
        }

        val year = if (yearBuilt.isBlank()) {
            0
        } else {
            PropertyFormInput.year(yearBuilt) ?: run {
                errors[YEAR_BUILT] = "Bitte ein gültiges Baujahr eingeben."
                0
            }
        }

        return PropertyWizardValidation(
            values = if (errors.isEmpty()) PropertyWizardValues(
                purchasePrice = purchase,
                livingArea = living,
                landArea = land,
                yearBuilt = year,
                buildingValue = building,
                landValue = landPart,
                loanAmount = loan
            ) else null,
            errors = errors
        )
    }

    fun unitArea(value: String): Double? {
        if (value.isBlank()) return 0.0
        return NumberFieldInput.nonNegative(value, floatTarget = true).value
    }
}

internal object PropertyFormInput {
    // The previous four-digit control allowed 9,999. Keep that range while bounding
    // allocation of per-unit preference keys and backup snapshots at a creation batch.
    const val MAX_CREATION_UNITS = 10_000

    fun unitCount(raw: String): Int? = raw.trim().takeIf { Regex("[0-9]+").matches(it) }
        ?.toIntOrNull()?.takeIf { it in 1..MAX_CREATION_UNITS }

    fun year(raw: String, currentYear: Int = LocalDate.now().year): Int? {
        if (raw.isBlank() || raw.trim() == "0") return 0
        return raw.trim().takeIf { Regex("[0-9]{4}").matches(it) }?.toIntOrNull()
            ?.takeIf { it in 1000..(currentYear + 1) }
    }

    fun requiredErrors(name: String, address: String, type: String): Map<String, String> = buildMap {
        if (name.isBlank()) put("name", "Bitte einen Objektnamen eingeben.")
        if (address.isBlank()) put("address", "Bitte die Objektadresse eingeben.")
        if (type.isBlank()) put("type", "Bitte eine Objektart eingeben.")
    }
}
