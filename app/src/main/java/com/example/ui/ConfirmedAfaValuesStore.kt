package com.example.ui

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/** Property-scoped, additive record of professionally confirmed AfA history. */
internal data class ConfirmedAfaValues(
    val cutoffDate: String,
    val cumulativeAfa: Double,
    val remainingBookValue: Double,
    val source: String
)

internal object ConfirmedAfaValuesStore {
    private const val PREFS = "afa_confirmed_values_prefs"

    fun read(context: Context, propertyId: String): ConfirmedAfaValues? {
        if (propertyId.isBlank()) return null
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(propertyId, null) ?: return null
        return runCatching {
            val value = JSONObject(raw)
            ConfirmedAfaValues(
                value.getString("cutoffDate"),
                value.getDouble("cumulativeAfa"),
                value.getDouble("remainingBookValue"),
                value.getString("source")
            ).takeIf {
                LocalDate.parse(it.cutoffDate) != null &&
                    it.cumulativeAfa.isFinite() && it.cumulativeAfa >= 0.0 &&
                    it.remainingBookValue.isFinite() && it.remainingBookValue >= 0.0 &&
                    it.source.isNotBlank()
            }
        }.getOrNull()
    }

    fun write(context: Context, propertyId: String, value: ConfirmedAfaValues): Boolean {
        require(propertyId.isNotBlank())
        require(value.cumulativeAfa.isFinite() && value.cumulativeAfa >= 0.0)
        require(value.remainingBookValue.isFinite() && value.remainingBookValue >= 0.0)
        LocalDate.parse(value.cutoffDate)
        require(value.source.isNotBlank())
        val json = JSONObject()
            .put("cutoffDate", value.cutoffDate)
            .put("cumulativeAfa", value.cumulativeAfa)
            .put("remainingBookValue", value.remainingBookValue)
            .put("source", value.source)
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(propertyId, json.toString()).commit()
    }
}
