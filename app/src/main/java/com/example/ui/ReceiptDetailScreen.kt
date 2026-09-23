package com.example.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
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

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.Color
import com.example.data.BankReceiptLink
import com.example.data.BankTransaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Native app-screen entry point. Uses the main app scaffold and bottom navigation. */
@Composable
fun ReceiptDetailScreen(viewModel: ReceiptViewModel) {
    val receipts by viewModel.receipts.collectAsState()
    val receiptId by viewModel.receiptDetailId.collectAsState()
    val receipt = receipts.firstOrNull { it.id == receiptId }
    if (receipt == null) {
        LaunchedEffect(receiptId) {
            if (receiptId != null) viewModel.closeReceiptDetail()
        }
        return
    }
    ReceiptDetailDialog(
        receipt = receipt,
        viewModel = viewModel,
        onDismiss = { viewModel.closeReceiptDetail() },
        asScreen = true
    )
}

/** Shared receipt detail host. Dialog mode remains available for legacy/test call sites. */
@Composable
fun ReceiptDetailDialog(
    receipt: Receipt,
    viewModel: ReceiptViewModel,
    onDismiss: () -> Unit,
    asScreen: Boolean = false
) {
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
    var editing by remember(current.id) { mutableStateOf(false) }
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

    val detailContent: @Composable () -> Unit = {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF5F8FC)
        ) {
            Box(Modifier.fillMaxSize()) {
                ReceiptDetailLayout(
            receipt = current,
            propertyName = properties.firstOrNull { it.propertyId == current.propertyId }?.name ?: "Nicht zugeordnet",
            entries = entries,
            bitmap = bitmap,
            loading = loading || status?.state == DocumentState.DOWNLOADING,
            previewMessage = status?.message ?: "Keine Vorschau verfügbar",
            message = message,
            fileIndex = selectedFile, fileCount = paths.size,
            editing = editing,
            onEditingChange = { editing = it },
            onFileChange = { selectedFile = it },
            onBack = onDismiss,
            onNavigate = ::navigate,
            onShare = {
                runCatching {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Beleg ${current.getEffectiveDisplayId()}")
                        putExtra(Intent.EXTRA_TEXT, "${current.aussteller}\n${receiptDisplayDate(current.datum)}\n${NumberFormatter.format(current.bruttobetrag)}\n${current.beschreibung}")
                    }
                    context.startActivity(Intent.createChooser(intent, "Beleg teilen"))
                }.onFailure { message = "Teilen nicht möglich: ${it.localizedMessage}" }
            },
            onFullScreen = { fullScreen = true },
            onDownload = { exportPath = path; save.launch(path?.let { File(it).name } ?: "Beleg") },
            onReplace = { replace.launch(arrayOf("image/*", "application/pdf")) },
            onDelete = { delete = true },
            onUnlink = { link, transaction -> viewModel.removeBankReceiptLink(link.linkId, transaction.transactionId) },
            embeddedInAppScaffold = asScreen,
            editor = { done -> ReceiptInlineEditor(current, viewModel, done) },
            additionalData = { ReceiptAdditionalData(current, viewModel) }
        )
        if (fullScreen && bitmap != null) FullScreenReceiptPreviewDialog(bitmap!!) { fullScreen = false }
        if (delete) ReceiptDeleteConfirmationDialog({ delete = false }, {
            delete = false; deletionRequested = true; viewModel.deleteReceipt(current.id)
        })
                pendingRepair?.let { (file, validation) ->
                    ConfirmRepairDocumentDialog(current, file, validation, viewModel) { pendingRepair = null }
                }
            }
        }
    }

    if (asScreen) {
        detailContent()
    } else {
        Dialog(
            onDismissRequest = { if (editing) editing = false else onDismiss() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = true
            )
        ) {
            detailContent()
        }
    }
}

