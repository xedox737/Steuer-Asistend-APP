package com.example.util

import android.util.Log
import com.example.BuildConfig

/** Only static operation labels belong here. Never pass payloads or throwables. */
internal object DiagnosticLog {
    fun d(tag: String, event: String) { if (BuildConfig.DEBUG) Log.d(tag, event) }
    fun i(tag: String, event: String) { if (BuildConfig.DEBUG) Log.i(tag, event) }
    fun w(tag: String, event: String) { if (BuildConfig.DEBUG) Log.w(tag, event) }
    fun e(tag: String, event: String) { if (BuildConfig.DEBUG) Log.e(tag, event) }
}
