package com.example.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ManagedDocument
import com.example.data.Loan
import com.example.data.PropertyMetadata
import com.example.data.Receipt
import java.time.LocalDate
import java.time.YearMonth
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal data class PropertyManagerSummary(
    val unitCount: Int,
    val rentedCount: Int,
    val vacantCount: Int,
    val expectedRent: Double,
    val actualRent: Double
) { val outstandingRent: Double get() = (expectedRent - actualRent).coerceAtLeast(0.0) }

internal object ImmobilienManagerProjection {
    fun receipts(property: PropertyMetadata, units: List<WohneinheitStatus>, all: List<Receipt>): List<Receipt> {
        if (property.id == 1) {
            return all.filter {
                it.propertyId == property.propertyId ||
                    it.propertyId == com.example.data.StableDocumentIdentity.LEGACY_PROPERTY_ID
            }
        }
        // Never infer a new property's ownership from a display/unit name. Legacy
        // receipts remain on property-1 until the user explicitly assigns them.
        return all.filter { it.propertyId == property.propertyId }
    }

    fun documents(property: PropertyMetadata, all: List<ManagedDocument>): List<ManagedDocument> =
        all.filter { it.propertyId == property.propertyId }

    fun loans(property: PropertyMetadata, all: List<Loan>): List<Loan> = all.filter {
        it.propertyId == property.propertyId ||
            (property.id == 1 && it.propertyId == com.example.data.StableDocumentIdentity.LEGACY_PROPERTY_ID)
    }

    fun summary(units: List<WohneinheitStatus>, receipts: List<Receipt>, month: YearMonth = YearMonth.now()): PropertyManagerSummary {
        val active = units.filter { it.status == "Vermietet" }
        val actual = receipts.filter {
            it.datum.startsWith(month.toString()) && isRentalIncomeReceipt(it)
        }.sumOf { it.bruttobetrag }
        return PropertyManagerSummary(
            unitCount = units.size,
            rentedCount = active.size,
            vacantCount = units.count { it.status != "Vermietet" },
            expectedRent = active.sumOf { it.kaltmiete },
            actualRent = actual
        )
    }
}

private enum class PropertySection { DASHBOARD, UNITS, RENT, RENT_MATRIX, RECEIPTS, FINANCE, RENOVATIONS, DOCUMENTS, TAX, TASKS, UTILITIES_PREP, DATA }
private enum class UnitDetailSection { OVERVIEW, TENANT, RENT, DOCUMENTS, COSTS }

@Composable
fun ImmobilienManagerScreen(viewModel: ReceiptViewModel) {
    val properties by viewModel.properties.collectAsState()
    val allReceipts by viewModel.receipts.collectAsState()
    val selected by viewModel.propertyMetadata.collectAsState()
    var openedPropertyId by remember { mutableStateOf<String?>(null) }
    var section by remember { mutableStateOf(PropertySection.DASHBOARD) }
    var showWizard by remember { mutableStateOf(false) }
    val opened = properties.firstOrNull { it.propertyId == openedPropertyId }

    if (showWizard) {
        PropertyCreationWizard(
            onDismiss = { showWizard = false },
            onSave = { metadata, units, loan ->
                viewModel.createProperty(metadata, units, loan)
                openedPropertyId = metadata.propertyId
                showWizard = false
            }
        )
    }

    if (opened == null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("properties_overview"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Apartment, null, tint = AccentBlue, modifier = Modifier.size(28.dp))
                        Text("Immobilien", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    }
                    FloatingActionButton(
                        onClick = { showWizard = true },
                        modifier = Modifier.testTag("add_property_button"),
                        containerColor = AccentBlue,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Add, "Immobilie hinzufügen")
                    }
                }
            }
            if (properties.isEmpty()) item { Text("Noch keine Immobilie vorhanden.", color = SlateGray) }
            items(properties, key = { it.propertyId }) { property ->
                val units = viewModel.getWohneinheitenForProperty(property)
                val propertyReceipts = ImmobilienManagerProjection.receipts(property, units, allReceipts)
                PropertyOverviewCard(property, ImmobilienManagerProjection.summary(units, propertyReceipts)) {
                    viewModel.selectProperty(property.propertyId)
                    openedPropertyId = property.propertyId
                    section = PropertySection.DASHBOARD
                }
            }
        }
    } else {
        LaunchedEffect(opened.propertyId) {
            if (selected?.propertyId != opened.propertyId) viewModel.selectProperty(opened.propertyId)
        }
        PropertyDetailHost(viewModel, opened, section, { section = it }) { openedPropertyId = null }
    }
}

@Composable
private fun PropertyOverviewCard(property: PropertyMetadata, summary: PropertyManagerSummary, onClick: () -> Unit) {
    val addressParts = property.adresse.split(',').map { it.trim() }.filter { it.isNotBlank() }
    val streetAndHouseNumber = addressParts.firstOrNull().orEmpty()
    val postalCodeAndCity = addressParts.drop(1).joinToString(", ")
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("property_card_${property.propertyId}"),
        shape = Ui2.shape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(property.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkNavy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                PropertyCoverImage(property, Modifier.size(96.dp), compactPlaceholder = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(streetAndHouseNumber, fontSize = 13.sp, color = SlateGray)
                    if (postalCodeAndCity.isNotBlank()) Text(postalCodeAndCity, fontSize = 13.sp, color = SlateGray)
                    Text("${summary.unitCount} Einheiten", fontSize = 12.sp, color = AccentBlue, fontWeight = FontWeight.SemiBold)
                }
            }
            HorizontalDivider(color = BorderColor)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PropertyOverviewMetric("Soll", NumberFormatter.format(summary.expectedRent), SlateGray, Modifier.weight(1f).padding(end = 12.dp))
                VerticalDivider(Modifier.height(36.dp), color = BorderColor)
                PropertyOverviewMetric("Ist", NumberFormatter.format(summary.actualRent), EmeraldGreen, Modifier.weight(1f).padding(horizontal = 12.dp))
                VerticalDivider(Modifier.height(36.dp), color = BorderColor)
                PropertyOverviewMetric("Offen", NumberFormatter.format(summary.outstandingRent), if (summary.outstandingRent > 0) CrimsonRed else EmeraldGreen, Modifier.weight(1f).padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun PropertyOverviewMetric(label: String, value: String, valueColor: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 11.sp, color = SlateGray)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
    }
}

