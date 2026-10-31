# Dauerhafte Original-App-Signierung – gepruefte Neuinstallations-APK

Stand: 31.10.2026 01:59 CET. **Automatisierte Abnahme der Test-APK bestanden; echte Google-/Android16-Geraeteabnahme noch offen.**

[PR152](https://github.com/xedox737/Steuer-Asistend-APP/pull/152) stellt die Original-App auf den vom Nutzer dauerhaft gespeicherten Schluessel um. Die echte CI-Keystore-Pruefung und eine unabhaengige Pruefung der heruntergeladenen APK bestaetigen denselben Fingerabdruck. Das fruehere Hindernis eines hier nicht verfuegbaren103-Privatschluessels ist fuer die ausdruecklich gewaehlt leere Neuinstallation damit umgangen; es wird keine direkte Update-Kompatibilitaet mit103 behauptet. Der neue private Schluessel bleibt beim Nutzer und in GitHub-Secrets.

## Quellen und durchgefuehrte Pruefungen

| Gegenstand | Nachweis |
| --- | --- |
| Base Main, einschliesslich gemergtem PR151 | `c7c0446dcc40027dffe168013ee8ef4c6a51d8d8` |
| Gepruefter PR-Head | `e2424257931f777046e9026671469fba58421b15` |
| Vollstaendiger Source-Tree | `057b2067e00c52a3d6188f4fd138e8baa6af563a` |
| Inhaltsgleicher lokaler Commit | `a043adf55adc9d168ebb3cc76fdffc5e09746f5d` |
| Tatsaechlicher synthetischer CI-Merge | `a5f3910c96a9ae1abfff7dae517ed143d06b992e` |
| Ganze CI, Versuch1 erfolgreich | [41150234715](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/41150234715) |
| verify / Emulatorjob | `124384771289` / `124388849239` |
| Vollstaendige JVM-Suite | 1.213 eindeutige Methoden,0 Fehler/Errors/Skips |
| Namespace-/Auth-Regressionen | 23 Namespace+48 Auth-/ViewModel-Methoden;Auth unter SDK36/Java21;Source-Hashes abgeglichen |
| Python-Signier-/Veroeffentlichungspruefungen | 16 bestanden |
| Lint,Debug-APK,Instrumentation-Build | Bestanden |
| Native Tests | 70 eindeutige Methoden,0 Fehler/Errors/Skips;API35/Android15 |
| Restore/MERGE und Originale | 60/60 SHA256 identisch,0 Full-Restore- und0 MERGE-Fehler;zweimal MERGE |
| 17-/21-Einheiten-Portfolio und Fachregressionen | Audit ohne Fehler |

XMLs wurden aus den heruntergeladenen Artefakten erneut gezaehlt; Archivgroessen und SHA256 stimmen mit GitHub ueberein. Der zuvor auf Main rote150%-Schriftgroessenfall besteht in diesem Lauf ohne Aenderung seiner Bedingungen. Dies ist keine Behauptung, die allgemeine Ursache dieses frueheren sporadischen Timeouts sei behoben. Dieser neue Lauf wurde nicht wiederholt, kein Test wurde abgeschwaecht oder ausgeblendet. Lokales regulaeres Gradle bleibt am bekannten Wildcard-IP-Fehler blockiert; lokal bestanden16 Python-Tests,striktes YAML-Parsing und diff-check. Beide neuen Signierregressionen waren vorher rot;Protokolle liegen im Archiv.

Der komplette App-Verzeichnisbaum ist gegen die Base bytegleich (`7012f8a214c2fd7bd756d6bb362c81cef707beac`). App-Code,Room,Package-ID,IDs,Backupformate,Drive-Namensraeume und die isolierte Phase11-Test-App wurden nicht geaendert. Die APK enthaelt den auf Main bereits gemergten Drive-Fix. Testportfolios und60 Originale sind synthetische Daten, keine Aussage ueber die reale Datenmenge auf dem Nutzergeraet. Es wurden keine Nutzer-/Drive-Daten geloescht, keine App installiert/deinstalliert und kein echter Google-Login durch Codex ausgefuehrt.

## Tatsaechliche APK und neue dauerhafte Identitaet

[APK-ZIP herunterladen](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/41150234715/artifacts/13022907967) (GitHub-Anmeldung kann erforderlich sein). ZIP entpacken; darin liegt `app-debug.apk`. Dies ist das gepruefte PR-Testartefakt, noch kein automatisch veroeffentlichtes Main-Release.

| Merkmal | Unabhaengig gepruefter Wert |
| --- | --- |
| Package | `com.aistudio.steuerassistent.vnhqkp` |
| VersionCode/Name | `1` / `1.0` |
| min/target/compile SDK | 24 / 36 / 36 |
| APK-Bytes | 34361197 |
| APK-SHA256 | `911d0147f5864fc243ce8f1b815590143442036e5bd8f11317dc05275290dfa4` |
| Zertifikat-SHA1 | `18:76:FC:D5:F5:D3:65:26:FC:44:F3:BA:37:AA:6C:AC:AB:FB:D9:8D` |
| Zertifikat-SHA256 | `279613fee81d1a619e373face816354c961868f9d35664b641117214aba2b5b3` |
| Signatur | Ein RSA3072-Signer;gueltige APK-v2-Signatur |
| Stabile Signierung in CI | `STABLE_PRODUCT_DEBUG_SIGNING=true`;tatsaechlicher APK-Zertifikatswaechter ausgefuehrt und bestanden |
| Direkte Aktualisierung der CF-signierten103 | **Nein**;maschinenlesbar `canUpdateVersion103=false` |

Der echte Keystore in GitHub passt zum neuen Fingerabdruck;es wurde kein temporaerer Ersatzschluessel verwendet. Der historische installierte103-Fingerabdruck CF bleibt getrennt erhalten und wird ebenso wie der alte102-Referenzwert EA fuer die neue Basis abgewiesen. Fuer spaetere Updates dieser neuen Basis wird derselbe gespeicherte Schluessel verwendet. Der kommerzielle Play-App-Signing-Prozess ist hier nicht als eingerichtet behauptet.

## CI-gepruefte Veroeffentlichung

Mit PR152 entfaellt der parallele automatische Release-Schritt aus Android-CI. Der automatische Publisher wartet auf erfolgreich abgeschlossene Main-CI und prueft ueber die API noch einmal exakte SHA,Repository,Push-Ereignis,Workflowdatei und beide Jobs. Auch manueller Start erfordert eine bereits erfolgreiche Main-CI fuer dieselbe SHA. Der ausgecheckte,gebaute und benannte Release-Commit stimmt ueberein;der GITHUB_SHA-Kontext eines spaeteren workflow_run wird nicht faelschlich als Build-Commit verwendet. Live-Metadaten eines alten erfolgreichen Main-Laufs wurden akzeptiert;der nach dem alten Merge rote Main-Lauf wurde korrekt gesperrt. Die drei Spezial-Publisher bleiben ausschliesslich manuell und behalten die strikten Keystore-/APK-Pruefungen;ihre Texte nennen den Neuinstallationswechsel.

PR152 wurde fuer diesen Nachweis nicht automatisch gemergt. Der Dokumentationsnachtrag veraendert den bereits geprueften Code-Head nicht. Das unveraenderte bisherige Main-Release kann bis zum Merge weiterhin am alten Pin blockiert sein;die obige PR-Test-APK ist davon unabhaengig gebaut und geprueft.

## Noch erforderliche Nutzer-/Geraeteschritte

Der Nutzer meldete den neuen OAuth-Fingerabdruck zuletzt als **noch nicht eingetragen**. Im bisherigen Google-Cloud-Projekt muss ein Android-Client fuer denselben Paketnamen und exakt dieselbe neue SHA1 eingerichtet werden. Alte Clients behalten. Die Cloud-Console-Konfiguration wurde hier nicht eingesehen;ein echter Login ist deshalb nicht als bestanden behauptet.

Nur wenn103 weiterhin lokal leer ist und die neue APK vollstaendig heruntergeladen wurde: Die alte Original-App kann fuer den ausdruecklich gewaehlten Neuinstallationsweg entfernt und die neue APK installiert werden. Die getrennte Phase11A-App und vorhandene Drive-Daten bleiben unangetastet. Weitere Anleitung: [INSTALLATION.md](INSTALLATION.md). Danach reale Anmeldung,dauerhafte Struktur,Neustart,Netzunterbrechung und echtes Backup/Restore auf dem POCO pruefen. Der bekannte alte Beleg-Speichertest kann weiterhin an seinen eigenen fehlenden Testdateien scheitern;diese werden nicht automatisch ersetzt. Erfolgreiche Initialisierung ist kein behaupteter erfolgreicher vollstaendiger Speichertest.

Die neue `.keystore`-Datei auf dem Nutzer-PC dauerhaft privat sichern. Den privaten Schluessel nicht in Chat oder oeffentliche Dateien stellen. Die erneute manuelle Einrichtung bei jedem App-Update ist mit dieser konstanten Basis nicht vorgesehen.

## Reproduzierbarer Nachweis

`Evidence.zip` enthaelt echte XMLs,CI-Metadaten,Integritaets-/Portfolio-Berichte,oeffentliche Zertifikatsausgaben und rote/gruene Signierregressionen. Keine privaten Schluessel,Nutzer-Konfiguration oder APK-Bytes. Nach Entpacken `python3 verify-evidence.py` ausfuehren. Der Pruefer kontrolliert SHA256-Manifest,1213 JVM-,70 native-,23 Namespace-,48 Auth-Methoden,60 Originalhashes,die neue Signaturbasis und die fehlende direkte103-Update-Kompatibilitaet erneut. Vollstaendige neue Ausfuehrung ueber Android-CI am angegebenen Code-Head.

**Automatisiert gepruefte Neuinstallations-Test-APK: bereit. Echte Google-/Android16-Geraeteabnahme: offen.**
