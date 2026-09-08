package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankLoanAssignment
import com.example.data.BankLoanPaymentType
import com.example.data.BankLoanSplitStatus
import com.example.data.BankLoanSuggestion
import com.example.data.BankRecurringPattern
import com.example.data.BankTransaction
import com.example.data.Loan
import com.example.data.RecurringCadence
import com.example.data.RecurringPaymentPattern

@Composable
fun BankPhase2CPanel(viewModel: ReceiptViewModel) {
    val transactions by viewModel.bankTransactions.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val suggestions by viewModel.bankLoanSuggestions.collectAsState()
    val assignments by viewModel.bankLoanAssignments.collectAsState()
    val recurring by viewModel.bankRecurringAnalysis.collectAsState()
    val storedPatterns by viewModel.bankRecurringPatterns.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, BorderColor),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Darlehen & wiederkehrende Zahlungen", fontWeight = FontWeight.Bold, color = DarkNavy)
                Text(
                    "Nur Analyse und Vorschläge. Keine Darlehensbuchung, keine DATEV-Freigabe und keine Zahlung wird automatisch ausgelöst.",
                    fontSize = 11.sp,
                    color = SlateGray
                )
            }
        }

        if (assignments.isNotEmpty()) {
            Text("Bestätigte Darlehenszuordnungen", fontWeight = FontWeight.SemiBold, color = DarkNavy)
            assignments.take(20).forEach { assignment ->
                val transaction = transactions.firstOrNull { it.transactionId == assignment.transactionId }
                val loan = loans.firstOrNull { it.id == assignment.loanId }
                LoanAssignmentCard(viewModel, assignment, transaction, loan)
            }
        }

        val openSuggestions = suggestions.entries
            .filter { (txId, _) -> assignments.none { it.transactionId == txId } }
            .take(20)
        if (openSuggestions.isNotEmpty()) {
            Text("Darlehensvorschläge", fontWeight = FontWeight.SemiBold, color = DarkNavy)
            openSuggestions.forEach { (transactionId, ranked) ->
                val transaction = transactions.firstOrNull { it.transactionId == transactionId } ?: return@forEach
                LoanSuggestionCard(viewModel, transaction, ranked, loans)
            }
        }

        HorizontalDivider()
        Text("Wiederkehrende Zahlungen", fontWeight = FontWeight.SemiBold, color = DarkNavy)
        if (recurring.patterns.isEmpty()) {
            Text("Noch kein belastbares wiederkehrendes Muster erkannt.", fontSize = 12.sp, color = SlateGray)
        } else {
            recurring.patterns.take(20).forEach { pattern ->
                RecurringPatternCard(
                    viewModel = viewModel,
                    pattern = pattern,
                    stored = storedPatterns.firstOrNull { it.patternId == pattern.patternId },
                    missing = pattern.patternId in recurring.missingExpectedPatternIds,
                    duplicateCount = transactions.count { it.transactionId in recurring.duplicateTransactionIds }
                )
            }
        }
    }
}

