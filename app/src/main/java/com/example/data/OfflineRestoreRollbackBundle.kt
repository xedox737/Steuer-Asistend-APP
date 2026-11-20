package com.example.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.DataInputStream
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Prepares related private rollback artifacts. It never applies a restore or writes the journal. */
internal object OfflineRestoreRollbackBundle {
    data class Prepared(val id: String, val database: OfflineRestoreDatabaseSnapshot.Snapshot,
        val preferences: OfflineRestorePreferenceSnapshot.Snapshot,
        val ownedFiles: OfflineRestoreOwnedFilesSnapshot.Snapshot,
        val references: OfflineRestoreReferenceSnapshot.Snapshot,
        val credentials: OfflineRestoreCredentialSnapshot.Snapshot?,
        val manifest: File, val manifestBytes: Long, val manifestSha256: String)

    /** Caller owns the shared maintenance token through journal creation and all later mutation. */
    suspend fun prepare(context: Context, database: AppDatabase, token: OfflineBackupCoordinator.Maintenance, includeCredentials: Boolean = false): Prepared {
        val id = UUID.randomUUID().toString()
        var db: OfflineRestoreDatabaseSnapshot.Snapshot? = null
        var prefs: OfflineRestorePreferenceSnapshot.Snapshot? = null
        var files: OfflineRestoreOwnedFilesSnapshot.Snapshot? = null
        var references: OfflineRestoreReferenceSnapshot.Snapshot? = null
        var credentials: OfflineRestoreCredentialSnapshot.Snapshot? = null
        var manifest: File? = null
        var delivered = false
        try {
            val result = withContext(Dispatchers.IO) {
                OfflineBackupCoordinator.shared.requireMaintenance(token)
                token.suspending {
                    val fixedDb = OfflineRestoreDatabaseSnapshot.capture(context, database, token, id).also { db = it }
                    val fixedPrefs = OfflineRestorePreferenceSnapshot.capture(context, token, id).also { prefs = it }
                    val fixedFiles = OfflineRestoreOwnedFilesSnapshot.capture(context, token, id).also { files = it }
                    val fixedReferences = OfflineRestoreReferenceSnapshot.capture(context, database, token, id, fixedFiles).also { references = it }
                    val fixedCredentials = if (includeCredentials) OfflineRestoreCredentialSnapshot.capture(context, token, id).also { credentials = it } else null
                    currentCoroutineContext().ensureActive()
                    val record = JSONObject().put("version", 2).put("owner", "local-full-restore-rollback").put("id", id)
                        .put("databaseBytes", fixedDb.size).put("databaseSha256", fixedDb.sha256)
                        .put("preferencesBytes", fixedPrefs.size).put("preferencesSha256", fixedPrefs.sha256)
                        .put("filesManifestBytes", fixedFiles.manifestBytes).put("filesManifestSha256", fixedFiles.manifestSha256)
                        .put("fileCount", fixedFiles.versions.size)
                        .put("referencesManifestBytes", fixedReferences.manifestBytes).put("referencesManifestSha256", fixedReferences.manifestSha256)
                        .put("referenceCount", fixedReferences.references.size)
                        .put("unavailableCount", fixedReferences.references.count { it.state == OfflineRestoreReferenceSnapshot.State.UNAVAILABLE })
                        .put("credentialMode", if (includeCredentials) "RESTORE" else "UNCHANGED")
                    if (fixedCredentials != null) record.put("credentialBytes", fixedCredentials.size).put("credentialSha256", fixedCredentials.sha256)
                    val bytes = record.toString().toByteArray(Charsets.UTF_8)
                    require(bytes.size in 1..MAX_MANIFEST)
                    val file = File(context.noBackupFilesDir, "offline-backup-state/rollback-$id.bundle.json")
                    if (!file.createNewFile()) invalid("Das Rückfallverzeichnis ist bereits belegt.")
                    manifest = file
                    FileOutputStream(file).use { output -> output.write(bytes); output.fd.sync() }
                    val readback = ByteArray(bytes.size)
                    FileInputStream(file).use { input ->
                        DataInputStream(input).readFully(readback)
                        if (input.read() != -1) invalid("Das Rückfallverzeichnis enthält unerwartete zusätzliche Daten.")
                    }
                    val sha = hash(bytes)
                    if (readback.size != bytes.size || hash(readback) != sha || !readback.contentEquals(bytes))
                        invalid("Das Rückfallverzeichnis konnte nicht vollständig rückgelesen werden.")
                    currentCoroutineContext().ensureActive()
                    Prepared(id, fixedDb, fixedPrefs, fixedFiles, fixedReferences, fixedCredentials, file, bytes.size.toLong(), sha)
                }
            }
            delivered = true
            return result
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid("Der Rückfallstand konnte nicht vollständig vorbereitet werden.") }
        catch (_: IllegalArgumentException) { invalid("Der Rückfallstand enthält ungültige Angaben.") }
        catch (_: IllegalStateException) { invalid("Der Rückfallstand konnte nicht sicher vorbereitet werden.") }
        catch (_: SecurityException) { invalid("Der Zugriff auf den privaten Rückfallstand wurde verweigert.") }
        finally {
            if (!delivered) withContext(NonCancellable + Dispatchers.IO) {
                var failed = false
                fun remove(file: File?) { if (file != null && file.exists() && !file.deleteRecursively()) failed = true }
                remove(manifest); remove(credentials?.file); remove(references?.directory); remove(files?.directory); remove(prefs?.file)
                db?.file?.let { file ->
                    remove(file); remove(File(file.path + "-journal")); remove(File(file.path + "-wal")); remove(File(file.path + "-shm"))
                }
                if (failed) invalid("Unvollständige eigene Rückfalldaten konnten nicht vollständig entfernt werden.")
            }
        }
    }

