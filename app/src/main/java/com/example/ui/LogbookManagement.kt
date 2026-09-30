package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.util.LogbookCsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

@Composable
internal fun LogbookDraftCards(drafts: Map<String, String>, viewModel: ReceiptViewModel, onResume: (Int) -> Unit) {
    var discard by remember { mutableStateOf<String?>(null) }
    drafts.forEach { (key, raw) ->
        val draft = runCatching { JSONObject(raw) }.getOrNull() ?: return@forEach
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) {
                Text("Entwurf: ${draft.optString("purpose").ifBlank { "Neue Fahrt" }}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onResume(key.toIntOrNull() ?: 0) }) { Text("Fortsetzen") }
                    TextButton(onClick = { discard = key }) { Text("Verwerfen") }
                }
            }
        }
    }
    discard?.let { key ->
        AlertDialog(onDismissRequest = { discard = null }, title = { Text("Entwurf verwerfen?") },
            text = { Text("Die ungespeicherten Eingaben dieser Fahrt werden gelöscht.") },
            confirmButton = { TextButton(onClick = { viewModel.clearLogbookDraft(key); discard = null }) { Text("Verwerfen") } },
            dismissButton = { TextButton(onClick = { discard = null }) { Text("Behalten") } })
    }
}

@Composable
internal fun LogbookReceiptPicker(receipts: List<Receipt>, onDismiss: () -> Unit, onSelect: (Receipt) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Beleg verknüpfen") }, text = {
        Column {
            OutlinedTextField(search, { search = it }, label = { Text("Beleg suchen") }, singleLine = true)
            Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                receipts.filter { "${it.aussteller} ${it.datum} ${it.beschreibung}".contains(search, true) }.forEach { receipt ->
                    TextButton(onClick = { onSelect(receipt) }) { Text("${receipt.aussteller} · ${receipt.datum}") }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } })
}

@Composable
internal fun LogbookPropertyPicker(properties: List<PropertyMetadata>, onDismiss: () -> Unit, onSelect: (PropertyMetadata) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Immobilie auswählen") }, text = {
        Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
            properties.forEach { property -> TextButton(onClick = { onSelect(property) }) { Text(property.adresse) } }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } })
}

@Composable
internal fun LogbookTripBrowser(trips: List<LogbookTrip>, onOpen: (LogbookTrip) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var search by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var month by rememberSaveable { mutableStateOf("") }
    var cancelled by rememberSaveable { mutableStateOf(false) }
    var csv by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val filtered = trips.filter { trip ->
        (cancelled || trip.cancelledAt.isBlank()) && (year.isBlank() || trip.date.startsWith(year)) &&
            (month.isBlank() || trip.date.substringAfter('-').substringBefore('-') == month) &&
            "${trip.purpose} ${trip.startAddress} ${trip.destinationAddress} ${trip.propertyReference}".contains(search, true)
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) {
                requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(("\uFEFF" + csv).toByteArray(Charsets.UTF_8)) }
            } }
            message = result.fold({ "CSV gespeichert." }, { "Export fehlgeschlagen: ${it.message}" })
        }
    }
    OutlinedTextField(search, { search = it }, label = { Text("Fahrten suchen") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LogbookFilter("Jahr", year, listOf("") + trips.map { it.date.take(4) }.distinct().sortedDescending()) { year = it }
        LogbookFilter("Monat", month, listOf("") + (1..12).map { it.toString().padStart(2, '0') }) { month = it }
    }
    Row {
        Checkbox(cancelled, { cancelled = it })
        Text("Stornierte Fahrten anzeigen", Modifier.padding(top = 12.dp))
    }
    OutlinedButton(onClick = { csv = LogbookCsvExporter.create(filtered); export.launch("Fahrtenbuch-${LocalDate.now()}.csv") }, enabled = filtered.isNotEmpty()) {
        Text("CSV exportieren (${filtered.size})")
    }
    message?.let { Text(it) }
    if (filtered.isEmpty()) Text("Keine passenden Fahrten.")
    filtered.forEach { trip ->
        Card(Modifier.fillMaxWidth().clickable { onOpen(trip) }) {
            Column(Modifier.padding(10.dp)) {
                Text("${trip.date} · ${trip.purpose}")
                Text("${trip.startAddress} → ${trip.destinationAddress}")
                Text(if (trip.cancelledAt.isBlank()) "${trip.taxDistanceKm} km" else "Storniert")
            }
        }
    }
}

