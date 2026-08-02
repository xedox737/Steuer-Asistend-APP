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
        newOpenAiKey: String
    ): String? {
        return try {
            if (newOpenAiKey.isNotBlank()) {
                AiProviderSettings.storeOpenAiKey(
                    getApplication(),
                    newOpenAiKey.toCharArray()
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
            "Der API-SchlÃ¼ssel konnte auf diesem GerÃ¤t nicht sicher gespeichert werden."
        }
    }

    fun deleteOpenAiKey() {
        _aiProviderState.value = AiProviderSettings.clearOpenAiKey(getApplication())
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
                    notes = "Miete vollstÃ¤ndig eingegangen"
                ),
                com.example.api.BankStatementMatchItem(
                    date = "05.07.2025",
                    counterparty = "Stadtwerke MÃ¼nchen",
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
                    notes = "âš ï¸ Kein Beleg in der App vorhanden! Bitte Kassenzettel hochladen."
                ),
                com.example.api.BankStatementMatchItem(
                    date = "12.07.2025",
                    counterparty = "Malermeister MÃ¼ller",
                    amount = 650.00,
                    isIncome = false,
                    purpose = "Re-Nr 2025-882 Renovierung",
                    status = "MISSING_RECEIPT",
                    notes = "âš ï¸ Abbuchung ohne Beleg! Rechnungsbeleg fehlt fÃ¼r Steuer."
                ),
                com.example.api.BankStatementMatchItem(
                    date = "28.07.2025",
                    counterparty = "Thomas Weber",
                    amount = 350.00,
                    isIncome = true,
                    purpose = "Teilzahlung Miete WE 05 (Soll: 700 â‚¬)",
                    status = "RENT_ARREARS",
                    notes = "ğŸš¨ MietrÃ¼ckstand: 350,00 â‚¬ fehlen fÃ¼r den Monat Juli!"
                )
            ),
            summary = "Bankabgleich ergab 2 fehlende Abbuchungsbelege und 1 MietrÃ¼ckstand. Handlungsbedarf vorliegend."
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

        // Loß½öîÚ$z{-®éÜj×WFFFGWÆ–6FW2Ò×WF&ÆU7FFTfÆ÷r†fÇ6R¢fÂ—46†V6¶–ætÖWFFFGWÆ–6FW3¢7FFTfÆ÷sÄ&ööÆVãâÒö—46†V6¶–ætÖWFFFGWÆ–6FW2æ57FFTfÆ÷r‚ ¢&—fFRfÂöÖWFFFGWÆ–6FTW'&÷"Ò×WF&ÆU7FFTfÆ÷sÅ7G&–æsóâ†çVÆÂ¢fÂÖWFFFGWÆ–6FTW'&÷#¢7FFTfÆ÷sÅ7G&–æsóâÒöÖWFFFGWÆ–6FTW'&÷"æ57FFTfÆ÷r‚ ¢gVâ'VäÖWFFFGWÆ–6FU&W÷'B‚’°¢f–WtÖöFVÅ66÷RæÆVæ6‚„F—7F6†W'2ä”ò’°¢ö—46†V6¶–ætÖWFFFGWÆ–6FW2çfÇVRÒG'VP¢öÖWFFFGWÆ–6FTW'&÷"çfÇVRÒçVÆÀ¢G'’°¢fÂVÖ–ÂÒövöövÆT66÷VçDVÖ–ÂçfÇVP¢–b†VÖ–Âæ—4çVÆÄ÷$V×G’‚’’°¢öÖWFFFGWÆ–6FTW'&÷"çfÇVRÒ$&—GFRÖVÆFRF–6‚§VW'7B&V’vöövÆRG&—fRââ ¢ö—46†V6¶–ætÖWFFFGWÆ–6FW2çfÇVRÒfÇ6P¢&WGW&äÆVæ6€¢Ğ¢fÂFö¶VâÒvWEfÆ–EFö¶Vâ†VÖ–Â¢fÂ6öæf–rÒG&—fUW'6—7FVæ6U&W÷6—F÷'’ævWDG&—fT6öæf–r‡Fö¶Vâ¢–b†6öæf–rÓÒçVÆÂ’°¢öÖWFFFGWÆ–6FTW'&÷"çfÇVRÒ$vöövÆRG&—fRÔ¶öæf–wW&F–öâ¶öæçFRæ–6‡BvVÆFVâvW&FVââ ¢ö—46†V6¶–ætÖWFFFGWÆ–6FW2çfÇVRÒfÇ6P¢&WGW&äÆVæ6€¢Ğ¢fÂ&W÷'BÒG&—fUW'6—7FVæ6U&W÷6—F÷'’ævVæW&FTÖWFFFGWÆ–6FU&W÷'B‡Fö¶VâÂ6öæf–r¢öÖWFFFGWÆ–6FU&W÷'BçfÇVRÒ&W÷'@¢Ò6F6‚†S¢W†6WF–öâ’°¢ÆöræR‚%&V6V—Ef–WtÖöFVÂ"Â$W'&÷"–â'VäÖWFFFGWÆ–6FU&W÷'B"ÂR¢öÖWFFFGWÆ–6FTW'&÷"çfÇVRÒ$fV†ÆW"&V’FW"GV&ÆWGFVç,;ÆgVæs¢G¶RæÖW76vWÒ ¢Òf–æÆÇ’°¢ö—46†V6¶–ætÖWFFFGWÆ–6FW2çfÇVRÒfÇ6P¢Ğ¢Ğ¢Ğ ¢gVâF—6Ö—74ÖWFFFGWÆ–6FU&W÷'B‚’°¢öÖWFFFGWÆ–6FU&W÷'BçfÇVRÒçVÆÀ¢öÖWFFFGWÆ–6FTW'&÷"çfÇVRÒçVÆÀ¢Ğ ¢&—fFRfÂöÖWFFF6ÆVçW&Wf–WrĞ¢×WF&ÆU7FFTfÆ÷sÆ6öÒæW†×ÆRæFFäÖWFFFGWÆ–6FT6ÆVçWÆãóâ†çVÆÂ¢fÂÖWFFF6ÆVçW&Wf–Ws ¢7FFTfÆ÷sÆ6öÒæW†×ÆRæFFäÖWFFFGWÆ–6FT6ÆVçWÆãóâÒöÖWFFF6ÆVçW&Wf–Wræ57FFTfÆ÷r‚ ¢&—fFRfÂöÖWFFF6ÆVçW&W7VÇBĞ¢×WF&ÆU7FFTfÆ÷sÆ6öÒæW†×ÆRæFFäÖWFFFGWÆ–6FT6ÆVçW&W7VÇCóâ†çVÆÂ¢fÂÖWFFF6ÆVçW&W7VÇC ¢7FFTfÆ÷sÆ6öÒæW†×ÆRæFFäÖWFFFGWÆ–6FT6ÆVçW&W7VÇCóâÒöÖWFFF6ÆVçW&W7VÇBæ57FFTfÆ÷r‚ ¢&—fFRfÂö—46ÆVæ–ætÖWFFFGWÆ–6FW2Ò×WF&ÆU7FFTfÆ÷r†fÇ6R¢fÂ—46ÆVæ–ætÖWFFFGWÆ–6FW3¢7FFTfÆ÷sÄ&ööÆVãâĞ¢ö—46ÆVæ–ætÖWFFFGWÆ–6FW2æ57FFTfÆ÷r‚ ¢gVâ&W&TÖWFFFGWÆ–6FT6ÆVçW†w&÷W¢6öÒæW†×ÆRæFFäÖWFFFGWÆ–6FTw&÷W’°¢G'’°¢öÖWFFF6ÆVçW&Wf–WrçfÇVRĞ¢6öÒæW†×ÆRæFFäÖWFFFGWÆ–6FT6ÆVçWÆææW"çÆâ†w&÷W¢öÖWFFFGWÆ–6FTW'&÷"çfÇVRÒçVÆÀ¢Ò6F6‚†S¢–ÆÆVvÄ&wVÖVçDW†6WF–öâ’°¢öÖWFFFGWÆ–6FTW'&÷"çfÇVRÒRæÖW76vP¢Ğ¢Ğ ¢gVâ6æ6VÄÖWFFFGWÆ–6FT6ÆVçW‚’°¢öÖWFFF6ÆVçW&Wf–WrçfÇVRÒçVÆÀ¢Ğ ¢gVâ6öæf—&ÔÖWFFFGWÆ–6FT6ÆVçW‚’°¢fÂÆâÒöÖWFFF6ÆVçW&Wf–WrçfÇVRó¢&WGW&à¢f–WtÖöFVÅ66÷RæÆVæ6‚„F—7F6†W'2ä”ò’°¢ö—46ÆVæ–ætÖWFFFGWÆ–6FW2çfÇVRÒG'VP¢öÖWFFFGWÆ–6FTW'&÷"çfÇVRÒçVÆÀ¢G'’°¢fÂVÖ–ÂÒövöövÆT66÷VçDVÖ–ÂçfÇVP¢ó¢F‡&÷r–ÆÆVvÅ7FFTW†6WF–öâ‚$&—GFRÖVÆFRF–6‚§VW'7B&V’vöövÆRG&—fRââ"¢fÂFö¶VâÒvWEfÆ–EFö¶Vâ†VÖ–Â¢fÂW†V7WF÷"Ò6öÒæW†×ÆRæFFäÖWFFFGWÆ–6FT6ÆVçWW†V7WF÷"€¢FVÆWFW"Ò6öÒæW†×ÆRæFFäÖWFFFGWÆ–6FTf–ÆTFVÆWFW"²f–ÆT–BÓà¢vöövÆTG&—fT6Æ–VçBæFVÆWFTf–ÆR‡Fö¶VâÂf–ÆT–B¢ÒÀ¢VF—BÒ²&W7VÇBÓâW'6—7DÖWFFF6ÆVçWVF—B‡&W7VÇB’Ğ¢¢fÂ&W7VÇBÒW†V7WF÷"æW†V7WFR‡ÆâÂW‡Æ–6—FÇ”6öæf—&ÖVBÒG'VR¢öÖWFFF6ÆVçW&W7VÇBçfÇVRÒ&W7VÇ@¢öÖWFFF6ÆVçW&Wf–WrçfÇVRÒçVÆÀ¢–b‡&W7VÇBæ6ö×ÆWFVB’°¢fÂ6öæf–rÒG&—fUW'6—7FVæ6U&W÷6—F÷'’ævWDG&—fT6öæf–r‡Fö¶Vâ¢–b†6öæf–rÒçVÆÂ’°¢öÖWFFFGWÆ–6FU&W÷'BçfÇVRĞ¢G&—fUW'6—7FVæ6U&W÷6—F÷'’ævVæW&FTÖWFFFGWÆ–6FU&W÷'B‡Fö¶VâÂ6öæf–r¢Ğ¢Ğ¢Ò6F6‚†S¢W†6WF–öâ’°¢ÆöræR‚%&V6V—Ef–WtÖöFVÂ"Â$ÖWFFFGWÆ–6FR6ÆVçWf–ÆVB"ÂR¢öÖWFFFGWÆ–6FTW'&÷"çfÇVRĞ¢$ÖWFFFVâÔGV&ÆWGFVâ¶öæçFVâæ–6‡B6–6†W"&W&V–æ–wBvW&FVã¢G¶RæÖW76vWÒ ¢Òf–æÆÇ’°¢ö—46ÆVæ–ætÖWFFFGWÆ–6FW2çfÇVRÒfÇ6P¢Ğ¢Ğ¢Ğ ¢gVâF—6Ö—74ÖWFFF6ÆVçW&W7VÇB‚’°¢öÖWFFF6ÆVçW&W7VÇBçfÇVRÒçVÆÀ¢Ğ ¢&—fFRgVâW'6—7DÖWFFF6ÆVçWVF—B€¢&W7VÇC¢6öÒæW†×ÆRæFFäÖWFFFGWÆ–6FT6ÆVçW&W7VÇ@¢’°¢fÂ&Vg2ÒvWDÆ–6F–öãÄÆ–6F–öãâ‚’ævWE6†&VE&VfW&Væ6W2€¢&ÖWFFFöGWÆ–6FUö6ÆVçWöVF—B"À¢6öçFW‡BäÔôDUõ$•dDP¢¢fÂVçG'’Ò÷&ræ§6öâä¥4ôäö&¦V7B‚’æÇ’°¢WB‚'F–ÖW7F×"Â7—7FVÒæ7W'&VçEF–ÖTÖ–ÆÆ—2‚’¢WB‚&–çFW&æÄ–B"Â&W7VÇBæ–çFW&æÄ–B¢WB‚&7F—fTÖWFFFf–ÆT–B"Â&W7VÇBæ7F—fTÖWFFFf–ÆT–B¢WB‚&FVÆWFVDÖWFFFf–ÆT–G2"Â÷&ræ§6öâä¥4ôä'&’‡&W7VÇBæFVÆWFVDÖWFFFf–ÆT–G2’¢WB‚&f–ÇW&W2"Â÷&ræ§6öâä¥4ôä'&’‡&W7VÇBæf–ÇW&W2’¢WB‚&6ö×ÆWFVB"Â&W7VÇBæ6ö×ÆWFVB¢Ğ¢fÂW†—7F–ærÒ&Vg2ævWE7G&–ær‚&VçG&–W2"Â%µÒ"’ó¢%µÒ ¢fÂVçG&–W2ÒG'’°¢÷&ræ§6öâä¥4ôä'&’†W†—7F–ær¢Ò6F6‚…ó¢W†6WF–öâ’°¢÷&ræ§6öâä¥4ôä'&’‚¢Ğ¢VçG&–W2çWB†VçG'’¢&Vg2æVF—B‚’çWE7G&–ær‚&VçG&–W2"ÂVçG&–W2çFõ7G&–ær‚’’æÇ’‚¢Ğ ¢gVâ6÷'&V7E&V6V—DÖWFFF†–çFW&æÄ–C¢7G&–ærÂæWtÖ–ÖUG—S¢7G&–ærÂæWtf–ÆVæÖS¢7G&–ær’°¢f–WtÖöFVÅ66÷RæÆVæ6‚„F—7F6†W'2ä”ò’°¢G'’°¢fÂVÖ–ÂÒövöövÆT66÷VçDVÖ–ÂçfÇVRó¢" ¢fÂFö¶VâÒ–b†VÖ–Âæ—4æ÷DV×G’‚’’vWEfÆ–EFö¶Vâ†VÖ–Â’VÇ6R" ¢fÂ6öæf–rÒ–b‡Fö¶Vâæ—4æ÷DV×G’‚’’G&—fUW'6—7FVæ6U&W÷6—F÷'’ævWDG&—fT6öæf–r‡Fö¶Vâ’VÇ6RçVÆÀ ¢fÂ7V66W72Ò–b†6öæf–rÒçVÆÂ’°¢G&—fUW'6—7FVæ6U&W÷6—F÷'’æ6÷'&V7E&V6V—DÖWFFF‡Fö¶VâÂ6öæf–rÂ–çFW&æÄ–BÂæWtÖ–ÖUG—RÂæWtf–ÆVæÖR¢ÒVÇ6R°¢fÂÆö6ÂÒ&W÷6—F÷'’ævWDÆÅ&V6V—G4Æ—7B‚’æf–æB²—Bæ–çFW&æÄ–BÓÒ–çFW&æÄ–BĞ¢–b†Æö6ÂÒçVÆÂ’°¢&W÷6—F÷'’æ–ç6W'B†Æö6Âæ6÷’†÷&–v–æÄÖ–ÖUG—RÒæWtÖ–ÖUG—RÂ7F÷&VDf–ÆVæÖRÒæWtf–ÆVæÖR’¢G'VP¢ÒVÇ6RfÇ6P¢Ğ ¢–b‡7V66W72bbFö¶Vâæ—4æ÷DV×G’‚’bb6öæf–rÒçVÆÂ’°¢fÂWFFVE&W÷'BÒG&—fUW'6—7FVæ6U&W÷6—F÷'’æVF—D÷&–v–æÅ&V6V—G2‡Fö¶VâÂ6öæf–r¢ö÷&–v–æÅ&V6V—DVF—E&W÷'BçfÇVRÒWFFVE&W÷'@¢Ğ¢Ò6F6‚†S¢W†6WF–öâ’°¢ÆöræR‚%&V6V—Ef–WtÖöFVÂ"Â$W'&÷"6÷'&V7F–ær&V6V—BÖWFFF"ÂR¢Ğ¢Ğ¢Ğ ¢gVâfÆ–FFTf–ÆTf÷%&W—"†6öçFW‡C¢6öçFW‡BÂf–ÆS¢f–ÆR“¢f–ÆUfÆ–FF–öå&W7VÇB°¢–b‚f–ÆRæW†—7G2‚’ÇÂf–ÆRæ6å&VB‚’’°¢&WGW&âf–ÆUfÆ–FF–öå&W7VÇB†fÇ6RÂf–ÆRÂ""Â""ÂÂÂ""Â$FFV’W†—7F–W'Bæ–6‡BöFW"—7BVæÆW6&"â"¢Ğ¢fÂÆVæwF‚Òf–ÆRæÆVæwF‚‚¢–b†ÆVæwF‚ÓÒÂ’°¢&WGW&âf–ÆUfÆ–FF–öå&W7VÇB†fÇ6RÂf–ÆRÂ""Â""ÂÂÂ""Â$FFV’—7BÆVW"ƒ'—FW2’â"¢Ğ ¢fÂ†VFW"Òf–ÆRæ–çWE7G&VÒ‚’çW6R²'Vâ²fÂ'VffW"Ò'—FT'&’ƒb“²f"öfg6WBÒ²v†–ÆR†öfg6WBÂ'VffW"ç6—¦R’²fÂ6÷VçBÒ—Bç&VB†'VffW"Âöfg6WBÂ'VffW"ç6—¦RÒöfg6WB“²–b†6÷VçBÂ’'&V³²öfg6WB³Ò6÷VçBÓ²'VffW"æ6÷”öb†öfg6WB’ÒĞ¢f"Ö–ÖUG—RÒ" ¢f"f÷&ÖDæÖRÒ"  ¢–b††VFW"ç6—¦RãÒBbb†VFW"æ6÷”öe&ævRƒÂB’æ6öçFVçDWVÇ2†'—FT'&”öbƒƒ#RÂƒSÂƒCBÂƒCb’’’°¢Ö–ÖUG—RÒ&Æ–6F–öâ÷Fb ¢f÷&ÖDæÖRÒ%Db ¢ÒVÇ6R–b††VFW"ç6—¦RãÒ2bb†VFW%³ÒÓÒ„dbçFô'—FR‚’bb†VFW%³ÒÓÒ„C‚çFô'—FR‚’bb†VFW%³%ÒÓÒ„dbçFô'—FR‚’’°¢Ö–ÖUG—RÒ&–ÖvRö§Vr ¢f÷&ÖDæÖRÒ$¥Tr ¢ÒVÇ6R–b††VFW"ç6—¦RãÒ‚bb†VFW"æ6÷”öe&ævRƒÂ‚’æ6öçFVçDWVÇ2†'—FT'&”öbƒƒƒ’çFô'—FR‚’ÂƒSÂƒDRÂƒCrÂƒBÂƒÂƒÂƒ’’’°¢Ö–ÖUG—RÒ&–ÖvR÷ær ¢f÷&ÖDæÖRÒ%är ¢ÒVÇ6R–b††VFW"ç6—¦RãÒ"bb†VFW"æ6÷”öe&ævRƒÂB’æ6öçFVçDWVÇ2‚%$”db"çFô'—FT'&’‚’’bb†VFW"æ6÷”öe&ævRƒ‚Â"’æ6öçFVçDWVÇ2‚%tT%"çFô'—FT'&’‚’’’°¢Ö–ÖUG—RÒ&–ÖvR÷vV' ¢f÷&ÖDæÖRÒ%tT% ¢ÒVÇ6R°¢&WGW&âf–ÆUfÆ–FF–öå&W7VÇB†fÇ6RÂf–ÆRÂ""Â%Tä´äõtâ"ÂÆVæwF‚Â""Â%Væ|;ÆÇF–vW2FFV–f÷&ÖB„Öv–2'—FW2“¢¶V–âDbÂ¥TrÂäröFW"tT%â"¢Ğ ¢òòfW&–g’&Wf–WrvVæW&F–öà¢G'’°¢–b†f÷&ÖDæÖRÓÒ%Db"’°¢fÂfBÒæG&ö–Bæ÷2å&6VÄf–ÆTFW67&—F÷"æ÷Vâ†f–ÆRÂæG&ö–Bæ÷2å&6VÄf–ÆTFW67&—F÷"äÔôDUõ$TEôôäÅ’¢fÂ&VæFW&W"ÒæG&ö–Bæw&†–72çFbåFe&VæFW&W"†fB¢–b‡&VæFW&W"çvT6÷VçBÃÒ’°¢&VæFW&W"æ6Æ÷6R‚¢fBæ6Æ÷6R‚¢&WGW&âf–ÆUfÆ–FF–öå&W7VÇB†fÇ6RÂf–ÆRÂÖ–ÖUG—RÂf÷&ÖDæÖRÂÆVæwF‚Â""Â%DbVçFŒ:FÇB¶V–æR6V—FVâƒ6V—FVâ’â"¢Ğ¢&VæFW&W"æ6Æ÷6R‚¢fBæ6Æ÷6R‚¢ÒVÇ6R°¢fÂ÷F–öç2Ò&—FÖf7F÷'’ä÷F–öç2‚’æÇ’²–ä§W7DFV6öFT&÷VæG2ÒG'VRĞ¢&—FÖf7F÷'’æFV6öFTf–ÆR†f–ÆRæ'6öÇWFUF‚Â÷F–öç2¢–b†÷F–öç2æ÷WEv–GF‚ÃÒÇÂ÷F–öç2æ÷WD†V–v‡BÃÒ’°¢&WGW&âf–ÆUfÆ–FF–öå&W7VÇB†fÇ6RÂf–ÆRÂÖ–ÖUG—RÂf÷&ÖDæÖRÂÆVæwF‚Â""Â$&–ÆFFFV’¶öæçFRæ–6‡BFV¶öF–W'BvW&FVââ"¢Ğ¢Ğ¢Ò6F6‚†S¢W†6WF–öâ’°¢&WGW&âf–ÆUfÆ–FF–öå&W7VÇB†fÇ6RÂf–ÆRÂÖ–ÖUG—RÂf÷&ÖDæÖRÂÆVæwF‚Â""Â%f÷'66†R¶öæçFRæ–6‡BW'¦WVwBvW&FVã¢G¶RæÖW76vWÒ"¢Ğ ¢òò6Æ7VÆFR4„Ó#S`¢fÂF–vW7BÒ¦fç6V7W&—G’äÖW76vTF–vW7BævWD–ç7Fæ6R‚%4„Ó#Sb"¢f–ÆRæ–çWE7G&VÒ‚’çW6R²7G&VÒÓà¢fÂ'VbÒ'—FT'&’ƒƒ“"¢f"&VC¢–ç@¢v†–ÆR‡7G&VÒç&VB†'Vb’æÇ6ò²&VBÒ—BÒÒÓ’°¢F–vW7BçWFFR†'VbÂÂ&VB¢Ğ¢Ğ¢fÂ6†#SbÒF–vW7BæF–vW7B‚’æ¦ö–åFõ7G&–ær‚""’²"S'‚"æf÷&ÖB†—B’Ğ ¢&WGW&âf–ÆUfÆ–FF–öå&W7VÇB€¢—5fÆ–BÒG'VRÀ¢f–ÆRÒf–ÆRÀ¢Ö–ÖUG—RÒÖ–ÖUG—RÀ¢f÷&ÖDæÖRÒf÷&ÖDæÖRÀ¢6—¦T'—FW2ÒÆVæwF‚À¢6†#SbÒ6†#S`¢¢Ğ ¢gVâ&W—%&V6V—DFö7VÖVçB‡&V6V—C¢&V6V—BÂFV×f–ÆS¢f–ÆRÂfÆ–FF–öã¢f–ÆUfÆ–FF–öå&W7VÇB’°¢f–WtÖöFVÅ66÷RæÆVæ6‚„F—7F6†W'2ä”ò’°¢÷&W—%V•7FFRçfÇVRÒ&W—%V•7FFRå&ö6W76–ær‚%,;ÆfRVæB6–6†W&RFFV’–âvöövÆRG&—fRâââ"¢G'’°¢fÂVÖ–ÂÒövöövÆT66÷VçDVÖ–ÂçfÇVP¢–b†VÖ–Âæ—4çVÆÄ÷$V×G’‚’’°¢÷&W—%V•7FFRçfÇVRÒ&W—%V•7FFRäW'&÷"‚$¶V–âvöövÆR66÷VçBævVÖVÆFWBâ"¢&WGW&äÆVæ6€¢Ğ¢fÂFö¶VâÒvWEfÆ–EFö¶Vâ†VÖ–Â¢fÂ6öæf–rÒG&—fUW'6—7FVæ6U&W÷6—F÷'’ævWDG&—fT6öæf–r‡Fö¶Vâ¢–b†6öæf–rÓÒçVÆÂ’°¢÷&W—%V•7FFRçfÇVRÒ&W—%V•7FFRäW'&÷"‚$vöövÆRG&—fR¶öæf–wW&F–öâ¶öæçFRæ–6‡BvVÆFVâvW&FVââ"¢&WGW&äÆVæ6€¢Ğ ¢fÂ&W2ÒG&—fUW'6—7FVæ6U&W÷6—F÷'’ç&W—$æEWÆöD÷&–v–æÄFö7VÖVçB€¢6öçFW‡BÒvWDÆ–6F–öâ‚’À¢66W75Fö¶VâÒFö¶VâÀ¢6öæf–rÒ6öæf–rÀ¢&V6V—BÒ&V6V—BÀ¢Æö6Äf–ÆRÒFV×f–ÆRÀ¢fÆ–FF–öå&W7VÇBÒfÆ–FF–öà¢ ¢–b‡&W2ç7V66W72bb&W2çWFFVE&V6V—BÒçVÆÂ’°¢fÂWFFVBÒ&W2çWFFVE&V6V—@¢öFö7VÖVçDF÷væÆöE7FGW2çfÇVRÒöFö7VÖVçDF÷væÆöE7FGW2çfÇVRçFô×WF&ÆTÖ‚’æÇ’°¢WB€¢&V6V—Bæ–çFW&æÄ–BÀ¢Fö7VÖVçDF÷væÆöE7FGW2€¢7FFRÒFö7VÖVçE7FFRäd”Ä$ÄRÀ¢Æö6ÅF‚ÒWFFVBæ–ÖvUW&ÂÀ¢ÖW76vRÒ$÷&–v–æÆFö·VÖVçBW&föÆw&V–6‚§VvV÷&FæWB ¢¢¢Ğ¢÷&W—%V•7FFRçfÇVRÒ&W—%V•7FFRå7V66W72€¢ÖW76vRÒ$÷&–v–æÆFö·VÖVçBwW&FRW&föÆw&V–6‚§VvV÷&FæWBVæB–âvöövÆRG&—fRvW6–6†W'Bâ"À¢WFFVE&V6V—BÒWFFV@¢¢ÒVÇ6R°¢÷&W—%V•7FFRçfÇVRÒ&W—%V•7FFRäW'&÷"‡&W2æW'&÷$ÖW76vRó¢$fV†ÆW"&V’FW"Fö·VÖVçFVç&W&GW"â"¢Ğ¢Ò6F6‚†S¢W†6WF–öâ’°¢ÆöræR‚%&V6V—Ef–WtÖöFVÂ"Â$W'&÷"–â&W—%&V6V—DFö7VÖVçB"ÂR¢÷&W—%V•7FFRçfÇVRÒ&W—%V•7FFRäW'&÷"‚$fV†ÆW#¢G¶RæÖW76vWÒ"¢Ğ¢Ğ¢Ğ§Ğ ¦FF6Æ72f–ÆUfÆ–FF–öå&W7VÇB€¢fÂ—5fÆ–C¢&ööÆVâÀ¢fÂf–ÆS¢f–ÆRÀ¢fÂÖ–ÖUG—S¢7G&–ærÀ¢fÂf÷&ÖDæÖS¢7G&–ærÀ¢fÂ6—¦T'—FW3¢ÆöærÀ¢fÂ6†#Sc¢7G&–ærÀ¢fÂW'&÷$ÖW76vS¢7G&–æsòÒçVÆÀ¢ §6VÆVB6Æ72&W—%V•7FFR°¢ö&¦V7B–FÆR¢&W—%V•7FFR‚¢FF6Æ72&ö6W76–ær‡fÂ7FW¢7G&–ær’¢&W—%V•7FFR‚¢FF6Æ727V66W72‡fÂÖW76vS¢7G&–ærÂfÂWFFVE&V6V—C¢&V6V—B’¢&W—%V•7FFR‚¢FF6Æ72W'&÷"‡fÂW'&÷#¢7G&–ær’¢&W—%V•7FFR‚§Ğ ¦FF6Æ72G&—fUFW7E7FFR€¢fÂ—5'Vææ–æs¢&ööÆVâÒfÇ6RÀ¢fÂvöövÆT66÷VçC¢7G&–ærÒ""À¢fÂÖ–äföÆFW$–C¢7G&–ærÒ""À¢fÂ7—7FVÔföÆFW$–C¢7G&–ærÒ""À¢fÂFW7Df–ÆT–C¢7G&–ærÒ""À¢fÂWÆöE7V66W73¢&ööÆVâÒfÇ6RÀ¢fÂF÷væÆöE7V66W73¢&ööÆVâÒfÇ6RÀ¢fÂ6öçFVçD–FVçF–6Ã¢&ööÆVâÒfÇ6RÀ¢fÂ&V6V—EFW7E7V66W73¢&ööÆVâÒfÇ6RÀ¢fÂFW7E&V6V—D–çFW&æÄ–C¢7G&–ærÒ""À¢fÂFW7E&V6V—DÖ–äG&—fTf–ÆT–C¢7G&–ærÒ""À¢fÂFW7E&V6V—DÖWFFFf–ÆT–C¢7G&–ærÒ""À¢fÂFW7E&V6V—DÖWFFFFƒ¢7G&–ærÒ""À¢fÂ—57V66W73¢&ööÆVâÒfÇ6RÀ¢fÂffV7FVD7F–öã¢7G&–æsòÒçVÆÂÀ¢fÂW'&÷$ÖW76vS¢7G&–æsòÒçVÆÂÀ¢fÂ‡GG7FGW46öFS¢–çCòÒçVÆÂÀ¢fÂ—5&TWF…&WV—&VC¢&ööÆVâÒfÇ6RÀ¢fÂFW7E'VåF–ÖS¢7G&–æsòÒçVÆÀ¢  ¦VçVÒ6Æ72Fö7VÖVçE7FFR°¢4„T4´”ärÀ¢DõtäÄôEõ$UT•$TBÀ¢DõtäÄôD”ärÀ¢d”Ä$ÄRÀ¢Tå5Uõ%DTBÀ¢U%$õ §Ğ ¦FF6Æ72Fö7VÖVçDF÷væÆöE7FGW2€¢fÂ7FFS¢Fö7VÖVçE7FFRÀ¢fÂÆö6ÅFƒ¢7G&–æsòÒçVÆÂÀ¢fÂÖW76vS¢7G&–æsòÒçVÆÀ¢