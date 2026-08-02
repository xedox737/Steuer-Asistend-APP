# Codex-Übergabe: Steuer-Assistent-App

Stand: 2. August 2026

## Verwendung auf dem neuen Rechner

1. Codex installieren und mit dem eigenen OpenAI-Konto anmelden.
2. Git und Android Studio installieren.
3. Für GitHub auf dem neuen Rechner einen eigenen SSH-Schlüssel einrichten.
4. Repository klonen und den Entwicklungsbranch öffnen:

```powershell
git clone git@github.com:xedox737/Steuer-Asistend-APP.git
cd Steuer-Asistend-APP
git switch agent/safe-duplicate-purge
```

5. Diese Datei an eine neue Codex-Aufgabe anhängen.
6. Den Auftrag am Ende dieser Datei als erste Nachricht verwenden.

Keine privaten SSH-Schlüssel, Keystores, Passwörter oder API-Schlüssel sind in dieser Datei enthalten.

## Projekt

- GitHub-Repository: `xedox737/Steuer-Asistend-APP`
- Hauptbranch: `main`
- Entwicklungsbranch: `agent/safe-duplicate-purge`
- Draft-Pull-Request: https://github.com/xedox737/Steuer-Asistend-APP/pull/3
- PR darf ohne ausdrückliche Freigabe des Benutzers nicht gemergt werden.
- Bestehende Referenz-Sicherheitsprüfung für gemeinsam verwendete Drive-Hauptdateien darf nicht entfernt, abgeschwächt oder umgangen werden.

## Ursprünglicher Fehler

Mehrere lokale beziehungsweise wiederhergestellte Belegdatensätze konnten dieselbe `mainDriveFileId` verwenden. Beim endgültigen Löschen blockierte die vorhandene Sicherheitsprüfung korrekt, weil die gemeinsame Drive-Hauptdatei noch von einem anderen Beleg referenziert wurde.

Beispiel der betroffenen Hauptdatei:

`1sUewRIEVf3wOE_x0mGXF5knu0tyhgr8R`

Die Ursache lag in wiederholten Restore-/Insert-Pfaden und fehlender kontrollierter Identitätsauflösung, nicht in der Sicherheitsprüfung.

## Umgesetzte Dubletten-Sicherheit

- Rein lesende Analyse von `ACTIVE`, `DELETED` und `DELETE_PENDING` nach `mainDriveFileId`.
- Einbeziehung von Room-Datensätzen, `receipt-index.json`, Tombstones und Metadatenreferenzen.
- Kanonische Auswahl nach Index-`internalId`, passender Metadaten-ID, Vollständigkeit, Alter und stabiler Room-ID.
- Papierkorb-Aktion „Dubletten sicher zusammenführen“ mit Vorschau und ausdrücklicher Bestätigung.
- Bei Einzelzusammenführung wird die gemeinsame Hauptdatei niemals gelöscht.
- Nichtkanonische Room-, Index- und Tombstone-Referenzen werden gezielt entfernt.
- Metadatendateien werden nur bei eindeutig nachgewiesener Referenzfreiheit entfernt.
- Vollständige Gruppenlöschung benötigt zwei Bestätigungen.
- Gruppenlöschung arbeitet in der Reihenfolge Referenzen → Metadaten → Hauptdatei genau einmal → lokale Datensätze.
- Persistente, idempotente Zustandsmaschine:

```text
PREVIEWED → CONFIRMED → REFERENCES_REMOVED → DRIVE_METADATA_REMOVED → MAIN_FILE_REMOVED → LOCAL_PURGED → COMPLETED
```

- Abgebrochene Vorgänge können fortgesetzt werden.
- Drive-Teilfortschritt wird gespeichert; bereits entfernte Dateien werden nicht schädlich erneut gelöscht.
- `sanitizeIndexEntries(...)` ist rein lesend und führt keine automatische Bereinigung realer Daten mehr aus.
- Vollständiger Drive-Restore verwendet den dublettensicheren Upsert.
- Auflösung beim Restore zuerst über `internalId`, danach über `mainDriveFileId`.
- Wiederholter Restore erzeugt keine zweite Room-Zeile.
- Vor der ersten Änderung wird der aktuelle Referenzbestand erneut geprüft.
- Alte Dubletten bleiben bei Migrationen erhalten und blockieren den App-Start nicht.

## Umgesetzte DATEV-Sicherheit

