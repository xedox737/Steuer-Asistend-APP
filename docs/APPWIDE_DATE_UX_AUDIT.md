# Appweite Datums-UX – Bestandsaufnahme und Prüfnachweise

Basis: `64648bbf393dcf13f8243dbb5f471b56e564a9c2` (main nach checkout, fetch und pull).
Branch: `feature/appwide-date-format-calendar`. PR #124. Nicht mergen.

## Ursache und Grenzen

Die App verwendete ISO-Daten direkt in Textfeldern und mehreren Listen, daneben lokale deutsche Formatter und einen separaten deutschen Statusdatumsparser. Die Änderung zentralisiert ausschließlich Datumseingabe und Darstellung. Fachlogik bleibt ISO/LocalDate-basiert.

| Klasse | Beispiele | Behandlung |
| --- | --- | --- |
| A – echte Nutzerdaten | Belegdatum, Kaufdatum, Nutzen/Lasten, Mietbeginn/-ende, Mietänderung, AfA-Beginn/Stichtag, Darlehensdaten, Fahrtdatum, Maßnahmen, Dokumentdatum, Aufgaben | UI TT.MM.JJJJ, Bearbeitung mit AppDateField, Callback kanonisches ISO |
| B – technische Zeitstempel | createdAt, updatedAt, importedAt, approvedAt, lastSyncedAt, cancelledAt, Instant, Export-IDs | Speicherung unverändert; lesbare Anzeige bei sichtbaren Verlaufsdaten formatiert |
| C – feste Austauschformate | Room/Preferences, Backup-JSON, Gemini-JSON, Drive-API, DATEV EXTF/XML, Dateinamen, Fahrtenbuch-CSV | ISO/technische Verträge unverändert; reine Begleitberichte werden deutsch dargestellt |
| D – vorher bereits deutsch | Dashboard, Belegdetails, Bank-Hauptliste, Finanzübersicht, Fahrtenbuch, Sanierungsbericht | Bestehende Anzeigehelper delegieren an DateFormatUtils; Dashboard behält deutschen Wochentag |
| E – frühere UI-Leaks | Kauf-/Miet-/Darlehensfelder, manuelle Belege/Scan, Stammdaten/AfA-Zeilen, Dokumentdetails, Bank-Prüfpanels, DATEV-Vorschau | Deutsche Darstellung und gemeinsamer Kalender |

## Zentrale API

`util/DateFormatUtils.kt`: konstante strikte Formatter; parseIso, parseGerman, safeParse (ISO/deutsches Legacy), safeFormat, formatIsoToGerman, parseGermanToIso, displayText sowie UTC-DatePicker-Konvertierung.

`ui/AppDateField.kt`: Material-Kalender mit deutscher Locale, freier manueller Eingabe, TT.MM.JJJJ-Placeholder, Icon „Datum auswählen“, required/optional, enabled/readOnly, Grenzen, initialDate, TestTags. Ungültige Entwürfe bleiben lokal, werden als Fehler dargestellt und nicht als ISO an den Aufrufer ausgegeben. DateFieldValidation blockiert zusätzlich Speichern mit einem veralteten gültigen Modellwert. Abbrechen/Zurück übernehmen keine Kalenderänderung.

## Migrierte Bereiche

| Bereich | Eingaben / Anzeigen |
| --- | --- |
| Belege | Inline-Editor, manuelle Erfassung/Scan-Auswertung; Dashboard, Listen, Tabelle, Detail, Original-Audit, Teilen/PDF |
| Immobilien | Anlageassistent, notarieller Kauf, Nutzen/Lasten, Stammdaten und Steuerübersicht |
| Miete | Mieterwechsel, Mietplan-Korrektur, Mietänderung, Erstbefüllung, älterer Mietereditor, Status-/Auszugsdatum; Mietverlauf |
| AfA | Nutzungsdauer-Beginn, bestätigte Vorjahreswerte-Stichtag; Erwerbs-/AfA-Datumsanzeigen |
| Darlehen | Beginn, Zinsbindung bis, Laufzeitende; zugeordnete Beleganzeigen |
| Fahrtenbuch | Fahrtdatum im Erfassungsdialog, Korrektur; Vorschläge, Listen und Verlauf |
| Aufgaben | Fälligkeitsdatum; Aufgabenliste |
| Dokumente | Dokumentdatum und datierte KI-Vorschläge (Kauf/Lasten/Vertrag); Details, erkannte Eckdaten |
| Sanierung | Maßnahmenstart/-ende; Prüfzeitraum, Belege und Steuerberaterbericht |
| Bank | Hauptliste/Details bleiben funktional erhalten; Miet-/Split-/Konflikt-/Wiederkehrend-Prüfpanels deutsch |
| DATEV | Buchungs-/Belegdatum in Vorschau sowie verständliche Validierungsmeldungen; EXTF/CSV/XML nicht geändert |

