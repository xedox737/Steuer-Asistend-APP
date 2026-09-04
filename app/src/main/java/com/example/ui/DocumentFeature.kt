package com.example.ui

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.example.data.DocumentFieldDecision
import com.example.data.ManagedDocument
import com.example.data.ManagedDocumentType
import java.io.File

@Composable
fun DocumentManagementScreen(viewModel: ReceiptViewModel) {
    val context = LocalContext.current
    val documents by viewModel.managedDocuments.collectAsState()
    val results by viewModel.documentSearchResults.collectAsState()
    val operationStatus by viewModel.documentOperationStatus.collectAsState()
    val duplicate by viewModel.pendingDocumentDuplicate.collectAsState()
    val aiReview by viewModel.documentAiReview.collectAsState()
    val migrationPreview by viewModel.documentMigrationPreview.collectAsState()
    val units by viewModel.wohneinheitenStatus.collectAsState()
    val property by viewModel.propertyMetadata.collectAsState()
    var query by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("") }
    var unitFilter by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ManagedDocument?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importManagedDocument(it) }
    }
    var cameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraUri?.let { viewModel.importManagedDocument(it) }
    }
    val startCameraScan = {
        val target = File(context.cacheDir, "document_scan_${System.currentTimeMillis()}.jpg")
        val scanUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", target)
        cameraUri = scanUri
        cameraLauncher.launch(scanUri)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCameraScan()
    }
    val shown = if (hasSearched) results else documents

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Dokumentenakte", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                Text(property?.name ?: "Immobilie", fontSize = 11.sp, color = SlateGray)
            }
            Button(
                onClick = { launcher.launch(arrayOf("application/pdf", "image/*", "text/plain")) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                modifier = Modifier.testTag("document_import_button")
            ) { Icon(Icons.Default.UploadFile, null); Text(" Importieren") }
        }
        OutlinedButton(onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCameraScan()
            else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }, modifier = Modifier.fillMaxWidth().testTag("document_scan_button")) { Text("Dokument scannen") }
        OutlinedTextField(query, { query = it }, label = { Text("Dokumente und OCR-Text durchsuchen") }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth().testTag("document_search_query"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Jahr") }, modifier = Modifier.weight(1f))
            OutlinedTextField(unitFilter, { unitFilter = it }, label = { Text("Unit-ID") }, modifier = Modifier.weight(1f))
            OutlinedTextField(typeFilter, { typeFilter = it.uppercase() }, label = { Text("Typ") }, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(categoryFilter, { categoryFilter = it }, label = { Text("Dokumentbereich/Kategorie") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            hasSearched = true
            viewModel.searchDocuments(query, property?.propertyId.orEmpty(), unitFilter, year, typeFilter, categoryFilter)
        }, modifier = Modifier.fillMaxWidth().testTag("document_search_button")) { Text("Suchen") }
        OutlinedButton(onClick = viewModel::previewDocumentStorageMigration, modifier = Modifier.fillMaxWidth().testTag("document_migration_preview")) {
            Text("Bestehende Drive-Ablage prüfen")
        }
        operationStatus?.let { Text(it, fontSize = 11.sp, color = SlateGray) }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (shown.isEmpty()) item { Text("Keine Dokumente gefunden.", color = SlateGray) }
            items(shown, key = { it.documentId }) { document ->
                Card(
                    Modifier.fillMaxWidth().clickable { selected = document }.testTag("document_${document.documentId}"),
                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, null, tint = AccentBlue)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(document.title.ifBlank { document.storedFilename }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("${document.documentType} • ${document.documentDate} • ${document.reviewStatus}", fontSize = 10.sp, color = SlateGray)
                            if (document.ocrText.isNotBlank()) Text(document.ocrText.replace('\n', ' ').take(120), fontSize = 10.sp, color = SlateGray, maxLines = 2)
                        }
                    }
                }
            }
        }
    }

    selected?.let { document -> DocumentDetailDialog(document, viewModel, { selected = null }) }
    duplicate?.let { pair ->
        AlertDialog(
            onDismissRequest = { viewModel.resolvePossibleDocumentDuplicate(false) },
            title = { Text("Mögliche Dublette gefunden") },
            text = { Text("${pair.first.originalFilename} ähnelt dem vorhandenen Dokument ${pair.second.title}. Das Original wird nicht automatisch dupliziert.") },
            confirmButton = { TextButton(onClick = { viewModel.resolvePossibleDocumentDuplicate(true) }) { Text("Vorhandenes verwenden") } },
            dismissButton = { Row { TextButton(onClick = { viewModel.resolvePossibleDocumentDuplicate(false, true) }) { Text("Separat übernehmen") }; TextButton(onClick = { viewModel.resolvePossibleDocumentDuplicate(false) }) { Text("Abbrechen") } } }
        )
    }
    aiReview?.let { (documentId, result) ->
        val document = documents.firstOrNull { it.documentId == documentId }
        if (document != null) DocumentAiReviewDialog(document, result, units, property, viewModel)
    }
    migrationPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDocumentMigrationPreview,
            title = { Text("Dokumentenablage aktualisieren") },
            text = { Text("Gefunden: ${preview.found}\nBereits neue Struktur: ${preview.alreadyNew}\nWerden verschoben: ${preview.willMove}\nWerden umbenannt: ${preview.willRename}\nBleiben unverändert: ${preview.unchanged}\nMögliche Dubletten: ${preview.possibleDuplicates}\nKonflikte: ${preview.conflicts}\nManuell prüfen: ${preview.review}\n\nEs werden keine Originale gelöscht oder kopiert. Die Drive-Datei-ID und der Inhalt bleiben erhalten und werden nach jeder Änderung geprüft.") },
            confirmButton = { Button(onClick = viewModel::confirmDocumentStorageMigration, modifier = Modifier.testTag("confirm_document_migration")) { Text("Migration bestätigen") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDocumentMigrationPreview) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun DocumentDetailDialog(document: ManagedDocument, viewModel: ReceiptViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(document.title.ifBlank { "Dokumentdetail" }) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                DetailRow("Dokumenttyp", document.documentType); DetailRow("Datum", document.documentDate)
                DetailRow("Immobilien-ID", document.propertyId); DetailRow("Wohneinheit-ID", document.unitId ?: "–")
                DetailRow("Speicherort", document.driveFolderId ?: "Nur lokal")
                DetailRow("OCR", document.ocrStatus); DetailRow("KI-Analyse", document.aiAnalysisStatus)
                DetailRow("Confidence", "${(document.aiConfidence * 100).toInt()} %"); DetailRow("Prüfstatus", document.reviewStatus)
                if (document.extractedFieldsJson.isNotBlank()) { HorizontalDivider(); Text("Extrahierte Daten", fontWeight = FontWeight.Bold); Text(document.extractedFieldsJson, fontSize = 10.sp) }
                if (document.ocrText.isNotBlank()) { HorizontalDivider(); Text("Erkannter Text", fontWeight = FontWeight.Bold); Text(document.ocrText.take(4000), fontSize = 10.sp) }
                OutlinedButton(onClick = { viewModel.runDocumentOcr(document.documentId) }, modifier = Modifier.fillMaxWidth()) { Text("OCR erneut ausführen") }
                Button(onClick = { viewModel.analyzeManagedDocument(document.documentId) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.AutoAwesome, null); Text(" Erneut analysieren") }
                OutlinedButton(onClick = {
                    val file = File(document.localUri)
                    if (file.exists()) {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, document.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Original öffnen") }
                if (!File(document.localUri).isFile && !document.driveFileId.isNullOrBlank()) {
                    OutlinedButton(onClick = { viewModel.downloadManagedDocument(document.documentId) }, modifier = Modifier.fillMaxWidth()) { Text("Original aus Drive laden") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}

@Composable
private fun DocumentAiReviewDialog(document: ManagedDocument, result: com.example.api.ManagedDocumentAiResult, units: List<WohneinheitStatus>, property: com.example.data.PropertyMetadata?, viewModel: ReceiptViewModel) {
    val currentUnit = units.firstOrNull { it.unitId == (result.suggestedUnitId.ifBlank { document.unitId.orEmpty() }) }
    val currentValues = mapOf(
        "objektadresse" to property?.adresse.orEmpty(), "kaufpreis" to (property?.gesamtKaufpreis?.toString() ?: ""),
        "kaufvertragsdatum" to property?.notariellesKaufdatum.orEmpty(), "nutzen_lasten" to property?.uebergangNutzenLasten.orEmpty(),
        "grundstuecksflaeche" to (property?.grundstuecksgroesse?.toString() ?: ""), "baujahr" to (property?.baujahr?.toString() ?: ""),
        "mieter" to currentUnit?.mieter.orEmpty(), "kaltmiete" to (currentUnit?.kaltmiete?.toString() ?: ""),
        "wohnflaeche" to (currentUnit?.wohnflaeche?.toString() ?: ""), "vertragsbeginn" to currentUnit?.mietvertragsstart.orEmpty()
    )
    var proposals by remember(result, property, currentUnit) { mutableStateOf(result.reviewFields(currentValues)) }
    var selectedType by remember(result) { mutableStateOf(runCatching { ManagedDocumentType.valueOf(result.documentType) }.getOrDefault(ManagedDocumentType.SONSTIGES)) }
    var selectedDate by remember(result) { mutableStateOf(result.documentDate.ifBlank { document.documentDate }) }
    var selectedUnitId by remember(result) { mutableStateOf(result.suggestedUnitId.ifBlank { document.unitId.orEmpty() }) }
    var typeMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = viewModel::dismissDocumentAiReview,
        title = { Text("Erkannte Daten prüfen") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("KI-Vorschlag ${(result.confidence * 100).toInt()} %. Nichts wird ohne Auswahl übernommen.", fontSize = 11.sp, color = SlateGray)
                OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Dokumenttyp: ${selectedType.name}") }
                DropdownMenu(typeMenu, { typeMenu = false }) { ManagedDocumentType.entries.forEach { type -> DropdownMenuItem({ Text(type.name) }, { selectedType = type; typeMenu = false }) } }
                OutlinedTextField(selectedDate, { selectedDate = it }, label = { Text("Dokumentdatum") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(selectedUnitId, { selectedUnitId = it }, label = { Text("Wohneinheit-ID (optional)") }, modifier = Modifier.fillMaxWidth())
                proposals.forEachIndexed { index, proposal ->
                    Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, BorderColor)) {
                        Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(proposal.label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Erkannt: ${proposal.detectedValue} • ${(proposal.confidence * 100).toInt()} %${proposal.sourcePage.takeIf(String::isNotBlank)?.let { " • $it" }.orEmpty()}", fontSize = 10.sp)
                            if (proposal.currentValue.isNotBlank()) Text("Aktuell: ${proposal.currentValue}", fontSize = 10.sp, color = SlateGray)
                            OutlinedTextField(proposal.editedValue ?: proposal.detectedValue, { value -> proposals = proposals.toMutableList().also { it[index] = proposal.copy(editedValue = value) } }, label = { Text("Geprüfter Wert") }, modifier = Modifier.fillMaxWidth())
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(proposal.decision == DocumentFieldDecision.UEBERNEHMEN, { checked -> proposals = proposals.toMutableList().also { it[index] = proposal.copy(decision = if (checked) DocumentFieldDecision.UEBERNEHMEN else DocumentFieldDecision.AUSSTEHEND) } })
                                Text("Übernehmen", fontSize = 11.sp)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { proposals = proposals.toMutableList().also { it[index] = proposal.copy(decision = DocumentFieldDecision.IGNORIEREN) } }) { Text("Ignorieren") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { viewModel.confirmManagedDocumentReview(document.documentId, selectedType, selectedDate, selectedUnitId.ifBlank { null }, proposals) }, modifier = Modifier.testTag("confirm_document_ai_review")) { Text("Geprüfte Werte übernehmen") } },
        dismissButton = { TextButton(onClick = viewModel::dismissDocumentAiReview) { Text("Abbrechen") } }
    )
}