@Composable
private fun LogbookFilter(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: ${selected.ifBlank { "Alle" }}") }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { value -> DropdownMenuItem(text = { Text(value.ifBlank { "Alle" }) }, onClick = { onSelect(value); expanded = false }) }
        }
    }
}

@Composable
internal fun LogbookManageActions(trip: LogbookTrip, viewModel: ReceiptViewModel) {
    val scope = rememberCoroutineScope()
    var action by remember { mutableStateOf<String?>(null) }
    var date by remember(trip.updatedAt) { mutableStateOf(trip.date) }
    var purpose by remember(trip.updatedAt) { mutableStateOf(trip.purpose) }
    var km by remember(trip.updatedAt) { mutableStateOf(trip.taxDistanceKm.toString().replace('.', ',')) }
    var reason by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    if (trip.cancelledAt.isBlank()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { action = "Korrigieren"; confirmed = false; reason = "" }) { Text("Korrigieren") }
        OutlinedButton(onClick = { action = "Stornieren"; confirmed = false; reason = "" }) { Text("Stornieren") }
    }
    message?.let { Text(it, color = CrimsonRed) }
    val history = runCatching { JSONArray(trip.historyJson) }.getOrDefault(JSONArray())
    if (history.length() > 0) {
        Text("Änderungsverlauf")
        for (index in 0 until history.length()) {
            val record = history.getJSONObject(index)
            Text("${record.optString("at")} · ${record.optString("action")}: ${record.optString("reason")}\nVorher: ${record.optString("date")} · ${record.optString("purpose")} · ${record.optDouble("km")} km")
        }
    }
    action?.let { currentAction ->
        AlertDialog(onDismissRequest = { if (!busy) action = null }, title = { Text("Fahrt $currentAction") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentAction == "Korrigieren") {
                    OutlinedTextField(date, { date = it }, label = { Text("Datum (JJJJ-MM-TT)") }, singleLine = true)
                    OutlinedTextField(purpose, { purpose = it }, label = { Text("Zweck") })
                    OutlinedTextField(km, { km = it }, label = { Text("Geprüfte Gesamtstrecke (km)") }, singleLine = true)
                }
                OutlinedTextField(reason, { reason = it }, label = { Text("Grund") })
                Text(if (currentAction == "Korrigieren") "Die Fahrt und ihr Kostenbeleg werden gemeinsam geändert. Die bisherige Fassung bleibt im Änderungsverlauf erhalten."
                    else "Die Fahrt bleibt als storniert erhalten. Ihr Kostenbeleg wird aus den aktiven Buchungen entfernt.")
                Row { Checkbox(confirmed, { confirmed = it }); Text("Angaben geprüft und Änderung bestätigen", Modifier.padding(top = 12.dp)) }
                message?.let { Text(it, color = CrimsonRed) }
            }
        }, confirmButton = {
            TextButton(enabled = confirmed && reason.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    val result = if (currentAction == "Stornieren") viewModel.cancelLogbookTrip(trip.id, reason.trim())
                        else viewModel.correctLogbookTrip(trip.id, date.trim(), purpose.trim(), km.replace(',', '.').toDoubleOrNull() ?: Double.NaN, reason.trim())
                    busy = false
                    result.fold({ message = null; action = null }, { message = it.message ?: "Änderung fehlgeschlagen." })
                }
            }) { Text(currentAction) }
        }, dismissButton = { TextButton(enabled = !busy, onClick = { action = null }) { Text("Abbrechen") } })
    }
}
