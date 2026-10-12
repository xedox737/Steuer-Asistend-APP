# Phase 7B – Objekt-Finanzweg und Smartphone-Sammelmieten

Base nach sauberem Checkout und `git fetch origin`: `ab52c85f9b0fa794395ff4a5d332288f79405adf`.
Branch: `fix/phase7b-property-finance-rent-loan-ux`. Kein Merge.
Testbasis: vollständiger Martin-Weber-Bericht `ImmoPilot_Produkttest_Martin_Weber_main_92a795a.pdf` vom 11.10.2026;
Arbeitsanweisungen, AGENTS, CODE_MAP, Immobilienarchitektur, Prüfwegweiser und Phase-7A-Code/Tests.

## R03 – bestätigte Ursache und Änderung

Aktiv: `PropertyReferenceDetail` → `PropertyDetailHost.RECEIPTS` → bisher `PropertyReceipts`.
Die alte private Komponente liest ausschließlich `ImmobilienManagerProjection.receipts(property, units, receipts)`:
`ReceiptViewModel.receipts` (Room ReceiptDao), anhand der Objektidentität/Legacy-Regel gefiltert.
Banktransaktionen, BankReceiptLinks und BankRentAssignments kommen dort nicht an.
Karten besitzen weder Click-Handler noch Detail-Callback. Der Kategorie-Textfilter verwendet nur
`hauptkategorie.contains`; Jahresfilter `datum.startsWith`, Einheitsfilter den Anzeigenamen.
Alleinstehende bestätigte Bankausgaben und sonstige Bankeingänge (Phase 7A), reine Bankmieten und Splits fehlen.

Der aktive Objektweg verwendet jetzt unverändert `LedgerScreen` / `LedgerOverview`:
Receipt-, Property-, BankRentAssignment-, BankReceiptLink-, BankTransaction- und BankLoanAssignment-StateFlows.
`LedgerPaymentPresentation.entries` bleibt einzige Zahlungsfluss-/Dedup-Projektion;
`filter` bleibt einzige Jahres-/Objekt-/Kategorie-/Suchfilterlogik und `totals(filtered)` bleibt gemeinsame
Quelle für Kennzahlen und angezeigte Liste. Kategorie entspricht der vorhandenen effektiven Unterkategorie
mit Hauptkategorie-Fallback; Suche berücksichtigt zusätzlich die Beleg-Hauptkategorie.
Keine neue Taxonomie, Summenberechnung oder Datenquelle.

`LedgerScreen(propertyId)` erhält die stabile ID direkt aus der geöffneten Objektakte. Im Objektkontext
ist diese für `LedgerFilters.propertyId` bindend; dieselbe vorhandene Filterdarstellung zeigt nur das
aktuelle Objekt als Option. Kein Wechsel auf „Alle Immobilien“, kein Name als Identität und kein
Rückgriff auf die globale Auswahl. Global bleibt der bisherige frei wählbare Objektfilter erhalten.
Objektgebundener Compose-Key trennt A/B. Vorhandener SaveableStateHolder erhält Objektakte, Sektion,
Ledger-Filter und Scrollzustand bei Beleg-/Bankdetail und Activity.recreate.
Belegdetail erhält `AppScreen.PROPERTIES` als vorhandenes Rückkehrziel, global weiterhin LEDGER.
Bankdetails verwenden den bereits vorhandenen Origin-Rückweg. Pfeil/System-Zurück aus Ledger führen
über den vorhandenen PropertyDetailHost zurück zur Objektakte, danach zur Portfolioübersicht.

PropertyReceipts hat nach vollständiger Aufrufsuche keinen weiteren Aufrufer; sie bleibt intern erhalten,
wie beauftragt, ist aber kein aktiver Finanzweg mehr. Keine separate alte Filterlogik für den Objektweg gepflegt.

## R04 – bestätigte Ursache und Änderung

Bisher fünf Felder in `Row.horizontalScroll` mit 160/210/110/100/110 dp plus 4 × 6 dp = 714 dp.
Neuer Dialog nutzt verfügbare Breite (bis 900 dp) und FontScale. Tabellenzeile nur, wenn 714 × FontScale dp
im Inhaltsbereich passen. Auf Smartphones fünf vertikal angeordnete vollständige Eingabefelder in einer
bestehenden Ui2-Karte pro Einheit. Einheitsname und Status können über FlowRow umbrechen.
Kein horizontales Scrollen. Eine gemeinsame Feldkomponente aktualisiert denselben InitialRentRow über unitId.
LazyColumn-Keys, Auswahl, Vorschau, Abbruch, validiertes zweistufiges Speichern, AppDateField und
RentPlanInput/GermanNumberInput bleiben erhalten. Schutz und erneute Persistenzprüfung vorhandener
Mietverträge/Historiendaten einschließlich zukünftiger Verträge unverändert.
Standard-Dialog-IME-Verhalten und vertikal scrollende Eingabefelder; Aktionen außerhalb der LazyColumn.

