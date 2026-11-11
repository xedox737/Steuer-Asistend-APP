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
| ReceiptDatabase.kt / AppDatabaseCallback.onOpen | startet asynchron UPDATE receipts für internalId/displayId; Anmeldung vor dem Einplanen, Abschluss-/Fehlerbarriere im Coordinator | DB; Initialisierung vor Erfassung abwarten, Fehler sperrt koordinierte Snapshots |
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
| ui/ReceiptViewModel.kt / forceDocumentDownload | fordert Remote-Neuabruf ohne Vorablöschung an | FILE + DB + REMOTE; Versionsübergabe nach vollständiger Prüfung, gemeinsames Gate offen |
| ui/ImmobilienManagerFeature.kt / PropertyImagePicker | kopiert Objektbild nach property-images, startet Metadatenupdate | FILE + DB; keine Kopf-/Navigationsänderung |
| data/DrivePersistenceRepository.kt / downloadDocumentOnDemand | Legacy-Einzelbeleg wird über ReceiptOriginalStorage.storeDownloadedVersion neu veröffentlicht; bisherige restored_receipts bleiben erhalten | FILE + DB; Mehrseitenpfad gesondert erfasst, gemeinsames Gate noch offen |
| data/DrivePersistenceRepository.kt / downloadReceiptOriginals | vorhandene lokale Bytes oder expliziter Remote-Neuabruf; neue Versionen über ReceiptOriginalStorage, Referenzübergabe nach Seitenprüfung | FILE + DB; gemeinsame Erfassung/Mutation noch zu koordinieren |
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

### Dateiversionsgrenze für Legacy-Einzelbeleg-Downloads (09.11.2026)

Konkreter weiterer Bedarf aus der Matrix: der Legacy-Zweig von
`DrivePersistenceRepository.downloadDocumentOnDemand` schrieb direkt in
`restored_receipts/internalId/filename`, auch wenn dieser Pfad bereits referenziert
war. Nur dieser Veröffentlichungspunkt wird auf die gemeinsam verwendete
`ReceiptOriginalStorage.storeDownloadedVersion` umgestellt. Die vorhandene
Speicherfunktion wird dafür aufgeteilt, ohne den Validierungsvertrag des
ManagedDocument-Downloads zu ändern. Neue Bytes werden vollständig geschrieben,
per fsync gesichert und unter neuem UUID-Pfad in managed_documents veröffentlicht;
die Metadaten werden anschließend über den bestehenden Repository-Aufruf angepasst.
Bestehende restored_receipts-Dateien und fachliche IDs/Dateinamen bleiben erhalten.

Der Download verwendet die bereits vorhandene `ManagedDocumentDriveGateway`-
Schnittstelle. Deren Produktivadapter ruft weiterhin dieselbe vorhandene
GoogleDriveClient-Downloadfunktion auf; die Änderung ermöglicht isolierte Tests
ohne echte Konten, Netzwerk, neue Berechtigungen oder Cloud-Schreiboperationen.
Keine Änderung an allgemeinem Sync, Formatdetektion oder Mehrseiten-Auswahl.

Gezielte Nachweise: zwei neue Methoden in `DrivePersistenceManagedDocumentE2eTest`
für Legacy-Download mit weiterhin lesbarer alter Version, stabile Zuordnungen,
Wiederholung ohne Dubletten und fehlende/leere Downloads ohne veränderte Referenz
oder neue Datei. Zusätzlich dessen bestehender Mehrseiten-Roundtrip und die fünf
Methoden von `ReceiptOriginalStorageVersionTest`, da deren Speicherpfad jetzt
gemeinsam genutzt wird. Alle acht ausgewählten Methoden bestanden, ohne Fehler,
Skips oder Platzhalter; exakte Methodennamen im JUnit-XML geprüft und Ergebnisse
unter `/tmp/immopilot-legacy-version-results` gesichert. Testprotokoll:
`/tmp/immopilot-legacy-version-tests.log` (Java 21, Gradle 9.3.1, Roborazzi-Verify).
Java 17: `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest` bestanden
(`/tmp/immopilot-legacy-version-build.log`), Lint ohne Error/Fatal;
`git diff --check` bestanden. Kein Emulatorlauf, echter Cloud-Test, Push oder CI-Eingriff.

Die zu diesem Prüfstand noch vorhandene Vorablöschung in
`ReceiptViewModel.forceDocumentDownload` wird im nachfolgenden Schritt bearbeitet;
die übrigen Schreib-/Löschgrenzen der Matrix sind noch nicht koordiniert. Die oben
festgelegte globale Snapshot-Alternative bleibt bis zur vollständigen Anbindung
und ihren Nebenläufigkeitsnachweisen inaktiv.

### Keine Vorablöschung beim erzwungenen Belegdownload (09.11.2026)

Konkreter Bedarf aus der Matrix: `ReceiptViewModel.forceDocumentDownload` löscht
löschte alle lokalen Originalpfade, bevor Anmeldung, Remote-Verfügbarkeit und
Prüfsummen geklärt sind. Dadurch zerstört schon ein fehlgeschlagener Neuversuch
vorhandene Dateien und gegebenenfalls eine gerade erfasste Live-Referenz.
`ReceiptOriginalPersistenceTest.forcedDownloadWithoutAccountPreservesEveryExistingOriginal`
weist diesen Fehler ohne Anmeldung oder Netzwerk mit zwei synthetischen Originalen nach.
Der Test schlug vor der Korrektur genau an der Assertion zum Erhalt der alten
Dateien fehl (ein ausgeführter Test, ein Assertion-Fehler, keine Skips;
`/tmp/immopilot-force-download-before.log` und zugehörige gesicherte XML-Datei).

Die kleinste notwendige Verdrahtung ist in `forceDocumentDownload`,
`downloadReceiptDocument`, `DrivePersistenceRepository.downloadDocumentOnDemand`
und `downloadReceiptOriginals` vorgenommen: expliziter Remote-Neuabruf ohne
Vorablöschung, Veröffentlichung neuer physischer Versionen über den vorhandenen
Originalspeicher, Referenzübergabe erst nach Prüfung aller Seiten. Normale Aufrufe
verwenden weiterhin vorhandene gültige lokale Originale. Zusätzliche gezielte
Repository-Tests prüfen korruptes lokales Original mit gültigem Remote-Ersatz,
fehlenden Remote-Inhalt und Fehler der zweiten Seite vor Referenzübergabe.
Das ist keine Freigabe für sonstige Sync-/UI- oder Navigationsänderungen.

Neue Remote-Versionen werden auch für Mehrseitenbelege nur unter neuen UUID-Pfaden
veröffentlicht. Vorherige Bytes bleiben unverändert, auch wenn die zweite Seite
fehlschlägt. Die bestehende Reihenfolge bleibt erhalten: erst alle Seiten prüfen,
dann Dokument- und Belegreferenzen aktualisieren. Ein Fehler bei späteren DB-
Schritten ist damit noch kein transaktionaler Gesamt-Rollback; das gemeinsame
Mutationsfenster und der Restore-Wiederanlauf sind weiterhin gesondert umzusetzen.
Nach einem Fehler können neue unreferenzierte Dateien verbleiben; es gibt keine
automatische Bereinigung und keinen irreführenden erfolgreichen Snapshot-Status.
Der erste kombinierte Lauf enthielt einen Synchronisationsfehler des neuen UI-
Tests: `runTest` ließ den virtuellen 5000-ms-Timer vor dem echten IO-Job ablaufen.
Die Prüfung wartet jetzt innerhalb von `withContext(Dispatchers.Default)` mit
demselben 5000-ms-Limit auf den abschließenden Fehlerstatus. Keine Assertion,
Produktions-Timeouts oder Synchronisierungsbedingung wurden abgeschwächt.
Protokoll des ersten Laufs: `/tmp/immopilot-force-download-tests.log`.

Alle 18 gezielten Methoden sind abschließend bestanden: sieben Methoden aus
`ReceiptOriginalPersistenceTest`, fünf Speicher-Versionsmethoden, fünf gezielt
ausgewählte Repository-Methoden und
`Phase10DRestoreMetadataTest.originalDownloadMustPreserveVersionAndSubsequentMergeOrdering`.
Die zehn bereits bestandenen unveränderten Speicher-/Repository-Methoden wurden
nach der reinen UI-Testkorrektur nicht erneut ausgeführt. Der zweite Lauf prüfte
die sieben betroffenen UI-Methoden und die zusätzlich identifizierte Metadaten-
Regression (`/tmp/immopilot-force-download-final-tests.log`). Alle endgültigen
JUnit-XML-Dateien liegen unter `/tmp/immopilot-force-download-results`; exakte
Methoden, Klassen, Fehler/Skips und Platzhalter wurden geprüft.
Java 21 / Gradle 9.3.1, Roborazzi-Verify. Java 17: `lintDebug`, `assembleDebug`,
`assembleDebugAndroidTest` bestanden (`/tmp/immopilot-force-download-build.log`),
Lint ohne Error/Fatal; `git diff --check` bestanden. Debug-Signatur unverändert:
SHA-256 `ee446593dea9492fa2eaa7fd9602206eb22a38e107de2d3a4e2ecfcc92b4005d`.
Keine Emulatorausführung, kein echter Cloud-Zugriff, Push, Merge oder CI-Eingriff.

### Zentrale Anbindung: SQLite und Startup-Reparatur (09.11.2026)

Die Writer-Matrix benennt `ReceiptDatabase.kt/AppDatabase.getDatabase` als einzigen
produktiven Room-Einstieg. Hier wird die vorhandene Framework-OpenHelper-Factory
mit einer delegierenden Mutationsverfolgung verbunden. Sie erfasst direkte SQL-
Aufrufe, DAO-Statements und Transaktionsenden, ohne SQL, Schema, Migrationen oder
Konfliktregeln zu ändern. Bei verschachtelten Transaktionen bleibt ein begonnener
Schreiber bis zum Ende der äußeren Transaktion aktiv, auch bei Rollback.
Readonly-Erfassung selbst darf die Generation nicht verändern.

`AppDatabaseCallback.onOpen` besitzt zusätzlich eine vom Start des asynchronen
Reparaturjobs bis zu dessen Abschluss gehaltene Schreibanmeldung. Fehler oder
Abbruch der Initialisierung sperren neue koordinierte Snapshots; ein nur
bereits eingeplanter Job gilt nicht als ruhender Datenstand. Die bestehende
V1-Exportfunktion wird hier noch nicht auf eine unvollständig angebundene
Koordination umgeschaltet. Preferences, Dateien, Sync und mehrteilige fachliche
Operationen bleiben anschließend ausdrücklich anzubinden.

