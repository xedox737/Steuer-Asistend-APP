from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# 1) Bank model/parser: Soll/Haben, audit updatedAt, reverse ranking hook,
#    payment-method score hook. Keep existing matcher/link engine intact.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/example/data/BankingModels.kt"
text = read(path)

text = replace_once(
    text,
    '    val noReceiptReason: String = "",\n    val importedAt: String = ""\n)',
    '    val noReceiptReason: String = "",\n    val importedAt: String = "",\n    val updatedAt: String = ""\n)',
    "BankTransaction.updatedAt"
)
text = replace_once(
    text,
    '    @Query("UPDATE bank_transactions SET reconciliationStatus = :status, noReceiptReason = :reason WHERE transactionId = :transactionId")\n    suspend fun updateTransactionStatus(transactionId: String, status: String, reason: String = "")',
    '    @Query("UPDATE bank_transactions SET reconciliationStatus = :status, noReceiptReason = :reason, updatedAt = :updatedAt WHERE transactionId = :transactionId")\n    suspend fun updateTransactionStatus(transactionId: String, status: String, reason: String = "", updatedAt: String)',
    "BankDao.updateTransactionStatus audit"
)
text = replace_once(
    text,
    '    @Query("UPDATE bank_transactions SET reconciliationStatus = \'OPEN\', noReceiptReason = \'\' WHERE reconciliationStatus IN (\'MATCHED\',\'PARTIAL\')")\n    suspend fun reopenLinkedTransactions()',
    '    @Query("UPDATE bank_transactions SET reconciliationStatus = \'OPEN\', noReceiptReason = \'\', updatedAt = :updatedAt WHERE reconciliationStatus IN (\'MATCHED\',\'PARTIAL\')")\n    suspend fun reopenLinkedTransactions(updatedAt: String)',
    "BankDao.reopenLinkedTransactions audit"
)
text = replace_once(
    text,
    '    private val amountAliases = listOf("betrag", "umsatz", "amount")\n',
    '    private val amountAliases = listOf("betrag", "umsatz", "amount")\n    private val debitAliases = listOf("soll", "belastung", "debit")\n    private val creditAliases = listOf("haben", "gutschrift", "credit")\n',
    "Soll/Haben aliases"
)
text = replace_once(
    text,
    '        val amountIdx = indexOf(amountAliases)\n        val currencyIdx = indexOf(currencyAliases)',
    '        val amountIdx = indexOf(amountAliases)\n        val debitIdx = indexOf(debitAliases)\n        val creditIdx = indexOf(creditAliases)\n        val currencyIdx = indexOf(currencyAliases)',
    "Soll/Haben indices"
)
text = replace_once(
    text,
    '        require(amountIdx >= 0) { "CSV-Spalte für Betrag wurde nicht erkannt." }',
    '        require(amountIdx >= 0 || debitIdx >= 0 || creditIdx >= 0) { "CSV-Spalte für Betrag bzw. Soll/Haben wurde nicht erkannt." }',
    "Soll/Haben required columns"
)
text = replace_once(
    text,
    '            val rawAmount = row.getOrNull(amountIdx).orEmpty()\n            val amount = parseAmount(rawAmount)\n            val bookingDate = normalizeDate(row.getOrNull(bookingIdx).orEmpty())',
    '            val amount = parseCsvAmount(row, amountIdx, debitIdx, creditIdx)\n            val bookingDate = normalizeDate(row.getOrNull(bookingIdx).orEmpty())',
    "Soll/Haben row parsing"
)
text = replace_once(
    text,
    '                importedAt = importedAt\n            )',
    '                importedAt = importedAt,\n                updatedAt = importedAt\n            )',
    "CSV transaction audit timestamp"
)
# The CAMT constructor has the same importedAt tail; patch its next remaining occurrence.
text = replace_once(
    text,
    '                        importedAt = importedAt\n                    )',
    '                        importedAt = importedAt,\n                        updatedAt = importedAt\n                    )',
    "CAMT transaction audit timestamp"
)
text = replace_once(
    text,
    '    private fun detectDelimiter(line: String): Char =\n',
    '''    private fun parseCsvAmount(row: List<String>, amountIdx: Int, debitIdx: Int, creditIdx: Int): Double? {
        // A populated normal amount column remains authoritative. Soll/Haben is only
        // used when no normal amount is present on that row, preventing double booking.
        if (amountIdx >= 0) {
            val rawAmount = row.getOrNull(amountIdx).orEmpty().trim()
            if (rawAmount.isNotBlank()) return parseAmount(rawAmount)
        }
        val debit = if (debitIdx >= 0) parseAmount(row.getOrNull(debitIdx).orEmpty()) else null
        val credit = if (creditIdx >= 0) parseAmount(row.getOrNull(creditIdx).orEmpty()) else null
        return when {
            debit != null && credit == null -> -kotlin.math.abs(debit)
            credit != null && debit == null -> kotlin.math.abs(credit)
            debit == null && credit == null -> null
            else -> null // ambiguous row: never create two or guessed bookings
        }
    }

    private fun detectDelimiter(line: String): Char =
''',
    "Soll/Haben helper"
)
text = replace_once(
    text,
    '        val confidence = when {\n            score >= 85 -> "HOCH"',
    '        val paymentAdjustment = BankPaymentMatchScore.adjustment(transaction, receipt)\n        score += paymentAdjustment.points\n        reasons += paymentAdjustment.reasons\n        val confidence = when {\n            score >= 85 -> "HOCH"',
    "payment score hook"
)
text = replace_once(
    text,
    '        return BankMatchSuggestion(transaction.transactionId, receipt.id, score.coerceAtMost(100), confidence, reasons)\n',
    '        return BankMatchSuggestion(transaction.transactionId, receipt.id, score.coerceIn(0, 100), confidence, reasons)\n',
    "bounded score"
)
text = replace_once(
    text,
    '    fun rankReceipts(transaction: BankTransaction, receipts: List<Receipt>, links: List<BankReceiptLink>): List<BankMatchSuggestion> =\n        receipts.asSequence()\n            .filter { receiptDirectionMatches(transaction, it) }\n            .map { score(transaction, it) }\n            .sortedByDescending { it.score }\n            .take(30)\n            .toList()\n\n    fun score',
    '''    fun rankReceipts(transaction: BankTransaction, receipts: List<Receipt>, links: List<BankReceiptLink>): List<BankMatchSuggestion> =
        receipts.asSequence()
            .filter { receiptDirectionMatches(transaction, it) }
            .filter { BankLinkPolicy.propose(transaction, it, links).allowed }
            .map { score(transaction, it) }
            .sortedByDescending { it.score }
            .take(30)
            .toList()

    fun rankTransactionsForReceipt(
        receipt: Receipt,
        transactions: List<BankTransaction>,
        links: List<BankReceiptLink>
    ): List<BankMatchSuggestion> = transactions.asSequence()
        .filter { it.reconciliationStatus != BankReconciliationStatus.NO_RECEIPT_REQUIRED }
        .filter { receiptDirectionMatches(it, receipt) }
        .filter { BankLinkPolicy.propose(it, receipt, links).allowed }
        .map { score(it, receipt) }
        .sortedWith(compareByDescending<BankMatchSuggestion> { it.score }.thenBy { it.transactionId })
        .take(50)
        .toList()

    fun score''',
    "reverse transaction ranking"
)
write(path, text)


