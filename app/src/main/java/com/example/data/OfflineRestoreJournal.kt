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
internal class OfflineRestoreJournal(private val context: Context) {
    enum class Phase { PREPARED, FILES, DATABASE, PREFERENCES, ROLLBACK, COMMITTED, ROLLED_BACK }
    data class Entry(val operationId: String, val stagingId: String, val rollbackId: String, val phase: Phase,
        val rollbackManifestBytes: Long, val rollbackManifestSha256: String,
        val filePlanBytes: Long? = null, val filePlanSha256: String? = null) {
        init {
            listOf(operationId, stagingId, rollbackId).forEach { require(UUID.fromString(it).toString() == it) }
            require(rollbackManifestBytes in 1..MAX_BUNDLE_BYTES && rollbackManifestSha256.matches(Regex("[0-9a-f]{64}")))
            require((filePlanBytes == null) == (filePlanSha256 == null))
            if (filePlanBytes != null) require(filePlanBytes in 1..MAX_PLAN_BYTES && requireNotNull(filePlanSha256).matches(Regex("[0-9a-f]{64}")))
        }
    }
    private val directory = File(context.noBackupFilesDir, "offline-backup-state")
    private val file = File(directory, "restore-journal.json")

    fun load(): Entry? = synchronized(lock) { guarded { read() } }
    fun begin(operationId: String, stagingId: String, rollbackId: String,
        rollbackManifestBytes: Long, rollbackManifestSha256: String): Entry =
        guarded { beginBound(Entry(operationId, stagingId, rollbackId, Phase.PREPARED, rollbackManifestBytes, rollbackManifestSha256)) }

    /** Arm the process gate before the first suspension or durable journal write. */
    suspend fun beginProtected(token: OfflineBackupCoordinator.Maintenance, rollbackId: String,
        rollbackManifestBytes: Long, rollbackManifestSha256: String, plan: OfflineRestoreFilePlanStore.Snapshot): Entry {
        val coordinator = OfflineBackupCoordinator.shared
        coordinator.requireMaintenance(token)
        coordinator.markRestorePending(token)
        // Even cancellation or an uncertain write failure leaves the gate armed. Recovery
        // verifies the journal (or its absence) before it is permitted to unblock writers.
        return beginWithFilePlan(token, rollbackId, rollbackManifestBytes, rollbackManifestSha256, plan)
    }

    /** Caller keeps maintenance through journal creation and every subsequent mutation. */
    suspend fun beginWithFilePlan(token: OfflineBackupCoordinator.Maintenance, rollbackId: String,
        rollbackManifestBytes: Long, rollbackManifestSha256: String, plan: OfflineRestoreFilePlanStore.Snapshot): Entry =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                OfflineBackupCoordinator.shared.requireMaintenance(token)
                token.suspending {
                    val checked = OfflineRestoreFilePlanStore.verify(context, plan.operationId, plan.stagingId, plan.bytes, plan.sha256)
                    require(checked.ownedDirectories.all { !it.exists() && it.absoluteFile == it.canonicalFile })
                    beginBound(Entry(plan.operationId, plan.stagingId, rollbackId, Phase.PREPARED,
                        rollbackManifestBytes, rollbackManifestSha256, plan.bytes, plan.sha256))
                }
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: OfflineBackupValidationException) { throw error }
            catch (_: IOException) { invalid() }
            catch (_: IllegalArgumentException) { invalid() }
            catch (_: IllegalStateException) { invalid() }
            catch (_: SecurityException) { invalid() }
        }

    private fun beginBound(entry: Entry): Entry = synchronized(lock) { guarded {
        if (read() != null) invalid()
        checkBoundFile(File(directory, "rollback-${entry.rollbackId}.bundle.json"), entry.rollbackManifestBytes, entry.rollbackManifestSha256)
        entry.filePlanBytes?.let { size ->
            checkBoundFile(File(directory, "file-plan-${entry.stagingId}.json"), size, requireNotNull(entry.filePlanSha256))
        }
        write(entry)
    } }
    private fun checkBoundFile(file: File, bytes: Long, sha256: String) {
        require(file.absoluteFile == file.canonicalFile && file.isFile && file.length() == bytes)
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192); var total = 0L
            while (true) { val count = input.read(buffer); if (count < 0) break
                require(count.toLong() <= bytes - total); digest.update(buffer, 0, count); total += count }
            require(total == bytes && file.length() == bytes)
        }
        require(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == sha256)
    }
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
            writer.beginObject().name("version").value(if (entry.filePlanBytes == null) 2L else 3L).name("owner").value("local-full-restore")
                .name("operationId").value(entry.operationId).name("stagingId").value(entry.stagingId)
                .name("rollbackId").value(entry.rollbackId).name("phase").value(entry.phase.name)
                .name("rollbackManifestBytes").value(entry.rollbackManifestBytes)
                .name("rollbackManifestSha256").value(entry.rollbackManifestSha256)
            entry.filePlanBytes?.let { writer.name("filePlanBytes").value(it).name("filePlanSha256").value(entry.filePlanSha256) }
            writer.endObject()
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
                val expected = if (name == "version" || name == "rollbackManifestBytes" || name == "filePlanBytes") JsonToken.NUMBER else JsonToken.STRING
                require(reader.peek() == expected); values[name] = reader.nextString()
            }
            reader.endObject(); require(reader.peek() == JsonToken.END_DOCUMENT)
            val common = setOf("version", "owner", "operationId", "stagingId", "rollbackId", "phase", "rollbackManifestBytes", "rollbackManifestSha256")
            require(when (values["version"]) {
                "2" -> values.keys == common
                "3" -> values.keys == common + setOf("filePlanBytes", "filePlanSha256")
                else -> false
            } && values["owner"] == "local-full-restore")
            val sizeText = values.getValue("rollbackManifestBytes")
            val size = sizeText.toLong(); require(size.toString() == sizeText)
            val planSize = values["filePlanBytes"]?.let { raw -> raw.toLong().also { require(it.toString() == raw) } }
            Entry(values.getValue("operationId"), values.getValue("stagingId"), values.getValue("rollbackId"),
                Phase.valueOf(values.getValue("phase")), size, values.getValue("rollbackManifestSha256"), planSize, values["filePlanSha256"])
        }
    }
    private inline fun <T> guarded(block: () -> T): T = try { block() }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: SecurityException) { invalid() }
    private fun invalid(): Nothing = throw OfflineBackupValidationException("Das lokale Wiederherstellungsjournal ist ungültig oder nicht verfügbar. Der Bestand darf noch nicht geöffnet oder verändert werden.")
    private companion object { val lock = Any(); const val MAX_BYTES = 8192; const val MAX_BUNDLE_BYTES = 16L * 1024; const val MAX_PLAN_BYTES = 128L * 1024 * 1024 }
}
