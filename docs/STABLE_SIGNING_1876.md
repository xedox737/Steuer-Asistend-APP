# Dauerhafte Original-App-Signierung: neuer Neuinstallationsstand

Base: `c7c0446dcc40027dffe168013ee8ef4c6a51d8d8` (gemergter PR151). Vor Arbeitsbeginn wurden Main, Repository-Status und Projektregeln erneut geprueft. Direkter `git fetch origin` scheitert weiterhin am Sandbox-Proxy; die exakten API-Commitobjekte wurden mit ihren Git-Hashes inklusive Merge-Signatur reproduziert und in einem eigenen Worktree/Branch verwendet.

## Freigegebener neuer Schluessel

Der Nutzer bestaetigte, dass die installierte Version103 keine lokalen Daten enthaelt, und entschied sich fuer eine Neuinstallation mit dauerhaftem Schluessel auf seinem PC. Anschliessend stellte er den oeffentlichen Zertifikat-SHA1 bereit:

`18:76:FC:D5:F5:D3:65:26:FC:44:F3:BA:37:AA:6C:AC:AB:FB:D9:8D`

Diese Freigabe ist keine Aussage ueber private Schluesselbytes, GitHub-Secret-Konfiguration, Google-Console-Eintraege oder bereits erfolgreiche Geraetetests. Der echte CI-Build muss den Keystore und die tatsaechliche APK-Signatur noch positiv gegen diesen Wert pruefen. Private Schluessel/Tokens werden weder angefordert noch veroeffentlicht.

Der historische installierte103-Fingerabdruck `CF:43:8D:2E:D2:1D:0F:D5:F8:B0:AB:81:BF:51:8A:51:78:A7:73:85` bleibt als eigene Konstante erhalten. Er und der fruehere102-Referenzwert EA werden fuer den neuen dauerhaften Stand abgewiesen. Es wird NICHT behauptet, die neue APK koenne eine anders signierte103 direkt aktualisieren. Der APK-Pruefer schreibt dies zusaetzlich maschinenlesbar nach `app/build/reports/product-signing.json` (`canUpdateVersion103=false`). Erst die vollstaendig gepruefte neue APK und passende OAuth-Konfiguration erlauben eine spaetere Neuinstallationsanleitung. Kein Deinstallieren durch Codex.

## Gezielte Aenderungen

- Ein einzelner fest freigegebener Produktfingerabdruck in `apk_signing.py`. Paket und kryptografisch gepruefte APK-Signatur muessen weiterhin stimmen. Die historische103-Identitaet wird nicht ueberschrieben oder als kompatibel ausgegeben.
- Der vorhandene strikte Keystore-Vorbereitungspfad wird auch in Android-CI verwendet: Validierung vor Installation, geschuetzte Dateirechte, kein zufaelliger Ersatzschluessel fuer Produktreleases. PR-Pruefungen ohne konfigurierte Secrets bleiben klar als temporaere CI-Artefakte gekennzeichnet und duerfen nicht als fertige Produktversion gelten.
- Die zwei parallelen automatischen Publisher konnten bisher denselben Release-Tag/Dateinamen ueberschreiben. Android-CI veroeffentlicht jetzt nur Pruefartefakte. Der eine automatische Produkt-Publisher reagiert erst nach erfolgreicher vollstaendiger Main-CI. Er prueft die exakte SHA, Workflow-Datei, Main-Branch, Push-Ereignis, Repository und beide Jobs erneut ueber die GitHub-API. Ein roter oder uebersprungener Emulatorjob sperrt die Veroeffentlichung. Manueller Start benoetigt ebenfalls eine bereits vollstaendig erfolgreiche Main-CI fuer dieselbe SHA. Der ausgecheckte, gebaute und im Release benannte Commit ist identisch, auch wenn Main zwischenzeitlich weitergeht.
- Die drei alten Spezial-Publisher bleiben ausschliesslich manuell verfuegbar und verlangen weiterhin denselben geprueften dauerhaften Schluessel; ihre Beschreibungen nennen den Neuinstallationswechsel. Kein alter Codepatch-Workflow wurde gestartet.

App-Quellen, Package-ID, isolierter Test-App-Branch, Manifest, Room, Beleg-/Objekt-/Einheiten-IDs, Drive-Namensraeume und Backupformate bleiben unveraendert. Auch bei leerer lokaler103 werden bestehende Drive-Daten nicht geloescht oder bereinigt. Andere Nutzer mit Daten duerfen keine Neuinstallation ohne gepruefte Sicherung durchfuehren.

## Reproduktion und lokale Nachweise

Vor dem Fix: elf Python-Methoden, genau zwei rote neue Faelle: der neue Nutzerfingerabdruck wird noch abgewiesen, der alte103-Fingerabdruck noch akzeptiert. Nach der Umstellung: sechzehn Methoden bestanden, einschliesslich fuenf neuer Release-CI-Vertragstests. Bestehende Tests fuer manipulierte, fremde oder ungepruefte APKs und einen synthetischen inkompatiblen privaten Keystore bleiben erhalten. Alte Erwartungswerte wurden nur gemaess ausdruecklich freigegebenem neuem Signierstand getrennt, nicht abgeschwaecht.

Der neue CI-Publisher-Waechter wurde zusaetzlich gegen echte historische API-Metadaten geprueft: Main-Lauf40887255955 wird akzeptiert; der wegen eines150%-Schriftgroessentests rote Main-Lauf40981744682 wird gesperrt. Keine blinde Wiederholung des roten Laufs. YAML wurde einschliesslich Erkennung doppelter Schluessel geprueft, `git diff --check` bestanden. Reguläres lokales Gradle bleibt wegen `FileLockContentionHandler / Could not determine a usable wildcard IP` blockiert. Da keine Kotlin-/App-Quellen geaendert werden, findet die erforderliche vollstaendige Android-Pruefung in CI statt.

Protokolle: `docs/stable-signing-1876-evidence`. Vollstaendige CI, echte Keystore-/APK-Zertifikatspruefung und Artefakt-Hashes werden nach dem Lauf nachgetragen. Der fruehere gruene PR151-Lauf gilt nicht automatisch als Test dieser Workflow-Aenderung. Der einzelne rote150%-Schriftgroessenfall nach dem alten Merge ist weiterhin ein offener Befund und wird nicht als behoben ausgegeben.

## Voraussetzungen vor praktischer Freigabe

Repository-Secret `PRODUCTION_DEBUG_KEYSTORE_BASE64` und Variable `PRODUCTION_DEBUG_SHA1` muessen zu diesem neuen festen Fingerabdruck passen. Die Schnittstelle hier kann Secrets/Variablen nicht direkt verwalten; ihr Vorhandensein wird nur anhand des tatsaechlichen CI-Ergebnisses festgestellt. Fuer Google OAuth muss im bisherigen Google-Cloud-Projekt derselbe neue SHA1 fuer `com.aistudio.steuerassistent.vnhqkp` hinterlegt werden. Reale Anmeldung, Drive-Root-Pruefung, Neustart und Netzunterbrechung bleiben bis zum Geraetetest offen. Eine kommerzielle Play-Signierung ist damit nicht als eingerichtet behauptet.
