# Phase 11B – endgültiger automatisierter Prüfstand (26.10.2026)

Reparatur-PR: https://github.com/xedox737/Steuer-Asistend-APP/pull/146 (Draft, nicht gemergt).
Base origin/main: 6d58dbac9d9f6d3c1c57c9b14579d5360751879d.
Produkt-Head: b687bccbaab2d9d6cc40f748d88090c3690e5a80; Testintegration: f56d0c164262b1af2bd65936cf769b7aef3b0143.
Produkt-CI 40364725921: 1156 JVM- und 70 eindeutige Emulatormethoden.
Test-CI 40370749483: 1183 JVM- und 73 eindeutige Emulatormethoden,
plus gesonderter wiederholter UI-Seed-/Prozessstopp-Fall.
Beide vollständig grün: AUTOMATISIERT BESTANDEN; keine Fehler/Skips.
Je Variante: 17 neue SDK36-Fälle, 60/60 Originalhashes, 0 fachliche Contract-/Relationsfehler.
Native Emulatoren: API35; echter POCO-X8-Pro-/Android16-/Google-OAuth-Nachtest: NOCH OFFEN.
Keine praktische Portfoliofreigabe. Keine Datenbereinigung oder Migration.

[Vollständiger Bericht](ImmoPilot-Phase11B-Drive-Synchronisierung.md) ·
[PDF](ImmoPilot-Phase11B-Drive-Synchronisierung.pdf) ·
[Evidence](phase11b-evidence/ImmoPilot-Phase11B-Evidence.zip).

Die Signaturen und OAuth-SHA1 des jeweiligen Download-Artefakts stehen im Bericht.
QA-Nebenbefund Q11B-01/P3 betrifft nur das exported-Ausgabefeld des alten APK-Helfers;
es wird nicht als Komponenten-Sicherheitsnachweis verwendet. Rohmanifeste liegen bei.

## Historischer Arbeitsverlauf

Die folgenden Angaben zu offenen Prüfungen beschreiben damalige Zwischenstände. Maßgeblich ist der endgültige Prüfstand oben.

# Phase 11B – Drive-Initialisierung, Issue #145

## Basis und Grenzen

Base origin/main: 6d58dbac9d9f6d3c1c57c9b14579d5360751879d (26.10.2026).
PR #144 inzwischen durch den Repository-Betreiber gemergt. Eigener Branch:
fix/phase11b-drive-initialization. Keine produktiven Daten oder Google-Konten
verwendet. Arbeitsanweisungen außer AGENTS.md nicht vorhanden.

Die installierte Original-APK des Geräteberichts ist nicht versionsgebunden
bekannt; Android-Auto-Restore alter Preferences ist nicht nachgewiesen.
Der echte OAuth-/Drive-Gerätenachtest bleibt NOCH OFFEN.

## Regression vor jeder Produktänderung

DriveInitializationRegressionTest gegen unveränderten Produktcode:
4 tatsächlich ausgeführte Methoden, 3 Fehler, 0 Errors/Skips.
JUnit-Primärnachweis: phase11b-evidence/initial-red-regression.xml.

- Frischer Root ohne Zugriff auf allgemeine MyDrive-Root-Metadaten: exakt
  "Drive-Sicherheitsabbruch: Gespeicherte Ordner-ID ist für diese App nicht zulässig."
- Vorhandener leerer eigener Root, allgemeine Root-Metadaten HTTP404:
  gleicher Abbruch; kein Systemordner.
- Ordnersuche HTTP403: fälschlich SuccessCreatedNew, sieben neue Ordner.
- Kontrollfall mit erreichbaren allgemeinen Root-Metadaten: bestanden.

Das simuliert einen zulässigen eingeschränkten Sichtbarkeitszustand unter
Scope drive.file, beweist aber nicht den tatsächlichen HTTP-Status des
Nutzergeräts. Die bisherige Simulation gab Root-Metadaten stets erfolgreich
zurück. Sicherheit muss stattdessen durch serverseitig auf Top-Level-Eltern
begrenzte Suche bewiesen werden. Fremde/nested Roots bleiben gesperrt.
API-/Berechtigungsfehler dürfen niemals als leeres Suchergebnis gelten.

