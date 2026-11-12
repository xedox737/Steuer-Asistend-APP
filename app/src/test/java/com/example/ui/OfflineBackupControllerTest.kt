package com.example.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.api.TestAndroidKeyStore
import com.example.data.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineBackupControllerTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var database: AppDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var folders: Folders
    private lateinit var backend: Backend
    private lateinit var controller: OfflineBackupController
    private val tree = Uri.parse("content://controller.test/tree/root")
    private val namespace = "controller-${UUID.randomUUID()}"
    private class Folders : OfflineBackupController.FolderAccess {
        val granted = hashSetOf<Uri>(); val rejected = hashSetOf<Uri>(); val released = mutableListOf<Uri>()
        var denied = false
        override fun hasGrant(uri: Uri) = uri in granted
        override fun persist(uri: Uri) { if (denied) throw SecurityException("synthetic denial"); granted += uri }
        override fun release(uri: Uri) { granted -= uri; released += uri }
        override fun label(uri: Uri) = "Testordner"
    }
    private class Backend(private val folders: Folders) : OfflineBackupDestination.Backend {
        val prior = Uri.parse("content://controller.test/document/prior")
        val documents = linkedMapOf(prior to "prior valid archive".toByteArray())
        var writeFails = false; var blockWrite = false
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        override fun access(tree: Uri) = OfflineBackupDestination.Access(tree in folders.granted, true, true,
            if (tree in folders.rejected) null else Long.MAX_VALUE)
        override fun create(tree: Uri, name: String): OfflineBackupDestination.Created {
            val uri = Uri.parse("content://controller.test/document/${UUID.randomUUID()}")
            documents[uri] = byteArrayOf(); return OfflineBackupDestination.Created(uri, true)
        }
        override fun write(uri: Uri): OutputStream = object : ByteArrayOutputStream() {
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                if (blockWrite) { entered.countDown(); release.await() }
                if (writeFails) throw IOException("synthetic write failure")
                super.write(bytes, offset, length)
            }
            override fun close() { documents[uri] = toByteArray() }
        }
        override fun read(uri: Uri): InputStream = documents.getValue(uri).inputStream()
        override fun rename(uri: Uri, name: String): Uri {
            val next = Uri.parse("content://controller.test/document/${UUID.randomUUID()}")
            documents[next] = documents.remove(uri)!!; return next
        }
        override fun delete(uri: Uri) = documents.remove(uri) != null
    }
    private fun controller(scope: CoroutineScope = this.scope) = OfflineBackupController(context, database, scope, folders) {
        uri -> OfflineBackupDestination(context, uri, backend)
    }
    private suspend fun await(job: Job?) { requireNotNull(job).join() }
    @Before fun setup(): Unit = runBlocking {
        TestAndroidKeyStore.register()
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, namespace).apply { check(mkdirs()) }
        context = object : ContextWrapper(base) {
            override fun getFilesDir() = File(root, "files").apply { check(isDirectory || mkdirs()) }
            override fun getCacheDir() = File(root, "cache").apply { check(isDirectory || mkdirs()) }
            override fun getNoBackupFilesDir() = File(root, "no-backup").apply { check(isDirectory || mkdirs()) }
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = base.getSharedPreferences("${namespace}_$name", mode)
        }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .openHelperFactory(OfflineBackupCoordinator.shared.sqliteFactory()).build()
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 1, propertyId = "house", name = "House"))
        database.receiptDao().insertReceipt(Receipt(id = 41, internalId = "stable", propertyId = "house", aussteller = "Synthetic",
            datum = "2026-11-12", uhrzeit = "", bruttobetrag = 12.34, hauptkategorie = "Kosten", unterkategorie = "Sonstiges",
            kontoNr = "4800", beschreibung = "Synthetic"))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        folders = Folders(); backend = Backend(folders); controller = controller()
        await(controller.reload())
    }
    @After fun cleanup() = runBlocking {
        backend.release.countDown(); scope.coroutineContext[Job]!!.cancelAndJoin()
        database.close(); PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        root.deleteRecursively(); TestAndroidKeyStore.remove()
    }
    private suspend fun configure() {
        await(controller.configure("synthetic-recovery-password".toCharArray(), "synthetic-recovery-password".toCharArray()))
        assertTrue(controller.state.value.configured)
    }

    @Test fun setupValidatesConfirmationWipesBuffersAndReloadsConfiguredGeneration() = runBlocking<Unit> {
        assertTrue(controller.state.value.loaded); assertFalse(controller.state.value.configured); assertFalse(controller.state.value.canSave)
        val wrong = "synthetic-recovery-password".toCharArray(); val confirmation = "synthetic-different-password".toCharArray()
        await(controller.configure(wrong, confirmation))
        assertFalse(controller.state.value.configured); assertTrue(wrong.all { it == '\u0000' }); assertTrue(confirmation.all { it == '\u0000' })
        assertTrue(controller.state.value.status.orEmpty().contains("übereinstimmend"))
        val password = "synthetic-recovery-password".toCharArray(); val matching = password.copyOf()
        await(controller.configure(password, matching))
        assertTrue(controller.state.value.configured); assertTrue(password.all { it == '\u0000' }); assertTrue(matching.all { it == '\u0000' })
        val id = controller.state.value.generationId
        val replacement = "synthetic-new-recovery-password".toCharArray(); val replacementConfirm = replacement.copyOf()
        await(controller.configure(replacement, replacementConfirm, change = true))
        assertNotEquals(id, controller.state.value.generationId)
        assertTrue(replacement.all { it == '\u0000' }); assertTrue(replacementConfirm.all { it == '\u0000' })
        val reloaded = controller(); await(reloaded.reload())
        assertEquals(controller.state.value.generationId, reloaded.state.value.generationId)
    }

    @Test fun folderSelectionPersistsOnlyValidatedGrantsAndKeepsOldFolderOnFailure() = runBlocking<Unit> {
        folders.denied = true; await(controller.selectFolder(tree))
        assertNull(OfflineBackupSettings(context).load().targetUri); assertFalse(controller.state.value.folderReady)
        folders.denied = false; await(controller.selectFolder(tree))
        assertTrue(controller.state.value.folderReady); assertEquals("Testordner", controller.state.value.folderLabel)
        assertEquals(tree.toString(), OfflineBackupSettings(context).load().targetUri)
        await(controller.setManualCredentials(true)); assertTrue(OfflineBackupSettings(context).load().manualCredentials)
        val invalid = Uri.parse("content://controller.test/tree/rejected"); folders.rejected += invalid
        await(controller.selectFolder(invalid))
        assertEquals(tree.toString(), OfflineBackupSettings(context).load().targetUri)
        assertTrue(tree in folders.granted); assertFalse(invalid in folders.granted); assertTrue(invalid in folders.released)
    }

    @Test fun saveUsesRealVerifiedPipelineAndFailureDoesNotReplacePreviousSuccess() = runBlocking<Unit> {
        configure(); await(controller.selectFolder(tree)); assertTrue(controller.state.value.canSave)
        await(controller.save())
        val success = requireNotNull(controller.state.value.lastPublished)
        assertTrue(controller.state.value.status.orEmpty().contains("vollständig geprüft"))
        val previousBytes = backend.documents.getValue(success.uri).copyOf()
        backend.writeFails = true; await(controller.save())
        assertEquals(success, controller.state.value.lastPublished)
        assertFalse(controller.state.value.status.orEmpty().contains("vollständig geprüft"))
        assertArrayEquals(previousBytes, backend.documents.getValue(success.uri))
        assertEquals("prior valid archive", backend.documents.getValue(backend.prior).toString(Charsets.UTF_8))
        assertFalse(controller.state.value.busy)
    }

    @Test fun canceledScopeStillConsumesUnstartedPasswordBuffers() = runBlocking<Unit> {
        val canceled = CoroutineScope(SupervisorJob().apply { cancel() } + Dispatchers.IO)
        val unstarted = controller(canceled)
        val password = "synthetic-recovery-password".toCharArray(); val confirmation = password.copyOf()
        await(unstarted.configure(password, confirmation))
        assertTrue(password.all { it == '\u0000' }); assertTrue(confirmation.all { it == '\u0000' })
        assertFalse(unstarted.state.value.busy); assertFalse(unstarted.state.value.configured)
    }

    @Test fun busyActionRejectsDuplicateRequestsAndCancelCleansOnlyCurrentAttempt() = runBlocking<Unit> {
        configure(); await(controller.selectFolder(tree)); backend.blockWrite = true
        val job = requireNotNull(controller.save())
        try {
            assertTrue(backend.entered.await(10, TimeUnit.SECONDS)); assertTrue(controller.state.value.busy)
            val other = Uri.parse("content://controller.test/tree/other")
            assertNull(controller.selectFolder(other)); assertFalse(other in folders.granted)
            val password = "synthetic-recovery-password".toCharArray(); val confirmation = password.copyOf()
            assertNull(controller.configure(password, confirmation))
            assertTrue(password.all { it == '\u0000' }); assertTrue(confirmation.all { it == '\u0000' })
        } finally { controller.cancel(); backend.release.countDown(); job.join() }
        assertNull(controller.state.value.lastPublished); assertFalse(controller.state.value.busy)
        assertEquals(setOf(backend.prior), backend.documents.keys)
        assertEquals("prior valid archive", backend.documents.getValue(backend.prior).toString(Charsets.UTF_8))
    }
}
