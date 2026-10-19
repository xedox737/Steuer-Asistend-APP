package com.example.data

import org.json.JSONArray
import org.json.JSONObject

/** Explicit contract in docs/PHASE10D_RESTORE_METADATA_CONTRACT.md.
 * Unknown fields are authoritative. In particular, allocations/proposals, source,
 * versions and all review fields are NOT discarded or normalized.
 */
internal data class RestoreContractSnapshot(
    val authoritativeFields: JSONObject,
    val normalizedFields: JSONObject,
    val reconstructedFields: JSONObject
) {
    fun canonical(): String = canonicalJson(JSONObject()
        .put("authoritativeFields", authoritativeFields)
        .put("normalizedFields", normalizedFields)
        .put("reconstructedFields", reconstructedFields))

    companion object {
        fun fromRaw(raw: String): RestoreContractSnapshot {
            val authoritative = JSONObject(raw)
            val supplemental = authoritative.getJSONObject("supplemental")
            val documents = supplemental.getJSONArray("managedDocuments")
            val orderByReceipt = JSONObject()
            val receiptGroups = (0 until documents.length()).map { documents.getJSONObject(it) }
                .filter { !it.isNull("receiptInternalId") && it.optString("receiptInternalId").isNotBlank() }
                .groupBy { it.getString("receiptInternalId") }
            receiptGroups.forEach { (receiptId, originals) ->
                val ordered = ReceiptOriginalChain.ordered(originals.map { json ->
                    ManagedDocument(documentId = json.getString("documentId"), propertyId = json.getString("propertyId"),
                        receiptInternalId = receiptId, createdAt = json.optString("createdAt"),
                        extractedFieldsJson = json.optString("extractedFieldsJson"))
                })
                // A map of ID -> rank retains order even though entity arrays are sets.
                orderByReceipt.put(receiptId, JSONObject().apply {
                    ordered.forEachIndexed { rank, document -> put(document.documentId, rank) }
                })
                originals.forEach { document ->
                    val originalRaw = document.optString("extractedFieldsJson")
                    val fields = runCatching { JSONObject(originalRaw) }.getOrNull()
                    // Only the observed single technical member may be reconstructed.
                    // Rich/invalid JSON remains byte-exact, including every reviewed field.
                    if (fields != null && fields.length() == 1 && fields.has("_receiptOriginalOrder") &&
                        fields.opt("_receiptOriginalOrder") is Number) {
                        document.put("extractedFieldsJson", "")
                    }
                }
            }

            val aliases = JSONObject()
            val preferences = supplemental.getJSONObject("propertyUnitPrefs")
            val properties = authoritative.getJSONArray("units")
            for (p in 0 until properties.length()) {
                val property = properties.getJSONObject(p)
                val propertyId = property.getString("propertyId")
                val units = property.getJSONArray("units")
                for (u in 0 until units.length()) {
                    val unit = units.getJSONObject(u)
                    val name = unit.getString("name")
                    val id = unit.getString("unitId")
                    val namePrefix = "property_${propertyId}_unit_${name}_"
                    val aliasKey = namePrefix + "id"
                    val alias = preferences.optJSONObject(aliasKey)
                    val idPrefix = "property_${propertyId}_unitid_${id}_"
                    val nameKeys = preferences.keys().asSequence().filter { it.startsWith(namePrefix) && it != aliasKey }.toList()
                    val safeLegacyValues = nameKeys.all { key ->
                        preferences.has(idPrefix + key.removePrefix(namePrefix)) &&
                            canonicalJson(preferences.get(key)) == canonicalJson(preferences.get(idPrefix + key.removePrefix(namePrefix)))
                    }
                    if (safeLegacyValues && (alias == null ||
                            (alias.optString("type") == "string" && alias.optString("value") == id))) {
                        aliases.put(aliasKey, id)
                        // If absent, identity is already fully recorded by structured units.
                        // A conflicting alias or unmatched legacy value is kept and turns red.
                        preferences.remove(aliasKey)
                    }
                }
            }
            return RestoreContractSnapshot(authoritative, JSONObject(), JSONObject()
                .put("receiptOriginalOrder", orderByReceipt).put("unitNameBindings", aliases))
        }
    }
}

/** Same RAW entity-set serialization as Phase10B; no field filtering or epsilon. */
internal fun canonicalJson(value: Any?): String = when (value) {
    is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") {
        JSONObject.quote(it) + ":" + canonicalJson(value.get(it))
    }
    is JSONArray -> (0 until value.length()).map { canonicalJson(value.get(it)) }.sorted().joinToString(",", "[", "]")
    is String -> JSONObject.quote(value)
    is Number -> JSONObject.numberToString(value)
    null, JSONObject.NULL -> "null"
    else -> value.toString()
}

internal fun snapshotDiff(before: String, after: String): JSONArray {
    val result = JSONArray()
    fun visit(a: Any?, b: Any?, path: String) {
        if (a is JSONObject && b is JSONObject) {
            (a.keys().asSequence().toSet() + b.keys().asSequence().toSet()).sorted().forEach { key ->
                if (a.has(key) && b.has(key)) visit(a.get(key), b.get(key), "$path/$key")
                else result.put(JSONObject().put("path", "$path/$key").put("before", a.opt(key) ?: "<missing>")
                    .put("after", b.opt(key) ?: "<missing>"))
            }
        } else if (a is JSONArray && b is JSONArray) {
            for (i in 0 until maxOf(a.length(), b.length())) {
                if (i < a.length() && i < b.length()) visit(a.get(i), b.get(i), "$path/$i")
                else result.put(JSONObject().put("path", "$path/$i").put("before", a.opt(i) ?: "<missing>")
                    .put("after", b.opt(i) ?: "<missing>"))
            }
        } else if (canonicalJson(a) != canonicalJson(b)) {
            result.put(JSONObject().put("path", path).put("before", a ?: JSONObject.NULL).put("after", b ?: JSONObject.NULL))
        }
    }
    visit(JSONObject(before), JSONObject(after), "")
    return result
}
