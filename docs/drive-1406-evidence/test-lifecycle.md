# Version 1406: Anmeldungstest-Lebenszyklus

Basis: Main `6dceb5005955d600268f5a8949db7fb2eea7f897`, sauberer lokaler
Quellbaum auf separatem Branch `agent/drive1406-test-lifecycle`. Main wurde über
den autorisierten GitHub-Connector geprüft; Git-Fetch bleibt am Proxy blockiert.

Main-CI [41677573776](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/41677573776)
führte 1.382 Unit-Methoden aus; zwei scheiterten. Die vollständigen XML-Stacks
belegen eine Testbereinigungs-Race in `DriveConnectionRegressionTest.cleanup`:
`ViewModelStore.clear()` fordert Abbruch an, wartet jedoch nicht auf dessen
Abschluss. `Dispatchers.resetMain()` überschnitt sich mit der Wiederaufnahme
eines noch abschließenden Coroutine-Kindes. Der Folgetest erbte diesen Zugriff;
zusätzlich scheiterte dessen Bereinigung am nicht initialisierten HTTP-Vorgänger.
Die ursprünglichen fachlichen Assertions wurden nicht abgeschwächt.

Die Korrektur erfasst bei jeder Modellerstellung den ursprünglichen Scope-Job,
leert die Stores und wartet auf alle Scope-Jobs, bevor HTTP-Transport und Main
zurückgesetzt werden. Das bestehende 15-Sekunden-Testlimit bleibt erhalten.
Keine Änderung an Produktcode, Anmeldelogik, Nutzerdaten, APK-Version oder CI.

Der neue Test `teardownWaitsForCancelledViewModelChildren` hält die Bereinigung
eines wirklich abgebrochenen ViewModel-Kindes mit einem Deferred an. Ohne Join
kehrt die Testbereinigung zu früh zurück: reproduzierbar 1 Methode / 1 Fehler.
Mit Join bleiben die Testressourcen bis zum Ende des Kindes installiert.
Die vollständige Anmeldungstestklasse besteht lokal: 45 Methoden, keine Fehler
oder Skips. Lokale Ausführung verwendet ausschließlich eine externe SDK-35-Kopie;
die SDK-36-Deklaration im Repository bleibt unverändert und wird separat kompiliert.
Gradle kann lokal wegen der bekannten FileLockContentionHandler-Netzwerksperre
nicht starten; volle SDK-36-Suite, Lint, Build und Emulator laufen daher über CI.

Nachbarprüfung: Die betroffenen vollständigen Drive-Suiten
`Phase11ESyncRegressionTest` und `DriveDocumentIndexRegressionTest` warten bereits
auf ihre ViewModel-Jobs vor dem Main-Reset. Andere fachliche Testklassen wurden
gelesen und bleiben außerhalb dieser gezielten Änderung; sie teilen wegen
`forkEvery = 1` keinen JVM-Worker mit der fehlgeschlagenen Anmeldungstestklasse.

Dies repariert den belegten Testlebenszyklus, nicht den weiterhin ungeklärten
realen Schreibabschlusskonflikt. Der reale unveränderte Lauf mit Version 1406
ist erfolgreich; die Abnahme des vollständigen Schreiblaufs bleibt offen.
