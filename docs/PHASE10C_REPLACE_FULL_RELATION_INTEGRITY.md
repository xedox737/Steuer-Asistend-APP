# Phase 10C – REPLACE_FULL Relation Integrity

## Stand und Auftrag

- Tatsächliche Basis: `66b617c54f11fb6ae1e468fbf05149fa1eb6ef05` (main per GitHub-Connector frisch geprüft, auch nach Unterbrechungen).
- Branch: `fix/phase10c-replace-full-relation-integrity`.
- Startbranch `work`, Arbeitsbaum vor Beginn sauber. `git fetch origin` versucht: Proxy `:8080` nicht erreichbar. Lokaler HEAD entspricht dem frisch bestätigten main; der veraltete lokale origin/main-Ref ist ausdrücklich keine Grundlage.
- AGENTS.md und docs/VERIFY_CHANGES.md gelesen. Einziger offener PR bei Beginn: #139, Draft, ungemergt. Keine Abhängigkeit von dessen Merge.
- Ziel: Snapshotdomänen bei REPLACE_FULL gemeinsam zurücksetzen; MERGE behält seine Konfliktpolitik. Keine Relation auf andere IDs umdeuten.

## E10B-01: Reproduktion und Ursache

Der fokussierte Test übernimmt aus #139 den realen Ablauf über `LogbookBookingStore`, `FullRestoreCoordinator`, `DrivePersistenceRepository.executeFullDriveRestore` und `SupplementalDriveBackup.restorePayload`. Nur der Drive-Transport ist fake; weder Receipt-Import noch Fahrtbuchung werden nachgebaut.

60 Quellbelege + zehn tatsächlich gebuchte Fahrten ergeben im reproduzierten Portfolio zehn Fahrten und 70 Belege (davon zehn Fahrtkostenbelege). Danach erzeugt die manuelle lokale Buchung Fahrt 11 mit Fahrtkostenbeleg 71. Zwei MERGEs müssen beides behalten. REPLACE_FULL muss beide entfernen. Ein weiterer lokaler Quellbeleg und eine Fahrt mit `sourceReceiptId` und lokaler Standardroute prüfen die zweite Receipt-Relation. Nach jedem Restore werden alle nichtleeren Trip-Receipt-Referenzen geprüft; zwei REPLACE_FULL-Läufe müssen exakt die Snapshot-Fahrten und dieselben numerischen/stabilen Receipt-IDs ergeben.

Vorher löschte `ReceiptRepository.clearCoreRestoreRelevantTables()` Belege, Receipt-Projektionen, Belegdokumentreferenzen und Exportauditläufe. Der Core-Import spielte nur die Snapshotbelege mit bewahrten numerischen IDs ein. Receipt 71 fehlte anschließend korrekt. Supplemental upsertete dagegen Fahrten, ohne den lokalen Restbestand zu entfernen. Fahrt 11 blieb mit `expenseReceiptId=71` stehen. Das ist ein unterschiedlicher Replace-Vertrag der beiden Seiten, kein ID-Mappingproblem.

MERGE war für diesen Fall korrekt: Core löscht dabei keine Belege; `RestoreConflictPolicy.shouldImport` erhält vorhandene bzw. neuere lokale Datensätze. Die Banklogik `mayAddRelationship` verhindert außerdem das Wiederbeleben inzwischen entfernter Links neuerer lokaler Buchungen.

## Restore-Reihenfolge und Transaktionen

