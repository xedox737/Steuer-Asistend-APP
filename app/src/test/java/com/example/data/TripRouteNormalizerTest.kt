package com.example.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TripRouteNormalizerTest {
    @Test
    fun stopOrderAndWhitespaceAreNormalizedCentrally() {
        val route = TripRouteNormalizer.normalize(
            "  A   Straße 1 ",
            listOf(TripStop(" C ", order = 1), TripStop(" B ", order = 0)),
            " D ", TripRouteMode.INDIVIDUELL, false
        )
        assertEquals(listOf("A Straße 1", "B", "C", "D"), route.stops.map { it.address })
    }

    @Test
    fun routesWithAndWithoutIntermediateStopHaveDifferentSignatures() {
        val direct = TripRouteNormalizer.normalize("A", emptyList(), "C", TripRouteMode.EINFACH, false)
        val via = TripRouteNormalizer.normalize("A", listOf(TripStop("B")), "C", TripRouteMode.EINFACH, false)
        assertNotEquals(direct.signature, via.signature)
    }

    @Test
    fun roundTripDoesNotDuplicateExistingReturn() {
        val first = TripRouteNormalizer.normalize("A", emptyList(), "B", TripRouteMode.HIN_UND_RUECKFAHRT, true)
        assertEquals(listOf("A", "B", "A"), first.stops.map { it.address })
        val normalizedAgain = TripRouteNormalizer.normalize(
            RouteDistanceRequest(first.stops, TripRouteMode.HIN_UND_RUECKFAHRT, true)
        )
        assertEquals(listOf("A", "B", "A"), normalizedAgain.stops.map { it.address })
    }

    @Test
    fun exactStandardRouteAvoidsProviderCall() = runTest {
        val normalized = TripRouteNormalizer.normalize("A", listOf(TripStop("B")), "C", TripRouteMode.INDIVIDUELL, false)
        var requests = 0
        val fake = RouteDistanceService { requests++; RouteDistanceResult(99.0, "FAKE", "now") }
        val standard = StandardRoute(
            name = "A-B-C", startAddress = "A", destinationAddress = "C",
            stopsJson = TripStopJson.encode(normalized.stops), routeMode = TripRouteMode.INDIVIDUELL.name,
            distanceKm = 24.7, routeSignature = normalized.signature, createdAt = "old", updatedAt = "now"
        )
        val result = RouteDistanceCoordinator.resolve(
            RouteDistanceRequest(normalized.stops, TripRouteMode.INDIVIDUELL, false), standard, false, fake
        )
        assertEquals(24.7, result!!.distanceKm, 0.001)
        assertEquals(0, requests)
    }
}
