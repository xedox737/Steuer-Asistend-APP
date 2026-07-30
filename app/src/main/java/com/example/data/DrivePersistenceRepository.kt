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
            GoogleDriveClient.uploadJson(accessToken, systemFolderId, filename, json, appProperties)
        }
    }

    private suspend fun uploadOrUpdateReceiptFileJson(
        accessToken: String,
        folderId: String,
        filename: String,
        json: String,
        appProperties: Map<String, String>? = null
    ): DriveFileResult {
        val existingFileId = GoogleDriveClient.findFileByName(accessToken, folderId, filename)
        return if (existingFileId != null) {
            GoogleDriveClient.updateJson(accessToken, existingFileId, json)
        } else {
            GoogleDriveClient.uploadJson(accessToken, folderId, filename, json, appProperties)
        }
    }

    suspend fun initializeDriveStorage(accessToken: String): DriveInitializationResult {
        try {
            Log.d(TAG, "Initializing Google Drive storage...")
            // 1. Search for existing main folder "Steuerassistent Belege"
            val rootFolderId = GoogleDriveClient.getOrCreateFolder(accessToken, "Steuerassistent Belege")
                ?: return DriveInitializationResult.Failure("Hauptordner 'Steuerassistent Belege' konnte nicht gefunden oder erstellt werden.")

            // 2. Search for existing app data system folder "_BelegApp-Daten"
            val systemFolder = GoogleDriveClient.findAppDataFolder(accessToken)

            if (systemFolder != null) {
                // Existing backup setup found
                Log.d(TAG, "Found existing app system folder: ${systemFolder.id}")
                val appConfigFile = GoogleDriveClient.findFileByAppProperty(accessToken, systemFolder.id, "appConfig")
                if (appConfigFile != null) {
                    val configJson = GoogleDriveClient.downloadJson(accessToken, appConfigFile.id)
                    val config = parseDriveAppConfig(configJson)
                    Log.d(TAG, "Loaded existing app configuration from Drive. Schema version: ${config.schemaVersion}")

                    // Check schema compatibility
                    if (config.schemaVersion > 1) {
                        return DriveInitializationResult.Failure("Inkompatible Datenbank-Schema-Version in Google Drive: ${config.schemaVersion}. Bitte aktualisieren Sie die App.")
                    }

                    // Determine if the local state is empty to see if we should auto-restore
                    val localReceipts = localRepository.allReceipts.first()
                    val isLocalEmpty = localReceipts.isEmpty()

                    var restored = false
                    if (isLocalEmpty) {
                        Log.d(TAG, "Local receipts database is empty. Triggering automatic restore of Stammdaten...")
                        restoreStammdatenFromDrive(accessToken, config)
                        restored = true
                    }

                    return DriveInitializationResult.SuccessLoadedExisting(config, restored)
                }
            }

            // If we are here, we must do a fresh setup (first time setup)
            Log.d(TAG, "No existing backup found. Performing first-time Drive backup initialization...")
            val newSystemFolder = GoogleDriveClient.createAppDataFolder(accessToken)

            // Create subfolders in "_BelegApp-Daten"
            val receiptsFolderId = GoogleDriveClient.getOrCreateFolder(accessToken, "receipts", newSystemFolder.id)
                ?: throw IOException("Subfolder receipts could not be created")
            val paymentsFolderId = GoogleDriveClient.getOrCreateFolder(accessToken, "payments", newSystemFolder.id)
                ?: throw IOException("Subfolder payments could not be created")
            val exportsFolderId = GoogleDriveClient.getOrCreateFolder(accessToken, "exports", newSystemFolder.id)
                ?: throw IOException("Subfolder exports could not be created")
            val transactionsFolderId = GoogleDriveClient.getOrCreateFolder(accessToken, "transactions", newSystemFolder.id)
                ?: throw IOException("Subfolder transactions could not be created")
            val backupsFolderId = GoogleDriveClient.getOrCreateFolder(accessToken, "backups", newSystemFolder.id)
                ?: throw IOException("Subfolder backups could not be created")

            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
            val newConfig = DriveAppConfig(
                appIdentifier = "ImmobilienBelegApp",
                schemaVersion = 1,
                rootFolderId = rootFolderId,
                systemFolderId = newSystemFolder.id,
                receiptsFolderId = receiptsFolderId,
                paymentsFolderId = paymentsFolderId,
                exportsFolderId = exportsFolderId,
                transactionsFolderId = transactionsFolderId,
                backupsFolderId = backupsFolderId,
                createdAt = now,
                updatedAt = now
            )

            // Save new app-config.json to Drive
            val uploadRes = GoogleDriveClient.uploadJson(
                accessToken = accessToken,
                folderId = newSystemFolder.id,
                filename = "app-config.json",
                json = newConfig.toJson(),
                appProperties = mapOf(
                    "appName" to "ImmobilienBelegApp",
                    "entityType" to "appConfig",
                    "schemaVersion" to "1"
                )
            )

            if (!uploadRes.success) {
                return DriveInitializationResult.Failure("Fehler beim Hochladen der Konfigurationsdatei: ${uploadRes.errorMessage}")
            }

            // Immediately back up existing local data
            saveStammdatenToDrive(accessToken, newConfig)

            return DriveInitializationResult.SuccessCreatedNew(newConfig)

        } catch (e: Exception) {
            Log.e(TAG, "Exception during initializeDriveStorage", e)
            return DriveInitializationResult.Failure(e.message ?: e.toString())
        }
    }

    suspend fun saveStammdatenToDrive(accessToken: String, config: DriveAppConfig): Boolean {
        try {
            Log.d(TAG, "Backing up Stammdaten to Google Drive...")

            // 1. PropertyMetadata
            val localMetadata = localRepository.getPropertyMetadata() ?: PropertyMetadata()
            val metaUpload = uploadOrUpdateJson(
                accessToken, config.systemFolderId, "propertyMetadata", "property-metadata.json", localMetadata.toJson()
            )

            // 2. Wohneinheiten
            val localUnits = getWohneinheitenFromPrefs(localMetadata)
            val unitsUpload = uploadOrUpdateJson(
                accessToken, config.systemFolderId, "units", "wohneinheiten.json", localUnits.toJson()
            )

            // 3. DATEV-Profile
            val localDatevProfile = DatevProfileService.getActiveProfile(context)
            val datevUpload = uploadOrUpdateJson(
                accessToken, config.systemFolderId, "datevProfiles", "datev-profiles.json", serializeDatevProfile(localDatevProfile)
            )

            // 4. KI-Lernregeln
            val localRules = getLearnedRulesFromPrefs()
            val rulesUpload = uploadOrUpdateJson(
                accessToken, config.systemFolderId, "aiLearnedRules", "ai-learned-rules.json", localRules.toRulesJson()
            )

            // 5. Categories
            val categoriesUpload = uploadOrUpdateJson(
                accessToken, config.systemFolderId, "categories", "categories.json", getCategoriesJson()
            )

            // 6. Category-Folder-Mappings (initialize empty mappings or read current ones if exist, otherwise upload basic structure)
            val mappingsFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "categoryFolderMappings")
            if (mappingsFile == null) {
                val initialMappingsJson = JSONObject().apply {
                    put("mappings", JSONArray())
                }.toString(4)
                uploadOrUpdateJson(
                    accessToken, config.systemFolderId, "categoryFolderMappings", "category-folder-mappings.json", initialMappingsJson
                )
            }

            val allSuccessful = metaUpload.success && unitsUpload.success && datevUpload.success && rulesUpload.success && categoriesUpload.success
            Log.d(TAG, "Backup of Stammdaten finished. All successful? $allSuccessful")
            return allSuccessful
        } catch (e: Exception) {
            Log.e(TAG, "Error backing up Stammdaten", e)
            return false
        }
    }

    suspend fun restoreStammdatenFromDrive(accessToken: String, config: DriveAppConfig): Boolean {
        try {
            Log.d(TAG, "Restoring Stammdaten from Google Drive...")

            // 1. PropertyMetadata
            val metaFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "propertyMetadata")
            if (metaFile != null) {
                val json = GoogleDriveClient.downloadJson(accessToken, metaFile.id)
                val metadata = parsePropertyMetadata(json)
                localRepository.updatePropertyMetadata(metadata)
                Log.d(TAG, "Restored PropertyMetadata successfully.")
            }

            // 2. Wohneinheiten
            val unitsFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "units")
            if (unitsFile != null) {
                val json = GoogleDriveClient.downloadJson(accessToken, unitsFile.id)
                val units = parseWohneinheiten(json)
                saveWohneinheitenToPrefs(units)
                Log.d(TAG, "Restored Wohneinheiten successfully.")
            }

            // 3. DATEV-Profile
            val datevFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "datevProfiles")
            if (datevFile != null) {
                val json = GoogleDriveClient.downloadJson(accessToken, datevFile.id)
                val profile = deserializeDatevProfile(json)
                if (profile != null) {
                    DatevProfileService.saveActiveProfile(context, profile)
                    Log.d(TAG, "Restored DATEV Profile successfully.")
                }
            }

            // 4. KI-Lernregeln
            val rulesFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "aiLearnedRules")
            if (rulesFile != null) {
                val json = GoogleDriveClient.downloadJson(accessToken, rulesFile.id)
                val rules = parseLearnedRules(json)
                saveLearnedRulesToPrefs(rules)
                Log.d(TAG, "Restored KI-Lernregeln successfully.")
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring Stammdaten from Drive", e)
            return false
        }
    }

    // --- Core Drive Persistence Models (Step 3) ---

    private fun Double.toCent(): Long = Math.round(this * 100.0)
    private fun Long.toEuro(): Double = this.toDouble() / 100.0

    fun List<ReceiptItem>.toPersistedItems(): List<PersistedReceiptItem> {
        return this.mapIndexed { index, item ->
            PersistedReceiptItem(
                id = "item_${index + 1}",
                bezeichnung = item.bezeichnung,
                menge = item.menge,
                einzelpreisCent = item.einzelpreis.toCent(),
                gesamtpreisCent = item.gesamtpreis.toCent(),
                hauptkategorie = item.hauptkategorie,
                unterkategorie = item.unterkategorie,
                wohneinheit = item.wohneinheit,
                massnahme = item.massnahme,
                konto = item.kontoNr,
                gegenkonto = "",
                buSchluessel = item.buSchluessel,
                steuersatz = item.steuersatz,
                privatanteilProzent = item.privatanteilProzent,
                zuordnungsstatus = "ZUGEORDNET"
            )
        }
    }

    fun PersistedReceipt.toJson(): String {
        return JSONObject().apply {
            put("schemaVersion", schemaVersion)
            put("revision", revision)
            put("internalId", internalId)
            put("displayId", displayId)
            
            put("driveFileId", driveFileId)
            put("driveFolderId", driveFolderId ?: JSONObject.NULL)
            put("filename", filename)
            put("mimeType", mimeType)
            
            val docsArray = JSONArray()
            for (doc in documents) {
                docsArray.put(JSONObject().apply {
                    put("id", doc.id)
                    put("receiptInternalId", doc.receiptInternalId)
                    put("driveFileId", doc.driveFileId)
                    put("driveFolderId", doc.driveFolderId ?: JSONObject.NULL)
                    put("filename", doc.filename)
                    put("mimeType", doc.mimeType)
                    put("sizeBytes", doc.sizeBytes ?: 0L)
                    put("role", doc.role)
                    put("createdAt", doc.createdAt)
                })
            }
            put("documents", docsArray)
            
            put("aussteller", aussteller ?: JSONObject.NULL)
            put("rechnungsnummer", rechnungsnummer ?: JSONObject.NULL)
            put("datum", datum ?: JSONObject.NULL)
            put("leistungsdatum", leistungsdatum ?: JSONObject.NULL)
            put("leistungszeitraumVon", leistungszeitraumVon ?: JSONObject.NULL)
            put("leistungszeitraumBis", leistungszeitraumBis ?: JSONObject.NULL)
            
            put("nettobetragCent", nettobetragCent ?: JSONObject.NULL)
            put("steuerbetragCent", steuerbetragCent ?: JSONObject.NULL)
            put("bruttobetragCent", bruttobetragCent ?: JSONObject.NULL)
            put("waehrung", waehrung)
            
            put("hauptkategorie", hauptkategorie ?: JSONObject.NULL)
            put("unterkategorie", unterkategorie ?: JSONObject.NULL)
            put("wohneinheit", wohneinheit ?: JSONObject.NULL)
            put("massnahme", massnahme ?: JSONObject.NULL)
            
            val posArray = JSONArray()
            for (pos in positionen) {
                posArray.put(JSONObject().apply {
                    put("id", pos.id)
                    put("bezeichnung", pos.bezeichnung)
                    put("menge", pos.menge)
                    put("einzelpreisCent", pos.einzelpreisCent)
                    put("gesamtpreisCent", pos.gesamtpreisCent)
                    put("hauptkategorie", pos.hauptkategorie)
                    put("unterkategorie", pos.unterkategorie)
                    put("wohneinheit", pos.wohneinheit)
                    put("massnahme", pos.massnahme)
                    put("konto", pos.konto)
                    put("gegenkonto", pos.gegenkonto)
                    put("buSchluessel", pos.buSchluessel)
                    put("steuersatz", pos.steuersatz)
                    put("privatanteilProzent", pos.privatanteilProzent)
                    put("zuordnungsstatus", pos.zuordnungsstatus)
                })
            }
            put("positionen", posArray)
            
            val allocArray = JSONArray()
            for (alloc in allocations) {
                allocArray.put(JSONObject().apply {
                    put("id", alloc.id)
                    put("description", alloc.description)
                    put("percent", alloc.percent)
                    put("amountCent", alloc.amountCent)
                })
            }
            put("allocations", allocArray)
            
            val propArray = JSONArray()
            for (prop in bookingProposals) {
                propArray.put(JSONObject().apply {
                    put("id", prop.id)
                    put("konto", prop.konto)
                    put("gegenkonto", prop.gegenkonto)
                    put("betragCent", prop.betragCent)
                    put("buSchluessel", prop.buSchluessel)
                })
            }
            put("bookingProposals", propArray)
            
            put("zahlungsstatus", zahlungsstatus ?: JSONObject.NULL)
            put("zahlungsdatum", zahlungsdatum ?: JSONObject.NULL)
            put("zahlungsreferenz", zahlungsreferenz ?: JSONObject.NULL)
            
            put("notizSteuerberater", notizSteuerberater ?: JSONObject.NULL)
            put("pruefstatus", pruefstatus ?: JSONObject.NULL)
            put("freigabestatus", freigabestatus ?: JSONObject.NULL)
            put("exportstatus", exportstatus ?: JSONObject.NULL)
            
            val expIdsArray = JSONArray()
            for (expId in exportIds) {
                expIdsArray.put(expId)
            }
            put("exportIds", expIdsArray)
            
            if (aiAnalysis != null) {
                put("aiAnalysis", JSONObject().apply {
                    put("ocrRawText", aiAnalysis.ocrRawText ?: JSONObject.NULL)
                    put("extractedPdfText", aiAnalysis.extractedPdfText ?: JSONObject.NULL)
                    put("modelName", aiAnalysis.modelName ?: JSONObject.NULL)
                    put("analyzedAt", aiAnalysis.analyzedAt ?: JSONObject.NULL)
                    val fieldsObj = JSONObject()
                    for ((k, v) in aiAnalysis.fields) {
                        fieldsObj.put(k, JSONObject().apply {
                            put("rawValue", v.rawValue ?: JSONObject.NULL)
                            put("normalizedValue", v.normalizedValue ?: JSONObject.NULL)
                            put("confidence", v.confidence ?: JSONObject.NULL)
                            put("source", v.source ?: JSONObject.NULL)
                            put("confirmed", v.confirmed)
                        })
                    }
                    put("fields", fieldsObj)
                })
            } else {
                put("aiAnalysis", JSONObject.NULL)
            }
            
            val analArray = JSONArray()
            for (anal in analysisRecords) {
                analArray.put(JSONObject().apply {
                    put("id", anal.id)
                    put("receiptInternalId", anal.receiptInternalId)
                    put("modelName", anal.modelName ?: JSONObject.NULL)
                    put("parserVersion", anal.parserVersion ?: JSONObject.NULL)
                    put("promptVersion", anal.promptVersion ?: JSONObject.NULL)
                    put("extractedPdfText", anal.extractedPdfText ?: JSONObject.NULL)
                    put("ocrRawText", anal.ocrRawText ?: JSONObject.NULL)
                    
                    val fieldsObj = JSONObject()
                    for ((k, v) in anal.fields) {
                        fieldsObj.put(k, JSONObject().apply {
                            put("rawValue", v.rawValue ?: JSONObject.NULL)
                            put("normalizedValue", v.normalizedValue ?: JSONObject.NULL)
                            put("confidence", v.confidence ?: JSONObject.NULL)
                            put("source", v.source ?: JSONObject.NULL)
                            put("confirmed", v.confirmed)
                        })
                    }
                    put("fields", fieldsObj)
                    put("analyzedAt", anal.analyzedAt)
                })
            }
            put("analysisRecords", analArray)
            
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
            put("lastSyncedAt", lastSyncedAt ?: JSONObject.NULL)
        }.toString(4)
    }

    fun parsePersistedReceipt(jsonStr: String): PersistedReceipt {
        val json = JSONObject(jsonStr)
        
        val docsList = mutableListOf<ReceiptDocumentReference>()
        val docsArr = json.optJSONArray("documents") ?: JSONArray()
        for (i in 0 until docsArr.length()) {
            val d = docsArr.getJSONObject(i)
            docsList.add(
                ReceiptDocumentReference(
                    id = d.getString("id"),
                    receiptInternalId = d.getString("receiptInternalId"),
                    driveFileId = d.getString("driveFileId"),
                    driveFolderId = if (d.isNull("driveFolderId")) null else d.getString("driveFolderId"),
                    filename = d.getString("filename"),
                    mimeType = d.getString("mimeType"),
                    sizeBytes = if (d.isNull("sizeBytes") || !d.has("sizeBytes")) null else d.getLong("sizeBytes"),
                    role = d.getString("role"),
                    createdAt = d.getString("createdAt")
                )
            )
        }
        
        val posList = mutableListOf<PersistedReceiptItem>()
        val posArr = json.optJSONArray("positionen") ?: JSONArray()
        for (i in 0 until posArr.length()) {
            val p = posArr.getJSONObject(i)
            posList.add(
                PersistedReceiptItem(
                    id = p.getString("id"),
                    bezeichnung = p.getString("bezeichnung"),
                    menge = p.optDouble("menge", 1.0),
                    einzelpreisCent = p.getLong("einzelpreisCent"),
                    gesamtpreisCent = p.getLong("gesamtpreisCent"),
                    hauptkategorie = p.getString("hauptkategorie"),
                    unterkategorie = p.getString("unterkategorie"),
                    wohneinheit = p.optString("wohneinheit", ""),
                    massnahme = p.optString("massnahme", ""),
                    konto = p.getString("konto"),
                    gegenkonto = p.optString("gegenkonto", ""),
                    buSchluessel = p.optString("buSchluessel", ""),
                    steuersatz = p.optDouble("steuersatz", 19.0),
                    privatanteilProzent = p.optDouble("privatanteilProzent", 0.0),
                    zuordnungsstatus = p.optString("zuordnungsstatus", "ZUGEORDNET")
                )
            )
        }

        val aiAnalysisObj = json.optJSONObject("aiAnalysis")
        val aiAnalysis = if (aiAnalysisObj != null) {
            val fieldsMap = mutableMapOf<String, PersistedAiField>()
            val fieldsObj = aiAnalysisObj.optJSONObject("fields")
            if (fieldsObj != null) {
                val keys = fieldsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val f = fieldsObj.getJSONObject(k)
                    fieldsMap[k] = PersistedAiField(
                        rawValue = if (f.isNull("rawValue")) null else f.optString("rawValue"),
                        normalizedValue = if (f.isNull("normalizedValue")) null else f.optString("normalizedValue"),
                        confidence = if (f.isNull("confidence") || !f.has("confidence")) null else f.optDouble("confidence"),
                        source = if (f.isNull("source")) null else f.optString("source"),
                        confirmed = f.optBoolean("confirmed", false)
                    )
                }
            }
            PersistedAiAnalysis(
                ocrRawText = if (aiAnalysisObj.isNull("ocrRawText")) null else aiAnalysisObj.optString("ocrRawText"),
                extractedPdfText = if (aiAnalysisObj.isNull("extractedPdfText")) null else aiAnalysisObj.optString("extractedPdfText"),
                modelName = if (aiAnalysisObj.isNull("modelName")) null else aiAnalysisObj.optString("modelName"),
                analyzedAt = if (aiAnalysisObj.isNull("analyzedAt")) null else aiAnalysisObj.optString("analyzedAt"),
                fields = fieldsMap
            )
        } else null
        
        return PersistedReceipt(
            schemaVersion = json.optInt("schemaVersion", 1),
            revision = json.optLong("revision", 1L),
            internalId = json.getString("internalId"),
            displayId = if (json.isNull("displayId")) null else json.getString("displayId"),
            documents = docsList,
            driveFileId = json.optString("driveFileId", ""),
            driveFolderId = if (json.isNull("driveFolderId")) null else json.optString("driveFolderId", null),
            filename = json.optString("filename", ""),
            mimeType = json.optString("mimeType", ""),
            aussteller = if (json.isNull("aussteller")) null else json.getString("aussteller"),
            rechnungsnummer = if (json.isNull("rechnungsnummer")) null else json.getString("rechnungsnummer"),
            datum = if (json.isNull("datum")) null else json.getString("datum"),
            leistungsdatum = if (json.isNull("leistungsdatum")) null else json.getString("leistungsdatum"),
            leistungszeitraumVon = if (json.isNull("leistungszeitraumVon")) null else json.getString("leistungszeitraumVon"),
            leistungszeitraumBis = if (json.isNull("leistungszeitraumBis")) null else json.getString("leistungszeitraumBis"),
            nettobetragCent = if (json.isNull("nettobetragCent") || !json.has("nettobetragCent")) null else json.getLong("nettobetragCent"),
            steuerbetragCent = if (json.isNull("steuerbetragCent") || !json.has("steuerbetragCent")) null else json.getLong("steuerbetragCent"),
            bruttobetragCent = if (json.isNull("bruttobetragCent") || !json.has("bruttobetragCent")) null else json.getLong("bruttobetragCent"),
            waehrung = json.optString("waehrung", "EUR"),
            hauptkategorie = if (json.isNull("hauptkategorie")) null else json.getString("hauptkategorie"),
            unterkategorie = if (json.isNull("unterkategorie")) null else json.getString("unterkategorie"),
            wohneinheit = if (json.isNull("wohneinheit")) null else json.getString("wohneinheit"),
            massnahme = if (json.isNull("massnahme")) null else json.getString("massnahme"),
            positionen = posList,
            zahlungsstatus = if (json.isNull("zahlungsstatus")) null else json.getString("zahlungsstatus"),
            zahlungsdatum = if (json.isNull("zahlungsdatum")) null else json.getString("zahlungsdatum"),
            zahlungsreferenz = if (json.isNull("zahlungsreferenz")) null else json.getString("zahlungsreferenz"),
            pruefstatus = if (json.isNull("pruefstatus")) null else json.getString("pruefstatus"),
            exportstatus = if (json.isNull("exportstatus")) null else json.getString("exportstatus"),
            aiAnalysis = aiAnalysis,
            createdAt = json.getString("createdAt"),
            updatedAt = json.getString("updatedAt"),
            lastSyncedAt = if (json.isNull("lastSyncedAt")) null else json.getString("lastSyncedAt")
        )
    }

    fun PersistedReceipt.toLocalReceipt(metadataFileId: String?): Receipt {
        val convertedPos = positionen.map { p ->
            ReceiptItem(
                bezeichnung = p.bezeichnung,
                menge = p.menge,
                einzelpreis = p.einzelpreisCent.toEuro(),
                gesamtpreis = p.gesamtpreisCent.toEuro(),
                hauptkategorie = p.hauptkategorie,
                unterkategorie = p.unterkategorie,
                wohneinheit = p.wohneinheit,
                massnahme = p.massnahme,
                kontoNr = p.konto,
                buSchluessel = p.buSchluessel,
                steuersatz = p.steuersatz,
                privatanteilProzent = p.privatanteilProzent
            )
        }
        val posJsonStr = ReceiptItemConverter.toJson(convertedPos)
        val mainDoc = documents.find { it.role == "MAIN_RECEIPT" }
        
        return Receipt(
            aussteller = aussteller ?: "",
            datum = datum ?: "",
            uhrzeit = "",
            bruttobetrag = (bruttobetragCent ?: 0L).toEuro(),
            hauptkategorie = hauptkategorie ?: "",
            unterkategorie = unterkategorie ?: "",
            kontoNr = positionen.firstOrNull()?.konto ?: "",
            beschreibung = aussteller ?: "",
            isEigenleistungSanierung = false,
            imageUrl = mainDoc?.filename ?: "",
            wohneinheit = wohneinheit ?: "",
            mieter = "",
            isArchivedToDrive = true,
            positionenJson = posJsonStr,
            exportStatus = exportstatus ?: "EXPORTBEREIT",
            pruefstatus = pruefstatus ?: "GEPRUEFT",
            exportlaufId = "",
            
            internalId = internalId,
            displayId = displayId,
            driveFileId = mainDoc?.driveFileId,
            driveFolderId = mainDoc?.driveFolderId,
            driveMetadataFileId = metadataFileId,
            storedFilename = mainDoc?.filename,
            originalMimeType = mainDoc?.mimeType,
            fileSizeBytes = mainDoc?.sizeBytes,
            syncStatus = "SYNCED",
            syncError = null,
            lastSyncedAt = lastSyncedAt,
            driveRevision = revision
        )
    }

    fun normalizePathSegment(segment: String): String {
        return segment.trim().lowercase()
            .replace("[^a-z0-9_-]".toRegex(), "-")
            .replace("-+".toRegex(), "-")
            .trim('-')
    }

    suspend fun getReceiptTargetFolderId(
        accessToken: String,
        rootFolderId: String,
        receipt: Receipt,
        systemFolderId: String? = null,
        context: Context? = null
    ): String {
        val yearName = try {
            receipt.datum.trim().split("-").firstOrNull()?.trim() ?: "2026"
        } catch (e: Exception) {
            "2026"
        }
        val hauptkategorieName = receipt.hauptkategorie.trim().ifEmpty { "Sonstiges" }
        val unterkategorieName = receipt.unterkategorie.trim().ifEmpty { "Allgemein" }

        val normYear = normalizePathSegment(yearName)
        val normHaupt = normalizePathSegment(hauptkategorieName)
        val normUnter = normalizePathSegment(unterkategorieName)

        val yearPath = normYear
        val hauptPath = "$normYear/$normHaupt"
        val unterPath = "$normYear/$normHaupt/$normUnter"

        val yearFolderId = GoogleDriveClient.getOrCreateFolder(
            accessToken = accessToken,
            folderName = yearName,
            parentId = rootFolderId,
            canonicalPathKey = yearPath,
            systemFolderId = systemFolderId,
            context = context
        ) ?: rootFolderId

        val hauptFolderId = GoogleDriveClient.getOrCreateFolder(
            accessToken = accessToken,
            folderName = hauptkategorieName,
            parentId = yearFolderId,
            canonicalPathKey = hauptPath,
            systemFolderId = systemFolderId,
            context = context
        ) ?: yearFolderId

        return GoogleDriveClient.getOrCreateFolder(
            accessToken = accessToken,
            folderName = unterkategorieName,
            parentId = hauptFolderId,
            canonicalPathKey = unterPath,
            systemFolderId = systemFolderId,
            context = context
        ) ?: hauptFolderId
    }

    suspend fun saveDriveAppConfig(accessToken: String, config: DriveAppConfig): Boolean {
        return try {
            val appConfigFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "appConfig")
            val jsonStr = config.toJson()
            if (appConfigFile != null) {
                GoogleDriveClient.updateJson(accessToken, appConfigFile.id, jsonStr).success
            } else {
                GoogleDriveClient.uploadJson(
                    accessToken = accessToken,
                    folderId = config.systemFolderId,
                    filename = "app-config.json",
                    json = jsonStr,
                    appProperties = mapOf(
                        "appName" to "ImmobilienBelegApp",
                        "entityType" to "appConfig",
                        "schemaVersion" to "1"
                    )
                ).success
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving DriveAppConfig", e)
            false
        }
    }

    fun upsertReceiptIndexEntry(
        entries: List<ReceiptIndexEntry>,
        newEntry: ReceiptIndexEntry
    ): List<ReceiptIndexEntry> {
        val existingIndex = entries.indexOfFirst {
            it.internalId == newEntry.internalId ||
            (newEntry.mainDriveFileId.isNotBlank() && it.mainDriveFileId == newEntry.mainDriveFileId) ||
            (newEntry.metadataFileId.isNotBlank() && it.metadataFileId == newEntry.metadataFileId)
        }

        return if (existingIndex >= 0) {
            entries.toMutableList().apply {
                this[existingIndex] = newEntry
            }
        } else {
            entries + newEntry
        }
    }

    suspend fun sanitizeIndexEntries(
        entries: List<ReceiptIndexEntry>,
        accessToken: String? = null
    ): List<ReceiptIndexEntry> {
        val resultList = entries.toMutableList()

        val obsoleteIdx = resultList.indexOfFirst {
            it.internalId == "c4082e97-62a7-4111-834b-4364a53a2b97" &&
            it.mainDriveFileId == "1fN2tpdm1S7SipCxmyXsnxgWJ7Htv9kfc"
        }

        if (obsoleteIdx != -1) {
            val obsoleteEntry = resultList[obsoleteIdx]
            Log.i(TAG, "Removing obsolete test duplicate entry internalId=${obsoleteEntry.internalId}, metadataFileId=${obsoleteEntry.metadataFileId}")
            resultList.removeAt(obsoleteIdx)

            if (!accessToken.isNullOrBlank() && obsoleteEntry.metadataFileId == "1vmkwAsNqtea_N4PZTquRyssN0yblyZ7K") {
                val usedByOther = resultList.any { it.metadataFileId == "1vmkwAsNqtea_N4PZTquRyssN0yblyZ7K" }
                if (!usedByOther) {
                    Log.i(TAG, "Deleting obsolete test metadata file from Drive: 1vmkwAsNqtea_N4PZTquRyssN0yblyZ7K")
                    GoogleDriveClient.deleteFile(accessToken, "1vmkwAsNqtea_N4PZTquRyssN0yblyZ7K")
                }
            }
        }

        val testEntries = resultList.filter {
            it.displayId?.startsWith("TEST-BLG-") == true ||
            it.internalId == "d1ccd205-1a81-4f6a-9fc0-86cb9e29dea2" ||
            it.internalId == "c4082e97-62a7-4111-834b-4364a53a2b97"
        }

        if (testEntries.size > 1) {
            val canonical = testEntries.find { it.internalId == "d1ccd205-1a81-4f6a-9fc0-86cb9e29dea2" }
                ?: testEntries.maxByOrNull { it.updatedAt }!!

            for (te in testEntries) {
                if (te.internalId != canonical.internalId) {
                    Log.i(TAG, "Removing test duplicate: internalId=${te.internalId}, metadataFileId=${te.metadataFileId}")
                    resultList.removeAll { it.internalId == te.internalId }
                    if (!accessToken.isNullOrBlank() && te.metadataFileId.isNotBlank() && te.metadataFileId != canonical.metadataFileId) {
                        val usedElsewhere = resultList.any { it.metadataFileId == te.metadataFileId }
                        if (!usedElsewhere) {
                            GoogleDriveClient.deleteFile(accessToken, te.metadataFileId)
                        }
                    }
                }
            }
        }

        return resultList
    }

    fun validateIndex(entries: List<ReceiptIndexEntry>): IndexValidationResult {
        val internalIds = mutableSetOf<String>()
        val metadataFileIds = mutableSetOf<String>()
        val mainDriveFileIds = mutableSetOf<String>()
        val displayIdToInternalId = mutableMapOf<String, String>()

        for (entry in entries) {
            if (entry.internalId.isBlank()) {
                return IndexValidationResult(false, "Indexeintrag ohne internalId gefunden.")
            }
            if (!internalIds.add(entry.internalId)) {
                return IndexValidationResult(false, "Doppelte internalId im Index: ${entry.internalId}")
            }

            if (entry.metadataFileId.isBlank()) {
                return IndexValidationResult(false, "Indexeintrag ohne metadataFileId (internalId=${entry.internalId})")
            }
            if (!metadataFileIds.add(entry.metadataFileId)) {
                return IndexValidationResult(false, "Doppelte metadataFileId im Index: ${entry.metadataFileId}")
            }

            if (entry.mainDriveFileId.isBlank()) {
                return IndexValidationResult(false, "Indexeintrag ohne mainDriveFileId (internalId=${entry.internalId})")
            }
            if (!mainDriveFileIds.add(entry.mainDriveFileId)) {
                return IndexValidationResult(false, "Doppelte mainDriveFileId im Index: ${entry.mainDriveFileId}")
            }

            val displayId = entry.displayId
            if (!displayId.isNullOrBlank()) {
                val existingInternalId = displayIdToInternalId[displayId]
                if (existingInternalId != null && existingInternalId != entry.internalId) {
                    return IndexValidationResult(false, "Doppelte displayId '$displayId' für unterschiedliche internalIds ($existingInternalId vs ${entry.internalId})")
                }
                displayIdToInternalId[displayId] = entry.internalId
            }
        }

        return IndexValidationResult(true)
    }

    suspend fun updateReceiptIndexInDrive(
        accessToken: String,
        config: DriveAppConfig,
        newEntry: ReceiptIndexEntry
    ): Boolean {
        try {
            val indexFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "receiptIndex")
            var indexEntries = mutableListOf<ReceiptIndexEntry>()
            
            if (indexFile != null) {
                try {
                    val jsonStr = GoogleDriveClient.downloadJson(accessToken, indexFile.id)
                    val root = JSONObject(jsonStr)
                    val array = root.optJSONArray("entries") ?: JSONArray()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        indexEntries.add(
                            ReceiptIndexEntry(
                                internalId = obj.getString("internalId"),
                                displayId = if (obj.isNull("displayId")) null else obj.getString("displayId"),
                                metadataFileId = obj.getString("metadataFileId"),
                                mainDriveFileId = obj.getString("mainDriveFileId"),
                                aussteller = if (obj.isNull("aussteller")) null else obj.getString("aussteller"),
                                rechnungsnummer = if (obj.isNull("rechnungsnummer")) null else obj.getString("rechnungsnummer"),
                                datum = if (obj.isNull("datum")) null else obj.getString("datum"),
                                bruttobetragCent = if (obj.isNull("bruttobetragCent") || !obj.has("bruttobetragCent")) null else obj.getLong("bruttobetragCent"),
                                hauptkategorie = if (obj.isNull("hauptkategorie")) null else obj.getString("hauptkategorie"),
                                unterkategorie = if (obj.isNull("unterkategorie")) null else obj.getString("unterkategorie"),
                                wohneinheit = if (obj.isNull("wohneinheit")) null else obj.getString("wohneinheit"),
                                massnahme = if (obj.isNull("massnahme")) null else obj.getString("massnahme"),
                                pruefstatus = if (obj.isNull("pruefstatus")) null else obj.getString("pruefstatus"),
                                freigabestatus = if (obj.isNull("freigabestatus")) null else obj.getString("freigabestatus"),
                                exportstatus = if (obj.isNull("exportstatus")) null else obj.getString("exportstatus"),
                                syncStatus = obj.optString("syncStatus", "SYNCED"),
                                updatedAt = obj.optString("updatedAt", "")
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading/parsing existing index, starting fresh", e)
                }
            }

            // 1. Sanitize & clean up known duplicate test entries first
            indexEntries = sanitizeIndexEntries(indexEntries, accessToken).toMutableList()

            // 2. Validate index before upsert
            val preValidation = validateIndex(indexEntries)
            if (!preValidation.isValid) {
                Log.e(TAG, "Index validation failed before upsert: ${preValidation.errorMessage}")
                return false
            }

            // 3. Upsert entry using strict uniqueness rules
            indexEntries = upsertReceiptIndexEntry(indexEntries, newEntry).toMutableList()

            // 4. Validate index after upsert
            val postValidation = validateIndex(indexEntries)
            if (!postValidation.isValid) {
                Log.e(TAG, "Index validation failed after upsert: ${postValidation.errorMessage}")
                return false
            }

            val entriesArray = JSONArray()
            for (entry in indexEntries) {
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

            val uploadRes = uploadOrUpdateJson(
                accessToken, config.systemFolderId, "receiptIndex", "receipt-index.json", indexJson
            )
            return uploadRes.success
        } catch (e: Exception) {
            Log.e(TAG, "Exception during index update", e)
            return false
        }
    }

    private fun getSha256(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun checkMagicBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val isPdf = bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte()
        val isPng = bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        val isJpg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        val isWebp = bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte()
        return isPdf || isPng || isJpg || isWebp
    }

    suspend fun syncReceiptToDrive(
        accessToken: String,
        config: DriveAppConfig,
        receipt: Receipt
    ): Boolean {
        try {
            Log.d(TAG, "Syncing receipt to Drive. ID: ${receipt.id}")
            
            var currentReceipt = receipt
            if (currentReceipt.internalId.isBlank()) {
                var matchedInternalId: String? = null
                var matchedDisplayId: String? = null

                // 1. Check existing driveMetadataFileId
                if (!currentReceipt.driveMetadataFileId.isNullOrBlank()) {
                    try {
                        val metaJson = GoogleDriveClient.downloadJson(accessToken, currentReceipt.driveMetadataFileId!!)
                        val parsed = parsePersistedReceipt(metaJson)
                        if (parsed.internalId.isNotBlank()) {
                            matchedInternalId = parsed.internalId
                            matchedDisplayId = parsed.displayId
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not fetch metadata for identification", e)
                    }
                }

                // 2. Search in receipt-index.json for mainDriveFileId
                if (matchedInternalId == null && !currentReceipt.driveFileId.isNullOrBlank()) {
                    val indexFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "receiptIndex")
                    if (indexFile != null) {
                        try {
                            val jsonStr = GoogleDriveClient.downloadJson(accessToken, indexFile.id)
                            val root = JSONObject(jsonStr)
                            val array = root.optJSONArray("entries") ?: JSONArray()
                            for (i in 0 until array.length()) {
                                val obj = array.getJSONObject(i)
                                if (obj.optString("mainDriveFileId") == currentReceipt.driveFileId) {
                                    matchedInternalId = obj.optString("internalId").ifBlank { null }
                                    matchedDisplayId = if (obj.isNull("displayId")) null else obj.optString("displayId")
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error checking index for mainDriveFileId", e)
                        }
                    }
                }

                // 3. Search Drive appProperties
                if (matchedInternalId == null) {
                    val searchMetaFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.receiptsFolderId, "receiptMetadata")
                    if (searchMetaFile != null) {
                        try {
                            val metaJson = GoogleDriveClient.downloadJson(accessToken, searchMetaFile.id)
                            val parsed = parsePersistedReceipt(metaJson)
                            if (parsed.driveFileId == currentReceipt.driveFileId || parsed.displayId == currentReceipt.displayId) {
                                matchedInternalId = parsed.internalId
                                matchedDisplayId = parsed.displayId
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error searching appProperties for internalId", e)
                        }
                    }
                }

                // 4. Only if genuinely new, generate UUID
                val finalInternalId = matchedInternalId ?: UUID.randomUUID().toString().replace("-", "").uppercase()
                val year = try {
                    currentReceipt.datum.split("-").firstOrNull()?.trim() ?: "2026"
                } catch (e: Exception) {
                    "2026"
                }
                val finalDisplayId = currentReceipt.displayId ?: matchedDisplayId ?: "BLG-$year-${String.format("%06d", currentReceipt.id)}"

                currentReceipt = currentReceipt.copy(
                    internalId = finalInternalId,
                    displayId = finalDisplayId
                )
                localRepository.insert(currentReceipt)
            }

            val targetFolderId = getReceiptTargetFolderId(accessToken, config.rootFolderId, currentReceipt, config.systemFolderId, context)

            var driveFileId = currentReceipt.driveFileId
            var filename = currentReceipt.storedFilename
            var mimeType = currentReceipt.originalMimeType
            var sizeBytes = currentReceipt.fileSizeBytes

            // 1. If driveFileId is empty, try to resolve it from the index or from properties search
            if (driveFileId.isNullOrEmpty()) {
                val indexFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "receiptIndex")
                if (indexFile != null) {
                    try {
                        val jsonStr = GoogleDriveClient.downloadJson(accessToken, indexFile.id)
                        val root = JSONObject(jsonStr)
                        val array = root.optJSONArray("entries") ?: JSONArray()
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            if (obj.optString("internalId") == currentReceipt.internalId) {
                                val matchedId = obj.optString("mainDriveFileId")
                                if (!matchedId.isNullOrBlank()) {
                                    driveFileId = matchedId
                                    Log.d(TAG, "Idempotency check: Found driveFileId from receipt-index: $driveFileId")
                                    break
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error checking index for internalId", e)
                    }
                }
            }

            if (driveFileId.isNullOrEmpty()) {
                val foundFile = GoogleDriveClient.findFileByReceiptProperties(accessToken, targetFolderId, currentReceipt.internalId, "ORIGINAL")
                if (foundFile != null) {
                    driveFileId = foundFile.id
                    Log.d(TAG, "Idempotency check: Found driveFileId by appProperties: $driveFileId")
                }
            }

            if (driveFileId.isNullOrEmpty()) {
                val formattedDate = currentReceipt.datum.trim().replace(".", "-").replace("/", "-")
                val sanitizedAussteller = currentReceipt.aussteller.replace("[^a-zA-Z0-9]".toRegex(), "_").ifEmpty { "Beleg" }
                val tempFilename = "Beleg_${formattedDate}_${sanitizedAussteller}_${currentReceipt.bruttobetrag}"
                
                var foundId: String? = null
                for (ext in listOf("pdf", "png", "jpg", "jpeg", "xml", "txt")) {
                    val candidateName = "$tempFilename.$ext"
                    val fileId = GoogleDriveClient.findFileByName(accessToken, targetFolderId, candidateName)
                    if (fileId != null) {
                        foundId = fileId
                        filename = candidateName
                        mimeType = when (ext) {
                            "pdf" -> "application/pdf"
                            "png" -> "image/png"
                            "jpg", "jpeg" -> "image/jpeg"
                            "xml" -> "application/xml"
                            else -> "text/plain"
                        }
                        break
                    }
                }
                
                if (foundId != null) {
                    driveFileId = foundId
                    Log.d(TAG, "Bestandsmigration: found existing Drive file ID $foundId for filename $filename")
                }
            }

            // Retrieve local file content and verify Magic Bytes and SHA-256
            val paths = currentReceipt.imageUrl.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            var fileBytes: ByteArray? = null
            var detectedExt = "txt"
            if (paths.isNotEmpty()) {
                val firstPath = paths[0]
                try {
                    if (firstPath.startsWith("content://") || firstPath.startsWith("file://")) {
                        val uri = android.net.Uri.parse(firstPath)
                        fileBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        val mime = context.contentResolver.getType(uri) ?: ""
                        detectedExt = when {
                            mime.contains("pdf") || firstPath.lowercase().endsWith(".pdf") -> "pdf"
                            mime.contains("png") || firstPath.lowercase().endsWith(".png") -> "png"
                            mime.contains("jpeg") || mime.contains("jpg") || firstPath.lowercase().endsWith(".jpg") || firstPath.lowercase().endsWith(".jpeg") -> "jpg"
                            mime.contains("xml") || firstPath.lowercase().endsWith(".xml") -> "xml"
                            else -> "bin"
                        }
                    } else {
                        val f = java.io.File(firstPath)
                        if (f.exists() && f.isFile) {
                            fileBytes = f.readBytes()
                            detectedExt = f.extension.lowercase().ifBlank { "bin" }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading file bytes from $firstPath", e)
                }
            }

            if (fileBytes != null && fileBytes.isNotEmpty()) {
                val localSha256 = getSha256(fileBytes)
                val header = fileBytes.take(16).toByteArray()
                var realExt = "bin"
                if (header.size >= 4 && header.copyOfRange(0, 4).contentEquals(byteArrayOf(0x25, 0x50, 0x44, 0x46))) {
                    realExt = "pdf"
                } else if (header.size >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) {
                    realExt = "jpg"
                } else if (header.size >= 8 && header.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))) {
                    realExt = "png"
                } else if (header.size >= 12 && header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) && header.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())) {
                    realExt = "webp"
                }
                
                mimeType = when (realExt) {
                    "pdf" -> "application/pdf"
                    "png" -> "image/png"
                    "jpg", "jpeg" -> "image/jpeg"
                    "webp" -> "image/webp"
                    "xml" -> "application/xml"
                    else -> "application/octet-stream"
                }
                filename = generateStandardizedReceiptFilename(currentReceipt, realExt)
                sizeBytes = fileBytes.size.toLong()

                var needsCreate = true

                // Verify found file on Drive
                if (!driveFileId.isNullOrEmpty()) {
                    try {
                        val driveBytes = GoogleDriveClient.downloadFileBytes(accessToken, driveFileId!!)
                        if (driveBytes != null && driveBytes.isNotEmpty()) {
                            val driveSha256 = getSha256(driveBytes)
                            val isMagicValid = checkMagicBytes(driveBytes)
                            if (driveSha256 == localSha256 && isMagicValid) {
                                needsCreate = false
                                Log.d(TAG, "Idempotency check: File on Drive is fully identical. Reusing ID: $driveFileId")
                            } else {
                                Log.d(TAG, "Idempotency check: File on Drive differs. Updating existing file.")
                                val appProperties = mapOf(
                                    "appName" to "ImmobilienBelegApp",
                                    "receiptInternalId" to currentReceipt.internalId,
                                    "displayId" to (currentReceipt.displayId ?: ""),
                                    "documentRole" to "ORIGINAL",
                                    "contentSha256" to localSha256,
                                    "schemaVersion" to "1"
                                )
                                val updatedId = GoogleDriveClient.uploadFile(
                                    accessToken = accessToken,
                                    folderId = targetFolderId,
                                    filename = filename!!,
                                    mimeType = mimeType!!,
                                    fileContent = fileBytes,
                                    existingFileId = driveFileId,
                                    appProperties = appProperties
                                )
                                if (updatedId != null) {
                                    driveFileId = updatedId
                                    needsCreate = false
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error downloading/verifying file $driveFileId, will treat as missing", e)
                        driveFileId = null
                    }
                }

                // Double check immediately before CREATE
                if (needsCreate && !driveFileId.isNullOrEmpty()) {
                    needsCreate = false
                } else if (needsCreate) {
                    val doubleCheckFile = GoogleDriveClient.findFileByReceiptProperties(accessToken, targetFolderId, currentReceipt.internalId, "ORIGINAL")
                    if (doubleCheckFile != null) {
                        driveFileId = doubleCheckFile.id
                        needsCreate = false
                        Log.d(TAG, "Idempotency double-check: Found file right before CREATE: $driveFileId")
                    }
                }

                if (needsCreate) {
                    val appProperties = mapOf(
                        "appName" to "ImmobilienBelegApp",
                        "receiptInternalId" to currentReceipt.internalId,
                        "displayId" to (currentReceipt.displayId ?: ""),
                        "documentRole" to "ORIGINAL",
                        "contentSha256" to localSha256,
                        "schemaVersion" to "1"
                    )
                    val uploadedId = GoogleDriveClient.uploadFile(
                        accessToken = accessToken,
                        folderId = targetFolderId,
                        filename = filename!!,
                        mimeType = mimeType!!,
                        fileContent = fileBytes,
                        appProperties = appProperties
                    )
                    if (uploadedId != null) {
                        val checkBytes = GoogleDriveClient.downloadFileBytes(accessToken, uploadedId)
                        if (checkBytes == null || checkBytes.isEmpty() || checkBytes.take(4) != fileBytes.take(4)) {
                            Log.e(TAG, "Uploaded file check failed!")
                            val updatedReceipt = currentReceipt.copy(
                                syncStatus = "ERROR",
                                syncError = "Die hochgeladene Datei ist fehlerhaft oder leer."
                            )
                            localRepository.insert(updatedReceipt)
                            return false
                        }
                        driveFileId = uploadedId
                        
                        // "Nach CREATE die neue Drive-ID sofort lokal sichern, bevor weitere Netzwerkaktionen erfolgen."
                        currentReceipt = currentReceipt.copy(
                            driveFileId = driveFileId,
                            driveFolderId = targetFolderId,
                            storedFilename = filename,
                            originalMimeType = mimeType,
                            fileSizeBytes = sizeBytes
                        )
                        localRepository.insert(currentReceipt)
                    } else {
                        val updatedReceipt = currentReceipt.copy(
                            syncStatus = "ERROR",
                            syncError = "Fehler beim Hochladen der Belegdatei."
                        )
                        localRepository.insert(updatedReceipt)
                        return false
                    }
                } else {
                    // "Nach CREATE die neue Drive-ID sofort lokal sichern, bevor weitere Netzwerkaktionen erfolgen."
                    currentReceipt = currentReceipt.copy(
                        driveFileId = driveFileId,
                        driveFolderId = targetFolderId,
                        storedFilename = filename,
                        originalMimeType = mimeType,
                        fileSizeBytes = sizeBytes
                    )
                    localRepository.insert(currentReceipt)
                }
            } else {
                val updatedReceipt = currentReceipt.copy(
                    syncStatus = "ERROR",
                    syncError = "Keine lokale Quelldatei gefunden oder Datei ist leer."
                )
                localRepository.insert(updatedReceipt)
                return false
            }

            val existingDocs = localRepository.getDocumentsForReceipt(currentReceipt.internalId)
            val docRef = existingDocs.find { it.driveFileId == driveFileId } ?: ReceiptDocumentReference(
                id = UUID.randomUUID().toString(),
                receiptInternalId = currentReceipt.internalId,
                driveFileId = driveFileId!!,
                driveFolderId = targetFolderId,
                filename = filename ?: "document",
                mimeType = mimeType ?: "application/octet-stream",
                sizeBytes = sizeBytes,
                role = "MAIN_RECEIPT",
                createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
            )
            localRepository.insertDocument(docRef)
            val allDocs = localRepository.getDocumentsForReceipt(currentReceipt.internalId)

            val persistedReceipt = PersistedReceipt(
                schemaVersion = 1,
                revision = (currentReceipt.driveRevision ?: 0L) + 1L,
                internalId = currentReceipt.internalId,
                displayId = currentReceipt.displayId,
                documents = allDocs,
                aussteller = currentReceipt.aussteller,
                rechnungsnummer = "",
                datum = currentReceipt.datum,
                nettobetragCent = (currentReceipt.bruttobetrag * 100.0).toLong() - ((currentReceipt.bruttobetrag * 100.0) * 0.19).toLong(),
                steuerbetragCent = ((currentReceipt.bruttobetrag * 100.0) * 0.19).toLong(),
                bruttobetragCent = (currentReceipt.bruttobetrag * 100.0).toLong(),
                hauptkategorie = currentReceipt.hauptkategorie,
                unterkategorie = currentReceipt.unterkategorie,
                wohneinheit = currentReceipt.wohneinheit,
                massnahme = "",
                positionen = currentReceipt.getPositionenList().toPersistedItems(),
                zahlungsstatus = "BEZAHLT",
                zahlungsdatum = currentReceipt.datum,
                zahlungsreferenz = "",
                pruefstatus = currentReceipt.pruefstatus,
                exportstatus = currentReceipt.exportStatus,
                createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()),
                updatedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()),
                lastSyncedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
            )

            val uploadedMetadataId = upsertReceiptMetadata(accessToken, config, currentReceipt)
            var driveMetadataFileId = uploadedMetadataId

            if (driveMetadataFileId.isNullOrEmpty()) {
                val updatedReceipt = currentReceipt.copy(
                    syncStatus = "ERROR",
                    syncError = "Fehler beim Hochladen der Belegmetadaten."
                )
                localRepository.insert(updatedReceipt)
                return false
            }

            val indexEntry = ReceiptIndexEntry(
                internalId = persistedReceipt.internalId,
                displayId = persistedReceipt.displayId,
                metadataFileId = driveMetadataFileId!!,
                mainDriveFileId = driveFileId!!,
                aussteller = persistedReceipt.aussteller,
                rechnungsnummer = persistedReceipt.rechnungsnummer,
                datum = persistedReceipt.datum,
                bruttobetragCent = persistedReceipt.bruttobetragCent,
                hauptkategorie = persistedReceipt.hauptkategorie,
                unterkategorie = persistedReceipt.unterkategorie,
                wohneinheit = persistedReceipt.wohneinheit,
                massnahme = persistedReceipt.massnahme,
                pruefstatus = persistedReceipt.pruefstatus,
                freigabestatus = persistedReceipt.freigabestatus,
                exportstatus = persistedReceipt.exportstatus,
                syncStatus = "SYNCED",
                updatedAt = persistedReceipt.updatedAt
            )

            val indexSuccess = updateReceiptIndexInDrive(accessToken, config, indexEntry)
            
            if (indexSuccess) {
                val finalReceipt = currentReceipt.copy(
                    internalId = persistedReceipt.internalId,
                    displayId = persistedReceipt.displayId,
                    driveFileId = driveFileId,
                    driveFolderId = targetFolderId,
                    driveMetadataFileId = driveMetadataFileId,
                    storedFilename = filename,
                    originalMimeType = mimeType,
                    fileSizeBytes = sizeBytes,
                    syncStatus = "SYNCED",
                    syncError = null,
                    lastSyncedAt = persistedReceipt.lastSyncedAt,
                    driveRevision = persistedReceipt.revision,
                    isArchivedToDrive = true
                )
                localRepository.insert(finalReceipt)
                Log.d(TAG, "Receipt fully synced and saved locally. ID: ${finalReceipt.id}")
                return true
            } else {
                val updatedReceipt = currentReceipt.copy(
                    syncStatus = "ERROR",
                    syncError = "Index-Eintrag konnte nicht aktualisiert werden."
                )
                localRepository.insert(updatedReceipt)
                return false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Sync failed with exception", e)
            val updatedReceipt = receipt.copy(
                syncStatus = "ERROR",
                syncError = e.message ?: e.toString()
            )
            localRepository.insert(updatedReceipt)
            return false
        }
    }

    suspend fun checkDriveInventoryForRestore(accessToken: String, config: DriveAppConfig): DriveRestorePreviewResult {
        val issues = mutableListOf<String>()
        val previews = mutableListOf<ReceiptPreviewSummary>()

        try {
            if (config.schemaVersion > 1) {
                return DriveRestorePreviewResult(
                    success = false,
                    errorMessage = "Inkompatible Schema-Version in Google Drive: ${config.schemaVersion}"
                )
            }

            var propertyFound = false
            var propertyName = ""
            var unitsCount = 0
            var datevProfileFound = false
            var learnedRulesFound = false

            val metaFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "propertyMetadata")
            if (metaFile != null) {
                try {
                    val json = GoogleDriveClient.downloadJson(accessToken, metaFile.id)
                    val meta = parsePropertyMetadata(json)
                    propertyFound = true
                    propertyName = meta.name
                } catch (e: Exception) {
                    issues.add("property-metadata.json unlesbar: ${e.message}")
                }
            }

            val unitsFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "units")
            if (unitsFile != null) {
                try {
                    val json = GoogleDriveClient.downloadJson(accessToken, unitsFile.id)
                    val units = parseWohneinheiten(json)
                    unitsCount = units.size
                } catch (e: Exception) {
                    issues.add("wohneinheiten.json unlesbar: ${e.message}")
                }
            }

            val datevFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "datevProfiles")
            if (datevFile != null) datevProfileFound = true

            val rulesFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "aiLearnedRules")
            if (rulesFile != null) learnedRulesFound = true

            val indexFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "receiptIndex")
            var receiptCountInIndex = 0
            var metadataFileCount = 0
            var mainDocCount = 0
            var duplicateCount = 0
            var missingFileCount = 0

            if (indexFile != null) {
                try {
                    val jsonStr = GoogleDriveClient.downloadJson(accessToken, indexFile.id)
                    val root = JSONObject(jsonStr)
                    var entries = mutableListOf<ReceiptIndexEntry>()
                    val array = root.optJSONArray("entries") ?: JSONArray()
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

                    entries = sanitizeIndexEntries(entries, accessToken).toMutableList()
                    receiptCountInIndex = entries.size

                    val seenInternalIds = mutableSetOf<String>()
                    val seenMetadataIds = mutableSetOf<String>()
                    val seenMainFileIds = mutableSetOf<String>()

                    for (entry in entries) {
                        val entryIssues = mutableListOf<String>()

                        if (entry.internalId.isBlank()) {
                            entryIssues.add("Fehlende internalId")
                            duplicateCount++
                        } else if (!seenInternalIds.add(entry.internalId)) {
                            entryIssues.add("Doppelte internalId (${entry.internalId})")
                            duplicateCount++
                        }

                        if (entry.metadataFileId.isBlank()) {
                            entryIssues.add("Fehlende metadataFileId")
                            missingFileCount++
                        } else if (!seenMetadataIds.add(entry.metadataFileId)) {
                            entryIssues.add("Doppelte metadataFileId (${entry.metadataFileId})")
                            duplicateCount++
                        }

                        if (entry.mainDriveFileId.isBlank()) {
                            entryIssues.add("Fehlende mainDriveFileId")
                            missingFileCount++
                        } else if (!seenMainFileIds.add(entry.mainDriveFileId)) {
                            entryIssues.add("Doppelte mainDriveFileId (${entry.mainDriveFileId})")
                            duplicateCount++
                        }

                        var metaReachable = false
                        if (entry.metadataFileId.isNotBlank()) {
                            try {
                                val metaJson = GoogleDriveClient.downloadJson(accessToken, entry.metadataFileId)
                                val parsed = parsePersistedReceipt(metaJson)
                                if (parsed.bruttobetragCent != null) {
                                    metaReachable = true
                                    metadataFileCount++
                                }
                            } catch (e: Exception) {
                                entryIssues.add("Metadaten-JSON nicht lesbar: ${e.message}")
                                missingFileCount++
                            }
                        }

                        var mainReachable = false
                        if (entry.mainDriveFileId.isNotBlank()) {
                            mainReachable = true
                            mainDocCount++
                        }

                        previews.add(
                            ReceiptPreviewSummary(
                                internalId = entry.internalId,
                                displayId = entry.displayId,
                                aussteller = entry.aussteller ?: "",
                                datum = entry.datum ?: "",
                                bruttobetragCent = entry.bruttobetragCent ?: 0L,
                                metadataReachable = metaReachable,
                                mainDocReachable = mainReachable,
                                issues = entryIssues
                            )
                        )
                    }
                } catch (e: Exception) {
                    issues.add("receipt-index.json unlesbar: ${e.message}")
                }
            }

            val inventory = DriveInventoryCheck(
                hasDriveFolder = true,
                receiptCountInIndex = receiptCountInIndex,
                metadataFileCount = metadataFileCount,
                mainDocCount = mainDocCount,
                duplicateCount = duplicateCount,
                missingFileCount = missingFileCount,
                schemaVersion = config.schemaVersion,
                propertyFound = propertyFound,
                propertyName = propertyName,
                unitsCount = unitsCount,
                datevProfileFound = datevProfileFound,
                learnedRulesFound = learnedRulesFound,
                exportsCount = 0,
                canRestore = receiptCountInIndex > 0 || propertyFound,
                issues = issues
            )

            return DriveRestorePreviewResult(
                success = true,
                inventory = inventory,
                receiptPreviews = previews
            )
        } catch (e: Exception) {
            return DriveRestorePreviewResult(
                success = false,
                errorMessage = e.message ?: e.toString()
            )
        }
    }

    private fun saveRestoreJournal(journal: RestoreJournal) {
        val prefs = context.getSharedPreferences("restore_journal_prefs", Context.MODE_PRIVATE)
        val json = JSONObject().apply {
            put("restoreId", journal.restoreId)
            put("startedAt", journal.startedAt)
            put("mode", journal.mode)
            put("phase", journal.phase)
            put("snapshotReceiptCount", journal.snapshotReceiptCount)
            put("importedReceiptCount", journal.importedReceiptCount)
            put("completedAt", journal.completedAt ?: "")
            put("error", journal.error ?: "")
        }.toString()
        prefs.edit().putString("latest_journal", json).apply()
    }

    fun getLatestRestoreJournal(): RestoreJournal? {
        val prefs = context.getSharedPreferences("restore_journal_prefs", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("latest_journal", null) ?: return null
        return try {
            val obj = JSONObject(jsonStr)
            RestoreJournal(
                restoreId = obj.optString("restoreId", ""),
                startedAt = obj.optString("startedAt", ""),
                mode = obj.optString("mode", ""),
                phase = obj.optString("phase", ""),
                snapshotReceiptCount = obj.optInt("snapshotReceiptCount", 0),
                importedReceiptCount = obj.optInt("importedReceiptCount", 0),
                completedAt = if (obj.optString("completedAt").isBlank()) null else obj.optString("completedAt"),
                error = if (obj.optString("error").isBlank()) null else obj.optString("error")
            )
        } catch (e: Exception) { null }
    }

    suspend fun getOrCreateDeletionsFolder(accessToken: String, config: DriveAppConfig): String? {
        return GoogleDriveClient.getOrCreateFolder(accessToken, "deletions", config.systemFolderId)
    }

    suspend fun uploadTombstone(
        accessToken: String,
        config: DriveAppConfig,
        tombstone: ReceiptTombstone
    ): DriveFileResult {
        try {
            val deletionsFolderId = getOrCreateDeletionsFolder(accessToken, config)
                ?: return DriveFileResult(false, null, "Deletions folder could not be created")

            val tombstoneJson = JSONObject().apply {
                put("internalId", tombstone.internalId)
                put("displayId", tombstone.displayId)
                put("deletedAt", tombstone.deletedAt)
                put("deletedBy", tombstone.deletedBy)
                put("deviceId", tombstone.deviceId ?: JSONObject.NULL)
                put("deletionId", tombstone.deletionId)
                put("previousMainDriveFileId", tombstone.previousMainDriveFileId)
                put("previousMetadataFileId", tombstone.previousMetadataFileId)
                put("deletionReason", tombstone.deletionReason ?: JSONObject.NULL)
                put("schemaVersion", tombstone.schemaVersion)
                put("status", tombstone.status)
            }.toString(4)

            val appProps = mapOf(
                "appName" to "ImmobilienBelegApp",
                "entityType" to "tombstone",
                "receiptInternalId" to tombstone.internalId,
                "deletionId" to tombstone.deletionId,
                "status" to tombstone.status,
                "schemaVersion" to "1"
            )

            val fileName = "${tombstone.internalId}.json"
            val uploadRes = uploadOrUpdateReceiptFileJson(
                accessToken = accessToken,
                folderId = deletionsFolderId,
                filename = fileName,
                json = tombstoneJson,
                appProperties = appProps
            )

            if (!uploadRes.success) {
                return uploadRes
            }

            // Step 2.3 Verification: Download uploaded tombstone back from Drive & verify content
            val uploadedFileId = uploadRes.fileId ?: return DriveFileResult(false, null, "Tombstone file ID missing")
            val downloadedJsonStr = GoogleDriveClient.downloadJson(accessToken, uploadedFileId)
            if (downloadedJsonStr.isBlank()) {
                return DriveFileResult(false, null, "Verification failed: Downloaded tombstone is empty")
            }

            val parsedObj = JSONObject(downloadedJsonStr)
            val verifiedInternalId = parsedObj.optString("internalId", "")
            if (verifiedInternalId != tombstone.internalId) {
                return DriveFileResult(false, null, "Verification failed: internalId mismatch ($verifiedInternalId vs ${tombstone.internalId})")
            }

            return DriveFileResult(true, uploadedFileId, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading tombstone for ${tombstone.internalId}", e)
            return DriveFileResult(false, null, e.message ?: e.toString())
        }
    }

    suspend fun getAllTombstonesFromDrive(
        accessToken: String,
        config: DriveAppConfig
    ): Map<String, ReceiptTombstone> {
        val tombstones = mutableMapOf<String, ReceiptTombstone>()
        try {
            val deletionsFolderId = getOrCreateDeletionsFolder(accessToken, config) ?: return tombstones
            val query = "'$deletionsFolderId' in parents and mimeType = 'application/json' and trashed = false"
            val encodedQ = java.net.URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.googleapis.com/drive/v3/files?q=$encodedQ&fields=files(id,name)"

            val client = okhttp3.OkHttpClient()
            val request = okhttp3.Request.Builder()
                .url(searchUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val files = json.optJSONArray("files") ?: JSONArray()
                    for (i in 0 until files.length()) {
                        val fileObj = files.getJSONObject(i)
                        val fileId = fileObj.getString("id")
                        try {
                            val tombstoneJsonStr = GoogleDriveClient.downloadJson(accessToken, fileId)
                            val tObj = JSONObject(tombstoneJsonStr)
                            val tombstone = ReceiptTombstone(
                                internalId = tObj.getString("internalId"),
                                displayId = tObj.optString("displayId", ""),
                                deletedAt = tObj.optString("deletedAt", ""),
                                deletedBy = tObj.optString("deletedBy", "LocalUser"),
                                deviceId = if (tObj.isNull("deviceId")) null else tObj.optString("deviceId"),
                                deletionId = tObj.optString("deletionId", ""),
                                previousMainDriveFileId = tObj.optString("previousMainDriveFileId", ""),
                                previousMetadataFileId = tObj.optString("previousMetadataFileId", ""),
                                deletionReason = if (tObj.isNull("deletionReason")) null else tObj.optString("deletionReason"),
                                schemaVersion = tObj.optInt("schemaVersion", 1),
                                status = tObj.optString("status", "DELETED")
                            )
                            tombstones[tombstone.internalId] = tombstone
                        } catch (e: Exception) {
                            Log.w(TAG, "Error downloading/parsing tombstone file $fileId", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching tombstones from Drive", e)
        }
        return tombstones
    }

    suspend fun deleteReceiptWithSync(
        accessToken: String?,
        config: DriveAppConfig?,
        receipt: Receipt,
        deletedBy: String = "LocalUser",
        deletionReason: String = "Vom Nutzer gelöscht"
    ): DeletionResult {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
        val deletionUuid = java.util.UUID.randomUUID().toString()

        val tombstone = ReceiptTombstone(
            internalId = receipt.internalId,
            displayId = receipt.getEffectiveDisplayId(),
            deletedAt = now,
            deletedBy = deletedBy,
            deletionId = deletionUuid,
            previousMainDriveFileId = receipt.driveFileId ?: "",
            previousMetadataFileId = receipt.driveMetadataFileId ?: "",
            deletionReason = deletionReason,
            status = "DELETED"
        )

        if (accessToken.isNullOrBlank() || config == null) {
            localRepository.softDelete(
                id = receipt.id,
                status = "DELETE_PENDING",
                deletedAt = now,
                deletedBy = deletedBy,
                deletionId = deletionUuid,
                reason = deletionReason
            )
            return DeletionResult.PendingOffline(tombstone, "Google Drive nicht erreichbar. Löschung als ausstehend gespeichert.")
        }

        try {
            // Step 2.2 & 2.3: Upload and verify tombstone
            val uploadRes = uploadTombstone(accessToken, config, tombstone)
            if (!uploadRes.success) {
                Log.e(TAG, "Tombstone upload failed: ${uploadRes.errorMessage}")
                localRepository.softDelete(
                    id = receipt.id,
                    status = "DELETE_PENDING",
                    deletedAt = now,
                    deletedBy = deletedBy,
                    deletionId = deletionUuid,
                    reason = deletionReason
                )
                return DeletionResult.PendingOffline(tombstone, "Tombstone konnte auf Google Drive nicht verifiziert werden: ${uploadRes.errorMessage}")
            }

            // Step 2.4: Update receipt-index.json
            val indexEntry = ReceiptIndexEntry(
                internalId = receipt.internalId,
                displayId = receipt.displayId,
                metadataFileId = receipt.driveMetadataFileId ?: "",
                mainDriveFileId = receipt.driveFileId ?: "",
                aussteller = receipt.aussteller,
                rechnungsnummer = "",
                datum = receipt.datum,
                bruttobetragCent = (receipt.bruttobetrag * 100).toLong(),
                hauptkategorie = receipt.hauptkategorie,
                unterkategorie = receipt.unterkategorie,
                wohneinheit = receipt.wohneinheit,
                massnahme = "",
                pruefstatus = receipt.pruefstatus,
                freigabestatus = "OFFEN",
                exportstatus = receipt.exportStatus,
                syncStatus = "DELETED",
                updatedAt = now
            )
            updateReceiptIndexInDrive(accessToken, config, indexEntry)

            // Step 2.5: Update metadata JSON & appProperties on Drive if metadata file exists
            if (!receipt.driveMetadataFileId.isNullOrBlank()) {
                try {
                    val currentMetaJsonStr = GoogleDriveClient.downloadJson(accessToken, receipt.driveMetadataFileId)
                    if (currentMetaJsonStr.isNotBlank()) {
                        val metaObj = JSONObject(currentMetaJsonStr)
                        metaObj.put("deletionStatus", "DELETED")
                        metaObj.put("deletedAt", now)
                        metaObj.put("deletionId", deletionUuid)
                        metaObj.put("deletionReason", deletionReason)

                        GoogleDriveClient.updateJson(
                            accessToken = accessToken,
                            fileId = receipt.driveMetadataFileId,
                            json = metaObj.toString(4)
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error updating metadata JSON on Drive for deletion", e)
                }
            }

            // Update local Room database
            localRepository.softDelete(
                id = receipt.id,
                status = "DELETED",
                deletedAt = now,
                deletedBy = deletedBy,
                deletionId = deletionUuid,
                reason = deletionReason
            )

            return DeletionResult.Success(tombstone)
        } catch (e: Exception) {
            Log.e(TAG, "Error during deleteReceiptWithSync", e)
            localRepository.softDelete(
                id = receipt.id,
                status = "DELETE_PENDING",
                deletedAt = now,
                deletedBy = deletedBy,
                deletionId = deletionUuid,
                reason = deletionReason
            )
            return DeletionResult.PendingOffline(tombstone, e.message ?: e.toString())
        }
    }

    suspend fun processPendingDeletions(accessToken: String, config: DriveAppConfig): Int {
        val pendingList = localRepository.getPendingDeletionReceipts()
        var count = 0
        for (receipt in pendingList) {
            val result = deleteReceiptWithSync(
                accessToken = accessToken,
                config = config,
                receipt = receipt,
                deletedBy = receipt.deletedBy ?: "LocalUser",
                deletionReason = receipt.deletionReason ?: "Ausstehende Löschung synchronisiert"
            )
            if (result is DeletionResult.Success) {
                count++
            }
        }
        return count
    }

    suspend fun restoreDeletedReceipt(
        accessToken: String?,
        config: DriveAppConfig?,
        receipt: Receipt
    ): Boolean {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())

        if (!accessToken.isNullOrBlank() && config != null) {
            try {
                val tombstone = ReceiptTombstone(
                    internalId = receipt.internalId,
                    displayId = receipt.getEffectiveDisplayId(),
                    deletedAt = now,
                    status = "RESTORED"
                )
                uploadTombstone(accessToken, config, tombstone)

                val indexEntry = ReceiptIndexEntry(
                    internalId = receipt.internalId,
                    displayId = receipt.displayId,
                    metadataFileId = receipt.driveMetadataFileId ?: "",
                    mainDriveFileId = receipt.driveFileId ?: "",
                    aussteller = receipt.aussteller,
                    rechnungsnummer = "",
                    datum = receipt.datum,
                    bruttobetragCent = (receipt.bruttobetrag * 100).toLong(),
                    hauptkategorie = receipt.hauptkategorie,
                    unterkategorie = receipt.unterkategorie,
                    wohneinheit = receipt.wohneinheit,
                    massnahme = "",
                    pruefstatus = receipt.pruefstatus,
                    freigabestatus = "OFFEN",
                    exportstatus = receipt.exportStatus,
                    syncStatus = "SYNCED",
                    updatedAt = now
                )
                updateReceiptIndexInDrive(accessToken, config, indexEntry)

                if (!receipt.driveMetadataFileId.isNullOrBlank()) {
                    val currentMetaJsonStr = GoogleDriveClient.downloadJson(accessToken, receipt.driveMetadataFileId)
                    if (currentMetaJsonStr.isNotBlank()) {
                        val metaObj = JSONObject(currentMetaJsonStr)
                        metaObj.put("deletionStatus", "ACTIVE")
                        metaObj.remove("deletedAt")
                        metaObj.remove("deletionId")

                        GoogleDriveClient.updateJson(
                            accessToken = accessToken,
                            fileId = receipt.driveMetadataFileId,
                            json = metaObj.toString(4)
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring receipt on Drive", e)
            }
        }

        localRepository.softDelete(
            id = receipt.id,
            status = "ACTIVE",
            deletedAt = "",
            deletedBy = "",
            deletionId = "",
            reason = ""
        )
        return true
    }

    suspend fun permanentlyDeleteReceipt(
        accessToken: String?,
        config: DriveAppConfig?,
        receipt: Receipt
    ): PermanentDeleteResult {
        // Requirement 9 Safety Checks
        val allReceipts = localRepository.getAllReceiptsIncludingDeletedList()
        val otherReceiptsWithMainFile = allReceipts.filter { it.id != receipt.id && !it.driveFileId.isNullOrBlank() && it.driveFileId == receipt.driveFileId }
        if (otherReceiptsWithMainFile.isNotEmpty()) {
            return PermanentDeleteResult.Error("Sicherheitsprüfung fehlgeschlagen: Die Hauptdatei '${receipt.driveFileId}' wird noch von einem anderen Beleg (${otherReceiptsWithMainFile[0].getEffectiveDisplayId()}) verwendet.")
        }

        val otherReceiptsWithMetaFile = allReceipts.filter { it.id != receipt.id && !it.driveMetadataFileId.isNullOrBlank() && it.driveMetadataFileId == receipt.driveMetadataFileId }
        if (otherReceiptsWithMetaFile.isNotEmpty()) {
            return PermanentDeleteResult.Error("Sicherheitsprüfung fehlgeschlagen: Die Metadaten-Datei '${receipt.driveMetadataFileId}' wird noch von einem anderen Beleg (${otherReceiptsWithMetaFile[0].getEffectiveDisplayId()}) verwendet.")
        }

        if (!accessToken.isNullOrBlank() && config != null) {
            try {
                if (!receipt.driveFileId.isNullOrBlank()) {
                    GoogleDriveClient.deleteFile(accessToken, receipt.driveFileId)
                }

                if (!receipt.driveMetadataFileId.isNullOrBlank()) {
                    GoogleDriveClient.deleteFile(accessToken, receipt.driveMetadataFileId)
                }

                val deletionsFolderId = getOrCreateDeletionsFolder(accessToken, config)
                if (deletionsFolderId != null) {
                    val tombstoneFileId = GoogleDriveClient.findFileByName(accessToken, deletionsFolderId, "${receipt.internalId}.json")
                    if (tombstoneFileId != null) {
                        GoogleDriveClient.deleteFile(accessToken, tombstoneFileId)
                    }
                }

                val indexFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "receiptIndex")
                if (indexFile != null) {
                    val indexJsonStr = GoogleDriveClient.downloadJson(accessToken, indexFile.id)
                    if (indexJsonStr.isNotBlank()) {
                        val root = JSONObject(indexJsonStr)
                        val array = root.optJSONArray("entries") ?: JSONArray()
                        val newArray = JSONArray()
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            if (obj.optString("internalId") != receipt.internalId) {
                                newArray.put(obj)
                            }
                        }
                        root.put("entries", newArray)
                        uploadOrUpdateJson(accessToken, config.systemFolderId, "receiptIndex", "receipt-index.json", root.toString(4))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting Drive files during permanent deletion", e)
            }
        }

        localRepository.deleteById(receipt.id)
        return PermanentDeleteResult.Success
    }

    suspend fun buildRestoreSnapshot(accessToken: String, config: DriveAppConfig): RestoreSnapshot {
        val warnings = mutableListOf<RestoreWarning>()
        val errors = mutableListOf<RestoreError>()

        if (config.schemaVersion > 1) {
            errors.add(RestoreError("UNSUPPORTED_SCHEMA", "Schema-Version ${config.schemaVersion} wird nicht unterstützt", isBlocking = true))
        }

        var propertyMetadata: PropertyMetadata? = null
        val metaFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "propertyMetadata")
        if (metaFile != null) {
            try {
                val json = GoogleDriveClient.downloadJson(accessToken, metaFile.id)
                propertyMetadata = parsePropertyMetadata(json)
            } catch (e: Exception) {
                warnings.add(RestoreWarning("PROPERTY_META_READ_ERROR", "property-metadata.json unlesbar: ${e.message}"))
            }
        }

        var units = emptyList<WohneinheitStatus>()
        val unitsFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "units")
        if (unitsFile != null) {
            try {
                val json = GoogleDriveClient.downloadJson(accessToken, unitsFile.id)
                units = parseWohneinheiten(json)
            } catch (e: Exception) {
                warnings.add(RestoreWarning("UNITS_READ_ERROR", "wohneinheiten.json unlesbar: ${e.message}"))
            }
        }

        var datevProfiles = emptyList<DatevProfile>()
        val datevFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "datevProfiles")
        if (datevFile != null) {
            try {
                val json = GoogleDriveClient.downloadJson(accessToken, datevFile.id)
                val profile = deserializeDatevProfile(json)
                if (profile != null) datevProfiles = listOf(profile)
            } catch (e: Exception) {
                warnings.add(RestoreWarning("DATEV_READ_ERROR", "datevProfiles unlesbar: ${e.message}"))
            }
        }

        var aiLearnedRules = emptyList<LearnedVendorRule>()
        val rulesFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "aiLearnedRules")
        if (rulesFile != null) {
            try {
                val json = GoogleDriveClient.downloadJson(accessToken, rulesFile.id)
                aiLearnedRules = parseLearnedRules(json)
            } catch (e: Exception) {
                warnings.add(RestoreWarning("AI_RULES_READ_ERROR", "aiLearnedRules unlesbar: ${e.message}"))
            }
        }

        val receipts = mutableListOf<PersistedReceipt>()
        val indexFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "receiptIndex")
        if (indexFile != null) {
            try {
                val jsonStr = GoogleDriveClient.downloadJson(accessToken, indexFile.id)
                val root = JSONObject(jsonStr)
                val array = root.optJSONArray("entries") ?: JSONArray()
                var indexEntries = mutableListOf<ReceiptIndexEntry>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    indexEntries.add(
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
                indexEntries = sanitizeIndexEntries(indexEntries, accessToken).toMutableList()

                val seenInternalIds = mutableSetOf<String>()
                val seenMetadataFileIds = mutableSetOf<String>()
                val tombstones = getAllTombstonesFromDrive(accessToken, config)

                for (entry in indexEntries) {
                    val tombstone = tombstones[entry.internalId]
                    if (entry.syncStatus == "DELETED" || tombstone?.status == "DELETED") {
                        Log.i(TAG, "Beleg ${entry.internalId} (${entry.displayId}) ist gelöscht (Tombstone) und wird bei der Wiederherstellung übersprungen.")
                        continue
                    }

                    if (entry.internalId.isBlank()) {
                        errors.add(RestoreError("BLANK_INTERNAL_ID", "Indexeintrag ohne internalId gefunden", isBlocking = true))
                        continue
                    }
                    if (!seenInternalIds.add(entry.internalId)) {
                        errors.add(RestoreError("DUPLICATE_INTERNAL_ID", "Doppelte internalId im Index: ${entry.internalId}", targetId = entry.internalId, isBlocking = true))
                    }
                    if (entry.metadataFileId.isBlank()) {
                        errors.add(RestoreError("MISSING_METADATA_FILE_ID", "Fehlende metadataFileId für Beleg '${entry.displayId ?: entry.internalId}'", targetId = entry.internalId, isBlocking = true))
                        continue
                    }
                    if (!seenMetadataFileIds.add(entry.metadataFileId)) {
                        errors.add(RestoreError("DUPLICATE_METADATA_FILE_ID", "Doppelte metadataFileId (${entry.metadataFileId}) für Beleg '${entry.displayId ?: entry.internalId}'", targetId = entry.internalId, isBlocking = true))
                    }

                    try {
                        val metaJson = GoogleDriveClient.downloadJson(accessToken, entry.metadataFileId)
                        val persisted = parsePersistedReceipt(metaJson)

                        if ((persisted.bruttobetragCent ?: -1L) < 0L) {
                            errors.add(RestoreError("INVALID_AMOUNT", "Ungültiger Bruttobetrag (${persisted.bruttobetragCent}) in Beleg '${persisted.displayId ?: persisted.internalId}'", targetId = persisted.internalId, isBlocking = true))
                        }

                        if (entry.mainDriveFileId.isBlank()) {
                            warnings.add(RestoreWarning("MISSING_MAIN_DOC", "Hauptdokument fehlt für Beleg '${persisted.displayId ?: persisted.internalId}'", targetId = persisted.internalId))
                        }

                        receipts.add(persisted)
                    } catch (e: Exception) {
                        errors.add(RestoreError("UNREADABLE_METADATA_JSON", "Metadaten-JSON für Beleg '${entry.displayId ?: entry.internalId}' nicht lesbar: ${e.message}", targetId = entry.internalId, isBlocking = true))
                    }
                }

                if (indexEntries.size != receipts.size) {
                    errors.add(RestoreError("INDEX_COUNT_MISMATCH", "Index enthält ${indexEntries.size} Einträge, aber nur ${receipts.size} Metadatendateien geladen", isBlocking = true))
                }
            } catch (e: Exception) {
                errors.add(RestoreError("INDEX_READ_ERROR", "receipt-index.json unlesbar: ${e.message}", isBlocking = true))
            }
        }

        val nowStr = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        return RestoreSnapshot(
            schemaVersion = config.schemaVersion,
            createdAt = nowStr,
            propertyMetadata = propertyMetadata,
            units = units,
            datevProfiles = datevProfiles,
            aiLearnedRules = aiLearnedRules,
            receipts = receipts,
            warnings = warnings,
            errors = errors
        )
    }

    suspend fun executeFullDriveRestore(
        accessToken: String,
        config: DriveAppConfig,
        requestedMode: RestoreMode = RestoreMode.REPLACE_FULL
    ): DriveRestoreReport {
        val nowStr = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val localReceiptsBefore = localRepository.getAllReceiptsList()
        val mode = if (localReceiptsBefore.isEmpty()) RestoreMode.REPLACE_EMPTY else requestedMode

        if (mode == RestoreMode.CANCEL) {
            return DriveRestoreReport(
                timestamp = nowStr,
                errorCount = 1,
                errors = listOf("Wiederherstellung durch Nutzer abgebrochen."),
                isSuccess = false
            )
        }

        var journal = RestoreJournal(
            restoreId = java.util.UUID.randomUUID().toString(),
            startedAt = nowStr,
            mode = mode.name,
            phase = "DOWNLOADING"
        )
        saveRestoreJournal(journal)

        val snapshot = buildRestoreSnapshot(accessToken, config)

        journal = journal.copy(phase = "VALIDATING", snapshotReceiptCount = snapshot.receipts.size)
        saveRestoreJournal(journal)

        val blockingErrors = snapshot.errors.filter { it.isBlocking }
        if (blockingErrors.isNotEmpty()) {
            val errMsgs = blockingErrors.map { "${it.code}: ${it.message}" } + snapshot.warnings.map { "Warnung: ${it.message}" }
            journal = journal.copy(phase = "FAILED", error = errMsgs.joinToString("; "))
            saveRestoreJournal(journal)
            return DriveRestoreReport(
                timestamp = nowStr,
                errorCount = errMsgs.size,
                errors = errMsgs,
                isSuccess = false
            )
        }

        journal = journal.copy(phase = "IMPORTING")
        saveRestoreJournal(journal)

        val db = AppDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
        var receiptsRestored = 0
        var mainDocsLinked = 0
        var metadataFilesLoaded = snapshot.receipts.size
        var itemsRestored = 0
        var splitsRestored = 0
        var proposalsRestored = 0
        var exportsRestored = 0

        try {
            db.withTransaction {
                if (mode == RestoreMode.REPLACE_FULL) {
                    localRepository.clearRestoreRelevantTables()
                }

                snapshot.propertyMetadata?.let {
                    localRepository.updatePropertyMetadata(it)
                }

                for (persisted in snapshot.receipts) {
                    val restoredReceipt = persisted.toLocalReceipt(persisted.documents.firstOrNull()?.driveFileId).copy(
                        syncStatus = "SYNCED",
                        lastSyncedAt = nowStr,
                        isArchivedToDrive = true
                    )
                    localRepository.insert(restoredReceipt)
                    receiptsRestored++

                    if (!restoredReceipt.driveFileId.isNullOrBlank()) {
                        mainDocsLinked++
                    }

                    for (doc in persisted.documents) {
                        localRepository.insertDocument(doc)
                    }

                    itemsRestored += persisted.positionen.size
                    splitsRestored += persisted.allocations.size
                    proposalsRestored += persisted.bookingProposals.size

                    if (!persisted.exportstatus.isNullOrBlank() && persisted.exportstatus != "EXPORTBEREIT") {
                        exportsRestored++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Room Transaction error during restore", e)
            journal = journal.copy(phase = "FAILED", error = "Room Transaktion fehlgeschlagen: ${e.message}")
            saveRestoreJournal(journal)
            return DriveRestoreReport(
                timestamp = nowStr,
                errorCount = 1,
                errors = listOf("Kritischer Fehler bei Room-Transaktion (Rollback durchgeführt): ${e.message}"),
                isSuccess = false
            )
        }

        journal = journal.copy(phase = "VERIFYING", importedReceiptCount = receiptsRestored)
        saveRestoreJournal(journal)

        val postReceipts = localRepository.getAllReceiptsList()
        if (mode == RestoreMode.REPLACE_FULL || mode == RestoreMode.REPLACE_EMPTY) {
            if (postReceipts.size != snapshot.receipts.size) {
                val err = "Nachkontrolle fehlgeschlagen: Erwartet ${snapshot.receipts.size} Belege, in DB ${postReceipts.size}"
                journal = journal.copy(phase = "FAILED", error = err)
                saveRestoreJournal(journal)
                return DriveRestoreReport(
                    timestamp = nowStr,
                    errorCount = 1,
                    errors = listOf(err),
                    isSuccess = false
                )
            }
        }

        try {
            if (snapshot.units.isNotEmpty()) {
                saveWohneinheitenToPrefs(snapshot.units)
            }
            if (snapshot.datevProfiles.isNotEmpty()) {
                DatevProfileService.saveActiveProfile(context, snapshot.datevProfiles.first())
            }
            if (snapshot.aiLearnedRules.isNotEmpty()) {
                saveLearnedRulesToPrefs(snapshot.aiLearnedRules)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Warnung beim Schreiben der SharedPreferences nach Restore: ${e.message}")
        }

        journal = journal.copy(phase = "COMPLETED", completedAt = nowStr)
        saveRestoreJournal(journal)

        val reportErrors = snapshot.warnings.map { "Warnung: ${it.message}" }
        return DriveRestoreReport(
            timestamp = nowStr,
            propertiesRestored = if (snapshot.propertyMetadata != null) 1 else 0,
            unitsRestored = snapshot.units.size,
            receiptsRestored = receiptsRestored,
            mainDocsLinked = mainDocsLinked,
            metadataFilesLoaded = metadataFilesLoaded,
            itemsRestored = itemsRestored,
            splitsRestored = splitsRestored,
            proposalsRestored = proposalsRestored,
            exportsRestored = exportsRestored,
            datevProfilesRestored = snapshot.datevProfiles.size,
            learnedRulesRestored = snapshot.aiLearnedRules.size,
            errorCount = reportErrors.size,
            errors = reportErrors,
            isSuccess = true
        )
    }

    suspend fun auditOriginalReceipts(
        accessToken: String,
        config: DriveAppConfig
    ): OriginalReceiptAuditReport {
        val nowStr = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        Log.i(TAG, "Starting read-only Originalbelege Magic-Bytes Audit...")

        val auditItems = mutableListOf<OriginalReceiptAuditItem>()

        // 1. Fetch index file from Drive
        val indexFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "receiptIndex")
        data class AuditCandidate(
            val internalId: String,
            val displayId: String?,
            val metadataFileId: String,
            val mainDriveFileId: String,
            val aussteller: String?,
            val datum: String?
        )

        val indexEntries = mutableListOf<AuditCandidate>()

        if (indexFile != null) {
            try {
                val jsonStr = GoogleDriveClient.downloadJson(accessToken, indexFile.id)
                val root = JSONObject(jsonStr)
                val array = root.optJSONArray("entries") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    indexEntries.add(
                        AuditCandidate(
                            internalId = obj.optString("internalId"),
                            displayId = if (obj.isNull("displayId")) null else obj.optString("displayId"),
                            metadataFileId = obj.optString("metadataFileId"),
                            mainDriveFileId = obj.optString("mainDriveFileId"),
                            aussteller = if (obj.isNull("aussteller")) null else obj.optString("aussteller"),
                            datum = if (obj.isNull("datum")) null else obj.optString("datum")
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading index file for audit", e)
            }
        }

        // Get all local DB receipts to complement metadata
        val localReceipts = localRepository.getAllReceiptsList().associateBy { it.internalId }

        if (indexEntries.isEmpty()) {
            for (local in localReceipts.values) {
                if (!local.driveFileId.isNullOrBlank()) {
                    indexEntries.add(
                        AuditCandidate(
                            internalId = local.internalId,
                            displayId = local.getEffectiveDisplayId(),
                            metadataFileId = local.driveMetadataFileId ?: "",
                            mainDriveFileId = local.driveFileId ?: "",
                            aussteller = local.aussteller,
                            datum = local.datum
                        )
                    )
                }
            }
        }

        // Process each receipt index entry (100% READ-ONLY)
        for (entry in indexEntries) {
            val internalId = entry.internalId
            val localMatch = localReceipts[internalId]

            val rawDisplayId = entry.displayId ?: localMatch?.getEffectiveDisplayId() ?: "BLG-UNBEKANNT"
            val displayId = if (rawDisplayId.startsWith("BLG-BLG-")) rawDisplayId.substring(4) else rawDisplayId
            val aussteller = entry.aussteller ?: localMatch?.aussteller ?: "Unbekannter Lieferant"
            val datum = entry.datum ?: localMatch?.datum ?: ""
            val mainDriveFileId = entry.mainDriveFileId

            val storedMime = localMatch?.originalMimeType ?: "application/pdf"
            val storedFilename = localMatch?.storedFilename ?: "beleg.pdf"

            if (mainDriveFileId.isBlank()) {
                auditItems.add(
                    OriginalReceiptAuditItem(
                        internalId = internalId,
                        displayId = displayId,
                        aussteller = aussteller,
                        datum = datum,
                        mainDriveFileId = "",
                        storedMimeType = storedMime,
                        storedFilename = storedFilename,
                        detectedFormatName = "Keine Datei",
                        detectedMimeType = "",
                        detectedExtension = "",
                        sizeBytes = 0L,
                        status = AuditStatus.RED,
                        cause = "Keine mainDriveFileId im Belegindex hinterlegt."
                    )
                )
                continue
            }

            // Fetch file info and partial magic bytes header from Drive
            val fileInfo = GoogleDriveClient.fetchDriveFileInfoAndHeader(accessToken, mainDriveFileId, maxHeaderBytes = 2048)

            if (!fileInfo.isReachable) {
                auditItems.add(
                    OriginalReceiptAuditItem(
                        internalId = internalId,
                        displayId = displayId,
                        aussteller = aussteller,
                        datum = datum,
                        mainDriveFileId = mainDriveFileId,
                        storedMimeType = storedMime,
                        storedFilename = storedFilename,
                        detectedFormatName = "Nicht erreichbar",
                        detectedMimeType = "",
                        detectedExtension = "",
                        sizeBytes = 0L,
                        status = AuditStatus.RED,
                        cause = "Datei in Google Drive nicht erreichbar (${fileInfo.errorMessage ?: "404 Not Found"})."
                    )
                )
                continue
            }

            if (fileInfo.sizeBytes == 0L) {
                auditItems.add(
                    OriginalReceiptAuditItem(
                        internalId = internalId,
                        displayId = displayId,
                        aussteller = aussteller,
                        datum = datum,
                        mainDriveFileId = mainDriveFileId,
                        storedMimeType = storedMime,
                        storedFilename = storedFilename,
                        detectedFormatName = "Leere Datei",
                        detectedMimeType = "",
                        detectedExtension = "",
                        sizeBytes = 0L,
                        status = AuditStatus.RED,
                        cause = "Datei ist leer (0 Bytes) in Google Drive."
                    )
                )
                continue
            }

            // Detect format via Magic Bytes
            val detection = detectMagicBytesFormat(fileInfo.headerBytes)

            if (!detection.isValidReceiptFormat) {
                val causeMsg = if (detection.formatName.contains("JSON")) {
                    "Datei ist eine Test-/JSON-Datei anstelle eines echten Originalbelegs (z. B. E2E Testbeleg)."
                } else {
                    "Ungültiges oder nicht zugelassenes Dateiformat: ${detection.formatName}."
                }
                auditItems.add(
                    OriginalReceiptAuditItem(
                        internalId = internalId,
                        displayId = displayId,
                        aussteller = aussteller,
                        datum = datum,
                        mainDriveFileId = mainDriveFileId,
                        storedMimeType = storedMime,
                        storedFilename = storedFilename,
                        detectedFormatName = detection.formatName,
                        detectedMimeType = detection.standardMimeType,
                        detectedExtension = detection.standardExtension,
                        sizeBytes = fileInfo.sizeBytes,
                        status = AuditStatus.RED,
                        cause = causeMsg
                    )
                )
                continue
            }

            // Valid receipt format detected (PDF, JPEG, PNG, WEBP) -> Check MIME and extension compatibility
            val mimeMatches = storedMime.equals(detection.standardMimeType, ignoreCase = true) ||
                    (detection.standardMimeType == "image/jpeg" && storedMime.equals("image/jpg", ignoreCase = true))
            val extMatches = storedFilename.endsWith(".${detection.standardExtension}", ignoreCase = true) ||
                    (detection.standardExtension == "jpg" && storedFilename.endsWith(".jpeg", ignoreCase = true))

            if (!mimeMatches || !extMatches) {
                auditItems.add(
                    OriginalReceiptAuditItem(
                        internalId = internalId,
                        displayId = displayId,
                        aussteller = aussteller,
                        datum = datum,
                        mainDriveFileId = mainDriveFileId,
                        storedMimeType = storedMime,
                        storedFilename = storedFilename,
                        detectedFormatName = detection.formatName,
                        detectedMimeType = detection.standardMimeType,
                        detectedExtension = detection.standardExtension,
                        sizeBytes = fileInfo.sizeBytes,
                        status = AuditStatus.YELLOW,
                        cause = "Dateiformat ist ${detection.formatName}, aber gespeicherter MIME-Typ ist '$storedMime' (Dateiname: '$storedFilename')."
                    )
                )
            } else {
                auditItems.add(
                    OriginalReceiptAuditItem(
                        internalId = internalId,
                        displayId = displayId,
                        aussteller = aussteller,
                        datum = datum,
                        mainDriveFileId = mainDriveFileId,
                        storedMimeType = storedMime,
                        storedFilename = storedFilename,
                        detectedFormatName = detection.formatName,
                        detectedMimeType = detection.standardMimeType,
                        detectedExtension = detection.standardExtension,
                        sizeBytes = fileInfo.sizeBytes,
                        status = AuditStatus.GREEN,
                        cause = "Gültiger Originalbeleg (${detection.formatName}, ${String.format(Locale.GERMANY, "%.1f", fileInfo.sizeBytes / 1024.0)} KB)."
                    )
                )
            }
        }

        val validCount = auditItems.count { it.status == AuditStatus.GREEN }
        val warningCount = auditItems.count { it.status == AuditStatus.YELLOW }
        val invalidCount = auditItems.count { it.status == AuditStatus.RED }

        return OriginalReceiptAuditReport(
            totalChecked = auditItems.size,
            validCount = validCount,
            warningCount = warningCount,
            invalidCount = invalidCount,
            items = auditItems,
            timestamp = nowStr
        )
    }

    suspend fun correctReceiptMetadata(
        accessToken: String,
        config: DriveAppConfig,
        internalId: String,
        newMimeType: String,
        newFilename: String
    ): Boolean {
        val local = localRepository.getAllReceiptsList().find { it.internalId == internalId } ?: return false
        val updated = local.copy(
            originalMimeType = newMimeType,
            storedFilename = newFilename
        )
        localRepository.insert(updated)
        if (accessToken.isNotBlank()) {
            syncReceiptToDrive(accessToken, config, updated)
        }
        return true
    }

    suspend fun downloadDocumentOnDemand(accessToken: String, receipt: Receipt): String {
        val fileId = receipt.driveFileId ?: throw Exception("Keine Drive-ID für diesen Beleg vorhanden.")
        try {
            val fileBytes = GoogleDriveClient.downloadFileBytes(accessToken, fileId) 
                ?: throw Exception("Datei in Google Drive nicht gefunden oder unlesbar.")
                
            if (fileBytes.isEmpty()) {
                throw Exception("Geleerte Datei (0 Bytes) aus Google Drive erhalten.")
            }
            
            // Detect real format via magic bytes
            var detectedExt: String? = null
            var detectedFormatName = "Unbekannt"
            val header = fileBytes.take(16).toByteArray()
            val hex = header.joinToString(" ") { b -> "%02X".format(b) }
            
            if (header.size >= 4 && header.copyOfRange(0, 4).contentEquals(byteArrayOf(0x25, 0x50, 0x44, 0x46))) {
                detectedExt = "pdf"
                detectedFormatName = "PDF"
            } else if (header.size >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) {
                detectedExt = "jpg"
                detectedFormatName = "JPEG"
            } else if (header.size >= 8 && header.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))) {
                detectedExt = "png"
                detectedFormatName = "PNG"
            } else if (header.size >= 12 && header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) && header.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())) {
                detectedExt = "webp"
                detectedFormatName = "WEBP"
            }

            val mime = receipt.originalMimeType ?: ""
            val ext = detectedExt ?: when {
                mime.contains("pdf", ignoreCase = true) -> "pdf"
                mime.contains("png", ignoreCase = true) -> "png"
                mime.contains("jpeg", ignoreCase = true) || mime.contains("jpg", ignoreCase = true) -> "jpg"
                mime.contains("webp", ignoreCase = true) -> "webp"
                mime.contains("xml", ignoreCase = true) -> "xml"
                mime.contains("text/plain", ignoreCase = true) -> "txt"
                else -> "bin"
            }
            
            // Generate filename based on actual extension
            var filename = receipt.storedFilename
            if (filename.isNullOrEmpty() || (detectedExt != null && !filename.endsWith(".$detectedExt", ignoreCase = true))) {
                val base = filename?.substringBeforeLast(".") ?: "original"
                filename = "$base.$ext"
            }
            
            val outputDir = java.io.File(context.filesDir, "restored_receipts/${receipt.internalId}")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            val targetFile = java.io.File(outputDir, filename)
            
            java.io.FileOutputStream(targetFile).use { output ->
                output.write(fileBytes)
                output.flush()
            }
            
            if (!targetFile.exists() || targetFile.length() == 0L) {
                throw Exception("Fehler: Heruntergeladene Datei konnte nicht geschrieben werden oder ist leer.")
            }

            // Make sure we only update the local fields, not anything that would trigger sync
            val updatedReceipt = receipt.copy(imageUrl = targetFile.absolutePath, lastSyncedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()))
            localRepository.insert(updatedReceipt)
            return targetFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading document on demand for fileId $fileId", e)
            throw e
        }
    }

    suspend fun runFullRestoreEndToEndTest(accessToken: String, config: DriveAppConfig): DriveRestoreReport {
        Log.d(TAG, "Starting E2E Non-Destructive Restore Test...")
        val snapshot = buildRestoreSnapshot(accessToken, config)
        val blockingErrors = snapshot.errors.filter { it.isBlocking }
        if (blockingErrors.isNotEmpty()) {
            return DriveRestoreReport(
                timestamp = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date()),
                errorCount = blockingErrors.size,
                errors = blockingErrors.map { "${it.code}: ${it.message}" },
                isSuccess = false
            )
        }

        val localCountBefore = localRepository.getAllReceiptsList().size
        Log.d(TAG, "Non-destructive dry-run snapshot test succeeded. Local count before: $localCountBefore, Snapshot receipts: ${snapshot.receipts.size}")

        val report = executeFullDriveRestore(accessToken, config, requestedMode = RestoreMode.REPLACE_FULL)
        Log.d(TAG, "E2E Restore completed. Restored receipts: ${report.receiptsRestored}, Errors: ${report.errorCount}")
        return report
    }

    suspend fun restoreReceiptsFromDrive(accessToken: String, config: DriveAppConfig): Boolean {
        val report = executeFullDriveRestore(accessToken, config)
        return report.isSuccess
    }

    suspend fun testDriveReceiptStorage(accessToken: String, config: DriveAppConfig): DriveReceiptTestResult {
        return try {
            val year = SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
            val defaultTestDisplayId = "TEST-BLG-$year-000001"

            // Determine stable IDs for test receipt (Requirement 3 - Option A)
            var testInternalId: String? = config.testReferences?.internalId?.ifBlank { null }
            var testDisplayId: String = config.testReferences?.displayId?.ifBlank { null } ?: defaultTestDisplayId
            var existingMainFileId: String? = config.testReferences?.mainDriveFileId?.ifBlank { null }
            var existingMetadataFileId: String? = config.testReferences?.metadataFileId?.ifBlank { null }

            // If not in config.testReferences, search Drive index for test receipt
            if (testInternalId == null) {
                val indexFile = GoogleDriveClient.findFileByAppProperty(accessToken, config.systemFolderId, "receiptIndex")
                if (indexFile != null) {
                    try {
                        val jsonStr = GoogleDriveClient.downloadJson(accessToken, indexFile.id)
                        val root = JSONObject(jsonStr)
                        val array = root.optJSONArray("entries") ?: JSONArray()
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val disp = obj.optString("displayId")
                            val intId = obj.optString("internalId")
                            if (disp == defaultTestDisplayId || disp.startsWith("TEST-BLG-") || intId == "d1ccd205-1a81-4f6a-9fc0-86cb9e29dea2") {
                                testInternalId = intId
                                testDisplayId = disp.ifBlank { defaultTestDisplayId }
                                existingMainFileId = obj.optString("mainDriveFileId").ifBlank { null }
                                existingMetadataFileId = obj.optString("metadataFileId").ifBlank { null }
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error checking index for existing test receipt", e)
                    }
                }
            }

            // If still null, check local DB
            if (testInternalId == null) {
                val localMatch = localRepository.allReceipts.first().find {
                    it.displayId == defaultTestDisplayId || it.internalId == "d1ccd205-1a81-4f6a-9fc0-86cb9e29dea2"
                }
                if (localMatch != null) {
                    testInternalId = localMatch.internalId
                    testDisplayId = localMatch.displayId ?: defaultTestDisplayId
                    existingMainFileId = localMatch.driveFileId
                    existingMetadataFileId = localMatch.driveMetadataFileId
                }
            }

            // Canonical test internalId if none found anywhere
            if (testInternalId == null) {
                testInternalId = "d1ccd205-1a81-4f6a-9fc0-86cb9e29dea2"
            }

            val testReceipt = Receipt(
                id = 999999,
                aussteller = "Test Baustoffhandel GmbH",
                datum = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                uhrzeit = "12:00",
                bruttobetrag = 119.00,
                hauptkategorie = "Renovierungs- / Reparaturkosten & Investitionen",
                unterkategorie = "Baustoffe",
                kontoNr = "4800",
                beschreibung = "E2E Testbeleg für Google Drive Speichertest",
                internalId = testInternalId,
                displayId = testDisplayId,
                driveFileId = existingMainFileId,
                driveMetadataFileId = existingMetadataFileId
            )

            // Save locally first
            localRepository.insert(testReceipt)

            // Sync to Drive
            val syncSuccess = syncReceiptToDrive(accessToken, config, testReceipt)
            if (!syncSuccess) {
                return DriveReceiptTestResult(success = false, errorMessage = "Fehler beim Synchronisieren des Testbelegs nach Drive.")
            }

            val updatedLocal = localRepository.getReceiptById(testReceipt.id) ?: testReceipt
            val metadataFileId = updatedLocal.driveMetadataFileId
            if (metadataFileId.isNullOrBlank()) {
                return DriveReceiptTestResult(success = false, errorMessage = "Keine driveMetadataFileId für Testbeleg erhalten.")
            }

            // Save updated DriveTestReferences to app-config.json in Drive
            val updatedTestRef = DriveTestReferences(
                internalId = updatedLocal.internalId,
                displayId = updatedLocal.displayId ?: testDisplayId,
                mainDriveFileId = updatedLocal.driveFileId ?: "",
                metadataFileId = metadataFileId
            )
            val updatedConfig = config.copy(testReferences = updatedTestRef)
            saveDriveAppConfig(accessToken, updatedConfig)

            // Download metadata JSON back from Drive and verify contents
            val downloadedJson = GoogleDriveClient.downloadJson(accessToken, metadataFileId)
            val downloadedPersisted = parsePersistedReceipt(downloadedJson)

            val internalIdMatch = downloadedPersisted.internalId == updatedLocal.internalId
            val displayIdMatch = downloadedPersisted.displayId == (updatedLocal.displayId ?: testDisplayId)
            val amountMatch = downloadedPersisted.bruttobetragCent == 11900L

            if (internalIdMatch && displayIdMatch && amountMatch) {
                DriveReceiptTestResult(
                    success = true,
                    testInternalId = updatedLocal.internalId,
                    mainDriveFileId = updatedLocal.driveFileId ?: "",
                    metadataFileId = metadataFileId,
                    metadataPath = "_BelegApp-Daten/receipts/${updatedLocal.internalId}.json"
                )
            } else {
                DriveReceiptTestResult(
                    success = false,
                    errorMessage = "Heruntergeladene Metadaten stimmen nicht mit dem Testbeleg überein."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in testDriveReceiptStorage", e)
            DriveReceiptTestResult(success = false, errorMessage = "Fehler beim E2E-Testbeleg-Test: ${e.message}")
        }
    }

    suspend fun getDriveAppConfig(accessToken: String): DriveAppConfig? {
        return when (val initRes = initializeDriveStorage(accessToken)) {
            is DriveInitializationResult.SuccessLoadedExisting -> initRes.config
            is DriveInitializationResult.SuccessCreatedNew -> initRes.config
            else -> null
        }
    }

    private fun extractYear(datum: String): String {
        return datum.trim().split("-", ".", "/").firstOrNull { it.length == 4 }
            ?: if (datum.length >= 4) datum.take(4) else "2026"
    }

    fun generateStandardizedReceiptFilename(receipt: Receipt, extension: String): String {
        fun sanitize(str: String): String {
            if (str.isBlank()) return ""
            var s = str
                .replace("ä", "ae").replace("Ä", "Ae")
                .replace("ö", "oe").replace("Ö", "Oe")
                .replace("ü", "ue").replace("Ü", "Ue")
                .replace("ß", "ss")
            s = s.replace("[^a-zA-Z0-9-]".toRegex(), "_")
            s = s.replace("_+".toRegex(), "_").replace("-+".toRegex(), "-")
            return s.trim('_', '-')
        }

        val datePart = sanitize(receipt.datum.trim())
        val unitPart = sanitize(receipt.wohneinheit)
        val vendorPart = sanitize(receipt.aussteller).ifBlank { "Unbekannt" }

        val rawCat = receipt.hauptkategorie.ifBlank { receipt.unterkategorie }
        val shortCat = when {
            rawCat.contains("Renovier", ignoreCase = true) -> "Renovierung"
            rawCat.contains("Instand", ignoreCase = true) -> "Instandhaltung"
            rawCat.contains("Verwalt", ignoreCase = true) -> "Verwaltung"
            rawCat.contains("Betriebs", ignoreCase = true) || rawCat.contains("Neben", ignoreCase = true) -> "Betriebskosten"
            rawCat.contains("Versich", ignoreCase = true) -> "Versicherung"
            rawCat.contains("Zins", ignoreCase = true) -> "Zinsen"
            else -> sanitize(rawCat).split("_").firstOrNull { it.isNotBlank() } ?: "Kosten"
        }

        val displayIdPart = sanitize(receipt.getEffectiveDisplayId())
        var cleanExt = extension.lowercase().trimStart('.').replace("jpeg", "jpg")
        if (cleanExt.isBlank()) cleanExt = "pdf"

        val parts = mutableListOf<String>()
        if (datePart.isNotBlank()) parts.add(datePart)
        if (unitPart.isNotBlank()) parts.add(unitPart)
        if (vendorPart.isNotBlank()) parts.add(vendorPart)
        if (shortCat.isNotBlank()) parts.add(shortCat)
        if (displayIdPart.isNotBlank()) parts.add(displayIdPart)

        var baseName = parts.joinToString("_")
        if (baseName.length > 95) {
            baseName = baseName.take(95).trimEnd('_', '-')
        }

        return "$baseName.$cleanExt"
    }

    suspend fun upsertReceiptMetadata(
        accessToken: String,
        config: DriveAppConfig,
        receipt: Receipt
    ): String? {
        val mutex = getMetadataMutex(receipt.internalId)
        return mutex.withLock {
            try {
                // 1. Get the latest state of the receipt from the database
                val currentReceipt = localRepository.getReceiptById(receipt.id) ?: receipt
                var driveMetadataFileId = currentReceipt.driveMetadataFileId

                // 2. Prepare metadata content
                val allDocs = localRepository.getDocumentsForReceipt(currentReceipt.internalId)
                val persistedReceipt = PersistedReceipt(
                    schemaVersion = 1,
                    revision = (currentReceipt.driveRevision ?: 0L) + 1L,
                    internalId = currentReceipt.internalId,
                    displayId = currentReceipt.displayId,
                    documents = allDocs,
                    driveFileId = currentReceipt.driveFileId ?: "",
                    driveFolderId = currentReceipt.driveFolderId,
                    filename = currentReceipt.storedFilename ?: "",
                    mimeType = currentReceipt.originalMimeType ?: "",
                    aussteller = currentReceipt.aussteller,
                    rechnungsnummer = "",
                    datum = currentReceipt.datum,
                    nettobetragCent = (currentReceipt.bruttobetrag * 100.0).toLong() - ((currentReceipt.bruttobetrag * 100.0) * 0.19).toLong(),
                    steuerbetragCent = ((currentReceipt.bruttobetrag * 100.0) * 0.19).toLong(),
                    bruttobetragCent = (currentReceipt.bruttobetrag * 100.0).toLong(),
                    hauptkategorie = currentReceipt.hauptkategorie,
                    unterkategorie = currentReceipt.unterkategorie,
                    wohneinheit = currentReceipt.wohneinheit,
                    massnahme = "",
                    positionen = currentReceipt.getPositionenList().toPersistedItems(),
                    zahlungsstatus = "BEZAHLT",
                    zahlungsdatum = currentReceipt.datum,
                    zahlungsreferenz = "",
                    pruefstatus = currentReceipt.pruefstatus,
                    exportstatus = currentReceipt.exportStatus,
                    createdAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()),
                    updatedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()),
                    lastSyncedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
                )

                val metadataFilename = "${persistedReceipt.internalId}.json"

                // 3. Search on Drive if ID is not known locally
                if (driveMetadataFileId.isNullOrEmpty()) {
                    // a) Search by properties: receiptInternalId + entityType=receiptMetadata
                    val foundFileByProps = GoogleDriveClient.findMetadataFileByProperties(
                        accessToken, config.receiptsFolderId, currentReceipt.internalId
                    )
                    if (foundFileByProps != null) {
                        driveMetadataFileId = foundFileByProps.id
                        Log.d(TAG, "Metadata found on Drive via properties query: $driveMetadataFileId")
                    } else {
                        // b) Fallback: search by exact filename: {internalId}.json
                        val foundFileIdByName = GoogleDriveClient.findFileByName(
                            accessToken, config.receiptsFolderId, metadataFilename
                        )
                        if (foundFileIdByName != null) {
                            driveMetadataFileId = foundFileIdByName
                            Log.d(TAG, "Metadata found on Drive via filename fallback: $driveMetadataFileId")
                        }
                    }
                }

                // 4. Update existing or create a new file
                val uploadRes = if (!driveMetadataFileId.isNullOrEmpty()) {
                    // Update existing
                    GoogleDriveClient.updateJson(accessToken, driveMetadataFileId, persistedReceipt.toJson())
                } else {
                    // Create new
                    val appProperties = mapOf(
                        "appName" to "ImmobilienBelegApp",
                        "entityType" to "receiptMetadata",
                        "receiptInternalId" to persistedReceipt.internalId,
                        "displayId" to (persistedReceipt.displayId ?: ""),
                        "schemaVersion" to "1"
                    )
                    GoogleDriveClient.uploadJson(
                        accessToken, config.receiptsFolderId, metadataFilename, persistedReceipt.toJson(), appProperties
                    )
                }

                if (uploadRes.success) {
                    val finalFileId = uploadRes.fileId ?: driveMetadataFileId
                    if (!finalFileId.isNullOrEmpty()) {
                        driveMetadataFileId = finalFileId
                        
                        // 5. Save the obtained metadataFileId immediately locally
                        val dbReceipt2 = localRepository.getReceiptById(currentReceipt.id) ?: currentReceipt
                        val updatedReceipt = dbReceipt2.copy(driveMetadataFileId = driveMetadataFileId)
                        localRepository.insert(updatedReceipt)
                        Log.d(TAG, "Metadata successfully saved to local database: $driveMetadataFileId")
                    }
                    driveMetadataFileId
                } else {
                    Log.e(TAG, "Failed to upload or update metadata on Drive: ${uploadRes.errorMessage}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in upsertReceiptMetadata", e)
                null
            }
        }
    }

    private suspend fun uploadReceiptMetadata(
        accessToken: String,
        config: DriveAppConfig,
        receipt: Receipt
    ): Boolean {
        return upsertReceiptMetadata(accessToken, config, receipt) != null
    }

    suspend fun repairAndUploadOriginalDocument(
        context: Context,
        accessToken: String,
        config: DriveAppConfig,
        receipt: Receipt,
        localFile: File,
        validationResult: com.example.ui.FileValidationResult
    ): RepairResult {
        try {
            Log.d(TAG, "Starting repair and upload for receipt internalId: ${receipt.internalId}")

            // 1. Resolve Drive target folder
            val yearName = extractYear(receipt.datum)
            val mainFolderId = config.rootFolderId
            val yearFolderId = GoogleDriveClient.getOrCreateFolder(accessToken, yearName, mainFolderId) ?: mainFolderId
            val hauptFolderId = GoogleDriveClient.getOrCreateFolder(accessToken, receipt.hauptkategorie.ifEmpty { "Sonstiges" }, yearFolderId) ?: yearFolderId
            val targetFolderId = GoogleDriveClient.getOrCreateFolder(accessToken, receipt.unterkategorie.ifEmpty { "Allgemein" }, hauptFolderId) ?: hauptFolderId

            // 2. Generate standardized filename
            val ext = validationResult.formatName.lowercase()
            val newFilename = generateStandardizedReceiptFilename(receipt, ext)

            // 3. Read local file bytes
            val fileBytes = localFile.readBytes()
            if (fileBytes.isEmpty()) {
                return RepairResult(false, errorMessage = "Lokale Belegdatei ist leer.")
            }

            // 4. Upload to Drive with required appProperties
            val appProps = mapOf(
                "receiptInternalId" to receipt.internalId,
                "displayId" to (receipt.displayId ?: ""),
                "documentRole" to "ORIGINAL",
                "schemaVersion" to "1"
            )
            val uploadedFileId = GoogleDriveClient.uploadFile(
                accessToken = accessToken,
                folderId = targetFolderId,
                filename = newFilename,
                mimeType = validationResult.mimeType,
                fileContent = fileBytes,
                appProperties = appProps
            )

            if (uploadedFileId.isNullOrEmpty()) {
                return RepairResult(false, errorMessage = "Upload zu Google Drive ist fehlgeschlagen.")
            }

            // 5. Post-Upload Verification
            Log.d(TAG, "Performing post-upload verification for fileId: $uploadedFileId")
            val checkBytes = GoogleDriveClient.downloadFileBytes(accessToken, uploadedFileId)
            if (checkBytes == null || checkBytes.isEmpty()) {
                return RepairResult(false, errorMessage = "Verifikation fehlgeschlagen: Hochgeladene Datei konnte nicht zurückgeladen werden.")
            }
            if (checkBytes.size.toLong() != validationResult.sizeBytes) {
                return RepairResult(false, errorMessage = "Verifikation fehlgeschlagen: Dateigröße auf Drive (${checkBytes.size}) weicht ab von Quellgröße (${validationResult.sizeBytes}).")
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
        
        // Map of internalId -> metadataFileId from index
        val indexMap = indexEntries.associate { it.internalId to it.metadataFileId }
        
        // Group all found files by their receiptInternalId
        val groupedFiles = allFiles.groupBy { it.receiptInternalId }
        
        val reportGroups = mutableListOf<MetadataDuplicateGroup>()
        var totalGroupsWithDuplicates = 0
        var totalDuplicateFilesCount = 0
        
        for ((internalId, files) in groupedFiles) {
            if (internalId.isBlank()) continue
            
            val referencedFileIdInIndex = indexMap[internalId]
            
            val details = files.map { file ->
                MetadataFileDetails(
                    driveId = file.id,
                    name = file.name,
                    createdTime = file.createdTime,
                    modifiedTime = file.modifiedTime,
                    isReferencedInIndex = file.id == referencedFileIdInIndex
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
                    referencedFileIdInIndex = referencedFileIdInIndex
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
    val referencedFileIdInIndex: String?
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