1. Coordinator: Supplemental laden, Schema/Datentypen/Preferences vorab validieren.
2. Core: Snapshot laden und prüfen; ID-Konflikte vor Mutation blockieren.
3. Core-Room-Transaktion: bei REPLACE_FULL Coretabellen leeren, Stammdaten und Receipts importieren, Beleganzahl kontrollieren. Numerische Receipt-ID und `internalId` bleiben erhalten.
4. Core schreibt seine Preferences und meldet Erfolg/Fehler.
5. Nur bei erfolgreichem Core: Supplemental-Room-Transaktion. Neu: für vorhandene Snapshotabschnitte abhängige Datensätze vor ihren Parents entfernen, danach bestehende Importlogik verwenden. Dokumentindex neu aufbauen und Originalkette abgleichen.
6. Neu: nur tatsächlich durch diesen Restore entfernte Parent-IDs (Property, Loan, Document, Bankkonto, Bankbuchung) erfassen und verbliebene aktive Referenzen darauf blockieren. Dadurch wird auch ein altes Teilbackup mit `loans: []`, aber fehlendem Dokumentabschnitt und lokalem Darlehensdokument zurückgerollt. Unabhängige historische Alt-IDs werden nicht zu neuen Pflicht-FKs. Zusätzlich vor Supplemental-Commit nichtleere `expenseReceiptId` und `sourceReceiptId` gegen den assemblierten Belegbestand einschließlich Soft-Deletes prüfen. Bei Fehler keine ID-Reparatur, sondern Exception/Rollback.
7. Erst nach Supplemental-Commit: inventarisierte Preferences restaurieren. Coordinator meldet Teilerfolg ausdrücklich als Fehler und nie als vollständigen Erfolg.

**Grenze:** Core und Supplemental sind weiterhin getrennte Transaktionen. Zwischen ihnen kann ein vorübergehend inkonsistenter Zustand sichtbar sein; ein Supplemental-Fehler rollt den vorher erfolgreich committed Core nicht zurück. Auch Preferences und lokale Dateien sind keine Room-Ressourcen. Eine gemeinsame Netzwerk-/Room-/Preferences-Transaktion wäre keine kleine sichere Korrektur dieser Architektur und wird hier nicht behauptet. Endintegrität nach erfolgreichem aktuellem Vollsnapshot-Restore und Supplemental-Rollback bei Insertfehlern werden getestet. Keine Behauptung einer prozessübergreifenden Alles-oder-nichts-Garantie.

## Korrektur

`clearSnapshotDomains` wird ausschließlich bei `restoreMode == REPLACE_FULL` innerhalb der vorhandenen Supplemental-Transaktion aufgerufen. Jeder Abschnitt wird nur bei `root.has(key)` ersetzt. Vorherige Validierung unterscheidet explizit `[]`, fehlenden Abschnitt und kaputte/null Arrays. Fehlende Legacy-Domänen sind keine leeren Snapshots.

Vorhandene DAO-Löschfunktionen werden verwendet. Nur für Bankkonten und Bankbuchungen fehlten äquivalente APIs; zwei `DELETE FROM`-DAO-Methoden wurden ergänzt. Keine neue Restoreengine, kein Schemawechsel, keine Migration. Dokumentersatz wird vom Restoremodus gesteuert, nicht von einem unabhängig widersprüchlich setzbaren Altflag. Explizites MERGE darf auch mit `replaceManagedDocuments=true` nichts löschen. Eine gezielte Prüfung der tatsächlich entfernten Parent-IDs verhindert neu verursachte Brüche zu absichtlich erhaltenen Legacy-Child-Abschnitten; sie repariert keine IDs und rollt bei Konflikt die Supplemental-Transaktion zurück.

Die betroffenen Room-Entities definieren keine gegenseitigen Room-FKs/Cascades. Die Löschung erfolgt trotzdem von Kindern zu Parents: Links/Assignments/Evidenz/Fahrten/Dokumente, danach Muster/Regeln, Buchungen/Konten, Routen/Darlehen/Objekte/Audit. Beim Import bleibt der bestehende Pfad erhalten. Schema-SQL, Trigger und Indizes werden nicht geändert. Der Fehler-Injektionstest erzeugt nur in seiner Testdatenbank einen temporären Insert-Abbruchtrigger und entfernt ihn wieder.

## Snapshot-/Restore-Matrix

S = Supplemental schema 15; C = Receipt/Core-Drive-Metadaten schema 2; P = inventarisierte Preferences in S. Bei REPLACE_FULL beziehen sich Delete/Replace ausschließlich auf **vorhandene** Abschnitte. S enthält Metadaten, keine externen Originalbytes.

