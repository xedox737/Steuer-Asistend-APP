package com.example.util

import com.example.data.BookingRecord
import com.example.data.DatevProfile
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object DatevCsvSerializer {

    const val EXTF_BOOKING_COLUMN_COUNT = 125
    private const val CRLF = "\r\n"

    /** Column names for DATEV Buchungsstapel, format version 13. */
    val EXTF_HEADER_COLUMNS_LIST = listOf(
        "Umsatz (ohne Soll/Haben-Kz)", "Soll/Haben-Kennzeichen", "WKZ Umsatz", "Kurs", "Basis-Umsatz", "WKZ Basis-Umsatz", "Konto", "Gegenkonto (ohne BU-Schlüssel)", "BU-Schlüssel", "Belegdatum",
        "Belegfeld 1", "Belegfeld 2", "Skonto", "Buchungstext", "Postensperre", "Diverse Adressnummer", "Geschäftspartnerbank", "Sachverhalt", "Zinssperre", "Beleglink",
        "Beleginfo - Art 1", "Beleginfo - Inhalt 1", "Beleginfo - Art 2", "Beleginfo - Inhalt 2", "Beleginfo - Art 3", "Beleginfo - Inhalt 3", "Beleginfo - Art 4", "Beleginfo - Inhalt 4", "Beleginfo - Art 5", "Beleginfo - Inhalt 5",
        "Beleginfo - Art 6", "Beleginfo - Inhalt 6", "Beleginfo - Art 7", "Beleginfo - Inhalt 7", "Beleginfo - Art 8", "Beleginfo - Inhalt 8", "KOST1 - Kostenstelle", "KOST2 - Kostenstelle", "Kost-Menge", "EU-Land u. UStID (Bestimmung)",
        "EU-Steuersatz (Bestimmung)", "Abw. Versteuerungsart", "Sachverhalt L+L", "Funktionsergänzung L+L", "BU 49 Hauptfunktionstyp", "BU 49 Hauptfunktionsnummer", "BU 49 Funktionsergänzung", "Zusatzinformation - Art 1", "Zusatzinformation- Inhalt 1", "Zusatzinformation - Art 2",
        "Zusatzinformation- Inhalt 2", "Zusatzinformation - Art 3", "Zusatzinformation- Inhalt 3", "Zusatzinformation - Art 4", "Zusatzinformation- Inhalt 4", "Zusatzinformation - Art 5", "Zusatzinformation- Inhalt 5", "Zusatzinformation - Art 6", "Zusatzinformation- Inhalt 6", "Zusatzinformation - Art 7",
        "Zusatzinformation- Inhalt 7", "Zusatzinformation - Art 8", "Zusatzinformation- Inhalt 8", "Zusatzinformation - Art 9", "Zusatzinformation- Inhalt 9", "Zusatzinformation - Art 10", "Zusatzinformation- Inhalt 10", "Zusatzinformation - Art 11", "Zusatzinformation- Inhalt 11", "Zusatzinformation - Art 12",
        "Zusatzinformation- Inhalt 12", "Zusatzinformation - Art 13", "Zusatzinformation- Inhalt 13", "Zusatzinformation - Art 14", "Zusatzinformation- Inhalt 14", "Zusatzinformation - Art 15", "Zusatzinformation- Inhalt 15", "Zusatzinformation - Art 16", "Zusatzinformation- Inhalt 16", "Zusatzinformation - Art 17",
        "Zusatzinformation- Inhalt 17", "Zusatzinformation - Art 18", "Zusatzinformation- Inhalt 18", "Zusatzinformation - Art 19", "Zusatzinformation- Inhalt 19", "Zusatzinformation - Art 20", "Zusatzinformation- Inhalt 20", "Stück", "Gewicht", "Zahlweise",
        "Forderungsart", "Veranlagungsjahr", "Zugeordnete Fälligkeit", "Skontotyp", "Auftragsnummer", "Buchungstyp", "USt-Schlüssel (Anzahlungen)", "EU-Land (Anzahlungen)", "Sachverhalt L+L (Anzahlungen)", "EU-Steuersatz (Anzahlungen)",
        "Erlöskonto (Anzahlungen)", "Herkunft-Kz", "Buchungs GUID", "KOST-Datum", "SEPA-Mandatsreferenz", "Skontosperre", "Gesellschaftername", "Beteiligtennummer", "Identifikationsnummer", "Zeichnernummer",
        "Postensperre bis", "Bezeichnung SoBil-Sachverhalt", "Kennzeichen SoBil-Buchung", "Festschreibung", "Leistungsdatum", "Datum Zuord. Steuerperiode", "Fälligkeit", "Generalumkehr (GU)", "Steuersatz", "Land",
        "Abrechnungsreferenz", "BVV-Position", "EU-Land u. UStID (Ursprung)", "EU-Steuersatz (Ursprung)", "Abw. Skontokonto"
    )

    val EXTF_HEADER_COLUMNS_125 = EXTF_HEADER_COLUMNS_LIST.joinToString(";")

    init {
        check(EXTF_HEADER_COLUMNS_LIST.size == EXTF_BOOKING_COLUMN_COUNT)
    }

    fun generateExtfHeader(profile: DatevProfile, year: String): String {
        val timestamp = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.GERMANY).format(Date())
        val startYearDate = "${year}0101"
        val endYearDate = "${year}1231"
        val festschreibung = if (profile.festschreibungskennzeichen) "1" else "0"
        val chart = profile.kontenrahmen.filter(Char::isDigit).takeLast(2)
        val description = "Buchungsstapel $year".take(30)

        val fields = listOf(
            quoteText("EXTF"), "700", "21", quoteText("Buchungsstapel"), "13", timestamp,
            "", quoteText(""), quoteText(""), quoteText(""),
            profile.beraterNummer, profile.mandantenNummer, startYearDate,
            profile.sachkontenLaenge.toString(), startYearDate, endYearDate,
            quoteText(description), quoteText(""), "1", "0", festschreibung,
            quoteText(profile.waehrung), "", quoteText(""), "", "",
            quoteText(chart), "", "", quoteText(""), quoteText("")
        )
        check(fields.size == 31)
        return fields.joinToString(";")
    }

    fun serializeBookingRecordToRow(record: BookingRecord): String {
        val fields = Array(EXTF_BOOKING_COLUMN_COUNT) { "" }

        fields[0] = String.format(Locale.GERMANY, "%.2f", Math.abs(record.bruttobetrag))
        fields[1] = quoteText(record.sollHaben)
        fields[2] = quoteText("")
        fields[5] = quoteText("")
        fields[6] = record.sachkonto
        fields[7] = record.gegenkonto
        fields[8] = quoteText(record.buSchluessel)

        val belegdatumDdMm = record.belegdatum
            .takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
            ?.split("-")
            ?.let { "${it[2]}${it[1]}" }
            ?: "0101"
        fields[9] = belegdatumDdMm
        fields[10] = quoteText(record.belegfeld1.take(36))
        fields[11] = quoteText(record.belegfeld2.take(12))
        fields[13] = quoteText(cleanText(record.beschreibung, 60))
        fields[15] = quoteText("")
        fields[19] = quoteText("")

        for (index in 20..35) fields[index] = quoteText("")
        fields[36] = quoteText(record.kost1.take(36))
        fields[37] = quoteText(record.kost2.take(36))
        fields[39] = quoteText("")
        fields[41] = quoteText("")

        for (index in 47..86) fields[index] = quoteText("")
        fields[47] = quoteText("Objekt")
        fields[48] = quoteText(record.kost1.ifBlank { "Immobilie" }.take(40))
        if (record.wohneinheitId.isNotBlank()) {
            fields[49] = quoteText("Wohneinheit")
            fields[50] = quoteText(record.wohneinheitId.take(40))
        }

        fields[94] = quoteText("")
        fields[95] = quoteText("")
        fields[97] = quoteText("")
        fields[101] = quoteText("")
        fields[102] = quoteText(stableBookingGuid(record.bookingId))
        fields[104] = quoteText("")
        fields[106] = quoteText("")
        fields[108] = quoteText("")
        fields[109] = quoteText("")
        fields[111] = quoteText("")
        fields[119] = quoteText("")
        fields[120] = quoteText("")
        fields[122] = quoteText("")

        return fields.joinToString(";")
    }

    fun serializeToCsvString(records: List<BookingRecord>, profile: DatevProfile, year: String): String {
        val lines = buildList {
            add(generateExtfHeader(profile, year))
            add(EXTF_HEADER_COLUMNS_125)
            records.forEach { add(serializeBookingRecordToRow(it)) }
        }
        return lines.joinToString(CRLF, postfix = CRLF)
    }

    fun createControlCsv(records: List<BookingRecord>): String {
        val lines = mutableListOf(
            "Buchung_ID;Beleg_ID;Belegdatum;Belegnummer;Zahlungspartner;Betrag_EUR;Soll_Haben;Sachkonto;Gegenkonto;KOST1_Objekt;KOST2_Einheit;Kategorie;Pruefstatus;Exportstatus"
        )
        records.forEach { record ->
            val amount = String.format(Locale.GERMANY, "%.2f", record.bruttobetrag)
            lines += listOf(
                record.bookingId,
                record.receiptId.toString(),
                record.belegdatum,
                quoteText(record.belegfeld1),
                quoteText(record.zahlungspartner),
                amount,
                record.sollHaben,
                record.sachkonto,
                record.gegenkonto,
                quoteText(record.kost1),
                quoteText(record.kost2),
                quoteText(record.hauptkategorie),
                record.pruefstatus,
                record.exportStatus
            ).joinToString(";")
        }
        return lines.joinToString(CRLF, postfix = CRLF)
    }

    internal fun quoteText(value: String): String =
        "\"" + value
            .replace("\"", "\"\"")
            .replace(Regex("[\\r\\n\\t]"), " ") + "\""

    private fun cleanText(value: String, maxLength: Int): String =
        value.replace(Regex("[\\r\\n\\t]"), " ").take(maxLength)

    private fun stableBookingGuid(bookingId: String): String =
        UUID.nameUUIDFromBytes("datev-booking:$bookingId".toByteArray(StandardCharsets.UTF_8))
            .toString()
            .uppercase(Locale.ROOT)
}
