package com.example.qa

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.room.withTransaction
import com.example.data.*
import com.example.util.DatevProfileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

/** One importer for the phone UI and explicit instrumentation seed. Debug source set only. */
object Phase11APortfolioSeeder {
    const val EMPTY_REQUIRED = "Das Testportfolio kann nur in einer leeren ImmoPilot-Testinstallation geladen werden."
    const val INCOMPLETE = "Das Laden wurde unterbrochen oder ist fehlgeschlagen. Diese Testinstallation ist gesperrt. Bitte eine neue leere Testinstallation verwenden; vorhandene Daten werden nicht gelöscht."
    const val ASSET = "phase11a/qa-transport-snapshot.zip"
    private const val JOURNAL = "phase11a_seed_journal"
    private val mutex = Mutex()

    fun requireIsolated(context: Context) = Phase11AIsolation.requireTestPackage(context.packageName,
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)

    suspend fun checkEmpty(context: Context, db: AppDatabase) {
        requireIsolated(context)
        check(context.getSharedPreferences(JOURNAL, 0).getString("state", null) != "pending") { INCOMPLETE }
        checkDomainsEmpty(context, db)
        listOf("managed_documents", "phase11a-originals", "property-images").forEach { name ->
            check(File(context.filesDir, name).walkTopDown().none { it.isFile }) { EMPTY_REQUIRED }
        }
        check(context.getSharedPreferences(JOURNAL, 0).getString("state", null) == null) { EMPTY_REQUIRED }
    }

