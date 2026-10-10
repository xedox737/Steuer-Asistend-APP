# Phase 6A – Identität, Freigabe und Zahlungsfluss

## Basis und Umfang

- Ausgangs-main nach checkout, fetch und ff-only pull: `6dd65bc73f05d3f4b485701a4603dc90efaf1b36`.
- Branch: `fix/phase6a-identity-payment-consistency`; Ausgangsarbeitsbaum sauber.
- main-CI `37993153950`: verify und Android-Emulator erfolgreich.
- Vollständig gelesen: Arbeitsanweisungen, AGENTS, Projekt-/Code-/Prüfwegweiser,
  Martin-Weber-Retest vom **10.10.2026** (`ImmoPilot-Produkttest-Martin-Weber-main-6dd65bc.md`),
  Phase-5A-/5B-Dokumentation und PRs #116–120 sowie Emulatorworkflow und Testscript.
- Die beigefügte ältere PDF vom 08.10. ersetzt den aktuellen Retest nicht.
- Ausschließlich MW-01–MW-04. Keine MW-05–MW-07, Banklisten-Neugestaltung,
  Restore-/Import-Refactorings, neue Zahlungsdatenbank oder neue DATEV-Fachlogik. **Nicht mergen.**

## Ursachenanalyse vor Codeänderungen

### MW-01

`Receipt.wohneinheit` ist der gespeicherte sichtbare Text. `ReceiptInlineEditor` baut
die Auswahl aus `PropertyMetadata.wohneinheiten` auf und verändert ausschließlich
`editWohneinheit`. `receipt.copy` übernimmt dabei die alte `unitId`.
`ReceiptViewModel.updateReceipt` ruft `resolveReceiptUnitId` auf; dieser gibt eine
noch im Objekt existierende alte ID zurück, **bevor** er den neuen Namen prüft.
Beim Propertywechsel setzt die UI nur den Namen auf Allgemein. Der Resolver erkennt
diesen nicht leeren Allgemeintext nicht ausdrücklich als Entfernung der Einheit.

Miet-Ist (`RentTrackingLogic`), Einheitenkosten (`ImmobilienManagerFeature`) und
Dokumentenakte bevorzugen stabile IDs, während Belegdetail/-liste und Teile der
Steuerdarstellung gespeicherte Namen zeigen. `DatevCostCenterResolver` verwendet
die ID für `wohneinheitId`, aber den separat übergebenen Namen für den KOST2-Token.
Dadurch entsteht WE01-ID + WE02-KOST2. Advisor-Manifest liest die gemappten IDs.
Ein weiterer direkt nachgewiesener Zuordnungsfehler: `ReceiptRepository.indexReceiptDocument`
behält bei leerer Receipt-unitId die vorherige Document-unitId. Alle Originalanhänge
werden sonst bereits mit der Receipt-Zuordnung indiziert. Dieser **direkte MW-01-Folgefehler**
wird ohne Änderung der Originalbytes-/Restore-Semantik mitkorrigiert.

Vorgesehene Korrektur: explizite Auswahl stabiler ID im bestehenden Editor,
gemeinsame objektgebundene Auflösung und Ableitung des Namens aus dieser ID;
Allgemein entfernt die ID, Objektwechsel entfernt fremde ID. Legacy-Namen nur
eindeutig innerhalb derselben Property auflösen. Vor Export widersprüchliche
bestehende ID-/Namenszuordnung zur erneuten Prüfung ausschließen, keine stille
historische Neuverteilung. Umbenennung derselben ID erhält Identität.

### MW-02

`DatevApprovalInvalidationPolicy.hasRelevantChange` prüft Bruttocent, Datum,
Haupt-/Unterkategorie, Konto, Beschreibung, Eigenleistung, sichtbare Einheit,
Mieter und Positions-JSON. **propertyId und unitId fehlen.** Deshalb behält ein
freigegebener Allgemeinbeleg beim reinen Objektwechsel seine Freigabe.

