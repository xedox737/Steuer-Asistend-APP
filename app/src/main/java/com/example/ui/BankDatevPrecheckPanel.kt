package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BankDatevPrecheckPolicy
import com.example.data.BankTransaction

@Composable
internal fun BankDatevPrecheckPanel(transactions: List<BankTransaction>) {
    val summary = remember(transactions) { BankDatevPrecheckPolicy.summarize(transactions) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "DATEV-Vorprüfung Bankbuchungen",
            fontWeight = FontWeight.Bold,
            color = DarkNavy
        )
        Text(
            "Diese Ansicht prüft nur den Bankstatus. Die endgültige Exportfreigabe erfolgt zusätzlich über Kontierung und DATEV-Exportpolicy.",
            fontSize = 11.sp,
            color = SlateGray
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("Geprüft", summary.checked)
                    Metric("Potenziell bereit", summary.potentiallyExportable)
                    Metric("Ungeklärt", summary.unresolved)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("Privat", summary.privateIgnored)
                    Metric("Umbuchung", summary.transfers)
                    Metric("Kein Beleg nötig", summary.noReceiptRequired)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("Erledigt", summary.done)
                }
            }
        }

        if (summary.reasons.isEmpty()) {
            Text("Keine Ausschluss- oder Prüfgründe in der aktuellen Auswahl.", fontSize = 11.sp, color = SlateGray)
        } else {
            Text("Warum nicht exportiert?", fontWeight = FontWeight.SemiBold, color = DarkNavy)
            summary.reasons.take(20).forEachIndexed { index, reason ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(reason.code, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text(reason.message, fontSize = 11.sp, color = SlateGray)
                    Text("Buchung: ${reason.transactionId.take(14)}…", fontSize = 9.sp, color = SlateGray)
                }
                if (index != summary.reasons.take(20).lastIndex) HorizontalDivider(color = BorderColor)
            }
            if (summary.reasons.size > 20) {
                Spacer(Modifier.height(2.dp))
                Text("+ ${summary.reasons.size - 20} weitere Prüfgründe", fontSize = 10.sp, color = SlateGray)
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: Int) {
    Column {
        Text(value.toString(), fontWeight = FontWeight.Bold, color = DarkNavy)
        Text(label, fontSize = 9.sp, color = SlateGray)
    }
}
