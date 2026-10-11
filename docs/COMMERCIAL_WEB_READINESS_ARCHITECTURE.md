# ADR: ImmoPilot kommerziell und Web-fähig vorbereiten (ohne Web-App)

Status: **Zielarchitektur / Vorbereitung, keine Implementierungsfreigabe für Cloud oder Web**  
Basis: `main` `64648bbf393dcf13f8243dbb5f471b56e564a9c2` (11.10.2026)

## Entscheidung

Die vorhandene Android-Anwendung bleibt **Kotlin + Jetpack Compose + Room, local-first**.
Es wird **keine Web-App, kein neuer Login, keine zentrale Cloud-Datenbank, kein Abo,
keine automatische Synchronisation und keine destruktive Datenmigration** eingebaut.
Ein späterer Browser-Client wird über **dieselbe versionierte fachliche API** arbeiten
wie Android; er greift **niemals direkt auf die lokale Room-Datenbank** zu.

Cloud-Anbieter und Web-Framework sind *nicht* verbindlich ausgewählt.
PostgreSQL + Backend/API (z. B. eigener Dienst oder geeignetes Managed Backend)
ist wegen der relationalen Beleg-, Objekt-, Miet- und Zahlungsbeziehungen
eine bevorzugte Evaluationsrichtung; Authentifizierung und Dateispeicher separat bewerten.
Firebase/Firestore aus bestehenden Codepfaden bedeutet **keine** Entscheidung für
Firestore als künftige zentrale Source of Truth.

## Heutiger Ist-Zustand und Grenzen

| Bereich | Bestehende Basis | Künftige fachliche Grenze |
|---|---|---|
| Immobilien | `PropertyMetadata.propertyId` | stabile objektweite Referenz |
| Einheiten/Mieter | Objekt + stabile Einheiten-ID, teilweise SharedPreferences | eindeutige Zugehörigkeit; keine Namen als Schlüssel |
| Belege | `Receipt.internalId`, `propertyId`, `unitId` | unverwechselbare Belegidentität und Freigabestatus |
| Dokumente | `ManagedDocument`, Originale und Referenzen | Metadaten getrennt von Dateiinhalt |
| Bank | Konten, Transaktionen, bestätigte Zuordnungen | Herkunft, Korrektur und Freigabe nachvollziehbar |
| DATEV/Steuer | vorhandene Policy, Export und Audit | **eine** maßgebliche fachliche Berechnung |
| Drive/Backup | `DrivePersistenceRepository`, `SupplementalDriveBackup` | **Backup ist keine bidirektionale Mehrgeräte-Synchronisation** |

Referenzen: `AGENTS.md`, `docs/CODE_MAP.md`,
`docs/IMMOBILIEN_MANAGER_ARCHITECTURE.md`,
`docs/DOCUMENT_MANAGEMENT_ARCHITECTURE.md`,
`docs/backup-persistence-inventory.md`,
`docs/PHASE5A_DATA_INTEGRITY.md` und
`docs/PHASE6A_IDENTITY_PAYMENT_CONSISTENCY.md`.

## Leitplanken ab sofort für neue Features

1. **Stabile IDs**: Eigenschaften über `propertyId`, `unitId`, `internalId`
   bzw. vorhandene fachliche IDs verbinden; keine Namen, Adressen, Listenindizes,
   gerätegebundene Room-Auto-IDs oder Dateipfade als öffentliche Identität.
   Fehlende Legacy-IDs nur über explizite, getestete Migration ergänzen.
2. **Geltungsbereich**: jede objekt- oder einheitsbezogene Abfrage benötigt einen
   expliziten Scope. Verwaiste/alte Zuordnungen nicht raten oder still übertragen.
3. **Fachlogik**: Validierung, Freigabe, Mietsoll/-ist, Zahlungsverknüpfung,
   AfA, 15%-Prüfung und DATEV von Compose-Ereignissen isolierbar halten.
   UI-Projektionen sind keine zweite Buchhaltungsquelle.
4. **Storage-Grenze**: lokale Repositorys bleiben führend. Neue Features sollten
   fachliche Lese-/Schreiboperationen hinter klaren Schnittstellen bündeln, statt
   direkt aus mehreren Screens in Room, Preferences und Drive zu schreiben.
   Keine flächendeckende Umstrukturierung nur für diese Vorbereitung.
