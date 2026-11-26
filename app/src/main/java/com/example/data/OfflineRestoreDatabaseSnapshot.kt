package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Private row-for-row rollback copy; it is not a portable or complete backup. */
internal object OfflineRestoreDatabaseSnapshot {
    data class Snapshot(val id: String, val file: File, val size: Long, val sha256: String, val rowCounts: Map<String, Long>)
    private val tables get() = (OfflineBackupMetadataWriter.BUSINESS_TABLES + OfflineBackupMetadataWriter.LOCAL_TABLES).sorted()

    /** The caller must hold the shared maintenance token throughout capture and handoff. */
    suspend fun capture(context: Context, source: AppDatabase, token: OfflineBackupCoordinator.Maintenance, id: String): Snapshot {
        var owned: File? = null; var delivered = false
        try {
            val snapshot = withContext(Dispatchers.IO) {
                OfflineBackupCoordinator.shared.requireMaintenance(token)
                require(UUID.fromString(id).toString() == id)
                token.writing {
                    val original = source.openHelper.writableDatabase
                    val pageCount = scalar(original, "PRAGMA page_count")
                    val pageSize = scalar(original, "PRAGMA page_size")
                    if (pageCount < 0 || pageSize <= 0 || pageCount > (Long.MAX_VALUE - OfflineBackupResourceBudget.RESERVE_BYTES) / pageSize / 2)
                        invalid("Der Speicherbedarf des Rückfallstands konnte nicht sicher bestimmt werden.")
                    val required = pageCount * pageSize * 2 + OfflineBackupResourceBudget.RESERVE_BYTES
                    val directory = File(context.noBackupFilesDir, "offline-backup-state")
                    if (!directory.isDirectory && !directory.mkdirs()) invalid("Der private Rückfallbereich ist nicht verfügbar.")
                    val available = directory.usableSpace
                    if (available < required) invalid("Zu wenig privater Speicher für den Rückfallstand: benötigt $required Byte, verfügbar $available Byte.")
                    val file = File(directory, "rollback-$id.db")
                    if (!file.createNewFile()) invalid("Der private Rückfallplatz ist bereits belegt.")
                    owned = file
                    val empty = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath).build()
                    try {
                        original.beginTransaction()
                        try {
                            OfflineRestoreRowCopy.replace(original::query, empty.openHelper.writableDatabase, tables)
                            original.setTransactionSuccessful()
                        } finally { original.endTransaction() }
                    } finally { empty.close() }
                    val copy = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath).build()
                    val counts = try {
                        val database = copy.openHelper.readableDatabase
                        database.query("PRAGMA integrity_check").use { require(it.moveToFirst() && it.getString(0) == "ok" && !it.moveToNext()) }
                        tables.associateWith { scalar(database, "SELECT COUNT(*) FROM `$it`") }
                    } finally { copy.close() }
                    FileOutputStream(file, true).use { it.fd.sync() }
                    val digest = MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { input -> val buffer = ByteArray(8192); while (true) {
                        val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count)
                    } }
                    Snapshot(id, file, file.length(), digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }, counts)
                }
            }
            delivered = true
            return snapshot
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid("Der Datenbank-Rückfallstand konnte nicht vollständig gesichert werden.") }
        catch (_: IllegalArgumentException) { invalid("Der Datenbank-Rückfallstand ist ungültig.") }
        catch (_: IllegalStateException) { invalid("Der Datenbank-Rückfallstand konnte nicht sicher geprüft werden.") }
        catch (_: android.database.sqlite.SQLiteException) { invalid("Der Datenbank-Rückfallstand konnte nicht vollständig gesichert werden.") }
        finally {
            if (!delivered) owned?.let { file ->
                listOf(file, File(file.path + "-journal"), File(file.path + "-wal"), File(file.path + "-shm")).forEach {
                    if (it.exists() && !it.delete()) invalid("Unvollständige private Rückfalldaten konnten nicht entfernt werden.")
                }
            }
        }
    }
    private fun scalar(database: SupportSQLiteDatabase, sql: String): Long = database.query(sql).use { require(it.moveToFirst()); it.getLong(0) }
    private fun invalid(message: String): Nothing = throw OfflineBackupValidationException(message)
}
