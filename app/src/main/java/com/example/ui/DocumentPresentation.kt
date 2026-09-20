package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.DocumentReviewStatus
import com.example.data.ManagedDocument
import com.example.data.ManagedDocumentType
import com.example.data.PropertyMetadata
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private data class DocumentPreviewState(val bitmap: Bitmap? = null, val pageCount: Int = 0)

@Composable
internal fun ManagedDocumentCard(
    document: ManagedDocument,
    property: PropertyMetadata?,
    units: List<WohneinheitStatus>,
    onClick: () -> Unit
) {
    val assignment = documentAssignmentLabel(document, property, units)
    val title = documentDisplayTitle(document, property)
    val description = documentDisplayDescription(document, property, assignment)
    val typeLabel = documentTypeLabel(document)
    val driveSynced = !document.driveFileId.isNullOrBlank() && document.migrationStatus == "SYNCED"

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = Ui2.shape,
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(
            Modifier.padding(Ui2.padding),
            horizontalArrangement = Arrangement.spacedBy(Ui2.spacing),
            verticalAlignment = Alignment.Top
        ) {
            DocumentPreview(document, Modifier.width(86.dp).height(112.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = DarkNavy,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(Icons.Default.MoreVert, null, tint = SlateGray, modifier = Modifier.size(19.dp))
                }
                Text(
                    description,
                    color = SlateGray,
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Description, null, tint = SlateGray, modifier = Modifier.size(15.dp))
                    Text(typeLabel, fontSize = 10.sp, color = SlateGray, maxLines = 1)
                    Text("·", color = SlateGray)
                    Icon(Icons.Default.Home, null, tint = SlateGray, modifier = Modifier.size(15.dp))
                    Text(assignment, fontSize = 10.sp, color = SlateGray, maxLines = 1)
                    Text("·", color = SlateGray)
                    Text(document.documentDate.ifBlank { "–" }, fontSize = 10.sp, color = SlateGray)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (driveSynced) Icons.Default.Cloud else Icons.Default.Sync,
                        null,
                        tint = if (driveSynced) androidx.compose.ui.graphics.Color(0xFF0A9B55) else AccentBlue,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        if (driveSynced) " In Drive gesichert" else if (!document.driveFileId.isNullOrBlank()) " Drive wird aktualisiert" else " Nur lokal",
                        color = if (driveSynced) androidx.compose.ui.graphics.Color(0xFF0A9B55) else AccentBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    StatusChip(
                        if (document.reviewStatus == DocumentReviewStatus.GEPRUEFT.name ||
                            document.reviewStatus == DocumentReviewStatus.UEBERNOMMEN.name) "Geprüft" else "Neu",
                        document.reviewStatus == DocumentReviewStatus.GEPRUEFT.name ||
                            document.reviewStatus == DocumentReviewStatus.UEBERNOMMEN.name
                    )
                }
            }
        }
    }
}