    /** Discard only a complete, still-owned preparation that was never bound to a journal. */
    suspend fun discardUnjournalled(context: Context, token: OfflineBackupCoordinator.Maintenance,
        bundle: Prepared, plan: OfflineRestoreFilePlanStore.Snapshot) = withContext(Dispatchers.IO) {
        try {
            OfflineBackupCoordinator.shared.requireMaintenance(token)
            token.suspending {
                require(OfflineRestoreJournal(context).load() == null)
                require(UUID.fromString(bundle.id).toString() == bundle.id)
                require(listOf(bundle.database.id, bundle.preferences.id, bundle.ownedFiles.id,
                    bundle.references.id).all { it == bundle.id })
                require(bundle.credentials == null || bundle.credentials.id == bundle.id)
                OfflineRestoreFilePlanStore.verify(context, plan.operationId, plan.stagingId, plan.bytes, plan.sha256)
                val parent = File(context.noBackupFilesDir, "offline-backup-state").absoluteFile
                require(parent.isDirectory && parent == parent.canonicalFile)
                fun expected(actual: File, name: String): File {
                    val file = File(parent, name).absoluteFile
                    require(actual.absoluteFile == file && file == file.canonicalFile)
                    return file
                }
                fun checked(file: File, bytes: Long, sha: String) {
                    require(bytes >= 0 && sha.matches(Regex("[0-9a-f]{64}")) && file.isFile && file.length() == bytes)
                    val digest = MessageDigest.getInstance("SHA-256"); var read = 0L
                    FileInputStream(file).use { input -> val buffer = ByteArray(8192); while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        require(count.toLong() <= bytes - read); digest.update(buffer, 0, count); read += count
                    } }
                    require(read == bytes && file.length() == bytes && hashDigest(digest.digest()) == sha)
                }
                val id = bundle.id
                val db = expected(bundle.database.file, "rollback-$id.db")
                val prefs = expected(bundle.preferences.file, "rollback-$id.preferences.json")
                val manifest = expected(bundle.manifest, "rollback-$id.bundle.json")
                val files = expected(bundle.ownedFiles.directory, "rollback-$id.files")
                val refs = expected(bundle.references.directory, "rollback-$id.references")
                val planFile = expected(plan.file, "file-plan-${plan.stagingId}.json")
                val credentials = bundle.credentials?.let { expected(it.file, "rollback-$id.credentials.json") }
                val sidecars = listOf("-journal", "-wal", "-shm").map { File(db.path + it) }
                require(sidecars.none { it.exists() })
                checked(db, bundle.database.size, bundle.database.sha256)
                checked(prefs, bundle.preferences.size, bundle.preferences.sha256)
                checked(manifest, bundle.manifestBytes, bundle.manifestSha256)
                checked(planFile, plan.bytes, plan.sha256)
                credentials?.let { checked(it, requireNotNull(bundle.credentials).size, bundle.credentials.sha256) }
                fun child(dir: File, name: String): File = File(dir, name).absoluteFile.also {
                    require(it.parentFile == dir && it == it.canonicalFile)
                }
                val ownedFiles = bundle.ownedFiles.versions.map { version ->
                    require(version.stagedName.matches(Regex("file-[0-9]+")))
                    child(files, version.stagedName).also { checked(it, version.bytes, version.sha256) }
                }
                val referenceFiles = bundle.references.references.filter { it.state == OfflineRestoreReferenceSnapshot.State.EXTERNAL }.map { reference ->
                    val name = requireNotNull(reference.stagedName); require(name.matches(Regex("external-[0-9]+")))
                    child(refs, name).also { checked(it, requireNotNull(reference.bytes), requireNotNull(reference.sha256)) }
                }
                val fileManifest = child(files, "manifest.json")
                val referenceManifest = child(refs, "manifest.json")
                checked(fileManifest, bundle.ownedFiles.manifestBytes, bundle.ownedFiles.manifestSha256)
                checked(referenceManifest, bundle.references.manifestBytes, bundle.references.manifestSha256)
                require(files.isDirectory && refs.isDirectory)
                require(files.list()?.toSet() == (ownedFiles.map { it.name } + fileManifest.name).toSet())
                require(refs.list()?.toSet() == (referenceFiles.map { it.name } + referenceManifest.name).toSet())
                require(ownedFiles.map { it.name }.toSet().size == ownedFiles.size)
                require(referenceFiles.map { it.name }.toSet().size == referenceFiles.size)
                require(OfflineRestoreJournal(context).load() == null)
                (ownedFiles + referenceFiles + listOf(fileManifest, referenceManifest)).forEach { require(it.delete()) }
                require(files.delete() && refs.delete())
                (listOfNotNull(credentials) + listOf(db, prefs, manifest, planFile)).forEach { require(it.delete()) }
            }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid("Die ungebundenen Rückfalldaten konnten nicht sicher entfernt werden.") }
        catch (_: IllegalArgumentException) { invalid("Die ungebundenen Rückfalldaten konnten nicht sicher entfernt werden.") }
        catch (_: IllegalStateException) { invalid("Die ungebundenen Rückfalldaten konnten nicht sicher entfernt werden.") }
        catch (_: SecurityException) { invalid("Die ungebundenen Rückfalldaten konnten nicht sicher entfernt werden.") }
    }
    private fun hashDigest(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun invalid(message: String): Nothing = throw OfflineBackupValidationException(message)
    private const val MAX_MANIFEST = 16 * 1024
}
