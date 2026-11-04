# ImmoPilot Codex Prompt Master

Repository-spezifischer Codex-Skill für die Erstellung sicherer, prüfbarer Entwicklungsaufträge. Grundlage: die vom Nutzer bereitgestellte Prompt-Master-1.8.2-ZIP und die aktuellen ImmoPilot-Arbeitsregeln.

## Anwendung

Starte Codex im Repository und schreibe:

```
Nutze $immopilot-prompt-master und erstelle einen Audit-Auftrag für die Beleg- und Bankzuordnung. Nur Analyse, keine Codeänderungen.
```

Weitere Beispiele in `examples/`.

## Wichtige Einschränkungen

- Der Skill **formuliert Aufträge**; er ändert App-Code nicht automatisch.
- `AGENTS.md`, `docs/VERIFY_CHANGES.md` und `docs/CODE_MAP.md` bleiben maßgeblich.
- Auf einem eigenen Arbeitsbranch installiert; keine Änderung von `main` ohne PR/Merge-Freigabe.
- Commit, Push, PR und Merge werden als getrennte genehmigungspflichtige Aktionen behandelt.

Die im ursprünglichen ZIP enthaltenen umfangreichen generischen Prompt-Master-Originalreferenzen sind **nicht** Teil dieser schlanken Repo-Installation; diese enthält die für ImmoPilot angepassten Kernregeln.