Die monatliche Mietfälligkeitsregel „z. B. 3. Werktag“, Steuerjahre, YearMonth-Filter und Uhrzeiten sind keine frei wählbaren Kalenderdaten und behalten ihre fachlichen Controls.

## Datenintegrität

Keine Room-Schemaänderung, keine Migration und keine Umschreibung bestehender Daten. Backup/Restore inklusive MERGE, Originaldateien und Bankimport bleiben unverändert. Gemini-Prompts, JSON-Verträge und Fehlerklassifizierung unverändert. Sortierung, Jahresfilter, Mietchronologie, AfA und 15%-Prüfung verwenden weiter dieselben ISO-/LocalDate-Werte. Kalenderkonvertierung erfolgt ausschließlich in UTC und LocalDate, nicht in der Gerätezeitzone.

## Neue Tests

- DateFormatUtilsTest: ISO/deutsche Darstellung, strikte vierstellige Eingabe, unmögliche Daten, Schaltjahr, leere Werte, Legacy-Read, chronologische ISO-Sortierung, Jahresgrenzen/Sommerzeit in UTC, Berlin, Los Angeles und Kiritimati.
- AppDateFieldTest: ISO-Anzeige, manuelle Eingabe/Callback, ungültige Entwürfe ohne Callback, optionales Leeren, Kalender Feld/Icon, Auswahl, Abbrechen, Pflichtdatum, Grenzen, disabled/readOnly.
- AppDateCalendarEmulatorTest: produktive Belegerfassung, Kalenderauswahl, Abbrechen, Android-Zurück, ungültige Eingabe blockiert Speicherung, ISO in Room, erneutes Öffnen; jeweils FontScale 1.0/1.3/1.5 mit Screenshots.
- AppDateDisplayEmulatorTest: AfA-/Miet-/Aufgaben-/Darlehensanzeigen aus ISO-Persistenz, deutsche Darlehensfelder und Semantics-Assertion ohne ISO-Datumsanzeige.
- AppDatePersistenceEmulatorTest: Kauf/Nutzen-Lasten-Kalender und Wiederöffnung; Fahrtkalender, echte Buchung und erneuter Activity-Start inklusive verknüpftem Kostenbeleg.
- Bestehende PropertyPersistence-, TenantChronology- und Phase6B-Workflow-Emulatortests verwenden jetzt den Kalender und prüfen weiter Chronologie, IDs, Persistenz und Sanierungsberechnung.

## CI-Stand

Läufe 1204/1205: lintDebug erfolgreich. Lauf 1205: 897 Unit-Tests, drei Assertion-Fehler (Kalendertag-Semantics, doppelte Formularfehlermeldung, geänderter deutscher DATEV-Fehlertext). Assertions präzisiert; keine Tests deaktiviert. Abschließende Resultate und Emulatornachweise werden im PR dokumentiert. Hier kein lokales Gradle/Android-SDK vorhanden; kein lokaler Emulatorlauf behauptet.

## Abschlusssuche: verbleibende ISO-Muster

Alle folgenden Treffer sind interne Initialwerte, Parser-/Modellverträge oder technische Zeitstempel; keine frei editierbaren ISO-Textfelder. Der technische Dateiname/CSV-Vertrag bleibt bewusst stabil. Sichtbare Kalenderwerte nutzen DateFormatUtils.

