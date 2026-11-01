# Phase 11E – fehlende Original-Baseline

## Realer Befund und geprüfter Stand

Nutzerbericht für POCO X8 Pro / Android 16: 42,3 Sekunden, 129 Drive-API-Aufrufe, 23 Belege nicht versucht, null geschriebene Bytes, `ORIGINAL_BASELINE_MISSING`. Zwei parallele Metadatenprüfungen wurden als Folge abgebrochen. Das ist kein erfolgreicher Synchronisierungs- oder Performance-Nachweis.

Ausgangs-Head von PR #156: `2d9eaf2e2f680e302b2371330a64ccb6328fb056`; frisch erneut geprüfter Main und tatsächliche Base: `5d5e61437fcf7324bb5e0f6442669f777d8ddeda`. Branch bleibt auf ausdrücklichen Wunsch `fix/phase11e-drive-sync`, PR bleibt Entwurf. Netzwerk-`git fetch` scheitert am Sandbox-Proxy; Main und PR wurden über GitHub geprüft.

Der Code entspricht dem bereits geprüften internen Kandidaten 1401, CI 41324830937: Original-Package und genehmigtes Zertifikat, 1295 Unit-Methoden und 70 API35-Instrumentation-Tests bestanden. Der Prüfcode wurde mit 1401 eingeführt; der neue Gerätebericht kann diesen Fall deshalb bereits eindeutig benennen. Die tatsächlich auf dem Handy installierte APK/Signatur wurde nicht per ADB ausgelesen. Neue Berichte speichern den Versionscode **beim Lauf**, statt eine aktuelle App-Version rückwirkend alten Berichten zuzuschreiben. Nächster interner Buildcode: 1402; kein Release oder Merge.

Die Anzahl 23 ist mit der ausdrücklichen Nutzerangabe vereinbar: 22 erfolgreich wiederhergestellte Belege plus ein neu angelegter Beleg. Keine automatische Bereinigung, Zusammenführung oder Löschung; der neue Test bildet genau diese Konstellation ab und kontrolliert sämtliche alten IDs.

## Ursache und kleinster sicherer Nachweis

Aufrufkette: manueller Button → `ReceiptViewModel.syncAllToDrive` → Initialisierung → `withSyncReadProof` → `validateSynchronizationInventory` → Metadaten im Dreier-Lesebatch → Original-Fingerprint → fehlender Vergleichs-Hash. Die Bestandsprüfung findet vor Hauptbuch-/Stammdaten-/Beleg-Schreibvorgängen statt. Deshalb bedeuten 23 „nicht versucht“ nicht 23 fehlgeschlagene Uploads. Die Erreichbarkeit des Originals behebt den fehlenden Inhaltsvergleich nicht.

Altmetadaten können kein `originalDocuments` enthalten; der Fallback verwendet dann das lokale ManagedDocument. Auch dessen Hash kann nach einer Wiederherstellung fehlen. Bisher blockierte die Prüfung unabhängig davon, ob eine unabhängige, passende Originalkopie vorhanden war. Der neue Regressionstest scheiterte vor der Korrektur mit vorhandener lokaler Kopie und intaktem Drive-Original; die negative Kontrolle ohne Kopie bleibt gesperrt.

Ein fehlender lokaler Hash darf jetzt ausschließlich anhand folgender **kombinierter** Nachweise ergänzt werden:

1. Eindeutige bestehende Index-/Metadaten-/Beleg-/Dokumentbindung; keine neuen IDs oder geratenen Dateizuordnungen.
2. Gemeinsame Belegrevision und übereinstimmende fachliche Belegdaten. Gespeicherte Dokumentreferenz und lokaler Bestand stimmen einschließlich Dateiname, MIME, Ordner und positiver Größe überein.
3. Unabhängiger Inhaltshinweis: bereits gespeicherter Original-Hash oder lesbare lokale Originalbytes. Bei mehreren lesbaren Kopien dürfen ihre Hashes nicht widersprüchlich sein. Mehrseitige Bildpfade werden nicht geraten.
4. Tatsächliche Drive-Originalbytes über die bestehende Namespace-Sicherung lesen; SHA-256 selbst berechnen. Hash, Größe und Dateibindung prüfen, monotone Drive-Version vor/nach dem Lesen muss gleich und vorhanden sein. Ein vom Server neu gelieferter Hash allein genügt nicht als historische Baseline.
5. Erst nach Abschluss sämtlicher Bestandsprüfungen den fehlenden lokalen Hash in einer Room-Transaktion ergänzen. Compare-and-set über den vollständigen erwarteten Dokumentdatensatz; keine neue Zeile, kein REPLACE, keine Änderung an Verweisen, URI, Größe, Fachfeldern oder Schema. Bei lokalem Konflikt blockieren.