| Datenart | Quelle | Primary Key | Referenzen / Scope | MERGE | REPLACE_FULL vorher → jetzt | Delete / Upsert vorher | Risiko vorher |
|---|---|---|---|---|---|---|---|
| receipts + Projektionen | C | numerische id + internalId | Property, Unit, Exportlauf, Originale | sichere stabile ID-Zuordnung; lokale Daten bleiben | Core exakt → unverändert | Delete + Import | Gegenseite blieb lokal |
| properties | C aktive Immobilie + S Portfolio | id; fachlich propertyId | Units, afaShorterDocumentId | lokale Metadaten bleiben | Upsert → Snapshotersetzen | Upsert-only | lokale AfA-Referenz auf ersetzte Dokumente |
| loans | S | id | propertyId | lokal erhalten | Upsert → Snapshotersetzen | Upsert-only | unsynchroner Portfoliozustand |
| logbookTrips | S | id | source/expenseReceiptId, propertyId, standardRouteId | neue lokale Fahrten bleiben | Upsert → Snapshotersetzen + Receiptprüfung | Upsert-only | E10B-01, source analog |
| standardRoutes | S | id | Trip-Provenienz | neuere lokal erhalten | Upsert → Snapshotersetzen | Upsert-only | lokale Routen gehören nicht zum Snapshot |
| exportAuditRuns | S (Core löscht sie) | exportlaufId | Beleg-IDs als Historie | lokal erhalten | Upsert → Snapshotersetzen | Core delete; S upsert | direkter S-Aufruf behielt lokale Läufe |
| managedDocuments | S + C Receipt-Originalmetadaten | documentId | Receipt internalId, Property, Unit, Loan, Renovation | neuere Metadaten/lokale Originale erhalten | nur Altflag → expliziter Modus | conditional delete + upsert | explizites REPLACE ohne Flag behielt Receipt-Dokument |
| bankAccounts | S | accountId | Konto-Scope | neuere lokal erhalten | Upsert → Snapshotersetzen | Upsert-only | lokaler Restbestand |
| bankTransactions | S | transactionId | Konto, Property, Unit, Transferbuchung | neuere Klassifikation erhalten | Upsert → Snapshotersetzen | Upsert-only | lokale Buchung auf zurückgesetzte Einheit |
| bankReceiptLinks | S | linkId | Transaction + Receipt id/internalId | keine entfernten Links neuerer Buchungen wiederbeleben | Upsert → Snapshotersetzen | Upsert-only | lokaler Link auf gelöschten Receipt |
| bankLearningRules | S | ruleId | Konto/Property/Unit-Scope | neuere lokal erhalten | Upsert → Snapshotersetzen | Upsert-only | lokaler Unit-Scope nach Unitersatz |
| bankRuleEvidence | S | evidenceId | Transaction, Receipt, optionale ruleId | bestehende Evidenz erhalten | IGNORE-Upsert → Snapshotersetzen | Insert-ignore | Receipt entfernt; auch vorhandene ID blieb alt |
| bankRentAssignments | S | assignmentId | Transaction, Property, Unit, optional Receipt | lokale/neuere Splits erhalten | Upsert → Snapshotersetzen | Upsert-only | Receipt/Unit entfernt |
| bankLoanAssignments | S | assignmentId; unique transactionId | Transaction, Loan, Property | lokale/neuere Zuordnung erhalten | Upsert → Snapshotersetzen | Upsert-only | abhängiger Restbestand bei Parentersatz |
| bankRecurringPatterns | S | patternId | Konto/Property-Scope | neuere lokal erhalten | Upsert → Snapshotersetzen | Upsert-only | abhängiger Restbestand bei Parentersatz |
| Preferences-Stores | P | Store + fachlicher Schlüssel | Units, Historie, Aufgaben, Sanierung, AfA usw. | lokale Keys; Historie/Tasks strukturiert zusammenführen | vorhandene exportierbare Keys löschen + Snapshot → unverändert | Replace vorhanden | kein gleicher Clearingfehler bei vorhandenem Abschnitt |
| lokale Original-/Bilddateien | Gerät; ggf. Hashrekonstruktion | Pfad/Hash | Document/Property-Metadaten | verwertbare Bytes behalten | Metadaten ersetzen; Bytes erhalten | keine pauschale Dateilöschung | physischer Restbestand ist keine aktive Relation |
| externe Drive-Dateien | Drive | Drive fileId | Original-/Metadatenreferenz | lesen | lesen | keine Remote-Löschung | keine Live-Drive-Abnahme |

