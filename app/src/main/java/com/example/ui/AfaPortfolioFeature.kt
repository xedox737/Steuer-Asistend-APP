package com.example.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ManagedDocument
import com.example.data.PropertyMetadata
import com.example.data.TaxPhase1Summary
import com.example.data.TaxPropertyCalculator
import java.io.File
import java.time.LocalDate

/** AfA navigation uses the existing property records. It never creates a second building. */
@Composable
internal fun AfaPortfolioScreen(viewModel: ReceiptViewModel, onBack: () -> Unit) {
    val properties by viewModel.properties.collectAsState()
    val receipts by viewModel.receipts.collectAsState()
    val documents by viewModel.managedDocuments.collectAsState()
    var propertyId by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    var showBreakdown by remember { mutableStateOf(false) }
    val property = properties.firstOrNull { it.propertyId == propertyId }
    val back: () -> Unit = { if (propertyId != null) { propertyId = null; tab = 0 } else onBack() }
    BackHandler(onBack = back)
    val summary = property?.let {
        TaxPropertyCalculator.calculate(it, ImmobilienManagerProjection.receipts(it, viewModel.getWohneinheitenForProperty(it), receipts))
    }

    if (property != null && editing) {
        AfaDurationDialog(property, documents.filter { it.propertyId == property.propertyId },
            onDismiss = { editing = false }, onSave = { viewModel.updatePropertyMetadata(it); editing = false })
    }
    if (summary != null && showBreakdown) {
        AlertDialog(onDismissRequest = { showBreakdown = false }, title = { Text("Kaufpreisaufteilung") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AfaValueRow("Gesamtkaufpreis", NumberFormatter.format(summary.purchasePrice))
                    AfaValueRow("Gebäudeanteil", NumberFormatter.format(summary.buildingPurchaseShare))
                    AfaValueRow("Grund und Boden", NumberFormatter.format(summary.landPurchaseShare))
                    AfaValueRow("Anteilige Anschaffungsnebenkosten", NumberFormatter.format(summary.buildingAncillaryShare))
                    AfaValueRow("AfA-Bemessungsgrundlage", NumberFormatter.format(summary.buildingAcquisitionCosts), true)
                    Text("Quelle: ${summary.allocationSource.ifBlank { "Nicht angegeben" }}", fontSize = 11.sp, color = SlateGray)
                }
            }, confirmButton = { TextButton(onClick = { showBreakdown = false }) { Text("Schließen") } })
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(Ui2.padding),
        verticalArrangement = Arrangement.spacedBy(Ui2.spacing)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                Column {
                    Text("AfA Gebäude", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text("Steuer-Assistent", fontSize = 12.sp, color = SlateGray)
                }
            }
        }
        if (property == null) {
            item { AfaCard {
                Text("Gebäude abschreiben", fontWeight = FontWeight.Bold, color = DarkNavy)
                Text("Alle vorhandenen Immobilien und ihre AfA auf einen Blick.", fontSize = 12.sp, color = SlateGray)
            } }
            item { Button(onClick = { viewModel.setScreen(AppScreen.PROPERTIES) }, modifier = Modifier.fillMaxWidth()) {
                Text("Immobilie hinzufügen")
            } }
            if (properties.isEmpty()) {
                item { AfaCard { Text("Noch keine Immobilie angelegt. Lege sie im Bereich Immobilien an.") } }
            } else {
                items(properties, key = { it.propertyId }) { building ->
                    val data = TaxPropertyCalculator.calculate(building,
                        ImmobilienManagerProjection.receipts(building, viewModel.getWohneinheitenForProperty(building), receipts))
                    Card(onClick = { propertyId = building.propertyId; tab = 0 }, modifier = Modifier.fillMaxWidth(), shape = Ui2.shape,
                        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AfaPhoto(building, Modifier.size(68.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(building.name, fontWeight = FontWeight.Bold, color = DarkNavy, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(building.adresse, fontSize = 11.sp, color = SlateGray, maxLines = 2)
                                    Text(building.status, fontSize = 11.sp, color = if (building.status == "Aktiv") EmeraldGreen else SlateGray)
                                }
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = SlateGray)
                            }
                            HorizontalDivider(color = BorderColor)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AfaMetric("Baujahr", building.baujahr.takeIf { it > 0 }?.toString() ?: "–", Modifier.weight(1f))
                                AfaMetric("AfA-Satz", if (data.afaRatePercent > 0) "${"%.1f".format(data.afaRatePercent)} %" else "–", Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                AfaMetric("Grundlage", NumberFormatter.format(data.buildingAcquisitionCosts), Modifier.weight(1f))
                                AfaMetric("AfA / Jahr", NumberFormatter.format(data.annualAfa), Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        } else if (summary != null) {
            item { AfaCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AfaPhoto(property, Modifier.size(92.dp))
                    Column(Modifier.weight(1f)) {
                        Text(property.name, fontWeight = FontWeight.Bold, color = DarkNavy, maxLines = 2)
                        Text(property.adresse, fontSize = 12.sp, color = SlateGray)
                        Text(property.status, fontSize = 11.sp, color = EmeraldGreen)
                    }
                }
                HorizontalDivider(color = BorderColor)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AfaMetric("Einheiten", viewModel.getWohneinheitenForProperty(property).size.toString(), Modifier.weight(1f))
                    AfaMetric("Wohnfläche", "${property.wohnflaeche.toInt()} m²", Modifier.weight(1f))
                    AfaMetric("Grundstück", "${property.grundstuecksgroesse.toInt()} m²", Modifier.weight(1f))
                }
            } }
            item {
                Row(Modifier.fillMaxWidth().background(Color(0xFFEAF3FF), Ui2.controlShape).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf("Stammdaten", "AfA Berechnung", "Verlauf").forEachIndexed { index, title ->
                        Box(Modifier.weight(1f).background(if (tab == index) AccentBlue else Color.Transparent, Ui2.controlShape)
                            .clickable { tab = index }.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                            Text(title, fontSize = 11.sp, maxLines = 1, color = if (tab == index) Color.White else DarkNavy)
                        }
                    }
                }
            }
            when (tab) {
                0 -> item { AfaCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Stammdaten", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = DarkNavy)
                        TextButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, null, Modifier.size(16.dp)); Text("AfA-Methode") }
                    }
                    AfaValueRow("Adresse", property.adresse)
                    AfaValueRow("Baujahr", property.baujahr.toString())
                    AfaValueRow("Kaufdatum", property.notariellesKaufdatum)
                    AfaValueRow("Nutzen und Lasten", property.uebergangNutzenLasten)
                    AfaValueRow("Kaufpreis", NumberFormatter.format(property.gesamtKaufpreis))
                    AfaValueRow("Gebäudeanteil", NumberFormatter.format(summary.buildingPurchaseShare))
                    AfaValueRow("Grund und Boden", NumberFormatter.format(summary.landPurchaseShare))
                    AfaValueRow("Anschaffungsnebenkosten (Gebäude)", NumberFormatter.format(summary.buildingAncillaryShare))
                    AfaValueRow("AfA-Methode", if (summary.shorterMethodActive) "Kürzere Nutzungsdauer" else "Regulär")
                    if (property.afaShorterYears > 0) AfaValueRow("Geplante Restnutzungsdauer", "${property.afaShorterYears} Jahre")
                    if (property.afaShorterYears > 0) AfaValueRow("Status", if (summary.shorterMethodActive) "Bestätigt" else "Szenario – nicht übernommen")
                    TextButton(onClick = { showBreakdown = true }) { Text("Kaufpreisaufteilung ansehen") }
                    TextButton(onClick = { viewModel.selectProperty(property.propertyId); viewModel.setScreen(AppScreen.PROPERTIES) }) {
                        Text("Immobilien-Stammdaten bearbeiten")
                    }
                } }
                1 -> item { AfaCard {
                    Text("Berechnung", fontWeight = FontWeight.Bold, color = DarkNavy)
                    Row(Modifier.fillMaxWidth().background(Color(0xFFEAF3FF), Ui2.controlShape).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Assessment, null, tint = AccentBlue)
                        Text(if (summary.shorterMethodActive) "Bestätigte kürzere Nutzungsdauer" else "Lineare Gebäude-AfA",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    AfaValueRow("Gebäude-Kaufpreisanteil", NumberFormatter.format(summary.buildingPurchaseShare))
                    AfaValueRow("+ Anteilige Nebenkosten", NumberFormatter.format(summary.buildingAncillaryShare))
                    AfaValueRow("AfA-Bemessungsgrundlage", NumberFormatter.format(summary.buildingAcquisitionCosts), true)
                    AfaValueRow("Reguläre AfA / volles Jahr", NumberFormatter.format(summary.standardAnnualAfa))
                    if (summary.shorterAnnualAfa > 0) {
                        AfaValueRow("Szenario (${property.afaShorterYears} Jahre)", NumberFormatter.format(summary.shorterAnnualAfa))
                        Text(if (summary.shorterMethodActive) "Bestätigt und für diese Berechnung verwendet."
                             else "Szenario – die reguläre AfA bleibt maßgeblich.", fontSize = 11.sp, color = SlateGray)
                    }
                    AfaValueRow("Jährliche AfA (volles Jahr)", NumberFormatter.format(summary.annualAfa), true)
                    AfaValueRow("Erstes Jahr ab ${summary.afaStartDate.ifBlank { "unbekannt" }}", NumberFormatter.format(summary.firstYearAfa))
                    if (summary.allocationNeedsReview) Text("Kaufpreisaufteilung prüfen: Differenz ${NumberFormatter.format(summary.allocationDifference)}", color = CrimsonRed, fontSize = 11.sp)
                    TextButton(onClick = { showBreakdown = true }) { Text("Aufteilung & Nebenkosten") }
                } }
                else -> item { AfaCard {
                    Text("AfA planen", fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text("Prognose für 10 Jahre · keine verbuchten Steuerwerte", fontSize = 11.sp, color = SlateGray)
                    val start = runCatching { LocalDate.parse(property.uebergangNutzenLasten.ifBlank { property.notariellesKaufdatum }) }.getOrNull()
                    if (start == null || summary.buildingAcquisitionCosts <= 0.0 || summary.annualAfa <= 0.0) {
                        Text("Für die Prognose fehlen AfA-Beginn oder Bemessungsgrundlage.", color = SlateGray)
                    } else {
                        var remaining = summary.buildingAcquisitionCosts
                        repeat(10) { index ->
                            val year = start.year + index
                            val planned = (if (index == 0) summary.firstYearAfa else summary.annualAfa).coerceAtMost(remaining)
                            remaining = (remaining - planned).coerceAtLeast(0.0)
                            AfaValueRow(year.toString(), NumberFormatter.format(planned))
                        }
                    }
                    HorizontalDivider()
                    Text("Bisherige AfA und tatsächlicher Restbuchwert werden erst nach Erfassung bestätigter Jahreswerte angezeigt.", fontSize = 11.sp, color = SlateGray)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                        Text("Unverbindliche Vorschau; steuerliche Angaben prüfen.", fontSize = 11.sp, color = SlateGray)
                    }
                } }
            }
        }
    }
}

