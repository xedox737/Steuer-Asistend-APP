package com.example.data

import android.content.Context
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Replaces business rows only after complete archive, file and rollback validation. */
internal object OfflineRestoreLiveDatabase {
    suspend fun apply(context: Context, live: AppDatabase, token: OfflineBackupCoordinator.Maintenance,
        entry: OfflineRestoreJournal.Entry, archive: OfflineBackupRestoreInspector.Prepared) {
        try {
            OfflineBackupCoordinator.shared.requireMaintenance(token)
            require(entry.phase == OfflineRestoreJournal.Phase.DATABASE)
            withContext(Dispatchers.IO) { token.suspending {
                OfflineRestoreDatabaseVerifier.verify(context, entry)
                OfflineRestoreStagedDatabase.prepare(context, token, entry, archive).use { staged ->
                    val source = staged.database.openHelper.writableDatabase
                    val sourceFile = File(requireNotNull(source.path)).canonicalFile
                    val stagingRoot = File(context.filesDir, "offline-backup-staging").canonicalFile
                    require(sourceFile.isFile && sourceFile.path.startsWith(stagingRoot.path + File.separator))
                    val plan = OfflineRestoreImportedFiles.verifyInstalled(context, token, entry)
                    require(OfflineRestoreJournal(context).load() == entry)
                    currentCoroutineContext().ensureActive()
                    token.writing {
                        val database = live.openHelper.writableDatabase
                        OfflineRestoreRowCopy.replace(source::query, database,
                            OfflineBackupMetadataWriter.BUSINESS_TABLES.sorted(), OfflineBackupMetadataWriter.LOCAL_TABLES,
                            allSequences = false) { require(OfflineRestoreJournal(context).load() == entry) }
                    }
                    // The search index is derived and can be replayed after a process interruption.
                    ReceiptRepository(live.receiptDao(), live.propertyDao(), managedDocumentDao = live.managedDocumentDao())
                        .rebuildDocumentSearchIndex()
                    require(OfflineRestoreImportedFiles.verifyInstalled(context, token, entry) == plan)
                    require(OfflineRestoreJournal(context).load() == entry)
                }
            } }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: android.database.SQLException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: NoSuchElementException) { invalid() }
        catch (_: SecurityException) { invalid() }
    }
    private fun invalid(): Nothing = throw OfflineBackupValidationException(
        "Die Datenbank konnte nicht vollständig wiederhergestellt werden. Der Rückfall bleibt gesperrt und kann ausgeführt werden."
    )
}
