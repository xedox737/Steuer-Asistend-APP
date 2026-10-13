# Phase 8B – W03/W04/W05

Base nach sauberem main-Checkout und erneutem `git fetch origin`:
`db6a19035fa87b975f5e813e142b3a8d65e9b796`.
Branch: `fix/phase8b-payment-history-datev-audit`. **Nicht mergen.**
Testbasis: vollständiger Martin-Weber-Bericht `ImmoPilot-Produkttest-Martin-Weber-main-72aa308.pdf`,
Arbeitsanweisungen, AGENTS, CODE_MAP, VERIFY_CHANGES, Immobilien-/Finanz-/Backup-Architektur,
Phase-6A-/7A-/7B-/8A-Dokumentation und angrenzende Bank-/Miet-/DATEV-/Backup-Tests.

## W03

Aktiver Weg: `PropertyDetailHost` → `UnifiedPropertyUnitsScreen` → `UnifiedUnitDetailScreen`.
Der Host liefert Objektbelege; die Einheitenansicht sammelt vorhandene BankRentAssignment-,
BankReceiptLink- und BankTransaction-StateFlows. Der aktuelle Monatscheck verwendet schon
`RentTrackingLogic.month` → `RentPaymentProjection`. Bisher liest ausschließlich die
Leerstands-Karte `lastPayment` aus namensgefilterten `unitReceipts` → `isRentalIncomeReceipt`
→ max Receipt.datum; Bankquellen erreichen diesen Ausdruck nicht.

Die bestehende Miet-/NK-Teilprojektion aus `LedgerPaymentPresentation.entries` ist jetzt als
`rentalEntries` wiederverwendbar. Private/Transfer-verknüpfte Beleganteile bleiben ausgeschlossen.
Sie verwendet unverändert `RentPaymentProjection.project`: manuell bestätigte Bankzuordnung,
Normal-Klassifikation, bestätigter Bankabgleich, gültiger Mietmonat, positive endliche Beträge,
keine Überallokation; Receipt-Prüfstatus und Löschstatus. Rent-/NK-Vorauszahlung/-Abrechnung
bleiben Zahlungen; Kaution und sonstige Einnahmen bleiben getrennt.

`lastRentalPayment` wählt daraus ausschließlich explizite `propertyId + unitId` und ein gültiges
positives Zahlungsdatum. Keine Namens-/Betrags-/Datumsidentitätsheuristik. Bestehende
Receipt-ID/Internal-ID/Link-/Assignment-Dedup-Regeln laufen vor Einheiten-/Datumsfilterung.
Miet-/NK-Splits derselben Transaktion und derselben Einheit ergeben für die letzte tatsächliche
Zahlung einen Betrag. Bankdatum bleibt das Zahlungsdatum; `rentMonth` und `tenantReference`
werden nicht umgeschrieben oder aus dem aktuellen Mieter neu abgeleitet.

Leerstand und Mieterwechsel filtern die Zahlungshistorie nicht weg. Die aktuell vermietete
Einheit zeigt weiterhin das unveränderte Monats-Soll/Ist/Offen aus `RentTrackingLogic`, sodass
September-Nachzahlung nicht zur Oktobermiete des Nachmieters wird. Kein historischer Write.
Receipt ohne stabile unitId wird für die letzte Zahlung nicht anhand eines Namens geraten;
Bestandsdaten bleiben erhalten. Bestehende Namensfallbacks anderer Detailbereiche unverändert.

Die bestehende Zahlungsstatus-Karte verwendet bei Großschrift ihre gleichen drei Teilkarten
untereinander; bei Schriftfaktor 1,0 bleibt die Drei-Spalten-Darstellung. Texte dürfen umbrechen.
Globaler Kopf/Bottom Navigation und Einheitennavigation unverändert.

## W04/W05

