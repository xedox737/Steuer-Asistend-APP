package com.example.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Receipt
import java.time.LocalDate
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

internal fun isRentalIncomeReceipt(receipt: Receipt): Boolean {
    val sub = receipt.unterkategorie.trim()
    if (receipt.hauptkategorie == "Miete, Nebenkosten & Kaution") {
        return sub !in setOf("Kaution", "Einzahlung Kaution", "Rückzahlung Kaution")
    }
    return receipt.hauptkategorie == "Sonstige Einnahmen" &&
        (sub.contains("Betriebskosten", true) || sub.contains("Miete", true))
}

@Composable
fun RentIncomeOverviewScreen(
    viewModel: ReceiptViewModel,
    propertyScoped: Boolean = false,
    historyVersion: Int = 0,
    onMonthlyCheck: () -> Unit = {},
    onTenantHistory: (String, WohneinheitStatus) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val receipts by viewModel.receipts.collectAsStateWithLifecycle()
    val properties by viewModel.properties.collectAsStateWithLifecycle()
    val units by viewModel.wohneinheitenStatus.collectAsStateWithLifecycle()
    val metadata by viewModel.propertyMetadata.collectAsStateWithLifecycle()
    val bankAssignments by viewModel.bankRentAssignments.collectAsStateWithLifecycle()
    val bankLinks by viewModel.bankReceiptLinks.collectAsStateWithLifecycle()
    val bankTransactions by viewModel.bankTransactions.collectAsStateWithLifecycle()
    var prefsVersion by remember { mutableIntStateOf(0) }
    val groups = remember(properties, units, metadata, propertyScoped, historyVersion, prefsVersion) {
        val visible = if (propertyScoped) listOfNotNull(metadata) else properties
        visible.map { property -> RentPropertyUnits(property,
            if (property.propertyId == metadata?.propertyId) units else viewModel.getWohneinheitenForProperty(property)) }
    }
    val scopedReceipts = remember(receipts, groups, propertyScoped) {
        RentOverviewPresentation.scopedReceipts(groups, receipts, propertyScoped)
    }
    val years = remember(groups, scopedReceipts, bankAssignments, historyVersion, prefsVersion) {
        RentOverviewPresentation.years(context, groups, scopedReceipts, bankAssignments = bankAssignments)
    }
    var selectedYear by rememberSaveable(if (propertyScoped) metadata?.propertyId else "all") { mutableIntStateOf(LocalDate.now().year) }
    var yearsOpen by remember { mutableStateOf(false) }
    val overview = remember(
        groups, scopedReceipts, selectedYear, prefsVersion, historyVersion,
        bankAssignments, bankLinks, bankTransactions
    ) {
        RentOverviewPresentation.year(
            context, groups, scopedReceipts, selectedYear,
            bankAssignments, bankLinks, bankTransactions
        )
    }
    var editing by remember { mutableStateOf<RentOverviewUnit?>(null) }
    var originalProperty by remember { mutableStateOf<String?>(null) }
    var pendingPlan by remember { mutableStateOf<ValidRentPlan?>(null) }
    fun dismissEditor() {
        editing = null
        pendingPlan = null
        originalProperty?.let(viewModel::selectProperty)
        originalProperty = null
    }
    // Existing updateWohneinheit writes the selected property. Wait for that existing
    // selection to become active before saving, then restore the global overview context.
    LaunchedEffect(pendingPlan, metadata?.propertyId) {
        val row = editing
        val plan = pendingPlan
        if (row != null && plan != null && metadata?.propertyId == row.property.propertyId) {
            val propertyId = row.property.propertyId
            val unitId = PropertyUnitScopedData.stableUnitId(propertyId, row.unit)
            val stored = TenantHistoryStore.ensureCurrentPeriod(
                context, row.unit, row.nebenkosten, row.sonstige, propertyId
            )
            val active = stored.lastOrNull { it.active && it.tenantName == row.unit.mieter }
                ?: stored.lastOrNull { it.active }
            val updated = if (active == null) {
                stored
            } else when (plan.mode) {
                RentPlanEditMode.CORRECT_EXISTING -> stored.map { period ->
                    if (period.id == active.id) period.copy(
                        kaltmiete = plan.kalt,
                        nebenkosten = plan.nk,
                        sonstige = plan.other,
                        startDate = plan.date
                    ) else period
                }
                RentPlanEditMode.CHANGE_FROM_DATE -> stored.map { period ->
                    if (period.id != active.id) period else period.copy(
                        rentChanges = (period.rentChanges.filterNot { it.effectiveDate == plan.date } +
                            RentAmountChange(plan.date, plan.kalt, plan.nk, plan.other))
                            .sortedBy { it.effectiveDate }
                    )
                }
            }
            if (active != null) {
                TenantHistoryStore.save(context, propertyId, unitId, row.unit.name, updated)
            }

            val currentPeriod = updated.lastOrNull { it.active && it.tenantName == row.unit.mieter }
                ?: updated.lastOrNull { it.active }
            val currentAmounts = currentPeriod?.amountsAt(LocalDate.now())
                ?: RentAmounts(plan.kalt, plan.nk, plan.other)
            PropertyUnitScopedData.setRentValues(
                context, propertyId, row.unit, currentAmounts.nebenkosten, currentAmounts.sonstige
            )
            viewModel.updateWohneinheit(
                row.unit.copy(
                    kaltmiete = currentAmounts.kaltmiete,
                    mietvertragsstart = if (plan.mode == RentPlanEditMode.CORRECT_EXISTING) plan.date else row.unit.mietvertragsstart
                )
            )
            prefsVersion++
            dismissEditor()
        }
    }

    LazyColumn(Modifier.fillMaxSize().testTag("rent_overview"), contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Mieteingänge", fontSize = 23.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                Text("Mieten, Nebenkostenvorauszahlungen und offene Beträge", fontSize = 11.sp, lineHeight = 15.sp, color = SlateGray)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedYear-- }, modifier = Modifier.testTag("rent_year_previous")) {
                    Icon(Icons.Default.ChevronLeft, "Vorheriges Mietjahr", tint = AccentBlue)
                }
                Box {
                    OutlinedButton(onClick = { yearsOpen = true }, shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderColor), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                        modifier = Modifier.testTag("rent_year"), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)) {
                        Text(selectedYear.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                        Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp), tint = SlateGray)
                    }
                    DropdownMenu(yearsOpen, { yearsOpen = false }) {
                        years.forEach { year -> DropdownMenuItem(text = { Text(year.toString()) },
                            onClick = { selectedYear = year; yearsOpen = false }) }
                    }
                }
                IconButton(onClick = { selectedYear++ }, modifier = Modifier.testTag("rent_year_next")) {
                    Icon(Icons.Default.ChevronRight, "Nächstes Mietjahr", tint = AccentBlue)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("rent_metrics")) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RentMetric("Ist-Einnahmen", overview.actual, Icons.AutoMirrored.Filled.TrendingUp, EmeraldGreen, "Zugeordnet · $selectedYear", Modifier.weight(1f), "actual")
                    RentMetric("Soll-Miete", overview.expected, Icons.Default.CalendarMonth, AccentBlue, "Jahres-Soll · $selectedYear", Modifier.weight(1f), "expected")
                }
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RentMetric("Nebenkosten", overview.utilities, Icons.Default.Payments, WarmOrange, "Eindeutige Einnahmen", Modifier.weight(1f), "utilities")
                    RentMetric("Offen", overview.missing, Icons.Default.Warning,
                        if (overview.missing > .01) CrimsonRed else EmeraldGreen, "Zum Jahres-Soll", Modifier.weight(1f), "missing")
                }
            }
        }
        item { RentYearChart(overview, onMonthlyCheck) }
        if (overview.unassigned.isNotEmpty()) item {
            RentCard(Modifier.testTag("rent_unassigned")) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = WarmOrange, modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        val count = overview.unassigned.size
                        Text("$count Mietzahlung${if (count == 1) "" else "en"} nicht zugeordnet", fontSize = 13.sp, lineHeight = 17.sp,
                            fontWeight = FontWeight.Bold, color = DarkNavy)
                        Text("${NumberFormatter.format(overview.unassigned.sumOf { it.bruttobetrag })} müssen geprüft werden",
                            fontSize = 11.sp, lineHeight = 15.sp, color = SlateGray)
                    }
                }
                TextButton(onClick = {
                    viewModel.setCategoryFilter(null)
                    viewModel.setSearchQuery("")
                    viewModel.setDateRangeFilter("$selectedYear-01-01", "$selectedYear-12-31")
                    viewModel.setScreen(AppScreen.RECEIPTS_LIST)
                }, modifier = Modifier.testTag("rent_review_payments"), contentPadding = PaddingValues(0.dp)) {
                    Text("Zahlungen prüfen", color = AccentBlue, fontSize = 12.sp)
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp))
                }
            }
        }
        item { Text("Wohneinheiten", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkNavy) }
        if (overview.rows.isEmpty() || (overview.actual == 0.0 && overview.expected == 0.0 && overview.unassigned.isEmpty())) item {
            RentCard(Modifier.testTag("rent_empty")) {
                Icon(Icons.Default.HomeWork, null, tint = AccentBlue, modifier = Modifier.size(28.dp))
                Text("Noch keine Mieteingänge vorhanden", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                Text("Hinterlege Mietdaten bei deinen Wohneinheiten und ordne eingehende Zahlungen zu.", fontSize = 11.sp, lineHeight = 15.sp, color = SlateGray)
            }
        }
        items(overview.rows, key = { it.key }) { row ->
            RentUnitCard(row, selectedYear, !propertyScoped, onEdit = {
                originalProperty = viewModel.selectedPropertyId.value
                editing = row
                viewModel.selectProperty(row.property.propertyId)
            }, onHistory = { onTenantHistory(row.property.propertyId, row.unit) })
        }
    }
    editing?.let { row ->
        RentPlanEditDialog(row.unit, row.nebenkosten, row.sonstige, onDismiss = { dismissEditor() },
            onSave = { plan -> pendingPlan = plan },
            previousTenancyEnd = TenantHistoryStore.load(context, row.property.propertyId,
                PropertyUnitScopedData.stableUnitId(row.property.propertyId, row.unit), row.unit.name)
                .filterNot { it.active }.mapNotNull { RentOverviewPresentation.date(it.endDate) }.maxOrNull(),
            incomeBreakdown = scopedReceipts.filter { it.propertyId == row.property.propertyId && it.wohneinheit == row.unit.name &&
                RentOverviewPresentation.date(it.datum)?.year == selectedYear && isRentalIncomeReceipt(it) }
                .groupBy { it.unterkategorie.ifBlank { "Sonstige Mieteinnahmen" } }.mapValues { (_, values) -> values.sumOf { it.bruttobetrag } })
    }
}

