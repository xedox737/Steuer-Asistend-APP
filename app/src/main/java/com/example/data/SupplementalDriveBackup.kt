package com.example.data

import android.content.Context
import com.example.api.GoogleDriveClient
import org.json.JSONArray
import org.json.JSONObject

object SupplementalDriveBackup {
    private const val ENTITY_TYPE = "supplementalBackup"
    private const val FILE_NAME = "supplementalBackup.json"
    internal const val SCHEMA_VERSION = 2
    data class Result(val success: Boolean, val message: String)

    suspend fun backup(context: Context, database: AppDatabase, accessToken: String, systemFolderId: String): Result =
        try {
            val root = createPayload(context, database)
            val existing = GoogleDriveClient.findFileByAppProperty(accessToken, systemFolderId, ENTITY_TYPE)
            val result = if (existing != null) GoogleDriveClient.updateJson(accessToken, existing.id, root.toString(4))
            else GoogleDriveClient.uploadJson(
                accessToken, systemFolderId, FILE_NAME, root.toString(4),
                mapOf("appName" to "ImmobilienBelegApp", "entityType" to ENTITY_TYPE, "schemaVersion" to SCHEMA_VERSION.toString())
            )
            Result(result.success, result.errorMessage ?: if (result.success) "Zusatzdaten gesichert" else "Zusatzdaten-Backup fehlgeschlagen")
        } catch (e: Exception) {
            Result(false, e.message ?: "Zusatzdaten-Backup fehlgeschlagen")
        }

    suspend fun restore(context: Context, database: AppDatabase, accessToken: String, systemFolderId: String): Result =
        try {
            val file = GoogleDriveClient.findFileByAppProperty(accessToken, systemFolderId, ENTITY_TYPE)
                ?: return Result(true, "Keine Zusatzdaten-Sicherung vorhanden")
            restorePayload(context, database, JSONObject(GoogleDriveClient.downloadJson(accessToken, file.id)))
            Result(true, "Zusatzdaten einschließlich Fahrtenbuch wiederhergestellt")
        } catch (e: Exception) {
            Result(false, e.message ?: "Zusatzdaten-Wiederherstellung fehlgeschlagen")
        }

