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
