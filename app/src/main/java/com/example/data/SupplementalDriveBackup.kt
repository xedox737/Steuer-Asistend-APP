package com.example.data

import android.content.Context
import com.example.api.GoogleDriveClient
import kotlinx.coroutines.CoroutineScope
import org.json.JSONArray
import org.json.JSONObject

object SupplementalDriveBackup {
    private const val ENTITY_TYPE = "supplementalBackup"
    private const val FILE_NAME = "supplementalBackup.json"
    private const val SCHEMA_VERSION = 1

    data class Result(
        val success: Boolean,
        val message: String
    )

    suspend fun backup(
        context: Context,
        database: AppDatabase,
        accessToken: String,
        systemFolderId: String
    ): Result {
        return try {
            val root = JSONObject().apply {
                put("schemaVersion", SCHEMA_VERSION)
                put("loans", JSONArray().apply {
                    database.loanDao().getAllLoans().forEach { loan ->
                        put(JSONObject().apply {
                            put("id", loan.id)
                            put("bezeichnung", loan.bezeichnung)
                            put("bank", loan.bank)
                            put("darlehensbetrag", loan.darlehensbetrag)
                            put("restschuld", loan.restschuld)
                            put("sollzinsProzent", loan.sollzinsProzent)
                            put("tilgungProzent", loan.tilgungProzent)
                            put("monatlicheRate", loan.monatlicheRate)
                            put("startDatum", loan.startDatum)
                            put("zinsbindungBis", loan.zinsbindungBis)
                            put("laufzeitBis", loan.laufzeitBis)
                            put("vermietungsanteilProzent", loan.vermietungsanteilProzent)
                            put("notiz", loan.notiz)
                            put("aktiv", loan.aktiv)
                        })
                    }
                })
                put("rentPlanPrefs", prefsToJson(context, "rent_plan_prefs"))
                put("tenantHistoryPrefs", prefsToJson(context, "tenant_history_prefs"))
                put("loanInterestAssignments", prefsToJson(context, "loan_interest_assignments"))
                put("annualTaxApprovalPrefs", prefsToJson(context, "annual_tax_approval_prefs"))
            }

            val existing = GoogleDriveClient.findFileByAppProperty(accessToken, systemFolderId, ENTITY_TYPE)
            val result = if (existing != null) {
                GoogleDriveClient.updateJson(accessToken, existing.id, root.toString(4))
            } else {
                GoogleDriveClient.uploadJson(
                    accessToken = accessToken,
                    folderId = systemFolderId,
                    filename = FILE_NAME,
                    json = root.toString(4),
                    appProperties = mapOf(
                        "appName" to "ImmobilienBelegApp",
                        "entityType" to ENTITY_TYPE,
                        "schemaVersion" to SCHEMA_VERSION.toString()
                    )
                )
            }
            Result(result.success, result.errorMessage ?: if (result.success) "Zusatzdaten gesichert" else "Zusatzdaten-Backup fehlgeschlagen")
        } catch (e: Exception) {
            Result(false, e.message ?: e.toString())
        }
    }

    suspend fun restore(
        context: Context,
        database: AppDatabase,
        accessToken: String,
        systemFolderId: String
    ): Result {
        return try {
            val file = GoogleDriveClient.findFileByAppProperty(accessToken, systemFolderId, ENTITY_TYPE)
                ?: return Result(true, "Keine Zusatzdaten-Sicherung vorhanden")
            val root = JSONObject(GoogleDriveClient.downloadJson(accessToken, file.id))

            val loans = root.optJSONArray("loans") ?: JSONArray()
            for (i in 0 until loans.length()) {
                val o = loans.getJSONObject(i)
                database.loanDao().upsertLoan(
                    Loan(
                        id = o.optInt("id", 0),
                        bezeichnung = o.optString("bezeichnung", ""),
                        bank = o.optString("bank", ""),
                        darlehensbetrag = o.optDouble("darlehensbetrag", 0.0),
                        restschuld = o.optDouble("restschuld", 0.0),
                        sollzinsProzent = o.optDouble("sollzinsProzent", 0.0),
                        tilgungProzent = o.optDouble("tilgungProzent", 0.0),
                        monatlicheRate = o.optDouble("monatlicheRate", 0.0),
                        startDatum = o.optString("startDatum", ""),
                        zinsbindungBis = o.optString("zinsbindungBis", ""),
                        laufzeitBis = o.optString("laufzeitBis", ""),
                        vermietungsanteilProzent = o.optDouble("vermietungsanteilProzent", 100.0),
                        notiz = o.optString("notiz", ""),
                        aktiv = o.optBoolean("aktiv", true)
                    )
                )
            }

            jsonToPrefs(context, "rent_plan_prefs", root.optJSONObject("rentPlanPrefs"))
            jsonToPrefs(context, "tenant_history_prefs", root.optJSONObject("tenantHistoryPrefs"))
            jsonToPrefs(context, "loan_interest_assignments", root.optJSONObject("loanInterestAssignments"))
            jsonToPrefs(context, "annual_tax_approval_prefs", root.optJSONObject("annualTaxApprovalPrefs"))
            Result(true, "Miet-, Mieterhistorien-, Darlehens- und Jahresfreigabedaten wiederhergestellt")
        } catch (e: Exception) {
            Result(false, e.message ?: e.toString())
        }
    }

    private fun prefsToJson(context: Context, name: String): JSONObject {
        val result = JSONObject()
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (key, value) ->
            when (value) {
                is String -> result.put(key, JSONObject().apply { put("type", "string"); put("value", value) })
                is Int -> result.put(key, JSONObject().apply { put("type", "int"); put("value", value) })
                is Long -> result.put(key, JSONObject().apply { put("type", "long"); put("value", value) })
                is Float -> result.put(key, JSONObject().apply { put("type", "float"); put("value", value.toDouble()) })
                is Boolean -> result.put(key, JSONObject().apply { put("type", "boolean"); put("value", value) })
            }
        }
        return result
    }

    private fun jsonToPrefs(context: Context, name: String, json: JSONObject?) {
        if (json == null) return
        val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val entry = json.optJSONObject(key) ?: continue
            when (entry.optString("type")) {
                "string" -> editor.putString(key, entry.optString("value", ""))
                "int" -> editor.putInt(key, entry.optInt("value", 0))
                "long" -> editor.putLong(key, entry.optLong("value", 0L))
                "float" -> editor.putFloat(key, entry.optDouble("value", 0.0).toFloat())
                "boolean" -> editor.putBoolean(key, entry.optBoolean("value", false))
            }
        }
        editor.apply()
    }
}
