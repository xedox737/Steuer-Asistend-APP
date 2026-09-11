from pathlib import Path

ROOT = Path('.')

def patch(path, old, new, count=1):
    p = ROOT / path
    text = p.read_text()
    if old not in text:
        raise SystemExit(f'marker not found in {path}: {old[:120]!r}')
    text2 = text.replace(old, new, count)
    p.write_text(text2)

# 1) Core model + DAO
path = 'app/src/main/java/com/example/data/BankingModels.kt'
patch(path,
'''object BankReconciliationStatus {
    const val OPEN = "OPEN"
    const val PARTIAL = "PARTIAL"
    const val MATCHED = "MATCHED"
    const val NO_RECEIPT_REQUIRED = "NO_RECEIPT_REQUIRED"
    const val REVIEW = "REVIEW"
}
''',
'''object BankReconciliationStatus {
    const val OPEN = "OPEN"
    const val PARTIAL = "PARTIAL"
    const val MATCHED = "MATCHED"
    const val NO_RECEIPT_REQUIRED = "NO_RECEIPT_REQUIRED"
    const val REVIEW = "REVIEW"
}

object BankTransactionClassification {
    const val NORMAL = "NORMAL"
    const val PRIVATE_IGNORED = "PRIVATE_IGNORED"
    const val TRANSFER = "TRANSFER"
}

object BankReviewState {
    const val OPEN = "OPEN"
    const val DONE = "DONE"
}
''')
patch(path,
'''        Index(value = ["bookingDate"], name = "index_bank_transactions_bookingDate"),
        Index(value = ["reconciliationStatus"], name = "index_bank_transactions_reconciliationStatus")
''',
'''        Index(value = ["bookingDate"], name = "index_bank_transactions_bookingDate"),
        Index(value = ["reconciliationStatus"], name = "index_bank_transactions_reconciliationStatus"),
        Index(value = ["classification"], name = "index_bank_transactions_classification")
''')
patch(path,
'''    val reconciliationStatus: String = BankReconciliationStatus.OPEN,
    val noReceiptReason: String = "",
    val importedAt: String = "",
    val updatedAt: String = ""
) {
    val isIncome: Boolean get() = amount > 0.0
    val absoluteAmount: Double get() = kotlin.math.abs(amount)
}
''',
'''    val reconciliationStatus: String = BankReconciliationStatus.OPEN,
    val noReceiptReason: String = "",
    val classification: String = BankTransactionClassification.NORMAL,
    val transferCounterAccountId: String = "",
    val linkedTransferTransactionId: String = "",
    val reviewState: String = BankReviewState.OPEN,
    val importedAt: String = "",
    val updatedAt: String = ""
) {
    val isIncome: Boolean get() = amount > 0.0
    val absoluteAmount: Double get() = kotlin.math.abs(amount)
    val isTaxRelevant: Boolean get() = classification == BankTransactionClassification.NORMAL
}
''')
patch(path,
'''    @Query("UPDATE bank_transactions SET reconciliationStatus = :status, noReceiptReason = :reason, updatedAt = :updatedAt WHERE transactionId = :transactionId")
    suspend fun updateTransactionStatus(transactionId: String, status: String, reason: String = "", updatedAt: String)
''',
'''    @Query("UPDATE bank_transactions SET reconciliationStatus = :status, noReceiptReason = :reason, updatedAt = :updatedAt WHERE transactionId = :transactionId")
    suspend fun updateTransactionStatus(transactionId: String, status: String, reason: String = "", updatedAt: String)

    @Query("UPDATE bank_transactions SET classification = :classification, transferCounterAccountId = :counterAccountId, linkedTransferTransactionId = :linkedTransactionId, reviewState = :reviewState, updatedAt = :updatedAt WHERE transactionId = :transactionId")
    suspend fun updateTransactionClassification(
        transactionId: String,
        classification: String,
        counterAccountId: String = "",
        linkedTransactionId: String = "",
        reviewState: String = BankReviewState.OPEN,
        updatedAt: String
    )
''')

