package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Snackbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.data.BankAccount
import com.example.data.BankMatchSuggestion
import com.example.data.BankReceiptLink
import com.example.data.BankReceiptMatcher
import com.example.data.BankReconciliationStatus
import com.example.data.BankReviewState
import com.example.data.BankTransaction
import com.example.data.BankTransactionClassification
import com.example.data.BankTransactionSplitPolicy
import com.example.data.Receipt
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class BankToolsMode { NONE, RULES, RENT, LOAN_RECURRING, REVIEW_COMBINATIONS, REVERSE_RECEIPT, DATEV_PRECHECK }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BankScreen(viewModel: ReceiptViewModel, onDetailVisibilityChanged: (Boolean) -> Unit = {}) {
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
    val undoNotice by viewModel.bankUndoNotice.collectAsState()

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
    var showImportDetails by remember { mutableStateOf(false) }
    var selectedTransactionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingBatchAction by remember { mutableStateOf<String?>(null) }
    var pendingPropertyChoice by remember { mutableStateOf(false) }
    var pendingCategoryChoice by remember { mutableStateOf(false) }
    var pendingCategoryAssignment by remember { mutableStateOf<Pair<String, String>?>(null) }

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
    val selectedTransactions = remember(transactions, selectedTransactionIds) { transactions.filter { it.transactionId in selectedTransactionIds } }
    val linksByTransaction = remember(links) { links.groupBy { it.transactionId } }
    val assignmentsByTransaction = remember(assignments) { assignments.groupBy { it.transactionId } }
    val receiptById = remember(receipts) { receipts.associateBy { it.id } }
    val accountById = remember(accounts) { accounts.associateBy { it.accountId } }
    val selectedTransaction = selectedTransactionId?.let { id -> transactions.firstOrNull { it.transactionId == id } }
    val selectedTransferSuggestion = selectedTransaction?.takeIf {
        it.classification == BankTransactionClassification.TRANSFER && it.linkedTransferTransactionId.isBlank()
    }?.let { com.example.data.BankTransferMatcher.suggestions(it, transactions).firstOrNull() }
    val selectedSuggestedTransferCounterpart = selectedTransferSuggestion?.let { suggestion ->
        transactions.firstOrNull { it.transactionId == suggestion.counterTransactionId }
    }
    val selectedTransferCounterpart = selectedTransaction?.linkedTransferTransactionId
        ?.takeIf { it.isNotBlank() }
        ?.let { id -> transactions.firstOrNull { it.transactionId == id } }
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
        onDetailVisibilityChanged(selectedTransaction != null)
    }

    BackHandler(enabled = selectedTransaction != null) { selectedTransactionId = null }

    if (selectedTransaction != null) {
        BankTransactionDetailsScreen(
            viewModel = viewModel,
            transaction = selectedTransaction,
            account = accountById[selectedTransaction.accountId],
            suggestion = suggestions[selectedTransaction.transactionId],
            lastMonthSuggestion = lastMonthSuggestion,
            transferSuggestion = selectedTransferSuggestion,
            suggestedTransferCounterpart = selectedSuggestedTransferCounterpart,
            transferCounterpart = selectedTransferCounterpart,
            linkedLinks = linksByTransaction[selectedTransaction.transactionId].orEmpty(),
            assignments = assignmentsByTransaction[selectedTransaction.transactionId].orEmpty(),
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
                        modifier = Modifier.weight(1f).height(52.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(26.dp),
                        placeholder = { Text("Buchungen durchsuchen …") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Suche") }
                    )
                    Box {
                        IconButton(onClick = { toolsMenuOpen = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Filter und Werkzeuge", tint = DarkNavy)
                        }
                        DropdownMenu(expanded = toolsMenuOpen, onDismissRequest = { toolsMenuOpen = false }) {
                            DropdownMenuItem(text = { Text("Bankregeln") }, onClick = { toolsMode = BankToolsMode.RULES; toolsMenuOpen = false })
                            DropdownMenuItem(text = { Text("Mietabgleich") }, onClick = { toolsMode = BankToolsMode.RENT; toolsMenuOpen = false })
                            DropdownMenuItem(text = { Text("Darlehen & Wiederkehrend") }, onClick = { toolsMode = BankToolsMode.LOAN_RECURRING; toolsMenuOpen = false })
                            DropdownMenuItem(text = { Text("Prüfwarteschlange & Sammelzahlungen") }, onClick = { toolsMode = BankToolsMode.REVIEW_COMBINATIONS; toolsMenuOpen = false })
                            DropdownMenuItem(text = { Text("Beleg → Bank-Zuordnung") }, onClick = { toolsMode = BankToolsMode.REVERSE_RECEIPT; toolsMenuOpen = false })
                            DropdownMenuItem(text = { Text("DATEV-Vorprüfung") }, onClick = { toolsMode = BankToolsMode.DATEV_PRECHECK; toolsMenuOpen = false })
                            if (!importStatus.isNullOrBlank()) {
                                DropdownMenuItem(text = { Text("Importdetails") }, onClick = { showImportDetails = true; toolsMenuOpen = false })
                            }
                            DropdownMenuItem(text = { Text("Kontoauszug importieren") }, onClick = {
                                toolsMenuOpen = false
                                importLauncher.launch(arrayOf("text/csv", "text/xml", "application/xml", "application/zip", "application/x-zip-compressed", "application/octet-stream", "text/plain"))
                            })
                        }
                    }
                }
            }

            if (selectedTransactionIds.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)), border = BorderStroke(1.dp, AccentBlue)) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${selectedTransactionIds.size} ausgewählt", fontWeight = FontWeight.Bold, color = DarkNavy, modifier = Modifier.weight(1f))
                                TextButton(onClick = { selectedTransactionIds = emptySet() }) { Text("Abbrechen") }
                            }
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    com.example.data.BankBatchAction.PRIVATE to "Privat",
                                    com.example.data.BankBatchAction.TRANSFER to "Umbuchung",
                                    com.example.data.BankBatchAction.NO_RECEIPT_REQUIRED to "Kein Beleg",
                                    com.example.data.BankBatchAction.REVIEW_DONE to "Erledigt",
                                    com.example.data.BankBatchAction.REVIEW_OPEN to "Wieder öffnen"
                                ).forEach { (action, label) ->
                                    OutlinedButton(onClick = { pendingBatchAction = action }) { Text(label) }
                                }
                                OutlinedButton(onClick = { pendingPropertyChoice = true }) { Text("Immobilie") }
                                OutlinedButton(onClick = { pendingCategoryChoice = true }) { Text("Kategorie") }
                            }
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BankCompactFilter.entries.forEach { item ->
                        FilterChip(
                            selected = filter == item,
                            onClick = { filter = item },
                            label = { Text("${BankCompactUiPolicy.filterLabel(item)} (${BankCompactUiPolicy.countFor(counts, item)})", maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.White,
                                labelColor = SlateGray,
                                selectedContainerColor = Color(0xFF3B82F6),
                                selectedLabelColor = Color.White
                            )
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
            }

            when (toolsMode) {
                BankToolsMode.RULES -> item { ToolContainer("Bankregeln", onClose = { toolsMode = BankToolsMode.NONE }) { BankRulesPanel(viewModel, learningRules) } }
                BankToolsMode.RENT -> item { ToolContainer("Mietabgleich", onClose = { toolsMode = BankToolsMode.NONE }) { BankRentPanel(viewModel) } }
                BankToolsMode.LOAN_RECURRING -> item { ToolContainer("Darlehen & Wiederkehrend", onClose = { toolsMode = BankToolsMode.NONE }) { BankPhase2CPanel(viewModel) } }
                BankToolsMode.REVIEW_COMBINATIONS -> item { ToolContainer("Prüfwarteschlange & Sammelzahlungen", onClose = { toolsMode = BankToolsMode.NONE }) { BankPhase2DReviewPanel(viewModel) } }
                BankToolsMode.DATEV_PRECHECK -> item { ToolContainer("DATEV-Vorprüfung", onClose = { toolsMode = BankToolsMode.NONE }) { BankDatevPrecheckPanel(accountTransactions) } }
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
                        Text(group.label, fontWeight = FontWeight.Bold, color = SlateGray, fontSize = 13.sp, modifier = Modifier.padding(top = 7.dp, bottom = 2.dp))
                    }
                    item(key = "group-${group.key}") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, BorderColor),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column {
                                group.transactions.forEachIndexed { index, transaction ->
                                    BankCompactTransactionRow(
                                        transaction = transaction,
                                        selected = transaction.transactionId in selectedTransactionIds,
                                        selectionMode = selectedTransactionIds.isNotEmpty(),
                                        onClick = {
                                            if (selectedTransactionIds.isEmpty()) selectedTransactionId = transaction.transactionId
                                            else selectedTransactionIds = selectedTransactionIds.toggle(transaction.transactionId)
                                        },
                                        onLongClick = { selectedTransactionIds = selectedTransactionIds.toggle(transaction.transactionId) }
                                    )
                                    if (index < group.transactions.lastIndex) HorizontalDivider(color = BorderColor)
                                }
                            }
                        }
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
            onSelect = { selectedIds ->
                viewModel.confirmManyBankTransactionsForReceipt(selectedIds, receipt.id)
                bankPickerForReceipt = null
            }
        )
    }
    noReceiptFor?.let { transaction ->
        NoReceiptReasonDialog(
            onDismiss = { noReceiptFor = null },
            onSelect = { reason ->
                viewModel.applyBankBatchAction(
                    setOf(transaction.transactionId),
                    com.example.data.BankBatchAction.NO_RECEIPT_REQUIRED,
                    noReceiptReason = reason,
                    overwriteProtected = false
                )
                noReceiptFor = null
            }
        )
    }
    receiptDetails?.let { receipt ->
        ReceiptDetailDialog(receipt = receipt, viewModel = viewModel, onDismiss = { receiptDetails = null })
    }
    if (showImportDetails && !importStatus.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { showImportDetails = false },
            title = { Text("Importdetails") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    Text(importStatus.orEmpty(), fontSize = 11.sp, color = SlateGray)
                }
            },
            confirmButton = {
                TextButton(onClick = { showImportDetails = false }) { Text("Schließen") }
            }
        )
    }
    pendingBatchAction?.let { action ->
        val preview = com.example.data.BankBatchActionPolicy.preview(
            selectedTransactions,
            links.filter { it.transactionId in selectedTransactionIds },
            action,
            category = pendingCategoryAssignment?.first.orEmpty(),
            subcategory = pendingCategoryAssignment?.second.orEmpty()
        )
        BankBatchConfirmDialog(
            selected = preview.selected,
            protected = preview.protected,
            onDismiss = { pendingBatchAction = null; pendingCategoryAssignment = null },
            onApply = { overwrite ->
                viewModel.applyBankBatchAction(
                    selectedTransactionIds,
                    action,
                    category = pendingCategoryAssignment?.first.orEmpty(),
                    subcategory = pendingCategoryAssignment?.second.orEmpty(),
                    overwriteProtected = overwrite
                )
                selectedTransactionIds = emptySet()
                pendingBatchAction = null
                pendingCategoryAssignment = null
            }
        )
    }
    if (pendingPropertyChoice) {
        val propertyIds = (transactions.map { it.propertyId } + receipts.map { it.propertyId }).filter { it.isNotBlank() }.distinct().sorted()
        AlertDialog(
            onDismissRequest = { pendingPropertyChoice = false },
            title = { Text("Immobilie zuweisen") },
            text = { Column { propertyIds.forEach { propertyId -> TextButton(onClick = {
                viewModel.applyBankBatchAction(selectedTransactionIds, com.example.data.BankBatchAction.PROPERTY, propertyId)
                selectedTransactionIds = emptySet(); pendingPropertyChoice = false
            }, modifier = Modifier.fillMaxWidth()) { Text(propertyId) } }; if (propertyIds.isEmpty()) Text("Noch keine Immobilie vorhanden.") } },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pendingPropertyChoice = false }) { Text("Abbrechen") } }
        )
    }
    if (pendingCategoryChoice) {
        val favorites = remember(transactions, receipts) {
            com.example.data.BankAssignmentFavoritesPolicy.categories(transactions, receipts)
        }
        AlertDialog(
            onDismissRequest = { pendingCategoryChoice = false },
            title = { Text("Kategorie zuweisen") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    if (favorites.isEmpty()) item { Text("Noch keine Kategorien aus bestätigten Belegen vorhanden.") }
                    items(favorites, key = { "${it.category}|${it.subcategory}" }) { favorite ->
                        TextButton(onClick = {
                            pendingCategoryAssignment = favorite.category to favorite.subcategory
                            pendingBatchAction = com.example.data.BankBatchAction.CATEGORY
                            pendingCategoryChoice = false
                        }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(favorite.category, fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOf(favorite.subcategory.takeIf { it.isNotBlank() }, "${favorite.useCount}× verwendet").filterNotNull().joinToString(" • "),
                                    fontSize = 10.sp,
                                    color = SlateGray
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pendingCategoryChoice = false }) { Text("Abbrechen") } }
        )
    }
    if (undoNotice.id != 0L) {
        Popup(alignment = Alignment.BottomCenter, properties = PopupProperties(focusable = false)) {
            Snackbar(
                modifier = Modifier.padding(12.dp),
                action = { TextButton(onClick = viewModel::undoLastBankBatchAction) { Text("Rückgängig") } }
            ) { Text(undoNotice.message) }
        }
    }
}

