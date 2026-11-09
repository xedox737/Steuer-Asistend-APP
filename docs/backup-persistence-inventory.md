# Persistenz- und Backup-Inventar

Quellcodeabgleich für die lokale Vollsicherung: `302b007ad510c44fb596a0081197490d3430374e`
(main, 09.11.2026). Room: 34; bestehendes lokales Archiv: V1; Zusatzbackup: 14.
Drive-AppConfig und Beleg-Metadaten behalten Schema 1.

Die bisherigen Tabellen unten beschreiben überwiegend die app-eigene Drive-Sicherung.
Der lokale Export-/Importweg und seine noch offenen Lücken stehen im folgenden
Abschnitt „Lokaler Archivweg“. Dieses Inventar belegt noch keine vollständige
oder konsistente neue Vollsicherung.

`BACKUP` sind fachliche Daten, `LOCAL_ONLY` geräte-/operationsgebundene Daten,
`SECRET` Zugangsdaten, `CACHE` rekonstruierbare Daten. Die Laufzeitliste für
Preferences ist `PersistentPreferenceInventory`; ihr Quelltest schlägt bei
neuen, nicht klassifizierten `getSharedPreferences`-Aufrufen fehl.

## SharedPreferences

| Datenquelle | Inhalt | Klasse / Sicherung |
|---|---|---|
| rent_plan_prefs | Nebenkosten und sonstige Mietbeträge, stabile Objekt-/Einheitenschlüssel und Altdaten | BACKUP |
| tenant_history_prefs | Mietperioden und Miethistorie | BACKUP |
| loan_interest_assignments | Zuordnung von Belegen zu Darlehen | BACKUP |
| afa_confirmed_values_prefs | Bestätigte AfA-Werte je Objekt, Stichtag und fachliche Herkunft | BACKUP |
| renovation_review_prefs | Sanierungsmaßnahmen, Belegbeziehungen und Prüfstand | BACKUP |
| annual_tax_approval_prefs | Jahresprüfung, Freigabe-Fingerprint und Zeitpunkt | BACKUP |
| wohneinheiten_prefs | Einheiten, Status, Mieter, Flächen, Mieten und stabile IDs | BACKUP |
| datev_kanzleiprofil_prefs | Bestehendes DatevProfile mit Kontenrahmen, Berater/Mandant und Mapping | BACKUP: nur active_profile_json |
| property_tasks_prefs | Aufgabenlisten je propertyId, taskId und unitId | BACKUP |
| unit_status_meta_prefs | Status und Wirksamkeitsdatum je Objekt/Einheit | BACKUP |
| unit_rental_detail_prefs | Zahlungsart, Fälligkeit, Kaution und Zimmer je Objekt/Einheit | BACKUP |
| bank_transaction_notes | Notizen unter unveränderter transactionId | BACKUP |
| ki_learned_rules_prefs | Gelernte fachliche Vendor-Regeln | BACKUP (zusätzlich zum bisherigen Stammdatenpfad) |
| logbook_drafts | Lokale Fahrtenentwürfe und Wiederholkennungen | BACKUP |
| ai_provider_settings | Anbieterauswahl und Modell; verschlüsselte API-Schlüssel und IVs | BACKUP: ausschließlich receipt_analysis_provider und openai_model; alle Credentials SECRET |
| google_drive_prefs | Gewähltes Objekt, automatische Sicherung; Verbindung und Token | BACKUP: ausschließlich selected_property_id und auto_backup; Token SECRET; Anmeldung/Verbindungsstatus LOCAL_ONLY |
| category_folder_mappings_prefs | Drive-Ordnerauflösung der aktuellen Verbindung | CACHE, auf Drive bereits in category-folder-mappings.json; beim Auflösen wieder gelesen |
| app_installation_prefs | Kennung der lokalen Installation | LOCAL_ONLY |
| restore_journal_prefs | Zustand einer lokalen Wiederherstellungsoperation | LOCAL_ONLY |
| duplicate_cleanup_journals | Fortsetzbare, explizit autorisierte Bereinigungsoperationen | LOCAL_ONLY, keine alten Löschoperationen auf Neuinstallation übertragen |
| metadata_duplicate_cleanup_audit | Lokales technisches Bereinigungsjournal | LOCAL_ONLY |

Gemischte Speicher verwenden eine Positivliste; keine Komplettkopie.
Zusätzlich werden erkennbare Credential-Schlüssel auch in fachlichen Speichern
auf Export und Import abgewiesen. Unbekannte Top-Level-Preference-Felder werden
nicht importiert. Fehlende Felder alter Backups ändern keinen lokalen Speicher.
Restore schreibt vorhandene stabile Schlüssel, ohne das gesamte Preference-File
zu leeren. String, Int, Long, Float, Boolean und StringSet bleiben typgetreu.

## Room (alle 20 registrierten Entities)

| Datenquelle | Inhalt | Klasse / Sicherung |
|---|---|---|
| receipts | Belegstammdaten, Positionen, Splits, Prüfung, Zuordnungen und Exportstatus | BACKUP, bestehende Drive-Belegmetadaten + Index |
| property_metadata | Immobilien, Kaufdaten, Flächen und AfA/Restnutzungsdauer | BACKUP, Stammdaten und alle Objekte im Zusatzbackup |
| loans | Darlehen und Objektzuordnung | BACKUP, Zusatzbackup |
| receipt_entities | Projektion von Receipt für Bestandsfunktionen | CACHE, beim Receipt-Restore durch Repository wieder erzeugt |
| belege | Belegprojektion für Bestandsfunktionen | CACHE, beim Receipt-Restore wieder erzeugt |
| export_audit_runs | Exportprotokolle, stabile Belegkennungen, Zeitraum und Prüfsumme | BACKUP, neu im Zusatzbackup |
| receipt_documents | Dokumentreferenzen eines Belegs | BACKUP, bestehende Belegmetadaten |
| logbook_trips | Fahrten, Quelle, Zuordnung, Wiederholkennung, Korrektur-/Stornohistorie | BACKUP, Zusatzbackup |
| standard_routes | Gespeicherte Strecken und Routensignaturen | BACKUP, Zusatzbackup |
| managed_documents | Dokumentmetadaten, stabile Zuordnungen, Prüfentscheidungen und Drive-Referenzen | BACKUP, Zusatzbackup und Dokumentindex; lokale Pfade nicht exportiert |
| document_search_fts | Suchindex, inkl. abgeleiteter OCR-Suche | CACHE, aus managed_documents neu aufgebaut; gespeicherter OCR-Text bleibt fachlicher Bestand |
| document_migration_journal | Lokaler technischer Dokumentmigrationsverlauf | LOCAL_ONLY |
| bank_accounts | Kontodaten ohne Zugangsdaten | BACKUP, Zusatzbackup |
| bank_transactions | Buchungen, Klassifikation und Bearbeitungsstand | BACKUP, Zusatzbackup |
| bank_receipt_links | Bestätigte Bank-/Belegverknüpfung | BACKUP, Zusatzbackup; receiptInternalId zur Auflösung |
| bank_learning_rules | Fachliche Bankregeln | BACKUP, Zusatzbackup |
| bank_rule_evidence | Regelbestätigungen | BACKUP, Zusatzbackup |
| bank_rent_assignments | Mietzuordnung je Objekt/Einheit/Monat | BACKUP, Zusatzbackup |
| bank_loan_assignments | Darlehenszuordnungen und Splits | BACKUP, Zusatzbackup |
| bank_recurring_patterns | Wiederkehrende Zahlungsvorschläge | BACKUP, Zusatzbackup |

## Dateien und Konfiguration

