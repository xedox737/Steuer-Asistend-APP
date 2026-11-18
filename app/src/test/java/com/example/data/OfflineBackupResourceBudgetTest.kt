package com.example.data

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineBackupResourceBudgetTest {
    private val directory = Files.createTempDirectory("immopilot-budget-").toFile()
    @After fun cleanup() { directory.deleteRecursively() }
    private fun draft(references: List<OfflineBackupMetadataWriter.Reference> = emptyList()) =
        OfflineBackupMetadataWriter.Draft(File(directory, "metadata.json"), "0".repeat(64), 100,
            mapOf("receipts" to 1L), references)
    private fun reference(path: String, size: Long? = null) = OfflineBackupMetadataWriter.Reference(
        "original-0", path, "synthetic document", "a".repeat(64), size, emptyList())

    @Test fun reservesConcurrentCipherRereadExtractionProbeAndPendingCopies() {
        val raw = 1024L * 1024; val metadata = 64L * 1024; val pending = raw + metadata
        val estimate = OfflineBackupResourceBudget.estimate(raw, metadata, 10, pending)
        assertTrue(estimate.targetArchiveBytes > raw + metadata)
        assertTrue(estimate.privateBytes >= pending + 3 * (raw + metadata) + 8 * metadata + OfflineBackupResourceBudget.RESERVE_BYTES)
        val fixed = OfflineBackupResourceBudget.estimate(raw, metadata, 10)
        assertEquals(pending, estimate.privateBytes - fixed.privateBytes)
        assertEquals(estimate.targetArchiveBytes, fixed.targetArchiveBytes)
    }

    @Test fun alreadyInventoriedOriginalIsNotCountedAgainAsExternalCopy() {
        val original = File(directory, "original.pdf").apply { writeBytes(ByteArray(1024 * 1024)) }
        val available = listOf(OfflineBackupFileInventory.Entry(original, "files/managed_documents/original.pdf",
            OfflineBackupFileInventory.Kind.ORIGINAL))
        val baseline = OfflineBackupResourceBudget.estimateCapture(draft(), available)
        val linked = OfflineBackupResourceBudget.estimateCapture(draft(listOf(reference(original.absolutePath))), available)
        assertEquals(0, linked.unknownOriginals)
        assertTrue(linked.privateBytes >= baseline.privateBytes)
        assertTrue(linked.privateBytes - baseline.privateBytes < 64 * 1024)
    }

    @Test fun unknownExternalSizesReserveAConservativeBoundAndKnownSizesReduceIt() {
        val unknown = OfflineBackupResourceBudget.estimateCapture(draft(listOf(reference("content://synthetic/unknown"))), emptyList())
        val known = OfflineBackupResourceBudget.estimateCapture(draft(listOf(reference("content://synthetic/unknown", 16))), emptyList())
        assertEquals(1, unknown.unknownOriginals); assertEquals(0, known.unknownOriginals)
        assertTrue(unknown.privateBytes - known.privateBytes > 1024L * 1024 * 1024)
        val error = assertThrows(OfflineBackupValidationException::class.java) { OfflineBackupResourceBudget.requireAvailable(unknown, 0) }
        assertTrue(error.message.orEmpty().contains("1 externe Originalgrößen"))
        val candidate = File(directory, "verified-candidate.pdf").apply { writeText("synthetic version") }
        val fallback = reference("content://synthetic/unknown").copy(verifiedCandidates = listOf(candidate.absolutePath))
        assertEquals(0, OfflineBackupResourceBudget.estimateCapture(draft(listOf(fallback)), emptyList()).unknownOriginals)
    }

    @Test fun rejectsUnderfundedOrOverflowingBudgetsWithConcreteNumbersAndNoWrites() {
        val estimate = OfflineBackupResourceBudget.estimate(100, 100, 1)
        val existing = File(directory, "prior.immopilot").apply { writeText("prior valid archive") }
        OfflineBackupResourceBudget.requireAvailable(estimate, estimate.privateBytes)
        val available = estimate.privateBytes - 1
        val error = assertThrows(OfflineBackupValidationException::class.java) { OfflineBackupResourceBudget.requireAvailable(estimate, available) }
        assertTrue(error.message.orEmpty().contains(estimate.privateBytes.toString()))
        assertTrue(error.message.orEmpty().contains(available.toString()))
        assertTrue(error.message.orEmpty().contains(OfflineBackupResourceBudget.RESERVE_BYTES.toString()))
        assertEquals("prior valid archive", existing.readText())
        assertThrows(IllegalArgumentException::class.java) { OfflineBackupResourceBudget.estimate(Long.MAX_VALUE, 100, 1) }
        assertThrows(IllegalArgumentException::class.java) { OfflineBackupResourceBudget.estimate(100, Long.MAX_VALUE, 1) }
        assertThrows(IllegalArgumentException::class.java) { OfflineBackupResourceBudget.estimate(100, 100, 10_001) }
        assertThrows(OfflineBackupValidationException::class.java) {
            OfflineBackupResourceBudget.estimateCapture(draft(listOf(reference("content://synthetic/large", Long.MAX_VALUE))), emptyList())
        }
    }

    @Test fun restoreBudgetIncludesFilesDatabaseProbeAndReserveAndRejectsShortage() {
        val metadata = 300L; val database = 500L
        val needed = OfflineBackupResourceBudget.estimateRestore(metadata, database, listOf(100, 200), listOf(400, 500))
        assertEquals(OfflineBackupResourceBudget.RESERVE_BYTES + 80L * 1024 * 1024 +
            16 * metadata + 4 * database + 2 * 300 + 900, needed)
        OfflineBackupResourceBudget.requireRestoreAvailable(needed, needed)
        val error = assertThrows(OfflineBackupValidationException::class.java) {
            OfflineBackupResourceBudget.requireRestoreAvailable(needed, needed - 1)
        }
        assertTrue(error.message.orEmpty().contains(needed.toString()))
        assertTrue(error.message.orEmpty().contains((needed - 1).toString()))
        assertThrows(OfflineBackupValidationException::class.java) {
            OfflineBackupResourceBudget.estimateRestore(metadata, Long.MAX_VALUE, emptyList(), emptyList())
        }
        assertThrows(OfflineBackupValidationException::class.java) {
            OfflineBackupResourceBudget.estimateRestore(metadata, database, listOf(-1), emptyList())
        }
    }
}
