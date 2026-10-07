# Immopilot – Prüfablauf für Änderungen

Die Befehle beziehen sich auf die aktuelle CI-Konfiguration, die Gradle 9.3.1 mit Java 17 verwendet. Das Repository besitzt derzeit keinen eingecheckten Gradle-Wrapper; lokal deshalb ein kompatibles `gradle` verwenden oder die GitHub-CI als Referenz nutzen.

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

```bash
gradle testDebugUnitTest -Proborazzi.test.record=true --stacktrace --no-daemon
gradle lintDebug --stacktrace --no-daemon
gradle assembleDebug --stacktrace --no-daemon
git diff --check
```

Relevante fokussierte Tests dürfen vorher laufen, ersetzen die Standardprüfung aber nicht.

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
