package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.api.AiProviderState
import com.example.api.ReceiptAnalysisProvider
import com.example.data.PropertyMetadata

internal enum class SettingsProvider(val title: String, val subtitle: String) {
    OPENAI("OpenAI", "Beleganalyse und Dokumentenprüfung"),
    GEMINI("Gemini", "Dokumenten- und Beleganalyse"),
    ROUTES("Google Routes", "Automatische Entfernungsberechnung");

    fun configured(state: AiProviderState) = when (this) {
        OPENAI -> state.hasOpenAiKey
        GEMINI -> state.hasGeminiKey
        ROUTES -> state.hasGoogleRoutesKey
    }
    val icon: ImageVector get() = when (this) {
        OPENAI -> Icons.Default.AutoAwesome
        GEMINI -> Icons.Default.AutoAwesome
        ROUTES -> Icons.Default.Route
    }
    val color: Color get() = when (this) {
        OPENAI -> EmeraldGreen
        GEMINI -> Color(0xFF7C3AED)
        ROUTES -> AccentBlue
    }
    val analysisProvider: ReceiptAnalysisProvider? get() = when (this) {
        OPENAI -> ReceiptAnalysisProvider.OPENAI
        GEMINI -> ReceiptAnalysisProvider.GEMINI
        ROUTES -> null
    }
}

/** Lives inside the existing global scaffold; only local settings navigation is owned here. */
@Composable
internal fun AppSettingsScreen(viewModel: ReceiptViewModel, onBack: () -> Unit) {
    val state by viewModel.aiProviderState.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("overview") }
    var providerName by rememberSaveable { mutableStateOf(SettingsProvider.OPENAI.name) }
    var providerReturn by rememberSaveable { mutableStateOf("overview") }
    val provider = SettingsProvider.valueOf(providerName)
    val back = {
        when (page) {
            "overview" -> onBack()
            "provider" -> page = providerReturn
            "key", "model" -> page = "provider"
            else -> page = "overview"
        }
    }
    BackHandler(onBack = back)
    fun openProvider(value: SettingsProvider, origin: String) {
        providerName = value.name
        providerReturn = origin
        page = "provider"
    }
    // Form input is deliberately not saveable and is disposed on navigation/reset.
    key(page, providerName) {
        when (page) {
            "overview" -> SettingsOverview(state, onBack = back, onProvider = { openProvider(it, "overview") }, onPage = { page = it })
            "keys" -> SettingsPage("API-Schlüssel", "Konfigurierte Anbieter auf diesem Gerät", back, "keys") {
                item { SettingsCard("Anbieter") {
                    SettingsProvider.entries.forEach { entry ->
                        ProviderRow(entry, state) { openProvider(entry, "keys") }
                    }
                } }
            }
            "provider" -> ProviderSettingsPage(viewModel, state, provider, back, onEditKey = { page = "key" }, onEditModel = { page = "model" })
            "key" -> ProviderKeyEditor(viewModel, state, provider, back)
            "model" -> OpenAiModelEditor(viewModel, state, back)
            "selection" -> AnalysisProviderSelection(viewModel, state, back)
            "addresses" -> SettingsAddresses(viewModel, back)
            "management" -> SettingsManagement(viewModel, back)
            "privacy" -> SettingsPage("Datenschutz", "Lokale Datenhaltung und sichere Speicherung", back, "privacy") {
                item { SettingsInfo("Lokale Daten", "Belege, Immobilien und weitere App-Daten werden lokal auf diesem Gerät gespeichert. Die App ist auch ohne Cloud-Konto nutzbar.") }
                item { SettingsInfo("KI und Routen", "Wenn du KI-Analyse oder Routenberechnung nutzt, werden die dafür benötigten Inhalte an den jeweiligen Anbieter übertragen. Prüfe vor der Nutzung, welche Daten du übermitteln möchtest.") }
                item { SettingsInfo("Sicherung", "Google Drive ist eine optionale Sicherung. API-Schlüssel werden nicht in die App-Sicherung oder Exporte aufgenommen. Die Android-System-Sicherung ist für diese App deaktiviert.") }
            }
            "info" -> SettingsPage("Info", "Über ImmoPilot", back, "info") {
                item { SettingsInfo("ImmoPilot", "Immobilien. Finanzen. Steuern.\nVersion ${BuildConfig.VERSION_NAME}") }
                item { SettingsInfo("KI-Ergebnisse prüfen", "KI-Vorschläge können Fehler enthalten und müssen geprüft werden. DATEV-Freigaben bleiben manuell.") }
            }
        }
    }
}

