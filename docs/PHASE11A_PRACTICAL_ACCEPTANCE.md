# Phase 11A – praktische Android-/Google-Drive-Abnahme

## Basis, Umfang und aktueller Status

Base-SHA: `69f35772b6480a958ca7479b024dbd4d8bd42fa1`, nach erfolgreichem `git fetch origin` am 20.10.2026 bestätigt, am 21.10.2026 erneut unverändert geprüft. Branch: `test/phase11a-practical-device-drive-acceptance`. Ausgangsbranch `work`, Arbeitsbaum vor Beginn sauber. Vorbereitungs-ID: `P11A-2026-10-20-01`; diese bezeichnet **keinen durchgeführten Gerätetest**.

Gelesen: AGENTS.md, PROJECT_STATUS.md, CODE_MAP.md, VERIFY_CHANGES.md und PHASE10D_RESTORE_METADATA_CONTRACT.md; analysiert: Phase10BPortfolioAcceptanceTest, RestoreContractSnapshot, SupplementalDriveBackup, DrivePersistenceRepository, bestehende Android-Teststrategie. `Immopilot_Work_Arbeitsanweisungen.md` und `Phase10B_Portfolio_E2E_Abnahme.md` fehlen im Checkout und in bereitgestellten Arbeitsordnern. Fundort angefragt; nicht als gelesen gewertet. Auch Phase 10D dokumentiert den fehlenden separaten 10B-Bericht. Die vorhandenen Tests und Originalartefakte bleiben die verfügbare Testbasis.

Kein Produktcode in `app/src/main` geändert. Keine Banklisten-, DATEV-, Drive-Architektur- oder Migrationsänderung. Der QA-Runner erweitert AndroidJUnitRunner, prüft zuerst das isolierte Debug-Package und delegiert danach normale Läufe an dessen Implementierung. Evidence benötigt `phase11aAction`. Der unveränderte Phase-10D-Vertragshelper wird aus `src/qa` für JVM- und Android-Tests gemeinsam kompiliert; er gelangt nicht ins App-APK. Die Buildkonfiguration enthält außerdem die unten beschriebene Debug-Isolation.

**Entscheidung: KEINE PRAKTISCHE PORTFOLIOFREIGABE.**

| Test-ID | Zweck | Status |
| --- | --- | --- |
| P11A-DRIVE-01 | Echter Cloudtransport, zweimal Backup/Restore | NOCH OFFEN |
| P11A-DEVICE2-01 | Unabhängiges zweites Gerät/Profil | NOCH OFFEN |
| P11A-PICKER-01 | Echter Systempicker, fünf Formate | NOCH OFFEN |
| P11A-VIEWER-01 | Externer Viewer, PDF/JPG/PNG | NOCH OFFEN |
| P11A-OFFLINE-01 | Offlinefehler, Bestandserhalt, Retry | NOCH OFFEN |
| P11A-INTERRUPT-01 | Echter Restore-Abbruch und Retry | NOCH OFFEN |
| P11A-MANUAL-01 | Gesamter Portfolio-/Navigationsablauf | NOCH OFFEN |
| P11A-RESTART-01 | Recent Apps, vollständiger Prozessneustart | NOCH OFFEN |
| P11A-RECREATE-01 | Drei Screens, Recreate/Prozesswiederherstellung | NOCH OFFEN |

| Pflichtfeld | Gerät A | Gerät/Profil B |
| --- | --- | --- |
| Modell | NOCH OFFEN | NOCH OFFEN |
| Android-Version | NOCH OFFEN | NOCH OFFEN |
| App-Version | NOCH OFFEN | NOCH OFFEN |
| Commit | NOCH OFFEN | NOCH OFFEN |
| Android-User / Gerätealias | NOCH OFFEN | NOCH OFFEN |
| APK-SHA256 / Installationsquelle | NOCH OFFEN | NOCH OFFEN |

Keine echten Geräte-/Drive-Schritte durchgeführt. IDs, Counts, Orphans, Hashes, Datenverluste und alle praktischen Ergebnisse: **NOCH OFFEN**. Neue bestätigte Produktbefunde P0=0, P1=0, P2=0, P3=0; kein Beweis praktischer Fehlerfreiheit. Keine alten E10D-Befunde neu zählen.

## Isolierte Installation – Folgeauftrag zu PR #143

Startprüfung am 21.10.2026: Arbeitsbaum sauber, bestehender Arbeitsbranch; `git fetch origin` und expliziter Main-Fetch erfolgreich. Tatsächlicher `origin/main`/Base: `69f35772b6480a958ca7479b024dbd4d8bd42fa1`; tatsächlicher PR-Ausgangshead: `9ba32bc77e547aff9cab3c9d81f3da9c44fdc37d`. PR bleibt Draft. AGENTS.md und diese Anleitung erneut gelesen; `Immopilot_Work_Arbeitsanweisungen.md` weiterhin nicht vorhanden. Finale Commit-/CI-Zuordnung im versionsgebundenen PR-Bericht und in den generierten Evidence-Dateien.

| Merkmal | Produkt / Release | Phase-11A-Debug |
| --- | --- | --- |
| applicationId | `com.aistudio.steuerassistent.vnhqkp` | `com.aistudio.steuerassistent.vnhqkp.phase11a` |
| Launcher-/App-Name | `Steuer-Assistent Anlage V` (bisheriger Name unverändert) | `ImmoPilot Test` |
| FileProvider | `com.aistudio.steuerassistent.vnhqkp.provider` | `com.aistudio.steuerassistent.vnhqkp.phase11a.provider` |
| QA-Test-APK | kein produktiver Bestandteil | `com.aistudio.steuerassistent.vnhqkp.phase11a.test` |

Umsetzung: ausschließlich Debug-`applicationIdSuffix = ".phase11a"` und Debug-Manifest-/Label-Ressource. Keine zusätzliche Build-Matrix; Release-DSL und Hauptmanifest unverändert. `namespace = com.example` bleibt der Kotlin-/Ressourcen-Namespace und ist **nicht** die Android-App-Identität.

