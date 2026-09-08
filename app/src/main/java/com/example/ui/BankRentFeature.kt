package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankRentAssignment
import com.example.data.BankRentAssignmentStatus
import com.example.data.BankRentMonthStatus
import java.time.YearMonth

private enum class RentUiFilter { OFFEN, BESTAETIGT, TEILZAHLUNG, KONFLIKT, UEBERZAHLUNG, ALLE }

@Composable
internal fun BankRentPanel(viewModel: ReceiptViewModel) {
    val suggestions by viewModel.bankRentSuggestions.collectAsState()
    val assignments by viewModel.bankRentAssignments.collectAsState()
    val transactions by viewModel.bankTransactions.collectAsState()
    var filter by remember { mutableStateOf(RentUiFilter.OFFEN) }
    var propertyFilter by remember { mutableStateOf("") }
    var unitFilter by remember { mutableStateOf("") }
    var monthFilter by remember { mutableStateOf("") }
    var partialFor by remember { mutableStateOf<BankRentSuggestion?>(null) }
    var manualFor by remember { mutableStateOf<BankRentSuggestion?>(null) }

    val flat = suggestions.values.flatten()
    val filtered = flat.filter { s ->
        (propertyFilter.isBlank() || s.propertyId == propertyFilter) &&
            (unitFilter.isBlank() || s.unitId == unitFilter) &&
            (monthFilter.isBlank() || s.rentMonth == monthFilter) &&
            when (filter) {
                RentUiFilter.OFFEN -> s.conflictState == RentConflictState.NONE && s.remainingAmount > 0.01
                RentUiFilter.TEILZAHLUNG -> s.remainingAmount > 0.01 && s.actualAmount < s.expectedAmount
                RentUiFilter.KONFLIKT -> s.conflictState != RentConflictState.NONE
                RentUiFilter.UEBERZAHLUNG -> s.conflictState == RentConflictState.OVERPAYMENT
                RentUiFilter.BESTAETIGT -> false
                RentUiFilter.ALLE -> true
            }
    }
    val confirmed = assignments.filter { it.status == BankRentAssignmentStatus.CONFIRMED }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Mietabgleich", fontSize = 17.sp, fontWeight = FontWeight.Black, color = DarkNavy)
        Text(
            "Bankeingänge werden nur vorgeschlagen. Erst deine Bestätigung erzeugt eine Mietzuordnung; DATEV, Steuer und Belege bleiben unverändert.",
            fontSize = 11.sp, color = SlateGray
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(filter == RentUiFilter.OFFEN, { filter = RentUiFilter.OFFEN }, label = { Text("Offen") })
            FilterChip(filter == RentUiFilter.TEILZAHLUNG, { filter = RentUiFilter.TEILZAHLUNG }, label = { Text("Teil") })
            FilterChip(filter == RentUiFilter.KONFLIKT, { filter = RentUiFilter.KONFLIKT }, label = { Text("Konflikte") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(filter == RentUiFilter.UEBERZAHLUNG, { filter = RentUiFilter.UEBERZAHLUNG }, label = { Text("Überzahlung") })
            FilterChip(filter == RentUiFilter.BESTAETIGT, { filter = RentUiFilter.BESTAETIGT }, label = { Text("Bestätigt") })
            FilterChip(filter == RentUiFilter.ALLE, { filter = RentUiFilter.ALLE }, label = { Text("Alle") })
        }
        OutlinedTextField(propertyFilter, { propertyFilter = it.trim() }, label = { Text("Objekt-ID Filter") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(unitFilter, { unitFilter = it.trim() }, label = { Text("Einheit-ID Filter") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(monthFilter, { monthFilter = it.trim() }, label = { Text("Monat YYYY-MM") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        if (filter == RentUiFilter.BESTAETIGT) {
            confirmed.forEach { assignment ->
                ConfirmedRentAssignmentCard(assignment) { viewModel.unlinkBankRentAssignment(assignment.assignmentId) }
            }
        } else if (filtered.isEmpty()) {
            Text("Für diesen Filter gibt es aktuell keine Mietvorschläge.", fontSize = 11.sp, color = SlateGray)
        } else {
            filtered.take(60).forEach { suggestion ->
                val tx = transactions.firstOrNull { it.transactionId == suggestion.transactionId }
                RentSuggestionCard(
                    suggestion = suggestion,
                    bookingDate = tx?.bookingDate.orEmpty(),
                    onConfirm = { viewModel.confirmBankRentSuggestion(suggestion, asPartial = false) },
                    onPartial = { partialFor = suggestion },
                    onManual = { manualFor = suggestion },
                    onNotRent = { viewModel.dismissBankRentSuggestion(suggestion.transactionId) },
                    onReview = { viewModel.markBankTransactionForReview(suggestion.transactionId) }
                )
            }
        }
    }

    partialFor?.let { suggestion ->
        PartialRentDialog(
            suggestion = suggestion,
            onDismiss = { partialFor = null },
            onConfirm = { amount ->
                viewModel.confirmBankRentSuggestion(suggestion, asPartial = true, allocatedAmount = amount)
                partialFor = null
            }
        )
    }
    manualFor?.let { suggestion ->
        ManualRentDialog(
            suggestion = suggestion,
            onDismiss = { manualFor = null },
            onConfirm = { propertyId, unitId, month, tenantRef, amount ->
                viewModel.confirmManualBankRentAssignment(
                    suggestion.transactionId, propertyId, unitId, month, tenantRef, amount
                )
                manualFor = null
            }
        )
    }
}

@Composable
private fun RentSuggestionCard(
    suggestion: BankRentSuggestion,
    bookingDate: String,
    onConfirm: () -> Unit,
    onPartial: () -> Unit,
    onManual: () -> Unit,
    onNotRent: () -> Unit,
    onReview: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, BorderColor), shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${suggestion.propertyLabel} · ${suggestion.unitName}", fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 12.sp)
                Text("${suggestion.score}/100 · ${suggestion.confidence}", fontWeight = FontWeight.Bold, color = AccentBlue, fontSize = 10.sp)
            }
            Text("Mieter: ${suggestion.tenantName.ifBlank { "nicht hinterlegt" }}", fontSize = 10.sp, color = SlateGray)
            Text("Mietmonat ${suggestion.rentMonth} · Buchung $bookingDate", fontSize = 10.sp, color = SlateGray)
            Text("Soll ${NumberFormatter.format(suggestion.expectedAmount)} · Ist ${NumberFormatter.format(suggestion.actualAmount)} · Rest ${NumberFormatter.format(suggestion.remainingAmount)}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = DarkNavy)
            Text("Differenz ${NumberFormatter.format(suggestion.difference)} · Typ ${suggestion.paymentType}", fontSize = 9.sp, color = SlateGray)
            if (suggestion.conflictState != RentConflictState.NONE) {
                Text("⚠ ${suggestion.conflictState}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WarmOrange)
            }
            suggestion.reasons.forEach { Text("• $it", fontSize = 9.sp, color = SlateGray) }
            HorizontalDivider(color = BorderColor)
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) { Text("Als Mietzahlung bestätigen") }
            OutlinedButton(onClick = onPartial, modifier = Modifier.fillMaxWidth()) { Text("Als Teilzahlung bestätigen") }
            OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) { Text("Andere Einheit/Mieter oder Mietmonat wählen") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onNotRent) { Text("Nicht als Miete") }
                TextButton(onClick = onReview) { Text("Manuell prüfen") }
            }
        }
    }
}

@Composable
private fun ConfirmedRentAssignmentCard(assignment: BankRentAssignment, onUnlink: () -> Unit) {
    Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, BorderColor), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${assignment.propertyId} · ${assignment.unitId} · ${assignment.rentMonth}", fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 11.sp)
            Text("Bestätigt: ${NumberFormatter.format(assignment.allocatedAmount)} · ${assignment.paymentType}", fontSize = 10.sp, color = EmeraldGreen)
            Text("Quelle: ${assignment.source}", fontSize = 9.sp, color = SlateGray)
            TextButton(onClick = onUnlink) { Text("Mietzuordnung lösen") }
        }
    }
}

