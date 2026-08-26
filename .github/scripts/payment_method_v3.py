from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]

def read(rel):
    return (ROOT / rel).read_text(encoding='utf-8')

def write(rel, text):
    (ROOT / rel).write_text(text, encoding='utf-8')

def require_replace(text, old, new, label, count=1):
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f'{label}: anchor not found')
    return text.replace(old, new, count)

# 1) Room model + safe migration 14 -> 15
rel = 'app/src/main/java/com/example/data/ReceiptDatabase.kt'
s = read(rel)
s = require_replace(
    s,
    '    val mieter: String = "", // Mieter name\n    val isArchivedToDrive: Boolean = false,',
    '    val mieter: String = "", // Mieter name\n    val zahlungsart: String = "Unbekannt",\n    val zahlungsartQuelle: String = "UNBEKANNT",\n    val zahlungsartConfidence: Double = 0.0,\n    val isArchivedToDrive: Boolean = false,',
    'Receipt fields'
)
if 'val MIGRATION_14_15' not in s:
    marker = '@Database(entities = [Receipt::class, PropertyMetadata::class, ReceiptEntity::class, Beleg::class, ExportAuditRun::class, ReceiptDocumentReference::class], version = 14, exportSchema = false)'
    migration = '''val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE receipts ADD COLUMN zahlungsart TEXT NOT NULL DEFAULT 'Unbekannt'")
        db.execSQL("ALTER TABLE receipts ADD COLUMN zahlungsartQuelle TEXT NOT NULL DEFAULT 'UNBEKANNT'")
        db.execSQL("ALTER TABLE receipts ADD COLUMN zahlungsartConfidence REAL NOT NULL DEFAULT 0.0")
    }
}

@Database(entities = [Receipt::class, PropertyMetadata::class, ReceiptEntity::class, Beleg::class, ExportAuditRun::class, ReceiptDocumentReference::class], version = 15, exportSchema = false)'''
    if marker not in s:
        raise SystemExit('Database version anchor not found')
    s = s.replace(marker, migration, 1)
    s = require_replace(
        s,
        '.addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)',
        '.addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)',
        'Migration registration'
    )
write(rel, s)

# 2) Shared AI result model + Gemini prompt
rel = 'app/src/main/java/com/example/api/GeminiClient.kt'
s = read(rel)
s = require_replace(
    s,
    '    @Json(name = "mieter") val mieter: String = "",\n    @Json(name = "positionen") val positionen: List<com.example.data.ReceiptItem> = emptyList()',
    '    @Json(name = "mieter") val mieter: String = "",\n    @Json(name = "zahlungsart") val zahlungsart: String = "Unbekannt",\n    @Json(name = "positionen") val positionen: List<com.example.data.ReceiptItem> = emptyList()',
    'ExtractedReceipt field'
)
if '- zahlungsart:' not in s:
    anchor = '            - mieter: Name des Mieters/Zahlers, falls auf dem Beleg oder der Überweisung genannt (z. B. "Erika Mustermann", "Hans Peter"), sonst leeres String "".\n'
    line = '            - zahlungsart: Nur eindeutig erkennbare Zahlungsangaben verwenden. Erlaubt sind exakt "Bar", "Girocard/EC", "Kreditkarte", "Überweisung", "Lastschrift", "PayPal" oder "Unbekannt". Bei Unsicherheit immer "Unbekannt".\n'
    if anchor in s:
        s = s.replace(anchor, anchor + line, 1)
    json_anchor = '              "mieter": "Erika Mustermann",\n'
    if json_anchor in s:
        s = s.replace(json_anchor, json_anchor + '              "zahlungsart": "Girocard/EC",\n', 1)
write(rel, s)

