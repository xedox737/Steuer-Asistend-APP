package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadOnlyReceiptIndexSanitizerTest {
    @Test
    fun diagnosisDoesNotChangeInputAndSelectsMatchingEntry() {
        val local = receipt("one", "main-1", "meta-good")
        val stale = index("one", "main-old", "meta-old", "2026-01-01")
        val matching = index("one", "main-1", "meta-good", "2026-01-02")
        val input = listOf(stale, matching)

        val result = ReadOnlyReceiptIndexSanitizer.diagnose(input, listOf(local))

        assertEquals(input, result.originalEntries)
        assertEquals(matching, result.canonicalEntries.single())
        assertEquals(listOf(stale), result.duplicateEntries)
        assertEquals(2, input.size)
    }

    @Test
    fun reportsMissingLocalAndSharedDriveReferencesWithoutDeletingAnything() {
        val entries = listOf(
            index("one", "shared-main", "shared-meta", "2026-01-01"),
            index("two", "shared-main", "shared-meta", "2026-01-02")
        )

        val result = ReadOnlyReceiptIndexSanitizer.diagnose(entries, listOf(receipt("one", "shared-main", "shared-meta")))

        assertTrue(result.hasIssues)
        assertEquals(setOf("two"), result.missingLocalInternalIds)
        assertEquals(listOf("one", "two"), result.sharedMainFileIds.getValue("shared-main"))
        assertEquals(listOf("one", "two"), result.sharedMetadataFileIds.getValue("shared-meta"))
        assertEquals(entries, result.originalEntries)
    }

    @Test
    fun cleanIndexHasNoIssues() {
        val entry = index("one", "main", "meta", "2026-01-01")
        val result = ReadOnlyReceiptIndexSanitizer.diagnose(
            listOf(entry),
            listOf(receipt("one", "main", "meta"))
        )

        assertFalse(result.hasIssues)
        assertEquals(listOf(entry), result.canonicalEntries)
    }

    private fun receipt(internalId: String, mainId: String, metadataId: String) = Receipt(
        aussteller = "Test",
        datum = "2026-01-01",
        uhrzeit = "",
        bruttobetrag = 1.0,
        hauptkategorie = "Test",
        unterkategorie = "Test",
        kontoNr = "",
        beschreibung = "",
        internalId = internalId,
        driveFileId = mainId,
        driveMetadataFileId = metadataId
    )

    private fun index(
        internalId: String,
        mainId: String,
        metadataId: String,
        updatedAt: String
    ) = ReceiptIndexEntry(
        internalId = internalId,
        displayId = "BLG-$internalId",
        metadataFileId = metadataId,
        mainDriveFileId = mainId,
        aussteller = "Test",
        rechnungsnummer = "R-1",
        datum = "2026-01-01",
        bruttobetragCent = 100,
        hauptkategorie = "Test",
        unterkategorie = "Test",
        wohneinheit = "",
        massnahme = "",
        pruefstatus = "",
        freigabestatus = "",
        exportstatus = "",
        syncStatus = "SYNCED",
        updatedAt = updatedAt
    )
}
