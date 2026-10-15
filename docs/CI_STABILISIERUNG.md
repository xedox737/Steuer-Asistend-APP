# CI-Stabilisierung – Analyse und offener Prüfstand

Stand: 15.10.2026. **Keine abgeschlossene Stabilitätsfreigabe.**

## Basis und Arbeitsregeln

- Repository: xedox737/Steuer-Asistend-APP.
- Aktueller Remote-main über GitHub Branch-API: `234bce3ed2fb654e78b020e0485fa616d3572d86`, Tree `4d0ce5ea8947277c45cac5fc327af78ccc46e0ba`.
- Branch: `fix/ci-stabilisierung`, exakt von diesem Commit.
- Lokaler Checkout nicht verfügbar: git clone scheitert am Sandbox-/Proxyzugriff. Kein erfolgreicher lokaler `git fetch origin`; der SHA ist über die Remote-API belegt. Änderungen werden per Git-Tree-API auf den unveränderten Base-Tree angewendet. Keine fremden lokalen Änderungen überschrieben.
- AGENTS.md, docs/VERIFY_CHANGES.md, docs/PROJECT_STATUS.md und docs/CODE_MAP.md gelesen. Historische Angaben in PROJECT_STATUS sind keine aktuelle Basis.
- `Immopilot_Work_Arbeitsanweisungen.md` ist weder im Root noch im rekursiven Remote-Dateibaum vorhanden. Es wurde kein Ersatzdokument erfunden.
- Alle Produktionsdateien, Datenintegritäts-, Navigations-, DATEV-, Backup- und sonstigen Tests bleiben erhalten.

## Letzte 50 Läufe

Snapshot der Repository-Actions-API mit per_page=50: 13 success, 24 failure, 13 cancelled. Darunter sind fünf erfolgreiche **Publish verified APK**-Läufe; diese ersetzen keinen Android-CI-Lauf. Sämtliche 50 Joblisten und alle 24 fehlgeschlagenen Joblogs wurden gelesen. Maschinenlesbare Laufübersicht: [ci-stability-history.json](ci-stability-history.json). Logauszüge mit Joblinks: [ci-stability-evidence.json](ci-stability-evidence.json).

Klassifikation ist bei reinen Assertions ausdrücklich vorläufig: Ein Timeout allein beweist keinen Produktfehler, ein fehlgeschlagener fachlicher Vertrag aber auch keinen Infrastrukturfehler.

