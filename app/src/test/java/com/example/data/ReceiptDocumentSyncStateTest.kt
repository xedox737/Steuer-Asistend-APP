package com.example.data

import org.junit.Assert.*
import org.junit.Test

class ReceiptDocumentSyncStateTest {
    private val reviewed = ManagedDocument(documentId = "receipt:r1", propertyId = "p1", receiptInternalId = "r1",
        unitId = "u1", documentType = "MIETVERTRAG", tenantReference = "tenant1", loanId = 71,
        renovationReference = "renovation1", reviewStatus = "GEPRUEFT", title = "Confirmed title",
        documentDate = "2026-09-30", updatedAt = "2026-09-30T12:00:00Z", ocrText = "Confirmed OCR",
        extractedFieldsJson = "{\"review\":true}", aiConfidence = 0.91)
    private val upload = ManagedDocument(documentId = "receipt:r1", propertyId = "p1", receiptInternalId = "r1",
        driveFileId = "uploaded1", driveFolderId = "folder1", storedFilename = "uploaded.pdf", sha256 = "verified-hash",
        fileSizeBytes = 123, mimeType = "application/pdf", localUri = "/synthetic/original.pdf", updatedAt = "2026-10-25T12:00:00Z")

    @Test fun verifiedTransferCannotEraseReviewedDomainStateOrInventANewerAuthoredVersion() {
        val result = ReceiptDocumentSyncState.merge(reviewed, upload)
        assertEquals(reviewed, result.copy(storedFilename = reviewed.storedFilename, mimeType = reviewed.mimeType,
            localUri = reviewed.localUri, driveFileId = reviewed.driveFileId, driveFolderId = reviewed.driveFolderId,
            sha256 = reviewed.sha256, fileSizeBytes = reviewed.fileSizeBytes, legacyDriveFolderId = reviewed.legacyDriveFolderId))
        assertEquals(upload.driveFileId, result.driveFileId)
        assertEquals(upload.sha256, result.sha256)
        assertEquals(upload.fileSizeBytes, result.fileSizeBytes)
        assertEquals(reviewed, ReceiptDocumentSyncState.merge(reviewed, upload).copy(
            storedFilename = reviewed.storedFilename, mimeType = reviewed.mimeType, localUri = reviewed.localUri,
            driveFileId = reviewed.driveFileId, driveFolderId = reviewed.driveFolderId, sha256 = reviewed.sha256,
            fileSizeBytes = reviewed.fileSizeBytes, legacyDriveFolderId = reviewed.legacyDriveFolderId))
    }

    @Test fun repeatedTransferIsIdempotent() {
        val first = ReceiptDocumentSyncState.merge(reviewed, upload)
        assertEquals(first, ReceiptDocumentSyncState.merge(first, upload))
    }

    @Test fun existingMissingUnitIsNeverAutomaticallyReassigned() {
        val missing = reviewed.copy(unitId = null)
        val receipt = Receipt(internalId = "r1", propertyId = "p1", unitId = "u1", aussteller = "Synthetic", datum = "2026-10-25",
            uhrzeit = "12:00", bruttobetrag = 1.0, hauptkategorie = "Test", unterkategorie = "Test", kontoNr = "4800", beschreibung = "Synthetic")
        val result = ReceiptDocumentSyncState.merge(missing, upload.copy(unitId = "u1"))
        assertNull(result.unitId)
        assertEquals("MISSING_DOCUMENT_UNIT", ReceiptDocumentSyncState.associationProblem(result, receipt))
        assertEquals(missing, missing.copy())
    }

    @Test fun conflictingPropertyBlocksTransfer() {
        assertThrows(IllegalArgumentException::class.java) { ReceiptDocumentSyncState.merge(reviewed, upload.copy(propertyId = "p2")) }
    }

    @Test fun conflictingReceiptIdentityBlocksTransfer() {
        assertThrows(IllegalArgumentException::class.java) { ReceiptDocumentSyncState.merge(reviewed, upload.copy(receiptInternalId = "r2")) }
    }

    @Test fun genuinelyNewDocumentUsesValidatedInitialState() {
        assertEquals(upload, ReceiptDocumentSyncState.merge(null, upload))
    }
}