- Nur ausdrücklich bestätigte und ausgeglichene Aufteilungen sind exportierbar.
- Keine UI-Übersteuerung für ungeprüfte KI-Vorschläge.
- Stabile Beleg-GUID und Belegreferenz werden aus `internalId` erzeugt, nicht aus der lokalen Room-ID.
- Mehrere Buchungszeilen eines Belegs erzeugen nur eine Anlage.
- Doppelte stabile `internalId` blockieren den Export.
- Bereits exportierte Belege werden unabhängig von einem UI-Schalter blockiert.
- Nach fachlicher Änderung ist eine neue ausdrückliche Freigabe erforderlich.
- Änderungen an Betrag, Datum, Kategorie, Konto, Beschreibung, Positionen, Wohneinheit oder Mieter setzen `freigabestatus` auf `OFFEN` zurück.
- Alte Aufteilungen, Buchungsvorschläge und Exportlauf-ID werden bei relevanten Änderungen entfernt.
- Reine Drive-/Synchronisierungsänderungen erhalten eine gültige Freigabe.
- Exportassistent zeigt konkrete Ausschlussgründe pro Beleg.
- Export-Audit speichert die tatsächlich exportierten stabilen Beleg-IDs.
- Vollständige ZIP-Pakete verwenden die verifizierten Originalbytes genau einmal pro stabiler Belegidentität.
- Synthetische Ersatz-PDFs werden nicht als Originalbelege ausgegeben.
- PDF, JPEG, PNG und WEBP werden anhand ihrer Dateisignatur geprüft.
- Fehlende oder nicht unterstützte Originalbelege werden mit sichtbarem Grund ausgeschlossen.

## Datenbank

- Aktuelle Room-Datenbankversion: 14.
- Migration 13→14 ergänzt:
  - `allocationsJson`
  - `bookingProposalsJson`
  - `freigabestatus`
- Bestehende Zeilen und bestehende Dubletten bleiben erhalten.
- Legacy-Belege starten ausdrücklich mit `freigabestatus = OFFEN`.
- Die Migration erfindet keine Aufteilung oder Freigabe.
- Ein physischer UNIQUE-Index auf `internalId` wird bei bestehenden Alt-Dubletten bewusst nicht erzwungen. Neue Restore-Dubletten werden über den kontrollierten Upsert verhindert.

## Weitere Sicherheitsarbeiten

- Sensible Gemini-Rohantworten wurden aus Logcat entfernt.
- Gemini-API-Key bleibt vorerst bewusst in der App, weil sie aktuell nur privat verwendet wird. APK nicht öffentlich verteilen.
- Drive-Sync-Fehlerbehandlung und Tests wurden ergänzt.
- Android-Systembackup für sensible Daten wurde abgesichert.
- CI baut und testet die Debug-App.
- Veraltete CI-Läufe desselben Branches werden automatisch abgebrochen, damit der neueste Stand priorisiert wird.

## Lokaler DATEV-Arbeitsstand vom 02.08.2026 (noch nicht auf GitHub)

- EXTF-Buchungsstapel auf Formatversion 13 mit exakt 125 Buchungsfeldern umgestellt.
- EXTF-Kopfzeile umfasst exakt 31 Felder; Text- und Zahlenfelder werden passend serialisiert.
- CSV-Ausgabe verwendet UTF-8 mit BOM im ZIP sowie ausschließlich CR/LF-Zeilenenden.
- Buchungs-GUID steht in Feld 103; Feld 101 (Erlöskonto Anzahlungen) bleibt unbenutzt.
- Ein strenger Formatprüfer blockiert fehlerhafte CSVs vor dem ZIP-Schreiben.
- Der ältere öffentliche Exportweg verwendet jetzt ebenfalls Mapping, Freigabeprüfung, Vorprüfung, Serializer und Formatprüfer.
- Ungeprüfte, nicht freigegebene, unvollständige oder bereits exportierte Belege werden blockiert.
- Eine automatisch erzeugte, fachlich nicht belegte Debitoren-/Kreditoren-Datei wurde aus dem alten ZIP-Weg entfernt.
- Direkter Kotlin-Compile-/Lauftest erfolgreich: Kopf 31, Spalten 125, Datensatz 125, CRLF, Ablehnung des alten Formats.
- Anonymisierte Referenzdatei: `outputs/EXTF_Buchungsstapel_2026_KORRIGIERT_TEST.csv`.
- Anonymisiertes Referenz-ZIP: `outputs/DATEV_Referenzexport_2026_KORRIGIERT_NUR_TESTDATEN.zip`.
- SHA-256 der Referenz-CSV: `414F95DEAF1875ED2154797E7F553512DC77C22C1481B38BCF39A3CC7E2DF14E`.
- Vollständiger Gradle-/Android-Build ist lokal noch nicht bestätigt, weil fehlende Maven-Abhängigkeiten in der eingeschränkten Umgebung nicht heruntergeladen werden konnten.
- GitHub-Commit, Push, CI-APK und Emulator-Test stehen aus; Draft-PR #3 bleibt unverändert.

