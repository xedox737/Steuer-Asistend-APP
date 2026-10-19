# Phase 10D – Restore-Metadatenvertrag und CI-Ausführung

## Basis und Prüfstatus

Base: `e4bee4cb80869af3c3b76c7947fafe0cacd82396`, nach `git status`, Branchprüfung und erfolgreichem `git fetch origin` bestätigt.
Arbeitsbranch zunächst `test/phase10d-restore-contract-ci`, nach Befundnachweis `fix/phase10d-restore-contract-ci`. PR #139 bleibt unverändert Draft und wird nicht gemergt.
AGENTS.md, PROJECT_STATUS.md, VERIFY_CHANGES.md und CODE_MAP.md gelesen. Historische August-Anweisungen in CODEX_HANDOFF.md sind laut PROJECT_STATUS.md überholt.

Primäre zugängliche Phase-10B-Beweise: Beschreibung von PR #139 und Originalartefakt
12147050949 aus Run 39154690088 (Head `430c92eeb061036376cb1fb63614e04f6170ffee`).
Der separate Bericht Phase10B_Portfolio_E2E_Abnahme.md/.pdf ist bislang weder im Checkout noch im Artefakt enthalten; zusätzliche Quelle beim Auftraggeber angefragt.
Die zwei Portfolio- und Review-Tests werden unverändert aus dem QA-Head übernommen, ohne PR #139 zu mergen.

Lokaler Gradle-Versuch scheitert vor Projektkonfiguration an `SocketException: Operation not permitted` im FileLockContentionHandler.
Kein lokaler Testlauf wird als bestanden gezählt. Kein `/dev/kvm`; Android-Nachweis über CI.

## Q10B-01 – ursprüngliche Rohdifferenzen

Unabhängig aus den JSON-Dateien des Originalartefakts nachgezählt, für 17 und 21 Einheiten identisch:

| Feldgruppe | MERGE 1 | MERGE 2 | REPLACE_FULL | Vorläufige Bewertung |
| --- | ---: | ---: | ---: | --- |
| Receipt allocations | 70 | 70 | 70 | Consumerprüfung offen; kein pauschaler Ausschluss |
| Receipt proposals | 70 | 70 | 70 | Consumerprüfung offen; kein pauschaler Ausschluss |
| Originalreihenfolge in extractedFieldsJson | 70 | 70 | 70 | Explizite Reihenfolge betrifft Dateikette; nur beweisbar gleiche effektive Ordnung rekonstruierbar |
| ManagedDocument source | 60 | 60 | 0 | Herkunftssemantik prüfen; nicht blind als Cache einstufen |
| ManagedDocument updatedAt | 60 | 60 | 60 | B: Konfliktversion, keine technische Neudatierung zulässig |
| Namens-ID-Aliasse auf bestehende IDs | 8 | 8 | 0 | Nur bei identischer stabiler Identität und ohne abweichende Legacy-Nutzdaten rekonstruierbar |
| Gesamt | 338 | 338 | 270 | Keine Rohdifferenz entfernt |

Restore 1/2 und lokale MERGE-Wiederholungen jeweils RAW 0. Originalhashes 60/60 erhalten.
Gezielte Phase10DRestoreMetadataTest-Fälle prüfen tatsächlichen Reindex, Restore und Originaldownload sowie danach ältere/neuere Dokumentversionen.
Bis zum reproduzierten Befund keine Produktänderung. Golden-Assertions bleiben zunächst strikt rot.

## Q10B-02 – SDK/JDK-Analyse

AGP 9.1.1, Gradle 9.3.1, Robolectric 4.16.1; compileSdk 36.1, targetSdk 36.
Bytecode bleibt Java 11. Bisherige Gradle-/CI-JVM Java 17. Keine Java-Toolchain im Projekt deklariert.
Robolectric-Standard sdk=35 bleibt bestehen.

