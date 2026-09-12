from pathlib import Path

vm_path = Path('app/src/main/java/com/example/ui/ReceiptViewModel.kt')
ui_path = Path('app/src/main/java/com/example/ui/BankFeature.kt')

vm = vm_path.read_text()
ui = ui_path.read_text()

vm_anchor = '''    fun markBankTransactionPrivateIgnored(transactionId: String) {
        classifyBankTransaction(transactionId, com.example.data.BankTransactionClassification.PRIVATE_IGNORED)
    }
'''
vm_insert = '''    fun executeBankBatchAction(
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

''' + vm_anchor
if vm_anchor not in vm:
    raise SystemExit('ReceiptViewModel anchor not found')
vm = vm.replace(vm_anchor, vm_insert, 1)

state_anchor = '''    var toolsMode by remember { mutableStateOf(BankToolsMode.NONE) }
    var showImportDetails by remember { mutableStateOf(false) }
'''
state_insert = '''    var toolsMode by remember { mutableStateOf(BankToolsMode.NONE) }
    var showImportDetails by remember { mutableStateOf(false) }
    var batchSelectionMode by remember { mutableStateOf(false) }
    var selectedBatchIds by remember { mutableStateOf(setOf<String>()) }
    var batchPreviewAction by remember { mutableStateOf<com.example.data.BankBatchAction?>(null) }
'''
if state_anchor not in ui:
    raise SystemExit('BankFeature state anchor not found')
ui = ui.replace(state_anchor, state_insert, 1)

menu_anchor = '''                            DropdownMenuItem(text = { Text("DATEV-Vorprüfung") }, onClick = { toolsMode = BankToolsMode.DATEV_PRECHECK; toolsMenuOpen = false })
'''
menu_insert = menu_anchor + '''                            DropdownMenuItem(text = { Text(if (batchSelectionMode) "Mehrfachauswahl beenden" else "Mehrfachauswahl") }, onClick = {
                                batchSelectionMode = !batchSelectionMode
                                if (!batchSelectionMode) selectedBatchIds = emptySet()
                                toolsMenuOpen = false
                            })
'''
if menu_anchor not in ui:
    raise SystemExit('BankFeature menu anchor not found')
ui = ui.replace(menu_anchor, menu_insert, 1)

summary_anchor = '''            when (toolsMode) {
'''
batch_panel = '''            if (batchSelectionMode) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Mehrfachauswahl", fontWeight = FontWeight.Bold, color = DarkNavy)
                            Text("${selectedBatchIds.size} Buchungen ausgewählt. Bestehende Beleglinks oder widersprüchliche Sonderstatus werden nicht überschrieben.", fontSize = 11.sp, color = SlateGray)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { batchPreviewAction = com.example.data.BankBatchAction.PRIVATE_IGNORED },
                                    enabled = selectedBatchIds.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Privat", fontSize = 11.sp) }
                                OutlinedButton(
                                    onClick = { batchPreviewAction = com.example.data.BankBatchAction.TRANSFER },
                                    enabled = selectedBatchIds.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Umbuchung", fontSize = 11.sp) }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { batchPreviewAction = com.example.data.BankBatchAction.NO_RECEIPT_REQUIRED },
                                    enabled = selectedBatchIds.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Kein Beleg", fontSize = 11.sp) }
                                TextButton(
                                    onClick = { batchSelectionMode = false; selectedBatchIds = emptySet() },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Abbrechen", fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }

''' + summary_anchor
if summary_anchor not in ui:
    raise SystemExit('BankFeature summary anchor not found')
ui = ui.replace(summary_anchor, batch_panel, 1)

row_anchor = '''                                    BankCompactTransactionRow(
                                        transaction = transaction,
                                        onClick = { selectedTransactionId = transaction.transactionId }
                                    )
'''
row_replace = '''                                    BankCompactTransactionRow(
                                        transaction = transaction,
                                        selected = transaction.transactionId in selectedBatchIds,
                                        onClick = {
                                            if (batchSelectionMode) {
                                                selectedBatchIds = if (transaction.transactionId in selectedBatchIds)
                                                    selectedBatchIds - transaction.transactionId
                                                else selectedBatchIds + transaction.transactionId
                                            } else {
                                                selectedTransactionId = transaction.transactionId
                                            }
                                        }
                                    )
'''
if row_anchor not in ui:
    raise SystemExit('BankFeature row anchor not found')
ui = ui.replace(row_anchor, row_replace, 1)

signature_anchor = '''internal fun BankCompactTransactionRow(transaction: BankTransaction, onClick: () -> Unit) {
'''
signature_replace = '''internal fun BankCompactTransactionRow(transaction: BankTransaction, selected: Boolean = false, onClick: () -> Unit) {
'''
if signature_anchor not in ui:
    raise SystemExit('BankFeature row signature anchor not found')
ui = ui.replace(signature_anchor, signature_replace, 1)

modifier_anchor = '''        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
'''
modifier_replace = '''        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color(0xFFEFF6FF) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
'''
if modifier_anchor not in ui:
    raise SystemExit('BankFeature row modifier anchor not found')
ui = ui.replace(modifier_anchor, modifier_replace, 1)

dialog_anchor = '''    if (showImportDetails && !importStatus.isNullOrBlank()) {
'''
dialog_insert = '''    batchPreviewAction?.let { action ->
        val selected = transactions.filter { it.transactionId in selectedBatchIds }
        val preview = com.example.data.BankBatchActionPolicy.preview(selected, links, action)
        val actionLabel = when (action) {
            com.example.data.BankBatchAction.PRIVATE_IGNORED -> "Privat / ignorieren"
            com.example.data.BankBatchAction.TRANSFER -> "Als Umbuchung markieren"
            com.example.data.BankBatchAction.NO_RECEIPT_REQUIRED -> "Kein Beleg erforderlich"
        }
        AlertDialog(
            onDismissRequest = { batchPreviewAction = null },
            title = { Text("Sammelaktion bestätigen") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(actionLabel, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text("${preview.selectedCount} ausgewählt • ${preview.eligibleCount} werden geändert • ${preview.unchangedCount} bereits passend • ${preview.conflictCount} werden übersprungen.", fontSize = 12.sp)
                    if (preview.conflicts.isNotEmpty()) {
                        Text("Nicht automatisch geändert:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        preview.conflicts.take(8).forEach { conflict ->
                            val tx = transactions.firstOrNull { it.transactionId == conflict.transactionId }
                            Text("• ${tx?.counterparty?.ifBlank { "Buchung" } ?: "Buchung"}: ${conflict.reason}", fontSize = 11.sp, color = SlateGray)
                        }
                        if (preview.conflicts.size > 8) Text("… und ${preview.conflicts.size - 8} weitere", fontSize = 11.sp, color = SlateGray)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = preview.eligibleCount > 0,
                    onClick = {
                        viewModel.executeBankBatchAction(preview.eligibleTransactionIds, action)
                        selectedBatchIds = emptySet()
                        batchSelectionMode = false
                        batchPreviewAction = null
                    }
                ) { Text("${preview.eligibleCount} anwenden") }
            },
            dismissButton = { TextButton(onClick = { batchPreviewAction = null }) { Text("Abbrechen") } }
        )
    }

''' + dialog_anchor
if dialog_anchor not in ui:
    raise SystemExit('BankFeature dialog anchor not found')
ui = ui.replace(dialog_anchor, dialog_insert, 1)

vm_path.write_text(vm)
ui_path.write_text(ui)
