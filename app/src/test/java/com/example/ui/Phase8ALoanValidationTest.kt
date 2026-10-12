package com.example.ui

import com.example.data.Loan
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class Phase8ALoanValidationTest {
    private val fields = LoanInputValidation.Field.entries
    private fun parse(raw: String, field: LoanInputValidation.Field = LoanInputValidation.Field.BALANCE) =
        LoanInputValidation.parse(raw, field)

    @Test fun germanNumbersAndTechnicalDecimalsUseSharedGrammar() {
        mapOf("100000" to 100000.0, "100000,00" to 100000.0, "100000,25" to 100000.25,
            "100000.25" to 100000.25, "100.000,25" to 100000.25, "3" to 3.0,
            "3,5" to 3.5, "0" to 0.0, "0,5" to 0.5).forEach { (raw, expected) ->
            assertEquals(LoanInputValidation.Status.VALID, parse(raw).status)
            assertEquals(expected, parse(raw).value!!, 0.0)
        }
    }
    @Test fun extremeAndNonFiniteInputsNeverProducePersistableValuesInAnyField() {
        val nearMax = BigDecimal.valueOf(Double.MAX_VALUE).toPlainString()
        val aboveMax = BigDecimal.valueOf(Double.MAX_VALUE).multiply(BigDecimal.TEN).toPlainString()
        fields.forEach { field ->
            listOf("9".repeat(310), "9".repeat(1000), nearMax, aboveMax, "NaN", "Infinity", "-Infinity", "1e309", "1e3").forEach { raw ->
                assertNotNull("$field $raw", parse(raw, field).error)
                assertNull(parse(raw, field).value)
            }
        }
        assertEquals(LoanInputValidation.Status.NON_FINITE, parse("9".repeat(310)).status)
        assertEquals(LoanInputValidation.Status.OUT_OF_RANGE, parse(nearMax).status)
        assertEquals(LoanInputValidation.Status.INVALID_FORMAT, parse("1e3").status)
    }
    @Test fun emptyRequiredAndEmptyOptionalDifferFromInvalidText() {
        fields.forEach { field ->
            val empty = parse("", field)
            assertEquals(LoanInputValidation.Status.EMPTY, empty.status)
            if (field.required) { assertNotNull(empty.error); assertNull(empty.value) }
            else { assertNull(empty.error); assertEquals(field.emptyValue, empty.value!!, 0.0) }
            listOf("abc", "1,2,3", "--", "12,,3", "€", " €€ ").forEach {
                assertEquals(LoanInputValidation.Status.INVALID_FORMAT, parse(it, field).status)
                assertNull(parse(it, field).value)
            }
        }
    }
    @Test fun negativesRejectedAndRentedShareNeverClamped() {
        fields.forEach { assertNotNull(parse("-2", it).error) }
        assertNotNull(parse("-500", LoanInputValidation.Field.MONTHLY).error)
        listOf("-1", "101", "120", "abc").forEach { assertNull(parse(it, LoanInputValidation.Field.RENTED).value) }
        listOf(0.0, 50.0, 100.0).forEach { assertEquals(it, parse(it.toString(), LoanInputValidation.Field.RENTED).value!!, 0.0) }
        // Percentages other than rented share have no invented banking maximum of 100%.
        assertEquals(120.0, parse("120", LoanInputValidation.Field.INTEREST).value!!, 0.0)
    }
    @Test fun legacySpecialValuesHaveGermanRepairStateAndCanBeCorrected() {
        listOf(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN).forEach { old ->
            fields.forEach { field ->
                val text = LoanInputValidation.prefill(old)
                val result = parse(text, field)
                assertEquals(LoanInputValidation.Status.INVALID_STORED, result.status)
                assertEquals(LoanInputValidation.STORED_ERROR, result.error)
                assertNull(result.value)
                assertEquals(50.25, parse("50,25", field).value!!, 0.0)
            }
        }
    }
    @Test fun unchangedPrefillPreservesAllStoredDoubleValuesIncludingZeroAndPrecision() {
        val loan = Loan(id = 81, darlehensbetrag = 100000.25, restschuld = 0.0,
            sollzinsProzent = 3.125, tilgungProzent = 0.0, monatlicheRate = 800.12345, vermietungsanteilProzent = 87.12345)
        LoanInputValidation.numbers(loan).forEach { (field, value) ->
            assertEquals(value, parse(LoanInputValidation.prefill(value), field).value!!, 0.0)
        }
        assertEquals("0,00", LoanInputValidation.prefill(0.0))
        listOf(0.0000001, Double.MIN_VALUE, 1.2345678901234567).forEach {
            assertEquals(it, parse(LoanInputValidation.prefill(it)).value!!, 0.0)
        }
    }
    @Test fun safetyLimitBoundsProductsWithoutRoundingOrClamping() {
        val limit = LoanInputValidation.MAX_SAFE_VALUE
        assertTrue((limit * limit).isFinite())
        assertEquals(limit, parse(LoanInputValidation.prefill(limit)).value!!, 0.0)
        assertNull(parse(LoanInputValidation.prefill(limit * 2)).value)
    }
    @Test fun dateChronologyBlocksImpossibleEndsAndPreservesUnusualValidTerms() {
        assertEquals(setOf("binding", "end"), LoanInputValidation.dateErrors("2026-01-01", "2025-12-31", "2025-12-31").keys)
        assertTrue(LoanInputValidation.dateErrors("2026-01-01", "2040-01-01", "2030-01-01").isEmpty())
        assertTrue(LoanInputValidation.dateErrors("2026-01-01", "2026-01-01", "2026-01-01").isEmpty())
        assertTrue(LoanInputValidation.dateErrors("", "", "").isEmpty())
        assertEquals(setOf("start"), LoanInputValidation.dateErrors("2026-02-30", "", "").keys)
    }
}
