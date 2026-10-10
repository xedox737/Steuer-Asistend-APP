package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.util.PdfExporter
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> ReviewDropdown(label: String, value: T?, choices: List<T>, text: (T) -> String,
    modifier: Modifier = Modifier, enabled: Boolean = true, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(value = value?.let(text).orEmpty(), onValueChange = {}, readOnly = true, enabled = enabled,
            label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice -> DropdownMenuItem(text = { Text(text(choice)) }, onClick = { onSelect(choice); expanded = false }) }
        }
    }
}

/** Shared explicit ID-based context for capture and measures. No global selection mutation. */
@Composable
internal fun ReceiptObjectContextPicker(properties: List<PropertyMetadata>, propertyId: String,
    units: List<WohneinheitStatus>, unitId: String, onProperty: (String) -> Unit, onUnit: (String) -> Unit,
    tagPrefix: String = "capture") {
    Ui2Section("Objektzuordnung") {
        ReviewDropdown("Immobilie", properties.firstOrNull { it.propertyId == propertyId }, properties,
            { it.name.ifBlank { it.adresse } }, Modifier.fillMaxWidth().testTag("${tagPrefix}_property")) { onProperty(it.propertyId) }
        val choices = listOf("" to "Gesamtobjekt / Allgemein") + units.map { it.unitId to it.name }
        ReviewDropdown("Einheit (optional)", choices.firstOrNull { it.first == unitId }, choices, { it.second },
            Modifier.fillMaxWidth().testTag("${tagPrefix}_unit"), propertyId.isNotBlank()) { onUnit(it.first) }
        if (propertyId.isBlank()) Text("Bitte vor dem Speichern eine Immobilie wählen.", color = WarmOrange)
    }
}

@Composable
internal fun RenovationErrorDialog(viewModel: ReceiptViewModel) {
    val error by viewModel.renovationError.collectAsStateWithLifecycle()
    error?.let { AlertDialog(onDismissRequest = viewModel::clearRenovationError,
        title = { Text("Sanierungsprüfung") }, text = { Text(it) },
        confirmButton = { TextButton(onClick = viewModel::clearRenovationError) { Text("OK") } }) }
}

