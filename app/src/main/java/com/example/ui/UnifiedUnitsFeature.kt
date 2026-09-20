package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ManagedDocument
import com.example.data.PropertyMetadata
import com.example.data.Receipt
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun UnifiedPropertyUnitsScreen(
    viewModel: ReceiptViewModel,
    property: PropertyMetadata,
    units: List<WohneinheitStatus>,
    receipts: List<Receipt>,
    documents: List<ManagedDocument>,
    onBackToProperty: () -> Unit
) {
    val context = LocalContext.current
    var selectedUnitId by remember { mutableStateOf<String?>(null) }
    var year by remember { mutableIntStateOf(LocalDate.now().year) }

    val selected = units.firstOrNull {
        PropertyUnitScopedData.stableUnitId(property.propertyId, it) == selectedUnitId
    }
    if (selected != null) {
        UnifiedUnitDetailScreen(
            viewModel = viewModel,
            property = property,
            unit = selected,
            receipts = receipts,
            documents = documents,
            onBack = { selectedUnitId = null }
        )
        return
    }

    val yearRows = remember(property.propertyId, units, receipts, year) {
        RentTrackingLogic.year(context, property.propertyId, units, receipts, year)
    }
    val totalExpected = yearRows.sumOf { it.expected }
    val totalActual = yearRows.sumOf { it.actual }
    val totalMissing = yearRows.sumOf { it.missing }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackToProperty) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = DarkNavy)
                }
                Text(
                    property.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        item {
            Text("Mieteinnahmen & Nebenkosten", fontSize = 22.sp, fontWeight = FontWeight.Black, color = DarkNavy)
            Text("Ist-Einnahmen aus Belegen · Sollwerte aus den Mietdaten", fontSize = 12.sp, color = SlateGray)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { year-- },
                    shape = Ui2.controlShape,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 6.dp)
                ) { Text("‹", fontSize = 22.sp, color = AccentBlue) }

                Card(
                    shape = Ui2.controlShape,
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                        Text("Steuerjahr $year", fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 15.sp)
                    }
                }

                OutlinedButton(
                    onClick = { year++ },
                    shape = Ui2.controlShape,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 6.dp)
                ) { Text("›", fontSize = 22.sp, color = AccentBlue) }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = Ui2.shape,
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Jahresübersicht",
                            modifier = Modifier.weight(1f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkNavy
                        )
                        Text("Details anzeigen", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = AccentBlue, modifier = Modifier.size(17.dp))
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UnifiedAnnualMetric(
                            "Ist-Einnahmen",
                            NumberFormatter.format(totalActual),
                            "aus Belegen",
                            Icons.Default.Assessment,
                            EmeraldGreen,
                            Color(0xFFF0FAF5),
                            Modifier.weight(1f)
                        )
                        UnifiedAnnualMetric(
                            "Soll-Hochrechnung",
                            NumberFormatter.format(totalExpected),
                            "aus aktuellen Verträgen",
                            Icons.Default.Payments,
                            DarkNavy,
                            Color(0xFFF3F7FD),
                            Modifier.weight(1f)
                        )
                        UnifiedAnnualMetric(
                            "Differenz / Rückstand",
                            NumberFormatter.format(totalMissing),
                            "offen",
                            Icons.Default.Assessment,
                            CrimsonRed,
                            Color(0xFFFFF3F3),
                            Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = BorderColor)
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3FF))
                        ) {
                            Text(
                                "i",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                color = AccentBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "Steuerlich maßgeblich sind die tatsächlich zugeflossenen Einnahmen. Der Sollwert dient nur der Mietkontrolle.",
                            modifier = Modifier.weight(1f),
                            fontSize = 10.sp,
                            color = SlateGray
                        )
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Wohneinheiten",
                    modifier = Modifier.weight(1f),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkNavy
                )
                Text("${units.size} Einheiten", fontSize = 12.sp, color = SlateGray)
            }
        }

        items(units, key = { PropertyUnitScopedData.stableUnitId(property.propertyId, it) }) { unit ->
            val nk = PropertyUnitScopedData.rentValue(context, property.propertyId, unit, "nk")
            val other = PropertyUnitScopedData.rentValue(context, property.propertyId, unit, "other")
            val month = RentTrackingLogic.month(context, property.propertyId, unit, receipts, YearMonth.now())
            UnifiedUnitOverviewCard(
                unit = unit,
                nk = nk,
                other = other,
                missing = month.missing,
                onOpen = { selectedUnitId = PropertyUnitScopedData.stableUnitId(property.propertyId, unit) }
            )
        }
    }
}