| Lauf | Workflow | Ergebnis | Klasse | Befund |
| --- | --- | --- | --- | --- |
| [38721934233](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38721934233) | Android CI | success | Erfolg | verify und Emulator erfolgreich |
| [38721934157](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38721934157) | Publish verified APK | success | Erfolg | Nur APK-Publish; kein vollständiger CI-Beleg |
| [38712643562](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38712643562) | Android CI | success | Erfolg | verify und Emulator erfolgreich |
| [38709266454](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38709266454) | Android CI | failure | Testfehler | AfA: Zurück-Node nach Navigation nicht gefunden; im Base durch 0a464e3 korrigiert. |
| [38705263745](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38705263745) | Android CI | failure | Emulator | Viele Start-Fokus-Timeouts; vor HOME-Fix a60408d. |
| [38703606308](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38703606308) | Android CI | failure | Testfehler | PrimaryNavigationComposeTest: Main-Looper/Banklink-Wartebedingung; AndroidStateAwait folgt in 317eab9. |
| [38703182457](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38703182457) | Android CI | cancelled | Abbruch | Lauf abgebrochen; neuere Commits/Concurrency vorhanden, genauer Auslöser aus Joblogs allein nicht bewiesen. |
| [38698492878](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38698492878) | Android CI | failure | Emulator | RootViewWithoutFocus / NoMatchingRoot; vor HOME-Fix a60408d. |
| [38697175580](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38697175580) | Android CI | failure | Testfehler | Phase9C-Compose: Room-State-Wartebedingung; AndroidStateAwait folgt in 317eab9. |
| [38695962375](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38695962375) | Android CI | failure | Testfehler | Phase9D-Compose: Room-State-Wartebedingung; AndroidStateAwait folgt in 317eab9. |
| [38653039878](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38653039878) | Publish verified APK | success | Erfolg | Nur APK-Publish; kein vollständiger CI-Beleg |
| [38653039874](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38653039874) | Android CI | failure | Emulator | Fokusverlust ab Phase8ALoan15; danach klassenübergreifende NoMatchingRoot-Fehler. |
| [38648154217](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38648154217) | Android CI | success | Erfolg | verify und Emulator erfolgreich |
| [38647378181](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38647378181) | Android CI | cancelled | Abbruch | Lauf abgebrochen; neuere Commits/Concurrency vorhanden, genauer Auslöser aus Joblogs allein nicht bewiesen. |
| [38642983924](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38642983924) | Android CI | failure | Emulator | RootViewWithoutFocus ab LoanDateComponent, danach klassenübergreifend. |
| [38637212486](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38637212486) | Android CI | failure | Emulator | Phase6BWorkflow: RootViewWithoutFocus. |
| [38617207967](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38617207967) | Android CI | failure | Infrastruktur | Lint-Schritt nach 10 Minuten abgebrochen; Ressourcen-/Cacheursache nicht abschließend bewiesen. |
| [38616654443](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38616654443) | Android CI | cancelled | Abbruch | Lauf abgebrochen; neuere Commits/Concurrency vorhanden, genauer Auslöser aus Joblogs allein nicht bewiesen. |
| [38611341496](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38611341496) | Android CI | failure | Testfehler (vorläufig) | Font-Matrix: nicht angezeigte Komponente und expected 1.5 / actual 1.0; Produkt-/Konfigurationsanteil offen. |
| [38608852058](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38608852058) | Android CI | failure | Testfehler | Logbook: DB trips=3, Flow trips=0; zusätzlich Phase9C-Wartebedingung. |
| [38608694203](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38608694203) | Android CI | cancelled | Abbruch | Lauf abgebrochen; neuere Commits/Concurrency vorhanden, genauer Auslöser aus Joblogs allein nicht bewiesen. |
| [38607325406](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38607325406) | Android CI | failure | Testfehler | RentOverviewComposeTest: Timeout bei Fixture/Flow-Synchronisierung. |
| [38599646305](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38599646305) | Publish verified APK | success | Erfolg | Nur APK-Publish; kein vollständiger CI-Beleg |
| [38599646293](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38599646293) | Android CI | failure | Testfehler | Logbook: DB trips=3, Flow trips=0. |
| [38568159698](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38568159698) | Android CI | success | Erfolg | verify und Emulator erfolgreich |
| [38563347144](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38563347144) | Android CI | failure | Testfehler (vorläufig) | Textlayout: Width overflow, unterschiedliche Messbreiten; Produkt-/Messfehler anhand Log allein nicht trennbar. |
| [38560260394](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38560260394) | Android CI | failure | Testfehler (vorläufig) | Textlayout: Width overflow; Produkt-/Messfehler anhand Log allein nicht trennbar. |
| [38559056345](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38559056345) | Android CI | cancelled | Abbruch | Lauf abgebrochen; neuere Commits/Concurrency vorhanden, genauer Auslöser aus Joblogs allein nicht bewiesen. |
| [38557649548](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38557649548) | Android CI | failure | Testfehler | Logbook: DB trips=3, Flow trips=0; Fixture-Isolation und Synchronisierung. |
| [38556127865](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38556127865) | Android CI | failure | Testfehler | Logbook/Phase5B-Compose: Timeouts. |
| [38542874481](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38542874481) | Android CI | failure | Testfehler (vorläufig) | Popup-WE-Node fehlt; Phase9B Assertions. Produktanteil ohne Artefaktprüfung offen. |
| [38540347930](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38540347930) | Android CI | failure | Testfehler | LogbookReferenceLayoutTest: wiederkehrender Fixture-Timeout. |
| [38539875800](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38539875800) | Android CI | cancelled | Abbruch | Lauf abgebrochen; neuere Commits/Concurrency vorhanden, genauer Auslöser aus Joblogs allein nicht bewiesen. |
| [38538184071](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38538184071) | Android CI | cancelled | Abbruch | Lauf abgebrochen; neuere Commits/Concurrency vorhanden, genauer Auslöser aus Joblogs allein nicht bewiesen. |
| [38535878397](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38535878397) | Android CI | failure | Testfehler | Instrumentation kompiliert nicht: Unresolved reference BankRentSuggestion. |
| [38534941340](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38534941340) | Android CI | cancelled | Abbruch | Lauf abgebrochen; neuere Commits/Concurrency vorhanden, genauer Auslöser aus Joblogs allein nicht bewiesen. |
| [38533479870](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38533479870) | Android CI | cancelled | Abbruch | Lauf abgebrochen; neuere Commits/Concurrency vorhanden, genauer Auslöser aus Joblogs allein nicht bewiesen. |
| [38532819058](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38532819058) | Android CI | cancelled | Abbruch | Lauf abgebrochen; neuere Commits/Concurrency vorhanden, genauer Auslöser aus Joblogs allein nicht bewiesen. |
| [38525090063](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38525090063) | Android CI | success | Erfolg | verify und Emulator erfolgreich |
| [38525090008](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38525090008) | Publish verified APK | success | Erfolg | Nur APK-Publish; kein vollständiger CI-Beleg |
| [38488177862](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38488177862) | Android CI | success | Erfolg | verify und Emulator erfolgreich |
| [38488104989](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38488104989) | Android CI | cancelled | Abbruch | Lauf abgebrochen; neuere Commits/Concurrency vorhanden, genauer Auslöser aus Joblogs allein nicht bewiesen. |
| [38483993683](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38483993683) | Android CI | failure | Produktfehler (Verdacht) | Backup-Vertrag: Assertion/JSONException; darf nicht als Infrastruktur-Flake abgetan werden. |
| [38481150712](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38481150712) | Android CI | cancelled | Abbruch | Lauf abgebrochen; neuere Commits/Concurrency vorhanden, genauer Auslöser aus Joblogs allein nicht bewiesen. |
| [38387403810](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38387403810) | Publish verified APK | success | Erfolg | Nur APK-Publish; kein vollständiger CI-Beleg |
| [38387403709](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38387403709) | Android CI | success | Erfolg | verify und Emulator erfolgreich |
| [38370165531](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38370165531) | Android CI | success | Erfolg | verify und Emulator erfolgreich |
| [38366835907](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38366835907) | Android CI | failure | Testfehler (vorläufig) | Phase8B-History Assertions bei allen Font-Skalen; Produktanteil ohne Artefaktprüfung offen. |
| [38364827809](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38364827809) | Android CI | failure | Testfehler | Instrumentation: Syntaxfehler und nicht auflösbare Referenzen in Phase8BHistoryEmulatorTest. |
| [38364458583](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38364458583) | Android CI | cancelled | Abbruch | Lauf abgebrochen; neuere Commits/Concurrency vorhanden, genauer Auslöser aus Joblogs allein nicht bewiesen. |

