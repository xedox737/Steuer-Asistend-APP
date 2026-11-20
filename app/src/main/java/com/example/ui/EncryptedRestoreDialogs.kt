package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.OfflineBackupRestoreInspector
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun EncryptedRestorePasswordDialog(onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Sicherung entschlüsseln") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Bitte das Wiederherstellungspasswort dieser Sicherung eingeben. Der aktuelle Bestand wird bei der Prüfung nicht verändert.")
            OutlinedTextField(value = password, onValueChange = { password = it }, singleLine = true,
                modifier = Modifier.testTag("offline_restore_password"), label = { Text("Wiederherstellungspasswort") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                isError = password.length > 1024,
                supportingText = { if (password.length > 1024) Text("Höchstens 1024 Zeichen") })
        }
    }, confirmButton = {
        TextButton(enabled = password.length in 16..1024, modifier = Modifier.testTag("offline_restore_password_confirm"), onClick = {
            val owned = password.toCharArray(); password = ""; onConfirm(owned)
        }) { Text("Sicherung prüfen") }
    }, dismissButton = { TextButton(onClick = { password = ""; onDismiss() }) { Text("Abbrechen") } })
}

@Composable
internal fun EncryptedRestorePreviewDialog(preview: OfflineBackupRestoreInspector.Preview, onClose: () -> Unit,
    onRestore: (Boolean) -> Boolean) {
    val context = LocalContext.current
    var confirmation by remember(preview.backupId) { mutableStateOf(false) }
    var includeCredentials by remember(preview.backupId) { mutableStateOf(false) }
    val created = remember(preview.archiveInfo.createdAt) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())
            .format(Instant.parse(preview.archiveInfo.createdAt))
    }
    AlertDialog(onDismissRequest = onClose, modifier = Modifier.testTag("offline_restore_preview"),
        title = { Text("Vollsicherung geprüft") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Erstellt: $created")
                Text("${preview.rowCounts["property_metadata"] ?: 0} Immobilien · ${preview.rowCounts["receipts"] ?: 0} Belege")
                Text("${preview.rowCounts.values.sum()} Datensätze in ${preview.rowCounts.size} Tabellen")
                Text("${preview.fileVersions} Dateiversionen · ${android.text.format.Formatter.formatFileSize(context, preview.originalBytes)} Originaldaten")
                Text(if (preview.credentialProviders.isEmpty()) "Keine persönlichen API-Schlüssel enthalten."
                    else "Persönliche API-Schlüssel enthalten: ${preview.credentialProviders.joinToString { it.name }}")
                Text("Die Prüfung hat noch keine Daten verändert.")
            }
        }, confirmButton = { Button(onClick = { includeCredentials = false; confirmation = true }, modifier = Modifier.testTag("offline_restore_replace")) { Text("Vollständig wiederherstellen") } },
        dismissButton = { TextButton(onClick = onClose, modifier = Modifier.testTag("offline_restore_preview_close")) { Text("Schließen") } })
    if (confirmation) AlertDialog(onDismissRequest = { confirmation = false },
        modifier = Modifier.testTag("offline_restore_replace_confirmation"),
        title = { Text("Aktuellen Bestand ersetzen?") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Die vollständige Wiederherstellung ersetzt den aktuellen fachlichen Bestand durch diese Sicherung. Vorher wird ein lokaler Rückfallstand erstellt. Die automatische Google-Drive-Synchronisierung bleibt anschließend ausgeschaltet.")
            if (preview.credentialProviders.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().testTag("offline_restore_include_credentials")
                    .toggleable(includeCredentials, role = Role.Checkbox, onValueChange = { includeCredentials = it }),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeCredentials, onCheckedChange = null)
                    Text("Enthaltene persönliche API-Schlüssel ebenfalls übernehmen")
                }
            }
        } },
        confirmButton = { Button(onClick = { if (onRestore(includeCredentials)) confirmation = false },
            modifier = Modifier.testTag("offline_restore_replace_confirm")) { Text("Bestand ersetzen") } },
        dismissButton = { TextButton(onClick = { confirmation = false }, modifier = Modifier.testTag("offline_restore_replace_cancel")) { Text("Abbrechen") } })
}
