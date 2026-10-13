# Phase 9A – Datenerhalt und Backup-/Restorevertrag

Basis: 5a58bc948daf50477c4578675abeebd237010491.
Branch: fix/phase9a-data-preservation-backup-restore. Kein Merge.
Scope ausschließlich A01, A02, A06, A07, A08, A09 aus dem Phase-9-Audit.

## A01: beschädigt ist nicht leer

TenantHistoryStore und PropertyTaskStore liefern einen getrennten Lesezustand:
kein Rohbestand, vollständig gelesen, beschädigter Rohbestand. Einzelne gültige
Nachbareinträge bleiben lesbar. Fehlerhafte Einträge bleiben im ursprünglichen
Preference-String erhalten; die lesbare Teilmenge ist keine Erlaubnis zum Speichern.
Bei beschädigten Daten blockieren save, ensureCurrentPeriod, Korrektur und Task-upsert.
Die bestehende hasStoredPeriods-Sicherung der Sammelerfassung bleibt erhalten.
Der gültige Legacy-Rohwert darf wie bisher kopiert werden; ein beschädigter Legacy-
Rohwert wird nicht automatisch migriert/überschrieben. Ein bewusst leeres Array kann
weiterhin initialisiert werden. Historien-/Aufgabenansicht zeigt eine deutsche
Prüfmeldung; Schreiben ist gesperrt. Keine Reparaturdatenbank, keine ID-Neuerzeugung.
Reparatur bleibt gezielt am erhaltenen Originalrohwert möglich.

## A02: fehlgeschlagene Serialisierung ist keine leere Liste

Positionseditor prüft Menge × Einzelpreis und die serialisierbare Gesamtliste.
ReceiptItemConverter.serialize liefert Result, toJson wirft im Fehlerfall kontrolliert
statt einen leeren String zu liefern. Endlichkeit aller Positionszahlen, Produkt,
Steuerprodukt und Gesamtsumme wird vor der Serialisierung geprüft. VM und Repository
validieren Positions-JSON nochmals vor Schreiben; insertAll prüft den gesamten Batch
vor dem ersten DAO-Write. UI zeigt einen deutschen Fehler. Bewusst alle Positionen
entfernen bleibt erlaubt. Ein Überlauf kann vorhandene Positionen oder andere
Receipt-Fachwerte nicht mehr als erfolgreichen Teilupdate ersetzen.

## A06: vollständiger Preflight

BackupNumbers unterscheidet fehlende Legacyfelder von vorhandenen beschädigten
Zahlen. Nur fehlende Felder erhalten die bisherigen definierten Defaults.
Ungültige/Null-/nichtendliche vorhandene Zahlen und nicht darstellbare Ganzzahlen
sind Fehler, keine 0/100-Ersatzwerte. Supplemental validiert alle vorhandenen Arrays
und Property-Bildbytes vor Room, Preferences oder Dateischreiben. LoanInputValidation
wird einschließlich Datumsreihenfolge wiederverwendet; PropertyWizardInput validiert
Immobilienwerte, die vorhandene Einheits-Flächenregel bleibt maßgeblich. Die allgemeine
Double→Float-Härtung aus N02 wird nicht vorgezogen.

Die normale vollständige Restore-Bestätigung lädt und validiert das Supplemental-
Snapshot vor dem Core-Import und übernimmt anschließend denselben geprüften Payload.
FullRestoreCoordinator kann Preflightfehler ohne Aufruf beider Schreibphasen melden.
Primäre Property-/Unit-JSON-Lesefehler sind ebenfalls blockierend; Stammdaten liest
Property und Units vor dem ersten dieser Writes. Beschädigte lokale Altwerte werden
nicht automatisch bereinigt. Historische Infinity-Loanwerte bleiben im Editor
reparierbar; neue beschädigte Backupwerte dürfen sie nicht erneut importieren.

## A07/A08: Cent- und Vorzeichenvertrag

Alle aktiven Receipt-Metadaten-, Index-, Papierkorb- und Reparaturschreiber nutzen
ReceiptMoney.toCent. Dieser Helper erhält die bisherige Math.round-Regel des
Positionsvertrags und prüft Endlichkeit und Long-Darstellbarkeit vor Konvertierung.
Keine neue Bank-/DATEV-Rundung. 0,29/1,13 werden zu 29/113 Cent; negative zulässige
Receiptbeträge behalten ihr Vorzeichen. Der Restore-Preflight blockiert fehlende/
beschädigte Beträge, nicht pauschal negative Gutschriften. ReceiptInputValidation
und DATEV haben bereits diese signed-Receipt-Konvention. EXTF, DATEV-Serializer,
Konten, BU, Freigabe und Encoding bleiben unverändert.

## A09: Versionierter Receipt-Vertrag

