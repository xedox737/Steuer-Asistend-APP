# Unvollständige Drive-Bestandsprüfung

Basis: `0c310d6cd7f05deeddbb80f24a15c2901217dc2c` (PR #152).

## Fehler und Verhalten

Wenn `receipt-index.json` oder ein darin enthaltener aktueller Dateiverweis
nicht gelesen werden konnte, enthielt die Vorschau bisher eine Warnung,
meldete aber trotzdem Erfolg und null Belege. Vorhandene Immobiliendaten
reichten aus, den Wiederherstellungsdialog freizugeben.

Die Bestandsprüfung ist jetzt nur erfolgreich, wenn die Such- und Leseschritte
sowie die einzelnen Belege vollständig geprüft wurden. Fehlgeschlagene Suchen
werden nicht als fehlende Sicherungsdateien behandelt. Ein beschädigtes oder
fehlendes `entries`-Array wird weder in der Vorschau noch im Restore-Preflight
als leere Liste interpretiert. Ein ausdrücklich leeres Array bleibt gültig.

Bei unvollständiger Prüfung zeigt der Dialog unbekannte Belegzahlen als
„Nicht prüfbar“ an und sperrt seine Bestätigung. Ein bekanntermaßen fehlerhafter
Vorschaustand sperrt auch den direkten ViewModel-Aufruf. Das Restore-Backend
prüft unabhängig davon erneut und bricht vor dem Import ab.

Insbesondere darf die Synchronisierung bei lokal leerem Bestand nicht fortfahren,
wenn die Prüfung fehlschlägt: Sie würde sonst einen unbekannten Drive-Bestand
mit einer leeren lokalen Liste überschreiben. Ein bekanntermaßen fehlerhafter
Vorschaustand erzwingt auch bei lokal vorhandenen Belegen eine erneute Prüfung
vor der Synchronisierung. Der Abbruch bewahrt Anmeldung,
bereits gefundenen Systemordner und bisherigen Sicherungszeitpunkt. Ein erneuter
manueller Prüfversuch kann den Fehlerstatus wieder aufheben.

Es werden keine Drive-Dateien bereinigt, Dateiverweise umgeschrieben oder Belege
aus dem Index entfernt. Die bestehenden Namespace-, Berechtigungs- und
Originaldateiprüfungen bleiben aktiv. Ein HTTP 404 allein unterscheidet nicht
zwischen gelöschten und für den jeweiligen Aufrufer unzugänglichen Dateien.

## Prüfung

- Vor der Korrektur: fünf fehlgeschlagene Methoden in 30 gezielten Repository-/Compose-Tests.
- Nach der Korrektur: 73 gezielte Repository-, ViewModel- und Compose-Methoden
  mit echter Kotlin-/Compose-Kompilierung und lokalem Robolectric SDK 35 erfolgreich.
  Die im Repository auf SDK 36 deklarierten Tests bleiben unverändert auf SDK 36;
  nur externe temporäre Testkopien wurden für den lokal verfügbaren SDK-35-Cache verwendet.
- Negative Fälle: nicht erreichbare aktuelle Indexreferenz, ungültiges Array,
  fehlgeschlagene Indexsuche, keine lokalen Änderungen bei REPLACE_FULL,
  keine Remote-Schreibzugriffe bei abgebrochener Synchronisierung,
  Anmeldung/Ordnerstatus/Zeitstempel erhalten und gesperrte Dialogbestätigung.
- Positivfall: geprüft leeres Belegverzeichnis mit Immobiliensicherung bleibt wiederherstellbar.
- `git diff --check` erfolgreich.
- Lokales Gradle startet in dieser Umgebung nicht:
  `Could not determine a usable wildcard IP for this machine` beim
  `FileLockContentionHandler`. Deshalb werden vollständige Unit-Tests auf SDK 36,
  Lint und APK-Bau sowie der native API-35-Emulatorlauf über Android-CI geprüft.
  Diese CI-Ergebnisse stehen beim Erstellen des PR noch aus.

Die Tests verwenden ausschließlich synthetische Dateien und simuliertes OAuth/HTTP.
Sie belegen keinen Gerätestart, keinen realen Google-App-Login und keine
Wiederherstellung des Nutzerbestands. Private Drive-Dateien gehören nicht zu
Repository, Testfixtures oder veröffentlichten Nachweisen.
