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

## E11A-01 – gemeinsamer Zugriffsschutz implementiert, gezielt geprüft

DriveNamespacePolicy liegt jetzt im gemeinsamen Produktcode und gilt für beide
App-Identitäten, einschließlich Release. Produktroot und Produktkennung bleiben
unverändert. Dateioperationen, JSON-Verweise und sämtliche Configrollen werden
gegen aktuelle Elternketten geprüft; Caches/Mappings sind Account-/Root-/Parent-
gebunden und werden vor Nutzung geprüft. Keine Migration oder Datenbereinigung.
Metadaten mit ausdrücklich fremder appName-Kennung werden ebenfalls abgewiesen.
Normale Legacydateien ohne Zusatzkennung innerhalb des gültigen Produktroots
bleiben zulässig. Historisch als Produktdaten markierte Testdateien im Produktroot
sind ohne Herkunftsnachweis nicht sicher unterscheidbar: kein automatischer Eingriff.

Der Zugriffsschutz der Referenz-Test-App wurde in eine gemeinsame Implementierung
überführt; die spätere vorläufige Integration muss die bisherigen variantenlokalen
Policydateien entfernen, damit keine doppelte Fach-/Sicherheitslogik entsteht.
Gezielte Namespace- und E11A-03-Tests bestanden; vollständige Regression ausstehend.

## E11A-02 – expliziter Vertrag und vollständiger frischer Restore geprüft

Ein dateiloser Fahrtbeleg wird nur anhand seiner tatsächlichen bestätigten,
nicht stornierten Fahrtenbuch-Buchung freigestellt. IDs, Immobilie, Datum,
Kilometerquelle und Betrag müssen zusammenpassen; bestehende Originalreferenzen
oder explizite Dateipfade verhindern die Ausnahme. Keine Ersatzdateien.
Der vorhandene Metadatenserializer wird für beide Belegformen verwendet; Index
und Metadaten speichern denselben typisierten Fahrtnachweis. Alte JSONs ohne
Nachweis behalten ihre bisherige Originalpflicht. Keine Room-Schemaänderung.
Vor Full-Restore werden Index, Metadaten und Supplemental-Fahrtenbuch gemeinsam
geprüft. Inkonsistenz blockiert vor dem lokalen Ersetzen.

38 tatsächliche gezielte Methoden bestanden (0 Fehler/Skips): 11 Namespace-,
5 reale Client-/Repository-HTTP-, 5 Fahrtvertrags-, 6 Dokumentzustands-,
2 lesende Room-Audit- sowie 9 bestehende Metadaten-/Restorekontrollen.
Der frische JVM-Testprofilfall prüft 70 Belege, 70 Dokumente, 5 Immobilien,
17 Einheiten, 120 Bankbuchungen, 10 Fahrten und 60 Originale. Dateien und
Preferences dieses Profils sind leer und vom Quellprofil getrennt. Nach
Full-Restore werden alle 60 Originale wirklich erneut vom simulierten Drive
heruntergeladen und gehasht; danach zweimal MERGE und erneuter Sync ohne
neue Cloud-IDs. Die bestehende Tombstone-Ordnerinitialisierung wird vor der
Idempotenz-Baseline ausdrücklich geprüft (nur ein eigener deletions-Ordner).
Normale Rechnungen mit leerer/fehlender/leerer Quelldatei bleiben Fehler.
Die JSON-Contract-Tests laufen unter Robolectric mit echten Android-JSON-Typen.
Keine bestehende Assertion entfernt oder abgeschwächt.

Vollständige Regression, Lint/APKs/CI und vorläufige Integration weiterhin offen.
Keine echte Google-/Gerätefreigabe; alle Cloudnachweise dieses Abschnitts nutzen
simulierten HTTP-Transport und ausschließlich synthetische Daten.

## Zusätzliche ID-Schutzfälle und verbindlicher Portfolio-Wächter

Datei-/Ordnerrollen werden auch in JSON-Verweisen unterschieden. Geschützte
Root-/System-/Backupcontainer können nicht über manipulierte Datei-IDs gelöscht
oder überschrieben werden. Nichtleere Ordner werden nicht gelöscht; die bestehende
Bereinigung tatsächlich leerer Kategorien bleibt möglich. Ein fremder/ungültiger
Cloudverweis verhindert endgültiges lokales Löschen des Belegs vor jeder Mutation.
Reine historische Tombstone-/Legacyverweise dürfen bereits fehlen; dies gewährt
keinen Dateizugriff. Aktuelle Referenzen und existierende fremde historische
Referenzen bleiben gesperrt. Scope-Prüfung und Rollenprüfung gelten gemeinsam.

Die bestehende Kontierung eines bestätigten Fahrtbelegs bleibt erhalten; auch
abweichend konfigurierte gültige Konten benötigen keine erfundene Anlage.
Der Vertrag schreibt kein DATEV-Konto um. Die explizite Fahrtbindung bleibt Pflicht.

Ein zusätzlicher CI-Wächter verlangt sämtliche neuen echten Testmethoden sowie
den unveränderten strikten Vergleich zwischen gesichertem Quellbestand und
frischem Full-Restore-Ziel nach erneutem Download/twice-MERGE. Der Seedzustand
vor erster Cloud-ID-Vergabe wird gesondert gespeichert, nicht mit einer bereits
hochgeladenen Cloud-ID-Baseline verwechselt. Der Checker wurde unverändert aus
der Referenz übernommen, keine Felder oder Fehler ausgeblendet. Der vorhandene
Evidence-Transport enthält die neuen JSONs und zählt echte XML-testcase-Knoten,
auch innerhalb verschachtelter Android-testsuites.

