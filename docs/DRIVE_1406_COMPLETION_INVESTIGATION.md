# Version1406: Diagnose des Abschlusswiderspruchs nach eigenen Schreibvorgängen

## Status: Ursache nicht nachgewiesen, keine Produktabnahme

Ausgangspunkt ist der tatsächliche PR158-Head
`c0c01420088c48fb918495f57940d5382e37bee5` (Version1405), Baum
`cb99876013d36ce7a82aafaab07abbbf4e7fedfd`. Main wurde vor Arbeit erneut als
`5d5e61437fcf7324bb5e0f6442669f777d8ddeda` bestätigt. Der saubere lokale
Checkpoint `42fbc74e6c048160d231044d79ffc16a787a87c9` enthält exakt diesen
veröffentlichten Baum; separate Arbeitsbranch `agent/drive1406-completion-proof`.
Synthetische Checkout-Historie wird nicht gepusht. Git-Fetch/gh-CLI-Einschränkungen
bleiben dokumentiert; GitHub-Connector ist ausdrücklich autorisiert.

Der reale POCO-Lauf1405 vom03.11.2026 09:15:52Z scheiterte trotz grüner CI1376/70
und sauberem Codex-Review:22/22 Belege unverändert, Dokumentindex17856 Bytes und
Zusatzsicherung308122 Bytes erfolgreich geschrieben, dann LOCAL_PRECONDITION in
der Abschlussprüfung (15GETs,0Schreibzugriffe). Gesamt418 Aufrufe/200,7s.
Der Nutzer bestätigte anschließend, dass der unveränderte zweite Lauf funktioniert.
Der vollständige Bericht dieses erfolgreichen Zweitlaufs liegt nicht vor.

Lesende Prüfung der beiden neuen Drive-Payloads: Dokumentindex weiterhin24 identische
IDs; einzig abweichendes Dokumentfeld ist der eine nachgewiesene Hash. Die Hauptschlüssel
der Zusatzsicherung und Anzahlen ihrer bisherigen Datensatzgruppen bleiben erhalten.
Dies beweist weder sämtliche einzelnen Fachwerte noch eine erfolgreiche Abschlussprüfung
des ersten Laufs. Keine produktiven Drive-Dateien wurden bei der Untersuchung geändert.
Private Inhalte liegen außerhalb von Git und CI.

## Eingrenzung und widerlegte Vermutung

Die gemeldete Ausnahme stammt aus `GoogleDriveClient.withSyncReadProof`: vor Erfolg
vergleicht die Abschlussprüfung gespeicherte JSON-/Original-Fingerprints mit frisch
gelesenen, eigentumsgeprüften Metadaten. Ein leerer Ausgangsversionsnachweis,
abweichende Version, Größe oder bekannte Prüfsumme blockiert.
Der bisherige Fehlerbericht enthält nicht, welche dieser Bedingungen zutrifft.

Die Response des Medienupdates wird nicht als Versionsnachweis verwendet; danach
folgt ein frischer Metadatenabruf. Unveränderte JSON-Snapshots bewahren ihren rohen
Byte-Fingerprint, geänderte Snapshots rekonstruieren die gesendeten UTF8-Bytes.
Zwei neue vollständige Testfälle mit fehlenden v3-ETags, fehlenden Metadaten-SHA-Werten
und auf die ID reduzierten Upload-Antworten **bestehen bereits auf1405**: beide
Manifeste werden geschrieben, der Zweitlauf schreibt nichts. Ein tatsächlich danach
veränderter JSON-Stand blockiert weiterhin. Ergebnis30/30 grün.
Die Vermutung, allein knappe Update-Antworten erklärten den Gerätefehler, ist für
diese Datenwege damit widerlegt. Versionsfortschritt bei gleichem Inhalt bleibt
eine unbewiesene Möglichkeit und wird nicht pauschal zugelassen.

## Eng begrenzte Diagnoseergänzung

