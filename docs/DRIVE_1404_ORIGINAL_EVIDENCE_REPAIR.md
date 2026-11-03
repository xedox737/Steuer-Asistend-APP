# Drive: gemeinsame Originalnachweise bis zum vollständigen Backup

## Ausgangsstand und Abnahmegrenze

Main wurde über GitHub als `5d5e61437fcf7324bb5e0f6442669f777d8ddeda` bestätigt.
Die Reparatur baut auf dem tatsächlichen PR158-Head
`6f4f1f65575fd1ad10d66a9dd78e6d797be08945` auf; damit sind PR156,
PR157, die HTTP-428-Absicherung und die DATEV-Repräsentationserhaltung enthalten.
Der lokale isolierte Branch ist `agent/drive1404-sync-integrity`.
Sein anfänglicher synthetischer Checkout `661893a5826aff8c2df89c8d1c235016915b9a91`
materialisiert denselben Baum wie der GitHub-Head; diese Ersatzhistorie wird nicht gepusht.
Der ursprüngliche Arbeitsbaum war sauber. Direkter Fetch scheitert am Workspace-Proxy.

Der reale Lauf vom 02.11.2026 21:20:12Z mit Version1403 war **nicht erfolgreich**:
22 Belege verarbeitet, danach Dokumentindex mit BACKUP_PREIMAGE_CONFLICT blockiert.
DATEV und weitere Stammdaten waren unverändert; Zusatzsicherung und Abschluss wurden
nicht erreicht. Die bisherige grüne CI beweist keine erfolgreiche Gerätesynchronisation.

Neue Kandidatenversion:1404, unverändertes Paket und dauerhaftes Produktzertifikat.
Freigabe erst nach vollständigem realem POCO-/Drive-Sync und unverändertem Zweitlauf.
Keine Deinstallation, Rücksetzung, Löschung oder automatischer Merge.

## Nachgewiesene gemeinsame Ursache

Codepfad: `ReceiptViewModel` vollständiger Sync →
`DrivePersistenceRepository.validateSynchronizationInventory` → vorhandene Originalnachweise
aus Belegmetadaten/lokalem Bestand und Eigentums-/Byteprüfung → technische Hashübernahme
ohne Änderung des fachlichen `updatedAt` → `updateManagedDocumentIndex` →
`GoogleDriveClient.updateSyncJson` → `DriveBackupConflictGuard.validate`.

Der bisherige Guard kannte den unabhängig geprüften Originalnachweis nicht. Ohne gemeinsame
JSON-Baseline wurde ein leerer alter Hash gegenüber dem korrekt nachgetragenen Hash als
unbekannte Fachänderung behandelt. Dieselbe Zeile steht in `supplementalBackup.managedDocuments`:
eine ausschließliche Dokumentindex-Ausnahme hätte den nächsten Abbruch nur verschoben.

Lesend bezogene echte Sicherungen bestätigen den relevanten Altbestandszustand:
24 eindeutig beleggebundene Dokumente bei22 Belegen,19 alte leere Hashs,
16 Belegmetadatensätze ohne `originalDocuments`. Dokumentindex und Zusatzsicherung stimmen
in ihren gemeinsamen Dokumentfeldern überein. Bei einem Original enthält die unabhängige
Belegmetadatenkopie bereits einen Hash, während beide alten Sicherungszeilen leer sind;
Identität, Dateibindung, Größe und fachlicher Änderungszeitpunkt stimmen überein.
Dies ist ein anderer Beleg als die im Bericht einmalig aktualisierte Metadatenzeile.
Private Inhalte wurden weder veröffentlicht noch in Git/CI übernommen; produktive Drive-Dateien
wurden bei der Untersuchung nicht verändert. Der aktuelle Room-Bestand des Geräts wurde
nicht ausgelesen; diese Quellenprüfung ist kein Live-Ende-zu-Ende-Test.

Zusätzlich prüft ein privater Offline-Replay die eine unabhängig gespeicherte Hashergänzung
auf Kopien beider echten Payloads: ohne Nachweis blockiert der produktive Guard, mit
synthetischer Attestierung akzeptiert er ausschließlich dieselbe Ergänzung (1 Methode,
2 Manifeste, grün). Die Attestierung/Version ist dabei simuliert; eine echte aktuelle
Dateiversion oder ein Live-Upload wird damit ausdrücklich nicht nachgewiesen.