## Lokaler OpenAI-Arbeitsstand vom 02.08.2026 (noch nicht auf GitHub)

- In den Kontoeinstellungen gibt es ein eigenes Menü zur Auswahl von Gemini oder OpenAI für neue Beleganalysen.
- Der OpenAI-Schlüssel wird vom Benutzer eingegeben, mit einem nicht exportierbaren Android-Keystore-Schlüssel per AES/GCM verschlüsselt und nie im UI-Zustand oder in Logs ausgegeben.
- Der Schlüssel wird nicht in BuildConfig oder die APK eingebettet. Die lokale `.env` ist ignoriert und darf nicht committed werden.
- OpenAI verwendet die Responses API mit Bildanalyse, strengem JSON-Schema und dem konfigurierbaren Standardmodell `gpt-5.6`.
- Modellantworten werden vor der Formularübernahme semantisch geprüft: Aussteller, echtes Kalenderdatum, positiver Bruttobetrag, erlaubte Kategorie, Unterkategorie, Konto sowie Positionswerte und Positionszuordnungen.
- Der direkte Schlüsselbetrieb ist in der UI ausdrücklich als privater Gerätemodus gekennzeichnet. Vor einer öffentlichen APK ist ein eigener Backend-Proxy erforderlich.
- Separater Kotlin-Compile-/Lauftest der neuen Kernklassen erfolgreich (`OPENAI_KOTLIN_CHECK_OK`). Dabei wurde auch die Ablehnung eines unmöglichen Datums geprüft.
- Ein vollständiger Gradle-Build scheitert in der lokalen eingeschränkten Umgebung weiterhin vor der eigentlichen Kotlin-Kompilierung an der Gradle-Laufzeit. Der vollständige CI-/APK-/Emulator-Test und ein echter OpenAI-Test mit anonymisiertem Beleg stehen deshalb noch aus.

## Lokaler Design-Arbeitsstand vom 02.08.2026 (noch nicht auf GitHub)

- Hauptnavigation von fünf kollidierenden Einträgen auf vier klare Bereiche reduziert: Start, Belege, Scannen und Finanzen.
- Fahrtenbuch, Mieteingänge und Steuerschätzung bleiben über den Schnellzugriff auf Start erreichbar; Start bleibt bei diesen Unterseiten als übergeordneter Bereich markiert.
- Scannen ist als visuell hervorgehobene Primäraktion in der unteren Navigation ausgeführt.
- Kopfzeile zeigt jetzt den aktuellen Seitentitel statt auf jeder Seite denselben App-Namen.
- Direkte, unbestätigte Datenrücksetzung wurde aus der globalen Kopfzeile entfernt.
- Untere Navigation und Kopfzeile verwenden ruhige, helle Material-3-Flächen mit größeren Touch-Zielen und besserer Lesbarkeit auf schmalen Smartphones.
- Dashboard-Warnung wurde kompakter und handlungsorientiert gestaltet; die anbieterspezifische Beschriftung wurde dort durch „KI-Assistenten“ ersetzt.
- Einstellungen sind nun in KI & Automatisierung, Belege & Speicher, Adressen sowie Objekt & Personen gegliedert. Dekorative Emojis und konkurrierende Kartenfarben wurden entfernt.
- App-weites Material-3-Farbsystem auf konsistente Blau-, Slate- und Grünwerte umgestellt; geräteabhängige Dynamic Colors sind für eindeutige Finanz- und Statusfarben standardmäßig deaktiviert.
- Typografie um konsistente Titel-, Text- und Labelstufen ergänzt.
- Kotlin-Syntaxprüfung der geänderten UI- und Theme-Dateien erfolgreich (`NO_KOTLIN_SYNTAX_ERRORS_DETECTED`).
- Vollständiger Gradle-Lauf hing erneut vor der eigentlichen Kotlin-Kompilierung und wurde beendet. Neuer APK-/Emulator-Screenshot sowie Live-Prüfung von Touch-Zielen und Textskalierung stehen noch aus.

## Letzter bestätigter Prüfstand

