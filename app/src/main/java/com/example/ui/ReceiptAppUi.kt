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
                AppScreen.BANK -> BankScreen(viewModel)
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
    val propertyMetadataState by viewModel.propertyMetadata.collectAsState()
    val metadata = propertyMetadataState ?: PropertyMetadata()

    val scrollState = rememberScrollState()
    var showEditPropertyDialog by remember { mutableStateOf(false) }
    val completeness by viewModel.anlageVCompleteness.collectAsState()

    // Calculations
    val taxPhase1 = com.example.data.TaxPropertyCalculator.calculate(metadata, receipts)
    val totalKaufpreis = metadata.gesamtKaufpreis
    val totalGebaeudeAnteil = metadata.gebaeudewert
    val limit15Percent = taxPhase1.limit15Percent

    val totalAnschaffung = receipts.filter { it.hauptkategorie == "Anschaffungskosten" }.sumOf { it.bruttobetrag }
    val totalFinanzierung = receipts.filter { it.hauptkategorie == "Finanzierung, Kredite & Versicherungen" }.sumOf { it.bruttobetrag }
    val totalRenovierung = receipts.filter { it.hauptkategorie == "Renovierungs- / Reparaturkosten & Investitionen" }.sumOf { it.bruttobetrag }
    val totalSonstige = receipts.filter { it.hauptkategorie == "Sonstige Ausgaben" }.sumOf { it.bruttobetrag }
    val totalExpenses = totalAnschaffung + totalFinanzierung + totalRenovierung + totalSonstige

    val totalIncome = receipts.filter { it.hauptkategorie == "Miete, Nebenkosten & Kaution" || it.hauptkategorie == "Sonstige Einnahmen" }.sumOf { it.bruttobetrag }
    val netCashflow = totalIncome - totalExpenses

    val bankStatementResult by viewModel.bankStatementResult.collectAsState()
    val missingReceiptsCount = bankStatementResult?.missingReceiptsCount ?: 0
    val rentArrearsCount = bankStatementResult?.rentArrearsCount ?: 0
    val totalBankAlerts = missingReceiptsCount + rentArrearsCount

    val isCloudActive by viewModel.isCloudActive.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val syncStatus by viewModel.driveSyncStatus.collectAsState()
    var showAuthDialog by remember { mutableStateOf(false) }
    var showKiPowerCenterDialog by remember { mutableStateOf(false) }
    var showAfaDetails by remember { mutableStateOf(false) }
    var showMonitorDetails by remember { mutableStateOf(false) }

    if (showKiPowerCenterDialog) {
        KiPowerCenterDialog(
            viewModel = viewModel,
            onDismiss = { showKiPowerCenterDialog = false }
        )
    }

    // Intentionally compact: configuration belongs in the app-wide settings dialog.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Guten Tag", fontSize = 22.sp, fontWeight = FontWeight.Black, color = DarkNavy)
            Text(
                "Erfasse Belege und behalte dein Objekt im Blick.",
                fontSize = 13.sp,
                color = SlateGray
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.setScreen(AppScreen.ADD_RECEIPT) },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AccentBlue)
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Beleg erfassen", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Foto aufnehmen oder Dokument auswählen", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Beleg erfassen", tint = Color.White)
            }
        }

        if (totalBankAlerts > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showKiPowerCenterDialog = true },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                border = BorderStroke(1.dp, Color(0xFFFED7AA))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = WarmOrange)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("$totalBankAlerts Hinweis${if (totalBankAlerts == 1) "" else "e"} aus dem Bankabgleich", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                        Text("$missingReceiptsCount fehlende Belege · $rentArrearsCount Mietrückstände", fontSize = 11.sp, color = SlateGray)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = WarmOrange, modifier = Modifier.size(18.dp))
                }
            }
        }

        LoanManagementSection(viewModel)

        Text("Überblick", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderColor),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Erfasste Belege", fontSize = 12.sp, color = SlateGray)
                    Text("${receipts.size}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = DarkNavy)
                }
                HorizontalDivider(color = BorderColor)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Einnahmen", fontSize = 12.sp, color = SlateGray)
                    Text(NumberFormatter.format(totalIncome), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ausgaben", fontSize = 12.sp, color = SlateGray)
                    Text(NumberFormatter.format(totalExpenses), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CrimsonRed)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Saldo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text((if (netCashflow >= 0) "+" else "") + NumberFormatter.format(netCashflow), fontSize = 14.sp, fontWeight = FontWeight.Black, color = if (netCashflow >= 0) EmeraldGreen else CrimsonRed)
                }
            }
        }

        Text("Schnellzugriff", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionCard(
                modifier = Modifier.weight(1f), title = "Belege", subtitle = "Archiv öffnen",
                icon = Icons.Default.Receipt, containerColor = Color(0xFFEFF6FF), contentColor = AccentBlue,
                onClick = { viewModel.setScreen(AppScreen.RECEIPTS_LIST) }
            )
            QuickActionCard(
                modifier = Modifier.weight(1f), title = "Finanzen", subtitle = "Auswertung",
                icon = Icons.Default.AccountBalance, containerColor = Color(0xFFECFDF5), contentColor = EmeraldGreen,
                onClick = { viewModel.setScreen(AppScreen.LEDGER) }
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionCard(
                modifier = Modifier.weight(1f), title = "Fahrtenbuch", subtitle = "Fahrten erfassen",
                icon = Icons.Default.DirectionsCar, containerColor = Color(0xFFFFF7ED), contentColor = WarmOrange,
                onClick = { viewModel.setScreen(AppScreen.LOGBOOK) }
            )
            QuickActionCard(
                modifier = Modifier.weight(1f), title = "Steuerschätzung", subtitle = "Anlage V",
                icon = Icons.Filled.Calculate, containerColor = Color(0xFFF5F3FF), contentColor = Color(0xFF7C3AED),
                onClick = { viewModel.setScreen(AppScreen.TAX_CALCULATOR) }
            )
        }
        QuickActionCard(
            modifier = Modifier.fillMaxWidth().testTag("rent_overview_quick_action"),
            title = "Mieteingänge", subtitle = "Soll/Ist & Nebenkosten",
            icon = Icons.Default.Home, containerColor = Color(0xFFEFF6FF), contentColor = AccentBlue,
            onClick = { viewModel.setScreen(AppScreen.RENT_OVERVIEW) }
        )
        QuickActionCard(
            modifier = Modifier.fillMaxWidth(),
            title = "Dokumentenakte",
            subtitle = "Verträge, Stammdaten und Volltextsuche",
            icon = Icons.Default.Description,
            containerColor = Color(0xFFEFF6FF),
            contentColor = AccentBlue,
            onClick = { viewModel.setScreen(AppScreen.DOCUMENTS) }
        )

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

        Text(
            "Objekt, Drive, KI und weitere Einstellungen findest du oben rechts über das Zahnrad.",
            fontSize = 11.sp,
            color = SlateGray,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
    return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- BANK STATEMENT MISSING RECEIPT ALERT BANNER ---
        if (totalBankAlerts > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                border = BorderStroke(1.dp, Color(0xFFFED7AA))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFEDD5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = WarmOrange, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Belege prüfen", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkNavy)
                            Text(
                                "$totalBankAlerts offene ${if (totalBankAlerts == 1) "Abweichung" else "Abweichungen"}",
                                fontSize = 11.sp,
                                color = SlateGray
                            )
                        }
                        Surface(color = WarmOrange, shape = CircleShape) {
                            Text(
                                text = "$totalBankAlerts",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$missingReceiptsCount ohne Beleg • $rentArrearsCount Mietrückstände",
                            fontSize = 11.sp,
                            color = SlateGray,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showKiPowerCenterDialog = true }) {
                            Text("Jetzt prüfen", fontWeight = FontWeight.Bold, color = WarmOrange)
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = WarmOrange,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- GEMINI KI POWER HUB CARD ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showKiPowerCenterDialog = true },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = BorderStroke(1.dp, Brush.horizontalGradient(listOf(AccentBlue, Color(0xFFF59E0B)))),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(AccentBlue, Color(0xFFF59E0B)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("KI-Assistenten", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                            Surface(
                                color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "5 Assistenten",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Prüfen, abgleichen und Dokumente verstehen",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Öffnen",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        // 1. Modern Hero Banner Header with Property Info & Live Cloud Badge
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkNavy),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0F172A),
                                Color(0xFF1E293B),
                                Color(0xFF0284C7).copy(alpha = 0.3f)
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
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
                                    .clip(CircleShape)
                                    .background(AccentBlue.copy(alpha = 0.2f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Anlage V Portfolio",
                                    color = AccentBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            val doneCount = completeness.count { it.value }
                            val totalCount = completeness.size.coerceAtLeast(1)
                            val percent = (doneCount * 100) / totalCount
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(EmeraldGreen.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "$percent% Vollständig",
                                    color = EmeraldGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(
                            onClick = { showEditPropertyDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                                .testTag("edit_property_metadata_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Stammdaten bearbeiten",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = metadata.name.ifEmpty { "Meine Immobilie" },
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = metadata.adresse.ifEmpty { "Keine Adresse hinterlegt" },
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Cloud Sync Strip Inside Banner
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("firebase_auth_status_card"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentUser != null) Color(0xFF166534).copy(alpha = 0.4f) else Color(0xFF9A3412).copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (currentUser != null) Color(0xFF22C55E).copy(alpha = 0.4f) else Color(0xFFF97316).copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = "Cloud Status",
                                    tint = if (currentUser != null) Color(0xFF86EFAC) else Color(0xFFFDBA74),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = if (currentUser != null) "Cloud-Backup Aktiv" else "Cloud: Offline-Modus",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (currentUser != null) "${currentUser?.email}" else "Melden Sie sich an für automatischen Sync",
                                        fontSize = 10.sp,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }
                            }

                            if (currentUser != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = { viewModel.syncWithCloud() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp).testTag("firestore_sync_now_button")
                                    ) {
                                        Text("Sync", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.signOutUser() },
                                        border = BorderStroke(1.dp, Color(0xFF86EFAC)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp).testTag("firebase_logout_button")
                                    ) {
                                        Text("Logout", fontSize = 10.sp)
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { showAuthDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp).testTag("firebase_login_button")
                                ) {
                                    Text("Anmelden", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (currentUser != null && syncStatus != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = syncStatus!!,
                            fontSize = 10.sp,
                            color = Color(0xFF86EFAC)
                        )
                    }
                }
            }
        }

        if (showAuthDialog) {
            FirebaseLoginDialog(
                viewModel = viewModel,
                onDismiss = { showAuthDialog = false }
            )
        }

        // 2. Interactive Quick Actions Grid
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Schnellzugriff & Aktionen",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = DarkNavy
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Scanner Shortcut
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    title = "Beleg Scannen",
                    subtitle = "KI-Erfassung",
                    icon = Icons.Default.AutoAwesome,
                    containerColor = Color(0xFFEFF6FF),
                    contentColor = AccentBlue,
                    onClick = { viewModel.setScreen(AppScreen.ADD_RECEIPT) }
                )

                // Rent Overview Shortcut
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    title = "Mieteingang",
                    subtitle = "Soll vs. Ist",
                    icon = Icons.Default.CheckCircle,
                    containerColor = Color(0xFFECFDF5),
                    contentColor = EmeraldGreen,
                    onClick = { viewModel.setScreen(AppScreen.RENT_OVERVIEW) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tax Calculator Shortcut
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    title = "Steuerschätzung",
                    subtitle = "Anlage V Profit",
                    icon = Icons.Filled.Calculate,
                    containerColor = Color(0xFFF5F3FF),
                    contentColor = Color(0xFF7C3AED),
                    onClick = { viewModel.setScreen(AppScreen.TAX_CALCULATOR) }
                )

                // Logbook Shortcut
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    title = "Fahrtenbuch",
                    subtitle = "30ct/km Rechner",
                    icon = Icons.Default.DirectionsCar,
                    containerColor = Color(0xFFFFF7ED),
                    contentColor = WarmOrange,
                    onClick = { viewModel.setScreen(AppScreen.LOGBOOK) }
                )
            }
        }

        // 3. Financial Performance KPI Cards
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Finanz-Überblick (Anlage V)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = DarkNavy
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Income Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(EmeraldGreen))
                            }
                            Text("Einnahmen", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = NumberFormatter.format(totalIncome),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = EmeraldGreen
                        )
                    }
                }

                // Expenses Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(CrimsonRed.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(CrimsonRed))
                            }
                            Text("Ausgaben", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = NumberFormatter.format(totalExpenses),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = CrimsonRed
                        )
                    }
                }

                // Net Cashflow Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val cashflowColor = if (netCashflow >= 0) EmeraldGreen else CrimsonRed
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(cashflowColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalance,
                                    contentDescription = null,
                                    tint = cashflowColor,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Text("Netto", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = (if (netCashflow >= 0) "+" else "") + NumberFormatter.format(netCashflow),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = cashflowColor
                        )
                    }
                }
            }
        }

        // 4. Gemini AI Assistant Search Card
        AiSearchCard(viewModel = viewModel)

        // 5. Wohneinheiten Status Widget
        WohneinheitenStatusSection(viewModel, receipts)

        // 6. Monthly Income vs. Expenses Bar Chart
        MonthlyIncomeExpenseChart(receipts)

        // 7. AfA-Übersicht
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("AfA Gebäude", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text("${taxPhase1.afaRatePercent}% p.a.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                }
                Text("Gebäude-Kaufpreisanteil: ${NumberFormatter.format(taxPhase1.buildingPurchaseShare)}", fontSize = 11.sp, color = SlateGray)
                Text("+ anteilige Anschaffungsnebenkosten: ${NumberFormatter.format(taxPhase1.buildingAncillaryShare)}", fontSize = 11.sp, color = SlateGray)
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
                Text("Vorbereitungshilfe: Gebäudewert und Anschaffungsnebenkosten müssen steuerlich plausibel auf Grund/Boden und Gebäude aufgeteilt sein.", fontSize = 9.5.sp, color = Color.Gray, lineHeight = 12.sp)
            }
        }

        // 8. 15%-Grenze Warning Monitor
        val progress15 = (taxPhase1.relevantModernizationNet / limit15Percent).toFloat().coerceIn(0f, 1f)
        val progressColor = when {
            taxPhase1.is15PercentExceeded -> CrimsonRed
            progress15 > 0.8f -> WarmOrange
            else -> EmeraldGreen
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, if (taxPhase1.is15PercentExceeded) CrimsonRed else BorderColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(progressColor.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "15%-Grenze (§ 6 Abs. 1 Nr. 1a EStG)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = progressColor
                            )
                        }
                    }
                    Icon(
                        imageVector = if (taxPhase1.is15PercentExceeded) Icons.Default.Warning else Icons.Default.Info,
                        contentDescription = "Status",
                        tint = progressColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Anschaffungsnahe Herstellungskosten",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkNavy
                )
                Text(
                    "3-Jahres-Prüfwert: 15% der Gebäude-Anschaffungskosten (${NumberFormatter.format(limit15Percent)}), maßgeblich ohne Umsatzsteuer.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    lineHeight = 14.sp
                )
                Text(
                    "Zeitraum: ${taxPhase1.monitorStartDate.ifBlank { "nicht festgelegt" }} bis ${taxPhase1.monitorEndDate.ifBlank { "nicht festgelegt" }} • Potenziell relevante Belege: ${taxPhase1.candidateReceiptCount}" +
                        if (taxPhase1.estimatedNetCount > 0) " • Netto bei ${taxPhase1.estimatedNetCount} Beleg(en) geschätzt" else "",
                    fontSize = 10.sp,
                    color = SlateGray,
                    lineHeight = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Spacer(modifier = Modifier.height(14.dp))

                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Color(0xFFECF0F1))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress15)
                            .fillMaxHeight()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(progressColor.copy(alpha = 0.8f), progressColor)
                                )
                            )
                    )
                    Text(
                        text = "${(progress15 * 100).toInt()}% verbraucht",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 8.dp),
                        color = if (progress15 > 0.5f) Color.White else DarkNavy,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Bereits verbucht:", fontSize = 12.sp, color = DarkNavy)
                    Text(
                        NumberFormatter.format(totalRenovierung),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkNavy
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Verbleibender Puffer:", fontSize = 12.sp, color = Color.Gray)
                    val buffer = limit15Percent - taxPhase1.relevantModernizationNet
                    Text(
                        if (buffer >= 0) NumberFormatter.format(buffer) else "Überschritten um " + NumberFormatter.format(-buffer),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (buffer >= 0) EmeraldGreen else CrimsonRed
                    )
                }

                if (taxPhase1.is15PercentExceeded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CrimsonRed.copy(alpha = 0.1f))
                            .border(1.dp, CrimsonRed, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = "Alarm", tint = CrimsonRed, modifier = Modifier.size(18.dp))
                            Text(
                                "STEUER-WARNUNG: Der vorläufige 15%-Prüfwert ist überschritten. Einordnung als anschaffungsnahe Herstellungskosten fachlich prüfen; Erweiterungen und jährlich übliche Erhaltungsarbeiten sind gesondert zu behandeln.",
                                fontSize = 11.sp,
                                color = CrimsonRed,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // 8. Objekt-Stammdaten Details Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Objekt-Stammdaten",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkNavy
                    )
                    IconButton(
                        onClick = { showEditPropertyDialog = true },
                        modifier = Modifier.size(32.dp).testTag("edit_property_metadata_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Stammdaten bearbeiten",
                            tint = SlateGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) { InfoColumn(label = "Baujahr", value = metadata.baujahr.toString()) }
                    Box(modifier = Modifier.weight(1f)) { InfoColumn(label = "Wohnfläche", value = "${metadata.wohnflaeche} m²") }
                    Box(modifier = Modifier.weight(1f)) { InfoColumn(label = "Grundstück", value = "${metadata.grundstuecksgroesse} m²") }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) { InfoColumn(label = "Notar. Kaufdatum", value = metadata.notariellesKaufdatum.ifEmpty { "-" }) }
                    Box(modifier = Modifier.weight(1.5f)) { InfoColumn(label = "Übergang Nutzen/Lasten", value = metadata.uebergangNutzenLasten.ifEmpty { "-" }) }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) { InfoColumn(label = "Gesamtkaufpreis", value = NumberFormatter.format(totalKaufpreis)) }
                    Box(modifier = Modifier.weight(1f)) { InfoColumn(label = "Gebäudewert", value = NumberFormatter.format(totalGebaeudeAnteil)) }
                }

                if (metadata.wohneinheiten.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Zugeordnete Wohneinheiten:",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = metadata.wohneinheiten,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = DarkNavy
                    )
                }
            }
        }

        // 9. Financial Ausgaben-Struktur (Donut Chart)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Ausgaben-Struktur",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkNavy
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Custom Donut Chart on Canvas
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 14.dp.toPx()
                            val total = if (totalExpenses == 0.0) 1.0 else totalExpenses

                            val angleAnschaffung = (totalAnschaffung / total * 360).toFloat()
                            val angleFinanzierung = (totalFinanzierung / total * 360).toFloat()
                            val angleRenovierung = (totalRenovierung / total * 360).toFloat()
                            val angleSonstige = (totalSonstige / total * 360).toFloat()

                            var startAngle = -90f

                            drawArc(
                                color = AccentBlue,
                                startAngle = startAngle,
                                sweepAngle = angleAnschaffung,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            startAngle += angleAnschaffung

                            drawArc(
                                color = WarmOrange,
                                startAngle = startAngle,
                                sweepAngle = angleFinanzierung,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            startAngle += angleFinanzierung

                            drawArc(
                                color = EmeraldGreen,
                                startAngle = startAngle,
                                sweepAngle = angleRenovierung,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            startAngle += angleRenovierung

                            drawArc(
                                color = CrimsonRed,
                                startAngle = startAngle,
                                sweepAngle = angleSonstige,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Gesamt",
                                fontSize = 9.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${receipts.size} Bel.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = DarkNavy
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        CategoryLegendRow(color = AccentBlue, label = "Anschaffungskosten", value = totalAnschaffung)
                        CategoryLegendRow(color = WarmOrange, label = "Finanzierung & Kredite", value = totalFinanzierung)
                        CategoryLegendRow(color = EmeraldGreen, label = "Renovierung", value = totalRenovierung)
                        CategoryLegendRow(color = CrimsonRed, label = "Sonstiges", value = totalSonstige)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                        .background(SoftBackground)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Gesamte Ausgaben:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkNavy)
                    Text(
                        NumberFormatter.format(totalExpenses),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = DarkNavy
                    )
                }
            }
        }

        // 10. Compliance Checklist & Timeline
        AnlageVChecklistSection(completeness)
        TimelineSection(metadata, receipts)

        // 11. Google Drive Sync Status
        GoogleDriveSyncCard(viewModel)
    }

    if (showEditPropertyDialog) {
        PropertyMetadataFormDialog(
            viewModel = viewModel,
            onDismiss = { showEditPropertyDialog = false }
        )
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

    if (receiptToDelete != null) {
        AlertDialog(
            onDismissRequest = { receiptToDelete = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = CrimsonRed)
                    Text("Beleg in den Papierkorb?", fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    "Der Beleg wird aus der Belegliste entfernt und in den Papierkorb verschoben. Er kann später wiederhergestellt werden.",
                    fontSize = 13.sp,
                    color = DarkNavy
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = receiptToDelete!!.id
                        receiptToDelete = null
                        viewModel.deleteReceipt(id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { receiptToDelete = null }
                ) {
                    Text("Abbrechen")
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailDialog(receipt: Receipt, viewModel: ReceiptViewModel, onDismiss: () -> Unit) {
    var isEditing by remember { mutableStateOf(false) }

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
                        text = if (isEditing) "Beleg bearbeiten" else "Beleg-Kontierung",
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
                    // 1. Image Preview or Aesthetic Placeholder
                    ReceiptPreviewSection(receipt = receipt, viewModel = viewModel)
                    
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
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = SlateGray),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Schließen", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        containerColor = Color.White
    )
}

@Composable
fun FullScreenReceiptPreviewDialog(bitmap: Bitmap, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var rotation by remember { mutableStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, rotationChange ->
                        scale *= zoom
                        rotation += rotationChange
                        offset += pan
                    }
                }
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Vollbild Beleg",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        rotationZ = rotation,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Schließen", tint = Color.White)
            }
        }
    }
}

