package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Replays verified database rows under maintenance; the caller keeps the recovery journal. */
internal object OfflineRestoreDatabaseRollback {
    private val tables get() = (OfflineBackupMetadataWriter.BUSINESS_TABLES + OfflineBackupMetadataWriter.LOCAL_TABLES).sorted()

    suspend fun apply(context: Context, live: AppDatabase, token: OfflineBackupCoordinator.Maintenance, entry: OfflineRestoreJournal.Entry) {
        try {
            OfflineBackupCoordinator.shared.requireMaintenance(token)
            require(entry.phase == OfflineRestoreJournal.Phase.ROLLBACK)
            withContext(Dispatchers.IO) {
                token.suspending {
                    val verified = OfflineRestoreDatabaseVerifier.verify(context, entry)
                    val index = OfflineRestoreRollbackVerifier.verifyIndex(context, entry)
                    require(index == verified.files.index && OfflineRestoreJournal(context).load() == entry)
                    currentCoroutineContext().ensureActive()
                    token.writing {
                        val db = live.openHelper.writableDatabase
                        var attached = false
                        try {
                            db.execSQL("ATTACH DATABASE ? AS restore_rollback", arrayOf(index.database.absolutePath))
                            attached = true
                            db.beginTransaction()
                            try {
                                db.execSQL("PRAGMA defer_foreign_keys=ON")
                                tables.forEach { db.execSQL("DELETE FROM main.`$it`") }
                                tables.forEach { db.execSQL("INSERT INTO main.`$it` SELECT * FROM restore_rollback.`$it`") }
                                db.execSQL("DELETE FROM main.sqlite_sequence")
                                db.execSQL("INSERT INTO main.sqlite_sequence SELECT * FROM restore_rollback.sqlite_sequence")
                                tables.forEach { table ->
                                    require(!db.query("SELECT * FROM main.`$table` EXCEPT SELECT * FROM restore_rollback.`$table`").use { it.moveToFirst() })
                                    require(!db.query("SELECT * FROM restore_rollback.`$table` EXCEPT SELECT * FROM main.`$table`").use { it.moveToFirst() })
                                }
                                require(!db.query("SELECT * FROM main.sqlite_sequence EXCEPT SELECT * FROM restore_rollback.sqlite_sequence").use { it.moveToFirst() })
                                require(!db.query("SELECT * FROM restore_rollback.sqlite_sequence EXCEPT SELECT * FROM main.sqlite_sequence").use { it.moveToFirst() })
                                require(OfflineRestoreJournal(context).load() == entry)
                                db.setTransactionSuccessful()
                            } finally { db.endTransaction() }
                        } finally { if (attached) db.execSQL("DETACH DATABASE restore_rollback") }
                    }
                    // Search is a derived FTS cache, not part of the business-table snapshot.
                    // If interrupted, the journal remains in ROLLBACK and replay can start again.
                    ReceiptRepository(live.receiptDao(), live.propertyDao(), managedDocumentDao = live.managedDocumentDao())
                        .rebuildDocumentSearchIndex()
                    require(OfflineRestoreJournal(context).load() == entry)
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: SecurityException) { invalid() }
        catch (_: android.database.SQLException) { invalid() }
    }

    private fun invalid(): Nothing = throw OfflineBackupValidationException(
        "Die Datenbank konnte nicht vollständig zurückgesetzt werden. Der Rückfall bleibt gesperrt und kann erneut versucht werden."
    )
}