Zwei unabhängige Ursachen: SDK 36 braucht Java 21; zusätzlich schließt der globale
`robolectric.enabledSdks=35` sowohl explizite SDK-36- als auch SDK-34-Tests aus.
Im alten XML stehen alle fünf als `testcase` unter `classname="Gradle Test Run :app:testDebugUnitTest"`;
es sind keine Methoden dieser Testklassen. XML skipped=0 beweist hier keine Ausführung.

| Klasse | Deklariertes SDK | Testmethoden im Quelltext |
| --- | ---: | ---: |
| ExampleRobolectricTest | 36 | 1 |
| GreetingScreenshotTest | 36 | 1 |
| RestoreSafetyTest | 36 | 2 |
| AdvisorAnnualSummaryIntegrationTest | 34 | 11 |
| SyntheticTaxExportE2eTest | 34 | 3 |

Minimaler Weg: globale SDK-Erlaubnisliste entfernen, deklarierte SDKs unverändert lassen;
nur vor dem Unit-Test-Task Java 21 installieren/aktivieren, danach für Android-/Release-Build auf Java 17 zurückschalten.
Lint und Emulatorjob bleiben Java 17. Keine Filter, Ausschlüsse, SDK-Absenkung oder Testdeaktivierung.
Ein verpflichtender XML-Prüfer zählt echte Methoden anhand ihres vollständigen Klassennamens,
gleicht die Zahl gegen @Test im Quelltext ab und verlangt keine Fehler, Skips oder Platzhalter.
Er schreibt `app/build/reports/phase10d/test-execution.json` und schlägt bei fehlenden Tests fehl.

## Noch ausstehend

### Bestätigter Produktbefund E10D-01 (P2)

Run 39172107868, Head `0bd58bf44b61344462c3cc0ecdc89062d9809ed0`, ausschließlich Test-/CI-Änderungen:
alle fünf Phase10DRestoreMetadataTest-Methoden tatsächlich ausgeführt und rot.
Ein dokumentierter fachlicher Titel `Neu lokal` mit neuerem updatedAt wird im echten Supplemental-MERGE wieder zu `Test`.
Reiner Receipt-Sync/Reindex und Originaldownload ersetzen die gespeicherte Konfliktversion Januar 2026 durch die technische Laufzeit Oktober 2026.
Damit ist fachlicher Metadatenverlust reproduziert, nicht nur ein Rohdiff. Begrenzte Produktkorrektur für Reindex/Restore ist durch den Auftrag gedeckt.

### CI-Reproduktionsbefund

Alle fünf vormals leeren Klassen führen jetzt Methoden aus (1/1/2/11/3), keine Platzhalter oder Skips.
Der Lauf ist noch rot: SyntheticTaxExportE2eTest hat zwei nun sichtbare Fehler; außerdem kontaminieren verschiedene SDK-Nativlaufzeiten im gleichen JVM-Worker nachfolgende Grafik-/SQLite-Tests (`UnsatisfiedLinkError`).
Diese Fehler sind getrennt von Restore-Produktfehlern zu behandeln. Keine Methoden ausblenden; JVM-Isolation und bislang latente Fixturefehler prüfen.
Der vollständige Lauf ist beendet; verify rot, Emulator/Builds folgerichtig übersprungen. Kein Retry und kein Abbruch.

### Bestätigter Produktbefund E10D-02 (P2)

Kein Nachweis vollständiger semantischer Gleichheit von `""` und `"[]"`:
AccountingApprovalJson, DatevReceiptEligibility und DatevMappingService lesen beide leer;
DatevApprovalInvalidationPolicy vergleicht beide Felder jedoch als unveränderte Rohstrings.
Ein zusätzlich lokal kompilierter und tatsächlich ausgeführter Test über echte Backup-Writer/-Reader
mit gültiger bestätigter Aufteilung reproduziert den Nutzereffekt: Die JSON-Neuserialisierung verändert
die gespeicherte Repräsentation; unveränderte erneute Übernahme der bestätigten Aufteilung löscht
`historic-export-10d` und setzt Freigabe/Exportstatus zurück. Vor Korrektur: Executed=1, Failures=1, Ignored=0.
Die aktuelle Restore-/Backup-Quellen wurden dafür unabhängig von Gradle kompiliert; vorhandene
generierte Android-/Room-Artefakte und der SDK-35-Robolectric-Cache wurden nur zur Testausführung verwendet.

