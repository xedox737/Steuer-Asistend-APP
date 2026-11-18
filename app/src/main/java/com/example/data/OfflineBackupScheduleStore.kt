package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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
import java.time.DayOfWeek
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/** Durable desired schedule and claims; it never registers or runs Android work itself. */
internal class OfflineBackupScheduleStore(context: Context) {
    data class Config(val enabled: Boolean, val rule: OfflineBackupSchedule.Rule,
        val targetUri: String?, val automaticCredentials: Boolean) {
        init {
            require(!enabled || targetUri != null)
            targetUri?.let { raw ->
                require(raw.length <= 16 * 1024 && raw.none(Char::isISOControl))
                val uri = Uri.parse(raw)
                require(uri.scheme == "content" && !uri.authority.isNullOrBlank() && DocumentsContract.isTreeUri(uri))
            }
        }
    }
    data class Occurrence(val id: String, val dueMillis: Long) {
        init { uuid(id); require(dueMillis > 0) }
    }
    data class Claim(val generation: String, val occurrence: Occurrence, val startedMillis: Long) {
        init { uuid(generation); require(startedMillis >= occurrence.dueMillis) }
    }
    data class State(val generation: String, val config: Config, val next: Occurrence?,
        val active: Claim?, val lastInterrupted: Claim?) {
        init { uuid(generation); require(config.enabled == (next != null)); require(active == null || active.occurrence.id != next?.id) }
    }
    enum class WorkState { WAITING, RUNNING, FINISHED, MISSING, UNKNOWN }
    enum class ClaimResult { CLAIMED, NOT_DUE, STALE, BUSY, ALREADY_CLAIMED }

    private val directory = File(context.noBackupFilesDir, "offline-backup-state")
    private val file = File(directory, "schedule.json")
    private val history = OfflineBackupHistory(context)
    fun load(): State? = synchronized(lock) { guarded { read() } }

    /** Persist a new generation before any registration; never cancels a claimed export. */
    fun configure(config: Config, now: Instant): State = synchronized(lock) { guarded {
        val previous = read()
        val next = if (config.enabled) Occurrence(id(), OfflineBackupSchedule.next(config.rule, now).toEpochMilli()) else null
        State(id(), config, next, previous?.active, previous?.lastInterrupted).also(::write)
    } }

    /** Persist the claim and following calendar occurrence together before starting export. */
    fun claim(generation: String, occurrenceId: String, now: Instant): ClaimResult = synchronized(lock) { guarded {
        uuid(generation); uuid(occurrenceId)
        val state = read() ?: return@guarded ClaimResult.STALE
        if (state.active?.occurrence?.id == occurrenceId) return@guarded ClaimResult.ALREADY_CLAIMED
        val due = state.next
        if (!state.config.enabled || state.generation != generation || due?.id != occurrenceId) return@guarded ClaimResult.STALE
        val millis = now.toEpochMilli()
        if (millis < due.dueMillis) return@guarded ClaimResult.NOT_DUE
        if (state.active != null) return@guarded ClaimResult.BUSY
        val following = Occurrence(id(), OfflineBackupSchedule.next(state.config.rule, now).toEpochMilli())
        write(state.copy(next = following, active = Claim(generation, due, millis)))
        ClaimResult.CLAIMED
    } }

    /** Only the exact claimed occurrence can finish; newer schedule generations are retained. */
    fun finish(occurrenceId: String): Boolean = synchronized(lock) { guarded {
        uuid(occurrenceId); val state = read() ?: return@guarded false
        if (state.active?.occurrence?.id != occurrenceId) return@guarded false
        write(state.copy(active = null)); true
    } }

    /** Call only with an authoritative WorkManager observation for the persisted active ID. */
    fun reconcileStoppedClaim(occurrenceId: String, observed: WorkState): Boolean = synchronized(lock) { guarded {
        uuid(occurrenceId); val state = read() ?: return@guarded false
        val active = state.active ?: return@guarded false
        if (active.occurrence.id != occurrenceId || observed !in setOf(WorkState.FINISHED, WorkState.MISSING)) return@guarded false
        // A verified archive may already be durable even if the process died before finish().
        val recorded = history.load()
        val verified = recorded.recent.any { it.operationId == occurrenceId && it.kind == OfflineBackupHistory.Kind.FULL && it.outcome == OfflineBackupHistory.Outcome.VERIFIED } ||
            recorded.lastFullSuccess?.let { it.operationId == occurrenceId && it.outcome == OfflineBackupHistory.Outcome.VERIFIED } == true
        write(state.copy(active = null, lastInterrupted = if (verified) state.lastInterrupted else active)); true
    } }

    /** KEEP applies only to this one persisted occurrence, never to a chain or a running predecessor. */
    fun needsRegistration(state: State?, observed: WorkState): Boolean =
        state != null && state.config.enabled && state.next != null && observed in setOf(WorkState.FINISHED, WorkState.MISSING)

