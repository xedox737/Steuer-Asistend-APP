package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankLoanMatcher
import com.example.data.BankLoanSuggestion
import com.example.data.BankMatchSuggestion
import com.example.data.BankReceiptLink
import com.example.data.BankReceiptMatcher
import com.example.data.BankReconciliationStatus
import com.example.data.BankReviewUiPolicy
import com.example.data.BankTransaction
import com.example.data.Receipt
import com.example.data.StableDocumentIdentity
import java.time.LocalDate
import java.time.YearMonth

private enum class BankListFilter { REVIEW, MATCHED, ALL }

private data class BankRentHint(
    val unit: WohneinheitStatus,
    val tenantName: String,
    val expected: Double,
    val score: Int
)

@Composable
fun BankScreen(viewModel: ReceiptViewModel) {
    val context = LocalContext.current
    val transactions by viewModel.bankTransactions.collectAsState()
    val accounts by viewModel.bankAccounts.collectAsState()
    val links by viewModel.bankReceiptLinks.collectAsState()
    val receipts by viewModel.receipts.collectAsState()
    val suggestions by viewModel.bankMatchSuggestions.collectAsState()
    val importStatus by viewModel.bankImportStatus.collectAsState()
    val units by viewModel.wohneinheitenStatus.collectAsState()
    val property by viewModel.propertyMetadata.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val learningRules by viewModel.bankLearningRules.collectAsState()
    val ruleEvaluations by viewModel.bankRuleEvaluations.collectAsState()
    val rentSuggestions by viewModel.bankRentSuggestions.collectAsState()
    val phase2CLoanSuggestions by viewModel.bankLoanSuggestions.collectAsState()

    var filter by remember { mutableStateOf(BankListFilter.REVIEW) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var receiptPickerFor by remember { mutableStateOf<BankTransaction?>(null) }
    var bankPickerForReceipt by remember { mutableStateOf<Receipt?>(null) }
    var noReceiptFor by remember { mutableStateOf<BankTransaction?>(null) }
    var showBankRules by remember { mutableStateOf(false) }
    var showRentMatching by remember { mutableStateOf(false) }
    var showPhase2C by remember { mutableStateOf(false) }
    var showPhase2D by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importBankFile(uri)
    }

    val filteredTransactions = remember(transactions, selectedAccountId) {
        selectedAccountId?.let { accountId -> transactions.filter { it.accountId == accountId } } ?: transactions
    }
    val propertyId = property?.propertyId ?: StableDocumentIdentity.LEGACY_PROPERTY_ID
    val loanSuggestions = phase2CLoanSuggestions
    val rentHints = remember(rentSuggestions, units, propertyId) {
        rentSuggestions.mapNotNull { (transactionId, candidates) ->
            val top = candidates.firstOrNull { it.propertyId == propertyId } ?: candidates.firstOrNull() ?: return@mapNotNull null
            val unit = units.firstOrNull { PropertyUnitScopedData.stableUnitId(top.propertyId, it) == top.unitId }
                ?: return@mapNotNull null
            transactionId to BankRentHint(unit, top.tenantName, top.expectedAmount, top.score)
        }.toMap()
    }
    val reviewCount = filteredTransactions.count { BankReviewUiPolicy.isReviewQueue(it.reconciliationStatus) }
    val matchedCount = filteredTransactions.count { BankReviewUiPolicy.isMatched(it.reconciliationStatus) }
    val missingReceiptCount = filteredTransactions.count { transaction ->
        transaction.amount < 0 && BankReviewUiPolicy.isReviewQueue(transaction.reconciliationStatus) &&
            (transaction.reconciliationStatus == BankReconciliationStatus.PARTIAL || suggestions[transaction.transactionId] == null)
    }
    val noReceiptCount = filteredTransactions.count { BankReviewUiPolicy.isNoReceiptRequired(it.reconciliationStatus) }
    val shown = when (filter) {
        BankListFilter.REVIEW -> filteredTransactions.filter { BankReviewUiPolicy.isReviewQueue(it.reconciliationStatus) }
        BankListFilter.MATCHED -> filteredTransactions.filter { BankReviewUiPolicy.isCompleted(it.reconciliationStatus) }
        BankListFilter.ALL -> filteredTransactions
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = AccentBlue)
                        Column {
                            Text("Bank & Belege", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DarkNavy)
                            Text(
                                if (accounts.isEmpty()) "Noch kein Konto importiert" else "${accounts.size} Konto/Konten • ${transactions.size} Buchungen",
                                fontSize = 12.sp,
                                color = SlateGray
                            )
                        }
                    }
                    Text(
                        "Kontoauszug importieren, passende Belege bestätigen oder direkt aus einer Buchung einen Beleg anlegen.",
                        fontSize = 13.sp,
                        color = SlateGray
                    )
                    if (accounts.isNotEmpty()) {
                        Text("Konto", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkNavy)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedAccountId == null,
                                    onClick = { selectedAccountId = null },
                                    label = { Text("Alle Konten") }
                                )
                            }
                            items(accounts, key = { it.accountId }) { account ->
                                FilterChip(
                                    selected = selectedAccountId == account.accountId,
                                    onClick = { selectedAccountId = account.accountId },
                                    label = {
                                        Text(
                                            buildString {
                                                append(account.displayName.ifBlank { "Bankkonto" })
                                                maskedIban(account.iban).takeIf { it.isNotBlank() }?.let { append(" • ").append(it) }
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            importLauncher.launch(arrayOf("text/csv", "text/xml", "application/xml", "application/octet-stream", "text/plain"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Text("  Kontoauszug importieren")
                    }
                    if (!importStatus.isNullOrBlank()) {
                        Text(importStatus.orEmpty(), fontSize = 12.sp, color = SlateGray)
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BankSummaryCard("Zu prüfen", reviewCount.toString(), Modifier.weight(1f))
                    BankSummaryCard("Beleg fehlt", missingReceiptCount.toString(), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BankSummaryCard("Zugeordnet", matchedCount.toString(), Modifier.weight(1f))
                    BankSummaryCard("Kein Beleg nötig", noReceiptCount.toString(), Modifier.weight(1f))
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == BankListFilter.REVIEW, onClick = { filter = BankListFilter.REVIEW }, label = { Text("Zu prüfen") })
                FilterChip(selected = filter == BankListFilter.MATCHED, onClick = { filter = BankListFilter.MATCHED }, label = { Text("Erledigt") })
                FilterChip(selected = filter == BankListFilter.ALL, onClick = { filter = BankListFilter.ALL }, label = { Text("Alle") })
            }
            OutlinedButton(onClick={ showBankRules = !showBankRules }, modifier=Modifier.fillMaxWidth()) { Text(if(showBankRules) "Bankregeln ausblenden" else "Bankregeln") }
            OutlinedButton(onClick={ showRentMatching = !showRentMatching }, modifier=Modifier.fillMaxWidth()) { Text(if(showRentMatching) "Mietabgleich ausblenden" else "Mietabgleich") }
            OutlinedButton(onClick={ showPhase2C = !showPhase2C }, modifier=Modifier.fillMaxWidth()) { Text(if(showPhase2C) "Darlehen & Wiederkehrend ausblenden" else "Darlehen & Wiederkehrend") }
            OutlinedButton(onClick={ showPhase2D = !showPhase2D }, modifier=Modifier.fillMaxWidth()) { Text(if(showPhase2D) "Prüfwarteschlange ausblenden" else "Prüfwarteschlange & Sammelzahlungen") }
        }
        if (showBankRules) { item { BankRulesPanel(viewModel, learningRules) } }
        if (showRentMatching) { item { BankRentPanel(viewModel) } }
        if (showPhase2C) { item { BankPhase2CPanel(viewModel) } }
        if (showPhase2D) { item { BankPhase2DReviewPanel(viewModel) } }

        if (shown.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Text(
                        if (transactions.isEmpty()) "Importiere zuerst einen CSV- oder CAMT.053-Kontoauszug." else "Für die aktuelle Kontoauswahl ist hier nichts zu prüfen.",
                        modifier = Modifier.padding(16.dp),
                        color = SlateGray
                    )
                }
            }
        } else {
            items(shown, key = { it.transactionId }) { transaction ->
                BankTransactionCard(
                    transaction = transaction,
                    suggestion = suggestions[transaction.transactionId],
                    receipt = suggestions[transaction.transactionId]?.receiptId?.let { id -> receipts.firstOrNull { it.id == id } },
                    linkedLinks = links.filter { it.transactionId == transaction.transactionId },
                    receipts = receipts,
                    rentHint = rentHints[transaction.transactionId],
                    loanSuggestions = loanSuggestions[transaction.transactionId].orEmpty(),
                    loans = loans,
                    onConfirmSuggestion = { suggestion ->
                        viewModel.confirmBankReceiptLink(transaction.transactionId, suggestion.receiptId)
                    },
                    onCreateReceipt = { viewModel.startReceiptFromBankTransaction(transaction) },
                    onCreateRentReceipt = { hint -> viewModel.startRentReceiptFromBankTransaction(transaction, hint.unit, hint.tenantName) },
                    onPickReceipt = { receiptPickerFor = transaction },
                    onNoReceipt = { noReceiptFor = transaction },
                    onManualReview = { viewModel.markBankTransactionForReview(transaction.transactionId) },
                    ruleEvaluation = ruleEvaluations[transaction.transactionId],
                    learningRules = learningRules,
                    onRuleRejected = { ruleId -> viewModel.rejectBankRuleMatch(ruleId) },
                    onReopen = { viewModel.reopenBankTransaction(transaction.transactionId) },
                    onUnlink = { link -> viewModel.removeBankReceiptLink(link.linkId, transaction.transactionId) }
                )
            }
        }

        if (filteredTransactions.isNotEmpty() && receipts.isNotEmpty()) {
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

        item { Spacer(Modifier.height(24.dp)) }
    }

    receiptPickerFor?.let { transaction ->
        BankReceiptPickerDialog(
            transaction = transaction,
            receipts = receipts,
            links = links,
            onDismiss = { receiptPickerFor = null },
            onSelect = { receipt ->
                viewModel.confirmBankReceiptLink(transaction.transactionId, receipt.id)
                receiptPickerFor = null
            }
        )
    }

    bankPickerForReceipt?.let { receipt ->
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
        NoReceiptReasonDialog(
            onDismiss = { noReceiptFor = null },
            onSelect = { reason ->
                viewModel.markBankTransactionNoReceiptRequired(transaction.transactionId, reason)
                noReceiptFor = null
            }
        )
    }
}

private fun bestRentHint(
    context: android.content.Context,
    propertyId: String,
    transaction: BankTransaction,
    units: List<WohneinheitStatus>,
    receipts: List<Receipt>
): BankRentHint? {
    if (transaction.amount <= 0) return null
    val month = runCatching { YearMonth.from(LocalDate.parse(transaction.bookingDate)) }.getOrNull() ?: return null
    return units.mapNotNull { unit ->
        val projection = RentTrackingLogic.month(context, propertyId, unit, receipts, month)
        if (projection.expected <= 0.01) return@mapNotNull null
        var score = 0
        val amountDiff = kotlin.math.abs(transaction.amount - projection.expected)
        score += when {
            amountDiff <= 0.01 -> 55
            amountDiff <= 5.0 -> 35
            amountDiff / projection.expected <= 0.05 -> 20
            else -> 0
        }
        val bankText = normalizeRentText("${transaction.counterparty} ${transaction.purpose}")
        val tenantText = normalizeRentText(projection.tenantNames)
        if (tenantText.isNotBlank() && tenantText.split(' ').filter { it.length >= 3 }.any { it in bankText }) score += 25
        val unitTokens = normalizeRentText("${unit.name} ${unit.label}").split(' ').filter { it.length >= 2 }
        if (unitTokens.any { it in bankText }) score += 10
        if ("miete" in bankText) score += 10
        BankRentHint(unit, projection.tenantNames, projection.expected, score.coerceAtMost(100)).takeIf { score >= 60 }
    }.maxByOrNull { it.score }
}

private fun normalizeRentText(value: String): String = value.lowercase(java.util.Locale.GERMANY)
    .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
    .replace(Regex("[^a-z0-9]+"), " ").trim()

private fun maskedIban(iban: String): String {
    val compact = iban.replace(" ", "").trim()
    if (compact.length <= 8) return compact
    return compact.take(4) + "••••" + compact.takeLast(4)
}

@Composable
private fun BankSummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = DarkNavy)
            Text(title, fontSize = 11.sp, color = SlateGray)
        }
    }
}

