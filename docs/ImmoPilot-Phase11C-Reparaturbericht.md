# ImmoPilot Phase 11C – Drive-Verbindungsstatus

Stand: 27.10.2026 · Issue #148 · Priorität P1

**Reparatur lokal implementiert und ergänzend geprüft. Automatische Gesamt-Abnahme, Veröffentlichung und praktische Gerätefreigabe sind OFFEN. Dieser Bericht ist keine Freigabe einer APK.**

## 1. Quellstände und Vorbereitung

| Gegenstand | Stand |
| --- | --- |
| Tatsächlicher GitHub-main / Base | `6f8f26f4055c21a0df994e52b970f3fecbf4889d` |
| GitHub-main-Tree | `b7e6f19e72d3ca1e8d3ba4d4ca747b4d07e75077` |
| Regressionen vor Produktänderung | `aa137a5` |
| Produkt-Reparatur-SHA | `2b58e6aaab913acb270695512b7fe4b4cbef1cf4` |
| Reparaturbranch | `fix/phase11c-drive-connection` |
| Bisherige isolierte Test-App | `agent/phase11b-isolated-drive`, `f56d0c164262b1af2bd65936cf769b7aef3b0143` |
| Lokale Test-App-Integration | `agent/phase11c-isolated-drive`, `9afb546fea289012bbb43d503002e6748e482687` |

Der anfängliche Arbeitsbranch hieß `work`; der Arbeitsbaum war sauber. `git fetch origin` wurde vor Änderungen mehrfach versucht. Die Sandbox konnte den Umgebungsproxy nicht erreichen: `Failed to connect to proxy port 8080`. Die Anfragen mit zusätzlichen Rechten lieferten vor den Gesprächsunterbrechungen keinen abgeschlossenen Fetch. Der lokale Remote-Tracking-Ref `origin/main` blieb deshalb auf dem veralteten `234bce3ed2fb654e78b020e0485fa616d3572d86`.

Der tatsächliche main wurde ersatzweise über die verbundene GitHub-Schnittstelle geprüft. SHA und Tree stimmten exakt mit dem vorhandenen lokalen HEAD überein. Eine erneute GitHub-Prüfung vor Berichterstellung bestätigte denselben main. Der Reparaturbranch baut auf diesem aktuellen Stand auf; ein erfolgreicher Git-Fetch wird ausdrücklich nicht behauptet.

Gelesen wurden `AGENTS.md`, `PROJECT_STATUS.md`, `CODE_MAP.md`, `VERIFY_CHANGES.md`, die betroffenen Auth-/Drive-/ViewModel-Pfade und die bestehenden Fehler-, Sicherheits- und Portfoliotests. PR #144, PR #146 und Issue #148 wurden über GitHub ausgewertet. main enthält beide Reparaturen sowie PR #147.

Referenzen: https://github.com/xedox737/Steuer-Asistend-APP/issues/148 · https://github.com/xedox737/Steuer-Asistend-APP/pull/144 · https://github.com/xedox737/Steuer-Asistend-APP/pull/146

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

## 5. Tatsächlich abgeschlossene lokale Prüfung

**137 eindeutige JUnit-Methoden in 19 Klassen, 0 Fehler, 0 Skips im letzten jeweiligen ergänzenden Lauf.** Enthalten sind 23 neue Verbindungsregressionen und alle 36 bisherigen Reparaturmethoden. Jede Methode ist einzeln in XML dokumentiert; es werden keine Gradle-Klassenplatzhalter gezählt.