- `GoogleDriveClient.withSyncReadProof`: dieselbe Sperrbedingung und derselbe Prüfcode;
  ergänzt feste, whitelisted Dateityp-/Feldpfade, z.B. `documentIndex_version`,
  `receiptMetadata_sha256` oder `unknownFile_fileSizeBytes`. Unbekannte bzw. bösartige
  `entityType`-Werte werden niemals in die Meldung übernommen.
- Vorhandene Felder transportieren ausschließlich numerische erwartete/aktuelle
  Version und Byteanzahlen. Keine Hashwerte, Dateinamen, IDs, Tokens oder Inhalte.
- `DriveSyncTrace`: Textbericht zeigt bei diesem Versionskonflikt dieselben
  numerischen Dateiversionen. Kein neues DTO, keine geänderte Persistenzgrammatik.
- VersionCode1406 unterscheidet den Diagnosekandidaten eindeutig von1405.

Keine Lockerung der Versions-/Hashprüfung; kein neuer Retry, keine Cache-Ausnahme,
kein Überschreiben, kein Löschen, keine Migration. Room34, Auth, Restore, Bank/DATEV,
Navigation, Header, StateFlows und CoroutineScopes unverändert.

Vier zusätzliche vollständige Fehlertests zeigten vor Diagnoseergänzung34 Methoden/
4 Fehler: Die Sperre wirkte bereits, aber Feldpfade/Versionen/Größen fehlten.
Nach Ergänzung34/34 grün, darunter JSON-Inhaltsänderung, reiner Versionsfortschritt
bei gleichen Bytes und Datenschutz bei bösartigem Rollentext. Berichtswiederherstellung,
keine weiteren Schreibzugriffe sowie Erhalt lokaler und aller Remote-Bytes geprüft.
Diese Tests reproduzieren den **Diagnosemangel**, nicht die tatsächliche Ursache
des POCO-Widerspruchs.

Lokale ProduktkompilierungSDK36 und ergänzende externe Robolectric-SDK35-Kopien;
eingechecktes SDK36 unverändert. Vollständige CI/Lint/Build/Emulator für den neuen
Head und APK-Signatur sind separat zu prüfen und im PR zu dokumentieren.
Der lokale Gradle-Start bleibt an FileLockContentionHandler/Wildcard-IP blockiert.

## Nächster erforderlicher Nachweis

Für die konkrete Reparatur fehlt ein tatsächlich fehlgeschlagener Abschlussbericht
mit Dateityp, Feld, erwarteter und aktueller Version/Größe nach einem Schreibvorgang.
Der erfolgreiche1405-No-Op alleine erfüllt die Abnahme des vorher fehlgeschlagenen
Schreiblaufs nicht. Keine fachlichen Nutzerdaten auf Verdacht ändern, keine Dateien
neu erzeugen oder Sicherungen entfernen, um einen Fehler zu erzwingen.

Der Diagnosekandidat wird nicht als vollständige Fehlerbehebung oder als erfolgreich
auf dem realen POCO getestete Reparatur bezeichnet. Kein Merge/Release. Erst nach
nachgewiesener Ursache, gezielter Korrektur und vollständigem realem Schreib- und
unverändertem Wiederholungslauf ist die abschließende Produktabnahme möglich.

## Quellen

- [DriveAPIv2: offizielles Discovery-Dokument](https://github.com/googleapis/google-api-go-client/blob/main/drive/v2/drive-api.json)
  dokumentiert `version` als sämtliche Serveränderungen umfassenden monotonen Zähler;
  `files.get.updateViewedDate` ist standardmäßigfalse, beim Update standardmäßigtrue.
  Daraus wird keine konkrete Versionsänderung dieses Gerätelaufs abgeleitet.
- [HTTP If-Match](https://www.rfc-editor.org/rfc/rfc9110.html#section-13.1.1):
  bedingte Schreibvorgänge und starke Validatoren bleiben unverändert.
