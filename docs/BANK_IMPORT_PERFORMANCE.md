# Bankimport: Listen- und Berechnungsperformance

Ausgangsstand: `a4fd2982e76915f69e72a2f21295a971a8beb2ec` (`main`, am 05.11.2026 erneut unverändert abgerufen).
Nur synthetische Fixtures; keine Echtdaten. Keine Änderung an Room-Schema, Migrationen,
Importformaten, IDs oder schreibenden Bank-/Beleg-/Mietzuordnungspfaden.

## Reproduktion und Ursache

`BankFeature.kt` machte eine komplette Datumsgruppe zu **einem** Lazy-Item:
`item { Card { Column { transactions.forEachIndexed { … } } } }`.
Damit komponierte bereits das erste sichtbare Item sämtliche Buchungen dieses Tages.
Die Reproduktion verwendet 1.250 bzw. 5.000 Buchungen am selben Tag, unveränderte Kategorien,
Unterkategorien, Konto-, Objekt-, Einheiten- und Import-IDs.

Lokale RED-Läufe (Robolectric SDK 35, native Grafik, 393×852 dp, JDK 21.0.8, Gradle 9.3.1):

| Buchungen | Komponierte Zeilen | Erste sichtbare Buchung | JVM-Heap vorher → nachher |
|---:|---:|---:|---:|
| 1.250 | 1.250 | 8.450 ms | 110.413.512 → 239.181.560 B |
| 5.000 | 5.000 | 17.020 ms | 140.173.784 → 492.307.136 B |

Beide Tests scheiterten gezielt an `composed_rows <= 50`, ohne OOM oder Infrastrukturfehler.
Jeweils ein eigener JVM-Lauf; Heap-Snapshots sind weder allokierte Bytes noch Android-PSS.
Die Zeiten enthalten VM-Erzeugung und UI-Bereitschaft. Das sind **keine Android-Gerätewerte**
und kein Nachweis eines konkreten ANR auf dem Nutzergerät.
JUnit-Belege: [1.250](bank-performance-evidence/1250.xml), [5.000](bank-performance-evidence/5000.xml).

Getrennte GREEN-Läufe je Datengröße (gleiche JVM-/Robolectric-Konfiguration):

| Buchungen | Komponierte Zeilen | Erste sichtbare Buchung | JVM-Heap vorher → nachher | Letzte Buchung per ID erreichbar |
|---:|---:|---:|---:|---:|
| 1.250 | 6 | 2.375 ms | 131.761.464 → 134.753.776 B | 128 ms |
| 5.000 | 6 | 2.478 ms | 139.154.496 → 161.830.704 B | 170 ms |

[GREEN 1.250](bank-performance-evidence/1250-after.xml),
[GREEN 5.000](bank-performance-evidence/5000-after.xml).
Die klarste Regressionseigenschaft ist die von der Gesamtmenge unabhängige Anzahl
komponierter Zeilen. Zeit-/Heapwerte dieser UI-Reproduktion haben je einen Lauf;
für statistische Android-Aussagen dienen die getrennten Emulator-Wiederholungen.

Weitere gemessene Ursachen:

- Bank-Belegmatching lief im `viewModelScope` ohne `flowOn`, damit auf dem Hauptthread.
  Die Liste abonnierte Beleg- und Mietvorschläge, obwohl sie diese erst in Details verwendet.
- Regex-Patterns wurden bei jeder Normalisierung erneut kompiliert. Der lokale JFR-Samplelauf
  enthält 135 inklusive `BankReceiptMatcher.score`- und 116 `normalize`-Samples; zusätzlich
  sind Regex-Kompilierung und HashMap-Aufbau in den Leaf-Frames sichtbar. Die Zahlen sind
  inklusive, überlappen und dürfen nicht addiert werden. Der Trace umfasst auch Warmups und
  Referenztests, nicht ausschließlich die gemessenen Intervalle.
- Mietkandidaten bauten für jeden Bankeingang vollständige Buchungs-/Belegindizes neu auf.
  Pro Einheit und Monat wurden dieselbe Mietprojektion und Historie wiederholt gelesen.
- Gruppierung parsierte Datumsstrings in den Sortiervergleichen. Suche erzeugte pro Zeile
  einen `DecimalFormat` und einen Regex.

Quellen: [Berechnungs-Baseline](bank-performance-evidence/computation-before.xml),
[JFR-Zusammenfassung](bank-performance-evidence/jfr-before-summary.json).

