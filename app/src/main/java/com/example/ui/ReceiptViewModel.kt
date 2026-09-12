package com.example.ui

import com.example.data.parseCamtV8
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
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
import com.example.data.toEntity
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.data.FirestoreService

enum class AppScreen {
    DASHBOARD,
    RECEIPTS_LIST,
    ADD_RECEIPT,
    LOGBOOK,
    LEDGER,
    RENT_OVERVIEW,
    TAX_CALCULATOR,
    DOCUMENTS,
    PROPERTIES,
    BANK,
    MORE
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
    val mietvertragsstart: String = "",
    val unitId: String = ""
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
    internal fun bankPhase2DServiceForUi(): com.example.data.BankPhase2DService = com.example.data.BankPhase2DService(database)
    private val repository = ReceiptRepository(
        database.receiptDao(),
        database.propertyDao(),
        database.receiptEntityDao(),
        database.belegDao(),
        database.exportAuditDao(),
        database.receiptDocumentDao(),
        database.managedDocumentDao()
    )
    val drivePersistenceRepository = com.example.data.DrivePersistenceRepository(application, repository)
    private val managedDocumentService = com.example.data.ManagedDocumentService(application, repository)
    val managedDocuments: StateFlow<List<com.example.data.ManagedDocument>> = repository.allManagedDocuments.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )
    private val _documentSearchResults = MutableStateFlow<List<com.example.data.ManagedDocument>>(emptyList())
    val documentSearchResults: StateFlow<List<com.example.data.ManagedDocument>> = _documentSearchResults.asStateFlow()
    private val _documentOperationStatus = MutableStateFlow<String?>(null)
    val documentOperationStatus: StateFlow<String?> = _documentOperationStatus.asStateFlow()
    private val _pendingDocumentDuplicate = MutableStateFlow<Pair<com.example.data.ManagedDocument, com.example.data.ManagedDocument>?>(null)
    val pendingDocumentDuplicate = _pendingDocumentDuplicate.asStateFlow()
    private var pendingDocumentDuplicateUri: android.net.Uri? = null
    private val _documentAiReview = MutableStateFlow<Pair<String, com.example.api.ManagedDocumentAiResult>?>(null)
    val documentAiReview = _documentAiReview.asStateFlow()
    private val _documentMigrationPreview = MutableStateFlow<com.example.data.DocumentMigrationPreview?>(null)
    val documentMigrationPreview = _documentMigrationPreview.asStateFlow()

    val logbookTrips: StateFlow<List<com.example.data.LogbookTrip>> =
        database.logbookDao().observeTrips().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
    val standardRoutes: StateFlow<List<com.example.data.StandardRoute>> =
        database.logbookDao().observeStandardRoutes().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private var routeDistanceService: com.example.data.RouteDistanceService =
        com.example.data.GoogleRoutesDistanceService(application) {
            AiProviderSettings.getGoogleRoutesKey(application)
        }

    internal fun setRouteDistanceServiceForTesting(service: com.example.data.RouteDistanceService) {
        routeDistanceService = service
    }


    private val sharedPrefs = application.getSharedPreferences("google_drive_prefs", Context.MODE_PRIVATE)
    private val _aiProviderState = MutableStateFlow(AiProviderSettings.loadState(application))
    val aiProviderState: StateFlow<AiProviderState> = _aiProviderState.asStateFlow()

    fun saveAiProviderSettings(
        provider: ReceiptAnalysisProvider,
        model: String,
        newOpenAiKey: String,
        newGeminiKey: String,
        newGoogleRoutesKey: String = ""
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
            if (newGoogleRoutesKey.isNotBlank()) {
                AiProviderSettings.storeGoogleRoutesKey(
                    getApplication(),
                    newGoogleRoutesKey.toCharArray()
                )
            }
            val onlyGoogleUpdate = newGoogleRoutesKey.isNotBlank() &&
                newOpenAiKey.isBlank() && newGeminiKey.isBlank() &&
                selectedProviderIsUnavailable(provider)
            if (onlyGoogleUpdate) {
                _aiProviderState.value = AiProviderSettings.loadState(getApplication())
                return null
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

    private fun selectedProviderIsUnavailable(provider: ReceiptAnalysisProvider): Boolean {
        val state = AiProviderSettings.loadState(getApplication())
        return when (provider) {
            ReceiptAnalysisProvider.OPENAI -> !state.hasOpenAiKey
            ReceiptAnalysisProvider.GEMINI -> !state.hasGeminiKey &&
                (com.example.BuildConfig.GEMINI_API_KEY.isBlank() || com.example.BuildConfig.GEMINI_API_KEY == "MY_GEMINI_API_KEY")
        }
    }

    fun deleteOpenAiKey() {
        _aiProviderState.value = AiProviderSettings.clearOpenAiKey(getApplication())
    }

    fun deleteGoogleRoutesKey() {
        _aiProviderState.value = AiProviderSettings.clearGoogleRoutesKey(getApplication())
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
    private val _bankImportStatus = MutableStateFlow<String?>(null)
    val bankImportStatus: StateFlow<String?> = _bankImportStatus.asStateFlow()
    private val _pendingBankTransactionId = MutableStateFlow<String?>(null)
    val pendingBankTransactionId: StateFlow<String?> = _pendingBankTransactionId.asStateFlow()

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

    val bankAccounts: StateFlow<List<com.example.data.BankAccount>> =
        database.bankDao().observeAccounts().stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val bankTransactions: StateFlow<List<com.example.data.BankTransaction>> =
        database.bankDao().observeTransactions().stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val bankReceiptLinks: StateFlow<List<com.example.data.BankReceiptLink>> =
        database.bankDao().observeLinks().stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val bankLearningRules: StateFlow<List<com.example.data.BankLearningRule>> =
        database.bankLearningRuleDao().observeRules().stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val bankRentAssignments: StateFlow<List<com.example.data.BankRentAssignment>> =
        database.bankRentAssignmentDao().observeAll().stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val bankLoanAssignments: StateFlow<List<com.example.data.BankLoanAssignment>> =
        database.bankLoanAssignmentDao().observeAll().stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val bankRecurringPatterns: StateFlow<List<com.example.data.BankRecurringPattern>> =
        database.bankRecurringPatternDao().observeAll().stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val bankRecurringAnalysis: StateFlow<com.example.data.RecurringPaymentAnalysis> =
        bankTransactions.map { transactions ->
            com.example.data.BankRecurringPaymentDetector.detect(transactions, java.time.LocalDate.now())
        }.flowOn(Dispatchers.Default).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = com.example.data.RecurringPaymentAnalysis(emptyList(), emptySet(), emptySet())
        )
    private val _dismissedBankLoanTransactions = MutableStateFlow<Set<String>>(emptySet())
    private val _dismissedBankRentTransactions = MutableStateFlow<Set<String>>(emptySet())

    val bankRuleEvaluations: StateFlow<Map<String, com.example.data.BankRuleEvaluation>> =
        combine(bankTransactions, bankLearningRules) { transactions, rules ->
            transactions.associate { it.transactionId to com.example.data.BankRuleEngine.evaluate(it, rules) }
        }.stateIn(scope=viewModelScope, started=SharingStarted.WhileSubscribed(5_000), initialValue=emptyMap())

    val bankMatchSuggestions: StateFlow<Map<String, com.example.data.BankMatchSuggestion>> =
        combine(bankTransactions, receipts, bankReceiptLinks, bankLearningRules) { transactions, currentReceipts, links, rules ->
            val base = com.example.data.BankReceiptMatcher.bestSuggestions(transactions, currentReceipts, links)
            base.mapValues { (transactionId, suggestion) ->
                val tx = transactions.firstOrNull { it.transactionId == transactionId }
                val receipt = currentReceipts.firstOrNull { it.id == suggestion.receiptId }
                if (tx != null && receipt != null) com.example.data.BankRuleScoring.enhance(suggestion, tx, receipt, rules) else suggestion
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
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

    val properties: StateFlow<List<PropertyMetadata>> = repository.allProperties
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private val _selectedPropertyId = MutableStateFlow(sharedPrefs.getString("selected_property_id", "").orEmpty())
    val selectedPropertyId: StateFlow<String> = _selectedPropertyId.asStateFlow()

    val propertyMetadata: StateFlow<PropertyMetadata?> = combine(properties, _selectedPropertyId) { all, selectedId ->
        all.firstOrNull { it.propertyId == selectedId } ?: all.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val bankRentSuggestions: StateFlow<Map<String, List<BankRentSuggestion>>> =
        combine(
            combine(bankTransactions, bankRentAssignments, bankLearningRules) { txs, assignments, rules -> Triple(txs, assignments, rules) },
            combine(properties, receipts, bankReceiptLinks) { props, currentReceipts, links -> Triple(props, currentReceipts, links) },
            _dismissedBankRentTransactions
        ) { core, rentalSources, dismissed ->
            val (transactions, assignments, rules) = core
            val (allProperties, currentReceipts, links) = rentalSources
            val unitsByProperty = allProperties.associate { property ->
                property.propertyId to getWohneinheitenForProperty(property)
            }
            transactions.asSequence()
                .filter { it.isIncome && it.transactionId !in dismissed }
                .mapNotNull { transaction ->
                    val candidates = BankRentCandidateFactory.build(
                        context = getApplication(), transaction = transaction,
                        properties = allProperties, unitsByProperty = unitsByProperty,
                        receipts = currentReceipts, assignments = assignments,
                        allTransactions = transactions, receiptLinks = links
                    )
                    BankRentMatcher.match(transaction, candidates, rules)
                        .takeIf { it.isNotEmpty() }
                        ?.let { transaction.transactionId to it }
                }.toMap()
        }.flowOn(Dispatchers.Default).stateIn(
            scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = emptyMap()
        )

    val propertyReceipts: StateFlow<List<Receipt>> = combine(receipts, propertyMetadata) { items, property ->
        if (property == null) items else items.filter {
            it.propertyId == property.propertyId ||
                (property.id == 1 && it.propertyId == com.example.data.StableDocumentIdentity.LEGACY_PROPERTY_ID)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val allLoans: StateFlow<List<com.example.data.Loan>> =
        database.loanDao().getAllLoansFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )

    val loans: StateFlow<List<com.example.data.Loan>> = combine(allLoans, propertyMetadata) { items, property ->
        if (property == null) items else items.filter {
            it.propertyId == property.propertyId ||
                (property.id == 1 && it.propertyId == com.example.data.StableDocumentIdentity.LEGACY_PROPERTY_ID)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())


    val bankLoanSuggestions: StateFlow<Map<String, List<com.example.data.BankLoanSuggestion>>> =
        combine(bankTransactions, allLoans, bankAccounts, bankLearningRules, _dismissedBankLoanTransactions) { transactions, currentLoans, accounts, rules, dismissed ->
            val accountsById = accounts.associateBy { it.accountId }
            transactions.asSequence()
                .filterNot { it.transactionId in dismissed }
                .filterNot { tx -> bankLoanAssignments.value.any { it.transactionId == tx.transactionId } }
                .mapNotNull { tx ->
                    com.example.data.BankLoanMatcher.suggestions(tx, currentLoans, accountsById[tx.accountId], transactions, rules)
                        .takeIf { it.isNotEmpty() }?.let { tx.transactionId to it }
                }.toMap()
        }.flowOn(Dispatchers.Default).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )


    val bankPhase2DAnalysis: StateFlow<com.example.data.BankPhase2DAnalysis> =
        combine(
            combine(bankTransactions, receipts, bankReceiptLinks, bankLearningRules) { txs, currentReceipts, links, rules ->
                arrayOf(txs, currentReceipts, links, rules)
            },
            combine(bankMatchSuggestions, bankRentAssignments, bankLoanAssignments, bankRecurringPatterns) { one, rent, loan, recurring ->
                arrayOf(one, rent, loan, recurring)
            }
        ) { base, classified ->
            @Suppress("UNCHECKED_CAST")
            com.example.data.BankPhase2DEngine.analyze(
                transactions = base[0] as List<com.example.data.BankTransaction>,
                receipts = base[1] as List<Receipt>,
                links = base[2] as List<com.example.data.BankReceiptLink>,
                rules = base[3] as List<com.example.data.BankLearningRule>,
                oneToOne = classified[0] as Map<String, com.example.data.BankMatchSuggestion>,
                rentAssignments = classified[1] as List<com.example.data.BankRentAssignment>,
                loanAssignments = classified[2] as List<com.example.data.BankLoanAssignment>,
                recurringPatterns = classified[3] as List<com.example.data.BankRecurringPattern>
            )
        }.flowOn(Dispatchers.Default).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = com.example.data.BankPhase2DAnalysis(emptyList(), emptyList())
        )

    val bankPhase2DCombinations: StateFlow<List<com.example.data.BankCombinationSuggestion>> =
        bankPhase2DAnalysis.map { it.combinations }.stateIn(
            scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = emptyList()
        )

    val bankPhase2DReviewQueue: StateFlow<List<com.example.data.BankReviewItem>> =
        bankPhase2DAnalysis.map { it.queue }.stateIn(
            scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = emptyList()
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
                    _wohneinheitenStatus.value = getWohneinheitenForProperty(meta)
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

        val propertyId = propertyMetadata.value?.propertyId ?: com.example.data.StableDocumentIdentity.LEGACY_PROPERTY_ID
        return unitNames.mapIndexed { index, name ->
            val preset = defaultPresetMap[name]
            val exists = unitPrefs.contains("unit_status_$name")
            val stableUnitId = unitPrefs.getString("unit_id_$name", null)
                ?: unitPrefs.getString("unit_id_index_$index", null)
                ?: com.example.data.StableDocumentIdentity.legacyUnitId(propertyId, name).also {
                    unitPrefs.edit().putString("unit_id_$name", it).putString("unit_id_index_$index", it).apply()
                }

            if (exists) {
                WohneinheitStatus(
                    name = name,
                    label = unitPrefs.getString("unit_label_$name", name) ?: name,
                    status = unitPrefs.getString("unit_status_$name", "Vermietet") ?: "Vermietet",
                    mieter = unitPrefs.getString("unit_mieter_$name", "") ?: "",
                    kaltmiete = unitPrefs.getFloat("unit_rent_$name", 0f).toDouble(),
                    wohnflaeche = unitPrefs.getFloat("unit_area_$name", 0f).toDouble(),
                    mietvertragsstart = unitPrefs.getString("unit_start_$name", "") ?: "",
                    unitId = stableUnitId
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
                preset.copy(unitId = stableUnitId)
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
                    mietvertragsstart = "",
                    unitId = stableUnitId
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

    fun getWohneinheitenForProperty(metadata: PropertyMetadata): List<WohneinheitStatus> {
        if (metadata.id == 1) return getWohneinheitenFromPrefs(metadata.wohneinheiten)
        val unitPrefs = getApplication<Application>().getSharedPreferences("wohneinheiten_prefs", Context.MODE_PRIVATE)
        return metadata.wohneinheiten.split(',').map(String::trim).filter(String::isNotBlank).mapIndexed { index, name ->
            val prefix = "property_${metadata.propertyId}_unit_${name}_"
            val stableId = unitPrefs.getString(prefix + "id", null)
                ?: com.example.data.StableDocumentIdentity.legacyUnitId(metadata.propertyId, name).also {
                    unitPrefs.edit().putString(prefix + "id", it).putString("property_${metadata.propertyId}_unit_id_index_$index", it).apply()
                }
            WohneinheitStatus(
                name = name,
                label = unitPrefs.getString(prefix + "label", name) ?: name,
                status = unitPrefs.getString(prefix + "status", "Leerstand") ?: "Leerstand",
                mieter = unitPrefs.getString(prefix + "mieter", "") ?: "",
                kaltmiete = unitPrefs.getFloat(prefix + "rent", 0f).toDouble(),
                wohnflaeche = unitPrefs.getFloat(prefix + "area", 0f).toDouble(),
                mietvertragsstart = unitPrefs.getString(prefix + "start", "") ?: "",
                unitId = stableId
            )
        }
    }

    fun updateWohneinheit(updated: WohneinheitStatus) {
        val unitPrefs = getApplication<Application>().getSharedPreferences("wohneinheiten_prefs", Context.MODE_PRIVATE)
        val selectedProperty = propertyMetadata.value ?: PropertyMetadata()
        val stableId = updated.unitId.ifBlank {
            com.example.data.StableDocumentIdentity.legacyUnitId(
                selectedProperty.propertyId,
                updated.name
            )
        }
        val unitIndex = _wohneinheitenStatus.value.indexOfFirst { it.unitId == updated.unitId || it.name == updated.name }
        unitPrefs.edit().apply {
            if (selectedProperty.id == 1) {
                putString("unit_status_${updated.name}", updated.status)
                putString("unit_label_${updated.name}", updated.label)
                putString("unit_mieter_${updated.name}", updated.mieter)
                putFloat("unit_rent_${updated.name}", updated.kaltmiete.toFloat())
                putFloat("unit_area_${updated.name}", updated.wohnflaeche.toFloat())
                putString("unit_start_${updated.name}", updated.mietvertragsstart)
                putString("unit_id_${updated.name}", stableId)
                if (unitIndex >= 0) putString("unit_id_index_$unitIndex", stableId)
            } else {
                val prefix = "property_${selectedProperty.propertyId}_unit_${updated.name}_"
                putString(prefix + "status", updated.status)
                putString(prefix + "label", updated.label)
                putString(prefix + "mieter", updated.mieter)
                putFloat(prefix + "rent", updated.kaltmiete.toFloat())
                putFloat(prefix + "area", updated.wohnflaeche.toFloat())
                putString(prefix + "start", updated.mietvertragsstart)
                putString(prefix + "id", stableId)
                if (unitIndex >= 0) putString("property_${selectedProperty.propertyId}_unit_id_index_$unitIndex", stableId)
            }
        }.apply()
        _wohneinheitenStatus.value = getWohneinheitenForProperty(selectedProperty)

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
                    val outcome = com.example.data.FullRestoreCoordinator.execute(
                        coreRestore = { drivePersistenceRepository.executeFullDriveRestore(token, initResult.config, mode) },
                        supplementalRestore = {
                            com.example.data.SupplementalDriveBackup.restore(
                                getApplication(), database, token, initResult.config.systemFolderId,
                                replaceManagedDocuments = mode == com.example.data.RestoreMode.REPLACE_FULL
                            )
                        },
                        onPhase = { phase ->
                            drivePersistenceRepository.updateLatestRestorePhase(phase)
                            _driveSyncStatus.value = "Wiederherstellung: ${phase.name}"
                        }
                    )
                    val report = outcome.coreReport
                    _restoreReport.value = if (outcome.isSuccess || !report.isSuccess) report else report.copy(
                        isSuccess = false,
                        errorCount = report.errorCount + 1,
                        errors = report.errors + outcome.message
                    )
                    _isRestoring.value = false
                    _isRestoreRequired.value = !outcome.isSuccess
                    if (report.isSuccess) {
                        _wohneinheitenStatus.value = getWohneinheitenFromPrefs()
                        loadLearnedRules()
                    }
                    _driveSyncStatus.value = if (outcome.isSuccess) {
                        "Wiederherstellung erfolgreich! ${report.receiptsRestored} Belege sowie Miet- und Darlehensdaten geladen."
                    } else {
                        outcome.message
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
                val documentStructureSuccess = drivePersistenceRepository.ensurePropertyDocumentStructure(token, config)
                
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
                var documentSuccessCount = 0
                for (document in repository.getAllManagedDocuments().filter { it.receiptInternalId.isNullOrBlank() }) {
                    if (drivePersistenceRepository.syncManagedDocumentToDrive(token, config, document.documentId)) documentSuccessCount++
                }
                val documentIndexSuccess = drivePersistenceRepository.updateManagedDocumentIndex(token, config)
                val supplementalBackup = com.example.data.SupplementalDriveBackup.backup(
                    getApplication(), database, token, config.systemFolderId
                )

                _isDriveSyncing.value = false
                if (csvSuccess && unitsSuccess && backupSuccess && supplementalBackup.success && documentStructureSuccess && documentIndexSuccess) {
                    _driveSyncStatus.value = "Erfolgreich! Hauptbuch, Wohneinheiten, Stammdaten, $successCount Belege und $documentSuccessCount Dokumente synchronisiert."
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

    fun importBankFile(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _bankImportStatus.value = "Kontoauszug wird eingelesen …"
            try {
                val resolver = getApplication<Application>().contentResolver
                val displayName = runCatching {
                    resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                }.getOrNull().orEmpty().ifBlank { "Kontoauszug" }
                val bytes = resolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    val maxBytes = com.example.data.BankZipImportLimits.MAX_ZIP_BYTES
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > maxBytes) throw IllegalArgumentException("Importdatei überschreitet die zulässige Maximalgröße.")
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: throw IllegalArgumentException("Datei konnte nicht gelesen werden.")
                val now = java.time.Instant.now().toString()
                val dao = database.bankDao()

                suspend fun persistBatch(batch: com.example.data.BankImportBatch): Pair<Int, Int> {
                    val existingAccount = dao.getAccount(batch.account.accountId)
                    dao.upsertAccount(
                        batch.account.copy(
                            displayName = existingAccount?.displayName?.takeIf { it.isNotBlank() } ?: batch.account.displayName,
                            bankName = existingAccount?.bankName.orEmpty().ifBlank { batch.account.bankName },
                            accountHolder = existingAccount?.accountHolder.orEmpty().ifBlank { batch.account.accountHolder },
                            createdAt = existingAccount?.createdAt?.takeIf { it.isNotBlank() } ?: batch.account.createdAt,
                            updatedAt = now
                        )
                    )
                    val results = dao.insertTransactions(batch.transactions)
                    val inserted = results.count { it != -1L }
                    return inserted to (batch.transactions.size - inserted)
                }

                if (com.example.data.BankZipImportParser.looksLikeZip(bytes)) {
                    _bankImportStatus.value = "ZIP erkannt • Inhalt wird lokal und sicher geprüft …"
                    val parsed = com.example.data.BankZipImportParser.parse(
                        zipBytes = bytes,
                        zipFileName = displayName,
                        importedAt = now
                    )
                    var inserted = 0
                    var duplicates = 0
                    parsed.batches.forEach { batch ->
                        val result = persistBatch(batch)
                        inserted += result.first
                        duplicates += result.second
                    }
                    val read = parsed.batches.sumOf { it.transactions.size }
                    val rowErrors = parsed.batches.sumOf { it.errorRows }
                    _bankImportStatus.value = buildString {
                        append("ZIP: ${parsed.totalEntries} Einträge • ${parsed.xmlEntries} XML • ${parsed.supportedCamtFiles} CAMT unterstützt")
                        append(" • $read Buchungen gelesen • $inserted neu • $duplicates Dubletten")
                        append(" • ${parsed.unsupportedFiles} nicht unterstützt • ${parsed.faultyFiles} fehlerhafte Dateien")
                        if (rowErrors > 0) append(" • $rowErrors fehlerhafte Buchungen")
                        parsed.entryReports.forEach { report ->
                            append("\n• ${report.entryName.take(120)}: ${report.status}")
                            if (report.format.isNotBlank()) append(" (${report.format})")
                            append(" – ${report.message}")
                        }
                    }
                } else {
                    val textContent = bytes.toString(Charsets.UTF_8)
                    val trimmed = textContent.trimStart()
                    val batch = if (trimmed.startsWith("<")) {
                        val detected = com.example.data.BankCamtV8FormatDetector.detect(textContent)
                        if (detected == com.example.data.BankCamtFormat.UNSUPPORTED) {
                            throw IllegalArgumentException("XML-Format wird nicht unterstützt. Erwartet wird CAMT.052.001.08 oder CAMT.053.001.08.")
                        }
                        _bankImportStatus.value = "${detected.label} erkannt • Buchungen werden geprüft …"
                        com.example.data.BankImportParser.parseCamtV8(
                            xml = textContent,
                            fallbackAccountName = displayName.substringBeforeLast('.'),
                            importedAt = now,
                            importFileName = displayName,
                            importRunId = com.example.data.BankTransactionIdentity.importRunId(detected.source, displayName, now)
                        )
                    } else {
                        _bankImportStatus.value = "CSV erkannt • Buchungen werden geprüft …"
                        com.example.data.BankImportParser.parseCsv(
                            text = textContent,
                            fallbackAccountName = displayName.substringBeforeLast('.'),
                            importedAt = now,
                            importFileName = displayName,
                            importRunId = com.example.data.BankTransactionIdentity.importRunId("CSV", displayName, now)
                        )
                    }
                    val (inserted, duplicates) = persistBatch(batch)
                    val skippedOrInvalid = batch.skippedRows + batch.errorRows
                    _bankImportStatus.value = buildString {
                        append("${batch.format}: ${batch.transactions.size} gültige Buchungen erkannt • $inserted neu • $duplicates Dubletten")
                        if (skippedOrInvalid > 0) append(" • $skippedOrInvalid übersprungen/fehlerhaft")
                        append(".")
                    }
                }
            } catch (e: Exception) {
                Log.e("ReceiptViewModel", "Bank import failed: ${e.javaClass.simpleName}")
                _bankImportStatus.value = "Import fehlgeschlagen: ${e.message ?: "unbekannter Fehler"}"
            }
        }
    }

    fun markBankTransactionReviewDone(transactionId: String) {
        updateBankTransactionReviewState(transactionId, com.example.data.BankReviewState.DONE)
    }

    fun reopenBankTransactionReview(transactionId: String) {
        updateBankTransactionReviewState(transactionId, com.example.data.BankReviewState.OPEN)
    }

    private fun updateBankTransactionReviewState(transactionId: String, reviewState: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = java.time.Instant.now().toString()
            database.bankDao().updateTransactionReviewState(transactionId, reviewState, now)
            _bankImportStatus.value = if (reviewState == com.example.data.BankReviewState.DONE) {
                "Buchung aus persönlicher Prüfliste als erledigt markiert. DATEV-Fachstatus bleibt unverändert."
            } else {
                "Buchung wieder in die persönliche Prüfliste aufgenommen."
            }
        }
    }

    fun confirmBankTransferPair(transactionId: String, counterpartTransactionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = database.bankDao()
            val first = dao.getTransaction(transactionId) ?: return@launch
            val second = dao.getTransaction(counterpartTransactionId) ?: return@launch
            if (!com.example.data.BankTransferPairPolicy.canConfirm(first, second)) {
                _bankImportStatus.value = "Gegenbuchung nicht verknüpft: Betrag, Richtung, Konto oder Datum passen nicht sicher genug."
                return@launch
            }
            val now = java.time.Instant.now().toString()
            val update = com.example.data.BankTransferPairPolicy.confirm(first, second, now)
            listOf(update.first, update.second).forEach { record ->
                dao.updateTransactionClassification(
                    transactionId = record.transactionId,
                    classification = record.classification,
                    transferCounterAccountId = record.transferCounterAccountId,
                    linkedTransferTransactionId = record.linkedTransferTransactionId,
                    reviewState = record.reviewState,
                    updatedAt = now
                )
            }
            _bankImportStatus.value = "Umbuchungspaar bestätigt und beidseitig verknüpft. Beide Buchungen bleiben vom normalen DATEV-Einnahmen-/Ausgabenexport ausgeschlossen."
        }
    }

    fun unlinkBankTransferPair(transactionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = database.bankDao()
            val selected = dao.getTransaction(transactionId) ?: return@launch
            val counterpart = selected.linkedTransferTransactionId.takeIf { it.isNotBlank() }?.let { dao.getTransaction(it) }
            val now = java.time.Instant.now().toString()
            val update = com.example.data.BankTransferPairPolicy.unlink(selected, counterpart, now)
            dao.updateTransactionClassification(
                transactionId = update.selected.transactionId,
                classification = update.selected.classification,
                transferCounterAccountId = update.selected.transferCounterAccountId,
                linkedTransferTransactionId = update.selected.linkedTransferTransactionId,
                reviewState = update.selected.reviewState,
                updatedAt = now
            )
            update.counterpart?.let { record ->
                dao.updateTransactionClassification(
                    transactionId = record.transactionId,
                    classification = record.classification,
                    transferCounterAccountId = record.transferCounterAccountId,
                    linkedTransferTransactionId = record.linkedTransferTransactionId,
                    reviewState = record.reviewState,
                    updatedAt = now
                )
            }
            _bankImportStatus.value = "Gegenbuchungs-Verknüpfung gelöst. Die Umbuchungs-Klassifikation bleibt bestehen und kann separat entfernt werden."
        }
    }

    fun executeBankBatchAction(
        transactionIds: List<String>,
        action: com.example.data.BankBatchAction
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = database.bankDao()
            val current = transactionIds.distinct().mapNotNull { dao.getTransaction(it) }
            val preview = com.example.data.BankBatchActionPolicy.preview(current, dao.getAllLinks(), action)
            val now = java.time.Instant.now().toString()

            preview.eligibleTransactionIds.forEach { transactionId ->
                val transaction = dao.getTransaction(transactionId) ?: return@forEach
                when (action) {
                    com.example.data.BankBatchAction.PRIVATE_IGNORED,
                    com.example.data.BankBatchAction.TRANSFER -> {
                        val target = if (action == com.example.data.BankBatchAction.PRIVATE_IGNORED)
                            com.example.data.BankTransactionClassification.PRIVATE_IGNORED
                        else com.example.data.BankTransactionClassification.TRANSFER
                        val updated = com.example.data.BankClassificationPolicy.classify(
                            transactionId = transaction.transactionId,
                            classification = target,
                            now = now
                        )
                        dao.updateTransactionClassification(
                            transactionId = transaction.transactionId,
                            classification = updated.classification,
                            transferCounterAccountId = updated.transferCounterAccountId,
                            linkedTransferTransactionId = updated.linkedTransferTransactionId,
                            reviewState = updated.reviewState,
                            updatedAt = now
                        )
                    }
                    com.example.data.BankBatchAction.NO_RECEIPT_REQUIRED -> {
                        dao.updateTransactionStatus(
                            transaction.transactionId,
                            com.example.data.BankReconciliationStatus.NO_RECEIPT_REQUIRED,
                            "Sammelaktion: Beleg nicht erforderlich",
                            now
                        )
                    }
                }
            }

            _bankImportStatus.value = buildString {
                append("Sammelaktion abgeschlossen: ${preview.eligibleCount} geändert")
                if (preview.unchangedCount > 0) append(" • ${preview.unchangedCount} bereits passend")
                if (preview.conflictCount > 0) append(" • ${preview.conflictCount} aus Sicherheitsgründen übersprungen")
                append(".")
            }
        }
    }

    fun markBankTransactionPrivateIgnored(transactionId: String) {
        classifyBankTransaction(transactionId, com.example.data.BankTransactionClassification.PRIVATE_IGNORED)
    }

    fun markBankTransactionTransfer(
        transactionId: String,
        transferCounterAccountId: String = "",
        linkedTransferTransactionId: String = ""
    ) {
        classifyBankTransaction(
            transactionId,
            com.example.data.BankTransactionClassification.TRANSFER,
            transferCounterAccountId,
            linkedTransferTransactionId
        )
    }

    fun resetBankTransactionClassification(transactionId: String) {
        classifyBankTransaction(transactionId, com.example.data.BankTransactionClassification.NORMAL)
    }

    private fun classifyBankTransaction(
        transactionId: String,
        classification: String,
        transferCounterAccountId: String = "",
        linkedTransferTransactionId: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = database.bankDao()
            val transaction = dao.getTransaction(transactionId) ?: return@launch
            val now = java.time.Instant.now().toString()
            if (classification != com.example.data.BankTransactionClassification.TRANSFER && transaction.linkedTransferTransactionId.isNotBlank()) {
                val counterpart = dao.getTransaction(transaction.linkedTransferTransactionId)
                if (counterpart?.linkedTransferTransactionId == transaction.transactionId) {
                    val counterpartUpdate = com.example.data.BankClassificationPolicy.classify(
                        transactionId = counterpart.transactionId,
                        classification = com.example.data.BankTransactionClassification.TRANSFER,
                        now = now
                    )
                    dao.updateTransactionClassification(
                        transactionId = counterpartUpdate.transactionId,
                        classification = counterpartUpdate.classification,
                        transferCounterAccountId = counterpartUpdate.transferCounterAccountId,
                        linkedTransferTransactionId = counterpartUpdate.linkedTransferTransactionId,
                        reviewState = counterpartUpdate.reviewState,
                        updatedAt = now
                    )
                }
            }
            val updated = com.example.data.BankClassificationPolicy.classify(
                transactionId = transaction.transactionId,
                classification = classification,
                transferCounterAccountId = transferCounterAccountId,
                linkedTransferTransactionId = linkedTransferTransactionId,
                now = now
            )
            dao.updateTransactionClassification(
                transactionId = transactionId,
                classification = updated.classification,
                transferCounterAccountId = updated.transferCounterAccountId,
                linkedTransferTransactionId = updated.linkedTransferTransactionId,
                reviewState = updated.reviewState,
                updatedAt = now
            )
            _bankImportStatus.value = when (updated.classification) {
                com.example.data.BankTransactionClassification.PRIVATE_IGNORED -> "Buchung als Privat/ignoriert markiert. Kein Belegabgleich und kein normaler DATEV-Export."
                com.example.data.BankTransactionClassification.TRANSFER -> "Buchung als Umbuchung markiert. Kein Belegabgleich und kein normaler Einnahmen-/Ausgabenexport."
                else -> "Sonderklassifikation entfernt. Buchung wird wieder normal geprüft."
            }
        }
    }

    fun startReceiptFromBankTransaction(transaction: com.example.data.BankTransaction) {
        _pendingBankTransactionId.value = transaction.transactionId
        _scanState.value = ScanUiState.Success(
            com.example.api.ExtractedReceipt(
                aussteller = transaction.counterparty,
                datum = transaction.bookingDate,
                uhrzeit = "",
                bruttobetrag = transaction.absoluteAmount,
                hauptkategorie = "",
                unterkategorie = "",
                kontoNr = "",
                beschreibung = transaction.purpose,
                isEigenleistungSanierung = false,
                wohneinheit = "",
                mieter = "",
                zahlungsart = "Überweisung",
                positionen = emptyList()
            )
        )
        _currentScreen.value = AppScreen.ADD_RECEIPT
    }

    fun startReceiptLikeLastMonth(
        transaction: com.example.data.BankTransaction,
        suggestion: com.example.data.BankLastMonthAssignmentSuggestion
    ) {
        if (suggestion.transactionId != transaction.transactionId ||
            com.example.data.BankTransactionClassification.normalize(transaction.classification) != com.example.data.BankTransactionClassification.NORMAL
        ) {
            _bankImportStatus.value = "Der Wiederholungs-Vorschlag ist nicht mehr aktuell."
            return
        }
        if (com.example.data.BankLastMonthAssignmentPolicy.hasAssignmentConflict(transaction, suggestion)) {
            _bankImportStatus.value = "Bestehende Objekt-/Einheitszuordnung weicht vom Vormonat ab. Nichts wurde überschrieben."
            return
        }
        suggestion.suggestedPropertyId.takeIf {
            it.isNotBlank() && it != com.example.data.StableDocumentIdentity.LEGACY_PROPERTY_ID
        }?.let(::selectProperty)
        _pendingBankTransactionId.value = transaction.transactionId
        _scanState.value = ScanUiState.Success(
            com.example.api.ExtractedReceipt(
                aussteller = suggestion.suggestedVendor.ifBlank { transaction.counterparty },
                datum = transaction.bookingDate,
                uhrzeit = "",
                bruttobetrag = transaction.absoluteAmount,
                hauptkategorie = suggestion.suggestedCategory,
                unterkategorie = suggestion.suggestedSubcategory,
                kontoNr = "",
                beschreibung = transaction.purpose,
                isEigenleistungSanierung = false,
                wohneinheit = suggestion.suggestedUnit,
                mieter = "",
                zahlungsart = suggestion.suggestedPaymentMethod.ifBlank { "Überweisung" },
                positionen = emptyList()
            )
        )
        _bankImportStatus.value = "Wie letzten Monat vorbefüllt. Es wurde ein neuer Belegentwurf erstellt; der alte Monatsbeleg wurde nicht verknüpft."
        _currentScreen.value = AppScreen.ADD_RECEIPT
    }

    fun startRentReceiptFromBankTransaction(
        transaction: com.example.data.BankTransaction,
        unit: WohneinheitStatus,
        tenantName: String
    ) {
        _pendingBankTransactionId.value = transaction.transactionId
        _scanState.value = ScanUiState.Success(
            com.example.api.ExtractedReceipt(
                aussteller = transaction.counterparty.ifBlank { tenantName },
                datum = transaction.bookingDate,
                uhrzeit = "",
                bruttobetrag = transaction.absoluteAmount,
                hauptkategorie = "Miete, Nebenkosten & Kaution",
                unterkategorie = "Warmmiete",
                kontoNr = "",
                beschreibung = transaction.purpose.ifBlank { "Mietzahlung ${unit.name}" },
                isEigenleistungSanierung = false,
                wohneinheit = unit.name,
                mieter = tenantName,
                zahlungsart = "Überweisung",
                positionen = emptyList()
            )
        )
        _currentScreen.value = AppScreen.ADD_RECEIPT
    }

    fun confirmBankReceiptLink(transactionId: String, receiptId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val transaction = database.bankDao().getTransaction(transactionId) ?: return@launch
            val receipt = repository.getReceiptById(receiptId) ?: return@launch
            _bankImportStatus.value = confirmBankReceiptLinkInternal(transaction, receipt)
        }
    }

    private suspend fun confirmBankReceiptLinkInternal(
        transaction: com.example.data.BankTransaction,
        receipt: Receipt
    ): String {
        val dao = database.bankDao()
        val allLinks = dao.getAllLinks()
        val allocation = com.example.data.BankLinkPolicy.propose(transaction, receipt, allLinks)
        if (!allocation.allowed) return allocation.reason
        dao.upsertLink(
            com.example.data.BankReceiptLink(
                linkId = com.example.data.BankLinkPolicy.linkId(transaction.transactionId, receipt.id, receipt.internalId),
                transactionId = transaction.transactionId,
                receiptId = receipt.id,
                receiptInternalId = receipt.internalId,
                allocatedAmount = allocation.amount,
                status = com.example.data.BankLinkStatus.CONFIRMED,
                source = com.example.data.BankLinkSource.NUTZER_BESTAETIGT,
                createdAt = java.time.Instant.now().toString()
            )
        )
        val propertyId = receipt.propertyId.takeIf { it.isNotBlank() }.orEmpty()
        val unitId = _wohneinheitenStatus.value.firstOrNull { unit ->
            receipt.wohneinheit.isNotBlank() && (unit.name.equals(receipt.wohneinheit, true) || unit.label.equals(receipt.wohneinheit, true))
        }?.let { unit -> PropertyUnitScopedData.stableUnitId(propertyId.ifBlank { com.example.data.StableDocumentIdentity.LEGACY_PROPERTY_ID }, unit) }.orEmpty()
        if ((transaction.propertyId.isBlank() && propertyId.isNotBlank()) || (transaction.unitId.isBlank() && unitId.isNotBlank())) {
            dao.upsertTransaction(
                transaction.copy(
                    propertyId = transaction.propertyId.ifBlank { propertyId },
                    unitId = transaction.unitId.ifBlank { unitId }
                )
            )
        }
        updateReceiptPaymentFromConfirmedBankMatch(receipt, transaction)
        refreshBankTransactionStatus(transaction.transactionId)
        val learning = com.example.data.BankLearningService(database.bankLearningRuleDao())
        learning.recordConfirmed(transaction, receipt)
        database.bankLearningRuleDao().getActiveRules().filter { com.example.data.BankRuleEngine.score(transaction, it) != null }.forEach { learning.recordRuleSuccess(it.ruleId) }
        return "Buchung und Beleg wurden bestätigt verbunden."
    }


    fun confirmPhase2DCombination(suggestionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val suggestion = bankPhase2DCombinations.value.firstOrNull { it.suggestionId == suggestionId }
            if (suggestion == null) {
                _bankImportStatus.value = "Kombinationsvorschlag ist nicht mehr aktuell."
                return@launch
            }
            _bankImportStatus.value = com.example.data.BankPhase2DService(database)
                .confirmCombination(suggestion, explicitlyConfirmed = true).message
        }
    }

    fun executePhase2DSafeBatch(stableKeys: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val selected = bankPhase2DReviewQueue.value.filter { it.stableKey in stableKeys }
            _bankImportStatus.value = com.example.data.BankPhase2DService(database)
                .executeSafeBatch(selected, explicitlyConfirmed = true).message
        }
    }

    fun changePhase2DAllocation(linkId: String, amount: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            _bankImportStatus.value = com.example.data.BankPhase2DService(database)
                .changeAllocation(linkId, amount, explicitlyConfirmed = true).message
        }
    }

    fun unlinkPhase2DLink(linkId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _bankImportStatus.value = com.example.data.BankPhase2DService(database).unlink(linkId).message
        }
    }

    fun confirmBankRentSuggestion(
        suggestion: BankRentSuggestion,
        asPartial: Boolean = false,
        allocatedAmount: Double? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val transaction = database.bankDao().getTransaction(suggestion.transactionId) ?: return@launch
            val alreadyConfirmed = database.bankRentAssignmentDao().confirmedRentAmount(
                suggestion.propertyId, suggestion.unitId, suggestion.rentMonth
            )
            val openBefore = (suggestion.expectedAmount - alreadyConfirmed).coerceAtLeast(0.0)
            val amount = allocatedAmount ?: if (asPartial) minOf(transaction.absoluteAmount, openBefore) else transaction.absoluteAmount
            if (amount <= 0.0 || amount > transaction.absoluteAmount + 0.01) {
                _bankImportStatus.value = "Mietzuordnung nicht gespeichert: Betrag ist unplausibel."
                return@launch
            }
            persistBankRentAssignment(transaction, suggestion, amount, com.example.data.BankRentAssignmentSource.USER_CONFIRMED)
        }
    }

    fun confirmManualBankRentAssignment(
        transactionId: String,
        propertyId: String,
        unitId: String,
        rentMonth: String,
        tenantReference: String,
        allocatedAmount: Double
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val transaction = database.bankDao().getTransaction(transactionId) ?: return@launch
            val property = properties.value.firstOrNull { it.propertyId == propertyId }
            val month = runCatching { java.time.YearMonth.parse(rentMonth) }.getOrNull()
            if (property == null || month == null || allocatedAmount <= 0.0 || allocatedAmount > transaction.absoluteAmount + 0.01) {
                _bankImportStatus.value = "Manuelle Mietzuordnung ungültig."
                return@launch
            }
            val unit = getWohneinheitenForProperty(property).firstOrNull {
                PropertyUnitScopedData.stableUnitId(propertyId, it) == unitId
            }
            if (unit == null) {
                _bankImportStatus.value = "Die gewählte Wohneinheit existiert nicht."
                return@launch
            }
            val periods = TenantHistoryStore.load(getApplication(), propertyId, unitId, unit.name)
            val validTenant = periods.any { "tenant-${it.id}" == tenantReference } ||
                (periods.isEmpty() && tenantReference == "tenant-${-unitId.hashCode().toLong()}")
            if (!validTenant) {
                _bankImportStatus.value = "Das gewählte Mietverhältnis existiert nicht."
                return@launch
            }
            val expected = RentTrackingLogic.month(getApplication(), propertyId, unit, receipts.value, month).expected
            if (expected <= 0.01) {
                _bankImportStatus.value = "Für diesen Mietmonat besteht kein Miet-Soll."
                return@launch
            }
            val tenantName = periods.firstOrNull { "tenant-${it.id}" == tenantReference }?.tenantName ?: unit.mieter
            val suggestion = BankRentSuggestion(
                transactionId = transactionId, propertyId = propertyId, unitId = unitId,
                tenantReference = tenantReference, tenantName = tenantName, rentMonth = rentMonth,
                expectedAmount = expected, actualAmount = transaction.absoluteAmount,
                difference = transaction.absoluteAmount - expected, score = 0,
                confidence = RentMatchConfidence.NIEDRIG,
                reasons = listOf("Manuell aus bestehenden Mietdaten gewählt"),
                paymentType = RentPaymentClassifier.classify(transaction),
                conflictState = RentConflictState.NONE,
                remainingAmount = (expected - allocatedAmount).coerceAtLeast(0.0),
                propertyLabel = property.name, unitName = unit.name
            )
            persistBankRentAssignment(transaction, suggestion, allocatedAmount, com.example.data.BankRentAssignmentSource.MANUAL)
        }
    }

    private suspend fun persistBankRentAssignment(
        transaction: com.example.data.BankTransaction,
        suggestion: BankRentSuggestion,
        amount: Double,
        source: String
    ) {
        val now = java.time.Instant.now().toString()
        val assignment = com.example.data.BankRentAssignment(
            assignmentId = com.example.data.BankRentAssignmentIdentity.id(
                transaction.transactionId, suggestion.propertyId, suggestion.unitId, suggestion.rentMonth, suggestion.paymentType
            ),
            transactionId = transaction.transactionId,
            propertyId = suggestion.propertyId,
            unitId = suggestion.unitId,
            rentMonth = suggestion.rentMonth,
            tenantReference = suggestion.tenantReference,
            allocatedAmount = amount,
            paymentType = suggestion.paymentType,
            status = com.example.data.BankRentAssignmentStatus.CONFIRMED,
            source = source,
            createdAt = now,
            updatedAt = now
        )
        database.bankRentAssignmentDao().upsert(assignment)
        if (transaction.propertyId.isBlank() || transaction.unitId.isBlank()) {
            database.bankDao().upsertTransaction(transaction.copy(
                propertyId = transaction.propertyId.ifBlank { suggestion.propertyId },
                unitId = transaction.unitId.ifBlank { suggestion.unitId },
                updatedAt = now
            ))
        }
        // A confirmed rental assignment may become Phase-2A evidence, but never activates a rule.
        val syntheticEvidenceReceipt = Receipt(
            id = 0,
            aussteller = suggestion.tenantName,
            datum = transaction.bookingDate,
            uhrzeit = "",
            bruttobetrag = amount,
            hauptkategorie = "Miete, Nebenkosten & Kaution",
            unterkategorie = when (suggestion.paymentType) {
                RentPaymentType.KAUTION -> "Kaution"
                RentPaymentType.NEBENKOSTEN -> "Betriebskosten/Nachzahlung"
                else -> "Warmmiete"
            },
            kontoNr = "",
            beschreibung = transaction.purpose,
            wohneinheit = suggestion.unitName,
            mieter = suggestion.tenantName,
            zahlungsart = "Überweisung",
            propertyId = suggestion.propertyId
        )
        com.example.data.BankLearningService(database.bankLearningRuleDao()).recordConfirmed(transaction, syntheticEvidenceReceipt)
        _dismissedBankRentTransactions.value = _dismissedBankRentTransactions.value - transaction.transactionId
        _bankImportStatus.value = "Mietzahlung wurde vom Nutzer bestätigt. Keine automatische Beleg-, DATEV- oder Steuerbuchung."
    }

    fun unlinkBankRentAssignment(assignmentId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val assignment = database.bankRentAssignmentDao().getById(assignmentId) ?: return@launch
            database.bankRentAssignmentDao().deleteById(assignmentId)
            refreshBankTransactionStatus(assignment.transactionId)
            _bankImportStatus.value = "Mietzuordnung wurde gelöst; Restbetrag und Mietstatus werden neu abgeleitet."
        }
    }

    fun dismissBankRentSuggestion(transactionId: String) {
        _dismissedBankRentTransactions.value = _dismissedBankRentTransactions.value + transactionId
        _bankImportStatus.value = "Buchung wird in dieser Prüfung nicht als Miete vorgeschlagen."
    }

    fun saveBankRule(rule: com.example.data.BankLearningRule) { viewModelScope.launch(Dispatchers.IO) { database.bankLearningRuleDao().upsertRule(rule) } }
    fun acceptBankRule(ruleId: String) { viewModelScope.launch(Dispatchers.IO) { com.example.data.BankLearningService(database.bankLearningRuleDao()).accept(ruleId) } }
    fun rejectBankRule(ruleId: String) { viewModelScope.launch(Dispatchers.IO) { com.example.data.BankLearningService(database.bankLearningRuleDao()).reject(ruleId) } }
    fun setBankRuleEnabled(ruleId: String, enabled: Boolean) { viewModelScope.launch(Dispatchers.IO) { val dao=database.bankLearningRuleDao(); val r=dao.getRule(ruleId)?:return@launch; dao.upsertRule(r.copy(enabled=enabled, state=com.example.data.BankRuleState.ACTIVE, updatedAt=java.time.Instant.now().toString())) } }
    fun deleteBankRule(ruleId: String) { viewModelScope.launch(Dispatchers.IO) { database.bankLearningRuleDao().deleteRule(ruleId) } }
    fun rejectBankRuleMatch(ruleId: String) { viewModelScope.launch(Dispatchers.IO) { com.example.data.BankLearningService(database.bankLearningRuleDao()).recordRuleRejection(ruleId) } }

    private suspend fun refreshBankTransactionStatus(transactionId: String) {
        val dao = database.bankDao()
        val transaction = dao.getTransaction(transactionId) ?: return
        val status = com.example.data.BankTransactionSplitPolicy.statusFor(transaction, dao.getLinksForTransaction(transactionId), database.bankRentAssignmentDao().getForTransaction(transactionId))
        dao.updateTransactionStatus(transactionId, status, "", java.time.Instant.now().toString())
    }

    private suspend fun updateReceiptPaymentFromConfirmedBankMatch(
        receipt: Receipt,
        transaction: com.example.data.BankTransaction
    ) {
        val decision = com.example.data.BankPaymentMethodPolicy.fromConfirmedBankMatch(transaction, receipt) ?: return
        repository.insert(
            receipt.copy(
                zahlungsart = decision.method,
                zahlungsartQuelle = decision.source,
                zahlungsartConfidence = decision.confidence
            )
        )
    }

    fun markBankTransactionNoReceiptRequired(transactionId: String, reason: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = database.bankDao()
            if (dao.getLinksForTransaction(transactionId).isNotEmpty()) {
                _bankImportStatus.value =
                    "Die Buchung ist bereits mit einem Beleg verbunden. Verknüpfung zuerst lösen."
                return@launch
            }
            dao.updateTransactionStatus(
                transactionId,
                com.example.data.BankReconciliationStatus.NO_RECEIPT_REQUIRED,
                reason.trim(),
                java.time.Instant.now().toString()
            )
            _bankImportStatus.value = "Als „kein Beleg erforderlich“ markiert: ${reason.trim()}."
        }
    }

    fun markBankTransactionForReview(transactionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val status = com.example.data.BankReviewStatusService(database.bankDao())
                .markForReview(transactionId)
            _bankImportStatus.value = when (status) {
                com.example.data.BankReconciliationStatus.REVIEW -> "Buchung wurde zur manuellen Prüfung markiert."
                com.example.data.BankReconciliationStatus.PARTIAL,
                com.example.data.BankReconciliationStatus.MATCHED -> "Buchung bleibt gemäß bestehender Zuordnung verknüpft."
                com.example.data.BankReconciliationStatus.NO_RECEIPT_REQUIRED -> "Buchung zuerst wieder öffnen, bevor sie manuell geprüft wird."
                else -> "Bankstatus blieb unverändert."
            }
        }
    }

    fun reopenBankTransaction(transactionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val status = com.example.data.BankReviewStatusService(database.bankDao())
                .reopen(transactionId)
            _bankImportStatus.value = when (status) {
                com.example.data.BankReconciliationStatus.OPEN -> "Buchung wurde wieder zur Prüfung geöffnet."
                com.example.data.BankReconciliationStatus.PARTIAL,
                com.example.data.BankReconciliationStatus.MATCHED -> "Buchung bleibt gemäß bestehender Zuordnung verknüpft."
                else -> "Bankstatus blieb unverändert."
            }
        }
    }

    fun proposeBankRuleFromConfirmedReceipt(transactionId: String, receiptId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val bankDao = database.bankDao()
            val transaction = bankDao.getTransaction(transactionId) ?: return@launch
            val receipt = repository.getReceiptById(receiptId) ?: return@launch
            val confirmed = bankDao.getLinksForTransaction(transactionId).any {
                it.receiptId == receiptId && it.status == com.example.data.BankLinkStatus.CONFIRMED
            }
            if (!confirmed) {
                _bankImportStatus.value = "Regel nicht erstellt: Die Belegzuordnung ist nicht bestätigt."
                return@launch
            }
            val now = java.time.Instant.now().toString()
            val rule = com.example.data.BankExplicitRuleProposal.create(transaction, receipt, now)
            val dao = database.bankLearningRuleDao()
            when (dao.getRule(rule.ruleId)?.state) {
                com.example.data.BankRuleState.ACTIVE -> {
                    _bankImportStatus.value = "Eine passende aktive Bankregel existiert bereits."
                    return@launch
                }
                com.example.data.BankRuleState.REJECTED -> {
                    _bankImportStatus.value = "Eine passende Regel wurde früher abgelehnt. Sie kann in Bankregeln geprüft werden."
                    return@launch
                }
            }
            dao.upsertRule(rule)
            _bankImportStatus.value = "Regelvorschlag gespeichert. Er ist deaktiviert und muss in Bankregeln ausdrücklich aktiviert werden."
        }
    }

    fun removeBankReceiptLink(linkId: String, transactionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = database.bankDao()
            val link = dao.getLink(linkId)
            dao.deleteLink(linkId)
            refreshBankTransactionStatus(transactionId)
            if (link != null) {
                val receipt = repository.getReceiptById(link.receiptId)
                val remainingReceiptLinks = dao.getLinksForReceipt(link.receiptId, link.receiptInternalId)
                if (receipt != null && remainingReceiptLinks.isEmpty() && receipt.zahlungsartQuelle.equals("BANKABGLEICH", true)) {
                    repository.insert(
                        receipt.copy(
                            zahlungsart = "Unbekannt",
                            zahlungsartQuelle = "UNBEKANNT",
                            zahlungsartConfidence = 0.0
                        )
                    )
                }
            }
            _bankImportStatus.value = "Zuordnung wurde gelöst."
        }
    }

    fun confirmBankLoanAssignment(transactionId: String, loanId: Int, paymentType: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val transaction = database.bankDao().getTransaction(transactionId) ?: return@launch
            val loan = database.loanDao().getAllLoans().firstOrNull { it.id == loanId } ?: return@launch
            val account = database.bankDao().getAccount(transaction.accountId)
            val rules = database.bankLearningRuleDao().getAllRules()
            val history = database.bankDao().getAllTransactions()
            val suggestion = com.example.data.BankLoanMatcher.score(transaction, loan, account, history, rules)
            val effectiveType = paymentType ?: suggestion.paymentType
            val split = if (effectiveType == com.example.data.BankLoanPaymentType.REGULAERE_RATE)
                com.example.data.BankLoanSplitProposer.propose(loan, transaction.absoluteAmount, suggestion.period)
            else null
            com.example.data.BankLoanAssignmentService(database.bankLoanAssignmentDao(), database.bankDao())
                .confirm(transaction, loan, suggestion, effectiveType, split)
            _dismissedBankLoanTransactions.value = _dismissedBankLoanTransactions.value - transactionId
            _bankImportStatus.value = if (suggestion.conflictState == com.example.data.BankLoanConflictState.NONE)
                "Darlehenszuordnung bestätigt. Zins/Tilgung bleibt ein prüfbarer Vorschlag."
            else "Darlehenszuordnung gespeichert und wegen Konflikt zur Prüfung markiert."
        }
    }

    fun markBankLoanAsSpecialRepayment(transactionId: String, loanId: Int) =
        confirmBankLoanAssignment(transactionId, loanId, com.example.data.BankLoanPaymentType.SONDERTILGUNG)

    fun dismissBankLoanSuggestion(transactionId: String) {
        _dismissedBankLoanTransactions.value = _dismissedBankLoanTransactions.value + transactionId
        _bankImportStatus.value = "Buchung wird nicht als Darlehen vorgeschlagen; Bank-/Beleglinks bleiben unverändert."
    }

    fun unlinkBankLoanAssignment(transactionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            com.example.data.BankLoanAssignmentService(database.bankLoanAssignmentDao(), database.bankDao()).unlink(transactionId)
            _bankImportStatus.value = "Darlehenszuordnung gelöst; bestehende Beleglinks wurden nicht verändert."
        }
    }

    fun updateBankLoanSplit(transactionId: String, interest: Double?, principal: Double?, accept: Boolean, edited: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            com.example.data.BankLoanAssignmentService(database.bankLoanAssignmentDao(), database.bankDao())
                .updateSplit(transactionId, interest, principal, accept, edited)
            _bankImportStatus.value = if (accept) "Zins-/Tilgungsvorschlag aktualisiert." else "Zins-/Tilgungsvorschlag abgelehnt."
        }
    }

    fun proposeBankRuleFromRecurringPattern(patternId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val pattern = bankRecurringAnalysis.value.patterns.firstOrNull { it.patternId == patternId } ?: return@launch
            if (pattern.occurrenceCount < com.example.data.BankRecurringThresholds.MIN_OCCURRENCES_FOR_PATTERN) {
                _bankImportStatus.value = "Für einen Regelvorschlag fehlen noch bestätigende Vorkommen."
                return@launch
            }
            val dao = database.bankLearningRuleDao()
            val ruleId = "rule-rec-" + com.example.data.BankTransactionIdentity.sha256(pattern.patternId).take(24)
            val existing = dao.getRule(ruleId)
            if (existing?.state == com.example.data.BankRuleState.ACTIVE) {
                _bankImportStatus.value = "Die passende Bankregel ist bereits aktiv."
                return@launch
            }
            val now = java.time.Instant.now().toString()
            val iban = pattern.normalizedCounterparty.removePrefix("iban:").takeIf { pattern.normalizedCounterparty.startsWith("iban:") }.orEmpty()
            val counterparty = pattern.normalizedCounterparty.takeUnless { it.startsWith("iban:") }.orEmpty()
            val terms = pattern.purposeFingerprint.split(' ').filter { it.length >= 3 }.take(6).joinToString("|")
            val direction = if (pattern.direction == com.example.data.RecurringDirection.INCOME)
                com.example.data.BankRuleDirection.INCOME else com.example.data.BankRuleDirection.EXPENSE
            dao.upsertRule(
                com.example.data.BankLearningRule(
                    ruleId = ruleId,
                    displayName = "Wiederkehrend: ${pattern.normalizedCounterparty.ifBlank { pattern.purposeFingerprint.ifBlank { "Bankzahlung" } }}",
                    enabled = false,
                    state = com.example.data.BankRuleState.PROPOSED,
                    ruleType = com.example.data.BankRuleType.COMBINED,
                    transactionDirection = direction,
                    counterpartyPattern = counterparty,
                    counterpartyIbanPattern = iban,
                    purposeTerms = terms,
                    amountMin = (pattern.typicalAmount - pattern.amountTolerance).coerceAtLeast(0.0),
                    amountMax = pattern.typicalAmount + pattern.amountTolerance,
                    currency = "EUR",
                    accountId = pattern.accountId,
                    propertyId = pattern.propertyId,
                    evidenceCount = pattern.occurrenceCount,
                    confidence = pattern.confidence,
                    source = com.example.data.BankRuleSource.USER_CREATED,
                    createdAt = existing?.createdAt?.ifBlank { now } ?: now,
                    updatedAt = now
                )
            )
            _bankImportStatus.value = "Regelvorschlag erstellt. Er ist deaktiviert und muss in Bankregeln ausdrücklich aktiviert werden."
        }
    }

    fun saveRecurringPattern(patternId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val detected = bankRecurringAnalysis.value.patterns.firstOrNull { it.patternId == patternId } ?: return@launch
            val existing = database.bankRecurringPatternDao().get(patternId)
            val now = java.time.Instant.now().toString()
            database.bankRecurringPatternDao().upsert(detected.toEntity(now).copy(createdAt = existing?.createdAt ?: now, enabled = existing?.enabled ?: true))
            _bankImportStatus.value = "Wiederkehrendes Muster gespeichert. Es erzeugt keine Buchung und keine Zahlung."
        }
    }

    fun setRecurringPatternEnabled(patternId: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            database.bankRecurringPatternDao().setEnabled(patternId, enabled, java.time.Instant.now().toString())
        }
    }

    fun resetScanState() {
        _pendingBankTransactionId.value = null
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

    private val _wizardExclusionReasons = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val wizardExclusionReasons: StateFlow<Map<String, List<String>>> =
        _wizardExclusionReasons.asStateFlow()

    private val _wizardIncludedReceipts = MutableStateFlow<List<Receipt>>(emptyList())
    val wizardIncludedReceipts: StateFlow<List<Receipt>> = _wizardIncludedReceipts.asStateFlow()

    private val _wizardValidationReport = MutableStateFlow<com.example.util.ValidationReport?>(null)
    val wizardValidationReport: StateFlow<com.example.util.ValidationReport?> = _wizardValidationReport.asStateFlow()

    private val _lastExportResult = MutableStateFlow<com.example.util.AdvisorPackageResult?>(null)
    val lastExportResult: StateFlow<com.example.util.AdvisorPackageResult?> = _lastExportResult.asStateFlow()

    fun recalculateWizardStepData(
        allRecs: List<Receipt> = receipts.value
    ) {
        val profile = _activeDatevProfile.value
        val unitFilter = _wizardUnitFilter.value
        val yearFilter = _wizardYearFilter.value
        val typeFilter = _wizardCategoryTypeFilter.value
        val duplicateInternalIds =
            com.example.util.DatevReceiptEligibility.duplicateInternalIds(allRecs)

        val included = mutableListOf<Receipt>()
        val excluded = mutableListOf<Receipt>()
        val exclusionReasons = linkedMapOf<String, List<String>>()
        val currentBankLinks = bankReceiptLinks.value
        val currentBankTransactions = bankTransactions.value

        allRecs.forEach { receipt ->
            val reasons = mutableListOf<String>()

            if (unitFilter != "ALLE" && receipt.wohneinheit != unitFilter) {
                reasons += "Wohneinheit entspricht nicht dem gewählten Filter."
            }
            if (yearFilter != "ALLE" && !receipt.datum.startsWith(yearFilter)) {
                reasons += "Belegdatum liegt außerhalb des gewählten Jahres."
            }
            if (typeFilter == "EINNAHMEN" &&
                !receipt.hauptkategorie.contains("Einnahmen", true) &&
                !receipt.hauptkategorie.contains("Miete", true)
            ) {
                reasons += "Beleg ist keine Einnahme."
            }
            if (typeFilter == "AUSGABEN" &&
                (receipt.hauptkategorie.contains("Einnahmen", true) ||
                    receipt.hauptkategorie.contains("Miete", true))
            ) {
                reasons += "Beleg ist keine Ausgabe."
            }
            reasons += com.example.util.DatevReceiptEligibility.issues(receipt)
                .map { it.message }
            reasons += com.example.data.BankLinkedReceiptDatevPolicy.exclusions(
                receipt = receipt,
                links = currentBankLinks,
                transactions = currentBankTransactions
            ).map { "${it.code}: ${it.message}" }
            if (receipt.internalId.trim() in duplicateInternalIds) {
                reasons += "Stabile Beleg-ID kommt mehrfach vor; Export ist bis zur Dublettenbereinigung blockiert."
            }
            if (_wizardTargetFormat.value == "FULL_ZIP" &&
                com.example.util.DatevOriginalAttachmentPolicy.resolve(receipt) == null
            ) {
                reasons += "Originalbeleg ist lokal nicht verfügbar oder hat ein nicht unterstütztes Format."
            }

            if (reasons.isEmpty()) {
                included += receipt
            } else {
                excluded += receipt
                exclusionReasons[com.example.util.DatevReceiptEligibility.key(receipt)] =
                    reasons.distinct()
            }
        }

        val bookingRecords = included.flatMap { receipt ->
            com.example.util.DatevMappingService.mapReceiptToBookingRecords(receipt, profile)
        }

        val report = com.example.util.BookingValidationService.validateRecords(
            records = bookingRecords,
            profile = profile,
            // DATEV packages must never contain unverified accounting proposals.
            allowUnverifiedExport = false
        )

        _wizardIncludedReceipts.value = included
        _wizardMappedRecords.value = bookingRecords
        _wizardExcludedReceipts.value = excluded
        _wizardExclusionReasons.value = exclusionReasons
        _wizardValidationReport.value = report
    }

    internal fun buildAdvisorAnnualSummaryForExport(
        context: Context,
        year: Int,
        currentReceipts: List<Receipt> = propertyReceipts.value,
        currentMetadata: PropertyMetadata = propertyMetadata.value ?: PropertyMetadata(),
        currentLoans: List<com.example.data.Loan> = loans.value,
        currentUnits: List<WohneinheitStatus> = _wohneinheitenStatus.value
    ): com.example.util.AdvisorAnnualSummary = buildAdvisorAnnualSummary(
        context = context.applicationContext,
        year = year,
        receipts = currentReceipts,
        metadata = currentMetadata,
        loans = currentLoans,
        units = currentUnits
    )

    suspend fun executeWizardExport(
        context: Context
    ): com.example.util.AdvisorPackageResult? {
        val currentReceipts = database.receiptDao().getAllReceiptsList()
        recalculateWizardStepData(currentReceipts)
        val records = _wizardMappedRecords.value
        val excluded = _wizardExcludedReceipts.value
        val profile = _activeDatevProfile.value
        val report = _wizardValidationReport.value ?: return null
        if (!report.isValidForExport || records.isEmpty()) {
            Log.w("ReceiptViewModel", "DATEV export blocked by validation policy")
            return null
        }
        val selectedYear = _wizardYearFilter.value.toIntOrNull()
        if (_wizardTargetFormat.value == "FULL_ZIP" && selectedYear == null) {
            Log.w(
                "ReceiptViewModel",
                "Steuerberaterpaket benötigt ein eindeutig ausgewähltes Steuerjahr"
            )
            return null
        }

        val packageResult = com.example.util.AdvisorPackageBuilder.buildPackage(
            context = context,
            records = records,
            includedReceipts = _wizardIncludedReceipts.value,
            excludedReceipts = excluded,
            includeOriginals = _wizardTargetFormat.value == "FULL_ZIP",
            profile = profile,
            validationReport = report,
            periodSummary = _wizardYearFilter.value,
            annualSummary = selectedYear?.let { year ->
                buildAdvisorAnnualSummaryForExport(
                    context = context,
                    year = year,
                    currentReceipts = currentReceipts,
                    currentMetadata = propertyMetadata.value
                        ?: database.propertyDao().getPropertyMetadata()
                        ?: PropertyMetadata(),
                    currentLoans = database.loanDao().getAllLoans(),
                    currentUnits = _wohneinheitenStatus.value
                )
            }
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
                exportierteReceiptIdsJson = org.json.JSONArray(
                    records.map { it.receiptId }
                        .distinct()
                        .mapNotNull { receiptId ->
                            receipts.value.firstOrNull { it.id == receiptId }
                                ?.internalId
                                ?.takeIf(String::isNotBlank)
                        }
                ).toString(),
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

        val bankDatevExclusions = receipts.flatMap { receipt ->
            com.example.data.BankLinkedReceiptDatevPolicy.exclusions(
                receipt = receipt,
                links = bankReceiptLinks.value,
                transactions = bankTransactions.value
            ).map { exclusion -> "${exclusion.code}: ${exclusion.message}" }
        }
        if (bankDatevExclusions.isNotEmpty()) {
            Log.w("ReceiptViewModel", "DATEV export blocked by bank classification: ${bankDatevExclusions.joinToString(" | ")}")
            return null
        }

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
        if (screen != AppScreen.ADD_RECEIPT) {
            _pendingBankTransactionId.value = null
        }
        _currentScreen.value = screen
        _scanState.value = ScanUiState.Idle
    }

    fun selectProperty(propertyId: String) {
        _selectedPropertyId.value = propertyId
        sharedPrefs.edit().putString("selected_property_id", propertyId).apply()
    }

    fun createProperty(metadata: PropertyMetadata, units: List<WohneinheitStatus>, loan: com.example.data.Loan? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val propertyId = metadata.propertyId.ifBlank { java.util.UUID.randomUUID().toString() }
            val stored = metadata.copy(id = repository.nextPropertyId(), propertyId = propertyId)
            repository.updatePropertyMetadata(stored)
            loan?.let { database.loanDao().upsertLoan(it.copy(propertyId = propertyId)) }
            val unitPrefs = getApplication<Application>().getSharedPreferences("wohneinheiten_prefs", Context.MODE_PRIVATE)
            units.forEachIndexed { index, unit ->
                val stableId = unit.unitId.ifBlank { com.example.data.StableDocumentIdentity.legacyUnitId(propertyId, unit.name) }
                val prefix = "property_${propertyId}_unit_${unit.name}_"
                unitPrefs.edit()
                    .putString(prefix + "id", stableId)
                    .putString(prefix + "label", unit.label)
                    .putString(prefix + "status", unit.status)
                    .putString(prefix + "mieter", unit.mieter)
                    .putFloat(prefix + "rent", unit.kaltmiete.toFloat())
                    .putFloat(prefix + "area", unit.wohnflaeche.toFloat())
                    .putString(prefix + "start", unit.mietvertragsstart)
                    .putString("property_${propertyId}_unit_id_index_$index", stableId)
                    .apply()
            }
            selectProperty(propertyId)
        }
    }

    fun importManagedDocument(uri: android.net.Uri, unitId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _documentOperationStatus.value = "Dokument wird geprüft …"
            val property = propertyMetadata.value ?: com.example.data.PropertyMetadata()
            when (val result = managedDocumentService.prepareImport(uri, property, unitId)) {
                is com.example.data.ManagedDocumentImportResult.Imported -> {
                    _documentOperationStatus.value = "Dokument importiert. OCR und KI-Zuordnung laufen …"
                    managedDocumentService.runOcr(result.document.documentId)
                    val analysis = managedDocumentService.analyze(result.document.documentId, property, _wohneinheitenStatus.value)
                    if (analysis != null) {
                        _documentAiReview.value = result.document.documentId to analysis
                        _documentOperationStatus.value = "Erkannte Daten müssen vor der Übernahme geprüft werden."
                    } else {
                        _documentOperationStatus.value = "Dokument und OCR-Text gespeichert. KI-Analyse derzeit nicht verfügbar."
                    }
                }
                is com.example.data.ManagedDocumentImportResult.ExactDuplicate ->
                    _documentOperationStatus.value = "Identisches Original bereits vorhanden: ${result.existing.title}"
                is com.example.data.ManagedDocumentImportResult.PossibleDuplicate -> {
                    pendingDocumentDuplicateUri = uri
                    _pendingDocumentDuplicate.value = result.candidate to result.existing
                    _documentOperationStatus.value = "Mögliche Dublette gefunden. Bitte entscheiden."
                }
                is com.example.data.ManagedDocumentImportResult.Error -> _documentOperationStatus.value = result.message
            }
        }
    }

    fun resolvePossibleDocumentDuplicate(useExisting: Boolean, keepSeparate: Boolean = false) {
        val pending = _pendingDocumentDuplicate.value ?: return
        val uri = pendingDocumentDuplicateUri
        viewModelScope.launch(Dispatchers.IO) {
            when {
                useExisting -> _documentOperationStatus.value = "Vorhandenes Dokument wird weiterverwendet."
                keepSeparate && uri != null -> {
                    val result = managedDocumentService.persistPossibleDuplicate(pending.first, uri)
                    if (result is com.example.data.ManagedDocumentImportResult.Imported) {
                        managedDocumentService.runOcr(result.document.documentId)
                        val analysis = managedDocumentService.analyze(result.document.documentId, propertyMetadata.value ?: com.example.data.PropertyMetadata(), _wohneinheitenStatus.value)
                        if (analysis != null) _documentAiReview.value = result.document.documentId to analysis
                        _documentOperationStatus.value = "Dokument separat importiert und zur Prüfung vorbereitet."
                    } else _documentOperationStatus.value = "Separater Import fehlgeschlagen."
                }
                else -> _documentOperationStatus.value = "Import abgebrochen."
            }
            pendingDocumentDuplicateUri = null
            _pendingDocumentDuplicate.value = null
        }
    }

    fun searchDocuments(query: String, propertyId: String = "", unitId: String = "", year: String = "", type: String = "", category: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            _documentSearchResults.value = repository.searchManagedDocuments(query, propertyId, unitId, year, type, category)
        }
    }

    fun runDocumentOcr(documentId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _documentOperationStatus.value = "Texterkennung läuft …"
            val updated = managedDocumentService.runOcr(documentId)
            _documentOperationStatus.value = if (updated?.ocrStatus == com.example.data.DocumentProcessingStatus.ERFOLGREICH.name) "Texterkennung abgeschlossen." else "Texterkennung fehlgeschlagen."
        }
    }

    fun downloadManagedDocument(documentId: String) {
        val email = _googleAccountEmail.value
        if (email.isNullOrBlank() || !_isDriveConnected.value) {
            _documentOperationStatus.value = "Bitte zuerst Google Drive verbinden."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val document = repository.getManagedDocument(documentId) ?: return@launch
            val fileId = document.driveFileId?.takeIf(String::isNotBlank) ?: return@launch
            _documentOperationStatus.value = "Original wird aus Google Drive geladen …"
            try {
                val bytes = GoogleDriveClient.downloadFileBytes(getValidToken(email), fileId)
                    ?: throw IllegalStateException("Original nicht gefunden")
                if (document.sha256.isNotBlank() && com.example.data.StableDocumentIdentity.sha256(bytes) != document.sha256) {
                    throw IllegalStateException("Sicherheitsprüfung des Originals fehlgeschlagen")
                }
                val extension = document.storedFilename.substringAfterLast('.', "bin")
                val file = java.io.File(getApplication<Application>().filesDir, "managed_documents/${document.documentId}.$extension")
                file.parentFile?.mkdirs(); file.outputStream().use { it.write(bytes) }
                repository.upsertManagedDocument(document.copy(localUri = file.absolutePath, fileSizeBytes = bytes.size.toLong(), updatedAt = java.time.Instant.now().toString()))
                _documentOperationStatus.value = "Original lokal verfügbar."
            } catch (e: Exception) {
                _documentOperationStatus.value = "Original konnte nicht geladen werden: ${e.message}"
            }
        }
    }

    fun analyzeManagedDocument(documentId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _documentOperationStatus.value = "KI-Dokumentanalyse läuft …"
            val result = managedDocumentService.analyze(documentId, propertyMetadata.value ?: com.example.data.PropertyMetadata(), _wohneinheitenStatus.value)
            if (result != null) {
                _documentAiReview.value = documentId to result
                _documentOperationStatus.value = "Erkannte Daten müssen geprüft werden."
            } else _documentOperationStatus.value = "KI-Dokumentanalyse nicht verfügbar oder fehlgeschlagen."
        }
    }

    fun dismissDocumentAiReview() { _documentAiReview.value = null }

    fun previewDocumentStorageMigration() {
        val email = _googleAccountEmail.value
        if (email.isNullOrBlank() || !_isDriveConnected.value) {
            _documentOperationStatus.value = "Bitte zuerst Google Drive verbinden."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _documentOperationStatus.value = "Bestehende Drive-Ablage wird nur lesend geprüft …"
            try {
                val token = getValidToken(email)
                val config = drivePersistenceRepository.getExistingDriveAppConfigReadOnly(token)
                    ?: throw IllegalStateException("Keine bestehende Drive-Ablage gefunden.")
                _documentMigrationPreview.value = drivePersistenceRepository.previewReceiptDocumentMigration(token, config)
                _documentOperationStatus.value = "Migrationsvorschau erstellt. Noch wurde keine Datei verändert."
            } catch (e: Exception) {
                _documentOperationStatus.value = "Migrationsvorschau fehlgeschlagen: ${e.message}"
            }
        }
    }

    fun dismissDocumentMigrationPreview() { _documentMigrationPreview.value = null }

    fun confirmDocumentStorageMigration() {
        val preview = _documentMigrationPreview.value ?: return
        val email = _googleAccountEmail.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _documentOperationStatus.value = "Bestätigte Dokumentmigration läuft …"
            try {
                val token = getValidToken(email)
                val config = drivePersistenceRepository.getExistingDriveAppConfigReadOnly(token)
                    ?: throw IllegalStateException("Die geprüfte Drive-Ablage ist nicht mehr erreichbar.")
                val result = drivePersistenceRepository.executeReceiptDocumentMigration(token, config, preview)
                _documentOperationStatus.value = "Migration abgeschlossen: ${result.found} inventarisiert, ${result.manualReview} manuell zu klären."
                _documentMigrationPreview.value = null
            } catch (e: Exception) {
                _documentOperationStatus.value = "Migration unterbrochen. Der Journalstand bleibt erhalten: ${e.message}"
            }
        }
    }

    fun confirmManagedDocumentReview(
        documentId: String,
        type: com.example.data.ManagedDocumentType,
        date: String,
        unitId: String?,
        proposals: List<com.example.data.DocumentFieldProposal>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val document = repository.getManagedDocument(documentId) ?: return@launch
            val accepted = com.example.data.DocumentReviewPolicy.confirmedValues(proposals)
            val json = org.json.JSONObject().apply { accepted.forEach { (key, value) -> put(key, value) } }.toString()
            val updated = managedDocumentService.confirmReview(document, type, date, unitId, json)
            applyConfirmedDocumentValues(updated, accepted)
            _documentAiReview.value = null
            val email = _googleAccountEmail.value
            if (!email.isNullOrBlank() && _isDriveConnected.value) {
                val synced = try {
                    val token = getValidToken(email)
                    val config = drivePersistenceRepository.getExistingDriveAppConfigReadOnly(token)
                    config != null && drivePersistenceRepository.syncManagedDocumentToDrive(token, config, documentId)
                } catch (e: Exception) {
                    Log.w("ReceiptViewModel", "Confirmed document remains pending for Drive sync", e)
                    false
                }
                _documentOperationStatus.value = if (synced) {
                    "Geprüfte Dokumentdaten übernommen und Drive-Ablage aktualisiert."
                } else {
                    "Geprüfte Dokumentdaten lokal übernommen. Drive-Synchronisierung muss erneut geprüft werden."
                }
            } else {
                _documentOperationStatus.value = "Geprüfte Dokumentdaten lokal übernommen. Drive-Synchronisierung folgt bei der nächsten Verbindung."
            }
        }
    }

    private suspend fun applyConfirmedDocumentValues(document: com.example.data.ManagedDocument, values: Map<String, String>) {
        fun number(key: String): Double? {
            val raw = values[key]?.trim()?.filter { it.isDigit() || it == '.' || it == ',' || it == '-' } ?: return null
            val decimalSeparator = when {
                raw.contains(',') -> ','
                raw.count { it == '.' } == 1 && raw.substringAfter('.').length <= 2 -> '.'
                else -> null
            }
            return raw.filterNot { it == '.' || it == ',' }
                .let { digits ->
                    if (decimalSeparator == null) digits
                    else {
                        val fraction = raw.substringAfterLast(decimalSeparator).filter(Char::isDigit)
                        val whole = raw.substringBeforeLast(decimalSeparator).filter { it.isDigit() || it == '-' }
                        "$whole.$fraction"
                    }
                }.toDoubleOrNull()
        }
        val currentProperty = database.propertyDao().getPropertyMetadata() ?: com.example.data.PropertyMetadata()
        var property = currentProperty
        values["objektadresse"]?.let { property = property.copy(adresse = it) }
        number("kaufpreis")?.let { property = property.copy(gesamtKaufpreis = it) }
        values["kaufvertragsdatum"]?.let { property = property.copy(notariellesKaufdatum = it) }
        values["nutzen_lasten"]?.let { property = property.copy(uebergangNutzenLasten = it) }
        number("grundstuecksflaeche")?.let { property = property.copy(grundstuecksgroesse = it) }
        number("baujahr")?.toInt()?.let { property = property.copy(baujahr = it) }
        if (property != currentProperty) repository.updatePropertyMetadata(property)

        if (document.documentType == com.example.data.ManagedDocumentType.DARLEHENSVERTRAG.name) {
            val existing = document.loanId?.let { id -> database.loanDao().getAllLoans().firstOrNull { it.id == id } }
            val loan = (existing ?: com.example.data.Loan(propertyId = document.propertyId)).copy(
                bank = values["bank"] ?: existing?.bank.orEmpty(),
                darlehensbetrag = number("darlehensbetrag") ?: existing?.darlehensbetrag ?: 0.0,
                restschuld = number("restschuld") ?: existing?.restschuld ?: 0.0,
                sollzinsProzent = number("sollzins") ?: existing?.sollzinsProzent ?: 0.0,
                tilgungProzent = number("tilgung") ?: existing?.tilgungProzent ?: 0.0,
                monatlicheRate = number("monatsrate") ?: existing?.monatlicheRate ?: 0.0,
                startDatum = values["startdatum"] ?: existing?.startDatum.orEmpty(),
                zinsbindungBis = values["zinsbindung"] ?: existing?.zinsbindungBis.orEmpty(),
                laufzeitBis = values["laufzeit"] ?: existing?.laufzeitBis.orEmpty()
            )
            val loanId = database.loanDao().upsertLoan(loan).toInt()
            repository.upsertManagedDocument(document.copy(loanId = if (loan.id != 0) loan.id else loanId))
        }

        val unit = _wohneinheitenStatus.value.firstOrNull { it.unitId == document.unitId }
        if (unit != null && document.documentType == com.example.data.ManagedDocumentType.MIETVERTRAG.name) {
            updateWohneinheit(unit.copy(
                mieter = values["mieter"] ?: unit.mieter,
                kaltmiete = number("kaltmiete") ?: unit.kaltmiete,
                wohnflaeche = number("wohnflaeche") ?: unit.wohnflaeche,
                mietvertragsstart = values["vertragsbeginn"] ?: unit.mietvertragsstart
            ))
        }
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
                val providerState = _aiProviderState.value
                val result = when (providerState.provider) {
                    ReceiptAnalysisProvider.GEMINI -> {
                        val key = AiProviderSettings.getGeminiKey(getApplication())
                        try {
                            GeminiClient.analyzeReceipt(
                                receiptText = text,
                                bitmap = bitmap,
                                bitmaps = bitmaps,
                                userLearnedRulesContext = learnedContext,
                                apiKeyOverride = key
                            )
                        } finally {
                            key?.fill('\u0000')
                        }
                    }
                    ReceiptAnalysisProvider.OPENAI -> {
                        val key = AiProviderSettings.getOpenAiKey(getApplication())
                            ?: throw OpenAiAnalysisException(
                                "Bitte in den KI-Anbieter-Einstellungen einen OpenAI-API-Schlüssel speichern.",
                                "KEY_MISSING"
                            )
                        try {
                            OpenAiClient.analyzeReceipt(
                                apiKey = key,
                                model = providerState.openAiModel,
                                receiptText = text,
                                bitmap = bitmap,
                                bitmaps = bitmaps,
                                userLearnedRulesContext = learnedContext
                            )
                        } finally {
                            key.fill('\u0000')
                        }
                    }
                }
                if (result != null) {
                    _scanState.value = ScanUiState.Success(result, savedPaths)
                } else {
                    _scanState.value = ScanUiState.Error("Fehler bei der Belegs-Extraktion. Bitte versuche es erneut.")
                }
            } catch (e: com.example.api.GeminiAnalysisException) {
                Log.e("ReceiptViewModel", "Gemini analysis custom exception caught: category=${e.category}, code=${e.errorCode}", e)
                _scanState.value = ScanUiState.Error(e.userMessage)
            } catch (e: OpenAiAnalysisException) {
                Log.e("ReceiptViewModel", "OpenAI receipt analysis failed: code=${e.errorCode}")
                _scanState.value = ScanUiState.Error(e.userMessage)
            } catch (e: Exception) {
                Log.e("ReceiptViewModel", "Unexpected exception during Gemini analysis", e)
                _scanState.value = ScanUiState.Error("Netzwerkfehler oder unerwartetes Problem: ${e.localizedMessage}")
            }
        }
    }

    data class DescriptionBackfillCandidate(
        val receiptId: Int,
        val displayId: String,
        val aussteller: String,
        val oldDescription: String,
        val newDescription: String,
        val isSafe: Boolean,
        val reason: String
    )

    private val _isBackfillingDescriptions = MutableStateFlow(false)
    val isBackfillingDescriptions = _isBackfillingDescriptions.asStateFlow()
    private val _descriptionBackfillStatus = MutableStateFlow<String?>(null)
    val descriptionBackfillStatus = _descriptionBackfillStatus.asStateFlow()
    private val _descriptionBackfillCandidates = MutableStateFlow<List<DescriptionBackfillCandidate>>(emptyList())
    val descriptionBackfillCandidates = _descriptionBackfillCandidates.asStateFlow()

    private fun normalizeDescriptionCompare(value: String): String = value
        .lowercase(Locale.GERMANY)
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun legacyDescriptionIssue(receipt: Receipt): Pair<Boolean, String>? {
        val description = receipt.beschreibung.trim()
        val vendor = receipt.aussteller.trim()
        if (description.isBlank()) return true to "Beschreibung fehlt"

        val descNorm = normalizeDescriptionCompare(description)
        val vendorNorm = normalizeDescriptionCompare(vendor)
        if (vendorNorm.isNotBlank() && descNorm == vendorNorm) {
            return true to "Beschreibung entspricht nur dem Aussteller"
        }

        val addressLike = Regex("\\b\\d{5}\\b").containsMatchIn(description) ||
            Regex("(?i)\\b(straße|strasse|str\\.|weg|allee|platz|gasse|ring|chaussee)\\b").containsMatchIn(description)
        val startsWithVendor = vendorNorm.length >= 4 && descNorm.startsWith(vendorNorm)
        if (startsWithVendor && addressLike) {
            return true to "Aussteller/Adresse statt Leistungszweck"
        }
        return null
    }

    private suspend fun loadDescriptionBackfillBitmaps(receipt: Receipt): List<Bitmap> {
        var paths = receipt.imageUrl.split(',').map { it.trim() }.filter { it.isNotBlank() }
        val hasLocalOriginal = paths.any { path -> runCatching { File(path).exists() && File(path).isFile }.getOrDefault(false) }

        if (!hasLocalOriginal && !receipt.driveFileId.isNullOrBlank()) {
            val email = _googleAccountEmail.value
            if (!email.isNullOrBlank() && _isDriveConnected.value) {
                runCatching {
                    val token = getValidToken(email)
                    val downloadedPath = drivePersistenceRepository.downloadDocumentOnDemand(token, receipt)
                    paths = listOf(downloadedPath)
                }.onFailure {
                    Log.w("ReceiptViewModel", "Original für Beschreibungs-Nacherkennung konnte nicht aus Drive geladen werden", it)
                }
            }
        }

        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            paths.flatMap { path ->
                runCatching {
                    val file = File(path)
                    if (!file.exists() || !file.isFile) return@runCatching emptyList<Bitmap>()
                    if (file.extension.equals("pdf", ignoreCase = true)) {
                        val rendered = mutableListOf<Bitmap>()
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                            PdfRenderer(descriptor).use { renderer ->
                                val pagesToRead = minOf(renderer.pageCount, 3)
                                for (index in 0 until pagesToRead) {
                                    renderer.openPage(index).use { page ->
                                        val scale = minOf(2.0f, 1600.0f / page.width.coerceAtLeast(1))
                                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        rendered.add(bitmap)
                                    }
                                }
                            }
                        }
                        rendered
                    } else {
                        listOfNotNull(BitmapFactory.decodeFile(file.absolutePath))
                    }
                }.getOrElse {
                    Log.w("ReceiptViewModel", "Original für Beschreibungs-Nacherkennung konnte nicht gerendert werden: $path", it)
                    emptyList()
                }
            }
        }
    }

    private suspend fun detectLegacyDescription(receipt: Receipt): String? {
        val bitmaps = loadDescriptionBackfillBitmaps(receipt)
        if (bitmaps.isEmpty()) return null
        return try {
            val learned = getUserLearnedRulesPromptContext()
            val prompt = """
                Analysiere ausschließlich den Zweck dieses Originalbelegs neu.
                Das Feld beschreibung soll eine kurze, sachliche deutsche Beschreibung enthalten, was gekauft, geliefert oder geleistet wurde.
                Keine Firmenanschrift, keine reine Wiederholung des Ausstellers, keine Bankdaten und nichts erfinden.
                Beispiele: "Kauf von Wandfarbe inklusive Versandkosten", "Maklerprovision für Immobilienkauf", "Elektroarbeiten Wohnung OG links".
                Wenn der Zweck nicht zuverlässig erkennbar ist, lasse beschreibung leer.
            """.trimIndent()
            val extracted = when (_aiProviderState.value.provider) {
                ReceiptAnalysisProvider.GEMINI -> {
                    val key = AiProviderSettings.getGeminiKey(getApplication())
                    try { GeminiClient.analyzeReceipt(receiptText = prompt, bitmaps = bitmaps, userLearnedRulesContext = learned, apiKeyOverride = key) }
                    finally { key?.fill('\u0000') }
                }
                ReceiptAnalysisProvider.OPENAI -> {
                    val key = AiProviderSettings.getOpenAiKey(getApplication()) ?: return null
                    try { OpenAiClient.analyzeReceipt(apiKey = key, model = _aiProviderState.value.openAiModel, receiptText = prompt, bitmaps = bitmaps, userLearnedRulesContext = learned) }
                    finally { key.fill('\u0000') }
                }
            }
            val candidate = extracted?.beschreibung.orEmpty().trim()
            val candidateNorm = normalizeDescriptionCompare(candidate)
            val oldNorm = normalizeDescriptionCompare(receipt.beschreibung)
            val vendorNorm = normalizeDescriptionCompare(receipt.aussteller)
            when {
                candidate.length < 6 -> null
                candidateNorm == oldNorm -> null
                vendorNorm.isNotBlank() && candidateNorm == vendorNorm -> null
                Regex("\\b\\d{5}\\b").containsMatchIn(candidate) && candidateNorm.startsWith(vendorNorm) -> null
                else -> candidate.take(240)
            }
        } finally {
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    fun analyzeLegacyDescriptions() {
        if (_isBackfillingDescriptions.value) return
        viewModelScope.launch {
            _isBackfillingDescriptions.value = true
            _descriptionBackfillCandidates.value = emptyList()
            try {
                val candidates = receipts.value.mapNotNull { receipt ->
                    legacyDescriptionIssue(receipt)?.let { issue -> Triple(receipt, issue.first, issue.second) }
                }
                if (candidates.isEmpty()) {
                    _descriptionBackfillStatus.value = "Keine offensichtlich fehlerhaften Altbeschreibungen gefunden."
                    return@launch
                }

                val suggestions = mutableListOf<DescriptionBackfillCandidate>()
                candidates.forEachIndexed { index, (receipt, isSafe, reason) ->
                    _descriptionBackfillStatus.value = "Prüfe Altbeleg ${index + 1} von ${candidates.size} …"
                    val detected = runCatching { detectLegacyDescription(receipt) }.getOrNull()
                    if (!detected.isNullOrBlank()) {
                        suggestions += DescriptionBackfillCandidate(
                            receiptId = receipt.id,
                            displayId = receipt.getEffectiveDisplayId(),
                            aussteller = receipt.aussteller,
                            oldDescription = receipt.beschreibung,
                            newDescription = detected,
                            isSafe = isSafe,
                            reason = reason
                        )
                    }
                }
                _descriptionBackfillCandidates.value = suggestions
                val safeCount = suggestions.count { it.isSafe }
                _descriptionBackfillStatus.value = "${candidates.size} verdächtige Altbelege geprüft: ${suggestions.size} Vorschläge, davon $safeCount sicher vorausgewählt."
            } finally {
                _isBackfillingDescriptions.value = false
            }
        }
    }

    fun applyDescriptionBackfill(receiptIds: Set<Int>) {
        if (receiptIds.isEmpty()) return
        viewModelScope.launch {
            val selected = _descriptionBackfillCandidates.value.filter { it.receiptId in receiptIds }
            var updatedCount = 0
            var skippedCount = 0
            selected.forEachIndexed { index, candidate ->
                _descriptionBackfillStatus.value = "Übernehme Beschreibung ${index + 1} von ${selected.size} …"
                val persisted = repository.getReceiptById(candidate.receiptId)
                if (persisted == null || persisted.beschreibung != candidate.oldDescription) {
                    skippedCount++
                    return@forEachIndexed
                }

                val proposed = persisted.copy(beschreibung = candidate.newDescription)
                val receiptToSave = com.example.data.DatevApprovalInvalidationPolicy.apply(persisted, proposed)
                repository.insert(receiptToSave)
                if (FirestoreService.isCloudActive()) {
                    FirestoreService.saveReceipt(receiptToSave)
                }
                if (_isDriveConnected.value && _autoDriveBackup.value) {
                    runCatching { uploadReceiptToDriveInternal(receiptToSave) }
                        .onFailure { Log.w("ReceiptViewModel", "Korrigierte Beschreibung konnte nicht sofort nach Drive synchronisiert werden", it) }
                }
                updatedCount++
            }
            _descriptionBackfillCandidates.value = _descriptionBackfillCandidates.value.filterNot { it.receiptId in receiptIds }
            _descriptionBackfillStatus.value = "Fertig: $updatedCount Beschreibungen aktualisiert" + if (skippedCount > 0) ", $skippedCount wegen zwischenzeitlicher Änderungen übersprungen." else "."
        }
    }

    fun clearDescriptionBackfillPreview() {
        _descriptionBackfillCandidates.value = emptyList()
    }

    private val _isBackfillingPaymentMethods = MutableStateFlow(false)
    val isBackfillingPaymentMethods = _isBackfillingPaymentMethods.asStateFlow()
    private val _paymentBackfillStatus = MutableStateFlow<String?>(null)
    val paymentBackfillStatus = _paymentBackfillStatus.asStateFlow()

    private fun normalizePaymentMethod(raw: String?): String {
        val value = raw.orEmpty().trim().lowercase(Locale.GERMANY)
        return when {
            value.isBlank() || value == "unbekannt" -> "Unbekannt"
            value.contains("paypal") -> "PayPal"
            value.contains("lastschrift") -> "Lastschrift"
            value.contains("überweisung") || value.contains("ueberweisung") -> "Überweisung"
            value.contains("mastercard") || value.contains("visa") || value.contains("kreditkarte") -> "Kreditkarte"
            value.contains("girocard") || value.contains("maestro") || Regex("(^|[^a-z])ec([^a-z]|$)").containsMatchIn(value) -> "Girocard/EC"
            value.contains("bargeld") || Regex("(^|[^a-z])bar([^a-z]|$)").containsMatchIn(value) -> "Bar"
            else -> "Unbekannt"
        }
    }

    private suspend fun detectExistingPaymentMethod(receipt: Receipt): Pair<String, String>? {
        val text = listOf(receipt.aussteller, receipt.beschreibung, receipt.positionenJson).joinToString(" ")
        val fromText = normalizePaymentMethod(text)
        if (fromText != "Unbekannt") return fromText to "TEXT_HEURISTIK"

        val paths = receipt.imageUrl.split(',').map { it.trim() }.filter { it.isNotBlank() }
        val bitmaps = kotlinx.coroutines.withContext(Dispatchers.IO) {
            paths.flatMap { path ->
                runCatching {
                    val file = File(path)
                    if (!file.exists() || !file.isFile) return@runCatching emptyList<Bitmap>()
                    if (file.extension.equals("pdf", ignoreCase = true)) {
                        val rendered = mutableListOf<Bitmap>()
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                            PdfRenderer(descriptor).use { renderer ->
                                val pagesToRead = minOf(renderer.pageCount, 3)
                                for (index in 0 until pagesToRead) {
                                    renderer.openPage(index).use { page ->
                                        val scale = minOf(2.0f, 1600.0f / page.width.coerceAtLeast(1))
                                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        rendered.add(bitmap)
                                    }
                                }
                            }
                        }
                        rendered
                    } else {
                        listOfNotNull(BitmapFactory.decodeFile(file.absolutePath))
                    }
                }.getOrElse {
                    Log.w("ReceiptViewModel", "Original für Zahlungsart konnte nicht gerendert werden: $path", it)
                    emptyList()
                }
            }
        }
        if (bitmaps.isEmpty()) return null
        return try {
            val learned = getUserLearnedRulesPromptContext()
            val extracted = when (_aiProviderState.value.provider) {
                ReceiptAnalysisProvider.GEMINI -> {
                    val key = AiProviderSettings.getGeminiKey(getApplication())
                    try { GeminiClient.analyzeReceipt(receiptText = "Bestimme insbesondere die Zahlungsart. Bei Unsicherheit Unbekannt.", bitmaps = bitmaps, userLearnedRulesContext = learned, apiKeyOverride = key) }
                    finally { key?.fill('\u0000') }
                }
                ReceiptAnalysisProvider.OPENAI -> {
                    val key = AiProviderSettings.getOpenAiKey(getApplication()) ?: return null
                    try { OpenAiClient.analyzeReceipt(apiKey = key, model = _aiProviderState.value.openAiModel, receiptText = "Bestimme insbesondere die Zahlungsart. Bei Unsicherheit Unbekannt.", bitmaps = bitmaps, userLearnedRulesContext = learned) }
                    finally { key.fill('\u0000') }
                }
            }
            val method = normalizePaymentMethod(extracted?.zahlungsart)
            if (method == "Unbekannt") null else method to "KI_NACHERKANNT"
        } finally {
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    fun backfillPaymentMethods() {
        if (_isBackfillingPaymentMethods.value) return
        viewModelScope.launch {
            _isBackfillingPaymentMethods.value = true
            try {
                val candidates = receipts.value.filter { normalizePaymentMethod(it.zahlungsart) == "Unbekannt" }
                var updated = 0
                candidates.forEachIndexed { index, receipt ->
                    _paymentBackfillStatus.value = "Prüfe Beleg ${index + 1} von ${candidates.size} …"
                    val detected = runCatching { detectExistingPaymentMethod(receipt) }.getOrNull()
                    if (detected != null) {
                        repository.insert(receipt.copy(
                            zahlungsart = detected.first,
                            zahlungsartQuelle = detected.second,
                            zahlungsartConfidence = if (detected.second == "KI_NACHERKANNT") 0.92 else 0.78
                        ))
                        updated++
                    }
                }
                _paymentBackfillStatus.value = "Fertig: $updated von ${candidates.size} Belegen ergänzt."
            } finally {
                _isBackfillingPaymentMethods.value = false
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
        zahlungsart: String = "Unbekannt",
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
                propertyId = propertyMetadata.value?.propertyId
                    ?: com.example.data.StableDocumentIdentity.LEGACY_PROPERTY_ID,
                mieter = mieter,
                zahlungsart = normalizePaymentMethod(zahlungsart),
                zahlungsartQuelle = if (normalizePaymentMethod(zahlungsart) == "Unbekannt") "UNBEKANNT" else "NUTZER_BESTAETIGT",
                zahlungsartConfidence = if (normalizePaymentMethod(zahlungsart) == "Unbekannt") 0.0 else 1.0,
                positionenJson = positionenJson
            )
            val newId = repository.insert(newReceipt)
            val savedReceipt = newReceipt.copy(id = newId.toInt())

            _pendingBankTransactionId.value?.let { pendingTransactionId ->
                database.bankDao().getTransaction(pendingTransactionId)?.let { transaction ->
                    _bankImportStatus.value = confirmBankReceiptLinkInternal(transaction, savedReceipt)
                }
                _pendingBankTransactionId.value = null
            }

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
            val persistedReceipt = repository.getReceiptById(receipt.id)
            val receiptToSave = com.example.data.DatevApprovalInvalidationPolicy.apply(
                persistedReceipt,
                receipt
            )

            // Learn rule automatically on user corrections
            learnVendorRule(
                receiptToSave.aussteller,
                receiptToSave.hauptkategorie,
                receiptToSave.unterkategorie,
                receiptToSave.kontoNr,
                receiptToSave.wohneinheit
            )

            repository.insert(receiptToSave)

            if (FirestoreService.isCloudActive()) {
                FirestoreService.saveReceipt(receiptToSave)
            }
            
            // Auto drive backup if enabled
            if (_isDriveConnected.value && _autoDriveBackup.value && !receiptToSave.isArchivedToDrive) {
                uploadReceiptToDriveInternal(receiptToSave)
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
            _wohneinheitenStatus.value = getWohneinheitenForProperty(metadata)

            // Keep the cloud backup current, but never use it to overwrite existing local metadata.
            if (_isDriveConnected.value && _autoDriveBackup.value) {
                syncAllToDrive()
            }
        }
    }

    fun deleteReceipt(id: Int, deletedBy: String = "LocalUser", reason: String = "Vom Nutzer gelöscht") {
        viewModelScope.launch(Dispatchers.IO) {
            val receipt = repository.getReceiptById(id) ?: return@launch
            val deleteDecision = com.example.data.BankReceiptDeletionPolicy.decide(
                receiptId = receipt.id,
                links = bankReceiptLinks.value
            )
            if (!deleteDecision.allowed) {
                Log.w("ReceiptViewModel", deleteDecision.reason ?: "Beleg ist noch mit Bankbuchungen verknüpft.")
                return@launch
            }
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
            val deleteDecision = com.example.data.BankReceiptDeletionPolicy.decide(
                receiptId = receipt.id,
                links = bankReceiptLinks.value
            )
            if (!deleteDecision.allowed) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete(com.example.data.PermanentDeleteResult.Error(
                        deleteDecision.reason ?: "Beleg ist noch mit Bankbuchungen verknüpft."
                    ))
                }
                return@launch
            }
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

    /**
     * Starts the receipt workflow over on this device without touching account,
     * property, cloud connection, or AI provider settings.
     */
    fun resetLocalReceiptData() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllData()
            database.bankDao().clearLinks()
            database.bankDao().reopenLinkedTransactions(java.time.Instant.now().toString())

            val editor = learnedRulesPrefs.edit()
            learnedRulesPrefs.all.keys
                .filter { it.startsWith("rule_") }
                .forEach(editor::remove)
            editor.apply()
            _learnedRules.value = emptyList()
            _bankStatementResult.value = null
            _isMatchingBankStatement.value = false
            _bankStatementResetVersion.value += 1
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


    suspend fun calculateLogbookRoadDistance(
        stops: List<com.example.data.TripStop>,
        routeMode: com.example.data.TripRouteMode,
        sameReturnRoute: Boolean
    ): com.example.data.RouteDistanceAttempt {
        return try {
            val result = routeDistanceService.calculateRoadDistance(
                com.example.data.RouteDistanceRequest(stops, routeMode, sameReturnRoute)
            )
            if (result == null) {
                com.example.data.RouteDistanceAttempt(errorMessage = "Google-Routenberechnung derzeit nicht verfügbar.")
            } else {
                com.example.data.RouteDistanceAttempt(result = result)
            }
        } catch (e: com.example.data.RouteDistanceException) {
            com.example.data.RouteDistanceAttempt(errorMessage = e.userMessage)
        } catch (_: Exception) {
            com.example.data.RouteDistanceAttempt(errorMessage = "Google-Routenberechnung derzeit nicht verfügbar.")
        }
    }

    suspend fun findStandardRoute(routeSignature: String): com.example.data.StandardRoute? {
        database.logbookDao().findStandardRoute(routeSignature)?.let { return it }
        // Lazily index pre-v19 standard routes without changing any historic trip distance.
        val legacy = database.logbookDao().getAllStandardRoutes().firstOrNull { route ->
            if (!route.active || route.routeSignature.isNotBlank()) return@firstOrNull false
            val mode = runCatching { com.example.data.TripRouteMode.valueOf(route.routeMode) }
                .getOrDefault(com.example.data.TripRouteMode.EINFACH)
            runCatching {
                val routeStops = route.stops.takeIf { it.size >= 2 } ?: listOf(
                    com.example.data.TripStop(route.startAddress, "Start", 0),
                    com.example.data.TripStop(route.destinationAddress, "Ziel", 1)
                )
                com.example.data.TripRouteNormalizer.normalize(
                    com.example.data.RouteDistanceRequest(routeStops, mode, route.sameReturnRoute)
                ).signature == routeSignature
            }.getOrDefault(false)
        } ?: return null
        database.logbookDao().upsertStandardRoute(legacy.copy(routeSignature = routeSignature))
        return legacy.copy(routeSignature = routeSignature)
    }

    suspend fun saveStandardRoute(route: com.example.data.StandardRoute): Long {
        val existing = database.logbookDao().findStandardRoute(route.routeSignature)
        return database.logbookDao().upsertStandardRoute(
            if (existing == null) route else route.copy(id = existing.id, createdAt = existing.createdAt)
        )
    }

    fun deleteStandardRoute(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { database.logbookDao().deleteStandardRoute(id) }
    }

    suspend fun saveLogbookTrip(
        originalReceipt: Receipt,
        purpose: String,
        startAddress: String,
        destinationAddress: String,
        stops: List<com.example.data.TripStop>,
        routeMode: com.example.data.TripRouteMode,
        sameReturnRoute: Boolean,
        evidence: com.example.data.DistanceEvidence,
        routeResult: com.example.data.RouteDistanceResult? = null,
        standardRouteId: Long? = null,
        correctionReason: String = "",
        correctionNote: String = "",
        note: String = ""
    ): Result<Long> = runCatching {
        require(evidence.manuallyConfirmed) { "Route, Fahrtzweck und Kilometer müssen vor dem Einbuchen bestätigt werden." }
        val normalized = com.example.data.TripRouteNormalizer.normalize(
            com.example.data.RouteDistanceRequest(stops, routeMode, sameReturnRoute)
        )
        val checkedEvidence = evidence.copy(correctionReason = correctionReason)
        val decision = com.example.data.LogbookDistancePolicy.decide(checkedEvidence)
        val distance = requireNotNull(decision.taxDistanceKm) {
            "Die steuerliche Kilometerzahl muss durch Route, GPS, Tacho, Standardstrecke oder manuell bestätigt werden."
        }
        val source = requireNotNull(decision.source)
        require(source != com.example.data.KilometerSource.KI_GESCHAETZT) {
            "Eine reine KI-Schätzung darf nicht steuerlich eingebucht werden."
        }
        require(!decision.correctionReasonRequired || correctionReason.isNotBlank()) {
            "Für die deutlich abweichende manuelle Strecke ist ein Korrekturgrund erforderlich."
        }
        require(correctionReason != "Sonstiges" || correctionNote.isNotBlank()) {
            "Für den Korrekturgrund Sonstiges ist eine kurze Beschreibung erforderlich."
        }
        val now = java.time.Instant.now().toString()
        val expenseId = repository.insert(
            Receipt(
                aussteller = "Fahrtkosten: ${originalReceipt.aussteller}",
                datum = originalReceipt.datum,
                uhrzeit = originalReceipt.uhrzeit,
                bruttobetrag = distance * 0.30,
                hauptkategorie = "Sonstige Ausgaben",
                unterkategorie = "Fahrtkosten",
                kontoNr = "4670",
                beschreibung = "Fahrtenbuch: ${normalized.stops.joinToString(" -> ") { it.label.ifBlank { it.address } }} | Zweck: $purpose | ${String.format(Locale.GERMANY, "%.1f", distance)} km | Quelle: ${source.name}",
                isEigenleistungSanierung = originalReceipt.isEigenleistungSanierung,
                wohneinheit = originalReceipt.wohneinheit
            )
        )
        val tripId = database.logbookDao().upsertTrip(
            com.example.data.LogbookTrip(
                date = originalReceipt.datum,
                time = originalReceipt.uhrzeit,
                purpose = purpose,
                propertyReference = originalReceipt.wohneinheit,
                startAddress = startAddress,
                destinationAddress = destinationAddress,
                stopsJson = com.example.data.TripStopJson.encode(normalized.stops),
                routeMode = routeMode.name,
                sameReturnRoute = sameReturnRoute,
                taxDistanceKm = distance,
                kilometerSource = source.name,
                aiEstimatedKm = evidence.aiEstimatedKm,
                routedKm = evidence.routedKm,
                manualKm = evidence.manualKm,
                gpsMeasuredKm = evidence.gpsMeasuredKm,
                odometerStartKm = evidence.odometerStartKm,
                odometerEndKm = evidence.odometerEndKm,
                standardRouteId = standardRouteId,
                plausibilityStatus = decision.plausibilityStatus.name,
                manuallyConfirmed = evidence.manuallyConfirmed,
                sourceReceiptId = originalReceipt.id,
                expenseReceiptId = expenseId.toInt(),
                routeProvider = routeResult?.providerId.orEmpty(),
                routeCalculatedAt = routeResult?.calculatedAt.orEmpty(),
                routeDurationSeconds = routeResult?.durationSeconds,
                correctionReason = correctionReason,
                correctionNote = correctionNote,
                routeSignature = normalized.signature,
                note = note,
                createdAt = now,
                updatedAt = now
            )
        )
        repository.insert(
            originalReceipt.copy(
                beschreibung = originalReceipt.beschreibung.replace(Regex("""\s*\[Fahrt gebucht:[^\]]*]"""), "") +
                    " [Fahrt gebucht: ${String.format(Locale.GERMANY, "%.1f", distance)} km, ${source.name}]"
            )
        )
        tripId
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
