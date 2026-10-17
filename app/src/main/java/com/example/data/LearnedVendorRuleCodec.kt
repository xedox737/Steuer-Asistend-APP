package com.example.data

import com.example.ui.LearnedVendorRule
import org.json.JSONObject

/** One rule is an object; vendor and unit labels can contain arbitrary separators. */
internal object LearnedVendorRuleCodec {
    fun encode(rule: LearnedVendorRule): String = JSONObject().apply {
        put("aussteller", rule.aussteller); put("hauptkategorie", rule.hauptkategorie)
        put("unterkategorie", rule.unterkategorie); put("kontoNr", rule.kontoNr)
        put("wohneinheit", rule.wohneinheit); put("count", rule.count)
    }.toString()

    fun decode(raw: String): LearnedVendorRule = if (raw.trimStart().startsWith("{")) {
        val obj = JSONObject(raw)
        LearnedVendorRule(obj.getString("aussteller"), obj.getString("hauptkategorie"),
            obj.getString("unterkategorie"), obj.getString("kontoNr"), obj.getString("wohneinheit"), obj.getInt("count"))
    } else {
        val parts = raw.split("|||")
        require(parts.size == 5 || parts.size == 6) { "Gespeicherte Händlerregel ist mehrdeutig. Bitte prüfen; Rohdaten bleiben erhalten." }
        LearnedVendorRule(parts[0], parts[1], parts[2], parts[3], parts[4],
            if (parts.size == 6) requireNotNull(parts[5].toIntOrNull()) else 1)
    }
}
