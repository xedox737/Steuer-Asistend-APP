package com.example.ui
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.content.Context
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Build
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.shouldShowRationale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Calculate

import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

import android.webkit.WebResourceRequest
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.ExtractedReceipt
import com.example.api.ReceiptAnalysisProvider
import com.example.data.Receipt
import com.example.data.PropertyMetadata
import java.text.DecimalFormat
import java.util.Locale

// Custom modern colors for the tax/accounting advisor theme
val SlateGray = Color(0xFF475569)
val DarkNavy = Color(0xFF0F172A)
val EmeraldGreen = Color(0xFF059669)
val AccentBlue = Color(0xFF2563EB)
val WarmOrange = Color(0xFFEA580C)
val CrimsonRed = Color(0xFFDC2626)
val SoftBackground = Color(0xFFF8FAFC)
val BorderColor = Color(0xFFE2E8F0)

val NumberFormatter = DecimalFormat("#,##0.00 €").apply {
    decimalFormatSymbols = decimalFormatSymbols.apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptAppUi(viewModel: ReceiptViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val receipts by viewModel.receipts.collectAsState()
    val bankStatementResult by viewModel.bankStatementResult.collectAsState()
    val missingReceiptsCount = bankStatementResult?.missingReceiptsCount ?: 0
    val rentArrearsCount = bankStatementResult?.rentArrearsCount ?: 0
    val totalBankAlerts = missingReceiptsCount + rentArrearsCount
    val screenTitle = when (currentScreen) {
        AppScreen.DASHBOARD -> "Übersicht"
        AppScreen.RECEIPTS_LIST -> "Belege"
        AppScreen.ADD_RECEIPT -> "Beleg erfassen"
        AppScreen.LOGBOOK -> "Fahrtenbuch"
        AppScreen.LEDGER -> "Finanzen"
        AppScreen.RENT_OVERVIEW -> "Mieteingänge"
        AppScreen.TAX_CALCULATOR -> "Steuerschätzung"
    }

    var showAccountSettingsDialog by remember { mutableStateOf(false) }
    var showKiPowerCenterDialog by remember { mutableStateOf(false) }

    if (showAccountSettingsDialog) {
        AccountSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showAccountSettingsDialog = false }
        )
    }

    if (showKiPowerCenterDialog) {
        KiPowerCenterDialog(
            viewModel = viewModel,
            onDismiss = { showKiPowerCenterDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column {
                            Text(
                                screenTitle,
                                fontWeight = FontWeight.Bold,
                                color = DarkNavy,
                                fontSize = 20.sp
                            )
                            Text(
                                if (currentScreen == AppScreen.DASHBOARD) "Steuer-Assistent • Anlage V" else "Steuer-Assistent",
                                color = SlateGray,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showKiPowerCenterDialog = true },
                            modifier = Modifier.testTag("ki_power_center_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "KI-Assistenten", tint = Color(0xFF7C3AED))
                        }
                        if (totalBankAlerts > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 4.dp, end = 4.dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(CrimsonRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$totalBankAlerts",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { showAccountSettingsDialog = true },
                        modifier = Modifier.testTag("account_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen", tint = DarkNavy)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    scrolledContainerColor = Color.White
                )
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = BorderColor)
                NavigationBar(
                    containerColor = Color.White,
                    modifier = Modifier.testTag("bottom_navigation")
                ) {
                    val items = listOf(
                        Triple(AppScreen.DASHBOARD, Icons.Default.Home, "Start"),
                        Triple(AppScreen.RECEIPTS_LIST, Icons.Default.Receipt, "Belege"),
                        Triple(AppScreen.ADD_RECEIPT, Icons.Default.AddCircle, "Scannen"),
                        Triple(AppScreen.LOGBOOK, Icons.Default.DirectionsCar, "Fahrtenbuch"),
                        Triple(AppScreen.LEDGER, Icons.Default.AccountBalance, "Finanzen")
                    )

                    items.forEach { (screen, icon, label) ->
                        val isPrimaryAction = screen == AppScreen.ADD_RECEIPT
                        val isSelected = currentScreen == screen ||
                            (screen == AppScreen.DASHBOARD && currentScreen in setOf(
                                AppScreen.RENT_OVERVIEW,
                                AppScreen.TAX_CALCULATOR
                            ))
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { viewModel.setScreen(screen) },
                            icon = {
                                Icon(
                                    icon,
                                    contentDescription = label,
                                    modifier = if (isPrimaryAction) Modifier.size(28.dp) else Modifier.size(24.dp)
                                )
                            },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = if (isPrimaryAction) Color.White else AccentBlue,
                                selectedTextColor = if (isPrimaryAction) AccentBlue else DarkNavy,
                                unselectedIconColor = if (isPrimaryAction) AccentBlue else SlateGray,
                                unselectedTextColor = SlateGray,
                                indicatorColor = if (isPrimaryAction) AccentBlue else Color(0xFFDBEAFE)
                            ),
                            modifier = Modifier.testTag("nav_item_${screen.name.lowercase()}")
                        )
                    }
                }
            }
        },
        containerColor = SoftBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.DASHBOARD -> DashboardScreen(viewModel)
                AppScreen.RECEIPTS_LIST -> ReceiptsListScreen(viewModel)
                AppScreen.ADD_RECEIPT -> AddReceiptScreen(viewModel)
                AppScreen.LOGBOOK -> LogbookScreen(viewModel)
                AppScreen.LEDGER -> LedgerScreen(viewModel)
                AppScreen.RENT_OVERVIEW -> RentOverviewScreen(viewModel)
                AppScreen.TAX_CALCULATOR -> TaxCalculatorScreen(viewModel)
            }
        }
    }
}

