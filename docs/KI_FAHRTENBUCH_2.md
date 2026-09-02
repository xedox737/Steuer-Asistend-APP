# KI-Fahrtenbuch 2.0

## Distanzregeln

Die KI-Beleganalyse bleibt erhalten und darf Händler, Datum, Zweck, Objektbezug, Route und Hin-/Rückfahrt vorschlagen. Eine KI-Schätzung wird als `KI_GESCHAETZT` angezeigt, aber niemals allein als steuerliche Kilometerzahl gespeichert.

Verwendbare Quellen sind `ROUTE_BERECHNET`, `GPS_GEMESSEN`, `TACHO`, `STANDARDSTRECKE` und `MANUELL`. Die zentrale Entscheidung liegt in `LogbookDistancePolicy`.

`RouteDistanceService` entkoppelt das Fahrtenbuch von Kartenanbietern. Solange kein echter Routinganbieter konfiguriert ist, liefert `UnavailableRouteDistanceService` bewusst kein Ergebnis. Die frühere PLZ-/Hash-Scheinberechnung wurde entfernt.

## GPS-Vorbereitung

`GpsTripRecorder` definiert den späteren Start-/Stopp-Vertrag. Die aktuelle Implementierung `DisabledGpsTripRecorder` zeichnet nichts auf und fordert keine Standortberechtigung an. Eine spätere Implementierung muss:

- nur nach ausdrücklichem Tippen auf „Fahrt starten“ aktiv werden,
- Laufzeitberechtigungen sichtbar behandeln,
- während der Messung einen Foreground Service mit permanenter Benachrichtigung verwenden,
- GPS-Punkte lokal speichern und erst beim Beenden zur Strecke verdichten,
- Unterbrechungen, unplausible Sprünge und Akkuverbrauch behandeln,
- niemals eine dauerhafte Hintergrundüberwachung starten.

Damit bleibt die App aktuell vollständig und ehrlich nutzbar, ohne eine halbfertige GPS-Funktion vorzutäuschen.
