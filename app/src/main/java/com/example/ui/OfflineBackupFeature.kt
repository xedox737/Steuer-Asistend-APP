package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.OfflineBackupService
import com.example.data.OfflineBackupValidationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

@Composable
internal fun OfflineBackupCard(viewModel: ReceiptViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember(viewModel) { viewModel.offlineBackupServiceForUi() }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var prepared by remember { mutableStateOf<OfflineBackupService.Prepared?>(null) }
    DisposableEffect(Unit) { onDispose { prepared?.close() } }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null && !busy) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    val temporary = File.createTempFile("offline-backup-", ".zip", context.cacheDir)
                    try {
                        temporary.outputStream().use { service.create(it) }
                        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Zieldatei konnte nicht geöffnet werden." }.use { output ->
                            temporary.inputStream().use { it.copyTo(output) }
                        }
                    } finally { temporary.delete() }
                }
                status = "Vollständige lokale Sicherung gespeichert."
            } catch (error: CancellationException) { throw error
            } catch (error: OfflineBackupValidationException) { status = error.message
            } catch (error: Exception) { status = "Die lokale Sicherung konnte nicht gespeichert werden. Bitte prüfe den Speicherort und versuche es erneut."
            } finally { busy = false }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) scope.launch {
            busy = true
            try {
                prepared?.close(); prepared = null
                prepared = withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openInputStream(uri)) { "Sicherung konnte nicht geöffnet werden." }.use { service.inspect(it) }
                }
                status = null
            } catch (error: CancellationException) { throw error
            } catch (error: OfflineBackupValidationException) { status = error.message
            } catch (error: Exception) { status = "Die Sicherung konnte nicht gelesen werden. Bitte wähle eine vollständige ImmoPilot-Vollsicherung als ZIP-Datei. Es wurden keine Daten übernommen."
            } finally { busy = false }
        }
    }
    Card(Modifier.fillMaxWidth(), shape = Ui2.shape, border = BorderStroke(1.dp, BorderColor),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
        Column(Modifier.padding(Ui2.padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Lokale Vollsicherung", color = DarkNavy)
            Text("Belege, Originaldateien, Immobilien, Mieten, Bank, AfA, Fahrtenbuch und Einstellungen – ohne Google-Konto. Zugangsschlüssel werden ausgelassen.", color = SlateGray)
            OutlinedButton(onClick = { export.launch("ImmoPilot-Sicherung-${LocalDate.now()}.zip") }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("offline_backup_export")) { Text("Vollsicherung speichern") }
            OutlinedButton(onClick = { import.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("offline_backup_import")) { Text("Lokale Sicherung prüfen") }
            if (busy) Text("Sicherung wird verarbeitet …")
            status?.let { Text(it, modifier = Modifier.testTag("offline_backup_status")) }
        }
    }
    prepared?.let { snapshot ->
        AlertDialog(onDismissRequest = { if (!busy) { snapshot.close(); prepared = null } },
            title = { Text("Lokale Sicherung wiederherstellen?") },
            text = { Text("${snapshot.propertyCount} Immobilien · ${snapshot.receiptCount} Belege · ${snapshot.fileCount} Originaldateien\n\nFehlende Daten werden ergänzt. Abweichende bestehende Datensätze werden nicht überschrieben; bei einem Konflikt wird die Übernahme vollständig abgebrochen. Für einen Gerätewechsel am besten auf einem leeren App-Bestand wiederherstellen.") },
            confirmButton = { Button(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { service.restore(snapshot); viewModel.refreshPreferencesAfterRestore() }
                        status = "Lokale Sicherung wiederhergestellt. Vorhandene identische Daten wurden nicht doppelt angelegt."
                        snapshot.close(); prepared = null
                    } catch (error: CancellationException) { throw error
                    } catch (error: OfflineBackupValidationException) { status = error.message
                    } catch (error: Exception) { status = "Die Wiederherstellung konnte nicht abgeschlossen werden. Bitte prüfe die Sicherungsdatei und versuche es erneut."
                    } finally { busy = false }
                }
            }) { Text("Wiederherstellen") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { snapshot.close(); prepared = null }) { Text("Abbrechen") } }
        )
    }
}