```text
app/src/main/java/com/example/api/GeminiClient.kt:141:    @Json(name = "datum") val datum: String = "", // YYYY-MM-DD
app/src/main/java/com/example/api/GeminiClient.kt:506:            - datum: Leistungs- oder Rechnungsdatum strikt im Format YYYY-MM-DD. Falls kein Datum erkennbar ist, setze einen leeren String und erfinde kein Datum.
app/src/main/java/com/example/api/GeminiClient.kt:1216:             "documentDate":"YYYY-MM-DD oder leer","targetArea":"","fields":[
app/src/main/java/com/example/api/GoogleDriveClient.kt:383:        // Format date as YYYY-MM-DD (jjjj-mm-dd)
app/src/main/java/com/example/api/GoogleDriveClient.kt:1518:        val timestampStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())
app/src/main/java/com/example/data/ReceiptDatabase.kt:86:    val datum: String, // YYYY-MM-DD
app/src/main/java/com/example/data/BookingRecord.kt:11:    val belegdatum: String,              // YYYY-MM-DD
app/src/main/java/com/example/data/BookingRecord.kt:12:    val leistungsdatum: String = "",     // YYYY-MM-DD
app/src/main/java/com/example/data/BookingRecord.kt:13:    val zahlungsdatum: String = "",      // YYYY-MM-DD
app/src/main/java/com/example/data/BookingRecord.kt:14:    val buchungsdatum: String,           // YYYY-MM-DD
app/src/main/java/com/example/data/DrivePersistenceRepository.kt:624:            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
app/src/main/java/com/example/data/DrivePersistenceRepository.kt:1835:                createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
app/src/main/java/com/example/data/DrivePersistenceRepository.kt:1903:                createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()),
app/src/main/java/com/example/data/DrivePersistenceRepository.kt:1904:                updatedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()),
app/src/main/java/com/example/data/DrivePersistenceRepository.kt:1905:                lastSyncedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
app/src/main/java/com/example/data/DrivePersistenceRepository.kt:2342:        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
app/src/main/java/com/example/data/DrivePersistenceRepository.kt:2477:        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
app/src/main/java/com/example/data/DrivePersistenceRepository.kt:3264:            val updatedReceipt = receipt.copy(imageUrl = targetFile.absolutePath, lastSyncedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()))
app/src/main/java/com/example/data/DrivePersistenceRepository.kt:3429:                datum = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
app/src/main/java/com/example/data/DrivePersistenceRepository.kt:3571:                    createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()),
app/src/main/java/com/example/data/DrivePersistenceRepository.kt:3572:                    updatedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()),
app/src/main/java/com/example/data/DrivePersistenceRepository.kt:3573:                    lastSyncedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
app/src/main/java/com/example/data/DrivePersistenceRepository.kt:3736:            val nowStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
app/src/main/java/com/example/data/DrivePersistenceRepository.kt:3764:                createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
app/src/main/java/com/example/data/TaxPropertyCalculator.kt:65:    private val germanDate = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).apply { isLenient = false }
app/src/main/java/com/example/data/ManagedDocumentService.kt:50:            documentDate = LocalDate.now().toString(),
app/src/main/java/com/example/data/ManagedDocumentService.kt:53:            storedFilename = DocumentFilenameGenerator.document(ManagedDocumentType.SONSTIGES, LocalDate.now().toString(), originalName.substringBeforeLast('.'), extension, id),
app/src/main/java/com/example/data/Beleg.kt:20:    val datum: String, // e.g. YYYY-MM-DD
app/src/main/java/com/example/util/DatevExporter.kt:199:        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMANY).format(Date())
app/src/main/java/com/example/util/AdvisorPackageBuilder.kt:47:        val timestampIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMANY).format(Date())
app/src/main/java/com/example/ui/ReceiptViewModel.kt:4633:                val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
app/src/main/java/com/example/ui/ReceiptViewModel.kt:4667:                    val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
app/src/main/java/com/example/ui/ReceiptViewModel.kt:4788:                    "yyyy-MM-dd'T'HH:mm:ss",
app/src/main/java/com/example/ui/LogbookFeature.kt:113:    aussteller = "Manuelle Fahrt", datum = LocalDate.now().toString(), uhrzeit = "",
app/src/main/java/com/example/ui/UnifiedUnitsFeature.kt:1105:    var effectiveDateText by remember { mutableStateOf(LocalDate.now().toString()) }
app/src/main/java/com/example/ui/UnifiedUnitsFeature.kt:1140:                                effectiveDateText = LocalDate.now().toString()
```

