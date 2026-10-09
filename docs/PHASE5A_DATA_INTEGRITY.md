# Phase 5A – Datenintegrität

Grundlage: Martin-Weber-Produkttest vom 08.10.2026 (F01–F05).
Ausgangs-main nach checkout/fetch/ff-only pull: `f2e070fe3bc8afe424633fb148044701787b8353`.
Branch: `fix/phase5a-data-integrity`. Ausgangsstatus sauber. Kein Merge.
Relevante Vorarbeiten: #106–108 (IDs/Miethistorie/Restore), #110–111 (Bankmiet-Dedup),
#114–115 (AfA, sichere Preference-Lesung). F06–F17 sind ausdrücklich außerhalb des Auftrags.

## Ursachenanalyse vor Produktivänderungen

### F01

`PersistentPreferenceInventory.restorePayload` schreibt fast alle bestehenden Schlüssel zurück.
Nur TenantHistory (Perioden-ID, rentChanges nach Wirksamkeitsdatum), Aufgaben (ID/updatedAt)
und bestätigte AfA-Werte (lokal gewinnt) besitzen besondere Regeln.
`SupplementalDriveBackup.restorePayload` verwendet ungeprüfte REPLACE-Upserts für Properties,
Darlehen, Fahrten, Standardrouten, Bankkonten/-transaktionen/-zuordnungen/-regeln.
ManagedDocument ist bereits über updatedAt konfliktgeschützt und erhält brauchbare lokale Dateien.
Der Core-Restore ersetzt Belege über den identitätsgeschützten, aber nicht konfliktgeschützten
`ReceiptRestoreUpsertResolver`. Core-Preference-Import schreibt außerdem alte Einheitenwerte,
DATEV-Profil und gelernte Regeln, bevor Supplemental-Restore läuft. Eine leere Belegliste schaltet
ungeachtet vorhandener anderer Daten auf REPLACE_EMPTY um.

Unit-State (`wohneinheiten_prefs`), NK (`rent_plan_prefs`), Statusmetadaten und TenantHistory
sind separate Quellen. Ein MERGE kann den Namen/die Kaltmiete auf Alt/760 zurücksetzen, obwohl
TenantHistory Neu/850 behält. Die zentrale Unit-Lesung berücksichtigt den Verlauf bisher nicht.
Der einfache Einheiten-/Mietereditor schreibt ausschließlich den Unit-State. Seine ausdrücklichen
Korrekturen müssen deshalb bei bestehendem Vertrag auch dessen identische Periode bzw. aktuell
wirksame Mietänderung korrigieren; zukünftige Änderungen und Nebenbeträge bleiben erhalten.
Receipt, PropertyMetadata und Loan haben lokal kein updatedAt/Versionsfeld.

### F02/F03

`AddReceiptScreen.selectedFiles` hält Picker-URI und Vorschau-Bitmaps im Compose-State.
Der Picker kopiert das Original nicht. Erst `analyzeReceipt` speichert Bilder (max. 1600 px,
JPEG Qualität 82); nur `ScanUiState.Success.localImagePaths` befüllt den an `saveReceipt`
übergebenen Pfad. Ohne KI-Erfolg bleibt er leer. PDF/PNG/WEBP-Originalbytes gehen so verloren.
Mehrere Analysebilder werden kommagetrennt in `Receipt.imageUrl` gespeichert.
`DatevOriginalAttachmentPolicy.resolve` sucht genau eine erste lesbare Datei; Builder und Manifest
verarbeiten genau diese. Auch `ReceiptRepository.indexReceiptDocument` und Receipt-Drive-Sync
indizieren/übertragen nur den ersten Pfad; Standalone-Drive-Sync schließt Belegdokumente aus.
Vorhandene ManagedDocument-Felder tragen bereits ID, Receipt-ID, Originalname, MIME, SHA,
Größe, lokalen/Drive-Pfad und Metadaten-JSON: keine neue Room-Tabelle erforderlich.
Automatische Einzelbelegsicherung benötigt dieselbe Kette direkt in Receipt-JSON:
Der bisherige documents-Eintrag enthält Drive-ID/MIME/Größe, aber keinen SHA,
Originalnamen oder Reihenfolge. Ein vollständiger Supplemental-Snapshot darf keine
Voraussetzung für vollständige neue Belegmetadaten sein.
Die additive Originalmetadatenkette erhält auch die stabile Einheitenreferenz. Der Core-Import
vergleicht mit Dokumentmetadaten vor Beginn seiner Transaktion, damit neu erzeugte Indexzeilen
keine künstliche lokale Aktualität gegenüber dem Backup vortäuschen.

