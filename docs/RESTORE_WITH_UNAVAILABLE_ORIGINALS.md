# Belegdaten trotz nicht erreichbarer Originaldatei wiederherstellen

Basis: main `106fac2f8aad6fe39bf2a1fdf1c7bedb9ada6e48` (PR #153).
Vergleich: Testversion 1300, Commit `820d9fdf9cd0c09954dd408106da973fbc80967d`,
Actions-Lauf `39023123422`.

## Ursache und Verhalten

Version 1300 lud die JSON-Dateien ohne Prüfung sämtlicher eingebetteter
Dateiverweise. Ihr Restore importierte Belegdaten und Originalreferenzen;
er lud dabei keine Originaldateien herunter. Die Vorschau zählte bereits eine
nichtleere Datei-ID als Hauptdokument und zählte auch als DELETED markierte
Indexeinträge. Ein erfolgreicher Datenimport belegte deshalb nicht die
Verfügbarkeit sämtlicher Originaldateien.

Die neuere allgemeine Referenzprüfung prüft alle eingebetteten IDs, bevor der
Restore gelöschte Indexeinträge filtern kann. Bereits ein Original mit HTTP 404
blockierte dadurch den gesamten Index beziehungsweise lesbare Belegmetadaten.
PR #153 korrigierte die irreführende Nullanzeige, ließ diese Sperre aber bestehen.

Ein eigener, ausschließlich beim Restore verwendeter JSON-Lesepfad unterscheidet
jetzt nicht erreichbare Originaldateien von fehlenden Belegmetadaten:

- Der JSON-Quelldatei müssen weiterhin geprüfte App-Ordner und Ancestor-Nachweise
  zugeordnet sein. Zugängliche eingebettete IDs müssen ebenfalls zum App-Bestand
  gehören. Fremde Inhalte werden nicht gelesen.
- Nur ein HTTP 404 der Original-ID selbst wird als Hinweis gesammelt.
  Fehlende übergeordnete Ordner, fremde Bestände, HTTP 401/403/503,
  Abbrüche und beschädigte Sicherungsstrukturen bleiben Fehler.
- Aktive Belegmetadaten müssen lesbar sein. Fehlende Metadatendateien gelöschter
  Indexeinträge dürfen als Historie fehlen; diese Einträge werden nicht importiert.
- Alle Belegdaten und Originalreferenzen bleiben unverändert erhalten. Es werden
  keine Ersatzoriginale erzeugt, Dateien umgebogen oder Originale aus anderen
  Belegen zugeordnet. Wiederholtes MERGE erzeugt keine zusätzlichen Belege.
- Die Vorschau zählt aktive Indexeinträge, lesbare Metadaten und erreichbare
  Hauptdateiverweise getrennt. Bei einem fehlenden Original lautet die Bestätigung
  „Belegdaten wiederherstellen“. Hinweise bleiben im Abschlussbericht sichtbar.
- Zusatzdaten verwenden denselben Restore-Lesepfad. Ihre Originalreferenzen
  bleiben erhalten, und der ViewModel-Bericht führt Hinweise auch aus diesem
  vorherigen Prüfschritt auf.

Der normale `downloadJson`-Pfad und alle Upload-/Änderungsprüfungen bleiben
unverändert streng. Eine tolerierte Referenz im lokalen Restore gewährt keinen
Zugriff auf diese Datei; spätere Dateioperationen prüfen ihre Berechtigung und
Ordnerzugehörigkeit erneut. Cloud-Sicherungen werden während des Restore nicht
bereinigt oder umgeschrieben.

## Prüfung beim Erstellen des PR

- Frisch kompilierter Kotlin-/Compose-Code: 80 gezielte Namespace-, Repository-,
  ViewModel- und Dialogmethoden bestanden.
- Zusätzlich 14 bestehende Erhaltungs-/Restore-Methoden bestanden, darunter der
  synthetische Bestand mit 70 Belegen und mehrfaches MERGE.
- Reproduktion gegen die Basis: Wiederherstellung mit nicht erreichbarem Original
  und Zusatzdaten scheitert dort an der allgemeinen HTTP-404-Referenzprüfung.
- Geprüft: Beträge, stabile Beleg-/Dokument-IDs, Hash, Dateigröße und fehlender
  Originalverweis bleiben beim wiederholten Import erhalten. Gelöschte Einträge
  werden nicht reaktiviert. Normale JSON-Lese-/Schreibzugriffe bleiben gesperrt,
  wenn sie aktuelle nicht erreichbare Originalreferenzen enthalten.
- Negative Kontrollen: fehlende aktive Metadaten, fremde Originale,
  fehlende Ancestors, HTTP 401/403/503, Cancellation und fehlerhafte Indexstruktur.
- Lokale Robolectric-Zusatzprüfung auf SDK 35; die im Repository ausdrücklich
  auf SDK 36 deklarierten ViewModel-/Compose-Tests wurden nicht umdeklariert.
  Dafür wurden ausschließlich externe temporäre Testkopien verwendet.
- `git diff --check` erfolgreich.
- Gradle kann hier keinen `FileLockContentionHandler` starten:
  `Could not determine a usable wildcard IP for this machine`.
  Vollständige JVM-Tests, Lint, APK-Bau und nativer API-35-Emulatorlauf werden in
  Android-CI geprüft. Diese CI-Ergebnisse stehen beim Erstellen des PR noch aus.

Alle Testdaten sind synthetisch. Kein echter Nutzerbestand wurde importiert,
kein Nutzergerät installiert oder deinstalliert und keine Drive-Datei geändert.
Die neue Software stellt kein tatsächlich fehlendes Originalbild wieder her.
