package com.example.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RentIncomeWithTenantHistoryScreen(viewModel: ReceiptViewModel) {
    val context = LocalContext.current
    val units by viewModel.wohneinheitenStatus.collectAsState()
    val rentPrefs = remember(context) { context.getSharedPreferences("rent_plan_prefs", Context.MODE_PRIVATE) }
    var showUnitPicker by remember { mutableStateOf(false) }
    var selectedUnit by remember { mutableStateOf<WohneinheitStatus?>(null) }
    var historyVersion by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        RentIncomeOverviewScreen(viewModel)

        ExtendedFloatingActionButton(
            onClick = { showUnitPicker = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp),
            icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
            text = { Text("Mieterwechsel") },
            containerColor = AccentBlue,
            contentColor = androidx.compose.ui.graphics.Color.White
        )
    }

    if (showUnitPicker) {
        AlertDialog(
            onDismissRequest = { showUnitPicker = false },
            title = { Text("Wohneinheit auswählen", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn {
                    items(units, key = { it.name }) { unit ->
                        Card(
                            onClick = {
                                selectedUnit = unit
                                showUnitPicker = false
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = SoftBackground),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(unit.label, fontWeight = FontWeight.Bold, color = DarkNavy, fontSize = 13.sp)
                                Text(
                                    if (unit.status == "Vermietet") unit.mieter.ifBlank { "Mieter nicht hinterlegt" } else unit.status,
                                    color = SlateGray,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showUnitPicker = false }) { Text("Abbrechen") } }
        )
    }

    selectedUnit?.let { unit ->
        val nk = rentPrefs.getFloat("nk_${unit.name}", 0f).toDouble()
        val other = rentPrefs.getFloat("other_${unit.name}", 0f).toDouble()
        TenantHistoryDialog(
            unit = unit,
            nebenkostenCurrent = nk,
            sonstigeCurrent = other,
            onDismiss = { selectedUnit = null },
            onCurrentTenantChanged = { newPeriod ->
                viewModel.updateWohneinheit(
                    unit.copy(
                        status = "Vermietet",
                        mieter = newPeriod.tenantName,
                        kaltmiete = newPeriod.kaltmiete,
                        mietvertragsstart = newPeriod.startDate
                    )
                )
                rentPrefs.edit()
                    .putFloat("nk_${unit.name}", newPeriod.nebenkosten.toFloat())
                    .putFloat("other_${unit.name}", newPeriod.sonstige.toFloat())
                    .apply()
            },
            onHistoryChanged = { historyVersion++ }
        )
    }
}
