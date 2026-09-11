from pathlib import Path

vm_path = Path('app/src/main/java/com/example/ui/ReceiptViewModel.kt')
ui_path = Path('app/src/main/java/com/example/ui/BankFeature.kt')
vm = vm_path.read_text()
ui = ui_path.read_text()

vm_marker = '''    fun markBankTransactionPrivateIgnored(transactionId: String) {
'''
vm_insert = '''    fun confirmBankTransferPair(transactionId: String, counterpartTransactionId: String) {
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

'''
if 'fun confirmBankTransferPair(' not in vm:
    assert vm_marker in vm
    vm = vm.replace(vm_marker, vm_insert + vm_marker, 1)

cleanup_marker = '''            val now = java.time.Instant.now().toString()
            val updated = com.example.data.BankClassificationPolicy.classify(
'''
cleanup_insert = '''            val now = java.time.Instant.now().toString()
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
'''
if 'counterpart?.linkedTransferTransactionId == transaction.transactionId' not in vm:
    assert cleanup_marker in vm
    vm = vm.replace(cleanup_marker, cleanup_insert, 1)

selected_marker = '''    val selectedTransaction = selectedTransactionId?.let { id -> transactions.firstOrNull { it.transactionId == id } }
'''
selected_insert = '''    val selectedTransferSuggestion = selectedTransaction?.takeIf {
        it.classification == BankTransactionClassification.TRANSFER && it.linkedTransferTransactionId.isBlank()
    }?.let { com.example.data.BankTransferMatcher.suggestions(it, transactions).firstOrNull() }
    val selectedSuggestedTransferCounterpart = selectedTransferSuggestion?.let { suggestion ->
        transactions.firstOrNull { it.transactionId == suggestion.counterTransactionId }
    }
    val selectedTransferCounterpart = selectedTransaction?.linkedTransferTransactionId
        ?.takeIf { it.isNotBlank() }
        ?.let { id -> transactions.firstOrNull { it.transactionId == id } }
'''
if 'val selectedTransferSuggestion' not in ui:
    assert selected_marker in ui
    ui = ui.replace(selected_marker, selected_marker + selected_insert, 1)

pass_marker = '''            lastMonthSuggestion = lastMonthSuggestion,
'''
pass_insert = '''            transferSuggestion = selectedTransferSuggestion,
            suggestedTransferCounterpart = selectedSuggestedTransferCounterpart,
            transferCounterpart = selectedTransferCounterpart,
'''
if 'transferSuggestion = selectedTransferSuggestion' not in ui:
    assert pass_marker in ui
    ui = ui.replace(pass_marker, pass_marker + pass_insert, 1)

sig_marker = '''    lastMonthSuggestion: com.example.data.BankLastMonthAssignmentSuggestion?,
    linkedLinks: List<BankReceiptLink>,
'''
sig_new = '''    lastMonthSuggestion: com.example.data.BankLastMonthAssignmentSuggestion?,
    transferSuggestion: com.example.data.BankTransferSuggestion?,
    suggestedTransferCounterpart: BankTransaction?,
    transferCounterpart: BankTransaction?,
    linkedLinks: List<BankReceiptLink>,
'''
if 'transferSuggestion: com.example.data.BankTransferSuggestion?' not in ui:
    assert sig_marker in ui
    ui = ui.replace(sig_marker, sig_new, 1)

card_marker = '''        if (lastMonthSuggestion != null) {
'''
card_insert = '''        if (transaction.classification == BankTransactionClassification.TRANSFER) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Umbuchung / Gegenbuchung", fontWeight = FontWeight.Bold, color = DarkNavy)
                        when {
                            transferCounterpart != null -> {
                                Text("Beidseitig verknüpft", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = EmeraldGreen)
                                Text(
                                    "${formatDate(transferCounterpart.bookingDate)} • ${transferCounterpart.counterparty.ifBlank { "Eigenes Konto" }} • ${NumberFormatter.format(transferCounterpart.amount)}",
                                    fontSize = 11.sp,
                                    color = DarkNavy
                                )
                                Text("Gegenkonto: ${transferCounterpart.accountId}", fontSize = 10.sp, color = SlateGray)
                                OutlinedButton(
                                    onClick = { viewModel.unlinkBankTransferPair(transaction.transactionId) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Gegenbuchungs-Verknüpfung lösen") }
                            }
                            transferSuggestion != null -> {
                                Text("Mögliche Gegenbuchung • ${transferSuggestion.score}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
                                suggestedTransferCounterpart?.let { candidate ->
                                    Text(
                                        "${formatDate(candidate.bookingDate)} • ${candidate.counterparty.ifBlank { "Eigenes Konto" }} • ${NumberFormatter.format(candidate.amount)}",
                                        fontSize = 11.sp,
                                        color = DarkNavy
                                    )
                                }
                                Text(transferSuggestion.reasons.joinToString(" • "), fontSize = 10.sp, color = SlateGray)
                                Text("Die Verknüpfung erfolgt erst nach deiner Bestätigung.", fontSize = 10.sp, color = SlateGray)
                                Button(
                                    onClick = { viewModel.confirmBankTransferPair(transaction.transactionId, transferSuggestion.counterTransactionId) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Als Gegenbuchung verknüpfen") }
                            }
                            else -> Text("Keine ausreichend passende Gegenbuchung auf einem anderen importierten Konto gefunden.", fontSize = 11.sp, color = SlateGray)
                        }
                    }
                }
            }
        }

'''
if 'Text("Umbuchung / Gegenbuchung"' not in ui:
    assert card_marker in ui
    ui = ui.replace(card_marker, card_insert + card_marker, 1)

vm_path.write_text(vm)
ui_path.write_text(ui)
print('Transfer counterpart UI patch applied')
