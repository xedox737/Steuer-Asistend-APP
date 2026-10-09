# Phase 5B – Workflow-Konsistenz F06–F17

## Grundlage und Grenzen

- Ausgangs-main nach erneutem Fetch am 09.10.2026: `ea5ac12e85b3eedfda4e8ba83d5219510e531145`.
- Branch: `fix/phase5b-workflow-consistency`; Ausgangsstatus sauber, keine Änderungen in main.
- Vollständig gelesen: beigefügte Arbeitsanweisungen, Martin-Weber-Produkttest vom 08.10.2026,
  Repository-AGENTS, Code-/Prüfwegweiser, Phase-5A-Dokumentation und Beschreibung von PR #116.
- #116 ist gemergt. Seine Konflikt-, Original- und Importregeln bleiben erhalten.
- Keine Banklisten-Neugestaltung, kein neuer Haupt-Navigationsweg, keine Änderung von
  DATEV-Kontierung, Kostenstellen, Freigabe, Jahreslogik oder Restore. Nicht mergen.

## Ursachenanalyse vor Codeänderungen

| Finding | Aktuelle Ursache und vorgesehene Korrektur |
| --- | --- |
| F06 | `LedgerScreen` übergibt ausschließlich Receipt-Flow; `LedgerPresentation` summiert Belegbeträge. Bestätigungen liegen getrennt in `bank_rent_assignments`, mit CONFIRMED/REVIEW und USER_CONFIRMED/MANUAL. Nur normale positive MATCHED/PARTIAL-Transaktionen mit gültigen bestätigten Zuweisungen zählen in `RentTrackingLogic`. Die dort vorhandene stabile Zuordnungs-/Beleglink-Abdeckung wird als gemeinsame lesende Projektion wiederverwendet. Finanzsicht unterscheidet Zahlungsfluss und freigegebene Buchungen; Bankanteile erhalten das tatsächliche Buchungsdatum, Mietkontrolle behält rentMonth. Keine Belegerzeugung oder neue DATEV-Freigabe. |
| F07 | `RentOverviewPresentation.year.unassigned` enthält nur bestätigte Mietbelege ohne Einheit. `Zahlungen prüfen` öffnet pauschal die Belegliste. Bankkandidaten aus bestehender Mietklassifikation/Zuordnungsprüfung fehlen. Gemeinsame Prüfliste für Belege, offene/review Bankkandidaten und Restbeträge, mit vorhandenen Detailwegen; PRIVATE/IGNORED/TRANSFER und sonstige klassifizierte Eingänge bleiben ausgeschlossen. Keine automatische Zahlungsbestätigung. |
| F08 | `TenantChangeDialog` prüft gültige Termine und Neubeginn nach Auszug, aber nicht Ende nach Altbeginn und übrige Perioden. `changeTenant` beendet alle endlosen Perioden ungeprüft. Historien-UI verwendet endDate-leer als AKTUELL, obwohl Zukunft/abgelaufene datierte Verträge abweichen. Gemeinsame Chronologieprüfung vor Schreiben, feldbezogene Fehler, eindeutiger betroffener Vertrag und aktueller Status über `currentAt`. Statusende wird gegen dieselben Vertragsgrenzen geprüft. |
| F09 | Der Test nennt den einfachen `PropertyEditScreen` ohne Kaufdaten und das globale `PropertyMetadataFormDialog` mit Kauf-/Steuerdaten. Auf aktuellem main ruft `PropertyData` bereits das globale Formular auf; der unvollständige einfache Editor ist unbenutzt. Das gemeinsame Formular vermisst jedoch Objektart/Notizen und ist implizit an die globale Auswahl gebunden. Vollständiges gemeinsames Formular ausdrücklich am Objekt binden, fehlende Felder ergänzen und unbenutzten Parallel-Editor entfernen. Bestehendes PropertyMetadata bleibt Quelle. |
| F10 | Wizard erzeugt Einheiten mit Leerstand; Mietdaten werden anschließend einzeln im Detail bzw. Mietplan erfasst. Vorhanden: `WohneinheitStatus`, `TenantPeriod`, `TenantHistoryStore`, `PropertyUnitScopedData`, deutsche Betrags-/Datumsprüfung, LazyColumn, gemeinsame Felder/Dialogs. Optionale kompakte Erstbefüllung in Einheitenübersicht; Zeilen separat bearbeiten, alle Änderungen vor Schreiben validieren und bestätigen. Bestehende Verträge werden gesperrt, niemals überschrieben. |
| F11 | Dashboard liest `bankStatementResult?.missingReceiptsCount ?: 0` und beschriftet dies als Offene Belege. Beleg-Prüfstatus UNGEPRUEFT/ZU_PRUEFEN und Freigabestatus sind unabhängig. Live-Prüfbestand und live fehlende Bankbeziehungen werden getrennt gezählt; offene unklassifizierte Bankeinträge bleiben ausdrücklich noch nicht geprüft. Das optionale alte KI-Analyseergebnis ist keine Kennzahlenquelle. |
| F12 | `yearReceipts` speist totals, Vorjahrsvergleich, Chart und Kategorien; `filtered` speist nur Liste. Alle sichtbaren Summen und Diagramme verwenden dieselbe gefilterte Projektion wie die Liste; Filterbasis wird benannt. |
| F13 | `UnifiedUnitOverviewCard` verwendet für Monatscheck und Mieterwechsel `onOpen`. Jahresübersicht zeigt Details anzeigen als Text/Icon ohne Aktion. Direkt die vorhandenen Dialoge öffnen, Detailaktion konkret an vorhandene Mietübersicht anbinden; bestehende Zurück-/Dialogwege erhalten. |
| F14 | Wizard löscht Nicht-Ziffern und erzeugt Listen synchron in onValueChange; -1/2,5 werden umgedeutet. `PropertyWizardInput` und beide Editoren erlauben jedes nicht negative Jahr; einfacher Editor prüft Sternchen nicht. Rohtext bewahren, positive Ganzzahlen und plausibles optionales Baujahr gemeinsam prüfen, Pflichtfelder durchsetzen. Einheitenlisten erst nach bestätigter gültiger Zahl außerhalb des UI-Threads vorbereiten und lazy anzeigen. |
| F15 | Belegliste enthält Gemini KI-Suche in Room-Belegen und Belegsdatenbank. Nutzersprache: Belege mit KI durchsuchen; keine Implementierungsbegriffe. |
| F16 | `executeWizardExport` ruft unabhängig von targetFormat `AdvisorPackageBuilder.buildPackage` auf, nur includeOriginals wechselt. Ergebnis/Teilen sind auf zipFile/application-zip festgelegt. Manifest zählt fileItems statt records. CSV erhält eigenen Output derselben geprüften Serializer-/Validator-Pipeline, korrektes Dateisuffix/MIME und Auditcount aus records. ZIP bleibt bestehender Paketweg. |
| F17 | Schritt 5 verspricht vier alte Ordner; Builder verwendet neun `AdvisorPackageStructure.requiredFolders` von 00_Start bis 08_Pruefprotokoll. UI-Beschreibung aus dieser gemeinsamen Quelle ableiten. |