## Geänderte Dateien

- `app/src/androidTest/java/com/example/e2e/AppDateCalendarEmulatorTest.kt`
- `app/src/androidTest/java/com/example/e2e/AppDateDisplayEmulatorTest.kt`
- `app/src/androidTest/java/com/example/e2e/AppDatePersistenceEmulatorTest.kt`
- `app/src/androidTest/java/com/example/e2e/EmulatorTestSupport.kt`
- `app/src/androidTest/java/com/example/e2e/Phase6BWorkflowEmulatorTest.kt`
- `app/src/androidTest/java/com/example/e2e/PropertyPersistenceEmulatorTest.kt`
- `app/src/androidTest/java/com/example/e2e/TenantChronologyEmulatorTest.kt`
- `app/src/main/java/com/example/api/GoogleDriveClient.kt`
- `app/src/main/java/com/example/ui/AfaPortfolioFeature.kt`
- `app/src/main/java/com/example/ui/AnnualTaxAssistantFeature.kt`
- `app/src/main/java/com/example/ui/AppDateField.kt`
- `app/src/main/java/com/example/ui/BankFeature.kt`
- `app/src/main/java/com/example/ui/BankPhase2CPanel.kt`
- `app/src/main/java/com/example/ui/BankPhase2DReviewPanel.kt`
- `app/src/main/java/com/example/ui/BankRentFeature.kt`
- `app/src/main/java/com/example/ui/BankTransactionSplitFeature.kt`
- `app/src/main/java/com/example/ui/DocumentFeature.kt`
- `app/src/main/java/com/example/ui/DocumentPresentation.kt`
- `app/src/main/java/com/example/ui/ImmobilienManagerFeature.kt`
- `app/src/main/java/com/example/ui/InitialRentBatch.kt`
- `app/src/main/java/com/example/ui/LedgerOverview.kt`
- `app/src/main/java/com/example/ui/LedgerPresentation.kt`
- `app/src/main/java/com/example/ui/LoanFeature.kt`
- `app/src/main/java/com/example/ui/LogbookFeature.kt`
- `app/src/main/java/com/example/ui/LogbookManagement.kt`
- `app/src/main/java/com/example/ui/PropertyTaskFeature.kt`
- `app/src/main/java/com/example/ui/ReceiptAppUi.kt`
- `app/src/main/java/com/example/ui/ReceiptDetailScreen.kt`
- `app/src/main/java/com/example/ui/ReceiptViewModel.kt`
- `app/src/main/java/com/example/ui/RenovationReviewFeature.kt`
- `app/src/main/java/com/example/ui/RentIncomeFeature.kt`
- `app/src/main/java/com/example/ui/RentOverviewPresentation.kt`
- `app/src/main/java/com/example/ui/RentPaymentReview.kt`
- `app/src/main/java/com/example/ui/TenantChronology.kt`
- `app/src/main/java/com/example/ui/TenantHistoryFeature.kt`
- `app/src/main/java/com/example/ui/UnifiedUnitsFeature.kt`
- `app/src/main/java/com/example/ui/UserInputValidation.kt`
- `app/src/main/java/com/example/util/AdvisorPackageBuilder.kt`
- `app/src/main/java/com/example/util/BookingValidationService.kt`
- `app/src/main/java/com/example/util/DateFormatUtils.kt`
- `app/src/main/java/com/example/util/DatevExportPolicy.kt`
- `app/src/main/java/com/example/util/DatevExporter.kt`
- `app/src/main/java/com/example/util/PdfExporter.kt`
- `app/src/main/java/com/example/util/RenovationAdvisorReport.kt`
- `app/src/test/java/com/example/ui/AppDateFieldTest.kt`
- `app/src/test/java/com/example/ui/Phase5BComposeWorkflowTest.kt`
- `app/src/test/java/com/example/ui/RentOverviewComposeTest.kt`
- `app/src/test/java/com/example/ui/TenantHistoryReliabilityTest.kt`
- `app/src/test/java/com/example/util/DateFormatUtilsTest.kt`
- `app/src/test/java/com/example/util/Phase5ADatevValidationTest.kt`
- `docs/APPWIDE_DATE_UX_AUDIT.md`
