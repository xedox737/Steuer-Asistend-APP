package com.example.data

/**
 * Granularer Buchungssatz für den DATEV EXTF-Buchungsstapel und Audit-Log.
 */
data class BookingRecord(
    val bookingId: String,               // Unveränderliche GUID/ID
    val receiptId: Int,                  // Verknüpfte Receipt ID
    val originalFileId: String = "",      // Dateipfad oder SHA-256
    val belegnummer: String,             // Eindeutige DATEV-Belegnummer (Belegfeld 1)
    val belegdatum: String,              // YYYY-MM-DD
    val leistungsdatum: String = "",     // YYYY-MM-DD
    val zahlungsdatum: String = "",      // YYYY-MM-DD
    val buchungsdatum: String,           // YYYY-MM-DD
    val zahlungspartner: String,         // Rechnungssteller / Empfänger
    val beschreibung: String,            // Buchungstext max 60 Zeichen
    val bruttobetrag: Double,            // Positiver Betrag
    val nettobetrag: Double = 0.0,
    val ustBetrag: Double = 0.0,
    val ustSatz: Double = 0.0,
    val sollHaben: String,               // "S" oder "H"
    val waehrung: String = "EUR",
    val sachkonto: String,              // Konto
    val gegenkonto: String,             // Gegenkonto
    val buSchluessel: String = "",      // BU-Schlüssel
    val belegfeld1: String,             // Eindeutige Belegnummer
    val belegfeld2: String = "",        // Optionale Zusatznummer
    val kost1: String = "",             // KOST1 (z.B. Objekt)
    val kost2: String = "",             // KOST2 (z.B. Wohneinheit)
    val objektId: String = "",
    val wohneinheitId: String = "",
    val hauptkategorie: String,
    val unterkategorie: String = "",
    val zahlungsart: String = "",
    val bankTransaktionId: String = "",
    val anteilProzent: Double = 100.0,   // Bei Split-Buchungen
    val steuerlichesJahr: Int,
    val exportStatus: String = "EXPORTBEREIT", // ENTWURF, KI_VORSCHLAG, ZU_PRUEFEN, GEPRUEFT, EXPORTBEREIT, EXPORTIERT, AUSGESCHLOSSEN
    val pruefstatus: String = "GEPRUEFT",      // UNGEPRUEFT, GEPRUEFT, KORRIGIERT
    val pruefer: String = "Nutzer",
    val pruefzeitpunkt: String = "",
    val kiKonfidenz: Double = 1.0,
    val warnings: List<String> = emptyList(),
    val exportlaufId: String = "",
    val kanzleiprofilVersion: Int = 1
)
