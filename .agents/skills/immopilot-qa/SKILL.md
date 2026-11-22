---
name: immopilot-qa
description: Verwenden, um ImmoPilot-Änderungen risikobasiert und nachweisbar zu prüfen, insbesondere Compose-Layouts, Navigation, Persistenz, lokale Datenintegrität und GitHub-CI; Prüfpflichten nicht abkürzen.
---

# ImmoPilot QA – fokussiert entwickeln, vollständig nachweisen

## Grundsatz
`AGENTS.md` und `docs/VERIFY_CHANGES.md` sind verbindlich; dieser Skill erweitert sie um eine **aufgabenbezogene Prüfauswahl**, ersetzt aber weder Pflichtprüfungen noch Branch-Schutz. Keine Tests, Assertions, Lint-Regeln oder Fehlerprüfungen abschwächen. Komponentenkatalog: `docs/IMMOPILOT_COMPONENT_CATALOG.md`.

## Ablauf
1. `origin/main`, Branch, Arbeitsstatus, beauftragten Scope und tatsächliche Diff-Pfade prüfen. Fremde Änderungen nicht übernehmen. Risiken anhand geänderter Funktionen und Abhängigkeiten klassifizieren; dokumentieren, warum weitere Bereiche unberührt bleiben.
2. Bei Fehlern zuerst reproduzierbaren Test für die Ursache schreiben (RED); bei reinem Dokumentations-/Skill-Change strukturelle und inhaltliche Prüfung statt erfundener App-Tests. Für UI mit Datenänderung gezielte Unit-, Compose- oder Android-Tests; bei reiner Darstellung passende Screenshot-/FontScale- und Navigationstests.
3. Besondere Prüfpunkte nach Änderungsart:
   - **Navigation:** System-Zurück, App-Pfeil, Bottom Navigation, Backstack, Dialog-/Picker-Abbruch.
   - **Persistenz/Room:** Migrationen ohne Verlust, stabile IDs, Neustart, StateFlow und Main-Thread-Last.
   - **Bank/Miete:** Importdubletten, Links, Splits, Zuordnung, Soll/Ist und keine private Doppelzählung.
   - **Belege/Dokumente:** Originalbytes, MIME, PDF/JPG/PNG/WEBP, URI-Berechtigungen, Objekt-/Einheitenbezug.
   - **DATEV:** geprüfte Freigaben, Konten/Kostenstellen, EXTF, CSV/ZIP und Originale; keine ungeprüften KI-Werte.
   - **Backup/Restore/Drive:** lokale Nutzung ohne Cloud, Konflikte, IDs/Referenzen, Idempotenz, Restore-Wiederholung.
   - **AfA/Fahrtenbuch:** historische Werte, Rechenweg, Zuordnung, Storno, Speichern und Neustart.
   - **Compose:** Theme/Wiederverwendung, 393 dp, Schriftfaktor 1.0/1.3/1.5, Tastatur, Kontrast, leere und fehlerhafte Zustände.
4. Während der Arbeit fokussierte relevante Prüfungen vorziehen; vor Push/PR die verbindliche Standardprüfung aus `docs/VERIFY_CHANGES.md` beachten: Java 21 `gradle testDebugUnitTest -Proborazzi.test.record=true --stacktrace --no-daemon` und `python3 .github/scripts/verify-test-execution.py`; Java 17 `gradle lintDebug --stacktrace --no-daemon`, `gradle assembleDebug --stacktrace --no-daemon`, `gradle assembleDebugAndroidTest --stacktrace --no-daemon`; `git diff --check`. Kein eingecheckter Gradle-Wrapper.
5. Ist die vollständige Prüfung technisch nicht ausführbar, Einschränkung, Umgebung und Ersatznachweise konkret dokumentieren; keine lokale Android-Ausführung ohne Emulator/KVM behaupten. Verpflichtende GitHub-Checks `verify` und `android-emulator-e2e` mit SHA/Run-ID vor Merge kontrollieren. `cancelled`, `skipped`, `pending` und unbekannt sind nicht grün.
6. Vor Merge aktuelle Basis, Konflikte, Diff und CI erneut prüfen. Merge nur nach ausdrücklicher Nutzerfreigabe. Wenn der Auftrag nur einen PR verlangt, offen lassen.

## Prüfbericht (Pflichtstruktur)
- Basis-SHA, Branch, Head-SHA und Diff-Dateien
- Geprüfte Risiken und bewusst unberührte Fachbereiche
- Für jede Prüfung: Befehl/CI-Name, Umgebung, **ausgeführt / bestanden / fehlgeschlagen / nicht ausgeführt**, Beleg (Log/Run-ID)
- Offene Daten-, Navigations-, Geräte- oder Cloud-Abnahme und verbleibende Risiken
- Push-/PR-/Merge-Status ohne Behauptungen über nicht durchgeführte Schritte

## Dokumentationsänderungen
Bei ausschließlich neu angelegten `.md`-Dateien ohne Android-Code- oder Build-Konfigurationsänderung sind Link-/Pfad-/Frontmatter-/Diff-Prüfungen unmittelbar relevant. Die aus dem Repository folgenden vor-PR-/CI-Anforderungen bleiben bestehen; fehlende Ausführung explizit ausweisen, nicht stillschweigend als bestanden deklarieren.
