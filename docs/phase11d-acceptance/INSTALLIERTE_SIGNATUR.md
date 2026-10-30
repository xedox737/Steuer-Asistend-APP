> Aktualisierter Nachweis: [Reparatur veralteter Diagnose-Verweise und tatsächliche Version-103-Signatur](../phase11d-stale-acceptance/ABNAHME.md). Die installierte Signatur ist inzwischen als CF:43:8D:2E:D2:1D:0F:D5:F8:B0:AB:81:BF:51:8A:51:78:A7:73:85 verifiziert. Der frühere unbekannte Gerätestatus unten ist historisch.

# Signatur der tatsächlich installierten Original-App bestimmen

Der Nutzer hat bestätigt, dass `EA:42:88:…:76:49` aus einer heruntergeladenen GitHub-APK stammt. Damit ist die Signatur der POCO-Installation noch nicht nachgewiesen. Die beiden früheren Publisher verwendeten denselben Release-Tag und APK-Dateinamen; ein späterer Download kann eine andere Signatur tragen.

Benötigt werden ein Rechner mit Android Platform Tools (`adb`), Android SDK Build Tools (`apksigner`) und eine vom Nutzer freigegebene USB-Debugging-Verbindung zum POCO. Die folgenden Schritte lesen die vorhandene APK; sie ändern weder die App noch ihre Daten.

1. Geräteverbindung prüfen:

   ```text
   adb devices
   ```

2. Pfad der installierten Original-App abfragen:

   ```text
   adb shell pm path com.aistudio.steuerassistent.vnhqkp
   ```

3. Aus der Ausgabe die Zeile mit `base.apk` verwenden und das Präfix `package:` entfernen. Den vollständigen Gerätepfad in den folgenden Befehl einsetzen:

   ```text
   adb pull "GERAETEPFAD_ZUR_BASE_APK" "immopilot-installiert.apk"
   ```

4. Das Zertifikat dieser ausgelesenen APK prüfen:

   ```text
   apksigner verify --print-certs "immopilot-installiert.apk"
   ```

   Unter Windows heißt das SDK-Werkzeug gegebenenfalls `apksigner.bat`.

5. Ausschließlich die Zeile **`Signer #1 certificate SHA-1 digest`** mitteilen. Gemeint ist der Zertifikat-SHA1, nicht der APK-Dateihash und nicht der Public-Key-SHA1. Private Schlüssel, Tokens oder Passwörter werden nicht benötigt.

Danach wird dieser Wert mit den Android-OAuth-Clients im tatsächlich verwendeten Cloud-Projekt verglichen. Der vorhandene Eintrag für die Download-SHA1 allein bestätigt die Gerätekonfiguration nicht. Bei abweichender Signatur muss zuerst ein passender OAuth-Client für die vorhandene Installation geprüft und anschließend deren nativer Login erneut getestet werden; eine APK-Neuinstallation ist dafür nicht erforderlich. Falls die Werte übereinstimmen, bleiben Projektzuordnung, Consent/Audience, Scope, Dienste und die eigentlichen Gerätelogs zu prüfen.

Ohne diese Messung und den echten Google-/Android16-Test bleibt die Freigabe NO-GO. Die Originalinstallation wird nicht deinstalliert oder ersetzt. Zertifikatspins und zukünftige Update-Signierung werden erst anhand verifizierter Geräteinformationen freigegeben.
