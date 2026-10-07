# Immopilot Projektstatus

Stand: 07.10.2026

## Verifizierte Basis

- Repository: `xedox737/Steuer-Asistend-APP`
- Hauptbranch: `main`
- Verifizierter `main`-SHA beim Erstellen dieser Datei: `57d43bffad59b66bade3217e437c6c09601cbce1`
- Dieser Stand enthält den Merge von PR #110: Bank-Mietkontrolle und Dokument-KI-Review-Grundkorrektur.

## Aktuell offener Funktions-PR

PR #111 – „Fix verbleibende Bank-Mietkontrolle und Dokument-KI-Reviews“

- Branch: `fix/rent-bank-document-review-followup`
- Head: `84c593aa06d9b721deea90b7a286500797428927`
- Status am 07.10.2026: offen, nicht gemergt
- Inhalt: stabile Bank-/Beleg-Deduplizierung in der Mietkontrolle sowie dokumentgebundener KI-Review-State und Rückwege
- Berichteter Prüfstand: `git diff --check`, 729 Unit-Tests, 32 Regressionstests, `lintDebug`, `assembleDebug` erfolgreich
- Noch praktisch zu prüfen: reale Geräte-/Live-KI-/Google-Drive-Wege

Nicht ungeprüft davon ausgehen, dass #111 noch offen ist: vor jeder Folgearbeit GitHub-Status und neuesten `main` erneut prüfen.

## Qualitätsphasen

Phase 1A–1D und Phase 2 wurden bis einschließlich PR #110 in `main` integriert. Der Follow-up zu Phase 2 liegt in PR #111.

Als nachgelagerte Phase-3-Themen vorgemerkt:

- F17: monatliche Rückstände besser fokussieren
- F18: verständliche Objekt-/Einheitenwahl statt interner IDs
- F19: bestätigte bisherige AfA / Restbuchwert erfassbar
- F20: mehrere Einheiten effizient anlegen; Grenze von 20 prüfen
- F21: Android-Schriftgröße auf Start/DATEV respektieren
- F22: verständlicher Backup-Anmeldeweg
- F23: hardcodierte Begrüßung ersetzen
- F24: Scan-Leerzustand vollständig/spracheinheitlich

Diese Liste ist keine automatische Umsetzungsfreigabe.

## Bekannte Prüfgrenzen

Nicht vollständig praktisch belegt: echte Kamera/Picker/Viewer, Live-KI-Routen, Google-Drive-Anmeldung/-Transfer, Cloud-Restore auf neuem Gerät und realer DATEV-Import.

## Veraltete Übergabedokumente

`docs/CODEX_HANDOFF.md` enthält historische Stände aus August 2026 und darf nicht als aktueller Projektstatus verwendet werden. `docs/BUILD_STATUS.md` ist ebenfalls nur ein alter CI-Trigger-/Zwischenstand. Für neue Arbeiten zuerst diese Datei, `AGENTS.md` und den tatsächlichen GitHub-Stand verwenden.
