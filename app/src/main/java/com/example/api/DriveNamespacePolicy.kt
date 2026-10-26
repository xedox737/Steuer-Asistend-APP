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

    fun execute(request: Request, send: (Request) -> Response): Response {
        val token = request.header("Authorization") ?: denied()
        val url = request.url
        if (url.host != "www.googleapis.com" || !url.isHttps) denied()
        val path = url.encodedPath
        val base = if (path.startsWith("/upload/")) "/upload/drive/v3/files" else "/drive/v3/files"
        if (path != base && !path.startsWith("$base/")) denied()
        val id = path.removePrefix(base).removePrefix("/")
        if (id.isNotEmpty()) {
            val meta = owned(id, token, send)
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
                ids.forEach { folder(it, token, send) }
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
    }

    fun validateConfig(config: DriveAppConfig, token: String, rootId: String?, systemId: String?, send: (Request) -> Response) {
        if (config.appIdentifier != appIdentifier) denied()
        if (listOf(config.rootFolderId, config.systemFolderId, config.receiptsFolderId, config.paymentsFolderId,
                config.exportsFolderId, config.transactionsFolderId, config.backupsFolderId).distinct().size != 7) denied()
        val auth = "Bearer $token"
        val root = owned(config.rootFolderId, auth, send)
        if (root.getString("name") != rootFolderName || (rootId != null && rootId != config.rootFolderId)) denied()
        fun child(id: String, name: String, parent: String) {
            val meta = folder(id, auth, send)
            if (meta.getString("name") != name || meta.getJSONArray("parents").getString(0) != parent) denied()
        }
        if (systemId != null && config.systemFolderId != systemId) denied()
        child(config.systemFolderId, "_BelegApp-Daten", config.rootFolderId)
        mapOf(config.receiptsFolderId to "receipts", config.paymentsFolderId to "payments", config.exportsFolderId to "exports",
            config.transactionsFolderId to "transactions", config.backupsFolderId to "backups").forEach { (id, name) ->
            child(id, name, config.systemFolderId)
        }
    }

    fun validateJsonReferences(json: String, token: String, send: (Request) -> Response) {
        val fields = setOf("rootFolderId", "systemFolderId", "receiptsFolderId", "paymentsFolderId", "exportsFolderId",
            "transactionsFolderId", "backupsFolderId", "driveFileId", "driveFolderId", "driveMetadataFileId",
            "metadataFileId", "mainDriveFileId", "previousMainDriveFileId", "previousMetadataFileId", "legacyDriveFolderId")
        val references = mutableSetOf<Triple<String, Boolean, Boolean>>()
        val historical = setOf("previousMainDriveFileId", "previousMetadataFileId", "legacyDriveFolderId")
        fun walk(value: Any?) {
            when (value) {
                is JSONObject -> value.keys().forEach { key ->
                    val item = value.opt(key)
                    if (key in fields && item is String && item.isNotBlank()) references += Triple(item, key.endsWith("FolderId"), key in historical)
                    walk(item)
                }
                is org.json.JSONArray -> (0 until value.length()).forEach { walk(value.opt(it)) }
            }
        }
        walk(org.json.JSONTokener(json).nextValue())
        // Reuse ancestor metadata only within this one JSON check, never across operations.
        val proof = mutableMapOf<String, JSONObject>()
        references.forEach { (id, folderExpected, historyOnly) ->
            try {
                val meta = owned(id, "Bearer $token", send, proof)
                if ((meta.optString("mimeType") == FOLDER) != folderExpected) denied()
            } catch (missing: MissingReference) {
                // A tombstone may name a file already deleted. This grants no access to it.
                // Missing ancestors and every current data/config reference still fail closed.
                if (!historyOnly || missing.id != id) throw missing
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

    private fun folder(id: String, token: String, send: (Request) -> Response): JSONObject = owned(id, token, send).also {
        if (it.optString("mimeType") != FOLDER) denied()
    }

    private fun owned(id: String, token: String, send: (Request) -> Response,
        proof: MutableMap<String, JSONObject> = mutableMapOf()): JSONObject {
        fun read(key: String) = proof.getOrPut(key) { readMetadata(key, token, send) }
        val original = read(id)
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
                if (!topLevelRoot(current.getString("id"), token, send)) denied()
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
            val files = json.optJSONArray("files") ?: throw DriveAccessFailure(DriveAccessFailure.Reason.INVALID_METADATA)
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
        val request = Request.Builder().url("https://www.googleapis.com/drive/v3/files/$id?fields=id,name,mimeType,parents,trashed,shortcutDetails,appProperties")
            .header("Authorization", token).build()
        val response = send(request)
        if (response.code == 404) { response.close(); throw MissingReference(id) }
        return readResponse(response).also {
            if (it.optString("mimeType").isBlank() || it.optString("name").isBlank() || it.optString("id").isBlank())
                throw DriveAccessFailure(DriveAccessFailure.Reason.INVALID_METADATA)
            if (it.optBoolean("trashed") || it.has("shortcutDetails")) denied()
            val tag = it.optJSONObject("appProperties")?.optString("appName").orEmpty()
            if (tag.isNotBlank() && tag != appIdentifier) denied()
            if (it.optString("id") != id) denied()
        }
    }

    private class MissingReference(val id: String) : DriveAccessFailure(DriveAccessFailure.Reason.HTTP, 404)

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
