package com.example.util

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.util.Log
import com.example.data.Receipt
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DatevConfig(
    val beraterNummer: String = "1111111",
    val mandantenNummer: String = "11111",
    val mandantenName: String = "Sergej Gerweck",
    val wirtschaftsjahr: Int = 2025,
    val chartType: String = "SKR03", // "SKR03" or "SKR04"
    val propertyName: String = "MFH Sulzerstraße",
    val propertyShort: String = "MFH Sulz"
)

object DatevExporter {

    private const val TAG = "DatevExporter"

    /**
     * Generates a unique 36-char UUID string based on receipt.id for consistent BEDI link
     */
    fun getReceiptGuid(receipt: Receipt): String {
        return try {
            UUID.nameUUIDFromBytes(receipt.id.toString().toByteArray(Charsets.UTF_8)).toString()
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }

    /**
     * Generates a 24-char hex ID for Belegfeld 1
     */
    fun getBelegfeld1(receipt: Receipt): String {
        val raw = getReceiptGuid(receipt).replace("-", "")
        return if (raw.length >= 24) raw.substring(0, 24) else raw
    }

    /**
     * Generates EXTF Buchungsstapel CSV according to exact DATEV EXTF specification
     */
    fun generateBuchungsstapelCsv(receipts: List<Receipt>, config: DatevConfig): String {
        val timestamp = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.GERMANY).format(Date())
        val startYearDate = "${config.wirtschaftsjahr}0101"
        val endYearDate = "${config.wirtschaftsjahr}1231"

        // Line 1: Header metadata line
        val headerLine1 = "EXTF;\"700\";\"21\";Buchungsstapel;\"13\";\"$timestamp\";;;;;\"${config.beraterNummer}\";\"${config.mandantenNummer}\";\"$startYearDate\";\"4\";\"$startYearDate\";\"$endYearDate\";;;\"1\";\"0\";\"0\";EUR"

        // Line 2: Header column titles (exact 116 columns)
        val headerLine2 = "\"Umsatz (ohne Soll/Haben-Kz)\";\"Soll/Haben-Kennzeichen\";\"WKZ Umsatz\";Kurs;\"Basis-Umsatz\";\"WKZ Basis-Umsatz\";Konto;\"Gegenkonto (ohne BU-Schlüssel)\";\"BU-Schlüssel\";Belegdatum;\"Belegfeld 1\";\"Belegfeld 2\";Skonto;Buchungstext;Postensperre;\"Diverse Adressnummer\";Geschäftspartnerbank;Sachverhalt;Zinssperre;Beleglink;\"Beleginfo - Art 1\";\"Beleginfo - Inhalt 1\";\"Beleginfo - Art 2\";\"Beleginfo - Inhalt 2\";\"Beleginfo - Art 3\";\"Beleginfo - Inhalt 3\";\"Beleginfo - Art 4\";\"Beleginfo - Inhalt 4\";\"Beleginfo - Art 5\";\"Beleginfo - Inhalt 5\";\"Beleginfo - Art 6\";\"Beleginfo - Inhalt 6\";\"Beleginfo - Art 7\";\"Beleginfo - Inhalt 7\";\"Beleginfo - Art 8\";\"Beleginfo - Inhalt 8\";\"KOST1 - Kostenstelle\";\"KOST2 - Kostenstelle\";\"Kost-Menge\";\"EU-Land u. UStID (Bestimmung)\";\"EU-Steuersatz\";\"Abw. Versteuerungsart\";\"Sachverhalt L+L\";\"Funktionsergänzung L+L\";\"BU 49 Hauptfunktionstyp\";\"BU 49 Hauptfunktionsnummer\";\"BU 49 Funktionsergänzung\";\"Zusatzinformation - Art 1\";\"Zusatzinformation - Inhalt 1\";\"Zusatzinformation - Art 2\";\"Zusatzinformation - Inhalt 2\";\"Zusatzinformation - Art 3\";\"Zusatzinformation - Inhalt 3\";\"Zusatzinformation - Art 4\";\"Zusatzinformation - Inhalt 4\";\"Zusatzinformation - Art 5\";\"Zusatzinformation - Inhalt 5\";\"Zusatzinformation - Art 6\";\"Zusatzinformation - Inhalt 6\";\"Zusatzinformation - Art 7\";\"Zusatzinformation - Inhalt 7\";\"Zusatzinformation - Art 8\";\"Zusatzinformation - Inhalt 8\";\"Zusatzinformation - Art 9\";\"Zusatzinformation - Inhalt 9\";\"Zusatzinformation - Art 10\";\"Zusatzinformation - Inhalt 10\";\"Zusatzinformation - Art 11\";\"Zusatzinformation - Inhalt 11\";\"Zusatzinformation - Art 12\";\"Zusatzinformation - Inhalt 12\";\"Zusatzinformation - Art 13\";\"Zusatzinformation - Inhalt 13\";\"Zusatzinformation - Art 14\";\"Zusatzinformation - Inhalt 14\";\"Zusatzinformation - Art 15\";\"Zusatzinformation - Inhalt 15\";\"Zusatzinformation - Art 16\";\"Zusatzinformation - Inhalt 16\";\"Zusatzinformation - Art 17\";\"Zusatzinformation - Inhalt 17\";\"Zusatzinformation - Art 18\";\"Zusatzinformation - Inhalt 18\";\"Zusatzinformation - Art 19\";\"Zusatzinformation - Inhalt 19\";\"Zusatzinformation - Art 20\";\"Zusatzinformation - Inhalt 20\";Stück;Gewicht;Zahlweise;Forderungsart;Veranlagungsjahr;\"Zugeordnete Fälligkeit\";Skontotyp;Auftragsnummer;\"Buchungstyp (Anzahlungen)\";\"USt-Schlüssel (Anzahlungen)\";\"EU-Land (Anzahlungen)\";\"Sachverhalt L+L (Anzahlungen)\";\"EU-Steuersatz (Anzahlungen)\";\"Erlöskonto (Anzahlungen)\";\"Herkunft-Kz\";\"Buchungs GUID\";\"KOST-Datum\";\"SEPA-Mandatsreferenz\";Skontosperre;Gesellschaftername;Beteiligtennummer;Identifikationsnummer;Zeichnernummer;\"Postensperre bis\";\"Bezeichnung SoBil-Sachverhalt\";\"Kennzeichen SoBil-Buchung\";Festschreibung;Leistungsdatum;\"Datum Zuord. Steuerperiode\";Fälligkeit;\"Generalumkehr (GU)\";Steuersatz;Land;Abrechnungsreferenz;\"BVV-Position\";\"EU-Land u. UStID (Ursprung)\";\"EU-Steuersatz (Ursprung)\";\"Abw. Skontokonto\""

        val dataRows = receipts.joinToString("\n") { r ->
            val isIncome = r.hauptkategorie == "Miete, Nebenkosten & Kaution" || r.hauptkategorie == "Sonstige Einnahmen"
            val shSymbol = if (isIncome) "H" else "S"
            val defaultGegenkonto = if (isIncome) "10000" else "70000"

            val konto = when {
                r.kontoNr.isNotEmpty() -> r.kontoNr
                config.chartType == "SKR04" -> {
                    if (isIncome) "4100" else "6470"
                }
                else -> {
                    if (isIncome) "8100" else "4801"
                }
            }

            // DATEV Belegdatum DDMM (e.g. 2810 for 28.10.)
            val belegdatum = try {
                val parts = r.datum.split("-")
                if (parts.size == 3) "${parts[2]}${parts[1]}" else "0101"
            } catch (e: Exception) {
                "0101"
            }

            val amountFormatted = String.format(Locale.GERMANY, "%.2f", r.bruttobetrag)
            val guid = getReceiptGuid(r)
            val belegfeld1 = getBelegfeld1(r)
            val buchungstext = r.beschreibung.replace("\"", "'").take(60)
            val beleglink = "BEDI \"\"$guid\"\""

            val fields = Array(116) { "" }
            fields[0] = "\"$amountFormatted\""
            fields[1] = shSymbol
            fields[6] = "\"$konto\""
            fields[7] = "\"$defaultGegenkonto\""
            fields[9] = "\"$belegdatum\""
            fields[10] = "\"$belegfeld1\""
            fields[13] = "\"$buchungstext\""
            fields[19] = "\"$beleglink\""
            fields[36] = "\"${config.propertyShort}\""
            fields[47] = "Objekt"
            fields[48] = "\"${config.propertyName}\""

            fields.joinToString(";")
        }

        return "$headerLine1\n$headerLine2\n$dataRows"
    }

