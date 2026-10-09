package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase5ARestoreConflictTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: ReceiptRepository
    private val old = "2026-01-01T00:00:00Z"
    private val newer = "2026-10-08T00:00:00Z"

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = ReceiptRepository(db.receiptDao(), db.propertyDao(), db.receiptEntityDao(), db.belegDao(),
            db.exportAuditDao(), db.receiptDocumentDao(), db.managedDocumentDao())
    }
    @After fun tearDown() {
        db.close()
        PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
    }

    @Test fun mergePreservesBankCorrectionRemovedLinksAndTripCancellationTwice() = runTest {
        val tx = BankTransaction("tx", "account", "2026-01-01", amount = 850.0, updatedAt = old)
        val assignment = BankRentAssignment("rent", "tx", "property", "old-unit", "2026-01", "Alt", 850.0,
            "KALTMIETE", createdAt = old, updatedAt = old)
        val link = BankReceiptLink("link", "tx", 8, allocatedAmount = 850.0, createdAt = old)
        val trip = LogbookTrip(id = 7, date = "2026-01-01", purpose = "Besichtigung", startAddress = "A",
            destinationAddress = "B", taxDistanceKm = 25.0, kilometerSource = "MANUELL", createdAt = old, updatedAt = old)
        db.bankDao().upsertTransaction(tx)
        db.bankRentAssignmentDao().upsert(assignment)
        db.bankDao().upsertLink(link)
        db.logbookDao().upsertTrip(trip)
        val backup = SupplementalDriveBackup.createPayload(context, db)
        val correctedTx = tx.copy(propertyId = "correct-property", updatedAt = newer)
        val correctedAssignment = assignment.copy(unitId = "new-unit", tenantReference = "Neu", updatedAt = newer)
        val cancelledTrip = trip.copy(taxDistanceKm = 22.0, cancelledAt = newer, updatedAt = newer,
            correctionReason = "Fahrt storniert", historyJson = "[{\"action\":\"Storno\"}]")
        db.bankDao().upsertTransaction(correctedTx)
        db.bankRentAssignmentDao().upsert(correctedAssignment)
        db.bankDao().deleteLink(link.linkId)
        db.logbookDao().upsertTrip(cancelledTrip)
        repeat(2) {
            SupplementalDriveBackup.restorePayload(context, db, backup)
            assertEquals(correctedTx, db.bankDao().getTransaction("tx"))
            assertEquals(correctedAssignment, db.bankRentAssignmentDao().getById("rent"))
            assertTrue(db.bankDao().getAllLinks().isEmpty())
            assertEquals(cancelledTrip, db.logbookDao().getTrip(7))
        }
        SupplementalDriveBackup.restorePayload(context, db, backup, restoreMode = RestoreMode.REPLACE_FULL)
        assertEquals(tx, db.bankDao().getTransaction("tx"))
        assertEquals(assignment, db.bankRentAssignmentDao().getById("rent"))
        assertEquals(link, db.bankDao().getLink("link"))
        assertEquals(trip, db.logbookDao().getTrip(7))
    }

    @Test fun removedRentAssignmentIsNotResurrectedByOlderTransaction() = runTest {
        val tx = BankTransaction("tx", "account", "2026-01-01", amount = 850.0, updatedAt = old)
        val assignment = BankRentAssignment("rent", "tx", "property", "unit", "2026-01", "Alt", 850.0,
            "KALTMIETE", createdAt = old, updatedAt = old)
        db.bankDao().upsertTransaction(tx)
        db.bankRentAssignmentDao().upsert(assignment)
        val backup = SupplementalDriveBackup.createPayload(context, db)
        db.bankRentAssignmentDao().deleteById("rent")
        db.bankDao().upsertTransaction(tx.copy(updatedAt = newer))
        repeat(2) { SupplementalDriveBackup.restorePayload(context, db, backup) }
        assertTrue(db.bankRentAssignmentDao().getAll().isEmpty())
    }

    @Test fun unversionedLocalBankStateDoesNotAuthorizeResurrectingMissingRelationship() = runTest {
        val tx = BankTransaction("unversioned", "account", "2026-01-01", amount = 100.0)
        val link = BankReceiptLink("removed", tx.transactionId, 8, allocatedAmount = 100.0)
        db.bankDao().upsertTransaction(tx)
        db.bankDao().upsertLink(link)
        val backup = SupplementalDriveBackup.createPayload(context, db)
        db.bankDao().deleteLink(link.linkId)
        repeat(2) { SupplementalDriveBackup.restorePayload(context, db, backup) }
        assertTrue(db.bankDao().getAllLinks().isEmpty())
    }

    @Test fun missingAndStrictlyNewerBankRowsAreImportedButUnknownVersionKeepsLocal() = runTest {
        val remote = BankTransaction("same", "account", "2026-01-01", amount = 10.0, updatedAt = newer)
        db.bankDao().upsertTransaction(remote)
        db.bankDao().upsertTransaction(remote.copy(transactionId = "backup-only"))
        val backup = SupplementalDriveBackup.createPayload(context, db)
        db.bankDao().upsertTransaction(remote.copy(propertyId = "older", updatedAt = old))
        db.bankDao().upsertTransaction(remote.copy(transactionId = "local-only", propertyId = "local"))
        SupplementalDriveBackup.restorePayload(context, db, backup)
        assertEquals(remote, db.bankDao().getTransaction("same"))
        assertEquals(setOf("same", "backup-only", "local-only"), db.bankDao().getAllTransactions().map { it.transactionId }.toSet())
        val unversioned = remote.copy(propertyId = "unknown", updatedAt = "not-a-timestamp")
        db.bankDao().upsertTransaction(unversioned)
        repeat(2) { SupplementalDriveBackup.restorePayload(context, db, backup) }
        assertEquals(unversioned, db.bankDao().getTransaction("same"))
    }

    @Test fun receiptMergeKeepsAccountingIdentityAndFullDocumentStateExactlyOnSecondRestore() = runTest {
        val receipt = Receipt(aussteller = "Alt", datum = "2026-01-01", uhrzeit = "", bruttobetrag = 760.0,
            hauptkategorie = "Renovierung", unterkategorie = "Material", kontoNr = "4800", beschreibung = "",
            internalId = "stable-receipt")
        val id = repository.insert(receipt).toInt()
        val backup = repository.getReceiptById(id)!!
        repository.insert(backup.copy(aussteller = "Neu", bruttobetrag = 850.0))
        val corrected = repository.getReceiptById(id)!!
        val docs = repository.getAllManagedDocuments()
        repeat(2) {
            repository.upsertRestoredReceipt(backup, RestoreMode.MERGE)
            assertEquals(corrected, repository.getReceiptById(id))
            assertEquals(docs, repository.getAllManagedDocuments())
        }
        repository.upsertRestoredReceipt(backup, RestoreMode.REPLACE_FULL)
        assertEquals(backup, repository.getReceiptById(id))
    }
}