Room (`receipt_database`), sämtliche SharedPreferences, Android-Keystore, interne Dateien/Cache und app-spezifische externe Verzeichnisse werden vom jeweiligen App-Context/UID abgeleitet. Keine hart codierten produktiven internen Pfade und keine DataStore-Verwendung in den untersuchten Produktionsquellen. `provider_paths.xml` veröffentlicht ausschließlich den eigenen Cache und `files/managed_documents/`, nicht beliebige fremde Datenverzeichnisse. FileProvider-Aufrufer verwenden `context.packageName + ".provider"`. Weitere zusammengeführte Provider (MLKit, Firebase, AndroidX Startup) verwenden die applicationId. Kein gemeinsamer `sharedUserId`; kein eigener DocumentsProvider oder WorkManager-Initializer. Der vollständige Binärnachweis wird als `phase11a-isolation/apks.json` erzeugt.

Das Hauptmanifest besitzt nur MAIN/LAUNCHER für MainActivity: keine eigenen Deep Links, empfangenden Share- oder File-Open-Filter. Das zusammengeführte Manifest enthält zusätzlich Firebase-Bibliotheksfilter für `genericidp://firebase.auth/` und `recaptcha://firebase.auth/`. Damit die Test-App diese gemeinsamen Browser-Callbacks nicht neben der Produkt-App beansprucht, setzt ausschließlich das Debug-Overlay `GenericIdpActivity`/`RecaptchaActivity` auf `exported=false` und entfernt dort deren Intent-Filter. Firebase-Browser-Provider-/Recaptcha-Callbacks sind damit in dieser Testinstallation nicht extern nutzbar; sie sind kein Bestandteil der bestehenden Drive-Play-Services-/WebView-Anmeldewege. Kein Release-Manifest-Eingriff. Ausgehende Import-/Öffnen-/Share-Intents bleiben unverändert und verwenden App-Context, MIME und temporäre URI-Grants. Beide Launcher-Einträge sind durch unterschiedliche Labels eindeutig. Externer Picker/Viewer bleibt ein **NOCH OFFENER** Praxistest. Exportierte Bibliotheks-/Debug-Komponenten und Permissions werden vollständig aus den APKs erfasst; der APK-Checker verweigert externe BROWSABLE-Callbacks der Test-App.

### Installation und Guard

Für echte Geräte **nur** das Artefakt `phase11a-isolated-apks` des freigegebenen CI-Laufs verwenden: App-APK, passendes QA-APK und `apks.json` gehören zusammen und haben dieselbe Signatur. Das allgemeine `app-debug`-Artefakt stammt aus einem anderen CI-Job mit eigenem Debug-Schlüssel und darf nicht mit dem QA-APK gemischt werden. Alternativ beide APKs gemeinsam lokal mit demselben verwalteten Debug-Schlüssel bauen. APK-SHA256 und öffentliche Zertifikats-SHA1/SHA256 stehen in `apks.json`; vor Installation gegen die heruntergeladenen Bytes prüfen. Keine Schlüssel oder Tokens einchecken.

```bash
gradle assembleDebug assembleDebugAndroidTest
adb -s SERIAL_A install -t /path/app-debug.apk
adb -s SERIAL_A install -t /path/app-debug-androidTest.apk
adb -s SERIAL_A shell pm list packages -U com.aistudio.steuerassistent.vnhqkp
adb -s SERIAL_A shell am start -n com.aistudio.steuerassistent.vnhqkp.phase11a/com.example.MainActivity
```

Bei bereits belegter Test-App keine Installation überschreiben, Daten löschen oder umbenennen: separaten sicheren Testlauf planen. Die normale App wird für echte Gerätetests weder ersetzt noch zurückgesetzt. Sämtliche Seed-/Snapshot-/Force-stop-Kommandos beziehen sich ausschließlich auf `.phase11a`.

`Phase11AIsolation.requireTestPackage` läuft im Runner vor jedem QA-/JUnit-Einstieg und nochmals vor dem datenbereinigenden Emulator-Setup. Produkt-ID führt zu genau:

```text
Phase 11A evidence/seed is not allowed on the production applicationId.
```

Kein Öffnen einer Room-Datenbank, kein Lesen/Schreiben von QA-Dateien oder Preferences durch diesen Pfad. Unbekannte Packages und nicht debuggable Builds werden ebenfalls abgewiesen. Der Host-Helfer prüft das tatsächlich installierte Instrumentation-Ziel vor dem Übertragen der Seed-ZIP und erfasst die tatsächliche applicationId im Snapshot/Provenienzbericht. Bei ausschließlich installierter Produkt-App: harter Abbruch, kein Fallback. Normale E2E-Testbereinigung bleibt ausschließlich auf dem ausdrücklich freigegebenen isolierten Wegwerf-Emulator erlaubt, niemals auf Bestandsgeräten.

### Automatisierter Parallel-/Sandbox-Nachweis

CI baut mittels ausdrücklich übergebenem `-I .github/scripts/phase11a-reference.init.gradle` einmal eine Debug-Referenz mit unveränderter Produkt-ID und Produktlabel, danach ohne Init-Script die isolierten App-/Test-APKs. Das Init-Script wird nicht vom normalen Build geladen; es erzeugt keinen neuen BuildType. Es verlangt `GITHUB_ACTIONS=true` und erlaubt ausschließlich die beiden Assemble-Tasks, keine Installation/Deinstallation/Connected-Tests. Referenz-APKs werden **nicht** als Geräte-Testinstallation veröffentlicht. Ihr QA-Runner enthält dieselbe Produktionssperre.