@Composable
private fun UnifiedAnnualMetric(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    valueColor: Color,
    background: Color,
    modifier: Modifier
) {
    Card(modifier = modifier, shape = Ui2.controlShape, colors = CardDefaults.cardColors(containerColor = background)) {
        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.55f))
            ) {
                Icon(icon, null, tint = valueColor, modifier = Modifier.padding(7.dp).size(18.dp))
            }
            Text(title, fontSize = 10.sp, color = SlateGray, maxLines = 2)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
            Text(subtitle, fontSize = 9.sp, color = SlateGray, maxLines = 2)
        }
    }
}

@Composable
private fun UnifiedUnitOverviewCard(
    unit: WohneinheitStatus,
    nk: Double,
    other: Double,
    missing: Double,
    onOpen: () -> Unit
) {
    val rented = unit.status == "Vermietet"
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = Ui2.shape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(unit.label.ifBlank { unit.name }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                        UnifiedStatusPill(if (rented) "Vermietet" else unit.status, rented)
                    }
                    Text(
                        if (rented) unit.mieter.ifBlank { "Mieter nicht hinterlegt" } else "Leerstand",
                        fontSize = 11.sp,
                        color = SlateGray
                    )
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = SlateGray)
            }

            HorizontalDivider(color = BorderColor)
            Row(Modifier.fillMaxWidth()) {
                UnifiedMiniMetric(
                    Icons.Default.HomeWork,
                    "Monatliches Soll",
                    NumberFormatter.format(unit.kaltmiete),
                    "Kaltmiete",
                    Modifier.weight(1f)
                )
                VerticalDivider(Modifier.height(58.dp), color = BorderColor)
                UnifiedMiniMetric(
                    Icons.Default.Receipt,
                    "Nebenkosten",
                    NumberFormatter.format(nk),
                    "laut Vertrag",
                    Modifier.weight(1f)
                )
                VerticalDivider(Modifier.height(58.dp), color = BorderColor)
                UnifiedMiniMetric(
                    Icons.Default.Payments,
                    "Sonstiges",
                    NumberFormatter.format(other),
                    "z. B. Garage",
                    Modifier.weight(1f)
                )
            }

            if (rented) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onOpen,
                        modifier = Modifier.weight(1f),
                        shape = Ui2.controlShape,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 7.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(17.dp))
                        Text(" Monatscheck", fontSize = 11.sp, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onOpen,
                        modifier = Modifier.weight(1f),
                        shape = Ui2.controlShape,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 7.dp)
                    ) {
                        Icon(Icons.Default.Settings, null, modifier = Modifier.size(17.dp))
                        Text(" Mieterwechsel", fontSize = 11.sp, maxLines = 1)
                    }
                }
                if (missing > 0.01) {
                    Text("Offener Betrag aktuell: ${NumberFormatter.format(missing)}", fontSize = 9.sp, color = CrimsonRed)
                }
            } else {
                OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = Ui2.controlShape) {
                    Text("+  Neu vermieten / Mietverhältnis anlegen", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun UnifiedMiniMetric(
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier
) {
    Row(
        modifier.padding(horizontal = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3FF))) {
            Icon(icon, null, tint = AccentBlue, modifier = Modifier.padding(7.dp).size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 9.sp, color = SlateGray, maxLines = 1)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy, maxLines = 1)
            Text(subtitle, fontSize = 8.sp, color = SlateGray, maxLines = 1)
        }
    }
}

@Composable
private fun UnifiedStatusPill(label: String, positive: Boolean) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (positive) Color(0xFFE8F8EF) else Color(0xFFFFEEEE)
        )
    ) {
        Text(
            "●  $label",
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (positive) EmeraldGreen else CrimsonRed
        )
    }
}