Vollständige Regression und Buildprüfung laufen als nächster Schritt; noch kein
Push und keine Freigabe. Phase C bleibt bis dahin unberührt.

## Strikter Wiederherstellungsvertrag – ergänzende Korrektur

Der unveränderte Phase11A-Checker meldete nach frischem Full-Restore und zwei
MERGEs zwei Contract-Abweichungen desselben geprüften Dokument-JSONs (Room und
Supplemental). Der bisherige Original-Metadatenparser rekonstruierte ausschließlich
`_receiptOriginalOrder`; der folgende Merge fügte diesen technischen Schlüssel
in die bestätigte Beschreibung ein. Die Original-Metadaten übertragen nun
`extractedFieldsJson` exakt; alte Backups ohne dieses optionale Feld behalten
die bisherige Rangrekonstruktion. Drei zusätzliche Fälle prüfen unverändertes
geprüftes Einzeloriginal, geprüfte Mehrseitenreihenfolge und Legacy-Ranglesbarkeit.
Die neue vollständige HTTP-Prüfung vergleicht jetzt auch den Rangschlüssel strikt;
der unabhängige Checker bleibt unverändert.

Der erste vollständige Regressionslauf auf 0fe306d wird vollständig ausgewertet.
Er zeigte sechs Fehler in alten manuell aufgebauten Testindizes: Die wirkliche
Metadatenschreibfunktion enthält jetzt einen gebundenen Fahrtnachweis, während
die Testindizes diesen wegließen. Die Phase10B/10C-Aufbauten übernehmen nun
den tatsächlich gespeicherten Nachweis und dessen Indexfelder; der Phase10C-
Transport enthält auch die tatsächlich erzeugte Supplemental-Sicherung.
Keine fachliche Assertion oder Fehlerprüfung wurde entfernt. Ein erneuter
vollständiger Lauf des korrigierten Stands ist erforderlich und noch offen.

## Gezielte Folgeprüfung auf d9ba35a

27 echte JVM-Methoden (Original-Metadaten, Phase10B Normal/Stress,
Phase10C Relationen/Rollback, bestehende ManagedDocument-E2E sowie reale
Client-/Repository-Portfoliofälle): 0 Fehler, 0 Skips.
Der unveränderte Phase11A-Checker besteht den Vorher-/Nachher-Vergleich:
60/60 Originalhashes, Contract-Abweichungen 0, fehlende/unerwartete IDs 0,
Dubletten 0, Relationsfehler/Orphans 0. Bestand: 5 Objekte, 17 Einheiten,
70 Belege/Dokumente, 120 Bankbuchungen, 10 Fahrten, 17 Mietverhältnisse,
5 AfA-Einträge und 1 Darlehen. Das ist simulierter HTTP-Transport, keine
praktische Drive-Abnahme. Der neue vollständige JVM-Lauf läuft noch.

Lokale Infrastruktur: Gleichzeitige Kotlin-Kompilierung zweier Checkouts
und der Testlauf überschritten die tatsächliche 8-GiB-cgroup-Grenze.
Der APK-Builddaemon wurde vom System beendet (oom=1, oom_kill=1);
dieser Build ist fehlgeschlagen und kein Produktfehlernachweis. Die
27 Testmethoden liefen erfolgreich zu Ende. Der APK-Build wird mit einem
Worker, ohne parallele Gradle-Projekte und 3-GiB-Heap erneut ausgeführt.
Keine Tests deaktiviert, kein GitHub-CI-Lauf abgebrochen.

## Vollständige lokale Produktregression auf d9ba35a

1.139 einzigartige echte Methoden, 0 Failures, 0 Errors, 0 Skips und
0 Suite-Platzhalter. Testausführungswächter, Phase10B-Portfolio-/DATEV-Audit
und neuer Reparaturwächter grün. Alle 36 Reparaturmethoden tatsächlich gelaufen;
60 Originalhashes, vollständiger Restore und zwei MERGEs ohne Vertragsfehler.
Auch Normal-/Stressportfolio mit 17/21 Einheiten grün. Roboelectric meldete
beim Aufräumen eines temporären Native-Verzeichnisses eine Warnung; alle
JUnit-XMLs und Wächter sind fehlerfrei. Keine Warnung als Test übersprungen.

Lint: 0 Fehler, 116 Warnungen. assembleDebug, assembleDebugAndroidTest und
assembleRelease im separaten Checkout desselben SHAs grün. Dieser lokale
Release-Build ist mangels Produktionsschlüssels unsigniert. Der begrenzte
Build lief ohne weiteren OOM-Kill zu Ende. Diese lokalen Läufe teilten sich
CPU/RAM; aus ihrer Dauer folgt keine CI-Timeoutänderung.

Das tatsächliche Produkt-Debug-APK behält ID
com.aistudio.steuerassistent.vnhqkp, Label Steuer-Assistent Anlage V,
Version 1.0/1 und vier eigene Provider-Authorities. Ein zusätzlicher
CI-Helfer prüft diese tatsächlichen APK-Eigenschaften und protokolliert
SHA256, Permissions und Komponenten. Keine Produkt-UI-Umbenennung.

Die vollständige HTTP-Portfolio-Prüfung wird zusätzlich um eine tatsächliche
bestätigte Dokumentprüfung mit nichtleeren Mieter-/Darlehens-/Sanierungs- und
Review-/OCR-Referenzen vor ihrer Baseline verstärkt. Das Darlehen stammt aus
dem bestehenden Portfolio und gehört zum selben Objekt. Diese zusätzliche
Teständerung wird separat geprüft; Produktquellen seit d9ba35a unverändert.
