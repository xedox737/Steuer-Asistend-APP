package com.example.ui

import com.example.util.DateFormatUtils

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppDatabase
import com.example.data.Loan
import com.example.data.Receipt
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun estimatedAnnualInterest(loan: Loan): Double =
    loan.restschuld.coerceAtLeast(0.0) * (loan.sollzinsProzent.coerceAtLeast(0.0) / 100.0) *
        (loan.vermietungsanteilProzent.coerceIn(0.0, 100.0) / 100.0)

private fun isInterestReceipt(receipt: Receipt): Boolean =
    receipt.hauptkategorie == "Finanzierung, Kredite & Versicherungen" &&
        (receipt.unterkategorie.equals("Kreditzinsen", ignoreCase = true) ||
            receipt.beschreibung.contains("zins", ignoreCase = true) ||
            receipt.beschreibung.contains("sollzins", ignoreCase = true))

private fun receiptYear(receipt: Receipt): Int? = receipt.datum.take(4).toIntOrNull()

private fun actualDeductibleInterest(receipts: List<Receipt>, loansById: Map<Int, Loan>, assignments: Map<Int, Int>): Double =
    receipts.sumOf { receipt ->
        val loan = assignments[receipt.id]?.let(loansById::get)
        if (loan == null) 0.0 else receipt.bruttobetrag * (loan.vermietungsanteilProzent.coerceIn(0.0, 100.0) / 100.0)
    }

