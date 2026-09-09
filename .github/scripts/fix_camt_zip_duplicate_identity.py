from pathlib import Path

path = Path('app/src/main/java/com/example/data/BankImportV8.kt')
text = path.read_text(encoding='utf-8')
old = '''                            val batch = BankImportParser.parseCamtV8(
                                xml = xml,
                                fallbackAccountName = name.substringAfterLast('/').substringBeforeLast('.').ifBlank { "Importiertes Konto" },
                                importedAt = importedAt,
                                importFileName = "$zipFileName!/$name",
                                importRunId = runId,
                                propertyId = propertyId,
                                unitId = unitId
                            )
                            batches += batch
'''
new = '''                            val entryFallbackName = name.substringAfterLast('/').substringBeforeLast('.').ifBlank { "Importiertes Konto" }
                            var batch = BankImportParser.parseCamtV8(
                                xml = xml,
                                fallbackAccountName = entryFallbackName,
                                importedAt = importedAt,
                                importFileName = "$zipFileName!/$name",
                                importRunId = runId,
                                propertyId = propertyId,
                                unitId = unitId
                            )
                            val existingSameIban = batch.account.iban.takeIf { it.isNotBlank() }?.let { iban ->
                                batches.firstOrNull { it.account.iban.equals(iban, ignoreCase = true) }
                            }
                            if (existingSameIban != null && existingSameIban.account.accountId != batch.account.accountId) {
                                batch = BankImportParser.parseCamtV8(
                                    xml = xml,
                                    fallbackAccountName = existingSameIban.account.displayName,
                                    importedAt = importedAt,
                                    importFileName = "$zipFileName!/$name",
                                    importRunId = runId,
                                    propertyId = propertyId,
                                    unitId = unitId
                                )
                            }
                            batches += batch
'''
if old not in text:
    raise SystemExit('target block not found')
path.write_text(text.replace(old, new, 1), encoding='utf-8')
