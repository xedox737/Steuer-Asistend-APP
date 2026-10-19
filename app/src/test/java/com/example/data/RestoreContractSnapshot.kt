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
        fun fromRaw(raw: String, roomRaw: String? = null): RestoreContractSnapshot {
            val authoritative = JSONObject(raw)
            val normalized = JSONObject()
            val supplemental = authoritative.getJSONObject("supplemental")
            val documents = supplemental.getJSONArray("managedDocuments")
            val orderByReceipt = originalRanks(documents)

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
                    if (safeLegacyValues && (!preferences.has(aliasKey) ||
                            (alias != null && alias.optString("type") == "string" && alias.optString("value") == id))) {
                        aliases.put(aliasKey, id)
                        // If absent, identity is already fully recorded by structured units.
                        // A conflicting alias or unmatched legacy value is kept and turns red.
                        preferences.remove(aliasKey)
                    }
                }
            }
            val reconstructed = JSONObject().put("receiptOriginalOrder", orderByReceipt).put("unitNameBindings", aliases)
            if (roomRaw != null) {
                val room = JSONObject(roomRaw)
                val roomDocuments = room.getJSONArray("managedDocuments")
                reconstructed.put("roomOriginalOrder", originalRanks(roomDocuments))
                for (i in 0 until roomDocuments.length()) {
                    val document = roomDocuments.getJSONObject(i)
                    // C: local path and OCR search text are materialized from the
                    // same hash-checked original. Every other persisted member stays.
                    document.remove("localUri")
                    document.remove("ocrText")
                }
                val receipts = room.getJSONArray("receipts")
                val backedReceipts = authoritative.getJSONArray("receipts")
                val moneyCentById = (0 until backedReceipts.length()).map { backedReceipts.getJSONObject(it) }
                    .filter { it.has("cent") }.associate { it.getString("internalId") to it.getLong("cent") }
                val normalizedMoney = JSONObject()
                for (i in 0 until receipts.length()) {
                    val receipt = receipts.getJSONObject(i)
                    val receiptId = receipt.getString("internalId")
                    val backedCent = moneyCentById[receiptId]
                    if (backedCent != null && receipt.has("bruttobetrag")) {
                        val amount = receipt.getDouble("bruttobetrag")
                        val centRepresentation = backedCent.toDouble() / 100.0
                        // D: ONLY the exact cent Double or its immediately adjacent
                        // IEEE-754 value, independently backed by the authoritative cents.
                        // Logbook's km * .30 produces these two observed neighbours.
                        // Sub-cent edits, larger drift and mismatched cents stay raw/red.
                        if (amount.isFinite() && (amount == centRepresentation ||
                                Math.nextUp(amount) == centRepresentation || Math.nextDown(amount) == centRepresentation)) {
                            receipt.put("bruttobetrag", centRepresentation)
                            normalizedMoney.put(receiptId, backedCent)
                        }
                    }
                    // C: these exact six local transport/cache fields cannot choose
                    // an authored version. File identities/hashes/order remain above.
                    if (room.optJSONObject("receiptOriginalChains")?.has(receipt.getString("internalId")) == true) {
                        receipt.remove("imageUrl")
                    }
                    receipt.remove("isArchivedToDrive")
                    receipt.remove("syncStatus")
                    receipt.remove("syncError")
                    receipt.remove("lastSyncedAt")
                    receipt.remove("driveRevision")
                }
                authoritative.put("persistedRoom", room)
                normalized.put("receiptGrossAmountCent", normalizedMoney)
            }
            return RestoreContractSnapshot(authoritative, normalized, reconstructed)
        }

        private fun originalRanks(documents: JSONArray): JSONObject {
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

            return orderByReceipt
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
