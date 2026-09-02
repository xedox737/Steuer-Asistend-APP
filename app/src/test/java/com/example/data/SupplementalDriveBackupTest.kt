package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.util.LogbookCsvExporter
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupplementalDriveBackupTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    @Test fun tripAndStandardRouteRestoreCompletelyAndIdempotently() = runTest {
        val now = "2026-09-02T10:00:00Z"
        val route = TripRouteNormalizer.normalize("A", listOf(TripStop("B")), "C", TripRouteMode.INDIVIDUELL, false)
        database.logbookDao().upsertTrip(
            LogbookTrip(
                id = 41, date = "2026-09-02", time = "10:00", purpose = "Materialkauf",
                propertyReference = "Objekt", startAddress = "A", destinationAddress = "C",
                stopsJson = TripStopJson.encode(route.stops), routeMode = TripRouteMode.INDIVIDUELL.name,
                taxDistanceKm = 27.5, kilometerSource = KilometerSource.MANUELL.name,
                aiEstimatedKm = 31.0, routedKm = 24.7, manualKm = 27.5,
                odometerStartKm = 100.0, odometerEndKm = 127.5,
                plausibilityStatus = TripPlausibilityStatus.MANUELL_BESTAETIGT.name,
                manuallyConfirmed = true, sourceReceiptId = 7, expenseReceiptId = 8,
                routeProvider = "GOOGLE_ROUTES", routeCalculatedAt = now, routeDurationSeconds = 1800,
                correctionReason = "Umleitung", correctionNote = "Baustelle; \"Süd\"", routeSignature = route.signature,
                note = "Beleg vorhanden", createdAt = now, updatedAt = now
            )
        )
        database.logbookDao().upsertStandardRoute(
            StandardRoute(
                id = 17, name = "A-B-C", startAddress = "A", destinationAddress = "C",
                stopsJson = TripStopJson.encode(route.stops), routeMode = TripRouteMode.INDIVIDUELL.name,
                distanceKm = 24.7, routeSignature = route.signature, sourceProvider = "GOOGLE_ROUTES",
                createdAt = now, updatedAt = now
            )
        )
        context.getSharedPreferences("ai_provider_settings", Context.MODE_PRIVATE).edit()
            .putString("google_routes_key_ciphertext", "MUST_NOT_LEAVE_DEVICE").apply()

        val payload = SupplementalDriveBackup.createPayload(context, database)
        assertEquals(2, payload.getInt("schemaVersion"))
        assertFalse(payload.toString().contains("MUST_NOT_LEAVE_DEVICE"))
        database.logbookDao().deleteTrip(41)
        database.logbookDao().deleteStandardRoute(17)

        SupplementalDriveBackup.restorePayload(context, database, payload)
        SupplementalDriveBackup.restorePayload(context, database, payload)
        val restored = database.logbookDao().getAllTrips()
        val restoredRoutes = database.logbookDao().getAllStandardRoutes()
        assertEquals(1, restored.size)
        assertEquals(1, restoredRoutes.size)
        assertEquals("Umleitung", restored.single().correctionReason)
        assertEquals("GOOGLE_ROUTES", restored.single().routeProvider)
        assertEquals(route.signature, restoredRoutes.single().routeSignature)

        val csv = LogbookCsvExporter.create(restored)
        assertTrue(csv.contains("Route Provider"))
        assertTrue(csv.contains("GOOGLE_ROUTES"))
        assertTrue(csv.contains("Umleitung"))
        assertTrue(csv.contains("A -> B -> C"))
        assertTrue(csv.contains("\"Baustelle; \"\"Süd\"\"\""))
        assertFalse(csv.contains("MUST_NOT_LEAVE_DEVICE"))
    }

    @Test fun schemaOneWithoutLogbookArraysStillRestores() = runTest {
        SupplementalDriveBackup.restorePayload(context, database, JSONObject("""{"schemaVersion":1,"loans":[]}"""))
        assertTrue(database.logbookDao().getAllTrips().isEmpty())
        assertTrue(database.logbookDao().getAllStandardRoutes().isEmpty())
    }
}
