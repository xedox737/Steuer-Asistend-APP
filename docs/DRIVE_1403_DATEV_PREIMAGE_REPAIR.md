# Drive 1403: DATEV-Vorzustand und vollständige Regression

## Ausgangspunkt und Nachweisgrenze

Nutzerbericht vom 02.11.2026: Hauptbuch erfolgreich aktualisiert, Immobilien-/Einheitendaten
unverändert, danach DATEV `CONFLICT / BACKUP_PREIMAGE_CONFLICT`, 79,9 Sekunden,
254 API-Aufrufe, 3.786 übertragene Bytes, 22 Belege nicht abgeglichen.

Vor Änderungen: `/workspace/Steuer-Asistend-APP`, Branch `work`, sauber,
HEAD `5d5e61437fcf7324bb5e0f6442669f777d8ddeda`.
`git fetch origin main` scheitert am nicht erreichbaren Workspace-Proxy.
Die authentifizierte GitHub-Verbindung bestätigt denselben aktuellen main-SHA und
Main-Tree `268ad2b901106aa97561d42002cba098fef28776`.

Reparaturarbeitsbaum: `/workspace/drive1403-datev`, eigener Branch
`agent/drive1403-datev-preimage`, aus diesem Main angelegt. Version 1403 und die betroffene
Konfliktprüfung existieren bislang im offenen PR #156. Deshalb wird dessen aktueller Head
`57ab32d239869fc590af81de9d8eeea175366377` als ausdrückliche Abhängigkeit übernommen.
Der lokal materialisierte Ausgangsbaum stimmt vollständig mit dessen Tree
`046a9c6384b0fc0b0ec08c0b386290e20ecbe823` überein. Lokale Snapshot-Commits sind keine
GitHub-Originalhistorie; der veröffentlichte Reparaturcommit hat den tatsächlichen
GitHub-Head als Parent. Der Folge-PR gegen #156 zeigt nur die neue Korrektur.

**Der private lokale und Remote-DATEV-Datenbestand liegt nicht vor.** Nachgewiesen sind
reproduzierbare Fehler im gemeldeten Codepfad. Ob genau diese Unterschiede den Gerätelauf
ausgelöst haben oder ob dort ein echter Konflikt vorliegt, muss der neue sichere Feldbericht
zeigen. Kein produktiver Drive-Zugriff und keine Behauptung einer erfolgreichen Live-Reparatur.

## Konkreter Codepfad und Ursache

`ReceiptViewModel.syncAllToDrive` führt Bestandsprüfung, Hauptbuch, Ordner und Einheiten aus.
`DrivePersistenceRepository.saveStammdatenToDrive` sichert danach Immobilien, Einheiten,
DATEV, Lernregeln, Kategorien und Zuordnungen. DATEV läuft über
`serializeDatevProfile` → `uploadOrUpdateJson` → `GoogleDriveClient.updateSyncJson` →
`DriveBackupConflictGuard.validate`.

Der Client vergleicht kanonisches JSON. Objekt-Schlüsselreihenfolge wird normalisiert,
String-Inhalte bleiben bewusst exakt. Bei unterschiedlichem Inhalt verlangt der Guard
für mehrdeutige Änderungen eine bekannte gemeinsame Vorversion aus `DrivePendingWrites`.
Dieses neue Journal liegt installationslokal in `noBackupFilesDir`; ein bestehender oder
wiederhergestellter fachlicher Bestand muss deshalb nicht schon eine Journal-Baseline besitzen.

DATEV besitzt `lastModified` (bei unabhängiger Default-Erstellung ein neuer Zeitstempel),
der generische Guard interpretiert dagegen nur `updatedAt`. Außerdem ist
`categoryKontoMapJson` eine in einem String gespeicherte Kontenmap. Gleiche Kontenmaps mit
anderer Reihenfolge/Whitespace sind für DATEV gleich, für den generischen Stringvergleich
verschieden. Die neuen HTTP-/Room-Tests reproduzieren damit die DATEV-Sperre bei identischen
Fachdaten. Ein regulärer Restore erhält vorhandenes `lastModified`; er erzeugt diesen Fehler
also nicht zwangsläufig.

