# Phase 11E – Prüf- und Performancebericht

Base: `5d5e61437fcf7324bb5e0f6442669f777d8ddeda`. Branch: `fix/phase11e-drive-sync`. Messdatum: 2026-11-01.

**Praktische Android-16-Abnahme OFFEN. Kein Merge und keine APK-Veröffentlichung freigegeben.**

## Tatsächlich ausgeführte lokale Prüfungen

| Prüfung | Ergebnis |
|---|---|
| Neue Phase11E-HTTP-/Room-/ViewModel-Regressionen | 43/43 bestanden |
| Bestehende Namespace-, Login-, Restore-Vorschau-, Preservation- und Phase10A/10D-Tests | 107/107 bestanden |
| Weitere Backup-/Dokument-/Bank-/Immobilien-/DATEV-/AfA-/Fahrtenbuch-/Mietdaten-Regressionen, 21 Klassen | 117/117 bestanden |
| Unveränderte Base mit separatem 20-ms-Transport | Fehler 0/22 reproduziert; eigene Baseline-Assertion 1/1 bestanden |
| Direkte Kotlin-/Compose-Kompilierung | Geänderte Produktklassen gegen SDK36 kompiliert |
| Room-KSP | Zwei tatsächlich neu erzeugte DAO-Implementierungen kompiliert; Schemahash unverändert |
| `git diff --check` | Bestanden |
| Lokales Gradle / lintDebug / assembleDebug | Extern blockiert: FileLockContentionHandler, kein nutzbares wildcard IP |
| Lokaler nativer Emulator | Nicht ausgeführt, `/dev/kvm` fehlt |
| GitHub verify / android-emulator-e2e | Ergebnisse mit Run-ID und geprüftem Commit in der PR-Beschreibung dokumentieren |
| POCO X8 Pro / Android16 / echte Google Drive API | Nicht durchgeführt; neuer Lauf und kopierter Gerätebericht erforderlich |

Das sind **267 bestandene Produkt-/Regressionstests**, zuzüglich einer unabhängigen Baseline-Reproduktion. Ergänzende lokale Robolectric-Ausführung verwendet ausschließlich externe SDK35-Testkopien, Java21 und den vorhandenen Cache. Die eingecheckten SDK36-Annotationen bleiben unverändert. Dies ersetzt keine vollständige Gradle-/SDK36-/Emulatorprüfung. Im ergänzenden SDK35-Lauf traten Fontconfig-Cache-Warnungen und bei bestehenden ViewModel-Test-Teardowns Ausgaben bereits geschlossener In-Memory-Datenbanken auf; keine Assertions wurden abgeschwächt oder deaktiviert. Native CI ist gesondert auszuwerten.

Der erste breitere Lauf hatte vier echte Statusregressionen: Bei nicht erreichbaren Dokumentmetadaten wurde vorzeitig zurückgegeben, bevor der bisherige lokale Fehlerstatus gesetzt wurde. Der Nullpfad wurde gezielt korrigiert. Der erneute komplette 117-Test-Lauf besteht einschließlich unveränderter 404-/Timeout-/429-/503-Assertions und fehlender Ersatz-Uploads. Weitere vor ihrer Korrektur tatsächlich rote neue Regressionen sind in der Ursachenanalyse beschrieben.

## Lokale synthetische Messungen

Identische synthetische HTTP-Latenz von 20 ms je Aufruf; kleine erzeugte Originaldateien; vorbereiteter Ordnerbestand. Ausschließlich JVM/Robolectric und Antworten im Speicher. Keine realen Google-/Mobilfunk-/Emulator-Messwerte. Die nachfolgenden Werte sind einzelne reproduzierte Läufe, keine statistische Latenzgarantie.

