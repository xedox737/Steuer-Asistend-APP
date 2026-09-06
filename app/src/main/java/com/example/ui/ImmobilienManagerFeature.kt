package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ManagedDocument
import com.example.data.Loan
import com.example.data.PropertyMetadata
import com.example.data.Receipt
import java.time.YearMonth

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
        val unitNames = units.map { it.name }.toSet()
        return all.filter {
            it.propertyId == property.propertyId ||
                (it.propertyId == com.example.data.StableDocumentIdentity.LEGACY_PROPERTY_ID && it.wohneinheit in unitNames)
        }
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
            it.datum.startsWith(month.toString()) &&
                isRentalIncomeReceipt(it)
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

private enum class PropertySection { DASHBOARD, UNITS, RENT, RECEIPTS, FINANCE, RENOVATIONS, DOCUMENTS, TAX, DATA }

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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("Meine Immobilien", fontSize = 22.sp, fontWeight = FontWeight.Black, color = DarkNavy)
                        Text("Objekte, Einheiten und Mieten im Überblick", fontSize = 11.sp, color = SlateGray)
                    }
                    Button(onClick = { showWizard = true }, modifier = Modifier.testTag("add_property_button")) {
                        Icon(Icons.Default.Add, null); Text(" Immobilie")
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
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("property_card_${property.propertyId}"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(16.dp), Arrangement.spacedBy(7.dp)) {
            Text(property.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            Text(property.adresse, fontSize = 11.sp, color = SlateGray)
            HorizontalDivider(color = BorderColor)
            Text("${summary.unitCount} Einheiten · ${summary.rentedCount} vermietet · ${summary.vacantCount} frei/prüfen", fontSize = 11.sp, color = DarkNavy)
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Soll ${NumberFormatter.format(summary.expectedRent)}", fontSize = 11.sp, color = SlateGray)
                Text("Ist ${NumberFormatter.format(summary.actualRent)}", fontSize = 11.sp, color = EmeraldGreen)
            }
            Text("Offen ${NumberFormatter.format(summary.outstandingRent)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (summary.outstandingRent > 0) CrimsonRed else EmeraldGreen)
        }
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
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (section == PropertySection.DASHBOARD) onBack() else onSection(PropertySection.DASHBOARD) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Text(" Zurück")
            }
            Column(Modifier.weight(1f)) {
                Text(property.name, fontWeight = FontWeight.Bold, color = DarkNavy)
                Text(property.adresse, fontSize = 10.sp, color = SlateGray)
            }
        }
        when (section) {
            PropertySection.DASHBOARD -> PropertyDashboard(
                property, units, propertyReceipts, propertyDocuments,
                ImmobilienManagerProjection.loans(property, loans).count { it.aktiv }, onSection
            )
            PropertySection.UNITS -> PropertyUnits(units)
            PropertySection.RENT -> RentIncomeWithTenantHistoryScreen(viewModel, propertyScoped = true)
            PropertySection.RECEIPTS -> PropertyReceipts(propertyReceipts)
            PropertySection.FINANCE -> LazyColumn(Modifier.fillMaxSize().padding(16.dp)) { item { LoanManagementSection(viewModel, propertyScoped = true) } }
            PropertySection.RENOVATIONS -> PropertyRenovations(propertyReceipts)
            PropertySection.DOCUMENTS -> DocumentManagementScreen(viewModel, propertyScoped = true)
            PropertySection.TAX -> AnnualTaxAssistantScreen(viewModel)
            PropertySection.DATA -> PropertyData(viewModel, property)
        }
    }
}