    internal suspend fun createPayload(context: Context, database: AppDatabase): JSONObject = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("loans", JSONArray().apply { database.loanDao().getAllLoans().forEach { put(it.toJson()) } })
        put("logbookTrips", JSONArray().apply { database.logbookDao().getAllTrips().forEach { put(it.toJson()) } })
        put("standardRoutes", JSONArray().apply { database.logbookDao().getAllStandardRoutes().forEach { put(it.toJson()) } })
        put("rentPlanPrefs", prefsToJson(context, "rent_plan_prefs"))
        put("tenantHistoryPrefs", prefsToJson(context, "tenant_history_prefs"))
        put("loanInterestAssignments", prefsToJson(context, "loan_interest_assignments"))
        put("annualTaxApprovalPrefs", prefsToJson(context, "annual_tax_approval_prefs"))
        // ai_provider_settings is deliberately excluded: no API key may enter Drive backup.
    }

    internal suspend fun restorePayload(context: Context, database: AppDatabase, root: JSONObject) {
        val loans = root.optJSONArray("loans") ?: JSONArray()
        for (index in 0 until loans.length()) database.loanDao().upsertLoan(loans.getJSONObject(index).toLoan())
        val trips = root.optJSONArray("logbookTrips") ?: JSONArray()
        for (index in 0 until trips.length()) database.logbookDao().upsertTrip(trips.getJSONObject(index).toTrip())
        val routes = root.optJSONArray("standardRoutes") ?: JSONArray()
        for (index in 0 until routes.length()) database.logbookDao().upsertStandardRoute(routes.getJSONObject(index).toStandardRoute())
        jsonToPrefs(context, "rent_plan_prefs", root.optJSONObject("rentPlanPrefs"))
        jsonToPrefs(context, "tenant_history_prefs", root.optJSONObject("tenantHistoryPrefs"))
        jsonToPrefs(context, "loan_interest_assignments", root.optJSONObject("loanInterestAssignments"))
        jsonToPrefs(context, "annual_tax_approval_prefs", root.optJSONObject("annualTaxApprovalPrefs"))
    }

    private fun Loan.toJson() = JSONObject().apply {
        put("id", id); put("bezeichnung", bezeichnung); put("bank", bank)
        put("darlehensbetrag", darlehensbetrag); put("restschuld", restschuld)
        put("sollzinsProzent", sollzinsProzent); put("tilgungProzent", tilgungProzent)
        put("monatlicheRate", monatlicheRate); put("startDatum", startDatum)
        put("zinsbindungBis", zinsbindungBis); put("laufzeitBis", laufzeitBis)
        put("vermietungsanteilProzent", vermietungsanteilProzent); put("notiz", notiz); put("aktiv", aktiv)
    }

    private fun JSONObject.toLoan() = Loan(
        id = optInt("id", 0), bezeichnung = optString("bezeichnung", ""), bank = optString("bank", ""),
        darlehensbetrag = optDouble("darlehensbetrag", 0.0), restschuld = optDouble("restschuld", 0.0),
        sollzinsProzent = optDouble("sollzinsProzent", 0.0), tilgungProzent = optDouble("tilgungProzent", 0.0),
        monatlicheRate = optDouble("monatlicheRate", 0.0), startDatum = optString("startDatum", ""),
        zinsbindungBis = optString("zinsbindungBis", ""), laufzeitBis = optString("laufzeitBis", ""),
        vermietungsanteilProzent = optDouble("vermietungsanteilProzent", 100.0),
        notiz = optString("notiz", ""), aktiv = optBoolean("aktiv", true)
    )

    private fun LogbookTrip.toJson() = JSONObject().apply {
        put("id", id); put("date", date); put("time", time); put("purpose", purpose)
        put("propertyReference", propertyReference); put("startAddress", startAddress)
        put("destinationAddress", destinationAddress); put("stopsJson", stopsJson)
        put("routeMode", routeMode); put("sameReturnRoute", sameReturnRoute)
        put("taxDistanceKm", taxDistanceKm); put("kilometerSource", kilometerSource)
        putNullable("aiEstimatedKm", aiEstimatedKm); putNullable("routedKm", routedKm)
        putNullable("gpsMeasuredKm", gpsMeasuredKm); putNullable("manualKm", manualKm)
        putNullable("odometerStartKm", odometerStartKm); putNullable("odometerEndKm", odometerEndKm)
        putNullable("standardRouteId", standardRouteId); put("plausibilityStatus", plausibilityStatus)
        put("manuallyConfirmed", manuallyConfirmed); putNullable("sourceReceiptId", sourceReceiptId)
        putNullable("expenseReceiptId", expenseReceiptId); put("routeProvider", routeProvider)
        put("routeCalculatedAt", routeCalculatedAt); putNullable("routeDurationSeconds", routeDurationSeconds)
        put("correctionReason", correctionReason); put("correctionNote", correctionNote)
        put("routeSignature", routeSignature); put("note", note); put("createdAt", createdAt); put("updatedAt", updatedAt)
    }

    private fun JSONObject.toTrip() = LogbookTrip(
        id = optLong("id", 0L), date = optString("date", ""), time = optString("time", ""),
        purpose = optString("purpose", ""), propertyReference = optString("propertyReference", ""),
        startAddress = optString("startAddress", ""), destinationAddress = optString("destinationAddress", ""),
        stopsJson = optString("stopsJson", ""), routeMode = optString("routeMode", TripRouteMode.EINFACH.name),
        sameReturnRoute = optBoolean("sameReturnRoute", false), taxDistanceKm = optDouble("taxDistanceKm", 0.0),
        kilometerSource = optString("kilometerSource", KilometerSource.MANUELL.name),
        aiEstimatedKm = nullableDouble("aiEstimatedKm"), routedKm = nullableDouble("routedKm"),
        gpsMeasuredKm = nullableDouble("gpsMeasuredKm"), manualKm = nullableDouble("manualKm"),
        odometerStartKm = nullableDouble("odometerStartKm"), odometerEndKm = nullableDouble("odometerEndKm"),
        standardRouteId = nullableLong("standardRouteId"),
        plausibilityStatus = optString("plausibilityStatus", TripPlausibilityStatus.PRUEFEN.name),
        manuallyConfirmed = optBoolean("manuallyConfirmed", false),
        sourceReceiptId = nullableInt("sourceReceiptId"), expenseReceiptId = nullableInt("expenseReceiptId"),
        routeProvider = optString("routeProvider", ""), routeCalculatedAt = optString("routeCalculatedAt", ""),
        routeDurationSeconds = nullableLong("routeDurationSeconds"), correctionReason = optString("correctionReason", ""),
        correctionNote = optString("correctionNote", ""), routeSignature = optString("routeSignature", ""),
        note = optString("note", ""), createdAt = optString("createdAt", ""), updatedAt = optString("updatedAt", "")
    )

    private fun StandardRoute.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("startAddress", startAddress); put("destinationAddress", destinationAddress)
        put("stopsJson", stopsJson); put("routeMode", routeMode); put("sameReturnRoute", sameReturnRoute)
        put("distanceKm", distanceKm); put("active", active); put("routeSignature", routeSignature)
        put("sourceProvider", sourceProvider); put("createdAt", createdAt); put("updatedAt", updatedAt)
    }

    private fun JSONObject.toStandardRoute() = StandardRoute(
        id = optLong("id", 0L), name = optString("name", ""), startAddress = optString("startAddress", ""),
        destinationAddress = optString("destinationAddress", ""), stopsJson = optString("stopsJson", ""),
        routeMode = optString("routeMode", TripRouteMode.EINFACH.name), sameReturnRoute = optBoolean("sameReturnRoute", false),
        distanceKm = optDouble("distanceKm", 0.0), active = optBoolean("active", true),
        routeSignature = optString("routeSignature", ""), sourceProvider = optString("sourceProvider", ""),
        createdAt = optString("createdAt", ""), updatedAt = optString("updatedAt", "")
    )

    private fun JSONObject.putNullable(name: String, value: Any?) { put(name, value ?: JSONObject.NULL) }
    private fun JSONObject.nullableDouble(name: String): Double? = if (!has(name) || isNull(name)) null else optDouble(name)
    private fun JSONObject.nullableLong(name: String): Long? = if (!has(name) || isNull(name)) null else optLong(name)
    private fun JSONObject.nullableInt(name: String): Int? = if (!has(name) || isNull(name)) null else optInt(name)

    private fun prefsToJson(context: Context, name: String): JSONObject = JSONObject().apply {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (key, value) ->
            when (value) {
                is String -> put(key, typed("string", value))
                is Int -> put(key, typed("int", value))
                is Long -> put(key, typed("long", value))
                is Float -> put(key, typed("float", value.toDouble()))
                is Boolean -> put(key, typed("boolean", value))
            }
        }
    }

    private fun typed(type: String, value: Any) = JSONObject().put("type", type).put("value", value)

    private fun jsonToPrefs(context: Context, name: String, json: JSONObject?) {
        if (json == null) return
        val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next(); val entry = json.optJSONObject(key) ?: continue
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

