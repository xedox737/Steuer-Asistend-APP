# Einmalige dauerhafte Signierung auf Windows

Der Nutzer hat bestaetigt: In Version103 liegen keine lokalen Daten. Ein Neuinstallationsweg ist damit moeglich. Bestehende Drive-Daten und andere Apps werden weiterhin nicht geloescht. Die App bleibt bis zur fertig geprueften neuen APK installiert.

Der bisherige Version103-Schluessel wurde im damaligen GitHub-Workflow neu erzeugt und nicht als Build-Artefakt gesichert. Die APK enthaelt nur das oeffentliche Zertifikat. Dieser Helfer erzeugt deshalb einen neuen, auf dem Nutzer-PC dauerhaft gespeicherten Debug-Schluessel. Er installiert/deinstalliert keine App und aendert keine Drive-Daten.

1. `Signing-Setup.zip` herunterladen und vollstaendig entpacken.
2. `ImmoPilot-Signierung-einrichten.cmd` doppelt anklicken. Vorhandenes Java/keytool wird genutzt; sonst wird Eclipse Temurin17 portabel von Adoptium geladen und vor Ausfuehrung per SHA256 geprueft. Der Helfer benoetigt Windows64-Bit und Internet.
3. Im geoeffneten GitHub-Fenster ein Repository-Secret mit dem Namen **PRODUCTION_DEBUG_KEYSTORE_BASE64** anlegen. In `Secret` den bereits kopierten Zwischenablageinhalt einfuegen und speichern. Der private Wert wird nie in der Konsole angezeigt. Nicht in Chat oder oeffentliche Dateien einfuegen. Anschliessend die Zwischenablage leeren, z.B. mit `Set-Clipboard -Value ''` in PowerShell.
4. Unter [Actions-Variablen](https://github.com/xedox737/Steuer-Asistend-APP/settings/variables/actions) eine Repository-Variable **PRODUCTION_DEBUG_SHA1** mit der angezeigten **OEFFENTLICHEN SHA1** anlegen beziehungsweise aktualisieren.
5. Nur diese oeffentliche SHA1 an Codex senden. Danach werden der fest freigegebene Zertifikatspin im Produktcode, die Tests und die Dokumentation gezielt auf den neuen Neuinstallationsstand umgestellt. Bis dahin verweigert die vorhandene CF-Signaturpruefung zu Recht eine Veroeffentlichung mit dem neuen Schluessel. Es wird kein inkompatibles Update als kompatibel ausgegeben.
6. Fuer Google OAuth wird der Android-Client fuer `com.aistudio.steuerassistent.vnhqkp` mit derselben neuen SHA1 eingerichtet. Danach folgen CI, APK-Signaturpruefung und erst dann die Installationsanleitung.

Der Schluessel liegt unter `%LOCALAPPDATA%\ImmoPilot\Signing\immopilot-debug.keystore`, die oeffentlichen Fingerabdruecke unter `public-identity.json`. Diesen Ordner gesichert aufbewahren und den Keystore separat privat sichern. Bei weiteren Aufrufen wird dieselbe Datei wiederverwendet, niemals ueberschrieben. Die Datei nutzt den vorhandenen Debug-Signiervertrag (Alias `androiddebugkey`, Passwort `android`), ist kein privater Produktions-/Play-Schluessel und gehoert ausschliesslich in private Sicherungen und das vorgesehene GitHub-Secret.

Transparenz: Der verbundene GitHub-App-Zugriff kann Repository-Secrets/Variablen nicht verwalten. Daher ist diese einmalige Eingabe durch den Repository-Inhaber erforderlich. Das Skript wurde hier statisch geprueft; ein Windows-/PowerShell-Lauf auf dem Nutzer-PC wurde nicht behauptet. Der bekannte Emulatorfehler beim Warten auf150% Schriftgroesse nach dem Merge bleibt separat offen und wird nicht durch die neue Signierung geloest.

Der Java-/JKS-Vertrag wurde hier mit einem temporaeren synthetischen RSA3072-Schluessel zweimal gegen die Android-Build-Tools36 geprueft: beide APK-v2-Signaturen gueltig, jeweils genau ein Signer, identischer Zertifikats-SHA1 und unveraenderte Keystore-Datei. Der Testschluessel wurde anschliessend geloescht. Das ist kein behaupteter Windows-/PowerShell-Lauf.
