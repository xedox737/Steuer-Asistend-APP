package com.example.util

import com.example.data.AccountingApprovalJson
import com.example.data.BookingRecord
import com.example.data.DatevProfile
import com.example.data.PersistedAllocation
import com.example.data.PersistedBookingProposal
import com.example.data.Receipt
import com.example.data.ReceiptItem
import com.example.data.ReceiptItemConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatevExporterTest {

    @Test
    fun testExtfHeaderGeneration() {
        val profile = DatevProfile.createDefaultSkr03()
        val header1 = DatevCsvSerializer.generateExtfHeader(profile, "2026")

        assertTrue(header1.startsWith("\"EXTF\";700;21;\"Buchungsstapel\";13;"))
        assertTrue(header1.contains(";1111111;11111;"))
        assertTrue(header1.contains(";\"EUR\";"))
        assertEquals(31, header1.split(";").size)

        val columns = DatevCsvSerializer.EXTF_HEADER_COLUMNS_125.split(";")
        assertEquals(125, columns.size)
    }

    @Test
    fun legacyEntryPointBlocksReceiptWithoutApproval() {
        val receipt = approvedReceipt().copy(
            freigabestatus = "OFFEN",
            allocationsJson = "",
            bookingProposalsJson = ""
        )

        val failure = runCatching {
            DatevExporter.generateBuchungsstapelCsv(
                receipts = listOf(receipt),
                config = DatevConfig(wirtschaftsjahr = 2026)
            )
        }.exceptionOrNull()

        assertNotNull(failure)
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("Freigabe"))
    }

    @Test
    fun legacyEntryPointProducesStrictDatevFormatForApprovedReceipt() {
        val csv = DatevExporter.generateBuchungsstapelCsv(
            receipts = listOf(approvedReceipt()),
            config = DatevConfig(wirtschaftsjahr = 2026, chartType = "SKR03")
        )

        val validation = DatevFormatValidator.validate(csv)
        assertTrue(validation.errors.joinToString(" | "), validation.isValid)
    }

    // 1. Zehn Positionen mit identischer Zuordnung ergeben eine DATEV-Zeile.
    @Test
    fun testTenIdenticalPositionsGrouping() {
        val profile = DatevProfile.createDefaultSkr03()
        val items = (1..10).map { i ->
            ReceiptItem(
                bezeichnung = "Artikel #$i",
                menge = 1.0,
                einzelpreis = 62.0,
                gesamtpreis = 62.0,
                hauptkategorie = "Instandhaltung & Reparaturen",
                unterkategorie = "Baumaterial",
                wohneinheit = "OG links",
                massnahme = "Komplettsanierung OG links",
                kontoNr = "4801"
            )
        }
        val receipt = Receipt(
            id = 1001,
            aussteller = "Bauhaus",
            datum = "2026-06-01",
            uhrzeit = "09:00",
            bruttobetrag = 620.0,
            hauptkategorie = "Instandhaltung & Reparaturen",
            unterkategorie = "Baumaterial",
            kontoNr = "4801",
            beschreibung = "Komplettsanierung Baustoffe",
            positionenJson = ReceiptItemConverter.toJson(items)
        )

        val records = DatevMappingService.buildDatevBookingRows(receipt, profile)
        assertEquals(1, records.size)
        assertEquals(620.0, records[0].bruttobetrag, 0.001)
    }

    // 2. Drei Kostenarten ergeben drei DATEV-Zeilen.
    @Test
    fun testThreeCostCategoriesGrouping() {
        val profile = DatevProfile.createDefaultSkr03()
        val items = listOf(
            ReceiptItem(bezeichnung = "Elektrokabel", gesamtpreis = 180.0, hauptkategorie = "Instandhaltung & Reparaturen", unterkategorie = "Elektro", kontoNr = "4801"),
            ReceiptItem(bezeichnung = "Siphon & Rohre", gesamtpreis = 95.0, hauptkategorie = "Instandhaltung & Reparaturen", unterkategorie = "Sanitär", kontoNr = "4801"),
            ReceiptItem(bezeichnung = "Parkettboden", gesamtpreis = 320.0, hauptkategorie = "Instandhaltung & Reparaturen", unterkategorie = "Boden", kontoNr = "4801")
        )
        val receipt = Receipt(
            id = 1002,
            aussteller = "Hornbach",
            datum = "2026-06-02",
            uhrzeit = "10:00",
            bruttobetrag = 595.0,
            hauptkategorie = "Instandhaltung & Reparaturen",
            unterkategorie = "Baumaterial",
            kontoNr = "4801",
            beschreibung = "Sanierungsmaterialien",
            positionenJson = ReceiptItemConverter.toJson(items)
        )

        val records = DatevMappingService.buildDatevBookingRows(receipt, profile)
        assertEquals(3, records.size)
        assertEquals(595.0, records.sumOf { it.bruttobetrag }, 0.001)
    }

    // 3. Zwei Wohneinheiten ergeben zwei DATEV-Zeilen.
    @Test
    fun testTwoHousingUnitsGrouping() {
        val profile = DatevProfile.createDefaultSkr03()
        val items = listOf(
            ReceiptItem(bezeichnung = "Farbe WE1", gesamtpreis = 100.0, hauptkategorie = "Instandhaltung & Reparaturen", wohneinheit = "WE 1", kontoNr = "4801"),
            ReceiptItem(bezeichnung = "Farbe WE2", gesamtpreis = 150.0, hauptkategorie = "Instandhaltung & Reparaturen", wohneinheit = "WE 2", kontoNr = "4801")
        )
        val receipt = Receipt(
            id = 1003,
            aussteller = "Malerfachhandel",
            datum = "2026-06-03",
            uhrzeit = "11:00",
            bruttobetrag = 250.0,
            hauptkategorie = "Instandhaltung & Reparaturen",
            unterkategorie = "Malerarbeiten",
            kontoNr = "4801",
            beschreibung = "Wandfarben",
            positionenJson = ReceiptItemConverter.toJson(items)
        )

        val records = DatevMappingService.buildDatevBookingRows(receipt, profile)
        assertEquals(2, records.size)
    }

    // 4. Zwei Maßnahmen ergeben zwei DATEV-Zeilen.
    @Test
    fun testTwoMeasuresGrouping() {
        val profile = DatevProfile.createDefaultSkr03()
        val items = listOf(
            ReceiptItem(bezeichnung = "Sanierung OG", gesamtpreis = 400.0, hauptkategorie = "Instandhaltung & Reparaturen", massnahme = "Sanierung OG", kontoNr = "4801"),
            ReceiptItem(bezeichnung = "Wartung Dach", gesamtpreis = 300.0, hauptkategorie = "Instandhaltung & Reparaturen", massnahme = "Wartung Dach", kontoNr = "4801")
        )
        val receipt = Receipt(
            id = 1004,
            aussteller = "Handwerker GmbH",
            datum = "2026-06-04",
            uhrzeit = "12:00",
            bruttobetrag = 700.0,
            hauptkategorie = "Instandhaltung & Reparaturen",
            unterkategorie = "Handwerker",
            kontoNr = "4801",
            beschreibung = "Arbeiten",
            positionenJson = ReceiptItemConverter.toJson(items)
        )

        val records = DatevMappingService.buildDatevBookingRows(receipt, profile)
        assertEquals(2, records.size)
    }

    // 5. Gleiche Kostenart, aber unterschiedlicher Steuerschlüssel ergibt zwei Zeilen.
    @Test
    fun testDifferentTaxKeyGrouping() {
        val profile = DatevProfile.createDefaultSkr03()
        val items = listOf(
            ReceiptItem(bezeichnung = "Pos A", gesamtpreis = 100.0, hauptkategorie = "Instandhaltung & Reparaturen", buSchluessel = "9", kontoNr = "4801"),
            ReceiptItem(bezeichnung = "Pos B", gesamtpreis = 100.0, hauptkategorie = "Instandhaltung & Reparaturen", buSchluessel = "8", kontoNr = "4801")
        )
        val receipt = Receipt(
            id = 1005,
            aussteller = "Lieferant",
            datum = "2026-06-05",
            uhrzeit = "13:00",
            bruttobetrag = 200.0,
            hauptkategorie = "Instandhaltung & Reparaturen",
            unterkategorie = "Material",
            kontoNr = "4801",
            beschreibung = "Mischbeleg",
            positionenJson = ReceiptItemConverter.toJson(items)
        )

        val records = DatevMappingService.buildDatevBookingRows(receipt, profile)
        assertEquals(2, records.size)
    }

    // 6. Gleiche Kostenart, aber unterschiedlicher Umsatzsteuersatz ergibt zwei Zeilen.
    @Test
    fun testDifferentVatRateGrouping() {
        val profile = DatevProfile.createDefaultSkr03()
        val items = listOf(
            ReceiptItem(bezeichnung = "Pos 19%", gesamtpreis = 119.0, hauptkategorie = "Instandhaltung & Reparaturen", steuersatz = 19.0, kontoNr = "4801"),
            ReceiptItem(bezeichnung = "Pos 7%", gesamtpreis = 107.0, hauptkategorie = "Instandhaltung & Reparaturen", steuersatz = 7.0, kontoNr = "4801")
        )
        val receipt = Receipt(
            id = 1006,
            aussteller = "Baustoffhandel",
            datum = "2026-06-06",
            uhrzeit = "14:00",
            bruttobetrag = 226.0,
            hauptkategorie = "Instandhaltung & Reparaturen",
            unterkategorie = "Material",
            kontoNr = "4801",
            beschreibung = "Verschiedene Steuersätze",
            positionenJson = ReceiptItemConverter.toJson(items)
        )

        val records = DatevMappingService.buildDatevBookingRows(receipt, profile)
        assertEquals(2, records.size)
    }

    // 7. Privatanteil wird entsprechend dem Kanzleiprofil behandelt.
    @Test
    fun testPrivateShareExportMode() {
        val items = listOf(
            ReceiptItem(bezeichnung = "Werkzeug Vermietung", gesamtpreis = 100.0, hauptkategorie = "Instandhaltung & Reparaturen", kontoNr = "4801"),
            ReceiptItem(bezeichnung = "Privates Spielzeug", gesamtpreis = 40.0, hauptkategorie = "Privatanteil", privatanteilProzent = 100.0, kontoNr = "4801")
        )
        val receipt = Receipt(
            id = 1007,
            aussteller = "Supermarkt",
            datum = "2026-06-07",
            uhrzeit = "15:00",
            bruttobetrag = 140.0,
            hauptkategorie = "Instandhaltung & Reparaturen",
            unterkategorie = "Mischkauf",
            kontoNr = "4801",
            beschreibung = "Einkauf",
            positionenJson = ReceiptItemConverter.toJson(items)
        )

        // Mode 1: Separate booking row
        val profileSeparate = DatevProfile.createDefaultSkr03().copy(privateShareExportMode = "SEPARATE_BOOKING_ROW")
        val recordsSep = DatevMappingService.buildDatevBookingRows(receipt, profileSeparate)
        assertEquals(2, recordsSep.size)

        // Mode 2: Exclude with documentation
        val profileExclude = DatevProfile.createDefaultSkr03().copy(privateShareExportMode = "EXCLUDE_WITH_DOCUMENTATION")
        val recordsEx = DatevMappingService.buildDatevBookingRows(receipt, profileExclude)
        assertEquals(1, recordsEx.size)
        assertEquals(100.0, recordsEx[0].bruttobetrag, 0.001)
    }

    // 8. Centbeträge werden ohne Rundungsfehler addiert.
    @Test
    fun testExactCentMathWithoutRoundingError() {
        val profile = DatevProfile.createDefaultSkr03()
        val items = listOf(
            ReceiptItem(bezeichnung = "Cent A", gesamtpreis = 12.33, hauptkategorie = "Instandhaltung & Reparaturen", kontoNr = "4801"),
            ReceiptItem(bezeichnung = "Cent B", gesamtpreis = 45.67, hauptkategorie = "Instandhaltung & Reparaturen", kontoNr = "4801")
        )
        val receipt = Receipt(
            id = 1008,
            aussteller = "Laden",
            datum = "2026-06-08",
            uhrzeit = "16:00",
            bruttobetrag = 58.00,
            hauptkategorie = "Instandhaltung & Reparaturen",
            unterkategorie = "Kleinteile",
            kontoNr = "4801",
            beschreibung = "Cent-Test",
            positionenJson = ReceiptItemConverter.toJson(items)
        )

        val records = DatevMappingService.buildDatevBookingRows(receipt, profile)
        assertEquals(1, records.size)
        assertEquals(58.00, records[0].bruttobetrag, 0.001)
    }

    // 9. Die Summe der Exportzeilen entspricht exakt der exportierbaren Belegsumme.
    @Test
    fun testSumOfExportRowsEqualsReceiptTotal() {
        val profile = DatevProfile.createDefaultSkr03()
        val items = listOf(
            ReceiptItem(bezeichnung = "Item 1", gesamtpreis = 33.33, hauptkategorie = "Instandhaltung & Reparaturen", unterkategorie = "A", kontoNr = "4801"),
            ReceiptItem(bezeichnung = "Item 2", gesamtpreis = 33.33, hauptkategorie = "Instandhaltung & Reparaturen", unterkategorie = "B", kontoNr = "4801"),
            ReceiptItem(bezeichnung = "Item 3", gesamtpreis = 33.34, hauptkategorie = "Instandhaltung & Reparaturen", unterkategorie = "C", kontoNr = "4801")
        )
        val receipt = Receipt(
            id = 1009,
            aussteller = "Store",
            datum = "2026-06-09",
            uhrzeit = "17:00",
            bruttobetrag = 100.00,
            hauptkategorie = "Instandhaltung & Reparaturen",
            unterkategorie = "Splits",
            kontoNr = "4801",
            beschreibung = "Dreier-Split",
            positionenJson = ReceiptItemConverter.toJson(items)
        )

        val records = DatevMappingService.buildDatevBookingRows(receipt, profile)
        val totalExportSum = records.sumOf { it.bruttobetrag }
        assertEquals(100.00, totalExportSum, 0.001)

        val sumIssues = BookingValidationService.validateReceiptSum(receipt, records, profile)
        assertTrue(sumIssues.isEmpty())
    }

    // 10. Eine unvollständige Aufteilung verhindert den Export.
    @Test
    fun testIncompleteAllocationBlocksExport() {
        val profile = DatevProfile.createDefaultSkr03()
        val receipt = Receipt(
            id = 1010,
            aussteller = "Unbekannter Laden",
            datum = "2026-06-10",
            uhrzeit = "18:00",
            bruttobetrag = 50.00,
            hauptkategorie = "", // Empty category = incomplete
            unterkategorie = "",
            kontoNr = "",
            beschreibung = "Unvollständig"
        )

        val records = DatevMappingService.buildDatevBookingRows(receipt, profile)
        val report = BookingValidationService.validateRecords(records, profile)

        assertFalse(report.isValidForExport)
        assertTrue(report.errors.any { it.field == "hauptkategorie" })
    }

    // 11. Mehrere Exportzeilen referenzieren dasselbe Originaldokument.
    @Test
    fun testMultipleRowsReferenceSameOriginalDocument() {
        val profile = DatevProfile.createDefaultSkr03()
        val items = listOf(
            ReceiptItem(bezeichnung = "Elektro", gesamtpreis = 180.0, hauptkategorie = "Instandhaltung & Reparaturen", unterkategorie = "Elektro", kontoNr = "4801"),
            ReceiptItem(bezeichnung = "Sanitär", gesamtpreis = 95.0, hauptkategorie = "Instandhaltung & Reparaturen", unterkategorie = "Sanitär", kontoNr = "4801")
        )
        val receipt = Receipt(
            id = 1011,
            aussteller = "Grosshandel",
            datum = "2026-06-11",
            uhrzeit = "19:00",
            bruttobetrag = 275.00,
            hauptkategorie = "Instandhaltung & Reparaturen",
            unterkategorie = "Material",
            kontoNr = "4801",
            beschreibung = "Beleg mit 2 Zeilen",
            imageUrl = "/path/to/original_receipt_1011.pdf",
            positionenJson = ReceiptItemConverter.toJson(items)
        )

        val records = DatevMappingService.buildDatevBookingRows(receipt, profile)
        assertEquals(2, records.size)

        val rec1 = records[0]
        val rec2 = records[1]

        assertEquals(rec1.receiptId, rec2.receiptId)
        assertEquals(rec1.originalFileId, rec2.originalFileId)
        assertEquals(rec1.belegnummer, rec2.belegnummer)
        assertEquals(rec1.belegfeld1, rec2.belegfeld1)
        assertEquals(rec1.belegdatum, rec2.belegdatum)
        assertEquals(rec1.zahlungspartner, rec2.zahlungspartner)
        assertEquals("/path/to/original_receipt_1011.pdf", rec1.originalFileId)
    }

    // 12. Ein bereits exportierter Beleg wird nicht unbemerkt erneut exportiert.
    @Test
    fun testAlreadyExportedReceiptWarning() {
        val profile = DatevProfile.createDefaultSkr03()
        val receipt = Receipt(
            id = 1012,
            aussteller = "Aral",
            datum = "2026-06-12",
            uhrzeit = "20:00",
            bruttobetrag = 70.00,
            hauptkategorie = "Fahrtkosten & Reisekosten",
            unterkategorie = "Kraftstoff",
            kontoNr = "4670",
            beschreibung = "Tanken",
            exportStatus = "EXPORTIERT",
            pruefstatus = "GEPRUEFT"
        )

        val records = DatevMappingService.buildDatevBookingRows(receipt, profile)
        val report = BookingValidationService.validateRecords(records, profile)

        assertTrue(report.warnings.any { it.field == "exportStatus" && it.message.contains("bereits früher exportiert") })
    }

    private fun approvedReceipt() = Receipt(
        id = 2001,
        aussteller = "Test GmbH",
        datum = "2026-08-01",
        uhrzeit = "",
        bruttobetrag = 100.0,
        hauptkategorie = "Instandhaltung & Reparaturen",
        unterkategorie = "Reparatur",
        kontoNr = "4801",
        beschreibung = "Anonymisierter Testbeleg",
        internalId = "receipt-2001",
        allocationsJson = AccountingApprovalJson.encodeAllocations(
            listOf(PersistedAllocation("a", "Reparatur", 100.0, 10_000))
        ),
        bookingProposalsJson = AccountingApprovalJson.encodeBookingProposals(
            listOf(PersistedBookingProposal("a", "4801", "70000", 10_000, ""))
        ),
        freigabestatus = "FREIGEGEBEN"
    )
}