Versionsnachweis: `gradle/libs.versions.toml` verwendet Room 2.7.0; dessen
API-Abhängigkeiten enthalten SQLite/SQLite-Framework 2.5.0. Originalquellen:
[Room 2.7.0 Sources](https://dl.google.com/dl/android/maven2/androidx/room/room-runtime-android/2.7.0/room-runtime-android-2.7.0-sources.jar),
Einträge `RoomDatabase.android.kt`, `RoomConnectionManager.android.kt` und
`driver/SupportSQLiteStatement.android.kt`;
[SQLite 2.5.0 Sources](https://dl.google.com/dl/android/maven2/androidx/sqlite/sqlite-android/2.5.0/sqlite-android-2.5.0-sources.jar),
Einträge `SupportSQLiteDatabase.android.kt`, `SupportSQLiteOpenHelper.android.kt`.
Die Definitionen sind zusätzlich gegen die tatsächlich eingebundenen Klassen
geprüft (`/tmp/immopilot-sqlite-2.5-api.txt`). Room dokumentiert
`openHelperFactory` als austauschbaren Datenbankzugang mit Framework-Default;
SQLite dokumentiert `inTransaction()` für den aktuellen Thread und Commit/Rollback
am Ende der äußeren Transaktion. Factory-Flags, Callback-Reihenfolge und
Rückgabewerte sind unverändert zu delegieren. Gezielte Tests müssen echte Room-
DAOs, direkte/kompilierte Statements, verschachtelte Transaktion/Rollback,
Readonly-Erfassung sowie den erfolgreichen und abgebrochenen Startup-Job prüfen.

Implementiert in `OfflineBackupCoordinator`: atomare Generation mit aktiven
Schreibern, idempotentes Schließen einer Anmeldung, Prüfung der Erfassungsmarke
und getrennte Initialisierungs-Abschlussbarriere. `awaitInitialization` wartet die
bekannten Jobs ab; `beginCapture` prüft anschließend Fehler und aktive Schreiber.
Die Fortsetzung wartender Coroutinen erfolgt außerhalb des Monitors. SQL und
Dateikopien laufen nicht unter dem Monitor. Neue spätere Schreibanmeldungen
entwerten eine laufende Erfassung; es gibt keine automatische Wiederholung.

Die Factory behält den Framework-Delegate, Datenbanknamen, Flags, Ergebnisse und
Exceptions bei. Schreibende Statements bleiben während äußerer Transaktionen
registriert. Nicht als reine SELECT-/bekannte Lese-PRAGMA erkannte Cursor werden
vorsorglich bis zum Schließen registriert. Die App und die geprüften Room-2.7.0-
Quellen haben keinen Aufruf von `execPerConnectionSQL`; sollte dieser künftig
auftreten, bleiben die Delegate-Semantik erhalten und koordinierte Snapshots
werden gesperrt, da diese API SQL erst auf später geöffneten Verbindungen ausführen
kann. Das ist keine stille Annahme über künftig unbekannte Schreibwege.

Zusätzliche versionsgenaue Quelle:
[SQLite-Framework 2.5.0 Sources](https://dl.google.com/dl/android/maven2/androidx/sqlite/sqlite-framework-android/2.5.0/sqlite-framework-android-2.5.0-sources.jar),
Einträge `FrameworkSQLiteDatabase.android.kt` und `FrameworkSQLiteOpenHelper.android.kt`.
Zwei geprüfte Besonderheiten: der Cancellation-Overload dereferenziert trotz
nullable Interface-Signatur das Signal; bei null wird deshalb der ursprüngliche
Overload ohne Signal verwendet. Nach Migration öffnet der Helper erneut und ruft
`onConfigure` ein zweites Mal auf. Native Create-/Migrationstransaktionen enden
außerhalb des Support-Interfaces. Ihre Schreibanmeldungen werden beim Öffnungs-
abschluss auch nach dem nativen Schließen einer alten Verbindung aufgelöst.

13 gezielte Methoden aus `OfflineBackupCoordinatorTest` bestanden: zwölf
Koordinations-/Integrationsfälle und ein anschließend ergänzter V1-Kompatibilitäts-
fall. Exakte Methoden und Klassen, Fehler/Skips, Duplikate und Platzhalter im
JUnit-XML geprüft; Ergebnisse `/tmp/immopilot-sql-coordination-results`.
Protokolle: `/tmp/immopilot-sql-coordination-verification.log` (zwölf Methoden),
`/tmp/immopilot-sql-coordination-v1-tests.log` (V1). Die bereits bestandenen zwölf
unveränderten Methoden wurden nach Ergänzung des V1-Falls nicht erneut ausgeführt.
Java 21 / Gradle 9.3.1, Roborazzi-Verify; Java 17: `lintDebug`, `assembleDebug`,
`assembleDebugAndroidTest` bestanden (`/tmp/immopilot-sql-coordination-build.log`),
Lint ohne Error/Fatal; `git diff --check` bestanden. Debug-Signatur unverändert.

Die vorangehenden Läufe sind nachvollziehbar getrennt: eine neue Testsignatur
benötigte explizites Unit; danach fand der erste Runtime-Lauf den null-Signal-
Fehler im Adapter. Die zusätzliche Integrationsprüfung fand eine falsche neue
Erwartung zur Callback-Anzahl; diese wurde anhand der Originalquelle auf die
exakte tatsächliche Reihenfolge korrigiert. Keine Assertion entfernt, kein
Timeout erhöht, keine Testwiederholung ohne zugehörige Änderung. Die Protokolle
`immopilot-sql-coordination-tests.log`, `-final-tests.log`, `-overload-tests.log`
und `-integration-tests.log` bleiben unter `/tmp` erhalten.

Diese vier Dateien bilden **nur die SQL-/Startup-Grundlage**. Auch bei erfassten
SQL-Statements benötigen mehrteilige Repository-/Fachoperationen noch durchgehende
Anmeldungen über ihre Teiltransaktionen hinweg. Preference-, Datei- und Sync-
Schreibwege sind ebenfalls noch anzubinden. Der bestehende Export wird weiterhin
nicht über eine teilweise angebundene Koordination als konsistenter Vollsnapshot
ausgegeben. Keine Schema-/Migrationsänderung, neue Produktionstabelle, CI-Änderung,
Emulatorausführung, echter Nutzer-/Cloud-Test, Push oder Merge.

### Schritt 4, Preference-Grenze im Inventar (09.11.2026)

Konkreter Bedarf aus der Matrix: direkte Preference-Editoren sind bislang nicht
an die gemeinsame Generation angebunden. Zunächst ist in
`OfflineBackupCoordinator` ein delegierender Editor-Adapter ergänzt und über
`PersistentPreferenceInventory.open` für bekannte BACKUP-Stores verwendet.
Die bestehenden Inventar-Lese-/Restore-Funktionen verwenden jetzt diesen Zugang;
sonstige fachliche Öffnungsstellen werden anschließend einzeln umgestellt.
Der globale Snapshot bleibt bis zum Abschluss dieser Anbindung inaktiv.

Nur commit/apply veröffentlichen Änderungen; sämtliche put/remove/clear-
Ketten müssen den erfassten Editor zurückgeben. Listener erhalten denselben
erfassten Preference-Zugang und bleiben schwach referenziert. Rückgabewerte,
unveröffentlichte Editor-Puffer und die asynchrone apply-Semantik bleiben erhalten.
Kind-/Key-Filter, MERGE-/REPLACE-Regeln und Credential-Schutz werden nicht geändert.
Unbekannte Store-Namen müssen abgelehnt und neue Öffnungsstellen von der
Inventarprüfung erkannt werden, auch beim neuen zentralen Zugang.

Quelle zur unmittelbaren atomaren Veröffentlichung im Speicher, Editor-Ketten
und Listener-Lebensdauer: [Android SharedPreferences, API 36](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-16.0.0_r1/core/java/android/content/SharedPreferences.java).
Gezielte Nachweise: alle Editor-Typen einschließlich Verkettung, getrennte Puffer,
commit-Rückgabe/Fehler, Listener und Deregistrierung, reine Reads, unbekannter
Store, tatsächlicher Inventar-Restore sowie vorhandener Secret-/V1-Schutz.
Genau fünf Dateien: Coordinator, Preference-Inventar, dessen bestehende
Koordinationstests, PersistentStorageInventoryTest und dieses Dokument.

Der Adapter ist je zugrundeliegendem Preference-Objekt stabil wiederverwendet.
Listener-Proxys halten den eigentlichen Listener nur schwach, geben bei Aufrufen
den erfassten Zugang weiter und lassen sich über diesen wieder deregistrieren.
Technische Stores bleiben außerhalb der fachlichen Generation. Der Editor zählt
auch fehlgeschlagene Commit-/Apply-Versuche, gibt boolesche Ergebnisse unverändert
weiter und lässt bei Exceptions keine aktive Anmeldung zurück.

Die Inventarprüfung erfasst nun beide Öffnungsformen. Jede gefundene Öffnung muss
vollständig mit einem Literal oder einer vorher definierten Konstante auflösbar
sein; ein nur erkanntes bekanntes Präfix vor einer Namensverkettung genügt nicht.
Neue synthetische Klassifizierungsfälle prüfen bekannte Zugänge sowie unbekannte,
zusammengesetzte und zur Laufzeit berechnete Namen. Die Prüfung wird weiterhin
über sämtliche Kotlin-/Java-Dateien des vorhandenen Main-Quellverzeichnisses
ausgeführt; zentrale Inventarzugriffe bleiben wie zuvor gesondert behandelt und
verweigern unbekannte Namen jetzt zusätzlich zur Laufzeit.

13 ausgewählte Methoden bestanden (erster Lauf): sieben Koordinationsmethoden,
drei Inventarmethoden und drei vorhandene Preference-Backup-Regressionen. Darunter
der V1-Export über erfasstes Room, Credential-Ausschluss/Injection-Abwehr, alle
primitiven Typen und String-Sets sowie Wiederherstellung aller fachlichen
Preferences und stabilen Zuordnungen auf frischer Installation zweimal.
Exakte Methoden und Klassen, Fehler/Skips, Duplikate und Platzhalter im XML geprüft;
Ergebnisse `/tmp/immopilot-preference-coordination-results`, Protokoll
`/tmp/immopilot-preference-coordination-tests.log` (Java 21, Gradle 9.3.1,
Roborazzi-Verify). Java 17: `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`
bestanden (`/tmp/immopilot-preference-coordination-build.log`), Lint ohne Error/Fatal;
`git diff --check` bestanden. Keine zusätzlichen fachfremden Tests, kein Emulator,
Push, Merge oder CI-Eingriff.

Der Secret-/Key-Filter und die MERGE-/REPLACE-Fachregeln blieben unverändert.
Die tatsächlichen Store-Öffnungsstellen außerhalb des Inventars verwenden bisher
weiterhin ihre bisherigen Zugänge. Als nächste konkrete Anbindung sind
`TenantHistoryFeature.kt/TenantHistoryStore` und `PropertyUnitScopedData.kt`
erfasst: beide besitzen Migrationen beim Lesen sowie Setter mit getrennten
Veröffentlichungen für stabile Schlüssel und historische Aliasse. Solche Setter
benötigen eine durchgehende fachliche Schreibanmeldung über beide Editor-Aufrufe.
Eine globale Konsistenzbehauptung oder Aktivierung erfolgt hier weiterhin nicht.

### Schritt 4: Mietverlauf und Einheitenwerte anbinden

Konkrete nächste Grenzen: `TenantHistoryFeature.kt/TenantHistoryStore` sowie
`PropertyUnitScopedData.kt/selectedPropertyId`, `rentValue`, `setRentValues`.
Ihre bekannten Preference-Öffnungen werden über das Inventar geführt. Die Setter
halten eine Anmeldung über kanonische und historische Editor-Veröffentlichungen;
Migrationen beim Lesen werden erst bei tatsächlicher Veröffentlichung gezählt.
Schlüssel, IDs, Beträge, Validierungen und Aliasregeln bleiben unverändert.
Gezielte Tests prüfen tatsächliche Migrationen und einen beobachteten Zwischenraum
zwischen den beiden Editor-Aufrufen, ergänzt um vorhandene Identitätsregressionen.
Vier Dateien: die beiden Schreibstellen, Coordinator-Test und dieses Dokument.

Abnahme: fünf ausgewählte Methoden bestanden, ohne Fehler/Skips/Platzhalter;
exakte XML-Namen geprüft, Ergebnisse `/tmp/immopilot-domain-preferences-results`.
Protokoll `/tmp/immopilot-domain-preferences-tests.log`. Java 21, Gradle 9.3.1,
Roborazzi-Verify; Java 17: lintDebug, assembleDebug, assembleDebugAndroidTest
bestanden (`/tmp/immopilot-domain-preferences-build.log`), Lint ohne Error/Fatal,
git diff --check bestanden. Die neuen Tests beobachten echte Store-Migrationen
und die Aliasabfrage zwischen zwei Editor-Veröffentlichungen; dort ist die
Erfassung gesperrt. Bereits kanonische Reads ändern die Generation nicht.

### Schritt 4: weitere einzelne Preference-Veröffentlichungen

Nächste konkret benannte Grenzen: AiProviderSettings (Selection, Key-Speicherung
und Reset), RenovationReviewStore (saveMeasure/saveRelation), ConfirmedAfaValuesStore
(load/save) und AnnualTaxAssistantFeature (Freigaben sowie zugehörige Lesezugänge).
Alle dortigen Öffnungen verwenden den erfassten Inventarzugang. Die jeweiligen
Veröffentlichungen erfolgen bereits über einzelne Editor-Commits/Apply-Ketten;
Cipher, Schlüsselbehandlung, Berechnungen, Steuersachlogik und UI bleiben gleich.
Fünf Dateien einschließlich dieses Dokuments. Prüfung über Inventarguard,
Provider-Key-Sicherheit und bestehende Sanierungs-/AfA-Persistenzregressionen.


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

Abnahme dieser vier Preference-Anbindungen: acht gezielte Methoden bestanden
(Provider-Key-Sicherheit, Sanierungspersistenz, AfA-/Credential-Backup und
Inventarguard), XML ohne Fehler/Skips/Duplikate geprüft. Ergebnisse:
`/tmp/immopilot-settings-writers-results`, Testprotokoll
`/tmp/immopilot-settings-writers-tests.log`. `lintDebug`, `assembleDebug` und
`assembleDebugAndroidTest` bestanden, Lint ohne Error/Fatal; Buildprotokoll
`/tmp/immopilot-settings-writers-build.log`. Kein GitHub-Upload.

### Schritt 4: Aufgaben, Darlehenszuordnungen und Einheiten-Metadaten

Nächste fünf Dateien: PropertyTaskFeature, LoanFeature, UnifiedUnitsFeature,
DatevProfileService und dieses Dokument. Die bekannten Preference-Zugänge
werden über das Inventar geöffnet. Der bestehende Statusdialog veröffentlicht
Mietverlauf, Einheitenstatus und Statusdatum zusammen unter einer logischen
Schreibanmeldung; seine Validierungen und Werte bleiben unverändert. Die
asynchronen ViewModel-/Cloud-Pfade werden anschließend separat angebunden.
Prüfung: Inventarguard, vollständige Preference-Wiederherstellung mit stabilen
Zuordnungen und Aktualisierung des bestehenden ViewModels nach Restore.

Abnahme: alle drei ausgewählten Methoden bestanden; exakte XML-Namen,
Fehlerfreiheit und keine Skips/Duplikate geprüft. XML:
`/tmp/immopilot-unit-settings-results`; Tests:
`/tmp/immopilot-unit-settings-tests.log`. `lintDebug`, `assembleDebug`,
`assembleDebugAndroidTest` bestanden (`/tmp/immopilot-unit-settings-build.log`),
Lint ohne Error/Fatal, `git diff --check` bestanden. Kein Push.

### Schritt 4: weitere Zugänge in Dokumenten- und Drive-Pfaden

Fünf Dateien: GoogleDriveClient, ManagedDocumentBookingPreparation,
OfflineBackupService, BankFeature und dieses Dokument. Ausschließlich bekannte
Preference-Öffnungen auf den Inventarzugang umstellen; Cache-Zugänge des
Drive-Clients bleiben ausdrücklich Cache. Keine Bank-/DATEV-Fachlogik ändern.
Prüfung über Inventarguard, portablen V1-Roundtrip mit Beziehungen und
wiederholte Dokumentenvorbereitung ohne doppelte Belege oder Originalindizes.

Abnahme: drei gezielt ausgewählte Methoden bestanden; XML auf exakte
Namen und keine Fehler/Skips/Duplikate geprüft und unter
`/tmp/immopilot-document-preferences-results` gesichert. Testprotokoll:
`/tmp/immopilot-document-preferences-tests.log`. `lintDebug`, `assembleDebug`
und `assembleDebugAndroidTest` bestanden; Lint ohne Error/Fatal. Build:
`/tmp/immopilot-document-preferences-build.log`. `git diff --check` bestanden.

### Schritt 4: Preference-Zugänge schließen und Umgehungen erkennen

Vier Dateien: DrivePersistenceRepository, ReceiptViewModel,
PersistentStorageInventoryTest und dieses Dokument. Die verbleibenden sieben
fachlichen Öffnungen im Drive-Repository und neun im ViewModel werden über
PersistentPreferenceInventory.open geführt. Technische LOCAL_ONLY-Journale
bleiben gesondert. Der Inventarguard lehnt zusätzlich direkte Öffnungen aller
BACKUP-Stores ab; ein gezielter Vorab-Lauf muss die bestehenden Umgehungen
erkennen, bevor diese umgestellt werden. Werte, Schlüssel, Restore-Modi und
Fachlogik bleiben unverändert. Anschließend Inventarguard und bestehende
Preference-/Identitätsregressionen prüfen.

Vorab-Nachweis: genau zwei Guard-Methoden ausgeführt, eine gezielte Assertion
in everySharedPreferenceOpeningMustResolveToAnExplicitClassification:
`DrivePersistenceRepository.kt: wohneinheiten_prefs umgeht die
Snapshot-Schreibkoordination`. Die zweite Methode prüfte erfolgreich alle
BACKUP-Stores gegen direkte Öffnung und den Inventarzugang. Keine Skips oder
unerwarteten Fehler. Belege: `/tmp/immopilot-final-preferences-red.xml` und
`/tmp/immopilot-final-preferences-red.log`. Anschließend ausschließlich die
16 dokumentierten Öffnungen umgestellt; kein Wiederholungsversuch zum
Verdecken eines Fehlers.

Abnahme: sieben exakt ausgewählte Methoden bestanden, XML-Namen und keine
Fehler/Skips/Duplikate geprüft. Ergebnisse:
`/tmp/immopilot-final-preferences-results`, Testprotokoll:
`/tmp/immopilot-final-preferences-tests.log`. Der Umgebungsneustart beendete
den ersten Build vor Task-Ausführung; keine Tests wiederholt. Der danach
gestartete Prüflauf bestand `lintDebug`, `assembleDebug` und
`assembleDebugAndroidTest` (`/tmp/immopilot-final-preferences-build-resumed.log`),
Lint ohne Error/Fatal; `git diff --check` bestanden. Alle derzeit inventarisierten
fachlichen Preference-Öffnungen verwenden nun die koordinierte Factory. Das
ersetzt weiterhin nicht die noch offene Anmeldung mehrteiliger Operationen.

### Schritt 4: asynchrone Übergabe bei Einheitenänderungen

Vier Dateien: OfflineBackupCoordinator, dessen Test, ReceiptViewModel und
dieses Dokument. Coroutine-Mutationen werden vor dem Einplanen angemeldet
und erst nach Abschluss aller strukturierten Kinder freigegeben; auch Abbruch
vor Eintritt und LAZY-Aufträge müssen die Anmeldung freigeben. Die drei
Schreib-/Cloud-Aufträge in updateWohneinheitForProperty verwenden diesen
Zugang. updateWohneinheit und updateWohneinheitForProperty halten zusätzlich
die synchrone Übergabe über Mietverlauf, Preferences und Einplanung zusammen.
Prüfung: beobachtete Warteschlange, Suspendierung, Kinder und Abbruch sowie
bestehende Einheiten-/Identitätsregressionen. Andere ViewModel-Aufträge
werden im nächsten Teilschritt geprüft und angebunden.

Vorab: genau der Warteschlangen-Test schlug erwartungsgemäß fehl, weil
vor Eintritt keine OfflineBackupValidationException ausgelöst wurde. Keine
Zeitüberschreitung oder Infrastrukturursache. Belege:
`/tmp/immopilot-coroutine-writers-red.xml` und
`/tmp/immopilot-coroutine-writers-red.log`. Danach Anmeldung vor launch
und Freigabe per Job.invokeOnCompletion ergänzt; anschließend die drei
Einheiten-Aufträge und ihre synchrone Übergabe angebunden.

Abnahme: acht exakt ausgewählte Methoden bestanden (drei neue Coroutine-,
zwei bestehende Domain-Koordinations-, zwei Einheiten-/Backup- und ein
Inventartest). XML ohne Fehler/Skips/Duplikate geprüft und unter
`/tmp/immopilot-coroutine-writers-results` gesichert. Testprotokoll:
`/tmp/immopilot-coroutine-writers-tests.log`. Der bekannte KSP-AWT-Threadfehler
beendete weder Compiler noch Tests; Gradle und XML bestanden. `lintDebug`,
`assembleDebug`, `assembleDebugAndroidTest` bestanden
(`/tmp/immopilot-coroutine-writers-build.log`), Lint ohne Error/Fatal;
`git diff --check` bestanden. Der ViewModel-Diff wurde zusätzlich nach
Rücknahme ausschließlich der Koordinationshüllen und Einrückungen exakt
gegen die unveränderte fachliche Ausgangslogik verglichen. Kein Push.

### Schritt 4: mehrteilige Einheitenmigration beim Lesen

Drei Dateien: ReceiptViewModel, OfflineBackupViewModelSnapshotTest und dieses
Dokument. loadUnitById kopiert ältere Felder bisher über mehrere Editoren.
Die Anmeldung beginnt erst bei der ersten tatsächlich erforderlichen Kopie
und bleibt bis zum Ende des Lese-/Migrationsvorgangs bestehen. Kanonische
Reads ohne Veröffentlichung bleiben unverändert ruhig. Ein echter ViewModel-
Test beobachtet die Statusabfrage zwischen erster und zweiter Feldkopie;
Robolectric-Application delegiert sämtliche Preference-Daten an echte Stores.
Keine Änderung der Schlüssel, Werte, Aliasregeln oder Mietberechnungen.

Vorab-Nachweis: Der neue Test erreichte tatsächlich die Abfrage zwischen
den Editoren und meldete `expected:<[true]> but was:<[false]>`: Erfassung
war dort noch erlaubt. Keine Timeout-/Initialisierungsursache. XML:
`/tmp/immopilot-unit-read-migration-red.xml`; Protokoll:
`/tmp/immopilot-unit-read-migration-red.log`. Danach eine erst bei
tatsächlicher Veröffentlichung beginnende Anmeldung ergänzt, Freigabe
auch bei Ausnahme/Return im finally. Alle vier Kopierstellen nutzen sie.

Offener Hinweis für den vollständigen Snapshot-/Restore-Vertrag: Die V1-
Schemasuche lässt `sqlite_*` aus. Für REPLACE_FULL sind neben den expliziten
Zeilen-IDs auch ursprüngliche AUTOINCREMENT-Höchststände (`sqlite_sequence`)
zu erfassen und zu prüfen, damit nach archivierten Löschungen keine alten
Kennungen erneut vergeben werden. Dies ist noch kein implementierter
V2-Transport; keine Änderung an Room-Schema oder Migrationen.

Abnahme: fünf ausgewählte Methoden bestanden, exakte XML-Namen und keine
Fehler/Skips/Duplikate geprüft (`/tmp/immopilot-unit-read-migration-results`;
`/tmp/immopilot-unit-read-migration-tests.log`). Der neue tatsächliche
ViewModel-Test prüft den beobachteten Editor-Zwischenraum, unveränderte
Kennung/Werte/Legacy-Daten und den ruhigen anschließenden kanonischen Read.
`lintDebug`, `assembleDebug`, `assembleDebugAndroidTest` bestanden
(`/tmp/immopilot-unit-read-migration-build.log`), Lint ohne Error/Fatal,
`git diff --check` bestanden. Fachliche Ausgangslogik nach Abzug allein der
Anmeldung/Hüllen/Einrückungen exakt verglichen. Kein GitHub-Upload.

### Schritt 4: weitere endliche ViewModel-Mutationen

Drei Dateien: ReceiptViewModel, OfflineBackupViewModelSnapshotTest und dieses
Dokument. Mutierende Aufträge in Import, Bearbeitung, Bankzuordnung, Export,
Löschen, Reparatur sowie Drive-/Firestore-Synchronisation werden vor der
Einplanung angemeldet und bleiben bis zum Jobabschluss erfasst. Reine
Such-/Analyseanzeigen und der unbegrenzte propertyMetadata-Collector bleiben
Leseaufträge. Der Integrationstest hält Room vor dem ersten Datenbankzugriff
einer echten createProperty-Anforderung an und prüft dort die Sperre. Nach
Freigabe müssen stabile Einheiten-ID, Room-Zeile, Preferences und Auswahl
vollständig veröffentlicht sein. Nur synthetische In-Memory-Daten.
Die hier ausgewählten Robolectric-Klassen deklarieren SDK 35; für diese
eng gefilterten Läufe genügt Java 17 (keine globale SDK-Erlaubnisliste).
Java 21 bleibt für ausgewählte SDK-36-Fälle erforderlich.

Vorab: genau die neue createProperty-Integrationsmethode scheiterte mit
fehlender OfflineBackupValidationException vor Room-Eintritt; der Test
ließ den angehaltenen Auftrag im finally vollständig fertiglaufen. Keine
Timeout-/Infrastrukturursache, keine Skips. Belege:
`/tmp/immopilot-viewmodel-mutations-red.xml` und
`/tmp/immopilot-viewmodel-mutations-red.log` (Java 17, SDK 35).

Angebunden: 88 weitere Coroutine-Starts in 88 ausdrücklich
zugeordneten mutierenden Funktionen. Vollständige Liste dieser Anbindungen:

- `ReceiptViewModel.acceptBankRule`
- `ReceiptViewModel.analyzeManagedDocument`
- `ReceiptViewModel.analyzeReceipt`
- `ReceiptViewModel.analyzeReceiptDuplicates`
- `ReceiptViewModel.applyBankBatchAction`
- `ReceiptViewModel.applyDescriptionBackfill`
- `ReceiptViewModel.backfillPaymentMethods`
- `ReceiptViewModel.changePhase2DAllocation`
- `ReceiptViewModel.checkForDriveRestore`
- `ReceiptViewModel.classifyBankTransaction`
- `ReceiptViewModel.clearAll`
- `ReceiptViewModel.clearAllLearnedRules`
- `ReceiptViewModel.confirmBankAccountImport`
- `ReceiptViewModel.confirmBankLoanAssignment`
- `ReceiptViewModel.confirmBankReceiptLink`
- `ReceiptViewModel.confirmBankRentSuggestion`
- `ReceiptViewModel.confirmBankTransferPair`
- `ReceiptViewModel.confirmDocumentStorageMigration`
- `ReceiptViewModel.confirmDuplicateMerge`
- `ReceiptViewModel.confirmManagedDocumentReview`
- `ReceiptViewModel.confirmManualBankRentAssignment`
- `ReceiptViewModel.confirmManyBankTransactionsForReceipt`
- `ReceiptViewModel.confirmMetadataDuplicateCleanup`
- `ReceiptViewModel.confirmPhase2DCombination`
- `ReceiptViewModel.confirmWholeDuplicateGroupDeletion`
- `ReceiptViewModel.correctReceiptMetadata`
- `ReceiptViewModel.createProperty`
- `ReceiptViewModel.deleteBankRule`
- `ReceiptViewModel.deleteLearnedRule`
- `ReceiptViewModel.deleteProperty`
- `ReceiptViewModel.deleteReceipt`
- `ReceiptViewModel.deleteStandardRoute`
- `ReceiptViewModel.diagnoseAndFixAllReceipts`
- `ReceiptViewModel.downloadManagedDocument`
- `ReceiptViewModel.downloadReceiptDocument`
- `ReceiptViewModel.ensureManagedDocumentReady`
- `ReceiptViewModel.executePhase2DSafeBatch`
- `ReceiptViewModel.executeWizardExport`
- `ReceiptViewModel.exportRenovationReport`
- `ReceiptViewModel.forceDocumentDownload`
- `ReceiptViewModel.importBankFile`
- `ReceiptViewModel.importManagedDocument`
- `ReceiptViewModel.initializeDrivePersistence`
- `ReceiptViewModel.insertReceiptQuietly`
- `ReceiptViewModel.learnVendorRule`
- `ReceiptViewModel.markBankTransactionForReview`
- `ReceiptViewModel.markBankTransactionNoReceiptRequired`
- `ReceiptViewModel.performFullRestoreConfirmation`
- `ReceiptViewModel.permanentlyDeleteReceipt`
- `ReceiptViewModel.proposeBankRuleFromConfirmedReceipt`
- `ReceiptViewModel.proposeBankRuleFromRecurringPattern`
- `ReceiptViewModel.rejectBankRule`
- `ReceiptViewModel.rejectBankRuleMatch`
- `ReceiptViewModel.removeBankReceiptLink`
- `ReceiptViewModel.reopenBankTransaction`
- `ReceiptViewModel.repairReceiptDocument`
- `ReceiptViewModel.requestDuplicateMerge`
- `ReceiptViewModel.requestWholeDuplicateGroupDeletion`
- `ReceiptViewModel.resetLocalReceiptData`
- `ReceiptViewModel.resolvePossibleDocumentDuplicate`
- `ReceiptViewModel.restoreReceipt`
- `ReceiptViewModel.resumeDuplicateCleanupOperation`
- `ReceiptViewModel.runDocumentOcr`
- `ReceiptViewModel.runOriginalReceiptAudit`
- `ReceiptViewModel.runRealDriveTest`
- `ReceiptViewModel.runRestoreDryRun`
- `ReceiptViewModel.saveBankRule`
- `ReceiptViewModel.saveReceipt`
- `ReceiptViewModel.saveRecurringPattern`
- `ReceiptViewModel.saveRenovationMeasure`
- `ReceiptViewModel.saveRenovationRelation`
- `ReceiptViewModel.setBankRuleEnabled`
- `ReceiptViewModel.setRecurringPatternEnabled`
- `ReceiptViewModel.syncAllToDrive`
- `ReceiptViewModel.syncManagedDocumentNow`
- `ReceiptViewModel.syncReceiptManually`
- `ReceiptViewModel.syncWithCloud`
- `ReceiptViewModel.undoLastBankBatchAction`
- `ReceiptViewModel.unlinkBankLoanAssignment`
- `ReceiptViewModel.unlinkBankRentAssignment`
- `ReceiptViewModel.unlinkBankTransferPair`
- `ReceiptViewModel.unlinkPhase2DLink`
- `ReceiptViewModel.updateBankLoanSplit`
- `ReceiptViewModel.updateBankTransactionReviewState`
- `ReceiptViewModel.updateManagedDocumentPresentation`
- `ReceiptViewModel.updatePropertyMetadata`
- `ReceiptViewModel.updateReceipt`
- `ReceiptViewModel.uploadReceiptToDriveInternal`

Die unveränderten Lese-/Anzeige- und Übergabeaufträge sind explizit
klassifiziert; init-Feld-Refresh, propertyMetadata-Collector und Starttimer
gehören dazu. Timer, Consent- und Anmeldeübergaben starten ihre tatsächlichen
Mutationen über die jeweils angemeldeten Methoden. Alle ausstehenden
viewModelScope.launch-Stellen sind so zugeordnet. Nach Rücknahme nur dieser
Launch-Ersetzungen stimmt die gesamte Quelldatei exakt mit dem Ausgangsstand
überein; keine fachlichen Bodies oder Parameter verändert.

Abnahme: acht ausgewählte Methoden unter Java 17/SDK 35 bestanden,
exakte XML-Namen und keine Fehler/Skips/Duplikate geprüft
(`/tmp/immopilot-viewmodel-mutations-results`,
`/tmp/immopilot-viewmodel-mutations-tests.log`). Der angehaltene tatsächliche
Immobilien-Auftrag ist vor Datenbankeintritt angemeldet; nach Freigabe sind
Room-Zeile, stabile Einheit, Preferences und Auswahl vollständig vorhanden.
`lintDebug`, `assembleDebug`, `assembleDebugAndroidTest` bestanden
(`/tmp/immopilot-viewmodel-mutations-build.log`), Lint ohne Error/Fatal,
`git diff --check` bestanden. Keine Änderung an SDK-Auswahl oder Prüfregeln.

### Separater Inventarschritt: inaktiver Legacy-Erzeugerpfad

Der Backend-Prüflauf bestand zehn Methoden und zeigte einen bereits bestehenden
Inventarfehler: Der Quelltest verlangte weiterhin eine aktive Öffnung von
`filesDir/restored_receipts`. Auch am unveränderten Head `c6fef75` gibt es keine
solche produktive Quellstelle (`rg`); Historie `3673862` ersetzte den alten
Downloadpfad durch neue Versionen in managed_documents. Die Backend-Änderungen
sind deshalb unverändert in einem benannten lokalen Stash gesichert und dieser
Prüfvertrag wird als eigener Dreidateien-Schritt bearbeitet, bevor die fünf
Backend-Dateien fortgeführt werden.

`restored_receipts` bleibt bewusst klassifizierter Legacy-Datenbestand.
Lediglich seine Erzeugerwurzel ist ausdrücklich inaktiv. Die Gleichheitsprüfung
verlangt weiterhin genau alle aktiven Quellwurzeln; unbekannte Zugriffe, neue
DataStores und eine unkommentierte Reaktivierung des alten Pfads werden weiterhin
abgelehnt. Keine Absenkung auf eine bloße Teilmengenprüfung. Ein neuer Service-
Roundtrip legt das einzige referenzierte Original in den alten Ordner, entfernt
nur dessen synthetische frühere Kopie und prüft Export sowie zweimaliges Restore
mit unveränderten Bytes, stabilen IDs und Beziehungen. Keine Nutzerdaten berührt.

Abnahme des getrennten Inventarschritts: drei exakt ausgewählte Methoden
bestanden; XML ohne Fehler/Skips/Duplikate geprüft und unter
`/tmp/immopilot-retired-root-inventory-results` gesichert. Tests:
`/tmp/immopilot-retired-root-inventory-tests.log`. Der Legacy-Roundtrip erhält
Originalbytes, stabile IDs und Beziehungen auch beim zweiten Restore.
`lintDebug`, `assembleDebug`, `assembleDebugAndroidTest` bestanden
(`/tmp/immopilot-retired-root-inventory-build.log`), Lint ohne Error/Fatal;
`git diff --check` bestanden. Ausschließlich zwei Testdateien und Dokument
geändert; Backend-Fortsetzung folgt mit unveränderten gesicherten Dateien.

### Schritt 4: Repository- und Originalspeicher-Grenzen

Fünf Dateien: ReceiptDatabase (ReceiptRepository), ReceiptOriginalStorage,
ManagedDocumentService, OfflineBackupCoordinatorTest und dieses Dokument.
Mehrteilige Repository-Veröffentlichungen werden vom ersten bis zum letzten
DAO-Aufruf angemeldet; direkte Aufrufe müssen denselben Schutz besitzen.
Original-Import/-Download meldet Staging, Veröffentlichung und Aufräumen an.
ManagedDocumentService hält mehrphasige Import-/OCR-/Analyse-/Review-Vorgänge
zusammen; Dateischreiben wird vor Metadatenveröffentlichung synchronisiert.
Prüfung: reale Unterbrechung nach Commit der Belegzeile vor Projektion,
Dateiveröffentlichungen sowie bestehende Originalversions-/Dokumenttests.

Vorab: beide neuen Methoden scheiterten erwartungsgemäß an fehlender
OfflineBackupValidationException. Beim Repository war die Belegzeile bereits
committed, die Projektion noch nicht geschrieben; beim Dateitest war eine
Downloadversion vollständig veröffentlicht. Keine Skips/Timeouts/unerwarteten
Fehler (`/tmp/immopilot-backend-writers-red.xml`,
`/tmp/immopilot-backend-writers-red.log`). Danach neun Repository-, drei
Originalspeicher- und vier ManagedDocumentService-Grenzen angebunden.
Importanmeldung im privaten persistPrepared umfasst Datei und Upsert;
reines URI-Lesen und Duplikatvorprüfung ändern keinen Bestand. Der Dateistream
wird vor dem Upsert per fd.sync synchronisiert. Abgesehen davon wurden alle
ursprünglichen Funktions-Bodies nach Abzug von Hüllen/Einrückungen exakt
verglichen. Keine fachlichen Rückgaben, Werte oder Aufrufparameter geändert.

Backend-Fortsetzung: die vier gesicherten Quelldateien wurden nach dem
separaten Inventar-Commit bytegenau aus dem eigenen Stash wiederaufgenommen;
die korrigierte Legacy-Klassifikation und ihr zusätzlicher Test bleiben
erhalten. Erneute gezielte Abnahme mit dem korrigierten Inventarguard folgt.

Abnahme der Backend-Fortsetzung: zwölf exakt ausgewählte Methoden bestanden,
XML ohne Fehler/Skips/Duplikate geprüft
(`/tmp/immopilot-backend-writers-verified-results`,
`/tmp/immopilot-backend-writers-verified-tests.log`). Der zuvor fehlgeschlagene
Inventarfall wurde in `5f57308` mit Legacy-Roundtrip separat behoben; sämtliche
ursprünglichen Backend-Funktions-Bodies blieben währenddessen unverändert
gesichert. Aktuell bestehen Repository-Zwischenraum, Original-Publikation,
alle fünf Versions-/Fehlerfälle, tatsächlicher Fünf-Format-Import, geprüfter
Dokument-Restore, beide Inventarguards und Legacy-Roundtrip.
`lintDebug`, `assembleDebug`, `assembleDebugAndroidTest` bestanden
(`/tmp/immopilot-backend-writers-build.log`), Lint ohne Error/Fatal;
`git diff --check` bestanden. Kein Push und kein Emulatorlauf.

### Schritt 4: vollständige Drive-Original-Downloads anmelden

Drei Dateien: DrivePersistenceRepository, DrivePersistenceManagedDocumentE2eTest
und dieses Dokument. downloadDocumentOnDemand sowie downloadReceiptOriginals
halten die Anmeldung von der Auflösung über sämtliche Dateiveröffentlichungen
bis zur letzten Dokument-/Belegreferenz. Ein direkter Backend-Aufruf mit zwei
Originalseiten beobachtet die zweite Remote-Abfrage: Die erste neue Dateiversion
ist bereits vorhanden, die fachlichen Referenzen sind noch unverändert. Genau
in diesem Zwischenraum muss die Erfassung gesperrt bleiben. Nur der Transport
ist ein vorhandener Fake; Originalspeicher, Repository und Room sind echt.

Vorab-Nachweis: Der tatsächliche erste Download war abgeschlossen und
die Referenzen noch alt; bei zweiter Seitenabfrage blieb Erfassung möglich
(`expected:<true> but was:<false>`). Genau eine neue Methode ausgeführt,
keine Skips/Timeouts (`/tmp/immopilot-drive-download-coordination-red.xml`,
`/tmp/immopilot-drive-download-coordination-red.log`). Danach beide
Download-Funktionen über den gesamten Ablauf angemeldet. Ursprüngliche
Funktions-Bodies nach Abzug nur der Hüllen/Einrückung exakt verglichen;
keine Änderung von Referenzreihenfolge, Validierungen oder Rückgaben.

Abnahme: neun gezielte Methoden bestanden; exakte XML-Namen und keine
Fehler/Skips/Duplikate geprüft
(`/tmp/immopilot-drive-download-coordination-results`,
`/tmp/immopilot-drive-download-coordination-tests.log`). Der beobachtete
Zwischenraum ist nun gesperrt; vorherige Bytes und Referenzen bleiben bei
fehlenden/defekten Seiten erhalten. Nur Leerraum auf Leerzeilen der beiden
geänderten Methoden nach dem Testlauf bereinigt; kein Verhalten verändert.
`lintDebug`, `assembleDebug`, `assembleDebugAndroidTest` bestanden
(`/tmp/immopilot-drive-download-coordination-build.log`), Lint ohne Error/Fatal;
`git diff --check` bestanden. Kein Push und kein Emulatorlauf.

### Schritt 4: zusammenhängende Restore-Veröffentlichungen

Fünf Dateien: PersistentPreferenceInventory, SupplementalDriveBackup,
OfflineBackupService, OfflineBackupCoordinatorTest und dieses Dokument.
Die bestehenden restorePayload-/restore-Aufrufe melden alle ihre Datei-,
Room- und Preference-Phasen gemeinsam an. Keine Änderung an MERGE/REPLACE,
Validierung, Reihenfolge oder bisherigen Fehler-/Rollbackpfaden; das dauerhafte
Wiederanlaufjournal und der Wartungsmodus aus Schritt 8 bleiben eine spätere,
gesonderte Implementierung. Drei reale Beobachtungen prüfen den Zwischenraum
zwischen Preference-Stores sowie den Eintritt vor Objektbild-/Originalspeicher-
Veröffentlichung. Kontextprobes beobachten nur; Speicher und Room bleiben echt.

Vorab: alle drei neuen Methoden erreichten ihre tatsächlichen Grenzen
und scheiterten mit `expected:<[true]> but was:<[false]>`, ohne zusätzliche
Fehler/Skips/Timeouts (`/tmp/immopilot-restore-publications-red.xml`,
`/tmp/immopilot-restore-publications-red.log`). Danach jeweils die komplette
Restore-Funktion angemeldet. Sämtliche ursprünglichen Bodies und unveränderten
Dateibereiche nach Abzug nur der Hülle/Einrückungen exakt verglichen.

Prüfumgebung nachträglich gegen die Projektkonfiguration abgeglichen:
PreferenceBackupCompletenessTest verwendet AndroidJUnit4 ohne SDK-Override;
app/src/test/resources/robolectric.properties legt standardmäßig SDK 35 fest.
Die damalige Annahme eines Standard-SDK 36 war falsch. Der Java-17-Start wurde
deshalb unnötig, aber vor testDebugUnitTest beendet (Client 143; keine Methode
lief). Der Folgelauf mit Java 21 führte dieselben zwölf Fälle mit ihren echten
SDKs vollständig aus. Keine SDK-Erlaubnisliste oder Teständerung. Explizite
SDK-36-Klassen, etwa RestoreSafetyTest, benötigen weiterhin Java 21.

Der anschließende Umgebungsneustart beendete den Java-21-Prozess nach der
Testkompilierung und vor der testDebugUnitTest-Aufgabe. Kein Gradle-Prozess
blieb aktiv und es lag noch kein Ergebnis dieses Laufs vor. Derselbe eng
gefilterte Prüflauf wird nach Wiederverfügbarkeit fortgesetzt; Infrastruktur-
unterbrechung, keine ausgeblendete Testfehlermeldung.

Abnahme: zwölf exakt ausgewählte Methoden bestanden; XML ohne Fehler,
Skips oder Duplikate geprüft (`/tmp/immopilot-restore-publications-results`,
`/tmp/immopilot-restore-publications-verified-tests-resumed.log`). Alle drei
vorher beobachteten Grenzen sind gesperrt; Roundtrip, Konfliktabbruch,
Objektbilder, ungültige Payloads, Credentials, höhere Schemaversion und
vollständige Preferences bestehen weiterhin. `lintDebug`, `assembleDebug`,
`assembleDebugAndroidTest` bestanden
(`/tmp/immopilot-restore-publications-build.log`), Lint ohne Error/Fatal,
`git diff --check` bestanden. Keine neue Journal-/Wartungssemantik in diesem
Teilschritt, kein GitHub-Upload und kein Emulatorlauf.

### Schritt 4: weitere Drive-Mutationsgrenzen

Drei Dateien: DrivePersistenceRepository, DrivePersistenceManagedDocumentE2eTest
und dieses Dokument. Die verbleibenden mutierenden Backend-Einstiege für
Initialisierung/Import, Stammdaten, Sync/Index, Löschen/Restore, Originalreparatur
und Dokumentmigration werden vor ihrer Persistenzphase gemeinsam angemeldet.
Reine Inventar-, Vorschau-, Parser- und Lesewege bleiben Lesewege. Ein direkter
Preference-Mutationsaufruf prüft die frühzeitige Anmeldung bereits beim Öffnen
des Stores; zahlreiche bestehende Fake-Drive-Regressionen sichern die bisherige
Fach- und Konfliktsemantik. Netzwerk-/Ordnerlogik wird dabei nicht geändert.

Vor dem Schreiben weiterer Dateien wird dieser Drive-Schritt auf fünf Dateien
erweitert und vollständig abgegrenzt: zusätzlich RestoreSafety und der vorhandene
Coordinator-Test. FullRestoreCoordinator.execute hält auch die Übergabe zwischen
fertigem Core-Import und beginnendem Zusatzimport zusammen. Der Test liest dort
die bereits geänderte echte Belegzeile und die noch alten Preferences und prüft
die Erfassungssperre; nur die äußeren Drive-Ergebnisobjekte sind synthetisch.
Ein zweites paralleles Restore-Journal wird dadurch nicht eingeführt.

Vorab: der direkte Preference-Aufruf erreichte den Store ohne Anmeldung
(`expected:<[true]> but was:<[false]>`). Der zusätzliche tatsächliche
Core-/Supplemental-Übergang war ebenfalls offen (`expected:<true> but
was:<false>`); geänderte Core-Zeile und alte Preferences wurden davor
erfolgreich beobachtet. Keine Skips/Timeouts oder unerwarteten Fehler.
Belege: `/tmp/immopilot-drive-mutations-red.xml`,
`/tmp/immopilot-drive-restore-handoff-red.xml` und zugehörige .log-Dateien.

Angebunden sind 37 weitere Drive-Funktions-/Gate-Grenzen sowie
FullRestoreCoordinator.execute. Bei Ausdrucksbodies beginnt die Anmeldung
im bestehenden Mutex-/Sync-Gate-/Trace-Block vor der Persistenzphase; diese
Gate-Semantik bleibt erhalten. Queued ViewModel-Anforderungen besitzen
bereits ihre äußere Anmeldung. GetDriveAppConfig ist ausdrücklich keine
reine Leseoperation: es delegiert an die angemeldete Initialisierung.
GetExistingDriveAppConfigReadOnly, Inventar/Vorschau und Parser bleiben
Lesewege. Privat-Metadaten-Upsert ist innerhalb seines Trace-Blocks angemeldet;
sein öffentlicher Overload delegiert unverändert dorthin. Vollständige Liste:

- `DrivePersistenceRepository.saveWohneinheitenToPrefs`
- `DrivePersistenceRepository.saveLearnedRulesToPrefs`
- `DrivePersistenceRepository.initializeDriveStorage`
- `DrivePersistenceRepository.initializeDriveStorageForSync`
- `DrivePersistenceRepository.initializeDriveStorageLocked`
- `DrivePersistenceRepository.saveStammdatenToDrive`
- `DrivePersistenceRepository.restoreStammdatenFromDrive`
- `DrivePersistenceRepository.saveDriveAppConfig`
- `DrivePersistenceRepository.updateReceiptIndexInDrive`
- `DrivePersistenceRepository.syncReceiptOriginalAttachments`
- `DrivePersistenceRepository.validateSynchronizationInventory`
- `DrivePersistenceRepository.syncReceiptToDrive`
- `DrivePersistenceRepository.getOrCreateDeletionsFolder`
- `DrivePersistenceRepository.uploadTombstone`
- `DrivePersistenceRepository.deleteReceiptWithSync`
- `DrivePersistenceRepository.processPendingDeletions`
- `DrivePersistenceRepository.restoreDeletedReceipt`
- `DrivePersistenceRepository.permanentlyDeleteReceipt`
- `DrivePersistenceRepository.executeFullDriveRestore`
- `DrivePersistenceRepository.restoreReceiptOriginalMetadata`
- `DrivePersistenceRepository.auditOriginalReceipts`
- `DrivePersistenceRepository.correctReceiptMetadata`
- `DrivePersistenceRepository.runFullRestoreEndToEndTest`
- `DrivePersistenceRepository.restoreReceiptsFromDrive`
- `DrivePersistenceRepository.retireLegacyDriveDiagnostic`
- `DrivePersistenceRepository.testDriveReceiptStorage`
- `DrivePersistenceRepository.upsertReceiptMetadata`
- `DrivePersistenceRepository.uploadReceiptMetadata`
- `DrivePersistenceRepository.repairAndUploadOriginalDocument`
- `DrivePersistenceRepository.replaceReceiptIndexEntries`
- `DrivePersistenceRepository.ensureDocumentFolder`
- `DrivePersistenceRepository.ensurePropertyDocumentStructure`
- `DrivePersistenceRepository.syncManagedDocumentToDrive`
- `DrivePersistenceRepository.acknowledgeOriginalBackupChanges`
- `DrivePersistenceRepository.updateManagedDocumentIndex`
- `DrivePersistenceRepository.executeReceiptDocumentMigration`
- `DrivePersistenceRepository.resumeInterruptedDocumentMigration`
- `FullRestoreCoordinator.execute`

Alle ursprünglichen Bodies nach Entfernen nur der äußeren Anmeldung bytegenau
verglichen; keine Triple-Quote-Literale in diesen geänderten Bodies. Keine
Änderungen an Parametern, Ergebnissen, Netzwerkprotokoll, Konfliktlogik oder
Restore-Reihenfolge. Es werden weder neue Journale noch neue Replay-Wege gebaut.

Abnahme: sechzehn exakt ausgewählte Methoden bestanden; XML ohne Fehler,
Skips oder Duplikate geprüft (`/tmp/immopilot-drive-mutations-results`,
`/tmp/immopilot-drive-mutations-tests.log`). Erfolgs-/Konflikt-/Hash-/404-/429-/
Timeoutfälle, vorhandene Restore-Sicherheitsfälle und beide Inventarguards
bestehen. Der Core-/Supplemental-Zwischenraum ist nun gesperrt.
`lintDebug`, `assembleDebug`, `assembleDebugAndroidTest` bestanden
(`/tmp/immopilot-drive-mutations-build.log`), Lint ohne Error/Fatal;
`git diff --check` bestanden. Alle 38 alten Bodies bytegenau erhalten,
37 Drive-Hüllen und eine äußere Restore-Hülle ergänzt. Kein GitHub-Upload
und kein Emulatorlauf.

### Schritt 4: gespeicherte Exportdateien und direkte ViewModel-Einstiege

Fünf Dateien: DatevCsvOutput, AdvisorPackageBuilder, ReceiptViewModel,
Phase5BDatevOutputTest und dieses Dokument. CSV-/Beraterpaket-Dateien erhalten
eine Anmeldung über Erstellung, Schließen und Prüfsumme. Die direkten Methoden
executeWizardExport(context), exportToDatev und exportToPdf melden ebenfalls
vor ihrer Persistenzphase an; beim Assistenten bleibt die Anmeldung bis zur
bereits koordiniert eingeplanten Audit-/Statusfortsetzung überlappend aktiv.
Der neue Cache-Kontexttest erzeugt gültige CSV-/ZIP-Dateien aus vorhandenen
synthetischen Buchungsfixtures und prüft den Eintritt vor Dateianlage sowie
vollständige Ausgabe und Freigabe danach. Fachliche Freigaben, Mapping,
Serialisierung, Statuslogik und UI bleiben unverändert.

Vorab: eine neue Methode erreichte die gültige CSV-Dateierstellung ohne
Anmeldung (`expected:<[true]> but was:<[false]>`), ohne Skips/Timeouts oder
unverwandte Fehler (`/tmp/immopilot-export-file-coordination-red.xml` und
.log). Danach zwei Dateierzeuger und drei direkte ViewModel-Einstiege
angemeldet. Alle fünf ursprünglichen Bodies bytegenau erhalten; nur äußere
Lebensdauerzeilen ergänzt. Die bestehende Audit-Fortsetzung wird bereits
vor Einplanung angemeldet und überlappt jetzt mit der direkten Exportphase.

Umgebungsunterbrechung beim Exportdatei-Schritt: beide APK-Aufgaben waren
beendet, der Gesamtabschluss einschließlich Lint fehlte. Nach Neustart kein
Gradle-Prozess aktiv; die neun bestandenen Test-XMLs bleiben gesichert. Nur
lintDebug und die beiden APK-Aufgaben werden mit unverändertem Quellcode
fortgesetzt, ohne Wiederholung bestandener Tests.

Abnahme: neun exakt ausgewählte Methoden bestanden; XML ohne Fehler,
Skips oder Duplikate geprüft (`/tmp/immopilot-export-file-coordination-results`,
`/tmp/immopilot-export-file-coordination-tests.log`). Gültige CSV-/ZIP-Ausgaben,
Hashes, Originalmanifest/Reihenfolge sowie Freigabe-/Formatregressionen
bestehen. `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest` nach
Umgebungsneustart bestanden
(`/tmp/immopilot-export-file-coordination-build-resumed.log`), Lint ohne
Error/Fatal; `git diff --check` bestanden. Alle fünf alten Funktions-Bodies
bytegenau erhalten. Kein GitHub-Upload und kein Emulatorlauf.

### Schritt 4: eigenständige ZIP-/PDF-Datei-Erzeuger

Fünf Dateien: DatevExporter, PdfExporter, Phase5BDatevOutputTest,
OfflineBackupFilePublicationEmulatorTest und dieses Dokument. Beide verbleibenden
ZIP-/PDF-Dateierzeuger melden ihre volle Lebensdauer an; die ursprünglichen
Berechnungs- und Formatierungs-Bodies bleiben bytegenau erhalten.

Der ZIP-Test scheiterte vorab gezielt an der fehlenden Anmeldung
(expected [true], actual [false]). Der neue PDF-Nachweis wurde aus dem JVM-Test
in einen geschützten Android-Test verlegt: Robolectric 4.16.1 enthält keinen
PdfDocument-Shadow; selbst mit nativer Grafik ergibt startPage bereits
"document is closed!". Eine vorübergehende Weitergabe der Originalausnahme
wurde bytegenau entfernt. Der Android-Test prüft echte PDF-Bytes, Anmeldung vor
Cache-Zugriff, Invalidierung älterer Capture-Tokens und Freigabe danach. Er nutzt
ein eigenes Ausgabeverzeichnis und die vorhandene isolierte Emulator-Schutzregel.
Dieser Android-Test ist bis zur tatsächlichen Emulatorausführung ausdrücklich
nur implementiert, nicht als bestanden zu werten. PropertyImagePicker folgt im
nächsten Inkrement zusammen mit dem verbleibenden Bank-CSV-Dateischreiber.

Abnahme: vier echte JVM-Methoden in Phase5BDatevOutputTest bestanden, XML ohne
Fehler/Skips/Duplikate (`/tmp/immopilot-leaf-file-coordination-green.xml`).
Lint sowie App-/Test-APK-Build bestanden
(`/tmp/immopilot-leaf-file-coordination-build.log`); keine Error/Fatal-Lintbefunde.
Die zwei ursprünglichen Dateierzeuger-Bodies sind bytegenau erhalten.
Der PDF-Android-Test kompiliert; Ausführung bleibt wegen fehlendem lokalem KVM
bis zum gezielten isolierten Emulatorlauf offen. Kein GitHub-Upload.

### Schritt 4: Objektbild- und Bank-CSV-Veröffentlichung

Geplant sind vier Dateien: ImmobilienManagerFeature, ReceiptAppUi,
OfflineBackupBankExportCoordinationTest und dieses Dokument. Der Bildauftrag
wird vor dem Einplanen bis zum strukturierten Abschluss angemeldet; die bereits
koordinierte Metadatenfortsetzung überlappt damit. Der Bank-CSV-Erzeuger erhält
eine äußere Anmeldung. Die vorhandenen Bodies, CSV-Inhalte und UI bleiben
unverändert. Der neue synthetische Test prüft echte gespeicherte CSV-Bytes,
Freigabe-Intent, Anmeldung vor Dateizugriff und saubere Freigabe danach.
Der bereits geprüfte launch-Helfer deckt Einplanung, Kindaufträge und Abbruch ab.

Vorab scheiterte genau die Anmeldung vor Cache-Publikation (Testzeile 43),
nach bereits erfolgreichem Inhalts- und Freigabe-Intent-Nachweis.
Kein Fixture-, Format- oder Providerfehler; RED-XML gesichert unter
`/tmp/immopilot-ui-file-coordination-red.xml`. Nun nur die äußere Bank-CSV-
Lebensdauer und den koordinierten Bildauftrag ergänzt. Beide bestehenden
Bodies einschließlich gemischter Zeilenenden bytegenau erhalten.

Abnahme: vier ausgewählte Methoden bestanden, XML ohne Fehler, Skips oder
Duplikate geprüft (`/tmp/immopilot-ui-file-coordination-results`). CSV-Bytes,
Freigabe-Intent sowie Lebensdauer vor Einplanung, über Kindaufträge und bei
Abbruch sind nachgewiesen. Lint und beide APK-Builds bestanden
(`/tmp/immopilot-ui-file-coordination-build.log`), keine Error/Fatal-Lintbefunde.
`git diff --check` bestanden; kein Emulatorlauf oder GitHub-Upload.

### Schritt 4: verfügbare Originalversionen und gespeicherte Exporte

Vier Dateien: neue OfflineBackupFileInventory, deren gezielte Nachweistests,
PersistentStorageInventoryTest und dieses Dokument. Das neue Inventar erfasst
alle vorhandenen Versionen in fachlichen Originalverzeichnissen sowie noch
vorhandene gespeicherte Export-/Berichtsdateien, auch ohne aktuelle Zeilenreferenz.
Einträge erhalten portable relative Zuordnung und Original-/Exportklassifikation;
Originale werden dabei weder gelesen, verändert noch entfernt. Nur ausdrücklich
klassifiziertes unreferenziertes Staging wird ausgelassen. Null-Byte-Dateien bleiben
sichtbar, damit die spätere Inhaltsprüfung gezielt abbricht. Verzeichnisfehler,
unsichere Links und Mengenüberschreitung dürfen keinen Teilerfolg ergeben.
Ein zusätzlicher Quellcodeguard ordnet alle eigenen Cache-Zugriffe bewusst zu.
Das aktiviert noch keinen Snapshot und ändert das V1-Archivformat nicht.

Vorab: alle sechs neuen Verhaltensfälle scheiterten an den noch fehlenden
Inventareigenschaften, ohne Skips oder Fremdfehler
(`/tmp/immopilot-complete-file-inventory-red.xml`). Anschließend Erfassung,
sichere relative Zuordnung, Quellenklassifikation und begrenzte Dateimenge ergänzt.

Zusätzlicher Quellcodebefund: CameraX hält `cacheDir/temp_receipt_*.jpg` nach dem
Einlesen weiter vor; analyzeReceipt erstellt daraus gegebenenfalls verkleinerte
JPEGs in filesDir. Diese noch vorhandenen ursprünglichen Aufnahmebytes sind
kein rekonstruierbarer Vorschaucache. Sie werden als ORIGINAL mit erfasst.
Die asynchrone CameraX-Veröffentlichung wird vor Snapshot-Aktivierung separat
koordiniert; bis dahin meldet isCoordinated für diese Quelle ausdrücklich false.
Der neue Vollständigkeitstest umfasst die erhaltenen Kamerabytes zusätzlich.
Der Exporter bleibt unverändert und verwendet das neue Inventar noch nicht.

Review-Korrektur vor Commit: ein zusätzlicher Fehlertest wies eine rohe
IOException mit privatem absoluten Pfad bereits beim Öffnen des Inventars nach
(`/tmp/immopilot-file-inventory-privacy-red.xml`). Konstruktion, Aufzählung und
Quellenprüfung normalisieren nun ausschließlich IO-/Berechtigungsfehler zu
pfadfreien Validierungsmeldungen. Die Originalausnahme wird weder angehängt
noch protokolliert. Der Test prüft IO- und SecurityException sowohl beim
Initialisieren als auch beim späteren Verzeichnislesen. Frühere bestandene
Neunerprüfung und Builds gelten noch nicht als Abnahme dieser Korrektur.

Abnahme des endgültigen Standes: zehn echte Methoden bestanden, ohne Fehler,
Skips, Duplikate oder Platzhalter
(`/tmp/immopilot-complete-file-inventory-final-results`). Lint und beide APK-
Builds bestanden (`/tmp/immopilot-complete-file-inventory-final-build.log`);
keine Error/Fatal-Lintbefunde. `git diff --check` bestanden. Erfasst werden alle
getesteten erhaltenen Versionen/Exporte, einschließlich der Rohkamera-Datei;
private Dateisystempfade gelangen auch bei Fehlern nicht in die Meldung.
Keine Aktivierung des Snapshots, kein Emulatorlauf und kein GitHub-Upload.

### Schritt 4: tatsächliche CameraX-I/O-Veröffentlichung

Vier Dateien: OfflineBackupCoordinator, dessen Tests, ReceiptAppUi und dieses
Dokument. Die vorhandene CameraX-Version 1.5.0 wurde anhand ihres offiziellen
Sources-JARs geprüft:
https://dl.google.com/dl/android/maven2/androidx/camera/camera-core/1.5.0/camera-core-1.5.0-sources.jar
ImageCapture.Builder.setIoExecutor wird über ImagePipeline an ProcessingNode
weitergegeben. Dessen I/O-Auftrag umfasst JpegBytes2Disk, EXIF-Änderung und das
abschließende Umbenennen aus FileUtil. RequestWithCallback kann bei Abbruch
bereits onError senden, während diese Verarbeitung noch ausläuft; ein bloßer
Callback-Zähler wäre daher kein vollständiger Nachweis.

Geplant ist eine Anmeldung vor Einplanung des tatsächlichen Executor-Auftrags,
die bis zu dessen Rückkehr oder Fehler aktiv bleibt. Der vorhandene IO-Dispatcher
wird weiterverwendet; keine neuen Threads, kein CameraX-Upgrade und keine Änderung
an UI oder Aufnahmeformat. Drei deterministische Tests prüfen echte Dateipublikation
mit eingeplanter Fortsetzung, Ablehnung vor Eintritt und Fehler bei synchroner
Ausführung. Das Inventar bleibt für Kameraquellen vorerst bewusst konservativ;
seine Freigabe erfolgt erst mit dem folgenden Snapshot-Nachweis.

Vorab scheiterten alle drei neuen Tests ausschließlich an der fehlenden
Capture-Sperre/Invalidierung; keine Fremdfehler oder Skips
(`/tmp/immopilot-camera-io-coordination-red.xml`). Nun vor Executor-Einplanung
anmelden, nach tatsächlicher I/O-Rückkehr im finally freigeben und Ablehnung
unverändert weiterreichen. Der CameraX-Builder erhält nur diese Executor-Grenze;
Import und Builderzeile sind die einzigen Änderungen in ReceiptAppUi. Alle
bisherigen Aufnahme-/Fehler-/Callback-Bodies bleiben bytegenau erhalten.

Abnahme: sieben echte ausgewählte Methoden bestanden; XML ohne Fehler, Skips,
Duplikate oder Platzhalter geprüft (`/tmp/immopilot-camera-io-coordination-results`).
Wartende und ineinander eingeplante Dateiveröffentlichung, Ablehnung und
synchroner Fehler geben den Zähler korrekt frei; vorhandene Coroutine-Grenzen
und der Cache-Guard bestehen. Lint und beide APK-Builds bestanden
(`/tmp/immopilot-camera-io-coordination-build.log`), keine Error/Fatal-Lintbefunde.
`git diff --check` bestanden. Originale UI-/Callback-Bodies bytegenau erhalten;
CameraX I/O-Grenze durch offizielle Sources 1.5.0 nachvollzogen.
Kein Kamera-/PDF-Emulatorlauf und kein GitHub-Upload.

### Schritt 4: gestreamter V2-Metadatenvertrag

Drei Dateien: neue OfflineBackupMetadataWriter, zugehöriger Nachweis und dieses
Dokument. Der neue Serializer liefert ausdrücklich nur einen noch nicht global
fixierten Entwurf. Er liest alle klassifizierten fachlichen Room-Tabellen und
inventarisierten Preferences in derselben Lesetransaktion, erhält SQL-Werte und
sqlite_sequence-Hochstände und schreibt Tabellenzeilen gestreamt in ein eigenes
privates Metadatenfile. Keine Sammlung der gesamten Datenbank als JSONObject.
Technischer document_migration_journal und abgeleitete FTS-Daten bleiben explizit
lokal; unbekannte Tabellen müssen eine neue Klassifikation erzwingen.

Dateiverweise werden als portable Alias-IDs ausgegeben; Live-Pfade, Besitzer,
Hash-/Größenansprüche und geprüfte Alternativkandidaten bleiben ausschließlich
im internen Entwurfsvertrag zur folgenden Originalfixierung. Der gesamte Snapshot
wird erst nach Kopie aller Originalversionen und Generationsprüfung freigegeben.
Metadaten bleiben auf 64 MiB, Referenzen auf 10.000 begrenzt; bestehende Grenzen
werden nicht erhöht. Vorhandene Ziele werden nicht überschrieben; Fehler oder
Abbruch entfernen nur das eigene neu erstellte Metadatenfile. Das V1-Format bleibt
unverändert; weder Archivfreigabe noch Erfolgseintrag entstehen in diesem Schritt.

Erster Testaufbau korrigiert: der Ausdrucksrumpf von setUp leitete Boolean statt
JUnit-void ab. Der Lauf endete vor Ausführung der Methoden und zählt ausdrücklich
nicht als Verhaltens-RED (`/tmp/immopilot-streamed-metadata-invalid-fixture.xml`).
Nur die Setup-Signatur wurde zu Unit korrigiert; die fünf Assertionsfälle bleiben
unverändert und werden vor der Implementierung erneut ausgeführt.

Nach Setup-Korrektur: alle fünf echten Methoden erreichten die beabsichtigten
fehlenden Eigenschaften (leere Tabellen-/Referenzdaten, fehlende Ablehnung bzw.
falscher Zielkonflikttyp), ohne Skips oder Fremdfehler
(`/tmp/immopilot-streamed-metadata-red-valid-fixture.xml`). Der Serializer ist
nun implementiert: Zeilen einzeln schreiben, UTF-8 strikt kodieren, 64-MiB-Grenze
vor Ausgabe prüfen, Encoder schließen und Dateideskriptor danach synchronisieren.
Technische Tabellen bleiben explizit klassifiziert. Gefilterte Preferences und
SQLite-Zählerstände stammen aus derselben Lesetransaktion. Der Entwurf ersetzt
Live-Pfade durch Alias-IDs; Originalbytes werden erst im folgenden Schritt fixiert.

Erster Implementierungslauf: sieben von acht Methoden bestanden. Der
Mehrseitennachweis wies auf die Live-Datei-Abhängigkeit der Legacy-Kommatrennung
hin (`/tmp/immopilot-streamed-metadata-multipage-failure.xml`). Gemischte
Datei-/Content-URI-Quellen werden im neuen Test ausdrücklich als JSON-Liste
vorgegeben; V2 gibt mehrere Alias-IDs immer als JSON-Liste aus. Der Legacy-Helfer
und bestehende Belegpfade werden nicht verändert. Zusätzlich SHA-256 direkt im
Ausgabestrom berechnen: der alte allgemeine Dateihash-Helfer kann Fehler-Sentinels
zurückgeben und wird für den neuen Metadatenvertrag deshalb nicht verwendet.
Die vorhandene Prüfsummenassertion vergleicht weiterhin unabhängig die echten
Dateibytes. Fertig-Markierung erst nach vollständiger Entwurfserstellung setzen.

Abnahme: acht exakt ausgewählte Methoden bestanden, XML ohne Fehler, Skips,
Duplikate oder Platzhalter geprüft (`/tmp/immopilot-streamed-metadata-results`).
Quellwerte/OCR/Korrekturen, 64-Bit-Hochstand, Preferences/Credential-Sperre,
Mehrseitenreihenfolge, private URI-Aliase, Grenzen, Zielkonflikt und Abbruchstart
sind nachgewiesen. Metadatenlesung invalidiert keinen eigenen Capture-Token und
ändert keine fachlichen Quellen. Lint und beide APK-Builds bestanden
(`/tmp/immopilot-streamed-metadata-build.log`), keine Error/Fatal-Lintbefunde.
`git diff --check` bestanden. Dies ist ein Metadatenentwurf, noch kein freigegebener
gemeinsamer Snapshot. Kein Emulatorlauf, GitHub-Upload oder Erfolgseintrag.

### Schritt 4: gemeinsame Snapshot-Fixierung

Fünf Dateien: neue OfflineBackupSnapshot, zugehöriger Nachweis,
OfflineBackupFileInventory, dessen Test und dieses Dokument. Die neue Factory
führt Metadatenentwurf und alle inventarisierten Original-/Exportversionen in
app-privatem laufbezogenem Staging zusammen. Inhaltsgleiche Payloads dürfen
zusammenfallen; jede physische Version behält ihren portablen Manifest-Eintrag.
Externe Quellen erfordern einen bereits erfassten Inhaltsnachweis. Ein Archiv
liest ausschließlich diese fixierten privaten Dateien und prüft Größe/Hash.

Vorher DB öffnen und Initialisierungen abwarten. Die Dateiliste wird vollständig
vor der ersten Kopie erfasst und nach der Fixierung erneut verglichen: auch ein
OS-Cache-Unlink ohne App-Schreibanmeldung darf keinen gemischten Bestand ergeben.
Kameraquellen werden jetzt als koordiniert klassifiziert, nachdem der tatsächliche
CameraX-I/O-Auftrag nachweislich angebunden ist. Der gemeinsame Generationsnachweis
wird erst nach den gezielten Zwischenraumtests aktiviert. Kein UI-Anschluss,
keine Zielveröffentlichung und kein Erfolgseintrag in diesem Inkrement.

Vorab: acht echte Methoden liefen; exakt drei scheiterten an der noch fehlenden
gemeinsamen Capture-Grenze (aktiver Schreiber, DB-/Original-Zwischenraummutation,
Preference-Publikation). Fünf bestanden bereits, einschließlich unveränderlicher
Archivbytes nach späteren Live-Änderungen, Cache-Unlink, Payload-Deduplizierung,
Grenzen/Abbruch und externer Quelle ohne Inhaltsnachweis
(`/tmp/immopilot-global-snapshot-red.xml`). Nun beginCapture nach DB-Öffnung und
Initialisierungsbarriere setzen und completeCapture erst nach geschlossenen,
synchronisierten Metadaten-, Manifest- und Originalkopien ausführen. Keine
Wiederholung bei Konflikt. Frühzeitige Framework-/Staging-Zugriffsfehler erhalten
ebenfalls pfadfreie Meldungen. Der neue Snapshot bleibt bis zur V2-Verschlüsselung
und geprüften Veröffentlichung intern; der V1-Bildschirm wird nicht umgestellt.

Abnahme der gemeinsamen Fixierung: 17 echte ausgewählte Methoden bestanden;
erhaltene XML-Ergebnisse nach Umgebungsneustart erneut ohne Fehler, Skips oder
Duplikate geprüft (Snapshot 8, Dateiinventar 7, Quellcodeguards 2). Der abgebrochene
Build wurde ohne Wiederholung bestandener Tests fortgesetzt. lintDebug,
assembleDebug und assembleDebugAndroidTest bestanden
(`/workspace/immopilot-global-snapshot-build-resumed.log`), Lint ohne Error/Fatal.
`git diff --check` bestanden. Kein UI-Anschluss, Emulatorlauf oder GitHub-Upload.

### Schritt 5: authentifizierte Streaming-Schicht

Vier Dateien: direkte Tink-Abhängigkeit, OfflineBackupStreamCipher, dessen
gezielter Test und dieses Dokument. Offizielle Sources von tink-android 1.24.0
bestätigen AesGcmHkdfStreaming mit 256-Bit-Eingangsschlüssel, HmacSha256,
1-MiB-Segmenten und Offset 0. Der vorhandene Runtime-Graph enthielt bislang
Tink Android 1.5.0 über Security-Crypto/ReCAPTCHA; der neue Graph wird geprüft.
Diese innere Schicht akzeptiert authentifizierte Headerbytes und verlangt beim
Lesen vollständigen EOF-Nachweis. Envelope, Recovery-KDF, Keystore und UI werden
in den folgenden Teilschritten angeschlossen; noch keine neue Exportfreigabe.
Nur die vier neuen unmittelbar relevanten Streaming-Tests werden ausgeführt.

Abnahme der Streaming-Schicht: ausschließlich vier neue Kryptotests bestanden,
JUnit-XML auf echte Methoden ohne Fehler/Skips geprüft. Lint und assembleDebug
nach Wiederaufnahme bestanden (/workspace/immopilot-stream-cipher-build-resumed.log),
Lint ohne Error/Fatal. Der neue Runtime-Graph und EOF-/Manipulationsnachweise
liegen in /workspace/immopilot-stream-cipher-tests.log. Keine Wiederholung
unveränderter Tests. Envelope und Recovery-Schlüssel bleiben nächster Teilschritt.

### Schritt 5: Recovery-Passwortableitung

Vier Dateien: direkte BC-Abhängigkeit 1.86, OfflineBackupPasswordKdf, dessen
gezielter Test und dieses Dokument. Offizielle Sources bestätigen den expliziten
SHA256Digest im PKCS5S2ParametersGenerator; dessen SHA-1-Standardkonstruktor wird
nicht verwendet. PBKDF2 erzeugt genau 32 Bytes bei 600.000 bis 2.000.000 Iterationen;
Salt 16–32 Bytes und Passwort 16–1024 Zeichen werden vor Ableitung geprüft.
Passwort- und UTF-8-Eingangspuffer werden auch bei Ablehnung gelöscht. Kein
Passwortspeicher, keine Logs, keine API-Aufrufe. Der unabhängige 600.000er Testwert
wurde mit Python hashlib.pbkdf2_hmac erzeugt. Nur drei neue KDF-Methoden prüfen.

Abnahme KDF: ausschließlich drei neue Methoden bestanden, XML ohne Fehler/Skips
geprüft. Unabhängiger 600.000er SHA256-Vektor stimmt überein, Parametergrenzen
und Löschen der Eingangspuffer sind nachgewiesen. lintDebug und assembleDebug
bestanden (/workspace/immopilot-password-kdf-build.log), keine Error/Fatal-
Lintbefunde. Kein Envelope-/Keystore-Anschluss oder GitHub-Upload.

### Schritt 5: MK-/DEK-Schlüsselumhüllung

Drei Dateien: OfflineBackupKeyEnvelope, dessen fünf gezielte Methoden und dieses
Dokument. Zufälliger neuer 256-Bit-MK je Generation, AES-256-GCM mit 12-Byte-IV und
128-Bit-Tag. Recovery bindet Generation, Formatkontext, Salt und Iterationen;
DEK-Umhüllung bindet Generation und Archivkennung. Keine eigene Cipher- oder
KDF-Implementierung; JCA AES/GCM und geprüfte PBKDF2-Schicht verwenden.
Neue Generationen erzeugen unabhängig neues Material; alte MKs werden nicht nur
neu umhüllt. Geöffnete Generationen sind schließbar, temporäre Schlüsselkopien
werden gelöscht. Noch kein persistenter Schlüssel-/Passwortspeicher und kein
Exportanschluss. Nur fünf neue Envelope-Tests ausführen.

Abnahme Envelope: ausschließlich fünf echte Methoden bestanden, XML ohne
Fehler/Skips geprüft. Passwort-Roundtrip, AAD-/Ciphertext-Manipulation,
unabhängiger neuer MK trotz gleicher neuer Kontextkennung beim Negativtest,
altes Passwort für alte Generation und geschlossene Generation nachgewiesen.
lintDebug und assembleDebug bestanden (/workspace/immopilot-key-envelope-build.log),
keine Error/Fatal-Lintbefunde. Kein persistenter Store, Archivanschluss oder Push.

### Schritt 5: begrenzter binärer Archivheader

Drei Dateien: OfflineBackupArchiveHeader, dessen vier gezielte Methoden und
Inventardokument. Magic/Version/Länge, feste Crypto-Suite, Generation/Archiv-ID,
KDF-Parameter und verschlüsselte MK-/DEK-Umhüllungen werden binär kodiert.
Header insgesamt maximal 64 KiB; Längenprüfung vor Allokation, KDF-Grenzen vor
jeder späteren Ableitung. Unbekannte Versionen/Suites und zusätzliche Felder
ablehnen. Exakte gelesene Headerbytes werden zur Streaming-AAD weitergereicht;
Ciphertext bleibt beim Headerlesen unangetastet. Noch keine Entschlüsselungs-
oder Restore-Freigabe durch erfolgreiches Headerparsen allein.

Abnahme Header: nur vier neue echte Methoden ausgeführt und bestanden, XML
ohne Fehler/Skips geprüft. Exakte AAD-Bytes und Ciphertext-Position, Header-/KDF-
Grenzen, Versions-/Suite-Sperre, Trunkierung und zusätzliche Daten nachgewiesen.
lintDebug und assembleDebug bestanden (/workspace/immopilot-archive-header-build.log),
keine Error/Fatal-Lintbefunde. Kein Entschlüsselungs-/Restore-Anschluss oder Push.

### Schritt 5: verschlüsselter Archiv-Datenstrom

Drei Dateien: OfflineBackupArchiveCrypto, dessen vier neue Integrationstests und
dieses Dokument. Pro Archiv zufälligen 256-Bit-DEK erzeugen, unter MK umhüllen,
Header voranstellen und alle Headerbytes als Streaming-AAD binden. Entschlüsselung
liefert erst nach authentifiziertem EOF und fsync erfolgreich zurück; nur ein
neuer privater Staging-Platz, maximal 2 GiB Klartext. Fehler/Abbruch löschen nur
diesen eigenen Platz, vorhandene Dateien bleiben erhalten. Passwort/DEK-Puffer
auch bei ungültigem Header löschen. Authentifiziert bedeutet noch nicht
manifest-/schema-/beziehungsgeprüft. API-Credentials werden hier nicht eingebaut;
eine spätere Credential-Sektion muss auch im entschlüsselten ZIP selbst
verschlüsselt bleiben und ausschließlich im Speicher geöffnet werden.
Nur vier neue Crypto-Integrationstests ausführen.

Erster Viererlauf bestanden. Zusätzlicher gezielter Abbruchtest wies danach
nach, dass Cancellation vor Eintritt in withContext den Passwortpuffer noch
nicht löschte (/workspace/immopilot-archive-crypto-cancel-red.xml). Bereinigung
liegt nun zusätzlich außerhalb des Dispatcher-Wechsels; vor Dateianlage
Cancellation erneut prüfen. Ein sechster Test prüft den echten ZIP-Abschluss
innerhalb des Cipher-Streams einschließlich erneutem äußeren Close. Nach der
Korrektur nur diese sechs unmittelbar betroffenen Crypto-Methoden ausführen.

Abnahme Archiv-Krypto: sechs echte Integrationstests bestanden, XML ohne Fehler
oder Skips geprüft. Vollständiger Wire-Roundtrip, Passwort-/Headerfehler,
fehlendes Schlusssegment, Größenbegrenzung, Schutz vorhandener Daten,
Dispatcher-Abbruch und ZIP-/Cipher-Abschluss sind nachgewiesen. lintDebug und
assembleDebug bestanden (/workspace/immopilot-archive-crypto-build.log), keine
Error/Fatal-Lintbefunde. Nur unmittelbar betroffene Tests ausgeführt. Noch kein
Keystore, Manifest-/Probe-Restore-Nachweis, UI-Anschluss oder GitHub-Upload.

### Schritt 5: Geräte-Keystore-Adapter

Drei Dateien: OfflineBackupDeviceKey, dessen gezielter Test und dieses Dokument.
Explizite Einrichtung eines 256-Bit-AES-Keystore-Schlüssels mit GCM, zufälliger
Provider-IV und ohne interaktive Authentisierung für später autorisierte Worker.
Der Keystore-Schlüssel wird niemals exportiert. MK-Umhüllung bindet den gesamten
Recovery-Kontext als AAD. Öffnen fehlender/defekter Schlüssel erzeugt keinen
Ersatz; erneute Einrichtung ersetzt keinen vorhandenen Eintrag. Temporäre MK-
Kopien werden gelöscht. Noch keine persistente Ablage der geschützten Records.
Fünf unmittelbar relevante Methoden auf Mindest-API 24 und API 35 ausführen;
Keystore-Adapter ist simuliert, AES-GCM real. Keine Geräte-/Hardware-Abnahme behaupten.

Abnahme Geräteadapter: fünf Methoden auf API 24 und 35, zehn echte Ausführungen
bestanden; XML ohne Fehler/Skips und mit beiden SDK-Fällen geprüft. Frische IVs,
identischer wiedergeöffneter MK, unveränderter vorhandener Alias, fehlender Alias
und manipulierte Records sind nachgewiesen. lintDebug und assembleDebug bestanden
(/workspace/immopilot-device-key-build.log), keine Error/Fatal-Lintbefunde.
Keystore-Simulation mit realem AES-GCM; kein physischer Hardware-Nachweis,
keine persistente Record-Ablage, kein UI-Anschluss oder GitHub-Upload.