| Szenario | Ergebnis | Dauer ms | API | list | get | generateIds | create | update | Originalanlagen/-Updates |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Base, 22 unverändert | Fehler, 0/22 | 21436 | 1044 | 200 | 835 | 0 | 0 | 9 | nicht als Erfolg bewerten |
| Repariert, 22 unverändert | Erfolgreich, 22 unverändert | 4077 | 258 | 32 | 226 | 0 | 0 | 0 | 0 / 0 |
| Ein neuer Beleg (23 gesamt) | Erfolgreich | 10939 | 598 | 127 | 460 | 3 | 3 | 5 | 1 / 0 |
| Fünf weitere neue (28 gesamt) | Erfolgreich | 26133 | 1352 | 328 | 996 | 10 | 10 | 8 | 5 / 0 |
| Identischer Folgelauf mit 28 | Erfolgreich, 28 unverändert | 4560 | 299 | 34 | 265 | 0 | 0 | 0 | 0 / 0 |

22 unverändert: **786 vermiedene API-Aufrufe**, 75,3 % weniger als der bisherige fehlgeschlagene warme Ablauf. `files.list` sinkt von 200 auf 32, `files.get` von 835 auf 226; neun unnötige Schreibvorgänge entfallen. Der erfolgreich reparierte, noch nicht optimierte Zwischenstand hatte 2606 Aufrufe; die nachfolgenden Optimierungen vermeiden davon 2348. Der Vergleich von fehlgeschlagenem altem und erfolgreichem neuem Ablauf ist ausdrücklich gekennzeichnet.

Im unveränderten 22er- und 28er-Folgelauf: keine Originalanlagen, keine Original-Updates und keine vollständigen Downloads der bereits vorhandenen Originale. Bei einem neuen Beleg gehören neben Original und Metadaten auch eine erforderliche Ordneranlage zu den drei `files.create`-Aufrufen. ID-Reservierungen sind separat gezählt, nicht als `files.get` ausgegeben.

Das Ziel unter fünf Sekunden wird hier für 22 unveränderte Belege erreicht. **Die Ziele unter zehn Sekunden für einen neuen und unter zwanzig Sekunden für fünf neue werden in diesem Lauf nicht erreicht.** Sicherheitsprüfungen wurden nicht zur Zielerreichung abgeschaltet. Der reale Ausgangswert ist ausschließlich laut Nutzerbericht **über 300 Sekunden, fehlgeschlagen**. Ein neuer POCO-Messwert fehlt; aus den simulierten Zeiten wird keine reale Beschleunigungsgarantie abgeleitet.

Die maschinenlesbare [Evidenz](phase11e-evidence/synthetic-benchmarks.json) enthält Ausgangswerte, neue Werte, phasenweise HTTP-Zahlen und SHA256 der tatsächlich geprüften Quellen. Vollständige Einzelphasen werden durch den Benchmark erneut unter `app/build/reports/phase11e/` erzeugt und vom bestehenden CI-Bericht-Artifact erfasst.

### Aufrufe pro Hauptphase im vorbereiteten 22er-Bestand

Die Vorher-Aufteilung stammt aus Instrumentierung ohne Produktreparatur und ohne künstliche Latenz; die Gesamtzahl entspricht der separat mit 20 ms gemessenen Base. Unterphasen sind bereits in ihren Eltern enthalten. Beispielsweise liegen die 44 Metadaten-Aufrufe des neuen Ablaufs innerhalb der 95 vorgelagerten Inventar-Aufrufe.

| Hauptphase | Bisher API | Repariert API | Daten / Übertragung im neuen No-Op |
|---|---:|---:|---|
| Initialisierung | 46 | 39 | Konfiguration und Namespace-Metadaten lesen |
| Bestandsprüfung | 0 | 95 | Index/Belegmetadaten lesen, SHA/Größe/Version prüfen |
| Hauptbuch-CSV | 7 | 3 | SHA/Größe prüfen, kein Upload |
| Immobilien-/Dokumentordner | 139 | 26 | Geprüfte IDs erneut nachweisen, keine Anlage |
| Einheiten-CSV | 7 | 3 | SHA/Größe prüfen, kein Upload |
| Stammdaten | 49 | 22 | JSON vergleichen, kein Upload |
| Belegschleife | 726 | 0 | Verifizierte Leseergebnisse im selben Lauf verwenden |
| Eigenständige Dokumente | 0 | 0 | Im Szenario keine eigenständigen Dokumente |
| Dokumentindex | 35 | 4 | Vorhandenes JSON lesen/vergleichen |
| Zusatzdaten | 35 | 4 | Vorhandenes JSON lesen/vergleichen, historische Referenzen erhalten |
| Abschluss | 0 | 62 | Frische Inhaltsversionen und gruppierte Namespace-Nachweise |

