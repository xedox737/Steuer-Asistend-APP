# ImmoPilot – Fehlervergleich Test-App vs. Original

Prüfbericht vom 25.10.2026 · Repository xedox737/Steuer-Asistend-APP · Referenz: Draft-PR #143

## 1. Zusammenfassung und Entscheidung

**E11A-02 und E11A-03 sind in beiden untersuchten Versionen tatsächlich reproduziert. Sie sind keine ausschließlich durch die Test-App eingeführten Fehler.** Der wichtigste Befund ist ein dauerhafter Zuordnungsdatenverlust bereits während des ersten erfolgreichen Beleg-Uploads: ManagedDocument.unitId wird null, Receipt.unitId bleibt erhalten. Der Fehler entsteht vor Download, MERGE und Restore. Nach erneutem Sync, zwei MERGEs und erneutem Öffnen der SQLite-Datenbank sind weiterhin 60 Dokumente betroffen. Alle 60 Originaldateien bleiben hashgleich.

Die aktuelle Test-App trennt ihre neue Drive-Struktur im geprüften Normalablauf. Die Original-App findet die neuen Testdateien im normalen Suchablauf ebenfalls nicht. Das ist jedoch keine allgemeine Zugriffssperre der Original-App: Mit ausdrücklich eingespeisten fremden IDs oder einem manipulierten lokalen Kategorie-Mapping liest, verändert, verschiebt und löscht der unveränderte main-Client fremde synthetische Dateien. Bereits früher unter Produktroot und Produktkennung geschriebene Testdateien werden von main weiterhin als Produktbestand gefunden. Ob reale Bestände solche Altvermischungen enthalten, wurde nicht untersucht.

**Die Original-App ist für produktiven Drive-Sync von Belegen mit Wohneinheiten- bzw. bestätigten Dokumentzuordnungen derzeit nicht zuverlässig genug. Eine uneingeschränkte produktive Empfehlung ist nicht vertretbar.** Daraus folgt kein Beweis, dass sämtliche lokalen Funktionen fehlerhaft sind. Ein tatsächlicher Schaden in den Daten des Nutzers ist nicht nachgewiesen, aber unter den reproduzierten Voraussetzungen möglich.

Es wurden keine fachlichen Reparaturen, Migrationen, Datenbereinigungen oder Rückspielungen realer Backups vorgenommen. Keine produktiven Daten wurden gelesen oder verändert. Kein PR wurde gemergt. Die Bereitstellung erfolgt auf einem separaten Audit-Branch; die geprüften App-Stände bleiben unverändert.

## 2. Geprüfte Stände und Grenzen

| Merkmal | Original-App | Test-App |
| --- | --- | --- |
| Tatsächlich geprüfter Commit | 69f35772b6480a958ca7479b024dbd4d8bd42fa1 | 8ddd556061d6d9a5ed607163aff42dbf3578f8c1 |
| Branch / Quelle | origin/main | test/phase11a-practical-device-drive-acceptance, PR #143 |
| JVM-App-Identität | com.aistudio.steuerassistent.vnhqkp | com.aistudio.steuerassistent.vnhqkp.phase11a |
| Drive-Root | Steuerassistent Belege | ImmoPilot Phase11A Testdaten |
| Systemordner | _BelegApp-Daten | _BelegApp-Daten |
| Drive-Kennung appProperties.appName | ImmobilienBelegApp | ImmoPilotPhase11ATest |
| Ausführung | Unveränderter main-Code, JVM-Debug-Test | Unveränderter Test-Head, JVM-Debug-Test |

Beide Branches wurden vor Beginn explizit abgerufen; PR-Head und Draft-Status wurden über GitHub geprüft. Der Vergleich betrifft diese Quellstände, nicht eine unbekannte auf einem Telefon installierte Store-/Release-APK. Die Produktversion im Auftrag wird durch origin/main definiert. Ein APK-Fingerprint des real genutzten Produktgeräts liegt nicht vor.

Testumgebung: JDK 21, Gradle 9.3.1, Robolectric 4.16.1, SDK 35 / Android 15, je Fall eine neue ausschließlich synthetische Room-Dateidatenbank in einem temporären JVM-Context. Der originale GoogleDriveClient und DrivePersistenceRepository werden ausgeführt; nur HTTP wird im Arbeitsspeicher abgefangen. Die private finale Client-Referenz von main bleibt unverändert. Die temporäre Interceptor-Liste wird nach jedem Fall zurückgesetzt. Es gibt keinen echten Google-Account, kein echtes Cloudlaufwerk, kein installiertes Produkt-Package und keine Live-Zugangsdaten.

Ein zusätzlicher Aufruf testReleaseUnitTest scheiterte an der unveränderten Buildkonfiguration: Task nicht vorhanden. Keine Release-JVM-Ausführung wird als bestanden gewertet. Die fehlerhaften Syncfunktionen sind gemeinsamer main-Quellcode ohne Debug-Sonderzweig; der ausgeführte Produktvergleich bleibt trotzdem ausdrücklich ein JVM-Debug-Nachweis. Ein erneutes Öffnen der Room-Datenbank ist kein vollständiger Android-Prozess-Kill.

