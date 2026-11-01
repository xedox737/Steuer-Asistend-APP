package com.example.api

import com.example.BuildConfig
import com.example.data.DriveAppConfig
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import org.json.JSONObject
import java.io.IOException

/** A shared production/test fence at the existing HTTP boundary, including legacy IDs and cleanup paths.
 * Only ancestry metadata is read outside the namespace, never file content or a mutation.
 * No persistent ownership cache: a moved folder or account change must be checked again.
 */
internal class DriveReadProof(val authorization: String) {
    val metadata = java.util.concurrent.ConcurrentHashMap<String, JSONObject>()
    val metadataEtags = java.util.concurrent.ConcurrentHashMap<String, String>()
    var syncCacheEnabled = false
    val syncJson = java.util.concurrent.ConcurrentHashMap<String, DriveJsonSnapshot>()
    val syncOriginals = java.util.concurrent.ConcurrentHashMap<String, DriveOriginalFingerprint>()
    // Positive lookups only, scoped to this account-bound run; never proof for a mutation.
    val syncEntities = java.util.concurrent.ConcurrentHashMap<String, DriveFile>()
    val roots = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    val previousMetadata = java.util.concurrent.ConcurrentLinkedQueue<JSONObject>()
    val previousRoots = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile var ownershipFailure: DriveAccessFailure? = null
    fun recordFailure(error: DriveAccessFailure) {
        if (error.reason in setOf(DriveAccessFailure.Reason.NAMESPACE, DriveAccessFailure.Reason.INVALID_ID,
                DriveAccessFailure.Reason.INVALID_METADATA, DriveAccessFailure.Reason.WRONG_LOCATION,
                DriveAccessFailure.Reason.WRONG_MIME) || error.httpStatus in setOf(401, 403)) ownershipFailure = error
    }
    fun clear() {
        // Invalidating a reusable proof must retain the objects already used by this result.
        previousMetadata.addAll(metadata.values); previousRoots.addAll(roots)
        metadata.clear(); metadataEtags.clear(); roots.clear()
    }
}

