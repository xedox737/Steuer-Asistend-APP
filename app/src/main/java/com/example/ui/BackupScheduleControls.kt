package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import com.example.data.OfflineBackupSchedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Job

@Composable
internal fun BackupScheduleControls(
    state: OfflineBackupController.State,
    blocked: Boolean,
    save: (Boolean, OfflineBackupSchedule.Rule, Boolean, Int) -> Job?,
) {
    var editing by remember { mutableStateOf(false) }
    val schedule = state.schedule
    val config = schedule?.config
    val zone = ZoneId.systemDefault()
    val locale = Locale.getDefault()
    val dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
    Text(if (config?.enabled == true) "Automatische Vollsicherung aktiv" else "Automatische Vollsicherung aus")
    state.scheduleProblem?.let { Text(it) }
    if (config?.enabled == true) BackupNotificationPermission()

    schedule?.next?.let { Text("Geplant ab ${dateFormat.format(Instant.ofEpochMilli(it.dueMillis))}. Android kann den Start verzögern.") }
    schedule?.active?.let { Text("Automatische Sicherung läuft seit ${dateFormat.format(Instant.ofEpochMilli(it.startedMillis))}") }
    OutlinedButton(onClick = { editing = true },
        enabled = !blocked && !state.busy && state.scheduleProblem == null && (state.canSave || config?.enabled == true)) {
        Text("Zeitplan bearbeiten")
    }
    if (!editing) return

    var time by remember { mutableStateOf(config?.rule?.time?.toString() ?: "07:00") }
    var day by remember { mutableStateOf(config?.rule?.day) }
    var credentials by remember { mutableStateOf(config?.automaticCredentials == true) }
    var warningDays by remember { mutableStateOf((config?.warningDays ?: 7).toString()) }
    val warning = warningDays.toIntOrNull()?.takeIf { it in 1..90 }
    val parsed = time.takeIf { it.matches(Regex("[0-9]{2}:[0-9]{2}")) }
        ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    val cadence = if (day == null) OfflineBackupSchedule.Cadence.DAILY else OfflineBackupSchedule.Cadence.WEEKLY
    AlertDialog(
        onDismissRequest = { editing = false },
        title = { Text("Automatische Vollsicherung") },
        text = {
            Column {
                Text("Sicherungsordner für diesen Zeitplan: ${state.folderLabel}")
                OutlinedTextField(value = time, onValueChange = { if (it.length <= 5) time = it },
                    label = { Text("Ortszeit (HH:MM)") }, singleLine = true)
                TextButton(onClick = { day = when (day) {
                    null -> DayOfWeek.MONDAY
                    DayOfWeek.SUNDAY -> null
                    else -> day?.plus(1)
                } }) {
                    Text(day?.let { "Wöchentlich am ${it.getDisplayName(TextStyle.FULL, locale)}" } ?: "Täglich")
                }
                OutlinedTextField(value = warningDays, onValueChange = { if (it.length <= 2) warningDays = it },
                    label = { Text("Warnen nach Tagen ohne Vollsicherung (1–90)") },
                    singleLine = true, isError = warning == null)
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = credentials, onCheckedChange = { credentials = it })
                    Text("Persönliche API-Schlüssel automatisch mitsichern")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null && warning != null && state.canSave && !blocked, onClick = {
                val rule = OfflineBackupSchedule.Rule(cadence, requireNotNull(parsed), day, zone)
                if (save(true, rule, credentials, requireNotNull(warning)) != null) editing = false
            }) { Text("Zeitplan speichern") }
        },
        dismissButton = {
            if (config?.enabled == true) {
                TextButton(enabled = !state.busy && !blocked, onClick = {
                    if (save(false, config.rule, false, config.warningDays) != null) editing = false
                }) { Text("Deaktivieren") }
            } else TextButton(onClick = { editing = false }) { Text("Abbrechen") }
        },
    )
}

@Composable
private fun BackupNotificationPermission() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    if (!allowed) {
        Text("Android-Benachrichtigungen für Sicherungsprobleme sind ausgeschaltet.")
        if (Build.VERSION.SDK_INT >= 33) {
            TextButton(onClick = { request.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text("Sicherungsbenachrichtigungen erlauben")
            }
        }
    }
}