Folgerung: Beide Strings bleiben B, keine Golden-Kanonisierung. Receipt-Backup-v2 erhält zwei
optionale Repräsentationsfelder für bereits existierende Room-Felder; keine neuen Room-Felder,
keine Änderung der DATEV-Logik. Typisierte Arrays bleiben erhalten, Widersprüche zur Rohrepräsentation
werden beim Lesen vor Import blockiert. Alte v2-Sicherungen ohne diese optionalen Werte bleiben
lesbar; ihre ursprünglich nicht gesicherte Rohrepräsentation ist nicht rückwirkend rekonstruierbar.
Room 34, Supplemental 15 und Receipt-Backup-Versionsnummer 2 bleiben unverändert.

Die Reproduktion, Feldtabelle und der Vertragshelper sind erarbeitet; abschließende Roh-/Vertragszahlen,
alle Regressionen/Builds und vollständige Emulator-CI werden noch nachgewiesen.
Room 34, Supplemental 15 und Receipt Backup 2 unverändert. Keine Freigabe vor vollständigem Nachweis.

## Verbindlicher Feldvertrag (A/B/C/D)

A = fachlich autoritativ, exakt erhalten. B = fachlich relevante Metadaten, exakt erhalten,
außer bei einer ausdrücklich fachlichen Änderung oder einer nachgewiesen neueren Backup-Version.
C = deterministisch rekonstruierbare Technik unter den unten genannten Bedingungen.
D = ausdrücklich erlaubte semantische Repräsentationsnormalisierung. Für die beobachteten
Aufteilungsstrings ist D **nicht** zulässig; die D-Liste dieses Vertrags ist leer.
Nicht ausdrücklich als C/D aufgeführte neue Felder bleiben im Test standardmäßig autoritativ.

„Exakt“ bezieht sich bei MERGE auf die nach Konfliktregel gewinnende Version; ein neuerer lokaler
Stand wird nicht gegen einen absichtlich älteren Snapshot gleichgesetzt. Bei REPLACE_FULL gilt
vollständig der gesicherte Stand. JSON-Objekt-/Entity-Set-Sortierung im bestehenden Golden-Serializer
ist keine Wertnormalisierung; Zahlen haben keine Toleranz. Originalreihenfolge wird zusätzlich als
Dokument-ID→Rang abgebildet, damit Set-Sortierung keine geänderte Seitenfolge verdeckt.

