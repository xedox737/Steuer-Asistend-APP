# Phase 10D – Restore-Metadatenvertrag und CI-Ausführung

## Basis und Prüfstatus

Base: `e4bee4cb80869af3c3b76c7947fafe0cacd82396`, nach `git status`, Branchprüfung und erfolgreichem `git fetch origin` bestätigt.
Arbeitsbranch zunächst `test/phase10d-restore-contract-ci`. PR #139 bleibt unverändert Draft und wird nicht gemergt.
AGENTS.md, PROJECT_STATUS.md, VERIFY_CHANGES.md und CODE_MAP.md gelesen. Historische August-Anweisungen in CODEX_HANDOFF.md sind laut PROJECT_STATUS.md überholt.

Primäre zugängliche Phase-10B-Beweise: Beschreibung von PR #139 und Originalartefakt
12147050949 aus Run 39154690088 (Head `430c92eeb061036376cb1fb63614e04f6170ffee`).
Der separate Bericht Phase10B_Portfolio_E2E_Abnahme.md/.pdf ist bislang weder im Checkout noch im Artefakt enthalten; zusätzliche Quelle beim Auftraggeber angefragt.
Die zwei Portfolio- und Review-Tests werden unverändert aus dem QA-Head übernommen, ohne PR #139 zu mergen.

Lokaler Gradle-Versuch scheitert vor Projektkonfiguration an `SocketException: Operation not permitted` im FileLockContentionHandler.
Kein lokaler Testlauf wird als bestanden gezählt. Kein `/dev/kvm`; Android-Nachweis über CI.

## Q10B-01 – ursprüngliche Rohdifferenzen

Unabhängig aus den JSON-Dateien des Originalartefakts nachgezählt, für 17 und 21 Einheiten identisch:

| Feldgruppe | MERGE 1 | MERGE 2 | REPLACE_FULL | Vorläufige Bewertung |
| --- | ---: | ---: | ---: | --- |
| Receipt allocations | 70 | 70 | 70 | Consumerprüfung offen; kein pauschaler Ausschluss |
| Receipt proposals | 70 | 70 | 70 | Consumerprüfung offen; kein pauschaler Ausschluss |
| Originalreihenfolge in extractedFieldsJson | 70 | 70 | 70 | Explizite Reihenfolge betrifft Dateikette; nur beweisbar gleiche effektive Ordnung rekonstruierbar |
| ManagedDocument source | 60 | 60 | 0 | Herkunftssemantik prüfen; nicht blind als Cache einstufen |
| ManagedDocument updatedAt | 60 | 60 | 60 | B: Konfliktversion, keine technische Neudatierung zulässig |
| Namens-ID-Aliasse auf bestehende IDs | 8 | 8 | 0 | Nur bei identischer stabiler Identität und ohne abweichende Legacy-Nutzdaten rekonstruierbar |
| Gesamt | 338 | 338 | 270 | Keine Rohdifferenz entfernt |

Restore 1/2 und lokale MERGE-Wiederholungen jeweils RAW 0. Originalhashes 60/60 erhalten.
Gezielte Phase10DRestoreMetadataTest-Fälle prüfen tatsächlichen Reindex, Restore und Originaldownload sowie danach ältere/neuere Dokumentversionen.
Bis zum reproduzierten Befund keine Produktänderung. Golden-Assertions bleiben zunächst strikt rot.

## Q10B-02 – SDK/JDK-Analyse

AGP 9.1.1, Gradle 9.3.1, Robolectric 4.16.1; compileSdk 36.1, targetSdk 36.
Bytecode bleibt Java 11. Bisherige Gradle-/CI-JVM Java 17. Keine Java-Toolchain im Projekt deklariert.
Robolectric-Standard sdk=35 bleibt bestehen.

Zwei unabhängige Ursachen: SDK 36 braucht Java 21; zusätzlich schließt der globale
`robolectric.enabledSdks=35` sowohl explizite SDK-36- als auch SDK-34-Tests aus.
Im alten XML stehen alle fünf als `testcase` unter `classname="Gradle Test Run :app:testDebugUnitTest"`;
es sind keine Methoden dieser Testklassen. XML skipped=0 beweist hier keine Ausführung.

| Klasse | Deklariertes SDK | Testmethoden im Quelltext |
| --- | ---: | ---: |
| ExampleRobolectricTest | 36 | 1 |
| GreetingScreenshotTest | 36 | 1 |
| RestoreSafetyTest | 36 | 2 |
| AdvisorAnnualSummaryIntegrationTest | 34 | 11 |
| SyntheticTaxExportE2eTest | 34 | 3 |

Minimaler Weg: globale SDK-Erlaubnisliste entfernen, deklarierte SDKs unverändert lassen;
nur vor dem Unit-Test-Task Java 21 installieren/aktivieren, danach für Android-/Release-Build auf Java 17 zurückschalten.
Lint und Emulatorjob bleiben Java 17. Keine Filter, Ausschlüsse, SDK-Absenkung oder Testdeaktivierung.
Ein verpflichtender XML-Prüfer zählt echte Methoden anhand ihres vollständigen Klassennamens,
gleicht die Zahl gegen @Test im Quelltext ab und verlangt keine Fehler, Skips oder Platzhalter.
Er schreibt `app/build/reports/phase10d/test-execution.json` und schlägt bei fehlenden Tests fehl.

## Noch ausstehend

Reproduktionslauf, vollständige verbindliche Feldtabelle, Vertragshelper, Fehlerklassifikation,
abschließende Roh-/Vertragszahlen, alle Regressionen/Builds und vollständige Emulator-CI.
Room 34, Supplemental 15 und Receipt Backup 2 unverändert. Keine Freigabe vor vollständigem Nachweis.
