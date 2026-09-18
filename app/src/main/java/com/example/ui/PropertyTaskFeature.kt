package com.example.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal data class PropertyTask(
    val id: String,
    val propertyId: String,
    val unitId: String = "",
    val title: String,
    val note: String = "",
    val dueDate: String = "",
    val category: String = "Sonstiges",
    val done: Boolean = false,
    val createdAt: String,
    val updatedAt: String
) {
    val overdue: Boolean get() = !done && dueDate.isNotBlank() &&
        runCatching { LocalDate.parse(dueDate).isBefore(LocalDate.now()) }.getOrDefault(false)
}

internal object PropertyTaskStore {
    const val PREFS = "property_tasks_prefs"
    private fun key(propertyId: String) = "tasks_$propertyId"

    fun load(context: Context, propertyId: String): List<PropertyTask> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(propertyId), null)
            ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        PropertyTask(
                            id = o.optString("id"),
                            propertyId = o.optString("propertyId", propertyId),
                            unitId = o.optString("unitId"),
                            title = o.optString("title"),
                            note = o.optString("note"),
                            dueDate = o.optString("dueDate"),
                            category = o.optString("category", "Sonstiges"),
                            done = o.optBoolean("done", false),
                            createdAt = o.optString("createdAt"),
                            updatedAt = o.optString("updatedAt")
                        )
                    )
                }
            }.filter { it.propertyId == propertyId }
                .sortedWith(compareBy<PropertyTask> { it.done }.thenBy { it.dueDate.ifBlank { "9999-99-99" } }.thenBy { it.title })
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, propertyId: String, tasks: List<PropertyTask>) {
        val arr = JSONArray()
        tasks.filter { it.propertyId == propertyId }.forEach { task ->
            arr.put(JSONObject().apply {
                put("id", task.id)
                put("propertyId", task.propertyId)
                put("unitId", task.unitId)
                put("title", task.title)
                put("note", task.note)
                put("dueDate", task.dueDate)
                put("category", task.category)
                put("done", task.done)
                put("createdAt", task.createdAt)
                put("updatedAt", task.updatedAt)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key(propertyId), arr.toString()).apply()
    }

    fun upsert(context: Context, task: PropertyTask) {
        val existing = load(context, task.propertyId).toMutableList()
        val index = existing.indexOfFirst { it.id == task.id }
        if (index >= 0) existing[index] = task else existing.add(task)
        save(context, task.propertyId, existing)
    }
}

@Composable
internal fun PropertyTasksScreen(propertyId: String, units: List<WohneinheitStatus>) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    val tasks = remember(propertyId, version) { PropertyTaskStore.load(context, propertyId) }

    if (showAdd) {
        PropertyTaskBottomSheet(propertyId, units, onDismiss = { showAdd = false }) { task ->
            PropertyTaskStore.upsert(context, task)
            version++
            showAdd = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Aufgaben & Fristen", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text("Für diese Immobilie", fontSize = 12.sp, color = SlateGray)
                }
                Button(
                    onClick = { showAdd = true },
                    modifier = Modifier.widthIn(min = 176.dp).heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Icon(Icons.Default.Add, null)
                    Text(" Aufgabe hinzufügen", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        if (tasks.isEmpty()) {
            item { TaskEmptyState() }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3FF)), border = BorderStroke(1.dp, Color(0xFFD9E9FF))) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, tint = AccentBlue)
                        Text("Tipp: Fälligkeiten werden hier übersichtlich gesammelt.", fontSize = 12.sp, color = AccentBlue)
                    }
                }
            }
        }
        items(tasks, key = { it.id }) { task ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = {
                        PropertyTaskStore.upsert(
                            context,
                            task.copy(done = !task.done, updatedAt = java.time.Instant.now().toString())
                        )
                        version++
                    }) {
                        Icon(if (task.done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (task.done) EmeraldGreen else AccentBlue)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(task.title, fontWeight = FontWeight.Bold, color = if (task.done) SlateGray else DarkNavy)
                        Text(task.category + if (task.dueDate.isNotBlank()) " · fällig ${task.dueDate}" else "", fontSize = 10.sp, color = if (task.overdue) CrimsonRed else SlateGray)
                        if (task.overdue) Text("ÜBERFÄLLIG", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = CrimsonRed)
                        if (task.note.isNotBlank()) Text(task.note, fontSize = 10.sp, color = SlateGray)
                        if (task.unitId.isNotBlank()) {
                            val label = units.firstOrNull { PropertyUnitScopedData.stableUnitId(propertyId, it) == task.unitId }?.label ?: "Einheit"
                            Text(label, fontSize = 9.sp, color = AccentBlue)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskEmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = Ui2.shape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.padding(10.dp), tint = AccentBlue)
            Text("Noch keine Aufgaben", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            Text("Lege Erinnerungen für Mieter, Wartung oder Fristen an.", fontSize = 12.sp, color = SlateGray)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PropertyTaskBottomSheet(
    propertyId: String,
    units: List<WohneinheitStatus>,
    onDismiss: () -> Unit,
    onSave: (PropertyTask) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Sonstiges") }
    var selectedUnit by remember { mutableStateOf<WohneinheitStatus?>(null) }
    var showUnitPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val categories = listOf("Mieter", "Wartung", "Sanierung", "Steuer", "Sonstiges")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Aufgabe hinzufügen", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { OutlinedTextField(title, { title = it }, label = { Text("Titel") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(note, { note = it }, label = { Text("Notiz (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3) }
                item {
                    OutlinedTextField(
                        due,
                        { due = it },
                        label = { Text("Fällig am (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.CalendarMonth, null, tint = SlateGray) }
                    )
                }
                item {
                    Text("Kategorie", fontSize = 12.sp, color = SlateGray)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.forEach { value ->
                            FilterChip(selected = category == value, onClick = { category = value }, label = { Text(value, fontSize = 12.sp) })
                        }
                    }
                }
                item {
                    Text("Einheit (optional)", fontSize = 12.sp, color = SlateGray)
                    OutlinedButton(onClick = { showUnitPicker = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(selectedUnit?.label ?: "Keine Einheit ausgewählt", Modifier.weight(1f), fontSize = 13.sp)
                        Text("Auswählen", color = AccentBlue, fontSize = 12.sp)
                    }
                }
                error?.let { message -> item { Text(message, color = CrimsonRed, fontSize = 10.sp) } }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Abbrechen") }
                Button(onClick = {
                val dueValid = due.isBlank() || runCatching { LocalDate.parse(due) }.isSuccess
                if (title.isBlank()) error = "Bitte einen Titel eingeben."
                else if (!dueValid) error = "Fälligkeitsdatum bitte als YYYY-MM-DD eingeben."
                else {
                    val now = java.time.Instant.now().toString()
                    onSave(
                        PropertyTask(
                            id = java.util.UUID.randomUUID().toString(),
                            propertyId = propertyId,
                            unitId = selectedUnit?.let { PropertyUnitScopedData.stableUnitId(propertyId, it) }.orEmpty(),
                            title = title.trim(),
                            note = note.trim(),
                            dueDate = due.trim(),
                            category = category.trim().ifBlank { "Sonstiges" },
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
                }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)) { Text("Speichern") }
            }
        }
    }
    if (showUnitPicker) {
        AlertDialog(
            onDismissRequest = { showUnitPicker = false },
            title = { Text("Einheit auswählen", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item { TextButton(onClick = { selectedUnit = null; showUnitPicker = false }, modifier = Modifier.fillMaxWidth()) { Text("Keine Einheit") } }
                    items(units, key = { PropertyUnitScopedData.stableUnitId(propertyId, it) }) { unit ->
                        TextButton(onClick = { selectedUnit = unit; showUnitPicker = false }, modifier = Modifier.fillMaxWidth()) { Text(unit.label) }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
