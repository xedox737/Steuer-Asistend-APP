# Bankimport: CAMT.052/053 V8 und ZIP

## Format-Erkennung

XML wird anhand des Root-Namespace erkannt. Unterstützt werden ausschließlich `camt.052.001.08` und `camt.053.001.08`. Eine `.xml`-Dateiendung allein gilt nicht als Formatnachweis. Der XML-Parser ist namespace-aware und deaktiviert DOCTYPE, externe Entities und externes DTD-/Schema-Laden. Die JAXP-Attribute für `accessExternalDTD` und `accessExternalSchema` werden als standardisierte Property-URIs gesetzt, damit die Absicherung auch mit dem Android/JVM-Compile-Target verfügbar bleibt.

## Multi-TxDtls-Entscheidung

Eine `Ntry` mit höchstens einem `TxDtls` behält die bestehende Ntry-Semantik. Enthält eine `Ntry` mehrere `TxDtls`, wird nur dann in Einzeltransaktionen aufgeteilt, wenn jeder Detaildatensatz einen gültigen Betrag und eine eindeutige DBIT-/CRDT-Richtung besitzt und die Summe der signierten Detailbeträge den signierten Ntry-Gesamtbetrag innerhalb der zentralen Geldtoleranz von 0,01 EUR exakt ergibt. Andernfalls bleibt die Ntry eine einzige Transaktion; dadurch entsteht keine Doppelzählung. Alle `Ustrd`-Texte der Ntry werden für den Verwendungszweck zusammengeführt.

## ZIP-Verarbeitung

ZIPs werden lokal per `ZipInputStream` gestreamt und nicht auf das Dateisystem entpackt. Verzeichnisse werden ignoriert, verschachtelte ZIPs nicht importiert und unsichere Pfade (`..`, absolute Pfade, Laufwerkspfade) blockiert. Produktionsgrenzen: maximal 20 MiB ZIP, 50 Einträge, 50 MiB entpackte Gesamtdaten, 10 MiB pro XML und maximal 200:1 Kompressionsverhältnis.

Jeder XML-Eintrag wird separat per Namespace geprüft. Unterstützte CAMT-Dateien werden über die bestehenden `BankAccount`-/`BankTransaction`-Modelle und die vorhandene DAO-Deduplizierung importiert. Der Importbericht bleibt UI-/Domainzustand; dafür gibt es keine neue Tabelle.

## Idempotenz

Die bestehende `BankTransactionIdentity` bleibt maßgeblich. Für CAMT-Konten wird aus Kompatibilitätsgründen weiterhin der historische CAMT053-Identitätssalt verwendet, sodass bestehende CAMT.053-Reimporte dieselben IDs behalten und CAMT.052/053 derselben eigenen IBAN kein paralleles Konto erzeugen. `INSERT IGNORE` schützt bestehende Transaktionen, Links und Reconciliation-Status beim Reimport.

## Datenschutz

Fixtures enthalten ausschließlich synthetische Daten. XML-/ZIP-Inhalte, vollständige IBANs, Kontoinhaber und Verwendungszwecke werden nicht in Debug-/Crash-Logs geschrieben.