@Composable
internal fun ManagedDocumentDetailScreen(
    document: ManagedDocument,
    property: PropertyMetadata?,
    units: List<WohneinheitStatus>,
    onBack: () -> Unit,
    onAnalyze: () -> Unit,
    onDownload: () -> Unit,
    onSync: () -> Unit,
    onUpdatePresentation: (String, String) -> Unit
) {
    val context = LocalContext.current
    val assignment = documentAssignmentLabel(document, property, units)
    val title = documentDisplayTitle(document, property)
    val description = documentDisplayDescription(document, property, assignment)
    val fields = remember(document.extractedFieldsJson, document.ocrText) { extractedDocumentFields(document) }
    val preview by rememberDocumentPreview(document)
    var showText by remember { mutableStateOf(false) }
    var editPresentation by remember { mutableStateOf(false) }
    val localAvailable = remember(document.localUri, document.updatedAt) { File(document.localUri).isFile }
    val driveSynced = !document.driveFileId.isNullOrBlank() && document.migrationStatus == "SYNCED"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(Ui2.spacing)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = DarkNavy)
                }
                Column(Modifier.weight(1f)) {
                    Text("Dokumentendetail", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text(property?.name ?: "Immobilie", fontSize = 12.sp, color = SlateGray)
                }
                Icon(Icons.Default.MoreVert, null, tint = DarkNavy)
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = Ui2.shape,
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFF5F8FD)),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Box(
                    Modifier.fillMaxWidth().height(260.dp).padding(Ui2.padding),
                    contentAlignment = Alignment.Center
                ) {
                    preview.bitmap?.let { bitmap ->
                        Image(
                            bitmap.asImageBitmap(),
                            "Dokumentvorschau",
                            modifier = Modifier.height(238.dp)
                                .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                                .clip(Ui2.controlShape),
                            contentScale = ContentScale.Fit
                        )
                    } ?: Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Description, null, tint = AccentBlue, modifier = Modifier.size(58.dp))
                        Text("Vorschau nicht verfügbar", color = SlateGray, fontSize = 12.sp)
                    }
                    if (preview.pageCount > 1) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd),
                            shape = Ui2.controlShape,
                            color = DarkNavy.copy(alpha = 0.72f)
                        ) {
                            Text(
                                "1 / \${preview.pageCount}",
                                color = androidx.compose.ui.graphics.Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Ui2.spacing)) {
                StatusChip(
                    if (document.reviewStatus == DocumentReviewStatus.GEPRUEFT.name ||
                        document.reviewStatus == DocumentReviewStatus.UEBERNOMMEN.name) "Geprüft" else "Prüfen",
                    document.reviewStatus == DocumentReviewStatus.GEPRUEFT.name ||
                        document.reviewStatus == DocumentReviewStatus.UEBERNOMMEN.name
                )
                DriveStatusChip(document)
            }
        }
        item {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            Text(description, fontSize = 13.sp, color = SlateGray)
        }
        item {
            DetailSection("Dokumentinformationen", Icons.Default.Description) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ui2.spacing)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Ui2.spacing)) {
                        MetadataLine("Dokumenttyp", documentTypeLabel(document))
                        MetadataLine("Datum", document.documentDate.ifBlank { "–" })
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Ui2.spacing)) {
                        MetadataLine("Zuordnung", assignment)
                        MetadataLine("Speicherort", when {
                            driveSynced && localAvailable -> "Drive + Lokal"
                            driveSynced -> "Drive"
                            else -> "Nur lokal"
                        })
                    }
                }
            }
        }
        item {
            DetailSection("Erkannte Eckdaten", Icons.Default.AutoAwesome) {
                if (fields.isEmpty()) {
                    Text(
                        "Noch keine strukturierten Eckdaten übernommen. Die KI-Analyse kann das Dokument erneut prüfen.",
                        color = SlateGray,
                        fontSize = 12.sp
                    )
                } else {
                    fields.take(8).chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ui2.spacing)) {
                            row.forEach { field ->
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = Ui2.controlShape,
                                    color = androidx.compose.ui.graphics.Color(0xFFF3F6FB)
                                ) {
                                    Column(Modifier.padding(Ui2.padding)) {
                                        Text(field.first, fontSize = 10.sp, color = SlateGray)
                                        Text(
                                            field.second,
                                            fontSize = 12.sp,
                                            color = DarkNavy,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item {
            DetailSection("Aktionen", Icons.Default.AutoAwesome) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ui2.spacing)) {
                    Button(
                        onClick = { if (localAvailable) openManagedDocument(context, document) else onDownload() },
                        modifier = Modifier.weight(1f),
                        shape = Ui2.controlShape,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                    ) {
                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp))
                        Text(" Original öffnen", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { if (localAvailable) openManagedDocument(context, document) else onDownload() },
                        modifier = Modifier.weight(1f),
                        shape = Ui2.controlShape
                    ) {
                        Icon(Icons.Default.Description, null, modifier = Modifier.size(18.dp))
                        Text(if (document.mimeType == "application/pdf") " PDF ansehen" else " Vorschau", fontSize = 11.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Ui2.spacing)) {
                    OutlinedButton(onClick = onAnalyze, modifier = Modifier.weight(1f), shape = Ui2.controlShape) {
                        Icon(Icons.Default.Sync, null, modifier = Modifier.size(18.dp))
                        Text(" Erneut analysieren", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { editPresentation = true },
                        modifier = Modifier.weight(1f),
                        shape = Ui2.controlShape
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                        Text(" Beschreibung", fontSize = 11.sp)
                    }
                }
                if (!driveSynced) {
                    OutlinedButton(onClick = onSync, modifier = Modifier.fillMaxWidth(), shape = Ui2.controlShape) {
                        Icon(Icons.Default.Cloud, null, modifier = Modifier.size(18.dp))
                        Text(" In Drive sichern")
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { showText = !showText },
                shape = Ui2.shape,
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(Modifier.padding(Ui2.padding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, null, tint = AccentBlue)
                        Text(" Erkannter Text", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = DarkNavy)
                        Icon(if (showText) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
                    }
                    Text(
                        if (showText) document.ocrText.ifBlank { "Noch kein Text erkannt." }
                        else document.ocrText.ifBlank { "Noch kein Text erkannt." }.replace("\\n", " ").take(140),
                        color = SlateGray,
                        fontSize = 11.sp,
                        maxLines = if (showText) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (editPresentation) {
        var editedTitle by remember(title) { mutableStateOf(title) }
        var editedDescription by remember(description) { mutableStateOf(description) }
        AlertDialog(
            onDismissRequest = { editPresentation = false },
            shape = Ui2.shape,
            title = { Text("Darstellung bearbeiten") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Ui2.spacing)) {
                    OutlinedTextField(
                        editedTitle, { editedTitle = it }, label = { Text("Titel") },
                        shape = Ui2.controlShape, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        editedDescription, { editedDescription = it }, label = { Text("Kurzbeschreibung") },
                        minLines = 3, shape = Ui2.controlShape, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdatePresentation(editedTitle.trim(), editedDescription.trim())
                        editPresentation = false
                    },
                    shape = Ui2.controlShape
                ) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { editPresentation = false }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun DetailSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = Ui2.shape,
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(Ui2.padding), verticalArrangement = Arrangement.spacedBy(Ui2.spacing)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = AccentBlue)
                Text(" $title", fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 15.sp)
            }
            content()
        }
    }
}

@Composable
private fun MetadataLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = SlateGray, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(value, color = DarkNavy, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusChip(text: String, positive: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (positive) androidx.compose.ui.graphics.Color(0xFFE2F7EA) else androidx.compose.ui.graphics.Color(0xFFE8F1FF)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (positive) Icons.Default.CheckCircle else Icons.Default.Description,
                null,
                tint = if (positive) androidx.compose.ui.graphics.Color(0xFF078B49) else AccentBlue,
                modifier = Modifier.size(14.dp)
            )
            Text(
                " $text",
                color = if (positive) androidx.compose.ui.graphics.Color(0xFF078B49) else AccentBlue,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DriveStatusChip(document: ManagedDocument) {
    val synced = !document.driveFileId.isNullOrBlank() && document.migrationStatus == "SYNCED"
    Surface(
        shape = CircleShape,
        color = if (synced) androidx.compose.ui.graphics.Color(0xFFE2F7EA) else androidx.compose.ui.graphics.Color(0xFFE8F1FF)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (synced) Icons.Default.Cloud else Icons.Default.Sync,
                null,
                tint = if (synced) androidx.compose.ui.graphics.Color(0xFF078B49) else AccentBlue,
                modifier = Modifier.size(14.dp)
            )
            Text(
                if (synced) " In Drive gesichert" else " Nur lokal",
                color = if (synced) androidx.compose.ui.graphics.Color(0xFF078B49) else AccentBlue,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DocumentPreview(document: ManagedDocument, modifier: Modifier = Modifier) {
    val preview by rememberDocumentPreview(document)
    Box(
        modifier.clip(Ui2.controlShape).background(androidx.compose.ui.graphics.Color(0xFFF3F6FB)),
        contentAlignment = Alignment.Center
    ) {
        preview.bitmap?.let { bitmap ->
            Image(bitmap.asImageBitmap(), "Dokumentvorschau", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } ?: Icon(Icons.Default.Description, null, tint = AccentBlue, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun rememberDocumentPreview(document: ManagedDocument) =
    produceState(DocumentPreviewState(), document.localUri, document.updatedAt, document.mimeType) {
        value = withContext(Dispatchers.IO) { loadPreview(document) }
    }

private fun loadPreview(document: ManagedDocument): DocumentPreviewState {
    val file = File(document.localUri)
    if (!file.isFile) return DocumentPreviewState()
    return try {
        if (document.mimeType == "application/pdf" || file.extension.equals("pdf", true)) {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(descriptor)
            try {
                if (renderer.pageCount == 0) return DocumentPreviewState()
                val page = renderer.openPage(0)
                try {
                    val width = 720
                    val height = (page.height * (width.toFloat() / page.width.toFloat())).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    DocumentPreviewState(bitmap, renderer.pageCount)
                } finally {
                    page.close()
                }
            } finally {
                renderer.close()
                descriptor.close()
            }
        } else if (document.mimeType.startsWith("image/")) {
            DocumentPreviewState(BitmapFactory.decodeFile(file.absolutePath), 1)
        } else DocumentPreviewState()
    } catch (_: Exception) {
        DocumentPreviewState()
    }
}

internal fun documentDisplayTitle(document: ManagedDocument, property: PropertyMetadata?): String {
    val raw = document.title.ifBlank { document.originalFilename.substringBeforeLast('.') }.trim()
    val type = documentTypeLabel(document)
    if (type == "Exposé" && (raw.startsWith("PDF-Exposé", true) || raw.matches(Regex(".*#\\d+.*")))) {
        val propertyName = property?.name?.substringBefore(" (")?.takeIf(String::isNotBlank)
        return listOfNotNull("Exposé", propertyName).joinToString(" ")
    }
    return raw.ifBlank { type }
}

internal fun documentDisplayDescription(document: ManagedDocument, property: PropertyMetadata?, assignment: String): String {
    customDocumentDescription(document)?.takeIf(String::isNotBlank)?.let { return it }
    val type = documentTypeLabel(document)
    val compactOcr = document.ocrText
        .replace(Regex("\\[Seite \\d+]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return when (type) {
        "Exposé" -> {
            val place = Regex("\\b\\d{5}\\s+[A-ZÄÖÜ][A-Za-zÄÖÜäöüß-]+").find(compactOcr)?.value
            val objectType = when {
                compactOcr.contains("Mehrfamilienhaus", true) -> "Mehrfamilienhaus"
                compactOcr.contains("Einfamilienhaus", true) -> "Einfamilienhaus"
                else -> property?.name?.substringBefore(" (") ?: "Immobilie"
            }
            buildString {
                append(objectType)
                place?.let { append(" in $it") }
                append(". Objekt- und Eckdaten erkannt.")
            }
        }
        "Mietvertrag" -> "Mietvertrag für $assignment mit erkannten Vertrags- und Mietdaten."
        "Rechnung" -> "Rechnung mit erkanntem Inhalt, Rechnungsdatum und relevanten Angaben."
        "Energieausweis" -> "Energieausweis mit erkannten Gebäude- und Energiedaten."
        "Grundriss" -> "Grundriss bzw. Planunterlage für $assignment."
        else -> compactOcr.take(150).takeIf(String::isNotBlank) ?: ("Dokument für " + (property?.name ?: "diese Immobilie") + ".")
    }
}

internal fun documentTypeLabel(document: ManagedDocument): String {
    val text = document.title + " " + document.originalFilename + " " + document.ocrText.take(600)
    if (text.contains("exposé", true) || text.contains("expose", true)) return "Exposé"
    return when (runCatching { ManagedDocumentType.valueOf(document.documentType) }.getOrDefault(ManagedDocumentType.SONSTIGES)) {
        ManagedDocumentType.KAUFVERTRAG -> "Kaufvertrag"
        ManagedDocumentType.NOTARUNTERLAGE -> "Notarunterlage"
        ManagedDocumentType.GRUNDBUCHAUSZUG -> "Grundbuchauszug"
        ManagedDocumentType.ENERGIEAUSWEIS -> "Energieausweis"
        ManagedDocumentType.MIETVERTRAG -> "Mietvertrag"
        ManagedDocumentType.UEBERGABEPROTOKOLL -> "Übergabeprotokoll"
        ManagedDocumentType.DARLEHENSVERTRAG -> "Darlehensvertrag"
        ManagedDocumentType.ZINSBESCHEINIGUNG -> "Zinsbescheinigung"
        ManagedDocumentType.VERSICHERUNGSPOLICE -> "Versicherung"
        ManagedDocumentType.GRUNDSTEUERDOKUMENT -> "Grundsteuer"
        ManagedDocumentType.KAUFPREISAUFTEILUNG -> "Kaufpreisaufteilung"
        ManagedDocumentType.RECHNUNG -> "Rechnung"
        ManagedDocumentType.KASSENBON -> "Kassenbon"
        ManagedDocumentType.SANIERUNGSUNTERLAGE -> "Sanierungsunterlage"
        ManagedDocumentType.BAUUNTERLAGE -> "Bauunterlage"
        ManagedDocumentType.GRUNDRISS -> "Grundriss"
        ManagedDocumentType.WOHNFLAECHENBERECHNUNG -> "Wohnflächenberechnung"
        ManagedDocumentType.PV_UNTERLAGE -> "PV-Unterlage"
        ManagedDocumentType.SONSTIGES -> "Unterlage"
    }
}

internal fun documentAssignmentLabel(document: ManagedDocument, property: PropertyMetadata?, units: List<WohneinheitStatus>): String {
    val id = document.unitId?.takeIf(String::isNotBlank) ?: return "Gesamtobjekt"
    return units.firstOrNull {
        it.unitId == id || PropertyUnitScopedData.stableUnitId(property?.propertyId.orEmpty(), it) == id
    }?.label?.takeIf(String::isNotBlank)
        ?: units.firstOrNull { it.unitId == id }?.name
        ?: "Wohneinheit"
}

private fun customDocumentDescription(document: ManagedDocument): String? = runCatching {
    JSONObject(document.extractedFieldsJson.ifBlank { "{}" }).optString("_displayDescription").takeIf(String::isNotBlank)
}.getOrNull()

private fun extractedDocumentFields(document: ManagedDocument): List<Pair<String, String>> {
    val json = runCatching { JSONObject(document.extractedFieldsJson.ifBlank { "{}" }) }.getOrNull() ?: return emptyList()
    val result = mutableListOf<Pair<String, String>>()
    val fields = json.optJSONArray("fields")
    if (fields != null) {
        for (index in 0 until fields.length()) {
            val field = fields.optJSONObject(index) ?: continue
            val label = field.optString("label").ifBlank { field.optString("key") }
            val value = field.optString("value")
            if (label.isNotBlank() && value.isNotBlank()) result += label to value
        }
    } else {
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.startsWith("_")) continue
            val value = json.optString(key)
            if (value.isNotBlank()) result += prettyFieldLabel(key) to value
        }
    }
    if (result.isNotEmpty()) return result.distinctBy { it.first.lowercase() }
    val compact = document.ocrText.replace(Regex("\\s+"), " ")
    Regex("\\\\b(\\\\d{5}\\\\s+[A-ZÄÖÜ][A-Za-zÄÖÜäöüß-]+)").find(compact)?.groupValues?.getOrNull(1)?.let {
        result += "Ort" to it
    }
    if (compact.contains("Mehrfamilienhaus", true)) result += "Objektart" to "Mehrfamilienhaus"
    return result
}

private fun prettyFieldLabel(key: String): String = key
    .replace("_", " ")
    .split(" ")
    .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

private fun openManagedDocument(context: Context, document: ManagedDocument) {
    val file = File(document.localUri)
    if (!file.isFile) return
    val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, document.mimeType.ifBlank { "*/*" })
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(intent) }
}