## Berechnungen vor/nach der Änderung

Identischer einzelner Testklassenlauf mit JFR `settings=profile` vor und nach der Änderung;
je zwei Warmups und drei gemessene Wiederholungen. JVM-Werte, keine Android-Latenz.

| Operation bei 5.000 Buchungen | Median vorher → nachher | Allokation pro Lauf vorher → nachher |
|---|---:|---:|
| Gruppierung | 17,27 → 1,67 ms | 30.697.240 → 125.168 B |
| Belegmatching (40 Belege) | 893,93 → 61,38 ms | 1.818.793.728 → 33.947.608 B |
| Mietkandidaten, kompletter Batch | 357,53 → 114,54 ms | 463.128.192 → 128.180.000 B |
| Suche | 26,80 → 19,49 ms | 13.873.272 → 6.723.944 B |

Die Zeitintervalle für Gruppierung, Matching und Mietkandidaten trennen sich bei 5.000
Buchungen (Min/Max stehen im XML). Bei 1.250 überlappen die Such-Zeitintervalle;
es wird dafür kein belastbarer Geschwindigkeitsgewinn behauptet. Die Suchänderung bleibt
wegen der deterministischen Allokationsreduktion von rund 52 % bei 5.000 Buchungen erhalten.
Die unveränderten Zähler dienen als Kontrolle; JIT/GC-Ausreißer verbieten den Vergleich
kleiner Unterschiede allein über Mittelwerte. Keine Timer-Assertions auf JVM-Mikrobenchmarks.

[Finales Nachher-XML](bank-performance-evidence/computation-prepared.xml),
[JFR nachher](bank-performance-evidence/jfr-prepared-summary.json).
Der erste Zwischenstand mit reiner Regex-Wiederverwendung ist weiterhin in
[computation-after.xml](bank-performance-evidence/computation-after.xml) dokumentiert;
er genügte dem nativen Detail-Budget noch nicht.
Beibehalten wurden Virtualisierung, bedarfsgesteuerte Worker-Berechnung,
Regex-/Formatter-Wiederverwendung, Datumsgruppierung und Miet-Batch.
Keine neuen SQL-Indizes, TTL-Caches oder Poolvergrößerungen wurden versuchsweise eingeführt.

## Technische Änderungen und Grenzen

- Jede Buchung erhält ein eigenes `itemsIndexed`-Item mit Schlüssel `transaction-<bestehende ID>`
  und wiederverwendbarem Content-Type. Datumsüberschriften und zusammenhängende Kartenoptik bleiben.
- Kontoauswahl, Zähler, Summen, Filter, Suche und Gruppierung werden auf `Dispatchers.Default`
  berechnet. `produceState`/`withContext` verhindern die Veröffentlichung einer bereits abgelösten Suche.
- Vorschlagsabonnements starten in den Buchungsdetails; das Mietwerkzeug abonniert weiterhin selbst.
  Belegmatching und Regelauswertung arbeiten auf `Dispatchers.Default`.
- Die nachgelagerte Regelanreicherung verwendet ID-Indizes statt wiederholter linearer Suchen.
- Der umgekehrte Belegabgleich berechnete bei jedem UI-Neuaufbau bis zu 30 vollständige
  Bankrankings auf Main. Ein separater JVM-Lauf misst bei 5.000 Buchungen 250 ms Median
  und 322.996.208 B Allokationen für diese 30 Abgleiche (2 Warmups, 5 Messungen).
  Dieser Lauf und das Buchungs-Picker-Ranking (14,85 ms, 18.322.752 B) werden nun einmal
  pro Eingabestand auf Default berechnet; Quellenwechsel verwerfen alte Ergebnisse.
  Beleg-Picker bei 40 Belegen: 0,225 ms, unverändert. Siehe
  [Messbeleg](bank-performance-evidence/reverse-computation.xml).
- Mietkandidaten verwenden eine `Batch` **pro Quell-Emission**. Indizes sowie Cache-Schlüssel
  enthalten stabile Objekt-/Einheiten-IDs und Mietmonat. Bei neuen Quellen wird die komplette
  Batch verworfen. Kein globaler Cache, kein TTL, kein persistierter Vorschlagszustand.
