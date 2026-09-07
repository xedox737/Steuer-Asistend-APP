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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankMatchSuggestion
import com.example.data.BankReceiptLink
import com.example.data.BankReceiptMatcher
import com.example.data.BankReconciliationStatus
import com.example.data.BankTransaction
import com.example.data.Receipt

private enum class BankListFilter { REVIEW, MATCHED, ALL }

@Composable
fun BankScreen(viewModel: ReceiptViewModel) {
    val transactions by viewModel.bankTransactions.collectAsState()
    val accounts by viewModel.bankAccounts.collectAsState()
    val links by viewModel.bankReceiptLinks.collectAsState()
    val receipts by viewModel.receipts.collectAsState()
    val suggestions by viewModel.bankMatchSuggestions.collectAsState()
    val importStatus by viewModel.bankImportStatus.collectAsState()

    var filter by remember { mutableStateOf(BankListFilter.REVIEW) }
    var receiptPickerFor by remember { mutableStateOf<BankTransaction?>(null) }
    var noReceiptFor by remember { mutableStateOf<BankTransaction?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importBankFile(uri)
    }

    val reviewCount = transactions.count {
        it.reconciliationStatus in setOf(BankReconciliationStatus.OPEN, BankReconciliationStatus.PARTIAL, BankReconciliationStatus.REVIEW)
    }
    val matchedCount = transactions.count { it.reconciliationStatus == BankReconciliationStatus.MATCHED }
    val noReceiptCount = transactions.count { it.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED }
    val shown = when (filter) {
        BankListFilter.REVIEW -> transactions.filter {
            it.reconciliationStatus in setOf(BankReconciliationStatus.OPEN, BankReconciliationStatus.PARTIAL, BankReconciliationStatus.REVIEW)
        }
        BankListFilter.MATCHED -> transactions.filter {
            it.reconciliationStatus == BankReconciliationStatus.MATCHED ||
                it.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED
        }
        BankListFilter.ALL -> transactions
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BankSummaryCard("Zu prüfen", reviewCount.toString(), Modifier.weight(1f))
                BankSummaryCard("Zugeordnet", matchedCount.toString(), Modifier.weight(1f))
                BankSummaryCard("Ohne Beleg", noReceiptCount.toString(), Modifier.weight(1f))
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filter == BankListFilter.REVIEW, onClick = { filter = BankListFilter.REVIEW }, label = { Text("Zu prüfen") })
                FilterChip(selected = filter == BankListFilter.MATCHED, onClick = { filter = BankListFilter.MATCHED }, label = { Text("Erledigt") })
                FilterChip(selected = filter == BankListFilter.ALL, onClick = { filter = BankListFilter.ALL }, label = { Text("Alle") })
            }
        }

        if (shown.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Text(
                        if (transactions.isEmpty()) "Importiere zuerst einen CSV- oder CAMT.053-Kontoauszug." else "Hier ist aktuell nichts zu prüfen.",
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
                    onConfirmSuggestion = { suggestion ->
                        viewModel.confirmBankReceiptLink(transaction.transactionId, suggestion.receiptId)
                    },
                    onCreateReceipt = { viewModel.startReceiptFromBankTransaction(transaction) },
                    onPickReceipt = { receiptPickerFor = transaction },
                    onNoReceipt = { noReceiptFor = transaction }
                )
            }
        }

        val unlinkedReceipts = receipts.filter { receipt ->
            links.none { it.receiptId == receipt.id || (receipt.internalId.isNotBlank() && it.receiptInternalId == receipt.internalId) }
        }
        if (transactions.isNotEmpty() && unlinkedReceipts.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Text("Belege ohne Bankzuordnung", fontWeight = FontWeight.Bold, color = DarkNavy)
                Text("Auch andersherum: vom Beleg zur passenden Buchung.", fontSize = 12.sp, color = SlateGray)
            }
            items(unlinkedReceipts.take(20), key = { "receipt-${it.id}" }) { receipt ->
                val reverse = BankReceiptMatcher.bestForReceipt(receipt, transactions, links)
                ReverseReceiptCard(
                    receipt = receipt,
                    suggestion = reverse,
                    transaction = reverse?.transactionId?.let { txId -> transactions.firstOrNull { it.transactionId == txId } },
                    onConfirm = { txId -> viewModel.confirmBankReceiptLink(txId, receipt.id) }
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
    onConfirmSuggestion: (BankMatchSuggestion) -> Unit,
    onCreateReceipt: () -> Unit,
    onPickReceipt: () -> Unit,
    onNoReceipt: () -> Unit
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

            if (transaction.reconciliationStatus !in setOf(BankReconciliationStatus.MATCHED, BankReconciliationStatus.NO_RECEIPT_REQUIRED)) {
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
                OutlinedButton(onClick = onPickReceipt, modifier = Modifier.fillMaxWidth()) {
                    Text("Vorhandenen Beleg suchen")
                }
                OutlinedButton(onClick = onCreateReceipt, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  Beleg aus Buchung anlegen")
                }
                TextButton(onClick = onNoReceipt, modifier = Modifier.align(Alignment.End)) {
                    Text("Kein Beleg erforderlich")
                }
            } else if (transaction.reconciliationStatus == BankReconciliationStatus.MATCHED) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen)
                    Text("  Bestätigt verbunden", fontSize = 12.sp, color = EmeraldGreen)
                }
            } else {
                Text(transaction.noReceiptReason, fontSize = 12.sp, color = SlateGray)
            }
        }
    }
}

@Composable
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
