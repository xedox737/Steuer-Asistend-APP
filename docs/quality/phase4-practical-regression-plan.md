# Phase 4 – Praxistest und Stabilisierung

**Basis:** `main` `c60b1325818db334486bf34a8b4f83765984cec4` (PR #114 bereits gemergt).  
**Arbeitsbranch:** `test/phase4-end-to-end-stabilization`. Keine Änderungen auf `main`, kein Merge ohne Auftrag.

## Abnahmestrategie

Grüne CI ist notwendige, nicht hinreichende Bedingung. Jede Prüfung benötigt **Ausgangsdaten → Aktion → erwartetes Ergebnis → Ist-Ergebnis → Beleg (Test/Log/Screenshot)**. Fehler werden zunächst reproduziert und dann ursächlich in kleinen Änderungen behoben.

| ID | Kritischer Pfad / Szenario | Automatisierbar | Manuelle Ergänzung | Risiko |
|---|---|---|---|---|
| P4-01 | 25 Einheiten anlegen, eindeutige IDs, Flächen und Positionen, App-Prozess neu starten | Robolectric/Room-/Prefs-Integration | Wizard mit 25 Einheiten auf Gerät, Scrollen, Speichern, erneut öffnen | hoch |
| P4-02 | Immobilie und Einheit wechseln, Dokument/Beleg und Bank nur dem richtigen Objekt zuordnen | Repository-/Compose-Regression | Fehlzuordnung durch schnelle Navigation versuchen | kritisch |
| P4-03 | Beleg scannen/importieren, editieren, neu öffnen, MIME/URI, Original unverändert | Service-/Robolectric-Tests | Kamera, PDF/JPG/PNG/WEBP auf Gerät | hoch |
| P4-04 | Bank CAMT Import/Reimport, Miete, Splits, Teilzahlung, Dublettenfreiheit | existierende Bank- und RentTracking-Tests + gezielte Integration | Detailaktionen und Rückweg | kritisch |
| P4-05 | Mieterwechsel, Miethistorie, monatsgenaue Soll/Ist und nachträgliche Korrektur | Historien-/Kontrolltests | Datums-/Formulargrenzen | kritisch |
| P4-06 | AfA bestätigt vs Prognose, Restbuchwert, Stichtag, ungültige Eingaben | Store-/Berechnungs-/Backup-Tests | Eingabedialog, Darstellung | hoch |
| P4-07 | DATEV EXTF/Konten/Kostenstellen/Freigabe, KI nur nach Bestätigung | Export-/Eligibility-Tests | ZIP in Buchhaltungssoftware einlesen | kritisch |
| P4-08 | Google Drive optional, Voll- und MERGE-Restore zweimal, IDs und Dateiverweise | Backup/Restore-Tests | Zweitgerät und Offline-Betrieb | kritisch |
| P4-09 | Fahrtenbuch CRUD, Kilometerstände, Zuordnungen, Neustart | DAO-/Store-Tests | GPS/Berechtigungen/Android-Zurück | hoch |
| P4-10 | Android Back/App-Pfeil, Bottom Navigation, Dialog und Unterseiten | PrimaryNavigationComposeTest | physisches Zurück, Rotation, Prozess-Neustart | hoch |
| P4-11 | 393×852 dp, 150 % Schriftgröße, kleine Breite, Scrollen, Tastatur, TalkBack | Robolectric-Semantik und Screenshot-Tests soweit möglich | visuelle Abnahme und TalkBack am Gerät | hoch |
| P4-12 | Offline, schlechte Dateien, Berechtigungen, Null-/SQLite-/Coroutines-Fehler | Fehlerzustands-/Migrations-Tests | Flugmodus, Datei ohne Rechte, langer Betrieb | hoch |

## Stufen

1. **Baseline:** neuesten main verifizieren; `lintDebug`, `testDebugUnitTest`, `assembleDebug`, Bericht hochladen. Bestehende Tests inventarisieren.
2. **Datenintegrität zuerst:** P4-01/02/04/05/06/07/08/09 – IDs, Restore, RentTracking, DATEV, keine destruktive Migration. Tests ergänzen, wo echte Lücken bestehen.
3. **Android-Workflows:** P4-03/10/11/12 – Navigation, Details, Scan, Dokumente, 150 %-Schriftgröße, Fehlerzustände. Screenshot-/Semantikprüfung ohne bestehendes Design zu ändern.
4. **Geräteabnahme:** APK auf realem Gerät testen. Manuelle Checks als **offen** markieren, solange keine Evidenz vorliegt.
5. **Merge-Gate:** alle neuen Tests und bisherige CI grün, keine bekannte kritische Regression; offene Gerätepunkte explizit dokumentiert. PR verbleibt Draft bis zur Freigabe.

## Ausgangsbefund

Die Android-CI des ersten Phase-4-Baseline-Commits auf `main` ist erfolgreich (zwei erfolgreiche `main`-Runs nach Merge #114). Die neuen Phase-4-Tests und Gerätetests sind davon **nicht** abgedeckt.

## Regeln

- Keine automatische Veränderung von Bankbuchungen, KI-Prüfergebnissen oder DATEV-Steuerdaten.
- Keine destruktiven Room-Migrationen, keine Neuanlage bestehender IDs, keine Datenlöschung.
- Keine Änderungen der globalen Navigation/Designsysteme ohne nachgewiesenen Fehler.
- Jede neue Regression reproduzierbar und isoliert. Offene Punkte ehrlich ausweisen.
