package com.example.e2e

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.data.OfflineBackupSchedule
import com.example.data.OfflineBackupScheduleStore
import com.example.data.OfflineBackupWorkScheduler
import com.example.data.OfflineBackupWorker
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineBackupScheduleEmulatorTest {
    @get:Rule val environment = EmulatorEnvironmentRule { 1f }

    @Test
    fun missingRegistrationIsRecreatedOnceOnAndroid() = runBlocking(Dispatchers.IO) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "schedule-emulator-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val context = object : ContextWrapper(base) {
            override fun getNoBackupFilesDir(): File = root
        }
        val manager = WorkManager.getInstance(base)
        val now = Instant.now()
        val rule = OfflineBackupSchedule.Rule(
            OfflineBackupSchedule.Cadence.DAILY,
            now.atZone(ZoneId.of("UTC")).toLocalTime().plusHours(12).withSecond(0).withNano(0),
            null,
            ZoneId.of("UTC"),
        )
        val state = OfflineBackupScheduleStore(context).configure(
            OfflineBackupScheduleStore.Config(true, rule, "content://synthetic.documents/tree/folder", false), now)
        val name = OfflineBackupWorkScheduler.name(state.generation, requireNotNull(state.next).id)
        try {
            assertEquals(emptyList<WorkInfo>(), manager.getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS))
            repeat(2) { OfflineBackupWorkScheduler(context, OfflineBackupWorker::class.java, manager).reconcile() }
            val work = manager.getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS).single()
            assertEquals(WorkInfo.State.ENQUEUED, work.state)
            assertEquals(state, OfflineBackupScheduleStore(context).load())
        } finally {
            manager.cancelUniqueWork(name).result.get(10, TimeUnit.SECONDS)
            root.deleteRecursively()
        }
        Unit
    }
}
