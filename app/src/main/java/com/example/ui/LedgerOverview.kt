package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
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
    bankAssignments: List<com.example.data.BankRentAssignment> = emptyList(),
    bankLinks: List<com.example.data.BankReceiptLink> = emptyList(),
    bankTransactions: List<com.example.data.BankTransaction> = emptyList(),
    onBank: (com.example.data.BankTransaction) -> Unit = {},
    onReceipt: (Receipt) -> Unit
) {
    var view by rememberSaveable { mutableStateOf(LedgerView.PAYMENTS) }
    val entries = remember(receipts, bankAssignments, bankLinks, bankTransactions, view) {
        LedgerPaymentPresentation.entries(receipts, bankAssignments, bankLinks, bankTransactions, view)
    }
    val years = remember(entries) {
        entries.mapNotNull { CalendarInput.parseIsoDate(it.date)?.year }.distinct().sortedDescending()
            .ifEmpty { listOf(java.time.LocalDate.now().year) }
    }
    var selectedYear by rememberSaveable { mutableIntStateOf(years.first()) }
    // Receipts arrive asynchronously; start with the newest available year unless the user chose one.
    var yearChosen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(years) { if (!yearChosen || selectedYear !in years) selectedYear = years.first() }
    var kind by rememberSaveable { mutableStateOf(LedgerKind.ALL) }
    var propertyId by rememberSaveable { mutableStateOf<String?>(null) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var period by rememberSaveable { mutableStateOf(LedgerPeriod.YEAR) }
    var query by rememberSaveable { mutableStateOf("") }
    val filters = LedgerFilters(selectedYear, kind, propertyId, category, period, query)
    val knownPropertyIds = properties.map { it.propertyId }.toSet()
    val yearEntries = remember(entries, selectedYear) { entries.filter { CalendarInput.parseIsoDate(it.date)?.year == selectedYear } }
    val filtered = remember(entries, filters, knownPropertyIds) {
        LedgerPaymentPresentation.filter(entries, filters, knownPropertyIds = knownPropertyIds)
    }
    val totals = remember(filtered) { LedgerPaymentPresentation.totals(filtered) }
    val previous = remember(entries, filters, knownPropertyIds) {
        LedgerPaymentPresentation.totals(LedgerPaymentPresentation.filter(entries, filters.copy(year = selectedYear - 1),
            today = java.time.LocalDate.now().minusYears(1), knownPropertyIds = knownPropertyIds))
    }
    val propertyOptions = remember(properties) {
        properties.map { it.propertyId to it.name.ifBlank { it.adresse.ifBlank { "Immobilie" } } }
    }
    val categories = remember(entries) { entries.map { it.category }.distinct().sorted() }

    LazyColumn(
        Modifier.fillMaxSize().testTag("ledger_overview"),
        contentPadding = PaddingValues(16.dp)
    ) {
        ledgerSection {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Einnahmen & Ausgaben", fontSize = 22.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, color = DarkNavy, modifier = Modifier.testTag("ledger_title"))
                    Text("Finanzübersicht deiner Immobilien", fontSize = 11.sp, lineHeight = 14.sp, color = SlateGray)
                }
                LedgerDropdown(selectedYear.toString(), "ledger_year", years.map { it to it.toString() }, Modifier.width(68.dp)) {
                    selectedYear = it; yearChosen = true
                }
            }
        }
        ledgerSection {
            LedgerDropdown(view.label, "ledger_view", LedgerView.entries.map { it to it.label }) { view = it }
            Text(if (view == LedgerView.PAYMENTS) "Erfasste Belege und bestätigte Bankmieten · steuerliche Freigabe separat"
                else "Ausdrücklich freigegebene Belegbuchungen", fontSize = 11.sp, color = SlateGray)
        }
        ledgerSection {
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
                    LedgerMetric("Buchungen", totals.count.toString(), Icons.AutoMirrored.Filled.ReceiptLong,
                        DarkNavy, "Mit den gewählten Filtern", Modifier.weight(1f), AccentBlue)
                }
            }
        }
        ledgerSection { LedgerYearChart(filtered, selectedYear) }
        ledgerSection { LedgerCategorySummary(filtered) }
        ledgerSection {
            Surface(Modifier.fillMaxWidth(), shape = Ui2.shape, color = Color.White, border = BorderStroke(0.5.dp, BorderColor)) {
                Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    LedgerKind.entries.forEach { option ->
                        Box(Modifier.weight(1f).testTag("ledger_kind_${option.name}")
                            .semantics { selected = kind == option }.clip(Ui2.shape)
                            .background(if (kind == option) AccentBlue else Color.White).clickable { kind = option }
                            .heightIn(min = 32.dp).padding(4.dp), contentAlignment = Alignment.Center) {
                            Text(option.label, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold,
                                color = if (kind == option) Color.White else DarkNavy)
                        }
                    }
                }
            }
        }
        ledgerSection {
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
        ledgerSection {
            Surface(Modifier.fillMaxWidth(), shape = Ui2.shape, color = Color.White, border = BorderStroke(0.5.dp, BorderColor)) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Search, null, tint = SlateGray, modifier = Modifier.size(18.dp))
                    androidx.compose.foundation.text.BasicTextField(query, { query = it },
                        Modifier.weight(1f).heightIn(min = 40.dp).testTag("ledger_search"), singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = DarkNavy),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) Text("Buchungen suchen …", fontSize = 12.sp, color = SlateGray)
                                inner()
                            }
                        })
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Suche löschen", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        item {
            Surface(Modifier.fillMaxWidth().testTag("ledger_bookings"), color = Color.White,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Buchungen", fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    if (filtered.isEmpty()) {
                        LedgerIcon(Icons.AutoMirrored.Filled.ReceiptLong, SlateGray)
                        Text(if (yearEntries.isEmpty()) "Noch keine Einnahmen oder Ausgaben vorhanden" else "Keine passenden Buchungen",
                            fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                        Text(if (yearEntries.isEmpty()) "Erfasse einen Beleg oder importiere Buchungen, um deine Finanzübersicht aufzubauen."
                            else "Passe die Filter oder den Suchbegriff an.", fontSize = 12.sp, lineHeight = 16.sp, color = SlateGray)
                    }
                }
            }
        }
        // Contiguous white rows form one visual list card while retaining lazy composition for large datasets.
        itemsIndexed(filtered, key = { _, row -> row.key }) { index, receipt ->
            Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp)) {
                LedgerBooking(receipt, propertyOptions.firstOrNull { it.first == receipt.propertyId }?.second
                    ?: "Nicht zugeordnet") {
                    receipt.receipt?.let(onReceipt) ?: receipt.transaction?.let(onBank)
                }
                if (index < filtered.lastIndex) HorizontalDivider(color = BorderColor.copy(alpha = .5f))
            }
        }
        item {
            Box(Modifier.testTag("ledger_end").fillMaxWidth().height(8.dp).background(Color.White,
                RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)))
            Spacer(Modifier.height(10.dp))
        }

    }
}

