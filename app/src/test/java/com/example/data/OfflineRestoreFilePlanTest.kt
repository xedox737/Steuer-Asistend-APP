package com.example.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class OfflineRestoreFilePlanTest {
    private lateinit var root: File
    private lateinit var context: Context
    private val id = UUID.randomUUID().toString()
    private fun manifest(): OfflineBackupManifest.Checked {
        val a = OfflineBackupManifest.Reference("files/0", "a".repeat(64), 10)
        val b = OfflineBackupManifest.Reference("files/1", "b".repeat(64), 20)
        return OfflineBackupManifest.Checked(emptyMap(), mapOf("original-0" to "files/0", "original-1" to "files/0"), listOf(
            OfflineBackupManifest.Version("files/managed_documents/rechnung.pdf", "files/managed_documents/rechnung.pdf", OfflineBackupFileInventory.Kind.ORIGINAL, a.entry),
            OfflineBackupManifest.Version("external/original-1", null, OfflineBackupFileInventory.Kind.ORIGINAL, a.entry),
            OfflineBackupManifest.Version("cache/exports/bericht.pdf", "cache/exports/bericht.pdf", OfflineBackupFileInventory.Kind.SAVED_EXPORT, b.entry)
        ), listOf(a, b), null, emptySet(), OfflineBackupArchiveInfo("2026-11-17T00:00:00Z", 1, "test"))
    }
    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, "file-plan-${UUID.randomUUID()}").apply { check(mkdirs()) }
        context = object : ContextWrapper(base) {
            override fun getFilesDir() = File(root, "files").apply { check(isDirectory || mkdirs()) }
            override fun getCacheDir() = File(root, "cache").apply { check(isDirectory || mkdirs()) }
            override fun getNoBackupFilesDir() = File(root, "no-backup").apply { check(isDirectory || mkdirs()) }
        }
    }
    @After fun cleanup() { root.deleteRecursively() }
    @Test fun allocatesUniqueOperationOwnedPathsWithoutWritingAndKeepsOriginalFilenames() {
        val plan = OfflineRestoreFilePlan.prepare(context, id, manifest())
        assertEquals(3, plan.items.size); assertEquals(2, plan.ownedDirectories.size)
        assertEquals("rechnung.pdf", plan.items[0].destination.name)
        assertEquals("bericht.pdf", plan.items[2].destination.name)
        assertEquals(plan.items[0].destination, plan.aliases.getValue("original-0"))
        assertEquals(plan.aliases.getValue("original-0"), plan.aliases.getValue("original-1"))
        assertTrue(plan.ownedDirectories.none { it.exists() }); assertTrue(plan.items.none { it.destination.exists() })
        assertEquals(plan, OfflineRestoreFilePlan.prepare(context, id, manifest()))
    }
    @Test fun refusesOccupiedOrSymlinkedOperationDirectoryWithoutChangingIt() {
        val folder = File(context.filesDir, "offline-originals/offline-restore-$id")
        folder.mkdirs(); val sentinel = File(folder, "keep").apply { writeText("keep") }
        assertThrows(OfflineBackupValidationException::class.java) { OfflineRestoreFilePlan.prepare(context, id, manifest()) }
        assertEquals("keep", sentinel.readText()); sentinel.delete(); folder.delete()
        val external = File(root, "external").apply { mkdirs() }
        Files.createSymbolicLink(folder.toPath(), external.toPath())
        assertThrows(OfflineBackupValidationException::class.java) { OfflineRestoreFilePlan.prepare(context, id, manifest()) }
        assertTrue(external.listFiles()!!.isEmpty())
    }
    @Test fun refusesUnknownPayloadAndNonOriginalAlias() {
        val source = manifest()
        val wrongAlias = source.copy(aliases = mapOf("original-0" to "files/1"))
        val unknown = source.copy(versions = source.versions.mapIndexed { index, item -> if (index == 0) item.copy(entry = "files/99") else item })
        assertThrows(OfflineBackupValidationException::class.java) { OfflineRestoreFilePlan.prepare(context, id, wrongAlias) }
        assertThrows(OfflineBackupValidationException::class.java) { OfflineRestoreFilePlan.prepare(context, id, unknown) }
    }
    @Test fun acceptsSystemProvidedSymlinkedBaseButKeepsTargetsUnderCanonicalDirectory() {
        val realFiles = context.filesDir
        val alias = File(root, "system-files-alias")
        Files.createSymbolicLink(alias.toPath(), realFiles.toPath())
        val wrapped = object : ContextWrapper(context) { override fun getFilesDir() = alias }
        val plan = OfflineRestoreFilePlan.prepare(wrapped, id, manifest())
        assertTrue(plan.items[0].destination.path.startsWith(realFiles.canonicalPath + File.separator))
        assertFalse(plan.items[0].destination.exists())
    }

    @Test fun durablePlanSurvivesCreatedDestinationsAndRejectsChanges() = runBlocking<Unit> {
        val stagingId = UUID.randomUUID().toString()
        val plan = OfflineRestoreFilePlan.prepare(context, id, manifest())
        OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
            val snapshot = OfflineRestoreFilePlanStore.capture(context, token, stagingId, plan)
            assertEquals(plan, OfflineRestoreFilePlanStore.verify(context, id, stagingId, snapshot.bytes, snapshot.sha256))
            val target = plan.items.first().destination
            target.parentFile!!.mkdirs(); target.writeText("synthetic partial restore")
            assertEquals(plan, OfflineRestoreFilePlanStore.verify(context, id, stagingId, snapshot.bytes, snapshot.sha256))
            val original = snapshot.file.readBytes(); snapshot.file.appendText(" ")
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineRestoreFilePlanStore.verify(context, id, stagingId, snapshot.bytes, snapshot.sha256)
            } }
            snapshot.file.writeBytes(original)
            val changed = snapshot.file.readText().replaceFirst("\"owner\":", "\"owner\":\"bad\",\"owner\":")
            snapshot.file.writeText(changed)
            val hash = MessageDigest.getInstance("SHA-256").digest(snapshot.file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineRestoreFilePlanStore.verify(context, id, stagingId, snapshot.file.length(), hash)
            } }
            assertEquals("synthetic partial restore", target.readText())
        }
    }
    @Test fun occupiedPlanDestinationAndForeignMaintenanceNeverOverwriteFiles() = runBlocking<Unit> {
        val stagingId = UUID.randomUUID().toString(); val plan = OfflineRestoreFilePlan.prepare(context, id, manifest())
        val directory = File(context.noBackupFilesDir, "offline-backup-state").apply { mkdirs() }
        val prior = File(directory, "file-plan-$stagingId.json").apply { writeText("keep") }
        OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineRestoreFilePlanStore.capture(context, token, stagingId, plan)
            } }
        }
        val foreign = OfflineBackupCoordinator().beginMaintenance()
        try { assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
            OfflineRestoreFilePlanStore.capture(context, foreign, UUID.randomUUID().toString(), plan)
        } } } finally { foreign.close() }
        assertEquals("keep", prior.readText())
    }

}