@Composable
private fun ReviewHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = DarkNavy) }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun RenovationProgress(summary: RenovationReviewSummary) {
    Ui2Section("Sanierungs- & 15%-Prüfung", Modifier.testTag("renovation_progress")) {
        Text(summary.property.name, fontWeight = FontWeight.Bold)
        Text("Prüfzeitraum: ${receiptDisplayDate(summary.basis.monitorStartDate).ifBlank { "Offen" }} – ${receiptDisplayDate(summary.basis.monitorEndDate).ifBlank { "Offen" }}")
        Text("Quelle: ${summary.startSource}", style = MaterialTheme.typography.bodySmall, color = SlateGray)
        Text("Gebäude-Anschaffungskosten: ${NumberFormatter.format(summary.basis.buildingAcquisitionCosts)}")
        Text("Quelle: bestehende AfA-Basis einschließlich anteiliger Erwerbsnebenkosten", style = MaterialTheme.typography.bodySmall, color = SlateGray)
        if (summary.basis.allocationNeedsReview) Text("Kaufpreisaufteilung prüfen.", color = WarmOrange)
        Text("15%-Grenze: ${NumberFormatter.format(summary.basis.limit15Percent)}")
        Text("Bisher berücksichtigt (netto): ${NumberFormatter.format(summary.consideredNet)}", Modifier.testTag("renovation_considered"), fontWeight = FontWeight.Bold)
        Text("Rest bis Grenze: ${NumberFormatter.format(summary.remaining)}")
        summary.usagePercent?.let { usage ->
            val color = when (summary.warning) {
                RenovationWarning.NEUTRAL -> EmeraldGreen
                RenovationWarning.HINWEIS -> WarmOrange
                RenovationWarning.WARNUNG, RenovationWarning.GRENZE_ERREICHT -> CrimsonRed
            }
            LinearProgressIndicator(progress = { (usage / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = color)
            Text("${String.format(java.util.Locale.GERMANY, "%.1f", usage)} % der Grenze", color = color)
            if (summary.warning == RenovationWarning.GRENZE_ERREICHT) Text(RenovationReviewCalculator.RETROSPECTIVE_WARNING, color = color)
            else if (summary.warning == RenovationWarning.WARNUNG) Text("Grenze fast erreicht – steuerlich prüfen.", color = color)
            else if (summary.warning == RenovationWarning.HINWEIS) Text("Grenze nähert sich – Zuordnungen prüfen.", color = color)
        } ?: Text("Gebäude-Anschaffungskosten fehlen. Bitte die vorhandenen AfA-/Kaufdaten ergänzen.", color = WarmOrange)
        Text(RenovationReviewCalculator.NO_TAX_DECISION, fontWeight = FontWeight.Bold)
        Text(RenovationReviewCalculator.PERIOD_NOTE, style = MaterialTheme.typography.bodySmall, color = SlateGray)
        summary.errors.forEach { Text(it, color = CrimsonRed) }
    }
}

/** Existing property section and existing More/monitor entry share this same screen. */
@Composable
internal fun RenovationReviewScreen(viewModel: ReceiptViewModel, property: PropertyMetadata, onBack: () -> Unit) {
    val summaries by viewModel.renovationSummaries.collectAsStateWithLifecycle()
    val documents by viewModel.managedDocuments.collectAsStateWithLifecycle()
    val summary = summaries[property.propertyId]
    var selectedMeasureId by rememberSaveable(property.propertyId) { mutableStateOf<String?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var editingMeasure by remember { mutableStateOf<RenovationMeasure?>(null) }
    var receiptPicker by remember { mutableStateOf(false) }
    var evidencePicker by remember { mutableStateOf(false) }
    var confirmReviewed by remember { mutableStateOf(false) }
    var documentDetail by remember { mutableStateOf<ManagedDocument?>(null) }
    var assignmentReceipt by remember { mutableStateOf<Receipt?>(null) }
    var report by remember { mutableStateOf<File?>(null) }
    val selected = summary?.measures?.firstOrNull { it.id == selectedMeasureId }
    val back = { if (selectedMeasureId != null) selectedMeasureId = null else onBack() }
    BackHandler(onBack = back)
    RenovationErrorDialog(viewModel)
    if (editorOpen) RenovationMeasureDialog(viewModel, property, editingMeasure, onDismiss = { editorOpen = false }) { editorOpen = false }
    if (receiptPicker && selected != null) RenovationReceiptPicker(viewModel, selected, onDismiss = { receiptPicker = false }) {
        assignmentReceipt = it; receiptPicker = false
    }
    assignmentReceipt?.let { receipt -> RenovationAssignmentDialog(viewModel, receipt, selected?.id, onDismiss = { assignmentReceipt = null }) }
    if (evidencePicker && selected != null) RenovationEvidenceDialog(viewModel, selected, documents, onDismiss = { evidencePicker = false })
    documentDetail?.let { DocumentDetailDialog(it, viewModel) { documentDetail = null } }
    if (confirmReviewed && selected != null) AlertDialog(onDismissRequest = { confirmReviewed = false },
        title = { Text("Steuerberater-Prüfung bestätigen") },
        text = { Text("Nur bestätigen, wenn der Steuerberater die Einordnung dieser Maßnahme geprüft hat. Belegfreigaben und Buchungen bleiben separat.") },
        confirmButton = { TextButton(onClick = {
            viewModel.saveRenovationMeasure(selected.copy(taxStatus = RenovationTaxStatus.STEUERBERATER_BESTAETIGT, status = RenovationStatus.BESTAETIGT)) { confirmReviewed = false }
        }) { Text("Bestätigung speichern") } }, dismissButton = { TextButton(onClick = { confirmReviewed = false }) { Text("Abbrechen") } })
    report?.let { RenovationReportDialog(it) { report = null } }
    Column(Modifier.fillMaxSize()) {
        ReviewHeader(if (selected == null) "Sanierung & 15%-Prüfung" else selected.name, back)
        if (summary == null) { Text("Prüfung wird geladen …", Modifier.padding(Ui2.padding)); return@Column }
        if (selected == null) {
            LazyColumn(Modifier.fillMaxSize().testTag("renovation_overview"), contentPadding = PaddingValues(Ui2.padding), verticalArrangement = Arrangement.spacedBy(Ui2.spacing)) {
                item { RenovationProgress(summary) }
                item { Button(onClick = { editingMeasure = null; editorOpen = true }, modifier = Modifier.fillMaxWidth().testTag("renovation_add_measure")) { Text("Maßnahme anlegen") } }
                item { OutlinedButton(onClick = { viewModel.exportRenovationReport(property.propertyId) { report = it } }, modifier = Modifier.fillMaxWidth()) { Text("Steuerberater-Bericht exportieren") } }
                item { Text("Maßnahmen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                if (summary.measures.isEmpty()) item { Text("Noch keine Maßnahmen. Reparaturbelege werden erst nach bewusster Zuordnung aufgenommen.") }
                items(summary.measures, key = { it.id }) { measure ->
                    val lines = summary.lines.filter { it.relation.renovationMeasureId == measure.id }
                    Ui2Destination(measure.name, "${measure.status.label} · ${measure.taxStatus.label}\n${NumberFormatter.format(lines.filter { it.included }.sumOf { it.netCost ?: 0.0 })} berücksichtigt", Icons.Default.Build) { selectedMeasureId = measure.id }
                }
                item { Text("Offene Prüffälle (${summary.openCases.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(summary.openCases, key = { it.relation.receiptInternalId }) { line -> RenovationReceiptLine(line) { assignmentReceipt = line.receipt } }
            }
        } else {
            val measureLines = summary.lines.filter { it.relation.renovationMeasureId == selected.id }
            val units = viewModel.getWohneinheitenForProperty(property)
            LazyColumn(Modifier.fillMaxSize().testTag("renovation_measure_detail"), contentPadding = PaddingValues(Ui2.padding), verticalArrangement = Arrangement.spacedBy(Ui2.spacing)) {
                item { Ui2Section(selected.name) {
                    Text(property.name)
                    Text(units.firstOrNull { it.unitId == selected.unitId }?.name ?: if (selected.unitId.isBlank()) "Gesamtobjekt / Allgemein" else "Einheit nicht verfügbar")
                    Text("Zeitraum: ${receiptDisplayDate(selected.startDate).ifBlank { "Offen" }} – ${receiptDisplayDate(selected.endDate).ifBlank { "Offen" }}")
                    Text(selected.status.label); if (selected.description.isNotBlank()) Text(selected.description)
                    Text("Summe brutto: ${NumberFormatter.format(measureLines.sumOf { it.grossCost })}")
                    OutlinedButton(onClick = { editingMeasure = selected; editorOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Maßnahme bearbeiten") }
                } }
                item { Ui2Section("Steuerliche Einordnung") {
                    Text("Prüfzeitraum: ${receiptDisplayDate(summary.basis.monitorStartDate).ifBlank { "Offen" }} – ${receiptDisplayDate(summary.basis.monitorEndDate).ifBlank { "Offen" }}")
                    Text(selected.taxStatus.label)
                    Text("Bruttokosten: ${NumberFormatter.format(measureLines.filter { it.grossCost >= 0 }.sumOf { it.grossCost })}")
                    Text("Erstattungen / Gutschriften: ${NumberFormatter.format(-measureLines.filter { it.grossCost < 0 }.sumOf { it.grossCost })}")
                    Text("Berücksichtigte Nettokosten: ${NumberFormatter.format(measureLines.filter { it.included }.sumOf { it.netCost ?: 0.0 })}")
                    Text("Steuerberater-Notiz: ${selected.advisorNote.ifBlank { "Keine Notiz" }}")
                    Text(RenovationReviewCalculator.NO_TAX_DECISION)
                    if (selected.endDate.isNotBlank() && summary.basis.monitorEndDate.isNotBlank() && selected.startDate <= summary.basis.monitorEndDate && selected.endDate > summary.basis.monitorEndDate)
                        Text("Maßnahme über Prüfzeitraum: zeitanteilige Kosten bitte steuerlich prüfen.", color = WarmOrange)
                } }
                item { Text("Belege & Nachweise", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(measureLines, key = { it.relation.receiptInternalId }) { line -> RenovationReceiptLine(line) {
                    line.receipt?.let { viewModel.openReceiptDetail(it.id, AppScreen.PROPERTIES) }
                } }
                if (measureLines.isEmpty()) item { Text("Noch keine Belege zugeordnet.") }
                items(selected.evidence, key = { it.documentId }) { evidence ->
                    val doc = documents.firstOrNull { it.documentId == evidence.documentId && it.propertyId == property.propertyId }
                    TextButton(onClick = { documentDetail = doc }, enabled = doc != null, modifier = Modifier.fillMaxWidth()) {
                        Text("${evidence.role.label}: ${doc?.originalFilename ?: "Dokument nicht verfügbar"}")
                    }
                }
                item { Button(onClick = { receiptPicker = true }, modifier = Modifier.fillMaxWidth()) { Text("Belege zuordnen") } }
                item { OutlinedButton(onClick = { evidencePicker = true }, modifier = Modifier.fillMaxWidth()) { Text("Nachweise referenzieren") } }
                item { OutlinedButton(onClick = { viewModel.exportRenovationReport(property.propertyId) { report = it } }, modifier = Modifier.fillMaxWidth()) { Text("Steuerberater-Bericht exportieren") } }
                item { OutlinedButton(onClick = { confirmReviewed = true }, modifier = Modifier.fillMaxWidth()) { Text("Als geprüft markieren") } }
            }
        }
    }
}

@Composable
private fun RenovationReceiptLine(line: RenovationReviewLine, onClick: () -> Unit) {
    Ui2Destination(line.receipt?.aussteller ?: "Beleg nicht verfügbar",
        "${line.receipt?.getEffectiveDisplayId() ?: line.relation.receiptInternalId}\n${line.periodLabel} · ${line.relation.taxStatus.label}\n${line.issue ?: if (line.included) "Netto berücksichtigt: ${NumberFormatter.format(line.netCost)}" else "Nicht in Prüfsumme"}",
        Icons.Default.Build, if (line.issue != null) WarmOrange else AccentBlue, onClick)
}

@Composable
internal fun RenovationReceiptCard(viewModel: ReceiptViewModel, receipt: Receipt) {
    val properties by viewModel.properties.collectAsStateWithLifecycle()
    val review by viewModel.renovationReview.collectAsStateWithLifecycle()
    val summaries by viewModel.renovationSummaries.collectAsStateWithLifecycle()
    val property = properties.firstOrNull { it.propertyId == receipt.propertyId }
    val relation = review.relations.firstOrNull { it.receiptInternalId == receipt.internalId }
    if (property == null && relation == null) return
    var assign by remember { mutableStateOf(false) }
    val line = summaries[receipt.propertyId]?.lines?.firstOrNull { it.relation.receiptInternalId == receipt.internalId }
    RenovationErrorDialog(viewModel)
    if (assign) RenovationAssignmentDialog(viewModel, receipt, onDismiss = { assign = false })
    Ui2Section("Sanierungs- & 15%-Prüfung", Modifier.testTag("receipt_renovation_card")) {
        Text(line?.periodLabel ?: "Noch nicht für 15%-Prüfung zugeordnet")
        Text("Maßnahme: ${review.measures.firstOrNull { it.id == relation?.renovationMeasureId }?.name ?: "Keine"}")
        Text("Prüfstatus: ${relation?.taxStatus?.label ?: "Nicht geprüft"}")
        Text(if (line?.included == true) "Für 15%-Prüfung berücksichtigt: ${NumberFormatter.format(line.netCost)} netto" else "Nicht in Prüfsumme")
        summaries[receipt.propertyId]?.usagePercent?.let { Text("Objekt: ${String.format(java.util.Locale.GERMANY, "%.1f", it)} % der Grenze") }
        line?.issue?.let { Text(it, color = WarmOrange) }
        if (relation != null && relation.propertyId != receipt.propertyId) Text("Die Sanierungszuordnung gehört zum bisherigen Objekt. Bitte erneut zuordnen.", color = WarmOrange)
        if (relation?.advisorMarked == true) Text("Steuerberater-Vormerkung gespeichert", Modifier.testTag("renovation_advisor_marked"))
        Text(RenovationReviewCalculator.NO_TAX_DECISION, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { assign = true }, enabled = property != null && receipt.internalId.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("renovation_assign")) { Text("Maßnahme zuordnen") }
        TextButton(onClick = { viewModel.saveRenovationRelation((relation ?: RenovationReceiptRelation(receipt.internalId, receipt.propertyId)).copy(advisorMarked = true)) },
            enabled = property != null && receipt.internalId.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("renovation_mark_advisor")) { Text("Steuerberater vormerken") }
    }
}

@Composable
private fun RenovationAssignmentDialog(viewModel: ReceiptViewModel, receipt: Receipt, suggestedMeasureId: String? = null, onDismiss: () -> Unit) {
    val review by viewModel.renovationReview.collectAsStateWithLifecycle()
    val original = review.relations.firstOrNull { it.receiptInternalId == receipt.internalId }
    var measureId by remember { mutableStateOf(suggestedMeasureId ?: original?.renovationMeasureId.orEmpty()) }
    var taxStatus by remember { mutableStateOf(original?.taxStatus ?: RenovationTaxStatus.NICHT_GEPRUEFT) }
    var net by remember { mutableStateOf(original?.confirmedNetAmount?.let { GermanNumberInput.formatForInput(it) }.orEmpty()) }
    var advisor by remember { mutableStateOf(original?.advisorMarked == true) }
    var error by remember { mutableStateOf<String?>(null) }
    val measures = review.measures.filter { it.propertyId == receipt.propertyId && (it.unitId.isBlank() || it.unitId == receipt.unitId) }
    val choices = listOf("" to "Keine Maßnahme") + measures.map { it.id to it.name }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Sanierungsprüfung zuordnen") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Ui2.spacing)) {
            Text(receipt.aussteller); Text("Ein Beleg wird nur einmal berücksichtigt. Wechsel ersetzt die bisherige Zuordnung.")
            ReviewDropdown("Maßnahme", choices.firstOrNull { it.first == measureId }, choices, { it.second }, Modifier.testTag("renovation_assignment_measure")) { measureId = it.first }
            ReviewDropdown("Steuerlicher Prüfstatus", taxStatus, RenovationTaxStatus.entries, { it.label }, Modifier.testTag("renovation_assignment_status")) { taxStatus = it }
            OutlinedTextField(net, { net = it }, label = { Text("Bestätigter Nettobetrag € (optional)") }, supportingText = {
                Text("Ohne Eingabe werden belastbare Positions-/MwSt.-Daten verwendet. Gutschriften zieht die App ab.") }, modifier = Modifier.fillMaxWidth().testTag("renovation_assignment_net"))
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(advisor, { advisor = it }); Text("Steuerberater vormerken", Modifier.weight(1f)) }
            if (measures.isEmpty()) Text("Eine Maßnahme kann in der Objektansicht unter Sanierung & 15%-Prüfung angelegt werden.")
            Text(RenovationReviewCalculator.NO_TAX_DECISION)
            error?.let { Text(it, color = CrimsonRed) }
        }
    }, confirmButton = { TextButton(onClick = {
        val parsed = if (net.isBlank()) null else GermanNumberInput.parseNonNegative(net)
        if (net.isNotBlank() && parsed == null) { error = "Bitte einen gültigen positiven Nettobetrag eingeben."; return@TextButton }
        if (parsed != null && parsed > kotlin.math.abs(receipt.bruttobetrag) + 0.01) { error = "Der Nettobetrag darf den Bruttobetrag nicht übersteigen."; return@TextButton }
        viewModel.saveRenovationRelation(RenovationReceiptRelation(receipt.internalId, receipt.propertyId, measureId, taxStatus, advisor, parsed), onDismiss)
    }, modifier = Modifier.testTag("renovation_assignment_save")) { Text("Zuordnung speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } })
}

@Composable
private fun RenovationMeasureDialog(viewModel: ReceiptViewModel, property: PropertyMetadata, initial: RenovationMeasure?, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val seed = remember { initial ?: RenovationMeasure(propertyId = property.propertyId, name = "") }
    val units = remember(property) { viewModel.getWohneinheitenForProperty(property) }
    var name by remember { mutableStateOf(seed.name) }; var description by remember { mutableStateOf(seed.description) }
    var unitId by remember { mutableStateOf(seed.unitId) }; var start by remember { mutableStateOf(seed.startDate) }
    var end by remember { mutableStateOf(seed.endDate) }; var status by remember { mutableStateOf(seed.status) }
    var taxStatus by remember { mutableStateOf(seed.taxStatus) }; var note by remember { mutableStateOf(seed.advisorNote) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "Maßnahme anlegen" else "Maßnahme bearbeiten") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Ui2.spacing)) {
            Text(property.name, fontWeight = FontWeight.Bold)
            OutlinedTextField(name, { name = it }, label = { Text("Name der Maßnahme") }, modifier = Modifier.fillMaxWidth().testTag("renovation_measure_name"))
            val choices = listOf("" to "Gesamtobjekt / Allgemein") + units.map { it.unitId to it.name }
            ReviewDropdown("Einheit (optional)", choices.firstOrNull { it.first == unitId }, choices, { it.second }) { unitId = it.first }
            OutlinedTextField(description, { description = it }, label = { Text("Beschreibung (optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(start, { start = it }, label = { Text("Startdatum JJJJ-MM-TT (optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(end, { end = it }, label = { Text("Enddatum JJJJ-MM-TT (optional)") }, modifier = Modifier.fillMaxWidth())
            ReviewDropdown("Maßnahmenstatus", status, RenovationStatus.entries, { it.label }) { status = it }
            ReviewDropdown("Steuerlicher Prüfstatus", taxStatus, RenovationTaxStatus.entries, { it.label }) { taxStatus = it }
            Text("Belege werden einzeln bewusst vorgemerkt. Dieser Status erteilt keine DATEV-Freigabe.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(note, { note = it }, label = { Text("Steuerberater-Notiz (optional)") }, modifier = Modifier.fillMaxWidth().testTag("renovation_measure_note"))
            error?.let { Text(it, color = CrimsonRed) }
        }
    }, confirmButton = { TextButton(onClick = {
        val measure = seed.copy(name = name, description = description, unitId = unitId, startDate = start.trim(), endDate = end.trim(), status = status, taxStatus = taxStatus, advisorNote = note)
        runCatching { RenovationReviewStore.validateMeasure(measure) }.onSuccess { viewModel.saveRenovationMeasure(measure, onSaved) }.onFailure { error = it.message }
    }, modifier = Modifier.testTag("renovation_measure_save")) { Text("Speichern") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } })
}

@Composable
private fun RenovationReceiptPicker(viewModel: ReceiptViewModel, measure: RenovationMeasure, onDismiss: () -> Unit, onSelect: (Receipt) -> Unit) {
    val receipts by viewModel.receipts.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Beleg wählen") }, text = {
        Column {
            OutlinedTextField(query, { query = it }, label = { Text("Beleg suchen") }, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(receipts.filter { it.propertyId == measure.propertyId && it.internalId.isNotBlank() &&
                    (measure.unitId.isBlank() || it.unitId == measure.unitId) && (query.isBlank() || "${it.aussteller} ${it.beschreibung} ${it.getEffectiveDisplayId()}".contains(query, true)) }, key = { it.id }) { receipt ->
                    TextButton(onClick = { onSelect(receipt) }, modifier = Modifier.fillMaxWidth().testTag("renovation_pick_receipt_${receipt.id}")) { Text("${receipt.aussteller}\n${receipt.getEffectiveDisplayId()} · ${receiptDisplayDate(receipt.datum)}") }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } })
}

@Composable
private fun RenovationEvidenceDialog(viewModel: ReceiptViewModel, measure: RenovationMeasure, documents: List<ManagedDocument>, onDismiss: () -> Unit) {
    var role by remember { mutableStateOf(RenovationEvidenceRole.RECHNUNG) }
    var evidence by remember { mutableStateOf(measure.evidence) }
    var query by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Vorhandene Nachweise") }, text = {
        Column {
            Text("Originale bleiben in der bestehenden Dokumentenakte. Nur die Referenz wird gespeichert.")
            ReviewDropdown("Rolle für neue Auswahl", role, RenovationEvidenceRole.entries, { it.label }) { role = it }
            OutlinedTextField(query, { query = it }, label = { Text("Dokument suchen") }, modifier = Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(documents.filter { it.propertyId == measure.propertyId && (query.isBlank() || it.originalFilename.contains(query, true)) }, key = { it.documentId }) { doc ->
                    val current = evidence.firstOrNull { it.documentId == doc.documentId }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(current != null, { checked -> evidence = evidence.filterNot { it.documentId == doc.documentId } + if (checked) listOf(RenovationEvidence(doc.documentId, role)) else emptyList() })
                        Text("${doc.originalFilename}\n${current?.role?.label.orEmpty()}", Modifier.weight(1f))
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { viewModel.saveRenovationMeasure(measure.copy(evidence = evidence), onDismiss) }) { Text("Referenzen speichern") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } })
}

@Composable
private fun RenovationReportDialog(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Steuerberater-Bericht erstellt") },
        text = { Text("Separate PDF-Prüfübersicht mit Maßnahmen, Belegen, Gutschriften und Notizen. Keine Buchungsdatei und keine Steuerentscheidung.") },
        confirmButton = { TextButton(onClick = { PdfExporter.sharePdf(context, file) }) { Text("Bericht teilen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } })
}

@Composable
internal fun RenovationPortfolioScreen(viewModel: ReceiptViewModel, onBack: () -> Unit) {
    val properties by viewModel.properties.collectAsStateWithLifecycle()
    var propertyId by rememberSaveable { mutableStateOf<String?>(null) }
    val property = properties.firstOrNull { it.propertyId == propertyId }
    if (property != null) { RenovationReviewScreen(viewModel, property) { propertyId = null }; return }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        ReviewHeader("Sanierung & 15%-Prüfung", onBack)
        LazyColumn(contentPadding = PaddingValues(Ui2.padding), verticalArrangement = Arrangement.spacedBy(Ui2.spacing)) {
            if (properties.isEmpty()) item { Text("Bitte zuerst eine Immobilie anlegen.") }
            items(properties.filter { it.status != "Archiviert" }, key = { it.propertyId }) { item ->
                Ui2Destination(item.name, item.adresse, Icons.Default.Build) { propertyId = item.propertyId }
            }
        }
    }
}
