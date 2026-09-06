package com.example.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Receipt
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private data class MonthlyRentCheckRow(
    val unit: WohneinheitStatus,
    val tenants: String,
    val expected: Double,
    val actual: Double,
    val changeInMonth: Boolean
) {
    val missing: Double get() = (expected - actual).coerceAtLeast(0.0)
    val overpayment: Double get() = (actual - expected).coerceAtLeast(0.0)
}

private fun isMonthlyRentalIncome(receipt: Receipt): Boolean {
    val sub = receipt.unterkategorie.trim()
    if (receipt.hauptkategorie == "Miete, Nebenkosten & Kaution") {
        return sub !in setOf("Kaution", "Einzahlung Kaution", "Rückzahlung Kaution")
    }
    return receipt.hauptkategorie == "Sonstige Einnahmen" &&
        (sub.contains("Betriebskosten", true) || sub.contains("Miete", true))
}

private fun receiptMonth(receipt: Receipt): YearMonth? =
    runCatching { YearMonth.from(LocalDate.parse(receipt.datum)) }.getOrNull()

private fun periodExpectedInMonth(period: TenantPeriod, month: YearMonth): Double {
    val monthStart = month.atDay(1)
    val monthEnd = month.atEndOfMonth()
    val start = runCatching { LocalDate.parse(period.startDate) }.getOrNull() ?: monthStart
    val end = runCatching { LocalDate.parse(period.endDate) }.getOrNull() ?: monthEnd
    val from = if (start.isAfter(monthStart)) start else monthStart
    val to = if (end.isBefore(monthEnd)) end else monthEnd
    if (to.isBefore(from)) return 0.0
    val occupiedDays = ChronoUnit.DAYS.between(from, to).toDouble() + 1.0
    return period.monatSoll * (occupiedDays / month.lengthOfMonth().toDouble())
}

private fun fallbackPeriod(unit: WohneinheitStatus, nk: Double, other: Double): TenantPeriod? {
    if (unit.status != "Vermietet") return null
    if (unit.mieter.isBlank() && unit.kaltmiete <= 0.0 && nk <= 0.0 && other <= 0.0) return null
    return TenantPeriod(
        id = -unit.name.hashCode().toLong(),
        unitName = unit.name,
        tenantName = unit.mieter,
        startDate = unit.mietvertragsstart,
        endDate = "",
        kaltmiete = unit.kaltmiete,
        nebenkosten = nk,
        sonstige = other
    )
}

