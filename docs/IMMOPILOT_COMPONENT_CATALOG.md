# ImmoPilot Development Kit – Komponentenkatalog (Phase 1)

Stand der Sichtung: main `4cddcfd0825fe243a3af6f4f52ed8766387f3c88` (20.11.2026). **Bestandsdokumentation, keine Implementierungs- oder Designfreigabe.** Vor jeder Umsetzung die Symbole und Aufrufer im aktuellen Branch erneut prüfen. Einstieg: [CODE_MAP.md](CODE_MAP.md); verbindliche Regeln: [AGENTS.md](../AGENTS.md) und [VERIFY_CHANGES.md](VERIFY_CHANGES.md).

## Bestehende wiederverwendbare Bausteine

| Baustein / Symbol | Quellpfad | Nutzung / wichtige Grenze |
| --- | --- | --- |
| `MyApplicationTheme`, `LightColorScheme`, `DarkColorScheme` | `app/src/main/java/com/example/ui/theme/Theme.kt` | Bestehendes Material-3-Farbschema und Formen nutzen; dynamische Farben sind standardmäßig deaktiviert. Keine zweite Markenpalette anlegen. |
| `Typography` | `app/src/main/java/com/example/ui/theme/Type.kt` | Bestehende Text-Hierarchie; Android-Schriftvergrößerung prüfen. |
| `Ui2` | `app/src/main/java/com/example/ui/Ui2Components.kt` | Gemeinsame Präsentationswerte: 16 dp Kartenradius, 12 dp Iconradius, 8 dp Kontrollradius; `padding=8.dp`, `spacing=6.dp`. Keine globalen Änderungen für Einzel-Screens. |
| `Ui2Section` | `ui/Ui2Components.kt` | Abschnittskarte mit Titel und Inhalt; `internal` im Paket `com.example.ui`. |
| `Ui2Grid`, `ui2GridColumns` | `ui/Ui2Components.kt` | Responsives Raster, bei zu wenig Platz / großer Schrift einspaltig; nicht ungeprüft durch starre 2-Spalten-Raster ersetzen. |
| `Ui2Action`, `Ui2ActionGrid`, `Ui2ActionCard` | `ui/Ui2Components.kt` | Wiedererkennbare Schnellaktionen mit Icon, Titel, Untertitel und Callback; `Ui2Action` hat Paket-Sichtbarkeit. |
| `Ui2Metric` | `ui/Ui2Components.kt` | KPI-/Statusdarstellung; Werte aus vorhandener State-/Fachlogik einspeisen, keine zweite Berechnung in UI. |
| `Ui2Destination` | `ui/Ui2Components.kt` | Navigierbare Zielkarte; bestehende Navigation nicht umgehen. |
| `AppDateField`, `rememberDateFieldValidation` | `ui/AppDateField.kt` | Deutsches Eingabedatum, Kalenderauswahl, ISO-Werte nach gültiger Eingabe, Validierung und Semantik; keine parallele freie Datumseingabe erfinden. |
| `PRIMARY_NAVIGATION_SCREENS`, `primaryNavigationDestination`, `PrimaryNavigationReset` | `ui/PrimaryNavigation.kt` | Hauptziele, Tab-Rückfallziele und Reset-Intents; bestehende Backstack-/Zurück-Konvention respektieren. |

Pfade mit Präfix `ui/` beziehen sich auf `app/src/main/java/com/example/ui/`. Die obigen Symbole wurden im angegebenen main-Stand im Quellcode überprüft; **nicht** jede Verwendung in allen Screens wurde einzeln abgenommen.

## Screen-Referenzen vor einer UI-Aufgabe

| Aufgabe | Vorhandener Einstieg / Referenz |
| --- | --- |
| Hauptlayout, Navigation, Erfassung, Belege | `ui/ReceiptAppUi.kt`, `ui/PrimaryNavigation.kt` |
| Immobilien / Einheiten | `ui/ImmobilienManagerFeature.kt`, `ui/UnifiedUnitsFeature.kt` |
| Mieten / Kontrolle | `ui/RentIncomeFeature.kt`, `ui/RentOverviewPresentation.kt` |
| Bank | `ui/BankFeature.kt` und `docs/bank-ui-reference-design.md`; Bankbuchungsliste nicht ohne ausdrücklichen Auftrag ändern |
| Finanzübersicht | `ui/LedgerOverview.kt`, `ui/LedgerPresentation.kt` |
| Dokumente / Belege | `ui/DocumentFeature.kt`, `ui/ReceiptDetailScreen.kt` |
| Einstellungen | `ui/AppSettingsScreen.kt`, `docs/app-settings/` |
| Top-/Bottom-Navigation | `ui/ReceiptAppUi.kt`, `ui/PrimaryNavigation.kt`; globalen Rahmen nicht ohne Auftrag ändern |

## Design- und Implementierungsentscheidungen

- **Bestehende Markenidentität zuerst:** Blau als bestehende primäre Aktion und Grün/Mint als Akzent nach tatsächlichem Theme; keine pauschale violette Neugestaltung. Für weitere Akzentfarben die bereits eingesetzten Screens vergleichen, keine abweichende Palette allein aufgrund dieses Dokuments festlegen.
- **Material 3 / Expressive selektiv:** Nur verfügbare, zum Projekt passende APIs nutzen. M3 Expressive ist kein Freibrief zum Austauschen der bestehenden Navigation, Karten oder Dialoge.
- **Layouts:** Touch-Ziele, Schriftfaktor 1.0/1.3/1.5, schmale Geräte, Tastatur/Scrollbarkeit und lange deutsche Beschriftungen berücksichtigen. Die bestehenden `Ui2Grid`-Breakpoint-Regeln zuerst nutzen.
- **Status und Daten:** Lade-/Leer-/Fehlerzustände verständlich, keine technischen Room-/StateFlow-Begriffe in Nutzertexten. Vorschauen immer mit synthetischen Daten, keine echten Miet-/Bankinformationen.
- **Datenhaltung:** Compose ist Präsentation; bestehende ViewModels, Repositories, stabile Objekt-/Einheiten-IDs, Validierung und Freigabewege bleiben maßgeblich. Keine Datenmigration im Rahmen eines reinen UI-Auftrags.
- **Vorschau:** Bestehende Preview-/Screenshot-/Emulatortests suchen, bevor neue Infrastruktur angelegt wird. Fehlende Tests dokumentieren statt eine ungeprüfte Preview als Abnahme zu deklarieren.

## Pflegehinweis

Bei neuen gemeinsam nutzbaren Komponenten den Quellpfad, sichtbaren Zweck, Einsatzgrenzen und passenden Screenshot-/UI-Test hier ergänzen. **Keine** massenhafte Extraktion sämtlicher Screen-Composables oder ungezielte Komplett-Refactorings aus diesem Katalog ableiten.

## Gezielte Nachweise für Phase 1/2

Geprüft: `AGENTS.md`, `docs/CODE_MAP.md`, `docs/VERIFY_CHANGES.md`, vorhandener `immopilot-prompt-master`, `Ui2Components.kt`, `AppDateField.kt`, `PrimaryNavigation.kt`, `theme/{Theme,Color,Type}.kt` und Repository-Dateibaum. Nur Dokumentation und Skills werden angelegt; Fachlogik, Datenmodelle, Migrationen, Navigation und Design-Quellcode bleiben unverändert.
