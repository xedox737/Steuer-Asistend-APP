package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.util.JsonWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Owns the immutable private input to the later encrypted archive engine. */
internal class OfflineBackupSnapshot private constructor(
    private val directory: File,
    private val fixed: List<FixedFile>,
    val summary: Summary
) : AutoCloseable {
    enum class Stage { METADATA_CAPTURED, ORIGINALS_FIXED, SNAPSHOT_FIXED }
    data class Progress(val stage: Stage, val completed: Int, val total: Int)
    data class Summary(val rowCounts: Map<String, Long>, val files: Int, val payloads: Int, val bytes: Long)
    private data class FixedFile(val entry: String, val file: File, val sha256: String, val bytes: Long)
    private data class Version(val id: String, val portablePath: String?, val kind: OfflineBackupFileInventory.Kind, val payload: FixedFile)
    private var closed = false

    suspend fun writeArchive(output: OutputStream): Unit = withContext(Dispatchers.IO) {
        if (closed) invalid("Der vorbereitete Snapshot ist bereits geschlossen.")
        try {
            ZipOutputStream(output).use { zip ->
                fixed.forEach { source ->
                    currentCoroutineContext().ensureActive()
                    val digest = MessageDigest.getInstance("SHA-256")
                    var size = 0L
                    zip.putNextEntry(ZipEntry(source.entry))
                    source.file.inputStream().use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            size += count
                            if (size > source.bytes) invalid("Ein fixierter Snapshot-Bestandteil wurde verändert.")
                            digest.update(buffer, 0, count)
                            zip.write(buffer, 0, count)
                        }
                    }
                    if (size != source.bytes || hex(digest.digest()) != source.sha256) invalid("Ein fixierter Snapshot-Bestandteil stimmt nicht mit seinem Nachweis überein.")
                    zip.closeEntry()
                }
            }
        } catch (_: IOException) {
            invalid("Der fixierte Snapshot konnte nicht vollständig archiviert werden. Bitte Speicher und Zugriff prüfen.")
        } catch (_: SecurityException) {
            invalid("Der Zugriff auf den fixierten Snapshot wurde verweigert.")
        }
    }

    override fun close() {
        if (!closed) {
            closed = true
            try {
                if (!directory.deleteRecursively()) invalid("Temporäre Snapshot-Dateien konnten nicht vollständig entfernt werden.")
            } catch (_: SecurityException) {
                invalid("Temporäre Snapshot-Dateien konnten wegen fehlender Zugriffsrechte nicht entfernt werden.")
            }
        }
    }

    companion object {
        private const val MAX_METADATA = 64L * 1024 * 1024
        private const val MAX_PAYLOAD = 2L * 1024 * 1024 * 1024 - MAX_METADATA
        private const val MAX_FILES = 10_000

        suspend fun capture(
            context: Context,
            database: AppDatabase,
            maximumPayloadBytes: Long = MAX_PAYLOAD,
            maximumFiles: Int = MAX_FILES,
            progress: (Progress) -> Unit = {}
        ): OfflineBackupSnapshot = withContext(Dispatchers.IO) {
            try {
                require(maximumPayloadBytes in 1..MAX_PAYLOAD && maximumFiles in 1..MAX_FILES)
                database.openHelper.writableDatabase
                OfflineBackupCoordinator.shared.awaitInitialization()
                val capture = OfflineBackupCoordinator.shared.beginCapture()
                val parent = File(context.filesDir, "offline-backup-staging")
                if (!parent.isDirectory && !parent.mkdirs()) invalid("Privates Snapshot-Staging ist nicht verfügbar.")
                val directory = File(parent, "snapshot-${UUID.randomUUID()}")
                if (!directory.mkdir()) invalid("Ein eindeutiger Snapshot-Arbeitsplatz konnte nicht angelegt werden.")
                var complete = false
                try {
                    val metadata = OfflineBackupMetadataWriter(context, database).write(File(directory, "metadata.json"))
                    progress(Progress(Stage.METADATA_CAPTURED, 0, 0))
                    val inventory = OfflineBackupFileInventory(context, maximumFiles)
                    val available = inventory.available()
                    val byCanonical = available.associateBy { it.source.canonicalPath }
                    val resolver = OfflineBackupResolver(context)
                    val payloads = linkedMapOf<String, FixedFile>()
                    val versions = linkedMapOf<String, Version>()
                    val aliases = linkedMapOf<String, String>()
                    var payloadBytes = 0L
                    var candidateNumber = 0
                    suspend fun pin(path: String, owner: String, hash: String?, size: Long?, candidates: List<String>): FixedFile {
                        currentCoroutineContext().ensureActive()
                        if (hash == null && !inventory.isCoordinated(path)) invalid("$owner: Die Originalversion ist ohne gespeicherten Inhaltsnachweis nicht sicher fixierbar.")
                        val staged = File(directory, "copy-${candidateNumber++}")
                        val sha = resolver.copy(path, staged, owner, hash, size, candidates)
                        val bytes = staged.length()
                        val existing = payloads[sha]
                        if (existing != null) {
                            if (existing.bytes != bytes) invalid("Widersprüchliche Inhaltsnachweise im Snapshot.")
                            if (!staged.delete()) invalid("Eine eigene doppelte Snapshot-Kopie konnte nicht entfernt werden.")
                            return existing
                        }
                        if (bytes > maximumPayloadBytes - payloadBytes) invalid("Die Snapshot-Originale überschreiten die Grenze von $maximumPayloadBytes Byte.")
                        payloadBytes += bytes
                        return FixedFile("files/${payloads.size}", staged, sha, bytes).also { payloads[sha] = it }
                    }
                    fun version(id: String, path: String?, kind: OfflineBackupFileInventory.Kind, payload: FixedFile) {
                        val old = versions[id]
                        if (old != null) {
                            if (old.payload.sha256 != payload.sha256) invalid("Eine Dateiversion besitzt widersprüchliche fixierte Inhalte.")
                        } else {
                            if (versions.size >= maximumFiles) invalid("Zu viele Dateiversionen im vollständigen Snapshot (maximal $maximumFiles).")
                            versions[id] = Version(id, path, kind, payload)
                        }
                    }
                    metadata.references.forEach { reference ->
                        val owned = localFile(reference.path)?.canonicalPath?.let(byCanonical::get)
                        val id = owned?.portablePath ?: "external/${reference.key}"
                        val payload = versions[id]?.payload ?: pin(reference.path, reference.owner, reference.expectedSha256,
                            reference.expectedSize, reference.verifiedCandidates)
                        if (reference.expectedSha256 != null && payload.sha256 != reference.expectedSha256) invalid("${reference.owner}: Der fixierte Inhalt passt nicht zur gespeicherten Prüfsumme.")
                        if (reference.expectedSize != null && payload.bytes != reference.expectedSize) invalid("${reference.owner}: Der fixierte Inhalt passt nicht zur gespeicherten Größe.")
                        version(id, owned?.portablePath, owned?.kind ?: OfflineBackupFileInventory.Kind.ORIGINAL, payload)
                        aliases[reference.key] = payload.entry
                    }
                    available.forEach { entry ->
                        if (entry.portablePath !in versions) {
                            val payload = pin(entry.source.absolutePath, "Erhaltene Datei „${entry.portablePath}“", null, null, emptyList())
                            version(entry.portablePath, entry.portablePath, entry.kind, payload)
                        }
                    }
                    progress(Progress(Stage.ORIGINALS_FIXED, versions.size, versions.size))
                    if (inventory.available().map { it.portablePath to it.kind } != available.map { it.portablePath to it.kind }) {
                        invalid("Verfügbare Original- oder Exportdateien wurden während der Erfassung entfernt oder verändert.")
                    }
                    val metadataFile = FixedFile("metadata.json", metadata.file, metadata.sha256, metadata.bytes)
                    val manifest = File(directory, "manifest.json")
                    val manifestDigest = MessageDigest.getInstance("SHA-256")
                    var manifestBytes = 0L
                    FileOutputStream(manifest).use { output ->
                        val bounded = object : OutputStream() {
                            private val digest = DigestOutputStream(output, manifestDigest)
                            override fun write(value: Int) { write(byteArrayOf(value.toByte()), 0, 1) }
                            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                                if (length.toLong() > MAX_METADATA - metadata.bytes - manifestBytes) invalid("Snapshot-Metadaten überschreiten die Grenze von 64 MiB.")
                                digest.write(bytes, offset, length); manifestBytes += length
                            }
                            override fun flush() = digest.flush()
                            override fun close() = flush()
                        }
                        JsonWriter(OutputStreamWriter(bounded, Charsets.UTF_8)).use { writer ->
                            writer.beginObject().name("format").value("ImmoPilotSnapshot").name("version").value(2L)
                            writer.name("metadata").beginObject().name("entry").value(metadataFile.entry)
                                .name("sha256").value(metadataFile.sha256).name("size").value(metadataFile.bytes).endObject()
                            writer.name("rowCounts").beginObject()
                            metadata.rowCounts.forEach { (name, count) -> writer.name(name).value(count) }
                            writer.endObject().name("aliases").beginObject()
                            aliases.forEach { (alias, entry) -> writer.name(alias).value(entry) }
                            writer.endObject().name("files").beginArray()
                            versions.values.forEach { file -> writer.beginObject().name("id").value(file.id)
                                .name("portablePath").value(file.portablePath).name("kind").value(file.kind.name)
                                .name("entry").value(file.payload.entry).endObject() }
                            writer.endArray().name("payloads").beginArray()
                            payloads.values.forEach { file -> writer.beginObject().name("entry").value(file.entry)
                                .name("sha256").value(file.sha256).name("size").value(file.bytes).endObject() }
                            writer.endArray().endObject()
                        }
                        output.fd.sync()
                    }
                    val manifestFile = FixedFile("manifest.json", manifest, hex(manifestDigest.digest()), manifestBytes)
                    OfflineBackupCoordinator.shared.completeCapture(capture)
                    val result = OfflineBackupSnapshot(directory, listOf(manifestFile, metadataFile) + payloads.values,
                        Summary(metadata.rowCounts, versions.size, payloads.size, metadata.bytes + manifestBytes + payloadBytes))
                    progress(Progress(Stage.SNAPSHOT_FIXED, versions.size, versions.size))
                    complete = true
                    result
                } catch (_: IOException) {
                    invalid("Der Snapshot konnte nicht vollständig fixiert werden. Bitte Speicher und Zugriff prüfen.")
                } catch (_: SecurityException) {
                    invalid("Der Zugriff auf Snapshot-Quellen oder Staging wurde verweigert.")
                } finally {
                    if (!complete) directory.deleteRecursively()
                }
            } catch (_: SQLiteException) {
                invalid("Die Datenbank konnte vor der Snapshot-Erfassung nicht vollständig geöffnet werden.")
            } catch (_: IOException) {
                invalid("Snapshot-Quellen oder privates Staging konnten nicht vollständig geöffnet werden.")
            } catch (_: SecurityException) {
                invalid("Der Zugriff auf Snapshot-Quellen oder privates Staging wurde verweigert.")
            }
        }

        private fun localFile(path: String): File? {
            val uri = Uri.parse(path)
            return when (uri.scheme) {
                null -> File(path)
                "file" -> if (uri.authority.isNullOrEmpty() && uri.query == null && uri.fragment == null) uri.path?.let(::File) else null
                else -> null
            }
        }
        private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
        private fun invalid(message: String): Nothing = throw OfflineBackupValidationException(message)
    }
}
