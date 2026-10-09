# Phase 5B – Workflow-Konsistenz F06–F17

## Grundlage und Grenzen

- Ausgangs-main nach erneutem Fetch am 09.10.2026: `ea5ac12e85b3eedfda4e8ba83d5219510e531145`.
- Branch: `fix/phase5b-workflow-consistency`; Ausgangsstatus sauber, keine Änderungen in main.
- Vollständig gelesen: beigefügte Arbeitsanweisungen, Martin-Weber-Produkttest vom 08.10.2026,
  Repository-AGENTS, Code-/Prüfwegweiser, Phase-5A-Dokumentation und Beschreibung von PR #116.
- #116 ist gemergt. Seine Konflikt-, Original- und Importregeln bleiben erhalten.
- Keine Banklisten-Neugestaltung, kein neuer Haupt-Navigationsweg, keine Änderung von
  DATEV-Kontierung, Kostenstellen, Freigabe, Jahreslogik oder Restore. Nicht mergen.

## Ursachenanalyse vor Codeänderungen

| Finding | Aktuelle Ursache und vorgesehene Korrektur |
| --- | --- |
| F06 | `LedgerScreen` übergibt ausschließlich Receipt-Flow; `LedgerPresentation` summiert Belegbeträge. Bestätigungen liegen getrennt in `bank_rent_assignments`, mit CONFIRMED/REVIEW und USER_CONFIRMED/MANUAL. Nur normale positive MATCHED/PARTIAL-Transaktionen mit gültigen bestätigten Zuweisungen zählen in `RentTrackingLogic`. Die dort vorhandene stabile Zuordnungs-/Beleglink-Abdeckung wird als gemeinsame lesende Projektion wiederverwendet. Finanzsicht unterscheidet Zahlungsfluss und freigegebene Buchungen; Bankanteile erhalten das tatsächliche Buchungsdatum, Mietkontrolle behält rentMonth. Keine Belegerzeugung oder neue DATEV-Freigabe. |
| F07 | `RentOverviewPresentation.year.unassigned` enthält nur bestätigte Mietbelege ohne Einheit. `Zahlungen prüfen` öffnet pauschal die Belegliste. Bankkandidaten aus bestehender Mietklassifikation/Zuordnungsprüfung fehlen. Gemeinsame Prüfliste für Belege, offene/review Bankkandidaten und Restbeträge, mit vorhandenen Detailwegen; PRIVATE/IGNORED/TRANSFER und sonstige klassifizierte Eingänge bleiben ausgeschlossen. Keine automatische Zahlungsbestätigung. |
| F08 | `TenantChangeDialog` prüft gültige Termine und Neubeginn nach Auszug, aber nicht Ende nach Altbeginn und übrige Perioden. `changeTenant` beendet alle endlosen Perioden ungeprüft. Historien-UI verwendet endDate-leer als AKTUELL, obwohl Zukunft/abgelaufene datierte Verträge abweichen. Gemeinsame Chronologieprüfung vor Schreiben, feldbezogene Fehler, eindeutiger betroffener Vertrag und aktueller Status über `currentAt`. Statusende wird gegen dieselben Vertragsgrenzen geprüft. |
| F09 | Der Test nennt den einfachen `PropertyEditScreen` ohne Kaufdaten und das globale `PropertyMetadataFormDialog` mit Kauf-/Steuerdaten. Auf aktuellem main ruft `PropertyData` bereits das globale Formular auf; der unvollständige einfache Editor ist unbenutzt. Das gemeinsame Formular vermisst jedoch Objektart/Notizen und ist implizit an die globale Auswahl gebunden. Vollständiges gemeinsames Formular ausdrücklich am Objekt binden, fehlende Felder ergänzen und unbenutzten Parallel-Editor entfernen. Bestehendes PropertyMetadata bleibt Quelle. |
| F10 | Wizard erzeugt Einheiten mit Leerstand; Mietdaten werden anschließend einzeln im Detail bzw. Mietplan erfasst. Vorhanden: `WohneinheitStatus`, `TenantPeriod`, `TenantHistoryStore`, `PropertyUnitScopedData`, deutsche Betrags-/Datumsprüfung, LazyColumn, gemeinsame Felder/Dialogs. Optionale kompakte Erstbefüllung in Einheitenübersicht; Zeilen separat bearbeiten, alle Änderungen vor Schreiben validieren und bestätigen. Bestehende Verträge werden gesperrt, niemals überschrieben. |
| F11 | Dashboard liest `bankStatementResult?.missingReceiptsCount ?: 0` und beschriftet dies als Offene Belege. Beleg-Prüfstatus UNGEPRUEFT/ZU_PRUEFEN und Freigabestatus sind unabhängig. Live-Prüfbestand und live fehlende Bankbeziehungen werden getrennt gezählt; offene unklassifizierte Bankeinträge bleiben ausdrücklich noch nicht geprüft. Das optionale alte KI-Analyseergebnis ist keine Kennzahlenquelle. |
| F12 | `yearReceipts` speist totals, Vorjahrsvergleich, Chart und Kategorien; `filtered` speist nur Liste. Alle sichtbaren Summen und Diagramme verwenden dieselbe gefilterte Projektion wie die Liste; Filterbasis wird benannt. |
| F13 | `UnifiedUnitOverviewCard` verwendet für Monatscheck und Mieterwechsel `onOpen`. Jahresübersicht zeigt Details anzeigen als Text/Icon ohne Aktion. Direkt die vorhandenen Dialoge öffnen, Detailaktion konkret an vorhandene Mietübersicht anbinden; bestehende Zurück-/Dialogwege erhalten. |
| F14 | Wizard löscht Nicht-Ziffern und erzeugt Listen synchron in onValueChange; -1/2,5 werden umgedeutet. `PropertyWizardInput` und beide Editoren erlauben jedes nicht negative Jahr; einfacher Editor prüft Sternchen nicht. Rohtext bewahren, positive Ganzzahlen und plausibles optionales Baujahr gemeinsam prüfen, Pflichtfelder durchsetzen. Einheitenlisten erst nach bestätigter gültiger Zahl außerhalb des UI-Threads vorbereiten und lazy anzeigen. |
| F15 | Belegliste enthält Gemini KI-Suche in Room-Belegen und Belegsdatenbank. Nutzersprache: Belege mit KI durchsuchen; keine Implementierungsbegriffe. |
| F16 | `executeWizardExport` ruft unabhängig von targetFormat `AdvisorPackageBuilder.buildPackage` auf, nur includeOriginals wechselt. Ergebnis/Teilen sind auf zipFile/application-zip festgelegt. Manifest zählt fileItems statt records. CSV erhält eigenen Output derselben geprüften Serializer-/Validator-Pipeline, korrektes Dateisuffix/MIME und Auditcount aus records. ZIP bleibt bestehender Paketweg. |
| F17 | Schritt 5 verspricht vier alte Ordner; Builder verwendet neun `AdvisorPackageStructure.requiredFolders` von 00_Start bis 08_Pruefprotokoll. UI-Beschreibung aus dieser gemeinsamen Quelle ableiten. |

## Prüfplan

Neue fachliche und Compose-Regressionen F06–F17, einschließlich Bank-/Beleg-Abdeckung,
Splits, REVIEW/Privat, Objekt-/Einheitenisolation, Vertragsgrenzen/Zukunft, Serienbefüllung,
Live-Prüfbestand, konsistenter Filter, direkten Aktionen, Rohtexteingabe, CSV-Datei/Audit und ZIP-Struktur.
Alle Phase-5A-Regressionen laufen unverändert im vollständigen testDebugUnitTest mit.

Die lokale Umgebung hat JDK 17, aber keinen Gradle/Android-SDK. Vollständige Prüfung
über die bestehende GitHub Android CI: testDebugUnitTest (einschließlich Compose/Navigation),
lintDebug, assembleDebug. Keine Tests deaktivieren oder Erwartungen abschwächen.
Ergebnisse, Diffreview und verbleibende praktische Prüfgrenzen werden nach Durchführung ergänzt.