@Composable
fun RentIncomeWithTenantHistoryScreen(viewModel: ReceiptViewModel, propertyScoped: Boolean = false) {
    val context = LocalContext.current
    val units by viewModel.wohneinheitenStatus.collectAsState()
    val receiptFlow = if (propertyScoped) viewModel.propertyReceipts else viewModel.receipts
    val receipts by receiptFlow.collectAsState()
    val rentPrefs = remember(context) { context.getSharedPreferences("rent_plan_prefs", Context.MODE_PRIVATE) }
    var showUnitPicker by remember { mutableStateOf(false) }
    var showMonthlyCheck by remember { mutableStateOf(false) }
    var selectedUnit by remember { mutableStateOf<WohneinheitStatus?>(null) }
    var historyVersion by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        RentIncomeOverviewScreen(viewModel, propertyScoped)

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ExtendedFloatingActionButton(
                onClick = { showMonthlyCheck = true },
                icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                text = { Text("Monatscheck") },
                containerColor = EmeraldGreen,
                contentColor = androidx.compose.ui.graphics.Color.White
            )
            ExtendedFloatingActionButton(
                onClick = { showUnitPicker = true },
                icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                text = { Text("Mieterwechsel") },
                containerColor = AccentBlue,
                contentColor = androidx.compose.ui.graphics.Color.White
            )
        }
    }

    if (showMonthlyCheck) {
        MonthlyRentCheckDialog(
            units = units,
            receipts = receipts,
            historyVersion = historyVersion,
            onDismiss = { showMonthlyCheck = false }
        )
    }

    if (showUnitPicker) {
        AlertDialog(
            onDismissRequest = { showUnitPicker = false },
            title = { Text("Wohneinheit auswählen", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn {
                    items(units, key = { it.name }) { unit ->
                        Card(
                            onClick = {
                                selectedUnit = unit
                                showUnitPicker = false
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = SoftBackground),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(unit.label, fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 13.sp)
                                Text(
                                    if (unit.status == "Vermietet") unit.mieter.ifBlank { "Mieter nicht hinterlegt" } else unit.status,
                                    color = SlateGray,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showUnitPicker = false }) { Text("Abbrechen") } }
        )
    }

    selectedUnit?.let { unit ->
        val nk = rentPrefs.getFloat("nk_${unit.name}", 0f).toDouble()
        val other = rentPrefs.getFloat("other_${unit.name}", 0f).toDouble()
        TenantHistoryDialog(
            unit = unit,
            nebenkostenCurrent = nk,
            sonstigeCurrent = other,
            onDismiss = { selectedUnit = null },
            onCurrentTenantChanged = { newPeriod ->
                rentPrefs.edit()
                    .putFloat("nk_${unit.name}", newPeriod.nebenkosten.toFloat())
                    .putFloat("other_${unit.name}", newPeriod.sonstige.toFloat())
                    .apply()
                viewModel.updateWohneinheit(
                    unit.copy(
                        status = "Vermietet",
                        mieter = newPeriod.tenantName,
                        kaltmiete = newPeriod.kaltmiete,
                        mietvertragsstart = newPeriod.startDate
                    )
                )
            },
            onHistoryChanged = { historyVersion++ }
        )
    }
}

@Composable
private fun MonthlyRentCheckDialog(
    units: List<WohneinheitStatus>,
    receipts: List<Receipt>,
    historyVersion: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val rentPrefs = remember(context) { context.getSharedPreferences("rent_plan_prefs", Context.MODE_PRIVATE) }
    var month by remember { mutableStateOf(YearMonth.now()) }

    val rows = remember(units, receipts, month, historyVersion) {
        units.map { unit ->
            val nk = rentPrefs.getFloat("nk_${unit.name}", 0f).toDouble()
            val other = rentPrefs.getFloat("other_${unit.name}", 0f).toDouble()
            val stored = TenantHistoryStore.load(context, unit.name)
            val periods = if (stored.isNotEmpty()) stored else listOfNotNull(fallbackPeriod(unit, nk, other))
            val relevant = periods.filter { periodExpectedInMonth(it, month) > 0.0 }
            val expected = relevant.sumOf { periodExpectedInMonth(it, month) }
            val actual = receipts.filter {
                it.wohneinheit == unit.name && receiptMonth(it) == month && isMonthlyRentalIncome(it)
            }.sumOf { it.bruttobetrag }
            MonthlyRentCheckRow(
                unit = unit,
                tenants = relevant.map { it.tenantName.ifBlank { "Mieter nicht hinterlegt" } }.distinct().joinToString(" → ").ifBlank {
                    if (unit.status == "Vermietet") unit.mieter.ifBlank { "Mieter nicht hinterlegt" } else unit.status
                },
                expected = expected,
                actual = actual,
                changeInMonth = relevant.size > 1
            )
        }
    }

    val totalExpected = rows.sumOf { it.expected }
    val totalActual = rows.sumOf { it.actual }
    val totalMissing = rows.sumOf { it.missing }
    val missingCount = rows.count { it.expected > 0.01 && it.missing > 0.01 }
    val unassigned = receipts.filter {
        receiptMonth(it) == month && isMonthlyRentalIncome(it) &&
            (it.wohneinheit.isBlank() || units.none { unit -> unit.name == it.wohneinheit })
    }.sumOf { it.bruttobetrag }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Miet-Monatscheck", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
                        Text("${month.monthValue.toString().padStart(2, '0')}/${month.year}", fontWeight = FontWeight.Bold, color = DarkNavy)
                        TextButton(onClick = { month = month.plusMonths(1) }) { Text("›") }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SoftBackground),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Monatsübersicht", fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 13.sp)
                            MonthlySummaryRow("Soll", totalExpected)
                            MonthlySummaryRow("Ist laut Belegen", totalActual)
                            MonthlySummaryRow("Offener Rückstand", totalMissing)
                            if (missingCount > 0) {
                                Text("⚠ $missingCount Wohneinheit${if (missingCount == 1) "" else "en"} mit fehlender/zu geringer Zahlung", fontSize = 10.sp, color = CrimsonRed, fontWeight = FontWeight.Bold)
                            } else if (totalExpected > 0.01) {
                                Text("✓ Alle erwarteten Mietzahlungen vollständig erfasst", fontSize = 10.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                            }
                            if (unassigned > 0.01) {
                                Text("⚠ ${NumberFormatter.format(unassigned)} Mietzahlung(en) ohne Wohneinheiten-Zuordnung", fontSize = 9.sp, color = WarmOrange)
                            }
                        }
                    }
                }
                items(rows, key = { it.unit.name }) { row ->
                    val status = when {
                        row.expected <= 0.01 -> "KEIN SOLL"
                        row.actual + 0.01 >= row.expected -> "BEZAHLT"
                        row.actual <= 0.01 -> "FEHLT"
                        else -> "TEILZAHLUNG"
                    }
                    val statusColor = when (status) {
                        "BEZAHLT" -> EmeraldGreen
                        "FEHLT", "TEILZAHLUNG" -> CrimsonRed
                        else -> SlateGray
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(row.unit.label, fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 12.sp)
                                    Text(row.tenants, color = SlateGray, fontSize = 9.sp)
                                }
                                Text(status, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = statusColor)
                            }
                            if (row.changeInMonth) {
                                Text("Mieterwechsel in diesem Monat – Soll zeitanteilig berechnet", fontSize = 9.sp, color = AccentBlue)
                            }
                            HorizontalDivider(color = BorderColor)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Soll", fontSize = 10.sp, color = SlateGray)
                                Text(NumberFormatter.format(row.expected), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Ist", fontSize = 10.sp, color = SlateGray)
                                Text(NumberFormatter.format(row.actual), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                            }
                            if (row.missing > 0.01) {
                                Text("Rückstand: ${NumberFormatter.format(row.missing)}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CrimsonRed)
                            } else if (row.overpayment > 0.01 && row.expected > 0.01) {
                                Text("Mehrzahlung: ${NumberFormatter.format(row.overpayment)}", fontSize = 9.sp, color = AccentBlue)
                            }
                        }
                    }
                }
                item {
                    Text(
                        "Ist = tatsächlich erfasste Miet-/Nebenkosteneinnahmen der Wohneinheit. Kautionen werden nicht als Mietzahlung gewertet.",
                        fontSize = 9.sp,
                        color = SlateGray
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}

@Composable
private fun MonthlySummaryRow(label: String, amount: Double) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 10.sp, color = SlateGray)
        Text(NumberFormatter.format(amount), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
    }
}
