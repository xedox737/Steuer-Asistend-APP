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

    private val _bankStatementResult = MutableStateFlow<com.example.api.BankStatementReconciliationResult?>(
        com.example.api.BankStatementReconciliationResult(
            period = "01.07.2025 - 31.07.2025",
            totalIncoming = 1780.0,
            totalOutgoing = 1496.30,
            matchedCount = 3,
            missingReceiptsCount = 2,
            rentArrearsCount = 1,
            items = listOf(
                com.example.api.BankStatementMatchItem(
                    date = "02.07.2025",
                    counterparty = "Max Mustermann",
                    amount = 750.0,
                    isIncome = true,
                    purpose = "Miete WE 01 Juli 2025",
                    status = "MATCHED",
                    notes = "Miete vollständig eingegangen"
                ),
                com.example.api.BankStatementMatchItem(
                    date = "05.07.2025",
                    counterparty = "Stadtwerke München",
                    amount = 280.50,
                    isIncome = false,
                    purpose = "Abschlag Strom & Gas",
                    status = "MATCHED",
                    notes = "Mit Versorgungs-Beleg aus Juli abgeglichen"
                ),
                com.example.api.BankStatementMatchItem(
                    date = "10.07.2025",
                    counterparty = "Hornbach Baumarkt",
                    amount = 145.80,
                    isIncome = false,
                    purpose = "Material Wandfarbe",
                    status = "MISSING_RECEIPT",
                    notes = "⚠️ Kein Beleg in der App vorhanden! Bitte Kassenzettel hochladen."
                ),
                com.example.api.BankStatementMatchItem(
                    date = "12.07.2025",
                    counterparty = "Malermeister Müller",
                    amount = 650.00,
                    isIncome = false,
                    purpose = "Re-Nr 2025-882 Renovierung",
                    status = "MISSING_RECEIPT",
                    notes = "⚠️ Abbuchung ohne Beleg! Rechnungsbeleg fehlt für Steuer."
                ),
                com.example.api.BankStatementMatchItem(
                    date = "28.07.2025",
                    counterparty = "Thomas Weber",
                    amount = 350.00,
                    isIncome = true,
                    purpose = "Teilzahlung Miete WE 05 (Soll: 700 €)",
                    status = "RENT_ARREARS",
                    notes = "🚨 Mietrückstand: 350,00 € fehlen für den Monat Juli!"
                )
            ),
            summary = "Bankabgleich ergab 2 fehlende Abbuchungsbelege und 1 Mietrückstand. Handlungsbedarf vorliegend."
        )
    )
    val bankStatementResult = _bankStatementResult.asStateFlow()
    private val _isMatchingBankStatement = MutableStateFlow(false)
    val isMatchingBankStatement = _isMatchingBankStatement.asStateFlow()

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
                editor.putFloat("unit_area_${preset.name}", preset.wohnflaeche.toFloat())
                editor.putString("unit_start_${preset.name}", preset.mietvertragsstart)
                editor.apply()
                preset
            } else {
                val floor = when (index % 4) {
                    0 -> "EG"
                    1 -> "1. OG"
                    2 -> "2. OG"
                    else -> "DG"
                }
                val newLabel = "$name ($floor)"
                val newStatus = "Vermietet"
                val newUnit = WohneinheitStatus(
                    name = name,
                    label = newLabel,
                    status = newStatus,
                    mieter = "",
                    kaltmiete = 500.0,
                    wohnflaeche = 60.0,
                    mietvertragsstart = ""
                )
                val editor = unitPrefs.edit()
                editor.putString("unit_status_$name", newUnit.status)
                editor.putString("unit_label_$name", newUnit.label)
                editor.putString("unit_mieter_$name", newUnit.mieter)
                editor.putFloat("unit_rent_$name", newUnit.kaltmiete.toFloat())
                editor.putFloat("unit_area_$name", newUnit.wohnflaeche.toFloat())
                editor.putString("unit_start_$name", newUnit.mietvertragsstart)
                editor.apply()
                newUnit
            }
        }
    }

    fun updateWohneinheit(updated: WohneinheitStatus) {
        val unitPrefs = getApplication<Application>().getSharedPreferences("wohneinheiten_prefs", Context.MODE_PRIVATE)
        unitPrefs.edit().apply {
            putString("unit_status_${updated.name}", updated.status)
            putString("unit_label_${updated.name}", updated.label)
            putString("unit_mieter_${updated.name}", updated.mieter)
            putFloat("unit_rent_${updated.name}", updated.kaltmiete.toFloat())
            putFloat("unit_area_${updated.name}", updated.wohnflaeche.toFloat())
            putString("unit_start_${updated.name}", updated.mietvertragsstart)
        }.apply()
        _wohneinheitenStatus.value = getWohneinheitenFromPrefs()

        if (FirestoreService.isCloudActive()) {
            viewModelScope.launch {
                FirestoreService.saveWohneinheit(updated)
            }
        }
        
        // Auto drive backup if enabled
        if (_isDriveConnected.value && _autoDriveBackup.value) {
            val email = _googleAccountEmail.value
            if (email != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val token = getValidToken(email)
                        val folderId = GoogleDriveClient.getOrCreateFolder(token) ?: return@launch
                        GoogleDriveClient.uploadWohneinheitenCsv(token, folderId, _wohneinheitenStatus.value)
                    } catch (e: Exception) {
                        Log.w("ReceiptViewModel", "Drive sync postponed for units: ${e.message}")
                    }
                }
            }
        }
    }

    fun connectDrive(email: String, accessToken: String? = null) {
        sharedPrefs.edit().apply {
            putBoolean("user_disconnected", false)
            putString("connected_email", email)
            if (accessToken != null) {
                putString("access_token", accessToken)
            }
        }.apply()
        _googleAccountEmail.value = email
        _isDriveConnected.value = true
        _driveSyncStatus.value = if (accessToken != null) {
            "Automatisch verknüpft (Aktiv: $email)"
        } else {
            "Automatisch verknüpft mit $email"
        }
        // Initialize Persistent Storage
        initializeDrivePersistence()
    }

    fun disconnectDrive() {
        sharedPrefs.edit().apply {
            putBoolean("user_disconnected", true)
            remove("connected_email")
            remove("access_token")
        }.apply()
        _googleAccountEmail.value = null
        _isDriveConnected.value = false
        _driveSyncStatus.value = "Verbindung getrennt"
        _driveSystemFolderStatus.value = "Nicht eingerichtet"
        _driveTestState.value = null
    }

    fun runRealDriveTest() {
        val email = _googleAccountEmail.value.takeIf { !it.isNullOrBlank() } ?: "Nicht angemeldet"
        _driveTestState.value = DriveTestState(
            isRunning = true,
            googleAccount = email
        )
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dateFormat = SimpleDateFormat("dd.MM.yyyy, HH:mm 'Uhr'", Locale.GERMANY)
                if (email == "Nicht angemeldet") {
                    val nowStr = dateFormat.format(Date())
                    _driveTestState.value = DriveTestState(
                        isRunning = false,
                        googleAccount = email,
                        mainFolderId = "Nicht gefunden",
                        systemFolderId = "Nicht gefunden",
                        testFileId = "Nicht erstellt",
                        uploadSuccess = false,
                        downloadSuccess = false,
                        contentIdentical = false,
                        isSuccess = false,
                        affectedAction = "Authentifizierung",
                        errorMessage = "Kein Google-Konto angemeldet. Bitte verbinden Sie sich zuerst über Google-Login.",
                        isReAuthRequired = true,
                        testRunTime = nowStr
                    )
                    return@launch
                }

                val token = getValidToken(email)
                val apiResult = GoogleDriveClient.runDriveTest(token)
                val nowStr = dateFormat.format(Date())

                var receiptTestRes = com.example.data.DriveReceiptTestResult(success = false, errorMessage = "Drive nicht initialisiert")
                val initResult = drivePersistenceRepository.initializeDriveStorage(token)
                if (initResult is com.example.data.DriveInitializationResult.SuccessLoadedExisting) {
                    receiptTestRes = drivePersistenceRepository.testDriveReceiptStorage(token, initResult.config)
                } else if (initResult is com.example.data.DriveInitializationResult.SuccessCreatedNew) {
                    receiptTestRes = drivePersistenceRepository.testDriveReceiptStorage(token, initResult.config)
                }

                val basicSuccess = apiResult.uploadSuccess && apiResult.downloadSuccess && apiResult.contentIdentical && apiResult.testFileId.isNotBlank()
                val isSuccess = basicSuccess && receiptTestRes.success

                _driveTestState.value = DriveTestState(
                    isRunning = false,
                    googleAccount = email,
                    mainFolderId = apiResult.mainFolderId.ifEmpty { "Nicht gefunden" },
                    systemFolderId = apiResult.systemFolderId.ifEmpty { "Nicht gefunden" },
                    testFileId = apiResult.testFileId.ifEmpty { "Nicht erstellt" },
                    uploadSuccess = apiResult.uploadSuccess,
                    downloadSuccess = apiResult.downloadSuccess,
                    contentIdentical = apiResult.contentIdentical,
                    receiptTestSuccess = receiptTestRes.success,
                    testReceiptInternalId = receiptTestRes.testInternalId,
                    testReceiptMainDriveFileId = receiptTestRes.mainDriveFileId,
                    testReceiptMetadataFileId = receiptTestRes.metadataFileId,
                    testReceiptMetadataPath = receiptTestRes.metadataPath,
                    isSuccess = isSuccess,
                    affectedAction = if (!basicSuccess) apiResult.affectedAction else if (!receiptTestRes.success) "Beleg-Speicherungs-Test" else null,
                    errorMessage = apiResult.errorMessage ?: receiptTestRes.errorMessage,
                    httpStatusCode = apiResult.httpStatusCode,
                    isReAuthRequired = apiResult.isReAuthRequired,
                    testRunTime = nowStr
                )
            } catch (e: Exception) {
                val nowStr = SimpleDateFormat("dd.MM.yyyy, HH:mm 'Uhr'", Locale.GERMANY).format(Date())
                val msg = e.message ?: e.toString()
                val isAuthErr = msg.contains("401") || msg.contains("403") || msg.contains("Sign-In") || msg.contains("Anmeldung")
                _driveTestState.value = DriveTestState(
                    isRunning = false,
                    googleAccount = email,
                    mainFolderId = "Nicht gefunden",
                    systemFolderId = "Nicht gefunden",
                    testFileId = "Nicht erstellt",
                    uploadSuccess = false,
                    downloadSuccess = false,
                    contentIdentical = false,
                    isSuccess = false,
                    affectedAction = "Authentifizierung",
                    errorMessage = msg,
                    httpStatusCode = if (msg.contains("401")) 401 else if (msg.contains("403")) 403 else null,
                    isReAuthRequired = isAuthErr,
                    testRunTime = nowStr
                )
            }
        }
    }

    fun setSyncStatus(status: String) {
        _driveSyncStatus.value = status
    }

    fun initializeDrivePersistence() {
        val email = _googleAccountEmail.value
        if (email.isNullOrEmpty() || !_isDriveConnected.value) {
            _driveSystemFolderStatus.value = "Nicht eingerichtet"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = getValidToken(email)
                _driveSyncStatus.value = "Initialisiere dauerhafte Datenstruktur..."
                val result = drivePersistenceRepository.initializeDriveStorage(token)
                when (result) {
                    is com.example.data.DriveInitializationResult.SuccessCreatedNew -> {
                        _driveSystemFolderStatus.value = "Gefunden"
                        _driveSyncStatus.value = "Google-Drive-Grundstruktur erfolgreich erstellt."
                        _driveSyncError.value = null
                        val nowStr = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                        _lastStammdatenBackupTime.value = nowStr
                        sharedPrefs.edit().putString("last_stammdaten_backup_time", nowStr).remove("drive_sync_error").apply()
                    }
                    is com.example.data.DriveInitializationResult.SuccessLoadedExisting -> {
                        _driveSystemFolderStatus.value = "Gefunden"
                        val isLocalEmpty = repository.allReceipts.first().isEmpty()
                        if (isLocalEmpty) {
                            val preview = drivePersistenceRepository.checkDriveInventoryForRestore(token, result.config)
                            _restorePreview.value = preview
                            if (preview.inventory.canRestore) {
                                _isRestoreRequired.value = true
                                _driveSyncStatus.value = "Google Drive Bestand gefunden (${preview.inventory.receiptCountInIndex} Belege). Wiederherstellung angeboten."
                            } else {
                                _driveSyncStatus.value = "Dauerhafte Datenstruktur verbunden."
                            }
                        } else {
                            _driveSyncStatus.value = "Dauerhafte Datenstruktur verbunden."
                        }
                        _driveSyncError.value = null
                        val nowStr = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                        _lastStammdatenBackupTime.value = nowStr
                        sharedPrefs.edit().putString("last_stammdaten_backup_time", nowStr).remove("drive_sync_error").apply()
                    }
                    is com.example.data.DriveInitializationResult.Failure -> {
                        _driveSystemFolderStatus.value = "Nicht eingerichtet"
                        _driveSyncStatus.value = "Fehler bei der Initialisierung"
                        _driveSyncError.value = result.error
                        sharedPrefs.edit().putString("drive_sync_error", result.error).apply()
                    }
                }
            } catch (e: Exception) {
                Log.w("ReceiptViewModel", "Drive initialization postponed: ${e.message}")
                _isDriveConnected.value = false
                _driveSystemFolderStatus.value = "Nicht eingerichtet"
                _driveSyncStatus.value = "Google-Anmeldung erforderlich (Web-Login)"
                _driveSyncError.value = e.message ?: e.toString()
                sharedPrefs.edit().putString("drive_sync_error", e.message ?: e.toString()).apply()
            }
        }
    }

    fun checkForDriveRestore() {
        val email = _googleAccountEmail.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _driveSyncStatus.value = "Prüfe Google-Drive-Bestand für Wiederherstellung..."
                val token = getValidToken(email)
                val initResult = drivePersistenceRepository.initializeDriveStorage(token)
                if (initResult is com.example.data.DriveInitializationResult.SuccessLoadedExisting) {
                    val preview = drivePersistenceRepository.checkDriveInventoryForRestore(token, initResult.config)
                    _restorePreview.value = preview
                    if (preview.inventory.canRestore) {
                        _isRestoreRequired.value = true
                        _driveSyncStatus.value = "Google Drive Bestand gefunden (${preview.inventory.receiptCountInIndex} Belege). Wiederherstellung bereit."
                    } else {
                        _driveSyncStatus.value = "Kein wiederherstellbarer Bestand in Google Drive gefunden."
                    }
                }
            } catch (e: Exception) {
                Log.e("ReceiptViewModel", "Error checking Drive inventory for restore", e)
                _driveSyncStatus.value = "Fehler bei der Drive-Bestandsprüfung: ${e.message}"
            }
        }
    }

    fun performFullRestoreConfirmation(mode: com.example.data.RestoreMode = com.example.data.RestoreMode.REPLACE_FULL) {
        val email = _googleAccountEmail.value ?: return
        _isRestoring.value = true
        _driveSyncStatus.value = "Wiederherstellung aus Google Drive läuft..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = getValidToken(email)
                val initResult = drivePersistenceRepository.initializeDriveStorage(token)
                if (initResult is com.example.data.DriveInitializationResult.SuccessLoadedExisting) {
                    val report = drivePersistenceRepository.executeFullDriveRestore(token, initResult.config, mode)
                    _restoreReport.value = report
                    _isRestoring.value = false
                    _isRestoreRequired.value = false
                    _wohneinheitenStatus.value = getWohneinheitenFromPrefs()
                    loadLearnedRules()
                    _driveSyncStatus.value = if (report.isSuccess) {
                        "Wiederherstellung erfolgreich! ${report.receiptsRestored} Belege geladen."
                    } else {
                        "Wiederherstellung mit ${report.errorCount} Fehlern abgeschlossen."
                    }
                } else {
                    _isRestoring.value = false
                    _driveSyncStatus.value = "Wiederherstellung abgebrochen: Keine Drive-Konfiguration."
                }
            } catch (e: Exception) {
                _isRestoring.value = false
                Log.e("ReceiptViewModel", "Error during full restore confirmation", e)
                _driveSyncStatus.value = "Fehler bei Wiederherstellung: ${e.message}"
            }
        }
    }

    fun runRestoreDryRun() {
        val email = _googleAccountEmail.value ?: return
        _isRestoring.value = true
        _driveSyncStatus.value = "Führe Restore-Test (Dry Run) durch..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = getValidToken(email)
                val initResult = drivePersistenceRepository.initializeDriveStorage(token)
                if (initResult is com.example.data.DriveInitializationResult.SuccessLoadedExisting) {
                    val report = drivePersistenceRepository.runFullRestoreEndToEndTest(token, initResult.config)
                    _restoreReport.value = report
                    _isRestoring.value = false
                    _driveSyncStatus.value = "Restore-Test abgeschlossen: ${report.receiptsRestored} Belege geprüft."
                } else {
                    _isRestoring.value = false
                    _driveSyncStatus.value = "Restore-Test abgebrochen: Keine Drive-Konfiguration."
                }
            } catch (e: Exception) {
                _isRestoring.value = false
                Log.e("ReceiptViewModel", "Error during restore dry run", e)
                _driveSyncStatus.value = "Fehler bei Restore-Test: ${e.message}"
            }
        }
    }

    fun dismissRestoreDialog() {
        _isRestoreRequired.value = false
    }

    fun clearRestoreReport() {
        _restoreReport.value = null
    }

    private val _documentDownloadStatus = MutableStateFlow<Map<String, DocumentDownloadStatus>>(emptyMap())
    val documentDownloadStatus: StateFlow<Map<String, DocumentDownloadStatus>> = _documentDownloadStatus.asStateFlow()

    fun checkDocumentStatus(receipt: Receipt) {
        val currentStatus = _documentDownloadStatus.value[receipt.internalId]
        if (currentStatus?.state == DocumentState.DOWNLOADING || currentStatus?.state == DocumentState.AVAILABLE) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _documentDownloadStatus.value = _documentDownloadStatus.value + (receipt.internalId to DocumentDownloadStatus(DocumentState.CHECKING))
            
            val paths = if (receipt.imageUrl.isNotEmpty()) receipt.imageUrl.split(",") else emptyList()
            val filesExist = paths.isNotEmpty() && paths.all { path -> 
                val f = java.io.File(path)
                val exists = f.exists()
                val canRead = f.canRead()
                val length = f.length()
                Log.d("ReceiptViewModel", "File check - path: $path, exists: $exists, canRead: $canRead, length: $length")
                exists && length > 0 
            }

            Log.d("ReceiptViewModel", """
                --- Beleg Prüfprotokoll ---
                internalId: ${receipt.internalId}
                mainDriveFileId vorhanden: ${!receipt.driveFileId.isNullOrEmpty()} (${receipt.driveFileId})
                gespeicherter alter imageUrl: ${receipt.imageUrl}
                existiert die lokale Datei tatsächlich?: $filesExist
                gespeicherter MIME-Typ: ${receipt.originalMimeType}
            """.trimIndent())

            if (filesExist) {
                val mime = receipt.originalMimeType ?: ""
                if (mime.contains("text/plain", ignoreCase = true)) {
                    _documentDownloadStatus.value = _documentDownloadStatus.value + (receipt.internalId to DocumentDownloadStatus(DocumentState.UNSUPPORTED, receipt.imageUrl, "Testdokument – keine Bildvorschau verfügbar"))
                } else {
                    _documentDownloadStatus.value = _documentDownloadStatus.value + (receipt.internalId to DocumentDownloadStatus(DocumentState.AVAILABLE, receipt.imageUrl))
                }
            } else if (!receipt.driveFileId.isNullOrEmpty()) {
                _documentDownloadStatus.value = _documentDownloadStatus.value + (receipt.internalId to DocumentDownloadStatus(DocumentState.DOWNLOAD_REQUIRED))
                downloadReceiptDocument(receipt)
            } else {
                _documentDownloadStatus.value = _documentDownloadStatus.value + (receipt.internalId to DocumentDownloadStatus(DocumentState.ERROR, null, "Keine lokale Datei und keine Drive-ID vorhanden."))
            }
        }
    }

    fun forceDocumentDownload(receipt: Receipt) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Delete existing local files if they exist to force redownload
                if (!receipt.imageUrl.isNullOrEmpty()) {
                    val paths = receipt.imageUrl.split(",")
                    paths.forEach { path ->
                        val file = java.io.File(path)
                        if (file.exists()) {
                            file.delete()
                            Log.d("ReceiptViewModel", "Deleted corrupted or empty local file at $path before forcing redownload.")
                        }
                    }
                }
                
                // Clear the status and trigger download
                _documentDownloadStatus.value = _documentDownloadStatus.value.toMutableMap().apply { remove(receipt.internalId) }
                downloadReceiptDocument(receipt)
            } catch (e: Exception) {
                Log.e("ReceiptViewModel", "Error in forceDocumentDownload", e)
            }
        }
    }

    fun diagnoseAndFixAllReceipts() {
        val email = _googleAccountEmail.value
        if (email.isNullOrEmpty()) {
            Log.e("ReceiptViewModel", "Diagnose failed: No account email")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = getValidToken(email)
                val receipts = repository.getAllReceiptsList()
                var validCount = 0
                var invalidCount = 0
                for (receipt in receipts) {
                    Log.d("ReceiptViewModel", "=== DIAGNOSE START: ${receipt.aussteller} (${receipt.datum}) ===")
                    Log.d("ReceiptViewModel", "internalId: ${receipt.internalId}, mainDriveFileId: ${receipt.driveFileId}, metadataFileId: ${receipt.driveMetadataFileId}")
                    
                    var mainFileJsonInfo: String? = null
                    if (!receipt.driveFileId.isNullOrEmpty()) {
                        val driveFileBytes = com.example.api.GoogleDriveClient.downloadFileBytes(token, receipt.driveFileId!!)
                        if (driveFileBytes != null) {
                            val header = driveFileBytes.take(16).toByteArray()
                            val hex = header.joinToString(" ") { b -> "%02X".format(b) }
                            Log.d("ReceiptViewModel", "MainDriveFile Header (16 bytes): $hex")
                            Log.d("ReceiptViewModel", "MainDriveFile Size: ${driveFileBytes.size} bytes")
                            
                            var detectedFormat = "Unknown"
                            if (header.size >= 4 && header.copyOfRange(0, 4).contentEquals(byteArrayOf(0x25, 0x50, 0x44, 0x46))) {
                                detectedFormat = "PDF"
                            } else if (header.size >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) {
                                detectedFormat = "JPEG"
                            } else if (header.size >= 8 && header.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))) {
                                detectedFormat = "PNG"
                            } else if (header.size >= 12 && header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) && header.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())) {
                                detectedFormat = "WEBP"
                            } else if (header.size >= 1 && header[0] == 0x7B.toByte()) {
                                detectedFormat = "JSON"
                                mainFileJsonInfo = String(driveFileBytes.take(200).toByteArray())
                            }
                            Log.d("ReceiptViewModel", "MainDriveFile Detected Format: $detectedFormat")
                            
                            if (detectedFormat == "JSON") {
                                Log.d("ReceiptViewModel", "Content snippet: $mainFileJsonInfo")
                                invalidCount++
                                
                                // Fix it! Search for the real original file.
                                val query = "'${receipt.driveFolderId}' in parents and trashed = false"
                                val files = com.example.api.GoogleDriveClient.searchFiles(token, query)
                                Log.d("ReceiptViewModel", "Found ${files.size} files in folder ${receipt.driveFolderId}")
                                
                                var realOriginalFileId: String? = null
                                var realOriginalSize: Long = 0
                                var realFormat = ""
                                
                                for (f in files) {
                                    if (f.id == receipt.driveFileId || f.id == receipt.driveMetadataFileId) continue
                                    
                                    val candidateBytes = com.example.api.GoogleDriveClient.downloadFileBytes(token, f.id)
                                    if (candidateBytes != null && candidateBytes.isNotEmpty()) {
                                        val candHeader = candidateBytes.take(16).toByteArray()
                                        if (candHeader.size >= 4 && candHeader.copyOfRange(0, 4).contentEquals(byteArrayOf(0x25, 0x50, 0x44, 0x46))) {
                                            realOriginalFileId = f.id
                                            realOriginalSize = candidateBytes.size.toLong()
                                            realFormat = "PDF"
                                            break
                                        } else if (candHeader.size >= 3 && candHeader[0] == 0xFF.toByte() && candHeader[1] == 0xD8.toByte() && candHeader[2] == 0xFF.toByte()) {
                                            realOriginalFileId = f.id
                                            realOriginalSize = candidateBytes.size.toLong()
                                            realFormat = "JPEG"
                                            break
                                        }
                                        // Add PNG/WEBP if needed
                                    }
                                }
                                
                                if (realOriginalFileId != null) {
                                    Log.d("ReceiptViewModel", "FOUND REAL ORIGINAL! ID: $realOriginalFileId, Format: $realFormat, Size: $realOriginalSize")
                                    val newMime = if (realFormat == "PDF") "application/pdf" else "image/jpeg"
                                    
                                    /* 
                                    // Update locally temporarily disabled for read-only diagnosis
                                    val updated = receipt.copy(
                                        driveFileId = realOriginalFileId,
                                        originalMimeType = newMime,
                                        fileSizeBytes = realOriginalSize,
                                        storedFilename = receipt.storedFilename?.replace(".txt", ".${realFormat.lowercase()}")?.replace(".json", ".${realFormat.lowercase()}") ?: "Beleg.${realFormat.lowercase()}"
                                    )
                                    repository.insert(updated)
                                    
                                    // Also update index in drive if needed (skipping for now to avoid side effects, but should ideally update index)
                                    
                                    Log.d("ReceiptViewModel", "Re-triggering download with new ID.")
                                    forceDocumentDownload(updated)
                                    */
                                } else {
                                    Log.d("ReceiptViewModel", "NO REAL ORIGINAL FOUND IN FOLDER.")
                                }
                            } else {
                                validCount++
                            }
                        }
                    }
                    Log.d("ReceiptViewModel", "=== DIAGNOSE END ===")
                }
                Log.d("ReceiptViewModel", "TOTAL DIAGNOSE RESULTS: $validCount valid, $invalidCount invalid originals.")
            } catch (e: Exception) {
                Log.e("ReceiptViewModel", "Diagnose error", e)
            }
        }
    }

    fun downloadReceiptDocument(receipt: Receipt) {
        val email = _googleAccountEmail.value
        if (email.isNullOrEmpty()) {
            _documentDownloadStatus.value = _documentDownloadStatus.value + (receipt.internalId to DocumentDownloadStatus(DocumentState.ERROR, null, "Nicht an Google Drive angemeldet."))
            return
        }
        
        _documentDownloadStatus.value = _documentDownloadStatus.value + (receipt.internalId to DocumentDownloadStatus(DocumentState.DOWNLOADING))
        
        viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            Log.d("ReceiptViewModel", "Start Drive-Download für Beleg: ${receipt.internalId} um $startTime")
            try {
                kotlinx.coroutines.withTimeout(30000L) {
                    val token = getValidToken(email)
                    _driveSyncStatus.value = "Lade Dokument für Beleg '${receipt.displayId}' herunter..."
                    
                    val localPath = drivePersistenceRepository.downloadDocumentOnDemand(token, receipt)
                    
                    val endTime = System.currentTimeMillis()
                    val targetFile = java.io.File(localPath)
                    Log.d("ReceiptViewModel", """
                        --- Beleg Downloadprotokoll ---
                        internalId: ${receipt.internalId}
                        Start und Ende des Drive-Downloads: $startTime bis $endTime (${endTime - startTime} ms)
                        erzeugter lokaler Zielpfad: $localPath
                        Ergebnis von File.exists(): ${targetFile.exists()}, File.canRead(): ${targetFile.canRead()}, File.length(): ${targetFile.length()} Bytes
                        endgültiger UI-Status: AVAILABLE
                    """.trimIndent())

                    val mime = receipt.originalMimeType ?: ""
                    if (mime.contains("text/plain", ignoreCase = true)) {
                        _documentDownloadStatus.value = _documentDownloadStatus.value + (receipt.internalId to DocumentDownloadStatus(DocumentState.UNSUPPORTED, localPath, "Testdokument – keine Bildvorschau verfügbar"))
                    } else {
                        _documentDownloadStatus.value = _documentDownloadStatus.value + (receipt.internalId to DocumentDownloadStatus(DocumentState.AVAILABLE, localPath))
                    }
                    _driveSyncStatus.value = "Dokument geladen."
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e("ReceiptViewModel", "Timeout downloading document", e)
                _driveSyncStatus.value = "Zeitüberschreitung beim Laden."
                _documentDownloadStatus.value = _documentDownloadStatus.value + (receipt.internalId to DocumentDownloadStatus(DocumentState.ERROR, null, "Zeitüberschreitung (30s) beim Download."))
            } catch (e: Exception) {
                Log.e("ReceiptViewModel", "Error downloading document", e)
                _driveSyncStatus.value = "Fehler beim Laden: ${e.message}"
                _documentDownloadStatus.value = _documentDownloadStatus.value + (receipt.internalId to DocumentDownloadStatus(DocumentState.ERROR, null, "Download fehlgeschlagen: ${e.message}"))
            }
        }
    }

    private fun getValidToken(email: String): String {
        val manualToken = sharedPrefs.getString("access_token", null)
        if (!manualToken.isNullOrEmpty()) {
            return manualToken
        }
        val scopeStr = "oauth2:https://www.googleapis.com/auth/drive.file"
        try {
            return com.google.android.gms.auth.GoogleAuthUtil.getToken(getApplication(), email, scopeStr)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("Account not present", ignoreCase = true) || 
                msg.contains("AccountNotPresent", ignoreCase = true) || 
                e.javaClass.simpleName.contains("AccountNotPresent", ignoreCase = true) || 
                msg.contains("Unknown", ignoreCase = true) || 
                msg.contains("unregistered", ignoreCase = true) ||
                e is com.google.android.gms.auth.UserRecoverableAuthException) {
                throw Exception("Google Play Services Sign-In fehlgeschlagen (Konto '$email' nicht auf dem Gerät eingerichtet). Bitte nutzen Sie den grünen 'Über Google-Login verbinden' Button für den sicheren Web-Zugang.")
            }
            throw e
        }
    }

    fun toggleAutoBackup(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("auto_backup", enabled).apply()
        _autoDriveBackup.value = enabled
    }

    fun syncAllToDrive() {
        val email = _googleAccountEmail.value ?: return
        if (!_isDriveConnected.value) {
            _driveSyncStatus.value = "Google-Anmeldung erforderlich"
            return
        }
        _isDriveSyncing.value = true
        _driveSyncStatus.value = "Synchronisiere mit Google Drive..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Get Google Auth Access Token
                val token = getValidToken(email)

                // 1. Initialize persistent drive structure first!
                val initResult = drivePersistenceRepository.initializeDriveStorage(token)
                val config = when (initResult) {
                    is com.example.data.DriveInitializationResult.SuccessCreatedNew -> {
                        _driveSystemFolderStatus.value = "Gefunden"
                        val nowStr = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                        _lastStammdatenBackupTime.value = nowStr
                        sharedPrefs.edit().putString("last_stammdaten_backup_time", nowStr).remove("drive_sync_error").apply()
                        initResult.config
                    }
                    is com.example.data.DriveInitializationResult.SuccessLoadedExisting -> {
                        _driveSystemFolderStatus.value = "Gefunden"
                        if (initResult.restoredStammdaten) {
                            _wohneinheitenStatus.value = getWohneinheitenFromPrefs()
                            loadLearnedRules()
                        }
                        val nowStr = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                        _lastStammdatenBackupTime.value = nowStr
                        sharedPrefs.edit().putString("last_stammdaten_backup_time", nowStr).remove("drive_sync_error").apply()
                        initResult.config
                    }
                    is com.example.data.DriveInitializationResult.Failure -> {
                        _driveSystemFolderStatus.value = "Nicht eingerichtet"
                        _driveSyncStatus.value = "Fehler: ${initResult.error}"
                        _driveSyncError.value = initResult.error
                        sharedPrefs.edit().putString("drive_sync_error", initResult.error).apply()
                        _isDriveSyncing.value = false
                        return@launch
                    }
                }

                // 2. Upload CSV Ledger
                val list = receipts.value
                val isLocalEmpty = list.isEmpty()
                if (isLocalEmpty && initResult is com.example.data.DriveInitializationResult.SuccessLoadedExisting) {
                    val preview = drivePersistenceRepository.checkDriveInventoryForRestore(token, initResult.config)
                    if (preview.inventory.canRestore) {
                        _restorePreview.value = preview
                        _isRestoreRequired.value = true
                        _isDriveSyncing.value = false
                        _driveSyncStatus.value = "Abgebrochen: Lokaler Bestand ist leer, aber Google Drive enthält ${preview.inventory.receiptCountInIndex} Belege. Bitte zuerst Wiederherstellung ausführen."
                        return@launch
                    }
                }

                val csvSuccess = GoogleDriveClient.uploadLedgerCsv(token, config.rootFolderId, list)
                
                // 3. Upload Wohneinheiten CSV
                val unitsSuccess = GoogleDriveClient.uploadWohneinheitenCsv(token, config.rootFolderId, _wohneinheitenStatus.value)

                // 4. Update JSON files in _BelegApp-Daten to ensure latest changes are saved
                val backupSuccess = drivePersistenceRepository.saveStammdatenToDrive(token, config)

                // 5. Upload each receipt
                var successCount = 0
                for (receipt in list) {
                    val success = drivePersistenceRepository.syncReceiptToDrive(token, config, receipt)
                    if (success) successCount++
                }

                _isDriveSyncing.value = false
                if (csvSuccess && unitsSuccess && backupSuccess) {
                    _driveSyncStatus.value = "Erfolgreich! Hauptbuch, Wohneinheiten, Stammdaten & $successCount Belege synchronisiert."
                    _driveSyncError.value = null
                    sharedPrefs.edit().remove("drive_sync_error").apply()
                } else if (csvSuccess) {
                    _driveSyncStatus.value = "Teilweise erfolgreich: Hauptbuch & $successCount Belege synchronisiert. Stammdaten nicht."
                    _driveSyncError.value = "Stammdaten-Backup fehlgeschlagen."
                    sharedPrefs.edit().putString("drive_sync_error", _driveSyncError.value).apply()
                } else {
                    _driveSyncStatus.value = "Teilweise erfolgreich: $successCount Belege hochgeladen."
                    _driveSyncError.value = "Synchronisationsfehler."
                    sharedPrefs.edit().putString("drive_sync_error", _driveSyncError.value).apply()
                }

            } catch (e: Exception) {
                Log.w("ReceiptViewModel", "Drive sync postponed: ${e.message}")
                _isDriveConnected.value = false
                _driveSyncStatus.value = "Google-Anmeldung erforderlich (Web-Login)"
                _driveSyncError.value = e.message ?: e.toString()
                sharedPrefs.edit().putString("drive_sync_error", e.message ?: e.toString()).apply()
                _isDriveSyncing.value = false
            }
        }
    }

    private fun uploadReceiptToDriveInternal(receipt: Receipt) {
        val email = _googleAccountEmail.value ?: return
        if (!_isDriveConnected.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = getValidToken(email)
                val initResult = drivePersistenceRepository.initializeDriveStorage(token)
                val config = when (initResult) {
                    is com.example.data.DriveInitializationResult.SuccessCreatedNew -> initResult.config
                    is com.example.data.DriveInitializationResult.SuccessLoadedExisting -> initResult.config
                    is com.example.data.DriveInitializationResult.Failure -> {
                        repository.insert(receipt.copy(syncStatus = "ERROR", syncError = "Drive-Initialisierungsfehler: ${initResult.error}"))
                        return@launch
                    }
                }
                drivePersistenceRepository.syncReceiptToDrive(token, config, receipt)
            } catch (e: Exception) {
                Log.w("ReceiptViewModel", "Auto backup postponed for receipt ${receipt.id}: ${e.message}")
                repository.insert(receipt.copy(syncStatus = "ERROR", syncError = e.message ?: e.toString()))
            }
        }
    }

    fun syncReceiptManually(receipt: Receipt) {
        val email = _googleAccountEmail.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val token = getValidToken(email)
                val initResult = drivePersistenceRepository.initializeDriveStorage(token)
                val config = when (initResult) {
                    is com.example.data.DriveInitializationResult.SuccessCreatedNew -> initResult.config
                    is com.example.data.DriveInitializationResult.SuccessLoadedExisting -> initResult.config
                    is com.example.data.DriveInitializationResult.Failure -> {
                        repository.insert(receipt.copy(syncStatus = "ERROR", syncError = "Drive-Initialisierungsfehler: ${initResult.error}"))
                        return@launch
                    }
                }
                
                // Set to PENDING first to show progress spinner
                repository.insert(receipt.copy(syncStatus = "PENDING", syncError = null))
                
                val success = drivePersistenceRepository.syncReceiptToDrive(token, config, receipt)
                if (!success) {
                    Log.w("ReceiptViewModel", "Manual sync failed for receipt: ${receipt.id}")
                }
            } catch (e: Exception) {
                Log.w("ReceiptViewModel", "Manual sync failed for receipt ${receipt.id}: ${e.message}")
                repository.insert(receipt.copy(syncStatus = "ERROR", syncError = e.message ?: e.toString()))
            }
        }
    }

    fun resetScanState() {
        _scanState.value = ScanUiState.Idle
    }
    
    // UI screen state
    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Scanner UI state
    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