@Composable
private fun SettingsOverview(state: AiProviderState, onBack: () -> Unit, onProvider: (SettingsProvider) -> Unit, onPage: (String) -> Unit) {
    SettingsPage("App-Einstellungen", "Passe ImmoPilot an deine Nutzung an", onBack, "overview") {
        item { SettingsCard("KI & Anbieter") {
            SettingsProvider.entries.forEach { provider -> ProviderRow(provider, state) { onProvider(provider) } }
            SettingsDivider()
            SettingsRow("Aktiver KI-Anbieter", if (state.provider == ReceiptAnalysisProvider.OPENAI) "OpenAI · ${state.openAiModel}" else "Gemini", Icons.Default.Tune, AccentBlue, "selection") { onPage("selection") }
        } }
        item { SettingsCard("Sicherheit") {
            SettingsRow("API-Schlüssel", "Werden verschlüsselt auf diesem Gerät gespeichert", Icons.Default.Key, WarmOrange, "keys") { onPage("keys") }
            SettingsDivider()
            SettingsRow("Datenschutz", "Lokale Datenhaltung und sichere Speicherung", Icons.Default.Shield, AccentBlue, "privacy") { onPage("privacy") }
        } }
        item { SettingsCard("Allgemein") {
            SettingsRow("Fahrtenbuch-Adressen", "Startadresse und Adresse des aktuellen Objekts", Icons.Default.DirectionsCar, AccentBlue, "addresses") { onPage("addresses") }
            SettingsDivider()
            SettingsRow("Verwaltung & Belege", "Objekt, Mieter, Dokumentenstatus und Papierkorb", Icons.Default.Folder, WarmOrange, "management") { onPage("management") }
        } }
        item { SettingsCard("Info") {
            SettingsRow("Über ImmoPilot", "Version ${BuildConfig.VERSION_NAME} · Hinweise zur Nutzung", Icons.Default.Info, SlateGray, "info") { onPage("info") }
        } }
    }
}

@Composable
private fun ProviderRow(provider: SettingsProvider, state: AiProviderState, onClick: () -> Unit) {
    SettingsRow(provider.title,
        if (provider == SettingsProvider.OPENAI) "Beleganalyse · Modell ${state.openAiModel}" else provider.subtitle,
        provider.icon, provider.color, provider.name, configured = provider.configured(state), onClick = onClick)
}

@Composable
private fun SettingsPage(title: String, subtitle: String, onBack: () -> Unit, tag: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize().background(SoftBackground).imePadding().testTag("settings_$tag"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = DarkNavy) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    Text(subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = SlateGray)
                }
            }
        }
        content()
    }
}

@Composable
private fun SettingsCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = Ui2.shape, colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, BorderColor)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (title != null) Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
            content()
        }
    }
}

@Composable private fun SettingsDivider() = HorizontalDivider(color = BorderColor.copy(alpha = .65f))

@Composable
private fun SettingsRow(title: String, subtitle: String, icon: ImageVector, color: Color, tag: String, configured: Boolean? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("settings_row_$tag"), shape = Ui2.iconShape, color = Color.White) {
        Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsIcon(icon, color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkNavy)
                Text(subtitle, fontSize = 11.sp, lineHeight = 14.sp, color = SlateGray)
            }
            if (configured != null) KeyStatus(configured)
            Icon(Icons.Default.ChevronRight, null, tint = SlateGray, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable private fun SettingsIcon(icon: ImageVector, color: Color) {
    Surface(Modifier.size(42.dp), shape = Ui2.iconShape, color = color.copy(alpha = .12f)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(23.dp)) }
    }
}

