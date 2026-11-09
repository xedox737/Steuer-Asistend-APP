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
und die im Quellcode angelegten privaten Dateiwurzeln. Neue DataStore-Erzeuger oder
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
