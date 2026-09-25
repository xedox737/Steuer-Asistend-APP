package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AfaShorterDurationTest {
    private val property = PropertyMetadata(
        baujahr = 1954,
        gebaeudewert = 200_000.0,
        grundUndBodenWert = 50_000.0,
        gesamtKaufpreis = 250_000.0,
        uebergangNutzenLasten = "2026-01-01"
    )

    @Test fun `shorter duration stays scenario until it is documented and confirmed`() {
        val scenario = property.copy(
            afaShorterYears = 25,
            afaShorterStartDate = "2026-01-01",
            afaShorterReason = "Gutachten",
            afaShorterDocumentId = "document-1"
        )
        val pending = TaxPropertyCalculator.calculate(scenario, emptyList())
        assertEquals(4_000.0, pending.annualAfa, 0.01)
        assertEquals(8_000.0, pending.shorterAnnualAfa, 0.01)
        assertFalse(pending.shorterMethodActive)

        val confirmed = TaxPropertyCalculator.calculate(scenario.copy(afaShorterConfirmed = true), emptyList())
        assertTrue(confirmed.shorterMethodActive)
        assertEquals(8_000.0, confirmed.annualAfa, 0.01)
        assertEquals(8_000.0, confirmed.firstYearAfa, 0.01)

        val undocumented = TaxPropertyCalculator.calculate(scenario.copy(afaShorterConfirmed = true, afaShorterDocumentId = ""), emptyList())
        assertFalse(undocumented.shorterMethodActive)
        assertEquals(4_000.0, undocumented.annualAfa, 0.01)
    }

    @Test fun `later start cannot silently replace an existing annual schedule`() {
        val result = TaxPropertyCalculator.calculate(property.copy(
            afaShorterYears = 25, afaShorterStartDate = "2027-01-01",
            afaShorterReason = "Gutachten", afaShorterDocumentId = "document-1", afaShorterConfirmed = true
        ), emptyList())
        assertFalse(result.shorterMethodActive)
        assertEquals(4_000.0, result.annualAfa, 0.01)
    }
}
