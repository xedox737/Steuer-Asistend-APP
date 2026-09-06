# Manuelle Testmatrix – Dokumente, KI und OCR

Diese Matrix ist für ein echtes Android-Smartphone und ausschließlich für fiktive Testdaten vorgesehen. Vor dem Test eine vorhandene App-Sicherung erstellen. Für Drive-Tests einen eindeutig getrennten Testbestand verwenden; niemals produktive Originale verschieben oder löschen.

## Voraussetzungen

- Vorhandene veröffentlichte App-Version ist installiert und enthält einige fiktive lokale Testdaten.
- Neue Debug-APK liegt vor.
- Testimmobilie `Musterstraße 12` mit `WE_01` und `WE_02` ist angelegt.
- Google Drive ist mit einem isolierten Testkonto oder Test-Root verbunden.
- KI-Provider ist mit einem Testschlüssel konfiguriert; Schlüssel nicht in Screenshots aufnehmen.
- Die Testdateien enthalten keine echten Personen-, Bank- oder Adressdaten.

## Checkliste

| Nr. | Test | Durchführung | Erwartetes Ergebnis | Status/Notiz |
|---:|---|---|---|---|
| 1 | Update installieren | Neue APK über die vorhandene Version installieren, App nicht vorher deinstallieren. | Installation erfolgreich; lokale Receipts, IDs, Immobilien, Darlehen und Fahrten bleiben erhalten. | ☐ |
| 2 | Lokalen Bestand prüfen | Dashboard, Belege, Dokumente, Finanzen, Mieten und Fahrtenbuch öffnen. | Bestehende Daten sind vollständig und ohne neue Dubletten sichtbar. | ☐ |
| 3 | Text-PDF importieren | Fiktive Handwerkerrechnung mit eingebettetem PDF-Text importieren. | Ein Dokument/Receipt entsteht; Original bleibt unverändert; Text wird ohne unnötige OCR übernommen. | ☐ |
| 4 | JPG importieren | Gut lesbaren, leicht schiefen fiktiven Rechnungs-Scan importieren. | Bild wird angenommen; Vorschau und Dateityp stimmen; kein Absturz. | ☐ |
| 5 | OCR prüfen | JPG und bildbasiertes PDF analysieren; zusätzlich schlechten Scan testen. | Lesbarer Text wird erkannt. Schlechter Scan liefert Teilresultat oder verständlichen Fehler, aber keinen Absturz. | ☐ |
| 6 | KI analysieren | Kaufvertrag, Darlehensvertrag, Energieausweis und Mietvertrag analysieren. | Passender Dokumenttyp und strukturierte Felder werden nur vorgeschlagen; niedrige Sicherheit führt zu `PRÜFEN`. | ☐ |
| 7 | KI-Vorschlag ändern | Einen erkannten Kaufpreis vor der Bestätigung bearbeiten. | Bestehender App-Wert bleibt bis zur Bestätigung unverändert; bearbeiteter Vorschlag ist sichtbar. | ☐ |
| 8 | Einzelfelder bestätigen | Ein Feld übernehmen, ein Feld ignorieren und ein Feld bearbeiten. | Nur ausdrücklich bestätigte Felder werden übernommen; ignorierte Felder bleiben unverändert. | ☐ |
| 9 | Drive-Ziel prüfen | Zunächst als `SONSTIGES` synchronisierten Kaufvertrag danach bestätigt als `KAUFVERTRAG` klassifizieren und synchronisieren. | Ziel ist `00_Stammdaten/01_Kauf_Eigentum`; gleiche DriveFileId und gleicher Inhalt/Hash; keine zweite Datei. | ☐ |
| 10 | Offline-Reklassifikation | Dokumentklassifikation bestätigen, während Drive nicht erreichbar ist; später wieder verbinden. | Lokale Klassifikation bleibt; Sync steht aus; spätere Synchronisierung reorganisiert dieselbe Datei. | ☐ |
| 11 | Dokument suchen | Nach `Heizungsventil`, `Testbank`, `520000`, `WE_01`, `Energieausweis`, `Grundsteuer`, `84,50` und einer Rechnungsnummer suchen. | Treffer aus OCR, PDF-Text und Metadaten erscheinen; Jahr-, Typ- und Einheitenfilter funktionieren; leere Suche stürzt nicht ab. | ☐ |
| 12 | Legacy-Preview | Drive-Inventur/Migrationsvorschau für einen isolierten Legacy-Testordner öffnen. | Vorschau ist read-only; Orphans, Legacy-Dateien, fehlende Referenzen und Konflikte werden markiert; nichts wird automatisch verändert. | ☐ |
| 13 | Dublettenschutz | Identische Kopie mit anderem Namen sowie gleichen Namen mit anderem Inhalt prüfen. | Identischer Hash wird erkannt; anderer Hash wird nicht als identisches Original behandelt; keine automatische Löschung. | ☐ |
| 14 | Keine Nebenwirkungen | Nach Inventur und abgebrochener Migration Dateianzahl, Namen und DriveFileIds vergleichen. | Bestand ist unverändert; fremde Datei wurde ignoriert. | ☐ |
| 15 | Backup | Supplemental-/Drive-Sicherung des fiktiven Testbestands ausführen. | Backup enthält Darlehen, Fahrten, Standardstrecken, ManagedDocuments und erlaubte Einstellungen; keine Schlüssel, Tokens, lokalen Pfade oder OCR-Volltexte. | ☐ |
| 16 | Restore Dry Run | Restore-Vorschau/Validierung mit gültigem und absichtlich fehlerhaftem Testbestand ausführen. | Blocking-Fehler löschen nichts; Core-Fehler verhindert Supplemental Restore; Teilfehler wird nicht als Vollerfolg gemeldet. | ☐ |
| 17 | Restore erfolgreich | Fiktiven Bestand in einer isolierten Testinstallation wiederherstellen. | IDs und DriveFileIds bleiben erhalten; Suchindex wird neu aufgebaut; Darlehen, Fahrten und Jahresfreigabe stimmen. | ☐ |
| 18 | DATEV-Testexport | Freigegebenen fiktiven Receipt exportieren; ungeprüften KI-Receipt daneben belassen. | DATEV-Validierung ist grün; nur zulässiger Receipt wird exportiert; Original nicht doppelt; physischer Kategorieordner ist irrelevant. | ☐ |
| 19 | Advisor-Paket | Testjahr 2026 mit vollständigen und anschließend mit fehlenden Originalen exportieren. | Summary funktioniert ohne vorheriges Öffnen des Jahresassistenten; Freigabe/Fingerprint und Readiness stimmen; keine doppelten Originale. | ☐ |
| 20 | Fahrtenbuch | Einfache Fahrt, Hin/Rückfahrt, Stopps, Google-Route, manuelle Korrektur und Standardstrecke öffnen/speichern. | Bestehende Werte, Provider, Signatur, Korrekturgrund und Stopps bleiben korrekt; keine doppelte Distanz. | ☐ |
| 21 | Navigation/Smoke | Dashboard → Belege → Dokumente → Fahrtenbuch → Finanzen → Mieten wechseln und zurück navigieren. | Alle Bereiche sind erreichbar; kein Absturz, kein leerer unerwarteter Screen und keine Navigation-Regression. | ☐ |

## Abschluss

- Gerät/Android-Version: ____________________
- App-Commit/APK: ____________________
- Testkonto/Test-Root: ____________________
- Ergebnis: ☐ bestanden ☐ mit Einschränkungen ☐ fehlgeschlagen
- Auffälligkeiten (ohne Schlüssel oder personenbezogene Daten): ____________________