@Composable
private fun RentCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderColor), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp), content = content)
    }
}

@Composable
private fun RentMetric(title: String, value: Double, icon: ImageVector, color: Color, subtitle: String, modifier: Modifier, tag: String) {
    Card(modifier.fillMaxHeight().heightIn(min = 100.dp).testTag("rent_metric_$tag"), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(28.dp).background(color.copy(alpha = .1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                }
                Text(title, fontSize = 12.sp, color = DarkNavy, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(NumberFormatter.format(value), fontSize = if (NumberFormatter.format(value).length > 14) 17.sp else 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, color = color)
            Text(subtitle, fontSize = 10.sp, lineHeight = 13.sp, color = SlateGray)
        }
    }
}

@Composable
private fun RentYearChart(year: RentOverviewYear, onMonthlyCheck: () -> Unit) {
    val months = year.months
    val upper = months.maxOf { maxOf(it.first, it.second, 0.0) }.coerceAtLeast(1.0)
    val lower = months.minOf { minOf(it.first, it.second, 0.0) }
    val range = upper - lower
    RentCard(Modifier.testTag("rent_chart")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Mietverlauf ${year.year}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkNavy, modifier = Modifier.weight(1f))
            listOf("Soll" to AccentBlue, "Ist" to EmeraldGreen).forEach { (label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(5.dp).background(color, CircleShape))
                    Text(label, fontSize = 10.sp, color = SlateGray)
                    Spacer(Modifier.width(7.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.width(47.dp).height(66.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                listOf(upper, (upper + lower) / 2, lower).forEach { value ->
                    Text(if (abs(value) >= 1_000_000) NumberFormat.getNumberInstance(Locale.GERMANY).apply { maximumFractionDigits = 1 }.format(value / 1_000_000) + " Mio. €"
                        else NumberFormat.getIntegerInstance(Locale.GERMANY).format(value) + " €", fontSize = 8.sp, lineHeight = 11.sp,
                        color = SlateGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.weight(1f)) {
                Column(Modifier.fillMaxWidth().height(66.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    repeat(3) { HorizontalDivider(color = BorderColor.copy(alpha = .5f)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    months.forEachIndexed { index, (expected, actual) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(Modifier.height(66.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                listOf(Triple(expected, AccentBlue, "expected"), Triple(actual, EmeraldGreen, "actual")).forEach { (value, color, kind) ->
                                    Box(Modifier.weight(1f).height(66.dp)) {
                                        Box(Modifier.fillMaxWidth().offset(y = ((upper - maxOf(value, 0.0)) / range * 66).toFloat().dp)
                                            .height((abs(value) / range * 66).toFloat().dp).background(color, RoundedCornerShape(2.dp))
                                            .testTag("rent_bar_${kind}_${index + 1}"))
                                    }
                                }
                            }
                            Text(listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")[index],
                                fontSize = 9.sp, lineHeight = 12.sp, color = SlateGray, maxLines = 1)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Jahres-Soll inkl. künftiger Monate", fontSize = 9.sp, color = SlateGray, modifier = Modifier.weight(1f))
            TextButton(onClick = onMonthlyCheck, modifier = Modifier.testTag("rent_monthly_check"), contentPadding = PaddingValues(0.dp)) {
                Text("Monatscheck", fontSize = 11.sp, color = AccentBlue)
            }
        }
    }
}

@Composable
private fun RentUnitCard(row: RentOverviewUnit, year: Int, showProperty: Boolean, onEdit: () -> Unit, onHistory: () -> Unit) {
    val status = when { row.expected <= .01 -> "Kein Soll"; row.missing > .01 -> "${NumberFormatter.format(row.missing)} offen"; else -> "Bezahlt" }
    val statusColor = when { row.expected <= .01 -> SlateGray; row.missing > .01 -> CrimsonRed; else -> EmeraldGreen }
    RentCard(Modifier.clickable(onClick = onEdit).testTag("rent_unit_${row.key}")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(32.dp).background(AccentBlue.copy(alpha = .1f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.HomeWork, null, tint = AccentBlue, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.unit.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkNavy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.tenants, fontSize = 10.sp, color = SlateGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(color = if (row.unit.status == "Vermietet") EmeraldGreen.copy(alpha = .1f) else SoftBackground, shape = RoundedCornerShape(8.dp)) {
                Text(row.unit.status, fontSize = 9.sp, color = if (row.unit.status == "Vermietet") EmeraldGreen else SlateGray, modifier = Modifier.padding(5.dp))
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp).testTag("rent_edit_${row.key}")) {
                Icon(Icons.Default.Edit, "Mietplan bearbeiten", tint = AccentBlue, modifier = Modifier.size(18.dp))
            }
        }
        if (showProperty) Text(row.property.name.ifBlank { row.property.adresse.ifBlank { "Immobilie" } }, fontSize = 9.sp,
            color = SlateGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Soll $year", fontSize = 10.sp, color = SlateGray)
                Text(NumberFormatter.format(row.expected), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Ist $year", fontSize = 10.sp, color = SlateGray)
                Text(NumberFormatter.format(row.actual), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
            }
        }
        Text("Monatlich ${NumberFormatter.format(row.monthly)} · Kalt ${NumberFormatter.format(row.unit.kaltmiete)} · NK ${NumberFormatter.format(row.nebenkosten)}",
            fontSize = 9.sp, lineHeight = 12.sp, color = SlateGray)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(if (row.expected <= .01) Icons.Default.Remove else if (row.missing > .01) Icons.Default.Warning else Icons.Default.CheckCircle,
                    null, tint = statusColor, modifier = Modifier.size(15.dp))
                Text(status, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor, modifier = Modifier.testTag("rent_status_${row.key}"))
            }
            TextButton(onClick = onHistory, modifier = Modifier.testTag("rent_history_${row.key}"), contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.SwapHoriz, null, tint = AccentBlue, modifier = Modifier.size(15.dp))
                Text("Mieterwechsel", fontSize = 10.sp, color = AccentBlue)
            }
        }
    }
}

@Composable
internal fun RentPlanEditDialog(
    unit: WohneinheitStatus,
    nebenkostenInitial: Double,
    sonstigeInitial: Double,
    onDismiss: () -> Unit,
    onSave: (ValidRentPlan) -> Unit,
    incomeBreakdown: Map<String, Double> = emptyMap(),
    previousTenancyEnd: LocalDate? = null
) {
    var mode by remember(unit.unitId, unit.name) { mutableStateOf(RentPlanEditMode.CORRECT_EXISTING) }
    var kalt by remember(unit.unitId, unit.name) { mutableStateOf(GermanNumberInput.formatForInput(unit.kaltmiete)) }
    var nk by remember(unit.unitId, unit.name) { mutableStateOf(GermanNumberInput.formatForInput(nebenkostenInitial)) }
    var other by remember(unit.unitId, unit.name) { mutableStateOf(GermanNumberInput.formatForInput(sonstigeInitial)) }
    var correctionStart by remember(unit.unitId, unit.name) { mutableStateOf(unit.mietvertragsstart) }
    var changeDate by remember(unit.unitId, unit.name) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var largeChangeConfirmation by remember { mutableStateOf<ValidRentPlan?>(null) }
    val keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val dateValue = if (mode == RentPlanEditMode.CORRECT_EXISTING) correctionStart else changeDate

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        title = { Text("Mietplan: ${unit.label}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DarkNavy) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("Sollwerte für die Mietkontrolle. Vorhandene Belege bleiben unverändert.", fontSize = 10.sp, color = SlateGray)
                Text("Art der Änderung", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = mode == RentPlanEditMode.CORRECT_EXISTING,
                        onClick = { mode = RentPlanEditMode.CORRECT_EXISTING; error = null },
                        label = { Text("Wert korrigieren") },
                        modifier = Modifier.testTag("rent_plan_mode_correction")
                    )
                    FilterChip(
                        selected = mode == RentPlanEditMode.CHANGE_FROM_DATE,
                        onClick = { mode = RentPlanEditMode.CHANGE_FROM_DATE; error = null },
                        label = { Text("Miete ändern ab Datum") },
                        modifier = Modifier.testTag("rent_plan_mode_change")
                    )
                }
                Text(
                    if (mode == RentPlanEditMode.CORRECT_EXISTING)
                        "Korrigiert den bestehenden Mietabschnitt, z. B. nach einer Fehleingabe."
                    else
                        "Legt innerhalb desselben Mietverhältnisses einen neuen Mietwert ab dem gewählten Monat an. Frühere Monate bleiben unverändert.",
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    color = SlateGray
                )
                OutlinedTextField(
                    kalt, { kalt = it; error = null },
                    label = { Text("Kaltmiete / Monat €") },
                    keyboardOptions = keyboard, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rent_plan_cold")
                )
                OutlinedTextField(
                    nk, { nk = it; error = null },
                    label = { Text("Nebenkostenvorauszahlung / Monat €") },
                    keyboardOptions = keyboard, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rent_plan_utilities")
                )
                OutlinedTextField(
                    other, { other = it; error = null },
                    label = { Text("Sonstige Mietbestandteile / Monat €") },
                    keyboardOptions = keyboard, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rent_plan_other")
                )
                OutlinedTextField(
                    value = dateValue,
                    onValueChange = {
                        if (mode == RentPlanEditMode.CORRECT_EXISTING) correctionStart = it else changeDate = it
                        error = null
                    },
                    label = {
                        Text(
                            if (mode == RentPlanEditMode.CORRECT_EXISTING)
                                "Mietbeginn JJJJ-MM-TT"
                            else
                                "Miete gültig ab JJJJ-MM-TT*"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rent_plan_start")
                )
                Text(
                    "Monatliches Soll: ${NumberFormatter.format((RentPlanInput.amount(kalt) ?: 0.0) + (RentPlanInput.amount(nk) ?: 0.0) + (RentPlanInput.amount(other) ?: 0.0))}",
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy
                )
                if (mode == RentPlanEditMode.CHANGE_FROM_DATE) {
                    Text("Mietänderungen sind aktuell nur zum Monatsersten zulässig.", fontSize = 9.sp, color = SlateGray)
                } else {
                    Text("Ohne Mietbeginn gilt der vorhandene Jahres-Sollansatz.", fontSize = 9.sp, color = SlateGray)
                }
                error?.let { Text(it, fontSize = 11.sp, color = CrimsonRed) }
                if (incomeBreakdown.isNotEmpty()) {
                    HorizontalDivider(color = BorderColor)
                    Text("Ist-Aufteilung im ausgewählten Jahr", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    incomeBreakdown.forEach { (category, amount) ->
                        Text("$category: ${NumberFormatter.format(amount)}", fontSize = 10.sp, color = SlateGray)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    error = RentPlanInput.error(kalt, nk, other, dateValue, previousTenancyEnd, mode)
                    if (error != null) return@Button
                    val plan = ValidRentPlan(
                        RentPlanInput.amount(kalt)!!,
                        RentPlanInput.amount(nk)!!,
                        RentPlanInput.amount(other)!!,
                        dateValue.trim(),
                        mode
                    )
                    val suspicious = mode == RentPlanEditMode.CHANGE_FROM_DATE &&
                        (
                            RentPlanInput.needsLargeChangeConfirmation(unit.kaltmiete, plan.kalt) ||
                                RentPlanInput.needsLargeChangeConfirmation(nebenkostenInitial, plan.nk) ||
                                RentPlanInput.needsLargeChangeConfirmation(sonstigeInitial, plan.other)
                            )
                    if (suspicious) largeChangeConfirmation = plan else onSave(plan)
                },
                modifier = Modifier.testTag("rent_plan_save"),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )

    largeChangeConfirmation?.let { plan ->
        AlertDialog(
            onDismissRequest = { largeChangeConfirmation = null },
            title = { Text("Ungewöhnlich große Mietänderung") },
            text = {
                Text(
                    "Der neue Mietbetrag weicht sehr stark vom bisherigen Wert ab. Bitte prüfen Sie die Eingabe, bevor Sie die Änderung speichern."
                )
            },
            confirmButton = {
                Button(onClick = { largeChangeConfirmation = null; onSave(plan) }) {
                    Text("Trotzdem speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { largeChangeConfirmation = null }) { Text("Eingabe prüfen") }
            }
        )
    }
}