| Datenquelle | Inhalt | Klasse / Sicherung |
|---|---|---|
| receipt_page_* und referenzierte Belegoriginale | Scanbilder, PDFs und Originaldateien | BACKUP, vorhandener Belegupload; Fehlschlag zählt jetzt als unvollständige Sicherung |
| filesDir/managed_documents | Importierte/gescannte Objekt- und Einheitsdokumente | BACKUP, vorhandener Dokumentupload + Dokumentindex; Fehlschlag verhindert Erfolgsmeldung |
| filesDir/offline-backup-staging | Vor Zieldialog erstelltes und geprüftes V1-Archiv | LOCAL_ONLY, nach Zielkopie, Fehler oder Dialogabbruch entfernen; noch kein prozessfester Coordinator |
| noBackupFilesDir/drive-pending-writes | Drive-Schreibabsichten, Bestätigungen und Hash-Vorstände, keine Originalbytes | LOCAL_ONLY; auf ein anderes Gerät weder übertragen noch als auszuführende Aufträge wiederherstellen |
| filesDir/restored_receipts, filesDir/receipt_documents, filesDir/offline-originals | Wiederhergestellte Originale und zusätzliche Beleganhänge | BACKUP über persistierte Dateireferenzen; unreferenzierte Dateien nicht still als vollständig erfasst ausgeben |
| filesDir/property-images | Objektbilder | BACKUP, neu als Bytes im Zusatzbackup; Restore unter objektbezogenem SHA-256-Dateiname statt fremdem Dateipfad |
| cacheDir (DATEV-CSV/ZIP) | Noch vorhandene Exportartefakte aus DatevCsvOutput und DatevExporter | Fachlich aufbewahrte Exporte separat inventarisieren; aktuell im lokalen V1 nicht erfasst |
| cacheDir/exports, cacheDir/reports, PdfExporter, AdvisorPackageBuilder | Gespeicherte Export-/Bericht-/Beraterpakete | Aufbewahrte Bytes fachlich inventarisieren; V1 erfasst sie nur bei einer vorhandenen fachlichen Dateireferenz |
| cacheDir (Vorschauen, Ersatzdateien) | Rekonstruierbare temporäre Darstellungen | CACHE nur soweit unreferenziert; referenzierte Originale und aufbewahrte Exporte sind keine Cache-Ausnahme |
| AndroidKeyStore | Geräteschlüssel für API-Credentials | SECRET, niemals exportieren |
| .env / BuildConfig-Platzhalter | Entwicklungs-/Build-Konfiguration | LOCAL_ONLY / SECRET, kein Drive-Backup |
| firebase-applet-config.json | Unreferenzierte Applet-Konfiguration, kein Android-google-services.json | Unbenutzt, aus Git entfernt; vorhandene Android-Firebase-Integration bleibt erhalten |
| ReceiptCategories und weitere Codekonstanten | Mit der App ausgelieferte Fachkonfiguration | Kein Nutzerspeicher; durch App-Installation verfügbar |

## Restore-Sicherheitsgrenzen

- Der Standardrestore verwendet MERGE. REPLACE_FULL bleibt ausdrücklich verfügbar.
- Room-Import bleibt transaktional; kein Schemawechsel und keine destructive Migration.
- Zusatzbackup validiert Schema und Preference-Typen vor dem Room-Import.
- Foto-Dateinamen stammen aus stabiler Objektkennung und Inhalt; identische Bilder verschiedener Objekte bleiben getrennt. Staging-Dateien werden atomar umbenannt.
- Fehlende Fotooriginale lassen das Backup fehlschlagen statt eine vollständige Sicherung vorzutäuschen.
- Restore ordnet Immobilien nach stabiler propertyId zu; kollidierende numerische Room-IDs fremder Objekte werden nicht überschrieben.
- Das Core-Restore erhält ein noch vorhandenes lokales Objektbild bis zum Zusatzrestore.
- Preferences werden erst nach dem erfolgreichen Room-Commit dauerhaft geschrieben.
- Wiederholter Restore erzeugt keine neuen Objekt-, Aufgaben-, Regel-, Notiz- oder Audit-IDs.

## Grenzen

- Echte Anmeldung und Neuinstallation auf einem physischen Gerät mit Google Drive benötigen einen separaten Integrationstest. Die automatisierten Tests prüfen serialisierte Payloads, frische Room-Datenbanken und geleerte Preferences.
- Objektbilder vergrößern das Zusatz-JSON durch Base64. Bei großen Portfolios ist ein eigener Drive-Bildtransport eine spätere Optimierung.
- Room und mehrere Preference-Dateien bilden keine gemeinsame atomare Transaktion. Ein Dateisystem-/Commit-Fehler wird gemeldet; ein erneuter Restore ist möglich.
- Gerätegebundene Journale werden nicht auf ein anderes Gerät übertragen. Gespeicherter OCR-Volltext, KI-Ergebnisse und Nutzerkorrekturen in managed_documents sind fachliche Daten; nur der daraus abgeleitete FTS-Index ist rekonstruierbar. Absolute Quellpfade sind keine portablen Zielreferenzen.


## Lokaler Archivweg (Quellcode, noch keine neue Implementierung)

`OfflineBackupService.create` liest alle SQL-Tabellen außer SQLite-/Room-internen
Tabellen und `document_search_fts*`. `inspect` prüft die Tabellen und Spalten gegen
die aktuelle Datenbank. `restore` importiert die Zeilen transaktional und baut FTS
über `DocumentSearchTextBuilder` neu auf. Die vorhandene Datenbank registriert
20 Entities: 19 reguläre Tabellen und einen virtuellen FTS-Index.

| Quellen | Lokaler Export / Import | Originalzuordnung | Vorhandener Nachweis / offene Abnahme |
|---|---|---|---|
| receipts | create / restore, komplette SQL-Zeilen | imageUrl, stabile internalId; managed_documents.receiptInternalId | OfflineBackupServiceTest, OfflineBackupRelationTest; weitere Fehler-/Snapshotfälle offen |
| property_metadata | create / restore | bildPfad, propertyId | OfflineBackupServiceTest, OfflineBackupRelationTest |
| receipt_entities, belege | create / restore, auch diese Projektionen werden lokal gespeichert | pdfPath bzw. bildPfad, sofern vorhanden | Lokaler V1 behandelt sie nicht als ausgelassene Caches; vollständiger Zeilenvergleich offen |
| receipt_documents | create / restore | driveFileId; zugehöriges lokales Original muss auflösbar sein | OfflineBackupServiceTest; zusätzliches Original ohne lokalen Pfad darf nicht still fehlen |
| managed_documents | create / restore, einschließlich ocrText und extractedFieldsJson | localUri, documentId, sha256, receiptInternalId | OfflineBackupServiceTest, OfflineBackupRelationTest; OCR-/Korrekturvergleich ergänzen |
| loans | create / restore | keine eigene Dateispalte; Belegzuordnung separat | OfflineBackupRelationTest |
| logbook_trips, standard_routes | create / restore | Beleg-IDs bei Fahrten | OfflineBackupRelationTest; Entwurfsdaten zusätzlich in logbook_drafts |
| export_audit_runs | create / restore | zipFileName ist keine portable Dateizuordnung | PreferenceBackupCompletenessTest prüft Historie über Zusatzbackup; Exportbytes fehlen im lokalen V1 |
| bank_accounts, bank_transactions, bank_receipt_links | create / restore | stabile Konten-/Transaktions-/Beleg-IDs | OfflineBackupRelationTest; vollständiger Feldvergleich ergänzen |
| bank_rent_assignments, bank_loan_assignments | create / restore | Objekt-/Einheiten-/Darlehens-IDs | OfflineBackupRelationTest |
| bank_learning_rules, bank_rule_evidence, bank_recurring_patterns | create / restore | fachliche Regel- und Transaktionsreferenzen | Vollständigen lokalen Roundtrip ergänzen; keine Cache-Ausnahme |
| document_migration_journal | V1 exportiert/importiert die Tabelle ebenfalls | technischer Verfahrenszustand | Für neuen Vertrag ausschließen bzw. neutralisieren; keine alten Migrationsaufträge starten |
| alle 16 BACKUP-Preference-Stores | writePayload / validatePayload / restorePayload | unveränderte fachliche Schlüssel; gemischte Stores nur Positivliste | PreferenceBackupCompletenessTest; PersistentStorageInventoryTest erkennt unbekannte SharedPreferences-Aufrufe |
| Originaldateien | files/N mit SHA-256 und Größe / filesDir/offline-originals | V1-Pfadabbildung; pathColumns = imageUrl, pdfPath, bildPfad, localUri | OfflineBackupServiceTest; Content-URIs und zusätzliche Dateiquellen noch nicht vollständig gelöst |

### Geklärte APK-Hinweise und Sicherheitsgrenzen

- Im App-Quellcode kein DataStore-Eigentümer und kein Aufruf von
  `preferencesDataStore`/`DataStoreFactory` gefunden. Die Gradle-Abhängigkeit allein
  belegt keinen eigenen Store `preferences_pb`. Bei späterem Einführen muss das
  Inventar ausdrücklich erweitert werden.
- `settings` ist im Quellcode eine UI-Seite, kein eigener Preference-Store.
  `safety_settings` ist kein gefundener eigener App-Store.
- `app_installation_prefs` erzeugt die lokale Installationskennung in
  `DrivePersistenceRepository`; auf dem Zielgerät neu erzeugen, nicht übertragen.
