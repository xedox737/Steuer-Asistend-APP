package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Composable
import org.json.JSONObject
import org.json.JSONArray
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DistanceEvidence
import com.example.data.KilometerSource
import com.example.data.LogbookDistancePolicy
import com.example.data.LogbookTrip
import com.example.data.PropertyMetadata
import com.example.data.Receipt
import com.example.data.StandardRoute
import com.example.data.RouteDistanceResult
import com.example.data.TripRouteMode
import com.example.data.TripRouteNormalizer
import com.example.data.TripStop
import com.example.data.TripStopJson
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val LogbookBlue = Color(0xFF0069D9)
private val LogbookPaleBlue = Color(0xFFEAF4FF)
private val LogbookCoins = ImageVector.Builder("LogbookCoins", 24.dp, 24.dp, 24f, 24f).apply {
    listOf(12f to 3f, 3f to 8f).forEach { (left, top) ->
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f) {
            moveTo(left, top + 2f)
            curveTo(left, top - 0.5f, left + 9f, top - 0.5f, left + 9f, top + 2f)
            curveTo(left + 9f, top + 4.5f, left, top + 4.5f, left, top + 2f)
            moveTo(left, top + 2f)
            lineTo(left, top + 10f)
            curveTo(left, top + 12.5f, left + 9f, top + 12.5f, left + 9f, top + 10f)
            lineTo(left + 9f, top + 2f)
            moveTo(left, top + 6f)
            curveTo(left, top + 8.5f, left + 9f, top + 8.5f, left + 9f, top + 6f)
        }
    }
}.build()

private fun Double.germanKm(): String = String.format(Locale.GERMANY, "%.1f km", this)
private val logbookDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)
private fun String.logbookDate(): String = runCatching { LocalDate.parse(this).format(logbookDateFormat) }.getOrDefault(this)

private fun manualTripReceipt() = Receipt(
    aussteller = "Manuelle Fahrt", datum = LocalDate.now().toString(), uhrzeit = "",
    bruttobetrag = 0.0, hauptkategorie = "", unterkategorie = "", kontoNr = "",
    beschreibung = ""
)

@Composable
private fun LogbookSavedDetail(trip: LogbookTrip, viewModel: ReceiptViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ArrowBack, "Zurück", Modifier.clickable(onClick = onBack))
            Spacer(Modifier.width(16.dp))
            Text("Fahrt prüfen", fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
        }
        LogbookPanel("") {
            LogbookRoute(trip.startAddress, trip.stops.drop(1).dropLast(1).map { it.address }, trip.destinationAddress,
                onStart = null, onDestination = null, onStop = null)
        }
        LogbookRouteStatus(trip.kilometerSource, trip.manuallyConfirmed, trip.taxDistanceKm)
        LogbookPanel("Fahrtdaten") {
            LogbookSummaryRow(Icons.Default.CalendarMonth, "Datum", trip.date.logbookDate())
            HorizontalDivider(color = BorderColor)
            LogbookSummaryRow(Icons.Default.SwapHoriz, "Strecke", trip.taxDistanceKm.germanKm())
            HorizontalDivider(color = BorderColor)
            LogbookSummaryRow(Icons.Default.Work, "Zweck der Fahrt", trip.purpose)
        }
        if (trip.cancelledAt.isBlank()) LogbookTaxAssignment() else Text("Fahrt storniert", color = CrimsonRed)
        LogbookManageActions(trip, viewModel)
        trip.sourceReceiptId?.let { LogbookPanel("Beleg") {
            LogbookValueRow(Icons.Default.ReceiptLong, "Verknüpfter Beleg", "#$it")
        } }

    }
}

@Composable
fun LogbookScreen(viewModel: ReceiptViewModel) {
    val receipts by viewModel.receipts.collectAsState()
    val metadata by viewModel.propertyMetadata.collectAsState()
    val effectiveMetadata = metadata ?: PropertyMetadata()
    val trips by viewModel.logbookTrips.collectAsState()
    val routes by viewModel.standardRoutes.collectAsState()
    val drafts by viewModel.logbookDrafts.collectAsState()
    val activeTrips = trips.filter { it.cancelledAt.isBlank() }
    val suggested = receipts.filter { receipt ->
        val vendor = receipt.aussteller.lowercase(Locale.GERMANY)
        val category = receipt.hauptkategorie.lowercase(Locale.GERMANY)
        val description = receipt.beschreibung.lowercase(Locale.GERMANY)
        val likelyTravel = listOf("obi", "hornbach", "bauhaus", "toom", "hagebau", "baumarkt").any(vendor::contains) ||
            category.contains("sanierung") || description.contains("material")
        likelyTravel && receipt.datum.matches(Regex("""\d{4}-\d{2}-\d{2}""")) &&
            trips.none { it.sourceReceiptId == receipt.id }
    }
    var selectedSuggestionId by rememberSaveable { mutableStateOf(-1) }
    var selectedTripId by rememberSaveable { mutableStateOf(-1L) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val selectedTrip = trips.firstOrNull { it.id == selectedTripId }
    val selectedSuggestion = if (selectedSuggestionId == 0) manualTripReceipt() else receipts.firstOrNull { it.id == selectedSuggestionId }


    selectedTrip?.let { trip ->
        LogbookSavedDetail(trip, viewModel, onBack = { selectedTripId = -1L })
        return
    }

    selectedSuggestion?.let { receipt ->
        LogbookEntryScreen(
            receipt = receipt,
            metadata = effectiveMetadata,
            viewModel = viewModel,
            onBack = { selectedSuggestionId = -1 },
            onSaved = { selectedSuggestionId = -1 }
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.White)
            .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Fahrtenbuch", fontSize = 20.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
        LogbookHero(activeTrips)
        LogbookDraftCards(drafts, viewModel, onResume = { selectedSuggestionId = it })
        OutlinedButton(
            onClick = { selectedSuggestionId = 0 },
            modifier = Modifier.fillMaxWidth().height(36.dp), shape = RoundedCornerShape(6.dp),
            contentPadding = PaddingValues(0.dp), border = BorderStroke(1.dp, LogbookBlue),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = LogbookBlue)
        ) { Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Neue Fahrt", fontWeight = FontWeight.Bold) }
        if (trips.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (showAll) "Alle Fahrten" else "Letzte Fahrten", fontWeight = FontWeight.Bold, color = DarkNavy)
                Text(if (showAll) "Weniger anzeigen" else "Alle anzeigen  ›", modifier = Modifier.clickable { showAll = !showAll }, color = LogbookBlue, fontSize = 12.sp, lineHeight = 15.sp)
            }
            if (showAll) LogbookTripBrowser(trips, onOpen = { selectedTripId = it.id })
            else activeTrips.take(5).forEach { SavedTripCard(it) { selectedTripId = it.id } }
        }
        if (suggested.isNotEmpty()) {
            Text("Fahrtvorschläge aus Belegen", fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp, color = DarkNavy)
            suggested.forEach { LogbookSuggestionPreview(it, effectiveMetadata) { selectedSuggestionId = it.id } }
        }
        if (routes.isNotEmpty()) StandardRoutesCard(routes, viewModel)
    }
}

