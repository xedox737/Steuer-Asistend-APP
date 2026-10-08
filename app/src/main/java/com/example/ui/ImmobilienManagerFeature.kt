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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    fun summary(
        context: android.content.Context,
        property: PropertyMetadata,
        units: List<WohneinheitStatus>,
        receipts: List<Receipt>,
        bankAssignments: List<com.example.data.BankRentAssignment>,
        bankLinks: List<com.example.data.BankReceiptLink>,
        bankTransactions: List<com.example.data.BankTransaction>,
        month: YearMonth = YearMonth.now()
    ): PropertyManagerSummary {
        val rows = units.map { unit ->
            RentTrackingLogic.month(
                context, property.propertyId, unit, receipts, month,
                bankAssignments, bankLinks, bankTransactions
            )
        }
        return PropertyManagerSummary(
            unitCount = units.size,
            rentedCount = units.count { it.status == "Vermietet" },
            vacantCount = units.count { it.status != "Vermietet" },
            expectedRent = rows.sumOf { it.expected },
            actualRent = rows.sumOf { it.actual }
        )
    }
}

private enum class PropertySection { DASHBOARD, UNITS, RENT, RENT_MATRIX, RECEIPTS, FINANCE, RENOVATIONS, DOCUMENTS, TAX, TASKS, UTILITIES_PREP, DATA }
private enum class UnitDetailSection { OVERVIEW, TENANT, RENT, DOCUMENTS, COSTS }