@Composable
private fun UnifiedUnitDetailScreen(
    viewModel: ReceiptViewModel,
    property: PropertyMetadata,
    unit: WohneinheitStatus,
    receipts: List<Receipt>,
    documents: List<ManagedDocument>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showHistory by remember { mutableStateOf(false) }
    var showMonthCheck by remember { mutableStateOf(false) }
    var showDocuments by remember { mutableStateOf(false) }
    var selectedDocumentId by remember { mutableStateOf<String?>(null) }

    val unitId = PropertyUnitScopedData.stableUnitId(property.propertyId, unit)
    val unitReceipts = receipts.filter { it.wohneinheit == unit.name }
    val unitDocs = documents.filter { it.unitId == unitId }
    val month = RentTrackingLogic.month(context, property.propertyId, unit, receipts, YearMonth.now())
    val nk = PropertyUnitScopedData.rentValue(context, property.propertyId, unit, "nk")
    val other = PropertyUnitScopedData.rentValue(context, property.propertyId, unit, "other")
    val totalRent = unit.kaltmiete + nk + other
    val lastPayment = unitReceipts.filter { isRentalIncomeReceipt(it) }.maxByOrNull { it.datum }
    val paidOnTime = month.expected > 0.01 && month.missing <= 0.01

    val selectedDocument = selectedDocumentId?.let { id -> unitDocs.firstOrNull { it.documentId == id } }
    if (selectedDocument != null) {
        ManagedDocumentDetailScreen(
            document = selectedDocument,
            property = property,
            units = listOf(unit),
            onBack = { selectedDocumentId = null },
            onAnalyze = { viewModel.analyzeManagedDocument(selectedDocument.documentId) },
            onDownload = { viewModel.downloadManagedDocument(selectedDocument.documentId) },
            onSync = { viewModel.syncManagedDocumentNow(selectedDocument.documentId) },
            onUpdatePresentation = { title, description ->
                viewModel.updateManagedDocumentPresentation(selectedDocument.documentId, title, description)
            }
        )
        return
    }

    if (showHistory) {
        TenantHistoryDialog(
            unit = unit,
            nebenkostenCurrent = nk,
            sonstigeCurrent = other,
            onDismiss = { showHistory = false },
            onCurrentTenantChanged = { newPeriod ->
                PropertyUnitScopedData.setRentValues(
                    context,
                    property.propertyId,
                    unit,
                    newPeriod.nebenkosten,
                    newPeriod.sonstige
                )
                viewModel.updateWohneinheit(
                    unit.copy(
                        status = "Vermietet",
                        mieter = newPeriod.tenantName,
                        kaltmiete = newPeriod.kaltmiete,
                        mietvertragsstart = newPeriod.startDate
                    )
                )
            },
            onHistoryChanged = {},
            propertyId = property.propertyId
        )
    }

    if (showMonthCheck) {
        UnifiedMonthlyCheckDialog(
            title = unit.label.ifBlank { unit.name },
            expected = month.expected,
            actual = month.actual,
            missing = month.missing,
            onDismiss = { showMonthCheck = false }
        )
    }

    if (showDocuments) {
        AlertDialog(
            onDismissRequest = { showDocuments = false },
            shape = Ui2.shape,
            title = { Text("Dokumente · ${unit.label.ifBlank { unit.name }}", fontWeight = FontWeight.Bold) },
            text = {
                if (unitDocs.isEmpty()) {
                    Text("Noch keine Dokumente dieser Wohneinheit vorhanden.", color = SlateGray)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(unitDocs, key = { it.documentId }) { doc ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    showDocuments = false
                                    selectedDocumentId = doc.documentId
                                },
                                shape = Ui2.controlShape,
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, BorderColor)
                            ) {
                                Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Description, null, tint = AccentBlue)
                                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                        Text(
                                            doc.title.ifBlank { doc.originalFilename },
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            color = DarkNavy
                                        )
                                        Text(documentTypeLabel(doc), fontSize = 9.sp, color = SlateGray)
                                    }
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = SlateGray)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDocuments = false }) { Text("Schließen") }
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = DarkNavy)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    unit.label.ifBlank { unit.name },
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkNavy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(unit.mieter.ifBlank { "Kein Mieter" }, fontSize = 12.sp, color = SlateGray)
                    UnifiedStatusPill(
                        if (unit.status == "Vermietet") "Vermietet" else unit.status,
                        unit.status == "Vermietet"
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item {
                UnifiedDetailCard("Wohneinheit im Überblick") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        UnifiedDetailMetric(
                            "Kaltmiete",
                            NumberFormatter.format(unit.kaltmiete),
                            Icons.Default.HomeWork,
                            DarkNavy,
                            Color(0xFFF4F8FD),
                            Modifier.weight(1f)
                        )
                        UnifiedDetailMetric(
                            "Nebenkosten",
                            NumberFormatter.format(nk),
                            Icons.Default.Payments,
                            DarkNavy,
                            Color(0xFFF4F8FD),
                            Modifier.weight(1f)
                        )
                        UnifiedDetailMetric(
                            "Sonstiges",
                            NumberFormatter.format(other),
                            Icons.Default.Receipt,
                            DarkNavy,
                            Color(0xFFF4F8FD),
                            Modifier.weight(1f)
                        )
                        UnifiedDetailMetric(
                            "Gesamtmiete",
                            NumberFormatter.format(totalRent),
                            Icons.Default.Assessment,
                            EmeraldGreen,
                            Color(0xFFF0FAF5),
                            Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                UnifiedDetailCard("Mietdaten") {
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            UnifiedDetailFact(
                                Icons.Default.CalendarMonth,
                                "Mietbeginn",
                                unit.mietvertragsstart.ifBlank { "–" }
                            )
                            UnifiedDetailFact(Icons.Default.Payments, "Zahlungsweise", "Nicht hinterlegt")
                            UnifiedDetailFact(Icons.Default.CalendarMonth, "Fälligkeit", "Nicht hinterlegt")
                        }
                        VerticalDivider(Modifier.height(132.dp), color = BorderColor)
                        Column(
                            Modifier.weight(1f).padding(start = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            UnifiedDetailFact(Icons.Default.AccountBalance, "Kaution", "Nicht hinterlegt")
                            UnifiedDetailFact(
                                Icons.Default.HomeWork,
                                "Wohnfläche",
                                "${unit.wohnflaeche.toInt()} m²"
                            )
                            UnifiedDetailFact(Icons.Default.Apartment, "Zimmer", "Nicht hinterlegt")
                        }
                    }
                }
            }

            item {
                UnifiedDetailCard("Zahlungsstatus") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        UnifiedStatusMetric(
                            "Letzte Zahlung",
                            lastPayment?.let { NumberFormatter.format(it.bruttobetrag) } ?: "–",
                            lastPayment?.datum?.let { "am $it" } ?: "keine Zahlung",
                            EmeraldGreen,
                            Color(0xFFF0FAF5),
                            Modifier.weight(1f)
                        )
                        UnifiedStatusMetric(
                            "Offener Betrag",
                            NumberFormatter.format(month.missing),
                            "",
                            if (month.missing > 0.01) CrimsonRed else DarkNavy,
                            if (month.missing > 0.01) Color(0xFFFFF3F3) else Color(0xFFF6F7FA),
                            Modifier.weight(1f)
                        )
                        UnifiedStatusMetric(
                            "Status",
                            if (paidOnTime) "Pünktlich" else if (month.expected <= 0.01) "Kein Soll" else "Offen",
                            "",
                            if (paidOnTime) EmeraldGreen else if (month.expected <= 0.01) SlateGray else CrimsonRed,
                            if (paidOnTime) Color(0xFFF0FAF5) else Color(0xFFFFF3F3),
                            Modifier.weight(1f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("i", color = AccentBlue, fontWeight = FontWeight.Bold)
                        Text(
                            "Monatscheck zeigt Ist-Zahlungen im Vergleich zum Soll.",
                            fontSize = 10.sp,
                            color = SlateGray
                        )
                    }
                }
            }

            item {
                UnifiedDetailCard("Dokumente") {
                    if (unitDocs.isEmpty()) {
                        Text("Noch keine Dokumente dieser Wohneinheit.", fontSize = 10.sp, color = SlateGray)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            unitDocs.take(3).forEach { doc ->
                                OutlinedButton(
                                    onClick = { selectedDocumentId = doc.documentId },
                                    modifier = Modifier.weight(1f),
                                    shape = Ui2.controlShape,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 5.dp, vertical = 7.dp)
                                ) {
                                    Icon(Icons.Default.Description, null, modifier = Modifier.size(16.dp))
                                    Text(" ${unifiedDocumentLabel(doc)}", fontSize = 9.sp, maxLines = 2)
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { showDocuments = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = Ui2.controlShape
                    ) {
                        Icon(Icons.Default.Description, null)
                        Text(" Alle Dokumente")
                    }
                }
            }

            item {
                UnifiedDetailCard("Aktionen") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(
                            onClick = { showMonthCheck = true },
                            modifier = Modifier.weight(1f),
                            shape = Ui2.controlShape,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 5.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(17.dp))
                            Text(" Monatscheck", fontSize = 10.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { showHistory = true },
                            modifier = Modifier.weight(1f),
                            shape = Ui2.controlShape,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 5.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Settings, null, modifier = Modifier.size(17.dp))
                            Text(" Mieterwechsel", fontSize = 10.sp, maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { showDocuments = true },
                            modifier = Modifier.weight(1f),
                            shape = Ui2.controlShape,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 5.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Description, null, modifier = Modifier.size(17.dp))
                            Text(" Dokumente öffnen", fontSize = 9.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnifiedDetailCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = Ui2.shape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            content()
        }
    }
}

@Composable
private fun UnifiedDetailMetric(
    label: String,
    value: String,
    icon: ImageVector,
    valueColor: Color,
    background: Color,
    modifier: Modifier
) {
    Card(modifier = modifier, shape = Ui2.controlShape, colors = CardDefaults.cardColors(containerColor = background)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                icon,
                null,
                tint = if (valueColor == EmeraldGreen) EmeraldGreen else AccentBlue,
                modifier = Modifier.size(19.dp)
            )
            Text(label, fontSize = 9.sp, color = SlateGray)
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
        }
    }
}

