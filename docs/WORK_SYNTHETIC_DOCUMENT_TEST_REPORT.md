# Testbericht – synthetische Dokumente, KI und OCR

## Umfang und Testumgebung

- Ausgangsstand: `7ab0dc30c87b0568ad6b1ce66e0c010bb12c526f` auf `main`
- Testart: lokale JVM-/Robolectric-Unit-Tests mit temporärem Dateisystem, In-Memory-Room und reinem In-Memory-Fake-Drive
- Externe Dienste: keine echten Google-Drive- oder KI-Aufrufe; keine produktiven Dateien, Receipts oder Backups verändert
- Fixtures: 35 deterministisch erzeugte, ausschließlich fiktive Dateien; PDF/JPG-, Konflikt-, Dubletten- und Legacy-Fälle
- Verifikation: finaler normaler Android-CI-Lauf auf `main` führt Unit Tests, Lint und Debug-APK-Build aus. Die genaue Run-ID steht im Abschlussbericht des Arbeitsauftrags.

## Erzeugte Fixtures

| Bereich | Anzahl | Inhalt |
|---|---:|---|
| Fachliche Dokumente | 18 | Handwerker- und Baumarktrechnung, Versicherungsrechnung, Grundsteuer, JPG-Scans, Text-/Bild-PDF, Kaufvertrag, Darlehen, Zinsen, Energieausweis, Mietvertrag, Übergabe, Police, Grundriss, Kaufpreisaufteilung, Sanierung |
| Dubletten/Konflikte | 12 | identische Kopie, gleicher Name/anderer Hash, geänderte PDF-Metadaten, fehlende lokale Referenzen, konkurrierende IDs, nicht erreichbare Datei, falscher Hash, fremde Datei |
| Legacy | 5 | lokale Referenz, nur receipt-index, nur AppProperties, Orphan und Hash-Dublette zur neuen Ablage |

Die Dateien werden durch `SyntheticDocumentFixtureFactory` erst im temporären Testverzeichnis erzeugt. Es werden keine Binär-Fixtures oder echten Daten in Git gespeichert.

## Automatisierte Prüffelder

| Funktionsbereich | Nachweis | Ergebnis nach finalem CI |
|---|---|---|
| Fixture-Erzeugung | genau 35 nichtleere Dateien unter isoliertem Temp-Root; Hash-/Namensvarianten geprüft | PASS |
| OCR/PDF | eingebetteter Text hat Vorrang; bildbasiertes PDF erzeugt keinen erfundenen Text; unlesbares Bild liefert kontrollierten Fehler; Hash bleibt unverändert | PASS |
| KI-Klassifikation | deterministischer Fake für Kaufvertrag, Darlehen, Mietvertrag, Energieausweis, Rechnung und unbekannt; niedrige Confidence bleibt `PRÜFEN` | PASS |
| KI-Feldprüfung | Vorschläge starten `AUSSTEHEND`; Ändern, Übernehmen und Ignorieren geprüft; bestehende Werte ändern sich erst nach ausdrücklicher Bestätigung | PASS |
| Drive-Reklassifikation | Echter `DrivePersistenceRepository.syncManagedDocumentToDrive`-Pfad mit In-Memory-Fake für Kaufvertrag, Darlehensvertrag und Mietvertrag; Vorschlag allein ohne Move; danach Ziel/Name aktualisiert, DriveFileId, Bytes und Hash stabil | PASS |
| Offline-Sync | bestätigte lokale Änderung bleibt pending; nach Wiederverbindung wird dieselbe Datei reorganisiert | PASS |
| Drive-Inventur | read-only Snapshot-Vergleich; `OK`, `LEGACY_LAYOUT`, `ORPHAN`, `MISSING_LOCAL_REFERENCE`, `MULTIPLE_REFERENCES`, `POSSIBLE_DUPLICATE`, `MIGRATION_PRUEFEN`; fremde Datei ignoriert | PASS |
| Migration/Dubletten | mehrdeutige Ziele/Hashes bleiben `PRÜFEN`; keine automatische Zusammenführung oder Löschung; Journal-/Resume-Logik durch bestehende Tests | PASS |
| Volltextsuche | acht geforderte Suchbegriffe sowie Jahr-, Typ-, Property- und Einheitenfilter; leere Suche | PASS |
| Backup/Sicherheit | ManagedDocuments, Darlehen und Fahrtenbuch; keine API-Schlüssel, Access-Tokens, lokalen Pfade oder OCR-Volltexte; Schema 3 | PASS |
| Restore | idempotentes Supplemental Restore, Suchindex-Neuaufbau, Rollback bei korruptem Dokumentarray, Core-Fehler blockiert Supplemental, Teilfehler bleibt Fehler | PASS |
| DATEV | bestehende Export-, Freigabe-, Mapping-, Original- und Formatvalidator-Tests; gültiger EXTF-Testexport | PASS |
| Advisor-Paket | UI-unabhängige Summary, Fingerprint/Freigabe, fehlende Originale, strukturierte Inhalte und ZIP-Dublettenschutz | PASS |
| Fahrtenbuch | unveränderte Routing-, Distanz-, Stop-, Standardroute-, Backup-/Restore- und CSV-Regressionstests | PASS |
| Room | bestehende explizite Migrationstests einschließlich Dokument-, Receipt-, Freigabe- und Fahrtenbuchmigration; keine destructive Migration | PASS |
| Smoke | bestehende Robolectric-/Compose-Smoke- und Screenshot-Tests; manuelle Navigationsmatrix ergänzt | PASS |

