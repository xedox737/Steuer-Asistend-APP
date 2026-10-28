Fixes #148.

Nach einem erfolgreichen Login konnten Netzwerk-/Initialisierungsfehler den Drive-Kontostatus fälschlich zurücksetzen. Gleichzeitig gingen HTTP401 und Coroutine-Abbrüche im Repository als Fehlertext verloren. Der Fix erhält die Anmeldung bei normalen Drive-Fehlern, erkennt echte Autorisierungsanforderungen typisiert, erneuert abgelehnte native Tokens begrenzt und serialisiert die vollständige Konfigurationsinitialisierung.

Web-/manuelle Token behalten ihre Ablaufzeit über Neustarts; ein nativer Kontowechsel übernimmt kein altes Web-Token. Der Hauptlogin verwendet den vorhandenen Play-Services-Pfad mit unverändertem `drive.file`-Scope. Die bestehende Namespace-Policy, Room- und Backupformate bleiben unverändert.

Base: `6f8f26f4055c21a0df994e52b970f3fecbf4889d`. Reparatur: `2b58e6aaab913acb270695512b7fe4b4cbef1cf4`.

Vor dem Produktfix: 12 ausgeführte ergänzende SDK35-Regressionen, davon 9 rot. Danach: 137 eindeutige ergänzende JVM-Methoden ohne Fehler/Skips, einschließlich 23 neuer Verbindungsfälle und 36 bisheriger Reparaturmethoden. Frischer Full-Restore und zweimal MERGE im simulierten HTTP-Drive: 60/60 Original-SHA256, keine Contract-/Relations-/ID-Fehler. Kein echter OAuth-/Gerätenachweis.

**Draft / keine Freigabe:** Vollständige Gradle-/SDK36-Suite, Lint, Emulator-CI und beide neuen APKs sind noch offen. Lokaler Gradle-Start scheitert an der Sandbox-Netzwerkumgebung; /dev/kvm fehlt. Signaturkompatibilität der installierten Test-App muss mit dem tatsächlichen bisherigen Schlüssel geklärt werden. Keine Deinstallation und kein automatischer Merge.

Bericht: `docs/ImmoPilot-Phase11C-Reparaturbericht.pdf` / `.md`; detaillierte lokale Nachweise: `docs/phase11c-evidence/`. Die bestehende Test-App ist lokal auf `agent/phase11c-isolated-drive` integriert; gemeinsame Main-Quellen unterscheiden sich ausschließlich durch die bestehenden QA-UI-Hooks.
