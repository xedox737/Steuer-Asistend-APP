package com.example.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Explicit inventory. Mixed stores export only the named non-credential fields. */
internal object PersistentPreferenceInventory {
    enum class Kind { BACKUP, SECRET, LOCAL_ONLY, CACHE }
    data class Store(val name: String, val kind: Kind, val payloadKey: String? = null,
                     val allowedKeys: Set<String>? = null)

    val stores = listOf(
        Store("rent_plan_prefs", Kind.BACKUP, "rentPlanPrefs"),
        Store("tenant_history_prefs", Kind.BACKUP, "tenantHistoryPrefs"),
        Store("loan_interest_assignments", Kind.BACKUP, "loanInterestAssignments"),
        Store("annual_tax_approval_prefs", Kind.BACKUP, "annualTaxApprovalPrefs"),
        Store("afa_confirmed_values_prefs", Kind.BACKUP, "confirmedAfaValuesPrefs"),
        Store("wohneinheiten_prefs", Kind.BACKUP, "propertyUnitPrefs"),
        Store("datev_kanzleiprofil_prefs", Kind.BACKUP, "datevProfilePrefs", setOf("active_profile_json")),
        Store("property_tasks_prefs", Kind.BACKUP, "propertyTaskPrefs"),
        Store("unit_status_meta_prefs", Kind.BACKUP, "unitStatusMetaPrefs"),
        Store("unit_rental_detail_prefs", Kind.BACKUP, "unitRentalDetailPrefs"),
        Store("bank_transaction_notes", Kind.BACKUP, "bankTransactionNotes"),
        Store("ki_learned_rules_prefs", Kind.BACKUP, "learnedRulePrefs"),
        Store("logbook_drafts", Kind.BACKUP, "logbookDraftPrefs"),
        Store("ai_provider_settings", Kind.BACKUP, "aiSelectionPrefs",
            setOf("receipt_analysis_provider", "openai_model")),
        Store("google_drive_prefs", Kind.BACKUP, "appSettingPrefs",
            setOf("selected_property_id", "auto_backup")),
        // Folder IDs belong to the connected Drive, and are resolved from its mappings file.
        Store("category_folder_mappings_prefs", Kind.CACHE),
        Store("app_installation_prefs", Kind.LOCAL_ONLY),
        Store("restore_journal_prefs", Kind.LOCAL_ONLY),
        Store("duplicate_cleanup_journals", Kind.LOCAL_ONLY),
        Store("metadata_duplicate_cleanup_audit", Kind.LOCAL_ONLY)
    )

    private val secretKey = Regex(
        "(?i)(^|[_-])(api[_-]?key|key[_-]?(ciphertext|iv)|ciphertext|token|pin|tan|password|secret|credential)([_-]|$)"
    )

    private fun permits(store: Store, key: String): Boolean =
        store.kind == Kind.BACKUP && (store.allowedKeys?.contains(key) ?: !secretKey.containsMatchIn(key))

    fun writePayload(context: Context, root: JSONObject) {
        stores.filter { it.kind == Kind.BACKUP }.forEach { store ->
            val values = JSONObject()
            context.getSharedPreferences(store.name, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                if (permits(store, key)) {
                    val typed = when (value) {
                        is String -> entry("string", value)
                        is Int -> entry("int", value)
                        is Long -> entry("long", value)
                        is Float -> entry("float", value.toDouble())
                        is Boolean -> entry("boolean", value)
                        is Set<*> -> entry("stringSet", JSONArray(value.filterIsInstance<String>().sorted()))
                        else -> null
                    }
                    if (typed != null) values.put(key, typed)
                }
            }
            root.put(requireNotNull(store.payloadKey), values)
        }
    }

    fun validatePayload(root: JSONObject) {
        stores.filter { it.kind == Kind.BACKUP }.forEach { store ->
            val payloadKey = requireNotNull(store.payloadKey)
            if (!root.has(payloadKey)) return@forEach
            val values = root.getJSONObject(payloadKey)
            values.keys().forEach { key ->
                if (permits(store, key)) {
                    val value = values.getJSONObject(key)
                    when (value.getString("type")) {
                        "string" -> value.getString("value")
                        "int" -> value.getInt("value")
                        "long" -> value.getLong("value")
                        "float" -> require(value.getDouble("value").toFloat().isFinite())
                        "boolean" -> value.getBoolean("value")
                        "stringSet" -> value.getJSONArray("value").let { array ->
                            (0 until array.length()).forEach { array.getString(it) }
                        }
                        else -> error("Unbekannter Preference-Typ in der Sicherung.")
                    }
                }
            }
        }
    }