@Composable
fun ImmobilienManagerScreen(viewModel: ReceiptViewModel) {
    val context = LocalContext.current
    val properties by viewModel.properties.collectAsStateWithLifecycle()
    val allReceipts by viewModel.receipts.collectAsStateWithLifecycle()
    val selected by viewModel.propertyMetadata.collectAsStateWithLifecycle()
    val bankAssignments by viewModel.bankRentAssignments.collectAsStateWithLifecycle()
    val bankLinks by viewModel.bankReceiptLinks.collectAsStateWithLifecycle()
    val bankTransactions by viewModel.bankTransactions.collectAsStateWithLifecycle()
    val visibleProperties = remember(properties) { properties.filterNot { it.status == "Archiviert" } }
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
                PropertyOverviewCard(
                    property,
                    ImmobilienManagerProjection.summary(
                        context, property, units, propertyReceipts,
                        bankAssignments, bankLinks, bankTransactions
                    )
                ) {
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
        PropertyDetailHost(
            viewModel, opened, section,
            bankAssignments, bankLinks, bankTransactions,
            { section = it }
        ) { openedPropertyId = null }
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
                VerticalDivider(Modifier.height(52.dp), color = BorderColor)
                PropertyOverviewMetric("Ist", NumberFormatter.format(summary.actualRent), EmeraldGreen, Modifier.weight(1f).padding(horizontal = 12.dp))
                VerticalDivider(Modifier.height(52.dp), color = BorderColor)
                PropertyOverviewMetric("Offen", NumberFormatter.format(summary.outstandingRent), if (summary.outstandingRent > 0) CrimsonRed else EmeraldGreen, Modifier.weight(1f).padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun PropertyOverviewMetric(label: String, value: String, valueColor: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text(label, fontSize = 11.sp, color = SlateGray)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor, maxLines = 1)
    }
}

@Composable
private fun PropertyDetailHost(
    viewModel: ReceiptViewModel,
    property: PropertyMetadata,
    section: PropertySection,
    bankAssignments: List<com.example.data.BankRentAssignment>,
    bankLinks: List<com.example.data.BankReceiptLink>,
    bankTransactions: List<com.example.data.BankTransaction>,
    onSection: (PropertySection) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val receipts by viewModel.receipts.collectAsStateWithLifecycle()
    val documents by viewModel.managedDocuments.collectAsStateWithLifecycle()
    val loans by viewModel.loans.collectAsStateWithLifecycle()
    val units by viewModel.wohneinheitenStatus.collectAsStateWithLifecycle()
    val propertyReceipts = ImmobilienManagerProjection.receipts(property, units, receipts)
    val propertyDocuments = ImmobilienManagerProjection.documents(property, documents)
    val propertyLoans = ImmobilienManagerProjection.loans(property, loans)
    val currentRentSummary = remember(
        property.propertyId, units, propertyReceipts,
        bankAssignments, bankLinks, bankTransactions
    ) {
        ImmobilienManagerProjection.summary(
            context, property, units, propertyReceipts,
            bankAssignments, bankLinks, bankTransactions
        )
    }
    var deleteRequested by remember(property.propertyId) { mutableStateOf(false) }
    BackHandler { if (section == PropertySection.DASHBOARD) onBack() else onSection(PropertySection.DASHBOARD) }
    Column(Modifier.fillMaxSize()) {
        if (section != PropertySection.UNITS && section != PropertySection.DOCUMENTS) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (section == PropertySection.DASHBOARD) onBack() else onSection(PropertySection.DASHBOARD) }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = DarkNavy)
                }
                Text(if (section == PropertySection.DASHBOARD) "Immobilie" else property.name, Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                if (section == PropertySection.DASHBOARD) {
                    IconButton(onClick = { deleteRequested = true }) {
                        Icon(Icons.Default.Delete, "Immobilie löschen", tint = CrimsonRed)
                    }
                }
            }
        }
        when (section) {
            PropertySection.DASHBOARD -> PropertyReferenceDetail(property, currentRentSummary, viewModel, onSection)
            PropertySection.UNITS -> UnifiedPropertyUnitsScreen(viewModel, property, units, propertyReceipts, propertyDocuments, onBackToProperty = { onSection(PropertySection.DASHBOARD) })
            PropertySection.RENT -> RentIncomeWithTenantHistoryScreen(viewModel, propertyScoped = true)
            PropertySection.RENT_MATRIX -> PropertyRentYearMatrix(property, units, propertyReceipts)
            PropertySection.RECEIPTS -> PropertyReceipts(propertyReceipts)
            PropertySection.FINANCE -> LazyColumn(Modifier.fillMaxSize().padding(16.dp)) { item { LoanManagementSection(viewModel, propertyScoped = true) } }
            PropertySection.RENOVATIONS -> PropertyRenovations(propertyReceipts)
            PropertySection.DOCUMENTS -> DocumentManagementScreen(viewModel, propertyScoped = true, onBack = { onSection(PropertySection.DASHBOARD) })
            PropertySection.TAX -> AnnualTaxAssistantScreen(viewModel)
            PropertySection.TASKS -> PropertyTasksScreen(property.propertyId, units)
            PropertySection.UTILITIES_PREP -> PropertyUtilitiesPreparation()
            PropertySection.DATA -> PropertyData(viewModel, property)
        }
    }
    if (deleteRequested) AlertDialog(
        onDismissRequest = { deleteRequested = false },
        title = { Text("Immobilie archivieren?", fontWeight = FontWeight.Bold) },
        text = { Text("Das Objekt wird aus der aktiven Übersicht entfernt. Belege, Unterlagen, Buchungen und die stabile Objekt-ID bleiben vollständig erhalten.") },
        confirmButton = { Button(onClick = { viewModel.deleteProperty(property); deleteRequested = false; onBack() }, colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)) { Text("Archivieren") } },
        dismissButton = { TextButton(onClick = { deleteRequested = false }) { Text("Abbrechen") } }
    )
}

