package com.example.data

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.CodingErrorAction
import java.util.UUID

/** Device-only operation history; it is not part of the portable user backup. */
internal class OfflineBackupHistory(context: Context) {
    enum class Kind { FULL, PARTIAL, RESTORE }
    enum class Outcome { STARTED, VERIFIED, FAILED, CANCELLED, INTERRUPTED, RESTORED, ROLLED_BACK }
    enum class Origin { MANUAL, AUTOMATIC }
    enum class Failure { UNAVAILABLE, VALIDATION, STORAGE, UNKNOWN }
    data class Event(val operationId: String, val kind: Kind, val outcome: Outcome, val timeMillis: Long,
        val bytes: Long? = null, val failure: Failure? = null, val origin: Origin? = null,
        val archiveId: String? = null, val targetUri: String? = null, val fileCount: Long? = null, val rowCount: Long? = null) {
        init {
            require(UUID.fromString(operationId).toString() == operationId && timeMillis > 0)
            require(if (kind == Kind.RESTORE) outcome in setOf(Outcome.STARTED, Outcome.RESTORED, Outcome.ROLLED_BACK, Outcome.FAILED, Outcome.CANCELLED)
                else outcome in setOf(Outcome.STARTED, Outcome.VERIFIED, Outcome.FAILED, Outcome.CANCELLED, Outcome.INTERRUPTED))
            require(if (outcome == Outcome.VERIFIED) bytes != null && bytes > 0 else bytes == null)
            require((outcome == Outcome.FAILED) == (failure != null))
            require((kind != Kind.RESTORE) == (origin != null))
            if (outcome == Outcome.VERIFIED) {
                require(UUID.fromString(requireNotNull(archiveId)).toString() == archiveId)
                val uri = Uri.parse(requireNotNull(targetUri)); require(targetUri.length <= 16 * 1024 && targetUri.none(Char::isISOControl) && uri.scheme == "content" && !uri.authority.isNullOrBlank())
                require(fileCount != null && fileCount >= 0 && rowCount != null && rowCount >= 0)
            } else require(archiveId == null && targetUri == null && fileCount == null && rowCount == null)
        }
    }
    data class History(val recent: List<Event> = emptyList(), val lastAttempt: Event? = null,
        val lastFullSuccess: Event? = null, val lastPartial: Event? = null, val lastRestore: Event? = null)

    private val directory = File(context.noBackupFilesDir, "offline-backup-state")
    private val file = File(directory, "history.json")
    fun load(): History = synchronized(lock) { guarded { read() } }

    /** A backup operation ID may begin only once, including after an interrupted process. */
    fun beginFullBackup(operationId: String, origin: Origin, timeMillis: Long): History = synchronized(lock) { guarded {
        val previous = read()
        val known = previous.recent + listOfNotNull(previous.lastAttempt, previous.lastFullSuccess, previous.lastPartial, previous.lastRestore)
        require(known.none { it.operationId == operationId && it.kind == Kind.FULL })
        record(Event(operationId, Kind.FULL, Outcome.STARTED, timeMillis, origin = origin))
    } }

    /** Same operation and outcome may be replayed exactly; conflicting replay fails closed. */
    fun record(event: Event): History = synchronized(lock) { guarded {
        val old = read()
        val related = (old.recent + listOfNotNull(old.lastAttempt, old.lastFullSuccess, old.lastPartial, old.lastRestore))
            .filter { it.operationId == event.operationId && it.kind == event.kind }
        val existing = related.filter { it.outcome == event.outcome }
        if (existing.isNotEmpty()) {
            require(existing.all { it.copy(timeMillis = event.timeMillis) == event })
            return@guarded old
        }
        require(related.none { it.outcome != Outcome.STARTED })
        val next = old.copy(recent = (old.recent + event).takeLast(MAX_EVENTS),
            lastAttempt = if (event.kind != Kind.RESTORE && (event.outcome == Outcome.STARTED || old.lastAttempt == null || old.lastAttempt.operationId == event.operationId)) event else old.lastAttempt,
            lastFullSuccess = if (event.kind == Kind.FULL && event.outcome == Outcome.VERIFIED) event else old.lastFullSuccess,
            lastPartial = if (event.kind == Kind.PARTIAL && event.outcome == Outcome.VERIFIED) event else old.lastPartial,
            lastRestore = if (event.kind == Kind.RESTORE) event else old.lastRestore)
        write(next); next
    } }

