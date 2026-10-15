# Phase 9D – AfA-Basis und Stichtagsprognose

Base: `e61520cf798484c34f6bf2dbec55bea01280b934` (frisch über GitHub geprüftes origin/main).
Branch: `fix/phase9d-afa-basis-cutoff-forecast`.
Lokales `git fetch origin` scheiterte an der nicht erreichbaren Proxyverbindung; der aktuelle Main-Commit wurde über den GitHub-Connector abgerufen und mit identischem Git-Objekthash lokal übernommen.

## N04: bestätigter Stand und Monatsprognose

Die aktive AfA-Portfolioansicht startete nach jedem bestätigten Stichtag bei `cutoff.year + 1`. Damit entfiel bei unterjährigen Bestätigungen der verbleibende Teil des Stichtagsjahres. Der Store erlaubt solche Stichtage; bestehende Tests definierten nur Jahresendstände.

Die vom Auftraggeber ausdrücklich bestätigte Semantik lautet: **Der Stichtagsmonat gilt als abgeschlossen, die Prognose beginnt im Folgemonat.** Keine taggenaue Abschreibung wird eingeführt. Beispiele:

| Bestätigter Stichtag | Erste Prognoseperiode |
|---|---|
| 30.06.2026 oder 15.06.2026 | Juli–Dezember 2026, 6 Monate |
| 31.12.2025 | Januar–Dezember 2026, 12 Monate |
| 01.01.2026 | Februar–Dezember 2026, 11 Monate |
| 29.02.2024 | März–Dezember 2024, 10 Monate |

`TaxPropertyCalculator.forecast` berechnet die unverändert zehn Kalenderjahreszeilen mit dem ersten Monatsanteil. Der UI-Adapter `afaForecast` übersetzt den bestätigten Stichtag in den Folgemonat. Ohne Bestätigung bleibt der Erwerbs-/AfA-Beginnmonat inklusive, wie in der bisherigen ersten Jahres-AfA. Eine Prognose beginnt nicht vor diesem Beginn. Ohne gültigen AfA-Beginn oder ohne gültige Basis gibt es keine erfundene Prognose.

Jede Zeile ist auf den verbleibenden Buchwert begrenzt. Die bestehende Standard-/bestätigte kürzere Nutzungsdauer bestimmt weiterhin die Jahres-AfA. Beispiel: 17.760 Euro jährlich und Stichtag 30.06. ergeben 8.880 Euro für 2026, höchstens den bestätigten Restbuchwert.

Die vorhandene Verlaufszeile kennzeichnet Teiljahre mit der Monatszahl. Der Bestätigungsdialog erklärt die Monatskonvention. `ConfirmedAfaValuesStore`, gespeicherte Quellen, Stichtage, Abschreibungen und Buchwerte werden nicht geändert oder automatisch neu bestätigt.

## A11: signierte Anschaffungsbelege und gemeinsame Basis

`TaxPropertyCalculator` clampte jeden einzelnen Anschaffungsbeleg vor der Aggregation auf null. Das entfernte fachliche Gutschriften. `RenovationReviewCalculator` verwendet auf dem aktuellen Main bereits diese zentrale Basis; dort war keine zweite Anschaffungsformel zu ersetzen.

Nur aktive, bereits als `Anschaffungskosten` klassifizierte Belege werden wie bisher einbezogen. Ihr Vorzeichen folgt dem bestehenden `DatevMappingService.isCreditNote`: negativer Betrag oder Beschreibung mit Gutschrift/Storno. Auch eine positive, ausdrücklich als Gutschrift bezeichnete Position vermindert die Basis entsprechend dieser vorhandenen Konvention. DATEV selbst wird nicht geändert. Unklassifizierte negative Belege werden nicht neu als Anschaffungskosten interpretiert.

Signierte Beträge werden ohne Einzel-Clamp mit der vorhandenen Gebäudequote aufgeteilt und aggregiert. Grund-/Gebäudeaufteilung, Kategorien, Löschstatus und Zeitraumregeln bleiben erhalten:

`Gebäudeanschaffungsbasis = Gebäude-Kaufanteil + Summe(signierte Kaufnebenkosten × Gebäudequote)`.

200.000 + 10.000 − 1.000 ergibt **209.000 Euro**, bei 2 % **4.180 Euro Jahres-AfA** und für die 15%-Prüfung **31.350 Euro**. Beide Ansichten konsumieren dieselbe `TaxPhase1Summary`.

Negative/ungültige Roh-Stammdaten, nichtendliche Anschaffungsbeträge oder eine negative/nichtendliche Gesamtbasis erzeugen einen ausdrücklichen `acquisitionBasisError`. Die blockierte Berechnung liefert keine negative AfA-/15%-Basis; abgeleitete Basis-/AfA-/Grenzwerte sind nullwertig, begleitet vom sichtbaren Fehler. Das ist **keine Reparatur gespeicherter Werte**: Originalbelege, signierte Einzelbelege, IDs und Bestätigungsrohwerte bleiben erhalten. Die Sanierungsprüfung übernimmt denselben Fehler in ihre bestehenden Prüfhinweise. Eine automatische steuerliche Einordnung/Freigabe entsteht nicht.

## Gezielte Nachweise

- `Phase9DAcquisitionBasisTest`: gemeinsame 209.000-Basis, 4.180-AfA, 31.350-Grenze; mehrere Gutschriften; Gebäudequote; positive Referenz; Kategorien-/Objekt-/Löschscope; ungültige negative Basis; vorhandene manuelle Kaufpreisvalidierung.
- `Phase9DForecastTest`: Restjahr, volle Folgejahre, Buchwertlimit, Erwerbsmonat, bestätigte kürzere Nutzungsdauer, fehlender Beginn, ungültige Basis/Buchwerte.
- `Phase9DCutoffContractTest`: tatsächlicher UI-Adapter für Jahresende, unterjährigen/untermonatlichen Stichtag, 01.01., Schaltjahr; unveränderte Store-Rohdaten und Objektisolation.
- `Phase9DAfaComposeTest`: echte MainActivity/Room/Preferences, korrigierte Basis und sichtbare Restjahreszeile.
- `Phase9DAfa10/13/15EmulatorTest`: echte App auf 393 dp mit FontScale 1.0/1.3/1.5; vollständige Fehler-/Hinweistexte, Screenshotbelege, Activity recreate, App- und Android-Zurück, unveränderte Bestätigungs-/Room-Daten sowie reparierbarer beschädigter Altbestand.

Verbindliche Prüfung: `git diff --check`, vollständiges `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest` und der vorhandene vollständige Android-Emulator-E2E-Job. Tatsächliche Run-/Head-Belege und Ergebnisse werden im PR und Abschlussbericht dokumentiert; angelegte Tests sind kein vorweggenommener Erfolgsnachweis.

## Grenzen und unveränderte Verträge

Room-Version **34**, keine Migration. Supplemental-Schema **15** und Receipt-Backupvertrag **2** unverändert. Keine Backup-, Restore-, Banklisten-, DATEV-Export-, Parser-, Navigations- oder Designsystemänderung. Unsichtbare Semantik-Tags unterstützen die Prüfung vorhandener UI-Elemente. Phase 9A/9B/9C und vorhandene AfA-/Sanierungstests bleiben Teil der vollständigen Regression. Keine neue AfA-Art, Steuerautomatik, Datenbereinigung oder Speicherung; kein Merge.
