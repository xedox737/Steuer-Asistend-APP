package com.example.data

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Ownership and conservative rollback of operation-owned import directories. */
internal object OfflineRestoreImportedFiles {
    private const val MARKER = ".immopilot-restore-owner"
    private data class Existing(val root: File, val marker: File?, val files: List<File>, val directories: List<File>)

    /** Only after a journal-bound FILES phase; does not import any payload. */
    suspend fun claim(context: Context, token: OfflineBackupCoordinator.Maintenance, entry: OfflineRestoreJournal.Entry,
        syncDirectory: (File) -> Unit = ::syncDirectory,
        rename: (File, File) -> Unit = { from, to -> Os.rename(from.path, to.path) }) {
        var temporary: File? = null
        try {
            OfflineBackupCoordinator.shared.requireMaintenance(token)
            require(entry.phase == OfflineRestoreJournal.Phase.FILES)
            withContext(Dispatchers.IO) { token.suspending {
                val verified = OfflineRestoreDatabaseVerifier.verify(context, entry)
                val plan = requireNotNull(verified.files.index.filePlan)
                val expected = marker(entry)
                val required = plan.items.fold(OfflineBackupResourceBudget.RESERVE_BYTES) { sum, item ->
                    require(item.payload.size <= Long.MAX_VALUE - sum); sum + item.payload.size
                }
                require(context.filesDir.usableSpace >= required && context.cacheDir.usableSpace >= required)
                // Check every existing directory before creating or changing any marker.
                plan.ownedDirectories.forEach { root -> inspect(root, plan, expected, allowEmptyUnmarked = true) }
                val state = File(context.noBackupFilesDir, "offline-backup-state")
                require(state.isDirectory && state.absoluteFile == state.canonicalFile)
                for (root in plan.ownedDirectories.sortedBy { it.path }) {
                    currentCoroutineContext().ensureActive(); require(OfflineRestoreJournal(context).load() == entry)
                    if (!root.exists()) {
                        val parent = requireNotNull(root.parentFile)
                        require(parent.isDirectory || parent.mkdirs())
                        require(parent.absoluteFile == parent.canonicalFile && root.absoluteFile == root.canonicalFile)
                        require(root.mkdir())
                        syncAncestors(context, parent, syncDirectory)
                    }
                    val marker = File(root, MARKER)
                    if (marker.exists()) { require(readMarker(marker).contentEquals(expected)); continue }
                    require(requireNotNull(root.list()).isEmpty())
                    val staged = File(state, "restore-owner-${UUID.randomUUID()}.tmp")
                    require(staged.absoluteFile == staged.canonicalFile && staged.createNewFile()); temporary = staged
                    FileOutputStream(staged).use { output -> output.write(expected); output.fd.sync() }
                    require(staged.readBytes().contentEquals(expected))
                    require(root.absoluteFile == root.canonicalFile && !marker.exists() && requireNotNull(root.list()).isEmpty())
                    rename(staged, marker); temporary = null
                    syncDirectory(root); syncDirectory(state)
                    require(readMarker(marker).contentEquals(expected))
                }
                require(OfflineRestoreJournal(context).load() == entry)
            } }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: ErrnoException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: SecurityException) { invalid() }
        finally { withContext(NonCancellable + Dispatchers.IO) { temporary?.let { if (it.exists() && !it.delete()) invalid() } } }
    }