@Composable
fun Icon(imageName: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, tint: Color, modifier: Modifier) {
    androidx.compose.material3.Icon(
        imageVector = imageName,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun RentOverviewScreen(viewModel:…181816 tokens truncated…eien referenzieren.",
                    fontSize = 13.sp,
                    color = DarkNavy
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val receipt = receiptToPermanentlyDelete!!
                        receiptToPermanentlyDelete = null
                        viewModel.permanentlyDeleteReceipt(receipt) { result ->
                            when (result) {
                                is com.example.data.PermanentDeleteResult.Success -> {
                                    showDeleteSuccess = true
                                    permanentDeleteError = null
                                }
                                is com.example.data.PermanentDeleteResult.Error -> {
                                    permanentDeleteError = result.message
                                    showDeleteSuccess = false
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    modifier = Modifier.testTag("confirm_permanent_delete_button")
                ) {
                    Text("Unwiderruflich löschen")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { receiptToPermanentlyDelete = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    if (showDeleteSuccess) {
        AlertDialog(
            onDismissRequest = { showDeleteSuccess = false },
            title = { Text("Erfolgreich gelöscht", fontWeight = FontWeight.Bold, color = EmeraldGreen) },
            text = { Text("Der Beleg wurde endgültig aus der Cloud und der lokalen Datenbank entfernt.", fontSize = 13.sp) },
            confirmButton = {
                Button(onClick = { showDeleteSuccess = false }, colors = ButtonDefaults.buttonColors(containerColor = DarkNavy)) {
                    Text("OK")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = CrimsonRed,
                    modifier = Modifier.size(24.dp)
                )
                Text("🗑️ Papierkorb", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DarkNavy)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
                Text(
                    "Hier befinden sich alle gelöschten Belege. Sie können diese wiederherstellen oder unwiderruflich aus der Cloud löschen.",
                    fontSize = 12.sp,
                    color = SlateGray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Button(
                    onClick = viewModel::analyzeReceiptDuplicates,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .testTag("analyze_receipt_duplicates_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkNavy)
                ) {
                    Text("Dubletten prüfen")
                }

                when (val cleanupState = duplicateCleanupState) {
                    DuplicateCleanupUiState.Loading -> {
                        Text(
                            "Dubletten werden rein lesend geprüft …",
                            fontSize = 12.sp,
                            color = SlateGray,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                    is DuplicateCleanupUiState.PendingOperations -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = WarmOrange.copy(alpha = 0.08f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "Abgebrochene Bereinigung gefunden",
                                    fontWeight = FontWeight.Bold,
                                    color = WarmOrange
                                )
                                cleanupState.operationIds.sorted().forEach { operationId ->
                                    Button(
                                        onClick = {
                                            viewModel.resumeDuplicateCleanupOperation(operationId)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Bereinigung fortsetzen")
                                    }
                                }
                            }
                        }
                    }
                    is DuplicateCleanupUiState.Ready -> {
                        if (cleanupState.groups.isEmpty()) {
                            Text(
                                "Keine Dublettengruppen gefunden.",
                                color = EmeraldGreen,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        } else {
                            cleanupState.groups.forEach { group ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = WarmOrange.copy(alpha = 0.08f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            "Hauptdatei: ${group.mainDriveFileId}",
                                            fontSize = 10.sp,
                                            color = SlateGray
                                        )
                                        Text(
                                            "Kanonisch: ${group.canonical.displayId}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            "${group.duplicatesToRemove.size} lokale Dublette(n)",
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            "Hauptdatei wird nicht gelöscht",
                                            color = EmeraldGreen,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row {
                                            TextButton(
                                                onClick = {
                                                    viewModel.requestDuplicateMerge(group)
                                                },
                                                modifier = Modifier.testTag(
                                                    "safe_merge_duplicate_group_button"
                                                )
                                            ) {
                                                Text("Dubletten sicher zusammenführen")
                                            }
                                            TextButton(
                                                onClick = {
                                                    viewModel.requestWholeDuplicateGroupDeletion(group)
                                                }
                                            ) {
                                                Text(
                                                    "Gesamte Gruppe löschen",
                                                    color = CrimsonRed
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is DuplicateCleanupUiState.Completed -> {
                        Text(
                            "Dublettenbereinigung abgeschlossen.",
                            color = EmeraldGreen,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                    is DuplicateCleanupUiState.Failed -> {
                        Text(
                            cleanupState.message,
                            color = CrimsonRed,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                    else -> Unit
                }

                permanentDeleteError?.let { err ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CrimsonRed.copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.3f)),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = err,
                            color = CrimsonRed,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (deletedReceipts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color.LightGray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Der Papierkorb ist leer", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).testTag("recycle_bin_list"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(deletedReceipts.size) { index ->
                            val receipt = deletedReceipts[index]
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = receipt.aussteller.ifEmpty { "Unbekannter Aussteller" },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = DarkNavy,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Belegdatum: ${receipt.datum} • Betrag: ${NumberFormatter.format(receipt.bruttobetrag)}",
                                                fontSize = 11.sp,
                                                color = SlateGray
                                            )
                                            receipt.deletedAt?.let { delAt ->
                                                if (delAt.isNotEmpty()) {
                                                    Text(
                                                        text = "Gelöscht am: $delAt",
                                                        fontSize = 10.sp,
                                                        color = CrimsonRed,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                        
                                        if (receipt.syncStatus == "DELETE_PENDING") {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = WarmOrange.copy(alpha = 0.1f),
                                                border = BorderStroke(1.dp, WarmOrange.copy(alpha = 0.4f)),
                                                modifier = Modifier.padding(start = 4.dp)
                                            ) {
                                                Text(
                                                    text = "Löschung ausstehend",
                                                    fontSize = 9.sp,
                                                    color = WarmOrange,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (receipt.syncStatus == "DELETE_PENDING") {
                                            TextButton(
                                                onClick = { viewModel.deleteReceipt(receipt.id) },
                                                modifier = Modifier.testTag("retry_delete_pending_button_${receipt.id}")
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = WarmOrange)
                                                    Text("Erneut versuchen", fontSize = 11.sp, color = WarmOrange, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        } else {
                                            TextButton(
                                                onClick = { viewModel.restoreReceipt(receipt) },
                                                modifier = Modifier.testTag("restore_receipt_button_${receipt.id}")
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = EmeraldGreen)
                                                    Text("Wiederherstellen", fontSize = 11.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.width(6.dp))
                                        
                                        TextButton(
                                            onClick = { receiptToPermanentlyDelete = receipt },
                                            modifier = Modifier.testTag("permanent_delete_receipt_button_${receipt.id}")
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(14.dp), tint = CrimsonRed)
                                                Text("Endgültig löschen", fontSize = 11.sp, color = CrimsonRed, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = DarkNavy)
            ) {
                Text("Schließen")
            }
        }
    )
}

