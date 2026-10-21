# Phase 11A – praktische Android-/Google-Drive-Abnahme

## Basis, Umfang und aktueller Status

Base-SHA: `69f35772b6480a958ca7479b024dbd4d8bd42fa1`, nach erfolgreichem `git fetch origin` am 20.10.2026 bestätigt, am 21.10.2026 erneut unverändert geprüft. Branch: `test/phase11a-practical-device-drive-acceptance`. Ausgangsbranch `work`, Arbeitsbaum vor Beginn sauber. Vorbereitungs-ID: `P11A-2026-10-20-01`; diese bezeichnet **keinen durchgeführten Gerätetest**.

Gelesen: AGENTS.md, PROJECT_STATUS.md, CODE_MAP.md, VERIFY_CHANGES.md und PHASE10D_RESTORE_METADATA_CONTRACT.md; analysiert: Phase10BPortfolioAcceptanceTest, RestoreContractSnapshot, SupplementalDriveBackup, DrivePersistenceRepository, bestehende Android-Teststrategie. `Immopilot_Work_Arbeitsanweisungen.md` und `Phase10B_Portfolio_E2E_Abnahme.md` fehlen im Checkout und in bereitgestellten Arbeitsordnern. Fundort angefragt; nicht als gelesen gewertet. Auch Phase 10D dokumentiert den fehlenden separaten 10B-Bericht. Die vorhandenen Tests und Originalartefakte bleiben die verfügbare Testbasis.

Kein Produktcode in `app/src/main` geändert. Keine Banklisten-, DATEV-, Drive-Architektur- oder Migrationsänderung. Der QA-Runner erweitert AndroidJUnitRunner, delegiert normale Läufe unverändert an dessen Implementierung und verwendet den Evidence-Pfad nur bei `phase11aAction`. Der unveränderte Phase-10D-Vertragshelper wird jetzt aus `src/qa` für JVM- und Android-Tests gemeinsam kompiliert; er gelangt nicht ins App-APK. Nur die Testquellpfade sind zusätzlich in der Buildkonfiguration eingetragen.

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
6. App aus Recent Apps entfernen, Prozess vollständig beenden, neu per Launcher öffnen. Beispiel für die gewählte Testinstallation: `adb -s SERIAL_A shell am force-stop --user 0 com.aistudio.steuerassistent.vnhqkp`. PID-Wechsel und sichtbare Daten prüfen, dann `phase11a-after-drive-restore.json` aufnehmen.
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
