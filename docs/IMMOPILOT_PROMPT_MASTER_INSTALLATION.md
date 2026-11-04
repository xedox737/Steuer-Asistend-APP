# Codex Prompt Master: Installation und Nutzung

## Installation

Der Skill ist auf dem Branch `chore/immopilot-codex-prompt-master` unter `.agents/skills/immopilot-prompt-master/` abgelegt. Nach dem ausdrücklich genehmigten Merge nach `main` ist er für Codex-Sitzungen verfügbar, die das aktualisierte Repository verwenden.

1. Repository im Codex-Workspace öffnen und aktualisieren.
2. Bei Bedarf neue Codex-Sitzung öffnen, damit die Skill-Erkennung aktualisiert wird.
3. Im Auftrag `$immopilot-prompt-master` nennen.
4. Beispielsweise: `Nutze $immopilot-prompt-master: Erstelle einen Bugfix-Prompt für doppelte Bankmieteingänge. Noch keine Änderungen.`
5. Den generierten Prompt prüfen und separat als Entwicklungsauftrag verwenden.

Der Skill ersetzt keine `AGENTS.md` und keine Prüfpflichten. Fehlt automatische Skill-Erkennung, die `SKILL.md` ausdrücklich im Kontext bereitstellen.

## Freigaben

Analysieren != Code ändern != Commit != Push != PR != Merge. Jede Stufe erfordert eine passende ausdrückliche Nutzerfreigabe. Keine Direkt-Commits auf main. Pull Requests nur nach Prüfung der für sie relevanten Checks mergen.

## Aktualisierung

Änderungen am Prompt Master in einem eigenen Branch und PR durchführen. Neue Repository-Regeln zuerst aus `AGENTS.md` und `docs/VERIFY_CHANGES.md` übernehmen.
