# Phase 10A – Strukturierte Identität und Anzeigepräzision

## Basis und Status

Base: `e4603086cf31924fb647198ba2e786bd3b64a925`.
Branch: `fix/phase10a-structured-identity-display-precision`. Kein Merge.
Arbeitsbaum zu Beginn sauber, Branch `work`. `git fetch origin` versucht: lokaler
Proxy auf Port 8080 nicht erreichbar. GitHub-Connector bestätigte denselben
aktuellen main wie den lokalen HEAD. Der lokale origin/main-Zeiger war veraltet
(`234bce3…`); er wurde nicht als aktuelle Basis ausgegeben.

AGENTS, CODE_MAP, PROJECT_STATUS, VERIFY_CHANGES und Phase 6A/6B, 7A/7B, 8A/8B,
9A/9B/9C/9D gelesen. Arbeitsanweisungsdatei und Produkttest-PDF sind im Repository
und den verfügbaren Library-/Shared-Dateien nicht vorhanden; die verbindlichen
M26-Reproduktionen stammen aus dem Auftrag. Historische Prüfstände der Dokumente
werden nicht als eigene Testergebnisse ausgegeben.

**Prüfstatus vor CI:** Implementierung und neue Regressionstests vorhanden;
`git diff --check` erfolgreich. Gradle scheitert lokal vor Kompilierung am
Socketzugriff (`Could not determine a usable wildcard IP`). Der Versuch mit
zusätzlicher Netzwerkberechtigung wurde unterbrochen. Kein lokaler Gradle- oder
Emulatorerfolg behauptet. Verbindlich sind die Checks des aktuellen PR-Heads.
Phase 10A ist erst nach vollständiger grüner CI inklusive Emulator abgenommen.

## M26-01: Ursache und Vertrag

Anlageassistent: Namen → `joinToString(", ")` → PropertyMetadata.wohneinheiten.
Reload: `split(",")` → Namen → Name-/Index-ID-Fallback. Zwei Namen konnten drei
Einträge und doppelte stabile IDs erzeugen. Der Drive-Reader enthielt denselben
Mechanismus und zusätzlich ungescopte Name-Schlüssel für andere Immobilien.

Es gibt keine lokale Room-Unit-Tabelle. Die vorhandene Room-Property-Spalte bleibt
das einzige Listenfeld, trägt nun jedoch ein versioniertes JSON-Objekt mit `unitListVersion: 1` und
einem Einheitenarray aus `{unitId,name}`. Ein alter Einheitsname `[]` bleibt damit
ein Label und wird nicht mit einer leeren strukturierten Liste verwechselt.
UnitIdentity kodiert und validiert dieses Feld zentral. Keine zweite Liste in
Preferences, keine zweite Datenarchitektur. Bestehende Preference-Fachwerte bleiben
unter propertyId + unitId; neue Anlagewerte werden vor Veröffentlichung der
Property geschrieben. UI-Editoren behalten eine explizite ID je Namensfeld.

Die ID im strukturierten Feld ist autoritativ. Index-Schlüssel werden nicht mehr
zur ID-Auflösung gelesen oder beim Reload überschrieben. Reihenfolge und Name
ändern keine bestehende ID. Namen mit Kommas, mehreren Kommas, Semikolon, Pipe,
Slash und Whitespace bleiben ein einzelnes Label. Die bestehende Regel gegen
leere/doppelte Namen innerhalb einer Immobilie bleibt erhalten. Gleichnamige
Einheiten unterschiedlicher Immobilien sind getrennt.

Legacy-Rohtexte bleiben erhalten. Vollständige vorhandene Name→ID-Schlüssel im
richtigen Property-Scope dürfen Listengrenzen nur dann rekonstruieren, wenn es
genau eine vollständige Lösung mit eindeutigen IDs gibt. Indexwerte sind kein
Beweis. Konkurrierende Lösungen, fehlende Struktur oder beschädigtes JSON ergeben
einen sichtbaren Prüfzustand statt geratener Einheiten. Beim Lesen werden diese
Rohwerte und IDs nicht umgeschrieben. Reparatur ist anhand des ursprünglichen
Snapshots möglich. Ein historischer einzelner separatorfreier Name behält den
bereits bestehenden deterministischen Legacy-ID-Vertrag; es wird keine zufällige
Ersatz-ID vergeben. Struktur wird erst bei einer bewussten gültigen Speicherung
geschrieben, nicht durch eine automatische Altwertbereinigung.

