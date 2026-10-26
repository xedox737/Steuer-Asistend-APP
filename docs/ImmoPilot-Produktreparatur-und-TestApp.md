# ImmoPilot – Produktreparatur und vorläufige Test-App-Integration

## Zusammenfassung

Alle drei dokumentierten Probleme sind auf dem Produkt-Reparaturbranch gezielt
behoben und vollständig automatisiert geprüft. Der darauf beruhende bestehende
Test-App-Aufbau ist auf eigenem Integrationsbranch aktualisiert und ebenfalls
vollständig CI-grün. Keine doppelte Fachlogik, keine Room-Migration, keine
produktiven Daten verwendet oder geändert. main bleibt unverändert.
Die Freigabe und ein Merge sind noch nicht erfolgt; die Testintegration ist
ausdrücklich vorläufig. Echte Google-/Geräteabnahme bleibt NOCH OFFEN.

Stand: 26.10.2026, nach abgeschlossenem CI-Lauf 40263818931.

## Aktueller vollständiger Reparatur- und Integrationsnachweis

Produkt-Head: `6dd94ecae1516a8e4ca57b91fd2a740875756133`.
Vorläufiger Test-Head: `4c65911edde5c1acfda8c6b727c56c1f4d79fd62`.
Produkt-PR: https://github.com/xedox737/Steuer-Asistend-APP/pull/144
Referenz-PR (weiterhin Draft auf 8ddd556):
https://github.com/xedox737/Steuer-Asistend-APP/pull/143

| Automatisierte Prüfung | Produktreparatur | Vorläufige Test-App |
| --- | --- | --- |
| Vollständige CI Run-ID | 40232280781 | 40263818931 |
| Tatsächliche eindeutige JVM-Methoden | 1139 | 1166 |
| Tatsächliche eindeutige Emulator-Methoden | 70 | 73 |
| Methodenfehler / Errors / Skips | 0 / 0 / 0 | 0 / 0 / 0 |
| Reparaturmethoden | 36 | 36 |
| Zusätzlich bestehende Phase11A-Methoden | nicht Teil des Produktbuilds | 27 |
| Alle 60 Originalhashes nach frischem Full-Restore | identisch | identisch |
| Contract-Abweichungen / Relationsfehler / Dubletten | 0 / 0 / 0 | 0 / 0 / 0 |
| Backup / wiederholter MERGE / Originaldownload | AUTOMATISIERT BESTANDEN | AUTOMATISIERT BESTANDEN |
| Lint / Debug / AndroidTest-APK | AUTOMATISIERT BESTANDEN | AUTOMATISIERT BESTANDEN |
| Echte Google-/Geräteprüfung | NOCH OFFEN | NOCH OFFEN |

CI-Links:
https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/40232280781
https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/40263818931

Test-App-Unittransport: SHA256
`e4c1dabb666834cfa878515bd5ba849b7e7af614808be1e1c5f184ccb85d982d`,
2.390.225 Bytes. Tatsächliche JUnit-Testcase-Knoten und eindeutige Methodennamen
wurden unabhängig nachgezählt; keine bloße Übernahme grüner Job-Symbole.
Der Full-Restore-Reparaturlauf ist
`P11A-2026-10-26-REPAIR-62139841-d0b9-4802-b4ee-8228696ab639`.
Der bestehende Namespace-Test verwendet einen historischen Datumspräfix
`P11A-2026-10-25-UNIT-DRIVE-`, ergänzt durch eine neue UUID; das ist kein
Laufdatum. Sein aktueller Nachweis hat UUID
`6b580685-b59b-42f1-bf3f-ab83d9dde6a7`, CI-/Commit-Zuordnung erfolgt über
ci-evidence-summary.json auf 40263818931 / 4c65911. Keine alte Evidence übernommen.

## Versionsgebundener Handy-Download und OAuth

[Neue Test-App-APK aus dem verify-Job](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/40263818931/artifacts/12626630987).
Mit GitHub-Anmeldung das ZIP herunterladen, entpacken und ausschließlich
`app-debug.apk` der Test-App installieren. Die Instrumentation-APK ist für
Handy-Seed/Drive nicht erforderlich. Keine produktive App ersetzen oder löschen.

| Merkmal exakt dieses verify-Artefakts | Wert |
| --- | --- |
| Application-ID | com.aistudio.steuerassistent.vnhqkp.phase11a |
| Sichtbarer Name | ImmoPilot Test |
| Version / Code | 1.0 / 1 |
| APK-SHA256 | 33d4e46d57c345564d317de8af90c4ea2fc1c9b2d9967accba447d4283aace2a |
| ZIP-Digest laut GitHub | 76cd537e95984d3016072c6dadb218a8b09ce60663acabbe954ddfe4778b8320 |
| Zertifikat SHA1 | 5fb1e127b3aac5d14e6de6be2441c60104d8ec87 |
| Zertifikat SHA256 | 13460858f1f71304bcf6a3145dd03e8e314d4d9f8cf377c96bd4d8ca42ddc1cc |
| Signaturprüfung | apksigner verify erfolgreich; Hashes aus öffentlichem DER-Zertifikat |
| Vier Provider-Authorities | ausschließlich eigenes .phase11a-Package |

