from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VM = ROOT / "app/src/main/java/com/example/ui/ReceiptViewModel.kt"
UI = ROOT / "app/src/main/java/com/example/ui/ReceiptAppUi.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"{label}: anchor not found")
    return text.replace(old, new, 1)


# ---------------- ViewModel ----------------
s = VM.read_text(encoding="utf-8")
anchor = '''    private val _isBackfillingPaymentMethods = MutableStateFlow(false)
    val isBackfillingPaymentMethods = _isBackfillingPaymentMethods.asStateFlow()
'''
block = r'''    data class DescriptionBackfillCandidate(
        val receiptId: Int,
        val displayId: String,
        val aussteller: String,
        val oldDescription: String,
        val newDescription: String,
        val isSafe: Boolean,
        val reason: String
    )

    private val _isBackfillingDescriptions = MutableStateFlow(false)
    val isBackfillingDescriptions = _isBackfillingDescriptions.asStateFlow()
    private val _descriptionBackfillStatus = MutableStateFlow<String?>(null)
    val descriptionBackfillStatus = _descriptionBackfillStatus.asStateFlow()
    private val _descriptionBackfillCandidates = MutableStateFlow<List<DescriptionBackfillCandidate>>(emptyList())
    val descriptionBackfillCandidates = _descriptionBackfillCandidates.asStateFlow()

    private fun normalizeDescriptionCompare(value: String): String = value
        .lowercase(Locale.GERMANY)
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun legacyDescriptionIssue(receipt: Receipt): Pair<Boolean, String>? {
        val description = receipt.beschreibung.trim()
        val vendor = receipt.aussteller.trim()
        if (description.isBlank()) return true to "Beschreibung fehlt"

        val descNorm = normalizeDescriptionCompare(description)
        val vendorNorm = normalizeDescriptionCompare(vendor)
        if (vendorNorm.isNotBlank() && descNorm == vendorNorm) {
            return true to "Beschreibung entspricht nur dem Aussteller"
        }

        val addressLike = Regex("\\b\\d{5}\\b").containsMatchIn(description) ||
            Regex("(?i)\\b(straße|strasse|str\\.|weg|allee|platz|gasse|ring|chaussee)\\b").containsMatchIn(description)
        val startsWithVendor = vendorNorm.length >= 4 && descNorm.startsWith(vendorNorm)
        if (startsWithVendor && addressLike) {
            return true to "Aussteller/Adresse statt Leistungszweck"
        }
        return null
    }

    private suspend fun loadDescriptionBackfillBitmaps(receipt: Receipt): List<Bitmap> {
        var paths = receipt.imageUrl.split(',').map { it.trim() }.filter { it.isNotBlank() }
        val hasLocalOriginal = paths.any { path -> runCatching { File(path).exists() && File(path).isFile }.getOrDefault(false) }

        if (!hasLocalOriginal && !receipt.driveFileId.isNullOrBlank()) {
            val email = _googleAccountEmail.value
            if (!email.isNullOrBlank() && _isDriveConnected.value) {
                runCatching {
                    val token = getValidToken(email)
                    val downloadedPath = drivePersistenceRepository.downloadDocumentOnDemand(token, receipt)
                    paths = listOf(downloadedPath)
                }.onFailure {
                    Log.w("ReceiptViewModel", "Original für Beschreibungs-Nacherkennung konnte nicht aus Drive geladen werden", it)
                }
            }
        }

        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            paths.flatMap { path ->
                runCatching {
                    val file = File(path)
                    if (!file.exists() || !file.isFile) return@runCatching emptyList<Bitmap>()
                    if (file.extension.equals("pdf", ignoreCase = true)) {
                        val rendered = mutableListOf<Bitmap>()
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                            PdfRenderer(descriptor).use { renderer ->
                                val pagesToRead = minOf(renderer.pageCount, 3)
                                for (index in 0 until pagesToRead) {
                                    renderer.openPage(index).use { page ->
                                        val scale = minOf(2.0f, 1600.0f / page.width.coerceAtLeast(1))
                                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        rendered.add(bitmap)
                                    }
                                }
                            }
                        }
                        rendered
                    } else {
                        listOfNotNull(BitmapFactory.decodeFile(file.absolutePath))
                    }
                }.getOrElse {
                    Log.w("ReceiptViewModel", "Original für Beschreibungs-Nacherkennung konnte nicht gerendert werden: $path", it)
                    emptyList()
                }
            }
        }
    }

    private suspend fun detectLegacyDescription(receipt: Receipt): String? {
        val bitmaps = loadDescriptionBackfillBitmaps(receipt)
        if (bitmaps.isEmpty()) return null
        return try {
            val learned = getUserLearnedRulesPromptContext()
            val prompt = """
                Analysiere ausschließlich den Zweck dieses Originalbelegs neu.
                Das Feld beschreibung soll eine kurze, sachliche deutsche Beschreibung enthalten, was gekauft, geliefert oder geleistet wurde.
                Keine Firmenanschrift, keine reine Wiederholung des Ausstellers, keine Bankdaten und nichts erfinden.
                Beispiele: "Kauf von Wandfarbe inklusive Versandkosten", "Maklerprovision für Immobilienkauf", "Elektroarbeiten Wohnung OG links".
                Wenn der Zweck nicht zuverlässig erkennbar ist, lasse beschreibung leer.
            """.trimIndent()
            val extracted = when (_aiProviderState.value.provider) {
                ReceiptAnalysisProvider.GEMINI -> {
                    val key = AiProviderSettings.getGeminiKey(getApplication())
                    try { GeminiClient.analyzeReceipt(receiptText = prompt, bitmaps = bitmaps, userLearnedRulesContext = learned, apiKeyOverride = key) }
                    finally { key?.fill('\u0000') }
                }
                ReceiptAnalysisProvider.OPENAI -> {
                    val key = AiProviderSettings.getOpenAiKey(getApplication()) ?: return null
                    try { OpenAiClient.analyzeReceipt(apiKey = key, model = _aiProviderState.value.openAiModel, receiptText = prompt, bitmaps = bitmaps, userLearnedRulesContext = learned) }
                    finally { key.fill('\u0000') }
                }
            }
            val candidate = extracted?.beschreibung.orEmpty().trim()
            val candidateNorm = normalizeDescriptionCompare(candidate)
            val oldNorm = normalizeDescriptionCompare(receipt.beschreibung)
            val vendorNorm = normalizeDescriptionCompare(receipt.aussteller)
            when {
                candidate.length < 6 -> null
                candidateNorm == oldNorm -> null
                vendorNorm.isNotBlank() && candidateNorm == vendorNorm -> null
                Regex("\\b\\d{5}\\b").containsMatchIn(candidate) && candidateNorm.startsWith(vendorNorm) -> null
                else -> candidate.take(240)
            }
        } finally {
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    fun analyzeLegacyDescriptions() {
        if (_isBackfillingDescriptions.value) return
        viewModelScope.launch {
            _isBackfillingDescriptions.value = true
            _descriptionBackfillCandidates.value = emptyList()
            try {
                val candidates = receipts.value.mapNotNull { receipt ->
                    legacyDescriptionIssue(receipt)?.let { issue -> Triple(receipt, issue.first, issue.second) }
                }
                if (candidates.isEmpty()) {
                    _descriptionBackfillStatus.value = "Keine offensichtlich fehlerhaften Altbeschreibungen gefunden."
                    return@launch
                }

                val suggestions = mutableListOf<DescriptionBackfillCandidate>()
                candidates.forEachIndexed { index, (receipt, isSafe, reason) ->
                    _descriptionBackfillStatus.value = "Prüfe Altbeleg ${index + 1} von ${candidates.size} …"
                    val detected = runCatching { detectLegacyDescription(receipt) }.getOrNull()
                    if (!detected.isNullOrBlank()) {
                        suggestions += DescriptionBackfillCandidate(
                            receiptId = receipt.id,
                            displayId = receipt.getEffectiveDisplayId(),
                            aussteller = receipt.aussteller,
                            oldDescription = receipt.beschreibung,
                            newDescription = detected,
                            isSafe = isSafe,
                            reason = reason
                        )
                    }
                }
                _descriptionBackfillCandidates.value = suggestions
                val safeCount = suggestions.count { it.isSafe }
                _descriptionBackfillStatus.value = "${candidates.size} verdächtige Altbelege geprüft: ${suggestions.size} Vorschläge, davon $safeCount sicher vorausgewählt."
            } finally {
                _isBackfillingDescriptions.value = false
            }
        }
    }

    fun applyDescriptionBackfill(receiptIds: Set<Int>) {
        if (receiptIds.isEmpty()) return
        viewModelScope.launch {
            val selected = _descriptionBackfillCandidates.value.filter { it.receiptId in receiptIds }
            var updatedCount = 0
            var skippedCount = 0
            selected.forEachIndexed { index, candidate ->
                _descriptionBackfillStatus.value = "Übernehme Beschreibung ${index + 1} von ${selected.size} …"
                val persisted = repository.getReceiptById(candidate.receiptId)
                if (persisted == null || persisted.beschreibung != candidate.oldDescription) {
                    skippedCount++
                    return@forEachIndexed
                }

                val proposed = persisted.copy(beschreibung = candidate.newDescription)
                val receiptToSave = com.example.data.DatevApprovalInvalidationPolicy.apply(persisted, proposed)
                repository.insert(receiptToSave)
                if (FirestoreService.isCloudActive()) {
                    FirestoreService.saveReceipt(receiptToSave)
                }
                if (_isDriveConnected.value && _autoDriveBackup.value) {
                    runCatching { uploadReceiptToDriveInternal(receiptToSave) }
                        .onFailure { Log.w("ReceiptViewModel", "Korrigierte Beschreibung konnte nicht sofort nach Drive synchronisiert werden", it) }
                }
                updatedCount++
            }
            _descriptionBackfillCandidates.value = _descriptionBackfillCandidates.value.filterNot { it.receiptId in receiptIds }
            _descriptionBackfillStatus.value = "Fertig: $updatedCount Beschreibungen aktualisiert" + if (skippedCount > 0) ", $skippedCount wegen zwischenzeitlicher Änderungen übersprungen." else "."
        }
    }

    fun clearDescriptionBackfillPreview() {
        _descriptionBackfillCandidates.value = emptyList()
    }

''' + anchor
s = replace_once(s, anchor, block, "ViewModel description backfill insertion")
VM.write_text(s, encoding="utf-8")