## 3. Vergleichstabelle

Bewertungen beziehen sich ausschließlich auf die daneben genannten Voraussetzungen. „Bereits behoben“ bei E11A-01 beschreibt den automatisierten Nachweis im aktuellen Test-Head; ein echter Gerätenachtest bleibt offen.

| Fehler-ID | Test-App | Original-App | Ursache | Priorität | Empfehlung |
| --- | --- | --- | --- | --- | --- |
| E11A-01, neue normale Struktur | Bereits behoben | Nachweislich nicht betroffen im geprüften Normalpfad | Getrennter Root und eigene Testkennung; reguläre Suchen finden den anderen Bestand nicht. | P1 | Neue Test-App verwenden; echten Root kontrollieren. Keine Aussage über Altvermischung. |
| E11A-01, fremde IDs / Altvermischung | Nachweislich nicht betroffen in den geprüften Guardfällen | Bestätigt betroffen unter der genannten Voraussetzung | main besitzt keine Namespace-Sperre für explizite fremde IDs; Mapping wird blind akzeptiert. Alte Testdateien mit Produktkennung sind nicht unterscheidbar. | P1 | Produktbestand nur lesend inventarisieren; ID-/Mapping-Härtung separat prüfen. Keine automatische Migration oder Löschung. |
| E11A-02 | Bestätigt betroffen | Bestätigt betroffen | syncReceiptToDrive verlangt lokale, nichtleere Quelldatei; keine Ausnahme für dateilose Fahrtbelege. | P2 | Fachlich zulässige dateilose Belege separat modellieren; Schutz für tatsächlich fehlende Originale erhalten. |
| E11A-03 | Bestätigt betroffen | Bestätigt betroffen | Neuer ManagedDocument-Konstruktor übernimmt unitId nicht; Default null ersetzt den bestehenden Datensatz. | P1 | Bestehende Zuordnung und bestätigte Metadaten im Beleg-Sync erhalten; danach vollständige Regression. |
| E11A-03, erweiterte Referenzen | Bestätigt betroffen beim vorbereiteten Beleg-Dokument | Bestätigt betroffen beim vorbereiteten Beleg-Dokument | Derselbe Neuaufbau verliert optionale Mieter-/Darlehens-/Sanierungsreferenzen und überschreibt bestätigten Typ, Datum, Titel und OCR-Metadaten. | P1 | Feldweise Erhaltungsregeln und nur lesende Bestandsprüfung; keine blinde Ableitung aus Receipt.unitId. |
| Reale Geräte-/Cloudfälle | Noch nicht abschließend prüfbar | Noch nicht abschließend prüfbar | Keine physischen Geräte, Live-OAuth- oder Google-Transportnachweise dieses Audits. | Prüfgrenze | Gesonderte praktische Abnahme nach fachlicher Reparatur. |

E11A-01 war ursprünglich ein Fehler der Testbereitstellung: Die alte Test-App verwendete Produktroot und Produktkennung. Das ist im aktuellen Test-Head korrigiert. Die zusätzliche bedingte main-Schwäche ist davon zu unterscheiden: Der normale Produktpfad ist nicht generell mit dem neuen Testpfad vermischt, aber bekannte/gespeicherte fremde IDs werden nicht technisch ausgeschlossen.

## 4. Reproduktionsbasis und Sicherheitsmaßnahmen

Die vollständige vorhandene Phase10B-/11A-ZIP wird unverändert wiederverwendet, keine vereinfachte Ersatzfixture. SHA256: b052a1ae4569196abb791375aa1fa01fe6a636a1d0082820acb576fa497ea638.

Portfolio: 5 Immobilien, 17 Wohneinheiten, 17 Mietverhältnisse/-historien, 120 Bankbuchungen, 70 Belege und 70 Dokumente, 10 Fahrten, 60 Originaldateien, Darlehen, fünf AfA-Datensätze, Sanierung und Mietzuordnungen. Fachliche IDs und ursprüngliche Dateihashes sind in beiden Läufen identisch. Die zusätzlichen Variantenprüfungen verwenden hiervon abgeleitete, ausdrücklich synthetische Einzelproben; sie ersetzen das Portfolio nicht.

Der Testaufbau prüft ZIP-Hash, alle 60 Dateihashes und Größen, frische Room-Datenbank und tatsächliches Package. Drive-IDs der Fixture werden vor Nutzung entfernt. Originaldateien liegen nur im temporären Testcontext. Die Daten werden mit vorhandenen Parsern und SupplementalDriveBackup.restorePayload in die neue synthetische Datenbank geladen. Der Phase11A-UI-Seed und sein Produkt-Package-Guard werden weder aufgerufen noch deaktiviert; es gibt keinen installierbaren Seed-Einstieg in die Produkt-App.

Das simulierte Konto enthält je nach Fall beide getrennten Roots, eigene Systemordner sowie synthetische Config-/Originaldateien. Normale Suchläufe werden von absichtlich manipulierten ID-/Mapping-Fällen getrennt bewertet. Datei-IDs, Bytes, Metadaten und Requestarten werden überprüft. Ein HTTP-Interceptor beantwortet sämtliche Requests selbst und ruft niemals den echten Transport auf.

