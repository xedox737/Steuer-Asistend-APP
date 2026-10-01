package com.example.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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

    fun restorePayload(context: Context, root: JSONObject) {
        stores.filter { it.kind == Kind.BACKUP }.forEach { store ->
            // Missing fields in older backups must leave the current local store alone.
            val values = root.optJSONObject(requireNotNull(store.payloadKey)) ?: return@forEach
            val editor = context.getSharedPreferences(store.name, Context.MODE_PRIVATE).edit()
            values.keys().forEach entryLoop@ { key ->
                if (permits(store, key)) {
                    val value = values.optJSONObject(key) ?: return@entryLoop
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
            }
            // Merge by stable key: restore is repeatable and does not erase local-only entries.
            check(editor.commit()) { "Einstellungen konnten nicht dauerhaft wiederhergestellt werden." }
        }
    }

    private fun entry(type: String, value: Any) = JSONObject().put("type", type).put("value", value)
}
