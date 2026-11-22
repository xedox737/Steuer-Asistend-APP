---
name: immopilot-design
description: Verwenden, wenn ImmoPilot-Screens visuell gestaltet, vereinheitlicht oder mit einer Referenzansicht verglichen werden sollen; bewahrt vorhandene Markenidentität, Compose-Komponenten und Navigation.
---

# ImmoPilot Design – bestehende Identität bewahren

## Auftrag
Designs **innerhalb** des bestehenden nativen Kotlin-/Compose-/Material-3-Systems entwerfen oder gezielt verbessern. Keine zweite UI-Bibliothek oder ein paralleles Designsystem erzeugen. Dieser Skill ergänzt die übergeordneten Regeln aus `AGENTS.md`, `docs/VERIFY_CHANGES.md` und `docs/IMMOPILOT_COMPONENT_CATALOG.md`; keine Regel überschreiben.

## Vorgehen
1. Aktuellen `origin/main`, Branch und `git status --short` prüfen; fremde Änderungen unangetastet lassen. Tatsächlichen UI-Einstieg sowie aktuelle Theme-/Komponentendateien lesen.
2. Den Ziel-Screen und **zwei passende fertige ImmoPilot-Referenzscreens** auswählen. Bestehende Karte, Iconfläche, Abstände, Typografie, Top App Bar, Bottom Navigation, Dialoge und Felder vergleichen. Nutzerbilder anhand Größe, Position, Reihenfolge und Alignment verwenden.
3. Zuerst `Ui2Section`, `Ui2ActionCard`, `Ui2Metric`, `Ui2Destination`, `Ui2Grid`, `AppDateField` und vorhandene Feature-Komponenten auf Eignung prüfen. Paket-Sichtbarkeit (`internal`) beachten. Wiederverwendung ist wichtiger als neue generische Abstraktionen.
4. Bestehende Theme-Farben verwenden. Blau für bisherige Primäraktionen und Grün/Mint nur dort, wo dies zur bestehenden Semantik und Marke passt. Kein pauschales Violett, keine fremden Design-Tokens. Material 3 Expressive nur selektiv und nach Prüfung der tatsächlich verfügbaren Abhängigkeiten/API.
5. Benennungen alltagstauglich halten; Lade-, Leer-, Fehler- und Bestätigungszustände visuell nachvollziehbar. Schriftgrößen 1.0/1.3/1.5, schmale Breiten, lange Texte, Kontrast und bedienbare Touchflächen berücksichtigen.
6. Änderungen minimal und nur am beauftragten Screen vornehmen. Header und globale Bottom Navigation niemals ohne ausdrücklichen Auftrag verändern. Bei UI-Navigation System-Zurück, App-Zurück, Dialog-Abbruch und Rückkehr nach Speichern prüfen.

## Abnahme
- Vorher/Nachher-Screenshots oder Compose-Previews mit **synthetischen Daten**, soweit Testumgebung verfügbar; fehlende Bildprüfung offen angeben.
- Optische Unterschiede zu Referenzen und bewusst unveränderte Elemente dokumentieren.
- Betroffene UI-/Navigationstests plus verbindliche Prüfungen aus `docs/VERIFY_CHANGES.md`; `verify` und `android-emulator-e2e` vor Merge, keine nicht ausgeführten Tests als grün deklarieren.
- Kein Eingriff in Room, Fachlogik, Belegoriginale, Bankliste, DATEV, Backup/Restore aufgrund einer reinen Designaufgabe.

## Ausgabe
Zielscreen, Referenzen und wiederverwendete Komponenten; geänderte Dateien; visuelle Gegenüberstellung; relevante Tests/CI mit Status und Run-ID; Risiken und offene Geräteprüfungen. Push/PR/Merge ausschließlich innerhalb ausdrücklich genehmigter Aktionsgrenzen.