- Android-Systembackup bleibt bewusst deaktiviert (`allowBackup=false`).
  `backup_rules.xml` und `data_extraction_rules.xml` enthalten nur Vorlagen und
  Kommentare, keine aktive Include-/Exclude-Auswahl. OEM-Geräteübertragung daraus
  nicht als geprüft ableiten. Die app-eigene Drive-Sicherung bleibt gesondert.
- `AiProviderSettings` verwendet AndroidKeyStore und AES/GCM direkt. Eine nutzbare
  Tink-Streaming-Compile-API ist durch den APK-Befund nicht bewiesen; im
  Versionskatalog steht keine direkte Tink-/Security-Crypto-Abhängigkeit.
- Der bestehende `RestoreJournal` hält Phasen und Zähler in Preferences fest,
  aber keinen dauerhaften Rückfallbestand für den lokalen Restore. Wiederverwendung
  setzt einen nachgewiesenen Startup-/Rollback-Vertrag voraus.

### Noch offene Voraussetzungen des Codingplans

Die Inventar-Abdeckungstests prüfen SharedPreferences, die tatsächlichen Room-Tabellen
und die im Quellcode angelegten `filesDir`-/`noBackupFilesDir`-Dateiwurzeln. Neue DataStore-Erzeuger oder
anders aufgebaute Dateiablagen verlangen eine explizite Erweiterung der Klassifikation.
Dies belegt die Quellenzuordnung, noch nicht den vollständigen fachlichen Roundtrip
oder die Konsistenz der späteren Engine. Noch vorhandene Exportartefakte im Cache
bleiben ein expliziter Umsetzungspunkt; kein flächiges Cache-Backup.

Vor produktiven Kernänderungen bleiben der Archiv-/Kryptovertrag und die komplette
Writer-Matrix festzulegen. Direkte Preference-Writer liegen unter anderem in
`PropertyUnitScopedData.setRentValues`, `PropertyTaskStore.save`,
`ConfirmedAfaValuesStore.write`, `RenovationReviewStore.saveMeasure/saveRelation`,
`ReceiptViewModel`, `LoanFeature` und `BankFeature`; daneben schreiben Repository,
DAOs und Drive-Restore. `ReceiptOriginalStorage.importOriginal` sowie
`ManagedDocumentService` erzeugen Originaldateien. Diese Aufzählung ist ein
belegter Einstieg, keine vollständige Writer-Matrix und kein Konsistenznachweis.
Ein Backup-Mutex allein darf nicht als deren Ersatz gelten.

Optionale Teilsicherung und automatische Aufbewahrung sind nicht beauftragt;
Aufbewahrung und Credential-Transport bleiben standardmäßig aus. Das spätere
CI-Profil erfordert laut Codingplan vor einem Implementierungs-Push ausdrückliche
Freigabe; bis dahin keinen Push mit sachfremden Vollsuiten auslösen.


## Festgelegter Umsetzungsvertrag für die folgenden Schritte

Diese Festlegungen definieren das Ziel; sie beschreiben ausdrücklich noch nicht
bereits implementierte Eigenschaften des vorhandenen V1-Service.

- Neue Archive: Format V2, Endung `.immopilot`, vollständig verschlüsselter
  ZIP-Datenstrom mit Tink AES256-GCM-HKDF-Streaming (1-MiB-Segmente), authentifizierter
  letzter Segmentkennung. Tink als direkte Compile-Abhängigkeit mit geprüftem
  Dependency-Graph deklarieren; konkrete verfügbare Version vor Schritt 5 prüfen.
  Kein zweites alternatives Archiv-Kryptoverfahren.
- Envelope: zufälliger 256-Bit-MK pro Schlüsselgeneration, zufälliger DEK pro
  Archiv; Umhüllung mit AES-256-GCM. Recovery-Umhüllung des MK über
  PBKDF2-HMAC-SHA256, zufälligem Salt von mindestens 16 Bytes und 600.000 Iterationen.
  Erlaubter Importbereich: 600.000 bis 2.000.000 Iterationen, genau 32 abgeleitete
  Bytes, Header höchstens 64 KiB; sonst vor Ableitung ablehnen. Für API 24/25 eine
  geprüfte bibliotheksgestützte PBKDF2-Implementierung verwenden, keine eigene KDF.
  Die konkrete Bibliotheks-API ist vor Schritt 5 zu verifizieren.
- Einrichtung: mindestens 16 Zeichen, Passwortbestätigung und erfolgreicher
  Testarchiv-Roundtrip. Passwort nicht speichern; Automatik erhält ausschließlich
  die Keystore-Umhüllung. Passwortwechsel erzeugt atomar einen unabhängigen neuen
  MK. Format, Generation und KDF-Parameter in authentifizierte Kontextdaten binden.
  Alte Archive benötigen das alte Passwort. Credentials ausschließlich im
  authentifiziert verschlüsselten Abschnitt und nie unverschlüsselt im Staging.
- Snapshot: gemeinsames Writer-Gate für Room, fachliche Preferences und Dateien;
  Room-Lesetransaktion plus Preference-Erfassung und geprüfte Originalkopien in
  privatem `filesDir` innerhalb der Erfassungsphase. Erst danach Gate freigeben.
  Archivierung und Zielkopie lesen nur dieses fixierte Staging. Ohne vollständige
  Writer-Matrix und den Nachweis jedes Übergabepunkts bleibt Schritt 4 gesperrt.
- Restore: validierter Zeilenimport bei unverändertem Schema, kein roher Tausch
  einer offenen Datenbank. Rückfallbestand für DB, fachliche Preferences und
  Dateizuordnungen vor Anwendung dauerhaft sichern; Phasenjournal atomar schreiben.
  App-Start prüft das Journal vor fachlicher Initialisierung. Bei unvollständigem
  Restore Rückfall vollständig herstellen; Wartungsmodus sperrt manuelle Sicherung,
  Worker und Drive-Sync. Bestehender Drive-Phasenkoordinator allein genügt nicht.
- Standardumfang: nur vollständige Archive; keine optionale Teilsicherung,
  Aufbewahrung aus, manueller und automatischer API-Schlüsseltransport aus.
  Warnfrist für vollständigen Erfolg: sieben Tage, sofern die Warnfunktion umgesetzt
  wird. Zielrechte auf neuem Gerät erneut freigeben; kein automatischer Sync.
- Schritte 2 und 2b korrigieren zunächst den bestehenden V1-Export, ohne dessen
  Format zu ändern: genaue Originalfehler, private Vorbereitung vor Zieldialog und
  getrennte Export-/Importmeldungen. Das ist noch kein V2-Vollständigkeitsnachweis.
- Hintergrundvertrag und konkrete WorkManager-Konfliktpolitik vor Schritt 10 anhand
  offizieller Android-Quellen bestätigen. Abruf am 09.11.2026 von
  `developer.android.com` (long-running, fgs/timeout, notification-permission) derzeit
  mit HTTP 403 gesperrt; keine aktuelle Dokumentenprüfung behaupten. Dieser offene
  Punkt blockiert Hintergrundimplementierung, nicht die V1-Sofortkorrektur.


### Ergänzender Quellenabgleich Android / KDF (09.11.2026)

Die API-Dokumentationen der offiziellen Quellrepositorys waren alternativ erreichbar:

- [ServiceInfo.dataSync](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/content/pm/ServiceInfo.java): Backup/Restore gehört zum Typ
  `dataSync`; ab Android 15 gilt dessen Sechs-Stunden-Limit und die typbezogene
  Berechtigung `FOREGROUND_SERVICE_DATA_SYNC`. Ab Ziel-API 34 ist der Typ explizit
  anzugeben, kein Typ `NONE`.
- [Service.onTimeout](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/app/Service.java): nach Timeout nur wenige Sekunden zum
  Stoppen; das Zeitbudget gilt über ein rollierendes 24-Stunden-Fenster.
