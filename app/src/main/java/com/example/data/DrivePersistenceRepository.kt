package com.example.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.example.api.GoogleDriveClient
import com.example.api.DriveFolder
import com.example.api.DriveFile
import com.example.api.DriveFileResult
import com.example.api.detectMagicBytesFormat
import com.example.ui.WohneinheitStatus
import com.example.ui.LearnedVendorRule
import com.example.util.DatevProfileService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class DriveTestReferences(
    val internalId: String,
    val displayId: String,
    val mainDriveFileId: String,
    val metadataFileId: String
)

data class IndexValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

data class DriveInventoryCheck(
    val hasDriveFolder: Boolean = false,
    val receiptCountInIndex: Int = 0,
    val metadataFileCount: Int = 0,
    val mainDocCount: Int = 0,
    val duplicateCount: Int = 0,
    val missingFileCount: Int = 0,
    val schemaVersion: Int = 1,
    val propertyFound: Boolean = false,
    val propertyName: String = "",
    val unitsCount: Int = 0,
    val datevProfileFound: Boolean = false,
    val learnedRulesFound: Boolean = false,
    val exportsCount: Int = 0,
    val canRestore: Boolean = false,
    val issues: List<String> = emptyList()
)

data class ReceiptPreviewSummary(
    val internalId: String,
    val displayId: String?,
    val aussteller: String,
    val datum: String,
    val bruttobetragCent: Long,
    val metadataReachable: Boolean,
    val mainDocReachable: Boolean,
    val issues: List<String> = emptyList()
)

data class DriveRestorePreviewResult(
    val success: Boolean = false,
    val inventory: DriveInventoryCheck = DriveInventoryCheck(),
    val errorMessage: String? = null,
    val receiptPreviews: List<ReceiptPreviewSummary> = emptyList()
)

data class DriveRestoreReport(
    val timestamp: String = "",
    val propertiesRestored: Int = 0,
    val unitsRestored: Int = 0,
    val receiptsRestored: Int = 0,
    val mainDocsLinked: Int = 0,
    val metadataFilesLoaded: Int = 0,
    val itemsRestored: Int = 0,
    val splitsRestored: Int = 0,
    val proposalsRestored: Int = 0,
    val exportsRestored: Int = 0,
    val datevProfilesRestored: Int = 0,
    val learnedRulesRestored: Int = 0,
    val errorCount: Int = 0,
    val errors: List<String> = emptyList(),
    val isSuccess: Boolean = true
)

enum class AuditStatus {
    GREEN,  // Gültiger Originalbeleg
    YELLOW, // Format gültig, aber MIME-Typ oder Endung stimmt nicht
    RED     // JSON statt Original, leere Datei, nicht erreichbar, beschädigt oder unbekannt
}

data class OriginalReceiptAuditItem(
    val internalId: String,
    val displayId: String,
    val aussteller: String,
    val datum: String,
    val mainDriveFileId: String,
    val storedMimeType: String,
    val storedFilename: String,
    val detectedFormatName: String,
    val detectedMimeType: String,
    val detectedExtension: String,
    val sizeBytes: Long,
    val status: AuditStatus,
    val cause: String
)

data class OriginalReceiptAuditReport(
    val totalChecked: Int = 0,
    val validCount: Int = 0,
    val warningCount: Int = 0,
    val invalidCount: Int = 0,
    val items: List<OriginalReceiptAuditItem> = emptyList(),
    val timestamp: String = ""
)

data class DriveAppConfig(
    val appIdentifier: String = "ImmobilienBelegApp",
    val schemaVersion: Int = 1,
    val rootFolderId: String,
    val systemFolderId: String,
    val receiptsFolderId: String,
    val paymentsFolderId: String,
    val exportsFolderId: String,
    val transactionsFolderId: String,
    val backupsFolderId: String,
    val createdAt: String,
    val updatedAt: String,
    val installationId: String = "",
    val lastSuccessfulRestoreAt: String? = null,
    val lastSuccessfulSyncAt: String? = null,
    val testReferences: DriveTestReferences? = null
)

data class ReceiptTombstone(
    val internalId: String,
    val displayId: String,
    val deletedAt: String,
    val deletedBy: String = "LocalUser",
    val deviceId: String? = null,
    val deletionId: String = java.util.UUID.randomUUID().toString(),
    val previousMainDriveFileId: String = "",
    val previousMetadataFileId: String = "",
    val deletionReason: String? = "Vom Nutzer gelöscht",
    val schemaVersion: Int = 1,
    val status: String = "DELETED" // "DELETED" or "RESTORED"
)

