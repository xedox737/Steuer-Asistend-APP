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
    const val PURCHASE_DATE = "Kaufdatum YYYY-MM-DD"
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
        val loan = if (loanAmount.isBlank()) null else optionalNonNegative(loanAmount, LOAN_AMOUNT)

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
        return GermanNumberInput.parse(value)?.takeIf { it >= 0.0 }
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