# 2) Dedicated business policy / DATEV guard
policy = ROOT / 'app/src/main/java/com/example/data/BankTransactionClassificationPolicy.kt'
policy.write_text('''package com.example.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

object BankTransactionClassificationPolicy {
    fun isTaxRelevant(transaction: BankTransaction): Boolean =
        transaction.classification == BankTransactionClassification.NORMAL

    fun isEligibleForAutomaticMatching(transaction: BankTransaction): Boolean =
        isTaxRelevant(transaction) && transaction.reconciliationStatus != BankReconciliationStatus.NO_RECEIPT_REQUIRED

    fun isEligibleForRecurring(transaction: BankTransaction): Boolean = isTaxRelevant(transaction)

    fun needsReceiptWarning(transaction: BankTransaction): Boolean =
        isEligibleForAutomaticMatching(transaction) && transaction.reconciliationStatus != BankReconciliationStatus.MATCHED

    fun isDatevExportAllowed(transaction: BankTransaction): Boolean =
        transaction.classification == BankTransactionClassification.NORMAL

    fun blockedReceiptIds(
        transactions: List<BankTransaction>,
        links: List<BankReceiptLink>
    ): Set<Int> {
        val blockedTransactions = transactions.asSequence()
            .filterNot(::isDatevExportAllowed)
            .map { it.transactionId }
            .toSet()
        return links.asSequence()
            .filter { it.transactionId in blockedTransactions }
            .map { it.receiptId }
            .toSet()
    }

    fun transferCandidates(
        source: BankTransaction,
        transactions: List<BankTransaction>,
        ownAccountIds: Set<String>,
        dayTolerance: Long = 3
    ): List<BankTransaction> {
        if (source.accountId !in ownAccountIds) return emptyList()
        val sourceDate = runCatching { LocalDate.parse(source.bookingDate) }.getOrNull() ?: return emptyList()
        return transactions.asSequence()
            .filter { it.transactionId != source.transactionId }
            .filter { it.accountId != source.accountId && it.accountId in ownAccountIds }
            .filter { abs(it.amount + source.amount) <= 0.01 }
            .filter {
                val date = runCatching { LocalDate.parse(it.bookingDate) }.getOrNull() ?: return@filter false
                abs(ChronoUnit.DAYS.between(sourceDate, date)) <= dayTolerance
            }
            .sortedWith(compareBy<BankTransaction> {
                abs(ChronoUnit.DAYS.between(sourceDate, LocalDate.parse(it.bookingDate)))
            }.thenBy { it.transactionId })
            .toList()
    }
}
''')

# 3) Room migration 28 -> 29
path = 'app/src/main/java/com/example/data/ReceiptDatabase.kt'
patch(path,
'''val MIGRATION_27_28 = object : androidx.room.migration.Migration(27, 28) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bank_rent_assignments ADD COLUMN note TEXT NOT NULL DEFAULT ''")
    }
}

@Database''',
'''val MIGRATION_27_28 = object : androidx.room.migration.Migration(27, 28) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bank_rent_assignments ADD COLUMN note TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_28_29 = object : androidx.room.migration.Migration(28, 29) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bank_transactions ADD COLUMN classification TEXT NOT NULL DEFAULT 'NORMAL'")
        db.execSQL("ALTER TABLE bank_transactions ADD COLUMN transferCounterAccountId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE bank_transactions ADD COLUMN linkedTransferTransactionId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE bank_transactions ADD COLUMN reviewState TEXT NOT NULL DEFAULT 'OPEN'")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bank_transactions_classification ON bank_transactions(classification)")
    }
}

@Database''')
patch(path, 'version = 28, exportSchema = false)', 'version = 29, exportSchema = false)')
patch(path,
'MIGRATION_26_27, MIGRATION_27_28)',
'MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29)')

