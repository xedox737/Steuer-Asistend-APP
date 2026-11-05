# Bankimport: Listen- und Berechnungsperformance

Ausgangsstand: `a4fd2982e76915f69e72a2f21295a971a8beb2ec` (`main`, am 04.11.2026 frisch abgerufen).
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
| Gruppierung | 17,27 → 1,54 ms | 30.697.240 → 124.720 B |
| Belegmatching (40 Belege) | 893,93 → 784,38 ms | 1.818.793.728 → 932.774.568 B |
| Mietkandidaten, kompletter Batch | 357,53 → 107,87 ms | 463.128.192 → 128.031.328 B |
| Suche | 26,80 → 22,33 ms | 13.873.272 → 6.723.888 B |

Die Zeitintervalle für Gruppierung, Matching und Mietkandidaten trennen sich bei 5.000
Buchungen (Min/Max stehen im XML). Bei 1.250 überlappen Such-/Matching-Zeitintervalle;
es wird dafür kein belastbarer Geschwindigkeitsgewinn behauptet. Die Suchänderung bleibt
wegen der deterministischen Allokationsreduktion von rund 52 % bei 5.000 Buchungen erhalten.
Die unveränderten Zähler dienen als Kontrolle; JIT/GC-Ausreißer verbieten den Vergleich
kleiner Unterschiede allein über Mittelwerte. Keine Timer-Assertions auf JVM-Mikrobenchmarks.

[Nachher-XML](bank-performance-evidence/computation-after.xml),
[JFR nachher](bank-performance-evidence/jfr-after-summary.json).
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
- Mietkandidaten verwenden eine `Batch` **pro Quell-Emission**. Indizes sowie Cache-Schlüssel
  enthalten stabile Objekt-/Einheiten-IDs und Mietmonat. Bei neuen Quellen wird die komplette
  Batch verworfen. Kein globaler Cache, kein TTL, kein persistierter Vorschlagszustand.
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
512 MiB Heap, SwiftShader und deaktivierte Animationen. Die Baseline zeichnet die alten
Zeilenmengen auf, ohne die neue Lazy-Obergrenze zu verlangen. Produktfehler bleiben Fehler;
beide Datengrößen werden versucht und der Gesamtjob bleibt bei Fehler rot.

Artefakte pro Revision: JSON-Messungen, `meminfo`, `gfxinfo`/Framestats, Method-Traces,
ein Perfetto-Trace pro Datengröße, Querypläne, Logcat, JUnit und drei echte Prozess-Kaltstarts
pro Größe (`am start -W`: **erste Activity-Frame**, nicht vollständige Bankbereitschaft).
Ein zusätzlicher Prozessstart wird separat mit `--start-profiler --sampling 1000` aufgezeichnet,
um auch ViewModel-/App-Initialisierung auf dem Hauptthread zu untersuchen. Dieser profilierte
Start fließt nicht in die drei normalen Startzeiten ein.
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

- 311 fokussierte Bank-/Miettests in 44 Klassen: bestanden, keine Fehler/Skips.
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
- 16 Python-Tests der Repository-Skripte sowie Bash-Syntax und `git diff --check`: bestanden.
- Java 17: `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest` bestanden, Dauer 9m19s.
  [Lokaler Android-Prüfbeleg](bank-performance-evidence/android-local-validation.json).
  Der vollständige Unit-Lauf wird in `verify` auf dem finalen PR-Commit erneut ausgeführt;
  der lokale erste Vollsuite-Lauf wird ausdrücklich nicht als vollständig grün ausgewiesen.

Die GitHub-Läufe `verify`, `android-emulator-e2e` und `Bank performance comparison` werden
mit Commit-SHAs, Run-IDs und tatsächlichen Ergebnissen im PR dokumentiert.
Lokal fehlt `/dev/kvm`; Android-Instrumentation wird gemäß `AGENTS.md` auf GitHub ausgeführt.
Es wird nicht automatisch gemergt.