| Field / Domain | Klasse | Exakt erhalten? | Normalisieren? | Neu erzeugen? | Konfliktnutzung | Nutzersichtbar | Exportrelevant | Consumer / Test |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Receipt.allocationsJson | B | ja, inkl. leerer Repräsentation | nein | nein | Freigabe-Invalidierung | Freigabe/Review | DATEV | AccountingApprovalJson; DatevApprovalInvalidationPolicy; DatevReceiptEligibility; DatevMappingService; Phase10DApprovalRepresentationTest |
| Receipt.bookingProposalsJson | B | ja | nein | nein | Freigabe-Invalidierung | Freigabe/Review | DATEV | dieselben Consumer; Raw-/Legacy-/Widerspruchstests |
| Receipt.freigabestatus, pruefstatus, exportStatus | A/B | ja | nein | nein | Receipt-MERGE behält lokal | ja | ja | ReceiptRestoreUpsertResolver; DatevReceiptEligibility; Phase6AApprovalTest und Portfolio |
| Receipt.exportlaufId | A | ja | nein | nein | Freigabe-Invalidierung | Audit | ja | DatevApprovalInvalidationPolicy; Phase10DApprovalRepresentationTest |
| Receipt.internalId, displayId | A | ja | nein | nein | primäre Restoreidentität | Belegnummer | GUID/Zuordnung | ReceiptRestoreUpsertResolver; Phase10BPortfolioAcceptanceTest |
| Receipt.numeric id | A (v2 gespeichert) | ja | nein | nein | ID-Kollision blockiert | Referenzen | Zuordnung | executeFullDriveRestore/upsertRestoredReceipt; Phase10C-Fälle |
| Receipt.propertyId, unitId | A | ja | nein | nein | Scope/Identität | Objekt/Einheit | ja | ReceiptUnitResolver; Phase10A/6A und Portfolio |
| Receipt.Betrag/Cent, Datum, Zeit, Kategorie, Konto, Beschreibung, Positionen, Zahlungsdaten | A | ja | nein | nein | lokaler Stand bei MERGE | ja | ja | Restore-Codec/TaxPropertyCalculator/DATEV; Portfolio und Phase9B |
| Receipt.imageUrl (lokale Pfadrepräsentation) | C | Byte-/Seitenreferenz ja; Gerätepfad nein | nur encodePaths-Vertrag | hashgeprüfter Gerätepfad | kein fachlicher Zeitstempel | Viewer | Originalbytes ja | ReceiptOriginalChain.localPaths/paths; downloadReceiptOriginals; ROOM-RAW und Originalhashprüfungen |
| Receipt.driveFileId, Originalketten-Dokument-IDs/-Relationen | A | ja | nein | nein | Datei-/Belegidentität | Viewer/Originalwahl | ja | ReceiptOriginalChain/ReceiptDocumentReference; Mehrseiten-/DATEV-Regressionen |
| Receipt.syncStatus, syncError, lastSyncedAt, driveRevision | C | Fachwerte unverändert | technischer Syncstatus | ja | keine Autorenkonfliktversion | Syncanzeige | nein | ReceiptRestoreUpsertResolver.preferredReceipt; Phase10D-Reindex-/Downloadtest |
| ManagedDocument.documentId, receiptInternalId, propertyId, unitId | A | ja | nein | nein | Schlüssel/Scope | Zuordnung | Originalzuordnung | StableDocumentIdentity; Originalmetadaten-/Portfolio-/Reviewtests |
| ManagedDocument.source | B | ja, DATEIIMPORT bleibt DATEIIMPORT | nein | nein | Teil der versionierten Entität | derzeit keine direkte Quellenanzeige | kein direkter DATEV-Consumer | ManagedDocumentService.prepareImport; ReceiptOriginalChain.parseMetadata; SupplementalDriveBackup.mergeManagedDocument; Phase10D MERGE/REPLACE |
| ManagedDocument.updatedAt | B | ja | nein | nur echte fachliche Änderung | ja, RestoreConflictPolicy | Sortierung/Reviewstand | indirekt | ReceiptRepository.indexReceiptDocument; SupplementalDriveBackup; Reindex/Download/älterer+neuerer MERGE |
| ManagedDocument.createdAt | B | ja | nein | nur neue Entität | Fallback-Originalsortierung | Dateihistorie | Originalfolge | ReceiptOriginalChain.ordered; Phase10D/ReviewedDocumentRestore |
| ManagedDocument.sha256, fileSizeBytes | A | ja | nein | Hashberechnung nur zur Bestätigung derselben Bytes | Hashprüfung, keine Zeitversion | Originalintegrität | ja | StableDocumentIdentity; downloadReceiptOriginals; 60/60 Hashes je Portfolio |
| ManagedDocument.originalFilename | B | ja | nein | nein | Teil der Version | ja | Manifest/Dateiname | ReceiptOriginalChain; SupplementalDriveBackup; fünf Formatimporte/Portfolio |
| ManagedDocument.storedFilename | B | ja | nein | nein | Teil der Version | ja | Dateikette/ZIP | DocumentFilenameGenerator nur bei fachlichem Review; ReviewedDocumentRestore |
| ManagedDocument.mimeType | B | ja | nein | nur verifizierte fehlende Altangabe ergänzen | Teil der Version | Viewer | ja | DatevOriginalAttachmentPolicy; fünf Formatimporte |
| ManagedDocument.extractedFieldsJson (fachliche Member) | B | ja, gesamte Rich-JSON-Repräsentation | nein | nein | Teil der Version | DocumentPresentation/DocumentFeature/Review | Rechnung/Review | DocumentAiReviewState; ManagedDocumentService; Phase10BReviewedDocumentRestoreTest |
| ManagedDocument.reviewStatus | B | ja | nein | nein | Teil der Version | ja | Freigabe/Review | DocumentModels.updateReviewIfPresent; Phase10B/10D |
| ManagedDocument.aiAnalysisStatus, aiConfidence, ocrStatus | B | ja | nein | nur echte Verarbeitung | Teil der Version | ja | Review | DocumentModels.updateProcessingIfPresent; bestehende Dokumenttests |
| ManagedDocument._receiptOriginalOrder | C nur bei gleicher effektiver Kette | effektive ID-/Seitenfolge exakt | keine Umordnung | deterministischer Rang | keine Autorenversion | Viewer; technisches JSON im Rohpanel | Originalauswahl/ZIP | ReceiptOriginalChain.ordered/metadataJson/parseMetadata/localPaths; Helper-Gegenprobe mit vertauschten Originalen |
| ManagedDocument.documentType, category, date, title, loanId, tenantReference, renovationReference, migrationStatus | B | ja | nein | nein | Teil der Version | ja | Review/Dateiablage | ManagedDocumentService/DocumentModels; E10D-01 Titel- und Reviewtests |
| ManagedDocument.localUri; DocumentSearchFts; ocrText | C | gleiche Originalbytes/Suchbasis | Gerätepfad/Index | ja | keine künstliche neue Version | Öffnen/Suche | Originalbytes | isUsableLocalUri/findManagedFileByHash/rebuildDocumentSearchIndex; vollständige ROOM-RAW-Dateien |
| Preferences unit ID/name bindings | C bedingt, sonst A | stabile IDs und effektive Werte ja | nur nachgewiesen redundanter Alias | ja, bestehende ID | keine neue Einheit | Zuordnung | indirekt | UnitIdentity.read/legacyAliasMatches; saveWohneinheitenToPrefs; Rename-/falscher-Alias-/Portfoliofälle |
| Preferences tenancy/history | A | ja, bestehende historische IDs/Werte | nein | nein | ID-basierter konservativer Merge | ja | Mieten | TenantHistoryStore; PersistentPreferenceInventory.mergeTenantHistory; Phase9A/10A/Portfolio |
| Preferences AfA/bestätigte Werte | A | ja | nein | nein | lokal bei MERGE ohne Version | ja | Anlage V | ConfirmedAfaValuesStore/TaxPropertyCalculator; Phase9D/Portfolio |
| Preferences tasks | A/B | IDs, Titel, Fristen/Version exakt | nein | nein | updatedAt bei bekannter Version | ja | ggf. Prüfhinweise | PropertyTaskStore; mergeTasks; RestoreMergeDataIntegrityTest |
| Preferences Mietwerte/Flächen/Status/Scopes | A | ja | nein | nein | ID vor Name | ja | Miete/AfA | PropertyUnitScopedData; UnitIdentity; Phase10A/9C |
| Bank transactionId/accountId, Beträge/Datum/Klassifikation | A/B | ja | nein | nein | Parentversion | ja | ja | SupplementalDriveBackup/BankDao; Phase10D Bank-/Trip-Konflikttest/Portfolio |
| Bank transaction.updatedAt (ebenso account/rules/assignments/recurring.updatedAt) | B | ja | nein | nein | ja, strikt neuer valider Instant | Bearbeitungsstand | indirekt | RestoreConflictPolicy; SupplementalDriveBackup.restorePayload; alter/neuer MERGE |
| Bank links / assignment IDs und Referenzen | A | ja | nein | nein | Parent-Transaktion; nicht wiederauferstehen lassen | ja | ja | mayAddRelationship, BankRent/LoanAssignment; Portfolio/Phase9A/10C |
| Bank links/assignments createdAt/status/source | B | ja | nein | nein | Entität/Parentversion | ja | Audit | Bank-Backupcode; bestehende RestoreMergeDataIntegrity-/Relationstests |
| Logbook trip IDs und bookingKey | A | ja | nein | nein | stabile Identität | ja | CSV/Fahrtbeleg | LogbookBookingStore/SupplementalDriveBackup; Phase10C/Portfolio |
| Logbook receipt refs/propertyId/standardRouteId | A | ja | nein | nein | Relationsintegrität | ja | ja | validateRemovedParentReferences und REPLACE-Trip-Nachkontrolle; alle 14 Phase10C-Fälle |
| Logbook Datum/Kilometer/Zweck/Korrektur/Historie | A | ja | nein | nein | Tripversion | ja | Fahrtkosten/CSV | LogbookBookingStore; Phase9B/10C/Portfolio |
| Logbook updatedAt/createdAt, Standardroute.updatedAt | B | ja | nein | nein | ja bei updatedAt | ja | Historie | RestoreConflictPolicy; Phase10D Bank-/Trip-Konflikttest |
| Loan IDs/Scopes/Werte; ExportAudit IDs/Belegreferenzen/Centwerte | A | ja | nein | nein | ohne Version: lokal bei MERGE | ja | ja | SupplementalDriveBackup; Phase10C/Portfolio/DATEV |
| ExportAudit timestamps/status | B | ja | nein | nein | kein künstlicher Importzeitpunkt | ja | Audit | ExportAuditDao/SupplementalDriveBackup; 100 vorbereitete Auditläufe je Portfolio |