@Composable private fun KeyStatus(configured: Boolean) {
    val color = if (configured) EmeraldGreen else SlateGray
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = .1f)) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (configured) Icon(Icons.Default.Check, null, tint = color, modifier = Modifier.size(12.dp))
            Text(if (configured) "Eingerichtet" else "Nicht eingerichtet", fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable private fun SettingsInfo(title: String, text: String) {
    SettingsCard(title) { Text(text, fontSize = 12.sp, lineHeight = 18.sp, color = SlateGray) }
}

@Composable
private fun ProviderSettingsPage(viewModel: ReceiptViewModel, state: AiProviderState, provider: SettingsProvider, onBack: () -> Unit, onEditKey: () -> Unit, onEditModel: () -> Unit) {
    var confirmRemove by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val configured = provider.configured(state)
    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false }, shape = Ui2.shape,
        title = { Text("${provider.title}-Schlüssel entfernen?", color = DarkNavy) },
        text = { Text("Der gespeicherte Schlüssel wird von diesem Gerät entfernt.") },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Abbrechen") } },
        confirmButton = { TextButton(onClick = {
            try {
                when (provider) {
                    SettingsProvider.OPENAI -> viewModel.deleteOpenAiKey()
                    SettingsProvider.GEMINI -> viewModel.deleteGeminiKey()
                    SettingsProvider.ROUTES -> viewModel.deleteGoogleRoutesKey()
                }
            } catch (_: Exception) { error = "Der Schlüssel konnte auf diesem Gerät nicht entfernt werden." }
            confirmRemove = false
        }, modifier = Modifier.testTag("confirm_remove_key"), colors = ButtonDefaults.textButtonColors(contentColor = CrimsonRed)) { Text("Entfernen") } }
    )
    SettingsPage(provider.title, provider.subtitle, onBack, "provider") {
        item { SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsIcon(provider.icon, provider.color)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(provider.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = DarkNavy)
                    KeyStatus(configured)
                }
            }
            Text(if (configured) provider.subtitle else "Für ${provider.title} ist noch kein API-Schlüssel hinterlegt.", fontSize = 12.sp, color = SlateGray)
        } }
        item { SettingsCard("Einstellungen") {
            if (provider == SettingsProvider.OPENAI) {
                SettingsRow("Aktives Modell", state.openAiModel, Icons.Default.Tune, provider.color, "model", onClick = onEditModel)
                SettingsDivider()
            }
            Text("API-Schlüssel", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DarkNavy)
            Text(if (configured) "••••••••••••••••" else "Nicht eingerichtet", modifier = Modifier.testTag("provider_key_mask"), fontSize = 14.sp, color = DarkNavy)
            Text(if (configured) "Sicher verschlüsselt gespeichert" else "Du kannst einen Schlüssel auf diesem Gerät hinterlegen.", fontSize = 11.sp, color = SlateGray)
        } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onEditKey, modifier = Modifier.fillMaxWidth().testTag("edit_provider_key"), shape = Ui2.iconShape, colors = ButtonDefaults.buttonColors(containerColor = provider.color)) {
                    Icon(Icons.Default.Key, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (configured) "Schlüssel ändern" else "Jetzt einrichten")
                }
                if (configured) OutlinedButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth().testTag("remove_provider_key"), shape = Ui2.iconShape, colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonRed), border = BorderStroke(1.dp, CrimsonRed.copy(alpha = .35f))) {
                    Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Schlüssel entfernen")
                }
            }
        }
        item { SettingsInfo("Sicherheitshinweis", "API-Schlüssel werden lokal mit Android Keystore verschlüsselt gespeichert und nicht im App-Backup oder in Exporten abgelegt.") }
        item { SettingsInfo("Verwendung", if (provider == SettingsProvider.ROUTES) "Fahrtenbuch · automatische Berechnung von Straßenkilometern" else "Beleganalyse · Dokumenten-Analyse\nAktiver Anbieter: ${if (state.provider == ReceiptAnalysisProvider.OPENAI) "OpenAI" else "Gemini"}") }
        if (error != null) item { Text(error!!, color = CrimsonRed, fontSize = 12.sp) }
    }
}

