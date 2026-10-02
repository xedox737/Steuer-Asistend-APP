package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PropertyMetadata
import com.example.data.Receipt
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun LedgerOverview(
    receipts: List<Receipt>, properties: List<PropertyMetadata>,
    onReceipt: (Receipt) -> Unit, onDatev: () -> Unit, onPdf: () -> Unit,
    tools: @Composable ColumnScope.() -> Unit
) {
    val years = remember(receipts) { LedgerPresentation.years(receipts) }
    var selectedYear by rememberSaveable { mutableIntStateOf(years.first()) }
    // Receipts arrive asynchronously; start with the newest available year unless the user chose one.
    var yearChosen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(years) { if (!yearChosen || selectedYear !in years) selectedYear = years.first() }
    var kind by rememberSaveable { mutableStateOf(LedgerKind.ALL) }
    var propertyId by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var period by rememberSaveable { mutableStateOf(LedgerPeriod.YEAR) }
    var query by rememberSaveable { mutableStateOf("") }
    var toolsExpanded by rememberSaveable { mutableStateOf(false) }
    val yearReceipts = remember(receipts, selectedYear) { LedgerPresentation.forYear(receipts, selectedYear) }
    val totals = remember(yearReceipts) { LedgerPresentation.totals(yearReceipts) }
    val previous = remember(receipts, selectedYear) {
        LedgerPresentation.totals(LedgerPresentation.forYear(receipts, selectedYear - 1))
    }
    val filtered = remember(receipts, properties, selectedYear, kind, propertyId, category, period, query) {
        LedgerPresentation.filter(receipts, LedgerFilters(selectedYear, kind, propertyId, category, period, query),
            knownPropertyIds = properties.map { it.propertyId }.toSet())
    }
    val propertyOptions = remember(properties) {
        properties.map { it.propertyId to it.name.ifBlank { it.adresse.ifBlank { "Immobilie" } } }
    }
    val categories = remember(receipts) { receipts.map(LedgerPresentation::category).distinct().sorted() }

    LazyColumn(
        Modifier.fillMaxSize().testTag("ledger_overview"),
        contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Einnahmen & Ausgaben", fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Finanzübersicht deiner Immobilien", fontSize = 12.sp, lineHeight = 16.sp, color = SlateGray, modifier = Modifier.weight(1f))
                    LedgerDropdown(selectedYear.toString(), "ledger_year", years.map { it to it.toString() }, Modifier.width(84.dp)) {
                        selectedYear = it; yearChosen = true
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerMetric("Einnahmen", LedgerPresentation.money(totals.income), Icons.Default.NorthEast,
                        EmeraldGreen, LedgerPresentation.comparison(totals.income, previous.income, selectedYear - 1) ?: "Kein Vorjahresvergleich", Modifier.weight(1f))
                    LedgerMetric("Ausgaben", LedgerPresentation.money(totals.expense), Icons.Default.SouthEast,
                        CrimsonRed, LedgerPresentation.comparison(totals.expense, previous.expense, selectedYear - 1) ?: "Kein Vorjahresvergleich", Modifier.weight(1f))
                }
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerMetric("Ergebnis", LedgerPresentation.money(totals.result), Icons.Default.BarChart,
                        if (totals.result >= 0) EmeraldGreen else CrimsonRed, "Einnahmen − Ausgaben", Modifier.weight(1f), AccentBlue)
                    LedgerMetric("Belege", totals.count.toString(), Icons.AutoMirrored.Filled.ReceiptLong,
                        DarkNavy, "Im ausgewählten Jahr", Modifier.weight(1f), AccentBlue)
                }
            }
        }
        item { LedgerYearChart(yearReceipts, selectedYear) }
        item {
            Surface(Modifier.fillMaxWidth(), shape = Ui2.shape, color = Color.White, border = BorderStroke(1.dp, BorderColor)) {
                Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LedgerKind.entries.forEach { option ->
                        Surface(onClick = { kind = option }, modifier = Modifier.weight(1f).testTag("ledger_kind_${option.name}")
                            .semantics { selected = kind == option },
                            shape = Ui2.shape, color = if (kind == option) AccentBlue else Color.White) {
                            Box(Modifier.heightIn(min = 40.dp).padding(6.dp), contentAlignment = Alignment.Center) {
                                Text(option.label, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold,
                                    color = if (kind == option) Color.White else DarkNavy)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LedgerDropdown(propertyOptions.firstOrNull { it.first == propertyId }?.second
                    ?: if (propertyId == "") "Nicht zugeordnet" else "Immobilie", "ledger_property",
                    listOf(null to "Alle Immobilien", "" to "Nicht zugeordnet") + propertyOptions,
                    Modifier.weight(1f), propertyId != null) { propertyId = it }
                LedgerDropdown(category ?: "Kategorie", "ledger_category", listOf(null to "Alle Kategorien") + categories.map { it to it },
                    Modifier.weight(1f), category != null) { category = it }
                LedgerDropdown(if (period == LedgerPeriod.YEAR) "Zeitraum" else period.label, "ledger_period",
                    LedgerPeriod.entries.map { it to it.label }, Modifier.weight(1f), period != LedgerPeriod.YEAR) { period = it }
            }
        }
        item {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("ledger_search"),
                placeholder = { Text("Buchungen suchen …", fontSize = 13.sp) }, singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null, tint = SlateGray) },
                trailingIcon = if (query.isNotEmpty()) {{ IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Suche löschen") } }} else null,
                shape = Ui2.shape, colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White, unfocusedBorderColor = BorderColor))
        }
        item { Text("Buchungen", fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = DarkNavy) }
        if (filtered.isEmpty()) {
            item {
                LedgerCard {
                    LedgerIcon(Icons.AutoMirrored.Filled.ReceiptLong, SlateGray)
                    Text(if (yearReceipts.isEmpty()) "Noch keine Einnahmen oder Ausgaben vorhanden" else "Keine passenden Buchungen",
                        fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text(if (yearReceipts.isEmpty()) "Erfasse einen Beleg oder importiere Buchungen, um deine Finanzübersicht aufzubauen."
                        else "Passe die Filter oder den Suchbegriff an.", fontSize = 12.sp, lineHeight = 16.sp, color = SlateGray)
                }
            }
        }
        items(filtered, key = { it.id }) { receipt ->
            LedgerBooking(receipt, propertyOptions.firstOrNull { it.first == receipt.propertyId }?.second ?: "Nicht zugeordnet") { onReceipt(receipt) }
        }
        item {
            LedgerCard(Modifier.testTag("ledger_exports")) {
                Text("Export & Auswertung", fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                Text("Daten exportieren und Auswertungen erstellen", fontSize = 12.sp, lineHeight = 16.sp, color = SlateGray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LedgerExportAction("DATEV", "Export für Steuerberater", Icons.Default.FileDownload,
                        Modifier.weight(1f).testTag("ledger_datev"), true, onDatev)
                    LedgerExportAction("PDF-Bericht", "Übersicht als PDF erstellen", Icons.Default.Description,
                        Modifier.weight(1f).testTag("ledger_pdf"), false, onPdf)
                }
            }
        }
        item {
            LedgerCard {
                Surface(onClick = { toolsExpanded = !toolsExpanded }, modifier = Modifier.fillMaxWidth().testTag("ledger_tools"), color = Color.White) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LedgerIcon(Icons.Default.Settings, SlateGray)
                        Column(Modifier.weight(1f)) {
                            Text("Weitere Werkzeuge", fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                            Text("Zusätzliche Funktionen für deine Buchhaltung", fontSize = 11.sp, lineHeight = 14.sp, color = SlateGray)
                        }
                        Icon(if (toolsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            if (toolsExpanded) "Werkzeuge einklappen" else "Werkzeuge ausklappen", tint = SlateGray)
                    }
                }
                if (toolsExpanded) tools()
            }
        }
    }
}

@Composable
private fun LedgerCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = Ui2.shape, colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun LedgerIcon(icon: ImageVector, color: Color) {
    Box(Modifier.size(40.dp).background(color.copy(alpha = .10f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun LedgerMetric(title: String, value: String, icon: ImageVector, color: Color, subtitle: String?,
    modifier: Modifier, iconColor: Color = color) {
    LedgerCard(modifier.fillMaxHeight().testTag("ledger_metric_$title")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LedgerIcon(icon, iconColor)
            Text(title, fontSize = 12.sp, lineHeight = 16.sp, color = DarkNavy, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // Allow very large real amounts to wrap; never truncate financial values.
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color, lineHeight = 25.sp)
        if (subtitle != null) Text(subtitle, fontSize = 10.sp, lineHeight = 13.sp, color = SlateGray)
    }
}

@Composable
private fun <T> LedgerDropdown(label: String, tag: String, options: List<Pair<T, String>>,
    modifier: Modifier = Modifier, selected: Boolean = false, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(onClick = { expanded = true }, modifier = Modifier.testTag(tag).fillMaxWidth(), shape = Ui2.shape,
            color = Color.White, border = BorderStroke(1.dp, if (selected) AccentBlue else BorderColor)) {
            Row(Modifier.heightIn(min = 40.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 12.sp, lineHeight = 16.sp, color = if (selected) AccentBlue else DarkNavy,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Default.ExpandMore, null, tint = SlateGray, modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (value, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); expanded = false }) }
        }
    }
}

@Composable
private fun LedgerBooking(receipt: Receipt, property: String, onClick: () -> Unit) {
    val income = LedgerPresentation.isIncome(receipt)
    val color = if (income) EmeraldGreen else CrimsonRed
    val category = LedgerPresentation.category(receipt)
    val icon = when {
        income -> Icons.Default.Home
        category.contains("fahrt", true) -> Icons.Default.DirectionsCar
        category.contains("versicher", true) -> Icons.Default.Shield
        category.contains("instand", true) || category.contains("sanier", true) -> Icons.Default.Build
        else -> Icons.AutoMirrored.Filled.ReceiptLong
    }
    Card(onClick, Modifier.fillMaxWidth().testTag("ledger_receipt_${receipt.id}"), shape = Ui2.shape,
        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LedgerIcon(icon, color)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(receipt.aussteller.ifBlank { receipt.beschreibung.ifBlank { "Beleg" } }, fontSize = 13.sp, lineHeight = 17.sp,
                        fontWeight = FontWeight.Bold, color = DarkNavy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(LedgerPresentation.displayDate(receipt), fontSize = 11.sp, lineHeight = 14.sp, color = SlateGray)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(LedgerPresentation.signedMoney(receipt), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = color)
                }
                Icon(Icons.Default.ChevronRight, null, tint = SlateGray, modifier = Modifier.size(16.dp))
            }
            Row(Modifier.padding(start = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(category, fontSize = 10.sp, lineHeight = 13.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).background(color.copy(alpha = .08f), Ui2.shape).padding(horizontal = 7.dp, vertical = 3.dp))
                Icon(Icons.Default.Apartment, null, tint = SlateGray, modifier = Modifier.size(13.dp))
                Text(property, fontSize = 10.sp, lineHeight = 13.sp, color = SlateGray, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun LedgerExportAction(title: String, subtitle: String, icon: ImageVector, modifier: Modifier,
    primary: Boolean, onClick: () -> Unit) {
    Surface(onClick, modifier, shape = Ui2.iconShape, color = if (primary) AccentBlue else Color.White,
        border = BorderStroke(1.dp, AccentBlue)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(icon, null, modifier = Modifier.size(18.dp), tint = if (primary) Color.White else AccentBlue)
                Text(title, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, color = if (primary) Color.White else AccentBlue)
            }
            Text(subtitle, fontSize = 10.sp, lineHeight = 13.sp, color = if (primary) Color.White else SlateGray)
        }
    }
}

/** Ledger-only version: leave the existing shared chart and other screens unchanged. */
@Composable
private fun LedgerYearChart(receipts: List<Receipt>, year: Int) {
    val monthly = remember(receipts) {
        (1..12).map { month -> LedgerPresentation.totals(receipts.filter { LedgerPresentation.date(it)?.monthValue == month }) }
    }
    val upper = monthly.maxOf { maxOf(0.0, it.income, it.expense) }.coerceAtLeast(1.0)
    val lower = monthly.minOf { minOf(0.0, it.income, it.expense) }
    val range = upper - lower
    val zeroY = (upper / range * 80).toFloat()
    val months = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")
    LedgerCard(Modifier.testTag("ledger_chart")) {
        Text("Verlauf $year", fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Einnahmen" to EmeraldGreen, "Ausgaben" to CrimsonRed).forEach { (label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(6.dp).background(color, CircleShape))
                    Text(label, fontSize = 10.sp, lineHeight = 13.sp, color = SlateGray)
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(102.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.width(58.dp).height(80.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                listOf(upper, (upper + lower) / 2, lower).forEach {
                    Text(java.text.NumberFormat.getIntegerInstance(Locale.GERMANY).format(it) + " €", fontSize = 8.sp, lineHeight = 11.sp,
                        color = SlateGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.weight(1f)) {
                Column(Modifier.fillMaxWidth().height(80.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    repeat(3) { HorizontalDivider(color = BorderColor.copy(alpha = .5f)) }
                }
                if (lower < 0) HorizontalDivider(Modifier.offset(y = zeroY.dp).testTag("ledger_chart_zero"), color = SlateGray)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    monthly.forEachIndexed { index, total ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(Modifier.height(80.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                listOf(Triple(total.income, EmeraldGreen, "INCOME"), Triple(total.expense, CrimsonRed, "EXPENSE"))
                                    .forEach { (value, color, kind) ->
                                    Box(Modifier.weight(1f).height(80.dp)) {
                                        Box(Modifier.fillMaxWidth()
                                            .offset(y = ((upper - maxOf(value, 0.0)) / range * 80).toFloat().dp)
                                            .height((abs(value) / range * 80).toFloat().dp)
                                            .testTag("ledger_bar_${kind}_${index + 1}")
                                            .background(color, if (value >= 0) RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                                                else RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)))
                                    }
                                }
                            }
                            Text(months[index], fontSize = 8.sp, lineHeight = 11.sp, color = SlateGray, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
