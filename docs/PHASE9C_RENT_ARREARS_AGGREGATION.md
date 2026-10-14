# Phase 9C – Mietrückstände pro Einheit

Base: 041d64b03169c0a73d8d16439d752d64f6bdd781.
Branch: fix/phase9c-rent-arrears-aggregation. Kein Merge.
Scope ausschließlich N03. Keine Mahnverwaltung oder Guthabenumbuchung.

## Ursache und Korrektur

UnifiedPropertyUnitsScreen bildete den Jahresrückstand als
max(GesamtSoll − GesamtIst, 0). PropertyManagerSummary berechnete „Offen“ ebenfalls
erst aus bereits summiertem Monatssoll/-ist. So verdeckte A mit 980 Soll / 1960 Ist
die Schulden von B mit 980 Soll / 0 Ist: GesamtSoll und GesamtIst jeweils 1960,
aber fälschlich 0 offen statt 980.

RentTrackingLogic.outstanding erhält die vorhandene Formel max(Soll − Ist, 0).
Die aktive Objektprojektion summiert nun die vorhandenen RentMonthProjection.missing
je Einheit. RentYearProjection.outstanding bietet den jährlichen Einheitensaldo;
UnifiedUnits und die bereits robuste RentOverviewUnit verwenden diesen selben Wert.
PropertyManagerSummary erhält den unabhängig berechneten Rückstand explizit;
es kann ihn nicht mehr aus GesamtSoll/GesamtIst rekonstruieren.

Guthaben ist max(Ist − Soll, 0) je Einheit, Rückstand max(Soll − Ist, 0) je Einheit.
Die beiden Werte dürfen unabhängig bestehen. Kein neues Guthabenfeld/keine neue
Karte notwendig. Die vorhandenen Soll-/Istwerte und legitimen Salden bleiben gleich.

## Scope und bestehende Projektionen

PropertyOverviewCard zeigt den aktuellen Monat. UnifiedUnits und RentOverviewYear
zeigen das ausgewählte Kalenderjahr. Keine Änderung der Zeitfilter, Mietperioden,
datierter Mietänderungen, Leerstandserwartung oder bestehenden PaymentProjection.
RentPaymentProjection bleibt die Quelle für bestätigte Bank-/Receiptzahlungen,
Teilzahlungen, rentMonth und Dedup. Stabile propertyId/unitId bleiben maßgeblich;
die Aggregation gruppiert nicht über sichtbare Namen. LedgerPaymentPresentation
und BankAllocation-/Splitregeln sind unverändert. Keine neuen DB-Abfragen.

RentYearProjection.missing summiert weiterhin explizit offene Mietmonate; dieser
Monatskontrollwert unterscheidet sich bewusst vom vorhandenen jährlichen
Einheitensaldo. Ein Septemberguthaben tilgt damit nicht automatisch die offene
Oktoberforderung in der Monatsprüfung. Es wird keine neue Mietkonto-/Tenancy-
Verrechnung eingeführt. Bereits bestehende Jahressaldierung innerhalb derselben
Einheit bleibt wie RentOverviewYear. „Offen“ über unabhängige Einheiten wird
an keiner aktiven betroffenen Karte aus einer Gesamtdifferenz abgeleitet.

Suchlauf: RentTenantWrapper summiert bereits Einheits-Monatswerte; AnnualRentRow
im Jahresassistenten begrenzt je Einheit vor Summierung. Beide unverändert.
PropertyDashboard und dessen RENT_MATRIX-Einstieg haben keinen aktiven Aufrufer;
die alte contextlose summary-Überladung wird noch von einem bestehenden Test
verwendet. Ihr notwendiger Summary-Konstruktoraufruf erhält ebenfalls einen
pro Einheit ermittelten Rückstand. Die aktive Monats-/Jahresprojektion wird dadurch
nicht ersetzt. Alte Monatsmatrixwerte bleiben unverändert.

## Prüfungen

Neue Phase9CRentArrearsTest-Fälle: verbindliches A/B-Beispiel mit Soll/Ist jeweils
1960 und Rückstand 980; bezahlte/teilbezahlte/gemischte/einzelne/leere Bestände;
300+680 mit Bank-/Receipt-Dedup; Kaution/Privat/Transfer/REVIEW ausgeschlossen;
gleichnamige Einheiten mit getrennten stabilen IDs in verschiedenen Objekten;
Dezemberzahlung mit Januarbankdatum über expliziten rentMonth; Aprilbeginn,
Vertragsende/Leerstand, Mietänderung, Mieterwechsel-Monatsgrenze und negative
Mietkorrektur unverändert vor dem Einheits-Clamp.

Der erste CI-Lauf bestand 995 von 996 Tests einschließlich aller neun neuen
N03-Projektionsfälle. Die neue Compose-Fixture wartete auf eine Property-Flow-
Aktualisierung in einer geteilten Datenbank. Der separate neue Compose-Test nutzt
nun die vorhandene IsolatedAppDatabaseRule vor Activitystart und prüft auch den
DAO-Bestand. Bestehende Compose-Tests bleiben unverändert; keine Assertions oder
Zeitlimits abgeschwächt.

Zusätzlicher Robolectric-Compose-Test öffnet die produktive Objektkarte und
UnifiedUnits und prüft jeweils 980 € offen. Android-E2E verwendet MainActivity,
Room und Preferences: Objektkarte, Jahres-/Einheitenkarte, Jahresdetails,
Activity recreate sowie Android-Zurück. Der Mietvertrag besteht in genau einem
Monat, damit die verglichenen Monats-/Jahresscopes bewusst dasselbe Soll haben.
Erhaltene Receipt-/Historienwerte werden nach Navigation erneut geprüft.
Screenshots: phase9c-property-open-980 und phase9c-unit-year-open-980.

Der vollständige vorhandene CI-Workflow prüft lintDebug, testDebugUnitTest,
assembleDebug, assembleDebugAndroidTest und den vollständigen Emulator-E2E-Job
einschließlich Phase 9A/9B, 6A/7A/7B/8B und FontScale-Regressionen.
Tatsächliche Ergebnisse werden nach Ausführung im PR/Abschlussbericht dokumentiert.
Lokaler Gradle/SDK/adb fehlen. Keine physische Geräteabnahme behauptet.

## Unverändert

Room 34, keine Migration/Entity/Tabelle. Supplemental 15 und Receipt-Backupvertrag 2
unverändert. Keine Persistenz-/Datenbereinigung, keine IDs geändert. Bankliste,
DATEV-Export, Backup-/Restorevertrag, Parser/Float-Schreibgrenzen, Fahrtenbuch,
Darlehen, AfA, globale Navigation/Design unverändert. N04/A11 bleiben Phase 9D.

Git fetch origin scheiterte am lokalen Proxy. main wurde frisch über den
GitHub-Connector abgerufen; signierter Originalcommit und identischer Baum
12090fa73b1ad4114d618f45c295d963034efcd7 wurden lokal verifiziert.
Nur eigener Branch/PR; nicht mergen.