- Normalisierte Textmerkmale, Tokens, Monatsnamen, Zahlungsarten und Datumswerte werden
  einmal je Buchung/Beleg innerhalb eines Matching-Aufrufs vorbereitet. Kein globaler Cache.
- Matching-Scores, Schwellen, Tie-Breaks, Zuordnungssummen und die Ausnahme der eigenen
  Transaktion beim Zahlungsrhythmus bleiben erhalten. Regex-Patterns werden wiederverwendet;
  `DecimalFormat` bleibt lokal pro Suchaufruf, also nicht zwischen Threads geteilt.
- Alle Buchungen werden weiterhin geladen und sind erreichbar. Das Belegmatching bleibt
  grundsätzlich paarweise; diese Änderung führt weder einen Kandidaten-Cutoff noch Paging
  oder eine Begrenzung importierter Daten ein.

DAO-Prüfung: `BankDao` lädt Konten, Buchungen und Links gesammelt über Room-Flows/Suspend-
Abfragen, die Mietzuordnungen ebenfalls gesammelt. Die Vorschlagsberechnungen führen keine
DAO-Abfrage pro Buchung aus. Vorhandene Indizes betreffen Datum, Konto, Status, Klassifikation,
Beleg-/Buchungslinks und Mietscope. Es wird kein zusätzlicher Index ohne gemessenen Queryplan
eingeführt. Der Android-Harness sichert Querypläne und separate DAO-Zeiten.

## Regression und reproduzierbare Messung

- `BankLargeListComposeTest`: echte BankScreen, 1.250/5.000 gleiche Datumsgruppe,
  höchstens 50 komponierte Zeilen, letzte stabile ID per Lazy-Key erreichbar, unveränderte
  vollständige Transaktions-/Kontoobjekte nach Laden und Scrollen.
- `BankComputationPerformanceTest`: eingefrorene Gruppierungs-/Suchreferenz inklusive
  ungültiger Datumswerte, DE-/US-Locale, Umlaute, Komma, Filterzähler und Matching-Ergebnisse.
- `BankReceiptPreparedMatchingTest`: eingefrorene ursprüngliche Score-/Payment-Referenz,
  beide Suchrichtungen, Links, Klassifikationen, Gleichstände, Gründe, Umlaute, ungültige
  Daten und Betrags-/Datumsschwellen; `BankDeviceFixtureFlowTest` prüft beide Vorschlags-Flows
  mit genau den finanziellen Fixtures des nativen Tests.
- `BankReverseMatchingComposeTest`: echter BankScreen, Tool und Bank-Picker mit 1.250/5.000
  Buchungen und 30 Belegen. Eine Test-Liste verbietet Matching-Iteration auf Main unabhängig
  von Timer/JIT. Beide RED-Fälle scheiterten genau an diesem Threadvertrag; Treffer, Score,
  höchste Tie-ID, erste/50. Pickerposition sowie vollständiger DB-Snapshot werden geprüft.
  Der finale Test verlangt genau 31 Matching-Iterationen (30 Belege + ein Picker), auch nach
  einer Listensuche. Der native Robolectric-Dialog erhält wie bestehende Repository-Tests
  eine feste Fenstergröße. Die Diagnose zeigte davor bereits wartende Worker und
  `RobolectricIdlingStrategy.requestLayoutIfNeeded`, keine Matching-Schleife;
  [Diagnose](bank-performance-evidence/reverse-ui-diagnostic.json),
  [RED](bank-performance-evidence/reverse-ui-red.xml), [GREEN](bank-performance-evidence/reverse-ui-green.xml).
- `BankRentCandidateBatchTest`: eingefrorene bisherige Factory als Referenz, mehrere Monate,
  Objekt-/Einheitenscope, History, Self-exclusion und eine neue Batch nach Quellenänderungen.
- `BankPerformanceEmulatorTest`: 1.250/5.000 synthetische Buchungen, 40 Belege, Links,
  Mietzuordnungen und Originaldatei; vollständige Daten-Snapshots und Datei-Bytes nach
  Navigation und Activity-Neuerstellung; je drei Activity-Relaunch-/Listen-/Detailläufe.
  Deterministische Zeilenobergrenze plus Median-Budget von unter 2 s für Main-Heartbeat-Lücken.
  Separates Method-Sampling erfasst tatsächliche Produktionsberechnungen und markierte
  Workloads. Profiling läuft außerhalb der normalen Zeitmessungen.

