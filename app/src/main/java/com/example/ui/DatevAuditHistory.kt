package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ExportAuditRun
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Display boundary only: historical raw audit codes and ordering remain untouched. */
internal object DatevAuditPresentation {
    const val PREVIEW_LIMIT = 5
    fun statusLabel(status: String): String = when (status) {
        "SUCCESS" -> "Erfolgreich"
        "CREATED_NOT_READY" -> "Erstellt · noch nicht bereit"
        "CANCELLED" -> "Abgebrochen"
        "FAILED" -> "Fehlgeschlagen"
        else -> "Status unbekannt"
    }

    fun dateLabel(timestamp: Long): String = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd.MM.uuuu · HH:mm", Locale.GERMANY))

    fun preview(runs: List<ExportAuditRun>): List<ExportAuditRun> = runs.take(PREVIEW_LIMIT)
    fun hasMore(runs: List<ExportAuditRun>): Boolean = runs.size > PREVIEW_LIMIT
}

@Composable
internal fun DatevAuditPreview(runs: List<ExportAuditRun>, onShowAll: () -> Unit) {
    Text("Revisionssichere Exporthistorie (${runs.size} Läufe):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
    if (runs.isEmpty()) {
        DatevAuditEmptyState()
    } else {
        if (DatevAuditPresentation.hasMore(runs)) {
            Text("Vorschau · die neuesten fünf Läufe", fontSize = 11.sp, color = SlateGray)
            OutlinedButton(onClick = onShowAll, modifier = Modifier.fillMaxWidth().testTag("datev_audit_show_all"),
                shape = Ui2.controlShape) { Text("Alle anzeigen (${runs.size})") }
        }
        DatevAuditPresentation.preview(runs).forEach { DatevAuditRunCard(it) }
    }
}

@Composable
internal fun DatevAuditHistory(runs: List<ExportAuditRun>) {
    // The DAO already orders timestamp DESC. Do not re-sort or re-load historical audits here.
    LazyColumn(modifier = Modifier.fillMaxSize().testTag("datev_audit_history"),
        contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Alle Exportläufe (${runs.size}) · neuester zuerst", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        if (runs.isEmpty()) item { DatevAuditEmptyState() }
        items(runs, key = { it.exportlaufId }) { DatevAuditRunCard(it) }
    }
}

@Composable
private fun DatevAuditEmptyState() {
    Text("Bisher keine protokollierten Exportläufe vorhanden.", fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DatevAuditRunCard(run: ExportAuditRun) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = Ui2.shape, modifier = Modifier.fillMaxWidth().testTag("datev_audit_${run.exportlaufId}")) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(run.exportlaufId, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(DatevAuditPresentation.statusLabel(run.status), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = when (run.status) {
                    "SUCCESS" -> Color(0xFF166534)
                    "FAILED" -> CrimsonRed
                    "CREATED_NOT_READY" -> WarmOrange
                    else -> SlateGray
                }, modifier = Modifier.testTag("datev_audit_status_${run.exportlaufId}"))
            Text(DatevAuditPresentation.dateLabel(run.timestamp), fontSize = 11.sp, color = SlateGray)
            Text("${run.kanzleiprofilNameVersion} | ${run.bookingCount} Sätze | ${String.format(Locale.GERMANY, "%.2f", run.totalAmount)} EUR", fontSize = 11.sp)
            if (run.zipFileName.isNotBlank()) Text("Datei: ${run.zipFileName}", fontSize = 11.sp)
            Text("SHA-256: ${run.zipSha256.take(20)}...", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