`phase11a-sandbox-check.py prepare` verlangt GitHub Actions, Emulator-Hardware, User 0 und ein **noch nicht installiertes** Produkt-Package. Es installiert die Referenz ohne `-r`, startet die echte MainActivity und legt vor der Baseline ausschließlich synthetische Referenz-Sentinels an. Kein `pm clear`, keine Deinstallation, keine DB-/Preferences-/Dateilöschung. Room/Dateien/Preferences und Cache-Sentinel werden gehasht. Echte Instrumentation-Aufrufe `seed` und `snapshot` gegen die Referenz müssen mit obigem Guard-Fehler abbrechen und alle gespeicherten Referenz-Bytes unverändert lassen.

Nach dem vorhandenen Portfolio-Smoke prüft `verify` beide installierten Packages, unterschiedliche UID und Datenverzeichnisse. Der isolierte Probe-Einstieg prüft echte Room-Integrität und 5 Objekte/70 Belege, eigene Preferences und Dateien/Cache/externe Verzeichnisse sowie verweigerten Zugriff auf existierende Referenzdateien, Referenz-DB und Referenz-FileProvider. Die Referenz bleibt bei 0 Objekten/0 Belegen und ihren ursprünglichen Preference-/Dateiwerten. Nach sämtlichen bisherigen E2E-Tests vergleicht `final` erneut alle Referenz-Hashes. Ergebnisse: `phase11a-isolation/baseline.json`, `sandbox.json`, `apks.json`, Screenshot und vorhandene Emulator-Diagnostik. Bis zum tatsächlich beendeten CI-Lauf gilt der neue Emulatornachweis als **NOCH OFFEN**; der PR-Bericht nennt danach die tatsächlichen Resultate.

### Google Drive / OAuth: vor echtem Gerätetest konfigurieren

**Direkte Nutzbarkeit der neuen Test-App mit echtem Drive: NOCH OFFEN, nicht bestätigt.** Lokale Sandbox-Isolation trennt keine Cloudkonten. Ausschließlich synthetische Portfolios und möglichst ein separates Google-Testkonto verwenden; Zugriff auf produktive Drive-Dateien weder voraussetzen noch testen.

Die alternative Play-Services-Anmeldung in `ReceiptAppUi.kt` verwendet `GoogleSignInOptions.DEFAULT_SIGN_IN`, E-Mail und Scope `https://www.googleapis.com/auth/drive.file`. Android-OAuth-Zulassung hängt an **Package plus Signatur-SHA1**, nicht am Kotlin-Namespace. Ein Android-Client für die Produkt-ID deckt `.phase11a` nicht ab. In den verfügbaren Quellen gibt es keine `google-services.json` und keinen überprüfbaren Console-Nachweis für das neue Package.

Erforderliche manuelle Prüfung/Einrichtung im zugehörigen Google-Cloud-Projekt:

1. Android-OAuth-Client für exakt `com.aistudio.steuerassistent.vnhqkp.phase11a` und die **SHA1 des tatsächlich installierten App-APK-Zertifikats** aus `apks.json` anlegen/registrieren. Bei lokalem Build SHA1 des verwendeten lokalen Debug-Zertifikats verwenden. CI erzeugt pro Job/Lauf einen neuen Schlüssel; ein Client für einen früheren APK-Fingerprint reicht daher nicht. Für dauerhafte Gerätetests einen stabilen verwalteten Testsignierschlüssel nutzen, ohne ihn zu committen.
2. Drive API im selben Projekt aktiv prüfen. OAuth-Zustimmungsbildschirm, `drive.file`-Scope und separates Testkonto als Testnutzer bei App-Status „Testing“ konfigurieren. Bestehende Freigaben sind hier nicht einsehbar, daher nicht als fehlend bewiesen oder erledigt markieren.
3. Falls Firebase/Google-Services-Konfiguration verwendet wird: separate Android-App mit der Test-ID und Test-SHA1 registrieren; passende Konfiguration ausschließlich für Debug bereitstellen, z.B. lokal `app/src/debug/google-services.json`. Keine Produktionskonfiguration ersetzen, keine Secrets committen. Das native Sign-in benötigt für die Package-/Zertifikatsregistrierung keinen Produktcode-Umbau.

Die Standard-UI besitzt außerdem einen bestehenden WebView-OAuth-Weg mit Web-Client und Firebase-Redirect. Dieser Weg ist nicht wie ein Android-Client an die applicationId gebunden, verlangt aber weiterhin gültige Web-Client-/Redirect-/Consent-Konfiguration und Googles Zulassung des tatsächlichen Anmeldewegs. Ein manueller Access-Token-Weg ist ebenfalls vorhanden. Keiner dieser Wege wurde live authentifiziert; keiner ersetzt einen echten Transport-/Restore-/Persistenznachweis. Keine OAuth- oder Drive-Architekturänderung in diesem Auftrag.

Für `P11A-DRIVE-01`: isolierte Test-App technisch vorbereiten und Sandbox-Nachweis abschließen; dann Testsignatur/OAuth-Zulassung und Testkonto real verifizieren. Bis dahin **keine Drive-Bereitschaft oder praktische Drive-Freigabe** behaupten.

## Testbasis und sichere Vorbereitung

Normalportfolio: fünf Objekte / 17 Einheiten; Stressportfolio: fünf Objekte / 21 Einheiten. Beide aus den vorhandenen Phase-10B-Tests, jeweils 120 Bankbuchungen, 60 Originaldateien, 70 Belege und 70 zugehörige Dokumentdatensätze (zehn Fahrtbelege ohne Originaldatei), zehn Fahrten, ein Darlehen, AfA, Sanierung, Mietverläufe, sechs Mietzuordnungen, ein Bankbeleglink, 100 DATEV-Auditläufe und stabile IDs. Keine neue Ersatzfixture als Praxisbeweis.

