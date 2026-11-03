# CI41614494878: zwei nachgewiesene Emulator-Testvorbereitungsfehler

Head8e4b49dca9c6577fdff2e107563ac87fd6c5d5ff: verify grün,1382 echte
Unitmethoden/202XML-Dateien, null Fehler/Skips/Platzhalter. Native:70 Methoden,
2 Fehler,0 Errors/Skips. ZIP-Digest der heruntergeladenen nativen Nachweise:
`c2db6ad3095e790dd71607dec2d699dec97fc6ba427445f4209ebdfc88937653`.
Dieser Gesamtlauf ist fehlgeschlagen und wird nicht als Freigabe gezählt.

## Darlehenseditor

LoanDateComponentEmulatorTest scheitert beim zweiten Editoraufruf am sofortigen
einzelnen Start-Feld-Lookup. Der Stack führt über fetchOneOrThrow/printToString
zu einer Semantikbeobachtung auf dem Instrumentation-Thread während der
Dialogregistrierung. Der Fehlerscreenshot zeigt den anschließend vorhandenen
Editor mit02.01.2026.

Korrektur ausschließlich im Test: vor beiden gleichartigen Editor-Feldabfragen
innerhalb der bestehenden10s auf genau einen registrierten Start-Eingabeknoten
warten. Alle deutschen Datums- und gespeicherten ISO-Assertions unverändert.
Kein zusätzlicher Klickversuch und kein pauschaler Retry.

## Schriftgröße1,5

Phase10AIdentity15EmulatorTest scheitert bereits beim Warten auf die geforderte
Schriftgröße. Der zugehörige WindowManager-Dump belegt mGlobalConfiguration und
Activity-mFullConfiguration/mLastReportedConfiguration mit1,0. Logcat zeigt die
verspätete Rücksetzung des vorherigen1,3-Tests auf1,0. settings-put hatte bereits
zurückgegeben, bevor die Systemkonfiguration angewendet war. Die von mehreren
Tests überschriebene environment-font-Datei identifiziert nicht den einzelnen Test.

Korrektur ausschließlich im geschützten, ausdrücklich disponiblen Testemulator:
nach Initialisierung der Compose-Testregel zuerst vorherige Rücksetzung in
Settings und tatsächlicher WindowManager-Konfiguration bestätigen; dann den
gewünschten Wert einmal setzen und beide Quellen bestätigen, bevor die Activity
startet. Beide Bedingungen teilen dieselbe10s-Deadline. Kein längeres Zeitlimit,
Sleep, Retry, Ressourcen-Override oder Produktionspatch. Hardware-/Debug-/Disposable-
Guards, Original-Schriftwiederherstellung und Testdatenisolation bleiben erhalten.

## Validierung und Grenzen

Unabhängige Agent-Skills-Prüfung der Stacks/Screenshots und des Testpatches;
SDK36-Kompilierung beider geänderter Tests samt abhängigen Identitäts-/Finanzfixtures
erfolgreich. git diff --check bestanden. Kein lokaler Emulatorlauf ohne KVM
behauptet. Die neue vollständige CI muss diese Korrektur bestätigen.

Produktcode/Version1406 bleiben exakt auf dem Codex-geprüften Stand8e4b49d.
Der Follow-up ändert nur Android-Testbereitschaft und Nachweise. Die Diagnose
ist weiterhin keine nachgewiesene Reparatur des realen1405-Abschlusswiderspruchs.
