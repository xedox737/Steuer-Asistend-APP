package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.ExtractedReceipt
import com.example.api.GeminiClient
import com.example.api.GoogleDriveClient
import com.example.api.AiProviderSettings
import com.example.api.AiProviderState
import com.example.api.OpenAiAnalysisException
import com.example.api.OpenAiClient
import com.example.api.ReceiptAnalysisProvider
import com.example.data.AppDatabase
import com.example.data.Receipt
import com.example.data.ReceiptRepository
import com.example.data.PropertyMetadata
import com.example.util.PdfExporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.example.data.FirestoreService

enum class AppScreen {
    DASHBOARD,
    RECEIPTS_LIST,
    ADD_RECEIPT,
    LOGBOOK,
    LEDGER,
    RENT_OVERVIEW,
    TAX_CALCULATOR
}

sealed interface ScanUiState {
    object Idle : ScanUiState
    object Loading : ScanUiState
    data class Success(val receipt: ExtractedReceipt, val localImagePaths: String = "") : ScanUiState
    data class Error(val message: String) : ScanUiState
}

data class MockReceiptTemplate(
    val title: String,
    val text: String,
    val filename: String
)

data class WohneinheitStatus(
    val name: String,
    val label: String,
    val status: String, // "Vermietet", "Leerstand", "Sanierung"
    val mieter: String,
    val kaltmiete: Double,
    val wohnflaeche: Double,
    val mietvertragsstart: String = ""
)

data class LearnedVendorRule(
    val aussteller: String,
    val hauptkategorie: String,
    val unterkategorie: String,
    val kontoNr: String,
    val wohneinheit: String = "",
    val count: Int = 1
)

class ReceiptViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = ReceiptRepository(
        database.receiptDao(),
        database.propertyDao(),
        database.receiptEntityDao(),
        database.belegDao(),
        database.exportAuditDao(),
        database.receiptDocumentDao()
    )
    val drivePersistenceRepository = com.example.data.DrivePersistenceRepository(application, repository)

    private val sharedPrefs = application.getSharedPreferences("google_drive_prefs", Context.MODE_PRIVATE)
    private val _aiProviderState = MutableStateFlow(AiProviderSettings.loadState(application))
    val aiProviderState: StateFlow<AiProviderState> = _aiProviderState.asStateFlow()

    fun saveAiProviderSettings(
        provider: ReceiptAnalysisProvider,
        model: String,
        newOpenAiKey: String,
        newGeminiKey: String
    ): String? {
        return try {
            if (newOpenAiKey.isNotBlank()) {
                AiProviderSettings.storeOpenAiKey(
                    getApplication(),
                    newOpenAiKey.toCharArray()
                )
            }
            if (newGeminiKey.isNotBlank()) {
                AiProviderSettings.storeGeminiKey(
                    getApplication(),
                    newGeminiKey.toCharArray()
                )
            }
            _aiProviderState.value = AiProviderSettings.saveSelection(
                context = getApplication(),
                provider = provider,
                model = model
            )
            null
        } catch (e: IllegalArgumentException) {
            e.message ?: "KI-Einstellungen konnten nicht gespeichert werden."
        } catch (_: Exception) {
            "Der API-Schlüssel konnte auf diesem Gerät nicht sicher gespeichert werden."
        }
    }

    fun deleteOpenAiKey() {
        _aiProviderState.value = AiProviderSettings.clearOpenAiKey(getApplication())
    }

    fun deleteGeminiKey() {
        _aiProviderState.value = AiProviderSettings.clearGeminiKey(getApplication())
    }
    private val learnedRulesPrefs = application.getSharedPreferences("ki_learned_rules_prefs", Context.MODE_PRIVATE)

    // KI Adaptive Learning Rules State
    private val _learnedRules = MutableStateFlow<List<LearnedVendorRule>>(emptyList())
    val learnedRules: StateFlow<List<LearnedVendorRule>> = _learnedRules.asStateFlow()

    val learnedRulesCount: StateFlow<Int> = _learnedRules.map { it.size }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    // --- 5 NEW GEMINI KI FEATURES STATE ---
    private val _taxPlausibilityReport = MutableStateFlow<com.example.api.TaxPlausibilityReport?>(null)
    val taxPlausibilityReport = _taxPlausibilityReport.asStateFlow()
    private val _isAnalyzingTaxPlausibility = MutableStateFlow(false)
    val isAnalyzingTaxPlausibility = _isAnalyzingTaxPlausibility.asStateFlow()

    private val _tenantUtilityStatement = MutableStateFlow<com.example.api.TenantUtilityStatement?>(null)
    val tenantUtilityStatement = _tenantUtilityStatement.asStateFlow()
    private val _isGeneratingUtilityStatement = MutableStateFlow(false)
    val isGeneratingUtilityStatement = _isGeneratingUtilityStatement.asStateFlow()

    private val _rentYieldReport = MutableStateFlow<com.example.api.RentYieldOptimizationReport?>(null)
    val rentYieldReport = _rentYieldReport.asStateFlow()
    private val _isOptimizingRentYield = MutableStateFlow(false)
    val isOptimizingRentYield = _isOptimizingRentYield.asStateFlow()

    private val _damageAssessment = MutableStateFlow<com.example.api.DamageAssessmentResult?>(null)
    val damageAssessment = _damageAssessment.asStateFlow()
    private val _isAssessingDamage = MutableStateFlow(false)
    val isAssessingDamage = _isAssessingDamage.asStateFlow()

    private val _contractAnalysis = MutableStateFlow<com.example.api.ContractAnalysisResult?>(null)
    val contractAnalysis = _contractAnalysis.asStateFlow()
    private val _isAnalyzingContract = MutableStateFlow(false)
    val isAnalyzingContract = _isAnalyzingContract.asStateFlow()

    private val _bankStatementResult = MutableStateFlow<com.example.api.BankStatementReconciliationResult?>(null)
    val bankStatementResult = _bankStatementResult.asStateFlow()
    private val _isMatchingBankStatement = MutableStateFlow(false)
    val isMatchingBankStatement = _isMatchingBankStatement.asStateFlow()
    private val _bankStatementResetVersion = MutableStateFlow(0)
    val bankStatementResetVersion = _bankStatementResetVersion.asStateFlow()

    // Google Drive Sync State
    private val _googleAccountEmail = MutableStateFlow<String?>(null)
    val googleAccountEmail: StateFlow<String?> = _googleAccountEmail.asStateFlow()

    private val _isDriveConnected = MutableStateFlow(false)
    val isDriveConnected: StateFlow<Boolean> = _isDriveConnected.asStateFlow()

    private val _isDriveSyncing = MutableStateFlow(false)
    val isDriveSyncing: StateFlow<Boolean> = _isDriveSyncing.asStateFlow()

    private val _driveSyncStatus = MutableStateFlow<String?>(null)
    val driveSyncStatus: StateFlow<String?> = _driveSyncStatus.asStateFlow()

    private val _autoDriveBackup = MutableStateFlow(true)
    val autoDriveBackup: StateFlow<Boolean> = _autoDriveBackup.asStateFlow()

    private val _driveSystemFolderStatus = MutableStateFlow("Nicht eingerichtet")
    val driveSystemFolderStatus: StateFlow<String> = _driveSystemFolderStatus.asStateFlow()

    private val _lastStammdatenBackupTime = MutableStateFlow(sharedPrefs.getString("last_stammdaten_backup_time", "Nie") ?: "Nie")
    val lastStammdatenBackupTime: StateFlow<String> = _lastStammdatenBackupTime.asStateFlow()

    private val _driveSyncError = MutableStateFlow<String?>(sharedPrefs.getString("drive_sync_error", null))
    val driveSyncError: StateFlow<String?> = _driveSyncError.asStateFlow()

    private val _driveTestState = MutableStateFlow<DriveTestState?>(null)
    val driveTestState: StateFlow<DriveTestState?> = _driveTestState.asStateFlow()

    private val _duplicateCleanupState =
        MutableStateFlow<DuplicateCleanupUiState>(DuplicateCleanupUiState.Idle)
    val duplicateCleanupState: StateFlow<DuplicateCleanupUiState> =
        _duplicateCleanupState.asStateFlow()
    private var duplicateCleanupController: DuplicateCleanupController? = null
    private var duplicateCleanupFeature: com.example.data.DuplicateCleanupFeature? = null

    // Google Drive Restore State
    private val _restorePreview = MutableStateFlow<com.example.data.DriveRestorePreviewResult?>(null)
    val restorePreview: StateFlow<com.example.data.DriveRestorePreviewResult?> = _restorePreview.asStateFlow()

    private val _restoreReport = MutableStateFlow<com.example.data.DriveRestoreReport?>(null)
    val restoreReport: StateFlow<com.example.data.DriveRestoreReport?> = _restoreReport.asStateFlow()

    private val _isRestoreRequired = MutableStateFlow(false)
    val isRestoreRequired: StateFlow<Boolean> = _isRestoreRequired.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    // Wohneinheiten Status State
    private val _wohneinheitenStatus = MutableStateFlow<List<WohneinheitStatus>>(emptyList())
    val wohneinheitenStatus: StateFlow<List<WohneinheitStatus>> = _wohneinheitenStatus.asStateFlow()

    // Firestore Cloud Sync State
    private val _isCloudActive = MutableStateFlow(false)
    val isCloudActive: StateFlow<Boolean> = _isCloudActive.asStateFlow()

    // Firebase Auth State
    private val _currentUser = MutableStateFlow<com.google.firebase.auth.FirebaseUser?>(null)
    val currentUser: StateFlow<com.google.firebase.auth.FirebaseUser?> = _currentUser.asStateFlow()

    // All receipts
    val receipts: StateFlow<List<Receipt>> = repository.allReceipts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val deletedReceipts: StateFlow<List<Receipt>> = repository.deletedReceipts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val receiptEntities: StateFlow<List<com.example.data.ReceiptEntity>> = repository.allReceiptEntities
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val propertyMetadata: StateFlow<PropertyMetadata?> = repository.propertyMetadata
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    init {
        // Initialize Firestore
        FirestoreService.initialize(application)
        _isCloudActive.value = FirestoreService.isCloudActive()
        _currentUser.value = FirestoreService.getCurrentUser()

        // Automatically connect to Google Drive on app launch
        val isExplicitlyDisconnected = sharedPrefs.getBoolean("user_disconnected", false)
        val savedEmail = sharedPrefs.getString("connected_email", null) ?: "sergej.alc28@gmail.com"
        
        if (!isExplicitlyDisconnected) {
            connectDrive(savedEmail)
        } else {
            _googleAccountEmail.value = savedEmail
            _isDriveConnected.value = false
        }
        
        _autoDriveBackup.value = sharedPrefs.getBoolean("auto_backup", true)
        
        // Initialize residential units status & collect updates from property metadata (Stammdaten)
        _wohneinheitenStatus.value = getWohneinheitenFromPrefs()
        viewModelScope.launch {
            propertyMetadata.collect { meta ->
                if (meta != null && meta.wohneinheiten.isNotEmpty()) {
                    _wohneinheitenStatus.value = getWohneinheitenFromPrefs(meta.wohneinheiten)
                }
            }
        }

        // Sync with Firestore Cloud
        syncWithCloud()

        // Automatically trigger Google Drive sync on app call/startup
        if (_isDriveConnected.value && _autoDriveBackup.value) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(1200)
                syncAllToDrive()
            }
        }

        // Load KI Learned Vendor Rules
        loadLearnedRules()
    }

    // --- KI ADAPTIVE LEARNING SYSTEM ---
    fun loadLearnedRules() {
        viewModelScope.launch(Dispatchers.IO) {
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
            _learnedRules.value = rulesList.sortedByDescending { it.count }
        }
    }

    fun learnVendorRule(
        aussteller: String,
        hauptkategorie: String,
        unterkategorie: String,
        kontoNr: String,
        wohneinheit: String = ""
    ) {
        val cleanAussteller = aussteller.trim()
        if (cleanAussteller.isEmpty() || cleanAussteller.lowercase().contains("unbekannt")) return

        viewModelScope.launch(Dispatchers.IO) {
            val normalizedKey = "rule_" + cleanAussteller.lowercase().replace(Regex("[^a-z0-9]"), "_")
            val existingString = learnedRulesPrefs.getString(normalizedKey, null)
            var currentCount = 1
            if (existingString != null) {
                val parts = existingString.split("|||")
                if (parts.size >= 6) {
                    currentCount = (parts[5].toIntOrNull() ?: 1) + 1
                }
            }

            val serialized = "$cleanAussteller|||$hauptkategorie|||$unterkategorie|||$kontoNr|||$wohneinheit|||$currentCount"
            learnedRulesPrefs.edit().putString(normalizedKey, serialized).apply()

            loadLearnedRules()
        }
    }

    fun deleteLearnedRule(aussteller: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalizedKey = "rule_" + aussteller.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
            learnedRulesPrefs.edit().remove(normalizedKey).apply()
            loadLearnedRules()
        }
    }

    fun clearAllLearnedRules() {
        viewModelScope.launch(Dispatchers.IO) {
            val editor = learnedRulesPrefs.edit()
            learnedRulesPrefs.all.keys.forEach { k ->
                if (k.startsWith("rule_")) editor.remove(k)
            }
            editor.apply()
            loadLearnedRules()
        }
    }

    fun getUserLearnedRulesPromptContext(): String {
        val explicitRules = _learnedRules.value
        val sb = StringBuilder()

        if (explicitRules.isNotEmpty()) {
            explicitRules.take(20).forEach { r ->
                sb.append("- Händler \"${r.aussteller}\": Hauptkategorie \"${r.hauptkategorie}\", Unterkategorie \"${r.unterkategorie}\", Konto \"${r.kontoNr}\"")
                if (r.wohneinheit.isNotEmpty() && r.wohneinheit != "Gesamtobjekt / Allgemein") {
                    sb.append(", Wohneinheit \"${r.wohneinheit}\"")
                }
                sb.append("\n")
            }
        }

        // Aggregate past receipt patterns from database as additional memory
        val existingReceipts = receipts.value
        if (existingReceipts.isNotEmpty()) {
            val vendorGroups = existingReceipts.filter { it.aussteller.isNotBlank() }
                .groupBy { it.aussteller.trim() }

            vendorGroups.forEach { (vendor, list) ->
                val alreadyExplicit = explicitRules.any { it.aussteller.equals(vendor, ignoreCase = true) }
                if (!alreadyExplicit && list.isNotEmpty()) {
                    val topReceipt = list.maxByOrNull { it.id }!!
                    sb.append("- Händler \"${vendor}\": Hauptkategorie \"${topReceipt.hauptkategorie}\", Unterkategorie \"${topReceipt.unterkategorie}\", Konto \"${topReceipt.kontoNr}\"\n")
                }
            }
        }

        return sb.toString().trim()
    }

    fun getWohneinheitenFromPrefs(metaUnitsStr: String? = null): List<WohneinheitStatus> {
        val unitPrefs = getApplication<Application>().getSharedPreferences("wohneinheiten_prefs", Context.MODE_PRIVATE)
        val rawUnitsStr = metaUnitsStr
            ?: propertyMetadata.value?.wohneinheiten
            ?: "WE 1, WE 2, WE 3, WE 4, WE 5, WE 6, WE 7"

        val parsedUnitNames = rawUnitsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val unitNames = if (parsedUnitNames.isNotEmpty()) parsedUnitNames else listOf("WE 1", "WE 2", "WE 3", "WE 4", "WE 5", "WE 6", "WE 7")

        val defaultPresetMap = mapOf(
            "WE 1" to WohneinheitStatus("WE 1", "WE 1 (EG links)", "Vermietet", "Hans Peter", 480.0, 60.0),
            "WE 2" to WohneinheitStatus("WE 2", "WE 2 (EG rechts)", "Vermietet", "Erika Mustermann", 440.0, 55.0),
            "WE 3" to WohneinheitStatus("WE 3", "WE 3 (1. OG links)", "Vermietet", "Familie Schmidt", 520.0, 65.0),
            "WE 4" to WohneinheitStatus("WE 4", "WE 4 (1. OG rechts)", "Sanierung", "Unbewohnt (Eigenleistung)", 440.0, 55.0),
            "WE 5" to WohneinheitStatus("WE 5", "WE 5 (2. OG links)", "Vermietet", "Klaus & Sabine", 520.0, 65.0),
            "WE 6" to WohneinheitStatus("WE 6", "WE 6 (2. OG rechts)", "Leerstand", "Keiner", 440.0, 55.0),
            "WE 7" to WohneinheitStatus("WE 7", "WE 7 (DG Studio)", "Vermietet", "Dr. Julia Wagner", 580.0, 65.0)
        )

        return unitNames.mapIndexed { index, name ->
            val preset = defaultPresetMap[name]
            val exists = unitPrefs.contains("unit_status_$name")

            if (exists) {
                WohneinheitStatus(
                    name = name,
                    label = unitPrefs.getString("unit_label_$name", name) ?: name,
                    status = unitPrefs.getString("unit_status_$name", "Vermietet") ?: "Vermietet",
                    mieter = unitPrefs.getString("unit_mieter_$name", "") ?: "",
                    kaltmiete = unitPrefs.getFloat("unit_rent_$name", 0f).toDouble(),
                    wohnflaeche = unitPrefs.getFloat("unit_area_$name", 0f).toDouble(),
                    mietvertragsstart = unitPrefs.getString("unit_start_$name", "") ?: ""
                )
            } else if (preset != null) {
                val editor = unitPrefs.edit()
                editor.putString("unit_status_${preset.name}", preset.status)
                editor.putString("unit_label_${preset.name}", preset.label)
                editor.putString("unit_mieter_${preset.name}", preset.mieter)
                editor.putFloat("unit_rent_${preset.name}", preset.kaltmiete.toFloat())
                editor.putFloat("unit_area_${preset.…25416 tokens truncated… fun analyzeDamagePhoto(bitmap: android.graphics.Bitmap, userDescription: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            _isAssessingDamage.value = true
            val result = com.example.api.GeminiClient.assessDamagePhoto(
                bitmap = bitmap,
                userDescription = userDescription
            )
            _damageAssessment.value = result
            _isAssessingDamage.value = false
        }
    }

    fun analyzeContractDocument(bitmap: android.graphics.Bitmap? = null, textContent: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAnalyzingContract.value = true
            val result = com.example.api.GeminiClient.analyzeContractDocument(
                bitmap = bitmap,
                textContent = textContent
            )
            _contractAnalysis.value = result
            _isAnalyzingContract.value = false
        }
    }

    fun runBankStatementMatching(rawStatementText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isMatchingBankStatement.value = true
            val currentList = receipts.value
            val tenantsText = _wohneinheitenStatus.value.joinToString("\n") { u ->
                "- ${u.name}: ${u.mieter}, Kaltmiete: ${u.kaltmiete}€, Status: ${u.status}"
            }
            val result = com.example.api.GeminiClient.matchBankStatement(
                rawStatementText = rawStatementText,
                receipts = currentList,
                tenantsInfo = tenantsText
            )
            _bankStatementResult.value = result
            _isMatchingBankStatement.value = false
        }
    }

    suspend fun estimateLogbookRouteDistance(
        startAddress: String,
        viaAddress: String,
        endAddress: String,
        routeType: String
    ): Double? {
        val key = AiProviderSettings.getGeminiKey(getApplication())
        return com.example.api.GeminiClient.estimateRouteDistance(
            startAddress = startAddress,
            viaAddress = viaAddress,
            endAddress = endAddress,
            routeType = routeType,
            apiKeyOverride = key
        )
    }

    // --- Document Repair & Manual Upload ---
    private val _repairUiState = MutableStateFlow<RepairUiState>(RepairUiState.Idle)
    val repairUiState: StateFlow<RepairUiState> = _repairUiState.asStateFlow()

    fun resetRepairUiState() {
        _repairUiState.value = RepairUiState.Idle
    }

    // --- Original Receipts Audit State & Methods ---
    private val _originalReceiptAuditReport = MutableStateFlow<com.example.data.OriginalReceiptAuditReport?>(null)
    val originalReceiptAuditReport: StateFlow<com.example.data.OriginalReceiptAuditReport?> = _originalReceiptAuditReport.asStateFlow()

    private val _isAuditingOriginalReceipts = MutableStateFlow(false)
    val isAuditingOriginalReceipts: StateFlow<Boolean> = _isAuditingOriginalReceipts.asStateFlow()

    private val _originalReceiptAuditError = MutableStateFlow<String?>(null)
    val originalReceiptAuditError: StateFlow<String?> = _originalReceiptAuditError.asStateFlow()

    fun runOriginalReceiptAudit() {
        viewModelScope.launch(Dispatchers.IO) {
            _isAuditingOriginalReceipts.value = true
            _originalReceiptAuditError.value = null
            try {
                val email = _googleAccountEmail.value
                if (email.isNullOrEmpty()) {
                    _originalReceiptAuditError.value = "Bitte melde dich zuerst bei Google Drive an."
                    _isAuditingOriginalReceipts.value = false
                    return@launch
                }
                val token = getValidToken(email)
                val config = drivePersistenceRepository.getDriveAppConfig(token)
                if (config == null) {
                    _originalReceiptAuditError.value = "Google Drive-Konfiguration konnte nicht geladen werden."
                    _isAuditingOriginalReceipts.value = false
                    return@launch
                }
                val report = drivePersistenceRepository.auditOriginalReceipts(token, config)
                _originalReceiptAuditReport.value = report
            } catch (e: Exception) {
                Log.e("ReceiptViewModel", "Error in runOriginalReceiptAudit", e)
                _originalReceiptAuditError.value = "Fehler bei der Bestandsprüfung: ${e.message}"
            } finally {
                _isAuditingOriginalReceipts.value = false
            }
        }
    }

    fun dismissOriginalReceiptAuditReport() {
        _originalReceiptAuditReport.value = null
        _originalReceiptAuditError.value = null
    }

    // --- Metadata Duplicate Report State & Methods ---
    private val _metadataDuplicateReport = MutableStateFlow<com.example.data.MetadataDuplicateReport?>(null)
    val metadataDuplicateReport: StateFlow<com.example.data.MetadataDuplicateReport?> = _metadataDuplicateReport.asStateFlow()

    private val _isCheckingMetadataDuplicates = MutableStateFlow(false)
    val isCheckingMetadataDuplicates: StateFlow<Boolean> = _isCheckingMetadataDuplicates.asStateFlow()

    private val _metadataDuplicateError = MutableStateFlow<String?>(null)
    val metadataDuplicateError: StateFlow<String?> = _metadataDuplicateError.asStateFlow()

    fun runMetadataDuplicateReport() {
        viewModelScope.launch(Dispatchers.IO) {
            _isCheckingMetadataDuplicates.value = true
            _metadataDuplicateError.value = null
            try {
                val email = _googleAccountEmail.value
                if (email.isNullOrEmpty()) {
                    _metadataDuplicateError.value = "Bitte melde dich zuerst bei Google Drive an."
                    _isCheckingMetadataDuplicates.value = false
                    return@launch
                }
                val token = getValidToken(email)
                val config = drivePersistenceRepository.getDriveAppConfig(token)
                if (config == null) {
                    _metadataDuplicateError.value = "Google Drive-Konfiguration konnte nicht geladen werden."
                    _isCheckingMetadataDuplicates.value = false
                    return@launch
                }
                val report = drivePersistenceRepository.generateMetadataDuplicateReport(token, config)
                _metadataDuplicateReport.value = report
            } catch (e: Exception) {
                Log.e("ReceiptViewModel", "Error in runMetadataDuplicateReport", e)
                _metadataDuplicateError.value = "Fehler bei der Dublettenprüfung: ${e.message}"
            } finally {
                _isCheckingMetadataDuplicates.value = false
            }
        }
    }

    fun dismissMetadataDuplicateReport() {
        _metadataDuplicateReport.value = null
        _metadataDuplicateError.value = null
    }

    private val _metadataCleanupPreview =
        MutableStateFlow<com.example.data.MetadataDuplicateCleanupPlan?>(null)
    val metadataCleanupPreview:
        StateFlow<com.example.data.MetadataDuplicateCleanupPlan?> = _metadataCleanupPreview.asStateFlow()

    private val _metadataCleanupResult =
        MutableStateFlow<com.example.data.MetadataDuplicateCleanupResult?>(null)
    val metadataCleanupResult:
        StateFlow<com.example.data.MetadataDuplicateCleanupResult?> = _metadataCleanupResult.asStateFlow()

    private val _isCleaningMetadataDuplicates = MutableStateFlow(false)
    val isCleaningMetadataDuplicates: StateFlow<Boolean> =
        _isCleaningMetadataDuplicates.asStateFlow()

    fun prepareMetadataDuplicateCleanup(group: com.example.data.MetadataDuplicateGroup) {
        try {
            _metadataCleanupPreview.value =
                com.example.data.MetadataDuplicateCleanupPlanner.plan(group)
            _metadataDuplicateError.value = null
        } catch (e: IllegalArgumentException) {
            _metadataDuplicateError.value = e.message
        }
    }

    fun cancelMetadataDuplicateCleanup() {
        _metadataCleanupPreview.value = null
    }

    fun confirmMetadataDuplicateCleanup() {
        val plan = _metadataCleanupPreview.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isCleaningMetadataDuplicates.value = true
            _metadataDuplicateError.value = null
            try {
                val email = _googleAccountEmail.value
                    ?: throw IllegalStateException("Bitte melde dich zuerst bei Google Drive an.")
                val token = getValidToken(email)
                val executor = com.example.data.MetadataDuplicateCleanupExecutor(
                    deleter = com.example.data.MetadataDuplicateFileDeleter { fileId ->
                        GoogleDriveClient.deleteFile(token, fileId)
                    },
                    audit = { result -> persistMetadataCleanupAudit(result) }
                )
                val result = executor.execute(plan, explicitlyConfirmed = true)
                _metadataCleanupResult.value = result
                _metadataCleanupPreview.value = null
                if (result.completed) {
                    val config = drivePersistenceRepository.getDriveAppConfig(token)
                    if (config != null) {
                        _metadataDuplicateReport.value =
                            drivePersistenceRepository.generateMetadataDuplicateReport(token, config)
                    }
                }
            } catch (e: Exception) {
                Log.e("ReceiptViewModel", "Metadata duplicate cleanup failed", e)
                _metadataDuplicateError.value =
                    "Metadaten-Dubletten konnten nicht sicher bereinigt werden: ${e.message}"
            } finally {
                _isCleaningMetadataDuplicates.value = false
            }
        }
    }

    fun dismissMetadataCleanupResult() {
        _metadataCleanupResult.value = null
    }

    private fun persistMetadataCleanupAudit(
        result: com.example.data.MetadataDuplicateCleanupResult
    ) {
        val prefs = getApplication<Application>().getSharedPreferences(
            "metadata_duplicate_cleanup_audit",
            Context.MODE_PRIVATE
        )
        val entry = org.json.JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("internalId", result.internalId)
            put("activeMetadataFileId", result.activeMetadataFileId)
            put("deletedMetadataFileIds", org.json.JSONArray(result.deletedMetadataFileIds))
            put("failures", org.json.JSONArray(result.failures))
            put("completed", result.completed)
        }
        val existing = prefs.getString("entries", "[]") ?: "[]"
        val entries = try {
            org.json.JSONArray(existing)
        } catch (_: Exception) {
            org.json.JSONArray()
        }
        entries.put(entry)
        prefs.edit().putString("entries", entries.toString()).apply()
    }

    fun correctReceiptMetadata(internalId: String, newMimeType: String, newFilename: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val email = _googleAccountEmail.value ?: ""
                val token = if (email.isNotEmpty()) getValidToken(email) else ""
                val config = if (token.isNotEmpty()) drivePersistenceRepository.getDriveAppConfig(token) else null

                val success = if (config != null) {
                    drivePersistenceRepository.correctReceiptMetadata(token, config, internalId, newMimeType, newFilename)
                } else {
                    val local = repository.getAllReceiptsList().find { it.internalId == internalId }
                    if (local != null) {
                        repository.insert(local.copy(originalMimeType = newMimeType, storedFilename = newFilename))
                        true
                    } else false
                }

                if (success && token.isNotEmpty() && config != null) {
                    val updatedReport = drivePersistenceRepository.auditOriginalReceipts(token, config)
                    _originalReceiptAuditReport.value = updatedReport
                }
            } catch (e: Exception) {
                Log.e("ReceiptViewModel", "Error correcting receipt metadata", e)
            }
        }
    }

    fun validateFileForRepair(context: Context, file: File): FileValidationResult {
        if (!file.exists() || !file.canRead()) {
            return FileValidationResult(false, file, "", "", 0L, "", "Datei existiert nicht oder ist unlesbar.")
        }
        val length = file.length()
        if (length == 0L) {
            return FileValidationResult(false, file, "", "", 0L, "", "Datei ist leer (0 Bytes).")
        }

        val header = file.inputStream().use { run { val buffer = ByteArray(16); var offset = 0; while (offset < buffer.size) { val count = it.read(buffer, offset, buffer.size - offset); if (count < 0) break; offset += count }; buffer.copyOf(offset) } }
        var mimeType = ""
        var formatName = ""

        if (header.size >= 4 && header.copyOfRange(0, 4).contentEquals(byteArrayOf(0x25, 0x50, 0x44, 0x46))) {
            mimeType = "application/pdf"
            formatName = "PDF"
        } else if (header.size >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) {
            mimeType = "image/jpeg"
            formatName = "JPEG"
        } else if (header.size >= 8 && header.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))) {
            mimeType = "image/png"
            formatName = "PNG"
        } else if (header.size >= 12 && header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) && header.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())) {
            mimeType = "image/webp"
            formatName = "WEBP"
        } else {
            return FileValidationResult(false, file, "", "UNKNOWN", length, "", "Ungültiges Dateiformat (Magic Bytes): Kein PDF, JPEG, PNG oder WEBP.")
        }

        // Verify preview generation
        try {
            if (formatName == "PDF") {
                val fd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = android.graphics.pdf.PdfRenderer(fd)
                if (renderer.pageCount <= 0) {
                    renderer.close()
                    fd.close()
                    return FileValidationResult(false, file, mimeType, formatName, length, "", "PDF enthält keine Seiten (0 Seiten).")
                }
                renderer.close()
                fd.close()
            } else {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    return FileValidationResult(false, file, mimeType, formatName, length, "", "Bilddatei konnte nicht dekodiert werden.")
                }
            }
        } catch (e: Exception) {
            return FileValidationResult(false, file, mimeType, formatName, length, "", "Vorschau konnte nicht erzeugt werden: ${e.message}")
        }

        // Calculate SHA-256
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buf = ByteArray(8192)
            var read: Int
            while (stream.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }

        return FileValidationResult(
            isValid = true,
            file = file,
            mimeType = mimeType,
            formatName = formatName,
            sizeBytes = length,
            sha256 = sha256
        )
    }

    fun repairReceiptDocument(receipt: Receipt, tempFile: File, validation: FileValidationResult) {
        viewModelScope.launch(Dispatchers.IO) {
            _repairUiState.value = RepairUiState.Processing("Prüfe und sichere Datei in Google Drive...")
            try {
                val email = _googleAccountEmail.value
                if (email.isNullOrEmpty()) {
                    _repairUiState.value = RepairUiState.Error("Kein Google Account angemeldet.")
                    return@launch
                }
                val token = getValidToken(email)
                val config = drivePersistenceRepository.getDriveAppConfig(token)
                if (config == null) {
                    _repairUiState.value = RepairUiState.Error("Google Drive Konfiguration konnte nicht geladen werden.")
                    return@launch
                }

                val res = drivePersistenceRepository.repairAndUploadOriginalDocument(
                    context = getApplication(),
                    accessToken = token,
                    config = config,
                    receipt = receipt,
                    localFile = tempFile,
                    validationResult = validation
                )

                if (res.success && res.updatedReceipt != null) {
                    val updated = res.updatedReceipt
                    _documentDownloadStatus.value = _documentDownloadStatus.value.toMutableMap().apply {
                        put(
                            receipt.internalId,
                            DocumentDownloadStatus(
                                state = DocumentState.AVAILABLE,
                                localPath = updated.imageUrl,
                                message = "Originaldokument erfolgreich zugeordnet"
                            )
                        )
                    }
                    _repairUiState.value = RepairUiState.Success(
                        message = "Originaldokument wurde erfolgreich zugeordnet und in Google Drive gesichert.",
                        updatedReceipt = updated
                    )
                } else {
                    _repairUiState.value = RepairUiState.Error(res.errorMessage ?: "Fehler bei der Dokumentenreparatur.")
                }
            } catch (e: Exception) {
                Log.e("ReceiptViewModel", "Error in repairReceiptDocument", e)
                _repairUiState.value = RepairUiState.Error("Fehler: ${e.message}")
            }
        }
    }
}