Die Gleichheit von lokaler Kopie und Drive beweist deren Übereinstimmung **zum Prüfzeitpunkt**, nicht die Unverändertheit seit einem unbekannten historischen Zeitpunkt. Ein vorher gespeicherter Hash ist der stärkere historische Nachweis. Fehlen beide unabhängigen Nachweise, kann dieser Fix den Fall nicht automatisch freigeben: `ORIGINAL_BASELINE_MISSING` bleibt bestehen. Größe, Dateiname, Kontoanmeldung und bloße Erreichbarkeit sind kein Inhaltsbeweis. Insbesondere wird keine Cloud-Datei erst heruntergeladen und anschließend als angeblich unabhängige lokale Kopie verwendet. Eine unbekannte historische Änderung bleibt unbekannt.

Während eines Laufs mit ergänzter Baseline wird eine nachfolgend erkannte lokale Originaländerung gesperrt und nicht zum Original-PATCH umgedeutet. Bestehende Drive-Originale werden durch diesen Nachweispfad ausschließlich gelesen. Neue, eigenständige Belege verwenden den vorhandenen Uploadpfad. Spätere bekannte Inhaltsänderungen und Konflikte bleiben durch die bestehenden Hash-/Versions-/If-Match-Prüfungen geschützt.

## Diagnose und Abbruch

Neue Unterschiede: fehlender unabhängiger Nachweis, widersprüchliche Originalbytes, widersprüchliche Referenzen/gemeinsamer Stand, Änderung während des Nachweises und konkurrierende lokale Dokumentänderung. Der kopierbare Bericht nennt den ursprünglichen Sicherheitskonflikt mit interner Beleg-ID und zählt dadurch abgebrochene parallele Prüfungen separat. Abgebrochene Geschwister sind keine zusätzlichen Originalkonflikte. Coroutine-Cancellation wird auch nach einer blockierenden Leseoperation vor einer Erfolgsmeldung geprüft. Nutzerabbruch ohne Primärkonflikt bleibt Nutzerabbruch.

## Analyse der API-Aufrufe

Die realen 129 Aufrufe sind Messwerte eines **gesperrten** Laufs. Der frühere vollständig kopierte 39,5-Sekunden-Bericht zerlegte seine ebenfalls 129 Aufrufe in Initialisierung 41 (13 list, 28 get) und Inventar 88 (2 list, 86 get). Für den neuen 42,3-Sekunden-Bericht liegt nur die Gesamtzahl vor; diese ältere Phasenaufteilung wird nicht als neue Einzelmessung behauptet.

Die initiale Index-Lektüre prüft bereits alle referenzierten Original-/Metadaten-IDs und ihre Namespace-Vorfahren; anschließend werden im ersten Dreierbatch die tatsächlichen JSON-Inhalte samt Byte-/Versionsnachweisen gelesen. Aufrufe vor dem ersten Beleg-Upload sind deshalb erwartbar. Wiederholungen umfassen unterschiedliche Zwecke: Namespace-Nachweis, Inhalt, frische Versionsbedingung und Abschlussprüfung. Der vorhandene laufgebundene Cache teilt positive Vorfahren-/Original-/JSON-Nachweise; keine neue Cache-Architektur und keine Abschaltung von Aktualitätsprüfungen.

Der erste sichere Baseline-Aufbau kostet bei vorhandener unabhängiger Kopie zusätzliche Original-Lektüre und frische Nachprüfung. Diese sind einmalig und erforderlich. Ohne unabhängigen Nachweis wird keine zusätzliche Original-Lektüre zum blinden Lernen eines Hashs begonnen. Nach erfolgreicher Ergänzung kann der Folgelauf die vorhandene inkrementelle Prüfung nutzen: kein Originaldownload und kein Original-Neu-Upload bei unverändertem Bestand. Das ist die gezielte Ersparnis; eine allgemeine Reduktion der 129 realen Aufrufe wird ohne neuen Gerätetest nicht behauptet. Die voneinander getrennten Initialisierungs- und Sync-Namespace-Prüfungen bleiben in diesem kleinen Fix erhalten; deren Zusammenlegung erfordert eine eigene Nachweisprüfung und ist hier bewusst nicht umgesetzt.

## Tests, Messungen und Grenzen

Sämtliche neuen Dateien und HTTP-Antworten sind synthetisch. Keine produktiven Drive-Daten gelesen oder verändert. Lokale Robolectric-Ausführung verwendet externe SDK35-Testkopien, Produktkompilierung gegen SDK36 und echte Room-KSP-Generierung; eingecheckte Annotationen bleiben SDK36. Reguläres lokales Gradle erneut am dokumentierten wildcard-IP/FileLockContentionHandler blockiert. Vollständige CI-Ergebnisse mit Commit/Run-ID werden im bestehenden PR ausgewertet; kein lokaler nativer Emulator.

