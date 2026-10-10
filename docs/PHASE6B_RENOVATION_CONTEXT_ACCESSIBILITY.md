# Phase 6B – Objektkontext, Sanierungsprüfung und Großschrift

## Basis / Ursachenanalyse vor Produktcodeänderungen

- main ausgecheckt, `git fetch origin` und `git pull --ff-only origin main` durchgeführt.
- Ausgangs-main: `3f93939e6f1356e45df15a7767e4ca191f4f9b5a`; Arbeitsbaum sauber.
- Branch: `fix/phase6b-renovation-context-accessibility`. **Nicht mergen.**
- Arbeitsanweisungen vollständig gelesen, AGENTS und Projekt-/Code-/Prüfwegweiser gelesen.
- Aktueller Martin-Weber-Retest vom 10.10.2026 (main `6dd65bc…`) vollständig gelesen;
  beigefügte PDF vom 08.10. ist eine ältere Quelle. PR #121 / Phase 6A ist enthalten.
- Bestehende Room-/Beleg-/Dokument-, Property-/Unit-, AfA-, DATEV-, Preference-Backup-,
  UI2- und echte Android-CI-Pfade geprüft. Lokal Java 17, aber kein Gradle/SDK/adb;
  erforderliche Android-Prüfungen laufen über den vorhandenen CI-Emulator.

### MW-05

`AddReceiptScreen` verwendet ausschließlich `propertyMetadata` und dessen kommaseparierte
Einheitennamen. Ein sichtbarer Property-Picker fehlt. `saveReceipt` liest beim Speichern
erneut den globalen Property-State und fällt ohne Kontext auf `property-1` zurück.
Der globale State selbst fällt auf das erste aktive Objekt zurück. Ein zuletzt geöffnetes
Haus B mit derselben WE01 wie Haus A ist deshalb eine plausible, aber falsche Zuordnung.
Manuelle Erfassung und Scanner-/Dateianalyse teilen denselben Speichereinstieg. KI kann
einen Einheitennamen vorbefüllen, ohne eine eigene stabile Auswahl zu besitzen.

Korrektur: eigener expliziter Erfassungskontext, sichtbarer Property-/Unit-Picker aus
bestehenden Material-/UI2-Komponenten, stabile IDs und vorhandener ReceiptUnitResolver.
Global nur eine tatsächlich gespeicherte aktive Auswahl vorbefüllen; kein First-Property-
Fallback. Objektakte darf explizit vorbefüllen. Wechsel leert die Einheitenwahl.
Speichern validiert erneut die gewählte Property/Unit, ohne globale Auswahl umzuschreiben.

### MW-06

Der feste Hinweis `01.10.25 - 31.01.26` steht direkt im Erfassungsformular.
`isEigenleistungSanierung` ist ein bestehendes Beleg-/DATEV-Feld und keine Maßnahmenbeziehung.
ReceiptItem besitzt zwar `massnahme`, diese freie Zeichenfolge ist keine stabile Identität.
PropertyMetadata besitzt bereits `uebergangNutzenLasten`, `notariellesKaufdatum`, Kaufpreis,
Gebäude-/Grundstückswert und Aufteilungsquelle. TaxPropertyCalculator berechnet daraus
Gebäude-Anschaffungskosten einschließlich zugeordneter Erwerbsnebenkosten, AfA und
Dreijahreszeitraum. Diese Basis wird wiederverwendet, strikt mit Objektbelegen.

Bestehende ManagedDocument-IDs, receiptInternalId, Originalanhänge/Dateihashes und
Dokumentöffner sind vorhanden. AdvisorAnnualSummary / PdfExporter / FileProvider bieten
Berichts-/Teilenmechanismen. Kanzleipaket enthält schon AfA-/Monitorunterlagen und Originale;
es fehlen Maßnahmen, stabile Belegbeziehungen und Steuerberater-Notizen.

Entscheidung: kleine lokale JSON-Struktur im vorhandenen Preference-/Supplemental-Pfad.
Ein Datensatz pro stabiler Maßnahmen-ID und pro receiptInternalId. Ein Beleg hat höchstens
eine aktuelle Beziehung; Wechsel überschreibt sie, Entfernung erhält einen leeren Bezug
als Schutz gegen Wiederbelebung durch MERGE. Dokumentnachweise referenzieren ausschließlich
ManagedDocument-IDs mit Rolle, keine weitere Dateiablage. Supplemental 14 → 15; Room 34
unverändert. Bestehende MERGE-Regel `local wins` je Schlüssel erhält spätere lokale Änderungen
und importiert fehlende IDs; zweimaliger MERGE ist idempotent.