sealed class DeletionResult {
    data class Success(val tombstone: ReceiptTombstone) : DeletionResult()
    data class PendingOffline(val tombstone: ReceiptTombstone, val reason: String) : DeletionResult()
    data class Error(val message: String, val canRetry: Boolean = true) : DeletionResult()
}

sealed class PermanentDeleteResult {
    object Success : PermanentDeleteResult()
    data class Error(val message: String) : PermanentDeleteResult()
}

data class CategoryFolderMapping(
    val id: String,
    val categoryKey: String,
    val year: Int?,
    val driveFolderId: String,
    val active: Boolean = true
)

sealed class DriveInitializationResult {
    data class SuccessCreatedNew(val config: DriveAppConfig) : DriveInitializationResult()
    data class SuccessLoadedExisting(val config: DriveAppConfig, val restoredStammdaten: Boolean) : DriveInitializationResult()
    data class Failure(val error: String) : DriveInitializationResult()
}

class DrivePersistenceRepository(
    private val context: Context,
    private val localRepository: ReceiptRepository
) {
    private val TAG = "DrivePersistenceRepo"

    companion object {
        // Serializes the complete receipt transaction (document, metadata, index and folder).
        // This prevents automatic sync, manual retry and background work from creating in parallel.
        private val receiptSyncGate = ReceiptSyncGate()
        private val receiptIndexMutex = kotlinx.coroutines.sync.Mutex()
        private val metadataMutexMap = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()
        fun getMetadataMutex(internalId: String): kotlinx.coroutines.sync.Mutex {
            return metadataMutexMap.getOrPut(internalId) { kotlinx.coroutines.sync.Mutex() }
        }
    }

    fun getOrCreateInstallationId(): String {
        val prefs = context.getSharedPreferences("app_installation_prefs", Context.MODE_PRIVATE)
        var id = prefs.getString("installation_id", null)
        if (id.isNullOrBlank()) {
            id = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("installation_id", id).apply()
        }
        return id
    }

    fun DriveAppConfig.toJson(): String {
        return JSONObject().apply {
            put("appIdentifier", appIdentifier)
            put("schemaVersion", schemaVersion)
            put("rootFolderId", rootFolderId)
            put("systemFolderId", systemFolderId)
            put("receiptsFolderId", receiptsFolderId)
            put("paymentsFolderId", paymentsFolderId)
            put("exportsFolderId", exportsFolderId)
            put("transactionsFolderId", transactionsFolderId)
            put("backupsFolderId", backupsFolderId)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
            put("installationId", if (installationId.isBlank()) getOrCreateInstallationId() else installationId)
            put("lastSuccessfulRestoreAt", lastSuccessfulRestoreAt ?: JSONObject.NULL)
            put("lastSuccessfulSyncAt", lastSuccessfulSyncAt ?: JSONObject.NULL)
            put("testReferences", testReferences?.let {
                JSONObject().apply {
                    put("internalId", it.internalId)
                    put("displayId", it.displayId)
                    put("mainDriveFileId", it.mainDriveFileId)
                    put("metadataFileId", it.metadataFileId)
                }
            } ?: JSONObject.NULL)
        }.toString(4)
    }

    fun parseDriveAppConfig(jsonStr: String): DriveAppConfig {
        val json = JSONObject(jsonStr)
        val testRefObj = json.optJSONObject("testReferences")
        val testRef = if (testRefObj != null) {
            DriveTestReferences(
                internalId = testRefObj.optString("internalId", ""),
                displayId = testRefObj.optString("displayId", ""),
                mainDriveFileId = testRefObj.optString("mainDriveFileId", ""),
                metadataFileId = testRefObj.optString("metadataFileId", "")
            )
        } else null

        return DriveAppConfig(
            appIdentifier = json.optString("appIdentifier", "ImmobilienBelegApp"),
            schemaVersion = json.optInt("schemaVersion", 1),
            rootFolderId = json.getString("rootFolderId"),
            systemFolderId = json.getString("systemFolderId"),
            receiptsFolderId = json.getString("receiptsFolderId"),
            paymentsFolderId = json.getString("paymentsFolderId"),
            exportsFolderId = json.getString("exportsFolderId"),
            transactionsFolderId = json.getString("transactionsFolderId"),
            backupsFolderId = json.getString("backupsFolderId"),
            createdAt = json.optString("createdAt", ""),
            updatedAt = json.optString("updatedAt", ""),
            installationId = json.optString("installationId", getOrCreateInstallationId()),
            lastSuccessfulRestoreAt = if (json.isNull("lastSuccessfulRestoreAt")) null else json.optString("lastSuccessfulRestoreAt"),
            lastSuccessfulSyncAt = if (json.isNull("lastSuccessfulSyncAt")) null else json.optString("lastSuccessfulSyncAt"),
            testReferences = testRef
        )
    }

    fun PropertyMetadata.toJson(): String {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("adresse", adresse)
            put("wohnort", wohnort)
            put("baujahr", baujahr)
            put("wohnflaeche", wohnflaeche)
            put("grundstuecksgroesse", grundstuecksgroesse)
            put("notariellesKaufdatum", notariellesKaufdatum)
            put("uebergangNutzenLasten", uebergangNutzenLasten)
            put("wohneinheiten", wohneinheiten)
            put("gesamtKaufpreis", gesamtKaufpreis)
            put("gebaeudewert", gebaeudewert)
        }.toString(4)
    }

    fun parsePropertyMetadata(jsonStr: String): PropertyMetadata {
        val json = JSONObject(jsonStr)
        return PropertyMetadata(
            id = json.optInt("id", 1),
            name = json.optString("name", ""),
            adresse = json.optString("adresse", ""),
            wohnort = json.optString("wohnort", ""),
            baujahr = json.optInt("baujahr", 1985),
            wohnflaeche = json.optDouble("wohnflaeche", 0.0),
            grundstuecksgroesse = json.optDouble("grundstuecksgroesse", 0.0),
            notariellesKaufdatum = json.optString("notariellesKaufdatum", ""),
            uebergangNutzenLasten = json.optString("uebergangNutzenLasten", ""),
            wohneinheiten = json.optString("wohneinheiten", ""),
            gesamtKaufpreis = json.optDouble("gesamtKaufpreis", 0.0),
            gebaeudewert = json.optDouble("gebaeudewert", 0.0)
        )
    }

    fun List<WohneinheitStatus>.toJson(): String {
        val array = JSONArray()
        for (u in this) {
            val obj = JSONObject().apply {
                put("name", u.name)
                put("label", u.label)
                put("status", u.status)
                put("mieter", u.mieter)
                put("kaltmiete", u.kaltmiete)
                put("wohnflaeche", u.wohnflaeche)
                put("mietvertragsstart", u.mietvertragsstart)
            }
            array.put(obj)
        }
        return JSONObject().apply {
            put("units", array)
        }.toString(4)
    }

    fun parseWohneinheiten(jsonStr: String): List<WohneinheitStatus> {
        val list = mutableListOf<WohneinheitStatus>()
        val root = JSONObject(jsonStr)
        val array = root.optJSONArray("units") ?: JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                WohneinheitStatus(
                    name = obj.getString("name"),
                    label = obj.optString("label", ""),
                    status = obj.optString("status", "Vermietet"),
                    mieter = obj.optString("mieter", ""),
                    kaltmiete = obj.optDouble("kaltmiete", 0.0),
                    wohnflaeche = obj.optDouble("wohnflaeche", 0.0),
                    mietvertragsstart = obj.optString("mietvertragsstart", "")
                )
            )
        }
        return list
    }

    fun serializeDatevProfile(profile: DatevProfile): String {
        val profileJsonStr = DatevProfileService.exportProfileToJson(profile)
        return JSONObject().apply {
            put("activeProfile", JSONObject(profileJsonStr))
        }.toString(4)
    }

    fun deserializeDatevProfile(jsonStr: String): DatevProfile? {
        try {
            val root = JSONObject(jsonStr)
            val profileObj = root.optJSONObject("activeProfile") ?: return null
            return DatevProfileService.importProfileFromJson(profileObj.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error deserializing DATEV profile", e)
            return null
        }
    }

    fun List<LearnedVendorRule>.toRulesJson(): String {
        val array = JSONArray()
        for (rule in this) {
            val obj = JSONObject().apply {
                put("aussteller", rule.aussteller)
                put("hauptkategorie", rule.hauptkategorie)
                put("unterkategorie", rule.unterkategorie)
                put("kontoNr", rule.kontoNr)
                put("wohneinheit", rule.wohneinheit)
                put("count", rule.count)
            }
            array.put(obj)
        }
        return JSONObject().apply {
            put("rules", array)
        }.toString(4)
    }

    fun parseLearnedRules(jsonStr: String): List<LearnedVendorRule> {
        val list = mutableListOf<LearnedVendorRule>()
        val root = JSONObject(jsonStr)
        val array = root.optJSONArray("rules") ?: JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                LearnedVendorRule(
                    aussteller = obj.getString("aussteller"),
                    hauptkategorie = obj.getString("hauptkategorie"),
                    unterkategorie = obj.getString("unterkategorie"),
                    kontoNr = obj.getString("kontoNr"),
                    wohneinheit = obj.optString("wohneinheit", ""),
                    count = obj.optInt("count", 1)
                )
            )
        }
        return list
    }

    fun getCategoriesJson(): String {
        val mainObj = JSONObject()
        mainObj.put("version", 1)
        val mainArr = JSONArray()
        for (cat in com.example.ui.ReceiptCategories.mainCategories) {
            mainArr.put(cat)
        }
        mainObj.put("mainCategories", mainArr)

        val subMapObj = JSONObject()
        for ((k, v) in com.example.ui.ReceiptCategories.subCategoriesMap) {
            val subArr = JSONArray()
            for (sub in v) {
                subArr.put(sub)
            }
            subMapObj.put(k, subArr)
        }
        mainObj.put("subCategories", subMapObj)
        return mainObj.toString(4)
    }

    fun getWohneinheitenFromPrefs(metadata: PropertyMetadata?): List<WohneinheitStatus> {
        val unitPrefs = context.getSharedPreferences("wohneinheiten_prefs", Context.MODE_PRIVATE)
        val rawUnitsStr = metadata?.wohneinheiten ?: "WE 1, WE 2, WE 3, WE 4, WE 5, WE 6, WE 7"
        val parsedUnitNames = rawUnitsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val unitNames = if (parsedUnitNames.isNotEmpty()) parsedUnitNames else listOf("WE 1", "WE 2", "WE 3", "WE 4", "WE 5", "WE 6", "WE 7")
        return unitNames.map { name ->
            WohneinheitStatus(
                name = name,
                label = unitPrefs.getString("unit_label_$name", name) ?: name,
                status = unitPrefs.getString("unit_status_$name", "Vermietet") ?: "Vermietet",
                mieter = unitPrefs.getString("unit_mieter_$name", "") ?: "",
                kaltmiete = unitPrefs.getFloat("unit_rent_$name", 0f).toDouble(),
                wohnflaeche = unitPrefs.getFloat("unit_area_$name", 0f).toDouble(),
                mietvertragsstart = unitPrefs.getString("unit_start_$name", "") ?: ""
            )
        }
    }

    fun saveWohneinheitenToPrefs(units: List<WohneinheitStatus>) {
        val unitPrefs = context.getSharedPreferences("wohneinheiten_prefs", Context.MODE_PRIVATE)
        val editor = unitPrefs.edit()
        for (u in units) {
            editor.putString("unit_status_${u.name}", u.status)
            editor.putString("unit_label_${u.name}", u.label)
            editor.putString("unit_mieter_${u.name}", u.mieter)
            editor.putFloat("unit_rent_${u.name}", u.kaltmiete.toFloat())
            editor.putFloat("unit_area_${u.name}", u.wohnflaeche.toFloat())
            editor.putString("unit_start_${u.name}", u.mietvertragsstart)
        }
        editor.apply()
    }

    fun getLearnedRulesFromPrefs(): List<LearnedVendorRule> {
        val learnedRulesPrefs = context.getSharedPreferences("ki_learned_rules_prefs", Context.MODE_PRIVATE)
        val rulesList = mutableListOf<LearnedVendorRule>()
        val allPrefs = learnedRulesPrefs.all
        for ((key, value) in allPrefs) {
            if (key.startsWith("rule_") && value is String) {
                val parts = value.split("|||")
                if (parts.size >= 5) {
                    rulesList.add(
                        LearnedVendorRule(
                            aussteller = parts[0],
                            hauptkategorie = parts[1],
                            unterkategorie = parts[2],
                            kontoNr = parts[3],
                            wohneinheit = parts[4],
                            count = parts.getOrNull(5)?.toIntOrNull() ?: 1
                        )
                    )
                }
            }
        }
        return rulesList
    }

    fun saveLearnedRulesToPrefs(rules: List<LearnedVendorRule>) {
        val learnedRulesPrefs = context.getSharedPreferences("ki_learned_rules_prefs", Context.MODE_PRIVATE)
        val editor = learnedRulesPrefs.edit()
        learnedRulesPrefs.all.keys.forEach { k ->
            if (k.startsWith("rule_")) editor.remove(k)
        }
        for (rule in rules) {
            val cleanVendor = rule.aussteller.trim()
            val normalizedKey = "rule_" + cleanVendor.lowercase().replace(Regex("[^a-z0-9]"), "_")
            val serialized = "${rule.aussteller}|||${rule.hauptkategorie}|||${rule.unterkategorie}|||${rule.kontoNr}|||${rule.wohneinheit}|||${rule.count}"
            editor.putString(normalizedKey, serialized)
        }
        editor.apply()
    }

    private suspend fun uploadOrUpdateJson(
        accessToken: String,
        systemFolderId: String,
        entityType: String,
        filename: String,
        json: String
    ): DriveFileResult {
        val file = GoogleDriveClient.findFileByAppProperty(accessToken, systemFolderId, entityType)
        return if (file != null) {
            GoogleDriveClient.updateJson(accessToken, file.id, json)
        } else {
            val appProperties = mapOf(
                "appName" to "ImmobilienBelegApp",
                "entityType" to entityType,
                "schemaVersion" to "1"
            )
      …37556 tokens truncated…fehlgeschlagen: Dateigröße auf Drive (${checkBytes.size}) weicht ab von Quellgröße (${validationResult.sizeBytes}).")
            }

            val downloadedSha256 = calculateSha256Bytes(checkBytes)
            if (downloadedSha256 != validationResult.sha256) {
                return RepairResult(false, errorMessage = "Verifikation fehlgeschlagen: SHA-256 Prüfsumme nach Upload stimmt nicht überein.")
            }

            // Verify Magic Bytes on downloaded content
            val checkHeader = checkBytes.take(16).toByteArray()
            var checkExt = ""
            if (checkHeader.size >= 4 && checkHeader.copyOfRange(0, 4).contentEquals(byteArrayOf(0x25, 0x50, 0x44, 0x46))) {
                checkExt = "pdf"
            } else if (checkHeader.size >= 3 && checkHeader[0] == 0xFF.toByte() && checkHeader[1] == 0xD8.toByte() && checkHeader[2] == 0xFF.toByte()) {
                checkExt = "jpg"
            } else if (checkHeader.size >= 8 && checkHeader.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))) {
                checkExt = "png"
            } else if (checkHeader.size >= 12 && checkHeader.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) && checkHeader.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())) {
                checkExt = "webp"
            }
            if (checkExt.isEmpty() || checkExt != ext.replace("jpeg", "jpg")) {
                return RepairResult(false, errorMessage = "Verifikation fehlgeschlagen: Formatsignatur der heruntergeladenen Datei ist ungültig ($checkExt).")
            }

            // 6. Copy local file to app private storage as permanent local document
            val localDocDir = File(context.filesDir, "receipt_documents")
            if (!localDocDir.exists()) localDocDir.mkdirs()
            val permanentFile = File(localDocDir, "repaired_${receipt.internalId}_${System.currentTimeMillis()}.$ext")
            localFile.copyTo(permanentFile, overwrite = true)

            // 7. Preserve old mainDriveFileId
            val legacyFileId = receipt.driveFileId

            // 8. Atomic receipt update
            val nowStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
            val updatedReceipt = receipt.copy(
                driveFileId = uploadedFileId, // new mainDriveFileId
                driveFolderId = targetFolderId,
                // driveMetadataFileId stays UNCHANGED!
                storedFilename = newFilename,
                originalMimeType = validationResult.mimeType,
                fileSizeBytes = validationResult.sizeBytes,
                imageUrl = permanentFile.absolutePath,
                syncStatus = "SYNCED",
                syncError = null,
                lastSyncedAt = nowStr
            )

            // Save locally
            localRepository.insert(updatedReceipt)

            // Save document reference locally
            val existingDocs = localRepository.getDocumentsForReceipt(updatedReceipt.internalId)
            val docRef = existingDocs.find { it.driveFileId == uploadedFileId } ?: ReceiptDocumentReference(
                id = UUID.randomUUID().toString(),
                receiptInternalId = updatedReceipt.internalId,
                driveFileId = uploadedFileId,
                driveFolderId = targetFolderId,
                filename = newFilename,
                mimeType = validationResult.mimeType,
                sizeBytes = validationResult.sizeBytes,
                role = "MAIN_RECEIPT",
                createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
            )
            localRepository.insertDocument(docRef.copy(filename = newFilename, driveFileId = uploadedFileId, driveFolderId = targetFolderId, mimeType = validationResult.mimeType, sizeBytes = validationResult.sizeBytes))

            // Update in-place in receipt-index.json
            val indexEntry = ReceiptIndexEntry(
                internalId = updatedReceipt.internalId,
                displayId = updatedReceipt.displayId,
                metadataFileId = updatedReceipt.driveMetadataFileId ?: "",
                mainDriveFileId = uploadedFileId,
                aussteller = updatedReceipt.aussteller,
                rechnungsnummer = null,
                datum = updatedReceipt.datum,
                bruttobetragCent = (updatedReceipt.bruttobetrag * 100).toLong(),
                hauptkategorie = updatedReceipt.hauptkategorie,
                unterkategorie = updatedReceipt.unterkategorie,
                wohneinheit = updatedReceipt.wohneinheit,
                massnahme = null,
                pruefstatus = updatedReceipt.pruefstatus,
                freigabestatus = "FREIGEGEBEN",
                exportstatus = updatedReceipt.exportStatus,
                syncStatus = "SYNCED",
                updatedAt = nowStr
            )
            val indexUpdateOk = updateReceiptIndexInDrive(accessToken, config, indexEntry)
            if (!indexUpdateOk) {
                Log.w(TAG, "Index update warning during repair: Index file could not be updated in Drive.")
            }

            // Update metadata.json in Drive
            val metaUpdateOk = uploadReceiptMetadata(accessToken, config, updatedReceipt)
            if (!metaUpdateOk) {
                Log.w(TAG, "Metadata update warning during repair: Metadata file could not be updated in Drive.")
            }

            Log.i(TAG, "Repair and upload completed successfully for ${receipt.internalId}. New DriveFileId: $uploadedFileId")
            return RepairResult(
                success = true,
                updatedReceipt = updatedReceipt,
                newDriveFileId = uploadedFileId,
                legacyInvalidFileId = legacyFileId
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in repairAndUploadOriginalDocument", e)
            return RepairResult(false, errorMessage = "Exception während Reparatur: ${e.message}")
        }
    }

    suspend fun replaceReceiptIndexEntries(
        accessToken: String,
        config: DriveAppConfig,
        entries: List<ReceiptIndexEntry>
    ): Boolean {
        val validation = validateIndex(entries)
        if (!validation.isValid) {
            throw IllegalStateException(
                "Bereinigter receipt-index.json ist ungültig: ${validation.errorMessage}"
            )
        }
        val entriesArray = JSONArray()
        entries.forEach { entry ->
            entriesArray.put(JSONObject().apply {
                put("internalId", entry.internalId)
                put("displayId", entry.displayId ?: JSONObject.NULL)
                put("metadataFileId", entry.metadataFileId)
                put("mainDriveFileId", entry.mainDriveFileId)
                put("aussteller", entry.aussteller ?: JSONObject.NULL)
                put("rechnungsnummer", entry.rechnungsnummer ?: JSONObject.NULL)
                put("datum", entry.datum ?: JSONObject.NULL)
                put("bruttobetragCent", entry.bruttobetragCent ?: JSONObject.NULL)
                put("hauptkategorie", entry.hauptkategorie ?: JSONObject.NULL)
                put("unterkategorie", entry.unterkategorie ?: JSONObject.NULL)
                put("wohneinheit", entry.wohneinheit ?: JSONObject.NULL)
                put("massnahme", entry.massnahme ?: JSONObject.NULL)
                put("pruefstatus", entry.pruefstatus ?: JSONObject.NULL)
                put("freigabestatus", entry.freigabestatus ?: JSONObject.NULL)
                put("exportstatus", entry.exportstatus ?: JSONObject.NULL)
                put("syncStatus", entry.syncStatus)
                put("updatedAt", entry.updatedAt)
            })
        }
        val indexJson = JSONObject().apply {
            put("version", 1)
            put("entries", entriesArray)
        }.toString(4)
        return uploadOrUpdateJson(
            accessToken,
            config.systemFolderId,
            "receiptIndex",
            "receipt-index.json",
            indexJson
        ).success
    }

    private fun calculateSha256Bytes(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    suspend fun getReceiptIndexFromDrive(accessToken: String, config: DriveAppConfig): List<ReceiptIndexEntry> {
        val indexFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "receiptIndex")
            ?: return emptyList()
        return try {
            val jsonStr = GoogleDriveClient.downloadJson(accessToken, indexFile.id)
            val root = JSONObject(jsonStr)
            val array = root.optJSONArray("entries") ?: org.json.JSONArray()
            val entries = mutableListOf<ReceiptIndexEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                entries.add(
                    ReceiptIndexEntry(
                        internalId = obj.optString("internalId", ""),
                        displayId = if (obj.isNull("displayId")) null else obj.optString("displayId"),
                        metadataFileId = obj.optString("metadataFileId", ""),
                        mainDriveFileId = obj.optString("mainDriveFileId", ""),
                        aussteller = obj.optString("aussteller", ""),
                        rechnungsnummer = obj.optString("rechnungsnummer", ""),
                        datum = obj.optString("datum", ""),
                        bruttobetragCent = obj.optLong("bruttobetragCent", 0L),
                        hauptkategorie = obj.optString("hauptkategorie", ""),
                        unterkategorie = obj.optString("unterkategorie", ""),
                        wohneinheit = obj.optString("wohneinheit", ""),
                        massnahme = obj.optString("massnahme", ""),
                        pruefstatus = obj.optString("pruefstatus", ""),
                        freigabestatus = obj.optString("freigabestatus", ""),
                        exportstatus = obj.optString("exportstatus", ""),
                        syncStatus = obj.optString("syncStatus", "SYNCED"),
                        updatedAt = obj.optString("updatedAt", "")
                    )
                )
            }
            entries
        } catch (e: Exception) {
            Log.e(TAG, "Error getting index from Drive", e)
            emptyList()
        }
    }

    suspend fun generateMetadataDuplicateReport(
        accessToken: String,
        config: DriveAppConfig
    ): MetadataDuplicateReport {
        val allFiles = GoogleDriveClient.listAllReceiptMetadataFiles(accessToken, config.receiptsFolderId)
        val indexEntries = getReceiptIndexFromDrive(accessToken, config)
        
        // Keep every index reference. Legacy indexes may contain more than one metadata file for
        // the same internalId; such groups are ambiguous and must remain read-only.
        val indexReferencesByInternalId = indexEntries
            .filter { it.internalId.isNotBlank() && it.metadataFileId.isNotBlank() }
            .groupBy { it.internalId }
            .mapValues { (_, entries) -> entries.map { it.metadataFileId }.toSet() }
        
        // Group all found files by their receiptInternalId
        val groupedFiles = allFiles.groupBy { it.receiptInternalId }
        
        val reportGroups = mutableListOf<MetadataDuplicateGroup>()
        var totalGroupsWithDuplicates = 0
        var totalDuplicateFilesCount = 0
        
        for ((internalId, files) in groupedFiles) {
            if (internalId.isBlank()) continue
            
            val referencedMetadataFileIds =
                indexReferencesByInternalId[internalId].orEmpty()
            val referencedFileIdInIndex = referencedMetadataFileIds.singleOrNull()
            
            val details = files.map { file ->
                MetadataFileDetails(
                    driveId = file.id,
                    name = file.name,
                    createdTime = file.createdTime,
                    modifiedTime = file.modifiedTime,
                    isReferencedInIndex = file.id in referencedMetadataFileIds
                )
            }
            
            // Check if there are duplicates: i.e., more than 1 file for this internalId
            val isDuplicate = files.size > 1
            if (isDuplicate) {
                totalGroupsWithDuplicates++
                totalDuplicateFilesCount += (files.size - 1)
            }
            
            reportGroups.add(
                MetadataDuplicateGroup(
                    internalId = internalId,
                    files = details,
                    referencedFileIdInIndex = referencedFileIdInIndex,
                    referencedMetadataFileIds = referencedMetadataFileIds
                )
            )
        }
        
        return MetadataDuplicateReport(
            totalGroupsWithDuplicates = totalGroupsWithDuplicates,
            totalDuplicateFilesCount = totalDuplicateFilesCount,
            groups = reportGroups
        )
    }
}

