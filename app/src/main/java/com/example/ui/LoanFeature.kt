package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDatabase
import com.example.data.Loan
import kotlinx.coroutines.launch

private fun parseLoanNumber(value: String): Double? = value.trim().replace(".", "").replace(',', '.').toDoubleOrNull()

private fun estimatedAnnualInterest(loan: Loan): Double =
    loan.restschuld.coerceAtLeast(0.0) * (loan.sollzinsProzent.coerceAtLeast(0.0) / 100.0) *
        (loan.vermietungsanteilProzent.coerceIn(0.0, 100.0) / 100.0)

@Composable
fun LoanManagementSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember(context) { AppDatabase.getDatabase(context.applicationContext, scope) }
    val loans by database.loanDao().getAllLoansFlow().collectAsState(initial = emptyList())
    var showManager by remember { mutableStateOf(false) }
    var editingLoan by remember { mutableStateOf<Loan?>(null) }
    var showNewLoan by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Loan?>(null) }

    val activeLoans = loans.filter { it.aktiv }
    val totalBalance = activeLoans.sumOf { it.restschuld }
    val estimatedInterest = activeLoans.sumOf(::estimatedAnnualInterest)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showManager = true }
            .testTag("loan_management_card"),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, BorderColor),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Darlehen & Schuldzinsen", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text("${activeLoans.size} aktive Darlehen", fontSize = 11.sp, color = SlateGray)
                }
                Text("Verwalten", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
            }
            if (activeLoans.isNotEmpty()) {
                HorizontalDivider(color = BorderColor)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Restschuld gesamt", fontSize = 11.sp, color = SlateGray)
                    Text(NumberFormatter.format(totalBalance), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Zins-Schätzwert/Jahr*", fontSize = 11.sp, color = SlateGray)
                    Text(NumberFormatter.format(estimatedInterest), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                }
                Text("*Planwert aus Restschuld × Sollzins × Vermietungsanteil; tatsächliche Schuldzinsen werden später aus Belegen/Kontoauszügen abgeglichen.", fontSize = 9.sp, color = SlateGray, lineHeight = 12.sp)
            } else {
                Text("Noch kein Darlehen hinterlegt. Tippe hier, um das erste hinzuzufügen.", fontSize = 11.sp, color = SlateGray)
            }
        }
    }

    if (showManager) {
        AlertDialog(
            onDismissRequest = { showManager = false },
            title = { Text("Darlehen verwalten", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showNewLoan = true },
                        modifier = Modifier.fillMaxWidth().testTag("add_loan_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Darlehen hinzufügen")
                    }
                    if (loans.isEmpty()) {
                        Text("Keine Darlehen vorhanden.", color = SlateGray, fontSize = 12.sp)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(loans, key = { it.id }) { loan ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = SoftBackground),
                                    border = BorderStroke(1.dp, BorderColor)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(loan.bezeichnung.ifBlank { "Darlehen #${loan.id}" }, fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 13.sp)
                                                Text(loan.bank.ifBlank { "Bank nicht angegeben" }, fontSize = 10.sp, color = SlateGray)
                                            }
                                            IconButton(onClick = { editingLoan = loan }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Bearbeiten", tint = AccentBlue)
                                            }
                                            IconButton(onClick = { pendingDelete = loan }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = CrimsonRed)
                                            }
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Restschuld", fontSize = 10.sp, color = SlateGray)
                                            Text(NumberFormatter.format(loan.restschuld), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Sollzins / Tilgung", fontSize = 10.sp, color = SlateGray)
                                            Text("${loan.sollzinsProzent}% / ${loan.tilgungProzent}%", fontSize = 11.sp, color = DarkNavy)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Monatsrate", fontSize = 10.sp, color = SlateGray)
                                            Text(NumberFormatter.format(loan.monatlicheRate), fontSize = 11.sp, color = DarkNavy)
                                        }
                                        if (!loan.aktiv) Text("Beendet / inaktiv", fontSize = 10.sp, color = CrimsonRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showManager = false }) { Text("Schließen") } }
        )
    }

    if (showNewLoan) {
        LoanEditDialog(
            initial = Loan(),
            title = "Darlehen hinzufügen",
            onDismiss = { showNewLoan = false },
            onSave = { loan -> scope.launch { database.loanDao().upsertLoan(loan) }; showNewLoan = false }
        )
    }

    editingLoan?.let { loan ->
        LoanEditDialog(
            initial = loan,
            title = "Darlehen bearbeiten",
            onDismiss = { editingLoan = null },
            onSave = { updated -> scope.launch { database.loanDao().upsertLoan(updated) }; editingLoan = null }
        )
    }

    pendingDelete?.let { loan ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Darlehen löschen?") },
            text = { Text("„${loan.bezeichnung.ifBlank { "Darlehen #${loan.id}" }}“ wird dauerhaft aus der App entfernt.") },
            confirmButton = {
                Button(
                    onClick = { scope.launch { database.loanDao().deleteLoan(loan.id) }; pendingDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                ) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun LoanEditDialog(
    initial: Loan,
    title: String,
    onDismiss: () -> Unit,
    onSave: (Loan) -> Unit
) {
    var bezeichnung by remember(initial.id) { mutableStateOf(initial.bezeichnung) }
    var bank by remember(initial.id) { mutableStateOf(initial.bank) }
    var darlehensbetrag by remember(initial.id) { mutableStateOf(initial.darlehensbetrag.takeIf { it != 0.0 }?.toString() ?: "") }
    var restschuld by remember(initial.id) { mutableStateOf(initial.restschuld.takeIf { it != 0.0 }?.toString() ?: "") }
    var sollzins by remember(initial.id) { mutableStateOf(initial.sollzinsProzent.takeIf { it != 0.0 }?.toString() ?: "") }
    var tilgung by remember(initial.id) { mutableStateOf(initial.tilgungProzent.takeIf { it != 0.0 }?.toString() ?: "") }
    var rate by remember(initial.id) { mutableStateOf(initial.monatlicheRate.takeIf { it != 0.0 }?.toString() ?: "") }
    var startDatum by remember(initial.id) { mutableStateOf(initial.startDatum) }
    var zinsbindungBis by remember(initial.id) { mutableStateOf(initial.zinsbindungBis) }
    var laufzeitBis by remember(initial.id) { mutableStateOf(initial.laufzeitBis) }
    var vermietungsanteil by remember(initial.id) { mutableStateOf(initial.vermietungsanteilProzent.toString()) }
    var notiz by remember(initial.id) { mutableStateOf(initial.notiz) }
    var aktiv by remember(initial.id) { mutableStateOf(initial.aktiv) }
    var validationError by remember(initial.id) { mutableStateOf<String?>(null) }

    val numericKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                item { OutlinedTextField(bezeichnung, { bezeichnung = it }, label = { Text("Bezeichnung*") }, modifier = Modifier.fillMaxWidth().testTag("loan_name"), singleLine = true) }
                item { OutlinedTextField(bank, { bank = it }, label = { Text("Bank / Kreditgeber") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(darlehensbetrag, { darlehensbetrag = it }, label = { Text("Darlehenssumme €*") }, keyboardOptions = numericKeyboard, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(restschuld, { restschuld = it }, label = { Text("Restschuld €*") }, keyboardOptions = numericKeyboard, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(sollzins, { sollzins = it }, label = { Text("Sollzins %*") }, keyboardOptions = numericKeyboard, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(tilgung, { tilgung = it }, label = { Text("Tilgung %") }, keyboardOptions = numericKeyboard, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
                item { OutlinedTextField(rate, { rate = it }, label = { Text("Monatliche Rate €") }, keyboardOptions = numericKeyboard, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(startDatum, { startDatum = it }, label = { Text("Start YYYY-MM-DD") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(zinsbindungBis, { zinsbindungBis = it }, label = { Text("Zinsbindung bis") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
                item { OutlinedTextField(laufzeitBis, { laufzeitBis = it }, label = { Text("Geplantes Laufzeitende") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(vermietungsanteil, { vermietungsanteil = it }, label = { Text("Vermietungsanteil %") }, keyboardOptions = numericKeyboard, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(notiz, { notiz = it }, label = { Text("Notiz") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column { Text("Darlehen aktiv", fontWeight = FontWeight.Medium); Text("Deaktivieren, wenn abgelöst/beendet", fontSize = 9.sp, color = SlateGray) }
                        Switch(checked = aktiv, onCheckedChange = { aktiv = it })
                    }
                }
                validationError?.let { error -> item { Text(error, color = CrimsonRed, fontSize = 11.sp, fontWeight = FontWeight.Bold) } }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val original = parseLoanNumber(darlehensbetrag)
                    val balance = parseLoanNumber(restschuld)
                    val interest = parseLoanNumber(sollzins)
                    val repayment = parseLoanNumber(tilgung) ?: 0.0
                    val monthly = parseLoanNumber(rate) ?: 0.0
                    val rented = (parseLoanNumber(vermietungsanteil) ?: 100.0).coerceIn(0.0, 100.0)
                    when {
                        bezeichnung.isBlank() -> validationError = "Bitte eine Bezeichnung eingeben."
                        original == null || original < 0.0 -> validationError = "Darlehenssumme ist ungültig."
                        balance == null || balance < 0.0 -> validationError = "Restschuld ist ungültig."
                        interest == null || interest < 0.0 -> validationError = "Sollzins ist ungültig."
                        else -> onSave(initial.copy(
                            bezeichnung = bezeichnung.trim(), bank = bank.trim(), darlehensbetrag = original,
                            restschuld = balance, sollzinsProzent = interest, tilgungProzent = repayment,
                            monatlicheRate = monthly, startDatum = startDatum.trim(), zinsbindungBis = zinsbindungBis.trim(),
                            laufzeitBis = laufzeitBis.trim(), vermietungsanteilProzent = rented, notiz = notiz.trim(), aktiv = aktiv
                        ))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                modifier = Modifier.testTag("save_loan_button")
            ) { Text("Speichern") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
