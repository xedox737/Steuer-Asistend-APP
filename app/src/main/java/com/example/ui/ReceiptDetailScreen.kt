package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.Receipt
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reference detail layout. Editing and tax workflows remain in the original dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailDialog(receipt: Receipt, viewModel: ReceiptViewModel, onDismiss: () -> Unit) {
    val receipts by viewModel.receipts.collectAsState()
    val current = receipts.firstOrNull { it.id == receipt.id } ?: receipt
    val links by viewModel.bankReceiptLinks.collectAsState()
    val transactions by viewModel.bankTransactions.collectAsState()
    val properties by viewModel.properties.collectAsState()
    val downloads by viewModel.documentDownloadStatus.collectAsState()
    val entries = links.filter {
        it.receiptId == current.id || (current.internalId.isNotBlank() && it.receiptInternalId == current.internalId)
    }.mapNotNull { link -> transactions.firstOrNull { it.transactionId == link.transactionId }?.let { link to it } }
    val status = downloads[current.internalId]
    val paths = (status?.localPath ?: current.imageUrl).split(',').filter { it.isNotBlank() }
    var selectedFile by remember(current.id) { mutableIntStateOf(0) }
    val path = paths.getOrNull(selectedFile) ?: paths.firstOrNull()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var fullScreen by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var deletionRequested by remember { mutableStateOf(false) }
    var pendingRepair by remember { mutableStateOf<Pair<File, FileValidationResult>?>(null) }
    var exportPath by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val source = exportPath
        if (uri != null && source != null) scope.launch {
            message = withContext(Dispatchers.IO) {
                runCatching {
                    File(source).inputStream().use { input ->
                        requireNotNull(context.contentResolver.openOutputStream(uri)).use { input.copyTo(it) }
                    }
                }.fold({ "Beleg gespeichert." }, { "Speichern fehlgeschlagen: ${it.localizedMessage}" })
            }
        }
    }
    val replace = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File.createTempFile("receipt_replacement_", ".tmp", context.cacheDir)
                    requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    }
                    file to viewModel.validateFileForRepair(context, file)
                }
            }
            result.onSuccess { pair ->
                if (pair.second.isValid) pendingRepair = pair
                else message = pair.second.errorMessage ?: "Ungültiges Dokument"
            }.onFailure { message = "Datei konnte nicht gelesen werden: ${it.localizedMessage}" }
        }
    }
    LaunchedEffect(current.internalId, current.imageUrl) { viewModel.checkDocumentStatus(current) }
    LaunchedEffect(receipts, deletionRequested) {
        if (deletionRequested && receipts.none { it.id == receipt.id }) onDismiss()
    }
    LaunchedEffect(path, status) {
        bitmap = null
        loading = true
        bitmap = withContext(Dispatchers.IO) { path?.let { receiptDetailBitmap(it) } }
        loading = false
    }
    fun navigate(screen: AppScreen) { onDismiss(); viewModel.setScreen(screen) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag("receipt_detail_screen"),
            containerColor = SoftBackground,
            topBar = {
                TopAppBar(
                    title = { Text("Beleg Details", fontSize = 19.sp, fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                    actions = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Weitere Belegfunktionen") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Bearbeiten") }, onClick = { menu = false; editing = true; advanced = true })
                                DropdownMenuItem(text = { Text("Weitere Daten / DATEV / Teilen") }, onClick = { menu = false; editing = false; advanced = true })
                                DropdownMenuItem(text = { Text("Dokumentdiagnose / alle Seiten") }, onClick = { menu = false; editing = false; advanced = true })
                                DropdownMenuItem(text = { Text("Beleg löschen") }, onClick = { menu = false; delete = true })
                            }
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    listOf(
                        Triple(AppScreen.DASHBOARD, Icons.Default.Home, "Start"),
                        Triple(AppScreen.RECEIPTS_LIST, Icons.Default.Receipt, "Belege"),
                        Triple(AppScreen.ADD_RECEIPT, Icons.Default.AddCircle, "Scannen"),
                        Triple(AppScreen.PROPERTIES, Icons.Default.Apartment, "Immobilien"),
                        Triple(AppScreen.MORE, Icons.Default.MoreHoriz, "Mehr")
                    ).forEach { (screen, icon, label) ->
                        NavigationBarItem(selected = screen == AppScreen.RECEIPTS_LIST,
                            onClick = { navigate(screen) }, icon = {
                                Icon(icon, label, Modifier.size(if (screen == AppScreen.ADD_RECEIPT) 40.dp else 24.dp),
                                    tint = if (screen == AppScreen.ADD_RECEIPT || screen == AppScreen.RECEIPTS_LIST) AccentBlue else SlateGray)
                            },
                            label = { Text(label, fontSize = 10.sp, maxLines = 1) })
                    }
                }
            }
        ) { insets ->
            Column(Modifier.padding(insets).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReceiptDetailCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(Modifier.width(82.dp).height(108.dp), shape = RoundedCornerShape(8.dp), color = SoftBackground) {
                            if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Beleg-Miniatur", contentScale = ContentScale.Fit)
                            else Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Description, "Keine Miniatur verfügbar") }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(current.aussteller.ifBlank { "Beleg" }, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(current.getEffectiveDisplayId(), fontSize = 12.sp, color = SlateGray)
                            Text(current.datum, fontSize = 12.sp, color = SlateGray)
                            Text(NumberFormatter.format(current.bruttobetrag), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            ReceiptAssignmentBadge(entries.isNotEmpty())
                        }
                    }
                }
                ReceiptDetailCard {
                    ReceiptReferenceRow("Kategorie", current.unterkategorie.ifBlank { current.hauptkategorie }, Icons.Default.Label) { editing = true; advanced = true }
                    HorizontalDivider()
                    ReceiptReferenceRow("Lieferant", current.aussteller, Icons.Default.Person) { editing = true; advanced = true }
                    HorizontalDivider()
                    ReceiptReferenceRow("Zahlungsart", current.zahlungsart, Icons.Default.CreditCard) { editing = true; advanced = true }
                    HorizontalDivider()
                    ReceiptReferenceRow("Immobilie", properties.firstOrNull { it.propertyId == current.propertyId }?.name ?: "Nicht zugeordnet", Icons.Default.Home)
                    HorizontalDivider()
                    ReceiptReferenceRow("Zuordnung", if (entries.isEmpty()) "Keine Buchung zugeordnet" else "${entries.size} Buchung(en)", Icons.Default.Link) { editing = false; advanced = true }
                }
                ReceiptDetailCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Beleg", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        TextButton(onClick = { fullScreen = true }, enabled = bitmap != null) {
                            Icon(Icons.Default.Fullscreen, null); Text("Vollbild")
                        }
                    }
                    Surface(Modifier.fillMaxWidth().height(180.dp), color = SoftBackground, shape = RoundedCornerShape(8.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Belegvorschau, erste Seite", Modifier.fillMaxSize().clickable { fullScreen = true }, contentScale = ContentScale.Fit)
                            else if (loading || status?.state == DocumentState.DOWNLOADING) CircularProgressIndicator()
                            else Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(status?.message ?: "Keine Vorschau verfügbar", fontSize = 12.sp)
                                TextButton(onClick = { editing = false; advanced = true }) { Text("Dokument prüfen") }
                            }
                        }
                    }
                    if (paths.size > 1) Row {
                        TextButton(onClick = { selectedFile = (selectedFile - 1).coerceAtLeast(0) }, enabled = selectedFile > 0) { Text("Zurück") }
                        Text("Datei ${selectedFile + 1}/${paths.size}", Modifier.align(Alignment.CenterVertically), fontSize = 12.sp)
                        TextButton(onClick = { selectedFile++ }, enabled = selectedFile < paths.lastIndex) { Text("Weiter") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(onClick = { exportPath = path; save.launch(path?.let { File(it).name } ?: "Beleg") },
                            enabled = path != null && bitmap != null, modifier = Modifier.weight(1f), contentPadding = PaddingValues(6.dp)) {
                            Icon(Icons.Default.Download, null, Modifier.size(20.dp)); Text("Herunterladen", fontSize = 12.sp)
                        }
                        FilledTonalButton(onClick = { replace.launch(arrayOf("image/*", "application/pdf")) },
                            modifier = Modifier.weight(1f), contentPadding = PaddingValues(6.dp)) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(20.dp)); Text("Beleg ersetzen", fontSize = 12.sp)
                        }
                    }
                    message?.let { Text(it, fontSize = 12.sp) }
                }
                ReceiptDetailCard {
                    Text("Zugeordnete Buchungen (${entries.size})", fontWeight = FontWeight.Bold)
                    if (entries.isEmpty()) Text("Noch keine Buchung zugeordnet.", fontSize = 12.sp, color = SlateGray)
                    entries.forEach { (_, transaction) ->
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth().clickable { editing = false; advanced = true }.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = EmeraldGreen)
                            Column(Modifier.weight(1f)) {
                                Text(transaction.bookingDate, fontSize = 12.sp)
                                Text(transaction.counterparty.ifBlank { transaction.purpose }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(NumberFormatter.format(transaction.amount), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                ReceiptAssignmentBadge(true)
                            }
                        }
                    }
                }
            }
        }
        if (fullScreen && bitmap != null) FullScreenReceiptPreviewDialog(bitmap!!) { fullScreen = false }
        if (advanced) ReceiptAdvancedDetailDialog(current, viewModel, { advanced = false }, initiallyEditing = editing)
        if (delete) ReceiptDeleteConfirmationDialog({ delete = false }, {
            delete = false; deletionRequested = true; viewModel.deleteReceipt(current.id)
        })
        pendingRepair?.let { (file, validation) ->
            ConfirmRepairDocumentDialog(current, file, validation, viewModel) { pendingRepair = null }
        }
    }
}

