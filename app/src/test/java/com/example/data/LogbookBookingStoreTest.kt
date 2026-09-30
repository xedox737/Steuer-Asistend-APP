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
    private suspend fun save(receipt: Receipt = source(), key: String = "request-1") = store.saveLogbookTrip(receipt, "Material", "A", "B",
        listOf(TripStop("A", "Start", 0), TripStop("B", "Ziel", 1)), TripRouteMode.EINFACH, false,
        DistanceEvidence(manualKm = 20.0, manuallyConfirmed = true), tripDate = "2026-09-12", propertyId = receipt.propertyId, bookingKey = key)

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
