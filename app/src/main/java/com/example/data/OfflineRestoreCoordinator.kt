package com.example.data

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.example.api.AiProviderSettings
import java.io.File
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** Coordinates a complete replacement while the caller retains the inspected archive. */
internal object OfflineRestoreCoordinator {
    internal data class FileOperations(val sync: (File) -> Unit, val rename: (File, File) -> Unit,
        val link: (File, File) -> Unit)
    private val androidFiles = FileOperations({ directory ->
        val fd = Os.open(directory.path, OsConstants.O_RDONLY, 0)
        try { require(OsConstants.S_ISDIR(Os.fstat(fd).st_mode)); Os.fsync(fd) } finally { Os.close(fd) }
    }, { from, to -> Os.rename(from.path, to.path) }, { from, to -> Os.link(from.path, to.path) })

    data class Result(val preparationCleanupFailed: Boolean)

    /** Caller must hold a Prepared lease until this method returns, including after cancellation. */
    suspend fun restore(context: Context, database: AppDatabase, archive: OfflineBackupRestoreInspector.Prepared,
        includeCredentials: Boolean, refresh: suspend () -> Unit, files: FileOperations = androidFiles): Result =
        OfflineBackupKeyManager(context).withRestoreExclusivity {
            val coordinator = OfflineBackupCoordinator.shared
            if (coordinator.observeRestoreJournal(context)) throw OfflineBackupValidationException(
                "Eine frühere Wiederherstellung muss zuerst abgeschlossen werden.")
            val journal = OfflineRestoreJournal(context)
            val history = OfflineBackupHistory(context)
            val operationId = UUID.randomUUID().toString()
            coordinator.beginMaintenance().use { token -> token.suspending {
                var plan: OfflineRestoreFilePlanStore.Snapshot? = null
                var rollback: OfflineRestoreRollbackBundle.Prepared? = null
                var historyStarted = false
                try {
                    require(journal.load() == null)
                    if (includeCredentials) require(archive.preview.credentialProviders.isNotEmpty())
                    history.record(OfflineBackupHistory.Event(operationId, OfflineBackupHistory.Kind.RESTORE,
                        OfflineBackupHistory.Outcome.STARTED, System.currentTimeMillis()))
                    historyStarted = true
                    val filePlan = OfflineBackupRoomProbe.prepare(context, archive.contents, archive.generation).use { probe ->
                        OfflineRestoreFilePlan.prepare(context, operationId, probe.manifest)
                    }
                    val preparedPlan = OfflineRestoreFilePlanStore.capture(context, token, UUID.randomUUID().toString(), filePlan)
                    plan = preparedPlan
                    val preparedRollback = OfflineRestoreRollbackBundle.prepare(context, database, token, includeCredentials)
                    rollback = preparedRollback
                    OfflineBackupResourceBudget.checkRestore(context, archive, preparedRollback, filePlan)
                    var entry = journal.beginProtected(token, preparedRollback.id, preparedRollback.manifestBytes, preparedRollback.manifestSha256, preparedPlan)
                    entry = journal.advance(entry, OfflineRestoreJournal.Phase.FILES)
                    OfflineRestoreImportedFiles.install(context, token, entry, archive, files.sync, files.rename, files.link)
                    entry = journal.advance(entry, OfflineRestoreJournal.Phase.DATABASE)
                    OfflineRestoreLiveDatabase.apply(context, database, token, entry, archive)
                    entry = journal.advance(entry, OfflineRestoreJournal.Phase.PREFERENCES)
                    OfflineRestoreLivePreferences.apply(context, token, entry, archive)
                    AiProviderSettings.importBackupCredentials(context, token, entry, archive)
                    refresh()
                    entry = journal.advance(entry, OfflineRestoreJournal.Phase.COMMITTED)
                    OfflineRestoreImportedFiles.finish(context, token, entry, files.sync)
                    OfflineRestoreRecovery.recordTerminal(context, entry)
                    journal.clear(entry)
                    val cleanupFailed = try {
                        withContext(NonCancellable) { OfflineRestoreRollbackBundle.discardUnjournalled(context, token, preparedRollback, preparedPlan) }
                        false
                    } catch (_: Exception) { true }
                    coordinator.clearRestorePending(context, token)
                    Result(cleanupFailed)
                } catch (error: Throwable) {
                    var committed = false
                    try {
                        withContext(NonCancellable) {
                            val pending = journal.load()
                            if (pending != null) {
                                require(pending.operationId == plan?.operationId && pending.rollbackId == rollback?.id)
                                val terminal = OfflineRestoreRecovery.recoverHeld(context, database, token, pending,
                                    OfflineRestoreRecovery.FileOperations(files.sync, files.rename))
                                refresh()
                                OfflineRestoreRecovery.recordTerminal(context, terminal)
                                journal.clear(terminal)
                                committed = terminal.phase == OfflineRestoreJournal.Phase.COMMITTED
                            } else if (historyStarted) {
                                val outcome = if (error is CancellationException) OfflineBackupHistory.Outcome.CANCELLED else OfflineBackupHistory.Outcome.FAILED
                                try { history.record(OfflineBackupHistory.Event(operationId, OfflineBackupHistory.Kind.RESTORE,
                                    outcome, System.currentTimeMillis(), failure = if (outcome == OfflineBackupHistory.Outcome.FAILED)
                                        OfflineBackupHistory.Failure.UNKNOWN else null)) }
                                catch (historyError: Exception) { error.addSuppressed(historyError) }
                            }
                            val fixedPlan = plan; val fixedRollback = rollback
                            if (fixedPlan != null) {
                                if (fixedRollback != null) OfflineRestoreRollbackBundle.discardUnjournalled(context, token, fixedRollback, fixedPlan)
                                else OfflineRestoreFilePlanStore.discardUnjournalled(context, token, fixedPlan)
                            }
                            if (coordinator.restorePending.value) coordinator.clearRestorePending(context, token)
                        }
                    } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
                    if (committed) return@suspending Result(preparationCleanupFailed = true)
                    throw error
                }
            } }
        }
}
