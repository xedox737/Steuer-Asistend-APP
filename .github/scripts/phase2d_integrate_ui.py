from pathlib import Path

BRANCH = "agent/bank-phase2d-combinations-review-queue"

vm = Path("app/src/main/java/com/example/ui/ReceiptViewModel.kt")
text = vm.read_text(encoding="utf-8")

state = r'''

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
'''

if "val bankPhase2DAnalysis:" not in text:
    marker = "\n    init {\n"
    if marker not in text:
        raise RuntimeError("ReceiptViewModel init marker not found")
    text = text.replace(marker, state + marker, 1)

methods = r'''

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
'''

if "fun confirmPhase2DCombination(" not in text:
    marker = "\n    fun confirmBankRentSuggestion(\n"
    if marker not in text:
        raise RuntimeError("ReceiptViewModel action marker not found")
    text = text.replace(marker, methods + marker, 1)
vm.write_text(text, encoding="utf-8")

bank = Path("app/src/main/java/com/example/ui/BankFeature.kt")
text = bank.read_text(encoding="utf-8")
state_marker = "    var showPhase2C by remember { mutableStateOf(false) }\n"
if "var showPhase2D" not in text:
    if state_marker not in text:
        raise RuntimeError("BankFeature state marker not found")
    text = text.replace(state_marker, state_marker + "    var showPhase2D by remember { mutableStateOf(false) }\n", 1)

button_marker = '            OutlinedButton(onClick={ showPhase2C = !showPhase2C }, modifier=Modifier.fillMaxWidth()) { Text(if(showPhase2C) "Darlehen & Wiederkehrend ausblenden" else "Darlehen & Wiederkehrend") }\n'
if "Prüfwarteschlange & Sammelzahlungen" not in text:
    if button_marker not in text:
        raise RuntimeError("BankFeature Phase2C button marker not found")
    text = text.replace(button_marker, button_marker + '            OutlinedButton(onClick={ showPhase2D = !showPhase2D }, modifier=Modifier.fillMaxWidth()) { Text(if(showPhase2D) "Prüfwarteschlange ausblenden" else "Prüfwarteschlange & Sammelzahlungen") }\n', 1)

panel_marker = "        if (showPhase2C) { item { BankPhase2CPanel(viewModel) } }\n"
if "BankPhase2DReviewPanel(viewModel)" not in text:
    if panel_marker not in text:
        raise RuntimeError("BankFeature panel marker not found")
    text = text.replace(panel_marker, panel_marker + "        if (showPhase2D) { item { BankPhase2DReviewPanel(viewModel) } }\n", 1)
bank.write_text(text, encoding="utf-8")