## Gezielter Fix und erster grüner Lauf

32 tatsächliche Methoden (13 Initialisierung, 14 bestehende Namespacefälle,
5 vollständige Dokument-/Restore-Portfolioprüfungen), keine Fehler/Skips.
Kein Test entfernt oder abgeschwächt. Produktcode auf Basis von #144.

Die allgemeine Root-Metadatenabfrage wird durch eine serverseitig auf den
Top-Level-Eltern `root` begrenzte Suche nach dem aktiven App-Root ersetzt.
Die gesuchte Root-ID muss darin vorkommen. Name, MIME, Tags, eindeutige
Elternkette, Shortcuts und fremde Roots werden weiterhin überprüft; kein
Fallback auf Produktroot, keine neue OAuth-Berechtigung und keine Migration.

Initialisierungssuche wertet Fehler strikt aus; nur erfolgreich vollständig
leere Suchergebnisse erlauben neue Ordner. Gesuchte, erstellte und gecachte
Ordner werden vor Nutzung geprüft. Cache ist Token-/Root-/Elterngebunden.
HTTP404 bleibt mehrdeutig und löst keine Mappinglöschung aus. Nur eigene,
nachweislich ungültige Test-Mappings (Syntax/MIME/Ort) werden einzeln entfernt;
keine Preferences-Löschung, kein Produkt-Mappingreset, keine Cloudmutation
an bestehenden Dateien. Fehlertexte enthalten keine IDs/Tokens/Antwortbodys.

Der erste separat gestartete strikte Evidence-Checker verweigerte die lokalen
Snapshots wegen fehlendem App-Commit (`missing app commit`). Das ist kein
grüner Integritätsnachweis. Versionierte CI-Evidence wird mit echtem Commit
neu erzeugt; Metadaten werden nicht nachträglich normalisiert/erfunden.
Vollständige JVM-/Lint-/APK-/CI-Prüfungen und echte Geräteprüfung noch offen.

## Nachgereichter Gerätebefund

Nutzerangabe vom 26.10.2026: POCO X8 Pro, Android 16, „die letzte Build-Version“.
Dies belegt Modell/OS laut Nutzerbericht, aber keinen exakten APK-Hash/Commit
der installierten Original-App. Keine eigene praktische Geräteprüfung behauptet.

## Ergänzte QA-/Kompatibilitätsabsicherung

Die Fehlermeldung beweist keine persistierte Alt-ID: der gerade erzeugte Root
wird bereits innerhalb desselben Init-Aufrufs im Memory-Cache wiederverwendet.
Ein leerer eigener Root ist ohne alte Preferences reproduzierbar.
Die bisherige Kategorie-Race-Behandlung (ältesten gültigen Treffer verwenden,
keine parallelen Ordner löschen) bleibt erhalten. Die neuen 13 Methoden
werden ausdrücklich unter SDK36/Android16 ausgeführt und prüfen den SDK im
Setup. Die zuvor im Test-App-Branch belegte Phase5B-Testbereitschaft wird
unverändert auch für den Produkt-Testaufbau übernommen: drei separate Flows
vor der Bankzeile abwarten, unveränderte Frist und alle Assertions erhalten.
Keine Bank-/Navigationslogikänderung.

## Versionsgebundene APK-Nachweise

Die bereits im Test-App-Branch geprüfte QA-Auswertung des verifizierten
öffentlichen APK-Zertifikats wird für beide Varianten verwendet. Sie erzeugt
APK-/Zertifikat-SHA256 und OAuth-SHA1 je tatsächlichem CI-Artefakt. Das
Prüfbeispiel enthält ausschließlich ein öffentliches Zertifikat, keinen
privaten Schlüssel. Drei Parserregressionen, keine neue Signierkonfiguration.

## Zusätzlich vor Veröffentlichung reproduzierter Leerlistenfall

