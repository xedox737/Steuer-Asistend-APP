# Phase 11E – Drive-Synchronisierung

Status: Implementierung im Entwurfs-PR #156; CI und praktische Abnahme getrennt auswerten. Praktische Abnahme **OFFEN**. Kein Merge und keine APK-Veröffentlichung ohne gesonderte Freigabe.

## Basis und verbindliche Grenzen

Tatsächlicher Base-SHA: `5d5e61437fcf7324bb5e0f6442669f777d8ddeda`, Tree `268ad2b901106aa97561d42002cba098fef28776` (einschließlich PR #155). Neuer Branch: `fix/phase11e-drive-sync`.

Branch und sauberer Status wurden geprüft. Ein erneuter `git fetch origin main` scheiterte am nicht erreichbaren Sandbox-Proxy. Main wurde frisch über GitHub gelesen, die Original-Commitobjekte einschließlich des signierten Merge-Commits gegen ihre Git-SHAs rekonstruiert und `origin/main` auf diesen nachgewiesenen Stand gesetzt. Der neue Worktree stammt exakt von dieser Base; ein erfolgreicher Netzwerk-Git-Fetch wird nicht behauptet.

Die bereitgestellte aktualisierte Arbeitsanweisung, AGENTS.md, Drive-/Backup-/Restore-Verträge und Änderungen der PRs #149–#154 werden als Arbeitsgrundlage berücksichtigt. Die spätere PR-Beschreibung ersetzt historische Zwischenstände nicht durch angebliche neue Geräteergebnisse.

Die originale Package-ID, OAuth-Anmeldung, Datenbank und isolierte Test-App bleiben erhalten. Automatisches Backup wird nicht aktiviert. Keine produktiven Nutzerdaten als Testdaten, keine Live-Drive-Mutationen, keine Bereinigungen oder Ersatzdateien. Alle Reproduktionen nutzen neu erzeugte synthetische Daten und simulierten HTTP-Transport.

## Bestätigter Gerätebefund laut Auftrag

POCO X8 Pro, Android 16/HyperOS: Anmeldung und Neustart erhalten; Inventar 22/22/22, ein Objekt/sieben Einheiten; Restore 22 Belege/55 Positionen, ein Split, ein DATEV-Profil und 15 Lernregeln; Belege nach Neustart vorhanden, mehrere Originale geöffnet. Manuelles Synchronisieren benötigt über 300 Sekunden und endet bei 0/22 sowie 0/0. Automatisches Backup wurde wieder ausgeschaltet.

Die erfolgreiche Bestandsprüfung nach Ausschalten der HyperOS-Akkueinschränkung ist eine bestätigte Korrelation. Eine ausschließliche Ursache sämtlicher Fehler ist nicht nachgewiesen. Die zwei erhaltenen 404-Zusatzverweise bleiben unverändert; die Identität oder frühere Löschung einer konkreten Datei wird nicht aus HTTP 404 allein abgeleitet.

## Phase A – verifizierter bisheriger Ablauf

`GoogleDriveBackupScreen` → `ReceiptViewModel.syncAllToDrive()` (Single-Flight-Job, Dispatchers.IO):

1. Laufende Initialisierung abwarten; Sitzung prüfen; Token und Drive-Konfiguration erneut initialisieren. Native 401 wird bei Initialisierung einmal durch Token-Invalidierung/Erneuerung behandelt.
2. Exakten alten synthetischen Diagnosedatensatz reversibel zurückstellen; aktuellen Room-Belegbestand lesen.
3. Bei leerem Bestand oder bekanntermaßen fehlgeschlagener Vorschau erneut Inventar prüfen. Bei unbekanntem Bestand abbrechen; kein implizites REPLACE.
4. Hauptbuch-CSV hochladen/aktualisieren.
5. `ensurePropertyDocumentStructure`: Immobilienwurzel und alle leeren Standard-Unterordner prüfen/anlegen.
6. Wohneinheiten-CSV hochladen/aktualisieren.
7. `saveStammdatenToDrive`: Objekt, Einheiten, DATEV, Lernregeln und Kategorien als JSON schreiben; Kategorie-Mappings prüfen.
8. Alle Belege **seriell** über `ReceiptSyncGate` synchronisieren. Je Beleg: Referenz-/Identitätsprüfung, Zielpfad, bestehende Original-ID auflösen, Metadaten mehrfach lesen, lokale Datei prüfen, bei vorhandenen lokalen Bytes bisher vollständiges Drive-Original herunterladen/hashvergleichen, Original ggf. aktualisieren/anlegen, Dokumentreferenzen, weitere Originalseiten, Belegmetadaten schreiben, gemeinsamen Belegindex separat lesen/upserten/schreiben.
9. Eigenständige Dokumente seriell synchronisieren; bestehende Originale dabei bisher vor und nach einer Layoutprüfung vollständig herunterladen. Dokumentindex kann bereits je Dokument geschrieben werden.
10. Dokumentindex zusätzlich einmal für alle ManagedDocuments schreiben.
11. Zusatzdaten für alle Fachbereiche schreiben.
12. Boolesche Teilergebnisse zusammenfassen. Der alte Text nennt lediglich erfolgreiche Beleg-/Dokumenttransaktionen; er unterscheidet keine unveränderten Daten, Teilerfolge, vorgelagerten Blockaden oder nicht versuchten Schritte.

Ordner-Caches sind bereits Token-/Root-/Parent-gebunden und werden bei Nutzung geprüft. Metadaten- und Belegindex-Mutexe existieren. PR #155 teilt lesende Ownership-Nachweise ausschließlich innerhalb von Inventar-/Snapshot-Läufen; der manuelle Sync nutzt diesen Vorteil bislang nicht. Jede normale Operation baut ihre Eltern-/Root-Nachweise erneut auf. Der OkHttp-Client ist bereits gemeinsam; eigene Drive-Zeitlimits fehlen. JSON-GETs haben seit #155 genau einen SocketTimeout-Retry mit 250 ms Pause. `DriveSyncFailurePolicy` enthält eine ältere Backoff-Funktion, wird aber im produktiven Sync nirgends aufgerufen. Keine bestehende strukturierte Phasen-/HTTP-Messung.

## Vor Produktreparaturen reproduzierte Ursachen

Neue `Phase11ESyncRegressionTest` gegen unveränderte Base-Produktklassen: vier tatsächlich ausgeführte Methoden, vier erwartete Fehler. Ergänzender lokaler SDK-35-Lauf; eingecheckte Tests bleiben SDK 36.

1. **Cloud-Original vorhanden, lokaler Cache fehlt:** 22 synthetische Belege haben gültige Original- und Metadaten-IDs. Trotzdem schlägt jeder an `syncReceiptToDrive` mit „Keine lokale Quelldatei gefunden oder Datei ist leer“ fehl. Die Existenz eines bereits gesicherten Originals wird nicht als eigenständiger, nachzuweisender Zustand unterstützt. Ein Restore muss nicht sämtliche lokalen Bytes herunterladen. Das reproduziert 0/22 ohne fehlende Drive-Belege.
2. **Gemeinsamer Index:** `updateReceiptIndexInDrive` liest den gesamten Index mit normalem `downloadJson`. Ein eigener 404 einer als DELETED markierten historischen Referenz blockiert diesen normalen Pfad vor dem Upsert. Ein aktiver Beleg mit erreichbarem Original und lokalen Bytes scheitert deshalb erst am gemeinsamen Index. Das wird als Belegfehler gezählt, obwohl andere Teilschritte erfolgreich waren. Die gelöschte Zeile wird im Test nicht entfernt.
3. **Zusatzdaten:** `SupplementalDriveBackup.backup` → `updateJson` → `validateDataReferences` → `DriveNamespacePolicy.validateJsonReferences` prüft auch alle erhaltenen Original-IDs in `managedDocuments`. Zwei nicht erreichbare historische Originalreferenzen blockieren selbst eine bytegleich bereits vorhandene Zusatzsicherung. Separat verwendet der Dokumentindex denselben strikten Schreibpfad für sämtliche ManagedDocuments, einschließlich Dokumenten gelöschter/technischer Belege. Ein Zusammenhang der Zusatzwarnungen mit einem zusätzlichen Sync-Fehler ist damit im Code und im synthetischen Zusatzbackup nachgewiesen; die genauen privaten IDs werden nicht untersucht oder verändert.
4. **Falscher Zeitstempel:** `initializeDrivePersistence` schreibt bei geladener Konfiguration einen neuen `last_stammdaten_backup_time`, obwohl dabei keine vollständige Sicherung durchgeführt wird. Ein Test mit vorherigem Sicherungszeitpunkt belegt die Ersetzung allein durch Verbinden/Initialisierung. Der abschließende Full-Sync schreibt zwar nur bei Gesamterfolg, kann aber den früheren Initialisierungsstempel nicht korrigieren.

Weitere belegte Risiken: CSV-Suche kann Netzwerk-/API-Fehler in „nicht gefunden“ umwandeln; mehrere Repository-Helfer reduzieren Fehler auf false/null; Supplemental fängt bislang auch Kontrollfehler. Bei Originalverifikation kann ein Fehler die vorhandene ID im flüchtigen Ablauf verwerfen und die Anlagesuche erneut öffnen. Bestehende Originale dürfen bei unbekanntem vorherigem Ergebnis nicht unkontrolliert neu angelegt oder überschrieben werden. Diese Pfade benötigen eigene Negativtests, bevor eine Korrektur als abgeschlossen gilt.

## Vorher-Messung (synthetischer HTTP-Transport, keine Netzlatenz)

| Szenario | Ergebnis | API gesamt | files.list | files.get | files.create | files.update | Dauer |
|---|---|---:|---:|---:|---:|---:|---:|
| 22 Cloud-Originale ohne lokalen Cache, erster manueller Lauf | 0/22, 22 lokale Quelldateifehler | 2198 | 469 | 1682 | 23 | 24 | 444 ms |
| Identischer zweiter Lauf, Ordner bereits vorhanden | erneut 0/22 | 1044 | 200 | 835 | 0 | 9 | 328 ms |
| Ein aktiver lokaler Beleg plus gelöschte Indexhistorie | Indexfehler, 0/1 | 1496 | 350 | 1098 | 23 | 25 | 159 ms |
| Bytegleich vorhandenes Zusatzbackup mit zwei erhaltenen 404-Verweisen | fehlgeschlagen | 5 | separat nach Phasenmessung | separat nach Phasenmessung | 0 | 0 | nicht gesondert gemessen |

Die Aufrufzahlen enthalten die durch die produktive Namespace-Policy ausgelösten Prüf-HTTP-Aufrufe. Die Laufzeiten messen monotone JVM-Zeit gegen Antworten im Arbeitsspeicher. Sie sind kein Google-Drive-/Emulator-/POCO-Benchmark. Der reale Ausgangswert bleibt **über 300 Sekunden, fehlgeschlagen**, ausschließlich laut Nutzerbericht. Ein neuer echter Gerätewert ist für die Abnahme erforderlich.

## Weitere vor der jeweiligen Korrektur nachgewiesene Fehler

- Ein Index-Upsert serialisierte unbeteiligte Einträge erneut und ergänzte optionale Felder mit `null`. Dadurch waren beim nächsten Lauf alle 22 unveränderten Einträge scheinbar veraltet. Der Index-Upsert erhält jetzt die anderen JSON-Einträge einschließlich unbekannter Felder unverändert.
- Ein während eines Original-Uploads lokal bearbeiteter Beleg wurde anschließend durch die alte Belegkopie überschrieben. Ein neuer Regressionstest schlug vor der Korrektur mit dem alten statt dem neueren Aussteller fehl. Die Bestätigung schreibt jetzt atomar ausschließlich Drive-Felder per SQL UPDATE. Fachliche Felder, Positionen und finanzielle Spiegel werden nicht ersetzt. Dokument-Speicherfelder werden transaktional in die aktuelle Zeile übernommen.
- Bei neu angelegten Metadaten fehlten Zahlungsart, Herkunft und Konfidenz. Der neue Test belegte vor der Korrektur „Unbekannt“ statt der bestätigten Zahlungsart. Alle drei Felder werden jetzt übernommen.
- Nach erfolgreicher Metadaten-Anlage mit verlorener HTTP-Antwort konnte die nächste stabile-ID-Suche die Datei zwar finden, behandelte deren bisher lokal fehlende technische ID aber als fachlichen Konflikt. Der Wiederaufnahmetest schlug vor der Korrektur fehl. Die fehlende ID darf nun ausschließlich nach Eigentums-, Inhalts- und Identitätsnachweis übernommen werden; eine bereits gebundene andere ID bleibt geschützt.

Diese Code-Reproduktionen erklären plausible reale 0/22- und Folgefehler. Welcher Teilschritt auf dem POCO zuletzt tatsächlich fehlschlug, muss der neue Gerätebericht bestätigen. Es wird keine nachträgliche private Netzwerkspur behauptet.

## Diagnose und bestehender Ablauf nach der Reparatur

Der manuelle Button verwendet weiterhin denselben ViewModel-/Repository-/Client-Pfad. Vor den bisherigen Schreibschritten erfolgt ein rein lesender Abgleich von Belegindex, aktiven Metadaten und Original-Fingerprints. Unvollständiger Bestand, fremde IDs, widersprüchliche Revisionen oder cloudseitige aktive Belege ohne lokale Entsprechung blockieren vor den CSV-/Stammdaten-Schreibvorgängen. Es erfolgt kein Restore.

Danach bleiben Hauptbuch-CSV → Immobilien-/Dokumentstruktur → Wohneinheiten-CSV → einzelne Stammdaten → Belege → eigenständige Dokumente → Dokumentindex → Zusatzdaten → abschließende Inhalts-/Namespace-Prüfung bestehen. Alle Schreibvorgänge bleiben seriell. Die bisher vorhandenen Gates/Mutexe werden verwendet und um ein gemeinsames Gate für Vollabgleich, automatischen und einzelnen Belegabgleich ergänzt. Wartende automatische Aufträge prüfen die Einstellung und Sitzung erneut. Die Einstellung wird nicht eingeschaltet.

`DriveSyncTrace` misst mit `System.nanoTime()` Start, Ende und Dauer jeder Phase; tatsächliche HTTP-Versuche einschließlich Eigentumsprüfungen; `files.list/get/create/update` und die separate ID-Reservierung `files.generateIds`; tatsächlich gelesene/geschriebene Body-Bytes, Fehlerklasse/HTTP-Status und Retry-Anzahl. Verschachtelte Phasen enthalten die Aufrufe ihrer Kinder: **Phasenzähler nicht nochmals zur Gesamtsumme addieren.** Bytewerte sind Nutzdaten ohne TLS-/HTTP-Overhead. Bei verlorenem Antwortkörper können empfangene Bytes entsprechend fehlen.

Der Drive-Bereich zeigt Phase, tatsächlich verarbeitete Belege/Dokumente, unveränderte/übertragene/nur metadatengeänderte/fehlgeschlagene/gesperrte/nicht versuchte/unterbrochene Datensätze und Laufzeit. Kein künstlicher Prozentbalken. Sicheres Abbrechen stoppt Folgeoperationen; ein bereits angenommener HTTP-Schreibvorgang kann noch abgeschlossen werden und wird beim nächsten manuellen Lauf abgeglichen.

Der kopierbare deutsche Bericht bleibt nach Fehlern und Neustart zugänglich, auch außerhalb der verbundenen Karte. Er enthält keine Kontoadressen, Tokens, URLs, Dateinamen oder Dokumentinhalte. Sichere interne IDs werden auf ein begrenztes Zeichenformat beschränkt. Der Bericht wird an Phasengrenzen und periodischen Datensatz-Meilensteinen sowie abschließend gespeichert. Nach Prozessende wird ein alter laufender Bericht ausdrücklich als **letzter Speicherpunkt** markiert: spätere Vorgänge sind unbekannt. Ein vollständiges Audit nach abruptem Prozessabbruch wird nicht behauptet.

Ein Fehler vor der Belegschleife führt z. B. zu 22 „nicht versucht“, statt 22 behaupteten Uploadfehlern. Nach einem Belegfehler werden weitere Belege nicht minutenlang mit derselben Netzstörung versucht. Bereits erfolgreiche Dateien bleiben bestehen. Der Zeitpunkt der letzten vollständigen Sicherung wird ausschließlich nach erfolgreicher Gesamtprüfung gesetzt; Initialisierung und Teilerfolge ändern ihn nicht.

## Inkrementeller Abgleich und Datensicherheit

- Bereits gebundene Original- und Metadaten-IDs werden unabhängig vom lokalen Cache verifiziert. Unveränderte Originale benötigen weder erneuten Upload noch bei verfügbarer serverseitiger SHA-256 vollständigen Download. Größe, stabile Identität, Revision und Inhaltsnachweis bleiben erforderlich.
- Fachliche Metadatenänderungen schreiben gezielt die Metadaten und den betroffenen Indexeintrag. Unbekannte vorhandene Felder und Rohdaten bleiben erhalten. Geänderte Originalbytes aktualisieren dieselbe Datei-ID nach Vorzustandsprüfung; leere IDs ersetzen keine gültigen Verweise.
- Ohne serverseitige SHA-256 werden echte Bytes und stabile Drive-Versionen geprüft. Ohne HTTP-ETag ist ein nachweislich unveränderter No-Op erlaubt; eine nicht sicher bedingbare Änderung wird mit HTTP-428-Diagnose gesperrt. JSON-/Original-Updates verwenden einen frisch nachgewiesenen Vorzustand und `If-Match`; HTTP 412 wird als Konflikt angezeigt. Die tatsächlichen Header-/Precondition-Eigenschaften des realen Drive-Backends bleiben Teil des offenen Gerätetests.
- Neue Dateien verwenden innerhalb des vorhandenen Anlagepfads vorab reservierte Drive-IDs. Eine lokale AtomicFile-Aufzeichnung wird vor POST geschrieben. Nach unklarem Erfolg wird dieselbe ID frisch geprüft, statt eine neue Datei anzulegen. POST/PATCH werden nicht blind wiederholt. Google dokumentiert vorab generierte IDs und die sichere Wiederaufnahme bei unklarem Anlageergebnis: [Dateien erstellen](https://developers.google.com/workspace/drive/api/guides/create-file), [Uploads](https://developers.google.com/workspace/drive/api/guides/manage-uploads).
- Wiederaufnahme-Fakten (IDs/Hashes, ausstehende Indexbestätigung, bekannte JSON-Ausgangsversion) liegen ausschließlich in `noBackupFilesDir/drive-pending-writes`, atomar geschrieben, ohne Inhalte oder Zugangsdaten. Sie werden nicht als fachliche Daten gesichert. Bereits verifizierte Original-/Metadaten-Schreibvorgänge werden bei einer späteren Indexstörung nicht erneut als neue Anlagen ausgeführt.
- Gemeinsame Sicherungen dürfen keine bereits vorhandenen stabilen Datensätze oder gültigen Dateiverweise verlieren. Neuere Cloud-Versionen und unbekannte Änderungen ohne gemeinsame Ausgangsversion blockieren. Kein automatisches Zusammenführen, Wiederherstellen oder REPLACE.
- Die beiden historischen eigenen 404-Verweise werden ausschließlich am bereits vorhandenen Entity-/Feldpfad erhalten. Sie können weder einen neuen Beleg autorisieren noch umgebogen, automatisch ersetzt oder reaktiviert werden. Fremde IDs, fehlende Elternordner, HTTP 401/403 und aktive notwendige Originale bleiben blockierend. Die gelöschte Indexhistorie wird nicht entfernt.
- Bestehende Dokumentreferenzen werden vor Wiederverwendung anhand tatsächlichen Inhalts und Namespace nachgewiesen. Eine nicht erreichbare historische Hashreferenz wird keinem neuen Dokument zugeordnet und löst keine automatische Ersatzanlage aus.

Die Original-Package-ID `com.aistudio.steuerassistent.vnhqkp`, produktiver Namespace `Steuerassistent Belege/_BelegApp-Daten`, OAuth-Anmeldeweg, Room-Version 34 und Entity-Schema bleiben unverändert. Neu generierter Room-Code hat denselben Identitätshash `42894a175052e9a85345873c5069e7a7`. Keine Migration, kein destruktiver Fallback, keine automatische Cloud-Bereinigung. Die isolierte Test-App und ihr Bereich werden nicht geändert.

## Netzwerk und Performance-Änderungen

Der gemeinsame OkHttp-Client bleibt erhalten. Einzelne Calls sind auf 20 Sekunden begrenzt, Connect/Read/Write auf zehn Sekunden. Für sichere GETs höchstens ein zusätzlicher Versuch, exponentielle Basis 250 ms plus Jitter bis 125 ms; HTTP 408/429/5xx und vorübergehende Netzfehler werden klassifiziert. `Retry-After` wird als Sekunden oder HTTP-Datum beachtet; übersteigt die Wartezeit zwei Sekunden, wird nachvollziehbar beendet statt zu früh oder minutenlang zu wiederholen. HTTP 403/404 und dauerhaft ungültige Anfragen werden nicht wiederholt. HTTP- und Body-Read-Retry teilen sich dasselbe Budget. Unsichere Schreibwiederholungen und versteckte OkHttp-Verbindungswiederholungen sind deaktiviert.

Vorübergehende DNS-/Timeout-/Verbindungsfehler löschen die Google-Anmeldung nicht. Die vorhandene einmalige native 401-Token-Erneuerung bleibt bestehen und wird gesondert gemessen. Nach einem Fehler mitten im Schreiben erfolgt keine automatische Wiederholung des ganzen Schreibpakets. Cancellation wird weitergegeben; bereits gestartete synchrone HTTP-Aufrufe können bis zur begrenzten Beendigung laufen.

Nur unabhängige **lesende** Metadaten-/Abschlussprüfungen laufen begrenzt parallel (maximal drei). Kein paralleles Index-Upsert, keine konkurrierende Anlage desselben Ordners oder Aktualisierung derselben Datei. Lese-Ergebnisse und positive stabile-ID-Suchen werden ausschließlich innerhalb eines Laufs wiederverwendet. Nach Mutationen werden Eigentumsnachweise invalidiert; vor dem Schreiben erfolgt eine frische zusammengefasste Referenz-/Elternprüfung. Am Ende werden beobachtete Dateien und Inhaltsversionen nochmals frisch attestiert. Die Optimierung ersetzt Einzelabfragen durch gefilterte, paginierte und feldbegrenzte Abfragen; sie entfernt keine erforderliche Namespace-Sicherheitsprüfung.

## Automatisierte Reproduktion und Pflichtmatrix

Produktiver Transport wird im Test durch `ComparisonDriveHttpFixture` ersetzt; Namespace-Policy, Repository, Room und ViewModel bleiben echt. Ausschließlich neue synthetische Datensätze, kleine synthetische PNG-Signaturdateien, kein Zugriff auf Nutzer-Drive. ETags, Versionen, SHA-256, verlorene Antworten und HTTP-/Netzfehler werden kontrolliert injiziert. Die echte serverseitige Google-Semantik wird dadurch nicht bewiesen.

```sh
gradle testDebugUnitTest --tests com.example.ui.Phase11ESyncRegressionTest --no-daemon
gradle testDebugUnitTest --no-daemon
gradle lintDebug assembleDebug --no-daemon
```

Der Benchmark ist `reproducibleTwentyMillisecondSyntheticLatencyBenchmarks`. Er erzeugt JSON mit allen Phasen unter `app/build/reports/phase11e/`. Keine externen Benchmarkdaten, Zugangsdaten oder speziellen Gerätebedingungen nötig. Eine 20-ms-Verzögerung je simuliertem HTTP-Aufruf liegt außerhalb des Fixture-Locks; unabhängige GETs können wie im Produktcode parallel laufen. Ein vorbereitetes Warm-up richtet den Bestand ein. Danach: 22 unverändert, ein neuer Beleg, fünf weitere neue Belege und unveränderter Folgelauf mit 28 Belegen. Die Zahlen gelten für diesen kleinen Datenbestand und keine realen Scan-Dateigrößen.

| Pflichtfall | Nachweis |
|---|---|
| 1: 22 unveränderte | Cloud-only-No-Op + Benchmark: kein Original-Download/-Upload bei Server-SHA |
| 2: ein neuer | Benchmark + bestätigte Zahlung: eine Originalanlage, nötige Metadaten/Index |
| 3: geänderter Beleg | Metadaten-only sowie geänderte Originalbytes auf gleicher ID |
| 4: vorhandene Referenz | verifizierte Dokumentreferenz ohne neue Originalanlage |
| 5–6: DNS vor Beginn/in Liste | begrenzte Versuche, nicht versucht statt 22 Uploadfehler, Login/IDs erhalten |
| 7: Upload-Timeout | verlorene Original-POST-/PATCH-Antwort, manuelle Wiederaufnahme ohne Dublette |
| 8: 401 | bestehender nativer Token-Erneuerungstest während Upload, Konto bleibt erhalten |
| 9: 403 | genau ein Versuch, kein Abmelden |
| 10: gelöschtes Zusatzdokument 404 | Erhalt desselben Slots, keine Neuzuordnung/Ersetzung |
| 11–12: 429/503 | begrenztes Budget, Retry-After einschließlich zu langer Wartezeit |
| 13: Abbruch paralleler Arbeit | Abbruch während unabhängiger paralleler Leseprüfungen; keine parallelen Uploads vorhanden |
| 14: Neustart nach Teilerfolg | separate persistente Room-Test-DB tatsächlich schließen/öffnen, Journal/IDs erhalten |
| 15–16: wiederholen/identische Läufe | verlorene Original-/Metadaten-Antworten sowie identischer Folgelauf ohne Duplikate |
| 17: Indexfehler | erfolgreiche Metadaten, fehlgeschlagener Index, sichere Wiederaufnahme |
| 18: Supplemental | Fehlerphase korrekt, vollständiger Sicherungszeitpunkt unverändert |
| 19: stabile IDs | Original-/Metadaten-/Dokument-/Entity-IDs und unbekannte JSON-Felder erhalten |
| 20: Login/Restore | vorhandene Namespace-, Anmelde-, Restore-/Portfolio-Vertragstests zusätzlich ausführen |

Zusätzliche Fälle: konkurrierende lokale Bearbeitung, fremde/verschobene Ordner, JSON-412, fehlende SHA/ETags, 900-Schritte-Bericht, neuere Cloud-Daten, Bankdatenverlust-Sperre, fehlende lokale aktive Cloud-Datensätze und historische DELETED-Reaktivierung.

## Validierung und offene Abnahme

Lokale direkte Kotlin-/Compose-Kompilierung gegen SDK 36 sowie echte Room-KSP-Generierung sind möglich. Gradle startet in dieser Sandbox nicht: `FileLockContentionHandler` → `Could not determine a usable wildcard IP`. Ergänzende lokale Robolectric-Ausführung nutzt SDK 35 aus dem Cache in ausschließlich externen Testkopien. Eingecheckte SDK-36-Annotationen werden nicht abgesenkt. Diese Ersatzprüfung ist kein bestandener Gradle-/SDK-36-/Emulatorlauf. Der Sandbox fehlt `/dev/kvm`; native Instrumentation muss über GitHub erfolgen.

Lokale Testergebnisse, geänderte Dateien und reproduzierbare Messwerte stehen in [PHASE11E_VALIDATION.md](PHASE11E_VALIDATION.md) und der zugehörigen synthetischen JSON-Evidenz. CI-Run-IDs und Commit-SHAs gehören in die PR-Beschreibung nach tatsächlich ausgewertetem Lauf. Kein grüner CI-Lauf ersetzt den POCO-Test.

Offene Risiken: Google-API-Latenzen, große Originale und langsame mobile Verbindungen können andere Laufzeiten ergeben. Ein 20-Sekunden-Call-Limit kann große Uploads abbrechen; bereits angenommene Anlagen bleiben per reservierter ID fortsetzbar. Unbekannte Cloud-/lokale Konflikte werden bewusst gesperrt und nicht automatisch aufgelöst. SHA-/ETag-Verfügbarkeit und bedingte Updates müssen am echten Backend überprüft werden. Persistierte Diagnose nach abruptem Prozessende ist ein gekennzeichneter Zwischenstand. Die Live-Ursache auf dem POCO muss der neue Bericht bestätigen.

### Reproduzierbarer POCO-X8-Pro-Test, Android 16 / HyperOS

1. Erst nach gesonderter Freigabe eine mit der vorhandenen Original-App kompatibel signierte Test-APK bereitstellen. Bestehende App/Daten nicht deinstallieren. Package und Signatur prüfen; kein Paketwechsel und keine Test-App-Verwechslung.
2. Automatisches Backup **ausgeschaltet lassen**. Bestehende 22 Belege und geöffnetes Original prüfen. Gerät mit stabilem WLAN, App im Vordergrund, HyperOS-Akku „Keine Einschränkungen“; dies ist eine kontrollierte Testbedingung und kein Ersatz für Fehlerbehandlung.
3. Manuelle Synchronisierung starten; Zeit und Phasen beobachten. Nach Ende den vollständigen Diagnosebericht kopieren, tatsächliche Laufzeit und Ergebnis mitteilen. **Dieser neue echte Messwert ist zwingend anzufordern und derzeit OFFEN.**
4. Ohne Datenänderung erneut synchronisieren. Erneut vollständigen Bericht und echte Zeit liefern. Erwartung: 22 unverändert, keine Original-Neuübertragung, nachvollziehbarer Gesamtabschluss. Keine fest versprochene API-Latenz.
5. App neu starten; Konto, 22 Belege, Positionen, Objekt/sieben Einheiten, Splits, DATEV-/KI-Daten und mehrere Originale prüfen. Die zwei bekannten Zusatz-404 müssen erhalten bleiben; keine Cloud-Bereinigung durchführen.
6. Netzwerkfehler und sicherer Abbruch zunächst ausschließlich in isolierten synthetischen Tests prüfen. Keine produktiven Belege für Störungstests verändern. Restore auf dem Produktivgerät nicht automatisch erneut ausführen; eine gesonderte isolierte Restore-Prüfung und die Bestandskontrolle bleiben Teil der Abnahme.
7. Bei Fehler den Bericht einschließlich letzter erfolgreicher Phase weitergeben. Kein Gesamt-Erfolg oder neuer vollständiger Sicherungsstempel bei fehlenden Pflichtteilen. Keine Freigabe allein aufgrund grüner CI.

**Praktische Phase-11E-Abnahme: OFFEN. Kein automatischer Merge, keine APK-Veröffentlichung ohne gesonderte Freigabe.**
