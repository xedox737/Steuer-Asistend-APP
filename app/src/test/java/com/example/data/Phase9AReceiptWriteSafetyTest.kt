package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase9AReceiptWriteSafetyTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ReceiptRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).allowMainThreadQueries().build()
        repository = ReceiptRepository(db.receiptDao(), db.propertyDao())
    }
    @After fun close() = db.close()

    @Test fun overflowCannotBecomeEmptySerializationOrPartiallyUpdateReceipt() = runTest {
        val good = listOf(ReceiptItem("A", 2.0, 10.0, 20.0), ReceiptItem("B", 1.0, 9.0, 9.0))
        val savedId = repository.insert(receipt(ReceiptItemConverter.toJson(good))).toInt()
        val original = repository.getReceiptById(savedId)!!
        val bad = ReceiptItem("Überlauf", 1e200, 1e200, Double.POSITIVE_INFINITY)
        assertTrue(ReceiptItemConverter.serialize(good + bad).isFailure)
        assertTrue(runCatching { ReceiptItemConverter.toJson(good + bad) }.isFailure)
        val json = """[{"bezeichnung":"Überlauf","menge":1e200,"einzelpreis":1e200,"gesamtpreis":1e308}]"""
        assertTrue(runCatching { repository.insert(original.copy(aussteller = "Darf nicht geschrieben werden", positionenJson = json)) }.isFailure)
        assertEquals(original, repository.getReceiptById(savedId))
        assertEquals(good, repository.getReceiptById(savedId)!!.getPositionenList())
    }

    @Test fun aggregateOverflowAndMalformedJsonAlsoBlockAtRepositoryBoundary() = runTest {
        val originalId = repository.insert(receipt("[]")).toInt()
        val original = repository.getReceiptById(originalId)!!
        val huge = ReceiptItem("Groß", 1.0, 1e308, 1e308)
        assertTrue(ReceiptItemConverter.serialize(listOf(huge, huge)).isFailure)
        assertTrue(runCatching { repository.insertAll(listOf(original.copy(aussteller = "Änderung"), original.copy(positionenJson = "kaputt"))) }.isFailure)
        assertEquals(original, repository.getReceiptById(originalId))
    }

    @Test fun safeLargeValuesZeroAndIntentionalRemovalRoundTrip() = runTest {
        val good = listOf(ReceiptItem("Groß", 1e100, 1e100, 1e200), ReceiptItem("Null", 0.0, 0.0, 0.0), ReceiptItem("Komma", 2.5, 1.13, 2.825))
        val id = repository.insert(receipt(ReceiptItemConverter.toJson(good))).toInt()
        val original = repository.getReceiptById(id)!!
        assertEquals(good, original.getPositionenList())
        repository.insert(original.copy(positionenJson = ReceiptItemConverter.toJson(emptyList())))
        assertTrue(repository.getReceiptById(id)!!.getPositionenList().isEmpty())
        assertEquals(id, repository.getReceiptById(id)!!.id)
    }
    @Test fun damagedLegacyPositionsCannotBeErasedByAnOtherwiseValidEdit() = runTest {
        val raw = "[{incomplete"
        val existing = receipt(raw).copy(id = 44, displayId = "BLG-44")
        db.receiptDao().insertReceipt(existing)
        assertTrue(runCatching { repository.insert(existing.copy(aussteller = "Neu", positionenJson = "")) }.isFailure)
        assertEquals(existing, repository.getReceiptById(44))
        assertTrue(ReceiptItemConverter.serialize(listOf(ReceiptItem("Netto", 1.0, 1.0, 1.0, steuersatz = -100.0))).isFailure)
    }

    private fun receipt(items: String) = Receipt(aussteller = "Original", datum = "2026-10-13", uhrzeit = "12:00", bruttobetrag = 29.0,
        hauptkategorie = "Sonstige Ausgaben", unterkategorie = "", kontoNr = "4970", beschreibung = "Unverändert", positionenJson = items, internalId = "phase9a-original")
}
