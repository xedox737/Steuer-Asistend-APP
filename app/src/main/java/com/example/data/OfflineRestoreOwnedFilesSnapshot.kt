package com.example.data

import android.content.Context
import android.util.JsonWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Rollback copies of currently reachable app-owned files; not a complete rollback by itself. */
internal object OfflineRestoreOwnedFilesSnapshot {
    data class Version(val portablePath: String, val kind: OfflineBackupFileInventory.Kind,
        val stagedName: String, val bytes: Long, val sha256: String)
    data class Snapshot(val id: String, val directory: File, val versions: List<Version>,
        val manifestBytes: Long, val manifestSha256: String)
    private data class Fingerprint(val bytes: Long, val sha256: String)
    private const val MAX_MANIFEST = 64L * 1024 * 1024

    suspend fun capture(context: Context, token: OfflineBackupCoordinator.Maintenance, id: String): Snapshot {
        var owned: File? = null; var delivered = false
        try {
            val result = withContext(Dispatchers.IO) {
                OfflineBackupCoordinator.shared.requireMaintenance(token)
                require(UUID.fromString(id).toString() == id)
                token.suspending {
                    val cancellation = currentCoroutineContext()
                    val inventory = OfflineBackupFileInventory(context)
                    val sources = inventory.available()
                    var required = OfflineBackupResourceBudget.RESERVE_BYTES
                    val sourceSizes = sources.map { source ->
                        cancellation.ensureActive()
                        val bytes = source.source.length()
                        if (bytes < 0 || bytes > Long.MAX_VALUE - required) invalid("Der Speicherbedarf der Dateirücklage ist zu groß.")
                        required += bytes
                        bytes
                    }
                    val parent = File(context.noBackupFilesDir, "offline-backup-state")
                    if (!parent.isDirectory && !parent.mkdirs()) invalid("Der private Rückfallbereich ist nicht verfügbar.")
                    val available = parent.usableSpace
                    if (available < required) invalid("Zu wenig privater Speicher für die Dateirücklage: benötigt $required Byte, verfügbar $available Byte.")
                    val directory = File(parent, "rollback-$id.files")
                    if (!directory.mkdir()) invalid("Der private Rückfallplatz ist bereits belegt.")
                    owned = directory
                    val versions = sources.mapIndexed { index, source ->
                        cancellation.ensureActive()
                        val stagedName = "file-$index"
                        val target = File(directory, stagedName)
                        val maximum = sourceSizes[index]
                        if (source.source.length() != maximum) invalid("Eine Quelldatei hat sich seit der Speicherprüfung geändert.")
                        val copied = FileInputStream(source.source).use { input ->
                            FileOutputStream(target).use { output ->
                                val result = fingerprint(input, output, maximum)
                                output.fd.sync(); result
                            }
                        }
                        val stored = FileInputStream(target).use { fingerprint(it, null, maximum) }
                        val current = FileInputStream(source.source).use { fingerprint(it, null, maximum) }
                        if (copied.bytes != maximum || copied != stored || copied != current) invalid("Eine Quelldatei hat sich während der Rückfallsicherung geändert.")
                        Version(source.portablePath, source.kind, stagedName, copied.bytes, copied.sha256)
                    }
                    if (inventory.available().map { it.portablePath to it.kind } != sources.map { it.portablePath to it.kind })
                        invalid("Der Dateibestand hat sich während der Rückfallsicherung geändert.")
                    val manifest = File(directory, "manifest.json")
                    var manifestBytes = 0L
                    val digest = MessageDigest.getInstance("SHA-256")
                    FileOutputStream(manifest).use { stream ->
                        val output = object : OutputStream() {
                            override fun write(value: Int) { if (manifestBytes >= MAX_MANIFEST) invalid("Das Dateiverzeichnis der Rückfallsicherung ist zu groß."); stream.write(value); digest.update(value.toByte()); manifestBytes++ }
                            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                                if (length.toLong() > MAX_MANIFEST - manifestBytes) invalid("Das Dateiverzeichnis der Rückfallsicherung ist zu groß.")
                                stream.write(bytes, offset, length); digest.update(bytes, offset, length); manifestBytes += length
                            }
                            override fun flush() = stream.flush()
                        }
                        val encoder = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                        val writer = JsonWriter(OutputStreamWriter(output, encoder))
                        writer.beginObject().name("version").value(1L).name("id").value(id).name("files").beginArray()
                        versions.forEach { version ->
                            cancellation.ensureActive()
                            writer.beginObject().name("path").value(version.portablePath).name("kind").value(version.kind.name)
                                .name("file").value(version.stagedName).name("bytes").value(version.bytes).name("sha256").value(version.sha256).endObject()
                        }
                        writer.endArray().endObject(); writer.flush(); stream.fd.sync()
                    }
                    val expected = digest.digest()
                    val actual = FileInputStream(manifest).use { fingerprint(it, null, MAX_MANIFEST) }
                    val hash = expected.joinToString("") { "%02x".format(it.toInt() and 255) }
                    if (actual.bytes != manifestBytes || actual.sha256 != hash) invalid("Das Dateiverzeichnis konnte nicht vollständig rückgelesen werden.")
                    Snapshot(id, directory, versions, manifestBytes, hash)
                }
            }
            delivered = true
            return result
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid("Die Dateirücklage konnte nicht vollständig gesichert werden.") }
        catch (_: SecurityException) { invalid("Der Zugriff auf die Dateirücklage wurde verweigert.") }
        catch (_: IllegalArgumentException) { invalid("Der Dateibestand der Rücklage ist ungültig.") }
        catch (_: IllegalStateException) { invalid("Die Dateirücklage konnte nicht sicher geprüft werden.") }
        finally { if (!delivered) owned?.let { if (it.exists() && !it.deleteRecursively()) invalid("Unvollständige private Rückfalldateien konnten nicht entfernt werden.") } }
    }
    private suspend fun fingerprint(input: java.io.InputStream, output: OutputStream?, maximum: Long): Fingerprint {
        val cancellation = currentCoroutineContext(); val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192); var count = 0L
        while (true) {
            cancellation.ensureActive(); val size = input.read(buffer); if (size < 0) break; if (size == 0) continue
            if (size.toLong() > maximum - count) invalid("Eine Quelldatei ist während der Rückfallsicherung gewachsen.")
            output?.write(buffer, 0, size); digest.update(buffer, 0, size); count += size
        }
        return Fingerprint(count, digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
    }
    private fun invalid(message: String): Nothing = throw OfflineBackupValidationException(message)
}
