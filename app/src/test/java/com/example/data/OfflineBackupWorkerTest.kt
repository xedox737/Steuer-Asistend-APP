package com.example.data

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineBackupWorkerTest {
    private lateinit var context: Context

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }

    @Test fun missingOccurrenceIsRejected() = runBlocking {
        val worker = TestListenableWorkerBuilder<OfflineBackupWorker>(context).build()
        assertEquals(ListenableWorker.Result.failure(), worker.doWork())
    }

    @Test fun foregroundNotificationUsesDataSyncType() = runBlocking {
        val worker = TestListenableWorkerBuilder<OfflineBackupWorker>(context).build()
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, worker.getForegroundInfo().foregroundServiceType)
    }
}