Das Stressportfolio mit 21 Einheiten wurde in diesem neuen Vergleich nicht erneut durch den realen Syncpfad geführt. Frühere CI-Restoreprüfungen für 17/21 Einheiten sind nur ergänzende Referenznachweise, kein Ersatz für diesen Vergleich.

## 5. E11A-03 – technische Ursache und Schadenszeitpunkt

Betroffene gemeinsame Dateien: data/DrivePersistenceRepository.kt, data/DocumentModels.kt und data/ReceiptDatabase.kt unter app/src/main/java/com/example/. Einstieg: DrivePersistenceRepository.syncReceiptToDrive. Der relevante ManagedDocument-Konstruktor ist zwischen beiden Commits **bytegleich**; seine SHA256 lautet 910081810cd5a0d7472b86818082c747fe4b6720ee5afe6c04846a29f4f0ca70. Er beginnt in main bei Zeile 1882, im Test-Head bei Zeile 1885.

Die gesamte Syncfunktion ist nach Ersetzen ausschließlich der buildabhängigen appName-Konstante durch den Produktwert ebenfalls gleich. DocumentModels.kt, ManagedDocumentService.kt, ReceiptDatabase.kt und ReceiptViewModel.kt sind vollständig bytegleich. Das ist zusätzliche Ursachenstützung; der eigentliche Fehlernachweis sind die getrennten Ausführungen und gespeicherten Room-Zustände.

Ablauf: Nach erfolgreichem Original-Upload wird das bestehende Dokument gelesen. Anschließend wird für dieselbe stabile documentId ein neues ManagedDocument gebaut. propertyId und receiptInternalId werden gesetzt, unitId jedoch nicht. Das Modell hat unitId: String? = null. Der anschließende Repository-Upsert führt über den DAO zu INSERT/REPLACE. Dadurch ersetzt null die vorherige Einheitenzuordnung dauerhaft. Receipt.unitId wird separat serialisiert und bleibt korrekt.

Reproduktion in beiden Versionen:

1. Vollständiges synthetisches Portfolio in die neue JVM-Testdatenbank laden und Baseline sichern. Das Dokument receipt:e10b-receipt-1 besitzt eine gültige unitId.
2. Eigene simulierte Drive-Struktur initialisieren.
3. Nur e10b-receipt-1 durch den echten syncReceiptToDrive-Pfad synchronisieren. Der Aufruf liefert true.
4. Dokument erneut aus Room lesen: unitId ist null. Receipt.unitId ist unverändert. Bis hier wurde kein Restore gestartet.
5. Alle 70 Belege synchronisieren. 60 Originalbelege werden übertragen; ihre 60 Dokument-Einheitenzuordnungen fehlen. Die zehn dateilosen Fahrtbelege werden abgewiesen.
6. Sync wiederholen, Backuptransport zweimal, Restore-Snapshot nur lesen, zwei MERGEs ausführen und Datenbank schließen/neu öffnen. Fehlende Zuordnungen bleiben bestehen.

| Zustand | Betroffene Dokumente | Scope-Meldungen des strikten Checkers | Originalhashes |
| --- | --- | --- | --- |
| Baseline | 0 | 0 | 60 gleich |
| Erster erfolgreicher Upload | 1 | 4 | 60 gleich |
| Gesamtsync 1 | 60 | 240 | 60 gleich |
| Gesamtsync 2 | 60 | 240 | 60 gleich |
| Read-only Download des Snapshots | 60 | 240 | 60 gleich |
| MERGE 1 und MERGE 2 | jeweils 60 | jeweils 240 | 60 gleich |
| Datenbank erneut geöffnet | 60 | 240 | 60 gleich |

Die 240 Meldungen stehen für 60 betroffene Dokumente: RAW- und Room-Sicht sowie beide Seiten des strukturellen Selbstvergleichs. Sie sind nicht 240 verschiedene verlorene Dokumente. Der Selbstvergleich prüft Struktur und kann keine vollständige Backup-Vertragsgleichheit beweisen. Die zusätzlichen direkten Vergleiche prüfen stabile Receipt-Werte, Einheiten, Mietverhältnisse und Originalhashes über alle acht Zustände.

### 5.1 Mieter, Mietverträge und andere Metadaten

Die 17 Mietverhältnisse/-historien, Immobilien-/Einheitendaten und die fachlichen Receipt-Zuordnungen bleiben im vollständigen Portfolio unverändert. Auch Beträge, Datum, Bankdaten, Darlehensdatensätze, Fahrten und AfA-/Sanierungswerte der geprüften Domänen bleiben gleich. Eine allgemeine Beschädigung der Mieter- oder Mietvertragsstammdaten ist damit nicht reproduziert.

Eine eigenständige synthetische Mietvertragsdatei ohne receiptInternalId behält unitId und tenantReference nach zwei Aufrufen von syncManagedDocumentToDrive und zwei Supplemental-MERGEs. Dieser separate Dokumentpfad nutzt document.copy und ist im geprüften Szenario nachweislich nicht vom Beleg-Konstruktorfehler betroffen.

