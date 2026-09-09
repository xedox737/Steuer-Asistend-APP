package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankBatchEligibility
import com.example.data.BankReviewItem
import com.example.data.BankReviewType

enum class Phase2DReviewFilter(val label: String) {
    ALL("Alle"), SAFE("Sicher"), MISSING("Fehlende Belege"), CONFLICTS("Konflikte"), PARTIAL("Teilzahlungen"),
    COMBINATIONS("Sammelzahlungen"), RENT("Mieten"), LOAN("Darlehen"), RECURRING("Wiederkehrend"), MANUAL("Manuell")
}

enum class Phase2DReviewSort(val label: String) { PRIORITY("Priorität"), DATE("Datum"), AMOUNT("Betrag"), CONFIDENCE("Confidence") }

@Composable
fun BankPhase2DReviewPanel(viewModel: ReceiptViewModel) {
    val queue by viewModel.bankPhase2DReviewQueue.collectAsState()
    val combinations by viewModel.bankPhase2DCombinations.collectAsState()
    val transactions by viewModel.bankTransactions.collectAsState()
    val receipts by viewModel.receipts.collectAsState()
    val links by viewModel.bankReceiptLinks.collectAsState()
    var filter by remember { mutableStateOf(Phase2DReviewFilter.ALL) }
    var sort by remember { mutableStateOf(Phase2DReviewSort.PRIORITY) }
    var expandedKey by remember { mutableStateOf<String?>(null) }
    var showBatchPreview by remember { mutableStateOf(false) }
    var missingReceiptItem by remember { mutableStateOf<BankReviewItem?>(null) }
    var noReceiptItem by remember { mutableStateOf<BankReviewItem?>(null) }
    var editLinkId by remember { mutableStateOf<String?>(null) }
    var editAmount by remember { mutableStateOf("") }

    val filtered = remember(queue, filter, sort) {
        val base = queue.filter { item ->
            when (filter) {
                Phase2DReviewFilter.ALL -> true
                Phase2DReviewFilter.SAFE -> item.type == BankReviewType.SAFE_SUGGESTION
                Phase2DReviewFilter.MISSING -> item.type == BankReviewType.MISSING_RECEIPT
                Phase2DReviewFilter.CONFLICTS -> item.conflicts.isNotEmpty() || item.type in setOf(BankReviewType.MULTIPLE_CANDIDATES, BankReviewType.AMOUNT_CONFLICT, BankReviewType.PROPERTY_CONFLICT, BankReviewType.POSSIBLE_DUPLICATE)
                Phase2DReviewFilter.PARTIAL -> item.type == BankReviewType.PARTIAL_PAYMENT
                Phase2DReviewFilter.COMBINATIONS -> item.type == BankReviewType.COMBINATION_SUGGESTION
                Phase2DReviewFilter.RENT -> item.type == BankReviewType.RENT_REVIEW
                Phase2DReviewFilter.LOAN -> item.type == BankReviewType.LOAN_REVIEW
                Phase2DReviewFilter.RECURRING -> item.type == BankReviewType.RECURRING_REVIEW
                Phase2DReviewFilter.MANUAL -> item.type == BankReviewType.MANUAL_REVIEW
            }
        }
        when (sort) {
            Phase2DReviewSort.PRIORITY -> base.sortedWith(compareByDescending<BankReviewItem> { it.priority }.thenByDescending { it.bookingDate }.thenBy { it.stableKey })
            Phase2DReviewSort.DATE -> base.sortedWith(compareByDescending<BankReviewItem> { it.bookingDate }.thenByDescending { it.priority })
            Phase2DReviewSort.AMOUNT -> base.sortedByDescending { it.remainingAmount }
            Phase2DReviewSort.CONFIDENCE -> base.sortedWith(compareByDescending<BankReviewItem> { it.score }.thenByDescending { it.priority })
        }
    }
    val preview = remember(queue) { BankBatchEligibility.preview(queue) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderColor)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Intelligente Prüfwarteschlange", fontWeight = FontWeight.Bold, color = DarkNavy)
                Text("Sammelzahlungen, Teilzahlungen und Konflikte werden transparent aus bestehenden Bank-/Belegdaten abgeleitet.", fontSize = 12.sp, color = SlateGray)
                Text("${queue.size} Prüfpunkt(e) • ${preview.eligibleKeys.size} sicher batchfähig", fontSize = 12.sp)
                Text("Keine automatische DATEV-, Steuer- oder Zahlungsaktion.", fontSize = 11.sp, color = SlateGray)
                Button(onClick = { showBatchPreview = true }, enabled = preview.eligibleKeys.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text("Sichere Fälle als Batch prüfen")
                }
            }
        }

        Text("Filter", fontWeight = FontWeight.SemiBold, color = DarkNavy)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Phase2DReviewFilter.entries) { item -> FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item.label) }) }
        }
        Text("Sortierung", fontWeight = FontWeight.SemiBold, color = DarkNavy)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(Phase2DReviewSort.entries) { item -> FilterChip(selected = sort == item, onClick = { sort = item }, label = { Text(item.label) }) }
        }

        if (filtered.isEmpty()) Text("Für diesen Filter gibt es aktuell nichts zu prüfen.", color = SlateGray, modifier = Modifier.padding(8.dp))

        filtered.forEach { item ->
            val expanded = expandedKey == item.stableKey
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderColor)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(item.title, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text("Priorität ${item.priority} • ${item.confidence} • Score ${item.score}%", fontSize = 12.sp)
                    Text("Betrag ${NumberFormatter.format(item.amount)} • Rest ${NumberFormatter.format(item.remainingAmount)}", fontSize = 12.sp)
                    if (item.propertyId.isNotBlank()) Text("Immobilie: ${item.propertyId}", fontSize = 11.sp, color = SlateGray)
                    if (item.conflicts.isNotEmpty()) Text("Konflikte: ${item.conflicts.joinToString()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { expandedKey = if (expanded) null else item.stableKey }) { Text(if (expanded) "Details schließen" else "Details") }
                    if (expanded) {
                        Text(item.explanation, fontSize = 12.sp)
                        val txLabels = item.transactionIds.mapNotNull { id -> transactions.firstOrNull { it.transactionId == id }?.let { "${it.bookingDate} • ${it.counterparty.ifBlank { it.purpose }} • ${NumberFormatter.format(it.amount)}" } }
                        if (txLabels.isNotEmpty()) Text("Bank: ${txLabels.joinToString(" | ")}", fontSize = 11.sp)
                        val receiptLabels = item.receiptIds.mapNotNull { id -> receipts.firstOrNull { it.id == id }?.let { "${it.aussteller} • ${NumberFormatter.format(it.bruttobetrag)}" } }
                        if (receiptLabels.isNotEmpty()) Text("Beleg(e): ${receiptLabels.joinToString(" | ")}", fontSize = 11.sp)
                        if (item.reasons.isNotEmpty()) Text("Gründe: ${item.reasons.joinToString(" • ")}", fontSize = 11.sp)
                        val existing = links.filter { it.transactionId in item.transactionIds || it.receiptId in item.receiptIds }
                        if (existing.isNotEmpty()) {
                            Text("Bestehende Links", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            existing.forEach { link ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${link.transactionId.take(8)} → Beleg ${link.receiptId}: ${NumberFormatter.format(link.allocatedAmount)}", fontSize = 11.sp)
                                    TextButton(onClick = { viewModel.unlinkPhase2DLink(link.linkId) }) { Text("Lösen") }
                                }
                                TextButton(onClick = { editLinkId = link.linkId; editAmount = link.allocatedAmount.toString() }) { Text("Teilbetrag ändern") }
                            }
                        }
                        when (item.type) {
                            BankReviewType.COMBINATION_SUGGESTION -> {
                                val suggestion = combinations.firstOrNull { it.transactionIds.toSet() == item.transactionIds.toSet() && it.receiptIds.toSet() == item.receiptIds.toSet() && it.conflicts.isEmpty() }
                                if (suggestion != null) {
                                    Text("Vorgeschlagene Allocations: ${suggestion.allocations.joinToString { "${it.transactionId.take(8)}→${it.receiptId}: ${NumberFormatter.format(it.amount)}" }}", fontSize = 11.sp)
                                    Button(onClick = { viewModel.confirmPhase2DCombination(suggestion.suggestionId) }) { Text("Kombination bestätigen") }
                                }
                            }
                            BankReviewType.SAFE_SUGGESTION -> if (item.transactionIds.size == 1 && item.receiptIds.size == 1) {
                                Button(onClick = { viewModel.confirmBankReceiptLink(item.transactionIds.single(), item.receiptIds.single()) }) { Text("Vorschlag bestätigen") }
                            }
                            BankReviewType.MISSING_RECEIPT -> {
                                val tx = transactions.firstOrNull { it.transactionId == item.transactionIds.firstOrNull() }
                                if (tx != null) Button(onClick = { viewModel.startReceiptFromBankTransaction(tx) }) { Text("Beleg scannen/importieren") }
                                OutlinedButton(onClick = { missingReceiptItem = item }) { Text("Vorhandenen Beleg wählen") }
                                TextButton(onClick = { noReceiptItem = item }) { Text("Kein Beleg erforderlich …") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBatchPreview) {
        AlertDialog(
            onDismissRequest = { showBatchPreview = false },
            title = { Text("Batch-Vorschau") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Sichere Fälle: ${preview.eligibleKeys.size}")
                    Text("Transaktionen: ${preview.transactionIds.size} • Belege: ${preview.receiptIds.size}")
                    Text("Gesamtbetrag: ${NumberFormatter.format(preview.totalAmount)}")
                    if (preview.excludedKeys.isNotEmpty()) Text("Ausgeschlossen: ${preview.excludedKeys.size}")
                    if (preview.warnings.isNotEmpty()) Text(preview.warnings.take(5).joinToString("\n"), fontSize = 11.sp, color = SlateGray)
                    Text("Nur die oben als sicher ausgewiesenen 1:1-Zuordnungen werden verknüpft. Keine DATEV-Freigabe.", fontSize = 11.sp)
                }
            },
            confirmButton = { Button(onClick = { viewModel.executePhase2DSafeBatch(preview.eligibleKeys); showBatchPreview = false }) { Text("Jetzt ausdrücklich bestätigen") } },
            dismissButton = { TextButton(onClick = { showBatchPreview = false }) { Text("Abbrechen") } }
        )
    }

    missingReceiptItem?.let { item ->
        val candidates = receipts.filter { receipt -> links.none { it.receiptId == receipt.id && it.status == com.example.data.BankLinkStatus.CONFIRMED } }.take(30)
        AlertDialog(
            onDismissRequest = { missingReceiptItem = null },
            title = { Text("Vorhandenen Beleg wählen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (candidates.isEmpty()) Text("Keine offenen Belege vorhanden.")
                    candidates.take(12).forEach { receipt ->
                        TextButton(onClick = {
                            item.transactionIds.firstOrNull()?.let { viewModel.confirmBankReceiptLink(it, receipt.id) }
                            missingReceiptItem = null
                        }, modifier = Modifier.fillMaxWidth()) { Text("${receipt.aussteller} • ${NumberFormatter.format(receipt.bruttobetrag)}") }
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { missingReceiptItem = null }) { Text("Schließen") } }
        )
    }

    noReceiptItem?.let { item ->
        val reasons = listOf("Eigene Umbuchung", "Privat", "Bankgebühr", "Sonstiges")
        AlertDialog(
            onDismissRequest = { noReceiptItem = null },
            title = { Text("Kein Beleg erforderlich") },
            text = { Column { reasons.forEach { reason -> TextButton(onClick = { item.transactionIds.firstOrNull()?.let { viewModel.markBankTransactionNoReceiptRequired(it, reason) }; noReceiptItem = null }) { Text(reason) } } } },
            confirmButton = {}, dismissButton = { TextButton(onClick = { noReceiptItem = null }) { Text("Abbrechen") } }
        )
    }

    editLinkId?.let { linkId ->
        AlertDialog(
            onDismissRequest = { editLinkId = null },
            title = { Text("Teilbetrag ändern") },
            text = { OutlinedTextField(value = editAmount, onValueChange = { editAmount = it }, label = { Text("Allocation in €") }, singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    editAmount.replace(',', '.').toDoubleOrNull()?.let { viewModel.changePhase2DAllocation(linkId, it) }
                    editLinkId = null
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { editLinkId = null }) { Text("Abbrechen") } }
        )
    }
}