@Composable
private fun UnifiedDetailFact(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3FF))) {
            Icon(icon, null, tint = AccentBlue, modifier = Modifier.padding(7.dp).size(17.dp))
        }
        Column {
            Text(label, fontSize = 9.sp, color = SlateGray)
            Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy, maxLines = 1)
        }
    }
}

@Composable
private fun UnifiedStatusMetric(
    label: String,
    value: String,
    subtitle: String,
    valueColor: Color,
    background: Color,
    modifier: Modifier
) {
    Card(modifier = modifier, shape = Ui2.controlShape, colors = CardDefaults.cardColors(containerColor = background)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, fontSize = 9.sp, color = SlateGray)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 8.sp, color = SlateGray, maxLines = 1)
        }
    }
}

private fun unifiedDocumentLabel(document: ManagedDocument): String {
    val label = documentTypeLabel(document)
    return when {
        label.contains("Mietvertrag", true) -> "Mietvertrag"
        label.contains("Übergabe", true) -> "Übergabeprotokoll"
        label.contains("Rechnung", true) -> "Nebenkostenabrechnung"
        else -> label
    }
}

@Composable
private fun UnifiedMonthlyCheckDialog(
    title: String,
    expected: Double,
    actual: Double,
    missing: Double,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Ui2.shape,
        title = { Text("Monatscheck · $title", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UnifiedPaymentRow("Soll", expected)
                UnifiedPaymentRow("Ist laut Belegen", actual)
                UnifiedPaymentRow("Offener Betrag", missing)
                Text(
                    if (missing <= 0.01 && expected > 0.01) {
                        "Zahlung für den aktuellen Monat vollständig erfasst."
                    } else {
                        "Zahlung für den aktuellen Monat prüfen."
                    },
                    fontSize = 10.sp,
                    color = if (missing <= 0.01 && expected > 0.01) EmeraldGreen else CrimsonRed
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}

@Composable
private fun UnifiedPaymentRow(label: String, value: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = SlateGray)
        Text(NumberFormatter.format(value), fontWeight = FontWeight.Bold, color = DarkNavy)
    }
}
