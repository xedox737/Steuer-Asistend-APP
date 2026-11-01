package com.example.data

import com.example.api.canonicalDriveJson
import org.json.JSONArray
import org.json.JSONObject

/** A conditional HTTP write alone cannot prove that the local snapshot is newer. Keep all
 * existing entities, reject newer remote versions, and require a known common preimage for
 * ambiguous unversioned edits. This performs no merge, cleanup, restore or ID replacement. */
internal object DriveBackupConflictGuard {
    private fun conflict(): Nothing = throw com.example.api.DriveSyncConflict(com.example.api.DriveSyncCheck.BACKUP_PREIMAGE_CONFLICT)
    // These are the keys emitted by SupplementalDriveBackup, never assignment/foreign keys.
    private val bankPrimaryKeys = mapOf("bankAccounts" to "accountId", "bankTransactions" to "transactionId",
        "bankReceiptLinks" to "linkId", "bankLearningRules" to "ruleId", "bankRuleEvidence" to "evidenceId",
        "bankRentAssignments" to "assignmentId", "bankLoanAssignments" to "assignmentId", "bankRecurringPatterns" to "patternId")
    fun validate(before: Any?, candidate: Any?, knownBaseline: Boolean, path: String = "") {
        if (canonicalDriveJson(before) == canonicalDriveJson(candidate)) return
        if (before is JSONObject && candidate is JSONObject) {
            val oldTime = before.optString("updatedAt").takeIf(String::isNotBlank)?.let(::instant)
            val newTime = candidate.optString("updatedAt").takeIf(String::isNotBlank)?.let(::instant)
            if (oldTime != null && newTime != null && newTime < oldTime) conflict()
            val newer = oldTime != null && newTime != null && newTime > oldTime
            before.keys().forEach { key ->
                if (!candidate.has(key)) conflict()
                if (key in com.example.api.driveReferenceFields && before.opt(key) is String && before.getString(key).isNotBlank() &&
                    (candidate.isNull(key) || candidate.optString(key).isBlank())) conflict()
                val child = "$path/$key"
                val localSetting = child in setOf("/appSettingPrefs/auto_backup", "/appSettingPrefs/selected_property_id")
                val schemaUpgrade = key == "schemaVersion" && before.optInt(key, -1) >= 0 && candidate.optInt(key, -1) >= before.getInt(key)
                validate(before.get(key), candidate.get(key), knownBaseline || newer || localSetting || schemaUpgrade, child)
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
                    oldIds.toSet().size != oldIds.size || nextIds.toSet().size != nextIds.size)) conflict()
            if (before.length() == 0) return
            if (oldIds.all { it != null }) {
                if (oldIds.toSet().size != oldIds.size || nextIds.any { it == null } || nextIds.toSet().size != nextIds.size) conflict()
                val byId = nextIds.zip(next).toMap()
                oldIds.zip(old).forEach { (id, value) -> validate(value, byId[id] ?: conflict(), knownBaseline, "$path/$id") }
            } else {
                // No stable identity: preserving every old value is provable; inferring a deletion
                // or replacement from an array position is not. A known baseline permits edits.
                if (!knownBaseline && old.any { a -> next.none { b -> canonicalDriveJson(a) == canonicalDriveJson(b) } }) conflict()
                if (candidate.length() < before.length()) conflict()
            }
            return
        }
        if (!knownBaseline) conflict()
    }

    private fun instant(raw: String): java.time.Instant? = runCatching { java.time.Instant.parse(raw) }.getOrNull()
        ?: runCatching { java.time.LocalDateTime.parse(raw).toInstant(java.time.ZoneOffset.UTC) }.getOrNull()
}
