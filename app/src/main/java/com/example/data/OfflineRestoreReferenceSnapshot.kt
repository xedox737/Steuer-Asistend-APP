package com.example.data

import android.content.Context
import android.net.Uri
import android.util.JsonWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Fixes referenced external files and marks already unavailable references for rollback. */
internal object OfflineRestoreReferenceSnapshot {
    enum class State { OWNED, EXTERNAL, UNAVAILABLE }
    data class Reference(val key: String, val path: String, val owner: String, val state: State,
        val stagedName: String?, val bytes: Long?, val sha256: String?)
    data class Snapshot(val id: String, val directory: File, val references: List<Reference>,
        val manifestBytes: Long, val manifestSha256: String)
    private data class Fingerprint(val bytes: Long, val sha256: String)
    private const val CONTENT_BUDGET = 1024L * 1024 * 1024
    private const val MAX_MANIFEST = 64L * 1024 * 1024

    suspend fun capture(context: Context, database: AppDatabase, token: OfflineBackupCoordinator.Maintenance,
        id: String, ownedFiles: OfflineRestoreOwnedFilesSnapshot.Snapshot): Snapshot {
        var owned: File? = null; var delivered = false
        try {
            val result = withContext(Dispatchers.IO) {
                OfflineBackupCoordinator.shared.requireMaintenance(token)
                require(UUID.fromString(id).toString() == id && ownedFiles.id == id)
                token.suspending {
                    val cancellation = currentCoroutineContext()
                    val parent = File(context.noBackupFilesDir, "offline-backup-state")
                    val expectedOwned = File(parent, "rollback-$id.files")
                    require(expectedOwned.canonicalFile == ownedFiles.directory.canonicalFile && expectedOwned.isDirectory)
                    val ownManifest = File(expectedOwned, "manifest.json")
                    val manifestFingerprint = FileInputStream(ownManifest).use { fingerprint(it, null, MAX_MANIFEST) }
                    require(manifestFingerprint.bytes == ownedFiles.manifestBytes && manifestFingerprint.sha256 == ownedFiles.manifestSha256)
                    val inventory = OfflineBackupFileInventory(context)
                    val sources = inventory.available()
                    require(sources.map { it.portablePath to it.kind } == ownedFiles.versions.map { it.portablePath to it.kind })
                    val ownedByPath = sources.zip(ownedFiles.versions).associate { (source, version) ->
                        cancellation.ensureActive()
                        require(version.stagedName.matches(Regex("file-[0-9]+")))
                        val fixed = File(expectedOwned, version.stagedName)
                        val actualSource = FileInputStream(source.source).use { fingerprint(it, null, version.bytes) }
                        val actualFixed = FileInputStream(fixed).use { fingerprint(it, null, version.bytes) }
                        require(actualSource == Fingerprint(version.bytes, version.sha256) && actualFixed == actualSource)
                        source.source.canonicalPath to version
                    }
                    val directory = File(parent, "rollback-$id.references")
                    if (!directory.mkdir()) invalid("Der private Rückfallplatz ist bereits belegt.")
                    owned = directory
                    val draftFile = File(directory, "draft.json")
                    val draft = OfflineBackupMetadataWriter(context, database, referenceMode = OfflineBackupMetadataWriter.ReferenceMode.ROLLBACK_INVENTORY).write(draftFile)
                    val pending = draft.references.map { reference ->
                        val local = localFile(reference.path)
                        val prior = local?.canonicalPath?.let(ownedByPath::get)
                        Triple(reference, local, prior)
                    }
                    var required = OfflineBackupResourceBudget.RESERVE_BYTES
                    val localPresent = pending.map { (_, local, _) -> local?.exists() }
                    val budgets = pending.mapIndexed { index, (reference, local, prior) ->
                        cancellation.ensureActive()
                        val amount = when {
                            prior != null -> 0L
                            local != null && localPresent[index] == true -> local.length()
                            Uri.parse(reference.path).scheme == "content" -> CONTENT_BUDGET
                            else -> 0L
                        }
                        if (amount < 0 || amount > Long.MAX_VALUE - required) invalid("Der Speicherbedarf der Originalreferenzen ist zu groß.")
                        required += amount
                        amount
                    }
                    val available = parent.usableSpace
                    if (available < required) invalid("Zu wenig privater Speicher für externe Originalreferenzen: benötigt $required Byte, verfügbar $available Byte.")
                    var externalIndex = 0
                    val references = pending.mapIndexed { index, (reference, local, prior) ->
                        cancellation.ensureActive()
                        when {
                            prior != null -> Reference(reference.key, reference.path, reference.owner, State.OWNED, prior.stagedName, prior.bytes, prior.sha256)
                            else -> {
                                val maximum = budgets[index]
                                if (local != null && (local.exists() != localPresent[index] ||
                                        (localPresent[index] == true && local.length() != maximum)))
                                    invalid("Eine externe Originaldatei hat sich seit der Speicherprüfung geändert.")
                                val first = open(context, reference.path, local)
                                if (first == null) Reference(reference.key, reference.path, reference.owner, State.UNAVAILABLE, null, null, null)
                                else {
                                    val stagedName = "external-${externalIndex++}"
                                    val target = File(directory, stagedName)
                                    val copied = first.use { input -> FileOutputStream(target).use { output ->
                                        val value = fingerprint(input, output, maximum); output.fd.sync(); value
                                    } }
                                    val stored = FileInputStream(target).use { fingerprint(it, null, maximum) }
                                    val current = open(context, reference.path, local)?.use { fingerprint(it, null, maximum) }
                                        ?: invalid("Eine externe Originalreferenz wurde während der Rückfallsicherung unzugänglich.")
                                    if (copied != stored || copied != current || (local != null && copied.bytes != maximum))
                                        invalid("Eine externe Originaldatei hat sich während der Rückfallsicherung geändert.")
                                    Reference(reference.key, reference.path, reference.owner, State.EXTERNAL, stagedName, copied.bytes, copied.sha256)
                                }
                            }
                        }
                    }
                    if (inventory.available().map { it.portablePath to it.kind } != sources.map { it.portablePath to it.kind })
                        invalid("Der app-eigene Dateibestand hat sich während der Rückfallsicherung geändert.")
                    if (!draftFile.delete()) invalid("Die private Metadaten-Zwischenkopie konnte nicht entfernt werden.")
                    val manifest = File(directory, "manifest.json"); var manifestBytes = 0L
                    val digest = MessageDigest.getInstance("SHA-256")
                    FileOutputStream(manifest).use { stream ->
                        val output = object : OutputStream() {
                            override fun write(value: Int) { if (manifestBytes >= MAX_MANIFEST) invalid("Das Referenzverzeichnis ist zu groß."); stream.write(value); digest.update(value.toByte()); manifestBytes++ }
                            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                                if (length.toLong() > MAX_MANIFEST - manifestBytes) invalid("Das Referenzverzeichnis ist zu groß.")
                                stream.write(bytes, offset, length); digest.update(bytes, offset, length); manifestBytes += length
                            }
                            override fun flush() = stream.flush()
                        }
                        val encoder = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                        val writer = JsonWriter(OutputStreamWriter(output, encoder))
                        writer.beginObject().name("version").value(1L).name("id").value(id).name("references").beginArray()
                        references.forEach { reference ->
                            cancellation.ensureActive()
                            val pathHash = hex(MessageDigest.getInstance("SHA-256").digest(reference.path.toByteArray(Charsets.UTF_8)))
                            writer.beginObject().name("key").value(reference.key).name("pathSha256").value(pathHash)
                                .name("owner").value(reference.owner).name("state").value(reference.state.name).name("file")
                            if (reference.stagedName == null) writer.nullValue() else writer.value(reference.stagedName)
                            writer.name("bytes"); if (reference.bytes == null) writer.nullValue() else writer.value(reference.bytes)
                            writer.name("sha256"); if (reference.sha256 == null) writer.nullValue() else writer.value(reference.sha256)
                            writer.endObject()
                        }
                        writer.endArray().endObject(); writer.flush(); stream.fd.sync()
                    }
                    val hash = hex(digest.digest())
                    val reread = FileInputStream(manifest).use { fingerprint(it, null, MAX_MANIFEST) }
                    if (reread.bytes != manifestBytes || reread.sha256 != hash) invalid("Das Referenzverzeichnis konnte nicht vollständig rückgelesen werden.")
                    Snapshot(id, directory, references, manifestBytes, hash)
                }
            }
            delivered = true
            return result
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid("Die Originalreferenzen konnten nicht vollständig geprüft werden.") }
        catch (_: SecurityException) { invalid("Der Zugriff auf die Originalreferenzen wurde verweigert.") }
        catch (_: IllegalArgumentException) { invalid("Die Originalreferenzen der Rücklage sind ungültig.") }
        catch (_: IllegalStateException) { invalid("Die Originalreferenzen konnten nicht sicher geprüft werden.") }
        finally { if (!delivered) owned?.let { if (it.exists() && !it.deleteRecursively()) invalid("Unvollständige private Referenzdaten konnten nicht entfernt werden.") } }
    }
    private fun localFile(path: String): File? {
        val uri = Uri.parse(path)
        val file = when (uri.scheme) {
            null -> File(path)
            "file" -> if (uri.authority.isNullOrEmpty() && uri.query == null && uri.fragment == null && !uri.path.isNullOrBlank()) File(uri.path!!) else null
            else -> null
        } ?: return null
        if (file.absoluteFile != file.canonicalFile) invalid("Ein Originalverweis ist kein sicher zuordenbarer Dateipfad.")
        return file
    }
    private fun open(context: Context, path: String, local: File?): InputStream? = try {
        when {
            local != null -> FileInputStream(local)
            Uri.parse(path).scheme == "content" -> context.contentResolver.openInputStream(Uri.parse(path))
            else -> null
        }
    } catch (_: FileNotFoundException) { null } catch (_: SecurityException) { null }
    private suspend fun fingerprint(input: InputStream, output: OutputStream?, maximum: Long): Fingerprint {
        val cancellation = currentCoroutineContext(); val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192); var count = 0L
        while (true) {
            cancellation.ensureActive(); val size = input.read(buffer); if (size < 0) break; if (size == 0) continue
            if (size.toLong() > maximum - count) invalid("Eine externe Originaldatei überschreitet das geprüfte Speicherbudget.")
            output?.write(buffer, 0, size); digest.update(buffer, 0, size); count += size
        }
        return Fingerprint(count, hex(digest.digest()))
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun invalid(message: String): Nothing = throw OfflineBackupValidationException(message)
}
