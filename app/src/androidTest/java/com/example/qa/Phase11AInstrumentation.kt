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
import java.util.zip.ZipFile

/** Explicit opt-in QA entry point, separate from the ordinary JUnit runner. */
class Phase11AInstrumentation : AndroidJUnitRunner() {
    private lateinit var qaArguments: Bundle
    override fun onCreate(arguments: Bundle?) {
        qaArguments = arguments ?: Bundle()
        super.onCreate(arguments)
    }

    override fun onStart() {
        if (!qaArguments.containsKey("phase11aAction")) {
            super.onStart()
            return
        }
        val result = Bundle()
        try {
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

    /** Preparation only. Never a Drive/restore acceptance result. Refuses occupied targets. */
    private suspend fun seed(db: AppDatabase) {
        require(qaArguments.getString("confirmEmptyTestInstallation") == "true")
        // Include legacy/secondary/orphan domain tables, not just backup payloads.
        // Only SQLite/Room/FTS structural metadata is exempt from the empty-target gate.
        val sqlite = db.openHelper.readableDatabase
        val tables = mutableListOf<String>()
        sqlite.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            while (cursor.moveToNext()) tables += cursor.getString(0)
        }
        tables.filterNot { it.startsWith("sqlite_") || it == "room_master_table" ||
            it == "android_metadata" || it.startsWith("document_search_fts_") }.forEach { table ->
            val quoted = "\"" + table.replace("\"", "\"\"") + "\""
            sqlite.query("SELECT EXISTS(SELECT 1 FROM $quoted LIMIT 1)").use { cursor ->
                require(cursor.moveToFirst() && cursor.getInt(0) == 0) { "Nichtleere Test-Datentabelle: $table" }
            }
        }
        val empty = SupplementalDriveBackup.createPayload(targetContext, db)
        for (key in empty.keys()) {
            val value = empty.get(key)
            require(value !is org.json.JSONArray || value.length() == 0) { "Nichtleerer Testbestand: $key" }
            require(value !is JSONObject || value.length() == 0) { "Nichtleere Testeinstellungen: $key" }
        }
        require(db.receiptDao().getAllReceiptsIncludingDeletedList().isEmpty())
        val bundle = File(targetContext.filesDir, "phase11a-input.zip")
        require(StableDocumentIdentity.sha256(bundle.readBytes()) == qaArguments.getString("fixtureSha256"))
        val repository = ReceiptRepository(db.receiptDao(), db.propertyDao(), db.receiptEntityDao(), db.belegDao(),
            db.exportAuditDao(), db.receiptDocumentDao(), db.managedDocumentDao())
        val drive = DrivePersistenceRepository(targetContext, repository)
        ZipFile(bundle).use { zip ->
            fun text(name: String) = zip.getInputStream(requireNotNull(zip.getEntry(name))).bufferedReader().use { it.readText() }
            val payload = JSONObject(text("supplemental.json"))
            SupplementalDriveBackup.validatePayload(payload)
            val properties = payload.getJSONArray("properties")
            require(properties.length() == 5)
            for (i in 0 until properties.length()) require(properties.getJSONObject(i).getString("propertyId") == "e10b-property-${i + 1}")
            val entries = zip.entries().asSequence().filter { it.name.startsWith("metadata/") && it.name.endsWith(".json") }.toList()
            require(entries.size == 70)
            val receipts = entries.map { with(drive) { parsePersistedReceipt(text(it.name)).toLocalReceipt(null) } }
            require(receipts.map { it.internalId }.toSet().size == 70)
            require(receipts.all { it.internalId.startsWith("e10b-") || it.propertyId.startsWith("e10b-property-") })
            val docs = payload.getJSONArray("managedDocuments")
            val files = mutableMapOf<String, File>()
            // Verify every byte before importing any database row. Do not extract supplied paths.
            for (i in 0 until docs.length()) {
                val doc = docs.getJSONObject(i)
                if (doc.getLong("fileSizeBytes") <= 0) continue
                val id = doc.getString("driveFileId")
                val candidates = zip.entries().asSequence().filter { it.name.startsWith("originals/$id.") }.toList()
                require(candidates.size == 1)
                val bytes = zip.getInputStream(candidates.single()).use { it.readBytes() }
                require(StableDocumentIdentity.sha256(bytes) == doc.getString("sha256") && bytes.size.toLong() == doc.getLong("fileSizeBytes"))
                val file = File(targetContext.filesDir, "phase11a-originals/${doc.getString("sha256")}")
                file.parentFile!!.mkdirs()
                file.writeBytes(bytes)
                files[doc.getString("documentId")] = file
                // Fake transport IDs from Phase10B are not valid live Drive IDs.
                doc.put("driveFileId", JSONObject.NULL).put("driveFolderId", JSONObject.NULL)
            }
            require(files.size == 60)
            receipts.forEach { receipt ->
                repository.insert(receipt.copy(imageUrl = files["receipt:${receipt.internalId}"]?.absolutePath.orEmpty(),
                    driveFileId = null, driveFolderId = null, driveMetadataFileId = null, driveRevision = null,
                    syncStatus = "PENDING", lastSyncedAt = null, isArchivedToDrive = false))
            }
            SupplementalDriveBackup.restorePayload(targetContext, db, payload, replaceManagedDocuments = true,
                restoreMode = RestoreMode.REPLACE_FULL, localDocumentsBeforeCore = emptyMap())
            repository.getAllManagedDocuments().forEach { doc ->
                files[doc.documentId]?.let { repository.upsertManagedDocument(doc.copy(localUri = it.absolutePath)) }
            }
        }
    }
}
