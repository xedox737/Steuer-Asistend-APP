package com.example.data

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.example.api.AiProviderSettings
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Replays an interrupted local restore. The caller owns the journal and maintenance lifetime. */
internal object OfflineRestoreRecovery {
    internal data class FileOperations(val syncDirectory: (File) -> Unit, val rename: (File, File) -> Unit)
    private val androidFiles = FileOperations(::syncDirectory) { from, to -> Os.rename(from.path, to.path) }

    /** Returns a terminal journal; the caller must refresh/close its DB before clearing it. */
    suspend fun recoverHeld(context: Context, live: AppDatabase?, token: OfflineBackupCoordinator.Maintenance,
        expected: OfflineRestoreJournal.Entry, files: FileOperations = androidFiles): OfflineRestoreJournal.Entry {
        try {
            OfflineBackupCoordinator.shared.requireMaintenance(token)
            return withContext(Dispatchers.IO) { token.suspending {
                val journal = OfflineRestoreJournal(context)
                require(journal.load() == expected)
                when (expected.phase) {
                    OfflineRestoreJournal.Phase.COMMITTED -> {
                        OfflineRestoreImportedFiles.finish(context, token, expected, files.syncDirectory)
                        expected
                    }
                    OfflineRestoreJournal.Phase.ROLLED_BACK -> {
                        val plan = requireNotNull(OfflineRestoreRollbackVerifier.verifyIndex(context, expected).filePlan)
                        require(plan.ownedDirectories.none { it.exists() })
                        expected
                    }
                    else -> {
                        val database = requireNotNull(live)
                        val checked = OfflineRestoreDatabaseVerifier.verify(context, expected)
                        val plan = requireNotNull(checked.files.index.filePlan)
                        val rollback = if (expected.phase == OfflineRestoreJournal.Phase.ROLLBACK) expected
                            else journal.advance(expected, OfflineRestoreJournal.Phase.ROLLBACK)
                        OfflineRestoreOwnedFilesRollback.apply(context, token, rollback, files.syncDirectory, files.rename)
                        OfflineRestoreDatabaseRollback.apply(context, database, token, rollback)
                        OfflineRestorePreferenceRollback.apply(context, token, rollback)
                        AiProviderSettings.restoreRollbackCiphertexts(context, token, rollback)
                        OfflineRestoreImportedFiles.rollback(context, token, rollback, files.syncDirectory)
                        require(plan.ownedDirectories.none { it.exists() })
                        require(journal.load() == rollback)
                        journal.advance(rollback, OfflineRestoreJournal.Phase.ROLLED_BACK)
                    }
                }
            } }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: ErrnoException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: NoSuchElementException) { invalid() }
        catch (_: SecurityException) { invalid() }
    }
    private fun syncDirectory(directory: File) {
        val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
        try { require(OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode)); Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }
    private fun invalid(): Nothing = throw OfflineBackupValidationException(
        "Die Wiederherstellung konnte nicht sicher abgeschlossen werden. Der Bestand bleibt gesperrt."
    )
}
