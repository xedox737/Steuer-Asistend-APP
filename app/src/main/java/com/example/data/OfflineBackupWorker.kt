package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/** Scheduled full backup; all business work is gated by the durable schedule and restore journal. */
internal class OfflineBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    private var lastProgress = ""

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val generation = inputData.getString(OfflineBackupWorkScheduler.KEY_GENERATION)
        val occurrence = inputData.getString(OfflineBackupWorkScheduler.KEY_OCCURRENCE)
        if (!validId(generation) || !validId(occurrence)) return@withContext Result.failure()
        val scheduler = OfflineBackupWorkScheduler(applicationContext, OfflineBackupWorker::class.java)
        val runner = OfflineBackupScheduledRunner(applicationContext, object : OfflineBackupScheduledRunner.Hooks {
            override suspend fun reconcile() = scheduler.reconcile()
            override suspend fun prepare(config: OfflineBackupScheduleStore.Config): OfflineBackupScheduledRunner.PreparedExport {
                setForeground(foregroundInfo("Sicherung wird vorbereitet …", -1))
                val destination = OfflineBackupDestination(applicationContext, Uri.parse(requireNotNull(config.targetUri)))
                // Checks persisted read/write grants and provider availability on every run.
                destination.preflight()
                val database = AppDatabase.getDatabase(applicationContext, databaseScope)
                database.openHelper.writableDatabase
                OfflineBackupCoordinator.shared.awaitInitialization()
                return object : OfflineBackupScheduledRunner.PreparedExport {
                    override suspend fun publish(operationId: String) {
                        OfflineBackupArchiveEngine(applicationContext, database).publish(destination,
                            consent = OfflineBackupCredentialSection.Consent(scheduled = config.automaticCredentials),
                            trigger = OfflineBackupCredentialSection.Trigger.SCHEDULED,
                            snapshotProgress = { progress ->
                                val text = when (progress.stage) {
                                    OfflineBackupSnapshot.Stage.METADATA_CAPTURED -> "Daten werden vorbereitet …"
                                    OfflineBackupSnapshot.Stage.ORIGINALS_FIXED -> "Originale werden gesichert …"
                                    OfflineBackupSnapshot.Stage.SNAPSHOT_FIXED -> "Sicherung wird geprüft …"
                                }
                                updateProgress(text, progress.completed, progress.total)
                            }, destinationProgress = { stage ->
                                val text = when (stage) {
                                    OfflineBackupArchiveEngine.Stage.WRITING -> "Sicherung wird gespeichert …"
                                    OfflineBackupArchiveEngine.Stage.VERIFYING -> "Sicherung wird rückgelesen …"
                                    OfflineBackupArchiveEngine.Stage.FINALIZING -> "Sicherung wird abgeschlossen …"
                                }
                                updateProgress(text, 0, 0)
                            }, operationId = operationId, cancellationOutcome = ::cancellationOutcome)
                    }
                }
            }
            override fun notifyFailure() = showFailure()
            override fun cancellationOutcome() = this@OfflineBackupWorker.cancellationOutcome()
        })
        when (runner.run(requireNotNull(generation), requireNotNull(occurrence))) {
            OfflineBackupScheduledRunner.Result.COMPLETED, OfflineBackupScheduledRunner.Result.SKIPPED -> Result.success()
            OfflineBackupScheduledRunner.Result.FAILED -> Result.failure()
            OfflineBackupScheduledRunner.Result.RETRY -> Result.retry()
        }
    }

    private fun cancellationOutcome(): OfflineBackupHistory.Outcome =
        if (Build.VERSION.SDK_INT >= 31 && stopReason == WorkInfo.STOP_REASON_CANCELLED_BY_APP) OfflineBackupHistory.Outcome.CANCELLED
        else OfflineBackupHistory.Outcome.INTERRUPTED

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo("Sicherung wird vorbereitet …", -1)

    private fun foregroundInfo(text: String, percent: Int): ForegroundInfo {
        channels()
        val notification = progressNotification(text, percent)
        // Android 14+ requires a declared dataSync type for backup/restore foreground work.
        // https://developer.android.com/about/versions/14/changes/fgs-types-required#data-sync
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(PROGRESS_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(PROGRESS_ID, notification)
    }

    private fun progressNotification(text: String, percent: Int): Notification =
        NotificationCompat.Builder(applicationContext, PROGRESS_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("Lokale Vollsicherung")
            .setContentText(text).setContentIntent(openApp()).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .addAction(0, "Abbrechen", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()

    private fun updateProgress(text: String, completed: Int, total: Int) {
        val percent = if (total > 0) ((completed.coerceIn(0, total).toLong() * 100) / total).toInt() else -1
        val key = "$text:$percent"
        if (lastProgress == key) return
        lastProgress = key
        setProgressAsync(Data.Builder().putString("stage", text).putInt("percent", percent).build())
        postIfAllowed(PROGRESS_ID, progressNotification(text, percent))
    }

    private fun showFailure() {
        channels()
        val notification = NotificationCompat.Builder(applicationContext, FAILURE_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error).setContentTitle("Lokale Sicherung nicht abgeschlossen")
            .setContentText("Bitte öffne ImmoPilot und prüfe den Sicherungsstatus.")
            .setContentIntent(openApp()).setAutoCancel(true).build()
        postIfAllowed(FAILURE_ID, notification)
    }

    @SuppressLint("MissingPermission") // Runtime permission and channel availability are checked immediately below.
    private fun postIfAllowed(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = NotificationManagerCompat.from(applicationContext)
        if (manager.areNotificationsEnabled()) manager.notify(id, notification)
    }
    private fun channels() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(PROGRESS_CHANNEL, "Sicherungsfortschritt", NotificationManager.IMPORTANCE_LOW))
            manager.createNotificationChannel(NotificationChannel(FAILURE_CHANNEL, "Sicherungsprobleme", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
    private fun openApp(): PendingIntent = PendingIntent.getActivity(applicationContext, 0,
        Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun validId(value: String?): Boolean = try { value != null && UUID.fromString(value).toString() == value } catch (_: IllegalArgumentException) { false }

    private companion object {
        const val PROGRESS_CHANNEL = "offline_backup_progress"
        const val FAILURE_CHANNEL = "offline_backup_failure"
        const val PROGRESS_ID = 1201
        const val FAILURE_ID = 1202
        // Room's on-open initialization must outlive a single Worker cancellation.
        val databaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