@Composable
private fun LoanSuggestionCard(
    viewModel: ReceiptViewModel,
    transaction: BankTransaction,
    ranked: List<BankLoanSuggestion>,
    loans: List<Loan>
) {
    val top = ranked.firstOrNull() ?: return
    val loan = loans.firstOrNull { it.id == top.loanId } ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, BorderColor),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(loan.bezeichnung.ifBlank { "Darlehen ${loan.id}" }, fontWeight = FontWeight.Bold, color = DarkNavy)
            Text("${loan.bank.ifBlank { "Bank nicht hinterlegt" }} • Objekt ${loan.propertyId.ifBlank { "nicht zugeordnet" }}", fontSize = 11.sp, color = SlateGray)
            Text(
                "Soll ${NumberFormatter.format(top.expectedAmount)} • Ist ${NumberFormatter.format(top.actualAmount)} • Differenz ${NumberFormatter.format(top.difference)}",
                fontSize = 12.sp,
                color = DarkNavy
            )
            Text(
                "${top.score}% ${top.confidence} • Rhythmus-Evidenz ${top.recurrenceEvidence + 1} Vorkommen • ${top.paymentType}",
                fontSize = 11.sp,
                color = SlateGray
            )
            if (top.conflictState != "NONE") Text("Konflikt: ${top.conflictState}", fontSize = 11.sp, color = SlateGray)
            if (top.reasons.isNotEmpty()) Text(top.reasons.take(5).joinToString(" • "), fontSize = 11.sp, color = SlateGray)
            Button(
                onClick = { viewModel.confirmBankLoanAssignment(transaction.transactionId, top.loanId) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (top.paymentType == BankLoanPaymentType.REGULAERE_RATE) "Als Darlehensrate bestätigen" else "Darlehenszuordnung bestätigen") }
            OutlinedButton(
                onClick = { viewModel.markBankLoanAsSpecialRepayment(transaction.transactionId, top.loanId) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Als Sondertilgung markieren") }
            if (ranked.size > 1) {
                Text("Anderes bestehendes Darlehen wählen", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                ranked.drop(1).take(3).forEach { alternative ->
                    val altLoan = loans.firstOrNull { it.id == alternative.loanId }
                    if (altLoan != null) {
                        TextButton(onClick = { viewModel.confirmBankLoanAssignment(transaction.transactionId, alternative.loanId) }) {
                            Text("${altLoan.bezeichnung.ifBlank { "Darlehen ${altLoan.id}" }} • ${alternative.score}%")
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { viewModel.dismissBankLoanSuggestion(transaction.transactionId) }) { Text("Nicht als Darlehen") }
                TextButton(onClick = { viewModel.markBankTransactionForReview(transaction.transactionId) }) { Text("Manuell prüfen") }
            }
        }
    }
}

@Composable
private fun LoanAssignmentCard(
    viewModel: ReceiptViewModel,
    assignment: BankLoanAssignment,
    transaction: BankTransaction?,
    loan: Loan?
) {
    var interestText by remember(assignment.assignmentId, assignment.proposedInterest) {
        mutableStateOf(assignment.proposedInterest?.let { "%.2f".format(java.util.Locale.GERMANY, it) }.orEmpty())
    }
    var principalText by remember(assignment.assignmentId, assignment.proposedPrincipal) {
        mutableStateOf(assignment.proposedPrincipal?.let { "%.2f".format(java.util.Locale.GERMANY, it) }.orEmpty())
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, BorderColor),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val loanName = loan?.bezeichnung?.takeIf { it.isNotBlank() } ?: "Darlehen ${assignment.loanId}"
            Text(loanName, fontWeight = FontWeight.Bold, color = DarkNavy)
            Text("${assignment.paymentType} • Periode ${assignment.period.ifBlank { "unklar" }} • ${assignment.status}", fontSize = 11.sp, color = SlateGray)
            Text("Zugeordnet ${NumberFormatter.format(assignment.allocatedAmount)}", fontSize = 12.sp, color = DarkNavy)
            transaction?.let { Text("Bank: ${it.bookingDate} • ${it.counterparty.ifBlank { it.purpose }}", fontSize = 11.sp, color = SlateGray) }
            if (assignment.proposedInterest != null && assignment.proposedPrincipal != null) {
                Text("Zins/Tilgung – nur Vorschlag", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                if (assignment.interestBasis.isNotBlank()) Text(assignment.interestBasis, fontSize = 10.sp, color = SlateGray)
                OutlinedTextField(value = interestText, onValueChange = { interestText = it }, label = { Text("Zins") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = principalText, onValueChange = { principalText = it }, label = { Text("Tilgung") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = {
                        viewModel.updateBankLoanSplit(
                            assignment.transactionId,
                            parseMoney(interestText),
                            parseMoney(principalText),
                            accept = true,
                            edited = assignment.splitStatus != BankLoanSplitStatus.PROPOSED
                        )
                    }) { Text("Vorschlag übernehmen/ändern") }
                    TextButton(onClick = { viewModel.updateBankLoanSplit(assignment.transactionId, null, null, accept = false) }) { Text("Ablehnen") }
                }
                Text("Tilgung wird dadurch nicht automatisch steuerlich als Ausgabe behandelt und nicht an DATEV freigegeben.", fontSize = 10.sp, color = SlateGray)
            } else {
                Text("Keine belastbare Grundlage für eine Zins-/Tilgungsaufteilung vorhanden.", fontSize = 11.sp, color = SlateGray)
            }
            TextButton(onClick = { viewModel.unlinkBankLoanAssignment(assignment.transactionId) }) { Text("Darlehenszuordnung lösen") }
        }
    }
}

@Composable
private fun RecurringPatternCard(
    viewModel: ReceiptViewModel,
    pattern: RecurringPaymentPattern,
    stored: BankRecurringPattern?,
    missing: Boolean,
    duplicateCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, BorderColor),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(pattern.normalizedCounterparty.ifBlank { "Wiederkehrende Zahlung" }, fontWeight = FontWeight.Bold, color = DarkNavy)
            Text("${cadenceLabel(pattern.cadence)} • typisch ${NumberFormatter.format(pattern.typicalAmount)} ± ${NumberFormatter.format(pattern.amountTolerance)} • Tag ${pattern.typicalDay}", fontSize = 11.sp, color = SlateGray)
            Text("${pattern.confidence}% Confidence • ${pattern.occurrenceCount} Vorkommen • zuletzt ${pattern.lastOccurrence}", fontSize = 11.sp, color = SlateGray)
            pattern.nextExpectedWindow?.let { window ->
                Text("Erwartet: ${window.fromDate} bis ${window.toDate} (typisch ${window.expectedDate})", fontSize = 11.sp, color = SlateGray)
            }
            if (missing) Text("Erwartete Zahlung noch nicht erkannt", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = DarkNavy)
            if (duplicateCount > 0) Text("Mögliche doppelte wiederkehrende Zahlung erkannt – Bankbuchung bleibt unverändert.", fontSize = 10.sp, color = SlateGray)
            if (pattern.reasons.isNotEmpty()) Text(pattern.reasons.take(4).joinToString(" • "), fontSize = 10.sp, color = SlateGray)
            if (stored == null) {
                OutlinedButton(onClick = { viewModel.saveRecurringPattern(pattern.patternId) }, modifier = Modifier.fillMaxWidth()) { Text("Muster speichern") }
            } else {
                Text("Gespeichertes Muster • ${if (stored.enabled) "aktiv" else "ignoriert"}", fontSize = 11.sp)
                TextButton(onClick = { viewModel.setRecurringPatternEnabled(stored.patternId, !stored.enabled) }) {
                    Text(if (stored.enabled) "Muster ignorieren" else "Muster wieder berücksichtigen")
                }
            }
            TextButton(onClick = { viewModel.proposeBankRuleFromRecurringPattern(pattern.patternId) }) { Text("Regel erstellen? (nur Vorschlag)") }
        }
    }
}

private fun cadenceLabel(value: String): String = when (value) {
    RecurringCadence.MONTHLY -> "monatlich"
    RecurringCadence.QUARTERLY -> "vierteljährlich"
    RecurringCadence.HALF_YEARLY -> "halbjährlich"
    RecurringCadence.YEARLY -> "jährlich"
    else -> value.ifBlank { "Rhythmus unklar" }
}

private fun parseMoney(value: String): Double? {
    val raw = value.trim()
    if (raw.isBlank()) return null
    return if (raw.contains(',')) raw.replace(".", "").replace(',', '.').toDoubleOrNull() else raw.toDoubleOrNull()
}
