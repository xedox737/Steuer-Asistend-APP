# PR #156 – Prüfung des Kandidaten 1403

Stand: 2026-11-01. Ausgangs-Head: `76d54358a0e899bfc76e4ffe0de200cfce0634f4`,
Main/Base: `5d5e61437fcf7324bb5e0f6442669f777d8ddeda`.

## PR-Stand und Übernahme von #157

Der GitHub-Vergleich bestätigt: Head von #157 (`298b7248184b18c4e4d02d1ecd8e858a223610c1`)
ist Vorfahr des Ausgangs-Heads von #156. Danach änderte sich ausschließlich
`app/build.gradle.kts` auf VersionCode **1403**. #156 bleibt ein offener Entwurf.
Beim Abruf waren keine Review-Threads, Review-Zusammenfassungen oder Kommentare vorhanden.

Die lokale isolierte Prüfbasis verwendet die vollständige Base und alle 30 am PR
geänderten Dateien. Jede Datei wurde über den GitHub-Connector am festen Head
abgerufen und gegen ihren Git-Blob-Hash geprüft. Tree:
`9726eb593922cb1918f8783953a0a524c7c9d312`. Der ursprüngliche Workspace bleibt erhalten.

## Befunde und gezielte Korrektur

1. [CI 41351307426](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/41351307426)
   führte 1307 Unit-Methoden aus; eine schlug fehl, null Skips.
   `missingLegacyOriginalHashIsNotReportedAsAnActualHttp412` verlangte noch eine
   Sperre für den unveränderten Cloud-only-Fall, den #157 absichtlich als
   schreibgeschützten No-Op erlaubt. Der Diagnosetest verwendet jetzt eine echte
   lokale Belegänderung. Die Sperre vor Schreibzugriffen, alle drei nicht
   versuchten Belege, erhaltene Anmeldung und die Trennung von lokaler Prüfung
   und echtem HTTP 412 werden weiterhin geprüft; Originalbytes und leerer Hash
   werden zusätzlich geprüft. Die beiden #157-Tests bleiben erhalten.
2. Ein neuer synthetischer Test belegt einen weiteren Referenzkonflikt: Bei lokal
   geändertem Dokumentreferenz-Dateinamen ließ die bisherige Vorprüfung den
   Cloud-only-No-Op zu. Erst die spätere Einzelbelegprüfung sperrte den Lauf;
   vorher wurden **39 POST-/PATCH-Anforderungen** an den In-Memory-Cloudbestand
   ausgeführt. Keine produktiven Drive-Zugriffe. Die vollständige Gleichheit der
   lokalen Dokumentreferenzen mit den gespeicherten Referenzen wird nun bereits
   während der Bestandsprüfung verlangt. Der neue Test verlangt null
   Schreibanforderungen, unverändertes Cloud-Inventar und Originalbytes,
   unveränderten leeren Hash und Erhalt der lokalen Referenzänderung.

## Prüfung und Grenzen

- 16 Python-Tests für Signatur-/CI-Schutzregeln bestanden.
- Die beiden #157-Tests, der angepasste Diagnosetest und der neue
  Referenzkonflikttest bestehen lokal: **4/4**, null Fehler/Skips. Der neue Test
  war zuvor mit 39 statt null Schreibanforderungen rot und ist nach der
  vorgezogenen Referenzprüfung grün (ergänzender SDK35-Prüfaufbau).
- Anschließend bestehen sämtliche **63 Phase11E-Methoden** im ergänzenden
  SDK35-Prüfaufbau: null Fehler/Skips. Dies ersetzt weder die vollständigen
  SDK36-Unit-Tests noch den GitHub-Emulatorlauf oder die reale POCO-Abnahme.
- Geänderte Produktklassen mit Kotlin/Compose gegen SDK36 kompiliert.
- Frische Room-KSP-Generierung; Schema-Version **34**, Identität
  `42894a175052e9a85345873c5069e7a7` unverändert.
- Lokales vollständiges `testDebugUnitTest` wurde gestartet, scheitert aber schon
  vor dem Build an `FileLockContentionHandler`: keine nutzbare Wildcard-IP in
  dieser Sandbox. Lokales reguläres Gradle/Lint/APK-Bauen ist damit blockiert.
  Kein `/dev/kvm`; keine lokale Instrumentation behauptet.
- Ergänzende lokale JVM-Läufe nutzen frische Produkt-/Room-Klassen und eine
  externe SDK35-Kopie des Tests mit Java21 aus dem vorhandenen Cache. Die
  eingecheckte SDK36-Annotation bleibt unverändert. Veraltete generierte Klassen
  und ein zunächst falscher Modulname im direkten Compileraufruf wurden als
  lokale Prüfaufbaufehler erkannt; diese Fehlstarts gelten nicht als bestandene
  Produktprüfungen. Maßgeblich bleibt der neue vollständige GitHub-CI-Lauf.
- Im Ausgangslauf waren `lintDebug` und die tatsächliche Testausführungsprüfung
  erfolgreich. Der Portfolio-Audit scheiterte wegen des einen Unit-Fehlers.
  APK-Build, APK-Identitäts-/Zertifikatsprüfung und `android-emulator-e2e` wurden
  übersprungen. Das einzige Artefakt war
  [android-reports](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/41351307426/artifacts/13101769456),
  **keine 1403-APK**.

Der Korrekturcommit löst über das bestehende `pull_request`-Ereignis einen neuen
vollständigen Android-CI-Lauf aus. Dessen endgültige Run-ID, tatsächliche
Testzahlen, Signaturprüfung und APK-Artefakt werden im Prüfkommentar zu
[PR #156](https://github.com/xedox737/Steuer-Asistend-APP/pull/156) dokumentiert.
Ein grüner Alt-Lauf von 1402 ist kein Nachweis für 1403.

## Review-Werkzeuge und Sicherheit

Angewendet: GH Review Loop sowie Agent Skills für Code-Review, Fehlersuche,
Tests, Sicherheit und Git-Arbeit. `gh` 2.46.0 und Python3 sind installiert.
`gh auth status` meldet jedoch eine ungültige Anmeldung über `GH_TOKEN`;
`git fetch` erreicht den konfigurierten Proxy nicht. Voraussetzung für den
skriptgestützten Loop ist eine funktionierende authentifizierte GitHub-CLI mit
erreichbarer GitHub-API und passenden Repository-/Actions-Rechten. Deshalb
erfolgen GitHub-, CI- und die vom Nutzer gewählte Codex-Review-Anforderung
separat über den verbundenen GitHub-Connector. Dies ist kein erfolgreich
abgeschlossener skriptgestützter GH Review Loop.

Keine Nutzerdaten gelöscht, keine produktiven Cloud-Daten gelesen oder
verändert, keine destruktive Migration, keine Änderung am Google-Login,
Package oder Signaturschlüssel. Kein Merge nach Main und kein öffentliches
Release. **Reale POCO-/Android16-Abnahme weiterhin offen.** Auch eine später
grüne CI und korrekt signierte APK belegen keinen erfolgreichen POCO-Test.