@Composable
private fun ProviderKeyEditor(viewModel: ReceiptViewModel, state: AiProviderState, provider: SettingsProvider, onBack: () -> Unit) {
    var input by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { input = "" } }
    var privateDeviceConfirmed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val activeConfigured = when (state.provider) {
        ReceiptAnalysisProvider.OPENAI -> state.hasOpenAiKey
        ReceiptAnalysisProvider.GEMINI -> state.hasGeminiKey
    }
    val saveProvider = if (activeConfigured) state.provider else provider.analysisProvider ?: state.provider
    SettingsPage("${provider.title}-Schlüssel", "Sicher auf diesem Gerät hinterlegen", onBack, "key") {
        item { SettingsCard("API-Schlüssel") {
            OutlinedTextField(input, { input = it.trim() }, label = { Text("Neuer API-Schlüssel") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password), singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("provider_api_key_input"), shape = Ui2.iconShape)
            Text("Der vorhandene Schlüssel wird nicht angezeigt. Ein neuer Schlüssel ersetzt ihn erst nach dem Speichern.", fontSize = 12.sp, color = SlateGray)
            if (provider.analysisProvider != null) {
                Text(if (activeConfigured) "Der aktive KI-Anbieter und das OpenAI-Modell bleiben erhalten. Den Anbieter wechselst du in der Anbieterauswahl." else "Beim ersten Einrichten wird ${provider.title} für neue KI-Analysen ausgewählt. Das OpenAI-Modell bleibt erhalten.", fontSize = 12.sp, color = SlateGray)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(privateDeviceConfirmed, { privateDeviceConfirmed = it }, modifier = Modifier.testTag("private_device_confirmation"))
                Text("Ich verwende diese APK nur privat und veröffentliche den Schlüssel nicht.", fontSize = 12.sp, color = DarkNavy)
            }
            error?.let { Text(it, color = CrimsonRed, fontSize = 12.sp, modifier = Modifier.testTag("settings_save_error")) }
            Button(onClick = {
                val result = viewModel.saveAiProviderSettings(saveProvider, state.openAiModel,
                    if (provider == SettingsProvider.OPENAI) input else "",
                    if (provider == SettingsProvider.GEMINI) input else "",
                    if (provider == SettingsProvider.ROUTES) input else "")
                if (result == null) { input = ""; onBack() } else error = result
            }, enabled = privateDeviceConfirmed && input.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("save_provider_key"), shape = Ui2.iconShape) { Text("Speichern") }
            TextButton(onClick = { input = ""; onBack() }, modifier = Modifier.fillMaxWidth()) { Text("Abbrechen") }
        } }
    }
}

@Composable
private fun OpenAiModelEditor(viewModel: ReceiptViewModel, state: AiProviderState, onBack: () -> Unit) {
    var model by remember { mutableStateOf(state.openAiModel) }
    var error by remember { mutableStateOf<String?>(null) }
    SettingsPage("OpenAI-Modell", "Vorhandene Modellkonfiguration", onBack, "model") {
        item { SettingsCard("Aktives Modell") {
            OutlinedTextField(model, { model = it.trim() }, label = { Text("OpenAI-Modell") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("openai_model_input"), shape = Ui2.iconShape)
            Text("Ein leeres Feld verwendet das bisherige Standardmodell. Die Verfügbarkeit hängt von deinem OpenAI-Konto ab.", color = SlateGray, fontSize = 12.sp)
            error?.let { Text(it, color = CrimsonRed, fontSize = 12.sp) }
            Button(onClick = {
                val result = viewModel.saveAiProviderSettings(state.provider, model, "", "")
                if (result == null) onBack() else error = result
            }, modifier = Modifier.fillMaxWidth().testTag("save_openai_model")) { Text("Speichern") }
        } }
    }
}

@Composable
private fun AnalysisProviderSelection(viewModel: ReceiptViewModel, state: AiProviderState, onBack: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    SettingsPage("KI-Anbieter", "Anbieter für neue Beleg- und Dokumentanalysen", onBack, "selection") {
        item { SettingsCard("Aktiver Anbieter") {
            listOf(SettingsProvider.OPENAI, SettingsProvider.GEMINI).forEach { provider ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = state.provider == provider.analysisProvider, enabled = provider.configured(state), onClick = {
                        error = viewModel.saveAiProviderSettings(provider.analysisProvider!!, state.openAiModel, "", "")
                    }, modifier = Modifier.testTag("select_${provider.name}"))
                    Column { Text(provider.title, color = DarkNavy); KeyStatus(provider.configured(state)) }
                }
            }
            Text("Für einen Wechsel muss der jeweilige Schlüssel eingerichtet sein. Google Routes arbeitet unabhängig vom KI-Anbieter.", fontSize = 12.sp, color = SlateGray)
            error?.let { Text(it, color = CrimsonRed, fontSize = 12.sp) }
        } }
    }
}

