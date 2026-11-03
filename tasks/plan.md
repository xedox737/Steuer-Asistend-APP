# Drive-Synchronisation: Dokumentindex und vollständiger Abschluss

Freigegebener Nutzerplan; kein automatischer Merge. Erledigt erst nach vollständigem
erfolgreichem realem Sync und unveränderter Wiederholung mit dem betroffenen Bestand.

## Ausgangspunkt

- Aktueller Main über GitHub: 5d5e61437fcf7324bb5e0f6442669f777d8ddeda.
- Aktiver 1403-Stand/PR158: 6f4f1f65575fd1ad10d66a9dd78e6d797be08945.
- Separater Branch: agent/drive1404-sync-integrity. Lokaler Ausgangscommit 661893a
  materialisiert exakt denselben Quellbaum wie GitHub6f4f1f6; Originalhistorie nicht pushen.
- Ursprünglicher Arbeitsbaum sauber; git fetch scheitert am Proxy, gh-Token ungültig.
  GitHub-Connector für Veröffentlichung/CI/gewählten Codex-Review autorisiert.
- Realbericht 02.11.2026 21:20:12Z:22Belege abgeglichen,24beleggebundene Dokumente,
  danach Dokumentindex BACKUP_PREIMAGE_CONFLICT; kein Index-Schreibzugriff.

## Vorgehen

1. Tatsächlichen Unterschied mit privaten lesend bezogenen Sicherungen und unabhängigen
   synthetischen Fehlertests nachweisen. Lokale/simulierte Bestände strikt isolieren.
2. Restore-Reihenfolge, lokale Änderungen, stabile IDs, bekannte Baselines und die
   gemeinsame JSON-Sicherheitsprüfung untersuchen. Keine pauschale Lockerung.
3. Minimale Korrektur der nachgewiesenen Ursache. Gleiche Inhalte bleiben No-Ops;
   echte Fremdänderungen bleiben erhalten. Präzise Diagnose ohne private Werte.
4. Gesamten Ablauf einschließlich Anhängen, Dokumentindex, Zusatzbackup, Abschluss,
   Unterbrechung/Wiederaufnahme, zweimaligem Restore und zweitem No-Op prüfen.
5. API-Kosten messen, nur bewiesen sichere gezielte Reduktion wiederholter Abfragen.
6. Gesamte Unit-Suite, Lint, APK-Build, tatsächliche CI-Emulatormethoden und Review;
   Signatur, Paket und neue eindeutige Buildversion unabhängig prüfen.
7. Reale POCO-/Drive-Abnahme durchführen bzw. notwendigen Gerätetest konkret ermöglichen.
   Automatisierte Prüfungen alleine erfüllen das abschließende Fertig-Kriterium nicht.

## Grenzen

Keine Löschung von Nutzerdaten/Drive-Dateien, kein Zurücksetzen, keine destruktive Migration,
kein Umgehen fehlender Versionsnachweise und keine automatische Wiederherstellung.
Auth, Bank-/DATEV-Fachlogik und Navigation erhalten. Private Sicherungsdateien liegen
außerhalb von Git und CI-Artefakten. Test-/Produktfehler von Infrastruktur unterscheiden.

## Quellen

- Android Offline-first: https://developer.android.com/topic/architecture/data-layer/offline-first
- HTTP If-Match: https://www.rfc-editor.org/rfc/rfc9110.html#section-13.1.1
- Drive Uploads: https://developers.google.com/workspace/drive/api/guides/manage-uploads
- Drive Performance: https://developers.google.com/workspace/drive/api/guides/performance
- Drive Changes: https://developers.google.com/workspace/drive/api/guides/manage-changes