### Consumerprüfung und erlaubte Rekonstruktion

`allocationsJson` und `bookingProposalsJson` wurden appweit gesucht. Alle direkten Leser sind
AccountingApprovalJson, DatevReceiptEligibility, DatevMappingService und DatevApprovalInvalidationPolicy;
Writer außerdem LogbookBookingStore, Receipt-Migration und der Drive-Codec. Die Decoder behandeln
leer/Array leer gleich, der Invalidierungsconsumer ausdrücklich nicht. Daher kein D-Feld und kein
Entfernen aus den Golden-Daten. Die optionalen v2-Rohwerte werden an beiden produktiven
Receipt-Backup-Writerstellen mitgeschrieben. Konfliktierende typisierte/Rohwerte werden blockiert.

`_receiptOriginalOrder` entsteht bei Mehrseitenimport/-index, beim Lesen von Originalmetadaten
und bei fehlenden Originalreferenzen beim Download. BEFORE enthält es bei den 70 Einzeloriginalen
nicht, weil die Dateikette bereits über ID/createdAt eindeutig ist. Seine Rangwirkung wird für
Viewer, lokale imageUrl-Kette, Originalmetadaten, Upload/Download und Export beibehalten. Nur ein
JSON mit genau diesem numerischen Member darf im Helper rekonstruiert werden; alle übrigen,
auch unbekannten/reviewten Member bleiben bytegenau. Falsche effektive Reihenfolge bleibt rot.