data class MetadataFileDetails(
    val driveId: String,
    val name: String,
    val createdTime: String,
    val modifiedTime: String,
    val isReferencedInIndex: Boolean
)

data class MetadataDuplicateGroup(
    val internalId: String,
    val files: List<MetadataFileDetails>,
    val referencedFileIdInIndex: String?,
    val referencedMetadataFileIds: Set<String> =
        referencedFileIdInIndex?.let { setOf(it) } ?: emptySet()
)

data class MetadataDuplicateReport(
    val totalGroupsWithDuplicates: Int,
    val totalDuplicateFilesCount: Int,
    val groups: List<MetadataDuplicateGroup>
)

data class RepairResult(
    val success: Boolean,
    val updatedReceipt: Receipt? = null,
    val newDriveFileId: String? = null,
    val legacyInvalidFileId: String? = null,
    val errorMessage: String? = null
)

// --- Data Classes for PersistedReceipt Structure ---

data class PersistedReceipt(
    val schemaVersion: Int = 1,
    val revision: Long = 1L,
    val internalId: String,
    val displayId: String?,
    val documents: List<ReceiptDocumentReference> = emptyList(),
    val metadataFileId: String = "",
    val driveFileId: String = "",
    val driveFolderId: String? = null,
    val filename: String = "",
    val mimeType: String = "",
    val aussteller: String?,
    val rechnungsnummer: String?,
    val datum: String?,
    val leistungsdatum: String? = null,
    val leistungszeitraumVon: String? = null,
    val leistungszeitraumBis: String? = null,
    val nettobetragCent: Long?,
    val steuerbetragCent: Long?,
    val bruttobetragCent: Long?,
    val waehrung: String = "EUR",
    val hauptkategorie: String?,
    val unterkategorie: String?,
    val wohneinheit: String?,
    val massnahme: String?,
    val positionen: List<PersistedReceiptItem>,
    val allocations: List<PersistedAllocation> = emptyList(),
    val bookingProposals: List<PersistedBookingProposal> = emptyList(),
    val zahlungsstatus: String?,
    val zahlungsdatum: String?,
    val zahlungsreferenz: String? = null,
    val notizSteuerberater: String? = null,
    val pruefstatus: String?,
    val freigabestatus: String? = "OFFEN",
    val exportstatus: String?,
    val exportIds: List<String> = emptyList(),
    val aiAnalysis: PersistedAiAnalysis? = null,
    val analysisRecords: List<AnalysisRecord> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
    val lastSyncedAt: String?
)