data class FileValidationResult(
    val isValid: Boolean,
    val file: File,
    val mimeType: String,
    val formatName: String,
    val sizeBytes: Long,
    val sha256: String,
    val errorMessage: String? = null
)

sealed class RepairUiState {
    object Idle : RepairUiState()
    data class Processing(val step: String) : RepairUiState()
    data class Success(val message: String, val updatedReceipt: Receipt) : RepairUiState()
    data class Error(val error: String) : RepairUiState()
}

data class DriveTestState(
    val isRunning: Boolean = false,
    val googleAccount: String = "",
    val mainFolderId: String = "",
    val systemFolderId: String = "",
    val testFileId: String = "",
    val uploadSuccess: Boolean = false,
    val downloadSuccess: Boolean = false,
    val contentIdentical: Boolean = false,
    val receiptTestSuccess: Boolean = false,
    val testReceiptInternalId: String = "",
    val testReceiptMainDriveFileId: String = "",
    val testReceiptMetadataFileId: String = "",
    val testReceiptMetadataPath: String = "",
    val isSuccess: Boolean = false,
    val affectedAction: String? = null,
    val errorMessage: String? = null,
    val httpStatusCode: Int? = null,
    val isReAuthRequired: Boolean = false,
    val testRunTime: String? = null
)


enum class DocumentState {
    CHECKING,
    DOWNLOAD_REQUIRED,
    DOWNLOADING,
    AVAILABLE,
    UNSUPPORTED,
    ERROR
}

data class DocumentDownloadStatus(
    val state: DocumentState,
    val localPath: String? = null,
    val message: String? = null
)