# ---------------------------------------------------------------------------
# 2) New focused policies: payment-method score + loan suggestions.
# ---------------------------------------------------------------------------
write("app/src/main/java/com/example/data/BankPhase1FinalPolicies.kt", r'''package com.example.data

import kotlin.math.abs

/** Final Phase-1 scoring additions. They only produce deterministic suggestions. */
data class PaymentMatchAdjustment(val points: Int, val reasons: List<String>)

object BankPaymentMatchScore {
    fun adjustment(transaction: BankTransaction, receipt: Receipt): PaymentMatchAdjustment {
        val method = normalize(receipt.zahlungsart)
        val bankText = normalize("${transaction.purpose} ${transaction.counterparty} ${transaction.bankReference}")
        return when {
            method == "bar" -> PaymentMatchAdjustment(-35, listOf("Zahlungsart Bar spricht gegen Bankmatch"))
            method == "ueberweisung" -> {
                if ("lastschrift" in bankText) PaymentMatchAdjustment(-8, listOf("Banktext spricht eher für Lastschrift"))
                else PaymentMatchAdjustment(8, listOf("Zahlungsart Überweisung passt zum Bankmatch"))
            }
            method == "lastschrift" && ("lastschrift" in bankText || "sepa" in bankText) ->
                PaymentMatchAdjustment(10, listOf("Zahlungsart Lastschrift passt zum Banktext"))
            (method == "karte" || method.contains("girocard") || method.contains("kreditkarte")) &&
                listOf("karte", "card", "girocard", "visa", "mastercard").any { it in bankText } ->
                PaymentMatchAdjustment(6, listOf("Kartenzahlung passt zum Banktext"))
            else -> PaymentMatchAdjustment(0, emptyList())
        }
    }

    private fun normalize(value: String): String = value.lowercase(java.util.Locale.GERMANY)
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace(Regex("[^a-z0-9]+"), " ").trim()
}

data class BankLoanSuggestion(
    val loanId: Int,
    val score: Int,
    val confidence: String,
    val reasons: List<String>
)

object BankLoanMatcher {
    fun suggestions(
        transaction: BankTransaction,
        loans: List<Loan>,
        account: BankAccount? = null,
        history: List<BankTransaction> = emptyList()
    ): List<BankLoanSuggestion> = loans.asSequence()
        .filter { it.aktiv && it.monatlicheRate > 0.0 }
        .map { score(transaction, it, account, history) }
        .filter { it.score >= 55 }
        .sortedWith(compareByDescending<BankLoanSuggestion> { it.score }.thenBy { it.loanId })
        .toList()

    fun score(
        transaction: BankTransaction,
        loan: Loan,
        account: BankAccount? = null,
        history: List<BankTransaction> = emptyList()
    ): BankLoanSuggestion {
        var score = 0
        val reasons = mutableListOf<String>()
        val actual = transaction.absoluteAmount
        val expected = abs(loan.monatlicheRate)
        val diff = abs(actual - expected)
        when {
            diff <= 0.01 -> { score += 50; reasons += "Monatsrate stimmt exakt" }
            diff <= 5.0 -> { score += 35; reasons += "Monatsrate nahezu gleich" }
            expected > 0.0 && diff / expected <= 0.05 -> { score += 20; reasons += "Monatsrate ähnlich" }
        }

        val loanBank = normalize(loan.bank)
        val accountBank = normalize(account?.bankName.orEmpty())
        val bankText = normalize("${transaction.counterparty} ${transaction.purpose} ${transaction.bankReference}")
        if (loanBank.isNotBlank() && accountBank.isNotBlank()) {
            if (tokenSimilarity(loanBank, accountBank) >= 0.5) {
                score += 20; reasons += "Bankname passt"
            } else {
                score -= 35; reasons += "Bankname weicht ab"
            }
        } else if (loanBank.isNotBlank() && loanBank.split(' ').filter { it.length >= 4 }.any { it in bankText }) {
            score += 15; reasons += "Bank im Zahlungstext"
        }

        val loanText = normalize("${loan.bezeichnung} ${loan.bank} ${loan.notiz}")
        val loanTokens = loanText.split(' ').filter { it.length >= 4 }.toSet()
        val txTokens = bankText.split(' ').filter { it.length >= 4 }.toSet()
        val overlap = loanTokens.intersect(txTokens).size
        if (overlap >= 2) { score += 15; reasons += "Darlehensreferenz im Zahlungstext" }
        else if (overlap == 1) { score += 8; reasons += "Zahlungstext passt zum Darlehen" }

        if (transaction.propertyId.isNotBlank() && loan.propertyId.isNotBlank()) {
            if (transaction.propertyId == loan.propertyId) {
                score += 10; reasons += "Immobilie passt"
            } else {
                score -= 20; reasons += "Immobilie weicht ab"
            }
        }

        val recurring = history.count {
            it.accountId == transaction.accountId && abs(it.absoluteAmount - actual) <= 0.01
        }
        if (recurring >= 2) { score += 10; reasons += "Wiederkehrender Betrag" }

        val bounded = score.coerceIn(0, 100)
        val confidence = when {
            bounded >= 85 -> "HOCH"
            bounded >= 65 -> "MITTEL"
            else -> "NIEDRIG"
        }
        return BankLoanSuggestion(loan.id, bounded, confidence, reasons)
    }

    private fun tokenSimilarity(a: String, b: String): Double {
        val left = normalize(a).split(' ').filter { it.length >= 3 }.toSet()
        val right = normalize(b).split(' ').filter { it.length >= 3 }.toSet()
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return left.intersect(right).size.toDouble() / minOf(left.size, right.size).toDouble()
    }

    private fun normalize(value: String): String = value.lowercase(java.util.Locale.GERMANY)
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace(Regex("[^a-z0-9]+"), " ").trim()
}
''')