Abgedeckt: passender lokaler Altbestand ohne Hash, gespeicherter Hash bei fehlender lokaler Datei, unbekannter Cloud-only-Altbestand, gleich große abweichende Originalbytes, falsche Dateibindung, Cloud-Versionswechsel beim Lesen, konkurrierende lokale Änderung, Abbruch, ursprünglicher Konflikt gegenüber zwei Folgeabbrüchen, Wiederaufnahme nach Indexfehler/VM-Neustart sowie 22 Altbelege plus ein neuer Beleg und identischer Folgelauf. Bestehende Hashänderungs-, Login-, Restore- und Finanzregressionen bleiben erhalten.

Tatsächlich bestanden: **60 gezielte Tests** einschließlich zehn neuer Baseline-/Konfliktfälle; Kotlin-/Compose-Kompilierung und neue Room-KSP-Generierung bestanden, Schema unverändert. Zwei neue Positivtests liefen zunächst auf dem alten Repository-Pfad rot (3er-Fall und 22+1-Fall). Die bisherigen 58 Fälle bestanden vor den letzten beiden Ergänzungen ebenfalls vollständig. **107 + 117 angrenzende Regressionstests bestanden** nach der letzten Produktkompilierung, insgesamt 284 lokale Methoden. Vollständige CI wird separat im PR nachgetragen.

Reproduzierbare Einzelmessungen ohne künstliche Netzwerkverzögerung, synthetischer 22+1-Bestand mit genau einer fehlenden Baseline und passender lokaler Kopie:

| Lauf | Ergebnis | ms | API | list | get | generateIds | create | update | Originale neu / ersetzt / heruntergeladen |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Vorher, alter Repository-Pfad | gesperrt, keine Uploads versucht | 88 | 96 | 15 | 81 | 0 | 0 | 0 | 0 / 0 / 0 |
| Nachher, vollständige kalte Sicherung plus ein neuer Beleg | erfolgreich | 575 | 1295 | 380 | 842 | 25 | 25 | 23 | 1 / 0 / 1 |
| Identischer Folgelauf mit 23 | erfolgreich, No-Op | 185 | 269 | 34 | 235 | 0 | 0 | 0 | 0 / 0 / 0 |

Der Vorher-Lauf endet nach Initialisierung 39 und Inventar 57 API-Aufrufen im ersten Dreierbatch. Nachher prüft das vollständige Inventar 98 Aufrufe; anschließend werden unter anderem die im kalten Fixture noch fehlenden Immobilien-/Dokumentordner und Stammdaten gesichert. Die Differenz 1295 zu 96 bedeutet daher **keine Performanceverschlechterung bei gleicher erledigter Arbeit**; sie vergleicht erfolgreichen Gesamtauftrag und früh blockierten Teilauftrag. Ebenso sind 1295 zu 269 nicht allein durch Hash-Ergänzung vermiedene Aufrufe: Der kalte Erstlauf erledigt zusätzliche Ordner-/Sicherungsarbeit. Das laufgebundene Cache-/Prüfmodell bleibt bestehen.

Konkret entfällt nach einmaligem Nachweis für dieses Original die erneute Nachweis-Lektüre: Medien-GET und zwei frische Original-Metadaten-GETs, also **drei GETs bei bereits geprüften Vorfahren**; Abschluss- und Versionsprüfungen bleiben aktiv. Zusätzliche Metadaten-/Indexaktualisierungen des Erstlaufs entfallen beim No-Op ebenfalls, ohne sie pauschal als ausschließlich durch diesen Fix eingespart auszugeben. Historischer 3er-Test: vorher 58 Aufrufe/56 ms gesperrt, nachher 864/89 ms erfolgreiche kalte Gesamtsicherung, Folgelauf 144/32 ms ohne Writes oder Originaldownloads. Die negative Kontrolle ohne Kopie bleibt bei 58 Aufrufen und null Writes gesperrt.

[Maschinenlesbare Einzelwerte](phase11e-evidence/baseline-recovery-synthetic.json). JVM-Zeiten ohne künstliche Netzwerkverzögerung sind keine realen Google-/Android16-Latenzen. Der reale Nachher-Messwert fehlt weiterhin.

**Praktische Abnahme OFFEN.** Für die Abnahme ist ein echter erfolgreicher POCO-Lauf und danach ein unveränderter Folgelauf mit neuen kopierten Berichten nötig. Auto-Backup bleibt aus. Update nur über bestehender Installation, keine Deinstallation, kein erneutes Restore und keine Cloud-Bereinigung zum Umgehen einer Sperre. Wenn der unabhängige Originalnachweis auch auf dem Handy fehlt, bleibt die Sperre sachlich korrekt; für eine weitere Freigabe ist eine belastbare Originalquelle bzw. gesonderte fachliche Konfliktklärung erforderlich.
