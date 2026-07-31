package com.example.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateCleanupExecutorTest {
    @Test
    fun mergeNeverDeletesSharedMainFileAndRunsMetadataBeforeLocalPurge() = runBlocking {
        val store = FakeStore(journal(removeWholeGroup = false))
        val gateway = FakeGateway()
        val result = DuplicateCleanupExecutor(store, gateway) { "2026-07-31T12:00:00Z" }.execute("op")

        assertTrue(result.completed)
        assertFalse(gateway.calls.any { it.startsWith("main:") })
        assertEquals(
            listOf("refs", "meta:m1", "meta:m2", "local"),
            gateway.calls
        )
    }

    @Test
    fun wholeGroupDeletesMainExactlyOnceAndLastBeforeLocalPurge() = runBlocking {
        val store = FakeStore(journal(removeWholeGroup = true))
        val gateway = FakeGateway()
        val executor = DuplicateCleanupExecutor(store, gateway) { "2026-07-31T12:00:00Z" }

        val first = executor.execute("op")
        val second = executor.execute("op")

        assertTrue(first.completed)
        assertTrue(second.completed)
        assertEquals(1, gateway.calls.count { it == "main:main" })
        assertEquals(
            listOf("refs", "meta:m1", "meta:m2", "main:main", "local"),
            gateway.calls
        )
    }

    @Test
    fun metadataFailureStopsBeforeMainAndLocalDeletion() = runBlocking {
        val store = FakeStore(journal(removeWholeGroup = true))
        val gateway = FakeGateway(failMetadataId = "m1")
        val result = DuplicateCleanupExecutor(store, gateway) { "now" }.execute("op")

        assertFalse(result.completed)
        assertEquals(DuplicateCleanupPhase.REFERENCES_REMOVED, result.phase)
        assertFalse(gateway.calls.any { it.startsWith("main:") })
        assertFalse(gateway.calls.contains("local"))
    }

    @Test
    fun retrySkipsMetadataFileThatWasAlreadyDeletedBeforeLaterFailure() = runBlocking {
        val store = FakeStore(journal(removeWholeGroup = true))
        val firstGateway = FakeGateway(failMetadataId = "m2")
        val firstResult = DuplicateCleanupExecutor(store, firstGateway) { "first" }.execute("op")

        assertFalse(firstResult.completed)
        assertEquals(setOf("m1"), store.current.removedMetadataFileIds)
        assertEquals(1, firstGateway.calls.count { it == "meta:m1" })

        val retryGateway = FakeGateway()
        val retryResult = DuplicateCleanupExecutor(store, retryGateway) { "retry" }.execute("op")

        assertTrue(retryResult.completed)
        assertFalse(retryGateway.calls.contains("meta:m1"))
        assertEquals(1, retryGateway.calls.count { it == "meta:m2" })
        assertEquals(setOf("m1", "m2"), store.current.removedMetadataFileIds)
    }

    @Test
    fun alreadyMissingDriveResourcesAreTreatedAsCompleted() = runBlocking {
        val store = FakeStore(journal(removeWholeGroup = true))
        val gateway = FakeGateway(missingMetadataId = "m1", mainAlreadyMissing = true)
        val result = DuplicateCleanupExecutor(store, gateway) { "now" }.execute("op")

        assertTrue(result.completed)
        assertEquals(DuplicateCleanupPhase.COMPLETED, store.current.phase)
        assertEquals(setOf("m1", "m2"), store.current.removedMetadataFileIds)
        assertTrue(gateway.calls.contains("local"))
    }

    @Test
    fun unconfirmedJournalPerformsNoMutation() = runBlocking {
        val store = FakeStore(journal(removeWholeGroup = true).copy(phase = DuplicateCleanupPhase.PREVIEWED))
        val gateway = FakeGateway()
        val result = DuplicateCleanupExecutor(store, gateway) { "now" }.execute("op")

        assertFalse(result.completed)
        assertTrue(gateway.calls.isEmpty())
    }

    private fun journal(removeWholeGroup: Boolean) = DuplicateCleanupJournal(
        operationId = "op",
        mainDriveFileId = "main",
        canonicalInternalId = if (removeWholeGroup) null else "canonical",
        targetInternalIds = listOf("duplicate"),
        metadataFileIds = listOf("m1", "m1", "m2"),
        tombstoneInternalIds = listOf("duplicate"),
        removeWholeGroup = removeWholeGroup,
        phase = DuplicateCleanupPhase.CONFIRMED,
        createdAt = "2026-07-31T11:00:00Z",
        updatedAt = "2026-07-31T11:00:00Z"
    )

    private class FakeStore(initial: DuplicateCleanupJournal) : DuplicateCleanupJournalStore {
        var current = initial
        override suspend fun load(operationId: String): DuplicateCleanupJournal = current
        override suspend fun save(journal: DuplicateCleanupJournal) {
            current = journal
        }
    }

    private class FakeGateway(
        private val failMetadataId: String? = null,
        private val missingMetadataId: String? = null,
        private val mainAlreadyMissing: Boolean = false
    ) : DuplicateCleanupGateway {
        val calls = mutableListOf<String>()

        override suspend fun removeNonCanonicalReferences(journal: DuplicateCleanupJournal) {
            calls += "refs"
        }

        override suspend fun deleteMetadataFile(fileId: String) {
            calls += "meta:$fileId"
            if (fileId == failMetadataId) error("metadata failed")
            if (fileId == missingMetadataId) throw DriveResourceAlreadyMissingException(fileId)
        }

        override suspend fun deleteMainFile(fileId: String) {
            calls += "main:$fileId"
            if (mainAlreadyMissing) throw DriveResourceAlreadyMissingException(fileId)
        }

        override suspend fun purgeLocalRecords(journal: DuplicateCleanupJournal) {
            calls += "local"
        }
    }
}
