# Drive-Synchronisation: Dokumentindex und vollständiger Abschluss

Nachtrag zum ausdrücklich angeforderten Merge vom 03.11.2026: PR158/156 sind
nach Main `6dceb5005955d600268f5a8949db7fb2eea7f897` gemergt; PR157 enthalten.
Die frühere Merge-Sperre wurde damit vom Nutzer aufgehoben. Main-CI41677573776
belegt zwei Fehler beim Aufräumen der Anmeldungstests. Separater Folgebranch
`agent/drive1406-test-lifecycle`: Abbruchabschluss vor Main-/HTTP-Reset abwarten,
deterministischer Rot-/Grün-Test, vollständige CI vor Folge-Merge und APK-Freigabe.
Details: `docs/drive-1406-evidence/test-lifecycle.md`.

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
- Zwischenstand1404: öffentlicher Head6b0d5cebfefea47a75c29c31883c0663811566af,
  vollständig grüne CI41572586823 mit1372 Unit-/70 Emulatormethoden, aber Codex-P1
  zum verlorenen bestätigten Zwischenstand nach fehlgeschlagenem Medienversuch.
  Separat vor Korrektur reproduziert (27 Methoden/2 Fehler), gemeinsame minimale
  Journal-Korrektur und28 neue +96 bestehende lokale Methoden grün. Nächster
  tatsächlicher Commit-Parent ist6b0d5ce; neue eindeutige Kandidatenversion1405.

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

Nachtrag03.11.: realer1405-Erstlauf schreibt Dokumentindex und Zusatzsicherung,
scheitert aber bei Abschluss mit LOCAL_PRECONDITION; unveränderter Zweitlauf laut
Nutzer erfolgreich. Tatsächliche abweichende Fingerprint-Komponente weiterhin
unbekannt.1406 ergänzt ausschließlich getestete datensparsame Abschlussdiagnose,
keine gelockerte Sicherheitsprüfung und keine behauptete neue Fehlerbehebung.
Eigener Branchagent/drive1406-completion-proof, tatsächlicher ParentC0c0142.
34 gezielte +96 bestehende Methoden lokal grün; volle neue CI/Signatur und
Geräte-Schreiblauf bleiben gesonderte Prüfaufgaben. Details in
docs/DRIVE_1406_COMPLETION_INVESTIGATION.md.

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
