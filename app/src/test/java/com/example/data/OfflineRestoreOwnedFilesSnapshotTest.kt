package com.example.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineRestoreOwnedFilesSnapshotTest {
    private lateinit var context: Context
    private lateinit var root: File
    private fun id() = UUID.randomUUID().toString()
    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, "rollback-files-${id()}").apply { check(mkdirs()) }
        context = object : ContextWrapper(base) {
            private fun directory(name: String) = File(root, name).apply { check(isDirectory || mkdirs()) }
            override fun getFilesDir() = directory("files")
            override fun getCacheDir() = directory("cache")
            override fun getNoBackupFilesDir() = directory("no-backup")
        }
    }
    @After fun cleanup() { root.deleteRecursively() }
    @Test fun copiesAllRetainedOriginalsEmptyFilesAndSavedExportsWithStableBytes() = runBlocking<Unit> {
        val original = File(context.filesDir, "managed_documents/original.pdf").apply { parentFile!!.mkdirs(); writeText("original") }
        File(context.filesDir, "managed_documents/old.pdf").writeText("retained old version")
        File(context.filesDir, "receipt_page_empty.jpg").writeBytes(byteArrayOf())
        File(context.cacheDir, "exports/report.csv").apply { parentFile!!.mkdirs(); writeText("export") }
        OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
            val snapshot = OfflineRestoreOwnedFilesSnapshot.capture(context, token, id())
            assertEquals(setOf("files/managed_documents/original.pdf", "files/managed_documents/old.pdf", "files/receipt_page_empty.jpg", "cache/exports/report.csv"),
                snapshot.versions.map { it.portablePath }.toSet())
            val fixed = snapshot.versions.single { it.portablePath.endsWith("original.pdf") }
            original.writeText("changed later")
            assertEquals("original", File(snapshot.directory, fixed.stagedName).readText())
            assertEquals(0L, snapshot.versions.single { it.portablePath.endsWith("empty.jpg") }.bytes)
            val manifest = File(snapshot.directory, "manifest.json")
            assertEquals(4, JSONObject(manifest.readText()).getJSONArray("files").length())
            assertEquals(manifest.length(), snapshot.manifestBytes); assertEquals(64, snapshot.manifestSha256.length)
        }
    }
    @Test fun occupiedRollbackDirectoryAndForeignTokenCannotDeleteExistingData() = runBlocking<Unit> {
        val snapshotId = id(); val prior = File(context.noBackupFilesDir, "offline-backup-state/rollback-$snapshotId.files").apply { mkdirs() }
        val sentinel = File(prior, "keep").apply { writeText("preserve") }
        OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestoreOwnedFilesSnapshot.capture(context, token, snapshotId) } }
        }
        val foreign = OfflineBackupCoordinator().beginMaintenance()
        try { assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestoreOwnedFilesSnapshot.capture(context, foreign, id()) } } }
        finally { foreign.close() }
        assertEquals("preserve", sentinel.readText())
    }
    @Test fun invalidOrCancelledOperationDoesNotCreateAFileSnapshot() = runBlocking<Unit> {
        OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestoreOwnedFilesSnapshot.capture(context, token, "invalid") } }
            assertThrows(kotlinx.coroutines.CancellationException::class.java) { runBlocking {
                coroutineContext[kotlinx.coroutines.Job]!!.cancel()
                OfflineRestoreOwnedFilesSnapshot.capture(context, token, id())
            } }
        }
        assertFalse(File(context.noBackupFilesDir, "offline-backup-state").exists())
    }
}
