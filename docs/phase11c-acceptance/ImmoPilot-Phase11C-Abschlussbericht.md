# ImmoPilot Phase11C – Abschlussbericht

Issue #148 · Stand 28.10.2026 · Automatisierte Abnahme und verbleibende Praxisgrenzen

**Automatisierte Abnahme bestanden.** Beide vollständigen CI-Varianten sind grün. Die praktische Google-/Android16-Freigabe und die Updatefähigkeit über die vorhandene POCO-Installation bleiben ausdrücklich offen.

## 1. Geprüfte Quellstände und Veröffentlichung

| Gegenstand | SHA / Nachweis |
| --- | --- |
| Base main | `6f8f26f4055c21a0df994e52b970f3fecbf4889d` |
| Lokal geprüfter Produktfix | `2b58e6aaab913acb270695512b7fe4b4cbef1cf4` |
| Derselbe Produktfix auf GitHub | `6ed31a292abdea4fd06834d52e494012d38e29b9` |
| Identischer Git-Tree beider Codecommits | `7252c88f004c7788aedd39a476e716527378db03` |
| Veröffentlichter PR-Head mit ursprünglicher Evidence | `cdb9a1006a3a72ee873a0986aaa512c9c5ceab05` |
| Bestehende frühere isolierte Test-App | `f56d0c164262b1af2bd65936cf769b7aef3b0143` |
| Lokal integrierte Test-App | `9afb546fea289012bbb43d503002e6748e482687` |
| Veröffentlichte Test-App / CI-Head | `9624be2599bd34287a910689d5182b3cc47e4872` |
| Reparatur-PR | https://github.com/xedox737/Steuer-Asistend-APP/pull/149 |

Der direkte Git-Transport konnte in der Sandbox den vorgesehenen Proxy nicht erreichen. Der vor Änderungen mehrfach versuchte Fetch ist im Lokalbericht dokumentiert. Der tatsächliche main wurde über GitHub geprüft und stimmte mit dem vorhandenen Checkout überein. Projektanweisungen, Architektur, PR #144, PR #146 und Issue #148 wurden vor dem Produktfix ausgewertet.

Die Veröffentlichung erfolgte über die GitHub-Schnittstelle. Alle 138 übertragenen Blob-Objekte wurden anhand ihrer Git-Hashes geprüft; die drei Produkt-Trees für rote Regressionen, Produktfix und ursprünglichen Bericht sind bytegleich erhalten. Die GitHub-API erzeugte andere Commit-Metadaten und damit andere Commit-IDs. `docs/phase11c-github-publication.json` ordnet beide Historien eindeutig zu. In der Testintegration wurden ausschließlich die CI-Referenzpins und die zugehörige Provenienz auf den veröffentlichten Produkt-SHA angepasst; der frühere Test-App-Head ist zusätzlicher Commit-Elternteil. Gemeinsame Produktquellen, QA-Hooks und Originalfixture sind dadurch nicht verändert worden.

Die beiden veröffentlichten Branches heißen `fix/phase11c-drive-connection` und `agent/phase11c-isolated-drive`. PR #149 ist Draft. Kein PR wurde gemergt und keine Produktinstallation ersetzt oder deinstalliert. Dieser ergänzende Abschlussbericht wird getrennt vom geprüften Code veröffentlicht, damit seine Erstellung keine laufende Code-CI abbricht.

## 2. Nachgewiesene Codefehler und Geräte-Grenze

Nachgewiesen ist ein Mechanismus für eine falsche Anzeige der Abmeldung. Der konkrete erste Fehler des POCO X8 Pro wurde mangels Geräteprotokoll und echtem OAuth-Zugang nicht ermittelt. Es ist daher nicht bewiesen, ob dort ein Token-, Konto-, Netzwerk-, Berechtigungs- oder Metadatenfehler vorausging.

