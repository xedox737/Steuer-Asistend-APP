from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"Anchor not found: {label}")
    return text.replace(old, new, 1)

# 1) Bank transaction model + matching guards
path = Path("app/src/main/java/com/example/data/BankingModels.kt")
text = path.read_text()
text = replace_once(
    text,
    '        Index(value = ["reconciliationStatus"], name = "index_bank_transactions_reconciliationStatus")\n',
    '        Index(value = ["reconciliationStatus"], name = "index_bank_transactions_reconciliationStatus"),\n'
    '        Index(value = ["classification"], name = "index_bank_transactions_classification")\n',
    "classification index"
)
text = replace_once(
    text,
    '    val noReceiptReason: String = "",\n    val importedAt: String = "",\n',
    '    val noReceiptReason: String = "",\n'
    '    val classification: String = BankTransactionClassification.NORMAL,\n'
    '    val transferCounterAccountId: String = "",\n'
    '    val linkedTransferTransactionId: String = "",\n'
    '    val reviewState: String = BankReviewState.OPEN,\n'
    '    val importedAt: String = "",\n',
    "classification fields"
)
text = replace_once(
    text,
    '    @Query("UPDATE bank_transactions SET reconciliationStatus = :status, noReceiptReason = :reason, updatedAt = :updatedAt WHERE transactionId = :transactionId")\n    suspend fun updateTransactionStatus(transactionId: String, status: String, reason: String = "", updatedAt: String)\n',
    '    @Query("UPDATE bank_transactions SET reconciliationStatus = :status, noReceiptReason = :reason, updatedAt = :updatedAt WHERE transactionId = :transactionId")\n'
    '    suspend fun updateTransactionStatus(transactionId: String, status: String, reason: String = "", updatedAt: String)\n\n'
    '    @Query("UPDATE bank_transactions SET classification = :classification, transferCounterAccountId = :transferCounterAccountId, linkedTransferTransactionId = :linkedTransferTransactionId, reviewState = :reviewState, updatedAt = :updatedAt WHERE transactionId = :transactionId")\n'
    '    suspend fun updateTransactionClassification(\n'
    '        transactionId: String,\n'
    '        classification: String,\n'
    '        transferCounterAccountId: String = "",\n'
    '        linkedTransferTransactionId: String = "",\n'
    '        reviewState: String = BankReviewState.OPEN,\n'
    '        updatedAt: String\n'
    '    )\n',
    "classification dao"
)
text = text.replace(
    '.filter { it.reconciliationStatus != BankReconciliationStatus.NO_RECEIPT_REQUIRED }',
    '.filter { BankClassificationPolicy.decision(it).eligibleForReceiptMatching }'
)
text = replace_once(
    text,
    '    fun bestForTransaction(\n        transaction: BankTransaction,\n        receipts: List<Receipt>,\n        links: List<BankReceiptLink> = emptyList()\n    ): BankMatchSuggestion? {\n        val linkedReceiptIds',
    '    fun bestForTransaction(\n        transaction: BankTransaction,\n        receipts: List<Receipt>,\n        links: List<BankReceiptLink> = emptyList()\n    ): BankMatchSuggestion? {\n        if (!BankClassificationPolicy.decision(transaction).eligibleForReceiptMatching) return null\n        val linkedReceiptIds',
    "bestForTransaction classification guard"
)
text = replace_once(
    text,
    '    fun rankReceipts(transaction: BankTransaction, receipts: List<Receipt>, links: List<BankReceiptLink>): List<BankMatchSuggestion> =\n        receipts.asSequence()\n',
    '    fun rankReceipts(transaction: BankTransaction, receipts: List<Receipt>, links: List<BankReceiptLink>): List<BankMatchSuggestion> =\n        if (!BankClassificationPolicy.decision(transaction).eligibleForReceiptMatching) emptyList() else receipts.asSequence()\n',
    "rankReceipts classification guard"
)
text = replace_once(
    text,
    '    fun propose(transaction: BankTransaction, receipt: Receipt, existingLinks: List<BankReceiptLink>): Allocation {\n        val txAllocated',
    '    fun propose(transaction: BankTransaction, receipt: Receipt, existingLinks: List<BankReceiptLink>): Allocation {\n        if (!BankClassificationPolicy.decision(transaction).eligibleForReceiptMatching) {\n            return Allocation(false, 0.0, transaction.absoluteAmount, receipt.bruttobetrag, "Privat-/Umbuchungen werden nicht mit Belegen verknüpft.")\n        }\n        val txAllocated',
    "link policy classification guard"
)
path.write_text(text)