private fun Set<String>.toggle(id: String): Set<String> = if (id in this) this - id else this + id

@Composable
private fun BankBatchConfirmDialog(
    selected: Int,
    protected: Int,
    onDismiss: () -> Unit,
    onApply: (overwriteProtected: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Massenänderung prüfen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("$selected Bankbuchungen ausgewählt.")
                if (protected > 0) {
                    Text(
                        "$protected Buchungen besitzen bereits bestätigte Links oder manuelle Zuordnungen.",
                        fontWeight = FontWeight.SemiBold,
                        color = CrimsonRed
                    )
                    Text("Du kannst nur unbelegte Buchungen ändern oder die vorhandenen Werte ausdrücklich überschreiben.", fontSize = 11.sp, color = SlateGray)
                } else {
                    Text("Die Änderung wird erst nach dieser Bestätigung gespeichert.", fontSize = 11.sp, color = SlateGray)
                }
            }
        },
        confirmButton = { Button(onClick = { onApply(false) }) { Text(if (protected > 0) "Nur unbelegte ändern" else "Ändern") } },
        dismissButton = {
            Row {
                if (protected > 0) TextButton(onClick = { onApply(true) }) { Text("Alle überschreiben") }
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        }
    )
}

private data class BankTransactionVisual(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val background: Color,
    val foreground: Color
)

