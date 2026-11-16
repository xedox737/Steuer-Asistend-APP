package com.example.data

import android.content.Context
import android.util.AtomicFile
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileInputStream
import java.security.MessageDigest
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.CodingErrorAction
import java.util.UUID

/** Device-only recovery marker. This class does not perform or certify a restore. */
internal class OfflineRestoreJournal(context: Context) {
    enum class Phase { PREPARED, FILES, DATABASE, PREFERENCES, ROLLBACK, COMMITTED, ROLLED_BACK }
    data class Entry(val operationId: String, val stagingId: String, val rollbackId: String, val phase: Phase,
        val rollbackManifestBytes: Long, val rollbackManifestSha256: String) {
        init {
            listOf(operationId, stagingId, rollbackId).forEach { require(UUID.fromString(it).toString() == it) }
            require(rollbackManifestBytes in 1..MAX_BUNDLE_BYTES && rollbackManifestSha256.matches(Regex("[0-9a-f]{64}")))
        }
    }
    private val directory = File(context.noBackupFilesDir, "offline-backup-state")
    private val file = File(directory, "restore-journal.json")

    fun load(): Entry? = synchronized(lock) { guarded { read() } }
    fun begin(operationId: String, stagingId: String, rollbackId: String,
        rollbackManifestBytes: Long, rollbackManifestSha256: String): Entry = synchronized(lock) { guarded {
        if (read() != null) invalid()
        val entry = Entry(operationId, stagingId, rollbackId, Phase.PREPARED, rollbackManifestBytes, rollbackManifestSha256)
        val bundle = File(directory, "rollback-${entry.rollbackId}.bundle.json")
        require(bundle.absoluteFile == bundle.canonicalFile && bundle.isFile && bundle.length() == rollbackManifestBytes)
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(bundle).use { input ->
            val buffer = ByteArray(1024); var total = 0L
            while (true) { val count = input.read(buffer); if (count < 0) break
                require(count.toLong() <= rollbackManifestBytes - total)
                digest.update(buffer, 0, count); total += count }
            require(total == rollbackManifestBytes)
        }
        require(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == rollbackManifestSha256)
        write(entry)
    } }
    fun advance(expected: Entry, next: Phase): Entry = synchronized(lock) { guarded {
        if (read() != expected || next !in allowed(expected.phase)) invalid()
        write(expected.copy(phase = next))
    } }
    fun clear(expected: Entry) = synchronized(lock) { guarded {
        if (expected.phase !in setOf(Phase.COMMITTED, Phase.ROLLED_BACK) || read() != expected) invalid()
        AtomicFile(file).delete()
        if (file.exists() || File(file.path + ".bak").exists() || File(file.path + ".new").exists()) invalid()
    } }
    private fun allowed(phase: Phase): Set<Phase> = when (phase) {
        Phase.PREPARED -> setOf(Phase.FILES, Phase.ROLLBACK)
        Phase.FILES -> setOf(Phase.DATABASE, Phase.ROLLBACK)
        Phase.DATABASE -> setOf(Phase.PREFERENCES, Phase.ROLLBACK)
        Phase.PREFERENCES -> setOf(Phase.COMMITTED, Phase.ROLLBACK)
        Phase.ROLLBACK -> setOf(Phase.ROLLED_BACK)
        Phase.COMMITTED, Phase.ROLLED_BACK -> emptySet()
    }
    private fun write(entry: Entry): Entry {
        val buffer = ByteArrayOutputStream()
        JsonWriter(OutputStreamWriter(buffer, Charsets.UTF_8)).use { writer ->
            writer.beginObject().name("version").value(2L).name("owner").value("local-full-restore")
                .name("operationId").value(entry.operationId).name("stagingId").value(entry.stagingId)
                .name("rollbackId").value(entry.rollbackId).name("phase").value(entry.phase.name)
                .name("rollbackManifestBytes").value(entry.rollbackManifestBytes)
                .name("rollbackManifestSha256").value(entry.rollbackManifestSha256).endObject()
        }
        val bytes = buffer.toByteArray(); require(bytes.size <= MAX_BYTES && decode(bytes) == entry)
        check(directory.isDirectory || directory.mkdirs())
        val atomic = AtomicFile(file); val output = atomic.startWrite(); var finished = false
        try { output.write(bytes); output.fd.sync(); atomic.finishWrite(output); finished = true }
        finally { if (!finished) atomic.failWrite(output) }
        if (read() != entry) invalid()
        return entry
    }
    private fun read(): Entry? {
        val atomic = AtomicFile(file)
        val input = try { atomic.openRead() } catch (error: FileNotFoundException) {
            if (file.exists() || File(file.path + ".bak").exists() || File(file.path + ".new").exists()) throw error
            return null
        }
        return input.use { source ->
            val buffer = ByteArrayOutputStream(); val chunk = ByteArray(1024)
            while (true) { val count = source.read(chunk); if (count < 0) break
                require(count <= MAX_BYTES - buffer.size()); buffer.write(chunk, 0, count) }
            decode(buffer.toByteArray())
        }
    }
    private fun decode(bytes: ByteArray): Entry {
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return JsonReader(InputStreamReader(ByteArrayInputStream(bytes), decoder)).use { reader ->
            reader.isLenient = false
            val values = mutableMapOf<String, String>(); reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName(); require(name !in values)
                val expected = if (name == "version" || name == "rollbackManifestBytes") JsonToken.NUMBER else JsonToken.STRING
                require(reader.peek() == expected); values[name] = reader.nextString()
            }
            reader.endObject(); require(reader.peek() == JsonToken.END_DOCUMENT)
            require(values.keys == setOf("version", "owner", "operationId", "stagingId", "rollbackId", "phase", "rollbackManifestBytes", "rollbackManifestSha256"))
            require(values["version"] == "2" && values["owner"] == "local-full-restore")
            val sizeText = values.getValue("rollbackManifestBytes")
            val size = sizeText.toLong(); require(size.toString() == sizeText)
            Entry(values.getValue("operationId"), values.getValue("stagingId"), values.getValue("rollbackId"),
                Phase.valueOf(values.getValue("phase")), size, values.getValue("rollbackManifestSha256"))
        }
    }
    private inline fun <T> guarded(block: () -> T): T = try { block() }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: SecurityException) { invalid() }
    private fun invalid(): Nothing = throw OfflineBackupValidationException("Das lokale Wiederherstellungsjournal ist ungültig oder nicht verfügbar. Der Bestand darf noch nicht geöffnet oder verändert werden.")
    private companion object { val lock = Any(); const val MAX_BYTES = 8192; const val MAX_BUNDLE_BYTES = 16L * 1024 }
}