@Composable
private fun PartialRentDialog(suggestion: BankRentSuggestion, onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var amount by remember(suggestion.transactionId) { mutableStateOf(minOf(suggestion.actualAmount, suggestion.expectedAmount).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Teilzahlung bestätigen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Offener Rest vor dieser Zahlung: ${NumberFormatter.format(suggestion.expectedAmount - (suggestion.expectedAmount - suggestion.remainingAmount - suggestion.actualAmount).coerceAtLeast(0.0))}", fontSize = 10.sp, color = SlateGray)
                OutlinedTextField(amount, { amount = it }, label = { Text("Zuzuordnender Betrag €") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { parseMoney(amount)?.let(onConfirm) }) { Text("Bestätigen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun ManualRentDialog(
    suggestion: BankRentSuggestion,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, Double) -> Unit
) {
    var propertyId by remember { mutableStateOf(suggestion.propertyId) }
    var unitId by remember { mutableStateOf(suggestion.unitId) }
    var month by remember { mutableStateOf(suggestion.rentMonth) }
    var tenantRef by remember { mutableStateOf(suggestion.tenantReference) }
    var amount by remember { mutableStateOf(suggestion.actualAmount.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mietzuordnung manuell prüfen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Es werden nur bereits vorhandene Objekt-, Einheiten- und Mietverhältnisdaten akzeptiert.", fontSize = 10.sp, color = SlateGray)
                OutlinedTextField(propertyId, { propertyId = it }, label = { Text("Objekt-ID") }, singleLine = true)
                OutlinedTextField(unitId, { unitId = it }, label = { Text("Einheit-ID") }, singleLine = true)
                OutlinedTextField(month, { month = it }, label = { Text("Mietmonat YYYY-MM") }, singleLine = true)
                OutlinedTextField(tenantRef, { tenantRef = it }, label = { Text("Mietverhältnis-Referenz") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Betrag €") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { parseMoney(amount)?.let { onConfirm(propertyId.trim(), unitId.trim(), month.trim(), tenantRef.trim(), it) } }) { Text("Bestätigen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

private fun parseMoney(value: String): Double? = value.trim().replace(".", "").replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
