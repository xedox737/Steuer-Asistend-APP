package com.example.ui

import com.example.api.BankStatementMatchItem
import com.example.api.BankStatementReconciliationResult
import com.example.api.GoogleDriveClient
import com.example.data.BankImportParser
import com.example.data.LogbookTrip
import com.example.data.Receipt
import com.example.data.Loan
import com.example.data.PropertyMetadata
import com.example.util.LogbookCsvExporter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Phase10AExportPrecisionTest {
    // Record boundaries are only unquoted newlines; field decoding uses the existing CSV parser.
    private fun records(csv: String): List<List<String>> {
        val rows = mutableListOf<String>()
        val row = StringBuilder()
        var quoted = false
        var index = 0
        while (index < csv.length) {
            val char = csv[index]
            if (char == '"') {
                row.append(char)
                if (quoted && index + 1 < csv.length && csv[index + 1] == '"') row.append(csv[++index])
                else quoted = !quoted
            } else if (char == '\n' && !quoted) { rows.add(row.toString()); row.clear() }
            else row.append(char)
            index++
        }
        assertFalse("Unclosed CSV field", quoted)
        if (row.isNotEmpty()) rows.add(row.toString())
        return rows.map { BankImportParser.splitCsvLine(it, ';') }
    }
    @Test fun optionalDriveUnitsCsvKeepsSeparatorsQuotesAndNewlinesInsideTwoUnits() {
        val name = "WE 01, EG; links | Altbau/\"Nord\"\nAnbau"
        val unit = WohneinheitStatus(name, "Lage; \"EG\"", "Vermietet", "Martin; Weber", 600.5, 41.25, "2020-01-01", "U1")
        val second = unit.copy(name = "WE 02", label = "WE 02", unitId = "U2", wohnflaeche = 55.5)
        val decoded = records(GoogleDriveClient.unitsCsv(listOf(unit, second)))
        assertEquals(3, decoded.size)
        assertTrue(decoded.all { it.size == 7 })
        assertEquals(listOf(name, unit.label, unit.status, unit.mieter, "600.5", "41.25", unit.mietvertragsstart), decoded[1])
        assertEquals("WE 02", decoded[2][0])
        assertEquals(name, unit.name)
        assertEquals("U1", unit.unitId)
    }
    @Test fun optionalDriveLedgerCsvDoesNotReplaceSemicolonsOrBreakDescriptionsIntoRows() {
        val receipt = Receipt(101, "Handel; \"Nord\"", "2026-10-01", "12:30", 119.25,
            "Werbungskosten; Haus", "Material; Bad", "4801", "Zeile eins; \"Original\"\nZeile zwei", propertyId = "P1", unitId = "U1")
        val decoded = records(GoogleDriveClient.ledgerCsv(listOf(receipt)))
        assertEquals(2, decoded.size)
        assertEquals(10, decoded[1].size)
        assertEquals(receipt.aussteller, decoded[1][1])
        assertEquals(receipt.hauptkategorie, decoded[1][5])
        assertEquals(receipt.unterkategorie, decoded[1][6])
        assertEquals(receipt.beschreibung, decoded[1][9])
        assertEquals("119.25", decoded[1][4])
    }
    @Test fun structuredBankReconciliationExportPreservesAllTextWithoutTouchingTheBankList() {
        val item = BankStatementMatchItem(date = "2026-10-01", counterparty = "Weber; \"Nord\"", amount = 700.25,
            isIncome = true, purpose = "Miete; WE 01, EG\nOktober", notes = "Hinweis; \"Original\"\nZweite Zeile")
        val decoded = records(bankStatementCsv("", BankStatementReconciliationResult(items = listOf(item))))
        assertEquals(2, decoded.size)
        assertEquals(listOf(item.date, item.counterparty, "700,25", "Einnahme", item.purpose, "Zugeordnet", item.notes), decoded[1])
    }
    @Test fun bankRawTextWithoutStructuredResultIsExportedWholeWithoutGuessedPipeFields() {
        val raw = "2026-10-01 | Handel|Nord | 119,25 | Zweck; \"Original\"\nZeile zwei"
        val decoded = records(bankStatementCsv(raw, null))
        assertEquals(2, decoded.size)
        assertEquals(listOf("", raw, "", "", "", "Unformatiert", ""), decoded[1])
        assertEquals(1, records(bankStatementCsv("", null)).size)
    }
    @Test fun advisorOverviewPreservesStoredAreasInterestRepaymentAndRentalShare() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val property = PropertyMetadata(propertyId = "P1", wohnflaeche = 41.255, grundstuecksgroesse = 100.125)
        val loan = Loan(propertyId = "P1", sollzinsProzent = 4.375, tilgungProzent = 2.125, vermietungsanteilProzent = 87.625)
        val summary = buildAdvisorAnnualSummary(context, 2026, emptyList(), property, listOf(loan), emptyList())
        assertTrue(summary.propertyOverview.contains("Wohnfläche: 41,255 m²"))
        assertTrue(summary.propertyOverview.contains("Grundstücksgröße: 100,125 m²"))
        assertTrue(summary.financing.contains("  Sollzins: 4,375 %"))
        assertTrue(summary.financing.contains("  Tilgung: 2,125 %"))
        assertTrue(summary.financing.contains("  Vermietungsanteil: 87,625 %"))
        assertEquals(4.375, loan.sollzinsProzent, 0.0)
        assertEquals(41.255, property.wohnflaeche, 0.0)
    }
    @Test fun logbookCsvRetainsEachStoredDistanceAndOdometerDecimal() {
        val trip = LogbookTrip(date = "2026-10-01", purpose = "Besichtigung; \"Nord\"", startAddress = "A", destinationAddress = "B",
            taxDistanceKm = 1.25, kilometerSource = "MANUELL", aiEstimatedKm = 1.99, routedKm = 1.75,
            gpsMeasuredKm = 1.5, odometerStartKm = 12345.25, odometerEndKm = 12346.5, manualKm = 1.25,
            createdAt = "now", updatedAt = "now")
        val decoded = records(LogbookCsvExporter.create(listOf(trip)))
        val row = decoded[0].zip(decoded[1]).toMap()
        assertEquals("1,25", row["Steuerliche Kilometer"])
        assertEquals("1,99", row["KI-Schaetzung"])
        assertEquals("1,75", row["Strassenroute"])
        assertEquals("1,5", row["GPS"])
        assertEquals("12345,25", row["Tacho Start"])
        assertEquals("12346,5", row["Tacho Ende"])
        assertEquals("1,25", row["Manuelle Kilometer"])
        assertEquals(trip.purpose, row["Fahrtzweck"])
    }
}