## Prüfplan

Neue fachliche und Compose-Regressionen F06–F17, einschließlich Bank-/Beleg-Abdeckung,
Splits, REVIEW/Privat, Objekt-/Einheitenisolation, Vertragsgrenzen/Zukunft, Serienbefüllung,
Live-Prüfbestand, konsistenter Filter, direkten Aktionen, Rohtexteingabe, CSV-Datei/Audit und ZIP-Struktur.
Alle Phase-5A-Regressionen laufen unverändert im vollständigen testDebugUnitTest mit.

Die lokale Umgebung hat JDK 17, aber keinen Gradle/Android-SDK. Vollständige Prüfung
über die bestehende GitHub Android CI: testDebugUnitTest (einschließlich Compose/Navigation),
lintDebug, assembleDebug. Keine Tests deaktivieren oder Erwartungen abschwächen.
Die endgültigen Ergebnisse des letzten Head-Commits sind im [PR #117](https://github.com/xedox737/Steuer-Asistend-APP/pull/117)
dokumentiert; seine CI-Prüfung ist vor einer späteren Zusammenführung maßgeblich.

## Umsetzung und Regressionen

| Finding | Ergebnis und ausführbare Prüfung |
| --- | --- |
| F06 | `LedgerPaymentPresentation` zeigt bestätigte Bankmieten im Zahlungsfluss, unabhängig von einer Beleganlage. Gemeinsame `RentPaymentProjection` verwendet die bestehende Komponenten-/Link-Abdeckung vor Datums- und Objektfiltern. `Phase5BPaymentConsistencyTest`: 980 ohne Beleg, stabil verknüpft weiterhin 980, getrennte identische Beträge ohne Beziehung, REVIEW/Privat/Umbuchung, Kaltmiete/NK/Kaution, Objekt-/Einheitenisolation, keine Freigabe. |
| F07 | `RentPaymentReview` verbindet ungeklärte Belege, Bank-Mietkandidaten und unzugeordnete Restbeträge. 1.020 offen bleibt außerhalb bestätigter Einnahmen; 500 zugeordnet lässt 520 offen, auch mit spiegelndem Beleglink. Fachtests sowie Jahres-/Monatscheck → Bankdetails → Rückkehr in `Phase5BComposeWorkflowTest`; bestehender Miet-Compose-Test prüft zusätzlich Belegdetails und Rückkehr. |
| F08 | `TenantChronology` prüft Altbeginn/Altende, neuen Beginn und sämtliche Intervalle vor Schreiben. `Phase5BContractInputTest` prüft 2021-04-01 / 2020-03-31, Überschneidungen, echte Kalendertermine, zukünftige Verträge und erhaltene IDs/rentChanges. Compose prüft feldbezogenen Altende-Fehler und unveränderte Historie. |
| F09 | Objekt-Stammdaten verwenden ausdrücklich dasselbe vollständige Formular wie die Verwaltung. Name, Adresse, Objektart, Baujahr, Fläche, Grundstück, Kaufdatum/-preis, Steuer-/AfA-Felder und Notizen bleiben im vorhandenen `PropertyMetadata`. Compose speichert Kaufdatum/-preis, Objektart und Notizen und prüft Property-/Unit-IDs. |
| F10 | Optionale Sammelerfassung in der vorhandenen Einheitenübersicht: markierte Zeilen mit Mieter, Beginn, Kaltmiete, NK, Sonstiges und Status; Vorschau nennt jede betroffene Einheit. `Phase5BInitialRentPersistenceTest` speichert 25 Einheiten, lädt aus bestehenden Stores neu und schützt vorhandene/ungültige Historien, fremde Einheiten und zukünftige Verträge. Fehler in einer Zeile verhindern die gesamte Erstbefüllung. Compose prüft Tabelle, Vorschau und Speichern. |
| F11 | Dashboard trennt aktuellen Belegprüfbestand und fehlende Bankbeziehungen. Nicht klassifizierte offene Bankeinträge machen den Prüfstand ausdrücklich unsicher. Fachtest prüft UNGEPRUEFT ≠ 0, bestätigte Links und ungeklärte Bankeinträge; Compose prüft die tatsächliche Dashboard-Beschriftung. |
| F12 | Liste, Kennzahlen, Kategorien, Jahresdiagramm und Vorjahrsvergleich verwenden dieselben Filter. Fachtest: A 1.000 + B 2.000 = 3.000, Filter A = 1.000; weitere Typ-/Kategorie-/Zeitraumfilter. Compose prüft die sichtbare Kennzahl und erhaltenen Objektfilter nach Bankdetail-Rückkehr. |
| F13 | Schnellaktionen öffnen vorhandenen Monatscheck bzw. Mieterwechsel direkt. „Details anzeigen“ öffnet die vorhandene objektbezogene Mietübersicht, Zurück führt zu den Einheiten. Compose prüft konkrete Dialogtitel und Rückziele. |
| F14 | Rohtext bleibt erhalten. Positive Ganzzahl bis 10.000: Schutz der Anzahl vorzubereitender Einheiten/Preference-Schlüssel, kein stilles Klemmen; Vorbereitung auf Default-Dispatcher und Lazy-Anzeige. Baujahr optional, bei Angabe 1000 bis aktuelles Jahr + 1; Sternchenfelder verpflichtend. Fach-/Compose-Tests blockieren -1, 2,5 und 9999 als Baujahr. |
| F15 | Nutzertexte der Belegsuche, Lade-/Ergebnisanzeige, lokalen Treffer-/Nulltreffer-Auswertung und des Leerzustands enthalten kein Room. Compose prüft die angezeigten Texte und erzeugt die lokalen Suchergebnisse ohne API-Schlüssel oder Netzaufruf. |
| F16 | `EXTF_CSV` verwendet denselben vorhandenen Serializer/Formatvalidator und erzeugt direkt UTF-8-BOM-CSV mit `text/csv`; der ZIP-Builder wird dabei nicht aufgerufen. DATEV-Fachlogik bleibt davor unverändert. Fachtest prüft Datei, Datensätze, MIME, Prüfsumme; Compose prüft Assistent, Audit, echten Share-Intent und lesbare CSV-URI. ZIP-Manifest zählt zwei Buchungen auch bei null Originalen. |
| F17 | Formatbeschreibung wird aus `AdvisorPackageStructure.requiredFolders` abgeleitet. ZIP-Test prüft alle neun wirklichen Ordner, Manifestzählung und Übereinstimmung mit der Beschreibung. |

## Datenintegrität und Navigation

- Room-Version weiterhin 34, Supplemental-Version weiterhin 14. Keine Schemaänderung,
  destructive Migration, neue Tabellen oder zusätzliche Property-Metadaten.
- Property-, Unit-, Receipt-, Bank- und bestehende TenantPeriod-IDs bleiben erhalten.
  Erstbefüllung erzeugt nur neue Vertragsperioden für bewusst ausgewählte leere Einheiten.
  Die Vertragsperioden aller vermieteten Zeilen werden in einer gemeinsamen Preference-Schreibung gesichert;
  bestehende Unit-/Mietplan-Stores bleiben die kompatiblen Nebenquellen.
- Zahlungsprojektion und Prüfkennzahlen sind lesend. Keine Belege werden erzeugt/entfernt
  oder DATEV-Freigaben automatisch gesetzt. Keine Änderungen an Kontierung, KOST1/KOST2,
  Readiness, Jahreslogik, Backup-/Restore oder Bankimport-Identität.
- App-/Android-Zurück aus Bankdetails kehrt zum Finanz-/Miet-Ursprung zurück.
  Bestehender primärer Navigationsreset bleibt erhalten; lokale Screen-/Objekt-/Filterzustände
  verwenden SaveableState. Monatscheck bleibt bei Bankdetail-Rückkehr geöffnet.
- Objekt → Stammdaten → Bearbeiten, Einheit → Monatscheck/Mieterwechsel,
  Einheit → Jahresdetails → Einheiten, Finanzen → Bank → Finanzen sowie Belegdetails-/DATEV-Rückwege
  werden durch neue und bestehende Compose-/Navigationstests geprüft.

## Prüfbestand

34 neue Tests in fünf Klassen: `Phase5BPaymentConsistencyTest` (13),
`Phase5BContractInputTest` (6), `Phase5BInitialRentPersistenceTest` (5),
`Phase5BComposeWorkflowTest` (8), `Phase5BDatevOutputTest` (2).
Der bestehende `RentOverviewComposeTest` behält seine Monats-/Historienprüfungen und prüft nun
den verlangten gemeinsamen Prüfbereich mit Belegdetail-Rückkehr statt der alten pauschalen Beleglisten-Weiterleitung.
Keine Deaktivierung und keine Abschwächung von Erwartungen.

Unveränderte Phase-5A-Regressionen: `Phase5ARestoreConflictTest` (MERGE local wins),
`Phase5AUnitStateTest`, `ReceiptOriginalPersistenceTest` (Originalbytes/Neustart),
`Phase5AMultipleOriginalExportTest` (alle Seiten), `Phase5ADatevValidationTest`,
`Phase5ABankImportTest` (strikte Kalender-/Betragsvalidierung und Dateiname-Dedup),
`Phase5ABankImportWorkflowTest`. Zusammen 26 Tests, zusätzlich bestehende Restore-/Bank-/DATEV-Suiten.

Native Robolectric-Dialogtests verwenden die vorhandene Application-Isolation, einen je Test
frischen FileProvider-Pfadcache und denselben
device-sized Fensterrahmen wie die bestehenden Mietplan-Tests. Dies verändert keine Produktionsfenster,
Timeouts oder Erwartungen. Roborazzi-Aufnahmen sind im CI-Artefakt `android-reports` enthalten.

## Geänderte Dateien

Alle Dateinamen sind relativ zu `app/src/main/java/com/example/`, sofern nicht anders angegeben.

| Bereich | Dateien |
| --- | --- |
| UI/Workflow | `ui/BankFeature.kt`, `ui/DashboardReviewPresentation.kt`, `ui/ImmobilienManagerFeature.kt`, `ui/InitialRentBatch.kt`, `ui/LedgerOverview.kt`, `ui/LedgerPaymentPresentation.kt`, `ui/PropertyUnitScopedData.kt`, `ui/ReceiptAppUi.kt`, `ui/ReceiptViewModel.kt`, `ui/RentIncomeFeature.kt`, `ui/RentOverviewPresentation.kt`, `ui/RentPaymentProjection.kt`, `ui/RentPaymentReview.kt`, `ui/RentTenantWrapper.kt`, `ui/TenantChronology.kt`, `ui/TenantHistoryFeature.kt`, `ui/UnifiedUnitsFeature.kt`, `ui/UserInputValidation.kt` |
| Export | `util/AdvisorAnnualPackageContent.kt`, `util/AdvisorPackageBuilder.kt`, `util/DatevCsvOutput.kt`, `util/ReceiptManifestService.kt` |
| Tests unter app/src/test/java/com/example/ | `ui/Phase5BComposeWorkflowTest.kt`, `ui/Phase5BContractInputTest.kt`, `ui/Phase5BInitialRentPersistenceTest.kt`, `ui/Phase5BPaymentConsistencyTest.kt`, `ui/RentOverviewComposeTest.kt`, `util/Phase5BDatevOutputTest.kt` |
| Dokumentation | `docs/PHASE5B_WORKFLOW_CONSISTENCY.md` |

## Praktische Prüfgrenzen

Die Sammelerfassung dient der Erstbefüllung; vorhandene Verträge werden über Mietverlauf/Mieterwechsel
bearbeitet. Der Zahlungsfluss enthält erfasste Belege und bestätigte Bankmieten; steuerlich freigegebene
Buchungen sind eine eigene Ansicht. CI/Native-Robolectric ersetzen keinen vollständigen Martin-Weber-
Geräteretest. Zuerst PR prüfen, **nicht mergen**; vollständiger Retest auf neuem main erst nach einer
separat beauftragten Zusammenführung.
