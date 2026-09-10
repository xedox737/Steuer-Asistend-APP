package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankAllocationPolicy
import com.example.data.BankManualSplitPosition
import com.example.data.BankReconciliationStatus
import com.example.data.BankRentAssignment
import com.example.data.BankSplitPaymentType
import com.example.data.BankTransaction
import com.example.data.BankTransactionSplitPolicy
import com.example.data.Receipt
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

private data class BankSplitDraft(
    val amount: String = "",
    val paymentType: String = BankSplitPaymentType.RENT,
    val propertyId: String = "",
    val unitId: String = "",
    val tenant: String = "",
    val note: String = "",
    val receiptId: Int? = null
)

@Composable
fun BankTransactionSplitActions(
    viewModel: ReceiptViewModel,
    transaction: BankTransaction,
    compactTrigger: Boolean = false,
    showTrigger: Boolean = true,
    showAssignments: Boolean = true
) {
    val links by viewModel.bankReceiptLinks.collectAsState()
    val assignments by viewModel.bankRentAssignments.collectAsState()
    val receipts by viewModel.receipts.collectAsState()
    val units by viewModel.wohneinheitenStatus.collectAsState()
    val property by viewModel.propertyMetadata.collectAsState()
    val transactionAssignments = assignments.filter { it.transactionId == transaction.transactionId }
    var showDialog by remember(transaction.transactionId) { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BankRentAssignment?>(null) }
    var editAmount by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val euro = remember { NumberFormat.getCurrencyInstance(Locale.GERMANY) }

    if (showTrigger && (transaction.reconciliationStatus == BankReconciliationStatus.OPEN || transaction.reconciliationStatus == BankReconciliationStatus.PARTIAL)) {
        if (compactTrigger) {
            Card(
                modifier = Modifier.fillMaxWidth().height(78.dp).clickable { showDialog = true },
                colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(
                    Modifier.fillMaxSize().padding(5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.CallSplit, contentDescription = "Buchung aufteilen", tint = AccentBlue, modifier = Modifier.size(21.dp))
                    Text("Buchung\naufteilen", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = DarkNavy, textAlign = TextAlign.Center)
                }
            }
        } else {
            OutlinedButton(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Buchung aufteilen") }
        }
    }

    if (showAssignments && transactionAssignments.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Bestehende Aufteilung", fontWeight = FontWeight.SemiBold, color = DarkNavy)
                transactionAssignments.forEach { assignment ->
                    Text("${euro.format(assignment.allocatedAmount)} → ${BankSplitPaymentType.label(assignment.paymentType)}", color = DarkNavy)
                    if (assignment.propertyId.isNotBlank()) Text("Immobilie: ${assignment.propertyId}${assignment.unitId.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()}", color = SlateGray)
                    if (assignment.tenantReference.isNotBlank()) Text("Mieter/Mietverhältnis: ${assignment.tenantReference}", color = SlateGray)
                    if (assignment.note.isNotBlank()) Text("Notiz: ${assignment.note}", color = SlateGray)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { editing = assignment; editAmount = assignment.allocatedAmount.toString().replace('.', ',') }) { Text("Teilbetrag ändern") }
                        TextButton(onClick = { scope.launch { viewModel.bankPhase2DServiceForUi().unlinkManualSplitAssignment(assignment.assignmentId) } }) { Text("Zuordnung lösen") }
                    }
                }
                val txLinks = links.filter { it.transactionId == transaction.transactionId }
                val allocated = BankTransactionSplitPolicy.allocatedAmount(transaction.transactionId, txLinks, transactionAssignments)
                Text("Gesamt zugeordnet: ${euro.format(allocated)} • Rest: ${euro.format((transaction.absoluteAmount - allocated).coerceAtLeast(0.0))}", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showDialog) {
        BankTransactionSplitDialog(
            transaction = transaction,
            links = links.filter { it.transactionId == transaction.transactionId },
            assignments = transactionAssignments,
            receipts = receipts,
            units = units,
            defaultPropertyId = transaction.propertyId.ifBlank { property?.propertyId.orEmpty() },
            onDismiss = { showDialog = false },
            onConfirm = { positions ->
                scope.launch {
                    val result = viewModel.bankPhase2DServiceForUi().confirmManualSplit(positions, true)
                    if (result.success) showDialog = false
                }
            }
        )
    }

    editing?.let { assignment ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Teilbetrag ändern") },
            text = { OutlinedTextField(value = editAmount, onValueChange = { editAmount = it }, label = { Text("Betrag") }) },
            confirmButton = {
                Button(onClick = {
                    val amount = editAmount.replace(',', '.').toDoubleOrNull()
                    if (amount != null) scope.launch {
                        val result = viewModel.bankPhase2DServiceForUi().changeManualSplitAssignment(assignment.assignmentId, amount, true)
                        if (result.success) editing = null
                    }
                }) { Text("Änderung bestätigen") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun BankTransactionSplitDialog(
    transaction: BankTransaction,
    links: List<com.example.data.BankReceiptLink>,
    assignments: List<BankRentAssignment>,
    receipts: List<Receipt>,
    units: List<WohneinheitStatus>,
    defaultPropertyId: String,
    onDismiss: () -> Unit,
    onConfirm: (List<BankManualSplitPosition>) -> Unit
) {
    var drafts by remember(transaction.transactionId) {
        mutableStateOf(listOf(BankSplitDraft(propertyId = defaultPropertyId, unitId = transaction.unitId, tenant = if (transaction.isIncome) transaction.counterparty else "")))
    }
    var previewMode by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf<Int?>(null) }
    var unitMenu by remember { mutableStateOf<Int?>(null) }
    var receiptMenu by remember { mutableStateOf<Int?>(null) }
    val euro = remember { NumberFormat.getCurrencyInstance(Locale.GERMANY) }

    fun positions(): List<BankManualSplitPosition> = drafts.map { d ->
        BankManualSplitPosition(
            transactionId = transaction.transactionId,
            amount = d.amount.replace(',', '.').toDoubleOrNull() ?: Double.NaN,
            paymentType = d.paymentType,
            propertyId = d.propertyId.trim(),
            unitId = d.unitId.trim(),
            tenantReference = d.tenant.trim(),
            rentMonth = transaction.bookingDate.take(7),
            note = d.note.trim(),
            receiptId = d.receiptId
        )
    }
    val preview = BankTransactionSplitPolicy.preview(transaction, links, assignments, positions())
    val already = BankTransactionSplitPolicy.allocatedAmount(transaction.transactionId, links, assignments)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (previewMode) "Aufteilung prüfen" else "Buchung aufteilen") },
        text = {
            LazyColumn(Modifier.heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("Buchungsdatum: ${transaction.bookingDate}")
                    Text("Gegenpartei: ${transaction.counterparty.ifBlank { "–" }}")
                    Text("Verwendungszweck: ${transaction.purpose.ifBlank { "–" }}")
                    Text("Originalbetrag: ${euro.format(transaction.absoluteAmount)}", fontWeight = FontWeight.Bold)
                    Text("Bereits zugeordnet: ${euro.format(already)}")
                    Text("Verbleibender Rest vor Aufteilung: ${euro.format((transaction.absoluteAmount - already).coerceAtLeast(0.0))}")
                }
                items(drafts.indices.toList()) { index ->
                    val draft = drafts[index]
                    Card(border = BorderStroke(1.dp, BorderColor)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("Teilposition ${index + 1}", fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(value = draft.amount, onValueChange = { value -> drafts = drafts.toMutableList().also { it[index] = draft.copy(amount = value) } }, label = { Text("Betrag") }, enabled = !previewMode)
                            OutlinedButton(onClick = { if (!previewMode) categoryMenu = index }, enabled = !previewMode) { Text("Kategorie: ${BankSplitPaymentType.label(draft.paymentType)}") }
                            DropdownMenu(expanded = categoryMenu == index, onDismissRequest = { categoryMenu = null }) {
                                BankSplitPaymentType.all.filter { BankSplitPaymentType.directionAllowed(it, transaction) }.forEach { type ->
                                    DropdownMenuItem(text = { Text(BankSplitPaymentType.label(type)) }, onClick = {
                                        drafts = drafts.toMutableList().also { it[index] = draft.copy(paymentType = type, receiptId = if (type == BankSplitPaymentType.RECEIPT) draft.receiptId else null) }
                                        categoryMenu = null
                                    })
                                }
                            }
                            OutlinedTextField(value = draft.propertyId, onValueChange = { value -> drafts = drafts.toMutableList().also { it[index] = draft.copy(propertyId = value) } }, label = { Text("Immobilie") }, enabled = !previewMode)
                            if (draft.paymentType in BankSplitPaymentType.rentScoped) {
                                OutlinedButton(onClick = { if (!previewMode) unitMenu = index }, enabled = !previewMode) { Text("Wohneinheit: ${draft.unitId.ifBlank { "auswählen" }}") }
                                DropdownMenu(expanded = unitMenu == index, onDismissRequest = { unitMenu = null }) {
                                    units.forEach { unit -> DropdownMenuItem(text = { Text(unit.label.ifBlank { unit.name }) }, onClick = {
                                        drafts = drafts.toMutableList().also { it[index] = draft.copy(unitId = unit.unitId.ifBlank { unit.name }) }
                                        unitMenu = null
                                    }) }
                                }
                                OutlinedTextField(value = draft.tenant, onValueChange = { value -> drafts = drafts.toMutableList().also { it[index] = draft.copy(tenant = value) } }, label = { Text("Mieter / Mietverhältnis") }, enabled = !previewMode)
                            }
                            if (draft.paymentType == BankSplitPaymentType.RECEIPT) {
                                val selected = receipts.firstOrNull { it.id == draft.receiptId }
                                OutlinedButton(onClick = { if (!previewMode) receiptMenu = index }, enabled = !previewMode) { Text("Beleg: ${selected?.let { "${it.datum} • ${it.aussteller} • ${euro.format(it.bruttobetrag)}" } ?: "auswählen"}") }
                                DropdownMenu(expanded = receiptMenu == index, onDismissRequest = { receiptMenu = null }) {
                                    receipts.take(30).forEach { receipt -> DropdownMenuItem(text = { Text("${receipt.datum} • ${receipt.aussteller} • ${euro.format(receipt.bruttobetrag)}") }, onClick = {
                                        drafts = drafts.toMutableList().also { it[index] = draft.copy(receiptId = receipt.id, propertyId = draft.propertyId.ifBlank { receipt.propertyId }) }
                                        receiptMenu = null
                                    }) }
                                }
                            }
                            OutlinedTextField(value = draft.note, onValueChange = { value -> drafts = drafts.toMutableList().also { it[index] = draft.copy(note = value) } }, label = { Text("Notiz (optional)") }, enabled = !previewMode)
                            if (!previewMode && drafts.size > 1) TextButton(onClick = { drafts = drafts.toMutableList().also { it.removeAt(index) } }) { Text("Position entfernen") }
                        }
                    }
                }
                if (!previewMode) item { OutlinedButton(onClick = { drafts = drafts + BankSplitDraft(propertyId = defaultPropertyId, unitId = transaction.unitId) }, modifier = Modifier.fillMaxWidth()) { Text("+ Position hinzufügen") } }
                item {
                    Text("Gesamtbetrag: ${euro.format(preview.originalAmount)}", fontWeight = FontWeight.SemiBold)
                    Text("Neue Zuordnungen: ${euro.format(preview.newAllocations)}")
                    Text("Verbleibender Rest: ${euro.format(preview.remainingAmount)}", fontWeight = FontWeight.Bold)
                    Text("Status danach: ${preview.statusAfter}")
                    if (!preview.valid) Text(preview.message, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (previewMode) Button(onClick = { onConfirm(positions()) }, enabled = preview.valid) { Text("Aufteilung bestätigen") }
            else Button(onClick = { previewMode = true }, enabled = preview.valid) { Text("Aufteilung prüfen") }
        },
        dismissButton = {
            if (previewMode) TextButton(onClick = { previewMode = false }) { Text("Zurück") }
            else TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
