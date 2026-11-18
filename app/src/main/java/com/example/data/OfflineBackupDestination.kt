package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Creates only new owned documents. Caller holds its generation throughout publication. */
internal class OfflineBackupDestination(private val context: Context, private val tree: Uri,
    private val backend: Backend = SafBackend(context)) {
    data class Access(val persistedReadWrite: Boolean, val directory: Boolean, val canCreate: Boolean, val availableBytes: Long?)
    data class Created(val uri: Uri, val canRename: Boolean)
    data class Published(val uri: Uri, val backupId: String, val bytes: Long, val sha256: String,
        val archiveInfo: OfflineBackupArchiveInfo, val summary: OfflineBackupSnapshot.Summary)
    enum class Stage { WRITING, VERIFYING, FINALIZING }
    interface Backend {
        fun access(tree: Uri): Access
        fun create(tree: Uri, name: String): Created
        fun write(uri: Uri): OutputStream
        fun read(uri: Uri): InputStream
        fun rename(uri: Uri, name: String): Uri?
        fun delete(uri: Uri): Boolean
    }

    suspend fun publish(source: OfflineBackupArchiveEngine.Prepared, generation: OfflineBackupKeyEnvelope.Generation,
        progress: (Stage) -> Unit = {}): Published = withContext(Dispatchers.IO) {
        var owned: Uri? = null; var directory: File? = null; var complete = false
        try {
            currentCoroutineContext().ensureActive()
            val bytes = source.file.length()
            require(source.file.isFile && bytes in 1..(2L * 1024 * 1024 * 1024 + 64L * 1024 * 1024))
            preflight(bytes)
            val parent = File(context.filesDir, "offline-backup-staging")
            require(parent.isDirectory || parent.mkdirs())
            OfflineBackupResourceBudget.requireAvailable(source.resources.copy(privateBytes = source.resources.privateBytes + bytes), parent.usableSpace)
            val run = File(parent, "destination-${UUID.randomUUID()}")
            require(run.mkdir()); directory = run
            val id = source.file.inputStream().use { OfflineBackupArchiveHeader.read(it).dataKey.archiveId }
            val pendingName = "ImmoPilot-$id-${UUID.randomUUID()}.partial"
            currentCoroutineContext().ensureActive()
            val created = backend.create(tree, pendingName)
            require(created.uri.scheme == "content" && created.uri.authority == tree.authority)
            owned = created.uri
            if (!created.canRename) fail("Dieser Ordneranbieter unterstützt keinen sicheren Abschluss durch Umbenennen.")
            currentCoroutineContext().ensureActive(); progress(Stage.WRITING)
            val sha = source.file.inputStream().use { input -> backend.write(created.uri).use { output ->
                copyExactly(input, output, bytes).also { output.flush() }
            } }
            currentCoroutineContext().ensureActive(); progress(Stage.VERIFYING)
            val reread = File(run, "target.immopilot")
            val targetSha = backend.read(created.uri).use { input -> FileOutputStream(reread).use { output ->
                copyExactly(input, output, bytes).also { output.fd.sync() }
            } }
            require(targetSha == sha)
            OfflineBackupArchiveValidator.verifyPrepared(context, reread, generation, source.summary)
            currentCoroutineContext().ensureActive(); progress(Stage.FINALIZING)
            val finalized = backend.rename(created.uri, "ImmoPilot-$id-${UUID.randomUUID()}.immopilot")
                ?: fail("Die geprüfte neue Sicherung konnte nicht fertiggestellt werden.")
            require(finalized.scheme == "content" && finalized.authority == tree.authority)
            owned = finalized
            // A provider may return a new URI when renaming. Read its complete final bytes too.
            val finalSha = backend.read(finalized).use { copyExactly(it, null, bytes) }
            require(finalSha == sha)
            currentCoroutineContext().ensureActive()
            complete = true
            Published(finalized, id, bytes, sha, source.archiveInfo, source.summary)
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: android.system.ErrnoException) { fail("Die neue Zieldatei konnte nicht sicher synchronisiert oder geschlossen werden.") }
        catch (_: IOException) { fail("Die neue Sicherung konnte im Ziel nicht vollständig geschrieben, geschlossen oder rückgelesen werden.") }
        catch (_: IllegalArgumentException) { fail("Die neue Zielsicherung stimmt nicht mit dem geprüften Archiv überein.") }
        catch (_: IllegalStateException) { fail("Das private Ziel-Rücklese-Staging ist nicht verfügbar.") }
        catch (_: SecurityException) { fail("Der Zugriff auf den Sicherungsordner wurde verweigert.") }
        finally {
            var cleanupFailed = false
            if (!complete) owned?.let { uri ->
                try { if (!backend.delete(uri)) cleanupFailed = true } catch (_: Exception) { cleanupFailed = true }
            }
            directory?.let { if (it.exists() && !it.deleteRecursively()) cleanupFailed = true }
            if (cleanupFailed) fail("Die neue unvollständige Sicherung oder ihr eigenes Arbeitsverzeichnis konnte nicht entfernt werden. Ältere Sicherungen wurden nicht gelöscht.")
        }
    }

    /** Read-only provider/space gate, called before snapshot work and again with measured sizes. */
    fun preflight(expectedCipherBytes: Long = 0) {
        try {
            require(expectedCipherBytes in 0..(2L * 1024 * 1024 * 1024 + 64L * 1024 * 1024))
            val access = backend.access(tree)
            if (!access.persistedReadWrite || !access.directory || !access.canCreate)
                fail("Der Sicherungsordner ist nicht mehr mit dauerhaften Lese- und Schreibrechten verfügbar.")
            val available = access.availableBytes?.takeIf { it >= 0 }
                ?: fail("Der Ordneranbieter meldet keinen zuverlässig bestimmbaren freien Zielplatz. Es wurde keine Zieldatei angelegt.")
            val needed = expectedCipherBytes + OfflineBackupResourceBudget.RESERVE_BYTES
            if (available < needed) fail("Zu wenig Zielspeicher: benötigt $needed Byte einschließlich Reserve, verfügbar $available Byte.")
        } catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { fail("Der Sicherungsordner konnte nicht geprüft werden.") }
        catch (_: SecurityException) { fail("Der Zugriff auf den Sicherungsordner wurde verweigert.") }
        catch (_: IllegalArgumentException) { fail("Der Sicherungsordner oder seine Kapazitätsangabe ist ungültig.") }
    }

    private suspend fun copyExactly(input: InputStream, output: OutputStream?, expected: Long): String {
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(8192); var bytes = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            require(count.toLong() <= expected - bytes)
            output?.write(buffer, 0, count); digest.update(buffer, 0, count); bytes += count
        }
        require(bytes == expected)
        return digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }

    private class SafBackend(private val context: Context) : Backend {
        private val resolver get() = context.contentResolver
        private fun document(tree: Uri) = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        override fun access(tree: Uri): Access {
            if (!DocumentsContract.isTreeUri(tree) || resolver.persistedUriPermissions.none {
                    it.uri == tree && it.isReadPermission && it.isWritePermission }) return Access(false, false, false, null)
            val uri = document(tree)
            val flags = resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) null else cursor.getString(0) to cursor.getInt(1)
            } ?: return Access(true, false, false, null)
            // Only a real directory FD provides trustworthy filesystem capacity; pipes do not.
            val space = try {
                resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    if (!OsConstants.S_ISDIR(Os.fstat(descriptor.fileDescriptor).st_mode)) null else {
                        val stat = Os.fstatvfs(descriptor.fileDescriptor)
                        if (stat.f_bavail < 0 || stat.f_frsize <= 0) null else Math.multiplyExact(stat.f_bavail, stat.f_frsize)
                    }
                }
            } catch (_: Exception) { null }
            return Access(true, flags.first == DocumentsContract.Document.MIME_TYPE_DIR,
                flags.second and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0, space)
        }
        override fun create(tree: Uri, name: String): Created {
            val uri = DocumentsContract.createDocument(resolver, document(tree), "application/octet-stream", name)
                ?: fail("Der Ordneranbieter hat keine neue Sicherungsdatei angelegt.")
            val rename = try {
                resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use {
                    it.moveToFirst() && it.getInt(0) and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0
                } ?: false
            } catch (_: Exception) { false }
            return Created(uri, rename)
        }
        override fun write(uri: Uri): OutputStream {
            val descriptor = resolver.openFileDescriptor(uri, "w") ?: fail("Die neue Zieldatei konnte nicht geöffnet werden.")
            val output = android.os.ParcelFileDescriptor.AutoCloseOutputStream(descriptor)
            return object : FilterOutputStream(output) {
                override fun write(bytes: ByteArray, offset: Int, length: Int) = out.write(bytes, offset, length)
                override fun close() {
                    try {
                        flush()
                        if (OsConstants.S_ISREG(Os.fstat(descriptor.fileDescriptor).st_mode)) descriptor.fileDescriptor.sync()
                    } finally { out.close() }
                }
            }
        }
        override fun read(uri: Uri): InputStream = resolver.openInputStream(uri) ?: fail("Die neue Zieldatei konnte nicht rückgelesen werden.")
        override fun rename(uri: Uri, name: String): Uri? = DocumentsContract.renameDocument(resolver, uri, name)
        override fun delete(uri: Uri) = DocumentsContract.deleteDocument(resolver, uri)
    }
    private companion object {
        fun fail(message: String): Nothing = throw OfflineBackupValidationException(message)
    }
}