- [ListenableWorker](https://github.com/androidx/androidx/blob/androidx-main/work/work-runtime/src/main/java/androidx/work/ListenableWorker.java): normale Arbeit hat ein
  Zehn-Minuten-Limit; langlebige Arbeit muss den Foreground-Übergang erfolgreich
  abschließen, bevor sie ein Ergebnis meldet. Der Foreground-Start kann wegen
  Systembeschränkungen scheitern.
- [ExistingWorkPolicy](https://github.com/androidx/androidx/blob/androidx-main/work/work-runtime/src/main/java/androidx/work/ExistingWorkPolicy.kt): `KEEP` ignoriert neue Arbeit
  bei ausstehender Arbeit, `REPLACE` bricht sie ab, `APPEND` übernimmt fehlgeschlagene
  Voraussetzungen; `APPEND_OR_REPLACE` verwirft solche Voraussetzungen. Keine dieser
  Eigenschaften ersetzt die dauerhafte Sollplanung und die geforderten Absturztests.
- [OWASP Password Storage Cheat Sheet](https://github.com/OWASP/CheatSheetSeries/blob/master/cheatsheets/Password_Storage_Cheat_Sheet.md): für PBKDF2-HMAC-SHA256 werden
  mindestens 600.000 Iterationen empfohlen; Argon2id bleibt die bevorzugte Alternative.

Die gesperrten Android-Webseiten sind dadurch nicht als gelesen zu melden. Vor
Schritt 10 bleiben die konkreten Quoten für Ziel-API 36, die verwendete
WorkManager-Version und das Benachrichtigungsverhalten zu bestätigen.


## Verifizierter Zwischenstand der Sofortkorrektur (09.11.2026)

- Basis `302b007ad510c44fb596a0081197490d3430374e`, Featurebranch
  `feat/local-full-backup`; kein Push, kein PR und kein Merge.
- Inventar aktualisiert; neue Tests erkennen unbekannte Room-Tabellen,
  private Dateiwurzeln und DataStore-Erzeuger. Der gesamte neue Archivumfang
  und seine fachliche Roundtrip-Abnahme sind dadurch noch nicht implementiert.
- Schritt 2: fehlende, leere, nicht lesbare und über 1 GiB große Originale
  getrennt melden; Datensatz, Titel und Dateiname benennen. Keine Quelldatenänderung.
- Schritt 2b: `OfflineBackupService.prepareExport` erstellt und prüft das V1-Archiv
  im privaten Speicher vor dem Zieldialog. Die Oberfläche behält Export- und
  Importmeldungen getrennt; Dialogabbruch räumt das vorbereitete Archiv auf.
  Nach der Zielkopie wird das Zielarchiv erneut eingelesen und geprüft.
- Java 21 / Gradle 9.3.1: 47 gezielte Methoden bestanden: PreferenceBackupCompletenessTest
  (13), die beiden aufgabenbezogenen PersistentStorageInventoryTest-Methoden (2),
  OfflineBackupServiceTest (23), OfflineBackupRelationTest (4),
  OfflineBackupPickerComposeTest (5). JUnit-XML gegen die Methoden der Quellen
  geprüft: keine Fehler, Skips, Duplikate oder Suite-Platzhalter. Roborazzi-Verify
  aktiviert; keine neue Screenshotaufnahme und kein behaupteter Bildvergleich.
- Java 17: `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest` bestanden;
  Lint-XML ohne Error-/Fatal-Befunde. `git diff --check` bestanden.
- Debug-Signatur gegen das Zertifikat des unveränderten Repository-Keystores
  geprüft: SHA-256 `ee446593dea9492fa2eaa7fd9602206eb22a38e107de2d3a4e2ecfcc92b4005d`.
  Dies ist kein Signaturvergleich mit einem echten Nutzergerät.
- Android-Emulatortest nicht ausgeführt: lokal kein `/dev/kvm`; GitHub-Ausführung
  erst nach ausdrücklich freigegebenem, aufgabenbezogenem CI-Profil.
- Weiter offen: Schritt 3 (Originalauflösung), vollständige Writer-Matrix und
  Snapshot-Konsistenz, V2-Verschlüsselung, Destination-Coordinator, Probe-Restore,
  vollständiger ersetzender Restore mit Wiederanlauf, dauerhafter Status,
  Zeitplanung und Worker sowie deren Gerätewechsel-/Emulatorabnahme.
- Konkreter Eingriffspunkt für die spätere Writer-Matrix: `ReceiptRepository`
  ist in `ReceiptDatabase.kt` definiert; eine eigene `ReceiptRepository.kt`
  existiert an dieser Basis nicht. Die vorhandenen Drive-Mutexe koordinieren
  Initialisierung, Index und Einzelmetadaten, kein gemeinsames Snapshot-Gate.

## Schritt 3: lokale Originalauflösung

`OfflineBackupResolver` übernimmt das begrenzte, abbrechbare Lesen und Prüfen
vorhandener lokaler Originale. Er erhält ausschließlich Referenzen aus dem für
den Lauf erfassten Datenbestand und schreibt nur in das laufbezogene Staging.

- Absolute Pfade, dekodierte `file:`-URIs und bereits berechtigte `content:`-URIs
  werden unterstützt. Keine neue Ordner-/URI-Freigabe, Anmeldung oder Berechtigung
  wird ausgelöst; fehlender Zugriff führt zum kontrollierten Fehler.
- Ist ein Original nicht erreichbar, darf eine vorhandene Kopie nur mit dem
  gespeicherten SHA-256 des angeforderten Originals einspringen. Kandidaten stammen
  aus dessen exaktem `storedFilename` im kanonischen privaten Verzeichnis
  `managed_documents` oder aus erfassten Dokumentreferenzen mit demselben Hash.
  Keine Verzeichnissuche nach ähnlichen Namen, kein Fallback ohne aufgezeichneten
  Hash und keine Traversal-/Symlink-Flucht über `storedFilename`.
- Auch ein leerer lokaler Dokumentverweis kann über die gespeicherte Identität und
  Prüfsumme aufgelöst werden. Dafür entsteht ausschließlich im Archiv eine eigene
  Referenz; der leere produktive Verweis wird nicht repariert oder überschrieben.
  Die bestehende Ausnahme für reine Beleg-Metadatenprojektionen bleibt unverändert.
- Mehrere widersprüchliche Hash-/Größenangaben für denselben Pfad werden abgelehnt.
  Eine vorhandene, aber inhaltlich veränderte Primärdatei wird nicht still durch
  eine ältere passende Kopie ersetzt. Gespeicherte positive Größen werden geprüft.
- Die Grenze von 1 GiB je Original bleibt erhalten und gilt auch beim Lesen von
  Providern mit unbekannter Länge. SHA-256 wird aus den tatsächlich gelesenen
  Bytes berechnet; Fehler-Sentinels wie `SHA_ERROR` sind kein Inhaltsnachweis.
- Eine zweite vollständige Quellenlesung muss zum kopierten Inhalt passen.
  Abbruch und Fehler räumen ausschließlich die neu angelegte Staging-Datei auf.
  Quellbytes, produktive Dateiverweise und Metadaten bleiben unverändert.
- URI-Queryparameter und Fragmente werden weder in Fehlermeldungen angezeigt noch
  als Klartext-Referenzen im V1-Archiv gespeichert. Solche Referenzen werden nur
  innerhalb des Archivs durch deterministische, undurchsichtige Kennungen ersetzt,
  einschließlich mehrseitiger Belegketten und Manifestzuordnung. Produktive Daten
  werden dafür nicht umgeschrieben. Es werden keine URI-Rechte übertragen.
- Der Restore ordnet die gesicherten Bytes wie bisher einem privaten Zielpfad zu;
  ursprüngliche URI-Zugangsdaten sind dafür nicht erforderlich.
  Das ist noch kein gemeinsamer Room-/Preference-/Datei-Snapshot: eine nachträgliche
  Hash-Prüfung ersetzt ausdrücklich nicht den Vertrag und die Writer-Matrix aus Schritt 4.

Optionales automatisches Cloud-Nachladen ist in diesem Teil nicht aktiviert.
Die geprüften vorhandenen Drive-Downloader liefern vollständige `ByteArray`s;
ihre unveränderte Verwendung wäre für Originale bis 1 GiB kein geeigneter
Streaming-Pfad. Cloud-only-Originale bleiben daher ein sichtbarer Abbruch mit
Benutzeraktion; kein automatischer Sync, keine Drive-Schreibzugriffe und keine
Anmeldedialoge aus der lokalen Sicherung. Ein späterer Downloadadapter muss den
vorhandenen autorisierten Zugriff lesend und mit begrenztem Speicher verwenden.

### Zusätzliche Befunde für Schritt 4 (noch keine vollständige Writer-Matrix)

- Die einzige produktive `Room.databaseBuilder`-Stelle liegt in
  `ReceiptDatabase.kt`, `AppDatabase.getDatabase`. Ein DAO-Mutex allein wäre
  trotzdem unvollständig: `AppDatabaseCallback.onOpen` startet einen asynchronen
  `UPDATE receipts` für fehlende `internalId`/`displayId` über `execSQL`.
  Dieser Start-/Reparaturpfad muss in Gate und Identitätsprüfung einbezogen werden.
- `DrivePendingWrites` schreibt eigene atomare Vorgangsdateien unter
  `noBackupFilesDir/drive-pending-writes`. Es sind technische Verbindungsnachweise,
  keine fachlichen Originale. Die Dateiwurzelprüfung wurde im folgenden Inventarschritt auf
  `filesDir` und `noBackupFilesDir` erweitert; nicht zugeordnete Getter-/
  Speicherzugriffe werden ebenfalls abgelehnt. Die Android-Bezeichnung `noBackupFilesDir` allein
  wäre keine ausreichende fachliche Klassifikation für künftige neue Quellen.
- Ein Vorab-Suchlauf fand zusätzlich zu den DAO-Deklarationen direkte
  Preference-, Datei- und SQL-Schreibstellen. Diese Fundstellen sind erst noch
  Funktionen, Datenquellen und konkreten Koordinationspunkten zuzuordnen;
  eine vollständige Matrix oder globale Snapshot-Sperre wird hier nicht behauptet.


### Prüfnachweis der lokalen Originalauflösung (09.11.2026)

- Ausgangs-Head `c98a226a75bf6d246ae84d29257442d31c23da04`, weiterhin Basis
  `302b007ad510c44fb596a0081197490d3430374e`; ausschließlich Featurebranch.
- Vor der Korrektur gezielt rot nachgewiesen: veralteter Pfad trotz passender
  Kopie, leeres `localUri`, kodierte Datei-URI, berechtigte Content-URI,
  URI-Parameter in Fehlermeldung/Archiv und übergroße Primärdatei trotz älterer Kopie.
- Abschließend 49 gezielte Methoden bestanden: OfflineBackupServiceTest (33),
  OfflineBackupResolverTest (6), OfflineBackupRelationTest (4),
  OfflineBackupPickerComposeTest (5) und die Dateiwurzel-Methode aus
  PersistentStorageInventoryTest (1). JUnit-XML gegen die Methoden der Quellen
  geprüft: keine Fehler, Skips, Duplikate oder Suite-Platzhalter.
- Neue Schutzfälle umfassen falsche Kopien, fehlende Hashes bei veraltetem und
  leerem Verweis, Traversal, verweigerte URI-Rechte, Mehrseiten-Restore ohne
  URI-Queryparameter, zweimaligen Restore ohne Dubletten, Datenänderung beim
  Rücklesen, Größenabweichung, Streaming-Limit, Abbruch und vorhandene Zieldatei.
- Eine fehlgeschlagene Mehrseitenprüfung wurde auf den statischen FileProvider-
  Cache zwischen Robolectric-Anwendungsinstanzen zurückgeführt. Die Testisolation
  wurde nach dem vorhandenen Compose-Testmuster korrigiert; alle Assertions blieben
  erhalten. Der vollständige betroffene Service-Lauf und die Abschlusssammlung
  wurden danach erfolgreich geprüft, ohne Timeout-Erhöhung oder blinden Retry.
- Java 21 / Gradle 9.3.1 für Tests, Roborazzi-Verify ohne neue Screenshotaufnahme;
  Java 17: `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest` bestanden.
  Lint-XML ohne Error-/Fatal-Befunde; `git diff --check` bestanden.
- Debug-Signierzertifikat unverändert:
  `ee446593dea9492fa2eaa7fd9602206eb22a38e107de2d3a4e2ecfcc92b4005d`.
- Keine Room-Migration, Preference-Serialisierungsänderung, neue Bibliothek,
  Manifest-/Berechtigungsänderung oder produktive Datenreparatur.
  Kein automatischer Cloud-Download, kein Push/PR/Merge, kein Emulatorlauf.
- Der Gesamtplan bleibt offen: vollständiges Inventar und Writer-Matrix,
  konsistenter Snapshot, V2-Verschlüsselung, sichere Destination und Probe-Restore,
  ersetzender Restore mit Wiederanlauf, dauerhafter Verlauf, Scheduler/Worker und
  Geräte-/Emulatorabnahme. Das gesondert freizugebende enge CI-Profil bleibt offen.


## Schreibweg-Katalog vor dem Snapshot-Gate (09.11.2026, Basis 992b7d9)

Die folgenden Tabellen ordnen die gefundenen produktiven Mutationsgrenzen zu.
**Keiner der vorgeschlagenen Koordinationspunkte ist damit schon implementiert.**
Die Katalogisierung ist kein Nachweis eines gemeinsamen Snapshots und keine
pauschale Änderungsfreigabe für die aufgeführten Screens oder Fachlogik.

Koordinationspunkte für die weitere Umsetzung:

- **DB:** gemeinsame Room-/SQLite-Grenze des einzigen produktiven Builders
  `ReceiptDatabase.kt: AppDatabase.getDatabase`, einschließlich generierter
  Statements, direkter SQL-Aufrufe und vollständiger Transaktionslebensdauer.
- **PREF:** sämtliche fachlichen Editor-Commits und `apply`-Aufrufe, einschließlich
  Entfernen/Clear und Migration beim Lesen. `apply` aktualisiert den Speicher
  synchron und darf nicht als ausschließlich spätere Dateisystemarbeit gelten.
- **FILE:** Veröffentlichung, Überschreiben und Löschen fachlicher Originalversionen
  sowie vorhandener aufbewahrter Exporte. Vor Gate-Freigabe müssen die zum
  Snapshot gehörenden Versionen fixiert sein; ein offener Deskriptor oder späterer
  Hash-Vergleich allein genügt nicht.
- **REMOTE:** Wartungs-/Operationsgrenze für laufende und neue Synchronisation
  beim Restore. Ein Backup-Mutex ersetzt weder diese Grenze noch DB/PREF/FILE.

### Room: alle 14 produktiv bereitgestellten DAOs

Die DAO-Grenze erfasst auch direkte Aufrufer aus ViewModels, Repository,
Drive-/Firestore-Import und Zusatzrestore. Eine ausschließlich in
`ReceiptRepository` eingebaute Sperre würde diese direkten Wege nicht abdecken.

| Datei / DAO | Mutierende Funktionen | Quellen | Geplanter Punkt |
|---|---|---|---|
| ReceiptDatabase.kt / ReceiptDao | insertReceipt, updateDriveSyncFields, deleteReceiptById, insertAll, deleteAll | receipts, einschließlich Löschzustand und Sync-Bestätigung | DB |
| ReceiptDatabase.kt / PropertyDao | deletePropertyByPropertyId, insertPropertyMetadata, upsertRestoredPropertyMetadata | property_metadata | DB; Bildveröffentlichung zusätzlich FILE |
| ReceiptDatabase.kt / LoanDao | upsertLoan, deleteLoan | loans | DB |
| ReceiptDatabase.kt / ReceiptEntityDao | insert, deleteById, deleteAll | receipt_entities | DB |
| Beleg.kt / BelegDao | insertBeleg, deleteBelegById, deleteAllBelege | belege | DB |
| ReceiptDatabase.kt / ExportAuditDao | insertRun, deleteAll | export_audit_runs | DB; Exportbytes zusätzlich FILE |
| ReceiptDatabase.kt / ReceiptDocumentDao | insertDocument, deleteDocumentById, deleteAll | receipt_documents | DB |
| DocumentModels.kt / ManagedDocumentDao | upsert, updateExisting, upsertAll, deleteById, deleteAllDocuments | managed_documents | DB; Originaldateien zusätzlich FILE |
| DocumentModels.kt / ManagedDocumentDao | adoptOriginalBaseline, updateDriveStorageIfPresent, updateLoanIfPresent, updateReviewIfPresent, updateProcessingIfPresent | Originalnachweis, OCR, KI-Ergebnis, Prüfung und Zuordnungen | DB, vollständige @Transaction-Grenze |
| DocumentModels.kt / ManagedDocumentDao | deleteSearchEntry, insertSearchEntry, clearSearchIndex | abgeleiteter FTS-Index | DB; beim Restore neu aufbauen |
| DocumentModels.kt / ManagedDocumentDao | clearMigrationJournal, upsertMigrationJournal, deleteMigrationJournal | document_migration_journal, technischer Zustand | DB; nicht als alte Aufträge reaktivieren |
| LogbookModels.kt / LogbookDao | upsertTrip, deleteTrip, upsertStandardRoute, deleteStandardRoute | logbook_trips, standard_routes | DB |
| BankingModels.kt / BankDao | upsertAccount, insertTransactions, upsertTransaction, updateTransactionStatus, updateTransactionClassification, updateTransactionReviewState | bank_accounts, bank_transactions | DB |
| BankingModels.kt / BankDao | upsertLink, deleteLink, clearLinks, deleteAllTransactions, deleteAllAccounts, reopenLinkedTransactions | bank_receipt_links und abhängige Buchungen/Konten | DB |
| BankLearningRules.kt / BankLearningRuleDao | upsertRule, deleteRule, insertEvidence, deleteEvidence | bank_learning_rules, bank_rule_evidence | DB |
| BankRentAssignments.kt / BankRentAssignmentDao | upsert, deleteById, clearAll | bank_rent_assignments | DB |
| BankLoanRecurringPersistence.kt / BankLoanAssignmentDao | upsert, deleteForTransaction | bank_loan_assignments | DB |
| BankLoanRecurringPersistence.kt / BankRecurringPatternDao | upsert, setEnabled, delete | bank_recurring_patterns | DB |

Zusätzliche SQL-Wege außerhalb der DAO-Mutatoren:

| Datei / Funktion | Wirkung | Bedarf / Koordination |
|---|---|---|
| ReceiptDatabase.kt / MIGRATION_10_11 bis MIGRATION_33_34 | DDL und Datenübernahmen beim Öffnen alter Schemata | normalen Schemaaufbau vor Snapshot abschließen; keine neue Migration für das Gate |
| ReceiptDatabase.kt / AppDatabaseCallback.onOpen | startet asynchron UPDATE receipts für internalId/displayId | DB; Startreparatur ausdrücklich erfassen und Identitäten vor Erfolg prüfen |
| OfflineBackupService.kt / restore | direkte INSERT-Statements in Room-Transaktion, FTS-Neuaufbau | DB + PREF + FILE, gemeinsamer Restore-Wartungsmodus |
| SupplementalDriveBackup.kt / restorePayload, clearSnapshotDomains | DAO-Import, Ersetzen/Löschen, Preferences und Objektbilder | DB + PREF + FILE; darf nicht am lokalen Restore vorbeilaufen |
| DrivePersistenceRepository.kt / executeFullDriveRestore und Restore-Aufrufer | orchestrieren fachlichen Import und Journal | dieselbe Wartungsgrenze wie lokaler Restore; bestehende Semantik erhalten |

### Fachliche Preference-Schreibwege

Dateien liegen unter `app/src/main/java/com/example`; `ui/` und `data/` sind
jeweils im Dateinamen angegeben. Genannt werden auch implizite Schreibvorgänge
beim Lesen und beim Entfernen anderer Werte.

| Datei / Funktion | Store / fachliche Daten | Geplanter Punkt |
|---|---|---|
| util/DatevProfileService.kt / saveActiveProfile | datev_kanzleiprofil_prefs, active_profile_json | PREF |
| ui/UnifiedUnitsFeature.kt / UnitStatusMetaStore.save | unit_status_meta_prefs | PREF |
| ui/UnifiedUnitsFeature.kt / UnitRentalDetailStore.save | unit_rental_detail_prefs | PREF |
| ui/TenantHistoryFeature.kt / TenantHistoryStore.read | tenant_history_prefs; übernimmt gültige Legacy-Werte beim Lesen | PREF, auch dieser Lesepfad schreibt |
| ui/TenantHistoryFeature.kt / TenantHistoryStore.save, saveInitialPeriods | tenant_history_prefs, neue und Legacy-Schlüssel | PREF; einen Editor vollständig erfassen |
| ui/PropertyUnitScopedData.kt / rentValue | rent_plan_prefs; Migration beim Lesen | PREF |
| ui/PropertyUnitScopedData.kt / setRentValues | rent_plan_prefs, neue und Legacy-Schlüssel | PREF |
| ui/PropertyTaskFeature.kt / PropertyTaskStore.save (einschließlich upsert-Aufrufern) | property_tasks_prefs | PREF |
| ui/ConfirmedAfaValuesStore.kt / write | afa_confirmed_values_prefs | PREF |
| ui/LoanFeature.kt / LoanManagementSection.assignReceipt | loan_interest_assignments | PREF |
| ui/BankFeature.kt / BankTransactionDetailsScreen, Notiz-onValueChange | bank_transaction_notes | PREF, keine Änderung der Bank-Fachlogik |
| ui/AnnualTaxAssistantFeature.kt / AnnualTaxAssistantScreen, Freigabe und Widerruf | annual_tax_approval_prefs | PREF |
| data/RenovationReviewStore.kt / saveMeasure, saveRelation | renovation_review_prefs | PREF; vorhandenen Store-Mutex nicht mit globaler Abdeckung verwechseln |
| ui/ReceiptViewModel.kt / learnVendorRule, deleteLearnedRule, clearAllLearnedRules | ki_learned_rules_prefs | PREF |
| ui/ReceiptViewModel.kt / loadUnitById, lokale string-/number-Helfer | wohneinheiten_prefs; migriert Namen-/Legacy-Schlüssel beim Lesen | PREF |
| ui/ReceiptViewModel.kt / updateWohneinheitForProperty, createProperty, updatePropertyMetadata | wohneinheiten_prefs und zugehörige Property-Zeilen | PREF + DB |
| ui/ReceiptViewModel.kt / selectProperty, deleteProperty, toggleAutoBackup | google_drive_prefs: selected_property_id, auto_backup, auch remove | PREF; keine Autorisierung durch gesicherte Einstellungen |
| ui/ReceiptViewModel.kt / resetLocalReceiptData | entfernt gelernte Regeln und Room-Belegbestand | PREF + DB; kein automatischer Reset durch Backup |
| ui/ReceiptViewModel.kt / saveLogbookDraft, clearLogbookDraft | logbook_drafts | PREF, einschließlich Entfernen |
| data/DrivePersistenceRepository.kt / saveWohneinheitenToPrefs, saveLearnedRulesToPrefs | wohneinheiten_prefs, ki_learned_rules_prefs | PREF; aktive Drive-Importe berücksichtigen |
| data/PersistentPreferenceInventory.kt / restorePayload | alle BACKUP-Stores, Merge/Replace und remove | PREF; Restore-/Rollback-Zuständigkeit, keine neue Autorisierung/Planaktivierung |
| api/AiProviderSettings.kt / saveSelection | ai_provider_settings: Anbieter und Modell | PREF; Credentials bleiben ausgeschlossen |
| api/AiProviderSettings.kt / clearOpenAiKey | entfernt Ciphertext/IV **und ändert den fachlichen Anbieter** | PREF trotz Key-Methodenname; auch getOpenAiKey-Fehlerpfad kann diesen Aufruf auslösen |

Gemischte und technische Preference-Wege sind ausdrücklich zu unterscheiden:

| Datei / Funktionen | Klassifikation | Bedeutung für das Gate |
|---|---|---|
| ui/ReceiptViewModel.kt / connectDrive, disconnectDrive, initializeDrivePersistence, publishIncompleteDriveInventory, checkForDriveRestore, handleDriveFailure, handleDriveOperationFailure, syncAllToDrive | google_drive_prefs, überwiegend Verbindungs-/Fehler-/Zeitstempelzustand | Feldweise Positivliste erhalten; REMOTE beim Restore. Nicht den Drive-Zeitstempel als lokalen Erfolg verwenden |
| api/AiProviderSettings.kt / storeOpenAiKey, storeGeminiKey, storeGoogleRoutesKey, clearGeminiKey, clearGoogleRoutesKey | Geheimnisse im gemischten ai_provider_settings | keine pauschale Store-Kopie; Opt-in bleibt aus. Gemeinsamen Store nicht beim Restore leeren |
| data/DrivePersistenceRepository.kt / getOrCreateInstallationId, saveRestoreJournal | app_installation_prefs, restore_journal_prefs, LOCAL_ONLY | nicht übertragen; Journal darf bei Wiederanlauf nicht durch Backup zurückgesetzt werden |
| data/SharedPreferencesDuplicateCleanupJournalStore.kt / save, removeCompleted | duplicate_cleanup_journals, LOCAL_ONLY | keine alten Bereinigungsaufträge wiederherstellen |
| ui/ReceiptViewModel.kt / persistMetadataCleanupAudit | metadata_duplicate_cleanup_audit, LOCAL_ONLY | kein fachlicher Sicherungsumfang |
| api/GoogleDriveClient.kt / saveCategoryMapping, invalidLocalMapping | category_folder_mappings_prefs, verbindungsgebundener Cache | auf Zielkonto neu auflösen; REMOTE-Operation während Restore sperren |

### Dateien: Originale, Exportartefakte und technische Staging-Daten

| Datei / Funktion | Betroffene Quelle / Mutationsart | Geplanter Punkt |
|---|---|---|
| data/ReceiptOriginalStorage.kt / importOriginal | schreibt Staging, veröffentlicht managed_documents/UUID.ext per rename | FILE: vollständige Veröffentlichung und zugehörige Metadatenzuordnung |
| data/ManagedDocumentService.kt / persistPrepared | schreibt managed_documents und persistiert Dokumentmetadaten | FILE + DB; mögliche In-place-Schreibweise vor einer Unveränderlichkeitsbehauptung prüfen |
| ui/ReceiptViewModel.kt / downloadManagedDocument | veröffentlicht über ReceiptOriginalStorage.storeDownloadedOriginal eine neue physische Version und aktualisiert danach lokale Referenz | FILE + DB; alte Version bleibt erhalten, gemeinsames Gate noch offen |
| ui/ReceiptViewModel.kt / analyzeReceipt | schreibt receipt_page_*.jpg | FILE + zugehöriger Beleg-/Dokumentimport |
| ui/ReceiptViewModel.kt / forceDocumentDownload | löscht vorhandene lokale Belegpfade vor erneutem Download | FILE + REMOTE; vor Löschung alte Snapshot-Version schützen |
| ui/ImmobilienManagerFeature.kt / PropertyImagePicker | kopiert Objektbild nach property-images, startet Metadatenupdate | FILE + DB; keine Kopf-/Navigationsänderung |
| data/DrivePersistenceRepository.kt / downloadDocumentOnDemand | schreibt restored_receipts | FILE + DB, falls referenzierter Bestand betroffen ist |
| data/DrivePersistenceRepository.kt / downloadReceiptOriginals | Staging und rename nach managed_documents | FILE + DB |
| data/DrivePersistenceRepository.kt / repairAndUploadOriginalDocument | copyTo(overwrite=true) nach receipt_documents, Beleg-Update und Drive-Schreiben | FILE + DB + REMOTE |
| data/SupplementalDriveBackup.kt / restorePayload | Objektbild-Staging und rename nach property-images | FILE + DB + PREF |
| data/OfflineBackupService.kt / restore | kopiert Original-Staging, rename nach offline-originals, wendet SQL/Preferences an | FILE + DB + PREF, Restore-Wartungsmodus |
| util/DatevExporter.kt / createDatevZipPackage; util/DatevCsvOutput.kt / create | noch vorhandene DATEV-ZIP-/CSV-Artefakte im Cache | FILE für tatsächlich aufbewahrte Exportdateien; kein pauschales Cache-Backup |
| util/AdvisorPackageBuilder.kt / buildPackage; util/PdfExporter.kt / exportReceiptsToPdf | Beraterpaket und PDF-Exportdateien | Inventar/FILE für vorhandene aufbewahrte Artefakte |
| ui/ReceiptViewModel.kt / exportToDatev | CSV, document.xml und datev_export.pdf im Cache | vorhandene aufbewahrte Ausgaben von temporären Zwischenständen unterscheiden |
| ui/ReceiptAppUi.kt / exportBankStatementToCsv | cacheDir/exports | gespeicherte CSV-Datei gesondert inventarisieren |
| ui/ReceiptDetailScreen.kt / ReceiptDetailDialog, ReceiptDetailScreen; ui/ReceiptAppUi.kt / ReceiptPreviewSection, DocumentStatusOverviewDialog | receipt_replacement_*, repair_file_*, repair_scan_*, overview_repair_* | Staging ist temporär, aber anschließende Originalersetzung muss über FILE/DB laufen |
| ui/ReceiptDetailScreen.kt / Datei-Exportcallback; ui/LogbookManagement.kt / CSV-Exportcallback | openOutputStream in ausdrücklich gewähltes SAF-Ziel | keine Wiederherstellungsberechtigung ableiten; externe Exporte nicht unbemerkt zum lokalen Bestand erklären |
| data/OfflineBackupService.kt / prepareExport, create, inspect, Prepared.close; data/OfflineBackupResolver.kt / copy; ui/OfflineBackupFeature.kt / Pickerablauf | eigene Archiv-/Import-/Original-Staging-Dateien und Zielkopie | eigene Operationskennung/Ownership, kein fachlicher Writer-Gate-Nachweis durch diese Dateien |
| data/DrivePendingWrites.kt / rememberOriginalChange, rememberBackupOriginalChange, rememberReceiptIndexPending, rememberJsonBaseline, creation, completed* | atomare lokale Drive-Vorgangsdateien | LOCAL_ONLY; aus fachlichem Snapshot ausschließen, aktive Sync-Operation über REMOTE berücksichtigen |

### Weitere aktive Synchronisation: Firestore

`ReceiptViewModel.syncWithCloud` importiert Firestore-Belege über
`ReceiptRepository.insertAll` und Einheiten über `updateWohneinheitForProperty`.
Andere ViewModel-Schreibpfade rufen `FirestoreService.saveReceipt`,
`deleteReceipt` und `saveWohneinheit` auf. Das sind tatsächlich vorhandene
Schreibwege, keine Freigabe einer neuen Cloud-Funktion. Die eingehenden Änderungen
müssen DB/PREF durchlaufen. Beim Restore müssen zusätzlich bereits gestartete
Firestore-Schreibfolgen unterbrochen/abgewartet und neue Folgen gesperrt werden;
ein ausschließlich auf Drive begrenzter Wartungsmodus wäre unvollständig.

### Noch zu schließende Nachweise vor Produktivcode des Snapshot-Gates

1. Die ausgewählte DB-Grenze gegen die tatsächlich verwendete Room-/SQLite-API
   prüfen: generierte DAO-Statements, direkte SQL-Aufrufe, Startup-Reparatur,
   Commit/Rollback und verschachtelte Transaktionen müssen erfasst sein.
2. Die Preference-Adapter müssen sämtliche oben genannten fachlichen Wege
   einschließlich Lesemigration, remove/clear und des gemischten Key-Fehlerpfads
   abdecken. Anmelde-/Credential-Daten nicht exportieren oder überschreiben.
3. Große Originale nicht bei blockiertem UI-Thread über eine lang gehaltene
   globale Sperre kopieren. Entweder einen nachweislich kurzen Erfassungsschritt
   oder vorher dokumentierte Versionierungsalternative wählen. Nicht nur einen
   Deskriptor öffnen und später einen Hash als gemeinsamen Zeitpunkt ausgeben.
4. Unbekannte externe/Content-URI-Versionen, Löschung und In-place-Änderung müssen
   einen fixierten Inhalt oder Abbruch ergeben. Erst nach vollständiger Fixierung
   freigeben und ausschließlich aus dem unveränderlichen Staging archivieren.
5. Gezielte Nachweise sind in den noch zu erstellenden Coordinator-/Restore-Tests
   entlang der produktiven Verantwortlichkeiten zu ergänzen: SQL/PREF/Originale
   während Erfassung ändern, nach Freigabe Datei ändern/löschen, Startup-Schreiber,
   paralleler manueller Lauf/Worker, laufender Drive-/Firestore-Sync und Prozessende.
   Diese Tests werden hier **nicht** als erstellt oder ausgeführt gemeldet.

Der Katalog nennt die bei dieser Basis gefundenen Grenzen. Er beweist noch keine
lückenlose instrumentierte Writer-Abdeckung; diese ist vor einer Erfolgsaussage
für den neuen Snapshot zwingend durch Implementierung und gezielte Tests zu belegen.

### Dateiversionsgrenze für einzelne Dokument-Downloads (09.11.2026)

Konkreter Bedarf aus der Writer-Matrix: `ReceiptViewModel.downloadManagedDocument`
schrieb bisher unmittelbar in `managed_documents/documentId.ext`. Ein Leser einer
bereits erfassten Referenz konnte dadurch während des Downloads verkürzte oder
neue Bytes erhalten. Die minimale Verdrahtung dieser Funktion verwendet jetzt
`ReceiptOriginalStorage.storeDownloadedOriginal`; kein Umbau des ViewModels oder
des allgemeinen Drive-Syncs.

Die Speicherfunktion prüft nichtleeren Inhalt, vorhandenen SHA-256 und eine
vorhandene positive Größenangabe, schreibt zunächst privates Staging, synchronisiert
den Dateideskriptor und veröffentlicht per Rename auf einen neuen UUID-Pfad im
selben Verzeichnis. Erst danach erhält der bestehende Repository-Aufruf die neue
Referenz. Dokument-ID, Objekt-/Einheiten-/Belegzuordnung, Drive-ID und fachlicher
Dateiname bleiben erhalten. Auch Legacy-Dokumente ohne Inhaltsnachweise erhalten
Hash und Größe. Pfaderweiterungen aus Metadaten werden begrenzt und validiert.

Vorherige Versionen werden hier weder überschrieben noch gelöscht. Auch ein Fehler
nach der Veröffentlichung darf keine möglicherweise bereits referenzierte Datei
entfernen. Es gibt bewusst noch keine automatische Bereinigung oder Aufbewahrung;
zusätzliche Downloads benötigen daher zusätzlichen lokalen Speicher. Das ist keine
globale Löschsperre: andere erfasste Schreib-/Löschwege sind weiterhin einzubinden.

Gezielter Nachweis: `ReceiptOriginalStorageVersionTest` prüft unveränderte alte
Bytes, mehrere gleichzeitig lesbare Versionen, Identitäts-/Metadatenerhalt,
Hash-/Größen-/Leerdateifehler ohne Veröffentlichung, Legacy-Nachweise, sichere
Pfadbildung und nicht verfügbaren Speicher. `OfflineBackupResolverTest` und
`ReceiptOriginalPersistenceTest` sichern die unmittelbar betroffenen Lese- und
Originalpersistenzpfade ab. Alle 17 ausgewählten Methoden bestanden, ohne Fehler,
Skips oder Platzhalter (JUnit-XML geprüft, Kopien unter
`/tmp/immopilot-original-version-results`). Java 21 / Gradle 9.3.1,
Roborazzi-Verify: `/tmp/immopilot-original-version-tests.log`.
Java 17: `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest` bestanden
(`/tmp/immopilot-original-version-build.log`), Lint ohne Error/Fatal.
`git diff --check` bestanden. Kein Emulatorlauf, Push oder CI-Eingriff.

Diese Grenze ist eine Voraussetzung für die im Folgenden festgelegte, noch
umzusetzende Snapshot-Fixierung. Sie koordiniert weder Room mit Preferences noch andere
Dateiproduzenten und belegt deshalb **noch keinen global konsistenten Snapshot**.

### Festgelegte Snapshot-Alternative für Schritt 4: geprüfte Mutationsgeneration

Für die weitere Umsetzung wird eine optimistische Erfassungsphase mit gemeinsamer
Mutationsgeneration festgelegt. Sie vermeidet eine UI-Sperre über das Kopieren
großer Originale. Das folgende Verfahren ist **noch nicht implementiert**; der
bestehende V1-Exporter erhält durch diese Festlegung keine Konsistenzgarantie.

1. Jeder fachliche Schreibweg aus der Matrix meldet Beginn und Ende an derselben
   prozessweiten Koordination. Ein atomar geschützter Zustand enthält eine monoton
   wachsende Generation und die Zahl aktiver Schreiber. Jede Mutation einschließlich
   fehlgeschlagener/abgebrochener Versuche verändert die Generation. Eine fachliche
   Operation über mehrere Stores bleibt vom ersten möglichen Schreibzugriff bis
   zum Abschluss aller Teiländerungen aktiv; zwischen DB und Preference-/Dateischritt
   darf keine scheinbar ruhende Lücke entstehen. Verschachtelte Schreiber sind erlaubt.
2. Erfassung startet nur bei null aktiven Schreibern und merkt sich die Generation.
   Sie liest Room in konsistenter Lesetransaktion, materialisiert die inventarisierten
   Preferences und kopiert sämtliche referenzierten Originalbytes in eigenes privates
   Staging. Die Room-Transaktion muss nicht über große Dateikopien gehalten werden:
   jede zwischenzeitliche Mutation macht die gesamte Erfassung ungültig.
3. Nach vollständiger Fixierung aller Bytes prüft die Koordination atomar dieselbe
   Generation und erneut null aktive Schreiber. Schon ein einziger Schreibbeginn
   erzwingt Abbruch und Entfernung ausschließlich des laufbezogenen Stagings.
   Keine automatische Wiederholung und keine Aktualisierung des letzten Erfolgs.
   Bei unveränderter Generation gab es im gesamten Erfassungsintervall keine
   registrierte fachliche Mutation; Room, Preferences und Originalzuordnungen
   stammen deshalb aus demselben ruhenden Stand.
4. Erst dieser Abschluss gibt das unveränderliche Staging zur Archivierung frei.
   Änderungen danach sind erlaubt und betreffen ausschließlich spätere Sicherungen.
   Archivierung, Verschlüsselung und Zielkopie verwenden keine Live-Dateien mehr.
   Bei Prozessende wird ein unvollständiges Staging niemals als Erfolg übernommen.
5. Für externe/Content-URI-Originale reicht eine Generation der App nicht: vorab in
   den erfassten Metadaten vorhandene Inhaltsnachweise müssen die kopierten Bytes
   eindeutig identifizieren. Fehlen belastbare Nachweise einer nicht vollständig
   koordinierten Quelle, wird abgebrochen. Hashes, die erst nach der Erfassung an
   einer veränderlichen Live-Quelle berechnet werden, ersetzen diese Bedingung nicht.

Die Aktivierung des neuen Snapshots ist gesperrt, bis die gesamte Matrix angebunden
und geprüft ist: alle DAO-/Repository-Zugänge, direkte SQL-Schreiber einschließlich
Startup-Reparatur, Preference-Editoren einschließlich Migrationen beim Lesen,
Dateiproduzenten/-löscher und laufender Drive-/Firestore-Sync. Ein nur teilweise
angebundener Zähler wäre genauso unzureichend wie ein alleiniger Backup-Mutex.
Die Originalveröffentlichung oben ist nur eine vorbereitende Dateigrenze.

Verpflichtende gezielte Nachweise bei der Anbindung: aktiver Schreiber vor Beginn;
DB-, Preference- und Originaländerung während Erfassung; mehrteiliger Schreiber
zwischen seinen Store-Schritten; Fehler und Abbruch ohne hängenden Aktivzähler;
Originaländerung direkt nach Freigabe bei anschließendem Lesen ausschließlich
aus Staging; unbekannte externe Version; Startup-/Sync-Schreiber. Erfolgreich ist
ein vollständig konsistenter Stand oder ein sichtbarer Abbruch. Eine hohe
Schreibaktivität darf die Sicherung verhindern, aber niemals das UI für die
Dauer einer großen Dateikopie blockieren oder einen Mischstand veröffentlichen.


### Prüfnachweis der Inventarergänzung und des Schreibweg-Katalogs (09.11.2026)

- Ausgangs-Head `992b7d9455782733f4b0a55dc1d6b2d4e5299159`, weiterhin
  Basis `302b007ad510c44fb596a0081197490d3430374e`.
- Die erweiterte Prüfung schlug vor der Klassifikation gezielt an
  `DrivePendingWrites.kt: noBackupFilesDir/drive-pending-writes` fehl.
  Die technische Quelle ist nun ausdrücklich LOCAL_ONLY zugeordnet.
- Die Guard-Schlüssel enthalten Speicher-API und Wurzelname; ein Wechsel von
  `noBackupFilesDir` nach `filesDir` ist daher keine still akzeptierte gleiche
  Quelle. Neue nicht zugeordnete Getter-/Speicherzugriffe verlangen eine bewusste
  Inventarergänzung. Java- und Kotlin-Dateien im vorhandenen Main-Quellverzeichnis
  werden für Datei-/DataStore- und Preference-Öffnungen geprüft.
- Zwei gezielte Methoden bestanden: `persistentFileRootsAndDataStoreOwnersRequireExplicitClassification`
  und `everySharedPreferenceOpeningMustResolveToAnExplicitClassification` aus
  `PersistentStorageInventoryTest`. JUnit-XML auf exakte Methoden, Fehler, Skips,
  Duplikate und Platzhalter geprüft. Keine anderen Testsuiten ausgeführt.
- Java 21 / Gradle 9.3.1 für Tests, Roborazzi-Verify; Java 17:
  `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest` bestanden.
  Lint-XML ohne Error-/Fatal-Befunde; `git diff --check` bestanden.
- Geändert wurden ausschließlich diese Inventar-Testdatei und dieses Dokument.
  App-Code, Daten, Datenbankschema, Signierung, CI und bestehende Aufgabenpläne
  blieben unverändert. Kein Push, PR, Merge oder Emulatorlauf.
- Der Schreibweg-Katalog benennt produktive Funktionen und notwendige
  Koordinationspunkte. Die Implementierung und vollständige Laufzeit-Abnahme
  eines gemeinsamen Snapshot-Gates bleiben offen; es gibt hier noch keinen
  neuen Konsistenz- oder Vollständigkeitsnachweis für die Sicherungs-Engine.