internal class DriveNamespacePolicy(
    applicationId: String,
    val rootFolderName: String = rootFor(applicationId)
) {
    val isIsolatedTest = applicationId == TEST_PACKAGE
    private val forbiddenRoot get() = if (isIsolatedTest) DriveEnvironment.PRODUCTION_ROOT else TEST_ROOT
    val appIdentifier = if (isIsolatedTest) TEST_APP_IDENTIFIER else DriveEnvironment.PRODUCTION_APP_IDENTIFIER
    init {
        if (rootFolderName != rootFor(applicationId)) {
            throw IOException("Drive-Sicherheitsabbruch: App-Identität und Cloud-Hauptordner stimmen nicht überein. Kein Zugriff auf den anderen Datenbestand.")
        }
    }

    fun execute(request: Request, send: (Request) -> Response): Response = execute(request, send, null)

    fun execute(request: Request, send: (Request) -> Response, readProof: DriveReadProof?): Response {
        val token = request.header("Authorization") ?: denied()
        val scope = readProof?.takeIf { it.authorization == token }
        val mutation = request.method != "GET"
        if (mutation) readProof?.clear()
        try {
            val url = request.url
            if (url.host != "www.googleapis.com" || !url.isHttps) denied()
            val path = url.encodedPath
            val base = if (path.startsWith("/upload/")) "/upload/drive/v3/files" else "/drive/v3/files"
            if (path != base && !path.startsWith("$base/")) denied()
            val id = path.removePrefix(base).removePrefix("/")
            // Opaque unused IDs carry no account file data. Creation still passes the ordinary
            // fresh namespace/parent fence, including when the same ID is retried after a crash.
            if (path == "/drive/v3/files/generateIds" && request.method == "GET" &&
                url.queryParameter("count") == "1" && url.queryParameter("space") == "drive" &&
                url.queryParameter("type") == "files") return send(request)
            if (id.isNotEmpty()) {
                val meta = owned(id, token, send, scope?.metadata ?: mutableMapOf(),
                    scope?.roots ?: mutableSetOf(), freshTarget = true)
                // The root itself is only a container; do not rename, move, overwrite or delete it.
                if (request.method != "GET" && protectedContainer(meta, token, send)) denied()
                if (meta.optString("mimeType") == FOLDER && url.queryParameter("uploadType") == "media") denied()
                if (meta.optString("mimeType") == FOLDER && request.method == "DELETE") {
                    val q = java.net.URLEncoder.encode("'${meta.getString("id")}' in parents", "UTF-8")
                    val children = Request.Builder().url("https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id)&pageSize=1")
                        .header("Authorization", token).build()
                    send(children).use {
                        if (!it.isSuccessful || JSONObject(it.body?.string().orEmpty()).getJSONArray("files").length() != 0) denied()
                    }
                }
                url.queryParameter("addParents")?.split(',')?.forEach { folder(it, token, send) }
                url.queryParameter("removeParents")?.let {
                    it.split(',').forEach { parent -> folder(parent, token, send) }
                    if (url.queryParameter("addParents").isNullOrBlank()) denied()
                }
                if (request.method == "PATCH" && url.queryParameter("uploadType") != "media") {
                    val json = metadata(request)
                    tag(json)
                    if (meta.optString("mimeType") == FOLDER && json.optString("name") == forbiddenRoot) denied()
                    if (json.has("parents")) parents(json, token, send)
                    if (json.has("shortcutDetails")) denied()
                }
            } else if (request.method == "GET") {
                val q = url.queryParameter("q") ?: denied()
                val expectedRoot = "name = '$rootFolderName' and mimeType = '$FOLDER' and trashed = false and 'root' in parents"
                if (q != expectedRoot) {
                    // All other searches must have an owned parent, combined only with AND.
                    val unquoted = q.replace(Regex("'(?:\\\\.|[^'\\\\])*'"), "''")
                    if (Regex("\\b(or|not)\\b", RegexOption.IGNORE_CASE).containsMatchIn(unquoted)) denied()
                    val ids = Regex("'([A-Za-z0-9_-]+)' in parents").findAll(q).map { it.groupValues[1] }.toList()
                    if (ids.isEmpty()) denied()
                    ids.forEach { folder(it, token, send, scope) }
                }
            } else if (request.method == "POST") {
                val json = metadata(request)
                tag(json)
                if (json.optString("mimeType") == FOLDER && json.optString("name") == forbiddenRoot) denied()
                if (!json.has("parents")) {
                    if (json.optString("name") != rootFolderName || json.optString("mimeType") != FOLDER || json.has("shortcutDetails")) denied()
                } else parents(json, token, send)
            } else denied()
            return send(request)
        } catch (error: DriveAccessFailure) {
            scope?.recordFailure(error)
            throw error
        } finally {
            if (mutation) readProof?.clear()
        }
    }

    /** One current metadata GET supplies both the ownership proof and the caller's projection. */
    fun readOwnedMetadata(id: String, token: String, send: (Request) -> Response, readProof: DriveReadProof?): DriveOwnedMetadata {
        val authorization = "Bearer $token"
        val scope = readProof?.takeIf { it.authorization == authorization }
        var etag: String? = null
        try {
            val metadata = owned(id, authorization, { request -> send(request).also { response ->
                if (request.url.encodedPath.endsWith("/$id")) etag = response.header("ETag")
            } }, scope?.metadata ?: mutableMapOf(), scope?.roots ?: mutableSetOf(), freshTarget = true)
            return DriveOwnedMetadata(metadata, etag)
        } catch (error: DriveAccessFailure) { scope?.recordFailure(error);throw error }
    }

    /** Re-attest every observed object and its ancestry before publishing a read result. */
    fun verifyReadProof(scope: DriveReadProof, send: (Request) -> Response) {
        scope.ownershipFailure?.let { throw it }
        val observed = (scope.previousMetadata.toList() + scope.metadata.values.toList()).groupBy { it.getString("id") }
        val roots = scope.previousRoots + scope.roots
        fun compare(previous: JSONObject, current: JSONObject) {
            if (listOf("name", "mimeType").any { previous.getString(it) != current.getString(it) } ||
                previous.getJSONArray("parents").toString() != current.getJSONArray("parents").toString()) denied()
        }
        roots.forEach { id ->
            val current = readMetadata(id, scope.authorization, send)
            observed[id].orEmpty().forEach { compare(it, current) }
            if (!topLevelRoot(id, scope.authorization, send)) denied()
        }
        // A parent-scoped FileList supplies the same current object evidence in one projection.
        // An omitted ID, changed ancestry, shortcut/tag/role or incomplete page still fails closed.
        val children = observed.filterKeys { it !in roots }.values.flatten()
            .groupBy { it.getJSONArray("parents").getString(0) }
        children.forEach { (parent, previous) ->
            val needed = previous.map { it.getString("id") }.toSet()
            val current = mutableMapOf<String, JSONObject>()
            var page: String? = null
            var complete = false
            repeat(100) {
                if (!complete) {
                    val url = okhttp3.HttpUrl.Builder().scheme("https").host("www.googleapis.com").addPathSegments("drive/v3/files")
                        .addQueryParameter("q", "'$parent' in parents and trashed = false")
                        .addQueryParameter("fields", "nextPageToken,incompleteSearch,files(id,name,mimeType,parents,trashed,shortcutDetails,appProperties)")
                        .addQueryParameter("pageSize", "1000").apply { page?.let { addQueryParameter("pageToken", it) } }.build()
                    val json = readResponse(send(Request.Builder().url(url).header("Authorization", scope.authorization).build()))
                    if (json.optBoolean("incompleteSearch")) throw DriveAccessFailure(DriveAccessFailure.Reason.INCOMPLETE_SEARCH)
                    val files = json.driveFileListItems()
                    for (i in 0 until files.length()) {
                        val file = files.getJSONObject(i)
                        val id = file.getString("id")
                        if (id in needed) {
                            validateMetadata(id, file)
                            if (current.put(id, file) != null) throw DriveAccessFailure(DriveAccessFailure.Reason.INVALID_METADATA)
                        }
                    }
                    page = json.optString("nextPageToken").takeIf(String::isNotBlank)
                    complete = current.keys.containsAll(needed) || page == null
                }
            }
            if (!complete) throw DriveAccessFailure(DriveAccessFailure.Reason.INCOMPLETE_SEARCH)
            previous.forEach { old -> compare(old, current[old.getString("id")] ?: denied()) }
        }
    }

    fun validateConfig(config: DriveAppConfig, token: String, rootId: String?, systemId: String?, send: (Request) -> Response) =
        validateConfig(config, token, rootId, systemId, send, null)

    fun validateConfig(config: DriveAppConfig, token: String, rootId: String?, systemId: String?, send: (Request) -> Response,
        readProof: DriveReadProof?) {
        if (config.appIdentifier != appIdentifier) denied()
        if (listOf(config.rootFolderId, config.systemFolderId, config.receiptsFolderId, config.paymentsFolderId,
                config.exportsFolderId, config.transactionsFolderId, config.backupsFolderId).distinct().size != 7) denied()
        val auth = "Bearer $token"
        val inherited = readProof?.takeIf { it.authorization == auth }
        val scope = inherited ?: DriveReadProof(auth)
        val root = owned(config.rootFolderId, auth, send, scope.metadata,
            scope.roots, freshTarget = true)
        if (root.getString("name") != rootFolderName || (rootId != null && rootId != config.rootFolderId)) denied()
        fun child(id: String, name: String, parent: String) {
            val meta = folder(id, auth, send, scope)
            if (meta.getString("name") != name || meta.getJSONArray("parents").getString(0) != parent) denied()
        }
        if (systemId != null && config.systemFolderId != systemId) denied()
        child(config.systemFolderId, "_BelegApp-Daten", config.rootFolderId)
        mapOf(config.receiptsFolderId to "receipts", config.paymentsFolderId to "payments", config.exportsFolderId to "exports",
            config.transactionsFolderId to "transactions", config.backupsFolderId to "backups").forEach { (id, name) ->
            child(id, name, config.systemFolderId)
        }
        if (inherited == null) verifyReadProof(scope, send)
    }

    fun validateJsonReferences(json: String, token: String, send: (Request) -> Response) =
        validateReferences(json, token, send, null, null)

    /** Restore may retain unavailable originals as references; this never permits content access
     * or a write to an unproved ID. Missing metadata, folders and ancestors remain fatal. */
    fun validateRestoreJsonReferences(json: String, token: String, send: (Request) -> Response): Set<String> =
        linkedSetOf<String>().also { validateReferences(json, token, send, it, null) }

    fun validateJsonReferences(json: String, token: String, send: (Request) -> Response, scope: DriveReadProof?) =
        validateReferences(json, token, send, null, scope)

    fun validateRestoreJsonReferences(json: String, token: String, send: (Request) -> Response, scope: DriveReadProof?): Set<String> =
        linkedSetOf<String>().also { validateReferences(json, token, send, it, scope) }

    /** Only an already-read source may retain the same missing original at the same entity/field.
     * This never authorizes a content/file operation on that missing ID or a new reference. */
    fun validatePreservedJsonReferences(json: String, token: String, previous: DriveRestoreJsonRead, send: (Request) -> Response,
                                      syncScope: DriveReadProof? = null) {
        val root = org.json.JSONTokener(previous.json).nextValue()
        val deleted = (root as? JSONObject)?.optJSONArray("entries")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.filter { it.optString("syncStatus") == "DELETED" }.toSet()
        }.orEmpty()
        val preserved = mutableMapOf<String, String>()
        val deletedLocations = hashSetOf<String>()
        visitDriveReferences(root) { key, id, owner, location ->
            if ((key in setOf("driveFileId", "mainDriveFileId") && id in previous.unavailableOriginalIds) ||
                (owner in deleted && key in setOf("metadataFileId", "mainDriveFileId"))) {
                if (preserved.put(location, id) != null) throw DriveAccessFailure(DriveAccessFailure.Reason.INVALID_METADATA)
                if (owner in deleted) deletedLocations += location
            }
        }
        val fence = syncScope?.takeIf { it.syncCacheEnabled && it.authorization == "Bearer $token" }?.let { existing ->
            DriveReadProof(existing.authorization).apply {
                // Observations identify what must be checked, never grant write authority.
                (existing.previousMetadata.toList() + existing.metadata.values).forEach { metadata[it.getString("id")] = it }
                roots.addAll(existing.previousRoots + existing.roots)
                ownershipFailure = existing.ownershipFailure
            }
        }
        validateReferences(json, token, send, null, fence, preserved, deletedLocations)
        // Fresh parent-scoped projections re-attest every reused reference AND ancestor before
        // PATCH. A move, missing ancestor, foreign tag or incomplete listing still blocks writes.
        if (fence != null) verifyReadProof(fence, send)
    }

    private fun validateReferences(json: String, token: String, send: (Request) -> Response,
        unavailableOriginals: MutableSet<String>?, readProof: DriveReadProof?, preserved: Map<String, String> = emptyMap(), deletedLocations: Set<String> = emptySet()) {
        data class Reference(val id: String, val folder: Boolean, val historyOnly: Boolean, val original: Boolean, val retainedMissing: Boolean)
        val references = mutableSetOf<Reference>()
        val historical = setOf("previousMainDriveFileId", "previousMetadataFileId", "legacyDriveFolderId")
        val root = org.json.JSONTokener(json).nextValue()
        // Only the two diagnostic file fields directly inside a complete app config are optional.
        // This does not permit a missing current receipt, folder, ancestor or nested lookalike.
        val configFolders = setOf("rootFolderId", "systemFolderId", "receiptsFolderId", "paymentsFolderId",
            "exportsFolderId", "transactionsFolderId", "backupsFolderId")
        val diagnosticReferences = (root as? JSONObject)?.takeIf { config ->
            config.optString("appIdentifier") == appIdentifier && config.optInt("schemaVersion") == 1 &&
                configFolders.all { config.opt(it) is String && config.getString(it).isNotBlank() }
        }?.optJSONObject("testReferences")
        val deletedEntries = (root as? JSONObject)?.optJSONArray("entries")?.let { array ->
            (0 until array.length()).mapNotNull { array.optJSONObject(it) }
                .filter { it.optString("syncStatus") == "DELETED" }.toSet()
        }.orEmpty()
        val locations = hashSetOf<String>()
        visitDriveReferences(root) { key, item, owner, location ->
            if (preserved.isNotEmpty() && !locations.add(location)) throw DriveAccessFailure(DriveAccessFailure.Reason.INVALID_METADATA)
            val optionalDiagnostic = owner === diagnosticReferences && key in setOf("mainDriveFileId", "metadataFileId")
            val deletedReference = unavailableOriginals != null && owner in deletedEntries && key in setOf("metadataFileId", "mainDriveFileId")
            references += Reference(item, key.endsWith("FolderId"),
                key in historical || optionalDiagnostic || deletedReference,
                key in setOf("driveFileId", "mainDriveFileId"), preserved[location] == item &&
                    (location !in deletedLocations || owner in deletedEntries))
        }
        // A bounded read attempt may share proof; its caller re-attests all observed objects on exit.
        val scope = readProof?.takeIf { it.authorization == "Bearer $token" }
        val proof = scope?.metadata ?: mutableMapOf<String, JSONObject>()
        val rootProofs = scope?.roots ?: mutableSetOf<String>()
        references.forEach { (id, folderExpected, historyOnly, original, retainedMissing) ->
            try {
                val meta = owned(id, "Bearer $token", send, proof, rootProofs)
                if ((meta.optString("mimeType") == FOLDER) != folderExpected) denied()
            } catch (missing: MissingReference) {
                // Tombstones and optional diagnostic files may already be absent. No access is granted.
                // An ancestor 404 is never recoverable. Ordinary reads/writes also reject current-file 404s.
                if (missing.id != id) throw missing
                if (!historyOnly && !(retainedMissing && !folderExpected)) {
                    if (unavailableOriginals == null || !original || folderExpected) throw missing
                    unavailableOriginals += id
                }
            } catch (error: DriveAccessFailure) {
                scope?.recordFailure(error)
                throw error
            }
        }
    }

    private fun protectedContainer(meta: JSONObject, token: String, send: (Request) -> Response): Boolean {
        if (meta.optString("mimeType") != FOLDER) return false
        if (meta.getString("name") == rootFolderName) return true
        val parent = readMetadata(meta.getJSONArray("parents").getString(0), token, send)
        if (meta.getString("name") == "_BelegApp-Daten" && parent.optString("name") == rootFolderName) return true
        return parent.optString("name") == "_BelegApp-Daten" && meta.getString("name") in
            setOf("receipts", "payments", "exports", "transactions", "backups", "deletions")
    }

    private fun metadata(request: Request): JSONObject {
        val buffer = Buffer()
        request.body?.writeTo(buffer) ?: denied()
        val text = if (request.body?.contentType()?.type == "multipart") {
            // Only the metadata part, never decode or log original document bytes.
            buffer.readUtf8LineStrict() // initial empty line
            buffer.readUtf8LineStrict() // boundary
            buffer.readUtf8LineStrict() // metadata content type
            buffer.readUtf8LineStrict() // empty line
            buffer.readUtf8LineStrict()
        } else buffer.readUtf8()
        return JSONObject(text)
    }

    private fun tag(json: JSONObject) {
        val name = json.optJSONObject("appProperties")?.optString("appName").orEmpty()
        if (name.isNotBlank() && name != appIdentifier) denied()
    }

    private fun parents(json: JSONObject, token: String, send: (Request) -> Response) {
        if (json.has("shortcutDetails") || json.optString("mimeType") == "application/vnd.google-apps.shortcut") denied()
        val ids = json.optJSONArray("parents") ?: denied()
        if (ids.length() != 1) denied()
        folder(ids.getString(0), token, send)
    }

    private fun folder(id: String, token: String, send: (Request) -> Response, scope: DriveReadProof? = null): JSONObject =
        owned(id, token, send, scope?.metadata ?: mutableMapOf(), scope?.roots ?: mutableSetOf(), freshTarget = true).also {
        if (it.optString("mimeType") != FOLDER) denied()
    }

    private fun owned(id: String, token: String, send: (Request) -> Response,
        proof: MutableMap<String, JSONObject> = mutableMapOf(),
        rootProofs: MutableSet<String> = mutableSetOf(), freshTarget: Boolean = false): JSONObject {
        fun read(key: String) = proof.getOrPut(key) { readMetadata(key, token, send) }
        val original = if (freshTarget) readMetadata(id, token, send).also { proof[id] = it } else read(id)
        var current = original
        val visited = mutableSetOf<String>()
        repeat(100) {
            if (!visited.add(current.getString("id"))) denied()
            val parents = current.optJSONArray("parents") ?: denied()
            if (parents.length() != 1) denied()
            val parentId = parents.getString(0)
            if (current.optString("name") == rootFolderName && current.optString("mimeType") == FOLDER) {
                // drive.file need not expose files.get("root"). Prove top-level membership
                // with the same server-filtered root search used during discovery instead.
                val rootId = current.getString("id")
                if (rootId !in rootProofs) {
                    if (!topLevelRoot(rootId, token, send)) denied()
                    rootProofs += rootId
                }
                return original
            }
            current = read(parentId)
            if (current.optString("mimeType") != FOLDER) denied()
        }
        denied()
    }

    private fun topLevelRoot(id: String, token: String, send: (Request) -> Response): Boolean {
        val query = "name = '$rootFolderName' and mimeType = '$FOLDER' and trashed = false and 'root' in parents"
        var page: String? = null
        repeat(100) {
            val url = okhttp3.HttpUrl.Builder().scheme("https").host("www.googleapis.com")
                .addPathSegments("drive/v3/files").addQueryParameter("q", query)
                .addQueryParameter("fields", "nextPageToken,incompleteSearch,files(id)").addQueryParameter("pageSize", "1000")
                .apply { page?.let { addQueryParameter("pageToken", it) } }.build()
            val request = Request.Builder().url(url).header("Authorization", token).build()
            val json = readResponse(send(request))
            if (json.optBoolean("incompleteSearch")) throw DriveAccessFailure(DriveAccessFailure.Reason.INCOMPLETE_SEARCH)
            val files = json.driveFileListItems()
            if ((0 until files.length()).any { files.getJSONObject(it).optString("id") == id }) return true
            page = json.optString("nextPageToken").takeIf(String::isNotBlank)
            if (page == null) return false
        }
        throw DriveAccessFailure(DriveAccessFailure.Reason.INCOMPLETE_SEARCH)
    }

    private fun readResponse(response: Response): JSONObject = response.use {
        if (!it.isSuccessful) throw DriveAccessFailure(DriveAccessFailure.Reason.HTTP, it.code)
        try { JSONObject(it.body?.string().orEmpty()) }
        catch (_: org.json.JSONException) { throw DriveAccessFailure(DriveAccessFailure.Reason.INVALID_METADATA) }
    }

    private fun readMetadata(id: String, token: String, send: (Request) -> Response): JSONObject {
        if (!Regex("[A-Za-z0-9_-]+").matches(id)) throw DriveAccessFailure(DriveAccessFailure.Reason.INVALID_ID)
        val request = Request.Builder().url("https://www.googleapis.com/drive/v3/files/$id?fields=id,name,mimeType,size,parents,trashed,shortcutDetails,appProperties,sha256Checksum,version")
            .header("Authorization", token).build()
        val response = send(request)
        if (response.code == 404) { response.close(); throw MissingReference(id) }
        return readResponse(response).also { validateMetadata(id, it) }
    }

    private fun validateMetadata(id: String, metadata: JSONObject) {
        with(metadata) {
            if (optString("mimeType").isBlank() || optString("name").isBlank() || optString("id").isBlank())
                throw DriveAccessFailure(DriveAccessFailure.Reason.INVALID_METADATA)
            if (optBoolean("trashed") || has("shortcutDetails")) denied()
            val tag = optJSONObject("appProperties")?.optString("appName").orEmpty()
            if (tag.isNotBlank() && tag != appIdentifier) denied()
            if (optString("id") != id) denied()
        }
    }

    private class MissingReference(val id: String) : DriveAccessFailure(DriveAccessFailure.Reason.HTTP, 404)
    fun isMissingTarget(error: Throwable, fileId: String): Boolean = error is MissingReference && error.id == fileId

    private fun denied(): Nothing = throw DriveAccessFailure(DriveAccessFailure.Reason.NAMESPACE)

    companion object {
        const val TEST_PACKAGE = "com.aistudio.steuerassistent.vnhqkp.phase11a"
        const val TEST_ROOT = "ImmoPilot Phase11A Testdaten"
        const val TEST_APP_IDENTIFIER = "ImmoPilotPhase11ATest"
        private const val FOLDER = "application/vnd.google-apps.folder"
        private fun rootFor(applicationId: String): String = when (applicationId) {
            "com.aistudio.steuerassistent.vnhqkp" -> DriveEnvironment.PRODUCTION_ROOT
            TEST_PACKAGE -> TEST_ROOT
            else -> throw IOException("Drive-Sicherheitsabbruch: Unbekannte App-Identität.")
        }
    }
}