Die dedizierte [Workflow-Datei](../.github/workflows/bank-performance.yml) kopiert **denselben**
Harness auf Base und Head des PR und verwendet API 35, Pixel 5, 2 Kerne, 2 GiB RAM,
512 MiB konfiguriertem AVD-Heap, SwiftShader und deaktivierte Animationen. Die Baseline zeichnet die alten
Zeilenmengen auf, ohne die neue Lazy-Obergrenze zu verlangen. Produktfehler bleiben Fehler;
beide Datengrößen werden versucht und der Gesamtjob bleibt bei Fehler rot.

Auf dem temporären Runner werden JSON-Messungen, `meminfo`, `gfxinfo`, ART-/Perfetto-Traces,
Querypläne und drei echte Prozess-Kaltstarts pro Größe erfasst (`am start -W`: **erste
Activity-Frame**, nicht vollständige Bankbereitschaft). Ein zusätzlicher Prozessstart wird
separat gesampelt. Zusätzliche Workloads umfassen Filter, Summen, umgekehrten Abgleich, Bank-Picker,
Vormonatszuordnung und Transferabgleich. Profiling ist nur im dedizierten Vergleich aktiv und belastet die normale
E2E-Suite nicht. Veröffentlicht wird ausschließlich `public/summary.json`: feste numerische
Felder für Zeiten, PSS/Heap, Frames und aggregierte Klassen-/Threadrollen. Rohprofile,
UI-Inhalte und Querypläne bleiben auf dem temporären Runner. Sieben Regressionstests prüfen
Auslassung privater Felder und fehlender/nichtnumerischer Messungen. Fehlende Werte werden
nicht als Nullzeiten oder Erfolg ausgegeben; der ursprüngliche Job-Exitcode bleibt erhalten.
`status=complete` bezeichnet vollständige Messfelder, keinen bestandenen Test: Auch nach
späterer Budget-/Profilingverletzung bleibt der Instrumentation-Exitcode ungleich null erhalten.
Profil-Slice-Wallzeiten sind inklusive, können überlappen und sind keine aufsummierbare CPU-Zeit.
Separat liefert Perfetto echte Scheduler-CPU-Zeiten für Main/Worker/Instrumentation/Other.
Der Filter berücksichtigt vollständige Cmdlines und den gekürzten Android-Prozessnamen;
fehlende passende Slices werden als fehlende Abdeckung ausgewiesen. Diese CPU-Summen
umfassen den gesamten Fall einschließlich Test-/Profilingarbeit, also unterschiedlich weit
abgeschlossene Baseline/Head-Abläufe; sie sind kein gleich langer UI-Vorher/Nachher-Benchmark.
Die fairen UI-Zeiten stammen aus den getrennten unprofilierten Drei-Lauf-Intervallen.
Die Activity-Relaunch-Metrik im Test läuft im warmen Prozess und wird separat benannt.
Die CI-Emulatoren sind vergleichbar, aber getrennte VMs; absolute Werte sind durch
Scheduling/JIT/GC beeinflusst. Keine Aussage über das konkrete Nutzergerät ohne dortige Messung.

Lokale fokussierte JVM-Reproduktion:

```bash
gradle testDebugUnitTest --tests com.example.ui.BankLargeListComposeTest --no-daemon
gradle testDebugUnitTest --tests com.example.ui.BankComputationPerformanceTest --no-daemon
gradle testDebugUnitTest --tests com.example.ui.BankRentCandidateBatchTest --no-daemon
```

Für Berechnungsvergleiche: zwei Warmups und drei Messungen je Funktion/Datengröße;
Median/Min/Max sowie ThreadMXBean-Allokationen. Die Mietmessung umfasst einen vollständigen
Vorschlags-Batch wie im ViewModel (vorher wiederholte Einzelaufrufe, nachher gemeinsamer Batch).
Die lokale JFR-Konfiguration muss vor/nach identisch bleiben.

## Prüfstand

Lokaler Prüfstand:

- Zunächst 311 fokussierte Bank-/Miettests; nach der Matcher-Vorbereitung 321 Bank-/Navigations-
  tests: bestanden, keine Fehler/Skips. Frozen Matcher-Referenz lief vor der Änderung grün.
