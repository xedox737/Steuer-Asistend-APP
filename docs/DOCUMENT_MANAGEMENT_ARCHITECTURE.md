# Objektbezogene Dokumentenverwaltung

## Ziel und Identität

Die Dokumentenakte ergänzt das vorhandene Belegsystem. `Receipt.internalId`/`displayId`, Drive-Datei-IDs, DATEV-Verknüpfungen und Tombstones bleiben unverändert. Nicht-belegartige Unterlagen verwenden `ManagedDocument.documentId`. Ein Beleg wird in der Dokumentensuche über `receipt:<internalId>` referenziert; dadurch entsteht keine zweite Belegidentität und kein zweites Original.

Die technische Objektzuordnung erfolgt über `PropertyMetadata.propertyId`, die Einheitenzuordnung über `WohneinheitStatus.unitId`. Ordner- und Anzeigenamen dürfen sich ändern, ohne eine neue Identität anzulegen.

## Drive-Struktur

Unterhalb des bestehenden App-Roots wird pro Property-ID einmalig eine Akte angelegt. Die stabile kanonische Ordnerzuordnung enthält die Property-ID; der sichtbare Name wird nur zur Darstellung verwendet.

```text
SteuerAssistent_<Objekt>/
  00_Stammdaten/
    01_Kauf_Eigentum/
    02_Grundstueck_Gebaeude/
    03_Energie_Technik/
    04_Versicherungen/
    05_Steuer_Grundlagen/
    06_Sonstige_Objektunterlagen/
  01_Einheiten/<Einheit>/{Mietvertrag,Uebergabe,Sonstige_Dokumente}/
  02_Belege/<Jahr>/
  03_Finanzierung_AfA/{Darlehen,Zinsunterlagen,Kaufpreisaufteilung,AfA}/
  04_Sanierungen/
  05_Steuerberater_Exporte/
  _System/
```

Neue laufende Belege liegen flach im Jahresordner. Kategorie, Unterkategorie, Wohneinheit, Zahlungsart und DATEV-Konto bleiben Metadaten. Neue Namen folgen `YYYY-MM-DD_AUSSTELLER_BETRAG_BELGID.ext`; Legacy-Namen bleiben lesbar und sind niemals Identität.

## Import, OCR und KI-Prüfung

1. Der Import liest das Original lokal, ermittelt SHA-256/Größe und prüft Dokument-ID, Receipt-ID, Drive-ID und Hash auf Dubletten.
2. PDFs nutzen zuerst eingebetteten Text. Nur bei unbrauchbarem Text werden Seiten lokal gerendert und mit ML Kit Text Recognition verarbeitet. Bilder werden direkt lokal erkannt.
3. Der OCR-Text wird lokal in Room/FTS indexiert. Drive-Backup enthält bewusst nicht den Volltext oder lokale Gerätepfade; der Index wird beim Restore aus sicheren Metadaten neu aufgebaut, OCR kann lokal erneut ausgeführt werden.
4. Gemini oder OpenAI klassifizieren über die vorhandene verschlüsselte Provider-Konfiguration. Dokumentinhalt gilt als unzuverlässige Eingabe. Ergebnisse bleiben `PRUEFEN`.
5. Der Prüfdialog zeigt Feld, erkannten/geänderten Wert, Confidence, aktuellen Wert und Seitenquelle. Nur explizit markierte Felder werden in Property-, Loan- oder Einheitendaten geschrieben.

API-Schlüssel werden weder im Dokumentmodell noch in Index, Backup, CSV, Logs oder Drive-Dateien gespeichert.

## Dubletten

- Gleiche Dokument-, Receipt- oder Drive-ID: vorhandene Identität verwenden.
- Gleicher SHA-256 und gleiche Größe: identisches Original verknüpfen, nicht erneut hochladen.
- Nur gleicher Name und gleiche Größe: Nutzerentscheidung `vorhandenes verwenden`, `separat übernehmen` oder `abbrechen`.
- Gleicher Name bei anderem Inhalt: separates Dokument; Dateiname ist kein Dublettenschlüssel.

## Bestandsmigration

Die Migration startet niemals automatisch. Die Vorschau arbeitet nur lesend und zählt unveränderte, zu verschiebende, umzubenennende sowie unklare Dateien. Erst die Bestätigung erstellt Zielordner und verschiebt/benennt dieselbe Drive-Datei um. Es gibt keine Kopie und kein Löschen.

Jeder Schritt wird in `document_migration_journal` gespeichert. Vorher und nachher wird der Inhalt per SHA-256 geprüft. Die DriveFileId bleibt erhalten. Unklare Eltern, fehlende Dateien oder Hashabweichungen werden `MIGRATION_PRUEFEN`; bestehende Originale werden nicht überschrieben. Ein erneuter Lauf ist idempotent und setzt anhand von Datei-ID, Ziel und Journal fort.

## Backup, Restore und Kompatibilität

Room-Version 20 fügt `managed_documents`, `document_search_fts`, `document_migration_journal` und die stabile Property-ID mit nicht-destruktiver Migration hinzu. Supplemental-Backup-Schema 3 enthält Dokumentmetadaten und Referenzen. Schema 1/2 ohne Dokumentarrays bleibt lesbar. Restore verwendet Upsert und baut den Suchindex neu auf.

DATEV und das Steuerberaterpaket beziehen Belege weiterhin über die bestehenden Receipt-/Originalreferenzen. Das Verschieben eines Drive-Originals ändert die Datei-ID nicht. Fahrtenbuch, Google Routes, Kilometer- und Plausibilitätslogik werden von der Dokumentenakte nicht verändert.

## Bewusste Grenzen

- Keine rechtliche oder steuerliche Interpretation von Grundbuch- oder Vertragsinhalten.
- Keine automatische endgültige KI-Zuordnung und kein Überschreiben von Stammdaten.
- Keine automatische Migration und keine rückwirkende Umbenennung ohne Bestätigung.
- OCR-Qualität hängt von Scan, Sprache und Layout ab; schlechte Ergebnisse bleiben prüfpflichtig.
- Die App verwaltet derzeit ein Property-Metadatenobjekt, nutzt aber bereits eine stabile Property-ID, sodass die Dokumentidentität nicht mehr vom Namen abhängt.
