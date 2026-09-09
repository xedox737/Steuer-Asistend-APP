from pathlib import Path

vm_path = Path('app/src/main/java/com/example/ui/ReceiptViewModel.kt')
text = vm_path.read_text(encoding='utf-8')
start = text.index('    fun importBankFile(uri: android.net.Uri) {')
end = text.index('    fun startReceiptFromBankTransaction', start)
replacement = r'''    fun importBankFile(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _bankImportStatus.value = "Kontoauszug wird eingelesen …"
            try {
                val resolver = getApplication<Application>().contentResolver
                val displayName = runCatching {
                    resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                }.getOrNull().orEmpty().ifBlank { "Kontoauszug" }
                val bytes = resolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    val maxBytes = com.example.data.BankZipImportLimits.MAX_ZIP_BYTES
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > maxBytes) throw IllegalArgumentException("Importdatei überschreitet die zulässige Maximalgröße.")
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: throw IllegalArgumentException("Datei konnte nicht gelesen werden.")
                val now = java.time.Instant.now().toString()
                val dao = database.bankDao()

                suspend fun persistBatch(batch: com.example.data.BankImportBatch): Pair<Int, Int> {
                    val existingAccount = dao.getAccount(batch.account.accountId)
                    dao.upsertAccount(
                        batch.account.copy(
                            displayName = existingAccount?.displayName?.takeIf { it.isNotBlank() } ?: batch.account.displayName,
                            bankName = existingAccount?.bankName.orEmpty().ifBlank { batch.account.bankName },
                            accountHolder = existingAccount?.accountHolder.orEmpty().ifBlank { batch.account.accountHolder },
                            createdAt = existingAccount?.createdAt?.takeIf { it.isNotBlank() } ?: batch.account.createdAt,
                            updatedAt = now
                        )
                    )
                    val results = dao.insertTransactions(batch.transactions)
                    val inserted = results.count { it != -1L }
                    return inserted to (batch.transactions.size - inserted)
                }

                if (com.example.data.BankZipImportParser.looksLikeZip(bytes)) {
                    _bankImportStatus.value = "ZIP erkannt • Inhalt wird lokal und sicher geprüft …"
                    val parsed = com.example.data.BankZipImportParser.parse(
                        zipBytes = bytes,
                        zipFileName = displayName,
                        importedAt = now
                    )
                    var inserted = 0
                    var duplicates = 0
                    parsed.batches.forEach { batch ->
                        val result = persistBatch(batch)
                        inserted += result.first
                        duplicates += result.second
                    }
                    val read = parsed.batches.sumOf { it.transactions.size }
                    val rowErrors = parsed.batches.sumOf { it.errorRows }
                    _bankImportStatus.value = buildString {
                        append("ZIP: ${parsed.totalEntries} Einträge • ${parsed.xmlEntries} XML • ${parsed.supportedCamtFiles} CAMT unterstützt")
                        append(" • $read Buchungen gelesen • $inserted neu • $duplicates Dubletten")
                        append(" • ${parsed.unsupportedFiles} nicht unterstützt • ${parsed.faultyFiles} fehlerhafte Dateien")
                        if (rowErrors > 0) append(" • $rowErrors fehlerhafte Buchungen")
                        parsed.entryReports.forEach { report ->
                            append("\n• ${report.entryName.take(120)}: ${report.status}")
                            if (report.format.isNotBlank()) append(" (${report.format})")
                            append(" – ${report.message}")
                        }
                    }
                } else {
                    val textContent = bytes.toString(Charsets.UTF_8)
                    val trimmed = textContent.trimStart()
                    val batch = if (trimmed.startsWith("<")) {
                        val detected = com.example.data.BankCamtV8FormatDetector.detect(textContent)
                        if (detected == com.example.data.BankCamtFormat.UNSUPPORTED) {
                            throw IllegalArgumentException("XML-Format wird nicht unterstützt. Erwartet wird CAMT.052.001.08 oder CAMT.053.001.08.")
                        }
                        _bankImportStatus.value = "${detected.label} erkannt • Buchungen werden geprüft …"
                        com.example.data.BankImportParser.parseCamtV8(
                            xml = textContent,
                            fallbackAccountName = displayName.substringBeforeLast('.'),
                            importedAt = now,
                            importFileName = displayName,
                            importRunId = com.example.data.BankTransactionIdentity.importRunId(detected.source, displayName, now)
                        )
                    } else {
                        _bankImportStatus.value = "CSV erkannt • Buchungen werden geprüft …"
                        com.example.data.BankImportParser.parseCsv(
                            text = textContent,
                            fallbackAccountName = displayName.substringBeforeLast('.'),
                            importedAt = now,
                            importFileName = displayName,
                            importRunId = com.example.data.BankTransactionIdentity.importRunId("CSV", displayName, now)
                        )
                    }
                    val (inserted, duplicates) = persistBatch(batch)
                    val skippedOrInvalid = batch.skippedRows + batch.errorRows
                    _bankImportStatus.value = buildString {
                        append("${batch.format}: ${batch.transactions.size} gültige Buchungen erkannt • $inserted neu • $duplicates Dubletten")
                        if (skippedOrInvalid > 0) append(" • $skippedOrInvalid übersprungen/fehlerhaft")
                        append(".")
                    }
                }
            } catch (e: Exception) {
                Log.e("ReceiptViewModel", "Bank import failed: ${e.javaClass.simpleName}")
                _bankImportStatus.value = "Import fehlgeschlagen: ${e.message ?: "unbekannter Fehler"}"
            }
        }
    }

'''
vm_path.write_text(text[:start] + replacement + text[end:], encoding='utf-8')

ui_path = Path('app/src/main/java/com/example/ui/BankFeature.kt')
ui = ui_path.read_text(encoding='utf-8')
old_mimes = 'importLauncher.launch(arrayOf("text/csv", "text/xml", "application/xml", "application/octet-stream", "text/plain"))'
new_mimes = 'importLauncher.launch(arrayOf("text/csv", "text/xml", "application/xml", "application/zip", "application/x-zip-compressed", "application/octet-stream", "text/plain"))'
if old_mimes not in ui:
    raise SystemExit('BankFeature MIME launcher anchor not found')
ui = ui.replace(old_mimes, new_mimes, 1)
ui = ui.replace(
    'if (transactions.isEmpty()) "Importiere zuerst einen CSV- oder CAMT.053-Kontoauszug." else "Für die aktuelle Kontoauswahl ist hier nichts zu prüfen."',
    'if (transactions.isEmpty()) "Importiere zuerst CSV, CAMT.052/053 V8 oder ein ZIP mit CAMT-Dateien." else "Für die aktuelle Kontoauswahl ist hier nichts zu prüfen."',
    1
)
ui_path.write_text(ui, encoding='utf-8')