@Composable
private fun PropertyDetailHost(
    viewModel: ReceiptViewModel,
    property: PropertyMetadata,
    section: PropertySection,
    onSection: (PropertySection) -> Unit,
    onBack: () -> Unit
) {
    val receipts by viewModel.receipts.collectAsState()
    val documents by viewModel.managedDocuments.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val units by viewModel.wohneinheitenStatus.collectAsState()
    val propertyReceipts = ImmobilienManagerProjection.receipts(property, units, receipts)
    val propertyDocuments = ImmobilienManagerProjection.documents(property, documents)
    val propertyLoans = ImmobilienManagerProjection.loans(property, loans)
    var edit by remember(property.propertyId) { mutableStateOf(false) }
    var deleteRequested by remember(property.propertyId) { mutableStateOf(false) }
    if (edit) {
        PropertyEditScreen(property, viewModel) { edit = false }
        return
    }
    BackHandler { if (section == PropertySection.DASHBOARD) onBack() else onSection(PropertySection.DASHBOARD) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (section == PropertySection.DASHBOARD) onBack() else onSection(PropertySection.DASHBOARD) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = DarkNavy)
            }
            Text(if (section == PropertySection.DASHBOARD) "Immobilie" else property.name, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            if (section == PropertySection.DASHBOARD) TextButton(onClick = { edit = true }) { Text("Bearbeiten", fontSize = 14.sp, color = AccentBlue) }
        }
        when (section) {
            PropertySection.DASHBOARD -> PropertyReferenceDetail(property, ImmobilienManagerProjection.summary(units, propertyReceipts), viewModel, onSection, onDelete = { deleteRequested = true })
            PropertySection.UNITS -> PropertyUnits(viewModel, property, units, propertyReceipts, propertyDocuments)
            PropertySection.RENT -> RentIncomeWithTenantHistoryScreen(viewModel, propertyScoped = true)
            PropertySection.RENT_MATRIX -> PropertyRentYearMatrix(property, units, propertyReceipts)
            PropertySection.RECEIPTS -> PropertyReceipts(propertyReceipts)
            PropertySection.FINANCE -> LazyColumn(Modifier.fillMaxSize().padding(16.dp)) { item { LoanManagementSection(viewModel, propertyScoped = true) } }
            PropertySection.RENOVATIONS -> PropertyRenovations(propertyReceipts)
            PropertySection.DOCUMENTS -> DocumentManagementScreen(viewModel, propertyScoped = true)
            PropertySection.TAX -> AnnualTaxAssistantScreen(viewModel)
            PropertySection.TASKS -> PropertyTasksScreen(property.propertyId, units)
            PropertySection.UTILITIES_PREP -> PropertyUtilitiesPreparation()
            PropertySection.DATA -> PropertyData(viewModel, property)
        }
    }
    if (deleteRequested) AlertDialog(
        onDismissRequest = { deleteRequested = false },
        title = { Text("Immobilie löschen?", fontWeight = FontWeight.Bold) },
        text = { Text("Das Objekt wird aus der Übersicht entfernt. Belege, Unterlagen und Buchungen bleiben zur Sicherheit erhalten.") },
        confirmButton = { Button(onClick = { viewModel.deleteProperty(property); deleteRequested = false; onBack() }, colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = { deleteRequested = false }) { Text("Abbrechen") } }
    )
}

@Composable
private fun PropertyReferenceDetail(property: PropertyMetadata, summary: PropertyManagerSummary, viewModel: ReceiptViewModel, onSection: (PropertySection) -> Unit, onDelete: () -> Unit) {
    val entries = listOf(
        PropertySection.DATA to ("Stammdaten" to Icons.Default.HomeWork),
        PropertySection.UNITS to ("Einheiten & Mietverhältnisse" to Icons.Default.Apartment),
        PropertySection.RENT to ("Mieteinnahmen & Nebenkosten" to Icons.Default.Payments),
        PropertySection.RECEIPTS to ("Einnahmen / Ausgaben" to Icons.Default.Receipt),
        PropertySection.DOCUMENTS to ("Objektunterlagen" to Icons.Default.Description),
        PropertySection.TASKS to ("Notizen & Aufgaben" to Icons.Default.Assessment)
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(shape = Ui2.shape, colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        PropertyCoverImage(property, Modifier.fillMaxWidth().height(190.dp))
                        PropertyImagePicker(property, viewModel, Modifier.align(Alignment.BottomEnd).padding(8.dp))
                    }
                    Text(property.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LocationOn, null, Modifier.size(17.dp), tint = SlateGray); Text(property.adresse, Modifier.padding(start = 4.dp), fontSize = 13.sp, color = SlateGray) }
                    Row(Modifier.fillMaxWidth()) {
                        PropertyMetric("${summary.unitCount}", "Einheiten", Modifier.weight(1f))
                        PropertyMetric(NumberFormatter.format(summary.expectedRent), "Mieteinnahmen", Modifier.weight(1f))
                        PropertyMetric("${property.wohnflaeche.toInt()} m²", "Wohnfläche", Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Card(shape = Ui2.shape, colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
                Column { entries.forEachIndexed { index, (target, entry) ->
                    if (index > 0) HorizontalDivider(color = BorderColor)
                    PropertyReferenceRow(entry.first, entry.second) { onSection(target) }
                } }
            }
        }
        item {
            OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFFFEDF0), contentColor = CrimsonRed)) {
                Icon(Icons.Default.Delete, null, Modifier.size(20.dp)); Text("  Immobilie löschen", fontSize = 13.sp)
            }
        }
    }
}

@Composable private fun PropertyMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) { Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkNavy, maxLines = 1); Text(label, fontSize = 10.sp, color = SlateGray, maxLines = 1) }
}

@Composable private fun PropertyReferenceRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(23.dp), tint = AccentBlue); Text(label, Modifier.weight(1f).padding(start = 14.dp), fontSize = 14.sp, color = DarkNavy); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp), tint = SlateGray)
    }
}

