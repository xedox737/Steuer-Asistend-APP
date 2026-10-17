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
        return JSONObject().put("unitListVersion", 1).put("units", JSONArray().apply {
            entries.forEach { put(JSONObject().put("unitId", it.unitId).put("name", it.name)) }
        }).toString()
    }

    fun read(raw: String, propertyId: String, prefs: SharedPreferences): Read {
        if (raw.isBlank()) return Read(emptyList())
        // A malformed structured value must never fall through to legacy text interpretation.
        if (raw.trimStart().startsWith("{")) return runCatching { Read(decode(raw)) }
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
            val legacyId = StableDocumentIdentity.legacyUnitId(propertyId, name)
            val expectedPrefix = "property_${propertyId}_unitid_${legacyId}_"
            val hasUnresolvedStableIdentity = prefs.all.any { (key, value) ->
                when {
                    key.startsWith("property_${propertyId}_unit_id_index_") -> value != legacyId
                    key.startsWith("property_${propertyId}_unitid_") -> !key.startsWith(expectedPrefix)
                    propertyId == StableDocumentIdentity.LEGACY_PROPERTY_ID && key.startsWith("unit_id_index_") -> value != legacyId
                    else -> false
                }
            }
            if (candidates[name] == null && (hasUnresolvedStableIdentity || candidates.isNotEmpty())) return Read(emptyList(), ERROR)
            return Read(listOf(Entry(candidates[name] ?: legacyId, name)))
        }
        return Read(emptyList(), ERROR)
    }

    /** A name alias is never allowed to override a different, already stored stable ID. */
    fun legacyAliasMatches(prefs: SharedPreferences, propertyId: String, unitId: String, name: String): Boolean {
        val scoped = runCatching { prefs.getString("property_${propertyId}_unit_${name}_id", null) }.getOrElse { return false }
        val legacy = if (propertyId == StableDocumentIdentity.LEGACY_PROPERTY_ID)
            runCatching { prefs.getString("unit_id_$name", null) }.getOrElse { return false } else null
        val aliases = listOfNotNull(scoped, legacy)
        return if (aliases.isEmpty()) unitId == StableDocumentIdentity.legacyUnitId(propertyId, name)
            else aliases.all { it == unitId }
    }

    fun decode(raw: String): List<Entry> {
        val root = JSONObject(raw)
        require(root.get("unitListVersion") == 1) { ERROR }
        val array = root.getJSONArray("units")
        return (0 until array.length()).map {
            val item = array.getJSONObject(it)
            Entry(item.get("unitId") as? String ?: error(ERROR), item.get("name") as? String ?: error(ERROR))
        }.also { encode(it) }
    }

    fun display(raw: String): String = if (raw.trimStart().startsWith("{")) runCatching {
        decode(raw).joinToString(", ") { it.name }
    }.getOrDefault(ERROR) else raw
}
