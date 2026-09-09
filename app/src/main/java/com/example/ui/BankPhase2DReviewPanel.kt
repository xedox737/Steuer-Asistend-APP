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
import com.example.data.BankBatchPreviewBuilder
import com.example.data.BankLinkStatus
import com.example.data.BankPhase2DReviewAction
import com.example.data.BankPhase2DReviewActionPolicy
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
    var receiptPickerItem by remember { mutableStateOf<BankReviewItem?>(null) }
    var noReceiptItem by remember { mutableStateOf<BankReviewItem?>(null) }
    var editLinkId by remember { mutableStateOf<String?>(null) }
    var editAmount by remember { mutableStateOf("") }
    var showRentWorkflow by remember { mutableStateOf(false) }
    var showPhase2CWorkflow by remember { mutableStateOf(false) }

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
    val preview = remember(queue, transactions, receipts, links) {
        BankBatchPreviewBuilder.build(queue, transactions, receipts, links)
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderColor)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Intelligente Prüfwarteschlange", fontWeight = FontWeight.Bold, color = DarkNavy)
                Text("Sammelzahlungen, Teilzahlungen und Konflikte werden transparent aus bestehenden Bank-/Belegdaten abgeleitet.", fontSize = 12.sp, color = SlateGray)
                Text("${queue.size} Prüfpunkt(e) • ${preview.caseCount} sicher batchfähig", fontSize = 12.sp)
                Text("Keine automatische DATEV-, Steuer- oder Zahlungsaktion.", fontSize = 11.sp, color = SlateGray)
                Button(onClick = { showBatchPreview = true }, enabled = preview.caseCount > 0, modifier = Modifier.fillMaxWidth()) {
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
            val existing = links.filter { it.transactionId in item.transactionIds || it.receiptId in item.receiptIds }
            val actions = BankPhase2DReviewActionPolicy.actionsFor(item.type, existing.isNotEmpty())
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
                        Text("Fall: ${item.stableKey}", fontSize = 10.sp, color = SlateGray)
                        val txObjects = item.transactionIds.mapNotNull { id -> transactions.firstOrNull { it.transactionId == id } }
                        txObjects.forEach { tx ->
                            Text("Bank: ${tx.bookingDate} • ${tx.counterparty.ifBlank { tx.purpose }} • ${NumberFormatter.format(tx.amount)}", fontSize = 11.sp)
                            if (tx.propertyId.isNotBlank()) Text("Bank-Property: ${tx.propertyId}", fontSize = 10.sp, color = SlateGray)
                        }
                        val receiptObjects = item.receiptIds.mapNotNull { id -> receipts.firstOrNull { it.id == id } }
                        receiptObjects.forEach { receipt ->
                            Text("Beleg ${receipt.id}: ${receipt.aussteller} • ${NumberFormatter.format(receipt.bruttobetrag)}", fontSize = 11.sp)
                            if (receipt.propertyId.isNotBlank()) Text("Beleg-Property: ${receipt.propertyId}", fontSize = 10.sp, color = SlateGray)
                        }
                        if (item.type == BankReviewType.AMOUNT_CONFLICT && txObjects.size == 1 && receiptObjects.size == 1) {
                            val diff = kotlin.math.abs(txObjects.single().absoluteAmount - receiptObjects.single().bruttobetrag)
                            Text("Betragsdifferenz: ${NumberFormatter.format(diff)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                        if (item.reasons.isNotEmpty()) Text("Gründe: ${item.reasons.joinToString(" • ")}", fontSize = 11.sp)
                        if (existing.isNotEmpty()) {
                            Text("Bestehende Links", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            existing.forEach { link ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${link.transactionId.take(8)} → Beleg ${link.receiptId}: ${NumberFormatter.format(link.allocatedAmount)}", fontSize = 11.sp)
                                    if (BankPhase2DReviewAction.UNLINK in actions) {
                                        TextButton(onClick = { viewModel.unlinkPhase2DLink(link.linkId) }) { Text("Lösen") }
                                    }
                                }
                                if (BankPhase2DReviewAction.EDIT_ALLOCATION in actions) {
                                    TextButton(onClick = { editLinkId = link.linkId; editAmount = link.allocatedAmount.toString() }) { Text("Teilbetrag ändern") }
                                }
                            }
                        }

                        if (BankPhase2DReviewAction.CONFIRM_COMBINATION in actions) {
                            val suggestion = combinations.firstOrNull { it.transactionIds.toSet() == item.transactionIds.toSet() && it.receiptIds.toSet() == item.receiptIds.toSet() && it.conflicts.isEmpty() }
                            if (suggestion != null) {
                                Text("Allocations: ${suggestion.allocations.joinToString { "${it.transactionId.take(8)}→${it.receiptId}: ${NumberFormatter.format(it.amount)}" }}", fontSize = 11.sp)
                                Text("Confidence ${suggestion.confidence} • Score ${suggestion.score}%", fontSize = 11.sp)
                                if (suggestion.reasons.isNotEmpty()) Text("Vorschlagsgründe: ${suggestion.reasons.joinToString(" • ")}", fontSize = 11.sp)
                                Button(onClick = { viewModel.confirmPhase2DCombination(suggestion.suggestionId) }) { Text("Kombination ausdrücklich bestätigen") }
                            } else if (item.type == BankReviewType.COMBINATION_SUGGESTION) {
                                Text("Konfliktbehaftete oder nicht eindeutige Kombination kann nicht direkt bestätigt werden.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (BankPhase2DReviewAction.CONFIRM_SAFE in actions && item.transactionIds.size == 1 && item.receiptIds.size == 1) {
                            Button(onClick = { viewModel.confirmBankReceiptLink(item.transactionIds.single(), item.receiptIds.single()) }) { Text("Vorschlag bestätigen") }
                        }
                        if (BankPhase2DReviewAction.CREATE_RECEIPT in actions) {
                            val tx = txObjects.firstOrNull()
                            if (tx != null) Button(onClick = { viewModel.startReceiptFromBankTransaction(tx) }) { Text("Beleg scannen/importieren") }
                        }
                        if (BankPhase2DReviewAction.OPEN_RECEIPT_PICKER in actions && item.transactionIds.isNotEmpty()) {
                            OutlinedButton(onClick = { receiptPickerItem = item }) { Text(if (item.type == BankReviewType.PARTIAL_PAYMENT) "Weiteren Beleg hinzufügen/auswählen" else "Anderen Beleg wählen") }
                        }
                        if (BankPhase2DReviewAction.NO_RECEIPT_REQUIRED in actions) {
                            TextButton(onClick = { noReceiptItem = item }) { Text("Kein Beleg erforderlich …") }
                        }
                        if (BankPhase2DReviewAction.OPEN_RENT_WORKFLOW in actions) {
                            OutlinedButton(onClick = { showRentWorkflow = true }) { Text("Im bestehenden Mietabgleich prüfen") }
                        }
                        if (BankPhase2DReviewAction.OPEN_LOAN_WORKFLOW in actions) {
                            OutlinedButton(onClick = { showPhase2CWorkflow = true }) { Text("Im bestehenden Darlehensworkflow prüfen") }
                        }
                        if (BankPhase2DReviewAction.OPEN_RECURRING_WORKFLOW in actions) {
                            OutlinedButton(onClick = { showPhase2CWorkflow = true }) { Text("Im bestehenden Recurring-Workflow prüfen") }
                        }
                        if (BankPhase2DReviewAction.MARK_MANUAL_REVIEW in actions) {
                            item.transactionIds.firstOrNull()?.let { txId ->
                                TextButton(onClick = { viewModel.markBankTransactionForReview(txId) }) { Text("Manuell prüfen / zurückstellen") }
                            }
                        }
                        if (item.type == BankReviewType.POSSIBLE_DUPLICATE) {
                            Text("Keine automatische Löschung, Zusammenführung oder Batch-Bestätigung.", fontSize = 11.sp, color = SlateGray)
                        }
                    }
                }
            }
        }

        if (showRentWorkflow) {
            Card(border = BorderStroke(1.dp, BorderColor)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Bestehender Phase-2B-Mietworkflow", fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showRentWorkflow = false }) { Text("Schließen") }
                    }
                    BankRentPanel(viewModel)
                }
            }
        }
        if (showPhase2CWorkflow) {
            Card(border = BorderStroke(1.dp, BorderColor)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Bestehender Phase-2C-Workflow", fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showPhase2CWorkflow = false }) { Text("Schließen") }
                    }
                    BankPhase2CPanel(viewModel)
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
                    Text("${preview.caseCount} sichere Fälle • ${preview.transactionCount} Transaktionen • ${preview.receiptCount} Belege")
                    Text("Gesamtbetrag: ${NumberFormatter.format(preview.totalAmount)}", fontWeight = FontWeight.Bold)
                    preview.cases.forEach { row ->
                        Card(border = BorderStroke(1.dp, BorderColor)) {
                            Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Fall ${row.stableKey}", fontSize = 10.sp, color = SlateGray)
                                Text("${row.bookingDate} • ${row.counterpartyOrPurpose}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text("Transaktion ${row.transactionId.take(12)} • ${NumberFormatter.format(row.transactionAmount)} • offen ${NumberFormatter.format(row.transactionRemaining)}", fontSize = 10.sp)
                                Text("Beleg ${row.receiptId} • ${row.receiptIssuer} • ${NumberFormatter.format(row.receiptAmount)} • offen ${NumberFormatter.format(row.receiptRemaining)}", fontSize = 10.sp)
                                Text("Allocation ${NumberFormatter.format(row.allocationAmount)} • ${row.confidence} • Score ${row.score}%", fontSize = 10.sp)
                            }
                        }
                    }
                    if (preview.excludedCases.isNotEmpty()) {
                        Text("Ausgeschlossen (${preview.excludedCases.size})", fontWeight = FontWeight.SemiBold)
                        preview.excludedCases.forEach { row ->
                            Text("• ${row.stableKey}: ${row.reason} [${row.confidence}, Score ${row.score}%]${if (row.conflicts.isNotEmpty()) " • ${row.conflicts.joinToString()}" else ""}", fontSize = 10.sp, color = SlateGray)
                        }
                    }
                    Text("Ausgeschlossene Fälle werden nicht ausgeführt. Die Eligibility wird unmittelbar vor Ausführung erneut domainseitig geprüft. Keine DATEV-Freigabe.", fontSize = 10.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.executePhase2DSafeBatch(preview.eligibleKeys); showBatchPreview = false },
                    enabled = preview.eligibleKeys.isNotEmpty()
                ) { Text("Jetzt ausdrücklich bestätigen") }
            },
            dismissButton = { TextButton(onClick = { showBatchPreview = false }) { Text("Abbrechen") } }
        )
    }

    receiptPickerItem?.let { item ->
        val txId = item.transactionIds.firstOrNull()
        val tx = txId?.let { id -> transactions.firstOrNull { it.transactionId == id } }
        val candidates = if (tx == null) emptyList() else receipts.filter { receipt ->
            val hasRoom = links.filter { it.receiptId == receipt.id && it.status == BankLinkStatus.CONFIRMED }.sumOf { it.allocatedAmount } < receipt.bruttobetrag - 0.01
            val propertyCompatible = tx.propertyId.isBlank() || receipt.propertyId.isBlank() || tx.propertyId == receipt.propertyId
            hasRoom && propertyCompatible
        }.take(30)
        AlertDialog(
            onDismissRequest = { receiptPickerItem = null },
            title = { Text("Passenden Beleg wählen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (candidates.isEmpty()) Text("Keine passenden offenen Belege vorhanden.")
                    candidates.take(12).forEach { receipt ->
                        TextButton(onClick = {
                            txId?.let { viewModel.confirmBankReceiptLink(it, receipt.id) }
                            receiptPickerItem = null
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("${receipt.aussteller} • ${NumberFormatter.format(receipt.bruttobetrag)} • ${receipt.datum}")
                        }
                    }
                }
            },
            confirmButton = {}, dismissButton = { TextButton(onClick = { receiptPickerItem = null }) { Text("Schließen") } }
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
