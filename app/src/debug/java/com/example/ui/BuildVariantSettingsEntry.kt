package com.example.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.qa.Phase11AIsolation
import com.example.qa.Phase11APortfolioSeeder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal data class TestToolsState(val checking: Boolean = true, val loading: Boolean = false,
    val available: Boolean = false, val success: Boolean = false, val message: String? = null,
    val runId: String? = null)

internal class Phase11ATestToolsViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(TestToolsState())
    val state = mutable.asStateFlow()
    private val context get() = getApplication<Application>()
    private val db get() = AppDatabase.getDatabase(context, viewModelScope)

    fun refresh() {
        if (mutable.value.loading) return
        mutable.value = mutable.value.copy(checking = true, available = false, success = false)
        viewModelScope.launch {
            var savedRunId: String? = null
            val result = withContext(Dispatchers.IO) { runCatching {
                Phase11APortfolioSeeder.requireIsolated(context)
                savedRunId = context.getSharedPreferences("phase11a_seed_journal", 0).getString("runId", null)
                Phase11APortfolioSeeder.checkEmpty(context, db)
            } }
            mutable.value = mutable.value.copy(checking = false, available = result.isSuccess,
                runId = savedRunId, message = result.exceptionOrNull()?.let {
                    if (it.message == Phase11APortfolioSeeder.INCOMPLETE) it.message else Phase11APortfolioSeeder.EMPTY_REQUIRED
                })
        }
    }

    fun load(onSeeded: suspend () -> Unit) {
        if (!mutable.value.available || mutable.value.checking || mutable.value.loading) return
        val runId = "P11A-${LocalDate.now(ZoneOffset.UTC)}-UI-${UUID.randomUUID()}"
        mutable.value = TestToolsState(checking = false, loading = true, runId = runId)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                Phase11APortfolioSeeder.requireIsolated(context)
                Phase11APortfolioSeeder.seedBundled(context, db, runId)
            } }
            val refresh = if (result.isSuccess) {
                // Refresh the existing app state via its existing post-restore refresh, no new domain logic.
                runCatching { onSeeded() }
            } else null
            mutable.value = TestToolsState(checking = false, success = result.isSuccess, runId = runId,
                message = if (refresh?.isFailure == true) "Das Testportfolio ist gespeichert. Bitte die App neu starten, um alle Ansichten zu aktualisieren."
                else result.exceptionOrNull()?.let {
                    if (it.message?.contains(Phase11APortfolioSeeder.EMPTY_REQUIRED) == true) Phase11APortfolioSeeder.EMPTY_REQUIRED
                    else if (it.message == Phase11APortfolioSeeder.INCOMPLETE) it.message
                    else "Das Testportfolio konnte nicht geladen werden. Es werden keine vorhandenen Daten gelöscht. Bitte die Testvorbereitung prüfen."
                })
        }
    }
}

/** No product-ID entry, including the CI reference. Release supplies a separate no-op. */
@Composable
internal fun BuildVariantSettingsEntry() {
    val context = LocalContext.current
    if (context.packageName != Phase11AIsolation.EXPECTED || !com.example.BuildConfig.DEBUG) return
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag("phase11a_tools_entry")) {
        Text("Phase 11A Testwerkzeuge")
    }
    if (open) {
        val tools: Phase11ATestToolsViewModel = viewModel()
        val app: ReceiptViewModel = viewModel()
        val state by tools.state.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) { tools.refresh() }
        Dialog(onDismissRequest = { if (!state.loading) open = false }, properties = DialogProperties(
            usePlatformDefaultWidth = false, dismissOnBackPress = !state.loading, dismissOnClickOutside = false)) {
            Phase11ATestToolsScreen(state, onBack = { open = false },
                onLoad = { tools.load { withContext(Dispatchers.IO) { app.refreshPreferencesAfterRestore() } } })
        }
    }
}

@Composable
internal fun Phase11ATestToolsScreen(state: TestToolsState, onBack: () -> Unit, onLoad: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = state.loading) { /* Keep the importer owned by its ViewModel; no navigation mid-write. */ }
    Surface(color = SoftBackground, modifier = Modifier.fillMaxSize().testTag("phase11a_tools")) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { TextButton(onClick = onBack, enabled = !state.loading) { Text("Zurück zu Einstellungen") } }
            item { Text("Phase 11A Testwerkzeuge", style = MaterialTheme.typography.headlineSmall, color = DarkNavy) }
            item { Card(border = BorderStroke(1.dp, WarmOrange)) {
                Text("Nur Testdaten – nicht für produktive Nutzung", Modifier.padding(16.dp), color = DarkNavy)
            } }
            item { Text("Lädt das bestehende synthetische Phase-10B/11A-Portfolio einschließlich aller 60 Originaldateien. Eine leere Testinstallation ist erforderlich.") }
            item { Button(onClick = { confirm = true }, enabled = state.available && !state.loading && !state.checking,
                modifier = Modifier.fillMaxWidth().testTag("phase11a_load_17")) { Text("17-Einheiten-Testportfolio laden") } }
            if (state.checking || state.loading) item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (state.loading) "Testportfolio wird geladen … Bitte warten." else "Testinstallation wird geprüft …")
            }
            state.message?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("phase11a_seed_error")) } }
            if (state.success) item {
                Text("Testportfolio erfolgreich geladen", color = EmeraldGreen, modifier = Modifier.testTag("phase11a_seed_success"))
                Text("5 Objekte\n17 Einheiten\n120 Bankbuchungen\n70 Belege\n70 Dokumente\n10 Fahrten\n60 Originaldateien")
                Text("Du kannst jetzt zur normalen App-Navigation zurückkehren. Google Drive wird separat geprüft.")
            }
            state.runId?.let { item { Text("Run-ID: $it", style = MaterialTheme.typography.bodySmall) } }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Testportfolio laden?") },
        text = { Text("Es werden ausschließlich synthetische Testdaten in die isolierte ImmoPilot-Test-App geschrieben.") },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Abbrechen") } },
        confirmButton = { TextButton(onClick = { confirm = false; onLoad() }, modifier = Modifier.testTag("phase11a_confirm_seed")) { Text("Testportfolio laden") } })
}
