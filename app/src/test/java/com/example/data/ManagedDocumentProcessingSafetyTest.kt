package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ManagedDocumentProcessingSafetyTest {
    private lateinit var database: AppDatabase
    private val document = ManagedDocument("doc", "property-b", "unit-b", title = "Original", localUri = "/original.pdf")
    private val pendingJson = """{"documentType":"MIETVERTRAG","confidence":0.9,"fields":[]}"""

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
    }
    @After fun tearDown() { database.close() }

    @Test fun deletedDocumentIsNotResurrectedByOcrAnalysisOrReviewCompletion() = runBlocking {
        val dao = database.managedDocumentDao()
        dao.upsert(document)
        dao.deleteById(document.documentId)
        assertNull(dao.updateProcessingIfPresent(document, ocrStatus = "ERFOLGREICH", ocrText = "Text"))
        assertNull(dao.updateProcessingIfPresent(document, aiStatus = "ERFOLGREICH", fieldsJson = pendingJson))
        assertNull(dao.updateReviewIfPresent(document, document.copy(reviewStatus = "GEPRUEFT")))
        assertNull(dao.updateLoanIfPresent(document, 7))
        assertNull(dao.getById(document.documentId))
        assertTrue(dao.getAll().isEmpty())
    }

    @Test fun reassignedDocumentRejectsAnalysisAndReviewFromPreviousContext() = runBlocking {
        val dao = database.managedDocumentDao()
        val moved = document.copy(propertyId = "property-a", unitId = "unit-a", title = "Neu zugeordnet")
        dao.upsert(moved)
        assertNull(dao.updateProcessingIfPresent(document, aiStatus = "ERFOLGREICH", fieldsJson = pendingJson))
        assertNull(dao.updateReviewIfPresent(document, document.copy(reviewStatus = "GEPRUEFT")))
        assertNull(dao.updateLoanIfPresent(document, 7))
        assertEquals(moved, dao.getById(document.documentId))
    }

    @Test fun failedRetryPreservesPreviousPendingDataAndConcurrentPresentationAndDriveChanges() = runBlocking {
        val dao = database.managedDocumentDao()
        val current = document.copy(title = "Bearbeiteter Titel", driveFileId = "drive-stable", storedFilename = "new.pdf",
            aiConfidence = .9, extractedFieldsJson = pendingJson, reviewStatus = "PRUEFEN")
        dao.upsert(current)
        val failed = dao.updateProcessingIfPresent(document, aiStatus = "FEHLGESCHLAGEN")!!
        assertEquals(current.title, failed.title)
        assertEquals(current.driveFileId, failed.driveFileId)
        assertEquals(current.storedFilename, failed.storedFilename)
        assertEquals(current.extractedFieldsJson, failed.extractedFieldsJson)
        assertEquals(.9, failed.aiConfidence, .001)
        assertEquals("PRUEFEN", failed.reviewStatus)
        assertEquals("FEHLGESCHLAGEN", failed.aiAnalysisStatus)
    }

    @Test fun successfulAnalysisKeepsLocalIdentityAndDescriptionAndUpdatesSearchIndex() = runBlocking {
        val dao = database.managedDocumentDao()
        dao.upsert(document.copy(extractedFieldsJson = """{"_displayDescription":"Meine Beschreibung"}"""))
        val updated = dao.updateProcessingIfPresent(document, ocrStatus = "ERFOLGREICH", ocrText = "Mietvertrag Weber",
            aiStatus = "ERFOLGREICH", confidence = .9, fieldsJson = pendingJson)!!
        assertEquals(document.documentId, updated.documentId)
        assertEquals(document.propertyId, updated.propertyId)
        assertEquals(document.unitId, updated.unitId)
        assertEquals(document.localUri, updated.localUri)
        assertTrue(updated.extractedFieldsJson.contains("Meine Beschreibung"))
        assertEquals(listOf(updated), dao.search("weber", "property-b", "unit-b", "", "", ""))
    }

    @Test fun confirmingReviewPreservesConcurrentDriveAndDescriptionChanges() = runBlocking {
        val dao = database.managedDocumentDao()
        dao.upsert(document.copy(driveFileId = "drive-stable", driveFolderId = "folder-stable", migrationStatus = "SYNCED",
            extractedFieldsJson = """{"_displayDescription":"Aktuelle Beschreibung"}"""))
        val reviewed = document.copy(documentType = "MIETVERTRAG", storedFilename = "Mietvertrag.pdf", reviewStatus = "GEPRUEFT", extractedFieldsJson = "{}")
        val saved = dao.updateReviewIfPresent(document, reviewed)!!
        assertEquals("drive-stable", saved.driveFileId)
        assertEquals("folder-stable", saved.driveFolderId)
        assertEquals("DRIVE_REORGANIZATION_PENDING", saved.migrationStatus)
        assertTrue(saved.extractedFieldsJson.contains("Aktuelle Beschreibung"))
        assertEquals("GEPRUEFT", saved.reviewStatus)
        val linked = dao.updateLoanIfPresent(saved, 7)!!
        assertEquals(saved.copy(loanId = 7), linked)
    }

    @Test fun confirmedReviewIsEmittedToExistingDocumentObserver() = runBlocking {
        val dao = database.managedDocumentDao()
        dao.upsert(document)
        val initial = CompletableDeferred<Unit>()
        val confirmed = CompletableDeferred<ManagedDocument>()
        val observer = launch {
            dao.observeAll().collect { documents ->
                documents.firstOrNull { it.documentId == document.documentId }?.let {
                    initial.complete(Unit)
                    if (it.reviewStatus == "GEPRUEFT") confirmed.complete(it)
                }
            }
        }
        try {
            withTimeout(5000) { initial.await() }
            val saved = dao.updateReviewIfPresent(document, document.copy(reviewStatus = "GEPRUEFT"))!!
            assertEquals(saved, dao.getById(document.documentId))
            assertEquals(saved, withTimeout(5000) { confirmed.await() })
        } finally { observer.cancel() }
    }
}
