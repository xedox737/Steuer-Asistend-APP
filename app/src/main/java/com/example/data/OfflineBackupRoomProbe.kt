package com.example.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.sqlite.SQLiteException
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.StringReader
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Owns an isolated real Room database. Never opens or applies to the live database/preferences. */
internal object OfflineBackupRoomProbe {
    class Prepared internal constructor(val database: AppDatabase, private val directory: File,
        val manifest: OfflineBackupManifest.Checked, val preferences: JSONObject) : AutoCloseable {
        override fun close() {
            try { database.close() } finally {
                if (directory.exists() && !directory.deleteRecursively()) invalid("Die private Prüfdatenbank konnte nicht entfernt werden.")
            }
        }
    }
    private data class Column(val name: String, val type: String, val primary: Int, val notNull: Boolean)

    suspend fun prepare(context: Context, contents: OfflineBackupArchiveValidator.StagedContents,
        generation: OfflineBackupKeyEnvelope.Generation? = null): Prepared {
        var owned: File? = null; var database: AppDatabase? = null; var delivered = false
        try {
            val prepared = withContext(Dispatchers.IO) {
                verifyStaging(contents)
                val manifest = OfflineBackupManifest.verify(contents, generation)
                val metadata = requireNotNull(contents.parts.getValue("metadata.json").file)
                val runtime = Runtime.getRuntime()
                if (metadata.length() > (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory() - 32 * 1024 * 1024) / 16)
                    invalid("Für die sichere Datenbankprüfung ist nicht genügend Arbeitsspeicher verfügbar.")
                val parent = requireNotNull(metadata.parentFile)
                if (parent.usableSpace < metadata.length() * 8 + 8 * 1024 * 1024)
                    invalid("Für die private Datenbankprüfung ist nicht genügend Speicherplatz verfügbar.")
                val directory = File(parent, "probe-${UUID.randomUUID()}")
                check(directory.mkdir()); owned = directory
                val probe = Room.databaseBuilder(context, AppDatabase::class.java, File(directory, "database.db").absolutePath).build()
                database = probe
                val db = probe.openHelper.writableDatabase
                val cancellation = currentCoroutineContext()
                val schema = schema(db)
                require(schema.keys == manifest.rowCounts.keys)
                db.execSQL("PRAGMA foreign_keys=ON")
                val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                val parser = JsonReader(InputStreamReader(metadata.inputStream(), decoder)).use { reader ->
                    reader.isLenient = false
                    val parser = Parser(reader, db, schema, contents, manifest, cancellation)
                    probe.withTransaction {
                        db.execSQL("PRAGMA defer_foreign_keys=ON")
                        require(db.query("PRAGMA defer_foreign_keys").use { it.moveToFirst() && it.getInt(0) == 1 })
                        parser.read()
                    }
                    parser
                }
                val readContext = PreferenceContext(context, parser.preferenceValues)
                PersistentPreferenceInventory.validatePayload(parser.preferences)
                val filtered = JSONObject().also { PersistentPreferenceInventory.writePayload(readContext, it) }
                require(filtered.keys().asSequence().toSet() == parser.preferences.keys().asSequence().toSet())
                filtered.keys().forEach { name ->
                    require(filtered.getJSONObject(name).keys().asSequence().toSet() == parser.preferences.getJSONObject(name).keys().asSequence().toSet())
                }
                db.query("PRAGMA integrity_check").use { require(it.moveToFirst() && it.getString(0) == "ok" && !it.moveToNext()) }
                db.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) }
                manifest.rowCounts.forEach { (table, count) ->
                    cancellation.ensureActive()
                    db.query("SELECT COUNT(*) FROM `$table`").use { require(it.moveToFirst() && it.getLong(0) == count) }
                }
                val sequences = db.query("SELECT name, seq FROM sqlite_sequence").use { cursor -> buildMap {
                    while (cursor.moveToNext()) if (cursor.getString(0) in schema) put(cursor.getString(0), cursor.getLong(1))
                } }
                require(sequences == parser.sequences)
                val checkFile = File(directory, "metadata-check.json")
                try {
                    val checked = OfflineBackupMetadataWriter(readContext, probe).write(checkFile)
                    val privatePaths = contents.parts.values.mapNotNull { it.file?.canonicalPath }.toSet()
                    require(checked.rowCounts == manifest.rowCounts && checked.references.all { it.path in privatePaths })
                } finally { checkFile.delete() }
                OfflineBackupService(readContext, probe).validateRelationships(readContext.archivedPreferences("wohneinheiten_prefs"))
                cancellation.ensureActive()
                Prepared(probe, directory, manifest, parser.preferences)
            }
            delivered = true
            return prepared
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: SQLiteException) { invalid() }
        catch (_: org.json.JSONException) { invalid() }
        catch (error: IllegalArgumentException) {
            // Preserve the failing check for diagnostics without retaining archive contents.
            val safeCause = IllegalArgumentException("Isolated Room verification check failed")
            safeCause.stackTrace = error.stackTrace
            throw OfflineBackupValidationException(
                "Die isolierte Datenbankprüfung ist fehlgeschlagen. Es wurden keine Daten übernommen."
            ).apply { initCause(safeCause) }
        }
        catch (_: IllegalStateException) { invalid() }
        catch (_: NoSuchElementException) { invalid() }
        catch (_: SecurityException) { invalid() }
        finally {
            if (!delivered) try { database?.close() } finally {
                owned?.let { if (it.exists() && !it.deleteRecursively()) invalid("Die fehlgeschlagene private Prüfdatenbank konnte nicht entfernt werden.") }
            }
        }
    }

    private suspend fun verifyStaging(contents: OfflineBackupArchiveValidator.StagedContents) {
        val cancellation = currentCoroutineContext()
        contents.parts.values.forEach { part ->
            cancellation.ensureActive()
            val digest = MessageDigest.getInstance("SHA-256")
            var bytes = 0L
            if (part.file != null) {
                part.file.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        cancellation.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(count.toLong() <= part.bytes - bytes)
                        digest.update(buffer, 0, count); bytes += count
                    }
                }
            } else {
                val memory = requireNotNull(part.inMemory)
                digest.update(memory); bytes = memory.size.toLong()
            }
            val sha = digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            require(bytes == part.bytes && sha == part.sha256)
        }
    }

    private fun schema(db: SupportSQLiteDatabase): Map<String, List<Column>> {
        val tables = db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor -> buildSet {
            while (cursor.moveToNext()) add(cursor.getString(0))
        } }.filterNot { it.startsWith("sqlite_") || it.startsWith("document_search_fts") || it in OfflineBackupMetadataWriter.SYSTEM_TABLES }.toSet()
        require(tables == OfflineBackupMetadataWriter.BUSINESS_TABLES + OfflineBackupMetadataWriter.LOCAL_TABLES)
        return OfflineBackupMetadataWriter.BUSINESS_TABLES.associateWith { table ->
            db.query("PRAGMA table_info(`$table`)").use { cursor -> buildList {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1); require(Regex("[A-Za-z_][A-Za-z0-9_]*").matches(name))
                    add(Column(name, cursor.getString(2), cursor.getInt(5), cursor.getInt(3) != 0))
                }
            } }
        }
    }

    private class Parser(private val reader: JsonReader, private val db: SupportSQLiteDatabase,
        private val schema: Map<String, List<Column>>, private val contents: OfflineBackupArchiveValidator.StagedContents,
        private val manifest: OfflineBackupManifest.Checked, private val cancellation: CoroutineContext) {
        val preferences = JSONObject()
        val preferenceValues = linkedMapOf<String, Map<String, Any>>()
        val sequences = linkedMapOf<String, Long>()
        private val usedAliases = hashSetOf<String>()
        private fun name(expected: String) { cancellation.ensureActive(); require(reader.nextName() == expected) }
        private fun text(): String { require(reader.peek() == JsonToken.STRING); return reader.nextString() }
        private fun integer(): Long {
            require(reader.peek() == JsonToken.NUMBER)
            val raw = reader.nextString(); require(Regex("-?(0|[1-9][0-9]{0,18})").matches(raw))
            return requireNotNull(raw.toLongOrNull())
        }
        private fun real(): Double {
            require(reader.peek() == JsonToken.NUMBER)
            return reader.nextDouble().also { require(it.isFinite()) }
        }
        private fun boolean(): Boolean { require(reader.peek() == JsonToken.BOOLEAN); return reader.nextBoolean() }
        fun read() {
            reader.beginObject(); name("format"); require(text() == "ImmoPilotOfflineBackup")
            name("version"); require(integer() == 2L)
            name("databaseVersion"); require(integer() == db.version.toLong())
            name("tables"); reader.beginObject(); val seen = hashSetOf<String>()
            while (reader.hasNext()) {
                cancellation.ensureActive(); val table = reader.nextName()
                require(table in schema && seen.add(table)); table(table, schema.getValue(table))
            }
            reader.endObject(); require(seen == schema.keys && usedAliases == manifest.aliases.keys)
            name("preferences"); readPreferences()
            name("sequences"); reader.beginObject()
            while (reader.hasNext()) {
                cancellation.ensureActive(); val table = reader.nextName()
                require(table in schema && table !in sequences)
                db.query("SELECT sql FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use {
                    require(it.moveToFirst() && Regex("(?i)\\bAUTOINCREMENT\\b").containsMatchIn(it.getString(0)))
                }
                sequences[table] = integer().also { require(it >= 0) }
            }
            reader.endObject(); require(!reader.hasNext()); reader.endObject(); require(reader.peek() == JsonToken.END_DOCUMENT)
            db.execSQL("DELETE FROM sqlite_sequence")
            sequences.forEach { (table, value) -> db.execSQL("INSERT INTO sqlite_sequence(name,seq) VALUES(?,?)", arrayOf(table, value)) }
        }
        private fun table(table: String, columns: List<Column>) {
            reader.beginObject(); name("columns"); reader.beginArray()
            columns.forEach { column ->
                require(reader.hasNext()); reader.beginObject()
                name("name"); require(text() == column.name); name("type"); require(text() == column.type)
                name("primary"); require(integer() == column.primary.toLong()); name("notNull"); require(boolean() == column.notNull)
                require(!reader.hasNext()); reader.endObject()
            }
            require(!reader.hasNext()); reader.endArray(); name("rows"); reader.beginArray(); var count = 0L
            val sql = "INSERT INTO `$table` (${columns.joinToString(",") { "`${it.name}`" }}) VALUES (${columns.joinToString(",") { "?" }})"
            db.compileStatement(sql).use { statement ->
                while (reader.hasNext()) {
                    cancellation.ensureActive(); require(count < manifest.rowCounts.getValue(table)); statement.clearBindings(); reader.beginArray()
                    columns.forEachIndexed { index, column ->
                        require(reader.hasNext())
                        val value = cell(column, table)
                        when (value) {
                            null -> statement.bindNull(index + 1)
                            is Long -> statement.bindLong(index + 1, value)
                            is Double -> statement.bindDouble(index + 1, value)
                            is String -> statement.bindString(index + 1, value)
                            is ByteArray -> statement.bindBlob(index + 1, value)
                        }
                    }
                    require(!reader.hasNext()); reader.endArray(); statement.executeInsert(); count++
                }
            }
            require(count == manifest.rowCounts.getValue(table)); reader.endArray(); require(!reader.hasNext()); reader.endObject()
        }
        private fun cell(column: Column, table: String): Any? {
            if (reader.peek() == JsonToken.NULL) {
                require(!column.notNull && column.primary == 0); reader.nextNull(); return null
            }
            return when (column.type.uppercase()) {
                "INTEGER" -> integer()
                "REAL" -> real()
                "BLOB" -> {
                    reader.beginObject(); name("blob"); val value = Base64.decode(text(), Base64.NO_WRAP)
                    require(!reader.hasNext()); reader.endObject(); value
                }
                "TEXT" -> text().let { raw ->
                    if (column.name !in OfflineBackupMetadataWriter.PATH_COLUMNS || raw.isBlank()) raw
                    else original(raw, column.name == "localUri" || (table == "property_metadata" && column.name == "bildPfad"))
                }
                else -> throw IllegalArgumentException()
            }
        }
        private fun original(raw: String, direct: Boolean): String {
            fun resolve(alias: String): String {
                val entry = manifest.aliases.getValue(alias); usedAliases += alias
                return requireNotNull(contents.parts.getValue(entry).file).canonicalPath
            }
            if (direct || !raw.startsWith("[")) return resolve(raw)
            val paths = mutableListOf<String>()
            JsonReader(StringReader(raw)).use { nested ->
                nested.beginArray()
                while (nested.hasNext()) { require(paths.size < 10_000 && nested.peek() == JsonToken.STRING); paths += resolve(nested.nextString()) }
                nested.endArray(); require(paths.isNotEmpty() && nested.peek() == JsonToken.END_DOCUMENT)
            }
            return JSONArray(paths).toString()
        }
        private fun readPreferences() {
            val stores = PersistentPreferenceInventory.stores.filter { it.kind == PersistentPreferenceInventory.Kind.BACKUP }
                .associateBy { requireNotNull(it.payloadKey) }
            reader.beginObject(); val seen = hashSetOf<String>()
            while (reader.hasNext()) {
                cancellation.ensureActive(); val key = reader.nextName(); require(key in stores && seen.add(key))
                val encoded = JSONObject(); val values = linkedMapOf<String, Any>()
                reader.beginObject()
                while (reader.hasNext()) {
                    cancellation.ensureActive(); val pref = reader.nextName(); require(pref !in values)
                    reader.beginObject(); name("type"); val type = text(); name("value")
                    val value: Any = when (type) {
                        "string" -> text(); "int" -> integer().also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt()
                        "long" -> integer(); "boolean" -> boolean()
                        "float" -> real().let { require(it.toFloat().isFinite() && it.toFloat().toDouble() == it); it.toFloat() }
                        "stringSet" -> {
                            val set = linkedSetOf<String>(); reader.beginArray()
                            while (reader.hasNext()) { cancellation.ensureActive(); require(set.add(text())) }; reader.endArray(); set
                        }
                        else -> throw IllegalArgumentException()
                    }
                    require(!reader.hasNext()); reader.endObject(); values[pref] = value
                    encoded.put(pref, JSONObject().put("type", type).put("value", when (value) {
                        is Set<*> -> JSONArray(value.toList()); is Float -> value.toDouble(); else -> value
                    }))
                }
                reader.endObject(); preferenceValues[stores.getValue(key).name] = values; preferences.put(key, encoded)
            }
            reader.endObject(); require(seen == stores.keys)
        }
    }

    private class PreferenceContext(base: Context, private val values: Map<String, Map<String, Any>>) : ContextWrapper(base) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = archivedPreferences(name)
        fun archivedPreferences(name: String): SharedPreferences = ReadOnlyPreferences(values[name] ?: emptyMap())
    }
    private class ReadOnlyPreferences(private val values: Map<String, Any>) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun contains(key: String) = key in values
        override fun getString(key: String, defValue: String?): String? = if (key in values) values[key] as String else defValue
        override fun getInt(key: String, defValue: Int) = if (key in values) values[key] as Int else defValue
        override fun getLong(key: String, defValue: Long) = if (key in values) values[key] as Long else defValue
        override fun getFloat(key: String, defValue: Float) = if (key in values) values[key] as Float else defValue
        override fun getBoolean(key: String, defValue: Boolean) = if (key in values) values[key] as Boolean else defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            if (key in values) (values[key] as Set<String>).toMutableSet() else defValues
        override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException("Nur lesende Prüfeinstellungen.")
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }
    private fun invalid(message: String = "Die isolierte Datenbankprüfung ist fehlgeschlagen. Es wurden keine Daten übernommen."): Nothing =
        throw OfflineBackupValidationException(message)
}