# 3) OpenAI strict schema
rel = 'app/src/main/java/com/example/api/OpenAiClient.kt'
s = read(rel)
if '.put("zahlungsart"' not in s:
    s = require_replace(
        s,
        '            .put("mieter", stringSchema())\n            .put("positionen",',
        '            .put("mieter", stringSchema())\n            .put("zahlungsart", stringSchema().put("enum", JSONArray(listOf("Bar", "Girocard/EC", "Kreditkarte", "Überweisung", "Lastschrift", "PayPal", "Unbekannt"))))\n            .put("positionen",',
        'OpenAI payment schema'
    )
    s = require_replace(
        s,
        '                        "wohneinheit", "mieter", "positionen"',
        '                        "wohneinheit", "mieter", "zahlungsart", "positionen"',
        'OpenAI required fields'
    )
if 'zahlungsart darf nur' not in s:
    anchor = '        kontoNr enthält nur die vorgeschlagene Sachkontonummer. Keine steuerliche Freigabe erteilen.\n'
    if anchor in s:
        s = s.replace(anchor, anchor + '        zahlungsart darf nur "Bar", "Girocard/EC", "Kreditkarte", "Überweisung", "Lastschrift", "PayPal" oder "Unbekannt" sein. Nur eindeutig sichtbare Angaben verwenden; sonst "Unbekannt".\n', 1)
write(rel, s)

# 4) ViewModel: save payment method + backfill existing receipts
rel = 'app/src/main/java/com/example/ui/ReceiptViewModel.kt'
s = read(rel)
marker = '    // Save extracted receipt to database\n'
if marker not in s:
    raise SystemExit('ViewModel save marker missing')
if 'fun backfillPaymentMethods()' not in s:
    block = '''    private val _isBackfillingPaymentMethods = MutableStateFlow(false)
    val isBackfillingPaymentMethods = _isBackfillingPaymentMethods.asStateFlow()
    private val _paymentBackfillStatus = MutableStateFlow<String?>(null)
    val paymentBackfillStatus = _paymentBackfillStatus.asStateFlow()

    private fun normalizePaymentMethod(raw: String?): String {
        val value = raw.orEmpty().trim().lowercase(Locale.GERMANY)
        return when {
            value.isBlank() || value == "unbekannt" -> "Unbekannt"
            value.contains("paypal") -> "PayPal"
            value.contains("lastschrift") -> "Lastschrift"
            value.contains("überweisung") || value.contains("ueberweisung") -> "Überweisung"
            value.contains("mastercard") || value.contains("visa") || value.contains("kreditkarte") -> "Kreditkarte"
            value.contains("girocard") || value.contains("maestro") || Regex("(^|[^a-z])ec([^a-z]|$)").containsMatchIn(value) -> "Girocard/EC"
            value.contains("bargeld") || Regex("(^|[^a-z])bar([^a-z]|$)").containsMatchIn(value) -> "Bar"
            else -> "Unbekannt"
        }
    }

    private suspend fun detectExistingPaymentMethod(receipt: Receipt): Pair<String, String>? {
        val text = listOf(receipt.aussteller, receipt.beschreibung, receipt.positionenJson).joinToString(" ")
        val fromText = normalizePaymentMethod(text)
        if (fromText != "Unbekannt") return fromText to "TEXT_HEURISTIK"

        val paths = receipt.imageUrl.split(',').map { it.trim() }.filter { it.isNotBlank() }
        val bitmaps = kotlinx.coroutines.withContext(Dispatchers.IO) {
            paths.mapNotNull { path -> runCatching {
                val file = File(path)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            }.getOrNull() }
        }
        if (bitmaps.isEmpty()) return null
        return try {
            val learned = getUserLearnedRulesPromptContext()
            val extracted = when (_aiProviderState.value.provider) {
                ReceiptAnalysisProvider.GEMINI -> {
                    val key = AiProviderSettings.getGeminiKey(getApplication())
                    try { GeminiClient.analyzeReceipt(receiptText = "Bestimme insbesondere die Zahlungsart. Bei Unsicherheit Unbekannt.", bitmaps = bitmaps, userLearnedRulesContext = learned, apiKeyOverride = key) }
                    finally { key?.fill('\\u0000') }
                }
                ReceiptAnalysisProvider.OPENAI -> {
                    val key = AiProviderSettings.getOpenAiKey(getApplication()) ?: return null
                    try { OpenAiClient.analyzeReceipt(apiKey = key, model = _aiProviderState.value.openAiModel, receiptText = "Bestimme insbesondere die Zahlungsart. Bei Unsicherheit Unbekannt.", bitmaps = bitmaps, userLearnedRulesContext = learned) }
                    finally { key.fill('\\u0000') }
                }
            }
            val method = normalizePaymentMethod(extracted?.zahlungsart)
            if (method == "Unbekannt") null else method to "KI_NACHERKANNT"
        } finally {
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    fun backfillPaymentMethods() {
        if (_isBackfillingPaymentMethods.value) return
        viewModelScope.launch {
            _isBackfillingPaymentMethods.value = true
            try {
                val candidates = receipts.value.filter { normalizePaymentMethod(it.zahlungsart) == "Unbekannt" }
                var updated = 0
                candidates.forEachIndexed { index, receipt ->
                    _paymentBackfillStatus.value = "Prüfe Beleg ${index + 1} von ${candidates.size} …"
                    val detected = runCatching { detectExistingPaymentMethod(receipt) }.getOrNull()
                    if (detected != null) {
                        repository.insert(receipt.copy(
                            zahlungsart = detected.first,
                            zahlungsartQuelle = detected.second,
                            zahlungsartConfidence = if (detected.second == "KI_NACHERKANNT") 0.92 else 0.78
                        ))
                        updated++
                    }
                }
                _paymentBackfillStatus.value = "Fertig: $updated von ${candidates.size} Belegen ergänzt."
            } finally {
                _isBackfillingPaymentMethods.value = false
            }
        }
    }

'''
    s = s.replace(marker, block + marker, 1)