Die acht zusätzlichen Aliasse kommen aus saveWohneinheitenToPrefs für die acht Einheiten des
primären Restore-Objekts. Der strukturierte UnitIdentity-Vertrag und ID-Prefixwerte bleiben die
Autorität. Aliasse dürfen nur dann aus der Vergleichsrepräsentation rekonstruiert werden, wenn
Name und stabile ID im strukturierten Bestand übereinstimmen und vorhandene namensbasierte
Werte mit den ID-basierten Werten identisch sind. Falsche IDs, unbekannte Aliasse oder unaufgelöste
Legacy-Daten bleiben sichtbar/rot. Rename ändert die stabile ID nicht; ein historischer Alias darf
keine andere ID ersetzen. Aliaswerte werden weiter im Rohbackup gespeichert; beim nächsten
Backup bleiben sie deshalb als zusätzliche technische Werte sichtbar.

### Autorenänderung, Restore, Sync und Index

Es gibt keine neue persistierte Autoren-/Index-/Restorezeit. Bestehende Receipt-Syncfelder und
RestoreJournal bleiben technische Nachweise. ManagedDocument.updatedAt ist die fachliche Version.
Ein Receipt-Insert aktualisiert Datum/Titel/Scope und diese Version nur, wenn genau diese
Receipt-Fachwerte gegenüber dem zuvor gespeicherten Receipt geändert wurden. Pfad-, Download-,
Suchindex- und Syncänderungen behalten bestätigte Metadaten, Herkunft und Version.