@Composable private fun PropertyCoverImage(property: PropertyMetadata, modifier: Modifier = Modifier, compactPlaceholder: Boolean = false) {
    val bitmap = remember(property.bildPfad) { property.bildPfad.takeIf { it.isNotBlank() }?.let { BitmapFactory.decodeFile(it) } }
    Card(modifier, shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3FF))) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), "Foto von ${property.name}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.HomeWork, null, Modifier.size(if (compactPlaceholder) 32.dp else 40.dp), tint = AccentBlue)
            Text(if (compactPlaceholder) "Bild hinzufügen" else "Noch kein Objektbild", fontSize = if (compactPlaceholder) 9.sp else 11.sp, color = SlateGray, maxLines = 1)
        }
    }
}

@Composable private fun PropertyImagePicker(property: PropertyMetadata, viewModel: ReceiptViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) scope.launch(Dispatchers.IO) {
        val target = File(File(context.filesDir, "property-images").apply { mkdirs() }, "${property.propertyId}-${System.currentTimeMillis()}.jpg")
        runCatching { context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } } }.onSuccess { viewModel.updatePropertyMetadata(property.copy(bildPfad = target.absolutePath)) }
    } }
    IconButton(onClick = { picker.launch("image/*") }, modifier = modifier.background(Color.White, androidx.compose.foundation.shape.CircleShape)) { Icon(Icons.Default.CameraAlt, "Objektbild hinzufügen", tint = AccentBlue) }
}

@Composable private fun PropertyEditScreen(property: PropertyMetadata, viewModel: ReceiptViewModel, onBack: () -> Unit) {
    var name by remember(property) { mutableStateOf(property.name) }; var address by remember(property) { mutableStateOf(property.adresse) }; var type by remember(property) { mutableStateOf(property.objektart) }
    var year by remember(property) { mutableStateOf(property.baujahr.toString()) }; var area by remember(property) { mutableStateOf(property.wohnflaeche.toString()) }; var land by remember(property) { mutableStateOf(property.grundstuecksgroesse.toString()) }; var notes by remember(property) { mutableStateOf(property.notizen) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = DarkNavy) }; Text("Immobilie bearbeiten", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            TextButton(onClick = { viewModel.updatePropertyMetadata(property.copy(name = name, adresse = address, objektart = type, baujahr = year.toIntOrNull() ?: property.baujahr, wohnflaeche = area.replace(',', '.').toDoubleOrNull() ?: property.wohnflaeche, grundstuecksgroesse = land.replace(',', '.').toDoubleOrNull() ?: property.grundstuecksgroesse, notizen = notes)); onBack() }) { Text("Speichern", fontSize = 14.sp, color = AccentBlue) }
        }
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Box { PropertyCoverImage(property, Modifier.fillMaxWidth().height(150.dp)); PropertyImagePicker(property, viewModel, Modifier.align(Alignment.BottomEnd).padding(8.dp)) } }
            item { PropertyEditField(name, "Name *") { name = it } }; item { PropertyEditField(address, "Adresse *") { address = it } }; item { PropertyEditField(type, "Objektart *") { type = it } }; item { PropertyEditField(year, "Baujahr") { year = it.filter(Char::isDigit) } }; item { PropertyEditField(area, "Wohnfläche (m²)") { area = it } }; item { PropertyEditField(land, "Grundstücksfläche (m²)") { land = it } }; item { PropertyEditField(notes, "Beschreibung / Notizen", false) { notes = it } }
        }
    }
}

@Composable private fun PropertyEditField(value: String, label: String, singleLine: Boolean = true, onChange: (String) -> Unit) { OutlinedTextField(value, onChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = singleLine) }