JSON liegt weiterhin als String im bestehenden Property-Backupfeld. Supplemental
15, Receiptvertrag 2, Room 34 unverändert; keine Migration. Neue App liest alte
Sicherungen. Alte App-Versionen verstehen den neuen Feldinhalt nicht; ein Downgrade
auf einen alten Parser ist kein unterstützter Rückweg.

## Referenzen und zusätzliche bestätigte Ursachen

| Priorität | Datei / Funktion | Reproduktion und Wirkung | Korrektur / Nachweis |
| --- | --- | --- | --- |
| P1 | ReceiptViewModel.loadUnits / resolveUnitId | M26-Kommaname erzeugt dritte Einheit und doppelte ID | Strukturierter gemeinsamer Codec, keine Indexidentität; Identity-/Compose-/Emulatortests |
| P1 | DrivePersistenceRepository.getWohneinheitenFromPrefs / saveWohneinheitenToPrefs | Kommaname bzw. gleichnamige fremde Property wird beim Backup falsch gelesen | Gleiche Struktur, propertyId+unitId, bestehender Restoremodus; Fake-Drive-Roundtrip |
| P1 | UnifiedUnitsFeature.unitReceipts, AnnualTaxAssistantFeature.buildUnitAnnualSummaries/buildAnnualRentRows, RentIncomeFeature.incomeBreakdown, RentOverviewPresentation.unassigned | U1 umbenennen, Receipt behält U1 und alten Namen: Beleg verschwindet aus direkter Anzeige oder wird unzugeordnet | ReceiptUnitResolver.belongs bevorzugt ID und prüft Scope; Legacy-Label nur eindeutig; Resolver-/Relations-E2E |
| P1 | ReceiptViewModel.loadUnitById / TenantHistoryStore / PropertyUnitScopedData | Ein alter Name zeigt auf OLD, strukturierter Eintrag auf NEW: Name-Fallback leiht Fläche/Miete/Historie oder überschreibt alte Rohwerte | Widersprechende Namensalias-IDs sperren Lesen und Legacy-Spiegelschreiben; Store-Test mit erhaltenem OLD-Bestand |
| P1 | FirestoreService.saveWohneinheit / ReceiptViewModel.syncWithCloud | Name als Cloud-Dokumentkey: Rename erzeugt weiteres Dokument; gleichnamige Properties teilen Key | Cloudkey aus Property+Unit-ID, Scope im vorhandenen Dokument; unbekannte/mehrdeutige Remote-IDs vor Writes blockiert; kein Löschen alter Remote-Dokumente |
| P2 | ReceiptViewModel.learnVendorRule/loadLearnedRules, DrivePersistenceRepository Rule-Preferences | Aussteller oder Einheit enthält `|||`: Felder/Count verschoben | JSON-Objekt, eindeutiger Legacyvertrag; beschädigte Rohwerte nicht überschreiben; Codec-Test |
| P2 | ReceiptOriginalChain / Originalpfad-Verbraucher | Datei `Original, EG.pdf` wird als zwei Pfade geöffnet/gezählt | Gemeinsamer Reader, vorhandene vollständige Datei zuerst; neue separatorhaltige Pfade als JSON; Originalpfadtest |
| P3 | RentOverviewPresentation.tenants | Mietername mit ` → ` wird erneut in scheinbare Personen zerlegt/dedupliziert | Projektion trägt Personenliste neben Anzeigetext; kein Split des Labels; Projektionstest |
| P3 | UnifiedUnitsFeature Wohnfläche | 41,25 → 41 m² | GermanNumberInput.formatQuantity; Compose-/Emulatorprüfung |
| P3 | AfaPortfolioFeature Wohn-/Grundstücksfläche und AfA-Satz | 41,25 → 41 m² bzw. 2,33 → 2,3 % | Gemeinsamer deutscher Mengenformatter, keine Fachwertänderung |
| P3 | LogbookFeature.germanKm | gespeicherte 1,25 km → 1,3 km | Gemeinsamer Mengenformatter, keine Kilometer-/Buchungsänderung |