1. `ReceiptViewModel.initializeDrivePersistence()` und `syncAllToDrive()` behandelten jede geworfene Exception wie einen Loginverlust. Schon ein Netzwerkfehler bei der nativen Tokenanforderung setzte `isDriveConnected=false` und zeigte die Web-Login-Aufforderung.
2. `DrivePersistenceRepository.initializeDriveStorage()` wandelte umgekehrt HTTP401 und Coroutine-Abbrüche in untypisierte Fehlertexte um. Der Aufrufer konnte echte Autorisierungsfehler nicht zuverlässig erkennen.
3. `getValidToken()` verwendete ein gespeichertes manuelles/Web-Token unbegrenzt. Der implizite Web-Flow liefert keinen Refresh-Token. Der native Login konnte ein altes Web-Token weiterverwenden, auch bei einem Kontowechsel.
4. Die bestehende Ordnersperre schützte einzelne Ordneroperationen, aber nicht die gesamte Suche und Anlage der Konfiguration. Zwei Initialisierungen erzeugten im roten Test zwei `appConfig`-Dateien.
5. Die unabhängige Firestore-Initialisierung konnte den Drive-Autorisierungshinweis beim Neustart überschreiben. Ein zusätzlicher Neustarttest deckte diese Statuskollision auf.

## 3. Änderungen und Zustandsvertrag

`DriveAccessFailure` bleibt die vorhandene sichere HTTP-/Metadaten-/Namespace-Fehlerklasse und erhält die Kategorie TIMEOUT. `DriveConnectionFailure` ordnet typisierte Fehler den Kategorien AUTHORIZATION, ACCESS, NOT_FOUND, NETWORK, TIMEOUT, METADATA, SAFETY und OTHER zu. Technische Rohmeldungen, Antwortkörper, Tokens und Kontoangaben werden nicht in die neuen Benutzermeldungen übernommen. Eine bloße Zeichenfolge wie „401“, „Unknown“ oder „Anmeldung“ entscheidet nicht mehr über den Kontostatus.

`isDriveConnected` bedeutet: Ein Konto beziehungsweise Credential ist verknüpft und wurde nicht ausdrücklich als unautorisiert erkannt. Netzwerkverfügbarkeit, Ordnerprüfung und Sync-Ergebnis werden über die vorhandenen Status-/Fehler-Flows angezeigt. Die vorhandene Architektur bleibt erhalten.

| Ereignis | Verhalten |
| --- | --- |
| Netzwerk / Timeout | Konto bleibt verknüpft; verständlicher Fehler und vorhandene Sync-Aktion zum Wiederholen |
| HTTP403 | Zugriffs-/Berechtigungshinweis; keine pauschale Abmeldung |
| HTTP404 | Zugriff/Ordner prüfen; keine pauschale Abmeldung oder Ersatzanlage aufgrund des Fehlers |
| Metadaten-/Namespacefehler | Zugriff bleibt blockiert; Konto wird nicht als abgemeldet dargestellt |
| Native Tokenablehnung mit HTTP401 bei Initialisierung | Play-Services-Cache invalidieren, einmal neu anfordern und Zugriff erneut prüfen |
| HTTP401 während einer längeren nativen Übertragung | Credential erneuern und sicher initialisieren; nach erfolgreicher Prüfung manuelle Wiederholung anbieten, teilweise erledigte Übertragung nicht automatisch wiederholen |
| Wiederholtes HTTP401 / typisierter Google-Auth-Fehler | Gezielte erneute Autorisierung; Kontobezeichnung und lokale Daten bleiben erhalten |
| Abgelaufenes oder abgelehntes Web-/manuelles Token | Erneute Autorisierung anbieten; keine erfundene Token-Erneuerung |
| Coroutine-Abbruch | Weitergeben; Busy-Zustände beenden, keinen Loginfehler daraus erzeugen |
| Erfolgreicher Zugriff | Verknüpften Kontostatus erhalten |

Die gesamte Repository-Initialisierung besitzt nun einen gemeinsamen Mutex, auch über mehrere Repository-Instanzen hinweg. Im ViewModel werden doppelte laufende Initialisierungen und Gesamtsyncs zusammengefasst. Ein Sitzungszähler und das Abbrechen alter Jobs verhindern, dass Ergebnisse einer abgelösten Anmeldung oder eines expliziten Disconnects den neuen Status überschreiben.