data class AiSearchUiState(
    val isLoading: Boolean = false,
    val query: String = "",
    val result: com.example.api.AiSearchResult? = null,
    val error: String? = null
)

    // Search and filter state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    private val _startDateFilter = MutableStateFlow<String?>(null)
    val startDateFilter: StateFlow<String?> = _startDateFilter.asStateFlow()

    private val _endDateFilter = MutableStateFlow<String?>(null)
    val endDateFilter: StateFlow<String?> = _endDateFilter.asStateFlow()

    private val _aiSearchState = MutableStateFlow(AiSearchUiState())
    val aiSearchState: StateFlow<AiSearchUiState> = _aiSearchState.asStateFlow()

    // Base filtered receipts
    private val baseFilteredReceipts: Flow<List<Receipt>> = combine(
        receipts,
        _searchQuery,
        _selectedCategoryFilter,
        _startDateFilter,
        _endDateFilter
    ) { list, query, filter, start, end ->
        list.filter { receipt ->
            val matchesQuery = query.isEmpty() || receipt.aussteller.contains(query, ignoreCase = true) ||
                    receipt.beschreibung.contains(query, ignoreCase = true) ||
                    receipt.kontoNr.contains(query) ||
                    receipt.hauptkategorie.contains(query, ignoreCase = true) ||
                    receipt.unterkategorie.contains(query, ignoreCase = true) ||
                    receipt.wohneinheit.contains(query, ignoreCase = true) ||
                    receipt.mieter.contains(query, ignoreCase = true)
            val matchesFilter = filter == null || receipt.hauptkategorie == filter
            val matchesStart = start == null || receipt.datum >= start
            val matchesEnd = end == null || receipt.datum <= end
            matchesQuery && matchesFilter && matchesStart && matchesEnd
        }
    }

    val filteredReceipts: StateFlow<List<Receipt>> = combine(
        baseFilteredReceipts,
        _aiSearchState
    ) { list: List<Receipt>, aiState: AiSearchUiState ->
        val matchingIds = aiState.result?.matchingReceiptIds
        if (matchingIds == null || matchingIds.isEmpty()) {
            list
        } else {
            list.filter { receipt -> matchingIds.contains(receipt.id.toLong()) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val rentStatus = combine(
        wohneinheitenStatus,
        receipts
    ) { units, allReceipts ->
        // Assuming current month is 2026-07
        val currentYearMonth = "2026-07"
        units.map { unit ->
            val hasPaid = allReceipts.any {
                it.wohneinheit == unit.name &&
                it.hauptkategorie == "Miete, Nebenkosten & Kaution" &&
                it.datum.startsWith(currentYearMonth)
            }
            unit to hasPaid
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    data class TaxReport(val income: Double, val expenses: Double, val profit: Double, val estimatedTax: Double)

    val taxReport = receipts.map { allReceipts ->
        val income = allReceipts.filter { it.hauptkategorie == "Miete, Nebenkosten & Kaution" }.sumOf { it.bruttobetrag }
        val expenses = allReceipts.filter { it.hauptkategorie != "Miete, Nebenkosten & Kaution" }.sumOf { it.bruttobetrag }
        val profit = (income - expenses).coerceAtLeast(0.0)
        // 30% flat estimate as placeholder
        val estimatedTax = profit * 0.30
        TaxReport(income, expenses, profit, estimatedTax)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TaxReport(0.0, 0.0, 0.0, 0.0)
    )

    fun generatePaymentReminder(unit: WohneinheitStatus): String {
        return """
            Zahlungserinnerung für Ihre Wohneinheit ${unit.label}
            
            Sehr geehrte/r Mieter/in ${unit.mieter},
            
            für die Wohneinheit ${unit.label} konnte bisher kein Zahlungseingang für die Kaltmiete in Höhe von ${unit.kaltmiete} EUR für den aktuellen Monat festgestellt werden.
            
            Wir bitten Sie, den ausstehenden Betrag zeitnah auf das unten genannte Konto zu überweisen.
            
            Sollte die Zahlung bereits erfolgt sein, betrachten Sie dieses Schreiben bitte als gegenstandslos.
            
            Mit freundlichen Grüßen,
            Ihre Hausverwaltung
        """.trimIndent()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun performAiSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _aiSearchState.value = AiSearchUiState()
            return
        }
        viewModelScope.launch {
            _aiSearchState.value = AiSearchUiState(isLoading = true, query = trimmed)
            val currentList = receipts.value
            var result = com.example.api.GeminiClient.answerNaturalLanguageQuery(trimmed, currentList)

            // Local fallback calculation if Gemini API returns null or key is missing
            if (result == null && currentList.isNotEmpty()) {
                val keywords = trimmed.lowercase().split(" ", ",", ";").filter { it.length > 2 }
                val matches = currentList.filter { r ->
                    keywords.any { kw ->
                        r.beschreibung.lowercase().contains(kw) ||
                        r.hauptkategorie.lowercase().contains(kw) ||
                        r.unterkategorie.lowercase().contains(kw) ||
                        r.aussteller.lowercase().contains(kw) ||
                        r.wohneinheit.lowercase().contains(kw) ||
                        r.mieter.lowercase().contains(kw)
                    }
                }
                val totalSum = matches.sumOf { it.bruttobetrag }
                val ids = matches.map { it.id.toLong() }
                val formattedSum = String.format(java.util.Locale.GERMANY, "%.2f €", totalSum)
                val fallbackAnswer = if (matches.isNotEmpty()) {
                    "Auswertung aus Room-Datenbank für '$trimmed': Insgesamt $formattedSum verteilt auf ${matches.size} Belege."
                } else {
                    "Keine passenden Belege für '$trimmed' in der Room-Datenbank gefunden."
                }
                result = com.example.api.AiSearchResult(
                    answer = fallbackAnswer,
                    totalAmount = if (matches.isNotEmpty()) totalSum else null,
                    matchingReceiptIds = ids
                )
            }

            if (result != null) {
                _aiSearchState.value = AiSearchUiState(isLoading = false, query = trimmed, result = result)
            } else {
                _aiSearchState.value = AiSearchUiState(
                    isLoading = false,
                    query = trimmed,
                    error = "Keine Belege in der Room-Datenbank vorhanden."
                )
            }
        }
    }

    fun clearAiSearch() {
        _aiSearchState.value = AiSearchUiState()
    }

    fun setCategoryFilter(filter: String?) {
        _selectedCategoryFilter.value = filter
    }
    
    fun setDateRangeFilter(start: String?, end: String?) {
        _startDateFilter.value = start
        _endDateFilter.value = end
    }
    
    // DATEV Audit Runs
    val allAuditRuns: StateFlow<List<com.example.data.ExportAuditRun>> = repository.allAuditRuns.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Active Kanzleiprofil State
    private val _activeDatevProfile = MutableStateFlow<com.example.data.DatevProfile>(
        com.example.util.DatevProfileService.getActiveProfile(getApplication())
    )
    val activeDatevProfile: StateFlow<com.example.data.DatevProfile> = _activeDatevProfile.asStateFlow()

    fun updateActiveDatevProfile(profile: com.example.data.DatevProfile) {
        _activeDatevProfile.value = profile
        com.example.util.DatevProfileService.saveActiveProfile(getApplication(), profile)
        recalculateWizardStepData()
    }

    // Wizard Step State (1 to 6)
    private val _wizardStep = MutableStateFlow(1)
    val wizardStep: StateFlow<Int> = _wizardStep.asStateFlow()

    fun setWizardStep(step: Int) {
        _wizardStep.value = step.coerceIn(1, 6)
        recalculateWizardStepData()
    }

    // Wizard Filters
    private val _wizardUnitFilter = MutableStateFlow("ALLE")
    val wizardUnitFilter: StateFlow<String> = _wizardUnitFilter.asStateFlow()

    private val _wizardYearFilter = MutableStateFlow("2026")
    val wizardYearFilter: StateFlow<String> = _wizardYearFilter.asStateFlow()

    private val _wizardCategoryTypeFilter = MutableStateFlow("ALLE")
    val wizardCategoryTypeFilter: StateFlow<String> = _wizardCategoryTypeFilter.asStateFlow()

    private val _wizardExcludeAlreadyExported = MutableStateFlow(true)
    val wizardExcludeAlreadyExported: StateFlow<Boolean> = _wizardExcludeAlreadyExported.asStateFlow()

    private val _wizardAllowUnverifiedOverride = MutableStateFlow(false)
    val wizardAllowUnverifiedOverride: StateFlow<Boolean> = _wizardAllowUnverifiedOverride.asStateFlow()

    private val _wizardTargetFormat = MutableStateFlow("FULL_ZIP") // FULL_ZIP, EXTF_CSV, CONTROL_CSV
    val wizardTargetFormat: StateFlow<String> = _wizardTargetFormat.asStateFlow()

    fun setWizardFilters(
        unit: String = _wizardUnitFilter.value,
        year: String = _wizardYearFilter.value,
        categoryType: String = _wizardCategoryTypeFilter.value,
        excludeExported: Boolean = _wizardExcludeAlreadyExported.value,
        allowUnverified: Boolean = _wizardAllowUnverifiedOverride.value,
        targetFormat: String = _wizardTargetFormat.value
    ) {
        _wizardUnitFilter.value = unit
        _wizardYearFilter.value = year
        _wizardCategoryTypeFilter.value = categoryType
        _wizardExcludeAlreadyExported.value = excludeExported
        _wizardAllowUnverifiedOverride.value = allowUnverified
        _wizardTargetFormat.value = targetFormat
        recalculateWizardStepData()
    }

    // Computed Wizard Data
    private val _wizardMappedRecords = MutableStateFlow<List<com.example.data.BookingRecord>>(emptyList())
    val wizardMappedRecords: StateFlow<List<com.example.data.BookingRecord>> = _wizardMappedRecords.asStateFlow()

    private val _wizardExcludedReceipts = MutableStateFlow<List<Receipt>>(emptyList())
    val wizardExcludedReceipts: StateFlow<List<Receipt>> = _wizardExcludedReceipts.asStateFlow()

    private val _wizardValidationReport = MutableStateFlow<com.example.util.ValidationReport?>(null)
    val wizardValidationReport: StateFlow<com.example.util.ValidationReport?> = _wizardValidationReport.asStateFlow()

    private val _lastExportResult = MutableStateFlow<com.example.util.AdvisorPackageResult?>(null)
    val lastExportResult: StateFlow<com.example.util.AdvisorPackageResult?> = _lastExportResult.asStateFlow()

    fun recalculateWizardStepData() {
        val allRecs = receipts.value
        val profile = _activeDatevProfile.value
        val unitFilter = _wizardUnitFilter.value
        val yearFilter = _wizardYearFilter.value
        val typeFilter = _wizardCategoryTypeFilter.value
        val excludeExported = _wizardExcludeAlreadyExported.value

        val included = mutableListOf<Receipt>()
        val excluded = mutableListOf<Receipt>()

        allRecs.forEach { r ->
            var keep = true

            if (unitFilter != "ALLE" && r.wohneinheit != unitFilter) {
                keep = false
            }
            if (yearFilter != "ALLE" && !r.datum.startsWith(yearFilter)) {
                keep = false
            }
            if (typeFilter == "EINNAHMEN" && (!r.hauptkategorie.contains("Einnahmen", true) && !r.hauptkategorie.contains("Miete", true))) {
                keep = false
            }
            if (typeFilter == "AUSGABEN" && (r.hauptkategorie.contains("Einnahmen", true) || r.hauptkategorie.contains("Miete", true))) {
                keep = false
            }
            if (excludeExported && r.exportStatus == "EXPORTIERT") {
                keep = false
            }

            if (keep) {
                included.add(r)
            } else {
                excluded.add(r)
            }
        }

        val bookingRecords = included.flatMap { r ->
            com.example.util.DatevMappingService.mapReceiptToBookingRecords(r, profile)
        }

        val report = com.example.util.BookingValidationService.validateRecords(
            records = bookingRecords,
            profile = profile,
            // DATEV packages must never contain unverified accounting proposals.
            allowUnverifiedExport = false
        )

        _wizardMappedRecords.value = bookingRecords
        _wizardExcludedReceipts.value = excluded
        _wizardValidationReport.value = report
    }

    fun executeWizardExport(context: Context): com.example.util.AdvisorPackageResult? {
        recalculateWizardStepData()
        val records = _wizardMappedRecords.value
        val excluded = _wizardExcludedReceipts.value
        val profile = _activeDatevProfile.value
        val report = _wizardValidationReport.value ?: return null
        if (!report.isValidForExport || records.isEmpty()) {
            Log.w("ReceiptViewModel", "DATEV export blocked by validation policy")
            return null
        }

        val packageResult = com.example.util.AdvisorPackageBuilder.buildPackage(
            context = context,
            records = records,
            excludedReceipts = excluded,
            profile = profile,
            validationReport = report,
            periodSummary = _wizardYearFilter.value
        )

        _lastExportResult.value = packageResult

        // Audit Run Record
        viewModelScope.launch {
            val auditRun = com.example.data.ExportAuditRun(
                exportlaufId = packageResult.exportId,
                timestamp = System.currentTimeMillis(),
                propertyName = profile.profileName,
                periodStart = "${_wizardYearFilter.value}-01-01",
                periodEnd = "${_wizardYearFilter.value}-12-31",
                filterSummary = "Objekt: ${profile.profileName}, Wohneinheit: ${_wizardUnitFilter.value}, Typ: ${_wizardCategoryTypeFilter.value}",
                exportierteReceiptIdsJson = "[]",
                kanzleiprofilNameVersion = "${profile.profileName} v${profile.version}",
                zipFileName = packageResult.zipFile.name,
                zipFileSizeBytes = packageResult.zipFile.length(),
                zipSha256 = packageResult.sha256Checksum,
                status = "SUCCESS",
                totalAmount = packageResult.totalAmountEur,
                bookingCount = packageResult.totalRecords,
                warningsCount = packageResult.warningsCount,
                logMessage = "Erfolgreich exportiert mit ${packageResult.totalRecords} Buchungssätzen."
            )

            repository.insertAuditRun(auditRun)

            // Update receipt statuses to EXPORTIERT
            val distinctReceiptIds = records.map { it.receiptId }.distinct()
            val currentList = receipts.value.toMutableList()
            val updatedList = currentList.map { r ->
                if (distinctReceiptIds.contains(r.id)) {
                    r.copy(exportStatus = "EXPORTIERT", exportlaufId = packageResult.exportId)
                } else {
                    r
                }
            }
            repository.insertAll(updatedList)
        }

        _wizardStep.value = 6
        return packageResult
    }

    // DATEV Export State
    private val _exportWithAttachments = MutableStateFlow(false)
    val exportWithAttachments: StateFlow<Boolean> = _exportWithAttachments.asStateFlow()
    private val _exportChartType = MutableStateFlow("SKR03")
    val exportChartType: StateFlow<String> = _exportChartType.asStateFlow()

    fun setExportOptions(withAttachments: Boolean, chartType: String) {
        _exportWithAttachments.value = withAttachments
        _exportChartType.value = chartType
    }

    fun exportToDatev(
        receipts: List<Receipt>, 
        context: Context, 
        withAttachments: Boolean, 
        chartType: String = "SKR03",
        format: String = "ZIP", // "ZIP", "CSV", "XML", or "PDF"
        beraterNummer: String = "1111111",
        mandantenNummer: String = "11111",
        mandantenName: String = "Sergej Gerweck",
        wirtschaftsjahr: Int = 2025
    ): File? {
        val meta = propertyMetadata.value
        val metaName = meta?.name ?: ""
        val propName = if (metaName.isNotEmpty()) metaName else "MFH Sulzerstraße"
        val propShort = if (metaName.isNotEmpty()) metaName.take(15) else "MFH Sulz"

        val config = com.example.util.DatevConfig(
            beraterNummer = beraterNummer.ifEmpty { "1111111" },
            mandantenNummer = mandantenNummer.ifEmpty { "11111" },
            mandantenName = mandantenName.ifEmpty { "Sergej Gerweck" },
            wirtschaftsjahr = wirtschaftsjahr,
            chartType = chartType,
            propertyName = propName,
            propertyShort = propShort
        )

        return try {
            when (format) {
                "ZIP" -> {
                    com.example.util.DatevExporter.createDatevZipPackage(context, receipts, config)
                }
                "CSV" -> {
                    if (withAttachments) {
                        com.example.util.DatevExporter.createDatevZipPackage(context, receipts, config)
                    } else {
                        val csvContent = com.example.util.DatevExporter.generateBuchungsstapelCsv(receipts, config)
                        val csvFile = File(context.cacheDir, "EXTF_Buchungsstapel_${wirtschaftsjahr}.csv")
                        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
                        java.io.FileOutputStream(csvFile).use { fos ->
                            fos.write(bom)
                            fos.write(csvContent.toByteArray(Charsets.UTF_8))
                        }
                        csvFile
                    }
                }
                "XML" -> {
                    val xmlContent = com.example.util.DatevExporter.generateDocumentXml(receipts, config)
                    val xmlFile = File(context.cacheDir, "document.xml")
                    xmlFile.writeText(xmlContent, Charsets.UTF_8)
                    xmlFile
                }
                else -> { // "PDF"
                    if (withAttachments) {
                        com.example.util.DatevExporter.createDatevZipPackage(context, receipts, config)
                    } else {
                        val pdfFile = File(context.cacheDir, "datev_export.pdf")
                        val document = android.graphics.pdf.PdfDocument()
                        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
                        val page = document.startPage(pageInfo)
                        val canvas = page.canvas

                        val titlePaint = android.graphics.Paint().apply {
                            color = 0xFF0F172A.toInt()
                            textSize = 14f
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                            isAntiAlias = true
                        }
                        val textPaint = android.graphics.Paint().apply {
                            color = 0xFF334155.toInt()
                            textSize = 9f
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
                            isAntiAlias = true
                        }

                        canvas.drawText("DATEV Export ($chartType - $wirtschaftsjahr)", 40f, 50f, titlePaint)
                        var yPos = 80f
                        receipts.forEach { r ->
                            if (yPos <= 800f) {
                                canvas.drawText("${r.datum} | ${r.aussteller} | ${r.kontoNr} | ${r.bruttobetrag} EUR | ${r.beschreibung}", 40f, yPos, textPaint)
                                yPos += 16f
                            }
                        }
                        document.finishPage(page)
                        java.io.FileOutputStream(pdfFile).use { fos -> document.writeTo(fos) }
                        document.close()
                        pdfFile
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ReceiptViewModel", "Error exporting to DATEV", e)
            null
        }
    }

    fun exportToPdf(context: Context, taxYear: Int, includeTaxAdvisorSummary: Boolean = false): File? {
        val currentReceipts = receipts.value
        val currentMeta = propertyMetadata.value
        return PdfExporter.exportReceiptsToPdf(context, taxYear, currentReceipts, currentMeta, includeTaxAdvisorSummary)
    }

    // Preloaded templates for mock scanning
    val mockTemplates = listOf(
        MockReceiptTemplate(
            title = "OBI Baumarkt (Farbe & Malerzubehör)",
            filename = "obi_farbe.png",
            text = """
                OBI BAUMARKT MUSTERSTADT
                Datum: 2025-10-15  Uhrzeit: 14:22
                Kassennummer: 04  Bon-Nr: 99812
                --------------------------------------
                1x ALPINA WEISS 10L          49.99 EUR
                2x MALERSET PROFI            18.50 EUR  (37.00 EUR)
                3x ABDECKFOLIE 20QM           3.50 EUR  (10.50 EUR)
                1x SCHLEIFPAPIER SET          8.90 EUR
                --------------------------------------
                SUMME                        106.39 EUR
                Gegeben Bar                 110.00 EUR
                Rückgeld                      3.61 EUR
                Vielen Dank für Ihren Einkauf!
            """.trimIndent()
        ),
        MockReceiptTemplate(
            title = "Hornbach (Laminat & Trittschall)",
            filename = "hornbach_boden.png",
            text = """
                HORNBACH Baumarkt AG
                Musterstrasse 42, 12345 Musterstadt
                Datum: 2025-11-08  Uhrzeit: 10:15
                --------------------------------------
                40x LAMINAT EICHE NATUR   14.95 EUR/QM  (598.00 EUR)
                5x TRITTSCHALLDAEMMUNG    19.99 EUR     (99.95 EUR)
                8x SOCKELLEISTE 2M         6.50 EUR     (52.00 EUR)
                1x MONTAGEKLEBER PROFI     8.50 EUR
                --------------------------------------
                NETTO-BETRAG                637.35 EUR
                MwSt. 19%                   121.10 EUR
                GESAMT-BETRAG               758.45 EUR
                GEZAHLT MIT EC-KARTE
            """.trimIndent()
        ),
        MockReceiptTemplate(
            title = "Bauhaus (Duschkabine Sanitär)",
            filename = "bauhaus_dusche.png",
            text = """
                BAUHAUS GMBH & CO. KG
                Musterstadt West
                Datum: 2025-12-05  Uhrzeit: 16:45
                --------------------------------------
                1x DUSCHKABINE GLAS         649.00 EUR
                1x THERMOSTATBATTERIE DUSCHE 189.00 EUR
                2x SILIKON KLAR 310ML        12.50 EUR  (25.00 EUR)
                2x FLIESENKLEBER 25KG        13.50 EUR  (27.00 EUR)
                --------------------------------------
                SUMME                       890.00 EUR
                KREDIKARTENZAHLUNG MAESTRO
            """.trimIndent()
        ),
        MockReceiptTemplate(
            title = "Notar Dr. Müller (Kaufvertrag)",
            filename = "notar_kaufvertrag.png",
            text = """
                Notariat Dr. Joachim Müller
                Kaiserstraße 10, 12345 Musterstadt
                Rechnung Nr: RE-2025-1008
                Datum: 2025-10-05  Uhrzeit: 11:30
                --------------------------------------
                Kostenrechnung gem. GNotKG
                Beurkundung Kaufvertrag vom 01.10.2025
                Geschäftswert: 250.000,00 EUR
                
                1. Gebühr KV Nr. 21100 (2.0)     2.150,00 EUR
                2. Schreibauslagen                   35,00 EUR
                3. Post- und Tel.-Gebühren           20,00 EUR
                --------------------------------------
                Netto-Kosten                 2.205,00 EUR
                MwSt. 19%                      418.95 EUR
                RECHNUNGSBETRAG              2.623.95 EUR
                Zahlbar innerhalb von 14 Tagen.
            """.trimIndent()
        ),
        MockReceiptTemplate(
            title = "Notar Dr. Müller (Grundschuld)",
            filename = "notar_grundschuld.png",
            text = """
                Notariat Dr. Joachim Müller
                Kaiserstraße 10, 12345 Musterstadt
                Rechnung Nr: RE-2025-1014
                Datum: 2025-10-12  Uhrzeit: 14:15
                --------------------------------------
                Kostenrechnung gem. GNotKG
                Beurkundung Grundschuldbestellung
                Geschäftswert: 200.000,00 EUR
                
                1. Gebühr KV Nr. 21200 (1.0)       580,00 EUR
                2. Nebenauslagen                     15,00 EUR
                --------------------------------------
                Netto                        595,00 EUR
                MwSt. 19%                    113.05 EUR
                RECHNUNGSBETRAG              708.05 EUR
                Grundschuldbestellung für Darlehen Sparkasse.
            """.trimIndent()
        ),
        MockReceiptTemplate(
            title = "Aral Tankstelle (Fahrtenbuch)",
            filename = "aral_tankbeleg.png",
            text = """
                Aral Tankstelle GmbH
                Hauptstrasse 100, 12345 Musterstadt
                Datum: 2025-10-15  Uhrzeit: 13:50
                --------------------------------------
                Super E10  45.20 Liter * 1.769 EUR
                GESAMT-BETRAG                79.96 EUR
                Enthaltene MwSt. 19%         12.77 EUR
                Vielen Dank für Ihren Besuch!
            """.trimIndent()
        ),
        MockReceiptTemplate(
            title = "IKEA Musterstadt (Küche Sanierung)",
            filename = "ikea_kueche.png",
            text = """
                IKEA DEUTSCHLAND GMBH
                Datum: 2026-01-10 Uhrzeit: 12:45
                --------------------------------------
                METOD KORPUS SCHRANK         85.00 EUR
                RINGHULT FRONT WEISS        45.00 EUR
                MAXIMERA SCHUBLADE HOCH     60.00 EUR
                EKBACKEN ARBEITSPLATTE      79.00 EUR
                --------------------------------------
                SUMME                       269.00 EUR
                KARTENZAHLUNG (PIN)
            """.trimIndent()
        )
    )

    // Anlage V Checklist Check
    val anlageVCompleteness: StateFlow<Map<String, Boolean>> = receipts.combine(propertyMetadata) { list, meta ->
        val requiredCategories = mapOf(
            "Grundsteuer" to "Sonstige Ausgaben",
            "Gebäudeversicherung" to "Finanzierung, Kredite & Versicherungen",
            "Instandhaltung/Sanierung" to "Renovierungs- / Reparaturkosten & Investitionen",
            "Schuldzinsen" to "Finanzierung, Kredite & Versicherungen",
            "Verwaltungskosten" to "Sonstige Ausgaben"
        )
        requiredCategories.mapValues { (_, category) ->
            list.any { it.hauptkategorie == category }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    fun setScreen(screen: AppScreen) {
        _currentScreen.value = screen
        _scanState.value = ScanUiState.Idle
    }

    // Trigger Gemini Receipt Analysis
    fun analyzeReceipt(text: String, bitmap: Bitmap? = null, bitmaps: List<Bitmap>? = null) {
        viewModelScope.launch {
            _scanState.value = ScanUiState.Loading
            try {
                // Determine all bitmaps to persist
                val allBitmaps = mutableListOf<Bitmap>()
                bitmap?.let { allBitmaps.add(it) }
                bitmaps?.let { allBitmaps.addAll(it) }

                // Save to local files in filesDir
                val savedPaths = if (allBitmaps.isNotEmpty()) {
                    val paths = mutableListOf<String>()
                    val context = getApplication<Application>()
                    allBitmaps.forEachIndexed { index, b ->
                        try {
                            val file = java.io.File(context.filesDir, "receipt_page_${System.currentTimeMillis()}_$index.jpg")
                            file.outputStream().use { out ->
                                // Scale down if bitmap resolution is excessively large (e.g. > 1600px)
                                val maxDim = 1600
                                val scaledBitmap = if (b.width > maxDim || b.height > maxDim) {
                                    val scale = maxDim.toFloat() / Math.max(b.width, b.height)
                                    Bitmap.createScaledBitmap(b, (b.width * scale).toInt(), (b.height * scale).toInt(), true)
                                } else {
                                    b
                                }
                                // Compress as JPEG at 82% quality for high readability under 300KB
                                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
                                if (scaledBitmap != b) {
                                    scaledBitmap.recycle()
                                }
                            }
                            paths.add(file.absolutePath)
                        } catch (e: Exception) {
                            Log.e("ReceiptViewModel", "Error saving bitmap", e)
                        }
                    }
                    paths.joinToString(",")
                } else {
                    ""
                }

                val learnedContext = getUserLearnedRulesPromptContext()
                val result = com.example.api.GeminiClient.analyzeReceipt(
                    receiptText = text,
                    bitmap = bitmap,
                    bitmaps = bitmaps,
                    userLearnedRulesContext = learnedContext
                )
                if (result != null) {
                    _scanState.value = ScanUiState.Success(result, savedPaths)
                } else {
                    _scanState.value = ScanUiState.Error("Fehler bei der Belegs-Extraktion. Bitte versuche es erneut.")
                }
            } catch (e: com.example.api.GeminiAnalysisException) {
                Log.e("ReceiptViewModel", "Gemini analysis custom exception caught: category=${e.category}, code=${e.errorCode}", e)
                _scanState.value = ScanUiState.Error(e.userMessage)
            } catch (e: Exception) {
                Log.e("ReceiptViewModel", "Unexpected exception during Gemini analysis", e)
                _scanState.value = ScanUiState.Error("Netzwerkfehler oder unerwartetes Problem: ${e.localizedMessage}")
            }
        }
    }

    // Save extracted receipt to database
    fun saveReceipt(
        aussteller: String,
        datum: String,
        uhrzeit: String,
        bruttobetrag: Double,
        hauptkategorie: String,
        unterkategorie: String,
        kontoNr: String,
        beschreibung: String,
        isEigenleistung: Boolean,
        imageUrl: String = "",
        wohneinheit: String = "",
        mieter: String = "",
        positionenJson: String = ""
    ) {
        viewModelScope.launch {
            // Learn rule automatically for KI adaptive memory
            learnVendorRule(aussteller, hauptkategorie, unterkategorie, kontoNr, wohneinheit)

            val newReceipt = Receipt(
                aussteller = aussteller,
                datum = datum,
                uhrzeit = uhrzeit,
                bruttobetrag = bruttobetrag,
                hauptkategorie = hauptkategorie,
                unterkategorie = unterkategorie,
                kontoNr = kontoNr,
                beschreibung = beschreibung,
                isEigenleistungSanierung = isEigenleistung,
                imageUrl = imageUrl,
                wohneinheit = wohneinheit,
                mieter = mieter,
                positionenJson = positionenJson
            )
            val newId = repository.insert(newReceipt)
            val savedReceipt = newReceipt.copy(id = newId.toInt())

            if (FirestoreService.isCloudActive()) {
                FirestoreService.saveReceipt(savedReceipt)
            }
            
            // Auto drive backup if enabled
            if (_isDriveConnected.value && _autoDriveBackup.value) {
                uploadReceiptToDriveInternal(savedReceipt)
            }

            _scanState.value = ScanUiState.Idle
            _currentScreen.value = AppScreen.RECEIPTS_LIST
        }
    }

    // Update an existing receipt in database
    fun updateReceipt(receipt: Receipt) {
        viewModelScope.launch {
            // Learn rule automatically on user corrections
            learnVendorRule(
                receipt.aussteller,
                receipt.hauptkategorie,
                receipt.unterkategorie,
                receipt.kontoNr,
                receipt.wohneinheit
            )

            repository.insert(receipt)

            if (FirestoreService.isCloudActive()) {
                FirestoreService.saveReceipt(receipt)
            }
            
            // Auto drive backup if enabled
            if (_isDriveConnected.value && _autoDriveBackup.value && !receipt.isArchivedToDrive) {
                uploadReceiptToDriveInternal(receipt)
            }
        }
    }

    // Insert a new receipt quietly (without changing screen or resetting state)
    fun insertReceiptQuietly(receipt: Receipt) {
        viewModelScope.launch {
            repository.insert(receipt)

            if (FirestoreService.isCloudActive()) {
                FirestoreService.saveReceipt(receipt)
            }
        }
    }

    fun updatePropertyMetadata(metadata: PropertyMetadata) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updatePropertyMetadata(metadata)
            _wohneinheitenStatus.value = getWohneinheitenFromPrefs(metadata.wohneinheiten)
        }
    }

    fun deleteReceipt(id: Int, deletedBy: String = "LocalUser", reason: String = "Vom Nutzer gelöscht") {
        viewModelScope.launch(Dispatchers.IO) {
            val receipt = repository.getReceiptById(id) ?: return@launch
            val email = _googleAccountEmail.value
            val isDriveActive = _isDriveConnected.value && !email.isNullOrBlank()

            if (!isDriveActive) {
                // Offline deletion: mark local status as DELETE_PENDING
                val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                val deletionUuid = java.util.UUID.randomUUID().toString()
                repository.softDelete(
                    id = id,
                    status = "DELETE_PENDING",
                    deletedAt = now,
                    deletedBy = deletedBy,
                    deletionId = deletionUuid,
                    reason = reason
                )
                if (FirestoreService.isCloudActive()) {
                    FirestoreService.deleteReceipt(id)
                }
            } else {
                try {
                    val token = getValidToken(email!!)
                    val initResult = drivePersistenceRepository.initializeDriveStorage(token)
                    val config = when (initResult) {
                        is com.example.data.DriveInitializationResult.SuccessCreatedNew -> initResult.config
                        is com.example.data.DriveInitializationResult.SuccessLoadedExisting -> initResult.config
                        is com.example.data.DriveInitializationResult.Failure -> null
                    }
                    val result = drivePersistenceRepository.deleteReceiptWithSync(
                        accessToken = token,
                        config = config,
                        receipt = receipt,
                        deletedBy = deletedBy,
                        deletionReason = reason
                    )
                    if (FirestoreService.isCloudActive()) {
                        FirestoreService.deleteReceipt(id)
                    }
                } catch (e: Exception) {
                    Log.e("ReceiptViewModel", "Error deleting receipt with sync, fallback to offline pending", e)
                    val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                    val deletionUuid = java.util.UUID.randomUUID().toString()
                    repository.softDelete(
                        id = id,
                        status = "DELETE_PENDING",
                        deletedAt = now,
                        deletedBy = deletedBy,
                        deletionId = deletionUuid,
                        reason = reason
                    )
                }
            }
        }
    }

    fun restoreReceipt(receipt: Receipt) {
        viewModelScope.launch(Dispatchers.IO) {
            val email = _googleAccountEmail.value
            val isDriveActive = _isDriveConnected.value && !email.isNullOrBlank()
            if (!isDriveActive) {
                // Restore locally
                repository.softDelete(
                    id = receipt.id,
                    status = "ACTIVE",
                    deletedAt = "",
                    deletedBy = "",
                    deletionId = "",
                    reason = ""
                )
            } else {
                try {
                    val token = getValidToken(email!!)
                    val initResult = drivePersistenceRepository.initializeDriveStorage(token)
                    val config = when (initResult) {
                        is com.example.data.DriveInitializationResult.SuccessCreatedNew -> initResult.config
                        is com.example.data.DriveInitializationResult.SuccessLoadedExisting -> initResult.config
                        is com.example.data.DriveInitializationResult.Failure -> null
                    }
                    drivePersistenceRepository.restoreDeletedReceipt(token, config, receipt)
                } catch (e: Exception) {
                    Log.e("ReceiptViewModel", "Error restoring receipt with sync", e)
                    repository.softDelete(
                        id = receipt.id,
                        status = "ACTIVE",
                        deletedAt = "",
                        deletedBy = "",
                        deletionId = "",
                        reason = ""
                    )
                }
            }
        }
    }

    fun permanentlyDeleteReceipt(receipt: Receipt, onComplete: (com.example.data.PermanentDeleteResult) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val email = _googleAccountEmail.value
            val isDriveActive = _isDriveConnected.value && !email.isNullOrBlank()
            val result = if (!isDriveActive) {
                repository.deleteById(receipt.id)
                com.example.data.PermanentDeleteResult.Success
            } else {
                try {
                    val token = getValidToken(email!!)
                    val initResult = drivePersistenceRepository.initializeDriveStorage(token)
                    val config = when (initResult) {
                        is com.example.data.DriveInitializationResult.SuccessCreatedNew -> initResult.config
                        is com.example.data.DriveInitializationResult.SuccessLoadedExisting -> initResult.config
                        is com.example.data.DriveInitializationResult.Failure -> null
                    }
                    drivePersistenceRepository.permanentlyDeleteReceipt(token, config, receipt)
                } catch (e: Exception) {
                    Log.e("ReceiptViewModel", "Error permanently deleting receipt with sync", e)
                    com.example.data.PermanentDeleteResult.Error("Permanentes Löschen fehlgeschlagen: ${e.message}")
                }
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onComplete(result)
            }
        }
    }

    private suspend fun createDuplicateCleanupController(): Triple<
        DuplicateCleanupController,
        List<com.example.data.ReceiptIndexEntry>,
        Map<String, com.example.data.ReceiptTombstone>
    > {
        val email = _googleAccountEmail.value
            ?: error("Google Drive ist nicht verbunden.")
        check(_isDriveConnected.value) { "Google Drive ist nicht verbunden." }
        val token = getValidToken(email)
        val config = when (val init = drivePersistenceRepository.initializeDriveStorage(token)) {
            is com.example.data.DriveInitializationResult.SuccessCreatedNew -> init.config
            is com.example.data.DriveInitializationResult.SuccessLoadedExisting -> init.config
            is com.example.data.DriveInitializationResult.Failure ->
                error(init.error)
        }
        val referenceMutator = com.example.data.DrivePersistenceDuplicateReferenceMutator(
            repository = drivePersistenceRepository,
            accessTokenProvider = { token },
            configProvider = { config }
        )
        val feature = com.example.data.DuplicateCleanupFeature(
            context = getApplication(),
            receiptRepository = repository,
            accessTokenProvider = { token },
            referenceMutator = referenceMutator,
            now = {
                java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss",
                    java.util.Locale.getDefault()
                ).format(java.util.Date())
            }
        )
        duplicateCleanupFeature = feature
        val controller = DuplicateCleanupController(
            DuplicateCleanupFeatureUseCase(feature)
        )
        duplicateCleanupController = controller
        return Triple(
            controller,
            drivePersistenceRepository.getReceiptIndexFromDrive(token, config),
            drivePersistenceRepository.getAllTombstonesFromDrive(token, config)
        )
    }

    fun loadPendingDuplicateCleanupOperations() {
        viewModelScope.launch(Dispatchers.IO) {
            val store = com.example.data.SharedPreferencesDuplicateCleanupJournalStore(
                getApplication()
            )
            val pending = store.listOperationIds().filterTo(mutableSetOf()) { operationId ->
                store.load(operationId)?.phase !=
                    com.example.data.DuplicateCleanupPhase.COMPLETED
            }
            if (pending.isNotEmpty()) {
                _duplicateCleanupState.value =
                    DuplicateCleanupUiState.PendingOperations(pending)
            }
        }
    }

    fun resumeDuplicateCleanupOperation(operationId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _duplicateCleanupState.value = DuplicateCleanupUiState.Loading
            try {
                val (controller, _, _) = createDuplicateCleanupController()
                controller.resume(operationId)
                _duplicateCleanupState.value = controller.state
            } catch (exception: Exception) {
                _duplicateCleanupState.value = DuplicateCleanupUiState.Failed(
                    exception.message ?: "Bereinigung konnte nicht fortgesetzt werden."
                )
            }
        }
    }

    fun analyzeReceiptDuplicates() {
        viewModelScope.launch(Dispatchers.IO) {
            _duplicateCleanupState.value = DuplicateCleanupUiState.Loading
            try {
                val (controller, indexEntries, tombstones) =
                    createDuplicateCleanupController()
                controller.load(indexEntries, tombstones)
                _duplicateCleanupState.value = controller.state
            } catch (exception: Exception) {
                _duplicateCleanupState.value = DuplicateCleanupUiState.Failed(
                    exception.message ?: "Dubletten konnten nicht geprüft werden."
                )
            }
        }
    }

    fun requestDuplicateMerge(preview: com.example.data.DuplicateGroupPreview) {
        viewModelScope.launch(Dispatchers.IO) {
            val controller = duplicateCleanupController ?: return@launch
            controller.requestMerge(preview)
            _duplicateCleanupState.value = controller.state
        }
    }

    fun confirmDuplicateMerge() {
        viewModelScope.launch(Dispatchers.IO) {
            val controller = duplicateCleanupController ?: return@launch
            controller.confirmMerge()
            _duplicateCleanupState.value = controller.state
        }
    }

    fun requestWholeDuplicateGroupDeletion(
        preview: com.example.data.DuplicateGroupPreview
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val controller = duplicateCleanupController ?: return@launch
            controller.requestWholeGroupDeletion(preview)
            _duplicateCleanupState.value = controller.state
        }
    }

    fun setWholeDuplicateGroupConfirmations(first: Boolean, second: Boolean) {
        val controller = duplicateCleanupController ?: return
        controller.setWholeGroupConfirmations(first, second)
        _duplicateCleanupState.value = controller.state
    }

    fun confirmWholeDuplicateGroupDeletion() {
        viewModelScope.launch(Dispatchers.IO) {
            val controller = duplicateCleanupController ?: return@launch
            controller.confirmWholeGroupDeletion()
            _duplicateCleanupState.value = controller.state
        }
    }

    fun dismissDuplicateCleanupState() {
        duplicateCleanupController?.cancel()
        _duplicateCleanupState.value = DuplicateCleanupUiState.Idle
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            repository.resetDefaults()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            database.receiptDao().deleteAll()
        }
    }

    fun syncWithCloud() {
        if (!FirestoreService.isCloudActive()) return
        if (FirestoreService.getCurrentUser() == null) {
            _driveSyncStatus.value = "Cloud-Modus lokal: Bitte anmelden, um Daten zu sichern."
            return
        }
        viewModelScope.launch {
            _driveSyncStatus.value = "Synchronisiere mit Firestore..."
            try {
                // 1. Sync Wohneinheiten
                val remoteUnits = FirestoreService.getWohneinheiten()
                if (remoteUnits != null && remoteUnits.isNotEmpty()) {
                    val unitPrefs = getApplication<Application>().getSharedPreferences("wohneinheiten_prefs", Context.MODE_PRIVATE)
                    val editor = unitPrefs.edit()
                    remoteUnits.forEach { u ->
                        editor.putString("unit_status_${u.name}", u.status)
                        editor.putString("unit_label_${u.name}", u.label)
                        editor.putString("unit_mieter_${u.name}", u.mieter)
                        editor.putFloat("unit_rent_${u.name}", u.kaltmiete.toFloat())
                        editor.putFloat("unit_area_${u.name}", u.wohnflaeche.toFloat())
                    }
                    editor.apply()
                    _wohneinheitenStatus.value = remoteUnits
                    Log.i("ReceiptViewModel", "Wohneinheiten aus Firestore geladen.")
                } else {
                    // Upload current local units to Firestore if remote is empty
                    val localUnits = _wohneinheitenStatus.value
                    localUnits.forEach { u ->
                        FirestoreService.saveWohneinheit(u)
                    }
                }

                // 2. Sync Receipts
                val remoteReceipts = FirestoreService.getReceipts()
                if (remoteReceipts != null) {
                    val localReceipts = repository.allReceipts.first()
                    // Insert remote receipts into local DB
                    if (remoteReceipts.isNotEmpty()) {
                        repository.insertAll(remoteReceipts)
                        Log.i("ReceiptViewModel", "Belege aus Firestore geladen.")
                    }
                    
                    // Upload local receipts to Firestore if they don't exist in remote
                    val remoteIds = remoteReceipts.map { it.id }.toSet()
                    localReceipts.forEach { r ->
                        if (!remoteIds.contains(r.id)) {
                            FirestoreService.saveReceipt(r)
                        }
                    }
                }
                _driveSyncStatus.value = "Firestore-Synchronisierung erfolgreich!"
            } catch (e: Exception) {
                Log.e("ReceiptViewModel", "Firestore sync error", e)
                _driveSyncStatus.value = "Fehler bei Firestore-Synchronisierung."
            }
        }
    }

    fun signInWithEmail(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val user = FirestoreService.signIn(email, password)
                if (user != null) {
                    _currentUser.value = user
                    syncWithCloud()
                    onSuccess()
                } else {
                    onError("Anmeldung fehlgeschlagen.")
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Fehler bei der Anmeldung.")
            }
        }
    }

    fun signUpWithEmail(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val user = FirestoreService.signUp(email, password)
                if (user != null) {
                    _currentUser.value = user
                    syncWithCloud()
                    onSuccess()
                } else {
                    onError("Registrierung fehlgeschlagen.")
                }
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "Fehler bei der Registrierung.")
            }
        }
    }

    fun signOutUser() {
        FirestoreService.signOut()
        _currentUser.value = null
    }

    // --- 5 AI FEATURE EXECUTION METHODS ---

    fun runTaxPlausibilityCheck(buildingPurchaseValue: Double = 600000.0) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAnalyzingTaxPlausibility.value = true
            val currentList = receipts.value
            val result = com.example.api.GeminiClient.analyzeTaxPlausibility(
                receipts = currentList,
                buildingPurchaseValue = buildingPurchaseValue
            )
            _taxPlausibilityReport.value = result
            _isAnalyzingTaxPlausibility.value = false
        }
    }

    fun generateTenantUtilityStatement(
        tenantName: String,
        unitName: String,
        sqm: Double,
        totalBuildingSqm: Double = 520.0,
        year: Int = 2025
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isGeneratingUtilityStatement.value = true
            val currentList = receipts.value
            val result = com.example.api.GeminiClient.generateTenantUtilityStatement(
                tenantName = tenantName,
                unitName = unitName,
                sqm = sqm,
                totalBuildingSqm = totalBuildingSqm,
                year = year,
                receipts = currentList
            )
            _tenantUtilityStatement.value = result
            _isGeneratingUtilityStatement.value = false
        }
    }

    fun runRentYieldOptimization(
        propertyLocation: String = "München / Deutschland",
        currentUnitsInfo: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isOptimizingRentYield.value = true
            val currentList = receipts.value
            val unitsText = if (currentUnitsInfo.isNotBlank()) currentUnitsInfo else {
                _wohneinheitenStatus.value.joinToString("\n") { u ->
                    "- ${u.name}: ${u.mieter}, Kaltmiete: ${u.kaltmiete}€, Fläche: ${u.wohnflaeche}m², Status: ${u.status}"
                }
            }
            val result = com.example.api.GeminiClient.optimizeRentAndYield(
                propertyLocation = propertyLocation,
                currentUnitsInfo = unitsText,
                receipts = currentList
            )
            _rentYieldReport.value = result
            _isOptimizingRentYield.value = false
        }
    }

    fun analyzeDamagePhoto(bitmap: android.graphics.Bitmap, userDescription: String = "") {
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