Kein Receipt/Property/Document-ID-Neumatching über Namen. Bankliste,
BankRentAssignment/BankReceiptLink, Belegrelationen, Dokumentbytes/MIME/Hash und
RenovationReview bleiben dieselben Datensätze. Der Bank-Splitpicker schreibt
keinen sichtbaren Namen mehr als Ersatz-ID. Annual-Composekeys benutzen stabile
Unit-IDs. Historische Fremd-/unbekannte IDs fallen nicht auf ein Label zurück.
DATEV-EXTF, CSV/ZIP, Konten, BU, Freigabe und Audit werden nicht geändert;
der Originalanhangpfad verwendet lediglich denselben Reader wie die App.

## M26-02 und Formatierung

GermanNumberInput bleibt der gemeinsame deutsche Zahlenhelper; Currency bleibt
NumberFormatter. Mengen erhalten variable Dezimalstellen ohne Ganzzahlkonversion:
41,25; 41,99; 41,5; 41. Float-Widening-Rauschen wird anhand des tatsächlich als
Float gespeicherten Werts dargestellt (41.99f, nicht 41.990001678...). Keine
DB-/Preference-Umschreibung oder Rundung gespeicherter Werte.

## Appweites Suchinventar

Basisinventar aktiver Kotlin-Produktdateien unter app/src/main, Treffer als
**Zeilen**, nicht Anzahl Methoden oder Bugs. Überschneidungen sind möglich:

- 218 Zeilen mit joinToString/split.
- 35 weitere substringBefore/substringAfter/tokenize-Zeilen.
- 44 Zeilen des Name-/Label-/Index-/Fallback-Suchmusters.
- 48 UI-Zeilen mit Ganzzahlkonversion/floor/ceil/%.0f.
- Zusätzlich Formatmuster %.1f/DecimalFormat/maximumFractionDigits und direkte
  Receipt-/Unit-Namensvergleiche verfolgt. Die Tabelle oben nennt ausschließlich
  reproduzierbare Fehlermechanismen; nicht jeder Treffer ist ein Fehler.

Bewusst unverändert: standardkonforme CSV-/DATEV-/CAMT-Formate, ISO-Datumteile,
Dateiendungen/Drive-Pfadsegmente, PDF-Textumbruch, Hash-Hexdarstellung, Such-/KI-
Prompttexte, explizite Suchbegriffsyntax, Anzahl/Baujahr/Progress/Confidence,
Bitmap-/Layout-/Dateigrößenkonversionen und gerundete Diagramm-/Monitorprozente.
Bestätigt wurden elf in der Tabelle getrennt dokumentierte Mechanismen/Anzeigepfade.
Von den 48 Ganzzahlkonversionszeilen waren vier Flächenanzeigen fehlerhaft (Einheit,
Objekt-Wohnfläche, AfA-Wohnfläche, AfA-Grundstück); zwei weitere bestätigte
Formatverluste lagen in den zusätzlich gesuchten %.1f-Anzeigen (AfA-Satz, Kilometer).
Kompakte Mio.-Summen sind bewusst gerundete Übersichtswerte. Nicht aktive alte
PropertyDashboard-Detailzweige werden nicht pauschal refaktoriert.

Abschließende Suche erneut für die geforderten Komma-/Semikolon-/Pipe-Muster,
toInt/roundToInt/toLong durchgeführt. Verbleibende Delimiter im gemeinsamen
Legacy-Reader sind explizite Rückwärtskompatibilität, keine neue ungesicherte
Speicherung separatorhaltiger Labels. Mehrdeutige historische Mehrfachpfadstrings
ohne vollständige vorhandene Dateien/strukturierte Dokumente bleiben eine Grenze:
Rohdaten werden nicht heuristisch umgeschrieben.

## Tests und Prüfgrenzen

Neue Phase10AIdentityTest: Kommanamen, vergiftete Indexwerte, mehrere Separatoren,
Whitespace, Reorder, Duplicate-Regel, eindeutige und mehrdeutige Legacywerte,
Propertyisolation, zweimal Supplemental-MERGE, REPLACE_FULL, Miet-/Bank-/Dokument-
beziehungen/Originalbytes, Mengenformatierung, Händlerregeln, Originalpfade und
Mieterlabelprojektion. Phase10ADriveRoundTripTest nutzt vorhandenen Fake-Drive,
produktiven Metadatawriter, FullRestoreCoordinator und tatsächliche Restorepfade:
leeres Ziel, zweimal MERGE, lokal umbenannter Bestand, REPLACE_FULL, Receipts,
Originale, BankReceiptLink, Miethistorie und RenovationReview.

