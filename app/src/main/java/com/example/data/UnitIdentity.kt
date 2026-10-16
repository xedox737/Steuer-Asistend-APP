package com.example.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** The existing PropertyMetadata field carries structure; preference values stay keyed by ID. */
internal object UnitIdentity {
    data class Entry(val unitId: String, val name: String)
    data class Read(val entries: List<Entry>, val error: String? = null)
    const val ERROR = "Die gespeicherte Einheitenliste ist nicht eindeutig. Bitte die ursprünglichen Einheiten und IDs anhand einer Sicherung prüfen. Die Rohdaten bleiben erhalten."

    fun encode(entries: List<Entry>): String {
        require(entries.all { it.unitId.isNotBlank() && it.name.isNotBlank() })
        require(entries.map { it.unitId }.distinct().size == entries.size)
        require(entries.map { it.name.trim().lowercase(java.util.Locale.ROOT) }.distinct().size == entries.size) {
            "Bitte eindeutige Einheitsnamen vergeben."
        }
        return JSONArray().apply { entries.forEach { put(JSONObject().put("unitId", it.unitId).put("name", it.name)) } }.toString()
    }

    fun read(raw: String, propertyId: String, prefs: SharedPreferences): Read {
        if (raw.isBlank()) return Read(emptyList())
        // A malformed structured value must never fall through to legacy text interpretation.
        if (raw.trimStart().startsWith("[")) return runCatching { Read(decode(raw)) }
            .getOrElse { Read(emptyList(), ERROR) }

        val scoped = "property_${propertyId}_unit_"
        val candidates = linkedMapOf<String, String>()
        var conflictingKeys = false
        prefs.all.forEach { (key, value) ->
            if (value is String && value.isNotBlank()) {
                val name = when {
                    key.startsWith(scoped) && key.endsWith("_id") -> key.removePrefix(scoped).removeSuffix("_id")
                    propertyId == StableDocumentIdentity.LEGACY_PROPERTY_ID && key.startsWith("unit_id_") &&
                        !key.startsWith("unit_id_index_") -> key.removePrefix("unit_id_")
                    else -> null
                }
                if (name != null) {
                    if (candidates[name] != null && candidates[name] != value) conflictingKeys = true
                    candidates.putIfAbsent(name, value)
                }
            }
        }
        if (conflictingKeys) return Read(emptyList(), ERROR)
        // Only complete stored name -> ID keys may establish old list boundaries.
        // Enumerate at most two solutions; ambiguity is an error, never an index fallback.
        val solutions = mutableListOf<List<Entry>>()
        fun visit(offset: Int, entries: List<Entry>) {
            if (solutions.size > 1) return
            if (offset == raw.length) { solutions.add(entries); return }
            candidates.forEach { (name, id) ->
                if (name.isNotEmpty() && entries.none { it.unitId == id } && raw.startsWith(name, offset)) {
                    val end = offset + name.length
                    if (end == raw.length) visit(end, entries + Entry(id, name))
                    else listOf(", ", ",").forEach { delimiter ->
                        if (raw.startsWith(delimiter, end)) visit(end + delimiter.length, entries + Entry(id, name))
                    }
                }
            }
        }
        visit(0, emptyList())
        val unique = solutions.distinct()
        if (unique.size == 1) return Read(unique.single())
        // The historic single-name contract is unambiguous; preserve its deterministic legacy ID.
        if (unique.isEmpty() && !raw.contains(',')) {
            val name = raw.trim()
            return Read(listOf(Entry(candidates[name] ?: StableDocumentIdentity.legacyUnitId(propertyId, name), name)))
        }
        return Read(emptyList(), ERROR)
    }

    fun decode(raw: String): List<Entry> {
        val array = JSONArray(raw)
        return (0 until array.length()).map {
            val item = array.getJSONObject(it)
            Entry(item.getString("unitId"), item.getString("name"))
        }.also { encode(it) }
    }

    fun display(raw: String): String = if (raw.trimStart().startsWith("[")) runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).joinToString(", ") { array.getJSONObject(it).getString("name") }
    }.getOrDefault(ERROR) else raw
}