Beim Start werden gespeicherte Credentials als solche wiederhergestellt. Ein Neustart ist kein neuer Login und verlängert kein Web-Token. Eine echte Autorisierungsanforderung bleibt über den Neustart erkennbar. Ein nativer Login entfernt ausschließlich die bisherigen manuellen Tokenfelder und den betreffenden Autorisierungsfehler; kein pauschaler Preferences-Reset findet statt.

Der Hauptbutton nutzt den bereits vorhandenen Play-Services-Login mit `drive.file`. Die zeitlich begrenzte Web-/manuelle Verbindung bleibt unter „Alternative Anmeldung / Erweitert“ verfügbar und wird entsprechend erklärt. Beide Web-Callbacks übernehmen `expires_in`; doppelte Callbacks verlängern die Frist nicht. Der Scope wurde nicht erweitert und keine zweite Authentifizierungsschicht eingeführt.

Betroffene Produktdateien: `ui/ReceiptViewModel.kt`, `ui/ReceiptAppUi.kt`, `api/GoogleDriveClient.kt`, `api/DriveAccessFailure.kt`, `data/DrivePersistenceRepository.kt` und die neue kleine Fehlerzuordnung `data/DriveConnectionFailure.kt`. Der HTTP401-/Cancellation-Kontrollfluss wird durch die bestehenden Drive-Hilfsmethoden weitergegeben, statt dort als `false`, `null` oder leerer Bestand zu verschwinden. Die Restore-/Diagnosepfade verwenden dieselbe Fehlerbehandlung.

## 4. Rote Tests vor dem Fix

Die Regressionen wurden vor Änderung der Produktimplementierung eingecheckt. Ein regulärer Gradle-Start scheiterte an der Sandbox. Ein ergänzender direkter JUnit-/Robolectric-Lauf war mit den vorhandenen lokalen Abhängigkeiten möglich. Die drei betroffenen ursprünglichen Main-Quellen wurden dafür frisch kompiliert. **12 tatsächlich ausgeführte Methoden, 9 Fehler**:

| Roter Fall | Beobachtung vor dem Fix |
| --- | --- |
| Netzwerk bei nativer Tokenanforderung | Falsche Abmeldung |
| Timeout bei nativer Tokenanforderung | Falsche Abmeldung |
| Neustart mit gespeichertem Konto und Offlinefehler | Falsche Abmeldung |
| HTTP401 bei manuellem Token | Keine erforderliche Autorisierung angezeigt |
| Native Anmeldung nach altem Web-Token | Altes Token weiterverwendet |
| Abgelehntes natives Token | Keine Invalidierung/Erneuerung; erwarteter Erfolg blieb aus |
| Zwei Initialisierungen | Zwei Konfigurationsdateien |
| Cancellation im Repository | Abbruch verschluckt |
| Cancellation im ViewModel | Abbruch als Syncfehler gespeichert |

Die anderen drei Fälle bestanden bereits: Metadatenfehler, HTTP403 und HTTP404 konnten als Repository-Fehler zurückkehren, ohne die Anmeldung zurückzusetzen. Nicht jeder Drive-Fehler führte daher schon im alten Code zum beobachteten UI-Fehler.

Primärnachweise: `phase11c-evidence/before-fix-direct-sdk35.txt`, `before-fix-recompiled-main-sdk35.txt` und der reproduzierbare Baseline-Lauf mit festem Base-Ref. Der erste der beiden Läufe verwendete vorhandene Klassen; der zweite kompilierte die betroffenen Main-Quellen frisch. Die Ergebnisse stimmen überein.

Die angeforderten A–G-Fälle sind konkret gebunden: A durch `networkDuringDriveInitializationKeepsAccountAndOriginalPreferences` und den Netzwerkfehler bei nativer Tokenanforderung; B durch `metadataFailureDoesNotSignOutOrCreateReplacementFolders`; C durch die abgelehnten manuellen/nativen Tokens und HTTP401 während Sync; D durch Konto-/Tokenfrist-/Autorisierungs-Neustarttests; E durch `simultaneousInitializationsDoNotDuplicateConfig` und doppelte Login-Callbacks; G durch Repository- und ViewModel-Cancellation. Diese Methoden gehören zur neuen Klasse `DriveConnectionRegressionTest` und bestehen in beiden SDK36-Suiten.

