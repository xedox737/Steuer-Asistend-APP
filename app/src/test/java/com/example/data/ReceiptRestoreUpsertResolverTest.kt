package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptRestoreUpsertResolverTest {
    @Test
    fun sameInternalIdUpdatesExistingRoomRow() {
        val existing = receipt(id = 7, internalId = "same", mainId = "main-a")
        val incoming = receipt(id = 0, internalId = "same", mainId = "main-a", aussteller = "Neu")

        val result = ReceiptRestoreUpsertResolver.resolve(incoming, listOf(existing))

        assertEquals(ReceiptUpsertResolution.Match.INTERNAL_ID, result.matchedBy)
        assertEquals(7, result.receipt.id)
        assertEquals("same", result.receipt.internalId)
        assertEquals("Neu", result.receipt.aussteller)
    }

    @Test
    fun sameMainDriveFileReusesExistingIdentity() {
        val existing = receipt(id = 11, internalId = "canonical", mainId = "shared")
        val incoming = receipt(id = 0, internalId = "restored-copy", mainId = "shared")

        val result = ReceiptRestoreUpsertResolver.resolve(incoming, listOf(existing))

        assertEquals(ReceiptUpsertResolution.Match.MAIN_DRIVE_FILE_ID, result.matchedBy)
        assertEquals(11, result.receipt.id)
        assertEquals("canonical", result.receipt.internalId)
    }

    @Test
    fun repeatedResolutionDoesNotCreateAnotherRowIdentity() {
        val existing = receipt(id = 5, internalId = "id-1", mainId = "main-1")
        val incoming = receipt(id = 0, internalId = "id-1", mainId = "main-1")

        val first = ReceiptRestoreUpsertResolver.resolve(incoming, listOf(existing)).receipt
        val second = ReceiptRestoreUpsertResolver.resolve(incoming, listOf(first)).receipt

        assertEquals(5, first.id)
        assertEquals(5, second.id)
    }

    @Test
    fun restoringSameSnapshotTwiceKeepsStableRowCount() {
        val original = receipt(id = 5, internalId = "canonical", mainId = "shared")
        val snapshotCopy = receipt(id = 0, internalId = "restored-copy", mainId = "shared")

        val first = ReceiptRestoreUpsertResolver.resolve(snapshotCopy, listOf(original)).receipt
        val afterFirstRestore = listOf(first)
        val second = ReceiptRestoreUpsertResolver.resolve(snapshotCopy, afterFirstRestore).receipt
        val afterSecondRestore = listOf(second)

        assertEquals(1, afterFirstRestore.size)
        assertEquals(1, afterSecondRestore.size)
        assertEquals(original.id, second.id)
        assertEquals(original.internalId, second.internalId)
    }

    @Test
    fun trulyNewReceiptIsPreparedForInsert() {
        val incoming = receipt(id = 99, internalId = "new", mainId = "new-main")

        val result = ReceiptRestoreUpsertResolver.resolve(incoming, emptyList())

        assertEquals(ReceiptUpsertResolution.Match.NEW, result.matchedBy)
        assertEquals(0, result.receipt.id)
    }

    private fun receipt(
        id: Int,
        internalId: String,
        mainId: String,
        aussteller: String = "Test"
    ) = Receipt(
        id = id,
        aussteller = aussteller,
        datum = "2026-07-31",
        uhrzeit = "12:00",
        bruttobetrag = 10.0,
        hauptkategorie = "Test",
        unterkategorie = "Test",
        kontoNr = "0000",
        beschreibung = "Test",
        internalId = internalId,
        displayId = "BLG-2026-000001",
        driveFileId = mainId,
        driveMetadataFileId = "meta-$internalId"
    )
}
