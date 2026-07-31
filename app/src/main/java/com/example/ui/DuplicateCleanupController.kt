package com.example.ui

import com.example.data.DuplicateCleanupFeature
import com.example.data.DuplicateCleanupJournal
import com.example.data.DuplicateCleanupReport
import com.example.data.DuplicateGroupPreview
import com.example.data.ReceiptIndexEntry
import com.example.data.ReceiptTombstone

/**
 * Thin, UI-friendly boundary around the duplicate cleanup feature.
 * The controller never performs cleanup during analysis or preview creation.
 */
interface DuplicateCleanupUseCase {
    suspend fun analyze(
        indexEntries: List<ReceiptIndexEntry>,
        tombstones: Map<String, ReceiptTombstone>,
        createdAtByInternalId: Map<String, String?> = emptyMap()
    ): List<DuplicateGroupPreview>

    suspend fun prepareMerge(preview: DuplicateGroupPreview): DuplicateCleanupJournal
    suspend fun confirmMerge(operationId: String): DuplicateCleanupReport
    suspend fun prepareWholeGroupDeletion(preview: DuplicateGroupPreview): DuplicateCleanupJournal
    suspend fun confirmWholeGroupDeletion(
        operationId: String,
        firstConfirmation: Boolean,
        secondConfirmation: Boolean
    ): DuplicateCleanupReport
}

class DuplicateCleanupFeatureUseCase(
    private val feature: DuplicateCleanupFeature
) : DuplicateCleanupUseCase {
    override suspend fun analyze(
        indexEntries: List<ReceiptIndexEntry>,
        tombstones: Map<String, ReceiptTombstone>,
        createdAtByInternalId: Map<String, String?>
    ): List<DuplicateGroupPreview> = feature.analyze(
        indexEntries,
        tombstones,
        createdAtByInternalId
    )

    override suspend fun prepareMerge(preview: DuplicateGroupPreview): DuplicateCleanupJournal =
        feature.prepareMerge(preview)

    override suspend fun confirmMerge(operationId: String): DuplicateCleanupReport =
        feature.confirmMerge(operationId)

    override suspend fun prepareWholeGroupDeletion(
        preview: DuplicateGroupPreview
    ): DuplicateCleanupJournal = feature.prepareWholeGroupDeletion(preview)

    override suspend fun confirmWholeGroupDeletion(
        operationId: String,
        firstConfirmation: Boolean,
        secondConfirmation: Boolean
    ): DuplicateCleanupReport = feature.confirmWholeGroupDeletion(
        operationId,
        firstConfirmation,
        secondConfirmation
    )
}

sealed interface DuplicateCleanupUiState {
    data object Idle : DuplicateCleanupUiState
    data object Loading : DuplicateCleanupUiState
    data class Ready(val groups: List<DuplicateGroupPreview>) : DuplicateCleanupUiState
    data class MergeConfirmation(
        val preview: DuplicateGroupPreview,
        val journal: DuplicateCleanupJournal
    ) : DuplicateCleanupUiState
    data class WholeGroupConfirmation(
        val preview: DuplicateGroupPreview,
        val journal: DuplicateCleanupJournal,
        val firstConfirmation: Boolean = false,
        val secondConfirmation: Boolean = false
    ) : DuplicateCleanupUiState
    data class Completed(val report: DuplicateCleanupReport) : DuplicateCleanupUiState
    data class Failed(val message: String) : DuplicateCleanupUiState
}

class DuplicateCleanupController(
    private val useCase: DuplicateCleanupUseCase
) {
    var state: DuplicateCleanupUiState = DuplicateCleanupUiState.Idle
        private set

    suspend fun load(
        indexEntries: List<ReceiptIndexEntry>,
        tombstones: Map<String, ReceiptTombstone>,
        createdAtByInternalId: Map<String, String?> = emptyMap()
    ) {
        state = DuplicateCleanupUiState.Loading
        state = runCatching {
            DuplicateCleanupUiState.Ready(
                useCase.analyze(indexEntries, tombstones, createdAtByInternalId)
            )
        }.getOrElse { DuplicateCleanupUiState.Failed(it.message ?: "Dubletten konnten nicht geprüft werden.") }
    }

    suspend fun requestMerge(preview: DuplicateGroupPreview) {
        state = runCatching {
            DuplicateCleanupUiState.MergeConfirmation(
                preview,
                useCase.prepareMerge(preview)
            )
        }.getOrElse { DuplicateCleanupUiState.Failed(it.message ?: "Vorschau konnte nicht erstellt werden.") }
    }

    suspend fun confirmMerge() {
        val current = state as? DuplicateCleanupUiState.MergeConfirmation ?: return
        state = execute { useCase.confirmMerge(current.journal.operationId) }
    }

    suspend fun requestWholeGroupDeletion(preview: DuplicateGroupPreview) {
        state = runCatching {
            DuplicateCleanupUiState.WholeGroupConfirmation(
                preview,
                useCase.prepareWholeGroupDeletion(preview)
            )
        }.getOrElse { DuplicateCleanupUiState.Failed(it.message ?: "Löschvorschau konnte nicht erstellt werden.") }
    }

    fun setWholeGroupConfirmations(first: Boolean, second: Boolean) {
        val current = state as? DuplicateCleanupUiState.WholeGroupConfirmation ?: return
        state = current.copy(firstConfirmation = first, secondConfirmation = second)
    }

    suspend fun confirmWholeGroupDeletion() {
        val current = state as? DuplicateCleanupUiState.WholeGroupConfirmation ?: return
        if (!current.firstConfirmation || !current.secondConfirmation) {
            state = DuplicateCleanupUiState.Failed(
                "Die vollständige Gruppenlöschung benötigt zwei ausdrückliche Bestätigungen."
            )
            return
        }
        state = execute {
            useCase.confirmWholeGroupDeletion(
                current.journal.operationId,
                current.firstConfirmation,
                current.secondConfirmation
            )
        }
    }

    fun cancel() {
        state = DuplicateCleanupUiState.Idle
    }

    private suspend fun execute(block: suspend () -> DuplicateCleanupReport): DuplicateCleanupUiState =
        runCatching(block).fold(
            onSuccess = { report ->
                if (report.completed) {
                    DuplicateCleanupUiState.Completed(report)
                } else {
                    val message = report.failures.joinToString("; ") { it.message }
                        .ifBlank { "Die Bereinigung wurde nicht abgeschlossen." }
                    DuplicateCleanupUiState.Failed(message)
                }
            },
            onFailure = { DuplicateCleanupUiState.Failed(it.message ?: "Bereinigung fehlgeschlagen.") }
        )
}