Dieselben Daten stehen zusätzlich unter
`SupplementalDriveBackup.createPayload` → `PersistentPreferenceInventory.writePayload` →
`datevProfilePrefs.active_profile_json.value`. Eine alleinige Änderung des DATEV-Schritts
würde den Konflikt in die spätere Zusatzdatensicherung verschieben.

Der frühere 428-Fall ist eine andere Schutzstufe: Die Hauptbuch-CSV verlangt einen atomaren
HTTP-Schreibvalidator. PR #156 Head 57ab32d bindet bei fehlendem v3-ETag ein starkes v2-ETag
an dieselbe ID, Größe und Drive-Version und verwendet es ausschließlich für bedingtes v2-PUT.
DATEV scheitert im gemeldeten Codepfad bereits vor dieser HTTP-Stufe am Inhaltsvergleich.
Die 428-Korrektur und sämtliche dazugehörigen Gegenfälle bleiben enthalten.

## Minimale Korrektur

Nur zwei neue/geänderte Produktdateien gegenüber 57ab32d:

- `data/DriveDatevPreimage.kt`: eng begrenzter DATEV-Vergleich an beiden vorhandenen
  Speicherstellen. Profil-ID, Profilversion und alle übrigen Felder einschließlich unbekannter
  Felder müssen übereinstimmen. Nur `lastModified` wird beim Äquivalenznachweis ausgeklammert;
  die enthaltene Kontenmap wird ausschließlich bei gültigen Stringwerten strukturell verglichen.
  Moshi prüft dabei dieselbe strikte JSON-Grammatik wie DATEV. Ungültige JSON-Texte werden
  nicht als gleichwertige gültige Profile übernommen.
- `api/GoogleDriveClient.kt`, `updateSyncJson`: Bei nachgewiesener Äquivalenz bleibt die
  Remote-Repräsentation erhalten. Ein reiner No-Op schreibt nichts; bei anderen Änderungen
  derselben Zusatzdatensicherung bleibt deren eingebetteter Remote-Profilstring exakt erhalten.
  Hash, Bestätigung und Lauf-Cache beziehen sich auf den tatsächlich geschriebenen Inhalt.
  Echte Konflikte melden ausschließlich feste DATEV-Feldnamen beziehungsweise `unknownFields`.
  Werte, dynamische JSON-Schlüssel, Namen und Kontonummern werden nicht protokolliert.

Keine Annahme „lokaler Zeitstempel ist neuer, also überschreiben“. Keine allgemeine
Normalisierung anderer JSON-Strings. Keine Lockerung des generischen Konfliktguards.
Unbekannte Fachänderungen bleiben gesperrt. Eine bekannte Baseline erlaubt weiterhin eine
bewusste lokale Bearbeitung unter derselben ID und mit serverseitigem If-Match.

Namespace-/Vorfahren-/Kontoprüfung, rohe Byte-Hashes, Drive-Version vor/nach dem Lesen,
starke ETags, 412/428 und abschließende Read-Proof-Prüfung bleiben bestehen.
Mehrdeutige Stammdatendateien werden im vorhandenen `findFileByAppProperty(failOnError=true)`
weiter abgewiesen; keine Datei wird gelöscht, neu zugeordnet oder aufgrund ihrer Größe adoptiert.

## Rot/Grün und Regression

`DriveDatevPreimageRegressionTest`: zwölf neue Methoden mit echtem Repository/Client und
synthetischem HTTP-/Room-Bestand. Unabhängig geschriebene Tests prüfen Timestamp-No-Ops in
beiden Richtungen, Map-Darstellung, echte Konten-/Mandantenänderungen, unbekannte Felder,
bekannte Baseline, ungültige JSON-Grammatik, Profil-ID/-Version, ungültige Zeitstempel und die
eingebettete Supplemental-Kopie mit neuer Standardroute und nachfolgendem No-Op.

