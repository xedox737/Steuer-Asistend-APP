from pathlib import Path

path = Path('app/src/main/java/com/example/data/BankImportV8.kt')
text = path.read_text(encoding='utf-8')
text = text.replace(
    '            val entry = entries.item(entryIndex) as? Element ?: run { errorRows++; continue }',
    '            val entry = entries.item(entryIndex) as? Element\n            if (entry == null) {\n                errorRows++\n                continue\n            }'
)
text = text.replace(
    '            val entryAmountElement = directOrFirstElement(entry, "Amt")\n            val entryRaw = entryAmountElement?.textContent?.trim()?.replace(\',\', \'.\')?.toDoubleOrNull()\n            val bookingDate = firstDate(entry, "BookgDt")\n            if (entryRaw == null || bookingDate.isBlank()) {',
    '            val entryAmountElement = directOrFirstElement(entry, "Amt")\n            if (entryAmountElement == null) {\n                errorRows++\n                continue\n            }\n            val entryRaw = entryAmountElement.textContent?.trim()?.replace(\',\', \'.\')?.toDoubleOrNull()\n            val bookingDate = firstDate(entry, "BookgDt")\n            if (entryRaw == null || bookingDate.isBlank()) {'
)
anchor = '''object BankZipImportLimits {
    const val MAX_ZIP_BYTES: Long = 20L * 1024 * 1024
    const val MAX_ENTRIES = 50
    const val MAX_TOTAL_UNCOMPRESSED_BYTES: Long = 50L * 1024 * 1024
    const val MAX_XML_BYTES: Long = 10L * 1024 * 1024
    const val MAX_COMPRESSION_RATIO = 200.0
}
'''
replacement = anchor + '''
/** Production defaults plus injectable limits for deterministic safety tests. */
data class BankZipSafetyLimits(
    val maxZipBytes: Long = BankZipImportLimits.MAX_ZIP_BYTES,
    val maxEntries: Int = BankZipImportLimits.MAX_ENTRIES,
    val maxTotalUncompressedBytes: Long = BankZipImportLimits.MAX_TOTAL_UNCOMPRESSED_BYTES,
    val maxXmlBytes: Long = BankZipImportLimits.MAX_XML_BYTES,
    val maxCompressionRatio: Double = BankZipImportLimits.MAX_COMPRESSION_RATIO
)
'''
if anchor not in text:
    raise SystemExit('ZIP limits anchor not found')
text = text.replace(anchor, replacement, 1)
text = text.replace(
    '        unitId: String = ""\n    ): BankZipParsedImport {\n        require(zipBytes.size.toLong() <= BankZipImportLimits.MAX_ZIP_BYTES)',
    '        unitId: String = "",\n        limits: BankZipSafetyLimits = BankZipSafetyLimits()\n    ): BankZipParsedImport {\n        require(zipBytes.size.toLong() <= limits.maxZipBytes)'
)
text = text.replace('require(totalEntries <= BankZipImportLimits.MAX_ENTRIES)', 'require(totalEntries <= limits.maxEntries)')
text = text.replace('require(totalUncompressed <= BankZipImportLimits.MAX_TOTAL_UNCOMPRESSED_BYTES)', 'require(totalUncompressed <= limits.maxTotalUncompressedBytes)')
text = text.replace('require(entryBytes <= BankZipImportLimits.MAX_XML_BYTES)', 'require(entryBytes <= limits.maxXmlBytes)')
text = text.replace('> BankZipImportLimits.MAX_COMPRESSION_RATIO)', '> limits.maxCompressionRatio)')
path.write_text(text, encoding='utf-8')

test_path = Path('app/src/test/java/com/example/data/BankCamtZipV8AcceptanceTest.kt')
test = test_path.read_text(encoding='utf-8')
marker = '    @Test fun zipPathTraversalIsBlockedAndNestedZipIsNeverImported() {'
extra = r'''    @Test fun zipSafetyLimitsBlockLargeXmlTotalSizeAndImplausibleCompression() {
        val xml = camt052().toByteArray()
        val one = zipOf("large.xml" to xml)
        assertTrue(runCatching {
            BankZipImportParser.parse(
                one, "large.zip",
                limits = BankZipSafetyLimits(maxXmlBytes = (xml.size - 1).toLong())
            )
        }.isFailure)

        val two = zipOf("a.xml" to xml, "b.xml" to xml)
        assertTrue(runCatching {
            BankZipImportParser.parse(
                two, "total.zip",
                limits = BankZipSafetyLimits(maxTotalUncompressedBytes = (xml.size + 10).toLong())
            )
        }.isFailure)

        assertTrue(runCatching {
            BankZipImportParser.parse(
                one, "ratio.zip",
                limits = BankZipSafetyLimits(maxCompressionRatio = 1.01)
            )
        }.isFailure)
    }

    @Test fun camt052MalformedRowsAreReportedWithoutInventingTransactions() {
        val malformed = camt052().replace(
            "<Ntry><Amt Ccy=\"EUR\">50.00</Amt><CdtDbtInd>CRDT</CdtDbtInd><Sts><Cd>PDNG</Cd></Sts><BookgDt><Dt>2026-09-09</Dt></BookgDt></Ntry>",
            "<Ntry><Amt Ccy=\"EUR\">not-a-number</Amt><CdtDbtInd>DBIT</CdtDbtInd><Sts><Cd>BOOK</Cd></Sts><BookgDt><Dt>2026-09-09</Dt></BookgDt></Ntry>" +
            "<Ntry><Amt Ccy=\"EUR\">10.00</Amt><CdtDbtInd>DBIT</CdtDbtInd><Sts><Cd>BOOK</Cd></Sts></Ntry>"
        )
        val batch = BankImportParser.parseCamtV8(malformed, "SYNTHETIC")
        assertEquals(2, batch.transactions.size)
        assertEquals(2, batch.errorRows)
    }

    @Test fun duplicateSupportedXmlInsideOneZipProducesStableDuplicateIdentities() {
        val parsed = BankZipImportParser.parse(
            zipOf("a.xml" to camt052().toByteArray(), "b.xml" to camt052().toByteArray()),
            "duplicates.zip"
        )
        assertEquals(2, parsed.batches.size)
        assertEquals(parsed.batches[0].transactions.map { it.transactionId }, parsed.batches[1].transactions.map { it.transactionId })
    }

'''
if marker not in test:
    raise SystemExit('test marker not found')
test = test.replace(marker, extra + marker, 1)
test_path.write_text(test, encoding='utf-8')