# ---------------- UI ----------------
s = UI.read_text(encoding="utf-8")
state_anchor = '''    val isPaymentBackfillRunning by viewModel.isBackfillingPaymentMethods.collectAsState()
    val paymentBackfillStatus by viewModel.paymentBackfillStatus.collectAsState()
'''
state_new = state_anchor + '''    val isDescriptionBackfillRunning by viewModel.isBackfillingDescriptions.collectAsState()
    val descriptionBackfillStatus by viewModel.descriptionBackfillStatus.collectAsState()
    val descriptionBackfillCandidates by viewModel.descriptionBackfillCandidates.collectAsState()
    var showDescriptionBackfillPreview by remember { mutableStateOf(false) }
'''
s = replace_once(s, state_anchor, state_new, "Ledger description states")

card_anchor = '''        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Zahlungsarten bestehender Belege", fontWeight = FontWeight.Bold, color = DarkNavy)
'''
card_new = '''        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Altbelege – Beschreibung nacherkennen", fontWeight = FontWeight.Bold, color = DarkNavy)
                Text(
                    descriptionBackfillStatus ?: "Prüft nur leere oder offensichtlich durch Aussteller/Adresse ersetzte Beschreibungen. Andere Belegdaten bleiben unverändert.",
                    fontSize = 11.sp,
                    color = SlateGray
                )
                Button(
                    onClick = { viewModel.analyzeLegacyDescriptions() },
                    enabled = !isDescriptionBackfillRunning,
                    modifier = Modifier.fillMaxWidth().testTag("legacy_description_backfill_button")
                ) {
                    Text(if (isDescriptionBackfillRunning) "Altbelege werden geprüft …" else "Altbelege prüfen")
                }
                if (descriptionBackfillCandidates.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showDescriptionBackfillPreview = true },
                        modifier = Modifier.fillMaxWidth().testTag("legacy_description_preview_button")
                    ) {
                        Text("Vorschau (${descriptionBackfillCandidates.size})")
                    }
                }
            }
        }

        if (showDescriptionBackfillPreview && descriptionBackfillCandidates.isNotEmpty()) {
            var selectedIds by remember(descriptionBackfillCandidates) {
                mutableStateOf(descriptionBackfillCandidates.filter { it.isSafe }.map { it.receiptId }.toSet())
            }
            AlertDialog(
                onDismissRequest = { showDescriptionBackfillPreview = false },
                title = { Text("Beschreibungen prüfen", fontWeight = FontWeight.Bold, color = DarkNavy) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Sichere Treffer sind vorausgewählt. Es wird ausschließlich das Feld Beschreibung/Zweck geändert.",
                            fontSize = 11.sp,
                            color = SlateGray
                        )
                        descriptionBackfillCandidates.forEach { candidate ->
                            val selected = candidate.receiptId in selectedIds
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = BorderStroke(1.dp, BorderColor)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = selected,
                                            onCheckedChange = { checked ->
                                                selectedIds = if (checked) selectedIds + candidate.receiptId else selectedIds - candidate.receiptId
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(candidate.displayId, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkNavy)
                                            Text(candidate.aussteller, fontSize = 10.sp, color = SlateGray)
                                        }
                                        Text(if (candidate.isSafe) "Sicher" else "Prüfen", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (candidate.isSafe) EmeraldGreen else Color(0xFFD97706))
                                    }
                                    Text("Grund: ${candidate.reason}", fontSize = 10.sp, color = SlateGray)
                                    Text("Alt: ${candidate.oldDescription.ifBlank { "(leer)" }}", fontSize = 11.sp, color = Color(0xFF991B1B))
                                    Text("Neu: ${candidate.newDescription}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = DarkNavy)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.applyDescriptionBackfill(selectedIds)
                            showDescriptionBackfillPreview = false
                        },
                        enabled = selectedIds.isNotEmpty()
                    ) { Text("Ausgewählte übernehmen") }
                },
                dismissButton = {
                    TextButton(onClick = { showDescriptionBackfillPreview = false }) { Text("Abbrechen") }
                }
            )
        }

''' + card_anchor
s = replace_once(s, card_anchor, card_new, "Ledger description backfill card")
UI.write_text(s, encoding="utf-8")

print("Legacy description backfill feature applied.")
