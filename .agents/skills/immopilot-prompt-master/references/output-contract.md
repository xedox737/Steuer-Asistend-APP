# Output-Vertrag für generierte Codex-Arbeitsaufträge

Ein fertiger Auftrag muss enthalten:

1. Repository `xedox737/Steuer-Asistend-APP`, Zielbranch und Pflicht zur erneuten aktuellen Git-Verifikation.
2. Verifizierbare Ziele mit reproduzierbarem Ist-/Soll-Verhalten.
3. Erlaubte und ausdrücklich verbotene Änderungen.
4. Architektur- und Datenintegritäts-Schutzregeln aus aktueller `AGENTS.md`.
5. Vorgehen mit Ursachenanalyse und minimaler Änderung.
6. Testplan nach `docs/VERIFY_CHANGES.md`, passend zu Scope und Umgebung.
7. CI-Prüfung mit SHA und Run-IDs, wenn Push/PR autorisiert sind.
8. Explizite Stufe für Commit/Push/PR/Merge. Keine implizite Erlaubnis.

Abschlussbericht-Format:

- Basis: Branch, Base-SHA, Head-SHA, etwaige PR-Nummer.
- Änderungen: konkrete Dateipfade, fachliche Wirkung, Diff-Statistik.
- Tests: tatsächlich ausgeführte Befehle, Ergebnisse, Blocker, keine erfundenen Erfolgsangaben.
- Risiken: Datenintegrität, Migrationen, Navigation, Offline-Modus und relevante Regressionen.
- CI: Workflow/Run-ID/Status, klar von lokalen Tests getrennt.
- Status: erledigt, offen, blockiert; nächste notwendige Freigabe.

Keine Behauptung `grün` ohne entsprechende Test- oder CI-Evidenz. Keine automatischen Merges.