Anders ist ein Beleg-verknüpftes Dokument mit bereits vorhandenen optionalen Referenzen: Im gezielt vorbereiteten Datensatz gehen tenantReference, loanId und renovationReference verloren. Der Darlehensstammdatensatz selbst bleibt erhalten. Eine über ManagedDocumentService.confirmReview bestätigte Dokumentart MIETVERTRAG wird wieder RECHNUNG, das bestätigte Dokumentdatum wieder das Belegdatum; Titel, OCR-Text, AI-Konfidenz und weitere Statusfelder werden ebenfalls überschrieben. extractedFieldsJson bleibt in dieser Probe erhalten. Die Referenzen waren ausdrücklich synthetische Voraussetzungen; nicht jede normale Rechnung enthält diese Felder.

Es wird im normalen Portfolio kein Dokument auf eine andere Immobilie oder eine falsche konkrete Einheit umgebucht. Bestätigt ist der Verlust der Einheitenzuordnung bei erhaltener propertyId. Auswirkungen in ungeprüften UI-Filtern oder abweichend inkonsistenten Beständen sind damit nicht abschließend bewertet. Bestehende reale Daten können bereits betroffen sein, wenn dieser Syncpfad benutzt wurde; tatsächliche Betroffenheit und Ausmaß erfordern eine getrennte, nur lesende Bestandsprüfung.

### 5.2 Warum bisherige grüne Restoretests den Fehler nicht ausschließen

Die bestehenden Phase10B-/10C-/10D-Kontrollen prüfen Metadatenerhaltung und Relationen beim Restore über vorhandene Test-Gateways. Sie sind in beiden Versionen weiterhin bestanden. Sie durchlaufen jedoch nicht diesen erfolgreichen Original-Upload mit anschließendem Neuaufbau des ManagedDocument. Eine reine Restorefreigabe ist deshalb kein Nachweis der Unversehrtheit nach vorherigem echten Beleg-Sync.

Der Download-only-Schritt verändert den gespeicherten Supplemental-Zustand in diesem Test nicht. Beide MERGEs erhalten den bereits beim Upload beschädigten Zustand. Eine Wiederherstellung aus einer tatsächlich älteren sauberen Sicherung wurde hier nicht erprobt; kein Backup wurde automatisch auf reale Daten zurückgespielt. Insbesondere darf keine automatische Reparatur oder Rückspielung aus diesen Ergebnissen abgeleitet werden.

## 6. E11A-02 – dateilose Fahrtbelege und weitere Quelldateifehler

Betroffen: DrivePersistenceRepository.syncReceiptToDrive, main ab Zeile 1506 / Test ab Zeile 1509; identischer Fehlerzweig main ab 1858 / Test ab 1861. Der Zweig prüft vorhandene lokale, nichtleere Bytes. Bei fehlenden Bytes wird der Beleg lokal mit syncStatus ERROR und syncError „Keine lokale Quelldatei gefunden oder Datei ist leer.“ gespeichert; Rückgabe false.

Alle zehn absichtlich originalfreien Fahrtbelege der unveränderten Fixture werden in beiden Versionen abgewiesen. Ihre stabilen IDs stehen in portfolio-result.json. Es handelt sich nicht um zehn verlorene Originaldateien: Die Fixture soll nur 60 Originale für 70 Belege enthalten. Der Pfad kann in diesem Zustand keinen dateilosen Fahrtbeleg synchronisieren. Die UI-Funktion ReceiptViewModel.syncAllToDrive verlangt Erfolg für sämtliche Belege und kann damit keinen vollständigen Sicherungserfolg melden.

Zusatzproben mit einer gewöhnlichen Rechnung und (a) leerem Pfad, (b) nicht existierender Datei und (c) leerer PDF-Datei liefern in beiden Versionen ebenfalls false und dieselbe Fehlermeldung. Alle drei lokalen Datensätze, ihre Beträge und Einheiten-IDs bleiben erhalten. Ein unnötiges lokales Verwerfen oder Löschen ist damit nicht beobachtet. Das vollständige Verhalten eines frischen Full-Restores für alle zehn dateilosen Belege wurde in diesem Audit nicht nachgewiesen; die erfolgreiche MERGE-Prüfung in einen bereits vorhandenen 70-Belege-Bestand ersetzt das nicht.

Die technische Originalpflicht ist für eine gewöhnliche Rechnung mit tatsächlich fehlender Datei ein sinnvoller Schutz. Die offene fachliche Frage betrifft zulässige selbst erzeugte Fahrtbelege ohne Original. Ein späterer Fix darf diese Schutzprüfung nicht pauschal entfernen. Andere eigenständige Dokumentarten besitzen einen separaten Pfad; deren vollständige Fehlerkombinationen wurden nicht alle getestet.

## 7. E11A-01 – Cloudtrennung, Gegenrichtung und Altbestand

Der Normalfall wird mit beiden Roots in demselben simulierten Konto geprüft. Jede Version initialisiert ihren richtigen eigenen Root, liest den fremden Config-Inhalt nicht und verändert das gegenüberliegende Inventar nicht. Globale Original-Suchen anhand derselben Receipt-ID finden dank unterschiedlicher appName-Kennung keine neue fremde Test-/Produktdatei. Produktrootname und Produktkennung bleiben unverändert.