@Composable
private fun BankTransactionCard(
    transaction: BankTransaction,
    suggestion: BankMatchSuggestion?,
    receipt: Receipt?,
    linkedLinks: List<BankReceiptLink>,
    receipts: List<Receipt>,
    rentHint: BankRentHint?,
    loanSuggestions: List<BankLoanSuggestion>,
    loans: List<com.example.data.Loan>,
    onConfirmSuggestion: (BankMatchSuggestion) -> Unit,
    onCreateReceipt: () -> Unit,
    onCreateRentReceipt: (BankRentHint) -> Unit,
    onPickReceipt: () -> Unit,
    onNoReceipt: () -> Unit,
    onManualReview: () -> Unit,
    ruleEvaluation: com.example.data.BankRuleEvaluation?,
    learningRules: List<com.example.data.BankLearningRule>,
    onRuleRejected: (String) -> Unit,
    onReopen: () -> Unit,
    onUnlink: (BankReceiptLink) -> Unit
) {
    val statusText = when (transaction.reconciliationStatus) {
        BankReconciliationStatus.MATCHED -> "Zugeordnet"
        BankReconciliationStatus.PARTIAL -> "Teilweise zugeordnet"
        BankReconciliationStatus.NO_RECEIPT_REQUIRED -> "Kein Beleg erforderlich"
        BankReconciliationStatus.REVIEW -> "Prüfen"
        else -> "Offen"
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(transaction.counterparty.ifBlank { "Unbekannter Zahlungspartner" }, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text(transaction.bookingDate, fontSize = 12.sp, color = SlateGray)
                }
                Text(
                    NumberFormatter.format(transaction.amount),
                    fontWeight = FontWeight.Bold,
                    color = if (transaction.amount >= 0) EmeraldGreen else DarkNavy
                )
            }
            if (transaction.purpose.isNotBlank()) {
                Text(transaction.purpose, fontSize = 12.sp, color = SlateGray, maxLines = 3)
            }
            Text(statusText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
            ruleEvaluation?.let { evaluation ->
                if (evaluation.hasConflict) Text("Mehrere Regeln passen – bitte selbst entscheiden.", fontSize=11.sp, color=SlateGray)
                evaluation.suggestions.firstOrNull()?.let { rs ->
                    val ruleName=learningRules.firstOrNull{it.ruleId==rs.ruleId}?.displayName ?: rs.ruleId
                    Text("Regel „$ruleName“ • ${rs.confidence}% • ${rs.reasons.take(3).joinToString(" • ")}", fontSize=11.sp, color=SlateGray)
                    TextButton(onClick={onRuleRejected(rs.ruleId)}) { Text("Regel passt hier nicht") }
                }
            }

            if (linkedLinks.isNotEmpty()) {
                HorizontalDivider()
                Text("Bestätigte Zuordnungen", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkNavy)
                linkedLinks.forEach { link ->
                    val linkedReceipt = receipts.firstOrNull { it.id == link.receiptId || (link.receiptInternalId.isNotBlank() && it.internalId == link.receiptInternalId) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(linkedReceipt?.aussteller?.ifBlank { "Beleg ${link.receiptId}" } ?: "Beleg ${link.receiptId}", fontSize = 12.sp)
                            Text("${NumberFormatter.format(link.allocatedAmount)} zugeordnet", fontSize = 11.sp, color = SlateGray)
                        }
                        TextButton(onClick = { onUnlink(link) }) { Text("Zuordnung lösen") }
                    }
                }
            }

            if (transaction.reconciliationStatus !in setOf(BankReconciliationStatus.MATCHED, BankReconciliationStatus.NO_RECEIPT_REQUIRED)) {
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
                if (suggestion != null && receipt != null) {
                    HorizontalDivider()
                    Text(
                        "Vorschlag ${suggestion.score}% • ${receipt.aussteller} • ${NumberFormatter.format(receipt.bruttobetrag)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkNavy
                    )
                    if (suggestion.reasons.isNotEmpty()) {
                        Text(suggestion.reasons.take(3).joinToString(" • "), fontSize = 11.sp, color = SlateGray)
                    }
                    Button(onClick = { onConfirmSuggestion(suggestion) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Text("  Beleg zuordnen")
                    }
                }
                if (rentHint != null && suggestion == null) {
                    HorizontalDivider()
                    Text(
                        "Mögliche Mietzahlung ${rentHint.score}% • ${rentHint.unit.label.ifBlank { rentHint.unit.name }} • Soll ${NumberFormatter.format(rentHint.expected)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkNavy
                    )
                    Text("Mieter: ${rentHint.tenantName.ifBlank { "nicht hinterlegt" }}", fontSize = 11.sp, color = SlateGray)
                    Button(onClick = { onCreateRentReceipt(rentHint) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Als Mietbeleg prüfen und anlegen")
                    }
                }
                OutlinedButton(onClick = onPickReceipt, modifier = Modifier.fillMaxWidth()) {
                    Text("Vorhandenen Beleg suchen")
                }
                OutlinedButton(onClick = onCreateReceipt, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  Beleg aus Buchung anlegen")
                }
                if (transaction.reconciliationStatus == BankReconciliationStatus.OPEN && linkedLinks.isEmpty()) {
                    OutlinedButton(onClick = onManualReview, modifier = Modifier.fillMaxWidth()) {
                        Text("Manuell prüfen")
                    }
                } else if (transaction.reconciliationStatus == BankReconciliationStatus.REVIEW && linkedLinks.isEmpty()) {
                    OutlinedButton(onClick = onReopen, modifier = Modifier.fillMaxWidth()) {
                        Text("Wieder auf offen setzen")
                    }
                }
                if (transaction.reconciliationStatus != BankReconciliationStatus.REVIEW) {
                    TextButton(onClick = onNoReceipt, modifier = Modifier.align(Alignment.End)) {
                        Text("Kein Beleg erforderlich")
                    }
                }
            } else if (transaction.reconciliationStatus == BankReconciliationStatus.MATCHED) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen)
                    Text("  Vollständig zugeordnet", fontSize = 12.sp, color = EmeraldGreen)
                }
            } else {
                Text(transaction.noReceiptReason, fontSize = 12.sp, color = SlateGray)
                OutlinedButton(onClick = onReopen, modifier = Modifier.fillMaxWidth()) {
                    Text("Zustand wieder öffnen")
                }
            }
        }
    }
}

