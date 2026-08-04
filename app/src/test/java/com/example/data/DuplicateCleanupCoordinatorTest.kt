package com.example.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateCleanupCoordinatorTest {
    @Test
    fun mergePurgesOnlyCapturedDuplicateAndKeepsCanonical() = runBlocking {
        val canonical = receipt(1, "canonical", "main")
        val duplicate = receipt(2, "duplicate", "main", status = "DELETED")
        val unrelated = receipt(3, "other", "other-main")
        val store = FakeReceiptStore(mutableListOf(canonical, duplicate, unrelated))
        val remote = FakeRemoteActions()
        val gateway = RepositoryDuplicateCleanupGateway(store, remote)
        val journal = journal(
            removeWholeGroup = false,
            canonicalInternalId = "canonical",
            targets = listOf("duplicate")
        )

        gateway.purgeLocalRecords(journal)

        assertEquals(listOf(2), store.deletedRoomIds)
        assertTrue(store.rows.any { it.internalId == "canonical" })
        assertTrue(store.rows.any { it.internalId == "other" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun mergeRejectsCanonicalAsTarget() = runBlocking {
        val store = FakeReceiptStore(mutableListOf(receipt(1, "canonical", "main")))
        RepositoryDuplicateCleanupGateway(store, FakeRemoteActions()).purgeLocalRecords(
            journal(
                removeWholeGroup = false,
                canonicalInternalId = "canonical",
                targets = listOf("canonical")
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun wholeGroupRejectsUncapturedReceiptUsingSameMainFile() = runBlocking {
        val store = FakeReceiptStore(
            mutableListOf(
                receipt(1, "one", "main"),
                receipt(2, "two", "main"),
                receipt(3, "late-duplicate", "main")
            )
        )
        RepositoryDuplicateCleanupGateway(store, FakeRemoteActions()).purgeLocalRecords(
            journal(
                removeWholeGroup = true,
                canonicalInternalId = null,
                targets = listOf("one", "two")
            )
        )
    }


    @Test
    fun sameInternalIdDuplicatePurgesOnlyCapturedRoomRow() = runBlocking {
        val canonical = receipt(1, "shared", "main")
        val duplicate = receipt(2, "shared", "main", status = "DELETED")
        val store = FakeReceiptStore(mutableListOf(canonical, duplicate))
        val gateway = RepositoryDuplicateCleanupGateway(store, FakeRemoteActions())

        gateway.purgeLocalRecords(
            journal(
                removeWholeGroup = false,
                canonicalInternalId = "shared",
                targets = emptyList(),
                targetRoomIds = listOf(2)
            )
        )

        assertEquals(listOf(2), store.deletedRoomIds)
        assertEquals(listOf(1), store.rows.map { it.id })
    }

    @Test
    fun preflightBlocksRemoteMutationWhenMetadataBecameReferenced() = runBlocking {
        val canonical = receipt(1, "canonical", "main", metadataId = "meta-canonical")
        val duplicate = receipt(2, "duplicate", "main", status = "DELETED", metadataId = "meta-duplicate")
        val lateReference = receipt(3, "late", "other-main", metadataId = "meta-duplicate")
        val remote = FakeRemoteActions()
        val gateway = RepositoryDuplicateCleanupGateway(
            FakeReceiptStore(mutableListOf(canonical, duplicate, lateReference)),
            remote
        )
        val planned = journal(
            removeWholeGroup = false,
            canonicalInternalId = "canonical",
            targets = listOf("duplicate"),
            targetRoomIds = listOf(2)
        ).copy(metadataFileIds = listOf("meta-duplicate"))

        val failure = runCatching {
            gateway.removeNonCanonicalReferences(planned)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(remote.calls.isEmpty())
    }

    @Test
    fun coordinatorPersistsPreviewBeforeAnyMutation() = runBlocking {
        val canonical = receipt(1, "canonical", "main")
        val duplicate = receipt(2, "duplicate", "main", status = "DELETED")
        val receiptStore = FakeReceiptStore(mutableListOf(canonical, duplicate))
        val journalStore = FakeJournalStore()
        val remote = FakeRemoteActions()
        val gateway = RepositoryDuplicateCleanupGateway(receiptStore, remote)
        val executor = DuplicateCleanupExecutor(journalStore, gateway) { "now" }
        val coordinator = DuplicateCleanupCoordinator(receiptStore, journalStore, executor) { "now" }
        val preview = ReceiptDuplicateAnalyzer.analyze(
            listOf(canonical, duplicate),
            listOf(index("canonical", "main")),
            emptyMap()
        ).single()

        val planned = coordinator.createMergePreview(preview)

        assertEquals(DuplicateCleanupPhase.PREVIEWED, planned.phase)
        assertEquals(planned, journalStore.current)
        assertTrue(receiptStore.deletedRoomIds.isEmpty())
        assertTrue(remote.calls.isEmpty())
    }

    @Test
    fun mergeConfirmationExecutesWithoutMainDeletion() = runBlocking {
        val canonical = receipt(1, "canonical", "main")
        val duplicate = receipt(2, "duplicate", "main", status = "DELETED")
        val receiptStore = FakeReceiptStore(mutableListOf(canonical, duplicate))
        val preview = ReceiptDuplicateAnalyzer.analyze(
            listOf(canonical, duplicate),
            listOf(index("canonical", "main")),
            emptyMap()
        ).single()
        val planned = DuplicateCleanupPlanner.planMerge(preview, "now", "op")
        val journalStore = FakeJournalStore(planned)
        val remote = FakeRemoteActions()
        val gateway = RepositoryDuplicateCleanupGateway(receiptStore, remote)
        val executor = DuplicateCleanupExecutor(journalStore, gateway) { "now" }
        val coordinator = DuplicateCleanupCoordinator(receiptStore, journalStore, executor) { "now" }

        val result = coordinator.confirmAndExecuteMerge("op")

        assertTrue(result.completed)
        assertFalse(remote.calls.any { it.startsWith("main:") })
        assertEquals(listOf(2), receiptStore.deletedRoomIds)
    }

    private fun journal(
        removeWholeGroup: Boolean,
        canonicalInternalId: String?,
        targets: List<String>,
        targetRoomIds: List<Int> = emptyList()
    ) = DuplicateCleanupJournal(
        operationId = "op",
        mainDriveFileId = "main",
        canonicalInternalId = canonicalInternalId,
        targetInternalIds = targets,
        metadataFileIds = emptyList(),
        tombstoneInternalIds = targets,
        removeWholeGroup = removeWholeGroup,
        phase = DuplicateCleanupPhase.CONFIRMED,
        createdAt = "now",
        updatedAt = "now",
        targetRoomIds = targetRoomIds
    )

    private fun receipt(
        id: Int,
        internalId: String,
        mainId: String,
        status: String = "ACTIVE",
        metadataId: String? = null
    ) = Receipt(
        id = id,
        aussteller = "Test",
        datum = "2026-01-01",
        uhrzeit = "",
        bruttobetrag = 1.0,
        hauptkategorie = "Test",
        unterkategorie = "Test",
        kontoNr = "",
        beschreibung = "",
        internalId = internalId,
        displayId = "BLG-$internalId",
        driveFileId = mainId,
        driveMetadataFileId = metadataId,
        deletionStatus = status
    )

    private fun index(internalId: String, mainId: String) = ReceiptIndexEntry(
        internalId = internalId,
        displayId = "BLG-$internalId",
        metadataFileId = "meta-$internalId",
        mainDriveFileId = mainId,
        aussteller = "Test",
        rechnungsnummer = null,
        datum = "2026-01-01",
        bruttobetragCent = 100,
        hauptkategorie = "Test",
        unterkategorie = "Test",
        wohneinheit = null,
        massnahme = null,
        pruefstatus = null,
        freigabestatus = null,
        exportstatus = null,
        syncStatus = "SYNCED",
        updatedAt = "now"
    )

    private class FakeReceiptStore(
        val rows: MutableList<Receipt>
    ) : DuplicateReceiptStore {
        val deletedRoomIds = mutableListOf<Int>()

        override suspend fun getAllIncludingDeleted(): List<Receipt> = rows.toList()

        override suspend fun deleteByRoomId(roomId: Int) {
            deletedRoomIds += roomId
            rows.removeAll { it.id == roomId }
        }
    }

    private class FakeRemoteActions : DuplicateRemoteCleanupActions {
        val calls = mutableListOf<String>()
        override suspend fun removeCapturedReferences(journal: DuplicateCleanupJournal) {
            calls += "refs"
        }
        override suspend fun deleteMetadataFile(fileId: String) {
            calls += "meta:$fileId"
        }
        override suspend fun deleteMainFile(fileId: String) {
            calls += "main:$fileId"
        }
    }

    private class FakeJournalStore(
        initial: DuplicateCleanupJournal? = null
    ) : DuplicateCleanupJournalStore {
        var current: DuplicateCleanupJournal? = initial
        override suspend fun load(operationId: String): DuplicateCleanupJournal? = current
        override suspend fun save(journal: DuplicateCleanupJournal) {
            current = journal
        }
    }
}
