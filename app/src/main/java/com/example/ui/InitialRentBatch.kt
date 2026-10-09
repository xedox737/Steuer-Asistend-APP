package com.example.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.PropertyMetadata
import kotlinx.coroutines.launch

internal data class InitialRentRow(
    val unitId: String, val tenant: String = "", val start: String = "",
    val cold: String = "0", val utilities: String = "0", val other: String = "0",
    val status: String = "Leerstand", val included: Boolean = false
)

internal object InitialRentInput {
    val statuses = listOf("Vermietet", "Leerstand", "Renovierung")
    fun eligible(unit: WohneinheitStatus, periods: List<TenantPeriod>): Boolean = periods.isEmpty() &&
        unit.mieter.isBlank() && unit.mietvertragsstart.isBlank() && unit.kaltmiete <= 0.0

    fun error(row: InitialRentRow): String? = when {
        row.status !in statuses -> "Bitte einen gültigen Status wählen."
        listOf(row.cold, row.utilities, row.other).any { RentPlanInput.amount(it) == null } ->
            "Kaltmiete, NK und Sonstiges müssen gültige Beträge ab 0 € sein."
        row.status == "Vermietet" && row.tenant.isBlank() -> "Bitte den Mieter eintragen."
        row.status == "Vermietet" && CalendarInput.parseIsoDate(row.start) == null -> "Bitte einen gültigen Mietbeginn (JJJJ-MM-TT) eingeben."
        row.status != "Vermietet" && (row.tenant.isNotBlank() || row.start.isNotBlank() ||
            listOf(row.cold, row.utilities, row.other).any { RentPlanInput.amount(it) != 0.0 }) ->
            "Bei Leerstand / Renovierung bleiben Mieter und Mietbeginn leer; Mietbeträge sind 0 €."
        else -> null
    }
}

@Composable
internal fun InitialRentBatchDialog(property: PropertyMetadata, units: List<WohneinheitStatus>,
    viewModel: ReceiptViewModel, onSaved: () -> Unit, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember(property.propertyId) { mutableStateOf(units.map {
        InitialRentRow(PropertyUnitScopedData.stableUnitId(property.propertyId, it))
    }) }
    val eligibleIds = remember(property.propertyId, units) { units.filter { unit ->
        val id = PropertyUnitScopedData.stableUnitId(property.propertyId, unit)
        !TenantHistoryStore.hasStoredPeriods(context, property.propertyId, id, unit.name) &&
            InitialRentInput.eligible(unit, TenantHistoryStore.load(context, property.propertyId, id, unit.name))
    }.map { PropertyUnitScopedData.stableUnitId(property.propertyId, it) }.toSet() }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var preview by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    fun change(row: InitialRentRow) {
        rows = rows.map { if (it.unitId == row.unitId) row.copy(included = true) else it }
        errors = errors - row.unitId
    }
    val selected = rows.filter { it.included && it.unitId in eligibleIds }
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, shape = Ui2.shape,
        title = { Text(if (preview) "${selected.size} Einheiten speichern?" else "Mietdaten gesammelt erfassen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Erstbefüllung: vorhandene Mietverträge bleiben geschützt. Nur markierte Zeilen werden gespeichert.")
                LazyColumn(Modifier.heightIn(max = 420.dp).testTag("rent_batch_rows"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(if (preview) selected else rows, key = { it.unitId }) { row ->
                        val unit = units.first { PropertyUnitScopedData.stableUnitId(property.propertyId, it) == row.unitId }
                        val enabled = row.unitId in eligibleIds && !saving
                        Column {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                if (!preview) Checkbox(row.included, { included ->
                                    rows = rows.map { if (it.unitId == row.unitId) it.copy(included = included) else it }
                                }, enabled = enabled, modifier = Modifier.testTag("rent_batch_select_${row.unitId}"))
                                Text(unit.label.ifBlank { unit.name })
                                if (!preview && enabled) {
                                    Spacer(Modifier.weight(1f))
                                    var expanded by remember(row.unitId) { mutableStateOf(false) }
                                    Box {
                                        OutlinedButton(onClick = { expanded = true }, enabled = !saving,
                                            modifier = Modifier.testTag("rent_batch_status_${row.unitId}")) { Text(row.status) }
                                        DropdownMenu(expanded, { expanded = false }) {
                                            InitialRentInput.statuses.forEach { status -> DropdownMenuItem(text = { Text(status) }, onClick = {
                                                change(row.copy(status = status)); expanded = false
                                            }) }
                                        }
                                    }
                                }
                            }
                            if (preview) Text("${row.status} · ${row.tenant.ifBlank { "leer" }} · ${row.start.ifBlank { "—" }} · Kalt ${row.cold} € · NK ${row.utilities} € · Sonstiges ${row.other} €")
                            else if (!enabled) Text("Vorhandene Mietdaten: Änderungen über Mietverlauf / Mieterwechsel.", color = SlateGray)
                            else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                BatchField(row.tenant, "Mieter", "tenant_${row.unitId}", 160, enabled) { change(row.copy(tenant = it)) }
                                BatchField(row.start, "Mietbeginn", "start_${row.unitId}", 140, enabled) { change(row.copy(start = it)) }
                                BatchField(row.cold, "Kaltmiete €", "cold_${row.unitId}", 110, enabled, true) { change(row.copy(cold = it)) }
                                BatchField(row.utilities, "NK €", "utilities_${row.unitId}", 100, enabled, true) { change(row.copy(utilities = it)) }
                                BatchField(row.other, "Sonstiges €", "other_${row.unitId}", 110, enabled, true) { change(row.copy(other = it)) }
                            }
                            errors[row.unitId]?.let { Text(it, color = CrimsonRed) }
                        }
                    }
                }
                errors["save"]?.let { Text(it, color = CrimsonRed) }
            }
        }, confirmButton = {
            Button(onClick = {
                errors = selected.mapNotNull { row -> InitialRentInput.error(row)?.let { row.unitId to it } }.toMap()
                if (selected.isEmpty()) errors = mapOf("save" to "Bitte mindestens eine Zeile markieren.")
                if (errors.isNotEmpty()) return@Button
                if (!preview) preview = true else {
                    saving = true
                    scope.launch {
                        val error = viewModel.saveInitialRentBatch(property.propertyId, selected)
                        saving = false
                        if (error == null) { onSaved(); onDismiss() }
                        else { preview = false; errors = mapOf("save" to error) }
                    }
                }
            }, enabled = !saving, modifier = Modifier.testTag("rent_batch_confirm")) {
                Text(if (saving) "Speichern …" else if (preview) "${selected.size} Einheiten speichern" else "Vorschau")
            }
        }, dismissButton = {
            if (preview) TextButton(onClick = { preview = false }, enabled = !saving) { Text("Zurück zur Tabelle") }
            else TextButton(onClick = onDismiss, enabled = !saving) { Text("Abbrechen") }
        })
}

@Composable
private fun BatchField(value: String, label: String, tag: String, width: Int, enabled: Boolean,
    amount: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, enabled = enabled,
        shape = Ui2.controlShape, keyboardOptions = KeyboardOptions(keyboardType = if (amount) KeyboardType.Decimal else KeyboardType.Text),
        modifier = Modifier.width(width.dp).testTag("rent_batch_$tag"))
}
