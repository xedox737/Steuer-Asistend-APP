> Aktualisierter Nachweis: [Reparatur veralteter Diagnose-Verweise und tatsächliche Version-103-Signatur](../phase11d-stale-acceptance/ABNAHME.md). Die installierte Signatur ist inzwischen als CF:43:8D:2E:D2:1D:0F:D5:F8:B0:AB:81:BF:51:8A:51:78:A7:73:85 verifiziert. Der frühere unbekannte Gerätestatus unten ist historisch.

# Phase 11D – Abschließender automatisierter Testnachweis

Stand: 29.10.2026. **Automatisierte Prüfung bestanden; praktische Veröffentlichung NO-GO.** Die konkrete Ursache des Google-Statuscodes 10 auf dem POCO ist weiterhin nicht bewiesen. Der Nutzer hat inzwischen bestätigt: Die angegebene SHA1 stammt aus der heruntergeladenen GitHub-APK, nicht direkt aus der installierten App. Die Gerätesignatur ist deshalb noch unbekannt. Es fand kein echter Google-Login, kein Gerätetest auf Android16 und kein Update der vorhandenen Installation statt.

[PR #151](https://github.com/xedox737/Steuer-Asistend-APP/pull/151) bleibt Draft. Kein Merge, keine Produktinstallation ersetzt oder deinstalliert. Die isolierte Phase11-Test-App wurde nicht geändert.

## Quellstände

| Gegenstand | SHA |
| --- | --- |
| Tatsächliche Base, einschließlich PR #150 | `bdc17be4abc1398dabddb28eda2d9074d455a031` |
| Erster Auth-/Consent-Fix | `f732ee5b21a6cfb35c2a1b449aab41335639b452` |
| Absicherung zusätzlicher Publisher | `e2a8df4e0f9f9288553596536b4198c28e175061` |
| Letzte Produktkorrektur: sofortiger Retry | `541681f5663ca3453c63b5e7d9d07947f6c85f57` |
| Geprüfter PR-Head | `ff1b6eaaba2b3e1a6c0349c908b981175f6888f3` |
| Geprüfter vollständiger Tree | `716e2a59f31d6db0f90fb287f3b10e0fc36a2558` |
| Tatsächlicher synthetischer PR-Merge-Checkout in CI | `907f0ec4e79e127495401e5ab650c708bb2abb0b` |

Die SHA-Zuordnung lokaler und über die GitHub-Schnittstelle importierter Commits steht im [ursprünglichen Bericht](https://github.com/xedox737/Steuer-Asistend-APP/blob/ff1b6eaaba2b3e1a6c0349c908b981175f6888f3/docs/PHASE11D_ORIGINAL_DRIVE_AUTH.md) und unter `docs/phase11d-evidence/github-publication.json`. Commit-Metadaten unterscheiden sich; Blob- und Tree-Inhalte wurden gegen die lokalen Git-Hashes geprüft. Der direkte Fetch scheiterte am Sandbox-Proxy; Main wurde unabhängig über GitHub geprüft und exakt importiert. Die ursprünglichen offenen CI-Felder sind historisch und werden durch diesen Nachweis aktualisiert.

## Belegte Ursachen und Reparaturen

1. `UserRecoverableAuthException` wurde als allgemeiner Auth-Fehler behandelt. Die App verwarf die Google-Freigabe-Intent und zeigte einen Loginverlust. Der neue rote Ausgangstest lief vor dem Produktfix: 24 tatsächliche SDK35-Methoden, genau dieser Fall fehlgeschlagen. Die Freigabe wird jetzt einmalig über den vorhandenen ActivityResult-Mechanismus gestartet. Zustimmung führt zu einer erneuten Tokenanforderung und sicheren Drive-Prüfung, nicht zu einem ungeprüften Erfolgsstatus.
2. Ein Fehler wurde per StateFlow veröffentlicht, bevor sein Initialisierungsjob beendet war. Ein sofortiges „erneut versuchen“ wurde als Doppelaufruf verworfen. Der vollständige SDK36-Lauf machte dies sichtbar; eine deterministische lokale Reproduktion schlug anschließend ebenfalls fehl (46 Methoden, genau der neue Retry-Fall rot). Jetzt wird höchstens eine explizite Wiederholung pro Sitzung vorgemerkt. Sie entfällt nach Erfolg, Logout oder Kontowechsel. Es gibt keine automatische Schleife und keine automatische Wiederholung teilweiser Übertragungen.
3. Version 102 stammt aus `publish-verified-apk.yml`, das weiterhin einen zufälligen Schlüssel erzeugte und den Schutz aus PR #150 umging. Drei alte Einmal-Workflows konnten ebenfalls direkt veröffentlichen. Alle fünf direkten Release-Publisher verlangen jetzt eine passende dauerhafte Signieridentität und prüfen das tatsächlich gebaute APK-Zertifikat. Die drei alten Codeänderungs-Workflows dürfen nur manuell starten, damit ein späterer Merge dieser Korrektur keine alten automatischen Codeänderungen auslöst.

Native Sign-In-Ergebnisse prüfen Android-Ergebnis, tatsächlichen SDK-Status und gewährten `drive.file`-Scope. Fehlercode 10 bleibt auch bei Android RESULT_CANCELED in der Diagnose erhalten. Doppelte und verspätete Rückgaben nach Logout/Kontowechsel werden abgewiesen. Fehlertexte enthalten keine Tokens, Kontodaten oder Intent-Extras. Die bestehenden Netzwerk-/401-/403-/404-/Cancellation-/Namespace-Regeln bleiben erhalten.

Betroffene Produktklassen: `NativeDriveSignIn`, `ReceiptAppUi.GoogleDriveSyncCard`, `ReceiptViewModel` und `DriveConnectionFailure`; dazu Signing-/Workflow-Wächter und Tests. Package-ID, Manifest, Room-Schema, Backupformat und Drive-Namespace-Policy bleiben unverändert. Der native Hauptpfad speichert keine neuen Access-/Refresh-Tokens in App-Preferences.

## Tatsächliche Prüfungen

[Finale CI 40919974587](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/40919974587), unveränderter Head `ff1b6ea`:

| Prüfung | Ergebnis |
| --- | --- |
| Vollständige JVM-/Robolectric-Suite | 1.203 eindeutige Methoden, 0 Fehler/Errors/Skips |
| Native Callback-/Consent-/Verbindungsregressionen | 47 tatsächliche Methoden unter SDK36/Java21: 10 SDK-Intent-/Optionsfälle und 37 ViewModel-/HTTP-/Lifecycle-Fälle |
| Lint, Debug-APK und Instrumentation-Build | Bestanden |
| Native Android-Emulatorprüfung | 70 eindeutige Methoden, 0 Fehler/Errors/Skips; API35/Android15 |
| Python-Signaturprüfungen | 8 bestanden, einschließlich Ablehnung eines synthetischen gültigen Fremdschlüssels ohne dessen Installation oder Ausgabe |
| Vorherige Drive-Reparaturmethoden | 36 tatsächlich ausgeführt, bestanden |
| Originale und Restore/MERGE | 60/60 SHA256-identische Testoriginale, 0 Full-Restore- und MERGE-Fehler; zweimaliges MERGE geprüft |
| Unabhängiger Phase10B-Portfolio-/DATEV-Audit | Keine Fehler |

JVM- und Emulator-XMLs wurden aus den heruntergeladenen Artefakten unabhängig erneut gezählt. Archivgröße und SHA256 wurden vorher gegen GitHub abgeglichen. Der SDK36-Wächter prüft alle tatsächlichen Methoden, Fehler/Skips sowie die Source-Hashes beider Auth-Testklassen. Es wurde keine Assertion, kein Timeout und keine Testbedingung für ein grünes Ergebnis abgeschwächt.

Ergänzend lokal: 47 SDK35-Methoden bestanden; die Main-Quellen wurden frisch kompiliert, für die beiden letzten reinen Testergänzungen unverändert wiederverwendet. Die eingecheckten Tests bleiben SDK36. Lokales reguläres Gradle scheitert am FileLockContentionHandler/fehlenden Wildcard-IP; `/dev/kvm` fehlt. Die nativen Prüfungen fanden auf GitHub statt. Lokales ADB war nicht nutzbar; keine POCO-Verbindung wurde hergestellt.

Nicht als Abnahme gezählt: [40913257040](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/40913257040) wurde wegen der belegten Publisher-Lücke ersetzt und abgebrochen. [40914708522](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/40914708522) führte 1.201 Methoden aus, eine scheiterte am verlorenen unmittelbaren Netzwerk-Retry; Emulator und APK-Build wurden danach übersprungen. Dieser rote Lauf und seine XMLs bleiben erhalten. Es wurde gezielt Produktcode korrigiert und anschließend die vollständige finale Suite ausgeführt.

## Daten- und App-Schutz

`unchanged-protected-sources.json` weist sieben bytegleiche Quellen gegenüber der Base nach: Namespace-Policy und DriveEnvironment, Room-Datenbank, Supplemental-Backup, Preference-Inventar, Buildkonfiguration und Manifest. Bestehende Immobilien, Einheiten, Mietverhältnisse, Dokument-/Belegzuordnungen, Bank, DATEV-Historie, AfA, Fahrtenbuch und Einstellungen werden nicht migriert oder bereinigt. Die CI prüft synthetische 17-/21-Einheiten-Portfolios; 60 Originale sind der Testsatz, keine angenommene Dateianzahl auf dem Nutzergerät.

Es wurden keine bestehenden Google-Drive-Ordner verändert oder gelöscht. Reale Drive-Übertragungen fanden nicht statt. Testtransporte waren ausdrücklich simuliert; lokale Nutzung bleibt unabhängig von Drive möglich.

## Tatsächliche CI-APK: kein kompatibles Produktupdate

Die heruntergeladenen APK-Bytes wurden unabhängig mit Build-Tools36 `apksigner` und `aapt2` geprüft:

| Merkmal | Wert |
| --- | --- |
| Package | `com.aistudio.steuerassistent.vnhqkp` |
| APK-Bytes | 34.361.197 |
| APK-SHA256 | `dee880bf112cae962d71091e6c019984e22249f5e0a2ae58cf182115cf89aa4a` |
| Zertifikat-SHA1 | `46bb24417545247eec24ba7811ab82bfd881470d` |
| Zertifikat-SHA256 | `61891a35f216f7c899655083d06de8dce5d04ffc053ba14f95a26b805e6d2682` |
| Signatur | gültige v2-Signatur, ein RSA2048-Debug-Signer |
| SHA1 der heruntergeladenen Version102-APK laut Nutzer | `ea42886d5b6eaffbad492686d965b74762a97649` |

Die CI verwendete `STABLE_PRODUCT_DEBUG_SIGNING=false`; der zusätzliche Produktupdate-Zertifikatswächter wurde für dieses ausdrücklich temporäre PR-Artefakt nicht ausgeführt. Das ist kein positiver Nachweis einer freigegebenen Signierkonfiguration. Das Artefakt trägt eine andere Signatur als die heruntergeladene Version102-APK. Weil die tatsächlich installierte Gerätesignatur noch unbekannt ist, ist eine Update-Kompatibilität ausdrücklich nicht bestätigt. Es wird nicht als funktionierende neue Produktversion ausgeliefert. Kein Release wurde durch diesen Auftrag veröffentlicht.

**Keine Installation, Deinstallation oder Profiländerung auf dem POCO als Umgehung durchführen.** Technische Tests einer temporären CI-APK ausschließlich auf einem separaten leeren Testgerät oder neuen Emulator. Der Original-Privatkey ist nicht aus einer APK rekonstruierbar; seine Verfügbarkeit beim Nutzer bleibt unbekannt.

## Noch offene tatsächliche Google-/Android16-Abnahme

1. Zertifikat direkt aus der installierten POCO-App ermitteln und mit dem Android-OAuth-Client abgleichen. Beide früheren Haupt-Publisher nutzten denselben Release-Tag und APK-Dateinamen; ein späterer Download beweist die Gerätesignatur nicht. Der Nutzer hat die Download-Herkunft bestätigt; ob die installierte App dieselbe oder eine andere Signatur trägt, ist offen. Die bestehenden Zertifikatspins verwenden noch den angegebenen Download-Referenzwert und sind vor einem GO mit dem Gerät abzugleichen.
2. Tatsächliche Cloud-Projekt-ID/-Nummer und Android-Client-Zuordnung, Drive-API-Aktivierung, Consent/Audience, Testnutzer, Scope und Wirksamkeitszeitpunkt prüfen. Diese Console-Einstellungen wurden hier nicht eingesehen. Google nennt für Testing-Nutzer eine siebentägige Autorisierungsfrist; der konkrete Projektstatus ist unbekannt. [Primärquelle](https://support.google.com/cloud/answer/15549945?hl=en).
3. Passenden vorhandenen Originalschlüssel und freigegebenen SHA1 sicher im vorgesehenen CI-Secret/der Repository-Variable einrichten. Weder Schlüssel noch Tokens in Chat/Logs/Git geben. Vor einem Update das tatsächlich gebaute APK-Zertifikat positiv prüfen.
4. Real auf Android16: Konto auswählen, Drive-Freigabe bestätigen, tatsächliches Anmeldeergebnis und Scope prüfen; produktiven Root `Steuerassistent Belege/_BelegApp-Daten` sicher erkennen; Neustart, Tokenablauf/Erneuerung, kurzzeitigen Netzausfall, Widerruf, Abbruch und bewussten Kontowechsel prüfen. Dabei den tatsächlichen lokalen Bestand erfassen und sämtliche Originale hashen, nicht Testdaten auf das Gerät übertragen.
5. Ohne passenden Schlüssel bleibt die alte Installation unverändert. Erst unabhängigen lokalen Export aller Geschäftsdaten/Originale sichern und Restore, IDs, Beziehungen und Hashes auf einem separaten leeren Testgerät prüfen. Eine spätere Migration braucht einen eigenen konkret freigegebenen und praktisch validierten Plan; hier wird keine Migration oder Deinstallation autorisiert.

Weitere dokumentierte Grenzen: GoogleSignIn/GoogleAuthUtil sind veraltete APIs; ein langfristiges kommerzielles GO verlangt die Bewertung/Validierung der unterstützten Identity-/AuthorizationClient-Lösung. Der optionale unveränderte Web-/manuelle Altpfad ist kein dauerhafter Produktionsweg und speichert sein temporäres Token noch in den bisherigen Preferences; diese Altlast muss vor kommerzieller Freigabe sicher entfernt oder migriert werden. Es werden dafür hier keine bestehenden Credentials pauschal gelöscht. Kunden sollen später nur zustimmen; OAuth-Projekt und geeignete stabile App-Signierung werden vom Anbieter einmal korrekt eingerichtet.

## Reproduzierbarer Nachweis und Entscheidung

`ImmoPilot-Phase11D-Abnahme-Evidence.zip` enthält tatsächliche XMLs, CI- und Integritätsberichte, Signaturausgaben, Quellschutz-Hashes und historische rote Nachweise. Nach dem Entpacken prüft `python3 verify-evidence.py` das SHA256-Manifest, tatsächliche eindeutige Methoden und die wesentlichen Integritätsbefunde erneut. Die APK selbst ist bewusst nicht im Evidence-Archiv enthalten. Vollständiger Neuaufbau über den unveränderten CI-Workflow am getesteten Head mit Gradle9.3.1, Java21 für JVM und Java17 für Android; keine globale SDK-Abschwächung.

**Automatisierte Abnahme: bestanden. Praktische Phase11D-/Veröffentlichungsfreigabe: NO-GO.** Die tatsächliche Code10-Geräteursache, live Google-Autorisierung, sichere Update-Signierung und der reale Android16-Test bleiben offen. PR bleibt Draft; kein automatischer Merge.

Aktualisierte Geräteanleitung: [INSTALLIERTE_SIGNATUR.md](INSTALLIERTE_SIGNATUR.md). Diese Messung ist ohne Änderung oder Neuinstallation der App möglich. Bei abweichender Gerätesignatur kann ein passender OAuth-Eintrag für die vorhandene Installation geprüft werden, bevor überhaupt ein App-Update erwogen wird.