Ausschließlich synthetische Testdaten in bewusst gewählter, separater Debug-Testinstallation. Keine produktive Installation löschen, überschreiben oder aktualisieren. Kein Helfer führt `pm clear`, Deinstallation, destruktive Migration oder automatisches Zurücksetzen aus. Nichtleere Seed-Ziele werden abgewiesen; dann frische Testumgebung wählen, keine Sperre umgehen. Seed vor erstem App-UI-Start in leerer Installation aufrufen.

1. Aus grünem CI-Artefakt `android-reports` das Verzeichnis `phase10b/units-17` bzw. `units-21` wählen. `qa-transport-snapshot.zip`, `.sha256`, `hash-manifest.json`, CI-Run-ID und Commit sichern. ZIP ist QA-Transport, kein echter Drive-Nachweis. Für die reine DATEV-Öffnungsprüfung zusätzlich die bestehenden Dateien `phase10b/datev/batch-120.csv`, `batch-120.zip` und `export-hashes.json` sichern; diese separate Exportfixture nicht als 70-Belege-Portfolio ausgeben.
2. Debug-APK und androidTest-APK aus demselben Phase-11A-Commit bauen: `gradle assembleDebug assembleDebugAndroidTest`; nur auf isolierter Testinstallation installieren. QA-Instrumentation steckt ausschließlich im Test-APK. **Keinen vollständigen `connectedDebugAndroidTest` auf Bestandsgeräten starten**: vorhandene Emulator-Fixtures bereinigen ihre Testdaten.
3. `adb devices -l` und `adb -s SERIAL shell am get-current-user` prüfen; Seriennummer und Android-User explizit wählen. Helfer wechselt das Profil nicht selbst. Gerät B bevorzugt echt, getrenntes Vordergrund-Testprofil nur ersatzweise mit dokumentierter Begründung.
4. Run-ID eindeutig vergeben, z.B. `P11A-2026-10-21-01`. Stressportfolio eigener Lauf, z.B. `…-02`. Evidence lokal außerhalb Git ablegen. Bestehende Snapshot-/Evidence-Dateien werden nicht überschrieben.

```bash
python3 .github/scripts/phase11a-init-run.py \
  --run-id P11A-2026-10-21-01 --commit APP_COMMIT_40_HEX --units 17 --output /tmp/p11a

python3 .github/scripts/phase11a-device-evidence.py seed \
  --serial SERIAL_A --user 0 --run-id P11A-2026-10-21-01 --label setup \
  --commit APP_COMMIT_40_HEX --output /tmp/p11a --test-portfolio-only \
  --fixture /path/qa-transport-snapshot.zip --fixture-sha256 TRUSTED_SHA256 \
  --confirm-empty-test-installation
```

Platzhalter durch echte geprüfte Werte ersetzen. Commit ist die Provenienzangabe des Prüfers; der Helfer kann den Git-Commit nicht aus der APK beweisen und erfasst zusätzlich den tatsächlichen APK-SHA256. Gerätedaten lokal sichern, Seriennummer vor Weitergabe pseudonymisieren. Kein automatischer Upload der Evidence, keine Tokens/Schlüssel/kompletten Preferences loggen.

Der Seed prüft leere Daten/Einstellungen, ZIP-Hash, Portfolioherkunft und 60 Originalhashes. Er erhält fachliche IDs und entfernt Fake-Drive-Verweise der Unit-Fixture. Das ist Vorbereitung, kein bestandenes Restore. Scheitert die Vorbereitung, keine Daten bereinigen; Ursache dokumentieren und frische Testumgebung verwenden.

## Baseline und gleicher After-Export

Vor dem ersten manuellen Test nach ruhendem Bestand `phase11a-baseline.json` aufnehmen. Sie enthält RAW, vollständige Receipt-/Document-ROOM-RAW-Zeilen sowie Beleg-/ReceiptEntity-Kopien mit eigenen IDs, denselben Phase-10D-Vertrag, Mietvertrags-IDs mit Objekt-/Einheiten-Scope, AfA mit vorhandenen Objekt-IDs, Counts ableitbar aus vollständigen Datensätzen sowie physisch gelesene Originalbytes als SHA256/Größe/Lesbarkeit. Keine erfundenen neuen fachlichen AfA-IDs. Snapshot nicht während Restore/Migration erfassen; er ist eine ruhende Bestandsaufnahme, keine atomare parallele Multi-Store-Transaktion.

```bash
python3 .github/scripts/phase11a-device-evidence.py snapshot \
  --serial SERIAL_A --user 0 --run-id P11A-2026-10-21-01 --label baseline \
  --commit APP_COMMIT_40_HEX --output /tmp/p11a --test-portfolio-only

python3 .github/scripts/phase11a-device-evidence.py snapshot \
  --serial SERIAL_A --user 0 --run-id P11A-2026-10-21-01 --label drive-restore \
  --commit APP_COMMIT_40_HEX --output /tmp/p11a --test-portfolio-only

python3 .github/scripts/phase11a-integrity-check.py \
  /tmp/p11a/P11A-2026-10-21-01/phase11a-baseline.json \
  /tmp/p11a/P11A-2026-10-21-01/phase11a-after-drive-restore.json \
  --units 17 --report /tmp/p11a/P11A-2026-10-21-01/P11A-DRIVE-01-integrity.json
```

Erster echter Upload erzeugt reale Drive-Datei-/Metadaten-IDs statt entfernter Fake-IDs. Deshalb zusätzlich nach vollständig beendetem Backup `phase11a-after-drive-backup.json` aufnehmen und als Restore-Referenz verwenden. Die ursprüngliche Baseline **behalten**, nicht ersetzen. Vergleich Baseline → After-Backup vollständig berichten: erwartete Transport-/Syncänderungen einzeln belegen; fachliche IDs, Counts, Beträge, Flächen, Mietdaten und alle Hashes müssen erhalten bleiben. Ein dadurch abweichender strikter Contractvergleich wird nicht grün normalisiert. Für Restore → gesicherter Stand verlangt der Vergleich gegen After-Backup null Contractabweichungen. Setup-/Uploadänderungen separat auswerten; keine ungeprüften Abweichungen unter „technisch“ verbergen.