# ---------------------------------------------------------------------------
# 3) Room 24: one additive audit column, no destructive migration.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/example/data/ReceiptDatabase.kt"
text = read(path)
text = replace_once(
    text,
    '''val MIGRATION_22_23 = object : androidx.room.migration.Migration(22, 23) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bank_accounts ADD COLUMN accountHolder TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE bank_transactions ADD COLUMN propertyId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE bank_transactions ADD COLUMN unitId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE bank_transactions ADD COLUMN importFileName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE bank_transactions ADD COLUMN importRunId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE bank_receipt_links ADD COLUMN source TEXT NOT NULL DEFAULT 'NUTZER_BESTAETIGT'")
    }
}

@Database''',
    '''val MIGRATION_22_23 = object : androidx.room.migration.Migration(22, 23) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bank_accounts ADD COLUMN accountHolder TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE bank_transactions ADD COLUMN propertyId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE bank_transactions ADD COLUMN unitId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE bank_transactions ADD COLUMN importFileName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE bank_transactions ADD COLUMN importRunId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE bank_receipt_links ADD COLUMN source TEXT NOT NULL DEFAULT 'NUTZER_BESTAETIGT'")
    }
}

val MIGRATION_23_24 = object : androidx.room.migration.Migration(23, 24) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bank_transactions ADD COLUMN updatedAt TEXT NOT NULL DEFAULT ''")
    }
}

@Database''',
    "Room 23->24 migration"
)
text = replace_once(text, '], version = 23, exportSchema = false)', '], version = 24, exportSchema = false)', "Room version 24")
text = replace_once(
    text,
    'MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23)',
    'MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24)',
    "register migration 23->24"
)
write(path, text)


# ---------------------------------------------------------------------------
# 4) Supplemental backup schema 7 with audit timestamp; old schemas still use
#    optString defaults, so 5/6 remain readable.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/example/data/SupplementalDriveBackup.kt"
text = read(path)
text = replace_once(text, 'internal const val SCHEMA_VERSION = 6', 'internal const val SCHEMA_VERSION = 7', "backup schema 7")
text = replace_once(
    text,
    '        put("reconciliationStatus", reconciliationStatus); put("noReceiptReason", noReceiptReason)\n        put("importedAt", importedAt)',
    '        put("reconciliationStatus", reconciliationStatus); put("noReceiptReason", noReceiptReason)\n        put("importedAt", importedAt); put("updatedAt", updatedAt)',
    "backup transaction updatedAt"
)
text = replace_once(
    text,
    '        noReceiptReason = optString("noReceiptReason", ""), importedAt = optString("importedAt", "")\n    )',
    '        noReceiptReason = optString("noReceiptReason", ""), importedAt = optString("importedAt", ""),\n        updatedAt = optString("updatedAt", "")\n    )',
    "restore transaction updatedAt"
)
write(path, text)


# ---------------------------------------------------------------------------
# 5) ViewModel: all bank status mutations timestamped + explicit reopen action.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/example/ui/ReceiptViewModel.kt"
text = read(path)
text = replace_once(
    text,
    '        dao.updateTransactionStatus(transactionId, status, "")',
    '        dao.updateTransactionStatus(transactionId, status, "", java.time.Instant.now().toString())',
    "refresh status audit"
)
text = replace_once(
    text,
    '''            dao.updateTransactionStatus(
                transactionId,
                com.example.data.BankReconciliationStatus.NO_RECEIPT_REQUIRED,
                reason.trim()
            )''',
    '''            dao.updateTransactionStatus(
                transactionId,
                com.example.data.BankReconciliationStatus.NO_RECEIPT_REQUIRED,
                reason.trim(),
                java.time.Instant.now().toString()
            )''',
    "no receipt audit"
)
text = replace_once(
    text,
    '    fun removeBankReceiptLink(linkId: String, transactionId: String) {',
    '''    fun reopenBankTransaction(transactionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = database.bankDao()
            if (dao.getLinksForTransaction(transactionId).isNotEmpty()) {
                refreshBankTransactionStatus(transactionId)
            } else {
                dao.updateTransactionStatus(
                    transactionId,
                    com.example.data.BankReconciliationStatus.OPEN,
                    "",
                    java.time.Instant.now().toString()
                )
            }
            _bankImportStatus.value = "Buchung wurde wieder zur Prüfung geöffnet."
        }
    }

    fun removeBankReceiptLink(linkId: String, transactionId: String) {''',
    "reopen bank action"
)
text = replace_once(
    text,
    '            database.bankDao().reopenLinkedTransactions()',
    '            database.bankDao().reopenLinkedTransactions(java.time.Instant.now().toString())',
    "reset status audit"
)
write(path, text)


