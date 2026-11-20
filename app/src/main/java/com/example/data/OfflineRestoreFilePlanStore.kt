package com.example.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Durable private file plan. Binding it to a restore journal is a separate required step. */
internal object OfflineRestoreFilePlanStore {
    data class Snapshot(val stagingId: String, val operationId: String, val file: File, val bytes: Long, val sha256: String)
    private const val MAX_BYTES = 128L * 1024 * 1024
    private const val MAX_ITEMS = 10_000
    private val shaPattern = Regex("[0-9a-f]{64}")

    suspend fun capture(context: Context, token: OfflineBackupCoordinator.Maintenance, stagingId: String,
        plan: OfflineRestoreFilePlan.Plan): Snapshot {
        var owned: File? = null; var delivered = false
        try {
            val result = withContext(Dispatchers.IO) {
                OfflineBackupCoordinator.shared.requireMaintenance(token)
                uuid(stagingId)
                token.suspending {
                    val entriesByDestination = plan.items.associate { it.destination to it.version.entry }
                    require(entriesByDestination.size == plan.items.size)
                    val aliasEntries = plan.aliases.mapValues { (_, path) -> entriesByDestination.getValue(path) }
                    require(rebuild(context, plan.operationId, plan.items.map { it.version to it.payload }, aliasEntries, true) == plan)
                    val directory = File(context.noBackupFilesDir, "offline-backup-state")
                    require(directory.isDirectory || directory.mkdirs())
                    require(directory.usableSpace > OfflineBackupResourceBudget.RESERVE_BYTES)
                    val file = File(directory, "file-plan-$stagingId.json")
                    require(file.absoluteFile == file.canonicalFile && file.createNewFile()); owned = file
                    var bytes = 0L; val digest = MessageDigest.getInstance("SHA-256"); val cancellation = currentCoroutineContext()
                    FileOutputStream(file).use { stream ->
                        val bounded = object : OutputStream() {
                            override fun write(value: Int) { require(bytes < MAX_BYTES); stream.write(value); digest.update(value.toByte()); bytes++ }
                            override fun write(data: ByteArray, offset: Int, length: Int) {
                                require(length.toLong() <= MAX_BYTES - bytes); stream.write(data, offset, length); digest.update(data, offset, length); bytes += length
                            }
                            override fun flush() = stream.flush()
                        }
                        val encoder = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                        val writer = JsonWriter(OutputStreamWriter(bounded, encoder))
                        writer.beginObject().name("version").value(1L).name("owner").value("local-restore-file-plan")
                            .name("operationId").value(plan.operationId).name("stagingId").value(stagingId).name("items").beginArray()
                        plan.items.forEach { item ->
                            cancellation.ensureActive()
                            writer.beginObject().name("id").value(item.version.id).name("portablePath").value(item.version.portablePath)
                                .name("kind").value(item.version.kind.name).name("entry").value(item.version.entry)
                                .name("bytes").value(item.payload.size).name("sha256").value(item.payload.sha256).endObject()
                        }
                        writer.endArray().name("aliases").beginObject()
                        plan.aliases.toSortedMap().forEach { (alias, path) ->
                            cancellation.ensureActive()
                            writer.name(alias).value(entriesByDestination.getValue(path))
                        }
                        writer.endObject().endObject(); writer.flush(); stream.fd.sync()
                    }
                    val hash = hex(digest.digest())
                    val checked = read(context, plan.operationId, stagingId, bytes, hash, true)
                    require(checked == plan)
                    Snapshot(stagingId, plan.operationId, file, bytes, hash)
                }
            }
            delivered = true; return result
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: NoSuchElementException) { invalid() }
        catch (_: SecurityException) { invalid() }
        finally { if (!delivered) withContext(NonCancellable + Dispatchers.IO) {
            owned?.let { if (it.exists() && !it.delete()) invalid() }
        } }
    }

