package com.example.data

import com.example.api.canonicalDriveJson
import org.json.JSONArray
import org.json.JSONObject

/** A conditional HTTP write alone cannot prove that the local snapshot is newer. Keep all
 * existing entities, reject newer remote versions, and require a known common preimage for
 * ambiguous unversioned edits. This performs no merge, cleanup, restore or ID replacement. */
internal object DriveBackupConflictGuard {
    /** Created only after independent original/hash verification, scoped to one account/run. */
    data class OriginalPreimage(val sha256: String, val size: Long)
    data class OriginalEvidence(val documentId: String, val propertyId: String, val unitId: String?,
        val receiptInternalId: String?, val fileId: String, val folderId: String, val filename: String,
        val mimeType: String, val sha256: String, val size: Long, val version: String,
        val ownPreimages: Set<OriginalPreimage> = emptySet())

    private val diagnosticFields = setOf("sha256", "fileSizeBytes", "updatedAt", "documents", "managedDocuments",
        "documentId", "driveFileId", "driveFolderId", "storedFilename", "propertyId", "unitId",
        "receiptInternalId", "schemaVersion", "reviewStatus", "documentDate")
    private fun conflict(path: String = ""): Nothing = throw com.example.api.DriveSyncConflict(
        com.example.api.DriveSyncCheck.BACKUP_PREIMAGE_CONFLICT,
        differentFields = listOf(path.substringAfterLast('/').takeIf { it in diagnosticFields } ?: "unknownFields"))

    /** Exact byte evidence for the same record/binding; never authority for other edits. */
    fun permitsOriginalFields(before: JSONObject, candidate: JSONObject, proof: OriginalEvidence): Boolean {
        if (proof.size <= 0 || !proof.version.matches(Regex("[1-9][0-9]*")) || !proof.sha256.matches(Regex("[a-f0-9]{64}"))) return false
        val binding = mapOf("documentId" to proof.documentId, "propertyId" to proof.propertyId,
            "unitId" to proof.unitId, "receiptInternalId" to proof.receiptInternalId,
            "driveFileId" to proof.fileId, "driveFolderId" to proof.folderId, "storedFilename" to proof.filename)
        if (binding.any { (key, value) -> canonicalDriveJson(before.opt(key)) != canonicalDriveJson(value) ||
                canonicalDriveJson(candidate.opt(key)) != canonicalDriveJson(value) }) return false
        val oldHash = before.opt("sha256") as? String ?: return false
        val newHash = candidate.opt("sha256") as? String ?: return false
        val oldSize = (before.opt("fileSizeBytes") as? Number)?.toString()?.toLongOrNull() ?: return false
        val newSize = (candidate.opt("fileSizeBytes") as? Number)?.toString()?.toLongOrNull() ?: return false
        val enrichment = (oldHash.isBlank() || oldHash.equals(proof.sha256, ignoreCase = true)) &&
            (oldSize == 0L || oldSize == proof.size)
        val ownChange = proof.ownPreimages.any { it.sha256.matches(Regex("[a-f0-9]{64}")) &&
            it.size > 0 && oldHash.equals(it.sha256, ignoreCase = true) && (oldSize == 0L || oldSize == it.size) }
        return (enrichment || ownChange) && newHash.equals(proof.sha256, ignoreCase = true) && newSize == proof.size
    }
    // These are the keys emitted by SupplementalDriveBackup, never assignment/foreign keys.
    private val bankPrimaryKeys = mapOf("bankAccounts" to "accountId", "bankTransactions" to "transactionId",
        "bankReceiptLinks" to "linkId", "bankLearningRules" to "ruleId", "bankRuleEvidence" to "evidenceId",
        "bankRentAssignments" to "assignmentId", "bankLoanAssignments" to "assignmentId", "bankRecurringPatterns" to "patternId")
    fun validate(before: Any?, candidate: Any?, knownBaseline: Boolean, path: String = "") =
        validate(before, candidate, knownBaseline, path, emptyMap())

    fun validate(before: Any?, candidate: Any?, knownBaseline: Boolean, path: String = "",
                 originals: Map<String, OriginalEvidence>) {
        if (canonicalDriveJson(before) == canonicalDriveJson(candidate)) return
        if (before is JSONObject && candidate is JSONObject) {
            val oldTime = before.optString("updatedAt").takeIf(String::isNotBlank)?.let(::instant)
            val newTime = candidate.optString("updatedAt").takeIf(String::isNotBlank)?.let(::instant)
            if (oldTime != null && newTime != null && newTime < oldTime) conflict("$path/updatedAt")
            val newer = oldTime != null && newTime != null && newTime > oldTime
            val original = originals[candidate.optString("documentId")]
            val enrichment = path.substringBeforeLast('/') in setOf("/documents", "/managedDocuments") &&
                original != null && permitsOriginalFields(before, candidate, original)
            before.keys().forEach { key ->
                if (!candidate.has(key)) conflict("$path/$key")
                if (key in com.example.api.driveReferenceFields && before.opt(key) is String && before.getString(key).isNotBlank() &&
                    (candidate.isNull(key) || candidate.optString(key).isBlank())) conflict("$path/$key")
                val child = "$path/$key"
                val localSetting = child in setOf("/appSettingPrefs/auto_backup", "/appSettingPrefs/selected_property_id")
                val schemaUpgrade = key == "schemaVersion" && before.optInt(key, -1) >= 0 && candidate.optInt(key, -1) >= before.getInt(key)
                val provenOriginalField = enrichment && key in setOf("sha256", "fileSizeBytes")
                validate(before.get(key), candidate.get(key), knownBaseline || newer || localSetting || schemaUpgrade || provenOriginalField, child, originals)
            }
            return
        }
        if (before is JSONArray && candidate is JSONArray) {
            val primaryKey = bankPrimaryKeys[path.substringAfterLast('/')]
            fun identity(value: Any?): String? = (value as? JSONObject)?.let { item ->
                (primaryKey?.let(::listOf) ?: listOf("internalId", "documentId", "id", "unitId", "propertyId", "bookingKey"))
                    .firstOrNull { item.has(it) && !item.isNull(it) && item.get(it).toString().isNotBlank() }
                    ?.let { "$it:${item.get(it)}" }
            }
            val old = (0 until before.length()).map { before.get(it) }
            val next = (0 until candidate.length()).map { candidate.get(it) }
            val oldIds = old.map(::identity)
            val nextIds = next.map(::identity)
            if (primaryKey != null && (oldIds.any { it == null } || nextIds.any { it == null } ||
                    oldIds.toSet().size != oldIds.size || nextIds.toSet().size != nextIds.size)) conflict(path)
            if (before.length() == 0) return
            if (oldIds.all { it != null }) {
                if (oldIds.toSet().size != oldIds.size || nextIds.any { it == null } || nextIds.toSet().size != nextIds.size) conflict(path)
                val byId = nextIds.zip(next).toMap()
                oldIds.zip(old).forEach { (id, value) -> validate(value, byId[id] ?: conflict(path), knownBaseline, "$path/$id", originals) }
            } else {
                // No stable identity: preserving every old value is provable; inferring a deletion
                // or replacement from an array position is not. A known baseline permits edits.
                if (!knownBaseline && old.any { a -> next.none { b -> canonicalDriveJson(a) == canonicalDriveJson(b) } }) conflict(path)
                if (candidate.length() < before.length()) conflict(path)
            }
            return
        }
        if (!knownBaseline) conflict(path)
    }

    private fun instant(raw: String): java.time.Instant? = runCatching { java.time.Instant.parse(raw) }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(raw).toInstant(java.time.ZoneOffset.UTC) }.getOrNull()
}