    private suspend fun checkDomainsEmpty(context: Context, db: AppDatabase) {
        val sqlite = db.openHelper.readableDatabase
        val tables = mutableListOf<String>()
        sqlite.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            while (cursor.moveToNext()) tables += cursor.getString(0)
        }
        tables.filterNot { it.startsWith("sqlite_") || it == "room_master_table" ||
            it == "android_metadata" || it.startsWith("document_search_fts_") }.forEach { table ->
            val quoted = "\"" + table.replace("\"", "\"\"") + "\""
            sqlite.query("SELECT EXISTS(SELECT 1 FROM $quoted LIMIT 1)").use { cursor ->
                check(cursor.moveToFirst() && cursor.getInt(0) == 0) {
                    "$EMPTY_REQUIRED Nichtleere Test-Datentabelle: $table"
                }
            }
        }
        val empty = SupplementalDriveBackup.createPayload(context, db)
        for (key in empty.keys()) {
            val value = empty.get(key)
            // Opening the normal UI automatically persists the untouched default DATEV profile.
            // This is the sole allowed bootstrap value; keep it byte-for-byte during import.
            if (key == "datevProfilePrefs" && pristineDatevDefault(context)) continue
            check(value !is JSONArray || value.length() == 0) { EMPTY_REQUIRED }
            check(value !is JSONObject || value.length() == 0) { EMPTY_REQUIRED }
        }
    }

    private fun pristineDatevDefault(context: Context): Boolean {
        val prefs = context.getSharedPreferences("datev_kanzleiprofil_prefs", 0)
        if (prefs.all.keys != setOf("active_profile_json")) return false
        return runCatching {
            DatevProfileService.importProfileFromJson(requireNotNull(prefs.getString("active_profile_json", null)))
                ?.copy(lastModified = 0) == DatevProfile.createDefaultSkr03().copy(lastModified = 0)
        }.getOrDefault(false)
    }

    suspend fun seedBundled(context: Context, db: AppDatabase, runId: String) {
        requireIsolated(context)
        withContext(Dispatchers.IO) {
            val expected = context.assets.open("phase11a/qa-transport-snapshot.sha256").bufferedReader().use { it.readText().trim() }
            val temporary = File.createTempFile("phase11a-fixture-", ".zip", context.cacheDir)
            try {
                context.assets.open(ASSET).use { input -> temporary.outputStream().use { input.copyTo(it) } }
                seed(context, db, temporary, expected, runId)
            } finally { temporary.delete() } // Only our own disposable input copy; never app domain data.
        }
    }

    suspend fun seed(context: Context, db: AppDatabase, bundle: File, sha256: String, runId: String) {
        requireIsolated(context) // Before database, preferences, bundle or journal access.
        require(Regex("P11A-\\d{4}-\\d{2}-\\d{2}-[A-Za-z0-9-]+").matches(runId))
        mutex.withLock {
            withContext(Dispatchers.IO) {
                checkEmpty(context, db)
                check(StableDocumentIdentity.sha256(bundle.readBytes()) == sha256) { "Die Testportfolio-Datei ist beschädigt." }
                val repository = ReceiptRepository(db.receiptDao(), db.propertyDao(), db.receiptEntityDao(), db.belegDao(),
                    db.exportAuditDao(), db.receiptDocumentDao(), db.managedDocumentDao())
                val drive = DrivePersistenceRepository(context, repository)
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
                    require(docs.length() == 70)
                    val originals = linkedMapOf<String, Pair<File, ByteArray>>()
                    // Validate every byte and receipt/document relation before any domain write.
                    for (i in 0 until docs.length()) {
                        val doc = docs.getJSONObject(i)
                        val receipt = receipts.single { it.internalId == doc.getString("receiptInternalId") }
                        require(doc.getString("documentId") == "receipt:${receipt.internalId}" && doc.getString("propertyId") == receipt.propertyId)
                        if (doc.getLong("fileSizeBytes") <= 0) continue
                        require(doc.getString("mimeType") in setOf("application/pdf", "image/jpeg", "image/png", "image/webp"))
                        val id = doc.getString("driveFileId")
                        val candidates = zip.entries().asSequence().filter { it.name.startsWith("originals/$id.") }.toList()
                        require(candidates.size == 1)
                        val bytes = zip.getInputStream(candidates.single()).use { it.readBytes() }
                        val hash = doc.getString("sha256")
                        require(Regex("[0-9a-f]{64}").matches(hash))
                        require(StableDocumentIdentity.sha256(bytes) == hash && bytes.size.toLong() == doc.getLong("fileSizeBytes"))
                        val extension = candidates.single().name.substringAfterLast('.').lowercase(java.util.Locale.ROOT)
                        val mimeByExtension = mapOf("pdf" to "application/pdf", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
                            "png" to "image/png", "webp" to "image/webp")
                        require(mimeByExtension[extension] == doc.getString("mimeType"))
                        // Use the existing FileProvider's private document root for later viewer checks.
                        val file = File(context.filesDir, "managed_documents/phase11a-$hash.$extension")
                        require(!file.exists())
                        require(originals.put(doc.getString("documentId"), file to bytes) == null)
                        doc.put("driveFileId", JSONObject.NULL).put("driveFolderId", JSONObject.NULL)
                    }
                    require(originals.size == 60)
                    if (pristineDatevDefault(context)) payload.remove("datevProfilePrefs")
                    // Durable marker precedes files/Room/preferences. Failure or process death blocks retry.
                    val journal = context.getSharedPreferences(JOURNAL, 0)
                    check(journal.edit().putString("state", "pending").putString("runId", runId)
                        .putString("fixtureSha256", sha256).commit()) { "Ladeprotokoll konnte nicht gespeichert werden." }
                    try {
                        originals.values.forEach { (file, bytes) ->
                            check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
                            check(file.createNewFile())
                            file.outputStream().use { it.write(bytes); it.fd.sync() }
                        }
                        db.withTransaction {
                            // Re-check Room under the write transaction to reject concurrent user edits.
                            checkDomainsEmpty(context, db)
                            receipts.forEach { receipt ->
                                repository.insert(receipt.copy(imageUrl = originals["receipt:${receipt.internalId}"]?.first?.absolutePath.orEmpty(),
                                    driveFileId = null, driveFolderId = null, driveMetadataFileId = null, driveRevision = null,
                                    syncStatus = "PENDING", lastSyncedAt = null, isArchivedToDrive = false))
                            }
                            SupplementalDriveBackup.restorePayload(context, db, payload, replaceManagedDocuments = true,
                                restoreMode = RestoreMode.REPLACE_FULL, localDocumentsBeforeCore = emptyMap())
                            repository.getAllManagedDocuments().forEach { doc ->
                                originals[doc.documentId]?.first?.let { repository.upsertManagedDocument(doc.copy(localUri = it.absolutePath)) }
                            }
                            check(db.propertyDao().getAllProperties().size == 5)
                            check(repository.getAllReceiptsList().size == 70 && repository.getAllManagedDocuments().size == 70)
                            check(db.bankDao().getAllTransactions().size == 120 && db.logbookDao().getAllTrips().size == 10)
                            val unitCount = db.propertyDao().getAllProperties().sumOf { drive.getWohneinheitenFromPrefs(it).size }
                            require(unitCount in setOf(17, 21))
                            originals.values.forEach { (file, bytes) -> check(file.readBytes().contentEquals(bytes)) }
                        }
                        check(journal.edit().putString("state", "complete").commit())
                        android.util.Log.i("Phase11A", "$runId seed complete originals=60 receipts=70 properties=5")
                    } catch (failure: Exception) {
                        android.util.Log.e("Phase11A", "$runId seed incomplete ${failure.javaClass.simpleName}")
                        throw IllegalStateException(INCOMPLETE, failure)
                    }
                }
            }
        }
    }
}
