# Aufgabenmuster

## Audit, nur lesen
- Repository-Version und betroffene Dateien ermitteln.
- Architektur, Fehlerquellen, Sicherheit, Performance und Tests anhand von Beweisen prüfen.
- Keine Dateien ändern, Commits erstellen, pushen oder mergen.
- Findings mit Schwere P0–P3, Pfad/Zeilen, Ursache, Auswirkung und Vorschlag berichten.

## Bugfix
- Ist/Soll konkretisieren und reproduzieren.
- Relevante bestehenden Tests und angrenzende Datenflüsse prüfen.
- Vor Änderung Regressionstest, möglichst zunächst rot.
- Minimalen Fix implementieren; Test grün; angrenzende Regression prüfen.
- Keine außerhalb des Auftrags liegenden Umstrukturierungen; Aktionsfreigaben beachten.

## Feature
- Fachliche Akzeptanzkriterien und Datenmodell definieren.
- Bestehende UI/Repository/Use-Cases und Schnittstellen weiterverwenden.
- Stabile IDs und local-first erhalten; Randfälle und Migrationen testen.
- Keine neue Cloud-/Webfunktion ohne spezifischen Auftrag.

## Refactoring
- Zuerst beobachtbares Verhalten und Basistests festhalten.
- Änderungen klein halten, keine fachliche Semantik ändern.
- Vorher/Nachher-Diff und Regressionstests dokumentieren.

## PR-Review
- Base-SHA, Head-SHA, Dateien, Diffs und CI-Runs prüfen.
- Datenintegrität, Fehlerbehandlung, Tests und Scope kontrollieren.
- Keine eigenständige Änderung und kein Merge, außer separat genehmigt.

## Merge-Gate
- Aktuelle Base/Head und Branch-Schutz kontrollieren.
- Relevante Checks inklusive verify/android-emulator-e2e verifizieren.
- Konflikte, Reviews und offene Findings ausweisen.
- Bis zur ausdrücklichen Merge-Freigabe stoppen.
