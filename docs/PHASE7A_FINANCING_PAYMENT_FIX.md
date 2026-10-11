# Phase 7A – Finanzierung und bestätigte sonstige Einnahmen

- Base: `92a795a9341971e6e6901cb722ce900b7f768c5d`, frisches `git fetch origin main`, sauberer main-Checkout.
- Branch: `fix/phase7a-loans-bank-income`. Kein Merge.
- Testbasis: vollständiger Martin-Weber-Bericht `ImmoPilot_Produkttest_Martin_Weber_main_92a795a.pdf` vom 11.10.2026 (Library), Arbeitsanweisungen, AGENTS, Code-/Architektur-/Prüfwegweiser und angrenzende Tests.

## R01

`PropertyDetailHost` rendert `PropertyReferenceDetail`, nicht das alte `PropertyDashboard`.
Dessen Finanzierungseintrag erreicht den weiterhin vorhandenen FINANCE-Zweig daher nicht.
Die aktive Aktionsliste erhält eine Zeile Finanzierung mit unverändertem `PropertyReferenceRow`.
FINANCE ruft die vorhandene `LoanManagementSection` mit expliziter `property.propertyId` auf.
Der Komponentenfilter verwendet diese stabile ID unabhängig vom global ausgewählten Objekt.
Dialogzustände sind an dieselbe ID gebunden. Room-LoanDao, IDs, Bestandsdaten und
Zinsbelegzuordnungen bleiben bestehen; kein zweites Darlehensmodul.

Navigation: Objekt → Finanzierung → bestehender Verwaltungsdialog → vorhandener Editor.
Speichern aktualisiert denselben Loan-Datensatz und schließt nur den Editor.
Abbrechen/System-Zurück/Editor-Pfeil schließen ohne Speichern. Verwaltungsdialog → Finanzierung;
Finanzierungs-Pfeil/System-Zurück → Objektakte. Keine neue globale Navigation.

Abgegrenzte erlaubte Miniänderung zu R05: gespeicherte Zahlvorbelegung nutzt
`GermanNumberInput.formatForInput`. Der vorhandene Parser entfernt Punkte; `90000.0`
wurde dadurch beim unveränderten Speichern zu `900000`. Deutsche Vorbelegung verhindert
auch diese Datenveränderung. Die ursprüngliche Dezimalpräzision bleibt über die Skala erhalten.
Kein Zahlen-/Darlehensrefactoring. Editor erhält einen Zurück-Pfeil zum vorhandenen Dismiss.
Die Zusammenfassung erhält lediglich Gewicht für den Titel, damit er bei Großschrift umbrechen kann.

## R02

Der abschließende direkte Bankzweig in `nonRentalEntries` verwirft bislang jede Einnahme
über `tx.isIncome`. Entfernt wird ausschließlich diese pauschale Richtungsbedingung.
Alle bestehenden Bestätigungs-, Objekt-, Klassifikations-, Zuordnungs- und Darlehensausschlüsse
bleiben maßgeblich. Die Fallbackkategorie berücksichtigt die Zahlungsrichtung.
Positive direkte Miete/NK, Darlehensauszahlungen und Kaution bleiben aus dem generischen
sonstigen Einnahmenpfad ausgeschlossen. Keine neue steuerliche Freigabe.

Dedup: stabile transactionId, bestätigte BankReceiptLinks und vorhandene BankRentAssignments
(Splits/Miete/NK/Kaution) sperren den generischen Zweig; BankLoanAssignments sperren ihn ebenfalls.
Links und Splits behalten ihre bisherige anteilige Abdeckung. Unbestätigte Splitreste werden nicht
generisch ergänzt. Keine Betrags-/Datums-/Partnerheuristik. Derselbe gefilterte Entry-Bestand
versorgt Liste, Einnahmen, Ausgaben, Ergebnis und Buchungszahl; LedgerOverview bleibt unverändert.

## Nachweise und Grenzen

- Neue `Phase7APaymentTest`: 18 gezielte Projektionsfälle einschließlich unsplit +200 EUR,
  Beleg/Split-Dedup, Miete/Teil-/Überzahlung/NK, Kaution, Privat, Transfer, Darlehen, Phase-6A-Ausgaben,
  Objekt-/Jahresfilter und keine automatische Aufnahme in APPROVED.
- Neue echte MainActivity/Room-Emulatorfälle: Finanzierung über aktive Objektakte, vorhandene Daten,
  Edit/Save/Cancel/Android-/App-Zurück, Activity.recreate und neuer Activity-/VM-Start, Objekt A/B,
  gemeinsamer Finanzfluss; Schriftfaktoren 1,0/1,3/1,5 mit Screenshots und Layoutassertion.
- Bank-E2E: synthetische Objekt-/Kategoriefixture, echte Bankdetailaktion „Kein Beleg erforderlich“
  mit Sonstiges, Zahlungsflusszeile und dieselben Kennzahlen/Objekt-/Jahresfilter. Die bestehende
  Bankdetailansicht hat keinen direkten unsplit Objektselektor; diese Zuordnung ist explizit Fixture,
  kein behaupteter UI-Schritt und keine Scope-Erweiterung der Bankliste.
- Lokal Java vorhanden, kein Gradle/Android-SDK/adb. Standardbuilds und vollständige vorhandene
  Regressions-/Emulatorsuite über unveränderten Android-CI-Workflow. Finale Ergebnisse im PR.
- Room 34, keine Migration. Supplemental-Backup 15 unverändert. DATEV, Freigaben, Bankliste,
  Bankpersistenz und Restore-Struktur unverändert. Bestehende Regressionen bleiben aktiv.
- R03 und R04 bleiben offen. R05-Zahlvorbelegung ist die ausdrücklich erlaubte Miniänderung;
  keine darüber hinausgehende Editorüberarbeitung. Keine externe Drive-/Kanzleiabnahme behauptet.