Der Geräteexport liest nach explizitem Instrumentationstart tatsächlich persistierte Daten, keinen alten Cloudexport. Instrumentation beendet einen laufenden UI-Prozess: zuerst sichtbaren Endzustand und Neustartverhalten prüfen/fotografieren, danach Snapshot. Ein Snapshot allein belegt keinen korrekten UI-Rückkehrscreen.

Checker: fehlende/neue IDs, Dubletten, Parent-/Scopebeziehungen, Orphans, Beträge, Flächen, Miet-/Datumswerte, Dateitypen, fehlende/unlesbare Originale, Hashes, Größen und Vertragsfelder. Keine globale Zahlentoleranz; unbekannte Vertragsfelder bleiben strikt. Bestehende Phase-10D-C/D-Regeln gelten unverändert. RAW-Differenzen bleiben im Bericht. Exit 0 heißt nur **AUTOMATISIERT BESTANDEN**; Exit 1 Integritätsabweichung; Exit 2 fehlende/ungültige Evidence. Der Checker setzt praktische Ergebnisse niemals automatisch auf bestanden.

Für Picker-Zusatzdateien separaten Lauf mit genau protokollierten neuen IDs/Hashes verwenden. Der ursprüngliche Baselinevergleich bleibt strikt und weist die beabsichtigten Neuanlagen sichtbar aus; nicht als unverändert/grün ausgeben. Danach vollständig geprüften Nachimportzustand als eigene Referenz sichern und nach Neustart erneut exportieren. Für **diesen getrennten Persistenzvergleich** `--post-import-reference` verwenden: alle IDs, Werte, Relationen und Hashes müssen zwischen Nachimportreferenz und Neustart exakt gleich sein; Mindestbestand des ursprünglichen Portfolios bleibt erforderlich. Eigenständige Dokumente ohne Beleg sind zulässig, Originalketten beleggebundener Dateien bleiben vollständig. Dieser Modus bestätigt weder die beabsichtigten Importänderungen noch einen Restore gegen das Originalportfolio. Kein ID-/Feld-Allowlisting; der normale Portfolio-/Drive-Checker bleibt unverändert strikt.

## Evidence- und Protokollpflicht je Test

`phase11a-init-run.py` erzeugt `run.json` und `report.md` mit allen Test-/Gerätefeldern. Je Test Datum/UTC, Prüfer, Gerät/Android/App-Commit, Ausgangszustand, tatsächlich durchgeführte Schritte, Ergebnis, Evidencepfade, Hashvergleich und Status ausfüllen. Stati exakt: `AUTOMATISIERT BESTANDEN`, `PRAKTISCH BESTANDEN`, `PRAKTISCH FEHLGESCHLAGEN`, `NOCH OFFEN`.

Mindestens Start-, kritischen Zwischen- und Endscreenshot, zusätzlich Fehlerzustand sichern. Namen enthalten Run-ID und Test-ID, z.B. `P11A-…-p11a-drive-01-start.png`. Helferaktionen `screenshot` und `logs` mit denselben Parametern und `--label p11a-drive-01-start` bzw. `…-end` nutzen. Jeder Export erhält SHA256-Begleitdatei mit Run-ID. Gerätesnapshots bleiben bytegenau wie im Gerätelog gehasht; APK-Hash, Android-User und Gerätealias liegen separat in `.device.json`. Screenshots zeigen die echte UI einschließlich Systempicker/externem Viewer.

Logs beschränken sich auf relevante Phase11A-/AndroidRuntime-/SQLite-/Room-Ausschnitte. Vor Weitergabe auf personenbezogene Daten prüfen. Optional Schrittmarke im Log:

```bash
adb -s SERIAL_A shell log -t Phase11A 'P11A-2026-10-21-01 P11A-DRIVE-01 backup-start'
```

Backup/Restore jeweils Start-/Endzeit und Run-Schritt-ID erfassen; Wiederholungen mit `…-2` kennzeichnen. Tatsächliche Restore-ID aus `meta.restoreJournal` des Snapshots bzw. vorhandener Journal-/Diagnoseevidence übernehmen, wenn verfügbar. Nicht verfügbar → `NOCH OFFEN`. Produkt bietet nicht für jeden Backupschritt eine exponierte Backup-ID: reale Drive-Datei-ID plus Revision/modifiedTime als Cloudidentität getrennt von Run-Schritt-ID notieren, keine UUID als angebliche Produkt-ID erfinden. Pflichtnachweis fehlt → Test bleibt entsprechend offen.

## P11A-DRIVE-01 – echter Rundlauf und Idempotenz

1. Testportfolio auf Gerät A öffnen, Startzustand/Baseline sichern. Über bestehende UI mit echtem Google-Testkonto verbinden.
2. Backup starten, vollständig beenden; Zeiten/Erfolgsmeldung sichern. Tatsächliche Drive-Dateien nachweisen: Datei-ID, Name, Größe, Timestamp/modifiedTime, Revision/Backup-ID, Index/Metadaten und 60 Originale. App-Erfolgstext alleine reicht nicht. Verborgene App-Ordner gegebenenfalls über authentifizierte Drive-Dateimetadaten prüfen; ohne Zugriff Nachweis offen lassen. Keine Zugangsdaten sichern.
3. Backup zweimal; Drive-Inventar vorher/nachher vergleichen. Keine unerwarteten zusätzlichen Dateien/Metadatensätze. Snapshot `after-drive-backup` als gespeicherte Restore-Referenz und ursprünglichen Baselinevergleich aufbewahren.
4. App schließen; nur den eindeutig identifizierten synthetischen lokalen Testbestand gezielt zurücksetzen. Keinen Cloudbestand löschen. Falls kein sicherer lokaler Reset verfügbar ist, frische isolierte Testinstallation/neues Profil verwenden und abweichenden Ablauf dokumentieren. Helfer bietet bewusst keinen Löschbefehl.
5. Restore über echte Drive-UI starten und vollständig abschließen; alle Originale laden, nicht nur Vorschauen.
6. App aus Recent Apps entfernen, Prozess vollständig beenden, neu per Launcher öffnen. Beispiel für die gewählte Testinstallation: `adb -s SERIAL_A shell am force-stop --user 0 com.aistudio.steuerassistent.vnhqkp.phase11a`. PID-Wechsel und sichtbare Daten prüfen, dann `phase11a-after-drive-restore.json` aufnehmen.
7. Checker gegen unveränderte Baseline **und** gegen After-Backup-Referenz; erwartete initiale Transportänderungen getrennt berichten. Restorevergleich: alle fachlichen IDs/Counts gleich, 60/60 SHA256, keine Dubletten/Orphans, Contractabweichungen 0.
8. Zweiten echten Restore durchführen, Prozessneustart, `phase11a-after-drive-restore-2.json`; gegen Referenz und Restore 1 vergleichen. RAW/ROOM-RAW-Differenzen bei Wiederholung zusätzlich prüfen; keine stillen fachlichen Änderungen.