Preferences-Inventar: rent_plan_prefs, tenant_history_prefs, loan_interest_assignments, annual_tax_approval_prefs, afa_confirmed_values_prefs, wohneinheiten_prefs, datev_kanzleiprofil_prefs (active_profile_json), property_tasks_prefs, RenovationReviewStore, unit_status_meta_prefs, unit_rental_detail_prefs, bank_transaction_notes, ki_learned_rules_prefs, logbook_drafts, ausgewählte ai_provider_settings und google_drive_prefs. Secrets, Cache, Installations-/Restorejournale sind ausdrücklich keine ersetzbaren Snapshotdomänen.

## Appweite Integritätsmatrix

Die Tests sind ausführbare konkrete Reproduktionen; Laufstatus separat unten. Statische Befunde sind nicht als bestandene Vorher-/Nachher-Läufe zu lesen.

| Relation | MERGE | REPLACE_FULL | Risiko vorher | Fix | Test |
|---|---|---|---|---|---|
| Trip → expense Receipt | erhalten | beide Snapshotbestand | bestätigt E10B-01 aus #139 | Trip-Replace, Prüfung | Phase10CReplaceFullRelationIntegrityTest |
| Trip → source Receipt | erhalten | beide Snapshotbestand | gleicher Upsert-Pfad | Trip-Replace, Prüfung | dieselbe Klasse, lokale Quelle + korrupte Quelle |
| Trip → StandardRoute | erhalten | beide Snapshotbestand | Restbestand, kein zusätzlicher Pflicht-FK | beide Snapshotabschnitte ersetzen | lokale Route/Fahrt nach Backup |
| Trip → Property | erhalten | beide Snapshotbestand | Portfolio-Teilbestand | beide Snapshotabschnitte ersetzen | Tripintegrität + Portfolio |
| BankReceiptLink → Receipt | erhalten | Snapshotbestand | lokaler Link bleibt bei Corelöschung | Link-Replace | Phase10CBankRelationRestoreTest.localBankReceiptLinkCannotOutliveReceiptOrTransaction |
| BankReceiptLink → Transaction | erhalten | Snapshotbestand | Parent und Child waren beide upsert-only | abhängige Domänen gemeinsam ersetzen | dieselbe Methode |
| RentAssignment → Transaction/Property/Unit/Receipt | erhalten | Snapshotbestand | optionaler Receipt + Unit werden zurückgesetzt | Assignment-Replace | localRentReceiptAssignmentCannotOutliveReceiptOrSnapshotUnit |
| LoanAssignment → Transaction/Loan | erhalten | Snapshotbestand | Parents zuvor ebenfalls erhalten; kein isolierter früherer Transaction-Delete-Bug | gemeinsamer Snapshotersatz | bankSnapshotReplacesAllLocalDomainsAndMergeKeepsClassificationAndRemovedLink |
| ManagedDocument → Receipt | erhalten | Snapshotbestand | Altflag konnte Replace unterlassen | Modus steuert Replace | Phase10CPortfolioRelationRestoreTest |
| ManagedDocument → Property | erhalten | Snapshotbestand | Parents zuvor upsert-only | gemeinsamer Snapshotersatz | Portfolio-Test |
| ManagedDocument → Unit | erhalten | Snapshotbestand | Units in Preferences schon ersetzt | Dokumentbestand mit ersetzen | Portfolio-/Preferences-Vertrag |
| ManagedDocument → Loan | erhalten | Snapshotbestand | Parents zuvor ebenfalls erhalten | abhängige Domänen gemeinsam ersetzen | Portfolio-Test mit lokalem Darlehen/Dokument und Teilbackup-Rollback |
| ManagedDocument → Renovation | erhalten | Snapshotbestand | renovationReference ist optionale Referenz/Alttext | keine Umdeutung; Stores ersetzen | vorhandene Sanierungs-/Preference-Tests |
| RenovationRelation → Receipt | erhalten | P ersetzt Relation/Measures gemeinsam | kein gleicher Clearingfehler bei vorhandenem P | keine Produktänderung | preferenceRelationsAlreadyReplaceRenovationTenancyTasksAndUnitDataTogether |
| Property/AfA → Document | erhalten | Snapshotbestand | lokales Property blieb, lokales Dokument konnte entfernt werden | Property-Replace | productionReplaceRemovesLocalPropertyAfaReferenceWithDocument |
| Receipt → Property/Unit/ExportRun | erhalten | C + S + P Snapshotbestand | kein weiterer eigenständiger Clearingfehler | bestehende ID-/Snapshotlogik, keine DATEV-Änderung | Phase10A + Phase9A Regressionen |
| Tenant/History/Tasks → Property/Unit | erhalten | P ersetzt Keys + Portfolio | P hatte bereits Replace-Vertrag | keine Storeänderung | bestehende Preference-/10A-Tests + Portfolio-Test |

