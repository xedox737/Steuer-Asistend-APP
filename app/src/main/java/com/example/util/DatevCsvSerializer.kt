package com.example.util

import com.example.data.BookingRecord
import com.example.data.DatevProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DatevCsvSerializer {

    /**
     * Header Column Titles (exact 116 columns according to DATEV EXTF spec Formatversion 700)
     */
    val EXTF_HEADER_COLUMNS_LIST = listOf(
        "\"Umsatz (ohne Soll/Haben-Kz)\"", "\"Soll/Haben-Kennzeichen\"", "\"WKZ Umsatz\"", "Kurs", "\"Basis-Umsatz\"", "\"WKZ Basis-Umsatz\"", "Konto", "\"Gegenkonto (ohne BU-Schlüssel)\"", "\"BU-Schlüssel\"", "Belegdatum",
        "\"Belegfeld 1\"", "\"Belegfeld 2\"", "Skonto", "Buchungstext", "Postensperre", "\"Diverse Adressnummer\"", "Geschäftspartnerbank", "Sachverhalt", "Zinssperre", "Beleglink",
        "\"Beleginfo - Art 1\"", "\"Beleginfo - Inhalt 1\"", "\"Beleginfo - Art 2\"", "\"Beleginfo - Inhalt 2\"", "\"Beleginfo - Art 3\"", "\"Beleginfo - Inhalt 3\"", "\"Beleginfo - Art 4\"", "\"Beleginfo - Inhalt 4\"", "\"Beleginfo - Art 5\"", "\"Beleginfo - Inhalt 5\"",
        "\"Beleginfo - Art 6\"", "\"Beleginfo - Inhalt 6\"", "\"Beleginfo - Art 7\"", "\"Beleginfo - Inhalt 7\"", "\"Beleginfo - Art 8\"", "\"Beleginfo - Inhalt 8\"", "\"KOST1 - Kostenstelle\"", "\"KOST2 - Kostenstelle\"", "\"Kost-Menge\"", "\"EU-Land u. UStID (Bestimmung)\"",
        "\"EU-Steuersatz\"", "\"Abw. Versteuerungsart\"", "\"Sachverhalt L+L\"", "\"Funktionsergänzung L+L\"", "\"BU 49 Hauptfunktionstyp\"", "\"BU 49 Hauptfunktionsnummer\"", "\"BU 49 Funktionsergänzung\"", "\"Zusatzinformation - Art 1\"", "\"Zusatzinformation - Inhalt 1\"", "\"Zusatzinformation - Art 2\"",
        "\"Zusatzinformation - Inhalt 2\"", "\"Zusatzinformation - Art 3\"", "\"Zusatzinformation - Inhalt 3\"", "\"Zusatzinformation - Art 4\"", "\"Zusatzinformation - Inhalt 4\"", "\"Zusatzinformation - Art 5\"", "\"Zusatzinformation - Inhalt 5\"", "\"Zusatzinformation - Art 6\"", "\"Zusatzinformation - Inhalt 6\"", "\"Zusatzinformation - Art 7\"",
        "\"Zusatzinformation - Inhalt 7\"", "\"Zusatzinformation - Art 8\"", "\"Zusatzinformation - Inhalt 8\"", "\"Zusatzinformation - Art 9\"", "\"Zusatzinformation - Inhalt 9\"", "\"Zusatzinformation - Art 10\"", "\"Zusatzinformation - Inhalt 10\"", "\"Zusatzinformation - Art 11\"", "\"Zusatzinformation - Inhalt 11\"", "\"Zusatzinformation - Art 12\"",
        "\"Zusatzinformation - Inhalt 12\"", "\"Zusatzinformation - Art 13\"", "\"Zusatzinformation - Inhalt 13\"", "\"Zusatzinformation - Art 14\"", "\"Zusatzinformation - Inhalt 14\"", "\"Zusatzinformation - Art 15\"", "\"Zusatzinformation - Inhalt 15\"", "\"Zusatzinformation - Art 16\"", "\"Zusatzinformation - Inhalt 16\"", "\"Zusatzinformation - Art 17\"",
        "\"Zusatzinformation - Inhalt 17\"", "\"Zusatzinformation - Art 18\"", "\"Zusatzinformation - Inhalt 18\"", "\"Zusatzinformation - Art 19\"", "\"Zusatzinformation - Inhalt 19\"", "\"Zusatzinformation - Art 20\"", "\"Zusatzinformation - Inhalt 20\"", "Stück", "Gewicht", "Zahlweise",
        "Forderungsart", "Veranlagungsjahr", "\"Zugeordnete Fälligkeit\"", "Skontotyp", "Auftragsnummer", "\"Buchungstyp (Anzahlungen)\"", "\"USt-Schlüssel (Anzahlungen)\"", "\"EU-Land (Anzahlungen)\"", "\"Sachverhalt L+L (Anzahlungen)\"", "\"EU-Steuersatz (Anzahlungen)\"",
        "\"Erlöskonto (Anzahlungen)\"", "\"Herkunft-Kz\"", "\"Buchungs GUID\"", "\"KOST-Datum\"", "\"SEPA-Mandatsreferenz\"", "Skontosperre", "Gesellschaftername", "Beteiligtennummer", "Identifikationsnummer", "Zeichnernummer",
        "\"Postensperre bis\"", "\"Bezeichnung SoBil-Sachverhalt\"", "\"Kennzeichen SoBil-Buchung\"", "Festschreibung", "Leistungsdatum", "\"Datum Zuord. Steuerperiode\""
    )

    val EXTF_HEADER_COLUMNS_116 = EXTF_HEADER_COLUMNS_LIST.joinToString(";")

    fun generateExtfHeader(profile: DatevProfile, year: String): String {
        val timestamp = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.GERMANY).format(Date())
        val startYearDate = "${year}0101"
        val endYearDate = "${year}1231"
        val festschreibungStr = if (profile.festschreibungskennzeichen) "1" else "0"

        return "EXTF;\"700\";\"21\";Buchungsstapel;\"13\";\"$timestamp\";;;;;\"${profile.beraterNummer}\";\"${profile.mandantenNummer}\";\"$startYearDate\";\"${profile.sachkontenLaenge}\";\"$startYearDate\";\"$endYearDate\";;;\"1\";\"$festschreibungStr\";\"0\";${profile.waehrung}"
    }

    fun serializeBookingRecordToRow(record: BookingRecord): String {
        val fields = Array(116) { "" }

        // Format amount: German decimal comma, no thousands separator
        val amountFormatted = String.format(Locale.GERMANY, "%.2f", Math.abs(record.bruttobetrag))
        fields[0] = "\"$amountFormatted\""
        fields[1] = record.sollHaben
        fields[6] = "\"${record.sachkonto}\""
        fields[7] = "\"${record.gegenkonto}\""
        if (record.buSchluessel.isNotBlank()) {
            fields[8] = "\"${record.buSchluessel}\""
        }

        // DDMM date format (e.g. 1803 for 2026-03-18)
        val belegdatumDdMm = try {
            val parts = record.belegdatum.split("-")
            if (parts.size == 3) "${parts[2]}${parts[1]}" else "0101"
        } catch (e: Exception) {
            "0101"
        }
        fields[9] = "\"$belegdatumDdMm\""
        fields[10] = "\"${record.belegfeld1}\""
        if (record.belegfeld2.isNotBlank()) {
            fields[11] = "\"${record.belegfeld2}\""
        }

        val cleanText = record.beschreibung.replace("\"", "'").replace(";", ",").replace("\n", " ").take(60)
        fields[13] = "\"$cleanText\""

        // Beleglink: Leave empty in offline advisor ZIP export per Section 7.3
        fields[19] = ""

        if (record.kost1.isNotBlank()) fields[36] = "\"${record.kost1.take(36)}\""
        if (record.kost2.isNotBlank()) fields[37] = "\"${record.kost2.take(36)}\""

        // Zusatzinformationen (Objekt & Wohneinheit)
        fields[47] = "Objekt"
        fields[48] = "\"${record.kost1.ifBlank { "Immobilie" }}\""
        if (record.wohneinheitId.isNotBlank()) {
            fields[49] = "Wohneinheit"
            fields[50] = "\"${record.wohneinheitId}\""
        }

        // Buchungs GUID
        fields[100] = "\"${record.bookingId}\""

        return fields.joinToString(";")
    }

    fun serializeToCsvString(records: List<BookingRecord>, profile: DatevProfile, year: String): String {
        val header1 = generateExtfHeader(profile, year)
        val header2 = EXTF_HEADER_COLUMNS_116
        val dataRows = records.joinToString("\n") { serializeBookingRecordToRow(it) }
        return "$header1\n$header2\n$dataRows"
    }

    /**
     * Create Control CSV for easy review in Excel/Calc
     */
    fun createControlCsv(records: List<BookingRecord>): String {
        val sb = StringBuilder()
        sb.append("Buchung_ID;Beleg_ID;Belegdatum;Belegnummer;Zahlungspartner;Betrag_EUR;Soll_Haben;Sachkonto;Gegenkonto;KOST1_Objekt;KOST2_Einheit;Kategorie;Pruefstatus;Exportstatus\n")

        records.forEach { r ->
            val amt = String.format(Locale.GERMANY, "%.2f", r.bruttobetrag)
            val party = r.zahlungspartner.replace(";", ",")
            val text = r.beschreibung.replace(";", ",").replace("\n", " ")
            sb.append("${r.bookingId};${r.receiptId};${r.belegdatum};\"${r.belegfeld1}\";\"$party\";$amt;${r.sollHaben};${r.sachkonto};${r.gegenkonto};\"${r.kost1}\";\"${r.kost2}\";\"${r.hauptkategorie}\";${r.pruefstatus};${r.exportStatus}\n")
        }
        return sb.toString()
    }
}
