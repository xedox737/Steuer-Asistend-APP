package com.example.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivePersistenceDuplicateReferenceMutatorTest {
    @Test
    fun removesOnlyCapturedIndexReferenceForConfirmedMainFile() = runBlocking {
        val store = FakeStore(
            entries = listOf(
                index("canonical", "main"),
                index("duplicate", "main"),
                index("duplicate", "other-main")
            )
        )
        val mutator = mutator(store)

        mutator.removeIndexReferences(setOf("duplicate"), "main")

        assertEquals(
            listOf(index("canonical", "main"), index("duplicate", "other-main")),
            store.replacedEntries
        )
    }

    @Test
    fun missingTombstoneIsIdempotentAndDoesNotDeleteAnything() = runBlocking {
        val store = FakeStore(entries = emptyList())
        val mutator = mutator(store)

        mutator.removeTombstones(setOf("missing"))

        assertTrue(store.deletedFileIds.isEmpty())
    }

    @Test
    fun ambiguousTombstoneDeleteFailureIsSurfaced() = runBlocking {
        val store = FakeStore(
            entries = emptyList(),
            tombstoneFiles = mapOf("duplicate" to "tombstone-file"),
            deleteResult = false
        )
        val mutator = mutator(store)

        val error = runCatching {
            mutator.removeTombstones(setOf("duplicate"))
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(listOf("tombstone-file"), store.deletedFileIds)
    }

    private fun mutator(store: FakeStore) = DrivePersistenceDuplicateReferenceMutator(
        store = store,
        accessTokenProvider = { "token" },
        configProvider = { config() }
    )

    private fun index(internalId: String, mainId: String) = ReceiptIndexEntry(
        internalId = internalId,
        displayId = "BLG-$internalId",
        metadataFileId = "meta-$internalId-$mainId",
        mainDriveFileId = mainId,
        aussteller = "Test",
        rechnungsnummer = "",
        datum = "2026-07-31",
        bruttobetragCent = 100,
        hauptkategorie = "Test",
        unterkategorie = "Test",
        wohneinheit = "",
        massnahme = "",
        pruefstatus = "",
        freigabestatus = "",
        exportstatus = "",
        syncStatus = "SYNCED",
        updatedAt = "2026-07-31T00:00:00"
    )

    private fun config() = DriveAppConfig(
        rootFolderId = "root",
        systemFolderId = "system",
        receiptsFolderId = "receipts",
        paymentsFolderId = "payments",
        exportsFolderId = "exports",
        transactionsFolderId = "transactions",
        backupsFolderId = "backups",
        createdAt = "now",
        updatedAt = "now"
    )

    private class FakeStore(
        private val entries: List<ReceiptIndexEntry>,
        private val tombstoneFiles: Map<String, String> = emptyMap(),
        private val deleteResult: Boolean = true
    ) : DuplicateReferenceDriveStore {
        var replacedEntries: List<ReceiptIndexEntry>? = null
        val deletedFileIds = mutableListOf<String>()

        override suspend fun loadIndex(
            token: String,
            config: DriveAppConfig
        ): List<ReceiptIndexEntry> = entries

        override suspend fun replaceIndex(
            token: String,
            config: DriveAppConfig,
            entries: List<ReceiptIndexEntry>
        ): Boolean {
            replacedEntries = entries
            return true
        }

        override suspend fun findTombstoneFile(
            token: String,
            config: DriveAppConfig,
            internalId: String
        ): String? = tombstoneFiles[internalId]

        override suspend fun deleteFile(token: String, fileId: String): Boolean {
            deletedFileIds += fileId
            return deleteResult
        }
    }
}
