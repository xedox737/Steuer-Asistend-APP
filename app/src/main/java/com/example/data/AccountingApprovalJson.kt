package com.example.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lossless local storage for user-confirmed accounting allocations and booking proposals.
 * Empty or malformed legacy values are read as empty lists and never converted into approvals.
 */
object AccountingApprovalJson {
    fun encodeAllocations(values: List<PersistedAllocation>): String =
        JSONArray().apply {
            values.forEach { value ->
                put(JSONObject().apply {
                    put("id", value.id)
                    put("description", value.description)
                    put("percent", value.percent)
                    put("amountCent", value.amountCent)
                })
            }
        }.toString()

    fun decodeAllocations(json: String?): List<PersistedAllocation> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.getJSONObject(index)
                    add(
                        PersistedAllocation(
                            id = value.optString("id", ""),
                            description = value.optString("description", ""),
                            percent = value.optDouble("percent", 0.0),
                            amountCent = value.optLong("amountCent", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encodeBookingProposals(values: List<PersistedBookingProposal>): String =
        JSONArray().apply {
            values.forEach { value ->
                put(JSONObject().apply {
                    put("id", value.id)
                    put("konto", value.konto)
                    put("gegenkonto", value.gegenkonto)
                    put("betragCent", value.betragCent)
                    put("buSchluessel", value.buSchluessel)
                })
            }
        }.toString()

    fun decodeBookingProposals(json: String?): List<PersistedBookingProposal> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.getJSONObject(index)
                    add(
                        PersistedBookingProposal(
                            id = value.optString("id", ""),
                            konto = value.optString("konto", ""),
                            gegenkonto = value.optString("gegenkonto", ""),
                            betragCent = value.optLong("betragCent", 0L),
                            buSchluessel = value.optString("buSchluessel", "")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