F wird zusätzlich von der unveränderten Klasse `ProductionDriveNamespaceTest` abgedeckt: `allSevenForeignConfigIdsAreRejectedAndForeignRestoreCannotClearLocalData` prüft sämtliche fremden Konfigurations-IDs, `directForeignReadsWritesDeletesAndCrossMovesAreDeniedWithoutProductChanges` sperrt fremde Lese-/Schreib-/Lösch-/Verschiebeversuche. In der isolierten Test-App ist der fremde Namespace der produktive Root. Metadaten zur Sicherheitsprüfung dürfen gelesen werden; fremde Datei-Inhalte und Veränderungen sind gesperrt. Diese bestehenden Sicherheitsregressionen waren bereits grün und bleiben es in der vollständigen Abnahme.

## 5. Vollständige automatische Prüfung auf GitHub

| Variante | Verify / Lint / JVM / Build | Native Emulatorprüfung | Tatsächliche Methoden |
| --- | --- | --- | --- |
| Produkt | Bestanden | Bestanden, zweiter Versuch | 1179 JVM / 70 native |
| Isolierte Test-App | Bestanden | Bestanden | 1206 JVM / 73 native |

Produkt-CI: https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/40733042865

Test-App-CI: https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/40725684070

Produkt-Verify `122974163326` führte 1.179 eindeutige JVM-Methoden ohne Fehler/Skips aus. Die PR-CI prüfte den synthetischen Merge-Checkout `1fcce7b9e731e33b2172e26f68654eef9c09f615` aus Base `6f8f26f` und dem veröffentlichten PR-Head `cdb9a10`; Head und tatsächlicher Checkout werden getrennt dokumentiert. Die Test-App prüfte den veröffentlichten Head `9624be2` mit 1.206 JVM- und 73 nativen Methoden ohne Fehler/Skips.

Der erste Produkt-Emulatorversuch (Job `122987684224`) führte alle 70 Methoden aus: 69 bestanden, eine scheiterte. `AppDateCalendarFont150Test.receiptCalendarManualValidationCancelBackAndPersistence` warf `Espresso RootViewWithoutFocusException` bei `closeSoftKeyboard()` (Kalendertestzeile 47), nachdem der Activity-Root keinen Fensterfokus erhalten hatte. Der gleiche unveränderte Test war in der Test-App-CI grün. Daher wurde genau dieser fehlgeschlagene Emulatorjob einmal unverändert manuell wiederholt. Keine Assertions, UI-/Produktquellen oder Timeouts wurden dafür geändert. Der erste rote XML-Nachweis bleibt unter `evidence/product/emulator-attempt1/` erhalten.

Der wiederholte Produkt-Emulatorjob `123026781613` bestand mit 70/70 eindeutigen Methoden, ohne Fehler, Errors oder Skips. Diese Wiederholung belegt das Bestehen am identischen Quellstand, beseitigt aber nicht das dokumentierte einmalige Fokusproblem des ersten Laufs. Die Produkt-Debug-APK wird weiterhin aus dem erfolgreichen ersten Verify-Job ausgeliefert; ihr Zertifikat und ihre Bytes bleiben dadurch eindeutig.

Die 23 neuen Verbindungsregressionen laufen im regulären CI-JVM-Task unter SDK36/Java21. Beide Varianten behalten die vollständigen bestehenden JVM-/Robolectric-Suiten und Ausführungswächter, die 17 vorherigen Initialisierungsregressionen sowie die 36 vorherigen Reparaturmethoden. Keine Assertion oder Testbedingung wurde für ein grünes Ergebnis abgeschwächt; keine Methoden wurden deaktiviert. Lint, Debug-/AndroidTest-Build und die bestehenden Packaging-/Integritäts-/Isolationswächter sind Teil der CI-Jobs. Signierte Release-Schritte ohne konfigurierte Release-Schlüssel sind keine Debug-APK-Freigabe und werden nicht als ausgeführte Gerätetests gezählt.

