# Phase11E – realer Sicherheitsabbruch und genaue Konflikt-Diagnose

## Nachgewiesener Gerätebefund

Testkandidat 1400 auf dem POCO: 23 lokale Belege, laut Nutzer ausschließlich ein neuer Beleg seit der erfolgreichen Wiederherstellung. Manueller Lauf 2026-11-01: **39,5 Sekunden, blockiert**, 129 API-Versuche, null create/update, null geschriebene Body-Bytes, alle 23 Belegabgleiche nicht versucht. Abbruch in Bestandsprüfung / Beleg-Metadaten innerhalb des ersten Dreier-Lesebatches. Zwei Metadatenprüfungen waren bereits erfolgreich; das ist kein abgeschlossener Belegabgleich. Anmeldung und vollständiger Sicherungszeitpunkt wurden nicht als neu erfolgreich ausgegeben.

Dieser Lauf ist **kein erfolgreicher Synchronisierungs- oder No-Op-Benchmark**. Praktische Abnahme bleibt OFFEN. Keine Aussage, dass die Belege fehlen oder beschädigt sind.

Der Bericht von 1400 enthielt bei verschiedenen lokalen Prüfungen synthetisch HTTP 412 und den Text „Drive-Bestand wurde zwischenzeitlich geändert“. Er unterscheidet deshalb nicht, ob ein tatsächlicher Serverstatus, ein fehlender Vergleichswert, eine lokale/remote Revisionsabweichung oder ein fehlgeschlagener Dateinachweis vorliegt. Eine tatsächliche Änderung durch ein anderes Gerät ist damit nicht bewiesen.

## Ausschließlich lesende echte Prüfung

Über die vorhandene Drive-Verbindung wurde die Übereinstimmung mit dem zuvor gezeigten App-Konto geprüft. Der produktive Root-/Systemordner und die Dateibindungen wurden anhand der vorhandenen Konfiguration sowie der Indexreferenz geerdet. Keine Datei angelegt, geändert, verschoben oder gelöscht; keine produktiven Inhalte als Testfixture übernommen.

Für den betroffenen Altbestand wurde lediglich technisch festgestellt:

- Metadaten im Legacy-Schema 1; kein `originalDocuments`-Hashnachweis und kein anderer Original-Hash in diesen Metadaten.
- Auch der Dokumentindex enthält für diese Referenz keinen Hash.
- Das Original ist erreichbar, hat positive Größe und passt hinsichtlich MIME-Typ und Ordnerbindung.
- Die betroffene JSON-Datei hat kein UTF-8-BOM.

Der lokale Revisions- und Hashzustand des Handys ist damit noch nicht ausgelesen. Ein nachträglicher lokaler Hash durch einen Original-Download sowie ein abweichender lokaler Revisionsstand bleiben möglich. Der konkrete lokal ausgelöste Prüfschritt ist daher **noch nicht abschließend nachgewiesen**. Legacy-Format alleine autorisiert kein blindes Überschreiben oder automatisches Lernen eines unbekannten Vergleichsstands.

## Gezielte Änderungen für Diagnosekandidat 1401

- Lokale Konflikte tragen einen typisierten Prüfcode. Die bisherige interne Konfliktnummer bleibt für bestehende Fehlerbehandlung kompatibel; im Benutzerbericht erscheint dafür ausdrücklich **kein behaupteter HTTP-Status**. Echte HTTP-412-Antworten bleiben separat als HTTP 412 erkennbar.
- Die Revisionsprüfung nennt lokale und Drive-Revision sowie ausschließlich abweichende **kompilierte Feldnamen**. Keine Feldwerte, Konten, Dateipfade, URLs, Tokens oder Dokumentinhalte.
- Unter anderem getrennte Codes: `RECEIPT_REVISION_MISMATCH`, `ORIGINAL_BASELINE_MISSING`, `ORIGINAL_CHECKSUM_MISMATCH`, `ORIGINAL_SIZE_INVALID`, `JSON_CHECKSUM_MISMATCH`, `JSON_SIZE_MISMATCH`, `JSON_VERSION_MISSING`, `JSON_VERSION_MISMATCH`, `JSON_ENCODING_INVALID`, `INDEX_BINDING_MISMATCH`, `INDEX_RECEIPT_NOT_LOCAL` und `BACKUP_PREIMAGE_CONFLICT`.
- Codes und technische Angaben bleiben im gespeicherten, kopierbaren Bericht nach Neustart erhalten. Bisherige Berichte ohne Zusatzfelder bleiben lesbar. Der allgemeine Konflikttext behauptet keine zwischenzeitliche Cloud-Änderung mehr.
- Die Zähler heißen „abgeglichen“: vorgelagerte Metadatenprüfung und tatsächlicher Belegabgleich werden sprachlich unterschieden.
- **Keine Schutzprüfung entfernt oder gelockert**, keine automatische Wiederherstellung, Revisionserhöhung, ID-Ersetzung oder Hash-Neuanlage bei fehlender Baseline. Die zwei historischen 404-Verweise bleiben unverändert. Auto-Backup bleibt ausgeschaltet.

