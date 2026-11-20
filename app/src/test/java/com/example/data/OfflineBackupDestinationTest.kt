package com.example.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineBackupDestinationTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var database: AppDatabase
    private val namespace = "destination-${UUID.randomUUID()}"
    private val tree = Uri.parse("content://test.documents/tree/root")
    @Before fun setup(): Unit = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, namespace).apply { check(mkdirs()) }
        context = object : ContextWrapper(base) {
            override fun getFilesDir() = File(root, "files").apply { check(isDirectory || mkdirs()) }
            override fun getCacheDir() = File(root, "cache").apply { check(isDirectory || mkdirs()) }
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = base.getSharedPreferences("${namespace}_$name", mode)
        }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .openHelperFactory(OfflineBackupCoordinator.shared.sqliteFactory()).build()
        val original = File(context.filesDir, "managed_documents/original.pdf").apply { parentFile!!.mkdirs(); writeText("%PDF-1.4 synthetic original") }
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 1, propertyId = "house", name = "House"))
        database.receiptDao().insertReceipt(Receipt(id = 41, internalId = "stable", propertyId = "house", aussteller = "Synthetic",
            datum = "2026-11-12", uhrzeit = "", bruttobetrag = 12.34, hauptkategorie = "Kosten", unterkategorie = "Sonstiges",
            kontoNr = "4800", beschreibung = "Synthetic", imageUrl = original.absolutePath))
        database.managedDocumentDao().upsert(ManagedDocument("doc", "house", receiptInternalId = "stable", title = "Original",
            localUri = original.absolutePath, originalFilename = "original.pdf", storedFilename = "original.pdf"))
    }
    @After fun cleanup() {
        database.close(); PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        root.deleteRecursively()
    }
    private suspend fun withSource(invalidScope: Boolean = false,
        block: suspend (OfflineBackupArchiveEngine.Prepared, OfflineBackupKeyEnvelope.Generation) -> Unit) {
        if (invalidScope) {
            val row = database.receiptDao().getAllReceiptsIncludingDeletedList().single()
            database.receiptDao().insertReceipt(row.copy(propertyId = "missing-house"))
        }
        OfflineBackupKeyEnvelope.create("synthetic-recovery-password".toCharArray()).use { generation ->
            OfflineBackupSnapshot.capture(context, database).use { snapshot ->
                val parent = File(context.filesDir, "offline-backup-staging")
                val resources = OfflineBackupResourceBudget.checkPrepared(parent, snapshot)
                val file = File(parent, "prepared-${UUID.randomUUID()}.immopilot")
                file.outputStream().use { OfflineBackupArchiveCrypto.encrypt(generation, it) { output -> snapshot.writeArchive(output) } }
                OfflineBackupArchiveEngine.Prepared(file, snapshot.summary, resources, snapshot.archiveInfo).use { block(it, generation) }
            }
        }
    }
    private enum class Fault { NONE, WRITE, WRITE_CLOSE, NATIVE_CLOSE, READ, READ_CLOSE, TRUNCATED, CORRUPT, EXTRA, RENAME, FINAL_CORRUPT, FINAL_CLOSE }
    private data class Node(var name: String, var bytes: ByteArray)
    private class FakeBackend(var fault: Fault = Fault.NONE) : OfflineBackupDestination.Backend {
        val prior = Uri.parse("content://test.documents/document/prior")
        val nodes = linkedMapOf(prior to Node("prior.immopilot", "prior valid archive".toByteArray()))
        val deleted = mutableListOf<Uri>(); val operations = mutableListOf<String>()
        var access = OfflineBackupDestination.Access(true, true, true, Long.MAX_VALUE)
        var renameSupported = true; var deleteFails = false; var foreignCreate = false; var created = 0; private var reads = 0
        override fun access(tree: Uri) = access
        override fun create(tree: Uri, name: String): OfflineBackupDestination.Created {
            created++; operations += "create"
            if (foreignCreate) return OfflineBackupDestination.Created(Uri.parse("content://foreign.documents/document/old"), true)
            val uri = Uri.parse("content://test.documents/document/${UUID.randomUUID()}")
            nodes[uri] = Node(name, byteArrayOf()); return OfflineBackupDestination.Created(uri, renameSupported)
        }
        override fun write(uri: Uri): OutputStream {
            operations += "write"
            return object : ByteArrayOutputStream() {
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    if (fault == Fault.WRITE) throw IOException("synthetic write failure")
                    super.write(bytes, offset, length)
                }
                override fun close() {
                    nodes.getValue(uri).bytes = toByteArray(); operations += "write-close"
                    if (fault == Fault.WRITE_CLOSE) throw IOException("synthetic close failure")
                    if (fault == Fault.NATIVE_CLOSE) throw android.system.ErrnoException("synthetic native close", android.system.OsConstants.EIO)
                }
            }
        }
        override fun read(uri: Uri): InputStream {
            reads++; val index = reads; operations += "read-$index"
            if (fault == Fault.READ) throw IOException("synthetic read failure")
            val original = nodes.getValue(uri).bytes
            val bytes = when {
                fault == Fault.TRUNCATED -> original.copyOf(original.size - 1)
                fault == Fault.EXTRA -> original + byteArrayOf(1)
                fault == Fault.CORRUPT || (fault == Fault.FINAL_CORRUPT && index == 2) -> original.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
                else -> original
            }
            return object : ByteArrayInputStream(bytes) {
                override fun close() {
                    operations += "read-close-$index"
                    if (fault == Fault.READ_CLOSE || (fault == Fault.FINAL_CLOSE && index == 2)) throw IOException("synthetic read close failure")
                }
            }
        }
        override fun rename(uri: Uri, name: String): Uri? {
            operations += "rename"
            if (fault == Fault.RENAME) return null
            val node = nodes.remove(uri)!!; node.name = name
            val renamed = Uri.parse("content://test.documents/document/${UUID.randomUUID()}")
            nodes[renamed] = node; return renamed
        }
        override fun delete(uri: Uri): Boolean {
            deleted += uri; if (deleteFails) return false
            return nodes.remove(uri) != null
        }
        fun assertPrior() { assertEquals("prior valid archive", nodes.getValue(prior).bytes.toString(Charsets.UTF_8)); assertFalse(prior in deleted) }
    }
    private fun noWorkingDirectories() {
        assertTrue(File(context.filesDir, "offline-backup-staging").walkTopDown().none { it.isDirectory &&
            (it.name.startsWith("destination-") || it.name.startsWith("verify-") || it.name.startsWith("reread-") || it.name.startsWith("probe-")) })
    }

    @Test fun closesWritesFullyProbesTargetThenRenamesAndChecksFinalUri() = runBlocking<Unit> {
        withSource { source, generation ->
            val backend = FakeBackend(); val stages = mutableListOf<OfflineBackupDestination.Stage>()
            val published = OfflineBackupDestination(context, tree, backend).publish(source, generation) { stages += it }
            assertArrayEquals(source.file.readBytes(), backend.nodes.getValue(published.uri).bytes)
            val sha = MessageDigest.getInstance("SHA-256").digest(source.file.readBytes()).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            assertEquals(source.file.length(), published.bytes); assertEquals(sha, published.sha256)
            assertEquals(source.archiveInfo, published.archiveInfo)
            assertEquals(source.file.inputStream().use { OfflineBackupArchiveHeader.read(it).dataKey.archiveId }, published.backupId)
            assertTrue(backend.nodes.getValue(published.uri).name.endsWith(".immopilot"))
            assertEquals(OfflineBackupDestination.Stage.entries, stages)
            assertTrue(backend.operations.indexOf("write-close") < backend.operations.indexOf("read-1"))
            assertTrue(backend.operations.indexOf("read-close-1") < backend.operations.indexOf("rename"))
            assertTrue(backend.operations.indexOf("rename") < backend.operations.indexOf("read-2"))
            assertTrue(backend.deleted.isEmpty()); backend.assertPrior(); noWorkingDirectories()
        }
    }

    @Test fun missingRightsUnknownSpaceAndInsufficientSpaceNeverCreateTarget() = runBlocking<Unit> {
        withSource { source, generation ->
            val valid = OfflineBackupDestination.Access(true, true, true, Long.MAX_VALUE)
            val variants = listOf(valid.copy(persistedReadWrite = false), valid.copy(directory = false), valid.copy(canCreate = false),
                valid.copy(availableBytes = null), valid.copy(availableBytes = source.file.length() + OfflineBackupResourceBudget.RESERVE_BYTES - 1))
            variants.forEach { access ->
                val backend = FakeBackend().apply { this.access = access }
                assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineBackupDestination(context, tree, backend).publish(source, generation) } }
                assertEquals(0, backend.created); assertTrue(backend.deleted.isEmpty()); backend.assertPrior(); noWorkingDirectories()
            }
            val foreign = FakeBackend().apply { foreignCreate = true }
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineBackupDestination(context, tree, foreign).publish(source, generation) } }
            assertTrue(foreign.deleted.isEmpty()); foreign.assertPrior(); noWorkingDirectories()
        }
    }

    @Test fun providerWriteCloseReadRenameAndFinalUriFailuresDeleteOnlyOwnNewDocument() = runBlocking<Unit> {
        withSource { source, generation ->
            Fault.entries.filter { it != Fault.NONE }.forEach { fault ->
                val backend = FakeBackend(fault)
                assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineBackupDestination(context, tree, backend).publish(source, generation) } }
                backend.assertPrior(); assertEquals(setOf(backend.prior), backend.nodes.keys); noWorkingDirectories()
            }
        }
    }

    @Test fun cancellationAtEachPublicationStageRemovesOnlyOwnedPendingDocument() = runBlocking<Unit> {
        withSource { source, generation ->
            OfflineBackupDestination.Stage.entries.forEach { stage ->
                val backend = FakeBackend()
                assertThrows(kotlinx.coroutines.CancellationException::class.java) { runBlocking {
                    OfflineBackupDestination(context, tree, backend).publish(source, generation) { if (it == stage) throw kotlinx.coroutines.CancellationException("synthetic stop") }
                } }
                backend.assertPrior(); assertEquals(setOf(backend.prior), backend.nodes.keys); noWorkingDirectories()
            }
        }
    }

    @Test fun unsupportedRenameOrFailedCleanupNeverReturnsSuccessOrDeletesOldArchive() = runBlocking<Unit> {
        withSource { source, generation ->
            val unsupported = FakeBackend().apply { renameSupported = false }
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineBackupDestination(context, tree, unsupported).publish(source, generation) } }
            assertFalse("write" in unsupported.operations); assertEquals(setOf(unsupported.prior), unsupported.nodes.keys)
            val unremovable = FakeBackend(Fault.WRITE).apply { deleteFails = true }
            val error = assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineBackupDestination(context, tree, unremovable).publish(source, generation) } }
            assertTrue(error.message.orEmpty().contains("konnte nicht entfernt"))
            assertTrue(unremovable.nodes.filterKeys { it != unremovable.prior }.values.all { it.name.endsWith(".partial") })
            unremovable.assertPrior(); unsupported.assertPrior(); noWorkingDirectories()
        }
    }

    @Test fun targetMustPassRealAuthenticationAndRoomRelationshipsNotOnlyCipherHash() = runBlocking<Unit> {
        withSource(invalidScope = true) { source, generation ->
            val backend = FakeBackend()
            val error = assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineBackupDestination(context, tree, backend).publish(source, generation) } }
            assertTrue(error.message.orEmpty().contains("Objekt- oder Einheitenbeziehung"))
            assertEquals(setOf(backend.prior), backend.nodes.keys); backend.assertPrior(); noWorkingDirectories()
        }
    }
}