Bei fehlenden Server-Checksummen werden tatsächliche Bytes heruntergeladen; diese Sicherheitsfallbacks haben andere Laufzeiten. Fehlende ETags erlauben verifizierten No-Op, aber sperren unsichere Änderungen. DNS/429/503 enden nach begrenztem GET-Budget; ein Write-Timeout wird über dieselbe reservierte ID bzw. den bekannten Vorzustand abgeglichen. HTTP-Status allein autorisiert weder neue Ordner noch das Ersetzen einer Datei.

## Geänderte Dateien und Auswirkungen

| Bereich / Dateien | Zweck |
|---|---|
| `api/GoogleDriveClient.kt`, `DriveNamespacePolicy.kt`, `DriveEnvironment.kt` | Bestehende API-/Namespace-Pfade: frische Schreibnachweise, laufgebundene Wiederverwendung, Fingerprints, begrenzte GET-Retries, reservierte Anlagen |
| `api/DriveJsonSnapshot.kt`, `DriveSyncTrace.kt` | Kleine Daten-/Diagnosehilfen innerhalb des vorhandenen Clients, JSON-Vorzustand und sichere monotone Messung |
| `data/DrivePersistenceRepository.kt` | Vorprüfung, Cloud-only-/No-Op-Pfad, getrennte Original-/Metadatenänderungen, sicherer Index-Upsert |
| `data/DriveSyncFailurePolicy.kt`, `DrivePendingWrites.kt`, `DriveBackupConflictGuard.kt`, `DriveSyncReceiptState.kt` | Netzbudget, atomare lokale Wiederaufnahmefakten, Konfliktschutz, Vergleich fachlicher Daten |
| `data/ReceiptDatabase.kt`, `DocumentModels.kt` | Ausschließlich Drive-Felder atomar bestätigen; konkurrierende fachliche Änderungen bewahren; keine Schema-/Versionsänderung |
| `data/SupplementalDriveBackup.kt`, `ManagedDocumentDriveGateway.kt` | Eigene historische Referenzen erhalten, Kontroll-/Netzfehler weitergeben, vorhandenen Indexpfad nutzen |
| `ui/ReceiptViewModel.kt`, `ReceiptAppUi.kt` | Gemeinsame Sperre, ehrliche Zähler und Sicherungszeit, ausschließlich Drive-Karte mit kopierbarer Diagnose und Abbruch |
| `test/.../Phase11ESyncRegressionTest.kt`, `ComparisonDriveHttpFixture.kt` | 43 synthetische Vertrags-/Fehler-/Benchmarktests, kontrollierte SHA/ETag/Version/ID-Antworten |
| `docs/PHASE11E_DRIVE_SYNC.md`, dieser Bericht, synthetische JSON-Evidenz | Ursachen, Aufrufketten, Messergebnisse, Pflichtmatrix, offene Risiken und echte Geräteanleitung |

Keine Änderungen an Bank-/DATEV-/AfA-/Fahrtenbuch-Fachlogik, Mietdatenmodellen, globalem Header oder Bottom Navigation. Backup/Restore behält bestehende IDs und Verweise; Synchronisierung führt kein automatisches Restore aus. Original-App und isolierte Test-App werden nicht vermischt. Die 117 angrenzenden Tests prüfen insbesondere Supplemental-Roundtrip, Banklinks/Splits/Lernregeln, Portfolio-/Mietdaten, DATEV-Exportregeln, bestätigte AfA und Fahrtenbuch; die 107 Drive-Tests prüfen zusätzlich Anmeldung, Namespace und Restore.

## Erster vollständiger CI-Lauf und Datumsregressionen

