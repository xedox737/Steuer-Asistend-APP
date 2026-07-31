package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptDuplicateAnalyzerTest {
    private fun receipt(
        id: Int,
        internalId: String,
        displayId: String,
        mainId: String,
        metadataId: String?,
        status: String = "ACTIVE",
        aussteller: String = "Firma"
    ) = Receipt(
        id = id,
        aussteller = aussteller,
        datum = "2025-01-01",
        uhrzeit = "",
        bruttobetrag = 10.0,
        hauptkategorie = "Sonstige Ausgaben",
        unterkategorie = "Sonstiges",
        kontoNr = "4970",
        beschreibung = "Test",
        internalId = internalId,
        displayId = displayId,
        driveFileId = mainId,
        driveMetadataFileId = metadataId,
        deletionStatus = status
    )

    private fun index(internalId: String, mainId: String, metadataId: String) = ReceiptIndexEntry(
        internalId = internalId,
        displayId = internalId,
        metadataFileId = metadataId,
        mainDriveFileId = mainId,
        aussteller = "Firma",
        rechnungsnummer = "",
        datum = "2025-01-01",
        bruttobetragCent = 1000,
        hauptkategorie = "Sonstige Ausgaben",
        unterkategorie = "Sonstiges",
        wohneinheit = "",
        massnahme = "",
        pruefstatus = "GEPRUEFT",
        freigabestatus = "OFFEN",
        exportstatus = "EXPORTBEREIT",
        syncStatus = "SYNCED",
        updatedAt = "2025-01-01T00:00:00"
    )

    @Test
    fun activeAndDeletedReceiptAreGroupedWithoutDeletingMainFile() {
        val main = "shared-main"
        val result = ReceiptDuplicateAnalyzer.analyze(
            receipts = listOf(
                receipt(1, "canonical", "BLG-1", main, "meta-1"),
                receipt(2, "duplicate", "BLG-2", main, "meta-2", "DELETED")
            ),
            indexEntries = listOf(index("canonical", main, "meta-1")),
            tombstones = emptyMap()
        )

        assertEquals(1, result.size)
        assertEquals("canonical", result.single().canonical.internalId)
        assertEquals(listOf("duplicate"), result.single().duplicatesToRemove.map { it.internalId })
        assertFalse(result.single().mainFileWillBeDeleted)
    }

    @Test
    fun bothDeletedStillKeepIndexedCanonical() {
        val main = "shared-main"
        val result = ReceiptDuplicateAnalyzer.analyze(
            receipts = listOf(
                receipt(1, "canonical", "BLG-1", main, "meta-1", "DELETED"),
                receipt(2, "duplicate", "BLG-2", main, "meta-2", "DELETE_PENDING")
            ),
            indexEntries = listOf(index("canonical", main, "meta-1")),
            tombstones = emptyMap()
        )

        assertEquals("canonical", result.single().canonical.internalId)
    }

    @Test
    fun sharedMetadataIsNeverMarkedOrphaned() {
        val main = "shared-main"
        val result = ReceiptDuplicateAnalyzer.analyze(
            receipts = listOf(
                receipt(1, "canonical", "BLG-1", main, "meta-shared"),
                receipt(2, "duplicate", "BLG-2", main, "meta-shared", "DELETED")
            ),
            indexEntries = listOf(index("canonical", main, "meta-shared")),
            tombstones = emptyMap()
        )

        assertTrue(result.single().metadataPlan.orphanMetadataFileIds.isEmpty())
        assertTrue("meta-shared" in result.single().metadataPlan.retainedMetadataFileIds)
    }

    @Test
    fun differentUnusedMetadataCanBeSuggestedAsOrphan() {
        val main = "shared-main"
        val result = ReceiptDuplicateAnalyzer.analyze(
            receipts = listOf(
                receipt(1, "canonical", "BLG-1", main, "meta-1"),
                receipt(2, "duplicate", "BLG-2", main, "meta-2", "DELETED")
            ),
            indexEntries = listOf(index("canonical", main, "meta-1")),
            tombstones = emptyMap()
        )

        assertEquals(setOf("meta-2"), result.single().metadataPlan.orphanMetadataFileIds)
    }

    @Test
    fun completenessThenOldestCreatedAtBreakTies() {
        val main = "shared-main"
        val records = listOf(
            DuplicateReceiptRecord(1, "a", "BLG-A", main, null, "ACTIVE", null, "2025-02-01", false, null, null, 5),
            DuplicateReceiptRecord(2, "b", "BLG-B", main, null, "ACTIVE", null, "2025-01-01", false, null, null, 5)
        )

        val canonical = ReceiptDuplicateAnalyzer.chooseCanonical(records, emptyList())
        assertEquals("b", canonical.internalId)
    }
}