private fun LazyListScope.ledgerSection(content: @Composable ColumnScope.() -> Unit) {
    item { Column(Modifier.fillMaxWidth().padding(bottom = 10.dp), content = content) }
}

@Composable
private fun LedgerCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = Ui2.shape, colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(0.5.dp, BorderColor)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun LedgerIcon(icon: ImageVector, color: Color, size: androidx.compose.ui.unit.Dp = 32.dp) {
    Box(Modifier.size(size).background(color.copy(alpha = .10f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun LedgerMetric(title: String, value: String, icon: ImageVector, color: Color, subtitle: String?,
    modifier: Modifier, iconColor: Color = color) {
    Card(modifier.fillMaxHeight().testTag("ledger_metric_$title"), shape = Ui2.shape,
        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(0.5.dp, BorderColor)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LedgerIcon(icon, iconColor)
                Text(title, fontSize = 12.sp, lineHeight = 16.sp, color = DarkNavy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color, lineHeight = 23.sp)
            if (subtitle != null) Text(subtitle, fontSize = 9.sp, lineHeight = 12.sp, color = SlateGray)
        }
    }
}

@Composable
private fun <T> LedgerDropdown(label: String, tag: String, options: List<Pair<T, String>>,
    modifier: Modifier = Modifier, selected: Boolean = false, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(modifier = Modifier.fillMaxWidth(), shape = Ui2.shape,
            color = Color.White, border = BorderStroke(1.dp, if (selected) AccentBlue else BorderColor)) {
            Row(Modifier.testTag(tag).clickable { expanded = true }.heightIn(min = 32.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
private fun LedgerBooking(receipt: LedgerEntry, property: String, onClick: () -> Unit) {
    val income = receipt.income
    val color = if (income) EmeraldGreen else CrimsonRed
    val category = receipt.category
    val icon = when {
        income -> Icons.Outlined.Home
        category.contains("fahrt", true) -> Icons.Outlined.DirectionsCar
        category.contains("versicher", true) -> Icons.Outlined.Shield
        category.contains("instand", true) || category.contains("sanier", true) -> Icons.Outlined.Build
        else -> Icons.AutoMirrored.Filled.ReceiptLong
    }
    Row(Modifier.fillMaxWidth().testTag(receipt.tag).clickable(onClick = onClick)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LedgerIcon(icon, if (income) EmeraldGreen else SlateGray)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(receipt.partner.ifBlank { receipt.description.ifBlank { "Beleg" } },
                    fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = DarkNavy,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                val signed = if (receipt.income) receipt.amount else -receipt.amount
                Text((if (signed >= 0) "+" else "−") + LedgerPresentation.money(abs(signed)), fontSize = 13.sp, lineHeight = 16.sp,
                    fontWeight = FontWeight.Bold, color = color)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(CalendarInput.parseIsoDate(receipt.date)?.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) ?: receipt.date, fontSize = 9.sp, lineHeight = 12.sp, color = SlateGray)
                Text(category, fontSize = 9.sp, lineHeight = 12.sp, color = color, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                        .background(color.copy(alpha = .08f), Ui2.shape).padding(horizontal = 5.dp, vertical = 2.dp))
            }
            if (receipt.transaction != null) Text("Bankzahlung bestätigt · Buchungsfreigabe separat", fontSize = 9.sp, color = SlateGray)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(Icons.Default.Apartment, null, tint = SlateGray, modifier = Modifier.size(10.dp))
                Text(property, fontSize = 9.sp, lineHeight = 11.sp, color = SlateGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = SlateGray, modifier = Modifier.size(14.dp))
    }
}

/** Ledger-only version: leave the existing shared chart and other screens unchanged. */
@Composable
private fun LedgerYearChart(receipts: List<LedgerEntry>, year: Int) {
    val monthly = remember(receipts) {
        (1..12).map { month -> LedgerPaymentPresentation.totals(receipts.filter { CalendarInput.parseIsoDate(it.date)?.monthValue == month }) }
    }
    val upper = monthly.maxOf { maxOf(0.0, it.income, it.expense) }.coerceAtLeast(1.0)
    val lower = monthly.minOf { minOf(0.0, it.income, it.expense) }
    val range = upper - lower
    val zeroY = (upper / range * 52).toFloat()
    val months = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")
    LedgerCard(Modifier.testTag("ledger_chart")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Verlauf $year", fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold,
                color = DarkNavy, modifier = Modifier.weight(1f))
            listOf("Einnahmen" to EmeraldGreen, "Ausgaben" to CrimsonRed).forEach { (label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.size(5.dp).background(color, CircleShape))
                    Text(label, fontSize = 9.sp, lineHeight = 12.sp, color = SlateGray)
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(66.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.width(48.dp).height(52.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                listOf(upper, (upper + lower) / 2, lower).forEach {
                    val label = if (abs(it) >= 1_000_000) {
                        val format = java.text.NumberFormat.getNumberInstance(Locale.GERMANY).apply { maximumFractionDigits = 1 }
                        format.format(it / 1_000_000) + " Mio. €"
                    } else java.text.NumberFormat.getIntegerInstance(Locale.GERMANY).format(it) + " €"
                    Text(label, fontSize = 8.sp, lineHeight = 11.sp,
                        color = SlateGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.weight(1f)) {
                Column(Modifier.fillMaxWidth().height(52.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    repeat(3) { HorizontalDivider(color = BorderColor.copy(alpha = .5f)) }
                }
                if (lower < 0) HorizontalDivider(Modifier.offset(y = zeroY.dp).testTag("ledger_chart_zero"), color = SlateGray)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    monthly.forEachIndexed { index, total ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(Modifier.height(52.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                listOf(Triple(total.income, EmeraldGreen, "INCOME"), Triple(total.expense, CrimsonRed, "EXPENSE"))
                                    .forEach { (value, color, kind) ->
                                    Box(Modifier.weight(1f).height(52.dp)) {
                                        Box(Modifier.fillMaxWidth()
                                            .offset(y = ((upper - maxOf(value, 0.0)) / range * 52).toFloat().dp)
                                            .height((abs(value) / range * 52).toFloat().dp)
                                            .testTag("ledger_bar_${kind}_${index + 1}")
                                            .background(color, if (value >= 0) RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
                                                else RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp)))
                                    }
                                }
                            }
                            Text(months[index], fontSize = 9.sp, lineHeight = 12.sp, color = SlateGray, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}


/** Read-only category aggregation of the selected year's existing expense receipts. */
@Composable
private fun LedgerCategorySummary(receipts: List<LedgerEntry>) {
    val expenses = remember(receipts) {
        receipts.filterNot { it.income }.groupBy { it.category }
            .mapValues { (_, rows) -> rows.sumOf { it.amount } }.entries.sortedByDescending { it.value }
    }
    val total = expenses.sumOf { it.value }
    LedgerCard(Modifier.testTag("ledger_categories")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ausgaben nach Kategorie", fontSize = 15.sp, lineHeight = 19.sp,
                fontWeight = FontWeight.Bold, color = DarkNavy, modifier = Modifier.weight(1f))
            Text(LedgerPresentation.money(total), fontSize = 12.sp, lineHeight = 16.sp,
                fontWeight = FontWeight.Bold, color = DarkNavy)
        }
        if (expenses.isEmpty()) Text("Noch keine Ausgaben im ausgewählten Jahr", fontSize = 11.sp, color = SlateGray)
        expenses.forEach { (category, value) ->
            Row(Modifier.heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val icon = when {
                    category.contains("instand", true) || category.contains("sanier", true) -> Icons.Outlined.Build
                    category.contains("fahrt", true) -> Icons.Outlined.DirectionsCar
                    category.contains("versicher", true) -> Icons.Outlined.Shield
                    else -> Icons.Outlined.Description
                }
                LedgerIcon(icon, SlateGray, 24.dp)
                Text(category, fontSize = 10.sp, lineHeight = 13.sp, color = DarkNavy,
                    modifier = Modifier.weight(1.2f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).height(5.dp).background(BorderColor, CircleShape)) {
                    val fraction = if (total > 0) (value / total).coerceIn(0.0, 1.0).toFloat() else 0f
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(CrimsonRed.copy(alpha = .75f), CircleShape))
                }
                Text(LedgerPresentation.money(value), fontSize = 10.sp, lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold, color = DarkNavy)
                Text(if (total > 0 && value >= 0) "${(value / total * 100).toInt()} %" else "–",
                    fontSize = 9.sp, color = SlateGray, modifier = Modifier.width(28.dp))
            }
        }
    }
}
