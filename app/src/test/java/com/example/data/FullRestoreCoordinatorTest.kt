package com.example.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullRestoreCoordinatorTest {
    @Test fun `core failure blocks supplemental restore and preserves supplemental data`() = runTest {
        var supplementalCalls = 0
        val outcome = FullRestoreCoordinator.execute(
            coreRestore = { DriveRestoreReport(isSuccess = false, errorCount = 1) },
            supplementalRestore = { supplementalCalls++; SupplementalDriveBackup.Result(true, "unexpected") }
        )
        assertFalse(outcome.isSuccess)
        assertFalse(outcome.supplementalAttempted)
        assertEquals(0, supplementalCalls)
        assertTrue(outcome.message.contains("unverändert"))
    }

    @Test fun `core and supplemental success produce complete success and ordered phases`() = runTest {
        val phases = mutableListOf<FullRestorePhase>()
        val outcome = FullRestoreCoordinator.execute(
            coreRestore = { DriveRestoreReport(isSuccess = true, receiptsRestored = 2) },
            supplementalRestore = { SupplementalDriveBackup.Result(true, "ok") },
            onPhase = { phases.add(it) }
        )
        assertTrue(outcome.isSuccess)
        assertTrue(outcome.supplementalAttempted)
        assertEquals(FullRestorePhase.COMPLETE, outcome.phase)
        assertEquals(listOf(FullRestorePhase.CORE_IMPORT, FullRestorePhase.CORE_VERIFY, FullRestorePhase.SUPPLEMENTAL_IMPORT, FullRestorePhase.SUPPLEMENTAL_VERIFY, FullRestorePhase.COMPLETE), phases)
    }

    @Test fun `supplemental failure is a partial failure never a full success`() = runTest {
        val outcome = FullRestoreCoordinator.execute(
            coreRestore = { DriveRestoreReport(isSuccess = true) },
            supplementalRestore = { SupplementalDriveBackup.Result(false, "kaputt") }
        )
        assertFalse(outcome.isSuccess)
        assertTrue(outcome.supplementalAttempted)
        assertTrue(outcome.message.contains("Zusatzdaten jedoch nicht vollständig"))
    }
}
