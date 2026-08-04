package com.example.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveDuplicateCleanupActionsTest {
    @Test
    fun mergeRemovesOnlyCapturedDuplicateReferences() = runBlocking {
        val refs = FakeReferenceMutator()
        val deleted = mutableListOf<String>()
        val actions = GoogleDriveDuplicateCleanupActions(
            accessTokenProvider = { "token" },
            referenceMutator = refs,
            deleteFile = { _, fileId -> deleted += fileId; true }
        )

        actions.removeCapturedReferences(journal(removeWholeGroup = false))

        assertEquals(setOf("duplicate"), refs.indexInternalIds)
        assertEquals(setOf("duplicate"), refs.tombstoneInternalIds)
        assertFalse("canonical" in refs.indexInternalIds)
        assertTrue(deleted.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun mergeRejectsCanonicalReferenceAsTarget() = runBlocking {
        val actions = GoogleDriveDuplicateCleanupActions(
            accessTokenProvider = { "token" },
            referenceMutator = FakeReferenceMutator(),
            deleteFile = { _, _ -> true }
        )
        actions.removeCapturedReferences(
            journal(removeWholeGroup = false).copy(targetInternalIds = listOf("canonical"))
        )
    }

    @Test
    fun failedDriveDeleteIsSurfacedAndCannotLookSuccessful() = runBlocking {
        val actions = GoogleDriveDuplicateCleanupActions(
            accessTokenProvider = { "token" },
            referenceMutator = FakeReferenceMutator(),
            deleteFile = { _, _ -> false }
        )

        val error = runCatching { actions.deleteMetadataFile("meta") }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
    }

    @Test
    fun confirmedMainDeleteUsesExactCapturedFileIdOnce() = runBlocking {
        val calls = mutableListOf<Pair<String, String>>()
        val actions = GoogleDriveDuplicateCleanupActions(
            accessTokenProvider = { "token" },
            referenceMutator = FakeReferenceMutator(),
            deleteFile = { token, fileId -> calls += token to fileId; true }
        )

        actions.deleteMainFile("main")

        assertEquals(listOf("token" to "main"), calls)
    }

    private fun journal(removeWholeGroup: Boolean) = DuplicateCleanupJournal(
        operationId = "op",
        mainDriveFileId = "main",
        canonicalInternalId = if (removeWholeGroup) null else "canonical",
        targetInternalIds = listOf("duplicate"),
        metadataFileIds = listOf("meta"),
        tombstoneInternalIds = listOf("duplicate"),
        removeWholeGroup = removeWholeGroup,
        phase = DuplicateCleanupPhase.CONFIRMED,
        createdAt = "now",
        updatedAt = "now"
    )

    private class FakeReferenceMutator : DuplicateReferenceMutator {
        var indexInternalIds: Set<String> = emptySet()
        var tombstoneInternalIds: Set<String> = emptySet()

        override suspend fun removeIndexReferences(internalIds: Set<String>, mainDriveFileId: String) {
            indexInternalIds = internalIds
        }

        override suspend fun removeTombstones(internalIds: Set<String>) {
            tombstoneInternalIds = internalIds
        }
    }
}
