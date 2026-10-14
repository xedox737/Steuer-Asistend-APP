package com.example.ui

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class GermanNumberInputContractTest {
    @Test fun sharedGrammarAcrossMoneyAreaRentLoanDocumentAndDistanceAdapters() {
        val valid = mapOf("0" to 0.0, "-0" to -0.0, "+12,5" to 12.5, "72,5" to 72.5, "72,50" to 72.5,
            "98.0" to 98.0, "1.234" to 1234.0, "12.345" to 12345.0, "1.234,56" to 1234.56,
            "1234,56" to 1234.56, "1234.56" to 1234.56)
        valid.forEach { (raw, value) ->
            assertEquals(raw, value, GermanNumberInput.parseResult(raw).value!!, 0.0)
            assertEquals(raw, value, NumberFieldInput.nonNegative(raw).value!!, 0.0)
            assertEquals(raw, value, PropertyWizardInput.unitArea(raw)!!, 0.0)
            assertEquals(raw, value, parseTenantNumber(raw)!!, 0.0)
            assertEquals(raw, value, LoanInputValidation.parse(raw, LoanInputValidation.Field.AMOUNT).value!!, 0.0)
            assertEquals(raw, value, DocumentNumberInput.validate(mapOf("kaufpreis" to raw)).getValue("kaufpreis"), 0.0)
        }
        listOf("abc", "1,2,3", "1.2.3,45", "1.234.56,78", "NaN", "Infinity", "-Infinity", "1e3", "1e309", "9".repeat(310)).forEach { raw ->
            assertNull(raw, GermanNumberInput.parse(raw))
            assertNotNull(raw, NumberFieldInput.nonNegative(raw, optional = true).error)
            assertNull(raw, PropertyWizardInput.unitArea(raw)); assertNull(raw, parseTenantNumber(raw))
            assertNotNull(raw, LoanInputValidation.parse(raw, LoanInputValidation.Field.AMOUNT).error)
            assertTrue(raw, runCatching { DocumentNumberInput.validate(mapOf("kaufpreis" to raw)) }.isFailure)
        }
    }

    @Test fun blankNegativeAndTechnicalPrefillHaveExplicitSemantics() {
        for (raw in listOf("", " ")) {
            assertNull(NumberFieldInput.nonNegative(raw, optional = true).error)
            assertNull(NumberFieldInput.nonNegative(raw, optional = true).value)
            assertNotNull(NumberFieldInput.nonNegative(raw).error)
        }
        assertEquals(-12.5, GermanNumberInput.parse("-12,5")!!, 0.0)
        assertNotNull(NumberFieldInput.nonNegative("-12,5").error)
        listOf(98.0, 72.5, 12.345, 0.0001, 1234.56).forEach {
            assertEquals(it, GermanNumberInput.parse(NumberFieldInput.prefill(it))!!, 0.0)
        }
        val odo = DistanceEvidence(odometerStartKm = GermanNumberInput.parse("12.345"), odometerEndKm = GermanNumberInput.parse("12.365"))
        assertEquals(20.0, LogbookDistancePolicy.decide(odo).taxDistanceKm!!, 0.0)
        assertNull(LogbookDistancePolicy.decide(DistanceEvidence(manualKm = Double.NaN, routedKm = 20.0)).taxDistanceKm)
        assertNull(LogbookDistancePolicy.decide(DistanceEvidence(manualKm = -12.5, routedKm = 20.0)).taxDistanceKm)
        assertNull(LogbookDistancePolicy.decide(DistanceEvidence(odometerStartKm = 12365.0, odometerEndKm = 12345.0, routedKm = 20.0)).taxDistanceKm)
    }

    @Test fun targetTypeAndLoanBoundariesAgreeWithTextAdapters() {
        val safe = Float.MAX_VALUE.toDouble()
        val overflow = safe * 2
        assertEquals(Float.MAX_VALUE, NumericWriteBoundary.float(safe))
        assertTrue(runCatching { NumericWriteBoundary.float(overflow) }.isFailure)
        assertNull(PropertyWizardInput.unitArea(NumberFieldInput.prefill(overflow)))
        assertTrue(runCatching { NumericWriteBoundary.float(Double.NaN) }.isFailure)
        assertTrue(runCatching { NumericWriteBoundary.float(Double.NEGATIVE_INFINITY) }.isFailure)
        assertTrue(runCatching { NumericWriteBoundary.float(1e-100) }.isFailure)
        val big = "1" + "0".repeat(200)
        assertNotNull(LoanInputValidation.parse(big, LoanInputValidation.Field.AMOUNT).error)
        assertNull(PropertyWizardInput.validate("", "", "", "", "", "", "", big).values)
        assertTrue(runCatching { DocumentNumberInput.validate(mapOf("darlehensbetrag" to big)) }.isFailure)
        assertTrue(runCatching { LoanInputValidation.validateWrite(Loan(darlehensbetrag = 1e200)) }.isFailure)
        assertTrue(runCatching { LoanInputValidation.validateWrite(Loan(startDatum = "2026-10-01", laufzeitBis = "2026-09-01")) }.isFailure)
        LoanInputValidation.validateWrite(Loan(darlehensbetrag = 100000.0, restschuld = 80000.0, sollzinsProzent = 3.125))
        assertTrue(runCatching { validateBackupProperty(PropertyMetadata(gesamtKaufpreis = -0.001)) }.isFailure)
        validateBackupProperty(PropertyMetadata(gesamtKaufpreis = 0.001))
    }
}
