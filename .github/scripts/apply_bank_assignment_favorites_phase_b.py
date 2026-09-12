from pathlib import Path

vm_path = Path('app/src/main/java/com/example/ui/ReceiptViewModel.kt')
ui_path = Path('app/src/main/java/com/example/ui/BankFeature.kt')
vm = vm_path.read_text()
ui = ui_path.read_text()

state_anchor = '''    private val _bankImportStatus = MutableStateFlow<String?>(null)\n    val bankImportStatus: StateFlow<String?> = _bankImportStatus.asStateFlow()\n'''
state_insert = state_anchor + '''    private val bankComfortPrefs = application.getSharedPreferences("bank_comfort_prefs", Context.MODE_PRIVATE)\n    private val _bankFavoriteKeys = MutableStateFlow(\n        bankComfortPrefs.getStringSet("assignment_favorites", emptySet()).orEmpty().toSet()\n    )\n    val bankFavoriteKeys: StateFlow<Set<String>> = _bankFavoriteKeys.asStateFlow()\n'''
if state_anchor not in vm:
    raise SystemExit('bank status anchor missing')
vm = vm.replace(state_anchor, state_insert, 1)

method_anchor = '''    fun startReceiptLikeLastMonth(\n'''
method_insert = '''    fun toggleBankAssignmentFavorite(receiptId: Int) {\n        viewModelScope.launch(Dispatchers.IO) {\n            val receipt = repository.getReceiptById(receiptId) ?: return@launch\n            val key = com.example.data.BankAssignmentFavoritesPolicy.key(receipt)\n            val current = _bankFavoriteKeys.value\n            val updated = if (key in current) current - key else current + key\n            bankComfortPrefs.edit().putStringSet("assignment_favorites", updated).apply()\n            _bankFavoriteKeys.value = updated\n            _bankImportStatus.value = if (key in updated)\n                "Zuordnung als Favorit gespeichert. Favoriten ändern keine Steuer- oder DATEV-Logik."\n            else "Zuordnung aus Favoriten entfernt."\n        }\n    }\n\n    fun startReceiptFromBankFavorite(\n        transaction: com.example.data.BankTransaction,\n        favorite: com.example.data.BankAssignmentFavorite\n    ) {\n        if (com.example.data.BankTransactionClassification.normalize(transaction.classification) !=\n            com.example.data.BankTransactionClassification.NORMAL\n        ) {\n            _bankImportStatus.value = "Schnellzuordnung ist für Privat-/Umbuchungsbuchungen nicht verfügbar."\n            return\n        }\n        favorite.propertyId.takeIf {\n            it.isNotBlank() && it != com.example.data.StableDocumentIdentity.LEGACY_PROPERTY_ID\n        }?.let(::selectProperty)\n        _pendingBankTransactionId.value = transaction.transactionId\n        _scanState.value = ScanUiState.Success(\n            com.example.api.ExtractedReceipt(\n                aussteller = favorite.vendor.ifBlank { transaction.counterparty },\n                datum = transaction.bookingDate,\n                uhrzeit = "",\n                bruttobetrag = transaction.absoluteAmount,\n                hauptkategorie = favorite.category,\n                unterkategorie = favorite.subcategory,\n                kontoNr = "",\n                beschreibung = transaction.purpose,\n                isEigenleistungSanierung = false,\n                wohneinheit = favorite.unitName,\n                mieter = "",\n                zahlungsart = favorite.paymentMethod.ifBlank { "Überweisung" },\n                positionen = emptyList()\n            )\n        )\n        _bankImportStatus.value = "Schnellzuordnung vorbefüllt. Erst der Nutzer bestätigt den neuen Beleg; keine automatische Buchung."\n        _currentScreen.value = AppScreen.ADD_RECEIPT\n    }\n\n''' + method_anchor
if method_anchor not in vm:
    raise SystemExit('favorite method anchor missing')
vm = vm.replace(method_anchor, method_insert, 1)

collect_anchor = '''    val importStatus by viewModel.bankImportStatus.collectAsState()\n    val undoState by viewModel.bankUndoState.collectAsState()\n    val learningRules by viewModel.bankLearningRules.collectAsState()\n'''
collect_insert = '''    val importStatus by viewModel.bankImportStatus.collectAsState()\n    val undoState by viewModel.bankUndoState.collectAsState()\n    val favoriteKeys by viewModel.bankFavoriteKeys.collectAsState()\n    val learningRules by viewModel.bankLearningRules.collectAsState()\n'''
if collect_anchor not in ui:
    raise SystemExit('favorite collect anchor missing')
ui = ui.replace(collect_anchor, collect_insert, 1)

favorites_anchor = '''    val lastMonthSuggestion = remember(selectedTransaction, transactions, links, receipts) {\n'''
favorites_insert = '''    val assignmentFavorites = remember(transactions, receipts, links, favoriteKeys) {\n        com.example.data.BankAssignmentFavoritesPolicy.derive(\n            transactions = transactions,\n            receipts = receipts,\n            links = links,\n            manualFavoriteKeys = favoriteKeys\n        ).take(8)\n    }\n    val lastMonthSuggestion = remember(selectedTransaction, transactions, links, receipts) {\n'''
if favorites_anchor not in ui:
    raise SystemExit('favorites derive anchor missing')
