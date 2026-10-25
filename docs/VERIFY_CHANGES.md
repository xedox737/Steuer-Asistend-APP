# Immopilot – Prüfablauf für Änderungen

Die CI verwendet Gradle 9.3.1. Der vollständige JVM-/Robolectric-Unit-Task benötigt Java 21
für die ausdrücklich auf SDK 36 deklarierten Tests; Lint, Android-/Release-Build und Emulatorjob
bleiben auf Java 17. Die SDK-34-/35-/36-Deklarationen dürfen nicht durch eine globale
Erlaubnisliste ausgeblendet werden. Das Repository besitzt keinen eingecheckten Gradle-Wrapper;
lokal ein kompatibles `gradle` verwenden oder die GitHub-CI als Referenz nutzen.

## Vor Beginn

```bash
git branch --show-current
git status --short
git fetch origin
git log -1 --oneline origin/main
```

Bei neuer Arbeit von aktuellem `origin/main` abzweigen. Keine fremden lokalen Änderungen überschreiben.

## Schnelle statische Kontrolle

```bash
git diff --check
git diff --stat origin/main...HEAD
```

## Standardprüfung

Zuerst mit Java 21 ausführen:

```bash
gradle testDebugUnitTest -Proborazzi.test.record=true --stacktrace --no-daemon
python3 .github/scripts/verify-test-execution.py
```

Danach für die Android-Prüfungen auf Java 17 wechseln:

```bash
gradle lintDebug --stacktrace --no-daemon
gradle assembleDebug --stacktrace --no-daemon
gradle assembleDebugAndroidTest --stacktrace --no-daemon
git diff --check
```

Relevante fokussierte Tests dürfen vorher laufen, ersetzen die Standardprüfung aber nicht.
Der XML-Prüfer verlangt echte Methoden mit dem vollständigen Klassennamen und prüft
Fehler/Skips. Ein `Gradle Test Run`-Klassenplatzhalter ist kein bestandener Test.
Die Klasse erhält wegen SDK-übergreifender JNI-Zustände einen eigenen JVM-Worker;
es werden weiterhin alle Tests ausgeführt. Details und Feldvertrag:
[`PHASE10D_RESTORE_METADATA_CONTRACT.md`](PHASE10D_RESTORE_METADATA_CONTRACT.md).

## Nach Änderungen mit besonderen Risiken

Zusätzlich manuell prüfen:

- Navigation/Backstack: System-Zurück und App-Zurück.
- Room: Migration vorhandener Daten; keine destructive migration.
- Drive/Restore: Wiederholung ohne Dubletten; IDs und Dateiverweise erhalten.
- Dokumente: Datei öffnen, MIME, URI-Rechte, Neustart.
- Bank: Kategorie, Objekt, Beleglinks, Splits, Mietzuordnung und Deduplizierung.
- Immobilien/Miete: Objekt-/Einheitenisolation und Historie.
- AfA: Rechenweg und Bestandswerte.
- Fahrtenbuch: Speichern, Bearbeiten, Löschen/Storno, Listenrefresh.
- DATEV: Mapping, Freigabe, EXTF-Struktur, CSV/ZIP und Beleganhänge.

## Vor PR/Merge

1. Zielbranch aktualisieren.
2. Konflikte ursächlich lösen.
3. Standardprüfung erneut ausführen.
4. CI vollständig grün abwarten.
5. Offene Geräte-/Cloud-Prüfgrenzen im PR nennen.
6. Merge nur mit ausdrücklicher Freigabe, wenn der Auftrag dies verlangt.

## Phase-11A-Evidence-Helfer

```bash
python3 -m unittest discover -s .github/scripts/tests -p 'test_phase11a*.py' -v
# Nach den vollständigen Unit-Tests, mit den tatsächlich erzeugten Portfolio-Snapshots:
python3 .github/scripts/phase11a-audit-ci.py
```

Der bestehende Phase-10D-Vertragshelper liegt unverändert unter `app/src/qa/java` und
wird in JVM-/Android-Testquellen eingebunden, nicht in die App. Der zusätzliche
`Phase11AInstrumentation`-Einstieg ist ausschließlich manuell aufzurufen; er zählt
nicht als automatisch durchgeführte Geräteabnahme. Ablauf und Prüfgrenzen:
[`PHASE11A_PRACTICAL_ACCEPTANCE.md`](PHASE11A_PRACTICAL_ACCEPTANCE.md).

