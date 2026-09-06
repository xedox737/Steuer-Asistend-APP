package com.example.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.StableDocumentIdentity
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.max

internal data class TenantPeriod(
    val id: Long,
    val unitName: String,
    val tenantName: String,
    val startDate: String,
    val endDate: String,
    val kaltmiete: Double,
    val nebenkosten: Double,
    val sonstige: Double
) {
    val monatSoll: Double get() = kaltmiete + nebenkosten + sonstige
    val active: Boolean get() = endDate.isBlank()
}

internal object TenantHistoryStore {
    private const val PREFS = "tenant_history_prefs"

    private fun legacyKey(unitName: String) = "history_$unitName"
    private fun scopedKey(propertyId: String, unitId: String) = "history_v2_${propertyId}_$unitId"

    private fun decode(raw: String, unitName: String): List<TenantPeriod> = try {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    TenantPeriod(
                        id = o.optLong("id", i.toLong() + 1),
                        unitName = unitName,
                        tenantName = o.optString("tenantName"),
                        startDate = o.optString("startDate"),
                        endDate = o.optString("endDate"),
                        kaltmiete = o.optDouble("kaltmiete", 0.0),
                        nebenkosten = o.optDouble("nebenkosten", 0.0),
                        sonstige = o.optDouble("sonstige", 0.0)
                    )
                )
            }
        }.sortedBy { it.startDate }
    } catch (_: Exception) {
        emptyList()
    }

    private fun encode(periods: List<TenantPeriod>): String {
        val arr = JSONArray()
        periods.sortedBy { it.startDate }.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("tenantName", p.tenantName)
                put("startDate", p.startDate)
                put("endDate", p.endDate)
                put("kaltmiete", p.kaltmiete)
                put("nebenkosten", p.nebenkosten)
                put("sonstige", p.sonstige)
            })
        }
        return arr.toString()
    }

    fun load(context: Context, propertyId: String, unitId: String, unitName: String): List<TenantPeriod> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stableUnitId = unitId.ifBlank { StableDocumentIdentity.legacyUnitId(propertyId, unitName) }
        val scoped = scopedKey(propertyId, stableUnitId)
        prefs.getString(scoped, null)?.let { return decode(it, unitName) }

        // Backward-compatible lazy migration only for the historical object.
        // The legacy entry is copied, never deleted.
        if (propertyId == StableDocumentIdentity.LEGACY_PROPERTY_ID) {
            prefs.getString(legacyKey(unitName), null)?.let { raw ->
                prefs.edit().putString(scoped, raw).apply()
                return decode(raw, unitName)
            }
        }
        return emptyList()
    }

    fun save(context: Context, propertyId: String, unitId: String, unitName: String, periods: List<TenantPeriod>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stableUnitId = unitId.ifBlank { StableDocumentIdentity.legacyUnitId(propertyId, unitName) }
        val raw = encode(periods)
        prefs.edit().putString(scopedKey(propertyId, stableUnitId), raw).apply()
        // Keep historical readers compatible for property-1, but never create a
        // name-only key for another property.
        if (propertyId == StableDocumentIdentity.LEGACY_PROPERTY_ID) {
            prefs.edit().putString(legacyKey(unitName), raw).apply()
        }
    }

    /** Compatibility API for existing non-property-aware callers. */
    fun load(context: Context, unitName: String): List<TenantPeriod> {
        val propertyId = PropertyUnitScopedData.selectedPropertyId(context)
        val unitId = StableDocumentIdentity.legacyUnitId(propertyId, unitName)
        return load(context, propertyId, unitId, unitName)
    }

    /** Compatibility API for existing non-property-aware callers. */
    fun save(context: Context, unitName: String, periods: List<TenantPeriod>) {
        val propertyId = PropertyUnitScopedData.selectedPropertyId(context)
        val unitId = StableDocumentIdentity.legacyUnitId(propertyId, unitName)
        save(context, propertyId, unitId, unitName, periods)
    }

    fun ensureCurrentPeriod(
        context: Context,
        unit: WohneinheitStatus,
        nebenkosten: Double,
        sonstige: Double,
        propertyId: String = PropertyUnitScopedData.selectedPropertyId(context)
    ): List<TenantPeriod> {
        val unitId = PropertyUnitScopedData.stableUnitId(propertyId, unit)
        val existing = load(context, propertyId, unitId, unit.name)
        if (existing.isNotEmpty() || unit.mieter.isBlank() || unit.status != "Vermietet") return existing
        val initial = TenantPeriod(
            id = System.currentTimeMillis(),
            unitName = unit.name,
            tenantName = unit.mieter,
            startDate = unit.mietvertragsstart.ifBlank { "" },
            endDate = "",
            kaltmiete = unit.kaltmiete,
            nebenkosten = nebenkosten,
            sonstige = sonstige
        )
        save(context, propertyId, unitId, unit.name, listOf(initial))
        return listOf(initial)
    }

    fun expectedForYear(periods: List<TenantPeriod>, year: Int): Double {
        if (periods.isEmpty()) return 0.0
        val yearStart = LocalDate.of(year, 1, 1)
        val yearEnd = LocalDate.of(year, 12, 31)
        return periods.sumOf { p ->
            val start = runCatching { LocalDate.parse(p.startDate) }.getOrNull() ?: yearStart
            val end = runCatching { LocalDate.parse(p.endDate) }.getOrNull() ?: yearEnd
            val from = if (start.isAfter(yearStart)) start else yearStart
            val to = if (end.isBefore(yearEnd)) end else yearEnd
            if (to.isBefore(from)) 0.0 else proratedMonthlyAmount(p.monatSoll, from, to)
        }
    }

    private fun proratedMonthlyAmount(monthly: Double, start: LocalDate, end: LocalDate): Double {
        var cursor = start.withDayOfMonth(1)
        val lastMonth = end.withDayOfMonth(1)
        var total = 0.0
        while (!cursor.isAfter(lastMonth)) {
            val ym = YearMonth.from(cursor)
            val monthStart = ym.atDay(1)
            val monthEnd = ym.atEndOfMonth()
            val occupiedStart = if (start.isAfter(monthStart)) start else monthStart
            val occupiedEnd = if (end.isBefore(monthEnd)) end else monthEnd
            if (!occupiedEnd.isBefore(occupiedStart)) {
                val days = ChronoUnit.DAYS.between(occupiedStart, occupiedEnd).toDouble() + 1.0
                total += monthly * (days / ym.lengthOfMonth().toDouble())
            }
            cursor = cursor.plusMonths(1)
        }
        return max(0.0, total)
    }
}