Die aktuelle Test-App enthält DriveEnvironment und den ausschließlich im Debug-Testbuild enthaltenen DriveNamespacePolicy. Er prüft aktuelle Elternketten, Configrollen, JSON-ID-Verweise und die tatsächlich zulässigen Roots. Die vorhandenen Namespace-Tests des letzten abgeschlossenen CI-Laufs 40196521890 prüfen zusätzlich weitere Lese-, Schreib-, Lösch-, Such-, Cache-/Move-/Shortcut- und Restore-Guardfälle. Das ist eine vorhandene CI-Referenz des exakt geprüften Test-Heads, keine neue Live-Abnahme.

Im neuen direkten Vergleich werden Lesen, JSON-Änderung, Löschen, Verschieben und ein fremdes gespeichertes Kategorie-Mapping getrennt erprobt. Ergebnis: Test-App weist alle fünf Fälle ab; main erlaubt sie mit ausdrücklich eingespeisten fremden IDs/Mappings. Das ist ein **bedingter** Zugriffsnachweis, kein Nachweis, dass die Original-App beim gewöhnlichen Start automatisch auf den neuen Testroot umschaltet. Nötige fremde Ancestry-Metadaten für den Test-Guard sind von einem Zugriff auf fremde Dateiinhalte zu unterscheiden.

Der main-Client cached Ordner anhand der bisherigen Schlüssel und übernimmt ein vorhandenes Mapping ohne Namespaceprüfung. Die Testvariante ergänzt Account-/Root-/Parent-Scope und erneute Eigentumsprüfung. Getrennte Android-Sandboxes teilen normale Preferences/Dateien nicht automatisch; aus der reinen Kontogleichheit folgt kein gemeinsamer lokaler Cache. Fremde IDs können jedoch bei Altvermischung, falschen gespeicherten Mappings oder ausdrücklich übergebenen Referenzen eine Rolle spielen. Ein Accountwechsel mit geteilten Dateien wurde nicht separat ausgeführt und wird nicht als zusätzlich bewiesener Fehler dargestellt.

Eine synthetische Alt-Testdatei mit Produktkennung im Produktroot bleibt für main auffindbar, für die aktuelle Test-App nicht. Die neue Isolation verschiebt, löscht, migriert oder entmischt Altdateien nicht. Kein echter Produkt-Drive-Bestand wurde inventarisiert. Die Aussage „Produktbestand ist in der Realität bereits sauber“ wäre daher unbelegt.

main wurde in diesem Auftrag weder lokal produktiv geändert noch auf GitHub fortgeschrieben. Die Produktkonfiguration im PR-Release bleibt laut Variantenimplementierung bei Produktroot/-kennung und reicht die bisherigen HTTP-Aufrufe weiter. Testroot/-kennung/-Hinweis und Guard liegen im Debug-Quellensatz; der tatsächliche Release-Ausschluss wurde im bestehenden CI des Test-Heads geprüft. Diese Schutzkorrektur enthält keinen Fix für E11A-02/E11A-03.

## 8. Weitere Warnungen, fehlgeschlagene Prüfungen und offene Fälle

### 8.1 Strikter Preferencevergleich bleibt rot

Beide Vergleichsläufe führen 20 Methoden aus: sechs neue Charakterisierungsfälle und 14 bestehende Restore-/Relationskontrollen. Je Lauf bestehen 19, eine Methode scheitert am strikten Vergleich propertyUnitPrefs. Nach dem ersten MERGE gibt es 110 statt 102 Einträge: acht zusätzliche Namens-ID-Aliase für die acht Einheiten der primären Immobilie. Es entfallen keine Schlüssel und kein vorhandener Wert ändert sich. Alle hinzugefügten Werte verweisen auf bereits gültige stabile Einheiten-IDs.

Die Quelle ist saveWohneinheitenToPrefs: Neben den stabilen unitid-Schlüsseln wird property_<propertyId>_unit_<name>_id gespeichert. Die Phase10D-Kontrollen behandeln solche passenden Legacy-Aliase als Kompatibilität. Diese zusätzliche rohe Differenz ist in beiden Versionen reproduziert, aber kein nachgewiesener Verlust von IDs, Mietdaten oder Flächen. Sie wird nicht aus dem Checker oder den Ergebnissen entfernt. Die ursprüngliche strikte Assertion bleibt erhalten; der Gradle-Testtask bleibt deshalb rot/Exit 1. Lediglich die Evidence-/Datenbank-Reopen-Erfassung wurde vor diese abschließende Assertion gestellt, damit der erste Diagnosefehler weitere Nachweise nicht verhindert.

### 8.2 Bereits dokumentierte QA-/Bereitstellungsprobleme

