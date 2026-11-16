package com.example.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Isolated Room/schema and original-reference validation; never opens or writes the live database. */
internal object OfflineRestoreDatabaseVerifier {
    data class Checked(val files: OfflineRestoreFileManifestVerifier.Checked,
        val preferences: OfflineRestorePreferenceVerifier.Checked,
        val references: List<OfflineBackupMetadataWriter.Reference>, val rowCounts: Map<String, Long>)
    private data class Column(val name: String, val type: String, val notNull: Int, val primary: Int)
    private data class ForeignKey(val table: String, val update: String, val delete: String, val match: String, val columns: List<Pair<String, String>>)
    private data class Index(val unique: Boolean, val partial: Boolean, val columns: List<String>)
    private val tables get() = (OfflineBackupMetadataWriter.BUSINESS_TABLES + OfflineBackupMetadataWriter.LOCAL_TABLES).sorted()
    private const val WORKSPACE = 80L * 1024 * 1024

    suspend fun verify(context: Context, journal: OfflineRestoreJournal.Entry): Checked = withContext(Dispatchers.IO) {
        var directory: File? = null; var probe: AppDatabase? = null; var baseline: AppDatabase? = null
        try {
            val files = OfflineRestoreFileManifestVerifier.verify(context, journal)
            val preferences = OfflineRestorePreferenceVerifier.verify(context, journal)
            val index = files.index; require(preferences.index == index)
            val parent = File(context.noBackupFilesDir, "offline-backup-state")
            require(index.databaseBytes > 0 && index.databaseBytes <= (Long.MAX_VALUE - WORKSPACE) / 2)
            val needed = index.databaseBytes * 2 + WORKSPACE; val available = parent.usableSpace
            if (available < needed) invalid("Zu wenig privater Speicher für die Rückfallprüfung: benötigt $needed Byte, verfügbar $available Byte.")
            val work = File(parent, "rollback-check-${UUID.randomUUID()}")
            if (!work.mkdir()) invalid("Der private Prüfbereich konnte nicht angelegt werden.")
            directory = work
            val copy = File(work, "rollback.db")
            val copiedHash = MessageDigest.getInstance("SHA-256"); var copiedBytes = 0L
            FileInputStream(index.database).use { input -> FileOutputStream(copy).use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive(); val size = input.read(buffer); if (size < 0) break
                    require(size.toLong() <= index.databaseBytes - copiedBytes)
                    output.write(buffer, 0, size); copiedHash.update(buffer, 0, size); copiedBytes += size
                }
                output.fd.sync()
            } }
            require(copiedBytes == index.databaseBytes && hex(copiedHash.digest()) == index.databaseSha256)
            val reread = MessageDigest.getInstance("SHA-256"); var rereadBytes = 0L
            FileInputStream(copy).use { input -> val buffer = ByteArray(8192); while (true) {
                currentCoroutineContext().ensureActive(); val size = input.read(buffer); if (size < 0) break
                require(size.toLong() <= index.databaseBytes - rereadBytes); reread.update(buffer, 0, size); rereadBytes += size
            } }
            require(rereadBytes == index.databaseBytes && hex(reread.digest()) == index.databaseSha256)
            val isolated = Room.databaseBuilder(context, AppDatabase::class.java, copy.absolutePath).build().also { probe = it }
            val fresh = Room.databaseBuilder(context, AppDatabase::class.java, File(work, "baseline.db").absolutePath).build().also { baseline = it }
            val actual = isolated.openHelper.writableDatabase
            val expected = fresh.openHelper.writableDatabase
            actual.query("PRAGMA integrity_check").use { require(it.moveToFirst() && it.getString(0) == "ok" && !it.moveToNext()) }
            require(actual.version == expected.version && businessTables(actual) == tables.toSet() && businessTables(expected) == tables.toSet())
            tables.forEach { table ->
                currentCoroutineContext().ensureActive()
                require(columns(actual, table) == columns(expected, table))
                require(foreignKeys(actual, table) == foreignKeys(expected, table))
                require(indices(actual, table) == indices(expected, table))
            }
            val emptyContext = object : ContextWrapper(context) {
                override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = EmptyPreferences
            }
            val draftFile = File(work, "references.json")
            val fixedByPathHash = files.references.associateBy { it.pathSha256 }
            val draft = OfflineBackupMetadataWriter(emptyContext, isolated,
                referenceMode = OfflineBackupMetadataWriter.ReferenceMode.ROLLBACK_INVENTORY,
                referenceFileExists = { path ->
                    val hash = hex(MessageDigest.getInstance("SHA-256").digest(path.toByteArray(Charsets.UTF_8)))
                    Uri.parse(path).scheme == null && fixedByPathHash[hash]?.state?.let {
                        it == OfflineRestoreReferenceSnapshot.State.OWNED || it == OfflineRestoreReferenceSnapshot.State.EXTERNAL
                    } == true
                }).write(draftFile)
            require(draft.references.size == files.references.size)
            val ownedByPath = files.files.associateBy { version ->
                val base = (if (version.portablePath.startsWith("files/")) context.filesDir else context.cacheDir).canonicalFile
                val relative = version.portablePath.substringAfter('/')
                File(base, relative).absolutePath
            }
            draft.references.zip(files.references).forEach { (actualReference, fixed) ->
                currentCoroutineContext().ensureActive()
                val pathHash = hex(MessageDigest.getInstance("SHA-256").digest(actualReference.path.toByteArray(Charsets.UTF_8)))
                require(actualReference.key == fixed.key && pathHash == fixed.pathSha256)
                val local = localFile(actualReference.path)
                val owned = local?.absolutePath?.let(ownedByPath::get)
                if (owned != null) {
                    require(fixed.state == OfflineRestoreReferenceSnapshot.State.OWNED && fixed.stagedName == owned.stagedName &&
                        fixed.bytes == owned.bytes && fixed.sha256 == owned.sha256)
                } else require(fixed.state != OfflineRestoreReferenceSnapshot.State.OWNED)
            }
            require(OfflineRestoreRollbackVerifier.verifyIndex(context, journal) == index)
            currentCoroutineContext().ensureActive()
            Checked(files, preferences, draft.references, draft.rowCounts)
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: NoSuchElementException) { invalid() }
        catch (_: SecurityException) { invalid() }
        catch (_: android.database.SQLException) { invalid() }
        finally {
            try { probe?.close() } finally { try { baseline?.close() } finally {
                directory?.let { if (it.exists() && !it.deleteRecursively()) invalid("Private Datenbankprüfdaten konnten nicht entfernt werden.") }
            } }
        }
    }
    private fun businessTables(db: SupportSQLiteDatabase): Set<String> = db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
        buildSet { while (cursor.moveToNext()) {
            val name = cursor.getString(0)
            if (!name.startsWith("sqlite_") && !name.startsWith("document_search_fts") && name !in OfflineBackupMetadataWriter.SYSTEM_TABLES) add(name)
        } }
    }
    private fun columns(db: SupportSQLiteDatabase, table: String): Map<String, Column> = db.query("PRAGMA table_info(`$table`)").use { cursor ->
        buildMap { while (cursor.moveToNext()) {
            val name = cursor.getString(1); require(name !in this)
            put(name, Column(name, cursor.getString(2).uppercase(Locale.ROOT), cursor.getInt(3), cursor.getInt(5)))
        } }
    }
    private fun foreignKeys(db: SupportSQLiteDatabase, table: String): Set<ForeignKey> = db.query("PRAGMA foreign_key_list(`$table`)").use { cursor ->
        data class Row(val sequence: Int, val table: String, val from: String, val to: String, val update: String, val delete: String, val match: String)
        val groups = linkedMapOf<Int, MutableList<Row>>()
        while (cursor.moveToNext()) groups.getOrPut(cursor.getInt(0)) { mutableListOf() }.add(Row(cursor.getInt(1), cursor.getString(2),
            cursor.getString(3), cursor.getString(4), cursor.getString(5), cursor.getString(6), cursor.getString(7)))
        groups.values.map { rows ->
            val sorted = rows.sortedBy { it.sequence }; val first = sorted.first()
            require(sorted.map { it.sequence } == sorted.indices.toList() && sorted.all { it.table == first.table && it.update == first.update && it.delete == first.delete && it.match == first.match })
            ForeignKey(first.table, first.update, first.delete, first.match, sorted.map { it.from to it.to })
        }.toSet()
    }
    private fun indices(db: SupportSQLiteDatabase, table: String): List<Index> {
        val result = mutableListOf<Index>()
        db.query("PRAGMA index_list(`$table`)").use { cursor ->
            val partialColumn = cursor.getColumnIndex("partial")
            while (cursor.moveToNext()) {
                val name = cursor.getString(1); require(name.matches(Regex("[A-Za-z0-9_]+")))
                val columns = db.query("PRAGMA index_info(`$name`)").use { columnsCursor -> buildList {
                    while (columnsCursor.moveToNext()) { require(columnsCursor.getInt(0) == size); add(columnsCursor.getString(2) ?: "") }
                } }
                result += Index(cursor.getInt(2) != 0, partialColumn >= 0 && cursor.getInt(partialColumn) != 0, columns)
            }
        }
        return result.sortedWith(compareBy({ it.unique }, { it.partial }, { it.columns.joinToString("\u0000") }))
    }
    private fun localFile(path: String): File? {
        val uri = Uri.parse(path)
        return when (uri.scheme) {
            null -> File(path)
            "file" -> if (uri.authority.isNullOrEmpty() && uri.query == null && uri.fragment == null && !uri.path.isNullOrBlank()) File(uri.path!!) else null
            else -> null
        }?.absoluteFile
    }
    private object EmptyPreferences : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any>()
        override fun contains(key: String) = false
        override fun getString(key: String, defValue: String?) = defValue
        override fun getInt(key: String, defValue: Int) = defValue
        override fun getLong(key: String, defValue: Long) = defValue
        override fun getFloat(key: String, defValue: Float) = defValue
        override fun getBoolean(key: String, defValue: Boolean) = defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?) = defValues
        override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException("Nur lesende Prüfeinstellungen.")
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun invalid(message: String = "Die Datenbank des Rückfallstands ist unvollständig oder ungültig. Der Bestand bleibt gesperrt."): Nothing =
        throw OfflineBackupValidationException(message)
}