# 4) Drive backup / restore
path = 'app/src/main/java/com/example/data/SupplementalDriveBackup.kt'
patch(path, 'internal const val SCHEMA_VERSION = 11', 'internal const val SCHEMA_VERSION = 12')
patch(path,
'''        put("reconciliationStatus", reconciliationStatus); put("noReceiptReason", noReceiptReason)
        put("importedAt", importedAt); put("updatedAt", updatedAt)
''',
'''        put("reconciliationStatus", reconciliationStatus); put("noReceiptReason", noReceiptReason)
        put("classification", classification); put("transferCounterAccountId", transferCounterAccountId)
        put("linkedTransferTransactionId", linkedTransferTransactionId); put("reviewState", reviewState)
        put("importedAt", importedAt); put("updatedAt", updatedAt)
''')
patch(path,
'''        reconciliationStatus = optString("reconciliationStatus", BankReconciliationStatus.OPEN),
        noReceiptReason = optString("noReceiptReason", ""), importedAt = optString("importedAt", ""),
        updatedAt = optString("updatedAt", "")
''',
'''        reconciliationStatus = optString("reconciliationStatus", BankReconciliationStatus.OPEN),
        noReceiptReason = optString("noReceiptReason", ""),
        classification = optString("classification", BankTransactionClassification.NORMAL),
        transferCounterAccountId = optString("transferCounterAccountId", ""),
        linkedTransferTransactionId = optString("linkedTransferTransactionId", ""),
        reviewState = optString("reviewState", BankReviewState.OPEN),
        importedAt = optString("importedAt", ""), updatedAt = optString("updatedAt", "")
''')

# 5) ViewModel: matching/review exclusions, actions, DATEV hard block
path = 'app/src/main/java/com/example/ui/ReceiptViewModel.kt'
patch(path,
'''            com.example.data.BankRecurringPaymentDetector.detect(transactions, java.time.LocalDate.now())
''',
'''            com.example.data.BankRecurringPaymentDetector.detect(
                transactions.filter(com.example.data.BankTransactionClassificationPolicy::isEligibleForRecurring),
                java.time.LocalDate.now()
            )
''')
patch(path,
'''            transactions.associate { it.transactionId to com.example.data.BankRuleEngine.evaluate(it, rules) }
''',
'''            transactions.asSequence()
                .filter(com.example.data.BankTransactionClassificationPolicy::isTaxRelevant)
                .associate { it.transactionId to com.example.data.BankRuleEngine.evaluate(it, rules) }
''')
patch(path,
'''            val base = com.example.data.BankReceiptMatcher.bestSuggestions(transactions, currentReceipts, links)
            base.mapValues { (transactionId, suggestion) ->
                val tx = transactions.firstOrNull { it.transactionId == transactionId }
''',
'''            val eligibleTransactions = transactions.filter(com.example.data.BankTransactionClassificationPolicy::isEligibleForAutomaticMatching)
            val base = com.example.data.BankReceiptMatcher.bestSuggestions(eligibleTransactions, currentReceipts, links)
            base.mapValues { (transactionId, suggestion) ->
                val tx = eligibleTransactions.firstOrNull { it.transactionId == transactionId }
''')
patch(path,
'''                .filter { it.isIncome && it.transactionId !in dismissed }
''',
'''                .filter { it.isIncome && it.transactionId !in dismissed && com.example.data.BankTransactionClassificationPolicy.isTaxRelevant(it) }
''')
patch(path,
'''            transactions.asSequence()
                .filterNot { it.transactionId in dismissed }
''',
'''            transactions.asSequence()
                .filter(com.example.data.BankTransactionClassificationPolicy::isTaxRelevant)
                .filterNot { it.transactionId in dismissed }
''')
patch(path,
'''                transactions = base[0] as List<com.example.data.BankTransaction>,
''',
'''                transactions = (base[0] as List<com.example.data.BankTransaction>)
                    .filter(com.example.data.BankTransactionClassificationPolicy::isTaxRelevant),
''')

