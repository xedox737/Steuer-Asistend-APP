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

## Vorbereitung für eine spätere kommerzielle Webversion

Die verbindliche Zielarchitektur und die bewusst **noch nicht** freigegebenen Cloud-/Web-Etappen stehen in
[`docs/COMMERCIAL_WEB_READINESS_ARCHITECTURE.md`](docs/COMMERCIAL_WEB_READINESS_ARCHITECTURE.md).
Bei neuen fachlichen Funktionen stabile IDs und Objekt-/Einheitenscope nutzen, fachliche Regeln
von Compose trennen und Datenmodelle für spätere versionierte API-Verträge nachvollziehbar halten.
**Jetzt keine Cloud-Synchronisation, Web-App, Mandantenmigration oder Abofunktion ohne eigenen Auftrag bauen.**

## Verbindliche CI-Prüfstrategie (vor Push und Merge)

1. **Vor dem Push lokal prüfen:** `git diff --check`, Kotlin-Kompilierung und gezielt betroffene Tests; soweit in der Arbeitsumgebung verfügbar außerdem `lintDebug`, `testDebugUnitTest` und `assembleDebug`. Bekannte Kompilier-, Lint- und Unit-Testfehler **vor** dem GitHub-Push beheben.
2. **Fachliche Regressionen gezielt prüfen:** Insbesondere stabile Objekt-/Einheiten-IDs, Mietverläufe, Persistenz, Room, Navigation, Bank, AfA, Fahrtenbuch, DATEV sowie Backup/Restore. Historische Werte nicht allein für grüne Assertions umschreiben.
3. **Emulator auf GitHub:** Fehlt der Codex-Sandbox `/dev/kvm`, Android-Instrumentation über GitHub Actions mit KVM ausführen. Nicht ausgeführte Emulatortests niemals als lokal bestanden melden.
4. **Läufe auswerten statt überholen:** Nach dem Push die Ergebnisse von `verify` und `android-emulator-e2e` abwarten, soweit sie für die aktuelle Fehlerdiagnose relevant sind. Keine neuen Commits erzeugen, die aussagekräftige Emulatorläufe unnötig per Concurrency abbrechen. Ausnahme: Ein eindeutig erkannter Fehler macht den alten Lauf obsolet; das dokumentieren.
5. **Fehler sauber klassifizieren:** Produkt-/Testfehler, Synchronisierung, Infrastruktur und Abbruch anhand von Lognachweisen unterscheiden. Roten Lauf gezielt beheben statt blind neu starten; abgebrochene und übersprungene Jobs nicht als Erfolg zählen.
6. **Keine grünen Tests erzwingen:** Keine Tests deaktivieren, Assertions abschwächen, Timeouts pauschal erhöhen, `continue-on-error` oder automatische Retries zur Fehlerverdeckung einführen.
7. **PR/Abschluss:** Aktuellen `main` berücksichtigen und verpflichtende Checks `verify` und `android-emulator-e2e` vor dem Merge bestehen lassen. CI-Run-IDs, Commit-SHAs, tatsächlich durchgeführte Prüfungen und offene Einschränkungen dokumentieren. Reine APK-Veröffentlichung ersetzt keine Android-CI.

Die Reihenfolge dient dazu, unnötige CI-Runden zu vermeiden; sie garantiert keinen grünen Erstlauf. Falls lokale Prüfungen technisch unmöglich sind, die Einschränkung transparent dokumentieren und die verfügbare Ersatzprüfung nutzen.
