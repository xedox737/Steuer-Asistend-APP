package com.example.qa

import android.content.Context
import android.net.Uri
import android.os.Build
import com.example.BuildConfig
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.io.File

/** Emulator evidence only; writes exclusively to the isolated application's own stores. */
object Phase11ASandboxProbe {
    fun capture(context: Context): JSONObject {
        Phase11AIsolation.requireTestPackage(context.packageName, BuildConfig.DEBUG)
        check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Disposable emulator only" }
        val db = AppDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
        fun scalar(sql: String): String = db.openHelper.readableDatabase.query(sql).use {
            check(it.moveToFirst()); it.getString(0)
        }
        check(scalar("PRAGMA integrity_check") == "ok")
        check(scalar("SELECT count(*) FROM property_metadata") == "5")
        check(scalar("SELECT count(*) FROM receipts") == "70")
        val prefs = context.getSharedPreferences("phase11a_isolation", Context.MODE_PRIVATE)
        check(prefs.edit().putString("sentinel", "isolated-test").commit())
        check(prefs.getString("sentinel", null) == "isolated-test")
        val paths = JSONObject()
        listOf("files" to context.filesDir, "cache" to context.cacheDir,
            "externalFiles" to requireNotNull(context.getExternalFilesDir(null)),
            "externalCache" to requireNotNull(context.externalCacheDir)).forEach { (name, dir) ->
            val file = File(dir, "phase11a-isolation.txt")
            file.writeText("isolated-test")
            check(file.readText() == "isolated-test")
            paths.put(name, file.absolutePath)
        }
        val productionRoot = File(context.dataDir.parentFile, Phase11AIsolation.PRODUCTION)
        listOf("databases/receipt_database", "shared_prefs/phase11a_isolation.xml",
            "files/phase11a-isolation.txt").forEach { relative ->
            check(runCatching { File(productionRoot, relative).inputStream().use { it.read() } }.isFailure) {
                "Unexpected access to production sandbox: $relative"
            }
        }
        val denial = runCatching {
            context.contentResolver.openInputStream(Uri.parse(
                "content://${Phase11AIsolation.PRODUCTION}.provider/cache/phase11a-isolation.txt"))?.use { it.read() }
        }.exceptionOrNull()
        check(denial is SecurityException || denial is java.io.FileNotFoundException) {
            "Production FileProvider must deny the isolated UID: $denial"
        }
        db.close()
        return JSONObject().put("applicationId", context.packageName).put("uid", android.os.Process.myUid())
            .put("dataDir", context.dataDir.absolutePath)
            .put("roomPath", context.getDatabasePath("receipt_database").absolutePath)
            .put("roomProperties", 5).put("roomReceipts", 70).put("roomIntegrity", "ok")
            .put("preferences", "isolated-test").put("paths", paths)
            .put("productionPrivateReadsDenied", true).put("productionProviderDenied", true)
            .put("productionProviderDenial", denial.javaClass.simpleName)
    }
}
