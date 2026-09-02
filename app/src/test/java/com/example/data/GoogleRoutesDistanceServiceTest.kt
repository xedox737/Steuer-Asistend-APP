package com.example.data

import java.time.Instant
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoogleRoutesDistanceServiceTest {
    @Test
    fun googleReturnsRealRoadDistanceAndProviderMetadata() = runTest {
        val transport = RecordingTransport(200, """{"routes":[{"distanceMeters":24700,"duration":"1800s"}]}""")
        val service = service(transport)
        val result = service.calculateRoadDistance(request(listOf("A", "B")))!!

        assertEquals(24.7, result.distanceKm, 0.001)
        assertEquals("GOOGLE_ROUTES", result.providerId)
        assertEquals("2026-09-02T10:00:00Z", result.calculatedAt)
        assertEquals(1800L, result.durationSeconds)
        assertEquals("DRIVE", JSONObject(transport.body).getString("travelMode"))
    }

    @Test
    fun httpFailureNeverInventsRoute() = runTest {
        val service = service(RecordingTransport(503, "{}"))
        val failure = runCatching { service.calculateRoadDistance(request(listOf("A", "B"))) }.exceptionOrNull()
        assertTrue(failure is RouteDistanceException)
        assertFalse((failure?.message ?: "").contains("secret-test-key"))
    }

    @Test
    fun allIntermediateStopsReachGoogleInOriginalOrder() = runTest {
        val transport = RecordingTransport(200, """{"routes":[{"distanceMeters":1000}]}""")
        service(transport).calculateRoadDistance(request(listOf("A", "B", "C", "D")))
        val json = JSONObject(transport.body)
        assertEquals("A", json.getJSONObject("origin").getString("address"))
        assertEquals("B", json.getJSONArray("intermediates").getJSONObject(0).getString("address"))
        assertEquals("C", json.getJSONArray("intermediates").getJSONObject(1).getString("address"))
        assertEquals("D", json.getJSONObject("destination").getString("address"))
        assertFalse(transport.body.contains("optimizeWaypointOrder"))
    }

    @Test
    fun roundTripAddsReturnExactlyOnce() = runTest {
        val first = RecordingTransport(200, """{"routes":[{"distanceMeters":20000}]}""")
        service(first).calculateRoadDistance(
            RouteDistanceRequest(listOf(TripStop("A", order = 0), TripStop("B", order = 1)), TripRouteMode.HIN_UND_RUECKFAHRT, true)
        )
        val firstJson = JSONObject(first.body)
        assertEquals("A", firstJson.getJSONObject("destination").getString("address"))
        assertEquals(1, firstJson.getJSONArray("intermediates").length())

        val alreadyReturned = RecordingTransport(200, """{"routes":[{"distanceMeters":20000}]}""")
        service(alreadyReturned).calculateRoadDistance(
            RouteDistanceRequest(
                listOf(TripStop("A", order = 0), TripStop("B", order = 1), TripStop("A", order = 2)),
                TripRouteMode.HIN_UND_RUECKFAHRT, true
            )
        )
        val secondJson = JSONObject(alreadyReturned.body)
        assertEquals(1, secondJson.getJSONArray("intermediates").length())
        assertEquals("A", secondJson.getJSONObject("destination").getString("address"))
    }

    @Test
    fun unavailableServiceStillReturnsNoSyntheticDistance() = runTest {
        assertNull(UnavailableRouteDistanceService.calculateRoadDistance(request(listOf("A", "B"))))
    }

    private fun request(addresses: List<String>) = RouteDistanceRequest(
        addresses.mapIndexed { index, value -> TripStop(value, order = index) },
        TripRouteMode.INDIVIDUELL,
        false
    )

    private fun service(transport: RoutesHttpTransport) = GoogleRoutesDistanceService(
        packageName = "com.example.test",
        signingCertificateSha1 = "AA:BB",
        apiKeyProvider = { "secret-test-key-123456".toCharArray() },
        transport = transport,
        now = { Instant.parse("2026-09-02T10:00:00Z") }
    )

    private class RecordingTransport(private val code: Int, private val response: String) : RoutesHttpTransport {
        var body: String = ""
        override suspend fun post(url: String, headers: Map<String, String>, body: String): RoutesHttpResponse {
            assertEquals(GoogleRoutesDistanceService.ENDPOINT, url)
            assertEquals("com.example.test", headers["X-Android-Package"])
            assertFalse(body.contains("secret-test-key"))
            this.body = body
            return RoutesHttpResponse(code, response)
        }
    }
}