    private fun write(history: History) {
        val bytes = ByteArrayOutputStream().also { buffer ->
            JsonWriter(OutputStreamWriter(buffer, Charsets.UTF_8)).use { writer ->
                writer.beginObject().name("version").value(1L).name("recent").beginArray()
                history.recent.forEach { writeEvent(writer, it) }
                writer.endArray().name("lastAttempt"); writeNullable(writer, history.lastAttempt)
                writer.name("lastFullSuccess"); writeNullable(writer, history.lastFullSuccess)
                writer.name("lastPartial"); writeNullable(writer, history.lastPartial)
                writer.name("lastRestore"); writeNullable(writer, history.lastRestore)
                writer.endObject()
            }
        }.toByteArray()
        require(bytes.size <= MAX_BYTES && decode(bytes) == history)
        check(directory.isDirectory || directory.mkdirs())
        val atomic = AtomicFile(file); val output = atomic.startWrite(); var finished = false
        try { output.write(bytes); output.fd.sync(); atomic.finishWrite(output); finished = true }
        finally { if (!finished) atomic.failWrite(output) }
        require(read() == history)
    }
    private fun writeNullable(writer: JsonWriter, event: Event?) {
        if (event == null) writer.nullValue() else writeEvent(writer, event)
    }
    private fun writeEvent(writer: JsonWriter, event: Event) {
        writer.beginArray().value(event.operationId).value(event.kind.name).value(event.outcome.name).value(event.timeMillis)
        if (event.bytes == null) writer.nullValue() else writer.value(event.bytes)
        if (event.failure == null) writer.nullValue() else writer.value(event.failure.name)
        if (event.origin == null) writer.nullValue() else writer.value(event.origin.name)
        if (event.archiveId == null) writer.nullValue() else writer.value(event.archiveId)
        if (event.targetUri == null) writer.nullValue() else writer.value(event.targetUri)
        if (event.fileCount == null) writer.nullValue() else writer.value(event.fileCount)
        if (event.rowCount == null) writer.nullValue() else writer.value(event.rowCount)
        writer.endArray()
    }
    private fun read(): History {
        val input = try { AtomicFile(file).openRead() } catch (error: FileNotFoundException) {
            if (file.exists() || File(file.path + ".bak").exists() || File(file.path + ".new").exists()) throw error
            return History()
        }
        val bytes = input.use { source ->
            val out = ByteArrayOutputStream(); val chunk = ByteArray(1024)
            while (true) { val count = source.read(chunk); if (count < 0) break
                require(count <= MAX_BYTES - out.size()); out.write(chunk, 0, count) }
            out.toByteArray()
        }
        return decode(bytes)
    }
    private fun decode(bytes: ByteArray): History {
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return JsonReader(InputStreamReader(ByteArrayInputStream(bytes), decoder)).use { reader ->
            reader.isLenient = false
            fun name(value: String) { require(reader.hasNext() && reader.nextName() == value) }
            fun event(): Event {
                reader.beginArray()
                fun string(): String { require(reader.peek() == JsonToken.STRING); return reader.nextString() }
                fun number(): Long { require(reader.peek() == JsonToken.NUMBER); val raw = reader.nextString()
                    return raw.toLong().also { require(it.toString() == raw) } }
                val id = string(); val kind = Kind.valueOf(string()); val outcome = Outcome.valueOf(string()); val time = number()
                val count = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else number()
                val reason = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else Failure.valueOf(string())
                val origin = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else Origin.valueOf(string())
                val archiveId = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else string()
                val target = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else string()
                val files = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else number()
                val rows = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else number()
                require(!reader.hasNext()); reader.endArray(); return Event(id, kind, outcome, time, count, reason, origin, archiveId, target, files, rows)
            }
            fun optional(): Event? = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else event()
            reader.beginObject(); name("version"); require(reader.peek() == JsonToken.NUMBER && reader.nextString() == "1")
            name("recent"); val recent = mutableListOf<Event>(); reader.beginArray()
            while (reader.hasNext()) { require(recent.size < MAX_EVENTS); recent += event() }; reader.endArray()
            name("lastAttempt"); val attempt = optional(); name("lastFullSuccess"); val full = optional()
            name("lastPartial"); val partial = optional(); name("lastRestore"); val restore = optional()
            require(!reader.hasNext()); reader.endObject(); require(reader.peek() == JsonToken.END_DOCUMENT)
            require(attempt == null || attempt.kind != Kind.RESTORE)
            require(full == null || full.kind == Kind.FULL && full.outcome == Outcome.VERIFIED)
            require(partial == null || partial.kind == Kind.PARTIAL && partial.outcome == Outcome.VERIFIED)
            require(restore == null || restore.kind == Kind.RESTORE)
            require(recent.distinctBy { Triple(it.operationId, it.kind, it.outcome) }.size == recent.size)
            History(recent, attempt, full, partial, restore)
        }
    }
    private inline fun <T> guarded(block: () -> T): T = try { block() }
    catch (_: IOException) { invalid() }
    catch (_: IllegalArgumentException) { invalid() }
    catch (_: IllegalStateException) { invalid() }
    catch (_: SecurityException) { invalid() }
    private fun invalid(): Nothing = throw OfflineBackupValidationException("Der lokale Sicherungsverlauf konnte nicht sicher gelesen oder gespeichert werden.")
    private companion object { val lock = Any(); const val MAX_BYTES = 2 * 1024 * 1024; const val MAX_EVENTS = 100 }
}
