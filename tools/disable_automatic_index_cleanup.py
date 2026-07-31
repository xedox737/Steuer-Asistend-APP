from pathlib import Path

path = Path("app/src/main/java/com/example/data/DrivePersistenceRepository.kt")
text = path.read_text(encoding="utf-8")
start_marker = "    suspend fun sanitizeIndexEntries("
end_marker = "\n    fun validateIndex("
start = text.index(start_marker)
end = text.index(end_marker, start)
replacement = '''    /**
     * Legacy compatibility hook. This method is intentionally read-only.
     * Duplicate cleanup must only happen through the explicit preview and confirmation flow.
     */
    suspend fun sanitizeIndexEntries(
        entries: List<ReceiptIndexEntry>,
        accessToken: String? = null
    ): List<ReceiptIndexEntry> {
        if (!accessToken.isNullOrBlank()) {
            Log.i(TAG, "Read-only index inspection: automatic cleanup is disabled (${entries.size} entries).")
        }
        return entries.toList()
    }
'''
updated = text[:start] + replacement + text[end:]
if updated == text:
    raise SystemExit("No change produced")
path.write_text(updated, encoding="utf-8")
