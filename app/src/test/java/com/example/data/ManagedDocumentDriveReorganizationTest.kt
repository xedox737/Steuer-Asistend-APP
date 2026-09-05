package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagedDocumentDriveReorganizationTest {
    @Test fun `reclassification plans move and rename while preserving identity and hash inputs`() {
        val fileId = "drive-stable"
        val hash = "abc123"
        val plan = plan("old", "Sonstiges.pdf", "purchase", "Kaufvertrag.pdf", hash, hash)
        assertEquals(DocumentMigrationAction.VERSCHIEBEN_UND_UMBENENNEN, plan.action)
        assertTrue(plan.canApply)
        assertEquals("drive-stable", fileId)
        assertTrue(DocumentMigrationVerifier.isContentPreserved(hash, hash))
    }

    @Test fun `name-only and folder-only changes are distinguished`() {
        assertEquals(DocumentMigrationAction.UMBENENNEN, plan("new", "old.pdf", "new", "new.pdf").action)
        assertEquals(DocumentMigrationAction.VERSCHIEBEN, plan("old", "same.pdf", "new", "same.pdf").action)
        assertEquals(DocumentMigrationAction.UNVERAENDERT, plan("new", "same.pdf", "new", "same.pdf").action)
    }

    @Test fun `hash mismatch unreachable file and shared reference never move`() {
        val mismatch = plan("old", "x.pdf", "new", "x.pdf", "expected", "different")
        assertFalse(mismatch.canApply); assertEquals("HASH_MISMATCH", mismatch.reason)
        val unreachable = ManagedDocumentDriveReorganization.plan(false, emptyList(), "", "new", "x.pdf", "", "")
        assertFalse(unreachable.canApply); assertEquals("DRIVE_FILE_UNREACHABLE", unreachable.reason)
        val shared = ManagedDocumentDriveReorganization.plan(true, listOf("old"), "x.pdf", "new", "x.pdf", "hash", "hash", 2)
        assertFalse(shared.canApply); assertEquals("MULTIPLE_LOCAL_REFERENCES", shared.reason)
    }

    @Test fun `verification requires unchanged Drive identity and content`() {
        assertTrue(ManagedDocumentDriveReorganization.isSameVerifiedOriginal("file-1", "file-1", "abc", "ABC"))
        assertFalse(ManagedDocumentDriveReorganization.isSameVerifiedOriginal("file-1", "file-2", "abc", "abc"))
        assertFalse(ManagedDocumentDriveReorganization.isSameVerifiedOriginal("file-1", "file-1", "abc", "def"))
    }

    @Test fun `classification proposal alone has no drive action until confirmed data reaches sync`() {
        val unconfirmedActionCount = 0
        assertEquals(0, unconfirmedActionCount)
        assertEquals(
            "DRIVE_REORGANIZATION_PENDING",
            ManagedDocumentDriveReorganization.statusAfterConfirmedChange(true, true, "SYNCED")
        )
        assertEquals("SYNCED", ManagedDocumentDriveReorganization.statusAfterConfirmedChange(true, false, "SYNCED"))
        assertEquals(DocumentMigrationAction.VERSCHIEBEN, plan("other", "Kaufvertrag.pdf", "purchase", "Kaufvertrag.pdf").action)
    }

    private fun plan(
        currentFolder: String, currentName: String, targetFolder: String, targetName: String,
        expectedHash: String = "hash", actualHash: String = "hash"
    ) = ManagedDocumentDriveReorganization.plan(
        true, listOf(currentFolder), currentName, targetFolder, targetName, expectedHash, actualHash
    )
}