# ---------------------------------------------------------------------------
# 6) Complete Bank UI: loan suggestions, reverse workflow including existing
#    links, alternative transaction chooser, individual unlink, explicit reopen.
# ---------------------------------------------------------------------------
path = "app/src/main/java/com/example/ui/BankFeature.kt"
text = read(path)
text = replace_once(
    text,
    'import com.example.data.BankMatchSuggestion\n',
    'import com.example.data.BankLoanMatcher\nimport com.example.data.BankLoanSuggestion\nimport com.example.data.BankMatchSuggestion\n',
    "BankFeature policy imports"
)
text = replace_once(
    text,
    '    val property by viewModel.propertyMetadata.collectAsState()\n',
    '    val property by viewModel.propertyMetadata.collectAsState()\n    val loans by viewModel.loans.collectAsState()\n',
    "BankFeature loans state"
)
text = replace_once(
    text,
    '    var receiptPickerFor by remember { mutableStateOf<BankTransaction?>(null) }\n    var noReceiptFor by remember { mutableStateOf<BankTransaction?>(null) }',
    '    var receiptPickerFor by remember { mutableStateOf<BankTransaction?>(null) }\n    var bankPickerForReceipt by remember { mutableStateOf<Receipt?>(null) }\n    var noReceiptFor by remember { mutableStateOf<BankTransaction?>(null) }',
    "reverse picker state"
)
text = replace_once(
    text,
    '''    val propertyId = property?.propertyId ?: StableDocumentIdentity.LEGACY_PROPERTY_ID
    val rentHints = remember(filteredTransactions, receipts, units, propertyId) {''',
    '''    val propertyId = property?.propertyId ?: StableDocumentIdentity.LEGACY_PROPERTY_ID
    val accountsById = remember(accounts) { accounts.associateBy { it.accountId } }
    val loanSuggestions = remember(filteredTransactions, loans, accounts) {
        filteredTransactions.mapNotNull { tx ->
            BankLoanMatcher.suggestions(tx, loans, accountsById[tx.accountId], filteredTransactions)
                .takeIf { it.isNotEmpty() }?.let { tx.transactionId to it }
        }.toMap()
    }
    val rentHints = remember(filteredTransactions, receipts, units, propertyId) {''',
    "loan suggestion map"
)
text = replace_once(
    text,
    '''                    rentHint = rentHints[transaction.transactionId],
                    onConfirmSuggestion = { suggestion ->''',
    '''                    rentHint = rentHints[transaction.transactionId],
                    loanSuggestions = loanSuggestions[transaction.transactionId].orEmpty(),
                    loans = loans,
                    onConfirmSuggestion = { suggestion ->''',
    "transaction card loan args"
)
text = replace_once(
    text,
    '''                    onNoReceipt = { noReceiptFor = transaction },
                    onUnlink = { link -> viewModel.removeBankReceiptLink(link.linkId, transaction.transactionId) }
                )''',
    '''                    onNoReceipt = { noReceiptFor = transaction },
                    onReopen = { viewModel.reopenBankTransaction(transaction.transactionId) },
                    onUnlink = { link -> viewModel.removeBankReceiptLink(link.linkId, transaction.transactionId) }
                )''',
    "transaction card reopen args"
)
old_reverse = '''        val unlinkedReceipts = receipts.filter { receipt ->
            links.none { it.receiptId == receipt.id || (receipt.internalId.isNotBlank() && it.receiptInternalId == receipt.internalId) }
        }
        if (filteredTransactions.isNotEmpty() && unlinkedReceipts.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Belege ohne Bankzuordnung", fontWeight = FontWeight.Bold, color = DarkNavy)
                Text("Auch andersherum: vom Beleg zur passenden Buchung.", fontSize = 12.sp, color = SlateGray)
            }
            items(unlinkedReceipts.take(20), key = { "receipt-${it.id}" }) { receipt ->
                val reverse = BankReceiptMatcher.bestForReceipt(receipt, filteredTransactions, links)
                ReverseReceiptCard(
                    receipt = receipt,
                    suggestion = reverse,
                    transaction = reverse?.transactionId?.let { txId -> filteredTransactions.firstOrNull { it.transactionId == txId } },
                    onConfirm = { txId -> viewModel.confirmBankReceiptLink(txId, receipt.id) }
                )
            }
        }
'''
new_reverse = '''        if (filteredTransactions.isNotEmpty() && receipts.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Bankabgleich aus Belegen", fontWeight = FontWeight.Bold, color = DarkNavy)
                Text("Beste Buchung bestätigen, eine andere offene Buchung auswählen oder einzelne Zuordnungen lösen.", fontSize = 12.sp, color = SlateGray)
            }
            items(receipts.take(30), key = { "receipt-${it.id}" }) { receipt ->
                val receiptLinks = links.filter {
                    it.receiptId == receipt.id || (receipt.internalId.isNotBlank() && it.receiptInternalId == receipt.internalId)
                }
                val reverse = BankReceiptMatcher.bestForReceipt(receipt, filteredTransactions, links)
                ReverseReceiptCard(
                    receipt = receipt,
                    suggestion = reverse,
                    transaction = reverse?.transactionId?.let { txId -> filteredTransactions.firstOrNull { it.transactionId == txId } },
                    linkedLinks = receiptLinks,
                    transactions = filteredTransactions,
                    onConfirm = { txId -> viewModel.confirmBankReceiptLink(txId, receipt.id) },
                    onChooseOther = { bankPickerForReceipt = receipt },
                    onUnlink = { link -> viewModel.removeBankReceiptLink(link.linkId, link.transactionId) }
                )
            }
        }
'''
text = replace_once(text, old_reverse, new_reverse, "complete reverse workflow")
text = replace_once(
    text,
    '''    noReceiptFor?.let { transaction ->
        NoReceiptReasonDialog(''',
    '''    bankPickerForReceipt?.let { receipt ->
        BankTransactionPickerDialog(
            receipt = receipt,
            transactions = filteredTransactions,
            links = links,
            onDismiss = { bankPickerForReceipt = null },
            onSelect = { transaction ->
                viewModel.confirmBankReceiptLink(transaction.transactionId, receipt.id)
                bankPickerForReceipt = null
            }
        )
    }

    noReceiptFor?.let { transaction ->
        NoReceiptReasonDialog(''',
    "reverse transaction dialog invocation"
)
text = replace_once(
    text,
    '''    receipts: List<Receipt>,
    rentHint: BankRentHint?,
    onConfirmSuggestion: (BankMatchSuggestion) -> Unit,''',
    '''    receipts: List<Receipt>,
    rentHint: BankRentHint?,
    loanSuggestions: List<BankLoanSuggestion>,
    loans: List<com.example.data.Loan>,
    onConfirmSuggestion: (BankMatchSuggestion) -> Unit,''',
    "BankTransactionCard signature loans"
)
text = replace_once(
    text,
    '''    onPickReceipt: () -> Unit,
    onNoReceipt: () -> Unit,
    onUnlink: (BankReceiptLink) -> Unit''',
    '''    onPickReceipt: () -> Unit,
    onNoReceipt: () -> Unit,
    onReopen: () -> Unit,
    onUnlink: (BankReceiptLink) -> Unit''',
    "BankTransactionCard signature reopen"
)
text = replace_once(
    text,
    '''            if (transaction.reconciliationStatus !in setOf(BankReconciliationStatus.MATCHED, BankReconciliationStatus.NO_RECEIPT_REQUIRED)) {
                if (suggestion != null && receipt != null) {''',
    '''            if (transaction.reconciliationStatus !in setOf(BankReconciliationStatus.MATCHED, BankReconciliationStatus.NO_RECEIPT_REQUIRED)) {
                if (loanSuggestions.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Mögliche Darlehensrate", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkNavy)
                    loanSuggestions.take(3).forEach { loanSuggestion ->
                        val loan = loans.firstOrNull { it.id == loanSuggestion.loanId }
                        if (loan != null) {
                            Text(
                                "${loan.bezeichnung.ifBlank { "Darlehen ${loan.id}" }} • ${loan.bank.ifBlank { "Bank nicht hinterlegt" }} • ${loanSuggestion.score}% ${loanSuggestion.confidence}",
                                fontSize = 12.sp, color = DarkNavy
                            )
                            Text(
                                "Soll ${NumberFormatter.format(loan.monatlicheRate)} • Ist ${NumberFormatter.format(transaction.absoluteAmount)}" +
                                    loan.propertyId.takeIf { it.isNotBlank() }?.let { " • Objekt $it" }.orEmpty(),
                                fontSize = 11.sp, color = SlateGray
                            )
                        }
                    }
                    if (loanSuggestions.size > 1) {
                        Text("Mehrere Darlehen passen – bitte selbst auswählen/prüfen. Keine automatische Verbuchung.", fontSize = 11.sp, color = SlateGray)
                    }
                }
                if (suggestion != null && receipt != null) {''',
    "loan suggestion card"
)
text = replace_once(
    text,
    '''            } else {
                Text(transaction.noReceiptReason, fontSize = 12.sp, color = SlateGray)
            }
''',
    '''            } else {
                Text(transaction.noReceiptReason, fontSize = 12.sp, color = SlateGray)
                OutlinedButton(onClick = onReopen, modifier = Modifier.fillMaxWidth()) {
                    Text("Zustand wieder öffnen")
                }
            }
''',
    "reopen UI"
)
old_card = '''@Composable
private fun ReverseReceiptCard(
    receipt: Receipt,
    suggestion: BankMatchSuggestion?,
    transaction: BankTransaction?,
    onConfirm: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(receipt.aussteller, fontWeight = FontWeight.SemiBold, color = DarkNavy)
                    Text(receipt.datum, fontSize = 11.sp, color = SlateGray)
                }
                Text(NumberFormatter.format(receipt.bruttobetrag), fontWeight = FontWeight.SemiBold)
            }
            if (suggestion != null && transaction != null) {
                Text(
                    "Passende Buchung: ${transaction.counterparty.ifBlank { transaction.purpose }} • ${suggestion.score}%",
                    fontSize = 12.sp,
                    color = SlateGray
                )
                OutlinedButton(onClick = { onConfirm(transaction.transactionId) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Buchung zuordnen")
                }
            } else {
                Text("Noch keine passende Buchung gefunden.", fontSize = 11.sp, color = SlateGray)
            }
        }
    }
}
'''
new_card = '''@Composable
private fun ReverseReceiptCard(
    receipt: Receipt,
    suggestion: BankMatchSuggestion?,
    transaction: BankTransaction?,
    linkedLinks: List<BankReceiptLink>,
    transactions: List<BankTransaction>,
    onConfirm: (String) -> Unit,
    onChooseOther: () -> Unit,
    onUnlink: (BankReceiptLink) -> Unit
) {
    val allocated = linkedLinks.sumOf { it.allocatedAmount }
    val remaining = (receipt.bruttobetrag - allocated).coerceAtLeast(0.0)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Bankabgleich", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(receipt.aussteller, fontWeight = FontWeight.SemiBold, color = DarkNavy)
                    Text(receipt.datum, fontSize = 11.sp, color = SlateGray)
                }
                Text(NumberFormatter.format(receipt.bruttobetrag), fontWeight = FontWeight.SemiBold)
            }
            if (linkedLinks.isNotEmpty()) {
                Text("Bereits mit Bankbuchung verbunden", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkNavy)
                linkedLinks.forEach { link ->
                    val linkedTransaction = transactions.firstOrNull { it.transactionId == link.transactionId }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(linkedTransaction?.counterparty?.ifBlank { linkedTransaction.purpose } ?: link.transactionId, fontSize = 12.sp)
                            if (linkedTransaction != null) {
                                Text("${linkedTransaction.bookingDate} • ${NumberFormatter.format(linkedTransaction.amount)} • ${NumberFormatter.format(link.allocatedAmount)} zugeordnet", fontSize = 11.sp, color = SlateGray)
                            }
                        }
                        TextButton(onClick = { onUnlink(link) }) { Text("Zuordnung lösen") }
                    }
                }
            }
            if (remaining > 0.009) {
                if (suggestion != null && transaction != null) {
                    Text("Passende Bankbuchung gefunden", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkNavy)
                    Text(
                        "${transaction.bookingDate} • ${transaction.counterparty.ifBlank { transaction.purpose }} • ${NumberFormatter.format(transaction.amount)} • Score ${suggestion.score}%",
                        fontSize = 12.sp, color = SlateGray
                    )
                    OutlinedButton(onClick = { onConfirm(transaction.transactionId) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Treffer bestätigen")
                    }
                } else {
                    Text("Keine passende Bankbuchung gefunden", fontSize = 12.sp, color = SlateGray)
                }
                OutlinedButton(onClick = onChooseOther, modifier = Modifier.fillMaxWidth()) {
                    Text("Andere Buchung auswählen")
                }
            }
        }
    }
}
'''
text = replace_once(text, old_card, new_card, "ReverseReceiptCard complete workflow")
text = replace_once(
    text,
    '''@Composable
private fun NoReceiptReasonDialog''',
    '''@Composable
private fun BankTransactionPickerDialog(
    receipt: Receipt,
    transactions: List<BankTransaction>,
    links: List<BankReceiptLink>,
    onDismiss: () -> Unit,
    onSelect: (BankTransaction) -> Unit
) {
    val ranked = remember(receipt.id, transactions, links) {
        BankReceiptMatcher.rankTransactionsForReceipt(receipt, transactions, links)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Andere Buchung auswählen") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ranked.isEmpty()) {
                    item { Text("Keine offene Bankbuchung ist für diesen Beleg verfügbar.") }
                } else {
                    items(ranked, key = { it.transactionId }) { suggestion ->
                        val transaction = transactions.firstOrNull { it.transactionId == suggestion.transactionId }
                        if (transaction != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, BorderColor),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(transaction.counterparty.ifBlank { transaction.purpose.ifBlank { "Bankbuchung" } }, fontWeight = FontWeight.SemiBold)
                                    Text("${transaction.bookingDate} • ${NumberFormatter.format(transaction.amount)} • Score ${suggestion.score}%", fontSize = 12.sp)
                                    if (suggestion.reasons.isNotEmpty()) Text(suggestion.reasons.take(3).joinToString(" • "), fontSize = 11.sp, color = SlateGray)
                                    TextButton(onClick = { onSelect(transaction) }) { Text("Zuordnen") }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}

@Composable
private fun NoReceiptReasonDialog''',
    "reverse picker dialog"
)
write(path, text)


