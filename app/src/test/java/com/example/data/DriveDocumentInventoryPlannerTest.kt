package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveDocumentInventoryPlannerTest {
    @Test fun `reconciles local receipt receipt index and managed document references`() {
        val references = listOf(
            ref("r-file", "r-1", "", DriveDocumentInventorySource.LOCAL_RECEIPT, "receipt.pdf"),
            ref("r-file", "r-1", "", DriveDocumentInventorySource.RECEIPT_INDEX, "receipt.pdf"),
            ref("d-file", "", "d-1", DriveDocumentInventorySource.MANAGED_DOCUMENT, "contract.pdf"),
            ref("d-file", "", "d-1", DriveDocumentInventorySource.DOCUMENT_INDEX, "contract.pdf")
        )
        val result = DriveDocumentInventoryPlanner.build(
            listOf(file("r-file", "receipt.pdf"), file("d-file", "contract.pdf")), references
        )
        assertEquals(2, result.size)
        assertTrue(result.single { it.driveFileId == "r-file" }.referencedByReceiptIndex)
        assertTrue(result.single { it.driveFileId == "d-file" }.referencedByManagedDocument)
        assertTrue(result.all { it.status == DriveDocumentInventoryStatus.OK })
    }

    @Test fun `drive-only app file is reported and foreign file is ignored without mutation`() {
        val orphan = file("orphan", "legacy.pdf", receiptId = "receipt-orphan")
        val foreign = file("foreign", "private.pdf", appRelevant = false)
        val files = listOf(orphan, foreign)
        val result = DriveDocumentInventoryPlanner.build(files, emptyList())
        assertEquals(1, result.size)
        assertEquals(DriveDocumentInventoryStatus.MISSING_LOCAL_REFERENCE, result.single().status)
        assertEquals(listOf(orphan, foreign), files)
    }

    @Test fun `unidentified legacy file is orphan and inventory never schedules an action`() {
        val result = DriveDocumentInventoryPlanner.build(
            listOf(file("legacy", "scan.pdf", appRelevant = false, legacy = true)), emptyList()
        )
        assertEquals(DriveDocumentInventoryStatus.ORPHAN, result.single().status)
        assertFalse(result.single().detectedSources.isEmpty())
    }

    @Test fun `same drive id or same identity on different files is a conflict`() {
        val sameFile = DriveDocumentInventoryPlanner.build(
            listOf(file("shared", "x.pdf")),
            listOf(ref("shared", "r-1", "", DriveDocumentInventorySource.LOCAL_RECEIPT), ref("shared", "r-2", "", DriveDocumentInventorySource.RECEIPT_INDEX))
        )
        assertEquals(DriveDocumentInventoryStatus.MULTIPLE_REFERENCES, sameFile.single().status)
        val sameReceipt = DriveDocumentInventoryPlanner.build(
            listOf(file("a", "a.pdf"), file("b", "b.pdf")),
            listOf(ref("a", "r", "", DriveDocumentInventorySource.LOCAL_RECEIPT), ref("b", "r", "", DriveDocumentInventorySource.RECEIPT_INDEX))
        )
        assertTrue(sameReceipt.all { it.status == DriveDocumentInventoryStatus.MULTIPLE_REFERENCES })
    }

    @Test fun `conflicting Drive app identity and missing document index are review states`() {
        val identityConflict = DriveDocumentInventoryPlanner.build(
            listOf(file("shared", "x.pdf", receiptId = "r-drive")),
            listOf(ref("shared", "r-local", "", DriveDocumentInventorySource.LOCAL_RECEIPT))
        )
        assertEquals(DriveDocumentInventoryStatus.MULTIPLE_REFERENCES, identityConflict.single().status)

        val missingDocumentIndex = DriveDocumentInventoryPlanner.build(
            listOf(file("document", "contract.pdf")),
            listOf(ref("document", "", "doc-1", DriveDocumentInventorySource.MANAGED_DOCUMENT, "contract.pdf"))
        )
        assertEquals(DriveDocumentInventoryStatus.MISSING_INDEX_REFERENCE, missingDocumentIndex.single().status)
    }

    @Test fun `identical hash is duplicate but equal filename with different hash is not`() {
        val duplicateRefs = listOf(
            ref("a", "", "a", DriveDocumentInventorySource.MANAGED_DOCUMENT, "same.pdf"),
            ref("b", "", "b", DriveDocumentInventorySource.MANAGED_DOCUMENT, "same.pdf")
        )
        val duplicates = DriveDocumentInventoryPlanner.build(
            listOf(file("a", "same.pdf", hash = "hash"), file("b", "same.pdf", hash = "hash")), duplicateRefs
        )
        assertTrue(duplicates.all { it.status == DriveDocumentInventoryStatus.POSSIBLE_DUPLICATE })
        val different = DriveDocumentInventoryPlanner.build(
            listOf(file("a", "same.pdf", hash = "one"), file("b", "same.pdf", hash = "two")), duplicateRefs
        )
        assertTrue(different.none { it.status == DriveDocumentInventoryStatus.POSSIBLE_DUPLICATE })
    }

    @Test fun `missing drive file and missing index are review states`() {
        val missingDrive = DriveDocumentInventoryPlanner.build(
            emptyList(), listOf(ref("missing", "r", "", DriveDocumentInventorySource.RECEIPT_INDEX))
        )
        assertEquals(DriveDocumentInventoryStatus.MIGRATION_PRUEFEN, missingDrive.single().status)
        val missingIndex = DriveDocumentInventoryPlanner.build(
            listOf(file("present", "receipt.pdf")),
            listOf(ref("present", "r", "", DriveDocumentInventorySource.LOCAL_RECEIPT, "receipt.pdf"))
        )
        assertEquals(DriveDocumentInventoryStatus.MISSING_INDEX_REFERENCE, missingIndex.single().status)
    }

    private fun ref(fileId: String, receiptId: String, documentId: String, source: DriveDocumentInventorySource, filename: String = "") =
        DriveInventoryReference(fileId, receiptId, documentId, source, "target", filename)

    private fun file(id: String, name: String, hash: String = "", receiptId: String = "", appRelevant: Boolean = true, legacy: Boolean = false) =
        DriveInventoryFile(id, name, parentIds = listOf("parent"), sha256 = hash, receiptInternalId = receiptId, appRelevant = appRelevant, legacyFolder = legacy)
}