# 2) Room schema migration 28 -> 29
path = Path("app/src/main/java/com/example/data/ReceiptDatabase.kt")
text = path.read_text()
migration = '''\nval MIGRATION_28_29 = object : androidx.room.migration.Migration(28, 29) {\n    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {\n        db.execSQL("ALTER TABLE bank_transactions ADD COLUMN classification TEXT NOT NULL DEFAULT 'NORMAL'")\n        db.execSQL("ALTER TABLE bank_transactions ADD COLUMN transferCounterAccountId TEXT NOT NULL DEFAULT ''")\n        db.execSQL("ALTER TABLE bank_transactions ADD COLUMN linkedTransferTransactionId TEXT NOT NULL DEFAULT ''")\n        db.execSQL("ALTER TABLE bank_transactions ADD COLUMN reviewState TEXT NOT NULL DEFAULT 'OPEN'")\n        db.execSQL("CREATE INDEX IF NOT EXISTS index_bank_transactions_classification ON bank_transactions(classification)")\n    }\n}\n\n'''
text = replace_once(text, '@Database(entities = [', migration + '@Database(entities = [', "migration insertion")
text = replace_once(text, 'version = 28, exportSchema = false)', 'version = 29, exportSchema = false)', "database version")
text = replace_once(
    text,
    'MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28)',
    'MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29)',
    "migration registration"
)
path.write_text(text)

# 3) ViewModel actions for explicit user classification + undo
path = Path("app/src/main/java/com/example/ui/ReceiptViewModel.kt")
text = path.read_text()
anchor = '    fun startReceiptFromBankTransaction(transaction: com.example.data.BankTransaction) {\n'
block = '''    fun markBankTransactionPrivateIgnored(transactionId: String) {\n        classifyBankTransaction(transactionId, com.example.data.BankTransactionClassification.PRIVATE_IGNORED)\n    }\n\n    fun markBankTransactionTransfer(\n        transactionId: String,\n        transferCounterAccountId: String = "",\n        linkedTransferTransactionId: String = ""\n    ) {\n        classifyBankTransaction(\n            transactionId,\n            com.example.data.BankTransactionClassification.TRANSFER,\n            transferCounterAccountId,\n            linkedTransferTransactionId\n        )\n    }\n\n    fun resetBankTransactionClassification(transactionId: String) {\n        classifyBankTransaction(transactionId, com.example.data.BankTransactionClassification.NORMAL)\n    }\n\n    private fun classifyBankTransaction(\n        transactionId: String,\n        classification: String,\n        transferCounterAccountId: String = "",\n        linkedTransferTransactionId: String = ""\n    ) {\n        viewModelScope.launch(Dispatchers.IO) {\n            val dao = database.bankDao()\n            val transaction = dao.getTransaction(transactionId) ?: return@launch\n            val now = java.time.Instant.now().toString()\n            val updated = com.example.data.BankClassificationPolicy.classify(\n                transactionId = transaction.transactionId,\n                classification = classification,\n                transferCounterAccountId = transferCounterAccountId,\n                linkedTransferTransactionId = linkedTransferTransactionId,\n                now = now\n            )\n            dao.updateTransactionClassification(\n                transactionId = transactionId,\n                classification = updated.classification,\n                transferCounterAccountId = updated.transferCounterAccountId,\n                linkedTransferTransactionId = updated.linkedTransferTransactionId,\n                reviewState = updated.reviewState,\n                updatedAt = now\n            )\n            _bankImportStatus.value = when (updated.classification) {\n                com.example.data.BankTransactionClassification.PRIVATE_IGNORED -> "Buchung als Privat/ignoriert markiert. Kein Belegabgleich und kein normaler DATEV-Export."\n                com.example.data.BankTransactionClassification.TRANSFER -> "Buchung als Umbuchung markiert. Kein Belegabgleich und kein normaler Einnahmen-/Ausgabenexport."\n                else -> "Sonderklassifikation entfernt. Buchung wird wieder normal geprüft."\n            }\n        }\n    }\n\n'''
text = replace_once(text, anchor, block + anchor, "viewmodel classification actions")
path.write_text(text)

