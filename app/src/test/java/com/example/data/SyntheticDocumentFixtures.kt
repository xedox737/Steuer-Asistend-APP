package com.example.data

import java.io.File
import java.io.ByteArrayOutputStream
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

internal data class SyntheticDocumentFixture(
    val number: Int,
    val filename: String,
    val bytes: ByteArray,
    val expectedType: ManagedDocumentType = ManagedDocumentType.SONSTIGES,
    val expectedTargetSuffix: String = "",
    val receiptInternalId: String = "",
    val documentId: String = "",
    val driveFileId: String = "fixture-drive-${number.toString().padStart(2, '0')}",
    val legacyPath: String = "",
    val appRelevant: Boolean = true,
    val reachable: Boolean = true,
    val declaredSha256: String? = null
) {
    val sha256: String get() = StableDocumentIdentity.sha256(bytes)
}

/** Deterministic, entirely fictitious files used only below the unit-test temp root. */
internal object SyntheticDocumentFixtureFactory {
    fun create(): List<SyntheticDocumentFixture> {
        val handwerk = pdf(
            "Handwerkerrechnung TestHandwerk GmbH Rechnungsnummer TH-2026-001 " +
                "Rechnungsdatum 14.02.2026 Reparatur Heizungsventil Objekt Musterstrasse 12 WE_02 " +
                "Netto 1050,34 EUR MwSt 199,56 EUR Brutto 1249,90 EUR"
        )
        val fixtures = mutableListOf(
            fixture(1, "01_Handwerkerrechnung.pdf", handwerk, ManagedDocumentType.RECHNUNG, "02_Belege/2026", receiptId = "receipt-01"),
            fixture(2, "02_Baumarkt_Rechnung.pdf", pdf("TestBaumarkt GmbH Rechnungsnummer TB-8450 Material fuer Reparatur Brutto 84,50 EUR Datum 15.02.2026"), ManagedDocumentType.RECHNUNG, "02_Belege/2026", receiptId = "receipt-02"),
            fixture(3, "03_Versicherungsrechnung.pdf", pdf("Gebaeudeversicherung Jahresbeitrag 2026 Betrag 417,00 EUR TestVersicherung AG"), ManagedDocumentType.RECHNUNG, "02_Belege/2026", receiptId = "receipt-03"),
            fixture(4, "04_Grundsteuerbescheid.pdf", pdf("Gemeinde Musterstadt Grundsteuerbescheid Jahr 2026 Objekt Musterstrasse 12"), ManagedDocumentType.GRUNDSTEUERDOKUMENT, "00_Stammdaten/05_Steuer_Grundlagen"),
            fixture(5, "05_Rechnung_Bildscan.jpg", jpeg("Lesbarer Bildscan TestWerkstatt Rechnung 85,20 EUR"), ManagedDocumentType.RECHNUNG, "02_Belege/2026"),
            fixture(6, "06_Schlechter_Bildscan.jpg", jpeg("reduzierte qualitaet", noisy = true)),
            fixture(7, "07_PDF_mit_Text.pdf", pdf("Eingebetteter PDF Text Rechnungsnummer EMB-2026-4711 Heizungsventil"), ManagedDocumentType.RECHNUNG, "02_Belege/2026"),
            fixture(8, "08_Bild_PDF_ohne_Text.pdf", imageOnlyPdf()),
            fixture(9, "09_Kaufvertrag.pdf", pdf("Kaufvertrag Testkaeufer Person Kaufpreis 520000 EUR Kaufdatum 01.10.2025 Objekt Musterstrasse 12"), ManagedDocumentType.KAUFVERTRAG, "00_Stammdaten/01_Kauf_Eigentum", documentId = "document-09"),
            fixture(10, "10_Darlehensvertrag.pdf", pdf("Darlehensvertrag Testbank AG Darlehensbetrag 450000 EUR Sollzins 3,99 Prozent Tilgung 1,50 Prozent"), ManagedDocumentType.DARLEHENSVERTRAG, "03_Finanzierung_AfA/Darlehen", documentId = "document-10"),
            fixture(11, "11_Zinsbescheinigung.pdf", pdf("Zinsbescheinigung Testbank AG Jahr 2026 Schuldzinsen 12345,67 EUR"), ManagedDocumentType.ZINSBESCHEINIGUNG, "03_Finanzierung_AfA/Zinsunterlagen"),
            fixture(12, "12_Energieausweis.pdf", pdf("Energieausweis Objekt Musterstrasse 12 Baujahr 1998 Ausstellungsdatum 01.08.2025"), ManagedDocumentType.ENERGIEAUSWEIS, "00_Stammdaten/03_Energie_Technik"),
            fixture(13, "13_Mietvertrag_WE_01.pdf", pdf("Mietvertrag WE_01 Testmieter Mietbeginn 01.01.2026 Kaltmiete 850 EUR Nebenkosten 220 EUR Kaution 2550 EUR"), ManagedDocumentType.MIETVERTRAG, "01_Einheiten/WE_01/Mietvertrag", documentId = "document-13"),
            fixture(14, "14_Uebergabeprotokoll_WE_01.pdf", pdf("Uebergabeprotokoll WE_01 Zaehlerstand Strom 12345 Wasser 678 Schluessel 3"), ManagedDocumentType.UEBERGABEPROTOKOLL, "01_Einheiten/WE_01/Uebergabe"),
            fixture(15, "15_Gebaeudeversicherung_Police.pdf", pdf("Gebaeudeversicherung Police TestVersicherung AG Versicherungsnummer TEST-4711"), ManagedDocumentType.VERSICHERUNGSPOLICE, "00_Stammdaten/04_Versicherungen"),
            fixture(16, "16_Grundriss.pdf", pdf("Grundriss Musterstrasse 12 Erdgeschoss Wohnflaeche 82 qm"), ManagedDocumentType.GRUNDRISS, "00_Stammdaten/02_Grundstueck_Gebaeude"),
            fixture(17, "17_Kaufpreisaufteilung.pdf", pdf("Kaufpreisaufteilung Objekt Musterstrasse 12 Gebaeude 390000 EUR Grund und Boden 130000 EUR"), ManagedDocumentType.KAUFPREISAUFTEILUNG, "03_Finanzierung_AfA/Kaufpreisaufteilung"),
            fixture(18, "18_Sanierung_Bad_WE_02.pdf", pdf("Sanierungsdokument Projekt Bad WE_02 Zeitraum 2026"), ManagedDocumentType.SANIERUNGSUNTERLAGE, "04_Sanierungen")
        )
        fixtures += fixture(19, "19_Handwerkerrechnung_Kopie.pdf", handwerk, ManagedDocumentType.RECHNUNG, "02_Belege/2026")
        fixtures += fixture(20, "01_Handwerkerrechnung.pdf", pdf("Gleicher Dateiname aber anderer Inhalt und anderer Betrag 99,00 EUR"), ManagedDocumentType.RECHNUNG)
        fixtures += fixture(21, "21_Handwerkerrechnung_neue_Metadaten.pdf", pdf("Metadata geaendert\n" + handwerk.toString(Charsets.ISO_8859_1)), ManagedDocumentType.RECHNUNG)
        fixtures += fixture(22, "22_Legacy_ohne_AppProperties.pdf", pdf("Legacy Dokument ohne AppProperties"), legacyPath = "2026/Sonstiges/Allgemein")
        fixtures += fixture(23, "23_Receipt_ohne_lokalen_Datensatz.pdf", pdf("Drive Receipt ohne lokale Referenz"), receiptId = "missing-receipt")
        fixtures += fixture(24, "24_Document_ohne_lokalen_Datensatz.pdf", pdf("Drive Document ohne lokale Referenz"), documentId = "missing-document")
        fixtures += fixture(25, "25_Eine_DriveId_zwei_Identitaeten.pdf", pdf("Konflikt zweier lokaler Identitaeten"), receiptId = "receipt-a", documentId = "document-b", driveId = "shared-drive-25")
        fixtures += fixture(26, "26_Gleiche_ReceiptId_A.pdf", pdf("Receipt Identitaet Datei A"), receiptId = "shared-receipt")
        fixtures += fixture(27, "27_Gleiche_DocumentId_A.pdf", pdf("Document Identitaet Datei A"), documentId = "shared-document")
        fixtures += fixture(28, "28_Nicht_erreichbar.pdf", pdf("Nicht erreichbare Drive Datei"), reachable = false)
        fixtures += fixture(29, "29_Falscher_Hash.pdf", pdf("Datei mit absichtlich falscher Checksumme"), declaredSha256 = "0".repeat(64))
        fixtures += fixture(30, "30_Fremde_Datei.pdf", pdf("Fremde Datei muss ignoriert werden"), appRelevant = false)
        fixtures += fixture(31, "Beleg_alt_1.pdf", pdf("Legacy Beleg mit vollstaendiger lokaler Referenz"), receiptId = "legacy-receipt-31", legacyPath = "2025/Handwerker/Reparaturen")
        fixtures += fixture(32, "Beleg_alt_2.pdf", pdf("Legacy Beleg nur im receipt-index"), receiptId = "legacy-index-32", legacyPath = "2026/Sonstiges/Allgemein")
        fixtures += fixture(33, "Legacy_AppProperties.pdf", pdf("Legacy AppProperties ohne lokalen Datensatz"), documentId = "legacy-document-33", legacyPath = "2026/Sonstiges/Allgemein")
        fixtures += fixture(34, "Legacy_unzugeordnet.pdf", pdf("Vollstaendig unzugeordnete Legacy Datei"), legacyPath = "2026/Sonstiges/Allgemein")
        fixtures += fixture(35, "Legacy_Dublette.pdf", fixtures.first().bytes, legacyPath = "2025/Handwerker/Reparaturen")
        return fixtures
    }

