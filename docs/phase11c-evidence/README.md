# Phase11C evidence – ergänzende lokale Prüfung

Dies sind lokale Diagnose- und Integritätsnachweise, keine vollständige Gradle-/CI-/Android16-Abnahme. Die neuen eingecheckten Regressionen bleiben auf SDK36. Nur eine temporäre Diagnosekopie wurde mit dem verfügbaren SDK35 ausgeführt.

- Base: `6f8f26f4055c21a0df994e52b970f3fecbf4889d`.
- Produktfix: `2b58e6aaab913acb270695512b7fe4b4cbef1cf4`.
- Isolierte Integration: `9afb546fea289012bbb43d503002e6748e482687`.
- Finale lokale Auswahl: 137 eindeutige Methoden aus 19 Klassen, 0 Fehler/Skips.
- Strenger HTTP-Portfoliovergleich: 60/60 Original-SHA256, keine Contract-/Relations-/ID-Verluste.

`local-verification-summary.json` enthält jede gezählte Methode und die Quellhashes. `direct-sdk35/` enthält die letzten jeweiligen XMLs und Logs. `direct-sdk35-attempt1/` und `direct-sdk35-attempt2/` enthalten vorherige fehlgeschlagene Versuche; deren Toolchain-/Testfixture-Ursachen sind im PDF erklärt. Rohlogs mit CloseGuard-Warnungen sind erhalten. Der neueste HTTP-Portfolio-Lauf wurde mit `GITHUB_SHA` an den tatsächlichen Produktfix gebunden.

`before-fix-pinned-base-reproduction-sdk35.txt` stammt aus einer erneuten Reproduktion, bei der die drei ursprünglichen Produktdateien per `git show` aus der festen Base und die 12 Testmethoden aus `aa137a5` gelesen und frisch kompiliert wurden. Ergebnis: 12 ausgeführt, 9 rot. Dieser Diagnosepfad verändert keine Repository-Quellen.

Die Skripte unter `tools/` dokumentieren den tatsächlich verwendeten Ersatzweg für die gesperrte Gradle-Umgebung. Sie benötigen die bereits vorhandenen Android-/Gradle-Abhängigkeiten, vorherige unveränderte App-/Testklassen unter `app/build`, JDK17 unter `/workspace/.setup`, Java21 unter `/usr/lib/jvm` und das SDK35-Robolectric-JAR. Sie sind bewusst keine portable CI-Abkürzung. Für die aufgezeichneten Direktläufe lagen die Skripte unter `/tmp/phase11c-*.py` und `DirectJunitEvidence.java` unter `/tmp`; Compiler-Ausgaben unter `/tmp/phase11c-direct`. Der Baseline-Reproduktionsweg verwendet ein separates temporäres Verzeichnis.

Reproduktion in dieser vorbereiteten Umgebung:

1. `python3 docs/phase11c-evidence/tools/recompile-baseline-sdk35.py` erwartet 12 ausgeführte Methoden und 9 Fehler gegen die unveränderte Base.
2. `python3 docs/phase11c-evidence/tools/compile-and-diagnose-sdk35.py` kompiliert die sechs geänderten Produktdateien einschließlich Compose und führt die 23 neuen Fälle ergänzend mit SDK35 aus.
3. `DirectJunitEvidence.java` nach `/tmp` kopieren. Die beiden `run-*.py` dokumentieren die weiteren ausgewählten Methoden, Java-Testklassen und das notwendige `java.io`-Modul-Opening. Sie schreiben Diagnose-XMLs in dieses Evidence-Verzeichnis; bestehende Nachweise vorher separat sichern. Die fehlgeschlagenen Zwischenversuche bleiben im gelieferten Archiv erhalten.

Der reguläre Abnahmepfad bleibt `docs/VERIFY_CHANGES.md`, einschließlich Java21/SDK36, vollständiger Suite, Ausführungs-/Portfolio-/Namespacewächtern, Java17-Lint/APK-Builds und beiden CI-Emulatorjobs. Die neue `phase11c-verify-connection.py` verlangt jede der 23 tatsächlich ausgeführten SDK36-Methoden ohne Fehler oder Skips. Aus den alten vorhandenen `app/build/test-results` wurde keine neue Vollabnahme abgeleitet.

Kein neuer CI-Lauf, keine APK-Freigabe, kein echter Google-Login und keine Installation/Deinstallation sind Teil dieser lokalen Nachweise.

Konsole-Textdateien sind lediglich auf genau einen abschließenden Zeilenumbruch vereinheitlicht; Meldungen und Testinhalte sind unverändert.
