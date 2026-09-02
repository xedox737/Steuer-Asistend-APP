package com.example.data

import com.example.util.LogbookCsvExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogbookDistancePolicyTest {
    @Test
    fun aiEstimateAloneIsNeverTaxDistance() {
        val result = LogbookDistancePolicy.decide(DistanceEvidence(aiEstimatedKm = 24.5))
        assertNull(result.taxDistanceKm)
        assertNull(result.source)
        assertEquals(TripPlausibilityStatus.PRUEFEN, result.plausibilityStatus)
        assertTrue(result.warnings.any { it.contains("KI-Strecke") })
    }

    @Test
    fun realRouteIsUsedAndSourceIsPersistable() {
        val result = LogbookDistancePolicy.decide(
            DistanceEvidence(aiEstimatedKm = 24.0, routedKm = 23.8)
        )
        assertEquals(23.8, result.taxDistanceKm!!, 0.001)
        assertEquals(KilometerSource.ROUTE_BERECHNET, result.source)
        assertEquals(TripPlausibilityStatus.PLAUSIBEL, result.plausibilityStatus)
    }

    @Test
    fun odometerDifferenceWinsAndInvalidOrderIsBlocked() {
        val valid = LogbookDistancePolicy.decide(
            DistanceEvidence(routedKm = 23.8, odometerStartKm = 50_000.0, odometerEndKm = 50_024.0)
        )
        assertEquals(24.0, valid.taxDistanceKm!!, 0.001)
        assertEquals(KilometerSource.TACHO, valid.source)

        val invalid = LogbookDistancePolicy.decide(
            DistanceEvidence(odometerStartKm = 50_024.0, odometerEndKm = 50_000.0)
        )
        assertNull(invalid.taxDistanceKm)
        assertTrue(invalid.warnings.any { it.contains("Endtachostand") })
    }

    @Test
    fun confirmedStandardRouteAndManualDistanceHaveExplicitSources() {
        val standard = LogbookDistancePolicy.decide(
            DistanceEvidence(standardRouteKm = 12.4, manuallyConfirmed = true)
        )
        assertEquals(KilometerSource.STANDARDSTRECKE, standard.source)
        assertEquals(TripPlausibilityStatus.MANUELL_BESTAETIGT, standard.plausibilityStatus)

        val manual = LogbookDistancePolicy.decide(
            DistanceEvidence(aiEstimatedKm = 15.0, manualKm = 12.4, manuallyConfirmed = true)
        )
        assertEquals(KilometerSource.MANUELL, manual.source)
        assertEquals(12.4, manual.taxDistanceKm!!, 0.001)
    }

    @Test
    fun materialDeviationRequiresReview() {
        val result = LogbookDistancePolicy.decide(
            DistanceEvidence(routedKm = 23.8, manualKm = 40.0)
        )
        assertEquals(TripPlausibilityStatus.PRUEFEN, result.plausibilityStatus)
        assertTrue(result.warnings.any { it.contains("weich") })
    }

    @Test
    fun confirmedManualOverrideWinsOverGoogleAndRequiresReasonWhenMaterial() {
        val modestOverride = LogbookDistancePolicy.decide(
            DistanceEvidence(routedKm = 24.7, manualKm = 27.5, manuallyConfirmed = true)
        )
        assertEquals(27.5, modestOverride.taxDistanceKm!!, 0.001)
        assertEquals(KilometerSource.MANUELL, modestOverride.source)

        val missingReason = LogbookDistancePolicy.decide(
            DistanceEvidence(routedKm = 24.7, manualKm = 34.0, manuallyConfirmed = true)
        )
        assertEquals(KilometerSource.MANUELL, missingReason.source)
        assertTrue(missingReason.correctionReasonRequired)
        assertEquals(TripPlausibilityStatus.PRUEFEN, missingReason.plausibilityStatus)

        val explained = LogbookDistancePolicy.decide(
            DistanceEvidence(
                routedKm = 24.7, manualKm = 34.0, manuallyConfirmed = true,
                correctionReason = "Umleitung"
            )
        )
        assertEquals(34.0, explained.taxDistanceKm!!, 0.001)
        assertEquals(KilometerSource.MANUELL, explained.source)
        assertEquals(TripPlausibilityStatus.MANUELL_BESTAETIGT, explained.plausibilityStatus)
    }

    @Test
    fun stopsRoundTripAndSourcesRemainInCsvExport() {
        val stops = listOf(
            TripStop("Zuhause", "Start", 0),
            TripStop("Baumarkt", "Zwischenstopp", 1),
            TripStop("Mietobjekt", "Ziel", 2),
            TripStop("Zuhause", "Rückkehr", 3)
        )
        val trip = LogbookTrip(
            id = 1,
            date = "2026-09-02",
            purpose = "Materialkauf",
            startAddress = "Zuhause",
            destinationAddress = "Mietobjekt",
            stopsJson = TripStopJson.encode(stops),
            routeMode = TripRouteMode.HIN_UND_RUECKFAHRT.name,
            sameReturnRoute = true,
            taxDistanceKm = 24.0,
            kilometerSource = KilometerSource.TACHO.name,
            odometerStartKm = 10_000.0,
            odometerEndKm = 10_024.0,
            plausibilityStatus = TripPlausibilityStatus.MANUELL_BESTAETIGT.name,
            manuallyConfirmed = true,
            createdAt = "now",
            updatedAt = "now"
        )
        assertEquals(stops, trip.stops)
        val csv = LogbookCsvExporter.create(listOf(trip))
        assertTrue(csv.contains("Zuhause -> Baumarkt -> Mietobjekt -> Zuhause"))
        assertTrue(csv.contains("TACHO"))
        assertTrue(csv.contains("\"24,0\""))
    }

    @Test
    fun unavailableRoutingNeverInventsDistance() = kotlinx.coroutines.test.runTest {
        assertNull(
            UnavailableRouteDistanceService.calculateRoadDistance(
                RouteDistanceRequest(
                    stops = listOf(TripStop("A"), TripStop("B")),
                    routeMode = TripRouteMode.EINFACH,
                    sameReturnRoute = false
                )
            )
        )
    }
}
