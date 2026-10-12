# Phase 8A – W01/W02 Darlehensvalidierung

Base nach sauberem main-Checkout und erneutem `git fetch origin main`:
`72aa308fb60b3a9d57809baebdcdc2299940374a`.
Branch: `fix/phase8a-loan-validation-safety`. Kein Merge.
Testbasis: vollständiger aktueller Martin-Weber-Bericht `ImmoPilot-Produkttest-Martin-Weber-main-72aa308.pdf`,
Arbeitsanweisungen, AGENTS, CODE_MAP, VERIFY_CHANGES, Phase-7A-/7B-Dokumentation und bestehende Tests.

## Ursachen und Regeln

W01: Der private `parseLoanNumber` entfernte jeden Punkt und verwendete `toDoubleOrNull`
ohne Endlichkeitsprüfung. Alle sechs Felder (Summe, Restschuld, Sollzins, Tilgung, Monatsrate,
Vermietungsanteil) konnten Sonderwerte erhalten. Der gemeinsame Save-Pfad prüfte nur die ersten
 drei Felder auf null/negativ; NaN umgeht negative Vergleiche. Infinity überlebt diese Prüfung.
Alle sechs Vorbelegungen berechneten `toBigDecimal().scale()` ohne Prüfung; dies wirft für Infinity/NaN.

W02: Optionales Parse-null wurde für Tilgung/Rate zu 0, Anteil zu 100. Anschließend
`coerceIn(0,100)` statt Validierung; negative Tilgung/Rate passierten. Kalendergültigkeit
allein sicherte keine Reihenfolge.

Der Editor verwendet jetzt `LoanInputValidation` auf `GermanNumberInput.parseResult`.
Dessen bestehende deutsche/technische Grammatik bleibt die einzige Zahlenlogik; `parse()` behält
sein bisheriges Double?/finite-Verhalten für sämtliche übrigen Formulare. Neue Statusinformationen
unterscheiden leer, Formatfehler und nicht-endlich; Darlehensregeln ergänzen Bereich/ungültigen Altwert.
Keine wissenschaftliche Eingabe, kein Float-Parser, kein Entfernen beliebiger Zeichen.

Pflicht: Summe, Restschuld, Sollzins. Optional leer: Tilgung/Rate = 0, Anteil = 100, wie bisher.
Nicht leer und ungültig hat **keinen** Ersatzwert. Alle sechs Felder: endlich und nicht negativ.
Anteil ausschließlich 0–100, inklusive Grenzen; kein Clamp. Keine erfundene 100%-Grenze für Sollzins/Tilgung.

Keine bestehende zentrale Darlehens-Obergrenze gefunden. Technischer Schutz für die übrigen Zahlen:
`sqrt(Double.MAX_VALUE) / 100`, etwa `1,34 × 10^152`.
Dies ist **keine** bankfachliche/steuerliche Plausibilitätsaussage. Das Produkt zweier zugelassener
Werte bleibt mit 10.000-fachem Abstand unter Double.MAX_VALUE; damit ist die bestehende
Restschuld × Zins-Rechnung geschützt. Double.MAX_VALUE-nahe Werte werden abgewiesen;
310/1000-stellige Überläufe ergeben keinen speicherbaren Wert. Keine Rundung/Begrenzung.

Vorbelegung prüft zuerst Endlichkeit und verwendet nur danach die bisherige deutsche Formatierung
mit vollständiger gespeicherter Skala. Ungültige Altwerte erscheinen als „Ungültiger gespeicherter Wert“
mit direktem Feldfehler und bleiben bis zur expliziten Korrektur unspeicherbar.
Keine globale Änderung am `GermanNumberInput.formatForInput`-Vertrag. Bestehendes 0 wird in
Pflichtfeldern als 0 vorbefüllt; neues Darlehen startet dort weiterhin leer. Optionales 0 bleibt leer.

Datum: AppDateField/ISO intern unverändert. Zinsbindungs-/Laufzeitende vor Start blockiert;
gleiches Datum erlaubt. Kein harter Vergleich Bindungsende ↔ geplantes Laufzeitende
(z.B. vorzeitige Rückzahlung), keine Datumsänderung. Fehler direkt am betroffenen Feld.
Alle Zahlen/Datumsfehler werden vor `onSave` geprüft, kein partieller Editor-Write.
Abbrechen und beide Rückwege unverändert. Objekt-/Darlehensidentität bindet Editor-State.

Der zusätzliche bestehende Dokument-Darlehens-Write in ReceiptViewModel prüft den fertigen
Darlehenskandidaten mit denselben Zahlenregeln vor upsert; kein KI-Feature/Anbieterverhalten geändert.
Objektanlage nutzt bereits den endlichkeitssicheren zentralen Parser. Restore darf historische
Sonderwerte weiterhin lesen, damit sie reparierbar bleiben; kein DAO-/Schema-/Restore-Filter,
keine automatische Bereinigung und keine Umdeutung zu null/0.

## Prüfungen

Neue 8 Unit-Testmethoden: deutsche Eingaben und Punktdezimal, alle sechs Felder gegen
310/1000 Ziffern, Double.MAX_VALUE-nahe/übersteigende Zahlen, NaN/±Infinity, Exponentenschreibweise,
leer vs. ungültig, Negativwerte, Anteilsgrenzen, Altwert-Korrektur, Skalen-/Zero-/Subnormal-Roundtrip,
technische Produktgrenze und Kalender/Chronologie.

Neue 9 Android-Compose-/E2E-Fälle (3 Methoden × Schrift 1,0/1,3/1,5):
aktiver Objektweg, extrem große Restschuld, W02-Beispiele, Room bleibt unverändert bei Fehler/Abbruch,
Korrektur/Speichern/Wiederöffnen, vollständige Gleichheit beim unveränderten Speichern,
Activity.recreate, neuer Activity/VM-Zyklus, Objekt A/B, historischer Restore mit Infinity und Reparatur.
NaN wird direkt als Modellfixture in den **produktiven** Editor eingespeist: SQLite bindet NaN
als NULL; ein behaupteter NaN-Room-Roundtrip wäre nicht aussagekräftig.
Screenshots: gültig, Extremrestschuld, Tilgung/Rate, Anteil, korrigiert, Infinity/NaN-Altbestand;
Fehlertexte per TextLayoutResult auf vollständigen Umbruch geprüft, Aktionen bleiben sichtbar.
Bestehende Phase-7A-/7B-/AppDateField-/Backup-/DATEV-/Bank-/AfA-/Gemini-Tests bleiben unverändert aktiv.

Lokal: Java 17, kein Gradle/Android-SDK/adb. Gradle-Aufrufe scheitern lokal bereits an
`gradle: command not found`. `git diff --check` lokal erfolgreich.
Build/Lint/gesamte Unit-Suite, assembleDebugAndroidTest und vollständiger Emulatorjob laufen
über den vorhandenen unveränderten Android-CI-Workflow. Ergebnisse erst nach tatsächlichem Lauf im PR.
Keine physische Geräte-, Cloud-Drive- oder Kanzleiabnahme behauptet.

## Unverändert

Room 34 / keine Migration. Supplemental-Backup 15 / Restore-Struktur unverändert.
Keine DATEV-, Banklisten-/Banklogik-, globale Navigations-/Kopf-/Bottom-Navigation-Änderung.
Keine neuen Features. W03/W04/W05 bleiben ausdrücklich offen.