@Composable
private fun PropertyDashboard(
    viewModel: ReceiptViewModel,
    property: PropertyMetadata,
    units: List<WohneinheitStatus>,
    receipts: List<Receipt>,
    documents: List<ManagedDocument>,
    loans: List<Loan>,
    onSection: (PropertySection) -> Unit
) {
    val context = LocalContext.current
    val summary = ImmobilienManagerProjection.summary(units, receipts)
    val unchecked = receipts.count { it.pruefstatus == "UNGEPRUEFT" || it.exportStatus == "ZU_PRUEFEN" }
    val year = LocalDate.now().year
    val yearReceipts = receipts.filter { it.datum.startsWith(year.toString()) }
    val yearRentIncome = yearReceipts.filter(::isRentalIncomeReceipt).sumOf { it.bruttobetrag }
    val yearExpenses = yearReceipts.filterNot(::isRentalIncomeReceipt).sumOf { it.bruttobetrag }
    val annualLoanRates = loans.filter { it.aktiv }.sumOf { it.monatlicheRate * 12.0 }
    val restDebt = loans.filter { it.aktiv }.sumOf { it.restschuld }
    val managementCashflow = yearRentIncome - yearExpenses - annualLoanRates
    val grossYield = if (property.gesamtKaufpreis > 0.0) summary.expectedRent * 12.0 / property.gesamtKaufpreis * 100.0 else null
    val openTasks = remember(property.propertyId) { PropertyTaskStore.load(context, property.propertyId).count { !it.done } }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
                Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
                    Text("Miete aktueller Monat", fontWeight = FontWeight.Bold, color = DarkNavy)
                    LinearProgressIndicator(progress = { if (summary.expectedRent <= 0) 0f else (summary.actualRent / summary.expectedRent).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("Soll ${NumberFormatter.format(summary.expectedRent)} · Ist ${NumberFormatter.format(summary.actualRent)} · Offen ${NumberFormatter.format(summary.outstandingRent)}", fontSize = 11.sp)
                    Text("${summary.vacantCount} freie/zu prüfende Einheiten · $unchecked ungeprüfte Belege · ${loans.count { it.aktiv }} aktive Darlehen", fontSize = 10.sp, color = SlateGray)
                    Text("Steuerjahr $year · ${documents.size} Dokumente · $openTasks offene Aufgaben", fontSize = 10.sp, color = SlateGray)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
                Column(Modifier.padding(14.dp), Arrangement.spacedBy(6.dp)) {
                    Text("Schnellaktionen", fontWeight = FontWeight.Bold, color = DarkNavy)
                    Button(onClick = { viewModel.setScreen(AppScreen.ADD_RECEIPT) }, modifier = Modifier.fillMaxWidth()) { Text("Beleg hinzufügen") }
                    OutlinedButton(onClick = { onSection(PropertySection.RENT) }, modifier = Modifier.fillMaxWidth()) { Text("Miete prüfen") }
                    OutlinedButton(onClick = { onSection(PropertySection.DOCUMENTS) }, modifier = Modifier.fillMaxWidth()) { Text("Dokumente öffnen") }
                    OutlinedButton(onClick = { onSection(PropertySection.UNITS) }, modifier = Modifier.fillMaxWidth()) { Text("Mieter / Einheit öffnen") }
                    OutlinedButton(onClick = { onSection(PropertySection.FINANCE) }, modifier = Modifier.fillMaxWidth()) { Text("Darlehen öffnen") }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
                Column(Modifier.padding(14.dp), Arrangement.spacedBy(5.dp)) {
                    Text("Wirtschaftlichkeit $year", fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text("Mieteinnahmen Ist: ${NumberFormatter.format(yearRentIncome)}", fontSize = 11.sp)
                    Text("Erfasste Ausgaben: ${NumberFormatter.format(yearExpenses)}", fontSize = 11.sp)
                    Text("Darlehensraten p.a.: ${NumberFormatter.format(annualLoanRates)}", fontSize = 11.sp)
                    Text("Restschuld: ${NumberFormatter.format(restDebt)}", fontSize = 11.sp)
                    Text("Management-Cashflow: ${NumberFormatter.format(managementCashflow)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (managementCashflow >= 0) EmeraldGreen else CrimsonRed)
                    grossYield?.let { Text("Bruttomietrendite auf Sollbasis: ${"%.2f".format(it)} %", fontSize = 11.sp) }
                    Text("Managementansicht aus aktuell erfassten App-Daten. Keine steuerliche Gewinnermittlung; Tilgung wird hier nur als Liquiditätsabfluss über die Darlehensrate berücksichtigt.", fontSize = 9.sp, color = SlateGray)
                }
            }
        }
        val destinations = listOf(
            Triple(PropertySection.UNITS, "Einheiten & Mieter", Icons.Default.Apartment),
            Triple(PropertySection.RENT, "Mieteingänge", Icons.Default.Payments),
            Triple(PropertySection.RENT_MATRIX, "Miet-Jahresübersicht", Icons.Default.CalendarMonth),
            Triple(PropertySection.RECEIPTS, "Belege & Kosten", Icons.Default.Receipt),
            Triple(PropertySection.FINANCE, "Finanzierung", Icons.Default.AccountBalance),
            Triple(PropertySection.RENOVATIONS, "Sanierungen", Icons.Default.Build),
            Triple(PropertySection.DOCUMENTS, "Dokumente", Icons.Default.Description),
            Triple(PropertySection.TASKS, "Aufgaben & Fristen", Icons.Default.CalendarMonth),
            Triple(PropertySection.UTILITIES_PREP, "Nebenkosten", Icons.Default.Payments),
            Triple(PropertySection.TAX, "Steuer & AfA", Icons.Default.Assessment),
            Triple(PropertySection.DATA, "Objektdaten", Icons.Default.HomeWork)
        )
        items(destinations) { (target, label, icon) -> PropertyDestination(label, icon) { onSection(target) } }
    }
}

@Composable private fun PropertyDestination(label: String, icon: ImageVector, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AccentBlue); Text(label, Modifier.weight(1f).padding(start = 12.dp), fontWeight = FontWeight.Bold, color = DarkNavy)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = SlateGray)
        }
    }
}

@Composable
private fun PropertyUnits(
    viewModel: ReceiptViewModel,
    property: PropertyMetadata,
    units: List<WohneinheitStatus>,
    receipts: List<Receipt>,
    documents: List<ManagedDocument>
) {
    var selectedUnitId by remember { mutableStateOf<String?>(null) }
    val selected = units.firstOrNull { PropertyUnitScopedData.stableUnitId(property.propertyId, it) == selectedUnitId }
    if (selected != null) {
        UnitDetailScreen(viewModel, property, selected, receipts, documents) { selectedUnitId = null }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Einheiten & Mietverhältnisse", fontSize = 20.sp, fontWeight = FontWeight.Black, color = DarkNavy) }
        items(units, key = { PropertyUnitScopedData.stableUnitId(property.propertyId, it) }) { unit ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { selectedUnitId = PropertyUnitScopedData.stableUnitId(property.propertyId, unit) },
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), Arrangement.spacedBy(4.dp)) {
                    Text("${unit.name} · ${unit.label}", fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text("Status: ${unit.status}", fontSize = 11.sp, color = if (unit.status == "Vermietet") EmeraldGreen else WarmOrange)
                    Text("Mieter: ${unit.mieter.ifBlank { "–" }}", fontSize = 11.sp)
                    Text("Kaltmiete: ${NumberFormatter.format(unit.kaltmiete)} · Seit: ${unit.mietvertragsstart.ifBlank { "–" }}", fontSize = 10.sp, color = SlateGray)
                }
            }
        }
    }
}

