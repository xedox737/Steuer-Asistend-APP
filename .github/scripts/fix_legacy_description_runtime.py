from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PATH = ROOT / "app/src/main/java/com/example/ui/ReceiptViewModel.kt"
s = PATH.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global s
    if new in s:
        return
    if old not in s:
        raise SystemExit(f"{label}: anchor not found")
    s = s.replace(old, new, 1)

# 1) Restored receipts with a Drive file must try Drive whenever a Google account is known.
replace_once(
'''        if (!hasLocalOriginal && !receipt.driveFileId.isNullOrBlank()) {
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
        }''',
'''        if (!hasLocalOriginal && !receipt.driveFileId.isNullOrBlank()) {
            val email = _googleAccountEmail.value
            if (!email.isNullOrBlank()) {
                runCatching {
                    val token = getValidToken(email)
                    val downloadedPath = drivePersistenceRepository.downloadDocumentOnDemand(token, receipt)
                    paths = listOf(downloadedPath)
                }.onFailure {
                    Log.w("ReceiptViewModel", "Original für Beschreibungs-Nacherkennung konnte nicht aus Drive geladen werden", it)
                }
            }
        }''',
"Drive fallback guard"
)

# 2) Add a lightweight failure reason so 0 suggestions is diagnosable.
replace_once(
'''    private val _descriptionBackfillCandidates = MutableStateFlow<List<DescriptionBackfillCandidate>>(emptyList())
    val descriptionBackfillCandidates = _descriptionBackfillCandidates.asStateFlow()
''',
'''    private val _descriptionBackfillCandidates = MutableStateFlow<List<DescriptionBackfillCandidate>>(emptyList())
    val descriptionBackfillCandidates = _descriptionBackfillCandidates.asStateFlow()
    private var descriptionBackfillMissingOriginals = 0
    private var descriptionBackfillAiErrors = 0
''',
"diagnostic counters"
)

replace_once(
'''    private suspend fun detectLegacyDescription(receipt: Receipt): String? {
        val bitmaps = loadDescriptionBackfillBitmaps(receipt)
        if (bitmaps.isEmpty()) return null
        return try {''',
'''    private suspend fun detectLegacyDescription(receipt: Receipt): String? {
        val bitmaps = loadDescriptionBackfillBitmaps(receipt)
        if (bitmaps.isEmpty()) {
            descriptionBackfillMissingOriginals++
            return null
        }
        return try {''',
"missing original counter"
)

# 3) Preflight API key and preserve AI errors instead of swallowing all exceptions silently.
replace_once(
'''        viewModelScope.launch {
            _isBackfillingDescriptions.value = true
            _descriptionBackfillCandidates.value = emptyList()
            try {
                val candidates = receipts.value.mapNotNull { receipt ->''',
'''        viewModelScope.launch {
            _isBackfillingDescriptions.value = true
            _descriptionBackfillCandidates.value = emptyList()
            descriptionBackfillMissingOriginals = 0
            descriptionBackfillAiErrors = 0
            try {
                val provider = _aiProviderState.value.provider
                val hasApiKey = when (provider) {
                    ReceiptAnalysisProvider.GEMINI -> !AiProviderSettings.getGeminiKey(getApplication()).isNullOrEmpty()
                    ReceiptAnalysisProvider.OPENAI -> !AiProviderSettings.getOpenAiKey(getApplication()).isNullOrEmpty()
                }
                if (!hasApiKey) {
                    _descriptionBackfillStatus.value = "Keine KI-API konfiguriert. Bitte zuerst in den Einstellungen einen API-Schlüssel für ${provider.name} hinterlegen."
                    return@launch
                }

                val candidates = receipts.value.mapNotNull { receipt ->''',
"API preflight"
)

replace_once(
'''                    val detected = runCatching { detectLegacyDescription(receipt) }.getOrNull()
                    if (!detected.isNullOrBlank()) {''',
'''                    val detected = runCatching { detectLegacyDescription(receipt) }
                        .onFailure {
                            descriptionBackfillAiErrors++
                            Log.w("ReceiptViewModel", "Altbeleg-Beschreibung konnte nicht analysiert werden: ${receipt.getEffectiveDisplayId()}", it)
                        }
                        .getOrNull()
                    if (!detected.isNullOrBlank()) {''',
"AI error counter"
)

replace_once(
'''                _descriptionBackfillCandidates.value = suggestions
                val safeCount = suggestions.count { it.isSafe }
                _descriptionBackfillStatus.value = "${candidates.size} verdächtige Altbelege geprüft: ${suggestions.size} Vorschläge, davon $safeCount sicher vorausgewählt."
''',
'''                _descriptionBackfillCandidates.value = suggestions
                val safeCount = suggestions.count { it.isSafe }
                _descriptionBackfillStatus.value = if (suggestions.isNotEmpty()) {
                    "${candidates.size} verdächtige Altbelege geprüft: ${suggestions.size} Vorschläge, davon $safeCount sicher vorausgewählt."
                } else {
                    buildString {
                        append("${candidates.size} verdächtige Altbelege geprüft: 0 Vorschläge.")
                        if (descriptionBackfillMissingOriginals > 0) append(" ${descriptionBackfillMissingOriginals} Originaldateien konnten nicht geladen werden.")
                        if (descriptionBackfillAiErrors > 0) append(" ${descriptionBackfillAiErrors} KI-Analysen sind fehlgeschlagen.")
                        if (descriptionBackfillMissingOriginals == 0 && descriptionBackfillAiErrors == 0) append(" Die KI hat keinen ausreichend sicheren neuen Zweck erkannt.")
                    }
                }
''',
"diagnostic status"
)

PATH.write_text(s, encoding="utf-8")
print("Legacy description runtime fix applied")
