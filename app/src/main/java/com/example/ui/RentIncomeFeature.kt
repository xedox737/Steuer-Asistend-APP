package com.example.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Receipt
import kotlin.math.max

private data class RentPlan(
    val unit: WohneinheitStatus,
    val nebenkosten: Double,
    val sonstige: Double
) {
    val monatSoll: Double get() = unit.kaltmiete + nebenkosten + sonstige
}

private data class UnitRentYear(
    val plan: RentPlan,
    val ist: Double,
    val soll: Double,
    val kaltIst: Double,
    val warmPauschalIst: Double,
    val sonstigeIst: Double,
    val betriebskostenNachzahlung: Double
) {
    val differenz: Double get() = ist - soll
    val rueckstand: Double get() = max(0.0, soll - ist)
}

private fun parseRentNumber(value: String): Double? =
    value.trim().replace(".", "").replace(',', '.').toDoubleOrNull()

private fun receiptYear(receipt: Receipt): Int? = receipt.datum.take(4).toIntOrNull()

private fun monthsExpected(unit: WohneinheitStatus, year: Int): Int {
    if (unit.status != "Vermietet") return 0
    val parts = unit.mietvertragsstart.split('-')
    val startYear = parts.getOrNull(0)?.toIntOrNull()
    val startMonth = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 12)
    return when {
        startYear == null || startMonth == null -> 12
        startYear < year -> 12
        startYear > year -> 0
        else -> 13 - startMonth
    }
}

private fun isRentalIncome(receipt: Receipt): Boolean {
    val sub = receipt.unterkategorie.trim()
    if (receipt.hauptkategorie == "Miete, Nebenkosten & Kaution") {
        return sub !in setOf("Kaution", "Einzahlung Kaution", "Rückzahlung Kaution")
    }
    return receipt.hauptkategorie == "Sonstige Einnahmen" &&
        (sub.contains("Betriebskosten", true) || sub.contains("Miete", true))
}

@Composable
fun RentIncomeOverviewScreen(viewModel: ReceiptViewModel) {
    val context = LocalContext.current
    val receipts by viewModel.receipts.collectAsState()
    val units by viewModel.wohneinheitenStatus.collectAsState()
    val prefs = remember(context) { context.getSharedPreferences("rent_plan_prefs", Context.MODE_PRIVATE) }
    var prefsVersion by remember { mutableIntStateOf(0) }
    val availableYears = remember(receipts) {
        receipts.mapNotNull(::receiptYear).distinct().sortedDescending().ifEmpty { listOf(2026) }
    }
    var selectedYear by remember(availableYears) { mutableIntStateOf(availableYears.first()) }
    var editingUnit by remember { mutableStateOf<WohneinheitStatus?>(null) }

    val plans = remember(units, prefsVersion) {
        units.map { unit ->
            RentPlan(
                unit = unit,
                nebenkosten = prefs.getFloat("nk_${unit.name}", 0f).toDouble(),
                sonstige = prefs.getFloat("other_${unit.name}", 0f).toDouble()
            )
        }
    }

    val yearRows = remember(receipts, plans, selectedYear) {
        plans.map { plan ->
            val unitReceipts = receipts.filter {
                it.wohneinheit == plan.unit.name && receiptYear(it) == selectedYear && isRentalIncome(it)
            }
            val ist = unitReceipts.sumOf { it.bruttobetrag }
            val kalt = unitReceipts.filter { it.unterkategorie == "Kaltmiete" }.sumOf { it.bruttobetrag }
            val warm = unitReceipts.filter { it.unterkategorie in setOf("Warmmiete", "Pauschalmiete") }.sumOf { it.bruttobetrag }
            val bk = unitReceipts.filter { it.unterkategorie.contains("Betriebskosten", true) }.sumOf { it.bruttobetrag }
            val sonstige = ist - kalt - warm - bk
            val soll = plan.monatSoll * monthsExpected(plan.unit, selectedYear)
            UnitRentYear(plan, ist, soll, kalt, warm, sonstige, bk)
        }
    }

    val totalIst = yearRows.sumOf { it.ist }
    val totalSoll = yearRows.sumOf { it.soll }
    val totalRueckstand = yearRows.sumOf { it.rueckstand }
    val unattributed = receipts.filter {
        receiptYear(it) == selectedYear && isRentalIncome(it) &&
            (it.wohneinheit.isBlank() || units.none { unit -> unit.name == it.wohneinheit })
    }.sumOf { it.bruttobetrag }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Mieteinnahmen & Nebenkosten", fontSize = 20.sp, fontWeight = FontWeight.Black, color = DarkNavy)
                Text("Ist-Einnahmen aus Belegen · Sollwerte aus den Mietdaten", fontSize = 11.sp, color = SlateGray)
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { selectedYear -= 1 }) { Text("‹") }
                Text("Steuerjahr $selectedYear", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                OutlinedButton(onClick = { selectedYear += 1 }) { Text("›") }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Jahresübersicht", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    SummaryRow("Ist-Einnahmen laut Belegen", totalIst, EmeraldGreen)
                    SummaryRow("Soll-Hochrechnung aktueller Verträge", totalSoll, DarkNavy)
                    SummaryRow("Offene Differenz / Rückstand", totalRueckstand, if (totalRueckstand > 0.0) CrimsonRed else EmeraldGreen)
                    if (unattributed > 0.0) {
                        HorizontalDivider(color = BorderColor)
                        Text("⚠ ${NumberFormatter.format(unattributed)} Miet-/BK-Einnahmen sind keiner Wohneinheit zugeordnet.", fontSize = 10.sp, color = WarmOrange)
                    }
                    Text("Steuerlich maßgeblich sind die tatsächlich zugeflossenen Einnahmen. Der Sollwert dient nur der Mietkontrolle.", fontSize = 9.sp, color = SlateGray, lineHeight = 12.sp)
                }
            }
        }

        item { Text("Wohneinheiten", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkNavy) }

        items(yearRows, key = { it.plan.unit.name }) { row ->
            val unit = row.plan.unit
            Card(
                modifier = Modifier.fillMaxWidth().clickable { editingUnit = unit },
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(unit.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                            Text(if (unit.status == "Vermietet") unit.mieter.ifBlank { "Mieter nicht hinterlegt" } else unit.status, fontSize = 10.sp, color = SlateGray)
                        }
                        Icon(Icons.Default.Edit, contentDescription = "Mietplan bearbeiten", tint = AccentBlue, modifier = Modifier.size(18.dp))
                    }
                    HorizontalDivider(color = BorderColor.copy(alpha = 0.7f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Monatliches Soll", fontSize = 10.sp, color = SlateGray)
                        Text(NumberFormatter.format(row.plan.monatSoll), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    }
                    Text("Kalt ${NumberFormatter.format(unit.kaltmiete)} · NK ${NumberFormatter.format(row.plan.nebenkosten)} · Sonstiges ${NumberFormatter.format(row.plan.sonstige)}", fontSize = 9.sp, color = SlateGray)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Ist $selectedYear", fontSize = 10.sp, color = SlateGray)
                        Text(NumberFormatter.format(row.ist), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Soll-Hochrechnung", fontSize = 10.sp, color = SlateGray)
                        Text(NumberFormatter.format(row.soll), fontSize = 11.sp, color = DarkNavy)
                    }
                    if (row.rueckstand > 0.01) {
                        Text("Offene Differenz: ${NumberFormatter.format(row.rueckstand)}", fontSize = 10.sp, color = CrimsonRed, fontWeight = FontWeight.Bold)
                    } else if (row.soll > 0.0) {
                        Text("Soll erreicht", fontSize = 10.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                    }
                    if (row.ist > 0.0) {
                        Text("Ist-Aufteilung: Kalt ${NumberFormatter.format(row.kaltIst)} · Warm/Pauschal ${NumberFormatter.format(row.warmPauschalIst)} · BK-Nachzahlung ${NumberFormatter.format(row.betriebskostenNachzahlung)} · Sonstiges ${NumberFormatter.format(row.sonstigeIst)}", fontSize = 9.sp, color = SlateGray, lineHeight = 12.sp)
                    }
                }
            }
        }
    }

    editingUnit?.let { unit ->
        val currentPlan = plans.firstOrNull { it.unit.name == unit.name } ?: RentPlan(unit, 0.0, 0.0)
        RentPlanEditDialog(
            unit = unit,
            nebenkostenInitial = currentPlan.nebenkosten,
            sonstigeInitial = currentPlan.sonstige,
            onDismiss = { editingUnit = null },
            onSave = { kalt, nk, other, start ->
                prefs.edit()
                    .putFloat("nk_${unit.name}", nk.toFloat())
                    .putFloat("other_${unit.name}", other.toFloat())
                    .apply()
                viewModel.updateWohneinheit(unit.copy(kaltmiete = kalt, mietvertragsstart = start))
                prefsVersion++
                editingUnit = null
            }
        )
    }
}

