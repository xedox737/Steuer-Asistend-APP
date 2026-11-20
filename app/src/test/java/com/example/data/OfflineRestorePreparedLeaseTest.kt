package com.example.data

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class OfflineRestorePreparedLeaseTest {
    @Test fun ownerCloseWaitsForAllLeasesAndRejectsNewTransfers() {
        val directory = Files.createTempDirectory("restore-lease-").toFile()
        try {
            val sentinel = directory.resolve("checked-archive").apply { writeText("synthetic") }
            val resources = OfflineBackupRestoreInspector.Resources().apply { this.directory = directory }
            val preview = OfflineBackupRestoreInspector.Preview("test", OfflineBackupArchiveInfo("2026-11-18T00:00:00Z", 1, "test"),
                emptyMap(), 0, 0, emptySet())
            val prepared = OfflineBackupRestoreInspector.Prepared(resources, preview)
            val first = prepared.acquireLease()
            val second = prepared.acquireLease()
            prepared.close()
            prepared.close()
            assertEquals("synthetic", sentinel.readText())
            assertThrows(IllegalStateException::class.java) { prepared.acquireLease() }
            first.close()
            first.close()
            assertTrue(sentinel.exists())
            second.close()
            second.close()
            assertFalse(directory.exists())
        } finally { directory.deleteRecursively() }
    }
}
