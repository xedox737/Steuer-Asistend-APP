---
name: immopilot-screen-builder
description: Verwenden, wenn ein neuer ImmoPilot-Compose-Screen oder eine gezielte Screen-Erweiterung aus einer fachlichen Nutzerbeschreibung umgesetzt werden soll; vorhandene UI, Datenverträge und Navigation wiederverwenden.
---

# ImmoPilot Screen Builder – Beschreibung zu nativer Compose-Oberfläche

## Geltung
Native Android-App, local-first; **kein** Web-App-Generator. Dieser Skill führt beauftragte UI-Aufgaben aus und unterscheidet sich vom `immopilot-prompt-master`, der nur Codex-Aufträge formuliert. `AGENTS.md` und `docs/VERIFY_CHANGES.md` sind verbindlich. Den Komponentenkatalog `docs/IMMOPILOT_COMPONENT_CATALOG.md` sowie bei Layout-Aufgaben `immopilot-design` verwenden.

## Umsetzung
1. `git branch --show-current`, `git status --short`, `git fetch origin` und tatsächlichen `origin/main`-SHA prüfen; separaten Featurebranch nutzen. Scope, Eintritts- und Rückweg, Dateneigentümer, erwartete Nutzeraktion sowie Akzeptanzkriterien knapp festhalten.
2. In `docs/CODE_MAP.md` die betroffenen Feature-Quellen ermitteln; vorhandene Screens, Composables, ViewModels, StateFlow, Repositories und Tests nur im relevanten Pfad lesen. Bereits vorhandene Lösungen ermitteln, keine doppelte Screen-/State-Logik erstellen.
3. UI-Bausteine aus `Ui2Components.kt`, Theme und `AppDateField.kt` bevorzugen. Zustand aus dem bestehenden ViewModel empfangen; nur flüchtigen UI-Zustand lokal verwalten. Fachberechnungen, Validierung und Datenpersistenz nicht in Composables duplizieren.
4. Falls ein neuer Datenvertrag nötig ist, die Fachänderung explizit benennen und gesondert risikobasiert prüfen; **nicht** in einem reinen Screen-Auftrag stillschweigend Room-Schema, IDs, Beleglinks, Bankbuchungen, DATEV oder Backup ändern.
5. Navigation in bestehende `AppScreen`-/Backstack-Flüsse einhängen, ohne globale Top App Bar oder Bottom Navigation zu ändern (außer ausdrücklich beauftragt). Android-Zurück, Zurück-Pfeil, Abbrechen und Rückkehr nach Speichern sind Akzeptanzkriterien.
6. Für eine Vorschau vorhandene Preview-, Screenshot- oder Android-UI-Tests bevorzugen, falls vorhanden. Synthetische Beispieldaten für leer/geladen/Fehler sowie große Schrift nutzen. Eine Preview ist kein Beweis für Speicherung, Restore oder End-to-End-Funktionen.
7. Kleine, nachvollziehbare Commits; keine ganzen Dateien ersetzen, wenn eine lokale Anpassung reicht. Unabhängige Agenten können Referenzdesign und Tests parallel bearbeiten, aber Schreibbereiche und Integrationsverantwortung müssen getrennt bleiben.

## Abnahme und Freigabegrenzen
- Eigene Anforderungen auf Screens, Datenherkunft, Formulareingaben, Seiteneffekte und Navigation abbilden.
- Bei Logik-/Verhaltensänderungen zunächst fehlschlagenden Regressionstest und danach Umsetzung; sonst geeignete Layout-/Screenshot-Prüfung.
- Tests, Lint, Build, `git diff --check` und erforderliche GitHub-CI nach `docs/VERIFY_CHANGES.md` ausführen; fehlende lokale Werkzeuge oder CI transparent melden, niemals „bestanden“ behaupten.
- Kein Push, PR oder Merge ohne die jeweils ausdrücklich erteilte Freigabe; kein Merge aus einem automatisch generierten Screen.

## Ergebnis
Dateiliste, tatsächlich wiederverwendete Komponenten, Screenshot/Preview-Nachweise, nachvollziehbarer Zustands-/Navigationsfluss, ausgeführte Tests, CI-Status, aktuelle SHAs und verbleibende Risiken.
