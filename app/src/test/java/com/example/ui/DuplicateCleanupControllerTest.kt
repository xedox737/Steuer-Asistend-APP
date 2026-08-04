package com.example.ui

import com.example.data.DuplicateCleanupFailure
import com.example.data.DuplicateCleanupJournal
import com.example.data.DuplicateCleanupPhase
import com.example.data.DuplicateCleanupReport
import com.example.data.DuplicateGroupPreview
import com.example.data.DuplicateMetadataPlan
import com.example.data.DuplicateReceiptRecord
import com.example.data.ReceiptIndexEntry
import com.example.data.ReceiptTombstone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateCleanupControllerTest {
    @Test
    fun loadIsReadOnlyAndShowsGroups() = runBlocking {
        val fake = FakeUseCase()
        val controller = DuplicateCleanupController(fake)

        controller.load(emptyList(), emptyMap())

        assertEquals(1, fake.analyzeCalls)
        assertEquals(0, fake.confirmMergeCalls)
        assertTrue(controller.state is DuplicateCleanupUiState.Ready)
    }

    @Test
    fun mergeRequiresPreviewBeforeExecution() = runBlocking {
        val fake = FakeUseCase()
        val controller = DuplicateCleanupController(fake)

        controller.requestMerge(preview())
        assertTrue(controller.state is DuplicateCleanupUiState.MergeConfirmation)
        assertEquals(0, fake.confirmMergeCalls)

        controller.confirmMerge()
        assertEquals(1, fake.confirmMergeCalls)
        assertTrue(controller.state is DuplicateCleanupUiState.Completed)
    }

    @Test
    fun wholeGroupDeletionRequiresBothConfirmations() = runBlocking {
        val fake = FakeUseCase()
        val controller = DuplicateCleanupController(fake)

        controller.requestWholeGroupDeletion(preview())
        controller.setWholeGroupConfirmations(first = true, second = false)
        controller.confirmWholeGroupDeletion()

        assertEquals(0, fake.confirmWholeGroupCalls)
        assertTrue(controller.state is DuplicateCleanupUiState.Failed)
    }

    @Test
    fun pendingJournalCanBeShownAndResumed() = runBlocking {
        val controller = DuplicateCleanupController(FakeUseCase())

        controller.showPendingOperations()
        assertTrue(controller.state is DuplicateCleanupUiState.PendingOperations)

        controller.resume("pending-op")
        assertTrue(controller.state is DuplicateCleanupUiState.Completed)
    }

    @Test
    fun partialCleanupReportIsShownAsFailure() = runBlocking {
        val fake = FakeUseCase(
            mergeReport = DuplicateCleanupReport(
                operationId = "op",
                phase = DuplicateCleanupPhase.REFERENCES_REMOVED,
                completed = false,
                failures = listOf(
                    DuplicateCleanupFailure(
                        phase = DuplicateCleanupPhase.DRIVE_METADATA_REMOVED,
                        target = "meta-2",
                        message = "Metadatendatei konnte nicht gelöscht werden."
                    )
                )
            )
        )
        val controller = DuplicateCleanupController(fake)

        controller.requestMerge(preview())
        controller.confirmMerge()

        val state = controller.state as DuplicateCleanupUiState.Failed
        assertTrue(state.message.contains("Metadatendatei"))
    }

    private class FakeUseCase(
        private val mergeReport: DuplicateCleanupReport = completedReport()
    ) : DuplicateCleanupUseCase {
        var analyzeCalls = 0
        var confirmMergeCalls = 0
        var confirmWholeGroupCalls = 0

        override suspend fun analyze(
            indexEntries: List<ReceiptIndexEntry>,
            tombstones: Map<String, ReceiptTombstone>,
            createdAtByInternalId: Map<String, String?>
        ): List<DuplicateGroupPreview> {
            analyzeCalls++
            return listOf(preview())
        }

        override suspend fun prepareMerge(preview: DuplicateGroupPreview): DuplicateCleanupJournal =
            journal(removeWholeGroup = false)

        override suspend fun confirmMerge(operationId: String): DuplicateCleanupReport {
            confirmMergeCalls++
            return mergeReport
        }

        override suspend fun prepareWholeGroupDeletion(
            preview: DuplicateGroupPreview
        ): DuplicateCleanupJournal = journal(removeWholeGroup = true)

        override suspend fun confirmWholeGroupDeletion(
            operationId: String,
            firstConfirmation: Boolean,
            secondConfirmation: Boolean
        ): DuplicateCleanupReport {
            confirmWholeGroupCalls++
            return completedReport()
        }

        override suspend fun pendingOperationIds(): Set<String> = setOf("pending-op")

        override suspend fun resume(operationId: String): DuplicateCleanupReport =
            completedReport()
    }

    companion object {
    private fun preview(): DuplicateGroupPreview {
        val canonical = record(1, "canonical")
        return DuplicateGroupPreview(
            mainDriveFileId = "main",
            canonical = canonical,
            duplicatesToRemove = listOf(record(2, "duplicate")),
            metadataPlan = DuplicateMetadataPlan(
                retainedMetadataFileIds = setOf("meta-canonical"),
                orphanMetadataFileIds = setOf("meta-duplicate")
            )
        )
    }

    private fun record(roomId: Int, internalId: String) = DuplicateReceiptRecord(
        roomId = roomId,
        internalId = internalId,
        displayId = "BLG-$internalId",
        mainDriveFileId = "main",
        metadataFileId = "meta-$internalId",
        deletionStatus = "DELETED",
        deletedAt = "2026-07-31T10:00:00Z",
        createdAt = "2026-07-01T10:00:00Z",
        indexReferenced = internalId == "canonical",
        indexMetadataFileId = if (internalId == "canonical") "meta-canonical" else null,
        tombstone = null,
        completenessScore = 10
    )

        private fun journal(removeWholeGroup: Boolean) = DuplicateCleanupJournal(
            operationId = "op",
            mainDriveFileId = "main",
            canonicalInternalId = if (removeWholeGroup) null else "canonical",
            targetInternalIds = listOf("duplicate"),
            metadataFileIds = listOf("meta-duplicate"),
            tombstoneInternalIds = listOf("duplicate"),
            removeWholeGroup = removeWholeGroup,
            phase = DuplicateCleanupPhase.PREVIEWED,
            createdAt = "2026-07-31T10:00:00Z",
            updatedAt = "2026-07-31T10:00:00Z"
        )

        private fun completedReport() = DuplicateCleanupReport(
            operationId = "op",
            phase = DuplicateCleanupPhase.COMPLETED,
            completed = true
        )
    }
}
