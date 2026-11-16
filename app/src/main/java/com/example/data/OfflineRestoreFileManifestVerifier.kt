package com.example.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** Validates private rollback file manifests and every listed payload, without applying them. */
internal object OfflineRestoreFileManifestVerifier {
    data class Reference(val key: String, val pathSha256: String, val state: OfflineRestoreReferenceSnapshot.State,
        val stagedName: String?, val bytes: Long?, val sha256: String?)
    data class Checked(val index: OfflineRestoreRollbackVerifier.Index,
        val files: List<OfflineRestoreOwnedFilesSnapshot.Version>, val references: List<Reference>)
    private const val MAX_MANIFEST = 64L * 1024 * 1024
    private val hashPattern = Regex("[0-9a-f]{64}")

    suspend fun verify(context: Context, journal: OfflineRestoreJournal.Entry): Checked = withContext(Dispatchers.IO) {
        try {
            val index = OfflineRestoreRollbackVerifier.verifyIndex(context, journal)
            val cancellation = currentCoroutineContext()
            fun reader(file: File): JsonReader {
                val runtime = Runtime.getRuntime()
                val free = runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()
                require(file.length() in 1..MAX_MANIFEST && file.length() <= (free - 16L * 1024 * 1024) / 4)
                val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                return JsonReader(InputStreamReader(FileInputStream(file), decoder)).apply { isLenient = false }
            }
            val files = reader(index.ownedManifest).use { Parser(it, cancellation).owned(index.rollbackId, index.fileCount) }
            val ownedDir = requireNotNull(index.ownedManifest.parentFile)
            val ownedByName = files.associateBy { it.stagedName }
            files.forEach { version -> checkPayload(ownedDir, version.stagedName, version.bytes, version.sha256) }
            requireExactDirectory(ownedDir, files.map { it.stagedName })
            val refs = reader(index.referenceManifest).use {
                Parser(it, cancellation).references(index.rollbackId, index.referenceCount, index.unavailableCount, ownedByName)
            }
            val referenceDir = requireNotNull(index.referenceManifest.parentFile)
            val external = refs.filter { it.state == OfflineRestoreReferenceSnapshot.State.EXTERNAL }
            external.forEach { reference -> checkPayload(referenceDir, requireNotNull(reference.stagedName), requireNotNull(reference.bytes), requireNotNull(reference.sha256)) }
            requireExactDirectory(referenceDir, external.map { requireNotNull(it.stagedName) })
            // Direct manifests and journal must still match after all payloads have been read.
            require(OfflineRestoreRollbackVerifier.verifyIndex(context, journal) == index)
            Checked(index, files, refs)
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: NoSuchElementException) { invalid() }
        catch (_: SecurityException) { invalid() }
    }
    private class Parser(private val reader: JsonReader, private val cancellation: CoroutineContext) {
        private fun fields(required: Set<String>, visit: (String) -> Unit) {
            val seen = hashSetOf<String>(); reader.beginObject()
            while (reader.hasNext()) {
                cancellation.ensureActive(); val name = reader.nextName(); require(name in required && seen.add(name)); visit(name)
            }
            reader.endObject(); require(seen == required)
        }
        private fun text(maximum: Int = 8192): String {
            require(reader.peek() == JsonToken.STRING)
            return reader.nextString().also { require(it.length <= maximum && '\u0000' !in it) }
        }
        private fun nullableText(): String? = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else text()
        private fun number(): Long {
            require(reader.peek() == JsonToken.NUMBER)
            val value = reader.nextString(); require(Regex("0|[1-9][0-9]{0,18}").matches(value))
            return requireNotNull(value.toLongOrNull())
        }
        private fun nullableNumber(): Long? = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else number()
        fun owned(id: String, expectedCount: Int): List<OfflineRestoreOwnedFilesSnapshot.Version> {
            val result = mutableListOf<OfflineRestoreOwnedFilesSnapshot.Version>(); val paths = hashSetOf<String>()
            var version: Long? = null; var foundId: String? = null
            fields(setOf("version", "id", "files")) { name -> when (name) {
                "version" -> version = number()
                "id" -> foundId = text()
                "files" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        cancellation.ensureActive(); require(result.size < 10_000)
                        var path: String? = null; var kind: String? = null; var file: String? = null; var bytes: Long? = null; var sha: String? = null
                        fields(setOf("path", "kind", "file", "bytes", "sha256")) { field -> when (field) {
                            "path" -> path = text(); "kind" -> kind = text(); "file" -> file = text(); "bytes" -> bytes = number(); "sha256" -> sha = text()
                        } }
                        val portablePath = requireNotNull(path); val fileKind = OfflineBackupFileInventory.Kind.valueOf(requireNotNull(kind))
                        require(OfflineBackupManifest.portableKind(portablePath) == fileKind && paths.add(portablePath))
                        require(file == "file-${result.size}" && hashPattern.matches(requireNotNull(sha)))
                        result += OfflineRestoreOwnedFilesSnapshot.Version(portablePath, fileKind, requireNotNull(file), requireNotNull(bytes), requireNotNull(sha))
                    }
                    reader.endArray()
                }
            } }
            require(version == 1L && foundId == id && result.size == expectedCount && reader.peek() == JsonToken.END_DOCUMENT)
            return result
        }
        fun references(id: String, expectedCount: Int, expectedUnavailable: Int,
            owned: Map<String, OfflineRestoreOwnedFilesSnapshot.Version>): List<Reference> {
            val result = mutableListOf<Reference>(); val paths = hashSetOf<String>(); var external = 0; var unavailable = 0
            var version: Long? = null; var foundId: String? = null
            fields(setOf("version", "id", "references")) { name -> when (name) {
                "version" -> version = number()
                "id" -> foundId = text()
                "references" -> {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        cancellation.ensureActive(); require(result.size < 10_000)
                        var key: String? = null; var path: String? = null; var owner: String? = null; var state: String? = null
                        var file: String? = null; var bytes: Long? = null; var sha: String? = null
                        fields(setOf("key", "pathSha256", "owner", "state", "file", "bytes", "sha256")) { field -> when (field) {
                            "key" -> key = text(); "pathSha256" -> path = text(); "owner" -> owner = text(MAX_MANIFEST.toInt())
                            "state" -> state = text(); "file" -> file = nullableText(); "bytes" -> bytes = nullableNumber(); "sha256" -> sha = nullableText()
                        } }
                        require(key == "original-${result.size}" && hashPattern.matches(requireNotNull(path)) && paths.add(path))
                        require(!owner.isNullOrBlank())
                        val refState = OfflineRestoreReferenceSnapshot.State.valueOf(requireNotNull(state))
                        when (refState) {
                            OfflineRestoreReferenceSnapshot.State.OWNED -> {
                                val item = owned[requireNotNull(file)] ?: error("Unbekannte Dateireferenz")
                                require(item.bytes == bytes && item.sha256 == sha)
                            }
                            OfflineRestoreReferenceSnapshot.State.EXTERNAL -> {
                                require(file == "external-${external++}" && bytes != null && sha != null && hashPattern.matches(requireNotNull(sha)))
                            }
                            OfflineRestoreReferenceSnapshot.State.UNAVAILABLE -> { require(file == null && bytes == null && sha == null); unavailable++ }
                        }
                        result += Reference(requireNotNull(key), requireNotNull(path), refState, file, bytes, sha)
                    }
                    reader.endArray()
                }
            } }
            require(version == 1L && foundId == id && result.size == expectedCount && unavailable == expectedUnavailable && reader.peek() == JsonToken.END_DOCUMENT)
            return result
        }
    }
    private suspend fun checkPayload(directory: File, name: String, expectedBytes: Long, expectedHash: String) {
        require(expectedBytes >= 0 && hashPattern.matches(expectedHash))
        val file = File(directory, name)
        require(file.absoluteFile == file.canonicalFile && file.isFile && file.canRead() && file.length() == expectedBytes)
        val digest = MessageDigest.getInstance("SHA-256"); var bytes = 0L
        FileInputStream(file).use { input -> val buffer = ByteArray(8192); while (true) {
            currentCoroutineContext().ensureActive(); val count = input.read(buffer); if (count < 0) break
            require(count.toLong() <= expectedBytes - bytes); digest.update(buffer, 0, count); bytes += count
        } }
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        require(bytes == expectedBytes && file.length() == expectedBytes && hash == expectedHash)
    }
    private fun requireExactDirectory(directory: File, names: List<String>) {
        val actual = requireNotNull(directory.list()).toSet()
        require(names.size == names.toSet().size && actual == names.toSet() + "manifest.json")
    }
    private fun invalid(): Nothing = throw OfflineBackupValidationException("Die Dateirücklage ist unvollständig oder beschädigt. Der Bestand bleibt gesperrt.")
}