Bisher: `allAuditRuns` aus Repository/Room-DAO (`ORDER BY timestamp DESC`) → Ergebnis-Schritt 6
→ `auditRuns.take(5)` ohne älteren Zugang und `Text(run.status)`.
Statusmodell: SUCCESS, CANCELLED, FAILED dokumentiert; Exportpfad schreibt zusätzlich
CREATED_NOT_READY. Keine RUNNING/PARTIAL-Codes erfunden.

Gemeinsamer UI-Mapper: Erfolgreich, Abgebrochen, Fehlgeschlagen, Erstellt · noch nicht bereit;
unbekannt → Status unbekannt. Audit-Rohcode, Modelle, Logs, DB und Backup unverändert.
Eine gemeinsame Auditkarte versorgt Vorschau und vollständige Historie. ID, Profilversion,
Buchungszahl, Summe und SHA bleiben sichtbar; vorhandener Zeitstempel mit deutschem Datum/Jahr
und vorhandener Dateiname machen ältere CSV-/ZIP-Läufe eindeutig erkennbar.

Vorschau bleibt die neuesten fünf mit ausdrücklichem Vorschauhinweis und bei >5 Läufen
„Alle anzeigen“. Vollständige Historie als LazyColumn mit stabilen exportlaufId-Keys und
unveränderter DAO-Reihenfolge. Auch vom DATEV-Start erreichbar, ohne neuen Export auszulösen.
Lokaler saveable Anzeigezustand; keine globale Route und kein neuer Backstack.
Android-/App-Zurück verwenden denselben Handler zur vorherigen DATEV-Ansicht (Start/Ergebnis).
Keine neuen Detail-/Reexportaktionen, kein zusätzlicher Status-/Jahresfilter.

## Tests und Prüfgrenzen

Neue Unit-Tests: Receipt/Bank, stabile IDs/Umbenennung/A-B-Isolation, direkte und Link-Dedup,
Miet-/NK-Komponenten, Leerstand/Nachzahlung, alte Mietrelation, offene Kandidaten, Kaution,
Privat/Transfer einschließlich verknüpfter Receipts, neuestes Zahlungsdatum, Teil-/Überzahlung.
Auditmapper mit sämtlichen Modell-/Write-Codes und unbekanntem Code; 0/1/5/6/20/100 Vorschau.
Unveränderter Supplemental-Backup-Roundtrip zweimal mit Bankzahlung/Mietrelation und 6 Roh-Audits.

Native Robolectric/Compose: 393 dp, 0/1/5 kompakt, 6/20/100 vollständig erreichbar,
LazyColumn-Scroll und gemeinsame deutsche Statuslabels bei 1,0/1,3/1,5.
Echte Android-MainActivity/Room-Tests bei allen drei FontScales: Objekt → Einheit →
Leerstands-Bankmiete 980 EUR am 05.10.2026, Activity.recreate, beide Rückwege;
laufende Einheit mit Bank-/Receipt-Dedup und Vormieter-Nachzahlung bleibt 600 EUR im aktuellen Monat.
DATEV-Ergebnisfixture mit 6 Runs, Vorschau/ältester Lauf/2021/Status, recreate,
beide Rückwege und Historieneinstieg vom DATEV-Start. Reale Exporterzeugung bleibt separat
im unveränderten DatevCsvEmulatorTest geprüft. Screenshots und Textlayout-Assertions im CI-Artefakt.

Lokal: Java 17 vorhanden, Gradle/Android-SDK/adb fehlen. Standardchecks und gesamter
bestehender Emulatorjob über den unveränderten Android-CI-Workflow; Ergebnisse erst nach Ausführung.
Keine physische Geräte-/Drive-/Kanzleiabnahme behauptet.

Room 34 und Supplemental-Backup 15 unverändert; keine Migration, keine Serializer-/EXTF-/CSV-/ZIP-/
Freigabe-/Banklistenänderung. Phase-8A-Darlehen, globale Navigation, Mietkontrollalgorithmus und
bestehende Phase-7A-/7B-Regressionsassertionen unverändert.