@Composable
private fun PropertyReferenceDetail(property: PropertyMetadata, summary: PropertyManagerSummary, viewModel: ReceiptViewModel, onSection: (PropertySection) -> Unit) {
    val entries = listOf(
        PropertySection.DATA to ("Stammdaten" to Icons.Default.HomeWork),
        PropertySection.UNITS to ("Einheiten" to Icons.Default.Apartment),
        PropertySection.RENT to ("Mieteingänge" to Icons.Default.Payments),
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
    var year by remember(property) { mutableStateOf(property.baujahr.toString()) }
    var area by remember(property) { mutableStateOf(GermanNumberInput.formatForInput(property.wohnflaeche)) }
    var land by remember(property) { mutableStateOf(GermanNumberInput.formatForInput(property.grundstuecksgroesse)) }
    var notes by remember(property) { mutableStateOf(property.notizen) }
    var areaError by remember(property) { mutableStateOf<String?>(null) }
    var landError by remember(property) { mutableStateOf<String?>(null) }
    var yearError by remember(property) { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = DarkNavy) }; Text("Immobilie bearbeiten", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            TextButton(onClick = {
                val parsedYear = if (year.isBlank()) property.baujahr else year.toIntOrNull()?.takeIf { it >= 0 }
                val parsedArea = if (area.isBlank()) property.wohnflaeche else GermanNumberInput.parseNonNegative(area)
                val parsedLand = if (land.isBlank()) property.grundstuecksgroesse else GermanNumberInput.parseNonNegative(land)
                yearError = if (parsedYear == null) "Bitte ein gültiges Baujahr eingeben." else null
                areaError = if (parsedArea == null) "Die Fläche darf nicht negativ oder ungültig sein." else null
                landError = if (parsedLand == null) "Die Fläche darf nicht negativ oder ungültig sein." else null
                if (yearError != null || areaError != null || landError != null) return@TextButton
                viewModel.updatePropertyMetadata(
                    property.copy(
                        name = name,
                        adresse = address,
                        objektart = type,
                        baujahr = parsedYear!!,
                        wohnflaeche = parsedArea!!,
                        grundstuecksgroesse = parsedLand!!,
                        notizen = notes
                    )
                )
                onBack()
            }) { Text("Speichern", fontSize = 14.sp, color = AccentBlue) }
        }
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Box { PropertyCoverImage(property, Modifier.fillMaxWidth().height(150.dp)); PropertyImagePicker(property, viewModel, Modifier.align(Alignment.BottomEnd).padding(8.dp)) } }
            item { PropertyEditField(name, "Name *") { name = it } }
            item { PropertyEditField(address, "Adresse *") { address = it } }
            item { PropertyEditField(type, "Objektart *") { type = it } }
            item { PropertyEditField(year, "Baujahr", error = yearError) { yearError = null; year = it.filter(Char::isDigit) } }
            item { PropertyEditField(area, "Wohnfläche (m²)", error = areaError) { areaError = null; area = it } }
            item { PropertyEditField(land, "Grundstücksfläche (m²)", error = landError) { landError = null; land = it } }
            item { PropertyEditField(notes, "Beschreibung / Notizen", false) { notes = it } }
        }
    }
}

@Composable private fun PropertyEditField(
    value: String,
    label: String,
    singleLine: Boolean = true,
    error: String? = null,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine
    )
}

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
    documents: List<ManagedDocument>,
    onBackToProperty: () -> Unit
) {
    var selectedUnitId by remember { mutableStateOf<String?>(null) }
    val selected = units.firstOrNull { PropertyUnitScopedData.stableUnitId(property.propertyId, it) == selectedUnitId }
    if (selected != null) {
        UnitDetailScreen(viewModel, property, selected, receipts, documents) { selectedUnitId = null }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackToProperty) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = DarkNavy) }
                Text("Einheiten & Mietverhältnisse", fontSize = 20.sp, fontWeight = FontWeight.Black, color = DarkNavy)
            }
        }
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
    val bankAssignments by viewModel.bankRentAssignments.collectAsStateWithLifecycle()
    val bankLinks by viewModel.bankReceiptLinks.collectAsStateWithLifecycle()
    val bankTransactions by viewModel.bankTransactions.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(UnitDetailSection.OVERVIEW) }
    var showHistory by remember { mutableStateOf(false) }
    var showStatus by remember { mutableStateOf(false) }
    val unitId = PropertyUnitScopedData.stableUnitId(property.propertyId, unit)
    val unitReceipts = receipts.filter { receipt -> receipt.unitId == unitId || (receipt.unitId.isBlank() && (receipt.wohneinheit.equals(unit.name, true) || receipt.wohneinheit.equals(unit.label, true))) }
    val unitDocs = documents.filter { it.unitId == unitId }
    val month = RentTrackingLogic.month(
        context, property.propertyId, unit, receipts, YearMonth.now(),
        bankAssignments, bankLinks, bankTransactions
    )
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
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = DarkNavy) }
            Text(unit.name, modifier = Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DarkNavy, maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { showStatus = true }) { Icon(Icons.Default.Settings, "Optionen", tint = DarkNavy) }
        }
        UnitContextCard(property = property, unit = unit, onStatusClick = { showStatus = true })
        val tabs = listOf(
            UnitDetailSection.OVERVIEW to "Übersicht",
            UnitDetailSection.TENANT to "Mieter",
            UnitDetailSection.RENT to "Miete",
            UnitDetailSection.DOCUMENTS to "Dokumente"
        )
        Row(Modifier.fillMaxWidth()) {
            tabs.forEach { (value, label) ->
                Tab(
                    selected = tab == value,
                    onClick = { tab = value },
                    modifier = Modifier.weight(1f),
                    text = { Text(label, maxLines = 1, fontSize = 11.sp) }
                )
            }
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
                UnitDetailSection.COSTS -> Unit
            }
        }
    }
}

