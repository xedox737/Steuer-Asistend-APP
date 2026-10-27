# Phase 11B – Dialog-Synchronisation und vollständige CI-Evidence

## Ausgangsbasis und roter Nachweis

Base: `12db374c7a5052af390fb6aa7edfde775f798092`, aktueller
`origin/main` bei Arbeitsbeginn. PR #146 war bereits gemergt.
Android-CI [40385709353](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/40385709353):
`verify` erfolgreich, Emulatorjob `121800734809` fehlgeschlagen.

70 tatsächlich ausgeführte Emulatormethoden, ein Fehler, keine Errors/Skips.
Betroffen: `Phase7BBatch10EmulatorTest.twentyUnitsKeepValuesWhileScrollingAndProtectExistingContracts`
bei Schriftfaktor 1,0. Die entsprechenden Fälle mit 1,3 und 1,5 bestanden.
Das ursprüngliche unveränderte JUnit-XML liegt unter
`phase11b-evidence/post-merge-phase7b-red.xml`; Herkunft, Quell-SHA und
SHA256 stehen in `post-merge-phase7b-red-source.json`.
Der heruntergeladene Logtransport wurde vor dem Entpacken nach Bytezahl und
SHA256 geprüft. Keine bestehenden Nutzerdaten oder echten Geräte beteiligt.

Die Assertion `assertIsNotEnabled()` meldete nicht etwa eine aktivierte
Checkbox, sondern einen fehlenden Knoten mit
`rent_batch_select_p7b-rent-unit`. Genau diese Unterscheidung ist relevant.

## Nachvollziehbare Ursache und Prüfgrenze

`ReceiptViewModel.saveInitialRentBatch()` schreibt auf `Dispatchers.IO` und
veröffentlicht anschließend die aktualisierten Einheiten über
`refreshPropertyUnits()`. Erst nach Rückkehr auf die UI-Coroutine ruft
`InitialRentBatchDialog` bei Erfolg `onSaved()` und `onDismiss()` auf.
Der bisherige Emulatortest wartete nur auf den veröffentlichten Mieternamen
und öffnete danach sofort erneut den Dialog.

Die Datenbereitschaft garantiert daher nicht, dass der vorherige Dialog die
Komposition bereits verlassen hat. Dessen Vorschau besitzt absichtlich
keine Auswahl-Checkboxen (`if (!preview) Checkbox(...)`). Ein weiterer
Klick auf den Öffnen-Knoten während dieses Übergangs stellt noch keinen
neuen Eingabedialog her. Der JVM-Test `Phase7BComposeTest.batch()` wartet
bereits ausdrücklich auf den Dismiss-Callback.

Die fehlende Synchronisationsbedingung ist im Quellablauf nachweisbar und
passt zum beobachteten fehlenden Knoten. Der genaue UI-Baum im Zeitpunkt des
ursprünglichen Fehlers wurde vom damaligen Produkt-Logtransport nicht
übertragen; die konkrete Vorschauansicht wird deshalb nicht als separat
beobachteter Screenshotbefund ausgegeben. Ein Fehler an Mietdaten oder
fachlichem Bestandsschutz ist durch diesen JUnit-Fehler nicht nachgewiesen.

## Gezielte Änderung

### Native Dialogbereitschaft

Nur `app/src/androidTest/java/com/example/e2e/Phase7BWorkflowEmulatorTest.kt`:
Nach den unveränderten Speicher-Assertions und vor erneutem Öffnen wartet
die gemeinsame Testmatrix, bis `rent_batch_rows` aus der Komposition entfernt
ist. Dafür gilt das bestehende 10-Sekunden-Limit. Es gibt keinen Sleep und
keinen Wiederholungsversuch des Testablaufs.

Alle drei Schriftgrößen, die Prüfung der deaktivierten Bestands-Checkbox,
das Fehlen editierbarer Bestandsfelder, stabile IDs, Beträge, Entwurfs- und
Recreate-Prüfungen bleiben unverändert. Ein tatsächlich fehlerhaft
aktivierter Bestandsdatensatz lässt die bestehende Assertion weiter scheitern.
Keine Änderung an `app/src/main`, Datenbank, Miet-, Bank-, Drive- oder
Restore-Logik. Kein Test entfernt, übersprungen oder abgeschwächt.

### Frische, laufgebundene JVM-Evidence

Der erste PR-Lauf
[40423995752](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/40423995752)
auf `377934d10aaeb391093a4ec533d920ef120ee1d0` erreichte den Emulator nicht:
`testDebugUnitTest` wurde `FROM-CACHE` geladen. Die wiederhergestellten
JUnit-Dateien enthalten 1156 Methoden ohne Testfehler, stellen jedoch keine
frische Ausführung in diesem Lauf dar. Die zusätzlichen Snapshots fehlten:
`phase10b/units-17/comparison-summary.json` und
`e11a03-repair/before-fresh-full-backed.json`. Beide unveränderten
Evidence-Wächter verweigerten daher zu Recht die Abnahme. Der native Job
wurde wegen des fehlgeschlagenen Verify-Jobs übersprungen und wird nicht als
bestanden gewertet. Roher Logauszug: `phase11b-evidence/qa-evidence-cache-red.txt`.