data class PersistedAiAnalysis(
    val ocrRawText: String? = null,
    val extractedPdfText: String? = null,
    val modelName: String? = null,
    val analyzedAt: String? = null,
    val fields: Map<String, PersistedAiField> = emptyMap()
)

data class PersistedAiField(
    val rawValue: String? = null,
    val normalizedValue: String? = null,
    val confidence: Double? = null,
    val source: String? = null,
    val confirmed: Boolean = false
)

data class DriveReceiptTestResult(
    val success: Boolean,
    val errorMessage: String? = null,
    val testInternalId: String = "",
    val mainDriveFileId: String = "",
    val metadataFileId: String = "",
    val metadataPath: String = ""
)

data class PersistedReceiptItem(
    val id: String,
    val bezeichnung: String,
    val menge: Double,
    val einzelpreisCent: Long,
    val gesamtpreisCent: Long,
    val hauptkategorie: String,
    val unterkategorie: String,
    val wohneinheit: String,
    val massnahme: String,
    val konto: String,
    val gegenkonto: String = "",
    val buSchluessel: String,
    val steuersatz: Double,
    val privatanteilProzent: Double,
    val zuordnungsstatus: String = "ZUGEORDNET"
)

data class PersistedAllocation(
    val id: String,
    val description: String,
    val percent: Double,
    val amountCent: Long
)

