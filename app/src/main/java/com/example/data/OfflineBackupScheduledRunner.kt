package com.example.data

import android.content.Context
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Coordinates one claimed occurrence; Android owns registration and the foreground service. */
internal class OfflineBackupScheduledRunner(private val context: Context, private val hooks: Hooks,
    private val now: () -> Instant = Instant::now) {
    enum class Result { COMPLETED, FAILED, RETRY, SKIPPED }
    interface PreparedExport { suspend fun publish(operationId: String) }
    interface Hooks {
        suspend fun reconcile()
        /** Called under a registered writer lease; do not publish a backup here. */
        suspend fun prepare(config: OfflineBackupScheduleStore.Config): PreparedExport
        fun notifyFailure()
    }
    private val schedule = OfflineBackupScheduleStore(context)
    private val history = OfflineBackupHistory(context)
    private val coordinator = OfflineBackupCoordinator.shared

    suspend fun run(generation: String, occurrenceId: String): Result {
        var claimed = false
        var publishing = false
        var retryReconciliation = true
        try {
            hooks.reconcile()
            retryReconciliation = false
            if (coordinator.observeRestoreJournal(context)) return Result.RETRY
            val prepared = coordinator.beginWrite().use {
                // The writer lease prevents a restore from entering between the journal
                // check and database initialization. It is released before snapshot capture.
                if (coordinator.observeRestoreJournal(context)) return Result.RETRY
                val decision = schedule.claimForExecution(generation, occurrenceId, now())
                when (decision.result) {
                    OfflineBackupScheduleStore.ClaimResult.NOT_DUE, OfflineBackupScheduleStore.ClaimResult.BUSY -> return Result.RETRY
                    OfflineBackupScheduleStore.ClaimResult.STALE, OfflineBackupScheduleStore.ClaimResult.ALREADY_CLAIMED -> return Result.SKIPPED
                    OfflineBackupScheduleStore.ClaimResult.CLAIMED -> Unit
                }
                claimed = true
                // The next occurrence is already durable even if this enqueue fails.
                retryReconciliation = true
                hooks.reconcile()
                retryReconciliation = false
                hooks.prepare(requireNotNull(decision.config))
            }
            publishing = true
            prepared.publish(occurrenceId)
            require(terminal(occurrenceId)?.outcome == OfflineBackupHistory.Outcome.VERIFIED)
            return Result.COMPLETED
        } catch (error: CancellationException) {
            if (claimed && !publishing) try { withContext(NonCancellable) { recordPreparationFailure(occurrenceId, cancelled = true) } }
            catch (historyError: Exception) { error.addSuppressed(historyError) }
            throw error
        } catch (error: Exception) {
            if (claimed && !publishing) {
                try { withContext(NonCancellable) { recordPreparationFailure(occurrenceId, cancelled = false) } }
                catch (historyError: Exception) { error.addSuppressed(historyError) }
            }
            if (claimed) try { hooks.notifyFailure() } catch (_: Exception) { }
            return if (claimed && !retryReconciliation) Result.FAILED else Result.RETRY
        } finally {
            if (claimed) withContext(NonCancellable) {
                // If recording failed, retain the claim so reconciliation cannot blindly rerun it.
                try { if (terminal(occurrenceId) != null) schedule.finish(occurrenceId) } catch (_: Exception) { }
            }
        }
    }

    private fun recordPreparationFailure(operationId: String, cancelled: Boolean) {
        history.beginFullBackup(operationId, OfflineBackupHistory.Origin.AUTOMATIC, now().toEpochMilli())
        history.record(OfflineBackupHistory.Event(operationId, OfflineBackupHistory.Kind.FULL,
            if (cancelled) OfflineBackupHistory.Outcome.CANCELLED else OfflineBackupHistory.Outcome.FAILED,
            now().toEpochMilli(), failure = if (cancelled) null else OfflineBackupHistory.Failure.UNKNOWN,
            origin = OfflineBackupHistory.Origin.AUTOMATIC))
    }
    private fun terminal(operationId: String): OfflineBackupHistory.Event? {
        val state = history.load()
        return (state.recent + listOfNotNull(state.lastAttempt, state.lastFullSuccess)).lastOrNull {
            it.operationId == operationId && it.kind == OfflineBackupHistory.Kind.FULL && it.outcome != OfflineBackupHistory.Outcome.STARTED
        }
    }
}
