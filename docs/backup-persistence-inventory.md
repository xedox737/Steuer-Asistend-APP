# Persistenz- und Backup-Inventar

Geprüfte Basis: `ed2c42ef420a93a7b9ba4be2694815ebb61c7d85` (main, PR #95).
Room: 33 → 33. Zusatzbackup: 13 → 14. Drive-AppConfig und Beleg-Metadaten behalten Schema 1.

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
| document_search_fts | Suchindex, inkl. abgeleiteter OCR-Suche | CACHE, wird nach Restore neu aufgebaut; OCR bei Bedarf erneut erzeugt |
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
| filesDir/property-images | Objektbilder | BACKUP, neu als Bytes im Zusatzbackup; Restore unter objektbezogenem SHA-256-Dateiname statt fremdem Dateipfad |
| cacheDir (DATEV-CSV/ZIP, Berichte, Vorschauen, Ersatzdateien) | Generierte Exporte und temporäre Dateien | CACHE; Exportprotokolle werden separat gesichert |
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
- Cache-/gerätegebundene Journale, OCR-Volltext und lokale Dateipfade werden bewusst nicht als fachliche Originaldaten übertragen.
