# Phase 9B – Gemeinsame Zahlen- und Schreibverträge

Base: d52dd3d1d711bbebdb0c89cc177b371582b70fb9.
Branch: fix/phase9b-shared-number-write-contracts. Kein Merge.
Scope: A03/A04/A05/A10, N01/N02/N06 und ausschließlich Parseranteil N05.

## Gemeinsame Grammatik und Feldregeln

GermanNumberInput bleibt der einzige Textparser. Gültige deutsche Gruppierung und
technische Dezimalpunktnotation werden nach dessen bestehender Grammatik gelesen:
98.0 → 98, 12.345 → 12345, 1.234,56 → 1234,56. Falsche Gruppierungen, wissenschaftliche
Notation und nichtendliche Werte sind Fehler. Minus wird nie aus Eingaben gefiltert.
NumberFieldInput ist ein kleiner Feldadapter darauf: wirklich blank kann optional
sein; nonblank invalid niemals. Vorzeichen, positive Distanz/Fläche und Float-Zieltyp
sind Feldentscheidungen. Vorbefüllung mit deutscher Dezimalnotation erhält alle
relevanten Dezimalstellen und verhindert die Mehrdeutigkeit eines technischen 12.345.
Ungültige Altwerte erhalten einen sichtbaren deutschen Korrekturtext.

## Ursachen und ersetzte Eingänge

- A03 BankRulesFeature: nullable lokale Kommakonvertierung ersetzt; ungültige
  nonblank Grenzen schließen den Dialog nicht und werden nicht als null gespeichert.
  ReceiptViewModel.saveBankRule prüft endliche, nicht negative Grenzen und min ≤ max
  vor dem DAO-Aufruf. Supplemental liest mit denselben fachlichen Grenzregeln.
- A04 PartialRent/ManualRent, manuelle Splits, Allocation-Edit und Zins/Tilgung:
  lokale replace/toDoubleOrNull-Parser entfernt. Bestehende BankAllocationPolicy,
  SplitPolicy/Service, Dedup, Privat/Transfer/Kaution und Projektionen bleiben erhalten.
- A05 Dokumentübernahme: bestätigter Rohtext bleibt Rohtext. DocumentNumberInput
  validiert bekannte Zahlenfelder mit GermanNumberInput/Feldadapter bzw.
  LoanInputValidation. Gesamte beabsichtigte Property-/Unit-/Loanwerte werden vor
  confirmReview geprüft; bei Fehler bleiben Dokument, Analysevorschlag und Fachwerte
  erhalten. Kein 1e3 → 13, kein ungültig → alter Wert als erfolgreicher Import.
- A10 Fläche für Betriebskosten-KI: keine erfundene 75-Vorbelegung oder Fehlerfallback.
  Deutsche positive Fläche erforderlich; ungültige Eingaben verhindern Request.
  Auch der direkte VM-Eingang prüft die übergebene und effektive Gebäudefläche.
- N01 Neu, Tacho, Standardstrecke und Korrektur verwenden die gemeinsame Grammatik.
  Rohtext einschließlich Minus bleibt sichtbar/im Draft. Ungültige nichtleere manuelle
  Eingabe blockiert auch bei vorhandener Route. Bestehende Quellenpriorität bleibt für
  valide Werte unverändert; direkte ungültige manuelle DistanceEvidence wird abgelehnt.
  saveStandardRoute akzeptiert ausschließlich endliche positive Kilometer.
- N02 NumericWriteBoundary prüft den tatsächlichen Float-Zielwert unmittelbar vor
  Persistenz. Endlicher Double mit Float-Infinity sowie Nicht-nullwert mit Float-Null
  werden blockiert. Keine erfundene fachliche Obergrenze. Betroffen: Anlageassistent,
  Unit-Edit, Mieterwechsel/Mietverlauf, NK/sonstige Kosten, Dokumentübernahme,
  Stammdaten-Restore und Firestore-Eingang. Batchwerte vor erstem fachlichen Write
  prüfen; ungültige Altwerte beim Lesen nicht in neue Schlüssel kopieren/bereinigen.
