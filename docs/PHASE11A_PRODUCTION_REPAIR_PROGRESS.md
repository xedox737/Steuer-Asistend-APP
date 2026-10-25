# Phase11A Produktreparatur – laufender Arbeitsstand

Base: 69f35772b6480a958ca7479b024dbd4d8bd42fa1.
Branch: fix/phase11a-production-data-integrity.
Audit: 321a8c30cd8ec5c9b20ad02e4758504976cd787b.

Dieser Stand ist keine fertige Reparatur und keine Freigabe.

## E11A-03 – begonnen

Der Beleg-Sync übernimmt bei vorhandenen ManagedDocuments ausschließlich technische
Übertragungsfelder. Alle übrigen vorhandenen Felder einschließlich unitId,
Mieter-/Darlehens-/Sanierungsreferenzen, Review, OCR und authored updatedAt bleiben
unverändert. Einheiten werden nur bei tatsächlich neuen Dokumenten initialisiert;
bestehende fehlende Einheiten werden nicht automatisch repariert. Widersprüchliche
Immobilien-/Belegidentitäten werden vor Remote-Mutationen zurückgewiesen.
ReceiptDocumentSyncState.scan ist ausschließlich lesend und meldet mögliche
Zuordnungskonflikte; es führt keine Datenbereinigung durch.

Neue gezielte Tests: Zustandserhalt, Wiederholung, Nichtreparatur alter Nullwerte,
Identitätskonflikte und tatsächlicher HTTP-Sync mit unverändertem vollständigem
Phase10B-Portfolio einschließlich zweier Upload-/MERGE-Läufe. Bestehende
Phase10D-/ReviewedDocument-Restorekontrollen bleiben unverändert.
Die Testfixture ist bytegleich zur bestehenden Phase11A-ZIP (SHA256
b052a1ae4569196abb791375aa1fa01fe6a636a1d0082820acb576fa497ea638),
ausschließlich unter src/test/resources; keine Testdaten im Produkt-APK.

## Noch auszuführen

- Gezielte E11A-03-Ergebnisse vollständig auswerten; danach lesenden Room-Audit testen.
- E11A-01: gemeinsame Namespace-Prüfung vor sämtlichen produktiven Drive-HTTP-Zugriffen,
  gültige bestehende Produktbackups erhalten, keine Migration/automatische Bereinigung.
- E11A-02: zulässige dateilose Fahrtbelege anhand echter Fahrtenbeziehungen unterscheiden;
  Metadaten-, Index-, Vorschau-, Restore-/Full-Restore-Vertrag gemeinsam erweitern.
  Keine Ersatzdatei und keine pauschale Lockerung gewöhnlicher Originalpflicht.
- Vollständige lokale Regressionen, Lint, APK-Builds und Android-CI; erst danach PR.
- Test-App erst auf eigenem vorläufigem Integrationsbranch aktualisieren,
  gemeinsame Fachlogik übernehmen, Sandbox und Test-Drive getrennt halten.
- Portfoliovergleich beider Varianten einschließlich Full-Restore und zweimal MERGE.
- Echte Geräte-/Google-Tests bleiben offen; kein Merge ohne gesonderte Freigabe.

Es wurden keine echten Nutzerdaten verändert und keine Backups auf reale Bestände
zurückgespielt. Kein Push, PR oder Merge für diesen laufenden Reparaturstand.

## Gezielter Zwischenstand E11A-03

17 tatsächliche Methoden bestanden, keine Fehler/Skips: sechs Zustandsfälle,
zwei echte Client-/Repository-HTTP-Fälle gegen simulierten Transport,
acht bestehende Phase10D- und ein ReviewedDocument-Restorefall.
Die HTTP-Fälle prüfen zwei Sync- und zwei MERGE-Läufe mit dem vollständigen
bestehenden Portfolio sowie den Erhalt bestätigter Review-/Referenzfelder.
E11A-02 wird in diesem Zwischenstand ausdrücklich noch als bestehender Fehler
charakterisiert; kein vollständiger Sync-Erfolg aller 70 Belege behauptet.

Zusätzlicher strikter Snapshot-Checker noch nicht ausgewertet: der lokale
Export kennzeichnet den uncommitteten Build als WORKTREE_UNCOMMITTED;
der Checker verlangt einen tatsächlichen 40-stelligen App-Commit und bricht
an dieser Metadatenprüfung ab. Keine Metadaten oder Checkerregeln zum
Erzwingen eines Erfolgs umgeschrieben. Nach einem versionierten Build erneut
exportieren und vollständig prüfen.

Vollständige Regressionen, Lint/APKs, CI, E11A-01/E11A-02 sowie Phase C/D
stehen noch aus. Kein vollständiger Abschluss und keine Freigabe.

## Versionierter E11A-03-Nachweis

Die oben beschriebene Metadaten-Prüfgrenze ist im erneuten echten Testlauf
behoben: derselbe unveränderte Testcode wurde mit tatsächlichem App-Commit
cb2a2413279f422c1bb6f0fe86b59f68607d22c6 erneut ausgeführt (kein Umschreiben
der exportierten Metadaten, kein gelockerter Checker).
17 tatsächliche Methoden bestanden, 0 Fehler/Skips. Der unveränderte strikte
Checker bestätigt den gespeicherten Zustand nach zweimal Sync/zweimal MERGE:
`ok=true`, 0 Strukturfehler, alle 60 Originalhashes korrekt.
Counts: 5 Immobilien, 17 Einheiten, 17 Mietverhältnisse, 120 Bankbuchungen,
70 Belege/Dokumente, 10 Fahrten, 60 Originale. Der strukturelle Selbstvergleich
ist kein vollständiger Vorher-/Nachher-Vertragsvergleich und keine Full-Restore-
oder praktische Google-Drive-Freigabe. Die normativen HTTP-Tests prüfen den
Erhalt aller fachlichen Dokumentfelder gesondert vor/nach den Operationen.

Lokale Evidence im Reparatur-Worktree:
`app/build/reports/e11a03-repair/preserved-after-two-merges.json`,
`app/build/reports/e11a03-repair/strict-structure.json`,
`app/build/test-results/testDebugUnitTest/`.
Testlog: `/tmp/immopilot-e11a03-versioned.log`.
E11A-01/E11A-02 und die vollständigen Phasen B–D bleiben ausstehend.
