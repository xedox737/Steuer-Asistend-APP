package com.example.ui

import org.junit.Assert.*
import org.junit.Test

class Phase7BLoanNumberTest {
    @Test fun centralGermanPrefillDisplaysStoredValuesWithoutTechnicalDecimalPoints() {
        mapOf(100000.0 to "100000,00", 90000.0 to "90000,00", 3.0 to "3,00", 3.5 to "3,50", 100000.25 to "100000,25").forEach { (number, expected) ->
            assertEquals(expected, GermanNumberInput.formatForInput(number, maxOf(2, number.toBigDecimal().scale())))
        }
    }
    @Test fun preservingStoredScaleKeepsDoubleSemanticsIncludingRatesBeyondTwoDecimals() {
        listOf(100000.0, 90000.0, 3.0, 3.5, 100000.25, 3.125, 87.12345).forEach { number ->
            val prefill = GermanNumberInput.formatForInput(number, maxOf(2, number.toBigDecimal().scale()))
            assertEquals(number, GermanNumberInput.parse(prefill)!!, 0.0)
        }
    }
}
