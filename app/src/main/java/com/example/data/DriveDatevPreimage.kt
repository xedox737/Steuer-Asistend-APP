package com.example.data

import com.example.api.canonicalDriveJson
import org.json.JSONObject

/** Conserve the remote representation of an equivalent DATEV profile. Never infer recency
 * from lastModified: independently created default profiles have different timestamps.
 * This also covers the same profile embedded in the supplemental preference snapshot. */
internal object DriveDatevPreimage {
    private val strictJson = com.squareup.moshi.Moshi.Builder().build().adapter(Any::class.java)
    private val fields = setOf("profileId", "profileName", "version", "beraterNummer", "mandantenNummer",
        "mandantenName", "wirtschaftsjahrBeginn", "sachkontenLaenge", "kontenrahmen", "waehrung",
        "festschreibungskennzeichen", "belegnummernlogik", "standardBuchungstext",
        "standardGegenkontoEinnahmen", "standardGegenkontoAusgaben", "defaultEinnahmenKonto",
        "defaultAusgabenKonto", "categoryKontoMapJson", "kost1Logic", "kost2Logic",
        "buSchluesselNormalMwst", "buSchluesselErmaessigtMwst", "privateShareExportMode", "lastModified")

    private fun objectOrNull(raw: Any?): JSONObject? = (raw as? String)?.let {
        // Use the same strict JSON grammar as DATEV's Moshi reader. org.json alone also
        // accepts trailing data/unquoted strings, which DATEV interprets as an invalid map.
        runCatching { strictJson.fromJson(it); JSONObject(it) }.getOrNull()
    }

    private fun preference(root: JSONObject): JSONObject? =
        root.optJSONObject("datevProfilePrefs")?.optJSONObject("active_profile_json")
            ?.takeIf { it.opt("type") == "string" }

    private fun comparable(profile: JSONObject): JSONObject = JSONObject(profile.toString()).apply {
        remove("lastModified")
        // This field is itself serialized JSON. Only account maps of string values qualify;
        // malformed maps and any substantive account difference keep the ordinary guard.
        objectOrNull(opt("categoryKontoMapJson"))?.takeIf { map ->
            map.keys().asSequence().all { map.opt(it) is String }
        }?.let { put("categoryKontoMapJson", it) }
    }

    private fun equivalent(before: JSONObject, candidate: JSONObject): Boolean {
        fun valid(profile: JSONObject): Boolean =
            (profile.opt("profileId") as? String)?.isNotBlank() == true &&
                profile.opt("version") is Int && profile.getInt("version") > 0 &&
                profile.opt("lastModified").let { it is Long && it >= 0 || it is Int && it >= 0 }
        return valid(before) && valid(candidate) &&
            canonicalDriveJson(comparable(before)) == canonicalDriveJson(comparable(candidate))
    }

    fun conserve(before: String, candidate: String): String {
        val old = objectOrNull(before) ?: return candidate
        val next = objectOrNull(candidate) ?: return candidate
        var changed = false
        val profile = old.optJSONObject("activeProfile")
        val nextProfile = next.optJSONObject("activeProfile")
        if (profile != null && nextProfile != null && equivalent(profile, nextProfile)) {
            next.put("activeProfile", profile)
            changed = true
        }
        val stored = preference(old)
        val nextStored = preference(next)
        val storedProfile = objectOrNull(stored?.opt("value"))
        val nextStoredProfile = objectOrNull(nextStored?.opt("value"))
        if (storedProfile != null && nextStoredProfile != null && equivalent(storedProfile, nextStoredProfile)) {
            nextStored!!.put("value", stored!!.getString("value"))
            changed = true
        }
        return if (changed) next.toString() else candidate
    }

    /** Fixed schema names only: JSON keys may themselves contain private/user supplied data. */
    fun differentFields(before: String, candidate: String): List<String> {
        val old = objectOrNull(before) ?: return emptyList()
        val next = objectOrNull(candidate) ?: return emptyList()
        val a = old.optJSONObject("activeProfile") ?: objectOrNull(preference(old)?.opt("value")) ?: return emptyList()
        val b = next.optJSONObject("activeProfile") ?: objectOrNull(preference(next)?.opt("value")) ?: return emptyList()
        return (a.keys().asSequence().toSet() + b.keys().asSequence().toSet()).filter {
            a.has(it) != b.has(it) || canonicalDriveJson(a.opt(it)) != canonicalDriveJson(b.opt(it))
        }.map { if (it in fields) it else "unknownFields" }.distinct().sorted()
    }
}