    fun materialize(root: File, fixtures: List<SyntheticDocumentFixture> = create()): Map<Int, File> = fixtures.associate { fixture ->
        val relative = fixture.legacyPath.takeIf(String::isNotBlank)?.let { "$it/${fixture.filename}" } ?: fixture.filename
        val file = File(root, "${fixture.number.toString().padStart(2, '0')}_$relative")
        file.parentFile?.mkdirs()
        file.writeBytes(fixture.bytes)
        fixture.number to file
    }

    private fun fixture(
        number: Int,
        filename: String,
        bytes: ByteArray,
        type: ManagedDocumentType = ManagedDocumentType.SONSTIGES,
        target: String = "",
        receiptId: String = "",
        documentId: String = "",
        driveId: String = "fixture-drive-${number.toString().padStart(2, '0')}",
        legacyPath: String = "",
        appRelevant: Boolean = true,
        reachable: Boolean = true,
        declaredSha256: String? = null
    ) = SyntheticDocumentFixture(number, filename, bytes, type, target, receiptId, documentId, driveId, legacyPath, appRelevant, reachable, declaredSha256)

    private fun pdf(text: String): ByteArray = ("%PDF-1.4\n" +
        "1 0 obj << /Type /Catalog >> endobj\n" +
        "2 0 obj << /Length ${text.length + 18} >>\nstream\nBT ($text) Tj ET\nendstream\nendobj\n%%EOF")
        .toByteArray(Charsets.ISO_8859_1)