| Bereich | Ergebnis |
| --- | --- |
| Neue Auth-/Init-/Sync-Regressionen | 23/23; A–E und G sowie Kontowechsel, Tokenfristen, späte Tokenablehnung, erneuerbares natives Token, Disconnect und Neustart |
| Namespace-/Sicherheitswächter | 14/14; fremde IDs, Konfigurationen, Inhalte, Mutationen und Restore bleiben gesperrt |
| Bisherige Reparaturmethoden insgesamt | 36/36 einschließlich obiger Sicherheitswächter |
| Portfolio, Backup/Restore und Originalmetadaten | Bestanden; 17- und 21-Einheiten-Portfolio, vollständiger Restore und zweimal MERGE |
| Navigation, Settings, Bank-/Beleg-/Einheiten-ViewModels, AfA | Die ausgewählten vorhandenen Methoden bestanden; vollständige Suite bleibt offen |
| Produkt-Pythonprüfungen | 3/3 |
| Vorhandene Test-App-Pythonwächter | 32/32 |
| Kotlin-/Compose-Kompilierung der sechs geänderten Produktquellen | Bestanden über direkten Compiler mit den vorhandenen Abhängigkeiten |
| `git diff --check` | Bestanden in beiden Arbeitsbäumen |

**Geltungsbereich:** Diese ergänzenden Läufe verwenden SDK35/Android15 im Robolectric-JVM-Prozess, einen simulierten HTTP-Transport und einen Play-Services-Shadow. Nur eine temporäre Kopie der neuen SDK36-Testklasse wurde für diesen gesonderten Diagnosepfad auf SDK35 gestellt. Die eingecheckte Klasse bleibt auf SDK36; kein regulärer Test wurde deaktiviert oder seine Assertion abgeschwächt. Die zusätzlichen Diagnosewerkzeuge und ihre Voraussetzungen liegen im Evidence-Verzeichnis. Sie ersetzen weder Gradle, CI, Instrumentation noch Android16.

Zwischenläufe bleiben sichtbar: Der zusätzliche Neustartfall scheiterte zunächst wegen der Firestore-Statuskollision und wurde durch Produktcode korrigiert. Ein Uploadtest erreichte zunächst den Upload nicht, weil der bestehende Leerer-Bestand-Restore-Schutz korrekt abbrach; der Test erhielt einen echten lokalen Beleg. Der direkte Prüfpfad benötigte außerdem den richtigen Kotlin-Modulnamen `app`, die vorhandenen Java-Testklassen und `--add-opens=java.base/java.io=ALL-UNNAMED`. Diese Infrastrukturkorrekturen änderten keine Produktassertion. Frühere XMLs/Logs stehen in `direct-sdk35-attempt1` und `direct-sdk35-attempt2`. Einzelne Robolectric-CloseGuard-Warnungen sind in den Rohlogs erhalten.

## 6. Integritätsnachweis

Der bestehende echte Client-/Repository-Pfad gegen den simulierten HTTP-Drive wurde an Reparatur-SHA `2b58e6aaab913acb270695512b7fe4b4cbef1cf4` gebunden erneut ausgeführt. Der unveränderte strenge Integritätsprüfer vergleicht den tatsächlichen gesicherten Bestand mit dem Ergebnis aus frischem Full-Restore und zwei MERGEs.

Ergebnis: **60/60 Originaldateien mit übereinstimmendem SHA256, 0 fachliche Contract-Abweichungen, 0 Relationsfehler, 0 Dubletten, 0 fehlende oder unerwartete IDs.** Der Bestand umfasst 5 Immobilien, 17 Einheiten/Mietverhältnisse, 70 Belege und Dokumente, 120 Bankbuchungen, 10 Fahrten und 5 AfA-Datensätze. DATEV und zusätzlich das 21-Einheiten-Stressportfolio wurden in den vorhandenen Prüfungen ausgeführt.

Technische RAW-Differenzen, etwa lokale Dateipfade, Drive-Revisionen und Sync-Zeitpunkte, werden nicht als Bytegleichheit ausgegeben. Sie sind vollständig im strengen Vergleich enthalten. Nachweise: `strict-portfolio-integrity.json`, die vier unbearbeiteten Snapshots unter `portfolio/` und das XML/Log von `ReceiptSyncPreservationE2ETest`.

