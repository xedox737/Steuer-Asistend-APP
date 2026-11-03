package com.example.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File

/** LOCAL_ONLY recovery facts, excluded from Android/Drive backups. No document bytes, paths,
 * account names or credentials. A root ID and file ID bind a fact to one owned cloud object. */
internal class DrivePendingWrites(context: Context) {
    private val directory = File(context.noBackupFilesDir, "drive-pending-writes")
    data class OriginalChange(val beforeSha256: String, val afterSha256: String)

    private fun record(root: String, id: String): AtomicFile {
        require(root.isNotBlank() && id.isNotBlank())
        val key = StableDocumentIdentity.sha256("$root\u0000$id".toByteArray(Charsets.UTF_8))
        check(directory.isDirectory || directory.mkdirs())
        return AtomicFile(File(directory, "$key.json"))
    }

    fun originalChange(root: String, id: String): OriginalChange? = synchronized(lock) {
        val file = record(root, id)
        val bytes = try { file.openRead().use { it.readBytes() } } catch (_: java.io.FileNotFoundException) { return@synchronized null }
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        check(json.getString("root") == root && json.getString("file") == id && json.getInt("version") == 1)
        OriginalChange(json.getString("before"), json.getString("after")).also {
            check(it.beforeSha256.matches(SHA) && it.afterSha256.matches(SHA))
        }
    }