## Wiederkehrende Ursachen und Maßnahmen

1. **Room/StateFlow und Robolectric-Main-Looper:** LogbookReferenceLayoutTest scheitert u. a. in [38599646293](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38599646293), [38608852058](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38608852058) und [38557649548](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38557649548) mit gespeicherten drei Fahrten, aber leerem beobachtetem Flow. main enthält bereits Isolation, einen expliziten WhileSubscribed-Collector und den Helfer AndroidStateAwait. Der Logbook-Test verwendet beim Warten auf die drei Produktionszustände jedoch noch direkt waitUntil. Änderung: denselben vorhandenen Helfer nutzen, der die Bedingung über runOnIdle prüft und Android-Main-Callbacks synchronisiert. **Die Bedingung und 30.000-ms-Grenze bleiben identisch.** Keine Assertions entfernt; Wirkung muss durch Tests bestätigt werden.
2. **Zwei Cache-Verwalter:** setup-java stellt ~/.gradle vor setup-gradle wieder her. Wiederholt (u. a. Jobs [116296397108](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38697175580/job/116296397108), [116035937336](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/38617207967/job/116035937336)) meldet setup-gradle: `Gradle User Home already exists: will not restore from cache.` Änderung: nur `cache: gradle` bei setup-java entfernen; setup-gradle bleibt alleiniger Cache-Verwalter. Kein Zusammenhang zwischen Cache und fachlichen Assertions behauptet. Der einzelne Lint-Timeout belegt keine generelle Notwendigkeit höherer Timeouts.
3. **Emulator-Fokus:** mindestens vier Läufe zeigen RootViewWithoutFocus/NoMatchingRoot, oft mit klassenübergreifenden Folgefehlern. main enthält bereits den HOME-einmalig-Fix [a60408d](https://github.com/xedox737/Steuer-Asistend-APP/commit/a60408d8746cf43500361f372abe9253822a7529), Fensterfokusprüfung und Cleanup bei fehlgeschlagenem Start. Diese Fixes nicht nochmals implementieren. Die zwei jüngsten vollständigen grünen Läufe (38712643562, 38721934233) liegen nach dem Fix, reichen aber nicht als Stabilitätsnachweis.
4. **Bereits behobene Testfehler:** Main-Looper-Helfer aus [317eab9](https://github.com/xedox737/Steuer-Asistend-APP/commit/317eab9aefecfdab18c66883fb0efa110fe6f696) wird in PrimaryNavigation/Phase9C/Phase9D bereits verwendet. AfA-Zurück-Selektor folgt in 0a464e3. Instrumentation-Syntaxfehler werden regulär vor Emulatorstart erkannt. Keine Änderungen daran auf Verdacht.
5. **Echte Fehler bleiben sichtbar:** Backup-Vertragsfehler, Textlayout-Assertions und fehlende Nodes werden nicht durch Retry, Skip oder continue-on-error verdeckt.

## Emulator, Ressourcen, Isolation und Concurrency

Geprüfte Konfiguration: API 35/google_apis/x86_64, Pixel 5, 2 CPU-Kerne, 2048 MB RAM, 512 MB Heap, KVM explizit geprüft, Cold Boot ohne Snapshots, Animationen/Spellchecker aus. Gradle: 4-GB-Heap, vier Worker, Kotlin in-process. Logs zeigen erfolgreiche Boots und gestartete Tests auch in fehlschlagenden Läufen; kein belegter OOM/KVM-/Bootmangel als Ursache der ausgewerteten Fehler. Deshalb keine Ressourcen-/Timeoutänderung auf Verdacht.

EmulatorEnvironmentRule begrenzt Datenbereinigung auf explizit freigegebene Debug-Emulatoren, leert Room und das persistente Preference-Inventar, setzt/restauriert Font-Skala. ActivityScenario wird geschlossen. Robolectric-Regeln isolieren Application-Factory und Room, Fixtures/Collector werden freigegeben. Bestehende Grenzen/Assertions bleiben erhalten.

Concurrency trennt Eventtyp und Branch und bricht überholte Läufe ab. 13 Abbrüche zählen nicht als Testfehler oder Erfolg. Fünf Stabilitätsläufe müssen **nacheinander** laufen, sonst können workflow_dispatch-Läufe auf demselben Branch einander abbrechen. Keine automatischen Testwiederholungen eingeführt. Kein AVD-Cache vorhanden; daher keine veralteten Snapshots.

## Prüfplan und aktuelle externe Grenzen

1. Gezielt: `gradle testDebugUnitTest --tests com.example.ui.LogbookReferenceLayoutTest -Proborazzi.test.record=true --stacktrace --no-daemon`.
2. Danach vollständige Android CI: lintDebug, alle Unit-Tests, assembleDebug, Instrumentation-Kompilierung und alle connectedDebugAndroidTest-Tests.
3. Fünf vollständige Läufe auf **identischem Head-Commit**; pro Lauf ID, Head-SHA, Ergebnis, Testzahlen und Ursache festhalten. Bei Änderung oder Fehler Serie neu beginnen, frühere Fehlläufe weiter dokumentieren.
4. Lokal fehlen Gradle, adb und der Checkout; Java ist 21 statt CI-Java-17. Deshalb bislang keine lokalen Android-/Gradle-Tests ausführbar. Whitespace-Kontrolle der beiden geänderten Dateien per `git diff --no-index --check` erfolgreich.
5. Der verfügbare Connector kann PR-CI auslösen, bietet jedoch keinen Workflow-Dispatch oder vollständigen Workflow-Rerun an; die vorhandenen Rerun-Werkzeuge starten nur einzelne/fehlgeschlagene Jobs. Diese sind kein Ersatz für fünf vollständige Läufe. Kein Erfolg wird fingiert. Noch keine fünf Läufe auf dem Änderungscommit nachgewiesen.
6. Fokussierter Test und die fünf Läufe bleiben als Abnahme offen; Ergebnisse werden im PR dokumentiert, ohne nachträgliche Dokumentationscommits den geprüften Head-SHA zu verändern.

## Merge-Schutz

Remote-main meldet `protected: false`, keine required_status_checks; Repository-Rulesets-API liefert `[]`. **Fehler blockieren aktuell den CI-Lauf, aber nicht verbindlich das Mergen.** Dieser Befund bestand vor den Änderungen. Der Auftrag zur Kontrolle ist keine Änderung der Repository-Zugriffsregeln: keine Admin-Einstellungen stillschweigend verändert. Für verbindliches Gate müssen mindestens `verify` und `android-emulator-e2e` als erforderliche PR-Checks aktiviert werden.

Workflow-Review: lint, Unit-Test, Build und Emulator-Test haben kein continue-on-error. Emulator braucht erfolgreiches verify; das Shellscript sichert den ursprünglichen Fehlercode trotz Diagnoseerfassung. Nur optionale Report-/Debug-Uploads haben bereits bestehendes continue-on-error. Alle Testfehler bleiben rot; Assertions/Tests/Produktlogik unverändert.

## Verbleibende Risiken

Die Wirkung des ergänzten Logbook-Wartens und des vereinheitlichten Caches ist noch nicht durch fünf Läufe belegt. Ein grüner Einzellauf beweist keine dauerhafte Stabilität. Emulator-Fokusfehler können trotz bestehender Fixes wiederkehren und müssen dann anhand der gesicherten Window-/Input-Diagnostik weiter untersucht werden. Vorläufige fachliche/Layout-Klassifikationen sind keine Entwarnung. PR bis zum Abschluss der offenen Prüfungen als Draft behandeln. Kein Merge nach main.

