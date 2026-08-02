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

val NumberFormatter = DecimalFormat("#,##0.00 в‚¬").apply {
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
        AppScreen.DASHBOARD -> "Гњbersicht"
        AppScreen.RECEIPTS_LIST -> "Belege"
        AppScreen.ADD_RECEIPT -> "Beleg erfassen"
        AppScreen.LOGBOOK -> "Fahrtenbuch"
        AppScreen.LEDGER -> "Finanzen"
        AppScreen.RENT_OVERVIEW -> "MieteingГ¤nge"
        AppScreen.TAX_CALCULATOR -> "SteuerschГ¤tzung"
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
                                if (currentScreen == AppScreen.DASHBOARD) "Steuer-Assistent вЂў Anlage V" else "Steuer-Assistent",
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
 Ы}лKh‘йм¶»§q«^uШ\›SЬ[™ЩB€
B€ЫX[ќ\Э]K›Ь\][Ы’YЛњЫЬќY

K™›Ь‘XXЪИЬ\][Ы’YO‚€ќ]ЫЉ€ЫђЫXЪИHВ€љY]У[Щ[њ™\Э[YQ\XШ]PЫX[ќ\Ь\][ЫЉЬ\][Ы’Y
B€K€[ЩYљY\€H[ЩYљY\‹™љ[X^ЪY

B€
HВ€^
ђ™\™Z[љYЭ[™И›ЬќЩ]™[€ЉB€B€B€B€B€B€\И\XШ]PЫX[ќ\ZTЭ]K”™XYHO€В€Y€
ЫX[ќ\Э]K™Ь›Э\Лљ\С[\J
JHВ€^
€’ЩZ[™HX›][™Ьќ\[€ЩYќ[™[‹€‹€ЫЫЬ€H[Y\[Ь™Y[‹€›ЫќЪ^™HHL‹њЬ€[ЩYљY\€H[ЩYљY\‹њY[™К›ЭЫHHL™
B€
B€H[ЩHВ€ЫX[ќ\Э]K™Ь›Э\Л™›Ь‘XXЪИЬ›Э\O‚€Ш\™
€[ЩYљY\€H[ЩYљY\‚€™љ[X^ЪY

B€њY[™К›ЭЫHH™
K€ЫЫЬњИHШ\™Y][ЛШ\™ЫЫЬњК€ЫЫќZ[™\ђЫЫЬ€HШ\›SЬ[™ЩKЫЬJ[HHЊЉB€
B€
HВ€ЫЫ[[Љ€[ЩYљY\€H[ЩYљY\‹њY[™КL™
K€™\ќXШ[\њ[™Щ[Y[ќH\њ[™Щ[Y[ќњЬXЩYћJ™
B€
HВ€^
€’]\]ZN€	ЩЬ›Э\›XZ[‘љ]™Qљ[RYH‹€›ЫќЪ^™HHLњЬ€ЫЫЬ€HЫ]QЬ^B€
B€^
€’Ш[›Ыљ\ШЪ€	ЩЬ›Э\Ш[›ЫљXШ[™\Ь^RYH‹€›ЫќЩZYЪH›ЫќЩZYЪђ›Ы€›ЫќЪ^™HHL‹њЬ€
B€^
€‰ЩЬ›Э\™\XШ]\ХФ™[[Э™KњЪ^™_HЪШ[HX›]JЉH‹€›ЫќЪ^™HHLKњЬ€
B€^
€’]\]ZHЪ\™љXЪЩ[0нњШЪ‹€ЫЫЬ€H[Y\[Ь™Y[‹€›ЫќЪ^™HHLKњЬ€›ЫќЩZYЪH›ЫќЩZYЪђ›Ы€
B€›ЭИВ€^ќ]ЫЉ€ЫђЫXЪИHВ€љY]У[Щ[њ™\]Y\Э\XШ]SY\™ЩJЬ›Э\
B€K€[ЩYљY\€H[ЩYљY\‹ќ\ЭYК€њШY™WЫY\™ЩWЩ\XШ]WЩЬ›Э\Шќ]Ы€‚€
B€
HВ€^
‘X›][€ЪXЪ\€ќ\Ш[[Y[™°п™[€ЉB€B€^ќ]ЫЉ€ЫђЫXЪИHВ€љY]У[Щ[њ™\]Y\ЭЪЫQ\XШ]QЬ›Э\[][ЫЉЬ›Э\
B€B€
HВ€^
€‘Щ\Ш[]HЬќ\H0нњШЪ[€‹€ЫЫЬ€HЬљ[\ЫЫ”™Y€
B€B€B€B€B€B€B€B€\И\XШ]PЫX[ќ\ZTЭ]KђЫЫ\]YO€В€^
€‘X›][™\™Z[љYЭ[™ИX™Щ\ШЪЬЬЩ[‹€‹€ЫЫЬ€H[Y\[Ь™Y[‹€›ЫќЪ^™HHL‹њЬ€[ЩYљY\€H[ЩYљY\‹њY[™К›ЭЫHHL™
B€
B€B€\И\XШ]PЫX[ќ\ZTЭ]K‘Z[YO€В€^
€ЫX[ќ\Э]K›Y\ЬШYЩK€ЫЫЬ€HЬљ[\ЫЫ”™Y€›ЫќЪ^™HHL‹њЬ€[ЩYљY\€H[ЩYљY\‹њY[™К›ЭЫHHL™
B€
B€B€[ЩHO€[љ]€B‚€\›X[™[ќ[]Q\њ›ЬЏЛ›]И\њ€O‚€Ш\™
€ЫЫЬњИHШ\™Y][ЛШ\™ЫЫЬњКЫЫќZ[™\ђЫЫЬ€HЬљ[\ЫЫ”™YЫЬJ[HHЊЉJK€›Ь™\€H›Ь™\”Э›ЪЩJK™Ьљ[\ЫЫ”™YЫЬJ[HHЊЩЉJK€[ЩYљY\€H[ЩYљY\‹њY[™К›ЭЫHHL‹™
B€
HВ€^
€^H\њ‹€ЫЫЬ€HЬљ[\ЫЫ”™Y€›ЫќЪ^™HHL‹њЬ€[ЩYљY\€H[ЩYљY\‹њY[™КL™
B€
B€B€B‚€Y€
[]Y™XЩZ\Лљ\С[\J
JHВ€›Ю
€[ЩYљY\€H[ЩYљY\‚€™љ[X^ЪY

B€ќЩZYЪ
YЉB€њY[™К™\ќXШ[HМ‹™
K€ЫЫќ[ќ[YЫ›Y[ќH[YЫ›Y[ќђЩ[ќ\‚€
HВ€ЫЫ[[ЉЬљ^›Ыќ[[YЫ›Y[ќH[YЫ›Y[ќђЩ[ќ\’Ьљ^›Ыќ[JHВ€XЫЫЉ€[XYЩU™XЭЬ€HXЫЫњЛ‘Y][‘[]K€ЫЫќ[ќ\ШЬљ\[Ы€Hќ[€[ќHЫЫЬ‹“YЪЬ^K€[ЩYљY\€H[ЩYљY\‹њЪ^™J™
B€
B€ЬXЩ\Љ[ЩYљY\€H[ЩYљY\‹љZYЪ
™
JB€^
‘\€\Y\љЫЬ€\ЭY\€‹ЫЫЬ€HЫЫЬ‹‘Ь^K›ЫќЩZYЪH›ЫќЩZYЪђ›Ы›ЫќЪ^™HHLЛњЬ
B€B€B€H[ЩHВ€^ћPЫЫ[[Љ€[ЩYљY\€H[ЩYљY\‹ќЩZYЪ
YЉKќ\ЭYКњ™XЮXЫWШљ[—Ы\ЭЉK€™\ќXШ[\њ[™Щ[Y[ќH\њ[™Щ[Y[ќњЬXЩYћJ™
B€
HВ€][\К[]Y™XЩZ\ЛњЪ^™JHИ[™^O‚€[™XЩZ\H[]Y™XЩZ\ЦЪ[™^B€Ш\™
€[ЩYљY\€H[ЩYљY\‹™љ[X^ЪY

K€ЫЫЬњИHШ\™Y][ЛШ\™ЫЫЬњКЫЫќZ[™\ђЫЫЬ€HЫЫЬЉ‘‘ЋђQђКJK€›Ь™\€H›Ь™\”Э›ЪЩJK™ЫЫЬЉ‘ђР‘QLJJB€
HВ€ЫЫ[[Љ[ЩYљY\€H[ЩYљY\‹њY[™КL™
JHВ€›ЭК€[ЩYљY\€H[ЩYљY\‹™љ[X^ЪY

K€Ьљ^›Ыќ[\њ[™Щ[Y[ќH\њ[™Щ[Y[ќ”ЬXЩP™]ЩY[‹€™\ќXШ[[YЫ›Y[ќH[YЫ›Y[ќђЩ[ќ\•™\ќXШ[B€
HВ€ЫЫ[[Љ[ЩYљY\€H[ЩYљY\‹ќЩZYЪ
YЉJHВ€^
€^H™XЩZ\]\ЬЭ[\‹љY‘[\HИ•[™ZШ[›ќ\€]\ЬЭ[\€€K€›ЫќЩZYЪH›ЫќЩZYЪђ›Ы€›ЫќЪ^™HHLЛњЬ€ЫЫЬ€H\љУ]ћK€X^[™\ИHK€Э™\™›ЭИH^Э™\™›ЭЛ‘[\Ъ\В€
B€^
€^Hђ™[YЩ][N€	Ь™XЩZ\™][_H8 (€™]YО€	Уќ[X™\‘›Ь›X]\‹™›Ь›X]
™XЩZ\њќ]Ш™]YК_H‹€›ЫќЪ^™HHLKњЬ€ЫЫЬ€HЫ]QЬ^B€
B€™XЩZ\™[]Y]Л›]И[]O‚€Y€
[]љ\У›Э[\J
JHВ€^
€^H‘Щ[0нњШЪ[N€	[]‹€›ЫќЪ^™HHLњЬ€ЫЫЬ€HЬљ[\ЫЫ”™Y€›ЫќЩZYЪH›ЫќЩZYЪ”Щ[ZP›Ы€
B€B€B€B€€Y€
™XЩZ\њЮ[ФЭ]\ИOH‘SUWФS‘S‘ИЉHВ€Э\™XЩJ€Ъ\HH›Э[™YЫЬ›™\”Ъ\J™
K€ЫЫЬ€HШ\›SЬ[™ЩKЫЬJ[HHЊYЉK€›Ь™\€H›Ь™\”Э›ЪЩJK™Ш\›SЬ[™ЩKЫЬJ[HHЌЉJK€[ЩYљY\€H[ЩYљY\‹њY[™КЭ\ќH™
B€
HВ€^
€^H“0нњШЪ[™И]\ЬЭZ[™‹€›ЫќЪ^™HHKњЬ€ЫЫЬ€HШ\›SЬ[™ЩK€›ЫќЩZYЪH›ЫќЩZYЪђ›Ы€[ЩYљY\€H[ЩYљY\‹њY[™КЬљ^›Ыќ[H‹™™\ќXШ[H‹™
B€
B€B€B€B€€ЬXЩ\Љ[ЩYљY\€H[ЩYљY\‹љZYЪ
™
JB€›ЭК€[ЩYљY\€H[ЩYљY\‹™љ[X^ЪY

K€Ьљ^›Ыќ[\њ[™Щ[Y[ќH\њ[™Щ[Y[ќ‘[™€™\ќXШ[[YЫ›Y[ќH[YЫ›Y[ќђЩ[ќ\•™\ќXШ[B€
HВ€Y€
™XЩZ\њЮ[ФЭ]\ИOH‘SUWФS‘S‘ИЉHВ€^ќ]ЫЉ€ЫђЫXЪИHИљY]У[Щ[™[]T™XЩZ\
™XЩZ\љY
HK€[ЩYљY\€H[ЩYљY\‹ќ\ЭYКњ™]ћWЩ[]WЬ[™[™ЧШќ]Ы—ЙЬ™XЩZ\љYHЉB€
HВ€›ЭК™\ќXШ[[YЫ›Y[ќH[YЫ›Y[ќђЩ[ќ\•™\ќXШ[KЬљ^›Ыќ[\њ[™Щ[Y[ќH\њ[™Щ[Y[ќњЬXЩYћJ™
JHВ€XЫЫЉXЫЫњЛ‘Y][”™Yњ™\ЪЫЫќ[ќ\ШЬљ\[Ы€Hќ[[ЩYљY\€H[ЩYљY\‹њЪ^™JM™
K[ќHШ\›SЬ[™ЩJB€^
‘\›™]]™\њЭXЪ[€‹›ЫќЪ^™HHLKњЬЫЫЬ€HШ\›SЬ[™ЩK›ЫќЩZYЪH›ЫќЩZYЪђ›Ы
B€B€B€H[ЩHВ€^ќ]ЫЉ€ЫђЫXЪИHИљY]У[Щ[њ™\ЭЬ™T™XЩZ\
™XЩZ\
HK€[ЩYљY\€H[ЩYљY\‹ќ\ЭYКњ™\ЭЬ™WЬ™XЩZ\Шќ]Ы—ЙЬ™XЩZ\љYHЉB€
HВ€›ЭК™\ќXШ[[YЫ›Y[ќH[YЫ›Y[ќђЩ[ќ\•™\ќXШ[KЬљ^›Ыќ[\њ[™Щ[Y[ќH\њ[™Щ[Y[ќњЬXЩYћJ™
JHВ€XЫЫЉXЫЫњЛ‘Y][”™Yњ™\ЪЫЫќ[ќ\ШЬљ\[Ы€Hќ[[ЩYљY\€H[ЩYљY\‹њЪ^™JM™
K[ќH[Y\[Ь™Y[ЉB€^
•ЪYY\љ\њЭ[[€‹›ЫќЪ^™HHLKњЬЫЫЬ€H[Y\[Ь™Y[‹›ЫќЩZYЪH›ЫќЩZYЪђ›Ы
B€B€B€B€€ЬXЩ\Љ[ЩYљY\€H[ЩYљY\‹ќЪY
‹™
JB€€^ќ]ЫЉ€ЫђЫXЪИHИ™XЩZ\Ф\›X[™[ќQ[]HH™XЩZ\K€[ЩYљY\€H[ЩYљY\‹ќ\ЭYКњ\›X[™[ќЩ[]WЬ™XЩZ\Шќ]Ы—ЙЬ™XЩZ\љYHЉB€
HВ€›ЭК™\ќXШ[[YЫ›Y[ќH[YЫ›Y[ќђЩ[ќ\•™\ќXШ[KЬљ^›Ыќ[\њ[™Щ[Y[ќH\њ[™Щ[Y[ќњЬXЩYћJ™
JHВ€XЫЫЉXЫЫњЛ‘Y][‘[]Q›Ь™]™\‹ЫЫќ[ќ\ШЬљ\[Ы€Hќ[[ЩYљY\€H[ЩYљY\‹њЪ^™JM™
K[ќHЬљ[\ЫЫ”™Y
B€^
‘[™рпYИ0нњШЪ[€‹›ЫќЪ^™HHLKњЬЫЫЬ€HЬљ[\ЫЫ”™Y›ЫќЩZYЪH›ЫќЩZYЪђ›Ы
B€B€B€B€B€B€B€B€B€B€K€ЫЫ™љ\›Pќ]Ы€HВ€ќ]ЫЉ€ЫђЫXЪИHЫ‘\ЫZ\ЬЛ€ЫЫЬњИHќ]Ы‘Y][Лќ]ЫђЫЫЬњКЫЫќZ[™\ђЫЫЬ€H\љУ]ћJB€
HВ€^
”ШЪYpзЩ[€ЉB€B€B€
BџB