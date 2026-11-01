# Phase 11E – Test auf dem POCO X8 Pro

Testkandidat: Original-App `com.aistudio.steuerassistent.vnhqkp`, Versionscode **1400**, unveränderter OAuth-Weg und unveränderte Room-Version 34. Der Versionscode liegt über der zuletzt bestätigten Testversion 1300. Das vorhandene dauerhafte Produktzertifikat muss vor Bereitstellung am gebauten APK geprüft werden. Ein höherer Versionscode allein beweist keine Signaturkompatibilität mit jeder früheren Installation.

**Bis zur gesonderten Freigabe nur interner CI-Build. Kein Merge, kein GitHub-Release und keine APK-Veröffentlichung.** Eine neue erfolgreiche CI gehört zu diesem Build; die frühere grüne CI für Versionscode 1 ersetzt diese Prüfung nicht. Praktische Abnahme weiterhin OFFEN.

## Installation nach Bereitstellungsfreigabe

1. Ausschließlich die geprüfte Phase11E-APK mit Versionscode 1400 verwenden. Bestehende ImmoPilot-App behalten: **nicht deinstallieren, keine App-Daten löschen**.
2. Falls GitHub eine ZIP-Datei liefert: ZIP entpacken und die darin enthaltene `.apk` öffnen. Die ZIP-Datei nicht lediglich in `.apk` umbenennen.
3. Android muss eine Aktualisierung der vorhandenen Original-App anbieten. Bei Paketkonflikt oder „App wurde nicht installiert“ stoppen und den Meldungstext sowie die installierte Versionsnummer weitergeben. Keine Deinstallation als Umgehung.
4. App öffnen; vorhandene 22 Belege und Immobilienobjekt/sieben Einheiten prüfen. Ein bereits wiederhergestelltes Original öffnen. Keine erneute Wiederherstellung für diesen Synchronisierungstest starten.

## Zwei manuelle Synchronisierungen

1. Unter **Mehr → Google Drive Backup** automatisches Backup ausgeschaltet lassen. Bestehendes Google-Konto verwenden; nicht trennen oder neu verbinden.
2. Stabiles WLAN, App im Vordergrund, POCO-Akkueinstellung „Keine Einschränkungen“. Diese Testbedingung ersetzt keine robuste Fehlerbehandlung.
3. **Synchronisieren** einmal antippen. Nach Abschluss den neuen **Diagnosebericht kopieren**, Ergebnis und Dauer mitteilen. Ein erster Lauf kann noch erforderliche Sicherungsschritte nachholen; keinen festen Zeitwert erwarten.
4. Ohne Datenänderungen ein zweites Mal synchronisieren. Auch diesen vollständigen Bericht kopieren. Erwartung: vorhandene unveränderte Belege übersprungen, keine unnötigen Original-Neuübertragungen, erfolgreicher Abschluss mit wahrheitsgemäßen Zählern.
5. App neu starten; Anmeldung, 22 Belege, 55 Positionen, ein Objekt/sieben Einheiten, Split, DATEV-Profil und KI-Lernregeln kontrollieren. Mehrere Originale öffnen.

Die beiden bekannten Zusatz-404 nicht löschen, ersetzen oder umbiegen. Keine neuen produktiven Belege als Störungstest erzeugen, keine künstliche Netzunterbrechung beim Schreiben. Bei Fehler den Bericht einschließlich betroffener Phase und letzter erfolgreicher Phase senden. Der Bericht bleibt auch nach Fehler und Neustart erreichbar.

Für die Abnahme erforderlich: **zwei neue echte Laufzeiten vom POCO, beide Diagnoseberichte und Bestätigung der Daten-/Anmeldungserhaltung**. Simulierte Werte und grüne Emulator-Tests sind kein Ersatz.
