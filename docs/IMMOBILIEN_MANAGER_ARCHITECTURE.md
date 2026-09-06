# Immobilien-Manager – Integrationsarchitektur

## Navigation

Die primäre Navigation bleibt `Start`, `Belege`, `Scannen`, `Immobilien`, `Mehr`. `Immobilien` öffnet die Portfolioübersicht. `Mehr` bündelt unverändert Fahrtenbuch, Finanzen, Dokumentenakte und Einstellungen. Phase 2 ändert keine globale Navigation oder Fachlogik außerhalb des Immobilien-Managers.

## Portfolio und Objekt-Dashboard

`ImmobilienManagerScreen` liest weiterhin alle Datensätze aus `property_metadata`. Der historische Datensatz `id = 1` / `property-1` bleibt erhalten. Die stabile `propertyId` ist die fachliche Objektidentität; Name und Adresse sind Anzeige.

Das Objekt-Dashboard verwendet bestehende Datenquellen und ergänzt ausschließlich Managementansichten:

| Bereich | Datenquelle / Verhalten |
|---|---|
| Einheiten & Mieter | `WohneinheitStatus`, bestehende Unit-Prefs, `TenantHistoryStore` |
| Mieteingänge | bestehende Receipt-Mieteinnahmen + Mietplan + Mieterhistorie |
| Miet-Jahresübersicht | 12 Monatsprojektionen aus derselben `RentTrackingLogic` wie der Monatscheck |
| Belege & Kosten | vorhandene `Receipt`-Datensätze, nur gefiltert |
| Finanzierung | vorhandene `LoanManagementSection` / `Loan`-Daten |
| Sanierungen | vorhandene Belege; 15-%-Bewertung bleibt im Steuerbereich |
| Dokumente | vorhandene `ManagedDocument`-Daten |
| Aufgaben & Fristen | lokale, property-scoped Aufgaben ohne Kalender-/Push-Integration |
| Nebenkosten | nur vorbereiteter Einstieg; keine Abrechnungslogik |
| Steuer & AfA | bestehender `AnnualTaxAssistantScreen`, unverändert |
| Objektdaten | bestehender `PropertyMetadataFormDialog` |

## Einheiten-Detailakte

Einheitenkarten sind anklickbar. Die Detailakte hat die Bereiche `Übersicht`, `Mieter`, `Miete`, `Dokumente`, `Kosten`.

- Übersicht: bestehender Einheitenstatus, Mieter, Kaltmiete, Wohnfläche und Mietbeginn.
- Mieter: bestehender `TenantHistoryStore` und vorhandener Mieterwechsel-Dialog.
- Miete: Soll/Ist des aktuellen Monats aus `RentTrackingLogic`.
- Dokumente: nur vorhandene `ManagedDocument`-Datensätze mit passender `propertyId` und stabiler `unitId`.
- Kosten: nur vorhandene Belege des geöffneten Objekts und der Einheit.

Es werden keine Dokumente, Belege oder Mieterdaten kopiert.

## Stabile Mehrimmobilien-Isolation

Phase 2 beseitigt die bekannte Kollision der historischen, namensbasierten Mietplan- und Mieterhistorien-Schlüssel.

Neue Mietplanwerte verwenden stabile Schlüssel nach dem Schema `propertyId + unitId`. Neue Mieterhistorien verwenden ebenfalls `propertyId + unitId`. Gleich benannte Einheiten, z. B. `OG links`, können damit in unterschiedlichen Immobilien unabhängig voneinander existieren.

Für `property-1` gilt eine konservative Legacy-Strategie:

1. Zuerst wird der neue stabile Schlüssel gelesen.
2. Fehlt er, darf ausschließlich für `property-1` auf den alten namensbasierten Schlüssel zurückgefallen werden.
3. Ein sicher gefundener Legacy-Wert wird idempotent in den neuen stabilen Schlüssel kopiert.
4. Der alte Wert wird niemals gelöscht.
5. Für andere Immobilien gibt es keinen Fallback auf namensbasierte Schlüssel.

Bestandsbelege werden nicht anhand eines gleichlautenden Einheitennamens einem neuen Objekt zugeschlagen. Für neue Immobilien zählt ausschließlich die explizite `propertyId`; bestehende Legacy-Belege bleiben auf `property-1`, bis der Nutzer sie ausdrücklich zuordnet.

## Miet-Monatscheck und Jahresmatrix

`RentTrackingLogic` ist die gemeinsame Projektion für Monatscheck, Einheitenakte und Jahresmatrix. Sie verwendet bestehende Receipt-Klassifizierung (`isRentalIncomeReceipt`), Mieterhistorie und Mietplanwerte. Zeitanteilige Mietzeiträume werden pro Monat berechnet.

Status:
- `PAID`: vollständig bezahlt
- `PARTIAL`: Teilzahlung / prüfen
- `MISSING`: offen
- `NO_EXPECTATION`: für den Monat kein Soll

Die Jahresmatrix ruft dieselbe Monatslogik für Januar bis Dezember auf; es existiert keine zweite Sollmietenberechnung für die Matrix.