Der vollständige Restore erfasst vor dem Core-Import ein flüchtiges Abbild der tatsächlich lokalen
Dokumente. Nur diese Daten sind lokale Konfliktevidenz für Supplemental-MERGE. Im Core gerade
rekonstruierte Receipt-Dokumente sind keine neue Nutzeränderung. Standalone-Supplemental-Restore
verwendet weiterhin den zu Beginn vorhandenen Room-Stand. REPLACE_FULL wählt die gesicherte
Entität. Brauchbare lokale Bytes werden weiterhin unabhängig davon nur nach passendem Hash verknüpft.

Bei Bank/Trip/Dokument/Task gilt: gültiger Backup-Instant strikt neuer → Backup darf übernehmen;
gleich, älter oder unbekannt → lokal bleibt. Receipt hat keine fachliche Änderungszeit: MERGE erhält
lokale Fachwerte; Drive-Revision/Synczeit sind kein Ersatz. Die Bankliste, Bankkonfliktregeln und
DATEV-Berechnung/Invalidierung wurden nicht geändert.

### RAW und CONTRACT

Unveränderte Phase-10B-Snapshotdateien bleiben unter BEFORE/AFTER_*.json erhalten, damit die
ursprünglichen 338/270 Diffgruppen vergleichbar bleiben. Hinzu kommen vollständige persistierte
Receipt-/ManagedDocument-ROOM-RAW-Dateien mit Gerätepfaden, Originalkettenfeldern und OCR-Text.
Für jeden Restorevergleich werden RAW-DIFF, ROOM-RAW-DIFF, CONTRACT-DIFF und comparison-summary.json
abgelegt. Contract-Diff verlangt null; CLASS-A/B-Differenzen werden nicht normalisiert.
Wiederholter Restore und lokale MERGEs verlangen zusätzlich RAW null. Der Helper enthält
explicit authoritativeFields, normalizedFields (leer) und reconstructedFields. Gegenproben
für source, Zeitstempel, Review, leere Strings, fremde IDs, unbekannte Felder und Seitenumordnung
halten den Contractvergleich absichtlich rot.

### Q10B-02: Isolation und latente Test-Fixtures

Der erste Lauf mit allen deklarierten SDKs belegt geteilte JNI-Zustände zwischen SDK 34/35/36:
Native RenderNode-/SQLite-Methoden schlagen nach SDK-Wechseln fehl. Jede Klasse bekommt nun
mit forkEvery=1 einen eigenen JVM-Worker; zwei Worker dürfen parallel arbeiten. Kein Testfilter,
kein exclude, Ignore, SDK-Suppress oder Skip. Der Unit-Task erhält für diese zusätzliche JVM-
Startarbeit 20 statt 10 Minuten; der Gesamtlauf bleibt auf 35 Minuten begrenzt. Keine Retries.

