package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.example.data.AppDatabase
import com.example.data.OfflineBackupArchiveEngine
import com.example.data.OfflineBackupCredentialSection
import com.example.data.OfflineBackupDestination
import com.example.data.OfflineBackupGenerationStore
import com.example.data.OfflineBackupKeyManager
import com.example.data.OfflineBackupSettings
import com.example.data.OfflineBackupSnapshot
import com.example.data.OfflineBackupValidationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** View-model-owned actions. No passwords in saved state, settings or publication requests. */
internal class OfflineBackupController(private val context: Context, database: AppDatabase, private val scope: CoroutineScope,
    private val folders: FolderAccess = AndroidFolderAccess(context),
    private val destination: (Uri) -> OfflineBackupDestination = { OfflineBackupDestination(context, it) }) {
    enum class Operation { LOADING, PASSWORD, FOLDER, OPTIONS, SAVING }
    data class State(val loaded: Boolean = false, val operation: Operation? = null,
        val config: OfflineBackupSettings.Config = OfflineBackupSettings.Config(), val generationId: String? = null,
        val keyProblem: String? = null, val folderReady: Boolean = false, val folderLabel: String = "Kein Sicherungsordner gewählt",
        val status: String? = null, val lastPublished: OfflineBackupDestination.Published? = null) {
        val busy get() = operation != null
        val configured get() = generationId != null
        val canSave get() = loaded && configured && folderReady && !busy
    }
    interface FolderAccess {
        fun hasGrant(uri: Uri): Boolean
        fun persist(uri: Uri)
        fun release(uri: Uri)
        fun label(uri: Uri): String?
    }
    private val settings = OfflineBackupSettings(context)
    private val keys = OfflineBackupKeyManager(context)
    private val engine = OfflineBackupArchiveEngine(context, database)
    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()
    private val lock = Any()
    private var current: Job? = null

    fun reload(): Job? = perform(Operation.LOADING) { refresh() }
    fun configure(password: CharArray, confirmation: CharArray, change: Boolean = false): Job? {
        val job = perform(Operation.PASSWORD) {
            if (!mutable.value.loaded || mutable.value.keyProblem != null)
                throw OfflineBackupValidationException("Der vorhandene Schlüsselzustand muss zuerst geprüft werden.")
            if (password.size !in 16..1024 || !password.contentEquals(confirmation))
                throw OfflineBackupValidationException("Das Wiederherstellungspasswort muss mindestens 16 Zeichen haben und übereinstimmend bestätigt werden.")
            if (change) {
                val expected = mutable.value.generationId
                    ?: throw OfflineBackupValidationException("Bitte zuerst das Wiederherstellungspasswort einrichten.")
                keys.changePassword(expected, password, confirmation)
            } else keys.initialize(password, confirmation)
            refresh()
            mutable.update { it.copy(status = if (change) "Neues Wiederherstellungspasswort eingerichtet. Ältere Archive benötigen weiterhin ihr bisheriges Passwort."
                else "Wiederherstellungspasswort eingerichtet. Bitte außerhalb der App sicher aufbewahren.") }
        }
        if (job == null) { password.fill('\u0000'); confirmation.fill('\u0000') }
        else job.invokeOnCompletion { password.fill('\u0000'); confirmation.fill('\u0000') }
        return job
    }
    fun selectFolder(uri: Uri): Job? = perform(Operation.FOLDER) {
        val previous = settings.load()
        val alreadyGranted = folders.hasGrant(uri)
        try {
            folders.persist(uri)
            destination(uri).preflight()
            settings.setTarget(uri)
        } catch (error: Exception) {
            if (!alreadyGranted && previous.targetUri != uri.toString()) try { folders.release(uri) } catch (_: Exception) { }
            throw error
        }
        refresh(); mutable.update { it.copy(status = "Sicherungsordner gespeichert und geprüft.") }
    }
    fun setManualCredentials(enabled: Boolean): Job? = perform(Operation.OPTIONS) {
        settings.setManualCredentials(enabled); refresh()
    }
    fun save(): Job? = perform(Operation.SAVING) {
        val config = settings.load()
        val uri = config.targetUri?.let(Uri::parse)
            ?: throw OfflineBackupValidationException("Bitte zuerst einen Sicherungsordner wählen.")
        if (mutable.value.generationId == null) throw OfflineBackupValidationException("Bitte zuerst das Wiederherstellungspasswort einrichten.")
        val result = engine.publish(destination(uri), OfflineBackupCredentialSection.Consent(manual = config.manualCredentials),
            snapshotProgress = { progress -> mutable.update { it.copy(status = when (progress.stage) {
                OfflineBackupSnapshot.Stage.METADATA_CAPTURED -> "Daten erfasst; Originale werden fixiert …"
                OfflineBackupSnapshot.Stage.ORIGINALS_FIXED -> "${progress.completed} Dateiversionen fixiert …"
                OfflineBackupSnapshot.Stage.SNAPSHOT_FIXED -> "Vollsicherung wird verschlüsselt und geprüft …"
            }) } }, destinationProgress = { stage -> mutable.update { it.copy(status = when (stage) {
                OfflineBackupDestination.Stage.WRITING -> "Vollsicherung wird in den Zielordner geschrieben …"
                OfflineBackupDestination.Stage.VERIFYING -> "Zielsicherung wird vollständig rückgelesen und geprüft …"
                OfflineBackupDestination.Stage.FINALIZING -> "Geprüfte Vollsicherung wird fertiggestellt …"
            }) } })
        mutable.update { it.copy(lastPublished = result, status = "Vollsicherung gespeichert und vollständig geprüft.") }
    }
    fun cancel() = synchronized(lock) { current?.cancel() }

    private fun refresh() {
        val config = settings.load()
        var keyProblem: String? = null
        val id = try { OfflineBackupGenerationStore(context).loadCurrent()?.use { it.recovery.generationId } }
            catch (error: OfflineBackupValidationException) { keyProblem = error.message; null }
        var ready = false; var label = "Kein Sicherungsordner gewählt"; var folderProblem: String? = null
        config.targetUri?.let { raw ->
            val uri = Uri.parse(raw)
            label = try { folders.label(uri)?.takeIf(String::isNotBlank) ?: "Gewählter Sicherungsordner" }
                catch (_: Exception) { "Gewählter Sicherungsordner" }
            try { destination(uri).preflight(); ready = true }
            catch (error: OfflineBackupValidationException) { folderProblem = error.message }
        }
        mutable.update { it.copy(loaded = true, config = config, generationId = id, keyProblem = keyProblem,
            folderReady = ready, folderLabel = label, status = keyProblem ?: folderProblem ?: it.status) }
    }
    private fun perform(operation: Operation, block: suspend () -> Unit): Job? = synchronized(lock) {
        if (current?.isActive == true || mutable.value.busy) return@synchronized null
        mutable.update { it.copy(operation = operation, status = null) }
        val job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try { block() }
            catch (error: CancellationException) { mutable.update { it.copy(status = "Vorgang abgebrochen. Bisherige Sicherungen bleiben unverändert.") }; throw error }
            catch (error: OfflineBackupValidationException) { mutable.update { it.copy(status = error.message) } }
            catch (_: Exception) { mutable.update { it.copy(status = "Der Sicherungsvorgang konnte nicht abgeschlossen werden. Bitte Ordner, Speicher und Einstellungen prüfen.") } }
            finally { mutable.update { it.copy(operation = null) } }
        }
        current = job
        job.invokeOnCompletion { synchronized(lock) {
            if (current === job) {
                current = null
                if (mutable.value.operation != null) mutable.update { it.copy(operation = null, status = "Vorgang abgebrochen.") }
            }
        } }
        job.start(); job
    }

    private class AndroidFolderAccess(private val context: Context) : FolderAccess {
        private val resolver get() = context.contentResolver
        override fun hasGrant(uri: Uri) = resolver.persistedUriPermissions.any { it.uri == uri }
        override fun persist(uri: Uri) = resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        override fun release(uri: Uri) = resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        override fun label(uri: Uri): String? {
            val document = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
            return resolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }
    }
}