Die separate Emulator-APK stammt von einem anderen CI-Job und besitzt einen
anderen Testsignierer. Für den obigen Download gelten ausschließlich die hier
zugeordneten Fingerprints. Die APK wurde anhand der tatsächlichen CI-Inspektion
geprüft; der ZIP-Digest stammt von GitHub. Die binären GitHub-Artefakte wurden
hier nicht heruntergeladen; die synthetische Evidence wurde unabhängig über
bereits vorhandene SHA256-geprüfte Logarchive gesichert.

Für nativen Google-Login muss ein Android-OAuth-Client für das exakte Package
und SHA1 dieses APK-Zertifikats zugelassen sein; zusätzlich Drive API,
Consent/Scopes und gegebenenfalls Testnutzer prüfen. Remote-Konfiguration wurde
nicht eingesehen: weder ihr Fehlen noch erfolgreiche Anmeldung wird behauptet.
Bestehende alternative Anmeldewege bleiben unverändert. CI-Debugsignierer
wechseln je Job/Lauf. Bei Signaturkonflikt keine bestehende Produktinstallation
oder Daten löschen; eine getrennte leere Testumgebung verwenden. Ein dauerhaft
verwalteter Testsignierer ist ein gesonderter Konfigurationsauftrag.

Handy-Ablauf nach Freigabe der isolierten Praxisvorbereitung: ImmoPilot Test
öffnen → Mehr → App-Einstellungen → Phase 11A Testwerkzeuge →
17-Einheiten-Testportfolio laden → Bestätigung → Counts prüfen → App neu starten.
Bei nichtleerer Test-App verweigert der Seed das Laden; Produktions-Package
wird vor jedem Seed/Evidence-Zugriff hart abgewiesen. Anschließend Drive verbinden,
Test-Datenspeicher und ausschließlich `ImmoPilot Phase11A Testdaten` kontrollieren.
Erst danach den getrennt protokollierten realen P11A-DRIVE-01 starten.


## Emulator-Nachweise der aktuellen Integration

Alle 73 erwarteten konkreten Android-Methoden sind tatsächlich ausgeführt,
eindeutig und ohne Fehler/Errors/Skips. Zusätzlich ein erneut ausgeführter
nativer UI-/Prozessfall; dieser wird nicht als 74. eindeutige Methode gezählt.
Android 15 / API 35, frischer Wegwerf-CI-Emulator, keine reale Produktinstallation.

Produkt/Test: UIDs **10209 / 10211**, getrennte Datenverzeichnisse, Room,
Preferences, interne Dateien, Cache und app-spezifische externe Verzeichnisse.
Test-Room integrity_check=ok, 5 Objekte und 70 Belege. Direkte Produkt-
Privatlesezugriffe verweigert, fremder Providerzugriff verweigert.
Produktreferenz nach allen E2Es mit identischen Domain-Dateihashes und Counts.
Seed und Snapshot gegen das Produkt-Package werden vor Datenzugriff abgewiesen.

Zusätzlicher nativer Seed-/Prozesslauf:
P11A-2026-10-26-UI-0ce13558-4bdd-4ad8-b8f5-90874a390883.
PID 10807 → vollständig beendet → Launcher-Neustart PID 10861.
Strikter Vorher-/Nachher-Vergleich: 60 Originalhashes, keine ID-Verluste,
Relationsfehler oder Contract-Abweichungen. Dies ist Seed-Persistenz im
Emulator, kein echter Cloud-Restore auf einem Nutzergerät.

Emulator-Evidencetransport SHA256
a2031eacb772e02e53b87a71dbd75058a84ed6b69e41e0a379c05bda266ac836,
154.425 Bytes. Archivgröße, SHA256 und vollständige Methodenmengen wurden
unabhängig geprüft. [Emulator-Artefakt einschließlich Screenshots](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/40263818931/artifacts/12627680244).

Produkt-Evidencetransporte: JVM db3726b6b1efbb7f16dc49ba06b476a9092bd0e09e3c5accf8e657623daa35e0
(1.629.917 Bytes), Emulator 4452e9aad1e3d48bf045f200919fc17ff3fb20a6e8cc8c841fb8216c19771897
(4.476 Bytes). Produkt-Head und GitHub-PR-Prüfcheckout besitzen denselben
Git-Tree 747ae342f0af353df97eb7e57ff2ed183ccc0c97. Es wurde trotzdem kein
Merge in main durchgeführt.

## Reproduzierbarkeit und vollständige Evidence

Die Datei docs/phase11a-repair-evidence/ImmoPilot-Produktreparatur-Evidence.zip
enthält die unveränderten vier geprüften CI-Evidencebestände mit tatsächlichen
JUnit-XMLs, Baselines, Nachher-Snapshots und Diagnoseberichten, sowie die
versionsgebundenen CI-/APK-Zusammenfassungen. manifest.json enthält Größe und
SHA256 jeder Datei; evidence-checksums.json sichert das gesamte ZIP.
Die Berichtsbereitstellung liegt auf einem eigenen reinen Dokumentationsbranch;
Produkt- und Integrationsheads werden dadurch nicht verändert.


## Ausgangsbasis und Grenzen