## Einheitenstatus

Die UI kann zusätzlich zu bestehenden Werten folgende Verwaltungszustände setzen:

- Vermietet
- Kündigung / Auszug geplant
- Leerstand
- Renovierung
- Vermarktung / Inseriert
- Neuvermietung geplant

Ein Status erzeugt keine Mietzahlung und keine Mieterhistorie. Die bestehende Mieterhistorie bleibt die Datenquelle für tatsächliche Mietverhältnisse.

## Schnellaktionen

Das Dashboard bietet smartphonefreundliche, vertikal angeordnete Schnellaktionen. Sie öffnen ausschließlich bestehende Workflows: Beleg erfassen, Miete prüfen, Dokumente, Einheit/Mieter und Darlehen. Es wurde keine parallele Fachlogik eingebaut.

## Wirtschaftlichkeitsansicht

Die Managementkarte leitet Kennzahlen ausschließlich aus vorhandenen Daten ab: aktuelle und jährliche Mieteinnahmen, erfasste Ausgaben, Darlehensraten, Restschuld, einfacher Liquiditäts-Cashflow und optional Bruttomietrendite bei vorhandenem Kaufpreis.

Diese Werte sind ausdrücklich eine Managementansicht aus aktuell erfassten App-Daten. Sie verändern weder DATEV noch Anlage V, AfA oder steuerliche Gewinnermittlung. Tilgung wird nicht als steuerlicher Aufwand umklassifiziert; die Darlehensrate erscheint nur als Liquiditätsabfluss in der Managementansicht.

## Aufgaben & Fristen

`PropertyTaskStore` speichert einfache lokale Aufgaben eindeutig je `propertyId`, optional je `unitId`. Felder sind ID, Titel, Notiz, Fälligkeit, Kategorie, Erledigt-Status sowie Erstell-/Änderungszeitpunkt. Überfällige Aufgaben werden gekennzeichnet. Es gibt bewusst keine Kalender-, Push- oder Reminder-Infrastruktur in Phase 2.

Die Aufgaben liegen in `property_tasks_prefs`. Da keine neue Room-Entity angelegt wurde, ist keine Room-Migration erforderlich. Die bestehende Room-Version bleibt 21.

## Nebenkosten-Vorbereitung

Der Immobilienbereich enthält einen vorbereiteten Einstieg `Nebenkosten`. Phase 2 implementiert ausdrücklich keine Nebenkostenabrechnung, keinen Umlageschlüssel, keine Heizkostenabrechnung und keine Forderungs-/Guthabenbuchung. Die vorhandene Receipt- und Mietlogik bleibt unangetastet.

## Datenmodell und Bestandsschutz

Es gibt weiterhin kein zweites Property-Modell. `Receipt`, `Loan`, `ManagedDocument`, `PropertyMetadata` und bestehende Steuer-/Exportmodelle werden nicht ersetzt. Phase 2 benötigt keine neue Room-Entity und keine destructive Migration; Room bleibt Version 21.

Unverändert bleiben insbesondere:
- DATEV und Exporthistorie
- Advisor-/Steuerberaterpaket
- AnnualTaxAssistant / Anlage V
- AfA und Kaufpreisaufteilung
- 15-%-Prüfung
- OCR / ML Kit und KI-Auswertung
- Drive-Dateistruktur, Hash-, Move/Rename- und Indexlogik
- Restore-Koordination
- Fahrtenbuch, Google Routes und Standardstrecken
- Scanner / CameraX

## Tests

Phase 2 ergänzt Regressionstests für:
- zwei Immobilien mit identischem Einheitennamen und getrennten Mietplanwerten,
- getrennte Mieterhistorien über `propertyId + unitId`,
- Legacy-Lesen und idempotentes Kopieren für `property-1`, ohne alte Schlüssel zu löschen,
- property-isolierte Aufgaben,
- strikte Receipt-Isolation: ein Legacy-Beleg mit identischem Einheitennamen darf nicht in ein zweites Objekt leaken.

Bestehende Tests für Portfolio, Room-Migration, Supplemental Backup, DATEV, Advisor, Drive/Dokumente und Fahrtenbuch bleiben aktiv und müssen im finalen CI weiter grün sein.

## Bekannte Restpunkte

- Bestandsbelege und Bestandsdarlehen bleiben bewusst `property-1`; keine automatische fachliche Umverteilung.
- Geplante Neuvermietung wird in Phase 2 als Status abgebildet; separate optionale Planfelder für Zielmiete/Startdatum/Notiz sind noch nicht als eigenes Persistenzmodell eingeführt, um bestehende Datenmodelle nicht unnötig zu verändern.
- Aufgaben/Fristen sind derzeit lokale App-Daten und noch kein externes Kalender-/Reminder-System.
- Der Nebenkostenbereich ist nur vorbereitet.
- Responsives Verhalten, CameraX/Scanner und reale OCR-Qualität müssen zusätzlich auf echten Android-Geräten geprüft werden.