## Ausgeführter Vorher-Nachweis zusätzlicher Befunde

Der diagnostische Zwischencommit `ee37bd7bbaf8d1e851b578388d6984e974f38176` hat gegenüber main ausschließlich die vier Testdateien und diesen Bericht geändert; beide Produktdateien entsprechen bytegleich main (GitHub-Compare geprüft). Android CI **#1307 / 39062657398**, vollständig abgeschlossen: Lint erfolgreich, Unit-Testjob rot, APK/Emulator wegen Verify-Fehlschlag übersprungen. **1058 Tests ausgeführt, zehn fehlgeschlagen.** Kein Infrastrukturfehler und kein abgebrochener Lauf. Die finalen Testdateien und Assertions entsprechen diesem Vorher-Nachweis.

| Befund | Tatsächlicher Zustand auf unverändertem main | Regression |
|---|---|---|
| E10B-01 | reale Buchung: nach REPLACE_FULL `orphan expenseReceiptId` | realBookingEmptyTargetDoubleMergeAndDoubleReplaceKeepExactTripReceiptSnapshot |
| E10C-02 | Receipt 71 entfernt; BankReceiptLink `local-link`, receiptId=71 bleibt | localBankReceiptLinkCannotOutliveReceiptOrTransaction |
| E10C-03 | lokales RentAssignment mit receiptId=71 bleibt nach Core-Receiptlöschung | localRentReceiptAssignmentCannotOutliveReceiptOrSnapshotUnit |
| E10C-04 | lokale BankRuleEvidence mit receiptId=71 bleibt nach Core-Receiptlöschung | localRuleEvidenceCannotOutliveReceipt |
| E10C-05 | Property/Preferences auf alte Unitliste gesetzt; lokale BankTransaction mit local-unit bleibt: `orphan bank unitId` | bankSnapshotReplacesAllLocalDomainsAndMergeKeepsClassificationAndRemovedLink |
| E10C-06 | mit echtem Produktionsflag: Dokument entfernt, lokales Property mit `afaShorterDocumentId=local-doc` bleibt | productionReplaceRemovesLocalPropertyAfaReferenceWithDocument |
| E10C-07 | explizites REPLACE_FULL ohne Altflag: Receipt 71 entfernt, ManagedDocument mit receiptInternalId=receipt-71 bleibt | explicitReplaceModeWithoutLegacyFlagStillReplacesDocuments |
| E10C-08 (zusätzliche MERGE-Sicherheitsgrenze) | explizites MERGE mit altem Dokumentflag löscht lokale Dokumente | absentLegacyArraysDoNotMeanEmptyAndMergeFlagCannotDeleteDocuments |

Zwei weitere rote Assertions sind **nicht** als zwei zusätzliche gleiche Root Causes gewertet: main akzeptiert einen ungültigen Snapshot-Tripbezug (`Invalid expenseReceiptId accepted`), und der neue Teilbackup-Schutzvertrag wird noch nicht erfüllt. Beim Teilbackup löscht main das Darlehen gerade nicht; der Test schützt den neu eingeführten echten Parentersatz vor einem neuen Bruch und beweist keinen vorherigen Darlehens-Delete-Bug. Insertfehler-Rollback und bereits korrekt ersetzte Preference-Relationen blieben im Vorherlauf grün. Die Root-Cause-Fixes sind zentral im gemeinsamen Replace-Vertrag; keine individuellen ID-Reparaturen.

## False Positives und Grenzen der Prüfung