Repository: `xedox737/Steuer-Asistend-APP`. Tatsächlich abgerufener Produkt-Base:
`69f35772b6480a958ca7479b024dbd4d8bd42fa1`.
Referenz-Test-App: `8ddd556061d6d9a5ed607163aff42dbf3578f8c1`, Draft-PR #143.
Audit: `321a8c30cd8ec5c9b20ad02e4758504976cd787b`.
Reparaturbranch: `fix/phase11a-production-data-integrity`.
Keine produktiven Daten, Google-Konten oder installierten Produkt-Apps wurden
für die Prüfung verwendet. Kein Merge, keine Datenmigration oder automatische
Zuordnungsreparatur. Echte Drive-/Geräteprüfungen bleiben **NOCH OFFEN**.

## Ursachen und Korrekturen

| Fehler | Ursache | Korrektur | Nachweis |
| --- | --- | --- | --- |
| E11A-03, P1 | Upload rekonstruierte vorhandene ManagedDocuments aus einem technischen Teilmodell und verlor Zuordnungen/Review-Felder. | Bestehende Dokumente behalten sämtliche fachlichen Felder; nur technische Übertragungsfelder ändern sich. Identitätswidersprüche brechen vor Cloudmutationen ab. Alte fehlende Einheiten werden nicht geraten. | `ReceiptDocumentSyncStateTest`, `ReceiptDocumentIntegrityAuditTest`, `ReceiptSyncPreservationE2ETest` |
| E11A-01, P1 | Die Original-App akzeptierte fremde explizite IDs und Mappings ohne Namespace-Nachweis. | Gemeinsamer Sicherheitsguard prüft aktuelle MyDrive-Abstammung, Root, Tags, Rollen und Konfiguration vor jedem Inhaltstransfer oder jeder Mutation. Schutz gilt auch für verschobene Cacheziele, geschützte Container und historische Verweise. | `ProductionDriveNamespaceTest` mit tatsächlichem Client/Repository und simuliertem HTTP |
| E11A-02, P2 | Jede Synchronisierung verlangte ein Original, auch bei fachlich bestätigten Fahrtkosten ohne Anlage. | Nur eine eindeutig gebundene, bestätigte, nicht stornierte, nicht KI-geschätzte Fahrt erlaubt einen dateilosen Metadateneintrag. Index, Metadaten und gesichertes Fahrtenbuch müssen denselben Nachweis tragen. Normale erforderliche Originale bleiben Pflicht. | `FilelessLogbookContractTest`, vollständige Portfolio-Synchronisierung und frischer Full-Restore |

Ergänzend deckte der unveränderte Integritätschecker eine Restore-Abweichung auf:
Ein rekonstruierter Originalseitenrang wurde in bereits geprüftes Dokument-JSON
eingefügt. Die Original-Metadaten übertragen dieses JSON jetzt exakt. Ältere
Backups ohne das optionale Feld rekonstruieren weiterhin die Rangfolge.
Einzeloriginal, geprüfte Mehrseitenfolge und Legacy-Lesbarkeit sind gesondert
getestet (`ReceiptOriginalMetadataPreservationTest`).

Die Produkt-Cloudstruktur bleibt `Steuerassistent Belege/_BelegApp-Daten`,
die Teststruktur `ImmoPilot Phase11A Testdaten/_BelegApp-Daten`.
Die Produktkennung bleibt `ImmobilienBelegApp`, die Testkennung
`ImmoPilotPhase11ATest`. Die Auswahl erfolgt ausschließlich anhand der
Application-ID. Beide Identitäten verwenden dieselbe Sicherheits-/Fachlogik.
Zur Prüfung fremder IDs wird ausschließlich deren Abstammungsmetadaten gelesen;
fremde Dateiinhalte werden nicht geladen, geändert, verschoben oder gelöscht.

## Prüfverfahren und Integrität

Der reale GoogleDriveClient und das reale DrivePersistenceRepository laufen in
Robolectric mit einer frischen privaten Room-Datenbank und einem rein lokalen
HTTP-Drive-Modell. Der Transport führt keinen Netzwerkzugriff aus. Die bestehende
Phase10B/11A-Portfoliofixture wird unverändert wiederverwendet (ZIP-SHA256:
`b052a1ae4569196abb791375aa1fa01fe6a636a1d0082820acb576fa497ea638`).

Ablauf: alle 70 Belege zweimal synchronisieren, zweimal sichern, zweimal MERGE;
zusätzlich Full-Restore in einen neuen Context mit eigenen Dateien, Preferences
und Datenbank, tatsächlicher erneuter Download aller 60 Originale, zweimal MERGE
und erneute Synchronisierung. Manipulierte Fahrtbeweise in Index, Metadaten oder
Supplemental-Sicherung müssen vor dem Ersetzen der lokalen Daten scheitern.

| Bestand im vollständigen Portfolio | Anzahl |
| --- | ---: |
| Objekte / Einheiten | 5 / 17 |
| Belege / ManagedDocuments | 70 / 70 |
| Originaldateien | 60 |
| Bankbuchungen / Bankkonten | 120 / 1 |
| Fahrten | 10 |
| Mietverhältnisse | 17 |
| AfA / Darlehen | 5 / 1 |

Die gezielte Folgeprüfung auf `d9ba35ad669803244e55a1231b44a83e0e6633ef`
führte 27 echte Methoden ohne Fehler/Skips aus. Der strikte Vorher-/Nachher-
Vergleich war grün: 60/60 SHA256, 0 Contract-Abweichungen, 0 fehlende/unerwartete
IDs, 0 Dubletten, 0 Relationsfehler/Orphans. Der Checker ist bytegleich zur
Referenz (SHA256 `20d318ab0a5097aa04e38184853223f27ea72061540ec28222a73a74021ee819`).
Erwartete technische Cloud-ID-Vergabe wird vor der gesicherten Baseline erledigt;
keine neuen Ignore-/Normalisierungsregeln wurden eingeführt.