5. **Portabilität**: Datenmodelle und Datums-/Geldfelder zwischen Plattformen
   explizit definieren. Intern ISO-Datum (`yyyy-MM-dd`) bzw. Zeitstempel mit
   Zeitzone; Anzeige in Deutschland `DD.MM.JJJJ`. Geldwerte in zukünftigen
   Verträgen als dezimale Strings/Minor Units, nicht binäre Fließkommazahlen
   übertragen; bestehende lokale `Double`-Felder nicht pauschal umstellen.
6. **Audit und Datenschutz**: Originalbelege unverändert, Freigaben mit
   Ursache/Identität/Zeitpunkt invalidieren, Exporthistorie nachvollziehbar.
   Keine Geheimnisse in Backup, Synchronisationspayload oder Logs.

## Zukünftiger logischer Systemaufbau (noch nicht implementiert)

```text
Android (Compose -> Use Cases/Repositories -> Room; offline nutzbar)
          |      optional, erst nach aktiver Anmeldung/Freigabe
          v
     Versionierte HTTPS-API + Authentifizierung + Autorisierung
          |           |                       |
          v           v                       v
  PostgreSQL      Objektspeicher          Audit/Jobs
  (Mandanten)     (Originaldateien)       (Exports/Events)
          ^
          |
Web (später: Browser-UI -> dieselbe API; keine Android-Abhängigkeit)
```

- Mehrmandantenmodell: `tenant/account` (Kundenorganisation) getrennt von
  `property` (Immobilie) und `unit` (Wohneinheit). `tenantId` niemals mit
  `propertyId` oder dem Fachbegriff Mieter verwechseln; intern besser
  `workspaceId` für Kundenmandant verwenden.
- `userId`, `workspaceId`, Rollen/Mitgliedschaften und Objektberechtigungen
  gehören in die spätere Serverarchitektur. Objekt-IDs bleiben innerhalb eines
  Workspace stabil; serverseitige Autorisierung muss **bei jeder** Objekt- und
  Dokumentanfrage gelten (auch bei Kenntnis fremder IDs).
- Rollen später mindestens Besitzer, Mitarbeiter und externer Steuerberater
  prüfen; Rechte fein genug für Dokumente, Finanzdaten und Exporte.
- Konto- und Tarifverwaltung sind ein eigenes Modul. `subscription` und
  `entitlement` dürfen niemals historisch gespeicherte Buchungsdaten löschen.
- KI-Anbieter-Keys anderer Nutzer dürfen nicht clientseitig geteilt werden;
  bei gehosteter KI: serverseitige Schlüssel-/Kostenkontrolle, Budgetlimits,
  Einwilligung und nachvollziehbare Prüfung der verarbeiteten personenbezogenen Daten.
- Personen- und Finanzdaten: DSGVO-Konzept inklusive Auftragsverarbeitung,
  Lösch-/Exportanfragen, EU-Hosting-Prüfung, Aufbewahrungsanforderungen,
  Berechtigungskonzept, verschlüsseltem Transport und Backup-Restore vor Launch;
  konkrete rechtliche Anforderungen gesondert prüfen.

## API-Kontrakte – erst als Entwurfsregeln

- Namespace `/api/v1`; Auth via standardisierte tokenbasierte Anmeldung;
  keine dauerhaften API-Tokens in Logs oder Klartext-Preferences.
- Ressourcenorientierung z. B. `/workspaces/{workspaceId}/properties/{propertyId}`,
  `.../units/{unitId}`, Belege, Dokumente und Zahlungen.
- Mutation mit servergeprüftem `workspaceId` aus der Auth-Session;
  Client-Scope darf nicht allein als Zugriffsnachweis gelten.
- Entitäten mit `id`, `revision`, `updatedAt`, `deletedAt`/Tombstone
  und optional `originDeviceId` planen; **nicht jetzt in Room nachrüsten**.
- `Idempotency-Key` für wiederholte POSTs, stabile Ereignis-IDs und
  optimistische Konkurrenzkontrolle (`If-Match`/`revision`) vorsehen.
- Fehler maschinenlesbar (`code`, `message`, optional `fieldErrors`),
  niemals still auf `property-1` oder einen anderen Workspace zurückfallen.
- Versionierte und explizit getestete JSON/Dateiverträge vor Implementierung
  festschreiben; `null` ≠ leer ≠ fehlend. Keine Rohkopie von Room-/Prefs-Objekten.