save_start = s.find('    fun saveReceipt(')
save_end = s.find('    // Update an existing receipt', save_start)
if save_start < 0 or save_end < 0:
    raise SystemExit('saveReceipt section missing')
save = s[save_start:save_end]
if 'zahlungsart: String = "Unbekannt"' not in save:
    save = require_replace(save,
        '        mieter: String = "",\n        positionenJson: String = ""\n',
        '        mieter: String = "",\n        zahlungsart: String = "Unbekannt",\n        positionenJson: String = ""\n',
        'saveReceipt params')
    save = require_replace(save,
        '                mieter = mieter,\n                positionenJson = positionenJson',
        '                mieter = mieter,\n                zahlungsart = normalizePaymentMethod(zahlungsart),\n                zahlungsartQuelle = if (normalizePaymentMethod(zahlungsart) == "Unbekannt") "UNBEKANNT" else "KI_SCAN",\n                zahlungsartConfidence = if (normalizePaymentMethod(zahlungsart) == "Unbekannt") 0.0 else 0.95,\n                positionenJson = positionenJson',
        'saveReceipt assignment')
    s = s[:save_start] + save + s[save_end:]
write(rel, s)

# 5) UI: scan field, detail edit, and backfill action
rel = 'app/src/main/java/com/example/ui/ReceiptAppUi.kt'
s = read(rel)
if 'var editZahlungsart by remember { mutableStateOf("Unbekannt") }' not in s:
    s = require_replace(s,
        '    var editMieter by remember { mutableStateOf("") }\n    var editPositionen by remember',
        '    var editMieter by remember { mutableStateOf("") }\n    var editZahlungsart by remember { mutableStateOf("Unbekannt") }\n    var editPositionen by remember',
        'scan payment state')
    s = require_replace(s,
        '            editIsEigenleistung = extracted.isEigenleistungSanierung\n',
        '            editIsEigenleistung = extracted.isEigenleistungSanierung\n            editZahlungsart = extracted.zahlungsart\n',
        'scan extracted payment')
    s = require_replace(s,
        '            // Positionen Editor Section\n',
        '''            OutlinedTextField(
                value = editZahlungsart,
                onValueChange = { editZahlungsart = it },
                label = { Text("Zahlungsart") },
                supportingText = { Text("Bar, Girocard/EC, Kreditkarte, Überweisung, Lastschrift, PayPal oder Unbekannt") },
                modifier = Modifier.fillMaxWidth().testTag("scan_payment_method")
            )

            // Positionen Editor Section
''',
        'scan payment field')
    s = require_replace(s,
        '                            mieter = editMieter,\n                            positionenJson =',
        '                            mieter = editMieter,\n                            zahlungsart = editZahlungsart,\n                            positionenJson =',
        'scan save payment')

