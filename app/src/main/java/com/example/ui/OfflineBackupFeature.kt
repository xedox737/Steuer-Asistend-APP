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
import com.example.data.OfflineBackupRestoreInspector
import com.example.data.OfflineBackupService
import com.example.data.OfflineBackupValidationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

@Composable
internal fun OfflineBackupCard(viewModel: ReceiptViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember(viewModel) { viewModel.offlineBackupServiceForUi() }
    val controller = remember(viewModel) { viewModel.offlineBackupControllerForUi() }
    val backupState by controller.state.collectAsState()
    var legacyOptions by remember { mutableStateOf(false) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) controller.selectFolder(uri)
    }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var exportArchive by remember { mutableStateOf<File?>(null) }
    var prepared by remember { mutableStateOf<OfflineBackupService.Prepared?>(null) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var inspected by remember { mutableStateOf<OfflineBackupRestoreInspector.Prepared?>(null) }
    var inspectStatus by remember { mutableStateOf<String?>(null) }
    var inspectJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) { onDispose {
        inspectJob?.cancel()
        val owned = inspected
        if (owned != null) scope.launch(Dispatchers.IO + NonCancellable) {
            try { owned.close() } catch (_: Exception) {
                com.example.util.DiagnosticLog.e("OfflineBackup", "Private restore inspection cleanup failed")
            }
        }
        prepared?.close(); exportArchive?.delete()
    } }
    fun closeInspected() {
        val owned = inspected ?: return
        inspected = null
        busy = true
        scope.launch(Dispatchers.IO + NonCancellable) {
            try { owned.close() }
            catch (_: Exception) { inspectStatus = "Die privaten Prüfdaten konnten nicht vollständig entfernt werden." }
            finally { busy = false }
        }
    }
    val inspectPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy && !backupState.busy && inspected == null) restoreUri = uri
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val temporary = exportArchive
        if (uri == null || temporary == null) {
            temporary?.delete(); exportArchive = null; busy = false
            if (uri != null) exportStatus = "Die vorbereitete Sicherung ist nicht mehr verfügbar. Bitte erneut speichern."
        } else scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Zieldatei konnte nicht geöffnet werden." }.use { output ->
                        temporary.inputStream().use { it.copyTo(output) }
                    }
                    requireNotNull(context.contentResolver.openInputStream(uri)) { "Zieldatei konnte nicht geprüft werden." }
                        .use { service.inspect(it).close() }
                }
                exportStatus = "Vollständige lokale Sicherung gespeichert."
            } catch (error: CancellationException) { throw error
            } catch (error: OfflineBackupValidationException) { exportStatus = error.message
            } catch (error: Exception) { exportStatus = "Die lokale Sicherung konnte nicht gespeichert und geprüft werden. Die Zieldatei ist möglicherweise unvollständig. Bitte prüfe den Speicherort und versuche es erneut."
            } finally { temporary.delete(); exportArchive = null; busy = false }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy && !backupState.busy) scope.launch {
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
            Text("Belege, Originaldateien, Immobilien, Mieten, Bank, AfA, Fahrtenbuch und Einstellungen – ohne Google-Konto. Neue Sicherungen sind passwortgeschützt. Persönliche API-Schlüssel werden nur mit ausdrücklicher Einwilligung mitgesichert.", color = SlateGray)
            EncryptedBackupControls(backupState, blocked = busy || prepared != null || inspected != null || restoreUri != null,
                onFolder = { folder.launch(backupState.config.targetUri?.let(android.net.Uri::parse)) },
                onConfigure = { password, confirmation, change -> controller.configure(password, confirmation, change) },
                onManualCredentials = { controller.setManualCredentials(it) },
                onSave = { controller.save() }, onCancel = { controller.cancel() }, onReload = { controller.reload() })
            OutlinedButton(onClick = { inspectPicker.launch(arrayOf("application/octet-stream")) },
                enabled = !busy && !backupState.busy && prepared == null && inspected == null && restoreUri == null,
                modifier = Modifier.fillMaxWidth().testTag("offline_backup_restore_inspect")) { Text("Verschlüsselte Sicherung prüfen") }
            if (inspectJob?.isActive == true) {
                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(onClick = { inspectJob?.cancel() }, modifier = Modifier.testTag("offline_restore_cancel")) { Text("Prüfung abbrechen") }
            }
            inspectStatus?.let { Text(it, Modifier.testTag("offline_restore_status")) }
            TextButton(onClick = { legacyOptions = !legacyOptions }, enabled = !busy && !backupState.busy && inspected == null && restoreUri == null,
                modifier = Modifier.testTag("offline_backup_legacy_options")) {
                Text(if (legacyOptions) "V1-Optionen schließen" else "Ältere V1-ZIP-Sicherungen")
            }
            if (legacyOptions) {
                Text("V1-ZIP ist unverschlüsselt und enthält keine Zugangsschlüssel. Nur an einem geschützten Ort speichern.", color = SlateGray)
                OutlinedButton(onClick = {
                    busy = true
                    exportStatus = null
                    scope.launch {
                        var temporary: File? = null
                        var handedToPicker = false
                        try {
                            withContext(Dispatchers.IO) {
                                temporary = service.prepareExport()
                            }
                            exportArchive = temporary
                            export.launch("ImmoPilot-Sicherung-${LocalDate.now()}-${java.util.UUID.randomUUID()}.zip")
                            handedToPicker = true
                        } catch (error: CancellationException) { throw error
                        } catch (error: OfflineBackupValidationException) { exportStatus = error.message
                        } catch (error: Exception) { exportStatus = "Die lokale Sicherung konnte nicht vorbereitet werden. Es wurde keine Zieldatei angelegt. Bitte prüfe den lokalen Speicher und versuche es erneut."
                        } finally {
                            if (!handedToPicker) {
                                temporary?.delete(); exportArchive = null; busy = false
                            }
                        }
                    }
                }, enabled = !busy && !backupState.busy && inspected == null && restoreUri == null,
                    modifier = Modifier.fillMaxWidth().testTag("offline_backup_legacy_export")) { Text("Unverschlüsselte V1-ZIP speichern") }
                OutlinedButton(onClick = { import.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !busy && !backupState.busy && inspected == null && restoreUri == null,
                    modifier = Modifier.fillMaxWidth().testTag("offline_backup_import")) { Text("V1-ZIP prüfen und ergänzen") }
            }
            if (busy) Text("Sicherung wird verarbeitet …")
            listOfNotNull(exportStatus, status).takeIf { it.isNotEmpty() }?.let {
                Text(it.joinToString("\n\n"), modifier = Modifier.testTag("offline_backup_status"))
            }
        }
    }
    restoreUri?.let { uri ->
        EncryptedRestorePasswordDialog(onDismiss = { restoreUri = null }, onConfirm = { password ->
            restoreUri = null
            busy = true
            inspectStatus = "Sicherung wird vollständig entschlüsselt und geprüft …"
            val job = scope.launch {
                try {
                    inspected = OfflineBackupRestoreInspector.inspect(context, password) {
                        context.contentResolver.openInputStream(uri)
                            ?: throw OfflineBackupValidationException("Die Sicherung konnte nicht geöffnet werden.")
                    }
                    inspectStatus = "Vollsicherung vollständig geprüft. Es wurden keine Daten übernommen."
                } catch (error: CancellationException) {
                    inspectStatus = "Prüfung abgebrochen. Es wurden keine Daten übernommen."
                    throw error
                } catch (error: OfflineBackupValidationException) { inspectStatus = error.message }
                catch (_: Exception) { inspectStatus = "Die Sicherung konnte nicht geprüft werden. Es wurden keine Daten übernommen." }
                finally { password.fill('\u0000'); busy = false }
            }
            job.invokeOnCompletion { password.fill('\u0000') }
            inspectJob = job
        })
    }
    inspected?.let { EncryptedRestorePreviewDialog(it.preview, onClose = { closeInspected() }) }
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