Die tatsächlich ausgeführten JUnit-XMLs wurden aus den heruntergeladenen Workflow-Artefakten erneut ausgewertet. Dateigröße und SHA256 der Archive wurden vor der Auswertung mit den GitHub-Artefaktangaben verglichen. Fehler, Errors, Skips und Suite-Platzhalter werden nicht als bestandene Methoden gezählt. Die nativen Emulatoren verwenden API35/Android15; SDK36-JVM-Prüfungen ersetzen keinen realen Android16-OAuth-Test.

Ein anfänglicher Test-App-Lauf beim Aufbau des Branch-Refs (`40725681290`, Zwischenstand `6ed31a2`) wurde durch die unmittelbare Veröffentlichung des endgültigen Test-App-Heads abgebrochen. Dieser Lauf wird nicht als Abnahme verwendet. Maßgeblich sind ausschließlich die beiden oben verlinkten endgültigen Läufe.

Die vorangegangenen ergänzenden lokalen Läufe mit 137 eindeutigen SDK35-Methoden, 3 Produkt-Pythonprüfungen und 32 Test-App-Pythonwächtern bleiben als zusätzliche Diagnose erhalten. Ihre Infrastruktur- und Fixture-Zwischenfehler sind im ursprünglichen Lokalbericht und den unveränderten Evidence-Dateien nachvollziehbar. Die vollständige CI ersetzt deren damaligen offenen Gradle-Status.

## 6. Backup, Restore, Originale und Fachregressionen

In beiden vollständigen Verify-Läufen bestätigen die 36 tatsächlich ausgeführten bisherigen Drive-Reparaturmethoden den strengen simulierten HTTP-Transport, vollständiges Backup/Restore sowie zweimaliges MERGE ohne Dubletten. Beide `repair-summary.json` melden 60/60 SHA256-identische Originale und jeweils 0 Restore-/MERGE-Fehler. Die ursprünglichen Fachcontracts, IDs und Zuordnungen blieben erhalten. Das ist eine automatisierte Prüfung mit synthetischen Daten, kein echter Google-Drive-Transfer.

Das Portfolio umfasst 5 Immobilien, 17 Einheiten/Mietverhältnisse, 70 Belege/Dokumente, 120 Bankbuchungen, 10 Fahrten, 5 AfA-Datensätze und 60 Originaldateien. Zusätzlich wird das vorhandene 21-Einheiten-Stressportfolio geprüft. Bestehende Prüfungen decken Dokumentzuordnung, Bank, Fahrtenbuch einschließlich dateiloser Fahrtbelege, AfA, DATEV, Navigation und ViewModels ab.

Technische RAW-Differenzen etwa bei lokalen Dateipfaden, Drive-Revisionen und Sync-Zeitpunkten bleiben sichtbar und werden nicht als Bytegleichheit ausgegeben. Die fachlichen Contracts und IDs werden streng geprüft. Room-, Supplemental-Backup-, Preference-Allowlist- und Namespace-Policy-Quellen sind gegenüber der Base unverändert. Keine destruktive Migration, Bereinigung oder automatische Zuordnungsänderung wurde eingeführt.

## 7. Nachweis der App- und Drive-Trennung

| Eigenschaft | Produkt | Isolierte Test-App |
| --- | --- | --- |
| Package-ID | `com.aistudio.steuerassistent.vnhqkp` | `com.aistudio.steuerassistent.vnhqkp.phase11a` |
| Drive-Root | `Steuerassistent Belege/_BelegApp-Daten` | `ImmoPilot Phase11A Testdaten/_BelegApp-Daten` |
| UID im aktuellen Isolations-Emulator | 10209 | 10211 |
| Privater Datenbereich | `/data/user/0/com.aistudio.steuerassistent.vnhqkp` | `/data/user/0/com.aistudio.steuerassistent.vnhqkp.phase11a` |

