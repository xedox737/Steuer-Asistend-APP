package com.example.data

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Untrusted container boundary. Staged contents are NOT yet a successful manifest/Room probe. */
internal object OfflineBackupArchiveValidator {
    private const val GIB = 1024L * 1024 * 1024
    data class Limits(val bytes: Long = 2 * GIB, val entries: Int = 10_003,
        val metadataBytes: Long = 64 * 1024 * 1024, val payloadBytes: Long = GIB) {
        init { require(bytes in 1..(2 * GIB) && entries in 2..10_003 &&
            metadataBytes in 1..(64 * 1024 * 1024) && payloadBytes in 1..GIB) }
    }
    data class Part(val file: File?, val bytes: Long, val sha256: String, val inMemory: ByteArray? = null)
    class StagedContents internal constructor(private val directory: File, val parts: Map<String, Part>) : AutoCloseable {
        override fun close() {
            parts.values.forEach { it.inMemory?.fill(0) }
            if (directory.exists() && !directory.deleteRecursively()) invalid()
        }
    }

    private class MemoryBuffer : ByteArrayOutputStream() { fun wipe() { buf.fill(0); reset() } }

    suspend fun stageZip(context: Context, archive: File, limits: Limits = Limits()): StagedContents {
        var owned: File? = null
        var credential: ByteArray? = null
        var delivered = false
        try {
            val result = withContext(Dispatchers.IO) {
                require(archive.isFile && archive.length() in 1..(2 * GIB))
                val parent = File(context.filesDir, "offline-backup-staging")
                check(parent.isDirectory || parent.mkdirs())
                val directory = File(parent, "verify-${UUID.randomUUID()}")
                check(directory.mkdir()); owned = directory
                val central = linkedMapOf<String, Pair<Long, Long>>()
                ZipFile(archive).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        currentCoroutineContext().ensureActive()
                        val entry = entries.nextElement()
                        require(!entry.isDirectory && validName(entry.name) && entry.name !in central)
                        require(central.size < limits.entries && entry.size in 0..entryLimit(entry.name, limits))
                        central[entry.name] = entry.size to entry.crc
                    }
                }
                require(central.keys.containsAll(setOf("manifest.json", "metadata.json")))
                val parts = linkedMapOf<String, Part>()
                var total = 0L; var metadata = 0L
                ZipInputStream(archive.inputStream().buffered()).use { zip ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val entry = zip.nextEntry ?: break
                        val expected = central[entry.name] ?: invalid()
                        require(!entry.isDirectory && validName(entry.name) && entry.name !in parts && parts.size < limits.entries)
                        val memory = if (entry.name == "credentials.bin") MemoryBuffer() else null
                        val file = if (memory == null) File(directory, "part-${parts.size}") else null
                        val digest = MessageDigest.getInstance("SHA-256")
                        var bytes = 0L
                        try {
                            val destination: OutputStream = memory ?: FileOutputStream(requireNotNull(file))
                            destination.use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = zip.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue
                                require(count.toLong() <= entryLimit(entry.name, limits) - bytes && count.toLong() <= limits.bytes - total)
                                if (!entry.name.startsWith("files/")) {
                                    require(count.toLong() <= limits.metadataBytes - metadata); metadata += count
                                }
                                output.write(buffer, 0, count); digest.update(buffer, 0, count)
                                bytes += count; total += count
                            }
                                if (output is FileOutputStream) output.fd.sync()
                            }
                            if (memory != null) credential = memory.toByteArray()
                        } finally { memory?.wipe() }
                        zip.closeEntry()
                        require(bytes == expected.first && entry.crc == expected.second)
                        parts[entry.name] = Part(file, bytes, digest.digest().joinToString("") {
                            (it.toInt() and 255).toString(16).padStart(2, '0') }, if (memory != null) credential else null)
                    }
                }
                require(parts.keys == central.keys)
                StagedContents(directory, parts)
            }
            delivered = true
            return result
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: SecurityException) { invalid() }
        finally {
            if (!delivered) {
                credential?.fill(0)
                owned?.let { if (it.exists() && !it.deleteRecursively()) invalid() }
            }
        }
    }
    /** Mandatory passwordless reread gate for a newly encrypted private engine result. */
    suspend fun verifyPrepared(context: Context, archive: File, generation: OfflineBackupKeyEnvelope.Generation,
        expected: OfflineBackupSnapshot.Summary): Unit = withContext(Dispatchers.IO) {
        var owned: File? = null
        try {
            val parent = File(context.filesDir, "offline-backup-staging")
            require(parent.isDirectory || parent.mkdirs())
            val directory = File(parent, "reread-${UUID.randomUUID()}")
            require(directory.mkdir()); owned = directory
            val plaintext = File(directory, "archive.zip")
            archive.inputStream().use { OfflineBackupArchiveCrypto.decryptToPrivateStaging(it, generation, plaintext) }
            stageZip(context, plaintext).use { staged ->
                OfflineBackupRoomProbe.prepare(context, staged, generation).use { probe ->
                    require(probe.manifest.rowCounts == expected.rowCounts && probe.manifest.versions.size == expected.files &&
                        probe.manifest.payloads.size == expected.payloads && staged.parts.values.sumOf { it.bytes } == expected.bytes)
                }
            }
            currentCoroutineContext().ensureActive()
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: SecurityException) { invalid() }
        finally { owned?.let { if (it.exists() && !it.deleteRecursively()) invalid() } }
    }

    private fun validName(name: String) = name in setOf("manifest.json", "metadata.json", "credentials.bin") ||
        Regex("files/(0|[1-9][0-9]{0,3})").matches(name)
    private fun entryLimit(name: String, limits: Limits): Long = when {
        name == "credentials.bin" -> minOf(128 * 1024L, limits.metadataBytes)
        name.startsWith("files/") -> limits.payloadBytes
        else -> limits.metadataBytes
    }
    private fun invalid(): Nothing = throw OfflineBackupValidationException("Das Sicherungsarchiv ist unvollständig, beschädigt oder überschreitet die erlaubten Grenzen. Es wurden keine Daten übernommen.")
}
