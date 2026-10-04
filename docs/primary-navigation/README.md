# Globale Hauptnavigation

Basis-main: `6a24d45c538c6d9c2a3c1e115b85ef8b5c328614` (PR #102; zuvor #101 und #100).
Branch: `agent/global-primary-navigation`. Kein Merge.

## Ursache und Lösung

Der Bottom-Navigation-Callback setzte ausschließlich `currentScreen`. Ein StateFlow emittiert denselben Wert nicht erneut. Lokale Immobilien- und Mehr-Unterseiten blieben deshalb bei Reselect geöffnet. Der Activity-Backstack behandelte Hauptbereichswechsel außerdem wie normale Unterseitenbesuche.

Alle fünf Bottom-Navigation-Callbacks verwenden nun `ReceiptViewModel.navigateToPrimaryDestination`. Ein monotoner `PrimaryNavigationReset` mit Ziel und Generation verändert sich bei jedem Klick. Der bestehende gemeinsame Screen-Host ist über die Generation mit Compose `key` gekapselt. Nur dieser Inhalt wird neu aufgebaut; der Scaffold mit Kopf und Bottom Navigation bleibt bestehen. Keine Reset-Handler pro Fachscreen, kein zweiter Router.

Das schließt die lokalen UI-Zustände einschließlich `openedPropertyId`, `section`, Einheitendetails, `page`, Einstellungen/Regeldialog, Belegeditor, Bankdetails/-Dialoge, Dokumentdetails/-Vorschau, AfA-/Monitor-Unterseiten, Fahrtenbuch- und Ledger-Unterzustände. ViewModel-/Datenbankdaten und die ausgewählte Property-ID werden nicht gelöscht. Bereits laufende ViewModel-Speichervorgänge laufen weiter. Die beim Speichern gestartete Belegnavigation berücksichtigt die Generation: Ein abgeschlossenes Speichern überschreibt kein inzwischen explizit gewähltes Hauptziel. Der Ursprung eines bereits gestarteten Speicherns bleibt für die vorhandene Bankverknüpfung erhalten.

`MainActivity` leert bei primärer Navigation seine bestehende History und akzeptiert das Reset-Ziel unabhängig von der Beobachtungsreihenfolge der zwei StateFlows. Lokale BackHandler und Rückpfeile bleiben unverändert. Android-Zurück am neu geöffneten Root führt zum Dashboard; zurück am Dashboard wird dem normalen Activity-Verhalten überlassen. Ein vorher verlassenes Detail wird nicht erneut geöffnet.

Die aktive Markierung ist eindeutig: Dashboard → Start; Belegliste/-detail → Belege; Erfassung → Scannen; Immobilien/lokale Immobilien-Unterseiten → Immobilien; Mehr und dessen externe Ziele (Bank, Ledger, Mietübersicht, Steuerübersicht, Dokumente, Fahrtenbuch, DATEV) → Mehr. Reihenfolge, Labels, Farben, Icons, Maße, Scan-Button und Kopf werden nicht geändert.

## Regressionstest-Matrix

Jede Zeile wird gegen **alle fünf Ziele Start / Belege / Scannen / Immobilien / Mehr** mit echten Bottom-Navigation-Klicks geprüft; danach werden sichtbare Root-Inhalte, genau eine aktive Markierung, der globale Kopf und die Navigation geprüft. Nach dem Wechsel prüft System-Zurück den Dashboard-Fallback statt einer Rückkehr in das geschlossene Detail.

| Ausgang | Ziele | Besonderer Nachweis |
| --- | --- | --- |
| Immobilie → Einheiten → Einheit | alle fünf | Immobilien-Reselect |
| Mehr → AfA | alle fünf | Mehr-Reselect |
| Mehr → Sanierungs-Monitor | alle fünf | Mehr-Reselect |
| Mehr → Regeln | alle fünf | Mehr-Reselect |
| Mehr → Backup | alle fünf | Mehr-Reselect |
| Belegdetail | alle fünf | Belege-Reselect, Detail-ID geschlossen |
| Belegdetail → Inline-Editor | alle fünf | Editor wird verlassen |
| Bank → Buchungsdetails | alle fünf | keine Bankdetails im Root |
| Dokumentenakte → Dokumentendetail | alle fünf | keine Dokumentdetails im Root |
| Mieteingänge | alle fünf | Mehr korrekt aktiv |
| Einnahmen & Ausgaben | alle fünf | Mehr korrekt aktiv |
| DATEV | alle fünf | bestehender Exportbereich verlassen |
| Fahrtenbuch | alle fünf | bestehender Bereich verlassen |

Weitere Tests:
- Mehrfaches Wechseln und Reselect erzeugt keine alte Hauptseitenhistorie.
- Ein zentrales Hauptnavigations-Intent schließt das echte Einstellungen-Dialogfenster; es erscheint beim erneuten Öffnen von Mehr nicht wieder.
- Immobilien → Objekt → Einheit → Android-Zurück → Einheiten → Rückpfeil → Objekt → Zurück → Übersicht.
- Die bestehenden MainNavigationRegressionTest, MoreMenuComposeTest, UnitDocumentBackNavigationTest und ReceiptDetailComposeTest sichern lokale Rückwege weiterhin ab.
- Ein bereits ausgelöster Beleg-Speichervorgang mit Bankursprung wird trotz eines neuen Hauptziels fertiggestellt, behält seine Verknüpfung und öffnet den alten Bereich nicht wieder.
- Fixture-Daten: gespeicherte Belege, Bankbuchung, Dokument einschließlich Datei-/MIME-Verweisen, ausgewählte Immobilie, Wohneinheit, Mietwerte, Mieterhistorie und Backup-Präferenzen bleiben über die gesamte Klickmatrix unverändert.
- Unit-Test: jede AppScreen-Route hat genau ein zulässiges Hauptziel.
- Scannen-Reselect schließt eine alte Bank-Vorbelegung und öffnet ein frisches manuelles/Scan-Formular ohne alten Ursprung.

## Umfang und Grenzen

Produktionsänderungen ausschließlich in `MainActivity.kt`, `ReceiptAppUi.kt`, `ReceiptViewModel.kt` und `PrimaryNavigation.kt`. Neue Tests in `PrimaryNavigationComposeTest.kt` und `PrimaryNavigationTest.kt`. `LedgerComposeTest.kt` erhält eine isolierte Daten-Fixture; seine fachlichen und visuellen Assertions bleiben unverändert. Die neue Navigations-Fixture wird vor und nach jedem Test bereinigt, damit keine gecachten Legacy-IDs andere Klassen beeinflussen.

Keine Room-Migration, kein Datenmodellwechsel; keine Änderungen an Bankberechnungen/-liste, DATEV-Export, Drive/Backup/Restore, Belegklassifikation oder Immobilien-Fachlogik. AfA-/Fahrtenbuchpersistenz bleibt durch unveränderte Schreibwege und die vollständige vorhandene Testsuite abgesichert; die Klickmatrix führt dort keine fachlichen Schreiboperationen aus.

Native Robolectric-/Compose-Tests bei 393 × 852 dp, keine Hardware-Instrumentierung. Android-Dialoge sind modal und empfangen Touch-Eingaben in einem eigenen Fenster: Das Dialogtest-Intent wird über dieselbe zentrale Funktion ausgelöst, ohne einen physischen Klick durch die Dialogfläche vorzutäuschen. Externe Dateiauswahl-/Viewer- und Android-Systemfenster gehören nicht zum App-Scaffold. Unbestätigte Formulare folgen dem bestehenden Verlassensverhalten. ViewModel-verwaltete fachliche Prüfvorgänge bleiben erhalten; diese werden nicht als Datenbereinigung verworfen. Es wurden keine echten Cloud- oder Exportvorgänge gestartet.

## Prüfergebnisse

- Gezielter Prüflauf: **28 Tests bestanden, 0 Fehler, 0 übersprungen** (18 neue Compose-Tests, 1 neuer Unit-Test, 4 vorhandene MainNavigationRegressionTest und 5 vorhandene MoreMenuComposeTest).
- Klickmatrix: 13 Ausgangsansichten × 5 Ziele = **65 echte Bottom-Navigation-Wechsel**.
- `git diff --check`: bestanden.
- Quellvergleich: globaler Kopf byte-identisch zur Basis; Bottom-Navigation-Oberfläche byte-identisch nach Ausblenden der beiden ausdrücklich geänderten Verhaltensstellen (Klick/aktive Zuordnung).
- Vollständige Unit-/Compose-Suite: **631 Tests bestanden, 0 Fehler, 0 übersprungen**. Keine Tests deaktiviert.
- Android Lint: **0 Fehler / 98 Warnungen** im Gesamtprojekt.
- Debug-APK: `assembleDebug` erfolgreich, **33,705,837 Byte**.
- Gemeinsamer abschließender Gradle-Lauf: **BUILD SUCCESSFUL** (`testDebugUnitTest`, `lintDebug`, `assembleDebug`), 2m 14s.
- Daten-Fixtures werden explizit isoliert; die bestehenden Ledger- und Legacy-Assertions bestehen unverändert.