Vor Produktänderung: acht Methoden ausgeführt, sechs rot. Vier Fehler betreffen die unerwartete
Sperre gleichwertiger Daten; zwei die noch fehlende Felddiagnose. Echte Kontenänderung und
bewusste Bearbeitung mit bekannter Baseline verhalten sich schon im Ausgangsstand korrekt.
Eine zusätzliche Gegenprüfung des ersten Helpers weist permissives org.json nach:
ein gezielter Test rot; anschließend wird die Parsergrenze mit Moshi abgesichert.
Der abschließende lokale Zwölf-Methoden-Lauf ist grün, ohne Fehler oder Skips.

`Phase11ESyncRegressionTest.datevEquivalentPreimageCompletesEveryPhaseAndResumesAfterLostSupplementalAcknowledgement`
prüft 22 vorhandene Belege, sämtliche Folgephasen, baselinefreien DATEV-No-Op an beiden
Speicherstellen, Metadatenänderung, zusätzlichen 23. Beleg, verlorene Supplemental-Antwort,
sichere Fortsetzung ohne zweiten Upload, zweimaligen MERGE-Restore und vollständige
unveränderte Folgesynchronisation. IDs, Originalbytes und Remote-DATEV bleiben erhalten.
Der bestehende Restore legt bei Bedarf genau einen leeren `deletions`-Ordner an;
der zweite Restore darf keinen weiteren anlegen. Bestehende Dateien bleiben bytegleich.

| Geforderter Ablauf | Konkreter automatisierter Nachweis |
|---|---|
| Erstmalige Sicherung ohne vorhandene Sicherungsdateien | Neuer Repository-Testaufbau erzeugt den Speicher über die reale Initialisierung; bestehende Initialisierungs- und Phase11E-Tests |
| Unveränderte Wiederholung, vollständiger zweiter Lauf | Neue durchgehende Pipeline; null POST/PATCH/PUT, identische IDs und Bytes |
| Neue lokale Belege | Neuer 23. Beleg; bestehender 1403-Volltest und `legacyStockPlusOneNewReceiptRemainsTwentyThreeWithNoDuplicateOriginals` |
| Belegmetadaten ändern | Neuer Pipeline-Test; `oneChangedReceiptUpdatesMetadataAndPreservesUnknownCloudFields` |
| Bestehende Hauptbuch-CSV und früherer 428 | Sämtliche sieben `drive1403*`-Methoden, darunter v2-Version, fehlende/starke Validatoren und 412 |
| Immobilien und Einheiten | Reale Stammdatensicherung in jedem neuen Repository-Test; Portfolio-/RoundTrip-Suite |
| DATEV-Profile | Zwölf neue Methoden, exakte Remote-Bytes/IDs und lokales Profil geprüft |
| Weitere Stammdaten und Supplemental | Gesamter Phase11E-Lauf; neue Supplemental-Kopie, Standardroute und vollständiger Payloadvergleich |
| Neuerer/unbekannter Remoteinhalt | Neue DATEV-Gegenfälle; `unknownNewerRemoteSnapshotCannotBeOverwrittenByOlderLocalData` |
| Fehlende/ungültige Versionsnachweise | Bestehende JSON-/Originalversion-, ETag-, Encoding- und 428-Tests |
| Konkurrierende Schreiber | `aConcurrentJsonChangeRejectsTheOldConditionalWrite`, `drive1403ConcurrentChangeAfterV2ReadIsRejectedByServerIfMatch` |
| Netzwerkabbruch | Neue verlorene Supplemental-Antwort; bestehende Original-/Metadaten-/POST-Lost-Ack-Tests |
| Teilweise erfolgreicher Lauf | Neuer Pipeline-Test; Index-Unterbrechung und persistenter Room-Neustart |
| Wiederholter Restore, Sync danach | Neuer doppelter MERGE; `SupplementalDriveBackupTest`, `Phase10ADriveRoundTripTest`, `RestoreMergeDataIntegrityTest` |

