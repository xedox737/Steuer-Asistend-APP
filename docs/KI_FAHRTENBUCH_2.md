# KI-Fahrtenbuch 2.0

## Verlässlicher Datenfluss

Die vorhandene Beleglogik bleibt der Komfort-Assistent: Belegdatum, Händler, möglicher Zweck, Objekt, Zwischenstopp und eine Gemini-Kilometerschätzung werden vorgeschlagen. `estimateRouteDistance` kennzeichnet die Zahl ausdrücklich als KI-Schätzung. `KI_GESCHAETZT` ist nur Vergleichswert und kann nie allein `taxDistanceKm` liefern.

Für belastbare Kilometer stehen unabhängig davon Google-Straßenroute, bestätigte Standardstrecke, Tacho, GPS-Architektur und manuelle Eingabe bereit. Der Nutzer muss Route, Zweck, Objekt und Kilometer ausdrücklich bestätigen, bevor eine Fahrt und der zugehörige 0,30-Euro/km-Ausgabenbeleg gespeichert werden.

## Route und Doppelzählung

`TripRouteNormalizer` bildet die einzige Routendefinition für UI, Google-Anfrage, Standardsignatur, Speicherung und Export:

`Start → Zwischenstopp 1 → … → Ziel → optionale Rückkehr`

Adressen werden nur getrimmt, kleingeschrieben und bei der Signatur hinsichtlich mehrfacher Leerzeichen normalisiert. Reihenfolge, Fahrtart und `sameReturnRoute` gehen in eine SHA-256-Signatur ein. Bei Hin/Rück wird die Rückkehr genau einmal als letzter Wegpunkt ergänzt. Google berechnet die komplette Route; es findet danach keine Verdopplung statt. Individuelle Routen werden nicht optimiert oder umsortiert.

## Kilometerentscheidung

Eine ausdrücklich bestätigte manuelle Strecke bleibt erhalten. Danach folgen tatsächliche GPS-Messung, Tachodifferenz, Google-Straßenroute und bestätigte Standardstrecke. Eine nicht bestätigte manuelle Zahl ist nur dann die belastbare Quelle, wenn keine automatische Quelle vorhanden ist und die Fahrt anschließend ausdrücklich bestätigt wird. Die KI bleibt immer außerhalb der steuerlichen Auswahl.

Abweichungen über dem zentralen Schwellenwert `max(2 km, 15 %)` führen zu einem Prüfhinweis. Eine deutlich abweichende manuelle Korrektur braucht einen Grund; bei „Sonstiges“ zusätzlich eine Beschreibung.

## Datenschutz und Sicherung

Der Google-Key wird wie die vorhandenen privaten Provider-Schlüssel mit Android Keystore AES/GCM verschlüsselt, maskiert angezeigt und kann gelöscht werden. Er wird weder geloggt noch in CSV, DATEV oder Drive gesichert.

`SupplementalDriveBackup` Schema 2 enthält Fahrten und Standardstrecken vollständig. IDs werden per Room-Upsert erhalten, wodurch wiederholter Restore stabil bleibt. Schema-1-Sicherungen ohne Fahrtenbuch-Arrays bleiben lesbar. Room 19 ergänzt neue Felder über die explizite Migration 18→19; alte Fahrten werden nicht neu berechnet oder umklassifiziert.

GPS bleibt über `GpsTripRecorder` vorbereitet, aber `DisabledGpsTripRecorder` bleibt aktiv. Es wurden keine Standortberechtigungen oder Hintergrunddienste ergänzt.

Google-Cloud-Einrichtung: [GOOGLE_ROUTES_SETUP.md](GOOGLE_ROUTES_SETUP.md).

