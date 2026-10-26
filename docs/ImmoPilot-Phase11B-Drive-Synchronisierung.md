# ImmoPilot – Phase 11B: Drive-Synchronisierung

Abschlussbericht zum P1-Issue #145 · 26.10.2026 · ausschließlich synthetische Prüfbestände.

## Ergebnis und Freigabegrenze

Der reproduzierte Initialisierungsfehler ist im gemeinsamen Produktcode behoben und automatisiert abgesichert. Die bestehende isolierte Test-App basiert vorläufig auf genau diesem reparierten Code. Vollständige Android-CI einschließlich Emulator wurde für beide Varianten abgewartet. Es wurden keine produktiven Nutzerdaten oder echten Google-Konten für die Tests verwendet und keine Pull Requests gemergt.

Automatisierter Status: AUTOMATISIERT BESTANDEN. Echter Google-/OAuth-/Gerätenachtest: NOCH OFFEN. Eine praktische Behebung auf dem Nutzergerät wird hier ausdrücklich nicht behauptet. Entscheidung: KEINE PRAKTISCHE PORTFOLIOFREIGABE. [Reparatur-PR #146](https://github.com/xedox737/Steuer-Asistend-APP/pull/146) bleibt Draft zur menschlichen Prüfung.

## Verbindliche Ausgangsbasis

Aktuell abgerufener origin/main vor Reparatur: 6d58dbac9d9f6d3c1c57c9b14579d5360751879d. PR #144 war bereits durch den Repository-Betreiber gemergt; die dort eingeführte Sicherheitsgrenze ist Grundlage dieses Fixes. Produktbranch: fix/phase11b-drive-initialization. Testbranch: agent/phase11b-isolated-drive. Die Integration verwendet die vorhandenen Testwerkzeuge und Fixture aus dem bestehenden Phase11A-Stand; keine Neuerstellung und keine zweite Fachimplementierung.

Gerätebefund laut Nutzer: POCO X8 Pro, Android 16, „die letzte Build-Version“. Exakter installierter APK-Hash/Commit unbekannt. Beide Apps zeigen verbundenen Drive, aber keinen eingerichteten Systemordner; der Test-Hauptordner besteht leer. Kein eigener physischer Zugriff auf dieses Gerät. Eine Deinstallation beweist nicht, ob Android alte Preferences wiederhergestellt hat.

| Variante | endgültiger Code-Head | vollständiger CI-Lauf | JVM-Methoden | eindeutige Emulatormethoden |
| --- | --- | --- | --- | --- |
| Produkt | b687bccbaab2d9d6cc40f748d88090c3690e5a80 | [CI 40364725921](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/40364725921) | 1156 | 70 |
| Test | f56d0c164262b1af2bd65936cf769b7aef3b0143 | [CI 40370749483](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/40370749483) | 1183 | 73 |

Der Produkt-PR-Checkout ist 7297595d03969d3bdaa8d234f471b43a839c66fd; die synthetische GitHub-PR-Checkoutkennung ist kein tatsächlich ausgeführter Merge. Test-Checkout: f56d0c164262b1af2bd65936cf769b7aef3b0143. Die Zuordnung von Quellständen, tatsächlichen Tests und CI-Artefakten liegt zusätzlich in den JSON-Zusammenfassungen im Evidence-Archiv.

## Reproduktion und bestätigte technische Ursachen

Vor jeglicher Produktänderung wurden vier Regressionen gegen den unveränderten Ausgangscode ausgeführt: drei Fehler, ein erfolgreicher Kontrollfall, keine Errors/Skips. Commit 9abe9d6 sichert diesen Test-vor-Fix-Nachweis einschließlich docs/phase11b-evidence/initial-red-regression.xml.

- Frisch angelegter eigener Root, aber HTTP404 für files.get(root): exakt die gemeldete Meldung „Drive-Sicherheitsabbruch: Gespeicherte Ordner-ID ist für diese App nicht zulässig.“
- Bereits vorhandener leerer eigener Root, allgemeine Root-Metadaten nicht erreichbar: derselbe Abbruch, Systemordner fehlt.
- Ordnersuche HTTP403: Initialisierung meldete fälschlich Erfolg und erzeugte sieben Ersatzordner.
- Erreichbare allgemeine Root-Metadaten: erfolgreicher Kontrollfall.

DriveNamespacePolicy.owned verlangte zusätzlich Metadaten des allgemeinen MyDrive-Root. Der angeforderte Scope drive.file darf jedoch eigene sichtbare App-Dateien erreichbar machen, ohne allgemeinen Root-Metadatenzugriff vorauszusetzen. Der simulierte eingeschränkte Sichtbarkeitszustand reproduziert exakt den Gerätebefund; der tatsächliche HTTP-Grund auf dem POCO bleibt ohne Live-Diagnose offen.

Die Bezeichnung „gespeicherte ID“ beweist keine Alt-Preference: getOrCreateFolder legt bereits den gerade gefundenen/erzeugten Root im Memory-Cache ab. Die nächste Suche nach _BelegApp-Daten innerhalb desselben initializeDriveStorage-Aufrufs prüft ihn erneut. Daher ist ein leerer eigener Root auch ohne alte lokale Daten reproduzierbar.

Zusätzlich verschluckten Ordnersuche und nullable Metadatenzugriffe Fehler: ungültige ID, falscher MIME-Type, fehlende Sichtbarkeit, HTTP- und Netzwerkfehler waren nicht zuverlässig unterscheidbar. Ein fehlgeschlagener Suchaufruf konnte als leerer Bestand zur Ersatzanlage führen.

## Gezielte Korrektur und erhaltene Sicherheitsarchitektur

GoogleDriveClient.getOrCreateFolder, getFileMetadataChecked und findAppDataFolder sowie DriveNamespacePolicy.owned/topLevelRoot und DrivePersistenceRepository.initializeDriveStorage bleiben die bestehenden gemeinsamen Komponenten. Keine neue Cloudschicht und keine neue Backupimplementierung.

Der Root-Nachweis erfolgt über eine erfolgreiche serverseitig gefilterte Suche nach dem exakten aktiven Rootnamen, Ordner-MIME, trashed=false und 'root' in parents. Die aktuelle Root-ID muss tatsächlich im Ergebnis enthalten sein. Ein bloßer Name oder eine leere Antwort genügt nie als Zugehörigkeitsnachweis.

Weiterhin geprüft werden MIME, App-Kennung, eindeutige Elternkette, Shortcuts, verschachtelte Imitationsroots, Configrollen, JSON-Verweise, geschützte Container und die geschützten Lese-/Schreib-/Verschiebe-/Löschzugriffe. Es gibt keinen Fallback zum anderen Root und keinen kontoweiten OAuth-Scope. Stammdaten-/Config-Suchen behandeln API-Fehler strikt.

Suchen sind vollständig paginiert und begrenzt. HTTP-/Netzwerk-/JSON-Fehler und incompleteSearch erlauben keine Ersatzanlage. Erfolgreiche leere FileList-Projektionen dürfen ein leeres files-Feld auslassen; error-Objekte, falsche Typen oder fremde Metadatenobjekte werden weiterhin abgewiesen.

Neue, gefundene und gecachte IDs werden vor Verwendung verifiziert. Cacheidentität bindet Token, aktiven Root, Eltern und Schlüsselart name#/path#. Der bisherige gemeinsame logische Mutex bleibt erhalten. Ein erfolgreicher Root-Nachweis wird ausschließlich innerhalb einer einzelnen JSON-Validierung wiederverwendet; es gibt keinen dauerhaften positiven Ownership-Cache. Ein extern verschobener Verweis wird bei der nächsten Operation erneut geprüft und abgewiesen.

DriveAccessFailure unterscheidet HTTP_401/403/404/429/500, NETWORK, INVALID_ID, INVALID_METADATA, WRONG_MIME, WRONG_LOCATION, NAMESPACE und INCOMPLETE_SEARCH. Meldungen enthalten weder Token noch Konto, private IDs oder Antwortbodys. HTTP404 bleibt mehrdeutig und löst keinen Mappingreset aus. Ausschließlich nachweislich ungültige einzelne eigene Test-Mappings dürfen neu initialisiert werden; Produkt-Mappings sowie fremde oder unklar erreichbare IDs bleiben unangetastet.

Keine automatische Löschung, Verschiebung, Umbenennung, Bereinigung oder Datenmigration. Keine Room-Migration. Keine Änderung der Bank-, DATEV-, Vermietungs-, AfA-, Fahrtenbuch- oder globalen Navigationslogik. Bestehende gültige Dateien und Backups behalten ihre IDs und Bytes in den simulierten Bestandsprüfungen.

| Umgebung | Application-ID | unveränderter Drivebereich |
| --- | --- | --- |
| Produkt | com.aistudio.steuerassistent.vnhqkp | Steuerassistent Belege / _BelegApp-Daten |
| Test | com.aistudio.steuerassistent.vnhqkp.phase11a | ImmoPilot Phase11A Testdaten / _BelegApp-Daten |

Der Quellenwächter belegt identische app/src/main-Quellen abgesehen von exakt den bestehenden zwei QA-UI-Hooks (AppSettingsScreen.kt und ReceiptAppUi.kt). Die originale gebündelte Testfixture hat SHA256 b052a1ae4569196abb791375aa1fa01fe6a636a1d0082820acb576fa497ea638. Test-Seed-Guard, leere Installation als Voraussetzung und bestehende lokale Sandbox bleiben erhalten.

## Endgültige Tests und Datenintegrität

Beide CI-Läufe sind vollständig erfolgreich. Verify, Lint, tatsächlicher Testausführungswächter, Unit/Robolectric, APK-Builds und Emulator-E2E sind bestanden. Keine bestehenden Tests entfernt, deaktiviert oder übersprungen; keine Assertions abgeschwächt oder Zeitlimits pauschal erhöht. Bedingte Schritte für nicht vorhandene Release-Signiergeheimnisse sind keine übersprungenen Tests.

Die finale lokale Test-App-Prüfung führte 1183 eindeutige JVM-Methoden ohne Fehler/Skips aus (29m12s); sämtliche Integritäts-/DATEV-/Portfolio-Wächter bestanden. LintDebug, assembleDebug, assembleDebugAndroidTest und unsigned assembleRelease bestanden anschließend (13m44s, 0 Lintfehler/Fatal, 120 Warnungen). Der tatsächliche Release-APK-Check bestätigt Produkt-Package/Label, keine Seed-UI/-Assets und den gemeinsamen Sicherheitsguard. Dieses unsigned Release ist kein installierbares Produkt-Update. Zusätzlich bestanden lokal die drei öffentlichen Zertifikat-Parserprüfungen der Produktvariante und die 32 vorhandenen Python-QA-Prüfungen der Testvariante; die entsprechenden CI-Schritte wurden ebenfalls abgewartet. Je Variante liefen 17 neue Initialisierungsfälle ausdrücklich unter SDK36/Android16 und 36 bestehende gezielte Reparaturmethoden. Sie decken frische und vorhandene eigene Roots, gültige/ungültige Cache- und Mapping-IDs, Konto-/Tokenwechsel, HTTP401/403/404/429/500, Netzwerkunterbrechung, falschen MIME, unvollständige/malformed Metadaten, Suchfehler, partielle Systemanlage und sichere Wiederaufnahme ab. Die vorhandenen Regressionen für Room, Beziehungen, Immobilien/Einheiten/Mietverhältnisse, Bank, Dokumente/Belege, Fahrtenbuch, AfA, DATEV, Backup/Restore und Navigation wurden vollständig ausgeführt.

| Nachweis | Produkt | Test |
| --- | --- | --- |
| Initialisierung unter SDK36 | 17 erfolgreich | 17 erfolgreich |
| Original-SHA256 nach Full-Restore und zweimal MERGE | 60/60 | 60/60 |
| fehlende / unerwartete IDs | 0 / 0 | 0 / 0 |
| Relations-/Dublettenfehler | 0 / 0 | 0 / 0 |
| fachliche Contract-Abweichungen | 0 | 0 |
| normales / Stressportfolio | 17 / 21 Einheiten | 17 / 21 Einheiten |
| tatsächlicher Google-Transport / Geräte-OAuth | NOCH OFFEN | NOCH OFFEN |

Das realistische Portfolio umfasst 5 Immobilien, 17 Einheiten und Mietverhältnisse, 70 Belege/Dokumente, 120 Bankbuchungen, 10 Fahrten und 60 physische Originaldateien sowie Darlehen, AfA, Sanierung und Zuordnungen. Wiederholter Sync, zwei Backups, frischer vollständiger Restore und zweimal MERGE erhalten die fachlichen IDs, Referenzen und Originalhashes. Die Test-Namespace-Prüfung belegt zwei Backup-Transporte, zwei MERGEs, null zusätzliche Root-Dubletten, null Orphans und identische Produkt-Inventare vor/nach dem Test. Sämtlicher Cloudtransport war IN_MEMORY_HTTP, kein echtes Google Drive.

Technische Rohdifferenzen werden im Evidence-JSON vollständig erhalten. Im Produktvergleich sind es 8 RAW-, 260 Room-RAW- und 120 sekundäre RAW-Abweichungen; fachlicher Contract und Beziehungen bleiben unverändert. Der unveränderte strikte Checker wird verwendet; keine neue Ignore-/Normalisierungsregel für diese Reparatur.

Der automatische und manuelle Sicherungspfad verwenden beide die reparierte Initialisierung und denselben Receipt-/Dokumenttransport. Die simulierten Repository-/Portfoliotests sichern diese gemeinsamen Funktionen ab. Ein echter UI-Test mit Google-Anmeldung, Auto-Backup-Schalter und realem Hintergrund-/Geräteverhalten bleibt separat offen.

Die native Test-App-Ausführung ergänzt die Android-Isolations- und Prozessneustartnachweise. Der Paralleltest verwendet einen frisch installierten synthetischen Produkt-ID-Referenzbuild mit aktivem QA-Seed-Guard, keine bestehende echte Produktinstallation. UIDs, Datenverzeichnisse, Room-Inhalte, Preferences sowie interne Datei-/Cache-Sentinels sind getrennt; die Referenz-Hashes bleiben nach allen E2Es unverändert. Der zusätzliche UI-Seed-Fall verlangt einen wirklich verschwundenen Prozess und anschließend eine neue PID; ein zusätzlicher gesonderter UI-Seed-/Prozessfall wird nicht als weitere eindeutige Methode in die Hauptsuite eingerechnet. Ein Neustart nach echtem Cloud-Restore ist damit noch nicht praktisch abgenommen. Lokal kein KVM; native Tests wurden auf den frischen GitHub-Emulatoren mit API35/Android15 (Pixel-5-Profil) ausgeführt. Die 17 neuen JVM-Initialisierungsfälle laufen unter SDK36/Android16; das ersetzt keinen nativen Test auf dem POCO/Android16.

## Nachvollziehbare Fehlversuche und Korrekturen vor Veröffentlichung

Zusätzliche rote Primärnachweise liegen unverändert bei: empty-list-red-regression.xml (zulässige leere FileList-Projektion), root-proof-red-regression.xml (doppelte Root-Abfrage innerhalb einer Operation), cache-identity-red-regression.xml (Ordnername/Pfad-Schlüsselkollision). Alle wurden gezielt vor dem endgültigen grünen Lauf behoben und abgesichert.

Der erste vollständige lokale Test-App-Lauf hatte 1181 tatsächliche Methoden und einen Fehler: eine alte assertNull-Erwartung für verbotene Ordneranlage passte nicht mehr zur expliziten Namespace-Exception. Die neue Assertion verlangt DriveAccessFailure/NAMESPACE und keine Fremdordneranlage; bestehende No-Cross-Read-/Write- und Inventarassertions bleiben erhalten. Ein erster lokaler Evidence-Aufruf ohne Commit verweigerte die Auswertung und wird nicht grün gezählt. Versionierte Evidence wurde neu erzeugt, nicht nachträglich normalisiert.

Die erste vollständige Produkt-CI 40319032256 auf e2c15f2 war mit 1154 JVM-/70 Emulatormethoden grün. Sie ist ein Zwischenstand und ersetzt nicht die oben angegebenen endgültigen Läufe. Kein relevanter CI-Lauf wurde durch diesen Auftrag abgebrochen. Reine QA-Bereitschaftskorrekturen für Phase5B/6B übernehmen bereits belegte Wartebedingungen bei unveränderten Fristen und Assertions; keine fachliche UI- oder Navigationsänderung.

## APKs, Herkunft und OAuth

Die folgenden Links bezeichnen jeweils das app-debug-Artefakt des Verify-Jobs. APK, Package, Label, Provider-Authorities und Signatur wurden aus dem tatsächlichen APK ermittelt. Debug-Zertifikate können zwischen Jobs/Läufen variieren; ein Emulator-APK darf nicht mit dem Verify-Artefakt verwechselt werden.

### Produkt-APK

[Versionsgebundenes APK-Artefakt](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/40364725921/artifacts/12669454877). Package: com.aistudio.steuerassistent.vnhqkp. Sichtbarer Name: Steuer-Assistent Anlage V. Version: 1.0 / Code 1. Größe: 34344813 Bytes. SHA256: 841d7f7d9e92a21d19c479ddfb91f3983e6b5c8be25c1245b349de9c07bf99cd. Signatur geprüft: True. Öffentliches Zertifikat SHA1: 744f9a4426d08662da868f30f92831d73deb5682; SHA256: a5b0d722506eb05ab89da0d637be025beb84751d706964915873804bb85d466c. Provider-Authorities: com.aistudio.steuerassistent.vnhqkp.provider, com.aistudio.steuerassistent.vnhqkp.mlkitinitprovider, com.aistudio.steuerassistent.vnhqkp.firebaseinitprovider, com.aistudio.steuerassistent.vnhqkp.androidx-startup.

### Test-APK

[Versionsgebundenes APK-Artefakt](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/40370749483/artifacts/12673342726). Package: com.aistudio.steuerassistent.vnhqkp.phase11a. Sichtbarer Name: ImmoPilot Test. Version: 1.0 / Code 1. Größe: 34466103 Bytes. SHA256: fc10f2c55de520c9e9973b115c2a1e7f905f40cbedb18b63602b57b9275bdd44. Signatur geprüft: True. Öffentliches Zertifikat SHA1: 58ef03a59a1c78807b3376041bf5fd8488eb9c4c; SHA256: 3ab78ce9c2b7dcc5ca09b3c25a552cc1e6d6049a65f288979827ac2bb8883e44. Provider-Authorities: com.aistudio.steuerassistent.vnhqkp.phase11a.provider, com.aistudio.steuerassistent.vnhqkp.phase11a.mlkitinitprovider, com.aistudio.steuerassistent.vnhqkp.phase11a.firebaseinitprovider, com.aistudio.steuerassistent.vnhqkp.phase11a.androidx-startup.

QA-Nebenbefund Q11B-01 (P3, Berichtsfeld): Der ältere APK-Helfer erkennt exported=true nur als 0xffffffff und zeigt bei aapt2-Ausgabe true fälschlich false an. Diese Komponenten-Flags werden deshalb nicht als Nachweis einer vollständigen Komponenten-Sicherheitsabnahme verwendet. Package, Label, Authorities, APK-Hash und Zertifikat sind unabhängig davon geprüft. Die unveränderten ursprünglichen JSON-Ausgaben bleiben im Archiv; die drei lokalen Rohmanifeste und phase11b-local-manifest-review.json belegen korrekt MainActivity exported=true und FileProvider exported=false. Das ist ein lokaler Manifestnachweis, keine nachträgliche Umschreibung der CI-Ergebnisse. Separaten kleinen QA-Parserfix empfehlen; keine App-/Drive-Funktion betroffen und keine Produktreparatur dafür eingeschoben.

Die tatsächliche Google-Anmeldung wurde nicht ausgeführt. In der zuständigen Google-Cloud-Konfiguration muss ein Android-OAuth-Client für das jeweilige exakte Package und den oben genannten SHA1 des heruntergeladenen Verify-APKs existieren; zusätzlich müssen Drive API, Consent-Konfiguration und gegebenenfalls OAuth-Testnutzer freigegeben sein. Ob dies bereits hinterlegt ist, wurde nicht eingesehen. Keine Secrets wurden committed und kein OAuth-Scope erweitert.

Die Produkt-Debug-APK nicht über eine Installation mit echten Nutzerdaten installieren oder dafür die Produkt-App deinstallieren. Für Tests der Produkt-ID ausschließlich ein leeres separates Gerät/Android-Profil verwenden. Eine abweichende Debug-Signatur berechtigt nicht zum Löschen bestehender Daten. Für den Handy-Test bevorzugt die isolierte Test-APK verwenden; auch bei deren Signaturkonflikt kein automatischer Datenreset.

## Verbleibende Risiken und priorisierter echter Nachtest

Ein unter drive.file nicht sichtbarer alter Ordner kann ohne explizite Berechtigung nicht übernommen werden. HTTP403/404 werden nicht durch Löschung, Reset oder unsicheren Fallback umgangen. Bereits vermischte historische Testdateien oder früher verlorene unitId-Zuordnungen werden nicht automatisch bereinigt/erraten. Die bestehende lesende Integritätsprüfung bleibt dafür notwendig. Die Reparatur verspricht keine Wiederherstellung früher verlorener Daten.

- Priorität 1: genaue installierte APK-Kennung und OAuth-Client des Download-Artefakts prüfen; keine produktive Installation ersetzen.
- Priorität 1: auf dem POCO X8 Pro/Android16 isolierte Test-App öffnen, vorhandenes synthetisches Testportfolio über Einstellungen → Phase 11A Testwerkzeuge laden, falls Installation leer ist. Bestehende Daten nicht löschen.
- Priorität 1: Google-Testkonto verbinden, Hinweis Test-Datenspeicher und richtigen Hauptordner prüfen. Vorhandenen leeren sichtbaren Testroot wiederverwenden und _BelegApp-Daten samt eigener Config einrichten lassen. Bei Fehler ausschließlich datensparsame HTTP-/Reason-Meldung protokollieren.
- Priorität 1: zweimal synchronisieren/sichern, vollständiger Restore und zweimal MERGE ausschließlich mit Testdaten; Prozess beenden/neu starten; Counts, IDs und alle 60 Originalhashes vergleichen. Produkt-Inventar nur lesend kontrollieren.
- Priorität 2: echten Auto-Backup-UI-Pfad, Offline-Verhalten, Abbruch, zweites Gerät/Profil, Dateipicker und externen Viewer separat abnehmen. Ohne tatsächliche Durchführung jeweils NOCH OFFEN.

Keine neuen am echten Gerät bestätigten P0/P1/P2/P3-Befunde während dieses Auftrags erhoben. Separater automatisiert/lesend bestätigter QA-Befund: Q11B-01, P3 (exported-Berichtsfeld); kein neuer funktionaler Appfehler. Issue #145 ist automatisiert reproduziert und repariert; sein tatsächlicher Gerätenachtest bleibt offen. Das ist keine pauschale Aussage über die Fehlerfreiheit bestehender Produktdaten. Original-App und Test-App werden ausschließlich zur Freigabe vorgelegt; kein Merge.

## Dateien und Reparatur-Commits

Die Produktänderung betrifft vier Main-Dateien: DriveAccessFailure.kt, DriveNamespacePolicy.kt, GoogleDriveClient.kt und DrivePersistenceRepository.kt. Weitere Dateien sind Tests, QA-Helpers, Workflow und Dokumentation. Vollständige Produkt-Dateiliste gegenüber der dokumentierten Basis:
- [.github/scripts/apk_signing.py](https://github.com/xedox737/Steuer-Asistend-APP/blob/b687bccbaab2d9d6cc40f748d88090c3690e5a80/.github/scripts/apk_signing.py)
- [.github/scripts/e11a-inspect-apk.py](https://github.com/xedox737/Steuer-Asistend-APP/blob/b687bccbaab2d9d6cc40f748d88090c3690e5a80/.github/scripts/e11a-inspect-apk.py)
- [.github/scripts/phase10b-evidence.py](https://github.com/xedox737/Steuer-Asistend-APP/blob/b687bccbaab2d9d6cc40f748d88090c3690e5a80/.github/scripts/phase10b-evidence.py)
- [.github/scripts/phase11b-verify-initialization.py](https://github.com/xedox737/Steuer-Asistend-APP/blob/b687bccbaab2d9d6cc40f748d88090c3690e5a80/.github/scripts/phase11b-verify-initialization.py)
- [.github/scripts/tests/fixtures/android-debug-public-cert.pem](https://github.com/xedox737/Steuer-Asistend-APP/blob/b687bccbaab2d9d6cc40f748d88090c3690e5a80/.github/scripts/tests/fixtures/android-debug-public-cert.pem)
- [.github/scripts/tests/test_phase11a_apk_signing.py](https://github.com/xedox737/Steuer-Asistend-APP/blob/b687bccbaab2d9d6cc40f748d88090c3690e5a80/.github/scripts/tests/test_phase11a_apk_signing.py)
- [.github/workflows/android-ci.yml](https://github.com/xedox737/Steuer-Asistend-APP/blob/b687bccbaab2d9d6cc40f748d88090c3690e5a80/.github/workflows/android-ci.yml)
- [app/src/androidTest/java/com/example/e2e/Phase6BWorkflowEmulatorTest.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/b687bccbaab2d9d6cc40f748d88090c3690e5a80/app/src/androidTest/java/com/example/e2e/Phase6BWorkflowEmulatorTest.kt)
- [app/src/main/java/com/example/api/DriveAccessFailure.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/b687bccbaab2d9d6cc40f748d88090c3690e5a80/app/src/main/java/com/example/api/DriveAccessFailure.kt)
- [app/src/main/java/com/example/api/DriveNamespacePolicy.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/b687bccbaab2d9d6cc40f748d88090c3690e5a80/app/src/main/java/com/example/api/DriveNamespacePolicy.kt)
- [app/src/main/java/com/example/api/GoogleDriveClient.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/b687bccbaab2d9d6cc40f748d88090c3690e5a80/app/src/main/java/com/example/api/GoogleDriveClient.kt)
- [app/src/main/java/com/example/data/DrivePersistenceRepository.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/b687bccbaab2d9d6cc40f748d88090c3690e5a80/app/src/main/java/com/example/data/DrivePersistenceRepository.kt)
- [app/src/test/java/com/example/api/DriveInitializationRegressionTest.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/b687bccbaab2d9d6cc40f748d88090c3690e5a80/app/src/test/java/com/example/api/DriveInitializationRegressionTest.kt)
- [app/src/test/java/com/example/ui/Phase5BComposeWorkflowTest.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/b687bccbaab2d9d6cc40f748d88090c3690e5a80/app/src/test/java/com/example/ui/Phase5BComposeWorkflowTest.kt)
- [docs/PHASE11B_DRIVE_INITIALIZATION.md](https://github.com/xedox737/Steuer-Asistend-APP/blob/b687bccbaab2d9d6cc40f748d88090c3690e5a80/docs/PHASE11B_DRIVE_INITIALIZATION.md)
- [docs/phase11b-evidence/cache-identity-red-regression.xml](https://github.com/xedox737/Steuer-Asistend-APP/blob/b687bccbaab2d9d6cc40f748d88090c3690e5a80/docs/phase11b-evidence/cache-identity-red-regression.xml)
- [docs/phase11b-evidence/empty-list-red-regression.xml](https://github.com/xedox737/Steuer-Asistend-APP/blob/b687bccbaab2d9d6cc40f748d88090c3690e5a80/docs/phase11b-evidence/empty-list-red-regression.xml)
- [docs/phase11b-evidence/initial-red-regression.xml](https://github.com/xedox737/Steuer-Asistend-APP/blob/b687bccbaab2d9d6cc40f748d88090c3690e5a80/docs/phase11b-evidence/initial-red-regression.xml)
- [docs/phase11b-evidence/root-proof-red-regression.xml](https://github.com/xedox737/Steuer-Asistend-APP/blob/b687bccbaab2d9d6cc40f748d88090c3690e5a80/docs/phase11b-evidence/root-proof-red-regression.xml)

Produkt-Commits in Reihenfolge:

- 9abe9d60fb60cf730879923054b129b879cc4b67 test: reproduce Drive initialization failure and unsafe 403 fallback
- c027c9722af924a472fe6f32aef41f268c4cb0f6 fix: initialize scoped Drive roots without unsafe search fallbacks
- 6afe763823f1999e58b155c9cf2f059f17e6db29 test: retain category race handling and cover Android 16 initialization
- 3154bf42725d2f881010e6b139d0165c449fef05 test: reuse verified APK certificate evidence for both variants
- 2a2303fc309e7ba82d08744a91cb44bf00f41b16 fix: accept valid empty Drive list projections without masking errors
- e2c15f22959f663ff6dcba813c0ca8e64d0c338d test: share proven emulator destination readiness across variants
- d53d92930e1909112f861e97f27d0f8989a05e30 fix: reuse root attestation only within one JSON validation
- b687bccbaab2d9d6cc40f748d88090c3690e5a80 fix: keep folder-name and canonical-path cache identities distinct

Die vorläufige Testintegration ergänzt gegenüber dem reparierten Produkt die folgenden bestehenden Build-/QA-/UI-/Fixture-Dateien:

- [.github/qa/phase11a-integration-ui-hooks.patch](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/.github/qa/phase11a-integration-ui-hooks.patch)
- [.github/scripts/phase10b-evidence.py](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/.github/scripts/phase10b-evidence.py)
- [.github/scripts/phase11a-audit-ci.py](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/.github/scripts/phase11a-audit-ci.py)
- [.github/scripts/phase11a-check-shared-product.py](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/.github/scripts/phase11a-check-shared-product.py)
- [.github/scripts/phase11a-check-ui-packaging.py](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/.github/scripts/phase11a-check-ui-packaging.py)
- [.github/scripts/phase11a-device-evidence.py](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/.github/scripts/phase11a-device-evidence.py)
- [.github/scripts/phase11a-emulator-smoke.py](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/.github/scripts/phase11a-emulator-smoke.py)
- [.github/scripts/phase11a-init-run.py](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/.github/scripts/phase11a-init-run.py)
- [.github/scripts/phase11a-inspect-apks.py](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/.github/scripts/phase11a-inspect-apks.py)
- [.github/scripts/phase11a-reference.init.gradle](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/.github/scripts/phase11a-reference.init.gradle)
- [.github/scripts/phase11a-sandbox-check.py](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/.github/scripts/phase11a-sandbox-check.py)
- [.github/scripts/phase11a-ui-seed-smoke.py](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/.github/scripts/phase11a-ui-seed-smoke.py)
- [.github/scripts/run-android-emulator-e2e.sh](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/.github/scripts/run-android-emulator-e2e.sh)
- [.github/scripts/tests/test_phase11a_device_guards.py](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/.github/scripts/tests/test_phase11a_device_guards.py)
- [.github/scripts/tests/test_phase11a_drive_boundary.py](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/.github/scripts/tests/test_phase11a_drive_boundary.py)
- [.github/scripts/tests/test_phase11a_evidence_counts.py](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/.github/scripts/tests/test_phase11a_evidence_counts.py)
- [.github/scripts/tests/test_phase11a_integrity.py](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/.github/scripts/tests/test_phase11a_integrity.py)
- [.github/scripts/tests/test_phase11a_phone_seed_guards.py](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/.github/scripts/tests/test_phase11a_phone_seed_guards.py)
- [.github/scripts/tests/test_phase11a_sandbox_guards.py](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/.github/scripts/tests/test_phase11a_sandbox_guards.py)
- [.github/workflows/android-ci.yml](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/.github/workflows/android-ci.yml)
- [app/build.gradle.kts](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/build.gradle.kts)
- [app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt)
- [app/src/androidTest/java/com/example/e2e/EmulatorTestSupport.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/androidTest/java/com/example/e2e/EmulatorTestSupport.kt)
- [app/src/androidTest/java/com/example/e2e/Phase11ADriveNamespaceEmulatorTest.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/androidTest/java/com/example/e2e/Phase11ADriveNamespaceEmulatorTest.kt)
- [app/src/androidTest/java/com/example/e2e/Phase11AUiSeedEmulatorTest.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/androidTest/java/com/example/e2e/Phase11AUiSeedEmulatorTest.kt)
- [app/src/androidTest/java/com/example/qa/Phase11AInstrumentation.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/androidTest/java/com/example/qa/Phase11AInstrumentation.kt)
- [app/src/androidTest/java/com/example/qa/Phase11ASandboxProbe.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/androidTest/java/com/example/qa/Phase11ASandboxProbe.kt)
- [app/src/debug/AndroidManifest.xml](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/debug/AndroidManifest.xml)
- [app/src/debug/assets/phase11a/hash-manifest.json](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/debug/assets/phase11a/hash-manifest.json)
- [app/src/debug/assets/phase11a/provenance.json](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/debug/assets/phase11a/provenance.json)
- [app/src/debug/assets/phase11a/qa-transport-snapshot.sha256](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/debug/assets/phase11a/qa-transport-snapshot.sha256)
- [app/src/debug/assets/phase11a/qa-transport-snapshot.zip](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/debug/assets/phase11a/qa-transport-snapshot.zip)
- [app/src/debug/java/com/example/qa/Phase11AIsolation.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/debug/java/com/example/qa/Phase11AIsolation.kt)
- [app/src/debug/java/com/example/qa/Phase11APortfolioSeeder.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/debug/java/com/example/qa/Phase11APortfolioSeeder.kt)
- [app/src/debug/java/com/example/ui/BuildVariantDriveNotice.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/debug/java/com/example/ui/BuildVariantDriveNotice.kt)
- [app/src/debug/java/com/example/ui/BuildVariantSettingsEntry.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/debug/java/com/example/ui/BuildVariantSettingsEntry.kt)
- [app/src/debug/res/values/strings.xml](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/debug/res/values/strings.xml)
- [app/src/main/java/com/example/ui/AppSettingsScreen.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/main/java/com/example/ui/AppSettingsScreen.kt)
- [app/src/main/java/com/example/ui/ReceiptAppUi.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/main/java/com/example/ui/ReceiptAppUi.kt)
- [app/src/qa/java/com/example/data/Phase11ASnapshot.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/qa/java/com/example/data/Phase11ASnapshot.kt)
- [app/src/qa/java/com/example/data/RestoreContractSnapshot.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/qa/java/com/example/data/RestoreContractSnapshot.kt)
- [app/src/qa/java/com/example/qa/Phase11ADriveHttpFixture.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/qa/java/com/example/qa/Phase11ADriveHttpFixture.kt)
- [app/src/release/java/com/example/ui/BuildVariantDriveNotice.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/release/java/com/example/ui/BuildVariantDriveNotice.kt)
- [app/src/release/java/com/example/ui/BuildVariantSettingsEntry.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/release/java/com/example/ui/BuildVariantSettingsEntry.kt)
- [app/src/test/java/com/example/data/Phase10BPortfolioAcceptanceTest.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/test/java/com/example/data/Phase10BPortfolioAcceptanceTest.kt)
- [app/src/testDebug/java/com/example/api/Phase11ADriveNamespaceTest.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/testDebug/java/com/example/api/Phase11ADriveNamespaceTest.kt)
- [app/src/testDebug/java/com/example/qa/Phase11AIsolationTest.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/testDebug/java/com/example/qa/Phase11AIsolationTest.kt)
- [app/src/testDebug/java/com/example/qa/Phase11APortfolioSeederTest.kt](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/app/src/testDebug/java/com/example/qa/Phase11APortfolioSeederTest.kt)
- [docs/PHASE11A_PRACTICAL_ACCEPTANCE.md](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/docs/PHASE11A_PRACTICAL_ACCEPTANCE.md)
- [docs/VERIFY_CHANGES.md](https://github.com/xedox737/Steuer-Asistend-APP/blob/f56d0c164262b1af2bd65936cf769b7aef3b0143/docs/VERIFY_CHANGES.md)

## Prüfnachweise

ImmoPilot-Phase11B-Evidence.zip enthält SHA256-/Byte-geprüfte CI-Transportarchive als entpackte JUnit-/JSON-Nachweise beider Verify-/Emulatorjobs, die endgültigen CI-Zusammenfassungen und die roten Primär-XMLs. manifest.json listet für jeden enthaltenen Nachweis SHA256 und Größe. evidence-checksums.json pinnt das ganze Archiv. Die Anzahl tatsächlich ausgeführter eindeutiger Methoden wurde unabhängig aus JUnit gezählt und gegen den Android-Quellbestand einschließlich geerbter Testmethoden geprüft. Ein grüner Jobstatus allein wurde nicht als ausreichender Portfolionachweis verwendet.