    /**
     * Generates EXTF Debitoren/Kreditoren CSV
     */
    fun generateKreditorenCsv(config: DatevConfig): String {
        val timestamp = SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.GERMANY).format(Date())
        val startYearDate = "${config.wirtschaftsjahr}0101"
        val endYearDate = "${config.wirtschaftsjahr}1231"

        val line1 = "\"EXTF\";\"700\";\"16\";\"Debitoren/Kreditoren\";\"5\";\"$timestamp\";\"\";\"\";\"\";\"\";\"${config.beraterNummer}\";\"${config.mandantenNummer}\";\"$startYearDate\";\"4\";\"$startYearDate\";\"$endYearDate\";\"\";\"\";\"1\";\"0\";\"0\";\"EUR\""

        val line2 = "\"Konto\";\"Name (Adressattyp Unternehmen)\";\"Unternehmensgegenstand\";\"Name (Adressattyp natürl. Person)\";\"Vorname (Adressattyp natürl. Person)\";\"Name (Adressattyp keine Angabe)\";\"Adressattyp\";\"Kurzbezeichnung\";\"EU-Land\";\"EU-UStID\";\"Anrede\";\"Titel/Akad. Grad\";\"Adelstitel\";\"Namensvorsatz\";\"Adressart\";\"Straße\";\"Postfach\";\"Postleitzahl\";\"Ort\";\"Land\";\"Versandzusatz\";\"Adresszusatz\";\"Abweichende Anrede\";\"Abw. Zustellbezeichnung 1\";\"Abw. Zustellbezeichnung 2\";\"Kennz. Korrespondenzadresse\";\"Adresse Gültig von\";\"Adresse Gültig bis\";\"Telefon\";\"Bemerkung (Telefon)\";\"Telefon GL\";\"Bemerkung (Telefon GL)\";\"E-Mail\";\"Bemerkung (E-Mail)\";\"Internet\";\"Bemerkung (Internet)\";\"Fax\";\"Bemerkung (Fax)\";\"Sonstige\";\"Bemerkung (Sonstige)\";\"Bankleitzahl 1\";\"Bankbezeichnung 1\";\"Bank-Kontonummer 1\";\"Länderkennzeichen 1\";\"IBAN-Nr. 1\";\"Leerfeld\";\"SWIFT-Code 1\";\"Abw. Kontoinhaber 1\";\"Kennz. Hauptbankverb. 1\";\"Bankverb 1 Gültig von\";\"Bankverb 1 Gültig bis\";\"Bankleitzahl 2\";\"Bankbezeichnung 2\";\"Bank-Kontonummer 2\";\"Länderkennzeichen 2\";\"IBAN-Nr. 2\";\"Leerfeld\";\"SWIFT-Code 2\";\"Abw. Kontoinhaber 2\";\"Kennz. Hauptbankverb. 2\";\"Bankverb 2 Gültig von\";\"Bankverb 2 Gültig bis\";\"Bankleitzahl 3\";\"Bankbezeichnung 3\";\"Bank-Kontonummer 3\";\"Länderkennzeichen 3\";\"IBAN-Nr. 3\";\"Leerfeld\";\"SWIFT-Code 3\";\"Abw. Kontoinhaber 3\";\"Kennz. Hauptbankverb. 3\";\"Bankverb 3 Gültig von\";\"Bankverb 3 Gültig bis\";\"Bankleitzahl 4\";\"Bankbezeichnung 4\";\"Bank-Kontonummer 4\";\"Länderkennzeichen 4\";\"IBAN-Nr. 4\";\"Leerfeld\";\"SWIFT-Code 4\";\"Abw. Kontoinhaber 4\";\"Kennz. Hauptbankverb. 4\";\"Bankverb 4 Gültig von\";\"Bankverb 4 Gültig bis\";\"Bankleitzahl 5\";\"Bankbezeichnung 5\";\"Bank-Kontonummer 5\";\"Länderkennzeichen 5\";\"IBAN-Nr. 5\";\"Leerfeld\";\"SWIFT-Code 5\";\"Abw. Kontoinhaber 5\";\"Kennz. Hauptbankverb. 5\";\"Bankverb 5 Gültig von\";\"Bankverb 5 Gültig bis\";\"Leerfeld\";\"Briefanrede\";\"Grußformel\";\"Kunden-/Lief.-Nr.\";\"Steuernummer\";\"Sprache\";\"Ansprechpartner\";\"Vertreter\";\"Sachbearbeiter\";\"Diverse-Konto\";\"Ausgabeziel\";\"Währungssteuerung\";\"Kreditlimit (Debitor)\";\"Zahlungsbedingung\";\"Fälligkeit in Tagen (Debitor)\";\"Skonto in Prozent (Debitor)\";\"Kreditoren-Ziel 1 Tg.\";\"Kreditoren-Skonto 1 %\";\"Kreditoren-Ziel 2 Tg.\";\"Kreditoren-Skonto 2 %\";\"Kreditoren-Ziel 3 Brutto Tg.\";\"Kreditoren-Ziel 4 Tg.\";\"Kreditoren-Skonto 4 %\";\"Kreditoren-Ziel 5 Tg.\";\"Kreditoren-Skonto 5 %\";\"Mahnung\";\"Kontoauszug\";\"Mahntext 1\";\"Mahntext 2\";\"Mahntext 3\";\"Kontoauszugstext\";\"Mahnlimit Betrag\";\"Mahnlimit %\";\"Zinsberechnung\";\"Mahnzinssatz 1\";\"Mahnzinssatz 2\";\"Mahnzinssatz 3\";\"Lastschrift\";\"Leerfeld\";\"Mandantenbank\";\"Zahlungsträger\";\"Indiv. Feld 1\";\"Indiv. Feld 2\";\"Indiv. Feld 3\";\"Indiv. Feld 4\";\"Indiv. Feld 5\";\"Indiv. Feld 6\";\"Indiv. Feld 7\";\"Indiv. Feld 8\";\"Indiv. Feld 9\";\"Indiv. Feld 10\";\"Indiv. Feld 11\";\"Indiv. Feld 12\";\"Indiv. Feld 13\";\"Indiv. Feld 14\";\"Indiv. Feld 15\";\"Abweichende Anrede (Rechnungsadresse)\";\"Adressart (Rechnungsadresse)\";\"Straße (Rechnungsadresse)\";\"Postfach (Rechnungsadresse)\";\"Postleitzahl (Rechnungsadresse)\";\"Ort (Rechnungsadresse)\";\"Land (Rechnungsadresse)\";\"Versandzusatz (Rechnungsadresse)\";\"Adresszusatz (Rechnungsadresse)\";\"Abw. Zustellbezeichnung 1 (Rechnungsadresse)\";\"Abw. Zustellbezeichnung 2 (Rechnungsadresse)\";\"Adresse Gültig von (Rechnungsadresse)\";\"Adresse Gültig bis (Rechnungsadresse)\";\"Bankleitzahl 6\";\"Bankbezeichnung 6\";\"Bank-Kontonummer 6\";\"Länderkennzeichen 6\";\"IBAN-Nr. 6\";\"Leerfeld\";\"SWIFT-Code 6\";\"Abw. Kontoinhaber 6\";\"Kennz. Hauptbankverb. 6\";\"Bankverb 6 Gültig von\";\"Bankverb 6 Gültig bis\";\"Bankleitzahl 7\";\"Bankbezeichnung 7\";\"Bank-Kontonummer 7\";\"Länderkennzeichen 7\";\"IBAN-Nr. 7\";\"Leerfeld\";\"SWIFT-Code 7\";\"Abw. Kontoinhaber 7\";\"Kennz. Hauptbankverb. 7\";\"Bankverb 7 Gültig von\";\"Bankverb 7 Gültig bis\";\"Bankleitzahl 8\";\"Bankbezeichnung 8\";\"Bank-Kontonummer 8\";\"Länderkennzeichen 8\";\"IBAN-Nr. 8\";\"Leerfeld\";\"SWIFT-Code 8\";\"Abw. Kontoinhaber 8\";\"Kennz. Hauptbankverb. 8\";\"Bankverb 8 Gültig von\";\"Bankverb 8 Gültig bis\";\"Bankleitzahl 9\";\"Bankbezeichnung 9\";\"Bank-Kontonummer 9\";\"Länderkennzeichen 9\";\"IBAN-Nr. 9\";\"Leerfeld\";\"SWIFT-Code 9\";\"Abw. Kontoinhaber 9\";\"Kennz. Hauptbankverb. 9\";\"Bankverb 9 Gültig von\";\"Bankverb 9 Gültig bis\";\"Bankleitzahl 10\";\"Bankbezeichnung 10\";\"Bank-Kontonummer 10\";\"Länderkennzeichen 10\";\"IBAN-Nr. 10\";\"Leerfeld\";\"SWIFT-Code 10\";\"Abw. Kontoinhaber 10\";\"Kennz. Hauptbankverb. 10\";\"Bankverb 10 Gültig von\";\"Bankverb 10 Gültig bis\";\"Nummer Fremdsystem\";\"Insolvent\";\"SEPA-Mandatsreferenz 1\";\"SEPA-Mandatsreferenz 2\";\"SEPA-Mandatsreferenz 3\";\"SEPA-Mandatsreferenz 4\";\"SEPA-Mandatsreferenz 5\";\"SEPA-Mandatsreferenz 6\";\"SEPA-Mandatsreferenz 7\";\"SEPA-Mandatsreferenz 8\";\"SEPA-Mandatsreferenz 9\";\"SEPA-Mandatsreferenz 10\";\"Verknüpftes OPOS-Konto\";\"Mahnsperre bis\";\"Lastschriftsperre bis\";\"Zahlungssperre bis\";\"Gebührenberechnung\";\"Mahngebühr 1\";\"Mahngebühr 2\";\"Mahngebühr 3\";\"Pauschalenberechnung\";\"Verzugspauschale 1\";\"Verzugspauschale 2\";\"Verzugspauschale 3\";\"Alternativer Suchname\";\"Status\";\"Anschrift manuell geändert (Korrespondenzadresse)\";\"Anschrift individuell (Korrespondenzadresse)\";\"Anschrift manuell geändert (Rechnungsadresse)\";\"Anschrift individuell (Rechnungsadresse)\";\"Fristberechnung bei Debitor\";\"Mahnfrist 1\";\"Mahnfrist 2\";\"Mahnfrist 3\";\"Letzte Frist\""