SyntheticTaxExportE2eTest enthielt eine historische Fixture mit ungescopten Receipt-/Loan-/
Tenant-Referenzen und einen anders benannten Anzeigenamen. Die Fixture wird auf dieselben
stabilen IDs und Objekt-/Einheitenscopes ausgerichtet; keine Assertion entfernt. Robolectric
4.16.1 besitzt keinen nativen PdfDocument-Writer (Handle 0). Ein reiner Test-Plattformadapter
schreibt die vom unveränderten Produktexporter gezeichneten Seiten/Texte/Positionen über PDFBox
in ein lesbares PDF. Zusätzlich zu den bisherigen ZIP-/CSV-/Originalhash-Assertions werden die
PDF-Seitenzahl und echter Textinhalt geprüft. Dies belegt den Exporter-/Paketvertrag, nicht die
native Android-PDF-Serialisierung. Der Adapter wird nicht in die App gepackt.

## Ausführungsnachweise und offene Grenzen

- CI-Reproduktion 39172107868 vollständig beendet: Lint grün, Unit-Kompilierung grün, 1088 Methoden,
  107 Fehler (Produktbefunde, SDK-JNI-Isolation und bislang latente Fixtures); keine Suite-Platzhalter.
- ExampleRobolectricTest: 1 executed, 0 failures/errors/skips; SDK 36.
- GreetingScreenshotTest: 1 executed, 0 failures/errors/skips; SDK 36.
- RestoreSafetyTest: 2 executed, 0 failures/errors/skips; SDK 36.
- AdvisorAnnualSummaryIntegrationTest: 11 executed, 0 failures/errors/skips; SDK 34.
- SyntheticTaxExportE2eTest: 3 executed, 2 failures, 0 errors/skips; SDK 34. Endgültiger grüner Nachweis ausstehend.
- Lokale Helper-Kompilierung und JUnit/Robolectric: 5/5 ausgeführt, 0 Fehler. E10D-02 vorher gezielt
  1/1 ausgeführt, Auditverweis tatsächlich verloren. Nach Korrektur 13 fokussierte Methoden
  ausgeführt, 0 Fehler, 0 ignoriert, einschließlich genau dieser Writer-/Reader-Probe.
- Vollständige Standard-Gradleprüfung bleibt lokal wegen Socketverbot unmöglich. Zusätzliche direkte
  Kotlin-Kompilierung aktueller Restore-Quellen ersetzt weder KSP/AGP noch Lint/Build/Emulator-CI.
- Vollständiger Korrektur-CI-Lauf inklusive aller Phase9A–9D/10A/14 Phase10C-Fälle, Debug-/androidTest-
  APK und Emulator wird vor Abschluss abgewartet. Abgebrochene/übersprungene Jobs zählen nicht als Erfolg.
- Keine Live-Drive-, Zweitgeräte-, Kanzleiimport- oder vollständige Android-Portfoliofreigabe behauptet.
  Der separate Phase-10B-Bericht ist weiterhin nicht verfügbar; Originalsnapshots und XML sind gesichert.
- Produktänderungen ja, ausschließlich E10D-01/E10D-02. Neue Befunde: 0 P0, 0 P1, 2 P2, 0 P3.
- Room-Version 34, Supplemental-Version 15, Receipt-Backup-Version 2; v2-JSON additiv um zwei notwendige,
  optionale Repräsentationsfelder ergänzt. Alte Sicherungen ohne diese Werte können verlorene Rohbytes
  nicht rückwirkend liefern. Für neue Backups ist die ursprüngliche Repräsentation verbindlich exakt.
- PR #139 bleibt Draft. Der reine Diagnosestand #141 wird durch den eigenen Fix-PR ersetzt; NICHT MERGEN.
