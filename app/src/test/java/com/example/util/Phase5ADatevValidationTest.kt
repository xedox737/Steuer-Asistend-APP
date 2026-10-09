package com.example.util

import com.example.data.BookingRecord
import com.example.data.DatevProfile
import org.junit.Assert.*
import org.junit.Test

class Phase5ADatevValidationTest {
    private val profile = DatevProfile.createDefaultSkr03().copy(beraterNummer = "1111111", mandantenNummer = "11111")
    private fun record(date: String = "2026-01-31", amount: Double = 100.0) = BookingRecord(
        bookingId = "booking", receiptId = 1, belegnummer = "BELEG-1", belegdatum = date, buchungsdatum = date,
        zahlungspartner = "Test", beschreibung = "Reparatur", bruttobetrag = amount, sollHaben = "S",
        sachkonto = "4800", gegenkonto = "1200", belegfeld1 = "BELEG-1", hauptkategorie = "Renovierung",
        objektId = "property", kost1 = "property", steuerlichesJahr = 2026)

    @Test fun impossibleCalendarDatesCannotPassDatevPrevalidation() {
        listOf("2026-02-30", "2025-02-29", "2026-04-31").forEach { date ->
            val report = BookingValidationService.validateRecords(listOf(record(date)), profile)
            assertFalse(date, report.isValidForExport)
            assertTrue(date, report.errors.any { it.field == "belegdatum" && it.message.contains(date) })
        }
        listOf("2024-02-29", "2026-01-31").forEach { date ->
            assertFalse(date, BookingValidationService.validateRecords(listOf(record(date)), profile).errors.any { it.field == "belegdatum" })
        }
    }

    @Test fun nonFiniteGrossNetVatAndSharesBlockExport() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { amount ->
            val report = BookingValidationService.validateRecords(listOf(record(amount = amount)), profile)
            assertFalse(report.isValidForExport)
            assertTrue(report.errors.any { it.field == "bruttobetrag" })
            assertTrue(report.totalAmount.isFinite())
            val details = BookingValidationService.validateRecords(listOf(record().copy(nettobetrag = amount,
                ustBetrag = amount, ustSatz = amount, anteilProzent = amount)), profile)
            assertFalse(details.isValidForExport)
            assertTrue(details.errors.map { it.field }.containsAll(listOf("nettobetrag", "ustBetrag", "ustSatz", "anteilProzent")))
        }
    }
}
