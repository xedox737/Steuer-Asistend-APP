Warning: truncated output (original token count: 184612)
Total output lines: 14029

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
import androidx.compose.material.icons.filled.Assessment
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
        AppScreen.DASHBOARD -> "Start"
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
    val headerIcon = when (currentScreen) {
        AppScreen.DASHBOARD -> Icons.Default.Home
        AppScreen.RECEIPTS_LIST -> Icons.Default.Receipt
        AppScreen.ADD_RECEIPT -> Icons.Default.AddCircle
        AppScreen.PROPERTIES -> Icons.Default.Apartment
        AppScreen.MORE -> Icons.Default.MoreHoriz
        else -> null
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
                        headerIcon?.let { icon ->
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = AccentBlue.copy(alpha = 0.12f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                        if (currentScreen == AppScreen.DASHBOARD) {
                            Column {
                                Text("ImmoPilot", fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 25.sp)
                                Text("Immobilien. Finanzen. Steuern.", color = SlateGray, fontSize = 11.sp)
                            }
                        } else {
                            Text(screenTitle, fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 20.sp)
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
                                if (isPrimaryAction) {
                                    Surface(
                                        modifier = Modifier.size(54.dp),
                                        shape = CircleShape,
                                        color = AccentBlue,
                                        shadowElevation = 6.dp
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(30.dp))
                                        }
                                    }
                                } else {
                                    Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
                                }
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
                                unselectedIconColor = if (isPrimaryAction) Color.White else SlateGray,
                                unselectedTextColor = SlateGray,
                                indicatorColor = if (isPrimaryAction) Color.Transparent else Color(0xFFDBEAFE)
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
    val bankTransactions by viewModel.bankTransactions.collectAsState()
    val learnedRulesCount by viewModel.learnedRulesCount.collectAsState()
    val bankStatementResult by viewModel.bankStatementResult.collectAsState()
    val missingReceiptsCount = bankStatementResult?.missingReceiptsCount ?: 0
    val rentArrearsCount = bankStatementResult?.rentArrearsCount ?: 0
    val totalBankAlerts = missingReceiptsCount + rentArrearsCount
    val openBankTransactions = remember(bankTransactions) { BankCompactUiPolicy.counts(bankTransactions).open }
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
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = Ui2.shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(Ui2.padding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("☀️", fontSize = 30.sp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Hallo Sergej!", fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Schön, dass du da bist!", fontSize = 12.sp, lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    java.time.LocalDate.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("EEEE\ndd.MM.yyyy", Locale.GERMAN)
                    ).replaceFirstChar { it.titlecase(Locale.GERMAN) },
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
        }
        Ui2Section("Aktueller Stand") {
            Ui2Grid(listOf(
                Triple("Offene Buchungen", openBankTransactions.toString(), AccentBlue),
                Triple("Offene Belege", missingReceiptsCount.toString(), EmeraldGreen),
                Triple("Belege gesamt", receipts.size.toString(), Color(0xFF7C3AED)),
                Triple("Regeln aktiv", learnedRulesCount.toString(), WarmOrange)
            )) { metric, modifier -> Ui2Metric(metric.first, metric.second, modifier, metric.third) }
            if (totalBankAlerts > 0) {
                Ui2Destination("$totalBankAlerts Hinweise aus dem Bankabgleich",
                    "$missingReceiptsCount fehlende Belege · $rentArrearsCount Mietrückstände",
                    Icons.Default.Warning) { showKiPowerCenterDialog = true }
            }
        }
        Ui2Section("Schnellaktionen") {
            val dashboardActions = listOf(
                Ui2Action("Beleg scannen", "", Icons.Default.PhotoCamera) { viewModel.setScreen(AppScreen.ADD_RECEIPT) },
                Ui2Action("Kontoauszüge importieren", "", Icons.Default.AccountBalance) { viewModel.setScreen(AppScreen.BANK) },
                Ui2Action("Neue Buchung", "", Icons.Default.AddCircle, EmeraldGreen) { viewModel.setScreen(AppScreen.ADD_RECEIPT) },
                Ui2Action("Auswertung anzeigen", "", Icons.Default.Assessment, EmeraldGreen) { viewModel.setScreen(AppScreen.LEDGER) }
            )
            Ui2Grid(dashboardActions) { action, modifier -> DashboardQuickAction(action, modifier) }
        }
        Ui2Section("Letzte Aktivitäten") {
            // The data source has receipt dates rather than a separate audit log. Keep the
            // visual activity treatment while naming the data honestly.
            Text("Zuletzt datierte Belege", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (receipts.isEmpty()) Text("Noch keine Belege erfasst.")
            receipts.sortedByDescending { it.datum }.take(5).forEachIndexed { index, receipt ->
                DashboardActivityRow(receipt) { selectedReceipt = receipt }
                if (index < receipts.take(5).lastIndex) HorizontalDivider(color = BorderColor)
            }
            TextButton(onClick = { viewModel.setScreen(AppScreen.RECEIPTS_LIST) }) { Text("Alle Belege anzeigen") }
        }
        Ui2Section("Einnahmen & Ausgaben") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardMoneyMetric("Einnahmen", totalIncome, Modifier.weight(1f))
                DashboardMoneyMetric("Ausgaben", totalExpenses, Modifier.weight(1f))
                DashboardMoneyMetric("Saldo", netCashflow, Modifier.weight(1f))
            }
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

@Composable
private fun DashboardQuickAction(action: Ui2Action, modifier: Modifier = Modifier) {
    Card(
        onClick = action.onClick,
        modifier = modifier.heightIn(min = 96.dp),
        shape = Ui2.shape,
        colors = CardDefaults.cardColors(
            containerColor = action.color.copy(alpha = 0.09f),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(action.icon, contentDescription = null, tint = action.color, modifier = Modifier.size(30.dp))
            Text(action.title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun DashboardActivityRow(receipt: Receipt, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(Modifier.size(36.dp), shape = RoundedCornerShape(10.dp), color = AccentBlue.copy(alpha = 0.10f)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Receipt, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(21.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(receipt.aussteller.ifBlank { receipt.getEffectiveDisplayId() }, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Text("${receipt.datum} · ${NumberFormatter.format(receipt.bruttobetrag)}", fontSize = 11.sp, lineHeight = 14.sp, color = SlateGray, maxLines = 1)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun DashboardMoneyMetric(label: String, amount: Double, modifier: Modifier = Modifier) {
    Surface(modifier, shape = Ui2.shape, color = AccentBlue.copy(alpha = 0.08f)) {
        Column(Modifier.padding(horizontal = 6.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(NumberFormatter.format(amount), fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, textAlign = TextAlign.Center)
            Text(label, fontSize = 10.sp, lineHeight = 12.sp, color = SlateGray, maxLines = 1)
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = Ui2.shape,
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3FF)),
            border = BorderStroke(1.dp, Color(0xFFD8E9FF))
        ) {
            Row(
                Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = AccentBlue.copy(alpha = 0.12f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (monitor) Icons.Default.Build else Icons.Default.Assessment, null, tint = AccentBlue)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (monitor) "Sanierung im Blick" else "Gebäude abschreiben",
                        fontWeight = FontWeight.Bold,
                        color = DarkNavy
                    )
                    Text(
                        if (monitor) "Behalte Kosten, Zeitraum und die 15%-Prüfung im Blick."
                        else "Verwalte deine Immobilien, berechne die AfA und behalte wichtige Daten im Blick.",
                        fontSize = 12.sp,
                        color = SlateGray
                    )
                }
            }
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
                        "Belege",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = DarkNavy
                    )
                    Text(
                        "Übersicht, Suche und Zuordnung",
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
            shape = Ui2.shape
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
            placeholder = { Text("Belege suchen …") },
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
            shape = Ui2.shape
        )

        // Category Quick Filters
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryFilterChip(
                label = "Alle (${receipts.size})",
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
   …124612 tokens truncated…                   listOf("ALLE", "AUSGABEN", "EINNAHMEN").forEach { tp ->
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