@Composable
private fun UnitDetailScreen(
    viewModel: ReceiptViewModel,
    property: PropertyMetadata,
    unit: WohneinheitStatus,
    receipts: List<Receipt>,
    documents: List<ManagedDocument>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var tab by remember { mutableStateOf(UnitDetailSection.OVERVIEW) }
    var showHistory by remember { mutableStateOf(false) }
    var showStatus by remember { mutableStateOf(false) }
    val unitId = PropertyUnitScopedData.stableUnitId(property.propertyId, unit)
    val unitReceipts = receipts.filter { it.wohneinheit == unit.name }
    val unitDocs = documents.filter { it.unitId == unitId }
    val month = RentTrackingLogic.month(context, property.propertyId, unit, receipts, YearMonth.now())
    val nk = PropertyUnitScopedData.rentValue(context, property.propertyId, unit, "nk")
    val other = PropertyUnitScopedData.rentValue(context, property.propertyId, unit, "other")

    if (showHistory) {
        TenantHistoryDialog(
            unit = unit,
            nebenkostenCurrent = nk,
            sonstigeCurrent = other,
            onDismiss = { showHistory = false },
            onCurrentTenantChanged = { newPeriod ->
                PropertyUnitScopedData.setRentValues(context, property.propertyId, unit, newPeriod.nebenkosten, newPeriod.sonstige)
                viewModel.updateWohneinheit(unit.copy(status = "Vermietet", mieter = newPeriod.tenantName, kaltmiete = newPeriod.kaltmiete, mietvertragsstart = newPeriod.startDate))
            },
            onHistoryChanged = {},
            propertyId = property.propertyId
        )
    }
    if (showStatus) {
        UnitStatusDialog(unit, onDismiss = { showStatus = false }) { status ->
            viewModel.updateWohneinheit(unit.copy(status = status))
            showStatus = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Text(" Einheiten & Mietverhältnisse") }
            Column(Modifier.weight(1f)) {
                Text(unit.label, fontWeight = FontWeight.Bold, color = DarkNavy)
                Text(property.name, fontSize = 9.sp, color = SlateGray)
            }
        }
        val tabs = listOf(
            UnitDetailSection.OVERVIEW to "Übersicht",
            UnitDetailSection.TENANT to "Mieter",
            UnitDetailSection.RENT to "Miete",
            UnitDetailSection.DOCUMENTS to "Dokumente",
            UnitDetailSection.COSTS to "Kosten"
        )
        ScrollableTabRow(selectedTabIndex = tabs.indexOfFirst { it.first == tab }.coerceAtLeast(0)) {
            tabs.forEach { (value, label) -> Tab(selected = tab == value, onClick = { tab = value }, text = { Text(label) }) }
        }
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            when (tab) {
                UnitDetailSection.OVERVIEW -> {
                    item { UnitInfoCard(unit) }
                    item { OutlinedButton(onClick = { showStatus = true }, modifier = Modifier.fillMaxWidth()) { Text("Status ändern") } }
                }
                UnitDetailSection.TENANT -> {
                    item { Text("Aktueller Mieter: ${unit.mieter.ifBlank { "–" }}", fontWeight = FontWeight.Bold) }
                    item { Text("Mietbeginn: ${unit.mietvertragsstart.ifBlank { "–" }}", color = SlateGray) }
                    item { Button(onClick = { showHistory = true }, modifier = Modifier.fillMaxWidth()) { Text("Mieterverlauf / Mieterwechsel") } }
                }
                UnitDetailSection.RENT -> {
                    item { Text("Aktueller Monat", fontWeight = FontWeight.Bold, color = DarkNavy) }
                    item { Text("Soll ${NumberFormatter.format(month.expected)} · Ist ${NumberFormatter.format(month.actual)} · Offen ${NumberFormatter.format(month.missing)}") }
                    item { Text("Kalt ${NumberFormatter.format(unit.kaltmiete)} · NK ${NumberFormatter.format(nk)} · Sonstiges ${NumberFormatter.format(other)}", fontSize = 10.sp, color = SlateGray) }
                }
                UnitDetailSection.DOCUMENTS -> {
                    if (unitDocs.isEmpty()) item { Text("Keine Dokumente dieser Einheit.", color = SlateGray) }
                    items(unitDocs, key = { it.documentId }) { doc ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(doc.title.ifBlank { doc.originalFilename }, fontWeight = FontWeight.Bold)
                                Text(doc.documentType, fontSize = 9.sp, color = SlateGray)
                            }
                        }
                    }
                }
                UnitDetailSection.COSTS -> {
                    if (unitReceipts.isEmpty()) item { Text("Keine Belege dieser Einheit.", color = SlateGray) }
                    items(unitReceipts, key = { it.id }) { receipt ->
                        Text("${receipt.datum} · ${receipt.beschreibung.ifBlank { receipt.aussteller }} · ${NumberFormatter.format(receipt.bruttobetrag)}", Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable private fun UnitInfoCard(unit: WohneinheitStatus) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), Arrangement.spacedBy(5.dp)) {
            Text(unit.label, fontWeight = FontWeight.Bold, color = DarkNavy)
            Text("Status: ${unit.status}")
            Text("Mieter: ${unit.mieter.ifBlank { "–" }}")
            Text("Kaltmiete: ${NumberFormatter.format(unit.kaltmiete)}")
            Text("Wohnfläche: ${unit.wohnflaeche} m²")
            Text("Mietbeginn: ${unit.mietvertragsstart.ifBlank { "–" }}")
        }
    }
}

