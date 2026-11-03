# Drive 1403 – fehlende Update-Vorbedingung

## Untersuchte Stände

- Auftrag vom 02.11.2026, P1, Hauptbuch-CSV, PRECONDITION/428, 173 Aufrufe, null Schreibaufrufe und 22 nicht abgeglichene Belege (Nutzerbericht).
- Vor Beginn frisch abgerufenes `origin/main`: `5d5e61437fcf7324bb5e0f6442669f777d8ddeda`. Checkout `main`, Arbeitsverzeichnis sauber.
- Version 1403 existiert ausschließlich im offenen Entwurfs-PR #156, Branch `fix/phase11e-drive-sync`, Ausgangs-Head `8f154f85e774b739bc49efac7ef3fb33791cf229`. Dieser enthält den aktuellen main vollständig. Arbeitsbranch der Korrektur: `fix/drive-1403-precondition`.
- Die Reparatur muss die bestehenden Phase11E-Schutzregeln enthalten; ein bloßer Patch auf main würde den gemeldeten Fehlerpfad nicht bearbeiten. Kein Merge nach main und kein öffentliches Release.

## Nachgewiesener Entstehungsort

Commit `67583c1` führte die ETag-Pflicht in `uploadFileToDrive`, `updateSyncJson` und `updateOriginalChecked` ein. Die App erzeugt dort selbst `DriveAccessFailure(HTTP, 428)`, wenn das ETag fehlt. `DriveSyncFailure.from` stellt dies als PRECONDITION dar. Für den gemeldeten Wortlaut muss Google keinen HTTP-428-Response geliefert haben. Der Fehler entsteht vor dem ersten Update.

Die bisherigen Fingerprints entstehen aus v3-Eigentumsmetadaten (`DriveNamespacePolicy.readOwnedMetadata` → `GoogleDriveClient.fetchOriginalFingerprintChecked`). Sie enthalten Datei-ID, Größe, SHA-256, Drive-Version und ausschließlich ein HTTP-Header-ETag. JSON-Snapshots übernehmen dasselbe Metadaten-ETag, ausdrücklich nicht das möglicherweise andere Medien-ETag. Laufbezogene Caches sind kontogebunden; diese Werte werden nicht als dauerhafte Schreibfreigabe gespeichert. Journal/Baseline-Hashes ersetzen kein serverseitiges If-Match.

Der bisherige synthetische HTTP-Transport liefert ausnahmslos ETag-Header. Er konnte deshalb erfolgreiche Änderungen ohne v3-Header nicht abbilden. Google v2 dokumentiert ein Dateiobjekt-Feld `etag`; v3 dokumentiert dieses Feld nicht. Eine Versionsnummer ist nur ein Beobachtungsnachweis und allein keine atomare Schreibvorbedingung.

## Gezielter Ansatz

Für eine Änderung ohne nutzbares v3-Metadaten-ETag wird ein v2-Dateiobjekt ausschließlich für dieselbe bereits nachgewiesene Datei gelesen. ID, Byte-Größe und monotone Drive-Version müssen exakt dem geprüften Vorzustand entsprechen. Ein starkes v2-ETag darf ausschließlich für ein bedingtes v2-Medien-Update verwendet werden; kein Übertragen eines v2-ETag auf die v3-Repräsentation. Das Update muss weiterhin serverseitig If-Match prüfen. Keine Vorbedingung wird aus Hash, Zeitstempel oder Versionsnummer erfunden.

Die Namespace-Policy erlaubt hierfür nur eng begrenzte v2-Einzeldatei-Lesezugriffe und bedingte Medien-Updates. Bestehende v3-Eigentums-, Eltern-, Root-, Shortcut-, Container- und Kontoprüfungen gelten weiter. Keine v2-Suche, Anlage, Löschung, Metadatenänderung oder unbedingte Aktualisierung wird freigegeben. Bestehende Anlagen mit reservierten IDs und Journal bleiben unverändert.

## API-Aufrufzahlen – getrennte Analyse

Die 173 real gemeldeten Aufrufe sind keine 173 Schreibversuche. Vor dem Hauptbuch läuft eine rein lesende Bestandsprüfung; Metadaten, Referenzen und Vorfahren/Root werden geprüft. Der Hauptbuchpfad sucht anschließend die vorhandene Datei und liest ihren Fingerprint (bei fehlendem Server-Hash zusätzlich die Bytes). Das fehlende ETag blockiert danach vor dem Update und vor der Belegschleife. Null Schreibaufrufe und 22 nicht versuchte Belege sind daher mit diesem Ablauf konsistent.