# ---------------------------------------------------------------------------
# 7) Existing backup test expects current schema. Keep it aligned.
# ---------------------------------------------------------------------------
path = "app/src/test/java/com/example/data/SupplementalDriveBackupTest.kt"
text = read(path)
text = replace_once(text, '        assertEquals(6, payload.getInt("schemaVersion"))', '        assertEquals(7, payload.getInt("schemaVersion"))', "backup schema test")
write(path, text)


# ---------------------------------------------------------------------------
# 8) Comprehensive deterministic acceptance tests for the new Phase-1 items.
# ---------------------------------------------------------------------------
write("app/src/test/java/com/example/data/BankPhase1FinalAcceptanceTest.kt", r'''package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankPhase1FinalAcceptanceTest {
    @Test fun csvSollOnlyIsNegative() {
        val tx = BankImportParser.parseCsv("Buchungstag;Soll\n04.09.2026;84,50").transactions.single()
        assertEquals(-84.50, tx.amount, 0.001)
    }

    @Test fun csvHabenOnlyIsPositive() {
        val tx = BankImportParser.parseCsv("Buchungstag;Haben\n05.09.2026;950,00").transactions.single()
        assertEquals(950.0, tx.amount, 0.001)
    }

    @Test fun csvSollAndHabenInSameFileSupportEmptyCellsAndGermanDecimals() {
        val csv = "Buchungstag;Belastung;Gutschrift\n04.09.2026;1.234,56;\n05.09.2026;;950,00"
        val tx = BankImportParser.parseCsv(csv).transactions
        assertEquals(-1234.56, tx[0].amount, 0.001)
        assertEquals(950.0, tx[1].amount, 0.001)
    }

    @Test fun normalAmountColumnWinsWithoutDoubleBooking() {
        val csv = "Buchungstag;Betrag;Soll;Haben\n04.09.2026;-84,50;999,00;"
        val tx = BankImportParser.parseCsv(csv).transactions
        assertEquals(1, tx.size)
        assertEquals(-84.50, tx.single().amount, 0.001)
    }

    @Test fun malformedSollHabenRowIsSkipped() {
        val csv = "Buchungstag;Debit;Credit\n04.09.2026;abc;\n05.09.2026;;10,00"
        val batch = BankImportParser.parseCsv(csv)
        assertEquals(1, batch.transactions.size)
        assertEquals(1, batch.errorRows)
    }

    @Test fun ambiguousSollAndHabenOnOneRowIsSkippedRatherThanDuplicated() {
        val csv = "Buchungstag;Soll;Haben\n04.09.2026;10,00;10,00\n05.09.2026;;5,00"
        val batch = BankImportParser.parseCsv(csv)
        assertEquals(1, batch.transactions.size)
        assertEquals(1, batch.errorRows)
    }

    @Test fun identicalRowsRemainTwoRealOccurrencesButAreStableAcrossReimport() {
        val csv = "Buchungstag;Auftraggeber;Verwendungszweck;Betrag;Referenz\n04.09.2026;A;X;-10,00;R\n04.09.2026;A;X;-10,00;R"
        val first = BankImportParser.parseCsv(csv).transactions
        val second = BankImportParser.parseCsv(csv).transactions
        assertEquals(2, first.size)
        assertNotEquals(first[0].transactionId, first[1].transactionId)
        assertEquals(first.map { it.transactionId }, second.map { it.transactionId })
    }

    @Test fun sameCounterpartyDateAmountButDifferentPurposeRemainDistinct() {
        val csv = "Buchungstag;Auftraggeber;Verwendungszweck;Betrag\n04.09.2026;A;Zweck 1;-10,00\n04.09.2026;A;Zweck 2;-10,00"
        val tx = BankImportParser.parseCsv(csv).transactions
        assertNotEquals(tx[0].transactionId, tx[1].transactionId)
    }

    @Test fun paymentMethodTransferImprovesMatchingButCannotCreateMatchAlone() {
        val tx = BankTransaction("t", "a", "2026-09-04", amount = -84.5, counterparty = "Hornbach", purpose = "SEPA Überweisung Material")
        val unknown = receipt(1, 84.5, "Unbekannt")
        val transfer = unknown.copy(zahlungsart = "Überweisung")
        assertTrue(BankReceiptMatcher.score(tx, transfer).score > BankReceiptMatcher.score(tx, unknown).score)
        val unrelated = transfer.copy(id = 2, bruttobetrag = 999.0, aussteller = "Andere Firma", beschreibung = "fremd")
        assertNull(BankReceiptMatcher.bestForTransaction(tx, listOf(unrelated)))
    }

    @Test fun paymentMethodDirectDebitImprovesMatching() {
        val tx = BankTransaction("t", "a", "2026-09-04", amount = -84.5, counterparty = "Hornbach", purpose = "SEPA Lastschrift")
        val unknown = receipt(1, 84.5, "Unbekannt")
        val debit = unknown.copy(zahlungsart = "Lastschrift")
        assertTrue(BankReceiptMatcher.score(tx, debit).score > BankReceiptMatcher.score(tx, unknown).score)
    }

    @Test fun cashClearlyReducesBankMatchAndUnknownIsNeutral() {
        val tx = BankTransaction("t", "a", "2026-09-04", amount = -84.5, counterparty = "Hornbach", purpose = "Material")
        val unknown = receipt(1, 84.5, "Unbekannt")
        val cash = unknown.copy(zahlungsart = "Bar")
        assertTrue(BankReceiptMatcher.score(tx, cash).score <= BankReceiptMatcher.score(tx, unknown).score - 30)
        assertEquals(BankReceiptMatcher.score(tx, unknown).score, BankReceiptMatcher.score(tx, unknown.copy(zahlungsart = "Unbekannt")).score)
    }

    @Test fun loanExactRateAndBankProducesSuggestionForDebitAndCredit() {
        val account = BankAccount("a", "Hauskonto", bankName = "Sparkasse")
        val loan = Loan(id = 1, bezeichnung = "Sanierungskredit", bank = "Sparkasse", monatlicheRate = 445.0, propertyId = "p")
        listOf(-445.0, 445.0).forEach { amount ->
            val tx = BankTransaction("t$amount", "a", "2026-09-04", amount = amount, purpose = "Sanierungskredit Rate", propertyId = "p")
            assertEquals(1, BankLoanMatcher.suggestions(tx, listOf(loan), account, listOf(tx, tx.copy(transactionId = "old"))).single().loanId)
        }
    }

    @Test fun loanSimilarRateCanStillBeSuggested() {
        val account = BankAccount("a", "Hauskonto", bankName = "Sparkasse")
        val loan = Loan(id = 2, bezeichnung = "Kredit", bank = "Sparkasse", monatlicheRate = 445.0)
        val tx = BankTransaction("t", "a", "2026-09-04", amount = -442.0, purpose = "Kredit Rate")
        assertTrue(BankLoanMatcher.suggestions(tx, listOf(loan), account).isNotEmpty())
    }

    @Test fun wrongBankSuppressesLoanSuggestion() {
        val account = BankAccount("a", "Hauskonto", bankName = "Volksbank")
        val loan = Loan(id = 3, bezeichnung = "Kredit", bank = "Sparkasse", monatlicheRate = 445.0)
        val tx = BankTransaction("t", "a", "2026-09-04", amount = -445.0)
        assertTrue(BankLoanMatcher.suggestions(tx, listOf(loan), account).isEmpty())
    }

    @Test fun twoPossibleLoansAreBothReturnedWithoutAutomaticChoice() {
        val account = BankAccount("a", "Hauskonto", bankName = "Sparkasse")
        val loans = listOf(
            Loan(id = 1, bezeichnung = "Kredit A", bank = "Sparkasse", monatlicheRate = 445.0),
            Loan(id = 2, bezeichnung = "Kredit B", bank = "Sparkasse", monatlicheRate = 445.0)
        )
        val tx = BankTransaction("t", "a", "2026-09-04", amount = -445.0, purpose = "Kredit Rate")
        assertEquals(setOf(1, 2), BankLoanMatcher.suggestions(tx, loans, account).map { it.loanId }.toSet())
    }

    @Test fun loanNoMatchAndInactiveLoanAreNotSuggested() {
        val account = BankAccount("a", "Hauskonto", bankName = "Sparkasse")
        val tx = BankTransaction("t", "a", "2026-09-04", amount = -100.0)
        assertTrue(BankLoanMatcher.suggestions(tx, listOf(Loan(id = 1, bank = "Sparkasse", monatlicheRate = 445.0)), account).isEmpty())
        assertTrue(BankLoanMatcher.suggestions(tx.copy(amount = -445.0), listOf(Loan(id = 2, bank = "Sparkasse", monatlicheRate = 445.0, aktiv = false)), account).isEmpty())
    }

    @Test fun splitOneBankToManyMovesPartialMatchedPartialOpen() {
        val tx = BankTransaction("t", "a", "2026-09-04", amount = -150.0)
        val r100 = receipt(1, 100.0)
        val r50 = receipt(2, 50.0)
        val firstAmount = BankLinkPolicy.propose(tx, r100, emptyList()).amount
        val first = BankReceiptLink("l1", "t", 1, r100.internalId, firstAmount)
        assertEquals(BankReconciliationStatus.PARTIAL, BankLinkPolicy.statusFor(tx, listOf(first)))
        val secondAmount = BankLinkPolicy.propose(tx, r50, listOf(first)).amount
        val second = BankReceiptLink("l2", "t", 2, r50.internalId, secondAmount)
        assertEquals(BankReconciliationStatus.MATCHED, BankLinkPolicy.statusFor(tx, listOf(first, second)))
        assertEquals(BankReconciliationStatus.PARTIAL, BankLinkPolicy.statusFor(tx, listOf(first)))
        assertEquals(BankReconciliationStatus.OPEN, BankLinkPolicy.statusFor(tx, emptyList()))
    }

    @Test fun collectionManyBanksToOneCompletesAndPreventsOverAllocation() {
        val receipt = receipt(9, 200.0)
        val t1 = BankTransaction("t1", "a", "2026-09-01", amount = -100.0)
        val t2 = BankTransaction("t2", "a", "2026-09-02", amount = -100.0)
        val t3 = BankTransaction("t3", "a", "2026-09-03", amount = -100.0)
        val first = BankReceiptLink("l1", "t1", 9, receipt.internalId, BankLinkPolicy.propose(t1, receipt, emptyList()).amount)
        assertEquals(100.0, BankLinkPolicy.propose(t2, receipt, listOf(first)).amount, 0.001)
        val second = BankReceiptLink("l2", "t2", 9, receipt.internalId, 100.0)
        assertFalse(BankLinkPolicy.propose(t3, receipt, listOf(first, second)).allowed)
    }

    @Test fun reverseRankingRespectsDirectionAndExistingAllocation() {
        val receipt = receipt(5, 100.0)
        val expense = BankTransaction("expense", "a", "2026-09-04", amount = -100.0, counterparty = "Hornbach")
        val income = BankTransaction("income", "a", "2026-09-04", amount = 100.0, counterparty = "Hornbach")
        val ranked = BankReceiptMatcher.rankTransactionsForReceipt(receipt, listOf(income, expense), emptyList())
        assertEquals(listOf("expense"), ranked.map { it.transactionId })
        val fullLink = BankReceiptLink("l", "expense", receipt.id, receipt.internalId, 100.0)
        assertTrue(BankReceiptMatcher.rankTransactionsForReceipt(receipt, listOf(expense), listOf(fullLink)).isEmpty())
    }

    private fun receipt(id: Int, amount: Double, payment: String = "Unbekannt") = Receipt(
        id = id, aussteller = "Hornbach", datum = "2026-09-04", uhrzeit = "", bruttobetrag = amount,
        hauptkategorie = "Renovierung", unterkategorie = "", kontoNr = "", beschreibung = "Material",
        zahlungsart = payment, internalId = "receipt-$id"
    )
}
''')