Die vollständige CI muss alle vorhandenen Unit-/Robolectric-Tests, Drive-/Restore-/Portfolio-/
Bank-/DATEV-/Login-Ausführungsaudits, lintDebug, assembleDebug, assembleDebugAndroidTest und
die tatsächlichen API35-Instrumentation-Methoden ausführen. Aktuelle Run-ID, Checkout-SHA,
Zählungen und APK-Nachweise werden nach Auswertung im Folge-PR dokumentiert.

## Lokale Prüfgrenzen und unveränderte Funktionen

Gradle 9.3.1 startet in dieser Sandbox nicht (`FileLockContentionHandler`, kein nutzbares
Wildcard-IP). Das betrifft fokussierte und vollständige Gradle-Unit-Aufrufe sowie
lintDebug/assembleDebug/assembleDebugAndroidTest; diese gelten lokal ausdrücklich nicht
als bestanden. Die angebotene zusätzliche Netzwerkfreigabe wurde abgebrochen.

Ersatzprüfung: frische Kotlin-/Compose-Kompilierung der aktuellen Produktquellen gegen SDK36,
Java21, frisch mit KSP erzeugte benötigte Room-Implementierungen. Lokale Robolectric-Läufe
verwenden eine externe SDK35-Testkopie und vorhandene Ressourcen; eingecheckte SDK36-
Annotationen und Buildregeln bleiben unverändert. Das ist keine vollständige Gradle-Prüfung.
Die 16 Python-Tests der CI-/Signaturprüfung sind lokal bestanden.

Room bleibt Version34; keine Migration oder Tabellenänderung. Keine Änderung von
Google-Anmeldung/Tokenverwaltung, Wiederherstellungslogik, Bankbuchungsliste, DATEV-Export,
Navigation, Header, Bottom Navigation, StateFlows oder CoroutineScopes. Keine neuen
Netzwerkoperationen, automatischen Sync-Starts, Retries, Löschungen oder lokalen Rücksetzungen.
Die Tests ergänzen Laufnachweise für die vorhandenen Datenwege; die Fachmodelle bleiben gleich.

## Performance und Abnahme

Die synthetischen Pipeline-Berichte enthalten je Phase Dauer, list/get/create/update,
übertragene Bytes und Wiederholungen. Der 22-Belege-No-Op benötigt im lokalen Test
258 Leseaufrufe und null Schreibzugriffe; nach Anlage des 23. Belegs 269 Leseaufrufe,
ebenfalls null Schreibzugriffe bei Fortsetzung und unveränderter Wiederholung.
Diese Werte sind kein Gerätebenchmark und kein Geschwindigkeitsgewinn gegenüber dem
realen Abbruch nach 254 Aufrufen. Die reale Aufteilung der 79,9 Sekunden ist ohne
vollständigen Gerätebericht unbekannt. Keine Performance-Architekturänderung wird beigemischt.

Wiederholte Zielmetadaten-Lesezugriffe entstehen in der bestehenden Eigentumsprüfung,
Byte-/Versionsprüfung und der abschließenden erneuten Bestätigung. Der aktuelle Laufcache
teilt Vorfahren und JSON-Snapshots nur innerhalb desselben Kontos/Laufs; Schreibzugriffe
invalidieren Nachweise. Diese Schutzlesungen werden nicht pauschal entfernt.

**Live-Drive-Test: nicht durchgeführt.** Es gibt keine autorisierte isolierte Live-Testumgebung
mit dem betroffenen Datenbestand. **Android-/POCO-Produktabnahme bleibt erforderlich:**
vollständiger Sync mit dem betroffenen Bestand, Prüfung aller 22 Belege und sämtlicher
Sicherungsphasen, unveränderte zweite Synchronisation sowie sichtbare Wiederherstellbarkeit.
Bei erneutem Konflikt den neuen Bericht mit festen Feldnamen auswerten; keinen Remote- oder
Lokalbestand löschen, keinen ungeprüften Restore auslösen und keine App deinstallieren.
Grüne CI, Signatur und Emulator sind dafür notwendige technische Nachweise, kein Ersatz.
Kein automatischer Merge nach main und keine Behauptung „auf POCO erfolgreich getestet“.