## Zusätzlich reproduzierte, behobene Vergleichsfehler

Diese Fehler sind unabhängig mit frisch erzeugten synthetischen Daten nachgewiesen; sie werden **nicht** als bestätigte Ursache des betroffenen POCO-Datensatzes ausgegeben.

1. Eine gültige UTF-8-JSON mit BOM scheiterte vorher bereits nach zwei Metadaten-GETs: `ResponseBody.string()` entfernt beim Dekodieren die BOM, anschließend wurde der neu kodierte Text mit SHA/Größe der ursprünglichen Datei verglichen. Jetzt werden SHA/Größe und der Abschlussnachweis gegen die **originalen Bytes** geprüft. Die vorhandene BOM-/Charset-Semantik bleibt erhalten. Ungültige Kodierungen werden streng abgelehnt statt still ersetzt. Tests enthalten UTF-8-BOM, UTF-16 LE/BE, Unicode und ungültiges UTF-8 sowie einen identischen zweiten Lauf ohne Original-Neuübertragung.
2. Ein gespeicherter SHA-256 in Großbuchstaben wurde in der Vorprüfung trotz identischen Digest-Bits als geändert behandelt. Der reproduzierende Test schlug vor der Korrektur mit `ORIGINAL_CHECKSUM_MISMATCH` fehl. Hex-Digests werden nun beim Server-Fingerprint normalisiert bzw. ohne Unterschied zwischen Groß-/Kleinschreibung verglichen. Auch die Wiederaufnahme nach verlorener Medien-Antwort berücksichtigt dieselbe Gleichheit. Andere Digest-Bits blockieren weiterhin; keine Blindwiederholung von Uploads.

## Nachweise und weiterer Gerätetest

Vor der jeweiligen Korrektur tatsächlich rote Tests: fehlender Legacy-Hash wurde fälschlich als HTTP 412 bezeichnet; gültige BOM-JSON wurde gesperrt; identischer Hex-Digest mit anderer Schreibweise wurde gesperrt. Danach: **50 gezielte Tests bestanden**, echte Kotlin-/Compose-Kompilierung und Room-KSP; keine Schemaänderung. Ergänzende lokale Ausführung weiterhin SDK35-Cache, eingecheckte Tests SDK36. Die bestehenden Tests zu keinem Cloud-Overwrite bei lokal fehlenden Belegen behalten sämtliche Datensicherheits-Assertions und verlangen zusätzlich den richtigen lokalen Prüfcode statt eines vorgetäuschten HTTP-Status.

Der neue CI-Lauf mit Head/Run-ID und dessen tatsächliche Ergebnisse gehören in PR #156; vorherige grüne Läufe sind kein Nachweis für das neue APK. Kein Merge oder öffentliches APK-Release. Version 1401 verwendet unverändert Original-Package, OAuth und Produktzertifikat.

Nach erfolgreicher Build-/Identitätsprüfung: Update über 1400 installieren, nicht deinstallieren. Auto-Backup aus lassen. **Einen** manuellen Lauf ausführen und den neuen vollständigen Bericht mit `Prüfcode`, lokalen/Drive-Revisionen und Feldnamen weitergeben. Bei erneuter Sperre nicht durch Bereinigung, wiederholtes Restore oder Ersetzen von Dateien umgehen. Erst anhand des konkreten Prüfcodes wird eine gegebenenfalls notwendige Legacy-Behandlung festgelegt. Realer Erfolg und Performance-Abnahme weiterhin **OFFEN**.


## Bestätigter Folgeauftrag nach 1401

Der neue reale Lauf nennt ausdrücklich `ORIGINAL_BASELINE_MISSING` (42,3 Sekunden, 129 API-Aufrufe, null geschriebene Bytes). Damit ist die vorher offene lokale Prüfstelle bestimmt. Der kleinste nachweisbare Korrekturpfad und seine weiterhin gesperrten unbekannten Fälle stehen in [PHASE11E_BASELINE_RECOVERY.md](PHASE11E_BASELINE_RECOVERY.md). Die oben beschriebenen Einschränkungen beziehen sich auf Kandidat 1401; Kandidat 1402 ergänzt ausschließlich unabhängig belegte fehlende Vergleichswerte. Praktische Abnahme bleibt offen.