Ein weiterer unabhängiger Fehlertest zeigte denselben gemeinsamen Guard-Fehler bei einer
legitimen Originaländerung H→B, einschließlich Größe40→41: Original, Belegmetadaten und
Belegindex werden korrekt aktualisiert, anschließend blockieren beide Sicherungsmanifeste
bei unverändertem fachlichem Zeitstempel. `syncKnownReceipt` entfernte den ursprünglichen
Änderungsnachweis bereits nach dem Belegindex und damit vor den abhängigen Sicherungen.

## Gezielte Korrektur

- `DriveBackupConflictGuard`: separate OriginalEvidence erlaubt ausschließlich belegte
  Hash-/Größenfelder derselben Dokument-/Objekt-/Einheiten-/Beleg-/Datei-/Ordner-/Namensbindung.
  Andere Felder, entfernte Datensätze, unbekannte Werte und neuere Remote-Versionen bleiben
  geschützt. Sichere Feldnamen verbessern die Konfliktdiagnose ohne private Werte.
- `DriveNamespacePolicy` / `GoogleDriveClient`: eigenständiger account-/laufgebundener
  Nachweis, getrennt vom gewöhnlichen Fingerprint-Cache. Vor abweichenden Sicherungsfeldern
  erfolgt eine frische Prüfung von Originalversion, Hash, Größe und Bindung. Eigene
  Originalmutationen invalidieren den alten Nachweis. Ein bloßer Server-Hash genügt nicht.
- `DrivePendingWrites`: eigener atomarer LOCAL_ONLY-Nachweis für legitim geprüfte
  Originaländerungen. Gespeichert werden Hashs, Größen und ein Digest der stabilen Bindung,
  keine Originalbytes, lokalen Pfade oder Zugangsdaten. Alte reine Hash-Intents werden
  nicht zu dieser Autorität aufgewertet. Exakte aufeinanderfolgende Übergänge können
  über Teilabbrüche verbunden werden; unbekannte positive Größen bleiben gesperrt.
- `DrivePersistenceRepository`: registriert Nachweise erst nach unabhängiger Prüfung und
  stellt sie nach Neustart wieder her. Belegindex-Acknowledgement und abhängige
  Sicherungsnachweise haben getrennte Lebensdauern. Auch der vorhandene geprüfte
  Standalone-No-Op registriert seinen unabhängigen Nachweis vor der Indexsicherung.
- `ReceiptViewModel`: entfernt erledigte technische Übergangsnachweise erst nach Erfolg
  sämtlicher Sicherungen; keine zusätzliche Coroutine, kein neuer Sync-Start, keine UI-
  oder StateFlow-Änderung. Fehler und Abbrüche erhalten die Wiederaufnahmedaten.

Starke If-Match-Prüfungen, Versionsbindung des v2-Fallbacks bei fehlendem v3-ETag,
Namespace-/Eigentumsprüfung und generische Konfliktregeln bleiben wirksam.
Keine Migration; Room34, Auth, Restore, Originalidentitäten, Bankbuchungsliste,
DATEV-Export, Navigation, Header und Bottom Navigation bleiben erhalten.

## Reproduktion und Regression

Die unabhängige `DriveDocumentIndexRegressionTest` nutzt echte produktive ViewModel-,
Repository-, Room-, Serialisierungs- und HTTP-Pfade mit vollständig synthetischem Bestand.
Vor Korrektur scheiterten zwei von neun Methoden an der falschen Hash-Sperre bzw.
Wiederaufnahme. Weitere vorab rote Tests belegen die eigene Originaländerung und den
verlorenen Dokumentindex-Acknowledge; ein eigener roter Test erfasst Größe0 als historischen
Unbekannt-Marker bei exakt belegtem früherem Hash. Ein weiterer roter Test wies nach,
dass stabile nichtnumerische Originalversionen zuvor sogar vollständigen Erfolg und
lokale Hashübernahme erlaubten. Gültige positive numerische Drive-Versionen werden
jetzt schon vor einer Baseline-Übernahme verlangt; fehlende/leere/ungültige Werte
erhalten die alten lokalen Daten und beide Remote-Manifeste.

