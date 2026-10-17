package com.example.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase10CReplaceFullRelationIntegrityTest : Phase10CRestoreFixture() {
    private suspend fun book(source: Receipt, key: String, route: Long? = null): Long =
        LogbookBookingStore(db, repository).saveLogbookTrip(source, "Besichtigung", "A", "B",
            listOf(TripStop("A", "Start", 0), TripStop("B", "Ziel", 1)), TripRouteMode.EINFACH, false,
            DistanceEvidence(manualKm = 25.0, manuallyConfirmed = true), standardRouteId = route,
            bookingKey = key).getOrThrow()

    // Focused reproduction from #139, retaining its real booking/coordinator/core path and strict orphan check.
    @Test fun realBookingEmptyTargetDoubleMergeAndDoubleReplaceKeepExactTripReceiptSnapshot() = runTest {
        seedProperty()
        (1..60).forEach { repository.insert(receipt(it)) }
        repeat(10) { book(repository.getReceiptById(it + 1)!!, "snapshot-$it") }
        val expectedTrips = db.logbookDao().getAllTrips()
        val expectedIds = repository.getAllReceiptsList().associate { it.id to it.internalId }
        backup()
        withContext(Dispatchers.IO) { db.clearAllTables() }
        clearPreferences()
        restore(RestoreMode.REPLACE_FULL)
        assertEquals(expectedTrips, db.logbookDao().getAllTrips())
        assertEquals(expectedIds, repository.getAllReceiptsList().associate { it.id to it.internalId })
        assertTripIntegrity()

        // Same manual booking as E10B-01: creates Trip 11 and expense Receipt 71.
        val localTripId = book(receipt(), "new-local")
        val expenseId = db.logbookDao().getTrip(localTripId)!!.expenseReceiptId!!
        assertEquals(71, expenseId)
        val routeId = db.logbookDao().upsertStandardRoute(StandardRoute(name = "Lokal", startAddress = "A",
            destinationAddress = "B", distanceKm = 25.0, createdAt = now, updatedAt = now))
        repository.insert(receipt(72))
        val sourceTrip = book(repository.getReceiptById(72)!!, "new-source", routeId)
        val localTrips = db.logbookDao().getAllTrips()
        val localReceipts = repository.getAllReceiptsList().associate { it.id to it.internalId }
        repeat(2) {
            restore(RestoreMode.MERGE)
            assertEquals(localTrips, db.logbookDao().getAllTrips())
            assertEquals(localReceipts, repository.getAllReceiptsList().associate { it.id to it.internalId })
            assertTripIntegrity()
        }
        repeat(2) {
            restore(RestoreMode.REPLACE_FULL)
            assertNull(db.logbookDao().getTrip(localTripId))
            assertNull(db.logbookDao().getTrip(sourceTrip))
            assertNull(repository.getReceiptById(expenseId))
            assertEquals(expectedTrips, db.logbookDao().getAllTrips())
            assertEquals(expectedIds, repository.getAllReceiptsList().associate { it.id to it.internalId })
            assertTrue(db.logbookDao().getAllStandardRoutes().isEmpty())
            assertTripIntegrity()
        }
    }

    @Test fun invalidSnapshotReceiptReferenceRollsBackSupplementalClearingAndPreferences() = runTest {
        seedProperty()
        repository.insert(receipt(1))
        book(repository.getReceiptById(1)!!, "snapshot")
        backup()
        val local = book(receipt(), "local")
        val before = db.logbookDao().getAllTrips()
        val prefs = context.getSharedPreferences("logbook_drafts", 0)
        prefs.edit().putString("local", "keep").commit()
        for (field in listOf("expenseReceiptId", "sourceReceiptId")) {
            val bad = JSONObject(payload.toString())
            bad.getJSONArray("logbookTrips").getJSONObject(0).put(field, 99999)
            val result = runCatching { SupplementalDriveBackup.restorePayload(context, db, bad, restoreMode = RestoreMode.REPLACE_FULL) }
            assertTrue("Invalid $field accepted", result.isFailure)
            assertEquals(before, db.logbookDao().getAllTrips())
            assertNotNull(db.logbookDao().getTrip(local))
            assertEquals("keep", prefs.getString("local", null))
        }
    }

    @Test fun insertFailureAfterClearingRollsBackRoom() = runTest {
        seedProperty()
        repository.insert(receipt(1))
        book(repository.getReceiptById(1)!!, "snapshot")
        backup()
        book(receipt(), "local")
        val before = db.logbookDao().getAllTrips()
        db.withTransaction { db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER phase10c_fail BEFORE INSERT ON logbook_trips BEGIN SELECT RAISE(ABORT, 'injected insert failure'); END") }
        try {
            assertTrue(runCatching { SupplementalDriveBackup.restorePayload(context, db, payload, restoreMode = RestoreMode.REPLACE_FULL) }.isFailure)
            assertEquals(before, db.logbookDao().getAllTrips())
        } finally { db.withTransaction { db.openHelper.writableDatabase.execSQL("DROP TRIGGER phase10c_fail") } }
    }
}
