package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateCleanupPlannerTest {
    @Test
    fun mergePlanNeverSchedulesSharedMainFileAndOnlyUsesOrphanMetadata() {
        val preview = preview()
        val journal = DuplicateCleanupPlanner.planMerge(preview, "now", "op")

        assertFalse(journal.removeWholeGroup)
        assertEquals("canonical", journal.canonicalInternalId)
        assertEquals(listOf("duplicate"), journal.targetInternalIds)
        assertEquals(listOf("orphan-meta"), journal.metadataFileIds)
        assertEquals(DuplicateCleanupPhase.PREVIEWED, journal.phase)
    }

    @Test
    fun wholeGroupPlanIncludesCanonicalAndDuplicateExactlyOnce() {
        val journal = DuplicateCleanupPlanner.planWholeGroupDeletion(preview(), "now", "op")

        assertTrue(journal.removeWholeGroup)
        assertEquals(null, journal.canonicalInternalId)
        assertEquals(setOf("canonical", "duplicate"), journal.targetInternalIds.toSet())
        assertEquals(setOf("canonical-meta", "duplicate-meta"), journal.metadataFileIds.toSet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun wholeGroupCannotBeConfirmedWithOnlyOneConfirmation() {
        val journal = DuplicateCleanupPlanner.planWholeGroupDeletion(preview(), "now", "op")
        DuplicateCleanupPlanner.confirmWholeGroupDeletion(
            journal,
            firstConfirmation = true,
            secondConfirmation = false
        )
    }

    @Test
    fun wholeGroupRequiresAndAcceptsTwoConfirmations() {
        val journal = DuplicateCleanupPlanner.planWholeGroupDeletion(preview(), "now", "op")
        val confirmed = DuplicateCleanupPlanner.confirmWholeGroupDeletion(
            journal,
            firstConfirmation = true,
            secondConfirmation = true
        )

        assertEquals(DuplicateCleanupPhase.CONFIRMED, confirmed.phase)
    }

    private fun preview() = DuplicateGroupPreview(
        mainDriveFileId = "main",
        canonical = record("canonical", "canonical-meta", tombstone = null),
        duplicatesToRemove = listOf(record("duplicate", "duplicate-meta", tombstone = null)),
        metadataPlan = DuplicateMetadataPlan(
            retainedMetadataFileIds = setOf("canonical-meta"),
            orphanMetadataFileIds = setOf("orphan-meta")
        ),
        mainFileWillBeDeleted = false
    )

    private fun record(
        internalId: String,
        metadataId: String,
        tombstone: ReceiptTombstone?
    ) = DuplicateReceiptRecord(
        roomId = if (internalId == "canonical") 1 else 2,
        internalId = internalId,
        displayId = internalId,
        mainDriveFileId = "main",
        metadataFileId = metadataId,
        deletionStatus = "DELETED",
        deletedAt = null,
        createdAt = null,
        indexReferenced = internalId == "canonical",
        indexMetadataFileId = if (internalId == "canonical") metadataId else null,
        tombstone = tombstone,
        completenessScore = 1
    )
}
