package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LogbookBookingStoreTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ReceiptRepository
    private lateinit var store: LogbookBookingStore
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        repository = ReceiptRepository(db.receiptDao(), db.propertyDao(), db.receiptEntityDao(), db.belegDao())
        store = LogbookBookingStore(db, repository)
    }
    @After fun close() { db.close() }
    private fun source() = Receipt(id = 81, aussteller = "Baumarkt", datum = "2026-09-01", uhrzeit = "", bruttobetrag = 50.0,
        hauptkategorie = "Sanierung", unterkategorie = "", kontoNr = "", beschreibung = "Original", propertyId = "property-other", wohneinheit = "OG")
    private suspend fun save(receipt: Receipt = source(), key: String = "request-1", tripTime: String = "") = store.saveLogbookTrip(receipt, "Material", "A", "B",
        listOf(TripStop("A", "Start", 0), TripStop("B", "Ziel", 1)), TripRouteMode.EINFACH, false,
        DistanceEvidence(manualKm = 20.0, manuallyConfirmed = true), tripDate = "2026-09-12", propertyId = receipt.propertyId, bookingKey = key, tripTime = tripTime)

    @Test fun newTripDoesNotInheritTheSourceReceiptTime() = runBlocking {
        val original = source().copy(uhrzeit = "10:30")
        repository.insert(original)
        val originalBefore = repository.getReceiptById(original.id)
        val id = save(original).getOrThrow()
        val trip = db.logbookDao().getTrip(id)!!
        assertEquals("", trip.time)
        assertEquals("", repository.getReceiptById(trip.expenseReceiptId!!)!!.uhrzeit)
        assertEquals(originalBefore, repository.getReceiptById(original.id))
    }

    @Test fun explicitTripTimeIsStoredSeparatelyFromTheSourceReceipt() = runBlocking {
        val original = source().copy(uhrzeit = "10:30")
        repository.insert(original)
        val originalBefore = repository.getReceiptById(original.id)
        val id = save(original, tripTime = "14:45").getOrThrow()
        val trip = db.logbookDao().getTrip(id)!!
        assertEquals("14:45", trip.time)
        assertEquals("14:45", repository.getReceiptById(trip.expenseReceiptId!!)!!.uhrzeit)
        assertEquals(originalBefore, repository.getReceiptById(original.id))
    }

    @Test fun invalidTripTimeRejectsWithoutCreatingATripOrExpense() = runBlocking {
        val original = source().copy(uhrzeit = "10:30")
        repository.insert(original)
        val before = repository.getAllReceiptsIncludingDeletedList()
        assertTrue(save(original, tripTime = "25:99").isFailure)
        assertEquals(before, repository.getAllReceiptsIncludingDeletedList())
        assertTrue(db.logbookDao().getAllTrips().isEmpty())
        assertEquals(1, db.receiptEntityDao().getAllEntities().first().size)
    }

    @Test fun tripTimeCorrectionUpdatesExpenseAndKeepsPreviousTimeInHistory() = runBlocking {
        val original = source().copy(uhrzeit = "10:30")
        repository.insert(original)
        val originalBefore = repository.getReceiptById(original.id)
        val id = save(original, tripTime = "14:45").getOrThrow()
        store.correct(id, "2026-09-13", "Besichtigung", 30.0, "Fahrtzeit geprüft", time = "15:20").getOrThrow()
        val corrected = db.logbookDao().getTrip(id)!!
        assertEquals("15:20", corrected.time)
        assertEquals("15:20", repository.getReceiptById(corrected.expenseReceiptId!!)!!.uhrzeit)
        val previous = org.json.JSONArray(corrected.historyJson).getJSONObject(0).getJSONObject("previous")
        assertEquals("14:45", previous.getString("time"))
        assertEquals("2026-09-12", previous.getString("date"))
        assertEquals(originalBefore, repository.getReceiptById(original.id))
    }

    @Test fun correctionWithoutTimePreservesExistingTimeAndExplicitBlankClearsIt() = runBlocking {
        repository.insert(source())
        val id = save(tripTime = "14:45").getOrThrow()
        store.correct(id, "2026-09-13", "Besichtigung", 30.0, "Kilometer geprüft").getOrThrow()
        val corrected = db.logbookDao().getTrip(id)!!
        assertEquals("14:45", corrected.time)
        assertEquals("14:45", repository.getReceiptById(corrected.expenseReceiptId!!)!!.uhrzeit)
        store.correct(id, "2026-09-13", "Besichtigung", 30.0, "Uhrzeit unbekannt", time = "").getOrThrow()
        val cleared = db.logbookDao().getTrip(id)!!
        assertEquals("", cleared.time)
        assertEquals("", repository.getReceiptById(cleared.expenseReceiptId!!)!!.uhrzeit)
        val previous = org.json.JSONArray(cleared.historyJson).getJSONObject(1).getJSONObject("previous")
        assertEquals("14:45", previous.getString("time"))
    }

    @Test fun invalidTimeCorrectionPreservesTripExpenseAndHistory() = runBlocking {
        repository.insert(source())
        val id = save(tripTime = "14:45").getOrThrow()
        val beforeTrip = db.logbookDao().getTrip(id)
        val beforeReceipts = repository.getAllReceiptsIncludingDeletedList()
        assertTrue(store.correct(id, "2026-09-13", "Besichtigung", 30.0, "Fahrtzeit geprüft", time = "25:99").isFailure)
        assertEquals(beforeTrip, db.logbookDao().getTrip(id))
        assertEquals(beforeReceipts, repository.getAllReceiptsIncludingDeletedList())
    }

    @Test fun tripDateAndPropertyDoNotChangeTheSourceReceipt() = runBlocking {
        repository.insert(source())
        val id = save().getOrThrow()
        val trip = db.logbookDao().getTrip(id)!!
        val expense = repository.getReceiptById(trip.expenseReceiptId!!)!!
        assertEquals("2026-09-12", trip.date)
        assertEquals("2026-09-12", expense.datum)
        assertEquals("property-other", expense.propertyId)
        assertEquals("OG", expense.wohneinheit)
        assertEquals("property-other", trip.propertyId)
        assertEquals(source(), repository.getReceiptById(81)!!.copy(internalId = "", displayId = null))
    }
    @Test fun aTripInsertFailureRollsBackAllExpenseWrites() = runBlocking {
        repository.insert(source())
        val before = repository.getAllReceiptsIncludingDeletedList().size
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_trip BEFORE INSERT ON logbook_trips BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        assertTrue(save().isFailure)
        assertEquals(before, repository.getAllReceiptsIncludingDeletedList().size)
        assertTrue(db.logbookDao().getAllTrips().isEmpty())
        assertEquals(1, db.receiptEntityDao().getAllEntities().first().size)
    }
    @Test fun concurrentRetriesCreateOnlyOneTripAndExpense() = runBlocking {
        repository.insert(source())
        val ids = (1..12).map { async(Dispatchers.IO) { save().getOrThrow() } }.awaitAll()
        assertEquals(1, ids.toSet().size)
        assertEquals(1, db.logbookDao().getAllTrips().size)
        assertEquals(2, repository.getAllReceiptsIncludingDeletedList().size)
    }
    @Test fun correctionAndCancellationKeepHistoryAndUpdateTheLinkedExpense() = runBlocking {
        repository.insert(source())
        val id = save().getOrThrow()
        val expenseId = db.logbookDao().getTrip(id)!!.expenseReceiptId!!
        val expense = repository.getReceiptById(expenseId)!!
        repository.insert(expense.copy(freigabestatus = "FREIGEGEBEN"))
        store.correct(id, "2026-09-13", "Besichtigung", 30.0, "Tacho geprüft").getOrThrow()
        val corrected = db.logbookDao().getTrip(id)!!
        assertEquals(30.0, corrected.taxDistanceKm, 0.001)
        assertTrue(corrected.historyJson.contains("Material"))
        assertEquals(9.0, repository.getReceiptById(expenseId)!!.bruttobetrag, 0.001)
        assertEquals("OFFEN", repository.getReceiptById(expenseId)!!.freigabestatus)
        assertEquals("2026-09-01", repository.getReceiptById(81)!!.datum)
        store.cancel(id, "Fahrt entfällt").getOrThrow()
        assertTrue(db.logbookDao().getTrip(id)!!.cancelledAt.isNotBlank())
        assertTrue(db.logbookDao().getTrip(id)!!.historyJson.contains("Storno"))
        assertEquals("DELETE_PENDING", repository.getReceiptById(expenseId)!!.deletionStatus)
        assertTrue(store.correct(id, "2026-09-13", "X", 10.0, "X").isFailure)
    }

    @Test fun invalidDistanceAndUnrepresentableGeneratedReceiptNeverMutateExistingTripOrExpense() = runBlocking {
        repository.insert(source())
        val id = save().getOrThrow()
        val beforeTrip = db.logbookDao().getTrip(id)!!
        val beforeReceipts = repository.getAllReceiptsIncludingDeletedList()
        for (km in listOf(-12.5, Double.NaN, Double.POSITIVE_INFINITY, 1e200)) {
            val result = store.saveLogbookTrip(source().copy(id = 0), "Besichtigung", "A", "B",
                listOf(TripStop("A", "Start", 0), TripStop("B", "Ziel", 1)), TripRouteMode.EINFACH, false,
                DistanceEvidence(manualKm = km, routedKm = 20.0, manuallyConfirmed = true),
                correctionReason = "Geprüfte Quelle", tripDate = "2026-10-14", propertyId = source().propertyId, bookingKey = "unsafe")
            assertTrue(result.isFailure)
            assertEquals(beforeTrip, db.logbookDao().getTrip(id))
            assertEquals(beforeReceipts, repository.getAllReceiptsIncludingDeletedList())
        }
        assertTrue(store.correct(id, "2026-10-14", "Besichtigung", 1e200, "Geprüft").isFailure)
        assertEquals(beforeTrip, db.logbookDao().getTrip(id))
        assertEquals(beforeReceipts, repository.getAllReceiptsIncludingDeletedList())
        val nonfiniteCompanion = store.saveLogbookTrip(source().copy(id = 0), "Besichtigung", "A", "B",
            listOf(TripStop("A", "Start", 0), TripStop("B", "Ziel", 1)), TripRouteMode.EINFACH, false,
            DistanceEvidence(manualKm = 20.0, aiEstimatedKm = Double.NaN, manuallyConfirmed = true),
            tripDate = "2026-10-14", propertyId = source().propertyId, bookingKey = "unsafe-companion")
        assertTrue(nonfiniteCompanion.isFailure)
        assertEquals(beforeTrip, db.logbookDao().getTrip(id))
        assertEquals(beforeReceipts, repository.getAllReceiptsIncludingDeletedList())
    }
    @Test fun exportedExpensesCannotBeChangedOrCancelled() = runBlocking {
        repository.insert(source())
        val id = save().getOrThrow()
        val expense = repository.getReceiptById(db.logbookDao().getTrip(id)!!.expenseReceiptId!!)!!
        repository.insert(expense.copy(exportStatus = "EXPORTIERT", exportlaufId = "run-1"))
        assertTrue(store.correct(id, "2026-09-13", "X", 10.0, "X").isFailure)
        assertTrue(store.cancel(id, "X").isFailure)
        assertEquals(20.0, db.logbookDao().getTrip(id)!!.taxDistanceKm, 0.001)
        assertEquals("ACTIVE", repository.getReceiptById(expense.id)!!.deletionStatus)
    }
}