if 'var editZahlungsart by remember(receipt)' not in s:
    s = require_replace(s,
        '    var editMieter by remember(receipt) { mutableStateOf(receipt.mieter) }\n    var editPositionen by remember(receipt)',
        '    var editMieter by remember(receipt) { mutableStateOf(receipt.mieter) }\n    var editZahlungsart by remember(receipt) { mutableStateOf(receipt.zahlungsart) }\n    var editPositionen by remember(receipt)',
        'detail payment state')
    detail_anchor = '                    ExposedDropdownMenuBox(\n                        expanded = wohneinheitExpanded,'
    idx = s.find(detail_anchor, s.find('fun ReceiptDetailDialog'))
    if idx < 0:
        raise SystemExit('detail payment field anchor missing')
    field = '''                    OutlinedTextField(
                        value = editZahlungsart,
                        onValueChange = { editZahlungsart = it },
                        label = { Text("Zahlungsart") },
                        modifier = Modifier.fillMaxWidth().testTag("edit_receipt_payment_method")
                    )

'''
    s = s[:idx] + field + s[idx:]
    detail_save_start = s.find('val updatedReceipt = receipt.copy(', s.find('fun ReceiptDetailDialog'))
    detail_save_end = s.find('                            )', detail_save_start)
    detail_chunk = s[detail_save_start:detail_save_end]
    if 'zahlungsart = editZahlungsart' not in detail_chunk:
        detail_chunk = detail_chunk.replace('                                mieter = editMieter,\n', '                                mieter = editMieter,\n                                zahlungsart = editZahlungsart,\n', 1)
        s = s[:detail_save_start] + detail_chunk + s[detail_save_end:]

if 'existing_payment_backfill_button' not in s:
    ledger = s.find('fun LedgerScreen(viewModel: ReceiptViewModel)')
    if ledger < 0:
        raise SystemExit('LedgerScreen missing')
    state_anchor = '    val context = LocalContext.current\n'
    pos = s.find(state_anchor, ledger)
    s = s[:pos+len(state_anchor)] + '    val isPaymentBackfillRunning by viewModel.isBackfillingPaymentMethods.collectAsState()\n    val paymentBackfillStatus by viewModel.paymentBackfillStatus.collectAsState()\n' + s[pos+len(state_anchor):]
    column_marker = '    val scrollState = rememberScrollState()\n'
    pos = s.find(column_marker, ledger)
    # insert card later at first main Column after dialogs; use Finanzamt dialog end marker if available
    body_marker = '    Column(\n        modifier = Modifier\n            .fillMaxSize()'
    body = s.find(body_marker, ledger)
    if body > 0:
        insert_at = s.find('{', body) + 1
        card = '''
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Zahlungsarten bestehender Belege", fontWeight = FontWeight.Bold, color = DarkNavy)
                Text(paymentBackfillStatus ?: "Unbekannte Zahlungsarten können aus Belegtext und vorhandenen Originalbildern nacherkannt werden.", fontSize = 11.sp, color = SlateGray)
                Button(
                    onClick = { viewModel.backfillPaymentMethods() },
                    enabled = !isPaymentBackfillRunning,
                    modifier = Modifier.fillMaxWidth().testTag("existing_payment_backfill_button")
                ) {
                    Text(if (isPaymentBackfillRunning) "Nacherkennung läuft …" else "Zahlungsarten nacherkennen")
                }
            }
        }
'''
        s = s[:insert_at] + card + s[insert_at:]
    else:
        raise SystemExit('Ledger content column missing')
write(rel, s)

print('payment_method_v3 patch applied successfully')
