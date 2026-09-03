package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import java.util.Locale
import kotlinx.coroutines.launch

private fun Double.germanKm(): String = String.format(Locale.GERMANY, "%.1f km", this)

@Composable
fun LogbookScreen(viewModel: ReceiptViewModel) {
    val receipts by viewModel.receipts.collectAsState()
    val metadata by viewModel.propertyMetadata.collectAsState()
    val effectiveMetadata = metadata ?: PropertyMetadata()
    val trips by viewModel.logbookTrips.collectAsState()
    val routes by viewModel.standardRoutes.collectAsState()
    val suggested = receipts.filter { receipt ->
        val vendor = receipt.aussteller.lowercase(Locale.GERMANY)
        val category = receipt.hauptkategorie.lowercase(Locale.GERMANY)
        val description = receipt.beschreibung.lowercase(Locale.GERMANY)
        val likelyTravel = listOf("obi", "hornbach", "bauhaus", "toom", "hagebau", "baumarkt").any(vendor::contains) ||
            category.contains("sanierung") || description.contains("material")
        likelyTravel && receipt.datum.matches(Regex("""\d{4}-\d{2}-\d{2}""")) &&
            trips.none { it.sourceReceiptId == receipt.id }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(SoftBackground)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("KI-Fahrtenbuch 2.0", fontSize = 22.sp, fontWeight = FontWeight.Black, color = DarkNavy)
        Text(
            "Die KI erkennt Belegdatum, Händler, Objektbezug und einen möglichen Fahrtzweck. " +
                "Ihre Kilometerangabe bleibt ein gekennzeichneter Vorschlag und wird nie allein steuerlich eingebucht.",
            fontSize = 12.sp, color = SlateGray
        )
        if (trips.isNotEmpty()) {
            Text("Gespeicherte Fahrten", fontWeight = FontWeight.Bold, color = DarkNavy)
            trips.take(5).forEach { SavedTripCard(it) }
        }
        if (routes.isNotEmpty()) StandardRoutesCard(routes, viewModel)
        Text("Fahrtvorschläge aus Belegen", fontWeight = FontWeight.Bold, color = DarkNavy)
        if (suggested.isEmpty()) {
            Text("Keine neuen passenden Belege gefunden.", color = SlateGray, fontSize = 12.sp)
        } else {
            suggested.forEach { LogbookSuggestionCard(it, effectiveMetadata, viewModel) }
        }
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun SavedTripCard(trip: LogbookTrip) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(trip.date, fontWeight = FontWeight.Bold, color = DarkNavy)
                Text(trip.taxDistanceKm.germanKm(), fontWeight = FontWeight.Black, color = EmeraldGreen)
            }
            Text(trip.purpose, fontSize = 12.sp, color = DarkNavy)
            Text("Quelle: ${trip.kilometerSource.replace('_', ' ')}", fontSize = 10.sp, color = SlateGray)
            Text("Status: ${trip.plausibilityStatus.replace('_', ' ')}", fontSize = 10.sp, color = SlateGray)
            Text(trip.stops.joinToString(" → ") { it.label.ifBlank { it.address } }, fontSize = 10.sp, color = SlateGray)
        }
    }
}

@Composable
private fun StandardRoutesCard(routes: List<StandardRoute>, viewModel: ReceiptViewModel) {
    var expanded by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Standardstrecken (${routes.size})", fontWeight = FontWeight.Bold, color = DarkNavy)
                Text(if (expanded) "Schließen" else "Verwalten", color = AccentBlue, fontSize = 11.sp)
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
        message?.let { Text(it, fontSize = 10.sp, color = SlateGray) }
    }
}