@Composable
private fun SummaryRow(label: String, amount: Double, color: androidx.compose.ui.graphics.Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 11.sp, color = SlateGray)
        Text(NumberFormatter.format(amount), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun RentPlanEditDialog(
    unit: WohneinheitStatus,
    nebenkostenInitial: Double,
    sonstigeInitial: Double,
    onDismiss: () -> Unit,
    onSave: (Double, Double, Double, String) -> Unit
) {
    var kalt by remember(unit.name) { mutableStateOf(unit.kaltmiete.toString()) }
    var nk by remember(unit.name) { mutableStateOf(nebenkostenInitial.toString()) }
    var other by remember(unit.name) { mutableStateOf(sonstigeInitial.toString()) }
    var start by remember(unit.name) { mutableStateOf(unit.mietvertragsstart) }
    var error by remember(unit.name) { mutableStateOf<String?>(null) }
    val keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mietplan: ${unit.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Die Sollwerte dienen der Mietkontrolle und verändern keine vorhandenen Belege.", fontSize = 10.sp, color = SlateGray)
                OutlinedTextField(kalt, { kalt = it }, label = { Text("Kaltmiete / Monat €") }, keyboardOptions = keyboard, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(nk, { nk = it }, label = { Text("Nebenkostenvorauszahlung / Monat €") }, keyboardOptions = keyboard, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(other, { other = it }, label = { Text("Sonstige Mietbestandteile / Monat €") }, keyboardOptions = keyboard, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(start, { start = it }, label = { Text("Mietvertragsstart YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Monatliches Soll: ${NumberFormatter.format((parseRentNumber(kalt) ?: 0.0) + (parseRentNumber(nk) ?: 0.0) + (parseRentNumber(other) ?: 0.0))}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                error?.let { Text(it, fontSize = 10.sp, color = CrimsonRed, fontWeight = FontWeight.Bold) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val cold = parseRentNumber(kalt)
                val utilities = parseRentNumber(nk)
                val extras = parseRentNumber(other)
                if (cold == null || cold < 0 || utilities == null || utilities < 0 || extras == null || extras < 0) {
                    error = "Bitte gültige positive Beträge eingeben."
                } else {
                    onSave(cold, utilities, extras, start.trim())
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