### F04/F05

`normalizeDate` übernimmt ISO allein nach Regex und nutzt für deutsche/Slash-Daten den
SMART-Resolver mit yyyy: 30.02.2026 wird normalisiert. CAMT liest Datumstext ohne Kalenderprüfung.
`parseAmount` akzeptiert `toDoubleOrNull` ohne Finite-/Syntaxprüfung. DATEV-Vorprüfung prüft
Datum nur per Regex und Betrag nur auf <= 0 (NaN passiert den Vergleich).
CSV-Zeilenfehler werden nur gezählt, nicht mit Feld/Originalwert ausgewiesen.
`accountId(source, iban, fallbackName)` hasht auch fallbackName; der Import übergibt den Dateinamen.
`transactionId` hasht diese Konto-ID plus Bankmerkmale; dadurch erzeugt Umbenennen neue IDs.
`occurrence` unterscheidet ausschließlich ansonsten identische Buchungen, nicht allgemeine
Listenpositionen. Die bestehende Identitätsfunktion wird weiterverwendet.

## Restore-Inventar und beabsichtigte Konfliktregeln

MERGE: Lokal bleibt, es sei denn beide Änderungszeitpunkte sind gültig und Backup ist strikt neuer.
Ohne Zeitinformation nur fehlende Einträge/Informationen ergänzen; additive Sammlungen verwenden
ihre bestehende Identität. Gleichstand/unbekannte Zeit bedeutet lokal gewinnt. Keine Namens-/Betragsheuristik.

