# Fehlerkorrekturen und Prüfbericht

Geprüfter aktueller main: `ed2c42ef420a93a7b9ba4be2694815ebb61c7d85`.
Zuletzt gemergte PRs #95 bis #88 geprüft; vorhandene Navigations-/Fahrtenbuchkorrekturen beibehalten.
Arbeitsbranch: `agent/backup-completeness-security`. Keine Änderungen direkt auf main, kein Merge.

| Priorität | Bereich | Gefundener Fehler | Ursache | Änderung | Teststatus |
|---|---|---|---|---|---|
| P1 | Backup: Preferences | Kanzleiprofil, Aufgaben, Status/Mietdetails, Banknotizen und KI-Regeln fehlen im Zusatzbackup | Unvollständige feste Speicherliste | Zentrales Inventar, typgetreuer Export/Import; stabile Schlüssel | Neuinstallation simuliert, doppelte Wiederherstellung, A/B-Zuordnungen bestanden |
| P1 | Secrets | Gemischte Preference-Dateien dürfen nicht vollständig kopiert werden | Zugangsdaten und Einstellungen liegen zusammen | Explizite Positivlisten, Credential-Filter auf Export und Import | Sentinel-/Injection-Tests bestanden |
| P1 | Dateien/Room | Objektbilder und Exportprotokolle fehlen | Foto nur als lokaler Pfad; Audit-Tabelle nicht gesichert | Bildbytes und Audit-Datensätze sichern; getrennte objektbezogene Fotodateien | Bytes, Reparatur, Idempotenz und Audit-Roundtrip bestanden |
| P1 | Restore-Sicherheit | Standarddialog kann vorhandene Belege ersetzen; ungültiges Dokumentarray kann als leer gelten | REPLACE_FULL als Default; optJSONArray(null) | Standarddialog MERGE; Vorvalidierung; bewusster Replace bleibt verfügbar | Alte/neue Payloads und Rollback-Schutz bestanden |
| P1 | Objektidentität | Merge kann fremdes lokales Objekt ersetzen oder gleiche propertyId duplizieren | Numerische Room-ID aus Backup wird ungeprüft übernommen | Restore-Upsert nach stabiler propertyId; kollidierende interne ID neu vergeben | Konflikt und Doppel-Restore bestanden |
| P2 | Backup-Erfolg | Fehlgeschlagene Einzeluploads können als erfolgreich erscheinen | Uploadzähler fehlen in Erfolgskriterium | Alle Beleg-/Dokumentuploads müssen erfolgreich sein | Codeprüfung und vollständige Tests bestanden; echter Drive-Upload nicht ausgeführt |
| P2 | Laufender UI-State | Restaurierte Auswahl, Profil, Entwürfe und Regeln nicht sofort aktuell | Nur Teilzustand neu gelesen; Regeln asynchron | Bestehende Preferences nach Restore vollständig und synchron neu lesen | ViewModel-Regression bestanden |
| P2 | Navigation | System-Back überspringt Einheiten-/Dokumentliste; geschlossener Belegdetail-Eintrag bleibt im Verlauf | Fehlende lokale BackHandler; globale History erkennt lokales Zurück nicht | Zwei lokale Handler und gezielte History-Korrektur | Fehler vor Fix reproduziert; Compose-Regressionen bestanden |
| P2 | Logging | Prompts, Inhalte, Pfade und IDs können geloggt werden | Produktive direkte Logs und Exception-Dumps | Nur statische Ereignisse über DEBUG-geschützten Logger | Quelltest und vollständige Tests bestanden |
| P2 | Lifecycle | UI sammelt StateFlows auch außerhalb aktivem Lifecycle | collectAsState ohne Lifecycle | Geprüfte VM-/Room-Flows mit collectAsStateWithLifecycle | Compose-/ViewModel-Tests bestanden; Dependency bereits vorhanden |
| P2 | Repository-Konfiguration | Unbenutzte Firebase-Applet-Konfiguration liegt in Git | Kein Verweis aus Android-App oder Build | Datei entfernt; Android-Firebase-Konfiguration unverändert | Referenzprüfung und Debug-Build bestanden |

Room: **33 → 33**, keine Migration hinzugefügt, keine destructive Migration.
Zusatzbackup: **13 → 14**; AppConfig und Belegmetadaten: **1 → 1**.

## Neue Tests

- `PreferenceBackupCompletenessTest`: 11 Regressionen (fachliche Preferences, zwei Restore-Läufe, A/B-Isolation, Secrets, Altformat, Typen, Fotos, Schema, Audit, Replace-Validierung, stabile Objektidentität).
- `PersistentStorageInventoryTest`: 2 Quelltests für explizite Preference-Klassifizierung und datensparsame Logs.
- `RestoredPreferenceStateTest`: laufender ViewModel-Zustand nach Restore.
- `UnitDocumentBackNavigationTest`: 2 Tests für System-Back und Pfeil mit übergeordnetem BackHandler.
- `MainNavigationRegressionTest`: 4 echte MainActivity-/Compose-Flows für Immobilien, Bank, Belegbearbeitung und AfA.
- Vorhandene Fahrtenbuch-Referenztests prüfen Route/Prüfen/Bearbeiten und beide Rückwege; unverändert beibehalten.

## Verbleibende Risiken