@Composable
private fun LogbookSuggestionCard(receipt: Receipt, metadata: PropertyMetadata, viewModel: ReceiptViewModel) {
    val scope = rememberCoroutineScope()
    var start by remember(receipt.id) { mutableStateOf(metadata.wohnort) }
    var intermediateStops by remember(receipt.id) { mutableStateOf(listOf(receipt.aussteller)) }
    var destination by remember(receipt.id) { mutableStateOf(metadata.adresse) }
    var purpose by remember(receipt.id) {
        mutableStateOf(if (receipt.beschreibung.isNotBlank()) "Materialkauf / ${receipt.beschreibung}" else "Materialkauf bei ${receipt.aussteller}")
    }
    var mode by remember(receipt.id) { mutableStateOf(TripRouteMode.INDIVIDUELL) }
    var sameReturnRoute by remember(receipt.id) { mutableStateOf(false) }
    var aiKm by remember(receipt.id) { mutableStateOf<Double?>(null) }
    var routeResult by remember(receipt.id) { mutableStateOf<RouteDistanceResult?>(null) }
    var manualKmText by remember(receipt.id) { mutableStateOf("") }
    var odometerStartText by remember(receipt.id) { mutableStateOf("") }
    var odometerEndText by remember(receipt.id) { mutableStateOf("") }
    var matchedStandardRoute by remember(receipt.id) { mutableStateOf<StandardRoute?>(null) }
    var useStandardRoute by remember(receipt.id) { mutableStateOf(false) }
    var correctionReason by remember(receipt.id) { mutableStateOf("") }
    var correctionNote by remember(receipt.id) { mutableStateOf("") }
    var correctionMenuExpanded by remember(receipt.id) { mutableStateOf(false) }
    var confirmed by remember(receipt.id) { mutableStateOf(false) }
    var message by remember(receipt.id) { mutableStateOf<String?>(null) }
    var busy by remember(receipt.id) { mutableStateOf(false) }

    val normalizedRoute = runCatching {
        TripRouteNormalizer.normalize(
            start,
            intermediateStops.mapIndexed { index, address -> TripStop(address, "Zwischenstopp ${index + 1}", index) },
            destination,
            mode,
            sameReturnRoute
        )
    }.getOrNull()

    LaunchedEffect(normalizedRoute?.signature) {
        routeResult = null
        confirmed = false
        matchedStandardRoute = normalizedRoute?.let { viewModel.findStandardRoute(it.signature) }
        useStandardRoute = matchedStandardRoute != null
    }

    val evidence = DistanceEvidence(
        aiEstimatedKm = aiKm, routedKm = routeResult?.distanceKm,
        odometerStartKm = odometerStartText.replace(',', '.').toDoubleOrNull(),
        odometerEndKm = odometerEndText.replace(',', '.').toDoubleOrNull(),
        standardRouteKm = matchedStandardRoute?.distanceKm?.takeIf { useStandardRoute },
        manualKm = manualKmText.replace(',', '.').toDoubleOrNull(),
        manuallyConfirmed = confirmed,
        correctionReason = correctionReason
    )
    val decision = LogbookDistancePolicy.decide(evidence)
    val stops = normalizedRoute?.stops.orEmpty()

    Card(
        modifier = Modifier.fillMaxWidth().testTag("suggested_trip_card_${receipt.id}"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(receipt.datum, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text("KI-Vorschlag: ${receipt.aussteller}", fontSize = 11.sp, color = AccentBlue)
                    Text("Objekt: ${receipt.wohneinheit.ifBlank { metadata.name }}", fontSize = 10.sp, color = SlateGray)
                }
                decision.taxDistanceKm?.let { Text(it.germanKm(), fontWeight = FontWeight.Black, color = EmeraldGreen) }
            }
            OutlinedTextField(purpose, { purpose = it }, label = { Text("Fahrtzweck") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(start, { start = it; confirmed = false }, label = { Text("Startadresse") }, modifier = Modifier.fillMaxWidth())
            Text("Zwischenstopps", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            intermediateStops.forEachIndexed { index, value ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { changed ->
                            intermediateStops = intermediateStops.toMutableList().also { it[index] = changed }
                        },
                        label = { Text("Zwischenstopp ${index + 1}") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            if (index > 0) intermediateStops = intermediateStops.toMutableList().also {
                                val item = it.removeAt(index); it.add(index - 1, item)
                            }
                        }, enabled = index > 0
                    ) { Text("↑") }
                    OutlinedButton(
                        onClick = {
                            if (index < intermediateStops.lastIndex) intermediateStops = intermediateStops.toMutableList().also {
                                val item = it.removeAt(index); it.add(index + 1, item)
                            }
                        }, enabled = index < intermediateStops.lastIndex
                    ) { Text("↓") }
                    OutlinedButton(onClick = {
                        intermediateStops = intermediateStops.toMutableList().also { it.removeAt(index) }
                    }) { Text("×") }
                }
            }
            OutlinedButton(onClick = { intermediateStops = intermediateStops + "" }) { Text("+ Zwischenstopp") }
            OutlinedTextField(destination, { destination = it; confirmed = false }, label = { Text("Zieladresse") }, modifier = Modifier.fillMaxWidth())

            Text("Fahrtart", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TripRouteMode.entries.forEach { option ->
                    val label = when (option) {
                        TripRouteMode.EINFACH -> "Einfach"
                        TripRouteMode.HIN_UND_RUECKFAHRT -> "Hin/Rück"
                        TripRouteMode.INDIVIDUELL -> "Individuell"
                    }
                    OutlinedButton(
                        onClick = {
                            mode = option
                            sameReturnRoute = option == TripRouteMode.HIN_UND_RUECKFAHRT
                            confirmed = false
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (mode == option) DarkNavy else Color.White,
                            contentColor = if (mode == option) Color.White else DarkNavy
                        )
                    ) { Text(label, fontSize = 10.sp) }
                }
            }
            if (mode == TripRouteMode.HIN_UND_RUECKFAHRT) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Gleiche Rückstrecke annehmen", fontSize = 12.sp, color = DarkNavy)
                        Text("Bei Aktivierung wird die Rückkehr einmal als letzter Wegpunkt berechnet.", fontSize = 9.sp, color = SlateGray)
                    }
                    Switch(checked = sameReturnRoute, onCheckedChange = { checked ->
                        sameReturnRoute = checked
                        if (!checked) mode = TripRouteMode.INDIVIDUELL
                        confirmed = false
                    })
                }
            }
            matchedStandardRoute?.let { route ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Standardstrecke: ${route.name}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(route.distanceKm.germanKm(), fontSize = 10.sp, color = SlateGray)
                    }
                    Switch(checked = useStandardRoute, onCheckedChange = { useStandardRoute = it; confirmed = false })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            val routeType = when {
                                mode == TripRouteMode.EINFACH -> "one_way"
                                mode == TripRouteMode.HIN_UND_RUECKFAHRT && sameReturnRoute -> "round_trip"
                                else -> "individual"
                            }
                            aiKm = viewModel.estimateLogbookRouteDistance(
                                start,
                                intermediateStops.filter(String::isNotBlank).joinToString(" → "),
                                destination,
                                routeType
                            )
                            busy = false
                            message = if (aiKm == null) "KI-Schätzung nicht verfügbar." else "KI-Strecke ist nur ein unbestätigter Vorschlag."
                        }
                    },
                    enabled = !busy && start.isNotBlank() && destination.isNotBlank()
                ) { Text("KI-Vorschlag") }
                OutlinedButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            val attempt = viewModel.calculateLogbookRoadDistance(stops, mode, sameReturnRoute)
                            routeResult = attempt.result
                            busy = false
                            message = attempt.errorMessage ?: "Google-Straßenroute berechnet."
                        }
                    },
                    enabled = !busy && stops.size >= 2
                ) { Text(if (routeResult == null) "Straßenroute berechnen" else "Route neu berechnen") }
            }
            aiKm?.let { Text("KI geschätzt: ${it.germanKm()} (nicht steuerlich verwendbar)", fontSize = 10.sp, color = SlateGray) }
            routeResult?.let {
                Text("Google Straßenroute: ${it.distanceKm.germanKm()}", fontSize = 10.sp, color = EmeraldGreen)
                Text("Provider: ${it.providerId} · berechnet: ${it.calculatedAt}", fontSize = 9.sp, color = SlateGray)
            }

            OutlinedTextField(
                manualKmText,
                { manualKmText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }; confirmed = false },
                label = { Text("Manuell bestätigte Gesamtstrecke (km)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    odometerStartText,
                    { odometerStartText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }; confirmed = false },
                    label = { Text("Tacho Start") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    odometerEndText,
                    { odometerEndText = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }; confirmed = false },
                    label = { Text("Tacho Ende") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
            if (manualKmText.isNotBlank()) {
                Box {
                    OutlinedButton(onClick = { correctionMenuExpanded = true }) {
                        Text("Korrekturgrund: ${correctionReason.ifBlank { "auswählen" }}")
                    }
                    DropdownMenu(expanded = correctionMenuExpanded, onDismissRequest = { correctionMenuExpanded = false }) {
                        listOf(
                            "Umleitung", "zusätzlicher Termin", "zusätzlicher Zwischenstopp",
                            "Parkplatzsuche", "abweichende gefahrene Route", "Sonstiges"
                        ).forEach { reason ->
                            DropdownMenuItem(text = { Text(reason) }, onClick = {
                                correctionReason = reason; correctionMenuExpanded = false; confirmed = false
                            })
                        }
                    }
                }
                OutlinedTextField(
                    correctionNote, { correctionNote = it; confirmed = false },
                    label = { Text(if (correctionReason == "Sonstiges") "Beschreibung (erforderlich)" else "Korrekturhinweis (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                "Steuerliche Quelle: ${decision.source?.name?.replace('_', ' ') ?: "NOCH NICHT BESTÄTIGT"}",
                fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = if (decision.source == null) CrimsonRed else EmeraldGreen
            )
            Text("Plausibilität: ${decision.plausibilityStatus.name.replace('_', ' ')}", fontSize = 10.sp, color = SlateGray)
            decision.warnings.forEach { Text("• $it", fontSize = 10.sp, color = CrimsonRed) }
            Row {
                Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                Text("Route, Zweck und steuerlich verwendete Kilometer geprüft", modifier = Modifier.padding(top = 12.dp), fontSize = 11.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            val result = viewModel.saveLogbookTrip(
                                originalReceipt = receipt, purpose = purpose.trim(),
                                startAddress = start.trim(), destinationAddress = destination.trim(),
                                stops = stops, routeMode = mode, sameReturnRoute = sameReturnRoute,
                                evidence = evidence.copy(manuallyConfirmed = true), routeResult = routeResult,
                                standardRouteId = matchedStandardRoute?.id?.takeIf { useStandardRoute },
                                correctionReason = correctionReason, correctionNote = correctionNote
                            )
                            busy = false
                            message = result.fold(
                                onSuccess = { "Fahrt nachvollziehbar gespeichert." },
                                onFailure = { it.message ?: "Fahrt konnte nicht gespeichert werden." }
                            )
                        }
                    },
                    enabled = !busy && confirmed && decision.taxDistanceKm != null &&
                        decision.source != KilometerSource.KI_GESCHAETZT &&
                        purpose.isNotBlank() && start.isNotBlank() && destination.isNotBlank() &&
                        (!decision.correctionReasonRequired || correctionReason.isNotBlank()) &&
                        (correctionReason != "Sonstiges" || correctionNote.isNotBlank()),
                    modifier = Modifier.fillMaxWidth().testTag("book_trip_button_${receipt.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                ) { Text("Fahrt einbuchen") }
                OutlinedButton(
                    onClick = {
                        val km = routeResult?.distanceKm
                        if (km == null) message = "Zuerst eine bestätigte Kilometerquelle erfassen."
                        else scope.launch {
                            val now = Instant.now().toString()
                            viewModel.saveStandardRoute(
                                StandardRoute(
                                    name = "${start.substringBefore(',')} → ${destination.substringBefore(',')}",
                                    startAddress = start.trim(), destinationAddress = destination.trim(),
                                    stopsJson = TripStopJson.encode(stops), routeMode = mode.name,
                                    sameReturnRoute = sameReturnRoute, distanceKm = km,
                                    routeSignature = normalizedRoute?.signature.orEmpty(),
                                    sourceProvider = routeResult?.providerId.orEmpty(),
                                    createdAt = now, updatedAt = now
                                )
                            )
                            message = "Als Standardstrecke gespeichert."
                        }
                    },
                    enabled = routeResult != null && normalizedRoute != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Als Standardstrecke speichern") }
            }
            message?.let { Text(it, fontSize = 10.sp, color = SlateGray) }
        }
    }
}