@Composable
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

@Composable
private fun BankReceiptPickerDialog(
    transaction: BankTransaction,
    receipts: List<Receipt>,
    links: List<BankReceiptLink>,
    onDismiss: () -> Unit,
    onSelect: (Receipt) -> Unit
) {
    val ranked = remember(transaction.transactionId, receipts, links) {
        BankReceiptMatcher.rankReceipts(transaction, receipts, links)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vorhandenen Beleg zuordnen") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ranked.isEmpty()) {
                    item { Text("Keine passenden Belege gefunden.") }
                } else {
                    items(ranked, key = { it.receiptId }) { suggestion ->
                        val receipt = receipts.firstOrNull { it.id == suggestion.receiptId }
                        if (receipt != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(1.dp, BorderColor),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(receipt.aussteller, fontWeight = FontWeight.SemiBold)
                                    Text("${receipt.datum} • ${NumberFormatter.format(receipt.bruttobetrag)} • ${suggestion.score}%", fontSize = 12.sp)
                                    TextButton(onClick = { onSelect(receipt) }) { Text("Zuordnen") }
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
    var query by remember(receipt.id) { mutableStateOf("") }
    val visible = remember(ranked, query) {
        val needle = query.trim().lowercase(java.util.Locale.GERMANY)
        if (needle.isBlank()) ranked else ranked.filter { suggestion ->
            transactions.firstOrNull { it.transactionId == suggestion.transactionId }?.let { transaction ->
                listOf(
                    transaction.bookingDate,
                    transaction.counterparty,
                    transaction.purpose,
                    transaction.bankReference,
                    transaction.amount.toString()
                ).any { it.lowercase(java.util.Locale.GERMANY).contains(needle) }
            } == true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Andere Buchung auswählen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Offene Bankbuchungen durchsuchen") }
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (visible.isEmpty()) {
                        item {
                            Text(
                                if (query.isBlank()) "Keine offene Bankbuchung ist für diesen Beleg verfügbar."
                                else "Keine Buchung passt zur Suche."
                            )
                        }
                    } else {
                        items(visible, key = { it.transactionId }) { suggestion ->
                            val transaction = transactions.firstOrNull { it.transactionId == suggestion.transactionId }
                            if (transaction != null) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    border = BorderStroke(1.dp, BorderColor),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text(
                                            transaction.counterparty.ifBlank { transaction.purpose.ifBlank { "Bankbuchung" } },
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "${transaction.bookingDate} • ${NumberFormatter.format(transaction.amount)} • Score ${suggestion.score}%",
                                            fontSize = 12.sp
                                        )
                                        if (suggestion.reasons.isNotEmpty()) {
                                            Text(
                                                suggestion.reasons.take(3).joinToString(" • "),
                                                fontSize = 11.sp,
                                                color = SlateGray
                                            )
                                        }
                                        TextButton(onClick = { onSelect(transaction) }) { Text("Zuordnen") }
                                    }
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
private fun NoReceiptReasonDialog(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val reasons = listOf("Eigene Umbuchung", "Mieteinnahme", "Darlehen / Tilgung", "Privat", "Bankgebühr", "Sonstiges")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Warum ist kein Beleg nötig?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                reasons.forEach { reason ->
                    TextButton(onClick = { onSelect(reason) }, modifier = Modifier.fillMaxWidth()) {
                        Text(reason, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