| Beobachtung | Bewertung und Nachweisgrenze |
| --- | --- |
| Phase6B-Emulatorassertion vor asynchron geladener Sanierungsübersicht, erster CI-Lauf 40184584401 rot | Dokumentierter QA-Synchronisierungsbefund. Test-Branch ergänzt gezieltes Warten; spätere vollständige CI grün. Kein im Vergleich bestätigter Produkt-Navigationsfehler; der entsprechende Original-Emulatorfall wurde hier nicht neu ausgeführt. |
| Namespace-/Failure-Evidence zunächst unter falschem adb-pull-Unterpfad gesucht | Phase11A-QA-Helfer, im Test-Head behoben und im bestehenden finalen CI-Transport nachgewiesen. main besitzt diesen Helfer nicht. Kein Produktdatenfehler. |
| Seed kann bei Prozessabbruch Reste in Dateien/Preferences hinterlassen | Test-only Ladejournal blockiert erneutes Laden und zeigt Fehler. Dokumentierte Testvorbereitungsgrenze, kein Nachweis eines Produkt-Seedfehlers; normale Produkt-App besitzt den Seed nicht. Keine automatische Bereinigung vorgenommen. |
| Wechselnder CI-Debug-Signierer / ältere Test-APK nicht aktualisierbar | Bedingte Bereitstellungsgrenze. Native OAuth-Registrierung benötigt Package plus tatsächlichen APK-Fingerprint. Kein in diesem Auftrag nachgewiesener Fehler der normalen produktiven Release-Installation. |
| OAuth, Google-WebView-/Native-Anmeldewege und Remote-Console-Konfiguration | Beide realen Installationen noch nicht abschließend prüfbar. Keine Secrets gelesen oder committed; kein echter Login behauptet. |
| KSP-AWT-Hintergrundwarnung, Android-Analytics-home nicht beschreibbar | Lokale Toolchain-Warnungen, keine reproduzierten App-Datenfehler. main-Lint/Debug-Build trotzdem erfolgreich; fachliche Testbefunde separat ausgewertet. |

### 8.3 Alle neun praktischen Fälle

| Test-ID | Test-App | Original-App / Grenze |
| --- | --- | --- |
| P11A-DRIVE-01 | NOCH OFFEN | Echter Google-Transport und kompletter Portfolio-Rundlauf nicht geprüft. |
| P11A-DEVICE2-01 | NOCH OFFEN | Kein echtes zweites Gerät/Profil untersucht. |
| P11A-PICKER-01 | NOCH OFFEN | Keine echte Systempicker-Abnahme in diesem Auftrag. |
| P11A-VIEWER-01 | NOCH OFFEN | Kein externer Viewer auf physischem Gerät untersucht. |
| P11A-OFFLINE-01 | NOCH OFFEN | Kein echter Offline-Restore samt Retry ausgeführt. |
| P11A-INTERRUPT-01 | NOCH OFFEN | Kein echter Prozessabbruch während eines Restores. |
| P11A-MANUAL-01 | NOCH OFFEN | Kein vollständiger manueller Android-Portfolio-/Navigationsablauf. |
| P11A-RESTART-01 | Praktisch NOCH OFFEN | Vorhandener Test-Emulatorprozessnachweis und neuer Room-Reopen-Nachweis sind keine echte Original-Geräteabnahme. |
| P11A-RECREATE-01 | Praktisch NOCH OFFEN | Kein neuer Vergleich der drei Recreate-Screens beider realen Apps. |

Die fehlenden Nachweise sind nicht als bestanden oder als Fehler der Original-App gewertet. Die bereits grüne Test-Emulator-Sandboxprüfung betrifft eine synthetische Produktreferenz aus dem PR-Build, nicht eine untersuchte reale Original-Installation. Die neuen main-JVM-Tests schließen diese Quellcode-Vergleichslücke für die konkret geprüften Syncfehler, nicht für sämtliche Geräte-/OAuth-Eigenschaften.

## 9. Tatsächlich ausgeführte Tests und Ergebnisse

| Prüfung | Original-main | Test-Head |
| --- | --- | --- |
| Neue Vergleichsmethoden | 6 ausgeführt; 5 bestanden, 1 RAW-Assertion rot | 6 ausgeführt; 5 bestanden, 1 RAW-Assertion rot |
| Bestehende Phase10BReviewedDocumentRestoreTest | 1 bestanden | 1 bestanden |
| Bestehende Phase10DRestoreMetadataTest | 8 bestanden | 8 bestanden |
| Bestehende Phase10CPortfolioRelationRestoreTest | 5 bestanden | 5 bestanden |
| Primärvergleich insgesamt | 20 tatsächliche eindeutige Methoden, 19 bestanden, 1 fehlgeschlagen, 0 Skips/Errors | 20 tatsächliche eindeutige Methoden, 19 bestanden, 1 fehlgeschlagen, 0 Skips/Errors |
| Unveränderter Integrity Checker | Baseline grün; nach Sync rot: 240 Scope-Meldungen, 60 Originalhashes korrekt | Identisches Ergebnis |
| Neue Android-Emulator-Ausführung | Nicht durchgeführt | Nicht durchgeführt; vorhandene CI-Referenz separat |
| main lintDebug und assembleDebug | Bestanden, Java 17, 4m12s | Aktueller Head bereits im vollständigen Referenz-CI geprüft; kein neuer App-Build dieses Audits behauptet |

Die sechs neuen Methoden heißen:

- completePortfolioPinpointsUploadLossBeforeDownloadMergeAndPersistsAfterDatabaseReopen
- normalDiscoveryUsesOwnRootAndTagsWhileLegacyMixedProductFilesRemainProductData
- explicitForeignIdsAndPoisonedMappingsAreBlockedOnlyByTestFence
- missingAndEmptySourcesAlsoRejectOrdinaryReceiptsAndPreserveRows
- standaloneRentalDocumentKeepsUnitAndTenantThroughOwnSyncAndSupplementalMerge
- authoredReceiptLinkedReviewAndOptionalReferencesAreOverwrittenByReceiptUpload

Eine bestandene Charakterisierung bedeutet, dass der beschriebene Fehler bzw. Guardzustand reproduziert wurde. Sie ist keine fachliche Fehlerfreiheit. Der strikte Checker und die absichtlich unverändert beibehaltene RAW-Assertion melden ihre tatsächlichen Fehler. Keine Assertion wurde abgeschwächt, kein Test übersprungen. Der Quellvergleich zeigt keine Änderung an den untersuchten App-Quellensätzen oder an deren Buildkonfiguration.

Referenz des exakt geprüften Test-Heads: vollständige Android CI 40196521890, 1.130 Unit-/Robolectric-Methoden, 13 Namespace-Fälle, 29 Python-Fälle, 73 eindeutige Emulatorfälle plus ein zusätzlicher Native-UI-/Prozessfall. Dieser schon vorher abgeschlossene Lauf ist grün, ersetzt aber nicht die roten neuen Diagnosen dieses Audits. Auf dem Audit-Branch wurde keine neue vollständige Android-CI als bestanden behauptet.

## 10. Priorisierte Reparaturempfehlung – noch nicht umgesetzt

1. **Zuerst E11A-03 in Original und Testpfad reparieren (P1).** Der Neuaufbau eines vorhandenen Beleg-Dokuments muss bestätigte, fachlich gültige Einheiten-/Referenz-/Review-Metadaten erhalten. Nicht nur unitId ergänzen und andere Verluste übersehen. Normative Regressionen müssen den tatsächlichen HTTP-Syncpfad vor und nach Upload, Wiederholung, Backup, Download, MERGE/Full-Restore und Neustart prüfen.
2. **Möglicherweise betroffene reale Bestände separat nur lesend untersuchen.** Receipt-/Dokument-IDs, propertyId/unitId und vorhandene bestätigte Metadaten inventarisieren. Reale Schäden nicht aus Testcounts hochrechnen. Keine automatischen Zuordnungen, Updates, Migrationsskripte oder Rückspielungen ohne gesonderten Auftrag. Receipt.unitId allein ist keine sichere Quelle für eine blinde Reparatur absichtlich abweichender Dokumentzuordnungen.
3. **Cloud-Altvermischung / fremde gespeicherte IDs absichern (P1, bedingt).** Aktuelle Test-Isolation beibehalten, alte Root-/Kennungsdateien nicht automatisch übernehmen. Produkt-Guard und Account-/Rootvalidierung als eigene Härtungsphase beurteilen. Alt-Testdateien im Produktroot nur mit belegter Herkunft behandeln; keine pauschale Löschung oder Verschiebung.
4. **E11A-02 danach fachlich lösen (P2).** Zulässige dateilose Fahrtbelege vollständig sichern und auf frischem Ziel mit stabilen IDs wiederherstellen. Die Originalpflicht für wirklich fehlende/kaputte Rechnungsdateien erhalten. Kein bestätigter lokaler Datensatzverlust in diesem Audit.
5. **Danach reale Abnahme nachholen.** Tatsächliche APK-/OAuth-Konfiguration verifizieren, nur Testkonto/-daten verwenden, neues Testroot und unveränderten Produktbestand dokumentieren; beide Backups/Restores, Offline, Abbruch, Zweitgerät, Picker/Viewer und Prozessneustart ausführen. Auch 21-Einheiten-Stressbestand ergänzen.

Ausdrückliche Antworten:

- **Auch Original-App reparieren:** E11A-03 und E11A-02; für E11A-01 ist zusätzlich eine bedingte ID-/Mapping-/Altbestands-Härtung angezeigt.
- **Ausschließlich Test-App:** die historische falsche Test-Root-Konfiguration und Phase11A-QA-/Bereitstellungsthemen. Beide fachlichen Syncfehler sind gemeinsam. Kein neuer ausschließlich aktueller Test-App-Fachfehler ist nachgewiesen.
- **Bestehende Daten gefährdet:** dauerhaft gespeicherte Dokumentzuordnungen und bestätigte Metadaten bei E11A-03; bedingt fremde Cloudbestände bei falschen IDs/Altvermischung. Realer Umfang ungeprüft. Originalbytes im Test erhalten.
- **Reihenfolge:** Zuordnungs-/Metadatenerhaltung, nur lesende Bestandsprüfung und Cloudhärtung, dateilose Belegsicherung, anschließend praktische Gesamtfreigabe.
- **Produktive Zuverlässigkeit:** keine uneingeschränkte Empfehlung für den betroffenen Drive-Workflow. Lokale Nutzung ist nicht pauschal widerlegt, aber eine vollständige praktische Freigabe ebenfalls nicht nachgewiesen.

**KEINE PRAKTISCHE PORTFOLIOFREIGABE. Keine Reparatur ohne gesonderten Auftrag. PR #143 bleibt Draft und ungemergt.**