Debug hat die getrennte applicationId `com.aistudio.steuerassistent.vnhqkp.phase11a`
und den Launcher-Namen `ImmoPilot Test`; Release-ID/-Name bleiben unverändert.
Seed/Evidence verweigern das Produkt-Package. Der vollständige Emulatorjob prüft
zusätzlich parallele Installation, verschiedene UID, getrennte Room-/Preference-/Dateidaten
und unveränderte Referenzdaten nach allen E2E-Tests. Das CI-Artefakt
`phase11a-isolated-apks` enthält zusammen signierte App-/QA-APKs und ihre Binäranalyse.
Vor echtem Drive-Test Package-/Zertifikatszulassung für OAuth gemäß Abnahmeprotokoll prüfen.

Die Handy-Testwerkzeuge liegen ausschließlich in `src/debug`; Release besitzt nur eine
leere Einstellungen-Erweiterung. UI und Instrumentation verwenden denselben Importer
und für identische IDs die eingebettete ursprüngliche 17-Einheiten-ZIP. Nach dem Build
`assembleDebug assembleRelease assembleDebugAndroidTest` zusätzlich
`python3 .github/scripts/phase11a-check-ui-packaging.py` ausführen: tatsächliche Release-APK
ohne Testoberfläche, Importer/Guard und Testdaten; Debug-APK mit unveränderter Fixture.
Die CI führt nach der kompletten Suite zusätzlich einen UI-Seed-/Prozessneustartfall aus;
dieser ist separat protokolliert und kein erneuter Versuch eines fehlgeschlagenen Tests.


## Phase-11A-Drive-Namespace

Der tatsächliche Debug-Build verwendet `ImmoPilot Phase11A Testdaten`, Release unverändert
`Steuerassistent Belege`. Die Root-Auswahl und alle HTTP-Einstiege werden über
`DriveEnvironment` geführt. Die Debug-Variante validiert Elternketten und JSON-Drive-IDs;
Release delegiert das bestehende Protokoll ohne diese Test-Cloudtexte.

Gezielt: `testDebugUnitTest --tests com.example.api.Phase11ADriveNamespaceTest`.
Der zusätzliche `Phase11ADriveNamespaceEmulatorTest` muss in der vollständigen Emulator-Suite
laufen. Er prüft den tatsächlichen Backup-Screen und den Android-HTTP-Pfad gegen eine
simulierte gemeinsame Kontostruktur. Echte Google-Konto-/Transportfreigabe folgt daraus nicht.

`python3 -m unittest discover -s .github/scripts/tests -p 'test_phase11a*.py'` enthält zusätzlich
den Wächter gegen ungesicherte Drive-HTTP-Einstiege. Der bestehende tatsächliche Release-APK-Check
prüft auch die Abwesenheit von Namespace-Prüfer, Testroot und Debug-Cloudhinweis. Namespace-JSON
und Emulator-JSON werden in den bestehenden Evidence-Transport aufgenommen.

E11A-02 bleibt bewusst offen: Die vollständige Fixture enthält zehn Fahrtbelege ohne Original.
Der Transporttest verlangt die bisherige Fehlermeldung für diese zehn Sync-Versuche und zeichnet
`fullUiSyncSuccess=false` auf. Zweimaliger Original-/Stammdaten-/Zusatzbackuptransport und zweimal
MERGE in den vorhandenen Bestand sind keine vollständige UI-Sync-/Fresh-Full-Restore-Freigabe.
Keine Assertions bestehender Regressionen abschwächen, keine fachliche Reparatur in diesem Auftrag.

Die Test-App verwendet zusätzlich ausschließlich `appProperties.appName=ImmoPilotPhase11ATest`;
Produkt weiterhin `ImmobilienBelegApp`. Die Gegenrichtungsprüfung lässt unveränderte globale
Produktqueries gegen dieselbe simulierte Kontostruktur laufen und verlangt ausschließlich
Produkt-Treffer, auch bei kollidierenden fachlichen IDs/Kategorieschlüsseln. Nach beiden MERGEs
validiert der Snapshot-Checker zusätzlich alle lokalen Beziehungen/Originalketten strukturell;
dieser Selbstvergleich bestätigt keine vollständige Sync-/Restore-Vertragsgleichheit.

E11A-03 ist zusätzlich im unveränderten Checker reproduziert: nach dem bestehenden
Original-Sync verlieren 60 ManagedDocuments ihre unitId. Die neue vollständige Post-Sync-
Snapshotdiagnose wird im Evidence-Transport separat als
`phase11a-drive/post-sync-integrity-diagnostic.json` einschließlich `ok=false` und sämtlicher
Fehler gesichert. Das ist ein offener P1/Zuordnungsdatenverlust, kein grüner Portfolio-Check.
Die bisherigen sechs Phase-10B-Restorevergleiche und der Seed-Strukturcheck bleiben unverändert
verpflichtend. Keine Fehler aus dem Checker entfernen/normalisieren; gesonderte Fixphase nötig.
