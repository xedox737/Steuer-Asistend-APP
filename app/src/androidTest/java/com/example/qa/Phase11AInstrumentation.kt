package com.example.qa

import androidx.test.runner.AndroidJUnitRunner
import android.os.Bundle
import android.os.Build
import android.util.Log
import com.example.data.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.time.Instant

/** Explicit opt-in QA entry point, separate from the ordinary JUnit runner. */
class Phase11AInstrumentation : AndroidJUnitRunner() {
    private lateinit var qaArguments: Bundle
    override fun onCreate(arguments: Bundle?) {
        qaArguments = arguments ?: Bundle()
        super.onCreate(arguments)
    }

    override fun onStart() {
        try {
            Phase11AIsolation.requireTestPackage(targetContext.packageName, com.example.BuildConfig.DEBUG)
        } catch (failure: IllegalStateException) {
            finish(1, Bundle().apply { putString("error", failure.message) })
            return
        }
        if (!qaArguments.containsKey("phase11aAction")) {
            super.onStart()
            return
        }
        val result = Bundle()
        try {
            if (qaArguments.getString("phase11aAction") == "isolation") {
                check(qaArguments.getString("immopilotDisposableEmulator") == "true")
                check(qaArguments.getString("testPortfolioOnly") == "true")
                val run = requireNotNull(qaArguments.getString("runId"))
                require(Regex("""P11A-\d{4}-\d{2}-\d{2}-[A-Za-z0-9-]+""").matches(run))
                result.putString("isolation", Phase11ASandboxProbe.capture(targetContext).put("runId", run).toString())
                Log.i("Phase11A", "$run isolated sandbox probe completed")
                finish(0, result)
                return
            }
            runBlocking(Dispatchers.IO) {
                val run = requireNotNull(qaArguments.getString("runId"))
                require(Regex("""P11A-\d{4}-\d{2}-\d{2}-[A-Za-z0-9-]+""").matches(run))
                require(qaArguments.getString("testPortfolioOnly") == "true") { "Nur isolierte Testinstallation erlaubt" }
                val action = requireNotNull(qaArguments.getString("phase11aAction"))
                val commit = requireNotNull(qaArguments.getString("commit"))
                require(Regex("[0-9a-f]{40}").matches(commit))
                val label = requireNotNull(qaArguments.getString("label"))
                require(Regex("[a-z0-9-]+").matches(label))
                val db = AppDatabase.getDatabase(targetContext, CoroutineScope(Dispatchers.IO))
                if (action == "seed") seed(db)
                else require(action == "snapshot")
                val info = targetContext.packageManager.getPackageInfo(targetContext.packageName, 0)
                val meta = JSONObject().put("runId", run).put("label", label).put("commit", commit)
                    .put("capturedAt", Instant.now().toString()).put("device", Build.MODEL)
                    .put("android", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT)
                    .put("appVersion", info.versionName).put("source", "DEVICE_INSTRUMENTATION")
                    .put("applicationId", targetContext.packageName).put("uid", android.os.Process.myUid())
                val snapshot = Phase11ASnapshot.capture(targetContext, db, meta)
                val dir = File(targetContext.filesDir, "phase11a/$run").apply { mkdirs() }
                val name = if (label == "baseline") "phase11a-baseline.json" else "phase11a-after-$label.json"
                val output = File(dir, name)
                require(!output.exists()) { "Evidence wird nicht überschrieben: $name" }
                output.writeText(snapshot.toString(2))
                Log.i("Phase11A", "$run $action $label snapshot-sha256=${StableDocumentIdentity.sha256(output.readBytes())}")
                result.putString("snapshot", output.relativeTo(targetContext.filesDir).path)
                db.close()
            }
            finish(0, result)
        } catch (failure: Exception) {
            // Do not dump domain data/credentials into system logs.
            result.putString("error", "Phase11A ${failure.javaClass.simpleName}: ${failure.message}")
            finish(1, result)
        }
    }

    private suspend fun seed(db: AppDatabase) {
        require(qaArguments.getString("confirmEmptyTestInstallation") == "true")
        Phase11APortfolioSeeder.seed(targetContext, db,
            File(targetContext.filesDir, "phase11a-input.zip"),
            requireNotNull(qaArguments.getString("fixtureSha256")),
            requireNotNull(qaArguments.getString("runId")))
    }
}
