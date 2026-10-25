package com.example.comparison

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject

/** One simulated account, real GoogleDriveClient request/response protocol; no network credentials.
 * This is transport evidence, never evidence of a real Google account or cloud round trip.
 */
class ComparisonDriveHttpFixture : Interceptor {
    data class Entry(val id: String, var name: String, val mime: String, var parent: String,
        var bytes: ByteArray, var properties: Map<String, String>, var revision: Int = 1)
    val entries = linkedMapOf<String, Entry>()
    val requests = mutableListOf<Request>()
    private var sequence = 0
    val myDrive = "account-root"
    init { entries[myDrive] = Entry(myDrive, "My Drive", FOLDER, "", byteArrayOf(), emptyMap()) }

    fun put(id: String, name: String, parent: String, json: String = "{}", entity: String = "", mime: String = "application/json"): Entry =
        Entry(id, name, mime, parent, json.toByteArray(), if (entity.isBlank()) emptyMap() else mapOf("appName" to "ImmobilienBelegApp", "entityType" to entity))
            .also { entries[id] = it }

    fun inventory(root: String): Map<String, String> = descendants(root).associate { id ->
        val file = entries.getValue(id)
        id to (metadata(file).toString() + ":" + com.example.data.StableDocumentIdentity.sha256(file.bytes))
    }
    fun descendants(root: String): List<String> = entries.keys.filter { id ->
        var current = id
        val visited = mutableSetOf<String>()
        while (visited.add(current)) {
            if (current == root) return@filter true
            current = entries[current]?.parent ?: break
        }
        false
    }
    fun orphanIds() = entries.values.filter { it.id != myDrive && it.parent !in entries }.map { it.id }

    @Synchronized override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        requests += request
        val url = request.url
        val path = url.encodedPath
        val base = if (path.startsWith("/upload/")) "/upload/drive/v3/files" else "/drive/v3/files"
        check(path == base || path.startsWith("$base/")) { "Unexpected endpoint" }
        val id = path.removePrefix(base).removePrefix("/").let { if (it == "root") myDrive else it }
        fun reply(code: Int, bytes: ByteArray, mime: String = "application/json") = Response.Builder()
            .request(request).protocol(Protocol.HTTP_1_1).code(code).message(if (code < 400) "OK" else "Missing")
            .body(bytes.toResponseBody(mime.toMediaType())).build()
        fun json(code: Int, value: JSONObject) = reply(code, value.toString().toByteArray())
        if (request.method == "GET" && id.isBlank()) {
            val query = url.queryParameter("q") ?: error("Unbounded inventory request")
            val parents = Regex("'([^']+)' in parents").findAll(query).map { it.groupValues[1].let { p -> if (p == "root") myDrive else p } }.toList()
            val name = Regex("name = '((?:\\\\.|[^'\\\\])*)'").find(query)?.groupValues?.get(1)?.replace("\\'", "'")?.replace("\\\\", "\\")
            val props = Regex("key='([^']+)' and value='([^']*)'").findAll(query).map { it.groupValues[1] to it.groupValues[2] }.toList()
            val files = entries.values.filter { f -> f.id != myDrive &&
                (parents.isEmpty() || f.parent in parents) && (name == null || f.name == name) &&
                (!query.contains("mimeType = '$FOLDER'") || f.mime == FOLDER) && props.all { f.properties[it.first] == it.second }
            }
            return json(200, JSONObject().put("files", JSONArray().apply { files.forEach { put(metadata(it)) } }))
        }
        if (request.method == "GET") {
            val file = entries[id] ?: return json(404, JSONObject())
            return if (url.queryParameter("alt") == "media") reply(200, file.bytes, file.mime) else json(200, metadata(file))
        }
        if (request.method == "DELETE") {
            entries.remove(id)
            return reply(204, byteArrayOf())
        }
        val bytes = Buffer().also { request.body!!.writeTo(it) }.readByteArray()
        if (request.method == "PATCH" && url.queryParameter("uploadType") == "media") {
            val file = entries[id] ?: return json(404, JSONObject())
            file.bytes = bytes
            file.revision++
            return json(200, metadata(file))
        }
        val meta: JSONObject
        val content: ByteArray
        var contentMime = "application/json"
        if (request.body!!.contentType()?.type == "multipart") {
            val wire = bytes.toString(Charsets.ISO_8859_1)
            val begin = wire.indexOf("\r\n\r\n") + 4
            val end = wire.indexOf("\r\n--", begin)
            meta = JSONObject(bytes.copyOfRange(begin, end).toString(Charsets.UTF_8))
            val contentStart = wire.indexOf("\r\n\r\n", end) + 4
            contentMime = wire.substring(end, contentStart).substringAfter("Content-Type: ").substringBefore("\r\n").substringBefore(';')
            content = bytes.copyOfRange(contentStart, wire.lastIndexOf("\r\n--"))
        } else {
            meta = JSONObject(bytes.toString(Charsets.UTF_8))
            content = byteArrayOf()
        }
        fun properties() = meta.optJSONObject("appProperties")?.let { obj -> obj.keys().asSequence().associateWith { obj.getString(it) } }.orEmpty()
        if (request.method == "POST") {
            val newId = "created-${++sequence}"
            val file = Entry(newId, meta.getString("name"), meta.optString("mimeType", contentMime),
                meta.optJSONArray("parents")?.getString(0) ?: myDrive, content, properties())
            entries[newId] = file
            return json(200, metadata(file))
        }
        check(request.method == "PATCH")
        val file = entries[id] ?: return json(404, JSONObject())
        if (meta.has("name")) file.name = meta.getString("name")
        if (meta.has("appProperties")) file.properties = properties()
        url.queryParameter("addParents")?.let { file.parent = it }
        file.revision++
        return json(200, metadata(file))
    }

    private fun metadata(file: Entry) = JSONObject().put("id", file.id).put("name", file.name).put("mimeType", file.mime)
        .put("size", file.bytes.size).put("parents", JSONArray().apply { if (file.parent.isNotBlank()) put(file.parent) })
        .put("trashed", false).put("appProperties", JSONObject(file.properties))
        .put("createdTime", "2026-10-25T00:00:00Z")
        .put("modifiedTime", java.time.Instant.parse("2026-10-25T00:00:00Z").plusSeconds(file.revision.toLong()).toString())
        .also { if (file.mime == "application/vnd.google-apps.shortcut") it.put("shortcutDetails", JSONObject().put("targetId", "prod-original")) }

    companion object { const val FOLDER = "application/vnd.google-apps.folder" }
}
