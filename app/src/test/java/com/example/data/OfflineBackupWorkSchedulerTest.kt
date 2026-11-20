package com.example.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
class OfflineBackupWorkSchedulerTest {
    class TestWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
        override fun doWork(): Result = Result.failure() // Test work stays delayed and never performs an export.
    }
    private lateinit var root: File
    private lateinit var context: Context
    private lateinit var manager: WorkManager
    private val now = Instant.parse("2026-11-18T05:00:00Z")
    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, "work-scheduler-${UUID.randomUUID()}").apply { check(mkdirs()) }
        context = object : ContextWrapper(base) { override fun getNoBackupFilesDir() = root }
        WorkManagerTestInitHelper.initializeTestWorkManager(base, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        manager = WorkManager.getInstance(base)
    }
    @After fun cleanup() { manager.cancelAllWork().result.get(); root.deleteRecursively() }

    @Test fun reconciliationIsIdempotentAndNeverReplacesOlderWork() = runBlocking<Unit> {
        val rule = OfflineBackupSchedule.Rule(OfflineBackupSchedule.Cadence.DAILY, LocalTime.of(7, 0), null, ZoneId.of("UTC"))
        val config = OfflineBackupScheduleStore.Config(true, rule, "content://synthetic.documents/tree/folder", false)
        val store = OfflineBackupScheduleStore(context)
        val first = store.configure(config, now); val firstOccurrence = requireNotNull(first.next)
        fun name(state: OfflineBackupScheduleStore.State) = OfflineBackupWorkScheduler.name(state.generation, requireNotNull(state.next).id)
        fun infos(name: String) = manager.getWorkInfosForUniqueWork(name).get()
        fun scheduler() = OfflineBackupWorkScheduler(context, TestWorker::class.java, manager) { now }
        // Simulate process death after durable configuration and before enqueue.
        assertTrue(infos(name(first)).isEmpty())
        coroutineScope { listOf(async { scheduler().reconcile() }, async { scheduler().reconcile() }).awaitAll() }
        assertEquals(1, infos(name(first)).size); assertEquals(WorkInfo.State.ENQUEUED, infos(name(first)).single().state)
        scheduler().reconcile(); assertEquals(1, infos(name(first)).size)
        val changed = store.configure(config.copy(rule = rule.copy(zone = ZoneId.of("Europe/Berlin"))), now)
        scheduler().reconcile()
        assertEquals(WorkInfo.State.ENQUEUED, infos(name(first)).single().state)
        assertEquals(1, infos(name(changed)).size); assertEquals(WorkInfo.State.ENQUEUED, infos(name(changed)).single().state)
        assertEquals(OfflineBackupScheduleStore.ClaimResult.STALE,
            store.claim(first.generation, firstOccurrence.id, Instant.ofEpochMilli(firstOccurrence.dueMillis)))
        val current = requireNotNull(changed.next)
        assertEquals(OfflineBackupScheduleStore.ClaimResult.CLAIMED,
            store.claim(changed.generation, current.id, Instant.ofEpochMilli(current.dueMillis)))
        val claimed = requireNotNull(store.load()); scheduler().reconcile()
        assertEquals(claimed.active, store.load()?.active)
        assertEquals(1, infos(name(claimed)).size)
    }
}