# ---------------------------------------------------------------------------
# 9) Complete migration coverage through 24 with preservation of pre-existing
#    non-bank tables and bank rows where the starting version supports them.
# ---------------------------------------------------------------------------
write("app/src/test/java/com/example/data/BankMigration24AcceptanceTest.kt", r'''package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankMigration24AcceptanceTest {
    @Test fun migration21To22To23To24PreservesCoreTablesAndCreatesBankSchema() = withDb(21) { db ->
        createCoreSentinels(db)
        MIGRATION_21_22.migrate(db)
        MIGRATION_22_23.migrate(db)
        MIGRATION_23_24.migrate(db)
        assertCoreSentinels(db)
        assertTrue(columns(db, "bank_transactions").containsAll(setOf("propertyId", "unitId", "importFileName", "importRunId", "updatedAt")))
    }

    @Test fun migration22To23To24PreservesCoreAndBankRows() = withDb(22) { db ->
        createCoreSentinels(db)
        createBank22(db)
        db.execSQL("INSERT INTO bank_accounts VALUES ('a','Haus','Sparkasse','DE1','EUR','CSV',1,'c','u')")
        db.execSQL("INSERT INTO bank_transactions VALUES ('t','a','2026-09-04','',-84.5,'EUR','Hornbach','','Material','R','CSV','OPEN','','import')")
        db.execSQL("INSERT INTO bank_receipt_links VALUES ('l','t',1,'r-1',84.5,'CONFIRMED','created')")
        MIGRATION_22_23.migrate(db)
        MIGRATION_23_24.migrate(db)
        assertCoreSentinels(db)
        db.query("SELECT counterparty, updatedAt FROM bank_transactions WHERE transactionId='t'").use {
            assertTrue(it.moveToFirst()); assertEquals("Hornbach", it.getString(0)); assertEquals("", it.getString(1))
        }
        db.query("SELECT source FROM bank_receipt_links WHERE linkId='l'").use {
            assertTrue(it.moveToFirst()); assertEquals(BankLinkSource.NUTZER_BESTAETIGT, it.getString(0))
        }
    }

    @Test fun migration23To24IsAdditiveAndPreservesBankRows() = withDb(23) { db ->
        createBank23(db)
        db.execSQL("INSERT INTO bank_accounts VALUES ('a','Haus','Sparkasse','DE1','EUR','CSV',1,'c','u','Sergej')")
        db.execSQL("INSERT INTO bank_transactions VALUES ('t','a','2026-09-04','',-84.5,'EUR','Hornbach','','Material','R','CSV','OPEN','','import','p','u','konto.csv','run')")
        db.execSQL("INSERT INTO bank_receipt_links VALUES ('l','t',1,'r-1',84.5,'CONFIRMED','created','MANUELL')")
        MIGRATION_23_24.migrate(db)
        db.query("SELECT propertyId, importFileName, updatedAt FROM bank_transactions WHERE transactionId='t'").use {
            assertTrue(it.moveToFirst()); assertEquals("p", it.getString(0)); assertEquals("konto.csv", it.getString(1)); assertEquals("", it.getString(2))
        }
        db.query("SELECT source FROM bank_receipt_links WHERE linkId='l'").use {
            assertTrue(it.moveToFirst()); assertEquals("MANUELL", it.getString(0))
        }
    }

    @Test fun freshDatabaseIsVersion24() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build().use { database ->
            assertEquals(24, database.openHelper.writableDatabase.version)
        }
    }

    private fun withDb(version: Int, block: (SupportSQLiteDatabase) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        helper.use { block(it.writableDatabase) }
    }

    private fun createCoreSentinels(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE receipts (id INTEGER NOT NULL PRIMARY KEY, marker TEXT NOT NULL)")
        db.execSQL("CREATE TABLE property_metadata (id INTEGER NOT NULL PRIMARY KEY, marker TEXT NOT NULL)")
        db.execSQL("CREATE TABLE loans (id INTEGER NOT NULL PRIMARY KEY, marker TEXT NOT NULL)")
        db.execSQL("CREATE TABLE managed_documents (documentId TEXT NOT NULL PRIMARY KEY, marker TEXT NOT NULL)")
        db.execSQL("INSERT INTO receipts VALUES (1,'receipt')")
        db.execSQL("INSERT INTO property_metadata VALUES (1,'property')")
        db.execSQL("INSERT INTO loans VALUES (1,'loan')")
        db.execSQL("INSERT INTO managed_documents VALUES ('d','document')")
    }

    private fun assertCoreSentinels(db: SupportSQLiteDatabase) {
        listOf("receipts" to "receipt", "property_metadata" to "property", "loans" to "loan", "managed_documents" to "document").forEach { (table, expected) ->
            db.query("SELECT marker FROM $table LIMIT 1").use { assertTrue(it.moveToFirst()); assertEquals(expected, it.getString(0)) }
        }
    }

    private fun createBank22(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE bank_accounts (accountId TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, bankName TEXT NOT NULL, iban TEXT NOT NULL, currency TEXT NOT NULL, source TEXT NOT NULL, active INTEGER NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL)")
        db.execSQL("CREATE TABLE bank_transactions (transactionId TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, bookingDate TEXT NOT NULL, valueDate TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, counterparty TEXT NOT NULL, counterpartyIban TEXT NOT NULL, purpose TEXT NOT NULL, bankReference TEXT NOT NULL, source TEXT NOT NULL, reconciliationStatus TEXT NOT NULL, noReceiptReason TEXT NOT NULL, importedAt TEXT NOT NULL)")
        db.execSQL("CREATE TABLE bank_receipt_links (linkId TEXT NOT NULL PRIMARY KEY, transactionId TEXT NOT NULL, receiptId INTEGER NOT NULL, receiptInternalId TEXT NOT NULL, allocatedAmount REAL NOT NULL, status TEXT NOT NULL, createdAt TEXT NOT NULL)")
    }

    private fun createBank23(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE bank_accounts (accountId TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, bankName TEXT NOT NULL, iban TEXT NOT NULL, currency TEXT NOT NULL, source TEXT NOT NULL, active INTEGER NOT NULL, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, accountHolder TEXT NOT NULL)")
        db.execSQL("CREATE TABLE bank_transactions (transactionId TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, bookingDate TEXT NOT NULL, valueDate TEXT NOT NULL, amount REAL NOT NULL, currency TEXT NOT NULL, counterparty TEXT NOT NULL, counterpartyIban TEXT NOT NULL, purpose TEXT NOT NULL, bankReference TEXT NOT NULL, source TEXT NOT NULL, reconciliationStatus TEXT NOT NULL, noReceiptReason TEXT NOT NULL, importedAt TEXT NOT NULL, propertyId TEXT NOT NULL, unitId TEXT NOT NULL, importFileName TEXT NOT NULL, importRunId TEXT NOT NULL)")
        db.execSQL("CREATE TABLE bank_receipt_links (linkId TEXT NOT NULL PRIMARY KEY, transactionId TEXT NOT NULL, receiptId INTEGER NOT NULL, receiptInternalId TEXT NOT NULL, allocatedAmount REAL NOT NULL, status TEXT NOT NULL, createdAt TEXT NOT NULL, source TEXT NOT NULL)")
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> {
        val result = mutableSetOf<String>()
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) result += cursor.getString(nameIndex)
        }
        return result
    }
}
''')