## P11A-DEVICE2-01 – unabhängiges Gerät/Profil

Gerät B frisch vorbereiten/installieren, Pflichtfelder ausfüllen, **keinen Seed und keine lokalen Bestandsdaten**. Leeren Zustand per Screenshot und `phase11a-after-device2-empty.json` sichern (feste Portfolio-Counts erwartbar rot). Dasselbe Test-Drive verbinden, genau das Backup von A laden, Restore und alle Originaldownloads beenden. Recent Apps entfernen, Prozess vollständig beenden, neu starten, sichtbare Counts prüfen. `phase11a-after-device2.json` gegen A-Referenz prüfen: gleiche IDs/Counts, 60/60 Hashes, keine Orphans, Contract 0. Gleiches App-APK/Commit verwenden. Zweites Gerät bevorzugen; getrenntes zweites Profil nur als begründeter Ersatz. Dieselbe App-Installation weiterverwenden genügt nicht.

## P11A-PICKER-01 – echter Android-Systempicker

Je PDF/JPG/JPEG/PNG/WEBP eine echte synthetische Originaldatei mit vorher gesichertem Dateinamen und SHA256 bereitstellen. Über App-Import echten Android-Systempicker öffnen; Provider und Auswahl fotografieren. Kein interner Testpfad oder direkter FileProvider-Serviceaufruf als Nachweis.

Je Datei Persistenz, Name, MIME, Dokument-/Beleg-ID, Objekt-/Einheitzuordnung, SHA256 und URI-Berechtigung prüfen. Vollständiger Prozessneustart, erneut öffnen, Zuordnungen/Datei prüfen. Persistierbare URI-Rechte nur behaupten, wenn Provider und App sie tatsächlich erhalten haben. Falls die App in private Dateien kopiert: private Kopie nach Wegfall des ursprünglichen Grants nachweisen und dieses Modell dokumentieren. Zusatzbestand/erwartete neue IDs gesondert erfassen; ursprünglichen strikten Vergleich und separaten `--post-import-reference`-Vergleich nach Prozessneustart sichern. Keine automatische Vollfreigabe des Imports.

## P11A-VIEWER-01 – externer Viewer

Mindestens PDF/JPG/PNG über bestehenden App-Öffnen-Intent an externen Android-Viewer übergeben. Viewer-Paket/Version, Intent-Aktion, MIME, content-URI und Read-Grant aus gezielter Activity-/Intentdiagnose dokumentieren. Inhalt tatsächlich sichtbar fotografieren, keine SecurityException/Leerdatei, Zurückweg zur App prüfen. Erfolgreicher Intentstart oder interner Viewer alleine reicht nicht.

## P11A-OFFLINE-01 – Fehler und Retry

Mit Netz verfügbares Backup und unveränderten lokalen Vorbestand sichern. WLAN und mobile Daten deaktivieren; tatsächlichen Offlinezustand nachweisen (Flugmodus bei aktivem WLAN genügt nicht). Restore über echte UI starten, erwartete verständliche Fehlermeldung mit Zeit/Logs erfassen. Kein Crash, Datenverlust oder halber inkonsistenter Bestand. Falls Cache den Restore ermöglicht, als solchen Ablauf berichten; kein erfundener Cloudfehler.

Nach Fehler Prozess neu starten, `phase11a-after-offline-failure.json` gegen Vorfehlerzustand. Netz aktivieren, Restore erneut, Prozessneustart, `phase11a-after-offline-retry.json` gegen gespeicherte Backupreferenz. Leeres Ziel nur mit eigenem leeren Vorzustand vergleichen; fester Portfolio-Checker beweist keine Leerdatenintegrität. Zwischenzustand nicht durch späteren Erfolg verstecken.

## P11A-INTERRUPT-01 – kontrollierter Restore-Abbruch

Vorbestand/Referenz und DB-Version 34 sichern, keine Migration auslösen. Echten Restore starten; tatsächlichen Fortschritt und Journalphase/Restore-ID festhalten. Während bestätigtem Download-/Restorevorgang außerhalb einer Migration kontrolliert beenden/Prozess killen, genaue Zeit dokumentieren. Kill nach Restoreende ist kein Abbruchtest. Phase nicht sicher erkennbar → `NOCH OFFEN`.

Neustart: kein Crash, DB konsistent, keine Orphans/halb importierten Eltern und Kinder. `phase11a-after-interrupt.json` und alle Abweichungen dokumentieren. Restore erneut über UI starten, Prozessneustart, `phase11a-after-interrupt-retry.json` gegen Referenz, gleiche IDs/Counts/60 Hashes und Contract 0. Unerwarteten Zwischenbestand nicht nachträglich als intakt erklären.

## P11A-MANUAL-01 – Portfolio und Navigation

