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
import androidx.compose.ui.platform.testTag
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

internal data class RentAmounts(
    val kaltmiete: Double,
    val nebenkosten: Double,
    val sonstige: Double
) {
    val monatSoll: Double get() = kaltmiete + nebenkosten + sonstige
}

internal data class RentAmountChange(
    val effectiveDate: String,
    val kaltmiete: Double,
    val nebenkosten: Double,
    val sonstige: Double
) {
    fun amounts(): RentAmounts = RentAmounts(kaltmiete, nebenkosten, sonstige)
}

internal data class TenantPeriod(
    val id: Long,
    val unitName: String,
    val tenantName: String,
    val startDate: String,
    val endDate: String,
    val kaltmiete: Double,
    val nebenkosten: Double,
    val sonstige: Double,
    val rentChanges: List<RentAmountChange> = emptyList()
) {
    val monatSoll: Double get() = kaltmiete + nebenkosten + sonstige
    val active: Boolean get() = endDate.isBlank()

    fun amountsAt(date: LocalDate): RentAmounts {
        val change = rentChanges.mapNotNull { entry ->
            CalendarInput.parseIsoDate(entry.effectiveDate)?.let { it to entry }
        }.filter { (effective, _) -> !effective.isAfter(date) }
            .maxByOrNull { (effective, _) -> effective }
            ?.second
        return change?.amounts() ?: RentAmounts(kaltmiete, nebenkosten, sonstige)
    }
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
                        sonstige = o.optDouble("sonstige", 0.0),
                        rentChanges = o.optJSONArray("rentChanges")?.let { changes ->
                            buildList {
                                for (j in 0 until changes.length()) {
                                    val change = changes.optJSONObject(j) ?: continue
                                    val effectiveDate = change.optString("effectiveDate")
                                    val kaltmiete = change.optDouble("kaltmiete", Double.NaN)
                                    val nebenkosten = change.optDouble("nebenkosten", Double.NaN)
                                    val sonstige = change.optDouble("sonstige", Double.NaN)
                                    if (CalendarInput.isValidIsoDate(effectiveDate) &&
                                        kaltmiete.isFinite() && nebenkosten.isFinite() && sonstige.isFinite()
                                    ) {
                                        add(RentAmountChange(effectiveDate, kaltmiete, nebenkosten, sonstige))
                                    }
                                }
                            }.sortedBy { it.effectiveDate }
                        }.orEmpty()
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
                put("rentChanges", JSONArray().apply {
                    p.rentChanges.sortedBy { it.effectiveDate }.forEach { change ->
                        put(JSONObject().apply {
                            put("effectiveDate", change.effectiveDate)
                            put("kaltmiete", change.kaltmiete)
                            put("nebenkosten", change.nebenkosten)
                            put("sonstige", change.sonstige)
                        })
                    }
                })
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

    fun save(context: Context, propertyId: String, unitId: String, unitName: String, periods: List<TenantPeriod>) = synchronized(this) {
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

    /** Commit the initial contractual truth of all rented rows as one preference write. */
    fun saveInitialPeriods(context: Context, propertyId: String, entries: List<Pair<WohneinheitStatus, TenantPeriod>>): Boolean = synchronized(this) {
        if (entries.any { (unit, _) -> load(context, propertyId, PropertyUnitScopedData.stableUnitId(propertyId, unit), unit.name).isNotEmpty() }) return@synchronized false
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        entries.forEach { (unit, period) ->
            val raw = encode(listOf(period))
            editor.putString(scopedKey(propertyId, PropertyUnitScopedData.stableUnitId(propertyId, unit)), raw)
            if (propertyId == StableDocumentIdentity.LEGACY_PROPERTY_ID) editor.putString(legacyKey(unit.name), raw)
        }
        editor.commit()
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

    fun changeTenant(
        periods: List<TenantPeriod>, exitDate: String, newPeriod: TenantPeriod,
        currentId: Long? = (currentAt(periods) ?: periods.lastOrNull { it.active })?.id
    ): List<TenantPeriod> {
        val error = TenantChronology.changeError(periods, currentId, exitDate, newPeriod.startDate)
        require(error == null) { error?.message.orEmpty() }
        require(periods.none { it.id == newPeriod.id }) { "Der neue Vertrag benötigt eine eindeutige Identität." }
        val updated = periods.map { if (it.id == currentId) it.copy(endDate = exitDate.trim()) else it } + newPeriod
        require(TenantChronology.periodsError(updated) == null) { "Ungültige Vertragschronologie." }
        return updated
    }

    /** One contractual source for today's tenant and rent; future/invalid periods do not win. */
    fun currentAt(periods: List<TenantPeriod>, date: LocalDate = LocalDate.now()): TenantPeriod? =
        periods.filter { period ->
            val start = if (period.startDate.isBlank()) LocalDate.MIN else CalendarInput.parseIsoDate(period.startDate)
            val end = if (period.endDate.isBlank()) LocalDate.MAX else CalendarInput.parseIsoDate(period.endDate)
            start != null && end != null && !end.isBefore(start) && !date.isBefore(start) && !date.isAfter(end)
        }.maxByOrNull { CalendarInput.parseIsoDate(it.startDate) ?: LocalDate.MIN }

    /** Explicit corrections from the simple editor must update the same contractual source. */
    fun correctCurrentRentalDetails(context: Context, propertyId: String, unitId: String, unit: WohneinheitStatus) {
        val periods = load(context, propertyId, unitId, unit.name)
        val today = LocalDate.now()
        val current = currentAt(periods, today) ?: return
        if (unit.status != "Vermietet") return
        val latestChange = current.rentChanges.filter {
            CalendarInput.parseIsoDate(it.effectiveDate)?.let { date -> !date.isAfter(today) } == true
        }.maxByOrNull { it.effectiveDate }
        val corrected = current.copy(
            tenantName = unit.mieter,
            startDate = unit.mietvertragsstart,
            kaltmiete = if (latestChange == null) unit.kaltmiete else current.kaltmiete,
            rentChanges = current.rentChanges.map { change ->
                if (change === latestChange) change.copy(kaltmiete = unit.kaltmiete) else change
            }
        )
        if (corrected != current) save(context, propertyId, unitId, unit.name,
            periods.map { if (it.id == current.id) corrected else it })
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

    fun expectedInMonth(period: TenantPeriod, month: YearMonth): Double {
        val monthStart = month.atDay(1)
        val monthEnd = month.atEndOfMonth()
        val start = CalendarInput.parseIsoDate(period.startDate) ?: monthStart
        val end = CalendarInput.parseIsoDate(period.endDate) ?: monthEnd
        val from = if (start.isAfter(monthStart)) start else monthStart
        val to = if (end.isBefore(monthEnd)) end else monthEnd
        if (to.isBefore(from)) return 0.0
        val days = ChronoUnit.DAYS.between(from, to).toDouble() + 1.0
        val monthly = period.amountsAt(monthStart).monatSoll
        return max(0.0, monthly * (days / month.lengthOfMonth().toDouble()))
    }

    fun expectedForYear(periods: List<TenantPeriod>, year: Int): Double =
        (1..12).sumOf { month ->
            val yearMonth = YearMonth.of(year, month)
            periods.sumOf { period -> expectedInMonth(period, yearMonth) }
        }
}

internal fun parseTenantNumber(value: String): Double? =
    GermanNumberInput.parseNonNegative(value)

@Composable
internal fun TenantHistoryDialog(
    unit: WohneinheitStatus,
    nebenkostenCurrent: Double,
    sonstigeCurrent: Double,
    onDismiss: () -> Unit,
    onCurrentTenantChanged: (TenantPeriod) -> Unit,
    onHistoryChanged: () -> Unit,
    propertyId: String = StableDocumentIdentity.LEGACY_PROPERTY_ID,
    initiallyShowChange: Boolean = false
) {
    val context = LocalContext.current
    val unitId = PropertyUnitScopedData.stableUnitId(propertyId, unit)
    var version by remember { mutableStateOf(0) }
    var periods by remember(propertyId, unitId, version) {
        mutableStateOf(TenantHistoryStore.ensureCurrentPeriod(context, unit, nebenkostenCurrent, sonstigeCurrent, propertyId))
    }
    var showChange by remember { mutableStateOf(initiallyShowChange) }

    if (!initiallyShowChange || !showChange) {
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
                                        Text(TenantChronology.status(p), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (TenantChronology.status(p) == "AKTUELL") EmeraldGreen else SlateGray)
                                    }
                                    Text("${p.startDate.ifBlank { "Start unbekannt" }} bis ${p.endDate.ifBlank { "unbefristet" }}", fontSize = 10.sp, color = SlateGray)
                                    HorizontalDivider(color = BorderColor)
                                    Text("Kalt ${NumberFormatter.format(p.kaltmiete)} · NK ${NumberFormatter.format(p.nebenkosten)} · Sonst. ${NumberFormatter.format(p.sonstige)}", fontSize = 9.sp, color = SlateGray)
                                    Text("Ausgangs-Soll ${NumberFormatter.format(p.monatSoll)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                                    p.rentChanges.sortedBy { it.effectiveDate }.forEach { change ->
                                        Text(
                                            "Mietänderung ab ${change.effectiveDate}: Kalt ${NumberFormatter.format(change.kaltmiete)} · NK ${NumberFormatter.format(change.nebenkosten)} · Sonst. ${NumberFormatter.format(change.sonstige)}",
                                            fontSize = 9.sp,
                                            lineHeight = 12.sp,
                                            color = AccentBlue
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )

    }

    if (showChange) {
        val activePeriod = TenantHistoryStore.currentAt(periods) ?: periods.lastOrNull { it.active }
        val currentAmounts = activePeriod?.amountsAt(LocalDate.now())
            ?: RentAmounts(unit.kaltmiete, nebenkostenCurrent, sonstigeCurrent)
        TenantChangeDialog(
            unit = unit,
            current = activePeriod,
            periods = periods,
            defaultCold = currentAmounts.kaltmiete,
            defaultNk = currentAmounts.nebenkosten,
            defaultOther = currentAmounts.sonstige,
            onDismiss = { showChange = false; if (initiallyShowChange) onDismiss() },
            onSave = { exitDate, newPeriod ->
                val updated = TenantHistoryStore.changeTenant(periods, exitDate, newPeriod, activePeriod?.id)
                TenantHistoryStore.save(context, propertyId, unitId, unit.name, updated)
                periods = updated
                version++
                TenantHistoryStore.currentAt(updated)?.let { current ->
                    val amounts = current.amountsAt(LocalDate.now())
                    onCurrentTenantChanged(current.copy(kaltmiete = amounts.kaltmiete, nebenkosten = amounts.nebenkosten, sonstige = amounts.sonstige))
                }
                onHistoryChanged()
                showChange = false
                if (initiallyShowChange) onDismiss()
            }
        )
    }
}

@Composable
private fun TenantChangeDialog(
    unit: WohneinheitStatus,
    current: TenantPeriod?,
    periods: List<TenantPeriod>,
    defaultCold: Double,
    defaultNk: Double,
    defaultOther: Double,
    onDismiss: () -> Unit,
    onSave: (String, TenantPeriod) -> Unit
) {
    var oldEnd by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newStart by remember { mutableStateOf("") }
    var cold by remember { mutableStateOf(GermanNumberInput.formatForInput(defaultCold)) }
    var nk by remember { mutableStateOf(GermanNumberInput.formatForInput(defaultNk)) }
    var other by remember { mutableStateOf(GermanNumberInput.formatForInput(defaultOther)) }
    var error by remember { mutableStateOf<String?>(null) }
    var chronologyError by remember { mutableStateOf<TenantChronologyError?>(null) }
    val keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mieterwechsel erfassen", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (current != null) {
                    item { Text("Bisher: ${current.tenantName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy) }
                    item { OutlinedTextField(oldEnd, { oldEnd = it; chronologyError = null }, label = { Text("Auszug bisheriger Mieter YYYY-MM-DD*") }, isError = chronologyError?.field == TenantChronology.OLD_END, supportingText = { if (chronologyError?.field == TenantChronology.OLD_END) Text(chronologyError!!.message) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("tenant_change_old_end")) }
                }
                item { OutlinedTextField(newName, { newName = it }, label = { Text("Neuer Mieter / Mietpartei*") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("tenant_change_name")) }
                item { OutlinedTextField(newStart, { newStart = it; chronologyError = null }, label = { Text("Einzug / Mietbeginn YYYY-MM-DD*") }, isError = chronologyError?.field == TenantChronology.NEW_START, supportingText = { if (chronologyError?.field == TenantChronology.NEW_START) Text(chronologyError!!.message) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("tenant_change_new_start")) }
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
                    val n = if (nk.isBlank()) 0.0 else parseTenantNumber(nk)
                    val o = if (other.isBlank()) 0.0 else parseTenantNumber(other)
                    chronologyError = TenantChronology.changeError(periods, current?.id, oldEnd, newStart)
                    when {
                        chronologyError != null -> error = null
                        current != null && endDate == null -> error = "Bitte ein gültiges Auszugsdatum eingeben."
                        startDate == null -> error = "Bitte ein gültiges Einzugsdatum eingeben."
                        current != null && endDate != null && startDate.isBefore(endDate.plusDays(1)) -> error = "Der neue Mietbeginn muss nach dem Auszug des bisherigen Mieters liegen."
                        newName.isBlank() -> error = "Bitte den neuen Mieter eintragen."
                        c == null || n == null || o == null -> error = "Bitte gültige Mietbeträge eingeben."
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