@Composable
private fun PropertyDashboard(property: PropertyMetadata, units: List<WohneinheitStatus>, receipts: List<Receipt>, documents: List<ManagedDocument>, activeLoanCount: Int, onSection: (PropertySection) -> Unit) {
    val summary = ImmobilienManagerProjection.summary(units, receipts)
    val unchecked = receipts.count { it.pruefstatus == "UNGEPRUEFT" || it.exportStatus == "ZU_PRUEFEN" }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
                Column(Modifier.padding(16.dp), Arrangement.spacedBy(8.dp)) {
                    Text("Miete aktueller Monat", fontWeight = FontWeight.Bold, color = DarkNavy)
                    LinearProgressIndicator(progress = { if (summary.expectedRent <= 0) 0f else (summary.actualRent / summary.expectedRent).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("Soll ${NumberFormatter.format(summary.expectedRent)} · Ist ${NumberFormatter.format(summary.actualRent)} · Offen ${NumberFormatter.format(summary.outstandingRent)}", fontSize = 11.sp)
                    Text("${summary.vacantCount} freie/zu prüfende Einheiten · $unchecked ungeprüfte Belege · $activeLoanCount aktive Darlehen", fontSize = 10.sp, color = SlateGray)
                    Text("Steuerjahr ${java.time.LocalDate.now().year} · ${documents.size} Dokumente · ${property.wohneinheiten.split(',').count { it.isNotBlank() }} Einheiten", fontSize = 10.sp, color = SlateGray)
                }
            }
        }
        val destinations = listOf(
            Triple(PropertySection.UNITS, "Einheiten & Mieter", Icons.Default.Apartment),
            Triple(PropertySection.RENT, "Mieteingänge", Icons.Default.Payments),
            Triple(PropertySection.RECEIPTS, "Belege & Kosten", Icons.Default.Receipt),
            Triple(PropertySection.FINANCE, "Finanzierung", Icons.Default.AccountBalance),
            Triple(PropertySection.RENOVATIONS, "Sanierungen", Icons.Default.Build),
            Triple(PropertySection.DOCUMENTS, "Dokumente", Icons.Default.Description),
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

@Composable private fun PropertyUnits(units: List<WohneinheitStatus>) = LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    item { Text("Einheiten & Mieter", fontSize = 20.sp, fontWeight = FontWeight.Black, color = DarkNavy) }
    items(units, key = { it.unitId }) { unit ->
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), Arrangement.spacedBy(4.dp)) {
                Text("${unit.name} · ${unit.label}", fontWeight = FontWeight.Bold, color = DarkNavy)
                Text("Status: ${unit.status}", fontSize = 11.sp, color = if (unit.status == "Vermietet") EmeraldGreen else WarmOrange)
                Text("Mieter: ${unit.mieter.ifBlank { "–" }}", fontSize = 11.sp)
                Text("Kaltmiete: ${NumberFormatter.format(unit.kaltmiete)} · Seit: ${unit.mietvertragsstart.ifBlank { "–" }}", fontSize = 10.sp, color = SlateGray)
            }
        }
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

@Composable private fun PropertyData(viewModel: ReceiptViewModel, property: PropertyMetadata) {
    var edit by remember { mutableStateOf(false) }
    if (edit) PropertyMetadataFormDialog(viewModel = viewModel, onDismiss = { edit = false })
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Objektdaten", fontSize = 20.sp, fontWeight = FontWeight.Black, color = DarkNavy) }
        item { Text("Name: ${property.name}\nAdresse: ${property.adresse}\nBaujahr: ${property.baujahr}\nKaufdatum: ${property.notariellesKaufdatum}\nKaufpreis: ${NumberFormatter.format(property.gesamtKaufpreis)}\nWohnfläche: ${property.wohnflaeche} m²\nGrundstück: ${property.grundstuecksgroesse} m²", color = DarkNavy) }
        item { Button(onClick = { edit = true }, modifier = Modifier.fillMaxWidth()) { Text("Objektdaten bearbeiten") } }
    }
}

@Composable
fun MoreScreen(viewModel: ReceiptViewModel) {
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) AccountSettingsDialog(viewModel = viewModel, onDismiss = { showSettings = false })
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Mehr", fontSize = 22.sp, fontWeight = FontWeight.Black, color = DarkNavy); Text("Weitere Bereiche und Einstellungen", fontSize = 11.sp, color = SlateGray) }
        item { PropertyDestination("Fahrtenbuch", Icons.Default.DirectionsCar) { viewModel.setScreen(AppScreen.LOGBOOK) } }
        item { PropertyDestination("Finanzen", Icons.Default.AccountBalance) { viewModel.setScreen(AppScreen.LEDGER) } }
        item { PropertyDestination("Dokumentenakte", Icons.Default.Description) { viewModel.setScreen(AppScreen.DOCUMENTS) } }
        item { PropertyDestination("Einstellungen", Icons.Default.Settings) { showSettings = true } }
    }
}

@Composable
private fun PropertyCreationWizard(onDismiss: () -> Unit, onSave: (PropertyMetadata, List<WohneinheitStatus>, Loan?) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val generatedPropertyId = remember { java.util.UUID.randomUUID().toString() }
    var name by remember { mutableStateOf("") }; var street by remember { mutableStateOf("") }; var zip by remember { mutableStateOf("") }; var city by remember { mutableStateOf("") }
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
                    0 -> { field(name, "Objektname") { name = it }; field(street, "Straße und Hausnummer") { street = it }; field(zip, "PLZ") { zip = it }; field(city, "Ort") { city = it }; field(purchaseDate, "Kaufdatum YYYY-MM-DD") { purchaseDate = it }; field(purchasePrice, "Kaufpreis €") { purchasePrice = it } }
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
                        propertyId = generatedPropertyId, name = name.ifBlank { street.ifBlank { "Neue Immobilie" } },
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
