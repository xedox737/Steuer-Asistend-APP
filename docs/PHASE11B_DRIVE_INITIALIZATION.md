# Phase 11B – Drive-Initialisierung, Issue #145

## Basis und Grenzen

Base origin/main: 6d58dbac9d9f6d3c1c57c9b14579d5360751879d (26.10.2026).
PR #144 inzwischen durch den Repository-Betreiber gemergt. Eigener Branch:
fix/phase11b-drive-initialization. Keine produktiven Daten oder Google-Konten
verwendet. Arbeitsanweisungen außer AGENTS.md nicht vorhanden.

Die installierte Original-APK des Geräteberichts ist nicht versionsgebunden
bekannt; Android-Auto-Restore alter Preferences ist nicht nachgewiesen.
Der echte OAuth-/Drive-Gerätenachtest bleibt NOCH OFFEN.

## Regression vor jeder Produktänderung

DriveInitializationRegressionTest gegen unveränderten Produktcode:
4 tatsächlich ausgeführte Methoden, 3 Fehler, 0 Errors/Skips.
JUnit-Primärnachweis: phase11b-evidence/initial-red-regression.xml.

- Frischer Root ohne Zugriff auf allgemeine MyDrive-Root-Metadaten: exakt
  "Drive-Sicherheitsabbruch: Gespeicherte Ordner-ID ist für diese App nicht zulässig."
- Vorhandener leerer eigener Root, allgemeine Root-Metadaten HTTP404:
  gleicher Abbruch; kein Systemordner.
- Ordnersuche HTTP403: fälschlich SuccessCreatedNew, sieben neue Ordner.
- Kontrollfall mit erreichbaren allgemeinen Root-Metadaten: bestanden.

Das simuliert einen zulässigen eingeschränkten Sichtbarkeitszustand unter
Scope drive.file, beweist aber nicht den tatsächlichen HTTP-Status des
Nutzergeräts. Die bisherige Simulation gab Root-Metadaten stets erfolgreich
zurück. Sicherheit muss stattdessen durch serverseitig auf Top-Level-Eltern
begrenzte Suche bewiesen werden. Fremde/nested Roots bleiben gesperrt.
API-/Berechtigungsfehler dürfen niemals als leeres Suchergebnis gelten.