## Produkt-CI abgeschlossen

Draft-PR #144, Head `6dd94ecae1516a8e4ca57b91fd2a740875756133`:
https://github.com/xedox737/Steuer-Asistend-APP/pull/144

Vollständiger CI-Lauf 40232280781, `verify` und `android-emulator-e2e` erfolgreich:
https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/40232280781

Tatsächlich 1.139 einzigartige JVM- und 70 einzigartige Emulator-Methoden,
0 Fehler/Errors/Skips/Platzhalter. Die Evidence-Archive wurden unabhängig mit
Bytesumme und SHA256 aus dem CI-Log geprüft und die JUnit-XMLs ausgezählt.
36 Reparaturmethoden, sechs Normal-/Stress-Restorevergleiche, frischer
Full-Restore mit nichtleeren fachlichen Referenzen, 60/60 Originalhashes,
0 Contract-Abweichungen/Orphans/Dubletten. CI-Checkout war der GitHub-PR-
Mergeprüfcommit `0671ad3508f1a3456c4c06c96211b0be135c3007`, kein Merge in main.

Produkt-Debug-APK im verify-Artefakt: SHA256
`3a13b20abf69d4752bf5573393193603a3117eb481ad2ec08006c5df4476202c`.
Package unverändert, Label `Steuer-Assistent Anlage V`, Version 1.0/1,
vier eigene Provider-Authorities, kein sharedUserId. Signierter Release-Build
war mangels CI-Signierkonfiguration ausgelassen; lokal wurde Release erfolgreich
unsigniert gebaut. Das ist kein veröffentlichtes Produktupdate.

Die Test-App-Integration erfolgt ausschließlich auf dem eigenen vorläufigen
Integrationsbranch; der bestehende PR #143 bleibt auf seinem Referenzhead.

## Fehlgeschlagene Läufe und Restrisiken

Der erste vollständige Lauf auf `0fe306d` führte 1.136 Methoden aus, davon sechs
fehlgeschlagen und keine übersprungen. Die alten Phase10B/10C-Testaufbauten
schrieben neue Fahrtnachweise mit dem tatsächlichen Serializer, bauten den Index
jedoch noch unvollständig von Hand. Die Aufbauten übernehmen nun den tatsächlich
gespeicherten Nachweis; sämtliche bisherigen fachlichen Assertions bleiben bestehen.
Alle sechs Fälle sind in der genannten gezielten Folgeprüfung grün.

Ein paralleler lokaler APK-Build wurde an der 8-GiB-cgroup-Grenze vom System
beendet (`oom_kill=1`, Gradle-Daemon verschwunden). Das zählt als fehlgeschlagener
Infrastrukturlauf. Der Wiederholungslauf begrenzt Gradle auf einen Worker, ohne
parallele Projekte und mit 3-GiB-Heap. Kein GitHub-CI-Lauf wurde abgebrochen.

Historisch mit Produktkennung im Produktroot gespeicherte Testdaten lassen sich
nicht sicher von Produktdaten unterscheiden. Es erfolgt keine heuristische
Bereinigung. Der Guard schützt die erwartete MyDrive-Struktur; manuell verschobene
oder Shared-Drive-Bestände sind nicht praktisch verifiziert und werden bei
fehlendem Zugehörigkeitsnachweis gesperrt. Bereits verlorene Dokumentzuordnungen
werden nur lesend gemeldet und benötigen eine gesonderte, belegte Reparatur.

Simulierte Transporte ersetzen keinen OAuth-, Geräte-, Picker-/Viewer-, Offline-
oder Abbruchnachweis. Ein frischer Room-/Context-Neustart ist kein echter Android-
Prozess-Kill. Vor praktischer Drive-Abnahme müssen installierter APK-Hash,
Application-ID, Signatur, OAuth-Konfiguration und angezeigter Testroot geprüft
werden. Lokale Release-Builds ohne Produktionsschlüssel sind unsigniert.

## Einordnung der zusätzlich versuchten OCR-Erwartung

Die erste Verschärfung des Referenztests setzte zusätzlich einen OCR-Cachetext
und erwartete diesen nach frischem Restore. Das war mit dem verbindlichen
Phase10D-Vertrag (Klasse C: aus Originalen rekonstruierbarer OCR-Cache)
unvereinbar. Alle fachlichen Referenzen blieben bereits in diesem roten Lauf
erhalten; nur der absichtlich nicht gesicherte Cachetext fehlte. Die neue
Referenzvorbereitung setzt deshalb keinen OCR-Cachewert. Alle fachlichen und
strikten Vergleichsassertions bleiben unverändert, der separate Upload-Test
verlangt weiterhin den Erhalt bereits vorhandenen lokalen OCR-Texts.
Es gab keine Produkt-/Backup-Vertragsänderung und keinen neuen Produktfehler.

## Vorläufige Test-App-Integration