Neue Receipt-Metadaten: Schema 2. App-/Drive-Konfiguration bleibt Schema 1;
Supplemental bleibt Schema 15; Room bleibt Version 34, keine Migration.
Schema 1 bleibt lesbar. Seine fehlenden neuen Felder verwenden definierte historische
Defaults; kontoNr kann ausschließlich beim fehlenden alten Feld aus der ersten
Position stammen. Ein explizites kontoNr, auch leer, wird nie durch Positionen ersetzt.
Schema 2 fordert die relevanten neuen Felder; unvollständige neue Sicherung blockiert.

Zusätzlich explizit erhalten: receiptId, unitId, exportlaufId, kontoNr,
isEigenleistungSanierung, Uhrzeit, Mieter und Löschmetadaten. Beide aktiven
Receipt-Metadatenschreiber tragen die gleichen unabhängigen Felder sowie die
OriginalDocument-Kette. Interne Beleg-, Property-, Unit-, Original-/Datei- und
Auditrelationen werden nicht aus sichtbaren Namen rekonstruiert.

Beim frischen Schema-2-Restore bleibt auch die freie Room-Beleg-ID erhalten.
Eine Kollision mit einem anderen Beleg wird vor Übernahme blockiert. Bei MERGE
bleiben vorhandene lokale Identitäten/Fachwerte gemäß ReceiptRestoreUpsertResolver
maßgeblich. Banklinks verwenden weiterhin receiptInternalId zur vorhandenen
Auflösung. Schema-1-Import und bestehende allgemeine Resolver-Aufrufe behalten
ihre bisherige lokale ID-Vergabe. REPLACE_FULL und MERGE bleiben getrennt.
Originalbytes/MIME/Hashes und Tombstone-Eligibility behalten ihre bestehenden Regeln.

## Regressionen

Neue Store-/Room-/Robolectric-Tests: beschädigte Nachbareinträge, innerer rentChanges-
Fehler, falscher Preference-Typ, blockierte Writers, unveränderte Rohwerte,
leerer/gültiger Bestand; Serializer-/Produkt-/Summenfehler, keine partiellen Writes,
sichere große Zahlen und bewusst leere Positionsliste; missing/invalid, alte Schemas,
Loan/Property/Unit, Chronologie, Coordinator-Preflight ohne Mutation.

Fake-Drive-Integration nutzt die vorhandene ManagedDocumentDriveGateway/Fake-Seam:
aktiven Metadata-Writer, buildRestoreSnapshot, frisches Ziel, zweimal MERGE,
REPLACE_FULL, Centreihe 0,29/1,13/10,01/1000,99/−119,00, Originalbytes/MIME/Hash,
stabile Receipt-/Unit-/Auditfelder und kollidierende Room-ID. Die bestehende
Drive-Seam erhält eng begrenzte JSON-/Tombstone-Lese-/Metadatenschreibmethoden;
keine zweite Backuparchitektur oder Änderung der fachlichen Tombstoneregeln.

Neue Android-Compose-Fälle: beschädigte Historie mit unverändertem Rohwert und
Screenshot; Positionsüberlauf, unveränderter Beleg und Reopen mit zwei Positionen;
restaurierter Beleg mit ursprünglichem Property-/Unit-/Auditkontext. Restore-Preflight
wird technisch durch Coordinator-/Room-/Fake-Drive-Integration belegt, da eine echte
Drive-Anmeldung im isolierten Emulator kein zuverlässiges Testfixture ist.

Der Phase-8A-Altwerttest behält alle Reparatur-/Abbruch-/Speicherassertionen.
Sein bisheriger Infinity-Restore-Eingang wird zusätzlich als abgewiesen geprüft;
der schon vorhandene historische Room-Infinity-Wert wird separat als Altbestand
injiziert. Keine Assertion entfernt/abgeschwächt, keine Tests deaktiviert.

Prüfstatus bei erstem Commit: git diff --check erfolgreich. Lokal fehlen Gradle,
Android-SDK und adb; Unit-/Lint-/Build-/Instrumentation- und vollständige Emulator-
Prüfung erfolgen über den bestehenden Android-CI-Workflow. Tatsächliche Resultate
werden erst nach Ausführung im PR und Abschlussbericht eingetragen.
Keine physische Geräte-, Live-Drive- oder Kanzleiabnahme behauptet.

## Arbeitsbasis und Grenzen

Git fetch origin scheiterte lokal am nicht erreichbaren Proxy. main wurde frisch
über den GitHub-Connector gelesen. Der lokale Git-Arbeitsstand verwendet den exakt
rekonstruierten signierten Originalcommit und unveränderte Git-Blobs; fehlende
Dateien werden sparse geführt. Über den Connector wird nur der eigene Branch
aktualisiert. Kein direkter main-Write, kein Merge.

Unverändert: Bankliste/Facharchitektur, DATEV, Fahrtenbuch, Mietaggregate,
AfA-Prognose, globale Header-/Bottom-Navigation und Backstack.
Keine allgemeine Parserhärtung (A03–A05/A10/A11, N01–N06) in dieser Phase.
