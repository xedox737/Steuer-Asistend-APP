package com.example.data

import android.content.Context

/**
 * Production entry point for the duplicate-cleanup workflow.
 *
 * It wires the read-only analyzer, durable journal, strict Drive actions and local Room gateway
 * into one feature object. Nothing is deleted by constructing this class or by calling [analyze].
 */
class DuplicateCleanupFeature(
    context: Context,
    receiptRepository: ReceiptRepository,
    accessTokenProvider: suspend () -> String,
    referenceMutator: DuplicateReferenceMutator,
    private val now: () -> String
) {
    private val receiptStore: DuplicateReceiptStore =
        ReceiptRepositoryDuplicateStore(receiptRepository)

    private val journalStore =
        SharedPreferencesDuplicateCleanupJournalStore(context)

    private val remoteActions: DuplicateRemoteCleanupActions =
        GoogleDriveDuplicateCleanupActions(
            accessTokenProvider = accessTokenProvider,
            referenceMutator = referenceMutator
        )

    private val gateway: DuplicateCleanupGateway =
        RepositoryDuplicateCleanupGateway(receiptStore, remoteActions)

    private val executor = DuplicateCleanupExecutor(
        journalStore = journalStore,
        gateway = gateway,
        now = now
    )

    private val coordinator = DuplicateCleanupCoordinator(
        receiptStore = receiptStore,
        journalStore = journalStore,
        executor = executor,
        now = now
    )

    /** Read-only. Does not change Room, Drive, index or tombstones. */
    suspend fun analyze(
        indexEntries: List<ReceiptIndexEntry>,
        tombstones: Map<String, ReceiptTombstone>,
        createdAtByInternalId: Map<String, String?> = emptyMap()
    ): List<DuplicateGroupPreview> = coordinator.analyze(
        indexEntries = indexEntries,
        tombstones = tombstones,
        createdAtByInternalId = createdAtByInternalId
    )

    /** Persists a preview only. No deletion is performed. */
    suspend fun prepareMerge(preview: DuplicateGroupPreview): DuplicateCleanupJournal =
        coordinator.createMergePreview(preview)

    /** Executes a previously prepared merge after explicit confirmation. */
    suspend fun confirmMerge(operationId: String): DuplicateCleanupReport =
        coordinator.confirmAndExecuteMerge(operationId)

    /** Persists a whole-group deletion preview only. No deletion is performed. */
    suspend fun prepareWholeGroupDeletion(
        preview: DuplicateGroupPreview
    ): DuplicateCleanupJournal = coordinator.createWholeGroupPreview(preview)

    /**
     * Executes whole-group deletion only when both confirmations are true.
     * The shared main file is handled only by this path and only after metadata removal.
     */
    suspend fun confirmWholeGroupDeletion(
        operationId: String,
        firstConfirmation: Boolean,
        secondConfirmation: Boolean
    ): DuplicateCleanupReport = coordinator.confirmAndExecuteWholeGroup(
        operationId = operationId,
        firstConfirmation = firstConfirmation,
        secondConfirmation = secondConfirmation
    )

    suspend fun resume(operationId: String): DuplicateCleanupReport =
        coordinator.resume(operationId)

    suspend fun pendingOperationIds(): Set<String> = journalStore.listOperationIds()
        .filterTo(mutableSetOf()) { operationId ->
            journalStore.load(operationId)?.phase != DuplicateCleanupPhase.COMPLETED
        }

    fun removeCompletedJournal(operationId: String) {
        journalStore.removeCompleted(operationId)
    }
}