vm = (ROOT / path).read_text()
marker = '    fun markBankTransactionNoReceiptRequired('
idx = vm.find(marker)
if idx < 0:
    raise SystemExit('markBankTransactionNoReceiptRequired marker not found')
methods = '''    fun markBankTransactionPrivate(transactionId: String) {
        setBankTransactionClassification(transactionId, com.example.data.BankTransactionClassification.PRIVATE_IGNORED)
    }

    fun markBankTransactionTransfer(transactionId: String, counterAccountId: String = "") {
        setBankTransactionClassification(transactionId, com.example.data.BankTransactionClassification.TRANSFER, counterAccountId)
    }

    fun clearBankTransactionClassification(transactionId: String) {
        setBankTransactionClassification(transactionId, com.example.data.BankTransactionClassification.NORMAL)
    }

    private fun setBankTransactionClassification(transactionId: String, classification: String, counterAccountId: String = "") {
        viewModelScope.launch {
            val reviewState = if (classification == com.example.data.BankTransactionClassification.NORMAL) {
                com.example.data.BankReviewState.OPEN
            } else {
                com.example.data.BankReviewState.DONE
            }
            database.bankDao().updateTransactionClassification(
                transactionId = transactionId,
                classification = classification,
                counterAccountId = counterAccountId,
                linkedTransactionId = "",
                reviewState = reviewState,
                updatedAt = java.time.Instant.now().toString()
            )
            _bankImportStatus.value = when (classification) {
                com.example.data.BankTransactionClassification.PRIVATE_IGNORED -> "Buchung als Privat / ignoriert markiert."
                com.example.data.BankTransactionClassification.TRANSFER -> "Buchung als Umbuchung markiert."
                else -> "Sonderstatus der Buchung aufgehoben."
            }
        }
    }

'''
vm = vm[:idx] + methods + vm[idx:]
# hard DATEV block for wizard
old = '        val records = _wizardMappedRecords.value\n        val excluded = _wizardExcludedReceipts.value\n'
new = '''        val records = _wizardMappedRecords.value
        val blockedReceiptIds = com.example.data.BankTransactionClassificationPolicy.blockedReceiptIds(
            bankTransactions.value, bankReceiptLinks.value
        )
        if (records.any { it.receiptId in blockedReceiptIds }) {
            Log.w("ReceiptViewModel", "DATEV export blocked: receipt is linked to PRIVATE_IGNORED or TRANSFER bank transaction")
            return null
        }
        val excluded = _wizardExcludedReceipts.value
'''
if old not in vm:
    raise SystemExit('DATEV wizard marker not found')
vm = vm.replace(old, new, 1)
# safe filtering in direct DATEV export
old = '        val propShort = if (metaName.isNotEmpty()) metaName.take(15) else "MFH Sulz"\n\n        val config = com.example.util.DatevConfig('
new = '''        val propShort = if (metaName.isNotEmpty()) metaName.take(15) else "MFH Sulz"
        val blockedReceiptIds = com.example.data.BankTransactionClassificationPolicy.blockedReceiptIds(
            bankTransactions.value, bankReceiptLinks.value
        )
        val exportReceipts = receipts.filterNot { it.id in blockedReceiptIds }
        if (receipts.isNotEmpty() && exportReceipts.isEmpty()) {
            Log.w("ReceiptViewModel", "DATEV export blocked: all receipts are linked to excluded bank transactions")
            return null
        }

        val config = com.example.util.DatevConfig('''
if old not in vm:
    raise SystemExit('direct DATEV marker not found')
vm = vm.replace(old, new, 1)
start = vm.find('    fun exportToDatev(')
end = vm.find('    fun exportToPdf(', start)
segment = vm[start:end]
segment = segment.replace('createDatevZipPackage(context, receipts, config)', 'createDatevZipPackage(context, exportReceipts, config)')
segment = segment.replace('generateBuchungsstapelCsv(receipts, config)', 'generateBuchungsstapelCsv(exportReceipts, config)')
segment = segment.replace('generateDocumentXml(receipts, config)', 'generateDocumentXml(exportReceipts, config)')
segment = segment.replace('                        receipts.forEach { r ->', '                        exportReceipts.forEach { r ->')
vm = vm[:start] + segment + vm[end:]
(ROOT / path).write_text(vm)

