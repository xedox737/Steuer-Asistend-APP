package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateCleanupRoomTargetTest {
    @Test
    fun mergeWithSharedInternalIdTargetsOnlyDuplicateRoomRow() {
        val canonical = record(roomId = 1, internalId = "shared", metadataFileId = "meta-canonical")
        val duplicate = record(roomId = 2, internalId = "shared", metadataFileId = "meta-duplicate")
        val preview = DuplicateGroupPreview(
            mainDriveFileId = "main",
            canonical = canonical,
            duplicatesToRemove = listOf(duplicate),
            metadataPlan = DuplicateMetadataPlan(
                retainedMetadataFileIds = setOf("meta-canonical"),
                orphanMetadataFileIds = setOf("meta-duplicate")
            )
        )

        val journal = DuplicateCleanupPlanner.planMerge(
            preview = preview,
            now = "2026-08-01T00:00:00Z",
            operationId = "op"
        )

        assertEquals(listOf(2), journal.targetRoomIds)
        assertTrue(journal.targetInternalIds.isEmpty())
        assertEquals("shared", journal.canonicalInternalId)
        assertFalse(journal.removeWholeGroup)
    }

    private fun record(
        roomId: Int,
        internalId: String,
        metadataFileId: String
    ) = DuplicateReceiptRecord(
        roomId = roomId,
        internalId = internalId,
        displayId = "BLG-$roomId",
        mainDriveFileId = "main",
        metadataFileId = metadataFileId,
        deletionStatus = "DELETED",
        deletedAt = "2026-08-01T00:00:00Z",
        createdAt = "2025-01-01T00:00:00Z",
        indexReferenced = roomId == 1,
        indexMetadataFileId = if (roomId == 1) metadataFileId else null,
        tombstone = null,
        completenessScore = 10
    )
}
