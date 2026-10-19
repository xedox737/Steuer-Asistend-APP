package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** No mocked conflict policy: actual Room, coordinator and receipt original downloader. */
@RunWith(RobolectricTestRunner::class)
class Phase10DRestoreMetadataTest : Phase10CRestoreFixture() {
    private val older = "2026-01-01T10:00:00Z"
    private val newer = "2026-02-01T10:00:00Z"

    private suspend fun seedReviewed(): ManagedDocument {
        seedProperty()
        repository.insert(receipt(1))
        val doc = repository.getAllManagedDocuments().single().copy(
            source = DocumentSource.DATEIIMPORT.name, createdAt = older, updatedAt = older,
            reviewStatus = DocumentReviewStatus.GEPRUEFT.name,
            extractedFieldsJson = JSONObject().put("_displayDescription", "Bestätigter Text")
                .put("rechnungNummer", "RE-10D-1").toString())
        repository.upsertManagedDocument(doc)
        backup()
        return doc
    }

    @Test fun technicalReceiptReindexMustNotInventANewerDocumentVersion() = runTest {
        val before = seedReviewed()
        val receipt = repository.getReceiptById(1)!!
        repository.insert(receipt.copy(lastSyncedAt = "technical sync"))
        assertEquals(before.updatedAt, repository.getManagedDocument(before.documentId)!!.updatedAt)
        val incoming = before.copy(updatedAt = newer, extractedFieldsJson = "{\"rechnungNummer\":\"RE-NEU\"}")
        val merged = SupplementalDriveBackup.mergeManagedDocument(context,
            repository.getManagedDocument(before.documentId), incoming, RestoreMode.MERGE)
        assertEquals("A real newer review must win after a technical sync", incoming.extractedFieldsJson, merged.extractedFieldsJson)
    }

    @Test fun freshDoubleMergeMustKeepConfirmedMetadataAndProvenance() = runTest {
        val before = seedReviewed()
        withContext(Dispatchers.IO) { db.clearAllTables() }
        clearPreferences()
        repeat(2) {
            restore(RestoreMode.MERGE)
            assertReview(before, repository.getManagedDocument(before.documentId)!!)
        }
    }

    @Test fun replaceMustKeepConfirmedMetadataAndProvenance() = runTest {
        val before = seedReviewed()
        restore(RestoreMode.REPLACE_FULL)
        assertReview(before, repository.getManagedDocument(before.documentId)!!)
    }

    @Test fun originalDownloadMustPreserveVersionAndSubsequentMergeOrdering() = runTest {
        val before = seedReviewed()
        restore(RestoreMode.REPLACE_FULL)
        val bytes = "%PDF-1.4\nphase10d-original".toByteArray()
        val cache = File(context.cacheDir, "phase10d-original.pdf").apply { writeBytes(bytes) }
        try {
            val restored = repository.getManagedDocument(before.documentId)!!.copy(
                localUri = cache.absolutePath, sha256 = StableDocumentIdentity.sha256(bytes),
                storedFilename = "original.pdf")
            drive.downloadReceiptOriginals("test-token", repository.getReceiptById(1)!!, listOf(restored), emptyList())
            val after = repository.getManagedDocument(before.documentId)!!
            assertEquals(before.updatedAt, after.updatedAt)
            val incoming = after.copy(updatedAt = newer, extractedFieldsJson = "{\"rechnungNummer\":\"NEWER\"}")
            val docs = payload.getJSONArray("managedDocuments")
            docs.getJSONObject(0).put("updatedAt", newer).put("extractedFieldsJson", incoming.extractedFieldsJson)
            SupplementalDriveBackup.restorePayload(context, db, payload, restoreMode = RestoreMode.MERGE)
            assertEquals(incoming.extractedFieldsJson, repository.getManagedDocument(before.documentId)!!.extractedFieldsJson)
            docs.getJSONObject(0).put("updatedAt", older).put("extractedFieldsJson", "{\"rechnungNummer\":\"OLDER\"}")
            SupplementalDriveBackup.restorePayload(context, db, payload, restoreMode = RestoreMode.MERGE)
            assertEquals(incoming.extractedFieldsJson, repository.getManagedDocument(before.documentId)!!.extractedFieldsJson)
        } finally { cache.delete() }
    }

    @Test fun actualSupplementalMergeUsesDocumentVersions() = runTest {
        val before = seedReviewed()
        val docs = payload.getJSONArray("managedDocuments")
        repository.upsertManagedDocument(before.copy(updatedAt = newer, title = "Neu lokal"))
        SupplementalDriveBackup.restorePayload(context, db, payload, restoreMode = RestoreMode.MERGE)
        assertEquals("Neu lokal", repository.getManagedDocument(before.documentId)!!.title)
        docs.getJSONObject(0).put("updatedAt", "2026-03-01T10:00:00Z").put("title", "Neu Backup")
        SupplementalDriveBackup.restorePayload(context, db, payload, restoreMode = RestoreMode.MERGE)
        assertEquals("Neu Backup", repository.getManagedDocument(before.documentId)!!.title)
    }

