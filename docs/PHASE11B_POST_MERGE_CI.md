# Phase 11B – Synchronisation des Phase7B-Emulatortests

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

## Abnahme

Die verbindliche Standardprüfung umfasst vollständiges `testDebugUnitTest`,
Testausführungs- und Portfolio-/Integritätswächter, `lintDebug`,
`assembleDebug` und `assembleDebugAndroidTest`. Native Ausführung erfolgt
mangels lokalem KVM in GitHub Actions; erwartete vollständige Suite weiterhin
70 eindeutige Methoden einschließlich aller drei Phase7B-Schriftgrößenfälle.
CI-Run-ID, tatsächliche Ergebnisse und der anschließende Merge-Stand werden
im zugehörigen Reparatur-PR dokumentiert. Ergebnisse noch laufender Prüfungen
werden hier nicht vorweggenommen.

Die früheren grünen Phase11B-PR-Läufe bleiben historische Nachweise. Der rote
nachfolgende Main-Lauf wird weder als bestanden gewertet noch durch einen
blinden Rerun ersetzt. Echte Google-/POCO-/OAuth-Prüfungen bleiben unabhängig
davon **NOCH OFFEN**; keine praktische Portfoliofreigabe.
