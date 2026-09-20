package com.example.ui

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.example.data.DocumentFieldDecision
import com.example.data.DocumentFieldProposal
import com.example.data.ManagedDocument
import com.example.data.ManagedDocumentType
import java.io.File

@Composable
fun DocumentManagementScreen(
    viewModel: ReceiptViewModel,
    propertyScoped: Boolean = false,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val documents by viewModel.managedDocuments.collectAsState()
    val operationStatus by viewModel.documentOperationStatus.collectAsState()
    val duplicate by viewModel.pendingDocumentDuplicate.collectAsState()
    val aiReview by viewModel.documentAiReview.collectAsState()
    val migrationPreview by viewModel.documentMigrationPreview.collectAsState()
    val units by viewModel.wohneinheitenStatus.collectAsState()
    val property by viewModel.propertyMetadata.collectAsState()
    var query by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf("Alle") }
    var importUnitId by remember { mutableStateOf("") }
    var importUnitMenuOpen by remember { mutableStateOf(false) }
    var assignmentRequest by remember { mutableStateOf<String?>(null) }
    var selectedDocumentId by remember { mutableStateOf<String?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            viewModel.importManagedDocument(it, importUnitId.ifBlank { null })
        }
    }
    val mlKitScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pageUris = scanResult?.pages?.map { it.imageUri }.orEmpty()
            if (pageUris.isEmpty()) {
                viewModel.setDocumentOperationStatus("Der Scan wurde ohne Dokument abgeschlossen.")
            } else {
                pageUris.forEach { pageUri -> viewModel.importManagedDocument(pageUri, importUnitId.ifBlank { null }) }
            }
        }
    }
    val startDocumentScanner: () -> Unit = {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(10)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        if (activity != null) {
            GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                .addOnSuccessListener { intentSender -> mlKitScannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build()) }
                .addOnFailureListener { error ->
                    Log.e("DocumentScanner", "ML Kit scanner could not start", error)
                    viewModel.setDocumentOperationStatus("Scanner konnte nicht gestartet werden. Bitte Datei importieren.")
                }
        } else {
            viewModel.setDocumentOperationStatus("Scanner ist in dieser Ansicht nicht verfügbar.")
        }
        Unit
    }

    val propertyFilter = if (propertyScoped) property?.propertyId.orEmpty() else ""
    val propertyDocuments = documents.filter { propertyFilter.isBlank() || it.propertyId == propertyFilter }
    val shown = propertyDocuments.filter { document ->
        val matchesQuery = query.isBlank() || listOf(document.title, document.originalFilename, document.ocrText)
            .any { it.contains(query, ignoreCase = true) }
        val typeLabel = documentTypeLabel(document)
        val matchesFilter = when (activeFilter) {
            "Exposés" -> typeLabel == "Exposé"
            "Mietverträge" -> typeLabel == "Mietvertrag"
            "Rechnungen" -> typeLabel == "Rechnung"
            "Unterlagen" -> typeLabel !in setOf("Exposé", "Mietvertrag", "Rechnung")
            else -> true
        }
        matchesQuery && matchesFilter
    }

    val selectedDocument = selectedDocumentId?.let { id -> documents.firstOrNull { it.documentId == id } }
    if (selectedDocument != null) {
        BackHandler { selectedDocumentId = null }
        ManagedDocumentDetailScreen(
            document = selectedDocument,
            property = property,
            units = units,
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(Ui2.spacing)
    ) {
        if (propertyScoped && onBack != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = DarkNavy)
                    }
                    Text("Dokumentenakte", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                }
            }
        } else {
            item { Text("Dokumentenakte", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DarkNavy) }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (propertyScoped) property?.name ?: "Immobilie" else "Alle Immobilien",
                    modifier = Modifier.weight(1f),
                    fontSize = 13.sp,
                    color = SlateGray
                )
                OutlinedButton(onClick = { activeFilter = "Alle" }, shape = Ui2.controlShape) { Text("Alle Dokumente") }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                border = BorderStroke(1.dp, BorderColor),
                shape = Ui2.shape
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(Ui2.padding),
                    horizontalArrangement = Arrangement.spacedBy(Ui2.spacing)
                ) {
                    OutlinedButton(
                        onClick = { assignmentRequest = "import" },
                        modifier = Modifier.weight(1f).testTag("document_import_button"),
                        shape = Ui2.controlShape
                    ) { Icon(Icons.Default.UploadFile, null); Text(" Importieren") }
                    Button(
                        onClick = { assignmentRequest = "scan" },
                        modifier = Modifier.weight(1f).testTag("document_scan_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        shape = Ui2.controlShape
                    ) { Icon(Icons.Default.Description, null); Text(" Scannen") }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Dokumente durchsuchen") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = Ui2.controlShape,
                modifier = Modifier.fillMaxWidth().testTag("document_search_query")
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Ui2.spacing)
            ) {
                listOf("Alle", "Exposés", "Mietverträge", "Rechnungen", "Unterlagen").forEach { label ->
                    if (activeFilter == label) {
                        Button(
                            onClick = { activeFilter = label },
                            shape = Ui2.controlShape,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text(label, fontSize = 10.sp) }
                    } else {
                        OutlinedButton(
                            onClick = { activeFilter = label },
                            shape = Ui2.controlShape,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text(label, fontSize = 10.sp) }
                    }
                }
            }
        }
        operationStatus?.let { status ->
            item { Text(status, fontSize = 11.sp, color = SlateGray) }
        }
        if (shown.isEmpty()) {
            item { Text("Noch keine Dokumente vorhanden.", color = SlateGray) }
        } else {
            items(shown, key = { it.documentId }) { document ->
                ManagedDocumentCard(
                    document = document,
                    property = property,
                    units = units,
                    onClick = { selectedDocumentId = document.documentId }
                )
            }
        }
    }

    assignmentRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { assignmentRequest = null },
            shape = Ui2.shape,
            title = { Text("Dokument zuordnen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Wähle, ob das Dokument zum Gesamtobjekt oder zu einer Wohnung gehört.", fontSize = 12.sp, color = SlateGray)
                    OutlinedButton(onClick = { importUnitMenuOpen = true }, modifier = Modifier.fillMaxWidth(), shape = Ui2.controlShape) {
                        Text(if (importUnitId.isBlank()) "Gesamtobjekt" else units.firstOrNull { PropertyUnitScopedData.stableUnitId(property?.propertyId.orEmpty(), it) == importUnitId }?.label ?: "Wohneinheit")
                    }
                    DropdownMenu(expanded = importUnitMenuOpen, onDismissRequest = { importUnitMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("Gesamtobjekt") }, onClick = { importUnitId = ""; importUnitMenuOpen = false })
                        units.forEach { unit ->
                            val stableId = PropertyUnitScopedData.stableUnitId(property?.propertyId.orEmpty(), unit)
                            DropdownMenuItem(text = { Text(unit.label.ifBlank { unit.name }) }, onClick = { importUnitId = stableId; importUnitMenuOpen = false })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    assignmentRequest = null
                    if (request == "import") {
                        launcher.launch(arrayOf("*/*"))
                    } else {
                        startDocumentScanner()
                    }
                }, shape = Ui2.controlShape) { Text(if (request == "import") "Datei auswählen" else "Scanner starten") }
            },
            dismissButton = { TextButton(onClick = { assignmentRequest = null }) { Text("Abbrechen") } }
        )
    }
    duplicate?.let { pair ->
        AlertDialog(
            onDismissRequest = { viewModel.resolvePossibleDocumentDuplicate(false) },
            shape = Ui2.shape,
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
            shape = Ui2.shape,
            title = { Text("Dokumentenablage aktualisieren") },
            text = { Text("Insgesamt geprüft: ${preview.found}\nKorrekt abgelegt: ${preview.alreadyNew}\nLegacy-Struktur: ${preview.inventoryItems.count { it.status == com.example.data.DriveDocumentInventoryStatus.LEGACY_LAYOUT }}\nWerden verschoben: ${preview.willMove}\nWerden umbenannt: ${preview.willRename}\nMögliche Dubletten: ${preview.possibleDuplicates}\nVerwaiste App-Dateien: ${preview.orphanedAppFiles}\nFehlende lokale Referenzen: ${preview.missingLocalReferences}\nIndex-Konflikte: ${preview.indexConflicts}\nManuell prüfen: ${preview.manualReview}\n\nEs werden keine Originale gelöscht oder kopiert. Unklare Dateien bleiben unverändert. Drive-Datei-ID und Inhalt werden nach jeder bestätigten Änderung geprüft.") },
            confirmButton = { Button(onClick = viewModel::confirmDocumentStorageMigration, modifier = Modifier.testTag("confirm_document_migration"), shape = Ui2.controlShape) { Text("Migration bestätigen") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDocumentMigrationPreview) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun DocumentDetailDialog(document: ManagedDocument, viewModel: ReceiptViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Ui2.shape,
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
                OutlinedButton(onClick = { viewModel.runDocumentOcr(document.documentId) }, modifier = Modifier.fillMaxWidth(), shape = Ui2.controlShape) { Text("OCR erneut ausführen") }
                Button(onClick = { viewModel.analyzeManagedDocument(document.documentId) }, modifier = Modifier.fillMaxWidth(), shape = Ui2.controlShape) { Icon(Icons.Default.AutoAwesome, null); Text(" Erneut analysieren") }
                OutlinedButton(onClick = {
                    val file = File(document.localUri)
                    if (file.exists()) {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, document.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }
                }, modifier = Modifier.fillMaxWidth(), shape = Ui2.controlShape) { Text("Original öffnen") }
                if (!File(document.localUri).isFile && !document.driveFileId.isNullOrBlank()) {
                    OutlinedButton(onClick = { viewModel.downloadManagedDocument(document.documentId) }, modifier = Modifier.fillMaxWidth(), shape = Ui2.controlShape) { Text("Original aus Drive laden") }
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
        shape = Ui2.shape,
        title = { Text("Erkannte Daten prüfen") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("KI-Vorschlag ${(result.confidence * 100).toInt()} %. Nichts wird ohne Auswahl übernommen.", fontSize = 11.sp, color = SlateGray)
                OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth(), shape = Ui2.controlShape) { Text("Dokumenttyp: ${selectedType.name}") }
                DropdownMenu(typeMenu, { typeMenu = false }) { ManagedDocumentType.entries.forEach { type -> DropdownMenuItem({ Text(type.name) }, { selectedType = type; typeMenu = false }) } }
                OutlinedTextField(selectedDate, { selectedDate = it }, label = { Text("Dokumentdatum") }, modifier = Modifier.fillMaxWidth(), shape = Ui2.controlShape)
                OutlinedTextField(selectedUnitId, { selectedUnitId = it }, label = { Text("Wohneinheit-ID (optional)") }, modifier = Modifier.fillMaxWidth(), shape = Ui2.controlShape)
                proposals.forEachIndexed { index, proposal ->
                    Card(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, BorderColor), shape = Ui2.shape) {
                        Column(Modifier.padding(Ui2.padding), verticalArrangement = Arrangement.spacedBy(Ui2.spacing)) {
                            Text(proposal.label, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Erkannt: ${proposal.detectedValue} • ${(proposal.confidence * 100).toInt()} %${proposal.sourcePage.takeIf(String::isNotBlank)?.let { " • $it" }.orEmpty()}", fontSize = 10.sp)
                            if (proposal.currentValue.isNotBlank()) Text("Aktuell: ${proposal.currentValue}", fontSize = 10.sp, color = SlateGray)
                            OutlinedTextField(proposal.editedValue ?: proposal.detectedValue, { value -> proposals = proposals.toMutableList().also { it[index] = proposal.copy(editedValue = value) } }, label = { Text("Geprüfter Wert") }, modifier = Modifier.fillMaxWidth(), shape = Ui2.controlShape)
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
        confirmButton = { Button(onClick = { viewModel.confirmManagedDocumentReview(document.documentId, selectedType, selectedDate, selectedUnitId.ifBlank { null }, proposals) }, modifier = Modifier.testTag("confirm_document_ai_review"), shape = Ui2.controlShape) { Text("Geprüfte Werte übernehmen") } },
        dismissButton = { TextButton(onClick = viewModel::dismissDocumentAiReview) { Text("Abbrechen") } }
    )
}