- Der vollständige Java-21-Lauf führte 1.392 Methoden in 205 Klassen aus: 1.391 bestanden,
  eine Navigationstest-Methode griff vor Abschluss der neuen Hintergrundberechnung auf eine
  Bankzeile zu. Der tatsächliche Methodenausführungsprüfer bestätigt die SDK-34/35/36-Ausführung
  ohne Suite-Platzhalter; sein Erfolg bedeutet nicht, dass der zunächst rote Navigationstest
  bestanden hätte.
- Nach gezielter Synchronisierung auf die sichtbare Bankzeile bestanden alle 23 Tests der
  beiden Navigationsklassen. Die Navigations- und Persistenzassertions bleiben unverändert;
  Zeitlimits wurden nicht erhöht. Zwei Emulator-Siblingpfade warten nun ebenfalls auf die
  Zeile. [RED](bank-performance-evidence/navigation-async-red.xml),
  [GREEN](bank-performance-evidence/navigation-async-green.xml),
  [Zusammenfassung](bank-performance-evidence/navigation-green-summary.json).
- 23 Python-Tests der Repository-Skripte sowie Bash-Syntax und `git diff --check`: bestanden.
- Java 17: `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest` bestanden, Dauer 9m19s.
  [Lokaler Android-Prüfbeleg](bank-performance-evidence/android-local-validation.json).
  Der vollständige Unit-Lauf wird in `verify` auf dem finalen PR-Commit erneut ausgeführt;
  der lokale erste Vollsuite-Lauf wird ausdrücklich nicht als vollständig grün ausgewiesen.
- Finale lokale Java-21-Buildprüfung: `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`
  bestanden (6m23s), keine Lint-Fehler.
  [Finaler Android-Prüfbeleg](bank-performance-evidence/android-final-validation.json).
- Finale lokale Vollsuite auf Produktions-/Unit-Stand `9b06479`: 1.403 Methoden in 209 Klassen,
  keine Fehler/Skips, 23m17s; tatsächliche SDK-34/35/36-Ausführung ohne Suite-Platzhalter bestätigt.
  [Vollständiger Prüfbeleg](bank-performance-evidence/full-final-result.json).

Die GitHub-Läufe `verify`, `android-emulator-e2e` und `Bank performance comparison` werden
mit Commit-SHAs, Run-IDs und tatsächlichen Ergebnissen im PR dokumentiert.
Lokal fehlt `/dev/kvm`; Android-Instrumentation wird gemäß `AGENTS.md` auf GitHub ausgeführt.
Es wird nicht automatisch gemergt.

## Native Zwischenmessung und Fehlerklassifikation

Commit `43dd3c5`, Vergleichslauf [41968950382](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/41968950382):
Baseline 1.250: Prozessabbruch; 5.000: echter `OutOfMemoryError` mit 192 MiB App-Growth-Limit,
trotz 512-MiB-AVD-Einstellung. Keine vollständigen drei Baseline-Läufe, keine erfundene Startzeit.
Zwischenstand nach Virtualisierung: je vier komponierte Zeilen, erste Liste 1.726/1.080 ms
(1.250/5.000), Main-Lücke 528/373 ms, PSS 286.645/306.179 KiB. Nur der erste Lauf kam bis zu
diesen Werten; beide Detailprüfungen scheiterten nach unveränderten 10 s am Belegmatching.
Diese Zahlen sind keine bestandene Drei-Lauf-Messung.

Das zusätzliche native Sampling [41971658799](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/41971658799)
zeigte bei beiden Größen fertige Mietvorschläge (250/1.000), aber noch null Belegvorschläge.
Receipt-Matcher-/Regex-Samples auf Worker-Threads bestätigten wiederholte paarweise Textarbeit.
Darauf beruht die Vorbereitung von Features pro Eingabe. Der identische JVM-Workload sinkt damit
bei 5.000 × 40 von 894 auf 61 ms und von 1.819 auf 34 MB Allokationen; native Endwerte folgen im PR.

Android-CI [41968950299](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/41968950299):
`verify` bestanden einschließlich kompletter Units/Lint/APKs; E2E 70/72 bestanden, kein Skip.
Neben dem 5.000er Detail-Timeout scheiterte ein bestehender Mietdialog-Test im Gesamtlauf.
[Isolationslauf 41974221540](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/41974221540)
bestand unverändert auf `main` und `43dd3c5`. Keine Assertion oder Zeitgrenze dafür geändert;
Ursache im Gesamtlauf noch nicht bewiesen. Die vollständige Suite wird erneut geprüft.