    @Test fun bankAndTripVersionsSurviveRestoreAndOrderBothFurtherMerges() = runTest {
        seedReviewed()
        val transaction = BankTransaction("transaction-10d", "account-10d", "2026-01-01",
            amount = 10.0, category = "Alt", updatedAt = older)
        val trip = LogbookTrip(id = 71, date = "2026-01-01", purpose = "Alt", startAddress = "A",
            destinationAddress = "B", taxDistanceKm = 10.0, manualKm = 10.0, kilometerSource = "MANUELL",
            createdAt = older, updatedAt = older, propertyId = "property-a")
        db.bankDao().upsertTransaction(transaction)
        db.logbookDao().upsertTrip(trip)
        val oldBackup = SupplementalDriveBackup.createPayload(context, db)
        db.bankDao().upsertTransaction(transaction.copy(category = "Neu lokal", updatedAt = newer))
        db.logbookDao().upsertTrip(trip.copy(purpose = "Neu lokal", updatedAt = newer))
        SupplementalDriveBackup.restorePayload(context, db, oldBackup, restoreMode = RestoreMode.MERGE)
        assertEquals("Neu lokal", db.bankDao().getAllTransactions().single().category)
        assertEquals(newer, db.bankDao().getAllTransactions().single().updatedAt)
        assertEquals("Neu lokal", db.logbookDao().getTrip(71)!!.purpose)
        oldBackup.getJSONArray("bankTransactions").getJSONObject(0)
            .put("category", "Neu Backup").put("updatedAt", "2026-03-01T10:00:00Z")
        oldBackup.getJSONArray("logbookTrips").getJSONObject(0)
            .put("purpose", "Neu Backup").put("updatedAt", "2026-03-01T10:00:00Z")
        SupplementalDriveBackup.restorePayload(context, db, oldBackup, restoreMode = RestoreMode.MERGE)
        assertEquals("Neu Backup", db.bankDao().getAllTransactions().single().category)
        assertEquals("Neu Backup", db.logbookDao().getTrip(71)!!.purpose)
    }

    @Test fun legacyBindingsRemainCompatibilityAliasesAcrossRestoreAndRename() = runTest {
        seedReviewed()
        val before = repository.getAllProperties().single()
        val units = drive.getWohneinheitenFromPrefs(before)
        val prefs = context.getSharedPreferences("wohneinheiten_prefs", 0)
        val key = "property_property-a_unit_WE 01, EG links_id"
        assertFalse(prefs.contains(key))
        drive.saveWohneinheitenToPrefs(units, RestoreMode.MERGE, before.propertyId)
        assertEquals("unit-a", prefs.getString(key, null))
        backup()
        restore(RestoreMode.MERGE)
        assertEquals(units.map { it.unitId }, drive.getWohneinheitenFromPrefs(before).map { it.unitId })
        val renamed = before.copy(wohneinheiten = UnitIdentity.encode(listOf(UnitIdentity.Entry("unit-a", "Neuer Name"))))
        repository.updatePropertyMetadata(renamed)
        val after = drive.getWohneinheitenFromPrefs(renamed)
        assertEquals("unit-a", after.single().unitId)
        assertEquals("Neuer Name", after.single().name)
        prefs.edit().putString(key, "foreign-id").commit()
        assertFalse(UnitIdentity.legacyAliasMatches(prefs, before.propertyId, "unit-a", "WE 01, EG links"))
        assertEquals("unit-a", drive.getWohneinheitenFromPrefs(renamed).single().unitId)
    }

    @Test fun authoredReceiptChangeStillAdvancesDocumentVersion() = runTest {
        val before = seedReviewed()
        repository.insert(repository.getReceiptById(1)!!.copy(aussteller = "Fachlich geändert"))
        val after = repository.getManagedDocument(before.documentId)!!
        assertEquals("Fachlich geändert", after.title)
        assertTrue(RestoreConflictPolicy.backupIsNewer(before.updatedAt, after.updatedAt))
        assertEquals(before.source, after.source)
    }

    private fun assertReview(expected: ManagedDocument, actual: ManagedDocument) {
        assertEquals(expected.documentId, actual.documentId)
        assertEquals(expected.propertyId, actual.propertyId)
        assertEquals(expected.unitId, actual.unitId)
        assertEquals(expected.source, actual.source)
        assertEquals(expected.createdAt, actual.createdAt)
        assertEquals(expected.updatedAt, actual.updatedAt)
        assertEquals(expected.reviewStatus, actual.reviewStatus)
        for (key in listOf("_displayDescription", "rechnungNummer"))
            assertEquals(JSONObject(expected.extractedFieldsJson).getString(key), JSONObject(actual.extractedFieldsJson).optString(key))
    }
}
