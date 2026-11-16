package com.example.data

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class OfflineBackupMaintenanceTest {
    @Test fun existingWriterPreventsEntryUntilItFinishes() {
        val coordinator = OfflineBackupCoordinator()
        val writer = coordinator.beginWrite()
        assertThrows(OfflineBackupValidationException::class.java) { coordinator.beginMaintenance() }
        writer.close()
        coordinator.beginMaintenance().close()
        coordinator.completeCapture(coordinator.beginCapture())
    }

    @Test fun maintenanceRejectsWritersAndCapturesButAllowsItsOwnSynchronousWrites() {
        val coordinator = OfflineBackupCoordinator()
        val oldCapture = coordinator.beginCapture()
        coordinator.beginMaintenance().use { maintenance ->
            assertThrows(OfflineBackupValidationException::class.java) { coordinator.beginWrite() }
            assertThrows(OfflineBackupValidationException::class.java) { coordinator.beginCapture() }
            assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(oldCapture) }
            assertThrows(OfflineBackupValidationException::class.java) { coordinator.beginMaintenance() }
            maintenance.writing { coordinator.writing { assertTrue(true) } }
            assertThrows(OfflineBackupValidationException::class.java) { coordinator.beginWrite() }
        }
        assertThrows(OfflineBackupValidationException::class.java) { coordinator.completeCapture(oldCapture) }
        coordinator.completeCapture(coordinator.beginCapture())
    }

    @Test fun cannotReleaseTokenWhileItsSynchronousActionIsStillRunning() {
        val coordinator = OfflineBackupCoordinator()
        val maintenance = coordinator.beginMaintenance()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val action = executor.submit {
                maintenance.writing {
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    coordinator.writing { }
                }
            }
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            assertThrows(IllegalStateException::class.java) { maintenance.close() }
            assertThrows(OfflineBackupValidationException::class.java) { coordinator.beginWrite() }
            release.countDown(); action.get(10, TimeUnit.SECONDS)
            maintenance.close()
            coordinator.writing { }
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun maintenancePrivilegeDoesNotEscapeToOtherThreadsOrSurviveRelease() {
        val coordinator = OfflineBackupCoordinator()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val maintenance = coordinator.beginMaintenance()
            maintenance.writing {
                val result = executor.submit<Boolean> {
                    try { coordinator.beginWrite().close(); false }
                    catch (_: OfflineBackupValidationException) { true }
                }
                assertTrue(result.get(10, TimeUnit.SECONDS))
            }
            val writer = maintenance.writing { coordinator.beginWrite() }
            assertThrows(IllegalStateException::class.java) { maintenance.close() }
            writer.close()
            maintenance.close()
            assertThrows(IllegalStateException::class.java) { maintenance.writing {} }
            maintenance.close()
            coordinator.writing { }
        } finally { executor.shutdownNow() }
    }
}