@Composable
private fun UnitContextCard(property: PropertyMetadata, unit: WohneinheitStatus, onStatusClick: () -> Unit) {
    val addressParts = property.adresse.split(',').map { it.trim() }.filter { it.isNotBlank() }
    val street = addressParts.firstOrNull().orEmpty()
    val postalCodeAndCity = addressParts.drop(1).joinToString(", ")
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = Ui2.shape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3FF))) {
                Icon(Icons.Default.Apartment, null, Modifier.padding(12.dp).size(28.dp), tint = AccentBlue)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(unit.label.ifBlank { unit.name }, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkNavy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(property.name, fontSize = 12.sp, color = SlateGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(street, fontSize = 11.sp, color = SlateGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (postalCodeAndCity.isNotBlank()) Text(postalCodeAndCity, fontSize = 11.sp, color = SlateGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onStatusClick,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                border = BorderStroke(1.dp, AccentBlue)
            ) { Text(unit.status, fontSize = 10.sp, color = AccentBlue, maxLines = 1) }
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
    var showLearnedRules by remember { mutableStateOf(false) }
    var page by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val receipts by viewModel.receipts.collectAsStateWithLifecycle()
    val rules by viewModel.bankLearningRules.collectAsStateWithLifecycle()
    when (page) {
        "settings" -> {
            AppSettingsScreen(viewModel) { page = null }
            return
        }
        "help" -> {
            HelpFaqScreen { page = null }
            return
        }
        "about" -> {
            AboutImmoPilotScreen { page = null }
            return
        }
    }
    if (showLearnedRules) KiLearnedRulesDialog(viewModel) { showLearnedRules = false }
    if (page == "afa" || page == "monitor") {
        if (page == "afa") AfaPortfolioScreen(viewModel) { page = null }
        else PropertyTaxUi2Screen(viewModel, monitor = true) { page = null }
        return
    }
    androidx.activity.compose.BackHandler(enabled = page != null) { page = null }
    LazyColumn(
        Modifier.fillMaxSize().testTag("more_menu"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
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
            // The More screen deliberately uses compact destination rows.  It mirrors the
            // reference navigation pattern while keeping every existing destination intact.
            item { MoreMenuGroup("Finanzen") {
                MoreMenuItem("Bank & Kontoauszüge", Icons.Default.AccountBalance, AccentBlue) { viewModel.setScreen(AppScreen.BANK) }
                MoreMenuItem("Einnahmen & Ausgaben", Icons.Default.Payments, CrimsonRed) { viewModel.setScreen(AppScreen.LEDGER) }
                MoreMenuItem("Mieteingänge", Icons.Default.HomeWork, AccentBlue) { viewModel.setScreen(AppScreen.RENT_OVERVIEW) }
            } }
            item { MoreMenuGroup("Steuern & Auswertung") {
                MoreMenuItem("Steuerliche Übersicht", Icons.Default.Assessment, WarmOrange) { viewModel.setScreen(AppScreen.TAX_CALCULATOR) }
                MoreMenuItem("AfA Gebäude", Icons.Default.Assessment, AccentBlue) { page = "afa" }
                MoreMenuItem("Sanierungs-Monitor", Icons.Default.Build, EmeraldGreen) { page = "monitor" }
                MoreMenuItem("DATEV Export", Icons.Default.Description, EmeraldGreen) { viewModel.openDatevExport() }
            } }
            item { MoreMenuGroup("Verwaltung") {
                MoreMenuItem("Dokumentenakte", Icons.Default.Description, AccentBlue) { viewModel.setScreen(AppScreen.DOCUMENTS) }
                MoreMenuItem("Fahrtenbuch", Icons.Default.DirectionsCar, AccentBlue) { viewModel.setScreen(AppScreen.LOGBOOK) }
            } }
            item { MoreMenuGroup("KI & Automatisierung") {
                MoreMenuItem("Gelernte Regeln", Icons.Default.Settings, Color(0xFF7C3AED)) { page = "rules" }
            } }
            item { MoreMenuGroup("Daten & Sicherung") {
                MoreMenuItem("Backup & Cloud", Icons.Default.Cloud, EmeraldGreen) { page = "backup" }
            } }
            item { MoreMenuGroup("Hilfe & Info") {
                MoreMenuItem("Hilfe & FAQ", Icons.Default.HelpOutline, AccentBlue) { page = "help" }
                MoreMenuItem("Über ImmoPilot", Icons.Default.Info, SlateGray) { page = "about" }
            } }
            item { MoreMenuGroup("Einstellungen") {
                MoreMenuItem("App-Einstellungen", Icons.Default.Settings, SlateGray) { page = "settings" }
            } }
        }
    }
}

/** Compact navigation used only on the More screen. */
@Composable
private fun MoreMenuGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("more_group_$title"),
        shape = Ui2.shape,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 4.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = DarkNavy
            )
            content()
        }
    }
}