Branch `agent/phase11a-repaired-production-integration`, Head
`4c65911edde5c1acfda8c6b727c56c1f4d79fd62`. Ausgang ist der bereits grüne
Produkt-Head 6dd94ec. Bestehende QA-/Debug-/Build-/UI-Dateien und die Originalfixture
wurden aus 8ddd556 übernommen. Es gab keinen Git-Merge. API, Datenzugriff,
Fachlogik und Modelle sind identisch zum Produktstand; die einzige Main-Differenz
ist der exakt bestehende Zwei-Dateien-UI-Hook-Patch (SHA256
`65b3edaad7e5939fa703eb17ad61e12a96357b4e112f763b330aed6355fffccd`).
Ein neuer CI-Wächter verifiziert dies gegen den gepinnten Produktcommit.

Debug bleibt com.aistudio.steuerassistent.vnhqkp.phase11a / ImmoPilot Test,
Release bleibt Produkt. Keine Kopien des Drive-Guards in Debug/Release; beide
verwenden dieselbe Main-Implementierung. 17-Einheiten-Handy-Seed, Consent,
Leere-Installation-Guard, Produktions-Package-Verweigerung, Originaldateien und
Test-Drive-Hinweis bleiben bestehen. Die Tests verlangen jetzt erfolgreiche
Fahrtbeleg-Synchronisierung und produktiven Zugriffsschutz. Keine vorhandene
Testmethode wurde entfernt; ein zusätzlicher Wächter fordert alle 27
Phase11A-Methoden mit vollständigem Namen und ohne Skips.

Lokal auf f5534ef vollständig: 1.166 JVM-Methoden, 0 Fehler/Errors/Skips/
Platzhalter, zunächst 29 Python-Tests, 36 Reparaturmethoden, 27 Phase11A-Methoden,
60/60 Hashes und 0 Integritätsabweichungen. Lint 0 Fehler/Fatal, 118 Warnungen.
Debug-, AndroidTest- und unsigned Release-Build grün. Tatsächliche Test-App-
Identität, Zertifikat und vier eigene Authorities verifiziert. Tatsächliche
Release-APK ohne Seed/UI/Assets, mit gemeinsamem Namespace-Guard.
Die lokale Metaspace-Warnung beendete den Build nicht; keine CI-Konfiguration
wurde dafür verändert.

Erster gezielter Integrationslauf: JUnit-Initialisierungsfehler wegen eines
Exception-Rückgabewerts am Ende eines neuen runBlocking-Tests. Diese Klasse
galt ausdrücklich nicht als ausgeführt. Nach Unit/void-Korrektur alle 13
Namespace-Methoden sowie anschließend alle 1.166 Methoden grün; alle Assertions
blieben erhalten. Kein zusätzlicher Produktfehler.

## Dokumentierte Integrations-CI-Fehler und QA-Korrektur

CI 40256561773 auf a526dc6 und CI 40259203340 auf 8022c15 bestanden jeweils
die 1.166 JVM-Methoden und sämtliche Daten-/Portfolio-Wächter. Beide scheiterten
anschließend in der neu ergänzten textuellen SHA1-Zertifikatauswertung. Die
APK-Signaturprüfung selbst war jeweils erfolgreich. Die abhängigen Emulatorjobs
waren übersprungen; diese Läufe zählen nicht als vollständige CI-Erfolge.

Die endgültige QA-Auswertung auf 9caefc2 fordert das von apksigner verifizierte
öffentliche PEM-Zertifikat an und berechnet SHA1/SHA256 direkt über die DER-Bytes.
Sie hängt damit nicht von SDK-/Signer-/Digestbezeichnungen ab. Fehlende, kaputte
oder mehrere verschiedene Zertifikate bleiben Fehler. Keine Signaturprüfung
wurde entfernt. Das öffentliche Zertifikat-Prüfbeispiel enthält keinen privaten
Schlüssel. 32 Python-Tests und die echte lokale APK-Inspektion sind grün.
Produktquellen und fachliche Anwendungsassertions blieben unverändert.

## Technische Einstiegspunkte und reproduzierbare Regressionen

### E11A-03 – Dokumentzuordnungen und bestätigte Felder

`app/src/main/java/com/example/data/ReceiptDocumentSyncState.kt`:
`requireIdentity`, `merge` und ausschließlich lesendes `scan`.
`DrivePersistenceRepository.syncReceiptToDrive` wendet die Prüfung vor
Cloudmutationen an und übernimmt bei bestehenden Dokumenten nur technische
Übertragungsfelder. `ReceiptOriginalChain.metadataJson/parseMetadata` erhält
geprüftes JSON exakt; Legacy-Originale behalten ihre Rangrekonstruktion.

Reproduktion/Regression: das originale Portfolio laden, an einem Dokument
mit Darlehen aus demselben Objekt Mieter-/Sanierungs-/Review-/Confidencefelder
bestätigen, zweimal alle 70 Belege synchronisieren und sichern. In einen neuen
privaten Context mit eigener leerer Room/Prefs/Dateien vollständig wiederherstellen,
alle 60 Originale erneut herunterladen und zweimal MERGE ausführen. Alle
autoritativen Felder und Originalbytes müssen zur gesicherten Baseline passen.
`ReceiptSyncPreservationE2ETest` führt genau diesen Pfad aus. Die zwei
`ReceiptDocumentIntegrityAuditTest`-Fälle belegen die Meldung alter fehlender/
widersprüchlicher unitId ohne Schreibzugriff oder neue Zuordnung.