Auf Produktstand 3154bf4 scheiterte der neue einzelne Test für eine erfolgreiche
partielle FileList-Antwort ohne leeres files-Feld (1 tatsächliche Methode,
1 Fehler, keine Skips). Dies war eine zu strenge Stelle im neuen Suchhelfer,
kein neuer bestätigter Gerätebefund. XML: phase11b-evidence/empty-list-red-regression.xml.
Die gemeinsame Listenvalidierung akzeptiert jetzt leere/ausgelassene Sammlungen
nur bei erfolgreichem, gültigem FileList-JSON. error-Objekte, falsche Typen,
fremde Metadatenobjekte und incompleteSearch bleiben Fehler. Root-Zugehörigkeit
verlangt weiterhin ein tatsächlich enthaltenes positives ID-Ergebnis.
Zwei zusätzliche Methoden sichern leere Projektion und malformed-200-Antworten;
insgesamt 15 neue SDK36-Initialisierungsmethoden. Keine HTTP- oder Ownership-
Fehler werden in ein leeres Suchergebnis umgewandelt.

## Gemeinsame native QA-Bereitschaft

Auch die bereits im Phase11A-Emulatorlauf 40184584401 nachgewiesene reine
Phase6B-Testbereitschaftskorrektur wird in den Produkt-Testaufbau übernommen:
vor der unveränderten Sichtbarkeitsassertion auf die tatsächlich sichtbare
Sanierungsübersicht warten. Bestehendes 10s-Limit, keine Assertion entfernt,
kein Timeout erhöht, keine Produkt-/Sanierungslogikänderung. Drei QA-Zeilen,
entsprechen exakt dem bestehenden Test-App-Stand. Native Ausführung erfolgt
mangels lokalem KVM in der vollständigen GitHub-CI; noch nicht als bestanden
behauptet.

## Root-Nachweis je JSON-Operation begrenzt

Zusätzlicher gezielter Aufrufzähltest auf e2c15f2: eine Methode tatsächlich
ausgeführt, erwarteter Root-Nachweis 1, tatsächlich 2 bei zwei Referenzen.
Das ist unnötiger Netzwerkaufwand in großen Indizes, kein bewiesener Grund
für eine konkrete Laufzeit-/Geräteabweichung. XML root-proof-red-regression.xml.
Die bestehende ausschließlich operationslokale Metadaten-Wiederverwendung
führt jetzt auch die bereits geprüften Roots in einem lokalen Set. Ein
Root-Nachweis je JSON-Prüfung, kein globaler/persistenter Ownership-Cache.
Jede Folgeoperation liest Metadaten und Zugehörigkeit erneut. Der neue
16. SDK36-Fall verlangt zusätzlich die Abweisung einer extern verschobenen
Datei bei der nächsten Prüfung; keine Aktualitätsprüfung abgeschaltet.
Die bereits laufende Produkt-CI wird einschließlich Emulator abgewartet;
der endgültige Stand erhält danach eine vollständige neue CI.

## Cache-Schlüsselarten erhalten

Zusätzlicher SDK36-Fall auf d53d929: ein normaler Ordnername und ein anderer
kanonischer Pfad mit gleichem Schlüsseltext konnten im neuen Suchhelfer
denselben Memory-Cacheeintrag treffen (1 tatsächlicher Test, 1 Fehler).
Primärnachweis cache-identity-red-regression.xml. Die bereits vor Phase11B
vorhandene Unterscheidung der Schlüsselarten wird explizit erhalten: name#
und path#, weiterhin mit Token-/Root-/Elternbindung. Keine persistenten
Schlüssel oder Cloudnamen werden migriert, keine vorhandenen Ordner verändert.
Der 17. Fall prüft getrennte IDs und wiederholte korrekte Wiederverwendung.

Erste Produkt-CI 40319032256 auf e2c15f2 vollständig grün einschließlich
Emulator; 1154 JVM-Methoden/15 neue Initialisierungsfälle sind belegt.
Dieser Lauf wird getrennt vom nachfolgenden endgültigen Stand dokumentiert.
Kein relevanter CI-Job wurde abgebrochen.

Die getrennten Cache-Schlüsselarten behalten den bisherigen gemeinsamen
logischen Mutex (Elternordner + Name/kanonischer Pfad) bei. So ändern die
Schlüsselpräfixe nicht die bisherige Serialisierung gleichartiger Anlagen.