@Composable
private fun MoreMenuItem(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp).testTag("more_item_$title"),
        shape = Ui2.controlShape,
        color = Color(0xFFF7F9FC)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp).testTag("more_icon_$title"))
            Text(title, modifier = Modifier.weight(1f), fontSize = 13.sp, color = DarkNavy)
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = SlateGray,
                modifier = Modifier.size(18.dp)
            )
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
    val unitNames = remember { mutableStateListOf<String>().apply { add("WE 01") } }
    val unitLocations = remember { mutableStateListOf<String>().apply { add("") } }
    val unitAreas = remember { mutableStateListOf<String>().apply { add("") } }
    var fieldErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var wizardMessage by remember { mutableStateOf<String?>(null) }
    val field: @Composable (String, String, (String) -> Unit) -> Unit = { value, label, change ->
        val message = fieldErrors[label]
        OutlinedTextField(
            value = value,
            onValueChange = {
                fieldErrors = fieldErrors - label
                wizardMessage = null
                change(it)
            },
            label = { Text(label) },
            isError = message != null,
            supportingText = { message?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
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
                    1 -> { field(yearBuilt, "Baujahr") { yearBuilt = it }; field(livingArea, "Wohnfläche m²") { livingArea = it }; field(landArea, "Grundstücksfläche m²") { landArea = it }; field(unitCount, "Anzahl Einheiten") { raw ->
                            val digits = raw.filter(Char::isDigit).take(4)
                            unitCount = digits
                            val requested = digits.toIntOrNull() ?: 0
                            while (unitNames.size < requested) {
                                unitNames.add("WE ${(unitNames.size + 1).toString().padStart(2, '0')}")
                                unitLocations.add("")
                                unitAreas.add("")
                            }
                        } }
                    2 -> { Text("Finanzierung (optional)", fontWeight = FontWeight.Bold); field(loanName, "Darlehensbezeichnung") { loanName = it }; field(loanBank, "Bank") { loanBank = it }; field(loanAmount, "Darlehensbetrag €") { loanAmount = it } }
                    3 -> LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((0 until (unitCount.toIntOrNull() ?: 1).coerceAtLeast(1)).toList()) { index ->
                            Text("Einheit ${index + 1}", fontWeight = FontWeight.Bold)
                            field(unitNames[index], "Name") { unitNames[index] = it }
                            field(unitLocations[index], "Lage / Bezeichnung") { unitLocations[index] = it }
                            field(unitAreas[index], "Wohnfläche m²") { unitAreas[index] = it }
                            if ((unitCount.toIntOrNull() ?: 1) > 1) {
                                TextButton(onClick = {
                                    unitNames.removeAt(index)
                                    unitLocations.removeAt(index)
                                    unitAreas.removeAt(index)
                                    unitCount = unitNames.size.toString()
                                }) { Text("Einheit entfernen") }
                            }
                        }
                        item {
                            TextButton(onClick = {
                                unitNames.add("WE ${(unitNames.size + 1).toString().padStart(2, '0')}")
                                unitLocations.add("")
                                unitAreas.add("")
                                unitCount = unitNames.size.toString()
                            }) { Text("+ Einheit hinzufügen") }
                        }
                    }
                    else -> { field(buildingValue, "Gebäudeanteil € (optional)") { buildingValue = it }; field(landValue, "Grund und Boden € (optional)") { landValue = it }; Text("AfA und 15-%-Prüfung verwenden danach unverändert die bestehende Steuerlogik.", fontSize = 10.sp, color = SlateGray) }
                }
                wizardMessage?.let { Text(it, color = CrimsonRed, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val validation = PropertyWizardInput.validate(
                    purchasePrice = purchasePrice,
                    livingArea = livingArea,
                    landArea = landArea,
                    purchaseDate = purchaseDate,
                    yearBuilt = yearBuilt,
                    buildingValue = buildingValue,
                    landValue = landValue,
                    loanAmount = loanAmount
                )
                val relevantFields = when (step) {
                    0 -> setOf(PropertyWizardInput.PURCHASE_PRICE, PropertyWizardInput.PURCHASE_DATE)
                    1 -> setOf(PropertyWizardInput.LIVING_AREA, PropertyWizardInput.LAND_AREA, PropertyWizardInput.YEAR_BUILT)
                    2 -> setOf(PropertyWizardInput.LOAN_AMOUNT)
                    4 -> validation.errors.keys
                    else -> emptySet()
                }
                val currentErrors = validation.errors.filterKeys { it in relevantFields }
                val count = unitCount.toIntOrNull() ?: 0
                if (count < 1) {
                    wizardMessage = "Bitte mindestens eine Wohneinheit angeben."
                    return@Button
                }
                val invalidUnitName = if (step == 3 || step == 4) {
                    (0 until count).firstOrNull { unitNames[it].isBlank() }
                } else null
                val duplicateUnitName = if (step == 3 || step == 4) {
                    (0 until count).groupBy { unitNames[it].trim().lowercase() }.values.firstOrNull { it.size > 1 }?.firstOrNull()
                } else null
                val invalidUnitArea = if (step == 3 || step == 4) {
                    (0 until count).firstOrNull { PropertyWizardInput.unitArea(unitAreas[it]) == null }
                } else null

                if (currentErrors.isNotEmpty() || invalidUnitArea != null || invalidUnitName != null || duplicateUnitName != null) {
                    fieldErrors = currentErrors
                    wizardMessage = if (invalidUnitName != null) {
                        "Bitte für Einheit ${invalidUnitName + 1} einen Namen eingeben."
                    } else if (duplicateUnitName != null) {
                        "Bitte eindeutige Einheitsnamen vergeben."
                    } else if (invalidUnitArea != null) {
                        "Bitte für Einheit ${invalidUnitArea + 1} eine gültige, nicht negative Wohnfläche eingeben."
                    } else {
                        "Bitte die markierten Eingaben prüfen."
                    }
                    return@Button
                }

                if (step < 4) {
                    fieldErrors = emptyMap()
                    wizardMessage = null
                    step++
                } else {
                    val values = validation.values ?: run {
                        fieldErrors = validation.errors
                        wizardMessage = "Bitte die markierten Eingaben prüfen."
                        return@Button
                    }
                    val savedUnitNames = (0 until count).map {
                        unitNames[it].ifBlank { "WE ${(it + 1).toString().padStart(2, '0')}" }
                    }
                    val metadata = PropertyMetadata(
                        propertyId = generatedPropertyId,
                        name = name.ifBlank { street.ifBlank { "Neue Immobilie" } },
                        objektart = objectType,
                        adresse = listOf(street, "$zip $city".trim()).filter(String::isNotBlank).joinToString(", "),
                        baujahr = values.yearBuilt,
                        wohnflaeche = values.livingArea,
                        grundstuecksgroesse = values.landArea,
                        notariellesKaufdatum = purchaseDate.trim(),
                        wohneinheiten = savedUnitNames.joinToString(", "),
                        gesamtKaufpreis = values.purchasePrice,
                        gebaeudewert = values.buildingValue,
                        grundUndBodenWert = values.landValue
                    )
                    val units = savedUnitNames.mapIndexed { index, unitName ->
                        WohneinheitStatus(
                            unitName,
                            unitLocations[index].ifBlank { unitName },
                            "Leerstand",
                            "",
                            0.0,
                            PropertyWizardInput.unitArea(unitAreas[index]) ?: 0.0,
                            unitId = java.util.UUID.randomUUID().toString()
                        )
                    }
                    val loan = values.loanAmount?.takeIf { it > 0.0 }?.let {
                        Loan(
                            bezeichnung = loanName.ifBlank { "Darlehen ${metadata.name}" },
                            bank = loanBank,
                            darlehensbetrag = it,
                            restschuld = it
                        )
                    }
                    onSave(metadata, units, loan)
                }
            }, enabled = step > 0 || name.isNotBlank(), modifier = Modifier.testTag("property_wizard_next")) {
                Text(if (step == 4) "Immobilie anlegen" else "Weiter")
            }
        },
        dismissButton = { Row { if (step > 0) TextButton(onClick = { step-- }) { Text("Zurück") }; TextButton(onClick = onDismiss) { Text("Abbrechen") } } }
    )
}

