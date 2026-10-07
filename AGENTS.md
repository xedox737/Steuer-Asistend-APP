# Immopilot / Steuer-Assistent – Arbeitsregeln

Diese Datei ist der schnelle Einstieg für Arbeiten am Repository. Für Details siehe `docs/PROJECT_STATUS.md`, `docs/CODE_MAP.md` und `docs/VERIFY_CHANGES.md`.

## Vor jeder Änderung

1. Aktuellen `main` abrufen und eigenen Branch verwenden.
2. `git status` prüfen; fremde Änderungen nicht überschreiben.
3. Bestehende Implementierung und angrenzende Daten-/Navigationspfade lesen.
4. Ursache statt Symptom beheben; kleine, nachvollziehbare Änderungen bevorzugen.
5. Keine bestehende Funktionalität entfernen oder durch vereinfachte Ersatzlogik ersetzen.

## Kritische Projektregeln

- Datenverlust ist nicht akzeptabel.
- Keine destruktiven Room-Migrationen.
- IDs, Dateiverweise, Objekt-/Einheiten-/Mietzuordnungen, Bankdaten, AfA- und Fahrtenbuchdaten erhalten.
- Backup/Restore lokal-first, idempotent und ohne Dubletten.
- Bestehende Bankbuchungsliste und DATEV-Logik nur ändern, wenn der Auftrag das ausdrücklich verlangt.
- Immopilot-Kopfbereich und globale Bottom Navigation nur bei ausdrücklichem Auftrag ändern.
- Android-Zurück und App-Zurück müssen logisch zum vorherigen Screen führen.
- Vorhandene Compose-Komponenten und das bestehende Immopilot-Designsystem verwenden.
- Keine API-Keys, Tokens, Passwörter oder privaten Schlüssel committen.

## Besonders mitprüfen

Navigation/Backstack, Room/Migrationen, ViewModels/StateFlow/Coroutines, Google Drive/Backup/Restore, Belege/Dokumente, Bank, Immobilien/Einheiten/Mietverhältnisse, AfA, Fahrtenbuch, DATEV, Einstellungen und KI-Funktionen.

## Fertig-Kriterium

Eine Änderung ist erst fertig, wenn relevante Tests, `lintDebug`, `assembleDebug` und `git diff --check` erfolgreich sind oder ein konkreter externer Prüfgrund dokumentiert ist. Keine Tests deaktivieren oder Fehler verstecken.