    private fun imageOnlyPdf(): ByteArray = ("%PDF-1.4\n" +
        "1 0 obj << /Type /Catalog >> endobj\n" +
        "2 0 obj << /Length 20 >>\nstream\nq 10 0 0 10 0 0 cm Q\nendstream\nendobj\n%%EOF")
        .toByteArray(Charsets.ISO_8859_1)

    private fun jpeg(label: String, noisy: Boolean = false): ByteArray {
        val image = BufferedImage(if (noisy) 520 else 900, if (noisy) 260 else 420, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = if (noisy) Color(205, 205, 205) else Color.WHITE
            graphics.fillRect(0, 0, image.width, image.height)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            graphics.rotate(if (noisy) -0.035 else -0.018, image.width / 2.0, image.height / 2.0)
            graphics.color = if (noisy) Color(150, 150, 150) else Color.BLACK
            graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, if (noisy) 17 else 30)
            label.split(' ').chunked(if (noisy) 4 else 5).forEachIndexed { index, words ->
                graphics.drawString(words.joinToString(" "), 35, 75 + index * if (noisy) 33 else 50)
            }
            if (noisy) {
                graphics.color = Color(180, 180, 180)
                for (x in 0 until image.width step 13) graphics.drawLine(x, 0, (x + 37).coerceAtMost(image.width), image.height)
            }
        } finally {
            graphics.dispose()
        }
        return ByteArrayOutputStream().use { output ->
            check(ImageIO.write(image, "jpg", output))
            output.toByteArray()
        }
    }
}