@Composable private fun AfaCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = Ui2.shape,
        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable private fun AfaMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 10.sp, color = SlateGray, maxLines = 2)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy, maxLines = 2)
    }
}

@Composable private fun AfaValueRow(label: String, value: String, emphasized: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1.25f), fontSize = 11.sp, color = if (emphasized) DarkNavy else SlateGray)
        Text(value.ifBlank { "–" }, Modifier.weight(0.85f), fontSize = 11.sp, textAlign = TextAlign.End,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal, color = DarkNavy)
    }
    HorizontalDivider(color = BorderColor)
}

@Composable private fun AfaPhoto(property: PropertyMetadata, modifier: Modifier) {
    val bitmap = remember(property.bildPfad) {
        runCatching { property.bildPfad.takeIf { it.isNotBlank() }?.let { BitmapFactory.decodeFile(it) } }.getOrNull()
    }
    Box(modifier.background(Color(0xFFEAF3FF), Ui2.iconShape), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Default.AccountBalance, null, tint = AccentBlue)
    }
}

@Composable private fun AfaDurationDialog(
    property: PropertyMetadata,
    documents: List<ManagedDocument>,
    onDismiss: () -> Unit,
    onSave: (PropertyMetadata) -> Unit
) {
    var years by remember(property.propertyId) { mutableStateOf(property.afaShorterYears.takeIf { it > 0 }?.toString().orEmpty()) }
    var start by remember(property.propertyId) { mutableStateOf(property.afaShorterStartDate) }
    var reason by remember(property.propertyId) { mutableStateOf(property.afaShorterReason) }
    var documentId by remember(property.propertyId) { mutableStateOf(property.afaShorterDocumentId) }
    var confirmed by remember(property.propertyId) { mutableStateOf(property.afaShorterConfirmed) }
    val regularRate = when { property.baujahr <= 0 -> 0.0; property.baujahr < 1925 -> 2.5; property.baujahr < 2023 -> 2.0; else -> 3.0 }
    val expectedStart = property.uebergangNutzenLasten.ifBlank { property.notariellesKaufdatum }
    val enteredYears = years.toIntOrNull() ?: 0
    val valid = regularRate > 0 && enteredYears in 1..100 && enteredYears < 100.0 / regularRate &&
        runCatching { LocalDate.parse(start) }.getOrNull() == runCatching { LocalDate.parse(expectedStart) }.getOrNull() &&
        reason.isNotBlank() && documents.any { it.documentId == documentId }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("AfA-Methode", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Regulär bleibt voreingestellt. Eine kürzere Nutzungsdauer kann zunächst als Szenario gespeichert werden.", fontSize = 12.sp) }
                item { OutlinedTextField(years, { years = it.filter(Char::isDigit).take(3); confirmed = false }, label = { Text("Kürzere Dauer in Jahren") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { OutlinedTextField(start, { start = it; confirmed = false }, label = { Text("Beginn (JJJJ-MM-TT)") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { Text("Für eine Bestätigung muss der Beginn derzeit ${expectedStart.ifBlank { "dem Erwerbsdatum" }} entsprechen. Spätere Methodenwechsel bleiben ein Szenario.", fontSize = 11.sp, color = SlateGray) }
                item { OutlinedTextField(reason, { reason = it; confirmed = false }, label = { Text("Begründung") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                item { Text("Nachweis aus der Dokumentenakte zuordnen", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                if (documents.isEmpty()) item { Text("Noch kein Dokument vorhanden. Importiere den Nachweis in der Dokumentenakte.", fontSize = 11.sp, color = SlateGray) }
                items(documents, key = { it.documentId }) { document ->
                    TextButton(onClick = { documentId = document.documentId; confirmed = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (documentId == document.documentId) "✓ " else "○ ")
                        Text(document.title.ifBlank { document.originalFilename }.ifBlank { document.documentId }, maxLines = 2, modifier = Modifier.weight(1f))
                    }
                }
                if (years.isNotBlank()) item { Text(if (valid) "Nachweis und Angaben vollständig. Bestätigung möglich."
                    else "Für eine Bestätigung fehlen gültige Jahre, Beginn, Begründung oder ein Dokument.",
                    fontSize = 11.sp, color = if (valid) EmeraldGreen else SlateGray) }
                item {
                    TextButton(onClick = { confirmed = !confirmed }, enabled = valid && years.isNotBlank()) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (confirmed) "Bestätigt – für AfA verwenden" else "Nach fachlicher Prüfung bestätigen")
                    }
                }
                item { Text("Eine Bestätigung ändert die AfA-Berechnung für dieses Objekt. Bitte die Voraussetzungen vor Verwendung prüfen.", fontSize = 11.sp, color = SlateGray) }
            }
        }, confirmButton = {
            Button(onClick = {
                onSave(property.copy(afaShorterYears = enteredYears, afaShorterStartDate = start.trim(),
                    afaShorterReason = reason.trim(), afaShorterDocumentId = documentId,
                    afaShorterConfirmed = confirmed && valid))
            }, enabled = years.isBlank() || (enteredYears in 1..100 && (regularRate == 0.0 || enteredYears < 100.0 / regularRate))) { Text("Speichern") }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } })
}