### E11A-01 – Cloud-Zugehörigkeit vor Inhaltszugriff und Mutation

`app/src/main/java/com/example/api/DriveNamespacePolicy.kt`: `execute`,
`validateConfig`, `validateJsonReferences`, `owned`, `protectedContainer`.
`DriveEnvironment` wählt unveränderlich anhand BuildConfig.APPLICATION_ID.
`GoogleDriveClient` besitzt nur drei zentrale rohe HTTP-Callbacks; Inhalt und
Mutation laufen über dieselbe Policy. Metadaten werden nur zur aktuellen
Zugehörigkeitsprüfung gelesen. JSON-Datei-/Ordnerrollen werden unterschieden.
`DrivePersistenceRepository` prüft auch Sync-/Restore-/Löschreferenzen vor
lokalen Mutationen. Fehlende historische Blattreferenzen dürfen gelesen werden,
aktuelle IDs und fehlende Vorfahren bleiben Fehler.

Reproduktion/Regression: simuliertes gemeinsames Konto mit vorhandenen
Produktdateien, eigener Teststruktur und kollidierenden fachlichen IDs anlegen.
Direkte fremde IDs, Config-IDs, Mappings, verschobene Cacheziele, Shortcuts,
Datei-/Ordner-Verwechslungen und ungesicherte globale Queries einspeisen.
Produkt/Test dürfen ausschließlich ihre eigenen Inhaltszugriffe/Mutationen
ausführen. Produktinventar inklusive IDs/Bytes/Metadaten muss unverändert bleiben;
eigene Initialisierung/Backup/MERGE muss wiederholbar ohne Root-Dubletten sein.
`ProductionDriveNamespaceTest` und die verstärkte
`Phase11ADriveNamespaceTest` testen die beiden tatsächlichen Build-Identitäten.

### E11A-02 – ausdrücklich bestätigte dateilose Fahrtkosten

`app/src/main/java/com/example/data/FilelessLogbookContract.kt`: `create`,
`validateMetadata`, `validatePair`, `validateSupplemental`.
`DrivePersistenceRepository.syncFilelessReceipt` nutzt den bestehenden
Metadatenwriter und denselben Index-Builder wie normale Belege. Der Nachweis
bindet Fahrt-/Beleg-ID, Objekt, Datum, Strecke, Quelle, Buchungsschlüssel,
Betrag und tatsächliches Konto. Unbestätigte/stornierte/KI-geschätzte/mehrdeutige
oder widersprüchliche Fahrten erhalten keine Ausnahme. Keine leeren Ersatzdateien.

Reproduktion/Regression: die zehn echten bestätigten Fahrtkosten des Original-
portfolios ohne Anlagen zweimal mitsynchronisieren und mit allen 70 Belegen
wiederherstellen. Originalmenge bleibt exakt 60. Manipulierte Nachweise in
Index, Metadaten oder Fahrtenbuch müssen Restore vor lokalem Ersetzen blockieren.
Normale Belege mit fehlender, nicht vorhandener oder leerer Originaldatei müssen
weiterhin scheitern. `FilelessLogbookContractTest` und die gemeinsamen echten
Client-/Repository-Portfoliofälle prüfen dies einschließlich unveränderter
konfigurierter Kontierung und wiederholter Synchronisierung.

## Reparatur-Commits

- cb2a2413279f422c1bb6f0fe86b59f68607d22c6 fix: preserve authored document associations during receipt upload
- 8809dcd8287e63b671b43c1f26b837eae525962e docs: record versioned document sync regression evidence
- 7df5bb5ec107d1106639fa20215af4a5d105a1ac fix: enforce Drive namespace ownership for production and test identities
- ebe8e2564cb249b55c265d0216e33b54d2989957 fix: back up confirmed fileless trip receipts with validated restore evidence
- 0fe306d17ecd6d5e9cc8ad679a6afd4f804bfff9 test: enforce full fresh restore integrity and strengthen Drive mutation guards
- 522d5bbb09fc8b169ec68b6e1d3c539f6df79243 fix: preserve reviewed original metadata verbatim across restore
- d9ba35ad669803244e55a1231b44a83e0e6633ef docs: record strict restore contract diagnosis and regression follow-up
- 05ca5464da567c19062f020016e44e4eda9bdc48 test: verify restored review references and built APK isolation
- b509e2beb7b879b2eacd53e05d9940a99c6c80be test: align fresh restore fixture with documented OCR cache contract
- 6dd94ecae1516a8e4ca57b91fd2a740875756133 docs: record complete local repair validation before CI

## Geänderte Dateien – Produktreparatur

26 Dateien/Umbenennungen:

