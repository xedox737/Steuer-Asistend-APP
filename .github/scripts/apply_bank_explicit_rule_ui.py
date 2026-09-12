from pathlib import Path

vm_path = Path('app/src/main/java/com/example/ui/ReceiptViewModel.kt')
ui_path = Path('app/src/main/java/com/example/ui/BankFeature.kt')
vm = vm_path.read_text()
ui = ui_path.read_text()

vm_marker = '''    fun removeBankReceiptLink(linkId: String, transactionId: String) {
'''
vm_insert = '''    fun proposeBankRuleFromConfirmedReceipt(transactionId: String, receiptId: Int) {
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

'''
if 'fun proposeBankRuleFromConfirmedReceipt(' not in vm:
    assert vm_marker in vm
    vm = vm.replace(vm_marker, vm_insert + vm_marker, 1)

ui_old = '''                                TextButton(onClick = { onReceiptDetails(receipt) }) { Text("Öffnen") }
                                TextButton(onClick = { viewModel.removeBankReceiptLink(link.linkId, transaction.transactionId) }) { Text("Lösen") }
'''
ui_new = '''                                TextButton(onClick = { onReceiptDetails(receipt) }) { Text("Öffnen") }
                                TextButton(onClick = { viewModel.proposeBankRuleFromConfirmedReceipt(transaction.transactionId, receipt.id) }) { Text("Regel merken") }
                                TextButton(onClick = { viewModel.removeBankReceiptLink(link.linkId, transaction.transactionId) }) { Text("Lösen") }
'''
if 'proposeBankRuleFromConfirmedReceipt(transaction.transactionId, receipt.id)' not in ui:
    assert ui_old in ui
    ui = ui.replace(ui_old, ui_new, 1)

vm_path.write_text(vm)
ui_path.write_text(ui)
print('Explicit rule proposal UI patch applied')
