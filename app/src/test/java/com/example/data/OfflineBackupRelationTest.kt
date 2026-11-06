package com.example.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineBackupRelationTest {
    private lateinit var source: ScopedContext
    private lateinit var target: ScopedContext
    private lateinit var sourceDb: AppDatabase
    private lateinit var targetDb: AppDatabase
    private lateinit var sourceService: OfflineBackupService
    private lateinit var targetService: OfflineBackupService
    private val property = PropertyMetadata(id = 3, propertyId = "relation-p1", name = "Haus A",
        wohneinheiten = UnitIdentity.encode(listOf(UnitIdentity.Entry("relation-u1", "WE 01"))))
    private val receipt = Receipt(id = 41, aussteller = "Synthetic vendor", datum = "2026-11-06", uhrzeit = "", bruttobetrag = 125.0,
        hauptkategorie = "Kosten", unterkategorie = "Reparatur", kontoNr = "4800", beschreibung = "Synthetic invoice",
        internalId = "relation-receipt", propertyId = "relation-p1", unitId = "relation-u1")

    @Before fun setUp(): Unit = runBlocking {
        val base: Context = ApplicationProvider.getApplicationContext()
        source = ScopedContext(base, "relation-source-${UUID.randomUUID()}")
        target = ScopedContext(base, "relation-target-${UUID.randomUUID()}")
        sourceDb = Room.inMemoryDatabaseBuilder(source, AppDatabase::class.java).build()
        targetDb = Room.inMemoryDatabaseBuilder(target, AppDatabase::class.java).build()
        sourceService = OfflineBackupService(source, sourceDb)
        targetService = OfflineBackupService(target, targetDb)
        sourceDb.propertyDao().insertPropertyMetadata(property)
        sourceDb.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 4, propertyId = "relation-p2", name = "Haus B",
            wohneinheiten = UnitIdentity.encode(listOf(UnitIdentity.Entry("relation-u2", "WE 01")))))
        sourceDb.receiptDao().insertReceipt(receipt)
        val bytes = "%PDF-1.4\nSynthetic relation original\n%%EOF".toByteArray()
        val original = File(source.filesDir, "original.pdf").apply { writeBytes(bytes) }
        sourceDb.managedDocumentDao().upsert(ManagedDocument(documentId = "relation-document", propertyId = property.propertyId,
            unitId = "relation-u1", receiptInternalId = receipt.internalId, documentDate = "2026-11-06", title = "Synthetic invoice",
            originalFilename = "invoice.pdf", storedFilename = "invoice.pdf", mimeType = "application/pdf", localUri = original.absolutePath,
            sha256 = StableDocumentIdentity.sha256(bytes), fileSizeBytes = bytes.size.toLong()))
        sourceDb.bankDao().upsertAccount(BankAccount("relation-account", "Synthetic account"))
        sourceDb.bankDao().insertTransactions(listOf(BankTransaction(transactionId = "relation-transaction", accountId = "relation-account",
            bookingDate = "2026-11-06", amount = -125.0, propertyId = "relation-p1", unitId = "relation-u1")))
        source.getSharedPreferences("rent_plan_prefs", 0).edit().putFloat("v2_relation-p1_relation-u1_nk", 80f).commit()
        target.getSharedPreferences("bank_transaction_notes", 0).edit().putString("keep", "Existing local note").commit()
    }

    @After fun tearDown() {
        sourceDb.close()
        targetDb.close()
        source.cleanup()
        target.cleanup()
    }

    @Test fun nonexistentDocumentPropertyRejectsTheEntireRehashedSnapshotAndPreservesPreferences() = runBlocking {
        val changed = changedSnapshot { root -> cell(root, "managed_documents", "propertyId", "missing-property") }
        assertRestoreRejected(changed)
    }

    @Test fun unitFromAnotherPropertyRejectsTheEntireRehashedSnapshotAndPreservesPreferences() = runBlocking {
        val changed = changedSnapshot { root -> cell(root, "managed_documents", "unitId", "relation-u2") }
        assertRestoreRejected(changed)
    }

    @Test fun bankTransactionWithMissingAccountRejectsTheEntireRehashedSnapshot() = runBlocking {
        val changed = changedSnapshot { root -> cell(root, "bank_transactions", "accountId", "missing-account") }
        assertRestoreRejected(changed)
    }

    @Test fun existingStableReceiptIdentityUnderDifferentRoomIdCannotBeDuplicatedByMerge() = runBlocking {
        targetDb.propertyDao().insertPropertyMetadata(property)
        val local = receipt.copy(id = 55)
        targetDb.receiptDao().insertReceipt(local)
        val archive = ByteArrayOutputStream().also { sourceService.create(it) }.toByteArray()
        assertRestoreRejected(archive, expectedReceipts = listOf(local), expectedProperties = listOf(property))
    }

    private suspend fun assertRestoreRejected(archive: ByteArray, expectedReceipts: List<Receipt> = emptyList(),
                                              expectedProperties: List<PropertyMetadata> = emptyList()) {
        val result = runCatching { targetService.inspect(ByteArrayInputStream(archive)).use { targetService.restore(it) } }
        assertTrue("Invalid app relationships must reject, even when the ZIP checksum is valid", result.isFailure)
        assertEquals(expectedReceipts, targetDb.receiptDao().getAllReceiptsList())
        assertEquals(expectedProperties, targetDb.propertyDao().getAllProperties())
        assertTrue(targetDb.managedDocumentDao().getAll().isEmpty())
        assertTrue(targetDb.bankDao().getAllAccounts().isEmpty())
        assertTrue(targetDb.bankDao().getAllTransactions().isEmpty())
        assertFalse(target.getSharedPreferences("rent_plan_prefs", 0).contains("v2_relation-p1_relation-u1_nk"))
        assertEquals("Existing local note", target.getSharedPreferences("bank_transaction_notes", 0).getString("keep", null))
    }

    private fun cell(root: JSONObject, tableName: String, columnName: String, value: String) {
        val table = root.getJSONObject("tables").getJSONObject(tableName)
        val columns = table.getJSONArray("columns")
        val column = (0 until columns.length()).single { columns.getString(it) == columnName }
        table.getJSONArray("rows").getJSONArray(0).put(column, value)
    }

    private suspend fun changedSnapshot(change: (JSONObject) -> Unit): ByteArray {
        val original = ByteArrayOutputStream().also { sourceService.create(it) }.toByteArray()
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(original)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                entries[entry.name] = input.readBytes()
                input.closeEntry()
            }
        }
        val root = JSONObject(entries.getValue("snapshot.json").toString(Charsets.UTF_8))
        change(root)
        val snapshot = root.toString().toByteArray(Charsets.UTF_8)
        entries["snapshot.json"] = snapshot
        entries["snapshot.sha256"] = StableDocumentIdentity.sha256(snapshot).toByteArray()
        return ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
                }
            }
        }.toByteArray()
    }

    private class ScopedContext(base: Context, private val namespace: String) : ContextWrapper(base) {
        private val root = File(base.cacheDir, namespace).apply { mkdirs() }
        override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            baseContext.getSharedPreferences("${namespace}_$name", mode)
        fun cleanup() {
            PersistentPreferenceInventory.stores.forEach { getSharedPreferences(it.name, 0).edit().clear().commit() }
            root.deleteRecursively()
        }
    }
}