# ---------------------------------------------------------------------------
# 10) Backup compatibility / no-receipt audit acceptance tests.
# ---------------------------------------------------------------------------
write("app/src/test/java/com/example/data/BankBackup7AcceptanceTest.kt", r'''package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankBackup7AcceptanceTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun close() = database.close()

    @Test fun schema7PreservesAuditNoReceiptReasonAndIsIdempotent() = runTest {
        database.bankDao().upsertAccount(BankAccount("a", "Haus", bankName = "Sparkasse", accountHolder = "Sergej"))
        database.bankDao().upsertTransaction(BankTransaction(
            "t", "a", "2026-09-04", amount = -5.0, reconciliationStatus = BankReconciliationStatus.NO_RECEIPT_REQUIRED,
            noReceiptReason = "Bankgebühr", importedAt = "import", updatedAt = "status-change"
        ))
        val payload = SupplementalDriveBackup.createPayload(context, database)
        assertEquals(7, payload.getInt("schemaVersion"))
        database.bankDao().upsertTransaction(database.bankDao().getTransaction("t")!!.copy(reconciliationStatus = BankReconciliationStatus.OPEN, noReceiptReason = "", updatedAt = "other"))
        SupplementalDriveBackup.restorePayload(context, database, payload)
        SupplementalDriveBackup.restorePayload(context, database, payload)
        val restored = database.bankDao().getTransaction("t")!!
        assertEquals(BankReconciliationStatus.NO_RECEIPT_REQUIRED, restored.reconciliationStatus)
        assertEquals("Bankgebühr", restored.noReceiptReason)
        assertEquals("status-change", restored.updatedAt)
        assertEquals(1, database.bankDao().getAllTransactions().count { it.transactionId == "t" })
    }

    @Test fun schema6WithoutUpdatedAtStillRestores() = runTest {
        val old = JSONObject("""
            {"schemaVersion":6,
             "bankAccounts":[{"accountId":"a","displayName":"Alt","bankName":"Sparkasse","accountHolder":"","iban":"DE","currency":"EUR","source":"CSV","active":true,"createdAt":"","updatedAt":""}],
             "bankTransactions":[{"transactionId":"t","accountId":"a","bookingDate":"2026-09-01","valueDate":"","amount":-10.0,"currency":"EUR","counterparty":"Alt","counterpartyIban":"","purpose":"","bankReference":"","source":"CSV","propertyId":"p","unitId":"","importFileName":"old.csv","importRunId":"run","reconciliationStatus":"OPEN","noReceiptReason":"","importedAt":"import"}],
             "bankReceiptLinks":[]}
        """.trimIndent())
        SupplementalDriveBackup.restorePayload(context, database, old)
        assertEquals("", database.bankDao().getTransaction("t")?.updatedAt)
        assertEquals("old.csv", database.bankDao().getTransaction("t")?.importFileName)
    }

    @Test fun bankBackupContainsNoCredentialLikeFields() = runTest {
        val serialized = SupplementalDriveBackup.createPayload(context, database).toString().lowercase()
        listOf("pin", "fints", "access_token", "oauth", "api_key", "apikey", "openai", "gemini_api_key").forEach {
            assertFalse("unexpected secret field marker $it", serialized.contains(it))
        }
    }
}
''')

print("Bank Phase 1 final-closure patch applied successfully")