data class PersistedBookingProposal(
    val id: String,
    val konto: String,
    val gegenkonto: String,
    val betragCent: Long,
    val buSchluessel: String
)

data class AnalysisRecord(
    val id: String,
    val receiptInternalId: String,
    val modelName: String?,
    val parserVersion: String?,
    val promptVersion: String?,
    val extractedPdfText: String?,
    val ocrRawText: String?,
    val fields: Map<String, ExtractedFieldRecord>,
    val analyzedAt: String
)

data class ExtractedFieldRecord(
    val rawValue: String?,
    val normalizedValue: String?,
    val confidence: Double?,
    val source: String?,
    val confirmed: Boolean
)

data class ReceiptIndexEntry(
    val internalId: String,
    val displayId: String?,
    val metadataFileId: String,
    val mainDriveFileId: String,
    val aussteller: String?,
    val rechnungsnummer: String?,
    val datum: String?,
    val bruttobetragCent: Long?,
    val hauptkategorie: String?,
    val unterkategorie: String?,
    val wohneinheit: String?,
    val massnahme: String?,
    val pruefstatus: String?,
    val freigabestatus: String?,
    val exportstatus: String?,
    val syncStatus: String,
    val updatedAt: String
)

data class PersistedCategory(
    val id: String = "",
    val key: String = "",
    val name: String = ""
)