- UI-Tests laufen mit Robolectric/Compose auf Android SDK 35; keine physische Geräte-/Emulator-Instrumentierung ausgeführt.
- Kein echter Google-Drive-OAuth-/Neuinstallations-End-to-End-Test; Payloadtests verwenden neue Room-Datenbanken und geleerte Preferences.
- Lint meldet 99 Warnungen (0 Fehler), überwiegend UseKtx; sie sind nicht deaktiviert worden.
- Bild-Base64 erhöht Größe und Arbeitsspeicherbedarf des Zusatzbackups.
- Room und Preference-Dateien bilden keine gemeinsame atomare Transaktion; Schreibfehler werden gemeldet und Restore kann wiederholt werden.
- Der entfernte Firebase-Key bleibt in der Git-Historie. Die unbenutzte Datei wurde entfernt; Cloud-seitige Key-Restriktionen sind hier nicht administrativ geprüft.

## Weitere Verbesserungsvorschläge

- Ergänzender Gerätetest mit separatem Google-Drive-Testkonto und realer Neuinstallation.
- Bei großen Portfolios separater Dateitransport für Objektbilder statt Base64 im JSON.
- Falls der alte Firebase-Applet-Key außerhalb dieser App genutzt wird, seine Restriktionen im zugehörigen Google-Cloud-Projekt prüfen.

## Ausgeführte Prüfungen

| Prüfung | Status |
|---|---|
| git diff --check | Bestanden |
| gradle testDebugUnitTest -Proborazzi.test.record=true | 563 Tests, 0 Fehler, 0 übersprungen |
| Room-Migrationstests | Bestanden (22 Tests in 10 Migration-Testklassen) |
| Backup-/Restore-, Bank-, DATEV-, Dokument-, Fahrtenbuchtests | Sämtliche vorhandenen und neuen Tests bestanden |
| Compose-UI-Tests | 17 Tests in 6 Klassen bestanden (Robolectric SDK 35) |
| gradle lintDebug | Bestanden: 0 Fehler, 99 Warnungen |
| gradle assembleDebug | Bestanden |
| Geräte-/Emulator-Instrumentierung | Nicht ausgeführt |

Debug-APK: 36454484 Bytes; SHA-256 `09470e97935a9f7b5a2543b01cac03023294b2eab3921367057dd4eaf1204f79`.

## Geänderte Dateien

- `app/src/main/java/com/example/MainActivity.kt`
- `app/src/main/java/com/example/api/GeminiClient.kt`
- `app/src/main/java/com/example/api/GoogleDriveClient.kt`
- `app/src/main/java/com/example/data/DrivePersistenceRepository.kt`
- `app/src/main/java/com/example/data/FirestoreService.kt`
- `app/src/main/java/com/example/data/PersistentPreferenceInventory.kt`
- `app/src/main/java/com/example/data/ReceiptDatabase.kt`
- `app/src/main/java/com/example/data/SupplementalDriveBackup.kt`
- `app/src/main/java/com/example/ui/AfaPortfolioFeature.kt`
- `app/src/main/java/com/example/ui/AnnualTaxAssistantFeature.kt`
- `app/src/main/java/com/example/ui/BankFeature.kt`
- `app/src/main/java/com/example/ui/BankPhase2CPanel.kt`
- `app/src/main/java/com/example/ui/BankPhase2DReviewPanel.kt`
- `app/src/main/java/com/example/ui/BankRentFeature.kt`
- `app/src/main/java/com/example/ui/BankTransactionSplitFeature.kt`
- `app/src/main/java/com/example/ui/DocumentFeature.kt`
- `app/src/main/java/com/example/ui/DocumentPresentation.kt`
- `app/src/main/java/com/example/ui/ImmobilienManagerFeature.kt`
- `app/src/main/java/com/example/ui/LoanFeature.kt`
- `app/src/main/java/com/example/ui/LogbookFeature.kt`
- `app/src/main/java/com/example/ui/ReceiptAppUi.kt`
- `app/src/main/java/com/example/ui/ReceiptDetailScreen.kt`
- `app/src/main/java/com/example/ui/ReceiptViewModel.kt`
- `app/src/main/java/com/example/ui/RentIncomeFeature.kt`
- `app/src/main/java/com/example/ui/RentTenantWrapper.kt`
- `app/src/main/java/com/example/ui/UnifiedUnitsFeature.kt`
- `app/src/main/java/com/example/util/DatevExporter.kt`
- `app/src/main/java/com/example/util/DiagnosticLog.kt`
- `app/src/main/java/com/example/util/PdfExporter.kt`
- `app/src/test/java/com/example/data/BankBackup7AcceptanceTest.kt`
- `app/src/test/java/com/example/data/BankLearningRulesBackupAcceptanceTest.kt`
- `app/src/test/java/com/example/data/BankPhase2CBackupAcceptanceTest.kt`
- `app/src/test/java/com/example/data/BankTransactionSplitMigrationBackupTest.kt`
- `app/src/test/java/com/example/data/PersistentStorageInventoryTest.kt`
- `app/src/test/java/com/example/data/PreferenceBackupCompletenessTest.kt`
- `app/src/test/java/com/example/data/SupplementalDriveBackupTest.kt`
- `app/src/test/java/com/example/ui/MainNavigationRegressionTest.kt`
- `app/src/test/java/com/example/ui/RestoredPreferenceStateTest.kt`
- `app/src/test/java/com/example/ui/UnitDocumentBackNavigationTest.kt`
- `docs/backup-persistence-inventory.md`
- `docs/fix-validation-report.md`
- `firebase-applet-config.json` (entfernt)