| Bereich | Identität | Lokales updatedAt | MERGE | REPLACE_FULL |
| --- | --- | --- | --- | --- |
| PropertyMetadata einschließlich Kauf/AfA-Plan | propertyId; Room-id erhalten | nein | lokaler Datensatz gewinnt; fehlendes Bild ergänzbar | Backup upsert mit stabiler propertyId/Room-id |
| Wohneinheiten/aktueller State | propertyId + unitId + Feld | nein | vorhandene Felder behalten; aktuellen Vertrag aus erhaltenem Verlauf lesen | Snapshotfelder wiederherstellen |
| TenantHistory | propertyId + unitId + period.id | nein | lokale Periode gewinnt; fehlende Perioden ergänzen | Snapshotliste |
| rentChanges | period.id + effectiveDate | nein | lokale Änderung gewinnt; fehlende Termine ergänzen | Snapshotliste |
| Aufgaben | task.id | ja (Instant) | strikt neuer gewinnt; additive IDs | Snapshotliste |
| BankAccounts | accountId | ja (Instant) | strikt neuer gewinnt | Backup upsert |
| BankTransactions | transactionId | ja (Instant) | strikt neuer gewinnt; Zuordnungen desselben Transaktionsstands konsistent schützen | Backup upsert |
| BankRentAssignments | assignmentId | ja (Instant) | strikt neuer gewinnt; alte gelöste Beziehungen nicht bei neuer lokaler Transaktion reaktivieren | Backup upsert |
| BankReceiptLinks | linkId | nur createdAt | lokal gewinnt; fehlende Links nur bei kompatiblem Transaktionsstand | Backup upsert; Receipt-ID über internalId auflösen |
| Belege | internalId, Legacy driveFileId; Room-id erhalten | nein (driveRevision ist nur Sync-Version) | lokal gewinnt; fehlende Originalreferenzen ergänzen | Core-Tabellen ersetzen und Snapshot importieren |
| Dokumentmetadaten/Originalanhänge | documentId + receiptInternalId | ja (Instant) | strikt neuer gewinnt; usable local copy behalten | dokumentierte Dokumentersetzung, lokale Bytes wiederverwendbar |
| Darlehen | loan.id | nein | lokaler Datensatz gewinnt | Backup upsert |
| AfA bestätigt | propertyId | nein | lokal gewinnt | Snapshotwerte |
| Fahrten inkl. Korrektur/Storno | trip.id; bookingKey zusätzlich vorhanden | ja (Instant) | strikt neuer gewinnt | Backup upsert |
| Standardrouten | route.id | ja (Instant) | strikt neuer gewinnt | Backup upsert |
| Objektbilder | propertyId; Dateiinhalt-SHA | nein | lokales Bild behalten; fehlendes Bild ergänzen | Backupbild |
| BankLoanAssignments | assignmentId; unique transactionId | ja (Instant) | strikt neuer gewinnt; Beziehungskonflikt schützen | Backup upsert |
| BankRecurringPatterns | patternId | ja (Instant) | strikt neuer gewinnt | Backup upsert |
| BankLearningRules | ruleId | ja (Instant) | strikt neuer gewinnt | Backup upsert |
| BankRuleEvidence | evidenceId | nur createdAt | additiv, IGNORE bei vorhandener ID | bisheriges additives Importverhalten |
| ExportAuditRuns | exportlaufId | nur Erstellungstimestamp | lokaler Eintrag gewinnt; fehlende IDs ergänzen | Backup upsert (Core leert vorher) |
| rent_plan, unit_status_meta, unit_rental_detail | fachlich gescopter Preference-Key | nein | vorhandener Key bleibt | erlaubte Snapshotkeys ersetzen |
| loan_interest, annual_tax, Banknotizen | vorhandener Preference-Key | nein | vorhandener Key bleibt | erlaubte Snapshotkeys ersetzen |
| DATEV-Profil, KI-Regeln/-Auswahl, Appauswahl, Fahrtenentwürfe | vorhandener Preference-Key | keine einheitliche Version | vorhandener Key bleibt; fehlende Keys ergänzen | erlaubte Snapshotkeys ersetzen |
| Secrets/lokale Journale/Caches | Inventory-Whitelist | nicht relevant | nie importieren | nie importieren |

REPLACE_FULL bleibt ausdrücklich unterschiedlich: Core löscht seine Restore-Tabellen;
vorhandene Supplemental-Semantik (Snapshot-Upsert; nur ManagedDocuments vollständig ersetzen)
wird nicht zu MERGE umgedeutet. Fehlende optionale Altbackup-Bereiche dürfen keine lokalen Daten löschen.

## Validierung

Neue Regressionen: Alt/760 → lokal Neu/850; Historie/Tasks additiv; gleiche ID älter/neuer;
lokal/Backup-only; MERGE wiederholt; Full Restore; Bankkorrektur/Entknüpfung; Fahrtenstorno;
PDF/PNG/WEBP/JPG ohne KI, Reload, alle Originalbytes; mehrseitiges ZIP/Manifest/Hashes;
Backupreferenzen; strenge Datum-/Betragsfehler; 60-Zeilen-Reimport und umbenannte Datei;
Legacy-Konto-ID und Monatsfortsetzung.

Lokale Umgebung besitzt JDK 17, aber keinen Gradle/Android-SDK. Vollständige Android-Prüfung
erfolgt über die unveränderte GitHub Android CI im PR-Kontext. Ergebnisse werden im PR protokolliert.
Echte Kamera, externe Dateiöffner, angemeldetes Drive und Kanzleiimport bleiben gesonderte Prüfgrenzen.

### Regressionen und Kompatibilität