Freigabe: OFFEN/FREIGEGEBEN; Prüfstatus u.a. UNGEPRUEFT, GEPRUEFT, KORRIGIERT,
ZU_PRUEFEN; Exportstatus u.a. ENTWURF, KI_VORSCHLAG, ZU_PRUEFEN, GEPRUEFT,
EXPORTBEREIT, EXPORTIERT, AUSGESCHLOSSEN. Die bestehende Invalidierung löscht
aktuelle bestätigte Aufteilungen/Vorschläge, setzt OFFEN/ZU_PRUEFEN/EXPORTBEREIT
und löst die aktuelle exportlaufId. Bestehende ExportAuditRun-Datensätze bleiben
erhalten; kein eigener Status für „nach Export geändert“ existiert. Diese
Semantik wird wiederverwendet, einschließlich Beschreibung als relevantem Feld.
Explizite Erst-/Neufreigabe unveränderter Quelldaten und Sync bleiben zulässig.

### MW-03

`LedgerPaymentPresentation.entries` kombiniert Nicht-Mietbelege mit
`RentPaymentProjection.receiptPayments` und `bankPayments`. Letztere stammen
ausschließlich aus bestätigten normalen MATCHED/PARTIAL-Bankmietzuordnungen
(CONFIRMED, USER_CONFIRMED/MANUAL). Sonstige Bankausgaben – auch ausdrücklich
NO_RECEIPT_REQUIRED – haben keinen Projektionspfad.

Bankstatus: OPEN, REVIEW, PARTIAL, MATCHED, NO_RECEIPT_REQUIRED; Klassifikation:
NORMAL, PRIVATE_IGNORED, TRANSFER; Review OPEN/DONE. Splits liegen bereits in
BankReceiptLink und BankRentAssignment (auch OTHER_EXPENSE), Darlehen separat in
BankLoanAssignment. Keine neue Persistenz nötig. RentPaymentProjection löst
stabile transactionId/assignmentId/receiptId/internalId-Beziehungen vor UI-Filtern
auf, mit Restabdeckung statt Betrag-/Datums-/Namensheuristik.

Vorgesehene Ergänzung im vorhandenen Ledger: bestätigte Immobilien-Bankausgaben,
bestätigte OTHER_EXPENSE-Anteile und stabile Beleglink-Abdeckung. Unbestätigte,
private, Transfer-, Kautions-, Darlehenstilgungs- und ungeklärte Restanteile nicht
als Aufwand zählen. Keine automatische Beleganlage, Freigabe oder Abzugsentscheidung.
Filter und Kennzahlen bleiben auf derselben abschließenden Entry-Liste.

### MW-04

`classifyBankTransaction` ändert die Transaktionsklassifikation und reviewState,
erhält aber Beleglinks und Belege. PRIVATE_IGNORED umfasst privat/ignoriert;
NO_RECEIPT_REQUIRED ist ein **separater Abgleichstatus**, keine Privatklassifikation.
Der Zahlungsfluss zählt Nicht-Mietbelege bisher ohne Bankprüfung vollständig.
`BankLinkedReceiptDatevPolicy` schließt verknüpfte PRIVATE_IGNORED/TRANSFER-Belege
bereits über dieselben Klassifikationen aus; die APPROVED-Ansicht benutzt diese Policy.

Vorgesehene Korrektur: gemeinsame stabile Link-/Klassifikationsabdeckung im Ledger;
bestätigte Sonderklassifikation unterdrückt den verknüpften Anteil, Rücknahme
stellt ihn genau einmal wieder her. Gemischte bestätigte geschäftliche Splits
werden anteilig gezählt; unzugeordnete/objektlose Restanteile nicht ergänzt.
Vorhandene DATEV-Ausschlussgründe machen den Freigabe-/Bankkonflikt prüfbar,
keine Daten- oder Historienlöschung.