@Composable
internal fun LogbookEntryScreen(
    receipt: Receipt,
    metadata: PropertyMetadata,
    viewModel: ReceiptViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.White).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        LogbookSuggestionCard(receipt, metadata, viewModel, onBack = onBack,
            onSaved = onSaved, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LogbookSuggestionPreview(receipt: Receipt, metadata: PropertyMetadata, onOpen: () -> Unit) {
    val purpose = if (receipt.beschreibung.isNotBlank()) "Material einkaufen" else "Fahrt zu ${receipt.aussteller}"
    Card(
        modifier = Modifier.fillMaxWidth().testTag("logbook_suggestion_${receipt.id}").clickable(onClick = onOpen),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(34.dp).background(LogbookBlue.copy(alpha = .09f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.DirectionsCar, null, tint = LogbookBlue, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(receipt.datum.logbookDate(), fontSize = 10.sp, lineHeight = 13.sp, color = SlateGray)
                Text("${metadata.wohnort.substringBefore(',')} → ${receipt.aussteller}", fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 15.sp, color = DarkNavy, maxLines = 1)
                Text("$purpose · KI-Vorschlag", fontSize = 10.sp, lineHeight = 13.sp, color = SlateGray, maxLines = 1)
            }
            Icon(Icons.Default.ChevronRight, null, tint = SlateGray, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LogbookHero(trips: List<LogbookTrip>) {
    val month = YearMonth.now()
    val monthlyTrips = trips.filter { it.date.startsWith(month.toString()) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LogbookMetric(Modifier.weight(1f), "${month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.GERMANY)} ${month.year}", "${monthlyTrips.size} Fahrten", LogbookBlue)
        LogbookMetric(Modifier.weight(1f), "Werbungskosten", String.format(Locale.GERMANY, "%.2f €", monthlyTrips.sumOf { it.taxDistanceKm } * 0.30), LogbookBlue)
    }
}

@Composable
private fun LogbookMetric(modifier: Modifier, label: String, value: String, tint: Color) {
    Card(modifier = modifier, shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = SoftBackground), border = BorderStroke(1.dp, BorderColor)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            LogbookIcon(if (label == "Werbungskosten") LogbookCoins else Icons.Default.CalendarMonth, tint)
            Spacer(Modifier.width(7.dp))
            Column {
                Text(label, fontSize = 9.sp, lineHeight = 12.sp, color = SlateGray, maxLines = 1)
                Text(value, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            }
        }
    }
}

@Composable
private fun SavedTripCard(trip: LogbookTrip, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen), shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Top) {
            LogbookIcon(Icons.Default.DirectionsCar)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(trip.date.logbookDate(), fontSize = 10.sp, lineHeight = 13.sp, color = SlateGray)
                Text("${trip.startAddress.substringBefore(',')} → ${trip.destinationAddress.substringBefore(',')}",
                    fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 15.sp, color = DarkNavy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Work, null, tint = SlateGray, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(6.dp)); Text(trip.purpose, fontSize = 10.sp, lineHeight = 13.sp, color = SlateGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = SlateGray, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(6.dp)); Text(trip.taxDistanceKm.germanKm(), fontSize = 10.sp, lineHeight = 13.sp, color = SlateGray)
                    Spacer(Modifier.weight(1f))
                    if (trip.manuallyConfirmed) LogbookBadge("Bestätigt")
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = SlateGray, modifier = Modifier.align(Alignment.CenterVertically).size(16.dp))
        }
    }
}

@Composable
private fun StandardRoutesCard(routes: List<StandardRoute>, viewModel: ReceiptViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Standardstrecken (${routes.size})", fontWeight = FontWeight.Bold, color = DarkNavy)
                Text(if (expanded) "Schließen" else "Verwalten", color = LogbookBlue, fontSize = 11.sp, lineHeight = 14.sp)
            }
            if (expanded) routes.forEach {
                EditableStandardRoute(it, viewModel)
                HorizontalDivider(color = BorderColor)
            }
        }
    }
}

