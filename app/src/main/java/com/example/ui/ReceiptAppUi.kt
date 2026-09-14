Warning: truncated output (original token count: 182747)
Total output lines: 13893

package com.example.ui
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.MoreHoriz
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

internal val PRIMARY_NAVIGATION_SCREENS = listOf(
    AppScreen.DASHBOARD,
    AppScreen.RECEIPTS_LIST,
    AppScreen.ADD_RECEIPT,
    AppScreen.PROPERTIES,
    AppScreen.MORE
)

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
        AppScreen.DOCUMENTS -> "Dokumentenakte"
        AppScreen.PROPERTIES -> "Immobilien"
        AppScreen.BANK -> "Bank & Belege"
        AppScreen.MORE -> "Mehr"
    }

    var showAccountSettingsDialog by remember { mutableStateOf(false) }
    var showKiPowerCenterDialog by remember { mutableStateOf(false) }
    var bankDetailsOpen by remember { mutableStateOf(false) }

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
            if (!(currentScreen == AppScreen.BANK && bankDetailsOpen)) {
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
                        onClick = { viewModel.setScreen(AppScreen.BANK) },
                        modifier = Modifier.testTag("bank_navigation_button")
                    ) {
                        Icon(Icons.Default.AccountBalance, contentDescription = "Bank & Belege", tint = DarkNavy)
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
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = BorderColor)
                NavigationBar(
                    containerColor = Color.White,
                    modifier = Modifier.testTag("bottom_navigation")
                ) {
                    val items = PRIMARY_NAVIGATION_SCREENS.map { screen ->
                        when (screen) {
                            AppScreen.DASHBOARD -> Triple(screen, Icons.Default.Home, "Start")
                            AppScreen.RECEIPTS_LIST -> Triple(screen, Icons.Default.Receipt, "Belege")
                            AppScreen.ADD_RECEIPT -> Triple(screen, Icons.Default.AddCircle, "Scannen")
                            AppScreen.PROPERTIES -> Triple(screen, Icons.Default.Apartment, "Immobilien")
                            AppScreen.MORE -> Triple(screen, Icons.Default.MoreHoriz, "Mehr")
                            else -> error("Nicht unterstütztes primäres Navigationsziel: $screen")
                        }
                    }

                    items.forEach { (screen, icon, label) ->
                        val isPrimaryAction = screen == AppScreen.ADD_RECEIPT
                        val isSelected = currentScreen == screen ||
                            (screen == AppScreen.DASHBOARD && currentScreen in setOf(
                                AppScreen.RENT_OVERVIEW,
                                AppScreen.TAX_CALCULATOR
                            )) ||
                            (screen == AppScreen.MORE && currentScreen == AppScreen.BANK)
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
                            label = {
                      Text(
                          label,
                          fontSize = if (screen == AppScreen.LOGBOOK) 9.sp else 11.sp,
                          fontWeight = FontWeight.SemiBold,
                          maxLines = 1
                      )
                  },
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
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 900.dp)
                    .fillMaxSize()
            ) {
            when (currentScreen) {
                AppScreen.DASHBOARD -> DashboardScreen(viewModel)
                AppScreen.RECEIPTS_LIST -> ReceiptsListScreen(viewModel)
                AppScreen.ADD_RECEIPT -> AddReceiptScreen(viewModel)
                AppScreen.LOGBOOK -> LogbookScreen(viewModel)
                AppScreen.LEDGER -> LedgerScreen(viewModel)
                AppScreen.RENT_OVERVIEW -> RentOverviewScreen(viewModel)
                AppScreen.TAX_CALCULATOR -> TaxCalculatorScreen(viewModel)
                AppScreen.DOCUMENTS -> DocumentManagementScreen(viewModel)
                AppScreen.PROPERTIES -> ImmobilienManagerScreen(viewModel)
                AppScreen.BANK -> BankScreen(viewModel, onDetailVisibilityChanged = { bankDetailsOpen = it })
                AppScreen.MORE -> MoreScreen(viewModel)
            }
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
fun RentOverviewScreen(viewModel: ReceiptViewModel) {
    RentIncomeWithTenantHistoryScreen(viewModel)
}

@Composable
fun TaxCalculatorScreen(viewModel: ReceiptViewModel) {
    AnnualTaxAssistantScreen(viewModel)
}

// --- SCREEN 1: DASHBOARD// --- SCREEN 1: DASHBOARD ---

@Composable
fun MonthlyIncomeExpenseChart(receipts: List<Receipt>) {
    val monthlyIncome = receipts
        .filter { it.datum.isNotEmpty() && (it.hauptkategorie == "Miete, Nebenkosten & Kaution" || it.hauptkategorie == "Sonstige Einnahmen") }
        .groupBy { it.datum.substring(0, 7) }
        .mapValues { entry -> entry.value.sumOf { it.bruttobetrag } }

    val monthlyExpenses = receipts
        .filter { it.datum.isNotEmpty() && !(it.hauptkategorie == "Miete, Nebenkosten & Kaution" || it.hauptkategorie == "Sonstige Einnahmen") }
        .groupBy { it.datum.substring(0, 7) }
        .mapValues { entry -> entry.value.sumOf { it.bruttobetrag } }

    val allMonths = (monthlyIncome.keys + monthlyExpenses.keys).sorted()

    if (allMonths.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Text("Keine Transaktionen für Diagramm vorhanden", color = Color.Gray, fontSize = 14.sp)
            }
        }
        return
    }

    val maxAmount = allMonths.maxOf { month ->
        maxOf(monthlyIncome[month] ?: 0.0, monthlyExpenses[month] ?: 0.0)
    }.coerceAtLeast(1.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Monatliche Einnahmen vs. Ausgaben",
                fontWeight = FontWeight.Bold,
                color = DarkNavy,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                allMonths.forEach { month ->
                    val income = monthlyIncome[month] ?: 0.0
                    val expense = monthlyExpenses[month] ?: 0.0

                    val incomeBarHeight = (income / maxAmount * 150).toFloat().coerceAtLeast(4f)
                    val expenseBarHeight = (expense / maxAmount * 150).toFloat().coerceAtLeast(4f)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(incomeBarHeight.dp)
                                    .background(
                                        EmeraldGreen,
                                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(expenseBarHeight.dp)
                                    .background(
                                        CrimsonRed,
                                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val displayMonth = if (month.length >= 7) {
                            month.substring(5) + "/" + month.substring(2, 4)
                        } else {
                            month
                        }
                        Text(
                            text = displayMonth,
                            fontSize = 9.sp,
                            color = SlateGray,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(EmeraldGreen, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Einnahmen", fontSize = 11.sp, color = DarkNavy, fontWeight = FontWeight.Medium)

                Spacer(modifier = Modifier.width(24.dp))

                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(CrimsonRed, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ausgaben", fontSize = 11.sp, color = DarkNavy, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WohneinheitenStatusSection(
    viewModel: ReceiptViewModel,
    receipts: List<Receipt>
) {
    val wohneinheiten by viewModel.wohneinheitenStatus.collectAsState()
    var selectedUnitForEdit by remember { mutableStateOf<WohneinheitStatus?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Status der ${wohneinheiten.size} Wohneinheiten",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = DarkNavy
            )
            Text(
                "Klick zum Editieren",
                fontSize = 11.sp,
                color = SlateGray,
                fontStyle = FontStyle.Italic
            )
        }

        wohneinheiten.forEach { unit ->
            val unitIncome = receipts
                .filter { it.wohneinheit == unit.name && (it.hauptkategorie == "Miete, Nebenkosten & Kaution" || it.hauptkategorie == "Sonstige Einnahmen") }
                .sumOf { it.bruttobetrag }

            val unitExpenses = receipts
                .filter { it.wohneinheit == unit.name && !(it.hauptkategorie == "Miete, Nebenkosten & Kaution" || it.hauptkategorie == "Sonstige Einnahmen") }
                .sumOf { it.bruttobetrag }

            val unitNet = unitIncome - unitExpenses

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedUnitForEdit = unit }
                    .testTag("unit_card_${unit.name.lowercase().replace(" ", "_")}"),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = unit.label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkNavy
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${unit.wohnflaeche} m²",
                                    fontSize = 11.sp,
                                    color = SlateGray
                                )
                                Text(
                                    text = "•",
                                    fontSize = 11.sp,
                                    color = Color.LightGray
                                )
                                Text(
                                    text = "Soll-Kaltmiete: ${NumberFormatter.format(unit.kaltmiete)}",
                                    fontSize = 11.sp,
                                    color = SlateGray
                                )
                            }
                        }

                        val badgeColor = when (unit.status) {
                            "Vermietet" -> EmeraldGreen
                            "Sanierung" -> WarmOrange
                            else -> CrimsonRed
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(badgeColor.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = unit.status,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = BorderColor.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Mieter",
                                tint = SlateGray,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (unit.status == "Vermietet") unit.mieter else if (unit.status == "Sanierung") "Sanierungsphase" else "Leerstand",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (unit.status == "Vermietet") DarkNavy else Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Einn:", fontSize = 10.sp, color = Color.Gray)
                                    Text(NumberFormatter.format(unitIncome), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Ausg:", fontSize = 10.sp, color = Color.Gray)
                                    Text(NumberFormatter.format(unitExpenses), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CrimsonRed)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (unitNet >= 0) EmeraldGreen.copy(alpha = 0.1f) else CrimsonRed.copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = (if (unitNet >= 0) "+" else "") + NumberFormatter.format(unitNet),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (unitNet >= 0) EmeraldGreen else CrimsonRed
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedUnitForEdit?.let { unit ->
        var editLabel by remember { mutableStateOf(unit.label) }
        var editStatus by remember { mutableStateOf(unit.status) }
        var editMieter by remember { mutableStateOf(unit.mieter) }
        var editRentStr by remember { mutableStateOf(unit.kaltmiete.toString()) }
        var editAreaStr by remember { mutableStateOf(unit.wohnflaeche.toString()) }

        var statusExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { selectedUnitForEdit = null },
            title = {
                Text(
                    text = "Details bearbeiten: ${unit.name}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkNavy
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = editLabel,
                        onValueChange = { editLabel = it },
                        label = { Text("Bezeichnung") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_unit_label")
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = editStatus,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Status") },
                            trailingIcon = {
                                IconButton(onClick = { statusExpanded = !statusExpanded }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                                }
                            },
                            modifier = Modifier.fillMaxWidth().clickable { statusExpanded = !statusExpanded }.testTag("edit_unit_status")
                        )
                        DropdownMenu(
                            expanded = statusExpanded,
                            onDismissRequest = { statusExpanded = false }
                        ) {
                            listOf("Vermietet", "Leerstand", "Sanierung").forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s) },
                                    onClick = {
                                        editStatus = s
                                        statusExpanded = false
                                        if (s != "Vermietet") {
                                            editMieter = if (s == "Sanierung") "Unbewohnt (Eigenleistung)" else "Keiner"
                                        }
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editMieter,
                        onValueChange = { editMieter = it },
                        label = { Text("Mieter") },
                        enabled = editStatus == "Vermietet" || editStatus == "Sanierung",
                        modifier = Modifier.fillMaxWidth().testTag("edit_unit_mieter")
                    )

                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        if (maxWidth < 360.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = editRentStr,
                                    onValueChange = { editRentStr = it },
                                    label = { Text("Kaltmiete (€)") },
                                    modifier = Modifier.fillMaxWidth().testTag("edit_unit_rent")
                                )
                                OutlinedTextField(
                                    value = editAreaStr,
                                    onValueChange = { editAreaStr = it },
                                    label = { Text("Fläche (m²)") },
                                    modifier = Modifier.fillMaxWidth().testTag("edit_unit_area")
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = editRentStr,
                                    onValueChange = { editRentStr = it },
                                    label = { Text("Kaltmiete (€)") },
                                    modifier = Modifier.weight(1f).testTag("edit_unit_rent")
                                )
                                OutlinedTextField(
                                    value = editAreaStr,
                                    onValueChange = { editAreaStr = it },
                                    label = { Text("Fläche (m²)") },
                                    modifier = Modifier.weight(1f).testTag("edit_unit_area")
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val rent = editRentStr.toDoubleOrNull() ?: unit.kaltmiete
                        val area = editAreaStr.toDoubleOrNull() ?: unit.wohnflaeche
                        viewModel.updateWohneinheit(
                            WohneinheitStatus(
                                name = unit.name,
                                label = editLabel,
                                status = editStatus,
                                mieter = editMieter,
                                kaltmiete = rent,
                                wohnflaeche = area
                            )
                        )
                        selectedUnitForEdit = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SlateGray)
                ) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedUnitForEdit = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
fun DashboardScreen(viewModel: ReceiptViewModel) {
    val receipts by viewModel.receipts.collectAsState()
    val learnedRulesCount by viewModel.learnedRulesCount.collectAsState()
    val bankStatementResult by viewModel.bankStatementResult.collectAsState()
    val missingReceiptsCount = bankStatementResult?.missingReceiptsCount ?: 0
    val rentArrearsCount = bankStatementResult?.rentArrearsCount ?: 0
    val totalBankAlerts = missingReceiptsCount + rentArrearsCount
    val totalAnschaffung = receipts.filter { it.hauptkategorie == "Anschaffungskosten" }.sumOf { it.bruttobetrag }
    val totalFinanzierung = receipts.filter { it.hauptkategorie == "Finanzierung, Kredite & Versicherungen" }.sumOf { it.bruttobetrag }
    val totalRenovierung = receipts.filter { it.hauptkategorie == "Renovierungs- / Reparaturkosten & Investitionen" }.sumOf { it.bruttobetrag }
    val totalSonstige = receipts.filter { it.hauptkategorie == "Sonstige Ausgaben" }.sumOf { it.bruttobetrag }
    val totalExpenses = totalAnschaffung + totalFinanzierung + totalRenovierung + totalSonstige
    val totalIncome = receipts.filter { it.hauptkategorie == "Miete, Nebenkosten & Kaution" || it.hauptkategorie == "Sonstige Einnahmen" }.sumOf { it.bruttobetrag }
    val netCashflow = totalIncome - totalExpenses
    var showKiPowerCenterDialog by remember { mutableStateOf(false) }
    var selectedReceipt by remember { mutableStateOf<Receipt?>(null) }
    if (showKiPowerCenterDialog) KiPowerCenterDialog(viewModel) { showKiPowerCenterDialog = false }
    selectedReceipt?.let { receipt -> ReceiptDetailDialog(receipt, viewModel) { selectedReceipt = null } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Ui2.padding),
        verticalArrangement = Arrangement.spacedBy(Ui2.spacing)
    ) {
        Ui2Section("Guten Tag!") {
            Text("Schön, dass du da bist.", style = MaterialTheme.typography.bodyLarge)
            Text(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMAN)),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Ui2Section("Aktueller Stand") {
            Ui2Grid(listOf(
                "Belege gesamt" to receipts.size.toString(),
                "Fehlende Belege" to missingReceiptsCount.toString(),
                "Mietrückstände" to rentArrearsCount.toString(),
                "Gelernte Regeln" to learnedRulesCount.toString()
            )) { metric, modifier -> Ui2Metric(metric.first, metric.second, modifier) }
            if (totalBankAlerts > 0) {
                Ui2Destination("$totalBankAlerts Hinweise aus dem Bankabgleich",
                    "$missingReceiptsCount fehlende Belege · $rentArrearsCount Mietrückstände",
                    Icons.Default.Warning) { showKiPowerCenterDialog = true }
            }
        }
        Ui2Section("Schnellaktionen") {
            Ui2ActionGrid(listOf(
                Ui2Action("Beleg scannen", "Scan & Upload Center", Icons.Default.PhotoCamera) { viewModel.setScreen(AppScreen.ADD_RECEIPT) },
                Ui2Action("Beleg hochladen", "Datei oder Bild auswählen", Icons.Default.Description) { viewModel.setScreen(AppScreen.ADD_RECEIPT) },
                Ui2Action("Kontoauszüge importieren", "Bank / Kontoauszüge", Icons.Default.AccountBalance) { viewModel.setScreen(AppScreen.BANK) },
                Ui2Action("Neue Buchung", "Beleg manuell erfassen", Icons.Default.Add, EmeraldGreen) { viewModel.setScreen(AppScreen.ADD_RECEIPT) }
            ))
        }
        Ui2Section("Letzte Aktivitäten") {
            // The source has receipt dates, not an audit event stream; label these honestly.
            Text("Zuletzt datierte Belege", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (receipts.isEmpty()) Text("Noch keine Belege erfasst.")
            receipts.take(5).forEach { receipt ->
                Ui2Destination(receipt.aussteller.ifBlank { receipt.getEffectiveDisplayId() },
                    "${receipt.datum} · ${NumberFormatter.format(receipt.bruttobetrag)}",
                    Icons.Default.Receipt) { selectedReceipt = receipt }
            }
            TextButton(onClick = { viewModel.setScreen(AppScreen.RECEIPTS_LIST) }) { Text("Alle Belege anzeigen") }
        }
        Ui2Section("Einnahmen & Ausgaben") {
            Ui2Grid(listOf(
                "Einnahmen" to NumberFormatter.format(totalIncome),
                "Ausgaben" to NumberFormatter.format(totalExpenses),
                "Saldo" to NumberFormatter.format(netCashflow)
            )) { metric, modifier -> Ui2Metric(metric.first, metric.second, modifier) }
        }
        LoanManagementSection(viewModel)
        Ui2Section("Weitere Übersichten") {
            Ui2Destination("Finanzen", "Auswertung", Icons.Default.AccountBalance) { viewModel.setScreen(AppScreen.LEDGER) }
            Ui2Destination("Fahrtenbuch", "Fahrten erfassen", Icons.Default.DirectionsCar) { viewModel.setScreen(AppScreen.LOGBOOK) }
            Ui2Destination("Steuerschätzung", "Anlage V", Icons.Default.Calculate) { viewModel.setScreen(AppScreen.TAX_CALCULATOR) }
            Box(Modifier.testTag("rent_overview_quick_action")) {
                Ui2Destination("Mieteingänge", "Soll/Ist & Nebenkosten", Icons.Default.Home) { viewModel.setScreen(AppScreen.RENT_OVERVIEW) }
            }
            Ui2Destination("Dokumentenakte", "Verträge, Stammdaten und Volltextsuche", Icons.Default.Description) { viewModel.setScreen(AppScreen.DOCUMENTS) }
        }
    }
}

/** The existing tax presentation moved from Start into More; the calculator is unchanged. */
@Composable
internal fun PropertyTaxUi2Screen(viewModel: ReceiptViewModel, monitor: Boolean, onBack: () -> Unit) {
    val receipts by viewModel.receipts.collectAsState()
    val propertyMetadataState by viewModel.propertyMetadata.collectAsState()
    val metadata = propertyMetadataState ?: PropertyMetadata()
    val taxPhase1 = com.example.data.TaxPropertyCalculator.calculate(metadata, receipts)
    var showAfaDetails by remember { mutableStateOf(false) }
    var showMonitorDetails by remember { mutableStateOf(false) }
    var showEditPropertyDialog by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(onBack = onBack)
    if (showEditPropertyDialog) PropertyMetadataFormDialog(viewModel) { showEditPropertyDialog = false }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Ui2.padding),
        verticalArrangement = Arrangement.spacedBy(Ui2.spacing)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück zu Mehr") }
            Text(if (monitor) "Sanierungs-Monitor" else "AfA Gebäude", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
        }
        Ui2Section(metadata.name.ifBlank { "Immobilie" }) {
            Text(metadata.adresse, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { showEditPropertyDialog = true }) { Text("Stammdaten bearbeiten") }
        }
        if (!monitor) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderColor),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("AfA Gebäude", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text("${taxPhase1.afaRatePercent}% p.a.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                }
                Text("Gebäude-Kaufpreisanteil: ${NumberFormatter.format(taxPhase1.buildingPurchaseShare)}", fontSize = 11.sp, color = SlateGray)
                Text("Grund und Boden: ${NumberFormatter.format(taxPhase1.landPurchaseShare)}", fontSize = 11.sp, color = SlateGray)
                Text("+ anteilige Anschaffungsnebenkosten: ${NumberFormatter.format(taxPhase1.buildingAncillaryShare)}", fontSize = 11.sp, color = SlateGray)
                if (taxPhase1.allocationNeedsReview) {
                    Text("⚠ Kaufpreisaufteilung weicht um ${NumberFormatter.format(taxPhase1.allocationDifference)} vom Gesamtkaufpreis ab.", fontSize = 10.sp, color = WarmOrange)
                }
                HorizontalDivider(color = BorderColor)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("AfA-Bemessungsgrundlage", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkNavy)
                    Text(NumberFormatter.format(taxPhase1.buildingAcquisitionCosts), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("AfA volles Jahr", fontSize = 12.sp, color = SlateGray)
                    Text(NumberFormatter.format(taxPhase1.annualAfa), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                }
                if (taxPhase1.afaStartDate.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Erstes Jahr ab ${taxPhase1.afaStartDate}", fontSize = 11.sp, color = SlateGray)
                        Text(NumberFormatter.format(taxPhase1.firstYearAfa), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                    }
                }
                Text(
                    "Vorbereitungswert. Kaufpreisaufteilung, Nebenkosten und AfA bitte vor der Steuererklärung prüfen.",
                    fontSize = 10.sp,
                    color = SlateGray,
                    lineHeight = 13.sp
                )
                TextButton(onClick = { showAfaDetails = true }, modifier = Modifier.align(Alignment.End)) {
                    Text("Aufteilung & Nebenkosten anzeigen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }


        } else {
        val compactProgress15 = if (taxPhase1.limit15Percent > 0.0) {
            (taxPhase1.relevantModernizationNet / taxPhase1.limit15Percent).toFloat().coerceIn(0f, 1f)
        } else 0f
        val compact15Color = when {
            taxPhase1.is15PercentExceeded -> CrimsonRed
            compactProgress15 >= 0.8f -> WarmOrange
            else -> EmeraldGreen
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, if (taxPhase1.is15PercentExceeded) CrimsonRed else BorderColor),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("15%-Sanierungsmonitor", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text("${"%.1f".format(taxPhase1.limitUsagePercent)}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = compact15Color)
                }
                LinearProgressIndicator(
                    progress = { compactProgress15 },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = compact15Color,
                    trackColor = Color(0xFFE2E8F0)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Prüfsumme netto", fontSize = 11.sp, color = SlateGray)
                    Text(NumberFormatter.format(taxPhase1.relevantModernizationNet), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("15%-Prüfwert", fontSize = 11.sp, color = SlateGray)
                    Text(NumberFormatter.format(taxPhase1.limit15Percent), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                }
                Text(
                    "Zeitraum: ${taxPhase1.monitorStartDate.ifBlank { "nicht festgelegt" }} bis ${taxPhase1.monitorEndDate.ifBlank { "nicht festgelegt" }} • ${taxPhase1.candidateReceiptCount} potenziell relevante Belege" +
                        if (taxPhase1.estimatedNetCount > 0) " • ${taxPhase1.estimatedNetCount} Nettobetrag/-beträge geschätzt" else "",
                    fontSize = 10.sp,
                    color = SlateGray,
                    lineHeight = 13.sp
                )
                TextButton(onClick = { showMonitorDetails = true }, modifier = Modifier.align(Alignment.End)) {
                    Text("Belege im Monitor anzeigen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                if (taxPhase1.is15PercentExceeded) {
                    Text(
                        "Prüfwert überschritten: steuerliche Einordnung fachlich prüfen.",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonRed
                    )
                }
            }
        }


        }
        if (showAfaDetails) {
            AlertDialog(
                onDismissRequest = { showAfaDetails = false },
                title = { Text("AfA – Kaufpreisaufteilung", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Kaufpreis: ${NumberFormatter.format(taxPhase1.purchasePrice)}", fontWeight = FontWeight.Bold)
                        Text("Gebäude: ${NumberFormatter.format(taxPhase1.buildingPurchaseShare)}")
                        Text("Grund und Boden: ${NumberFormatter.format(taxPhase1.landPurchaseShare)}")
                        Text("Quelle: ${taxPhase1.allocationSource}", fontSize = 11.sp, color = SlateGray)
                        HorizontalDivider()
                        Text("Anschaffungsnebenkosten", fontWeight = FontWeight.Bold)
                        if (taxPhase1.acquisitionCostDetails.isEmpty()) {
                            Text("Keine Belege der Kategorie Anschaffungskosten vorhanden.", fontSize = 11.sp, color = SlateGray)
                        } else {
                            taxPhase1.acquisitionCostDetails.forEach { item ->
                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))) {
                                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("${item.displayId} • ${item.datum}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text(item.description, fontSize = 10.sp)
                                        Text("Gesamt: ${NumberFormatter.format(item.grossAmount)} • Gebäudeanteil: ${NumberFormatter.format(item.buildingAllocatedAmount)}", fontSize = 10.sp, color = SlateGray)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showAfaDetails = false }) { Text("Schließen") } }
            )
        }

        if (showMonitorDetails) {
            AlertDialog(
                onDismissRequest = { showMonitorDetails = false },
                title = { Text("15%-Monitor – Belegprüfung", fontWeight = FontWeight.Bold) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (taxPhase1.monitorDetails.isEmpty()) {
                            Text("Keine Renovierungs-/Reparaturbelege vorhanden.", color = SlateGray)
                        } else {
                            taxPhase1.monitorDetails.forEach { item ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = if (item.included) Color(0xFFECFDF5) else Color(0xFFF8FAFC)),
                                    border = BorderStroke(1.dp, if (item.included) EmeraldGreen.copy(alpha = 0.35f) else BorderColor)
                                ) {
                                    Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("${item.displayId} • ${item.datum}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            Text(if (item.included) "EINBEZOGEN" else "AUSGESCHLOSSEN", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (item.included) EmeraldGreen else SlateGray)
                                        }
                                        Text(item.description, fontSize = 10.sp)
                                        Text(item.reason, fontSize = 9.sp, color = SlateGray)
                                        if (item.included) Text("Netto Prüfwert: ${NumberFormatter.format(item.netAmount)}" + if (item.estimatedNet) " (geschätzt)" else "", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showMonitorDetails = false }) { Text("Schließen") } }
            )
        }


    }
}

@Composable
fun QuickActionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .heightIn(min = 80.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.2f))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val compactCard = maxWidth < 125.dp
            if (compactCard) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(contentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        color = DarkNavy,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        color = Color.Gray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(contentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (title.length > 12) 11.sp else 13.sp,
                            lineHeight = 16.sp,
                            color = DarkNavy,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            color = Color.Gray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InfoColumn(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
    }
}

@Composable
fun CategoryLegendRow(color: Color, label: String, value: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                label,
                fontSize = 11.sp,
                color = DarkNavy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            NumberFormatter.format(value),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = DarkNavy
        )
    }
}

@Composable
fun AiSearchCard(
    viewModel: ReceiptViewModel,
    modifier: Modifier = Modifier
) {
    val aiSearchState by viewModel.aiSearchState.collectAsState()
    var inputQuery by remember { mutableStateOf("") }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F7FF)),
        border = BorderStroke(1.dp, Color(0xFFC7D2FE))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6))
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Gemini KI-Suche in Room-Belegen",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Text(
                        "Fragen in natürlicher Sprache an deine Belegsdatenbank",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }

                if (aiSearchState.result != null || aiSearchState.error != null) {
                    TextButton(
                        onClick = {
                            viewModel.clearAiSearch()
                            inputQuery = ""
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Zurücksetzen", fontSize = 12.sp, color = Color(0xFF2563EB))
                    }
                }
            }

            // Input Field
            OutlinedTextField(
                value = inputQuery,
                onValueChange = { inputQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_search_input"),
                placeholder = { Text("z.B. Wie viel habe ich dieses Jahr für die Heizung ausgegeben?", fontSize = 12.sp) },
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (inputQuery.isNotBlank()) {
                                viewModel.performAiSearch(inputQuery)
                            }
                        },
                        enabled = inputQuery.isNotBlank() && !aiSearchState.isLoading,
                        modifier = Modifier.testTag("ai_search_submit_button")
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "KI Fragen",
                            tint = if (inputQuery.isNotBlank()) Color(0xFF2563EB) else Color.Gray
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFFCBD5E1),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            )

            // Example Question Chips
            val exampleQueries = listOf(
                "Wie viel habe ich dieses Jahr für die Heizung ausgegeben?",
                "Zeige Baumarkt-Rechnungen",
                "Ausgaben für Reparaturen"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                exampleQueries.forEach { q ->
                    SuggestionChip(
                        onClick = {
                            inputQuery = q
                            viewModel.performAiSearch(q)
                        },
                        label = { Text(q, fontSize = 11.sp) },
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = Color(0xFFBFDBFE)
                        ),
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = Color.White
                        )
                    )
                }
            }

            // Loading state
            if (aiSearchState.isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF2563EB)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Gemini analysiert die Room-Datenbank...",
                        fontSize = 13.sp,
                        color = Color(0xFF1E293B),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // AI Result Card
            aiSearchState.result?.let { res ->
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("ai_search_result_card"),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "✨ Gemini KI-Antwort",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E40AF)
                            )
                            res.totalAmount?.let { amount ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFDCFCE7)
                                ) {
                                    Text(
                                        text = String.format(java.util.Locale.GERMANY, "Summe: %.2f €", amount),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF166534)
                                    )
                                }
                            }
                        }

                        Text(
                            text = res.answer,
                            fontSize = 13.sp,
                            color = Color(0xFF334155),
                            lineHeight = 18.sp
                        )

                        if (res.matchingReceiptIds.isNotEmpty()) {
                            Text(
                                text = "🎯 Room-Datenbank unten gefiltert auf ${res.matchingReceiptIds.size} Beleg(e).",
                                fontSize = 11.sp,
                                color = Color(0xFF2563EB),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // AI Error Card
            aiSearchState.error?.let { err ->
                Text(
                    text = err,
                    fontSize = 12.sp,
                    color = Color(0xFFDC2626)
                )
            }
        }
    }
}