- Commit: `afc039534b9bb7bc32848e0392c317797d4657f6`
- Android CI: Lauf `#304`
- Lauf: https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/30721988269
- `lintDebug`: erfolgreich
- `testDebugUnitTest`: erfolgreich
- `assembleDebug`: erfolgreich
- Debug-Artefakt: `app-debug`
- Artefakt-SHA-256: `fbc60b0e6c3366b7348a970b825c0cad20bdd96979acae1874687db1104fe324`
- Signierter Release-Build wurde erwartungsgemäß übersprungen, weil im PR-Lauf keine Release-Secrets verfügbar waren.

## Noch offener praktischer Nachweis

Der Quellcode, die Unit-Tests, Lint und der Debug-Build sind bestätigt. Ein echter Emulator-/Smartphone-Test des jüngsten Stands ist noch offen.

Zu prüfen:

1. Debug-APK aus CI #304 installieren.
2. App starten und auf Abstürze prüfen.
3. Papierkorb öffnen und rein lesende Dublettenanalyse starten.
4. Vorschau der sicheren Zusammenführung prüfen.
5. Bestätigen, dass die UI ausdrücklich „Hauptdatei wird nicht gelöscht“ anzeigt.
6. Einzelzusammenführung mit anonymisierten Testdaten prüfen.
7. Vollständige Gruppenlöschung nur mit Testdaten und beiden Bestätigungen prüfen.
8. App während einer Testbereinigung beenden und Fortsetzung prüfen.
9. Drive-Restore zweimal ausführen und prüfen, dass die lokale Beleganzahl gleich bleibt.
10. DATEV-Aufteilung ausdrücklich freigeben.
11. Relevante Belegdaten ändern und prüfen, dass die Freigabe auf `OFFEN` zurückgesetzt wird.
12. DATEV-ZIP erzeugen und prüfen, dass der Originalbeleg genau einmal enthalten ist.
13. Prüfen, dass bereits exportierte Belege nicht erneut exportiert werden.
14. Logcat auf Abstürze und sensible Gemini-Rohdaten prüfen.

## Wichtige Regeln für die Fortsetzung

- Ausschließlich auf `agent/safe-duplicate-purge` arbeiten.
- PR #3 als Draft offenlassen.
- Niemals selbstständig nach `main` mergen.
- Keine automatische Bereinigung realer Belegdaten.
- Jede Löschung benötigt die vorgesehene ausdrückliche Bestätigung.
- Gemeinsame Hauptdatei bei Einzelzusammenführung niemals löschen.
- Bestehende Referenz-Sicherheitsprüfung nicht entfernen oder umgehen.
- Keine IDs automatisch verändern.
- Keine Passwörter, API-Schlüssel oder Keystores committen.
- Nach Änderungen Unit-Tests, Lint und Debug-Build ausführen.
- Erfolgreiche Tests nur behaupten, wenn sie tatsächlich ausgeführt wurden.
- Benutzer vor einem Merge zuerst die APK testen lassen.

## Startauftrag für Codex auf dem neuen Rechner

```text
Arbeite im Repository xedox737/Steuer-Asistend-APP ausschließlich auf dem Branch agent/safe-duplicate-purge. Lies zuerst diese Übergabedatei und prüfe anschließend den aktuellen Branch- und PR-Stand gegen GitHub. PR #3 muss Draft bleiben und darf nicht gemergt werden.

Der letzte bestätigte Stand ist Commit afc039534b9bb7bc32848e0392c317797d4657f6 mit erfolgreichem Android-CI-Lauf #304. Prüfe, ob der Branch inzwischen weitergelaufen ist. Führe danach den noch offenen Emulator-/Gerätetest des aktuellen Debug-Artefakts durch. Teste insbesondere Dublettenanalyse, sichere Einzelzusammenführung ohne Löschen der gemeinsamen Hauptdatei, doppelt bestätigte Gruppenlöschung mit Testdaten, Wiederaufnahme nach App-Abbruch, zweimaligen Restore ohne zusätzliche Room-Zeile, DATEV-Freigabe und deren Rücksetzung nach relevanten Änderungen, Originalbeleg genau einmal im DATEV-ZIP sowie den Schutz vor Wiederholungsexporten.

Nutze ausschließlich anonymisierte Testdaten. Führe keine automatische Bereinigung realer Daten aus. Behebe gefundene Fehler mit kleinen logischen Commits, prüfe nach jedem Block CI und aktualisiere am Ende die Beschreibung von Draft-PR #3. Die bestehende Referenz-Sicherheitsprüfung darf nicht entfernt, abgeschwächt oder umgangen werden.
```
