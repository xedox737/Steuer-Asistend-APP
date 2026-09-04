package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentStorageMigrationPlannerTest {
    @Test fun `preview distinguishes move rename unchanged and review without changing ids`() {
        val preview = DocumentStorageMigrationPlanner.preview(listOf(
            candidate("a", "old", "a-old.pdf", "new", "a-new.pdf"),
            candidate("b", "new", "b.pdf", "new", "b.pdf"),
            candidate("c", "old", "c.pdf", "new", "c.pdf"),
            candidate("d", "new", "d-old.pdf", "new", "d-new.pdf"),
            candidate("e", "old", "x.pdf", "new", "x.pdf", ambiguous = true)
        ))
        assertEquals(5, preview.found); assertEquals(1, preview.unchanged)
        assertEquals(1, preview.moveOnly); assertEquals(1, preview.renameOnly)
        assertEquals(1, preview.moveAndRename); assertEquals(1, preview.review)
        assertEquals("file-a", preview.items.first().candidate.driveFileId)
    }

    @Test fun `same input is idempotent and hashes must match`() {
        val items = listOf(candidate("a", "new", "new.pdf", "new", "new.pdf"))
        assertEquals(DocumentStorageMigrationPlanner.preview(items), DocumentStorageMigrationPlanner.preview(items))
        assertTrue(DocumentMigrationVerifier.isContentPreserved("abc", "ABC"))
    }

    @Test fun `duplicate drive ids and ambiguous entries are never scheduled destructively`() {
        val duplicate = candidate("a", "old", "a.pdf", "new", "a.pdf")
        val preview = DocumentStorageMigrationPlanner.preview(listOf(duplicate, duplicate.copy(receiptInternalId = "other"), candidate("x", "old", "x.pdf", "new", "x.pdf", ambiguous = true)))
        assertEquals(2, preview.found)
        assertEquals(DocumentMigrationAction.PRUEFEN, preview.items.last().action)
    }

    @Test fun `target filename conflict requires review`() {
        val preview = DocumentStorageMigrationPlanner.preview(listOf(
            candidate("a", "old-a", "a.pdf", "new", "same.pdf"),
            candidate("b", "old-b", "b.pdf", "new", "same.pdf")
        ))
        assertEquals(1, preview.conflicts)
        assertTrue(preview.items.all { it.action == DocumentMigrationAction.PRUEFEN })
    }

    @Test fun `journal preserves original reference for resume`() {
        val item = DocumentStorageMigrationPlanner.preview(listOf(candidate("a", "old", "old.pdf", "new", "new.pdf"))).items.single()
        val journal = DocumentStorageMigrationPlanner.journal(item)
        assertEquals("file-a", journal.driveFileId)
        assertEquals("old", journal.originalFolderId)
        assertEquals("PLANNED", journal.state)
        val resumed = DocumentStorageMigrationPlanner.resume(listOf(journal, journal.copy(documentId = "done", state = "COMPLETED")))
        assertEquals(1, resumed.found)
        assertEquals("file-a", resumed.items.single().candidate.driveFileId)
    }

    private fun candidate(id: String, currentFolder: String, currentName: String, targetFolder: String, targetName: String, ambiguous: Boolean = false) =
        DocumentMigrationCandidate(id, "file-$id", currentFolder, currentName, targetFolder, targetName, "hash-$id", ambiguous)
}
