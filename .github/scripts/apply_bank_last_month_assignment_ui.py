from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if text.count(old) != 1:
        raise SystemExit(f"Expected one anchor in {path}, found {text.count(old)}")
    p.write_text(text.replace(old, new, 1))


vm = "app/src/main/java/com/example/ui/ReceiptViewModel.kt"
feature = "app/src/main/java/com/example/ui/BankFeature.kt"

vm_anchor = '''    fun startRentReceiptFromBankTransaction(
'''
vm_insert = '''    fun startReceiptLikeLastMonth(
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
'''
replace_once(vm, vm_anchor, vm_insert)

selected_anchor = '''    val selectedTransaction = selectedTransactionId?.let { id -> transactions.firstOrNull { it.transactionId == id } }

    LaunchedEffect(selectedTransaction != null) {
'''
selected_insert = '''    val selectedTransaction = selectedTransactionId?.let { id -> transactions.firstOrNull { it.transactionId == id } }
    val lastMonthSuggestion = remember(selectedTransaction, transactions, links, receipts) {
        selectedTransaction?.let { transaction ->
            com.example.data.BankLastMonthAssignmentPolicy.suggest(
                target = transaction,
                transactions = transactions,
                links = links,
                receipts = receipts
            )
        }
    }

    LaunchedEffect(selectedTransaction != null) {
'''
replace_once(feature, selected_anchor, selected_insert)

call_anchor = '''            suggestion = suggestions[selectedTransaction.transactionId],
            linkedLinks = linksByTransaction[selectedTransaction.transactionId].orEmpty(),
'''
call_insert = '''            suggestion = suggestions[selectedTransaction.transactionId],
            lastMonthSuggestion = lastMonthSuggestion,
            linkedLinks = linksByTransaction[selectedTransaction.transactionId].orEmpty(),
'''
replace_once(feature, call_anchor, call_insert)

sig_anchor = '''    suggestion: BankMatchSuggestion?,
    linkedLinks: List<BankReceiptLink>,
'''
sig_insert = '''    suggestion: BankMatchSuggestion?,
    lastMonthSuggestion: com.example.data.BankLastMonthAssignmentSuggestion?,
    linkedLinks: List<BankReceiptLink>,
'''
replace_once(feature, sig_anchor, sig_insert)

ui_anchor = '''        if (linkedLinks.isNotEmpty() || assignments.isNotEmpty()) {
'''
ui_insert = '''        if (lastMonthSuggestion != null) {
            item {
                val conflict = com.example.data.BankLastMonthAssignmentPolicy.hasAssignmentConflict(transaction, lastMonthSuggestion)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Wie letzten Monat zuordnen", fontWeight = FontWeight.Bold, color = DarkNavy)
                            Text("${lastMonthSuggestion.confidence}%", fontWeight = FontWeight.Bold, color = EmeraldGreen)
                        }
                        val targetText = listOf(
                            lastMonthSuggestion.suggestedVendor,
                            lastMonthSuggestion.suggestedCategory,
                            lastMonthSuggestion.suggestedSubcategory,
                            lastMonthSuggestion.suggestedUnit
                        ).filter { it.isNotBlank() }.joinToString(" • ")
                        if (targetText.isNotBlank()) Text(targetText, fontSize = 12.sp, color = SlateGray)
                        Text(
                            lastMonthSuggestion.reasons.take(4).joinToString(" • "),
                            fontSize = 11.sp,
                            color = SlateGray
                        )
                        if (conflict) {
                            Text(
                                "Bestehende Objekt-/Einheitszuordnung weicht ab. Es wird nichts überschrieben.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CrimsonRed
                            )
                        } else {
                            Button(
                                onClick = { viewModel.startReceiptLikeLastMonth(transaction, lastMonthSuggestion) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Zuordnung übernehmen und neuen Beleg prüfen") }
                        }
                        Text(
                            "Der alte Monatsbeleg wird nicht übernommen oder erneut verknüpft.",
                            fontSize = 10.sp,
                            color = SlateGray
                        )
                    }
                }
            }
        }

        if (linkedLinks.isNotEmpty() || assignments.isNotEmpty()) {
'''
replace_once(feature, ui_anchor, ui_insert)

print("Last-month assignment UI patch applied")