/** Stateless data inputs let the actual mobile layout be rendered in Compose UI tests. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ReceiptDetailLayout(
    receipt: Receipt,
    propertyName: String,
    entries: List<Pair<BankReceiptLink, BankTransaction>>,
    bitmap: Bitmap?,
    loading: Boolean = false,
    previewMessage: String = "Keine Vorschau verfügbar",
    message: String? = null,
    fileIndex: Int = 0,
    fileCount: Int = 1,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onFileChange: (Int) -> Unit = {},
    onBack: () -> Unit,
    onNavigate: (AppScreen) -> Unit,
    onShare: () -> Unit,
    onFullScreen: () -> Unit,
    onDownload: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
    onUnlink: (BankReceiptLink, BankTransaction) -> Unit,
    embeddedInAppScaffold: Boolean = false,
    editor: @Composable (() -> Unit) -> Unit,
    additionalData: @Composable () -> Unit
) {
    var additionalExpanded by remember(receipt.id) { mutableStateOf(false) }
    var selectedLink by remember(receipt.id) { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val editorTarget = remember { BringIntoViewRequester() }
    val linksTarget = remember { BringIntoViewRequester() }
    val extraTarget = remember { BringIntoViewRequester() }
    LaunchedEffect(editing) { if (editing) editorTarget.bringIntoView() }
    LaunchedEffect(additionalExpanded) { if (additionalExpanded) extraTarget.bringIntoView() }
    BackHandler(enabled = editing) { onEditingChange(false) }
    val blue = Color(0xFF0066FF)
    val navy = Color(0xFF10182D)
    val slate = Color(0xFF526078)
    val paleBlue = Color(0xFFEAF3FF)
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("receipt_detail_screen"),
        containerColor = Color(0xFFF5F8FC),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                if (!embeddedInAppScaffold) TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = AccentBlue.copy(alpha = 0.12f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Home,
                                        contentDescription = null,
                                        tint = AccentBlue,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Column {
                                Text("ImmoPilot", fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 25.sp)
                                Text("Immobilien. Finanzen. Steuern.", color = SlateGray, fontSize = 11.sp)
                            }
                        }
                    }
                )
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF5F8FC)),
                    title = {
                        Text(
                            if (editing) "Beleg bearbeiten" else "Belegdetails",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = navy
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (editing) onEditingChange(false) else onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = navy)
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Default.MoreVert, "Weitere Belegfunktionen", tint = navy)
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Weitere Belegdaten") },
                                    onClick = { menu = false; additionalExpanded = true }
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!embeddedInAppScaffold) NavigationBar(
                containerColor = Color.White,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .testTag("receipt_detail_bottom_navigation")
            ) {
                listOf(
                    Triple(AppScreen.DASHBOARD, Icons.Default.Home, "Start"),
                    Triple(AppScreen.RECEIPTS_LIST, Icons.Default.ReceiptLong, "Belege"),
                    Triple(AppScreen.ADD_RECEIPT, Icons.Default.Add, "Scannen"),
                    Triple(AppScreen.PROPERTIES, Icons.Default.Apartment, "Immobilien"),
                    Triple(AppScreen.MORE, Icons.Default.GridView, "Mehr")
                ).forEach { (screen, icon, label) ->
                    NavigationBarItem(selected = screen == AppScreen.RECEIPTS_LIST,
                        onClick = { onNavigate(screen) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = blue, selectedTextColor = blue,
                            unselectedIconColor = slate, unselectedTextColor = slate, indicatorColor = paleBlue),
                        icon = {
                            if (screen == AppScreen.ADD_RECEIPT) Surface(color = blue, shape = RoundedCornerShape(50)) {
                                Icon(icon, null, Modifier.padding(8.dp).size(28.dp), tint = Color.White)
                            } else Icon(icon, null, Modifier.size(24.dp))
                        }, label = { Text(label, fontSize = 10.sp, maxLines = 1) })
                }
            }
        }
    ) { insets ->
        Column(Modifier.padding(insets).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("receipt_detail_scroll"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!editing) ReceiptDetailCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(Modifier.width(100.dp).height(132.dp), shape = RoundedCornerShape(8.dp), color = Color(0xFFEDF0F5)) {
                        if (bitmap != null) Image(bitmap.asImageBitmap(), "Beleg-Miniatur", contentScale = ContentScale.Fit)
                        else Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Description, "Keine Miniatur verfügbar", tint = slate) }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(receipt.aussteller.ifBlank { "Beleg" }, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = navy)
                        Text(receipt.getEffectiveDisplayId(), fontSize = 12.sp, color = slate)
                        Text(receiptDisplayDate(receipt.datum), fontSize = 12.sp, color = slate)
                        Text(NumberFormatter.format(receipt.bruttobetrag), fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = navy)
                        ReceiptAssignmentBadge(entries.isNotEmpty())
                    }
                }
                if (!editing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReceiptDetailAction("Bearbeiten", Icons.Default.Edit, Modifier.weight(1f).testTag("edit_receipt_button")) { onEditingChange(true) }
                    ReceiptDetailAction("Teilen", Icons.Default.Share, Modifier.weight(1f), onClick = onShare)
                }
            }
            if (editing) ReceiptDetailCard {
                Column(Modifier.bringIntoViewRequester(editorTarget)) {
                    editor { onEditingChange(false) }
                }
            }
            if (!editing) ReceiptDetailCard {
                ReceiptReferenceRow("Kategorie", receipt.unterkategorie.ifBlank { receipt.hauptkategorie }, Icons.Default.Description) { onEditingChange(true) }
                ReceiptDetailDivider()
                ReceiptReferenceRow("Lieferant", receipt.aussteller, Icons.Default.PersonOutline) { onEditingChange(true) }
                ReceiptDetailDivider()
                ReceiptReferenceRow("Zahlungsart", receipt.zahlungsart, Icons.Default.CreditCard) { onEditingChange(true) }
                ReceiptDetailDivider()
                ReceiptReferenceRow("Immobilie", propertyName, Icons.Default.Home) { onEditingChange(true) }
                ReceiptDetailDivider()
                ReceiptReferenceRow("Zuordnung", when (entries.size) { 0 -> "Keine Buchung"; 1 -> "1 Buchung"; else -> "${entries.size} Buchungen" }, Icons.Default.Link) {
                    selectedLink = entries.firstOrNull()?.first?.linkId
                    scope.launch { linksTarget.bringIntoView() }
                }
            }
            if (!editing) ReceiptLineItemsCard(receipt.getPositionenList(), navy, slate)
            if (!editing) ReceiptDetailCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Beleg", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = navy)
                    TextButton(onClick = onFullScreen, enabled = bitmap != null, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Icon(Icons.Default.Fullscreen, null, Modifier.size(20.dp)); Spacer(Modifier.width(4.dp)); Text("Vollbild", fontSize = 13.sp)
                    }
                }
                Surface(Modifier.fillMaxWidth().height(140.dp), color = Color(0xFFEDF0F5), shape = RoundedCornerShape(8.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        if (bitmap != null) Image(bitmap.asImageBitmap(), "Belegvorschau, erste Seite",
                            Modifier.fillMaxSize().clickable(onClick = onFullScreen), contentScale = ContentScale.Fit)
                        else if (loading) CircularProgressIndicator()
                        else Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(previewMessage, fontSize = 12.sp, color = slate)
                            TextButton(onClick = { additionalExpanded = true }) { Text("Dokument prüfen") }
                        }
                    }
                }
                if (fileCount > 1) Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onFileChange(fileIndex - 1) }, enabled = fileIndex > 0) { Text("Zurück") }
                    Text("Datei ${fileIndex + 1}/$fileCount", fontSize = 12.sp)
                    TextButton(onClick = { onFileChange(fileIndex + 1) }, enabled = fileIndex < fileCount - 1) { Text("Weiter") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReceiptDetailAction("Herunterladen", Icons.Default.Download, Modifier.weight(1f), enabled = bitmap != null, onClick = onDownload)
                    ReceiptDetailAction("Beleg ersetzen", Icons.Default.Refresh, Modifier.weight(1f), onClick = onReplace)
                }
                message?.let { Text(it, fontSize = 12.sp) }
            }
            if (!editing) ReceiptDetailCard {
                Column(Modifier.bringIntoViewRequester(linksTarget).testTag("receipt_bank_links_card"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Zugeordnete Buchungen (${entries.size})", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = navy)
                    if (entries.isEmpty()) Text("Noch keine Buchung zugeordnet.", fontSize = 12.sp, color = slate)
                    entries.forEach { (link, transaction) ->
                        ReceiptDetailDivider()
                        Row(Modifier.fillMaxWidth().clickable { selectedLink = if (selectedLink == link.linkId) null else link.linkId }
                            .heightIn(min = 48.dp).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF009B57), modifier = Modifier.size(24.dp))
                            Column(Modifier.weight(1f)) {
                                Text(receiptDisplayDate(transaction.bookingDate), fontSize = 12.sp, color = navy)
                                Text(transaction.counterparty.ifBlank { transaction.purpose }, fontSize = 12.sp, color = slate)
                            }
                            Text(NumberFormatter.format(transaction.amount), fontSize = 13.sp, color = navy)
                            Icon(if (selectedLink == link.linkId) Icons.Default.ExpandMore else Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = slate)
                        }
                        if (selectedLink == link.linkId) {
                            Text(transaction.purpose, fontSize = 12.sp, color = slate)
                            Text("Zugeordnet: ${NumberFormatter.format(link.allocatedAmount)}", fontSize = 12.sp)
                            TextButton(onClick = { onUnlink(link, transaction) }) { Text("Verknüpfung lösen", color = CrimsonRed) }
                        }
                    }
                }
            }
            if (!editing) ReceiptDetailCard {
                Column(Modifier.bringIntoViewRequester(extraTarget)) {
                    Row(Modifier.fillMaxWidth().clickable { additionalExpanded = !additionalExpanded }
                        .heightIn(min = 44.dp).testTag("receipt_more_data"), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Description, null, tint = slate, modifier = Modifier.size(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Weitere Belegdaten", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = navy)
                            Text("Steuerdaten, DATEV und Dokumentdiagnose", fontSize = 11.sp, lineHeight = 14.sp, color = slate)
                        }
                        Icon(if (additionalExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            if (additionalExpanded) "Zuklappen" else "Aufklappen", tint = slate)
                    }
                    if (additionalExpanded) additionalData()
                }
            }
            if (!editing) OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFFFF8D99)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFFFEDF0), contentColor = Color(0xFFD00024))) {
                Icon(Icons.Default.DeleteOutline, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text("Beleg löschen", fontSize = 13.sp)
            }
        }
    }
}

/** Read-only itemized receipt view. Changes are made exclusively in the edit form. */
@Composable
private fun ReceiptLineItemsCard(
    items: List<com.example.data.ReceiptItem>,
    navy: Color,
    slate: Color
) {
    ReceiptDetailCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.List, null, Modifier.size(22.dp), tint = Color(0xFF009B57))
            Spacer(Modifier.width(8.dp))
            Text("Einzelne Positionen / Artikel (${items.size})", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = navy)
        }
        if (items.isEmpty()) {
            Text("Keine einzelnen Positionen erfasst.", fontSize = 12.sp, color = slate)
        } else {
            items.forEachIndexed { index, item ->
                if (index > 0) ReceiptDetailDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.bezeichnung.ifBlank { "Position ${index + 1}" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = navy)
                        Text("${item.menge} × ${NumberFormatter.format(item.einzelpreis)}", fontSize = 11.sp, color = slate)
                    }
                    Text(NumberFormatter.format(item.gesamtpreis), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = navy)
                }
            }
        }
    }
}