    /** Install only freshly reverified archive payloads; existing files are never replaced. */
    suspend fun install(context: Context, token: OfflineBackupCoordinator.Maintenance, entry: OfflineRestoreJournal.Entry,
        prepared: OfflineBackupRestoreInspector.Prepared,
        syncDirectory: (File) -> Unit = ::syncDirectory,
        rename: (File, File) -> Unit = { from, to -> Os.rename(from.path, to.path) },
        link: (File, File) -> Unit = { from, to -> Os.link(from.path, to.path) }) {
        var temporary: File? = null
        try {
            OfflineBackupCoordinator.shared.requireMaintenance(token)
            require(entry.phase == OfflineRestoreJournal.Phase.FILES)
            withContext(Dispatchers.IO) { token.suspending {
                val rollback = OfflineRestoreDatabaseVerifier.verify(context, entry)
                val plan = requireNotNull(rollback.files.index.filePlan)
                // Repeat the complete authenticated manifest and isolated Room probe before any live file change.
                OfflineBackupRoomProbe.prepare(context, prepared.contents, prepared.generation).use { probe ->
                    require(OfflineRestoreFilePlan.prepare(context, entry.operationId, probe.manifest, requireUnused = false) == plan)
                    plan.items.forEach { item ->
                        val part = prepared.contents.parts.getValue(item.version.entry)
                        require(part.file != null && part.bytes == item.payload.size && part.sha256 == item.payload.sha256)
                    }
                }
                require(OfflineRestoreJournal(context).load() == entry)
                claim(context, token, entry, syncDirectory, rename)
                val state = File(context.noBackupFilesDir, "offline-backup-state")
                for (item in plan.items) {
                    currentCoroutineContext().ensureActive(); require(OfflineRestoreJournal(context).load() == entry)
                    val destination = item.destination; val child = requireNotNull(destination.parentFile)
                    val root = requireNotNull(child.parentFile)
                    require(root in plan.ownedDirectories && readMarker(File(root, MARKER)).contentEquals(marker(entry)))
                    require(child.absoluteFile == child.canonicalFile && destination.absoluteFile == destination.canonicalFile)
                    if (destination.exists()) { requireFile(destination, item.payload); continue }
                    if (!child.exists()) { require(child.mkdir()); syncDirectory(root) }
                    require(child.isDirectory && requireNotNull(child.list()).isEmpty())
                    val source = requireNotNull(prepared.contents.parts.getValue(item.version.entry).file)
                    val stage = File(state, "restore-payload-${UUID.randomUUID()}.tmp")
                    require(stage.absoluteFile == stage.canonicalFile && stage.createNewFile()); temporary = stage
                    val digest = MessageDigest.getInstance("SHA-256"); var count = 0L
                    FileInputStream(source).use { input -> FileOutputStream(stage).use { output ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            currentCoroutineContext().ensureActive(); val size = input.read(buffer); if (size < 0) break
                            require(size.toLong() <= item.payload.size - count)
                            output.write(buffer, 0, size); digest.update(buffer, 0, size); count += size
                        }
                        output.fd.sync()
                    } }
                    require(count == item.payload.size && digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == item.payload.sha256)
                    requireFile(stage, item.payload)
                    require(child.absoluteFile == child.canonicalFile && destination.absoluteFile == destination.canonicalFile && !destination.exists())
                    require(OfflineRestoreJournal(context).load() == entry)
                    // A hard link is atomic and fails if the destination already exists.
                    link(stage, destination)
                    syncDirectory(child)
                    requireFile(destination, item.payload)
                    require(stage.delete()); temporary = null; syncDirectory(state)
                }
                require(OfflineRestoreJournal(context).load() == entry)
            } }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: ErrnoException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: NoSuchElementException) { invalid() }
        catch (_: SecurityException) { invalid() }
        finally { withContext(NonCancellable + Dispatchers.IO) { temporary?.let { if (it.exists() && !it.delete()) invalid() } } }
    }

    /** Check that every planned file is installed and still matches its authenticated payload. */
    suspend fun verifyInstalled(context: Context, token: OfflineBackupCoordinator.Maintenance,
        entry: OfflineRestoreJournal.Entry): OfflineRestoreFilePlan.Plan {
        try {
            OfflineBackupCoordinator.shared.requireMaintenance(token)
            require(entry.phase in setOf(OfflineRestoreJournal.Phase.FILES, OfflineRestoreJournal.Phase.DATABASE))
            return withContext(Dispatchers.IO) { token.suspending {
                val index = OfflineRestoreRollbackVerifier.verifyIndex(context, entry)
                val plan = requireNotNull(index.filePlan)
                val expected = marker(entry)
                val existing = plan.ownedDirectories.map { root ->
                    requireNotNull(inspect(root, plan, expected, allowEmptyUnmarked = false)).also { require(it.marker != null) }
                }
                require(existing.flatMap { it.files }.toSet() == plan.items.map { it.destination }.toSet())
                require(OfflineRestoreJournal(context).load() == entry)
                plan
            } }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: NoSuchElementException) { invalid() }
        catch (_: SecurityException) { invalid() }
    }

    /** Delete only verified bytes in directories owned by this journal; unexpected data is preserved. */
    suspend fun rollback(context: Context, token: OfflineBackupCoordinator.Maintenance, entry: OfflineRestoreJournal.Entry,
        syncDirectory: (File) -> Unit = ::syncDirectory) {
        try {
            OfflineBackupCoordinator.shared.requireMaintenance(token)
            require(entry.phase == OfflineRestoreJournal.Phase.ROLLBACK)
            withContext(Dispatchers.IO) { token.suspending {
                val verified = OfflineRestoreDatabaseVerifier.verify(context, entry)
                val plan = requireNotNull(verified.files.index.filePlan)
                val expected = marker(entry)
                // Preflight all directories before the first deletion, protecting unknown data.
                val existing = plan.ownedDirectories.sortedBy { it.path }.mapNotNull { inspect(it, plan, expected, allowEmptyUnmarked = true) }
                val payloads = plan.items.associate { it.destination to it.payload }
                require(payloads.size == plan.items.size)
                for (item in existing) {
                    currentCoroutineContext().ensureActive(); require(OfflineRestoreJournal(context).load() == entry)
                    for (file in item.files) {
                        requireFile(file, payloads.getValue(file))
                        require(file.delete())
                        syncDirectory(requireNotNull(file.parentFile))
                    }
                    for (directory in item.directories.sortedByDescending { it.path.length }) {
                        require(directory.absoluteFile == directory.canonicalFile && directory.isDirectory && requireNotNull(directory.list()).isEmpty())
                        require(directory.delete()); syncDirectory(requireNotNull(directory.parentFile))
                    }
                    item.marker?.let { file -> require(readMarker(file).contentEquals(expected) && file.delete()); syncDirectory(item.root) }
                    require(item.root.absoluteFile == item.root.canonicalFile && item.root.isDirectory && requireNotNull(item.root.list()).isEmpty())
                    require(item.root.delete()); syncAncestors(context, requireNotNull(item.root.parentFile), syncDirectory)
                }
                require(OfflineRestoreJournal(context).load() == entry)
            } }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: ErrnoException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: NoSuchElementException) { invalid() }
        catch (_: SecurityException) { invalid() }
    }

    private suspend fun inspect(root: File, plan: OfflineRestoreFilePlan.Plan, markerBytes: ByteArray, allowEmptyUnmarked: Boolean): Existing? {
        require(root.absoluteFile == root.canonicalFile)
        if (!root.exists()) return null
        require(root.isDirectory)
        val permitted = plan.items.filter { it.destination.parentFile?.parentFile == root }.associateBy { requireNotNull(it.destination.parentFile).name }
        val contents = requireNotNull(root.listFiles()).toList()
        val markerFile = contents.singleOrNull { it.name == MARKER }
        if (markerFile == null) {
            require(allowEmptyUnmarked && contents.isEmpty())
            return Existing(root, null, emptyList(), emptyList())
        }
        require(readMarker(markerFile).contentEquals(markerBytes))
        val files = mutableListOf<File>(); val directories = mutableListOf<File>()
        for (directory in contents.filter { it.name != MARKER }) {
            currentCoroutineContext().ensureActive()
            val item = requireNotNull(permitted[directory.name])
            require(directory.absoluteFile == directory.canonicalFile && directory.isDirectory)
            val children = requireNotNull(directory.listFiles())
            require(children.size <= 1)
            children.forEach { file -> require(file == item.destination); requireFile(file, item.payload); files += file }
            directories += directory
        }
        return Existing(root, markerFile, files, directories)
    }
    private suspend fun requireFile(file: File, expected: OfflineBackupManifest.Reference) {
        require(file.absoluteFile == file.canonicalFile && file.isFile && file.length() == expected.size)
        val digest = MessageDigest.getInstance("SHA-256"); var total = 0L
        FileInputStream(file).use { input -> val buffer = ByteArray(8192); while (true) {
            currentCoroutineContext().ensureActive(); val count = input.read(buffer); if (count < 0) break
            require(count.toLong() <= expected.size - total); digest.update(buffer, 0, count); total += count
        } }
        require(total == expected.size && file.length() == total && digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == expected.sha256)
    }
    private fun marker(entry: OfflineRestoreJournal.Entry): ByteArray =
        ("ImmoPilot restore owner v1\n${entry.operationId}\n${entry.stagingId}\n${requireNotNull(entry.filePlanSha256)}\n").toByteArray(Charsets.UTF_8)
    private fun readMarker(file: File): ByteArray {
        require(file.absoluteFile == file.canonicalFile && file.isFile && file.length() in 1..256)
        return file.readBytes()
    }
    private fun syncAncestors(context: Context, start: File, sync: (File) -> Unit) {
        val files = context.filesDir.canonicalFile; val cache = context.cacheDir.canonicalFile
        val base = listOf(files, cache).filter { start.path == it.path || start.path.startsWith(it.path + File.separator) }
            .maxByOrNull { it.path.length } ?: throw IllegalArgumentException()
        var current = start
        while (true) {
            require(current.absoluteFile == current.canonicalFile && current.isDirectory)
            sync(current)
            if (current == base) break
            current = requireNotNull(current.parentFile)
        }
    }
    private fun syncDirectory(directory: File) {
        val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
        try { require(OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode)); Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }
    private fun invalid(): Nothing = throw OfflineBackupValidationException(
        "Die Restore-Dateien konnten nicht sicher zugeordnet oder bereinigt werden. Der Rückfall bleibt gesperrt; vorhandene Daten bleiben geschützt."
    )
}
