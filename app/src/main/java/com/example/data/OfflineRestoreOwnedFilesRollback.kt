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

/** Replays previously existing app-owned files; does not delete files or write external sources. */
internal object OfflineRestoreOwnedFilesRollback {
    suspend fun apply(context: Context, token: OfflineBackupCoordinator.Maintenance, entry: OfflineRestoreJournal.Entry,
        syncDirectory: (File) -> Unit = ::syncDirectory,
        rename: (File, File) -> Unit = { from, to -> Os.rename(from.path, to.path) }) {
        var work: File? = null
        try {
            OfflineBackupCoordinator.shared.requireMaintenance(token)
            require(entry.phase == OfflineRestoreJournal.Phase.ROLLBACK)
            withContext(Dispatchers.IO) {
                token.suspending {
                    // Verify the database as well, so file mappings are bound to its original references.
                    val verified = OfflineRestoreDatabaseVerifier.verify(context, entry)
                    val journal = OfflineRestoreJournal(context)
                    val owned = verified.files.files
                    val destinations = owned.map { version ->
                        require(OfflineBackupManifest.portableKind(version.portablePath) == version.kind)
                        val base = if (version.portablePath.startsWith("files/")) context.filesDir else context.cacheDir
                        require(base.absoluteFile == base.canonicalFile && base.isDirectory)
                        File(base, version.portablePath.substringAfter('/')).also { destination ->
                            require(destination.absoluteFile == destination.canonicalFile)
                            require(destination.path.startsWith(base.path + File.separator))
                            require(!destination.exists() || destination.isFile)
                        }
                    }
                    val parent = File(context.noBackupFilesDir, "offline-backup-state")
                    val directory = File(parent, "rollback-files-work-${UUID.randomUUID()}")
                    require(directory.absoluteFile == directory.canonicalFile && directory.mkdir())
                    work = directory
                    owned.zip(destinations).forEachIndexed { number, (version, destination) ->
                        currentCoroutineContext().ensureActive()
                        require(journal.load() == entry)
                        val required = version.bytes + OfflineBackupResourceBudget.RESERVE_BYTES
                        require(required >= version.bytes && directory.usableSpace >= required)
                        val source = File(requireNotNull(verified.files.index.ownedManifest.parentFile), version.stagedName)
                        val staged = File(directory, "file-$number")
                        val digest = MessageDigest.getInstance("SHA-256"); var total = 0L
                        FileInputStream(source).use { input -> FileOutputStream(staged).use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val size = input.read(buffer); if (size < 0) break
                                require(size.toLong() <= version.bytes - total)
                                output.write(buffer, 0, size); digest.update(buffer, 0, size); total += size
                            }
                            output.fd.sync()
                        } }
                        require(total == version.bytes && hex(digest.digest()) == version.sha256)
                        val destinationParent = requireNotNull(destination.parentFile)
                        require(destinationParent.isDirectory || destinationParent.mkdirs())
                        require(destination.absoluteFile == destination.canonicalFile && destinationParent.absoluteFile == destinationParent.canonicalFile)
                        require(!destination.exists() || destination.isFile)
                        require(journal.load() == entry)
                        rename(staged, destination)
                        val base = (if (version.portablePath.startsWith("files/")) context.filesDir else context.cacheDir).absoluteFile
                        var ancestor = destinationParent
                        while (true) {
                            require(ancestor.absoluteFile == ancestor.canonicalFile)
                            syncDirectory(ancestor)
                            if (ancestor == base) break
                            ancestor = requireNotNull(ancestor.parentFile)
                            require(ancestor.path == base.path || ancestor.path.startsWith(base.path + File.separator))
                        }
                        syncDirectory(directory)
                        require(destination.isFile && destination.length() == version.bytes)
                        val actual = MessageDigest.getInstance("SHA-256"); var reread = 0L
                        FileInputStream(destination).use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val size = input.read(buffer); if (size < 0) break
                                require(size.toLong() <= version.bytes - reread)
                                actual.update(buffer, 0, size); reread += size
                            }
                        }
                        require(reread == version.bytes && hex(actual.digest()) == version.sha256)
                    }
                    require(journal.load() == entry)
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (error: ErrnoException) { throw OfflineBackupValidationException("Der Dateirückfall konnte nicht abgeschlossen werden.").apply { initCause(error) } }
        catch (error: IllegalArgumentException) { throw OfflineBackupValidationException("Der Dateirückfall konnte nicht abgeschlossen werden.").apply { initCause(error) } }
        catch (_: IllegalStateException) { invalid() }
        catch (_: SecurityException) { invalid() }
        finally {
            withContext(NonCancellable + Dispatchers.IO) {
                work?.let { if (it.exists() && !it.deleteRecursively()) invalid() }
            }
        }
    }

    private fun syncDirectory(directory: File) {
        val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
        try { require(OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode)); Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun invalid(): Nothing = throw OfflineBackupValidationException(
        "App-eigene Dateien konnten nicht vollständig zurückgesetzt werden. Der Rückfall bleibt gesperrt und kann erneut versucht werden."
    )
}