Ohne den vollständigen realen Phasenbericht ist die exakte Aufteilung der 173 Aufrufe nicht belegbar. Verschachtelte Phasenzähler dürfen nicht nochmals zur Gesamtsumme addiert werden. Der neue synthetische 22-Belege-Test protokolliert API-Zahlen je Phase, ersetzt aber keinen Gerätebenchmark. Der Fallback fügt nur bei tatsächlich erforderlichen Änderungen ohne nutzbares v3-ETag einen v2-Lesezugriff einschließlich bestehender Sicherheitsprüfungen hinzu. Keine Cache-, Parallelitäts-, Retry- oder Performance-Optimierung gehört zu diesem Patch.

## Quellen

- https://developers.google.com/workspace/drive/api/reference/rest/v2/files (`etag`, `version`, `fileSize`)
- https://developers.google.com/workspace/drive/api/reference/rest/v2/files/update (bedingter Medien-Update-Pfad, PUT)
- https://developers.google.com/workspace/drive/api/reference/rest/v3/files (`version`, Prüfsummen; kein JSON-ETag-Feld)
- https://developers.google.com/workspace/drive/api/guides/v2-to-v3-reference

## Prüfstatus

### Regression und lokaler Gesamtlauf

- Vor der Produktänderung: `gradle testDebugUnitTest --tests '*Phase11ESyncRegressionTest.drive1403*'` mit Java 21 und SDK 36. **7 echte Methoden, 5 fehlgeschlagen**, keine Skips/Platzhalter. Die Produktdateien entsprachen dabei unverändert `8f154f8`. Der Hauptbuch-Test reproduziert PRECONDITION/428 nach Bestandsprüfung mit **137 API-Aufrufen und null Schreibzugriffen**. [XML](drive-1403-evidence/red-regression.xml), [vollständige synthetische Phasenmessung](drive-1403-evidence/red-full-sync.json).
- Nach der Korrektur: `gradle testDebugUnitTest -Proborazzi.test.record=true --no-daemon --no-configuration-cache` mit Java 21, Gradle 9.3.1 und unveränderten SDK-34-/35-/36-Annotationen: **1.335 Methoden, null Fehler, null Skips, null Suite-Platzhalter**, BUILD SUCCESSFUL. `verify-test-execution.py` ebenfalls bestanden. [Gesamtzählung](drive-1403-evidence/local-unit-summary.json).
- Darin **83/83 Phase11E-Methoden** und **7/7 neue Namespace-Methoden**: [Phase11E-XML](drive-1403-evidence/TEST-com.example.ui.Phase11ESyncRegressionTest.xml), [Namespace-XML](drive-1403-evidence/TEST-com.example.api.DriveConditionalWriteNamespaceTest.xml).
- Im ersten fokussierten grünen Produktlauf waren zwei neue Testaufbaufehler sichtbar: mitgezählte Initialisierungs-POSTs und eine erwartete rohe statt der bereits bestehenden kontrolliert verpackten Netzwerkexception. Nur diese Testannahmen wurden korrigiert; der vollständige 1.335-Methoden-Lauf enthält die korrigierten Tests. Produktprüfungen und Sicherheitsassertions wurden nicht abgeschwächt.
- `python3 -m unittest discover -s .github/scripts/tests -q`: **16/16 bestanden**.
- Lokale APK-Identitäts-/Providerprüfung über `e11a-inspect-apk.py`: bestanden, Original-Package und VersionCode 1403; temporäres lokales Prüfzertifikat, keine Produkt-Signaturfreigabe.
- `git diff --check`: bestanden. Unabhängige statische Prüfung der neuen Namespace-/Versionspfade: keine konkreten Befunde; keine Live-API-Verifikation.
- `gradle lintDebug assembleDebug assembleDebugAndroidTest --no-daemon --no-configuration-cache` mit Java 17: **BUILD SUCCESSFUL**. Beide APKs erstellt; Lint erfolgreich. Die lokale APK verwendet ausschließlich ein temporäres Prüfzertifikat und ist kein Nachweis für ein updatekompatibles Produkt-APK. Die Produkt-Signatur wird separat in CI geprüft.
- Zusätzliche lokale CI-Audits: Phase10B-Portfolio/DATEV-Nachweis sowie Phase11B-, Phase11C- und Phase11D-Ausführungsnachweise bestanden. Der strikte `e11a-verify-repair.py`-Nachweis ist lokal **nicht bestanden**: Die erzeugten lokalen Snapshots tragen korrekt `commit=LOCAL`; der unveränderte Prüfer verlangt eine echte 40-stellige Commit-Provenienz (`missing app commit`). Keine Metadaten wurden nachträglich ersetzt und keine Prüfung gelockert. Dieser Nachweis muss im GitHub-Lauf mit tatsächlichem SHA bestanden werden.

