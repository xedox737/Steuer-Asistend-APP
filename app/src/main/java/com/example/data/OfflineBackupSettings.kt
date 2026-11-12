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

/** Device-only configuration. Never implies that a saved URI still has usable grants. */
internal class OfflineBackupSettings(context: Context) {
    data class Config(val targetUri: String? = null, val manualCredentials: Boolean = false) {
        init { targetUri?.let { value ->
            require(value.length <= 16 * 1024 && value.none(Char::isISOControl))
            val uri = Uri.parse(value)
            require(uri.scheme == "content" && !uri.authority.isNullOrBlank() && DocumentsContract.isTreeUri(uri))
        } }
    }
    private val directory = File(context.noBackupFilesDir, "offline-backup-state")
    private val file = File(directory, "settings.json")

    fun load(): Config = synchronized(lock) { guarded { read() } }
    fun setTarget(uri: Uri?): Config = update { it.copy(targetUri = uri?.toString()) }
    fun setManualCredentials(enabled: Boolean): Config = update { it.copy(manualCredentials = enabled) }

    private fun update(transform: (Config) -> Config): Config = synchronized(lock) { guarded {
        val next = transform(read())
        val buffer = ByteArrayOutputStream()
        JsonWriter(OutputStreamWriter(buffer, Charsets.UTF_8)).use { writer ->
            writer.beginObject().name("version").value(1L).name("targetUri")
            if (next.targetUri == null) writer.nullValue() else writer.value(next.targetUri)
            writer.name("manualCredentials").value(next.manualCredentials).endObject()
        }
        val bytes = buffer.toByteArray(); require(bytes.size <= MAX_BYTES)
        require(decode(bytes) == next)
        check(directory.isDirectory || directory.mkdirs())
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        var finished = false
        try {
            output.write(bytes); output.fd.sync(); atomic.finishWrite(output); finished = true
        } finally { if (!finished) atomic.failWrite(output) }
        require(read() == next) // Never cache an uncommitted in-memory configuration.
        next
    } }

    private fun read(): Config {
        val atomic = AtomicFile(file)
        val input = try { atomic.openRead() } catch (error: FileNotFoundException) {
            if (file.exists() || File(file.path + ".bak").exists()) throw error
            return Config()
        }
        return input.use { source ->
            val buffer = ByteArrayOutputStream(); val chunk = ByteArray(1024)
            while (true) {
                val count = source.read(chunk); if (count < 0) break
                require(count <= MAX_BYTES - buffer.size()); buffer.write(chunk, 0, count)
            }
            decode(buffer.toByteArray())
        }
    }
    private fun decode(bytes: ByteArray): Config {
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return JsonReader(InputStreamReader(ByteArrayInputStream(bytes), decoder)).use { reader ->
            reader.isLenient = false
            var target: String? = null; var manual = false
            val seen = hashSetOf<String>(); reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName(); require(seen.add(name))
                when (name) {
                    "version" -> { require(reader.peek() == JsonToken.NUMBER && reader.nextString() == "1") }
                    "targetUri" -> if (reader.peek() == JsonToken.NULL) reader.nextNull() else {
                        require(reader.peek() == JsonToken.STRING); target = reader.nextString()
                    }
                    "manualCredentials" -> { require(reader.peek() == JsonToken.BOOLEAN); manual = reader.nextBoolean() }
                    else -> throw IllegalArgumentException()
                }
            }
            reader.endObject(); require(reader.peek() == JsonToken.END_DOCUMENT && seen == setOf("version", "targetUri", "manualCredentials"))
            Config(target, manual)
        }
    }
    private inline fun <T> guarded(block: () -> T): T = try { block() }
        catch (_: IOException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: SecurityException) { invalid() }
    private fun invalid(): Nothing = throw OfflineBackupValidationException("Die lokalen Sicherungseinstellungen konnten nicht sicher gelesen oder gespeichert werden. Bitte den Ordner und die Einstellungen prüfen.")
    private companion object { val lock = Any(); const val MAX_BYTES = 64 * 1024 }
}