- A · .github/scripts/e11a-inspect-apk.py
- A · .github/scripts/e11a-verify-repair.py
- M · .github/scripts/phase10b-evidence.py
- A · .github/scripts/phase11a-integrity-check.py
- M · .github/workflows/android-ci.yml
- A · app/src/main/java/com/example/api/DriveEnvironment.kt
- A · app/src/main/java/com/example/api/DriveNamespacePolicy.kt
- M · app/src/main/java/com/example/api/GoogleDriveClient.kt
- M · app/src/main/java/com/example/data/DrivePersistenceRepository.kt
- A · app/src/main/java/com/example/data/FilelessLogbookContract.kt
- M · app/src/main/java/com/example/data/ManagedDocumentDriveGateway.kt
- A · app/src/main/java/com/example/data/ReceiptDocumentSyncState.kt
- M · app/src/main/java/com/example/data/ReceiptOriginalChain.kt
- M · app/src/main/java/com/example/data/SupplementalDriveBackup.kt
- A · app/src/test/java/com/example/api/ProductionDriveNamespaceTest.kt
- A · app/src/test/java/com/example/comparison/ComparisonDriveHttpFixture.kt
- A · app/src/test/java/com/example/comparison/ComparisonSnapshot.kt
- A · app/src/test/java/com/example/comparison/ReceiptSyncPreservationE2ETest.kt
- A · app/src/test/java/com/example/data/FilelessLogbookContractTest.kt
- M · app/src/test/java/com/example/data/Phase10BPortfolioAcceptanceTest.kt
- M · app/src/test/java/com/example/data/Phase10CRestoreFixture.kt
- A · app/src/test/java/com/example/data/ReceiptDocumentIntegrityAuditTest.kt
- A · app/src/test/java/com/example/data/ReceiptDocumentSyncStateTest.kt
- A · app/src/test/java/com/example/data/ReceiptOriginalMetadataPreservationTest.kt
- A · app/src/test/resources/phase11a/qa-transport-snapshot.zip
- A · docs/PHASE11A_PRODUCTION_REPAIR_PROGRESS.md

## Geänderte Dateien – vorläufige Test-App gegenüber Produktreparatur

56 Dateien/Umbenennungen:

- A · .github/qa/phase11a-integration-ui-hooks.patch
- A · .github/scripts/apk_signing.py
- M · .github/scripts/e11a-inspect-apk.py
- M · .github/scripts/phase10b-evidence.py
- A · .github/scripts/phase11a-audit-ci.py
- A · .github/scripts/phase11a-check-shared-product.py
- A · .github/scripts/phase11a-check-ui-packaging.py
- A · .github/scripts/phase11a-device-evidence.py
- A · .github/scripts/phase11a-emulator-smoke.py
- A · .github/scripts/phase11a-init-run.py
- A · .github/scripts/phase11a-inspect-apks.py
- A · .github/scripts/phase11a-reference.init.gradle
- A · .github/scripts/phase11a-sandbox-check.py
- A · .github/scripts/phase11a-ui-seed-smoke.py
- M · .github/scripts/run-android-emulator-e2e.sh
- A · .github/scripts/tests/fixtures/android-debug-public-cert.pem
- A · .github/scripts/tests/test_phase11a_apk_signing.py
- A · .github/scripts/tests/test_phase11a_device_guards.py
- A · .github/scripts/tests/test_phase11a_drive_boundary.py
- A · .github/scripts/tests/test_phase11a_evidence_counts.py
- A · .github/scripts/tests/test_phase11a_integrity.py
- A · .github/scripts/tests/test_phase11a_phone_seed_guards.py
- A · .github/scripts/tests/test_phase11a_sandbox_guards.py
- M · .github/workflows/android-ci.yml
- M · app/build.gradle.kts
- M · app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt
- M · app/src/androidTest/java/com/example/e2e/EmulatorTestSupport.kt
- A · app/src/androidTest/java/com/example/e2e/Phase11ADriveNamespaceEmulatorTest.kt
- A · app/src/androidTest/java/com/example/e2e/Phase11AUiSeedEmulatorTest.kt
- M · app/src/androidTest/java/com/example/e2e/Phase6BWorkflowEmulatorTest.kt
- A · app/src/androidTest/java/com/example/qa/Phase11AInstrumentation.kt
- A · app/src/androidTest/java/com/example/qa/Phase11ASandboxProbe.kt
- A · app/src/debug/AndroidManifest.xml
- A · app/src/debug/assets/phase11a/hash-manifest.json
- A · app/src/debug/assets/phase11a/provenance.json
- A · app/src/debug/assets/phase11a/qa-transport-snapshot.sha256
- A · app/src/debug/assets/phase11a/qa-transport-snapshot.zip
- A · app/src/debug/java/com/example/qa/Phase11AIsolation.kt
- A · app/src/debug/java/com/example/qa/Phase11APortfolioSeeder.kt
- A · app/src/debug/java/com/example/ui/BuildVariantDriveNotice.kt
- A · app/src/debug/java/com/example/ui/BuildVariantSettingsEntry.kt
- A · app/src/debug/res/values/strings.xml
- M · app/src/main/java/com/example/ui/AppSettingsScreen.kt
- M · app/src/main/java/com/example/ui/ReceiptAppUi.kt
- A · app/src/qa/java/com/example/data/Phase11ASnapshot.kt
- R100 · app/src/test/java/com/example/data/RestoreContractSnapshot.kt · app/src/qa/java/com/example/data/RestoreContractSnapshot.kt
- A · app/src/qa/java/com/example/qa/Phase11ADriveHttpFixture.kt
- A · app/src/release/java/com/example/ui/BuildVariantDriveNotice.kt
- A · app/src/release/java/com/example/ui/BuildVariantSettingsEntry.kt
- M · app/src/test/java/com/example/data/Phase10BPortfolioAcceptanceTest.kt
- M · app/src/test/java/com/example/ui/Phase5BComposeWorkflowTest.kt
- A · app/src/testDebug/java/com/example/api/Phase11ADriveNamespaceTest.kt
- A · app/src/testDebug/java/com/example/qa/Phase11AIsolationTest.kt
- A · app/src/testDebug/java/com/example/qa/Phase11APortfolioSeederTest.kt
- A · docs/PHASE11A_PRACTICAL_ACCEPTANCE.md
- M · docs/VERIFY_CHANGES.md