Der native Test-App-Lauf bestätigt getrennte Room-/Preferences-/Dateibereiche, verweigerte Zugriffe auf produktive Privatdateien und Provider sowie unveränderte synthetische Produktreferenzen nach allen E2Es. Die Prozessstopp-/Launcher-Neustart-Prüfungen und Seed-Werkzeuge bleiben erhalten. Es handelte sich um neu angelegte synthetische CI-Profile; das ist kein Test auf echten Nutzerdaten.

Die gemeinsame Fachlogik ist per bestehendem exaktem Quellwächter an den veröffentlichten Produktfix `6ed31a2` gebunden. Unterschiede unter Main beschränken sich auf dieselben bisherigen QA-UI-Hooks in zwei UI-Dateien; deren Inhaltszeilen wurden unabhängig mit der früheren Test-App verglichen. Der unveränderte Fixture-Blob ist `412050d0a6b5420e7ad8f37057cdcbb84fd96299`; sein SHA256 ist `b052a1ae4569196abb791375aa1fa01fe6a636a1d0082820acb576fa497ea638`. Die Test-App enthält die bestehenden Werkzeuge und Originalfixture; die Release-Packaging-Prüfung schließt QA-Seed und QA-Oberflächen aus dem Produkt-Release aus.

## 8. Tatsächliche APK-Artefakte und Signaturen

| Artefakt | Tatsächliche Identität / SHA256 |
| --- | --- |
| Produkt-Debug | `com.aistudio.steuerassistent.vnhqkp` · Version 1/1.0 · min24/target36/compile36 |
| Produkt-APK SHA256 | `a2f650ce410b5663137d2f396f07af1164ac3659e33a2548768eec152131ec07` |
| Produkt-Zertifikat SHA1 | `a7f52efd6607b6a9313afbba63135c092d2567af` |
| Produkt-Zertifikat SHA256 | `7bf0a3f61403c2f9977463e8a137ee7b08f2ccc4d4663a1c7ef9914b720c7184` |
| Isolierte ImmoPilot-Test-App | `com.aistudio.steuerassistent.vnhqkp.phase11a` · Version 1/1.0 · min24/target36/compile36 |
| Test-APK SHA256 | `48d471e4e29f8dd252755fdc8d7db9bbc65131c092f4f468d39fa40c625779ae` |
| Test-Zertifikat SHA1 | `3b44c4e81a3d23986e2704dd1eb5c3cd14fdbbeb` |
| Test-Zertifikat SHA256 | `685dd607bba01c90c6030e5af55c1b4b1c40937515f881c9405b2f185123078c` |

Beide APKs haben eine unabhängig geprüfte gültige v2-Signatur, jeweils einen RSA2048-Debug-Signer. Produkt-Debug-APK: 34.344.813 Bytes. Test-APK: 34.482.487 Bytes.

Produkt-Download: https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/40733042865/artifacts/12838255420

Test-App-Download: https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/40725684070/artifacts/12835065956

Die GitHub-Downloads sind ZIP-Archive mit der jeweiligen APK. Die oben genannten SHA256 beziehen sich auf die ausgepackten APK-Bytes. Die APK-Artefakte haben laut GitHub Ablaufdatum 26.01.2027; Emulator-Rohartefakte 11.11.2026. Relevante XMLs und JSONs bleiben zusätzlich im ergänzenden Evidence-Archiv erhalten.

Die APKs stammen jeweils aus dem Verify-Job der betreffenden Variante. APKs des Emulatorjobs beziehungsweise die synthetische Produktreferenz aus der Test-App-CI sind andere Artefakte und werden nicht als ausgelieferte Produkt-Debug-APK ausgegeben. Die APK-Bytes wurden unabhängig auf Package, SHA256 und gültige Signatur geprüft. Die Signaturprüfungen verwenden die tatsächlichen APKs; aus einem Berichtsfeld allein wurde keine Zertifikatidentität abgeleitet.