## R05 – bereits in Phase 7A korrigiert

Im Base sind Darlehenssumme, Restschuld, Sollzins, Tilgung, Rate und Vermietungsanteil bereits über
GermanNumberInput.formatForInput vorbefüllt. Skala mindestens 2, sonst volle gespeicherte Double-Dezimalpräzision.
Speichern nutzt den bestehenden LoanFeature-Parser (Punkte entfernen, Komma → Punkt).
Kein zusätzlicher produktiver Zahlenfix nötig. LoanEditDialog bleibt privat und unverändert. Die Zahlen-/Speicherassertionen laufen über
den aktiven Finanzierungsweg auf echtem Android mit Room. Die Compose-Prüfung enthält 100000/90000/3,
3,5/100000,25 und 87,12345; vollständige Loan-Gleichheit beim unveränderten Speichern.
Bestehender Phase-7A-Emulatorpfad erhält zusätzlich unverändertes Speichern mit vollständiger Room-Gleichheit.

## Tests und Prüfgrenzen

Neue Scope-Projektionsfälle: A/B, +200-Eingang, 1200-Ausgabe, verknüpfter 500-Beleg, Split über A/B,
Dedup, identische Liste/Kennzahlen, Jahr, Haupt-/Unterkategoriesuche.
Neue Robolectric/Compose-Fälle: 393 dp, 1/8/20 Einheiten, Schriftfaktoren 1/1,3/1,5, Tablet,
Zuordnung/Scrollzustand/Vorschau/Speichern, Abbruch/System-Zurück und Loan-Zahlenroundtrip.
Neue echte MainActivity-Emulatorfälle: Objekt-Ledger, Belegdetail, beide Rückwege, weiteres Zurück,
A/B mit identischen Namen, abweichende globale Auswahl, Bankdetail-Rückkehr, Filter und Activity.recreate;
20 Einheiten bei allen drei Schriftfaktoren, echte IME/Datumskalender, Eingabe/Scrollen/Speichern/Wiederöffnen,
Bestandsvertrag geschützt. Screenshots im bestehenden CI-Report-/Emulatorartefakt.

Lokal Java 17 vorhanden, kein Gradle/Android-SDK/adb. Build/Lint und sämtliche bestehenden
Unit-/Restore-/DATEV-/Phase-7A-/Emulatortests über den unveränderten Android-CI-Workflow.
Tatsächliche Ergebnisse werden nach Ausführung im PR dokumentiert; keine vorweggenommene Erfolgsaussage.
Room-Schema, Migrationen, Backup 15, Restore, DATEV, Bankliste und Persistenzlogik unverändert.
Keine Cloud-/Kanzlei-/Realgeräteabnahme behauptet.

Native Robolectric prüft die responsive Sammelerfassung. Der zusätzliche vollständige
Darlehens-Zahlen-/Eingabe-/Speichertest läuft als Android-Compose-Test über den aktiven Objektweg:
Robolectric erreicht für den vorhandenen FlowRow-Editor keinen ruhenden Layoutzustand und läuft
auch mit Legacy-Grafik in eine Textvermessungsschleife (AppNotIdleException / ShadowTrace-OOM).
Die Assertions wurden auf das echte Android-Rendering mit zusätzlicher Room-Prüfung
übertragen; keine Produktionsfensteränderung, Testsperre, Timeout-Erhöhung oder Testabschaltung.
Zwei reine Zahlenregressionen prüfen die zentrale Vorbelegung und präzisen Double-Roundtrip.
Die bestehende Ledger-Compose-Klasse erhält dieselbe Application/AndroidViewModelFactory-Isolation
wie die übrigen App-Workflowtests; ihre Erwartungen bleiben unverändert.

Die gemeinsame Emulator-Zurückhilfe behält Espressos echte Key-Injektion und Main-Thread-
Synchronisation bei und wählt ausdrücklich das tatsächlich fokussierte Android-Fenster.
Dies vermeidet den beobachteten gecachten Activity-Root hinter modalen Kalendern.
Eine direkte, verallgemeinerte UiAutomation-Injektion wurde verworfen, weil der Fenster-
Fokuswechsel noch nicht zwingend abgeschlossen war. Keine Callback-Abkürzung, Wiederholung,
Zeitverzögerung oder schwächere Zielassertion. Der Darlehens-Zahlenfall scrollt die LazyColumn
vor der Suche nach noch nicht komponierten Feldern und identifiziert den Editor über seinen Dialogtitel.
