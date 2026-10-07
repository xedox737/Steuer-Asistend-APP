# Immopilot Code-Wegweiser

Stand: 07.10.2026. Dieser Wegweiser nennt die wichtigsten Einstiegspunkte; vor Änderungen immer die tatsächlichen Abhängigkeiten im aktuellen Branch prüfen.

## App-Einstieg und Navigation

- `app/src/main/java/com/example/MainActivity.kt` – App-Einstieg.
- `app/src/main/java/com/example/ui/ReceiptAppUi.kt` – zentrale UI-/Screen-Verknüpfung.
- `app/src/main/java/com/example/ui/PrimaryNavigation.kt` – globale Hauptnavigation.
- `app/src/main/java/com/example/ui/Ui2Components.kt` – gemeinsame UI2-/Immopilot-Komponenten.
- `app/src/main/java/com/example/ui/theme/` – Farben, Theme, Typografie.

## Belege und Dokumente

- `ui/ReceiptDetailScreen.kt`, `ui/ReceiptViewModel.kt`
- `ui/DocumentFeature.kt`, `ui/DocumentPresentation.kt`
- `data/Beleg.kt`, `data/ReceiptEntity.kt`, `data/ReceiptDatabase.kt`
- `data/DocumentModels.kt`, `data/ManagedDocumentService.kt`
- `data/DocumentStorageMigration.kt`

Bei Dateiänderungen MIME-Type, persistente URI-Rechte, PDF/JPG/PNG/WEBP, Objekt-/Wohnungszuordnung und Neustart prüfen.

## Bank und Mietkontrolle

- `ui/BankFeature.kt`, `ui/BankRentFeature.kt`, `ui/BankRentMatching.kt`
- `ui/BankTransactionSplitFeature.kt`
- `data/BankRentAssignments.kt`, `data/BankTransactionSplit.kt`
- `data/BankingModels.kt`

Bestehende Buchungsliste nicht unbeauftragt ändern. Bei Miet-Ist Bankzuordnung, Belegverknüpfung, Splits, Teil-/Überzahlungen und Doppelzählung gemeinsam prüfen.

## Immobilien, Einheiten und Mietverhältnisse

- `ui/ImmobilienManagerFeature.kt`
- `ui/UnifiedUnitsFeature.kt`
- `ui/PropertyUnitScopedData.kt`
- `ui/RentIncomeFeature.kt`, `ui/RentOverviewPresentation.kt`
- `ui/TenantHistoryFeature.kt`

Stabile IDs sind die Identität; Namen oder Listenpositionen nicht als Ersatz verwenden.

## AfA

- `ui/AfaPortfolioFeature.kt`
- `data/TaxPropertyCalculator.kt`

Gebäude-/Grundstücksanteil, Kaufnebenkosten, Beginn, Satz, Restnutzungsdauer, bisherige Abschreibung und Restbuchwert nachvollziehbar halten.

## Fahrtenbuch

- `ui/LogbookFeature.kt`, `ui/LogbookManagement.kt`
- `data/LogbookModels.kt`, `data/LogbookBookingStore.kt`
- `util/LogbookCsvExporter.kt`

Neu/Bearbeiten/Löschen, Kilometerstände, Datum/Uhrzeit, Objektzuordnung, Persistenz, Rücknavigation und Listenaktualisierung prüfen.

## DATEV

- `util/DatevExporter.kt`
- `util/DatevExportPolicy.kt`
- `util/DatevMappingService.kt`
- `util/DatevCsvSerializer.kt`
- `util/DatevFormatValidator.kt`
- `data/DatevProfile.kt`

Bei reinen Designänderungen Exportlogik nicht anfassen. Keine ungeprüften KI-Daten exportieren.

## Drive, Backup und Restore

- `api/GoogleDriveClient.kt`
- `data/DrivePersistenceRepository.kt`
- `data/SupplementalDriveBackup.kt`
- `data/RestoreSafety.kt`, `data/RestoreEligibilityPolicy.kt`
- `data/ReceiptDuplicateSafeUpsert.kt`
- `data/DuplicateCleanup*`

Keine Dubletten, keine falschen Überschreibungen, Tombstones/IDs/Dateiverweise erhalten; lokale Nutzung muss ohne Drive funktionieren.

## KI und Anbieter

- `api/AiProviderSettings.kt`
- `api/GeminiClient.kt`
- `api/OpenAiClient.kt`
- `api/ManagedDocumentAi.kt`
- `ui/AppSettingsScreen.kt`

Schlüssel niemals loggen oder committen; Review-/Bestätigungszustände an das richtige Dokument/Objekt binden.