**Updategrenze:** Die neue Test-APK hat Zertifikat-SHA1 `3b44c4e81a3d23986e2704dd1eb5c3cd14fdbbeb`. Das unterscheidet sich vom bekannten früheren Test-Zertifikat `58ef03a59a1c78807b3376041bf5fd8488eb9c4c`. Welches Zertifikat tatsächlich auf dem POCO installiert ist, wurde nicht ausgelesen. Ein datenbewahrendes Update über diese Installation ist daher nicht freigegeben. Ohne passenden bisherigen privaten Signierschlüssel darf keine Updatefähigkeit behauptet werden. Keine Deinstallation als Ausweg; die vorhandenen lokalen Daten bleiben unangetastet.

Die Produkt-Debug-APK ist ein separates Testartefakt für ein leeres eigenes Testgerät/-Profil. Sie darf keine bestehende produktive Installation ersetzen. Es wurde keine APK durch diesen Auftrag auf einem Nutzergerät installiert.

## 9. Noch offene echte Google-/Android16-Abnahme

**OFFEN:** OAuth-Konfiguration für die jeweilige Package-ID und tatsächliche neue Zertifikat-SHA1; Drive-API-Aktivierung; Consent-/Testnutzer-/Scope-Konfiguration; Übereinstimmung der nativen und bisherigen Web-OAuth-Projekte beziehungsweise Zugriff unter `drive.file`; echter Neustart mit Google-Konto; Tokenablauf und Erneuerung; kurzzeitiger Netzausfall; Anlage/Auffinden von `_BelegApp-Daten` im richtigen echten Test-Root; POCO X8 Pro mit Android16.

Es fand kein erfolgreicher echter Google-Login oder Cloudtransfer in dieser Abnahme statt. Die HTTP-/Play-Services-Simulationen und der native Android15-Emulator beweisen die Codeverträge, aber nicht die konkrete Ursache des ursprünglichen Gerätefehlers oder eine funktionsfähige Google-Console-Konfiguration. Kein Scope wurde erweitert. Die vorhandenen, inzwischen als veraltet markierten GoogleSignIn-/GoogleAuthUtil-APIs wurden gezielt weiterverwendet; eine neue Auth-Architektur wurde nicht eingeführt. Der begrenzte Web-Flow wird nicht als dauerhafte Refresh-Lösung ausgegeben.

## 10. Reproduzierbarkeit und verbleibende Freigabe

Geliefert werden PR #149, beide getrennten APK-Artefakte, dieser PDF-/Markdown-Bericht und `ImmoPilot-Phase11C-Abnahme-Evidence.zip`. Das Archiv enthält die tatsächlichen JVM-/Emulator-XMLs, Verbindungs-/Backup-/Isolationsnachweise, APK-Signaturausgaben, CI-Metadaten, ein SHA256-Manifest sowie `verify-evidence.py`. Nach dem Entpacken kann `python3 verify-evidence.py` die Archivhashes, eindeutigen tatsächlichen Testmethoden, SDK36-Ausführung und 60 Originalhashes erneut prüfen. Für einen vollständigen Neuaufbau sind die bestehenden Workflows mit Gradle9.3.1/Java21 für JVM und Java17 für Android zu verwenden, einschließlich `lintDebug`, `testDebugUnitTest -Proborazzi.test.record=true`, `assembleDebug` und dem unveränderten Emulator-Skript aus `.github/workflows/android-ci.yml`. Die vollständigen Quellstände sind über die angegebenen SHAs reproduzierbar.

Zur Reproduktion dienen die CI-Workflows an den genannten unveränderlichen Commit-Heads, die vollständigen XMLs, gebundene SDK36-Ausführungsnachweise, Integritätsberichte und das Hashmanifest des Evidence-Archivs. Der ursprüngliche Lokalbericht bleibt als historische Dokumentation der roten Regressionen und der damaligen Sandbox-Einschränkungen erhalten; seine damaligen offenen Veröffentlichungs-/CI-Felder werden durch diesen Bericht aktualisiert.

Der nachgewiesene falsche Loginverlust ist im gemeinsamen Code korrigiert und automatisiert geprüft. Die vollständige automatisierte Abnahme beider Varianten ist abgeschlossen. PR #149 bleibt Draft, bis echte Google-/Android16-Abnahme und sichere Signierung für ein Update der vorhandenen Testinstallation geklärt sind. Kein automatischer Merge und keine praktische Gerätefreigabe.