### Verpflichtende Abnahmefälle

| Fall | Automatischer Nachweis |
|---|---|
| Gültige bestehende Vorbedingung | Bestehende bedingte v3-Updates bleiben im vollständigen Phase11E-Lauf grün; neuer v2-Originaltest prüft dieselbe ID und If-Match. |
| Keine Vorbedingung verfügbar | Fehlende v3- und v2-ETags erlauben keinen Schreibzugriff; unveränderte No-Ops bleiben möglich. Wildcard, schwache und ungebundene Validatoren bleiben gesperrt. |
| Externe Änderung vor dem Fallback | Gleiche Dateigröße bei anderer Version wird vor jedem Update gesperrt; kein frisches ETag wird einem alten JSON-Snapshot zugeordnet. |
| Externe Änderung unmittelbar vor dem Schreiben | Serverseitiges 412 auf das alte If-Match; externe Bytes bleiben unverändert. |
| Anlage und Wiederholung | Vollabgleich legt einen zusätzlichen Originaldatensatz genau einmal an; 23 eindeutige Belege und nachfolgend null Schreibzugriffe. Bestehende Wiederaufnahme-/Journaltests bleiben grün. |
| Originale, Metadaten, Index, Hauptbuch | Vollständiger ViewModel → Repository → echter DriveClient → synthetischer HTTP-/Room-Ablauf. Hauptbuch entspricht den finalen lokalen Zeilen; neuer Indexeintrag referenziert dieselben Original-/Metadaten-IDs. Alle 22 Bestandsoriginale behalten ID und Bytes. |
| Offline und Berechtigungen | v2-Lesen mit 403 oder DNS-Fehler sperrt vor jedem Schreiben; bestehende kontrollierte Netzwerkklassifikation einschließlich Ursache wird geprüft. |
| Lokale Daten und Zuordnungen | Bestehende IDs, Display-IDs, Immobilienzuordnung, Beträge und lokale Dateipfade bleiben erhalten. Kein Room-/Modell-/Migrationscode geändert. |
| Namespace | Fremde oder inzwischen verschobene Dateien, fehlende Vorfahren, geschützte Ordner und andere v2-Operationen bleiben gesperrt. |

### Synthetische API-Zahlen nach der Korrektur

| Ablauf | Belege | API-Aufrufe | Anlagen | Updates | neue Originale | Ergebnis |
|---|---:|---:|---:|---:|---:|---|
| Erster vollständiger Lauf, veraltetes Hauptbuch, Struktur erstmals ergänzt | 22 | 1072 | 21 | 20 | 0 | erfolgreich |
| Eine lokale Metadatenänderung | 22 | 393 | 0 | 5 | 0 | erfolgreich |
| Ein zusätzlicher Beleg | 23 | 607 | 3 | 5 | 1 | erfolgreich |
| Unveränderte Wiederholung | 23 | 269 | 0 | 0 | 0 | erfolgreich |

[Erstlauf](drive-1403-evidence/drive1403-no-v3-etag-first.json), [Änderung](drive-1403-evidence/drive1403-no-v3-etag-edit.json), [Anlage](drive-1403-evidence/drive1403-no-v3-etag-create.json), [Wiederholung](drive-1403-evidence/drive1403-no-v3-etag-repeat.json).

Das sind **keine Verbesserungsquoten gegenüber den realen 173 Aufrufen**: Der bisherige Lauf brach am Hauptbuch ab, die erfolgreichen Läufe durchlaufen sämtliche späteren Phasen und teils neue Ordner. Der verbleibende Umfang rein lesender Eigentums-/Versions-/Referenzprüfungen ist ein getrenntes Performance-Thema. Es wurden keine Prüfungen eingespart und keine Leistungsoptimierungen beigemischt.

### GitHub und offene Geräteabnahme

Die nach dem Push zu diesem Dateistand gestarteten GitHub-Checks werden im PR #156 mit Run-ID und SHA dokumentiert; historische Checks für `8f154f8` gelten nicht als Nachweis für die neue Korrektur. Kein automatischer Merge, kein öffentliches Release. Kein lokales `/dev/kvm`; echte Instrumentation läuft auf GitHub.

**Realer Google-Drive-/POCO-/Android16-Abnahmelauf OFFEN.** Synthetische HTTP-/Room-Tests sind kein Nachweis, dass die reale Google-API das erwartete ETag-/If-Match-Verhalten mit dem Gerätekonto liefert. Keine produktiven Kontodaten wurden abgerufen oder verändert.