## Prüfplan

Gezielte Resolver-/ViewModel-/DATEV-/Ledger-Regressionen, tatsächliche Room- und
Dokumentindex-Persistenz, Einheitwechsel/Objektwechsel/Privatkorrektur in echter
MainActivity mit Screenshots. Alle bestehenden 799 JVM-Tests einschließlich
Phase 5A/5B und alle acht vorhandenen Emulatorfälle bleiben aktiv.
`git diff --check`, `testDebugUnitTest`, `lintDebug`, `assembleDebug` und
`connectedDebugAndroidTest` über die bestehende CI. Lokal fehlen Gradle,
Android-SDK und adb; CI-Ausführung und Berichte werden abschließend geprüft.

Room 34 / Supplemental 14 unverändert vorgesehen. Navigation unverändert:
Bearbeitung im bestehenden Detail, Speichern/Abbrechen/Android- und App-Zurück
über vorhandene Wege. Keine globale Header-/Bottom-Navigation-Änderung.

## Umsetzung und Prüflauf

- Editor übergibt die explizite stabile Unit-ID; `ReceiptUnitResolver` wird von
  Editor, Speichern und bestehender Banklink-Zuordnung wiederverwendet.
  DATEV-Eingaben verwenden den aktuellen Namen derselben ID; widersprüchliche
  Altzuordnung wird zur Prüfung ausgeschlossen. Allgemein entfernt auch die
  nachgewiesene veraltete Unit-Zuordnung im Dokumentindex; Originaldaten bleiben erhalten.
- Bestehende Freigabeinvalidierung umfasst zusätzlich Objekt-/Unit-ID und
  bestätigte Konten-/Steueraufteilungen. Exportaudit bleibt unverändert erhalten.
- Vorhandene Ledger-Projektion ergänzt Bankausgaben und OTHER_EXPENSE/OTHER_INCOME-
  Splits. Klassifikation kommt aus `BankClassificationPolicy`, stabile Linkauflösung
  aus `BankLinkedReceiptDatevPolicy`; direkte receiptId-Splitbeziehungen zählen
  ebenfalls einmal. Keine Beleganlage oder automatische DATEV-Freigabe.
- Vollständige private/ignorierte Zahlungen zählen null; gemischte bestätigte
  Splits zählen ausschließlich objektbezogene geschäftliche Anteile. Objektlose
  und ungeklärte Reste werden nicht zum Aufwand ergänzt. Unaufgeteilte Darlehensraten
  bleiben ausgeschlossen; ein ausdrücklich belegter, bestätigter Geschäftsanteil
  (z.B. Zinsen) bleibt über seinen Beleglink erhalten. Dafür gibt es eine eigene
  Regression; es wird keine neue Zins-/Tilgungsberechnung eingeführt.
  Vorhandene DATEV-Ausschlüsse werden im Belegdetail sichtbar.
- Neue Tests: Resolver/DATEV, Freigaben, Bank-/Belegprojektion, tatsächliche
  Room-/Dokument-/Backup-Persistenz sowie drei echte MainActivity-Emulatorfälle.
  Androidfälle prüfen Speichern, erneutes Öffnen, Activity-Neustart, Abbrechen,
  Android-/App-Zurück, sichtbaren Freigabeentzug und Privatkorrektur/Rücknahme.
- Screenshots werden über den vorhandenen Emulator-Artefaktpfad hochgeladen;
  Workflow, Testscript und alle bestehenden Tests bleiben unverändert aktiv.
- CI-Berichte, Screenshots und die abschließende fachlich-technische Prüfung
  werden in [PR #121](https://github.com/xedox737/Steuer-Asistend-APP/pull/121)
  dokumentiert. Der vorhandene CI-Workflow prüft die vollständige Suite und den
  echten API-35-Emulator; maßgeblich ist jeweils der dort ausgewiesene Head-SHA.