@Composable
private fun ReceiptDetailCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE3E8EF)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun ReceiptDetailAction(label: String, icon: ImageVector, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 40.dp),
        shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFEAF3FF), contentColor = Color(0xFF0066FF))) {
        Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun ReceiptDetailDivider() { HorizontalDivider(color = Color(0xFFEEF1F6)) }

@Composable
private fun ReceiptReferenceRow(label: String, value: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 38.dp).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = Color(0xFF526078))
        Text(label, Modifier.weight(0.8f), fontSize = 12.sp, color = Color(0xFF526078))
        Text(value.ifBlank { "Nicht angegeben" }, Modifier.weight(1.5f), fontSize = 12.sp, color = Color(0xFF10182D))
        Icon(Icons.Default.Edit, "${label} bearbeiten", Modifier.size(18.dp), tint = Color(0xFF0066FF))
    }
}

@Composable
private fun ReceiptAssignmentBadge(assigned: Boolean) {
    Surface(color = if (assigned) Color(0xFFE5F9ED) else Color(0xFFEAF3FF), shape = RoundedCornerShape(6.dp)) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (assigned) Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = Color(0xFF009B57))
            Text(if (assigned) "Zugeordnet" else "Offen", fontSize = 12.sp, color = if (assigned) Color(0xFF009B57) else Color(0xFF0066FF))
        }
    }
}

internal fun receiptDisplayDate(value: String): String = runCatching {
    LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}.getOrDefault(value)

/** Accept both existing decimal-dot values and German amount input without silently saving zero. */
internal fun parseReceiptEditAmount(value: String): Double? {
    val compact = value.trim().replace(" ", "").replace("€", "")
    val normalized = if (compact.contains(',')) compact.replace(".", "").replace(',', '.') else compact
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
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
