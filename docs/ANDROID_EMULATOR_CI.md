# Android-Emulator-CI

Basis: `main` **7ac14f225f159db74df146f7161d84e32f6a02f6**. Branch: `ci/android-emulator-e2e`.

## Ausführung

`.github/workflows/android-ci.yml` behält den bisherigen `verify`-Job vollständig bei:
Java 17, Gradle 9.3.1, Lint, Unit-/Robolectric-/Roborazzi-Tests, Debug-APK,
Berichte und bisherige Release-/Testversion-Schritte. Danach startet
`android-emulator-e2e` über `needs: verify`; ein Fehler macht den PR rot.
Kein `continue-on-error` und keine automatischen Testwiederholungen.

Push auf `main`/`agent/**`, PR gegen `main` und manueller Start bleiben erhalten.
Die Ausnahme für `.github/workflows/**` wurde entfernt, damit auch reine
Workflowänderungen nach einem Merge geprüft werden. Der Featurebranch läuft
über den PR-Trigger; er bekommt keinen zusätzlichen Push-Trigger.

## Emulator

- Ubuntu `ubuntu-latest`, ReactiveCircus/android-emulator-runner **2.38.0**, auf
  Commit `a421e43855164a8197daf9d8d40fe71c6996bb0d` fixiert.
- Android 15, **API 35**, **x86_64**, **Google APIs**, ohne Google-Konto.
  Google-APIs-Image entspricht den vorhandenen Play-Services-Abhängigkeiten;
  Drive, KI und Routes werden nicht angemeldet oder aufgerufen.
- Profil **pixel_5**, Portrait, unveränderte Profilauflösung/-Density:
  nominell **1080 × 2340 px / 440 dpi**, etwa **393 × 851 dp** vor Systemleisten.
  Tatsächliche Werte stehen in `device-environment.txt` und den Font-Berichten.
- KVM zwingend aktiv, 2 Cores, 2048 MB RAM, 512 MB Heap, headless/SwiftShader;
  Kamera deaktiviert, alle drei Android-Animationen über die Action deaktiviert.
- Ein Boot, keine Matrix/Snapshots. Bootgrenze 300 s; Jobgrenze 45 min.
  Test-APKs werden vor dem Boot kompiliert. Emulator-/Testschritt: maximal 20 min.
  Die Emulator-Binär-/Systemimage-Version wird protokolliert; SDK-Pakete folgen
  dem stabilen SDK-Kanal und sind nicht auf eine Image-Revision eingefroren.