    fun restorePayload(
        context: Context,
        root: JSONObject,
        mode: RestoreMode = RestoreMode.MERGE
    ) {
        stores.filter { it.kind == Kind.BACKUP }.forEach { store ->
            val values = root.optJSONObject(requireNotNull(store.payloadKey)) ?: return@forEach
            val prefs = context.getSharedPreferences(store.name, Context.MODE_PRIVATE)
            val editor = prefs.edit()

            if (mode == RestoreMode.REPLACE_FULL) {
                prefs.all.keys.filter { permits(store, it) }.forEach(editor::remove)
            }

            values.keys().forEach entryLoop@ { key ->
                if (!permits(store, key)) return@entryLoop
                val value = values.optJSONObject(key) ?: return@entryLoop

                if (mode == RestoreMode.MERGE &&
                    prefs.contains(key) &&
                    store.name in setOf("tenant_history_prefs", "property_tasks_prefs")
                ) {
                    val merged = mergeStructuredStringValue(store.name, prefs.all[key] as? String, value)
                    if (merged != null) editor.putString(key, merged)
                    return@entryLoop
                }

                // No per-field version exists in the remaining stores. A snapshot must not
                // roll back unit values, rent, assignments, notes, drafts or settings.
                if (mode == RestoreMode.MERGE && prefs.contains(key)) {
                    if (value.optString("type") == "stringSet" && prefs.all[key] is Set<*>) {
                        val array = value.getJSONArray("value")
                        editor.putStringSet(key, prefs.getStringSet(key, emptySet()).orEmpty() +
                            (0 until array.length()).map { array.getString(it) })
                    }
                    return@entryLoop
                }

                when (value.optString("type")) {
                    "string" -> editor.putString(key, value.getString("value"))
                    "int" -> editor.putInt(key, value.getInt("value"))
                    "long" -> editor.putLong(key, value.getLong("value"))
                    "float" -> editor.putFloat(key, value.getDouble("value").toFloat())
                    "boolean" -> editor.putBoolean(key, value.getBoolean("value"))
                    "stringSet" -> {
                        val array = value.getJSONArray("value")
                        editor.putStringSet(key, (0 until array.length()).map { array.getString(it) }.toSet())
                    }
                    else -> error("Unbekannter Preference-Typ in der Sicherung.")
                }
            }
            check(editor.commit()) { "Einstellungen konnten nicht dauerhaft wiederhergestellt werden." }
        }
    }

    private fun mergeStructuredStringValue(
        storeName: String,
        localRaw: String?,
        backupEntry: JSONObject
    ): String? {
        if (backupEntry.optString("type") != "string" || localRaw == null) return null
        val backupRaw = backupEntry.optString("value", "")
        return runCatching {
            when (storeName) {
                "tenant_history_prefs" -> mergeTenantHistory(localRaw, backupRaw)
                "property_tasks_prefs" -> mergeTasks(localRaw, backupRaw)
                else -> null
            }
        }.getOrNull()
    }

    private fun mergeTenantHistory(localRaw: String, backupRaw: String): String {
        val local = JSONArray(localRaw)
        val backup = JSONArray(backupRaw)
        val merged = linkedMapOf<String, JSONObject>()

        fun tenantKey(item: JSONObject): String? {
            val id = item.optLong("id", Long.MIN_VALUE)
            if (id != Long.MIN_VALUE && id != 0L) return "id:$id"
            return null
        }

        for (i in 0 until local.length()) {
            val item = local.getJSONObject(i)
            merged[tenantKey(item) ?: "unidentified-local:$i"] = JSONObject(item.toString())
        }
        for (i in 0 until backup.length()) {
            val incoming = backup.getJSONObject(i)
            // Without a stable ID there is no safe way to match an old period to local work.
            val key = tenantKey(incoming) ?: continue
            val current = merged[key]
            merged[key] = if (current == null) JSONObject(incoming.toString()) else mergeTenantPeriod(current, incoming)
        }

        return JSONArray().apply {
            merged.values.sortedBy { it.optString("startDate", "") }.forEach(::put)
        }.toString()
    }

    private fun mergeTenantPeriod(local: JSONObject, backup: JSONObject): JSONObject {
        val result = JSONObject(local.toString())
        val byDate = linkedMapOf<String, JSONObject>()

        fun collect(source: JSONArray?) {
            if (source == null) return
            for (i in 0 until source.length()) {
                val change = source.optJSONObject(i) ?: continue
                val effective = change.optString("effectiveDate", "")
                if (effective.isBlank()) continue
                byDate.putIfAbsent(effective, JSONObject(change.toString()))
            }
        }

        collect(local.optJSONArray("rentChanges"))
        collect(backup.optJSONArray("rentChanges"))
        result.put("rentChanges", JSONArray().apply {
            byDate.toSortedMap().values.forEach(::put)
        })
        return result
    }

    private fun mergeTasks(localRaw: String, backupRaw: String): String {
        val local = JSONArray(localRaw)
        val backup = JSONArray(backupRaw)
        val merged = linkedMapOf<String, JSONObject>()

        fun taskKey(item: JSONObject): String? =
            item.optString("id", "").takeIf(String::isNotBlank)

        for (i in 0 until local.length()) {
            val item = local.getJSONObject(i)
            merged[taskKey(item) ?: "unidentified-local:$i"] = JSONObject(item.toString())
        }
        for (i in 0 until backup.length()) {
            val incoming = backup.getJSONObject(i)
            val key = taskKey(incoming) ?: continue
            val current = merged[key]
            merged[key] = when {
                current == null -> JSONObject(incoming.toString())
                backupIsNewer(current.optString("updatedAt"), incoming.optString("updatedAt")) ->
                    JSONObject(incoming.toString())
                else -> current
            }
        }

        return JSONArray().apply {
            merged.values.sortedWith(
                compareBy<JSONObject> { it.optBoolean("done", false) }
                    .thenBy { it.optString("dueDate", "").ifBlank { "9999-99-99" } }
                    .thenBy { it.optString("id", "") }
            ).forEach(::put)
        }.toString()
    }

    private fun backupIsNewer(localUpdatedAt: String, backupUpdatedAt: String): Boolean {
        val local = runCatching { Instant.parse(localUpdatedAt) }.getOrNull() ?: return false
        val backup = runCatching { Instant.parse(backupUpdatedAt) }.getOrNull() ?: return false
        return backup.isAfter(local)
    }

    private fun entry(type: String, value: Any) = JSONObject().put("type", type).put("value", value)
}