    fun rememberOriginalChange(root: String, id: String, before: String, after: String) = synchronized(lock) {
        require(before.matches(SHA) && after.matches(SHA))
        val file = record(root, id)
        val bytes = JSONObject().put("version", 1).put("root", root).put("file", id)
            .put("before", before).put("after", after).toString().toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Throwable) { file.failWrite(stream); throw error }
    }

    fun completed(root: String, id: String) = synchronized(lock) { record(root, id).delete() }

    private fun backupBinding(document: ManagedDocument): String = StableDocumentIdentity.sha256(
        com.example.api.canonicalDriveJson(JSONObject().put("documentId", document.documentId)
            .put("propertyId", document.propertyId).put("unitId", document.unitId ?: JSONObject.NULL)
            .put("receiptInternalId", document.receiptInternalId ?: JSONObject.NULL)
            .put("fileId", document.driveFileId).put("folderId", document.driveFolderId)
            .put("filename", document.storedFilename).put("mime", document.mimeType)).toByteArray(Charsets.UTF_8))

    /** Separate from receipt acknowledgement: both dependent backup manifests still need these
     * exact own-write preimages. Never promote a legacy hash-only intent into this authority. */
    fun backupOriginalPreimages(root: String, document: ManagedDocument, hash: String, size: Long): Set<DriveBackupConflictGuard.OriginalPreimage> = synchronized(lock) {
        val id = requireNotNull(document.driveFileId)
        val file = record("backup-original:$root", id)
        val json = try { file.openRead().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) } }
            catch (_: java.io.FileNotFoundException) { return@synchronized emptySet() }
        check(json.getInt("version") == 1 && json.getString("root") == root && json.getString("file") == id)
        if (json.getString("binding") != backupBinding(document) || json.getString("after") != hash ||
            json.getLong("afterSize") != size) return@synchronized emptySet()
        val preimages = json.getJSONArray("preimages")
        (0 until preimages.length()).map { index -> preimages.getJSONObject(index).let {
            DriveBackupConflictGuard.OriginalPreimage(it.getString("sha256"), it.getLong("size"))
        } }.toSet().also { values -> check(values.isNotEmpty() && values.all { it.sha256.matches(SHA) && it.size > 0 }) }
    }

    fun rememberBackupOriginalChange(root: String, document: ManagedDocument, beforeHash: String, beforeSize: Long,
                                     afterHash: String, afterSize: Long) = synchronized(lock) {
        require(beforeHash.matches(SHA) && afterHash.matches(SHA) && beforeSize > 0 && afterSize > 0)
        val preimages = backupOriginalPreimages(root, document, beforeHash, beforeSize) +
            DriveBackupConflictGuard.OriginalPreimage(beforeHash, beforeSize)
        val id = requireNotNull(document.driveFileId)
        val file = record("backup-original:$root", id)
        val bytes = JSONObject().put("version", 1).put("root", root).put("file", id)
            .put("binding", backupBinding(document)).put("after", afterHash).put("afterSize", afterSize)
            .put("preimages", org.json.JSONArray(preimages.map { JSONObject().put("sha256", it.sha256).put("size", it.size) }))
            .toString().toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Throwable) { file.failWrite(stream); throw error }
    }

    fun completedBackupOriginal(root: String, id: String) = synchronized(lock) {
        record("backup-original:$root", id).delete()
    }
    fun receiptIndexPending(metadataId: String): Boolean = synchronized(lock) {
        val file = record("receipt-index-pending", metadataId)
        try { file.openRead().use { input -> check(input.read() == 1) }; true }
        catch (_: java.io.FileNotFoundException) { false }
    }
    fun rememberReceiptIndexPending(metadataId: String) = synchronized(lock) {
        val file = record("receipt-index-pending", metadataId)
        val stream = file.startWrite()
        try { stream.write(1); file.finishWrite(stream) }
        catch (error: Throwable) { file.failWrite(stream); throw error }
    }
    fun completedReceiptIndex(metadataId: String) = synchronized(lock) { record("receipt-index-pending", metadataId).delete() }

    data class Creation(val fileId: String, val hashes: Set<String>)
    /** Read existing evidence only. A missing journal must never allocate/adopt an ID. */
    fun pendingCreation(scope: String, key: String): Creation? = synchronized(lock) {
        val file = record(scope, "create:$key")
        val previous = try { file.openRead().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) } }
            catch (_: java.io.FileNotFoundException) { return@synchronized null }
        check(previous.getInt("version") == 1 && previous.getString("scope") == scope && previous.getString("key") == key)
        val id = previous.getString("file")
        val array = previous.getJSONArray("hashes")
        val hashes = (0 until array.length()).map { array.getString(it) }.toSet()
        check(id.matches(Regex("[A-Za-z0-9_-]{1,256}")) && hashes.isNotEmpty() && hashes.all { it.matches(SHA) })
        Creation(id, hashes)
    }
    fun jsonBaseline(id: String): String? = synchronized(lock) {
        val file = record("json-baseline", id)
        val json = try { file.openRead().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) } }
            catch (_: java.io.FileNotFoundException) { return@synchronized null }
        check(json.getString("file") == id && json.getInt("version") == 1)
        json.getString("sha256").also { check(it.matches(SHA)) }
    }

    fun rememberJsonBaseline(id: String, hash: String) = synchronized(lock) {
        require(hash.matches(SHA))
        if (jsonBaseline(id) == hash) return@synchronized
        val file = record("json-baseline", id)
        val bytes = JSONObject().put("version", 1).put("file", id).put("sha256", hash).toString().toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Throwable) { file.failWrite(stream); throw error }
    }

    fun creation(scope: String, key: String, desiredHash: String, allocate: () -> String): Creation = synchronized(lock) {
        require(desiredHash.matches(SHA))
        val file = record(scope, "create:$key")
        val previous = pendingCreation(scope, key)
        val id = previous?.fileId ?: allocate()
        require(id.matches(Regex("[A-Za-z0-9_-]{1,256}")))
        val hashes = previous?.hashes.orEmpty()
        check(hashes.all { it.matches(SHA) })
        val allowed = hashes + desiredHash
        val bytes = JSONObject().put("version", 1).put("scope", scope).put("key", key).put("file", id)
            .put("hashes", org.json.JSONArray(allowed.toList())).toString().toByteArray(Charsets.UTF_8)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (error: Throwable) { file.failWrite(stream); throw error }
        Creation(id, allowed)
    }
    companion object {
        private val lock = Any()
        private val SHA = Regex("[a-f0-9]{64}")
    }
}