Phase10AIdentityComposeTest prüft die produktive Detailanzeige bei wiederholtem
Öffnen mit Kommaname/U1/41,25 m². Native Phase10AIdentity10/13/15EmulatorTest
verwenden produktives createProperty, zwei Einheiten, echte MainActivity-Screens,
Rename, unveränderte Receipt-/Bank-/Dokument-/Mietbeziehungen, Activity recreate,
App- und Android-Zurück bei FontScale 1,0/1,3/1,5. Keine Tests deaktiviert,
Assertions gelockert, pauschalen Retries oder CI-Zeitlimits geändert.

Pflichtprüfung: gezielte Phase10A-/9A-/9B-/9C-/9D-Tests, testDebugUnitTest,
lintDebug, assembleDebug, assembleDebugAndroidTest und kompletter vorhandener
Emulatorjob. Run-/Head-Belege und abschließende Resultate stehen im PR. Angelegte
Tests sind kein vorweggenommener Erfolgsnachweis. Live-Drive-/Firestorekonten,
physisches Gerät und Kanzleiimport werden nicht als geprüft ausgegeben.

## Laufprotokoll

- Run 38983523905, Head 5fd918a: Kompilierungsfehler in der Vollständigkeitsanzeige
  des Metadateneditors (übrig gebliebener Verweis auf editWohneinheiten). Auf die
  strukturierten Namensfelder korrigiert. Keine Assertions oder Tests verändert.
- Run 38983757418, Head 2bda7fb: lintDebug erfolgreich; Unit-Suite beim Erstellen
  dieses Protokolleintrags noch aktiv. Dieser Zwischenstand ersetzt nicht die
  komplette Abnahme des folgenden finalen Heads.
- Neue Legacy-Prüfung enthält außerdem einen sichtbaren Fehlerzustand bei allen
  drei Emulator-Schriftgrößen, erhaltenen Rohwert nach Activity recreate und
  Verbot scheinbarer leerer Einheitenkarten. Das Formular bietet Einheiten weiter
  einzeln zum Hinzufügen/Entfernen an; vorhandene IDs werden beim Rename bewahrt.
  Nur eine ausdrücklich hinzugefügte neue Einheit erhält eine neue ID.

- Run 38983757418: alle 1.030 JVM-Fälle ausgeführt; 1.015 bestanden, 15 Fehler.
  Die neuen Phase-10A-Fälle bestanden. Die 15 Fehler betrafen drei ältere
  Fixturegruppen (Phase5BInitialRentPersistenceTest, Phase7BComposeTest,
  RentOverviewComposeTest), die mehrteilige Kommastrings ohne stabile Zuordnung
  bereitstellten. Die RentOverview-Fixture ersetzte anschließend sogar die zuvor
  erzeugten IDs. Diese Fixtures erhalten nun ihre beabsichtigten stabilen IDs
  bereits bei Vorbereitung als strukturierte Liste. Der native Phase-7B-
  Erweiterungsfixture folgt demselben Vertrag und behält die ursprüngliche erste
  ID. Sämtliche fachlichen Assertions, Fallzahlen und Zeitlimits bleiben bestehen;
  mehrdeutige Legacywerte werden weiterhin ausdrücklich separat getestet.

- Run 38984437402, Head d7018166: Verify vollständig grün (Lint, gesamte JVM-Suite,
  assembleDebug); assembleDebugAndroidTest ebenfalls erfolgreich. Der vollständige
  Emulatorlauf führte alle 70 Fälle ohne Skips aus: 67 bestanden, die drei neuen
  FontScale-Fälle scheiterten an derselben Test-Erwartung für TenantPeriod.unitName.
  Dieser Wert wird vom bestehenden Historienreader aus dem aktuellen Anzeigenamen
  projiziert und nicht als Mietidentität gespeichert. Nach Rename muss der Test
  daher den neuen Anzeigenamen erwarten. Alle übrigen Periodenfelder werden weiter
  vollständig verglichen; zusätzlich wird der unveränderte gespeicherte
  Historienrohwert vor/nach Rename geprüft. Keine Produktionsänderung, kein
  gelockerter ID-/Relationsvergleich, Retry, Skip oder Timeoutwechsel.
  Der anschließende vollständige aktuelle Lauf bleibt Pflicht.
