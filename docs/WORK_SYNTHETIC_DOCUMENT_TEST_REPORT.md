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
| Drive-Reklassifikation | Fake-Drive-E2E für Kaufvertrag, Darlehensvertrag und Mietvertrag; Vorschlag allein ohne Move; danach Ziel/Name aktualisiert, DriveFileId und Hash stabil | PASS |
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

Der vorhandene Code enthielt bereits die sicherheitsrelevanten Kernmechanismen: stabile Dokumentidentitäten, PDF-Text-vor-OCR, unverbindliche KI-Vorschläge, read-only Drive-Inventur, Hashprüfung vor Reorganisation, transaktionales Supplemental Restore, DATEV-Validierung, Advisor-Readiness und unveränderte Fahrtenbuchlogik. Die Lücke lag in einem durchgängigen, gemeinsam verwendeten synthetischen Beispieldatensatz und zusammenhängenden Fake-Drive-Regressionstests.

Es wurde deshalb keine Produktarchitektur verändert. Ergänzt wurden nur Testcode und Dokumentation. Die Tests erzeugen den Bestand zur Laufzeit, prüfen die bestehenden Policies gemeinsam und verändern keine realen Daten.

## Gefundene Bugs

Keine neue produktive Abweichung wurde bei der statischen Ist-Analyse identifiziert. Sollte der finale CI-Lauf einen Fehler zeigen, wird dieser Bericht erst nach Root-Cause-Analyse und gezielter Korrektur als abgeschlossen gewertet.

## Offene Risiken und echte Gerätetests

- ML-Kit-OCR auf einem echten, leicht schiefen und einem absichtlich schlechten Kamerabild benötigt weiterhin den Gerätetest; Robolectric validiert die Steuerlogik und den kontrollierten Fehlerpfad, nicht die native Erkennungsqualität.
- CameraX-/Scanner-Verhalten, Android-Dateiauswahl, Berechtigungsdialoge und visuelle Darstellung müssen auf mindestens einem kleinen und einem aktuellen Android-Gerät geprüft werden.
- Echte Provider-Latenz, Kontingente, Authentifizierung und Google-Drive-Konsistenz sind bewusst nicht Teil der deterministischen Unit Tests. Ein isolierter Test-Drive kann ergänzend gemäß manueller Matrix geprüft werden.
- Die synthetischen PDF-Container sind bewusst klein und deterministisch; komplexe Fremd-PDFs (verschlüsselt, beschädigt, exotische Fonts) bleiben Teil manueller/kuratierter Robustheitstests.

## Finale Einschätzung

Die automatisierbare Geschäftslogik ist durch isolierte synthetische Dateien und vorhandene Regressionstests abgedeckt. Produktionsdaten und externe Dienste bleiben unberührt. Die Freigabe dieses Testauftrags setzt einen vollständig erfolgreichen normalen Android-CI-Lauf samt Debug-APK-Artefakt voraus.