# 6) Compact filters for new statuses
path = 'app/src/main/java/com/example/ui/BankCompactUiPolicy.kt'
patch(path, 'import com.example.data.BankReconciliationStatus\n', 'import com.example.data.BankReconciliationStatus\nimport com.example.data.BankTransactionClassification\n')
patch(path,
'''    REVIEW,
    NO_RECEIPT_REQUIRED
''',
'''    REVIEW,
    PRIVATE,
    TRANSFER,
    NO_RECEIPT_REQUIRED
''')
patch(path,
'''    val partial: Int,
    val review: Int,
    val noReceiptRequired: Int
''',
'''    val partial: Int,
    val review: Int,
    val privateIgnored: Int,
    val transfer: Int,
    val noReceiptRequired: Int
''')
patch(path,
'''        review = transactions.count { it.reconciliationStatus == BankReconciliationStatus.REVIEW },
        noReceiptRequired = transactions.count { it.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED }
''',
'''        review = transactions.count { it.reconciliationStatus == BankReconciliationStatus.REVIEW },
        privateIgnored = transactions.count { it.classification == BankTransactionClassification.PRIVATE_IGNORED },
        transfer = transactions.count { it.classification == BankTransactionClassification.TRANSFER },
        noReceiptRequired = transactions.count { it.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED }
''')
patch(path,
'''        BankCompactFilter.REVIEW -> transactions.filter { it.reconciliationStatus == BankReconciliationStatus.REVIEW }
        BankCompactFilter.NO_RECEIPT_REQUIRED -> transactions.filter { it.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED }
''',
'''        BankCompactFilter.REVIEW -> transactions.filter { it.reconciliationStatus == BankReconciliationStatus.REVIEW }
        BankCompactFilter.PRIVATE -> transactions.filter { it.classification == BankTransactionClassification.PRIVATE_IGNORED }
        BankCompactFilter.TRANSFER -> transactions.filter { it.classification == BankTransactionClassification.TRANSFER }
        BankCompactFilter.NO_RECEIPT_REQUIRED -> transactions.filter { it.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED }
''')
patch(path,
'''        BankCompactFilter.REVIEW -> "Prüfen"
        BankCompactFilter.NO_RECEIPT_REQUIRED -> "Kein Beleg nötig"
''',
'''        BankCompactFilter.REVIEW -> "Prüfen"
        BankCompactFilter.PRIVATE -> "Privat"
        BankCompactFilter.TRANSFER -> "Umbuchung"
        BankCompactFilter.NO_RECEIPT_REQUIRED -> "Kein Beleg nötig"
''')
patch(path,
'''        BankCompactFilter.REVIEW -> counts.review
        BankCompactFilter.NO_RECEIPT_REQUIRED -> counts.noReceiptRequired
''',
'''        BankCompactFilter.REVIEW -> counts.review
        BankCompactFilter.PRIVATE -> counts.privateIgnored
        BankCompactFilter.TRANSFER -> counts.transfer
        BankCompactFilter.NO_RECEIPT_REQUIRED -> counts.noReceiptRequired
''')