# 4) Compact UI policy: special movements count as resolved, not missing receipts.
path = Path("app/src/main/java/com/example/ui/BankCompactUiPolicy.kt")
text = path.read_text()
text = text.replace(
    'transaction.reconciliationStatus == BankReconciliationStatus.OPEN',
    'BankClassificationPolicy.decision(transaction).requiresReceiptReview && transaction.reconciliationStatus == BankReconciliationStatus.OPEN'
)
text = text.replace(
    'it.reconciliationStatus == BankReconciliationStatus.OPEN',
    'BankClassificationPolicy.decision(it).requiresReceiptReview && it.reconciliationStatus == BankReconciliationStatus.OPEN'
)
if 'BankClassificationPolicy' in text and 'import com.example.data.BankClassificationPolicy' not in text:
    text = text.replace('import com.example.data.BankTransaction\n', 'import com.example.data.BankTransaction\nimport com.example.data.BankClassificationPolicy\n')
path.write_text(text)

# 5) Existing Bank UI: add actions/status without redesign.
path = Path("app/src/main/java/com/example/ui/BankFeature.kt")
text = path.read_text()
if 'import com.example.data.BankTransactionClassification\n' not in text:
    text = replace_once(
        text,
        'import com.example.data.BankTransaction\n',
        'import com.example.data.BankTransaction\nimport com.example.data.BankTransactionClassification\n',
        'classification UI import'
    )

text = text.replace(
    '            BankStatusBadge(transaction.reconciliationStatus)',
    '            BankPrimaryStatusBadge(transaction)'
)

badge_anchor = '@Composable\nprivate fun BankTransactionDetailsScreen('
badge_block = '''@Composable\nprivate fun BankPrimaryStatusBadge(transaction: BankTransaction) {\n    when (transaction.classification) {\n        BankTransactionClassification.PRIVATE_IGNORED -> Text(\n            "Privat",\n            fontSize = 10.sp,\n            fontWeight = FontWeight.SemiBold,\n            color = SlateGray\n        )\n        BankTransactionClassification.TRANSFER -> Text(\n            "Umbuchung",\n            fontSize = 10.sp,\n            fontWeight = FontWeight.SemiBold,\n            color = AccentBlue\n        )\n        else -> BankStatusBadge(transaction.reconciliationStatus)\n    }\n}\n\n'''
text = replace_once(text, badge_anchor, badge_block + badge_anchor, 'classification status badge')

menu_anchor = '''                    DropdownMenu(expanded = detailMenuOpen, onDismissRequest = { detailMenuOpen = false }) {\n                        if (transaction.reconciliationStatus == BankReconciliationStatus.OPEN) {\n'''
menu_block = '''                    DropdownMenu(expanded = detailMenuOpen, onDismissRequest = { detailMenuOpen = false }) {\n                        if (transaction.classification == BankTransactionClassification.NORMAL) {\n                            DropdownMenuItem(text = { Text("Privat / ignorieren") }, onClick = {\n                                viewModel.markBankTransactionPrivateIgnored(transaction.transactionId)\n                                detailMenuOpen = false\n                            })\n                            DropdownMenuItem(text = { Text("Als Umbuchung markieren") }, onClick = {\n                                viewModel.markBankTransactionTransfer(transaction.transactionId)\n                                detailMenuOpen = false\n                            })\n                        } else {\n                            DropdownMenuItem(text = { Text("Sonderklassifikation entfernen") }, onClick = {\n                                viewModel.resetBankTransactionClassification(transaction.transactionId)\n                                detailMenuOpen = false\n                            })\n                        }\n                        if (transaction.classification == BankTransactionClassification.NORMAL && transaction.reconciliationStatus == BankReconciliationStatus.OPEN) {\n'''
text = replace_once(text, menu_anchor, menu_block, 'detail classification actions')

# Hide receipt-specific action row/suggestions for special movements.
quick_anchor = '''        item {\n            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {\n                BankQuickAction("Beleg\\nsuchen", Icons.Default.Search, Modifier.weight(1f), onClick = onPickReceipt)\n'''
quick_block = '''        if (transaction.classification == BankTransactionClassification.NORMAL) item {\n            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {\n                BankQuickAction("Beleg\\nsuchen", Icons.Default.Search, Modifier.weight(1f), onClick = onPickReceipt)\n'''
text = replace_once(text, quick_anchor, quick_block, 'hide receipt quick actions for special classification')

path.write_text(text)

print("Phase A persistence/UI patch applied")