private fun bankTransactionVisual(transaction: BankTransaction): BankTransactionVisual {
    val text = "${transaction.counterparty} ${transaction.purpose}".lowercase(Locale.GERMANY)
    return when {
        listOf("miete", "mieter", "kaution", "nebenkosten").any { it in text } || transaction.amount > 0 ->
            BankTransactionVisual(Icons.Default.Home, Color(0xFFDCFCE7), Color(0xFF16A34A))
        listOf("hornbach", "handwerk", "werkzeug").any { it in text } ->
            BankTransactionVisual(Icons.Default.Build, Color(0xFFEFF3F8), Color(0xFF64748B))
        listOf("haisch", "baumarkt", "obi", "toom", "bauhaus", "kartenzahlung").any { it in text } ->
            BankTransactionVisual(Icons.Default.ShoppingCart, Color(0xFFFEE2E2), Color(0xFFEF4444))
        listOf("stadtwerk", "strom", "gas", "wasser", "energie").any { it in text } ->
            BankTransactionVisual(Icons.Default.Person, Color(0xFFEFF3F8), Color(0xFF64748B))
        listOf("telekom", "vodafone", "telefon", "internet", "mobilfunk").any { it in text } ->
            BankTransactionVisual(Icons.Default.Description, Color(0xFFEFF3F8), Color(0xFF64748B))
        transaction.amount < 0 ->
            BankTransactionVisual(Icons.Default.ShoppingCart, Color(0xFFFEE2E2), Color(0xFFEF4444))
        else -> BankTransactionVisual(Icons.Default.AccountBalance, Color(0xFFEFF3F8), Color(0xFF64748B))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BankCompactTransactionRow(
    transaction: BankTransaction,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val visual = bankTransactionVisual(transaction)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color(0xFFEFF6FF) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
            Spacer(Modifier.size(4.dp))
        }
        Box(
            modifier = Modifier.size(42.dp).clip(CircleShape).background(visual.background),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                visual.icon,
                contentDescription = null,
                tint = visual.foreground,
                modifier = Modifier.size(23.dp)
            )
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                transaction.counterparty.ifBlank { "Unbekannter Zahlungspartner" },
                fontWeight = FontWeight.Bold,
                color = DarkNavy,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                BankCompactUiPolicy.compactSubtitle(transaction),
                fontSize = 11.sp,
                color = SlateGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BankPrimaryStatusBadge(transaction)
        }
        Spacer(Modifier.size(6.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                NumberFormatter.format(transaction.amount),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (transaction.amount >= 0) EmeraldGreen else DarkNavy,
                maxLines = 1
            )
            Text(formatDate(transaction.bookingDate), fontSize = 10.sp, color = SlateGray)
            Text(
                "›",
                fontSize = 21.sp,
                color = if (transaction.reconciliationStatus == BankReconciliationStatus.OPEN) AccentBlue else Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
private fun BankPrimaryStatusBadge(transaction: BankTransaction) {
    when (transaction.classification) {
        BankTransactionClassification.PRIVATE_IGNORED -> Text(
            "Privat",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = SlateGray
        )
        BankTransactionClassification.TRANSFER -> Text(
            "Umbuchung",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = AccentBlue
        )
        else -> BankStatusBadge(transaction.reconciliationStatus)
    }
    if (transaction.classification == BankTransactionClassification.NORMAL && transaction.reviewState == BankReviewState.DONE) {
        Text("Erledigt", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = SlateGray)
    }
}

@Composable
private fun BankTransactionDetailsScreen(
    viewModel: ReceiptViewModel,
    transaction: BankTransaction,
    account: BankAccount?,
    suggestion: BankMatchSuggestion?,
    lastMonthSuggestion: com.example.data.BankLastMonthAssignmentSuggestion?,
    transferSuggestion: com.example.data.BankTransferSuggestion?,
    suggestedTransferCounterpart: BankTransaction?,
    transferCounterpart: BankTransaction?,
    linkedLinks: List<BankReceiptLink>,
    assignments: List<com.example.data.BankRentAssignment>,
    receiptById: Map<Int, Receipt>,
    rentSuggestion: com.example.data.BankRentSuggestion?,
    units: List<WohneinheitStatus>,
    onBack: () -> Unit,
    onPickReceipt: () -> Unit,
    onNoReceipt: () -> Unit,
    onReceiptDetails: (Receipt) -> Unit
) {
    val linkedReceipts = linkedLinks.mapNotNull { link -> receiptById[link.receiptId]?.let { link to it } }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
                Text("Buchungsdetails", fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 19.sp, modifier = Modifier.weight(1f))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderColor)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val visual = bankTransactionVisual(transaction)
                        Box(Modifier.size(54.dp).clip(CircleShape).background(visual.background), contentAlignment = Alignment.Center) {
                            Icon(visual.icon, contentDescription = null, tint = visual.foreground, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(transaction.counterparty.ifBlank { "Unbekannter Zahlungspartner" }, fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 17.sp)
                            BankPrimaryStatusBadge(transaction)
                        }
                        Text(NumberFormatter.format(transaction.amount), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (transaction.amount >= 0) EmeraldGreen else DarkNavy)
                    }
                    HorizontalDivider()
                    BankDetailLine("Buchungsdatum", formatDate(transaction.bookingDate), Icons.Default.CalendarMonth)
                    if (transaction.valueDate.isNotBlank()) BankDetailLine("Valutadatum", formatDate(transaction.valueDate), Icons.Default.CalendarMonth)
                    if (transaction.purpose.isNotBlank()) BankDetailLine("Verwendungszweck", transaction.purpose, Icons.Default.Description)
                    if (transaction.bankReference.isNotBlank()) BankDetailLine("Referenz", transaction.bankReference, Icons.Default.Description)
                    BankDetailLine("Konto", buildString {
                        append(account?.displayName?.ifBlank { "Bankkonto" } ?: "Bankkonto")
                        account?.iban?.takeIf { it.isNotBlank() }?.let { append("\n").append(maskedIban(it)) }
                    }, Icons.Default.AccountBalance)
                }
            }
        }
        item {
            val labels = BankDetailActionPolicy.labels(transaction)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderColor)) {
                Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Was möchtest du tun?", fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFEAF4FF)).padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Schnell & einfach", color = AccentBlue, fontSize = 9.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BankActionCard(labels.privateAction, "Nicht in die Steuer übernehmen", Icons.Default.Person, Color(0xFFFFE8EC), CrimsonRed, Modifier.weight(1f)) {
                            if (transaction.classification == BankTransactionClassification.PRIVATE_IGNORED) viewModel.resetBankTransactionClassification(transaction.transactionId)
                            else viewModel.markBankTransactionPrivateIgnored(transaction.transactionId)
                        }
                        BankActionCard(labels.transferAction, "Zwischen eigenen Konten", Icons.Default.SwapHoriz, Color(0xFFE7F2FF), AccentBlue, Modifier.weight(1f)) {
                            if (transaction.classification == BankTransactionClassification.TRANSFER) viewModel.resetBankTransactionClassification(transaction.transactionId)
                            else viewModel.markBankTransactionTransfer(transaction.transactionId)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BankActionCard(labels.reviewAction, labels.reviewSubtitle, Icons.Default.CheckCircle, Color(0xFFE8F8ED), EmeraldGreen, Modifier.weight(1f)) {
                            if (transaction.reviewState == BankReviewState.DONE) viewModel.reopenBankTransactionReview(transaction.transactionId)
                            else viewModel.markBankTransactionReviewDone(transaction.transactionId)
                        }
                        BankActionCard("Manuell prüfen", "Später bearbeiten", Icons.Default.Visibility, Color(0xFFFFF6D8), Color(0xFFF2A900), Modifier.weight(1f)) {
                            viewModel.markBankTransactionForReview(transaction.transactionId)
                        }
                    }
                }
            }
        }

        if (transaction.classification == BankTransactionClassification.NORMAL) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderColor)) {
                Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Beleg zuordnen", fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 15.sp)
                    BankReceiptActionGrid(viewModel, transaction, onPickReceipt, onNoReceipt)
                    Text(
                        when {
                            linkedLinks.isNotEmpty() -> "Beleg zugeordnet."
                            suggestion != null -> "Passender Belegvorschlag gefunden."
                            else -> "Noch kein passender Beleg gefunden."
                        },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF1F5F9)).padding(10.dp),
                        color = SlateGray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (transaction.classification == BankTransactionClassification.NORMAL) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderColor)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Weitere Informationen", fontWeight = FontWeight.Bold, color = DarkNavy, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = SlateGray)
                }
            }
        }

        if (transaction.classification == BankTransactionClassification.TRANSFER) {
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

        if (lastMonthSuggestion != null) {
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
                                TextButton(onClick = { viewModel.proposeBankRuleFromConfirmedReceipt(transaction.transactionId, receipt.id) }) { Text("Regel merken") }
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

        if (assignments.isNotEmpty()) {
            item {
                BankTransactionSplitActions(viewModel = viewModel, transaction = transaction, showTrigger = false, showAssignments = true)
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
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
internal fun BankQuickAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    tint: Color = AccentBlue,
    containerColor: Color = Color(0xFFEFF6FF)
) {
    Card(
        modifier = modifier.heightIn(min = 86.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.fillMaxSize().padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(25.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold, color = DarkNavy, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
internal fun BankActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(modifier = modifier.heightIn(min = 86.dp).clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(25.dp))
            Spacer(Modifier.size(7.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 11.sp, lineHeight = 13.sp)
                Text(subtitle, color = SlateGray, fontSize = 9.sp, lineHeight = 11.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun BankReceiptActionGrid(
    viewModel: ReceiptViewModel,
    transaction: BankTransaction,
    onPickReceipt: () -> Unit,
    onNoReceipt: () -> Unit
) {
    val useFourColumns = BankDetailActionPolicy.useFourReceiptColumns(LocalDensity.current.fontScale)
    if (useFourColumns) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
            BankQuickAction("Beleg\nsuchen", Icons.Default.Search, Modifier.weight(1f), onClick = onPickReceipt)
            BankQuickAction("Beleg\nhochladen", Icons.Default.Description, Modifier.weight(1f), onClick = { viewModel.startReceiptFromBankTransaction(transaction) })
            Column(Modifier.weight(1f)) { BankTransactionSplitActions(viewModel, transaction, compactTrigger = true, showAssignments = false) }
            BankQuickAction("Kein Beleg\nerforderlich", Icons.Default.CheckCircle, Modifier.weight(1f), onClick = onNoReceipt, tint = EmeraldGreen, containerColor = Color(0xFFE8F8ED))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BankQuickAction("Beleg suchen", Icons.Default.Search, Modifier.weight(1f), onClick = onPickReceipt)
                BankQuickAction("Beleg hochladen / anlegen", Icons.Default.Description, Modifier.weight(1f), onClick = { viewModel.startReceiptFromBankTransaction(transaction) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) { BankTransactionSplitActions(viewModel, transaction, compactTrigger = true, showAssignments = false) }
                BankQuickAction("Kein Beleg erforderlich", Icons.Default.CheckCircle, Modifier.weight(1f), onClick = onNoReceipt, tint = EmeraldGreen, containerColor = Color(0xFFE8F8ED))
            }
        }
    }
}

@Composable
internal fun BankStatusBadge(status: String) {
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
            val amountColor = when (title) {
                "Eingänge" -> EmeraldGreen
                "Ausgänge" -> CrimsonRed
                "Saldo" -> AccentBlue
                else -> if (positiveStyle) EmeraldGreen else CrimsonRed
            }
            Text(NumberFormatter.format(amount), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = amountColor, maxLines = 1)
        }
    }
}

@Composable
private fun BankDetailLine(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(10.dp))
        Text(label, modifier = Modifier.width(126.dp), fontSize = 10.sp, lineHeight = 13.sp, color = SlateGray)
        Text(value, modifier = Modifier.weight(1f), fontSize = 11.sp, lineHeight = 14.sp, color = DarkNavy)
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
    onSelect: (Set<String>) -> Unit
) {
    val ranked = remember(receipt.id, transactions, links) { BankReceiptMatcher.rankTransactionsForReceipt(receipt, transactions, links) }
    var query by remember(receipt.id) { mutableStateOf("") }
    var selectedIds by remember(receipt.id) { mutableStateOf<Set<String>>(emptySet()) }
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
        title = { Text("Weitere Bankbuchungen verknüpfen") },
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = transaction.transactionId in selectedIds,
                                            onCheckedChange = { selectedIds = selectedIds.toggle(transaction.transactionId) }
                                        )
                                        Column {
                                            Text(transaction.counterparty.ifBlank { transaction.purpose.ifBlank { "Bankbuchung" } }, fontWeight = FontWeight.SemiBold)
                                            Text("${formatDate(transaction.bookingDate)} • ${NumberFormatter.format(transaction.amount)} • ${suggestion.score}%", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSelect(selectedIds) }, enabled = selectedIds.isNotEmpty()) {
                Text("${selectedIds.size} verknüpfen • ${NumberFormatter.format(transactions.filter { it.transactionId in selectedIds }.sumOf { it.absoluteAmount })}")
            }
        },
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

private fun maskedIban(iban: String): String {
    val compact = iban.replace(" ", "").trim()
    if (compact.length <= 8) return compact
    return compact.take(4) + " •••• •••• " + compact.takeLast(4)
}

private fun formatDate(value: String): String {
    val date = runCatching { LocalDate.parse(value) }.getOrNull() ?: return value
    return date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY))
}