# 7) Existing detail/list UI: unobtrusive badges + actions
path = 'app/src/main/java/com/example/ui/BankFeature.kt'
patch(path, 'import com.example.data.BankReconciliationStatus\n', 'import com.example.data.BankReconciliationStatus\nimport com.example.data.BankTransactionClassification\n')
text = (ROOT / path).read_text()
text = text.replace('            BankStatusBadge(transaction.reconciliationStatus)\n', '''            BankStatusBadge(transaction.reconciliationStatus)
            if (transaction.classification != BankTransactionClassification.NORMAL) {
                BankClassificationBadge(transaction.classification)
            }
''')
old = '''                    DropdownMenu(expanded = detailMenuOpen, onDismissRequest = { detailMenuOpen = false }) {
                        if (transaction.reconciliationStatus == BankReconciliationStatus.OPEN) {
'''
new = '''                    DropdownMenu(expanded = detailMenuOpen, onDismissRequest = { detailMenuOpen = false }) {
                        if (transaction.classification == BankTransactionClassification.NORMAL) {
                            DropdownMenuItem(text = { Text("Als Privat / ignorieren markieren") }, onClick = {
                                viewModel.markBankTransactionPrivate(transaction.transactionId)
                                detailMenuOpen = false
                            })
                            DropdownMenuItem(text = { Text("Als Umbuchung markieren") }, onClick = {
                                viewModel.markBankTransactionTransfer(transaction.transactionId)
                                detailMenuOpen = false
                            })
                        } else {
                            DropdownMenuItem(text = { Text("Sonderstatus aufheben") }, onClick = {
                                viewModel.clearBankTransactionClassification(transaction.transactionId)
                                detailMenuOpen = false
                            })
                        }
                        if (transaction.reconciliationStatus == BankReconciliationStatus.OPEN) {
'''
if old not in text:
    raise SystemExit('BankFeature menu marker not found')
text = text.replace(old, new, 1)
old = '''        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                BankQuickAction("Beleg\\nsuchen", Icons.Default.Search, Modifier.weight(1f), onClick = onPickReceipt)
                BankQuickAction("Beleg\\nanlegen", Icons.Default.Description, Modifier.weight(1f), onClick = { viewModel.startReceiptFromBankTransaction(transaction) })
                Column(Modifier.weight(1f)) {
                    BankTransactionSplitActions(viewModel = viewModel, transaction = transaction, compactTrigger = true, showAssignments = false)
                }
                BankQuickAction("Kein Beleg\\nerforderlich", Icons.Default.CheckCircle, Modifier.weight(1f), onClick = onNoReceipt)
            }
        }
'''
new = '''        if (transaction.classification == BankTransactionClassification.NORMAL) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                    BankQuickAction("Beleg\\nsuchen", Icons.Default.Search, Modifier.weight(1f), onClick = onPickReceipt)
                    BankQuickAction("Beleg\\nanlegen", Icons.Default.Description, Modifier.weight(1f), onClick = { viewModel.startReceiptFromBankTransaction(transaction) })
                    Column(Modifier.weight(1f)) {
                        BankTransactionSplitActions(viewModel = viewModel, transaction = transaction, compactTrigger = true, showAssignments = false)
                    }
                    BankQuickAction("Kein Beleg\\nerforderlich", Icons.Default.CheckCircle, Modifier.weight(1f), onClick = onNoReceipt)
                }
            }
        }
'''
if old not in text:
    raise SystemExit('BankFeature quick-action marker not found')
text = text.replace(old, new, 1)
old = '''        } else {
            item {
                OutlinedButton(
                    onClick = onNoReceipt,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    border = BorderStroke(1.dp, CrimsonRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Buchung ignorieren", color = CrimsonRed, fontWeight = FontWeight.Bold)
                }
            }
        }
'''
new = '''        } else if (transaction.classification == BankTransactionClassification.NORMAL) {
            item {
                OutlinedButton(
                    onClick = { viewModel.markBankTransactionPrivate(transaction.transactionId) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    border = BorderStroke(1.dp, CrimsonRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Als Privat / ignorieren markieren", color = CrimsonRed, fontWeight = FontWeight.Bold)
                }
            }
        }
'''
if old not in text:
    raise SystemExit('BankFeature ignore marker not found')