@Composable
private fun SettingsAddresses(viewModel: ReceiptViewModel, onBack: () -> Unit) {
    val current by viewModel.propertyMetadata.collectAsStateWithLifecycle()
    val metadata = current ?: PropertyMetadata()
    var home by remember(metadata) { mutableStateOf(metadata.wohnort) }
    var address by remember(metadata) { mutableStateOf(metadata.adresse) }
    var saved by remember { mutableStateOf(false) }
    SettingsPage("Fahrtenbuch-Adressen", "Adressen für das aktuell ausgewählte Objekt", onBack, "addresses") {
        item { SettingsCard("Adressen") {
            OutlinedTextField(home, { home = it; saved = false }, label = { Text("Meine Adresse / Wohnort (Startadresse)") }, modifier = Modifier.fillMaxWidth().testTag("menu_user_address_input"), singleLine = true, shape = Ui2.iconShape)
            OutlinedTextField(address, { address = it; saved = false }, label = { Text("Immobilien-Adresse (Zielobjekt)") }, modifier = Modifier.fillMaxWidth().testTag("menu_property_address_input"), singleLine = true, shape = Ui2.iconShape)
            Button(onClick = { viewModel.updatePropertyMetadata(metadata.copy(wohnort = home, adresse = address)); saved = true }, modifier = Modifier.fillMaxWidth().testTag("save_menu_addresses_button")) { Text("Adressen speichern") }
            if (saved && current?.wohnort == home && current?.adresse == address) Text("Adressen gespeichert", fontSize = 12.sp, color = EmeraldGreen)
        } }
    }
}

@Composable
private fun SettingsManagement(viewModel: ReceiptViewModel, onBack: () -> Unit) {
    var action by remember { mutableStateOf<String?>(null) }
    when (action) {
        "tenants" -> TenantManagementDialog(viewModel) { action = null }
        "property" -> PropertyMetadataFormDialog(viewModel) { action = null }
        "documents" -> DocumentStatusOverviewDialog(viewModel) { action = null }
        "recycle" -> RecycleBinDialog(viewModel) { action = null }
        "reset" -> ResetLocalDataDialog(onConfirm = { viewModel.resetLocalReceiptData(); action = null }, onDismiss = { action = null })
    }
    SettingsPage("Verwaltung & Belege", "Bestehende Verwaltungs- und Pflegefunktionen", onBack, "management") {
        item { SettingsCard("Objekt & Personen") {
            SettingsRow("Mieter verwalten", "Mieterdaten der vorhandenen Einheiten", Icons.Default.People, AccentBlue, "tenants") { action = "tenants" }
            SettingsDivider()
            SettingsRow("Objekt-Stammdaten", "Daten des aktuell ausgewählten Objekts", Icons.Default.HomeWork, AccentBlue, "property") { action = "property" }
        } }
        item { SettingsCard("Belege & Speicher") {
            SettingsRow("Dokumentenstatus & Reparatur", "Originaldokumente prüfen und fehlende Dateien ersetzen", Icons.Default.FindInPage, AccentBlue, "documents") { action = "documents" }
            SettingsDivider()
            SettingsRow("Papierkorb", "Gelöschte Belege ansehen und wiederherstellen", Icons.Default.DeleteOutline, WarmOrange, "recycle") { action = "recycle" }
            SettingsDivider()
            SettingsRow("Lokale Belegdaten zurücksetzen", "Bestehende Funktion mit Sicherheitsbestätigung", Icons.Default.DeleteForever, CrimsonRed, "reset") { action = "reset" }
        } }
    }
}
