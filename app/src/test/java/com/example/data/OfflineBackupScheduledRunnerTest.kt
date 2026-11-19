package com.example.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CancellationException
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
class OfflineBackupScheduledRunnerTest {
    private lateinit var root: File
    private lateinit var context: Context
    private lateinit var schedule: OfflineBackupScheduleStore
    private lateinit var history: OfflineBackupHistory
    private lateinit var initial: OfflineBackupScheduleStore.State
    private lateinit var clock: Instant
    private val coordinator get() = OfflineBackupCoordinator.shared
    private val rule = OfflineBackupSchedule.Rule(OfflineBackupSchedule.Cadence.DAILY, LocalTime.of(7, 0), null, ZoneId.of("UTC"))
    private val config get() = OfflineBackupScheduleStore.Config(true, rule, "content://synthetic.documents/tree/first", false)

    private inner class Hooks : OfflineBackupScheduledRunner.Hooks {
        var reconciles = 0; var prepares = 0; var warnings = 0; var failReconcileAt = 0
        var prepareError: Exception? = null; var changeConfig = false; var systemInterrupted = false
        var usedConfig: OfflineBackupScheduleStore.Config? = null; var publications = 0
        override suspend fun reconcile() { reconciles++; if (reconciles == failReconcileAt) throw IllegalStateException("synthetic scheduling error") }
        override suspend fun prepare(config: OfflineBackupScheduleStore.Config): OfflineBackupScheduledRunner.PreparedExport {
            prepares++; usedConfig = config
            assertThrows(OfflineBackupValidationException::class.java) { coordinator.beginMaintenance() }
            prepareError?.let { throw it }
            if (changeConfig) schedule.configure(config.copy(targetUri = "content://synthetic.documents/tree/second", automaticCredentials = true), clock)
            return object : OfflineBackupScheduledRunner.PreparedExport {
                override suspend fun publish(operationId: String) {
                    publications++
                    history.beginFullBackup(operationId, OfflineBackupHistory.Origin.AUTOMATIC, clock.toEpochMilli())
                    history.record(OfflineBackupHistory.Event(operationId, OfflineBackupHistory.Kind.FULL, OfflineBackupHistory.Outcome.VERIFIED,
                        clock.toEpochMilli(), 10, null, OfflineBackupHistory.Origin.AUTOMATIC, UUID.randomUUID().toString(),
                        "content://synthetic.documents/document/archive", 1, 2))
                }
            }
        }
        override fun notifyFailure() { warnings++ }
        override fun cancellationOutcome() = if (systemInterrupted) OfflineBackupHistory.Outcome.INTERRUPTED else OfflineBackupHistory.Outcome.CANCELLED
    }
    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, "scheduled-runner-${UUID.randomUUID()}").apply { check(mkdirs()) }
        context = object : ContextWrapper(base) { override fun getNoBackupFilesDir() = root }
        schedule = OfflineBackupScheduleStore(context); history = OfflineBackupHistory(context)
        initial = schedule.configure(config, Instant.parse("2026-11-18T05:00:00Z"))
        clock = Instant.ofEpochMilli(requireNotNull(initial.next).dueMillis)
    }
    @After fun cleanup() {
        if (coordinator.restorePending.value) {
            val file = File(root, "offline-backup-state/restore-journal.json")
            listOf(file, File(file.path + ".bak"), File(file.path + ".new")).forEach { it.delete() }
            coordinator.beginMaintenance().use { coordinator.clearRestorePending(context, it) }
        }
        root.deleteRecursively()
    }
    private fun runner(hooks: Hooks) = OfflineBackupScheduledRunner(context, hooks) { clock }
    private val occurrence get() = requireNotNull(initial.next).id

    @Test fun runningExportKeepsClaimedSettingsAndDoesNotRunTwice() = runBlocking<Unit> {
        val hooks = Hooks().apply { changeConfig = true }
        assertEquals(OfflineBackupScheduledRunner.Result.COMPLETED, runner(hooks).run(initial.generation, occurrence))
        assertEquals(config, hooks.usedConfig); assertFalse(requireNotNull(hooks.usedConfig).automaticCredentials)
        assertEquals(1, hooks.publications); assertEquals(OfflineBackupHistory.Outcome.VERIFIED, history.load().lastFullSuccess?.outcome)
        assertNull(schedule.load()?.active); assertTrue(requireNotNull(schedule.load()).config.automaticCredentials)
        assertEquals(OfflineBackupScheduledRunner.Result.SKIPPED, runner(hooks).run(initial.generation, occurrence))
        assertEquals(1, hooks.publications)
    }
    @Test fun openRestoreBlocksBeforeClaimOrDatabasePreparation() = runBlocking<Unit> {
        val journal = File(root, "offline-backup-state/restore-journal.json").apply { writeText("{broken") }
        val hooks = Hooks()
        assertEquals(OfflineBackupScheduledRunner.Result.RETRY, runner(hooks).run(initial.generation, occurrence))
        assertTrue(coordinator.restorePending.value); assertEquals("{broken", journal.readText())
        assertEquals(initial, schedule.load()); assertEquals(0, hooks.prepares); assertTrue(history.load().recent.isEmpty())
    }
    @Test fun preparationFailureIsRecordedAndDoesNotPretendSuccess() = runBlocking<Unit> {
        val hooks = Hooks().apply { prepareError = IllegalStateException("synthetic") }
        assertEquals(OfflineBackupScheduledRunner.Result.FAILED, runner(hooks).run(initial.generation, occurrence))
        assertEquals(OfflineBackupHistory.Outcome.FAILED, history.load().lastAttempt?.outcome)
        assertNull(history.load().lastFullSuccess); assertNull(schedule.load()?.active)
        assertNotEquals(occurrence, schedule.load()?.next?.id); assertEquals(1, hooks.warnings); assertEquals(0, hooks.publications)
    }
    @Test fun failedNextRegistrationRetriesReconciliationWithoutRepeatingExport() = runBlocking<Unit> {
        val hooks = Hooks().apply { failReconcileAt = 2 }
        assertEquals(OfflineBackupScheduledRunner.Result.RETRY, runner(hooks).run(initial.generation, occurrence))
        assertEquals(OfflineBackupHistory.Outcome.FAILED, history.load().lastAttempt?.outcome)
        assertNull(schedule.load()?.active); assertEquals(0, hooks.publications)
        assertEquals(OfflineBackupScheduledRunner.Result.SKIPPED, runner(hooks).run(initial.generation, occurrence))
        assertEquals(0, hooks.publications)
    }
    @Test fun cancellationBeforePublicationIsRecordedWithoutSuccess() {
        val hooks = Hooks().apply { prepareError = CancellationException("synthetic stop") }
        assertThrows(CancellationException::class.java) { runBlocking { runner(hooks).run(initial.generation, occurrence) } }
        assertEquals(OfflineBackupHistory.Outcome.CANCELLED, history.load().lastAttempt?.outcome)
        assertNull(history.load().lastFullSuccess); assertNull(schedule.load()?.active)
    }
    @Test fun systemInterruptionBeforePublicationIsRecordedAndWarned() {
        val hooks = Hooks().apply { prepareError = CancellationException("synthetic timeout"); systemInterrupted = true }
        assertThrows(CancellationException::class.java) { runBlocking { runner(hooks).run(initial.generation, occurrence) } }
        assertEquals(OfflineBackupHistory.Outcome.INTERRUPTED, history.load().lastAttempt?.outcome)
        assertNull(history.load().lastFullSuccess); assertNull(schedule.load()?.active); assertEquals(1, hooks.warnings)
    }
    @Test fun laterOccurrenceWaitsWhileAnEarlierClaimIsActive() = runBlocking<Unit> {
        assertEquals(OfflineBackupScheduleStore.ClaimResult.CLAIMED, schedule.claim(initial.generation, occurrence, clock))
        val next = requireNotNull(schedule.load()?.next); clock = Instant.ofEpochMilli(next.dueMillis)
        val hooks = Hooks()
        assertEquals(OfflineBackupScheduledRunner.Result.RETRY, runner(hooks).run(initial.generation, next.id))
        assertEquals(occurrence, schedule.load()?.active?.occurrence?.id); assertEquals(0, hooks.prepares)
    }
}
