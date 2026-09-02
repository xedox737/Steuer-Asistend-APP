# Google Routes für das KI-Fahrtenbuch einrichten

Die App nutzt ausschließlich den REST-Endpunkt `POST https://routes.googleapis.com/directions/v2:computeRoutes` mit `travelMode=DRIVE`. Textadressen werden als Google-Route-Waypoints übergeben; Zwischenstopps bleiben in ihrer erfassten Reihenfolge. Es gibt keine Luftlinien- oder Schätzstrecke als Routing-Fallback.

## Einrichtung in Google Cloud

1. In der [Google Cloud Console](https://console.cloud.google.com/) ein eigenes Projekt auswählen oder erstellen.
2. Billing für dieses Projekt aktivieren. Google Routes ist ein abrechnungsfähiger Dienst.
3. Unter **APIs & Dienste → Bibliothek** die **Routes API** aktivieren.
4. Eine separate Geocoding API ist für diese Implementierung nicht nötig: `computeRoutes` nimmt Textadressen direkt an und liefert Geocoding-Status zurück. Geocoding nur aktivieren, wenn später ein separater, abstrahierter Geocoding-Workflow ergänzt wird.
5. Unter **APIs & Dienste → Anmeldedaten** einen separaten API-Schlüssel für diese App erstellen.
6. Den Schlüssel in der App unter **Einstellungen → KI- und Routing-Anbieter → Google Routes** eingeben. Er wird mit einem nicht exportierbaren Android-Keystore-Schlüssel AES/GCM-verschlüsselt gespeichert und nur maskiert angezeigt. Es gibt keine BuildConfig- oder Repository-Konfiguration für den Google-Schlüssel.
7. Den Schlüssel auf die **Routes API** beschränken. Zusätzlich eine zur direkten Android-REST-Nutzung passende Einschränkung setzen und Paketname sowie SHA-1-Zertifikatsfingerabdruck prüfen. Die App sendet `X-Android-Package` und `X-Android-Cert`. Für eine öffentlich verteilte App ist ein eigener abgesicherter Backend-Proxy die robustere Schlüsselgrenze.
8. Unter **Quotas & System Limits** ein passendes Tages-/Minutenlimit setzen.
9. Unter **Billing → Budgets & alerts** ein Budget und Warnschwellen konfigurieren. Budgetwarnungen sperren Ausgaben nicht automatisch.
10. In einem Fahrtvorschlag vollständige Adressen eintragen und ausdrücklich **Straßenroute berechnen** wählen. Danach müssen Distanz, Provider `GOOGLE_ROUTES` und Berechnungszeit erscheinen.

Die App ruft Google nicht bei Recomposition oder Tastendruck auf. Eine exakt passende bestätigte Standardstrecke kann ohne API-Aufruf verwendet werden; eine neue Anfrage entsteht erst durch **Straßenroute berechnen/Route neu berechnen**.

## Typische Fehler

- **API not enabled / HTTP 403:** Routes API im richtigen Projekt aktivieren und kurz auf die Aktivierung warten.
- **Billing disabled / HTTP 403:** Billing-Verknüpfung des Projekts prüfen.
- **Invalid key / HTTP 401 oder 403:** Schlüssel in der App ersetzen; API- und App-Einschränkungen, Paketname und Zertifikat kontrollieren.
- **Quota exceeded / HTTP 429:** Quota prüfen oder später erneut versuchen; die App führt keine aggressiven Retries aus.
- **Adresse nicht gefunden / HTTP 400 oder leere Route:** Straße, Hausnummer, PLZ und Ort vervollständigen. Teiltreffer werden nicht als bestätigte Route übernommen.
- **Timeout/5xx:** Internetverbindung prüfen und später manuell neu berechnen. Es wird keine KI- oder Luftlinienzahl als Straßenroute gespeichert.

Niemals echte Schlüssel in Dokumentation, Quellcode, GitHub, Screenshots oder Testdaten eintragen. Der Schlüssel wird nicht in Drive-Backups, CSV-/DATEV-Exporte oder Logs aufgenommen.

Quellen: [Compute Routes](https://developers.google.com/maps/documentation/routes/compute_route_directions), [API Security Best Practices](https://developers.google.com/maps/api-security-best-practices), [API-Key Restrictions](https://cloud.google.com/docs/authentication/api-keys), [Quotas](https://developers.google.com/maps/documentation/routes/usage-and-billing), [Budgets](https://cloud.google.com/billing/docs/how-to/budgets).