## Feststellungen und Änderungen

Der vorhandene Code enthielt bereits die sicherheitsrelevanten Kernmechanismen: stabile Dokumentidentitäten, PDF-Text-vor-OCR, unverbindliche KI-Vorschläge, read-only Drive-Inventur, Hashprüfung vor Reorganisation, transaktionales Supplemental Restore, DATEV-Validierung, Advisor-Readiness und unveränderte Fahrtenbuchlogik. Die Lücke lag in einem durchgängigen, gemeinsam verwendeten synthetischen Beispieldatensatz sowie in einem Test des echten Repository-Sync-Pfads statt nur seiner Planungslogik.

Für den produktiven ManagedDocument-Pfad wurde die kleinstmögliche testbare Schnittstelle `ManagedDocumentDriveGateway` ergänzt. Ihre Produktionsimplementierung delegiert die bestehenden Ordner-, Metadaten-, Download-, Move/Rename-, Upload- und Index-Operationen unverändert an `GoogleDriveClient`; es entstand kein zweiter Drive-Client und keine zusätzliche Geschäftslogik. Ergänzt wurden ansonsten nur Testcode und Dokumentation. Die Tests erzeugen den Bestand zur Laufzeit, prüfen die bestehenden Policies gemeinsam und verändern keine realen Daten.

## Finaler produktionsnaher Drive-E2E

Der Repository-Test beginnt mit einem echten `ManagedDocument` in einer In-Memory-Room-Datenbank und einem vorhandenen Original im Fake Drive. Er führt anschließend `ManagedDocumentService.confirmReview(...)` und den produktiven `DrivePersistenceRepository.syncManagedDocumentToDrive(...)` aus. Damit sind echte Zielpfadauflösung, kanonische Benennung, Move/Rename, Hashprüfung, lokale DB-Aktualisierung und die reale Erzeugung von `document-index.json` in einer Kette abgedeckt.

| Fall | Erwartung | Ergebnis |
|---|---|---|
| Fixture 9 – Kaufvertrag | `00_Stammdaten/01_Kauf_Eigentum`; gleiche File-ID, Bytes und SHA-256 | PASS |
| Fixture 10 – Darlehensvertrag | `03_Finanzierung_AfA/Darlehen`; gleiche File-ID, Bytes und SHA-256 | PASS |
| Fixture 13 – Mietvertrag | `01_Einheiten/WE_01/Mietvertrag`; bestätigte stabile Unit-ID im Index | PASS |
| Offline/503 mit Retry | kein Upload und keine Mutation; bestätigte Klassifikation bleibt; Retry reorganisiert dasselbe Original | PASS |
| HTTP 429 / Timeout | kein Move, Rename oder Upload; klarer Prüfstatus | PASS |
| Hash-Mismatch | blockiert vor Move/Rename; kein Ersatz-Upload und kein falscher Index-Erfolg | PASS |
| Drive 404 | bestehende ID bleibt erhalten; keine Ersatzkopie | PASS |
| Dokumentindex | alle Identitäts-, Zuordnungs-, Datei-, Hash-, Größen-, Review- und Zeitfelder geparst geprüft; alter Ordner/Name entfernt | PASS |

## Synthetischer DATEV- und Advisor-E2E