    /** Callers must obtain the expected size and digest from a trusted journal-bound source. */
    suspend fun verify(context: Context, operationId: String, stagingId: String, bytes: Long, sha256: String): OfflineRestoreFilePlan.Plan =
        withContext(Dispatchers.IO) { try { read(context, operationId, stagingId, bytes, sha256, false) }
        catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: NoSuchElementException) { invalid() }
        catch (_: SecurityException) { invalid() } }

    /** Discard an owned plan only before it has been bound to any journal. */
    suspend fun discardUnjournalled(context: Context, token: OfflineBackupCoordinator.Maintenance, snapshot: Snapshot) =
        withContext(Dispatchers.IO) {
            try {
                OfflineBackupCoordinator.shared.requireMaintenance(token)
                token.suspending {
                    require(OfflineRestoreJournal(context).load() == null)
                    verify(context, snapshot.operationId, snapshot.stagingId, snapshot.bytes, snapshot.sha256)
                    val expected = File(context.noBackupFilesDir, "offline-backup-state/file-plan-${snapshot.stagingId}.json").absoluteFile
                    require(snapshot.file.absoluteFile == expected && expected == expected.canonicalFile)
                    require(OfflineRestoreJournal(context).load() == null && expected.delete())
                }
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: OfflineBackupValidationException) { throw error }
            catch (_: IOException) { invalid() }
            catch (_: IllegalArgumentException) { invalid() }
            catch (_: IllegalStateException) { invalid() }
            catch (_: SecurityException) { invalid() }
        }

    private suspend fun read(context: Context, operationId: String, stagingId: String, size: Long, hash: String,
        requireUnused: Boolean): OfflineRestoreFilePlan.Plan {
        uuid(operationId); uuid(stagingId); require(size in 1..MAX_BYTES && shaPattern.matches(hash))
        val file = File(context.noBackupFilesDir, "offline-backup-state/file-plan-$stagingId.json")
        require(file.absoluteFile == file.canonicalFile && file.isFile && file.canRead() && file.length() == size)
        val digest = MessageDigest.getInstance("SHA-256"); var total = 0L
        FileInputStream(file).use { input -> val buffer = ByteArray(8192); while (true) {
            currentCoroutineContext().ensureActive(); val count = input.read(buffer); if (count < 0) break
            require(count.toLong() <= size - total); digest.update(buffer, 0, count); total += count
        } }
        require(total == size && file.length() == size && hex(digest.digest()) == hash)
        val runtime = Runtime.getRuntime(); val available = runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()
        require(size <= (available - 16L * 1024 * 1024) / 4)
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        val items = mutableListOf<Pair<OfflineBackupManifest.Version, OfflineBackupManifest.Reference>>()
        val aliases = linkedMapOf<String, String>()
        JsonReader(InputStreamReader(FileInputStream(file), decoder)).use { reader ->
            reader.isLenient = false; reader.beginObject()
            fun name(expected: String) { require(reader.hasNext() && reader.nextName() == expected) }
            fun text(): String { require(reader.peek() == JsonToken.STRING); return reader.nextString().also { require(it.length <= 8192 && '\u0000' !in it) } }
            name("version"); require(reader.peek() == JsonToken.NUMBER && reader.nextString() == "1")
            name("owner"); require(text() == "local-restore-file-plan")
            name("operationId"); require(text() == operationId)
            name("stagingId"); require(text() == stagingId)
            name("items"); reader.beginArray()
            while (reader.hasNext()) {
                currentCoroutineContext().ensureActive(); require(items.size < MAX_ITEMS); reader.beginObject()
                name("id"); val id = text(); name("portablePath")
                val path = if (reader.peek() == JsonToken.NULL) { reader.nextNull(); null } else text()
                name("kind"); val kind = OfflineBackupFileInventory.Kind.valueOf(text())
                name("entry"); val entry = text(); require(entry.matches(Regex("files/(0|[1-9][0-9]{0,3})")))
                name("bytes"); require(reader.peek() == JsonToken.NUMBER); val raw = reader.nextString()
                val count = requireNotNull(raw.toLongOrNull()); require(count.toString() == raw && count in 0..(1024L * 1024 * 1024))
                name("sha256"); val sha = text(); require(shaPattern.matches(sha))
                require(!reader.hasNext()); reader.endObject()
                items += OfflineBackupManifest.Version(id, path, kind, entry) to OfflineBackupManifest.Reference(entry, sha, count)
            }
            reader.endArray(); name("aliases"); reader.beginObject()
            while (reader.hasNext()) {
                currentCoroutineContext().ensureActive(); require(aliases.size < MAX_ITEMS)
                val alias = reader.nextName(); require(alias !in aliases); aliases[alias] = text()
            }
            reader.endObject(); require(!reader.hasNext()); reader.endObject(); require(reader.peek() == JsonToken.END_DOCUMENT)
        }
        val reread = MessageDigest.getInstance("SHA-256"); var rereadBytes = 0L
        FileInputStream(file).use { input -> val buffer = ByteArray(8192); while (true) {
            currentCoroutineContext().ensureActive(); val count = input.read(buffer); if (count < 0) break
            require(count.toLong() <= size - rereadBytes); reread.update(buffer, 0, count); rereadBytes += count
        } }
        require(rereadBytes == size && file.length() == size && hex(reread.digest()) == hash)
        return rebuild(context, operationId, items, aliases, requireUnused)
    }
    private fun rebuild(context: Context, operationId: String,
        items: List<Pair<OfflineBackupManifest.Version, OfflineBackupManifest.Reference>>,
        aliases: Map<String, String>, requireUnused: Boolean): OfflineRestoreFilePlan.Plan {
        require(items.size <= MAX_ITEMS && aliases.keys == (0 until aliases.size).map { "original-$it" }.toSet())
        val grouped = items.map { it.second }.groupBy { it.entry }
        require(grouped.values.all { it.distinct().size == 1 })
        val manifest = OfflineBackupManifest.Checked(emptyMap(), aliases, items.map { it.first },
            grouped.values.map { it.first() }, null, emptySet(), OfflineBackupArchiveInfo("2026-01-01T00:00:00Z", 0, "internal"))
        return OfflineRestoreFilePlan.prepare(context, operationId, manifest, requireUnused)
    }
    private fun uuid(value: String) { require(UUID.fromString(value).toString() == value) }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun invalid(): Nothing = throw OfflineBackupValidationException("Der private Restore-Dateiplan ist ungültig oder unvollständig. Der Bestand bleibt gesperrt.")
}