- Standardrouten können im normalen UI hart gelöscht werden, ohne historische Fahrten umzuschreiben (`ReceiptViewModel.deleteStandardRoute`). Deshalb ist eine historische standardRouteId kein pauschal erzwingbarer Pflicht-FK. Der neue Test verlangt nur konsistente frisch erzeugte Snapshotfälle; keine Umdeutung historischer Routen.
- `propertyReference`, Mieter-/Renovierungstexte, Exportlauf-Historienfelder und leere optionale IDs sind nicht automatisch Pflicht-FKs. Legacy propertyId wird nicht auf die erste Immobilie umgemappt.
- LoanAssignment → Transaction war zuvor nicht durch Löschen von Banktransactions gebrochen: beide Tabellen waren upsert-only. Der konsistente Ersatz beider ist nun Teil desselben Snapshotvertrags, kein erfundener separater Parent-Delete-Befund.
- Preferences löschen bei REPLACE_FULL schon bisher ihre exportierbaren Keys. Sanierungsrelationen, Tenant-Historie und Tasks erfordern deshalb keinen neuen Parser oder Store.
- Alte Backups ohne Abschnitte behalten diese lokalen Domänen aus Kompatibilitätsgründen. Neue Brüche zu erhaltenen Room-Child-Domänen werden anhand tatsächlich entfernter Parent-IDs blockiert. Fehlende Snapshotdomänen, fehlende Preferenceabschnitte oder bereits historisch inkonsistente Daten sind keine Garantie für einen vollständigen aktuellen Vollsnapshot. Keine Daten werden anhand fehlender Abschnitte pauschal gelöscht.
- Supplemental-Preflight kennt den Core-Belegbestand nicht. Die gezielte Tripprüfung findet deshalb nach Assembly in der Supplemental-Transaktion statt. Andere historisch ungültige Snapshotreferenzen werden hier nicht pauschal als neue harte FKs eingeführt. Die zusätzliche Parent-Delta-Prüfung erfasst ausschließlich IDs, die vor dieser Supplemental-Transaktion vorhanden waren und von genau diesem Restore entfernt werden; leere optionale Scopes und LEGACY_PROPERTY_ID bleiben von einer neuen Property-FK-Pflicht ausgenommen.
- Stabile internalIds wären langfristig für Tripbezüge über Geräte hinweg robuster; für diesen Fehler genügen bestehende numerische Snapshot-ID-Erhaltung und konsistenter Ersatz. Keine neue persistierte Tripreferenz.
- Die Restorelogik übernimmt weiter den bestehenden Originaldatei-/OCR-/Pfadvertrag; „exakter Snapshot“ bezeichnet die fachlichen Datensätze/IDs, nicht bytegleichen Roh-JSON-Export aller lokalen Metadaten.

## Versionen und unberührte Bereiche

Room **34 → 34**, keine Migration, keine destructive migration. Supplemental **15 → 15**, Receipt-Backup **2 → 2**. Keine neuen persistierten Backupfelder. Navigation, Design, Parser-/Schreibgrenzen, Bankbuchungsliste und DATEV-Exportlogik unverändert. Im bestehenden Phase10A-Emulatortest wurde nach einem belegten CI-Synchronisierungsfehler ausschließlich das Warten auf die neue Compose-Übersicht nach Activity-Recreate ergänzt; Assertions und Timeoutgrenze bleiben unverändert.

## Tests und Prüfstatus

Neue fokussierte Robolectric-Tests:

- `Phase10CReplaceFullRelationIntegrityTest`: echter Fahrtbuchungs-/Core-/Coordinatorpfad, leeres Ziel, zwei MERGEs, zwei REPLACE_FULLs, numerische/stabile IDs, expense/source-Orphans, Standardroute, Property; ungültige Snapshotbezüge und echter Insertfehler mit Rollback.
- `Phase10CBankRelationRestoreTest`: getrennte konkrete Link-, Mietbeleg- und Evidenzfälle; Snapshotersatz der Bankdomänen und Schutz lokaler Klassifikation/entferntem Link bei zwei MERGEs.
- `Phase10CPortfolioRelationRestoreTest`: Produktionsaufruf mit Dokumentflag für den Dokument-/Property-/Unit-/Loan-/AfA-Fall; expliziter Modus ohne Flag separat; bereits korrekt ersetzte Preferences, Legacyabschnitte und Parent-/Child-Teilbackup-Rollback; explizites MERGE mit widersprüchlichem Altflag darf nicht löschen.
- `Phase10CRestoreFixture`: gemeinsame Fake-Transport-/Corefixture, keine zweite Restoreimplementierung.