Einmal komplett: Start → Immobilien → Objekt → Einheit → Mietverhältnis → Dokumente → Belege → Bank → Buchungsdetail → AfA → Fahrtenbuch → DATEV → Einstellungen → Backup/Restore. Objekt-/Einheitenwechsel, Mietverlauf, Darlehen, AfA und Sanierung gegen Snapshotwerte prüfen. Bankbuchungsliste nur testen; bestehenden DATEV-Export praktisch öffnen/prüfen, keine DATEV-Korrektur. Gesammelte Exportkopie im Run-Ordner nach Run-/Test-ID benennen und bytegenauen SHA256 sichern; ursprüngliche App-/Drive-Dateien nicht umbenennen.

Android-Zurück, App-Zurück, Bottom Navigation, Unterseiten, Dialoge und Bearbeitungsscreens je Übergang prüfen. Ohne beabsichtigte Änderung verlassen, keine unbegründeten Sprünge zur Startseite/doppelten Backstack-Einträge. Screenfolge protokollieren; Start/kritischen Detail-/DATEV-/Rückweg/Ende fotografieren. `phase11a-after-manual.json` gegen Baseline bzw. dokumentierten Vorbestand; beabsichtigte Datenänderungen mit betroffenen IDs getrennt behandeln.

## P11A-RESTART-01 und P11A-RECREATE-01

Nach jedem Restore Recent Apps entfernen **und** Prozess vollständig beenden, Launcherstart und PID-Wechsel nachweisen. Daten/Counts sichtbar prüfen, dann Snapshot: keine nur im RAM vorhandenen Entitäten, keine lazy Restore-Lücke. Instrumentationaufnahme allein ersetzt die UI-Prüfung nicht.

Mindestens Mietverhältnis, Dokumentdetail und Bankbuchungsdetail: Screen/Eingabestand aufnehmen, Hintergrund/Vordergrund, Activity-Recreate (z.B. Rotation, danach Orientierung zurücksetzen) sowie getrennt Prozesswiederherstellung im Hintergrund prüfen. `am kill --user USER PACKAGE` nur bei App im Hintergrund; tatsächlichen PID-Wechsel nachweisen. Force-stop verlangt bewussten Launcherstart und beweist keine automatische Taskwiederherstellung. Richtigen Screen/State, gespeicherte Daten, Verhalten ungespeicherter Eingaben und Crashfreiheit je Screen mit Start/Zwischen/Ende, Methode, PID/Activity und After-Snapshot dokumentieren. Ohne tatsächliche Neuinstanziierung kein bestandenes Recreate.

## Fehler und Abschluss

Neue echte Produktfehler als E11A-01, E11A-02, …: Priorität P0/P1/P2/P3, Reproduktion, Gerät, Android, Screenshot, Log, betroffene IDs, Datenverlust ja/nein, Root Cause nur falls sicher. Zunächst dokumentieren, keinen stillen Fix im Testauftrag; separate Fixphase vorschlagen. Roter automatischer Check ist zunächst ein Befund, kein automatisch auf echtem Gerät reproduzierter Produktfehler.

Abschlussbericht: Base, Branch, Commit, Draft-PR, CI-Run-ID, durchgeführte automatische und praktische Tests getrennt, offene Fälle, sämtliche Testergebnisse, IDs/Counts/Dubletten/Orphans/60 Hashes/Contractabweichungen, neue P0–P3 und genau eine Freigabeentscheidung. CI-Ergebnisse versionsgebunden im Draft-PR nachtragen; alte grüne Läufe ersetzen keine erneute Prüfung.

Technisch vorbereitete Phase darf offene Praxisfälle haben. Volle praktische Empfehlung nur bei allen echten grünen Nachweisen, 0 P0/P1, keinem Datenverlust, keinen Orphans und null vertragsrelevanten Abweichungen. Aktuell: **KEINE PRAKTISCHE PORTFOLIOFREIGABE**. PR bleibt Draft und wird nicht gemergt.

## Automatisierte Nachweise dieser Änderung

Lokal: 17 Python-Methoden ausgeführt, 0 Fehler (Integrität, Profilverwechslung, nicht vertrauenswürdige Fixture, Evidence-Überschreiben, Instrumentationfehler und Laufvorlage). Pythonquellen kompilieren, `git diff --check` grün. Der verschobene Phase-10D-Vertragshelper wurde bytegenau gegen `origin/main` geprüft.

CI führt alle vorhandenen Lint-/Unit-/Robolectric-/Ausführungswächter-/Build-/Emulatorprüfungen erneut aus. Dazu sechs Snapshotvergleiche (17/21 Einheiten × Restore 1/2/REPLACE) über dieselbe neue Capture-/Checker-Pipeline und einen expliziten QA-Runner-/Seed-/Prozessneustart-Smoke auf frischem, wegwerfbarem Emulator. Dafür wird ausschließlich das Phase-10B-Fixture-Artefakt desselben CI-Laufs verwendet; anschließend laufen sämtliche bisherigen Emulatorfälle unverändert. Diese automatisierten Nachweise geben keine echten Drive-/Zweitgeräte-/Picker-/Viewerfälle frei. Konkrete CI-Run-ID/Head, Ergebnisse und etwaige externe lokale Prüfgrenzen werden versionsgebunden im Draft-PR und Abschlussbericht dokumentiert.

### Lokaler Abschlussnachweis am 21.10.2026

