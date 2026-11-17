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
                    // The live connection must see the completed isolated transaction, including WAL pages.
                    source.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
                        require(cursor.moveToFirst() && cursor.getInt(0) == 0)
                    }
                    val sourceFile = File(requireNotNull(source.path)).canonicalFile
                    val stagingRoot = File(context.filesDir, "offline-backup-staging").canonicalFile
                    require(sourceFile.isFile && sourceFile.path.startsWith(stagingRoot.path + File.separator))
                    val plan = OfflineRestoreImportedFiles.verifyInstalled(context, token, entry)
                    require(OfflineRestoreJournal(context).load() == entry)
                    currentCoroutineContext().ensureActive()
                    token.writing {
                        val database = live.openHelper.writableDatabase
                        var attached = false
                        try {
                            database.execSQL("ATTACH DATABASE ? AS restore_import", arrayOf(sourceFile.path)); attached = true
                            database.beginTransaction()
                            try {
                                database.execSQL("PRAGMA defer_foreign_keys=ON")
                                val tables = OfflineBackupMetadataWriter.BUSINESS_TABLES.sorted()
                                tables.forEach { database.execSQL("DELETE FROM main.`$it`") }
                                // Device-local migration work refers to the old inventory and must not restart.
                                OfflineBackupMetadataWriter.LOCAL_TABLES.sorted().forEach { database.execSQL("DELETE FROM main.`$it`") }
                                tables.forEach { database.execSQL("INSERT INTO main.`$it` SELECT * FROM restore_import.`$it`") }
                                tables.forEach { table ->
                                    database.execSQL("DELETE FROM main.sqlite_sequence WHERE name = ?", arrayOf(table))
                                    database.execSQL("INSERT INTO main.sqlite_sequence SELECT * FROM restore_import.sqlite_sequence WHERE name = ?", arrayOf(table))
                                    require(!database.query("SELECT * FROM main.`$table` EXCEPT SELECT * FROM restore_import.`$table`").use { it.moveToFirst() })
                                    require(!database.query("SELECT * FROM restore_import.`$table` EXCEPT SELECT * FROM main.`$table`").use { it.moveToFirst() })
                                    require(!database.query("SELECT name, seq FROM main.sqlite_sequence WHERE name = ? EXCEPT SELECT name, seq FROM restore_import.sqlite_sequence WHERE name = ?", arrayOf(table, table)).use { it.moveToFirst() })
                                    require(!database.query("SELECT name, seq FROM restore_import.sqlite_sequence WHERE name = ? EXCEPT SELECT name, seq FROM main.sqlite_sequence WHERE name = ?", arrayOf(table, table)).use { it.moveToFirst() })
                                }
                                require(!database.query("PRAGMA main.foreign_key_check").use { it.moveToFirst() })
                                require(OfflineRestoreJournal(context).load() == entry)
                                database.setTransactionSuccessful()
                            } finally { database.endTransaction() }
                        } finally { if (attached) database.execSQL("DETACH DATABASE restore_import") }
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
