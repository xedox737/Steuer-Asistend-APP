package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.OfflineBackupHistory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Presentation only. Password input deliberately never enters saved state. */
@Composable
internal fun EncryptedBackupControls(
    state: OfflineBackupController.State,
    blocked: Boolean = false,
    onFolder: () -> Unit,
    onConfigure: (CharArray, CharArray, Boolean) -> Unit,
    onManualCredentials: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onReload: () -> Unit,
) {
    var passwordDialog by remember { mutableStateOf(false) }
    val enabled = state.loaded && !state.busy && !blocked
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (state.configured) "Wiederherstellungspasswort eingerichtet" else "Wiederherstellungspasswort noch nicht eingerichtet")
        state.keyProblem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = { passwordDialog = true }, enabled = enabled && state.keyProblem == null,
            modifier = Modifier.testTag("offline_backup_password_setup")) {
            Text(if (state.configured) "Passwort ändern" else "Passwort einrichten")
        }
        Text(state.folderLabel)
        OutlinedButton(onClick = onFolder, enabled = enabled, modifier = Modifier.testTag("offline_backup_folder")) {
            Text("Sicherungsordner wählen")
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .testTag("offline_backup_include_credentials")
            .toggleable(state.config.manualCredentials, enabled = enabled, role = Role.Checkbox, onValueChange = onManualCredentials),
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.config.manualCredentials, onCheckedChange = null)
            Text("Persönliche API-Schlüssel verschlüsselt mitsichern (nur manuelle Sicherungen)", Modifier.weight(1f))
        }
        Button(onClick = onSave, enabled = state.canSave && !blocked, modifier = Modifier.testTag("offline_backup_export")) {
            Text("Vollsicherung speichern")
        }
        if (state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth().testTag("offline_backup_progress"))
            if (state.operation != OfflineBackupController.Operation.LOADING) {
                TextButton(onClick = onCancel, modifier = Modifier.testTag("offline_backup_cancel")) { Text("Abbrechen") }
            }
        }
        BackupHistorySummary(state)
        state.status?.let { Text(it, Modifier.testTag("offline_backup_v2_status")) }
        if (!state.loaded && !state.busy || state.keyProblem != null && !state.busy) {
            TextButton(onClick = onReload, enabled = !blocked) { Text("Erneut prüfen") }
        }
    }
    if (passwordDialog) RecoveryPasswordDialog(change = state.configured,
        onDismiss = { passwordDialog = false }, onConfirm = { password, confirmation ->
            passwordDialog = false
            onConfigure(password, confirmation, state.configured)
        })
}

@Composable
private fun BackupHistorySummary(state: OfflineBackupController.State) {
    val context = LocalContext.current
    state.historyProblem?.let { Text(it, Modifier.testTag("offline_backup_history_error"), color = MaterialTheme.colorScheme.error) }
    val history = state.history ?: return
    fun time(event: OfflineBackupHistory.Event): String = try {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(event.timeMillis))
    } catch (_: Exception) { "Zeit nicht verfügbar" }
    fun outcome(event: OfflineBackupHistory.Event): String = when (event.outcome) {
        OfflineBackupHistory.Outcome.STARTED -> "begonnen; Abschluss nicht bestätigt"
        OfflineBackupHistory.Outcome.VERIFIED -> "geprüft abgeschlossen"
        OfflineBackupHistory.Outcome.FAILED -> when (event.failure) {
            OfflineBackupHistory.Failure.UNAVAILABLE -> "fehlgeschlagen: Ziel oder Zugriff nicht verfügbar"
            OfflineBackupHistory.Failure.STORAGE -> "fehlgeschlagen: Speicher- oder Schreibfehler"
            else -> "fehlgeschlagen"
        }
        OfflineBackupHistory.Outcome.CANCELLED -> "abgebrochen"
        OfflineBackupHistory.Outcome.INTERRUPTED -> "unterbrochen"
        OfflineBackupHistory.Outcome.RESTORED -> "abgeschlossen"
        OfflineBackupHistory.Outcome.ROLLED_BACK -> "zurückgesetzt"
    }
    val success = history.lastFullSuccess
    if (success == null) Text("Noch keine geprüfte Vollsicherung.", Modifier.testTag("offline_backup_history_success"))
    else {
        Text("Letzte geprüfte Vollsicherung: ${time(success)}", Modifier.testTag("offline_backup_history_success"))
        Text("Ziel: ${state.lastSuccessTargetLabel ?: "Zielname nicht verfügbar"} · ${android.text.format.Formatter.formatFileSize(context, requireNotNull(success.bytes))} · ${success.fileCount} Dateien · ${success.rowCount} Datensätze")
    }
    history.lastAttempt?.let { event ->
        val kind = if (event.kind == OfflineBackupHistory.Kind.PARTIAL) "Teilsicherung" else "Vollsicherung"
        Text("Letzter Versuch ($kind): ${outcome(event)} · ${time(event)}", Modifier.testTag("offline_backup_history_attempt"))
    }
    history.lastPartial?.let { Text("Letzte geprüfte Teilsicherung: ${time(it)}", Modifier.testTag("offline_backup_history_partial")) }
    history.lastRestore?.let { Text("Letzte Wiederherstellung: ${outcome(it)} · ${time(it)}", Modifier.testTag("offline_backup_history_restore")) }
}

@Composable
private fun RecoveryPasswordDialog(change: Boolean, onDismiss: () -> Unit, onConfirm: (CharArray, CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val valid = password.length in 16..1024 && password == confirmation
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (change) "Wiederherstellungspasswort ändern" else "Wiederherstellungspasswort einrichten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ohne dieses Passwort ist eine Wiederherstellung nicht möglich. Bitte außerhalb der App sicher aufbewahren.")
                if (change) Text("Ältere Archive benötigen weiterhin ihr bisheriges Passwort.")
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Passwort") },
                    modifier = Modifier.testTag("offline_backup_password"), singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    isError = password.length > 1024,
                    supportingText = { Text(if (password.length > 1024) "Höchstens 1024 Zeichen" else "Mindestens 16 Zeichen") })
                OutlinedTextField(value = confirmation, onValueChange = { confirmation = it }, label = { Text("Passwort bestätigen") },
                    modifier = Modifier.testTag("offline_backup_password_confirmation"), singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    isError = confirmation.isNotEmpty() && password != confirmation,
                    supportingText = { if (confirmation.isNotEmpty() && password != confirmation) Text("Die Passwörter stimmen nicht überein.") })
            }
        },
        confirmButton = { TextButton(enabled = valid, modifier = Modifier.testTag("offline_backup_password_confirm"), onClick = {
            val ownedPassword = password.toCharArray()
            val ownedConfirmation = confirmation.toCharArray()
            password = ""; confirmation = ""
            onConfirm(ownedPassword, ownedConfirmation)
        }) { Text("Einrichten") } },
        dismissButton = { TextButton(onClick = { password = ""; confirmation = ""; onDismiss() }) { Text("Abbrechen") } })
}
