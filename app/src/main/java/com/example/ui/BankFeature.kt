package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankAccount
import com.example.data.BankMatchSuggestion
import com.example.data.BankReceiptLink
import com.example.data.BankReceiptMatcher
import com.example.data.BankReconciliationStatus
import com.example.data.BankTransaction
import com.example.data.BankTransactionSplitPolicy
import com.example.data.Receipt
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class BankToolsMode { NONE, RULES, RENT, LOAN_RECURRING, REVIEW_COMBINATIONS, REVERSE_RECEIPT }

@Composable
fun BankScreen(viewModel: ReceiptViewModel) {
    val transactions by viewModel.bankTransactions.collectAsState()
    val accounts by viewModel.bankAccounts.collectAsState()
    val links by viewModel.bankReceiptLinks.collectAsState()
    val receipts by viewModel.receipts.collectAsState()
    val suggestions by viewModel.bankMatchSuggestions.collectAsState()
    val assignments by viewModel.bankRentAssignments.collectAsState()
    val units by viewModel.wohneinheitenStatus.collectAsState()
    val rentSuggestions by viewModel.bankRentSuggestions.collectAsState()
    val importStatus by viewModel.bankImportStatus.collectAsState()
    val learningRules by viewModel.bankLearningRules.collectAsState()

    var searchText by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(BankCompactFilter.ALL) }
    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    var selectedTransactionId by remember { mutableStateOf<String?>(null) }
    var receiptPickerFor by remember { mutableStateOf<BankTransaction?>(null) }
    var bankPickerForReceipt by remember { mutableStateOf<Receipt?>(null) }
    var noReceiptFor by remember { mutableStateOf<BankTransaction?>(null) }
    var receiptDetails by remember { mutableStateOf<Receipt?>(null) }
    var accountMenuOpen by remember { mutableStateOf(false) }
    var toolsMenuOpen by remember { mutableStateOf(false) }
    var toolsMode by remember { mutableStateOf(BankToolsMode.NONE) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importBankFile(uri)
    }

    val accountTransactions = remember(transactions, selectedAccountId) {
        BankCompactUiPolicy.account(transactions, selectedAccountId)
    }
    val counts = remember(accountTransactions) { BankCompactUiPolicy.counts(accountTransactions) }
    val summary = remember(accountTransactions) { BankCompactUiPolicy.summary(accountTransactions) }
    val shown = remember(accountTransactions, filter, searchText) {
        BankCompactUiPolicy.search(BankCompactUiPolicy.filter(accountTransactions, filter), searchText)
    }
    val groups = remember(shown) { BankCompactUiPolicy.group(shown) }
    val linksByTransaction = remember(links) { links.groupBy { it.transactionId } }
    val assignmentsByTransaction = remember(assignments) { assignments.groupBy { it.transactionId } }
    val receiptById = remember(receipts) { receipts.associateBy { it.id } }
    val accountById = remember(accounts) { accounts.associateBy { it.accountId } }
    val selectedTransaction = selectedTransactionId?.let { id -> transactions.firstOrNull { it.transactionId == id } }

    BackHandler(enabled = selectedTransaction != null) { selectedTransactionId = null }

    if (selectedTransaction != null) {
        BankTransactionDetailsScreen(
            viewModel = viewModel,
            transaction = selectedTransaction,
            account = accountById[selectedTransaction.accountId],
            suggestion = suggestions[selectedTransaction.transactionId],
            linkedLinks = linksByTransaction[selectedTransaction.transactionId].orEmpty(),
            assignments = assignmentsByTransaction[selectedTransaction.transactionId].orEmpty(),
            receipts = receipts,
            receiptById = receiptById,
            rentSuggestion = rentSuggestions[selectedTransaction.transactionId].orEmpty().firstOrNull(),
            units = units,
            onBack = { selectedTransactionId = null },
            onPickReceipt = { receiptPickerFor = selectedTransaction },
            onNoReceipt = { noReceiptFor = selectedTransaction },
            onReceiptDetails = { receiptDetails = it }
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Buchungen durchsuchen …") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Suche") }
                    )
                    IconButton(onClick = { toolsMenuOpen = true }) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = "Filter und Werkzeuge")
                    }
                    DropdownMenu(expanded = toolsMenuOpen, onDismissRequest = { toolsMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("Bankregeln") }, onClick = { toolsMode = BankToolsMode.RULES; toolsMenuOpen = false })
                        DropdownMenuItem(text = { Text("Mietabgleich") }, onClick = { toolsMode = BankToolsMode.RENT; toolsMenuOpen = false })
                        DropdownMenuItem(text = { Text("Darlehen & Wiederkehrend") }, onClick = { toolsMode = BankToolsMode.LOAN_RECURRING; toolsMenuOpen = false })
                        DropdownMenuItem(text = { Text("Prüfwarteschlange & Sammelzahlungen") }, onClick = { toolsMode = BankToolsMode.REVIEW_COMBINATIONS; toolsMenuOpen = false })
                        DropdownMenuItem(text = { Text("Beleg → Bank-Zuordnung") }, onClick = { toolsMode = BankToolsMode.REVERSE_RECEIPT; toolsMenuOpen = false })
                        DropdownMenuItem(text = { Text("Kontoauszug importieren") }, onClick = {
                            toolsMenuOpen = false
                            importLauncher.launch(arrayOf("text/csv", "text/xml", "application/xml", "application/zip", "application/x-zip-compressed", "application/octet-stream", "text/plain"))
                        })
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BankCompactFilter.entries.forEach { item ->
                        FilterChip(
                            selected = filter == item,
                            onClick = { filter = item },
                            label = { Text("${BankCompactUiPolicy.filterLabel(item)} (${BankCompactUiPolicy.countFor(counts, item)})", maxLines = 1) }
                        )
                    }
                }
            }

            item {
                Box {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { accountMenuOpen = true },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = DarkNavy)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                val account = selectedAccountId?.let(accountById::get)
                                Text(account?.displayName?.ifBlank { "Bankkonto" } ?: "Alle Konten", fontWeight = FontWeight.SemiBold, color = DarkNavy)
                                Text(account?.iban?.let(::maskedIban).orEmpty().ifBlank { if (account == null) "Alle importierten Bankkonten" else "IBAN nicht hinterlegt" }, fontSize = 11.sp, color = SlateGray)
                            }
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Konto auswählen")
                        }
                    }
                    DropdownMenu(expanded = accountMenuOpen, onDismissRequest = { accountMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("Alle Konten") }, onClick = { selectedAccountId = null; accountMenuOpen = false })
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text("${account.displayName.ifBlank { "Bankkonto" }} • ${maskedIban(account.iban)}") },
                                onClick = { selectedAccountId = account.accountId; accountMenuOpen = false }
                            )
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BankMoneySummaryCard("Eingänge", summary.incoming, true, Modifier.weight(1f))
                    BankMoneySummaryCard("Ausgänge", summary.outgoing, false, Modifier.weight(1f))
                    BankMoneySummaryCard("Saldo", summary.balance, summary.balance >= 0, Modifier.weight(1f))
                }
                if (!importStatus.isNullOrBlank()) Text(importStatus.orEmpty(), fontSize = 11.sp, color = SlateGray, modifier = Modifier.padding(top = 4.dp))
            }

            when (toolsMode) {
                BankToolsMode.RULES -> item { ToolContainer("Bankregeln", onClose = { toolsMode = BankToolsMode.NONE }) { BankRulesPanel(viewModel, learningRules) } }
                BankToolsMode.RENT -> item { ToolContainer("Mietabgleich", onClose = { toolsMode = BankToolsMode.NONE }) { BankRentPanel(viewModel) } }
                BankToolsMode.LOAN_RECURRING -> item { ToolContainer("Darlehen & Wiederkehrend", onClose = { toolsMode = BankToolsMode.NONE }) { BankPhase2CPanel(viewModel) } }
                BankToolsMode.REVIEW_COMBINATIONS -> item { ToolContainer("Prüfwarteschlange & Sammelzahlungen", onClose = { toolsMode = BankToolsMode.NONE }) { BankPhase2DReviewPanel(viewModel) } }
                BankToolsMode.REVERSE_RECEIPT -> item {
                    ToolContainer("Beleg → Bank-Zuordnung", onClose = { toolsMode = BankToolsMode.NONE }) {
                        BankReverseReceiptPanel(
                            transactions = accountTransactions,
                            receipts = receipts,
                            links = links,
                            onConfirm = { txId, receiptId -> viewModel.confirmBankReceiptLink(txId, receiptId) },
                            onChooseOther = { bankPickerForReceipt = it },
                            onUnlink = { viewModel.removeBankReceiptLink(it.linkId, it.transactionId) }
                        )
                    }
                }
                BankToolsMode.NONE -> Unit
            }

            if (groups.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderColor)) {
                        Text(
                            if (transactions.isEmpty()) "Noch keine Bankbuchungen vorhanden. Importiere CSV, CAMT.052/053 V8 oder ZIP-CAMT."
                            else "Keine Buchung passt zu Suche, Status und Konto.",
                            modifier = Modifier.padding(18.dp), color = SlateGray
                        )
                    }
                }
            } else {
                groups.forEach { group ->
                    item(key = "date-${group.key}") {
                        Text(group.label, fontWeight = FontWeight.Bold, color = SlateGray, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                    }
                    items(group.transactions, key = { it.transactionId }) { transaction ->
                        BankCompactTransactionRow(transaction = transaction, onClick = { selectedTransactionId = transaction.transactionId })
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
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
            transactions = accountTransactions,
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
    receiptDetails?.let { receipt -> ReceiptMiniDetailsDialog(receipt = receipt, onDismiss = { receiptDetails = null }) }
}

@Composable
private fun BankCompactTransactionRow(transaction: BankTransaction, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = SlateGray, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.size(9.dp))
            Column(Modifier.weight(1f)) {
                Text(transaction.counterparty.ifBlank { "Unbekannter Zahlungspartner" }, fontWeight = FontWeight.Bold, color = DarkNavy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(BankCompactUiPolicy.compactSubtitle(transaction), fontSize = 11.sp, color = SlateGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BankStatusBadge(transaction.reconciliationStatus)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(NumberFormatter.format(transaction.amount), fontWeight = FontWeight.Bold, color = if (transaction.amount >= 0) EmeraldGreen else DarkNavy, maxLines = 1)
                Text(formatDate(transaction.bookingDate), fontSize = 10.sp, color = SlateGray)
                Text("›", fontSize = 20.sp, color = AccentBlue)
            }
        }
    }
}

@Composable
private fun BankTransactionDetailsScreen(
    viewModel: ReceiptViewModel,
    transaction: BankTransaction,
    account: BankAccount?,
    suggestion: BankMatchSuggestion?,
    linkedLinks: List<BankReceiptLink>,
    assignments: List<com.example.data.BankRentAssignment>,
    receipts: List<Receipt>,
    receiptById: Map<Int, Receipt>,
    rentSuggestion: com.example.data.BankRentSuggestion?,
    units: List<WohneinheitStatus>,
    onBack: () -> Unit,
    onPickReceipt: () -> Unit,
    onNoReceipt: () -> Unit,
    onReceiptDetails: (Receipt) -> Unit
) {
    val linkedReceipts = linkedLinks.mapNotNull { link -> receiptById[link.receiptId]?.let { link to it } }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                Text("Buchungsdetails", fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 19.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Default.MoreHoriz, contentDescription = "Weitere Buchungsdaten", tint = SlateGray)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderColor)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFF1F5F9)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = SlateGray)
                        }
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(transaction.counterparty.ifBlank { "Unbekannter Zahlungspartner" }, fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 17.sp)
                            BankStatusBadge(transaction.reconciliationStatus)
                        }
                        Text(NumberFormatter.format(transaction.amount), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (transaction.amount >= 0) EmeraldGreen else DarkNavy)
                    }
                    HorizontalDivider()
                    BankDetailLine("Buchungsdatum", formatDate(transaction.bookingDate))
                    if (transaction.valueDate.isNotBlank()) BankDetailLine("Valutadatum", formatDate(transaction.valueDate))
                    if (transaction.purpose.isNotBlank()) BankDetailLine("Verwendungszweck", transaction.purpose)
                    if (transaction.bankReference.isNotBlank()) BankDetailLine("Referenz", transaction.bankReference)
                    BankDetailLine("Konto", buildString {
                        append(account?.displayName?.ifBlank { "Bankkonto" } ?: "Bankkonto")
                        account?.iban?.takeIf { it.isNotBlank() }?.let { append("\n").append(maskedIban(it)) }
                    })
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                BankQuickAction("Beleg\nsuchen", Icons.Default.Search, Modifier.weight(1f), onClick = onPickReceipt)
                BankQuickAction("Beleg\nanlegen", Icons.Default.Add, Modifier.weight(1f), onClick = { viewModel.startReceiptFromBankTransaction(transaction) })
                Column(Modifier.weight(1f)) {
                    BankTransactionSplitActions(viewModel = viewModel, transaction = transaction)
                }
                BankQuickAction("Kein Beleg\nerforderlich", Icons.Default.CheckCircle, Modifier.weight(1f), onClick = onNoReceipt)
            }
        }

        if (linkedLinks.isNotEmpty() || assignments.isNotEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderColor)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("✓ Zugeordnet", fontWeight = FontWeight.Bold, color = EmeraldGreen)
                        linkedReceipts.forEach { (link, receipt) ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(receipt.aussteller.ifBlank { "Beleg ${receipt.id}" }, fontWeight = FontWeight.SemiBold, color = DarkNavy)
                                    Text("${receipt.getEffectiveDisplayId()} • ${NumberFormatter.format(link.allocatedAmount)}", fontSize = 11.sp, color = SlateGray)
                                }
                                TextButton(onClick = { onReceiptDetails(receipt) }) { Text("Öffnen") }
                                TextButton(onClick = { viewModel.removeBankReceiptLink(link.linkId, transaction.transactionId) }) { Text("Lösen") }
                            }
                        }
                        assignments.forEach { assignment ->
                            Text("${NumberFormatter.format(assignment.allocatedAmount)} → ${com.example.data.BankSplitPaymentType.label(assignment.paymentType)}", color = DarkNavy, fontWeight = FontWeight.SemiBold)
                        }
                        val allocated = BankTransactionSplitPolicy.allocatedAmount(transaction.transactionId, linkedLinks, assignments)
                        Text("Gesamt zugeordnet: ${NumberFormatter.format(allocated)} • Rest: ${NumberFormatter.format((transaction.absoluteAmount - allocated).coerceAtLeast(0.0))}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SlateGray)
                    }
                }
            }
        }

        if (suggestion != null) {
            val receipt = receiptById[suggestion.receiptId]
            if (receipt != null && linkedLinks.none { it.receiptId == receipt.id }) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderColor)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("✨ KI-Vorschläge (1)", fontWeight = FontWeight.Bold, color = DarkNavy)
                                Text("${suggestion.score}%", fontWeight = FontWeight.Bold, color = EmeraldGreen)
                            }
                            Text(receipt.getEffectiveDisplayId(), fontWeight = FontWeight.SemiBold, color = DarkNavy)
                            Text(receipt.aussteller, fontSize = 12.sp, color = SlateGray)
                            Text("${NumberFormatter.format(receipt.bruttobetrag)} • ${formatDate(receipt.datum)} • ${suggestion.confidence}", fontSize = 11.sp, color = SlateGray)
                            if (suggestion.reasons.isNotEmpty()) Text(suggestion.reasons.take(3).joinToString(" • "), fontSize = 11.sp, color = SlateGray)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { viewModel.confirmBankReceiptLink(transaction.transactionId, receipt.id) }, modifier = Modifier.weight(1f)) { Text("Beleg zuordnen") }
                                OutlinedButton(onClick = { onReceiptDetails(receipt) }, modifier = Modifier.weight(1f)) { Text("Details") }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderColor)) {
                    Text("Noch kein passender Beleg gefunden.", modifier = Modifier.padding(12.dp), color = SlateGray)
                }
            }
        }

        if (rentSuggestion != null && transaction.amount > 0 && transaction.reconciliationStatus != BankReconciliationStatus.MATCHED) {
            val unit = units.firstOrNull { PropertyUnitScopedData.stableUnitId(rentSuggestion.propertyId, it) == rentSuggestion.unitId }
            if (unit != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderColor)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Mögliche Mietzahlung", fontWeight = FontWeight.Bold, color = DarkNavy)
                            Text("${rentSuggestion.tenantName} • ${NumberFormatter.format(rentSuggestion.expectedAmount)} • ${rentSuggestion.score}%", fontSize = 12.sp, color = SlateGray)
                            OutlinedButton(onClick = { viewModel.startRentReceiptFromBankTransaction(transaction, unit, rentSuggestion.tenantName) }, modifier = Modifier.fillMaxWidth()) { Text("Mietbeleg prüfen/anlegen") }
                        }
                    }
                }
            }
        }

        if (transaction.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderColor)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("Kein Beleg erforderlich", fontWeight = FontWeight.Bold, color = DarkNavy)
                        if (transaction.noReceiptReason.isNotBlank()) Text(transaction.noReceiptReason, color = SlateGray)
                        OutlinedButton(onClick = { viewModel.reopenBankTransaction(transaction.transactionId) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Buchung wieder öffnen") }
                    }
                }
            }
        } else {
            item {
                OutlinedButton(onClick = onNoReceipt, modifier = Modifier.fillMaxWidth()) { Text("Kein Beleg erforderlich") }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun BankQuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.height(78.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.fillMaxSize().padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = label.replace('\n', ' '), tint = AccentBlue, modifier = Modifier.size(21.dp))
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = DarkNavy, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BankStatusBadge(status: String) {
    val (background, foreground) = when (status) {
        BankReconciliationStatus.MATCHED -> Color(0xFFDCFCE7) to Color(0xFF166534)
        BankReconciliationStatus.PARTIAL -> Color(0xFFFFEDD5) to Color(0xFF9A3412)
        BankReconciliationStatus.REVIEW -> Color(0xFFEDE9FE) to Color(0xFF6D28D9)
        BankReconciliationStatus.NO_RECEIPT_REQUIRED -> Color(0xFFE2E8F0) to Color(0xFF334155)
        else -> Color(0xFFDBEAFE) to Color(0xFF1D4ED8)
    }
    Text(
        BankCompactUiPolicy.statusLabel(status),
        modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(8.dp)).background(background).padding(horizontal = 7.dp, vertical = 2.dp),
        color = foreground,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun BankMoneySummaryCard(title: String, amount: Double, positiveStyle: Boolean, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(horizontal = 7.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 9.sp, color = SlateGray)
            Text(NumberFormatter.format(amount), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (positiveStyle) EmeraldGreen else CrimsonRed, maxLines = 1)
        }
    }
}

@Composable
private fun BankDetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.weight(0.38f), fontSize = 11.sp, color = SlateGray)
        Text(value, modifier = Modifier.weight(0.62f), fontSize = 12.sp, color = DarkNavy)
    }
}

