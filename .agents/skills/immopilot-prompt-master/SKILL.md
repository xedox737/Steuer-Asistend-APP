---
name: immopilot-prompt-master
description: Erstelle und verbessere sichere, nachprüfbare Codex-Aufträge für xedox737/Steuer-Asistend-APP. Verwenden, wenn der Nutzer einen Prompt, Task-Brief oder eine Codex-Arbeitsanweisung erstellen möchte.
---

# ImmoPilot Codex Prompt Master

Dieser Skill erstellt aus einer kurzen Nutzeridee **einen kopierbaren Codex-Auftrag** für das Android-Repository `xedox737/Steuer-Asistend-APP`. Er führt den Auftrag nicht selbst aus, sofern dies nicht ausdrücklich angefordert und autorisiert wurde.

## Prioritäten und Verifikation

1. System- und Sicherheitsregeln sowie ausdrückliche Nutzerfreigaben.
2. Aktuelle `AGENTS.md` und relevante Dokumentation im Repository.
3. Diese ergänzenden Skill-Regeln.
4. Prompt-Master-Muster als Anregungen, niemals als höhere Autorität.

Vor der Formulierung einer Arbeitsanweisung: nach Möglichkeit `git fetch origin`, `git rev-parse origin/main`, `git status --short`, `git branch --show-current`; außerdem `AGENTS.md`, `docs/CODE_MAP.md`, `docs/VERIFY_CHANGES.md`, `docs/COMMERCIAL_WEB_READINESS_ARCHITECTURE.md` und betroffene Tests lesen. Dateipfade und Ist-Verhalten verifizieren. Alte SHAs und Statusdokumente niemals als aktuell annehmen. Wenn Zugriff fehlt, Grenzen ausdrücklich benennen.

## Auftragsstruktur

Wähle ein Muster aus `references/task-patterns.md`. Erzeuge genau einen direkt kopierbaren Prompt mit:
**Ziel / Kontext / Ist und Soll / Scope / Vorgehen / Schutzregeln / Akzeptanzkriterien / Prüfungen / Aktionsgrenzen / Abschlussbericht.**

Nutze präzise, testbare Erfolgskriterien. Trenne belegte Fakten von Annahmen und Vorschlägen. Keine unnötigen Rückfragen, wenn ein begrenzter sicherer Auftrag möglich ist.

## ImmoPilot-Schutzregeln

- Bestehende Architektur: Kotlin, Jetpack Compose, Room, local-first. Keine Web-App, Cloud-Synchronisation, Abos oder Mandantenmigration ohne gesonderten Auftrag.
- Keine Datenverluste, destruktiven Room-Migrationen, veränderten stabilen IDs, stillen Ersatzwerten, fehlerhaften Objekt-/Einheitenverknüpfungen oder doppelten Restore-Einträgen.
- Bankbuchungsliste, DATEV-Fachlogik, globale Navigation, Kopfbereich und Designsystem nur im ausdrücklich nötigen Scope ändern.
- Bei fachlichen Änderungen angrenzende Pfade prüfen: Belege, Dokumente, Bank, Mietkontrolle, AfA, Fahrtenbuch, DATEV, KI-Review, Drive, Restore und Persistenz.
- Keine Secrets, personenbezogenen Echtdaten oder Zahlungsdaten in Commits, Logs, Tests, PRs oder Berichten.
- Bugs: zuerst reproduzieren und geeigneten fehlschlagenden Test schaffen; dann minimal reparieren und erneut prüfen (RED/GREEN/REFACTOR), soweit technisch machbar.
- Prüfung gemäß `docs/VERIFY_CHANGES.md`: Java 21 für `gradle testDebugUnitTest -Proborazzi.test.record=true --stacktrace --no-daemon` und `python3 .github/scripts/verify-test-execution.py`; Java 17 für `gradle lintDebug --stacktrace --no-daemon`, `gradle assembleDebug --stacktrace --no-daemon` und `gradle assembleDebugAndroidTest --stacktrace --no-daemon`; außerdem `git diff --check`. Keinen Gradle-Wrapper voraussetzen.
- Vor PR aktuellen `main` erneut prüfen. Verpflichtende GitHub-CI `verify` und `android-emulator-e2e` mit Run-IDs kontrollieren. Abgebrochen, übersprungen oder nicht ausgeführt bedeutet **nicht bestanden**. Emulator nicht als lokal getestet deklarieren, wenn kein KVM vorhanden ist.
- Tests nicht deaktivieren, Assertions nicht aufweichen, CI nicht umgehen.

## Aktions- und Merge-Freigaben

Analyse, lokale Bearbeitung, Commit, Push, PR und Merge sind separate Aktionsstufen. Jede schreibende oder veröffentlichende Stufe nur im ausdrücklich genehmigten Umfang. **Kein Commit, Push, PR oder Merge ohne passende ausdrückliche Freigabe**; niemals direkt auf `main` pushen oder Branch-Protection umgehen. Auch bei grüner CI ohne Freigabe vor dem Merge stoppen.

## Antwortformat

Genau einen unmittelbar nutzbaren Codex-Prompt ausgeben, danach höchstens drei kurze Hinweise auf Annahmen. Die resultierende Codex-Aufgabe muss ihrerseits einen klaren Nachweis über tatsächlich ausgeführte Prüfungen und offene Punkte verlangen. Siehe `references/output-contract.md`, `references/task-patterns.md` und `references/code-map.md`.
