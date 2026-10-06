package com.example.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserInputValidationTest {
    @Test
    fun germanAndTechnicalDecimalFormatsRoundTripSafely() {
        val cases = mapOf(
            "760.0" to 760.0,
            "760,0" to 760.0,
            "760,00" to 760.0,
            "1.249,90" to 1249.90,
            "1249,90" to 1249.90,
            "1.050.000,00" to 1050000.0,
            "72,5" to 72.5,
            "1234" to 1234.0,
            "0,75" to 0.75
        )
        cases.forEach { (input, expected) ->
            assertEquals(input, expected, GermanNumberInput.parse(input)!!, 0.000001)
        }
        assertEquals(760.0, GermanNumberInput.parse(GermanNumberInput.formatForInput(760.0))!!, 0.0)
        assertEquals("760,00", GermanNumberInput.formatForInput(760.0))
    }

    @Test
    fun invalidAndNonFiniteNumbersAreRejected() {
        listOf("abc", "12,34,56", "1e309", "NaN", "Infinity", "-Infinity").forEach {
            assertNull(it, GermanNumberInput.parse(it))
        }
        assertNull(GermanNumberInput.parseNonNegative("-72,5"))
    }

    @Test
    fun calendarValidationUsesRealDates() {
        assertNotNull(CalendarInput.parseIsoDate("2024-02-29"))
        assertNotNull(CalendarInput.parseIsoDate("2026-02-28"))
        listOf("2026-02-29", "2026-02-30", "2026-13-01", "2026-00-01", "31.02.2026").forEach {
            assertNull(it, CalendarInput.parseIsoDate(it))
        }
    }

    @Test
    fun propertyWizardAcceptsGermanValuesAndRejectsUnsafeInputs() {
        val valid = PropertyWizardInput.validate(
            purchasePrice = "1.050.000,00",
            livingArea = "620,5",
            landArea = "950",
            purchaseDate = "2026-10-01",
            yearBuilt = "1954",
            buildingValue = "",
            landValue = "",
            loanAmount = ""
        )
        assertTrue(valid.errors.isEmpty())
        assertEquals(1050000.0, valid.values!!.purchasePrice, 0.001)
        assertEquals(620.5, valid.values!!.livingArea, 0.001)
        assertEquals(950.0, valid.values!!.landArea, 0.001)

        val negativeArea = PropertyWizardInput.validate(
            purchasePrice = "100000",
            livingArea = "-72,5",
            landArea = "950",
            purchaseDate = "2026-10-01",
            yearBuilt = "",
            buildingValue = "",
            landValue = "",
            loanAmount = ""
        )
        assertEquals("Die Fläche darf nicht negativ sein.", negativeArea.errors[PropertyWizardInput.LIVING_AREA])

        val impossibleDate = PropertyWizardInput.validate(
            purchasePrice = "100000",
            livingArea = "72,5",
            landArea = "950",
            purchaseDate = "2026-02-30",
            yearBuilt = "",
            buildingValue = "",
            landValue = "",
            loanAmount = ""
        )
        assertEquals("Bitte ein gültiges Datum eingeben.", impossibleDate.errors[PropertyWizardInput.PURCHASE_DATE])

        val infinite = PropertyWizardInput.validate(
            purchasePrice = "1e309",
            livingArea = "72,5",
            landArea = "950",
            purchaseDate = "2026-02-28",
            yearBuilt = "",
            buildingValue = "",
            landValue = "",
            loanAmount = ""
        )
        assertFalse(infinite.errors.isEmpty())
        assertNull(infinite.values)
    }

    @Test
    fun manualReceiptInputAcceptsCommaAmountAndRejectsImpossibleDate() {
        assertEquals(1249.90, ReceiptInputValidation.amount("1249,90")!!, 0.001)
        assertNotNull(ReceiptInputValidation.date("2026-02-28"))
        assertNull(ReceiptInputValidation.date("2026-02-30"))
    }
}
