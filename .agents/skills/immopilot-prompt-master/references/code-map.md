# ImmoPilot Code-Wegweiser (vor Nutzung im aktuellen Branch verifizieren)

Maßgeblich ist immer die aktuelle `docs/CODE_MAP.md` des Repositories.

- App-Einstieg: `app/src/main/java/com/example/MainActivity.kt`
- UI und Navigation: `ui/ReceiptAppUi.kt`, `ui/PrimaryNavigation.kt`, `ui/Ui2Components.kt`
- Belege: `ui/ReceiptDetailScreen.kt`, `ui/ReceiptViewModel.kt`, `data/ReceiptDatabase.kt`
- Bank/Miete: `ui/BankFeature.kt`, `ui/BankRentFeature.kt`, `ui/BankRentMatching.kt`
- Objekte/Einheiten: `ui/ImmobilienManagerFeature.kt`, `ui/UnifiedUnitsFeature.kt`
- AfA: `ui/AfaPortfolioFeature.kt`, `data/TaxPropertyCalculator.kt`
- Fahrtenbuch: `ui/LogbookFeature.kt`, `data/LogbookModels.kt`
- DATEV: `util/DatevExporter.kt`, `util/DatevExportPolicy.kt`
- Drive/Restore: `api/GoogleDriveClient.kt`, `data/DrivePersistenceRepository.kt`, `data/RestoreSafety.kt`
- KI: `api/GeminiClient.kt`, `api/OpenAiClient.kt`, `api/ManagedDocumentAi.kt`

Relative Pfade unter `app/src/main/java/com/example/`. Dateien/Beziehungen nie ohne Prüfung voraussetzen.
