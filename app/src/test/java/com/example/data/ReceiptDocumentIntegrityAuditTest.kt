package com.example.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReceiptDocumentIntegrityAuditTest : Phase10CRestoreFixture() {
    @Test fun missingUnitIsReportedWithoutRepairingTheStoredDocument() = runTest {
        seedProperty()
        repository.insert(receipt(1).copy(unitId="unit-1"))
        val document = repository.getAllManagedDocuments().single().copy(unitId=null)
        repository.upsertManagedDocument(document)
        val before = canonicalJson(SupplementalDriveBackup.createPayload(context,db))
        val result = ReceiptDocumentSyncState.scan(repository)
        assertEquals(listOf(ReceiptDocumentSyncState.Issue(document.documentId,document.receiptInternalId!!,"MISSING_DOCUMENT_UNIT")), result)
        assertEquals(document,repository.getManagedDocument(document.documentId))
        assertEquals(before,canonicalJson(SupplementalDriveBackup.createPayload(context,db)))
    }

    @Test fun conflictingUnitIsReportedWithoutChoosingAnotherUnit() = runTest {
        seedProperty()
        repository.insert(receipt(1).copy(unitId="unit-1"))
        val document = repository.getAllManagedDocuments().single().copy(unitId="different-unit")
        repository.upsertManagedDocument(document)
        assertEquals("UNIT_MISMATCH",ReceiptDocumentSyncState.scan(repository).single().reason)
        assertEquals(document,repository.getManagedDocument(document.documentId))
    }
}
