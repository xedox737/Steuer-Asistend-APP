package com.example.data

import android.content.Context
import androidx.room.withTransaction
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException

/** Creates a fresh isolated probe whose references point to verified installed files. */
internal object OfflineRestoreStagedDatabase {
    /** Keep [archive] open until the returned probe is closed. Never writes the live database. */
    suspend fun prepare(context: Context, token: OfflineBackupCoordinator.Maintenance,
        entry: OfflineRestoreJournal.Entry, archive: OfflineBackupRestoreInspector.Prepared): OfflineBackupRoomProbe.Prepared {
        var owned: OfflineBackupRoomProbe.Prepared? = null; var delivered = false
        try {
            val result = withContext(Dispatchers.IO) {
                OfflineBackupCoordinator.shared.requireMaintenance(token)
                require(entry.phase in setOf(OfflineRestoreJournal.Phase.FILES, OfflineRestoreJournal.Phase.DATABASE))
                token.suspending {
                    val plan = OfflineRestoreImportedFiles.verifyInstalled(context, token, entry)
                    val probe = OfflineBackupRoomProbe.prepare(context, archive.contents, archive.generation).also { owned = it }
                    require(OfflineRestoreFilePlan.prepare(context, entry.operationId, probe.manifest, requireUnused = false) == plan)
                    val replacements = linkedMapOf<String, String>()
                    probe.manifest.aliases.forEach { (alias, payloadEntry) ->
                        val source = requireNotNull(archive.contents.parts.getValue(payloadEntry).file).canonicalPath
                        val target = plan.aliases.getValue(alias).canonicalPath
                        val previous = replacements.put(source, target)
                        require(previous == null || previous == target)
                    }
                    val used = hashSetOf<String>()
                    fun mapped(path: String): String = requireNotNull(replacements[path]).also { used += path }
                    fun rewrite(raw: String): String {
                        if (!raw.startsWith("[")) return mapped(raw)
                        val input = JSONArray(raw); require(input.length() in 1..10_000)
                        val output = JSONArray()
                        for (index in 0 until input.length()) output.put(mapped(input.getString(index)))
                        return output.toString()
                    }
                    val database = probe.database.openHelper.writableDatabase
                    val cancellation = currentCoroutineContext()
                    probe.database.withTransaction {
                        for (table in OfflineBackupMetadataWriter.BUSINESS_TABLES.sorted()) {
                            cancellation.ensureActive()
                            val columns = database.query("PRAGMA table_info(`$table`)").use { cursor -> buildSet {
                                while (cursor.moveToNext()) add(cursor.getString(1))
                            } }
                            for (column in OfflineBackupMetadataWriter.PATH_COLUMNS.intersect(columns).sorted()) {
                                database.query("SELECT rowid, `$column` FROM `$table` WHERE `$column` IS NOT NULL AND `$column` != '' ORDER BY rowid").use { cursor ->
                                    while (cursor.moveToNext()) {
                                        cancellation.ensureActive()
                                        val rowId = cursor.getLong(0); val updated = rewrite(cursor.getString(1))
                                        database.execSQL("UPDATE `$table` SET `$column` = ? WHERE rowid = ?", arrayOf(updated, rowId))
                                    }
                                }
                            }
                        }
                        require(used == replacements.keys)
                        require(OfflineRestoreJournal(context).load() == entry)
                    }
                    probe.manifest.rowCounts.forEach { (table, expected) ->
                        cancellation.ensureActive()
                        database.query("SELECT COUNT(*) FROM `$table`").use { require(it.moveToFirst() && it.getLong(0) == expected) }
                    }
                    database.query("PRAGMA integrity_check").use { require(it.moveToFirst() && it.getString(0) == "ok" && !it.moveToNext()) }
                    database.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) }
                    require(OfflineRestoreImportedFiles.verifyInstalled(context, token, entry) == plan)
                    probe
                }
            }
            delivered = true; return result
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: android.database.SQLException) { invalid() }
        catch (_: JSONException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: NoSuchElementException) { invalid() }
        catch (_: SecurityException) { invalid() }
        finally { if (!delivered) owned?.close() }
    }
    private fun invalid(): Nothing = throw OfflineBackupValidationException(
        "Die Wiederherstellungsdatenbank konnte nicht sicher vorbereitet werden. Der Bestand bleibt gesperrt."
    )
}