- Dokumente: Metadaten und unveränderliche Originalbytes getrennt transportieren;
  Hash/Größe/MIME/Originalreferenz, Upload-Abschluss und Freigabe prüfen.

## Sync- und Konfliktstrategie (Design, noch keine Funktion)

- Optionaler Cloud-Modus; bis zur bestätigten Aktivierung bleiben alle Daten
  lokal, bestehendes Drive-Backup und Restore unverändert.
- Erster Sync ist eine **kontrollierte Übernahme** pro Workspace, mit
  Vollständigkeitsprüfung aller Datenklassen und Originaldateien; Benutzer muss
  Zielkonto/Workspace bestätigen. Kein automatisches Verschieben zu fremden Konten.
- Outbox/Inbox-Modell mit idempotenten Operationen, serverseitiger Revision,
  Wiederaufnahme nach Unterbrechung, atomaren Fachtransaktionen und
  dokumentierter Reihenfolge abhängiger Belege/Dateien.
- Konfliktregel **niemals pauschal last-write-wins** für Freigaben,
  Mietverträge, Zahlungen, Bankbeleglinks oder Löschungen.
  Auf fachliche Konflikte: gesperrte automatische Übernahme, nachvollziehbare
  Vergleichsansicht, explizite Nutzerentscheidung.
- Löschungen als Tombstones, damit offline Geräte keine Daten wiederbeleben.
  Kein endgültiges serverseitiges Löschen allein aufgrund eines lokalen
  Sicherungs- oder Restorezustands.
- Drive bleibt vorerst persönlicher Backup-Kanal, nicht Server-Datenbank und
  nicht implizite Synchronisierung zwischen fremden Nutzern.

## Umsetzung in späteren, separat freizugebenden Etappen

**A – ab jetzt, Android ohne Verhaltensänderung**  
Bei jedem betroffenen Feature fachliche IDs/Scope dokumentieren, Businessregeln
testbar halten, Persistenzinventar pflegen. Keine zusätzliche Infrastruktur.

**B – vor Cloud-Pilot**  
Daten- und API-Verträge mit Beispielpayloads, Rollen- und Mandantenmodell,
Migration/Import, Offline-Konfliktmatrix, Bedrohungsmodell, Kosten-/DSGVO-Review,
Serverauthentifizierung, serverseitige Berechtigungstests.

**C – begrenzter Cloud-Pilot**  
Separat aktivierbarer Sync für Testdaten mit sicheren Backups und Rollback,
Mehrgeräte-/Mandanten-Isolation, Last- und Konflikttests; nie
unkontrolliert an produktive Daten anbinden.

**D – erst danach Web-Client**  
Web nutzt dieselben fachlichen API-/Validierungsverträge.
Keine parallelen Berechnungen für DATEV, AfA oder Mietkontrolle.

**E – erst vor kommerziellem Launch**  
Abos/Billing, Kunden-Support, Datenexport, Sperr-/Löschkonzept,
Betriebsüberwachung, Security-Review und rechtliche Dokumentation.

## Konkrete Abnahmekriterien für spätere Architekturarbeiten

- Offline-Android, vorhandener Backup/Restore und DATEV unverändert korrekt.
- Zwei Workspaces mit identischen Objekt-/Einheitennamen sehen **keine**
  fremden Daten, Dateien, Suchergebnisse oder Exporte; negative API-Tests.
- Gleiche Fachfälle liefern unter Android und Web dieselben Ergebnisse.
- Doppelter Upload/Request erzeugt keinen doppelten Beleg oder Zahlungsfluss.
- Unterbrechung und zeitgleiche Änderung führen nicht zu Datenverlust oder
  stiller Umfreigabe; abgelehnte Konflikte bleiben überprüfbar.
- Originale, Prüfsummen und Export-Freigabekette bleiben erhalten.
- Neue Schema- und Sync-Versionen besitzen vorab getestete Migration und
  Rückwärtskompatibilitätsstrategie.

## Nicht Bestandteil dieses Schritts

Keine Anwendungscodeänderungen, keine Room-/Prefs-Migration, keine
Android-UI-Änderung, kein neuer Cloud-Service, keine Website, keine
Preis-/Abo-Funktion, keine automatische Kontoerstellung, kein Daten-Upload.
