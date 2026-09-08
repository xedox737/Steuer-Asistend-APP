from pathlib import Path

root = Path('.')

def patch(path, old, new):
    p = root / path
    text = p.read_text()
    if old not in text:
        raise SystemExit(f'pattern not found in {path}: {old[:160]}')
    p.write_text(text.replace(old, new, 1))

# Enrich matcher with confirmed-receipt and known payment-rhythm factors.
matcher='app/src/main/java/com/example/ui/BankRentMatching.kt'
patch(matcher,
'''    val alreadyConfirmedAmount: Double,
    val accountId: String = ""
) {''',
'''    val alreadyConfirmedAmount: Double,
    val accountId: String = "",
    val knownDayOfMonth: Int? = null,
    val existingRentalReceiptLink: Boolean = false
) {''')
patch(matcher,
'''        if (c.accountId.isNotBlank() && tx.accountId == c.accountId) {
            score += 5; reasons += "Konto passt"
        }

        val monthMatches''',
'''        if (c.accountId.isNotBlank() && tx.accountId == c.accountId) {
            score += 5; reasons += "Konto passt"
        }
        if (c.existingRentalReceiptLink) {
            score += 7; reasons += "Bestehender bestätigter Mietbeleg-Link passt"
        }
        BankRentCandidateFactory.rhythmDistanceDays(tx, c)?.let { distance ->
            when {
                distance <= 2 -> { score += 6; reasons += "Bekannter Zahlungsrhythmus passt" }
                distance <= 5 -> { score += 3; reasons += "Zahlung liegt nahe am bekannten Rhythmus" }
                else -> reasons += "Zahlung weicht vom bekannten Rhythmus ab"
            }
        }

        val monthMatches''')

vm='app/src/main/java/com/example/ui/ReceiptViewModel.kt'
patch(vm,
'''    val bankLearningRules: StateFlow<List<com.example.data.BankLearningRule>> =
        database.bankLearningRuleDao().observeRules().stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val bankRuleEvaluations:''',
'''    val bankLearningRules: StateFlow<List<com.example.data.BankLearningRule>> =
        database.bankLearningRuleDao().observeRules().stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val bankRentAssignments: StateFlow<List<com.example.data.BankRentAssignment>> =
        database.bankRentAssignmentDao().observeAll().stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )
    private val _dismissedBankRentTransactions = MutableStateFlow<Set<String>>(emptySet())

    val bankRuleEvaluations:''')
patch(vm,
'''    val propertyMetadata: StateFlow<PropertyMetadata?> = combine(properties, _selectedPropertyId) { all, selectedId ->
        all.firstOrNull { it.propertyId == selectedId } ?: all.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val propertyReceipts:''',
'''    val propertyMetadata: StateFlow<PropertyMetadata?> = combine(properties, _selectedPropertyId) { all, selectedId ->
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

    val propertyReceipts:''')
patch(vm,
'''    fun saveBankRule(rule: com.example.data.BankLearningRule) { viewModelScope.launch(Dispatchers.IO) { database.bankLearningRuleDao().upsertRule(rule) } }
''',
'''    fun confirmBankRentSuggestion(
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
''')

bank='app/src/main/java/com/example/ui/BankFeature.kt'
patch(bank,
'''    val ruleEvaluations by viewModel.bankRuleEvaluations.collectAsState()

    var filter''',
'''    val ruleEvaluations by viewModel.bankRuleEvaluations.collectAsState()
    val rentSuggestions by viewModel.bankRentSuggestions.collectAsState()

    var filter''')
patch(bank,
'''    var showBankRules by remember { mutableStateOf(false) }
''',
'''    var showBankRules by remember { mutableStateOf(false) }
    var showRentMatching by remember { mutableStateOf(false) }
''')
patch(bank,
'''    val rentHints = remember(filteredTransactions, receipts, units, propertyId) {
        filteredTransactions.filter { it.amount > 0 && it.reconciliationStatus in setOf(BankReconciliationStatus.OPEN, BankReconciliationStatus.REVIEW) }
            .mapNotNull { tx -> bestRentHint(context, propertyId, tx, units, receipts)?.let { tx.transactionId to it } }
            .toMap()
    }
''',
'''    val rentHints = remember(rentSuggestions, units, propertyId) {
        rentSuggestions.mapNotNull { (transactionId, candidates) ->
            val top = candidates.firstOrNull { it.propertyId == propertyId } ?: candidates.firstOrNull() ?: return@mapNotNull null
            val unit = units.firstOrNull { PropertyUnitScopedData.stableUnitId(top.propertyId, it) == top.unitId }
                ?: return@mapNotNull null
            transactionId to BankRentHint(unit, top.tenantName, top.expectedAmount, top.score)
        }.toMap()
    }
''')
patch(bank,
'''            OutlinedButton(onClick={ showBankRules = !showBankRules }, modifier=Modifier.fillMaxWidth()) { Text(if(showBankRules) "Bankregeln ausblenden" else "Bankregeln") }
        }
        if (showBankRules) { item { BankRulesPanel(viewModel, learningRules) } }
''',
'''            OutlinedButton(onClick={ showBankRules = !showBankRules }, modifier=Modifier.fillMaxWidth()) { Text(if(showBankRules) "Bankregeln ausblenden" else "Bankregeln") }
            OutlinedButton(onClick={ showRentMatching = !showRentMatching }, modifier=Modifier.fillMaxWidth()) { Text(if(showRentMatching) "Mietabgleich ausblenden" else "Mietabgleich") }
        }
        if (showBankRules) { item { BankRulesPanel(viewModel, learningRules) } }
        if (showRentMatching) { item { BankRentPanel(viewModel) } }
''')
