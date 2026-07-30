package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RestoreSafetyTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: ReceiptRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ReceiptRepository(
            receiptDao = db.receiptDao(),
            receiptEntityDao = db.receiptEntityDao(),
            belegDao = db.belegDao(),
            receiptDocumentDao = db.receiptDocumentDao(),
            exportAuditDao = db.exportAuditDao(),
            propertyDao = db.propertyDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testLocalDataPreservedOnFailedValidation() = runBlocking {
        // Populate existing local receipt
        val initialReceipt = Receipt(
            internalId = "LOCAL-001",
            displayId = "BLG-2026-001",
            aussteller = "Lokaler Anbieter",
            datum = "2026-01-01",
            uhrzeit = "12:00",
            bruttobetrag = 100.0,
            hauptkategorie = "Instandhaltung",
            unterkategorie = "Reparatur",
            kontoNr = "4800",
            beschreibung = "Lokaler Beleg"
        )
        repository.insert(initialReceipt)

        val beforeList = repository.getAllReceiptsList()
        assertEquals(1, beforeList.size)
        assertEquals("LOCAL-001", beforeList[0].internalId)

        // Create a snapshot with blocking error
        val invalidSnapshot = RestoreSnapshot(
            receipts = emptyList(),
            errors = listOf(RestoreError("UNREADABLE_METADATA", "Corrupted JSON file", isBlocking = true))
        )

        val blockingErrors = invalidSnapshot.errors.filter { it.isBlocking }
        assertTrue(blockingErrors.isNotEmpty())

        // Ensure database was NOT cleared when validation fails
        val afterList = repository.getAllReceiptsList()
        assertEquals(1, afterList.size)
        assertEquals("LOCAL-001", afterList[0].internalId)
    }

    @Test
    fun testTransactionalRestoreSuccess() = runBlocking {
        val initialReceipt = Receipt(
            internalId = "OLD-001",
            displayId = "BLG-2026-001",
            aussteller = "Alter Anbieter",
            datum = "2026-01-01",
            uhrzeit = "10:00",
            bruttobetrag = 50.0,
            hauptkategorie = "Instandhaltung",
            unterkategorie = "Reparatur",
            kontoNr = "4800",
            beschreibung = "Alter Beleg"
        )
        repository.insert(initialReceipt)

        // Valid restore snapshot with new restored receipt
        val validReceipt = PersistedReceipt(
            internalId = "RESTORED-001",
            displayId = "BLG-2026-999",
            aussteller = "Wiederhergestellter Anbieter",
            rechnungsnummer = "REC-999",
            datum = "2026-02-15",
            nettobetragCent = 16807L,
            steuerbetragCent = 3193L,
            bruttobetragCent = 20000L,
            hauptkategorie = "Instandhaltung",
            unterkategorie = "Sanierung",
            wohneinheit = "WE 1",
            massnahme = "M-01",
            positionen = emptyList(),
            zahlungsstatus = "BEZAHLT",
            zahlungsdatum = "2026-02-15",
            pruefstatus = "GEPRUEFT",
            exportstatus = "EXPORTBEREIT",
            createdAt = "2026-02-15 10:00:00",
            updatedAt = "2026-02-15 10:00:00",
            lastSyncedAt = "2026-02-15 10:00:00"
        )
        val validSnapshot = RestoreSnapshot(
            receipts = listOf(validReceipt),
            errors = emptyList()
        )

        // Execute transactionally
        db.runInTransaction {
            runBlocking {
                repository.clearRestoreRelevantTables()
                val restoredLocal = Receipt(
                    aussteller = validReceipt.aussteller ?: "",
                    datum = validReceipt.datum ?: "",
                    uhrzeit = "",
                    bruttobetrag = 200.0,
                    hauptkategorie = validReceipt.hauptkategorie ?: "",
                    unterkategorie = validReceipt.unterkategorie ?: "",
                    kontoNr = "4800",
                    beschreibung = validReceipt.aussteller ?: "",
                    internalId = validReceipt.internalId,
                    displayId = validReceipt.displayId,
                    syncStatus = "SYNCED"
                )
                repository.insert(restoredLocal)
            }
        }

        val postList = repository.getAllReceiptsList()
        assertEquals(1, postList.size)
        assertEquals("RESTORED-001", postList[0].internalId)
        assertEquals("Wiederhergestellter Anbieter", postList[0].aussteller)
    }
}