Lokale Gradle-/Kompilierungsstarts scheitern vor Projektkonfiguration: `FileLockContentionHandler` / „Could not determine a usable wildcard IP for this machine“. Gradle 9.3.1, JDK17 und SDK sind vorbereitet, aber lokale Socketverwendung wird von der Sandbox verhindert. Zwei Versuche zusätzlicher Netzwerkfreigabe wurden unterbrochen; keine lokale erfolgreiche Ausführung behauptet. `/dev/kvm` fehlt. `git diff --check` ist separat ausführbar.

Ausgeführte CI:

| Lauf | Head | Ergebnis |
|---|---|---|
| [Android CI #1306 / 39059714564](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/39059714564) | 8f43ea56fd8620df852f5569dd89342205673e40 | erster Fixstand vollständig erfolgreich: lint, Unit/Robolectric, assembleDebug, assembleDebugAndroidTest und 70 Android-Emulatortests |
| [Android CI #1307 / 39062657398](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/39062657398) | ee37bd7bbaf8d1e851b578388d6984e974f38176 | diagnostischer unveränderter main-Produktcode: lint grün; zehn strikte Assertions rot, 1058 Tests ausgeführt; APK/Emulator folgerichtig übersprungen |
| [Android CI #1308 / 39063579125](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/39063579125) | 7a2ff42e0b965f2d7ab075e623bdc91a0be144cb | verify vollständig grün; XML 1063 Einträge, 0 Failures/Errors/Skips, alle zwölf Phase10C-Fälle und Phase9A–9D/10A grün. assembleDebugAndroidTest erfolgreich. Emulator: 70 ausgeführt, 1 Fehler, 0 Skips in Phase10AIdentity15EmulatorTest; Testsynchronisationsfehler nach Recreate, vollständig ausgewertet. |

Der Fehler in #1308 entsteht in `detail(renamed)` direkt nach `scenario.recreate()` (vorher Zeile 73/49). `performScrollToNode` fragt die noch nicht sicher registrierte neue Übersicht ab; der Semantics-Fehlerdump greift während des Layouts auf dem Instrumentation-Thread zu (`Detected multithreaded access to SnapshotStateObserver`, vorheriger Thread 2, aktueller Runner-Thread 41). Der spätere Diagnosebaum enthält die richtige Übersicht und die umbenannte Einheit. Der Test wartet nun vor dem Scrollen über `onAllNodesWithTag(...).fetchSemanticsNodes().size == 1` auf genau diesen erwarteten Zustand. Bestehende Grenze 10.000 ms, keine Retries, keine entfernten Assertions, keine Produkt-/Navigationsänderung. Der neue Lauf wird wegen dieser konkreten Synchronisierungskorrektur gestartet, nicht als blinder Wiederholungsversuch.

Der finale Fix enthält exakt dieselben zwölf Phase10C-Testfälle wie der Vorherlauf plus die zentrale Parent-Delta-Absicherung. Sein abschließender CI-Lauf und die Conclusion werden im [PR #140](https://github.com/xedox737/Steuer-Asistend-APP/pull/140) nach vollständigem Ablauf dokumentiert; maßgeblich sind die Checks des finalen PR-Heads. Der PR wird nicht gemergt. Keine Freigabe aus übersprungenen Jobs ableiten. Bestehende Phase10A- und Phase9A–9D-Tests bleiben unverändert und müssen im normalen vollständigen Testsatz laufen. PR #139 wird weder gemergt noch werden dessen Assertions abgeschwächt.

Praktisch offen: echtes Drive, reales Gerät, Prozessabbruch zwischen Core/Supplemental, externe Provider/Picker, Kanzleiimport. Keine Behauptung solcher Tests. Ein späterer erneuter Phase10B-Lauf nach separat freigegebenem Merge bleibt Voraussetzung für die Portfoliofreigabe.
