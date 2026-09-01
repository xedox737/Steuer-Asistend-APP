# Steuer-Assistent – Übergabe für ChatGPT Work

Stand: 01.09.2026
Repository: `xedox737/Steuer-Asistend-APP`
Branch: `main`

## Aktueller Entwicklungsstand

Phase 3 ist abgeschlossen.

Zuletzt technisch verifiziert:
- Jahresassistent mit Anlage-V-Struktur
- Vorjahresvergleich
- Wohneinheiten-Auswertung
- Jahres-Mietprüfung
- Jahresabschluss-Check
- Anlage-V-Vorschau
- Werteherkunft / Prüfstatus
- manuelle Jahresfreigabe mit Fingerprint-Invalidierung
- Jahresfreigabe ist im Supplemental-Drive-Backup enthalten

Der letzte normale Android-CI-Testbuild für den Phase-3.8-Stand wurde separat angestoßen.

## Wichtige Arbeitsweise ab jetzt

Nicht nach jedem Mini-Schritt auf Rückmeldung warten.

Stattdessen in zusammenhängenden Arbeitsblöcken vorgehen:
1. exakte aktuelle Quelldateien lesen
2. mehrere zusammenhängende Schritte umsetzen
3. nach sinnvollen Teilabschnitten kompilieren / Tests prüfen
4. nur bei echtem Fehler stoppen
5. Ursache exakt aus Log/Source bestimmen
6. gezielt korrigieren
7. am Ende einen sauberen Gesamtbuild / Android CI erzeugen

Keine unnötigen temporären Workflow-Ketten und keine wiederholten Vollbuilds.

Niemals Erfolg behaupten, solange CI noch queued/in progress ist.

## Phase 4 – Ziel

Phase 4 erstellt einen professionellen Steuerberater-Jahresabschluss-Export auf Basis der bereits vorhandenen App-Daten.

Geplante 6 Hauptschritte:

### 4.1 Steuerberater-Paket Grundstruktur
Ein strukturiertes ZIP-Paket pro Steuerjahr erzeugen.

Vorgesehene Ordner:
- `00_Start`
- `01_DATEV`
- `02_Originalbelege`
- `03_Anlage_V`
- `04_Mieten`
- `05_Finanzierung`
- `06_AfA_und_15Prozent`
- `07_Sanierungen`
- `08_Pruefprotokoll`

Keine Dubletten der Originalbelege.

### 4.2 Jahresübersicht / Start-PDF
Ein Steuerberater-Startdokument erzeugen.

Reihenfolge:
1. Objektübersicht
2. Finanzierung
3. Mieten
4. Sanierungen
5. AfA / 15-%-Monitor
6. Werbungskosten
7. offene Prüfhinweise
8. beigefügte Originalunterlagen

### 4.3 Anlage-V-Unterlagen integrieren
Die Daten aus dem bestehenden Jahresassistenten übernehmen:
- Einnahmen
- Werbungskosten
- AfA
- Schuldzinsen
- Mietabgleich
- offene Prüfpunkte
- Abschlusscheck
- Anlage-V-Vorschau
- Werteherkunft / Prüfstatus
- manuelle Jahresfreigabe

Keine festen ELSTER-Zeilennummern erfinden.

### 4.4 Belege + DATEV bündeln
Bestehende DATEV-Komponenten wiederverwenden.

Bereits vorhandene relevante Klassen:
- `app/src/main/java/com/example/util/AdvisorPackageBuilder.kt`
- `app/src/main/java/com/example/util/PdfExporter.kt`
- `app/src/main/java/com/example/util/DatevExporter.kt`
- `app/src/main/java/com/example/util/DatevCsvSerializer.kt`
- `app/src/main/java/com/example/util/DatevFormatValidator.kt`
- `app/src/main/java/com/example/util/DatevOriginalAttachmentPolicy.kt`
- `app/src/main/java/com/example/util/ReceiptManifestService.kt`
- `app/src/main/java/com/example/data/ExportAuditRun.kt`

Bestehende DATEV-Sicherheitsregeln dürfen nicht abgeschwächt werden.

### 4.5 Abschluss- und Freigabelogik
Ein Paket darf nur als `BEREIT FÜR STEUERBERATER` gelten, wenn:
- keine kritischen roten Jahresprüfpunkte offen sind
- keine kritischen Abschlusschecks offen sind
- manuelle Jahresfreigabe für genau den aktuellen Daten-Fingerprint gültig ist
- erforderliche Originalunterlagen vorhanden sind bzw. fehlende Unterlagen eindeutig ausgewiesen werden