private fun parseTenantNumber(value: String): Double? =
    value.trim().replace(".", "").replace(',', '.').toDoubleOrNull()

@Composable
internal fun TenantHistoryDialog(
    unit: WohneinheitStatus,
    nebenkostenCurrent: Double,
    sonstigeCurrent: Double,
    onDismiss: () -> Unit,
    onCurrentTenantChanged: (TenantPeriod) -> Unit,
    onHistoryChanged: () -> Unit,
    propertyId: String = StableDocumentIdentity.LEGACY_PROPERTY_ID
) {
    val context = LocalContext.current
    val unitId = PropertyUnitScopedData.stableUnitId(propertyId, unit)
    var version by remember { mutableStateOf(0) }
    var periods by remember(propertyId, unitId, version) {
        mutableStateOf(TenantHistoryStore.ensureCurrentPeriod(context, unit, nebenkostenCurrent, sonstigeCurrent, propertyId))
    }
    var showChange by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mieterwechsel · ${unit.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Alte Mietverhältnisse bleiben erhalten. Ein neuer Wechsel beendet den bisherigen Vertrag und legt einen neuen Vertrag an.", fontSize = 10.sp, color = SlateGray)
                Button(
                    onClick = { showChange = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(" Mieterwechsel erfassen")
                }
                if (periods.isEmpty()) {
                    Text("Noch keine Mieterhistorie vorhanden.", fontSize = 11.sp, color = SlateGray)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 390.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(periods.sortedByDescending { it.startDate }, key = { it.id }) { p ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = SoftBackground),
                                border = BorderStroke(1.dp, BorderColor),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(p.tenantName.ifBlank { "Mieter ohne Namen" }, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkNavy)
                                        Text(if (p.active) "AKTUELL" else "BEENDET", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (p.active) EmeraldGreen else SlateGray)
                                    }
                                    Text("${p.startDate.ifBlank { "Start unbekannt" }} bis ${p.endDate.ifBlank { "heute" }}", fontSize = 10.sp, color = SlateGray)
                                    HorizontalDivider(color = BorderColor)
                                    Text("Kalt ${NumberFormatter.format(p.kaltmiete)} · NK ${NumberFormatter.format(p.nebenkosten)} · Sonst. ${NumberFormatter.format(p.sonstige)}", fontSize = 9.sp, color = SlateGray)
                                    Text("Monatliches Soll ${NumberFormatter.format(p.monatSoll)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )

    if (showChange) {
        TenantChangeDialog(
            unit = unit,
            current = periods.lastOrNull { it.active },
            defaultNk = nebenkostenCurrent,
            defaultOther = sonstigeCurrent,
            onDismiss = { showChange = false },
            onSave = { exitDate, newPeriod ->
                val updated = periods.map { p ->
                    if (p.active) p.copy(endDate = exitDate) else p
                } + newPeriod
                TenantHistoryStore.save(context, propertyId, unitId, unit.name, updated)
                periods = updated
                version++
                onCurrentTenantChanged(newPeriod)
                onHistoryChanged()
                showChange = false
            }
        )
    }
}

@Composable
private fun TenantChangeDialog(
    unit: WohneinheitStatus,
    current: TenantPeriod?,
    defaultNk: Double,
    defaultOther: Double,
    onDismiss: () -> Unit,
    onSave: (String, TenantPeriod) -> Unit
) {
    var oldEnd by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newStart by remember { mutableStateOf("") }
    var cold by remember { mutableStateOf(unit.kaltmiete.toString()) }
    var nk by remember { mutableStateOf(defaultNk.toString()) }
    var other by remember { mutableStateOf(defaultOther.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    val keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mieterwechsel erfassen", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (current != null) {
                    item { Text("Bisher: ${current.tenantName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy) }
                    item { OutlinedTextField(oldEnd, { oldEnd = it }, label = { Text("Auszug bisheriger Mieter YYYY-MM-DD*") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                }
                item { OutlinedTextField(newName, { newName = it }, label = { Text("Neuer Mieter / Mietpartei*") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(newStart, { newStart = it }, label = { Text("Einzug / Mietbeginn YYYY-MM-DD*") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(cold, { cold = it }, label = { Text("Neue Kaltmiete / Monat €*") }, keyboardOptions = keyboard, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(nk, { nk = it }, label = { Text("Neue NK-Vorauszahlung / Monat €") }, keyboardOptions = keyboard, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(other, { other = it }, label = { Text("Sonstige Mietbestandteile / Monat €") }, keyboardOptions = keyboard, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                error?.let { item { Text(it, fontSize = 10.sp, color = CrimsonRed, fontWeight = FontWeight.Bold) } }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val startDate = runCatching { LocalDate.parse(newStart.trim()) }.getOrNull()
                    val endDate = if (current != null) runCatching { LocalDate.parse(oldEnd.trim()) }.getOrNull() else null
                    val c = parseTenantNumber(cold)
                    val n = parseTenantNumber(nk) ?: 0.0
                    val o = parseTenantNumber(other) ?: 0.0
                    when {
                        current != null && endDate == null -> error = "Bitte ein gültiges Auszugsdatum eingeben."
                        startDate == null -> error = "Bitte ein gültiges Einzugsdatum eingeben."
                        current != null && endDate != null && startDate.isBefore(endDate.plusDays(1)) -> error = "Der neue Mietbeginn muss nach dem Auszug des bisherigen Mieters liegen."
                        newName.isBlank() -> error = "Bitte den neuen Mieter eintragen."
                        c == null || c < 0 || n < 0 || o < 0 -> error = "Bitte gültige Mietbeträge eingeben."
                        else -> onSave(
                            oldEnd.trim(),
                            TenantPeriod(
                                id = System.currentTimeMillis(),
                                unitName = unit.name,
                                tenantName = newName.trim(),
                                startDate = newStart.trim(),
                                endDate = "",
                                kaltmiete = c,
                                nebenkosten = n,
                                sonstige = o
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) { Text("Wechsel speichern") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