Ein gemeinsamer synthetischer Receipt-Bestand für 2026 umfasst Handwerker, Baumarkt, Versicherung, Grundsteuer, Verwaltung, Miete und Schuldzinsen sowie negative Fälle für ungeprüfte KI, bereits exportiert, unvollständige Kontierung und doppelte stabile Identität. Die Originaldateien stammen aus demselben deterministischen Fixture-Generator wie die Dokumenttests.

Der DATEV-Test durchläuft Freigabe-/Eligibility-Prüfung, echtes Mapping, BookingValidation, `DatevExporter`, `DatevCsvSerializer`, `DatevFormatValidator`, OriginalAttachmentPolicy und schließlich den DATEV-Bereich des echten Advisor-ZIP. Er prüft Beträge, stabile Belegreferenzen, ausgeschlossene ungeprüfte Belege, Dublettenblockade, einmalige Originalbytes und ein wieder einlesbares gültiges EXTF-Artefakt.

Der Advisor-Test erzeugt die Jahresdaten über `ReceiptViewModel.buildAdvisorAnnualSummaryForExport(...)`, ohne zuvor einen UI-Screen zu öffnen. Geprüft werden Einnahmen, Ausgaben, zugeordnete Schuldzinsen, AfA, Mietabgleich, Fingerprint und aktuelle manuelle Freigabe. Das reale `AdvisorPackageBuilder`-ZIP enthält alle neun Pflichtbereiche, Start-PDF, Anlage-V-Vorschau, Werteherkunft, DATEV-Datei und eindeutige Originale. Veralteter Fingerprint, geänderte Jahresdaten, kritische Jahres-/Abschlussfehler und fehlende erforderliche Originale bleiben blockierend.

Die DATEV- und Advisor-Prüfung nutzt bewusst dieselben Receipt-IDs, Beträge und Originaldateien. Legacy-Drive-Ordnerwerte sind in den Receipts gesetzt, beeinflussen aber weder Mapping noch Export; damit ist die fehlende Folderpfad-Abhängigkeit ausdrücklich abgedeckt.

## Gefundene Bugs

Der erste CI-Lauf der Testentwicklung zeigte einen kontrolliert reproduzierbaren Fehler: `DocumentOcrService` initialisierte den ML-Kit-Recognizer bereits im Konstruktor. War `MlKitContext` nicht verfügbar, entstand eine `IllegalStateException` noch vor dem in `extract` vorhandenen Fehlerfang. Dadurch konnte insbesondere ein unlesbarer Bildscan den vorgesehenen kontrollierten Fehlerpfad nicht erreichen.

Minimaler Fix: Der Recognizer wird lazy initialisiert. Für eine ungültige Bilddatei wird er gar nicht benötigt; bei echter OCR erfolgt seine Initialisierung innerhalb des bereits geschützten `try/catch`. Erkennungslogik, OCR-Reihenfolge und Originaldatei bleiben unverändert.

## Offene Risiken und echte Gerätetests

- ML-Kit-OCR auf einem echten, leicht schiefen und einem absichtlich schlechten Kamerabild benötigt weiterhin den Gerätetest; Robolectric validiert die Steuerlogik und den kontrollierten Fehlerpfad, nicht die native Erkennungsqualität.
- CameraX-/Scanner-Verhalten, Android-Dateiauswahl, Berechtigungsdialoge und visuelle Darstellung müssen auf mindestens einem kleinen und einem aktuellen Android-Gerät geprüft werden.
- Echte Provider-Latenz, Kontingente, Authentifizierung und Google-Drive-Konsistenz sind bewusst nicht Teil der deterministischen Unit Tests. Ein isolierter Test-Drive kann ergänzend gemäß manueller Matrix geprüft werden.
- Der Fake unterscheidet 404, 429, 503 und Timeout deterministisch. Die konkrete HTTP-Fehlerübersetzung des echten Google-Endpoints sowie eventual consistency nach einem realen Move/Rename bleiben ein Test mit einem isolierten Drive-Konto.
- Die synthetischen PDF-Container sind bewusst klein und deterministisch; komplexe Fremd-PDFs (verschlüsselt, beschädigt, exotische Fonts) bleiben Teil manueller/kuratierter Robustheitstests.

## Finale Einschätzung

Die automatisierbare Geschäftslogik ist durch isolierte synthetische Dateien und vorhandene Regressionstests abgedeckt. Produktionsdaten und externe Dienste bleiben unberührt. Die Freigabe dieses Testauftrags setzt einen vollständig erfolgreichen normalen Android-CI-Lauf samt Debug-APK-Artefakt voraus.