## 11. Nachweise, Quellverweise und Reproduktion

Primärnachweise befinden sich im beigefügten Evidence-ZIP: vollständige XMLs, alle acht Snapshots je Version, unveränderte strikte Diagnosen, sechs Ergebnisdateien je Version, Quell-Diffs/-Hashes und finale Test-/Buildlogs. Jeder enthaltene Nachweis hat einen SHA256-Eintrag im Manifest. Nur synthetische Daten; keine echten personenbezogenen Informationen oder Auth-Header.

Evidence-ZIP: ImmoPilot-Fehlervergleich-Evidence.zip. Dateien: 87. SHA256: 31b5910eee28263a21c34d64d9ba061b9c75ba963c367b265dbde18df284df8d.

Original-Portfolio-Run-ID: P11A-2026-10-25-COMPARE-c1a38a78-785c-40ec-a05e-6a957aab6da7.

Test-Portfolio-Run-ID: P11A-2026-10-25-COMPARE-2c64fbb5-e27b-4cdc-912f-1c837759a51e.

Wichtige Quellen (immutable Commit-Links):

- [Original: syncReceiptToDrive und ManagedDocument-Konstruktor](https://github.com/xedox737/Steuer-Asistend-APP/blob/69f35772b6480a958ca7479b024dbd4d8bd42fa1/app/src/main/java/com/example/data/DrivePersistenceRepository.kt#L1882)
- [Test: identischer ManagedDocument-Konstruktor](https://github.com/xedox737/Steuer-Asistend-APP/blob/8ddd556061d6d9a5ed607163aff42dbf3578f8c1/app/src/main/java/com/example/data/DrivePersistenceRepository.kt#L1885)
- [Original: ManagedDocument-Modell und DAO](https://github.com/xedox737/Steuer-Asistend-APP/blob/69f35772b6480a958ca7479b024dbd4d8bd42fa1/app/src/main/java/com/example/data/DocumentModels.kt#L41)
- [Original: ReceiptRepository](https://github.com/xedox737/Steuer-Asistend-APP/blob/69f35772b6480a958ca7479b024dbd4d8bd42fa1/app/src/main/java/com/example/data/ReceiptDatabase.kt)
- [Original: ReceiptViewModel.syncAllToDrive](https://github.com/xedox737/Steuer-Asistend-APP/blob/69f35772b6480a958ca7479b024dbd4d8bd42fa1/app/src/main/java/com/example/ui/ReceiptViewModel.kt#L1638)
- [Original: SupplementalDriveBackup.mergeManagedDocument](https://github.com/xedox737/Steuer-Asistend-APP/blob/69f35772b6480a958ca7479b024dbd4d8bd42fa1/app/src/main/java/com/example/data/SupplementalDriveBackup.kt)
- [Original: GoogleDriveClient, globale Suchen und Mappingcache](https://github.com/xedox737/Steuer-Asistend-APP/blob/69f35772b6480a958ca7479b024dbd4d8bd42fa1/app/src/main/java/com/example/api/GoogleDriveClient.kt)
- [Test: Debug-DriveNamespacePolicy](https://github.com/xedox737/Steuer-Asistend-APP/blob/8ddd556061d6d9a5ed607163aff42dbf3578f8c1/app/src/debug/java/com/example/api/BuildVariantDriveEnvironment.kt)
- [Test: Release-Delegation](https://github.com/xedox737/Steuer-Asistend-APP/blob/8ddd556061d6d9a5ed607163aff42dbf3578f8c1/app/src/release/java/com/example/api/BuildVariantDriveEnvironment.kt)
- [Test: aktueller vollständiger Phase11A-Bericht](https://github.com/xedox737/Steuer-Asistend-APP/blob/8ddd556061d6d9a5ed607163aff42dbf3578f8c1/docs/PHASE11A_PRACTICAL_ACCEPTANCE.md)
- [Vorhandener vollständiger Referenz-CI-Lauf](https://github.com/xedox737/Steuer-Asistend-APP/actions/runs/40196521890)

QA-Helfer und genaue Wiederholungsanleitung: .github/qa/phase11a-comparison/README.md. Der Komfort-Runner bindet die Tests ausschließlich über ein Gradle-Init-Script in zwei neue detached Worktrees ein. Beide App-Quellstände bleiben unverändert. Er setzt keine Erfolgsfreigabe und gibt bei tatsächlichen Testfehlern einen Fehlercode zurück.

Vorbereitungsfehler des neuen QA-Helfers wurden korrigiert: Der integrierte Kotlin-Testquellensatz musste über kotlin.directories angebunden werden; ein nullable SQLite-Pfad benötigte eine explizite Nichtnullprüfung. Diese anfänglichen Compile-/Harnessprobleme werden nicht als App-Befunde gezählt. Die abschließenden hier gezählten Läufe enthalten tatsächlich sämtliche sechs neuen Methoden und alle 14 Kontrollen. Nach dem ersten RAW-Aliasbefund wurde ausschließlich die Evidence-Erfassung vor die unveränderte abschließende Assertion gelegt. Keine fachliche Reparatur.