        val row1 = Array(235) { "" }
        row1[0] = "\"70000\""
        row1[3] = "\"Sammelkunde/Sammellieferant\""
        row1[6] = "1"
        row1[14] = "\"STR\""

        val row2 = Array(235) { "" }
        row2[0] = "\"10000\""
        row2[3] = "\"Sammelkunde/Sammellieferant\""
        row2[6] = "1"
        row2[14] = "\"STR\""

        return "$line1\n$line2\n${row1.joinToString(";")}\n${row2.joinToString(";")}"
    }

    /**
     * Generates DATEV Unternehmen Online BEDI XML (document.xml)
     */
    fun generateDocumentXml(receipts: List<Receipt>, config: DatevConfig): String {
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMANY).format(Date())
        val sb = StringBuilder()

        sb.append("<?xml version='1.0' encoding='UTF-8'?>\n")
        sb.append("<archive version=\"6.0\" generatingSystem=\"Steuerassistent\" xsi:schemaLocation=\"http://xml.datev.de/bedi/tps/document/v06.0 Document_v060.xsd\" xmlns=\"http://xml.datev.de/bedi/tps/document/v06.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n")
        sb.append("  <header>\n")
        sb.append("    <date>$nowIso</date>\n")
        sb.append("    <description>Belegexport 01.01.${config.wirtschaftsjahr} - 31.12.${config.wirtschaftsjahr}</description>\n")
        sb.append("    <consultantNumber>${config.beraterNummer}</consultantNumber>\n")
        sb.append("    <clientNumber>${config.mandantenNummer}</clientNumber>\n")
        sb.append("    <clientName>${escapeXml(config.mandantenName)}</clientName>\n")
        sb.append("  </header>\n")
        sb.append("  <content>\n")

        receipts.forEach { r ->
            val guid = getReceiptGuid(r)
            val belegfeld1 = getBelegfeld1(r)
            val dateFormatted = try {
                val parts = r.datum.split("-")
                if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else r.datum
            } catch (e: Exception) {
                r.datum
            }
            val monthFormatted = try {
                val parts = r.datum.split("-")
                if (parts.size == 3) "${parts[0]}-${parts[1]}" else "${config.wirtschaftsjahr}-01"
            } catch (e: Exception) {
                "${config.wirtschaftsjahr}-01"
            }

            val amountFormatted = String.format(Locale.GERMANY, "%.2f", r.bruttobetrag)
            val desc = "$dateFormatted $amountFormatted EUR Sammelkunde/Sammel"
            val buchungstext = escapeXml(r.beschreibung)
            val keywords = "Objekt: ${config.propertyName}; Kontakt: Sammelkunde/Sammellieferant; Datum: $dateFormatted; Betrag: $amountFormatted EUR; Belegnummer: $belegfeld1; Buchungstext: $buchungstext"

            sb.append("    <document guid=\"$guid\" type=\"1\">\n")
            sb.append("      <description>${escapeXml(desc)}</description>\n")
            sb.append("      <keywords>${escapeXml(keywords)}</keywords>\n")
            sb.append("      <extension name=\"$guid.pdf\" xsi:type=\"File\"/>\n")
            sb.append("      <repository>\n")
            sb.append("        <level id=\"1\" name=\"Buchführung\"/>\n")
            sb.append("        <level id=\"2\" name=\"${escapeXml(config.propertyName)}\"/>\n")
            sb.append("        <level id=\"3\" name=\"$monthFormatted\"/>\n")
            sb.append("      </repository>\n")
            sb.append("    </document>\n")
        }

        sb.append("  </content>\n")
        sb.append("</archive>")

        return sb.toString()
    }

    /**
     * Creates a complete DATEV export ZIP package containing:
     * - EXTF_Buchungsstapel.csv
     * - EXTF_Debitoren_Kreditoren.csv
     * - document.xml (DATEV BEDI XML)
     * - Attached receipt PDF files named <guid>.pdf
     */
    fun createDatevZipPackage(
        context: Context,
        receipts: List<Receipt>,
        config: DatevConfig
    ): File {
        val zipFile = File(context.cacheDir, "DATEV_Export_${config.wirtschaftsjahr}_${System.currentTimeMillis()}.zip")

        val buchungsstapelCsv = generateBuchungsstapelCsv(receipts, config)
        val kreditorenCsv = generateKreditorenCsv(config)
        val documentXml = generateDocumentXml(receipts, config)

        // UTF-8 BOM for DATEV Windows compatibility
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // 1. EXTF_Buchungsstapel.csv
            zos.putNextEntry(ZipEntry("EXTF_Buchungsstapel.csv"))
            zos.write(bom)
            zos.write(buchungsstapelCsv.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. EXTF_Debitoren_Kreditoren.csv
            zos.putNextEntry(ZipEntry("EXTF_Debitoren_Kreditoren.csv"))
            zos.write(bom)
            zos.write(kreditorenCsv.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. document.xml
            zos.putNextEntry(ZipEntry("document.xml"))
            zos.write(documentXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 4. Receipt attachments converted to <guid>.pdf
            receipts.forEach { receipt ->
                val guid = getReceiptGuid(receipt)
                val pdfBytes = createPdfForReceipt(context, receipt)
                if (pdfBytes != null && pdfBytes.isNotEmpty()) {
                    zos.putNextEntry(ZipEntry("$guid.pdf"))
                    zos.write(pdfBytes)
                    zos.closeEntry()
                }
            }
        }

        return zipFile
    }

    private fun createPdfForReceipt(context: Context, receipt: Receipt): ByteArray? {
        return try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = android.graphics.Paint().apply {
                color = 0xFF0F172A.toInt()
                textSize = 16f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                isAntiAlias = true
            }
            val bodyPaint = android.graphics.Paint().apply {
                color = 0xFF334155.toInt()
                textSize = 11f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
                isAntiAlias = true
            }

            canvas.drawText("BELEG-DOKUMENT", 40f, 50f, titlePaint)
            canvas.drawText("Aussteller: ${receipt.aussteller}", 40f, 80f, bodyPaint)
            canvas.drawText("Datum: ${receipt.datum}", 40f, 100f, bodyPaint)
            canvas.drawText("Betrag: ${String.format(Locale.GERMANY, "%.2f", receipt.bruttobetrag)} EUR", 40f, 120f, bodyPaint)
            canvas.drawText("Kategorie: ${receipt.hauptkategorie} / ${receipt.unterkategorie}", 40f, 140f, bodyPaint)
            canvas.drawText("Konto-Nr: ${receipt.kontoNr}", 40f, 160f, bodyPaint)
            canvas.drawText("Beschreibung: ${receipt.beschreibung}", 40f, 180f, bodyPaint)

            // If an image file path exists, draw the receipt image on the PDF page
            if (receipt.imageUrl.isNotEmpty()) {
                val imgFile = File(receipt.imageUrl)
                if (imgFile.exists()) {
                    val bmp = BitmapFactory.decodeFile(imgFile.absolutePath)
                    if (bmp != null) {
                        val maxW = 515f
                        val maxH = 580f
                        val scale = Math.min(maxW / bmp.width, maxH / bmp.height)
                        val drawW = (bmp.width * scale).toInt()
                        val drawH = (bmp.height * scale).toInt()
                        val destRect = android.graphics.Rect(40, 210, 40 + drawW, 210 + drawH)
                        canvas.drawBitmap(bmp, null, destRect, null)
                        bmp.recycle()
                    }
                }
            }

            document.finishPage(page)
            val outputStream = java.io.ByteArrayOutputStream()
            document.writeTo(outputStream)
            document.close()
            outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating PDF for receipt ${receipt.id}", e)
            null
        }
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