// --- SCREEN 2: RECEIPTS LIST ---

@Composable
fun ReceiptThumbnailBox(
    imageUrl: String,
    receipt: Receipt,
    modifier: Modifier = Modifier,
    onZoomClick: ((Bitmap) -> Unit)? = null
) {
    val paths = remember(imageUrl) {
        if (imageUrl.isNotEmpty()) imageUrl.split(",") else emptyList()
    }
    val firstPath = paths.firstOrNull()

    val bitmap = remember(firstPath) {
        if (!firstPath.isNullOrEmpty()) {
            try {
                BitmapFactory.decodeFile(firstPath)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    val categoryColor = when (receipt.hauptkategorie) {
        "Anschaffungskosten" -> AccentBlue
        "Finanzierung, Kredite & Versicherungen" -> WarmOrange
        "Renovierungs- / Reparaturkosten & Investitionen" -> EmeraldGreen
        else -> CrimsonRed
    }

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = bitmap != null) {
                if (bitmap != null && onZoomClick != null) onZoomClick(bitmap)
            },
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Beleg Scan Vorschau",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Overlay Badge for Scan & Zoom
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = DarkNavy.copy(alpha = 0.75f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Zoom",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                        if (paths.size > 1) {
                            Text(
                                text = "${paths.size} S.",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // High precision digital receipt document preview graphic
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFFFFFFFF), Color(0xFFF1F5F9))
                            )
                        )
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = categoryColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = receipt.kontoNr.ifEmpty { "BELEG" },
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = categoryColor
                            )
                        }
                        Text(
                            text = receipt.datum.takeLast(5),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SlateGray
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = receipt.aussteller.ifEmpty { "Eigenbeleg" },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkNavy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = NumberFormatter.format(receipt.bruttobetrag),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = DarkNavy
                        )
                    }

                    // Bottom simulated barcode line
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(6.dp)
                            .background(Color.LightGray.copy(alpha = 0.4f), RoundedCornerShape(1.dp)),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(6) {
                            Box(
                                modifier = Modifier
                                    .width(1.5.dp)
                                    .fillMaxHeight()
                                    .background(Color.DarkGray.copy(alpha = 0.6f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReceiptGridCard(
    receipt: Receipt,
    onItemClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onZoomClick: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = when (receipt.hauptkategorie) {
        "Anschaffungskosten" -> AccentBlue
        "Finanzierung, Kredite & Versicherungen" -> WarmOrange
        "Renovierungs- / Reparaturkosten & Investitionen" -> EmeraldGreen
        else -> CrimsonRed
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .testTag("receipt_grid_item_${receipt.id}"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Receipt Document Preview Box at top of Card - compact height
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(Color(0xFFF1F5F9))
                    .padding(5.dp)
            ) {
                ReceiptThumbnailBox(
                    imageUrl = receipt.imageUrl,
                    receipt = receipt,
                    modifier = Modifier.fillMaxSize(),
                    onZoomClick = onZoomClick
                )
            }

            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Category & Date Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.weight(1f, fill = false),
                        shape = RoundedCornerShape(4.dp),
                        color = categoryColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = receipt.hauptkategorie,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = categoryColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = receipt.datum,
                        fontSize = 9.5.sp,
                        color = SlateGray,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Aussteller
                Text(
                    text = receipt.aussteller.ifEmpty { "Unbekannter Aussteller" },
                    fontWeight = FontWeight.Bold,
                    color = DarkNavy,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Description
                val desc = receipt.beschreibung.ifEmpty { receipt.unterkategorie }
                if (desc.isNotEmpty()) {
                    Text(
                        text = desc,
                        fontSize = 10.5.sp,
                        color = Color.DarkGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (receipt.mieter.isNotEmpty()) {
                    Text(
                        text = "Mieter: ${receipt.mieter}",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SlateGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (receipt.wohneinheit.isNotEmpty() && receipt.wohneinheit != "Gesamtobjekt / Allgemein") {
                    Text(
                        text = receipt.wohneinheit,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SlateGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(vertical = 1.dp))

                // Price & Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = NumberFormatter.format(receipt.bruttobetrag),
                        fontWeight = FontWeight.Black,
                        color = DarkNavy,
                        fontSize = 13.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = onItemClick,
                            modifier = Modifier.size(24.dp).testTag("preview_receipt_${receipt.id}")
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "Details", tint = AccentBlue, modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.size(24.dp).testTag("delete_receipt_${receipt.id}")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = CrimsonRed, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReceiptsListScreen(viewModel: ReceiptViewModel) {
    val query by viewModel.searchQuery.collectAsState()
    val filter by viewModel.selectedCategoryFilter.collectAsState()
    val receipts by viewModel.filteredReceipts.collectAsState()
    var selectedReceiptForDetail by remember { mutableStateOf<Receipt?>(null) }
    var fullScreenPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // Toggle for View Modes: "grid", "list", "table"
        var viewMode by remember { mutableStateOf("list") }
    var receiptToDelete by remember { mutableStateOf<Receipt?>(null) }
    var showRecycleBinFromBelege by remember { mutableStateOf(false) }

    // All processed receipts for overall category summaries
    val allReceipts by viewModel.receipts.collectAsState()
    
    // Group all processed receipts by main category and sum expenditures
    val categoriesWithSums = remember(allReceipts) {
        allReceipts.groupBy { it.hauptkategorie }
            .map { (cat, list) -> cat to list.sumOf { it.bruttobetrag } }
            .sortedByDescending { it.second }
    }

    if (fullScreenPreviewBitmap != null) {
        FullScreenReceiptPreviewDialog(fullScreenPreviewBitmap!!) { fullScreenPreviewBitmap = null }
    }

    if (showRecycleBinFromBelege) {
        RecycleBinDialog(viewModel = viewModel, onDismiss = { showRecycleBinFromBelege = false })
    }

    receiptToDelete?.let { pending ->
        ReceiptDeleteConfirmationDialog(
            onDismiss = { receiptToDelete = null },
            onConfirm = {
                receiptToDelete = null
                viewModel.deleteReceipt(pending.id)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Belegs-Archiv",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = DarkNavy
                    )
                    Text(
                        "Vorschau & Belegs-Verwaltung",
                        fontSize = 11.sp,
                        color = SlateGray
                    )
                }
                IconButton(
                    onClick = { showRecycleBinFromBelege = true },
                    modifier = Modifier.size(36.dp).testTag("list_open_recycle_bin_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Papierkorb",
                        tint = CrimsonRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            // Separate row avoids squeezing title and selector on narrow screens.
            Row(
                modifier = Modifier
                    .align(Alignment.End)
                    .background(BorderColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (viewMode == "grid") DarkNavy else Color.Transparent)
                        .clickable { viewMode = "grid" }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Raster",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (viewMode == "grid") Color.White else SlateGray
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (viewMode == "list") DarkNavy else Color.Transparent)
                        .clickable { viewMode = "list" }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Liste",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (viewMode == "list") Color.White else SlateGray
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (viewMode == "table") DarkNavy else Color.Transparent)
                        .clickable { viewMode = "table" }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Tabelle",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (viewMode == "table") Color.White else SlateGray
                    )
                }
            }
        }

        // Summary Statistics Ribbon Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Receipt, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                        Text("${receipts.size} Belege", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(14.dp))
                        Text("${receipts.count { it.imageUrl.isNotEmpty() }} Scans", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Gesamt: ", fontSize = 11.sp, color = SlateGray)
                    Text(NumberFormatter.format(receipts.sumOf { it.bruttobetrag }), fontSize = 13.sp, fontWeight = FontWeight.Black, color = DarkNavy)
                }
            }
        }

        // Gemini AI Natural Language Search Card
        AiSearchCard(viewModel = viewModel)

        // Search Bar & Filters
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_field"),
            placeholder = { Text("Aussteller, Beschreibung, Konto...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Suchen") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Löschen")
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = BorderColor,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            ),
            singleLine = true,
            shape = RoundedCornerShape(8.dp)
        )

        // Category Quick Filters
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryFilterChip(
                label = "Alle",
                isSelected = filter == null,
                onClick = { viewModel.setCategoryFilter(null) }
            )
            CategoryFilterChip(
                label = "Anschaffung",
                isSelected = filter == "Anschaffungskosten",
                onClick = { viewModel.setCategoryFilter("Anschaffungskosten") }
            )
            CategoryFilterChip(
                label = "Finanzierung",
                isSelected = filter == "Finanzierung, Kredite & Versicherungen",
                onClick = { viewModel.setCategoryFilter("Finanzierung, Kredite & Versicherungen") }
            )
            CategoryFilterChip(
                label = "Renovierung",
                isSelected = filter == "Renovierungs- / Reparaturkosten & Investitionen",
                onClick = { viewModel.setCategoryFilter("Renovierungs- / Reparaturkosten & Investitionen") }
            )
            CategoryFilterChip(
                label = "Betriebskosten",
                isSelected = filter == "Betriebs- / Nebenkosten",
                onClick = { viewModel.setCategoryFilter("Betriebs- / Nebenkosten") }
            )
            CategoryFilterChip(
                label = "Miete & Kaution",
                isSelected = filter == "Miete, Nebenkosten & Kaution",
                onClick = { viewModel.setCategoryFilter("Miete, Nebenkosten & Kaution") }
            )
            CategoryFilterChip(
                label = "Sonst. Ausgaben",
                isSelected = filter == "Sonstige Ausgaben",
                onClick = { viewModel.setCategoryFilter("Sonstige Ausgaben") }
            )
            CategoryFilterChip(
                label = "Sonst. Einnahmen",
                isSelected = filter == "Sonstige Einnahmen",
                onClick = { viewModel.setCategoryFilter("Sonstige Einnahmen") }
            )
        }

        if (viewMode == "grid") {
            // Grid / Preview Cards View (Rasteransicht mit Beleg-Vorschau Header)
            if (receipts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageName = Icons.Default.Description,
                            contentDescription = "Keine Belege",
                            tint = Color.LightGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Keine Belege gefunden", color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text("Nutze den Scanner, um neue Belege zu erfassen.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("receipts_grid"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    receipts.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                ReceiptGridCard(
                                    receipt = pair[0],
                                    onItemClick = { selectedReceiptForDetail = pair[0] },
                                    onDeleteClick = { receiptToDelete = pair[0] },
                                    onZoomClick = { bmp -> fullScreenPreviewBitmap = bmp },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (pair.size > 1) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    ReceiptGridCard(
                                        receipt = pair[1],
                                        onItemClick = { selectedReceiptForDetail = pair[1] },
                                        onDeleteClick = { receiptToDelete = pair[1] },
                                        onZoomClick = { bmp -> fullScreenPreviewBitmap = bmp },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        } else if (viewMode == "list") {
            // Receipt List items with Preview Thumbnail (Listenansicht)
            if (receipts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageName = Icons.Default.Description,
                            contentDescription = "Keine Belege",
                            tint = Color.LightGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Keine Belege gefunden", color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text("Nutze den Scanner, um neue Belege zu erfassen.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("receipts_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    receipts.forEach { receipt ->
                        ReceiptRowItem(
                            receipt = receipt,
                            onItemClick = { selectedReceiptForDetail = receipt },
                            onDeleteClick = { receiptToDelete = receipt },
                            onZoomClick = { bmp -> fullScreenPreviewBitmap = bmp }
                        )
                    }
                }
            }
        } else {
            // Tabular & Analytic View (Tabellenansicht)
            
            // 1. Sum of Expenditures per Main Category
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Summe der Ausgaben pro Hauptkategorie",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkNavy
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (categoriesWithSums.isEmpty()) {
                        Text(
                            "Keine verarbeiteten Belege vorhanden.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            categoriesWithSums.forEach { (category, total) ->
                                val categoryColor = when (category) {
                                    "Anschaffungskosten" -> AccentBlue
                                    "Finanzierung, Kredite & Versicherungen" -> WarmOrange
                                    "Renovierungs- / Reparaturkosten & Investitionen" -> EmeraldGreen
                                    else -> CrimsonRed
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(categoryColor, CircleShape)
                                        )
                                        Text(
                                            text = category,
                                            fontSize = 12.sp,
                                            color = SlateGray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = NumberFormatter.format(total),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = DarkNavy
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Tabular spreadsheet view of receipts, sorted by date
            var sortAscending by remember { mutableStateOf(false) }
            
            // Sort receipts by date and time (and also apply currently selected search query & category filter)
            val tableReceipts = remember(receipts, sortAscending) {
                if (sortAscending) {
                    receipts.sortedWith(compareBy<Receipt> { it.datum }.thenBy { it.uhrzeit })
                } else {
                    receipts.sortedWith(compareByDescending<Receipt> { it.datum }.thenByDescending { it.uhrzeit })
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Beleg-Tabelle (nach Datum sortiert)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkNavy
                    )
                    Text(
                        text = if (sortAscending) "Datum aufsteigend" else "Datum absteigend",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                
                if (tableReceipts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Keine Belege für die aktuellen Filter vorhanden.", color = Color.Gray, fontSize = 12.sp)
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        // Horizontal scroll for spreadsheet, rows render vertically inside outer scrollable page
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Column {
                                // Header Row
                                Row(
                                    modifier = Modifier
                                        .background(SoftBackground)
                                        .padding(vertical = 10.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Interactive sorting date header
                                    Row(
                                        modifier = Modifier
                                            .width(105.dp)
                                            .clickable { sortAscending = !sortAscending },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            "Datum",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = SlateGray
                                        )
                                        Icon(
                                            imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                            contentDescription = "Sortieren",
                                            tint = SlateGray,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    
                                    Text(
                                        "Aussteller",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = SlateGray,
                                        modifier = Modifier.width(150.dp)
                                    )
                                    
                                    Text(
                                        "Hauptkategorie",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = SlateGray,
                                        modifier = Modifier.width(180.dp)
                                    )
                                    
                                    Text(
                                        "Konto",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = SlateGray,
                                        modifier = Modifier.width(80.dp)
                                    )
                                    
                                    Text(
                                        "Beschreibung",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = SlateGray,
                                        modifier = Modifier.width(180.dp)
                                    )
                                    
                                    Text(
                                        "Betrag (Brutto)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = SlateGray,
                                        modifier = Modifier.width(110.dp),
                                        textAlign = TextAlign.End
                                    )
                                    Text(
                                        "Archiv",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = SlateGray,
                                        modifier = Modifier.width(60.dp),
                                        textAlign = TextAlign.Center
                                    )
                                
                                }
                                
                                // Divider below header
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(BorderColor)
                                )
                                
                                // Table Rows
                                tableReceipts.forEachIndexed { index, receipt ->
                                    val isEven = index % 2 == 0
                                    val rowBgColor = if (isEven) Color.White else SoftBackground
                                    
                                    Row(
                                        modifier = Modifier
                                            .background(rowBgColor)
                                            .clickable { selectedReceiptForDetail = receipt }
                                            .padding(vertical = 10.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = receipt.datum,
                                            fontSize = 12.sp,
                                            color = DarkNavy,
                                            modifier = Modifier.width(105.dp)
                                        )
                                        
                                        Text(
                                            text = receipt.aussteller,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DarkNavy,
                                            modifier = Modifier.width(150.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        
                                        val categoryColor = when (receipt.hauptkategorie) {
                                            "Anschaffungskosten" -> AccentBlue
                                            "Finanzierung, Kredite & Versicherungen" -> WarmOrange
                                            "Renovierungs- / Reparaturkosten & Investitionen" -> EmeraldGreen
                                            else -> CrimsonRed
                                        }
                                        
                                        Row(
                                            modifier = Modifier.width(180.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(categoryColor, CircleShape)
                                            )
                                            Text(
                                                text = receipt.hauptkategorie,
                                                fontSize = 11.sp,
                                                color = SlateGray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        
                                        Text(
                                            text = receipt.kontoNr,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = SlateGray,
                                            modifier = Modifier.width(80.dp)
                                        )
                                        
                                        Text(
                                            text = receipt.beschreibung.ifEmpty { "-" },
                                            fontSize = 11.sp,
                                            color = Color.Gray,
                                            modifier = Modifier.width(180.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        
                                        Text(
                                            text = NumberFormatter.format(receipt.bruttobetrag),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DarkNavy,
                                            modifier = Modifier.width(110.dp),
                                            textAlign = TextAlign.End
                                        )
                                        
                                        Box(
                                            modifier = Modifier.width(100.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            when (receipt.syncStatus) {
                                                "SYNCED" -> {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.Center
                                                     ) {
                                                         Icon(
                                                             imageVector = Icons.Default.Check,
                                                             contentDescription = "Synchronisiert",
                                                             tint = EmeraldGreen,
                                                             modifier = Modifier.size(16.dp)
                                                         )
                                                         Spacer(modifier = Modifier.width(4.dp))
                                                         Text(
                                                             text = "Gegen",
                                                             fontSize = 11.sp,
                                                             color = EmeraldGreen,
                                                             fontWeight = FontWeight.SemiBold
                                                         )
                                                     }
                                                 }
                                                 "PENDING" -> {
                                                     IconButton(
                                                         onClick = { viewModel.syncReceiptManually(receipt) },
                                                         modifier = Modifier.size(24.dp).testTag("sync_pending_button_${receipt.id}")
                                                     ) {
                                                         Icon(
                                                             imageVector = Icons.Default.Refresh,
                                                             contentDescription = "Ausstehend, zum Synchronisieren klicken",
                                                             tint = Color(0xFFFF9800),
                                                             modifier = Modifier.size(18.dp)
                                                         )
                                                     }
                                                 }
                                                 "ERROR" -> {
                                                     var showErrorDialog by remember { mutableStateOf(false) }
                                                     
                                                     Row(
                                                         verticalAlignment = Alignment.CenterVertically,
                                                         horizontalArrangement = Arrangement.Center
                                                     ) {
                                                         IconButton(
                                                             onClick = { showErrorDialog = true },
                                                             modifier = Modifier.size(24.dp).testTag("show_sync_error_button_${receipt.id}")
                                                         ) {
                                                             Icon(
                                                                 imageVector = Icons.Default.Warning,
                                                                 contentDescription = "Fehler anzeigen",
                                                                 tint = Color.Red,
                                                                 modifier = Modifier.size(18.dp)
                                                             )
                                                         }
                                                         Spacer(modifier = Modifier.width(2.dp))
                                                         IconButton(
                                                             onClick = { viewModel.syncReceiptManually(receipt) },
                                                             modifier = Modifier.size(24.dp).testTag("retry_sync_button_${receipt.id}")
                                                         ) {
                                                             Icon(
                                                                 imageVector = Icons.Default.Refresh,
                                                                 contentDescription = "Wiederholen",
                                                                 tint = DarkNavy,
                                                                 modifier = Modifier.size(18.dp)
                                                             )
                                                         }
                                                     }
                                                     
                                                     if (showErrorDialog) {
                                                         AlertDialog(
                                                             onDismissRequest = { showErrorDialog = false },
                                                             title = { Text("Synchronisationsfehler") },
                                                             text = { 
                                                                 Text(
                                                                     text = receipt.syncError ?: "Unbekannter Synchronisationsfehler.",
                                                                     fontSize = 14.sp
                                                                 )
                                                             },
                                                             confirmButton = {
                                                                 TextButton(onClick = { showErrorDialog = false }) {
                                                                     Text("Schließen")
                                                                 }
                                                             }
                                                         )
                                                     }
                                                 }
                                                 else -> {
                                                     Row(
                                                         verticalAlignment = Alignment.CenterVertically,
                                                         horizontalArrangement = Arrangement.Center
                                                     ) {
                                                         Icon(
                                                             imageVector = Icons.Default.Info,
                                                             contentDescription = "Review benötigt",
                                                             tint = Color(0xFFFBC02D),
                                                             modifier = Modifier.size(16.dp)
                                                         )
                                                         Spacer(modifier = Modifier.width(4.dp))
                                                         IconButton(
                                                             onClick = { viewModel.syncReceiptManually(receipt) },
                                                             modifier = Modifier.size(24.dp).testTag("retry_review_sync_button_${receipt.id}")
                                                         ) {
                                                             Icon(
                                                                 imageVector = Icons.Default.Refresh,
                                                                 contentDescription = "Neu synchronisieren",
                                                                 tint = DarkNavy,
                                                                 modifier = Modifier.size(16.dp)
                                                             )
                                                         }
                                                     }
                                                 }
                                             }
                                         }
                                    }
                                    
                                    Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(BorderColor.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail Dialog
    selectedReceiptForDetail?.let { receipt ->
        ReceiptDetailDialog(
            receipt = receipt,
            viewModel = viewModel,
            onDismiss = { selectedReceiptForDetail = null }
        )
    }
}

@Composable
fun CategoryFilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) SlateGray else Color.White,
        border = BorderStroke(1.dp, if (isSelected) SlateGray else BorderColor)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else DarkNavy,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun ReceiptRowItem(
    receipt: Receipt,
    onItemClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onZoomClick: ((Bitmap) -> Unit)? = null
) {
    val categoryColor = when (receipt.hauptkategorie) {
        "Anschaffungskosten" -> AccentBlue
        "Finanzierung, Kredite & Versicherungen" -> WarmOrange
        "Renovierungs- / Reparaturkosten & Investitionen" -> EmeraldGreen
        else -> CrimsonRed
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .testTag("receipt_item_${receipt.id}"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Receipt Document Preview Box on Left
                ReceiptThumbnailBox(
                    imageUrl = receipt.imageUrl,
                    receipt = receipt,
                    modifier = Modifier
                        .size(52.dp, 64.dp)
                        .testTag("receipt_thumbnail_${receipt.id}"),
                    onZoomClick = onZoomClick
                )

                // Metadata
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        receipt.aussteller,
                        fontWeight = FontWeight.Bold,
                        color = DarkNavy,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(receipt.datum, fontSize = 11.sp, color = Color.Gray)
                        Text("•", fontSize = 11.sp, color = Color.Gray)
                        Text(receipt.hauptkategorie, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = categoryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (receipt.mieter.isNotEmpty()) {
                        Text("Mieter: ${receipt.mieter}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SlateGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else if (receipt.wohneinheit.isNotEmpty() && receipt.wohneinheit != "Gesamtobjekt / Allgemein") {
                        Text(receipt.wohneinheit, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SlateGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        receipt.beschreibung.ifEmpty { receipt.unterkategorie },
                        fontSize = 11.sp,
                        color = Color.DarkGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Value & Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    NumberFormatter.format(receipt.bruttobetrag),
                    fontWeight = FontWeight.Black,
                    color = DarkNavy,
                    fontSize = 14.sp
                )
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp).testTag("delete_receipt_${receipt.id}")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = CrimsonRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

object ReceiptCategories {
    val mainCategories = listOf(
        "Anschaffungskosten",
        "Betriebs- / Nebenkosten",
        "Finanzierung, Kredite & Versicherungen",
        "Miete, Nebenkosten & Kaution",
        "Renovierungs- / Reparaturkosten & Investitionen",
        "Sonstige Ausgaben",
        "Sonstige Einnahmen"
    )

    val subCategoriesMap = mapOf(
        "Anschaffungskosten" to listOf(
            "Abbruchkosten", "Architekt", "Baukosten", "Erschließungskosten",
            "Grundbuchgebühren", "Grunderwerbsteuer", "Gutachter", "Kaufpreis Garagen",
            "Kaufpreis Objekt", "Kaufpreis Sonstiges", "Kaufpreis Stellplatz",
            "Maklerprovision", "Notarkosten", "Vermesser"
        ),
        "Betriebs- / Nebenkosten" to listOf(
            "Abwasser", "Allgemeinstrom", "Antenne/Kabelanschluss", "Aufzug sowie Aufzugswartung",
            "Betriebskosten", "Einmalige Ungezieferbekämpfung", "Entwässerung und Niederschlagswasser",
            "Fassadenreinigung", "Frischwasser", "Fußwegreinigung", "Gartenpflege", "Gebäudereinigung",
            "Gebäudeversicherung", "Grundsteuer", "Hausgeld und WEG Nebenkosten", "Hausverwaltungskosten",
            "Hauswart/Hausmeister", "Heiz- und Warmwasserkosten", "Heizkosten", "Kosten für Brennstoffe",
            "Legionellenuntersuchung", "Müllbeseitigung", "Nutzerwechselgebühren",
            "Regelmäßige Dachrinnenreinigung & Fassadenreinigung", "Regelmäßige Ungezieferbekämpfung",
            "Reinigung Öltank", "Reinigungskosten", "Sach- und Haftpflichtversicherung",
            "Schornsteinreinigung", "Sonstige Betriebskosten", "Sonstige Versicherungen",
            "Straßenreinigung", "Thermenwartung", "Wachdienst / Pförtner", "Warmwasserkosten",
            "Wartung Rauchmelder & Feuerlöscher", "Wartung der Heizungsanlage / Thermen", "Winterdienst"
        ),
        "Finanzierung, Kredite & Versicherungen" to listOf(
            "Erbpachtzins", "Geldbeschaffungskosten", "Kontoführungsgebühren",
            "Kreditauszahlung", "Kreditrate", "Kredittilgung", "Kreditzinsen",
            "Rechtsschutzversicherung", "Sondertilgung", "Vorfälligkeitsentschädigung"
        ),
        "Miete, Nebenkosten & Kaution" to listOf(
            "Einzahlung Kaution", "Garage & Stellplätze", "Kaltmiete", "Kaution",
            "Mietzuschlag", "Pauschalmiete", "Rückzahlung Kaution", "Stellplatz, Garage, Keller",
            "Warmmiete"
        ),
        "Renovierungs- / Reparaturkosten & Investitionen" to listOf(
            "Ausstattung", "Außenanlagen", "Bad", "Balkon & Terasse", "Dach & Fassade",
            "Elektrik & Beleuchtung", "Fenster, Tür & Boden", "Heizung & Therme", "Innenbereich",
            "Instandhaltungsrücklage", "Sanitär", "Schadensbeseitigung", "Sonstige Einrichtungen",
            "Streichen, Tapezieren", "Wärme- & Schalldämmung"
        ),
        "Sonstige Ausgaben" to listOf(
            "Anwaltskosten", "Einkommensteuer", "Entsorgung Hausrat", "Fahrtkosten",
            "Gebühren", "Gerichtskosten", "Inserate", "Kapitalertragssteuer",
            "Kostenaufwand für leerstehende Räumlichkeiten", "Privateinlage", "Privatentnahme",
            "Rechtsberatungskosten", "Sonstige", "Sonstiges", "Sperrmüllentsorgung",
            "Steuerberatungskosten", "Umsatzsteuer bei Gewerbe", "Umsatzsteuer-Vorauszahlung",
            "Vermesser", "Vermietung", "Verwaltungskosten des Vermieters", "Verwaltungskosten für Sozialwohnungen"
        ),
        "Sonstige Einnahmen" to listOf(
            "Einnahmen aus Münzwaschgeräten", "Einrichtungen der Wäschepflege", "Guthabenzins",
            "Gutschrift aus Betriebskostenabrechnung", "Nachzahlung aus Betriebskostenabrechnung"
        )
    )

    fun getSuggestedKonto(haupt: String, unter: String): String {
        return when (haupt) {
            "Anschaffungskosten" -> "0050"
            "Finanzierung, Kredite & Versicherungen" -> {
                when (unter) {
                    "Geldbeschaffungskosten" -> "2120"
                    "Kreditzinsen" -> "2110"
                    "Kontoführungsgebühren" -> "4970"
                    else -> "2120"
                }
            }
            "Renovierungs- / Reparaturkosten & Investitionen" -> "4830"
            "Sonstige Ausgaben" -> {
                if (unter == "Fahrtkosten") "4670" else "4970"
            }
            "Betriebs- / Nebenkosten" -> "4970"
            "Miete, Nebenkosten & Kaution" -> "4970"
            "Sonstige Einnahmen" -> "4970"
            else -> "4830"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptPositionenEditor(
    positionen: List<com.example.data.ReceiptItem>,
    onPositionenChanged: (List<com.example.data.ReceiptItem>) -> Unit
) {
    var showAddItemDialog by remember { mutableStateOf(false) }
    var newItemBezeichnung by remember { mutableStateOf("") }
    var newItemMenge by remember { mutableStateOf("1.0") }
    var newItemEinzelpreis by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(EmeraldGreen.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Positionen",
                            tint = EmeraldGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Einzelne Positionen / Artikel (${positionen.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = DarkNavy
                    )
                }

                IconButton(
                    onClick = { showAddItemDialog = true },
                    modifier = Modifier
                        .size(32.dp)
                        .background(EmeraldGreen.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Position hinzufügen",
                        tint = EmeraldGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (positionen.isEmpty()) {
                Text(
                    text = "Keine einzelnen Artikel erfasst. Tippe auf '+', um Posten manuell hinzuzufügen.",
                    fontSize = 11.sp,
                    color = SlateGray,
                    fontStyle = FontStyle.Italic
                )
            } else {
                positionen.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.bezeichnung.ifEmpty { "Position #${index + 1}" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = DarkNavy
                            )
                            Text(
                                text = "${item.menge}x @ ${NumberFormatter.format(item.einzelpreis)}",
                                fontSize = 10.sp,
                                color = SlateGray
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = NumberFormatter.format(item.gesamtpreis),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = EmeraldGreen
                            )
                            IconButton(
                                onClick = {
                                    val updated = positionen.toMutableList().apply { removeAt(index) }
                                    onPositionenChanged(updated)
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Löschen",
                                    tint = CrimsonRed,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddItemDialog) {
        AlertDialog(
            onDismissRequest = { showAddItemDialog = false },
            title = { Text("Neue Position hinzufügen", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newItemBezeichnung,
                        onValueChange = { newItemBezeichnung = it },
                        label = { Text("Artikelbezeichnung / Leistung") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newItemMenge,
                        onValueChange = { newItemMenge = it },
                        label = { Text("Menge (z. B. 1.0)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newItemEinzelpreis,
                        onValueChange = { newItemEinzelpreis = it },
                        label = { Text("Einzelpreis in EUR") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mengeVal = newItemMenge.toDoubleOrNull() ?: 1.0
                        val einzelpreisVal = newItemEinzelpreis.toDoubleOrNull() ?: 0.0
                        val gesamtpreisVal = mengeVal * einzelpreisVal
                        val newItem = com.example.data.ReceiptItem(
                            bezeichnung = newItemBezeichnung,
                            menge = mengeVal,
                            einzelpreis = einzelpreisVal,
                            gesamtpreis = gesamtpreisVal
                        )
                        onPositionenChanged(positionen + newItem)
                        newItemBezeichnung = ""
                        newItemMenge = "1.0"
                        newItemEinzelpreis = ""
                        showAddItemDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("Hinzufügen", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddItemDialog = false }) {
                    Text("Abbrechen", color = SlateGray)
                }
            }
        )
    }
}

@Composable
private fun ReceiptDeleteConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Beleg in den Papierkorb?", fontWeight = FontWeight.Bold) },
        text = { Text("Der Beleg wird aus der Belegliste entfernt und in den Papierkorb verschoben. Er kann später wiederhergestellt werden.") },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                modifier = Modifier.testTag("confirm_delete_button")) { Text("Löschen") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReceiptDetailDialog(receipt: Receipt, viewModel: ReceiptViewModel, onDismiss: () -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val previewRequester = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val actionScope = rememberCoroutineScope()
    if (confirmDelete) ReceiptDeleteConfirmationDialog(
        onDismiss = { confirmDelete = false },
        onConfirm = {
            confirmDelete = false
            viewModel.deleteReceipt(receipt.id)
        }
    )
    val bankReceiptLinks by viewModel.bankReceiptLinks.collectAsState()
    val bankTransactions by viewModel.bankTransactions.collectAsState()
    val linkedBankEntries = remember(receipt.id, receipt.internalId, bankReceiptLinks, bankTransactions) {
        val transactionById = bankTransactions.associateBy { it.transactionId }
        bankReceiptLinks
            .filter { link ->
                link.receiptId == receipt.id ||
                    (receipt.internalId.isNotBlank() && link.receiptInternalId == receipt.internalId)
            }
            .mapNotNull { link -> transactionById[link.transactionId]?.let { transaction -> link to transaction } }
            .sortedByDescending { (_, transaction) -> transaction.bookingDate }
    }

    // State for all editable fields
    var editAussteller by remember(receipt) { mutableStateOf(receipt.aussteller) }
    var editDatum by remember(receipt) { mutableStateOf(receipt.datum) }
    var editUhrzeit by remember(receipt) { mutableStateOf(receipt.uhrzeit) }
    var editBruttobetrag by remember(receipt) { mutableStateOf(receipt.bruttobetrag.toString()) }
    var editHauptkategorie by remember(receipt) { mutableStateOf(receipt.hauptkategorie) }
    var editUnterkategorie by remember(receipt) { mutableStateOf(receipt.unterkategorie) }
    var editKontoNr by remember(receipt) { mutableStateOf(receipt.kontoNr) }
    var editBeschreibung by remember(receipt) { mutableStateOf(receipt.beschreibung) }
    var editIsEigenleistung by remember(receipt) { mutableStateOf(receipt.isEigenleistungSanierung) }
    var editWohneinheit by remember(receipt) { mutableStateOf(if (receipt.wohneinheit.isEmpty()) "Gesamtobjekt / Allgemein" else receipt.wohneinheit) }
    var editMieter by remember(receipt) { mutableStateOf(receipt.mieter) }
    var editZahlungsart by remember(receipt) { mutableStateOf(receipt.zahlungsart) }
    var editPositionen by remember(receipt) { mutableStateOf(receipt.getPositionenList()) }

    var mainCategoryExpanded by remember { mutableStateOf(false) }
    var subCategoryExpanded by remember { mutableStateOf(false) }
    var wohneinheitExpanded by remember { mutableStateOf(false) }

    val propertyMetadataState by viewModel.propertyMetadata.collectAsState()
    val metadata = propertyMetadataState ?: PropertyMetadata()
    val unitsList = remember(metadata.wohneinheiten) {
        metadata.wohneinheiten.split(",").map { it.trim() }.filter { it.isNotEmpty() } + listOf("Gesamtobjekt / Allgemein")
    }

    val categoryColor = when (if (isEditing) editHauptkategorie else receipt.hauptkategorie) {
        "Anschaffungskosten" -> AccentBlue
        "Finanzierung, Kredite & Versicherungen" -> WarmOrange
        "Renovierungs- / Reparaturkosten & Investitionen" -> EmeraldGreen
        else -> CrimsonRed
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isEditing) Icons.Default.Edit else Icons.Default.Receipt,
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (isEditing) "Beleg bearbeiten" else "Belegdetails",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = DarkNavy
                    )
                }
                if (!isEditing) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = SlateGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        text = {
            val scrollState = rememberScrollState()
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = if (isEditing) 620.dp else 560.dp)
                    .verticalScroll(scrollState)
            ) {
                if (isEditing) {
                    // Edit Form
                    OutlinedTextField(
                        value = editAussteller,
                        onValueChange = { editAussteller = it },
                        label = { Text("Aussteller / Kreditor") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_receipt_aussteller")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editDatum,
                            onValueChange = { editDatum = it },
                            label = { Text("Datum (YYYY-MM-DD)") },
                            modifier = Modifier.weight(1.1f).testTag("edit_receipt_datum")
                        )
                        OutlinedTextField(
                            value = editUhrzeit,
                            onValueChange = { editUhrzeit = it },
                            label = { Text("Uhrzeit") },
                            modifier = Modifier.weight(0.9f).testTag("edit_receipt_uhrzeit")
                        )
                    }

                    OutlinedTextField(
                        value = editBruttobetrag,
                        onValueChange = { editBruttobetrag = it },
                        label = { Text("Bruttobetrag in EUR") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("edit_receipt_betrag")
                    )

                    ExposedDropdownMenuBox(
                        expanded = mainCategoryExpanded,
                        onExpandedChange = { mainCategoryExpanded = !mainCategoryExpanded }
                    ) {
                        OutlinedTextField(
                            value = editHauptkategorie,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Hauptkategorie") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mainCategoryExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth().testTag("edit_receipt_hauptkategorie")
                        )
                        ExposedDropdownMenu(
                            expanded = mainCategoryExpanded,
                            onDismissRequest = { mainCategoryExpanded = false }
                        ) {
                            ReceiptCategories.mainCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category) },
                                    onClick = {
                                        editHauptkategorie = category
                                        val firstSub = ReceiptCategories.subCategoriesMap[category]?.firstOrNull() ?: ""
                                        editUnterkategorie = firstSub
                                        editKontoNr = ReceiptCategories.getSuggestedKonto(category, firstSub)
                                        mainCategoryExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    val currentSubs = ReceiptCategories.subCategoriesMap[editHauptkategorie] ?: emptyList()
                    ExposedDropdownMenuBox(
                        expanded = subCategoryExpanded,
                        onExpandedChange = { subCategoryExpanded = !subCategoryExpanded }
                    ) {
                        OutlinedTextField(
                            value = editUnterkategorie,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unterkategorie") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subCategoryExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth().testTag("edit_receipt_unterkategorie")
                        )
                        ExposedDropdownMenu(
                            expanded = subCategoryExpanded,
                            onDismissRequest = { subCategoryExpanded = false }
                        ) {
                            currentSubs.forEach { sub ->
                                DropdownMenuItem(
                                    text = { Text(sub) },
                                    onClick = {
                                        editUnterkategorie = sub
                                        editKontoNr = ReceiptCategories.getSuggestedKonto(editHauptkategorie, sub)
                                        subCategoryExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editKontoNr,
                        onValueChange = { editKontoNr = it },
                        label = { Text("DATEV Konto-Nr.") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_receipt_konto")
                    )

                    OutlinedTextField(
                        value = editBeschreibung,
                        onValueChange = { editBeschreibung = it },
                        label = { Text("Beschreibung / Zweck") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_receipt_beschreibung")
                    )

                    OutlinedTextField(
                        value = editMieter,
                        onValueChange = { editMieter = it },
                        label = { Text("Mieter / Zahler (optional)") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_receipt_mieter_field")
                    )

                    OutlinedTextField(
                        value = editZahlungsart,
                        onValueChange = { editZahlungsart = it },
                        label = { Text("Zahlungsart") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_receipt_payment_method")
                    )

                    ExposedDropdownMenuBox(
                        expanded = wohneinheitExpanded,
                        onExpandedChange = { wohneinheitExpanded = !wohneinheitExpanded }
                    ) {
                        OutlinedTextField(
                            value = editWohneinheit,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Zugeordnete Wohneinheit") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = wohneinheitExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth().testTag("edit_receipt_wohneinheit_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = wohneinheitExpanded,
                            onDismissRequest = { wohneinheitExpanded = false }
                        ) {
                            unitsList.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(unit) },
                                    onClick = {
                                        editWohneinheit = unit
                                        wohneinheitExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Eigenleistung Sanierung?", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkNavy)
                            Text("Arbeitsleistung ohne Handwerkerbeleg", fontSize = 11.sp, color = SlateGray)
                        }
                        Switch(
                            checked = editIsEigenleistung,
                            onCheckedChange = { editIsEigenleistung = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = EmeraldGreen, checkedTrackColor = EmeraldGreen.copy(alpha = 0.5f))
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    ReceiptPositionenEditor(
                        positionen = editPositionen,
                        onPositionenChanged = { editPositionen = it }
                    )
                } else {
                    Ui2Section(receipt.aussteller.ifBlank { "Beleg" }) {
                        Text(receipt.getEffectiveDisplayId(), style = MaterialTheme.typography.bodyMedium)
                        Text(receipt.datum, style = MaterialTheme.typography.bodyMedium)
                        Text(NumberFormatter.format(receipt.bruttobetrag), style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold)
                    }
                    Ui2Section("Beleg Aktionen") {
                        Ui2ActionGrid(listOf(
                            Ui2Action("Beleg anzeigen", "Dokumentvorschau", Icons.Default.Receipt) {
                                actionScope.launch { previewRequester.bringIntoView() }
                            },
                            Ui2Action("Beleg bearbeiten", "Angaben bearbeiten", Icons.Default.Edit) { isEditing = true },
                            Ui2Action("Beleg teilen", "Zusammenfassung senden", Icons.Default.Share) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Beleg ${receipt.getEffectiveDisplayId()}")
                                    putExtra(Intent.EXTRA_TEXT, "${receipt.aussteller}\n${receipt.datum}\n${NumberFormatter.format(receipt.bruttobetrag)}\n${receipt.beschreibung}")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Beleg teilen"))
                            },
                            Ui2Action("Beleg löschen", "In den Papierkorb", Icons.Default.Delete, CrimsonRed) { confirmDelete = true }
                        ))
                    }
                    Box(Modifier.bringIntoViewRequester(previewRequester)) {
                        ReceiptPreviewSection(receipt = receipt, viewModel = viewModel)
                    }
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    // 2. Extrahiertes Metadaten Grid / Detail-Tabelle
                    Text(
                        text = "Extrahierte Belegdaten",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = DarkNavy
                    )
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SoftBackground, RoundedCornerShape(8.dp))
                            .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        DetailRow(label = "Aussteller", value = receipt.aussteller)
                        HorizontalDivider()
                        if (receipt.mieter.isNotEmpty()) {
                            DetailRow(label = "Mieter / Zahler", value = receipt.mieter)
                            HorizontalDivider()
                        }
                        DetailRow(label = "Zugeordnete Wohneinheit", value = if (receipt.wohneinheit.isNotEmpty()) receipt.wohneinheit else "Gesamtobjekt / Allgemein")
                        HorizontalDivider()
                        DetailRow(label = "Datum & Uhrzeit", value = "${receipt.datum} ${if (receipt.uhrzeit.isNotEmpty()) receipt.uhrzeit + " Uhr" else ""}")
                        HorizontalDivider()
                        DetailRow(label = "Bruttobetrag", value = NumberFormatter.format(receipt.bruttobetrag), highlight = true)
                        HorizontalDivider()
                        DetailRow(label = "Hauptkategorie", value = receipt.hauptkategorie)
                        HorizontalDivider()
                        DetailRow(label = "Unterkategorie", value = receipt.unterkategorie)
                        HorizontalDivider()
                        DetailRow(label = "Eigenleistung Sanierung?", value = if (receipt.isEigenleistungSanierung) "Ja (Sanierungsphase)" else "Nein")
                        if (receipt.beschreibung.isNotEmpty()) {
                            HorizontalDivider()
                            DetailRow(label = "Beschreibung / Zweck", value = receipt.beschreibung)
                        }
                    }

                    if (linkedBankEntries.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("receipt_bank_links_card"),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (linkedBankEntries.size == 1) "Mit 1 Bankbuchung verknüpft" else "Mit ${linkedBankEntries.size} Bankbuchungen verknüpft",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = DarkNavy
                                )
                                linkedBankEntries.forEach { (link, transaction) ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(SoftBackground, RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = transaction.counterparty.ifBlank { transaction.purpose.ifBlank { "Bankbuchung" } },
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.sp,
                                                    color = DarkNavy,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = transaction.bookingDate,
                                                    fontSize = 10.sp,
                                                    color = SlateGray
                                                )
                                            }
                                            Text(
                                                text = NumberFormatter.format(transaction.amount),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = DarkNavy
                                            )
                                        }
                                        TextButton(
                                            onClick = { viewModel.removeBankReceiptLink(link.linkId, transaction.transactionId) },
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text("Verknüpfung lösen", fontSize = 11.sp, color = CrimsonRed)
                                        }
                                    }
                                }
                                Text(
                                    "Der Beleg bleibt gespeichert, wenn nur eine einzelne Bank-Verknüpfung gelöst wird.",
                                    fontSize = 10.sp,
                                    color = SlateGray
                                )
                            }
                        }
                    }

                    // 2.2 Google Drive Synchronisationsstatus
                    var showDriveDetails by remember { mutableStateOf(false) }

                    Text(
                        text = "Google Drive Status",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = DarkNavy
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when (receipt.syncStatus) {
                                    "SYNCED" -> EmeraldGreen.copy(alpha = 0.08f)
                                    "ERROR" -> CrimsonRed.copy(alpha = 0.08f)
                                    else -> WarmOrange.copy(alpha = 0.08f)
                                }
                            )
                            .border(
                                1.dp,
                                when (receipt.syncStatus) {
                                    "SYNCED" -> EmeraldGreen.copy(alpha = 0.3f)
                                    "ERROR" -> CrimsonRed.copy(alpha = 0.3f)
                                    else -> WarmOrange.copy(alpha = 0.3f)
                                },
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    when (receipt.syncStatus) {
                                        "SYNCED" -> {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                                            Text("✓ Beleg und Daten gespeichert", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                                        }
                                        "ERROR" -> {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(18.dp))
                                            Text("✕ Synchronisationsfehler", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CrimsonRed)
                                        }
                                        else -> {
                                            Icon(Icons.Default.Info, contentDescription = null, tint = WarmOrange, modifier = Modifier.size(18.dp))
                                            Text("⚠ Metadaten noch nicht gespeichert", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WarmOrange)
                                        }
                                    }
                                }

                                if (receipt.syncStatus != "SYNCED") {
                                    Button(
                                        onClick = { viewModel.syncReceiptManually(receipt) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp).testTag("retry_sync_button_${receipt.id}"),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                                    ) {
                                        Text("Erneut versuchen", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }

                            if (receipt.syncStatus == "ERROR" && !receipt.syncError.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(receipt.syncError!!, fontSize = 11.sp, color = CrimsonRed)
                            }

                            if (receipt.syncStatus == "SYNCED" && !receipt.lastSyncedAt.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Letzter Sync: ${receipt.lastSyncedAt}", fontSize = 10.sp, color = SlateGray)
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(
                                onClick = { showDriveDetails = !showDriveDetails },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text(
                                    if (showDriveDetails) "Technische IDs verbergen" else "Technische IDs (Diagnose)",
                                    fontSize = 10.sp,
                                    color = AccentBlue
                                )
                            }

                            if (showDriveDetails) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White, RoundedCornerShape(4.dp))
                                        .border(1.dp, BorderColor, RoundedCornerShape(4.dp))
                                        .padding(8.dp)
                                ) {
                                    Text("Interne ID: ${receipt.internalId.ifEmpty { "-" }}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = DarkNavy)
                                    Text("Anzeige ID: ${receipt.displayId ?: "-"}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = DarkNavy)
                                    Text("Drive Datei ID: ${receipt.driveFileId ?: "-"}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = DarkNavy)
                                    Text("Drive Metadaten ID: ${receipt.driveMetadataFileId ?: "-"}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = DarkNavy)
                                }
                            }
                        }
                    }

                    // 2.5 Einzelne Posten & DATEV-Exportvorschau Pipeline
                    val positionenList = receipt.getPositionenList()
                    val activeProfileState by viewModel.activeDatevProfile.collectAsState()
                    val allocations = remember(receipt, activeProfileState) {
                        com.example.util.DatevMappingService.buildAllocationsFromReceipt(receipt, activeProfileState)
                    }
                    val datevRows = remember(receipt, activeProfileState) {
                        com.example.util.DatevMappingService.buildDatevBookingRows(receipt, activeProfileState)
                    }

                    Text(
                        text = "DATEV Export-Vorschau & Transformation",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = DarkNavy
                    )

                    // Pipeline Summary Badge Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                            .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("${if (positionenList.isEmpty()) 1 else positionenList.size}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkNavy)
                            Text("OCR-Positionen", fontSize = 10.sp, lineHeight = 12.sp, color = SlateGray, textAlign = TextAlign.Center, maxLines = 2)
                        }
                        Text("→", color = SlateGray, fontWeight = FontWeight.Bold, modifier = Modifier.width(12.dp), textAlign = TextAlign.Center)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("${allocations.size}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkNavy)
                            Text("Aufteilungen", fontSize = 10.sp, lineHeight = 12.sp, color = SlateGray, textAlign = TextAlign.Center, maxLines = 2)
                        }
                        Text("→", color = SlateGray, fontWeight = FontWeight.Bold, modifier = Modifier.width(12.dp), textAlign = TextAlign.Center)
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("${datevRows.size}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = EmeraldGreen)
                            Text("DATEV-Zeilen", fontSize = 10.sp, lineHeight = 12.sp, color = EmeraldGreen, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }

                    if (positionenList.isNotEmpty()) {
                        Text(
                            text = "Erkannte Positionen (${positionenList.size})",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = SlateGray
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SoftBackground, RoundedCornerShape(8.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            positionenList.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.bezeichnung.ifEmpty { "Position #${index + 1}" },
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = DarkNavy
                                        )
                                        Text(
                                            text = "${item.menge}x @ ${NumberFormatter.format(item.einzelpreis)}",
                                            fontSize = 10.sp,
                                            color = SlateGray
                                        )
                                    }
                                    Text(
                                        text = NumberFormatter.format(item.gesamtpreis),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = DarkNavy
                                    )
                                }
                                if (index < positionenList.size - 1) {
                                    HorizontalDivider(color = Color(0xFFE2E8F0), modifier = Modifier.padding(vertical = 2.dp))
                                }
                            }
                        }
                    }

                    // 3. Voraussichtliche DATEV-Buchungszeilen
                    Text(
                        text = "Voraussichtliche DATEV-Buchungen (${datevRows.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = DarkNavy
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        datevRows.forEachIndexed { idx, row ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(categoryColor.copy(alpha = 0.08f))
                                    .border(1.dp, categoryColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Zeile ${idx + 1}: ${row.beschreibung}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = DarkNavy,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = NumberFormatter.format(row.bruttobetrag),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            color = categoryColor
                                        )
                                    }
                                    HorizontalDivider(color = categoryColor.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 2.dp))
                                    Column(
                              modifier = Modifier.fillMaxWidth(),
                              verticalArrangement = Arrangement.spacedBy(2.dp)
                          ) {
                              Text(
                                  "Kostenart: ${row.hauptkategorie}",
                                  fontSize = 10.sp,
                                  lineHeight = 13.sp,
                                  color = SlateGray,
                                  maxLines = 2,
                                  overflow = TextOverflow.Ellipsis
                              )
                              Text(
                                  "Kostenstelle: ${row.wohneinheitId.ifBlank { "Gesamt" }}",
                                  fontSize = 10.sp,
                                  lineHeight = 13.sp,
                                  color = SlateGray,
                                  maxLines = 2,
                                  overflow = TextOverflow.Ellipsis
                              )
                          }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (receipt.freigabestatus == "FREIGEGEBEN") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Diese DATEV-Aufteilung wurde ausdrücklich freigegeben.",
                                modifier = Modifier.padding(12.dp),
                                color = Color(0xFF166534),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                com.example.util.DatevMappingService
                                    .confirmDatevPreview(receipt, datevRows)
                                    ?.let { confirmedReceipt ->
                                        viewModel.updateReceipt(confirmedReceipt)
                                        onDismiss()
                                    }
                            },
                            enabled = datevRows.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("DATEV-Aufteilung ausdrücklich freigeben", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Erst nach dieser Bestätigung wird der Beleg in einen DATEV-Export aufgenommen.",
                            fontSize = 10.sp,
                            color = SlateGray
                        )
                    }

                }
            }
        },
        confirmButton = {
            if (isEditing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { isEditing = false },
                        colors = ButtonDefaults.buttonColors(containerColor = SlateGray),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Abbrechen", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val parsedBetrag = editBruttobetrag.toDoubleOrNull() ?: 0.0
                            val updatedReceipt = receipt.copy(
                                aussteller = editAussteller,
                                datum = editDatum,
                                uhrzeit = editUhrzeit,
                                bruttobetrag = parsedBetrag,
                                hauptkategorie = editHauptkategorie,
                                unterkategorie = editUnterkategorie,
                                kontoNr = editKontoNr,
                                beschreibung = editBeschreibung,
                                isEigenleistungSanierung = editIsEigenleistung,
                                wohneinheit = editWohneinheit,
                                mieter = editMieter,
                                zahlungsart = editZahlungsart,
                                zahlungsartQuelle = if (editZahlungsart.trim().equals(receipt.zahlungsart.trim(), ignoreCase = true)) receipt.zahlungsartQuelle else if (editZahlungsart.trim().equals("Unbekannt", ignoreCase = true) || editZahlungsart.isBlank()) "UNBEKANNT" else "MANUELL",
                                zahlungsartConfidence = if (editZahlungsart.trim().equals("Unbekannt", ignoreCase = true) || editZahlungsart.isBlank()) 0.0 else if (editZahlungsart.trim().equals(receipt.zahlungsart.trim(), ignoreCase = true)) receipt.zahlungsartConfidence else 1.0,
                                positionenJson = com.example.data.ReceiptItemConverter.toJson(editPositionen)
                            )
                            viewModel.updateReceipt(updatedReceipt)
                            isEditing = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("save_edited_receipt_button")
                    ) {
                        Text("Speichern", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { isEditing = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        modifier = Modifier.weight(1f).height(48.dp).testTag("edit_receipt_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bearbeiten", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    B…82747 tokens truncated…                 },
                                    enabled = !isGeneratingUtility,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isGeneratingUtility) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Abrechnung wird erstellt...", fontSize = 12.sp)
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Betriebskosten-Abrechnung generieren", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                utilityStatement?.let { statement ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                        border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.4f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("Ergebnis Nebenkostenabrechnung (${statement.period})", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = DarkNavy)
                                            Text("Mieteranteil: ${statement.tenantShareAmount} € • Vorauszahlungen: ${statement.prepaymentsAmount} €", fontSize = 11.sp, color = SlateGray)
                                            
                                            Surface(
                                                color = if (statement.balanceAmount >= 0) EmeraldGreen.copy(alpha = 0.15f) else CrimsonRed.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = if (statement.balanceAmount >= 0) "Nachzahlung Mieter: +${statement.balanceAmount} €" else "Guthaben Mieter: ${statement.balanceAmount} €",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.5.sp,
                                                    color = if (statement.balanceAmount >= 0) EmeraldGreen else CrimsonRed
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Kostenaufschlüsselung:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            statement.costBreakdown.forEach { item ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("${item.category} (${item.costKey})", fontSize = 10.5.sp, color = SlateGray, modifier = Modifier.weight(1f))
                                                    Text("${item.tenantShare} €", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text("Anschreiben-Entwurf:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            Surface(
                                                color = Color.White,
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                            ) {
                                                Text(
                                                    text = statement.formalDraftText,
                                                    fontSize = 10.sp,
                                                    color = DarkNavy,
                                                    modifier = Modifier.padding(8.dp),
                                                    lineHeight = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // --- TAB 3: MIETPREIS & RENDITE ---
                        3 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = locationText,
                                    onValueChange = { locationText = it },
                                    label = { Text("Standort / Ort") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Button(
                                    onClick = { viewModel.runRentYieldOptimization(propertyLocation = locationText) },
                                    enabled = !isOptimizingRent,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isOptimizingRent) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Rendite wird berechnet...", fontSize = 12.sp)
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Mietpreis- & Renditepotenzial analysieren", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                rentReport?.let { report ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                        border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Text("Aktuelle Kaltmiete: ${report.currentTotalRentNet} €", fontSize = 11.sp, color = SlateGray)
                                                    Text("Soll-Miete: ${report.recommendedTotalRentNet} €", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EmeraldGreen)
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("Brutto-Rendite: ${report.currentGrossYieldPct}%", fontSize = 11.sp, color = SlateGray)
                                                    Text("Potenzial: ${report.potentialGrossYieldPct}%", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = AccentBlue)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Empfehlungen pro Wohneinheit:", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                            report.unitOptimizations.forEach { opt ->
                                                Surface(
                                                    color = Color.White,
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(8.dp)) {
                                                        Text("${opt.unitName}: ${opt.currentRent} € ➔ ${opt.suggestedRent} €", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = DarkNavy)
                                                        Text("Begründung: ${opt.reasoning}", fontSize = 10.5.sp, color = SlateGray)
                                                        Text("Rechtsgrundlage: ${opt.legalBasis}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
                                                    }
                                                }
                                            }

                                            Text(report.summaryText, fontSize = 10.5.sp, color = DarkNavy)
                                        }
                                    }
                                }
                            }
                        }

                        // --- TAB 4: MÄNGEL- & SCHADENS-FOTO ---
                        4 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Fotografiere oder beschreibe einen Schaden in der Mietwohnung:", fontSize = 11.sp, color = SlateGray)

                                OutlinedTextField(
                                    value = damageDescription,
                                    onValueChange = { damageDescription = it },
                                    label = { Text("Schadensbeschreibung") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2
                                )

                                Button(
                                    onClick = {
                                        val sampleBitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
                                        val canvas = android.graphics.Canvas(sampleBitmap)
                                        canvas.drawColor(android.graphics.Color.LTGRAY)
                                        val paint = android.graphics.Paint().apply {
                                            color = android.graphics.Color.DKGRAY
                                            textSize = 30f
                                        }
                                        canvas.drawText("Wasserschaden-Muster", 20f, 150f, paint)

                                        viewModel.analyzeDamagePhoto(sampleBitmap, damageDescription)
                                    },
                                    enabled = !isAssessingDamage,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isAssessingDamage) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Gemini analysiert Schaden...", fontSize = 12.sp)
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Foto-Schadensanalyse starten", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                damageAssessment?.let { dmg ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                        border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.4f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(dmg.damageTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkNavy)
                                            Text("Schweregrad: ${dmg.severity} • Dringlichkeit: ${dmg.urgency}", fontSize = 11.sp, color = CrimsonRed, fontWeight = FontWeight.Bold)
                                            Text("Geschätzte Kosten: ${dmg.estimatedRepairCostRange}", fontSize = 11.sp, color = SlateGray)
                                            Text("Steuerliche Kategorie: ${dmg.taxCategory}", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
                                            Text("Empfohlene Aktion: ${dmg.recommendedAction}", fontSize = 10.5.sp, color = DarkNavy)

                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Handwerker-Anfrage Schreiben:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            Surface(
                                                color = Color.White,
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                            ) {
                                                Text(
                                                    text = dmg.craftsmanDraftLetter,
                                                    fontSize = 10.sp,
                                                    color = DarkNavy,
                                                    modifier = Modifier.padding(8.dp),
                                                    lineHeight = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // --- TAB 5: VERTRAGS- & FRISTEN-ANALYSATOR ---
                        5 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = contractText,
                                    onValueChange = { contractText = it },
                                    label = { Text("Vertragstext / Auszug") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3
                                )

                                Button(
                                    onClick = { viewModel.analyzeContractDocument(textContent = contractText) },
                                    enabled = !isAnalyzingContract,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkNavy),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isAnalyzingContract) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Vertrag wird geprüft...", fontSize = 12.sp)
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Vertrag & Fristen durchleuchten", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                contractAnalysis?.let { contract ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                        border = BorderStroke(1.dp, DarkNavy.copy(alpha = 0.3f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("${contract.contractType} (${contract.parties})", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = DarkNavy)
                                            Text("Beginn: ${contract.startDate} • Kündigungsfrist: ${contract.noticePeriod}", fontSize = 11.sp, color = SlateGray)
                                            Text("Mietanpassung: ${contract.rentAdjustmentClause}", fontSize = 11.sp, color = DarkNavy)
                                            Text("Kaution: ${contract.depositTerms}", fontSize = 11.sp, color = SlateGray)

                                            if (contract.riskFlags.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("⚠️ Rechtliche Risiken / Fallstricke:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CrimsonRed)
                                                contract.riskFlags.forEach { flag ->
                                                    Text("• $flag", fontSize = 10.5.sp, color = CrimsonRed)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("Zusammenfassung: ${contract.summary}", fontSize = 10.5.sp, color = DarkNavy)
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
                colors = ButtonDefaults.buttonColors(containerColor = DarkNavy),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Schließen", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun KiLearnedRulesDialog(
    viewModel: ReceiptViewModel,
    onDismiss: () -> Unit
) {
    val rules by viewModel.learnedRules.collectAsState()
    var showAddRuleForm by remember { mutableStateOf(false) }

    var newVendor by remember { mutableStateOf("") }
    var newHauptkategorie by remember { mutableStateOf("Renovierungs- / Reparaturkosten & Investitionen") }
    var newUnterkategorie by remember { mutableStateOf("Streichen, Tapezieren") }
    var newKontoNr by remember { mutableStateOf("4830") }
    var newWohneinheit by remember { mutableStateOf("Gesamtobjekt / Allgemein") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(24.dp))
                Text("Gelerntes KI-Wissen & Händler-Regeln", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = DarkNavy)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = EmeraldGreen.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Die KI lernt automatisch aus deinen gespeicherten Belegen und Korrekturen. Neue Scans werden anhand deiner gelernten Händler-Regeln bevorzugt eingeordnet.",
                            fontSize = 11.sp,
                            color = DarkNavy,
                            lineHeight = 15.sp
                        )
                    }
                }

                if (showAddRuleForm) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Manuelle Händler-Regel hinzufügen", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = DarkNavy)

                            OutlinedTextField(
                                value = newVendor,
                                onValueChange = { newVendor = it },
                                label = { Text("Händler / Markt Name", fontSize = 11.sp) },
                                placeholder = { Text("z.B. Bauhaus, Hornbach, OBI") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = newHauptkategorie,
                                onValueChange = { newHauptkategorie = it },
                                label = { Text("Hauptkategorie", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = newUnterkategorie,
                                    onValueChange = { newUnterkategorie = it },
                                    label = { Text("Unterkategorie", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = newKontoNr,
                                    onValueChange = { newKontoNr = it },
                                    label = { Text("Konto-Nr", fontSize = 11.sp) },
                                    modifier = Modifier.width(90.dp),
                                    singleLine = true
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showAddRuleForm = false }) {
                                    Text("Abbrechen", fontSize = 11.sp)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Button(
                                    onClick = {
                                        if (newVendor.isNotBlank()) {
                                            viewModel.learnVendorRule(
                                                aussteller = newVendor,
                                                hauptkategorie = newHauptkategorie,
                                                unterkategorie = newUnterkategorie,
                                                kontoNr = newKontoNr,
                                                wohneinheit = newWohneinheit
                                            )
                                            newVendor = ""
                                            showAddRuleForm = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Regel Speichern", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showAddRuleForm = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, EmeraldGreen)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                            Text("Händler-Regel manuell hinzufügen", fontSize = 11.5.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (rules.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Noch keine expliziten Händler-Regeln gespeichert.\nSpeichere oder korrigiere Belege, damit die KI lernt!",
                            fontSize = 12.sp,
                            color = SlateGray,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text("Gelernte Händler-Zuordnungen (${rules.size}):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkNavy)

                    rules.forEach { rule ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(rule.aussteller, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkNavy)
                                        Surface(
                                            color = EmeraldGreen.copy(alpha = 0.15f),
                                            shape = CircleShape
                                        ) {
                                            Text(
                                                text = "${rule.count}x gelernt",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = EmeraldGreen
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${rule.hauptkategorie} • ${rule.unterkategorie} (Konto ${rule.kontoNr})",
                                        fontSize = 11.sp,
                                        color = SlateGray
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.deleteLearnedRule(rule.aussteller) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Löschen", tint = CrimsonRed, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { viewModel.clearAllLearnedRules() }
                        ) {
                            Text("Alle Regeln löschen", fontSize = 11.sp, color = CrimsonRed)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = DarkNavy),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Fertig", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun AccountSettingsDialog(
    viewModel: ReceiptViewModel,
    onDismiss: () -> Unit
) {
    var showTenantDialog by remember { mutableStateOf(false) }
    var showPropertyMetadataDialog by remember { mutableStateOf(false) }
    var showKiLearnedRulesDialog by remember { mutableStateOf(false) }
    var showAiProviderSettingsDialog by remember { mutableStateOf(false) }
    var showDocumentStatusOverviewDialog by remember { mutableStateOf(false) }
    var showRecycleBinDialog by remember { mutableStateOf(false) }
    var showLocalDataResetDialog by remember { mutableStateOf(false) }
    var showDriveSettingsDialog by remember { mutableStateOf(false) }

    val currentMetadata by viewModel.propertyMetadata.collectAsState()
    val metadata = currentMetadata ?: PropertyMetadata()
    val learnedRulesCount by viewModel.learnedRulesCount.collectAsState()

    var editWohnort by remember(metadata) { mutableStateOf(metadata.wohnort) }
    var editAdresse by remember(metadata) { mutableStateOf(metadata.adresse) }

    if (showTenantDialog) {
        TenantManagementDialog(viewModel = viewModel, onDismiss = { showTenantDialog = false })
    }

    if (showPropertyMetadataDialog) {
        PropertyMetadataFormDialog(viewModel = viewModel, onDismiss = { showPropertyMetadataDialog = false })
    }

    if (showKiLearnedRulesDialog) {
        KiLearnedRulesDialog(viewModel = viewModel, onDismiss = { showKiLearnedRulesDialog = false })
    }

    if (showAiProviderSettingsDialog) {
        AiProviderSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showAiProviderSettingsDialog = false }
        )
    }

    if (showDocumentStatusOverviewDialog) {
        DocumentStatusOverviewDialog(viewModel = viewModel, onDismiss = { showDocumentStatusOverviewDialog = false })
    }

    if (showRecycleBinDialog) {
        RecycleBinDialog(viewModel = viewModel, onDismiss = { showRecycleBinDialog = false })
    }

    if (showLocalDataResetDialog) {
        ResetLocalDataDialog(
            onConfirm = {
                viewModel.resetLocalReceiptData()
                showLocalDataResetDialog = false
            },
            onDismiss = { showLocalDataResetDialog = false }
        )
    }

    if (showDriveSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showDriveSettingsDialog = false },
            title = { Text("Google Drive & Sicherung", fontWeight = FontWeight.Bold, color = DarkNavy) },
                        text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                ) {
                    GoogleDriveSyncCard(viewModel)
                }
            },
            confirmButton = { TextButton(onClick = { showDriveSettingsDialog = false }) { Text("Fertig") } }
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
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(24.dp)
                )
                Text("Einstellungen", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = DarkNavy)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Verwalte KI, Belege, Objekt- und persönliche Einstellungen an einem Ort.",
                    fontSize = 12.sp,
                    color = SlateGray,
                    lineHeight = 16.sp
                )

                Text("KI & Automatisierung", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = SlateGray)

                val aiProviderState by viewModel.aiProviderState.collectAsState()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAiProviderSettingsDialog = true }
                        .testTag("ai_provider_settings_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(20.dp))
                            Column {
                                Text("KI-Anbieter für Beleganalyse", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = DarkNavy)
                                Text(
                                    if (aiProviderState.provider == ReceiptAnalysisProvider.OPENAI) {
                                        "OpenAI • ${aiProviderState.openAiModel} • Schlüssel ${if (aiProviderState.hasOpenAiKey) "gespeichert" else "fehlt"}"
                                    } else {
                                        "Gemini • bisherige Konfiguration"
                                    },
                                    fontSize = 10.5.sp,
                                    color = SlateGray
                                )
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(18.dp))
                    }
                }

                // KI Adaptive Memory Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showKiLearnedRulesDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Gelerntes KI-Wissen ($learnedRulesCount Regeln)", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = DarkNavy)
                                Text("Automatisch gelernte Händler-Zuordnungen verwalten", fontSize = 10.5.sp, color = SlateGray)
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                    }
                }

                Text("Daten & Sicherung", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = SlateGray)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDriveSettingsDialog = true }
                        .testTag("drive_settings_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Cloud, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Google Drive & Sicherung", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = DarkNavy)
                                Text("Verbindung, automatische Sicherung und Wiederherstellung", fontSize = 10.5.sp, color = SlateGray)
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                    }
                }

                Text("Belege & Speicher", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = SlateGray)

                // Document Status Overview Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDocumentStatusOverviewDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Dokumentenstatus & Reparatur", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = DarkNavy)
                                Text("Prüfe Originaldokumente aller Belege und ersetze fehlende Dateien", fontSize = 10.5.sp, color = SlateGray)
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                    }
                }

                // Papierkorb Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showRecycleBinDialog = true }
                        .testTag("recycle_bin_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Papierkorb", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = DarkNavy)
                                Text("Gelöschte Belege ansehen, wiederherstellen oder endgültig löschen", fontSize = 10.5.sp, color = SlateGray)
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(18.dp))
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLocalDataResetDialog = true }
                        .testTag("reset_local_receipt_data_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7F7)),
                    border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(20.dp))
                            Column {
                                Text("Lokale Belegdaten zurücksetzen", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = DarkNavy)
                                Text("Für einen Neustart mit leeren Belegen – APIs und Drive bleiben erhalten", fontSize = 10.5.sp, color = SlateGray)
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(18.dp))
                    }
                }

                Text("Adressen", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateGray)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Fahrtenbuch Adressen",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkNavy
                            )
                        }

                        OutlinedTextField(
                            value = editWohnort,
                            onValueChange = { editWohnort = it },
                            label = { Text("Meine Adresse / Wohnort (Startadresse)", fontSize = 11.sp) },
                            placeholder = { Text("z.B. Hauptstraße 1, 12345 Wohnstadt") },
                            modifier = Modifier.fillMaxWidth().testTag("menu_user_address_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = editAdresse,
                            onValueChange = { editAdresse = it },
                            label = { Text("Immobilien-Adresse (Zielobjekt)", fontSize = 11.sp) },
                            placeholder = { Text("z.B. Musterstraße 42, 12345 Musterstadt") },
                            modifier = Modifier.fillMaxWidth().testTag("menu_property_address_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                val updated = metadata.copy(wohnort = editWohnort, adresse = editAdresse)
                                viewModel.updatePropertyMetadata(updated)
                            },
                            modifier = Modifier.align(Alignment.End).testTag("save_menu_addresses_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Adressen speichern", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Section 2: Other settings
                Text("Objekt & Personen", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = SlateGray)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showTenantDialog = true },
                        modifier = Modifier.weight(1f).testTag("manage_tenants_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Mieter verwalten", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { showPropertyMetadataDialog = true },
                        modifier = Modifier.weight(1f).testTag("manage_property_metadata_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Objekt-Stammdaten", fontSize = 11.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Aktuelles Steuerjahr:", fontSize = 12.sp, color = SlateGray)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkNavy.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "2026",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkNavy
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = metadata.copy(wohnort = editWohnort, adresse = editAdresse)
                    viewModel.updatePropertyMetadata(updated)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = DarkNavy)
            ) {
                Text("Fertig")
            }
        }
    )
}

@Composable
fun ResetLocalDataDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = CrimsonRed) },
        title = { Text("Lokale Belegdaten zurücksetzen", fontWeight = FontWeight.Bold, color = DarkNavy) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Alle lokal gespeicherten Belege, Dokumentverknüpfungen, Export-Historien, gelernten KI-Zuordnungen sowie der Bankabgleich werden entfernt.",
                    color = SlateGray,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Text(
                    "Erhalten bleiben: Objekt- und Mieterdaten, Drive-Verbindung und alle API-Schlüssel. Dateien in Google Drive werden nicht gelöscht.",
                    color = EmeraldGreen,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
            ) { Text("Jetzt zurücksetzen") }
        }
    )
}

@Composable
fun AiProviderSettingsDialog(
    viewModel: ReceiptViewModel,
    onDismiss: () -> Unit
) {
    val savedState by viewModel.aiProviderState.collectAsState()
    var selectedProvider by remember(savedState.provider) { mutableStateOf(savedState.provider) }
    var model by remember(savedState.openAiModel) { mutableStateOf(savedState.openAiModel) }
    var apiKeyInput by remember { mutableStateOf("") }
    var geminiApiKeyInput by remember { mutableStateOf("") }
    var googleRoutesApiKeyInput by remember { mutableStateOf("") }
    var privateDeviceConfirmed by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(22.dp))
                Text("KI- und Routing-Anbieter", fontWeight = FontWeight.Bold, color = DarkNavy)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Wähle, welcher Dienst ausschließlich neue Belege analysiert. DATEV-Freigaben bleiben immer manuell.",
                    fontSize = 12.sp,
                    color = SlateGray
                )

                listOf(
                    ReceiptAnalysisProvider.GEMINI to "Gemini",
                    ReceiptAnalysisProvider.OPENAI to "OpenAI"
                ).forEach { (provider, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedProvider = provider }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedProvider == provider,
                            onClick = { selectedProvider = provider }
                        )
                        Column {
                            Text(label, fontWeight = FontWeight.Bold, color = DarkNavy)
                            Text(
                                if (provider == ReceiptAnalysisProvider.OPENAI) {
                                    "Responses API mit Bildanalyse und strengem JSON-Schema"
                                } else {
                                    "Vorhandene Gemini-Anbindung"
                                },
                                fontSize = 10.5.sp,
                                color = SlateGray
                            )
                        }
                    }
                }

                if (selectedProvider == ReceiptAnalysisProvider.GEMINI) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Privater Gerätemodus", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF9A3412))
                            Text(
                                "Der Gemini-Schlüssel wird lokal mit Android Keystore verschlüsselt und nicht synchronisiert.",
                                fontSize = 10.5.sp,
                                color = Color(0xFF9A3412)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = geminiApiKeyInput,
                        onValueChange = { geminiApiKeyInput = it.trim() },
                        label = {
                            Text(if (savedState.hasGeminiKey) "Neuer Gemini-API-Schlüssel (leer = vorhandenen behalten)" else "Gemini-API-Schlüssel")
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("gemini_api_key_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { privateDeviceConfirmed = !privateDeviceConfirmed },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = privateDeviceConfirmed,
                            onCheckedChange = { privateDeviceConfirmed = it }
                        )
                        Text(
                            "Ich verwende diese APK nur privat und veröffentliche den Schlüssel nicht.",
                            fontSize = 11.sp,
                            color = DarkNavy
                        )
                    }

                    if (savedState.hasGeminiKey) {
                        OutlinedButton(
                            onClick = {
                                geminiApiKeyInput = ""
                                viewModel.deleteGeminiKey()
                            },
                            modifier = Modifier.testTag("delete_gemini_api_key_button")
                        ) {
                            Text("Gespeicherten Gemini-Schlüssel löschen", fontSize = 11.sp)
                        }
                    }
                }

                if (selectedProvider == ReceiptAnalysisProvider.OPENAI) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Privater Gerätemodus", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF9A3412))
                            Text(
                                "Ein direkt in einer App verwendeter API-Schlüssel ist nicht für eine öffentliche APK geeignet. Der Schlüssel wird lokal mit Android Keystore verschlüsselt und nicht synchronisiert.",
                                fontSize = 10.5.sp,
                                color = Color(0xFF9A3412)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it.trim() },
                        label = {
                            Text(
                                if (savedState.hasOpenAiKey) "Neuer API-Schlüssel (leer = vorhandenen behalten)" else "OpenAI-API-Schlüssel"
                            )
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("openai_api_key_input")
                    )

                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it.trim() },
                        label = { Text("OpenAI-Modell") },
                        supportingText = { Text("Empfohlen für den ersten Qualitätsvergleich: gpt-5.6") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("openai_model_input")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { privateDeviceConfirmed = !privateDeviceConfirmed },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = privateDeviceConfirmed,
                            onCheckedChange = { privateDeviceConfirmed = it }
                        )
                        Text(
                            "Ich verwende diese APK nur privat und veröffentliche den Schlüssel nicht.",
                            fontSize = 11.sp,
                            color = DarkNavy
                        )
                    }

                    if (savedState.hasOpenAiKey) {
                        OutlinedButton(
                            onClick = {
                                apiKeyInput = ""
                                viewModel.deleteOpenAiKey()
                                selectedProvider = ReceiptAnalysisProvider.GEMINI
                            },
                            modifier = Modifier.testTag("delete_openai_api_key_button")
                        ) {
                            Text("Gespeicherten OpenAI-Schlüssel löschen", fontSize = 11.sp)
                        }
                    }
                }

                HorizontalDivider(color = BorderColor)
                Text("Google Routes – echte Straßenkilometer", fontWeight = FontWeight.Bold, color = DarkNavy)
                Text(
                    "Unabhängig von der KI. Der Schlüssel wird mit Android Keystore verschlüsselt, nur maskiert behandelt und nicht gesichert oder exportiert.",
                    fontSize = 10.5.sp, color = SlateGray
                )
                OutlinedTextField(
                    value = googleRoutesApiKeyInput,
                    onValueChange = { googleRoutesApiKeyInput = it.trim() },
                    label = {
                        Text(if (savedState.hasGoogleRoutesKey) "Neuer Google-Routes-Schlüssel (leer = behalten)" else "Google-Routes-API-Schlüssel")
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("google_routes_api_key_input")
                )
                if (savedState.hasGoogleRoutesKey) {
                    Text("Google-Routes-Schlüssel: •••••••• (gespeichert)", fontSize = 10.sp, color = EmeraldGreen)
                    OutlinedButton(
                        onClick = {
                            googleRoutesApiKeyInput = ""
                            viewModel.deleteGoogleRoutesKey()
                        },
                        modifier = Modifier.testTag("delete_google_routes_api_key_button")
                    ) { Text("Google-Routes-Schlüssel löschen", fontSize = 11.sp) }
                }

                errorMessage?.let {
                    Text(it, color = CrimsonRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val error = viewModel.saveAiProviderSettings(
                        provider = selectedProvider,
                        model = model,
                        newOpenAiKey = apiKeyInput,
                        newGeminiKey = geminiApiKeyInput,
                        newGoogleRoutesKey = googleRoutesApiKeyInput
                    )
                    if (error == null) {
                        apiKeyInput = ""
                        geminiApiKeyInput = ""
                        googleRoutesApiKeyInput = ""
                        onDismiss()
                    } else {
                        errorMessage = error
                    }
                },
                enabled = privateDeviceConfirmed && (
                    googleRoutesApiKeyInput.isNotBlank() || when (selectedProvider) {
                        ReceiptAnalysisProvider.OPENAI -> savedState.hasOpenAiKey || apiKeyInput.isNotBlank()
                        ReceiptAnalysisProvider.GEMINI -> savedState.hasGeminiKey || geminiApiKeyInput.isNotBlank()
                    }
                ),
                modifier = Modifier.testTag("save_ai_provider_settings_button")
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    apiKeyInput = ""
                    geminiApiKeyInput = ""
                    googleRoutesApiKeyInput = ""
                    onDismiss()
                }
            ) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
fun TenantManagementDialog(
    viewModel: ReceiptViewModel,
    onDismiss: () -> Unit
) {
    val units by viewModel.wohneinheitenStatus.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mieterverwaltung") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(units) { unit ->
                    TenantItem(unit = unit, onUpdate = { updatedUnit -> viewModel.updateWohneinheit(updatedUnit) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Schließen")
            }
        }
    )
}

@Composable
fun TenantItem(
    unit: WohneinheitStatus,
    onUpdate: (WohneinheitStatus) -> Unit
) {
    var mieter by remember { mutableStateOf(unit.mieter) }
    var start by remember { mutableStateOf(unit.mietvertragsstart) }
    var kaltmiete by remember { mutableStateOf(unit.kaltmiete.toString()) }
    var isEditing by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = unit.label, style = MaterialTheme.typography.titleMedium)
            if (isEditing) {
                OutlinedTextField(value = mieter, onValueChange = { mieter = it }, label = { Text("Mieter") })
                OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Mietvertragsstart") })
                OutlinedTextField(value = kaltmiete, onValueChange = { kaltmiete = it }, label = { Text("Kaltmiete") })
                Button(onClick = {
                    onUpdate(unit.copy(mieter = mieter, mietvertragsstart = start, kaltmiete = kaltmiete.toDoubleOrNull() ?: 0.0))
                    isEditing = false
                }) {
                    Text("Speichern")
                }
            } else {
                Text("Mieter: $mieter")
                Text("Mietvertragsstart: $start")
                Text("Kaltmiete: $kaltmiete")
                TextButton(onClick = { isEditing = true }) {
                    Text("Bearbeiten")
                }
            }
        }
    }
}

@Composable
fun DatevExportDialog(
    viewModel: ReceiptViewModel,
    receipts: List<Receipt>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val exportScope = rememberCoroutineScope()

    val step by viewModel.wizardStep.collectAsState()
    val activeProfile by viewModel.activeDatevProfile.collectAsState()
    val mappedRecords by viewModel.wizardMappedRecords.collectAsState()
    val excludedReceipts by viewModel.wizardExcludedReceipts.collectAsState()
    val exclusionReasons by viewModel.wizardExclusionReasons.collectAsState()
    val validationReport by viewModel.wizardValidationReport.collectAsState()
    val lastResult by viewModel.lastExportResult.collectAsState()
    val auditRuns by viewModel.allAuditRuns.collectAsState()

    val unitFilter by viewModel.wizardUnitFilter.collectAsState()
    val yearFilter by viewModel.wizardYearFilter.collectAsState()
    val typeFilter by viewModel.wizardCategoryTypeFilter.collectAsState()
    val targetFormat by viewModel.wizardTargetFormat.collectAsState()

    var editableBeraterNr by remember(activeProfile) { mutableStateOf(activeProfile.beraterNummer) }
    var editableMandantenNr by remember(activeProfile) { mutableStateOf(activeProfile.mandantenNummer) }
    var editableMandantenName by remember(activeProfile) { mutableStateOf(activeProfile.mandantenName) }
    var editableSachkontenLaenge by remember(activeProfile) { mutableStateOf(activeProfile.sachkontenLaenge.toString()) }

    LaunchedEffect(Unit) {
        viewModel.recalculateWizardStepData()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header & Step Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DATEV Export-Assistent (Redesign v2.5)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Schritt $step von 6: " + when (step) {
                                1 -> "Umfang & Filterung"
                                2 -> "Kanzleiprofil & Mapping"
                                3 -> "Vorprüfung & Plausibilität"
                                4 -> "DATEV-Vorschau (125 Spalten)"
                                5 -> "Paketerzeugung"
                                else -> "Ergebnis & Exporthistorie"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Progress Step Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (i in 1..6) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Step Content Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (step) {
                        1 -> {
                            // SCHRITT 1: Umfang
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Select property filter & period scope:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text("Wirtschaftsjahr:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf("2026", "2025", "ALLE").forEach { yr ->
                                                FilterChip(
                                                    selected = yearFilter == yr,
                                                    onClick = { viewModel.setWizardFilters(year = yr) },
                                                    label = { Text(yr, fontSize = 12.sp) }
                                                )
                                            }
                                        }
                                    }

                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text("Belegtyp:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf("ALLE", "AUSGABEN", "EINNAHMEN").forEach { tp ->
                                                FilterChip(
                                                    selected = typeFilter == tp,
                                                    onClick = { viewModel.setWizardFilters(categoryType = tp) },
                                                    label = { Text(tp.take(3), fontSize = 11.sp) }
                                                )
                                            }
                                        }
                                    }
                                }

                                Column {
                                    Text("Wohneinheit:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf("ALLE", "WE 1", "WE 2", "WE 3", "WE 4", "WE 5", "WE 6", "WE 7").forEach { unit ->
                                            FilterChip(
                                                selected = unitFilter == unit,
                                                onClick = { viewModel.setWizardFilters(unit = unit) },
                                                label = { Text(unit, fontSize = 12.sp) }
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider()

                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFFFF7ED)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Bereits exportierte Belege werden aus Sicherheitsgründen immer ausgeschlossen. Nach einer fachlichen Änderung muss die DATEV-Aufteilung erneut freigegeben werden.",
                                        modifier = Modifier.padding(10.dp),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF9A3412)
                                    )
                                }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Export-Umfang Vorschau:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("• Eingeschlossene Buchungssätze: ${mappedRecords.size}", fontSize = 12.sp)
                                        Text("• Ausgeschlossene Belege: ${excludedReceipts.size}", fontSize = 12.sp)
                                        Text(
                                            "• Gesamtsumme Brutto: ${String.format(Locale.GERMANY, "%.2f", validationReport?.totalAmount ?: 0.0)} EUR",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (excludedReceipts.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                "Warum Belege ausgeschlossen sind:",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            excludedReceipts.take(10).forEach { receipt ->
                                                val key = com.example.util.DatevReceiptEligibility.key(receipt)
                                                val reasons = exclusionReasons[key].orEmpty()
                                                Text(
                                                    "• ${receipt.getEffectiveDisplayId()}: ${reasons.joinToString(" ")}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                            if (excludedReceipts.size > 10) {
                                                Text(
                                                    "Weitere ${excludedReceipts.size - 10} Belege sind ausgeschlossen.",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // SCHRITT 2: Kanzleiprofil
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Mandats- & Kanzleiprofil Stammdaten:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            viewModel.updateActiveDatevProfile(com.example.data.DatevProfile.createDefaultSkr03())
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("SKR03 Standard", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.updateActiveDatevProfile(com.example.data.DatevProfile.createDefaultSkr04())
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("SKR04 Standard", fontSize = 11.sp)
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = editableBeraterNr,
                                        onValueChange = {
                                            editableBeraterNr = it
                                            viewModel.updateActiveDatevProfile(activeProfile.copy(beraterNummer = it))
                                        },
                                        label = { Text("Berater-Nr.") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = editableMandantenNr,
                                        onValueChange = {
                                            editableMandantenNr = it
                                            viewModel.updateActiveDatevProfile(activeProfile.copy(mandantenNummer = it))
                                        },
                                        label = { Text("Mandanten-Nr.") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }

                                OutlinedTextField(
                                    value = editableMandantenName,
                                    onValueChange = {
                                        editableMandantenName = it
                                        viewModel.updateActiveDatevProfile(activeProfile.copy(mandantenName = it))
                                    },
                                    label = { Text("Mandantenname") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = editableSachkontenLaenge,
                                        onValueChange = {
                                            editableSachkontenLaenge = it
                                            val len = it.toIntOrNull() ?: 4
                                            viewModel.updateActiveDatevProfile(activeProfile.copy(sachkontenLaenge = len.coerceIn(4, 8)))
                                        },
                                        label = { Text("Sachkontenlänge (4-8)") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Festschreibung:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = activeProfile.festschreibungskennzeichen,
                                                onCheckedChange = {
                                                    viewModel.updateActiveDatevProfile(activeProfile.copy(festschreibungskennzeichen = it))
                                                }
                                            )
                                            Text("Aktiv", fontSize = 12.sp)
                                        }
                                    }
                                }

                                HorizontalDivider()

                                Text("Kategorie-Zuordnungen (SKR Konten-Mapping):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Standard Gegenkonto Einnahmen: ${activeProfile.standardGegenkontoEinnahmen} (Debitor/Kasse)", fontSize = 12.sp)
                                        Text("Standard Gegenkonto Ausgaben: ${activeProfile.standardGegenkontoAusgaben} (Kreditor/Bank)", fontSize = 12.sp)
                                        Text("Standard Einnahmenkonto: ${activeProfile.defaultEinnahmenKonto}", fontSize = 12.sp)
                                        Text("Standard Ausgabenkonto: ${activeProfile.defaultAusgabenKonto}", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        3 -> {
                            // SCHRITT 3: Vorprüfung & Plausibilität
                            val report = validationReport
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (report != null) {
                                    if (report.isValidForExport) {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF166534))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text("Vorprüfung erfolgreich!", fontWeight = FontWeight.Bold, color = Color(0xFF166534), fontSize = 14.sp)
                                                    Text("Alle ${report.totalRecords} Buchungssätze sind DATEV-konform und freigegeben.", fontSize = 12.sp, color = Color(0xFF166534))
                                                }
                                            }
                                        }
                                    } else {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFF991B1B))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text("Vorprüfung mit ${report.errors.size} Blockern:", fontWeight = FontWeight.Bold, color = Color(0xFF991B1B), fontSize = 14.sp)
                                                    Text("Bitte prüfe und bestätige die betroffenen Belege vor dem Export.", fontSize = 12.sp, color = Color(0xFF991B1B))
                                                }
                                            }
                                        }
                                    }

                                    Text(
                                        "Ungeprüfte KI-Vorschläge können nicht als DATEV-Paket exportiert werden.",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.error
                                    )

                                    Text("Prüfprotokoll Details:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                                    report.errors.forEach { err ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = Color(0xFFDC2626),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "BLOCKER",
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(err.message, fontSize = 12.sp, color = Color(0xFF7F1D1D))
                                            }
                                        }
                                    }

                                    report.warnings.forEach { warn ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                                            border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = Color(0xFFD97706),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "WARNUNG",
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(warn.message, fontSize = 12.sp, color = Color(0xFF78350F))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        4 -> {
                            // SCHRITT 4: DATEV-Vorschau (Spaltenansicht)
                            Column(modifier = Modifier.fillMaxSize()) {
                                Text("DATEV EXTF Buchungszeilen Vorschau (${mappedRecords.size} Sätze):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(8.dp))

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(mappedRecords.size) { idx ->
                                        val rec = mappedRecords[idx]
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("${rec.belegdatum} | ${rec.belegfeld1}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Text(
                                                        "${String.format(Locale.GERMANY, "%.2f", rec.bruttobetrag)} EUR (${rec.sollHaben})",
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (rec.sollHaben == "H") Color(0xFF166534) else MaterialTheme.colorScheme.primary,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    "Soll: ${rec.sachkonto} -> Gegen: ${rec.gegenkonto} | KOST1: ${rec.kost1} | KOST2: ${rec.kost2}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    "Text: ${rec.beschreibung}",
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        5 -> {
                            // SCHRITT 5: Erzeugungsoptionen
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Wähle das gewünschte Übergabeformat:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Card(
                                        border = if (targetFormat == "FULL_ZIP") BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.setWizardFilters(targetFormat = "FULL_ZIP") }
                                    ) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = targetFormat == "FULL_ZIP", onClick = { viewModel.setWizardFilters(targetFormat = "FULL_ZIP") })
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("Vollständiges Steuerberater-Übergabepaket (.zip)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("Enthält 01_DATEV/, 02_Belege/, 03_Kontrolle/, 04_Dokumentation/ und manifest.json mit SHA-256 Hashes.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }

                                    Card(
                                        border = if (targetFormat == "EXTF_CSV") BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.setWizardFilters(targetFormat = "EXTF_CSV") }
                                    ) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            RadioButton(selected = targetFormat == "EXTF_CSV", onClick = { viewModel.setWizardFilters(targetFormat = "EXTF_CSV") })
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("Reiner EXTF Buchungsstapel (.csv)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Text("125 Spalten, DATEV-Formatversion 13, ohne digitale Belege.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider()

                                Button(
                                    onClick = {
                                        exportScope.launch {
                                            viewModel.executeWizardExport(context)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    enabled = validationReport?.isValidForExport == true && mappedRecords.isNotEmpty()
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("DATEV-Exportpaket jetzt erzeugen", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        else -> {
                            // SCHRITT 6: Ergebnis & Exporthistorie
                            val res = lastResult
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (res != null) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF166534))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Exportpaket erfolgreich erstellt!", fontWeight = FontWeight.Bold, color = Color(0xFF166534), fontSize = 15.sp)
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Exportlauf-ID: ${res.exportId}", fontSize = 12.sp, color = Color(0xFF166534), fontWeight = FontWeight.SemiBold)
                                            Text("Dateiname: ${res.zipFile.name}", fontSize = 12.sp, color = Color(0xFF166534))
                                            Text("SHA-256: ${res.sha256Checksum.take(24)}...", fontSize = 11.sp, color = Color(0xFF166534))
                                            Text("Gesamtsumme: ${String.format(Locale.GERMANY, "%.2f", res.totalAmountEur)} EUR (${res.totalRecords} Sätze)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                context.packageName + ".provider",
                                                res.zipFile
                                            )
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/zip"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "DATEV Exportpaket teilen"))
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("DATEV Paket Teilen / Speichern", fontWeight = FontWeight.Bold)
                                    }
                                }

                                HorizontalDivider()

                                Text("Revisionssichere Exporthistorie (${auditRuns.size} Läufe):", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                                if (auditRuns.isEmpty()) {
                                    Text("Bisher keine protokollierten Exportläufe vorhanden.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    auditRuns.take(5).forEach { run ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(run.exportlaufId, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Text(run.status, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                                                }
                                                Text("${run.kanzleiprofilNameVersion} | ${run.bookingCount} Sätze | ${String.format(Locale.GERMANY, "%.2f", run.totalAmount)} EUR", fontSize = 11.sp)
                                                Text("SHA-256: ${run.zipSha256.take(20)}...", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Navigation Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step > 1 && step < 6) {
                        OutlinedButton(onClick = { viewModel.setWizardStep(step - 1) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Zurück")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (step < 5) {
                        Button(onClick = { viewModel.setWizardStep(step + 1) }) {
                            Text("Weiter")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    } else if (step == 6) {
                        Button(onClick = onDismiss) {
                            Text("Fertigstellen")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentCropDialog(
    initialBitmap: Bitmap,
    onDismiss: () -> Unit,
    onCropped: (Bitmap) -> Unit
) {
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var applyGrayscale by remember { mutableStateOf(false) }
    var applyAutoCrop by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = DarkNavy
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = EmeraldGreen)
                        Text(
                            "Dokument zuschneiden & optimieren",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen", tint = Color.White)
                    }
                }

                // Image Canvas Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val previewBitmap = remember(initialBitmap, rotationAngle, applyGrayscale, applyAutoCrop) {
                        processCroppedBitmap(
                            initialBitmap,
                            rotationAngle,
                            applyGrayscale,
                            if (applyAutoCrop) 0.06f else 0f,
                            if (applyAutoCrop) 0.06f else 0f,
                            if (applyAutoCrop) 0.04f else 0f,
                            if (applyAutoCrop) 0.04f else 0f
                        )
                    }
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "Zuschnitt-Vorschau",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }

                // Controls
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rotate Left
                        IconButton(
                            onClick = { rotationAngle = (rotationAngle - 90f) % 360f },
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(Icons.Default.RotateLeft, contentDescription = "Drehen Links", tint = Color.White)
                        }

                        // Rotate Right
                        IconButton(
                            onClick = { rotationAngle = (rotationAngle + 90f) % 360f },
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = "Drehen Rechts", tint = Color.White)
                        }

                        // Auto-Crop Toggle
                        FilterChip(
                            selected = applyAutoCrop,
                            onClick = { applyAutoCrop = !applyAutoCrop },
                            label = { Text("Auto-Ränder", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldGreen,
                                selectedLabelColor = Color.White
                            )
                        )

                        // Grayscale Toggle
                        FilterChip(
                            selected = applyGrayscale,
                            onClick = { applyGrayscale = !applyGrayscale },
                            label = { Text("S/W-Filter", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.FilterBAndW, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldGreen,
                                selectedLabelColor = Color.White
                            )
                        )
                    }

                    // Apply Button
                    Button(
                        onClick = {
                            val finalBitmap = processCroppedBitmap(
                                initialBitmap,
                                rotationAngle,
                                applyGrayscale,
                                if (applyAutoCrop) 0.06f else 0f,
                                if (applyAutoCrop) 0.06f else 0f,
                                if (applyAutoCrop) 0.04f else 0f,
                                if (applyAutoCrop) 0.04f else 0f
                            )
                            onCropped(finalBitmap)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Zuschnitt übernehmen & KI-Analyse starten", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

fun processCroppedBitmap(
    source: Bitmap,
    rotation: Float,
    grayscale: Boolean,
    cropTopRatio: Float,
    cropBottomRatio: Float,
    cropLeftRatio: Float,
    cropRightRatio: Float
): Bitmap {
    var bitmap = source

    // Rotate if needed
    if (rotation != 0f) {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(rotation)
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    // Crop edges
    val left = (bitmap.width * cropLeftRatio).toInt().coerceIn(0, (bitmap.width * 0.3f).toInt())
    val top = (bitmap.height * cropTopRatio).toInt().coerceIn(0, (bitmap.height * 0.3f).toInt())
    val right = (bitmap.width * (1f - cropRightRatio)).toInt().coerceIn(left + 50, bitmap.width)
    val bottom = (bitmap.height * (1f - cropBottomRatio)).toInt().coerceIn(top + 50, bitmap.height)

    val cropWidth = (right - left).coerceAtLeast(10)
    val cropHeight = (bottom - top).coerceAtLeast(10)

    var cropped = Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)

    // Grayscale or High Contrast Filter
    if (grayscale) {
        val bwBitmap = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bwBitmap)
        val paint = android.graphics.Paint()
        val cm = android.graphics.ColorMatrix()
        cm.setSaturation(0f)
        val scale = 1.2f
        val translate = (-0.1f * 255f)
        val contrastMatrix = floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
        cm.postConcat(android.graphics.ColorMatrix(contrastMatrix))
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        canvas.drawBitmap(cropped, 0f, 0f, paint)
        cropped = bwBitmap
    }

    return cropped
}

@Composable
fun DriveRestoreConfirmationDialog(
    preview: com.example.data.DriveRestorePreviewResult?,
    isRestoring: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val inv = preview?.inventory ?: com.example.data.DriveInventoryCheck()

    AlertDialog(
        onDismissRequest = { if (!isRestoring) onDismiss() },
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Cloud, contentDescription = null, tint = AccentBlue)
                Text("Google Drive-Bestand gefunden", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Es wurde ein gesicherter App-Bestand in Ihrem Google Drive gefunden. Möchten Sie diesen vollständig in die lokale App wiederherstellen?",
                    fontSize = 12.sp,
                    color = DarkNavy
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SoftBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Prüfbericht (Drive Inventory):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                        HorizontalDivider()
                        DetailRow("Immobilien-Objekt", if (inv.propertyFound) inv.propertyName else "Kein Name")
                        DetailRow("Wohneinheiten", "${inv.unitsCount} Einheiten")
                        DetailRow("Belege im Index", "${inv.receiptCountInIndex} Einträge")
                        DetailRow("Metadaten-Dateien", "${inv.metadataFileCount} JSON-Dateien")
                        DetailRow("Hauptdokumente (PDF/Img)", "${inv.mainDocCount} Dateien")
                        DetailRow("DATEV-Profil", if (inv.datevProfileFound) "Vorhanden" else "Fehlt")
                        DetailRow("KI-Lernregeln", if (inv.learnedRulesFound) "Vorhanden" else "Fehlt")
                        
                        if (inv.duplicateCount > 0) {
                            Text("⚠️ Duplikate/Warnungen: ${inv.duplicateCount}", fontSize = 11.sp, color = CrimsonRed, fontWeight = FontWeight.Bold)
                        }
                        if (inv.missingFileCount > 0) {
                            Text("⚠️ Unvollständige Dateien: ${inv.missingFileCount}", fontSize = 11.sp, color = CrimsonRed)
                        }
                    }
                }

                if (inv.issues.isNotEmpty()) {
                    Text("Hinweise / Anmerkungen:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    inv.issues.forEach { issue ->
                        Text("• $issue", fontSize = 10.sp, color = CrimsonRed)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isRestoring,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                modifier = Modifier.testTag("confirm_restore_button")
            ) {
                if (isRestoring) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Wiederherstellen...", color = Color.White)
                } else {
                    Text("Jetzt wiederherstellen", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        },
        dismissButton = {
            if (!isRestoring) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_restore_button")
                ) {
                    Text("Abbrechen", color = Color.Gray)
                }
            }
        }
    )
}

@Composable
fun DriveRestoreReportDialog(
    report: com.example.data.DriveRestoreReport,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (report.isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (report.isSuccess) EmeraldGreen else CrimsonRed
                )
                Text(
                    if (report.isSuccess) "Wiederherstellung abgeschlossen" else "Wiederherstellungs-Bericht",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkNavy
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Zeitstempel: ${report.timestamp}", fontSize = 11.sp, color = Color.Gray)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SoftBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Wiederhergestellte Elemente:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                        HorizontalDivider()
                        DetailRow("Immobilien-Stammdaten", "${report.propertiesRestored} Objekt(e)")
                        DetailRow("Wohneinheiten", "${report.unitsRestored} Einheiten")
                        DetailRow("Belege geladen", "${report.receiptsRestored} Belege")
                        DetailRow("Hauptdokumente verknüpft", "${report.mainDocsLinked} Dateien")
                        DetailRow("Metadaten-Dateien", "${report.metadataFilesLoaded} geladen")
                        DetailRow("Belegpositionen", "${report.itemsRestored} Positionen")
                        DetailRow("Aufteilungen / Splits", "${report.splitsRestored} Aufteilungen")
                        DetailRow("DATEV-Profile", "${report.datevProfilesRestored} Profil")
                        DetailRow("KI-Lernregeln", "${report.learnedRulesRestored} Regeln")
                    }
                }

                if (report.errorCount > 0) {
                    Text("Fehler / Warnungen (${report.errorCount}):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CrimsonRed)
                    report.errors.forEach { err ->
                        Text("• $err", fontSize = 10.sp, color = CrimsonRed)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SlateGray),
                modifier = Modifier.testTag("close_restore_report_button")
            ) {
                Text("Schließen", color = Color.White)
            }
        }
    )
}

@Composable
fun ConfirmRepairDocumentDialog(
    receipt: Receipt,
    file: File,
    validation: FileValidationResult,
    viewModel: ReceiptViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repairUiState by viewModel.repairUiState.collectAsState()

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(file.absolutePath) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                if (validation.formatName == "PDF") {
                    val fd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = android.graphics.pdf.PdfRenderer(fd)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val bmp = Bitmap.createBitmap(600, (600.toFloat() / page.width * page.height).toInt(), Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        renderer.close()
                        fd.close()
                        previewBitmap = bmp
                    }
                } else {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    previewBitmap = bmp
                }
            } catch (e: Exception) {
                Log.e("ConfirmRepair", "Error decoding preview", e)
            }
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (repairUiState !is RepairUiState.Processing) {
                viewModel.resetRepairUiState()
                onDismiss()
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Build, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(24.dp))
                Text("Originaldokument zuordnen", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DarkNavy)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Dieses Dokument als neues Original für Beleg ${receipt.getEffectiveDisplayId()} (${receipt.aussteller}) zuordnen?",
                    fontSize = 13.sp,
                    color = DarkNavy
                )

                if (previewBitmap != null) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "Vorschau",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().background(Color.White)
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SoftBackground),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Dateiname: ${file.name}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                        Text("Format: ${validation.formatName} (${validation.mimeType})", fontSize = 11.sp, color = SlateGray)
                        Text("Größe: ${android.text.format.Formatter.formatShortFileSize(context, validation.sizeBytes)}", fontSize = 11.sp, color = SlateGray)
                        Text("SHA-256: ${validation.sha256.take(20)}...", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = SlateGray)
                        Text("Ziel: Steuerassistent Belege / ${receipt.datum.take(4)} / ${receipt.hauptkategorie}", fontSize = 10.sp, color = AccentBlue)
                    }
                }

                when (val state = repairUiState) {
                    is RepairUiState.Processing -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AccentBlue)
                            Text(state.step, fontSize = 12.sp, color = AccentBlue)
                        }
                    }
                    is RepairUiState.Success -> {
                        Card(colors = CardDefaults.cardColors(containerColor = EmeraldGreen.copy(alpha = 0.1f))) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen)
                                Text(state.message, fontSize = 12.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    is RepairUiState.Error -> {
                        Card(colors = CardDefaults.cardColors(containerColor = CrimsonRed.copy(alpha = 0.1f))) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonRed)
                                Text(state.error, fontSize = 12.sp, color = CrimsonRed)
                            }
                        }
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            if (repairUiState is RepairUiState.Success) {
                Button(
                    onClick = {
                        viewModel.resetRepairUiState()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("Fertig")
                }
            } else {
                Button(
                    onClick = {
                        viewModel.repairReceiptDocument(receipt, file, validation)
                    },
                    enabled = repairUiState !is RepairUiState.Processing,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    Text("Bestätigen & Hochladen")
                }
            }
        },
        dismissButton = {
            if (repairUiState !is RepairUiState.Processing && repairUiState !is RepairUiState.Success) {
                OutlinedButton(onClick = {
                    viewModel.resetRepairUiState()
                    onDismiss()
                }) {
                    Text("Abbrechen")
                }
            }
        }
    )
}

@Composable
fun DocumentStatusOverviewDialog(
    viewModel: ReceiptViewModel,
    onDismiss: () -> Unit
) {
    val receipts by viewModel.receipts.collectAsState()
    val downloadStatusMap by viewModel.documentDownloadStatus.collectAsState()

    val context = LocalContext.current

    var selectedReceiptForRepair by remember { mutableStateOf<Receipt?>(null) }
    var pendingRepairFile by remember { mutableStateOf<File?>(null) }
    var pendingValidation by remember { mutableStateOf<FileValidationResult?>(null) }
    var repairErrorMsg by remember { mutableStateOf<String?>(null) }

    val repairFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            selectedReceiptForRepair?.let { receipt ->
                try {
                    val inputStream = context.contentResolver.openInputStream(selectedUri)
                    val tempFile = File(context.cacheDir, "overview_repair_${System.currentTimeMillis()}")
                    inputStream?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    val validation = viewModel.validateFileForRepair(context, tempFile)
                    if (validation.isValid) {
                        pendingRepairFile = tempFile
                        pendingValidation = validation
                        repairErrorMsg = null
                    } else {
                        repairErrorMsg = validation.errorMessage ?: "Ungültiges Dokument"
                    }
                } catch (e: Exception) {
                    repairErrorMsg = "Fehler beim Lesen der Datei: ${e.message}"
                }
            }
        }
    }

    if (selectedReceiptForRepair != null && pendingRepairFile != null && pendingValidation != null) {
        ConfirmRepairDocumentDialog(
            receipt = selectedReceiptForRepair!!,
            file = pendingRepairFile!!,
            validation = pendingValidation!!,
            viewModel = viewModel,
            onDismiss = {
                pendingRepairFile = null
                pendingValidation = null
                selectedReceiptForRepair = null
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Search, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(24.dp))
                Text("Dokumenten-Status Übersicht", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = DarkNavy)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (repairErrorMsg != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = CrimsonRed.copy(alpha = 0.1f))) {
                        Text(repairErrorMsg!!, fontSize = 11.sp, color = CrimsonRed, modifier = Modifier.padding(8.dp))
                    }
                }

                Text(
                    "Übersicht des Dokumenten-Status aller gespeicherten Belege:",
                    fontSize = 12.sp,
                    color = SlateGray
                )

                receipts.forEach { receipt ->
                    val status = downloadStatusMap[receipt.internalId]
                    val isMissingOrError = receipt.driveFileId.isNullOrEmpty() || status?.state == DocumentState.ERROR || status?.state == DocumentState.UNSUPPORTED

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isMissingOrError) CrimsonRed.copy(alpha = 0.05f) else EmeraldGreen.copy(alpha = 0.05f)
                        ),
                        border = BorderStroke(1.dp, if (isMissingOrError) CrimsonRed.copy(alpha = 0.3f) else EmeraldGreen.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${receipt.getEffectiveDisplayId()}: ${receipt.aussteller}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = DarkNavy
                                )
                                Text(
                                    if (isMissingOrError) "⚠️ Reparierbar / Fehlt" else "✓ In Ordnung",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isMissingOrError) CrimsonRed else EmeraldGreen
                                )
                            }
                            Text(
                                "Datum: ${receipt.datum} | ${receipt.bruttobetrag} € | ${receipt.hauptkategorie}",
                                fontSize = 10.5.sp,
                                color = SlateGray
                            )
                            if (isMissingOrError) {
                                Button(
                                    onClick = {
                                        selectedReceiptForRepair = receipt
                                        repairFilePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Original nachreichen", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = DarkNavy)) {
                Text("Schließen")
            }
        }
    )
}

@Composable
fun OriginalReceiptAuditDialog(
    report: com.example.data.OriginalReceiptAuditReport,
    onDismiss: () -> Unit,
    onRepairReceipt: (String) -> Unit,
    onScanReceipt: (String) -> Unit,
    onCorrectMetadata: (internalId: String, newMime: String, newFilename: String) -> Unit
) {
    var selectedItemForCorrection by remember { mutableStateOf<com.example.data.OriginalReceiptAuditItem?>(null) }

    if (selectedItemForCorrection != null) {
        val item = selectedItemForCorrection!!
        val suggestedFilename = if (!item.storedFilename.endsWith(".${item.detectedExtension}", ignoreCase = true)) {
            val base = if (item.storedFilename.contains(".")) item.storedFilename.substringBeforeLast(".") else item.storedFilename
            "$base.${item.detectedExtension}"
        } else item.storedFilename

        AlertDialog(
            onDismissRequest = { selectedItemForCorrection = null },
            title = {
                Text("Metadaten korrigieren", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DarkNavy)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Sollen die gespeicherten Metadaten für Beleg ${item.displayId} an das tatsächlich erkannte Format angepasst werden?",
                        fontSize = 12.sp,
                        color = DarkNavy
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SoftBackground),
                        border = BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Erkanntes Format: ${item.detectedFormatName}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = EmeraldGreen)
                            Text("Gespeicherter MIME-Typ: ${item.storedMimeType} → Neu: ${item.detectedMimeType}", fontSize = 11.sp, color = DarkNavy)
                            Text("Gespeicherter Dateiname: ${item.storedFilename} → Neu: $suggestedFilename", fontSize = 11.sp, color = DarkNavy)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val itemToCorrect = item
                        selectedItemForCorrection = null
                        onCorrectMetadata(itemToCorrect.internalId, itemToCorrect.detectedMimeType, suggestedFilename)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("Jetzt korrigieren", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedItemForCorrection = null }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Verified, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(24.dp))
                Column {
                    Text("Originalbelege Bestandsprüfung", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DarkNavy)
                    Text("Magic-Bytes Echtzeit-Audit in Google Drive", fontSize = 11.sp, color = SlateGray)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Summary KPI Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = SlateGray.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Geprüft", fontSize = 9.5.sp, color = SlateGray)
                            Text("${report.totalChecked}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkNavy)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = EmeraldGreen.copy(alpha = 0.12f))
                    ) {
                        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Gültig", fontSize = 9.5.sp, color = EmeraldGreen)
                            Text("${report.validCount}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = EmeraldGreen)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = WarmOrange.copy(alpha = 0.12f))
                    ) {
                        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Warnungen", fontSize = 9.5.sp, color = WarmOrange)
                            Text("${report.warningCount}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = WarmOrange)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = CrimsonRed.copy(alpha = 0.12f))
                    ) {
                        Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Ungültig", fontSize = 9.5.sp, color = CrimsonRed)
                            Text("${report.invalidCount}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CrimsonRed)
                        }
                    }
                }

                HorizontalDivider(color = BorderColor)

                if (report.items.isEmpty()) {
                    Text("Keine Belege im Belegindex gefunden.", fontSize = 12.sp, color = SlateGray)
                }

                report.items.forEach { item ->
                    val bgColor = when (item.status) {
                        com.example.data.AuditStatus.GREEN -> EmeraldGreen.copy(alpha = 0.06f)
                        com.example.data.AuditStatus.YELLOW -> WarmOrange.copy(alpha = 0.08f)
                        com.example.data.AuditStatus.RED -> CrimsonRed.copy(alpha = 0.06f)
                    }
                    val borderColor = when (item.status) {
                        com.example.data.AuditStatus.GREEN -> EmeraldGreen.copy(alpha = 0.4f)
                        com.example.data.AuditStatus.YELLOW -> WarmOrange.copy(alpha = 0.5f)
                        com.example.data.AuditStatus.RED -> CrimsonRed.copy(alpha = 0.4f)
                    }
                    val statusText = when (item.status) {
                        com.example.data.AuditStatus.GREEN -> "✓ GRÜN: Gültig"
                        com.example.data.AuditStatus.YELLOW -> "⚠️ GELB: Format abweichend"
                        com.example.data.AuditStatus.RED -> "❌ ROT: Ungültig / Kein Original"
                    }
                    val statusTextColor = when (item.status) {
                        com.example.data.AuditStatus.GREEN -> EmeraldGreen
                        com.example.data.AuditStatus.YELLOW -> WarmOrange
                        com.example.data.AuditStatus.RED -> CrimsonRed
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().testTag("audit_item_${item.displayId}"),
                        colors = CardDefaults.cardColors(containerColor = bgColor),
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${item.displayId} • ${item.aussteller}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = DarkNavy
                                )
                                Text(
                                    statusText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusTextColor
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Datum: ${item.datum}",
                                    fontSize = 10.5.sp,
                                    color = SlateGray
                                )
                                Text(
                                    "Größe: ${if (item.sizeBytes > 0) String.format(java.util.Locale.GERMANY, "%.1f KB", item.sizeBytes / 1024.0) else "0 Bytes"}",
                                    fontSize = 10.5.sp,
                                    color = SlateGray
                                )
                            }

                            Text(
                                "Gespeichert: ${item.storedMimeType} (${item.storedFilename})",
                                fontSize = 10.sp,
                                color = DarkNavy
                            )
                            Text(
                                "Erkannt: ${item.detectedFormatName}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkNavy
                            )

                            Text(
                                "Ursache: ${item.cause}",
                                fontSize = 10.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                color = statusTextColor,
                                lineHeight = 13.sp
                            )

                            if (item.status == com.example.data.AuditStatus.RED) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onRepairReceipt(item.internalId) },
                                        modifier = Modifier.weight(1f).testTag("reupload_button_${item.displayId}"),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        border = BorderStroke(1.dp, AccentBlue),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
                                    ) {
                                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Datei nachreichen", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { onScanReceipt(item.internalId) },
                                        modifier = Modifier.weight(1f).testTag("rescan_button_${item.displayId}"),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        border = BorderStroke(1.dp, EmeraldGreen),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen)
                                    ) {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Neu scannen", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else if (item.status == com.example.data.AuditStatus.YELLOW) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Button(
                                    onClick = { selectedItemForCorrection = item },
                                    modifier = Modifier.fillMaxWidth().testTag("correct_metadata_button_${item.displayId}"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = WarmOrange)
                                ) {
                                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Metadaten nach Bestätigung korrigieren", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = DarkNavy)) {
                Text("Schließen", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun MetadataDuplicateReportDialog(
    report: com.example.data.MetadataDuplicateReport,
    onDismiss: () -> Unit,
    onPrepareCleanup: (com.example.data.MetadataDuplicateGroup) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(24.dp))
                Column {
                    Text("Metadaten-Dubletten-Bericht", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DarkNavy)
                    Text("Echtzeit-Analyse im Google Drive Beleg-Ordner", fontSize = 11.sp, color = SlateGray)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Dieser Bericht zeigt alle im Ordner '_BelegApp-Daten/receipts' gefundenen Beleg-Metadaten-Dateien. Es wurden keine Dateien gelöscht oder verändert.",
                    fontSize = 11.sp,
                    color = SlateGray
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SoftBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Zusammenfassung:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                        HorizontalDivider()
                        DetailRow("Belege mit Mehrfachdateien", "${report.totalGroupsWithDuplicates}")
                        DetailRow("Überzählige Dubletten-Dateien", "${report.totalDuplicateFilesCount}")
                    }
                }

                val duplicatesOnly = report.groups.filter { it.files.size > 1 }
                
                if (duplicatesOnly.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(EmeraldGreen.copy(alpha = 0.05f))
                            .border(1.dp, EmeraldGreen.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✅ Keine Metadaten-Dubletten gefunden!", fontSize = 12.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("Details zu gefundenen Dubletten:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    
                    duplicatesOnly.forEach { group ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("internalId: ${group.internalId}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkNavy)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(CrimsonRed.copy(alpha = 0.1f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("${group.files.size} Dateien", fontSize = 10.sp, color = CrimsonRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                                
                                Text(
                                    "Referenzierte ID im Index: ${group.referencedFileIdInIndex ?: "Keine Referenz im Index"}",
                                    fontSize = 11.sp,
                                    color = if (group.referencedFileIdInIndex != null) EmeraldGreen else CrimsonRed,
                                    fontWeight = FontWeight.Medium
                                )
                                
                                HorizontalDivider()
                                
                                group.files.forEach { file ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("📄 ${file.name}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                                            if (file.isReferencedInIndex) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(EmeraldGreen.copy(alpha = 0.1f))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text("Im Index aktiv", fontSize = 9.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(SlateGray.copy(alpha = 0.1f))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text("Überzählige Dublette", fontSize = 9.sp, color = SlateGray)
                                                }
                                            }
                                        }
                                        Text("Drive ID: ${file.driveId}", fontSize = 10.sp, color = SlateGray)
                                        Text("Erstellt: ${file.createdTime}", fontSize = 9.sp, color = SlateGray)
                                        Text("Geändert: ${file.modifiedTime}", fontSize = 9.sp, color = SlateGray)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                        if (group.referencedFileIdInIndex != null) {
                            OutlinedButton(
                                onClick = { onPrepareCleanup(group) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonRed)
                            ) {
                                Text("Verwaiste JSON-Dateien sicher bereinigen", fontSize = 11.sp)
                            }
                        } else {
                            Text(
                                "Keine Bereinigung möglich: receipt-index.json enthält keine aktive Referenz.",
                                color = CrimsonRed,
                                fontSize = 10.sp
                            )
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
                Text("Schließen", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun RecycleBinDialog(
    viewModel: ReceiptViewModel,
    onDismiss: () -> Unit
) {
    val deletedReceipts by viewModel.deletedReceipts.collectAsState()
    val duplicateCleanupState by viewModel.duplicateCleanupState.collectAsState()
    var receiptToPermanentlyDelete by remember { mutableStateOf<Receipt?>(null) }
    var permanentDeleteError by remember { mutableStateOf<String?>(null) }
    var showDeleteSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadPendingDuplicateCleanupOperations()
    }
    
    when (val cleanupState = duplicateCleanupState) {
        is DuplicateCleanupUiState.MergeConfirmation -> AlertDialog(
            onDismissRequest = viewModel::dismissDuplicateCleanupState,
            title = { Text("Dubletten sicher zusammenführen", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Erhalten bleibt: ${cleanupState.preview.canonical.displayId}")
                    Text(
                        "Entfernt werden: " +
                            cleanupState.preview.duplicatesToRemove.joinToString { it.displayId }
                    )
                    Text(
                        "Die gemeinsame Hauptdatei wird nicht gelöscht.",
                        color = EmeraldGreen,
                        fontWeight = FontWeight.Bold
                    )
                    if (cleanupState.preview.metadataPlan.orphanMetadataFileIds.isNotEmpty()) {
                        Text(
                            "Verwaiste Metadatendateien nach Bestätigung: " +
                                cleanupState.preview.metadataPlan.orphanMetadataFileIds.joinToString()
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = viewModel::confirmDuplicateMerge) {
                    Text("Zusammenführen")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::dismissDuplicateCleanupState) {
                    Text("Abbrechen")
                }
            }
        )

        is DuplicateCleanupUiState.WholeGroupConfirmation -> AlertDialog(
            onDismissRequest = viewModel::dismissDuplicateCleanupState,
            title = { Text("Gesamte Dublettengruppe löschen", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Alle Belegdatensätze und Metadaten werden gelöscht. " +
                            "Die gemeinsame Hauptdatei wird exakt einmal und zuletzt gelöscht.",
                        color = CrimsonRed
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = cleanupState.firstConfirmation,
                            onCheckedChange = {
                                viewModel.setWholeDuplicateGroupConfirmations(
                                    it,
                                    cleanupState.secondConfirmation
                                )
                            }
                        )
                        Text("Ich habe die vollständige Gruppe geprüft.")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = cleanupState.secondConfirmation,
                            onCheckedChange = {
                                viewModel.setWholeDuplicateGroupConfirmations(
                                    cleanupState.firstConfirmation,
                                    it
                                )
                            }
                        )
                        Text("Ich bestätige die unwiderrufliche Löschung.")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmWholeDuplicateGroupDeletion,
                    enabled = cleanupState.firstConfirmation && cleanupState.secondConfirmation,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed)
                ) {
                    Text("Gruppe endgültig löschen")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = viewModel::dismissDuplicateCleanupState) {
                    Text("Abbrechen")
                }
            }
        )

        else -> Unit
    }

    if (receiptToPermanentlyDelete != null) {
        AlertDialog(
            onDismissRequest = { receiptToPermanentlyDelete = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonRed)
                    Text("Endgültig löschen?", fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    "Möchten Sie den Beleg '${receiptToPermanentlyDelete!!.getEffectiveDisplayId()}' wirklich unwiderruflich und endgültig löschen?\n\n" +
                    "Diese Aktion kann nicht rückgängig gemacht werden. Die zugehörigen Dateien auf Google Drive werden, falls vorhanden, endgültig gelöscht. " +
                    "Stellen Sie sicher, dass keine anderen Belege diese Dateien referenzieren.",
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