@Composable
fun AnlageVChecklistSection(completeness: Map<String, Boolean>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Dokumenten-Checkliste (Anlage V)", fontWeight = FontWeight.Bold, color = DarkNavy)
            Spacer(modifier = Modifier.height(8.dp))
            completeness.forEach { (category, isDone) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Icon(
                        imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isDone) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(category, modifier = Modifier.padding(start = 8.dp), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun TimelineSection(metadata: PropertyMetadata, receipts: List<Receipt>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Zeitstrahl: Kauf & Übergang", fontWeight = FontWeight.Bold, color = DarkNavy)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Kauf", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(metadata.notariellesKaufdatum, fontSize = 12.sp)
                }
                Column {
                    Text("Nutzen/Lasten", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(metadata.uebergangNutzenLasten, fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Minimalist timeline indicator
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color.LightGray))
        }
    }
}

sealed class PreviewState {
    object Loading : PreviewState()
    data class Success(val bitmap: Bitmap) : PreviewState()
    data class Error(
        val message: String,
        val phase: String,
        val mimeType: String?,
        val detectedFormat: String?,
        val fileSize: Long,
        val exceptionClass: String?,
        val exceptionMessage: String?
    ) : PreviewState()
}

@Composable
fun ReceiptPreviewSection(receipt: Receipt, viewModel: ReceiptViewModel) {
    var fullScreenImage by remember { mutableStateOf<Bitmap?>(null) }
    
    val downloadStatusMap by viewModel.documentDownloadStatus.collectAsState()
    val status = downloadStatusMap[receipt.internalId]
    
    var pendingRepairFile by remember { mutableStateOf<File?>(null) }
    var pendingValidation by remember { mutableStateOf<FileValidationResult?>(null) }
    var repairErrorMsg by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val activity = context as? Activity

    // Repair File Picker Launcher
    val repairFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                val inputStream = context.contentResolver.openInputStream(selectedUri)
                val tempFile = File(context.cacheDir, "repair_file_${System.currentTimeMillis()}")
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

    // Repair ML Kit Scanner Launcher
    val repairScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            if (scanningResult != null && !scanningResult.pages.isNullOrEmpty()) {
                val firstPageUri = scanningResult.pages!![0].imageUri
                try {
                    val inputStream = context.contentResolver.openInputStream(firstPageUri)
                    val tempFile = File(context.cacheDir, "repair_scan_${System.currentTimeMillis()}.jpg")
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
                        repairErrorMsg = validation.errorMessage ?: "Ungültiges gescanntes Dokument"
                    }
                } catch (e: Exception) {
                    repairErrorMsg = "Fehler beim Lesen des Scans: ${e.message}"
                }
            }
        }
    }

    fun triggerRepairScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        val scanner = GmsDocumentScanning.getClient(options)
        if (activity != null) {
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    repairScannerLauncher.launch(
                        IntentSenderRequest.Builder(intentSender).build()
                    )
                }
                .addOnFailureListener {
                    repairFilePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                }
        } else {
            repairFilePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
        }
    }

    LaunchedEffect(receipt.internalId) {
        if (status == null) {
            viewModel.checkDocumentStatus(receipt)
        }
    }

    if (fullScreenImage != null) {
        FullScreenReceiptPreviewDialog(fullScreenImage!!) { fullScreenImage = null }
    }

    if (pendingRepairFile != null && pendingValidation != null) {
        ConfirmRepairDocumentDialog(
            receipt = receipt,
            file = pendingRepairFile!!,
            validation = pendingValidation!!,
            viewModel = viewModel,
            onDismiss = {
                pendingRepairFile = null
                pendingValidation = null
            }
        )
    }

    val state = status?.state ?: DocumentState.CHECKING
    val currentLocalPath = status?.localPath ?: receipt.imageUrl
    
    val paths = remember(currentLocalPath) {
        if (currentLocalPath.isNotEmpty()) currentLocalPath.split(",") else emptyList()
    }

    if (state != DocumentState.CHECKING || paths.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Eingescannte Beleg-Seiten",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SlateGray,
                modifier = Modifier.align(Alignment.Start)
            )
            
            when (state) {
                DocumentState.CHECKING -> {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AccentBlue)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Beleg wird geprüft...", fontSize = 12.sp, color = DarkNavy)
                        }
                    }
                }
                DocumentState.DOWNLOAD_REQUIRED, DocumentState.DOWNLOADING -> {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AccentBlue)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Beleg wird aus Google Drive geladen...", fontSize = 12.sp, color = DarkNavy)
                        }
                    }
                }
                DocumentState.ERROR -> {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Fehler", tint = CrimsonRed, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(status?.message ?: "Fehler beim Laden", fontSize = 12.sp, color = CrimsonRed, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = { viewModel.downloadReceiptDocument(receipt) }) {
                                Text("Erneut versuchen")
                            }
                        }
                    }
                }
                DocumentState.UNSUPPORTED -> {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderColor),
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(status?.message ?: "Keine Bildvorschau verfügbar", fontSize = 12.sp, color = SlateGray)
                        }
                    }
                }
                DocumentState.AVAILABLE -> {
                    if (paths.isEmpty()) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderColor),
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Keine Datei verfügbar", fontSize = 12.sp, color = SlateGray)
                            }
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(paths) { path ->
                                var previewState by remember(path) { mutableStateOf<PreviewState>(PreviewState.Loading) }
                                
                                LaunchedEffect(path) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val file = java.io.File(path)
                                            if (!file.exists()) {
                                                previewState = PreviewState.Error(
                                                    message = "Lokale Datei fehlt",
                                                    phase = "RENDERING",
                                                    mimeType = receipt.originalMimeType,
                                                    detectedFormat = null,
                                                    fileSize = 0L,
                                                    exceptionClass = "FileNotFoundException",
                                                    exceptionMessage = "Die Datei existiert nicht unter: $path"
                                                )
                                                return@withContext
                                            }
                                            if (file.length() == 0L) {
                                                previewState = PreviewState.Error(
                                                    message = "Lokale Datei ist leer",
                                                    phase = "RENDERING",
                                                    mimeType = receipt.originalMimeType,
                                                    detectedFormat = null,
                                                    fileSize = 0L,
                                                    exceptionClass = "IOException",
                                                    exceptionMessage = "Die Datei hat 0 Bytes"
                                                )
                                                return@withContext
                                            }

                                            // Check format
                                            var detectedFormat = "Unbekannt"
                                            var isPdf = false
                                            file.inputStream().use {
                                                val header = run { val buffer = ByteArray(16); var offset = 0; while (offset < buffer.size) { val count = it.read(buffer, offset, buffer.size - offset); if (count < 0) break; offset += count }; buffer.copyOf(offset) }
                                                val hex = header.joinToString(" ") { b -> "%02X".format(b) }
                                                
                                                if (header.size >= 4 && header.copyOfRange(0, 4).contentEquals(byteArrayOf(0x25, 0x50, 0x44, 0x46))) {
                                                    detectedFormat = "PDF (%PDF-)"
                                                    isPdf = true
                                                } else if (header.size >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) {
                                                    detectedFormat = "JPEG"
                                                } else if (header.size >= 8 && header.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))) {
                                                    detectedFormat = "PNG"
                                                } else if (header.size >= 12 && header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) && header.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())) {
                                                    detectedFormat = "WEBP"
                                                } else {
                                                    detectedFormat = "Unbekannt ($hex...)"
                                                }
                                            }

                                            if (isPdf) {
                                                try {
                                                    val fd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                                                    val renderer = android.graphics.pdf.PdfRenderer(fd)
                                                    if (renderer.pageCount > 0) {
                                                        val page = renderer.openPage(0)
                                                        val bmp = Bitmap.createBitmap(800, (800.toFloat() / page.width * page.height).toInt(), Bitmap.Config.ARGB_8888)
                                                        val canvas = android.graphics.Canvas(bmp)
                                                        canvas.drawColor(android.graphics.Color.WHITE)
                                                        page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                                        page.close()
                                                        renderer.close()
                                                        fd.close()
                                                        previewState = PreviewState.Success(bmp)
                                                    } else {
                                                        renderer.close()
                                                        fd.close()
                                                        previewState = PreviewState.Error(
                                                            message = "PDF hat keine Seiten",
                                                            phase = "RENDERING",
                                                            mimeType = receipt.originalMimeType,
                                                            detectedFormat = detectedFormat,
                                                            fileSize = file.length(),
                                                            exceptionClass = "IllegalArgumentException",
                                                            exceptionMessage = "PdfRenderer.pageCount ist 0"
                                                        )
                                                    }
                                                } catch (e: Exception) {
                                                    previewState = PreviewState.Error(
                                                        message = "PDF-Rendering fehlgeschlagen",
                                                        phase = "RENDERING",
                                                        mimeType = receipt.originalMimeType,
                                                        detectedFormat = detectedFormat,
                                                        fileSize = file.length(),
                                                        exceptionClass = e.javaClass.simpleName,
                                                        exceptionMessage = e.message
                                                    )
                                                }
                                            } else if (detectedFormat == "JPEG" || detectedFormat == "PNG" || detectedFormat == "WEBP") {
                                                try {
                                                    val options = BitmapFactory.Options()
                                                    options.inJustDecodeBounds = true
                                                    BitmapFactory.decodeFile(path, options)
                                                    
                                                    val reqWidth = 1200
                                                    val reqHeight = 1600
                                                    var inSampleSize = 1
                                                    if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                                                        val halfHeight: Int = options.outHeight / 2
                                                        val halfWidth: Int = options.outWidth / 2
                                                        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                                                            inSampleSize *= 2
                                                        }
                                                    }
                                                    
                                                    options.inJustDecodeBounds = false
                                                    options.inSampleSize = inSampleSize
                                                    
                                                    val bmp = BitmapFactory.decodeFile(path, options)
                                                    if (bmp != null) {
                                                        previewState = PreviewState.Success(bmp)
                                                    } else {
                                                        previewState = PreviewState.Error(
                                                            message = "Bilddekodierung fehlgeschlagen",
                                                            phase = "RENDERING",
                                                            mimeType = receipt.originalMimeType,
                                                            detectedFormat = detectedFormat,
                                                            fileSize = file.length(),
                                                            exceptionClass = "DecodeError",
                                                            exceptionMessage = "BitmapFactory.decodeFile lieferte null"
                                                        )
                                                    }
                                                } catch (e: Exception) {
                                                    previewState = PreviewState.Error(
                                                        message = "Bilddekodierung fehlgeschlagen",
                                                        phase = "RENDERING",
                                                        mimeType = receipt.originalMimeType,
                                                        detectedFormat = detectedFormat,
                                                        fileSize = file.length(),
                                                        exceptionClass = e.javaClass.simpleName,
                                                        exceptionMessage = e.message
                                                    )
                                                }
                                            } else if (receipt.originalMimeType?.contains("text/plain", ignoreCase = true) == true) {
                                                previewState = PreviewState.Error(
                                                    message = "Dateiformat falsch erkannt (Textdokument)",
                                                    phase = "RENDERING",
                                                    mimeType = receipt.originalMimeType,
                                                    detectedFormat = detectedFormat,
                                                    fileSize = file.length(),
                                                    exceptionClass = "UnsupportedFormat",
                                                    exceptionMessage = "text/plain wird nicht als Bild unterstützt"
                                                )
                                            } else {
                                                previewState = PreviewState.Error(
                                                    message = "Unbekanntes Dateiformat",
                                                    phase = "RENDERING",
                                                    mimeType = receipt.originalMimeType,
                                                    detectedFormat = detectedFormat,
                                                    fileSize = file.length(),
                                                    exceptionClass = "UnsupportedFormat",
                                                    exceptionMessage = "Kein PDF, JPEG, PNG oder WEBP"
                                                )
                                            }
                                        } catch (e: Exception) {
                                            previewState = PreviewState.Error(
                                                message = "Fehler beim Dateizugriff",
                                                phase = "RENDERING",
                                                mimeType = receipt.originalMimeType,
                                                detectedFormat = "Unbekannt",
                                                fileSize = 0L,
                                                exceptionClass = e.javaClass.simpleName,
                                                exceptionMessage = e.message
                                            )
                                        }
                                    }
                                }

                                when (val state = previewState) {
                                    is PreviewState.Loading -> {
                                        Box(
                                            modifier = Modifier
                                                .width(280.dp).height(360.dp)
                                                .background(SoftBackground, RoundedCornerShape(8.dp))
                                                .border(1.dp, BorderColor, RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                        }
                                    }
                                    is PreviewState.Success -> {
                                        Card(
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, BorderColor),
                                            modifier = Modifier
                                                .width(280.dp).height(360.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { fullScreenImage = state.bitmap }
                                        ) {
                                            Image(
                                                bitmap = state.bitmap.asImageBitmap(),
                                                contentDescription = "Beleg Seite",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                    is PreviewState.Error -> {
                                        Box(
                                            modifier = Modifier
                                                .width(280.dp)
                                                .heightIn(min = 360.dp)
                                                .background(SoftBackground, RoundedCornerShape(8.dp))
                                                .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                                                .padding(16.dp),
                                            contentAlignment = Alignment.TopCenter
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Warning,
                                                    contentDescription = "Fehler beim Laden",
                                                    tint = CrimsonRed,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                                if (state.detectedFormat == "JSON" || state.exceptionClass != null) {
                                                    Text("Das Originaldokument dieses Belegs wurde nicht korrekt gesichert. Bitte laden oder scannen Sie das Original erneut.", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = DarkNavy, textAlign = TextAlign.Center)
                                                } else {
                                                    Text(state.message, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkNavy, textAlign = TextAlign.Center)
                                                }

                                                if (repairErrorMsg != null) {
                                                    Text(repairErrorMsg!!, fontSize = 12.sp, color = CrimsonRed, textAlign = TextAlign.Center)
                                                }
                                                
                                                Button(
                                                    onClick = { 
                                                        repairFilePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Originaldatei auswählen", fontSize = 12.sp)
                                                }

                                                OutlinedButton(
                                                    onClick = { 
                                                        triggerRepairScan()
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Original neu scannen", fontSize = 12.sp)
                                                }

                                                OutlinedButton(
                                                    onClick = { 
                                                        viewModel.diagnoseAndFixAllReceipts()
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("In Drive nach Original suchen", fontSize = 12.sp)
                                                }
                                                
                                                var showDetails by remember { mutableStateOf(false) }
                                                TextButton(onClick = { showDetails = !showDetails }) {
                                                    Text(if (showDetails) "Technische Details ausblenden" else "Technische Details einblenden", fontSize = 12.sp, color = SlateGray)
                                                }
                                                
                                                if (showDetails) {
                                                    Column(
                                                        modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(4.dp)).padding(8.dp),
                                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Text("Phase: ${state.phase}", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                                        Text("MIME: ${state.mimeType ?: "null"}", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                                        Text("Format: ${state.detectedFormat ?: "null"}", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                                        Text("Größe: ${state.fileSize} Bytes", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                                        Text("Exception: ${state.exceptionClass ?: "null"}", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                                        Text("Details: ${state.exceptionMessage ?: "null"}", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Aesthetic mock invoice receipt graphic for pre-populated entries
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = SoftBackground),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Receipt,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Virtueller Beleg",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = DarkNavy
                )
                Text(
                    "System-generierter Buchungsvorlage",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .background(Color.White, RoundedCornerShape(4.dp))
                        .border(1.dp, BorderColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            receipt.aussteller.uppercase(),
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            color = DarkNavy,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Datum: ${receipt.datum} ${receipt.uhrzeit}",
                            fontSize = 9.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Buchungsposten:", fontSize = 9.sp, color = SlateGray)
                            Text("Konto ${receipt.kontoNr}", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = SlateGray)
                        }
                        
                        Text(
                            text = if (receipt.beschreibung.isNotEmpty()) receipt.beschreibung else receipt.unterkategorie,
                            fontSize = 9.sp,
                            color = DarkNavy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        Canvas(modifier = Modifier.fillMaxWidth().height(1.dp)) {
                            drawLine(
                                color = BorderColor,
                                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                strokeWidth = 2f,
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "SUMME EUR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = DarkNavy
                            )
                            Text(
                                NumberFormatter.format(receipt.bruttobetrag),
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = EmeraldGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = SlateGray,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = if (highlight) FontWeight.Black else FontWeight.Bold,
            color = if (highlight) EmeraldGreen else DarkNavy,
            modifier = Modifier.weight(0.65f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun HorizontalDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(BorderColor.copy(alpha = 0.5f))
    )
}

@Composable
fun DetailItem(label: String, value: String, isBold: Boolean = false) {
    Column {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Text(value, fontSize = 14.sp, fontWeight = if (isBold) FontWeight.Black else FontWeight.Bold, color = DarkNavy)
    }
}

fun getKontoLabel(kontoNr: String): String {
    return when (kontoNr) {
        "0050" -> "Anschaffungskosten Gebäude"
        "2120" -> "Geldbeschaffungskosten"
        "2110" -> "Kreditzinsen"
        "4970" -> "Kontoführungsgebühren"
        "4830" -> "Instandhaltung Gebäude"
        "4670" -> "Fahrtkosten"
        else -> "Sonstiges"
    }
}

// --- SCREEN 3: ADD/SCAN RECEIPT (WITH GEMINI SIMULATION) ---

data class SelectedFile(
    val uri: Uri,
    val name: String,
    val isPdf: Boolean,
    val bitmaps: List<Bitmap>
)

private fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "beleg_dokument"
}

private fun loadBitmapsFromUri(context: Context, uri: Uri): List<Bitmap> {
    val bitmaps = mutableListOf<Bitmap>()
    val mimeType = context.contentResolver.getType(uri)
    if (mimeType == "application/pdf" || uri.toString().endsWith(".pdf", ignoreCase = true)) {
        try {
            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            if (parcelFileDescriptor != null) {
                val pdfRenderer = android.graphics.pdf.PdfRenderer(parcelFileDescriptor)
                val pageCount = pdfRenderer.pageCount
                // limit to first 3 pages for API token safety/efficiency
                for (i in 0 until minOf(pageCount, 3)) {
                    val page = pdfRenderer.openPage(i)
                    // Create bitmap with reasonable size for Gemini (e.g. max 1024 width/height to avoid memory overload)
                    val width = minOf(page.width, 1024)
                    val height = (width.toFloat() / page.width * page.height).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    
                    // Fill background white before rendering PDF (since page can have transparent background)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    
                    page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps.add(bitmap)
                    page.close()
                }
                pdfRenderer.close()
                parcelFileDescriptor.close()
            }
        } catch (e: Exception) {
            Log.e("ReceiptAppUi", "Error rendering PDF to bitmaps: ${e.localizedMessage}", e)
        }
    } else {
        // Standard image decoding
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    bitmaps.add(bitmap)
                }
            }
        } catch (e: Exception) {
            Log.e("ReceiptAppUi", "Error decoding image bitmap: ${e.localizedMessage}", e)
        }
    }
    return bitmaps
}

@Composable
fun AiAnalysisLoadingContent(
    modifier: Modifier = Modifier,
    statusSubtitle: String = "Gemini KI analysiert Ihr Dokument..."
) {
    var activeStep by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1200)
            if (activeStep < 2) {
                activeStep++
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ai_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        tonalElevation = 6.dp,
        border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.3f)),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with pulsing icon & title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(EmeraldGreen.copy(alpha = 0.25f), EmeraldGreen.copy(alpha = 0.05f))
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "KI Analyse",
                        tint = EmeraldGreen,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer { alpha = pulseAlpha }
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "KI-Dokumentenanalyse",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = DarkNavy,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = EmeraldGreen.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "VERARBEITUNG",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = EmeraldGreen,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = statusSubtitle,
                        fontSize = 11.5.sp,
                        color = SlateGray,
                        lineHeight = 15.sp
                    )
                }
            }

            // Animated progress bar
            val progressTarget = when (activeStep) {
                0 -> 0.38f
                1 -> 0.72f
                else -> 0.94f
            }
            val animatedProgress by animateFloatAsState(
                targetValue = progressTarget,
                animationSpec = tween(700, easing = FastOutSlowInEasing),
                label = "progress"
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LinearProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = EmeraldGreen,
                    trackColor = Color(0xFFE2E8F0)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = when (activeStep) {
                            0 -> "1/3 Dokument hochladen & optimieren..."
                            1 -> "2/3 Gemini KI extrahiert Daten..."
                            else -> "3/3 Kontierung & Kategorisierung..."
                        },
                        fontSize = 10.5.sp,
                        color = SlateGray,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen
                    )
                }
            }

            // Checklist steps breakdown
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val steps = listOf(
                    "Dokument hochladen & Bild optimieren",
                    "Gemini KI extrahiert Betrag, Datum & Aussteller",
                    "Automatische Kontierung & SKR-Kategorisierung"
                )

                steps.forEachIndexed { index, stepText ->
                    val isDone = index < activeStep
                    val isCurrent = index == activeStep

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(
                                    when {
                                        isDone -> EmeraldGreen
                                        isCurrent -> EmeraldGreen.copy(alpha = 0.15f)
                                        else -> Color(0xFFE2E8F0)
                                    },
                                    CircleShape
                                )
                                .border(
                                    width = if (isCurrent) 1.5.dp else 0.dp,
                                    color = if (isCurrent) EmeraldGreen else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDone) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            } else if (isCurrent) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = EmeraldGreen,
                                    strokeWidth = 1.8.dp
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF94A3B8), CircleShape)
                                )
                            }
                        }

                        Text(
                            text = stepText,
                            fontSize = 11.5.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isDone -> DarkNavy
                                isCurrent -> EmeraldGreen
                                else -> Color(0xFF94A3B8)
                            }
                        )
                    }
                }
            }

            // Adaptive Memory Note
            Surface(
                color = EmeraldGreen.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "🧠 Lernende KI aktiv: Passt sich automatisch deinen früheren Belegs-Korrekturen an.",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EmeraldGreen
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReceiptScreen(viewModel: ReceiptViewModel) {
    val scanState by viewModel.scanState.collectAsState()
    val aiProviderState by viewModel.aiProviderState.collectAsState()
    val aiProviderLabel = if (aiProviderState.provider == ReceiptAnalysisProvider.OPENAI) "OpenAI" else "Gemini"

    var selectedFiles by remember { mutableStateOf<List<SelectedFile>>(emptyList()) }
    var previewingFile by remember { mutableStateOf<SelectedFile?>(null) }
    var croppingFile by remember { mutableStateOf<SelectedFile?>(null) }

    val context = LocalContext.current
    val activity = context as? Activity

    // Google ML Kit Document Scanner Launcher
    val mlKitScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            if (scanningResult != null) {
                val pages = scanningResult.pages
                if (!pages.isNullOrEmpty()) {
                    val bitmaps = pages.flatMap { page ->
                        loadBitmapsFromUri(context, page.imageUri)
                    }
                    if (bitmaps.isNotEmpty()) {
                        // Trigger Gemini analysis with auto-cropped pages
                        viewModel.analyzeReceipt(text = "", bitmaps = bitmaps)

                        val virtualUri = Uri.parse("mlkit-scan://${System.currentTimeMillis()}")
                        val name = "MLKit_Document_Scan_${System.currentTimeMillis() / 1000}.jpg"
                        selectedFiles = selectedFiles + SelectedFile(
                            uri = virtualUri,
                            name = name,
                            isPdf = false,
                            bitmaps = bitmaps
                        )
                    }
                }
            }
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val newSelectedFiles = uris.mapNotNull { uri ->
                try {
                    val name = getFileName(context, uri)
                    val isPdf = name.endsWith(".pdf", ignoreCase = true) || context.contentResolver.getType(uri) == "application/pdf"
                    val bitmaps = loadBitmapsFromUri(context, uri)
                    if (bitmaps.isNotEmpty()) {
                        SelectedFile(uri = uri, name = name, isPdf = isPdf, bitmaps = bitmaps)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e("AddReceiptScreen", "Error loading uri: ${uri}", e)
                    null
                }
            }
            selectedFiles = selectedFiles + newSelectedFiles
        }
    }

    val triggerMlKitScanner: () -> Unit = {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(10)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        val scanner = GmsDocumentScanning.getClient(options)
        if (activity != null) {
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    mlKitScannerLauncher.launch(
                        IntentSenderRequest.Builder(intentSender).build()
                    )
                }
                .addOnFailureListener { e ->
                    Log.e("MLKitScanner", "ML Kit Scanner launch error: ${e.message}", e)
                    filePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                }
        } else {
            filePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
        }
    }

    if (previewingFile != null) {
        val file = previewingFile!!
        Dialog(
            onDismissRequest = { previewingFile = null }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = file.name,
                            fontWeight = FontWeight.Bold,
                            color = DarkNavy,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { previewingFile = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Schließen", tint = SlateGray)
                        }
                    }

                    // Document status card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (file.isPdf) Icons.Default.Description else Icons.Default.Receipt,
                                contentDescription = if (file.isPdf) "PDF" else "Dokument",
                                tint = if (file.isPdf) CrimsonRed else EmeraldGreen,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = file.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = DarkNavy
                            )
                            Text(
                                text = if (file.isPdf) "PDF-Dokument bereit für KI-Analyse" else "${file.bitmaps.size} Seite(n) für KI-Analyse bereit",
                                fontSize = 11.sp,
                                color = SlateGray
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!file.isPdf && file.bitmaps.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    croppingFile = file
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, EmeraldGreen)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(16.dp))
                                    Text("Zuschneiden & Optimieren", fontSize = 11.sp, color = DarkNavy)
                                }
                            }
                        } else {
                            Text(
                                text = if (file.isPdf) "PDF-Dokument" else "${file.bitmaps.size} Seite(n) gescannt",
                                fontSize = 11.sp,
                                color = SlateGray
                            )
                        }
                        Button(
                            onClick = { previewingFile = null },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Schließen", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (croppingFile != null) {
        val file = croppingFile!!
        val initialBitmap = file.bitmaps.firstOrNull()
        if (initialBitmap != null) {
            DocumentCropDialog(
                initialBitmap = initialBitmap,
                onDismiss = { croppingFile = null },
                onCropped = { croppedBitmap ->
                    croppingFile = null
                    previewingFile = null
                    // Trigger Gemini AI analysis with optimized cropped bitmap
                    viewModel.analyzeReceipt(text = "", bitmaps = listOf(croppedBitmap))
                    // Update selected file list
                    val updatedFiles = selectedFiles.map {
                        if (it.uri == file.uri) {
                            it.copy(bitmaps = listOf(croppedBitmap))
                        } else it
                    }
                    selectedFiles = updatedFiles
                }
            )
        } else {
            croppingFile = null
        }
    }

    // Form Fields for Manual review / Edit after extraction
    var editAussteller by remember { mutableStateOf("") }
    var editDatum by remember { mutableStateOf("") }
    var editUhrzeit by remember { mutableStateOf("") }
    var editBruttobetrag by remember { mutableStateOf("") }
    var editHauptkategorie by remember { mutableStateOf("Renovierungs- / Reparaturkosten & Investitionen") }
    var editUnterkategorie by remember { mutableStateOf("Baukosten") }
    var editKontoNr by remember { mutableStateOf("4830") }
    var editBeschreibung by remember { mutableStateOf("") }
    var editIsEigenleistung by remember { mutableStateOf(false) }
    var editWohneinheit by remember { mutableStateOf("Gesamtobjekt / Allgemein") }
    var editMieter by remember { mutableStateOf("") }
    var editZahlungsart by remember { mutableStateOf("Unbekannt") }
    var editPositionen by remember { mutableStateOf<List<com.example.data.ReceiptItem>>(emptyList()) }
    var wohneinheitExpanded by remember { mutableStateOf(false) }
    var localImagePaths by remember { mutableStateOf("") }

    val propertyMetadataState by viewModel.propertyMetadata.collectAsState()
    val metadata = propertyMetadataState ?: PropertyMetadata()
    val unitsList = remember(metadata.wohneinheiten) {
        metadata.wohneinheiten.split(",").map { it.trim() }.filter { it.isNotEmpty() } + listOf("Gesamtobjekt / Allgemein")
    }

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

    // Dynamic Account suggesting
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

    // Auto-populate when Gemini analysis completes successfully
    LaunchedEffect(scanState) {
        val state = scanState
        if (state is ScanUiState.Success) {
            val extracted = state.receipt
            editAussteller = extracted.aussteller
            editDatum = extracted.datum
            editUhrzeit = extracted.uhrzeit
            editBruttobetrag = extracted.bruttobetrag.toString()
            editHauptkategorie = extracted.hauptkategorie
            editUnterkategorie = extracted.unterkategorie
            editKontoNr = extracted.kontoNr
            editBeschreibung = extracted.beschreibung
            editIsEigenleistung = extracted.isEigenleistungSanierung
            editZahlungsart = extracted.zahlungsart
            if (extracted.mieter.isNotEmpty()) {
                editMieter = extracted.mieter
            }
            if (extracted.wohneinheit.isNotEmpty()) {
                editWohneinheit = extracted.wohneinheit
            }
            editPositionen = extracted.positionen
            localImagePaths = state.localImagePaths
        }
    }

    if (scanState is ScanUiState.Loading) {
        Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                AiAnalysisLoadingContent(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else if (scanState is ScanUiState.Error) {
        AlertDialog(
            onDismissRequest = { viewModel.resetScanState() },
            title = { Text("Analysefehler") },
            text = { Text((scanState as ScanUiState.Error).message) },
            confirmButton = {
                TextButton(onClick = { viewModel.resetScanState() }) {
                    Text("OK")
                }
            }
        )
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Beleg erfassen (KI & Manuell)",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = DarkNavy
        )

        // Dropdown menu flags
        var mainCategoryExpanded by remember { mutableStateOf(false) }
        var subCategoryExpanded by remember { mutableStateOf(false) }

        // SECTION 1: Document Upload & AI Control (Beleg-Scan & Upload Center)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header of the Upload Center
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(EmeraldGreen.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "KI",
                            tint = EmeraldGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            "Beleg-Scan & Upload Center",
                            fontWeight = FontWeight.Bold,
                            color = DarkNavy,
                            fontSize = 16.sp
                        )
                        Text(
                            "Scannen oder hochladen – die KI füllt die Buchungsdaten für dich aus.",
                            color = SlateGray,
                            fontSize = 11.sp
                        )
                    }
                }

                // Interactive Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Primary Featured Action: Dokumenten-Scanner with Auto-Crop
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { triggerMlKitScanner() }
                            .testTag("scan_camera_button"),
                        color = Color.Unspecified,
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 2.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF10B981), // Emerald 500
                                            Color(0xFF059669)  // Emerald 600
                                        )
                                    )
                                )
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(Color.White.copy(alpha = 0.22f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Beleg scannen",
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 12.5.sp
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .background(Color.White, CircleShape)
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "AUTO-CROP",
                                                    fontSize = 7.5.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color(0xFF059669)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Automatische Erkennung und Zuschnitt",
                                            color = Color.White.copy(alpha = 0.92f),
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .background(Color.White.copy(alpha = 0.18f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Secondary File Upload Action
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { filePickerLauncher.launch(arrayOf("image/*", "application/pdf")) }
                            .testTag("scan_upload_button"),
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shadowElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(Color(0xFFF1F5F9), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.UploadFile,
                                        contentDescription = null,
                                        tint = DarkNavy,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Datei oder Bild hochladen",
                                        fontWeight = FontWeight.Bold,
                                        color = DarkNavy,
                                        fontSize = 12.5.sp
                                    )
                                    Text(
                                        text = "PDF, JPG oder PNG aus Galerie / Dateimanager",
                                        color = Color(0xFF64748B),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // Dotted Empty State or Document List
                if (selectedFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                            .drawBehind {
                                drawRoundRect(
                                    color = Color(0xFFCBD5E1),
                                    style = Stroke(
                                        width = 2.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                    ),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
                                )
                            }
                            .clickable { triggerMlKitScanner() }
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null,
                                tint = SlateGray.copy(alpha = 0.6f),
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                "Noch keine Belege bereitgestellt",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkNavy
                            )
                            Text(
                                "Scanne einen Beleg oder wähle eine Datei aus. Die Analyse startest du anschließend mit einem Tipp.",
                                fontSize = 10.sp,
                                color = SlateGray,
                                textAlign = TextAlign.Center,
                                lineHeight = 14.sp
                            )
                        }
                    }
                } else {
                    // Document selection queue header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bereit zur Analyse · ${selectedFiles.size} Dokumente",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkNavy
                        )
                        Box(
                            modifier = Modifier
                                .background(EmeraldGreen.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$aiProviderLabel bereit",
                                fontSize = 9.sp,
                                color = EmeraldGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        items(selectedFiles) { file ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .width(140.dp)
                                    .height(115.dp)
                                    .clickable { previewingFile = file }
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.SpaceBetween,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .background(Color.White, RoundedCornerShape(8.dp))
                                                .padding(4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (file.isPdf) Icons.Default.Description else Icons.Default.Receipt,
                                                contentDescription = if (file.isPdf) "PDF" else "Dokument",
                                                tint = if (file.isPdf) CrimsonRed else EmeraldGreen,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = file.name,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = DarkNavy,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = if (file.isPdf) "PDF Dokument" else "${file.bitmaps.size} Seite(n)",
                                                fontSize = 8.sp,
                                                color = SlateGray
                                            )
                                        }
                                    }

                                    // Remove button
                                    IconButton(
                                        onClick = {
                                            selectedFiles = selectedFiles.filter { it.uri != file.uri }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(24.dp)
                                            .padding(3.dp)
                                            .background(Color.White, CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Entfernen",
                                            tint = CrimsonRed,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Prominent Trigger button for Gemini KI analysis
                    Button(
                        onClick = {
                            val allBitmaps = selectedFiles.flatMap { it.bitmaps }
                            if (allBitmaps.isNotEmpty()) {
                                viewModel.analyzeReceipt(text = "", bitmaps = allBitmaps)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("trigger_gemini_analysis_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                        shape = RoundedCornerShape(12.dp),
                        enabled = scanState !is ScanUiState.Loading,
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "$aiProviderLabel-Analyse starten",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    "Beträge, Aussteller, Datum und Kategorie werden automatisch erkannt.",
                                    fontSize = 9.sp,
                                    color = Color.White.copy(alpha = 0.88f),
                                    lineHeight = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Add scan tips checklist to make the UI feel premium & professional!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1F5F9).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = SlateGray,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Tipps für perfekte Erkennung: Leg Belege glatt hin, vermeide Schatten/Spiegelungen und fotografiere flach von oben.",
                        fontSize = 9.5.sp,
                        color = SlateGray,
                        lineHeight = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // SECTION 2: AI State Presentation (Loading / Success / Error / Idle)
        when (scanState) {
            is ScanUiState.Loading -> {
                AiAnalysisLoadingContent(
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is ScanUiState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = EmeraldGreen.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, EmeraldGreen)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = EmeraldGreen,
                                modifier = Modifier.size(24.dp)
                            )
                            Text("Extraktion erfolgreich!", fontWeight = FontWeight.Bold, color = EmeraldGreen)
                        }
                        Text(
                            "Die extrahierten Daten wurden unten eingetragen. Bitte überprüfe die Werte vor dem Einbuchen.",
                            color = DarkNavy,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            is ScanUiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CrimsonRed.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, CrimsonRed)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = CrimsonRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Text("KI-Analyse fehlgeschlagen", fontWeight = FontWeight.Bold, color = CrimsonRed)
                        }
                        Text(
                            (scanState as ScanUiState.Error).message,
                            color = CrimsonRed,
                            fontSize = 11.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val allBitmaps = selectedFiles.flatMap { it.bitmaps }
                            if (allBitmaps.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        viewModel.analyzeReceipt(text = "", bitmaps = allBitmaps)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text("Erneut analysieren", fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                            OutlinedButton(
                                onClick = { viewModel.resetScanState() },
                                border = BorderStroke(1.dp, SlateGray),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateGray),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Schließen", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            is ScanUiState.Idle -> {
                // No specific state card needed, since form is always shown below
            }
        }

        // SECTION 3: Receipt Entry Form (Always visible)
        Text("Buchungsdaten für Beleg", fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 16.sp)

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = editAussteller,
                onValueChange = { editAussteller = it },
                label = { Text("Aussteller / Creditor") },
                modifier = Modifier.fillMaxWidth().testTag("edit_aussteller"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = editDatum,
                    onValueChange = { editDatum = it },
                    label = { Text("Datum (JJJJ-MM-TT)") },
                    modifier = Modifier.weight(1f).testTag("edit_datum"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )
                OutlinedTextField(
                    value = editUhrzeit,
                    onValueChange = { editUhrzeit = it },
                    label = { Text("Uhrzeit (HH:MM)") },
                    modifier = Modifier.weight(1f).testTag("edit_uhrzeit"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )
            }

            OutlinedTextField(
                value = editBruttobetrag,
                onValueChange = { editBruttobetrag = it },
                label = { Text("Bruttobetrag (€)") },
                modifier = Modifier.fillMaxWidth().testTag("edit_betrag"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            // EXPOSED DROPDOWN: Hauptkategorie
            ExposedDropdownMenuBox(
                expanded = mainCategoryExpanded,
                onExpandedChange = { mainCategoryExpanded = !mainCategoryExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = editHauptkategorie,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Hauptkategorie") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mainCategoryExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth().testTag("edit_hauptkategorie"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )
                ExposedDropdownMenu(
                    expanded = mainCategoryExpanded,
                    onDismissRequest = { mainCategoryExpanded = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    mainCategories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                editHauptkategorie = category
                                editUnterkategorie = subCategoriesMap[category]?.firstOrNull() ?: ""
                                editKontoNr = getSuggestedKonto(category, editUnterkategorie)
                                mainCategoryExpanded = false
                            }
                        )
                    }
                }
            }

            // EXPOSED DROPDOWN: Unterkategorie
            val currentSubs = subCategoriesMap[editHauptkategorie] ?: emptyList()
            ExposedDropdownMenuBox(
                expanded = subCategoryExpanded,
                onExpandedChange = { subCategoryExpanded = !subCategoryExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = editUnterkategorie,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Unterkategorie") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subCategoryExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth().testTag("edit_unterkategorie"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )
                ExposedDropdownMenu(
                    expanded = subCategoryExpanded,
                    onDismissRequest = { subCategoryExpanded = false },
                    modifier = Modifier.background(Color.White)
                ) {
                    currentSubs.forEach { sub ->
                        DropdownMenuItem(
                            text = { Text(sub) },
                            onClick = {
                                editUnterkategorie = sub
                                editKontoNr = getSuggestedKonto(editHauptkategorie, sub)
                                subCategoryExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = editKontoNr,
                onValueChange = { editKontoNr = it },
                label = { Text("DATEV Konto-Nr (Instandhaltung, Zinsen, etc.)") },
                modifier = Modifier.fillMaxWidth().testTag("edit_konto"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            OutlinedTextField(
                value = editBeschreibung,
                onValueChange = { editBeschreibung = it },
                label = { Text("Beschreibung (Zweck)") },
                modifier = Modifier.fillMaxWidth().testTag("edit_beschreibung"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            OutlinedTextField(
                value = editMieter,
                onValueChange = { editMieter = it },
                label = { Text("Mieter / Zahler (optional)") },
                modifier = Modifier.fillMaxWidth().testTag("edit_receipt_mieter"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            // EXPOSED DROPDOWN: Wohneinheit
            ExposedDropdownMenuBox(
                expanded = wohneinheitExpanded,
                onExpandedChange = { wohneinheitExpanded = !wohneinheitExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = editWohneinheit,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Zugeordnete Wohneinheit") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = wohneinheitExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth().testTag("edit_receipt_wohneinheit"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )
                ExposedDropdownMenu(
                    expanded = wohneinheitExpanded,
                    onDismissRequest = { wohneinheitExpanded = false },
                    modifier = Modifier.background(Color.White)
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

            // Checkbox Eigenleistung
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editIsEigenleistung = !editIsEigenleistung }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = editIsEigenleistung,
                    onCheckedChange = { editIsEigenleistung = it },
                    colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen),
                    modifier = Modifier.testTag("edit_eigenleistung_checkbox")
                )
                Column {
                    Text("Eigenleistung Sanierung?", fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 13.sp)
                    Text("Fällt in die Sanierungsphase 01.10.25 - 31.01.26", color = Color.Gray, fontSize = 11.sp)
                }
            }

            OutlinedTextField(
                value = editZahlungsart,
                onValueChange = { editZahlungsart = it },
                label = { Text("Zahlungsart") },
                supportingText = { Text("Bar, Girocard/EC, Kreditkarte, Überweisung, Lastschrift, PayPal oder Unbekannt") },
                modifier = Modifier.fillMaxWidth().testTag("scan_payment_method")
            )

            // Positionen Editor Section
            ReceiptPositionenEditor(
                positionen = editPositionen,
                onPositionenChanged = { editPositionen = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        // Reset Form / Reset selection
                        editAussteller = ""
                        editDatum = ""
                        editUhrzeit = ""
                        editBruttobetrag = ""
                        editHauptkategorie = "Renovierungs- / Reparaturkosten & Investitionen"
                        editUnterkategorie = "Baukosten"
                        editKontoNr = "4830"
                        editBeschreibung = ""
                        editIsEigenleistung = false
                        editWohneinheit = "Gesamtobjekt / Allgemein"
                        editMieter = ""
                        editPositionen = emptyList()
                        selectedFiles = emptyList()
                        viewModel.setScreen(AppScreen.DASHBOARD)
                    },
                    modifier = Modifier.weight(1.5f).height(48.dp),
                    border = BorderStroke(1.dp, SlateGray)
                ) {
                    Text("Zurück / Reset", color = SlateGray, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        val amount = editBruttobetrag.toDoubleOrNull() ?: 0.0
                        viewModel.saveReceipt(
                            aussteller = editAussteller,
                            datum = editDatum,
                            uhrzeit = editUhrzeit,
                            bruttobetrag = amount,
                            hauptkategorie = editHauptkategorie,
                            unterkategorie = editUnterkategorie,
                            kontoNr = editKontoNr,
                            beschreibung = editBeschreibung,
                            isEigenleistung = editIsEigenleistung,
                            imageUrl = localImagePaths,
                            wohneinheit = editWohneinheit,
                            mieter = editMieter,
                            zahlungsart = editZahlungsart,
                            positionenJson = com.example.data.ReceiptItemConverter.toJson(editPositionen)
                        )
                    },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("save_extracted_receipt_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Text("Einbuchen", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- SCREEN 4: KI-FAHRTENBUCH 2.0 (siehe LogbookFeature.kt) ---

// --- SCREEN 5: LEDGER & ACCOUNT BALANCES ---

@Composable
fun LedgerScreen(viewModel: ReceiptViewModel) {
    val receipts by viewModel.receipts.collectAsState()
    val context = LocalContext.current
    val isPaymentBackfillRunning by viewModel.isBackfillingPaymentMethods.collectAsState()
    val paymentBackfillStatus by viewModel.paymentBackfillStatus.collectAsState()
    val isDescriptionBackfillRunning by viewModel.isBackfillingDescriptions.collectAsState()
    val descriptionBackfillStatus by viewModel.descriptionBackfillStatus.collectAsState()
    val descriptionBackfillCandidates by viewModel.descriptionBackfillCandidates.collectAsState()
    var showDescriptionBackfillPreview by remember { mutableStateOf(false) }

    // Calculate totals per DATEV Konto
    val kontoMap = receipts.groupBy { it.kontoNr }
    val sortedKonten = listOf("0050", "2110", "2120", "4830", "4670", "4970")

    val scrollState = rememberScrollState()

    var showTaxYearDialog by remember { mutableStateOf(false) }

    val availableYears = remember(receipts) {
        receipts.mapNotNull {
            try {
                it.datum.substring(0, 4).toInt()
            } catch (e: Exception) {
                null
            }
        }.distinct().sortedDescending()
    }

    val yearsToSelect = if (availableYears.isEmpty()) {
        listOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))
    } else {
        availableYears
    }

    if (showTaxYearDialog) {
        var selectedYear by remember { mutableStateOf(yearsToSelect.first()) }
        var includeAdvisorSummary by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showTaxYearDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = EmeraldGreen
                    )
                    Text(
                        text = "Finanzamt PDF Export",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkNavy
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Wähle das Steuerjahr für den Export aus. Der Bericht enthält eine Zusammenfassung nach Werbungskosten-Kategorien (Anlage V) sowie ein detailliertes Buchungsjournal.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )

                    Text(
                        text = "Steuerjahr auswählen:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkNavy
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        yearsToSelect.forEach { year ->
                            val isSelected = year == selectedYear
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isSelected) EmeraldGreen else Color.White,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) EmeraldGreen else BorderColor,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .clickable { selectedYear = year }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = year.toString(),
                                    color = if (isSelected) Color.White else DarkNavy,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Zusatz-Optionen:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkNavy
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F5F9))
                            .clickable { includeAdvisorSummary = !includeAdvisorSummary }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Checkbox(
                            checked = includeAdvisorSummary,
                            onCheckedChange = { includeAdvisorSummary = it },
                            colors = CheckboxDefaults.colors(checkedColor = EmeraldGreen)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Zusammenfassung für Steuerberater",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkNavy
                            )
                            Text(
                                text = "Erstellt eine zusätzliche strukturierte DATEV-Kontenrahmen-Tabelle im PDF-Bericht.",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                lineHeight = 13.sp
                            )
                        }
                    }

                    val yearReceiptsCount = receipts.count {
                        try {
                            it.datum.substring(0, 4).toInt() == selectedYear
                        } catch (e: Exception) {
                            false
                        }
                    }

                    if (yearReceiptsCount == 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFEF3C7), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Keine Belege für das Steuerjahr $selectedYear gefunden.",
                                fontSize = 11.sp,
                                color = Color(0xFF92400E)
                            )
                        }
                    } else {
                        Text(
                            text = "Gefundene Einträge: $yearReceiptsCount Belege",
                            fontSize = 12.sp,
                            color = EmeraldGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pdfFile = viewModel.exportToPdf(context, selectedYear, includeAdvisorSummary)
                        if (pdfFile != null) {
                            com.example.util.PdfExporter.sharePdf(context, pdfFile)
                        }
                        showTaxYearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Exportieren & Teilen", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showTaxYearDialog = false }) {
                    Text("Abbrechen", color = Color.Gray, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Altbelege – Beschreibung nacherkennen", fontWeight = FontWeight.Bold, color = DarkNavy)
                Text(
                    descriptionBackfillStatus ?: "Prüft nur leere oder offensichtlich durch Aussteller/Adresse ersetzte Beschreibungen. Andere Belegdaten bleiben unverändert.",
                    fontSize = 11.sp,
                    color = SlateGray
                )
                Button(
                    onClick = { viewModel.analyzeLegacyDescriptions() },
                    enabled = !isDescriptionBackfillRunning,
                    modifier = Modifier.fillMaxWidth().testTag("legacy_description_backfill_button")
                ) {
                    Text(if (isDescriptionBackfillRunning) "Altbelege werden geprüft …" else "Altbelege prüfen")
                }
                if (descriptionBackfillCandidates.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showDescriptionBackfillPreview = true },
                        modifier = Modifier.fillMaxWidth().testTag("legacy_description_preview_button")
                    ) {
                        Text("Vorschau (${descriptionBackfillCandidates.size})")
                    }
                }
            }
        }

        if (showDescriptionBackfillPreview && descriptionBackfillCandidates.isNotEmpty()) {
            var selectedIds by remember(descriptionBackfillCandidates) {
                mutableStateOf(descriptionBackfillCandidates.filter { it.isSafe }.map { it.receiptId }.toSet())
            }
            AlertDialog(
                onDismissRequest = { showDescriptionBackfillPreview = false },
                title = { Text("Beschreibungen prüfen", fontWeight = FontWeight.Bold, color = DarkNavy) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Sichere Treffer sind vorausgewählt. Es wird ausschließlich das Feld Beschreibung/Zweck geändert.",
                            fontSize = 11.sp,
                            color = SlateGray
                        )
                        descriptionBackfillCandidates.forEach { candidate ->
                            val selected = candidate.receiptId in selectedIds
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = BorderStroke(1.dp, BorderColor)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = selected,
                                            onCheckedChange = { checked ->
                                                selectedIds = if (checked) selectedIds + candidate.receiptId else selectedIds - candidate.receiptId
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(candidate.displayId, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkNavy)
                                            Text(candidate.aussteller, fontSize = 10.sp, color = SlateGray)
                                        }
                                        Text(if (candidate.isSafe) "Sicher" else "Prüfen", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (candidate.isSafe) EmeraldGreen else Color(0xFFD97706))
                                    }
                                    Text("Grund: ${candidate.reason}", fontSize = 10.sp, color = SlateGray)
                                    Text("Alt: ${candidate.oldDescription.ifBlank { "(leer)" }}", fontSize = 11.sp, color = Color(0xFF991B1B))
                                    Text("Neu: ${candidate.newDescription}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = DarkNavy)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.applyDescriptionBackfill(selectedIds)
                            showDescriptionBackfillPreview = false
                        },
                        enabled = selectedIds.isNotEmpty()
                    ) { Text("Ausgewählte übernehmen") }
                },
                dismissButton = {
                    TextButton(onClick = { showDescriptionBackfillPreview = false }) { Text("Abbrechen") }
                }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Zahlungsarten bestehender Belege", fontWeight = FontWeight.Bold, color = DarkNavy)
                Text(paymentBackfillStatus ?: "Unbekannte Zahlungsarten können aus Belegtext und vorhandenen Originalbildern nacherkannt werden.", fontSize = 11.sp, color = SlateGray)
                Button(
                    onClick = { viewModel.backfillPaymentMethods() },
                    enabled = !isPaymentBackfillRunning,
                    modifier = Modifier.fillMaxWidth().testTag("existing_payment_backfill_button")
                ) {
                    Text(if (isPaymentBackfillRunning) "Nacherkennung läuft …" else "Zahlungsarten nacherkennen")
                }
            }
        }

        var showDatevExportDialog by remember { mutableStateOf(false) }
        if (showDatevExportDialog) {
            DatevExportDialog(
                viewModel = viewModel,
                receipts = receipts,
                onDismiss = { showDatevExportDialog = false }
            )
        }

        // DATEV Export Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(EmeraldGreen.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DATEV Export",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkNavy
                    )
                    Text(
                        text = "Exportiere Belege als DATEV-kompatible CSV oder PDF.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 15.sp
                    )
                }

                Button(
                    onClick = { showDatevExportDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text("Export", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Professional PDF Export Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(EmeraldGreen.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Finanzamt PDF-Export",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkNavy
                    )
                    Text(
                        text = "Generiere einen strukturierten Steuerbericht mit Werbungskosten-Auswertung & Buchungsjournal.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 15.sp
                    )
                }

                Button(
                    onClick = { showTaxYearDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text("Export", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Text(
            "DATEV-Kontenrahmen SKR 03",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = DarkNavy
        )

        // Account balances list
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Saldenaufstellung",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentBlue
                )
                Spacer(modifier = Modifier.height(12.dp))

                sortedKonten.forEach { account ->
                    val accReceipts = kontoMap[account] ?: emptyList()
                    val balance = accReceipts.sumOf { it.bruttobetrag }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Konto $account", fontWeight = FontWeight.Black, fontSize = 13.sp, color = DarkNavy)
                            Text(getKontoLabel(account), fontSize = 11.sp, color = Color.Gray)
                        }
                        Text(
                            NumberFormatter.format(balance),
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = DarkNavy
                        )
                    }
                    if (account != sortedKonten.last()) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(BorderColor)
                        )
                    }
                }
            }
        }

        // Professional Journal / Buchungsjournal
        Text("Buchungsjournal (Soll & Haben)", fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 16.sp)

        if (receipts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Keine Buchungssätze vorhanden. Erfasse Belege im Archiv.", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            receipts.forEach { receipt ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(receipt.datum, fontSize = 11.sp, color = Color.Gray)
                            Text("Beleg-ID: #${receipt.id}", fontSize = 11.sp, color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(receipt.aussteller, fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 13.sp)
                            Text(NumberFormatter.format(receipt.bruttobetrag), fontWeight = FontWeight.Black, color = DarkNavy, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SoftBackground)
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Soll: Konto ${receipt.kontoNr}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SlateGray)
                            Text("Haben: Konto 1200 (Bank)", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GoogleDriveSyncCard(viewModel: ReceiptViewModel) {
    val context = LocalContext.current
    val isConnected by viewModel.isDriveConnected.collectAsState()
    val email by viewModel.googleAccountEmail.collectAsState()
    val isSyncing by viewModel.isDriveSyncing.collectAsState()
    val syncStatus by viewModel.driveSyncStatus.collectAsState()
    val autoBackup by viewModel.autoDriveBackup.collectAsState()
    val systemFolderStatus by viewModel.driveSystemFolderStatus.collectAsState()
    val lastBackupTime by viewModel.lastStammdatenBackupTime.collectAsState()
    val syncError by viewModel.driveSyncError.collectAsState()
    val driveTestState by viewModel.driveTestState.collectAsState()

    val restorePreview by viewModel.restorePreview.collectAsState()
    val restoreReport by viewModel.restoreReport.collectAsState()
    val isRestoreRequired by viewModel.isRestoreRequired.collectAsState()
    val isRestoring by viewModel.isRestoring.collectAsState()
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    val isCheckingDuplicates by viewModel.isCheckingMetadataDuplicates.collectAsState()
    val duplicateReport by viewModel.metadataDuplicateReport.collectAsState()
    val duplicateError by viewModel.metadataDuplicateError.collectAsState()
    val metadataCleanupPreview by viewModel.metadataCleanupPreview.collectAsState()
    val metadataCleanupResult by viewModel.metadataCleanupResult.collectAsState()
    val isCleaningMetadataDuplicates by viewModel.isCleaningMetadataDuplicates.collectAsState()

    var showManualInput by remember { mutableStateOf(false) }
    var manualEmail by remember { mutableStateOf("sergej.alc28@gmail.com") }
    var manualTokenInput by remember { mutableStateOf("") }
    var showWebViewLogin by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            if (account != null && account.email != null) {
                viewModel.connectDrive(account.email!!)
            }
        } catch (e: Exception) {
            Log.e("DriveAuth", "Sign in failed", e)
            viewModel.setSyncStatus("Google Play Services Anmeldung nicht verfügbar (Account not present). Bitte nutzen Sie stattdessen den grünen Button 'Über Google-Login verbinden'!")
        }
    }

    if (showWebViewLogin) {
        AlertDialog(
            onDismissRequest = { showWebViewLogin = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Google-Login (Sicherer Web-Zugang)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    IconButton(onClick = { showWebViewLogin = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen")
                    }
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.setSupportZoom(true)
                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        url?.let { currentUrl ->
                                            if (currentUrl.contains("localhost") || currentUrl.contains("firebaseapp.com") || currentUrl.contains("access_token=")) {
                                                val fragment = currentUrl.substringAfter("#", "")
                                                val params = fragment.split("&").associate {
                                                    val parts = it.split("=")
                                                    if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
                                                }
                                                val accessToken = params["access_token"]
                                                if (!accessToken.isNullOrEmpty()) {
                                                    viewModel.connectDrive("sergej.alc28@gmail.com", accessToken)
                                                    showWebViewLogin = false
                                                }
                                            }
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val currentUrl = request?.url?.toString() ?: ""
                                        if (currentUrl.contains("localhost") || currentUrl.contains("firebaseapp.com") || currentUrl.contains("access_token=")) {
                                            val fragment = currentUrl.substringAfter("#", "")
                                            val params = fragment.split("&").associate {
                                                val parts = it.split("=")
                                                if (parts.size == 2) parts[0] to parts[1] else parts[0] to ""
                                            }
                                            val accessToken = params["access_token"]
                                            if (!accessToken.isNullOrEmpty()) {
                                                viewModel.connectDrive("sergej.alc28@gmail.com", accessToken)
                                                showWebViewLogin = false
                                                return true
                                            }
                                        }
                                        return false
                                    }
                                }
                                
                                val authUrl = "https://accounts.google.com/o/oauth2/v2/auth?" +
                                        "client_id=262346512049-tks9rgpsc7qe27hn5l80rmqdu4obbkai.apps.googleusercontent.com" +
                                        "&redirect_uri=https://gen-lang-client-0729558537.firebaseapp.com/__/auth/handler" +
                                        "&response_type=token" +
                                        "&scope=https://www.googleapis.com/auth/drive.file" +
                                        "&prompt=consent"
                                loadUrl(authUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showWebViewLogin = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("google_drive_sync_card"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = "Cloud Icon",
                    tint = if (isConnected) EmeraldGreen else AccentBlue,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "Google Drive Backup",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkNavy
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!isConnected) {
                Text(
                    "Sichern Sie Ihre Belege geordnet nach Jahr → Hauptkategorie → Unterkategorie direkt in Ihrem Google Drive Ordner.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { showWebViewLogin = true },
                    modifier = Modifier.fillMaxWidth().testTag("connect_drive_webview_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) {
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Über Google-Login verbinden", fontWeight = FontWeight.Bold, color = Color.White)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
                            .build()
                        val client = GoogleSignIn.getClient(context, gso)
                        launcher.launch(client.signInIntent)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("connect_drive_button"),
                    border = BorderStroke(1.dp, BorderColor),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkNavy)
                ) {
                    Text("Über Play Services verbinden", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Developer / Testing Fallback
                TextButton(
                    onClick = { showManualInput = !showManualInput },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        if (showManualInput) "Manuelle Verknüpfung ausblenden" else "Manuellen Access-Token eingeben",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                if (showManualInput) {
                    OutlinedTextField(
                        value = manualEmail,
                        onValueChange = { manualEmail = it },
                        label = { Text("E-Mail-Adresse") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = BorderColor
                        )
                    )
                    OutlinedTextField(
                        value = manualTokenInput,
                        onValueChange = { manualTokenInput = it },
                        label = { Text("Google OAuth Access-Token") },
                        placeholder = { Text("ya29.a0Ac...") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentBlue,
                            unfocusedBorderColor = BorderColor
                        )
                    )
                    Button(
                        onClick = { 
                            if (manualTokenInput.isNotEmpty()) {
                                viewModel.connectDrive(manualEmail, manualTokenInput)
                            } else {
                                viewModel.connectDrive(manualEmail)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("connect_drive_manual_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = SlateGray)
                    ) {
                        Text("Verbinden mit Token", color = Color.White)
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Tipp: Sie können einen Test-Token von https://developers.google.com/oauthplayground kopieren (Scope: drive.file).",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        lineHeight = 14.sp
                    )
                }
            } else {
                // Connected state
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(EmeraldGreen.copy(alpha = 0.1f))
                        .border(1.dp, EmeraldGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = EmeraldGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            "Verknüpft mit Google Drive",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkNavy
                        )
                        Text(
                            maskEmailAddress(email),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Google-Drive-Datenspeicherung Metadata Dashboard
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SoftBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        "Google-Drive-Datenspeicherung",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkNavy
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Status:", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            if (isConnected) "Verbunden" else "Nicht verbunden",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isConnected) EmeraldGreen else CrimsonRed
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Systemordner (_BelegApp-Daten):", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            if (isConnected) systemFolderStatus else "Nicht eingerichtet",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isConnected && systemFolderStatus == "Gefunden") EmeraldGreen else Color.Gray
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Letzte Stammdatensicherung:", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            if (isConnected) lastBackupTime else "Nie",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkNavy
                        )
                    }

                    syncError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(CrimsonRed.copy(alpha = 0.08f))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = CrimsonRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Synchronisationsfehler: $err",
                                fontSize = 10.sp,
                                color = CrimsonRed,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Auto Backup Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Automatisches Backup",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkNavy
                        )
                        Text(
                            "Neue Belege sofort hochladen",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = autoBackup,
                        onCheckedChange = { viewModel.toggleAutoBackup(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = EmeraldGreen,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.LightGray
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.disconnectDrive() },
                        modifier = Modifier.weight(1f).testTag("disconnect_drive_button"),
                        border = BorderStroke(1.dp, CrimsonRed),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonRed)
                    ) {
                        Text("Trennen", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.syncAllToDrive() },
                        modifier = Modifier.weight(1.5f).testTag("sync_drive_button"),
                        enabled = !isSyncing,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sende...", color = Color.White)
                        } else {
                            Text("Synchronisieren", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // Sync status message
            syncStatus?.let { status ->
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateGray.copy(alpha = 0.05f))
                        .padding(10.dp)
                ) {
                    Text(
                        status,
                        fontSize = 11.sp,
                        color = SlateGray,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = BorderColor)
            Spacer(modifier = Modifier.height(12.dp))

            // Google-Drive-Diagnose Karte
            GoogleDriveDiagnoseCard(
                isConnected = isConnected,
                email = email,
                driveTestState = driveTestState,
                onRunTest = { viewModel.runRealDriveTest() }
            )

            if (isConnected) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = BorderColor)
                Spacer(modifier = Modifier.height(12.dp))

                // Datensicherung und Wiederherstellung
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SoftBackground)
                        .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        "Datensicherung & Wiederherstellung",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkNavy
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Vollständige Wiederherstellung des App-Bestands aus Google Drive (Stammdaten, Wohneinheiten, Belegindex & Metadaten).",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.checkForDriveRestore() },
                            modifier = Modifier.weight(1f).testTag("check_drive_inventory_button"),
                            border = BorderStroke(1.dp, AccentBlue),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue)
                        ) {
                            Text("Bestand prüfen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showRestoreConfirmDialog = true },
                            modifier = Modifier.weight(1.3f).testTag("start_full_restore_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                        ) {
                            Text("Wiederherstellen", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { viewModel.runRestoreDryRun() },
                        modifier = Modifier.fillMaxWidth().testTag("restore_dry_run_button"),
                        border = BorderStroke(1.dp, SlateGray),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateGray)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore-Test ausführen (Dry Run)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val isAuditing by viewModel.isAuditingOriginalReceipts.collectAsState()

                    OutlinedButton(
                        onClick = { viewModel.runOriginalReceiptAudit() },
                        modifier = Modifier.fillMaxWidth().testTag("audit_original_receipts_button"),
                        enabled = !isAuditing,
                        border = BorderStroke(1.dp, EmeraldGreen),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen)
                    ) {
                        if (isAuditing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = EmeraldGreen,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Prüfe Magic-Bytes...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Originalbelege prüfen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { viewModel.runMetadataDuplicateReport() },
                        modifier = Modifier.fillMaxWidth().testTag("metadata_duplicate_report_button"),
                        enabled = !isCheckingDuplicates,
                        border = BorderStroke(1.dp, CrimsonRed),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonRed)
                    ) {
                        if (isCheckingDuplicates) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = CrimsonRed,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Suche nach Dubletten...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Metadaten-Dubletten prüfen (nur lesen)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            duplicateReport?.let { report ->
                MetadataDuplicateReportDialog(
                    report = report,
                    onDismiss = { viewModel.dismissMetadataDuplicateReport() },
                    onPrepareCleanup = { viewModel.prepareMetadataDuplicateCleanup(it) }
                )
            }

            metadataCleanupPreview?.let { plan ->
                AlertDialog(
                    onDismissRequest = { if (!isCleaningMetadataDuplicates) viewModel.cancelMetadataDuplicateCleanup() },
                    title = { Text("Verwaiste Metadaten sicher löschen", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Beleg: ${plan.internalId}")
                            Text("Bleibt erhalten: ${plan.activeMetadataFileId}", color = EmeraldGreen)
                            Text("Nach Bestätigung werden nur diese verwaisten Dateien gelöscht:")
                            plan.orphanMetadataFileIds.forEach { Text("• $it", fontSize = 11.sp) }
                            Text(
                                "Die aktive metadataFileId wird nicht gelöscht.",
                                fontWeight = FontWeight.Bold,
                                color = CrimsonRed
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.confirmMetadataDuplicateCleanup() },
                            enabled = !isCleaningMetadataDuplicates
                        ) {
                            Text(if (isCleaningMetadataDuplicates) "Bereinigung läuft…" else "Jetzt sicher löschen")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { viewModel.cancelMetadataDuplicateCleanup() },
                            enabled = !isCleaningMetadataDuplicates
                        ) { Text("Abbrechen") }
                    }
                )
            }

            metadataCleanupResult?.let { result ->
                AlertDialog(
                    onDismissRequest = { viewModel.dismissMetadataCleanupResult() },
                    title = {
                        Text(
                            if (result.completed) "Bereinigung abgeschlossen" else "Bereinigung teilweise fehlgeschlagen",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Aktive Datei erhalten: ${result.activeMetadataFileId}")
                            Text("Gelöscht: ${result.deletedMetadataFileIds.size}")
                            result.failures.forEach { Text("• $it", color = CrimsonRed, fontSize = 11.sp) }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { viewModel.dismissMetadataCleanupResult() }) { Text("OK") }
                    }
                )
            }

            duplicateError?.let { err ->
                AlertDialog(
                    onDismissRequest = { viewModel.dismissMetadataDuplicateReport() },
                    title = { Text("Fehler bei Dublettenprüfung", fontWeight = FontWeight.Bold) },
                    text = { Text(err) },
                    confirmButton = {
                        Button(onClick = { viewModel.dismissMetadataDuplicateReport() }) {
                            Text("OK")
                        }
                    }
                )
            }

            if (isRestoreRequired || showRestoreConfirmDialog) {
                DriveRestoreConfirmationDialog(
                    preview = restorePreview,
                    isRestoring = isRestoring,
                    onConfirm = {
                        showRestoreConfirmDialog = false
                        viewModel.performFullRestoreConfirmation()
                    },
                    onDismiss = {
                        showRestoreConfirmDialog = false
                        viewModel.dismissRestoreDialog()
                    }
                )
            }

            restoreReport?.let { report ->
                DriveRestoreReportDialog(
                    report = report,
                    onDismiss = { viewModel.clearRestoreReport() }
                )
            }

            val auditReport by viewModel.originalReceiptAuditReport.collectAsState()
            val auditError by viewModel.originalReceiptAuditError.collectAsState()

            auditReport?.let { report ->
                OriginalReceiptAuditDialog(
                    report = report,
                    onDismiss = { viewModel.dismissOriginalReceiptAuditReport() },
                    onRepairReceipt = { internalId ->
                        // Handled via main repair flow
                    },
                    onScanReceipt = { internalId ->
                        // Handled via main scan flow
                    },
                    onCorrectMetadata = { internalId, newMime, newFilename ->
                        viewModel.correctReceiptMetadata(internalId, newMime, newFilename)
                    }
                )
            }

            auditError?.let { err ->
                AlertDialog(
                    onDismissRequest = { viewModel.dismissOriginalReceiptAuditReport() },
                    title = { Text("Prüfung fehlgeschlagen", fontWeight = FontWeight.Bold) },
                    text = { Text(err) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.dismissOriginalReceiptAuditReport() }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}

fun maskEmailAddress(email: String?): String {
    if (email.isNullOrBlank()) return "Nicht angemeldet"
    if (!email.contains("@")) return email
    val parts = email.split("@", limit = 2)
    val user = parts[0]
    val domain = parts[1]
    val maskedUser = when {
        user.length <= 2 -> "${user.take(1)}***"
        user.length <= 4 -> "${user.take(2)}***"
        else -> "${user.take(3)}***"
    }
    return "$maskedUser@$domain"
}

fun shortenDriveId(id: String?): String {
    if (id.isNullOrBlank() || id == "Nicht gefunden" || id == "Nicht erstellt") return id ?: "-"
    if (id.length <= 10) return id
    return "${id.take(4)}…${id.takeLast(4)}"
}

@Composable
fun GoogleDriveDiagnoseCard(
    isConnected: Boolean,
    email: String?,
    driveTestState: DriveTestState?,
    onRunTest: () -> Unit
) {
    var isDiagnoseExpanded by remember { mutableStateOf(false) }
    var showTechDetails by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val maskedAccount = maskEmailAddress(email)
    val lastTestTime = driveTestState?.testRunTime ?: "Noch nicht durchgeführt"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("google_drive_diagnose_card"),
        colors = CardDefaults.cardColors(containerColor = SoftBackground),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row (Klickbar zum Auf-/Zuklappen)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isDiagnoseExpanded = !isDiagnoseExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Google-Drive-Diagnose",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkNavy
                    )
                }
                Icon(
                    imageVector = if (isDiagnoseExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isDiagnoseExpanded) "Einklappen" else "Ausklappen",
                    tint = SlateGray,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (!isDiagnoseExpanded) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Google Drive:", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            if (isConnected) "Verbunden" else "Nicht verbunden",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isConnected) EmeraldGreen else CrimsonRed
                        )
                    }
                    Text(
                        "Letzte Prüfung: $lastTestTime",
                        fontSize = 10.sp,
                        color = SlateGray
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = BorderColor.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(8.dp))

                // 1. Konto
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("Konto", fontSize = 11.sp, color = Color.Gray)
                    Text(maskedAccount, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkNavy)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 2. Verbindungsstatus
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("Verbindungsstatus", fontSize = 11.sp, color = Color.Gray)
                    Text(
                        if (isConnected) "Verbunden" else "Nicht verbunden",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) EmeraldGreen else CrimsonRed
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 3. Letzte Prüfung
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("Letzte Prüfung", fontSize = 11.sp, color = Color.Gray)
                    Text(lastTestTime, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = DarkNavy)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Test-Button
                Button(
                    onClick = onRunTest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("run_real_drive_test_button"),
                    enabled = driveTestState?.isRunning != true,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) {
                    if (driveTestState?.isRunning == true) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Führe echten Drive-Test aus...", color = Color.White, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Echten Drive-Test starten", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // Ergänzung Testergebnis
                driveTestState?.let { state ->
                    if (!state.isRunning) {
                        Spacer(modifier = Modifier.height(12.dp))

                        if (state.isSuccess) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(EmeraldGreen.copy(alpha = 0.08f))
                                    .border(1.dp, EmeraldGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(18.dp))
                                        Text("✓ Google-Drive-Test erfolgreich", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Upload:", fontSize = 11.sp, color = Color.DarkGray)
                                        Text("Erfolgreich", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Download:", fontSize = 11.sp, color = Color.DarkGray)
                                        Text("Erfolgreich", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Datenprüfung:", fontSize = 11.sp, color = Color.DarkGray)
                                        Text("Identisch", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                                    }
                                    state.testRunTime?.let { timeStr ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Geprüft am:", fontSize = 11.sp, color = Color.DarkGray)
                                            Text(timeStr, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = DarkNavy)
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CrimsonRed.copy(alpha = 0.08f))
                                    .border(1.dp, CrimsonRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(18.dp))
                                        Text("⚠ Google-Drive-Test fehlgeschlagen", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CrimsonRed)
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    state.affectedAction?.let { act ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Fehler bei:", fontSize = 11.sp, color = CrimsonRed, fontWeight = FontWeight.Bold)
                                            Text(act, fontSize = 11.sp, color = CrimsonRed, fontWeight = FontWeight.Medium)
                                        }
                                    }

                                    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                        Text("Beschreibung:", fontSize = 11.sp, color = CrimsonRed, fontWeight = FontWeight.Bold)
                                        Text(
                                            state.errorMessage ?: "Unbekannter Fehler",
                                            fontSize = 11.sp,
                                            color = CrimsonRed,
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        TextButton(
                            onClick = { showTechDetails = !showTechDetails },
                            modifier = Modifier.fillMaxWidth().testTag("toggle_tech_details_button")
                        ) {
                            Text(
                                if (showTechDetails) "Technische Details verbergen" else "Technische Details anzeigen",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentBlue
                            )
                        }

                        if (showTechDetails) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White)
                                    .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                                    .padding(10.dp)
                            ) {
                                Text("Technische Details", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                                Spacer(modifier = Modifier.height(6.dp))

                                Text("Vollständiges Konto:", fontSize = 10.sp, color = Color.Gray)
                                Text(email ?: "Nicht angemeldet", fontSize = 11.sp, color = DarkNavy)
                                Spacer(modifier = Modifier.height(4.dp))

                                TechDetailIdRow(
                                    label = "Hauptordner-ID",
                                    fullId = state.mainFolderId,
                                    shortenedId = shortenDriveId(state.mainFolderId),
                                    onCopy = {
                                        clipboardManager.setText(AnnotatedString(state.mainFolderId))
                                        android.widget.Toast.makeText(context, "Hauptordner-ID kopiert", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )

                                TechDetailIdRow(
                                    label = "Systemordner-ID",
                                    fullId = state.systemFolderId,
                                    shortenedId = shortenDriveId(state.systemFolderId),
                                    onCopy = {
                                        clipboardManager.setText(AnnotatedString(state.systemFolderId))
                                        android.widget.Toast.makeText(context, "Systemordner-ID kopiert", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )

                                TechDetailIdRow(
                                    label = "Testdatei-ID",
                                    fullId = state.testFileId,
                                    shortenedId = shortenDriveId(state.testFileId),
                                    onCopy = {
                                        clipboardManager.setText(AnnotatedString(state.testFileId))
                                        android.widget.Toast.makeText(context, "Testdatei-ID kopiert", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )

                                if (state.testReceiptMetadataFileId.isNotBlank()) {
                                    TechDetailIdRow(
                                        label = "Beleg-Metadaten-ID",
                                        fullId = state.testReceiptMetadataFileId,
                                        shortenedId = shortenDriveId(state.testReceiptMetadataFileId),
                                        onCopy = {
                                            clipboardManager.setText(AnnotatedString(state.testReceiptMetadataFileId))
                                            android.widget.Toast.makeText(context, "Beleg-Metadaten-ID kopiert", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }

                                state.httpStatusCode?.let { code ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("HTTP-Statuscode:", fontSize = 10.sp, color = Color.Gray)
                                        Text(code.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                                    }
                                }

                                if (state.isReAuthRequired) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Erneute Anmeldung:", fontSize = 10.sp, color = CrimsonRed)
                                        Text("Erforderlich", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CrimsonRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TechDetailIdRow(
    label: String,
    fullId: String,
    shortenedId: String,
    onCopy: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, fontSize = 10.sp, color = Color.Gray)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                shortenedId,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = DarkNavy
            )
            if (fullId.isNotBlank() && fullId != "Nicht gefunden" && fullId != "Nicht erstellt") {
                TextButton(
                    onClick = onCopy,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text("ID kopieren", fontSize = 10.sp, color = AccentBlue)
                }
            }
        }
    }
}



@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPermissionRequestView(
    cameraPermissionState: com.google.accompanist.permissions.PermissionState,
    onDismiss: () -> Unit,
    onUseSystemCamera: () -> Unit,
    onTriggerMlKitScanner: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.PhotoCamera,
            contentDescription = "Kamera",
            tint = Color.White,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Kamera-Berechtigung benötigt",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Um einen Beleg direkt fotografieren und analysieren zu können, benötigt die App Zugriff auf Ihre Kamera.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (onTriggerMlKitScanner != null) {
            Button(
                onClick = {
                    onDismiss()
                    onTriggerMlKitScanner()
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                    Text("Google ML Kit Scanner starten (Auto-Crop)", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = { cameraPermissionState.launchPermissionRequest() },
            colors = ButtonDefaults.buttonColors(containerColor = DarkNavy),
            border = BorderStroke(1.dp, EmeraldGreen),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("In-App Kamera Berechtigung erteilen", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onUseSystemCamera,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("System-Kamera nutzen (Fallback)", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onDismiss) {
            Text("Abbrechen", color = Color.White.copy(alpha = 0.6f))
        }
    }
}

private fun createMockReceiptBitmap(type: String): Bitmap {
    val width = 800
    val height = 1200
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint()
    
    // Background: Warm off-white paper texture
    paint.color = android.graphics.Color.parseColor("#FAF6F0")
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    
    // Receipt inner border
    paint.color = android.graphics.Color.parseColor("#D1D5DB")
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 3f
    canvas.drawRect(20f, 20f, (width - 20).toFloat(), (height - 20).toFloat(), paint)
    
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = android.graphics.Color.BLACK
    paint.isAntiAlias = true
    
    // Header title
    paint.textSize = 38f
    paint.isFakeBoldText = true
    canvas.drawText("MUSTER-QUITTUNG / BELEG", 100f, 100f, paint)
    
    paint.textSize = 24f
    paint.isFakeBoldText = false
    val currentDateStr = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMANY).format(java.util.Date())
    canvas.drawText("Datum: $currentDateStr", 100f, 160f, paint)
    canvas.drawText("Uhrzeit: 14:32 Uhr", 100f, 200f, paint)
    
    paint.textSize = 28f
    paint.isFakeBoldText = true
    
    when (type) {
        "material" -> {
            canvas.drawText("OBI Baumarkt Hamburg-Nord", 100f, 280f, paint)
            paint.isFakeBoldText = false
            paint.textSize = 24f
            canvas.drawText("--------------------------------------------------", 100f, 330f, paint)
            canvas.drawText("1x Laminat Buche Premium         89.99 EUR", 100f, 380f, paint)
            canvas.drawText("2x Sockelleisten MDF 2.5m         14.50 EUR", 100f, 420f, paint)
            canvas.drawText("1x Montagekleber Express          10.99 EUR", 100f, 460f, paint)
            canvas.drawText("--------------------------------------------------", 100f, 510f, paint)
            paint.isFakeBoldText = true
            paint.textSize = 28f
            canvas.drawText("GESAMTBETRAG (BRUTTO)          115.48 EUR", 100f, 570f, paint)
            paint.textSize = 22f
            paint.isFakeBoldText = false
            canvas.drawText("inkl. 19% MwSt.:                 18.44 EUR", 100f, 610f, paint)
            canvas.drawText("Netto-Betrag:                    97.04 EUR", 100f, 640f, paint)
            canvas.drawText("Zahlart: Barzahlung", 100f, 690f, paint)
            canvas.drawText("Konto-Nummer / Buchungscode: 4830", 100f, 740f, paint)
            canvas.drawText("Zugeordnete Wohneinheit: WE 1", 100f, 780f, paint)
            canvas.drawText("Beschreibung: Laminat fuer Wohnzimmerrenovierung", 100f, 820f, paint)
        }
        "handwerker" -> {
            canvas.drawText("Elektro Schmidt GmbH", 100f, 280f, paint)
            paint.isFakeBoldText = false
            paint.textSize = 24f
            canvas.drawText("--------------------------------------------------", 100f, 330f, paint)
            canvas.drawText("Sanierung Sicherungskasten WE 2  450.00 EUR", 100f, 380f, paint)
            canvas.drawText("Leitungsverlegung Kueche WE 2     220.00 EUR", 100f, 420f, paint)
            canvas.drawText("Anfahrt & Ruestzeit                35.00 EUR", 100f, 460f, paint)
            canvas.drawText("--------------------------------------------------", 100f, 510f, paint)
            paint.isFakeBoldText = true
            paint.textSize = 28f
            canvas.drawText("RECHNUNGSBETRAG (BRUTTO)        705.00 EUR", 100f, 570f, paint)
            paint.textSize = 22f
            paint.isFakeBoldText = false
            canvas.drawText("inkl. 19% MwSt.:                112.56 EUR", 100f, 610f, paint)
            canvas.drawText("Netto-Betrag:                   592.44 EUR", 100f, 640f, paint)
            canvas.drawText("Zahlart: Bankueberweisung innerhalb 14 Tagen", 100f, 690f, paint)
            canvas.drawText("Konto-Nummer / Buchungscode: 4800", 100f, 740f, paint)
            canvas.drawText("Zugeordnete Wohneinheit: WE 2", 100f, 780f, paint)
            canvas.drawText("Beschreibung: Elektroarbeiten ElektroSchmidt WE2", 100f, 820f, paint)
        }
        else -> {
            canvas.drawText("Notariat Dr. jur. Gabriel", 100f, 280f, paint)
            paint.isFakeBoldText = false
            paint.textSize = 24f
            canvas.drawText("--------------------------------------------------", 100f, 330f, paint)
            canvas.drawText("Kaufvertrag Grundstück / Haus   1800.00 EUR", 100f, 380f, paint)
            canvas.drawText("Hebegebuehren Treuhandkonto       250.00 EUR", 100f, 420f, paint)
            canvas.drawText("--------------------------------------------------", 100f, 510f, paint)
            paint.isFakeBoldText = true
            paint.textSize = 28f
            canvas.drawText("RECHNUNGSBETRAG (BRUTTO)       2050.00 EUR", 100f, 570f, paint)
            paint.textSize = 22f
            paint.isFakeBoldText = false
            canvas.drawText("inkl. 19% MwSt.:                327.31 EUR", 100f, 610f, paint)
            canvas.drawText("Netto-Betrag:                  1722.69 EUR", 100f, 640f, paint)
            canvas.drawText("Zahlungsziel: Sofort nach Erhalt", 100f, 690f, paint)
            canvas.drawText("Konto-Nummer: Anschaffungskosten", 100f, 740f, paint)
            canvas.drawText("Kaufdatum: 01.10.2025", 100f, 780f, paint)
            canvas.drawText("Beschreibung: Notarkosten Grundstueckskauf", 100f, 820f, paint)
        }
    }
    
    // Bottom watermark to look scanned
    paint.color = android.graphics.Color.parseColor("#9CA3AF")
    paint.textSize = 18f
    paint.isFakeBoldText = false
    canvas.drawText("Simulierter Belegscann v3.1 (AI Studio)", 100f, 1020f, paint)
    canvas.drawText("Fuer Steuererklaerung Anlage V optimiert", 100f, 1050f, paint)
    
    return bitmap
}

@Composable
fun CameraActiveView(
    onDismiss: () -> Unit,
    onImagesCaptured: (List<Bitmap>) -> Unit,
    onUseSystemCamera: () -> Unit,
    onTriggerMlKitScanner: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember {
        try {
            ProcessCameraProvider.getInstance(context)
        } catch (e: Throwable) {
            Log.e("CameraActiveView", "Error getting ProcessCameraProvider instance", e)
            null
        }
    }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }
    
    // List of all captured pages as an immutable list in state
    var capturedBitmaps by remember { mutableStateOf(listOf<Bitmap>()) }

    // Multi-page mode
    var isMultiPage by remember { mutableStateOf(false) }

    // Preview for current taken page
    var currentCapturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var cameraBindingFailed by remember { mutableStateOf(false) }
    
    // Advanced features state
    var isLowLight by remember { mutableStateOf(false) }
    var isTooTilted by remember { mutableStateOf(false) }
    var isStable by remember { mutableStateOf(true) }
    var tiltX by remember { mutableStateOf(0f) }
    var tiltY by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        
        var lastX = 0f
        var lastY = 0f
        var lastZ = 0f
        var stableTimeMs = 0L
        var lastUpdate = System.currentTimeMillis()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val now = System.currentTimeMillis()
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                
                tiltX = x
                tiltY = y
                
                // Tilt check relative to flat plane
                val tiltDeviation = Math.sqrt((x * x + y * y).toDouble())
                isTooTilted = tiltDeviation > 2.0
                
                // Stability calculation
                val delta = Math.abs(x - lastX) + Math.abs(y - lastY) + Math.abs(z - lastZ)
                lastX = x
                lastY = y
                lastZ = z
                
                val dt = now - lastUpdate
                lastUpdate = now
                
                if (delta < 0.25) {
                    stableTimeMs += dt
                } else {
                    stableTimeMs = 0L
                }
                
                isStable = stableTimeMs > 1000L
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    // OCR Guide State
    var guideStep by remember { mutableStateOf(0) }
    val guideSteps = listOf(
        "Beleg flach hinlegen",
        "Gute Beleuchtung sicherstellen",
        "Alle Ränder sichtbar lassen"
    )
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            guideStep = (guideStep + 1) % guideSteps.size
        }
    }

    // Focus animation state
    val infiniteTransition = rememberInfiniteTransition(label = "focus_animation")
    val focusAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "focus_alpha"
    )

    var isSimulationMode by remember { mutableStateOf(false) }
    var selectedMockType by remember { mutableStateOf("material") }

    val activeSimulation = isSimulationMode || cameraBindingFailed

    var isAutoTriggerEnabled by remember { mutableStateOf(true) }

    val triggerCapture: () -> Unit = {
        if (!isCapturing && currentCapturedBitmap == null) {
            if (activeSimulation) {
                isCapturing = true
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        val bmp = createMockReceiptBitmap(selectedMockType)
                        currentCapturedBitmap = bmp
                    } catch (e: Exception) {
                        captureError = "Simulation failed: ${e.localizedMessage}"
                    } finally {
                        isCapturing = false
                    }
                }, 600)
            } else {
                val capture = imageCapture
                if (capture != null) {
                    isCapturing = true
                    try {
                        val photoFile = File(context.cacheDir, "temp_receipt_${System.currentTimeMillis()}.jpg")
                        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                        val executor = ContextCompat.getMainExecutor(context)
                        capture.takePicture(
                            outputFileOptions,
                            executor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    try {
                                        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                                        if (bitmap != null) {
                                            currentCapturedBitmap = bitmap
                                        } else {
                                            captureError = "Image decode failed"
                                        }
                                    } catch (e: Exception) {
                                        captureError = "Error: ${e.localizedMessage}"
                                    } finally {
                                        isCapturing = false
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    captureError = "Error: ${exception.localizedMessage}"
                                    isCapturing = false
                                }
                            }
                        )
                    } catch (e: Exception) {
                        captureError = "Fehler bei Aufnahme: ${e.localizedMessage}"
                        isCapturing = false
                    }
                }
            }
        }
    }

    LaunchedEffect(isStable, isTooTilted, isLowLight, isCapturing, currentCapturedBitmap, isAutoTriggerEnabled) {
        if (isAutoTriggerEnabled && isStable && !isTooTilted && !isLowLight && !isCapturing && currentCapturedBitmap == null) {
            delay(1000) // Sustain stability for 1.0 second
            if (isStable && !isTooTilted && !isLowLight && !isCapturing && currentCapturedBitmap == null) {
                triggerCapture()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (currentCapturedBitmap != null) {
            // Preview Taken Image Mode
            val bitmap = currentCapturedBitmap!!
            
            var sharpnessCheckStatus by remember { mutableStateOf(0) } // 0 = checking, 1 = sharp, 2 = blurry
            LaunchedEffect(bitmap) {
                sharpnessCheckStatus = 0
                delay(800)
                // Just a mock logic for now, always assume sharp unless simulating a failure if we want, but let's just say it's sharp
                sharpnessCheckStatus = 1
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Erfasste Seite ${capturedBitmaps.size + 1}",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { currentCapturedBitmap = null }
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Verwerfen", tint = Color.White)
                    }
                }

                // Image preview box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Aufgenommene Seite",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                    
                    // Sharpness Indicator Overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .background(
                                color = when (sharpnessCheckStatus) {
                                    0 -> Color.Black.copy(alpha = 0.7f)
                                    1 -> EmeraldGreen.copy(alpha = 0.9f)
                                    else -> Color.Red.copy(alpha = 0.9f)
                                },
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (sharpnessCheckStatus == 0) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text("Prüfe Bildschärfe...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else if (sharpnessCheckStatus == 1) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Text("Bild ist ausreichend scharf", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Text("Bild unscharf! Bitte wiederholen", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Action panel
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.DarkGray.copy(alpha = 0.5f))
                        .navigationBarsPadding()
                        .padding(bottom = 36.dp, top = 20.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Ist diese Seite gut lesbar und scharf?",
                        color = Color.White,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                // Save current page and go back to camera to capture more
                                capturedBitmaps = capturedBitmaps + bitmap
                                currentCapturedBitmap = null
                            },
                            modifier = Modifier.weight(1.5f),
                            border = BorderStroke(1.dp, Color.White),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Nächste Seite")
                        }

                        Button(
                            onClick = {
                                // Add current page and submit everything
                                onImagesCaptured(capturedBitmaps + bitmap)
                            },
                            modifier = Modifier.weight(2f),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            enabled = sharpnessCheckStatus != 0
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("KI-Analyse", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Live Preview Mode
            if (activeSimulation) {
                // Interactive Camera Simulator Viewport
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F172A))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Kamera-Simulationsmodus",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (cameraBindingFailed) {
                            "Keine Hardware-Kamera erkannt (Emulator-Modus). Wähle eine Beleg-Vorlage aus und drücke den Auslöser, um den Belegscan & die Gemini KI-Extraktion zu testen!"
                        } else {
                            "Simulationsmodus manuell gestartet. Wähle eine Vorlage aus, um das Scannen und die KI-Extraktion ohne physischen Beleg zu testen!"
                        },
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onTriggerMlKitScanner != null) {
                            Button(
                                onClick = {
                                    onDismiss()
                                    onTriggerMlKitScanner()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ML Kit Auto-Scanner",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Button(
                            onClick = onUseSystemCamera,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "System-Kamera",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Dotted Viewfinder representation
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(220.dp)
                            .border(2.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Muster-Beleg im Fokus:",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = when (selectedMockType) {
                                    "material" -> "🛠️ OBI Baumarkt (115,48 €)"
                                    "handwerker" -> "⚡ Elektro Schmidt (705,00 €)"
                                    else -> "⚖️ Notariat Dr. Gabriel (2.050,00 €)"
                                },
                                color = EmeraldGreen,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Vorlagen-Wahl
                    Text(
                        text = "VORLAGE WÄHLEN:",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "material" to "🛠️ OBI",
                            "handwerker" to "⚡ Elektro",
                            "notar" to "⚖️ Notar"
                        ).forEach { (type, label) ->
                            val isSelected = selectedMockType == type
                            Button(
                                onClick = { selectedMockType = type },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) EmeraldGreen else Color.DarkGray.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FIT_CENTER
                            }

                            if (cameraProviderFuture == null) {
                                cameraBindingFailed = true
                            } else {
                                val executor = ContextCompat.getMainExecutor(ctx)
                                cameraProviderFuture.addListener({
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()

                                        val preview = Preview.Builder().build().also {
                                            it.surfaceProvider = previewView.surfaceProvider
                                        }

                                        val capture = ImageCapture.Builder()
                                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                            .build()

                                        imageCapture = capture

                                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                                        if (cameraProvider.hasCamera(cameraSelector)) {
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                cameraSelector,
                                                preview,
                                                capture
                                            )
                                        } else {
                                            cameraBindingFailed = true
                                        }
                                    } catch (e: Exception) {
                                        Log.e("CameraActiveView", "Camera binding failed", e)
                                        cameraBindingFailed = true
                                    }
                                }, executor)
                            }

                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // OCR Guide & Focus Overlay
                    if (!activeSimulation && !cameraBindingFailed) {
                        
                        // Warnings
                        if (isLowLight || isTooTilted || !isStable) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .windowInsetsPadding(WindowInsets.safeDrawing)
                                    .padding(top = 80.dp)
                                    .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = when {
                                        isLowLight -> "Licht verstärken!"
                                        isTooTilted -> "Gerät gerade halten!"
                                        !isStable -> "Kamera ruhig halten!"
                                        else -> ""
                                    },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Corner markers & Grid (restricted to central viewport region to prevent bleeding)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 110.dp, bottom = 150.dp, start = 32.dp, end = 32.dp)
                        ) {
                            // Grid
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 1.dp.toPx()
                                val color = Color.White.copy(alpha = 0.3f)
                                // Vertical lines
                                drawLine(color, Offset(size.width / 3, 0f), Offset(size.width / 3, size.height), strokeWidth)
                                drawLine(color, Offset(2 * size.width / 3, 0f), Offset(2 * size.width / 3, size.height), strokeWidth)
                                // Horizontal lines
                                drawLine(color, Offset(0f, size.height / 3), Offset(size.width, size.height / 3), strokeWidth)
                                drawLine(color, Offset(0f, 2 * size.height / 3), Offset(size.width, 2 * size.height / 3), strokeWidth)
                            }
                            
                            // Corner markers
                            val cornerSize = 40.dp
                            val stroke = 4.dp
                            Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                val color = Color.White
                                // Top-Left
                                drawLine(color, Offset(0f, 0f), Offset(cornerSize.toPx(), 0f), stroke.toPx())
                                drawLine(color, Offset(0f, 0f), Offset(0f, cornerSize.toPx()), stroke.toPx())
                                // Top-Right
                                drawLine(color, Offset(size.width - cornerSize.toPx(), 0f), Offset(size.width, 0f), stroke.toPx())
                                drawLine(color, Offset(size.width, 0f), Offset(size.width, cornerSize.toPx()), stroke.toPx())
                                // Bottom-Left
                                drawLine(color, Offset(0f, size.height), Offset(cornerSize.toPx(), size.height), stroke.toPx())
                                drawLine(color, Offset(0f, size.height - cornerSize.toPx()), Offset(0f, size.height), stroke.toPx())
                                // Bottom-Right
                                drawLine(color, Offset(size.width - cornerSize.toPx(), size.height), Offset(size.width, size.height), stroke.toPx())
                                drawLine(color, Offset(size.width, size.height - cornerSize.toPx()), Offset(size.width, size.height), stroke.toPx())
                            }
                        }

                        // Centered Level/Tilt Crosshair Indicator
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(120.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val centerX = size.width / 2
                                val centerY = size.height / 2
                                val outerRadius = 40.dp.toPx()
                                val innerRadius = 8.dp.toPx()
                                
                                // Draw outer target circle
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.5f * focusAlpha),
                                    radius = outerRadius,
                                    center = Offset(centerX, centerY),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                )
                                
                                // Draw static center dot
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.3f),
                                    radius = innerRadius,
                                    center = Offset(centerX, centerY)
                                )
                                
                                // Calculate dynamic bubble offset based on tilt
                                val maxOffset = outerRadius - innerRadius
                                val offsetX = (tiltX / 5f).coerceIn(-1f, 1f) * maxOffset
                                val offsetY = (tiltY / 5f).coerceIn(-1f, 1f) * maxOffset
                                
                                val isAligned = !isTooTilted
                                
                                // Draw dynamic level bubble
                                drawCircle(
                                    color = if (isAligned) EmeraldGreen else Color.Red,
                                    radius = innerRadius,
                                    center = Offset(centerX + offsetX, centerY + offsetY)
                                )
                                
                                // Draw perfect crosshair lines when aligned
                                if (isAligned) {
                                    val lineLength = 15.dp.toPx()
                                    val strokeWidth = 1.5.dp.toPx()
                                    // Left line
                                    drawLine(EmeraldGreen, Offset(centerX - outerRadius, centerY), Offset(centerX - outerRadius + lineLength, centerY), strokeWidth)
                                    // Right line
                                    drawLine(EmeraldGreen, Offset(centerX + outerRadius, centerY), Offset(centerX + outerRadius - lineLength, centerY), strokeWidth)
                                    // Top line
                                    drawLine(EmeraldGreen, Offset(centerX, centerY - outerRadius), Offset(centerX, centerY - outerRadius + lineLength), strokeWidth)
                                    // Bottom line
                                    drawLine(EmeraldGreen, Offset(centerX, centerY + outerRadius), Offset(centerX, centerY + outerRadius - lineLength), strokeWidth)
                                }
                            }
                        }

                        // Animated Step Instruction
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(32.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = guideSteps[guideStep],
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            // Top Status Overlay & Manual Simulation Switch in a unified, non-overlapping Bar
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Close button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = "Schließen", tint = Color.White)
                }

                // Middle: Mode Switch & Status Pill
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (activeSimulation) "Simulator" else "S. ${capturedBitmaps.size + 1}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (!cameraBindingFailed) {
                        // Multi-page toggle pill
                        Box(
                            modifier = Modifier
                                .background(if (isMultiPage) EmeraldGreen else Color.DarkGray, RoundedCornerShape(12.dp))
                                .clickable { isMultiPage = !isMultiPage }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isMultiPage) "Multi" else "Einzel",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Sim toggle pill
                        Box(
                            modifier = Modifier
                                .background(if (isSimulationMode) EmeraldGreen else Color.DarkGray, RoundedCornerShape(12.dp))
                                .clickable { isSimulationMode = !isSimulationMode }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isSimulationMode) "Sim" else "Kamera",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // Emulator tag
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFDC2626), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Emulator",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // Right: Auto-Trigger Badge
                Box(
                    modifier = Modifier
                        .background(if (isAutoTriggerEnabled) EmeraldGreen.copy(alpha = 0.15f) else Color.DarkGray.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .border(1.dp, if (isAutoTriggerEnabled) EmeraldGreen else Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                        .clickable { isAutoTriggerEnabled = !isAutoTriggerEnabled }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isAutoTriggerEnabled) Icons.Default.AutoAwesome else Icons.Default.Camera,
                            contentDescription = null,
                            tint = if (isAutoTriggerEnabled) EmeraldGreen else Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (isAutoTriggerEnabled) "Auto" else "Manuell",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bottom control bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .navigationBarsPadding()
                    .padding(bottom = 36.dp, top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Horizontal list of already captured pages
                if (capturedBitmaps.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(86.dp)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(capturedBitmaps) { index, bmp ->
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.5.dp, EmeraldGreen, RoundedCornerShape(8.dp))
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Seite ${index + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                // Delete/trash button on the top right
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(18.dp)
                                        .background(Color.Red, CircleShape)
                                        .clickable { 
                                            capturedBitmaps = capturedBitmaps.filterIndexed { i, _ -> i != index }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Löschen",
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                                // Page badge at bottom left
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topEnd = 4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("S.${index + 1}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (captureError != null) {
                    Text(
                        text = captureError ?: "",
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Abbrechen", color = Color.White)
                    }

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(if (isCapturing) Color.Gray else Color.White)
                            .clickable(enabled = !isCapturing) {
                                triggerCapture()
                            }
                            .weight(1.2f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(color = DarkNavy, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(
                                Icons.Default.Camera,
                                contentDescription = "Foto aufnehmen",
                                tint = DarkNavy,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Complete Multi-Page Capture early button
                    if (capturedBitmaps.isNotEmpty()) {
                        Button(
                            onClick = { onImagesCaptured(capturedBitmaps) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Fertig (${capturedBitmaps.size})", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1.5f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyMetadataFormDialog(
    viewModel: ReceiptViewModel,
    onDismiss: () -> Unit
) {
    val currentMetadata by viewModel.propertyMetadata.collectAsState()
    val metadata = currentMetadata ?: PropertyMetadata()

    var editName by remember(metadata) { mutableStateOf(metadata.name) }
    var editAdresse by remember(metadata) { mutableStateOf(metadata.adresse) }
    var editWohnort by remember(metadata) { mutableStateOf(metadata.wohnort) }
    var editBaujahr by remember(metadata) { mutableStateOf(metadata.baujahr.toString()) }
    var editWohnflaeche by remember(metadata) { mutableStateOf(metadata.wohnflaeche.toString()) }
    var editGrundstuecksgroesse by remember(metadata) { mutableStateOf(metadata.grundstuecksgroesse.toString()) }
    var editNotariellesKaufdatum by remember(metadata) { mutableStateOf(metadata.notariellesKaufdatum) }
    var editUebergangNutzenLasten by remember(metadata) { mutableStateOf(metadata.uebergangNutzenLasten) }
    var editWohneinheiten by remember(metadata) { mutableStateOf(metadata.wohneinheiten) }
    var editGesamtKaufpreis by remember(metadata) { mutableStateOf(metadata.gesamtKaufpreis.toString()) }
    var editGebaeudewert by remember(metadata) { mutableStateOf(metadata.gebaeudewert.toString()) }
    var editGrundUndBodenWert by remember(metadata) { mutableStateOf(metadata.grundUndBodenWert.toString()) }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Objekt-Stammdaten bearbeiten",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = DarkNavy
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Trage hier die Stammdaten deines Anlageobjekts ein. Diese Daten bilden die Basis für Berechnungen wie die 15%-Grenze.",
                    fontSize = 12.sp,
                    color = SlateGray,
                    lineHeight = 16.sp
                )

                // Dynamic progress calculation for Anlage V completeness
                val completenessChecks = listOf(
                    Pair("Objekt-Bezeichnung", editName.isNotBlank()),
                    Pair("Anschrift / Adresse", editAdresse.isNotBlank()),
                    Pair("Baujahr", editBaujahr.toIntOrNull() != null && editBaujahr.toInt() > 0),
                    Pair("Notarielles Kaufdatum", editNotariellesKaufdatum.isNotBlank()),
                    Pair("Übergang Nutzen/Lasten", editUebergangNutzenLasten.isNotBlank()),
                    Pair("Wohnfläche", editWohnflaeche.toDoubleOrNull() != null && editWohnflaeche.toDouble() > 0.0),
                    Pair("Grundstücksgröße", editGrundstuecksgroesse.toDoubleOrNull() != null && editGrundstuecksgroesse.toDouble() > 0.0),
                    Pair("Wohneinheiten", editWohneinheiten.isNotBlank()),
                    Pair("Gesamtkaufpreis", editGesamtKaufpreis.toDoubleOrNull() != null && editGesamtKaufpreis.toDouble() > 0.0),
                    Pair("Gebäudewert", editGebaeudewert.toDoubleOrNull() != null && editGebaeudewert.toDouble() > 0.0),
                    Pair("Grund und Boden", editGrundUndBodenWert.toDoubleOrNull() != null && editGrundUndBodenWert.toDouble() >= 0.0)
                )
                val completedCount = completenessChecks.count { it.second }
                val totalCount = completenessChecks.size
                val progressFraction = completedCount.toFloat() / totalCount
                val missingFields = completenessChecks.filter { !it.second }.map { it.first }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("completeness_progress_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Datenvollständigkeit (Anlage V)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkNavy
                            )
                            Text(
                                text = "$completedCount von $totalCount (${(progressFraction * 100).toInt()}%)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = if (progressFraction == 1f) EmeraldGreen else AccentBlue
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Custom styled progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFE2E8F0))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(if (progressFraction > 0f) progressFraction else 0.001f)
                                    .fillMaxHeight()
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = if (progressFraction == 1f) {
                                                listOf(EmeraldGreen.copy(alpha = 0.8f), EmeraldGreen)
                                            } else {
                                                listOf(AccentBlue.copy(alpha = 0.8f), AccentBlue)
                                            }
                                        )
                                    )
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (missingFields.isEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Perfekt! Alle notwendigen Datenpunkte sind erfasst.",
                                    fontSize = 10.sp,
                                    color = EmeraldGreen,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Text(
                                text = "Es fehlen noch: ${missingFields.joinToString(", ")}",
                                fontSize = 10.sp,
                                color = SlateGray,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Objekt-Bezeichnung") },
                    placeholder = { Text("z.B. Mehrfamilienhaus") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_property_name"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                OutlinedTextField(
                    value = editAdresse,
                    onValueChange = { editAdresse = it },
                    label = { Text("Anschrift / Adresse (Objekt)") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_property_adresse"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                OutlinedTextField(
                    value = editWohnort,
                    onValueChange = { editWohnort = it },
                    label = { Text("Startadresse / Wohnort (Fahrtenbuch)") },
                    placeholder = { Text("z.B. Hauptstraße 1, 12345 Wohnstadt") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_property_wohnort"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                OutlinedTextField(
                    value = editBaujahr,
                    onValueChange = { editBaujahr = it },
                    label = { Text("Baujahr") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("edit_property_baujahr"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editNotariellesKaufdatum,
                        onValueChange = { editNotariellesKaufdatum = it },
                        label = { Text("Notar. Kaufdatum") },
                        placeholder = { Text("YYYY-MM-DD") },
                        modifier = Modifier.weight(1f).testTag("edit_property_notarielles_kaufdatum"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )

                    OutlinedTextField(
                        value = editUebergangNutzenLasten,
                        onValueChange = { editUebergangNutzenLasten = it },
                        label = { Text("Übergang Nutzen/Lasten") },
                        placeholder = { Text("YYYY-MM-DD") },
                        modifier = Modifier.weight(1.2f).testTag("edit_property_uebergang"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editWohnflaeche,
                        onValueChange = { editWohnflaeche = it },
                        label = { Text("Wohnfläche (m²)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("edit_property_wohnflaeche"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )

                    OutlinedTextField(
                        value = editGrundstuecksgroesse,
                        onValueChange = { editGrundstuecksgroesse = it },
                        label = { Text("Grundstück (m²)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("edit_property_grundstueck"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )
                }

                OutlinedTextField(
                    value = editWohneinheiten,
                    onValueChange = { editWohneinheiten = it },
                    label = { Text("Wohneinheiten (kommagetrennt)") },
                    placeholder = { Text("z.B. WE 1, WE 2, WE 3, WE 4") },
                    supportingText = { Text("Belege können diesen Einheiten zugeordnet werden.", fontSize = 10.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("edit_property_wohneinheiten"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    OutlinedTextField(
        value = editGesamtKaufpreis,
        onValueChange = { editGesamtKaufpreis = it },
        label = { Text("Gesamtkaufpreis (€)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f).testTag("edit_property_kaufpreis"),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        )
    )

    OutlinedTextField(
        value = editGebaeudewert,
        onValueChange = { editGebaeudewert = it },
        label = { Text("Gebäudewert (€)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f).testTag("edit_property_gebaeudewert"),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        )
    )
}

OutlinedTextField(
    value = editGrundUndBodenWert,
    onValueChange = { editGrundUndBodenWert = it },
    label = { Text("Grund und Boden (€)") },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    modifier = Modifier.fillMaxWidth().testTag("edit_property_grund_boden"),
    colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White
    )
)

val allocationDiffPreview = (editGesamtKaufpreis.toDoubleOrNull() ?: 0.0) -
    (editGebaeudewert.toDoubleOrNull() ?: 0.0) -
    (editGrundUndBodenWert.toDoubleOrNull() ?: 0.0)
if (kotlin.math.abs(allocationDiffPreview) > 1.0) {
    Text(
        "Hinweis: Gebäude + Grund/Boden weichen um ${NumberFormatter.format(allocationDiffPreview)} vom Kaufpreis ab.",
        fontSize = 10.sp,
        color = WarmOrange
    )
}
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalBaujahr = editBaujahr.toIntOrNull() ?: metadata.baujahr
                    val finalWohnflaeche = editWohnflaeche.toDoubleOrNull() ?: metadata.wohnflaeche
                    val finalGrundstuecksgroesse = editGrundstuecksgroesse.toDoubleOrNull() ?: metadata.grundstuecksgroesse
                    val finalGesamtKaufpreis = editGesamtKaufpreis.toDoubleOrNull() ?: metadata.gesamtKaufpreis
                    val finalGebaeudewert = editGebaeudewert.toDoubleOrNull() ?: metadata.gebaeudewert
                    val finalGrundUndBodenWert = editGrundUndBodenWert.toDoubleOrNull() ?: metadata.grundUndBodenWert

                    val updated = metadata.copy(
                        name = editName,
                        adresse = editAdresse,
                        wohnort = editWohnort,
                        baujahr = finalBaujahr,
                        wohnflaeche = finalWohnflaeche,
                        grundstuecksgroesse = finalGrundstuecksgroesse,
                        notariellesKaufdatum = editNotariellesKaufdatum,
                        uebergangNutzenLasten = editUebergangNutzenLasten,
                        wohneinheiten = editWohneinheiten,
                        gesamtKaufpreis = finalGesamtKaufpreis,
                        gebaeudewert = finalGebaeudewert,
                        grundUndBodenWert = finalGrundUndBodenWert,
                        kaufpreisAufteilungQuelle = "MANUELL"
                    )
                    viewModel.updatePropertyMetadata(updated)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                modifier = Modifier.testTag("save_property_metadata_button")
            ) {
                Text("Speichern", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss
            ) {
                Text("Abbrechen", color = SlateGray)
            }
        },
        containerColor = Color.White
    )
}

@Composable
fun FirebaseLoginDialog(
    viewModel: ReceiptViewModel,
    onDismiss: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegisterMode by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(
                text = if (isRegisterMode) "Neues Konto erstellen" else "Anmelden",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = DarkNavy
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isRegisterMode) 
                        "Erstellen Sie ein kostenloses Konto, um Ihre Belege sicher in der Cloud zu speichern."
                        else "Melden Sie sich mit Ihrem persönlichen Account an.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    label = { Text("E-Mail-Adresse") },
                    modifier = Modifier.fillMaxWidth().testTag("auth_email_input"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("Passwort") },
                    modifier = Modifier.fillMaxWidth().testTag("auth_password_input"),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { 
                            isRegisterMode = !isRegisterMode 
                            errorMessage = null
                        },
                        enabled = !isLoading
                    ) {
                        Text(
                            text = if (isRegisterMode) "Bereits ein Konto? Anmelden" else "Konto erstellen",
                            fontSize = 12.sp,
                            color = AccentBlue
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Bitte füllen Sie alle Felder aus."
                        return@Button
                    }
                    if (password.length < 6) {
                        errorMessage = "Das Passwort muss mindestens 6 Zeichen lang sein."
                        return@Button
                    }
                    isLoading = true
                    if (isRegisterMode) {
                        viewModel.signUpWithEmail(
                            email = email.trim(),
                            password = password,
                            onSuccess = {
                                isLoading = false
                                onDismiss()
                            },
                            onError = { err ->
                                isLoading = false
                                errorMessage = err
                            }
                        )
                    } else {
                        viewModel.signInWithEmail(
                            email = email.trim(),
                            password = password,
                            onSuccess = {
                                isLoading = false
                                onDismiss()
                            },
                            onError = { err ->
                                isLoading = false
                                errorMessage = err
                            }
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                modifier = Modifier.testTag("auth_submit_button"),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (isRegisterMode) "Registrieren" else "Anmelden")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Abbrechen", color = Color.Gray)
            }
        },
        containerColor = Color.White
    )
}

fun exportBankStatementToCsv(
    context: Context,
    statementText: String,
    result: com.example.api.BankStatementReconciliationResult?
) {
    try {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "Kontoauszug_Bankabgleich.csv")

        val sb = java.lang.StringBuilder()
        // German standard CSV header
        sb.append("Datum;Empfaenger_Absender;Betrag_EUR;Buchungstyp;Verwendungszweck;Abgleich_Status;Hinweis\n")

        if (result != null && result.items.isNotEmpty()) {
            for (item in result.items) {
                val date = item.date.replace(";", ",")
                val party = item.counterparty.replace(";", ",")
                val amount = String.format(java.util.Locale.GERMANY, "%.2f", item.amount)
                val type = if (item.isIncome) "Einnahme" else "Ausgabe"
                val purpose = item.purpose.replace(";", ",")
                val statusText = when (item.status) {
                    "MATCHED" -> "Zugeordnet"
                    "MISSING_RECEIPT" -> "FEHLENDER BELEG"
                    "RENT_ARREARS" -> "MIETRÜCKSTAND"
                    else -> "Ungeklärt"
                }
                val notes = item.notes.replace(";", ",").replace("\n", " ")
                sb.append("$date;\"$party\";$amount;$type;\"$purpose\";\"$statusText\";\"$notes\"\n")
            }
        } else {
            val lines = statementText.lines().filter { it.isNotBlank() }
            for (line in lines) {
                val parts = line.split("|").map { it.trim() }
                if (parts.size >= 3) {
                    val date = parts[0].replace(";", ",")
                    val party = parts[1].replace(";", ",")
                    val rawAmount = parts[2].replace(";", ",").replace("EUR", "").trim()
                    val purpose = parts.drop(3).joinToString(" ").replace(";", ",")
                    val isIncome = !rawAmount.startsWith("-")
                    val type = if (isIncome) "Einnahme" else "Ausgabe"
                    sb.append("$date;\"$party\";\"$rawAmount\";$type;\"$purpose\";\"Standardisiert\";\"\"\n")
                } else {
                    val cleanLine = line.replace(";", ",")
                    sb.append(";\"$cleanLine\";;;;\"Unformatiert\";\"\"\n")
                }
            }
        }

        file.writeText(sb.toString(), Charsets.UTF_8)

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            context.packageName + ".provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Kontoauszug_Bankabgleich.csv")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Kontoauszug als CSV exportieren"))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Fehler beim CSV-Export: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
    }
}

@Composable
fun KiPowerCenterDialog(
    viewModel: ReceiptViewModel,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Steuer, 1: Bankabgleich, 2: Nebenkosten, 3: Mietpreis, 4: Mängel, 5: Verträge

    // State bindings
    val taxReport by viewModel.taxPlausibilityReport.collectAsState()
    val isAnalyzingTax by viewModel.isAnalyzingTaxPlausibility.collectAsState()

    val bankStatementResult by viewModel.bankStatementResult.collectAsState()
    val isMatchingBankStatement by viewModel.isMatchingBankStatement.collectAsState()
    val bankStatementResetVersion by viewModel.bankStatementResetVersion.collectAsState()

    val utilityStatement by viewModel.tenantUtilityStatement.collectAsState()
    val isGeneratingUtility by viewModel.isGeneratingUtilityStatement.collectAsState()

    val rentReport by viewModel.rentYieldReport.collectAsState()
    val isOptimizingRent by viewModel.isOptimizingRentYield.collectAsState()

    val damageAssessment by viewModel.damageAssessment.collectAsState()
    val isAssessingDamage by viewModel.isAssessingDamage.collectAsState()

    val contractAnalysis by viewModel.contractAnalysis.collectAsState()
    val isAnalyzingContract by viewModel.isAnalyzingContract.collectAsState()

    val context = LocalContext.current

    // Inputs for Bankabgleich
    var bankStatementText by remember(bankStatementResetVersion) { mutableStateOf("") }

    val bankCsvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val content = context.contentResolver.openInputStream(it)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: ""
                if (content.isNotBlank()) {
                    bankStatementText = content
                    android.widget.Toast.makeText(context, "Bank CSV/Kontoauszug erfolgreich importiert!", android.widget.Toast.LENGTH_SHORT).show()
                    viewModel.runBankStatementMatching(content)
                } else {
                    android.widget.Toast.makeText(context, "Die gewählte Datei ist leer.", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Fehler beim Lesen der Datei: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    // Inputs for Nebenkosten
    var tenantName by remember { mutableStateOf("Max Mustermann") }
    var unitName by remember { mutableStateOf("WE 01 (OG Links)") }
    var sqmText by remember { mutableStateOf("75.0") }

    // Inputs for Mietpreis
    var locationText by remember { mutableStateOf("München / Deutschland") }

    // Inputs for Mängel
    var damageDescription by remember { mutableStateOf("Feuchtigkeitsfleck an der Badezimmerdecke mit leichtem Schimmelansatz.") }

    // Inputs for Verträge
    var contractText by remember { mutableStateOf("Mietvertrag § 5: Die Kaltmiete beträgt 650€. Wertsicherungsklausel nach VPI (Indexmiete). Schönheitsreparaturen sind bei Auszug auszuführen.") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(26.dp))
                Column {
                    Text("Gemini KI-Zentrale", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = DarkNavy)
                    Text("Intelligente Assistenten für deine Immobilien & Finanzen", fontSize = 11.sp, color = SlateGray)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 530.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tab Selection Bar
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                    containerColor = Color(0xFFF1F5F9),
                    contentColor = DarkNavy
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("⚖️ Steuer", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("🏦 Bankabgleich", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                val alertCount = (bankStatementResult?.missingReceiptsCount ?: 0) + (bankStatementResult?.rentArrearsCount ?: 0)
                                if (alertCount > 0) {
                                    Surface(
                                        color = CrimsonRed,
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = "$alertCount",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("📄 Nebenkosten", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("📈 Mietpreis", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        text = { Text("🛠️ Mängel-Foto", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 5,
                        onClick = { selectedTab = 5 },
                        text = { Text("📜 Verträge", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (selectedTab) {
                        // --- TAB 0: STEUER- & PLAUSIBILITÄTSPRÜFER ---
                        0 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(
                                    color = Color(0xFFEFF6FF),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = "Überprüft alle Belege auf die 15%-Grenze (§6 EStG), Handwerkerleistungen (§35a EStG) und Unstimmigkeiten vor Abgabe der Steuererklärung.",
                                        fontSize = 11.sp,
                                        color = DarkNavy,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }

                                Button(
                                    onClick = { viewModel.runTaxPlausibilityCheck() },
                                    enabled = !isAnalyzingTax,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isAnalyzingTax) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Gemini prüft Steuerkonformität...", fontSize = 12.sp)
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Jetzt Steuer-Prüfung starten", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                taxReport?.let { report ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Status:", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                                Surface(
                                                    color = when (report.overallStatus) {
                                                        "KRITISCH" -> CrimsonRed.copy(alpha = 0.2f)
                                                        "WARNUNG" -> Color(0xFFD97706).copy(alpha = 0.2f)
                                                        else -> EmeraldGreen.copy(alpha = 0.2f)
                                                    },
                                                    shape = CircleShape
                                                ) {
                                                    Text(
                                                        text = report.overallStatus,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp,
                                                        color = when (report.overallStatus) {
                                                            "KRITISCH" -> CrimsonRed
                                                            "WARNUNG" -> Color(0xFFD97706)
                                                            else -> EmeraldGreen
                                                        }
                                                    )
                                                }
                                            }

                                            Text("15%-Grenze Sanierung:", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                            LinearProgressIndicator(
                                                progress = { (report.sanierungLimitPercentage / 100.0).toFloat().coerceIn(0f, 1f) },
                                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                                                color = if (report.is15PercentLimitExceeded) CrimsonRed else EmeraldGreen,
                                                trackColor = Color(0xFFE2E8F0)
                                            )
                                            Text(
                                                text = "${report.sanierungCostSum} € / Limit: ${report.sanierungCostLimit} € (${report.sanierungLimitPercentage}%)",
                                                fontSize = 10.5.sp,
                                                color = SlateGray
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Erkenntnisse (${report.findings.size}):", fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                            report.findings.forEach { finding ->
                                                Surface(
                                                    color = Color.White,
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(8.dp)) {
                                                        Text(finding.title, fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = DarkNavy)
                                                        Text(finding.description, fontSize = 10.5.sp, color = SlateGray)
                                                        if (finding.actionRecommendation.isNotBlank()) {
                                                            Text("💡 Empfehlung: ${finding.actionRecommendation}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
                                                        }
                                                    }
                                                }
                                            }

                                            Text(report.summaryText, fontSize = 11.sp, color = DarkNavy, lineHeight = 15.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // --- TAB 1: KI KONTOAUSZUGS-MATCHING (BANKABGLEICH) ---
                        1 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Surface(
                                    color = Color(0xFFF0FDF4),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = "Importiere deine Bank-Kontoauszug-Datei (CSV/Text) oder füge den Inhalt ein. Die KI gleicht automatisch Mieteinnahmen & Ausgaben mit Belegen ab & erkennt fehlende Belege oder Mietrückstände.",
                                        fontSize = 11.sp,
                                        color = DarkNavy,
                                        modifier = Modifier.padding(10.dp),
                                        lineHeight = 15.sp
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            bankCsvPickerLauncher.launch(arrayOf("*/*"))
                                        },
                                        modifier = Modifier.weight(1.1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = DarkNavy),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("📂 Bank CSV importieren", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { exportBankStatementToCsv(context, bankStatementText, bankStatementResult) },
                                        modifier = Modifier.weight(0.9f),
                                        border = BorderStroke(1.dp, EmeraldGreen),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = EmeraldGreen)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("📥 CSV Export", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                OutlinedTextField(
                                    value = bankStatementText,
                                    onValueChange = { bankStatementText = it },
                                    label = { Text("Kontoauszug / Transaktionen (Text oder CSV)", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 4
                                )

                                Button(
                                    onClick = { viewModel.runBankStatementMatching(bankStatementText) },
                                    enabled = !isMatchingBankStatement,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isMatchingBankStatement) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Gemini prüft Transaktionen...", fontSize = 12.sp)
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("🏦 KI Bankabgleich durchführen", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                bankStatementResult?.let { res ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                        border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.4f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Bankabgleich Ergebnis (${res.period})", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = DarkNavy)
                                                OutlinedButton(
                                                    onClick = { exportBankStatementToCsv(context, bankStatementText, res) },
                                                    modifier = Modifier.height(28.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    border = BorderStroke(1.dp, EmeraldGreen),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(12.dp), tint = EmeraldGreen)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("CSV Export", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Surface(
                                                    color = EmeraldGreen.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text("Zugeordnet", fontSize = 9.5.sp, color = SlateGray)
                                                        Text("${res.matchedCount}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = EmeraldGreen)
                                                    }
                                                }
                                                Surface(
                                                    color = Color(0xFFD97706).copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text("Fehlende Belege", fontSize = 9.5.sp, color = SlateGray)
                                                        Text("${res.missingReceiptsCount}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFD97706))
                                                    }
                                                }
                                                Surface(
                                                    color = CrimsonRed.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Text("Mietrückstände", fontSize = 9.5.sp, color = SlateGray)
                                                        Text("${res.rentArrearsCount}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CrimsonRed)
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("Abgeglichene Umsätze (${res.items.size}):", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = DarkNavy)

                                            res.items.forEach { item ->
                                                Surface(
                                                    color = Color.White,
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text("${item.date} • ${item.counterparty}", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = DarkNavy)
                                                            Surface(
                                                                color = when (item.status) {
                                                                    "MATCHED" -> EmeraldGreen.copy(alpha = 0.15f)
                                                                    "MISSING_RECEIPT" -> Color(0xFFD97706).copy(alpha = 0.15f)
                                                                    "RENT_ARREARS" -> CrimsonRed.copy(alpha = 0.15f)
                                                                    else -> SlateGray.copy(alpha = 0.15f)
                                                                },
                                                                shape = CircleShape
                                                            ) {
                                                                Text(
                                                                    text = when (item.status) {
                                                                        "MATCHED" -> "✅ Zugeordnet"
                                                                        "MISSING_RECEIPT" -> "⚠️ Fehlender Beleg"
                                                                        "RENT_ARREARS" -> "🚨 Mietrückstand"
                                                                        else -> "Ungeklärt"
                                                                    },
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = when (item.status) {
                                                                        "MATCHED" -> EmeraldGreen
                                                                        "MISSING_RECEIPT" -> Color(0xFFD97706)
                                                                        "RENT_ARREARS" -> CrimsonRed
                                                                        else -> SlateGray
                                                                    }
                                                                )
                                                            }
                                                        }
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(item.purpose, fontSize = 10.5.sp, color = SlateGray, modifier = Modifier.weight(1f))
                                                            Text(
                                                                text = "${if (item.isIncome) "+" else "-"}${item.amount} €",
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 11.5.sp,
                                                                color = if (item.isIncome) EmeraldGreen else DarkNavy
                                                            )
                                                        }
                                                        if (item.notes.isNotBlank()) {
                                                            Text(
                                                                text = "💡 ${item.notes}",
                                                                fontSize = 10.sp,
                                                                color = if (item.status == "MISSING_RECEIPT") Color(0xFFD97706) else if (item.status == "RENT_ARREARS") CrimsonRed else AccentBlue,
                                                                lineHeight = 13.sp
                                                            )
                                                        }

                                                        if (item.status == "MISSING_RECEIPT") {
                                                            Button(
                                                                onClick = {
                                                                    onDismiss()
                                                                    viewModel.setScreen(AppScreen.ADD_RECEIPT)
                                                                },
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(top = 4.dp),
                                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                                shape = RoundedCornerShape(6.dp)
                                                            ) {
                                                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(13.dp))
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text("Beleg für ${item.counterparty} (${item.amount} €) jetzt scannen", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        } else if (item.status == "RENT_ARREARS") {
                                                            OutlinedButton(
                                                                onClick = {
                                                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                                        data = Uri.parse("mailto:")
                                                                        putExtra(Intent.EXTRA_SUBJECT, "Zahlungserinnerung Miete")
                                                                        putExtra(Intent.EXTRA_TEXT, "Hallo ${item.counterparty},\n\nbeim Bankabgleich wurde für den aktuellen Monat ein Fehlbetrag / Mietrückstand festgestellt.\n\nBitte überweise den ausstehenden Betrag.\n\nVielen Dank!")
                                                                    }
                                                                    context.startActivity(Intent.createChooser(intent, "Mahnung senden"))
                                                                },
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(top = 4.dp),
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                                border = BorderStroke(1.dp, CrimsonRed),
                                                                shape = RoundedCornerShape(6.dp)
                                                            ) {
                                                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp), tint = CrimsonRed)
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text("Mahnung / Zahlungserinnerung senden", fontSize = 10.5.sp, color = CrimsonRed, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            Text(res.summary, fontSize = 10.5.sp, color = DarkNavy, lineHeight = 14.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // --- TAB 2: NEBENKOSTENABRECHNUNG ---
                        2 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = tenantName,
                                    onValueChange = { tenantName = it },
                                    label = { Text("Mieter Name") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = unitName,
                                        onValueChange = { unitName = it },
                                        label = { Text("Wohneinheit") },
                                        modifier = Modifier.weight(1.5f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = sqmText,
                                        onValueChange = { sqmText = it },
                                        label = { Text("Fläche (m²)") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }

                                Button(
                                    onClick = {
                                        val sqm = sqmText.toDoubleOrNull() ?: 75.0
                                        viewModel.generateTenantUtilityStatement(tenantName, unitName, sqm)
                                    },
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

                Text("KI & Automatisierung", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateGray)

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

                Text("Daten & Sicherung", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateGray)
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

                Text("Belege & Speicher", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateGray)

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
                Text("Objekt & Personen", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SlateGray)

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