@Composable
fun LoanManagementSection(viewModel: ReceiptViewModel, propertyScoped: Boolean = false, propertyId: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember(context) { AppDatabase.getDatabase(context.applicationContext, scope) }
    val allLoans by database.loanDao().getAllLoansFlow().collectAsStateWithLifecycle(initialValue = emptyList())
    val allReceipts by database.receiptDao().getAllReceipts().collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedProperty by viewModel.propertyMetadata.collectAsStateWithLifecycle()
    val properties by viewModel.properties.collectAsStateWithLifecycle()
    val selectedPropertyId = propertyId ?: selectedProperty?.propertyId ?: com.example.data.StableDocumentIdentity.LEGACY_PROPERTY_ID
    val scopedProperty = properties.firstOrNull { it.propertyId == selectedPropertyId }
    val loans = if (propertyScoped || propertyId != null) {
        scopedProperty?.let { ImmobilienManagerProjection.loans(it, allLoans) }
            ?: allLoans.filter { it.propertyId == selectedPropertyId }
    } else allLoans
    val receipts = if (propertyScoped || propertyId != null) allReceipts.filter { it.propertyId == selectedPropertyId } else allReceipts
    val assignmentPrefs = remember(context) { context.getSharedPreferences("loan_interest_assignments", 0) }

    var showManager by remember(selectedPropertyId) { mutableStateOf(false) }
    var editingLoan by remember(selectedPropertyId) { mutableStateOf<Loan?>(null) }
    var showNewLoan by remember(selectedPropertyId) { mutableStateOf(false) }
    var pendingDelete by remember(selectedPropertyId) { mutableStateOf<Loan?>(null) }
    var receiptToAssign by remember(selectedPropertyId) { mutableStateOf<Receipt?>(null) }
    var taxYear by remember(selectedPropertyId) { mutableIntStateOf(LocalDate.now().year) }
    var assignmentVersion by remember(selectedPropertyId) { mutableIntStateOf(0) }

    val assignments = remember(loans, receipts, assignmentVersion) {
        receipts.associateNotNull { receipt ->
            val loanId = assignmentPrefs.getInt("receipt_${receipt.id}", 0)
            if (loanId > 0) receipt.id to loanId else null
        }
    }
    val activeLoans = loans.filter { it.aktiv }
    val loansById = loans.associateBy { it.id }
    val yearInterestReceipts = receipts.filter { isInterestReceipt(it) && receiptYear(it) == taxYear }
    val unmatchedInterestReceipts = yearInterestReceipts.filter { assignments[it.id] == null }
    val totalBalance = activeLoans.sumOf { it.restschuld }
    val estimatedInterest = activeLoans.sumOf(::estimatedAnnualInterest)
    val actualInterest = actualDeductibleInterest(yearInterestReceipts, loansById, assignments)
    val difference = actualInterest - estimatedInterest

    fun assignReceipt(receiptId: Int, loanId: Int?) {
        val editor = assignmentPrefs.edit()
        if (loanId == null) editor.remove("receipt_$receiptId") else editor.putInt("receipt_$receiptId", loanId)
        editor.apply()
        assignmentVersion++
        viewModel.syncAllToDrive()
    }

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
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("Darlehen & Schuldzinsen", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text("${activeLoans.size} aktive Darlehen · Steuerjahr $taxYear", fontSize = 11.sp, color = SlateGray)
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
                    Text("Zins-Planwert/Jahr", fontSize = 11.sp, color = SlateGray)
                    Text(NumberFormatter.format(estimatedInterest), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tatsächlich gezahlte Schuldzinsen", fontSize = 11.sp, color = SlateGray)
                    Text(NumberFormatter.format(actualInterest), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Abweichung Ist – Plan", fontSize = 11.sp, color = SlateGray)
                    Text(NumberFormatter.format(difference), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (difference > 0) WarmOrange else DarkNavy)
                }
                if (unmatchedInterestReceipts.isNotEmpty()) {
                    Text(
                        "${unmatchedInterestReceipts.size} Zinsbeleg${if (unmatchedInterestReceipts.size == 1) "" else "e"} noch keinem Darlehen zugeordnet.",
                        fontSize = 10.sp,
                        color = WarmOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "Ist-Wert = zugeordnete Zinsbelege × Vermietungsanteil. Tilgung wird nicht als Schuldzins gerechnet.",
                    fontSize = 9.sp,
                    color = SlateGray,
                    lineHeight = 12.sp
                )
            } else {
                Text("Noch kein Darlehen hinterlegt. Tippe hier, um das erste hinzuzufügen.", fontSize = 11.sp, color = SlateGray)
            }
        }
    }

    if (showManager) {
        AlertDialog(
            onDismissRequest = { showManager = false },
            title = { Text("Darlehen & Schuldzinsen", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { taxYear-- }) { Text("‹") }
                            Text("Steuerjahr $taxYear", fontWeight = FontWeight.Bold, color = DarkNavy)
                            TextButton(onClick = { taxYear++ }) { Text("›") }
                        }
                    }
                    item {
                        Button(
                            onClick = { showNewLoan = true },
                            modifier = Modifier.fillMaxWidth().testTag("add_loan_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Darlehen hinzufügen")
                        }
                    }

                    if (loans.isEmpty()) {
                        item { Text("Keine Darlehen vorhanden.", color = SlateGray, fontSize = 12.sp) }
                    } else {
                        items(loans, key = { "loan_${it.id}" }) { loan ->
                            val actualForLoan = yearInterestReceipts
                                .filter { assignments[it.id] == loan.id }
                                .sumOf { it.bruttobetrag * (loan.vermietungsanteilProzent.coerceIn(0.0, 100.0) / 100.0) }
                            val planForLoan = estimatedAnnualInterest(loan)
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
                                        IconButton(onClick = { editingLoan = loan }) { Icon(Icons.Default.Edit, contentDescription = "Bearbeiten", tint = AccentBlue) }
                                        IconButton(onClick = { pendingDelete = loan }) { Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = CrimsonRed) }
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Restschuld", fontSize = 10.sp, color = SlateGray)
                                        Text(NumberFormatter.format(loan.restschuld), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Plan-Zins $taxYear", fontSize = 10.sp, color = SlateGray)
                                        Text(NumberFormatter.format(planForLoan), fontSize = 11.sp, color = DarkNavy)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Ist-Zins $taxYear", fontSize = 10.sp, color = SlateGray)
                                        Text(NumberFormatter.format(actualForLoan), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                                    }
                                    Text("Vermietungsanteil: ${loan.vermietungsanteilProzent}%", fontSize = 9.sp, color = SlateGray)
                                    if (!loan.aktiv) Text("Beendet / inaktiv", fontSize = 10.sp, color = CrimsonRed, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    item {
                        HorizontalDivider(color = BorderColor)
                        Text("Zinsbelege $taxYear zuordnen", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkNavy, modifier = Modifier.padding(top = 4.dp))
                        Text("Die App erkennt Belege der Kategorie Kreditzinsen. Du bestätigst nur das zugehörige Darlehen.", fontSize = 10.sp, color = SlateGray)
                    }

                    if (yearInterestReceipts.isEmpty()) {
                        item { Text("Keine Zinsbelege für $taxYear gefunden.", color = SlateGray, fontSize = 11.sp) }
                    } else {
                        items(yearInterestReceipts, key = { "interest_${it.id}" }) { receipt ->
                            val assignedLoan = assignments[receipt.id]?.let(loansById::get)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                                border = BorderStroke(1.dp, if (assignedLoan == null) androidx.compose.ui.graphics.Color(0xFFFED7AA) else BorderColor)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(receipt.aussteller, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                                            Text(DateFormatUtils.safeFormat(receipt.datum), fontSize = 9.sp, color = SlateGray)
                                        }
                                        Text(NumberFormatter.format(receipt.bruttobetrag), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                                    }
                                    Text(receipt.beschreibung.ifBlank { receipt.unterkategorie }, fontSize = 9.sp, color = SlateGray, maxLines = 2)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            assignedLoan?.let { "→ ${it.bezeichnung}" } ?: "Noch nicht zugeordnet",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (assignedLoan == null) WarmOrange else EmeraldGreen
                                        )
                                        TextButton(onClick = { receiptToAssign = receipt }) {
                                            Text(if (assignedLoan == null) "Zuordnen" else "Ändern", fontSize = 10.sp)
                                        }
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

    receiptToAssign?.let { receipt ->
        val currentLoanId = assignments[receipt.id]
        AlertDialog(
            onDismissRequest = { receiptToAssign = null },
            title = { Text("Zinsbeleg zuordnen", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text("${DateFormatUtils.safeFormat(receipt.datum)} · ${NumberFormatter.format(receipt.bruttobetrag)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(receipt.aussteller, fontSize = 11.sp, color = SlateGray)
                    }
                    items(loans.filter { it.aktiv }, key = { "assign_${it.id}" }) { loan ->
                        OutlinedButton(
                            onClick = { assignReceipt(receipt.id, loan.id); receiptToAssign = null },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (currentLoanId == loan.id) "✓ ${loan.bezeichnung}" else loan.bezeichnung)
                        }
                    }
                    if (currentLoanId != null) {
                        item {
                            TextButton(
                                onClick = { assignReceipt(receipt.id, null); receiptToAssign = null },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Zuordnung entfernen", color = CrimsonRed) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { receiptToAssign = null }) { Text("Abbrechen") } }
        )
    }

    if (showNewLoan) {
        LoanEditDialog(
            initial = Loan(propertyId = selectedPropertyId),
            title = "Darlehen hinzufügen",
            onDismiss = { showNewLoan = false },
            onSave = { loan -> scope.launch { database.loanDao().upsertLoan(loan); viewModel.syncAllToDrive() }; showNewLoan = false }
        )
    }

    editingLoan?.let { loan ->
        LoanEditDialog(
            initial = loan,
            title = "Darlehen bearbeiten",
            onDismiss = { editingLoan = null },
            onSave = { updated -> scope.launch { database.loanDao().upsertLoan(updated); viewModel.syncAllToDrive() }; editingLoan = null }
        )
    }

    pendingDelete?.let { loan ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Darlehen löschen?") },
            text = { Text("„${loan.bezeichnung.ifBlank { "Darlehen #${loan.id}" }}“ wird dauerhaft aus der App entfernt. Bereits zugeordnete Zinsbelege bleiben erhalten, sind danach aber nicht mehr zugeordnet.") },
            confirmButton = {
                Button(
                    onClick = {
                        assignments.filterValues { it == loan.id }.keys.forEach { assignReceipt(it, null) }
                        scope.launch { database.loanDao().deleteLoan(loan.id); viewModel.syncAllToDrive() }
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                ) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Abbrechen") } }
        )
    }
}

private inline fun <K, V, R> Map<K, V>.associateNotNull(transform: (Map.Entry<K, V>) -> R?): List<R> =
    entries.mapNotNull(transform)

private fun <T, K, V> Iterable<T>.associateNotNull(transform: (T) -> Pair<K, V>?): Map<K, V> =
    mapNotNull(transform).toMap()

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LoanEditDialog(
    initial: Loan,
    title: String,
    onDismiss: () -> Unit,
    onSave: (Loan) -> Unit
) {
    val dateFields = rememberDateFieldValidation()
    var bezeichnung by remember(initial.id, initial.propertyId) { mutableStateOf(initial.bezeichnung) }
    var bank by remember(initial.id, initial.propertyId) { mutableStateOf(initial.bank) }
    var darlehensbetrag by remember(initial.id, initial.propertyId) { mutableStateOf(LoanInputValidation.prefill(initial.darlehensbetrag, blankZero = initial.id == 0)) }
    var restschuld by remember(initial.id, initial.propertyId) { mutableStateOf(LoanInputValidation.prefill(initial.restschuld, blankZero = initial.id == 0)) }
    var sollzins by remember(initial.id, initial.propertyId) { mutableStateOf(LoanInputValidation.prefill(initial.sollzinsProzent, blankZero = initial.id == 0)) }
    var tilgung by remember(initial.id, initial.propertyId) { mutableStateOf(LoanInputValidation.prefill(initial.tilgungProzent, blankZero = true)) }
    var rate by remember(initial.id, initial.propertyId) { mutableStateOf(LoanInputValidation.prefill(initial.monatlicheRate, blankZero = true)) }
    var startDatum by remember(initial.id, initial.propertyId) { mutableStateOf(initial.startDatum) }
    var zinsbindungBis by remember(initial.id, initial.propertyId) { mutableStateOf(initial.zinsbindungBis) }
    var laufzeitBis by remember(initial.id, initial.propertyId) { mutableStateOf(initial.laufzeitBis) }
    var vermietungsanteil by remember(initial.id, initial.propertyId) { mutableStateOf(LoanInputValidation.prefill(initial.vermietungsanteilProzent, blankZero = false)) }
    var notiz by remember(initial.id, initial.propertyId) { mutableStateOf(initial.notiz) }
    var aktiv by remember(initial.id, initial.propertyId) { mutableStateOf(initial.aktiv) }
    var validationError by remember(initial.id, initial.propertyId) { mutableStateOf<String?>(null) }
    var attemptedSave by remember(initial.id, initial.propertyId) { mutableStateOf(false) }
    val numberResults = mapOf(
        LoanInputValidation.Field.AMOUNT to LoanInputValidation.parse(darlehensbetrag, LoanInputValidation.Field.AMOUNT),
        LoanInputValidation.Field.BALANCE to LoanInputValidation.parse(restschuld, LoanInputValidation.Field.BALANCE),
        LoanInputValidation.Field.INTEREST to LoanInputValidation.parse(sollzins, LoanInputValidation.Field.INTEREST),
        LoanInputValidation.Field.REPAYMENT to LoanInputValidation.parse(tilgung, LoanInputValidation.Field.REPAYMENT),
        LoanInputValidation.Field.MONTHLY to LoanInputValidation.parse(rate, LoanInputValidation.Field.MONTHLY),
        LoanInputValidation.Field.RENTED to LoanInputValidation.parse(vermietungsanteil, LoanInputValidation.Field.RENTED)
    )
    fun fieldError(field: LoanInputValidation.Field): String? = numberResults.getValue(field).let {
        if (attemptedSave || it.status == LoanInputValidation.Status.INVALID_STORED) it.error else null
    }
    val chronologyErrors = LoanInputValidation.dateErrors(startDatum, zinsbindungBis, laufzeitBis)

    val numericKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val expandedNumbers = LocalDensity.current.fontScale >= 1.3f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp).testTag("loan_editor_fields"), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                item { OutlinedTextField(bezeichnung, { bezeichnung = it }, label = { Text("Bezeichnung*") }, modifier = Modifier.fillMaxWidth().testTag("loan_name"), singleLine = true) }
                item { OutlinedTextField(bank, { bank = it }, label = { Text("Bank / Kreditgeber") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item {
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = if (expandedNumbers) 1 else 2) {
                        OutlinedTextField(darlehensbetrag, { darlehensbetrag = it }, label = { Text("Darlehenssumme €*") }, isError = fieldError(LoanInputValidation.Field.AMOUNT) != null, supportingText = fieldError(LoanInputValidation.Field.AMOUNT)?.let { error -> { Text(error, modifier = Modifier.testTag("loan_error_AMOUNT")) } }, keyboardOptions = numericKeyboard, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(restschuld, { restschuld = it }, label = { Text("Restschuld €*") }, isError = fieldError(LoanInputValidation.Field.BALANCE) != null, supportingText = fieldError(LoanInputValidation.Field.BALANCE)?.let { error -> { Text(error, modifier = Modifier.testTag("loan_error_BALANCE")) } }, keyboardOptions = numericKeyboard, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
                item {
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = if (expandedNumbers) 1 else 2) {
                        OutlinedTextField(sollzins, { sollzins = it }, label = { Text("Sollzins %*") }, isError = fieldError(LoanInputValidation.Field.INTEREST) != null, supportingText = fieldError(LoanInputValidation.Field.INTEREST)?.let { error -> { Text(error, modifier = Modifier.testTag("loan_error_INTEREST")) } }, keyboardOptions = numericKeyboard, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(tilgung, { tilgung = it }, label = { Text("Tilgung %") }, isError = fieldError(LoanInputValidation.Field.REPAYMENT) != null, supportingText = fieldError(LoanInputValidation.Field.REPAYMENT)?.let { error -> { Text(error, modifier = Modifier.testTag("loan_error_REPAYMENT")) } }, keyboardOptions = numericKeyboard, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
                item { OutlinedTextField(rate, { rate = it }, label = { Text("Monatliche Rate €") }, isError = fieldError(LoanInputValidation.Field.MONTHLY) != null, supportingText = fieldError(LoanInputValidation.Field.MONTHLY)?.let { error -> { Text(error, modifier = Modifier.testTag("loan_error_MONTHLY")) } }, keyboardOptions = numericKeyboard, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppDateField(value = startDatum, onValueChange = { startDatum = it }, label = "Start", modifier = Modifier.fillMaxWidth(), isError = attemptedSave && chronologyErrors["start"] != null, supportingText = chronologyErrors["start"]?.takeIf { attemptedSave }?.let { error -> { Text(error) } }, required = false, onValidityChange = { dateFields.update("startDatum", it) })
                        AppDateField(value = zinsbindungBis, onValueChange = { zinsbindungBis = it }, label = "Zinsbindung bis", modifier = Modifier.fillMaxWidth(), isError = attemptedSave && chronologyErrors["binding"] != null, supportingText = chronologyErrors["binding"]?.takeIf { attemptedSave }?.let { error -> { Text(error) } }, required = false, onValidityChange = { dateFields.update("zinsbindungBis", it) })
                    }
                }
                item { AppDateField(value = laufzeitBis, onValueChange = { laufzeitBis = it }, label = "Geplantes Laufzeitende", modifier = Modifier.fillMaxWidth(), isError = attemptedSave && chronologyErrors["end"] != null, supportingText = chronologyErrors["end"]?.takeIf { attemptedSave }?.let { error -> { Text(error) } }, required = false, onValidityChange = { dateFields.update("laufzeitBis", it) }) }
                item { OutlinedTextField(vermietungsanteil, { vermietungsanteil = it }, label = { Text("Vermietungsanteil %") }, isError = fieldError(LoanInputValidation.Field.RENTED) != null, supportingText = fieldError(LoanInputValidation.Field.RENTED)?.let { error -> { Text(error, modifier = Modifier.testTag("loan_error_RENTED")) } }, keyboardOptions = numericKeyboard, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(notiz, { notiz = it }, label = { Text("Notiz") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Darlehen aktiv", fontWeight = FontWeight.Medium)
                            Text("Deaktivieren, wenn abgelöst/beendet", fontSize = 9.sp, color = SlateGray)
                        }
                        Switch(checked = aktiv, onCheckedChange = { aktiv = it })
                    }
                }
                validationError?.let { error -> item { Text(error, color = CrimsonRed, fontSize = 11.sp, fontWeight = FontWeight.Bold) } }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    attemptedSave = true
                    if (!dateFields.isValid) { validationError = DateFormatUtils.INPUT_ERROR; return@Button }
                    if (numberResults.values.any { it.error != null } || chronologyErrors.isNotEmpty()) {
                        validationError = "Bitte die markierten Felder korrigieren."
                        return@Button
                    }
                    val original = requireNotNull(numberResults.getValue(LoanInputValidation.Field.AMOUNT).value)
                    val balance = requireNotNull(numberResults.getValue(LoanInputValidation.Field.BALANCE).value)
                    val interest = requireNotNull(numberResults.getValue(LoanInputValidation.Field.INTEREST).value)
                    val repayment = requireNotNull(numberResults.getValue(LoanInputValidation.Field.REPAYMENT).value)
                    val monthly = requireNotNull(numberResults.getValue(LoanInputValidation.Field.MONTHLY).value)
                    val rented = requireNotNull(numberResults.getValue(LoanInputValidation.Field.RENTED).value)
                    when {
                        bezeichnung.isBlank() -> validationError = "Bitte eine Bezeichnung eingeben."
                        else -> onSave(
                            initial.copy(
                                bezeichnung = bezeichnung.trim(),
                                bank = bank.trim(),
                                darlehensbetrag = original,
                                restschuld = balance,
                                sollzinsProzent = interest,
                                tilgungProzent = repayment,
                                monatlicheRate = monthly,
                                startDatum = startDatum.trim(),
                                zinsbindungBis = zinsbindungBis.trim(),
                                laufzeitBis = laufzeitBis.trim(),
                                vermietungsanteilProzent = rented,
                                notiz = notiz.trim(),
                                aktiv = aktiv
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                modifier = Modifier.testTag("save_loan_button")
            ) { Text("Speichern") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
