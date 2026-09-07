# Bank & Belegabgleich – Phase 1

## Ziel

Der Bankbereich ergänzt den bestehenden Steuer-Assistenten um persistente Kontoumsätze und einen einfachen bidirektionalen Belegabgleich. Es entsteht keine zweite Beleg-, Miet-, Darlehens- oder DATEV-Logik.

Der Nutzer kann von beiden Seiten arbeiten:

- Bankbuchung öffnen → vorhandenen Beleg zuordnen oder einen neuen Beleg mit vorausgefüllten Bankdaten anlegen.
- Bestehenden Beleg öffnen/anzeigen → passende noch offene Bankbuchung vorgeschlagen bekommen und bestätigen.

Eine dauerhafte Verknüpfung entsteht ausschließlich nach Nutzerbestätigung.

## Datenmodell

Room wird additiv von Version 21 auf 22 erweitert.

### `bank_accounts`

Speichert die importierte Kontoidentität und die Quelle. Phase 1 unterstützt CSV und CAMT.053. Eine spätere FinTS-/Banking4-Anbindung kann dieselbe Tabelle verwenden.

### `bank_transactions`

Speichert unveränderte fachliche Kerndaten des Kontoumsatzes:

- stabile `transactionId`
- Konto
- Buchungs-/Wertstellungsdatum
- Betrag mit Vorzeichen
- Währung
- Zahlungspartner und Gegenkonto-IBAN
- Verwendungszweck
- Bankreferenz
- Importquelle
- Abgleichstatus

Die `transactionId` wird deterministisch aus normalisierten Transaktionsmerkmalen erzeugt. Ein wiederholter Import derselben Datei erzeugt dadurch keine zweite Buchung.

### `bank_receipt_links`

Eigene Relation zwischen Bankbuchung und vorhandenem `Receipt`. Es wird bewusst kein 1:1-Feld in `Receipt` eingebaut. Dadurch sind von Anfang an möglich:

- 1 Buchung ↔ 1 Beleg
- 1 Buchung ↔ mehrere Belege (Sammelzahlung)
- mehrere Buchungen ↔ 1 Beleg (Teilzahlungen)

`allocatedAmount` verhindert, dass eine Buchung oder ein Beleg unbemerkt über den Gesamtbetrag hinaus zugeordnet wird.

## Import

### CSV

Unterstützt übliche deutschsprachige Spaltenbezeichnungen, unter anderem:

- Buchungstag / Buchungsdatum / Datum
- Wertstellung
- Auftraggeber/Empfänger
- Verwendungszweck / Buchungstext
- Betrag / Umsatz
- Währung
- IBAN
- Referenz

Semikolon, Tab und Komma werden als mögliche Trennzeichen erkannt. Deutsche Betragsdarstellung wie `1.234,56` wird normalisiert.

### CAMT.053

ISO-20022-Kontoauszüge werden lokal geparst. DTD und externe XML-Entities sind deaktiviert. `DBIT` wird als negativer, `CRDT` als positiver Betrag gespeichert.

## Matching

Die persistente Entscheidung basiert nicht auf KI.

Der lokale Matcher bewertet insbesondere:

1. exakten bzw. ähnlichen Betrag,
2. Abstand zwischen Beleg- und Buchungsdatum,
3. Ähnlichkeit Zahlungspartner ↔ Aussteller,
4. Überschneidungen zwischen Verwendungszweck und Belegbeschreibung,
5. vorhandene Belegnummer im Verwendungszweck.

Konfidenzklassen:

- `HOCH`: ab 85 Punkten
- `MITTEL`: ab 65 Punkten
- `NIEDRIG`: darunter

Auch ein hoher Treffer bleibt nur ein Vorschlag. Erst `Zuordnen` schreibt `BankReceiptLink`.

Der schon vorhandene Gemini-Kontoauszugsabgleich (`BankStatementReconciliationResult`) bleibt unverändert als optionale Assistenz bestehen. Er ist nicht die Datenquelle für dauerhafte Verknüpfungen.

## UX

Neuer Bereich `Bank & Belege`:

- Kontoauszug importieren
- `Zu prüfen`, `Erledigt`, `Alle`
- bester Belegvorschlag direkt an der Buchung
- `Vorhandenen Beleg suchen`
- `Beleg aus Buchung anlegen`
- `Kein Beleg erforderlich` mit Grund
- Abschnitt `Belege ohne Bankzuordnung` für den umgekehrten Arbeitsweg

Beim Anlegen eines Belegs aus einer Buchung wird das bestehende Belegformular wiederverwendet. Vorausgefüllt werden Zahlungspartner, Buchungsdatum, Betrag, Verwendungszweck und Zahlungsart `Überweisung`. Kategorie, Immobilie/Einheit und steuerliche Einordnung bleiben bewusst prüfpflichtig.

## Sicherheit und Bestandsschutz

Nicht verändert werden:

- bestehende `Receipt`-Fachlogik
- OCR / Scanner / KI-Beleganalyse
- Managed Documents / Drive-Dateistruktur
- DATEV-Freigabelogik und Exporthistorie
- Advisor-/Steuerberaterpaket
- AnnualTaxAssistant, AfA, Kaufpreisaufteilung und 15-%-Prüfung
- bestehende Miet-Soll-/Ist-Logik
- Loan-/Zinslogik
- Fahrtenbuch / Google Routes

Der vorhandene `BookingRecord.bankTransaktionId` wird in Phase 1 nicht automatisch befüllt. DATEV wird nicht von einem unbestätigten Bank-Match beeinflusst.

## Backup

Supplemental Drive Backup wird rückwärtskompatibel um Bankkonten, Bankbuchungen und bestätigte Verknüpfungen ergänzt. Ältere Backups ohne Bankfelder bleiben lesbar.

## Phase 1 Grenzen / nächste Schritte

Noch nicht Bestandteil dieser Phase:

- automatische FinTS-/Banking4-Synchronisierung
- automatische Mietverbuchung aus Bankdaten
- automatische Darlehensaufteilung
- lernende Bankregeln
- vollautomatische Sammelzahlungsauflösung
- automatische DATEV-Verknüpfung

Diese Funktionen können später auf den gleichen `BankTransaction`- und `BankReceiptLink`-Strukturen aufbauen, ohne den Phase-1-Import oder die Belegverknüpfungen neu zu entwickeln.