Die Quellen für Room, Supplemental-Backupformat, Preference-Export-Allowlist, DriveEnvironment und DriveNamespacePolicy sind gegenüber der Base bytegleich. Der unabhängige SHA256-Nachweis liegt in `unchanged-data-and-namespace-sources.json`. Keine Migration, Bereinigung, ID-Änderung oder Datenlöschung wurde eingeführt. Keine echte Produktinstallation oder Google-Drive-Datei wurde angefasst.

## 7. Trennung und Integration der beiden Apps

| Eigenschaft | Produkt | Isolierte Test-App |
| --- | --- | --- |
| Package-ID | `com.aistudio.steuerassistent.vnhqkp` | `com.aistudio.steuerassistent.vnhqkp.phase11a` |
| Drive-Root | `Steuerassistent Belege/_BelegApp-Daten` | `ImmoPilot Phase11A Testdaten/_BelegApp-Daten` |
| Lokale Sandbox | Android-Package des Produkts | Eigenes Android-Package |
| Phase11A-Werkzeuge und Seed | Keine Testwerkzeuge im Produkt | Bestehende Werkzeuge und Originalfixture übernommen |

Die bestehende Test-App wurde über ihren geprüften bisherigen Overlay-Diff übernommen, statt neu entwickelt zu werden. Die Originalfixture ist bytegleich; Git-Blob `412050d0a6b5420e7ad8f37057cdcbb84fd96299`. Die gemeinsame Fachlogik entspricht dem reparierten Produktstand. Zulässig bleiben ausschließlich die bisherigen QA-UI-Hooks in zwei UI-Dateien. Deren hinzugefügte/entfernte Zeilen wurden unabhängig mit dem bisherigen Overlay verglichen; nur Positionen und Blob-Indizes änderten sich. Der bestehende exakte Quellparitätswächter bleibt aktiv und ist auf den neuen Produkt-SHA gepinnt.

Dies ist ein **Quell-/Wächternachweis**. Neue APK-Manifestprüfungen, UID-/Sandbox-Emulatorbeobachtungen und Test-App-JVM-/Instrumentation-Abnahme sind noch offen und werden nicht durch frühere Phase11B-Ergebnisse ersetzt.

## 8. Noch offene reguläre Abnahme und Auslieferung

Die vollständige JVM-Suite sowie `lintDebug`, `assembleDebug` und `assembleDebugAndroidTest` wurden regulär angestoßen. Gradle scheiterte vor Taskausführung an `FileLockContentionHandler: Could not determine a usable wildcard IP for this machine`. `/dev/kvm` fehlt. Es gibt daher keinen neuen nativen Emulatorlauf und keine neuen geprüften APKs.

| Verpflichtender Lieferpunkt | Status |
| --- | --- |
| Reparatur-PR für #148 | OFFEN; lokale Branches/Commits und PR-Text vorbereitet, nicht veröffentlicht |
| Vollständige JVM-Suite / SDK36 | OFFEN; regulärer Gradle-Start blockiert |
| Lint | OFFEN; regulärer Gradle-Start blockiert |
| Native Android-Emulatortests beider Apps | OFFEN; keine lokale KVM-Umgebung, kein neuer CI-Lauf |
| Produkt-Debug-APK | OFFEN; kein neuer erfolgreicher Build, keine neue APK-Signaturprüfung |
| Isolierte ImmoPilot-Test-APK | OFFEN; kein neuer erfolgreicher Build, keine neue APK-Signaturprüfung |
| Neue CI-Run-IDs und Artefaktlinks | OFFEN; keine neuen Läufe veröffentlicht |
| PDF-Prüfbericht und reproduzierbare lokale Evidence | Vorhanden; mit diesen ausdrücklichen Prüfgrenzen |

Keine alten APKs oder historischen CI-Links werden als neue Phase11C-Artefakte ausgegeben. Kein automatischer Merge und keine Installation/Deinstallation wurden vorgenommen.