- N06 Wizard/Dokument/Restore nutzen LoanInputValidation einschließlich numerischer
  Safety-Grenze, Anteil und dateErrors. Zinsbindung nach geplantem Laufzeitende bleibt
  die bestehende Warnung, kein neuer harter Fehler.
- N05 Bestätigte AfA-Beträge verwenden die gemeinsame Grammatik. Gültige gespeicherte
  Werte werden präzise vorbefüllt; keine Änderung der Prognose oder AfA-Fachrechnung.

## Datenerhalt und Architektur

Room bleibt 34, keine Migration. Supplemental bleibt 15, Receipt-Backupvertrag 2.
Keine neue Backuparchitektur, keine automatische Datenbereinigung oder neue IDs.
Alle 9A-Verträge bleiben maßgeblich: beschädigt ≠ leer, Serializerfehler ≠ leere Liste,
Restore invalid ≠ missing, Cent-/Gutschriften-/Receipt-Relationen und MERGE/REPLACE_FULL.
Ungültige lokale Float-Altwerte bleiben erhalten und durch bewusste Eingabe reparierbar;
nach Reparatur wird der vollständige Backup-Payload geprüft, kein Datensatz ignoriert.
Drive bleibt optional. Kein Live-Firestore-Nachweis behauptet.

## Prüfung und Regression

GermanNumberInputContractTest vergleicht Geld/Fläche/Miete/Loan/Dokument/Distanz mit
gemeinsamer Parametermatrix, blank/invalid/Minus, Tausendern, technical prefill,
Float-Grenze und Darlehens-Safety-Grenze. Phase9BWriteBoundaryTest prüft direkte
VM-/Store-Eingänge, unveränderte Bestände bei Fehler, Batch-Restore, Altwert-Reparatur
mit anschließend vollständigem Backup und Standardroute-Reopen.

Android-Emulatorfälle nutzen produktive Compose-Dialoge/Screens in MainActivity,
produktive Room-/Preferences- und Booking-Grenzen. Matrix bei ~393 dp und FontScale
1.0/1.3/1.5: Bankteilzahlung, AfA-Gruppierung, Fahrtenbuch Minus/Tacho/Kostenbeleg/Reopen,
Wizard extreme Loan-/Floatwerte und bestätigter ungültiger Dokumentrohwert.
Screenshots liegen im bestehenden Emulator-Diagnoseartefakt. UI-Fehlertexte werden
auf Textüberlauf geprüft; betroffene Dialoginhalte sind bei Bedarf scrollbar.
Keine globale Navigation oder Designsystemänderung.

Vollständiger vorhandener Workflow prüft testDebugUnitTest, lintDebug, assembleDebug,
assembleDebugAndroidTest und connectedDebugAndroidTest. Alle 9A-/8A-/8B-/7A-/7B- und
vorhandenen Bank/AfA/Dokument/Gemini-Regressionsfälle bleiben aktiviert. Lokaler
Gradle/SDK/adb fehlen; tatsächliche CI-Ergebnisse werden nach Ausführung im PR und
Abschlussbericht dokumentiert, nicht vorab als bestanden ausgegeben.

## Grenzen und bewusst unangetastet

N03/Mietaggregate bleiben 9C; N04/AfA-Stichtag sowie A11/Kaufgutschriften/15%-Basis
bleiben 9D. Bankliste und DATEV-Export/Freigabe/Auditlogik unverändert. Kein neues
Login, Cloudmodell, Webprodukt, Abo oder Feature. Keine physische Geräte-, Live-KI-,
Drive- oder Kanzleiabnahme. Git-fetch scheiterte am Proxy; aktuelles main wurde über
GitHub frisch gelesen und der signierte Originalcommit samt identischem Baum verifiziert.