Die Tests schreiben zusätzliche QA-Dateien außerhalb der standardmäßig
registrierten JUnit-/HTML-Ausgaben. Außerdem lesen ihre Snapshot-Metadaten
die CI-Umgebung, ohne dass diese Identität bisher als Test-Task-Eingabe
deklariert war. Ein Cachetreffer konnte somit XML ohne die zugehörigen
Snapshots liefern. Die Herkunft des alten Cacheeintrags wird nicht als
nachgewiesen behauptet.

Eine zunächst untersuchte Registrierung zusätzlicher Ausgabeordner wurde
nicht als erfolgreiche Cache-Lösung ausgegeben: Der vollständige frische
Lauf bestand mit 1156 Methoden, die anschließende lokale
Wiederherstellungsprobe lieferte aber keinen Cachetreffer. Diese Probe wurde
nach dem erkannten Fehlschlag beendet; sie ist weder eine erfolgreiche
Wiederherstellung noch eine weitere bestandene Gesamtsuite. Eine gezielte
Gradle-Diagnose zeigte zusätzlich eine deaktivierte Cache-Nutzung wegen
Dateien ohne eindeutig zugeordneten Output-Owner. Die Aussage über diese
Diagnose wird nicht mit einer bewiesenen Herkunft des ursprünglichen
CI-Cacheeintrags verwechselt. Lokaler Nachweis:
`phase11b-evidence/local-cache-probe-not-passed.txt`.

Die endgültige, eng begrenzte Regel in `app/build.gradle.kts` lautet deshalb:
Die Abnahmesuite `testDebugUnitTest` muss tatsächlich ausgeführt werden.
`outputs.upToDateWhen { false }` und ein begründetes `doNotCacheIf` verhindern
sowohl `UP-TO-DATE` als auch das Laden/Speichern ihrer Testergebnisse im
Build-Cache. Die öffentlichen Werte `GITHUB_SHA`, `GITHUB_RUN_ID` und
`GITHUB_RUN_ATTEMPT` sind zusätzlich explizite Task-Eingaben. Alle Compiler-,
Ressourcen- und APK-Tasks behalten ihre bisherigen Caches.

Diese Regel passt zum Abnahmevertrag: Laufgebundene Snapshot-Dateien und
die tatsächliche Methodenausführung müssen gemeinsam nachweisbar sein.
Die Integrity-Checker bleiben unverändert; fehlende Evidence wird weiterhin
abgewiesen. Die Probe mit Cache-Wiederherstellung wird ausdrücklich nicht
als bestanden behauptet und ist nicht Teil der endgültigen Lösung.

Kein globales Abschalten des Build-Caches, keine neue Buildvariante und keine
Signing-/Application-ID-/Produktlogikänderung. Kein Retry und keine
Timeout-Erhöhung. Für alle lokalen Proben wurden ausschließlich generierte
synthetische Build-Ausgaben reversibel beiseitegelegt, keine App- oder
Nutzerdaten gelöscht. Der endgültige Voll-Lauf und die neue CI müssen die
frische Ausführung ohne `FROM-CACHE`/`UP-TO-DATE` sowie vollständige
Portfolio-/Restore-Nachweise belegen.

## Abnahme

Die verbindliche Standardprüfung umfasst vollständiges `testDebugUnitTest`,
Testausführungs- und Portfolio-/Integritätswächter, `lintDebug`,
`assembleDebug` und `assembleDebugAndroidTest`. Native Ausführung erfolgt
mangels lokalem KVM in GitHub Actions; erwartete vollständige Suite weiterhin
70 eindeutige Methoden einschließlich aller drei Phase7B-Schriftgrößenfälle.
Der endgültige lokale Lauf mit der Frische-Regel bestand in 24 Minuten 5 Sekunden:
1156 eindeutige Methoden, keine Failures, Errors oder Skips, einschließlich
5 Phase7B-Compose-, 36 Reparatur- und 17 SDK36-Initialisierungsfällen.
Die unveränderten Evidence-Wächter bestanden: beide Portfolio-Größen, DATEV
mit 120 Buchungen, 60/60 Originalhashes sowie null Restore-/MERGE-Fehler.
`lintDebug`, `assembleDebug` und `assembleDebugAndroidTest` bestanden danach
in 3 Minuten 45 Sekunden; Lint: keine Errors/Fatals, 118 bestehende Warnungen.
Laufidentität und Prüfgrenzen stehen in
`phase11b-evidence/jvm-freshness-proof.json`.

CI-Run-ID, tatsächliche Ergebnisse und der anschließende Merge-Stand werden
im zugehörigen Reparatur-PR dokumentiert. Ergebnisse noch laufender Prüfungen
werden hier nicht vorweggenommen.

Die früheren grünen Phase11B-PR-Läufe bleiben historische Nachweise. Der rote
nachfolgende Main-Lauf wird weder als bestanden gewertet noch durch einen
blinden Rerun ersetzt. Echte Google-/POCO-/OAuth-Prüfungen bleiben unabhängig
davon **NOCH OFFEN**; keine praktische Portfoliofreigabe.