Prüffälle umfassen vollständige Sicherung bis Abschluss und schreibfreien Zweitlauf,
gemischten Altbestand mit24 Dokumenten/19 fehlenden Hashs/16 älteren Metadaten,
Metadatenänderung, zweimaligen Restore, unterbrochene Indexantwort, getrennte Account-
Nachweise, fremde/neue Felder, echte neuere Daten, nicht belegte Hash-Ersetzung,
geänderte Originalversion/Bindung, eigene H→B→C-Übergänge sowie externe Originaländerungen.
IDs, vollständige Inhalte, Originalbytes, Datensatzanzahl und ausbleibende Dubletten
werden überprüft. Bestehende Phase11E- und DATEV-Tests bleiben unverändert aktiv.

Endgültiger lokaler Stand:24 neue +84 bestehende Phase11E +12 DATEV-Methoden,
null Fehler/Skips;16 Python-Prüfungen und Diff-Prüfung bestanden.
[Rot-/Grün-Zähler, tatsächliche Methodennamen und Quellhash](drive-1404-evidence/local-regression.json).

Lokale SDK36-Produktkompilierung erfolgt mit den vorhandenen Projektabhängigkeiten.
Ergänzende lokale Robolectric-Ausführung verwendet externe SDK35-Kopien; eingecheckte
SDK36-Deklarationen werden nicht verändert. Der vollständige Gradle-Start ist vor
Projektaufbau durch FileLockContentionHandler / fehlende verwendbare Wildcard-IP blockiert.
Deshalb sind die vollständigen unveränderten Unit-, Lint-, Build- und tatsächlichen
Emulatorprüfungen auf GitHub Actions zwingend. Aktuelle Run-IDs, Methodenanzahlen und
Artefaktnachweise werden für den veröffentlichten Head im PR dokumentiert.

## Performance und Quellen

Der reale Fehlerlauf:396 Aufrufe/127s, davon Initialisierung41/12,975s,
Bestandsprüfung126/35,397s, Ordner62/18,536s, eine Metadaten-/Indexänderung88/30,307s,
Dokumentindex51/17,668s. Stammdaten22/9,320s. Unterphasen sind bereits in ihren
Eltern enthalten und dürfen nicht zusätzlich summiert werden. Keine HTTP-Retries.
Insbesondere31 list-Aufrufe je Metadaten-/Belegindexmutation und wiederholte
Eigentums-/Vorfahrprüfungen bleiben ein gemessener Kostenpunkt.

Die Reparatur prüft zusätzlich nur Originale, deren Manifest-Hash/Größe tatsächlich
abweicht. Sie führt keinen allgemeinen Cache, Changes-Feed oder Architekturumbau ein.
Eine Beschleunigung des echten Gerätelaufs wird ohne Messung nicht behauptet.

- [Android: Offline-first und Konfliktbehandlung](https://developer.android.com/topic/architecture/data-layer/offline-first#conflict-resolution)
- [HTTP: starkes If-Match gegen verlorene Updates](https://www.rfc-editor.org/rfc/rfc9110.html#section-13.1.1)
- [Drive: Inhaltsübertragungen](https://developers.google.com/workspace/drive/api/guides/manage-uploads)
- [Drive: Performance und gezielte Felder](https://developers.google.com/workspace/drive/api/guides/performance)
- [Drive: Änderungsnachverfolgung](https://developers.google.com/workspace/drive/api/guides/manage-changes)

Der GH Review Loop benötigt eine authentifizierte gh-CLI. `gh` ist vorhanden, der
GH_TOKEN ist ungültig; GitHub-Zugriffe, CI-Auswertung und der ausgewählte Codex-Review
erfolgen mit ausdrücklicher Nutzerautorisierung separat über den verbundenen GitHub-Zugang.
Ein erfolgreicher Skriptlauf oder ein erfolgreicher Live-Drive-Test wird nicht behauptet.