## Dritte Integrations-CI: Testfixture-Synchronisierung

CI 40261085185 auf 9caefc2 bestand die Zertifikat-/APK-Prüfung und alle
Reparatur-/Portfolio-Wächter. Von 1.166 ausgeführten JVM-Methoden scheiterte
eine bestehende Compose-Navigation: Phase5BComposeWorkflowTest fand die
erwartete Bankzeile ledger_bank_bank-a nicht. Gesamt-CI rot, Emulator abhängig
übersprungen, kein vollständiger Abnahmenachweis.

Die Testvorbereitung wartete nur auf Belege und Mietzuordnungen. Die Bankzeile
entsteht jedoch erst mit dem separat asynchron gelieferten Bankbuchungs-Flow.
Die QA-Korrektur wartet nun auf die konkreten Fixture-IDs aller drei Flows und
prüft die erwartete fachliche Bankzeile zusätzlich vor der Navigation. Die
bisherige 10-Sekunden-Frist, Metrik-/Filter-/Bankzeilen-/Buchungsdetail-/Zurück-
Assertions bleiben erhalten. Keine Bank-/Produktlogikänderung. Lokal alle acht
Methoden dieser Klasse grün, ohne Skips.


## Auswertung technischer Unterschiede und Restrisiken

Der unveränderte strikte Checker unterscheidet fachliche Contract-Abweichungen
von RAW-Differenzen. Beim frischen Full-Restore sind acht zusätzliche gültige
Legacy-Einheitenalias-Schlüssel sowie neue lokale URI-/Sync-Revisions-/Zeitwerte
sichtbar. Diese Unterschiede bleiben vollständig im JSON-Nachweis erhalten;
0 Contract-Abweichungen bedeutet ausdrücklich keine Bytegleichheit aller
lokalen Preferences oder technischen Transportfelder. Es wurden keine
bestehenden Prüferregeln gelockert. Fachliche IDs, bestätigte Zuordnungen,
Beträge, Flächen, Mieten, Datumswerte und Originalbytes werden geprüft.

Die Reparatur verhindert den erneut reproduzierten Verlust; sie beweist nicht,
dass vorhandene reale Bestände fehlerfrei sind. Alte Dokumente mit fehlender
unitId dürfen nicht anhand der Belegzuordnung automatisch umgebucht werden.
Zunächst den lesenden Integritätsaudit verwenden, auffällige IDs mit Originalen
und Zuordnungshistorie belegen, eine Datenreparatur gesondert freigeben.
Keine Bereinigung des alten Produktroots und kein automatisches Backup-Rückspielen.

Die lokale unsigned Release-APK ist kein auslieferbares signiertes Update.
Produktive Signierschlüssel wurden nicht gelesen oder übernommen. Signierte
Release-Schritte sind bei fehlender CI-Konfiguration übersprungen; keine
Testmethoden werden deshalb als ausgeführt gezählt.

## Praktische Prüfungen und Freigabeabhängigkeit

| Prüfung | Status |
| --- | --- |
| Echter Google-Drive-Transport und OAuth auf dem Handy | NOCH OFFEN |
| Zweites echtes Gerät oder getrenntes Android-Profil | NOCH OFFEN |
| Echter Systempicker und externer PDF-/Bildviewer | NOCH OFFEN |
| Echter Offline-Restore und kontrollierter Restore-Abbruch | NOCH OFFEN |
| Vollständiger manueller Android-Portfolioablauf | NOCH OFFEN |
| Prozessneustart nach echtem Cloud-Restore | NOCH OFFEN |

Ein frischer JVM-Context/Room-Restore ersetzt keinen tatsächlichen Prozess-Kill.
Der Emulator-UI-Seed-Neustart ist ein eigener automatisierter Nachweis; er
belegt keinen praktischen Cloud-Rundlauf. Die Produktreparatur ist noch nicht
in main und nicht auf produktiven Geräten installiert. Phase C bleibt gemäß
Auftrag vorläufig auf eigenem Integrationsbranch, bis die Produktreparatur
freigegeben wurde. Bestehender PR #143 bleibt Draft auf 8ddd556; kein Merge.

Neue Produktbefunde im Reparaturauftrag: P0/P1/P2/P3 = 0/0/0/0 beobachtet.
Die früher dokumentierten 2 P1 und 1 P2 sind gezielt repariert und automatisiert
geprüft, aber noch nicht produktiv ausgeliefert. Fehlgeschlagene QA-/Infrastruktur-
läufe sind oben aufgeführt und gelten nicht als bestandene Abnahmen.

**KEINE PRAKTISCHE PORTFOLIOFREIGABE.** Die automatisierten Ergebnisse erlauben
die Codeprüfung der Reparatur und die Vorbereitung isolierter realer Tests.
Eine uneingeschränkte produktive Empfehlung für den noch unveränderten main-
Driveworkflow ist aus den ursprünglichen Befunden weiterhin nicht ableitbar.
Kein PR wurde gemergt; vor jedem Merge wird gestoppt.
