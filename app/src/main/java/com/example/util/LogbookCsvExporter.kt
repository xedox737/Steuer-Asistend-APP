package com.example.util

import com.example.data.LogbookTrip
import com.example.data.TripStopJson

object LogbookCsvExporter {
    private fun decimal(value: Double): String =
        com.example.ui.GermanNumberInput.formatQuantity(value, minimumDecimals = 1, grouping = false)

    fun create(trips: List<LogbookTrip>): String {
        val header = listOf(
            "Datum", "Uhrzeit", "Fahrtzweck", "Objekt", "Start", "Ziel", "Route",
            "Fahrtart", "Gleiche Rueckstrecke", "Steuerliche Kilometer",
            "Kilometerquelle", "KI-Schaetzung", "Strassenroute", "GPS",
            "Tacho Start", "Tacho Ende", "Manuelle Kilometer", "Route Provider",
            "Route berechnet am", "Korrekturgrund", "Korrekturhinweis",
            "Plausibilitaet", "Manuell bestaetigt", "Beleg-ID", "Notiz", "Immobilien-ID", "Status", "Storniert am", "Aenderungsverlauf"
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
                        decimal(trip.taxDistanceKm), trip.kilometerSource,
                        trip.aiEstimatedKm?.let(::decimal).orEmpty(),
                        trip.routedKm?.let(::decimal).orEmpty(),
                        trip.gpsMeasuredKm?.let(::decimal).orEmpty(),
                        trip.odometerStartKm?.let(::decimal).orEmpty(),
                        trip.odometerEndKm?.let(::decimal).orEmpty(),
                        trip.manualKm?.let(::decimal).orEmpty(),
                        trip.routeProvider, trip.routeCalculatedAt,
                        trip.correctionReason, trip.correctionNote,
                        trip.plausibilityStatus,
                        if (trip.manuallyConfirmed) "Ja" else "Nein",
                        trip.sourceReceiptId?.toString().orEmpty(), trip.note, trip.propertyId,
                        if (trip.cancelledAt.isBlank()) "Aktiv" else "Storniert", trip.cancelledAt, trip.historyJson
                    ).joinToString(";") { csvField(it) }
                )
            }
        }
    }

}

/** Standard CSV quoting shared by non-DATEV exports; labels and line breaks remain literal data. */
internal fun csvField(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
