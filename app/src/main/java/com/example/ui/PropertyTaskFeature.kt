package com.example.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
        PropertyTaskDialog(propertyId, units, onDismiss = { showAdd = false }) { task ->
            PropertyTaskStore.upsert(context, task)
            version++
            showAdd = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Aufgaben & Fristen", fontSize = 20.sp, fontWeight = FontWeight.Black, color = DarkNavy)
                    Text("Lokal und eindeutig dieser Immobilie zugeordnet", fontSize = 10.sp, color = SlateGray)
                }
                Button(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null); Text(" Aufgabe") }
            }
        }
        if (tasks.isEmpty()) item { Text("Keine Aufgaben vorhanden.", color = SlateGray) }
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
private fun PropertyTaskDialog(
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
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aufgabe hinzufügen", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Titel*") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Notiz") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(due, { due = it }, label = { Text("Fällig YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(category, { category = it }, label = { Text("Kategorie") }, supportingText = { Text("Mieter, Finanzierung, Versicherung, Wartung, Sanierung, Steuer oder Sonstiges") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Einheit optional", fontSize = 10.sp, color = SlateGray)
                units.forEach { unit ->
                    Text(
                        text = if (selectedUnit?.unitId == unit.unitId) "✓ ${unit.label}" else unit.label,
                        modifier = Modifier.fillMaxWidth().clickable { selectedUnit = if (selectedUnit?.unitId == unit.unitId) null else unit }.padding(vertical = 5.dp),
                        fontSize = 10.sp,
                        color = if (selectedUnit?.unitId == unit.unitId) AccentBlue else DarkNavy
                    )
                }
                error?.let { Text(it, color = CrimsonRed, fontSize = 10.sp) }
            }
        },
        confirmButton = {
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
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