- `Phase5AUnitStateTest`: reales ViewModel-Lesen von Alt/760 → Neu/850, NK/Status/Mietbeginn aus gültiger Historie, mehrfacher MERGE, expliziter REPLACE_FULL.
- `RestoreMergeDataIntegrityTest`: additive Perioden/rentChanges/Aufgaben, updatedAt-Konflikte und Altbackup-Dokumentreferenzen.
- `Phase5ARestoreConflictTest`: Bankkorrektur, entfernte Beleg-/Mietbeziehungen, Fahrtenstorno, Backup-only/local-only, unbestimmte Version, Beleg-/Metadaten-Idempotenz.
- `ReceiptOriginalPersistenceTest`: echtes saveReceipt ohne KI, geschlossene/erneut geöffnete Room-Datenbank, PDF-Öffnung, PNG/WEBP/JPG-Bytes, zwei Anhänge, Fehlerblockade und wiederholter Supplemental-Restore.
- `Phase5AMultipleOriginalExportTest`: tatsächliches Advisor-ZIP, zwei einzelne Manifestobjekte, IDs/Namen/MIME/Größe/Hashes/Reihenfolge; fehlende zweite Datei und falscher Hash blockieren.
- `DrivePersistenceManagedDocumentE2eTest`: bestehender Fake-Drive-Gateway prüft Attachment-Upload, Reimport ohne neue Drive-Datei, Backup und vollständigen Download aller Seiten.
- `Phase5ABankImportTest`: strenge Formate, Kalender-/Betragsfehler mit Originalzeilen, 60/60/60 → weiterhin 60, Folgemonat +60, Legacy-Konto-/Transaktions-IDs, explizite Auswahl ohne eigene Kennung.
- `Phase5ABankImportWorkflowTest`: realer ViewModel-Import ohne Kennung bleibt bis Kontowahl schreibfrei; Doppelbestätigung, umbenannte Datei, detaillierte Fehlermeldungen und Abbruch.
- `Phase5ADatevValidationTest`: unmögliche Kalenderdaten und nicht endliche numerische Werte blockieren die vorhandene Vorprüfung.

Zwei bisherige Property-Tests und ein Banktest erwarteten ausdrücklich das fehlerhafte Überschreiben
bei MERGE. Sie prüfen jetzt zusätzlich den erhaltenen lokalen Zustand und verlangen bei REPLACE_FULL
weiterhin sämtliche ursprünglichen Snapshotwerte. Ein Bank-Roundtrip-Test verwendet gültige
Änderungszeitpunkte, damit der nachweisbar neuere Backupstand importiert werden darf.
Keine Tests werden deaktiviert oder Kontrollen entfernt. Der PDF-Test verwendet eine vollständige
PDF-Datei mit korrektem xref, weil Robolectric keinen nativen PdfDocument-Schreiber bereitstellt.
Import und Room-Neustart werden über Produktiv-APIs geprüft; PDFBox (nur testImplementation)
öffnet die gespeicherte Datei und prüft ihre Seite/Abmessung ohne Mock. SDK-35-PdfRenderer ruft
in dieser JVM ein nicht verfügbares Android-FileDescriptor.getOwnerId$ auf; sein echter Geräteeinsatz
bleibt eine ausdrücklich dokumentierte Prüfgrenze. Die APK erhält keine PDFBox-Abhängigkeit.

Room bleibt Version 34, Supplemental-JSON Version 14. Keine Schemaänderung, Migration oder
destructive Migration. Neue Anhänge verwenden die vorhandene ManagedDocument-ID und
receiptInternalId; Reihenfolge wird additiv im vorhandenen Metadaten-JSON gespeichert.
Receipt-JSON sichert alle Originalmetadaten additiv auch bei automatischen Einzelbackups,
ohne lokale Pfade oder OCR-Volltext zu exportieren. Altbackups ohne diese Reihenfolge oder
Anhangsmetadaten bleiben lesbar. Vorhandene Konto-/
Transaktions-IDs und ihre Beziehungen werden nicht umgeschrieben. Mehrere bereits vorhandene
Konten mit gleicher Kennung werden bewusst nicht automatisch konsolidiert; Import verlangt Auswahl.
Ohne eigene Kontokennung ist eine ausdrückliche Kontowahl/-anlage erforderlich. Bereits verlorene
Originalbytes aus historischen Erfassungen lassen sich durch diese Änderung nicht rekonstruieren.