- Gradle 9.3.1 / Java 21: vollständiger `testDebugUnitTest` mit bestehendem `-Proborazzi.test.record=true`, **1103 tatsächliche Methoden, 0 failures/errors/skips**, keine Suite-Platzhalter. Laufzeit 16m16s. Alle bestehenden Regressionen im vollständigen Lauf enthalten.
- `verify-test-execution.py`: grün; die fünf SDK-34-/36-Klassen tatsächlich 1/1/2/11/3 Methoden ausgeführt. Bestehender `phase10b-audit-evidence.py`: grün.
- Nach letzter Snapshot-/Seed-Ergänzung beide Kotlin-Testarten erneut kompiliert und 12 betroffene Kotlin-Methoden erneut erfolgreich; anschließend sechs neue Integritätsvergleiche erfolgreich. Je Vergleich 60 passende Originalhashes, 0 Contractabweichungen und keine festgestellten Orphans. Zusätzliche technische RAW-/ROOM-RAW-/Sekundärkopie-Differenzen bleiben separat im Bericht sichtbar.
- Gradle 9.3.1 / Java 17: `lintDebug assembleDebug assembleDebugAndroidTest` erfolgreich (kombinierter Lauf 11 Minuten). Keine APK-/Lintprüfung als bloß vorbereitet gezählt.
- 17 Python-Methoden erfolgreich; Workflow-YAML, Python-/Shellsyntax und `git diff --check` geprüft.
- Der im Record-Modus erzeugte Greeting-Referenzbild-Diff wurde nicht übernommen. Keine Screenshot-Goldens oder Assertions für diese Phase geändert.
- Anfängliche lokale Toolchain-/Cacheprobleme: fehlende SDK-/JDK-Werkzeuge und schreibgeschütztes Benutzerverzeichnis. Werkzeuge und Java-/Robolectric-Caches im Workspace eingerichtet; frühere fehlgeschlagene Starts zählen nicht als Erfolg. Lokaler adb-Start scheitert weiterhin am schreibgeschützten Standard-Benutzerverzeichnis; kein lokaler Emulatornachweis, kein `/dev/kvm` und kein echter Gerätezugriff bereitgestellt. Android-Ausführung folgt über CI.
- GitHub-CI und Emulator-Smoke bleiben bis zum tatsächlich beendeten Lauf offen; versionsgebundene Run-ID, Head und Resultate im Draft-PR/Abschlussbericht. Alle echten Praxisfälle unverändert **NOCH OFFEN**.

### Lokaler Nachweis des Isolations-Folgeauftrags am 21.10.2026

**AUTOMATISIERT BESTANDEN:** 24 Python-Methoden; 16 gezielte Kotlin-Methoden; vollständige Unit-/Robolectric-Suite mit 1107 tatsächlichen Methoden, 0 failures/errors/skips/Platzhalter. SDK-Ausführungswächter und bestehender Phase-10B-Audit grün, sechs Portfolio-Snapshotvergleiche grün. Kein Screenshot-Golden übernommen. Java 17 / Gradle 9.3.1: Referenz-App/-Test-APK, danach isolierte `assembleDebug assembleDebugAndroidTest`, `lintDebug` und `processReleaseManifest` grün. Referenz-Init-Script außerhalb von CI und für `installDebug --dry-run` nachweislich abgewiesen; keine Installation ausgelöst.

Die anschließende Binäranalyse bestätigt VersionName **1.0**, VersionCode **1**, die oben genannten Produkt-/Test-IDs und Launcher-Labels, fehlenden sharedUserId und vier jeweils eigene, untereinander kollisionsfreie Provider-Authorities (`.provider`, `.mlkitinitprovider`, `.firebaseinitprovider`, `.androidx-startup`). Alle vier Provider sind nicht exportiert; nur FileProvider erteilt temporäre URI-Grants. Zusammengeführtes Release-Manifest: ursprüngliche Produkt-ID, ursprüngliche Label-Ressource, nicht debuggable, ursprüngliche Authorities.

Lokale APK-Hashes dieses Android-Code-Stands (lokaler Testschlüssel; **nicht** die künftig separat signierten CI-APKs):

| Datei | SHA256 |
| --- | --- |
| isoliertes App-APK | `e258e97d67cf3acad34e9ab752aef0a7ca1326a4e40d0c150c82ebe413258ac7` |
| passendes QA-APK | `c79afc15a51c9ba7bb491c0d22287c441d8191b59216849e583c4206848c902c` |

Öffentliche lokale Zertifikats-SHA1 beider APKs: `7b30129dc53f6b2adb4c188b03bd2b39feb52e19`. Ausschließlich für **diese lokalen APKs** zur OAuth-Registrierung verwenden. Finale Code-Commit-/CI-Run-Zuordnung und die tatsächlich bereitgestellten CI-APK-Hashes/Fingerprints stehen im [versionsgebundenen Abschlussbericht von PR #143](https://github.com/xedox737/Steuer-Asistend-APP/pull/143) sowie im `apks.json` des zugehörigen Artefakts. CI-Merge-Commit und PR-Head gesondert abgleichen; keine ungeprüfte APK aus einem älteren Lauf installieren.

Isolierte App-Permissions: INTERNET, CAMERA, ACCESS_NETWORK_STATE, `com.google.android.providers.gsf.permission.READ_GSERVICES` und die eigene `com.aistudio.steuerassistent.vnhqkp.phase11a.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Exportiert: MainActivity, Compose PreviewActivity, androidx.activity.ComponentActivity, Google RevocationBoundService (geschützt durch `com.google.android.gms.auth.api.signin.permission.REVOCATION_NOTIFICATION`) und ProfileInstallReceiver (DUMP-geschützt). Keine externen BROWSABLE-Filter. Vollständige Komponenten-/Permission-/Zertifikatsliste einschließlich QA-APK in `apks.json`.

Kein lokales `/dev/kvm`/echtes Android-Gerät; deshalb lokale UID-/Parallel-/Room-/Preference-Isolationsausführung **NOCH OFFEN**. Dafür ist der vollständige CI-Emulatorjob vorbereitet; nur dessen tatsächlich beendete Resultate gelten als automatisierter Nachweis. OAuth, echter Drive und alle neun echten Praxisfälle bleiben **NOCH OFFEN**. Neue bestätigte Produktbefunde P0/P1/P2/P3 jeweils 0. Kein Produktfix, keine praktische Freigabe.