@Composable
private fun ToolContainer(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, color = DarkNavy, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Schließen") }
            }
            content()
        }
    }
}

@Composable
private fun BankReverseReceiptPanel(
    transactions: List<BankTransaction>,
    receipts: List<Receipt>,
    links: List<BankReceiptLink>,
    onConfirm: (String, Int) -> Unit,
    onChooseOther: (Receipt) -> Unit,
    onUnlink: (BankReceiptLink) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        receipts.take(30).forEach { receipt ->
            val receiptLinks = links.filter { it.receiptId == receipt.id || (receipt.internalId.isNotBlank() && it.receiptInternalId == receipt.internalId) }
            val suggestion = BankReceiptMatcher.bestForReceipt(receipt, transactions, links)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderColor)) {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    Text(receipt.aussteller.ifBlank { receipt.getEffectiveDisplayId() }, fontWeight = FontWeight.SemiBold, color = DarkNavy)
                    Text("${formatDate(receipt.datum)} • ${NumberFormatter.format(receipt.bruttobetrag)}", fontSize = 11.sp, color = SlateGray)
                    receiptLinks.forEach { link ->
                        Text("${NumberFormatter.format(link.allocatedAmount)} bereits zugeordnet", fontSize = 10.sp, color = EmeraldGreen)
                        TextButton(onClick = { onUnlink(link) }) { Text("Zuordnung lösen") }
                    }
                    if (receiptLinks.isEmpty() && suggestion != null) {
                        val tx = transactions.firstOrNull { it.transactionId == suggestion.transactionId }
                        if (tx != null) {
                            Text("Vorschlag: ${tx.counterparty.ifBlank { tx.purpose }} • ${suggestion.score}%", fontSize = 11.sp, color = SlateGray)
                            TextButton(onClick = { onConfirm(tx.transactionId, receipt.id) }) { Text("Treffer bestätigen") }
                        }
                    }
                    TextButton(onClick = { onChooseOther(receipt) }) { Text("Andere Buchung auswählen") }
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
    val ranked = remember(transaction.transactionId, receipts, links) { BankReceiptMatcher.rankReceipts(transaction, receipts, links) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vorhandenen Beleg zuordnen") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ranked.isEmpty()) item { Text("Keine passenden Belege gefunden.") }
                else items(ranked, key = { it.receiptId }) { suggestion ->
                    val receipt = receipts.firstOrNull { it.id == suggestion.receiptId }
                    if (receipt != null) {
                        Card(modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, BorderColor), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(10.dp)) {
                                Text(receipt.aussteller, fontWeight = FontWeight.SemiBold)
                                Text("${formatDate(receipt.datum)} • ${NumberFormatter.format(receipt.bruttobetrag)} • ${suggestion.score}%", fontSize = 12.sp)
                                TextButton(onClick = { onSelect(receipt) }) { Text("Zuordnen") }
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
    val ranked = remember(receipt.id, transactions, links) { BankReceiptMatcher.rankTransactionsForReceipt(receipt, transactions, links) }
    var query by remember(receipt.id) { mutableStateOf("") }
    val visible = remember(ranked, query, transactions) {
        val needle = query.trim().lowercase(Locale.GERMANY)
        if (needle.isBlank()) ranked else ranked.filter { suggestion ->
            transactions.firstOrNull { it.transactionId == suggestion.transactionId }?.let { transaction ->
                listOf(transaction.bookingDate, transaction.counterparty, transaction.purpose, transaction.bankReference, transaction.amount.toString())
                    .any { it.lowercase(Locale.GERMANY).contains(needle) }
            } == true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Andere Buchung auswählen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Offene Bankbuchungen durchsuchen") })
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (visible.isEmpty()) item { Text(if (query.isBlank()) "Keine offene Bankbuchung ist für diesen Beleg verfügbar." else "Keine Buchung passt zur Suche.") }
                    else items(visible, key = { it.transactionId }) { suggestion ->
                        val transaction = transactions.firstOrNull { it.transactionId == suggestion.transactionId }
                        if (transaction != null) {
                            Card(modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, BorderColor), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(transaction.counterparty.ifBlank { transaction.purpose.ifBlank { "Bankbuchung" } }, fontWeight = FontWeight.SemiBold)
                                    Text("${formatDate(transaction.bookingDate)} • ${NumberFormatter.format(transaction.amount)} • ${suggestion.score}%", fontSize = 12.sp)
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
private fun NoReceiptReasonDialog(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val reasons = listOf("Eigene Umbuchung", "Mieteinnahme", "Darlehen / Tilgung", "Privat", "Bankgebühr", "Sonstiges")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Warum ist kein Beleg nötig?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            reasons.forEach { reason -> TextButton(onClick = { onSelect(reason) }, modifier = Modifier.fillMaxWidth()) { Text(reason, modifier = Modifier.fillMaxWidth()) } }
        } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun ReceiptMiniDetailsDialog(receipt: Receipt, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(receipt.getEffectiveDisplayId()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(receipt.aussteller.ifBlank { "Unbekannter Aussteller" }, fontWeight = FontWeight.Bold)
                Text("Datum: ${formatDate(receipt.datum)}")
                Text("Betrag: ${NumberFormatter.format(receipt.bruttobetrag)}")
                if (receipt.beschreibung.isNotBlank()) Text(receipt.beschreibung)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}

private fun maskedIban(iban: String): String {
    val compact = iban.replace(" ", "").trim()
    if (compact.length <= 8) return compact
    return compact.take(4) + " •••• •••• " + compact.takeLast(4)
}

private fun formatDate(value: String): String {
    val date = runCatching { LocalDate.parse(value) }.getOrNull() ?: return value
    return date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY))
}