@Composable private fun UnitStatusDialog(unit: WohneinheitStatus, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val states = listOf("Vermietet", "Kündigung / Auszug geplant", "Leerstand", "Renovierung", "Vermarktung / Inseriert", "Neuvermietung geplant")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Status · ${unit.name}", fontWeight = FontWeight.Bold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { states.forEach { state -> OutlinedButton(onClick = { onSave(state) }, modifier = Modifier.fillMaxWidth()) { Text(state) } } } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun PropertyRentYearMatrix(property: PropertyMetadata, units: List<WohneinheitStatus>, receipts: List<Receipt>) {
    val context = LocalContext.current
    var year by remember { mutableIntStateOf(LocalDate.now().year) }
    val rows = remember(property.propertyId, units, receipts, year) { RentTrackingLogic.year(context, property.propertyId, units, receipts, year) }
    val totalExpected = rows.sumOf { it.expected }
    val totalActual = rows.sumOf { it.actual }
    val totalMissing = rows.sumOf { it.missing }
    val suspicious = rows.sumOf { it.suspiciousMonths }
    val monthNames = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                OutlinedButton(onClick = { year-- }) { Text("‹") }
                Text("Miet-Jahresübersicht $year", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = DarkNavy)
                OutlinedButton(onClick = { year++ }) { Text("›") }
            }
        }
        item { Text("Soll ${NumberFormatter.format(totalExpected)} · Ist ${NumberFormatter.format(totalActual)} · Offen ${NumberFormatter.format(totalMissing)} · $suspicious auffällige Monate", fontSize = 11.sp, color = SlateGray) }
        items(rows, key = { PropertyUnitScopedData.stableUnitId(property.propertyId, it.unit) }) { row ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), Arrangement.spacedBy(6.dp)) {
                    Text(row.unit.label, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.months.forEachIndexed { index, month ->
                            val symbol = when (month.status) {
                                RentPaymentStatus.PAID -> "●"
                                RentPaymentStatus.PARTIAL -> "◐"
                                RentPaymentStatus.MISSING -> "●"
                                RentPaymentStatus.NO_EXPECTATION -> "○"
                            }
                            val color = when (month.status) {
                                RentPaymentStatus.PAID -> EmeraldGreen
                                RentPaymentStatus.PARTIAL -> WarmOrange
                                RentPaymentStatus.MISSING -> CrimsonRed
                                RentPaymentStatus.NO_EXPECTATION -> SlateGray
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(monthNames[index], fontSize = 9.sp, color = SlateGray)
                                Text(symbol, fontSize = 18.sp, color = color)
                            }
                        }
                    }
                    Text("Soll ${NumberFormatter.format(row.expected)} · Ist ${NumberFormatter.format(row.actual)} · Offen ${NumberFormatter.format(row.missing)}", fontSize = 10.sp)
                }
            }
        }
        item { Text("Grün = bezahlt · Gelb = Teilzahlung/prüfen · Rot = offen · Grau = kein Soll", fontSize = 9.sp, color = SlateGray) }
    }
}

@Composable private fun PropertyReceipts(receipts: List<Receipt>) {
    var year by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    val filtered = receipts.filter {
        (year.isBlank() || it.datum.startsWith(year.trim())) &&
            (category.isBlank() || it.hauptkategorie.contains(category.trim(), ignoreCase = true)) &&
            (unit.isBlank() || it.wohneinheit.contains(unit.trim(), ignoreCase = true))
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Belege & Kosten", fontSize = 20.sp, fontWeight = FontWeight.Black, color = DarkNavy)
            Text("Bestehende Belege dieses Objekts – ohne Kopien", fontSize = 10.sp, color = SlateGray)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Jahr") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(category, { category = it }, label = { Text("Kategorie filtern") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(unit, { unit = it }, label = { Text("Einheit filtern") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        }
        items(filtered, key = { it.id }) { receipt ->
            val isIncome = receipt.hauptkategorie in setOf("Miete, Nebenkosten & Kaution", "Sonstige Einnahmen")
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text(receipt.aussteller, fontWeight = FontWeight.Bold); Text("${receipt.datum} · ${receipt.hauptkategorie} · ${receipt.wohneinheit}", fontSize = 10.sp, color = SlateGray) }
                    Text((if (isIncome) "+ " else "− ") + NumberFormatter.format(receipt.bruttobetrag), fontWeight = FontWeight.Bold, color = if (isIncome) EmeraldGreen else CrimsonRed)
                }
            }
        }
    }
}

@Composable private fun PropertyRenovations(receipts: List<Receipt>) {
    val renovations = receipts.filter { it.hauptkategorie.contains("Renovierungs") || it.hauptkategorie.contains("Instandhaltung") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Sanierungen", fontSize = 20.sp, fontWeight = FontWeight.Black, color = DarkNavy); Text("15-%-Bewertung erfolgt unverändert im Steuerbereich.", fontSize = 11.sp, color = SlateGray) }
        items(renovations, key = { it.id }) { Text("${it.datum} · ${it.beschreibung} · ${NumberFormatter.format(it.bruttobetrag)}", Modifier.fillMaxWidth().padding(8.dp)) }
    }
}

@Composable private fun PropertyUtilitiesPreparation() {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Nebenkosten", fontSize = 20.sp, fontWeight = FontWeight.Black, color = DarkNavy) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
                Column(Modifier.padding(14.dp), Arrangement.spacedBy(6.dp)) {
                    Text("Für spätere Erweiterung vorbereitet", fontWeight = FontWeight.Bold)
                    Text("In dieser Phase wird bewusst keine Nebenkostenabrechnung, kein Umlageschlüssel und keine Heizkostenberechnung erzeugt. Bestehende Beleg- und Mietlogik bleibt unverändert.", fontSize = 10.sp, color = SlateGray)
                }
            }
        }
    }
}

@Composable private fun PropertyData(viewModel: ReceiptViewModel, property: PropertyMetadata) {
    var edit by remember { mutableStateOf(false) }
    if (edit) PropertyMetadataFormDialog(viewModel = viewModel, onDismiss = { edit = false })
    val unitCount = property.wohneinheiten
        .split(',')
        .map { it.trim() }
        .count { it.isNotBlank() }
    val allocationSource = when (property.kaufpreisAufteilungQuelle.uppercase()) {
        "MANUELL" -> "Manuell"
        "ABGELEITET" -> "Abgeleitet"
        else -> property.kaufpreisAufteilungQuelle.ifBlank { "Nicht hinterlegt" }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Stammdaten", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DarkNavy) }
        item {
            Card(shape = Ui2.shape, colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
                Column {
                    PropertyCoverImage(property, Modifier.fillMaxWidth().height(190.dp).padding(10.dp))
                    PropertyDataSection("Objekt") {
                        PropertyFactRow("Name", property.name)
                        PropertyFactDivider()
                        PropertyFactRow("Adresse", property.adresse)
                        PropertyFactDivider()
                        PropertyFactRow("Objektart · Status", "${property.objektart} · ${property.status}")
                    }
                    PropertyDataSection("Flächen & Einheiten") {
                        PropertyFactRow("Wohnfläche", "${property.wohnflaeche} m²")
                        PropertyFactDivider()
                        PropertyFactRow("Grundstücksfläche", "${property.grundstuecksgroesse} m²")
                        PropertyFactDivider()
                        PropertyFactRow("Anzahl Einheiten", unitCount.toString())
                    }
                    PropertyDataSection("Kauf & Steuer") {
                        PropertyFactRow("Notarielles Kaufdatum", property.notariellesKaufdatum)
                        PropertyFactDivider()
                        PropertyFactRow("Übergang Nutzen/Lasten", property.uebergangNutzenLasten)
                        PropertyFactDivider()
                        PropertyFactRow("Gesamtkaufpreis", NumberFormatter.format(property.gesamtKaufpreis))
                        PropertyFactDivider()
                        PropertyFactRow("Gebäudewert", NumberFormatter.format(property.gebaeudewert))
                        PropertyFactDivider()
                        PropertyFactRow("Grund und Boden", NumberFormatter.format(property.grundUndBodenWert))
                        PropertyFactDivider()
                        PropertyFactRow("Aufteilungsquelle", allocationSource)
                    }
                    PropertyDataSection("Notizen", showDivider = false) {
                        Text(
                            property.notizen.ifBlank { "Keine Notizen hinterlegt." },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            fontSize = 13.sp,
                            color = if (property.notizen.isBlank()) SlateGray else DarkNavy
                        )
                    }
                }
            }
        }
        item { Button(onClick = { edit = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Stammdaten bearbeiten") } }
    }
}

@Composable private fun PropertyDataSection(title: String, showDivider: Boolean = true, content: @Composable () -> Unit) {
    if (showDivider) HorizontalDivider(color = BorderColor)
    Text(title, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
    content()
}

@Composable private fun PropertyFactDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = BorderColor)
}

@Composable private fun PropertyFactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(0.8f), fontSize = 12.sp, color = SlateGray)
        Text(value, Modifier.weight(1.4f), fontSize = 13.sp, color = DarkNavy)
    }
}

