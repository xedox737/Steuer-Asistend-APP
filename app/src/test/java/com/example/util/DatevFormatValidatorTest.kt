package com.example.util

import com.example.data.BookingRecord
import com.example.data.DatevProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatevFormatValidatorTest {

    private val record = BookingRecord(
        bookingId = "CONF_A80A0BB9B104_1521464816",
        receiptId = 1,
        belegnummer = "BLG-A80A0BB9B10436D497",
        belegdatum = "2026-01-30",
        buchungsdatum = "2026-01-30",
        zahlungspartner = "Testbank",
        beschreibung = "Kreditzinsen",
        bruttobetrag = 580.0,
        sollHaben = "S",
        sachkonto = "2110",
        gegenkonto = "70000",
        belegfeld1 = "BLG-A80A0BB9B10436D497",
        kost1 = "Standard SKR03",
        hauptkategorie = "Zinsen & Geldbeschaffungskosten",
        steuerlichesJahr = 2026
    )

    @Test
    fun generatedFormatVersion13PassesStrictValidation() {
        val csv = DatevCsvSerializer.serializeToCsvString(
            listOf(record),
            DatevProfile.createDefaultSkr03(),
            "2026"
        )

        val result = DatevFormatValidator.validate(csv)

        assertTrue(result.errors.joinToString(" | "), result.isValid)
        assertTrue(csv.endsWith("\r\n"))
        assertFalse(Regex("(?<!\\r)\\n").containsMatchIn(csv))

        val lines = csv.removeSuffix("\r\n").split("\r\n")
        assertEquals(31, lines[0].split(";").size)
        assertEquals(125, lines[1].split(";").size)
        assertEquals(125, lines[2].split(";").size)
    }

    @Test
    fun numericAndTextFieldsUseDatevQuotingRules() {
        val row = DatevCsvSerializer.serializeBookingRecordToRow(record)
        val fields = row.split(";")

        assertEquals("580,00", fields[0])
        assertEquals("\"S\"", fields[1])
        assertEquals("2110", fields[6])
        assertEquals("70000", fields[7])
        assertEquals("3001", fields[9])
        assertEquals("", fields[100]) // field 101 is Erlöskonto, not Buchungs-GUID
        assertTrue(fields[102].matches(Regex("\"[0-9A-F-]{36}\"")))
    }

    @Test
    fun malformedLegacyExportIsRejected() {
        val legacy = "EXTF;\"700\";\"21\";Buchungsstapel;\"13\";\"20260802094125984\";;;;;\"1111111\";\"11111\";\"20260101\";\"4\";\"20260101\";\"20261231\";;;\"1\";\"0\";\"0\";EUR\n" +
            DatevCsvSerializer.EXTF_HEADER_COLUMNS_LIST.take(116).joinToString(";") + "\n" +
            Array(116) { "" }.joinToString(";")

        val result = DatevFormatValidator.validate(legacy)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("LF-Zeilenenden") })
        assertTrue(result.errors.any { it.contains("22 statt 31") })
    }
}