Offizielle Grundlage: [Action und Konfigurationsoptionen](https://github.com/ReactiveCircus/android-emulator-runner/tree/v2.38.0),
[Release 2.38.0](https://github.com/ReactiveCircus/android-emulator-runner/releases/tag/v2.38.0).

## Tests

Alle neuen Tests starten die echte `MainActivity`, verwenden Produktions-Room
und SharedPreferences sowie Compose-Semantik; kein zusätzlicher App-/NavHost.

| Klasse | Emulatorprüfung |
| --- | --- |
| ImmoPilotEmulatorSmokeTest | Start/Dashboard und fünf Hauptziele einschließlich Scan-Einstieg |
| NavigationBackstackEmulatorTest | Einheitendetail → Einheitenliste durch injiziertes Android BACK und App-Pfeil; zweites BACK → Objekt |
| PropertyPersistenceEmulatorTest | Fünfstufige Objekt-/Einheitenanlage, deutsche Beträge/Flächen; Screenwechsel, Activity.recreate, Schließen/Neustart mit neuem VM, zweite Room-Verbindung und stabile IDs |
| TenantChronologyEmulatorTest | Ende vor Beginn abgewiesen, danach gültiger Wechsel mit deutschem Mietbetrag dauerhaft gespeichert |
| FinancialFilterEmulatorTest | Zwei Objekte, zwei Belege und bestätigte Bankmiete; Objekt-A-Liste und Einnahmen/Ausgaben/Ergebnis/Buchungszahl konsistent |
| DatevCsvEmulatorTest | Synthetisch freigegebener PDF-Beleg, Wizard über UI, CSV-Auswahl, echte lesbare .csv/EXTF, Exportprotokoll; keine neue ZIP |
| FontScaleEmulatorTest | Echte Systemeinstellung 1,5 vor Activitystart; Dashboard/DATEV-Texte sichtbar und vollständig, ohne Ellipse/Zeilenverlust und innerhalb der Layoutgrenzen (1 px Rundungstoleranz); Export-Einstieg/Pfeil und fünf Tabs bedienbar |

Der vorhandene Paketnamen-Instrumentationtest bleibt bestehen. Breite Logiktests
bleiben in `src/test`. Kein Retry, kein pauschales Sleep; Compose/Espresso-Idling
und begrenztes `waitUntil(10_000)` für asynchrone Speicherung.

## Lokal

Nur auf einem **wegwerfbaren Debug-Emulator ohne eigene Appdaten** ausführen.
Die Testregel verweigert Realgeräte und benötigt eine explizite Freigabe für
Testdatenbereinigung. Sie leert nur die Daten des installierten Testtargets;
Produktionscode und Migrationen werden nicht verändert. Schriftgröße wird
nach jedem Test wiederhergestellt. Alle Daten sind synthetisch, keine Secrets.

```bash
gradle assembleDebug assembleDebugAndroidTest --stacktrace --no-daemon
# Nach dem Emulatorboot, inklusive Diagnoseartefakten:
bash .github/scripts/run-android-emulator-e2e.sh
# Alternativ nur die Instrumentation:
gradle connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.immopilotDisposableEmulator=true \
  --stacktrace --no-daemon
```

Kein Gradle-Wrapper im Projekt; Gradle 9.3.1/Java 17 verwenden. Der Diagnose-Script
setzt Portrait/fontScale 1,0; Animationen und Bootbereitschaft übernimmt in CI die Action.

## Artefakte und Fehlerdiagnose

`android-emulator-e2e-reports` wird mit `always()` für 14 Tage hochgeladen:
Instrumentation-HTML/XML, Geräte-/Font-Konfiguration, Laufzeit/Exitcode,
Dashboard, Einheitendetail/-liste, gespeicherte Immobilie/Neustart,
Mieterwechsel, Finanzfilter, CSV-Ergebnis und Dashboard/DATEV bei Schriftgröße 1,5.
Bei Testfehlern zusätzlich Screenshot/Semantik; bei Laufversagen letzte
Displayaufnahme und Android-Window-XML. Crash-Logcat und ein auf 12.000 Zeilen
begrenztes Runtime/Activity/SQLite/Room/TestRunner-Log werden als Dateien gesichert,
nicht in die Konsole kopiert. Die EXIT-Falle sammelt vor Emulatorabschaltung
und erhält den ursprünglichen Fehlercode. Bei Boot-/SDK-/KVM-Fehlern helfen die
Action-Logs; ohne laufendes Gerät können keine Gerätescreenshots entstehen.
App-/Dialogbilder verwenden Compose `captureToImage`/Android-PixelCopy nach
Frame-Synchronisation. Sie zeigen das App- bzw. Dialogfenster; Systemleisten und
andere überlagernde Fenster sind kein Teil dieser Aufnahme. Bei Diagnosefehlern
bleibt die vollständige Geräteaufnahme über UiAutomation/ADB als Rückfall erhalten.

## Grenzen und Erweiterung

Activity-Neustart mit neuem ViewModel ist kein vollständiger Prozesskill.
Schriftprüfung ist eine gezielte Assertion und visuelle Diagnose, keine vollständige
Pixelregression jedes Screens. Texteingabe nutzt Compose-Testaktionen; reale
IME-/Touch-Eigenheiten werden dadurch nicht umfassend geprüft.
Bei Schriftgröße 1,5 zeigen die Bilder weiterhin ungünstige Umbrüche langer
Kartenlabels und eine gekürzte Branding-Unterzeile/Immobilien-Navbeschriftung.
Die zentralen geprüften Texte, der DATEV-Einstieg und alle fünf Tabs bleiben bedienbar;
dieser CI-PR ist keine vollständige typografische Überarbeitung.
Kein Realgerätetest: Kamera, reale Performance, Drive-OAuth, externe Viewer,
Live-KI/Routes und Kanzlei-DATEV-Import bleiben offen. Keine Kameraabnahme durch
virtuelle Hardware behaupten. Zunächst ein stabiles Profil beibehalten; weitere
APIs/Geräte erst nach belegter Stabilität ergänzen.

## Abnahme dieses PR

Die erste CI-Vorprüfung (Lauf `37945138876`) bestätigte 799 Unit-Tests,
Lint und Debug-Build. API 35 bootete mit KVM in etwa 35 s bei 1080 × 2340 px
und 440 dpi. Vor der Instrumentation scheiterte zunächst die Versionsdiagnose:
Die Action startet den Emulator über den absoluten SDK-Pfad, fügt ihn aber nicht
zum Script-PATH hinzu. Deshalb verwendet das Script jetzt explizit
`$ANDROID_HOME/emulator/emulator` (alternativ `$ANDROID_SDK_ROOT`).
Auch die Diagnose erhält `-no-window -noaudio`, damit sie wie der laufende
Emulator die Headless-Binärdatei ohne Desktop-/PulseAudio-Abhängigkeit verwendet.
Dieser erste Lauf gilt nicht als bestandener E2E-Test.

Vollständige CI-Ergebnisse und Laufzeiten stehen im PR. Ein grün behaupteter
Gerätelauf benötigt erfolgreiche Instrumentation-XML und die zugehörigen
Artefakte. Der PR wird nicht gemergt.
