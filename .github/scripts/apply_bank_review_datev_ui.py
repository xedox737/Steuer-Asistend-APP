from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"Anchor not found: {label}")
    return text.replace(old, new, 1)

# Persist review state independently from reconciliation/classification.
path = Path("app/src/main/java/com/example/data/BankingModels.kt")
text = path.read_text()
anchor = '''    @Query("SELECT * FROM bank_receipt_links ORDER BY createdAt DESC, linkId")\n    fun observeLinks(): Flow<List<BankReceiptLink>>\n'''
insert = '''    @Query("UPDATE bank_transactions SET reviewState = :reviewState, updatedAt = :updatedAt WHERE transactionId = :transactionId")\n    suspend fun updateTransactionReviewState(transactionId: String, reviewState: String, updatedAt: String)\n\n'''
text = replace_once(text, anchor, insert + anchor, "review-state DAO")
path.write_text(text)

# ViewModel actions: manual DONE / reopen review, without altering tax/reconciliation state.
path = Path("app/src/main/java/com/example/ui/ReceiptViewModel.kt")
text = path.read_text()
anchor = '''    fun markBankTransactionPrivateIgnored(transactionId: String) {\n'''
insert = '''    fun markBankTransactionReviewDone(transactionId: String) {\n        updateBankTransactionReviewState(transactionId, com.example.data.BankReviewState.DONE)\n    }\n\n    fun reopenBankTransactionReview(transactionId: String) {\n        updateBankTransactionReviewState(transactionId, com.example.data.BankReviewState.OPEN)\n    }\n\n    private fun updateBankTransactionReviewState(transactionId: String, reviewState: String) {\n        viewModelScope.launch(Dispatchers.IO) {\n            val now = java.time.Instant.now().toString()\n            database.bankDao().updateTransactionReviewState(transactionId, reviewState, now)\n            _bankImportStatus.value = if (reviewState == com.example.data.BankReviewState.DONE) {\n                "Buchung aus persönlicher Prüfliste als erledigt markiert. DATEV-Fachstatus bleibt unverändert."\n            } else {\n                "Buchung wieder in die persönliche Prüfliste aufgenommen."\n            }\n        }\n    }\n\n'''
text = replace_once(text, anchor, insert + anchor, "review-state ViewModel actions")
path.write_text(text)

# Extend existing Bank UI only; no redesign.
path = Path("app/src/main/java/com/example/ui/BankFeature.kt")
text = path.read_text()
text = replace_once(
    text,
    'import com.example.data.BankReconciliationStatus\n',
    'import com.example.data.BankReconciliationStatus\nimport com.example.data.BankReviewState\n',
    "BankReviewState import"
)
text = replace_once(
    text,
    'private enum class BankToolsMode { NONE, RULES, RENT, LOAN_RECURRING, REVIEW_COMBINATIONS, REVERSE_RECEIPT }',
    'private enum class BankToolsMode { NONE, RULES, RENT, LOAN_RECURRING, REVIEW_COMBINATIONS, REVERSE_RECEIPT, DATEV_PRECHECK }',
    "DATEV tool enum"
)
text = replace_once(
    text,
    '                            DropdownMenuItem(text = { Text("Beleg → Bank-Zuordnung") }, onClick = { toolsMode = BankToolsMode.REVERSE_RECEIPT; toolsMenuOpen = false })\n',
    '                            DropdownMenuItem(text = { Text("Beleg → Bank-Zuordnung") }, onClick = { toolsMode = BankToolsMode.REVERSE_RECEIPT; toolsMenuOpen = false })\n'
    '                            DropdownMenuItem(text = { Text("DATEV-Vorprüfung") }, onClick = { toolsMode = BankToolsMode.DATEV_PRECHECK; toolsMenuOpen = false })\n',
    "DATEV tool menu"
)
text = replace_once(
    text,
    '                BankToolsMode.REVERSE_RECEIPT -> item {\n',
    '                BankToolsMode.DATEV_PRECHECK -> item { ToolContainer("DATEV-Vorprüfung", onClose = { toolsMode = BankToolsMode.NONE }) { BankDatevPrecheckPanel(accountTransactions) } }\n'
    '                BankToolsMode.REVERSE_RECEIPT -> item {\n',
    "DATEV tool panel"
)
old_badge = '''private fun BankPrimaryStatusBadge(transaction: BankTransaction) {\n    when (transaction.classification) {\n'''
new_badge = '''private fun BankPrimaryStatusBadge(transaction: BankTransaction) {\n    when (transaction.classification) {\n'''
# keep opening but append separate review state after the when block using exact tail
text = replace_once(
    text,
    '''        else -> BankStatusBadge(transaction.reconciliationStatus)\n    }\n}\n\n@Composable\nprivate fun BankTransactionDetailsScreen''',
    '''        else -> BankStatusBadge(transaction.reconciliationStatus)\n    }\n    if (transaction.classification == BankTransactionClassification.NORMAL && transaction.reviewState == BankReviewState.DONE) {\n        Text("Erledigt", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = SlateGray)\n    }\n}\n\n@Composable\nprivate fun BankTransactionDetailsScreen''',
    "DONE badge"
)
menu_anchor = '''                        if (transaction.classification == BankTransactionClassification.NORMAL && transaction.reconciliationStatus == BankReconciliationStatus.OPEN) {\n'''
menu_insert = '''                        if (transaction.classification == BankTransactionClassification.NORMAL) {\n                            if (transaction.reviewState == BankReviewState.OPEN) {\n                                DropdownMenuItem(text = { Text("Als erledigt markieren") }, onClick = {\n                                    viewModel.markBankTransactionReviewDone(transaction.transactionId)\n                                    detailMenuOpen = false\n                                })\n                            } else {\n                                DropdownMenuItem(text = { Text("Prüfung wieder öffnen") }, onClick = {\n                                    viewModel.reopenBankTransactionReview(transaction.transactionId)\n                                    detailMenuOpen = false\n                                })\n                            }\n                        }\n'''
text = replace_once(text, menu_anchor, menu_insert + menu_anchor, "DONE detail action")
path.write_text(text)

print("Bank review/DATEV UI patch applied")
