# Immobilien-Manager – Integrationsarchitektur

## Navigation

Die primäre Navigation lautet `Start`, `Belege`, `Scannen`, `Immobilien`, `Mehr`. Der neue Bereich `Immobilien` öffnet die Portfolioübersicht. `Mehr` bündelt Fahrtenbuch, Finanzen, Dokumentenakte und Einstellungen; die bisherigen Screens und ihre Fachlogik bleiben bestehen.

## Portfolio und Objekt-Dashboard

`ImmobilienManagerScreen` liest alle Datensätze aus der bestehenden Room-Tabelle `property_metadata`. Der bisherige Datensatz mit `id = 1` bleibt unverändert erhalten und erscheint automatisch. Eine stabile `propertyId` ist weiterhin die fachliche Identität; Name und Adresse sind nur Anzeige.

Jede Objektkarte zeigt kompakt Einheitenstatus und die Monatsprojektion aus den vorhandenen Wohneinheiten/Mietbelegen. Das Objekt-Dashboard bündelt die vorhandenen Funktionsbereiche:

| Bereich | Bestehende Datenquelle/Funktion |
|---|---|
| Einheiten & Mieter | `WohneinheitStatus`, bestehende SharedPreferences und `TenantHistoryStore` |
| Mieteingänge | `RentIncomeWithTenantHistoryScreen` einschließlich Mietplan und Monatscheck |
| Belege & Kosten | vorhandene `Receipt`-Datensätze, nur gefiltert dargestellt |
| Finanzierung | `LoanManagementSection` und bestehende Zinszuordnung |
| Sanierungen | vorhandene Belege; die 15-%-Bewertung bleibt im bestehenden Steuerbereich |
| Dokumente | `ManagedDocument` und `DocumentManagementScreen`, gefiltert über stabile `propertyId` |
| Steuer & AfA | `AnnualTaxAssistantScreen`, bestehende AfA-, 15-%-, Fingerprint- und Advisor-Logik |
| Objektdaten | bestehendes `PropertyMetadataFormDialog` |

Es wurden keine DATEV-, Advisor-, AfA-, Miet-, Darlehens-, Dokument-, Drive- oder Fahrtenbuchberechnungen dupliziert.

## Immobilien anlegen

Der fünfstufige Assistent erfasst Basis-, Gebäude-, Finanzierungs-, Einheiten- und Steuerangaben. Er schreibt eine weitere Zeile in die vorhandene `property_metadata`-Tabelle und erzeugt stabile Unit-IDs. Finanzierung kann übersprungen und anschließend in der bestehenden Darlehensverwaltung ergänzt werden. Unvollständige optionale Angaben bleiben bei ihren vorhandenen neutralen Defaultwerten.

## Datenmodell und Bestandsschutz

Es gibt kein zweites Property-Modell. `PropertyDao` wurde lediglich um Listen-, ID- und Lookup-Abfragen ergänzt. Das vorhandene Schema kann bereits mehrere `PropertyMetadata`-Zeilen speichern. `Receipt` und `Loan` wurden um eine stabile `propertyId` ergänzt; Room-Version 21 und `MIGRATION_20_21` setzen Bestandszeilen ausdrücklich auf `property-1`, ohne sie neu zu klassifizieren oder zu löschen.

Der ViewModel-State hält die ausgewählte stabile `propertyId`. Alle bisherigen Verbraucher von `propertyMetadata` erhalten dadurch das ausgewählte Objekt, während ohne Auswahl weiterhin der bestehende erste Datensatz verwendet wird. Einheiten des Bestandsobjekts verwenden unverändert die bisherigen Preference-Schlüssel. Neue Objekte erhalten property-namespacete Schlüssel, damit gleich benannte Einheiten nicht kollidieren.

Der ManagedDocument-Sync löst die Metadaten jetzt anhand der `propertyId` des Dokuments auf; die bestehende Gateway-, Hash-, Move/Rename- und Indexlogik bleibt unverändert.

## Bestehende Datenübernahme

- `property_metadata.id = 1` bleibt der bestehende Datensatz.
- Bestehende Property- und Unit-IDs werden nicht geändert.
- Bestehende Belege, Darlehen, Dokumente, Drive-IDs, Hashes, Backups und Exporte werden nicht migriert oder umgeschrieben.
- Neue Belege und Darlehen werden dem aktuell geöffneten Objekt über `propertyId` zugeordnet. Die globale Ansicht unter `Mehr` bleibt objektübergreifend.
- Legacy-Einheiten lesen und schreiben weiterhin ihre bisherigen Schlüssel.
- Neue Objekte und Einheiten sind nach einem Neustart aus Room bzw. den bestehenden App-Preferences wieder verfügbar.
- Supplemental-Backup-Schema 4 sichert zusätzlich das Property-Portfolio, property-namespacete Einheiten und die Darlehenszuordnung. Ältere Backups ohne diese Felder bleiben lesbar.

## Tests

- Portfolio-Projektion: bestehende Legacy-Belege, objektbezogene Receipt-/Document-Filter, Soll/Ist/Offen.
- Room-Persistenz/Neustart: Bestandsobjekt und neues Objekt koexistieren; Lookup über stabile `propertyId`; bestehender Singleton-Zugriff bleibt kompatibel.
- Migration 20 → 21: bestehende Belege und Darlehen bleiben erhalten und erhalten nur den sicheren Legacy-Default.
- Supplemental Backup/Restore: Portfolio, Einheiten und Darlehenszuordnung bleiben idempotent erhalten.
- Die vollständige bestehende Unit-, Lint- und APK-Regression bleibt Bestandteil des finalen Android-CI-Laufs.

## Bekannte Restpunkte

- Bestandsbelege und Bestandsdarlehen bleiben aus Sicherheitsgründen dem bisherigen Objekt `property-1` zugeordnet. Eine fachliche Umverteilung alter Datensätze erfolgt bewusst nicht automatisch.
- Mietplan und Mieterverlauf verwenden aus Bestandsschutzgründen weiterhin ihre historischen, namensbasierten Preference-Schlüssel. Gleich benannte Einheiten in verschiedenen neuen Objekten sollten daher bis zu einer eigenen rückwärtskompatiblen Schlüssel-Migration vermieden werden.
- Der Assistent legt Einheiten mit neutralem Status `Leerstand` an. Mieterhistorie, Mietplan und Darlehen werden anschließend in den bereits vorhandenen Fachdialogen gepflegt.
- Responsives Verhalten, CameraX/Scanner und native OCR-Qualität bleiben zusätzlich auf einem kleinen und einem aktuellen Android-Gerät manuell zu prüfen.

## Sinnvolle spätere Erweiterungen

Eine Jahresmatrix für alle Einheiten kann auf dem bestehenden Monatscheck aufbauen; sie gehört nicht in diesen Integrationsblock. Der Anlegeassistent kann später um die vollständige Darlehens- und Einheitenbearbeitung innerhalb der Schritte erweitert werden; aktuell führt er dafür bewusst in die vorhandenen Fachdialoge.