@Composable
private fun ReceiptDetailCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = Ui2.shape, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun ReceiptReferenceRow(label: String, value: String, icon: ImageVector, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .heightIn(min = 44.dp).padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = SlateGray)
        Text(label, Modifier.weight(0.8f), fontSize = 12.sp, color = SlateGray)
        Text(value.ifBlank { "Nicht angegeben" }, Modifier.weight(1.4f), fontSize = 13.sp)
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp))
    }
}

@Composable
private fun ReceiptAssignmentBadge(assigned: Boolean) {
    Surface(color = (if (assigned) EmeraldGreen else AccentBlue).copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
        Text(if (assigned) "Zugeordnet" else "Offen", Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            fontSize = 11.sp, color = if (assigned) EmeraldGreen else AccentBlue)
    }
}

/** Bounded first-page rendering, off the UI thread. Full document workflows remain available. */
private fun receiptDetailBitmap(path: String): Bitmap? = runCatching {
    val file = File(path)
    val pdf = file.inputStream().use { input ->
        val header = ByteArray(5)
        input.read(header) == 5 && String(header, Charsets.US_ASCII) == "%PDF-"
    }
    if (pdf) ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(0).use { page ->
                val ratio = 1200f / maxOf(page.width, page.height)
                Bitmap.createBitmap((page.width * ratio).toInt().coerceAtLeast(1), (page.height * ratio).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(android.graphics.Color.WHITE)
                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    } else {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        options.inSampleSize = 1
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1600) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        BitmapFactory.decodeFile(path, options)
    }
}.getOrNull()