    private fun write(state: State) {
        val buffer = ByteArrayOutputStream()
        JsonWriter(OutputStreamWriter(buffer, Charsets.UTF_8)).use { writer ->
            writer.beginArray().value(1L).value(state.generation).value(state.config.enabled)
                .value(state.config.rule.cadence.name).value(state.config.rule.time.hour.toLong() * 60 + state.config.rule.time.minute)
            if (state.config.rule.day == null) writer.nullValue() else writer.value(state.config.rule.day.name)
            writer.value(state.config.rule.zone.id)
            if (state.config.targetUri == null) writer.nullValue() else writer.value(state.config.targetUri)
            writer.value(state.config.automaticCredentials)
            writeOccurrence(writer, state.next); writeClaim(writer, state.active); writeClaim(writer, state.lastInterrupted)
            writer.endArray()
        }
        val bytes = buffer.toByteArray(); require(bytes.size <= MAX_BYTES && decode(bytes) == state)
        check(directory.isDirectory || directory.mkdirs())
        val atomic = AtomicFile(file); val output = atomic.startWrite(); var done = false
        try { output.write(bytes); output.fd.sync(); atomic.finishWrite(output); done = true }
        finally { if (!done) atomic.failWrite(output) }
        require(read() == state)
    }
    private fun writeOccurrence(writer: JsonWriter, value: Occurrence?) {
        if (value == null) writer.nullValue() else writer.beginArray().value(value.id).value(value.dueMillis).endArray()
    }
    private fun writeClaim(writer: JsonWriter, value: Claim?) {
        if (value == null) writer.nullValue() else {
            writer.beginArray().value(value.generation); writeOccurrence(writer, value.occurrence)
            writer.value(value.startedMillis).endArray()
        }
    }
    private fun read(): State? {
        val input = try { AtomicFile(file).openRead() } catch (error: FileNotFoundException) {
            if (file.exists() || File(file.path + ".bak").exists() || File(file.path + ".new").exists()) throw error
            return null
        }
        val bytes = input.use { source -> val out = ByteArrayOutputStream(); val chunk = ByteArray(1024)
            while (true) { val count = source.read(chunk); if (count < 0) break
                require(count <= MAX_BYTES - out.size()); out.write(chunk, 0, count) }; out.toByteArray() }
        return decode(bytes)
    }
    private fun decode(bytes: ByteArray): State {
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return JsonReader(InputStreamReader(ByteArrayInputStream(bytes), decoder)).use { reader ->
            reader.isLenient = false; reader.beginArray()
            fun text(): String { require(reader.peek() == JsonToken.STRING); return reader.nextString().also { require(it.length <= 16 * 1024) } }
            fun number(): Long { require(reader.peek() == JsonToken.NUMBER); val raw = reader.nextString()
                return raw.toLong().also { require(it.toString() == raw) } }
            fun boolean(): Boolean { require(reader.peek() == JsonToken.BOOLEAN); return reader.nextBoolean() }
            fun nullableText(): String? = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else text()
            fun occurrence(): Occurrence? {
                if (reader.peek() == JsonToken.NULL) { reader.nextNull(); return null }
                reader.beginArray(); val value = Occurrence(text(), number()); require(!reader.hasNext()); reader.endArray(); return value
            }
            fun claim(): Claim? {
                if (reader.peek() == JsonToken.NULL) { reader.nextNull(); return null }
                reader.beginArray(); val generation = text(); val occurrence = requireNotNull(occurrence()); val started = number()
                require(!reader.hasNext()); reader.endArray(); return Claim(generation, occurrence, started)
            }
            require(number() == 1L); val generation = text(); val enabled = boolean()
            val cadence = OfflineBackupSchedule.Cadence.valueOf(text()); val minutes = number(); require(minutes in 0..1439)
            val day = nullableText()?.let(DayOfWeek::valueOf); val zone = ZoneId.of(text()); val target = nullableText()
            val credentials = boolean(); val next = occurrence(); val active = claim(); val interrupted = claim()
            require(!reader.hasNext()); reader.endArray(); require(reader.peek() == JsonToken.END_DOCUMENT)
            State(generation, Config(enabled, OfflineBackupSchedule.Rule(cadence, LocalTime.of((minutes / 60).toInt(), (minutes % 60).toInt()), day, zone), target, credentials), next, active, interrupted)
        }
    }
    private inline fun <T> guarded(block: () -> T): T = try { block() }
    catch (_: IOException) { invalid() }
    catch (_: DateTimeException) { invalid() }
    catch (_: IllegalArgumentException) { invalid() }
    catch (_: IllegalStateException) { invalid() }
    catch (_: SecurityException) { invalid() }
    private fun invalid(): Nothing = throw OfflineBackupValidationException("Der lokale Sicherungszeitplan konnte nicht sicher gelesen oder gespeichert werden.")
    private companion object {
        val lock = Any(); const val MAX_BYTES = 128 * 1024
        fun uuid(value: String) { require(UUID.fromString(value).toString() == value) }
        fun id() = UUID.randomUUID().toString()
    }
}
