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
import androidx.compose.ui.graphics.Color
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

/** Gemeinsame Belegdetailseite nach verbindlichem Referenzdesign. */
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

    fun openAdvanced(edit: Boolean = false) {
        editing = edit
        advanced = true
    }
    fun navigate(screen: AppScreen) { onDismiss(); viewModel.setScreen(screen) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize().testTag("receipt_detail_screen"),
            containerColor = SoftBackground,
            topBar = {
                TopAppBar(
                    title = { Text("Belegdetails", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                    actions = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Weitere Belegfunktionen") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Bearbeiten") }, onClick = { menu = false; openAdvanced(true) })
                                DropdownMenuItem(text = { Text("Weitere Belegdaten") }, onClick = { menu = false; openAdvanced(false) })
                                DropdownMenuItem(text = { Text("Dokumentdiagnose / alle Seiten") }, onClick = { menu = false; openAdvanced(false) })
                                DropdownMenuItem(text = { Text("Beleg löschen") }, onClick = { menu = false; delete = true })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SoftBackground)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    listOf(
                        Triple(AppScreen.DASHBOARD, Icons.Default.Home, "Start"),
                        Triple(AppScreen.RECEIPTS_LIST, Icons.Default.Receipt, "Belege"),
                        Triple(AppScreen.ADD_RECEIPT, Icons.Default.AddCircle, "Scannen"),
                        Triple(AppScreen.PROPERTIES, Icons.Default.Apartment, "Immobilien"),
                        Triple(AppScreen.MORE, Icons.Default.GridView, "Mehr")
                    ).forEach { (screen, icon, label) ->
                        NavigationBarItem(
                            selected = screen == AppScreen.RECEIPTS_LIST,
                            onClick = { navigate(screen) },
                            icon = {
                                Icon(
                                    icon,
                                    label,
                                    Modifier.size(if (screen == AppScreen.ADD_RECEIPT) 42.dp else 24.dp),
                                    tint = if (screen == AppScreen.ADD_RECEIPT || screen == AppScreen.RECEIPTS_LIST) AccentBlue else SlateGray
                                )
                            },
                            label = { Text(label, fontSize = 10.sp, maxLines = 1) }
                        )
                    }
                }
            }
        ) { insets ->
            Column(
                Modifier.padding(insets).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ReceiptDetailCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(Modifier.width(108.dp).height(142.dp), shape = RoundedCornerShape(10.dp), color = SoftBackground) {
                            if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Beleg-Miniatur", contentScale = ContentScale.Fit)
                            else Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Description, "Keine Miniatur verfügbar", tint = SlateGray) }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(current.aussteller.ifBlank { "Beleg" }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(current.getEffectiveDisplayId(), fontSize = 13.sp, color = SlateGray)
                            Text(current.datum, fontSize = 13.sp, color = SlateGray)
                            Spacer(Modifier.height(3.dp))
                            Text(NumberFormatter.format(current.bruttobetrag), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            ReceiptAssignmentBadge(entries.isNotEmpty())
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { openAdvanced(true) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = AccentBlue.copy(alpha = 0.08f), contentColor = AccentBlue)
                        ) {
                            Icon(Icons.Default.Edit, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Bearbeiten", fontWeight = FontWeight.SemiBold)
                        }
                        FilledTonalButton(
                            onClick = { openAdvanced(false) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = AccentBlue.copy(alpha = 0.08f), contentColor = AccentBlue)
                        ) {
                            Icon(Icons.Default.Share, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Teilen", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                ReceiptDetailCard(contentPadding = PaddingValues(horizontal = 14.dp)) {
                    ReceiptReferenceRow("Kategorie", current.unterkategorie.ifBlank { current.hauptkategorie }, Icons.Default.Description) { openAdvanced(true) }
                    HorizontalDivider()
                    ReceiptReferenceRow("Lieferant", current.aussteller, Icons.Default.Person) { openAdvanced(true) }
                    HorizontalDivider()
                    ReceiptReferenceRow("Zahlungsart", current.zahlungsart, Icons.Default.CreditCard) { openAdvanced(true) }
                    HorizontalDivider()
                    ReceiptReferenceRow("Immobilie", properties.firstOrNull { it.propertyId == current.propertyId }?.name ?: "Nicht zugeordnet", Icons.Default.Home) { openAdvanced(true) }
                    HorizontalDivider()
                    ReceiptReferenceRow("Zuordnung", if (entries.isEmpty()) "Keine Buchung" else "${entries.size} Buchung${if (entries.size == 1) "" else "en"}", Icons.Default.Link) { openAdvanced(false) }
                }

                ReceiptDetailCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Beleg", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { fullScreen = true }, enabled = bitmap != null) {
                            Icon(Icons.Default.Fullscreen, null, Modifier.size(22.dp)); Spacer(Modifier.width(4.dp)); Text("Vollbild")
                        }
                    }
                    Surface(Modifier.fillMaxWidth().height(214.dp), color = SoftBackground, shape = RoundedCornerShape(10.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Belegvorschau, erste Seite", Modifier.fillMaxSize().clickable { fullScreen = true }, contentScale = ContentScale.Fit)
                            else if (loading || status?.state == DocumentState.DOWNLOADING) CircularProgressIndicator()
                            else Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(status?.message ?: "Keine Vorschau verfügbar", fontSize = 12.sp)
                                TextButton(onClick = { openAdvanced(false) }) { Text("Dokument prüfen") }
                            }
                        }
                    }
                    if (paths.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = { selectedFile = (selectedFile - 1).coerceAtLeast(0) }, enabled = selectedFile > 0) { Text("Zurück") }
                        Text("Datei ${selectedFile + 1}/${paths.size}", Modifier.align(Alignment.CenterVertically), fontSize = 12.sp)
                        TextButton(onClick = { selectedFile++ }, enabled = selectedFile < paths.lastIndex) { Text("Weiter") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { exportPath = path; save.launch(path?.let { File(it).name } ?: "Beleg") },
                            enabled = path != null && bitmap != null,
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = AccentBlue.copy(alpha = 0.08f), contentColor = AccentBlue)
                        ) {
                            Icon(Icons.Default.Download, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Herunterladen", fontSize = 12.sp)
                        }
                        FilledTonalButton(
                            onClick = { replace.launch(arrayOf("image/*", "application/pdf")) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = AccentBlue.copy(alpha = 0.08f), contentColor = AccentBlue)
                        ) {
                            Icon(Icons.Default.Refresh, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Beleg ersetzen", fontSize = 12.sp)
                        }
                    }
                    message?.let { Text(it, fontSize = 12.sp) }
                }

                ReceiptDetailCard(contentPadding = PaddingValues(0.dp)) {
                    Text(
                        "Zugeordnete Buchungen (${entries.size})",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (entries.isEmpty()) {
                        HorizontalDivider()
                        Text("Noch keine Buchung zugeordnet.", modifier = Modifier.padding(14.dp), fontSize = 12.sp, color = SlateGray)
                    } else {
                        entries.forEach { (_, transaction) ->
                            HorizontalDivider()
                            Row(
                                Modifier.fillMaxWidth().clickable { openAdvanced(false) }.padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = EmeraldGreen, modifier = Modifier.size(28.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(transaction.bookingDate, fontSize = 13.sp)
                                    Text(transaction.counterparty.ifBlank { transaction.purpose }, fontSize = 13.sp, color = SlateGray, maxLines = 1)
                                }
                                Text(NumberFormatter.format(transaction.amount), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SlateGray)
                            }
                        }
                    }
                }

                ReceiptDetailCard(contentPadding = PaddingValues(0.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { openAdvanced(false) }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Description, null, tint = SlateGray, modifier = Modifier.size(28.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Weitere Belegdaten", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Steuerdaten, DATEV und Dokumentdiagnose", fontSize = 12.sp, color = SlateGray)
                        }
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = SlateGray)
                    }
                }

                OutlinedButton(
                    onClick = { delete = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    border = BorderStroke(1.dp, CrimsonRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(21.dp)); Spacer(Modifier.width(8.dp)); Text("Beleg löschen", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        if (fullScreen && bitmap != null) FullScreenReceiptPreviewDialog(bitmap!!) { fullScreen = false }
        if (advanced) ReceiptAdvancedDetailDialog(current, viewModel, { advanced = false }, initiallyEditing = editing)
        if (delete) ReceiptDeleteConfirmationDialog({ delete = false }, {
            delete = false
            deletionRequested = true
            viewModel.deleteReceipt(current.id)
        })
        pendingRepair?.let { (file, validation) ->
            ConfirmRepairDocumentDialog(current, file, validation, viewModel) { pendingRepair = null }
        }
    }
}

@Composable
private fun ReceiptDetailCard(
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(contentPadding, verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun ReceiptReferenceRow(label: String, value: String, icon: ImageVector, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 54.dp).padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = SlateGray)
        Text(label, Modifier.weight(0.72f), fontSize = 13.sp, color = SlateGray)
        Text(value.ifBlank { "Nicht angegeben" }, Modifier.weight(1.25f), fontSize = 14.sp)
        if (onClick != null) Icon(Icons.Default.Edit, null, Modifier.size(20.dp), tint = AccentBlue)
    }
}

@Composable
private fun ReceiptAssignmentBadge(assigned: Boolean) {
    Surface(
        color = (if (assigned) EmeraldGreen else AccentBlue).copy(alpha = 0.1f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (assigned) Icon(Icons.Default.CheckCircle, null, Modifier.size(17.dp), tint = EmeraldGreen)
            Text(
                if (assigned) "Zugeordnet" else "Offen",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (assigned) EmeraldGreen else AccentBlue
            )
        }
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
                Bitmap.createBitmap(
                    (page.width * ratio).toInt().coerceAtLeast(1),
                    (page.height * ratio).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                ).also {
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
