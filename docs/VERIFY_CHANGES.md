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