@Composable
private fun EditableStandardRoute(route: StandardRoute, viewModel: ReceiptViewModel) {
    val scope = rememberCoroutineScope()
    var name by remember(route.id) { mutableStateOf(route.name) }
    var distance by remember(route.id) { mutableStateOf(route.distanceKm.toString()) }
    var startAddress by remember(route.id) { mutableStateOf(route.startAddress) }
    var destinationAddress by remember(route.id) { mutableStateOf(route.destinationAddress) }
    var intermediate by remember(route.id) {
        mutableStateOf(route.stops.filter { it.label.startsWith("Zwischenstopp") }.map { it.address })
    }
    var message by remember(route.id) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Bezeichnung") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(startAddress, { startAddress = it }, label = { Text("Start") }, modifier = Modifier.fillMaxWidth())
        intermediate.forEachIndexed { index, value ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value, { changed ->
                    intermediate = intermediate.toMutableList().also { it[index] = changed }
                }, label = { Text("Zwischenstopp ${index + 1}") }, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = {
                    if (index > 0) intermediate = intermediate.toMutableList().also {
                        val item = it.removeAt(index); it.add(index - 1, item)
                    }
                }, enabled = index > 0) { Text("↑") }
                OutlinedButton(onClick = {
                    if (index < intermediate.lastIndex) intermediate = intermediate.toMutableList().also {
                        val item = it.removeAt(index); it.add(index + 1, item)
                    }
                }, enabled = index < intermediate.lastIndex) { Text("↓") }
                OutlinedButton(onClick = {
                    intermediate = intermediate.toMutableList().also { it.removeAt(index) }
                }) { Text("×") }
            }
        }
        OutlinedButton(onClick = { intermediate = intermediate + "" }) { Text("+ Zwischenstopp") }
        OutlinedTextField(destinationAddress, { destinationAddress = it }, label = { Text("Ziel") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            distance, { distance = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
            label = { Text("Gesamtstrecke (km)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val km = distance.replace(',', '.').toDoubleOrNull()
                if (name.isBlank() || km == null || km <= 0.0) message = "Bezeichnung und positive Strecke erforderlich."
                else scope.launch {
                    val mode = runCatching { TripRouteMode.valueOf(route.routeMode) }.getOrDefault(TripRouteMode.EINFACH)
                    val normalized = runCatching {
                        TripRouteNormalizer.normalize(
                            startAddress,
                            intermediate.mapIndexed { index, address -> TripStop(address, "Zwischenstopp ${index + 1}", index) },
                            destinationAddress, mode, route.sameReturnRoute
                        )
                    }.getOrElse { message = it.message; return@launch }
                    viewModel.saveStandardRoute(route.copy(
                        name = name.trim(), startAddress = startAddress.trim(), destinationAddress = destinationAddress.trim(),
                        stopsJson = TripStopJson.encode(normalized.stops), distanceKm = km,
                        routeSignature = normalized.signature, sourceProvider = KilometerSource.MANUELL.name,
                        updatedAt = Instant.now().toString()
                    ))
                    message = "Gespeichert."
                }
            }) { Text("Änderung speichern") }
            OutlinedButton(onClick = { viewModel.deleteStandardRoute(route.id) }) { Text("Löschen") }
        }
        message?.let { Text(it, fontSize = 10.sp, lineHeight = 13.sp, color = SlateGray) }
    }
}

@Composable
private fun LogbookPanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (title.isNotBlank()) Text(title, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            content()
        }
    }
}