Neue Prüfung ist bewusst getrennt von buchhalterischer Freigabe. Nur ausdrücklich für
15 % vorgemerkte Beziehungen zählen, niemals alle Reparaturkategorien. Netto aus belastbaren
vorhandenen Positionen oder bewusst eingegebenem Nettowert; fehlende Nettodaten bleiben
offene Prüffälle. Gutschriften folgen derselben Vorzeichen-/Stornoerkennung wie DATEV.
Belegdatum ist ein vorläufiger Zeitbezug, kein Nachweis des steuerlichen Leistungszeitraums;
zeitraumübergreifende Maßnahmen werden kenntlich gemacht. Keine automatische Steuerentscheidung.
Keine Änderungen an AfA-Basis/Formeln oder DATEV-EXTF/BU/Konten/Freigabe/Readiness/Jahresfreigabe.
Der bisherige Sanierungs-Monitor-Einstieg öffnet dieselbe neue Prüfung. Die bestehende
Jahresabschluss-/Buchungslogik bleibt unabhängig; Maßnahmenstatus verändert sie nicht.

### MW-07

UI2Grid erzwingt ab 320 dp zwei Spalten ohne FontScale. Ui2Metric ohne Icon legt teilweise
lange Statuswerte und Label nebeneinander; die verbleibende Labelbreite zerlegt Bankbelege.
Ui2ActionCard reserviert zusätzlich 42 dp für das Icon, weshalb Kontoauszüge im schmalen
Halbkarten-Textbereich bricht. Hauptziel-Label Immobilien besitzt maxLines=1. Header enthält
zwei Texte in der festen Standard-TopAppBar-Höhe; Untertext kann bei 1.5 überlaufen.

Korrektur: vorhandene Komponenten nach verfügbarer Breite und FontScale anordnen, lange
Kennzahlen untereinander, Karten natürlich wachsen lassen. Header-/Bottom-Navigation-
Struktur, Farben, Icons und Hauptziele erhalten; ausschließlich Text-/Inhaltshöhe flexibel
machen. Keine verkleinerte Schrift, scaleX, Ellipse oder neue Designsprache.

## Fachliche Quelle / Grenzen

BMF-Schreiben vom 26.01.2026, Abgrenzung Instandsetzung/Modernisierung:
https://www.bundesfinanzministerium.de/Content/DE/Downloads/BMF_Schreiben/Steuerarten/Einkommensteuer/2026-01-26-instandsetzung-modernisierung-gebaeude.html
Gesetzliche Grundlage (§ 6 Abs. 1 Nr. 1a EStG):
https://www.gesetze-im-internet.de/estg/__6.html
Vorprüfung, Zuordnung und Übergabe, keine Einzelfall-Steuerentscheidung. Zeitraum beginnt
bevorzugt mit Besitz/Nutzen/Lasten; Notardatum ist nur ausdrücklich benannter Ersatz.

## Prüfplan

Neue Unit-/Robolectric-Fälle: Erfassungskontext, gleiche Unit-Namen, Allgemein,
objektbezogene Basis/Zeiträume, Netto/Gutschrift, bewusste Auswahl, Dedup/Wechsel,
Notizen/Nachweise, defekte Beziehungen, MERGE zweimal und REPLACE.
Echte MainActivity: global A/WE01 speichern/wieder öffnen, Objekt → Prüfung → Maßnahme
mit Beleg, Abbrechen/App-/Android-Zurück, FontScale 1.0/1.3/1.5 bei ca. 393 dp für
Start, Immobilien, Beleg, Prüfung und DATEV. Textlayoutassertions prüfen zusätzlich
Wortgrenzen und abgeschnittene Texte, Screenshots als vorhandene CI-Artefakte.
Vollständige bestehende Phase-5A-/5B-/6A- und Android-Suiten bleiben aktiv.

Der Emulator erzeugt zusätzlich einen echten PDF-Bericht, prüft ihn mit Android PdfRenderer
und sichert ihn über den vorhandenen CI-Artefaktpfad. Wiederherstellung wird auch mit einem
vollständigen Objekt-/Maßnahmen-/Beleg-/Gutschrift-/Dokument-Graphen geprüft: bestehender
Core-Upsert, anschließend Supplemental, jeweils zweimal MERGE. Der Bericht listet auch
explizit vorgemerkte Belege ohne Maßnahme. Kennzahlen werden pro StateFlow-Projektion
einmal berechnet, nicht bei jedem UI-Zugriff erneut summiert.

Verbindliche Prüfergebnisse, Artefakte und finaler Git-Stand werden in
PR #122 dokumentiert: https://github.com/xedox737/Steuer-Asistend-APP/pull/122
