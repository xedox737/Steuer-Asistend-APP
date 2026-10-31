# Drive-Leseprüfung und isolierte Diagnose

## Fehler und Verhalten

Ein mobiler Timeout beim Lesen einer kleinen Beleg-Metadatendatei wurde als fehlende Datei gezählt. Jeder Download und jeder eingebettete Verweis prüfte dieselben Vorfahren und den Hauptordner erneut. Außerdem erzeugte der bisherige Schreibtest einen lokalen Fachbeleg ohne Quelldatei und scheiterte dadurch selbst. Dieser Testbeleg konnte das Schutzkriterium „lokaler Bestand leer“ verfälschen.

- Eine Inventarprüfung bzw. ein Snapshot-Lesevorgang teilt die Nachweise der Vorfahren ausschließlich innerhalb dieses Vorgangs und desselben Bearer-Tokens. Angeforderte Zieldateien werden weiterhin frisch geprüft. Vor Ergebnisfreigabe werden alle beobachteten Objekte (auch frühere Nachweise nach Cache-Invalidierung) und Hauptordner erneut geprüft. Verschiebungen und Zugriffsverletzungen verhindern die Übernahme.
- Schreiboperationen invalidieren den wiederverwendbaren Nachweis und verwenden frische Schreib- und JSON-Verweisprüfungen. Es gibt keinen persistenten Berechtigungs-Cache.
- Ausschließlich ein JSON-GET wird bei `SocketTimeoutException` einmal nach 250 ms wiederholt. HTTP-Fehler, Uploads und Testassertionen werden nicht wiederholt; HTTP-Zeitlimits bleiben unverändert. Dauerhafte Leseunterbrechungen sperren die Wiederherstellung und erscheinen getrennt von fehlenden Dateien.
- Nicht lesbare oder ungültige Löschmarkierungen brechen die Wiederherstellung ab. Eine teilweise gelesene Löschhistorie darf keine gelöschten Belege wiederherstellen.
- Der Schreib-/Lesetest verwendet ausschließlich eigene, per App-/Entity-Markierung identifizierte PNG-/JSON-Dateien unter `_BelegApp-Daten/drive-diagnostics`. Er verändert weder Belegindex noch App-Konfiguration noch Fachbelege in Room.
- Nur der exakt erkennbare alte synthetische Testdatensatz (reservierte ID 999999, Testanzeige-ID, Hersteller, Beschreibung, Betrag und Uhrzeit) wird lokal reversibel als gelöscht markiert. Datensatz, ID und Werte bleiben erhalten; keine Cloud-Datei oder Löschmarkierung wird bereinigt. Vor der Leerbestandsprüfung wird der aktuelle Room-Bestand neu gelesen.

## Validierung

Gezielte Tests decken begrenzte Wiederholung, dauerhaften Timeout ohne falsche Fehlzählung, wiederverwendete Nachweise mit abschließender Prüfung, Verschiebungen während des Lesens, Tokenwechsel/Abbruch, strikte Schreibprüfungen, Nachweiserhalt nach Retry-Invalidierung, isolierte Diagnosedateien, präzise Schreibfehler, ungültige Löschmarker sowie den Schutz eines leeren lokalen Fachbestands ab. Compose prüft die getrennte Darstellung temporärer Lesefehler und die weiterhin gesperrte Wiederherstellung.

Lokal: frische Kotlin-/Compose-Kompilierung der betroffenen Produktions- und Testdateien; Robolectric mit lokal verfügbarem SDK 35. Die eingecheckten SDK-36-Testkonfigurationen bleiben unverändert. Bestehende Erhaltungs-/Restore-Regressionen werden zusätzlich ausgeführt. Gradle kann in dieser Sandbox wegen `FileLockContentionHandler: Could not determine a usable wildcard IP` nicht starten. Vollständiger SDK-36-Build, Lint, JVM- und native Emulatortests sind deshalb vor dem Merge über `verify` und `android-emulator-e2e` verpflichtend.

Reale Wiederherstellung und App-OAuth auf dem privaten Telefon werden durch diese Tests nicht nachgewiesen. Es gibt keine Änderung an Signatur, Paketkennung oder Kontoeinrichtung.
