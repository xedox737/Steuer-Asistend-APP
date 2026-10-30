# Phase11D – Abnahme der Reparatur veralteter Diagnose-Verweise

Stand: 30.10.2026 01:24 CET. **Automatisierte Abnahme bestanden. Praktische Geräte-/Update-Freigabe weiterhin NO-GO.**

Die Korrektur liegt in [PR151](https://github.com/xedox737/Steuer-Asistend-APP/pull/151), weiterhin Draft und ohne Merge. Fehlende alte Diagnose-Testdateien blockieren im reparierten Code die bestehende Drive-Initialisierung nicht mehr. Sicherheitsprüfungen für echte Daten und Ordner bleiben aktiv. Die Reparatur wurde noch nicht auf dem POCO ausgeführt. Dieser Nachweis aktualisiert die ausstehenden CI-Felder der [Quellanalyse](https://github.com/xedox737/Steuer-Asistend-APP/blob/862776bba5ce854687b06637e32e9dfbf49bee54/docs/PHASE11D_STALE_DIAGNOSTIC_REFERENCES.md) und die früher unbekannte Gerätesignatur im alten Phase11D-Bericht.

## Exakte Quellen und CI

| Gegenstand | SHA / Nachweis |
| --- | --- |
| Tatsächliche Main-Base | `bdc17be4abc1398dabddb28eda2d9074d455a031` |
| Vorheriger geprüfter PR-Head | `ff1b6eaaba2b3e1a6c0349c908b981175f6888f3` |
| Neuer geprüfter Reparatur-Head | `862776bba5ce854687b06637e32e9dfbf49bee54` |
| Geprüfter Source-Tree | `90007c9276995f716a760cd0bd30cbc0083a29e5` |
| Inhaltsgleicher lokaler Fix-Commit | `bf762d71cfec385ab040d0702280581d73b371c0` |
| Tatsächlicher synthetischer PR-Merge-Checkout | `5bcbd2d16cc9e1132ea8a98f0ffd69acdd49c6e3` |
| Vollständige CI, final Versuch2 erfolgreich | [40971627197](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/40971627197) |
| Ursprünglicher erfolgreicher verify-Job | `123789520458` |
| Erster roter Emulatorjob | `123797940054` |
| Erfolgreicher Emulatorjob auf frischer Instanz | `123809161603` |

Der direkte Fetch scheitert am Sandbox-Proxy. Main und PR wurden unabhängig über die GitHub-Schnittstelle geprüft. Dateien/Tree des übertragenen Fixes stimmen über Git-Hashes mit der lokalen Quelle überein. Der Bericht wurde separat auf dem Dokumentationsbranch ergänzt, damit der bereits geprüfte Code und dessen CI-Lauf unverändert bleiben.

## Befund, Reparatur und vorher rote Fälle

Die vom Nutzer hochgeladene Konfiguration enthält alte `testReferences`. Beim JSON-Laden prüft `GoogleDriveClient.downloadJson` auch diese Verweise mit `DriveNamespacePolicy.validateJsonReferences`. Der eigene HTTP404 einer Diagnose-Datei blockierte die ganze Initialisierung. Die Drive-Browsermeldung zum alten Testoriginal passt dazu; welche konkrete Metadatenabfrage auf dem Gerät404 lieferte, wurde nicht mitgeschnitten. Der Fehlerpfad ist durch echte Repository-/HTTP-Vertragstests mit simuliertem Transport reproduziert.

Vor dem Produktfix:21 Namespace-Methoden, genau3 Fehler (Diagnose-Original fehlt, Metadaten fehlen, beide fehlen). Ein weiterer ViewModel-Lauf mit48 Methoden hat genau1 Fehler: Der echte Initialisierungsfehler wurde durch „Drive nicht initialisiert“/„Beleg-Speicherungs-Test“ verdeckt. Ein Python-Test war wegen des falschen bisherigen Pins rot. Die vollständigen roten Protokolle sind im Evidence-Archiv erhalten.

Die enge Korrektur toleriert nur den eigenen404 der beiden direkten Felder `testReferences.mainDriveFileId` und `testReferences.metadataFileId` einer vollständigen, passenden Schema1-App-Konfiguration. Bestehende Dateien werden weiterhin auf Namespace und Dateityp geprüft. Fehlende Vorfahren, echte aktuelle Datei-/Ordnerverweise, geschachtelte gleichnamige Felder und fremde erreichbare IDs bleiben blockiert.401/403/503,Timeout und Cancellation bleiben Fehler beziehungsweise Abbruch. Es wird kein Zugriff auf fehlende oder fremde Dateien gewährt, keine Konfiguration umgeschrieben und keine Ersatzstruktur angelegt.

`ReceiptViewModel.runRealDriveTest` zeigt jetzt die tatsächliche Initialisierungsphase, sichere vorhandene Fehlermeldung und HTTP-Status. Ein ausdrücklich gestarteter Beleg-Speichertest kann weiterhin an seinen eigenen nicht erreichbaren Testdateien scheitern; die Korrektur löscht/ersetzt sie nicht automatisch. Erfolgreiche Initialisierung wird nicht als erfolgreicher vollständiger Speichertest ausgegeben.

## Tatsächlich bestandene Prüfungen

| Prüfung | Ergebnis |
| --- | --- |
| Vollständige JVM-/Robolectric-Suite | 1.213 eindeutige Methoden,0 Fehler/Errors/Skips |
| Namespace-Regressionen | 23 Methoden bestanden, einschließlich9 neuer Fälle |
| Native Auth-/ViewModel-Regressionen unter SDK36/Java21 | 48 Methoden bestanden (10native SDK-Verträge+38ViewModel), Quellhashes unabhängig abgeglichen |
| Python-Signaturtests | 9 bestanden, einschließlich Gerätezertifikat-Pin und Ablehnung inkompatibler APKs |
| Lint und Debug-APK-Build | Bestanden |
| Build der Instrumentation-APK und native Emulatorprüfung | Bestanden;70 eindeutige Methoden,0 Fehler/Errors/Skips;API35/Android15 |
| Drive-Reparatur-/Integritätswächter | 45 tatsächliche Methoden bestanden |
| Originaldateien und Restore/MERGE | 60/60 SHA256 identisch,0 Full-Restore- und0 MERGE-Fehler;zweimaliges MERGE |
| 17-/21-Einheiten-Portfolio, Bank/DATEV und Fachregressionen | Unabhängiger Audit ohne Fehler |

JVM-/Emulator-XMLs wurden aus heruntergeladenen Artefakten erneut gezählt. Archivgröße und SHA256 stimmen mit GitHub überein. Keine Tests, Assertions oder Timeouts wurden abgeschwächt. Reguläres lokales Gradle bleibt wegen fehlender Wildcard-IP blockiert; lokal ergänzend frisch kompilierte SDK35-Läufe mit23+48 Methoden und9 Python-Tests bestanden. Diese Ergänzung ersetzt nicht die oben tatsächlich ausgeführte vollständige CI.

### Roter Emulatorversuch bleibt erhalten

Versuch1 hatte70 Methoden:1 bestanden,69 fehlgeschlagen,0 übersprungen. Alle69 Fehler liegen identisch **vor dem Testkörper** in der unveränderten Fokusprüfung `EmulatorTestSupport.kt:99`. Screenshot, Fensterhierarchie und InputDispatcher belegen den externen ANR „Pixel Launcher isn't responding“ (`com.google.android.apps.nexuslauncher`); das App-Crashlog ist leer. Dieser Lauf ist ausdrücklich kein Erfolg.

Nach dieser Diagnose wurde nur der fehlgeschlagene Emulatorjob auf einer frischen Instanz erneut gestartet. Kein Produkt-/Testcode, keine Assertion und kein Timeout wurden verändert; kein automatischer Retry wurde eingebaut. Versuch2 besteht mit70/70 tatsächlichen Methoden. Beide Ergebnisse und der erste Fehlernachweis stehen im Archiv. Die ursprüngliche verify-Prüfung wird am selben unveränderten Head übernommen.

## Tatsächlich installierte Signatur und gebautes Artefakt

Die per ADB vom Nutzergerät kopierte APK ist unabhängig geprüft und stimmt bytegenau mit Release103 überein:Package `com.aistudio.steuerassistent.vnhqkp`,34.344.813 Bytes,APK-SHA256 `a924bddf49c0181f0fc17facb896d4eb8bd159a4a95085c1df42128d631bbc28`. Ein RSA2048-Signer, gültige v2-Signatur.

**Geräte-Zertifikat-SHA1: `CF:43:8D:2E:D2:1D:0F:D5:F8:B0:AB:81:BF:51:8A:51:78:A7:73:85`.** Zertifikat-SHA256: `1af38eddb4652685580a472541c57eab80e1249197372e644ca825d449f25ef7`. Der frühere EA-Wert war eine Download-Referenz einer anderen APK. Die Publisher-Wächter verwenden jetzt den tatsächlich gemessenen CF-Wert.

Die neue CI-APK hat gültige Signatur, aber Zertifikat-SHA1 `92d0cd8603af6a9fec5c944ccb9eba5fc24addd3` und APK-SHA256 `2de2291e3faf649b72eced33aec0b59713a088e49a9386f7295d24fe47dec4fb`. Sie passt **nicht** zur installierten App. CI meldet `STABLE_PRODUCT_DEBUG_SIGNING=false`; der zusätzliche Produktupdate-Zertifikatscheck wurde daher für das reine PR-Testartefakt übersprungen. Kein kompatibles Produktupdate oder Release ist freigegeben. Artifact-ID12941308192 ist nur ein CI-Testartefakt, keine Installationsanweisung.

Der einzige im Workspace gefundene lokale Debug-Schlüssel hat ebenfalls eine andere öffentliche Signatur. Ein passender privater Schlüssel für103 ist hier nicht verfügbar; ob er außerhalb vorliegt, ist unbekannt. Private Schlüssel lassen sich nicht aus der APK rekonstruieren und werden nicht im Chat angefordert oder veröffentlicht. **Keine Deinstallation als Umgehung und kein Ersetzen der produktiven Installation.**

## Daten- und Freigabegrenzen

Sechs geschützte Quellen sind bytegleich mit Main:DriveEnvironment,Room-Datenbank,SupplementalBackup,Preference-Inventar,Buildkonfiguration und Manifest. Die Namespace-Policy enthält nur die beschriebene enge Ausnahme für optionale Diagnose-Verweise; echte Daten und fremde Bereiche bleiben geschützt. IDs, Beziehungen, lokale Daten, Backups und Drive-Inhalte des Nutzers wurden nicht verändert. Die isolierte Phase11-Test-App blieb unverändert. Portfoliozahlen und60 Originale sind synthetische Testsätze, keine Behauptung über die tatsächliche Dateianzahl auf dem Gerät.

Offen: passender dauerhafter CF-Signierschlüssel und positiv geprüfte kompatible APK; realer Test der reparierten App auf Android16 mit Google-Login,Root-Prüfung,Neustart,Netzausfall,Token-Erneuerung und sicherem Backup/Restore. Die genaue Geräte-404-Abfrage ist nicht bewiesen. Die bestehenden Grenzen zur veralteten Google-Identity-API und zum unveränderten optionalen Web-Altpfad bleiben dokumentiert. Keine automatische Datenmigration, keine Bereinigung und kein Merge.

## Reproduktion

`Evidence.zip` enthält tatsächliche XMLs,CI-Metadaten,Integritätsberichte,öffentliche Signaturnachweise,Quellschutz-Hashes und rote/grüne Regressionen. Es enthält weder die Nutzer-Konfiguration noch private Schlüssel oder APK-Bytes. Entpacken und `python3 verify-evidence.py` ausführen:SHA256-Manifest,1213 JVM-,70 native-,23 Namespace-,48 Auth-Methoden,60 Originalhashes und die weiterhin offene Update-Freigabe werden erneut geprüft. `checksums.json` enthält den Archivhash. Vollständiger Neubau über Android-CI am angegebenen Head mit Java21 für JVM/SDK36 und Java17 für Android-Build und Emulator.

**Automatisierte Code-Abnahme: bestanden. Praktische Produkt-/Gerätefreigabe: NO-GO.**