Für die reguläre Fortsetzung: Produktbranch und Test-App-Branch veröffentlichen, den vorbereiteten Draft-PR erstellen, vollständige `verify`-/`android-emulator-e2e`-Jobs beider Varianten abwarten, jeden Fehler ursächlich auswerten und anschließend die tatsächlichen Verify-APKs unabhängig prüfen. Die neue CI-Ausführungsprüfung verlangt jede der 23 SDK36-Regressionsmethoden ohne Fehler/Skips. Alle bisherigen Sicherheits-, Ausführungs-, Portfolio- und Isolationswächter bleiben erhalten.

## 9. Echte Google-/Android16-Prüfung und Signatur-Risiko

**Alle folgenden Punkte sind OFFEN:** OAuth-Client für Test-Package und tatsächliches APK-Zertifikat; SHA1 des neuen bereitgestellten APK-Zertifikats; Drive-API-Aktivierung; Consent-/Testnutzer-Konfiguration; tatsächlich gewährter `drive.file`-Scope; erfolgreicher echter Neustart; kurzzeitiger Netzausfall; Tokenablauf/Erneuerung; Anlage von `_BelegApp-Daten` im richtigen echten Test-Root; POCO X8 Pro unter Android16.

Aus Issue #148 ist die frühere Test-Zertifikat-SHA1 `58ef03a59a1c78807b3376041bf5fd8488eb9c4c` bekannt. Sie ist **kein Nachweis für eine neue Phase11C-APK**. Der bisherige CI-Workflow erzeugt flüchtige Debug-Keystores. Ohne den bisherigen privaten Signierschlüssel ist ein Update über die installierte Test-App mit Erhalt ihrer lokalen Daten nicht belegt; ein neues Zertifikat kann zugleich eine neue Android-OAuth-Clientregistrierung benötigen. Ein öffentliches Zertifikat oder dessen SHA1 ersetzt den privaten Schlüssel nicht. Dieser Bericht empfiehlt keine Deinstallation als Ausweg.

Ein neuer Produkt-Debug-Build ist ausschließlich ein separates Testartefakt; er darf keine bestehende Produktinstallation ersetzen. Beide tatsächlichen APKs müssen vor Bereitstellung auf Package, Manifest/Authorities, SHA256 und Zertifikat-SHA1/SHA256 geprüft werden. Für die Test-App muss zusätzlich der Updatepfad mit passender Signatur geklärt sein.

Die nativen Play-Services-/GoogleSignIn-APIs wurden gezielt weiterverwendet; sie sind inzwischen als veraltet markiert. Eine Architektur-Migration ist kein Bestandteil dieser Reparatur. Die echte Android16-/OAuth-Abnahme bleibt deshalb zwingend. Der temporäre Web-Flow wird nicht als dauerhafte Refresh-Lösung dargestellt.

## 10. Evidence und Reproduktion

`phase11c-evidence/local-verification-summary.json` enthält sämtliche 137 Methoden und die SHA256-Werte der geänderten Quellen. JUnit-XMLs und Logs, rote Vorher-Läufe, Infrastrukturfehler, Quellparität, Originalsnapshots und Hilfswerkzeuge sind beigefügt. Das ZIP und sein Dateimanifest dienen der unveränderten Weitergabe.

Reguläre Prüfung laut `VERIFY_CHANGES.md`: mit Java21 `gradle testDebugUnitTest -Proborazzi.test.record=true --no-daemon`, anschließend die bestehenden Ausführungs-/Integritätswächter einschließlich `phase11c-verify-connection.py`; mit Java17 `gradle lintDebug assembleDebug assembleDebugAndroidTest --no-daemon`. Danach beide CI-Emulatorjobs. Der ergänzende SDK35-Direktlauf ist ausdrücklich kein Ersatz.

Die lokale Arbeit ist reviewbar. **Die vom Auftrag verlangte vollständige automatische Abnahme und Lieferung mit PR und zwei APKs ist noch nicht erreicht.**
