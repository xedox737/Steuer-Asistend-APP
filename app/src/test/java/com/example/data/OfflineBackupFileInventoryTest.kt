package com.example.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineBackupFileInventoryTest {
    private lateinit var root: File
    private lateinit var context: Context
    @Before fun setUp() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, "inventory-${UUID.randomUUID()}").apply { check(mkdirs()) }
        context = object : ContextWrapper(base) {
            override fun getFilesDir() = File(root, "files").apply { check(isDirectory || mkdirs()) }
            override fun getCacheDir() = File(root, "cache").apply { check(isDirectory || mkdirs()) }
        }
    }
    @After fun tearDown() { root.deleteRecursively() }
    private fun put(path: String, bytes: ByteArray = "synthetic:$path".toByteArray()): File =
        File(root, path).apply { parentFile!!.mkdirs(); writeBytes(bytes) }

    @Test fun includesRetainedUnreferencedOriginalVersionsAndAllSavedExportOwnersWithoutChangingBytes() {
        val originals = listOf("files/managed_documents/old-version.pdf", "files/managed_documents/new-version.pdf",
            "files/property-images/property.jpg", "files/offline-originals/import.pdf", "files/restored_receipts/legacy.pdf",
            "files/receipt_documents/repaired.pdf", "files/receipt_page_123_0.jpg", "cache/temp_receipt_123.jpg")
        val exports = listOf("cache/EXTF_Buchungsstapel_2026.csv", "cache/DATEV_Export_house_2026_EXP_1.zip",
            "cache/Steuerbericht_Finanzamt_2026.pdf", "cache/Steuerbericht_Steuerberater_2026.pdf",
            "cache/document.xml", "cache/datev_export.pdf", "cache/exports/Kontoauszug_Bankabgleich.csv",
            "cache/reports/Sanierungspruefung-123.pdf")
        val expected = (originals + exports).associateWith { put(it).readBytes().toList() }
        val actual = OfflineBackupFileInventory(context).available()
        assertEquals(expected.keys.toList().sorted(), actual.map { it.portablePath })
        assertEquals(expected, actual.associate { it.portablePath to it.source.readBytes().toList() })
        assertEquals(originals.toSet(), actual.filter { it.kind == OfflineBackupFileInventory.Kind.ORIGINAL }.map { it.portablePath }.toSet())
        assertEquals(exports.toSet(), actual.filter { it.kind == OfflineBackupFileInventory.Kind.SAVED_EXPORT }.map { it.portablePath }.toSet())
    }

    @Test fun ignoresOnlyClassifiedTechnicalStagingAndUnreferencedPreviewCaches() {
        val technical = listOf("files/offline-backup-staging/current.zip", "files/managed_documents/document-download-1.tmp",
            "files/managed_documents/receipt-import-1.tmp", "files/property-images/restore-1.tmp",
            "files/offline-originals/original-1.tmp", "cache/receipt_replacement_1.tmp", "cache/repair_file_1",
            "cache/repair_scan_1.jpg", "cache/overview_repair_1",
            "cache/offline-export-1/0", "cache/offline-import-1/snapshot.json")
        technical.forEach { put(it) }
        val keep = put("files/managed_documents/user-document.tmp")
        val entries = OfflineBackupFileInventory(context).available()
        assertEquals(listOf(keep.canonicalFile), entries.map { it.source })
        assertTrue(technical.all { File(root, it).isFile })
    }

    @Test fun emptyAvailableOriginalIsKeptForExplicitValidationRatherThanSilentlyOmitted() {
        val empty = put("files/managed_documents/empty.pdf", byteArrayOf())
        val entries = OfflineBackupFileInventory(context).available()
        assertEquals(listOf(empty.canonicalFile), entries.map { it.source })
        assertEquals(0L, entries.single().source.length())
    }

    @Test fun externalContentAndTechnicalSourcesCannotClaimCoordinatedVersions() {
        val inventory = OfflineBackupFileInventory(context)
        val original = put("files/managed_documents/one.pdf")
        val saved = put("cache/reports/report.pdf")
        val preview = put("cache/repair_file_1")
        val external = put("external/one.pdf")
        val camera = put("cache/temp_receipt_123.jpg")
        assertTrue(inventory.isCoordinated(original.absolutePath))
        assertTrue(inventory.isCoordinated(original.toURI().toString()))
        assertTrue(inventory.isCoordinated(saved.absolutePath))
        assertFalse(inventory.isCoordinated(preview.absolutePath))
        assertFalse(inventory.isCoordinated(camera.absolutePath)) // Until CameraX callbacks are connected.
        assertFalse(inventory.isCoordinated(external.absolutePath))
        assertFalse(inventory.isCoordinated("content://synthetic/original"))
        assertFalse(inventory.isCoordinated(original.toURI().toString() + "?token=synthetic"))
    }

    @Test fun unavailableDirectoryAndFileCountOverflowAbortWithoutDeletingAvailableData() {
        val blocked = put("files/managed_documents")
        assertThrows(OfflineBackupValidationException::class.java) { OfflineBackupFileInventory(context).available() }
        assertTrue(blocked.isFile)
        check(blocked.delete())
        val first = put("files/managed_documents/one.pdf")
        val second = put("files/managed_documents/two.pdf")
        assertThrows(OfflineBackupValidationException::class.java) { OfflineBackupFileInventory(context, maximumFiles = 1).available() }
        assertTrue(first.isFile && second.isFile)
        assertThrows(IllegalArgumentException::class.java) { OfflineBackupFileInventory(context, maximumFiles = 10_001) }
    }

    @Test fun filesystemFailuresNeverExposePrivatePathsDuringInitializationOrEnumeration() {
        val privateDetail = "synthetic denied path: ${root.absolutePath}/private-document"
        listOf(java.io.IOException(privateDetail), SecurityException(privateDetail)).forEach { failure ->
            listOf(true, false).forEach { failDuringInitialization ->
                val broken = object : File(context.cacheDir.path) {
                    override fun getCanonicalFile(): File = if (failDuringInitialization) throw failure else this
                    override fun listFiles(): Array<File>? = throw failure
                }
                val probe = object : ContextWrapper(context) {
                    override fun getCacheDir(): File = broken
                }
                val error = assertThrows(OfflineBackupValidationException::class.java) {
                    OfflineBackupFileInventory(probe).available()
                }
                assertFalse(error.message.orEmpty().contains(root.absolutePath))
                assertFalse(error.message.orEmpty().contains(privateDetail))
                assertNull(error.cause)
            }
        }
    }

    @Test fun symlinkCannotMakeExternalBytesAnOwnedOriginal() {
        val external = put("external/secret.pdf")
        val link = File(context.filesDir, "managed_documents/link.pdf").apply { parentFile!!.mkdirs() }
        Files.createSymbolicLink(link.toPath(), external.toPath())
        try {
            val inventory = OfflineBackupFileInventory(context)
            assertFalse(inventory.isCoordinated(link.absolutePath))
            val error = assertThrows(OfflineBackupValidationException::class.java) { inventory.available() }
            assertFalse(error.message.orEmpty().contains(root.absolutePath))
            assertTrue(external.isFile)
        } finally { check(link.delete()) }
    }
}
