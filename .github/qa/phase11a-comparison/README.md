# Phase 11A: Fehlervergleich – ausschließlich QA

Geprüft: `main` **69f35772b6480a958ca7479b024dbd4d8bd42fa1** und Test-App
**8ddd556061d6d9a5ed607163aff42dbf3578f8c1**. Keine fachliche Reparatur.
Die Helfer liegen außerhalb der App-Quellensätze. Das Init-Script bindet sie ausschließlich
für einen ausdrücklich gestarteten JVM-Audit ein. Kein APK-Seed, kein ADB, kein echter Drive.

## Reproduzieren

JDK 21, Gradle 9.3.1 und Android-SDK 36.1 bereitstellen. Beide Commit-Objekte müssen im
Repository vorhanden sein. `ANDROID_HOME` oder `ANDROID_SDK_ROOT` setzen.

```bash
python3 .github/qa/phase11a-comparison/run_comparison.py \
  --gradle /pfad/zu/gradle-9.3.1/bin/gradle \
  --output /tmp/immo-fehlervergleich-neuer-lauf
```

Optional `--init-script /pfad/umgebung.init.gradle` ausschließlich für die lokale
Proxy-/CA-/Robolectric-Konfiguration. Die Arbeitsumgebung dieses Berichts benötigte
einen beschreibbaren JVM-user.home für Robolectric sowie die bereitgestellten Proxy-/CA-Werte.
Keine Secrets in Init-Scripts/Repository schreiben.

Der Runner erstellt zwei neue detached Worktrees, liest die unveränderte originale
Phase10B-ZIP aus dem Test-Commit und prüft deren SHA256
`b052a1ae4569196abb791375aa1fa01fe6a636a1d0082820acb576fa497ea638`.
Der JVM-Fall verwendet existierende Parser und SupplementalDriveBackup für den
Aufbau seiner ausschließlich synthetischen, frischen Room-Testdatenbank. Der bestehende
Phase11A-UI-Seed und sein Produkt-Package-Guard werden **nicht** aufgerufen oder umgangen.
JVM-Fingerprint, temporärer Context, App-ID und eigener Datenbankname werden geprüft.

Die originale private finale OkHttpClient-Referenz bleibt unverändert. Nur dessen
Interceptor-Liste wird innerhalb des Test-JVMs vorübergehend auf den simulierten
Transport gesetzt und anschließend zurückgesetzt. Es gibt kein `chain.proceed`, keine
Google-Zugangsdaten, keine installierte Produkt-App und keinen echten Cloud-Aufruf.
Anwendungsquellen, Build-Identität und Guard-Logik der untersuchten Versionen bleiben unverändert.

## Ergebnisse richtig lesen

Pro Ziel: **20 Methoden**, davon sechs neue Vergleichsfälle und 14 bestehende
Phase10B/10C/10D-Kontrollen. Der vollständige Portfoliofall behält einen **roten strikten
Rohdatenvergleich**: beim MERGE entstehen acht Legacy-Namensaliase zusätzlich zu den
102 Einheiten-Preference-Einträgen. Keine bestehenden Werte ändern sich/entfallen.
Der Runner unterdrückt diesen Fehler nicht und gibt weiterhin Exit 1 zurück.
Alle 20 Methoden müssen tatsächlich in XML vorhanden sein; keine Skips/Platzhalter.

Vor der strikten abschließenden Assertion werden alle Zustände einschließlich
Datenbank-Wiederöffnung gesichert. Die Assertion wurde weder entfernt noch gelockert.
E11A-02/E11A-03 werden durch explizite Charakterisierungsassertions reproduziert;
eine bestandene Charakterisierung ist **keine bestandene fachliche Integrität**.
Der unveränderte Phase11A-Checker liefert separat 4 Scopefehler nach dem ersten Upload
und 240 nach dem vollständigen Sync für 60 betroffene Dokumente.

`ComparisonDriveHttpFixture.kt` ist die unveränderte HTTP-Fixture aus 8ddd556 mit
ausschließlich geändertem Paket-/Klassennamen. `ComparisonSnapshot.kt` ist derselbe
Snapshothelper mit geändertem Objektnamen. Keine vereinfachte Portfoliofixture.
`source_audit.py` vergleicht die gepinnten Quelltexte und speichert unveränderte Diffs.
`analyse_results.py` wertet die vollständigen gespeicherten JSON-Snapshots unabhängig
mit dem unveränderten Checker aus. Checkerpfad als zweites Argument übergeben.

Die reproduzierbaren Primärläufe dieses Berichts wurden über gleichwertige explizite
Gradle-Aufrufe ausgeführt; der Komfort-Runner wurde auf Python-Syntax/CLI geprüft.
Ein zusätzlicher Versuch `testReleaseUnitTest` konnte nicht ausgeführt werden:
dieser Task ist im unveränderten Projekt nicht konfiguriert. Keine Release-Unit-Ausführung
behaupten. Der Originalvergleich läuft mit dem Original-main-Code und Produkt-Package
im JVM-Debug-Test; eine installierte Store-/Release-APK wurde nicht untersucht.

PDF, Markdown, unveränderte XML/JSON-Diagnosen und Hashmanifest stehen unter
`docs/phase11a-comparison/`. Der PDF-Abschlussbericht befindet sich unter
`docs/ImmoPilot-Fehlervergleich-TestApp-vs-Original.pdf`.