ui = ui.replace(favorites_anchor, favorites_insert, 1)

call_anchor = '''            linkedLinks = linksByTransaction[selectedTransaction.transactionId].orEmpty(),\n            assignments = assignmentsByTransaction[selectedTransaction.transactionId].orEmpty(),\n            receiptById = receiptById,\n'''
call_insert = '''            linkedLinks = linksByTransaction[selectedTransaction.transactionId].orEmpty(),\n            assignments = assignmentsByTransaction[selectedTransaction.transactionId].orEmpty(),\n            receiptById = receiptById,\n            assignmentFavorites = assignmentFavorites,\n            favoriteKeys = favoriteKeys,\n'''
if call_anchor not in ui:
    raise SystemExit('detail favorites call anchor missing')
ui = ui.replace(call_anchor, call_insert, 1)

sig_anchor = '''    assignments: List<com.example.data.BankRentAssignment>,\n    receiptById: Map<Int, Receipt>,\n    rentSuggestion: com.example.data.BankRentSuggestion?,\n'''
sig_insert = '''    assignments: List<com.example.data.BankRentAssignment>,\n    receiptById: Map<Int, Receipt>,\n    assignmentFavorites: List<com.example.data.BankAssignmentFavorite>,\n    favoriteKeys: Set<String>,\n    rentSuggestion: com.example.data.BankRentSuggestion?,\n'''
if sig_anchor not in ui:
    raise SystemExit('detail favorites signature anchor missing')
ui = ui.replace(sig_anchor, sig_insert, 1)

last_month_anchor = '''        if (lastMonthSuggestion != null) {\n'''
favorites_card = '''        if (transaction.classification == BankTransactionClassification.NORMAL && assignmentFavorites.isNotEmpty()) {\n            item {\n                Card(\n                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),\n                    border = BorderStroke(1.dp, BorderColor)\n                ) {\n                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {\n                        Text("Schnellzuordnungen", fontWeight = FontWeight.Bold, color = DarkNavy)\n                        Text("Favoriten, häufige und zuletzt verwendete Zuordnungen. Es wird nur ein neuer Belegentwurf vorbefüllt.", fontSize = 10.sp, color = SlateGray)\n                        assignmentFavorites.take(4).forEach { favorite ->\n                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {\n                                Column(Modifier.weight(1f)) {\n                                    Text(\n                                        (if (favorite.manualFavorite) "★ " else "") + favorite.label,\n                                        fontWeight = FontWeight.SemiBold,\n                                        fontSize = 12.sp,\n                                        color = DarkNavy,\n                                        maxLines = 1,\n                                        overflow = TextOverflow.Ellipsis\n                                    )\n                                    Text(\n                                        "${favorite.vendor.ifBlank { "Zuordnung" }} • ${favorite.useCount}× verwendet${favorite.lastUsedDate.takeIf { it.isNotBlank() }?.let { " • zuletzt ${formatDate(it)}" }.orEmpty()}",\n                                        fontSize = 10.sp,\n                                        color = SlateGray,\n                                        maxLines = 1,\n                                        overflow = TextOverflow.Ellipsis\n                                    )\n                                }\n                                TextButton(onClick = { viewModel.startReceiptFromBankFavorite(transaction, favorite) }) { Text("Übernehmen") }\n                            }\n                        }\n                    }\n                }\n            }\n        }\n\n''' + last_month_anchor
if last_month_anchor not in ui:
    raise SystemExit('favorites card anchor missing')
ui = ui.replace(last_month_anchor, favorites_card, 1)

linked_anchor = '''                                TextButton(onClick = { onReceiptDetails(receipt) }) { Text("Öffnen") }\n                                TextButton(onClick = { viewModel.proposeBankRuleFromConfirmedReceipt(transaction.transactionId, receipt.id) }) { Text("Regel merken") }\n                                TextButton(onClick = { viewModel.removeBankReceiptLink(link.linkId, transaction.transactionId) }) { Text("Lösen") }\n'''
linked_insert = '''                                TextButton(onClick = { onReceiptDetails(receipt) }) { Text("Öffnen") }\n                                val favoriteKey = com.example.data.BankAssignmentFavoritesPolicy.key(receipt)\n                                TextButton(onClick = { viewModel.toggleBankAssignmentFavorite(receipt.id) }) {\n                                    Text(if (favoriteKey in favoriteKeys) "★" else "☆")\n                                }\n                                TextButton(onClick = { viewModel.proposeBankRuleFromConfirmedReceipt(transaction.transactionId, receipt.id) }) { Text("Regel merken") }\n                                TextButton(onClick = { viewModel.removeBankReceiptLink(link.linkId, transaction.transactionId) }) { Text("Lösen") }\n'''
if linked_anchor not in ui:
    raise SystemExit('linked favorite button anchor missing')
ui = ui.replace(linked_anchor, linked_insert, 1)

vm_path.write_text(vm)
ui_path.write_text(ui)