@Composable
private fun LogbookValueRow(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = LogbookBlue, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 9.sp, lineHeight = 12.sp, color = SlateGray)
            Text(value.ifBlank { "Antippen und eingeben" }, fontSize = 12.sp, lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold, color = DarkNavy,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onClick != null) Icon(Icons.Default.ChevronRight, null, tint = SlateGray, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun LogbookSuggestionCard(
    receipt: Receipt,
    metadata: PropertyMetadata,
    viewModel: ReceiptViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val draftKey = receipt.id.toString()
    val draft = remember(receipt.id) { runCatching { JSONObject(viewModel.logbookDrafts.value[draftKey].orEmpty()) }.getOrDefault(JSONObject()) }
    var draftSaved by remember { mutableStateOf(false) }
    val bookingKey = rememberSaveable(receipt.id) { draft.optString("bookingKey").ifBlank { java.util.UUID.randomUUID().toString() } }
    var linkedReceiptId by rememberSaveable(receipt.id) { mutableStateOf(draft.optInt("linkedReceiptId", receipt.id)) }
    var propertyId by rememberSaveable(receipt.id) { mutableStateOf(draft.optString("propertyId", if (receipt.id > 0) receipt.propertyId else metadata.propertyId)) }
    var unit by rememberSaveable(receipt.id) { mutableStateOf(draft.optString("unit", receipt.wohneinheit)) }
    val allReceipts by viewModel.receipts.collectAsState()
    val allProperties by viewModel.properties.collectAsState()
    var selectReceipt by remember { mutableStateOf(false) }
    var selectProperty by remember { mutableStateOf(false) }
    val linkedReceipt = allReceipts.firstOrNull { it.id == linkedReceiptId }
    var start by rememberSaveable(receipt.id) { mutableStateOf(draft.optString("start", metadata.wohnort)) }
    var intermediateStops by rememberSaveable(receipt.id, receipt.aussteller) {
        mutableStateOf<List<String>>(draft.optJSONArray("stops")?.let { array -> List(array.length()) { array.getString(it) } } ?: if (receipt.id == 0) emptyList() else listOf(receipt.aussteller))
    }
    var destination by rememberSaveable(receipt.id) { mutableStateOf(draft.optString("destination", metadata.adresse)) }
    var purpose by rememberSaveable(receipt.id) {
        mutableStateOf(draft.optString("purpose", if (receipt.id == 0) "" else if (receipt.beschreibung.isNotBlank())
            "Materialkauf / ${receipt.beschreibung}" else "Materialkauf bei ${receipt.aussteller}"))
    }
    var mode by rememberSaveable(receipt.id) { mutableStateOf(runCatching { TripRouteMode.valueOf(draft.optString("mode")) }.getOrDefault(TripRouteMode.HIN_UND_RUECKFAHRT)) }
    var sameReturnRoute by rememberSaveable(receipt.id) { mutableStateOf(draft.optBoolean("sameReturnRoute", true)) }
    var aiKm by remember(receipt.id) { mutableStateOf(if (draft.has("aiKm")) draft.getDouble("aiKm") else null) }
    var routeResult by remember(receipt.id) { mutableStateOf(if (draft.has("routedKm")) RouteDistanceResult(draft.getDouble("routedKm"), draft.optString("provider"), draft.optString("calculatedAt")) else null) }
    var manualKmText by rememberSaveable(receipt.id) { mutableStateOf(draft.optString("manualKmText", "")) }
    var odometerStartText by rememberSaveable(receipt.id) { mutableStateOf(draft.optString("odometerStartText", "")) }
    var odometerEndText by rememberSaveable(receipt.id) { mutableStateOf(draft.optString("odometerEndText", "")) }
    var matchedStandardRoute by remember(receipt.id) { mutableStateOf<StandardRoute?>(null) }
    var useStandardRoute by remember(receipt.id) { mutableStateOf(false) }
    var correctionReason by rememberSaveable(receipt.id) { mutableStateOf(draft.optString("correctionReason", "")) }
    var correctionNote by rememberSaveable(receipt.id) { mutableStateOf(draft.optString("correctionNote", "")) }
    var correctionMenuExpanded by remember(receipt.id) { mutableStateOf(false) }
    var confirmed by remember(receipt.id) { mutableStateOf(false) }
    var message by remember(receipt.id) { mutableStateOf<String?>(null) }
    var busy by remember(receipt.id) { mutableStateOf(false) }
    var step by rememberSaveable(receipt.id) { mutableStateOf(draft.optInt("step", 0)) }
    var stepHistory by rememberSaveable(receipt.id) { mutableStateOf(draft.optJSONArray("history")?.let { a -> List(a.length()) { a.getInt(it) } } ?: emptyList<Int>()) }
    fun openStep(nextStep: Int) {
        if (nextStep != step) {
            stepHistory = stepHistory + step
            step = nextStep
        }
    }
    fun goBack() {
        val previousStep = stepHistory.lastOrNull()
        if (previousStep == null) {
            onBack()
        } else {
            stepHistory = stepHistory.dropLast(1)
            step = previousStep
        }
    }
    BackHandler(onBack = ::goBack)
    var tripDate by rememberSaveable(receipt.id, receipt.aussteller) { mutableStateOf(draft.optString("date", receipt.datum)) }
    var editTarget by remember { mutableStateOf<String?>(null) }
    var editValue by remember { mutableStateOf("") }

    val normalizedRoute = runCatching {
        TripRouteNormalizer.normalize(
            start,
            intermediateStops.mapIndexed { index, address -> TripStop(address, "Zwischenstopp ${index + 1}", index) },
            destination, mode, sameReturnRoute
        )
    }.getOrNull()
    var distanceSignature by rememberSaveable(receipt.id) { mutableStateOf(draft.optString("signature", "")) }
    LaunchedEffect(normalizedRoute?.signature) {
        val routeChanged = distanceSignature != normalizedRoute?.signature
        if (routeChanged) {
            routeResult = null
            aiKm = null
            distanceSignature = normalizedRoute?.signature.orEmpty()
        }
        confirmed = false
        matchedStandardRoute = normalizedRoute?.let { viewModel.findStandardRoute(it.signature) }
        useStandardRoute = if (!routeChanged && draft.has("useStandardRoute")) draft.optBoolean("useStandardRoute") else matchedStandardRoute != null
    }
    val evidence = DistanceEvidence(
        aiEstimatedKm = aiKm, routedKm = routeResult?.distanceKm,
        odometerStartKm = odometerStartText.replace(',', '.').toDoubleOrNull(),
        odometerEndKm = odometerEndText.replace(',', '.').toDoubleOrNull(),
        standardRouteKm = matchedStandardRoute?.distanceKm?.takeIf { useStandardRoute },
        manualKm = manualKmText.replace(',', '.').toDoubleOrNull(),
        manuallyConfirmed = confirmed, correctionReason = correctionReason
    )
    val decision = LogbookDistancePolicy.decide(evidence)
    val stops = normalizedRoute?.stops.orEmpty()
    val draftJson = JSONObject().put("bookingKey", bookingKey).put("linkedReceiptId", linkedReceiptId)
        .put("propertyId", propertyId).put("unit", unit).put("start", start).put("destination", destination)
        .put("stops", JSONArray(intermediateStops)).put("purpose", purpose).put("date", tripDate)
        .put("mode", mode.name).put("sameReturnRoute", sameReturnRoute).put("manualKmText", manualKmText)
        .put("odometerStartText", odometerStartText).put("odometerEndText", odometerEndText)
        .put("correctionReason", correctionReason).put("correctionNote", correctionNote)
        .put("step", step).put("history", JSONArray(stepHistory)).put("signature", distanceSignature).put("useStandardRoute", useStandardRoute)
        .apply { aiKm?.let { put("aiKm", it) }; routeResult?.let { put("routedKm", it.distanceKm); put("provider", it.providerId); put("calculatedAt", it.calculatedAt) } }.toString()
    SideEffect { if (!draftSaved && viewModel.logbookDrafts.value[draftKey] != draftJson) viewModel.saveLogbookDraft(draftKey, draftJson) }
    if (selectReceipt) LogbookReceiptPicker(allReceipts.filter { it.id != linkedReceiptId && it.unterkategorie != "Fahrtkosten" }, onDismiss = { selectReceipt = false }) {
        linkedReceiptId = it.id; propertyId = it.propertyId; unit = it.wohneinheit; confirmed = false; selectReceipt = false
    }
    if (selectProperty) LogbookPropertyPicker(allProperties, onDismiss = { selectProperty = false }) {
        propertyId = it.propertyId; unit = ""; linkedReceiptId = 0; confirmed = false; selectProperty = false
    }
    val validDate = runCatching { LocalDate.parse(tripDate) }.isSuccess
    val validTrip = start.isNotBlank() && destination.isNotBlank() && purpose.isNotBlank() && validDate && propertyId.isNotBlank()
    val canSave = validTrip && confirmed && !busy && decision.taxDistanceKm != null &&
        decision.source != KilometerSource.KI_GESCHAETZT &&
        (!decision.correctionReasonRequired || correctionReason.isNotBlank()) &&
        (correctionReason != "Sonstiges" || correctionNote.isNotBlank())

    fun edit(target: String, value: String) { editTarget = target; editValue = value }
    editTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text(when {
                target == "start" -> "Startadresse"
                target == "destination" -> "Zieladresse"
                target == "date" -> "Datum (JJJJ-MM-TT)"
                target == "purpose" -> "Zweck der Fahrt"
                else -> "Zwischenstopp"
            }) },
            text = { OutlinedTextField(editValue, { editValue = it }, singleLine = target != "purpose") },
            confirmButton = {
                Button(onClick = {
                    when {
                        target == "start" -> start = editValue.trim()
                        target == "destination" -> destination = editValue.trim()
                        target == "date" -> tripDate = editValue.trim()
                        target == "purpose" -> purpose = editValue.trim()
                        target.startsWith("stop:") -> {
                            val index = target.substringAfter(':').toInt()
                            intermediateStops = intermediateStops.toMutableList().also { it[index] = editValue.trim() }
                        }
                    }
                    confirmed = false
                    editTarget = null
                }) { Text("Übernehmen") }
            },
            dismissButton = { OutlinedButton(onClick = { editTarget = null }) { Text("Abbrechen") } }
        )
    }

    Column(modifier.fillMaxSize().testTag("suggested_trip_card_${receipt.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ArrowBack, "Zurück", Modifier.clickable(onClick = ::goBack))
            Spacer(Modifier.width(16.dp))
            Text(if (step == 2) "Fahrt prüfen" else "Neue Fahrt", fontSize = 18.sp, lineHeight = 21.sp,
                fontWeight = FontWeight.Bold, color = DarkNavy)
        }
        if (step != 2) LogbookSteps(step) { index -> if (index < step) openStep(index) }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (step) {
                0 -> {
                    LogbookPanel("Route") {
                        LogbookRoute(start, intermediateStops, destination,
                            onStart = { edit("start", start) }, onDestination = { edit("destination", destination) },
                            onStop = { index -> edit("stop:$index", intermediateStops[index]) },
                            onRemove = { index ->
                                intermediateStops = intermediateStops.toMutableList().also { it.removeAt(index) }
                                confirmed = false
                            })
                        OutlinedButton(onClick = {
                            val index = intermediateStops.size
                            intermediateStops = intermediateStops + ""
                            edit("stop:$index", "")
                        }, modifier = Modifier.fillMaxWidth().height(32.dp), shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = LogbookPaleBlue, contentColor = LogbookBlue),
                            border = BorderStroke(0.dp, Color.Transparent), contentPadding = PaddingValues(0.dp)) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp)); Text("Zwischenstopp", fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                    LogbookPanel("Fahrtdetails") {
                        LogbookValueRow(Icons.Default.CalendarMonth, "Datum", tripDate.logbookDate()) { edit("date", tripDate) }
                        LogbookValueRow(Icons.Default.LocationOn, "Immobilie", allProperties.firstOrNull { it.propertyId == propertyId }?.adresse ?: metadata.adresse) { selectProperty = true }
                        HorizontalDivider(color = BorderColor)
                        LogbookValueRow(Icons.Default.Work, "Zweck der Fahrt", purpose) { edit("purpose", purpose) }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(TripRouteMode.HIN_UND_RUECKFAHRT to "Hin & zurück",
                                TripRouteMode.EINFACH to "Nur Hinweg").forEach { (option, label) ->
                                OutlinedButton(onClick = {
                                    mode = option; sameReturnRoute = option == TripRouteMode.HIN_UND_RUECKFAHRT
                                    confirmed = false
                                }, modifier = Modifier.weight(1f).height(32.dp), shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (mode == option) LogbookBlue else SoftBackground,
                                        contentColor = if (mode == option) Color.White else SlateGray),
                                    border = BorderStroke(0.dp, Color.Transparent), contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(if (option == TripRouteMode.HIN_UND_RUECKFAHRT) Icons.Default.SwapHoriz else Icons.Default.ArrowForward,
                                        null, Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp)); Text(label, fontSize = 10.sp, lineHeight = 13.sp)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LogbookMiniMetric(Modifier.weight(1f), "Entfernung",
                            decision.taxDistanceKm?.germanKm() ?: aiKm?.germanKm() ?: "–",
                            if (decision.taxDistanceKm == null) "Nur Schätzung" else if (!confirmed) "Zur Prüfung" else "Bestätigt", Icons.Default.Route) { openStep(1) }
                        LogbookMiniMetric(Modifier.weight(1f), "Werbungskosten",
                            decision.taxDistanceKm?.let { String.format(Locale.GERMANY, "%.2f €", it * 0.30) } ?: "–",
                            "0,30 € je km", LogbookCoins) { openStep(1) }
                    }
                    if (receipt.id > 0) Text("Vorschlag aus Beleg: ${receipt.aussteller}", fontSize = 10.sp, lineHeight = 13.sp, color = SlateGray)
                }
                1 -> {
                    LogbookPanel("Strecke ermitteln und bestätigen") {
                        Text("KI-Werte sind Vorschläge. Für die Buchung ist eine bestätigte Kilometerquelle nötig.",
                            fontSize = 11.sp, lineHeight = 14.sp, color = SlateGray)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = {
                                busy = true
                                scope.launch {
                                    aiKm = viewModel.estimateLogbookRouteDistance(start,
                                        intermediateStops.filter(String::isNotBlank).joinToString(" → "), destination,
                                        when (mode) {
                                            TripRouteMode.EINFACH -> "one_way"
                                            TripRouteMode.HIN_UND_RUECKFAHRT -> "round_trip"
                                            else -> "individual"
                                        })
                                    busy = false
                                    message = if (aiKm == null) "KI-Schätzung nicht verfügbar." else "KI-Strecke ist ein unbestätigter Vorschlag."
                                }
                            }, enabled = !busy && start.isNotBlank() && destination.isNotBlank(),
                                modifier = Modifier.weight(1f)) { Text("KI-Vorschlag", fontSize = 11.sp, lineHeight = 14.sp) }
                            OutlinedButton(onClick = {
                                busy = true
                                scope.launch {
                                    val attempt = viewModel.calculateLogbookRoadDistance(stops, mode, sameReturnRoute)
                                    routeResult = attempt.result
                                    busy = false
                                    message = attempt.errorMessage ?: "Straßenroute berechnet."
                                }
                            }, enabled = !busy && stops.size >= 2,
                                modifier = Modifier.weight(1f)) { Text("Straßenroute", fontSize = 11.sp, lineHeight = 14.sp) }
                        }
                        aiKm?.let { Text("KI-Schätzung: ${it.germanKm()}", fontSize = 11.sp, lineHeight = 14.sp, color = SlateGray) }
                        routeResult?.let { Text("Straßenroute: ${it.distanceKm.germanKm()} · ${it.providerId}", fontSize = 11.sp, lineHeight = 14.sp, color = EmeraldGreen) }
                        matchedStandardRoute?.let { route ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Standardstrecke: ${route.name} (${route.distanceKm.germanKm()})",
                                    modifier = Modifier.weight(1f), fontSize = 11.sp, lineHeight = 14.sp)
                                Switch(checked = useStandardRoute, onCheckedChange = { useStandardRoute = it; confirmed = false })
                            }
                        }
                        OutlinedTextField(manualKmText,
                            { manualKmText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }; confirmed = false },
                            label = { Text("Manuell bestätigte Strecke (km)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(odometerStartText,
                                { odometerStartText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }; confirmed = false },
                                label = { Text("Tacho Start") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(odometerEndText,
                                { odometerEndText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }; confirmed = false },
                                label = { Text("Tacho Ende") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true, modifier = Modifier.weight(1f))
                        }
                        if (manualKmText.isNotBlank()) {
                            Box {
                                OutlinedButton(onClick = { correctionMenuExpanded = true }) {
                                    Text("Korrekturgrund: ${correctionReason.ifBlank { "auswählen" }}", fontSize = 11.sp, lineHeight = 14.sp)
                                }
                                DropdownMenu(expanded = correctionMenuExpanded,
                                    onDismissRequest = { correctionMenuExpanded = false }) {
                                    listOf("Umleitung", "zusätzlicher Termin", "zusätzlicher Zwischenstopp",
                                        "Parkplatzsuche", "abweichende gefahrene Route", "Sonstiges").forEach { reason ->
                                        DropdownMenuItem(text = { Text(reason) }, onClick = {
                                            correctionReason = reason; correctionMenuExpanded = false; confirmed = false
                                        })
                                    }
                                }
                            }
                            OutlinedTextField(correctionNote, { correctionNote = it; confirmed = false },
                                label = { Text(if (correctionReason == "Sonstiges") "Beschreibung (erforderlich)" else "Korrekturhinweis (optional)") },
                                modifier = Modifier.fillMaxWidth())
                        }
                        Text("Quelle: ${decision.source?.name?.replace('_', ' ') ?: "Noch nicht bestätigt"}",
                            fontSize = 11.sp, lineHeight = 14.sp, color = if (decision.source == null) CrimsonRed else EmeraldGreen)
                        decision.warnings.forEach { Text("• $it", fontSize = 10.sp, lineHeight = 13.sp, color = CrimsonRed) }
                        OutlinedButton(onClick = {
                            val km = routeResult?.distanceKm
                            if (km == null) message = "Zuerst eine Straßenroute berechnen."
                            else scope.launch {
                                val now = Instant.now().toString()
                                viewModel.saveStandardRoute(StandardRoute(
                                    name = "${start.substringBefore(',')} → ${destination.substringBefore(',')}",
                                    startAddress = start.trim(), destinationAddress = destination.trim(),
                                    stopsJson = TripStopJson.encode(stops), routeMode = mode.name,
                                    sameReturnRoute = sameReturnRoute, distanceKm = km,
                                    routeSignature = normalizedRoute?.signature.orEmpty(),
                                    sourceProvider = routeResult?.providerId.orEmpty(),
                                    createdAt = now, updatedAt = now
                                ))
                                message = "Als Standardstrecke gespeichert."
                            }
                        }, enabled = routeResult != null && normalizedRoute != null,
                            modifier = Modifier.fillMaxWidth()) { Text("Als Standardstrecke speichern", fontSize = 11.sp, lineHeight = 14.sp) }
                    }
                }
                else -> {
                    LogbookPanel("") {
                        LogbookRoute(start, intermediateStops.filter(String::isNotBlank), destination,
                            onStart = { openStep(0) }, onDestination = { openStep(0) }, onStop = { openStep(0) })
                    }
                    LogbookRouteStatus(decision.source?.name, confirmed, decision.taxDistanceKm)
                    LogbookPanel("Fahrtdaten") {
                        LogbookSummaryRow(Icons.Default.CalendarMonth, "Datum", tripDate.logbookDate()) { openStep(0) }
                        HorizontalDivider(color = BorderColor)
                        LogbookSummaryRow(Icons.Default.SwapHoriz, "Strecke",
                            (decision.taxDistanceKm?.germanKm() ?: "Noch nicht bestätigt") +
                                if (mode == TripRouteMode.HIN_UND_RUECKFAHRT) " (Hin & zurück)" else "") { openStep(1) }
                        HorizontalDivider(color = BorderColor)
                        LogbookSummaryRow(Icons.Default.Work, "Zweck der Fahrt", purpose) { openStep(0) }
                    }
                    LogbookTaxAssignment()
                    LogbookPanel("Beleg (optional)") {
                        LogbookValueRow(Icons.Default.ReceiptLong,
                            if (linkedReceiptId > 0) "Verknüpfter Beleg" else "Beleg verknüpfen",
                            linkedReceipt?.aussteller ?: "Vorhandenen Beleg auswählen.") { selectReceipt = true }
                        if (linkedReceiptId > 0) Text("Verknüpfung lösen", fontSize = 11.sp, color = LogbookBlue,
                            modifier = Modifier.clickable { linkedReceiptId = 0; unit = ""; confirmed = false })
                    }
                    if (decision.taxDistanceKm == null) OutlinedButton(onClick = { openStep(1) },
                        modifier = Modifier.fillMaxWidth().height(32.dp), shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LogbookBlue),
                        contentPadding = PaddingValues(0.dp)) { Text("Strecke bestätigen", fontSize = 11.sp, lineHeight = 14.sp) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = confirmed, onCheckedChange = { confirmed = it }, modifier = Modifier.testTag("logbook_confirmation"),
                            colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = LogbookBlue))
                        Text("Route, Zweck und Kilometer geprüft", fontSize = 10.sp, lineHeight = 13.sp, color = DarkNavy)
                    }
                }
            }
        }
        message?.let { Text(it, fontSize = 10.sp, lineHeight = 13.sp, color = CrimsonRed, modifier = Modifier.padding(vertical = 3.dp)) }
        Button(
            onClick = {
                when (step) {
                    0 -> openStep(2)
                    1 -> openStep(2)
                    else -> {
                        busy = true
                        scope.launch {
                            val result = viewModel.saveLogbookTrip(
                                originalReceipt = (linkedReceipt ?: manualTripReceipt()).copy(wohneinheit = unit, propertyId = propertyId),
                                tripDate = tripDate, propertyId = propertyId, bookingKey = bookingKey, purpose = purpose.trim(),
                                startAddress = start.trim(), destinationAddress = destination.trim(),
                                stops = stops, routeMode = mode, sameReturnRoute = sameReturnRoute,
                                evidence = evidence.copy(manuallyConfirmed = true), routeResult = routeResult,
                                standardRouteId = matchedStandardRoute?.id?.takeIf { useStandardRoute },
                                correctionReason = correctionReason, correctionNote = correctionNote
                            )
                            busy = false
                            message = result.fold(onSuccess = { draftSaved = true; viewModel.clearLogbookDraft(draftKey); onSaved(); "Fahrt gespeichert." },
                                onFailure = { it.message ?: "Fahrt konnte nicht gespeichert werden." })
                        }
                    }
                }
            },
            enabled = if (step == 2) canSave else validTrip && !busy,
            modifier = Modifier.fillMaxWidth().height(44.dp).testTag("book_trip_button_${receipt.id}"),
            colors = ButtonDefaults.buttonColors(containerColor = LogbookBlue),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(when (step) { 0 -> "Fahrt prüfen"; 1 -> "Weiter zur Prüfung"; else -> "Fahrt speichern" },
                fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LogbookMiniMetric(modifier: Modifier, label: String, value: String, hint: String, icon: ImageVector, onClick: () -> Unit) {
    Card(modifier = modifier.height(72.dp).clickable(onClick = onClick), shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
            LogbookIcon(icon)
            Spacer(Modifier.width(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, fontSize = 9.sp, lineHeight = 12.sp, color = SlateGray)
                if (label == "Entfernung") LogbookBadge(hint, LogbookBlue, LogbookPaleBlue)
                Text(value, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                if (label != "Entfernung" && value == "–") Text(hint, fontSize = 9.sp, lineHeight = 12.sp, color = SlateGray)
            }
        }
    }
}

@Composable
private fun LogbookIcon(icon: ImageVector, tint: Color = DarkNavy) {
    Box(Modifier.size(28.dp).background(LogbookPaleBlue, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun LogbookBadge(text: String, tint: Color = EmeraldGreen, background: Color = Color(0xFFD3F0DD)) {
    Row(Modifier.background(background, RoundedCornerShape(50)).padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, null, tint = tint, modifier = Modifier.size(10.dp))
        Spacer(Modifier.width(3.dp)); Text(text, fontSize = 8.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

@Composable
private fun LogbookSteps(step: Int, onStep: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            drawLine(Color(0xFFCCDEF1), Offset(size.width / 6, 12.dp.toPx()),
                Offset(size.width * 5 / 6, 12.dp.toPx()), strokeWidth = 1.dp.toPx())
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("Route", "Details", "Prüfen").forEachIndexed { index, label ->
                Column(Modifier.weight(1f).clickable { onStep(index) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(24.dp).background(if (index == step) LogbookBlue else Color.White, RoundedCornerShape(50))
                        .border(1.dp, if (index == step) LogbookBlue else Color(0xFFCCDEF1), RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center) {
                        Text("${index + 1}", fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold,
                            color = if (index == step) Color.White else DarkNavy)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(label, fontSize = 10.sp, lineHeight = 13.sp, color = if (index == step) LogbookBlue else SlateGray)
                }
            }
        }
    }
}

@Composable
private fun LogbookRoute(start: String, stops: List<String>, destination: String,
    onStart: (() -> Unit)?, onDestination: (() -> Unit)?, onStop: ((Int) -> Unit)?, onRemove: ((Int) -> Unit)? = null) {
    val addresses = listOf(start) + stops + destination
    addresses.forEachIndexed { index, value ->
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(24.dp).height(40.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                if (index > 0) drawLine(DarkNavy, Offset(x, 0f), Offset(x, 12.dp.toPx()),
                    strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())))
                if (index < addresses.lastIndex) drawLine(DarkNavy, Offset(x, 24.dp.toPx()), Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())))
                if (index == 0) drawCircle(LogbookBlue, radius = 6.dp.toPx(), center = Offset(x, 18.dp.toPx()))
                else if (index < addresses.lastIndex) {
                    drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(x, 18.dp.toPx()))
                    drawCircle(LogbookBlue, radius = 3.dp.toPx(), center = Offset(x, 18.dp.toPx()))
                }
            }
            if (index == addresses.lastIndex) Icon(Icons.Default.LocationOn, null, tint = DarkNavy,
                modifier = Modifier.align(Alignment.Center).size(18.dp))
            }
            Column(Modifier.weight(1f).clickable(enabled = onStart != null || onDestination != null || onStop != null) {
                when (index) { 0 -> onStart?.invoke(); addresses.lastIndex -> onDestination?.invoke(); else -> onStop?.invoke(index - 1) }
            }.padding(start = 6.dp, end = 4.dp)) {
                Text(when (index) { 0 -> "Startadresse"; addresses.lastIndex -> "Zieladresse"; else -> "Zwischenstopp $index" },
                    fontSize = 9.sp, lineHeight = 12.sp, color = SlateGray)
                Text(value.ifBlank { "Antippen und eingeben" }, fontSize = 12.sp, lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold, color = DarkNavy, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (index < addresses.lastIndex) HorizontalDivider(Modifier.padding(top = 4.dp), color = BorderColor)
            }
            if (onStart != null || onDestination != null || onStop != null) Icon(Icons.Default.ChevronRight, null, tint = SlateGray, modifier = Modifier.size(16.dp).clickable {
                when (index) { 0 -> onStart?.invoke(); addresses.lastIndex -> onDestination?.invoke(); else -> onStop?.invoke(index - 1) }
            })
            if (index in 1 until addresses.lastIndex && onRemove != null) Text("×", color = SlateGray,
                modifier = Modifier.clickable { onRemove(index - 1) }.padding(horizontal = 6.dp))
        }
    }
}

@Composable
private fun LogbookSummaryRow(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = DarkNavy, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 9.sp, lineHeight = 12.sp, color = SlateGray, modifier = Modifier.width(80.dp))
        Text(value, fontSize = 10.sp, lineHeight = 13.sp, color = DarkNavy, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (onClick != null) Icon(Icons.Default.ChevronRight, null, tint = SlateGray, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun LogbookRouteStatus(source: String?, confirmed: Boolean, km: Double?) {
    val verified = source != null && confirmed
    val tint = if (verified) EmeraldGreen else WarmOrange
    Card(colors = CardDefaults.cardColors(containerColor = if (verified) Color(0xFFE3F6EB) else Color(0xFFFFF4E5)),
        shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(if (verified) "Strecke bestätigt" else "Strecke noch nicht bestätigt", fontWeight = FontWeight.Bold,
                    fontSize = 11.sp, lineHeight = 14.sp, color = tint)
                Text(if (source == null) "Kilometerquelle ergänzen und Strecke prüfen." else "${when (source) { "MANUELL" -> "Manuell bestätigt"; "ROUTE_BERECHNET" -> "Straßenroute"; "TACHO" -> "Tachostand"; "STANDARDSTRECKE" -> "Standardstrecke"; "GPS_GEMESSEN" -> "GPS-Messung"; else -> "Geprüfte Strecke" }} · ${km?.germanKm()}",
                    fontSize = 9.sp, lineHeight = 12.sp, color = SlateGray)
            }
            if (verified) LogbookBadge(if (source == "ROUTE_BERECHNET") "Route geprüft" else "Bestätigt")
        }
    }
}

@Composable
private fun LogbookTaxAssignment() {
    LogbookPanel("Steuerliche Zuordnung") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogbookIcon(LogbookCoins)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Als Werbungskosten erfassen", fontSize = 11.sp, lineHeight = 14.sp, color = DarkNavy)
                Text("Der Betrag wird automatisch Ihrer Steuerübersicht zugeordnet.", fontSize = 9.sp, lineHeight = 12.sp, color = SlateGray)
            }
            // This reflects the existing fixed booking policy; it is intentionally read-only.
            Switch(checked = true, onCheckedChange = null, modifier = Modifier.height(28.dp).scale(0.65f),
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = LogbookBlue, checkedThumbColor = Color.White))
        }
    }
}
