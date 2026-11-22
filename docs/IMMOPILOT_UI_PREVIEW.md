# ImmoPilot Development Kit – UI-Vorschau (Phase 3)

## Ziel und Geltungsbereich

Neue oder bestehende Compose-Bausteine rasch **visuell** prüfen, ohne die produktive App zu ändern oder echte Mieter-/Bankdaten zu verwenden. Die Vorschau ersetzt **weder** Android-Geräte- und Navigationstests **noch** die verpflichtenden Prüfungen in [VERIFY_CHANGES.md](VERIFY_CHANGES.md).

## Bereits vorhandene Infrastruktur

- `app/build.gradle.kts` enthält das Roborazzi-Gradle-Plugin und die Robolectric-/Compose-/Roborazzi-Testbibliotheken.
- `app/src/test/java/com/example/ui/MoreMenuComposeTest.kt` erzeugt bereits Roborazzi-Bilder unter `app/build/reports/more-menu/`.
- `.github/workflows/android-ci.yml` führt `testDebugUnitTest` mit `-Proborazzi.test.record=true` aus und lädt `app/build/reports/**` als `android-reports` hoch.
- Für die Markenfarben gelten `theme/Theme.kt` und `Ui2Components.kt`; der separate [Komponentenkatalog](IMMOPILOT_COMPONENT_CATALOG.md) befindet sich bis zum Merge von PR #187 noch auf dessen Featurebranch.

## Neues erstes Galeriebild

`app/src/test/java/com/example/ui/Ui2ComponentPreviewTest.kt` rendert ein **synthetisches** Beispiel aus `Ui2Section`, `Ui2Metric`, `Ui2ActionCard` und `Ui2Destination` im produktiven Theme bei `393dp × 852dp` und speichert `app/build/reports/immopilot-ui-preview/components-phone.png`. Ein Test prüft das Vorhandensein wichtiger UI-Texte und die Lesbarkeit/Grundgröße der erzeugten Bilddatei. Er ist **kein** pixelgenauer Designvergleich, kein Navigationstest und kein Speichertest.

## Gezielt lokal ausführen

Das Repository besitzt keinen eingecheckten Gradle-Wrapper. JDK 21 für die Robolectric-Suite verwenden; bei fehlenden Android-SDK-/Gradle-Abhängigkeiten keine erfolgreichen Ausführungen behaupten.

```bash
gradle testDebugUnitTest --tests 'com.example.ui.Ui2ComponentPreviewTest' -Proborazzi.test.record=true --stacktrace --no-daemon
```

Anschließend `app/build/reports/immopilot-ui-preview/components-phone.png` öffnen und mit dem beauftragten Referenz-Screen vergleichen. Unter GitHub Actions entsteht das Bild beim regulären `verify`-Lauf und wird als Teil des bestehenden `android-reports`-Artefakts gespeichert. Für die nächste Entwicklungsstufe können weitere gezielte Galerietests (z. B. Schriftfaktor 1.3/1.5, Leer-/Fehlerzustände, Miet- und Dokumentenkarten) ergänzt werden, **erst** nach Prüfung der vorhandenen passenden Screen-Tests.

## Freigabekriterien

- Bestehendes Theme, Insets, Kartenradius, Abstände und Schrift-Hierarchie visuell gegen fertige ImmoPilot-Screens beurteilen.
- Nur künstliche Daten; keine Zugangsdaten, Dokumentoriginale oder echte Zahlungsvorgänge in Screenshots.
- Gezielten Test und anschließend `docs/VERIFY_CHANGES.md` einschließlich `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, vollständiger JVM-Tests und verpflichtender Android-CI ausführen.
- Keine Änderungen an Bottom Navigation, Top App Bar, Room, Fachlogik, DATEV, Bank oder Backup/Restore durch diese Vorschauaufgabe.
- Bei nicht ausgeführten Schritten den Zustand offen dokumentieren; automatische PR-CI ist ein Prüfversuch, kein vorweggenommenes Bestehen.