Run [41269739084](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/41269739084), PR-Head `67583c105afb21a33251fde9b2f7605d634dc4fb`, tatsächlich ausgecheckter Test-Merge `6ebd38d02ca9e00909ccda5c1b1291e875d5de52`. Beide Trees wurden gegen `2250c8b97ad279f248f462d959dc167938dda12e` geprüft und stimmen überein.

- Lint bestanden, tatsächliche Kotlin-/KSP-/Test-Kompilierung bestanden.
- 1288 Methoden tatsächlich ausgeführt: 1286 bestanden, zwei vorhandene Mietübersichts-UI-Tests fehlgeschlagen. Keine übersprungenen Tests/Platzhalter. Der Folge-Audit schlägt wegen dieser zwei Fehler ebenfalls korrekt fehl.
- **Alle 43 Phase11E-Tests auf eingechecktem SDK36 bestanden**, XML `tests=43, failures=0, errors=0, skipped=0`, 71,237 Sekunden für die gesamte Klasse. Dies ist keine echte Google-/POCO-Messung.
- `verify` insgesamt fehlgeschlagen; `android-emulator-e2e` und anschließender APK-Build wurden übersprungen und gelten ausdrücklich nicht als bestanden.

Beide UI-Fehler sind auch auf unveränderter Base lokal mit denselben Assertions reproduziert. Ursache ist der Monatswechsel auf November 2026: `Phase5BComposeWorkflowTest` erwartete einen Oktober-Zahlungseintrag, ohne den Standardmonat des Dialogs einzustellen; `RentOverviewComposeTest` klickte pauschal fünf Monate zurück und erwartete Mai (funktionierte im Oktober, landet im November bei Juni). Die Korrektur wählt den jeweiligen Fixture-Monat über die vorhandenen UI-Pfeile und prüft den angezeigten Zielmonat. Die anfängliche Auswahl wird aus dem tatsächlich angezeigten Dialog gelesen, ohne Systemzeit zu verändern. Alle bisherigen Zahlungs-/Navigations-/Persistenz-Assertions bleiben erhalten. Keine Produkt- oder Finanzlogikänderung und keine pauschalen Timeout-Erhöhungen.

Zusätzlich geändert: ausschließlich `test/ui/Phase5BComposeWorkflowTest.kt`, `RentOverviewComposeTest.kt` und gemeinsamer Testhelfer `MonthlyRentFixtureNavigation.kt`. Beide korrigierten Methoden wurden lokal einzeln ausgeführt und bestehen (zusätzlich 2/2). Der breite zusätzliche lokale UI-Lauf wurde nach per Thread-Dump belegtem Hängen in `RentOverviewComposeTest.editingSameNamedUnitInAnotherPropertySavesOnlyItsScopedPlan` / `performScrollToNode` abgebrochen; er gilt nicht als bestanden. Die vollständigen Klassen müssen erneut durch die reguläre CI. Der rote Lauf wird nicht durch blindes Wiederholen als grün dargestellt.

## Noch offen

- Tatsächlich ausgewertete vollständige GitHub-CI/SDK36/Instrumentation: Ergebnis und Run-IDs in der PR nachtragen, Fehler gesondert klassifizieren.
- Reale Google-Backend-Eigenschaften und Android16-/HyperOS-Verhalten, echte No-Op-Zeit und erfolgreiche Synchronisierung auf dem POCO.
- Ein-/Fünf-Neubeleg-Zielzeiten sind lokal noch verfehlt; größere Bestände und große Dateien nicht als vermessen darstellen.
- Reale neue Diagnose muss den zuletzt auf dem POCO fehlgeschlagenen Teilschritt bestätigen. HyperOS-Akkueinstellung ist nur eine Testbedingung.

Die [Geräteanleitung](PHASE11E_DRIVE_SYNC.md#reproduzierbarer-poco-x8-pro-test-android-16--hyperos) fordert ausdrücklich einen neuen echten Messwert und den kopierten Bericht an. Praktische Abnahme bleibt **OFFEN**; kein automatischer Merge, keine APK-Veröffentlichung ohne gesonderte Freigabe.
