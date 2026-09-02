package com.example.util

import com.example.data.LogbookTrip
import com.example.data.TripStopJson
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object LogbookCsvExporter {
    private val decimal = DecimalFormat("0.0", DecimalFormatSymbols(Locale.GERMANY))

    fun create(trips: List<LogbookTrip>): String {
        val header = listOf(
            "Datum", "Uhrzeit", "Fahrtzweck", "Objekt", "Start", "Ziel", "Route",
            "Fahrtart", "Gleiche Rueckstrecke", "Steuerliche Kilometer",
            "Kilometerquelle", "KI-Schaetzung", "Strassenroute", "GPS",
            "Tacho Start", "Tacho Ende", "Manuelle Kilometer", "Route Provider",
            "Route berechnet am", "Korrekturgrund", "Korrekturhinweis",
            "Plausibilitaet", "Manuell bestaetigt", "Beleg-ID", "Notiz"
        ).joinToString(";")
        return buildString {
            appendLine(header)
            trips.sortedWith(compareBy<LogbookTrip> { it.date }.thenBy { it.time }.thenBy { it.id }).forEach { trip ->
                val route = TripStopJson.decode(trip.stopsJson).joinToString(" -> ") { it.address }
                appendLine(
                    listOf(
                        trip.date, trip.time, trip.purpose, trip.propertyReference,
                        trip.startAddress, trip.destinationAddress, route, trip.routeMode,
                        if (trip.sameReturnRoute) "Ja" else "Nein",
                        decimal.format(trip.taxDistanceKm), trip.kilometerSource,
                        trip.aiEstimatedKm?.let(decimal::format).orEmpty(),
                        trip.routedKm?.let(decimal::format).orEmpty(),
                        trip.gpsMeasuredKm?.let(decimal::format).orEmpty(),
                        trip.odometerStartKm?.let(decimal::format).orEmpty(),
                        trip.odometerEndKm?.let(decimal::format).orEmpty(),
                        trip.manualKm?.let(decimal::format).orEmpty(),
                        trip.routeProvider, trip.routeCalculatedAt,
                        trip.correctionReason, trip.correctionNote,
                        trip.plausibilityStatus,
                        if (trip.manuallyConfirmed) "Ja" else "Nein",
                        trip.sourceReceiptId?.toString().orEmpty(), trip.note
                    ).joinToString(";") { csv(it) }
                )
            }
        }
    }

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