text = text.replace(old, new, 1)
insert_marker = '''@Composable
private fun BankMoneySummaryCard'''
badge = '''@Composable
private fun BankClassificationBadge(classification: String) {
    val (label, background, foreground) = when (classification) {
        BankTransactionClassification.PRIVATE_IGNORED -> Triple("Privat", Color(0xFFF1F5F9), Color(0xFF475569))
        BankTransactionClassification.TRANSFER -> Triple("Umbuchung", Color(0xFFE0F2FE), Color(0xFF0369A1))
        else -> return
    }
    Text(
        label,
        modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).background(background).padding(horizontal = 7.dp, vertical = 2.dp),
        color = foreground,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun BankMoneySummaryCard'''
if insert_marker not in text:
    raise SystemExit('BankFeature badge insertion marker not found')
text = text.replace(insert_marker, badge, 1)
(ROOT / path).write_text(text)

# 8) Acceptance tests
p = ROOT / 'app/src/test/java/com/example/data/BankPrivateTransferPhaseAAcceptanceTest.kt'
p.write_text('''package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankPrivateTransferPhaseAAcceptanceTest {
    private fun tx(id: String, amount: Double = -100.0, classification: String = BankTransactionClassification.NORMAL) =
        BankTransaction(transactionId = id, accountId = "a", bookingDate = "2026-09-11", amount = amount, classification = classification)

    @Test fun private_is_not_tax_relevant_matchable_or_datev_exportable() {
        val t = tx("p", classification = BankTransactionClassification.PRIVATE_IGNORED)
        assertFalse(BankTransactionClassificationPolicy.isTaxRelevant(t))
        assertFalse(BankTransactionClassificationPolicy.isEligibleForAutomaticMatching(t))
        assertFalse(BankTransactionClassificationPolicy.needsReceiptWarning(t))
        assertFalse(BankTransactionClassificationPolicy.isDatevExportAllowed(t))
    }

    @Test fun transfer_is_not_normal_income_or_expense_for_tax_pipeline() {
        val t = tx("u", classification = BankTransactionClassification.TRANSFER)
        assertFalse(BankTransactionClassificationPolicy.isTaxRelevant(t))
        assertFalse(BankTransactionClassificationPolicy.isEligibleForAutomaticMatching(t))
        assertFalse(BankTransactionClassificationPolicy.isDatevExportAllowed(t))
    }

    @Test fun no_receipt_required_remains_separate_from_classification() {
        val t = tx("n").copy(reconciliationStatus = BankReconciliationStatus.NO_RECEIPT_REQUIRED)
        assertEquals(BankTransactionClassification.NORMAL, t.classification)
        assertTrue(BankTransactionClassificationPolicy.isTaxRelevant(t))
        assertFalse(BankTransactionClassificationPolicy.isEligibleForAutomaticMatching(t))
    }

    @Test fun datev_guard_blocks_receipts_linked_to_private_or_transfer_transactions() {
        val transactions = listOf(tx("n"), tx("p", classification = BankTransactionClassification.PRIVATE_IGNORED), tx("u", classification = BankTransactionClassification.TRANSFER))
        val links = listOf(
            BankReceiptLink("l1", "n", 1, allocatedAmount = 10.0),
            BankReceiptLink("l2", "p", 2, allocatedAmount = 10.0),
            BankReceiptLink("l3", "u", 3, allocatedAmount = 10.0)
        )
        assertEquals(setOf(2, 3), BankTransactionClassificationPolicy.blockedReceiptIds(transactions, links))
    }

    @Test fun transfer_candidate_search_requires_opposite_amount_other_own_account_and_near_date() {
        val source = BankTransaction("s", "a", "2026-09-10", amount = -1000.0)
        val good = BankTransaction("g", "b", "2026-09-11", amount = 1000.0)
        val wrongAmount = BankTransaction("w", "b", "2026-09-11", amount = 900.0)
        val external = BankTransaction("e", "x", "2026-09-11", amount = 1000.0)
        assertEquals(listOf("g"), BankTransactionClassificationPolicy.transferCandidates(source, listOf(good, wrongAmount, external), setOf("a", "b")).map { it.transactionId })
    }
}
''')

print('Bank private/transfer Phase A patch applied')