Ändern sich relevante Jahresdaten, muss die Bereitschaft automatisch wieder verfallen.

### 4.6 Exportprüfung + Gesamtbuild
Vor Abschluss:
- ZIP-Inhalt automatisiert prüfen
- Pflichtordner prüfen
- keine doppelten Originalbelege
- DATEV-Formatprüfung bestehen lassen
- Unit Tests
- Kotlin/Android Compile
- Debug APK
- normaler Android-CI-Lauf

Erst danach Phase 4 als abgeschlossen melden.

## Bereits identifizierte aktuelle Dateien

Phase-3-Jahreslogik:
- `app/src/main/java/com/example/ui/AnnualTaxAssistantFeature.kt`

Drive-Zusatzbackup:
- `app/src/main/java/com/example/data/SupplementalDriveBackup.kt`

Steuer-/AfA-Berechnung:
- `app/src/main/java/com/example/data/TaxPropertyCalculator.kt`

Finanzierung:
- `app/src/main/java/com/example/ui/LoanFeature.kt`

Mieten/Mieterhistorie:
- `app/src/main/java/com/example/ui/RentIncomeFeature.kt`
- `app/src/main/java/com/example/ui/RentTenantWrapper.kt`
- `app/src/main/java/com/example/ui/TenantHistoryFeature.kt`

Haupt-UI / ViewModel:
- `app/src/main/java/com/example/ui/ReceiptAppUi.kt`
- `app/src/main/java/com/example/ui/ReceiptViewModel.kt`

## Phase-3-Jahresfreigabe – wichtige Details

SharedPreferences:
`annual_tax_approval_prefs`

Gespeichert pro Jahr:
- `fingerprint_<year>`
- `approved_at_<year>`

Der Fingerprint berücksichtigt u. a.:
- Jahreswerte Einnahmen/Ausgaben/Ergebnis
- Buckets
- Prüfpunkte
- Abschlusschecks
- Miet-Soll/Ist
- relevante Jahresbelege
- Objektmetadaten
- Darlehen

Wird etwas Relevantes geändert, ist die alte Freigabe automatisch veraltet.

Die Jahresfreigabe wird über `SupplementalDriveBackup` gesichert und wiederhergestellt.

## Steuerliche / fachliche Leitplanken

- App ist Vorbereitungshilfe, keine Steuerberatung.
- Keine ungeprüften KI-Daten automatisch in DATEV übernehmen.
- Keine festen ELSTER-Zeilennummern erfinden.
- 15-%-Grenze und steuerliche Einordnung konservativ anzeigen, aber keine verbindliche Rechts-/Steuerentscheidung vortäuschen.
- Originalbelege nur aus tatsächlich verifizierten Originalbytes in das Steuerberaterpaket aufnehmen.
- Synthetische Ersatz-PDFs nicht als Originalbelege ausgeben.
- bestehende Dubletten- und Referenz-Sicherheitsmechanismen nicht umgehen.

## Nächster konkreter Auftrag in Work

Arbeite Phase 4 in zwei Blöcken ab.

### Block A
Umsetzen und jeweils intern prüfen:
- 4.1 Steuerberater-Paket Grundstruktur
- 4.2 Jahresübersicht / Start-PDF
- 4.3 Anlage-V-Unterlagen integrieren

Vorgehen:
1. `AdvisorPackageBuilder.kt`, `PdfExporter.kt`, `DatevExporter.kt`, `AnnualTaxAssistantFeature.kt` und die unmittelbar benötigten Modelle exakt lesen.
2. Bestehende Infrastruktur wiederverwenden statt parallele Exportlogik neu zu erfinden.
3. 4.1 umsetzen und kompilieren.
4. 4.2 ergänzen und kompilieren/testen.
5. 4.3 ergänzen und testen.
6. Nur bei Fehlern anhalten und zuerst die exakte Ursache analysieren.

### Block B
Danach ohne erneute Freigabe fortfahren, sofern Block A grün ist:
- 4.4 Belege + DATEV bündeln
- 4.5 Abschluss-/Freigabelogik
- 4.6 Exportprüfung + Gesamtbuild

Am Ende einen einzigen normalen Android-CI-Lauf erzeugen und erst nach bestätigtem Erfolg Phase 4 als abgeschlossen melden.
