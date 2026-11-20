package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Only registers work. Archive creation and the restore guard stay in the worker. */
internal class OfflineBackupScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(8_000) { reconcile(context.applicationContext, action == Intent.ACTION_TIME_CHANGED) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        suspend fun reconcile(context: Context, clockEdited: Boolean = false) = withContext(Dispatchers.IO) {
            try {
                val store = OfflineBackupScheduleStore(context)
                if (store.load() != null) {
                    store.refreshClock(ZoneId.systemDefault(), Instant.now(), clockEdited)
                    OfflineBackupWorkScheduler(context, OfflineBackupWorker::class.java).reconcile()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: OfflineBackupValidationException) {
                // Desired state stays on disk for the next reconciliation attempt.
            } catch (_: IllegalStateException) {
                // WorkManager may be unavailable during startup.
            }
        }
    }
}