@Composable
fun MoreScreen(viewModel: ReceiptViewModel) {
    var showSettings by remember { mutableStateOf(false) }
    var showDatev by remember { mutableStateOf(false) }
    var showLearnedRules by remember { mutableStateOf(false) }
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val receipts by viewModel.receipts.collectAsState()
    val rules by viewModel.bankLearningRules.collectAsState()
    if (showSettings) AccountSettingsDialog(viewModel = viewModel, onDismiss = { showSettings = false })
    if (showDatev) DatevExportDialog(viewModel, receipts) { showDatev = false }
    if (showLearnedRules) KiLearnedRulesDialog(viewModel) { showLearnedRules = false }
    if (page == "afa" || page == "monitor") {
        PropertyTaxUi2Screen(viewModel, monitor = page == "monitor") { page = null }
        return
    }
    androidx.activity.compose.BackHandler(enabled = page != null) { page = null }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Ui2.padding),
        verticalArrangement = Arrangement.spacedBy(Ui2.spacing)
    ) {
        if (page != null) {
            item { TextButton(onClick = { page = null }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                Text("Zurück zu Mehr")
            } }
            if (page == "backup") item { GoogleDriveSyncCard(viewModel) }
            if (page == "rules") {
                item { Ui2Section("Regeln") {
                    Ui2Destination("Gelerntes KI-Wissen", "Händler-Zuordnungen verwalten", Icons.Default.Settings) { showLearnedRules = true }
                    BankRulesPanel(viewModel, rules)
                } }
            }
        } else {
            item { Ui2Section("Finanzen") {
                Ui2Destination("Bank / Kontoauszüge", "Kontoauszüge importieren und zuordnen", Icons.Default.AccountBalance, AccentBlue) { viewModel.setScreen(AppScreen.BANK) }
                Ui2Destination("Einnahmen / Ausgaben", "Mieteinnahmen und Ausgaben erfassen", Icons.Default.Payments, CrimsonRed) { viewModel.setScreen(AppScreen.LEDGER) }
                Ui2Destination("DATEV Export", "Buchungen für den Steuerberater", Icons.Default.Description, EmeraldGreen) { showDatev = true }
                Ui2Destination("Steuerliche Übersicht", "Wichtige Kennzahlen", Icons.Default.Assessment, WarmOrange) { viewModel.setScreen(AppScreen.TAX_CALCULATOR) }
                Ui2Destination("Mieteingänge", "Soll/Ist & Nebenkosten", Icons.Default.HomeWork) { viewModel.setScreen(AppScreen.RENT_OVERVIEW) }
            } }
            item { Ui2Section("Verwaltung") {
                Ui2Destination("Dokumentenakte", "Dokumente und Volltextsuche", Icons.Default.Description, AccentBlue) { viewModel.setScreen(AppScreen.DOCUMENTS) }
                Ui2Destination("Regeln", "Automatische Zuordnung", Icons.Default.Settings, Color(0xFF7C3AED)) { page = "rules" }
                Ui2Destination("Backup & Cloud", "Sicherung und Wiederherstellung", Icons.Default.Description, EmeraldGreen) { page = "backup" }
            } }
            item { Ui2Section("Objekte & Steuern") {
                Ui2Destination("Immobilien verwalten", "Objekte, Einheiten und Stammdaten", Icons.Default.Apartment, AccentBlue) { viewModel.setScreen(AppScreen.PROPERTIES) }
                Ui2Destination("AfA Gebäude", "Abschreibung berechnen und verwalten", Icons.Default.Assessment, AccentBlue) { page = "afa" }
                Ui2Destination("Sanierungs-Monitor", "Maßnahmen, Kosten und Zeitplan", Icons.Default.Build, EmeraldGreen) { page = "monitor" }
                Ui2Destination("Fahrtenbuch", "Dienst- und Objektfahrten erfassen", Icons.Default.DirectionsCar, AccentBlue) { viewModel.setScreen(AppScreen.LOGBOOK) }
            } }
            item { Ui2Section("Einstellungen") {
                Ui2Destination("App Einstellungen", "KI, Sicherung, Belege und persönliche Angaben", Icons.Default.Settings) { showSettings = true }
            } }
            item {
                Card(
                    shape = Ui2.shape,
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3FF)),
                    border = BorderStroke(1.dp, Color(0xFFD8E9FF))
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Lightbulb, null, tint = AccentBlue)
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Tipp", fontWeight = FontWeight.Bold, color = DarkNavy)
                            Text(
                                "Alle wichtigen Funktionen an einem Ort – für eine einfache und effiziente Verwaltung deiner Immobilien.",
                                fontSize = 12.sp,
                                color = SlateGray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PropertyCreationWizard(onDismiss: () -> Unit, onSave: (PropertyMetadata, List<WohneinheitStatus>, Loan?) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val generatedPropertyId = remember { java.util.UUID.randomUUID().toString() }
    var name by remember { mutableStateOf("") }; var street by remember { mutableStateOf("") }; var zip by remember { mutableStateOf("") }; var city by remember { mutableStateOf("") }
    var objectType by remember { mutableStateOf("Mehrfamilienhaus") }
    var purchaseDate by remember { mutableStateOf("") }; var purchasePrice by remember { mutableStateOf("") }; var yearBuilt by remember { mutableStateOf("") }
    var livingArea by remember { mutableStateOf("") }; var landArea by remember { mutableStateOf("") }; var unitCount by remember { mutableStateOf("1") }
    var buildingValue by remember { mutableStateOf("") }; var landValue by remember { mutableStateOf("") }
    var loanName by remember { mutableStateOf("") }; var loanBank by remember { mutableStateOf("") }; var loanAmount by remember { mutableStateOf("") }
    val unitNames = remember { mutableStateListOf<String>().apply { repeat(20) { add("WE ${(it + 1).toString().padStart(2, '0')}") } } }
    val unitLocations = remember { mutableStateListOf<String>().apply { repeat(20) { add("") } } }
    val unitAreas = remember { mutableStateListOf<String>().apply { repeat(20) { add("") } } }
    val field: @Composable (String, String, (String) -> Unit) -> Unit = { value, label, change -> OutlinedTextField(value, change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Immobilie anlegen · ${step + 1}/5", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (step) {
                    0 -> {
                        field(name, "Objektname") { name = it }
                        Text("Objektart", fontSize = 12.sp, color = SlateGray)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Eigentumswohnung", "Einfamilienhaus", "Mehrfamilienhaus", "Gewerbeeinheit", "Grundstück").forEach { type ->
                                FilterChip(
                                    selected = objectType == type,
                                    onClick = {
                                        objectType = type
                                        if (type == "Eigentumswohnung") unitCount = "1"
                                    },
                                    label = { Text(type, fontSize = 11.sp) }
                                )
                            }
                        }
                        field(street, "Straße und Hausnummer") { street = it }
                        field(zip, "PLZ") { zip = it }
                        field(city, "Ort") { city = it }
                        field(purchaseDate, "Kaufdatum YYYY-MM-DD") { purchaseDate = it }
                        field(purchasePrice, "Kaufpreis €") { purchasePrice = it }
                    }
                    1 -> { field(yearBuilt, "Baujahr") { yearBuilt = it }; field(livingArea, "Wohnfläche m²") { livingArea = it }; field(landArea, "Grundstücksfläche m²") { landArea = it }; field(unitCount, "Anzahl Einheiten (max. 20)") { unitCount = it.filter(Char::isDigit).take(2) } }
                    2 -> { Text("Finanzierung (optional)", fontWeight = FontWeight.Bold); field(loanName, "Darlehensbezeichnung") { loanName = it }; field(loanBank, "Bank") { loanBank = it }; field(loanAmount, "Darlehensbetrag €") { loanAmount = it } }
                    3 -> LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((0 until (unitCount.toIntOrNull() ?: 1).coerceIn(1, 20)).toList()) { index ->
                            Text("Einheit ${index + 1}", fontWeight = FontWeight.Bold)
                            field(unitNames[index], "Name") { unitNames[index] = it }
                            field(unitLocations[index], "Lage / Bezeichnung") { unitLocations[index] = it }
                            field(unitAreas[index], "Wohnfläche m²") { unitAreas[index] = it }
                        }
                    }
                    else -> { field(buildingValue, "Gebäudeanteil € (optional)") { buildingValue = it }; field(landValue, "Grund und Boden € (optional)") { landValue = it }; Text("AfA und 15-%-Prüfung verwenden danach unverändert die bestehende Steuerlogik.", fontSize = 10.sp, color = SlateGray) }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (step < 4) step++ else {
                    val count = (unitCount.toIntOrNull() ?: 1).coerceIn(1, 20)
                    val savedUnitNames = (0 until count).map { unitNames[it].ifBlank { "WE ${(it + 1).toString().padStart(2, '0')}" } }
                    val metadata = PropertyMetadata(
                        propertyId = generatedPropertyId, name = name.ifBlank { street.ifBlank { "Neue Immobilie" } }, objektart = objectType,
                        adresse = listOf(street, "$zip $city".trim()).filter(String::isNotBlank).joinToString(", "),
                        baujahr = yearBuilt.toIntOrNull() ?: 0, wohnflaeche = livingArea.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        grundstuecksgroesse = landArea.replace(',', '.').toDoubleOrNull() ?: 0.0, notariellesKaufdatum = purchaseDate,
                        wohneinheiten = savedUnitNames.joinToString(", "), gesamtKaufpreis = purchasePrice.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        gebaeudewert = buildingValue.replace(',', '.').toDoubleOrNull() ?: 0.0, grundUndBodenWert = landValue.replace(',', '.').toDoubleOrNull() ?: 0.0
                    )
                    val units = savedUnitNames.mapIndexed { index, unitName ->
                        WohneinheitStatus(unitName, unitLocations[index].ifBlank { unitName }, "Leerstand", "", 0.0, unitAreas[index].replace(',', '.').toDoubleOrNull() ?: 0.0)
                    }
                    val loan = loanAmount.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }?.let {
                        Loan(bezeichnung = loanName.ifBlank { "Darlehen ${metadata.name}" }, bank = loanBank, darlehensbetrag = it, restschuld = it)
                    }
                    onSave(metadata, units, loan)
                }
            }, enabled = step > 0 || name.isNotBlank(), modifier = Modifier.testTag("property_wizard_next")) { Text(if (step == 4) "Immobilie anlegen" else "Weiter") }
        },
        dismissButton = { Row { if (step > 0) TextButton(onClick = { step-- }) { Text("Zurück") }; TextButton(onClick = onDismiss) { Text("Abbrechen") } } }
    )
}

