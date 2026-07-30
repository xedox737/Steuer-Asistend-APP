package com.example.util

import android.content.Context
import com.example.data.DatevProfile
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object DatevProfileService {

    private const val PREFS_NAME = "datev_kanzleiprofil_prefs"
    private const val KEY_ACTIVE_PROFILE_JSON = "active_profile_json"
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val jsonAdapter = moshi.adapter(DatevProfile::class.java)

    fun getActiveProfile(context: Context): DatevProfile {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ACTIVE_PROFILE_JSON, null)
        return if (!json.isNullOrBlank()) {
            try {
                jsonAdapter.fromJson(json) ?: DatevProfile.createDefaultSkr03()
            } catch (e: Exception) {
                DatevProfile.createDefaultSkr03()
            }
        } else {
            val defaultProfile = DatevProfile.createDefaultSkr03()
            saveActiveProfile(context, defaultProfile)
            defaultProfile
        }
    }

    fun saveActiveProfile(context: Context, profile: DatevProfile) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = jsonAdapter.toJson(profile)
        prefs.edit().putString(KEY_ACTIVE_PROFILE_JSON, json).apply()
    }

    fun exportProfileToJson(profile: DatevProfile): String {
        return jsonAdapter.toJson(profile)
    }

    fun importProfileFromJson(json: String): DatevProfile? {
        return try {
            jsonAdapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }
}