data class PersistedPayment(
    val id: String = "",
    val internalId: String = "",
    val datum: String = "",
    val betragCent: Long = 0L,
    val empfaenger: String = ""
)

data class PersistedExportRun(
    val id: String = "",
    val exportDatum: String = "",
    val format: String = "",
    val count: Int = 0
)

data class RestoreWarning(
    val code: String,
    val message: String,
    val targetId: String? = null
)

data class RestoreError(
    val code: String,
    val message: String,
    val targetId: String? = null,
    val isBlocking: Boolean = true
)

data class RestoreSnapshot(
    val schemaVersion: Int = 1,
    val createdAt: String = "",
    val propertyMetadata: PropertyMetadata? = null,
    val units: List<WohneinheitStatus> = emptyList(),
    val categories: List<PersistedCategory> = emptyList(),
    val categoryFolderMappings: List<CategoryFolderMapping> = emptyList(),
    val datevProfiles: List<DatevProfile> = emptyList(),
    val aiLearnedRules: List<LearnedVendorRule> = emptyList(),
    val receipts: List<PersistedReceipt> = emptyList(),
    val payments: List<PersistedPayment> = emptyList(),
    val exportRuns: List<PersistedExportRun> = emptyList(),
    val warnings: List<RestoreWarning> = emptyList(),
    val errors: List<RestoreError> = emptyList()
)

data class RestoreJournal(
    val restoreId: String = java.util.UUID.randomUUID().toString(),
    val startedAt: String = "",
    val mode: String = "REPLACE_FULL",
    val phase: String = "PREPARING",
    val snapshotReceiptCount: Int = 0,
    val importedReceiptCount: Int = 0,
    val completedAt: String? = null,
    val error: String? = null
)

enum class RestoreMode {
    REPLACE_EMPTY,
    MERGE,
    REPLACE_FULL,
    CANCEL
}

